package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.FlashStep;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.ReiatsuEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * A Vasto Lorde: the highest class of Menos Grande, as small as a person and stronger than a captain. It is no
 * {@link Menos}, so none of their cap, bait or lifetime rules is its, and no Menos count, arrival or meal takes it (see
 * {@link HollowKind#isVasto}). It hunts as any Hollow does, with its wits about it, and released reiatsu never
 * frightens it off. Its health and blow are the server's to set; its armour is the heaviest of any Hollow's,
 * toughened against heavy blows. It is no sinner and never saved, so it goes with its chunk, or, out of Hueco Mundo,
 * through a tear for lack of prey. It comes by itself only far out on Hueco Mundo's dunes, a few at a time
 * ({@code VastoLordeComing}); otherwise a command brings it: {@code /summon}, or {@code /bleach garganta}, whose tear
 * brings it alone.
 *
 * <p>Its white bar shows to the souls about it who see spirits. Each has one of three looks of the mod's own, rolled as
 * it spawns ({@link #look}): bone plates over its shoulders and chest, a horned mask, or a crested one. The look is sent
 * to every client, so each draws the same, and is never saved, as the body is never saved; a summon's data may name it
 * ({@code Look}).
 *
 * <p>It fights as no lesser Hollow does. It gathers a Cero at its brow, between the horns, for three quarters of a
 * second, standing and facing its prey, and its aim holds still for the last quarter ({@link #startCero}). It rushes
 * along the ground at prey too far for its blow ({@link #dash}), never into a wall, off a ledge or into the void, and its
 * presence weighs on everything about it as released reiatsu would, withering the small Hollows and the Pluses it dwarfs
 * ({@link #pulsePresence}). None of it is saved, as the body is not.
 */
public class VastoLorde extends Hollow implements CeroBeam.Caster {
    /** How much of a heavy blow its armour still turns, on top of the armour itself. */
    public static final double TOUGHNESS = 2.0;
    /** Its three looks, and how many there are. */
    public static final int PLATED = 0;
    public static final int HORNED = 1;
    public static final int CRESTED = 2;
    public static final int LOOKS = 3;
    /** The tag of a summon's data that names its look. Read, never written. */
    public static final String LOOK_TAG = "Look";
    /** How long its aim holds still before the Cero fires: a step aside in this last quarter second dodges it. */
    public static final int CERO_AIM_LOCK_TICKS = 5;
    /** The nearest across the ground its prey may be for a Cero: nearer, it strikes instead. */
    public static final double CERO_MIN_RANGE = 4.0;
    /** It rushes at prey at least this far off across the ground, and stops this far short of it. */
    public static final double DASH_FROM = 8.0;
    public static final double DASH_SHORT = 2.0;
    /** How often its presence beats, in ticks. */
    public static final int PRESENCE_INTERVAL_TICKS = 20;
    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(VastoLorde.class,
            EntityDataSerializers.INT);
    private static final long NEVER = ShinigamiProgress.NEVER;
    /** One just come waits this long after its first step before its first Cero, and a broken gather this long again. */
    private static final int CERO_FIRST_DELAY_TICKS = 40;
    private static final int CERO_RETRY_TICKS = 40;
    /** Up to this many ticks are added at random to the Cero's cooldown. */
    private static final int CERO_COOLDOWN_JITTER = 20;
    /**
     * After a refusal its goal asks again no sooner than this many ticks on. The engine asks goals every second tick, so
     * prey behind a wall costs one look in six ticks rather than one in two.
     */
    private static final int CERO_ASK_TICKS = 5;
    /** How far above the middle of its mask the Cero gathers, as a share of the mask: at the brow, between the horns. */
    private static final double BROW_RISE = 0.3;
    private static final float LOOK_TURN = 30.0F;
    /** How far ahead the head looks along a locked aim. */
    private static final double LOCKED_LOOK_DISTANCE = 8.0;
    /** One just come waits this long after its first step before its first rush, and a refused rush this long again. */
    private static final int DASH_FIRST_DELAY_TICKS = 10;
    private static final int DASH_RETRY_TICKS = 10;
    /** The least a rush goes. */
    private static final double DASH_MIN = 1.0;

    private final BossBar bar = new BossBar(this, "bossbar.bleach.vasto_lorde", BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS);
    // Server side and never saved. NEVER until its first step: a still body may gather at once, and one yet to step may
    // rush, so tests may call them.
    private long ceroReadyAt = NEVER;
    private long dashReadyAt = NEVER;
    @Nullable
    private CeroBeam charging;

    public VastoLorde(EntityType<? extends VastoLorde> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.VASTO_LORDE.attributes().add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS);
    }

    /**
     * The Vasto Lorde alive in this world's loaded chunks, however they came. One in a chunk on its way out is not
     * counted: it goes with its chunk, as none is saved.
     */
    public static int aliveIn(ServerLevel level) {
        return level.getEntities(ModEntities.VASTO_LORDE.get(), Entity::isAlive).size();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.VASTO_LORDE;
    }

    /** Every Hollow's goals, and its Cero before its blow. */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new CeroGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOOK, PLATED);
    }

    /** Spawning rolls its look, each of the three alike; one that skipped spawning keeps the plated look. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setLook(level.getRandom().nextInt(LOOKS));
        return data;
    }

    /** Which of its three looks it has: {@link #PLATED}, {@link #HORNED} or {@link #CRESTED}. */
    public int look() {
        return entityData.get(DATA_LOOK);
    }

    /** Gives it this look, any number taken round the three. */
    public void setLook(int look) {
        entityData.set(DATA_LOOK, Math.floorMod(look, LOOKS));
    }

    /** A summon's data may name its look; nothing writes it, as the body is never saved. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(LOOK_TAG, Tag.TAG_ANY_NUMERIC)) {
            setLook(tag.getInt(LOOK_TAG));
        }
    }

    /** Moved to another world by command, it comes there as a copy of itself, in its look. */
    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof VastoLorde old) {
            setLook(old.look());
        }
    }

    /** Its health and the damage of its blow are the config's ([bosses.vasto_lorde]). */
    @Override
    protected ConfigBases configBases() {
        return new ConfigBases(BleachConfig.get(BleachConfig.VASTO_LORDE_HEALTH),
                BleachConfig.get(BleachConfig.VASTO_LORDE_DAMAGE));
    }

    /** Server side, every tick, so it runs with NoAI too: its bar, last, so the bar shows the health this tick left. */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            bar.tick();
        }
    }

    /**
     * Its first step starts the waits for a first Cero, after its goals have had their turn ({@link #mayCero}), and for a
     * first rush, before this step asks for one. Each second its presence beats. Then, at prey in sight too far for its
     * blow but within a rush and two blocks, it rushes to stand two blocks short of it, and a refused rush waits a moment.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isRemoved() || !isAlive() || !(level() instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        if (ceroReadyAt == NEVER) {
            ceroReadyAt = now + CERO_FIRST_DELAY_TICKS;
        }
        if (dashReadyAt == NEVER) {
            dashReadyAt = now + DASH_FIRST_DELAY_TICKS;
        }
        if (tickCount % PRESENCE_INTERVAL_TICKS == 0) {
            pulsePresence();
        }
        LivingEntity target = getTarget();
        if (target == null || charging != null || now < dashReadyAt || !target.isAlive() || !canAttack(target)) {
            return;
        }
        double across = Math.sqrt(Mth.square(target.getX() - getX()) + Mth.square(target.getZ() - getZ()));
        boolean rushes = across >= DASH_FROM && across <= BleachConfig.get(BleachConfig.VASTO_LORDE_DASH_BLOCKS) + DASH_SHORT
                && getSensing().hasLineOfSight(target);
        if (rushes && !dash(target.position().subtract(position()), across - DASH_SHORT)) {
            dashReadyAt = now + DASH_RETRY_TICKS;
        }
    }

    // ---- Its Cero ----

    /**
     * Its Cero: a Vasto Lorde player's width, blast, blocks, knockback, reach and gather (0.75 seconds), with its own damage
     * ([bosses.vasto_lorde] cero_damage, times the hollows damage multiplier) and an aim that holds for the last quarter
     * second. It breaks soft blocks only where [menos] cero_breaks_blocks and mob griefing allow.
     */
    public CeroBeam.Spec ceroSpec() {
        CeroBeam.Spec form = HollowCero.spec(soul(), HollowTier.VASTO_LORDE);
        float damage = (float) (BleachConfig.get(BleachConfig.VASTO_LORDE_CERO_DAMAGE)
                * BleachConfig.get(BleachConfig.HOLLOW_DAMAGE_MULTIPLIER));
        return new CeroBeam.Spec(damage, form.range(), form.radius(), form.blastRadius(), form.blastShare(), form.maxBlocks(),
                form.knockback(), form.colour(), form.chargeTicks(), CERO_AIM_LOCK_TICKS, form.breaksBlocks());
    }

    /**
     * Whether it may gather a Cero at this prey now: live prey its beam would strike, at least {@link #CERO_MIN_RANGE}
     * off across the ground and within the beam's reach, in sight, with its waits over, none gathering, nothing keeping
     * it from gathering and nothing solid at its brow. With its wits about it, not until its first step has begun the
     * first wait (its goals ask before that in the step), so prey given as it comes is not shot at once; a still body may
     * at once. The cheap checks first, as its goal asks often.
     */
    public boolean mayCero(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || charging != null || ceroReadyAt == NEVER && !isNoAi()
                || level().getGameTime() < ceroReadyAt || !keepsCharging() || !canAttack(target)
                || !CeroBeam.mayStrike(this, target)) {
            return false;
        }
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        Vec3 muzzle = ceroMuzzle(1.0F);
        double range = BleachConfig.get(BleachConfig.CERO_RANGE);
        BlockPos brow = BlockPos.containing(muzzle);
        return dx * dx + dz * dz >= CERO_MIN_RANGE * CERO_MIN_RANGE
                && muzzle.distanceToSqr(target.getBoundingBox().getCenter()) <= range * range && hasLineOfSight(target)
                && level().getBlockState(brow).getCollisionShape(level(), brow).isEmpty();
    }

    /** Starts a Cero gathering at its prey where it stands, if it may ({@link #mayCero}). Returns the beam, or null. */
    @Nullable
    public CeroBeam startCero() {
        if (!(level() instanceof ServerLevel server) || !mayCero(getTarget())) {
            return null;
        }
        charging = CeroBeam.charge(server, this, ceroSpec());
        if (charging == null) {
            onCeroCancelled();
        } else {
            getNavigation().stop();
        }
        return charging;
    }

    /** The Cero gathering at its brow, if one is. Known on the server only. */
    @Nullable
    public CeroBeam chargingBeam() {
        return charging;
    }

    /** At its brow, between the horns: just before its mask's face and a little above its middle, along where it looks. */
    @Override
    public Vec3 ceroMuzzle(float partialTick) {
        HollowKind.MaskSpot mask = kind().mask;
        double scale = getScale();
        Vec3 forward = Vec3.directionFromRotation(0.0F, Mth.rotLerp(partialTick, yHeadRotO, yHeadRot));
        return getPosition(partialTick).add(forward.scale((mask.forward() + mask.size() / 2.0 + Menos.MUZZLE_AHEAD) * scale))
                .add(0.0, (mask.up() + mask.size() * BROW_RISE) * scale, 0.0);
    }

    /** From its brow to the middle of its prey, or null without live prey in sight, asked afresh each time. */
    @Override
    @Nullable
    public Vec3 ceroAim() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !hasLineOfSight(target)) {
            return null;
        }
        Vec3 toward = target.getBoundingBox().getCenter().subtract(ceroMuzzle(1.0F));
        return toward.lengthSqr() < 1.0E-6 ? null : toward.normalize();
    }

    /** A gather holds while it stands free: alive, not held, leaving, masking or dissolving, nor reeling from its mask. */
    @Override
    public boolean keepsCharging() {
        return isAlive() && !isImmobile() && !isPurifying() && level().getGameTime() - lastReelAt >= REEL_TICKS;
    }

    @Override
    public void onCeroFired(CeroBeam.Shot shot) {
        long cooldown = BleachConfig.get(BleachConfig.VASTO_LORDE_CERO_COOLDOWN_SECONDS) * 20L;
        ceroReadyAt = level().getGameTime() + cooldown + random.nextInt(CERO_COOLDOWN_JITTER + 1);
        charging = null;
    }

    @Override
    public void onCeroCancelled() {
        ceroReadyAt = level().getGameTime() + CERO_RETRY_TICKS;
        charging = null;
    }

    /** A fresh crack in its mask also breaks a gathering Cero. */
    @Override
    public boolean onMaskStruck(long now) {
        boolean fresh = super.onMaskStruck(now);
        if (fresh && charging != null && charging.isCharging()) {
            charging.cancel();
        }
        return fresh;
    }

    // ---- Its rush ----

    /**
     * A rush along the ground this way, at most {@code distance} and [bosses.vasto_lorde] dash_blocks, landing at least a
     * block off on footing it can stand on, past nothing solid, harmful or unloaded, as a flash step's sweep finds it
     * ({@link FlashStep#sweep}): never into or through a wall, off a ledge or into the void. Refused without AI, while
     * dead, held, leaving, riding, gathering a Cero, under Gritz or a Bakudō's hold, or before its cooldown is over.
     * Returns whether it rushed.
     */
    public boolean dash(Vec3 direction, double distance) {
        if (!(level() instanceof ServerLevel server) || isNoAi() || !isAlive() || isImmobile() || isPassenger()
                || charging != null || hasEffect(ModEffects.GRITZ) || hasEffect(ModEffects.BAKUDO_HELD)
                || server.getGameTime() < dashReadyAt) {
            return false;
        }
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        double step = Math.min(distance, BleachConfig.get(BleachConfig.VASTO_LORDE_DASH_BLOCKS));
        if (flat.lengthSqr() < 1.0E-6 || !(step >= DASH_MIN)) {
            return false;
        }
        Vec3 start = position();
        FlashStep.DashPath path = FlashStep.sweep(server, this, getBoundingBox(), flat.normalize(), step, true);
        Vec3 end = path.end();
        if (!path.supported() || !Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z)
                || end.distanceTo(start) < DASH_MIN) {
            return false;
        }
        teleportTo(end.x, end.y, end.z);
        getNavigation().stop();
        resetFallDistance();
        dust(server, start);
        dust(server, end);
        SpiritSenses.play(server, start.x, start.y, start.z, ModSounds.VASTO_LORDE_DASH.get(), getSoundSource(), 1.0F, 1.0F,
                false);
        dashReadyAt = server.getGameTime() + BleachConfig.get(BleachConfig.VASTO_LORDE_DASH_COOLDOWN_SECONDS) * 20L;
        return true;
    }

    /** White ash and a puff where it rushed from and where it lands, for those who see spirits. */
    private static void dust(ServerLevel level, Vec3 feet) {
        SpiritSenses.particles(level, ParticleTypes.WHITE_ASH, feet.x, feet.y + 0.8, feet.z, 16, 0.3, 0.6, 0.3, 0.0);
        SpiritSenses.particles(level, ParticleTypes.POOF, feet.x, feet.y + 0.1, feet.z, 4, 0.25, 0.05, 0.25, 0.0);
    }

    // ---- Its presence ----

    /**
     * One beat of its presence ({@link ReiatsuEvents#presence}) at its own reiatsu, out to [bosses.vasto_lorde]
     * presence_radius, withering presence_harm; none at a radius of 0. It beats each second from its AI step only, so
     * never while it is still (NoAI), masking, leaving, seized or held by Negación ({@link #isImmobile}). A Bakudō's
     * hold pins its body but not its AI step, so it stops its rush but not its presence. Returns how many it withered.
     */
    public int pulsePresence() {
        double radius = BleachConfig.get(BleachConfig.VASTO_LORDE_PRESENCE_RADIUS);
        return radius <= 0.0 ? 0 : ReiatsuEvents.presence(this, SoulLogic.reiatsu(this), radius,
                (float) BleachConfig.get(BleachConfig.VASTO_LORDE_PRESENCE_HARM));
    }

    /** A soul that stops seeing it loses its bar at once. */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.forget(player);
    }

    /** Its bar. */
    public BossBar bossBar() {
        return bar;
    }

    /** It goes when it is killed, leaves for lack of prey or its chunk unloads, never because players are far. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /**
     * Its Cero as a goal, so it stands and faces its prey while the Cero gathers: it starts when {@link #mayCero} allows,
     * asking again only a few ticks after a refusal, and nothing breaks it off but the gather breaking. The head follows
     * the prey until the aim locks, then holds.
     */
    public static final class CeroGoal extends Goal {
        private final VastoLorde lorde;
        private long nextAskAt;
        private int lockAt;
        @Nullable
        private Vec3 lockedLook;

        CeroGoal(VastoLorde lorde) {
            this.lorde = lorde;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            long now = lorde.level().getGameTime();
            if (now < nextAskAt) {
                return false;
            }
            boolean may = lorde.mayCero(lorde.getTarget());
            nextAskAt = may ? now : now + CERO_ASK_TICKS;
            return may;
        }

        @Override
        public boolean canContinueToUse() {
            return lorde.charging != null && lorde.charging.isCharging();
        }

        @Override
        public void start() {
            CeroBeam.Spec spec = lorde.ceroSpec();
            lockAt = spec.chargeTicks() - spec.aimLockTicks();
            lockedLook = null;
            lorde.startCero();
        }

        @Override
        public void tick() {
            CeroBeam beam = lorde.charging;
            LivingEntity target = lorde.getTarget();
            if (beam == null) {
                return;
            }
            if (lockedLook == null && beam.tickCount >= lockAt) {
                Vec3 aim = lorde.ceroAim();
                lockedLook = aim == null ? null : lorde.ceroMuzzle(1.0F).add(aim.scale(LOCKED_LOOK_DISTANCE));
            }
            if (lockedLook != null) {
                lorde.getLookControl().setLookAt(lockedLook.x, lockedLook.y, lockedLook.z, LOOK_TURN, LOOK_TURN);
            } else if (target != null) {
                lorde.getLookControl().setLookAt(target, LOOK_TURN, LOOK_TURN);
            }
        }

        @Override
        public void stop() {
            if (lorde.charging != null && lorde.charging.isCharging()) {
                lorde.charging.cancel();
            }
            lorde.charging = null;
            lockedLook = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
    }
}
