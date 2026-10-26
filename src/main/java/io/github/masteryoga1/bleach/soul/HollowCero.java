package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A Hollow player's Cero, from Gillian up, and an Arrancar's, which gathers and hits as the form it rose
 * from did ({@link ArrancarLogic#ceroForm}), and harder in Resurrección. Held on the Cero key, it gathers
 * at the mouth, and it fires when the key comes up, or by itself once fully gathered. It runs on the Menos
 * mobs' engine ({@link CeroBeam}) with no aim lock, so the beam goes where the crosshair is at the moment
 * it fires. Let go at least half gathered ({@link BleachConfig#CERO_MIN_CHARGE}), it fires at the share
 * gathered; sooner, it fizzles. The reiryoku is paid only when it fires, as a Getsuga Tenshō's is, never
 * leaving less than the {@link ZanpakutoLogic#RESERVE}, and the cooldown starts then. A fizzle is free.
 *
 * <p>The gather breaks, for free, on a fresh crack in the mask (a blow to a mask that was already cracked
 * does not count, as for the Menos mobs), a devour hold starting, the soul ceasing to be a Menos or an
 * Arrancar, and spectator mode; the engine itself lets it fizzle if the player dies, logs out or changes
 * dimension. Whom it strikes and what it breaks follow every soul's projectile rules
 * ({@link CeroBeam#mayStrike}).
 *
 * <p>The beam and the times live in the soul as transients, so nothing about a Cero is saved.
 */
public final class HollowCero {
    /** Why a Cero would not begin. Each is told on the action bar ({@link #messageKey}). */
    public enum Refusal {
        /** A plain Hollow: only a Menos Grande gathers a Cero. */
        NOT_MENOS,
        /** A devour hold is on: the jaws are full. */
        DEVOURING,
        /** The last Cero fired too short a while ago. */
        COOLDOWN,
        /** Paying for it would leave less than the reserve. */
        TOO_WEAK;

        /** {@code message.bleach.cero.<name>}. */
        public String messageKey() {
            return "message.bleach.cero." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The blast where the beam bursts, the most blocks it breaks and its knockback, per block of half-width. */
    public static final double BLAST_PER_RADIUS = 1.8, BLOCKS_PER_RADIUS = 10.0, KNOCKBACK_PER_RADIUS = 0.8;
    /** The message of a Cero let go too soon. */
    public static final String FIZZLED = "message.bleach.cero.fizzled";

    private HollowCero() {
    }

    /**
     * Starts gathering a Cero at the mouth, the first check that fails winning. A second press while one
     * gathers does nothing, and nothing is told when the world will not have the Cero (a dead player, or
     * no server level).
     *
     * @return the refusal, or null when it began (or was already gathering)
     */
    @Nullable
    public static Refusal start(Player player, SoulData data, long now) {
        if (charging(data)) {
            // Settles a Cero that has fired or gone since the last tick, so its cost and cooldown come first.
            tick(player, data, now);
            if (charging(data)) {
                return null;
            }
        }
        HollowTier tier = ArrancarLogic.ceroForm(data);
        if (!tier.isMenos()) {
            return Refusal.NOT_MENOS;
        }
        if (data.devourTargetId >= 0) {
            return Refusal.DEVOURING;
        }
        if (now - data.lastCeroAt < BleachConfig.get(BleachConfig.CERO_COOLDOWN_SECONDS) * 20L) {
            return Refusal.COOLDOWN;
        }
        if (data.reiryoku() - cost(data) < ZanpakutoLogic.RESERVE) {
            return Refusal.TOO_WEAK;
        }
        if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        CeroBeam beam = CeroBeam.charge(level, player, spec(data, tier));
        if (beam != null) {
            data.ceroBeamId = beam.getId();
            data.ceroStartedAt = now;
        }
        return null;
    }

    /**
     * The Cero key came up. Gathered at least {@link BleachConfig#CERO_MIN_CHARGE} of the way, the Cero
     * fires now at that share of its strength, and is paid for; less, it fizzles at no cost, and the
     * player is told. One that already fired by itself is only paid for.
     */
    public static void release(Player player, SoulData data, long now) {
        if (!charging(data)) {
            return;
        }
        CeroBeam beam = beam(player, data);
        if (beam == null || beam.isFired()) {
            // Gone, it is forgotten; fired by itself, it is paid for.
            tick(player, data, now);
            return;
        }
        if (!player.isAlive()) {
            cancel(player, data);
            return;
        }
        float share = (now - data.ceroStartedAt) / (float) Math.max(1, beam.chargeTicks());
        if (share < BleachConfig.get(BleachConfig.CERO_MIN_CHARGE)) {
            beam.cancel();
            clear(data);
            if (player instanceof ServerPlayer server) {
                server.displayClientMessage(Component.translatable(FIZZLED).withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        beam.fireNow(Math.min(1.0F, share));
        // Paid only if it really fired: one whose caster could no longer fire it fizzled instead.
        if (beam.isFired()) {
            pay(data, now);
        }
        clear(data);
    }

    /**
     * Lets a gathering Cero fizzle, for free: a screen opened, a devour hold began, or the soul stopped
     * being a Hollow or an Arrancar. One that already fired is paid for instead.
     */
    public static void cancel(Player player, SoulData data) {
        if (!charging(data)) {
            return;
        }
        CeroBeam beam = beam(player, data);
        if (beam != null && beam.isFired()) {
            pay(data, player.level().getGameTime());
        } else if (beam != null) {
            beam.cancel();
        }
        clear(data);
    }

    /**
     * Each body tick of a Hollow or an Arrancar: a Cero that fired by itself is paid for and the cooldown
     * starts; one that has gone without firing is forgotten; and one still gathering fizzles if the mask
     * cracked fresh since it began, a devour hold is on, the soul is no longer a Menos (an Arrancar always
     * counts as the form it rose from), or the player watches as a spectator.
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
            pay(data, now);
            clear(data);
            return;
        }
        // Stamped only by a fresh crack, and strictly after the start, so a crack in the start's own tick came first.
        if (!ArrancarLogic.ceroForm(data).isMenos() || data.devourTargetId >= 0
                || data.hollowFreshCrackAt > data.ceroStartedAt || player.isSpectator()) {
            beam.cancel();
            clear(data);
        }
    }

    /** Whether a Cero is gathering, as far as the soul knows. */
    public static boolean charging(SoulData data) {
        return data.ceroBeamId >= 0;
    }

    /**
     * The Cero this form gathers at full strength: the form's damage, width and gather time, and a blast,
     * blocks and knockback that grow with the width, in a Menos's red. It never locks its aim early.
     */
    public static CeroBeam.Spec spec(SoulData data, HollowTier tier) {
        float radius = (float) BleachConfig.get(form(tier).ceroRadius());
        return new CeroBeam.Spec(damage(data, tier), BleachConfig.get(BleachConfig.CERO_RANGE), radius,
                (float) (radius * BLAST_PER_RADIUS), (float) BleachConfig.get(BleachConfig.CERO_BLAST_SHARE),
                (int) Math.round(radius * BLOCKS_PER_RADIUS), (float) (radius * KNOCKBACK_PER_RADIUS), CeroBeam.MENOS_RED,
                chargeTicks(tier), 0, BleachConfig.get(BleachConfig.CERO_BREAKS_BLOCKS));
    }

    /**
     * A fully gathered Cero's damage: the form's own, and more for each square root of maximum reiryoku,
     * up to {@link BleachConfig#CERO_MAX_DAMAGE}; then, for an Arrancar in Resurrección, the release's
     * multiplier, so a released Cero is the one that can pass the cap. The Hollow mobs' damage multiplier
     * is a mob setting and does not reach it.
     */
    public static float damage(SoulData data, HollowTier tier) {
        double perRoot = BleachConfig.get(BleachConfig.CERO_DAMAGE_PER_ROOT_REIRYOKU);
        double damage = BleachConfig.get(form(tier).ceroDamage()) + perRoot * Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        double capped = Math.min(BleachConfig.get(BleachConfig.CERO_MAX_DAMAGE), damage);
        return (float) (capped * ArrancarLogic.ceroMultiplier(data));
    }

    /** The reiryoku a Cero costs when it fires: a share of the maximum, but never less than the least cost. */
    public static float cost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.CERO_MIN_COST),
                BleachConfig.get(BleachConfig.CERO_COST_FRACTION) * data.maxReiryoku());
    }

    /** The ticks this form's Cero takes to gather fully. */
    public static int chargeTicks(HollowTier tier) {
        return BleachConfig.get(form(tier).ceroChargeTicks());
    }

    /** The soul's gathering Cero, or null when it has gone (or is not this player's). */
    @Nullable
    private static CeroBeam beam(Player player, SoulData data) {
        return player.level().getEntity(data.ceroBeamId) instanceof CeroBeam beam && beam.owner() == player ? beam : null;
    }

    /** A plain Hollow gathers no Cero; should one ever be asked for, it is a Gillian's. */
    private static BleachConfig.MenosForm form(HollowTier tier) {
        BleachConfig.MenosForm form = tier.form();
        return form != null ? form : BleachConfig.GILLIAN_FORM;
    }

    /** A Cero fired: its cost, never below the reserve, and the cooldown from now. */
    private static void pay(SoulData data, long now) {
        data.setReiryoku(Math.max(ZanpakutoLogic.RESERVE, data.reiryoku() - cost(data)));
        data.lastCeroAt = now;
    }

    private static void clear(SoulData data) {
        data.ceroBeamId = -1;
        data.ceroStartedAt = HollowProgress.NEVER;
    }
}
