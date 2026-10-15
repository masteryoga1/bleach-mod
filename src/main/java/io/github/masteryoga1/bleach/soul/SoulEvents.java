package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-side hooks that run the soul: regeneration, training, growth, death and syncing. */
public final class SoulEvents {
    /** Items that train Zanjutsu, on top of every vanilla-style sword. */
    public static final TagKey<Item> ZANJUTSU_WEAPONS = TagKey.create(Registries.ITEM, BleachMod.id("zanjutsu_weapons"));

    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final int COMBAT_XP_COOLDOWN_TICKS = 10;
    private static final int SPRINT_TICKS_PER_XP = 20;
    private static final int MEDITATION_TICKS_PER_XP = 100;
    private static final float CLOSE_CALL_HEALTH_FRACTION = 0.3F;

    private SoulEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(SoulEvents::onPlayerTick);
        bus.addListener(SoulEvents::onIncomingDamage);
        bus.addListener(SoulEvents::onDamageTaken);
        bus.addListener(SoulEvents::onDeath);
        bus.addListener(SoulEvents::onLoggedIn);
        bus.addListener(SoulEvents::onRespawn);
        bus.addListener(SoulEvents::onChangedDimension);
        bus.addListener(SoulEvents::onClone);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (!data.isInitialized()) {
            SoulLogic.initialize(data);
            SoulLogic.applyStatModifiers(player, data);
        }

        // Server-side players are moved by packets between ticks, so compare with last tick's position.
        double movedSqr = 0.0;
        if (data.hasLastPosition) {
            double dx = player.getX() - data.lastX;
            double dz = player.getZ() - data.lastZ;
            movedSqr = dx * dx + dz * dz;
        }
        data.lastX = player.getX();
        data.lastZ = player.getZ();
        data.hasLastPosition = true;

        boolean meditating = player.isCrouching() && player.onGround() && movedSqr < 1.0E-4;
        SoulLogic.regenTick(data, meditating || player.isSleeping());

        if (meditating) {
            if (++data.meditationTicks >= MEDITATION_TICKS_PER_XP) {
                data.meditationTicks = 0;
                SoulLogic.addStatXp(player, data, Stat.REIATSU_CONTROL, 1);
            }
        } else {
            data.meditationTicks = 0;
        }

        if (player.isSprinting() && player.onGround() && movedSqr > 0.01) {
            if (++data.sprintTicks >= SPRINT_TICKS_PER_XP) {
                data.sprintTicks = 0;
                SoulLogic.addStatXp(player, data, Stat.HOHO, 1);
            }
        }

        if (data.isDirty() && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            BleachNetwork.syncSoul(player, data);
        }
    }

    /** Zanjutsu and Hakuda: stronger melee hits, and training from landing them. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker
                || !source.is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        SoulData data = SoulLogic.get(attacker);
        ItemStack weapon = attacker.getMainHandItem();
        Stat trained;
        if (weapon.isEmpty()) {
            event.setAmount(event.getAmount() + SoulLogic.hakudaBonus(data));
            trained = Stat.HAKUDA;
        } else if (isZanjutsuWeapon(weapon)) {
            event.setAmount(event.getAmount() * SoulLogic.zanjutsuMultiplier(data));
            trained = Stat.ZANJUTSU;
        } else {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (victim instanceof ArmorStand) {
            return;
        }
        // One XP per swing at most, so spam-clicking through invulnerability frames does not train faster.
        if (attacker.tickCount - data.lastCombatXpTick >= COMBAT_XP_COOLDOWN_TICKS) {
            data.lastCombatXpTick = attacker.tickCount;
            SoulLogic.addStatXp(attacker, data, trained, 1);
        }
    }

    public static boolean isZanjutsuWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.is(ZANJUTSU_WEAPONS);
    }

    /** Surviving a close call in a fight grows reiryoku. */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.isAlive()
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == player
                || player.getHealth() >= player.getMaxHealth() * CLOSE_CALL_HEALTH_FRACTION) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        long cooldown = BleachConfig.get(BleachConfig.CLOSE_CALL_COOLDOWN_SECONDS) * 20L;
        if (now - data.lastCloseCallTime >= cooldown) {
            data.lastCloseCallTime = now;
            SoulLogic.grow(data, BleachConfig.get(BleachConfig.CLOSE_CALL_GROWTH));
        }
    }

    /**
     * Killing hostile creatures grows reiryoku. Once Hollows exist this will count spiritual beings
     * only; until then any hostile mob stands in for them.
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer killer && victim instanceof Enemy) {
            SoulLogic.grow(SoulLogic.get(killer),
                    victim.getMaxHealth() * BleachConfig.get(BleachConfig.KILL_GROWTH_PER_MAX_HEALTH));
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BleachNetwork.syncSoul(player, SoulLogic.get(player));
        }
    }

    /** Reapplies the transient stat modifiers and sends the client a full copy. */
    public static void refresh(ServerPlayer player) {
        SoulData data = SoulLogic.get(player);
        if (!data.isInitialized()) {
            SoulLogic.initialize(data);
        }
        SoulLogic.applyStatModifiers(player, data);
        BleachNetwork.syncSoul(player, data);
    }

    /** The soul survives death; the player comes back with part of their reiryoku. */
    private static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        SoulData copy = SoulLogic.get(event.getOriginal()).copy();
        copy.setReiryoku(copy.maxReiryoku() * (float) BleachConfig.get(BleachConfig.RESPAWN_REIRYOKU_FRACTION));
        event.getEntity().setData(SoulAttachments.SOUL, copy);
    }
}
