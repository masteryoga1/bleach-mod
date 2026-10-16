package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-side hooks for the Shinigami path, and the things Shinigami do for others: enrolment,
 * the exams, Konsō, the Asauchi, and lending a Human their power. The rules themselves are in
 * {@link ShinigamiLogic}. What can be done without a connection takes a {@link Player}, so game
 * tests can use mock players.
 *
 * <p>Between players, the giver sneaks and uses a Zanpakutō on the other player to offer Konsō (to
 * a Plus) or their power (to a Human, used twice to confirm), and the other accepts by using an
 * empty hand on them within {@link #OFFER_TICKS} ticks.
 */
public final class ShinigamiEvents {
    private static final int KONSO_RING_POINTS = 16;
    private static final double KONSO_RING_RADIUS = 0.8;
    /** How long an offer stands: 30 seconds. */
    public static final int OFFER_TICKS = 600;
    /** How often one soul may make an offer: every 5 seconds. */
    private static final int OFFER_INTERVAL_TICKS = 100;
    /**
     * The second use of a blade on a Human that confirms lending them power must come at least this
     * long after the use before it, and at most {@link #POWER_CONFIRM_TICKS}. Holding the button down
     * repeats the use every 4 ticks, and each repeat restarts the wait, so it never confirms.
     */
    private static final int POWER_CONFIRM_MIN_TICKS = 10;
    private static final int POWER_CONFIRM_TICKS = 100;
    /** How close the two must stand for an offer to be accepted, squared: 6 blocks. */
    private static final double ACCEPT_RANGE_SQR = 36.0;
    private static final int ENCOUNTER_INTERVAL_TICKS = 1200;
    /** No wounded Shinigami appears while another is this close. */
    private static final double ENCOUNTER_EXCLUSION_RANGE = 128.0;
    private static final int ENCOUNTER_TRIES = 12;
    private static final double ENCOUNTER_MIN_DISTANCE = 8.0;
    private static final double ENCOUNTER_MAX_DISTANCE = 16.0;

    private ShinigamiEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ShinigamiEvents::onRespawn);
        bus.addListener(ShinigamiEvents::onEntityInteract);
        bus.addListener(ShinigamiEvents::onPlayerTickPre);
        bus.addListener(ShinigamiEvents::onPlayerTick);
        bus.addListener(ShinigamiEvents::onChangedDimension);
        bus.addListener(ShinigamiEvents::onLoggedIn);
        bus.addListener(ShinigamiEvents::onServerStarted);
    }

    /** A new world builds its guaranteed Academy before anyone can build where it goes. Not for game tests. */
    private static void onServerStarted(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) {
            AcademyLocator.prepareNewWorld(event.getServer());
        }
    }

    /**
     * A Plus who never learned where the Academy is (one from before there was an Academy) senses it
     * now, and a soul who left while standing on reishi is standing on it again.
     */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (data.state() == SoulState.PLUS && data.shinigami().academyPos().isEmpty()) {
            AcademyLocator.sense(player);
        }
        ReishiFooting.resumeAfterLogin(player);
    }

    /** Reishi footing does not follow a soul into another world. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ReishiFooting.end(player);
        }
    }

    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && SoulLogic.hasSoul(player)) {
            ReishiFooting.tickBeforeMove(player, SoulLogic.get(player));
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        ReishiFooting.tick(player, data);
        if (player.tickCount % 20 == 0) {
            if (ShinigamiLogic.endLoanIfDue(data, now)) {
                SoulLogic.applyStatModifiers(player, data);
                player.sendSystemMessage(Component.translatable("message.bleach.power.returned").withStyle(ChatFormatting.AQUA));
            }
            expireOffer(player, data, now);
        }
        if (player.tickCount % ENCOUNTER_INTERVAL_TICKS == 0 && player.isAlive()) {
            tryEncounter(player, data, now);
        }
    }

    /** A Substitute who died is told what their power made of them. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (data.becameShinigami) {
            data.becameShinigami = false;
            player.sendSystemMessage(Component.translatable("message.bleach.became_shinigami")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    // ---- The Academy ----

    /** A Rukongai soul enrolls at the Academy and is handed an Asauchi. */
    public static boolean enroll(Player player) {
        SoulData data = SoulLogic.get(player);
        if (!ShinigamiLogic.canEnroll(data)) {
            return false;
        }
        ShinigamiLogic.enroll(data);
        giveAsauchi(player, data);
        playSound(player, SoundEvents.BOOK_PAGE_TURN);
        player.sendSystemMessage(Component.translatable("message.bleach.enrolled"));
        if (player instanceof ServerPlayer serverPlayer) {
            Milestones.check(serverPlayer, data);
        }
        return true;
    }

    /** A student sits the exams: on a pass they become a Shinigami and receive the Shihakushō. */
    public static boolean graduate(Player player) {
        SoulData data = SoulLogic.get(player);
        if (!ShinigamiLogic.isStudent(data)) {
            return false;
        }
        List<Stat> missing = ShinigamiLogic.graduationShortfall(data);
        if (!missing.isEmpty()) {
            Component arts = ComponentUtils.formatList(missing.stream().map(Stat::displayName).toList(),
                    Component.literal(", "));
            player.displayClientMessage(Component.translatable("message.bleach.graduation_failed", arts), true);
            return false;
        }
        ShinigamiLogic.graduate(data);
        SoulLogic.applyStatModifiers(player, data);
        give(player, new ItemStack(ModItems.SHIHAKUSHO_KOSODE.get()));
        give(player, new ItemStack(ModItems.SHIHAKUSHO_HAKAMA.get()));
        give(player, new ItemStack(ModItems.WARAJI.get()));
        playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        player.sendSystemMessage(Component.translatable("message.bleach.graduated").withStyle(ChatFormatting.GOLD));
        if (player instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.GRADUATED);
        }
        return true;
    }

    /** Whether the Academy would hand this soul a new Asauchi: the wait is over and they carry no blade. */
    public static boolean mayReceiveAsauchi(Player player, SoulData data, long now) {
        return ShinigamiLogic.canReissueAsauchi(data, now) && !player.getInventory().contains(BleachTags.ZANPAKUTO);
    }

    /** The instructor replaces a lost Asauchi, at most once every ten minutes. */
    public static boolean reissueAsauchi(Player player) {
        SoulData data = SoulLogic.get(player);
        if (player.getInventory().contains(BleachTags.ZANPAKUTO)) {
            player.displayClientMessage(Component.translatable("message.bleach.asauchi_already"), true);
            return false;
        }
        if (!ShinigamiLogic.canReissueAsauchi(data, player.level().getGameTime())) {
            player.displayClientMessage(Component.translatable("message.bleach.asauchi_not_yet"), true);
            return false;
        }
        giveAsauchi(player, data);
        playSound(player, SoundEvents.ARMOR_EQUIP_IRON.value());
        player.displayClientMessage(Component.translatable("message.bleach.asauchi_reissued"), true);
        return true;
    }

    /** Hands over a new Asauchi and remembers when. */
    public static void giveAsauchi(Player player, SoulData data) {
        give(player, new ItemStack(ModItems.ASAUCHI.get()));
        data.setShinigami(data.shinigami().withAsauchiIssuedAt(player.level().getGameTime()));
    }

    /** Puts an item in the player's inventory, or at their feet if it is full. */
    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    // ---- Konsō ----

    /**
     * Konsō: the pommel touches the Plus's forehead, its chain falls away and its soul passes on. A
     * player who performs it grows a little stronger, up to a daily limit.
     *
     * @param performer who sends the Plus on: a player, a Shinigami NPC, or null
     * @param sendOn    whether the soul is sent on to the Academy
     * @return false, changing nothing, if the soul was not a Plus
     */
    public static boolean konso(@Nullable LivingEntity performer, Player plus, boolean sendOn) {
        SoulData data = SoulLogic.get(plus);
        if (!ShinigamiLogic.konso(data)) {
            return false;
        }
        konsoEffects(plus);
        plus.sendSystemMessage(Component.translatable("message.bleach.konso.received")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (plus instanceof ServerPlayer soul) {
            // Granted now, as the soul may enroll and graduate before the next periodic check.
            Milestones.check(soul, data);
        }
        if (sendOn && BleachConfig.get(BleachConfig.KONSO_SENDS_TO_ACADEMY) && plus instanceof ServerPlayer soul) {
            AcademyLocator.sendTo(soul);
        }
        if (performer instanceof Player player && SoulLogic.hasSoul(player)) {
            ShinigamiLogic.rewardKonso(SoulLogic.get(player), player.level().getGameTime());
            player.displayClientMessage(Component.translatable("message.bleach.konso.performed", plus.getDisplayName()), true);
            if (player instanceof ServerPlayer serverPlayer) {
                Milestones.grant(serverPlayer, Milestones.KONSO);
            }
        }
        return true;
    }

    /** A seal ring at the feet, soul wisps sinking into it, and one dark puff where the chain was. */
    private static void konsoEffects(Player plus) {
        if (!(plus.level() instanceof ServerLevel level)) {
            return;
        }
        double x = plus.getX();
        double y = plus.getY() + 0.05;
        double z = plus.getZ();
        for (int i = 0; i < KONSO_RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / KONSO_RING_POINTS;
            level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(angle) * KONSO_RING_RADIUS, y,
                    z + Math.sin(angle) * KONSO_RING_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.SOUL, x, plus.getY() + 1.0, z, 6, 0.25, 0.4, 0.25, 0.01);
        level.sendParticles(ParticleTypes.SQUID_INK, x, plus.getY() + 1.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ---- Offers between players ----

    /**
     * Sneaking with a Zanpakutō offers the other player Konsō or power; an empty hand accepts what
     * they offered. The client only swings the arm: the server decides.
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player user = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Player target)
                || user.isSpectator() || target.isSpectator()) {
            return;
        }
        ItemStack held = user.getMainHandItem();
        if (user.isShiftKeyDown() && held.is(BleachTags.ZANPAKUTO)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            if (!user.level().isClientSide) {
                offerWithBlade(user, target);
            }
        } else if (held.isEmpty() && !user.level().isClientSide && accept(user, target)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /** What a blade held out to another player offers them, if anything. */
    private static void offerWithBlade(Player giver, Player target) {
        if (!SoulLogic.hasSoul(giver) || !SoulLogic.hasSoul(target)) {
            return;
        }
        SoulData giverData = SoulLogic.get(giver);
        SoulData targetData = SoulLogic.get(target);
        if (!SoulLogic.isAware(giverData)) {
            return;
        }
        long now = giver.level().getGameTime();
        if (targetData.state() == SoulState.PLUS) {
            if (ShinigamiLogic.hasShinigamiPower(giverData) && ShinigamiLogic.isPowerLent(giverData, now)) {
                giver.displayClientMessage(Component.translatable("message.bleach.power_lent").withStyle(ChatFormatting.GRAY), true);
            } else if (!ShinigamiLogic.canPerformKonso(giverData, now)) {
                giver.displayClientMessage(Component.translatable("message.bleach.konso.untrained").withStyle(ChatFormatting.GRAY), true);
            } else {
                offer(giver, target, SoulOffer.Kind.KONSO);
            }
        } else if (targetData.state() == SoulState.HUMAN && giverData.state() == SoulState.SHINIGAMI) {
            offerPower(giver, giverData, target, targetData, now);
        }
    }

    /** The first use of the blade on a Human asks the Shinigami to be sure; a second one makes the offer. */
    private static void offerPower(Player giver, SoulData giverData, Player human, SoulData humanData, long now) {
        if (ShinigamiLogic.isPowerLent(giverData, now)) {
            giver.displayClientMessage(Component.translatable("message.bleach.power_lent").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!ShinigamiLogic.canGivePower(giverData, now)) {
            giver.displayClientMessage(Component.translatable("message.bleach.power.too_weak").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!ShinigamiLogic.canReceivePower(humanData)) {
            giver.displayClientMessage(Component.translatable("message.bleach.power.cannot_receive", human.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long sinceLastUse = now - giverData.powerConfirmAt;
        if (human.getUUID().equals(giverData.powerConfirmTarget) && sinceLastUse <= POWER_CONFIRM_TICKS) {
            if (sinceLastUse >= POWER_CONFIRM_MIN_TICKS) {
                giverData.powerConfirmTarget = null;
                offer(giver, human, SoulOffer.Kind.POWER);
            } else {
                giverData.powerConfirmAt = now;
            }
            return;
        }
        giverData.powerConfirmTarget = human.getUUID();
        giverData.powerConfirmAt = now;
        giver.displayClientMessage(Component.translatable("message.bleach.power.confirm", human.getDisplayName())
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * Offers the target Konsō or the giver's power, replacing whatever they were offered before. They
     * have {@link #OFFER_TICKS} ticks to accept. Does nothing if the giver made an offer just now.
     *
     * @return whether the offer was made
     */
    public static boolean offer(Player giver, Player target, SoulOffer.Kind kind) {
        SoulData giverData = SoulLogic.get(giver);
        long now = giver.level().getGameTime();
        if (now - giverData.lastOfferAt < OFFER_INTERVAL_TICKS) {
            return false;
        }
        giverData.lastOfferAt = now;
        SoulLogic.get(target).pendingOffer = new SoulOffer(kind, giver.getUUID(), now + OFFER_TICKS);
        Component message = kind == SoulOffer.Kind.KONSO
                ? konsoOffer(giver, target)
                : Component.translatable("message.bleach.offer.power", giver.getDisplayName());
        target.sendSystemMessage(message);
        target.displayClientMessage(Component.translatable("message.bleach.offer.hint").withStyle(ChatFormatting.GRAY), true);
        giver.displayClientMessage(Component.translatable(kind == SoulOffer.Kind.KONSO
                ? "message.bleach.offer.sent" : "message.bleach.offer.sent.power", target.getDisplayName()), true);
        return true;
    }

    /** Tells a Plus what Konsō would do: free them, and send them to the Academy if there is one. */
    private static Component konsoOffer(Player giver, Player plus) {
        BlockPos academy = BleachConfig.get(BleachConfig.KONSO_SENDS_TO_ACADEMY) && plus instanceof ServerPlayer soul
                ? AcademyLocator.academyFor(soul) : null;
        if (academy == null) {
            return Component.translatable("message.bleach.offer.konso.nowhere", giver.getDisplayName());
        }
        if (plus.level().dimension() != Level.OVERWORLD) {
            return Component.translatable("message.bleach.offer.konso.far", giver.getDisplayName());
        }
        BlockPos from = plus.blockPosition();
        return Component.translatable("message.bleach.offer.konso", giver.getDisplayName(),
                AcademyLocator.direction(from, academy), AcademyLocator.distance(from, academy));
    }

    /** What the soul was offered, if it is still open. */
    @Nullable
    public static SoulOffer pendingOffer(Player player) {
        SoulOffer offer = SoulLogic.get(player).pendingOffer;
        return offer != null && player.level().getGameTime() <= offer.expiresAt() ? offer : null;
    }

    /**
     * Accepts what {@code offerer} offered this soul, if the offer still stands. The offer is used up
     * either way; nothing more happens if the two are no longer close, or either can no longer go
     * through with it.
     *
     * @return whether {@code offerer} had made this soul an offer
     */
    public static boolean accept(Player accepter, Player offerer) {
        SoulData data = SoulLogic.get(accepter);
        SoulOffer offer = data.pendingOffer;
        long now = accepter.level().getGameTime();
        if (offer == null || !offer.isFrom(offerer.getUUID(), now)) {
            return false;
        }
        data.pendingOffer = null;
        if (!accepter.isAlive() || !offerer.isAlive() || accepter.level() != offerer.level()
                || accepter.distanceToSqr(offerer) > ACCEPT_RANGE_SQR || !SoulLogic.hasSoul(offerer)) {
            return true;
        }
        SoulData offererData = SoulLogic.get(offerer);
        switch (offer.kind()) {
            case KONSO -> {
                if (ShinigamiLogic.canPerformKonso(offererData, now)) {
                    konso(offerer, accepter, true);
                }
            }
            case POWER -> {
                if (ShinigamiLogic.canGivePower(offererData, now) && ShinigamiLogic.canReceivePower(data)) {
                    transfer(offerer, accepter);
                }
            }
        }
        return true;
    }

    /** An offer nobody took lapses, and the one who made it hears so. */
    private static void expireOffer(ServerPlayer player, SoulData data, long now) {
        SoulOffer offer = data.pendingOffer;
        if (offer == null || now <= offer.expiresAt()) {
            return;
        }
        data.pendingOffer = null;
        ServerPlayer giver = player.server.getPlayerList().getPlayer(offer.from());
        if (giver != null) {
            giver.displayClientMessage(Component.translatable("message.bleach.offer.expired", player.getDisplayName())
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ---- The Substitute route ----

    /**
     * A Shinigami lends a Human their power. The Human's own power awakens and a blade forms in their
     * hands; the giver is left nearly powerless until the loan ends (see {@link ShinigamiLogic#lend}).
     */
    public static void transfer(Player giver, Player human) {
        SoulData giverData = SoulLogic.get(giver);
        SoulData humanData = SoulLogic.get(human);
        ShinigamiLogic.transferFromShinigami(giverData, humanData, giver.level().getGameTime());
        if (giverData.isReiatsuReleased()) {
            giverData.setReiatsuReleased(false);
            giverData.lastReleaseChangeTick = giver.tickCount;
        }
        SoulLogic.exhaust(giver);
        SoulLogic.applyStatModifiers(giver, giverData);
        SoulLogic.applyStatModifiers(human, humanData);
        giveAsauchi(human, humanData);
        powerEffects(human);
        giver.sendSystemMessage(Component.translatable("message.bleach.power.given", human.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED));
        human.sendSystemMessage(Component.translatable("message.bleach.power.received").withStyle(ChatFormatting.AQUA));
    }

    /** The wounded Shinigami pours her power into a Human, whose own power awakens. */
    public static boolean acceptPowerFrom(WoundedShinigami giver, Player human) {
        SoulData data = SoulLogic.get(human);
        if (giver.gavePower() || !ShinigamiLogic.canReceivePower(data)) {
            return false;
        }
        ShinigamiLogic.awaken(data, BleachConfig.get(BleachConfig.AWAKENING_GROWTH));
        SoulLogic.applyStatModifiers(human, data);
        giveAsauchi(human, data);
        giver.markGavePower();
        powerEffects(human);
        human.sendSystemMessage(Component.translatable("message.bleach.power.received").withStyle(ChatFormatting.AQUA));
        return true;
    }

    /**
     * Once a minute at night, a wounded Shinigami may come down near an aware Human out under the sky
     * who could take her power.
     */
    private static void tryEncounter(ServerPlayer player, SoulData data, long now) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || !level.isNight() || player.isCreative() || player.isSpectator()
                || !ShinigamiLogic.canMeetWoundedShinigami(data, now) || !level.canSeeSky(player.blockPosition())
                || player.getRandom().nextDouble() >= BleachConfig.get(BleachConfig.ENCOUNTER_CHANCE_PER_MINUTE)
                || !level.getEntitiesOfClass(WoundedShinigami.class,
                        player.getBoundingBox().inflate(ENCOUNTER_EXCLUSION_RANGE)).isEmpty()) {
            return;
        }
        EntityType<WoundedShinigami> type = ModEntities.WOUNDED_SHINIGAMI.get();
        Vec3 spot = findEncounterSpot(level, player, type);
        WoundedShinigami npc = spot != null ? type.create(level) : null;
        if (npc == null) {
            return;
        }
        float yaw = (float) (Mth.atan2(player.getZ() - spot.z, player.getX() - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
        npc.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        EventHooks.finalizeMobSpawn(npc, level, level.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.EVENT, null);
        if (!level.addFreshEntity(npc)) {
            return;
        }
        data.setShinigami(data.shinigami().withWoundedMetAt(now));
        player.sendSystemMessage(Component.translatable("message.bleach.wounded_nearby")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Open ground 8 to 16 blocks from the player, in a chunk that is ticking, with room to stand. */
    @Nullable
    private static Vec3 findEncounterSpot(ServerLevel level, Player player, EntityType<?> type) {
        RandomSource random = player.getRandom();
        for (int i = 0; i < ENCOUNTER_TRIES; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double distance = Mth.nextDouble(random, ENCOUNTER_MIN_DISTANCE, ENCOUNTER_MAX_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.isPositionEntityTicking(BlockPos.containing(x, player.getY(), z))) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockPos below = pos.below();
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                continue;
            }
            AABB box = type.getSpawnAABB(x + 0.5, pos.getY(), z + 0.5);
            if (level.noCollision(box) && !level.containsAnyLiquid(box)) {
                return new Vec3(x + 0.5, pos.getY(), z + 0.5);
            }
        }
        return null;
    }

    private static void powerEffects(Player human) {
        if (human.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, human.getX(), human.getY() + 1.0, human.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
        }
        playSound(human, SoundEvents.BEACON_POWER_SELECT);
    }

    private static void playSound(Player player, SoundEvent sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
