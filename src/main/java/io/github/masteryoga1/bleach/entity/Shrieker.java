package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 * blocks as any mob does, so walls, Sekkiseki and the Shakonmaku hold it. From its circle it looses its leeches at its
 * prey a few at a time ({@link ShriekerLeech}). Its bar shows to the souls about it who see spirits. It is always a
 * sinner, as it was a killer in life. Its health, the damage of its swoop and its brood are the server's to set. It is
 * never saved, and for now comes only by command.
 *
 * <p>Once every leech it loosed clings, none near its head, it hangs still and rings its forked tongue; as the ring ends
 * each clinging leech bursts. A blow to its mask while it rings breaks the ring off, and one of its own leeches sent back
 * to burst by its head hurts it badly and cracks its mask.
 */
public class Shrieker extends Hollow {
    /** How far apart its wingbeats are, in ticks: the model's beat and the sound of it keep this time together. */
    public static final int FLAP_TICKS = 18;
    /** Its flying speed in blocks a tick at a modifier of 1, before what slows it. */
    public static final double FLYING_SPEED = 0.35;
    /**
     * Its own synced flags: diving at its prey, and ringing its tongue. Bit 16 is each kind's own past Hollow's four (a
     * Menos keeps its Cero's charge there); a Shrieker takes 32 too, so 64 and 128 are still free for every Hollow.
     */
    private static final int FLAG_SWOOPING = 16;
    private static final int FLAG_RINGING = 32;
    /** How long its ring sounds before its leeches burst (a creeper's fuse, for now), and its wait after a broken one. */
    public static final int RING_TICKS = 30;
    public static final int RING_RETRY_TICKS = 40;
    /** How much further than a burst at its head reaches every clinging leech must be for it to ring. */
    private static final double RING_MARGIN = 1.0;
    /** How far past its body its wings reach, in blocks, so it is still drawn while only a wing is in view. */
    private static final double WING_REACH = 1.5;
    private static final float FLAP_VOLUME = 0.6F;
    /** How long after the last of its leeches is spent before it looses more. */
    public static final int LOOSE_COOLDOWN_TICKS = 100;
    /**
     * Its leeches come from the hole in its chest, this high up its body (the hole on its model's texture), fanned out
     * this far apart in degrees, a little up, at this speed.
     */
    private static final double CHEST = 0.55;
    private static final float LOOSE_FAN = 35.0F;
    private static final float LOOSE_PITCH = -20.0F;
    private static final double LOOSE_SPEED = 0.3;

    private final BossBar bar = new BossBar(this, "bossbar.bleach.shrieker", BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    /** The game time until which a blow to its mask holds it low and keeps it from swooping. Not saved. */
    private long stalledUntil;
    /**
     * Its leeches out now, loosed and not yet spent, how many it holds back (drawn from the config when first asked, so a
     * test may set it before) and when it may loose more. Server side, never shown and not saved.
     */
    private final List<ShriekerLeech> leeches = new ArrayList<>();
    private int held = -1;
    private long looseReadyAt;
    /** When its ring ends, when it may ring again after a broken one, and when a leech last burst by its head. */
    private long ringUntil;
    private long ringReadyAt;
    private long headBurstAt = Long.MIN_VALUE;

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

    /** Its health and the damage of its swoop are the config's ([bosses.shrieker]). */
    @Override
    protected ConfigBases configBases() {
        return new ConfigBases(BleachConfig.get(BleachConfig.SHRIEKER_HEALTH),
                BleachConfig.get(BleachConfig.SHRIEKER_DAMAGE));
    }

    /**
     * Server side, every tick, so it runs with NoAI too: first the air holding it up or letting it fall, before it
     * moves; then its wingbeat while it flies; its spent leeches let go, the wait for more begun once all are; the end
     * of its ring, at which each clinging leech bursts until one by its head kills it; and its bar, last, so the bar
     * shows the health this tick left. Hollow's tick has set the config's strength for one that skipped spawning.
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
        if (isAlive() && !onGround() && !isSwooping() && (level().getGameTime() + getId() * 3L) % FLAP_TICKS == 0) {
            playSound(ModSounds.SHRIEKER_FLAP.get(), FLAP_VOLUME, 0.9F + random.nextFloat() * 0.2F);
        }
        long now = level().getGameTime();
        if (!leeches.isEmpty() && leeches.removeIf(leech -> leech.isRemoved()) && leeches.isEmpty()) {
            looseReadyAt = now + LOOSE_COOLDOWN_TICKS;
        }
        if (isRinging() && now >= ringUntil) {
            setFlag(FLAG_RINGING, false);
            for (ShriekerLeech leech : List.copyOf(leeches)) {
                if (!isAlive()) {
                    break;
                }
                if (!leech.isRemoved() && leech.isClinging()) {
                    leech.burst();
                }
            }
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

    /**
     * A blow to the mask that makes it reel also holds it low while it reels, and ends a swoop. Any blow to the mask
     * breaks off a ring, fresh crack or not, and it waits a while to ring again; its leeches cling on.
     */
    @Override
    public boolean onMaskStruck(long now) {
        boolean fresh = super.onMaskStruck(now);
        if (lastReelAt == now) {
            stalledUntil = now + REEL_TICKS;
        }
        if (isRinging()) {
            setFlag(FLAG_RINGING, false);
            ringReadyAt = now + RING_RETRY_TICKS;
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

    /** Whether it rings its tongue now, on both sides, for the model. */
    public boolean isRinging() {
        return getFlag(FLAG_RINGING);
    }

    /** Its leeches out now, loosed and not yet spent. Server side. */
    public List<ShriekerLeech> leeches() {
        return Collections.unmodifiableList(leeches);
    }

    /** How many leeches it holds back still, never shown to anyone; public for the tests. Server side. */
    public int leechesHeld() {
        if (held < 0) {
            held = BleachConfig.get(BleachConfig.SHRIEKER_LEECHES);
        }
        return held;
    }

    /** Sets how many leeches it holds back; for the tests, which keep a fight to its claws or ring a few. */
    public void setLeechesHeld(int count) {
        held = Math.max(0, count);
    }

    /**
     * Looses its next leeches at its prey from its chest, as many as it looses at once, if it holds any back, every one
     * it loosed before is spent and its wait after them is over, and its prey is one it may strike. Its fight calls this
     * from its circle; public so the tests and the smoke test drive a Shrieker with no will of its own by the same code.
     * Returns how many it loosed.
     */
    public int looseLeeches() {
        LivingEntity prey = getTarget();
        if (!(level() instanceof ServerLevel server) || !isAlive() || prey == null || !leeches.isEmpty() || leechesHeld() <= 0
                || server.getGameTime() < looseReadyAt || !CeroBeam.mayStrike(this, prey)) {
            return 0;
        }
        int count = Math.min(held, BleachConfig.get(BleachConfig.SHRIEKER_LEECHES_AT_ONCE));
        Vec3 chest = position().add(0.0, getBbHeight() * CHEST, 0.0);
        for (int i = 0; i < count; i++) {
            float yaw = yBodyRot + (i - (count - 1) * 0.5F) * LOOSE_FAN;
            ShriekerLeech leech = ShriekerLeech.loose(server, this, prey, chest,
                    Vec3.directionFromRotation(LOOSE_PITCH, yaw).scale(LOOSE_SPEED));
            if (leech != null) {
                leeches.add(leech);
            }
        }
        held -= count;
        playSound(ModSounds.SHRIEKER_LOOSE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        return leeches.size();
    }

    /**
     * Begins to ring, if a broken ring's wait is over and every leech of its own still out clings, none within a margin
     * of a burst's reach of its head and none with less of its cling left than the ring lasts, so every one is there to
     * burst. Its fight calls this from its circle; public for the tests and the smoke test.
     */
    public boolean startRing() {
        long now = level().getGameTime();
        if (level().isClientSide || !isAlive() || isRinging() || now < ringReadyAt) {
            return false;
        }
        Vec3 mask = HollowMask.maskCenter(this);
        double safe = ShriekerLeech.HEAD_RADIUS + RING_MARGIN;
        boolean clinging = false;
        for (ShriekerLeech leech : leeches) {
            if (leech.isRemoved()) {
                continue;
            }
            if (!leech.isClinging() || leech.clingLeft() < RING_TICKS || leech.distanceToSqr(mask) < safe * safe) {
                return false;
            }
            clinging = true;
        }
        if (clinging) {
            ringUntil = now + RING_TICKS;
            setFlag(FLAG_RINGING, true);
            playSound(ModSounds.SHRIEKER_RING.get(), 1.0F, 1.0F);
        }
        return clinging;
    }

    /**
     * One of its own leeches burst by its head: it is hurt badly, by whoever sent the leech there or bore it, and its mask
     * cracks as under a blow (which breaks a ring), once a tick at most however many burst there.
     */
    void burstAtHead(ShriekerLeech leech, @Nullable LivingEntity cause) {
        long now = level().getGameTime();
        if (!(level() instanceof ServerLevel server) || !isAlive() || now == headBurstAt) {
            return;
        }
        headBurstAt = now;
        if (hurt(CeroBeam.sourceOf(server, cause, leech, ShriekerLeech.KIND),
                (float) BleachConfig.get(BleachConfig.SHRIEKER_HEAD_DAMAGE)) && isAlive()) {
            onMaskStruck(now);
        }
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
     * its fight never ends a dive or a ring it died in: the flags go here, or the model would show them in death.
     */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // A cancelled death leaves it alive, and a second call does nothing: only a real first death counts.
        if (!wasDead && dead) {
            setSwooping(false);
            setFlag(FLAG_RINGING, false);
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
