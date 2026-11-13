package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.Rukongai;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * Academy, a Shinigami at Soul Society's Senkaimon court, a Menos Grande or an Arrancar in Hueco Mundo, and anyone else
 * where vanilla puts it. A bed or anchor of its own still wins. Only while the server's
 * {@code [soul_society] home_world_respawn} is on.
 */
public final class HomeWorldRespawn {
    /** Where a soul wakes, with what it is told there; {@link #VANILLA} leaves the respawn as it is. */
    public enum Home {
        RUKONGAI("message.bleach.respawn.rukongai"),
        ACADEMY("message.bleach.respawn.academy"),
        COURT("message.bleach.respawn.court"),
        HUECO_MUNDO("message.bleach.respawn.hueco_mundo"),
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
     * frees, or an Arrancar, in Hueco Mundo; anyone else, a Substitute, a Plus, a Human and a basic Hollow among them,
     * where vanilla puts it.
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
            case ARRANCAR -> Home.HUECO_MUNDO;
            default -> Home.VANILLA;
        };
    }

    /** Whether the blow that killed this Hollow purifies it, so it comes back a soul (see {@link HollowBody#afterDeath}). */
    private static boolean freedByDeath(SoulData data) {
        return data.hollow().purifiedOnDeath() && BleachConfig.get(BleachConfig.PURIFICATION_FREES);
    }

    /**
     * Read from the soul as it died: death has not changed it yet (see {@link SoulEvents}' Clone, which comes after). Its
     * own spawn is a point set, in a world that still exists, whose bed or anchor is still there. A soul back from the
     * End's fight is left alone, and so is one that nowhere in its home world will hold. A Menos or an Arrancar wakes in
     * Hueco Mundo where it died, pushed out past Las Noches as a Garganta sets a soul down.
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
        ServerLevel world = BleachWorlds.level(server,
                home == Home.HUECO_MUNDO ? BleachWorlds.HUECO_MUNDO : BleachWorlds.SOUL_SOCIETY);
        if (world == null) {
            return;
        }
        Vec3 spot = switch (home) {
            case RUKONGAI -> RukongaiEvents.home(player, world);
            case ACADEMY -> AcademyLocator.landing(player, world);
            case HUECO_MUNDO -> TravelEvents.huecoMundoLanding(player, world, player.getX(), player.getZ());
            default -> TravelEvents.landing(player, world, TravelLogic.COURT);
        };
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
