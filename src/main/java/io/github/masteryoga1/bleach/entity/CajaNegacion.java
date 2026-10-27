package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.EspadaLedger;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.MenosBody;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.SafeLanding;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * The Caja Negación: the small cube a seated Espada carries, which, slipped into a Hollow hole from behind, closes
 * a cage of rebounding beams round the creature and shuts it out of the world for a while (Ch. 278). The creature
 * rides the cage, which is one of the held states the mod knows ({@link Negacion#held}): nothing can hurt it but the
 * void and commands, and it can hurt, use or build nothing (see {@link Negacion}). It cannot sneak out either
 * ({@link #locks}, read by the PlayerMixin). Once the cage has closed, the creature is hidden from all but itself and
 * spectators, and every creature near that hunted it forgets it, once a second. A quiet Garganta tear opens a little
 * before its time is up, and it tears its way out where it was taken, unharmed and falling slowly, as Ulquiorra did
 * (Ch. 315); the cage shrinks away. It never kills, and nobody outside can open it, but by command.
 *
 * <p>The cube is a plain item (the registry's {@code CAJA_NEGACION}); its use is {@link #useCube}, its rules
 * {@link #refusal}, and the time it holds {@link #holdTicks(LivingEntity)}, which reads only the creature held. Its
 * cooldown is kept in the {@link EspadaLedger}, which also gives the Espada its cube and takes back any other.
 *
 * <p>The server decides everything; a client only draws the cage from the synced phase and size (see the client's
 * CajaNegacionRenderer) and places its rider in it as the server does. It is never saved: the type is
 * {@code noSave}. A mob held when its chunk unloads or the world saves is written on its own, on foot
 * ({@link #saveAsPassenger}), so it loads free where it was taken and is never lost; a player saved while riding it
 * loads on foot, and one logging out is set down first.
 */
public class CajaNegacion extends Entity implements Negacion.HoldingVehicle {
    /** The cage closing on its rider, counted in the hold. */
    public static final int CLOSE_TICKS = 15;
    /** The renderer's shrink-away for those outside, once the cage has closed. */
    public static final int SHRINK_TICKS = 8;
    /** The tear opens this long before the release, as before Negación's carry-off (see {@link NegacionLight}). */
    public static final int TEAR_LEAD_TICKS = 30;
    /** The cage fading out once it has let go, in ticks. */
    public static final int FADE_TICKS = 10;
    /** A cage summoned empty waits this long for a rider, then goes. */
    public static final int EMPTY_TICKS = 100;
    /** Creatures this close that hunt the held one forget it, in blocks. */
    public static final double CLEAR_RANGE = 32.0;
    /** The cube's edge is this much more than its rider's height or width, whichever is the greater. */
    public static final float SIZE_MARGIN = 0.3F;
    /** Half the angle of the target's view, in degrees: a user inside it is seen coming. */
    public static final float FRONT_HALF_ANGLE = 60.0F;
    /** What a held player is told when its attack or use is cancelled. */
    public static final String HELD = "message.bleach.caja_negacion.held";

    /** What the cage is doing, synced by ordinal: append only. */
    public enum Phase { CLOSING, HOLDING, ENDING }

    /** Why a cube was not used. Each is told to the user. */
    public enum Refusal {
        NOT_ESPADA, COOLDOWN, NO_HOLE, TOO_BIG, BUSY, PROTECTED, SEES_IT;

        /** {@code message.bleach.caja_negacion.<name>}. */
        public String messageKey() {
            return "message.bleach.caja_negacion." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(CajaNegacion.class, EntityDataSerializers.BYTE);
    /** The cube's edge, in blocks. */
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(CajaNegacion.class, EntityDataSerializers.FLOAT);

    /** The edge of a cage with no rider yet, as the type is sized; also the least edge of any. */
    private static final float DEFAULT_SIZE = 1.0F;
    /** The tear is this much wider than half the rider's width, and within these sizes, as Negación's is. */
    private static final float TEAR_SIZE_MARGIN = 0.5F;
    private static final float TEAR_MIN_SIZE = 1.0F;
    private static final float TEAR_MAX_SIZE = 2.5F;
    /** The culling box is this many times the edge, for the beams' burst outward at the end. */
    private static final double CULL_SCALE = 1.6;
    /** How often the fights round the held one are dropped. */
    private static final int SWEEP_TICKS = 20;
    /** A held player is told it can touch nothing at most this often. */
    private static final int TELL_TICKS = 20;
    /** A built-in mob is lifted to the first clear spot up to this many steps of half a block above where it was taken. */
    private static final int CLEAR_STEPS = 6;
    private static final double CLEAR_STEP = 0.5;
    /** The user's view is measured across only; a user closer than this across is never seen. */
    private static final double SEEN_MIN_DISTANCE = 0.1;
    private static final float SOUND_VOLUME = 1.0F;
    private static final String CLOSED = "message.bleach.caja_negacion.closed";
    private static final String SHUT_IN = "message.bleach.caja_negacion.shut_in";
    private static final String SHUT_IN_NOBODY = "message.bleach.caja_negacion.shut_in.nobody";
    private static final String TORN_OUT = "message.bleach.caja_negacion.torn_out";

    // Server-side state, never saved: the cage is never saved.
    /** The hold's length in ticks: below 0 until the hold begins, worked out from the rider then if still unset. */
    private int holdTicks = -1;
    /** The tick of the release, or -1 before the hold. */
    private int releaseAt = -1;
    /** The name of whoever closed it, told to a held player; null for one summoned or ridden by command. */
    @Nullable
    private Component userName;
    /** Where the cage closed, which is where its rider is set down. */
    @Nullable
    private Vec3 origin;
    @Nullable
    private GargantaRift tear;
    /** Whether the tear has been opened (or refused by the level), so it is opened only once. */
    private boolean tearOpened;
    private int lastToldAt = -TELL_TICKS;
    private int lastSweepAt;
    /** Set once the time is up: the open tear is then left to close by itself. */
    private boolean released;
    /** {@link #tickCount} when the phase began; on a client, when it learned of it. */
    private int phaseStartedAt;

    public CajaNegacion(EntityType<? extends CajaNegacion> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    // ---- The cube's use ----

    /**
     * Closes a Caja on this creature and mounts it, by force: the cage stands where it stands, and the hold begins
     * at once ({@link #beginHold}). A {@code holdTicks} below 0 is worked out from the creature
     * ({@link #holdTicks(LivingEntity)}). Null, with nothing left, if it could not mount: a creature the cage may
     * not hold ({@link Negacion#mayHold}), one in another world, or one carrying a rider of its own.
     */
    @Nullable
    public static CajaNegacion close(ServerLevel level, LivingEntity target, @Nullable Player user, int holdTicks) {
        if (target.level() != level || target.isVehicle() || !Negacion.mayHold(target)) {
            return null;
        }
        CajaNegacion caja = new CajaNegacion(ModEntities.CAJA_NEGACION.get(), level);
        caja.moveTo(target.getX(), target.getY(), target.getZ(), 0.0F, 0.0F);
        caja.holdTicks = holdTicks;
        caja.userName = user != null ? user.getDisplayName() : null;
        if (!level.addFreshEntity(caja)) {
            return null;
        }
        if (!target.startRiding(caja, true)) {
            caja.discard();
            return null;
        }
        return caja;
    }

    /**
     * Ticks this creature is held, read from it alone and never from its user: a seated Espada player is held
     * {@code espada_seconds}; anything else by its share of an Espada's strength (its reiatsu over
     * {@code espada_min_max_reiryoku}), the full {@code max_seconds} up to {@code full_share}, falling in a straight
     * line to {@code min_seconds} at the whole line and beyond. A line of 0 counts every creature at it.
     */
    public static int holdTicks(LivingEntity target) {
        if (target instanceof Player player && EspadaLedger.isSeatedEspada(player)) {
            return BleachConfig.get(BleachConfig.CAJA_ESPADA_SECONDS) * 20;
        }
        int most = BleachConfig.get(BleachConfig.CAJA_MAX_SECONDS);
        int least = Math.min(BleachConfig.get(BleachConfig.CAJA_MIN_SECONDS), most);
        double full = BleachConfig.get(BleachConfig.CAJA_FULL_SHARE);
        double line = ArrancarRank.espadaMinMaxReiryoku();
        double share = line > 0.0 ? SoulLogic.reiatsu(target) / line : 1.0;
        double seconds;
        if (share <= full) {
            seconds = most;
        } else if (share >= 1.0) {
            seconds = least;
        } else {
            seconds = most - (share - full) / (1.0 - full) * (most - least);
        }
        return (int) Math.round(seconds * 20.0);
    }

    /** A Hollow mob, an Arrancar NPC, or a player whose soul is a Hollow or an Arrancar. */
    public static boolean hasHole(LivingEntity target) {
        if (target instanceof Hollow || target instanceof ArrancarNpc) {
            return true;
        }
        return target instanceof Player player && SoulLogic.hasSoul(player)
                && HollowPlayerLogic.isHollowOrArrancar(SoulLogic.get(player));
    }

    /**
     * Why this user may not shut this creature away now, or null when it may, the first that applies winning: the
     * user is no seated Espada (or the Caja is off); its cube is not ready (creative users need not wait); the
     * creature has no Hollow hole; it is too big (as for Negación, a Gillian or a giant player); it is busy (the user
     * itself, held already, carrying a rider, in another world, or a creature the cage may not hold now); a player
     * the PvP or team rules protect, or one in creative or spectator; and it sees the user coming, standing in its
     * view, unless it is the user's own Fracción.
     */
    @Nullable
    public static Refusal refusal(Player user, LivingEntity target, long now) {
        if (!BleachConfig.get(BleachConfig.CAJA_ENABLED) || !EspadaLedger.isSeatedEspada(user)) {
            return Refusal.NOT_ESPADA;
        }
        if (!user.isCreative() && now < readyAt(user)) {
            return Refusal.COOLDOWN;
        }
        if (!hasHole(target)) {
            return Refusal.NO_HOLE;
        }
        if (target.getBbHeight() > Negacion.MAX_HEIGHT || target.getBbWidth() > Negacion.MAX_WIDTH
                || target instanceof Gillian || target instanceof Player giant && MenosBody.isGiant(giant)) {
            return Refusal.TOO_BIG;
        }
        if (target == user || target.level() != user.level() || Negacion.held(target) || target.isVehicle()
                || !Negacion.mayHold(target)) {
            return Refusal.BUSY;
        }
        if (target instanceof Player player && !mayShutAway(user, player)) {
            return Refusal.PROTECTED;
        }
        if (BleachConfig.get(BleachConfig.CAJA_FROM_BEHIND) && !ownFraccion(user, target) && sees(target, user)) {
            return Refusal.SEES_IT;
        }
        return null;
    }

    /**
     * Uses the cube on this creature: the refusals ({@link #refusal}), then the close. The cube needs its rest from
     * now (kept in the ledger, and swept over the hotbar), but not for a creative user; the user is told above the
     * hotbar who is shut away and for how long, and it reaches the milestone. A cage that could not close counts as
     * busy and costs nothing. Nothing at all on a client.
     */
    @Nullable
    public static Refusal use(Player user, LivingEntity target, long now) {
        if (!(user.level() instanceof ServerLevel level)) {
            return null;
        }
        Refusal refusal = refusal(user, target, now);
        if (refusal != null) {
            return refusal;
        }
        CajaNegacion caja = close(level, target, user, holdTicks(target));
        if (caja == null) {
            return Refusal.BUSY;
        }
        int cooldown = BleachConfig.get(BleachConfig.CAJA_COOLDOWN_SECONDS) * 20;
        if (!user.isCreative() && cooldown > 0) {
            EspadaLedger.get(level.getServer()).setCajaReadyAt(user.getUUID(), now + cooldown);
            user.getCooldowns().addCooldown(ModItems.CAJA_NEGACION.get(), cooldown);
        }
        user.displayClientMessage(Component.translatable(CLOSED, target.getDisplayName(), caja.holdSeconds())
                .withStyle(ChatFormatting.GRAY), true);
        if (user instanceof ServerPlayer server) {
            Milestones.grant(server, Milestones.CAJA_NEGACION);
        }
        return null;
    }

    /**
     * The cube's use on a creature within reach (from the item). A client takes the click and leaves the rest to the
     * server, which uses it ({@link #use}) and tells any refusal above the hotbar, the cooldown with the seconds left.
     * The action is consumed either way, so the other hand gets no turn.
     */
    public static InteractionResult useCube(Player player, LivingEntity target) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        long now = player.level().getGameTime();
        Refusal refusal = use(player, target, now);
        if (refusal != null) {
            MutableComponent message = refusal == Refusal.COOLDOWN
                    ? Component.translatable(refusal.messageKey(), secondsToReady(player, now))
                    : Component.translatable(refusal.messageKey());
            player.displayClientMessage(message.withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Whole seconds, rounded up, until this user's cube is ready again; 0 when it is. */
    public static long secondsToReady(Player user, long now) {
        return Math.max(0L, (long) Math.ceil((readyAt(user) - now) / 20.0));
    }

    // ---- Who is shut away ----

    /** Riding a Caja that has not begun to end. Both sides. */
    public static boolean shutAway(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof CajaNegacion caja && caja.phase() != Phase.ENDING;
    }

    /** Riding a Caja that hides its rider now (HOLDING). Both sides. */
    public static boolean hidden(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof CajaNegacion caja && caja.hidesRider();
    }

    /** {@link #shutAway}, for the sneak lock: a player in a Caja cannot sneak out of it. */
    public static boolean locks(Player player) {
        return shutAway(player);
    }

    // ---- What a client sees ----

    public Phase phase() {
        Phase[] phases = Phase.values();
        return phases[Mth.clamp(entityData.get(DATA_PHASE), 0, phases.length - 1)];
    }

    /** The cube's edge, in blocks. */
    public float size() {
        return entityData.get(DATA_SIZE);
    }

    /** Ticks since the phase began; a client counts from when it learned of it, or of the hold's start. */
    public int phaseTicks() {
        return tickCount - phaseStartedAt;
    }

    /** Whether the cage hides its rider now: once it has closed, until it lets go. */
    public boolean hidesRider() {
        return phase() == Phase.HOLDING;
    }

    /** The creature the cage holds: its rider, if living. */
    @Nullable
    public LivingEntity held() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    /**
     * Whether a rider getting off now is landed where the cage closed: on the server, alive, and still in this world.
     * A player already taken to another world, which vanilla does with its mount kept, is never pulled back.
     */
    public boolean landsHere(Entity rider) {
        return !level().isClientSide() && rider.isAlive() && rider.level() == level();
    }

    /**
     * A client starts its count of the phase when it learns of it, and again when it learns the cage's size, which
     * is set only as the hold begins (a cage summoned empty begins it when it is ridden). The server likewise.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PHASE.equals(key) || DATA_SIZE.equals(key)) {
            phaseStartedAt = tickCount;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, (byte) Phase.CLOSING.ordinal());
        builder.define(DATA_SIZE, DEFAULT_SIZE);
    }

    // ---- The cage's life ----

    /**
     * Server side, by phase: a cage closing or holding keeps its rider safe, drops the fights round it once a
     * second, opens the tear and lets it go when the time comes; one that has let go fades and goes. A cage summoned
     * empty waits a while for a rider, and anything ridden into it that is not a living creature is put out at once,
     * and the cage goes. A client only times the phases, which the renderer reads. Under a frozen tick rate the
     * world's time stands still, so a cage that still ticks (one with a player in it) waits with it, its release
     * pushed back a tick at a time, and the tear stays in step.
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
        if (getFirstPassenger() != null && !(getFirstPassenger() instanceof LivingEntity)) {
            // A boat or a minecart put in by /ride is never held: it is put out, and the cage goes.
            ejectPassengers();
            discard();
            return;
        }
        switch (phase()) {
            case CLOSING, HOLDING -> tickHold(level);
            case ENDING -> {
                if (phaseTicks() >= FADE_TICKS) {
                    discard();
                }
            }
        }
    }

    /** One tick of the hold, or of the wait for a rider. See {@link #tick}. */
    private void tickHold(ServerLevel level) {
        LivingEntity rider = held();
        if (rider == null) {
            if (releaseAt >= 0) {
                end();
            } else if (tickCount >= EMPTY_TICKS) {
                discard();
            }
            return;
        }
        // Another world: a player moved there by anything that keeps its mount (see Negacion's listeners).
        if (!rider.isAlive() || rider.isRemoved() || rider.level() != level) {
            end();
            return;
        }
        if (phase() == Phase.CLOSING && phaseTicks() >= CLOSE_TICKS) {
            setPhase(Phase.HOLDING);
        }
        keepSafe(rider);
        if (tickCount - lastSweepAt >= SWEEP_TICKS) {
            lastSweepAt = tickCount;
            dropFights(rider);
        }
        openTear(level, rider);
        if (releaseAt >= 0 && tickCount >= releaseAt) {
            release();
        }
    }

    /** Under a frozen tick rate: the rider is kept safe, and nothing else moves on. */
    private void waitFrozen() {
        phaseStartedAt++;
        lastSweepAt++;
        if (releaseAt >= 0) {
            releaseAt++;
        }
        LivingEntity rider = phase() != Phase.ENDING ? held() : null;
        if (rider != null) {
            keepSafe(rider);
        }
    }

    /** Nothing burns, freezes, drowns or keeps a fall in the cage. */
    private static void keepSafe(LivingEntity rider) {
        rider.clearFire();
        rider.setTicksFrozen(0);
        rider.setAirSupply(rider.getMaxAirSupply());
        rider.resetFallDistance();
    }

    /**
     * The hold begins with a rider in the cage, which closes round it from now. A kneeling Número stops kneeling
     * (the mount has stood it up already). The cube is sized to the rider, the time is worked out if no one set it
     * (never shorter than the closing and the tear's lead together), the quiet tear is opened where the cage stands,
     * its closing is heard by those who see spirits, every fight round the rider is dropped, and a held player is
     * told in chat who shut it away and when it will tear its way out.
     */
    private void beginHold(LivingEntity rider) {
        phaseStartedAt = tickCount;
        origin = position();
        if (rider instanceof ArrancarNpc npc && npc.isYielding()) {
            npc.endYield();
        }
        float size = Math.max(DEFAULT_SIZE, Math.max(rider.getBbWidth(), rider.getBbHeight()) + SIZE_MARGIN);
        // Forced, so a client that saw the cage before it was ridden restarts its count with the hold.
        entityData.set(DATA_SIZE, size, true);
        if (holdTicks < 0) {
            holdTicks = holdTicks(rider);
        }
        holdTicks = Math.max(holdTicks, CLOSE_TICKS + TEAR_LEAD_TICKS);
        releaseAt = tickCount + holdTicks;
        if (level() instanceof ServerLevel level) {
            openTear(level, rider);
            SpiritSenses.play(level, getX(), getY(), getZ(), ModSounds.CAJA_NEGACION_CLOSE.get(), SoundSource.NEUTRAL,
                    SOUND_VOLUME, 1.0F, false);
        }
        lastSweepAt = tickCount;
        dropFights(rider);
        if (rider instanceof Player player) {
            MutableComponent told = userName != null
                    ? Component.translatable(SHUT_IN, userName, holdSeconds())
                    : Component.translatable(SHUT_IN_NOBODY, holdSeconds());
            player.displayClientMessage(told.withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /**
     * Opens the quiet tear in the middle of the cage, timed to open {@link #TEAR_LEAD_TICKS} before the release and to
     * close behind it, once. A tear that would wait longer than a Garganta may lie dormant (a hold of more than about
     * a minute) is opened later, once its time is that near; one the level refuses leaves the hold without a tear.
     */
    private void openTear(ServerLevel level, LivingEntity rider) {
        if (tearOpened || releaseAt < 0) {
            return;
        }
        long wait = releaseAt - TEAR_LEAD_TICKS - tickCount;
        if (wait > GargantaRift.DORMANT_LIMIT) {
            return;
        }
        tearOpened = true;
        GargantaRift rift = GargantaRift.arrival(level, new Vec3(getX(), getY() + size() / 2.0, getZ()),
                level.getGameTime() + wait, List.of(), null,
                Mth.clamp(rider.getBbWidth() / 2.0F + TEAR_SIZE_MARGIN, TEAR_MIN_SIZE, TEAR_MAX_SIZE));
        // Not an arrival: no pager beeps for it, as for Negación's own tear.
        rift.setQuiet(true);
        tear = level.addFreshEntity(rift) ? rift : null;
    }

    /** The rider forgets its own fight, and every creature near that hunted the rider forgets it. */
    private void dropFights(LivingEntity rider) {
        if (rider instanceof Mob mob) {
            mob.setTarget(null);
        }
        for (Mob hunter : level().getEntitiesOfClass(Mob.class, rider.getBoundingBox().inflate(CLEAR_RANGE),
                other -> other.getTarget() == rider)) {
            hunter.setTarget(null);
        }
    }

    /** The time is up: the cage lets go of its rider, which comes back here to be set down, and fades. */
    private void release() {
        released = true;
        // Open by now, the tear closes behind the rider by itself.
        tear = null;
        setPhase(Phase.ENDING);
        ejectPassengers();
    }

    /**
     * Ends the cage early, once: it fades from now, the tear goes with it if the release has not come, and any rider
     * gets off (which comes back here, and stops at the first check).
     */
    private void end() {
        if (phase() == Phase.ENDING) {
            return;
        }
        setPhase(Phase.ENDING);
        if (tear != null && !released) {
            tear.discard();
        }
        tear = null;
        ejectPassengers();
    }

    private void setPhase(Phase phase) {
        entityData.set(DATA_PHASE, (byte) phase.ordinal());
        phaseStartedAt = tickCount;
    }

    /** The hold's length in whole seconds, rounded up, as told to the user and the held. */
    private int holdSeconds() {
        return Mth.ceil(Math.max(holdTicks, 0) / 20.0);
    }

    /**
     * Logging out and the like: the cage lets this player go, which sets it down where it was taken
     * ({@link #removePassenger}), and fades. Does nothing for a player it does not hold. Server only.
     */
    @Override
    public void setDown(Player player) {
        if (level().isClientSide() || player.getVehicle() != this) {
            return;
        }
        end();
    }

    /** Tells this held player, above the hotbar, that it can touch nothing: at most once a second. Server only. */
    @Override
    public void tellHeld(Player player) {
        if (level().isClientSide() || tickCount - lastToldAt < TELL_TICKS) {
            return;
        }
        lastToldAt = tickCount;
        player.displayClientMessage(Component.translatable(HELD).withStyle(ChatFormatting.GRAY), true);
    }

    // ---- Riding ----

    /** The first living rider begins the hold, on the server; anything else is put out at the next tick. */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide() && phase() == Phase.CLOSING && releaseAt < 0 && passenger instanceof LivingEntity rider) {
            beginHold(rider);
        }
    }

    /**
     * A rider let go, whatever the reason (the release, a cage that ended or was killed, a logout, a dimension
     * change, {@code /ride} or a teleport), falls slowly for a while if it lives. One that lives and is still in
     * this world ({@link #landsHere}) is set down where the cage closed and told so: a player as safely as it can
     * be, anything else lifted out of any block that has filled its spot since. Then the cage ends.
     */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide()) {
            return;
        }
        if (passenger instanceof LivingEntity rider) {
            if (rider.isAlive()) {
                rider.resetFallDistance();
                int guard = BleachConfig.get(BleachConfig.NEGACION_FALL_GUARD_SECONDS) * 20;
                if (guard > 0) {
                    rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, guard, 0, false, false, true));
                }
            }
            if (origin != null && landsHere(rider)) {
                land(rider, origin);
            }
        }
        end();
    }

    /** Sets a rider down at this spot: a server player through {@link SafeLanding}, anything else at a clear spot. */
    private void land(LivingEntity rider, Vec3 at) {
        if (rider instanceof ServerPlayer player) {
            SafeLanding.tryMoveTo(player, player.serverLevel(), at);
        } else {
            // A mob, or a game test's mock player, which has no connection to be moved through.
            clearSpot(rider, at);
        }
        if (rider instanceof Player player) {
            player.displayClientMessage(Component.translatable(TORN_OUT).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Moves the rider to this spot, or to the first clear one up to three blocks above it; failing all, to the spot. */
    private void clearSpot(LivingEntity rider, Vec3 at) {
        for (int step = 0; step <= CLEAR_STEPS; step++) {
            Vec3 spot = at.add(0.0, step * CLEAR_STEP, 0.0);
            if (level().noCollision(rider, rider.getDimensions(rider.getPose()).makeBoundingBox(spot))) {
                rider.teleportTo(spot.x, spot.y, spot.z);
                return;
            }
        }
        rider.teleportTo(at.x, at.y, at.z);
    }

    /**
     * One rider, and none once the cage has ended. Vanilla asks this even of a forced mount, which skips
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

    /** The rider stands in the cage, its feet where the cage stands, on both sides. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        move.accept(passenger, getX(), getY(), getZ());
    }

    /** A rider that leaves stays where it was put down. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    /** The held stand in the cage. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Water and lava do not wash a rider out of the cage. */
    @Override
    public boolean canBeRiddenUnderFluidType(FluidType type, Entity rider) {
        return true;
    }

    // ---- Saving ----

    /**
     * Never saved itself; a held mob is written in its place, on its own and on foot (it carries no rider: a
     * creature carrying one is never shut away), so it loads free where it was taken. A player is never written
     * here: players are saved apart, and one in a cage loads on foot, as vanilla does with a {@code noSave} mount.
     */
    @Override
    public boolean saveAsPassenger(CompoundTag tag) {
        LivingEntity rider = held();
        return rider != null && !(rider instanceof Player) && rider.saveAsPassenger(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // ---- Untouchable ----

    /** It cannot be aimed at, used or struck; nobody outside can open it. */
    @Override
    public boolean isPickable() {
        return false;
    }

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

    /** The cube is drawn larger than the type's box, and bursts outward as it ends: culled by what is drawn. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double half = size() * CULL_SCALE / 2.0;
        double middle = getY() + size() / 2.0;
        return new AABB(getX() - half, middle - half, getZ() - half, getX() + half, middle + half, getZ() + half);
    }

    // ---- Helpers ----

    /** The game time this user's cube is ready again, from the server's ledger; 0 off the server. */
    private static long readyAt(Player user) {
        MinecraftServer server = user.getServer();
        return server == null ? 0L : EspadaLedger.get(server).cajaReadyAt(user.getUUID());
    }

    /**
     * Whether this user may shut this player away: never one in creative or spectator; only while the Caja may take
     * players at all ({@code pvp}), the server allows PvP, and the teams let the user harm it.
     */
    private static boolean mayShutAway(Player user, Player target) {
        if (target.isCreative() || target.isSpectator() || !BleachConfig.get(BleachConfig.CAJA_PVP)) {
            return false;
        }
        MinecraftServer server = user.getServer();
        // A mock player in a game test is a plain Player, whose canHarmPlayer asks only the teams.
        return server != null && server.isPvpAllowed() && user.canHarmPlayer(target);
    }

    /** Whether this creature is the user's own pledged Fracción, which never needs to be taken from behind. */
    private static boolean ownFraccion(Player user, LivingEntity target) {
        return target instanceof ArrancarNpc npc && npc.isPledged() && user.getUUID().equals(npc.leaderId());
    }

    /**
     * Whether the user stands in the target's view: within {@link #FRONT_HALF_ANGLE} of where its head faces, measured
     * across only. A user right on top of it, closer than {@link #SEEN_MIN_DISTANCE} across, is not seen.
     */
    private static boolean sees(LivingEntity target, Player user) {
        Vec3 across = new Vec3(user.getX() - target.getX(), 0.0, user.getZ() - target.getZ());
        if (across.horizontalDistance() < SEEN_MIN_DISTANCE) {
            return false;
        }
        Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYHeadRot());
        return facing.dot(across.normalize()) > Math.cos(Math.toRadians(FRONT_HALF_ANGLE));
    }
}
