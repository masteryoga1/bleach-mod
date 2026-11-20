package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DutyShinigami;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Shinigami on duty in the Human World. Once a minute, a Plus in the Overworld whose chain has begun to wear, and
 * who was not visited lately, is visited by one, who comes down nearby to perform its Konsō ({@link DutyShinigami}); so
 * is a soul that has passed on, which it sends home if it stays near. A Plus none can reach is told why. Not on the
 * game-test server, whose tests place their own.
 */
public final class DutyEvents {
    private static final long TICKS_PER_MINUTE = 1200L;
    /** The check falls half a minute off the wounded Shinigami's, so two never come down in one tick. */
    private static final int CHECK_INTERVAL_TICKS = 1200;
    private static final int CHECK_OFFSET_TICKS = 600;
    /** No Shinigami on duty comes while any Shinigami NPC is this close. Public for game tests. */
    public static final double EXCLUSION_RANGE = 128.0;
    /** Open ground 8 to 16 blocks away in 12 tries, as the wounded Shinigami's, at most 16 above or below the Plus. */
    private static final double MIN_DISTANCE = 8.0;
    private static final double MAX_DISTANCE = 16.0;
    private static final int TRIES = 12;
    private static final int MAX_RISE = 16;
    /** A floor 3 to 10 blocks away within 4 of the Plus's height, first under a roof, else last: a cave's, a tower's. */
    private static final double FLOOR_MIN_DISTANCE = 3.0;
    private static final double FLOOR_MAX_DISTANCE = 10.0;
    private static final int FLOOR_RISE = 4;
    private static final String ARRIVES_KEY = "message.bleach.duty_arrives";
    /**
     * What a due Plus is told when none can reach it, when none comes to its world, and when none comes but its chain
     * holds there. Public for game tests.
     */
    public static final String UNREACHABLE_KEY = "message.bleach.duty_unreachable";
    public static final String ELSEWHERE_KEY = "message.bleach.duty_elsewhere";
    public static final String HOLDS_KEY = "message.bleach.duty_holds";
    /** What a soul that has passed on is told when one comes down to send it home. Public for game tests. */
    public static final String HOMEWARD_KEY = "message.bleach.duty_homeward";

    private DutyEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(DutyEvents::onPlayerTick);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % CHECK_INTERVAL_TICKS != CHECK_OFFSET_TICKS || !player.isAlive()
                || !SoulLogic.hasSoul(player) || player.server instanceof GameTestServer
                || !BleachConfig.get(BleachConfig.KONSO_DUTY_SHINIGAMI)) {
            return;
        }
        tryVisit(player, SoulLogic.get(player), player.level().getGameTime());
    }

    /**
     * Whether a Plus is due a visit: its chain has worn {@code duty_after_minutes} of play, or a quarter of its length if
     * that is less, and its last visit was {@code duty_revisit_minutes} ago or more.
     */
    public static boolean due(SoulData data, long lastVisit, long now) {
        if (data.state() != SoulState.PLUS || data.chainMaxTicks() <= 0) {
            return false;
        }
        long after = Math.min(BleachConfig.get(BleachConfig.KONSO_DUTY_AFTER_MINUTES) * TICKS_PER_MINUTE,
                data.chainMaxTicks() / 4);
        long worn = (long) data.chainMaxTicks() - data.chainTicks();
        return worn >= after && now - lastVisit >= revisitTicks();
    }

    /**
     * What a due Plus that no Shinigami on duty came to is told in this world: in the Overworld that none could reach
     * it; in Soul Society, the Shinigami's own world, nothing; in the Dangai that none comes there; and in any other
     * world that its chain holds there. Public for tests.
     */
    @Nullable
    public static String unreachedKey(ResourceKey<Level> world) {
        if (world == Level.OVERWORLD) {
            return UNREACHABLE_KEY;
        }
        if (world == BleachWorlds.SOUL_SOCIETY) {
            return null;
        }
        return SoulLogic.chainWearsIn(world) ? ELSEWHERE_KEY : HOLDS_KEY;
    }

    /**
     * Whether a soul that has passed on is due a visit to send it home: in the Overworld with no bed or anchor of its own
     * there, with a home in Soul Society (see {@link HomeWorldRespawn#passedOnHome}), while the server sends souls home,
     * and its last visit {@code duty_revisit_minutes} ago or more. Public for game tests.
     */
    public static boolean dueHome(SoulData data, ResourceKey<Level> world, boolean spawnHere, long lastVisit, long now) {
        return world == Level.OVERWORLD && !spawnHere && sendsHome() && HomeWorldRespawn.passedOnHome(data) != null
                && now - lastVisit >= revisitTicks();
    }

    /** Whether this soul has a bed or anchor of its own in the Overworld: there it is at home, as death would wake it. */
    public static boolean spawnsInOverworld(ServerPlayer soul) {
        return soul.getRespawnPosition() != null && soul.getRespawnDimension() == Level.OVERWORLD;
    }

    /**
     * Whether a Shinigami on duty sends a soul that has passed on home: while {@code duty_sends_home} is on, souls wake in
     * their home worlds, and Konsō sends souls somewhere, since with {@code sends_to} NOWHERE the server keeps them where
     * they were freed.
     */
    public static boolean sendsHome() {
        return BleachConfig.get(BleachConfig.KONSO_DUTY_SENDS_HOME) && BleachConfig.get(BleachConfig.HOME_WORLD_RESPAWN)
                && BleachConfig.get(BleachConfig.KONSO_SENDS_TO) != KonsoTarget.NOWHERE;
    }

    /**
     * A Shinigami on duty comes down near this soul if it is due one, a Plus for its Konsō or a soul that has passed on to
     * send it home, stands in the Overworld outside creative and spectator mode, and no Shinigami NPC is near; the visit
     * is marked and the soul told which way to look. A due Plus none came to is told why, at most once a revisit's wait,
     * and a soul that has passed on nothing; public for game tests.
     *
     * @return whether one came
     */
    public static boolean tryVisit(ServerPlayer player, SoulData data, long now) {
        long last = player.getData(SoulAttachments.DUTY_VISIT);
        // None comes to send a soul home where there is no Soul Society to send it to.
        boolean homeward = dueHome(data, player.level().dimension(), spawnsInOverworld(player), last, now)
                && BleachWorlds.level(player.server, BleachWorlds.SOUL_SOCIETY) != null;
        if (player.isCreative() || player.isSpectator() || !homeward && !due(data, last, now)
                || !player.serverLevel().getEntitiesOfClass(ShinigamiNpc.class,
                        player.getBoundingBox().inflate(EXCLUSION_RANGE)).isEmpty()) {
            return false;
        }
        ResourceKey<Level> world = player.level().dimension();
        DutyShinigami duty = world == Level.OVERWORLD ? comeDown(player) : null;
        if (duty == null) {
            // A soul that has passed on is told nothing, and looked for again a minute on.
            String key = homeward ? null : unreachedKey(world);
            // A Plus gnawing at its chain wears it even where it would hold.
            if (HOLDS_KEY.equals(key) && DevourLogic.gnawing(data)) {
                key = ELSEWHERE_KEY;
            }
            if (key != null && now - data.dutyToldAt >= revisitTicks()) {
                data.dutyToldAt = now;
                player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            return false;
        }
        duty.showPassage();
        player.setData(SoulAttachments.DUTY_VISIT, now);
        if (homeward) {
            duty.sendHomeLater(player);
        }
        Component way = Component.translatable(ShinigamiLogic.compassKey(duty.getX() - player.getX(),
                duty.getZ() - player.getZ()));
        player.sendSystemMessage(Component.translatable(homeward ? HOMEWARD_KEY : ARRIVES_KEY, way)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return true;
    }

    /**
     * One comes down on open ground near the Plus, or else on a floor near its own height. A Plus under a roof, as in a
     * cave or a mine, is looked for on a floor about it first, so the visitor is not left on the ground above it.
     */
    @Nullable
    private static DutyShinigami comeDown(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        EntityType<DutyShinigami> type = ModEntities.DUTY_SHINIGAMI.get();
        // Water counts in the heightmap, so a Plus in it is not taken to be under a roof.
        boolean roofed = !player.isInWater() && level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(), player.getBlockZ()) > player.getEyeY();
        Vec3 spot = roofed ? floorNear(level, player, type) : null;
        if (spot == null) {
            spot = SpawnSpots.openGround(level, player, type, MIN_DISTANCE, MAX_DISTANCE, TRIES, MAX_RISE);
        }
        if (spot == null && !roofed) {
            spot = floorNear(level, player, type);
        }
        return ShinigamiEvents.comeDownAt(player, type, spot);
    }

    @Nullable
    private static Vec3 floorNear(ServerLevel level, ServerPlayer player, EntityType<DutyShinigami> type) {
        return SpawnSpots.floorNear(level, player, type, FLOOR_MIN_DISTANCE, FLOOR_MAX_DISTANCE, TRIES, FLOOR_RISE);
    }

    /** The wait between two visits to one soul, and between two lines telling a Plus why none came, in ticks. */
    private static long revisitTicks() {
        return BleachConfig.get(BleachConfig.KONSO_DUTY_REVISIT_MINUTES) * TICKS_PER_MINUTE;
    }
}
