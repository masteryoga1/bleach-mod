package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Spiritual awareness on the server: the unaware cannot touch spirits, with their hands, weapons,
 * arrows or potions; mundane creatures such as iron golems, wolves and skeletons can neither see
 * nor hurt spirit mobs (Hollows, Pluses, Shinigami NPCs); and every client is told which of the
 * players around it are spirits so it can hide them (see the client's ClientSouls).
 */
public final class SpiritEvents {
    private SpiritEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(SpiritEvents::onAttack);
        // Before anything adjusts the damage of a hit that should not land at all.
        bus.addListener(EventPriority.HIGH, SpiritEvents::onIncomingDamage);
        bus.addListener(SpiritEvents::onChangeTarget);
        bus.addListener(SpiritEvents::onEffectApplicable);
        bus.addListener(SpiritEvents::onPlayerTickPre);
        bus.addListener(SpiritEvents::onStartTracking);
        bus.addListener(SpiritEvents::onPlayerTick);
    }

    /** Whether this player's hands and weapons pass straight through the target. */
    public static boolean passesThrough(Player attacker, LivingEntity target) {
        return !attacker.isCreative() && SoulLogic.hasSoul(attacker) && !SoulLogic.isAware(SoulLogic.get(attacker))
                && SoulLogic.isSpirit(target);
    }

    /**
     * Whether this creature belongs to the mundane world and cannot sense the target: neither is a
     * player, the creature is no spirit, and the target is a spirit mob. Players in a spirit state
     * still have bodies the mundane world can reach.
     */
    public static boolean blindToSpirit(LivingEntity creature, @Nullable LivingEntity target) {
        return target != null && !(creature instanceof Player) && !(target instanceof Player)
                && !SoulLogic.isSpirit(creature) && SoulLogic.isSpirit(target);
    }

    private static void onAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof LivingEntity target && passesThrough(event.getEntity(), target)) {
            event.setCanceled(true);
        }
    }

    /**
     * Covers what the attack event misses: sweeps, arrows and anything else a player causes. Blows,
     * arrows and blasts from mundane creatures pass through spirit mobs too.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity cause = event.getSource().getEntity();
        if (cause instanceof LivingEntity creature && blindToSpirit(creature, target)) {
            event.setCanceled(true);
            return;
        }
        if (cause instanceof Player attacker && attacker != target
                && passesThrough(attacker, target)) {
            event.setCanceled(true);
            // A sweep still runs Fire Aspect after the hit fails; remember the fire to undo it next tick.
            // Keep the lowest, in case an earlier hit this tick already set the spirit alight.
            if (target instanceof Player && SoulLogic.hasSoul(target)) {
                SoulData data = SoulLogic.get(target);
                data.fireBeforeIgnoredHit = Math.min(data.fireBeforeIgnoredHit, target.getRemainingFireTicks());
            }
        }
    }

    /**
     * Whether a potion thrown or left behind by this owner passes straight through the target. Instant
     * Health and Harming are kept off spirits by mixins on splash potions and lingering clouds.
     */
    public static boolean potionPassesThrough(@Nullable Entity owner, LivingEntity target) {
        return owner instanceof Player player && player != target && passesThrough(player, target);
    }

    /** Splash and lingering potions from the unaware pass through spirits too. */
    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        Entity source = event.getEffectSource();
        if (source instanceof Projectile projectile && projectile.getOwner() != null) {
            source = projectile.getOwner();
        } else if (source instanceof AreaEffectCloud cloud && cloud.getOwner() != null) {
            source = cloud.getOwner();
        }
        if (potionPassesThrough(source, event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** A mundane creature never takes a spirit mob as its target: an iron golem walks straight past a Hollow. */
    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (blindToSpirit(event.getEntity(), event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && SoulLogic.hasSoul(player)) {
            SoulData data = SoulLogic.get(player);
            if (data.fireBeforeIgnoredHit != SoulData.NO_FIRE_RECORDED) {
                if (player.getRemainingFireTicks() > data.fireBeforeIgnoredHit) {
                    player.setRemainingFireTicks(data.fireBeforeIgnoredHit);
                }
                data.fireBeforeIgnoredHit = SoulData.NO_FIRE_RECORDED;
            }
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target
                && SoulLogic.hasSoul(target)) {
            BleachNetwork.sendAppearance(viewer, target, SoulAppearance.of(target, SoulLogic.get(target)));
        }
    }

    /** Tells trackers when this player's appearance changes: state, chain length, released reiatsu or sheathed blade. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        SoulAppearance appearance = SoulAppearance.of(player, data);
        if (!appearance.equals(data.lastSentAppearance)) {
            data.lastSentAppearance = appearance;
            BleachNetwork.broadcastAppearance(player, appearance);
        }
    }
}
