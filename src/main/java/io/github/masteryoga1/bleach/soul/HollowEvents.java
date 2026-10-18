package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.registry.BleachTags;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Fighting Hollows on the server: blows to the mask, a Hollow's bite on a Plus's chain, and what a
 * Hollow's death means, purified or slain. It also settles the first Hollow, Fishbone D, when it
 * dies or its quarry does. The rules themselves are in {@link HollowLogic}. What can be done without
 * a connection takes a {@link Player}, so game tests can use mock players.
 */
public final class HollowEvents {
    /** A hit after this many ticks of invulnerability is a fresh one; vanilla only lets it through if it hits harder. */
    private static final int FRESH_HIT_INVULNERABLE_TICKS = 10;
    /** How long, at most, Fishbone D lingers after its quarry dies. */
    private static final int QUARRY_DEATH_LINGER_TICKS = 100;
    private static final int BITE_MESSAGE_INTERVAL_TICKS = 100;

    private HollowEvents() {
    }

    public static void register(IEventBus bus) {
        // Normal priority: after SpiritEvents has cancelled the blows of the unaware and SoulEvents has
        // added Zanjutsu or Hakuda, and before ReiatsuEvents weighs the reiatsu difference.
        bus.addListener(HollowEvents::onIncomingDamage);
        bus.addListener(HollowEvents::onDamageTaken);
        bus.addListener(HollowEvents::onDeath);
    }

    /**
     * A blow or arrow that meets the mask before the body hits twice as hard. A fresh one also cracks
     * the mask, stops the Hollow healing and makes it reel. The event fires before the invulnerability
     * check, so a hit inside the invulnerability frames gets the damage but none of the rest. What does
     * no harm, such as a snowball, does nothing to the mask.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Hollow hollow) || hollow.level().isClientSide() || event.getAmount() <= 0.0F
                || !HollowMask.struck(hollow, event.getSource())) {
            return;
        }
        event.setAmount(event.getAmount() * (float) BleachConfig.get(BleachConfig.MASK_DAMAGE_MULTIPLIER));
        if (hollow.invulnerableTime <= FRESH_HIT_INVULNERABLE_TICKS) {
            hollow.onMaskStruck(hollow.level().getGameTime());
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                Milestones.grant(player, Milestones.MASK_STRIKE);
                ZanpakutoEvents.onMaskStrike(player);
            }
        }
    }

    /** Fires only for hits that got past shields and invulnerability frames. Hit XP is given in {@link SoulEvents}. */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Entity cause = event.getSource().getEntity();
        if (cause instanceof Hollow) {
            bite(victim);
        } else if (victim instanceof Hollow hollow && cause instanceof Player player && SoulLogic.hasSoul(player)) {
            countBodyHit(player, hollow);
            ZanpakutoEvents.onHollowStruck(player, event.getSource());
        }
    }

    /** A Hollow's bite gnaws at the chain of a Plus, spirit or player. */
    private static void bite(LivingEntity victim) {
        if (victim instanceof PlusSpirit plus) {
            HollowLogic.biteChain(plus.soul());
        } else if (victim instanceof Player player && SoulLogic.hasSoul(player)) {
            SoulData data = SoulLogic.get(player);
            if (data.state() != SoulState.PLUS || !HollowLogic.biteChain(data)) {
                return;
            }
            long now = player.level().getGameTime();
            if (now - data.lastBiteMessageAt >= BITE_MESSAGE_INTERVAL_TICKS) {
                data.lastBiteMessageAt = now;
                player.displayClientMessage(Component.translatable("message.bleach.chain_bitten")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
        }
    }

    /** Five blows in a row that land on a Hollow's body and not its mask, and the soul is told where to aim. Once. */
    private static void countBodyHit(Player player, Hollow hollow) {
        SoulData data = SoulLogic.get(player);
        if (hollow.wasMaskStruckAt(hollow.level().getGameTime())) {
            data.bodyHitsWithoutMask = 0;
            return;
        }
        if (++data.bodyHitsWithoutMask >= HollowLogic.MASK_HINT_HITS && !data.hunt().maskHinted()) {
            data.bodyHitsWithoutMask = 0;
            data.setHunt(data.hunt().withMaskHinted(true));
            player.sendSystemMessage(Component.translatable("message.bleach.mask_hint")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * After {@link PlusEvents}, which may have saved a Plus. A Hollow killed by a player is purified or
     * slain; Fishbone D's death settles its quarry's first Hollow; a player's death may end the first
     * Hollow too, and a Hollow player killed by another is worth reiryoku like a Hollow mob.
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (victim instanceof Hollow hollow) {
            onHollowDeath(hollow, source);
        } else if (victim instanceof Player player) {
            if (player instanceof ServerPlayer quarry) {
                endFirstHollowFor(quarry);
            }
            onHollowPlayerDeath(player, source);
        }
    }

    private static void onHollowDeath(Hollow hollow, DamageSource source) {
        if (source.getEntity() instanceof Player killer && !hollow.isPurifying()) {
            if (!SoulLogic.hasSoul(killer)) {
                // No soul to grow, but the experience a Hollow is worth is still the killer's.
                killer.giveExperiencePoints(hollow.kind().xp);
            } else if (HollowLogic.purifies(source, SoulLogic.get(killer), hollow.level().getGameTime())) {
                hollow.startPurification();
                ItemStack weapon = source.getWeaponItem();
                onPurified(killer, hollow, weapon != null ? weapon : ItemStack.EMPTY);
            } else {
                onSlain(killer, hollow);
                if (source.is(BleachTags.DESTROYS_SOULS)) {
                    QuincyEvents.onSoulDestroyed(killer, hollow);
                }
            }
        }
        if (hollow instanceof FishboneD fishbone) {
            settleFirstHollow(fishbone);
        }
    }

    /**
     * A soul purified a Hollow: its reiryoku grows by half as much again as for slaying it, and it
     * trains Zanjutsu, and a nameless Asauchi that did it bonds. The purified soul's light eases the
     * Hollow poison of Quincy nearby. Called once the Hollow has started to dissolve; techniques that
     * purify (Getsuga Tenshō) come through here too.
     */
    public static void onPurified(Player killer, Hollow hollow, ItemStack weapon) {
        SoulData data = SoulLogic.get(killer);
        SoulLogic.grow(data, HollowLogic.killGrowth(hollow.soul().maxReiryoku(), data.maxReiryoku(), true));
        SoulLogic.addStatXp(killer, data, Stat.ZANJUTSU, BleachConfig.get(BleachConfig.PURIFICATION_ZANJUTSU_XP));
        data.setHunt(data.hunt().withPurified(data.hunt().purified() + 1));
        killer.giveExperiencePoints(hollow.kind().xp);
        killer.displayClientMessage(Component.translatable("message.bleach.hollow.purified")
                .withStyle(ChatFormatting.AQUA), true);
        if (killer instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.PURIFIED);
            if (hollow instanceof FishboneD) {
                Milestones.grant(player, Milestones.FISHBONE_D);
            }
        }
        ZanpakutoEvents.onPurified(killer, hollow, weapon);
        if (hollow.level() instanceof ServerLevel level) {
            QuincyEvents.onPurifiedNearby(level, hollow.position());
        }
    }

    /**
     * A soul destroyed a Hollow without purifying it: its reiryoku still grows. A Heilig Pfeil
     * destroys the soul itself as well (see {@link QuincyEvents#onSoulDestroyed}).
     */
    static void onSlain(Player killer, Hollow hollow) {
        SoulData data = SoulLogic.get(killer);
        SoulLogic.grow(data, HollowLogic.killGrowth(hollow.soul().maxReiryoku(), data.maxReiryoku(), false));
        data.setHunt(data.hunt().withSlain(data.hunt().slain() + 1));
        killer.giveExperiencePoints(hollow.kind().xp);
    }

    /**
     * A Hollow or Arrancar player killed by another player is worth as much as a Hollow of their
     * reiatsu, but grows the killer's reiryoku only once in a while: they come back as they were. A
     * Quincy's arrow counts the soul as destroyed.
     */
    private static void onHollowPlayerDeath(Player victim, DamageSource source) {
        if (!(source.getEntity() instanceof Player killer) || killer == victim
                || !SoulLogic.hasSoul(killer) || !SoulLogic.hasSoul(victim)) {
            return;
        }
        SoulData victimData = SoulLogic.get(victim);
        if (victimData.state() != SoulState.HOLLOW && victimData.state() != SoulState.ARRANCAR) {
            return;
        }
        SoulData data = SoulLogic.get(killer);
        long now = killer.level().getGameTime();
        boolean purified = HollowLogic.purifies(source, data, now);
        HuntRecord hunt = data.hunt();
        if (HollowLogic.playerKillGrows(hunt, now)) {
            SoulLogic.grow(data, HollowLogic.killGrowth(victimData.maxReiryoku(), data.maxReiryoku(), purified));
            hunt = hunt.withLastPlayerKillGrowthAt(now);
        }
        data.setHunt(purified ? hunt.withPurified(hunt.purified() + 1) : hunt.withSlain(hunt.slain() + 1));
        if (!purified && source.is(BleachTags.DESTROYS_SOULS)) {
            QuincyEvents.onHollowPlayerDestroyed(killer, victim);
        }
    }

    /**
     * Fishbone D died, whatever killed it: its quarry has met its first Hollow, and if still standing
     * has lived through it. Whoever purified it was told so in {@link #onPurified}.
     */
    private static void settleFirstHollow(FishboneD fishbone) {
        UUID quarryId = fishbone.quarry();
        MinecraftServer server = fishbone.getServer();
        ServerPlayer quarry = quarryId == null || server == null ? null : server.getPlayerList().getPlayer(quarryId);
        if (quarry == null || !SoulLogic.hasSoul(quarry)) {
            return;
        }
        SoulData data = SoulLogic.get(quarry);
        if (fishbone.getUUID().equals(data.firstHollowId)) {
            data.firstHollowId = null;
        }
        // A quarry whose first Hollow was already settled (it died to this one, or it was settled before) is
        // told nothing, as when one retreats.
        boolean settled = data.hunt().firstHollowDone();
        data.setHunt(data.hunt().withFirstHollowDone(true));
        if (!settled && quarry.isAlive()) {
            quarry.sendSystemMessage(Component.translatable("message.bleach.first_hollow.survived")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            Milestones.grant(quarry, Milestones.FIRST_HOLLOW);
        }
    }

    /**
     * The quarry died while Fishbone D hunted it, within its reach: the first Hollow is over all the
     * same (in canon, the way a soul becomes a Plus), and Fishbone D slips away soon after, without a
     * word. One that lost its quarry, or had already gone, settles nothing; it comes back.
     */
    private static void endFirstHollowFor(ServerPlayer quarry) {
        if (!SoulLogic.hasSoul(quarry)) {
            return;
        }
        SoulData data = SoulLogic.get(quarry);
        FishboneD fishbone = GargantaEvents.firstHollowOf(quarry, data);
        double range = FishboneD.QUARRY_RANGE;
        if (fishbone == null || fishbone.distanceToSqr(quarry) > range * range) {
            return;
        }
        fishbone.cutShort(QUARRY_DEATH_LINGER_TICKS);
        data.firstHollowId = null;
        data.setHunt(data.hunt().withFirstHollowDone(true));
    }
}
