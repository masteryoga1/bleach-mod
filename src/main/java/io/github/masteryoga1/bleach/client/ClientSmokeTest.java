package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A check CI runs on a real client (the {@code runClientSmokeTest} Gradle task, which sets
 * {@code -Dbleach.smokeTest=true}). It makes a flat world, turns the player into a Plus, releases
 * reiatsu, brings on Encroachment, turns them into a Hollow and opens the soul screen, all with the
 * camera behind the player so the chain, mask and HUD are drawn, then quits. Any crash in the mod's
 * client code fails the run, and so does a soul that did not reach the client as expected.
 */
public final class ClientSmokeTest {
    public static final String PASSED = "Bleach client smoke test passed";
    private static final String FAILED = "Bleach client smoke test FAILED: ";
    /** Give up if the world has not loaded after this many client ticks (five minutes). */
    private static final int WORLD_TIMEOUT_TICKS = 6000;

    private static boolean worldRequested;
    private static int ticksWaiting;
    private static int ticksInWorld;
    private static boolean finished;

    private ClientSmokeTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("bleach.smokeTest");
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (finished || minecraft.getOverlay() != null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            if (!worldRequested && minecraft.screen != null) {
                worldRequested = true;
                createWorld(minecraft);
            } else if (++ticksWaiting > WORLD_TIMEOUT_TICKS) {
                fail(minecraft, "the world did not load");
            }
            return;
        }

        SoulData soul = minecraft.player.getData(SoulAttachments.SOUL);
        switch (++ticksInWorld) {
            case 40 -> {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                command(minecraft, "bleach soul @s plus");
            }
            case 80 -> {
                expect(minecraft, soul.state() == SoulState.PLUS && soul.chainMaxTicks() > 0,
                        "the client never saw the Plus and its chain");
                command(minecraft, "bleach reiryoku @s 500");
            }
            case 100 -> BleachNetwork.requestReiatsuToggle();
            case 140 -> {
                expect(minecraft, soul.isReiatsuReleased(), "the release key did not release reiatsu");
                command(minecraft, "bleach chain @s 30");
            }
            case 180 -> {
                expect(minecraft, SoulLogic.isEncroaching(soul), "a 30-second chain is not encroaching");
                command(minecraft, "bleach soul @s hollow");
            }
            case 220 -> {
                expect(minecraft, soul.state() == SoulState.HOLLOW, "the client never saw the Hollow");
                minecraft.setScreen(new SoulStatsScreen());
            }
            case 260 -> {
                minecraft.setScreen(null);
                finished = true;
                BleachMod.LOGGER.info(PASSED);
                minecraft.stop();
            }
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft minecraft) {
        BleachMod.LOGGER.info("Smoke test: creating a flat world");
        minecraft.options.renderDistance().set(2);
        LevelSettings settings = new LevelSettings("bleach-smoke-test-" + System.currentTimeMillis(),
                GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings,
                WorldOptions.defaultWithRandomSeed(),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                minecraft.screen);
    }

    private static void command(Minecraft minecraft, String command) {
        BleachMod.LOGGER.info("Smoke test: /{}", command);
        minecraft.player.connection.sendCommand(command);
    }

    private static void expect(Minecraft minecraft, boolean condition, String problem) {
        if (!condition) {
            fail(minecraft, problem);
        }
    }

    private static void fail(Minecraft minecraft, String problem) {
        finished = true;
        BleachMod.LOGGER.error(FAILED + problem);
        minecraft.stop();
    }
}
