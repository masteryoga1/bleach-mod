package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shrieker: a great winged Hollow, ape-like and bat-like, and a boss that hunts from the air. It circles high over its
 * prey, out of reach of a blade swung from the ground, swoops on it when its claws are ready, and keeps its claws low a
 * while after, its mask where a blade finds it, before it climbs back to its circle (see {@link ShriekerFlightGoal}). A
 * blow to its mask that makes it reel breaks off a swoop and drops it to that low height for as long. It moves against
 * blocks as any mob does, so walls, Sekkiseki and the Shakonmaku hold it. Its bar shows to the souls about it who see
 * spirits. It is always a sinner, as it was a killer in life. Its health and the damage of its swoop are the server's to
 * set. It is never saved, and for now comes only by command.
 */
public class Shrieker extends Hollow {
    /** How far apart its wingbeats are, in ticks: the model's beat and the sound of it keep this time together. */
    public static final int FLAP_TICKS = 18;
    /** Its flying speed in blocks a tick at a modifier of 1, before what slows it. */
    public static final double FLYING_SPEED = 0.35;
    /**
     * Its own synced flag: diving at its prey. Bit 16 is each kind's own past Hollow's four (a Menos keeps its Cero's
     * charge there); 32, 64 and 128 are still free for every Hollow.
     */
    private static final int FLAG_SWOOPING = 16;
    /** How far past its body its wings reach, in blocks, so it is still drawn while only a wing is in view. */
    private static final double WING_REACH = 1.5;
    private static final float FLAP_VOLUME = 0.6F;

    private final BossBar bar = new BossBar(this, "bossbar.bleach.shrieker", BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    /** Whether its health and damage are the config's yet. Not saved: it never is. */
    private boolean basesSet;
    /** The game time until which a blow to its mask holds it low and keeps it from swooping. Not saved. */
    private long stalledUntil;

    /** Its own move control, which reads none of its fields, so it may be made here (see {@link Hollow}). */
    public Shrieker(EntityType<? extends Shrieker> type, Level level) {
        super(type, level);
        moveControl = new ShriekerMoveControl(this);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.SHRIEKER.attributes().add(Attributes.FLYING_SPEED, FLYING_SPEED);
    }

    @Override
    public HollowKind kind() {
        return HollowKind.SHRIEKER;
    }

    /** It finds its way through the air. Made in the mob's constructor, so from constants only. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    /**
     * Its own goals in place of a Hollow's: the fight in the air for the bite on foot and the leap, a flyer's wander for
     * the walk, and no gathering to a bait, which never holds a Hollow that comes only by command. Its prey is sought as
     * far up and down as across.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ShriekerFlightGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, hurtByTargetGoal());
        targetSelector.addGoal(2, new HollowHuntGoal(this, true));
    }

    /** Its health and damage from the config, as the base its size, the difficulty and the multipliers work on. */
    @Override
    public void applySpawnStats(Difficulty difficulty, float scale) {
        setBases();
        super.applySpawnStats(difficulty, scale);
    }

    private void setBases() {
        basesSet = true;
        setBase(Attributes.MAX_HEALTH, BleachConfig.get(BleachConfig.SHRIEKER_HEALTH));
        setBase(Attributes.ATTACK_DAMAGE, BleachConfig.get(BleachConfig.SHRIEKER_DAMAGE));
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Server side, every tick, so it runs with NoAI too: first the air holding it up or letting it fall, before it
     * moves; then the config's strength for one that skipped spawning (a summon with data, a game test), whole if it was
     * whole, though without the difficulty and multipliers spawning adds; its wingbeat while it flies; and its bar,
     * last, so the bar shows the health this tick left.
     */
    @Override
    public void tick() {
        if (!level().isClientSide) {
            boolean aloft = holdsAloft();
            setNoGravity(aloft);
            if (aloft && !isAlive()) {
                // Held up in death, it stays where the blow found it: neither its dive nor the blow's push carries it on.
                setDeltaMovement(Vec3.ZERO);
            }
        }
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!basesSet && isAlive()) {
            boolean whole = getHealth() >= getMaxHealth();
            setBases();
            if (whole) {
                setHealth(getMaxHealth());
            }
        }
        if (isAlive() && !onGround() && !isSwooping() && (level().getGameTime() + getId() * 3L) % FLAP_TICKS == 0) {
            playSound(ModSounds.SHRIEKER_FLAP.get(), FLAP_VOLUME, 0.9F + random.nextFloat() * 0.2F);
        }
        bar.tick();
    }

    /**
     * Whether the air holds it up: while it lives, and while it dissolves, purified, so it hangs where the blade found it
     * and its soul rises there; one slain falls. A kind of end that keeps the body would hold it up here too.
     */
    protected boolean holdsAloft() {
        return isAlive() || isPurifying();
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

    /** A blow to the mask that makes it reel also holds it low while it reels, and ends a swoop. */
    @Override
    public boolean onMaskStruck(long now) {
        boolean fresh = super.onMaskStruck(now);
        if (lastReelAt == now) {
            stalledUntil = now + REEL_TICKS;
        }
        return fresh;
    }

    /** Whether a blow to its mask holds it now, low and with no swoop. Known on the server only. */
    public boolean isStalled() {
        return level().getGameTime() < stalledUntil;
    }

    /** Whether it is diving at its prey, on both sides, for the model. */
    public boolean isSwooping() {
        return getFlag(FLAG_SWOOPING);
    }

    /** Set by its fight as a dive begins and ends; public for the tests. */
    public void setSwooping(boolean swooping) {
        setFlag(FLAG_SWOOPING, swooping);
    }

    /** Every Shrieker was a sinner in life, whatever its data says. */
    @Override
    public boolean isSinner() {
        return true;
    }

    /** It goes when it is killed, or leaves for lack of prey, never because players are far. */
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

    /** It flies past ladders and vines; it never climbs them. */
    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(WING_REACH, 0.5, WING_REACH);
    }

    /**
     * Killed or purified by a player's blow: that player has brought a Shrieker down. Its goals stop with its life, so
     * its fight never ends a dive it died in: the flag goes here, or the model would show it diving in death.
     */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // A cancelled death leaves it alive, and a second call does nothing: only a real first death counts.
        if (!wasDead && dead) {
            setSwooping(false);
            if (source.getEntity() instanceof ServerPlayer player) {
                Milestones.grant(player, Milestones.SHRIEKER);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SHRIEKER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SHRIEKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHRIEKER_DEATH.get();
    }

    /** Its own cries reach the shimmer band faintly, as every Hollow's do. */
    @Override
    protected boolean screams(SoundEvent sound) {
        return super.screams(sound) || sound == ModSounds.SHRIEKER_AMBIENT.get() || sound == ModSounds.SHRIEKER_HURT.get()
                || sound == ModSounds.SHRIEKER_DEATH.get();
    }
}
