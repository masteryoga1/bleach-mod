package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Asauchi on the server: the bond that grows while a soul carries it, the hidden leaning that
 * how the soul fights builds meanwhile, learning the name, and keeping the blades in line with the
 * soul (bound, rebound or sealed as the bond says) and through death. The rules are in
 * {@link ZanpakutoLogic}; everything here that a game test calls takes a {@link Player}.
 */
public final class ZanpakutoEvents {
    private static final int RECONCILE_TICKS = 20;
    private static final int CARRY_TICKS = 1200;
    private static final int LEAN_TICKS = 200;
    private static final int HAKUDA_LEAN_COOLDOWN_TICKS = 10;
    private static final int PURIFICATION_LEAN = 2;
    private static final long TICKS_PER_MINUTE = 1200L;

    private ZanpakutoEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ZanpakutoEvents::onPlayerTick);
        bus.addListener(ZanpakutoEvents::onDrops);
        bus.addListener(ZanpakutoEvents::onClone);
        bus.addListener(ZanpakutoEvents::onWakeUp);
        bus.addListener(ZanpakutoEvents::onDamaged);
        bus.addListener(ZanpakutoEvents::onLoggedOut);
    }

    // ---- The bond ----

    /** Whether the bond can grow now: a soul that bonds, carrying a nameless Asauchi, that does not know the name yet. */
    public static boolean mayBond(Player player, SoulData data, long now) {
        return mayBond(data, now, ZanpakutoLogic.carriesUnboundAsauchi(player));
    }

    private static boolean mayBond(SoulData data, long now, boolean carrying) {
        return carrying && ZanpakutoLogic.canBond(data, now) && !data.zanpakuto().named();
    }

    /** Whether the hidden leaning can still move: the bond can grow and no spirit has been chosen. */
    public static boolean mayLean(Player player, SoulData data, long now) {
        return mayBond(player, data, now) && data.zanpakuto().spirit().isEmpty();
    }

    /**
     * Grows the bond by a source worth {@code raw}, after the server's multiplier and up to full. The
     * blade whispers once for the highest quarter crossed; at full it locks in its spirit.
     *
     * @return how much the bond grew
     */
    public static int giveBond(Player player, SoulData data, int raw, long now) {
        return raw <= 0 ? 0 : giveBond(player, data, raw, now, ZanpakutoLogic.carriesUnboundAsauchi(player));
    }

    /** As {@link #giveBond(Player, SoulData, int, long)}, told whether a nameless Asauchi is carried, as the one just used is. */
    private static int giveBond(Player player, SoulData data, int raw, long now, boolean carrying) {
        if (raw <= 0 || !mayBond(data, now, carrying)) {
            return 0;
        }
        ZanpakutoBond bond = data.zanpakuto();
        int gained = Math.min(ZanpakutoBond.FULL - bond.bond(), ZanpakutoLogic.scaledBond(raw));
        if (gained <= 0) {
            return 0;
        }
        bond = bond.withBond(bond.bond() + gained);
        int quarter = ZanpakutoLogic.quarterOf(bond.bond());
        boolean whisper = quarter > bond.whispered();
        if (whisper) {
            bond = bond.withWhispered(quarter);
        }
        data.setZanpakuto(bond);
        if (whisper) {
            player.sendSystemMessage(Component.translatable("message.bleach.bond.whisper." + quarter)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            player.playNotifySound(ModSounds.ZANPAKUTO_WHISPER.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            if (quarter >= 4 && player instanceof ServerPlayer serverPlayer) {
                Milestones.grant(serverPlayer, Milestones.ASAUCHI_BOND);
            }
        }
        if (bond.isFull()) {
            ensureSpirit(player, data);
        }
        return gained;
    }

    /** Leans the blade towards one kind of spirit, while that can still change which one it becomes. */
    public static void lean(Player player, SoulData data, ZanpakutoType type, int amount, long now) {
        if (amount > 0) {
            lean(data, type, amount, now, ZanpakutoLogic.carriesUnboundAsauchi(player));
        }
    }

    private static void lean(SoulData data, ZanpakutoType type, int amount, long now, boolean carrying) {
        if (amount <= 0 || !mayBond(data, now, carrying) || data.zanpakuto().spirit().isPresent()) {
            return;
        }
        ZanpakutoBond bond = data.zanpakuto();
        long value = Math.min(ZanpakutoBond.MAX_AFFINITY, (long) bond.affinity(type) + amount);
        data.setZanpakuto(bond.withAffinity(type, (int) value));
    }

    /**
     * Once the bond is full, picks the spirit the blade becomes from the leaning, if none is chosen
     * yet. Never changes a chosen one. Does nothing before the bond is full, so the leaning keeps
     * counting until then.
     */
    public static void ensureSpirit(Player player, SoulData data) {
        ZanpakutoBond bond = data.zanpakuto();
        if (!bond.isFull() || bond.spirit().isPresent()) {
            return;
        }
        ZanpakutoSpirits.registry(player.level())
                .flatMap(registry -> ZanpakutoLogic.chooseSpirit(data, player.getUUID(), registry))
                .ifPresent(id -> data.setZanpakuto(bond.withSpirit(Optional.of(id))));
    }

    /**
     * The soul learns its zanpakutō's name. The blade (if it is a nameless Asauchi or already the
     * soul's) is bound to it with the spirit's name, colour and form; the soul grows, the first time,
     * and is told how to call it out.
     */
    public static void learnName(Player player, SoulData data, ResourceLocation id, ZanpakutoSpirit spirit, ItemStack stack) {
        ZanpakutoBond bond = data.zanpakuto();
        boolean constant = spirit.isConstant();
        if (ZanpakutoLogic.isUnboundAsauchi(stack) || ZanpakutoLogic.isOwnBlade(stack, player)) {
            ZanpakutoItem.bind(stack, player, id, spirit, constant && bond.firstReleaseDone());
        }
        Component name = spirit.name().copy().withColor(spirit.colour());
        boolean first = !bond.named();
        data.setZanpakuto(bond.withSpirit(Optional.of(id)).asNamed(name, constant).withWhispered(4));
        if (first) {
            SoulLogic.grow(data, BleachConfig.get(BleachConfig.NAME_GROWTH));
        }
        player.sendSystemMessage(Component.translatable("message.bleach.zanpakuto.named", name));
        player.sendSystemMessage(Component.translatable(
                constant ? "message.bleach.zanpakuto.call_constant" : "message.bleach.zanpakuto.call",
                Component.keybind("key.bleach.zanpakuto_release")).withStyle(ChatFormatting.GRAY));
        if (player instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.ZANPAKUTO_NAME);
        }
    }

    // ---- Keeping the blades in line with the soul ----

    /**
     * Brings every Asauchi the player carries in line with their bond: their own blades lose a name
     * the soul no longer knows, take the spirit's form, and seal if a release went stale (or is a
     * second copy while the one in hand is released, or a constant release the soul has yet to make
     * on this spirit); someone else's released blade seals; an anvil's name comes off a bound blade.
     * A named soul with no blade of its own sees the spirit take shape again in a nameless Asauchi it
     * carries.
     */
    public static void reconcile(Player player, SoulData data) {
        ZanpakutoBond bond = data.zanpakuto();
        Optional<ResourceLocation> id = bond.spirit();
        Optional<ZanpakutoSpirit> spirit = bond.named() ? ZanpakutoSpirits.get(player.level(), id) : Optional.empty();
        boolean released = bond.constant() && bond.firstReleaseDone();
        ItemStack held = player.getMainHandItem();
        boolean holdingRelease = ZanpakutoLogic.isOwnBlade(held, player) && ZanpakutoItem.blade(held).released();
        boolean ownFound = false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof ZanpakutoItem)) {
                continue;
            }
            ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
            if (!blade.isBound()) {
                continue;
            }
            if (blade.isOwnedBy(player.getUUID())) {
                if (!bond.named()) {
                    ZanpakutoItem.unbind(stack);
                    continue;
                }
                ownFound = true;
                if (!blade.spirit().equals(id)) {
                    if (spirit.isPresent()) {
                        ZanpakutoItem.bind(stack, player, id.get(), spirit.get(), released);
                    }
                } else if (blade.released() && !blade.constant() && (!data.shikaiActive || (holdingRelease && stack != held))) {
                    ShikaiEvents.sealStack(stack, player.level());
                } else if (blade.released() && blade.constant() && !bond.firstReleaseDone() && spirit.isPresent()) {
                    // Kept elsewhere while the spirit changed away and back: released before the soul released it.
                    ZanpakutoItem.setReleased(stack, spirit.get(), false);
                } else if (spirit.isPresent()) {
                    // Puts back a model or stats something else changed.
                    ZanpakutoItem.applyForm(stack, blade.released() ? spirit.get().shikai() : spirit.get().sealed());
                }
            } else if (blade.released() && !blade.constant()) {
                ShikaiEvents.sealStack(stack, player.level());
            }
            if (stack.has(DataComponents.CUSTOM_NAME)) {
                stack.remove(DataComponents.CUSTOM_NAME);
            }
        }
        if (bond.named() && !ownFound && spirit.isPresent()) {
            ItemStack target = unboundToReform(player);
            if (!target.isEmpty()) {
                ZanpakutoItem.bind(target, player, id.get(), spirit.get(), released);
                player.displayClientMessage(Component.translatable("message.bleach.zanpakuto.reformed", bond.name()), true);
            }
        }
    }

    /** The nameless Asauchi a lost blade re-forms in: the one in hand, else the first carried. */
    private static ItemStack unboundToReform(Player player) {
        if (ZanpakutoLogic.isUnboundAsauchi(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ZanpakutoLogic.isUnboundAsauchi(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- Bond sources ----

    /** One of meditation's Reiatsu Control grants, made while sitting still or in Jinzen. */
    public static void onMeditationGrant(Player player, SoulData data) {
        giveBond(player, data, BleachConfig.get(BleachConfig.BOND_PER_MEDITATION), player.level().getGameTime());
    }

    /** A close call survived, inside its cooldown: a lean to constant release, and bond. */
    public static void onCloseCall(Player player, SoulData data, long now) {
        lean(player, data, ZanpakutoType.CONSTANT_RELEASE, ZanpakutoLogic.closeCallLean(data), now);
        giveBond(player, data, ZanpakutoLogic.closeCallBond(data), now);
    }

    /**
     * A Hollow purified with a nameless Asauchi: a lean to power, to kidō in rain, water, snow or
     * heat, and to constant release with reiatsu released, then bond for the Hollow's reiatsu.
     */
    public static void onPurified(Player killer, Hollow hollow, ItemStack weapon) {
        if (!ZanpakutoLogic.isUnboundAsauchi(weapon)) {
            return;
        }
        // The blade that did it is carried, wherever it is now.
        SoulData data = SoulLogic.get(killer);
        long now = killer.level().getGameTime();
        lean(data, ZanpakutoType.POWER, PURIFICATION_LEAN, now, true);
        if (ZanpakutoLogic.isElementalPlace(killer)) {
            lean(data, ZanpakutoType.KIDO, PURIFICATION_LEAN, now, true);
        }
        if (data.isReiatsuReleased()) {
            lean(data, ZanpakutoType.CONSTANT_RELEASE, PURIFICATION_LEAN, now, true);
        }
        giveBond(killer, data, ZanpakutoLogic.purificationBond(hollow.soul().maxReiryoku()), now, true);
    }

    /** A fresh blow to a Hollow's mask with a nameless Asauchi in hand leans to power. */
    public static void onMaskStrike(Player player) {
        if (ZanpakutoLogic.isUnboundAsauchi(player.getMainHandItem())) {
            lean(SoulLogic.get(player), ZanpakutoType.POWER, 1, player.level().getGameTime(), true);
        }
    }

    /** A bare-handed blow that lands on a Hollow leans to power, at most once every half second. */
    public static void onHollowStruck(Player player, DamageSource source) {
        if (!player.getMainHandItem().isEmpty() || source.getDirectEntity() != player || !source.is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        if (player.tickCount - data.lastAffinityHitTick < HAKUDA_LEAN_COOLDOWN_TICKS || !mayLean(player, data, now)) {
            return;
        }
        data.lastAffinityHitTick = player.tickCount;
        lean(player, data, ZanpakutoType.POWER, 1, now);
    }

    /** Opening Jinzen bonds, at most once per cooldown, and each time it does it leans to kidō. */
    public static void creditJinzen(Player player, SoulData data, long now) {
        long cooldown = Math.round(BleachConfig.get(BleachConfig.JINZEN_BOND_COOLDOWN_SECONDS) * 20.0);
        if (now - data.zanpakuto().lastJinzenBondAt() < cooldown || !mayBond(player, data, now)) {
            return;
        }
        lean(player, data, ZanpakutoType.KIDO, 1, now);
        data.setZanpakuto(data.zanpakuto().withLastJinzenBondAt(now));
        giveBond(player, data, BleachConfig.get(BleachConfig.BOND_PER_JINZEN), now);
    }

    /**
     * A full night's sleep with the Asauchi bonds, at most once per cooldown.
     *
     * @return how much the bond grew
     */
    public static int onFullNightsSleep(Player player, SoulData data, long now) {
        long cooldown = Math.round(BleachConfig.get(BleachConfig.SLEEP_BOND_COOLDOWN_MINUTES) * (double) TICKS_PER_MINUTE);
        if (now - data.zanpakuto().lastSleepBondAt() < cooldown) {
            return 0;
        }
        int gained = giveBond(player, data, BleachConfig.get(BleachConfig.BOND_PER_SLEEP), now);
        if (gained > 0) {
            data.setZanpakuto(data.zanpakuto().withLastSleepBondAt(now));
        }
        return gained;
    }

    // ---- Death ----

    /**
     * Takes the soul's own blades out of its death drops, if the server keeps them, and holds sealed
     * copies until it respawns. Someone else's blade drops as anything else does.
     */
    public static void keepBlades(Player player, Collection<ItemEntity> drops) {
        if (!BleachConfig.get(BleachConfig.KEEP_ON_DEATH)) {
            return;
        }
        List<ItemStack> kept = new ArrayList<>();
        Iterator<ItemEntity> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next().getItem();
            if (ZanpakutoLogic.isOwnBlade(stack, player)) {
                ItemStack copy = stack.copy();
                ShikaiEvents.sealStack(copy, player.level());
                kept.add(copy);
                it.remove();
            }
        }
        if (!kept.isEmpty()) {
            List<ItemStack> all = new ArrayList<>(player.getData(SoulAttachments.KEPT_BLADES));
            all.addAll(kept);
            player.setData(SoulAttachments.KEPT_BLADES, List.copyOf(all));
        }
    }

    /** Hands the blades kept through death to the respawned player, dropping any that do not fit. */
    public static void restoreBlades(Player original, Player clone) {
        List<ItemStack> kept = original.getData(SoulAttachments.KEPT_BLADES);
        original.setData(SoulAttachments.KEPT_BLADES, List.of());
        clone.setData(SoulAttachments.KEPT_BLADES, List.of());
        for (ItemStack stack : kept) {
            ItemStack copy = stack.copy();
            if (!clone.getInventory().add(copy) && !copy.isEmpty()) {
                clone.drop(copy, false);
            }
        }
    }

    // ---- Listeners ----

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.isAlive() || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        if (player.tickCount % RECONCILE_TICKS == 0) {
            reconcile(player, data);
        }
        if (player.tickCount % CARRY_TICKS == 0) {
            giveBond(player, data, BleachConfig.get(BleachConfig.BOND_PER_MINUTE_CARRIED), now);
        }
        if (player.tickCount % LEAN_TICKS == 0) {
            if (ReishiFooting.isActive(player)) {
                lean(player, data, ZanpakutoType.KIDO, 1, now);
            }
            if (data.isReiatsuReleased()) {
                lean(player, data, ZanpakutoType.CONSTANT_RELEASE, 1, now);
            }
        }
        if (data.jinzen != null && !player.isUsingItem()) {
            JinzenLogic.end(player, data);
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            keepBlades(player, event.getDrops());
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            restoreBlades(event.getOriginal(), event.getEntity());
        }
    }

    /** Only a night slept through: the night skipped, not a player getting up or being woken. */
    private static void onWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || event.wakeImmediately() || event.updateLevel()) {
            return;
        }
        onFullNightsSleep(player, SoulLogic.get(player), player.level().getGameTime());
    }

    /** Any hurt breaks Jinzen: letting go of the blade ends it and closes the screen. */
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide() && event.getNewDamage() > 0.0F) {
            SoulData data = SoulLogic.get(player);
            if (data.jinzen != null) {
                player.stopUsingItem();
                JinzenLogic.end(player, data);
            }
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        JinzenLogic.end(event.getEntity(), SoulLogic.get(event.getEntity()));
    }
}
