package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Playing as a Hollow, on the events: the body's tick, the pause in healing after a hurt, the food,
 * armour and villagers a Hollow is refused, what a mindless Gillian cannot do, the claws put right
 * before a blow, a Hollow devoured by the Hollow that killed it, and the way back to being a soul, by
 * the Academy instructor's blade or a purifying blow. The rules are in {@link HollowPlayerLogic} and
 * what they do to the body in {@link HollowBody} and {@link MenosBody}. The refusals run on both sides,
 * so the client does not first show what the server then undoes.
 */
public final class HollowPlayerEvents {
    private HollowPlayerEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(HollowPlayerEvents::onPlayerTick);
        bus.addListener(HollowPlayerEvents::onDamageTaken);
        bus.addListener(HollowPlayerEvents::onUseItemStart);
        bus.addListener(HollowPlayerEvents::onRightClickItem);
        bus.addListener(HollowPlayerEvents::onRightClickBlock);
        bus.addListener(HollowPlayerEvents::onEntityInteract);
        bus.addListener(HollowPlayerEvents::onEntityInteractSpecific);
        bus.addListener(HollowPlayerEvents::onAttackEntity);
        // Low, after the soul's own death handlers have had their say, and never for a death that was cancelled.
        bus.addListener(EventPriority.LOW, HollowPlayerEvents::onDeath);
        bus.addListener(HollowPlayerEvents::onRespawn);
        MenosBody.register(bus);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && SoulLogic.hasSoul(player)) {
            HollowBody.tickBody(player, SoulLogic.get(player), player.level().getGameTime());
        }
    }

    /** Damage that landed holds back a Hollow's regeneration for a while. */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0.0F && !event.getEntity().level().isClientSide()
                && event.getEntity() instanceof Player player && HollowPlayerLogic.isHollowPlayer(player)) {
            HollowBody.onHurt(SoulLogic.get(player), player.level().getGameTime());
        }
    }

    /**
     * Food is ash to a Hollow: it cannot start to eat or drink it (see {@link HollowPlayerLogic#cannotConsume}).
     * A mindless Gillian cannot start using anything.
     */
    private static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && refusedMindless(player)) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && HollowPlayerLogic.isHollowPlayer(player)
                && HollowPlayerLogic.cannotConsume(event.getItem(), player)) {
            event.setCanceled(true);
            refuseFood(player);
        }
    }

    /**
     * Right-click wearing of armour a Hollow's hide refuses. The armour slots already refuse it (see
     * LivingEntityMixin); in the off hand, this would otherwise swap the two hands. A mindless Gillian
     * cannot use any item.
     */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (refusedMindless(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemStack stack = event.getItemStack();
        Equipable equipable = Equipable.get(stack);
        if (equipable != null && HollowPlayerLogic.refusesArmour(player, stack, equipable.getEquipmentSlot())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Whether a bite of cake, straight from the block, is refused, as food is to a Hollow under survival's
     * rules. Asked by CakeBlockMixin on both sides whenever a click reaches the bite. Only a Hollow that
     * vanilla would let bite (a starving one, whose food bar is held low) is told so: a fed one is full to
     * vanilla too, and placing a block or using an item against a cake says nothing.
     */
    public static boolean refusesCake(Player player) {
        if (!HollowPlayerLogic.survivalRules(player) || !HollowPlayerLogic.isHollowPlayer(player)) {
            return false;
        }
        if (player.canEat(false)) {
            refuseFood(player);
        }
        return true;
    }

    /**
     * A mindless Gillian cannot place a block or use what it holds on one. A bare hand still opens doors,
     * works levers and opens chests, as it can do nothing else anyway.
     */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().isEmpty() && refusedMindless(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Villagers and wandering traders shy away from a Hollow they cannot see (see
     * {@link HollowPlayerLogic#shunnedByVillagers}). A mindless Gillian deals with no creature but a
     * Shinigami, so the Academy's instructor can still purify it.
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof ShinigamiNpc) && refusedMindless(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (event.getTarget() instanceof AbstractVillager && HollowPlayerLogic.shunnedByVillagers(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!player.level().isClientSide()) {
                SoulData data = SoulLogic.get(player);
                HollowBody.actionBar(player, data, Component.translatable("message.bleach.hollow_player.villager")
                        .withStyle(ChatFormatting.GRAY), player.level().getGameTime());
            }
        }
    }

    /** A click on a particular part of a creature (an armour stand's slots, for one): as {@link #onEntityInteract}. */
    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof ShinigamiNpc) && refusedMindless(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Just before a blow, the claws are put right for what is in hand now, so a hotbar swap since the last
     * tick never carries the claws onto a sword. A hand just emptied gets them on the next tick instead
     * (see {@link HollowBody#reconcile}).
     */
    private static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (HollowPlayerLogic.isHollow(data) || data.hollowBodyApplied) {
            HollowBody.reconcile(player, data);
        }
    }

    /** A Hollow that kills a Hollow player devours it, as it would a Plus. */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Player victim)
                || !HollowPlayerLogic.isHollowPlayer(victim)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Hollow hollow && hollow.isAlive()) {
            hollow.devour(victim);
        }
    }

    /**
     * A Hollow that a purifying blow killed wakes as a soul, and is sent on to the Academy as Konsō
     * would send it, if the server does that. A respawn anchor it never stayed at gets its charge back.
     */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SoulLogic.hasSoul(player)
                || !HollowBody.consumeCleansed(SoulLogic.get(player))) {
            return;
        }
        player.sendSystemMessage(Component.translatable("message.bleach.hollow_player.cleansed")
                .withStyle(ChatFormatting.AQUA));
        if (BleachConfig.get(BleachConfig.KONSO_SENDS_TO_ACADEMY)) {
            ServerLevel respawnLevel = player.serverLevel();
            boolean usedAnchor = ChainEvents.respawnedAtAnchor(player);
            if (AcademyLocator.sendTo(player) && usedAnchor) {
                ChainEvents.refundAnchorCharge(respawnLevel, player.getRespawnPosition());
            }
        }
        Milestones.grant(player, Milestones.CLEANSED);
    }

    /**
     * The instructor's blade (NpcAction.PURIFY): the mask is broken and the Hollow is a soul again, where
     * it stands, at the Academy. Any devouring hold ends by itself on the next tick, the soul being no
     * longer a Hollow.
     *
     * @return false, changing nothing, unless the visitor is a Hollow and the server lets the instructor do it
     */
    public static boolean purifyByInstructor(ShinigamiNpc npc, Player visitor) {
        if (!SoulLogic.hasSoul(visitor) || !BleachConfig.get(BleachConfig.INSTRUCTOR_PURIFIES)) {
            return false;
        }
        SoulData data = SoulLogic.get(visitor);
        if (!HollowPlayerLogic.isHollow(data)) {
            return false;
        }
        HollowBody.leaveBody(visitor, data);
        HollowBody.purify(data);
        npc.swing(InteractionHand.MAIN_HAND);
        ShinigamiEvents.konsoEffects(visitor);
        visitor.sendSystemMessage(Component.translatable("message.bleach.hollow_player.purified_by_instructor")
                .withStyle(ChatFormatting.AQUA));
        if (visitor instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.CLEANSED);
            Milestones.check(player, data);
        }
        return true;
    }

    /**
     * Whether this player is a mindless Gillian (see {@link MenosBody#mindless}), told so on the server and
     * not too often. On both sides, so the client never shows what the server refuses.
     */
    private static boolean refusedMindless(Player player) {
        if (!MenosBody.mindless(player)) {
            return false;
        }
        if (!player.level().isClientSide()) {
            HollowBody.actionBar(player, SoulLogic.get(player), Component.translatable("message.bleach.menos.mindless")
                    .withStyle(ChatFormatting.GRAY), player.level().getGameTime());
        }
        return true;
    }

    /** Tells a Hollow, on the server and not too often, that food is no use to it. */
    private static void refuseFood(Player player) {
        if (!player.level().isClientSide()) {
            Component message = Component.translatable("message.bleach.hollow_player.cannot_eat")
                    .withStyle(ChatFormatting.GRAY);
            HollowBody.actionBar(player, SoulLogic.get(player), message, player.level().getGameTime());
        }
    }
}
