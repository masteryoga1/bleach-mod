package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-side hooks that run the soul: regeneration, training, growth, death and syncing. */
public final class SoulEvents {
    /** Items that train Zanjutsu, on top of every vanilla-style sword. */
    public static final TagKey<Item> ZANJUTSU_WEAPONS = TagKey.create(Registries.ITEM, BleachMod.id("zanjutsu_weapons"));

    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final int MILESTONE_CHECK_TICKS = 20;
    private static final int COMBAT_XP_COOLDOWN_TICKS = 10;
    private static final int SPRINT_TICKS_PER_XP = 20;
    private static final int MEDITATION_TICKS_PER_XP = 100;
    /** Meditation stops training after this many XP grants (one minute) until the player moves again. */
    private static final int MEDITATION_GRANTS_PER_SESSION = 12;
    private static final float CLOSE_CALL_HEALTH_FRACTION = 0.3F;

    private SoulEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(SoulEvents::onPlayerTick);
        bus.addListener(SoulEvents::onIncomingDamage);
        bus.addListener(SoulEvents::onDamageTaken);
        // Lowest priority, and skipped if cancelled, so it only counts totems that really save the player.
        bus.addListener(EventPriority.LOWEST, SoulEvents::onTotemUsed);
        bus.addListener(SoulEvents::onDeath);
        bus.addListener(SoulEvents::onLoggedIn);
        bus.addListener(SoulEvents::onRespawn);
        bus.addListener(SoulEvents::onChangedDimension);
        bus.addListener(SoulEvents::onClone);
        SpiritEvents.register(bus);
        ChainEvents.register(bus);
        ReiatsuEvents.register(bus);
        ShinigamiEvents.register(bus);
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
        if (mayRegenerate(player, data)) {
            SoulLogic.regenTick(data, meditating || player.isSleeping(),
                    ShinigamiLogic.regenMultiplier(player, data, player.level().getGameTime()));
        }

        if (meditating) {
            if (data.meditationGrants < MEDITATION_GRANTS_PER_SESSION
                    && ++data.meditationTicks >= MEDITATION_TICKS_PER_XP) {
                data.meditationTicks = 0;
                data.meditationGrants++;
                SoulLogic.addStatXp(player, data, Stat.REIATSU_CONTROL, 1);
            }
        } else {
            data.meditationTicks = 0;
            data.meditationGrants = 0;
        }

        if (player.isSprinting() && player.onGround() && movedSqr > 0.01) {
            if (++data.sprintTicks >= SPRINT_TICKS_PER_XP) {
                data.sprintTicks = 0;
                SoulLogic.addStatXp(player, data, Stat.HOHO, 1);
            }
        }

        if (player.tickCount % MILESTONE_CHECK_TICKS == 0) {
            Milestones.check(player, data);
        }
        if (data.isDirty() && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            BleachNetwork.syncSoul(player, data);
        }
    }

    /**
     * Whether reiryoku refills this tick. Releasing reiatsu pours it out, so it does not refill until
     * the release ends and the reiatsu settles.
     */
    public static boolean mayRegenerate(Player player, SoulData data) {
        return !data.isReiatsuReleased() && !ReiatsuEvents.isSettling(player, data);
    }

    /** Zanjutsu and Hakuda make melee hits stronger. Training happens once a hit lands, in {@link #onDamageTaken}. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        ServerPlayer attacker = meleeAttacker(event.getSource());
        Stat stat = attacker == null ? null : meleeStat(attacker.getMainHandItem());
        if (stat == null) {
            return;
        }
        SoulData data = SoulLogic.get(attacker);
        if (stat == Stat.HAKUDA) {
            event.setAmount(event.getAmount() + SoulLogic.hakudaBonus(data));
        } else {
            event.setAmount(event.getAmount() * SoulLogic.zanjutsuMultiplier(data));
        }
    }

    /** The player behind a direct melee hit, or null for anything else (arrows, magic, mobs). */
    private static ServerPlayer meleeAttacker(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer attacker
                && source.getDirectEntity() == attacker
                && source.is(DamageTypes.PLAYER_ATTACK)) {
            return attacker;
        }
        return null;
    }

    /** The stat a melee hit with this item uses: Hakuda bare-handed, Zanjutsu with a sword, else null. */
    private static Stat meleeStat(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return Stat.HAKUDA;
        }
        return isZanjutsuWeapon(weapon) ? Stat.ZANJUTSU : null;
    }

    public static boolean isZanjutsuWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.is(ZANJUTSU_WEAPONS);
    }

    /** Fires only for hits that got past shields and invulnerability frames. */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        trainFromLandedHit(event);
        if (event.getEntity() instanceof ServerPlayer player
                && player.isAlive()
                && event.getNewDamage() > 0.0F
                && player.getHealth() < player.getMaxHealth() * CLOSE_CALL_HEALTH_FRACTION) {
            closeCall(player, event.getSource());
        }
    }

    private static void trainFromLandedHit(LivingDamageEvent.Post event) {
        ServerPlayer attacker = meleeAttacker(event.getSource());
        Stat stat = attacker == null ? null : meleeStat(attacker.getMainHandItem());
        // Damage soaked by absorption hearts still counts as a hit that landed.
        boolean landed = event.getNewDamage() > 0.0F
                || event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0.0F;
        if (stat == null || !landed || event.getEntity() == attacker) {
            return;
        }
        SoulData data = SoulLogic.get(attacker);
        // One XP per swing at most, so sweeping several mobs or spam-clicking does not train faster.
        if (attacker.tickCount - data.lastCombatXpTick >= COMBAT_XP_COOLDOWN_TICKS) {
            data.lastCombatXpTick = attacker.tickCount;
            SoulLogic.addStatXp(attacker, data, stat, 1);
        }
    }

    /** A Totem of Undying saving you in a fight is the closest call there is. */
    private static void onTotemUsed(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            closeCall(player, event.getSource());
        }
    }

    /** Surviving a close call against another creature grows reiryoku, at most once per cooldown. */
    private static void closeCall(ServerPlayer player, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        long cooldown = BleachConfig.get(BleachConfig.CLOSE_CALL_COOLDOWN_SECONDS) * 20L;
        if (now - data.lastCloseCallTime() >= cooldown) {
            data.setLastCloseCallTime(now);
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
            // A release does not outlast the session.
            SoulLogic.get(player).setReiatsuReleased(false);
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

    /** The soul survives death, changed by it: see {@link SoulLogic#afterDeath}. */
    private static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            SoulData next = SoulLogic.afterDeath(SoulLogic.get(event.getOriginal()));
            if (next.becamePlus) {
                next.diedAt = event.getOriginal().position();
            }
            event.getEntity().setData(SoulAttachments.SOUL, next);
        }
    }
}
