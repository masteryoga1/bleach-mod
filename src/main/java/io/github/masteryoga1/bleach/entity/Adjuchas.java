package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An Adjuchas, a Menos Grande that has won a shape of its own by devouring: a quick, clever beast,
 * several times a Gillian's power, that hunts in packs. Each looks a little different (see
 * {@link #variant()}).
 *
 * <p>A pack has no saved leader: the one with the lowest entity id nearby leads ({@link #packLeader()}),
 * the rest follow it, and what one hunts, all hunt. Only one in a pack gathers a Cero at a time. Badly
 * hurt, an Adjuchas turns on weaker Hollows nearby and eats each one it kills to heal (our own rule:
 * canon never shows it).
 */
public class Adjuchas extends Menos {
    /** How far the pack reaches, for its leader, its shared prey and its one Cero at a time. */
    public static final double PACK_RANGE = 24.0;
    /** Further than this from its leader it follows; within this it stops. */
    public static final double FOLLOW_FAR = 10.0;
    public static final double FOLLOW_NEAR = 5.0;
    public static final int FOLLOW_REPATH_TICKS = 10;
    /** How often one with prey gives it to the pack. */
    public static final int SHARE_INTERVAL = 20;
    /** How far a hungry one looks for a weaker Hollow to eat. */
    public static final double HUNT_RANGE = 16.0;
    /** The size of the tear a pack comes through. */
    public static final float ARRIVAL_TEAR_SIZE = 1.5F;
    /** How high it leaps at its prey. */
    public static final float LEAP = 0.5F;
    public static final int VARIANT_HORNS = 0;
    public static final int VARIANT_SPIKES = 1;
    public static final int VARIANT_TAIL = 2;
    public static final int VARIANTS = 3;

    private static final float VOLUME = 1.5F;

    public Adjuchas(EntityType<? extends Adjuchas> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.ADJUCHAS.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.ADJUCHAS;
    }

    /** A beast's goals, with the Cero first, the pack and its hunger; no gathering at a bait. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CeroGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, LEAP));
        goalSelector.addGoal(3, new HollowMeleeGoal(this));
        goalSelector.addGoal(4, new FollowLeaderGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, hurtByTargetGoal());
        targetSelector.addGoal(2, new HollowHuntGoal(this));
        targetSelector.addGoal(3, new HungerGoal(this));
    }

    /** Its Cero, the same at every size. */
    @Override
    protected CeroBeam.Spec ceroSpec() {
        // The [hollows] damage multiplier reaches the Cero too, as it does every Hollow's blows.
        return new CeroBeam.Spec((float) (BleachConfig.get(BleachConfig.ADJUCHAS_CERO_DAMAGE)
                        * BleachConfig.get(BleachConfig.HOLLOW_DAMAGE_MULTIPLIER)),
                BleachConfig.get(BleachConfig.ADJUCHAS_CERO_RANGE),
                (float) BleachConfig.get(BleachConfig.ADJUCHAS_CERO_RADIUS),
                (float) BleachConfig.get(BleachConfig.ADJUCHAS_CERO_BLAST_RADIUS),
                (float) BleachConfig.get(BleachConfig.CERO_BLAST_SHARE),
                BleachConfig.get(BleachConfig.ADJUCHAS_CERO_MAX_BLOCKS),
                (float) BleachConfig.get(BleachConfig.ADJUCHAS_CERO_KNOCKBACK),
                CeroBeam.MENOS_RED,
                BleachConfig.get(BleachConfig.ADJUCHAS_CERO_CHARGE_TICKS),
                BleachConfig.get(BleachConfig.ADJUCHAS_CERO_AIM_LOCK_TICKS),
                BleachConfig.get(BleachConfig.CERO_BREAKS_BLOCKS));
    }

    @Override
    protected double ceroMinRange() {
        return BleachConfig.get(BleachConfig.ADJUCHAS_CERO_MIN_RANGE);
    }

    @Override
    protected int ceroCooldownTicks() {
        return BleachConfig.get(BleachConfig.ADJUCHAS_CERO_COOLDOWN_SECONDS) * 20;
    }

    /** One Cero in the pack at a time. */
    @Override
    protected boolean mayChargeNow() {
        for (Adjuchas mate : packMates()) {
            if (mate.isChargingCero()) {
                return false;
            }
        }
        return true;
    }

    /** It goes back through a small tear, as a lesser Hollow does. */
    @Override
    protected void onLifetimeOver() {
        depart();
    }

    /** Every second, one with prey gives it to the pack. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isRemoved() && isAlive() && !isDeparting() && tickCount % SHARE_INTERVAL == 0 && getTarget() != null) {
            shareTarget();
        }
    }

    /** Below its share of health, it hunts weaker Hollows to eat. */
    public boolean isHungry() {
        return getHealth() < BleachConfig.get(BleachConfig.ADJUCHAS_HUNGER_HEALTH) * getMaxHealth();
    }

    /**
     * Whether, hungry, it would eat this: a Hollow weaker than itself, alive, not dissolving or
     * leaving, and never a Menos.
     */
    public boolean isEdible(LivingEntity entity) {
        return isHungry() && entity instanceof Hollow hollow && !(hollow instanceof Menos) && hollow.isAlive()
                && !hollow.isPurifying() && !hollow.isDeparting() && SoulLogic.reiatsu(hollow) < SoulLogic.reiatsu(this);
    }

    /** The pack's leader: of every Adjuchas within the pack's range, itself too, the one with the lowest id. */
    public Adjuchas packLeader() {
        Adjuchas leader = this;
        for (Adjuchas other : level().getEntitiesOfClass(Adjuchas.class, getBoundingBox().inflate(PACK_RANGE),
                other -> other.isAlive() && !other.isDeparting())) {
            if (other.getId() < leader.getId()) {
                leader = other;
            }
        }
        return leader;
    }

    /** The others of its pack: every other Adjuchas within the pack's range, alive and not leaving. */
    public List<Adjuchas> packMates() {
        return level().getEntitiesOfClass(Adjuchas.class, getBoundingBox().inflate(PACK_RANGE),
                other -> other != this && other.isAlive() && !other.isDeparting());
    }

    /** Gives its prey to every pack mate that has none and may hunt it. */
    public void shareTarget() {
        LivingEntity prey = getTarget();
        if (prey == null || !prey.isAlive()) {
            return;
        }
        for (Adjuchas mate : packMates()) {
            if (mate.getTarget() == null && mate.canAttack(prey)) {
                mate.setTarget(prey);
            }
        }
    }

    /** Which of its forms it has: horns, dorsal spikes or a long tail. Clients know the id from the spawn packet. */
    public int variant() {
        return Math.floorMod(getUUID().hashCode(), VARIANTS);
    }

    /** No kinship with a Hollow it would eat. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity instanceof LivingEntity living && isEdible(living)) {
            return false;
        }
        return super.isAlliedTo(entity);
    }

    /** As every Hollow, and a Hollow it would eat besides. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return isEdible(target) || super.canAttack(target);
    }

    /** A lesser Hollow it kills, it eats: healing, and growing in power up to twice its kind's. */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        if (victim instanceof Hollow hollow && !(hollow instanceof Menos)
                && SoulLogic.reiatsu(hollow) < SoulLogic.reiatsu(this)) {
            devour(hollow);
            playSound(ModSounds.HOLLOW_DEVOUR.get(), VOLUME, 0.8F);
            Vec3 mask = HollowMask.maskCenter(this);
            SpiritSenses.particles(level, ParticleTypes.SQUID_INK, mask.x, mask.y, mask.z, 8, 0.2, 0.2, 0.2, 0.02);
        }
        return super.killedEntity(level, victim);
    }

    @Override
    protected float getSoundVolume() {
        return VOLUME;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.ADJUCHAS_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ADJUCHAS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ADJUCHAS_DEATH.get();
    }

    /** With no prey, it keeps up with its pack's leader. */
    static final class FollowLeaderGoal extends Goal {
        private final Adjuchas adjuchas;
        @Nullable
        private Adjuchas leader;
        private int repathIn;

        FollowLeaderGoal(Adjuchas adjuchas) {
            this.adjuchas = adjuchas;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (adjuchas.getTarget() != null) {
                return false;
            }
            Adjuchas lead = adjuchas.packLeader();
            if (lead == adjuchas || adjuchas.distanceToSqr(lead) <= FOLLOW_FAR * FOLLOW_FAR) {
                return false;
            }
            leader = lead;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && leader.isAlive() && !leader.isDeparting() && leader.level() == adjuchas.level()
                    && adjuchas.getTarget() == null && adjuchas.distanceToSqr(leader) > FOLLOW_NEAR * FOLLOW_NEAR;
        }

        @Override
        public void start() {
            repathIn = 0;
        }

        @Override
        public void tick() {
            if (leader != null && --repathIn <= 0) {
                repathIn = reducedTickDelay(FOLLOW_REPATH_TICKS);
                adjuchas.getNavigation().moveTo(leader, 1.0);
            }
        }

        @Override
        public void stop() {
            leader = null;
            adjuchas.getNavigation().stop();
        }
    }

    /** Hungry, it hunts the nearest weaker Hollow in sight within {@link #HUNT_RANGE}. */
    static final class HungerGoal extends NearestAttackableTargetGoal<Hollow> {
        private final Adjuchas adjuchas;

        HungerGoal(Adjuchas adjuchas) {
            super(adjuchas, Hollow.class, 10, true, false, adjuchas::isEdible);
            this.adjuchas = adjuchas;
        }

        @Override
        public boolean canUse() {
            return adjuchas.isHungry() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return adjuchas.isHungry() && super.canContinueToUse();
        }

        /** A constant: it is read while the goal is made, before this goal's own fields are set. */
        @Override
        protected double getFollowDistance() {
            return HUNT_RANGE;
        }
    }
}
