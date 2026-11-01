package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.ArrancarNpcLogic;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import org.joml.Vector3f;

/**
 * A Cero, the "hollow flash": reiatsu a Menos gathers into a glowing orb at its mouth and looses all at
 * once as a beam (a Hollow player from Gillian up does too, see
 * {@link io.github.masteryoga1.bleach.soul.HollowCero}; an Arrancar or a masked Visored will later). This
 * entity is both halves. While it charges it sits at its caster's muzzle and grows; a caster that is
 * seized, reeling, struck on its mask or otherwise kept from it lets it fizzle ({@link #cancel}). A little
 * before it fires the aim stops following the prey ({@link Spec#aimLockTicks}), so a quick soul can step
 * out of the line.
 *
 * <p>Then {@link #fire} strikes the whole line at once: every creature on it the caster may strike
 * ({@link #mayStrike}), nearest first and each once, until a shield raised towards the mouth takes it
 * or a wall ends it. Where it meets a block it bursts, hurting what stands about (never what the beam
 * already struck) and blasting soft blocks away where mob griefing allows. What is left is the picture
 * of the beam, which fades over {@link #FADE_TICKS}.
 *
 * <p>Other shots strike the same way, as a {@link Kind} of their own: an Arrancar's Bala is a quiet one that
 * stops at the first creature (see {@link io.github.masteryoga1.bleach.soul.Bala}), and is seen as a short
 * {@link #trace}; an Arrancar's Gran Rey Cero gathers and fires as this entity, with a kind of its own (see
 * {@link io.github.masteryoga1.bleach.soul.GranReyCero}). A shot that stops at its last creature may burst
 * there too ({@link #burstAt}), as a Hadō does. A Hadō gathered in the palm is this entity's orb too, charged as a
 * quiet kind, and a Hadō's gust finds what it reaches with {@link #inCone}.
 *
 * <p>The server decides everything; a client only keeps the orb at the caster's mouth and draws it, then
 * the beam, from the synced data. It is never saved. A {@code /summon} with {@code Reach} raises a
 * beam as a picture that strikes nothing (see {@link #readAdditionalSaveData}).
 */
public class CeroBeam extends Entity {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("cero"));
    /** The red of a Menos's Cero. */
    public static final int MENOS_RED = 0xDB141F;
    /** The near-white heart of the orb and the beam. */
    public static final int HOT_CORE = 0xFFE0E0;
    /** Ticks a fired beam stays to be seen, fading all the while. */
    public static final int FADE_TICKS = 12;
    /** The longest a summoned picture of a beam stays, in ticks. */
    public static final int MAX_DISPLAY_LIFE = 200;
    /** Drawn out to this many blocks from the muzzle: eight chunks. */
    public static final double RENDER_DISTANCE = 128.0;
    /**
     * How far ahead of the eyes a caster without a mouth of its own (a player) gathers the Cero, in blocks
     * at its size of 1: it grows with the caster ({@link #muzzleOf}).
     */
    public static final double LOOK_MUZZLE_AHEAD = 0.6;
    /** How far short of a wall right in front of the face a looking shooter's Cero gathers. */
    private static final double MUZZLE_WALL_MARGIN = 0.1;
    /** How far back along the beam from where it bursts the blast looks for what it can reach, in blocks. */
    public static final double BLAST_CLEARANCE = 0.2;
    /** The volume of the hiss of a Cero that fizzles. */
    public static final float FIZZLE_VOLUME = 0.8F;

    /** The caster's entity id, or -1 for none, so a client can keep the orb at its mouth. */
    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(CeroBeam.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE_TICKS =
            SynchedEntityData.defineId(CeroBeam.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(CeroBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOUR =
            SynchedEntityData.defineId(CeroBeam.class, EntityDataSerializers.INT);
    /** From the muzzle to where the beam stopped, once fired. */
    private static final EntityDataAccessor<Vector3f> DATA_REACH =
            SynchedEntityData.defineId(CeroBeam.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_FIRED =
            SynchedEntityData.defineId(CeroBeam.class, EntityDataSerializers.BOOLEAN);
    /** Ticks the beam is shown for after firing. */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(CeroBeam.class, EntityDataSerializers.INT);

    /** The half-width of a summoned picture that names none. */
    private static final float DEFAULT_RADIUS = 0.5F;
    /** The longest beam, in blocks, as the longest range a spec allows. */
    private static final float MAX_REACH = 128.0F;
    private static final float CHARGE_VOLUME = 2.0F;
    private static final float FIRE_VOLUME = 2.5F;
    private static final float FIZZLE_PITCH = 1.6F;
    private static final int FIZZLE_SMOKE = 6;
    private static final float BLAST_VOLUME = 2.0F;
    private static final float BLAST_PITCH = 1.2F;
    private static final int BLAST_SMOKE = 12;
    private static final double BLAST_SMOKE_SPREAD = 0.6;
    /**
     * The puff a quiet shot ({@link Kind#quiet}) leaves where a wall or a shield stops it, or where it bursts at its last
     * creature ({@link #burstAt}), in place of a burst.
     */
    private static final int QUIET_SMOKE = 4;
    private static final double QUIET_SMOKE_SPREAD = 0.1;
    private static final double SMOKE_SPEED = 0.02;
    /** Below this squared length the beam runs too nearly straight up or down to push anything along it. */
    private static final double FLAT_EPSILON = 1.0E-6;
    /** A box weighed this close to a cone's apex is in the cone whatever the angle (see {@link #inCone}). */
    private static final double APEX_EPSILON = 1.0E-3;
    /** How far short of unloaded ground a beam stops, so looking along it never loads a chunk. */
    private static final double UNLOADED_MARGIN = 0.01;

    /** What this Cero does when it fires. Server only; null on a summoned picture. */
    @Nullable
    private Spec spec;
    /** How it strikes when it fires: a Cero unless charged as another kind. Server only, and never saved. */
    private Kind kind = Kind.CERO;
    /** The caster, kept as charged. Server only; a client finds it by {@link #DATA_OWNER}. */
    @Nullable
    private LivingEntity shooter;
    /** The way it will fire, once the aim has locked. */
    @Nullable
    private Vec3 lockedAim;
    @Nullable
    private Shot lastShot;
    /** Told in the tick it fires, after its caster: a player's own code settles the price there. Server only. */
    @Nullable
    private Consumer<CeroBeam> onFired;
    /** {@link #tickCount} when it fired; on a client, when it learned that it had. */
    private int firedAt = -1;

    /**
     * Who charges a Cero and keeps it going: a Menos mob now; an Arrancar or a masked Visored later. A player
     * is none: its Cero gathers ahead of its eyes and fires where it looks, and its own code keeps it.
     */
    public interface Caster {
        /** Where the Cero gathers, between last tick ({@code 0}) and this one ({@code 1}). Both sides. */
        Vec3 ceroMuzzle(float partialTick);

        /** The way to fire now, as a unit vector, or null with nothing in sight to fire at. Server side. */
        @Nullable
        Vec3 ceroAim();

        /** Whether it can go on gathering; the Cero of a caster that cannot fizzles. Server side. */
        boolean keepsCharging();

        /** Its Cero has fired and done this. */
        void onCeroFired(Shot shot);

        /** Its Cero fizzled before it fired. */
        void onCeroCancelled();
    }

    /**
     * Everything one Cero is: its damage on Normal, how far it reaches and how wide it is (in blocks,
     * the half-width), the blast where it bursts on a block (its radius and the share of the damage it
     * deals), the most blocks the blast may break, the extra knockback along the beam, its colour as
     * {@code 0xRRGGBB}, the ticks it gathers, how many of those before firing the aim stays locked, and
     * whether it may break blocks at all (the server config and mob griefing must allow it too). Each
     * value is clamped so a bad config cannot break it.
     */
    public record Spec(float damage, double range, float radius, float blastRadius, float blastShare, int maxBlocks,
                       float knockback, int colour, int chargeTicks, int aimLockTicks, boolean breaksBlocks) {
        public Spec {
            damage = within(damage, 0.0F, 1_000.0F);
            range = Double.isNaN(range) ? 1.0 : Mth.clamp(range, 1.0, MAX_REACH);
            radius = within(radius, 0.05F, 8.0F);
            blastRadius = within(blastRadius, 0.0F, 16.0F);
            blastShare = within(blastShare, 0.0F, 1.0F);
            maxBlocks = Mth.clamp(maxBlocks, 0, 256);
            knockback = within(knockback, 0.0F, 10.0F);
            colour &= 0xFFFFFF;
            chargeTicks = Mth.clamp(chargeTicks, 1, 1_200);
            aimLockTicks = Mth.clamp(aimLockTicks, 0, chargeTicks);
        }

        /**
         * This Cero gathered only so far, from 0 to 1: its damage, its blast's reach, the blocks it may break
         * (rounded) and its knockback each scaled by that share. Its width, range and colour stay as they are.
         */
        public Spec weakened(float share) {
            float kept = within(share, 0.0F, 1.0F);
            return new Spec(damage * kept, range, radius, blastRadius * kept, blastShare, Math.round(maxBlocks * kept),
                    knockback * kept, colour, chargeTicks, aimLockTicks, breaksBlocks);
        }
    }

    /**
     * What one Cero did: where it started and where it stopped, whether it burst on a block (not when a
     * shield, or the last creature its {@link Kind} may strike, took it first; see {@link #burstAt} to burst
     * there) and whether a shield stopped it, the creatures the beam struck, nearest first (not those only the
     * blast caught), and how many blocks the blast broke.
     */
    public record Shot(Vec3 from, Vec3 to, boolean hitBlock, boolean blocked, List<LivingEntity> struck,
                       int blocksBroken) {
        public Shot {
            struck = List.copyOf(struck);
        }
    }

    /**
     * How a beam strikes: its damage type, whether it is quiet (no Cero sound, and a puff of smoke in place
     * of a burst where a wall or a shield stops it), and how many creatures the beam may strike that it
     * meets before it stops, hurt or not. Those it may not strike ({@link #mayStrike}) it passes, uncounted.
     */
    public record Kind(ResourceKey<DamageType> damage, boolean quiet, int pierce) {
        /** A Cero: loud, bursting, and on through every creature in its way. */
        public static final Kind CERO = new Kind(DAMAGE, false, Integer.MAX_VALUE);

        public Kind {
            pierce = Math.max(1, pierce);
        }
    }

    /**
     * Where the beam first meets a creature, or where a cone weighs it ({@link #inCone}), and the box it meets there:
     * the creature's own, or a part's.
     */
    private record Entry(LivingEntity target, Vec3 at, AABB box) {
    }

    public CeroBeam(EntityType<? extends CeroBeam> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * Starts a Cero gathering at the shooter's muzzle ({@link #muzzleOf}) and adds it to the level, with
     * the charge's sound. It fires by itself once the spec's charge is gathered, the way the shooter aimed
     * ({@link #aimOf}) when the aim locked; a caller may fire it sooner, at full strength ({@link #fireNow()})
     * or weaker ({@link #fireNow(float)}), or let it fizzle ({@link #cancel}). Returns null, adding nothing,
     * when the shooter is dead or in another level, or the level refuses the Cero. It strikes as a Cero
     * ({@link Kind#CERO}).
     */
    @Nullable
    public static CeroBeam charge(ServerLevel level, LivingEntity shooter, Spec spec) {
        return charge(level, shooter, spec, Kind.CERO);
    }

    /**
     * Starts a beam gathering as {@link #charge(ServerLevel, LivingEntity, Spec)} does, which strikes as this
     * kind when it fires, by itself or sooner, with the charge's sound unless the kind is quiet: a Gran Rey Cero is
     * gathered so, and a Hadō's orb in the palm, quietly.
     */
    @Nullable
    public static CeroBeam charge(ServerLevel level, LivingEntity shooter, Spec spec, Kind kind) {
        if (!shooter.isAlive() || shooter.level() != level) {
            return null;
        }
        CeroBeam beam = new CeroBeam(ModEntities.CERO_BEAM.get(), level);
        Vec3 muzzle = muzzleOf(shooter, 1.0F);
        beam.moveTo(muzzle.x, muzzle.y, muzzle.z, shooter.getYHeadRot(), shooter.getXRot());
        beam.spec = spec;
        beam.kind = kind;
        beam.shooter = shooter;
        beam.entityData.set(DATA_OWNER, shooter.getId());
        beam.entityData.set(DATA_CHARGE_TICKS, spec.chargeTicks());
        beam.entityData.set(DATA_RADIUS, spec.radius());
        beam.entityData.set(DATA_COLOUR, spec.colour());
        if (!level.addFreshEntity(beam)) {
            return null;
        }
        // The Warden's charge is the Cero's own sound: a quiet kind's caller gives its orb a sound of its own.
        if (!kind.quiet()) {
            SpiritSenses.play(level, muzzle.x, muzzle.y, muzzle.z, ModSounds.CERO_CHARGE.get(), shooter.getSoundSource(),
                    CHARGE_VOLUME, 1.0F, false);
        }
        return beam;
    }

    /**
     * Fires now, as the kind it was charged as, unless it already has: along the locked aim, or where the
     * shooter aims now if the aim has not locked yet. A Cero whose shooter has gone, or has nothing in sight
     * to fire at, fizzles instead. The beam is then seen for {@link #FADE_TICKS} and goes. Server only.
     */
    public void fireNow() {
        if (isFired() || isRemoved() || spec == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        LivingEntity owner = owner();
        Vec3 direction = null;
        if (owner != null && owner.isAlive() && owner.level() == level()) {
            direction = lockedAim != null ? lockedAim : aimOf(owner);
        }
        if (direction == null) {
            cancel();
            return;
        }
        // Marked as fired before it strikes, so nothing the shot sets off can make it fizzle halfway.
        firedAt = tickCount;
        entityData.set(DATA_FIRED, true);
        entityData.set(DATA_LIFE, FADE_TICKS);
        Shot shot = fire(server, owner, this, position(), direction, spec, kind);
        lastShot = shot;
        entityData.set(DATA_REACH, shot.to().subtract(shot.from()).toVector3f());
        if (owner instanceof Caster caster) {
            caster.onCeroFired(shot);
        }
        if (onFired != null) {
            onFired.accept(this);
        }
    }

    /**
     * What to do as it fires, by itself or sooner, in that same tick: a player is no {@link Caster}, so a Gran Rey
     * Cero is paid for here, with no tick between for a logout or a change of world to slip into. Server only.
     */
    public void whenFired(Consumer<CeroBeam> action) {
        onFired = action;
    }

    /**
     * Fires now, as {@link #fireNow()} does, at this share of its full strength (see {@link Spec#weakened}):
     * a Cero let go before it had fully gathered. Server only.
     */
    public void fireNow(float strength) {
        if (isFired() || isRemoved() || spec == null) {
            return;
        }
        spec = spec.weakened(strength);
        fireNow();
    }

    /**
     * Lets a Cero that has not fired fizzle out: it goes with a hiss and a little smoke, and its caster
     * is told ({@link Caster#onCeroCancelled}). Does nothing once it has fired. Server only.
     */
    public void cancel() {
        if (isFired() || isRemoved() || !(level() instanceof ServerLevel server)) {
            return;
        }
        discard();
        LivingEntity owner = owner();
        if (owner instanceof Caster caster) {
            caster.onCeroCancelled();
        }
        SoundSource category = owner != null ? owner.getSoundSource() : SoundSource.HOSTILE;
        SpiritSenses.play(server, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, category, FIZZLE_VOLUME,
                FIZZLE_PITCH, false);
        double spread = radius() * 0.5;
        SpiritSenses.particles(server, ParticleTypes.SMOKE, getX(), getY(), getZ(), FIZZLE_SMOKE, spread, spread, spread,
                SMOKE_SPEED);
    }

    /** What the Cero did when it fired, or null before then (and for one that fizzled). Server only. */
    @Nullable
    public Shot lastShot() {
        return lastShot;
    }

    /** How it strikes when it fires, as it was charged. Server only: a client always reads a Cero. */
    public Kind kind() {
        return kind;
    }

    /** Whoever charges it, or null for none or one gone from view. */
    @Nullable
    public LivingEntity owner() {
        if (shooter != null) {
            return shooter;
        }
        int id = entityData.get(DATA_OWNER);
        return id >= 0 && level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    /** Whether it is still gathering: neither fired nor gone. */
    public boolean isCharging() {
        return !isFired() && !isRemoved();
    }

    public boolean isFired() {
        return entityData.get(DATA_FIRED);
    }

    /** How many ticks the charge takes in all. */
    public int chargeTicks() {
        return entityData.get(DATA_CHARGE_TICKS);
    }

    /** How much of the charge is gathered, from 0 to 1. */
    public float chargeProgress(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / Math.max(1, chargeTicks()), 0.0F, 1.0F);
    }

    /** The beam's half-width, in blocks. */
    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    /** Its colour, as {@code 0xRRGGBB}. */
    public int colour() {
        return entityData.get(DATA_COLOUR);
    }

    /** From the muzzle to where the beam stopped; zero before it fires. */
    public Vec3 reach() {
        return new Vec3(entityData.get(DATA_REACH));
    }

    /** Ticks since it fired; 0 before then. A client counts from when it learned that it had. */
    public float firedAge(float partialTick) {
        if (!isFired() || firedAt < 0) {
            return 0.0F;
        }
        return tickCount - firedAt + partialTick;
    }

    /**
     * How strongly the fired beam is drawn: 1 until the last {@link #FADE_TICKS} of its life (all of an
     * engine beam's), then down to 0 as it ends. 1 while it charges.
     */
    public float fade(float partialTick) {
        if (!isFired()) {
            return 1.0F;
        }
        int life = Math.max(1, entityData.get(DATA_LIFE));
        float left = life - firedAge(partialTick);
        return Mth.clamp(left / Math.min(FADE_TICKS, life), 0.0F, 1.0F);
    }

    /**
     * Strikes the line at once as a Cero does ({@link Kind#CERO}): see
     * {@link #fire(ServerLevel, LivingEntity, Entity, Vec3, Vec3, Spec, Kind)}.
     */
    public static Shot fire(ServerLevel level, @Nullable LivingEntity shooter, @Nullable Entity direct, Vec3 from,
                            Vec3 dir, Spec spec) {
        return fire(level, shooter, direct, from, dir, spec, Kind.CERO);
    }

    /**
     * Strikes the line from {@code from} along {@code dir} at once, for {@code shooter} (who may be
     * gone), and returns what it did. The beam reaches the spec's range, short of the first block that
     * would stop a creature (fluids pass) and of ground that is not loaded. It strikes every creature the
     * shooter may strike ({@link #mayStrike}) whose box, or one of whose parts' boxes, it passes within
     * its half-width of with nothing solid between, nearest first and each once, with the kind's damage
     * type; one whose shield, raised towards the muzzle, takes the hit ends the beam there, and so does the
     * last creature the kind may strike ({@link Kind#pierce}), hurt or not. Those it hurts are also pushed
     * along it. If it then meets a block, it bursts: what it may strike within the blast radius and in the
     * open from there takes the blast's share of the damage, and soft blocks about the struck one are blasted
     * away, nearest first, where the spec, the server config, mob griefing and the shooter's rights allow (see
     * {@link GetsugaTensho#cuttable} and {@link GetsugaTensho#permitted}). A quiet kind makes no Cero sound,
     * and leaves only a puff of smoke where a wall or a shield stops it. {@code direct} is what fired it,
     * which a shield faces: the Cero, or the shooter itself when null. A shot with no beam of its own should
     * pass a stand-in rather than null, as a living direct entity makes a blocked hit a blow from its hand.
     */
    public static Shot fire(ServerLevel level, @Nullable LivingEntity shooter, @Nullable Entity direct, Vec3 from,
                            Vec3 dir, Spec spec, Kind kind) {
        Vec3 heading = dir.normalize();
        // A direction of zero (or not a number) goes nowhere and strikes nothing.
        if (!(heading.lengthSqr() >= 0.5)) {
            return new Shot(from, from, false, false, List.of(), 0);
        }
        Vec3 far = loadedEnd(level, from, from.add(heading.scale(spec.range())), heading);
        BlockHitResult clip = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        boolean wall = clip.getType() == HitResult.Type.BLOCK;
        Vec3 end = wall ? clip.getLocation() : far;
        double radius = spec.radius();

        List<Entry> entries = new ArrayList<>();
        for (LivingEntity target : SpiritProjectiles.creaturesIn(level, new AABB(from, end).inflate(radius),
                target -> mayStrike(shooter, target))) {
            Entry entry = entryOf(target, from, end, radius);
            // A wide beam reaches past the edge of a wall or pillar only where its middle could see.
            if (entry != null && inTheOpen(level, entry.at(), entry.box())) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingDouble(entry -> entry.at().distanceToSqr(from)));

        DamageSource source = sourceOf(level, shooter, direct, kind);
        Vec3 push = pushAlong(heading, shooter);
        List<LivingEntity> struck = new ArrayList<>();
        boolean blocked = false;
        // Whether it ran out of creatures it may strike before the wall: a Cero never does.
        boolean stopped = false;
        int met = 0;
        Vec3 to = end;
        for (Entry entry : entries) {
            LivingEntity target = entry.target();
            // Asked before the hit, which may break the shield.
            boolean shielded = target.isDamageSourceBlocked(source);
            if (target.hurt(source, spec.damage())) {
                struck.add(target);
                // As a blow or an arrow does, so a pledged Fracción joins what its Espada's Cero or Bala struck.
                if (shooter != null) {
                    shooter.setLastHurtMob(target);
                }
                if (push != null && spec.knockback() > 0.0F) {
                    target.knockback(spec.knockback(), -push.x, -push.z);
                    target.hurtMarked = true;
                }
            } else if (shielded) {
                blocked = true;
                to = entry.at();
                break;
            }
            if (++met >= kind.pierce()) {
                stopped = true;
                to = entry.at();
                break;
            }
        }

        boolean hitBlock = wall && !blocked && !stopped;
        int broken = 0;
        if (hitBlock) {
            blast(level, shooter, source, end, heading, spec, struck);
            if (spec.breaksBlocks() && BleachConfig.get(BleachConfig.CERO_BREAKS_BLOCKS)) {
                broken = carve(level, shooter, clip.getBlockPos(), spec);
            }
        }
        SoundSource category = shooter != null ? shooter.getSoundSource() : SoundSource.HOSTILE;
        if (hitBlock || blocked) {
            burstLook(level, to, kind, category);
        }
        if (!kind.quiet()) {
            SpiritSenses.play(level, from.x, from.y, from.z, ModSounds.CERO_FIRE.get(), category, FIRE_VOLUME, 1.0F,
                    false);
        }
        return new Shot(from, to, hitBlock, blocked, struck, broken);
    }

    /**
     * Bursts a shot that stopped at its last creature, as {@link #fire} bursts one that met a block: what the shooter
     * may strike within the blast radius of {@code shot.to()}, where the beam met that creature, but not what the beam
     * struck, takes the blast's share of the damage, and the kind's puff of smoke or burst is left there. It breaks no
     * blocks. Nothing happens when the shot met a block (fire burst it already) or a shield, struck fewer creatures
     * than its kind's pierce, or has no blast. {@code direct} and {@code dir} are those the shot was fired with, so the
     * blast's damage and heading are the beam's own.
     */
    public static void burstAt(ServerLevel level, @Nullable LivingEntity shooter, @Nullable Entity direct, Shot shot,
                               Vec3 dir, Spec spec, Kind kind) {
        // Struck as many as its pierce, so the beam ended at the last of them; one met but not hurt reads as no hit.
        if (shot.hitBlock() || shot.blocked() || shot.struck().size() < kind.pierce() || spec.blastRadius() <= 0.0F) {
            return;
        }
        Vec3 heading = dir.normalize();
        // A direction of zero (or not a number) went nowhere, as in fire.
        if (!(heading.lengthSqr() >= 0.5)) {
            return;
        }
        Vec3 to = shot.to();
        blast(level, shooter, sourceOf(level, shooter, direct, kind), to, heading, spec, shot.struck());
        burstLook(level, to, kind, shooter != null ? shooter.getSoundSource() : SoundSource.HOSTILE);
    }

    /**
     * A picture of a shot that strikes nothing: a fired beam from {@code shot.from()} to {@code shot.to()},
     * this wide and this colour, seen for {@code life} ticks (1 to {@link #MAX_DISPLAY_LIFE}), all of them
     * fading, and drawn from the owner's eyes as the owner's own (so it starts a little way out for it in
     * first person). Like any Cero it is shown only to those who see spirits. Returns null, adding nothing,
     * if the level refuses it.
     */
    @Nullable
    public static CeroBeam trace(ServerLevel level, @Nullable LivingEntity owner, Shot shot, float radius, int colour,
                                 int life) {
        CeroBeam beam = new CeroBeam(ModEntities.CERO_BEAM.get(), level);
        Vec3 from = shot.from();
        beam.moveTo(from.x, from.y, from.z, owner != null ? owner.getYHeadRot() : 0.0F,
                owner != null ? owner.getXRot() : 0.0F);
        if (owner != null) {
            beam.entityData.set(DATA_OWNER, owner.getId());
        }
        beam.entityData.set(DATA_RADIUS, within(radius, 0.05F, 8.0F));
        beam.entityData.set(DATA_COLOUR, colour & 0xFFFFFF);
        beam.entityData.set(DATA_REACH, shot.to().subtract(from).toVector3f());
        beam.entityData.set(DATA_FIRED, true);
        beam.entityData.set(DATA_LIFE, Mth.clamp(life, 1, MAX_DISPLAY_LIFE));
        // With no spec it never strikes: the fired branch of the server's tick only lets it go after its life.
        beam.firedAt = beam.tickCount;
        return level.addFreshEntity(beam) ? beam : null;
    }

    /**
     * Every creature the shooter may strike that stands, by its own box or one of its parts', within range of the apex
     * and within half the angle (in degrees) of the axis, in the open from the apex: nearest first. A box is weighed at
     * its point nearest the axis point nearest its centre (the axis running from the apex for the range), so a large
     * creature at the edge is caught. A box holding the apex is in, and a block between the apex and the middle of the
     * box keeps it out, as it keeps a burst off what stands behind it. Strikes nothing: a Hadō's gust asks it, and does
     * the rest. Empty for no range or no axis; the range is at most the longest beam's.
     */
    public static List<LivingEntity> inCone(ServerLevel level, @Nullable LivingEntity shooter, Vec3 apex, Vec3 axis,
                                            double range, float angle) {
        Vec3 heading = axis.normalize();
        // No reach, or a way of zero (or not a number), finds nothing.
        if (!(range > 0.0) || !(heading.lengthSqr() >= 0.5)) {
            return new ArrayList<>();
        }
        double reach = Math.min(range, MAX_REACH);
        double edge = Math.cos(Math.toRadians(angle / 2.0));
        List<Entry> entries = new ArrayList<>();
        for (LivingEntity target : SpiritProjectiles.creaturesIn(level, AABB.ofSize(apex, 2.0 * reach, 2.0 * reach,
                2.0 * reach), target -> mayStrike(shooter, target))) {
            // Its own box first, then its parts', as nearestBox walks them.
            Entry entry = coneEntry(level, target, target.getBoundingBox(), apex, heading, reach, edge);
            PartEntity<?>[] parts = target.isMultipartEntity() ? target.getParts() : null;
            if (parts != null) {
                for (int i = 0; entry == null && i < parts.length; i++) {
                    entry = coneEntry(level, target, parts[i].getBoundingBox(), apex, heading, reach, edge);
                }
            }
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingDouble(entry -> entry.at().distanceToSqr(apex)));
        List<LivingEntity> found = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            found.add(entry.target());
        }
        return found;
    }

    /**
     * Whether a Cero fired by {@code owner} may strike this creature. A Hollow's strikes only what it
     * would hunt or fight: its target, whatever last hurt it, or its prey ({@link HollowLogic#isPreyFor}),
     * and never another Hollow, a Plus (spirit or player), a player in creative or spectator mode, or
     * anything it is allied to. So the mundane creatures about, the Academy's instructor and every Plus
     * are spared: a Plus is to be eaten, not blasted. An Arrancar NPC's (its Bala too, whose owner it is)
     * strikes likewise only its target, whatever last hurt it, or its prey ({@link ArrancarNpcLogic#isPrey}),
     * and never another Arrancar NPC, its leader, a Plus, a Shinigami or Quincy NPC, or a player in creative or
     * spectator mode; a player's pledged Fracción has no prey of its own and spares what its Espada may not harm
     * ({@link ArrancarNpc#mayHarmForEspada}). Anyone else's Cero keeps the rules of every soul's projectiles
     * ({@link SpiritProjectiles#mayStrike}).
     */
    public static boolean mayStrike(@Nullable Entity owner, LivingEntity target) {
        if (owner instanceof ArrancarNpc npc) {
            if (!target.isAlive() || target instanceof ArrancarNpc || target instanceof PlusSpirit
                    || target instanceof ShinigamiNpc || target instanceof QuincyNpc
                    || target.getUUID().equals(npc.leaderId())) {
                return false;
            }
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return false;
            }
            if (npc.isPledged() && !npc.mayHarmForEspada(target)) {
                return false;
            }
            return target == npc.getTarget() || target == npc.getLastHurtByMob()
                    || !npc.isPledged() && ArrancarNpcLogic.isPrey(target);
        }
        if (!(owner instanceof Hollow hollow)) {
            return SpiritProjectiles.mayStrike(owner, target);
        }
        // Being a Hollow, the shooter spares itself too.
        if (!target.isAlive() || target instanceof Hollow || target instanceof PlusSpirit) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator() || isPlus(player))) {
            return false;
        }
        if (hollow.isAlliedTo(target)) {
            return false;
        }
        return target == hollow.getTarget() || target == hollow.getLastHurtByMob() || HollowLogic.isPreyFor(hollow, target);
    }

    /**
     * Where the shooter's Cero gathers: a {@link Caster}'s own muzzle, or a little ahead of the eyes, further
     * the bigger the shooter is, so a towering Menos player gathers it clear of its own mask. It never gathers
     * past a wall, door or pane right in front of the face, or the beam would start on the far side of it.
     */
    public static Vec3 muzzleOf(LivingEntity shooter, float partialTick) {
        if (shooter instanceof Caster caster) {
            return caster.ceroMuzzle(partialTick);
        }
        Vec3 eye = shooter.getEyePosition(partialTick);
        Vec3 view = shooter.getViewVector(partialTick);
        Vec3 ahead = eye.add(view.scale(LOOK_MUZZLE_AHEAD * shooter.getScale()));
        BlockHitResult wall = shooter.level().clip(new ClipContext(eye, ahead, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (wall.getType() == HitResult.Type.MISS) {
            return ahead;
        }
        return eye.add(view.scale(Math.max(0.0, wall.getLocation().distanceTo(eye) - MUZZLE_WALL_MARGIN)));
    }

    /** The way the shooter would fire now: a {@link Caster}'s own aim (null with nothing in sight), or where it looks. */
    @Nullable
    public static Vec3 aimOf(LivingEntity shooter) {
        return shooter instanceof Caster caster ? caster.ceroAim() : shooter.getLookAngle();
    }

    /**
     * Server side, each tick: a fired beam goes once its life is over, and a summoned picture that never
     * fired goes at once. A charging Cero fizzles if its caster has gone, died, left this level or can no
     * longer keep it; otherwise it stays at the muzzle, locks the aim when the time comes (fizzling if
     * there is nothing in sight then) and fires when the charge is gathered. A client keeps a charging
     * orb at the caster's mouth.
     */
    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (level().isClientSide()) {
            LivingEntity owner = owner();
            if (!isFired() && owner != null) {
                setOldPosAndRot();
                setPos(muzzleOf(owner, 1.0F));
            }
            return;
        }
        if (isFired()) {
            if (tickCount - firedAt >= entityData.get(DATA_LIFE)) {
                discard();
            }
            return;
        }
        if (spec == null) {
            discard();
            return;
        }
        LivingEntity owner = owner();
        if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.level() != level()
                || owner instanceof Caster caster && !caster.keepsCharging()) {
            cancel();
            return;
        }
        setPos(muzzleOf(owner, 1.0F));
        if (lockedAim == null && tickCount >= spec.chargeTicks() - spec.aimLockTicks()) {
            lockedAim = aimOf(owner);
            if (lockedAim == null) {
                cancel();
                return;
            }
        }
        if (tickCount >= spec.chargeTicks()) {
            fireNow();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_CHARGE_TICKS, 1);
        builder.define(DATA_RADIUS, DEFAULT_RADIUS);
        builder.define(DATA_COLOUR, MENOS_RED);
        builder.define(DATA_REACH, new Vector3f());
        builder.define(DATA_FIRED, false);
        builder.define(DATA_LIFE, FADE_TICKS);
    }

    /** A client starts the beam's life when it learns the Cero has fired. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_FIRED.equals(key) && level().isClientSide() && isFired()) {
            firedAt = tickCount;
        }
    }

    /**
     * Only {@code /summon} reads anything, to raise a picture of a fired beam that strikes nothing:
     * {@code Reach}, three numbers from the muzzle to the beam's end (at most 128 blocks long), and
     * optionally {@code Radius} (0.05 to 8), {@code Colour} (as {@code 0xRRGGBB}) and {@code Life}, the
     * ticks it stays (1 to 200). Without {@code Reach} it goes on its first tick.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.get("Reach") instanceof ListTag list && list.size() == 3 && list.get(0) instanceof NumericTag x
                && list.get(1) instanceof NumericTag y && list.get(2) instanceof NumericTag z) {
            Vector3f reach = new Vector3f(x.getAsFloat(), y.getAsFloat(), z.getAsFloat());
            float length = reach.length();
            if (Float.isFinite(length)) {
                if (length > MAX_REACH) {
                    reach.mul(MAX_REACH / length);
                }
                entityData.set(DATA_REACH, reach);
                entityData.set(DATA_FIRED, true);
                firedAt = 0;
            }
        }
        if (tag.contains("Radius", Tag.TAG_ANY_NUMERIC)) {
            entityData.set(DATA_RADIUS, within(tag.getFloat("Radius"), 0.05F, 8.0F));
        }
        if (tag.contains("Colour", Tag.TAG_ANY_NUMERIC)) {
            entityData.set(DATA_COLOUR, tag.getInt("Colour") & 0xFFFFFF);
        }
        if (tag.contains("Life", Tag.TAG_ANY_NUMERIC)) {
            entityData.set(DATA_LIFE, Mth.clamp(tag.getInt("Life"), 1, MAX_DISPLAY_LIFE));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** The orb and the beam are drawn far wider than the box, so they are culled by what is drawn. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double radius = radius();
        if (isFired()) {
            return new AABB(position(), position().add(reach())).inflate(2.0 * radius + 1.0);
        }
        return getBoundingBox().inflate(3.0 * radius + 2.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Water thrown up by nothing would show a Cero to those who cannot see spirits. */
    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    /** Whether this player is a Plus. */
    private static boolean isPlus(Player player) {
        return SoulLogic.hasSoul(player) && SoulLogic.get(player).state() == SoulState.PLUS;
    }

    /** A value clamped between the bounds, the lower one for NaN. */
    private static float within(float value, float min, float max) {
        return Float.isNaN(value) ? min : Mth.clamp(value, min, max);
    }

    /** Whether the ground at this block is loaded, at any height. */
    private static boolean hasGround(Level level, BlockPos pos) {
        return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /**
     * Where a beam from {@code from} to {@code to} must stop so that it never looks into ground that is
     * not loaded, which would load it: {@code to}, or just short of where its path first enters such ground.
     */
    private static Vec3 loadedEnd(Level level, Vec3 from, Vec3 to, Vec3 heading) {
        BlockPos unloaded = BlockGetter.traverseBlocks(from, to, level,
                (ground, pos) -> hasGround(ground, pos) ? null : pos.immutable(), ground -> null);
        if (unloaded == null) {
            return to;
        }
        Vec3 edge = new AABB(unloaded).clip(from, to).orElse(from);
        return from.add(heading.scale(Math.max(0.0, edge.distanceTo(from) - UNLOADED_MARGIN)));
    }

    /**
     * Where the path from {@code from} to {@code to} first comes within {@code radius} of the creature's
     * box or one of its parts' boxes ({@code from} itself when it starts that close), or null if it never does.
     */
    @Nullable
    private static Entry entryOf(LivingEntity target, Vec3 from, Vec3 to, double radius) {
        Entry nearest = entryInto(target, target.getBoundingBox(), from, to, radius);
        PartEntity<?>[] parts = target.isMultipartEntity() ? target.getParts() : null;
        if (parts != null) {
            for (PartEntity<?> part : parts) {
                Entry entry = entryInto(target, part.getBoundingBox(), from, to, radius);
                if (entry != null && (nearest == null || entry.at().distanceToSqr(from) < nearest.at().distanceToSqr(from))) {
                    nearest = entry;
                }
            }
        }
        return nearest;
    }

    @Nullable
    private static Entry entryInto(LivingEntity target, AABB box, Vec3 from, Vec3 to, double radius) {
        AABB grown = box.inflate(radius);
        Vec3 at = grown.contains(from) ? from : grown.clip(from, to).orElse(null);
        return at == null ? null : new Entry(target, at, box);
    }

    /**
     * Where a cone from {@code apex} along {@code heading} weighs this box, and the box, or null when it is not in the
     * cone: the box's point nearest the axis point nearest its centre must lie within {@code reach} of the apex and at
     * least {@code edge} (the cosine of half the angle) along the axis, and the box's middle in the open from the apex.
     * The point is always on the box, near its point nearest the axis: for a large box at the edge of the cone the two
     * may be a degree or two apart.
     */
    @Nullable
    private static Entry coneEntry(Level level, LivingEntity target, AABB box, Vec3 apex, Vec3 heading, double reach,
                                   double edge) {
        Vec3 centre = box.getCenter();
        Vec3 at;
        if (box.contains(apex)) {
            at = apex;
        } else {
            Vec3 onAxis = apex.add(heading.scale(Mth.clamp(centre.subtract(apex).dot(heading), 0.0, reach)));
            at = new Vec3(Mth.clamp(onAxis.x, box.minX, box.maxX), Mth.clamp(onAxis.y, box.minY, box.maxY),
                    Mth.clamp(onAxis.z, box.minZ, box.maxZ));
        }
        Vec3 offset = at.subtract(apex);
        double distance = offset.length();
        // Written so that an angle that is not a number lets in only what holds the apex.
        if (!(distance <= reach) || !(distance < APEX_EPSILON || offset.dot(heading) >= edge * distance)) {
            return null;
        }
        return level.clip(new ClipContext(apex, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS ? new Entry(target, at, box) : null;
    }

    /** Whether nothing solid stands between this point and the nearest point of the box. */
    private static boolean inTheOpen(Level level, Vec3 at, AABB box) {
        Vec3 nearest = new Vec3(Mth.clamp(at.x, box.minX, box.maxX), Mth.clamp(at.y, box.minY, box.maxY),
                Mth.clamp(at.z, box.minZ, box.maxZ));
        return nearest.distanceToSqr(at) < 1.0E-6 || level.clip(new ClipContext(at, nearest, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /**
     * The damage a beam of this kind deals: of the kind's damage type, from {@code direct} (or the shooter when null),
     * caused by the shooter. Public, so what a caller adds to a shot, such as the burn a Hadō leaves, is weighed against
     * the same source.
     */
    public static DamageSource sourceOf(ServerLevel level, @Nullable LivingEntity shooter, @Nullable Entity direct,
                                        Kind kind) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(kind.damage()), direct != null ? direct : shooter, shooter);
    }

    /**
     * The way along the level to push what the beam strikes: its own heading, flattened, or, for a beam
     * straight up or down, the way the shooter faces (no way at all without one).
     */
    @Nullable
    private static Vec3 pushAlong(Vec3 heading, @Nullable LivingEntity shooter) {
        Vec3 flat = new Vec3(heading.x, 0.0, heading.z);
        if (flat.lengthSqr() >= FLAT_EPSILON) {
            return flat.normalize();
        }
        return shooter != null ? Vec3.directionFromRotation(0.0F, shooter.getYRot()) : null;
    }

    /**
     * The burst where the beam meets a block, or its last creature ({@link #burstAt}): what the shooter may
     * strike within the blast radius of {@code at}, which the beam itself did not strike and which stands in
     * the open from just short of that point, takes the blast's share of the damage.
     */
    private static void blast(ServerLevel level, @Nullable LivingEntity shooter, DamageSource source, Vec3 at,
                              Vec3 heading, Spec spec, List<LivingEntity> struck) {
        double radius = spec.blastRadius();
        float damage = spec.damage() * spec.blastShare();
        if (radius <= 0.0 || damage <= 0.0F) {
            return;
        }
        Vec3 clear = at.subtract(heading.scale(BLAST_CLEARANCE));
        AABB around = AABB.ofSize(at, 2.0 * radius, 2.0 * radius, 2.0 * radius);
        for (LivingEntity target : SpiritProjectiles.creaturesIn(level, around,
                target -> !struck.contains(target) && mayStrike(shooter, target))) {
            AABB box = nearestBox(target, at);
            if (box.distanceToSqr(at) <= radius * radius && level.clip(new ClipContext(clear, box.getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType()
                    == HitResult.Type.MISS) {
                target.hurt(source, damage);
            }
        }
    }

    /**
     * What is seen and heard where a shot bursts or a shield stops it: a quiet kind's puff of smoke, or a loud one's
     * flash, smoke and bang. Only those who see spirits see or hear it.
     */
    private static void burstLook(ServerLevel level, Vec3 at, Kind kind, SoundSource category) {
        if (kind.quiet()) {
            SpiritSenses.particles(level, ParticleTypes.SMOKE, at.x, at.y, at.z, QUIET_SMOKE, QUIET_SMOKE_SPREAD,
                    QUIET_SMOKE_SPREAD, QUIET_SMOKE_SPREAD, SMOKE_SPEED);
            return;
        }
        SpiritSenses.particles(level, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        SpiritSenses.particles(level, ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, BLAST_SMOKE, BLAST_SMOKE_SPREAD,
                BLAST_SMOKE_SPREAD, BLAST_SMOKE_SPREAD, SMOKE_SPEED);
        SpiritSenses.play(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), category, BLAST_VOLUME,
                BLAST_PITCH, false);
    }

    /** The creature's box, or the box of whichever of its parts is nearest to this point. */
    private static AABB nearestBox(LivingEntity target, Vec3 at) {
        AABB nearest = target.getBoundingBox();
        PartEntity<?>[] parts = target.isMultipartEntity() ? target.getParts() : null;
        if (parts != null) {
            for (PartEntity<?> part : parts) {
                AABB box = part.getBoundingBox();
                if (box.distanceToSqr(at) < nearest.distanceToSqr(at)) {
                    nearest = box;
                }
            }
        }
        return nearest;
    }

    /**
     * Blasts soft blocks away about the struck one: those within the blast radius of it (at least a
     * block), nearest first, until the spec's most are gone. Each must be soft enough
     * ({@link BleachConfig#CERO_MAX_HARDNESS}) and the breaking allowed, by mob griefing, the shooter's
     * rights and protection mods. Blasted blocks are gone, dropping nothing. Returns how many broke.
     */
    private static int carve(ServerLevel level, @Nullable LivingEntity shooter, BlockPos centre, Spec spec) {
        if (spec.maxBlocks() <= 0 || !EventHooks.canEntityGrief(level, shooter)) {
            return 0;
        }
        double radius = Math.max(1.0, spec.blastRadius());
        int reach = Mth.floor(radius);
        List<BlockPos> sphere = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach),
                centre.offset(reach, reach, reach))) {
            if (pos.distSqr(centre) <= radius * radius) {
                sphere.add(pos.immutable());
            }
        }
        sphere.sort(Comparator.comparingDouble(pos -> pos.distSqr(centre)));
        double maxHardness = BleachConfig.get(BleachConfig.CERO_MAX_HARDNESS);
        int broken = 0;
        for (BlockPos pos : sphere) {
            if (broken >= spec.maxBlocks()) {
                break;
            }
            if (!hasGround(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (GetsugaTensho.cuttable(state, level, pos, maxHardness)
                    && GetsugaTensho.permitted(level, pos, state, shooter, shooter, true)
                    && (shooter == null || EventHooks.onEntityDestroyBlock(shooter, pos, state))
                    && level.destroyBlock(pos, false, shooter)) {
                broken++;
            }
        }
        return broken;
    }
}
