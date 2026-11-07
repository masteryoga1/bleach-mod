package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The test server's own Soul Society. The test server makes its worlds from the flat preset, and the game-test worlds
 * mixin hands it the datapack's worlds too, so Soul Society is a real world here, and its chunks generate as in play.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SoulSocietyWorldTests {
    private SoulSocietyWorldTests() {
    }

    @GameTest(template = "empty")
    public static void soulSocietyLoadsInTests(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel soul = server.getLevel(BleachWorlds.SOUL_SOCIETY);
        helper.assertTrue(soul != null && soul.dimensionTypeRegistration().is(BleachWorlds.SOUL_SOCIETY_TYPE)
                        && soul.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator
                        && generator.generatorSettings().is(BleachWorlds.SOUL_SOCIETY_NOISE),
                "the test server has no Soul Society made from its files");
        helper.assertTrue(server.overworld().getChunkSource().getGenerator() instanceof FlatLevelSource,
                "the test server's Overworld is no longer flat");
        helper.assertTrue(BleachWorlds.level(server, BleachWorlds.SOUL_SOCIETY) == soul, "travel would not find the world");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soulSocietyGeneratesThePlan(GameTestHelper helper) {
        ServerLevel soul = helper.getLevel().getServer().getLevel(BleachWorlds.SOUL_SOCIETY);
        helper.assertTrue(soul != null, "the test server has no Soul Society");
        int plateau = SeireiteiPlan.PLATEAU_Y, top = SeireiteiPlan.WALL_TOP_Y, g = SeireiteiPlan.GATE_RADIUS;
        int d = (int) (g / Math.sqrt(2.0)), w = SeireiteiPlan.GATE_HALF_WIDTH;
        BlockPos lawn = new BlockPos(300, plateau - 1, 40), wall = new BlockPos(d, top - 1, d);
        BlockPos gap = new BlockPos(0, plateau - 1, g);
        // Each generated in full where it is, wall feature and all: about 25 chunks round each, once.
        List.of(lawn, wall, gap).forEach(soul::getChunk);
        Block sekkiseki = ModBlocks.SEKKISEKI.get();
        helper.assertTrue(soul.getHeight(Heightmap.Types.WORLD_SURFACE, lawn.getX(), lawn.getZ()) == plateau
                && soul.getBlockState(lawn).is(Blocks.GRASS_BLOCK), "Seireitei's lawn is not grass under open air at y 71");
        helper.assertTrue(soul.getBlockState(wall).is(sekkiseki) && soul.getBlockState(wall.above()).isAir()
                        && soul.getBlockState(wall.atY(1)).is(sekkiseki)
                        && soul.getBlockState(wall.atY(0)).is(Blocks.BEDROCK),
                "the Seireiheki at (" + d + ", " + d + ") is not Sekkiseki from y 1 to " + (top - 1) + " on bedrock");
        helper.assertTrue(soul.getBlockState(gap).is(Blocks.GRASS_BLOCK) && soul.getBlockState(gap.above()).isAir()
                        && soul.getBlockState(new BlockPos(w, 90, g)).is(sekkiseki)
                        && soul.getBlockState(new BlockPos(w - 1, 90, g)).isAir(),
                "the south gate's gap is not open lawn between walls");
        helper.succeed();
    }

    /**
     * A joined player takes the mod's login payloads, crosses into Soul Society as a gate will send it, stays there
     * while it ticks, and leaves the server when disconnected.
     */
    @GameTest(template = "empty")
    public static void aJoinedPlayerCrossesToSoulSociety(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel soul = server.getLevel(BleachWorlds.SOUL_SOCIETY);
        helper.assertTrue(soul != null, "the test server has no Soul Society");
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        helper.assertTrue(server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.level() == helper.getLevel(), "the player did not join in the test's world");
        Vec3 court = new Vec3(192.5, SeireiteiPlan.PLATEAU_Y, -287.5);
        soul.getChunk(BlockPos.containing(court));
        player.changeDimension(new DimensionTransition(soul, court, Vec3.ZERO, 0.0F, 0.0F,
                DimensionTransition.DO_NOTHING));
        helper.assertTrue(player.level() == soul && player.position().distanceTo(court) < 1.0e-6,
                "the player did not arrive at the court");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(player.level() == soul && !player.isRemoved()
                    && Math.abs(player.getY() - SeireiteiPlan.PLATEAU_Y) < 1.0e-6,
                    "after 20 ticks the player is no longer standing at the court");
            player.connection.disconnect(Component.literal("Test finished"));
        });
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(server.getPlayerList().getPlayer(player.getUUID()) == null && player.isRemoved(),
                    "the disconnected player is still on the server");
            helper.succeed();
        });
    }
}
