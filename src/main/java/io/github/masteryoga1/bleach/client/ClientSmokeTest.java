package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A check CI runs on a real client (the {@code runClientSmokeTest} Gradle task, which sets
 * {@code -Dbleach.smokeTest=true}). It makes a flat world, turns the player into a Plus, releases
 * reiatsu, brings on Encroachment, turns them into a Hollow and opens the soul screen. Then it makes
 * them a Shinigami in the Shihakushō with a sheathed Asauchi, opens the Tailoring Table and the
 * instructor's dialogue, flash steps, stands on reishi footing by jumping while falling, places an
 * Academy and quits. All of it is seen with the camera behind the player, so the chain, mask, garb,
 * saya, NPCs and HUD are drawn, and screenshots of the main moments are saved for review. Any crash
 * in the mod's client code fails the run, and so does anything that did not reach the client as
 * expected.
 */
public final class ClientSmokeTest {
    public static final String PASSED = "Bleach client smoke test passed";
    private static final String FAILED = "Bleach client smoke test FAILED: ";
    /** Give up if the world has not loaded after this many client ticks (five minutes). */
    private static final int WORLD_TIMEOUT_TICKS = 6000;

    /** How far the flash step must carry the player, and how high above the ground footing must hold them. */
    private static final double DASH_MIN_DISTANCE = 4.0;
    private static final double FOOTING_MIN_HEIGHT = 8.0;

    private static boolean worldRequested;
    private static int ticksWaiting;
    private static int ticksInWorld;
    private static boolean finished;
    /** The first tick of each {@link #expectWithin} window that has already passed. */
    private static final Set<Integer> windowsPassed = new HashSet<>();
    /** Where the player stood before the flash step. */
    private static Vec3 dashFrom;

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

        LocalPlayer player = minecraft.player;
        SoulData soul = player.getData(SoulAttachments.SOUL);
        ++ticksInWorld;
        expectWithin(minecraft, 345, 380,
                () -> horizontalDistance(player.position(), dashFrom) >= DASH_MIN_DISTANCE,
                "the flash step did not carry the player " + DASH_MIN_DISTANCE + " blocks");
        if (expectWithin(minecraft, 390, 430,
                () -> player.onGround() && player.getY() > dashFrom.y + FOOTING_MIN_HEIGHT
                        && player.level().getBlockState(player.blockPosition().below()).is(ModBlocks.REISHI_FOOTING.get()),
                "jumping while falling did not stand the player on reishi footing")) {
            shot(minecraft, "footing");
        }
        expectWithin(minecraft, 450, 500, () -> instructorsNear(minecraft, 64.0).size() >= 2,
                "the placed Academy's instructor never reached the client");
        if (finished) {
            return;
        }

        switch (ticksInWorld) {
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
                // Now a Shinigami, with a full reserve and nothing slowing them down.
                minecraft.setScreen(null);
                if (soul.isReiatsuReleased()) {
                    BleachNetwork.requestReiatsuToggle();
                }
                command(minecraft, "bleach reiryoku @s 500 500");
                command(minecraft, "effect clear @s");
                command(minecraft, "bleach soul @s shinigami");
                command(minecraft, "bleach stat @s hoho 10");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
            }
            case 262 -> {
                // Split over two ticks so the burst of commands reads as two.
                command(minecraft, "item replace entity @s hotbar.8 with bleach:asauchi");
                command(minecraft, "item replace entity @s armor.chest with bleach:shihakusho_kosode");
                command(minecraft, "item replace entity @s armor.legs with bleach:shihakusho_hakama");
                command(minecraft, "item replace entity @s armor.feet with bleach:waraji");
                command(minecraft, "setblock ~2 ~ ~ bleach:tailoring_table");
                command(minecraft, "summon bleach:academy_instructor ~-2 ~ ~");
                command(minecraft, "summon bleach:wounded_shinigami ~-2 ~ ~3");
            }
            case 300 -> {
                expect(minecraft, soul.state() == SoulState.SHINIGAMI && soul.tier(PowerPath.SHINIGAMI) >= 1,
                        "the client never saw the Shinigami");
                expect(minecraft, player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SHIHAKUSHO_KOSODE.get())
                                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.SHIHAKUSHO_HAKAMA.get())
                                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.WARAJI.get()),
                        "the player is not wearing the Shihakushō");
                expect(minecraft, ClientSouls.appearance(player).bladeSheathed(),
                        "the Asauchi in the hotbar is not sheathed at the hip");
                shot(minecraft, "gear");
                BlockPos table = BlockPos.containing(player.getX() + 2.0, player.getY(), player.getZ());
                minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
            }
            case 320 -> {
                expect(minecraft, minecraft.screen instanceof TailoringScreen, "the Tailoring Table did not open");
                player.closeContainer();
                List<AcademyInstructor> instructors = instructorsNear(minecraft, 6.0);
                expect(minecraft, !instructors.isEmpty(), "the summoned instructor never reached the client");
                if (!instructors.isEmpty()) {
                    minecraft.gameMode.interact(player, instructors.get(0), InteractionHand.MAIN_HAND);
                }
            }
            case 340 -> {
                expect(minecraft, minecraft.screen instanceof ShinigamiDialogueScreen,
                        "talking to the instructor did not open the dialogue");
                minecraft.setScreen(null);
                dashFrom = player.position();
                BleachNetwork.requestFlashStep(1.0F, 0.0F);
            }
            case 380 -> command(minecraft, "tp @s ~ ~12 ~");
            // Pressing jump again while falling, through the real key, asks for footing.
            case 386 -> minecraft.options.keyJump.setDown(true);
            case 388 -> minecraft.options.keyJump.setDown(false);
            case 440 -> command(minecraft, "place structure bleach:academy ~ ~-12 ~20");
            case 505 -> shot(minecraft, "academy");
            case 510 -> minecraft.setScreen(new SoulStatsScreen());
            // A screenshot shows the last frame drawn, so it waits a moment for the screen to be drawn.
            case 515 -> shot(minecraft, "stats");
            case 550 -> {
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
        minecraft.options.renderDistance().set(4);
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
        if (!condition && !finished) {
            fail(minecraft, problem);
        }
    }

    /**
     * Checks a condition every tick from {@code from} to {@code to}, for something that takes the
     * server a while. It passes the first tick the condition holds, and fails if it never does.
     *
     * @return true on the tick it passes
     */
    private static boolean expectWithin(Minecraft minecraft, int from, int to, BooleanSupplier condition, String problem) {
        if (finished || ticksInWorld < from || ticksInWorld > to || windowsPassed.contains(from)) {
            return false;
        }
        if (condition.getAsBoolean()) {
            windowsPassed.add(from);
            return true;
        }
        if (ticksInWorld == to) {
            fail(minecraft, problem);
        }
        return false;
    }

    private static List<AcademyInstructor> instructorsNear(Minecraft minecraft, double range) {
        LocalPlayer player = minecraft.player;
        return minecraft.level.getEntitiesOfClass(AcademyInstructor.class, player.getBoundingBox().inflate(range),
                instructor -> instructor.distanceTo(player) <= range);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    /** Saves a screenshot to {@code screenshots/} for a person to look over. A failure here is only logged. */
    private static void shot(Minecraft minecraft, String name) {
        try {
            Screenshot.grab(minecraft.gameDirectory, "bleach-smoke-" + name + ".png", minecraft.getMainRenderTarget(),
                    message -> BleachMod.LOGGER.info("Smoke test: {}", message.getString()));
        } catch (RuntimeException e) {
            BleachMod.LOGGER.warn("Smoke test: could not save the {} screenshot", name, e);
        }
    }

    private static void fail(Minecraft minecraft, String problem) {
        finished = true;
        BleachMod.LOGGER.error(FAILED + problem);
        minecraft.stop();
    }
}
