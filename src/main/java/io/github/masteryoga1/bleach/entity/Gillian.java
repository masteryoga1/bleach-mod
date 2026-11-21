package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.ShakonmakuEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import org.joml.Vector3f;

/**
 * A Gillian, the commonest class of Menos Grande: a towering cloaked giant with a long-nosed white
 * mask, one much like the next. It lumbers straight at its prey and swats it, for it is far too big to
 * find a path (see {@link Navigation}); boxed in, it tramples through leaves, then gives up for a while
 * and gathers a Cero instead. Every few seconds it reaches for the nearest lesser Hollow and draws it up
 * to its mouth to eat, as one did at the cracked sky over Karakura. A beaten ally it sees it may rescue
 * instead, calling down Negación's light to lift it away into a Garganta ({@link #tryRescue}).
 *
 * <p>It comes down from the sky through a tear of its own or a bait's cracked sky ({@link #emerge}),
 * sinking slowly to land heavily. Badly hurt, frightened by stronger reiatsu, past its lifetime or left
 * alone, it pulls itself back up into a tear above its head ({@link #beginRetreat}); a player who has
 * just hurt it when it is beaten has driven it off.
 *
 * <p>Its body reaches far above the reach of entity lookups, so it carries two hit boxes of its own
 * ({@link Part}): one for the body above its knees and one about its mask. The aware within range see
 * a boss bar while it lives.
 */
public class Gillian extends Menos {
    public static final float WIDTH = 3.4F;
    public static final float HEIGHT = 15.0F;
    public static final float EYE_HEIGHT = 13.0F;
    /** How far past its sides a swat reaches, at any height of its body. */
    public static final double ATTACK_REACH = 2.5;
    /** The body part runs from this high above the feet, and is this tall. */
    public static final double BODY_BOTTOM = 4.0;
    public static final float BODY_HEIGHT = 9.0F;
    /** The edge of the head part, a cube about the mask. */
    public static final float HEAD_SIZE = 3.2F;
    /** How far below the bottom of a tear its feet are as it comes through, so its head is in the tear. */
    public static final double EMERGE_BELOW_TEAR = 9.0;
    /** The size of its own tear when it comes by chance, and how far from the soul it lands and how high it starts. */
    public static final float ARRIVAL_TEAR_SIZE = 2.5F;
    public static final int ARRIVAL_MIN_DISTANCE = 20;
    public static final int ARRIVAL_MAX_DISTANCE = 36;
    public static final int ARRIVAL_RISE = 12;
    /** Columns tried below a tear, out to this radius, and the furthest it may fall to the ground. */
    public static final int LANDING_TRIES = 16;
    public static final double LANDING_RING_MAX = 8.0;
    public static final int LANDING_MAX_DROP = 28;
    /** How long its slow fall lasts at most, and how long the heavy landing slows it. */
    public static final int DESCENT_SLOW_FALL_TICKS = 200;
    public static final int HEAVY_LANDING_TICKS = 30;
    /** The first feed comes this long after its first step or its landing. */
    public static final int FEED_FIRST_DELAY_TICKS = 40;
    /** How long the tongue pulls a Hollow at most, and how fast, in blocks a tick. */
    public static final int FEED_PULL_TICKS = 40;
    public static final double FEED_PULL_SPEED = 0.35;
    /** How far above and below its feet it looks for a Hollow to eat. */
    public static final double FEED_VERTICAL_REACH = 16.0;
    /** A Hollow this close to the mouth is swallowed. */
    public static final double FEED_SWALLOW_DISTANCE = 2.0;
    /** A Hollow pulled this much further than the reach is let go. */
    public static final double FEED_LET_GO_SLACK = 6.0;
    /** How far above or below itself a Gillian looks for an ally to rescue. */
    public static final double RESCUE_VERTICAL_REACH = 32.0;
    /** The tear it pulls itself up into: its size and how far above its feet it opens. */
    public static final float RETREAT_TEAR_SIZE = 2.5F;
    public static final double RETREAT_TEAR_RISE = 10.0;
    /** It starts to rise this long into the retreat, this fast, and is gone at the end. */
    public static final int RETREAT_RISE_START = 10;
    public static final double RETREAT_RISE_PER_TICK = 0.15;
    public static final int RETREAT_TICKS = 60;
    /** The aware within this range see its boss bar, refreshed this often. */
    public static final double BOSS_BAR_RANGE = 64.0;
    public static final int BOSS_BAR_INTERVAL = 20;
    /** Blocked this long, it stops striding and rests this long. */
    public static final int STRIDE_GIVE_UP_TICKS = 40;
    public static final int STRIDE_REST_TICKS = 100;
    /** While blocked it tramples leaves this often, this many at a time. */
    public static final int TRAMPLE_INTERVAL = 10;
    public static final int TRAMPLE_BUDGET = 12;
    /** Blocks walked between footsteps. */
    public static final float STEP_DISTANCE = 3.0F;
    public static final int PART_BODY = 0;
    public static final int PART_HEAD = 1;

    private static final long NEVER = ShinigamiProgress.NEVER;
    private static final double GOLDEN_ANGLE = 2.39996;
    private static final int SLOWNESS_IV = 3;
    private static final float LOUD = 3.0F;
    private static final float STEP_VOLUME = 1.5F;
    private static final float STRIDE_TURN = 10.0F;
    /** A pulled Hollow is held a little longer than the pull can last. */
    private static final int FEED_HOLD_TICKS = FEED_PULL_TICKS + 10;
    private static final int TONGUE_POINTS = 8;
    private static final DustParticleOptions TONGUE = new DustParticleOptions(new Vector3f(0.55F, 0.12F, 0.2F), 1.5F);

    private final Part[] parts;           // [PART_BODY, PART_HEAD]
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("bossbar.bleach.menos_grande"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    // Server-side state, not saved: a Gillian is never saved.
    private boolean descending;
    private long descendingSince = NEVER;
    private boolean retreating;
    private long retreatStart = NEVER;
    private long nextFeedAt = NEVER;
    @Nullable
    private Hollow feedingOn;
    private long feedStart = NEVER;
    private long restUntil = NEVER;        // read by StrideGoal
    private long nextRescueAt = NEVER;
    private long rescueReadyAt = NEVER;

    public Gillian(EntityType<? extends Gillian> type, Level level) {
        super(type, level);
        parts = new Part[] {new Part(this, WIDTH, BODY_HEIGHT), new Part(this, HEAD_SIZE, HEAD_SIZE)};
        // Ids for itself and both parts, as the Ender Dragon takes them: the client gives the parts theirs the same way.
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    /** Its kind's stats, and no blast can throw it. */
    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.GILLIAN.attributes().add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public HollowKind kind() {
        return HollowKind.GILLIAN;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Navigation(this, level);
    }

    /** Its own goals: no paths to follow, so no chase, stroll or gathering at a bait, and it cannot swim. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new CeroGoal(this));
        goalSelector.addGoal(2, new StrideGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, hurtByTargetGoal());
        targetSelector.addGoal(2, new HollowHuntGoal(this));
    }

    @Override
    protected CeroBeam.Spec ceroSpec() {
        // The [hollows] damage multiplier reaches the Cero too, as it does every Hollow's blows.
        return new CeroBeam.Spec((float) (BleachConfig.get(BleachConfig.GILLIAN_CERO_DAMAGE)
                        * BleachConfig.get(BleachConfig.HOLLOW_DAMAGE_MULTIPLIER)),
                BleachConfig.get(BleachConfig.GILLIAN_CERO_RANGE),
                (float) BleachConfig.get(BleachConfig.GILLIAN_CERO_RADIUS),
                (float) BleachConfig.get(BleachConfig.GILLIAN_CERO_BLAST_RADIUS),
                (float) BleachConfig.get(BleachConfig.CERO_BLAST_SHARE),
                BleachConfig.get(BleachConfig.GILLIAN_CERO_MAX_BLOCKS),
                (float) BleachConfig.get(BleachConfig.GILLIAN_CERO_KNOCKBACK),
                CeroBeam.MENOS_RED,
                BleachConfig.get(BleachConfig.GILLIAN_CERO_CHARGE_TICKS),
                BleachConfig.get(BleachConfig.GILLIAN_CERO_AIM_LOCK_TICKS),
                BleachConfig.get(BleachConfig.CERO_BREAKS_BLOCKS));
    }

    @Override
    protected double ceroMinRange() {
        return BleachConfig.get(BleachConfig.GILLIAN_CERO_MIN_RANGE);
    }

    @Override
    protected int ceroCooldownTicks() {
        return BleachConfig.get(BleachConfig.GILLIAN_CERO_COOLDOWN_SECONDS) * 20;
    }

    /** Not with a Hollow on its tongue. */
    @Override
    protected boolean mayChargeNow() {
        return feedingOn == null;
    }

    @Override
    public boolean isRetreating() {
        return retreating;
    }

    @Override
    public boolean isDescending() {
        return descending;
    }

    @Override
    protected void onLifetimeOver() {
        beginRetreat(false);
    }

    /** A Gillian leaves by pulling itself up into the sky, never through a small tear at its feet. */
    @Override
    protected void leave() {
        beginRetreat(false);
    }

    @Override
    public void depart() {
        beginRetreat(false);
    }

    /** The Hollow on its tongue, if any. Known on the server only. */
    @Nullable
    public Hollow feedingOn() {
        return feedingOn;
    }

    public Part body() {
        return parts[PART_BODY];
    }

    public Part head() {
        return parts[PART_HEAD];
    }

    /**
     * Pulls itself up into a tear that opens above its head, and is gone within three seconds. It
     * drops its prey and its charge, and fights no more, but can still be hurt and killed on the way.
     * It cannot while it is dead or dissolving, held in a Hollow player's jaws, or still coming down.
     *
     * @param beaten whether it was driven off, by its wounds or by stronger reiatsu: then a player who
     *               hurt it in the last five seconds has bested it, and the aware nearby are told
     */
    public void beginRetreat(boolean beaten) {
        if (retreating || !isAlive() || isPurifying() || isSeized() || descending
                || !(level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        retreating = true;
        retreatStart = now;
        setTarget(null);
        getNavigation().stop();
        CeroBeam beam = chargingBeam();
        if (beam != null && beam.isCharging()) {
            beam.cancel();
        }
        letGo(now);
        GargantaRift tear = GargantaRift.arrival(level, position().add(0.0, RETREAT_TEAR_RISE, 0.0), now, List.of(), null,
                RETREAT_TEAR_SIZE);
        // Not an arrival: no pager beeps for it.
        tear.setQuiet(true);
        level.addFreshEntity(tear);
        if (beaten && lastHurtByPlayer instanceof ServerPlayer player && lastHurtByPlayerTime > 0) {
            Milestones.grant(player, Milestones.MENOS_GRANDE);
            Component message = Component.translatable("message.bleach.menos.retreat").withStyle(ChatFormatting.GRAY);
            for (ServerPlayer aware : SpiritSenses.awareNear(level, position(), BOSS_BAR_RANGE)) {
                aware.displayClientMessage(message, true);
            }
        }
    }

    /**
     * Where a Gillian coming through this tear can land: the first of sixteen columns on a fixed
     * spiral out from below it with dry, solid ground under it, close enough below for the fall, and a
     * clear shaft for the whole body with no player in it. Its feet start {@link #EMERGE_BELOW_TEAR}
     * below the tear.
     *
     * @return the feet, at the middle of the column; null if no column will do
     */
    @Nullable
    public static Vec3 skyLanding(ServerLevel level, Vec3 tear) {
        double feetY = tear.y - EMERGE_BELOW_TEAR;
        for (int i = 0; i < LANDING_TRIES; i++) {
            double radius = i == 0 ? 0.0 : 1.0 + (LANDING_RING_MAX - 1.0) * i / (LANDING_TRIES - 1);
            double angle = i * GOLDEN_ANGLE;
            int x = Mth.floor(tear.x + Math.cos(angle) * radius);
            int z = Mth.floor(tear.z + Math.sin(angle) * radius);
            Vec3 feet = landingAt(level, x, z, feetY);
            if (feet != null) {
                return feet;
            }
        }
        return null;
    }

    /** Whether a Gillian can come down this column from these feet, and where it starts if so. */
    @Nullable
    private static Vec3 landingAt(ServerLevel level, int x, int z, double feetY) {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (feetY < groundY || feetY > groundY + LANDING_MAX_DROP) {
            return null;
        }
        BlockPos ground = new BlockPos(x, groundY, z);
        BlockPos below = ground.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(below).isEmpty()
                || !level.isPositionEntityTicking(ground) || !level.getWorldBorder().isWithinBounds(ground)) {
            return null;
        }
        double centreX = x + 0.5;
        double centreZ = z + 0.5;
        double half = WIDTH / 2.0;
        AABB shaft = new AABB(centreX - half, groundY, centreZ - half, centreX + half, feetY + HEIGHT, centreZ + half);
        if (!level.noCollision(shaft) || level.containsAnyLiquid(shaft)
                || !level.getEntitiesOfClass(Player.class, shaft.inflate(1.0, 0.0, 1.0)).isEmpty()) {
            return null;
        }
        return new Vec3(centreX, feetY, centreZ);
    }

    /**
     * A Gillian pushes through the sky with its feet here, facing the soul that drew it, and sinks
     * slowly to the ground if there is air below. It goes after that soul if it is prey.
     *
     * @return the Gillian, now in the world; null if it had no room or could not be added
     */
    @Nullable
    public static Gillian emerge(ServerLevel level, Vec3 feet, @Nullable ServerPlayer drawer) {
        Gillian gillian = ModEntities.GILLIAN.get().create(level);
        if (gillian == null) {
            return null;
        }
        ServerPlayer facing = drawer != null && drawer.level() == level ? drawer : null;
        float yaw = facing != null
                ? (float) (Mth.atan2(facing.getZ() - feet.z, facing.getX() - feet.x) * Mth.RAD_TO_DEG) - 90.0F
                : level.getRandom().nextFloat() * 360.0F;
        gillian.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        gillian.setYHeadRot(yaw);
        gillian.yHeadRotO = yaw;
        gillian.setYBodyRot(yaw);
        gillian.yBodyRotO = yaw;
        EventHooks.finalizeMobSpawn(gillian, level, level.getCurrentDifficultyAt(BlockPos.containing(feet)),
                MobSpawnType.EVENT, null);
        gillian.setDrawer(drawer != null ? drawer.getUUID() : null);
        if (!level.noCollision(gillian.getBoundingBox())) {
            return null;
        }
        if (level.noCollision(gillian.getBoundingBox().move(0.0, -0.5, 0.0))) {
            gillian.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DESCENT_SLOW_FALL_TICKS, 0, false, false));
            gillian.descending = true;
            gillian.descendingSince = level.getGameTime();
        }
        if (!level.addFreshEntity(gillian)) {
            return null;
        }
        gillian.playSound(ModSounds.GILLIAN_AMBIENT.get(), LOUD, 0.8F);
        if (facing != null && HollowLogic.isPreyFor(gillian, facing)) {
            gillian.setTarget(facing);
        }
        return gillian;
    }

    /**
     * Server side, every tick so it all still happens with NoAI: letting go of prey it can no longer
     * hold, the landing, the rise into the sky, and the boss bar.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        long now = level.getGameTime();
        if (feedingOn != null && (!isAlive() || isSeized())) {
            letGo(now);
        }
        if (descending) {
            if (onGround()) {
                land(level, now);
            } else if (now - descendingSince >= DESCENT_SLOW_FALL_TICKS) {
                descending = false;
            }
        }
        if (retreating && isAlive()) {
            long age = now - retreatStart;
            if (age >= RETREAT_TICKS) {
                discard();
                return;
            }
            if (age >= RETREAT_RISE_START) {
                setNoGravity(true);
                setDeltaMovement(Vec3.ZERO);
                setPos(getX(), getY() + RETREAT_RISE_PER_TICK, getZ());
            }
        }
        if (tickCount % BOSS_BAR_INTERVAL == 0) {
            updateBossBar(level);
        }
    }

    /** The parts follow the body on both sides, after it has moved. */
    @Override
    public void aiStep() {
        super.aiStep();
        tickParts();
    }

    /** Driven off by its wounds or by stronger reiatsu; otherwise it looks for an ally to rescue, and feeds. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isRemoved() || !isAlive() || retreating || !(level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (nextFeedAt == NEVER) {
            nextFeedAt = now + FEED_FIRST_DELAY_TICKS;
        }
        if (nextRescueAt == NEVER) {
            nextRescueAt = now + rescueInterval();
        }
        if (getHealth() <= BleachConfig.get(BleachConfig.GILLIAN_RETREAT_HEALTH) * getMaxHealth()
                || ReiatsuFleeGoal.isFrightened(this)) {
            beginRetreat(true);
            if (retreating) {
                return;
            }
        }
        if (feedingOn == null && now >= nextRescueAt) {
            nextRescueAt = now + rescueInterval();
            tryRescue(level, now);
        }
        tickFeeding(level, now);
    }

    /**
     * One look for an ally to rescue: if the switch is on, this Gillian's wait is over and an ally needs it,
     * the chance decides, and then Negación's light comes down on that ally. Whether one came down.
     */
    public boolean tryRescue(ServerLevel level, long now) {
        if (!BleachConfig.get(BleachConfig.NEGACION_MOB_RESCUE) || now < rescueReadyAt) {
            return false;
        }
        Hollow ally = rescueTarget();
        if (ally == null || random.nextDouble() >= BleachConfig.get(BleachConfig.NEGACION_MOB_CHANCE)) {
            return false;
        }
        if (Negacion.lift(level, ally) == null) {
            return false;
        }
        rescueReadyAt = now + BleachConfig.get(BleachConfig.NEGACION_MOB_COOLDOWN_SECONDS) * 20L;
        getLookControl().setLookAt(ally, 30.0F, 30.0F);
        return true;
    }

    /**
     * The ally this Gillian would lift away now, ignoring the chance and the wait: the nearest Hollow in
     * reach and in sight, other than a Gillian, that the light may hold, that is beaten down to the rescue
     * share of its health, and that was last hurt by something other than a Hollow.
     */
    @Nullable
    public Hollow rescueTarget() {
        double reach = BleachConfig.get(BleachConfig.NEGACION_MOB_REACH);
        double beaten = BleachConfig.get(BleachConfig.NEGACION_RESCUE_HEALTH);
        List<Hollow> allies = new ArrayList<>(level().getEntitiesOfClass(Hollow.class,
                getBoundingBox().inflate(reach, RESCUE_VERTICAL_REACH, reach),
                hollow -> hollow != this && !(hollow instanceof Gillian) && Negacion.mayHold(hollow)
                        && hollow.getHealth() <= beaten * hollow.getMaxHealth()
                        && hollow.getLastHurtByMob() != null && !(hollow.getLastHurtByMob() instanceof Hollow)));
        allies.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (Hollow ally : allies) {
            if (hasLineOfSight(ally)) {
                return ally;
            }
        }
        return null;
    }

    private static long rescueInterval() {
        return BleachConfig.get(BleachConfig.NEGACION_MOB_INTERVAL_SECONDS) * 20L;
    }

    /** Pulling back into the sky, it goes after no one. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !retreating && super.canAttack(target);
    }

    /** Frozen as every Hollow is, and while it pulls itself into the sky. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || retreating;
    }

    /** Its long arms reach well past its sides, at any height of its body. */
    @Override
    protected AABB getAttackBoundingBox() {
        return getBoundingBox().inflate(ATTACK_REACH, 0.0, ATTACK_REACH);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        // Null while the entity is being made; the constructor sets the ids again once the parts exist.
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    /** Its nose and arms stick out of its box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.5, 0.5, 1.5);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected float nextStep() {
        return moveDist + STEP_DISTANCE;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.GILLIAN_STEP.get(), STEP_VOLUME, 1.0F);
    }

    @Override
    protected float getSoundVolume() {
        return LOUD;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GILLIAN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GILLIAN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GILLIAN_DEATH.get();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    /** Gone, by unloading or otherwise: no bar is left behind, and no Hollow is left held. */
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossBar.removeAllPlayers();
        letGo(level().getGameTime());
    }

    /** Killed on the way up, it falls as any Hollow does. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        bossBar.removeAllPlayers();
        letGo(level().getGameTime());
        if (retreating && !isAlive()) {
            setNoGravity(false);
        }
    }

    /** The body from above its knees and the head about its mask, placed where the Gillian now stands. */
    private void tickParts() {
        float scale = getScale();
        Part body = parts[PART_BODY];
        Part head = parts[PART_HEAD];
        body.resize(scale);
        head.resize(scale);
        body.placeAt(getX(), getY() + BODY_BOTTOM * scale, getZ());
        Vec3 mask = HollowMask.maskCenter(this);
        head.placeAt(mask.x, mask.y - HEAD_SIZE * scale / 2.0, mask.z);
    }

    /** Down at last: the slow fall ends, it staggers under its own weight, and dust flies. */
    private void land(ServerLevel level, long now) {
        descending = false;
        removeEffect(MobEffects.SLOW_FALLING);
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HEAVY_LANDING_TICKS, SLOWNESS_IV, false, false));
        SpiritSenses.particles(level, ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 30, 1.7, 0.2, 1.7, 0.02);
        playSound(ModSounds.GILLIAN_STEP.get(), LOUD, 0.6F);
        delayCero(CERO_FIRST_DELAY_TICKS);
        nextFeedAt = now + FEED_FIRST_DELAY_TICKS;
    }

    /** Pulls the Hollow on its tongue, or reaches for the next once it is time. */
    private void tickFeeding(ServerLevel level, long now) {
        if (feedingOn != null) {
            pull(level, feedingOn, now);
            return;
        }
        if (now < nextFeedAt || descending || isSeized() || isChargingCero()) {
            return;
        }
        Hollow prey = nearestMeal();
        if (prey == null) {
            nextFeedAt = now + feedInterval();
            return;
        }
        prey.seize(now + FEED_HOLD_TICKS);
        feedingOn = prey;
        feedStart = now;
    }

    /**
     * The nearest lesser Hollow within reach across the ground and in sight: no Menos, none held (in jaws or
     * in Negación's light), dissolving, leaving or across the Shakonmaku, no student's practice Hollow, and none
     * stronger than this Gillian. One a bait holds will do.
     */
    @Nullable
    private Hollow nearestMeal() {
        double reach = BleachConfig.get(BleachConfig.GILLIAN_FEED_REACH);
        float own = SoulLogic.reiatsu(this);
        AABB box = new AABB(getX() - reach, getY() - FEED_VERTICAL_REACH, getZ() - reach,
                getX() + reach, getY() + FEED_VERTICAL_REACH, getZ() + reach);
        List<Hollow> meals = new ArrayList<>(level().getEntitiesOfClass(Hollow.class, box,
                hollow -> !(hollow instanceof Menos) && hollow.isAlive() && !hollow.isSeized() && !hollow.isPurifying()
                        && !hollow.isDeparting() && !hollow.isPractice() && !Negacion.held(hollow)
                        && horizontalDistanceSqr(hollow) <= reach * reach
                        && SoulLogic.reiatsu(hollow) <= own
                        && !ShakonmakuEvents.stops(level(), hollow, hollow.position(), position())));
        meals.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (Hollow meal : meals) {
            if (hasLineOfSight(meal)) {
                return meal;
            }
        }
        return null;
    }

    /** One tick of the tongue: the Hollow rises toward the mouth, and is swallowed once there or once time is up. */
    private void pull(ServerLevel level, Hollow prey, long now) {
        double reach = BleachConfig.get(BleachConfig.GILLIAN_FEED_REACH) + FEED_LET_GO_SLACK;
        if (!prey.isAlive() || prey.isRemoved() || !prey.isSeized() || prey.level() != level
                || horizontalDistanceSqr(prey) > reach * reach
                || Math.abs(prey.getY() - getY()) > FEED_VERTICAL_REACH + FEED_LET_GO_SLACK) {
            letGo(now);
            return;
        }
        Vec3 mouth = ceroMuzzle(1.0F);
        Vec3 centre = prey.getBoundingBox().getCenter();
        Vec3 toward = mouth.subtract(centre);
        double distance = toward.length();
        if (now - feedStart >= FEED_PULL_TICKS || distance <= FEED_SWALLOW_DISTANCE) {
            swallow(level, prey, mouth, now);
            return;
        }
        // Moved, not pushed: a held or NoAI Hollow does not move by its own speed.
        Vec3 step = toward.scale(Math.min(FEED_PULL_SPEED, distance) / distance);
        // The tongue lets go at the Shakonmaku: a prey's own tick never sees a pull, so it is judged here.
        if (ShakonmakuEvents.stops(level, prey, prey.position(), prey.position().add(step))) {
            letGo(now);
            return;
        }
        prey.setPos(prey.getX() + step.x, prey.getY() + step.y, prey.getZ() + step.z);
        prey.setDeltaMovement(Vec3.ZERO);
        if ((now - feedStart) % 2 == 0) {
            for (int i = 0; i < TONGUE_POINTS; i++) {
                Vec3 point = mouth.lerp(centre, (i + 0.5) / TONGUE_POINTS);
                SpiritSenses.particles(level, TONGUE, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** The jaws close on it. One that lives through it is let go. */
    private void swallow(ServerLevel level, Hollow prey, Vec3 mouth, long now) {
        feedingOn = null;
        nextFeedAt = now + feedInterval();
        prey.hurt(DevourLogic.source(level, this), DevourLogic.killingBlow(prey));
        if (prey.isAlive()) {
            prey.release();
            return;
        }
        devour(prey);
        SpiritSenses.play(level, mouth.x, mouth.y, mouth.z, ModSounds.HOLLOW_DEVOUR.get(), getSoundSource(), 2.0F, 0.6F,
                false);
        SpiritSenses.particles(level, ParticleTypes.SQUID_INK, mouth.x, mouth.y, mouth.z, 12, 0.4, 0.4, 0.4, 0.02);
    }

    /** Lets go of the Hollow on its tongue, if any, and waits before reaching for another. */
    private void letGo(long now) {
        Hollow prey = feedingOn;
        if (prey == null) {
            return;
        }
        feedingOn = null;
        nextFeedAt = now + feedInterval();
        if (prey.isAlive()) {
            prey.release();
        }
    }

    private static long feedInterval() {
        return BleachConfig.get(BleachConfig.GILLIAN_FEED_INTERVAL_SECONDS) * 20L;
    }

    private double horizontalDistanceSqr(Entity other) {
        double dx = other.getX() - getX();
        double dz = other.getZ() - getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Breaks the leaves around its body, nearest its feet first and no more than a dozen at a time,
     * where mob griefing and the config allow and nothing protects them. Nothing else breaks.
     */
    private void trample(ServerLevel level) {
        if (!BleachConfig.get(BleachConfig.GILLIAN_TRAMPLES_LEAVES) || !EventHooks.canEntityGrief(level, this)) {
            return;
        }
        AABB box = getBoundingBox().inflate(0.6, 0.0, 0.6);
        List<BlockPos> leaves = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY + 1.0), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
                leaves.add(pos.immutable());
            }
        }
        leaves.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(getX(), getY(), getZ())));
        int broken = 0;
        for (BlockPos pos : leaves) {
            if (broken >= TRAMPLE_BUDGET) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (GetsugaTensho.permitted(level, pos, state, this, this, true)
                    && EventHooks.onEntityDestroyBlock(this, pos, state) && level.destroyBlock(pos, true, this)) {
                broken++;
            }
        }
    }

    /** Shows its health to the aware within range, and to no one else. */
    private void updateBossBar(ServerLevel level) {
        if (!isAlive()) {
            bossBar.removeAllPlayers();
            return;
        }
        bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        List<ServerPlayer> aware = SpiritSenses.awareNear(level, position(), BOSS_BAR_RANGE);
        for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
            if (!aware.contains(player)) {
                bossBar.removePlayer(player);
            }
        }
        for (ServerPlayer player : aware) {
            bossBar.addPlayer(player);
        }
    }

    /** Walks straight at its prey and swats it: a Gillian has no paths to follow. */
    static final class StrideGoal extends Goal {
        private final Gillian gillian;
        /** Ticks in a row it has been walking into something. */
        private int stuck;

        StrideGoal(Gillian gillian) {
            this.gillian = gillian;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = gillian.getTarget();
            return target != null && target.isAlive() && gillian.canAttack(target) && !gillian.descending
                    && !gillian.retreating && gillian.level().getGameTime() >= gillian.restUntil;
        }

        @Override
        public boolean canContinueToUse() {
            return stuck < STRIDE_GIVE_UP_TICKS && canUse();
        }

        @Override
        public void start() {
            stuck = 0;
        }

        @Override
        public void stop() {
            stuck = 0;
            gillian.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = gillian.getTarget();
            if (target == null) {
                return;
            }
            gillian.getLookControl().setLookAt(target, STRIDE_TURN, STRIDE_TURN);
            boolean inReach = gillian.isWithinMeleeAttackRange(target);
            if (inReach) {
                stuck = 0;
            } else {
                // Set again every tick: the move control stops after one tick without a new aim.
                gillian.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
                stuck = gillian.horizontalCollision ? stuck + 1 : 0;
                if (gillian.horizontalCollision && gillian.tickCount % TRAMPLE_INTERVAL == 0
                        && gillian.level() instanceof ServerLevel level) {
                    gillian.trample(level);
                }
            }
            if (inReach && gillian.attackReady() && gillian.hasLineOfSight(target)) {
                gillian.swing(InteractionHand.MAIN_HAND);
                gillian.doHurtTarget(target);
            }
            if (stuck >= STRIDE_GIVE_UP_TICKS) {
                gillian.restUntil = gillian.level().getGameTime() + STRIDE_REST_TICKS;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /**
     * Ground navigation that never finds a path, for a body too big for any. Every way of asking for
     * a path ends here, so nothing (not even fleeing from reiatsu) ever walks a Gillian by a path.
     */
    static final class Navigation extends GroundPathNavigation {
        Navigation(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        @Nullable
        protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy,
                                  float followRange) {
            return null;
        }
    }

    /** One of the Gillian's two hit boxes, a body above its knees and a head about its mask (see {@link GiantPart}). */
    public static final class Part extends GiantPart<Gillian> {
        Part(Gillian parent, float width, float height) {
            super(parent, width, height);
        }
    }
}
