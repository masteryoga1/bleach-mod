package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.world.GateHouses;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the great gates and their wickets in the real Soul Society: four in a batch of their own each work one gate,
 * and the wicket's walk runs with the gate houses' batch on the east house, which none of those holds. Every test that
 * works a gate drops it, waits for it laid and down, and drops it again however it ends, so a gate left falling by one
 * batch is down before the next works it.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GateTests {
    private static final String BATCH = "gate_state";
    /** The gates by the plan's order, the Rukongai's regions'. */
    private static final int EAST = 0, SOUTH = 1, WEST = 2, NORTH = 3;
    private static final int LIFT = SeireiteiPlan.GATE_LIFT;
    private static final String[] GATE_KEYS = {"message.bleach.place.gate.east", "message.bleach.place.gate.south",
            "message.bleach.place.gate.west", "message.bleach.place.gate.north"};
    private static final String SEIREIHEKI_KEY = "message.bleach.place.seireiheki";

    private GateTests() {
    }

    // ---- Helpers ----

    /** Soul Society's chunks about this gate's house, held as the Shakonmaku's tests hold theirs, and the gate dropped. */
    private static ShakonmakuTests.Cleanup hold(GameTestHelper helper, int gate) {
        ChunkPos home = GateHouses.STAMP.startChunk(gate);
        return dropped(ShakonmakuTests.hold(helper, home.x, home.z), gate);
    }

    /** Asserts that this gate is laid, and stands or waits as said at this height. */
    static void assertIs(GameTestHelper helper, GateState gates, int gate, GateState.Status status, int height) {
        helper.assertTrue(gates.status(gate) == status && gates.height(gate) == height, "gate " + gate + " is "
                + gates.status(gate) + " at height " + gates.height(gate) + ", not " + status + " at " + height);
    }

    /** Asserts that this gate is laid and down, as every test that works one waits for first. */
    static void assertDown(GameTestHelper helper, GateState gates, int gate) {
        assertIs(helper, gates, gate, GateState.Status.DOWN, 0);
    }

    /**
     * Asserts that this gate stands so high, its block in exactly those of its cells, and that its wicket stands whole,
     * hinged on the right of a soul coming in from the Rukongai, turned with the house.
     */
    static void assertGate(GameTestHelper helper, ServerLevel soul, int gate, int height, String when) {
        for (int row = 0; row < GateState.ROWS + LIFT; row++) {
            boolean in = row >= height && row < height + GateState.ROWS;
            for (BlockPos column : GateState.columns(gate)) {
                BlockState state = soul.getBlockState(column.above(row));
                helper.assertTrue(in ? state.is(ModBlocks.GREAT_GATE.get()) : state.isAir(), "gate " + gate + "'s cell "
                        + column.above(row).toShortString() + " is " + state + " " + when);
            }
        }
        BlockPos wicket = GateState.wicket(gate);
        BlockState lower = soul.getBlockState(wicket), upper = soul.getBlockState(wicket.above());
        Direction facing = GateHouses.STAMP.sites().get(gate).rotation().rotate(Direction.NORTH);
        helper.assertTrue(lower.is(ModBlocks.WICKET.get()) && lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && lower.getValue(DoorBlock.FACING) == facing
                        && lower.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT
                        && upper.is(ModBlocks.WICKET.get()) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER,
                "gate " + gate + "'s wicket at " + wicket.toShortString() + " is " + lower + " under " + upper + " " + when);
    }

    /** Drops this gate now, should a run cut short have left it lifted, and again when this hold's test ends. */
    static ShakonmakuTests.Cleanup dropped(ShakonmakuTests.Cleanup held, int gate) {
        GateState.get(held.soul).drop(held.soul, gate);
        held.atEnd(() -> GateState.get(held.soul).drop(held.soul, gate));
        return held;
    }

    /** Runs this command as the server itself, in the Overworld, and gives back every line it said. */
    private static List<Component> run(ServerLevel soul, String command) {
        List<Component> said = new ArrayList<>();
        CommandSource listener = new CommandSource() {
            @Override
            public void sendSystemMessage(Component line) {
                said.add(line);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        MinecraftServer server = soul.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(listener), command);
        return said;
    }

    /** Whether this line, or one it holds, has this key and, unless the gate is -1, names that gate first. */
    private static boolean says(Component line, String key, int gate) {
        if (line.getContents() instanceof TranslatableContents words && words.getKey().equals(key)) {
            return gate < 0 || words.getArgs().length > 0 && words.getArgs()[0] instanceof Component name
                    && key(name).equals(GATE_KEYS[gate]);
        }
        return line.getSiblings().stream().anyMatch(each -> says(each, key, gate));
    }

    /** Asserts that a command said one line, the one with this key naming this gate. */
    private static void assertSaid(GameTestHelper helper, List<Component> said, String key, int gate, String what) {
        helper.assertTrue(said.size() == 1 && says(said.get(0), key, gate), what + " said "
                + said.stream().map(Component::getString).toList() + ", not " + key);
    }

    /** A line's lang key, or its text if it has none. */
    private static String key(Component line) {
        return line.getContents() instanceof TranslatableContents words ? words.getKey() : line.getString();
    }

    /** A cow set down here in Soul Society, that neither walks nor falls, taken away when the test ends. */
    private static Cow cow(ShakonmakuTests.Cleanup held, Vec3 at) {
        Cow cow = EntityType.COW.create(held.soul);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return held.add(cow);
    }

    /** Moves this player as its client would, and asserts where the server then has it. */
    private static void step(GameTestHelper helper, ServerPlayer player, Vec3 to, Vec3 expected, String what) {
        ShakonmakuTests.move(player, to);
        helper.assertTrue(player.position().distanceToSqr(expected) < 1.0E-6, what + " is at " + player.position()
                + ", not " + expected);
    }

    // ---- GateState ----

    /**
     * An operator in the Overworld lifts Shōryūmon by command and drops it, each told back by the gate's name: the
     * status as it goes, and its cells as each move leaves them. A name it does not have is no gate.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void anOperatorLiftsAndDropsAGateByCommand(GameTestHelper helper) {
        ServerLevel soul = hold(helper, EAST).soul;
        GateState gates = GateState.get(soul);
        helper.startSequence()
                .thenWaitUntil(() -> assertDown(helper, gates, EAST))
                .thenExecute(() -> {
                    assertGate(helper, soul, EAST, 0, "before the command");
                    assertSaid(helper, run(soul, "bleach gate shoryuumon lift"), "commands.bleach.unknown_gate", -1,
                            "lifting a gate not named");
                    assertSaid(helper, run(soul, "bleach gate shoryumon status"), "commands.bleach.gate.status.down",
                            EAST, "the status of the gate down");
                    assertSaid(helper, run(soul, "bleach gate Shoryumon status"), "commands.bleach.gate.status.down",
                            EAST, "the status asked with a capital");
                    assertSaid(helper, run(soul, "bleach gate shoryumon lift"), "commands.bleach.gate.lift", EAST,
                            "the lift");
                    assertSaid(helper, run(soul, "bleach gate shoryumon lift"), "commands.bleach.gate.status.rising",
                            EAST, "a second lift");
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    assertSaid(helper, run(soul, "bleach gate shoryumon status"), "commands.bleach.gate.status.rising",
                            EAST, "the status of the gate rising");
                    helper.assertTrue(gates.height(EAST) > 0 && gates.height(EAST) < LIFT,
                            "the gate is at " + gates.height(EAST) + " 30 ticks into its lift");
                    assertGate(helper, soul, EAST, gates.height(EAST), "while it rose");
                })
                .thenWaitUntil(() -> assertIs(helper, gates, EAST, GateState.Status.LIFTED, LIFT))
                .thenExecute(() -> {
                    assertGate(helper, soul, EAST, LIFT, "once lifted");
                    assertSaid(helper, run(soul, "bleach gate shoryumon status"), "commands.bleach.gate.status.lifted",
                            EAST, "the status of the gate lifted");
                    assertSaid(helper, run(soul, "bleach gate shoryumon drop"), "commands.bleach.gate.drop", EAST,
                            "the drop");
                })
                .thenWaitUntil(() -> assertDown(helper, gates, EAST))
                .thenExecute(() -> {
                    assertGate(helper, soul, EAST, 0, "once dropped");
                    assertSaid(helper, run(soul, "bleach gate shoryumon drop"), "commands.bleach.gate.status.down",
                            EAST, "a second drop");
                })
                .thenSucceed();
    }

    /**
     * Shuwaimon, lifted, is dropped over a cow in its row: it breaks a block of dirt in its way, its item dropped, and comes
     * down past a spectator to wait over the cow, unhurt, until it has gone, then at a barrier until that is cleared.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void aFallingGateWaitsForASoulUnderIt(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = hold(helper, SOUTH);
        ServerLevel soul = held.soul;
        GateState gates = GateState.get(soul);
        Vec3 under = Vec3.atBottomCenterOf(GateState.columns(SOUTH).get(5));
        // Dirt in the passage under the gate lifted, and a barrier, which no gate breaks, at the doorway's floor.
        BlockPos dirt = GateState.columns(SOUTH).get(2).above(6), barrier = GateState.columns(SOUTH).get(8);
        held.atEnd(() -> {
            if (soul.getBlockState(barrier).is(Blocks.BARRIER)) {
                soul.removeBlock(barrier, false);
            }
        });
        ServerPlayer spectator = TestPlayers.join(helper, GameType.SPECTATOR, new Vec3(1.5, 1.0, 1.5));
        Cow[] cow = new Cow[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertDown(helper, gates, SOUTH))
                .thenExecute(() -> gates.lift(soul, SOUTH))
                .thenWaitUntil(() -> assertIs(helper, gates, SOUTH, GateState.Status.LIFTED, LIFT))
                .thenExecute(() -> {
                    cow[0] = cow(held, under);
                    soul.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
                    spectator.teleportTo(soul, under.x + 2.0, under.y + 5.0, under.z, 0.0F, 0.0F);
                    gates.drop(soul, SOUTH);
                })
                // The cow stands 1.4 high, in the gate's rows 0 and 1.
                .thenWaitUntil(() -> assertIs(helper, gates, SOUTH, GateState.Status.WAITING, 2))
                .thenIdle(20)
                .thenExecute(() -> {
                    assertIs(helper, gates, SOUTH, GateState.Status.WAITING, 2);
                    assertGate(helper, soul, SOUTH, 2, "over the cow");
                    helper.assertTrue(cow[0].isAlive() && cow[0].getHealth() == cow[0].getMaxHealth()
                            && cow[0].position().equals(under), "the cow under the gate is at " + cow[0].position());
                    // Pushed out of the gate's row, a drop can fly some 6 blocks along the passage before it lands.
                    List<ItemEntity> items = soul.getEntitiesOfClass(ItemEntity.class, new AABB(dirt).inflate(8.0),
                            item -> item.getItem().is(Items.DIRT));
                    helper.assertFalse(items.isEmpty(), "the dirt the gate broke dropped nothing");
                    items.forEach(Entity::discard);
                    cow[0].discard();
                    soul.setBlockAndUpdate(barrier, Blocks.BARRIER.defaultBlockState());
                })
                .thenWaitUntil(() -> assertIs(helper, gates, SOUTH, GateState.Status.WAITING, 1))
                .thenIdle(10)
                .thenExecute(() -> {
                    assertIs(helper, gates, SOUTH, GateState.Status.WAITING, 1);
                    soul.removeBlock(barrier, false);
                })
                .thenWaitUntil(() -> assertDown(helper, gates, SOUTH))
                .thenExecute(() -> assertGate(helper, soul, SOUTH, 0, "once its way was clear"))
                .thenSucceed();
    }

    /**
     * Hakutōmon's house with its gate's and wicket's cells open, as a house stood before the gates, and a post broken,
     * gains them when laid again, a cow they bury on each half moved out of the gate's row on that half, and a lift
     * frees a cow in the slot.
     * The heights and targets saved read back, none laid by the reading, and a damaged entry reads within range.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void aHouseWithItsGateCellsOpenGainsThemWhenLaid(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = hold(helper, WEST);
        ServerLevel soul = held.soul;
        GateState gates = GateState.get(soul);
        // In the west gate's row (x -517) on the Rukongai's half, and on the lawn's half leaning into it; a cow is 0.9 wide.
        Vec3 inRow = Vec3.atBottomCenterOf(GateState.columns(WEST).get(5)), leaning = inRow.add(0.75, 0.0, 2.0);
        // In the slot over the doorway, the gate's rows 28 and 29 once it rises.
        Vec3 inSlot = inRow.add(0.0, 28.0, 0.0);
        Cow[] cows = new Cow[3];
        helper.startSequence()
                .thenWaitUntil(() -> assertDown(helper, gates, WEST))
                .thenExecute(() -> {
                    for (int row = 0; row < GateState.ROWS + LIFT; row++) {
                        for (BlockPos column : GateState.columns(WEST)) {
                            soul.setBlock(column.above(row), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    soul.setBlock(GateState.wicket(WEST).above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    soul.setBlock(GateState.wicket(WEST), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    cows[0] = cow(held, inRow);
                    cows[1] = cow(held, leaning);
                    // A survival player may break a doorway post, the stamp's marker: the house's reference still counts.
                    SeireiteiPlan.Site site = GateHouses.STAMP.sites().get(WEST);
                    BlockPos post = GateHouses.STAMP.turned(WEST, GateHouses.LEFT_POST,
                            new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z()));
                    BlockState standing = soul.getBlockState(post);
                    soul.setBlock(post, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    held.atEnd(() -> soul.setBlock(post, standing, Block.UPDATE_CLIENTS));
                    gates.forget(GateHouses.box(WEST));
                    assertIs(helper, gates, WEST, GateState.Status.NOT_LAID, 0);
                })
                .thenWaitUntil(() -> assertDown(helper, gates, WEST))
                .thenExecute(() -> {
                    assertGate(helper, soul, WEST, 0, "laid in a house whose cells were open");
                    BlockState block = ModBlocks.GREAT_GATE.get().defaultBlockState();
                    helper.assertTrue(block.getBlock().defaultDestroyTime() < 0.0F && block.is(BleachTags.NEGATES_REIRYOKU)
                                    && block.getPistonPushReaction() == PushReaction.BLOCK,
                            "the great gate can be broken or pushed, or lets spirit arts through");
                    // Each against the gate's face on its own half, as far through as its half width and a hair.
                    Vec3 out = new Vec3(-517.46, inRow.y, inRow.z), in = new Vec3(-515.54, leaning.y, leaning.z);
                    helper.assertTrue(cows[0].position().distanceToSqr(out) < 1.0E-6
                                    && cows[1].position().distanceToSqr(in) < 1.0E-6,
                            "the cows the gate buried are at " + cows[0].position() + " and " + cows[1].position());
                    CompoundTag saved = gates.save(new CompoundTag(), soul.registryAccess());
                    GateState read = GateState.load(saved, soul.registryAccess());
                    for (int gate = EAST; gate <= NORTH; gate++) {
                        helper.assertTrue(read.height(gate) == gates.height(gate) && read.target(gate) == gates.target(gate)
                                        && read.status(gate) == GateState.Status.NOT_LAID,
                                "gate " + gate + " was read back from " + saved + " as " + read.status(gate));
                    }
                    // Damaged: the east entry out of range, the north one gone.
                    ListTag list = saved.getList("Gates", Tag.TAG_COMPOUND);
                    list.getCompound(EAST).putInt("Height", 99);
                    list.getCompound(EAST).putInt("Target", 7);
                    list.remove(NORTH);
                    GateState lenient = GateState.load(saved, soul.registryAccess());
                    helper.assertTrue(lenient.height(EAST) == LIFT && lenient.target(EAST) == 0
                                    && lenient.height(NORTH) == 0 && lenient.target(NORTH) == 0,
                            "a damaged entry was read as " + lenient.height(EAST) + " to " + lenient.target(EAST));
                    cows[2] = cow(held, inSlot);
                    gates.lift(soul, WEST);
                })
                .thenWaitUntil(() -> assertIs(helper, gates, WEST, GateState.Status.LIFTED, LIFT))
                .thenExecute(() -> {
                    // Into the doorway on the Rukongai's half, against the gate's face, its head under the doorway's.
                    Vec3 freed = new Vec3(-517.46, SeireiteiPlan.GATE_DOOR_TOP_Y - 1.41, inSlot.z);
                    helper.assertTrue(cows[2].position().distanceToSqr(freed) < 1.0E-6, "the cow in the slot is at "
                            + cows[2].position() + " once the gate rose into it");
                    gates.drop(soul, WEST);
                })
                .thenWaitUntil(() -> assertDown(helper, gates, WEST))
                .thenSucceed();
    }

    /** A Cero fired from Kokuryōmon's doorway at the gate, down, comes undone at its face: no blast and no carving. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void aCeroComesUndoneAtAGreatGate(GameTestHelper helper) {
        ServerLevel soul = hold(helper, NORTH).soul;
        GateState gates = GateState.get(soul);
        CeroBeam.Spec spec = new CeroBeam.Spec(10.0F, 16.0, 0.5F, 3.0F, 0.5F, 8, 0.0F, 0xFFFFFF, 1, 0, true);
        BlockPos column = GateState.columns(NORTH).get(5);
        // Three blocks inside the gate's row, at the doorway's middle height, aimed out at the Rukongai.
        Vec3 from = new Vec3(column.getX() + 0.5, 90.5, column.getZ() + 3.5), out = new Vec3(0.0, 0.0, -1.0);
        helper.startSequence()
                .thenWaitUntil(() -> assertDown(helper, gates, NORTH))
                .thenExecute(() -> {
                    CeroBeam.Shot shot = CeroBeam.fire(soul, null, null, from, out, spec);
                    helper.assertTrue(shot.negated() && !shot.hitBlock() && shot.blocksBroken() == 0
                            && Math.abs(shot.to().z - (column.getZ() + 1.0)) < 1.0E-3, "the Cero at the gate: " + shot);
                    assertGate(helper, soul, NORTH, 0, "after the Cero");
                })
                .thenSucceed();
    }

    // ---- The wicket ----

    /**
     * A Shinigami comes in from the Rukongai by the east house's wicket beside Shōryūmon, down: a step through the wicket
     * shut is put back; the soul opens it by hand, both halves swing, and it walks through into Seireitei and out again.
     */
    @GameTest(template = "empty", batch = "gate_house", timeoutTicks = 400)
    public static void aSoulOpensTheWicketAndWalksThrough(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = hold(helper, EAST);
        ServerLevel soul = held.soul;
        GateState gates = GateState.get(soul);
        BlockPos wicket = GateState.wicket(EAST);
        held.atEnd(() -> shut(soul, wicket));
        // Along the wicket's way, clear of the wicket swung open against its north side and of the pier south of it.
        double x = wicket.getX(), z = wicket.getZ() + 0.6;
        Vec3[] way = {new Vec3(x + 3.5, 72.0, z), new Vec3(x + 1.5, 72.0, z), new Vec3(x - 0.5, 72.0, z),
                new Vec3(x - 2.5, 72.0, z), new Vec3(x - 6.5, 72.0, z)};
        ServerPlayer player = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, way[0]);
        BlockHitResult touch = new BlockHitResult(Vec3.atCenterOf(wicket), Direction.EAST, wicket, false);
        int[] order = {2, 3, 4, 3, 2, 1, 0};
        var sequence = helper.startSequence()
                .thenWaitUntil(() -> assertDown(helper, gates, EAST))
                .thenIdle(5)
                .thenExecute(() -> step(helper, player, way[1], way[1], "the soul in the wicket's way"))
                .thenIdle(1)
                .thenExecute(() -> step(helper, player, way[2], way[1], "the soul stepping through the shut wicket"))
                .thenIdle(5)
                .thenExecute(() -> {
                    InteractionResult used = player.gameMode.useItemOn(player, soul, player.getMainHandItem(),
                            InteractionHand.MAIN_HAND, touch);
                    BlockState lower = soul.getBlockState(wicket), upper = soul.getBlockState(wicket.above());
                    helper.assertTrue(used.consumesAction() && lower.is(ModBlocks.WICKET.get())
                                    && lower.getValue(DoorBlock.OPEN) && upper.is(ModBlocks.WICKET.get())
                                    && upper.getValue(DoorBlock.OPEN),
                            "opening the wicket gave " + used + ", and it is " + lower + " under " + upper);
                })
                .thenIdle(1);
        for (int each : order) {
            sequence = sequence.thenExecute(() -> step(helper, player, way[each], way[each],
                    "the soul stepping through the open wicket")).thenIdle(1);
        }
        sequence.thenExecute(() -> {
                    assertGate(helper, soul, EAST, 0, "after the walk");
                    player.gameMode.useItemOn(player, soul, player.getMainHandItem(), InteractionHand.MAIN_HAND, touch);
                    BlockState lower = soul.getBlockState(wicket), upper = soul.getBlockState(wicket.above());
                    helper.assertTrue(!lower.getValue(DoorBlock.OPEN) && !upper.getValue(DoorBlock.OPEN),
                            "the wicket shut again is " + lower + " under " + upper);
                })
                .thenSucceed();
    }

    /** Shuts the wicket here if a test left it open, so that the houses' batch finds it as laid. */
    private static void shut(ServerLevel soul, BlockPos wicket) {
        for (BlockPos half : List.of(wicket, wicket.above())) {
            BlockState state = soul.getBlockState(half);
            if (state.is(ModBlocks.WICKET.get()) && state.getValue(DoorBlock.OPEN)) {
                soul.setBlock(half, state.setValue(DoorBlock.OPEN, false), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ---- The place line ----

    /**
     * In each gate's gap below the wall's top, in the doorway, at its inner face, in the wicket's way and at the top row,
     * the place line names the gate, each gate a place of its own; on the roof over it and in the wall beside it, the
     * Seireiheki, as before; inside Seireitei and out in the Rukongai, the column alone says, as before.
     */
    @GameTest(template = "empty")
    public static void thePlaceLineNamesTheGateInItsGap(GameTestHelper helper) {
        Set<Integer> places = new HashSet<>();
        for (Rukongai.Region region : Rukongai.Region.values()) {
            int gate = region.ordinal();
            SeireiteiPlan.Site site = GateHouses.STAMP.sites().get(gate);
            BlockPos corner = new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z());
            BlockPos doorway = GateState.columns(gate).get(5);
            BlockPos face = GateHouses.STAMP.turned(gate, new BlockPos(SeireiteiPlan.GATE_HALF_WIDTH, 0, 0), corner);
            BlockPos top = doorway.atY(SeireiteiPlan.WALL_TOP_Y - 1), roof = doorway.atY(SeireiteiPlan.WALL_TOP_Y);
            BlockPos wall = GateHouses.STAMP.turned(gate, new BlockPos(GateHouses.SIZE.getX(), 18, SeireiteiPlan.GATE_ROW),
                    corner);
            for (BlockPos at : List.of(doorway, face, GateState.wicket(gate), top)) {
                int place = Rukongai.place(at.getX(), at.getY(), at.getZ());
                String told = key(Rukongai.placeName(at.getX(), at.getY(), at.getZ()));
                helper.assertTrue(told.equals(GATE_KEYS[gate]) && place == Rukongai.GATEWAY - gate,
                        at.toShortString() + " is told as " + told + ", place " + place);
                places.add(place);
            }
            for (BlockPos at : List.of(roof, wall)) {
                int place = Rukongai.place(at.getX(), at.getY(), at.getZ());
                String told = key(Rukongai.placeName(at.getX(), at.getY(), at.getZ()));
                helper.assertTrue(told.equals(SEIREIHEKI_KEY) && place == Rukongai.WALL,
                        at.toShortString() + " is told as " + told + ", place " + place);
            }
        }
        helper.assertTrue(places.size() == GATE_KEYS.length && !places.contains(Rukongai.NOWHERE)
                && places.stream().allMatch(place -> place < Rukongai.SEIREITEI), "the gates' places are " + places);
        for (int[] column : new int[][]{{100, 100}, {0, 540}, {-600, 20}}) {
            int x = column[0], z = column[1];
            helper.assertTrue(Rukongai.place(x, SeireiteiPlan.PLATEAU_Y, z) == Rukongai.place(x, z)
                            && key(Rukongai.placeName(x, SeireiteiPlan.PLATEAU_Y, z)).equals(key(Rukongai.placeName(x, z))),
                    "(" + x + ", " + z + ") is told otherwise by its height");
        }
        helper.succeed();
    }

    // ---- The blocks ----

    /**
     * Asserts that this block, registered under this name with this item, is as hard as Sekkiseki: broken by no tool in
     * survival or blast, moved by no piston, spawned on by nothing, dropping nothing, and no Wither's, dragon's or feature's.
     */
    private static void assertAsHard(GameTestHelper helper, Block block, String name, Item item) {
        ServerLevel level = helper.getLevel();
        BlockState state = block.defaultBlockState();
        helper.assertTrue(BuiltInRegistries.BLOCK.get(BleachMod.id(name)) == block && block.asItem() == item,
                name + " is not its block, or has not its item");
        helper.assertTrue(block.defaultDestroyTime() < 0.0F && block.getExplosionResistance() >= 3_600_000.0F,
                name + " can be broken in survival or by a blast");
        helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK && !PistonBaseBlock.isPushable(state, level,
                helper.absolutePos(new BlockPos(1, 20, 1)), Direction.EAST, true, Direction.EAST), name + " can be pushed");
        helper.assertFalse(state.isValidSpawn(level, BlockPos.ZERO, EntityType.ZOMBIE), "a zombie may spawn on " + name);
        helper.assertTrue(block.getLootTable().equals(BuiltInLootTables.EMPTY) && !WitherBoss.canDestroy(state),
                name + " drops " + block.getLootTable().location() + ", or a Wither may break it");
        for (TagKey<Block> tag : List.of(BlockTags.WITHER_IMMUNE, BlockTags.DRAGON_IMMUNE,
                BlockTags.FEATURES_CANNOT_REPLACE)) {
            helper.assertTrue(state.is(tag), name + " is not in #" + tag.location());
        }
    }

    /** The great gate as registered: as hard as Sekkiseki, and undoing reiryoku as the wall does. */
    @GameTest(template = "empty")
    public static void theGreatGateIsRegistered(GameTestHelper helper) {
        assertAsHard(helper, ModBlocks.GREAT_GATE.get(), "great_gate", ModItems.GREAT_GATE.get());
        helper.assertTrue(ModBlocks.GREAT_GATE.get().defaultBlockState().is(BleachTags.NEGATES_REIRYOKU),
                "the great gate does not undo reiryoku");
        helper.succeed();
    }

    /**
     * The wicket as registered: as hard as Sekkiseki but not undoing reiryoku, and a wooden door any soul opens by hand,
     * in no door tag, that no mob may break and that stands on air.
     */
    @GameTest(template = "empty")
    public static void theWicketIsRegistered(GameTestHelper helper) {
        Block wicket = ModBlocks.WICKET.get();
        assertAsHard(helper, wicket, "wicket", ModItems.WICKET.get());
        ServerLevel level = helper.getLevel();
        BlockState door = wicket.defaultBlockState();
        BlockPos high = helper.absolutePos(new BlockPos(1, 20, 1));
        Entity zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(!door.is(BleachTags.NEGATES_REIRYOKU) && !door.is(BlockTags.DOORS)
                && !door.is(BlockTags.WOODEN_DOORS), "the wicket is in the wrong tags");
        helper.assertTrue(wicket instanceof DoorBlock hung && hung.type() == BlockSetType.DARK_OAK
                        && DoorBlock.isWoodenDoor(door) && !door.canEntityDestroy(level, high, zombie)
                        && level.getBlockState(high.below()).isAir() && door.canSurvive(level, high),
                "the wicket is not a wooden door a zombie may not break, standing on air");
        helper.succeed();
    }
}
