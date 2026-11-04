package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.Bala;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.KidoWard;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bakudō #81 Dankū: a pale wall of light raised a little before its caster, square to the nearest compass direction,
 * for its ward's seconds. It stops every Hadō and every projectile shot at it, its own side's too, and stands; a Cero,
 * Bala, Gran Rey Cero, Getsuga Tenshō, Heilig Pfeil or Letzt Pfeil is weighed by its shooter's reiatsu against the
 * wall's strength ({@link #withstands}), and one heavier shatters it and goes on. Bodies, blows and binds pass it. It
 * goes at its time, when its caster dies, goes or leaves the level, and when shattered. The server decides everything;
 * a client draws it from its synced facing, size, colour and end. Never saved (its type is {@code noSave}), and hidden
 * from the unaware as a spirit ({@code #bleach:spirits}).
 */
public class DankuWall extends Entity {
    /** How thick it stands, in blocks, along its facing. */
    public static final double THICKNESS = 0.25;
    /** The farthest before its caster it rises, whatever its spell says; the nearest is a block. */
    public static final double MAX_DISTANCE = 8.0;
    /** The widest it is either side of its middle, whatever its spell says, so the lookups still find it. */
    public static final float MAX_HALF_WIDTH = 2.75F;
    /** The least half-width and height it has. */
    public static final float MIN_SIZE = 0.25F;
    /**
     * How much further than a line a search for walls reaches. A lookup finds an entity by its position, here the
     * wall's bottom middle, and looks only 2 blocks to the sides of the box it is asked about and 4 below, so a wall met
     * at its end or near its top would be missed without it.
     */
    public static final double LOOKUP_MARGIN = 1.0;

    /** What a wall meets, for its weight ({@link #weight}): a Hadō and any other projectile weigh nothing. */
    public enum Art { HADO, PROJECTILE, BALA, CERO, GRAN_REY_CERO, GETSUGA, HEILIG_PFEIL, LETZT_PFEIL }

    /** Where a line from its start meets a wall, and how far along it. */
    public record Meeting(DankuWall wall, Vec3 at, double distance) {
    }

    /** The walls a line meets, nearest first, met in turn as a shot goes along it. */
    public static final class Along {
        private final List<Meeting> meetings;
        /** The first meeting not yet taken. */
        private int next;

        private Along(List<Meeting> meetings) {
            this.meetings = meetings;
        }

        /**
         * Takes in turn the walls met at most this far along: each the shot outweighs shatters, and the first that holds
         * rings and is returned, where the shot ends. Null when none of them holds. Server side.
         */
        @Nullable
        public Meeting upTo(double distance, @Nullable Entity shooter, Art art) {
            while (next < meetings.size() && meetings.get(next).distance() <= distance) {
                Meeting meeting = meetings.get(next++);
                if (meeting.wall().meets(shooter, art)) {
                    meeting.wall().stopLook(meeting.at());
                    return meeting;
                }
            }
            return null;
        }

        /**
         * How far along the first wall not yet taken that would hold this shot stands, or {@link Double#MAX_VALUE} when
         * none would. Changes nothing.
         */
        public double holdsAt(@Nullable Entity shooter, Art art) {
            for (int i = next; i < meetings.size(); i++) {
                Meeting meeting = meetings.get(i);
                if (meeting.wall().standing() && meeting.wall().withstands(shooter, art)) {
                    return meeting.distance();
                }
            }
            return Double.MAX_VALUE;
        }
    }

    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(DankuWall.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Float> DATA_HALF_WIDTH =
            SynchedEntityData.defineId(DankuWall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(DankuWall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOUR =
            SynchedEntityData.defineId(DankuWall.class, EntityDataSerializers.INT);
    /** The game time it is gone. */
    private static final EntityDataAccessor<Long> DATA_ENDS_AT =
            SynchedEntityData.defineId(DankuWall.class, EntityDataSerializers.LONG);

    private static final float TICKS_PER_SECOND = 20.0F;
    /** How many of the spell's particles flare over it as it rises, and how fast the motes drift. */
    private static final int RISE_MOTES = 24;
    private static final double MOTE_SPEED = 0.02;
    /** The ring of a shot it stops, and the motes there. */
    private static final float STOP_VOLUME = 1.0F;
    private static final float STOP_PITCH = 1.4F;
    private static final int STOP_MOTES = 4;
    private static final double STOP_SPREAD = 0.1;
    /** The breaking glass of a wall shattered, and the motes scattered over it. */
    private static final float SHATTER_VOLUME = 1.2F;
    private static final float SHATTER_PITCH = 0.8F;
    private static final int SHATTER_MOTES = 40;
    private static final double SHATTER_SPREAD = 0.05;
    private static final double SHATTER_SPEED = 0.05;
    /** What an arrow it stops keeps of its motion, turned back, so it drops before the wall. */
    private static final double ARROW_BOUNCE = -0.1;

    // Server side only, never synced or saved.
    @Nullable
    private LivingEntity caster;
    @Nullable
    private KidoSpell spell;
    private float strength;
    /** The game time of the last stop's ring, so a volley rings once a tick. */
    private long lastLookTick = Long.MIN_VALUE;

    public DankuWall(EntityType<? extends DankuWall> type, Level level) {
        super(type, level);
    }

    // ---- Raising and meeting it ----

    /**
     * Raises this spell's wall before this caster: square to the nearest compass direction it faces, its effect's
     * {@code range} before its feet and at their height, its {@code radius} either side and its ward's {@code height}
     * tall, until its ward's {@code seconds} have passed. Its strength is the caster's reiatsu now, times the margin and
     * this share. The hand is thrown up, and those who see spirits hear the spell's sound and see its particle flare
     * over the wall. Null if the level would not take it.
     */
    @Nullable
    public static DankuWall raise(ServerLevel level, LivingEntity caster, KidoSpell spell, float share, long now) {
        KidoSpell.Effect effect = spell.effect();
        Direction facing = caster.getDirection();
        double distance = Mth.clamp(effect.range(), 1.0, MAX_DISTANCE);
        float halfWidth = Mth.clamp(effect.radius(), MIN_SIZE, MAX_HALF_WIDTH);
        float height = Math.max(effect.ward().height(), MIN_SIZE);
        Vec3 at = caster.position().add(facing.getStepX() * distance, 0.0, facing.getStepZ() * distance);
        DankuWall wall = new DankuWall(ModEntities.DANKU_WALL.get(), level);
        wall.entityData.set(DATA_FACING, facing);
        wall.entityData.set(DATA_HALF_WIDTH, halfWidth);
        wall.entityData.set(DATA_HEIGHT, height);
        wall.entityData.set(DATA_COLOUR, spell.look().colour());
        wall.entityData.set(DATA_ENDS_AT, now + Math.round(effect.ward().seconds() * TICKS_PER_SECOND));
        wall.caster = caster;
        wall.spell = spell;
        wall.strength = (float) (SoulLogic.reiatsu(caster) * BleachConfig.get(BleachConfig.KIDO_DANKU_REIATSU_MARGIN)
                * share);
        wall.moveTo(at.x, at.y, at.z, facing.toYRot(), 0.0F);
        if (!level.addFreshEntity(wall)) {
            return null;
        }
        caster.swing(InteractionHand.MAIN_HAND, true);
        KidoSpell.Look look = spell.look();
        double middle = at.y + height / 2.0;
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                at.x, middle, at.z, sound, SoundSource.PLAYERS, 1.0F, look.pitch(), false));
        SimpleParticleType particle = KidoEvents.particleOf(look);
        if (particle != null) {
            Direction across = facing.getClockWise();
            RandomSource random = wall.getRandom();
            for (int i = 0; i < RISE_MOTES; i++) {
                double side = (random.nextDouble() * 2.0 - 1.0) * halfWidth;
                SpiritSenses.particles(level, particle, at.x + across.getStepX() * side, at.y + random.nextDouble() * height,
                        at.z + across.getStepZ() * side, 1, 0.0, 0.0, 0.0, MOTE_SPEED);
            }
        }
        return wall;
    }

    /**
     * Every standing wall the line from {@code from} to {@code to} meets, by that line, nearest first: where it enters
     * the wall's box, or at {@code from} when it starts inside one. Changes nothing.
     */
    public static Along along(Level level, Vec3 from, Vec3 to) {
        List<Meeting> meetings = new ArrayList<>();
        for (DankuWall wall : level.getEntities(ModEntities.DANKU_WALL.get(), new AABB(from, to).inflate(LOOKUP_MARGIN),
                DankuWall::standing)) {
            AABB box = wall.getBoundingBox();
            Vec3 at = box.contains(from) ? from : box.clip(from, to).orElse(null);
            if (at != null) {
                meetings.add(new Meeting(wall, at, at.distanceTo(from)));
            }
        }
        meetings.sort(Comparator.comparingDouble(Meeting::distance));
        return new Along(meetings);
    }

    /**
     * The first wall on this line that holds this art from this shooter, shattering on the way those it outweighs, or
     * null when none holds. Server side.
     */
    @Nullable
    public static Meeting meet(Level level, Vec3 from, Vec3 to, @Nullable Entity shooter, Art art) {
        return along(level, from, to).upTo(Double.MAX_VALUE, shooter, art);
    }

    /** Whether a wall on this line would hold this art from this shooter. Shatters nothing. */
    public static boolean shelters(Level level, Vec3 from, Vec3 to, @Nullable Entity shooter, Art art) {
        return along(level, from, to).holdsAt(shooter, art) < Double.MAX_VALUE;
    }

    /** How heavily an art weighs against a wall, times its shooter's reiatsu, from the server config; 0 for no weight. */
    public static double weight(Art art) {
        return switch (art) {
            case HADO, PROJECTILE -> 0.0;
            case BALA -> BleachConfig.get(BleachConfig.KIDO_DANKU_WEIGHT_BALA);
            case CERO -> BleachConfig.get(BleachConfig.KIDO_DANKU_WEIGHT_CERO);
            case GRAN_REY_CERO -> BleachConfig.get(BleachConfig.KIDO_DANKU_WEIGHT_GRAN_REY_CERO);
            case GETSUGA -> BleachConfig.get(BleachConfig.KIDO_DANKU_WEIGHT_GETSUGA);
            case HEILIG_PFEIL -> BleachConfig.get(BleachConfig.KIDO_DANKU_WEIGHT_HEILIG_PFEIL);
            case LETZT_PFEIL -> BleachConfig.get(BleachConfig.KIDO_DANKU_WEIGHT_LETZT_PFEIL);
        };
    }

    /** The art of a shot through the Cero's engine, by its damage: a Hadō, a Bala, a Gran Rey Cero, or else a Cero. */
    public static Art artOf(CeroBeam.Kind kind) {
        ResourceKey<DamageType> damage = kind.damage();
        if (KidoEvents.HADO.equals(damage)) {
            return Art.HADO;
        }
        if (Bala.DAMAGE.equals(damage)) {
            return Art.BALA;
        }
        return GranReyCero.DAMAGE.equals(damage) ? Art.GRAN_REY_CERO : Art.CERO;
    }

    /** The art of a projectile: a Letzt Pfeil, another arrow of light (Seele Schneider's too), or any other. */
    public static Art artOf(Projectile projectile) {
        if (projectile instanceof LetztPfeil) {
            return Art.LETZT_PFEIL;
        }
        return projectile instanceof HeiligPfeil ? Art.HEILIG_PFEIL : Art.PROJECTILE;
    }

    /**
     * A shooter's reiatsu as a wall weighs it, Blut Arterie's share counted as it is against a defender; nothing for a
     * shot with no living shooter.
     */
    public static float reiatsuOf(@Nullable Entity shooter) {
        return shooter instanceof LivingEntity living ? SoulLogic.reiatsu(living) * QuincyLogic.reiatsuFactor(living) : 0.0F;
    }

    /** Whether it still stands: not gone, and its time not yet up. Both sides. */
    public boolean standing() {
        return !isRemoved() && level().getGameTime() < endsAt();
    }

    /**
     * Whether it would hold this art from this shooter: the shooter's reiatsu times the art's weight is at most its
     * strength (a tie holds), and an art of no weight is always held. Changes nothing.
     */
    public boolean withstands(@Nullable Entity shooter, Art art) {
        double weight = weight(art);
        return weight <= 0.0 || reiatsuOf(shooter) * weight <= strength;
    }

    /**
     * Meets this art from this shooter: true if it stands and holds it; false if it no longer stands, or if the shot
     * outweighs it, when it shatters. Server side.
     */
    public boolean meets(@Nullable Entity shooter, Art art) {
        if (!standing()) {
            return false;
        }
        if (withstands(shooter, art)) {
            return true;
        }
        shatter();
        return false;
    }

    /**
     * It breaks: those who see spirits hear glass break and see motes scatter over it, its caster reads that it broke
     * on its action bar, and it is gone. Server side, once.
     */
    public void shatter() {
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        float height = height();
        double middle = getY() + height / 2.0;
        Direction across = facing().getClockWise();
        SpiritSenses.play(level, getX(), middle, getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, SHATTER_VOLUME,
                SHATTER_PITCH, false);
        SpiritSenses.particles(level, ParticleTypes.END_ROD, getX(), middle, getZ(), SHATTER_MOTES,
                Math.abs(across.getStepX()) * halfWidth() / 2.0 + SHATTER_SPREAD, height / 4.0,
                Math.abs(across.getStepZ()) * halfWidth() / 2.0 + SHATTER_SPREAD, SHATTER_SPEED);
        if (caster instanceof Player player && spell != null) {
            player.displayClientMessage(Component.translatable(KidoWard.BROKEN_KEY, spell.title())
                    .withStyle(ChatFormatting.GRAY), true);
        }
        discard();
    }

    /** Rings where it stopped a shot, for those who see spirits, at most once a tick. Server side. */
    public void stopLook(Vec3 at) {
        if (!(level() instanceof ServerLevel level) || level.getGameTime() == lastLookTick) {
            return;
        }
        lastLookTick = level.getGameTime();
        SpiritSenses.play(level, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, STOP_VOLUME,
                STOP_PITCH, false);
        SpiritSenses.particles(level, ParticleTypes.END_ROD, at.x, at.y, at.z, STOP_MOTES, STOP_SPREAD, STOP_SPREAD,
                STOP_SPREAD, MOTE_SPEED);
    }

    /** The way it faces, which its thickness runs along: the way its caster faced. */
    public Direction facing() {
        return entityData.get(DATA_FACING);
    }

    /** How far it reaches either side of its middle, across its facing. */
    public float halfWidth() {
        return entityData.get(DATA_HALF_WIDTH);
    }

    /** How tall it stands from its feet. */
    public float height() {
        return entityData.get(DATA_HEIGHT);
    }

    /** Its spell's colour, as {@code 0xRRGGBB}. */
    public int colour() {
        return entityData.get(DATA_COLOUR);
    }

    /** The game time it is gone. */
    public long endsAt() {
        return entityData.get(DATA_ENDS_AT);
    }

    /** What it withstands: its caster's reiatsu at the cast, times the margin and the cast's share. 0 on a client. */
    public float strength() {
        return strength;
    }

    /** Who raised it. Server side only. */
    @Nullable
    public LivingEntity caster() {
        return caster;
    }

    // ---- The entity ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FACING, Direction.NORTH);
        builder.define(DATA_HALF_WIDTH, 0.5F);
        builder.define(DATA_HEIGHT, 1.0F);
        builder.define(DATA_COLOUR, 0xFFFFFF);
        builder.define(DATA_ENDS_AT, 0L);
    }

    /** Its box follows its facing and size, so a client builds it as it learns them with the wall. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_FACING.equals(key) || DATA_HALF_WIDTH.equals(key) || DATA_HEIGHT.equals(key)) {
            setBoundingBox(makeBoundingBox());
        }
    }

    /**
     * A slab {@link #THICKNESS} thick along its facing and its half-width either side across it, rising from its feet.
     * Reads only the synced data, which the entity has before its first box is made.
     */
    @Override
    protected AABB makeBoundingBox() {
        boolean eastWest = facing().getAxis() == Direction.Axis.X;
        double across = halfWidth();
        double along = THICKNESS / 2.0;
        double dx = eastWest ? along : across;
        double dz = eastWest ? across : along;
        Vec3 at = position();
        return new AABB(at.x - dx, at.y, at.z - dz, at.x + dx, at.y + height(), at.z + dz);
    }

    /**
     * Server side, it goes at its time, or as soon as its caster is dead or gone or in another level. Nothing else: it
     * never moves, and no water, fire or portal touches it.
     */
    @Override
    public void tick() {
        if (level().isClientSide()) {
            return;
        }
        LivingEntity raiser = caster;
        if (!standing() || raiser == null || raiser.isRemoved() || raiser.isDeadOrDying() || raiser.level() != level()) {
            discard();
        }
    }

    /** Every projectile meets it while it stands, though no aim or blow does: it is not pickable. */
    @Override
    public boolean canBeHitByProjectile() {
        return standing();
    }

    /** While it stands it stops what meets it (see stop); the deflection itself runs only on the server. */
    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        return standing() ? (shot, deflector, rng) -> stop(shot) : ProjectileDeflection.NONE;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    /** Never saved (see the type's noSave). */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // ---- Helpers ----

    /**
     * Stops a projectile that met it: an arrow of light fades as it does at a shield, another arrow drops before the
     * wall to be picked up, and anything else is gone. Then it rings where the projectile's path met it. Server side.
     */
    private void stop(Projectile projectile) {
        if (level().isClientSide()) {
            return;
        }
        Vec3 from = projectile.position();
        Vec3 at = getBoundingBox().clip(from, from.add(projectile.getDeltaMovement())).orElse(from);
        if (projectile instanceof HeiligPfeil pfeil) {
            pfeil.fade();
        } else if (projectile instanceof AbstractArrow arrow) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(ARROW_BOUNCE));
            arrow.hasImpulse = true;
        } else {
            projectile.discard();
        }
        stopLook(at);
    }
}
