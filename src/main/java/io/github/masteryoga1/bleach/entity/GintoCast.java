package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.soul.SprengerShape;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a Gintō spell leaves standing for a moment: the mark of a Gritz binding, a Wolke cloud that
 * takes the fall out of anyone who drops onto it, the pillar of Heizen, or the blast of a Sprenger
 * among five Seele Schneider. Heizen and Sprenger strike once, when the fuse runs out, for the
 * caster: Heizen everything its owner may harm in a pillar 5 blocks across and 6 high, Sprenger
 * everything inside the pentagon of blades (which it holds until then and then uses up), leaving
 * those it does not kill bound by Gritz. Neither touches a block. The spell, size and fuse are synced for drawing; the rest
 * only the server knows. Never saved: a cast whose chunk unloads is gone.
 */
public class GintoCast extends Entity {
    public static final ResourceKey<DamageType> HEIZEN = ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("heizen"));
    public static final ResourceKey<DamageType> SPRENGER = ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("sprenger"));

    private static final EntityDataAccessor<Byte> DATA_SPELL =
            SynchedEntityData.defineId(GintoCast.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(GintoCast.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FUSE =
            SynchedEntityData.defineId(GintoCast.class, EntityDataSerializers.INT);

    /** How far below a Heizen's foot, and a Wolke's, a creature still counts as inside it. */
    private static final double BELOW = 0.5;
    /** How high above a Wolke a falling creature is already caught. */
    private static final double WOLKE_HEIGHT = 3.0;
    /** How far a cast is drawn from, in blocks: farther than a box this small would allow. */
    private static final double RENDER_DISTANCE = 64.0;
    private static final int HEIZEN_PARTICLES = 40;
    private static final int SPRENGER_PARTICLES = 60;

    /** The caster, as spawned, so a caster who is not in the level (as in a game test) is still found. */
    @Nullable
    private Player caster;
    @Nullable
    private UUID ownerId;
    private float strength;
    private int lifeTicks = 1;
    @Nullable
    private SprengerShape shape;
    private List<SeeleSchneiderPfeil> blades = List.of();
    /** Whether the fuse has run out and the spell has struck. */
    private boolean fired;

    public GintoCast(EntityType<? extends GintoCast> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * Raises a cast of this spell at {@code at} for its owner and adds it to the level: {@code radius}
     * across (a Wolke's or a Sprenger's), striking with {@code strength} after {@code fuseTicks}
     * ticks (Heizen and Sprenger), and gone after {@code lifeTicks}. A Sprenger needs its shape, and
     * holds its blades (see {@link SeeleSchneiderPfeil#arm}) until it strikes and uses them up.
     */
    public static GintoCast spawn(ServerLevel level, Player owner, GintoSpell spell, Vec3 at, float radius, int fuseTicks,
                                  int lifeTicks, float strength, @Nullable SprengerShape shape,
                                  List<SeeleSchneiderPfeil> blades) {
        GintoCast cast = new GintoCast(ModEntities.GINTO_CAST.get(), level);
        cast.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        cast.entityData.set(DATA_SPELL, (byte) spell.ordinal());
        cast.entityData.set(DATA_RADIUS, radius);
        cast.entityData.set(DATA_FUSE, Math.max(0, fuseTicks));
        cast.caster = owner;
        cast.ownerId = owner.getUUID();
        cast.lifeTicks = Math.max(1, lifeTicks);
        cast.strength = strength;
        cast.shape = shape;
        cast.blades = List.copyOf(blades);
        if (level.addFreshEntity(cast)) {
            for (SeeleSchneiderPfeil blade : cast.blades) {
                blade.arm(true);
            }
        }
        return cast;
    }

    /**
     * How much a Heizen deals to one creature: this share of the caster's strength, twice as much
     * against a Hollow or anything like one, never more than the configured cap.
     */
    public static float heizenDamage(float strength, boolean hollow) {
        float damage = (float) (BleachConfig.get(BleachConfig.HEIZEN_DAMAGE_MULTIPLIER) * strength * (hollow ? 2.0 : 1.0));
        return Math.min((float) BleachConfig.get(BleachConfig.HEIZEN_MAX_DAMAGE), damage);
    }

    public GintoSpell spell() {
        return GintoSpell.BY_ID.apply(entityData.get(DATA_SPELL));
    }

    /** How far across from its middle a Wolke or a Sprenger reaches. */
    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    /** How many ticks after it rises a Heizen or a Sprenger strikes. */
    public int fuse() {
        return entityData.get(DATA_FUSE);
    }

    /** The caster, or null if they have gone. Server only. */
    @Nullable
    public Player owner() {
        if (caster != null && !caster.isRemoved()) {
            return caster;
        }
        return ownerId != null && level() instanceof ServerLevel server ? server.getPlayerByUUID(ownerId) : null;
    }

    /** Whether this is a Wolke and the creature is on it or just above it: within its radius, and up to 3 blocks over it. */
    public boolean cushions(Entity entity) {
        if (spell() != GintoSpell.WOLKE) {
            return false;
        }
        double dx = entity.getX() - getX();
        double dz = entity.getZ() - getZ();
        double dy = entity.getY() - getY();
        double radius = radius();
        return dx * dx + dz * dz <= radius * radius && dy >= -BELOW && dy <= WOLKE_HEIGHT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPELL, (byte) 0);
        builder.define(DATA_RADIUS, 0.0F);
        builder.define(DATA_FUSE, 0);
    }

    /**
     * On the server: a Wolke takes the fall out of everyone on it, every tick; Heizen and Sprenger
     * strike once the fuse has run out, if their caster is still here (and a Sprenger's five blades
     * still stand, or it lets them go unused); and the cast goes when its time is up.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        GintoSpell spell = spell();
        if (spell == GintoSpell.WOLKE) {
            cushionFalls();
        }
        if (!fired && tickCount >= fuse()) {
            fired = true;
            Player owner = owner();
            if (owner != null && spell == GintoSpell.HEIZEN) {
                heizen(server, owner);
            } else if (spell == GintoSpell.SPRENGER) {
                if (owner != null && shape != null && bladesStand()) {
                    sprenger(server, owner, shape);
                } else {
                    release();
                    if (owner != null) {
                        owner.displayClientMessage(Component.translatable("message.bleach.sprenger.broken")
                                .withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
        }
        if (tickCount >= lifeTicks) {
            discard();
        }
    }

    /** Whether every blade of a Sprenger's formation still stands stuck where it was cast with. */
    private boolean bladesStand() {
        for (SeeleSchneiderPfeil blade : blades) {
            if (blade.isRemoved() || !blade.planted()) {
                return false;
            }
        }
        return !blades.isEmpty();
    }

    /** Lets go of the blades still standing, so they can be taken up again. */
    private void release() {
        for (SeeleSchneiderPfeil blade : blades) {
            if (!blade.isRemoved()) {
                blade.arm(false);
            }
        }
    }

    /** Gone, by its time running out, its chunk unloading or anything else: any blade it still holds is let go. */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        release();
    }

    private void cushionFalls() {
        double radius = radius();
        AABB around = new AABB(getX() - radius, getY() - BELOW, getZ() - radius,
                getX() + radius, getY() + WOLKE_HEIGHT, getZ() + radius);
        // Searched a little wider, so a creature standing right on an edge is not missed; cushions() decides.
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, around.inflate(0.5), this::cushions)) {
            living.resetFallDistance();
        }
    }

    /** Strikes every creature the caster may harm whose feet are in the pillar, once. */
    private void heizen(ServerLevel server, Player owner) {
        double half = QuincyArtsLogic.HEIZEN_HALF_WIDTH;
        double top = QuincyArtsLogic.HEIZEN_HEIGHT - BELOW;
        AABB pillar = new AABB(getX() - half, getY() - BELOW, getZ() - half, getX() + half, getY() + top, getZ() + half);
        DamageSource source = source(HEIZEN, owner);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, pillar.inflate(1.0),
                living -> living != owner && feetIn(living, pillar) && SpiritProjectiles.mayStrike(owner, living))) {
            target.hurt(source, heizenDamage(strength, QuincyLogic.isHollowLike(target)));
        }
        Vec3 middle = pillar.getCenter();
        SpiritSenses.particles(server, ParticleTypes.END_ROD, middle.x, middle.y, middle.z, HEIZEN_PARTICLES,
                half * 0.4, top * 0.4, half * 0.4, 0.02);
        SpiritSenses.play(server, getX(), getY(), getZ(), ModSounds.HEIZEN.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
    }

    /**
     * Strikes every creature the caster may harm inside the pentagon, once, binds those left standing
     * with Gritz, and uses up the blades.
     */
    private void sprenger(ServerLevel server, Player owner, SprengerShape shape) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : shape.corners()) {
            minX = Math.min(minX, corner.x);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxZ = Math.max(maxZ, corner.z);
        }
        // As far as SprengerShape#contains reaches: a block below the lowest corner, five above the highest.
        AABB around = new AABB(minX, shape.minY() - 1.0, minZ, maxX, shape.maxY() + 5.0, maxZ).inflate(1.0);
        DamageSource source = source(SPRENGER, owner);
        float damage = Math.min((float) BleachConfig.get(BleachConfig.SPRENGER_MAX_DAMAGE),
                (float) (BleachConfig.get(BleachConfig.SPRENGER_DAMAGE_MULTIPLIER) * strength));
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, around,
                living -> living != owner && shape.contains(living.position()) && SpiritProjectiles.mayStrike(owner, living))) {
            target.hurt(source, damage);
            if (target.isAlive()) {
                target.addEffect(new MobEffectInstance(ModEffects.GRITZ, QuincyArtsLogic.SPRENGER_SURVIVOR_GRITZ_TICKS, 0,
                        false, false, true), owner);
            }
        }
        for (SeeleSchneiderPfeil blade : blades) {
            if (!blade.isRemoved()) {
                blade.discard();
            }
        }
        double spread = radius() * 0.5;
        SpiritSenses.particles(server, ParticleTypes.END_ROD, getX(), getY() + 1.0, getZ(), SPRENGER_PARTICLES,
                spread, 1.0, spread, 0.05);
        SpiritSenses.play(server, getX(), getY(), getZ(), ModSounds.SPRENGER.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
    }

    private static boolean feetIn(Entity entity, AABB box) {
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        return x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ;
    }

    /** A blow of this type dealt by the cast for its caster, built as a Heilig Pfeil builds its own. */
    private DamageSource source(ResourceKey<DamageType> type, Player owner) {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                this, owner);
    }

    /** As far round as it is drawn: a Wolke's or a Sprenger's reach, or a Heizen pillar. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double reach = Math.max(radius(), QuincyArtsLogic.HEIZEN_HALF_WIDTH);
        return new AABB(getX() - reach, getY() - BELOW, getZ() - reach,
                getX() + reach, getY() + QuincyArtsLogic.HEIZEN_HEIGHT, getZ() + reach);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }
}
