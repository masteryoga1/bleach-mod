package io.github.masteryoga1.bleach.gametest;

import com.mojang.authlib.GameProfile;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Real server players for game tests. Vanilla's mock server player cannot take the mod's payloads, so this joins one
 * the way NeoForge's own test framework does: on an in-memory connection that has every channel, answers teleports and
 * keep-alives as a client would, and keeps what its action bar and chat show and the dialogues it is opened (and, for
 * one a test watches, the particles and sounds it is sent) for the test to read.
 */
final class TestPlayers {
    /** What each joined player has been shown on its action bar, for its test to read. */
    private static final Map<UUID, List<Component>> ACTION_BAR = new ConcurrentHashMap<>();
    /** What each joined player has been shown in chat, for its test to read. */
    private static final Map<UUID, List<Component>> CHAT = new ConcurrentHashMap<>();
    /** The dialogues each joined player has been opened, oldest first, for its test to read. */
    private static final Map<UUID, List<OpenDialoguePayload>> DIALOGUES = new ConcurrentHashMap<>();
    /** The particle and sound packets sent to each player a test watches ({@link #watchEffects}), oldest first. */
    private static final Map<UUID, List<Packet<?>>> EFFECTS = new ConcurrentHashMap<>();

    private TestPlayers() {
    }

    /**
     * A player joined to the test server at a spot in the test's box, in the given game mode. It ticks with its
     * connection like a real player, and it leaves the server when the test passes or fails.
     */
    static ServerPlayer join(GameTestHelper helper, GameType mode, Vec3 relative) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-player"), false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public boolean isMemoryConnection() {
                return true;
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
                super.send(packet, listener, flush);
                if (packet instanceof ClientboundKeepAlivePacket alive) {
                    player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(alive.getId()));
                } else if (packet instanceof ClientboundPlayerPositionPacket moved) {
                    ServerboundAcceptTeleportationPacket answer = new ServerboundAcceptTeleportationPacket(moved.getId());
                    // As a client answers: after this tick, so never inside the change of world that sent it.
                    server.tell(new TickTask(server.getTickCount(), () -> {
                        if (!player.hasDisconnected()) {
                            player.connection.handleAcceptTeleportPacket(answer);
                        }
                    }));
                } else if (packet instanceof ClientboundSystemChatPacket chat) {
                    (chat.overlay() ? ACTION_BAR : CHAT).computeIfAbsent(player.getUUID(),
                            id -> new CopyOnWriteArrayList<>()).add(chat.content());
                } else if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof OpenDialoguePayload dialogue) {
                    DIALOGUES.computeIfAbsent(player.getUUID(), id -> new CopyOnWriteArrayList<>()).add(dialogue);
                } else if (packet instanceof ClientboundLevelParticlesPacket || packet instanceof ClientboundSoundPacket) {
                    List<Packet<?>> effects = EFFECTS.get(player.getUUID());
                    if (effects != null) {
                        effects.add(packet);
                    }
                }
            }
        };
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        server.getConnection().getConnections().add(connection);
        helper.testInfo.addListener(new Leaver(player));
        player.gameMode.changeGameModeForPlayer(mode);
        Vec3 at = helper.absoluteVec(relative);
        player.teleportTo(level, at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** The lines this joined player has been shown on its action bar so far, oldest first. */
    static List<Component> actionBar(ServerPlayer player) {
        return ACTION_BAR.getOrDefault(player.getUUID(), List.of());
    }

    /** The lines this joined player has been shown in chat so far, oldest first. */
    static List<Component> chat(ServerPlayer player) {
        return CHAT.getOrDefault(player.getUUID(), List.of());
    }

    /** The dialogues this joined player has been opened so far, oldest first. */
    static List<OpenDialoguePayload> dialogues(ServerPlayer player) {
        return DIALOGUES.getOrDefault(player.getUUID(), List.of());
    }

    /** From now on keeps the particle and sound packets this joined player is sent, for {@link #takeEffects}. */
    static void watchEffects(ServerPlayer player) {
        EFFECTS.put(player.getUUID(), Collections.synchronizedList(new ArrayList<>()));
    }

    /** The particle and sound packets this watched player has been sent since the last take, oldest first. */
    static List<Packet<?>> takeEffects(ServerPlayer player) {
        List<Packet<?>> effects = EFFECTS.getOrDefault(player.getUUID(), new ArrayList<>());
        synchronized (effects) {
            List<Packet<?>> taken = List.copyOf(effects);
            effects.clear();
            return taken;
        }
    }

    /** Takes the player off the server when its test ends; the server drops it on its next tick. */
    private record Leaver(ServerPlayer player) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo info) {
        }

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            leave();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            leave();
        }

        @Override
        public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
        }

        /** Forgets the player's action bar, chat, dialogues and effects and disconnects it, unless it has already gone. */
        private void leave() {
            ACTION_BAR.remove(player.getUUID());
            CHAT.remove(player.getUUID());
            DIALOGUES.remove(player.getUUID());
            EFFECTS.remove(player.getUUID());
            if (!player.hasDisconnected()) {
                player.connection.disconnect(Component.literal("Test finished"));
            }
        }
    }
}
