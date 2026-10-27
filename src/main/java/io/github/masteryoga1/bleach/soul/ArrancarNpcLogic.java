package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The rules of the Arrancar NPCs, pure: the ranks and souls they are given, whom they hunt and come for, how many
 * come with a Privaron Espada, when a beaten one is recalled, and their Cero and Bala. The numbers come from
 * {@code [arrancar.npc]} and its two kinds, read each time, so a changed config reaches them at once.
 *
 * <p>No NPC is ever seated: the Espada's seats belong to the server's roster of Arrancar players, and an NPC given
 * a seat holds the Privaron number of it instead ({@link #unseated}).
 */
public final class ArrancarNpcLogic {
    private ArrancarNpcLogic() {
    }

    // ---- Rank and soul ----

    /** The numbers of this kind: a Privaron Espada's own section, else a Número's (which also stands for none). */
    public static BleachConfig.ArrancarNpcKind numbers(ArrancarRank.Kind kind) {
        return kind == ArrancarRank.Kind.PRIVARON ? BleachConfig.ARRANCAR_NPC_PRIVARON : BleachConfig.ARRANCAR_NPC_NUMERO;
    }

    /** A Número of 11 to 99, at random. */
    public static ArrancarRank rollNumero(RandomSource random) {
        return new ArrancarRank(ArrancarRank.FIRST_NUMERO + random.nextInt(ArrancarRank.LAST_NUMERO
                - ArrancarRank.FIRST_NUMERO + 1), 0);
    }

    /** A Privaron Espada of 100 to 109, at random: the mark of whichever seat it lost. */
    public static ArrancarRank rollPrivaron(RandomSource random) {
        return new ArrancarRank(0, ArrancarRank.PRIVARON_BASE + random.nextInt(ArrancarRank.SEATS));
    }

    /**
     * An Espada seat s becomes the Privaron 100 + s % 10 (seat 4 is 104, seat 10 is 100), keeping any Número;
     * anything else is returned unchanged.
     */
    public static ArrancarRank unseated(ArrancarRank rank) {
        if (rank.kind() != ArrancarRank.Kind.ESPADA) {
            return rank;
        }
        return new ArrancarRank(rank.numero(), ArrancarRank.privaronNumber(rank.seat()));
    }

    /**
     * The form an Arrancar of this kind rose from, for a roll in [0, 1): below its {@code gillian_share} a Gillian,
     * below that and its {@code vasto_lorde_share} together a Vasto Lorde, else an Adjuchas.
     */
    public static HollowTier rollOrigin(ArrancarRank.Kind kind, double roll) {
        BleachConfig.ArrancarNpcKind numbers = numbers(kind);
        double gillian = BleachConfig.get(numbers.gillianShare());
        if (roll < gillian) {
            return HollowTier.GILLIAN;
        }
        if (roll < gillian + BleachConfig.get(numbers.vastoLordeShare())) {
            return HollowTier.VASTO_LORDE;
        }
        return HollowTier.ADJUCHAS;
    }

    /**
     * The maximum reiryoku an Arrancar of this kind is given, for a roll in [0, 1): evenly between its kind's
     * bounds (a highest below the lowest counts as the lowest), never as much as an Espada needs
     * ({@link ArrancarRank#espadaMinMaxReiryoku}), and at least 1.
     */
    public static float rollMaxReiryoku(ArrancarRank.Kind kind, double roll) {
        BleachConfig.ArrancarNpcKind numbers = numbers(kind);
        double low = BleachConfig.get(numbers.minMaxReiryoku());
        double high = Math.max(low, BleachConfig.get(numbers.maxMaxReiryoku()));
        double rolled = Mth.lerp(Mth.clamp(roll, 0.0, 1.0), low, high);
        // Below the Espada's line, so even a strong NPC's reiryoku says it could not have held a seat.
        double below = ArrancarRank.espadaMinMaxReiryoku() - 1.0;
        return (float) Math.max(1.0, Math.min(rolled, below));
    }

    // ---- Prey ----

    /**
     * Whether Arrancar hunt a player with this soul: an aware Human (or any, with {@code huntUnaware}), a Shinigami,
     * an Academy student; never a Plus or another Rukongai soul; a Hollow, or an Arrancar below the Espada, only with
     * {@code huntHollowPlayers}. A seated Espada never.
     */
    public static boolean huntsSoul(SoulData data, boolean huntHollowPlayers, boolean huntUnaware) {
        return switch (data.state()) {
            case HUMAN -> SoulLogic.isAware(data) || huntUnaware;
            case SHINIGAMI -> true;
            // No Arrancar in canon hunts the souls of the dead; they are a Hollow's meal, not an Arrancar's quarry.
            case PLUS -> false;
            case RUKONGAI -> ShinigamiLogic.isStudent(data);
            case HOLLOW -> huntHollowPlayers;
            // Lower ranks defer to an Espada, or fear one, whatever the server lets them hunt.
            case ARRANCAR -> huntHollowPlayers && ArrancarRank.of(data).kind() != ArrancarRank.Kind.ESPADA;
        };
    }

    /**
     * Whether Arrancar hunt this creature: only a living player in neither creative nor spectator mode, with a soul
     * they hunt ({@link #huntsSoul}, by {@code [hollows] hunt_hollow_players} and {@code hunt_unaware}).
     */
    public static boolean isPrey(LivingEntity target) {
        if (!(target instanceof Player player) || !player.isAlive() || player.isCreative() || player.isSpectator()
                || !SoulLogic.hasSoul(player)) {
            return false;
        }
        return huntsSoul(SoulLogic.get(player), BleachConfig.get(BleachConfig.HUNT_HOLLOW_PLAYERS),
                BleachConfig.get(BleachConfig.HUNT_UNAWARE));
    }

    /** Whether Arrancar may come through a Garganta for this soul: one they hunt, with at least {@code min_reiatsu}. */
    public static boolean mayComeFor(SoulData drawer) {
        return huntsSoul(drawer, BleachConfig.get(BleachConfig.HUNT_HOLLOW_PLAYERS),
                BleachConfig.get(BleachConfig.HUNT_UNAWARE))
                && drawer.maxReiryoku() >= BleachConfig.get(BleachConfig.ARRANCAR_NPC_MIN_REIATSU);
    }

    /**
     * Whether an arrival for a soul this strong is a Privaron Espada and its Fracción, for a roll in [0, 1): it needs
     * {@code group_min_reiatsu}, and the roll below {@code group_share}.
     */
    public static boolean bringsGroup(float drawerMaxReiryoku, double roll) {
        return drawerMaxReiryoku >= BleachConfig.get(BleachConfig.ARRANCAR_NPC_GROUP_MIN_REIATSU)
                && roll < BleachConfig.get(BleachConfig.ARRANCAR_NPC_GROUP_SHARE);
    }

    /** How many Números a Privaron Espada brings: {@code fraccion_min} to {@code fraccion_max}, at random. */
    public static int fraccionCount(RandomSource random) {
        int min = BleachConfig.get(BleachConfig.ARRANCAR_NPC_FRACCION_MIN);
        int max = Math.max(min, BleachConfig.get(BleachConfig.ARRANCAR_NPC_FRACCION_MAX));
        return min + random.nextInt(max - min + 1);
    }

    // ---- The recall ----

    /**
     * Whether a beaten Privaron Espada is recalled now, for its one roll in [0, 1): recalls are on, it has not
     * rolled before, its health is at or below {@code recall_health} of its maximum, and the roll is below
     * {@code recall_chance}.
     */
    public static boolean shouldRecall(float health, float maxHealth, boolean rolled, double roll) {
        return BleachConfig.get(BleachConfig.ARRANCAR_NPC_RECALL) && !rolled
                && health <= BleachConfig.get(BleachConfig.ARRANCAR_NPC_RECALL_HEALTH) * maxHealth
                && roll < BleachConfig.get(BleachConfig.ARRANCAR_NPC_RECALL_CHANCE);
    }

    // ---- Arts ----

    /**
     * An Arrancar NPC's Cero: the Cero of the form its soul rose from ({@link HollowCero#spec}), with its kind's damage
     * (times {@code damage_multiplier}) and gather, the NPCs' reach and aim lock, and blasting blocks only where both
     * {@code [arrancar.npc] cero_breaks_blocks} and {@code [menos] cero_breaks_blocks} allow. The width, the blast,
     * the blocks, the knockback and the red stay the form's.
     */
    public static CeroBeam.Spec ceroSpec(SoulData soul, ArrancarRank.Kind kind) {
        CeroBeam.Spec form = HollowCero.spec(soul, ArrancarLogic.origin(soul));
        BleachConfig.ArrancarNpcKind numbers = numbers(kind);
        float damage = (float) (BleachConfig.get(numbers.ceroDamage())
                * BleachConfig.get(BleachConfig.ARRANCAR_NPC_DAMAGE_MULTIPLIER));
        boolean breaksBlocks = BleachConfig.get(BleachConfig.ARRANCAR_NPC_CERO_BREAKS_BLOCKS)
                && BleachConfig.get(BleachConfig.CERO_BREAKS_BLOCKS);
        return new CeroBeam.Spec(damage, BleachConfig.get(BleachConfig.ARRANCAR_NPC_CERO_RANGE), form.radius(),
                form.blastRadius(), form.blastShare(), form.maxBlocks(), form.knockback(), form.colour(),
                BleachConfig.get(numbers.ceroChargeTicks()), BleachConfig.get(BleachConfig.ARRANCAR_NPC_CERO_AIM_LOCK_TICKS),
                breaksBlocks);
    }

    /** An Arrancar NPC's Bala: a player's of the same soul ({@link Bala#spec}), its damage times damage_multiplier. */
    public static CeroBeam.Spec balaSpec(SoulData soul) {
        CeroBeam.Spec bala = Bala.spec(soul);
        float damage = (float) (bala.damage() * BleachConfig.get(BleachConfig.ARRANCAR_NPC_DAMAGE_MULTIPLIER));
        return new CeroBeam.Spec(damage, bala.range(), bala.radius(), bala.blastRadius(), bala.blastShare(),
                bala.maxBlocks(), bala.knockback(), bala.colour(), bala.chargeTicks(), bala.aimLockTicks(),
                bala.breaksBlocks());
    }
}
