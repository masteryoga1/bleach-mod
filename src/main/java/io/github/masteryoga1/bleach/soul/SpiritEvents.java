package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Spiritual awareness on the server: the unaware cannot touch spirits, and every client is told
 * which of the players around it are spirits so it can hide them (see the client's SpiritVisibility).
 */
public final class SpiritEvents {
    private SpiritEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(SpiritEvents::onAttack);
        // Before anything adjusts the damage of a hit that should not land at all.
        bus.addListener(EventPriority.HIGH, SpiritEvents::onIncomingDamage);
        bus.addListener(SpiritEvents::onStartTracking);
        bus.addListener(SpiritEvents::onPlayerTick);
    }

    /** Whether this player's hands and weapons pass straight through the target. */
    public static boolean passesThrough(Player attacker, LivingEntity target) {
        return !attacker.isCreative() && SoulLogic.hasSoul(attacker) && !SoulLogic.isAware(SoulLogic.get(attacker))
                && SoulLogic.isSpirit(target);
    }

    private static void onAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof LivingEntity target && passesThrough(event.getEntity(), target)) {
            event.setCanceled(true);
        }
    }

    /** Covers what the attack event misses: sweeps, arrows and anything else a player causes. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker && attacker != event.getEntity()
                && passesThrough(attacker, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target
                && SoulLogic.hasSoul(target)) {
            BleachNetwork.sendAppearance(viewer, target, SoulLogic.get(target));
        }
    }

    /** Tells trackers when this player's appearance changes: state, chain length or released reiatsu. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        SoulAppearance appearance = SoulAppearance.of(data);
        if (!appearance.equals(data.lastSentAppearance)) {
            data.lastSentAppearance = appearance;
            BleachNetwork.broadcastAppearance(player, data);
        }
    }
}
