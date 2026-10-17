package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Heilig Pfeil: an arrow of reishi loosed from a Quincy's Heilig Bogen. It flies dead straight (no
 * gravity, and barely slowed by water) for {@link #lifeTicks()} ticks, strikes only creatures its
 * owner may harm ({@link SpiritProjectiles#mayStrike}), never the same one twice, and pierces as many
 * more as it was given. A creature that takes no harm from it and is not blocking (one still
 * reeling from a blow, or an Enderman slipping aside) is flown through without spending a pierce; a
 * shield stops it. It bursts on the first block it meets, leaves nothing to pick up, goes when its
 * owner does and is never saved. A Hollow it kills is destroyed, not purified: its damage type is in
 * {@code #bleach:destroys_souls} and not in {@code #bleach:purifies}. The server decides every hit; a
 * client only flies it and remembers what it passed through.
 */
public class HeiligPfeil extends AbstractArrow {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("heilig_pfeil"));

    private static final EntityDataAccessor<Float> DATA_CHARGE =
            SynchedEntityData.defineId(HeiligPfeil.class, EntityDataSerializers.FLOAT);

    /** The life of an arrow that was never launched; {@link #launch} sets the configured one. */
    private static final int DEFAULT_LIFE_TICKS = 30;
    /**
     * How much bigger than its box a creature counts for a shot fired from inside it, in blocks: as
     * much as vanilla's own search makes it, so a shot from within that margin is not missed either.
     */
    private static final double POINT_BLANK_INFLATE = 0.3;
    /** The spray of reishi where an arrow strikes. */
    private static final int BURST_COUNT = 6;
    private static final double BURST_SPREAD = 0.1;
    private static final double BURST_SPEED = 0.05;

    /** The damage of each hit, before {@link QuincyLogic#damageAgainst}. Server only. */
    private float damage;
    /** How many more creatures it may pass through after the next one it hurts. Server only. */
    private int pierceLeft;
    private int lifeTicks = DEFAULT_LIFE_TICKS;
    /** Every creature already struck or flown through, on both sides, so none is met twice. */
    private final IntOpenHashSet struck = new IntOpenHashSet();
    /** Whether the first server tick has looked for a creature the arrow starts inside. */
    private boolean checkedPointBlank;

    public HeiligPfeil(EntityType<? extends HeiligPfeil> type, Level level) {
        super(type, level);
        setSilent(true);
        pickup = Pickup.DISALLOWED;
    }

    /**
     * Looses an arrow from {@code from} along {@code direction} at {@code speed} blocks a tick, spread
     * by {@code inaccuracy} as vanilla arrows are, and adds it to the level. The charge, from 0 for a
     * tap to 1 for a full draw, sets how big it looks; the damage and the pierce (how many creatures
     * beyond the first it may pass through) are the caller's, so a test can pin them. The owner's own
     * motion is not added.
     */
    public static HeiligPfeil launch(Level level, LivingEntity owner, Vec3 from, Vec3 direction, float charge,
                                     float damage, int pierce, float speed, float inaccuracy) {
        HeiligPfeil arrow = new HeiligPfeil(ModEntities.HEILIG_PFEIL.get(), level);
        arrow.setOwner(owner);
        // Never one to pick up, whoever owns it (see setOwner).
        arrow.pickup = Pickup.DISALLOWED;
        arrow.setPos(from);
        arrow.entityData.set(DATA_CHARGE, Mth.clamp(charge, 0.0F, 1.0F));
        arrow.damage = damage;
        arrow.pierceLeft = Math.max(0, pierce);
        arrow.lifeTicks = BleachConfig.get(BleachConfig.PFEIL_LIFE_TICKS);
        // A direction too short to normalise would scatter the arrow at random: aim where the owner looks.
        Vec3 heading = direction.lengthSqr() < 1.0E-8 ? owner.getViewVector(1.0F) : direction;
        arrow.shoot(heading.x, heading.y, heading.z, speed, inaccuracy);
        level.addFreshEntity(arrow);
        return arrow;
    }

    /** From 0 for a tap to 1 for a full draw. */
    public float charge() {
        return entityData.get(DATA_CHARGE);
    }

    /** The damage of each hit, before Hollows take their extra share. Only the server knows it. */
    public float damage() {
        return damage;
    }

    /** How many ticks the arrow flies for. Only the server knows it; a client sees the default. */
    public int lifeTicks() {
        return lifeTicks;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0.0F);
    }

    /**
     * Server side, the first tick strikes a creature the arrow starts inside, which vanilla's search
     * along the path cannot see (a shot pressed against a foe). After moving, the arrow goes once its
     * time is up or its owner has gone or left this level.
     */
    @Override
    public void tick() {
        if (!level().isClientSide() && !checkedPointBlank) {
            checkedPointBlank = true;
            strikePointBlank();
            if (isRemoved()) {
                return;
            }
        }
        super.tick();
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        Entity owner = getOwner();
        if (tickCount >= lifeTicks || owner == null || owner.level() != level()) {
            discard();
        }
    }

    /**
     * Strikes the nearest creature the arrow starts inside, if it may: through the same impact event
     * and deflection check as a hit along the path.
     */
    private void strikePointBlank() {
        Vec3 at = position();
        Entity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        AABB around = new AABB(at, at).inflate(POINT_BLANK_INFLATE);
        for (Entity entity : level().getEntities(this, around,
                other -> other.getBoundingBox().inflate(POINT_BLANK_INFLATE).contains(at) && canHitEntity(other))) {
            double distanceSqr = entity.getBoundingBox().getCenter().distanceToSqr(at);
            if (distanceSqr < nearestSqr) {
                nearest = entity;
                nearestSqr = distanceSqr;
            }
        }
        if (nearest != null) {
            EntityHitResult hit = new EntityHitResult(nearest);
            if (!EventHooks.onProjectileImpact(this, hit)) {
                hitTargetOrDeflectSelf(hit);
            }
        }
    }

    /**
     * The creature a hit on this entity strikes: the entity itself if it is living, the body a part
     * belongs to (an Ender Dragon's wing or head), or none.
     */
    @Nullable
    private static LivingEntity targetOf(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }

    /** No arrow is ever handed out: this only fills vanilla's pickup slot, which must never be empty. */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    /**
     * How many more creatures vanilla's flight should look for this tick. The client never looks past
     * the first, so it cannot keep finding creatures it has not been told were struck.
     */
    @Override
    public byte getPierceLevel() {
        return level().isClientSide() ? 0 : (byte) Mth.clamp(pierceLeft, 0, Byte.MAX_VALUE);
    }

    /** Only a creature the owner may harm, through its parts for a many-part body, and never one met before. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || struck.contains(entity.getId())) {
            return false;
        }
        LivingEntity target = targetOf(entity);
        return target != null && !struck.contains(target.getId()) && SpiritProjectiles.mayStrike(getOwner(), target);
    }

    /**
     * Server side: strikes the creature, a Hollow's mask if the path meets it first, then tells the
     * Quincy rules about a hit that landed. A hit that hurts spends a pierce, or ends the arrow when
     * none is left; a shield ends it; anything else (no harm done, no shield) lets it fly on through.
     * A client only remembers the creature, so it flies on past it until the server says otherwise.
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity entity = result.getEntity();
        struck.add(entity.getId());
        if (level().isClientSide()) {
            return;
        }
        LivingEntity target = targetOf(entity);
        if (target == null) {
            return;
        }
        struck.add(target.getId());
        Entity owner = getOwner();
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE), this, owner);
        // Judged before the blow: a Hollow it kills starts dissolving, and a dissolving mask is never struck.
        boolean mask = target instanceof Hollow hollow && HollowMask.struck(hollow, source);
        Vec3 from = position();
        Vec3 at = target.getBoundingBox().clip(from, from.add(getDeltaMovement()))
                .orElse(target.getBoundingBox().getCenter());
        boolean hurt = entity.hurt(source, QuincyLogic.damageAgainst(damage, target));
        if (hurt) {
            QuincyEvents.onArrowLanded(owner, target, mask);
            burst(at);
        }
        if (hurt || target.isBlocking()) {
            if (!hurt || pierceLeft <= 0) {
                discard();
            } else {
                pierceLeft--;
            }
        }
    }

    /**
     * The first block it meets still feels the hit (a target block, a bell), as with any projectile;
     * then, on the server, the arrow bursts and is gone. A client flies on until it hears so.
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockState state = level().getBlockState(result.getBlockPos());
        state.onProjectileHit(level(), state, result, this);
        if (!level().isClientSide()) {
            burst(result.getLocation());
            discard();
        }
    }

    /** A spray of reishi and the strike's sound, for those who can see spirits. Server side. */
    private void burst(Vec3 at) {
        if (level() instanceof ServerLevel serverLevel) {
            SpiritSenses.particles(serverLevel, ParticleTypes.END_ROD, at.x, at.y, at.z, BURST_COUNT,
                    BURST_SPREAD, BURST_SPREAD, BURST_SPREAD, BURST_SPEED);
            SpiritSenses.play(serverLevel, at.x, at.y, at.z, ModSounds.HEILIG_PFEIL_HIT.get(), SoundSource.PLAYERS,
                    1.0F, 1.0F + (random.nextFloat() - 0.5F) * 0.2F, false);
        }
    }

    /**
     * Vanilla makes an arrow a player owns one they may pick up, whenever its owner is set: on launch,
     * on a client that sees it, and when a Breeze turns it back. Nothing is ever left of this one to
     * pick up, so it never becomes one (a Getsuga Tenshō would knock a free arrow out of it otherwise).
     */
    @Override
    public void setOwner(@Nullable Entity owner) {
        super.setOwner(owner);
        pickup = Pickup.DISALLOWED;
    }

    /** Reishi is not something to pick up. */
    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }
}
