package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.item.DenreishinkiItem;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Garganta: tears in the sky that Hollows come through, drawn by the reiatsu of spiritually aware
 * souls. Every half minute each soul may draw one, more often the stronger it is. A soul's first
 * Hollow is always Fishbone D, who comes once it has been aware for a while, at night. The rules
 * are in {@link HollowLogic}; the rift itself, and the Hollows dropping out of it, are
 * {@link GargantaRift}'s.
 */
public final class GargantaEvents {
    private static final double SPOT_MIN_DISTANCE = 12.0;
    private static final double SPOT_MAX_DISTANCE = 24.0;
    private static final int SPOT_TRIES = 12;
    /** Arrivals land at most this far above or below the soul, which keeps them out of caves. */
    private static final int SPOT_MAX_RISE = 12;
    /** How high above its landing spot a rift opens, at most. */
    private static final int RIFT_MAX_HEIGHT = 6;
    private static final float ARRIVAL_SIZE = 1.0F;
    private static final float FIRST_HOLLOW_SIZE = 1.5F;
    /** No wounded Shinigami comes ahead of the first Hollow while another is this close. */
    private static final double WOUNDED_EXCLUSION_RANGE = 128.0;
    /** Aware souls this close see the tear open, for the milestone. */
    private static final double WITNESS_RANGE = 48.0;
    private static final int RING_POINTS = 20;
    private static final int AWARENESS_STAMP_TICKS = 20;

    private GargantaEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(GargantaEvents::onPlayerTickPre);
        bus.addListener(GargantaEvents::onLoggedIn);
        bus.addListener(GargantaEvents::onRespawn);
        bus.addListener(GargantaEvents::onChangedDimension);
    }

    /**
     * Before the player moves, so the spot is searched around where they really stand (see
     * {@link SpiritEvents}). Each soul is checked on its own tick of the cycle, spreading the work.
     */
    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (player.tickCount % AWARENESS_STAMP_TICKS == 0) {
            data.setHunt(HollowLogic.noteAwareness(data.hunt(), data, playTicks(player)));
        }
        if ((player.tickCount + player.getId()) % HollowLogic.ARRIVAL_CHECK_TICKS != 0) {
            return;
        }
        long now = player.level().getGameTime();
        if (!mayArriveNear(player, data, now)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int nearby = hollowsNear(level, player.position());
        int cap = BleachConfig.get(BleachConfig.MAX_NEARBY_HOLLOWS);
        if (nearby >= cap) {
            return;
        }
        if (HollowLogic.firstHollowDue(data, playTicks(player), now, level.isNight())) {
            startFirstHollow(player, data, now, false);
            return;
        }
        if (HollowLogic.awaitsFirstHollow(data) || !HollowLogic.arrivalCooledDown(data.hunt(), now)) {
            return;
        }
        if (player.getRandom().nextDouble() < HollowLogic.arrivalChancePerCheck(data, level.isNight())) {
            openArrival(player, data, null, now, false);
        }
    }

    /**
     * Whether a Garganta may open near this soul now: the world allows it (not in a game test), and
     * the soul is alive, in survival or adventure mode, aware and not a Hollow, past its grace after
     * joining, and not under an Academy instructor's protection.
     */
    public static boolean mayArriveNear(ServerPlayer player, SoulData data, long now) {
        ServerLevel level = player.serverLevel();
        if (player.server instanceof GameTestServer
                || !HollowLogic.worldAllowsArrivals(level.getDifficulty(),
                        level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING), level.dimension() == Level.OVERWORLD)) {
            return false;
        }
        if (!player.isAlive() || !player.gameMode.isSurvival() || !SoulLogic.hasSoul(player)
                || !SoulLogic.isAware(data) || data.state() == SoulState.HOLLOW || data.state() == SoulState.ARRANCAR
                || now < data.arrivalGraceUntil) {
            return false;
        }
        double safe = BleachConfig.get(BleachConfig.ACADEMY_SAFE_RADIUS);
        return safe <= 0.0 || level.getEntitiesOfClass(AcademyInstructor.class, player.getBoundingBox().inflate(safe),
                instructor -> instructor.distanceToSqr(player) <= safe * safe).isEmpty();
    }

    /**
     * Opens a Garganta near the soul, with the Hollows it drew: of a kind picked by its reiatsu, or the
     * one given. Stronger souls draw Hound packs and second Hollows, never more than the cap allows.
     * A command ignores the cap and leaves the cooldown alone.
     *
     * @return false, changing nothing, if there was no open ground for the Hollows to land on
     */
    public static boolean openArrival(ServerPlayer player, SoulData data, @Nullable HollowKind forced, long now,
                                      boolean byCommand) {
        ServerLevel level = player.serverLevel();
        float reiatsu = data.maxReiryoku();
        HollowKind kind = forced != null ? forced
                : HollowLogic.pickArrival(reiatsu, plusesNear(level, player), level.getRandom());
        int room = byCommand ? Integer.MAX_VALUE
                : BleachConfig.get(BleachConfig.MAX_NEARBY_HOLLOWS) - hollowsNear(level, player.position());
        int count = HollowLogic.groupSize(kind, reiatsu, room, level.getRandom().nextDouble());
        EntityType<? extends Hollow> type = ModEntities.hollowType(kind);
        // Room for the biggest Hollow of the kind, as each rolls its size only as it drops.
        Vec3 landing = SpawnSpots.openGround(level, player, type, kind.scaleMax, SPOT_MIN_DISTANCE, SPOT_MAX_DISTANCE,
                SPOT_TRIES, SPOT_MAX_RISE);
        if (landing == null) {
            return false;
        }
        Vec3 at = landing.add(0.0, SpawnSpots.clearAbove(level, type, kind.scaleMax, landing, RIFT_MAX_HEIGHT), 0.0);
        float size = kind == HollowKind.FISHBONE_D ? FIRST_HOLLOW_SIZE : ARRIVAL_SIZE;
        GargantaRift rift = GargantaRift.arrival(level, at, now, Collections.nCopies(count, kind), player, size);
        if (!level.addFreshEntity(rift)) {
            return false;
        }
        if (!byCommand) {
            data.setHunt(data.hunt().withLastArrivalAt(now));
        }
        return true;
    }

    /**
     * Fishbone D comes for this soul, its first Hollow. A Human who could take a Shinigami's power may
     * first meet a wounded one who stumbles in fleeing it, half a minute ahead; anyone else hears it
     * coming a few seconds before the sky tears. {@code woundedAlreadyHere} is for a wounded Shinigami
     * who came on her own just now.
     *
     * @return false, changing nothing, if Fishbone D is already hunting the soul or there is no open
     *         ground; also false if the world refused the rift, though a wounded Shinigami may have come
     */
    public static boolean startFirstHollow(ServerPlayer player, SoulData data, long now, boolean woundedAlreadyHere) {
        ServerLevel level = player.serverLevel();
        if (firstHollowOf(player, data) != null) {
            return false;
        }
        EntityType<FishboneD> type = ModEntities.FISHBONE_D.get();
        float scale = HollowKind.FISHBONE_D.scaleMax;
        Vec3 landing = SpawnSpots.openGround(level, player, type, scale, SPOT_MIN_DISTANCE, SPOT_MAX_DISTANCE, SPOT_TRIES,
                SPOT_MAX_RISE);
        if (landing == null) {
            return false;
        }
        String message = null;
        long openAt;
        if (woundedAlreadyHere) {
            openAt = now + HollowLogic.FIRST_HOLLOW_WOUNDED_DELAY;
        } else if (woundedMayCome(level, player, data) && ShinigamiEvents.spawnWoundedFor(player, data, now) != null) {
            message = "message.bleach.first_hollow.wounded";
            openAt = now + HollowLogic.FIRST_HOLLOW_WOUNDED_DELAY;
        } else {
            message = "message.bleach.first_hollow.coming";
            openAt = now + HollowLogic.FIRST_HOLLOW_DELAY;
        }
        Vec3 at = landing.add(0.0, SpawnSpots.clearAbove(level, type, scale, landing, RIFT_MAX_HEIGHT), 0.0);
        GargantaRift rift = GargantaRift.arrival(level, at, openAt, List.of(HollowKind.FISHBONE_D), player,
                FIRST_HOLLOW_SIZE);
        if (!level.addFreshEntity(rift)) {
            return false;
        }
        data.setHunt(data.hunt().withLastArrivalAt(now));
        if (message != null) {
            player.sendSystemMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return true;
    }

    /**
     * The Fishbone D hunting this soul as its first Hollow, if one is alive in the soul's world and not
     * leaving: the one it remembers, or else one within reach that has it as its quarry, since the
     * soul forgets it on respawning or relogging. Remembers the one found, or forgets a stale one.
     */
    @Nullable
    public static FishboneD firstHollowOf(ServerPlayer quarry, SoulData data) {
        ServerLevel level = quarry.serverLevel();
        FishboneD found = null;
        if (data.firstHollowId != null && level.getEntity(data.firstHollowId) instanceof FishboneD fishbone
                && hunts(fishbone, quarry)) {
            found = fishbone;
        } else {
            List<FishboneD> near = level.getEntitiesOfClass(FishboneD.class,
                    quarry.getBoundingBox().inflate(FishboneD.QUARRY_RANGE), fishbone -> hunts(fishbone, quarry));
            found = near.isEmpty() ? null : near.get(0);
        }
        data.firstHollowId = found != null ? found.getUUID() : null;
        return found;
    }

    private static boolean hunts(FishboneD fishbone, ServerPlayer quarry) {
        return fishbone.isAlive() && !fishbone.isDeparting() && quarry.getUUID().equals(fishbone.quarry());
    }

    /** Whether a wounded Shinigami may stumble in ahead of the first Hollow: she must have someone to give her power to. */
    private static boolean woundedMayCome(ServerLevel level, ServerPlayer player, SoulData data) {
        return BleachConfig.get(BleachConfig.FIRST_HOLLOW_WOUNDED_SHINIGAMI) && BleachConfig.get(BleachConfig.WOUNDED_ENCOUNTERS)
                && ShinigamiLogic.canReceivePower(data)
                && level.getEntitiesOfClass(WoundedShinigami.class, player.getBoundingBox().inflate(WOUNDED_EXCLUSION_RANGE))
                        .isEmpty();
    }

    /**
     * Called by a rift as it tears open: a scream across the sky, black ink and a swirl of portal
     * light for those who can see it, the milestone for aware souls nearby, a beep on every
     * Denreishinki in range, and a chill in the blood of Quincy in range (see
     * {@link QuincyEvents#senseHollows}). The first Hollow's rift also carries Fishbone D's roar.
     */
    public static void onRiftOpened(ServerLevel level, GargantaRift rift) {
        double x = rift.getX();
        double z = rift.getZ();
        float size = rift.size();
        double middle = rift.getY() + 2.0 * size;
        SpiritSenses.play(level, x, middle, z, ModSounds.GARGANTA_OPEN.get(), SoundSource.HOSTILE, 1.0F, 1.0F, true);
        if (rift.pending().contains(HollowKind.FISHBONE_D)) {
            SpiritSenses.play(level, x, middle, z, ModSounds.FISHBONE_D_ROAR.get(), SoundSource.HOSTILE, 2.0F, 1.0F, true);
        }
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / RING_POINTS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            // A count of 0 sends one particle moving along (dx, 0, dz), so the ring bursts outwards.
            SpiritSenses.particles(level, ParticleTypes.SQUID_INK, x + dx * size, middle, z + dz * size,
                    0, dx, 0.0, dz, 0.15);
        }
        SpiritSenses.particles(level, ParticleTypes.REVERSE_PORTAL, x, middle, z, 40,
                0.5 * size, 1.5 * size, 0.5 * size, 0.05);
        for (ServerPlayer witness : SpiritSenses.awareNear(level, rift.position(), WITNESS_RANGE)) {
            Milestones.grant(witness, Milestones.GARGANTA);
        }
        pingPagers(level, rift.position(), "message.bleach.denreishinki.arrival");
        QuincyEvents.senseHollows(level, rift.position());
    }

    /** Fishbone D has landed and taken up the hunt: the soul it hunts remembers it, so nothing else comes meanwhile. */
    public static void onFirstHollowLanded(ServerPlayer quarry, FishboneD fishbone) {
        if (SoulLogic.hasSoul(quarry)) {
            SoulLogic.get(quarry).firstHollowId = fishbone.getUUID();
        }
    }

    /**
     * Fishbone D gave up the hunt and tore its way out. Unless it was only leaving after its quarry
     * died, the quarry is told it will be back, and it is, once the retry time has passed.
     */
    public static void onFishboneRetreat(FishboneD fishbone, @Nullable ServerPlayer quarry, boolean announce) {
        if (quarry == null || !SoulLogic.hasSoul(quarry)) {
            return;
        }
        SoulData data = SoulLogic.get(quarry);
        if (fishbone.getUUID().equals(data.firstHollowId)) {
            data.firstHollowId = null;
        }
        if (!announce || data.hunt().firstHollowDone()) {
            return;
        }
        data.setHunt(data.hunt().withLastArrivalAt(fishbone.level().getGameTime()));
        quarry.sendSystemMessage(Component.translatable("message.bleach.first_hollow.retreated")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /**
     * Beeps every Denreishinki within range whose holder can read it, wherever in their inventory it
     * is, with which way and roughly how far the trouble is.
     */
    public static void pingPagers(ServerLevel level, Vec3 at, String messageKey) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || !pagerReaches(player, at, now)) {
                continue;
            }
            double dx = at.x - player.getX();
            double dz = at.z - player.getZ();
            player.playNotifySound(ModSounds.DENREISHINKI_PING.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable(messageKey,
                    Component.translatable(ShinigamiLogic.compassKey(dx, dz)),
                    ShinigamiLogic.roundedDistance(Math.sqrt(dx * dx + dz * dz), 5L)), true);
        }
    }

    /** Whether trouble here beeps this player's Denreishinki: they carry one they can read, and are in range. */
    static boolean pagerReaches(ServerPlayer player, Vec3 at, long now) {
        double range = BleachConfig.get(BleachConfig.DENREISHINKI_RANGE);
        return player.distanceToSqr(at) <= range * range && SoulLogic.hasSoul(player)
                && DenreishinkiItem.canRead(SoulLogic.get(player), now) && DenreishinkiItem.carries(player);
    }

    /** The player's time in the world, in ticks, from the play-time statistic, so it survives relogging. */
    public static int playTicks(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
    }

    private static int hollowsNear(ServerLevel level, Vec3 at) {
        double range = HollowLogic.NEARBY_HOLLOW_RANGE;
        return level.getEntitiesOfClass(Hollow.class, new AABB(at, at).inflate(range),
                hollow -> hollow.isAlive() && hollow.distanceToSqr(at) <= range * range).size();
    }

    private static boolean plusesNear(ServerLevel level, ServerPlayer player) {
        double range = HollowLogic.PLUS_NEARBY_RANGE;
        return !level.getEntitiesOfClass(PlusSpirit.class, player.getBoundingBox().inflate(range),
                plus -> plus.isAlive() && plus.distanceToSqr(player) <= range * range).isEmpty();
    }

    private static void grace(ServerPlayer player) {
        SoulLogic.get(player).arrivalGraceUntil = player.level().getGameTime()
                + BleachConfig.get(BleachConfig.ARRIVAL_GRACE_SECONDS) * 20L;
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            grace(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            grace(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            grace(player);
        }
    }
}
