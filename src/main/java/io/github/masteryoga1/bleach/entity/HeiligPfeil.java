package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
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
 * shield raised towards it stops it. It bursts on the first block it meets, leaves nothing to pick up, goes when its
 * owner does and is never saved. A Hollow it kills is destroyed, not purified: its damage type is in
 * {@code #bleach:destroys_souls} and not in {@code #bleach:purifies}. The server decides every hit; a
 * client only flies it and remembers what it passed through.
 * <p>
 * The arts of a restored Quincy build on it. A volley arrow ({@link #launchVolley}, from Ginrei
 * Kojaku's stream and Licht Regen) is a plain one with its own damage type, which lands through the
 * hurt cooldown. The Seele Schneider and the Letzt Pfeil are subclasses that change only the hooks
 * ({@link #damageType}, {@link #sticks}, {@link #onStuck}, {@link #fade}, {@link #isSpent}); each
 * hook's default is what this arrow has always done. A plain full-draw arrow from a third-tier
 * Quincy that would pass just beside a sealed Quincy's heart gives them their power back instead of
 * striking (see {@link #heartTarget}). So does the Quincy Elder's heart shot, at the one soul he is
 * restoring ({@link QuincyElder#restoreTarget()}).
 */
public class HeiligPfeil extends AbstractArrow {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("heilig_pfeil"));
    /** The arrows of Ginrei Kojaku's stream and of Licht Regen: tagged to land through the hurt cooldown. */
    public static final ResourceKey<DamageType> VOLLEY_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("heilig_pfeil_volley"));

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
    /** How much bigger than its box a creature is to an arrow's flight, as vanilla's search makes it. */
    private static final double ENTITY_HIT_INFLATE = 0.3;
    /** How a volley arrow looks: halfway between a tap and a full draw. */
    private static final float VOLLEY_CHARGE = 0.5F;

    /** The damage of each hit, before {@link QuincyLogic#damageAgainst}. Server only. */
    private float damage;
    /** How many more creatures it may pass through after the next one it hurts. Server only. */
    private int pierceLeft;
    private int lifeTicks = DEFAULT_LIFE_TICKS;
    /** Whether it is one of a stream or a Licht Regen (see {@link #launchVolley}). Server only. */
    private boolean volley;
    /** Every creature already struck or flown through, on both sides, so none is met twice. */
    private final IntOpenHashSet struck = new IntOpenHashSet();
    /** Whether the first server tick has looked for a creature the arrow starts inside. */
    private boolean checkedPointBlank;
    /** Whether this arrow's own blow is being dealt, when a shield may stop it whatever its pierce. */
    private boolean hurting;
    /**
     * Whether this tick's flight met a creature, after which vanilla's flight no longer meets the block
     * ahead; cleared when the arrow is turned back, since it then no longer flies that way.
     */
    private boolean metCreature;
    /** Where this tick's flight started, and how far it was to go. */
    private Vec3 flightFrom = Vec3.ZERO;
    private Vec3 flightStep = Vec3.ZERO;

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
        HeiligPfeil arrow = aim(new HeiligPfeil(ModEntities.HEILIG_PFEIL.get(), level), owner, from, direction, charge,
                damage, pierce, speed, inaccuracy);
        level.addFreshEntity(arrow);
        return arrow;
    }

    /**
     * Looses one arrow of a volley (Ginrei Kojaku's stream, Licht Regen): a plain arrow of this
     * damage that pierces nothing, looks half drawn, flies for {@code lifeTicks} ticks and lands
     * through the hurt cooldown ({@link #VOLLEY_DAMAGE_TYPE}), so every arrow of a stream counts. It
     * never restores a sealed Quincy.
     */
    public static HeiligPfeil launchVolley(Level level, LivingEntity owner, Vec3 from, Vec3 direction, float damage,
                                           float speed, float inaccuracy, int lifeTicks) {
        HeiligPfeil arrow = aim(new HeiligPfeil(ModEntities.HEILIG_PFEIL.get(), level), owner, from, direction,
                VOLLEY_CHARGE, damage, 0, speed, inaccuracy);
        arrow.lifeTicks = lifeTicks;
        arrow.volley = true;
        level.addFreshEntity(arrow);
        return arrow;
    }

    /**
     * Readies a new arrow as {@link #launch} describes, without adding it to the level: everything a
     * launch does but making the arrow and adding it, so a subclass's launch can do both itself.
     */
    protected static <T extends HeiligPfeil> T aim(T arrow, LivingEntity owner, Vec3 from, Vec3 direction, float charge,
                                                   float damage, int pierce, float speed, float inaccuracy) {
        // Private fields cannot be reached through the type variable, only through the class itself.
        HeiligPfeil base = arrow;
        base.setOwner(owner);
        // Never one to pick up, whoever owns it (see setOwner).
        base.pickup = Pickup.DISALLOWED;
        base.setPos(from);
        base.entityData.set(DATA_CHARGE, Mth.clamp(charge, 0.0F, 1.0F));
        base.damage = damage;
        base.pierceLeft = Math.max(0, pierce);
        base.lifeTicks = BleachConfig.get(BleachConfig.PFEIL_LIFE_TICKS);
        // A direction too short to normalise would scatter the arrow at random: aim where the owner looks.
        Vec3 heading = direction.lengthSqr() < 1.0E-8 ? owner.getViewVector(1.0F) : direction;
        base.shoot(heading.x, heading.y, heading.z, speed, inaccuracy);
        return arrow;
    }

    /**
     * The sealed Quincy, nearest the start, whom a full-draw shot from {@code from} to {@code to}
     * would pass just beside the heart of, or null: another living player, not a spectator, whose power
     * can come back by an arrow now ({@link QuincyArtsLogic#restorable}), whose box (grown as for a
     * point-blank shot) holds the start or meets the path, and whom the path crosses beside the heart
     * ({@link QuincyArtsLogic#besideTheHeart}). It asks nothing of the shooter, a player or the Quincy
     * Elder; the arrow does.
     */
    @Nullable
    public static Player heartTarget(@Nullable Entity owner, Vec3 from, Vec3 to, Iterable<? extends Player> candidates,
                                     long now) {
        Vec3 heading = to.subtract(from);
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : candidates) {
            if (candidate == owner || !candidate.isAlive() || candidate.isSpectator() || !SoulLogic.hasSoul(candidate)) {
                continue;
            }
            AABB box = candidate.getBoundingBox().inflate(POINT_BLANK_INFLATE);
            if (!box.contains(from) && box.clip(from, to).isEmpty()) {
                continue;
            }
            if (!QuincyArtsLogic.restorable(candidate, SoulLogic.get(candidate), now, true)
                    || !QuincyArtsLogic.besideTheHeart(from, heading, candidate.position(), candidate.yBodyRot,
                            candidate.getBbHeight())) {
                continue;
            }
            double distance = candidate.position().distanceToSqr(from);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
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

    /** Whether it is one arrow of a stream or a Licht Regen. Only the server knows it. */
    public boolean isVolley() {
        return volley;
    }

    /** What its hits deal: a volley's lands through the hurt cooldown. */
    protected ResourceKey<DamageType> damageType() {
        return volley ? VOLLEY_DAMAGE_TYPE : DAMAGE_TYPE;
    }

    /** Whether it sticks into the block it meets, as a vanilla arrow does, rather than bursting on it. */
    protected boolean sticks() {
        return false;
    }

    /** Called on both sides once it has stuck into a block (only when it {@link #sticks}). */
    protected void onStuck(BlockHitResult hit) {
    }

    /**
     * How it ends when its flight is over (its time up, its owner gone, its last pierce spent or a
     * shield in its way): it is gone. Server side.
     */
    protected void fade() {
        discard();
    }

    /**
     * Whether its flight is over and only vanilla's arrow is left: no owner's rules, no hits, no
     * time limit. Never for this arrow, which is gone when it ends.
     */
    protected boolean isSpent() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0.0F);
    }

    /**
     * A spent arrow is only vanilla's. Otherwise, server side, an arrow whose owner has gone or left
     * this level ends ({@link #fade}) before it can strike anything without its owner's rules. A plain
     * full-draw arrow from a Quincy who can restore others that would pass beside a sealed Quincy's
     * heart this tick gives their power back and goes, harming no one, before anything else can
     * meet it (see {@link #heartTarget}); so does the Quincy Elder's, at the soul he is restoring. The
     * first tick strikes the creatures the arrow starts inside, which vanilla's search along the path
     * cannot see (a shot pressed against a foe). A tick
     * whose flight met a creature and flew on still bursts on the block that ended its path: vanilla's
     * flight forgets that block once it has met a creature, and would carry the arrow into or through
     * it (see {@link #checkInsideBlocks}).
     * After moving, the arrow ends once its time is up or its owner has gone.
     */
    @Override
    public void tick() {
        if (isSpent()) {
            super.tick();
            return;
        }
        if (!level().isClientSide()) {
            Entity owner = getOwner();
            if (owner == null || owner.level() != level()) {
                fade();
                return;
            }
            if (getClass() == HeiligPfeil.class && !volley && charge() >= QuincyArtsLogic.RESTORE_CHARGE
                    && (owner instanceof Player shooter && QuincyArtsLogic.canRestoreOthers(shooter) && restoreAhead(shooter)
                        || owner instanceof QuincyElder elder && restoreAheadByElder(elder))) {
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
        flightFrom = position();
        flightStep = getDeltaMovement();
        metCreature = false;
        super.tick();
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        Entity owner = getOwner();
        if (tickCount >= lifeTicks || owner == null || owner.level() != level()) {
            fade();
        }
    }

    /**
     * Whether this tick's path, up to the first block in its way, passes beside the heart of a sealed
     * Quincy whose power then comes back by this shooter's arrow. Server side.
     */
    private boolean restoreAhead(Player shooter) {
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        BlockHitResult wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            to = wall.getLocation();
        }
        long now = level().getGameTime();
        Player target = heartTarget(shooter, from, to, level().players(), now);
        return target != null && QuincyArtsEvents.tryRestoreByArrow(shooter, target, now);
    }

    /**
     * Whether this tick's path, up to the first block in its way, passes beside the heart of the soul
     * the Elder is restoring, whose power then comes back. Server side. A shot that never does simply
     * flies on: the Elder's arrows strike only Hollows (see {@link SpiritProjectiles#mayStrike}).
     */
    private boolean restoreAheadByElder(QuincyElder elder) {
        Player target = elder.restoreTarget();
        if (target == null) {
            return false;
        }
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        BlockHitResult wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            to = wall.getLocation();
        }
        long now = level().getGameTime();
        return heartTarget(elder, from, to, List.of(target), now) != null
                && QuincyArtsEvents.tryRestoreByElder(elder, target, now);
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

    /**
     * Where a path from {@code from} to {@code to} this tick ends at the latest on a creature: at the
     * first one on it that will stop the arrow (a shield raised towards it, one that turns arrows
     * aside, or the one met past its last pierce), or at {@code to} if none will. Server side; it
     * strikes nothing.
     */
    protected Vec3 creatureStop(Vec3 from, Vec3 to) {
        AABB sweep = getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0);
        List<Entity> met = new ArrayList<>();
        List<Vec3> where = new ArrayList<>();
        for (Entity entity : level().getEntities(this, sweep, this::canHitEntity)) {
            AABB box = entity.getBoundingBox().inflate(ENTITY_HIT_INFLATE);
            Vec3 at = box.contains(from) ? from : box.clip(from, to).orElse(null);
            if (at != null) {
                met.add(entity);
                where.add(at);
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < met.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> where.get(i).distanceToSqr(from)));
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(damageType()), this, getOwner());
        IntOpenHashSet bodies = new IntOpenHashSet();
        int pierce = pierceLeft;
        for (int i : order) {
            Entity entity = met.get(i);
            LivingEntity target = targetOf(entity);
            if (target == null || !bodies.add(target.getId())) {
                continue;
            }
            boolean shielded;
            hurting = true;
            try {
                shielded = target.isDamageSourceBlocked(source);
            } finally {
                hurting = false;
            }
            if (shielded || entity.deflection(this) != ProjectileDeflection.NONE || pierce-- <= 0) {
                return where.get(i);
            }
        }
        return to;
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

    /**
     * Only a creature the owner may harm, through its parts for a many-part body, and never one met
     * before. A Quincy who has just won the power back is passed by (see
     * {@link QuincyArtsLogic#justRestored}).
     */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || struck.contains(entity.getId())) {
            return false;
        }
        LivingEntity target = targetOf(entity);
        return target != null && !struck.contains(target.getId()) && SpiritProjectiles.mayStrike(getOwner(), target)
                && !(target instanceof Player player && QuincyArtsLogic.justRestored(player, level().getGameTime()));
    }

    /**
     * Server side: strikes the creature, a Hollow's mask if the path meets it first, then tells the
     * Quincy rules about a hit that landed. A hit that hurts spends a pierce, or ends the arrow when
     * none is left; a shield raised towards it ends it; anything else (no harm done, no shield) lets it fly on through.
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
        if (owner instanceof LivingEntity shooter) {
            // As any arrow does, so the shooter's tamed wolves join in.
            shooter.setLastHurtMob(target);
        }
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(damageType()), this, owner);
        // Judged before the blow: a Hollow it kills starts dissolving, and a dissolving mask is never struck.
        boolean mask = target instanceof Hollow hollow && HollowMask.struck(hollow, source);
        Vec3 from = position();
        Vec3 at = target.getBoundingBox().clip(from, from.add(getDeltaMovement()))
                .orElse(target.getBoundingBox().getCenter());
        boolean blocking;
        boolean hurt;
        hurting = true;
        try {
            // Judged as vanilla judges it (a shield raised towards the shot), while the arrow reads as
            // unpierceable, and before the blow too: a shield the blow breaks is no longer raised.
            blocking = target.isDamageSourceBlocked(source);
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
                fade();
            } else {
                pierceLeft--;
            }
        }
    }

    /**
     * Vanilla's flight ends by moving the arrow its whole step and letting the blocks it is then in
     * feel it. When that flight met a creature it forgot the block that ended its path, so on the
     * server the arrow bursts on that block first, before anything past it (a button on the far side
     * of a thin wall) can feel the arrow. One that {@link #sticks} is put back where the step began
     * first, so it sticks at the wall as vanilla's own hit would leave it, not a whole step on. One
     * spent by the creature it met was turned back, and the wall is no longer in its way.
     */
    @Override
    protected void checkInsideBlocks() {
        if (metCreature && !isRemoved() && !level().isClientSide() && !isSpent()) {
            metCreature = false;
            BlockHitResult wall = level().clip(new ClipContext(flightFrom, flightFrom.add(flightStep),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() == HitResult.Type.BLOCK && !EventHooks.onProjectileImpact(this, wall)) {
                if (sticks()) {
                    setPos(flightFrom);
                    setDeltaMovement(flightStep);
                    hitTargetOrDeflectSelf(wall);
                    // Vanilla moves an arrow by what its hit left of its step: to just short of the wall.
                    setPos(position().add(getDeltaMovement()));
                } else {
                    hitTargetOrDeflectSelf(wall);
                }
                if (isRemoved()) {
                    return;
                }
            }
        }
        super.checkInsideBlocks();
    }

    /** Turned back (by a Breeze): the wall ahead on the path it was flying is no longer in its way. */
    @Override
    protected void onDeflection(@Nullable Entity deflector, boolean byAttack) {
        super.onDeflection(deflector, byAttack);
        metCreature = false;
    }

    /**
     * The first block it meets still feels the hit (a target block, a bell), as with any projectile;
     * then, on the server, the arrow bursts and is gone. A client flies on until it hears so. One
     * that {@link #sticks} sticks into the block as a vanilla arrow does, on both sides, instead.
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (sticks()) {
            super.onHitBlock(result);
            onStuck(result);
            return;
        }
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
