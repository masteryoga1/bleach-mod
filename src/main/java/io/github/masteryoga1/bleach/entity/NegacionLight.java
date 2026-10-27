package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.SafeLanding;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Negación: the pillar of light a Gillian calls down on a comrade (Ch. 178). The comrade rides it, which is
 * the one held state the mod knows ({@link Negacion#held}): nothing can hurt it but the void and commands,
 * and it can hurt, use or build nothing (see {@link Negacion}). The light falls for half a second, then
 * rises slowly with its comrade into a quiet Garganta tear that opens above it, and carries it off: a mob is
 * gone from the world, with no drops, experience or kill, and a player is set down at its respawn point, or
 * back where the light found it. A roof, the top of the world or unloaded ground stops the rise, and with too
 * little room to climb there is no tear, though the carry-off still comes.
 *
 * <p>A mob is mounted at once. A player must agree: the light lands as an offer only that player can take
 * by using it, within a few seconds and a few blocks ({@link Phase#OFFERED}), or it fades. A held player
 * leaves at once by sneaking, as from any mount, and falls slowly for a while after. Death, logging out,
 * a teleport or another world end the hold too, and a chunk unloading takes the light and its mob together.
 *
 * <p>The server decides everything; a client only draws the column from the synced data (see the client's
 * NegacionLightRenderer) and places its rider on it as the server does. It is never saved: the type is
 * {@code noSave}, so a player saved while riding it loads on foot.
 */
public class NegacionLight extends Entity {
    /** What the light is doing, synced by ordinal: append only. */
    public enum Phase { OFFERED, HOLDING, ENDING }

    /** The light coming down before anything rises, in ticks. */
    public static final int LAND_TICKS = 10;
    /** The light fading out once it has ended, in ticks. */
    public static final int FADE_TICKS = 10;
    /**
     * The tear opens this long before the carry-off: fully open 20 ticks before it, closing from 30 ticks
     * after and gone at 50 (see {@link GargantaRift#GROW_TICKS}, {@link GargantaRift#CLOSE_AGE} and
     * {@link GargantaRift#END_AGE}).
     */
    public static final int TEAR_LEAD_TICKS = 30;
    /** No tear opens when the light can climb less than this, in blocks. */
    public static final double MIN_TEAR_RISE = 2.0;
    /** An offered player may take the light from this far away, in blocks. */
    public static final double BOARD_REACH = 3.0;
    /** An offer ends once its player goes further away than this, in blocks. */
    public static final double OFFER_LEASH = 16.0;
    /** The column's pale gold, as {@code 0xRRGGBB}. */
    public static final int PALE_GOLD = 0xFFF2B0;

    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(NegacionLight.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HALF_WIDTH =
            SynchedEntityData.defineId(NegacionLight.class, EntityDataSerializers.FLOAT);
    /** The world height the column stands on, NaN until the light is placed or ridden. */
    private static final EntityDataAccessor<Float> DATA_BASE =
            SynchedEntityData.defineId(NegacionLight.class, EntityDataSerializers.FLOAT);
    /** The world height the column reaches, NaN until the light is placed or ridden. */
    private static final EntityDataAccessor<Float> DATA_TOP =
            SynchedEntityData.defineId(NegacionLight.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OFFERED_TO =
            SynchedEntityData.defineId(NegacionLight.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final float DEFAULT_HALF_WIDTH = 0.5F;
    /** The column is this much wider than its comrade on each side. */
    private static final float HALF_WIDTH_MARGIN = 0.25F;
    /** A placed column reaches this far above its comrade's head until a hold measures it. */
    private static final double PLACED_TOP_MARGIN = 1.0;
    /** A held column reaches this far above its comrade's head where the rise ends. */
    private static final double HELD_TOP_MARGIN = 0.5;
    /** The rise is measured in steps of this many blocks. */
    private static final double SCAN_STEP = 0.25;
    /** The tear is this much wider than half the comrade's width, and within these sizes. */
    private static final float TEAR_SIZE_MARGIN = 0.5F;
    private static final float TEAR_MIN_SIZE = 1.0F;
    private static final float TEAR_MAX_SIZE = 2.5F;
    /** Two motes of light rise through the column every second tick. */
    private static final int MOTE_TICKS = 2;
    private static final int MOTES = 2;
    private static final int DEPART_BURST = 20;
    private static final float SOUND_VOLUME = 1.0F;
    /** A held player is told it can touch nothing at most this often. */
    private static final int TELL_TICKS = 20;
    /** What a carried player is told of where it was set down. */
    private static final String SET_DOWN_SPAWN = "message.bleach.negacion.set_down.spawn";
    private static final String SET_DOWN_BACK = "message.bleach.negacion.set_down.back";
    private static final String LET_GO = "message.bleach.negacion.let_go";

    // Server-side state, never saved: the light is never saved.
    /** The player the light is offered to, kept as a reference so mock players work. */
    @Nullable
    private Player offerTarget;
    @Nullable
    private GargantaRift tear;
    /** Where the light stood when its hold began, for a player set down where it was found. */
    @Nullable
    private Vec3 origin;
    private int liftAt = -1;
    private int carryAt = -1;
    /** The highest the light may rise, measured when the hold began. */
    private double topLimit;
    /** Set while the light lets go of its comrade to carry it off, so it is not set down with a fall guard. */
    private boolean carrying;
    /** {@link #tickCount} when the phase began; on a client, when it learned of it. */
    private int phaseStartedAt;
    private int lastToldAt = -TELL_TICKS;

    public NegacionLight(EntityType<? extends NegacionLight> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * Lands a light at this creature's feet, sized to it: offered to {@code offerTo} when given, else empty
     * and waiting to be mounted (see {@link Negacion#lift}). The descent is heard by those who see spirits.
     *
     * @return the light, now in the world; null if the level refused it
     */
    @Nullable
    public static NegacionLight place(ServerLevel level, LivingEntity target, @Nullable Player offerTo) {
        NegacionLight light = new NegacionLight(ModEntities.NEGACION.get(), level);
        light.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), 0.0F);
        light.entityData.set(DATA_HALF_WIDTH, target.getBbWidth() / 2.0F + HALF_WIDTH_MARGIN);
        light.entityData.set(DATA_BASE, (float) target.getY());
        light.entityData.set(DATA_TOP, (float) (target.getBoundingBox().maxY + PLACED_TOP_MARGIN));
        if (offerTo != null) {
            light.offerTarget = offerTo;
            light.entityData.set(DATA_OFFERED_TO, Optional.of(offerTo.getUUID()));
        }
        if (!level.addFreshEntity(light)) {
            return null;
        }
        SpiritSenses.play(level, light.getX(), light.getY(), light.getZ(), ModSounds.NEGACION_DESCEND.get(),
                SoundSource.NEUTRAL, SOUND_VOLUME, 1.0F, false);
        return light;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, (byte) 0);
        builder.define(DATA_HALF_WIDTH, DEFAULT_HALF_WIDTH);
        builder.define(DATA_BASE, Float.NaN);
        builder.define(DATA_TOP, Float.NaN);
        builder.define(DATA_OFFERED_TO, Optional.empty());
    }

    // ---- What a client sees ----

    public Phase phase() {
        Phase[] phases = Phase.values();
        return phases[Mth.clamp(entityData.get(DATA_PHASE), 0, phases.length - 1)];
    }

    /** Ticks since the phase began; a client counts from when it learned of it. */
    public int phaseTicks() {
        return tickCount - phaseStartedAt;
    }

    /** The column's half-width, in blocks. */
    public float columnHalfWidth() {
        return entityData.get(DATA_HALF_WIDTH);
    }

    /** The world height the column stands on, NaN until the light is placed or ridden. */
    public float columnBase() {
        return entityData.get(DATA_BASE);
    }

    /** The world height the column reaches, NaN until the light is placed or ridden. */
    public float columnTop() {
        return entityData.get(DATA_TOP);
    }

    /** The player the light is offered to, if any. */
    @Nullable
    public UUID offeredTo() {
        return entityData.get(DATA_OFFERED_TO).orElse(null);
    }

    /** The creature the light holds: its rider, if living. */
    @Nullable
    public LivingEntity held() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    /** The tick of the carry-off, or -1 before the hold. Known on the server only. */
    public int carryAt() {
        return carryAt;
    }

    /** A client starts its count of the phase when it learns of it; the server when it sets it. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PHASE.equals(key)) {
            phaseStartedAt = tickCount;
        }
    }

    // ---- The light's life ----

    /**
     * Server side, by phase: an offer waits for its player and fades when the time runs out or the player goes;
     * a hold keeps its comrade safe, rises, and carries it off when the time comes; an ended light fades and
     * goes. A client only times the fades, which the renderer reads. Under a frozen tick rate the world's time
     * stands still, so a light that still ticks (one with a player on it) waits with it, its timeline pushed
     * back a tick at a time, and the tear stays in step.
     */
    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || !(level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.tickRateManager().runsNormally()) {
            waitFrozen();
            return;
        }
        switch (phase()) {
            case OFFERED -> tickOffer();
            case HOLDING -> tickHold(level);
            case ENDING -> {
                if (phaseTicks() >= FADE_TICKS) {
                    discard();
                }
            }
        }
    }

    /** Ends the offer once its time is up, or once its player has gone, died, left this world or walked away. */
    private void tickOffer() {
        if (phaseTicks() >= BleachConfig.get(BleachConfig.NEGACION_OFFER_SECONDS) * 20) {
            end();
            return;
        }
        Player player = offerTarget;
        if (player != null && (player.isRemoved() || !player.isAlive() || player.level() != level()
                || player.distanceToSqr(this) > OFFER_LEASH * OFFER_LEASH)) {
            end();
        }
    }

    /** One tick of the hold. See {@link #tick}. */
    private void tickHold(ServerLevel level) {
        LivingEntity held = held();
        // Another world: a player moved there by anything that keeps its mount (see Negacion's listeners).
        if (held == null || !held.isAlive() || held.isRemoved() || held.level() != level) {
            end();
            return;
        }
        keepSafe(held);
        if (tickCount >= liftAt && getY() < topLimit) {
            double step = Math.min(BleachConfig.get(BleachConfig.NEGACION_RISE_SPEED) / 20.0, topLimit - getY());
            // Something may have come into the way since the climb was measured: then it climbs no further.
            if (level.noCollision(held, held.getBoundingBox().move(0.0, step, 0.0))) {
                setPos(getX(), getY() + step, getZ());
            } else {
                topLimit = getY();
            }
        }
        if (tickCount % MOTE_TICKS == 0) {
            float base = columnBase();
            float top = columnTop();
            double y = Float.isNaN(base) || Float.isNaN(top) ? getY() : base + random.nextDouble() * (top - base);
            double spread = columnHalfWidth() * 0.6;
            SpiritSenses.particles(level, ParticleTypes.END_ROD, getX(), y, getZ(), MOTES, spread, 0.3, spread, 0.02);
        }
        if (tickCount >= carryAt) {
            carryOff(level, held);
        }
    }

    /** Under a frozen tick rate: the comrade is kept safe, and nothing else moves on. */
    private void waitFrozen() {
        phaseStartedAt++;
        if (liftAt >= 0) {
            liftAt++;
        }
        if (carryAt >= 0) {
            carryAt++;
        }
        LivingEntity held = phase() == Phase.HOLDING ? held() : null;
        if (held != null) {
            keepSafe(held);
        }
    }

    /** Nothing burns, freezes, drowns or keeps a fall in the light. */
    private static void keepSafe(LivingEntity held) {
        held.clearFire();
        held.setTicksFrozen(0);
        held.setAirSupply(held.getMaxAirSupply());
        held.resetFallDistance();
    }

    /**
     * The hold begins with a rider on the light. The rise is measured once, from here: up to the planned rise
     * (the speed over the hold), in steps, the rider's box moved onto the light (a player boards from up to
     * {@link #BOARD_REACH} away), until a step meets a block or a creature in the way, the top of the world or
     * unloaded ground. With room to climb {@link #MIN_TEAR_RISE} or more, a quiet tear is opened where the
     * column will end, timed to be fully open before the carry-off and to close behind it; one the level
     * refuses leaves the hold without a tear. A living rider falls slowly from now until a while after the hold
     * ends, so that a crash or a logout mid-air never ends in a fall.
     */
    private void beginHold(Entity passenger) {
        setPhase(Phase.HOLDING);
        liftAt = tickCount + LAND_TICKS;
        carryAt = liftAt + BleachConfig.get(BleachConfig.NEGACION_HOLD_SECONDS) * 20;
        origin = position();
        entityData.set(DATA_HALF_WIDTH, passenger.getBbWidth() / 2.0F + HALF_WIDTH_MARGIN);
        entityData.set(DATA_BASE, (float) getY());
        double planned = BleachConfig.get(BleachConfig.NEGACION_RISE_SPEED) / 20.0 * (carryAt - liftAt);
        AABB box = passenger.getBoundingBox().move(getX() - passenger.getX(), getY() - passenger.getY(),
                getZ() - passenger.getZ());
        double headroom = headroom(passenger, box, planned);
        topLimit = getY() + headroom;
        double top = topLimit + passenger.getBbHeight() + HELD_TOP_MARGIN;
        entityData.set(DATA_TOP, (float) top);
        if (headroom >= MIN_TEAR_RISE && level() instanceof ServerLevel level) {
            GargantaRift rift = GargantaRift.arrival(level, new Vec3(getX(), top, getZ()),
                    level.getGameTime() + (carryAt - tickCount) - TEAR_LEAD_TICKS, List.of(), null,
                    Mth.clamp(passenger.getBbWidth() / 2.0F + TEAR_SIZE_MARGIN, TEAR_MIN_SIZE, TEAR_MAX_SIZE));
            // Not an arrival: no pager beeps for it, as for a Gillian's own retreat.
            rift.setQuiet(true);
            tear = level.addFreshEntity(rift) ? rift : null;
        }
        if (passenger instanceof LivingEntity living) {
            int guard = BleachConfig.get(BleachConfig.NEGACION_FALL_GUARD_SECONDS) * 20;
            living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, carryAt - tickCount + guard, 0, false, false,
                    true));
        }
    }

    /** The last clear height up to {@code planned} for this box: 0 if it cannot climb at all. */
    private double headroom(Entity passenger, AABB box, double planned) {
        double clear = 0.0;
        for (double step = SCAN_STEP; clear < planned; step += SCAN_STEP) {
            double height = Math.min(step, planned);
            if (!roomFor(passenger, box.move(0.0, height, 0.0))) {
                break;
            }
            clear = height;
        }
        return clear;
    }

    /** Whether this box is clear of blocks and creatures in the way, below the top of the world, over loaded ground. */
    private boolean roomFor(Entity passenger, AABB box) {
        Level level = level();
        if (box.maxY > level.getMaxBuildHeight()) {
            return false;
        }
        for (int chunkX = Mth.floor(box.minX) >> 4; chunkX <= Mth.floor(box.maxX) >> 4; chunkX++) {
            for (int chunkZ = Mth.floor(box.minZ) >> 4; chunkZ <= Mth.floor(box.maxZ) >> 4; chunkZ++) {
                // Unloaded ground would count as empty air to the collision check, and is never loaded for this.
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return level.noCollision(passenger, box);
    }

    /**
     * The carry-off, at the top of the rise: the light's departure is heard and bursts at the comrade's head,
     * and it lets go. A mob is gone from the world (no drops, experience or kill, and a Menos frees its room
     * for another); a player is set down far away ({@link #setDownFar}). Then the light fades.
     */
    private void carryOff(ServerLevel level, LivingEntity held) {
        double headY = held.getBoundingBox().maxY;
        SpiritSenses.play(level, getX(), headY, getZ(), ModSounds.NEGACION_DEPART.get(), SoundSource.NEUTRAL,
                SOUND_VOLUME, 1.0F, false);
        double spread = columnHalfWidth();
        SpiritSenses.particles(level, ParticleTypes.REVERSE_PORTAL, getX(), headY, getZ(), DEPART_BURST, spread, 0.5,
                spread, 0.05);
        carrying = true;
        held.stopRiding();
        if (held instanceof Player player) {
            setDownFar(player);
        } else {
            held.discard();
        }
        carrying = false;
        end();
    }

    /**
     * A carried player is set down at its respawn point when that is in this world (an anchor keeps its charge,
     * as a bed does), else back where the light found it, each only where it lands safely; failing both it is
     * let go where it is, still falling slowly. Told what came of it.
     */
    private void setDownFar(Player player) {
        if (player instanceof ServerPlayer server) {
            String told;
            if (setDownAtSpawn(server)) {
                told = SET_DOWN_SPAWN;
            } else if (origin != null && SafeLanding.tryMoveTo(server, server.serverLevel(), origin)) {
                told = SET_DOWN_BACK;
            } else {
                told = LET_GO;
            }
            server.displayClientMessage(Component.translatable(told).withStyle(ChatFormatting.GRAY), true);
        } else if (origin != null) {
            // A game test's mock player has no connection to be moved through.
            player.teleportTo(origin.x, origin.y, origin.z);
        }
        player.resetFallDistance();
    }

    /**
     * Sets a carried player down at its respawn point, if the server does that, the point is in this world, its
     * bed or anchor is still there, and the spot is safe. Asked as a respawn would ask, but keeping the charge.
     */
    private static boolean setDownAtSpawn(ServerPlayer player) {
        if (!BleachConfig.get(BleachConfig.NEGACION_PLAYER_TO_RESPAWN)) {
            return false;
        }
        DimensionTransition spawn = player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
        return spawn.newLevel() == player.serverLevel() && !spawn.missingRespawnBlock()
                && SafeLanding.tryMoveTo(player, spawn.newLevel(), spawn.pos());
    }

    /**
     * Logging out and the like: lets this player off where the light first fell, falling slowly, and the light
     * fades. Does nothing for a player it does not hold. Server only.
     */
    public void setDown(Player player) {
        if (level().isClientSide() || player.getVehicle() != this) {
            return;
        }
        carrying = false;
        player.stopRiding();
        if (player instanceof ServerPlayer server && origin != null) {
            SafeLanding.tryMoveTo(server, server.serverLevel(), origin);
        }
        end();
    }

    /** Tells this held player, above the hotbar, that it can touch nothing: at most once a second. Server only. */
    public void tellHeld(Player player) {
        if (level().isClientSide() || tickCount - lastToldAt < TELL_TICKS) {
            return;
        }
        lastToldAt = tickCount;
        player.displayClientMessage(Component.translatable(Negacion.HELD).withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Ends the light, once: it fades from now, an unopened tear goes with it if the carry-off has not come, and
     * any rider gets off (which comes back here, and stops at the first check).
     */
    private void end() {
        if (phase() == Phase.ENDING) {
            return;
        }
        setPhase(Phase.ENDING);
        if (tear != null && (carryAt < 0 || tickCount < carryAt)) {
            tear.discard();
        }
        tear = null;
        ejectPassengers();
    }

    private void setPhase(Phase phase) {
        entityData.set(DATA_PHASE, (byte) phase.ordinal());
        phaseStartedAt = tickCount;
    }

    // ---- Riding ----

    /** The first rider begins the hold, on the server. */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide() && phase() != Phase.HOLDING) {
            beginHold(passenger);
        }
    }

    /**
     * A rider let go other than by the carry-off (a sneak, a teleport, a light that ended) keeps falling slowly for
     * a while, and the light ends. A dead or vanished rider gets nothing.
     */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide() || carrying) {
            return;
        }
        if (passenger instanceof LivingEntity living && living.isAlive()) {
            living.resetFallDistance();
            int guard = BleachConfig.get(BleachConfig.NEGACION_FALL_GUARD_SECONDS) * 20;
            if (guard > 0) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, guard, 0, false, false, true));
            }
        }
        end();
    }

    /**
     * One rider, and none once the light has ended. Vanilla asks this even of a forced mount, which skips
     * {@link #canAddPassenger}; NeoForge marks it deprecated, with nothing to take its place for this.
     */
    @Override
    @SuppressWarnings("deprecation")
    protected boolean couldAcceptPassenger() {
        return getPassengers().isEmpty() && phase() != Phase.ENDING;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return couldAcceptPassenger();
    }

    /** The rider stands on the light, its feet where the light is, on both sides. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        move.accept(passenger, getX(), getY(), getZ());
    }

    /** A rider that leaves stays where it is, in the air if need be, falling slowly. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    /** The held stand in the light. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Water and lava do not wash a rider out of the light. */
    @Override
    public boolean canBeRiddenUnderFluidType(FluidType type, Entity rider) {
        return true;
    }

    /**
     * Using an offered light boards it, for the player it is offered to only, from close by. A spectator, a
     * player too far away or one already held cannot; a client takes the click and leaves the rest to the
     * server.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (phase() != Phase.OFFERED || !player.getUUID().equals(offeredTo())) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSpectator() || player.distanceToSqr(this) > BOARD_REACH * BOARD_REACH || Negacion.held(player)) {
            return InteractionResult.PASS;
        }
        return player.startRiding(this, true) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    // ---- Untouchable ----

    /** Only an offer can be aimed at, to be used; a held rider cannot click its own light. */
    @Override
    public boolean isPickable() {
        return phase() == Phase.OFFERED;
    }

    /** Arrows and other shots pass through, even an offer that can be clicked. */
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    /** The column is drawn far taller than the box, so it is culled by what is drawn, with room for the tear. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double halfWidth = columnHalfWidth();
        float base = columnBase();
        float top = columnTop();
        double from = Float.isNaN(base) ? getY() : base;
        double to = Float.isNaN(top) ? getY() + 12.0 : top;
        return new AABB(getX() - halfWidth, from, getZ() - halfWidth, getX() + halfWidth, to + 4.0, getZ() + halfWidth);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
