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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Shrieker: a great winged Hollow, ape-like and bat-like, and a boss. For now it hunts on foot as any Hollow does; its
 * wings spread only while it is off the ground, falling or knocked into the air. Its bar shows to the souls about it who
 * see spirits. It is always a sinner, as it was a killer in life. Its health and the damage of its blows are the
 * server's to set. It is never saved, and for now comes only by command.
 */
public class Shrieker extends Hollow {
    /** Its flying speed in blocks a tick, kept with its attributes for when it takes wing; on foot it walks. */
    public static final double FLYING_SPEED = 0.35;
    /** How far past its body its wings reach, in blocks, so it is still drawn while only a wing is in view. */
    private static final double WING_REACH = 1.5;

    private final BossBar bar = new BossBar(this, "bossbar.bleach.shrieker", BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    /** Whether its health and damage are the config's yet. Not saved: it never is. */
    private boolean basesSet;

    public Shrieker(EntityType<? extends Shrieker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.SHRIEKER.attributes().add(Attributes.FLYING_SPEED, FLYING_SPEED);
    }

    @Override
    public HollowKind kind() {
        return HollowKind.SHRIEKER;
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
     * Server side, every tick, so it runs with NoAI too: the config's strength for one that skipped spawning (a summon
     * with data, a game test), whole if it was whole, though without the difficulty and multipliers spawning adds; and
     * its bar, last, so the bar shows the health this tick left.
     */
    @Override
    public void tick() {
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
        bar.tick();
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

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(WING_REACH, 0.5, WING_REACH);
    }

    /** Killed or purified by a player's blow: that player has brought a Shrieker down. */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // A cancelled death leaves it alive, and a second call does nothing: only a real first death counts.
        if (!wasDead && dead && source.getEntity() instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.SHRIEKER);
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
