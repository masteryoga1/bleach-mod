package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.item.HellButterflyItem;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Senkaimon's rules: who may open one, what it costs, where it leads and whom it takes. They read the soul, the
 * player and the config, and the last rule of {@link #mayOpenSenkaimon} also looks for open gates in the opener's own
 * level, so game tests can call them directly with mock players. {@link TravelEvents} acts on them.
 */
public final class TravelLogic {
    /** Where every gate from the Human World comes out: the middle of the court's block, on Seireitei's lawn. */
    public static final Vec3 COURT = new Vec3(SeireiteiPlan.SENKAIMON_COURT_X + 0.5, SeireiteiPlan.PLATEAU_Y,
            SeireiteiPlan.SENKAIMON_COURT_Z + 0.5);
    private static final long TICKS_PER_SECOND = 20L;

    private TravelLogic() {
    }

    /** Why a soul may not open a Senkaimon, or why its turn ended, with the action-bar line it is told (null: none). */
    public enum Refusal {
        /** Dead or a spectator. */
        UNABLE(null),
        /** Held by a Negación light or a Caja, whose holder says so ({@code Negacion.refused}). */
        HELD(null),
        NO_POWER("message.bleach.senkaimon.no_power"),
        TAKEN("message.bleach.visored.takeover.sealed"),
        BOUND("message.bleach.bakudo.bound"),
        LENT("message.bleach.getsuga.lent"),
        WRONG_WORLD("message.bleach.senkaimon.wrong_world"),
        NO_BLADE("message.bleach.getsuga.no_blade"),
        /** Told with the seconds left. */
        COOLDOWN("message.bleach.senkaimon.cooldown"),
        TOO_WEAK("message.bleach.senkaimon.too_weak"),
        /** The new gate would stand within a soul's reach of an open one. */
        CROWDED("message.bleach.senkaimon.crowded"),
        /** The turn ended before the gate opened. */
        SLIPPED("message.bleach.senkaimon.slipped");

        @Nullable
        private final String messageKey;

        Refusal(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        @Nullable
        public String messageKey() {
            return messageKey;
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
