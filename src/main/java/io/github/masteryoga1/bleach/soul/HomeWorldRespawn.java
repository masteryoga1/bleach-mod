package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.LasNoches;
import io.github.masteryoga1.bleach.world.Rukongai;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * Where a soul with no spawn of its own wakes when it dies: one that has passed on at its Rukongai home, a student at the
 * Academy, a Shinigami at Soul Society's Senkaimon court, a Menos Grande in Hueco Mundo outside Las Noches, an Arrancar
 * inside it, and anyone else where vanilla puts it. A bed or anchor of its own still wins. Only while the server's
 * {@code [soul_society] home_world_respawn} is on; a soul that has passed on and lingers in the Human World is sent to
 * the same home by the Shinigami on duty (see {@link #sendHome}).
 */
public final class HomeWorldRespawn {
    /** What a soul the Shinigami on duty sends home is told there: its Rukongai home's name, or the Academy's gates. */
    public static final String SENT_HOME_KEY = "message.bleach.duty_sent_home";
    public static final String SENT_HOME_ACADEMY_KEY = "message.bleach.duty_sent_home.academy";

    /** Where a soul wakes, with what it is told there; {@link #VANILLA} leaves the respawn as it is. */
    public enum Home {
        RUKONGAI("message.bleach.respawn.rukongai"),
        ACADEMY("message.bleach.respawn.academy"),
        COURT("message.bleach.respawn.court"),
        HUECO_MUNDO("message.bleach.respawn.hueco_mundo"),
        LAS_NOCHES("message.bleach.respawn.las_noches"),
        VANILLA(null);

        @Nullable
        private final String wokeKey;

        Home(@Nullable String wokeKey) {
            this.wokeKey = wokeKey;
        }
    }

    private HomeWorldRespawn() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(HomeWorldRespawn::onRespawnPosition);
    }

    /**
     * Where this soul wakes, read as it died: where vanilla puts it if it has a spawn of its own or is being torn apart
     * (which wakes it a living Human); a soul that has passed on at its Rukongai home, or at the Academy as a student or
     * where the server's Konsō takes that shortcut; a Shinigami at the court; a Menos Grande that no purifying blow
     * frees in Hueco Mundo, an Arrancar in Las Noches; anyone else, a Substitute, a Plus, a Human and a basic Hollow
     * among them, where vanilla puts it.
     */
    public static Home homeOf(SoulData data, boolean ownSpawn) {
        if (ownSpawn || data.isSoulDestroyed()) {
            return Home.VANILLA;
        }
        return switch (data.state()) {
            case RUKONGAI -> (ShinigamiLogic.isStudent(data)
                    || BleachConfig.get(BleachConfig.KONSO_SENDS_TO) == KonsoTarget.ACADEMY) ? Home.ACADEMY : Home.RUKONGAI;
            case SHINIGAMI -> Home.COURT;
            case HOLLOW -> HollowTier.of(data).isMenos() && !freedByDeath(data) ? Home.HUECO_MUNDO : Home.VANILLA;
            case ARRANCAR -> Home.LAS_NOCHES;
            default -> Home.VANILLA;
        };
    }

    /**
     * A soul's home in Soul Society if it has passed on, the Rukongai or the Academy, read as {@link #homeOf} reads it for
     * a soul with no spawn of its own; null for any other soul.
     */
    @Nullable
    public static Home passedOnHome(SoulData data) {
        Home home = homeOf(data, false);
        return home == Home.RUKONGAI || home == Home.ACADEMY ? home : null;
    }

    /**
     * Sends a soul that has passed on home, alive and as it is, its own spawn kept, to where it would wake with none, and
     * tells it why; one no home there will hold, or whose change of world is refused, stays and is told so.
     *
     * @return whether it was moved
     */
    public static boolean sendHome(ServerPlayer soul) {
        Home home = passedOnHome(SoulLogic.get(soul));
        if (home == null) {
            return false;
        }
        ServerLevel world = BleachWorlds.level(soul.server, BleachWorlds.SOUL_SOCIETY);
        Vec3 spot = world == null ? null
                : home == Home.RUKONGAI ? RukongaiEvents.home(soul, world) : AcademyLocator.landing(soul, world);
        if (spot != null) {
            // Gone in the motes of a passing, riders set down; the command's teleport takes it off its mount and wakes it.
            ShinigamiNpc.showPassage(soul.serverLevel(), soul.getX(), soul.getY(), soul.getZ());
            soul.ejectPassengers();
            soul.teleportTo(world, spot.x, spot.y, spot.z, Set.of(),
                    home == Home.RUKONGAI ? RukongaiEvents.facingSeireitei(spot) : AcademyLocator.LANDING_YAW, 0.0F);
        }
        // Another mod may refuse the change of world.
        if (spot == null || soul.level() != world) {
            soul.sendSystemMessage(Component.translatable("message.bleach.konso.no_academy")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return false;
        }
        soul.resetFallDistance();
        MutableComponent told = home == Home.RUKONGAI
                ? Component.translatable(SENT_HOME_KEY, Rukongai.placeName(Mth.floor(spot.x), Mth.floor(spot.z)))
                : Component.translatable(SENT_HOME_ACADEMY_KEY);
        soul.sendSystemMessage(told.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return true;
    }

    /** Whether the blow that killed this Hollow purifies it, so it comes back a soul (see {@link HollowBody#afterDeath}). */
    private static boolean freedByDeath(SoulData data) {
        return data.hollow().purifiedOnDeath() && BleachConfig.get(BleachConfig.PURIFICATION_FREES);
    }

    /**
     * Read from the soul as it died: death has not changed it yet (see {@link SoulEvents}' Clone, which comes after). Its
     * own spawn is a point set, in a world that still exists, whose bed or anchor is still there. A soul back from the
     * End's fight is left alone, and so is one that nowhere in its home world will hold. A Menos wakes in Hueco Mundo where
     * it died, pushed out past Las Noches as a Garganta sets a soul down; an Arrancar inside Las Noches, on its court's
     * floor, or outside the wall as a Menos where none will hold it.
     */
    private static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight() || !(event.getEntity() instanceof ServerPlayer player) || !SoulLogic.hasSoul(player)
                || !BleachConfig.get(BleachConfig.HOME_WORLD_RESPAWN)) {
            return;
        }
        MinecraftServer server = player.server;
        // Vanilla sends a soul whose point lies in a world that is gone to the Overworld's spawn, and calls it found.
        boolean ownSpawn = player.getRespawnPosition() != null && server.getLevel(player.getRespawnDimension()) != null
                && !event.getOriginalDimensionTransition().missingRespawnBlock();
        Home home = homeOf(SoulLogic.get(player), ownSpawn);
        if (home == Home.VANILLA) {
            return;
        }
        boolean hueco = home == Home.HUECO_MUNDO || home == Home.LAS_NOCHES;
        ServerLevel world = BleachWorlds.level(server, hueco ? BleachWorlds.HUECO_MUNDO : BleachWorlds.SOUL_SOCIETY);
        if (world == null) {
            return;
        }
        Vec3 spot = switch (home) {
            case RUKONGAI -> RukongaiEvents.home(player, world);
            case ACADEMY -> AcademyLocator.landing(player, world);
            case LAS_NOCHES -> LasNoches.wakeSpot(player, world);
            case HUECO_MUNDO -> TravelEvents.huecoMundoLanding(player, world, player.getX(), player.getZ());
            default -> TravelEvents.landing(player, world, TravelLogic.COURT);
        };
        // Rarely, no floor inside will hold it: the court built over, flooded or dug out. It wakes outside, and is told so.
        if (spot == null && home == Home.LAS_NOCHES) {
            home = Home.HUECO_MUNDO;
            spot = TravelEvents.huecoMundoLanding(player, world, player.getX(), player.getZ());
        }
        if (spot == null) {
            return;
        }
        float yaw = switch (home) {
            case RUKONGAI -> RukongaiEvents.facingSeireitei(spot);
            case ACADEMY -> AcademyLocator.LANDING_YAW;
            default -> 0.0F;
        };
        event.setDimensionTransition(new DimensionTransition(world, spot, Vec3.ZERO, yaw, 0.0F,
                DimensionTransition.DO_NOTHING));
        Component woke = home == Home.RUKONGAI
                ? Component.translatable(home.wokeKey, Rukongai.placeName(Mth.floor(spot.x), Mth.floor(spot.z)))
                : Component.translatable(home.wokeKey);
        // Told once it has woken: the player the event names is the one that died, and a new one takes its place.
        UUID id = player.getUUID();
        server.tell(new TickTask(server.getTickCount(), () -> {
            ServerPlayer woken = server.getPlayerList().getPlayer(id);
            if (woken != null) {
                woken.sendSystemMessage(woke.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }));
    }
}
