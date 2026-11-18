package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The great gates of the four gate houses, in Soul Society's saved data: how many rows each stands above its doorway's
 * floor, from 0 (down) to {@link SeireiteiPlan#GATE_LIFT} (lifted), and which of the two it is moving to. Each gate is
 * laid from this, never by the template, once its house is seen standing and again after every start, so that its cells
 * come to match the height saved (server thread only).
 */
public final class GateState extends SavedData {
    public static final String NAME = "bleach_gates";
    /** Ticks a gate takes over each row, and between looks at one not yet laid or whose chunks are not loaded. */
    public static final int STEP_TICKS = 5, LOOK_TICKS = 20;
    /** The gate's rows, its doorway's from floor to head; it can stand in these and the slot's above. */
    public static final int ROWS = SeireiteiPlan.GATE_DOOR_TOP_Y - SeireiteiPlan.PLATEAU_Y;
    private static final int LIFT = SeireiteiPlan.GATE_LIFT;
    private static final int GATES = GateHouses.STAMP.sites().size();
    /** In a house's template before its turn, in the gate's row: the doorway's first and last columns. */
    private static final int FIRST_X = SeireiteiPlan.GATE_HALF_WIDTH - SeireiteiPlan.GATE_DOOR_HALF_WIDTH,
            LAST_X = SeireiteiPlan.GATE_HALF_WIDTH + SeireiteiPlan.GATE_DOOR_HALF_WIDTH - 1;
    private static final String LIST = "Gates", HEIGHT = "Height", TARGET = "Target";
    private static final SavedData.Factory<GateState> FACTORY =
            new SavedData.Factory<>(GateState::new, GateState::load, null);
    /** Each gate's columns at the plateau across its doorway, as the stamp turns them. */
    private static final BlockPos[][] COLUMNS = new BlockPos[GATES][];
    /** Each gate's box: every cell it fills down or lifted. */
    private static final BoundingBox[] BOXES = new BoundingBox[GATES];

    static {
        for (int gate = 0; gate < GATES; gate++) {
            SeireiteiPlan.Site site = GateHouses.STAMP.sites().get(gate);
            BlockPos corner = new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z());
            COLUMNS[gate] = new BlockPos[LAST_X - FIRST_X + 1];
            for (int x = FIRST_X; x <= LAST_X; x++) {
                COLUMNS[gate][x - FIRST_X] =
                        GateHouses.STAMP.turned(gate, new BlockPos(x, 0, SeireiteiPlan.GATE_ROW), corner);
            }
            BOXES[gate] = BoundingBox.fromCorners(COLUMNS[gate][0], COLUMNS[gate][LAST_X - FIRST_X].above(ROWS + LIFT - 1));
        }
    }

    private final int[] height = new int[GATES], target = new int[GATES];
    /** Not saved: whether this start has laid the gate, whether its last step was held up, and when it is next looked at. */
    private final boolean[] laid = new boolean[GATES], held = new boolean[GATES];
    private final int[] nextAt = new int[GATES];

    /** Where a gate stands and what it is doing: down, lifted, rising, falling, held up, or not laid since the start. */
    public enum Status {
        DOWN, LIFTED, RISING, FALLING, WAITING, NOT_LAID
    }

    /** The gates of Soul Society, this level, made down on first asking. */
    public static GateState get(ServerLevel soulSociety) {
        return soulSociety.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The gates as saved: a height out of range is brought within it, and a target that is not the lift is down. */
    public static GateState load(CompoundTag tag, HolderLookup.Provider registries) {
        GateState state = new GateState();
        ListTag gates = tag.getList(LIST, Tag.TAG_COMPOUND);
        for (int gate = 0; gate < Math.min(GATES, gates.size()); gate++) {
            CompoundTag each = gates.getCompound(gate);
            state.height[gate] = Mth.clamp(each.getInt(HEIGHT), 0, LIFT);
            state.target[gate] = each.getInt(TARGET) == LIFT ? LIFT : 0;
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag gates = new ListTag();
        for (int gate = 0; gate < GATES; gate++) {
            CompoundTag each = new CompoundTag();
            each.putInt(HEIGHT, height[gate]);
            each.putInt(TARGET, target[gate]);
            gates.add(each);
        }
        tag.put(LIST, gates);
        return tag;
    }

    /** How many rows this gate stands above its doorway's floor. */
    public int height(int gate) {
        return height[gate];
    }

    /** The height this gate is moving to, or stands at: 0 or the lift. */
    public int target(int gate) {
        return target[gate];
    }

    /** Whether this gate's blocks have been laid since the server started, so they stand as its height says. */
    public boolean laid(int gate) {
        return laid[gate];
    }

    /** Where this gate (0 to 3, in the plan's order) stands and what it is doing. */
    public Status status(int gate) {
        if (!laid[gate]) {
            return Status.NOT_LAID;
        }
        if (height[gate] == target[gate]) {
            return height[gate] == 0 ? Status.DOWN : Status.LIFTED;
        }
        return held[gate] ? Status.WAITING : target[gate] > height[gate] ? Status.RISING : Status.FALLING;
    }

    /**
     * Sets this gate (0 to 3, in the plan's order) rising to its lift, which no body stops, with a sound if it moves now.
     * The command (7j4) and the keepers (7j5) lift and drop a gate by these two calls.
     *
     * @return whether it was not already lifted or rising
     */
    public boolean lift(ServerLevel level, int gate) {
        return moveTo(level, gate, LIFT);
    }

    /**
     * Sets this gate (0 to 3, in the plan's order) falling to close its doorway, holding while a body is under it, with a
     * sound if it moves now.
     *
     * @return whether it was not already down or falling
     */
    public boolean drop(ServerLevel level, int gate) {
        return moveTo(level, gate, 0);
    }

    private boolean moveTo(ServerLevel level, int gate, int to) {
        if (target[gate] == to) {
            return false;
        }
        target[gate] = to;
        held[gate] = false;
        setDirty();
        if (laid[gate]) {
            nextAt[gate] = level.getServer().getTickCount() + STEP_TICKS;
            if (height[gate] != to) {
                sound(level, gate, SoundEvents.PISTON_EXTEND);
            }
        }
        return true;
    }

    /** A part of a house was just placed over these cells: each gate it meets is laid again at its next look. */
    public void forget(BoundingBox part) {
        for (int gate = 0; gate < GATES; gate++) {
            if (BOXES[gate].intersects(part)) {
                laid[gate] = false;
                held[gate] = false;
                nextAt[gate] = 0;
            }
        }
    }

    /** A chunk of Soul Society has loaded: each gate not yet laid whose house covers it is looked at on the next tick. */
    public void wake(ChunkPos pos) {
        for (int gate = 0; gate < GATES; gate++) {
            if (!laid[gate] && GateHouses.STAMP.covered(gate).contains(pos)) {
                nextAt[gate] = 0;
            }
        }
    }

    /** This gate's columns at the plateau, across its doorway in turn. Public for game tests. */
    public static List<BlockPos> columns(int gate) {
        return List.of(COLUMNS[gate]);
    }

    /** Each tick, the gates due a look: one not laid tries to be, one moving steps. A still gate costs a comparison. */
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(BleachWorlds.SOUL_SOCIETY);
        if (level != null) {
            get(level).tick(level);
        }
    }

    private void tick(ServerLevel level) {
        int now = level.getServer().getTickCount();
        for (int gate = 0; gate < GATES; gate++) {
            if (now < nextAt[gate]) {
                continue;
            }
            if (!laid[gate]) {
                nextAt[gate] = now + (lay(level, gate) ? STEP_TICKS : LOOK_TICKS);
            } else if (height[gate] == target[gate]) {
                nextAt[gate] = Integer.MAX_VALUE;
            } else if (!loaded(level, gate)) {
                nextAt[gate] = now + LOOK_TICKS;
            } else {
                nextAt[gate] = now + STEP_TICKS;
                step(level, gate);
            }
        }
    }

    /**
     * Lays this gate at its height, if its house stands in loaded chunks, and says whether it did: the gate's block over
     * whatever stands in its rows, breaking what can be broken with its drops, and none of it in the rest. Any soul it
     * buries is then moved out.
     */
    private boolean lay(ServerLevel level, int gate) {
        SeireiteiPlan.Site site = GateHouses.STAMP.sites().get(gate);
        if (!GateHouses.STAMP.stands(level, gate, new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z()))) {
            return false;
        }
        BlockState block = ModBlocks.GREAT_GATE.get().defaultBlockState();
        for (int row = 0; row < ROWS + LIFT; row++) {
            boolean in = row >= height[gate] && row < height[gate] + ROWS;
            for (BlockPos column : COLUMNS[gate]) {
                BlockPos cell = column.above(row);
                if (in) {
                    fill(level, cell, block);
                } else if (level.getBlockState(cell).is(block.getBlock())) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        laid[gate] = true;
        held[gate] = false;
        GateHouses.freeAll(level, BOXES[gate]);
        return true;
    }

    /**
     * Moves this gate a row, into the row past its leading edge, breaking what stands there with its drops, and out of the
     * row at its trailing edge, unless a block there cannot be broken or, falling, a body is in it. A lift moves out any
     * soul it buries.
     */
    private void step(ServerLevel level, int gate) {
        boolean up = target[gate] > height[gate];
        int into = up ? height[gate] + ROWS : height[gate] - 1, left = up ? height[gate] : height[gate] + ROWS - 1;
        held[gate] = !up && occupied(level, gate, into) || blocked(level, gate, into);
        if (held[gate]) {
            return;
        }
        BlockState block = ModBlocks.GREAT_GATE.get().defaultBlockState();
        for (BlockPos column : COLUMNS[gate]) {
            fill(level, column.above(into), block);
            level.setBlock(column.above(left), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        height[gate] += up ? 1 : -1;
        setDirty();
        if (up) {
            GateHouses.freeAll(level, row(gate, into));
        }
        if (height[gate] == target[gate]) {
            sound(level, gate, SoundEvents.ANVIL_LAND);
        }
    }

    /** Sets this block here, first breaking with its drops anything else that can be broken. */
    private static void fill(ServerLevel level, BlockPos cell, BlockState block) {
        BlockState there = level.getBlockState(cell);
        if (!there.isAir() && !there.is(block.getBlock()) && there.getDestroySpeed(level, cell) >= 0.0F) {
            level.destroyBlock(cell, true, null);
        }
        level.setBlock(cell, block, Block.UPDATE_ALL);
    }

    /** Whether the house's chunks, which hold every cell of the gate and of what its blocks touch, are loaded. */
    private static boolean loaded(ServerLevel level, int gate) {
        for (ChunkPos pos : GateHouses.STAMP.covered(gate)) {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                return false;
            }
        }
        return true;
    }

    /** This row of the gate's cells, across its doorway. */
    private static BoundingBox row(int gate, int row) {
        return BoundingBox.fromCorners(COLUMNS[gate][0].above(row), COLUMNS[gate][COLUMNS[gate].length - 1].above(row));
    }

    /** Whether a body is in this row of the gate's: a living one not a spectator, or anything such a one rides. */
    private static boolean occupied(ServerLevel level, int gate, int row) {
        return !level.getEntitiesOfClass(Entity.class, AABB.of(row(gate, row)), GateState::holdsUp).isEmpty();
    }

    private static boolean holdsUp(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.isAlive() && !living.isSpectator();
        }
        for (Entity rider : entity.getIndirectPassengers()) {
            if (rider instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block in this row of the gate's cannot be broken, so the gate cannot move into it. */
    private static boolean blocked(ServerLevel level, int gate, int row) {
        for (BlockPos column : COLUMNS[gate]) {
            BlockPos cell = column.above(row);
            BlockState there = level.getBlockState(cell);
            if (!there.isAir() && !there.is(ModBlocks.GREAT_GATE.get()) && there.getDestroySpeed(level, cell) < 0.0F) {
                return true;
            }
        }
        return false;
    }

    /** A sound from the middle of the gate where it stands, heard far off as a great gate's should be. */
    private void sound(ServerLevel level, int gate, SoundEvent sound) {
        BlockPos middle = COLUMNS[gate][COLUMNS[gate].length / 2].above(height[gate] + ROWS / 2);
        level.playSound(null, middle, sound, SoundSource.BLOCKS, 4.0F, 0.5F);
    }
}
