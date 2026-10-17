package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
    /** Whether this arrow's own blow is being dealt, when a shield may stop it whatever its pierce. */
    private boolean hurting;
    /** Whether this tick's flight met a creature, after which vanilla's flight no longer meets the block ahead. */
    private boolean metCreature;

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
     * Server side, an arrow whose owner has gone or left this level goes before it can strike anything
     * without its owner's rules. The first tick strikes the creatures the arrow starts inside, which
     * vanilla's search along the path cannot see (a shot pressed against a foe). A tick whose flight
     * met a creature and flew on still bursts on the block that ended its path: vanilla's flight
     * forgets that block once it has met a creature, and would carry the arrow into or through it.
     * After moving, the arrow goes once its time is up or its owner has gone.
     */
    @Override
    public void tick() {
        if (!level().isClientSide()) {
            Entity owner = getOwner();
            if (owner == null || owner.level() != level()) {
                discard();
                return;
            }
            if (!checkedPointBlank) {
                checkedPointBlank = true;
                strikePointBlank();
                if (isRemoved()) {
                    return;
                }
            }
        }
        Vec3 from = position();
        Vec3 step = getDeltaMovement();
        metCreature = false;
        super.tick();
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        if (metCreature) {
            BlockHitResult wall = level().clip(new ClipContext(from, from.add(step), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (wall.getType() == HitResult.Type.BLOCK && !EventHooks.onProjectileImpact(this, wall)) {
                hitTargetOrDeflectSelf(wall);
                if (isRemoved()) {
                    return;
                }
            }
        }
        Entity owner = getOwner();
        if (tickCount >= lifeTicks || owner == null || owner.level() != level()) {
            discard();
        }
    }

    /**
     * Strikes the creatures the arrow starts inside, nearest first, as far as it may go: each through
     * the same impact event and deflection check as a hit along the path, until one ends the arrow,
     * turns it aside or cancels the impact. One it only starts near counts if it lies ahead, so a
     * creature brushing the shooter's back or side is not struck by a shot aimed elsewhere.
     */
    private void strikePointBlank() {
        Vec3 at = position();
        Vec3 heading = getDeltaMovement();
        AABB around = new AABB(at, at).inflate(POINT_BLANK_INFLATE);
        List<Entity> inside = new ArrayList<>(level().getEntities(this, around,
                other -> startsAt(other.getBoundingBox(), at, heading) && canHitEntity(other)));
        inside.sort(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().distanceToSqr(at)));
        for (Entity entity : inside) {
            if (isRemoved()) {
                return;
            }
            // One struck already may have been this one's body, or a part of it.
            if (!canHitEntity(entity)) {
                continue;
            }
            EntityHitResult hit = new EntityHitResult(entity);
            if (EventHooks.onProjectileImpact(this, hit)
                    || hitTargetOrDeflectSelf(hit) != ProjectileDeflection.NONE) {
                return;
            }
        }
    }

    /** Whether a shot from {@code at} along {@code heading} starts inside this box, or just short of it. */
    private static boolean startsAt(AABB box, Vec3 at, Vec3 heading) {
        return box.contains(at) || box.inflate(POINT_BLANK_INFLATE).contains(at)
                && box.getCenter().subtract(at).dot(heading) > 0.0;
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
     * Whether vanilla's flight should look for another creature this tick, after the one it met. On
     * the server it always does while the arrow flies on, since one flown through or the last one
     * pierced may stand right in front of the next; {@link #onHitEntity} ends the arrow when it should
     * stop, and {@link #struck} keeps the search from meeting anyone twice. While its own blow is
     * dealt it reads as no pierce at all, so a shield can stop it. The client never looks past the
     * first, so it cannot keep finding creatures it has not been told were struck.
     */
    @Override
    public byte getPierceLevel() {
        if (level().isClientSide() || hurting) {
            return 0;
        }
        return (byte) Mth.clamp(pierceLeft + 1, 1, Byte.MAX_VALUE);
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
        metCreature = true;
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
        // Judged before the blow too: a shield the blow breaks is no longer raised once it lands.
        boolean blocking = target.isBlocking();
        boolean hurt;
        hurting = true;
        try {
            hurt = entity.hurt(source, QuincyLogic.damageAgainst(damage, target));
        } finally {
            hurting = false;
        }
        if (hurt && target instanceof EnderMan) {
            // It slipped aside rather than took the blow: flown through, as one that takes no harm is.
            return;
        }
        if (hurt) {
            QuincyEvents.onArrowLanded(owner, target, mask);
            burst(at);
        }
        if (hurt || blocking) {
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

    /** Reishi does not pass through portals: what it was loosed with is never saved, so a copy would be empty. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    /** Only the aware see it splash into water. */
    @Override
    protected void doWaterSplashEffect() {
        if (level() instanceof ServerLevel server) {
            super.doWaterSplashEffect();
            SpiritSenses.splash(server, this);
        }
    }

    /**
     * A client never counts it as in water, so vanilla's trail of bubbles is not drawn for the unaware.
     * Its flight is the same either way: water slows it no more than air does.
     */
    @Override
    public boolean isInWater() {
        return !level().isClientSide() && super.isInWater();
    }

    /** Reishi is not something to pick up. */
    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }
}
