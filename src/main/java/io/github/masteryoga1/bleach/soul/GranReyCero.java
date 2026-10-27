package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * An Arrancar's Gran Rey Cero, the strongest Cero, sealed or released: blood from its own body mixed into the
 * Cero (Ch. 280, where Grimmjow plants his sword and cuts his finger on it), in a colour of its own, here its
 * blade's. Only an Arrancar with enough maximum reiryoku has it ({@link #eligible}, a stand-in until Arrancar
 * ranks arrive); it sneaks and presses the Cero key. Sealed, it needs its own blade in the main hand to draw the
 * blood on; released, its claws do the cutting.
 *
 * <p>The blood is paid when the gather starts, as health and never as a blow, so nothing softens it and it can
 * never kill: it is refused unless enough health would be left, and creative pays none. The beam gathers as long
 * as the Cero of the form the Arrancar rose from would, and fires itself when full; let go sooner, it scatters,
 * with no reiryoku spent and no wait, but the blood stays spilt. When it fires it costs a large share of maximum
 * reiryoku, never leaving less than the {@link ZanpakutoLogic#RESERVE}, and the next must wait a minute. That
 * wait is saved in the soul's {@link HollowProgress.Arrancar} record, so a relog does not skip it; the gather
 * itself lives in the soul as transients, so nothing else is saved.
 *
 * <p>It strikes through the Cero's own engine ({@link CeroBeam} charged with {@link #KIND}), so it keeps every
 * rule a Cero keeps on PvP, teams, pets, shields and griefing, and Hierro and the reiatsu difference cut it as
 * they cut any blow. Only one beam gathers at a time: a Cero does not begin while a Gran Rey Cero gathers, nor
 * this while a Cero does, and no Bala can be thrown meanwhile. The gather fizzles, for free, when the soul stops
 * being an Arrancar, watches as a spectator, is held in a Negación light, opens a screen or dies.
 *
 * <p>The keys reach here through {@link DevourEvents#onKey}, and {@link ArrancarEvents#tick} keeps it going.
 * The numbers read only the server's settings and the synced soul, so the soul screen can show them; everything
 * else takes a {@link Player}, so game tests can use mock players, and only the milestone needs a real one.
 */
public final class GranReyCero {
    public static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("gran_rey_cero"));
    /** Loud, bursting, and on through every creature in its way, as a Cero. */
    public static final CeroBeam.Kind KIND = new CeroBeam.Kind(DAMAGE, false, Integer.MAX_VALUE);
    /** The message of a Gran Rey Cero let go before it had gathered. */
    public static final String FIZZLED = "message.bleach.gran_rey_cero.fizzled";

    /** Why a Gran Rey Cero may not begin, first that fails winning, in this order. */
    public enum Refusal {
        /** Held in a Negación light, which lets the soul touch nothing (see {@link Negacion#held}). */
        HELD,
        /** Not yet strong enough: too little maximum reiryoku. */
        LOCKED,
        /** The last one fired too short a while ago. */
        COOLDOWN,
        /** Sealed, and its own blade is not in the main hand to draw the blood on. */
        NO_BLADE,
        /** Paying for it would leave less than the reserve. */
        TOO_WEAK,
        /** Spilling the blood would leave too little health. */
        TOO_HURT;

        /** {@code message.bleach.gran_rey_cero.<name>}. */
        public String messageKey() {
            return "message.bleach.gran_rey_cero." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Dark blood, at the hand when the cut is made and dripping from it while the beam gathers. */
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.45F, 0.0F, 0.0F), 1.0F);
    private static final int CUT_BLOOD = 6;
    private static final double CUT_BLOOD_SPREAD = 0.08;
    private static final float CUT_VOLUME = 1.0F;
    /** Where the hand is, from the eyes: ahead along the look, out to the main-hand side, and down. */
    private static final double HAND_AHEAD = 0.5, HAND_SIDE = 0.35, HAND_DROP = 0.45;
    /** Ticks between two drops of blood from the hand while it gathers. */
    private static final int DRIP_TICKS = 4;
    private static final double DRIP_SPREAD = 0.03;
    /** The ring of dust about the gathering orb: its points, its radius as a share of the beam's and its turn a tick. */
    private static final int RING_POINTS = 6;
    private static final double RING_SHARE = 0.6;
    private static final double RING_TURN = Math.toRadians(30.0);
    private static final float RING_DUST_SIZE = 1.2F;
    /** Space torn along the fired beam: a mote every so many blocks, spread by this share of the beam's width. */
    private static final double WARP_STEP = 2.0;
    private static final double WARP_SHARE = 0.6;
    private static final double WARP_SPEED = 0.02;
    /** The two strands wound about the fired beam: a point every so many blocks, one turn in so many, at most so many. */
    private static final double HELIX_STEP = 1.5;
    private static final double HELIX_TURN_LENGTH = 6.0;
    private static final int HELIX_MAX_POINTS = 48;
    private static final float HELIX_DUST_SIZE = 1.5F;
    private static final float FIRE_VOLUME = 2.5F;
    /** Below this length a fired beam went nowhere, and nothing is drawn along it. */
    private static final double SHORTEST_SHOT = 1.0E-3;
    /** The Spec's own clamp on damage, which a released Gran Rey Cero never passes. */
    private static final double MAX_DAMAGE = 1_000.0;

    private GranReyCero() {
    }

    // ---- The numbers (all safe on the client: config and synced soul only) ----

    /** Whether this soul has the Gran Rey Cero: an Arrancar with at least {@link #requiredMaxReiryoku}. */
    public static boolean eligible(SoulData data) {
        return ArrancarLogic.isArrancar(data) && data.maxReiryoku() >= requiredMaxReiryoku();
    }

    /** The maximum reiryoku an Arrancar needs for a Gran Rey Cero, standing in for its rank. */
    public static float requiredMaxReiryoku() {
        return (float) BleachConfig.get(BleachConfig.GRAN_REY_CERO_MIN_MAX_REIRYOKU);
    }

    /**
     * A Gran Rey Cero's damage: a multiple of the Arrancar's own sealed Cero (see {@link HollowCero#baseDamage}),
     * up to the most; then, in Resurrección, the release's Cero multiplier, so a released one is the one that can
     * pass the cap, though never the beam's own clamp. Only the server knows of the release (see
     * {@link ArrancarLogic#released}).
     */
    public static float damage(SoulData data) {
        HollowTier origin = ArrancarLogic.origin(data);
        double grand = Math.min(BleachConfig.get(BleachConfig.GRAN_REY_CERO_MAX_DAMAGE),
                BleachConfig.get(BleachConfig.GRAN_REY_CERO_DAMAGE_MULTIPLIER) * HollowCero.baseDamage(data, origin));
        return (float) Math.min(MAX_DAMAGE, grand * ArrancarLogic.ceroMultiplier(data));
    }

    /** The reiryoku a Gran Rey Cero costs when it fires: a share of the maximum, but never less than the least cost. */
    public static float cost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.GRAN_REY_CERO_MIN_COST),
                BleachConfig.get(BleachConfig.GRAN_REY_CERO_COST_FRACTION) * data.maxReiryoku());
    }

    /** The health the blood costs when the gather starts. */
    public static float blood() {
        return (float) BleachConfig.get(BleachConfig.GRAN_REY_CERO_BLOOD_HEALTH);
    }

    /** The blood as hearts, for the soul screen: "2", or with one decimal ("2.5") when not whole. */
    public static String bloodHearts() {
        // Half the health, to one decimal place, so a value a hair off a whole heart still reads as whole.
        double hearts = Math.round(blood() * 5.0) / 10.0;
        return hearts == Math.floor(hearts) ? Long.toString((long) hearts) : String.format(Locale.ROOT, "%.1f", hearts);
    }

    /** The ticks it takes to gather fully: the Cero of the form the Arrancar rose from, times the setting. */
    public static int chargeTicks(SoulData data) {
        double ticks = HollowCero.chargeTicks(ArrancarLogic.origin(data))
                * BleachConfig.get(BleachConfig.GRAN_REY_CERO_CHARGE_MULTIPLIER);
        return (int) Math.max(1L, Math.round(ticks));
    }

    /** Its colour, as {@code 0xRRGGBB}: the Arrancar's own blade's, the same on every client. */
    public static int colour(Player player) {
        return ArrancarLogic.blade(player.getUUID()).colour();
    }

    /**
     * The beam as the engine takes it: its damage, reach and width, its blast with the Cero's share of the
     * damage, the blocks it may break and its knockback, the Arrancar's colour, its gather, and no aim lock, so it
     * goes where the crosshair is when it fires.
     */
    public static CeroBeam.Spec spec(Player player, SoulData data) {
        return new CeroBeam.Spec(damage(data), BleachConfig.get(BleachConfig.GRAN_REY_CERO_RANGE),
                (float) BleachConfig.get(BleachConfig.GRAN_REY_CERO_RADIUS),
                (float) BleachConfig.get(BleachConfig.GRAN_REY_CERO_BLAST_RADIUS),
                (float) BleachConfig.get(BleachConfig.CERO_BLAST_SHARE),
                BleachConfig.get(BleachConfig.GRAN_REY_CERO_MAX_BLOCKS),
                (float) BleachConfig.get(BleachConfig.GRAN_REY_CERO_KNOCKBACK), colour(player), chargeTicks(data), 0,
                BleachConfig.get(BleachConfig.GRAN_REY_CERO_BREAKS_BLOCKS));
    }

    /** Whole seconds, rounded up, until the next Gran Rey Cero may begin. */
    public static long secondsToReady(SoulData data, long now) {
        return Math.max(1L, (long) Math.ceil((data.hollow().arrancar().granReyCeroReadyAt() - now) / 20.0));
    }

    // ---- The key ----

    /**
     * Why this Arrancar may not begin a Gran Rey Cero now, the first that fails winning, or null if it may. It
     * changes nothing. A soul that is not an Arrancar at all is {@link Refusal#LOCKED}.
     */
    @Nullable
    public static Refusal refusal(Player player, SoulData data, long now) {
        if (Negacion.held(player)) {
            return Refusal.HELD;
        }
        if (!eligible(data)) {
            return Refusal.LOCKED;
        }
        if (now < data.hollow().arrancar().granReyCeroReadyAt()) {
            return Refusal.COOLDOWN;
        }
        if (!ArrancarLogic.released(data) && !ArrancarLogic.isOwnBlade(player.getMainHandItem(), player)) {
            return Refusal.NO_BLADE;
        }
        if (data.reiryoku() - cost(data) < ZanpakutoLogic.RESERVE) {
            return Refusal.TOO_WEAK;
        }
        if (!player.hasInfiniteMaterials() && player.getHealth() - blood()
                < BleachConfig.get(BleachConfig.GRAN_REY_CERO_MIN_HEALTH_LEFT)) {
            return Refusal.TOO_HURT;
        }
        return null;
    }

    /**
     * Starts gathering a Gran Rey Cero ahead of the eyes, the first refusal winning, and spills the blood for it
     * at the hand. A second press while one gathers does nothing, and so does a press by a soul that is not an
     * Arrancar or while a Cero gathers, untold; nothing is told either when the world will not have the beam (a
     * dead player, or no server level), and then nothing is paid.
     *
     * @return the refusal, or null when it began (or nothing was to be told)
     */
    @Nullable
    public static Refusal start(Player player, SoulData data, long now) {
        if (charging(data)) {
            // Settles one that has fired or gone since the last tick, so its cost and its wait come first.
            tick(player, data, now);
            if (charging(data)) {
                return null;
            }
        }
        if (!ArrancarLogic.isArrancar(data) || HollowCero.charging(data)) {
            return null;
        }
        Refusal refusal = refusal(player, data, now);
        if (refusal != null) {
            return refusal;
        }
        if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        CeroBeam beam = CeroBeam.charge(level, player, spec(player, data), KIND);
        if (beam == null) {
            return null;
        }
        data.granReyCeroBeamId = beam.getId();
        data.granReyCeroStartedAt = now;
        beam.whenFired(GranReyCero::fired);
        // Health, not a blow: no armour, totem, Hierro or hurt flash, and the refusal above keeps it from killing.
        if (!player.hasInfiniteMaterials()) {
            player.setHealth(player.getHealth() - blood());
        }
        Vec3 hand = hand(player);
        SpiritSenses.play(level, hand.x, hand.y, hand.z, ModSounds.GRAN_REY_CERO_CUT.get(), SoundSource.PLAYERS,
                CUT_VOLUME, 1.0F, false);
        SpiritSenses.particles(level, BLOOD, hand.x, hand.y, hand.z, CUT_BLOOD, CUT_BLOOD_SPREAD, CUT_BLOOD_SPREAD,
                CUT_BLOOD_SPREAD, 0.0);
        return null;
    }

    /**
     * The Cero key came up. A Gran Rey Cero fully gathered fires now and is paid for; one let go sooner scatters,
     * with no reiryoku spent and no wait, the blood already spilt, and the player is told. One that already fired
     * by itself is only paid for.
     */
    public static void release(Player player, SoulData data, long now) {
        if (!charging(data)) {
            return;
        }
        CeroBeam beam = beam(player, data);
        if (beam == null || beam.isFired()) {
            // Gone, it is forgotten; fired, it is settled.
            tick(player, data, now);
            return;
        }
        if (!player.isAlive()) {
            cancel(player, data);
            return;
        }
        // A half-gathered one would only be a Cero, so there is no weaker shot: full or nothing.
        if (now - data.granReyCeroStartedAt < beam.chargeTicks()) {
            beam.cancel();
            clear(data);
            player.displayClientMessage(Component.translatable(FIZZLED).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // Paid as it fires (see fired); one whose caster could no longer fire it fizzled instead, for nothing.
        beam.fireNow();
        clear(data);
    }

    /**
     * Settles one that has just fired, by itself or let go, in the tick it fires: the reiryoku is paid and the wait
     * begins before a logout, a death or a change of world can come between the shot and its price.
     */
    private static void fired(CeroBeam beam) {
        if (beam.owner() instanceof Player player) {
            SoulData data = SoulLogic.get(player);
            if (data.granReyCeroBeamId == beam.getId()) {
                paid(player, data, beam, beam.level().getGameTime());
                clear(data);
            }
        }
    }

    /**
     * Lets a gathering Gran Rey Cero fizzle, with no reiryoku spent (the blood stays spilt): a screen opened, or
     * the soul stopped being an Arrancar. One that already fired is paid for instead.
     */
    public static void cancel(Player player, SoulData data) {
        if (!charging(data)) {
            return;
        }
        CeroBeam beam = beam(player, data);
        if (beam != null && beam.isFired()) {
            paid(player, data, beam, player.level().getGameTime());
        } else if (beam != null) {
            beam.cancel();
        }
        clear(data);
    }

    /**
     * Each body tick of an Arrancar, from {@link ArrancarEvents#tick}: one that fired is paid for if its own fire
     * did not settle it already; one that has gone without firing is forgotten; and one still gathering fizzles if the soul
     * is no longer an Arrancar, watches as a spectator or is held in a Negación light, or else turns its ring and
     * drips its blood while the game runs.
     */
    public static void tick(Player player, SoulData data, long now) {
        if (!charging(data)) {
            return;
        }
        CeroBeam beam = beam(player, data);
        if (beam == null) {
            clear(data);
            return;
        }
        if (beam.isFired()) {
            paid(player, data, beam, now);
            clear(data);
            return;
        }
        if (!ArrancarLogic.isArrancar(data) || player.isSpectator() || Negacion.held(player)) {
            beam.cancel();
            clear(data);
            return;
        }
        gathering(player, data, beam, now);
    }

    /** Whether a Gran Rey Cero is gathering, as far as the soul knows. */
    public static boolean charging(SoulData data) {
        return data.granReyCeroBeamId >= 0;
    }

    /**
     * A refusal as told above the hotbar, in grey: the maximum reiryoku needed for one that is locked, and the
     * seconds left for one on its wait.
     */
    public static Component message(Refusal refusal, SoulData data, long now) {
        MutableComponent message = switch (refusal) {
            case LOCKED -> Component.translatable(refusal.messageKey(), Math.round(requiredMaxReiryoku()));
            case COOLDOWN -> Component.translatable(refusal.messageKey(), secondsToReady(data, now));
            default -> Component.translatable(refusal.messageKey());
        };
        return message.withStyle(ChatFormatting.GRAY);
    }

    // ---- Helpers ----

    /** The soul's gathering Gran Rey Cero, or null when it has gone (or is not this player's). */
    @Nullable
    private static CeroBeam beam(Player player, SoulData data) {
        return player.level().getEntity(data.granReyCeroBeamId) instanceof CeroBeam beam && beam.owner() == player
                ? beam : null;
    }

    /**
     * A Gran Rey Cero fired: its cost, never below the reserve, and the wait from now, kept in the saved record;
     * then the warped space and the two strands along the shot, its roar at the muzzle over the Cero's own, and
     * the milestone.
     */
    private static void paid(Player player, SoulData data, CeroBeam beam, long now) {
        data.setReiryoku(Math.max(ZanpakutoLogic.RESERVE, data.reiryoku() - cost(data)));
        HollowProgress hollow = data.hollow();
        long cooldown = BleachConfig.get(BleachConfig.GRAN_REY_CERO_COOLDOWN_SECONDS) * 20L;
        data.setHollow(hollow.withArrancar(hollow.arrancar().withGranReyCeroReadyAt(now + cooldown)));
        if (player.level() instanceof ServerLevel level) {
            CeroBeam.Shot shot = beam.lastShot();
            Vec3 muzzle = shot != null ? shot.from() : beam.position();
            if (shot != null) {
                fired(level, shot, beam.radius(), beam.colour());
            }
            SpiritSenses.play(level, muzzle.x, muzzle.y, muzzle.z, ModSounds.GRAN_REY_CERO_FIRE.get(), SoundSource.PLAYERS,
                    FIRE_VOLUME, 1.0F, false);
        }
        if (player instanceof ServerPlayer server) {
            Milestones.grant(server, Milestones.GRAN_REY_CERO);
        }
    }

    /**
     * While it gathers: a ring of the Arrancar's colour turning about the orb, across the way it looks, and a drop
     * of blood from the hand every few ticks. Timed by the game's clock, so it waits under a frozen tick rate.
     */
    private static void gathering(Player player, SoulData data, CeroBeam beam, long now) {
        if (!(player.level() instanceof ServerLevel level) || !level.tickRateManager().runsNormally()) {
            return;
        }
        long age = now - data.granReyCeroStartedAt;
        Across across = Across.of(player.getViewVector(1.0F));
        double radius = RING_SHARE * beam.radius();
        DustParticleOptions dust = dust(beam.colour(), RING_DUST_SIZE);
        Vec3 orb = beam.position();
        for (int i = 0; i < RING_POINTS; i++) {
            Vec3 at = across.at(orb, age * RING_TURN + 2.0 * Math.PI * i / RING_POINTS, radius);
            SpiritSenses.particles(level, dust, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (Math.floorMod(age, DRIP_TICKS) == 0) {
            Vec3 hand = hand(player);
            SpiritSenses.particles(level, BLOOD, hand.x, hand.y, hand.z, 1, DRIP_SPREAD, 0.0, DRIP_SPREAD, 0.0);
        }
    }

    /**
     * Along a fired shot, placeholders for the space it warps: motes of a reversed portal every few blocks about
     * the beam, and two strands of the Arrancar's colour wound about its edge.
     */
    private static void fired(ServerLevel level, CeroBeam.Shot shot, float radius, int colour) {
        Vec3 from = shot.from();
        Vec3 path = shot.to().subtract(from);
        double length = path.length();
        if (length < SHORTEST_SHOT) {
            return;
        }
        Vec3 heading = path.scale(1.0 / length);
        double spread = WARP_SHARE * radius;
        for (double along = 0.0; along <= length; along += WARP_STEP) {
            Vec3 at = from.add(heading.scale(along));
            SpiritSenses.particles(level, ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 1, spread, spread, spread,
                    WARP_SPEED);
        }
        Across across = Across.of(heading);
        DustParticleOptions dust = dust(colour, HELIX_DUST_SIZE);
        int points = (int) Math.min(HELIX_MAX_POINTS, Math.floor(length / HELIX_STEP) + 1);
        for (int i = 0; i < points; i++) {
            double along = i * HELIX_STEP;
            Vec3 centre = from.add(heading.scale(along));
            double turn = 2.0 * Math.PI * along / HELIX_TURN_LENGTH;
            for (int strand = 0; strand < 2; strand++) {
                Vec3 at = across.at(centre, turn + Math.PI * strand, radius);
                SpiritSenses.particles(level, dust, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Where the main hand is, as near as the blood needs: a little ahead of the chest, to the main-hand side. The
     * side is the facing turned a quarter to the right, and the other way for a left-handed player.
     */
    private static Vec3 hand(Player player) {
        Vec3 side = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
        if (player.getMainArm() == HumanoidArm.LEFT) {
            side = side.reverse();
        }
        return player.getEyePosition().add(player.getViewVector(1.0F).scale(HAND_AHEAD)).add(side.scale(HAND_SIDE))
                .subtract(0.0, HAND_DROP, 0.0);
    }

    private static DustParticleOptions dust(int colour, float size) {
        return new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F), size);
    }

    private static void clear(SoulData data) {
        data.granReyCeroBeamId = -1;
        data.granReyCeroStartedAt = HollowProgress.NEVER;
    }

    /** Two ways at right angles to an axis and to each other, for drawing circles about it. */
    private record Across(Vec3 first, Vec3 second) {
        /** Below this squared length the axis runs too nearly straight up or down to cross with the vertical. */
        private static final double UPRIGHT_EPSILON = 1.0E-6;

        static Across of(Vec3 axis) {
            Vec3 first = axis.cross(new Vec3(0.0, 1.0, 0.0));
            if (first.lengthSqr() < UPRIGHT_EPSILON) {
                first = axis.cross(new Vec3(1.0, 0.0, 0.0));
            }
            first = first.normalize();
            return new Across(first, axis.cross(first).normalize());
        }

        /** The point this far from the centre, at this angle about the axis. */
        Vec3 at(Vec3 centre, double angle, double radius) {
            return centre.add(first.scale(Math.cos(angle) * radius)).add(second.scale(Math.sin(angle) * radius));
        }
    }
}
