package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.world.GateHouses;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the great gates in the real Soul Society; those that work a gate run in a batch of their own, one gate each.
 * Every test that works a gate drops it, waits for it laid and down, and drops it again however it ends, so a gate left
 * falling by one batch is down before the next works it.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GateTests {
    private static final String BATCH = "gate_state";
    /** The gates by the plan's order, the Rukongai's regions'. */
    private static final int EAST = 0, SOUTH = 1, WEST = 2, NORTH = 3;
    private static final int LIFT = SeireiteiPlan.GATE_LIFT;

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

    /** Asserts that this gate stands so high, its block in exactly those of its cells. */
    static void assertGate(GameTestHelper helper, ServerLevel soul, int gate, int height, String when) {
        for (int row = 0; row < GateState.ROWS + LIFT; row++) {
            boolean in = row >= height && row < height + GateState.ROWS;
            for (BlockPos column : GateState.columns(gate)) {
                BlockState state = soul.getBlockState(column.above(row));
                helper.assertTrue(in ? state.is(ModBlocks.GREAT_GATE.get()) : state.isAir(), "gate " + gate + "'s cell "
                        + column.above(row).toShortString() + " is " + state + " " + when);
            }
        }
    }

    /** Drops this gate now, should a run cut short have left it lifted, and again when this hold's test ends. */
    static ShakonmakuTests.Cleanup dropped(ShakonmakuTests.Cleanup held, int gate) {
        GateState.get(held.soul).drop(held.soul, gate);
        held.atEnd(() -> GateState.get(held.soul).drop(held.soul, gate));
        return held;
    }

    /** A cow set down here in Soul Society, that neither walks nor falls, taken away when the test ends. */
    private static Cow cow(ShakonmakuTests.Cleanup held, Vec3 at) {
        Cow cow = EntityType.COW.create(held.soul);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return held.add(cow);
    }

    // ---- GateState ----

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
                    List<ItemEntity> items = soul.getEntitiesOfClass(ItemEntity.class, new AABB(dirt).inflate(3.0, 8.0, 3.0),
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
     * Hakutōmon's house with its gate's cells open, as a house stood before the gates, and a post broken, gains them when
     * laid again, a cow they bury on each half moved out of the gate's row on that half, and a lift frees a cow in the slot.
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
}
