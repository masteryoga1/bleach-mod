package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A Hollow player devouring a soul: the Devour key from the client, the hold that seizes the prey, the
 * blow that swallows it and what the meal is worth. Also a Plus gnawing at its own Chain of Fate while
 * it holds the same key. The rules themselves are in {@link DevourLogic}; this class keeps the hold
 * going tick by tick and changes the world.
 *
 * <p>The hold lives in the devourer's soul as transients, so nothing about it is saved: a logout, a
 * death or a restart simply ends it, and the seize on the prey lapses by itself a moment later.
 */
public final class DevourEvents {
    /** Slowness II on a seized player, renewed every so often while the hold lasts. */
    public static final int SEIZED_SLOW_TICKS = 15;
    public static final int SEIZED_SLOW_INTERVAL_TICKS = 10;
    /** Gnawing at the chain: a moment of darkness, and a little pain while there is health to spare. */
    public static final int GNAW_BLINDNESS_TICKS = 30;
    public static final float GNAW_PAIN_ABOVE_HEALTH = 4.0F;
    public static final float GNAW_PAIN = 1.0F;
    /** The message of a hold broken by anything but letting go. */
    public static final String BROKEN = "message.bleach.devour.broken";

    private static final int SLOWNESS_II = 1;
    private static final long NEVER = HollowProgress.NEVER;

    private DevourEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(DevourEvents::onPlayerTick);
        bus.addListener(DevourEvents::onDamageTaken);
        bus.addListener(DevourEvents::onDrops);
        bus.addListener(DevourEvents::onLoggedOut);
    }

    /**
     * The Devour and Cero keys, from {@link io.github.masteryoga1.bleach.network.HollowKeyPayload} on the
     * server thread. A press of the Devour key starts a hold (a Hollow) or the gnawing (a Plus); letting go
     * ends either; sneaking with it turns the Hollow sight on or off. The target id is only the client's
     * hint. The Cero key starts, fires or lets fizzle a Menos's, an Arrancar's or a masked Visored's Cero
     * ({@link HollowCero}), and pressed by a sneaking Arrancar, its Gran Rey Cero ({@link GranReyCero}); an
     * Arrancar no longer devours: its Devour press and release start and end a Pesquisa focus ({@link Pesquisa}),
     * and its sight key does nothing.
     */
    public static void onKey(ServerPlayer player, HollowKey key, int targetId) {
        if (!SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        switch (key) {
            case PRESS -> {
                if (!player.isAlive()) {
                    return;
                }
                if (data.state() == SoulState.PLUS) {
                    DevourLogic.Gnaw gnaw = startGnaw(player, data, now);
                    if (gnaw == DevourLogic.Gnaw.QUINCY || gnaw == DevourLogic.Gnaw.OFF) {
                        HollowBody.actionBar(player, data, Component.translatable("message.bleach.gnaw." + gnaw.key())
                                .withStyle(ChatFormatting.GRAY), now);
                    }
                } else if (HollowPlayerLogic.isHollow(data)) {
                    Entity target = targetId >= 0 ? player.level().getEntity(targetId) : null;
                    DevourLogic.Refusal refusal = startDevour(player, data, target, now);
                    if (refusal != null && refusal != DevourLogic.Refusal.NOT_HOLLOW
                            && refusal != DevourLogic.Refusal.COOLDOWN) {
                        HollowBody.actionBar(player, data, Component.translatable("message.bleach.devour." + refusal.key())
                                .withStyle(ChatFormatting.GRAY), now);
                    }
                } else if (ArrancarLogic.isArrancar(data) && !player.isSpectator()) {
                    // The client's aim means nothing to it: Pesquisa feels all about it.
                    Pesquisa.press(player, data, now);
                }
            }
            case RELEASE -> {
                stopGnaw(data);
                if (data.devourTargetId >= 0) {
                    stopDevour(player, data, null, now);
                }
                Pesquisa.release(data);
            }
            case SIGHT -> {
                if (player.isAlive() && HollowPlayerLogic.isHollow(data)) {
                    toggleSight(player, data);
                }
            }
            case CERO -> {
                // A Visored is heard masked or not, so it is told why it gathers none (HollowCero#start).
                if (player.isAlive() && !player.isSpectator() && (HollowPlayerLogic.isHollowOrArrancar(data)
                        || VisoredLogic.isVisored(data) && VisoredLogic.enabled())) {
                    startCero(player, data, now);
                }
            }
            // The release and the cancel go to whichever beam gathers: the key is the same.
            case CERO_RELEASE -> {
                if (GranReyCero.charging(data)) {
                    GranReyCero.release(player, data, now);
                } else {
                    HollowCero.release(player, data, now);
                }
            }
            case CERO_CANCEL -> {
                HollowCero.cancel(player, data);
                GranReyCero.cancel(player, data);
            }
            case GRAN_REY_CERO -> {
                if (player.isAlive() && !player.isSpectator() && HollowPlayerLogic.isHollowOrArrancar(data)) {
                    if (!ArrancarLogic.isArrancar(data)) {
                        // A Hollow sneaking with the Cero key still gathers a Cero.
                        startCero(player, data, now);
                    } else {
                        GranReyCero.Refusal refusal = GranReyCero.start(player, data, now);
                        if (refusal != null) {
                            player.displayClientMessage(GranReyCero.message(refusal, data, now), true);
                        }
                    }
                }
            }
        }
    }

    /** Starts a Cero for a Hollow, an Arrancar or a Visored, telling a refusal on the action bar. */
    private static void startCero(ServerPlayer player, SoulData data, long now) {
        HollowCero.Refusal refusal = HollowCero.start(player, data, now);
        if (refusal != null) {
            player.displayClientMessage(Component.translatable(refusal.messageKey()).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Starts a hold on the target, or on the nearest prey within the cone when there is no living target
     * under the crosshair. A refused target is told as it is, with no other prey tried in its place: the
     * player aimed at it. A hold already on the same prey goes on unchanged; one on another prey ends first.
     *
     * @return the refusal, or null when the hold began (or was already on this prey)
     */
    @Nullable
    public static DevourLogic.Refusal startDevour(Player player, SoulData data, @Nullable Entity target, long now) {
        LivingEntity prey;
        if (target instanceof LivingEntity aimed) {
            if (data.devourTargetId >= 0 && aimed.getId() == data.devourTargetId) {
                return null;
            }
            DevourLogic.Refusal refusal = DevourLogic.refusal(player, data, aimed, now);
            if (refusal != null) {
                return refusal;
            }
            prey = aimed;
        } else {
            // The devourer's own refusals (not a Hollow, the cooldown) come before there being no target.
            DevourLogic.Refusal early = DevourLogic.refusal(player, data, null, now);
            if (early != DevourLogic.Refusal.NO_TARGET) {
                return early;
            }
            prey = DevourLogic.nearestPrey(player, data, now);
            if (prey == null) {
                return DevourLogic.Refusal.NO_TARGET;
            }
        }
        if (prey.getId() == data.devourTargetId) {
            return null;
        }
        if (data.devourTargetId >= 0) {
            stopDevour(player, data, null, now);
        }
        // Jaws full of prey cannot gather a Cero: it fizzles, at no cost.
        if (HollowCero.charging(data)) {
            HollowCero.cancel(player, data);
        }
        data.devourTargetId = prey.getId();
        data.devourHeldTicks = 0;
        data.devourPlus = prey instanceof PlusSpirit;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(DevourLogic.DEVOURING_ID,
                    DevourLogic.DEVOURING_SLOWDOWN, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        seize(player, prey, now, true);
        if (prey instanceof ServerPlayer victim) {
            victim.displayClientMessage(Component.translatable("message.bleach.devour.seized", player.getDisplayName())
                    .withStyle(ChatFormatting.RED), true);
        }
        return null;
    }

    /**
     * Ends a hold: the prey is let go, the devourer's slowness comes off and the cooldown starts. A
     * non-null key is told to the devourer as the reason; letting go of the key says nothing.
     */
    public static void stopDevour(Player player, SoulData data, @Nullable String messageKey, long now) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(DevourLogic.DEVOURING_ID);
        }
        if (data.devourTargetId < 0) {
            return;
        }
        Entity prey = player.level().getEntity(data.devourTargetId);
        if (prey instanceof Hollow hollow) {
            hollow.release();
        } else if (prey instanceof PlusSpirit plus) {
            plus.release();
        }
        data.devourTargetId = -1;
        data.devourHeldTicks = 0;
        data.devourPlus = false;
        data.devourReadyAt = now + BleachConfig.get(BleachConfig.DEVOUR_COOLDOWN_TICKS);
        if (messageKey != null && player instanceof ServerPlayer server) {
            server.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Swallows the prey: the meal is measured, then the devour blow lands, and only a prey that dies of
     * it (no totem, no other mod saving it) feeds the devourer. Nothing drops from a devoured creature
     * but a player (see {@link #onDrops}).
     *
     * @return whether the prey died
     */
    public static boolean complete(Player devourer, LivingEntity prey, long now) {
        if (DevourLogic.preyOf(prey) == null || !prey.isAlive()) {
            return false;
        }
        DevourLogic.Meal meal = DevourLogic.meal(devourer, prey);
        Component name = prey.getDisplayName();
        prey.hurt(DevourLogic.source(devourer.level(), devourer), DevourLogic.killingBlow(prey));
        if (!prey.isDeadOrDying()) {
            return false;
        }
        SoulData data = SoulLogic.get(devourer);
        reward(devourer, data, meal, now);
        SpiritSenses.play(prey, ModSounds.HOLLOW_DEVOUR.get(), SoundSource.PLAYERS, 1.0F,
                0.8F + devourer.getRandom().nextFloat() * 0.2F, false);
        if (devourer.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, ParticleTypes.SQUID_INK, prey.getX(), prey.getY(0.5), prey.getZ(),
                    12, 0.3, 0.4, 0.3, 0.02);
        }
        if (devourer instanceof ServerPlayer server) {
            server.displayClientMessage(Component.translatable("message.bleach.devour.done", name,
                    (int) Math.floor(HollowBody.satiety(data))).withStyle(ChatFormatting.GOLD), true);
        }
        return true;
    }

    /**
     * What a meal gives its devourer: soul hunger, the count of what it has devoured, reiryoku, health,
     * growth and souls, a Hollow's experience, and its road as a Menos (see {@link MenosEvolution#afterMeal}).
     * A player feeds growth, the base souls and the road as a Menos only once in a while, as a player kill
     * does, but a Hollow player's lost souls always pass to its devourer.
     */
    public static void reward(Player devourer, SoulData data, DevourLogic.Meal meal, long now) {
        HollowBody.addSatiety(data, meal.satiety());
        HollowProgress hollow = data.hollow();
        HollowKind kind = meal.kind();
        hollow = switch (meal.prey()) {
            case PLUS -> hollow.withPluses(hollow.pluses() + 1);
            case HOLLOW -> kind == null ? hollow : hollow.withHollows(kind, hollow.hollows(kind) + 1);
            case PLAYER -> hollow.withPlayers(hollow.players() + 1);
        };
        data.setHollow(hollow.withLastMealAt(now));

        float max = data.maxReiryoku();
        double refill = max * BleachConfig.get(BleachConfig.DEVOUR_REIRYOKU_FRACTION);
        data.setReiryoku((float) Math.min(max, data.reiryoku() + refill));
        devourer.heal((float) (devourer.getMaxHealth() * BleachConfig.get(BleachConfig.DEVOUR_HEAL_FRACTION)));

        // Growth after the refill, so the refill is measured on the maximum the devourer had.
        int gained = meal.stolenSouls();
        boolean playerPrey = meal.prey() == DevourLogic.Prey.PLAYER;
        HuntRecord hunt = data.hunt();
        // The same soul again, soon after, comes back as it was: it feeds nothing that grows.
        boolean fresh = !playerPrey || HollowLogic.playerKillGrows(hunt, now);
        if (fresh) {
            if (playerPrey) {
                data.setHunt(hunt.withLastPlayerKillGrowthAt(now));
            }
            SoulLogic.grow(data, HollowLogic.killGrowth(meal.reiatsu(), SoulLogic.reiatsu(devourer), false)
                    * BleachConfig.get(BleachConfig.DEVOUR_GROWTH_MULTIPLIER));
            gained += meal.souls();
        }
        int before = data.hollow().souls();
        int after = before + gained;
        data.setHollow(data.hollow().withSouls(after));
        if (meal.prey() == DevourLogic.Prey.HOLLOW && kind != null) {
            devourer.giveExperiencePoints(kind.xp);
        }
        // Last, so a soul that rises is measured on everything this meal gave it. A stale player meal counts
        // for no form and holds off no regression, or two Hollows could raise each other by turns.
        MenosEvolution.afterMeal(devourer, data, fresh ? meal : new DevourLogic.Meal(meal.prey(), null, meal.reiatsu(),
                meal.satiety(), meal.souls(), meal.stolenSouls(), false), now);

        if (devourer instanceof ServerPlayer server) {
            int mark = HollowPlayerLogic.stirring(before, after);
            if (mark > 0) {
                server.sendSystemMessage(Component.translatable("message.bleach.hollow_player.stir", mark)
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            }
            Milestones.grant(server, Milestones.DEVOURED);
            if (meal.hollowLike()) {
                Milestones.grant(server, Milestones.CANNIBAL);
            }
            Milestones.check(server, data);
        }
    }

    /**
     * A Plus starts gnawing at its own chain, if it may (see {@link DevourLogic#gnawRefusal}). Told once
     * a press; a press while already gnawing changes nothing.
     *
     * @return {@link DevourLogic.Gnaw#GNAWING}, or why not
     */
    public static DevourLogic.Gnaw startGnaw(Player player, SoulData data, long now) {
        DevourLogic.Gnaw gnaw = DevourLogic.gnawRefusal(player, data);
        if (gnaw == DevourLogic.Gnaw.GNAWING && !DevourLogic.gnawing(data)) {
            data.gnawStartedAt = now;
            if (player instanceof ServerPlayer server) {
                server.displayClientMessage(Component.translatable("message.bleach.gnaw.start")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
        }
        return gnaw;
    }

    /** The Plus stops gnawing: the chain wears away at its usual pace again. */
    public static void stopGnaw(SoulData data) {
        data.gnawStartedAt = NEVER;
    }

    // ---- Events ----

    /** Keeps a hold and the gnawing going, on the server. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = level.getGameTime();
        if (data.devourTargetId >= 0) {
            tickHold(player, data, level, now);
        }
        if (DevourLogic.gnawing(data)) {
            tickGnaw(player, data, level);
        }
    }

    /**
     * A devourer that is hurt loses its grip. The pangs of its own hunger do not count: a starving
     * Hollow must still be able to feed. The prey being hurt does not break the hold.
     */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || event.getNewDamage() <= 0.0F || event.getSource().is(HollowBody.SOUL_HUNGER)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (data.devourTargetId >= 0) {
            stopDevour(player, data, BROKEN, player.level().getGameTime());
        }
    }

    /** Nothing is left of a devoured creature to drop; a devoured player's belongings fall as at any death. */
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player) && DevourLogic.isDevoured(event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** Leaving lets go of the prey and the chain. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        SoulData data = SoulLogic.get(player);
        stopDevour(player, data, null, player.level().getGameTime());
        stopGnaw(data);
    }

    // ---- The hold ----

    /**
     * One tick of a hold: it breaks if the devourer or the prey can no longer go on (the health the prey
     * had is not asked again); otherwise the prey stays seized, and once the key has been held long enough
     * (counted only while the game runs) the prey is devoured.
     */
    private static void tickHold(Player player, SoulData data, ServerLevel level, long now) {
        Entity found = level.getEntity(data.devourTargetId);
        if (!(found instanceof LivingEntity prey) || holdBroken(player, data, prey)) {
            stopDevour(player, data, BROKEN, now);
            return;
        }
        seize(player, prey, now, false);
        if (player.tickCount % DevourLogic.PARTICLE_INTERVAL_TICKS == 0) {
            SpiritSenses.particles(level, ParticleTypes.DAMAGE_INDICATOR, prey.getX(), prey.getY(0.5), prey.getZ(),
                    2, 0.2, 0.2, 0.2, 0.0);
        }
        if (level.tickRateManager().runsNormally() && ++data.devourHeldTicks >= DevourLogic.holdTicks(prey)) {
            stopDevour(player, data, null, now);
            complete(player, prey, now);
        }
    }

    /**
     * Whether a hold can no longer go on: the devourer no Hollow, dead, a spectator or at a container;
     * the prey dead, gone, elsewhere or now beyond its jaws; or the prey out of reach or sight. A
     * devourer in creative mode may go on, as it may start.
     */
    private static boolean holdBroken(Player player, SoulData data, LivingEntity prey) {
        return !HollowPlayerLogic.isHollow(data) || !player.isAlive() || player.isSpectator()
                || player.containerMenu != player.inventoryMenu
                || !prey.isAlive() || prey.isRemoved() || prey.level() != player.level()
                || DevourLogic.protectedPrey(player, prey)
                || !player.canInteractWithEntity(prey, DevourLogic.REACH_GRACE) || !player.hasLineOfSight(prey);
    }

    /**
     * Holds the prey for a moment more: a Plus or a Hollow cannot move or act until the seize lapses; a
     * player is slowed, renewed every so often, and can still fight back.
     */
    private static void seize(Player devourer, LivingEntity prey, long now, boolean first) {
        if (prey instanceof Hollow hollow) {
            hollow.seize(now + DevourLogic.SEIZE_TICKS);
        } else if (prey instanceof PlusSpirit plus) {
            plus.seize(now + DevourLogic.SEIZE_TICKS);
        } else if (prey instanceof Player && (first || devourer.tickCount % SEIZED_SLOW_INTERVAL_TICKS == 0)) {
            prey.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SEIZED_SLOW_TICKS, SLOWNESS_II,
                    false, false, true), devourer);
        }
    }

    // ---- The gnawing and the sight ----

    /**
     * One tick of gnawing: it stops once the Plus may no longer gnaw (no longer a Plus, its chain gone, its
     * game mode changed), dies or opens a container. Every so often, while the game runs, the bite blinds
     * it for a moment and, while it has health to spare, hurts.
     */
    private static void tickGnaw(Player player, SoulData data, ServerLevel level) {
        if (DevourLogic.gnawRefusal(player, data) != DevourLogic.Gnaw.GNAWING || !player.isAlive()
                || player.containerMenu != player.inventoryMenu) {
            stopGnaw(data);
            return;
        }
        if (!level.tickRateManager().runsNormally() || ++data.gnawTicks % DevourLogic.GNAW_PAIN_INTERVAL_TICKS != 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, GNAW_BLINDNESS_TICKS, 0, false, false, true));
        SpiritSenses.play(player, ModSounds.HOLLOW_DEVOUR.get(), SoundSource.PLAYERS, 0.4F, 1.4F, false);
        if (player.getHealth() > GNAW_PAIN_ABOVE_HEALTH) {
            player.hurt(ChainEvents.encroachment(level), GNAW_PAIN);
        }
    }

    /** Sneaking with the key: the Hollow sight on or off, at once. */
    private static void toggleSight(ServerPlayer player, SoulData data) {
        boolean sight = !data.hollow().sight();
        data.setHollow(data.hollow().withSight(sight));
        player.displayClientMessage(Component.translatable(sight
                ? "message.bleach.hollow_player.sight.on"
                : "message.bleach.hollow_player.sight.off").withStyle(ChatFormatting.GRAY), true);
        HollowBody.reconcile(player, data);
    }
}
