package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    /** A Plus who meditates this long is reminded which way the Academy lies, once per sitting. */
    private static final int ACADEMY_REMINDER_TICKS = 40;
    private static final float CLOSE_CALL_HEALTH_FRACTION = 0.3F;
    /**
     * The health of a player leaving the End by its exit portal, which builds the player anew with all it
     * had (see {@link #onClone}) but before the stat modifiers are back on it, so a Menos would be cut down
     * to 20. Held only until {@link #onRespawn}, in the same call, has put them back.
     */
    private static final Map<UUID, Float> HEALTH_KEPT = new HashMap<>();

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
        HollowEvents.register(bus);
        HollowPlayerEvents.register(bus);
        GargantaEvents.register(bus);
        PlusEvents.register(bus);
        ZanpakutoEvents.register(bus);
        ShikaiEvents.register(bus);
        VisoredEvents.register(bus);
        KidoEvents.register(bus);
        TravelEvents.register(bus);
        DangaiEvents.register(bus);
        QuincyEvents.register(bus);
        QuincyArtsEvents.register(bus);
        QuincyNpcEvents.register(bus);
        BaitEvents.register(bus);
        DutyEvents.register(bus);
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

        // Jinzen counts too: its screen releases every key, so the soul no longer reads as crouching.
        boolean meditating = (player.isCrouching() && player.onGround() && movedSqr < 1.0E-4)
                || JinzenLogic.isSitting(player, data);
        if (mayRegenerate(player, data)) {
            SoulLogic.regenTick(data, meditating || player.isSleeping(),
                    ShinigamiLogic.regenMultiplier(player, data, player.level().getGameTime()));
        }

        if (meditating) {
            if (++data.meditatingFor == ACADEMY_REMINDER_TICKS) {
                AcademyLocator.remind(player, data);
            }
            if (data.meditationGrants < MEDITATION_GRANTS_PER_SESSION
                    && ++data.meditationTicks >= MEDITATION_TICKS_PER_XP) {
                data.meditationTicks = 0;
                data.meditationGrants++;
                SoulLogic.addStatXp(player, data, Stat.REIATSU_CONTROL, 1);
                ZanpakutoEvents.onMeditationGrant(player, data);
                QuincyEvents.onMeditationGrant(player, data);
            }
        } else {
            data.meditationTicks = 0;
            data.meditationGrants = 0;
            data.meditatingFor = 0;
        }

        if (player.isSprinting() && player.onGround() && movedSqr > 0.01) {
            if (++data.sprintTicks >= SPRINT_TICKS_PER_XP) {
                data.sprintTicks = 0;
                SoulLogic.addStatXp(player, data, Stat.HOHO, 1);
            }
        }

        QuincyEvents.tick(player, data, meditating);
        VisoredEvents.tick(player, data, player.level().getGameTime());

        if (player.tickCount % MILESTONE_CHECK_TICKS == 0) {
            Milestones.check(player, data);
        }
        if (data.isDirty() && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            BleachNetwork.syncSoul(player, data);
        }
    }

    /**
     * Whether reiryoku refills this tick. Releasing reiatsu pours it out, so it does not refill until
     * the release ends and the reiatsu settles; standing on reishi footing paid in reiryoku, holding a
     * Shikai that can seal, an Arrancar's Resurrección or a Visored's mask, or weaving Ransōtengai's
     * threads spends it as it comes; channelling Kaidō pours it out, and each pulse of it that does
     * something rests it for a second after (see {@link KidoHeal}). A Quincy's footing is paid in
     * reishi instead.
     */
    public static boolean mayRegenerate(Player player, SoulData data) {
        return mayRegenerate(player, data, player.level().getGameTime());
    }

    /** {@link #mayRegenerate(Player, SoulData)} at this game time: Kaidō's refill asks it at the pulse's. */
    public static boolean mayRegenerate(Player player, SoulData data, long now) {
        boolean footingOnReiryoku = ReishiFooting.isActive(player) && !QuincyLogic.footingOnReishi(data, now);
        return !data.isReiatsuReleased() && !ReiatsuEvents.isSettling(player, data) && !footingOnReiryoku
                && !data.isShikaiActive() && !QuincyRansotengai.isWoven(data) && !ArrancarLogic.released(data)
                && !VisoredLogic.isMasked(data) && data.kidoHeal == null && now >= data.kidoHealRestUntil;
    }

    /**
     * Zanjutsu and Hakuda make melee hits stronger (Zanjutsu never in a Hollow's hand). Training happens
     * once a hit lands, in {@link #onDamageTaken}.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        ServerPlayer attacker = meleeAttacker(event.getSource());
        if (attacker == null) {
            return;
        }
        SoulData data = SoulLogic.get(attacker);
        Stat stat = meleeStat(data, attacker.getMainHandItem());
        if (stat == null) {
            return;
        }
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

    /**
     * The stat a melee hit with this item uses: Hakuda bare-handed, Zanjutsu with a sword, else null.
     * A Hollow knows nothing of swordsmanship: a blade in its claws deals its plain damage and trains
     * nothing, though its bare-handed blows still use Hakuda.
     */
    private static Stat meleeStat(SoulData data, ItemStack weapon) {
        if (weapon.isEmpty()) {
            return Stat.HAKUDA;
        }
        return isZanjutsuWeapon(weapon) && !HollowPlayerLogic.isHollow(data) ? Stat.ZANJUTSU : null;
    }

    /**
     * Swords and the tagged blades train Zanjutsu. A Seele Schneider counts only with its blade
     * formed: a bare hilt is no blade.
     */
    public static boolean isZanjutsuWeapon(ItemStack stack) {
        return (stack.getItem() instanceof SwordItem || stack.is(ZANJUTSU_WEAPONS))
                && !(SeeleSchneiderItem.isSeele(stack) && !SeeleSchneiderItem.isFormed(stack));
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
        if (attacker == null) {
            return;
        }
        SoulData data = SoulLogic.get(attacker);
        Stat stat = meleeStat(data, attacker.getMainHandItem());
        // Damage soaked by absorption hearts still counts as a hit that landed.
        boolean landed = event.getNewDamage() > 0.0F
                || event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0.0F;
        if (stat == null || !landed || event.getEntity() == attacker) {
            return;
        }
        // One grant per swing at most, so sweeping several mobs or spam-clicking does not train faster.
        // A Hollow is worth more than a mundane creature, and its mask more still. An Arrancar NPC is worth what
        // an unmasked Hollow is: its mask is only a remnant, with nothing to strike.
        if (attacker.tickCount - data.lastCombatXpTick >= COMBAT_XP_COOLDOWN_TICKS) {
            data.lastCombatXpTick = attacker.tickCount;
            int xp = event.getEntity() instanceof Hollow hollow
                    ? HollowLogic.hitXp(true, hollow.wasMaskStruckAt(attacker.level().getGameTime()))
                    : event.getEntity() instanceof ArrancarNpc ? HollowLogic.hitXp(true, false) : 1;
            SoulLogic.addStatXp(attacker, data, stat, xp);
        }
    }

    /** A Totem of Undying saving you in a fight is the closest call there is. */
    private static void onTotemUsed(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            closeCall(player, event.getSource());
        }
    }

    /** Surviving a close call against another creature grows reiryoku, and the Asauchi bond, at most once per cooldown. */
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
            ZanpakutoEvents.onCloseCall(player, data, now);
        }
    }

    /**
     * Killing a hostile creature of the mundane world grows reiryoku only if the server turns it on
     * (it is off by default). Spiritual beings, which are what really count, are handled by
     * {@link HollowEvents}.
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof Player killer && SoulLogic.hasSoul(killer)
                && victim instanceof Enemy && !SoulLogic.isSpirit(victim)) {
            SoulLogic.grow(SoulLogic.get(killer),
                    victim.getMaxHealth() * BleachConfig.get(BleachConfig.MUNDANE_KILL_GROWTH_PER_MAX_HEALTH));
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
            Float kept = HEALTH_KEPT.remove(player.getUUID());
            if (kept != null) {
                player.setHealth(Math.min(kept, player.getMaxHealth()));
            }
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SoulData data = SoulLogic.get(player);
            BleachNetwork.syncSoul(player, data);
            // The client has a new player for the new dimension, which knows nothing of its own looks
            // (a Hollow's cracked mask, say): the next tick sends them again.
            data.lastSentAppearance = null;
        }
    }

    /**
     * Reapplies the transient stat modifiers, settles the Quincy bloodline (see
     * {@link QuincyEvents#onRefresh}), brings the Visored record's clocks into this world's time (see
     * {@link VisoredEvents#onRefresh}) and sends the client a full copy.
     */
    public static void refresh(ServerPlayer player) {
        SoulData data = SoulLogic.get(player);
        if (!data.isInitialized()) {
            SoulLogic.initialize(data);
        }
        SoulLogic.applyStatModifiers(player, data);
        QuincyEvents.onRefresh(player, data);
        VisoredEvents.onRefresh(player, data);
        BleachNetwork.syncSoul(player, data);
    }

    /**
     * The soul survives death, changed by it: see {@link SoulLogic#afterDeath}. A player leaving the End
     * keeps its soul as it was, and its health too (see {@link #HEALTH_KEPT}).
     */
    private static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            SoulData next = SoulLogic.afterDeath(SoulLogic.get(event.getOriginal()));
            if (next.becamePlus) {
                next.diedAt = event.getOriginal().position();
            }
            event.getEntity().setData(SoulAttachments.SOUL, next);
        } else {
            HEALTH_KEPT.put(event.getEntity().getUUID(), event.getOriginal().getHealth());
        }
    }
}
