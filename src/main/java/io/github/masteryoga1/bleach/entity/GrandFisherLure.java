package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.GrandFisherComing;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Grand Fisher's lure: a part of its own body, grown on a stalk from its brow, in the shape of a small girl who stands
 * and sobs. It is a spirit, so only the aware see, hear or touch it, and it is no Hollow, so nothing that looks for
 * Hollows (the pager, a Quincy's aim, another Hollow's hunt) ever takes it for one. Its body keeps it within the
 * config's reach, drawing it back along the stalk, and a stalk pulled far past that tears.
 *
 * <p>A blade's blow from a soul who sees her cuts the stalk, not the girl, and a lure whose stalk tears, or whose body
 * is gone or dead, is cut off as if by a blade: she cries out, the stalk withdraws to the brow, and she lies limp and
 * silent until the config's time passes, then fades away with no death. Any other blow only hurts her, by her own
 * health. A lure laid with no body yet waits, sobbing, for her prey: one who sees her coming within a few blocks of her,
 * or striking her, brings her body bursting out of the ground beside her; none coming within the config's time, or on
 * Peaceful, she fades. Only its body links it ({@link GrandFisher#takeLure}). Its link is its body's entity id, synced so
 * the client draws the stalk; both are never saved, so the id never outlives the session that gave it. Her body escaped
 * into her ({@link #flee}), she gets up if she lay cut off and runs from the souls about her, then fades as a cut lure
 * does.
 */
public class GrandFisherLure extends Mob {
    /** Its body's entity id while it is linked, kept once it is cut off; -1 for none. */
    private static final EntityDataAccessor<Integer> DATA_BODY =
            SynchedEntityData.defineId(GrandFisherLure.class, EntityDataSerializers.INT);
    /** 0 while it hangs on its stalk; once cut off or fled, how long before it fades, in ticks. */
    private static final EntityDataAccessor<Integer> DATA_FADE =
            SynchedEntityData.defineId(GrandFisherLure.class, EntityDataSerializers.INT);
    /**
     * The game time it was cut off or fled, so either side knows how long it has lain or run, and a client that first sees
     * it late draws its stalk and its whitening as far along as they are; unread while it hangs on its stalk.
     */
    private static final EntityDataAccessor<Long> DATA_CUT =
            SynchedEntityData.defineId(GrandFisherLure.class, EntityDataSerializers.LONG);
    /** Whether her body escaped into her: she stands and runs, not lies, as she fades. */
    private static final EntityDataAccessor<Boolean> DATA_FLED =
            SynchedEntityData.defineId(GrandFisherLure.class, EntityDataSerializers.BOOLEAN);
    /** How far past the reach its stalk stretches before it tears, as a share of the reach. */
    public static final double TEAR = 1.5;
    /** How long a cut stalk takes to withdraw to the brow, and how long before it fades the lure whitens. */
    public static final int WITHDRAW_TICKS = 10;
    public static final int WHITEN_TICKS = 20;
    private static final int DEATH_TICKS = 20;
    private static final float LOOK_RANGE = 8.0F;
    /** Fled, how fast she runs, from the nearest soul who sees her within how far, how far at a time, how often. */
    private static final double RUN_SPEED = 0.3;
    private static final double RUN_FROM = 24.0;
    private static final double RUN_STEP = 8.0;
    private static final int RUN_TICKS = 20;
    /** Laid alone, how near a soul who sees her must come to bring her body out, and how often she looks. */
    private static final double BURST_DISTANCE = 4.0;
    private static final int LOOK_TICKS = 5;

    public GrandFisherLure(EntityType<? extends GrandFisherLure> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 15.0).add(Attributes.MOVEMENT_SPEED, RUN_SPEED)
                .add(Attributes.SAFE_FALL_DISTANCE, HollowKind.SAFE_FALL_DISTANCE);
    }

    /**
     * A new lure here, facing this way, with the config's health, on no stalk yet: one its body grows and takes at once,
     * or one laid alone to wait for its body. Null if the world refused it.
     */
    @Nullable
    public static GrandFisherLure grow(ServerLevel level, Vec3 at, float yaw) {
        GrandFisherLure lure = ModEntities.GRAND_FISHER_LURE.get().create(level);
        if (lure == null) {
            return null;
        }
        lure.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        lure.setYHeadRot(yaw);
        lure.setYBodyRot(yaw);
        AttributeInstance health = lure.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_HEALTH));
        }
        lure.setHealth(lure.getMaxHealth());
        return level.addFreshEntity(lure) ? lure : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BODY, -1);
        builder.define(DATA_FADE, 0);
        builder.define(DATA_CUT, 0L);
        builder.define(DATA_FLED, false);
    }

    /** She only looks about her, at whoever comes near. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, LOOK_RANGE));
    }

    /** Linked to its body, by {@link GrandFisher#takeLure} only. */
    void attachTo(GrandFisher body) {
        entityData.set(DATA_BODY, body.getId());
    }

    /** Its body, as either side knows it by the synced id, on its stalk or cut off; null for none or one gone. */
    @Nullable
    public GrandFisher body() {
        int id = entityData.get(DATA_BODY);
        return id >= 0 && level().getEntity(id) instanceof GrandFisher body && !body.isRemoved() ? body : null;
    }

    /** Whether its stalk is cut and she lies cut off, on both sides; once fled, she is not. */
    public boolean isSevered() {
        return isFading() && !hasFled();
    }

    /** Whether her body escaped into her, on both sides. */
    public boolean hasFled() {
        return entityData.get(DATA_FLED);
    }

    /** Whether she was laid alone and waits for her prey, her body not yet come: no body linked, and not fading. */
    public boolean isWaiting() {
        return entityData.get(DATA_BODY) < 0 && !isFading();
    }

    /** Whether she is on her way to fading, cut off or fled. */
    public boolean isFading() {
        return entityData.get(DATA_FADE) > 0;
    }

    /** Where the stalk meets it, the top of its head, at this partial tick. */
    public Vec3 stalkEnd(float partialTick) {
        return getPosition(partialTick).add(0.0, getBbHeight(), 0.0);
    }

    /**
     * How far its cut stalk has withdrawn to the brow, from 0 on the stalk to 1 gone, for the renderer; gone at once for
     * a lure fled, whose body may still be seen for a tick before it goes.
     */
    public float withdrawn(float partialTick) {
        if (hasFled()) {
            return 1.0F;
        }
        return isSevered() ? Mth.clamp((lain() + partialTick) / WITHDRAW_TICKS, 0.0F, 1.0F) : 0.0F;
    }

    /** How white it has turned as it fades, from 0 to 1 over its last {@link #WHITEN_TICKS}, for the renderer. */
    public float fadeProgress(float partialTick) {
        int fade = entityData.get(DATA_FADE);
        return fade > 0 ? Mth.clamp((lain() + partialTick - (fade - WHITEN_TICKS)) / WHITEN_TICKS, 0.0F, 1.0F) : 0.0F;
    }

    /** Cuts it off from its stalk: it cries out, goes limp and fades after the config's time. */
    public void sever() {
        sever(BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_FADE_SECONDS) * 20);
    }

    /** Cuts it off from its stalk, to fade after this many ticks; a test may pass fewer. Server side, once. */
    public void sever(int fadeTicks) {
        if (level().isClientSide || isFading()) {
            return;
        }
        entityData.set(DATA_CUT, level().getGameTime());
        entityData.set(DATA_FADE, Math.max(1, fadeTicks));
        goalSelector.removeAllGoals(goal -> true);
        getNavigation().stop();
        playSound(ModSounds.GRAND_FISHER_LURE_CRY.get(), 1.0F, getVoicePitch());
    }

    /**
     * Her body has escaped into her (canon, Ch. 21): she laughs its laugh, gets up if she lay cut off, runs from the souls
     * about her and fades after the config's time, as a cut lure does. Server side, once.
     */
    public void flee() {
        if (level().isClientSide || hasFled()) {
            return;
        }
        entityData.set(DATA_FLED, true);
        entityData.set(DATA_CUT, level().getGameTime());
        entityData.set(DATA_FADE, Math.max(1, BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_FADE_SECONDS) * 20));
        playSound(ModSounds.GRAND_FISHER_AMBIENT.get(), 1.0F, 1.0F);
    }

    /** She fades now, into soul wisps only the aware see, with no death. Server side. */
    public void fadeNow() {
        if (level() instanceof ServerLevel server && !isRemoved()) {
            wisps(server, ParticleTypes.SOUL, 4);
            discard();
        }
    }

    /** How many ticks it has lain cut off, or run. */
    private long lain() {
        return level().getGameTime() - entityData.get(DATA_CUT);
    }

    /** The nearest soul within this range who sees her, spectators aside; null for none. */
    @Nullable
    private ServerPlayer nearestSoul(ServerLevel server, double range) {
        ServerPlayer nearest = null;
        for (ServerPlayer soul : SpiritSenses.awareNear(server, position(), range)) {
            if (!soul.isSpectator() && (nearest == null || distanceToSqr(soul) < distanceToSqr(nearest))) {
                nearest = soul;
            }
        }
        return nearest;
    }

    /** Fled, she runs on along the ground away from the nearest soul who sees her, if any is near. */
    private void runFrom(ServerLevel server) {
        ServerPlayer from = nearestSoul(server, RUN_FROM);
        if (from == null) {
            return;
        }
        Vec3 away = position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
        Vec3 way = away.lengthSqr() > 1.0E-4 ? away.normalize() : Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 to = position().add(way.scale(RUN_STEP));
        getNavigation().moveTo(to.x, to.y, to.z, 1.0);
    }

    /**
     * Server side, every tick, so it runs with NoAI too. Cut off or fled, it fades once its time is up; fled, it sets off
     * again each second. Laid alone, it waits for its prey, and fades on Peaceful. On its stalk: its body gone, dead or in
     * another world cuts it off; a stalk stretched past {@link #TEAR} times the reach tears; and past the reach from the
     * brow it is drawn back across the ground to the reach, if the brow is not so far above or below it that no spot
     * across the ground is near enough.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        if (isFading()) {
            if (lain() >= entityData.get(DATA_FADE)) {
                fadeNow();
            } else if (hasFled() && tickCount % RUN_TICKS == 0) {
                runFrom(server);
            }
            return;
        }
        if (!isAlive()) {
            return;
        }
        if (entityData.get(DATA_BODY) < 0) {
            // Laid alone: her time up, or on Peaceful, she fades, and a soul who sees her come near brings her body out.
            ServerPlayer prey = tickCount % LOOK_TICKS == 0 ? nearestSoul(server, BURST_DISTANCE) : null;
            if (tickCount >= BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_WAIT_SECONDS) * 20
                    || server.getDifficulty() == Difficulty.PEACEFUL) {
                fadeNow();
            } else if (prey != null) {
                GrandFisherComing.burst(server, this, prey);
            }
            return;
        }
        GrandFisher body = body();
        if (body == null || !body.isAlive()) {
            sever();
            return;
        }
        Vec3 over = stalkEnd(1.0F).subtract(body.brow(1.0F));
        double reach = BleachConfig.get(BleachConfig.GRAND_FISHER_LURE_REACH);
        double across = over.horizontalDistance();
        double within = Math.sqrt(Math.max(0.0, reach * reach - over.y * over.y));
        if (over.length() > reach * TEAR) {
            sever();
        } else if (across > within && across > 0.0) {
            double back = (across - within) / across;
            move(MoverType.SELF, new Vec3(-over.x * back, 0.0, -over.z * back));
        }
    }

    /**
     * A blow of a blade from a soul who sees her cuts her stalk and does her no harm. It lands as any blow does, even in
     * the half second after another, or not at all, as the rules that turn blows aside decide; an unaware soul's passes
     * through her. Any other blow, or any blow once she is cut off or fled, hurts her by its damage. Laid alone, a blow
     * that lands from anyone is spent bringing her body out, and does her no harm.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isWaiting() && source.getEntity() instanceof LivingEntity prey && level() instanceof ServerLevel server) {
            if (!lands(source)) {
                return false;
            }
            GrandFisherComing.burst(server, this, prey);
            return true;
        }
        if (isFading() || !cuts(source)) {
            return super.hurt(source, amount);
        }
        if (!lands(source)) {
            return false;
        }
        sever();
        return true;
    }

    /**
     * Whether this blow lands on her as a blow with no damage: one that cuts the stalk, not the girl, or brings her body
     * out. A hurt cooldown always refuses a blow of no damage, so it is lifted for it, and put back if the blow is turned
     * aside.
     */
    private boolean lands(DamageSource source) {
        int cooldown = invulnerableTime;
        invulnerableTime = 0;
        if (!super.hurt(source, 0.0F)) {
            invulnerableTime = cooldown;
            return false;
        }
        return true;
    }

    /** Whether this is a blow of a blade in a player's own hand, as Zanjutsu reads one. */
    private static boolean cuts(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        return source.is(DamageTypeTags.IS_PLAYER_ATTACK) && source.getEntity() instanceof Player
                && source.getDirectEntity() == source.getEntity() && weapon != null && SoulEvents.isZanjutsuWeapon(weapon);
    }

    /** A blade's cut cries out once, as she is cut off, and not as a hurt as well. */
    @Override
    protected void playHurtSound(DamageSource source) {
        if (isFading() || !cuts(source)) {
            super.playHurtSound(source);
        }
    }

    /** Cut off, she lies where she fell. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isSevered();
    }

    /** Drawn and heard as a child: her proportions, her voice. Her size is her type's, never halved. */
    @Override
    public boolean isBaby() {
        return true;
    }

    @Override
    public float getAgeScale() {
        return 1.0F;
    }

    /** Falls as a mob does, then fades into soul wisps only the aware can see, without the usual puff of smoke. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= DEATH_TICKS && level() instanceof ServerLevel server && !isRemoved()) {
            wisps(server, ParticleTypes.SOUL, 6);
            wisps(server, ParticleTypes.SQUID_INK, 2);
            remove(RemovalReason.KILLED);
        }
    }

    private void wisps(ServerLevel server, ParticleOptions particle, int count) {
        SpiritSenses.particles(server, particle, getX(), getY() + getBbHeight() * 0.5, getZ(), count, 0.2, 0.3, 0.2, 0.02);
    }

    /** Every sound goes only to those who hear spirits. Nothing is played on the client. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent() && !level().isClientSide) {
            SpiritSenses.play(this, sound, getSoundSource(), volume, pitch, false);
        }
    }

    /** She sobs while she hangs on her stalk, and is silent once it is cut or she has fled. */
    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isFading() ? null : ModSounds.GRAND_FISHER_LURE_SOB.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GRAND_FISHER_LURE_CRY.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GRAND_FISHER_LURE_CRY.get();
    }

    /** Her renderer draws her stalk, so while any of it shows she is drawn while her body is in view. */
    @Override
    public AABB getBoundingBoxForCulling() {
        GrandFisher body = withdrawn(1.0F) < 1.0F ? body() : null;
        AABB own = super.getBoundingBoxForCulling();
        return body == null ? own : own.minmax(body.getBoundingBox());
    }

    /** A spirit's body: walls and crowds cannot crush it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING);
    }

    /** Pushed only by what can touch a spirit. */
    @Override
    public void push(Entity entity) {
        if (SpiritSenses.bodiesTouch(this, entity)) {
            super.push(entity);
        }
    }

    /** Pushes only what can touch a spirit. */
    @Override
    protected void doPush(Entity entity) {
        if (SpiritSenses.bodiesTouch(this, entity)) {
            super.doPush(entity);
        }
    }

    /** No steps, sounds or vibrations as it moves, which the unaware would hear or feel. */
    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    /** Only the aware see it splash into water. The sound goes through {@link #playSound}. */
    @Override
    protected void doWaterSplashEffect() {
        if (level() instanceof ServerLevel server) {
            super.doWaterSplashEffect();
            SpiritSenses.splash(server, this);
        }
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) {
        return false;
    }

    /** It lives only beside its body, and is never saved: a copy in another world would have none. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
