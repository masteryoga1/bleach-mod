package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
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
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
 * Academy, summons each Hollow and a Plus, cracks a mask, purifies a Hollow, sends a Plus on, opens a
 * Garganta and checks that a soul just below the threshold sees only a shimmer, and quits. All of it
 * is seen with the camera behind the player, so the chain, mask, garb, saya, NPCs, Hollows and HUD
 * are drawn, and screenshots of the main moments are saved for review. Any crash in the mod's client
 * code fails the run, and so does anything that did not reach the client as expected.
 */
public final class ClientSmokeTest {
    public static final String PASSED = "Bleach client smoke test passed";
    private static final String FAILED = "Bleach client smoke test FAILED: ";
    /** Give up if the world has not loaded after this many client ticks (five minutes). */
    private static final int WORLD_TIMEOUT_TICKS = 6000;

    /** How far the flash step must carry the player, and how high above the ground footing must hold them. */
    private static final double DASH_MIN_DISTANCE = 4.0;
    private static final double FOOTING_MIN_HEIGHT = 8.0;
    /** How far around the player the summoned Hollows and Plus, and the Garganta, are looked for. */
    private static final double SUMMONED_RANGE = 24.0;
    private static final double RIFT_RANGE = 40.0;
    /** Summoned creatures stand still, facing south, towards a player who faces north. */
    private static final String STILL = "{NoAI:1b,Rotation:[0f,0f]}";

    private static boolean worldRequested;
    private static int ticksWaiting;
    private static int ticksInWorld;
    private static boolean finished;
    /** The first tick of each {@link #expectWithin} window that has already passed. */
    private static final Set<Integer> windowsPassed = new HashSet<>();
    /** Where the player stood before the flash step. */
    private static Vec3 dashFrom;
    /** What the current step acts on: the Hollow to strike, or the Plus to send on. */
    @Nullable
    private static Entity subject;

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
        expectWithin(minecraft, 565, 640, () -> summonedSeen(minecraft),
                "the summoned Hollows and Plus never reached the client");
        if (expectWithin(minecraft, 660, 700, () -> subject instanceof Hollow hollow && hollow.isMaskCracked(),
                "a blow to the mask did not crack it")) {
            shot(minecraft, "mask");
        }
        if (expectWithin(minecraft, 740, 780, () -> soul.hunt().purified() >= 1,
                "the Asauchi kill did not purify the Hollow")) {
            shot(minecraft, "purify");
        }
        // The Plus summoned at tick 561 stands within 4 blocks too, so this follows the Plus that was sent on.
        expectWithin(minecraft, 815, 860,
                () -> subject instanceof PlusSpirit sent && sent.isRemoved() && soul.hunt().sentOn() >= 1,
                "Konsō did not send the Plus on");
        if (expectWithin(minecraft, 872, 930, () -> openRift(minecraft) != null,
                "the Garganta never opened on the client")) {
            GargantaRift rift = openRift(minecraft);
            if (rift != null) {
                look(minecraft, rift.position().add(0.0, 2.0, 0.0));
            }
        }
        if (expectWithin(minecraft, 940, 980,
                () -> nearest(minecraft, SpiderHollow.class, 6.0,
                        spider -> spider.isAlive() && isSpiderHollow(spider) && ClientSouls.isHiddenFromViewer(spider)) != null
                        && SpiritShimmer.emitted() > 0,
                "an unaware soul just below the threshold saw the Hollow or no shimmer")) {
            shot(minecraft, "unaware");
        }
        if (finished) {
            return;
        }

        switch (ticksInWorld) {
            case 40 -> {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                // No Garganta, wandering Plus or first Hollow comes on its own: every Hollow here is summoned.
                command(minecraft, "gamerule doMobSpawning false");
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
            case 550 -> minecraft.setScreen(null);
            // Hollows vanish on Peaceful. Back on the ground, off the footing, facing north.
            case 560 -> {
                command(minecraft, "difficulty easy");
                command(minecraft, "tp @s ~ -60 ~ 180 0");
            }
            case 561 -> {
                command(minecraft, "summon bleach:spider_hollow ~-4 ~ ~-6 " + STILL);
                command(minecraft, "summon bleach:hexapodus ~-1.5 ~ ~-6 " + STILL);
                command(minecraft, "summon bleach:hound_hollow ~1.5 ~ ~-6 " + STILL);
                command(minecraft, "summon bleach:serpent_hollow ~4 ~ ~-7 " + STILL);
                command(minecraft, "summon bleach:fishbone_d ~ ~ ~-11 " + STILL);
                command(minecraft, "summon bleach:plus ~-2 ~ ~-3 " + STILL);
            }
            case 600 -> shot(minecraft, "hollows");
            // Each thing summoned gets twenty ticks or more to reach the client before it is looked for.
            case 625 -> command(minecraft, "summon bleach:spider_hollow ~ ~ ~-2.5 " + STILL);
            case 650 -> aimAtMask(minecraft, "the Spider Hollow in front of the player never reached the client");
            // Slot 0 is empty, so this is a bare hand.
            case 654 -> {
                if (subject != null) {
                    minecraft.gameMode.attack(player, subject);
                }
            }
            case 705 -> command(minecraft, "kill @e[type=bleach:spider_hollow,distance=..4]");
            case 706 -> command(minecraft, "summon bleach:spider_hollow ~ ~ ~-2.5 {NoAI:1b,Rotation:[0f,0f],Health:1f}");
            // The Asauchi, put in the last hotbar slot at tick 262.
            case 708 -> player.getInventory().selected = 8;
            case 730 -> aimAtMask(minecraft, "the weakened Spider Hollow never reached the client");
            case 732 -> {
                if (subject != null) {
                    minecraft.gameMode.attack(player, subject);
                }
            }
            case 785 -> command(minecraft, "summon bleach:plus ~ ~ ~-2 " + STILL);
            case 805 -> minecraft.options.keyShift.setDown(true);
            case 810 -> {
                subject = nearest(minecraft, PlusSpirit.class, 3.0, plus -> plus.isAlive() && !plus.isDeparting());
                expect(minecraft, subject != null, "the Plus in front of the player never reached the client");
                if (subject != null) {
                    look(minecraft, subject.getEyePosition());
                }
            }
            // The interaction carries the sneak with it, so the server sees Konsō.
            case 812 -> {
                if (subject != null) {
                    minecraft.gameMode.interact(player, subject, InteractionHand.MAIN_HAND);
                }
            }
            case 818 -> minecraft.options.keyShift.setDown(false);
            case 870 -> command(minecraft, "bleach garganta @s spider");
            case 915 -> shot(minecraft, "garganta");
            case 932 -> command(minecraft, "kill @e[type=#bleach:hollows]");
            case 935 -> {
                // Just below the threshold with no power awakened. The Hollow state at tick 180 left a Hollow tier.
                command(minecraft, "bleach soul @s human");
                command(minecraft, "bleach tier @s shinigami 0");
                command(minecraft, "bleach tier @s hollow 0");
                command(minecraft, "bleach reiryoku @s 22 22");
                command(minecraft, "summon bleach:spider_hollow ~ ~ ~-3 {NoAI:1b}");
            }
            case 985 -> {
                command(minecraft, "kill @e[type=#bleach:hollows]");
                command(minecraft, "kill @e[type=bleach:plus]");
                command(minecraft, "difficulty peaceful");
            }
            case 1010 -> {
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

    /** The nearest entity of this class within {@code range} blocks of the player that passes the filter, or null. */
    @Nullable
    private static <T extends Entity> T nearest(Minecraft minecraft, Class<T> type, double range, Predicate<T> filter) {
        LocalPlayer player = minecraft.player;
        T nearest = null;
        double nearestSqr = range * range;
        for (T entity : minecraft.level.getEntitiesOfClass(type, player.getBoundingBox().inflate(range), filter)) {
            double distanceSqr = entity.distanceToSqr(player);
            if (distanceSqr <= nearestSqr) {
                nearest = entity;
                nearestSqr = distanceSqr;
            }
        }
        return nearest;
    }

    /** Turns the player's eyes to a point, as the mouse would. */
    private static void look(Minecraft minecraft, Vec3 target) {
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    /** Whether this is a Spider Hollow and not a Hexapodus, which is a kind of Spider Hollow too. */
    private static boolean isSpiderHollow(Entity entity) {
        return entity.getType() == ModEntities.SPIDER_HOLLOW.get();
    }

    /** Takes the living Spider Hollow just in front of the player as the subject, and looks at its mask. */
    private static void aimAtMask(Minecraft minecraft, String problem) {
        subject = nearest(minecraft, SpiderHollow.class, 3.5, spider -> spider.isAlive() && isSpiderHollow(spider));
        expect(minecraft, subject != null, problem);
        if (subject instanceof Hollow hollow) {
            look(minecraft, HollowMask.maskCenter(hollow));
        }
    }

    /** Whether one of each kind of Hollow and a Plus has reached the client nearby, and none is hidden. */
    private static boolean summonedSeen(Minecraft minecraft) {
        List<EntityType<?>> types = List.of(ModEntities.SPIDER_HOLLOW.get(), ModEntities.HEXAPODUS.get(),
                ModEntities.HOUND_HOLLOW.get(), ModEntities.SERPENT_HOLLOW.get(), ModEntities.FISHBONE_D.get(),
                ModEntities.PLUS.get());
        for (EntityType<?> type : types) {
            if (nearest(minecraft, Entity.class, SUMMONED_RANGE,
                    entity -> entity.getType() == type && !ClientSouls.isHiddenFromViewer(entity)) == null) {
                return false;
            }
        }
        return true;
    }

    /** The nearest Garganta on the client that is at least mostly open, or null. */
    @Nullable
    private static GargantaRift openRift(Minecraft minecraft) {
        return nearest(minecraft, GargantaRift.class, RIFT_RANGE, rift -> rift.openness(0.0F) >= 0.8F);
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
