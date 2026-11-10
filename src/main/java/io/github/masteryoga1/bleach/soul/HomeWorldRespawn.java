package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * A soul that has passed on and holds no Shinigami power cannot open a gate, so when it dies with no spawn of its own it
 * wakes at the gates of the Academy in Soul Society, where Konsō's shortcut sends a soul (see
 * {@link AcademyLocator#sendTo}). A bed or anchor it set after passing on still wins. Only while the server's
 * {@code [soul_society] home_world_respawn} is on.
 */
public final class HomeWorldRespawn {
    private static final String WOKE_KEY = "message.bleach.respawn.academy";

    private HomeWorldRespawn() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(HomeWorldRespawn::onRespawnPosition);
    }

    /**
     * Whether a soul wakes at the Academy: one that has passed on (a Rukongai soul, a student among them), with no
     * Shinigami power and not being torn apart (which wakes it a living Human), and with no spawn of its own.
     */
    public static boolean wakesAtAcademy(SoulData data, boolean ownSpawn) {
        return !ownSpawn && data.state() == SoulState.RUKONGAI && !data.isSoulDestroyed()
                && !ShinigamiLogic.hasShinigamiPower(data);
    }

    /**
     * Read from the soul as it died: death has not changed it yet (see {@link SoulEvents}' Clone, which comes after). Its
     * own spawn is a point set, in a world that still exists, whose bed or anchor is still there. A soul back from the
     * End's fight is left alone.
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
        if (!wakesAtAcademy(SoulLogic.get(player), ownSpawn)) {
            return;
        }
        ServerLevel soulSociety = BleachWorlds.level(server, BleachWorlds.SOUL_SOCIETY);
        Vec3 spot = soulSociety != null ? AcademyLocator.landing(player, soulSociety) : null;
        if (spot == null) {
            return;
        }
        event.setDimensionTransition(new DimensionTransition(soulSociety, spot, Vec3.ZERO, AcademyLocator.LANDING_YAW, 0.0F,
                DimensionTransition.DO_NOTHING));
        // Told once it has woken: the player the event names is the one that died, and a new one takes its place.
        UUID id = player.getUUID();
        server.tell(new TickTask(server.getTickCount(), () -> {
            ServerPlayer woken = server.getPlayerList().getPlayer(id);
            if (woken != null) {
                woken.sendSystemMessage(Component.translatable(WOKE_KEY)
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }));
    }
}
