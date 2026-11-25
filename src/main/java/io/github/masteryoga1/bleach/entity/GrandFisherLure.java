package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
 * health. A lure laid with no body yet waits for one; only its body links it ({@link GrandFisher#takeLure}). Its link is
 * its body's entity id, synced so the client draws the stalk; both are never saved, so the id never outlives the session
 * that gave it.
 */
public class GrandFisherLure extends Mob {
    /** Its body's entity id while it is linked, kept once it is cut off; -1 for none. */
    private static final EntityDataAccessor<Integer> DATA_BODY =
            SynchedEntityData.defineId(GrandFisherLure.class, EntityDataSerializers.INT);
    /** 0 while it hangs on its stalk; once cut off, how long it lies before it fades, in ticks. */
    private static final EntityDataAccessor<Integer> DATA_FADE =
            SynchedEntityData.defineId(GrandFisherLure.class, EntityDataSerializers.INT);
    /**
     * The game time it was cut off, so either side knows how long it has lain, and a client that first sees it late draws
     * its stalk and its whitening as far along as they are; unread while it hangs on its stalk.
     */
    private static final EntityDataAccessor<Long> DATA_CUT =
            SynchedEntityData.defineId(GrandFisherLure.class, EntityDataSerializers.LONG);
    /** How far past the reach its stalk stretches before it tears, as a share of the reach. */
    public static final double TEAR = 1.5;
    /** How long a cut stalk takes to withdraw to the brow, and how long before it fades the lure whitens. */
    public static final int WITHDRAW_TICKS = 10;
    public static final int WHITEN_TICKS = 20;
    private static final int DEATH_TICKS = 20;
    private static final float LOOK_RANGE = 8.0F;

    public GrandFisherLure(EntityType<? extends GrandFisherLure> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 15.0)
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

    /** Whether its stalk is cut, on both sides. */
    public boolean isSevered() {
        return entityData.get(DATA_FADE) > 0;
    }

    /** Where the stalk meets it, the top of its head, at this partial tick. */
    public Vec3 stalkEnd(float partialTick) {
        return getPosition(partialTick).add(0.0, getBbHeight(), 0.0);
    }

    /** How far its cut stalk has withdrawn to the brow, from 0 on the stalk to 1 gone, for the renderer. */
    public float withdrawn(float partialTick) {
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
        if (level().isClientSide || isSevered()) {
            return;
        }
        entityData.set(DATA_CUT, level().getGameTime());
        entityData.set(DATA_FADE, Math.max(1, fadeTicks));
        goalSelector.removeAllGoals(goal -> true);
        getNavigation().stop();
        playSound(ModSounds.GRAND_FISHER_LURE_CRY.get(), 1.0F, getVoicePitch());
    }

    /** How many ticks it has lain cut off. */
    private long lain() {
        return level().getGameTime() - entityData.get(DATA_CUT);
    }

    /**
     * Server side, every tick, so it runs with NoAI too. Cut off, it fades once its time is up. On its stalk: its body
     * gone, dead or in another world cuts it off; a stalk stretched past {@link #TEAR} times the reach tears; and past the
     * reach from the brow it is drawn back across the ground to the reach, if the brow is not so far above or below it
     * that no spot across the ground is near enough.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        if (isSevered()) {
            if (lain() >= entityData.get(DATA_FADE)) {
                wisps(server, ParticleTypes.SOUL, 4);
                discard();
            }
            return;
        }
        if (entityData.get(DATA_BODY) < 0 || !isAlive()) {
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
     * through her. Any other blow, or any blow once she is cut off, hurts her by its damage.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isSevered() || !cuts(source)) {
            return super.hurt(source, amount);
        }
        // The blade cuts the stalk, not the girl, so the blow carries no damage, which a hurt cooldown always refuses: the
        // cooldown is lifted for it, and put back if the blow is turned aside.
        int cooldown = invulnerableTime;
        invulnerableTime = 0;
        if (!super.hurt(source, 0.0F)) {
            invulnerableTime = cooldown;
            return false;
        }
        sever();
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
        if (isSevered() || !cuts(source)) {
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

    /** She sobs while she hangs on her stalk, and is silent once it is cut. */
    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isSevered() ? null : ModSounds.GRAND_FISHER_LURE_SOB.get();
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
