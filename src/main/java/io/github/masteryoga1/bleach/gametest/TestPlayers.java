package io.github.masteryoga1.bleach.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
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
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Real server players for game tests. Vanilla's mock server player cannot take the mod's payloads, so this joins one
 * the way NeoForge's own test framework does: on an in-memory connection that has every channel.
 */
final class TestPlayers {
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

    /** Takes the player off the server when its test ends; the server drops it on its next tick. */
    private record Leaver(ServerPlayer player) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo info) {
        }

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            player.connection.disconnect(Component.literal("Test finished"));
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            player.connection.disconnect(Component.literal("Test finished"));
        }

        @Override
        public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
        }
    }
}
