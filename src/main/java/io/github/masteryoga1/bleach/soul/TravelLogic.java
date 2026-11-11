package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.item.HellButterflyItem;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The gates' rules: who may open a Senkaimon or a Garganta, what each costs, where each leads and whom the Senkaimon
 * takes. They read the soul, the player and the config, and the last rule of each opening also looks for open gates in
 * the opener's own level, so game tests can call them directly with mock players. {@link TravelEvents} acts on them.
 */
public final class TravelLogic {
    /** Where every gate from the Human World comes out: the middle of the court's block, on Seireitei's lawn. */
    public static final Vec3 COURT = new Vec3(SeireiteiPlan.SENKAIMON_COURT_X + 0.5, SeireiteiPlan.PLATEAU_Y,
            SeireiteiPlan.SENKAIMON_COURT_Z + 0.5);
    private static final long TICKS_PER_SECOND = 20L;
    /** Room a Garganta leaves over the head of the tallest body it is torn for. */
    private static final double PASSAGE_HEADROOM = 1.2;

    private TravelLogic() {
    }

    /** The two gates the Gate key opens: which one depends on who turns it ({@link #gateFor}). */
    public enum Gate { SENKAIMON, GARGANTA }

    /**
     * Why a soul may not open a gate, or why its turn ended, with the action-bar line it is told for each gate (null:
     * none).
     */
    public enum Refusal {
        /** Dead or a spectator. */
        UNABLE(null, null),
        /** Held by a Negación light or a Caja, whose holder says so ({@code Negacion.refused}). */
        HELD(null, null),
        NO_POWER("message.bleach.senkaimon.no_power", "message.bleach.garganta.no_power"),
        TAKEN("message.bleach.visored.takeover.sealed", "message.bleach.visored.takeover.sealed"),
        BOUND("message.bleach.bakudo.bound", "message.bleach.bakudo.bound"),
        /** The Senkaimon's alone: no Hollow lends a Shinigami's power. */
        LENT("message.bleach.getsuga.lent", null),
        WRONG_WORLD("message.bleach.senkaimon.wrong_world", "message.bleach.garganta.wrong_world"),
        /** The Senkaimon's alone: a Garganta is torn with no blade. */
        NO_BLADE("message.bleach.getsuga.no_blade", null),
        /** Told with the seconds left. */
        COOLDOWN("message.bleach.senkaimon.cooldown", "message.bleach.garganta.cooldown"),
        TOO_WEAK("message.bleach.senkaimon.too_weak", "message.bleach.garganta.too_weak"),
        /** The new gate would stand within a soul's reach of an open one. */
        CROWDED("message.bleach.senkaimon.crowded", "message.bleach.garganta.crowded"),
        /** The turn ended before the gate opened. */
        SLIPPED("message.bleach.senkaimon.slipped", "message.bleach.garganta.slipped");

        @Nullable
        private final String messageKey;
        @Nullable
        private final String gargantaKey;

        Refusal(@Nullable String messageKey, @Nullable String gargantaKey) {
            this.messageKey = messageKey;
            this.gargantaKey = gargantaKey;
        }

        /** The Senkaimon's line. */
        @Nullable
        public String messageKey() {
            return messageKey;
        }

        /** The line for this refusal at this gate. */
        @Nullable
        public String messageKey(Gate gate) {
            return gate == Gate.GARGANTA ? gargantaKey : messageKey;
        }
    }

    /** Where a gate from Soul Society tries to land, in the order {@link #returnOrder} gives. */
    public enum Return { RECORD, RESPAWN, WORLD_SPAWN }

    /** The world a Senkaimon opened here leads to: Soul Society from the Human World and back; null anywhere else. */
    @Nullable
    public static ResourceKey<Level> leadsTo(ResourceKey<Level> from) {
        if (from == Level.OVERWORLD) {
            return BleachWorlds.SOUL_SOCIETY;
        }
        return from == BleachWorlds.SOUL_SOCIETY ? Level.OVERWORLD : null;
    }

    /** Whether this soul holds a Shinigami's power it can use: it could wield a zanpakutō, and no Quincy power blocks it. */
    public static boolean shinigami(SoulData data) {
        return ZanpakutoLogic.canWield(data) && !QuincyLogic.blocksShinigamiPower(data);
    }

    /** Whether a Senkaimon takes this soul: that power of its own, no Hollow holding its body, and a Jigokuchō carried. */
    public static boolean guided(Player player) {
        SoulData data = SoulLogic.hasSoul(player) ? SoulLogic.get(player) : null;
        return data != null && shinigami(data) && !VisoredLogic.takenOver(data) && HellButterflyItem.carries(player);
    }

    /** Whether this stack turns like a key for this soul: its own zanpakutō, or a nameless Asauchi. */
    public static boolean turnsKey(Player player, ItemStack stack) {
        return ZanpakutoLogic.isOwnBlade(stack, player) || ZanpakutoLogic.isUnboundAsauchi(stack);
    }

    /** The gate this soul's key opens: a Garganta for a Hollow of any form or an Arrancar, a Senkaimon for anyone else. */
    public static Gate gateFor(SoulData data) {
        return HollowPlayerLogic.isHollowOrArrancar(data) ? Gate.GARGANTA : Gate.SENKAIMON;
    }

    /** Whether this soul can tear a Garganta open: a Menos Grande of any form, or an Arrancar. */
    public static boolean opensGarganta(SoulData data) {
        return HollowTier.of(data).isMenos() || ArrancarLogic.isArrancar(data);
    }

    /**
     * The world a Garganta opened here leads to: Hueco Mundo from the Human World or Soul Society, and from Hueco Mundo
     * the Human World, or Soul Society when it is torn sneaking; null anywhere else.
     */
    @Nullable
    public static ResourceKey<Level> gargantaLeadsTo(ResourceKey<Level> from, boolean sneaking) {
        if (from == Level.OVERWORLD || from == BleachWorlds.SOUL_SOCIETY) {
            return BleachWorlds.HUECO_MUNDO;
        }
        if (from == BleachWorlds.HUECO_MUNDO) {
            return sneaking ? BleachWorlds.SOUL_SOCIETY : Level.OVERWORLD;
        }
        return null;
    }

    /**
     * Null if this soul may open a Senkaimon in this world now; otherwise the first rule it fails. Creative skips the
     * wait and the cost; the last rule looks at the gates already open in the opener's own level.
     */
    @Nullable
    public static Refusal mayOpenSenkaimon(Player player, SoulData data, ResourceKey<Level> world, long now) {
        if (!player.isAlive() || player.isSpectator()) {
            return Refusal.UNABLE;
        }
        if (Negacion.held(player)) {
            return Refusal.HELD;
        }
        if (!shinigami(data)) {
            return Refusal.NO_POWER;
        }
        if (VisoredLogic.takenOver(data)) {
            return Refusal.TAKEN;
        }
        if (BakudoEffect.bound(player)) {
            return Refusal.BOUND;
        }
        if (ShinigamiLogic.isPowerLent(data, now)) {
            return Refusal.LENT;
        }
        if (leadsTo(world) == null) {
            return Refusal.WRONG_WORLD;
        }
        if (!turnsKey(player, player.getMainHandItem())) {
            return Refusal.NO_BLADE;
        }
        if (!player.isCreative()) {
            if (now < data.senkaimonReadyAt) {
                return Refusal.COOLDOWN;
            }
            if (data.reiryoku() - openCost(data) < ZanpakutoLogic.RESERVE) {
                return Refusal.TOO_WEAK;
            }
        }
        // Last, so the entity search runs only when every other rule passes.
        return Senkaimon.crowded(player) ? Refusal.CROWDED : null;
    }

    /**
     * Null if this soul may open a Garganta in this world now; otherwise the first rule it fails. Creative skips the wait
     * and the cost; the last rule looks at the gates already open in the opener's own level.
     */
    @Nullable
    public static Refusal mayOpenGarganta(Player player, SoulData data, ResourceKey<Level> world, long now) {
        if (!player.isAlive() || player.isSpectator()) {
            return Refusal.UNABLE;
        }
        if (Negacion.held(player)) {
            return Refusal.HELD;
        }
        if (!opensGarganta(data)) {
            return Refusal.NO_POWER;
        }
        if (BakudoEffect.bound(player)) {
            return Refusal.BOUND;
        }
        if (gargantaLeadsTo(world, false) == null) {
            return Refusal.WRONG_WORLD;
        }
        if (!player.isCreative()) {
            if (now < data.gargantaReadyAt) {
                return Refusal.COOLDOWN;
            }
            if (data.reiryoku() - gargantaCost(data) < ZanpakutoLogic.RESERVE) {
                return Refusal.TOO_WEAK;
            }
        }
        // Last, so the entity search runs only when every other rule passes.
        return GargantaRift.crowded(player, passageSize(player, data)) ? Refusal.CROWDED : null;
    }

    /** The reiryoku a gate costs this soul: a share of its maximum, at least the least cost. */
    public static float openCost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.SENKAIMON_MIN_COST),
                BleachConfig.get(BleachConfig.SENKAIMON_COST_SHARE) * data.maxReiryoku());
    }

    /** Ticks the blade is turned before the gate opens. */
    public static long unlockTicks() {
        return Math.max(1L, Math.round(BleachConfig.get(BleachConfig.SENKAIMON_UNLOCK_SECONDS) * TICKS_PER_SECOND));
    }

    /** Starts the wait before this soul's next gate. */
    public static void startCooldown(SoulData data, long now) {
        data.senkaimonReadyAt = now + BleachConfig.get(BleachConfig.SENKAIMON_COOLDOWN_SECONDS) * TICKS_PER_SECOND;
    }

    /** Whole seconds left of that wait, rounded up. */
    public static long cooldownSeconds(SoulData data, long now) {
        return Math.max(0L, (data.senkaimonReadyAt - now + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    /** The reiryoku a Garganta costs this soul: a share of its maximum, at least the least cost. */
    public static float gargantaCost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.GARGANTA_MIN_COST),
                BleachConfig.get(BleachConfig.GARGANTA_COST_SHARE) * data.maxReiryoku());
    }

    /** Ticks the key is held before this gate opens. */
    public static long turnTicks(Gate gate) {
        return gate == Gate.GARGANTA
                ? Math.max(1L, Math.round(BleachConfig.get(BleachConfig.GARGANTA_HOLD_SECONDS) * TICKS_PER_SECOND))
                : unlockTicks();
    }

    /** Ticks a Garganta stays, from the moment it starts to open to the moment it is gone. */
    public static int gargantaOpenTicks() {
        return BleachConfig.get(BleachConfig.GARGANTA_OPEN_SECONDS) * (int) TICKS_PER_SECOND;
    }

    /** Starts the wait before this soul's next Garganta. */
    public static void startGargantaCooldown(SoulData data, long now) {
        data.gargantaReadyAt = now + BleachConfig.get(BleachConfig.GARGANTA_COOLDOWN_SECONDS) * TICKS_PER_SECOND;
    }

    /** Whole seconds left of that wait, rounded up. */
    public static long gargantaCooldownSeconds(SoulData data, long now) {
        return Math.max(0L, (data.gargantaReadyAt - now + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    /** The size of the Garganta this soul tears open, for its full standing height ({@link #passageSize(double)}). */
    public static float passageSize(Entity opener, SoulData data) {
        return passageSize(Math.max(opener.getBbHeight(), MenosBody.HEIGHT_PER_SCALE * MenosBody.targetScale(data)));
    }

    /**
     * The size of a Garganta torn for a body this tall: 1, a tear 2 wide and 4 tall, or as large as the body and 1.2
     * blocks over its head need, so a Gillian of 10.8 blocks tears one of size 3, 6 wide and 12 tall.
     */
    public static float passageSize(double standingHeight) {
        return (float) Math.max(1.0, (standingHeight + PASSAGE_HEADROOM) / GargantaRift.PASSAGE_HEIGHT);
    }

    /**
     * Whether the instructor's wait for another Jigokuchō is over on this game day. A day stamped later than today, as
     * when the clock is set back after nights slept through, does not hold one back.
     */
    public static boolean butterflyDue(TravelRecord record, long day) {
        return record.butterflyDay() < 0 || day < record.butterflyDay()
                || day - record.butterflyDay() >= BleachConfig.get(BleachConfig.BUTTERFLY_REISSUE_DAYS);
    }

    /**
     * Where a guided soul leaving Soul Society tries to come out, in order: where it last left the Human World by a
     * gate, its respawn point if that is in the Human World, and world spawn.
     */
    public static List<Return> returnOrder(TravelRecord record, @Nullable ResourceKey<Level> respawnWorld) {
        List<Return> order = new ArrayList<>(3);
        if (record.humanWorldReturn().filter(at -> at.dimension() == Level.OVERWORLD).isPresent()) {
            order.add(Return.RECORD);
        }
        if (respawnWorld == Level.OVERWORLD) {
            order.add(Return.RESPAWN);
        }
        order.add(Return.WORLD_SPAWN);
        return order;
    }
}
