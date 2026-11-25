package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The training room under the Urahara Shop, {@code bleach:urahara_training_room}: a hall of rock whose floor lies 22 blocks
 * under the shop's, reached by a hatch behind the counter and a ladder, which no structure set lists. Once the shop stands at
 * its ring's spot and the chunks about the room are loaded, a stamp builds it under that shop, in worlds made before it too,
 * unless it would leave the world or its land, or players have built where it would go, when it is given up and logged once.
 */
public final class UraharaRoom {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("urahara_training_room"));
    public static final ResourceLocation TEMPLATE = BleachMod.id("urahara_training_room");
    /**
     * The room's rock and air before the shop's turn, from the shop's corner on its floor: from its floor 22 below the
     * shop's to its roof 9 below, over the shop's footprint grown alike on each side. The template's corner is the first.
     */
    public static final BlockPos ROOM_MIN = new BlockPos(-10, -22, -8), ROOM_MAX = new BlockPos(22, -9, 23);
    /** The shaft's rock round the ladder, from over the room's roof to under the shop's floor. */
    public static final BlockPos SHAFT_MIN = new BlockPos(1, -8, 8), SHAFT_MAX = new BlockPos(3, -1, 10);
    /** The hatch in the shop's floor behind the counter's west end, which the ladder runs down from. */
    public static final BlockPos HATCH = new BlockPos(2, 0, 9);
    /** The counter's west end, beside the hatch: with the floor under the hatch, how the stamp knows the shop stands. */
    private static final BlockPos COUNTER_END = new BlockPos(2, 1, 8);
    /** The land over the roof, two blocks at the least: no more than 6 below the level the shop's floor rests on. */
    private static final int COVER = 2;
    /** How far over the world's floor the room's lowest block keeps: the Overworld lays bedrock in its lowest 5 layers. */
    private static final int FLOOR = 5;
    /** How many further checks, one a tick, a chunk's load gives the room: 5 seconds, as a site's stamp gives. */
    private static final int LOAD_TRIES = 100;
    /** The name the room's mark is saved under with the Overworld. */
    private static final String MARK = "bleach_urahara_training_room";
    /**
     * What grows or forms in caves beyond the tags {@link #natural} reads: lichen, lush caves' plants, dripstone, geodes,
     * and the magma the floors of underground pools turn to.
     */
    private static final Set<Block> CAVE = Set.of(Blocks.GLOW_LICHEN, Blocks.MOSS_CARPET, Blocks.AZALEA,
            Blocks.FLOWERING_AZALEA, Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.SMALL_DRIPLEAF,
            Blocks.SPORE_BLOSSOM, Blocks.HANGING_ROOTS, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.VINE,
            Blocks.POINTED_DRIPSTONE, Blocks.DRIPSTONE_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST,
            Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD,
            Blocks.SMOOTH_BASALT, Blocks.OBSIDIAN, Blocks.MAGMA_BLOCK);
    /** This server's room: its Overworld, its stamp (none where there is no shop to build under) and its mark. */
    @Nullable
    private static Plan plan;

    private UraharaRoom() {
    }

    /** The room as its structure lays it out under the shop at this chunk, as {@code /place structure} does; or null. */
    @Nullable
    public static StructureStart startAt(ServerLevel overworld, ChunkPos chunk) {
        Structure laid = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        if (laid == null) {
            return null;
        }
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        StructureStart start = laid.generate(overworld.registryAccess(), generator, generator.getBiomeSource(),
                overworld.getChunkSource().randomState(), overworld.getStructureManager(), overworld.getSeed(), chunk, 0,
                overworld, biome -> true);
        return start.isValid() && start.getPieces().get(0) instanceof PoolElementStructurePiece ? start : null;
    }

    /**
     * Whether this is ground as the world makes it: air, lava, the rock, earth, sand, gravel and water caves cut through
     * (the carvers' tag holds water), ore, or what grows or forms in caves.
     */
    private static boolean natural(BlockState state) {
        return state.isAir() || state.is(Blocks.LAVA) || state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)
                || state.is(BlockTags.LUSH_GROUND_REPLACEABLE) || state.is(Tags.Blocks.ORES)
                || CAVE.contains(state.getBlock());
    }

    /** An Overworld chunk has loaded: the room's stamp looks at it, made in a task queued after the load. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            Plan here = plan;
            if (here != null && here.level() == level) {
                here.look(event);
            } else {
                MinecraftServer server = level.getServer();
                server.tell(new TickTask(server.getTickCount(), () -> plan(level).look(event)));
            }
        }
    }

    /** The further tries, one a tick, that a check asked for. */
    public static void onServerTick(ServerTickEvent.Post event) {
        Plan here = plan;
        if (here != null && here.stamp() != null && !here.mark().decided()) {
            here.stamp().onServerTick(event.getServer());
        }
    }

    /** A world closed: the next is planned afresh. */
    public static void onServerStopped(ServerStoppedEvent event) {
        plan = null;
    }

    /** This server's plan for this Overworld, made on first asking, and again for another Overworld. */
    private static Plan plan(ServerLevel overworld) {
        Plan here = plan;
        if (here == null || here.level() != overworld) {
            here = Plan.of(overworld);
            plan = here;
        }
        return here;
    }

    /**
     * The room under one shop, stamped as {@link SiteStamp} stamps a site: where it lies, the chunks it covers, what it
     * chose and the tries a load left it.
     */
    public static final class Stamp {
        private final StructureStart start;
        private final PoolElementStructurePiece piece;
        private final BlockPos corner;
        private final BoundingBox room, shaft;
        private final List<ChunkPos> covered, around;
        private final UraharaShop.Mark mark;
        private final BooleanSupplier shopBuilt;
        private final Consumer<String> warn;
        /** How many further checks, one a tick, are still to run; server thread only. */
        private int triesLeft;

        /**
         * The stamp for the room this start lays out, keeping its choice in this mark, asking whether the shop over it was
         * built there, and telling a refusal to this line.
         */
        public Stamp(StructureStart start, UraharaShop.Mark mark, BooleanSupplier shopBuilt, Consumer<String> warn) {
            this.start = start;
            this.piece = (PoolElementStructurePiece) start.getPieces().get(0);
            this.corner = piece.getPosition().subtract(ROOM_MIN.rotate(piece.getRotation()));
            this.room = BoundingBox.fromCorners(at(ROOM_MIN), at(ROOM_MAX));
            this.shaft = BoundingBox.fromCorners(at(SHAFT_MIN), at(SHAFT_MAX));
            this.covered = ChunkPos.rangeClosed(new ChunkPos(room.minX() >> 4, room.minZ() >> 4),
                    new ChunkPos(room.maxX() >> 4, room.maxZ() >> 4)).toList();
            this.around = covered.stream().flatMap(pos -> ChunkPos.rangeClosed(pos, 1)).distinct().toList();
            this.mark = mark;
            this.shopBuilt = shopBuilt;
            this.warn = warn;
        }

        /** Where this place of the shop's, before its turn and from its corner on its floor, lies in the world. */
        public BlockPos at(BlockPos place) {
            return corner.offset(place.rotate(piece.getRotation()));
        }

        /** The room's rock and air. */
        public BoundingBox room() {
            return room;
        }

        /** The chunks the room covers, across x and then z. */
        public List<ChunkPos> covered() {
            return covered;
        }

        /** How many further checks, one a tick, a load has left it. Public for tests. */
        public int triesLeft() {
            return triesLeft;
        }

        /** A chunk has loaded: if it is one the room covers or one beside them, the room is checked once the tick is done. */
        void onChunkLoad(ChunkEvent.Load event) {
            if (event.getLevel() instanceof ServerLevel level && around.contains(event.getChunk().getPos())) {
                queueCheck(level, true);
            }
        }

        /**
         * Queues this tick's further check, if a check left tries, counting one off, or with none left, every
         * {@link SiteStamp#SWEEP_TICKS}, one while the room's choice is still to make and its chunks and those beside them
         * are all loaded: a chunk made full again while it stays in memory fires no second load, and the shop over it may
         * be built after the tries ran out. Public for tests.
         */
        public void onServerTick(MinecraftServer server) {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) {
                return;
            }
            if (triesLeft > 0) {
                triesLeft--;
                queueCheck(level, false);
            } else if (server.getTickCount() % SiteStamp.SWEEP_TICKS == 0 && !mark.decided() && loaded(level)) {
                queueCheck(level, false);
            }
        }

        /** Checks once the server's current work is done: a chunk's load event comes inside the load itself. */
        private void queueCheck(ServerLevel level, boolean fromLoad) {
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount(), () -> ensureBuiltIfLoaded(level, fromLoad)));
        }

        /**
         * As {@link #ensureBuilt(ServerLevel)}, once the chunks the room covers and those beside them are loaded and the
         * creatures of those it covers have joined; a load gives tries that count down until the room's choice is made, so a
         * shop the same load builds is found. Public for tests.
         */
        public void ensureBuiltIfLoaded(ServerLevel level, boolean fromLoad) {
            if (!loaded(level)) {
                // The last of them to load checks again, or the sweep, once one loaded before is made full again.
                if (fromLoad) {
                    triesLeft = LOAD_TRIES;
                }
                return;
            }
            if (!covered.stream().allMatch(pos -> level.areEntitiesLoaded(pos.toLong()))) {
                // Read off the thread, saved creatures may join many ticks after their chunk, and no event says when.
                triesLeft = LOAD_TRIES;
                return;
            }
            ensureBuilt(level);
            if (mark.decided()) {
                triesLeft = 0;
            } else if (fromLoad) {
                // No shop yet: the shop's own stamp, asked by this load too, may build it within the tries.
                triesLeft = LOAD_TRIES;
            }
        }

        /** Whether every chunk the room covers, and every one beside them, is loaded. */
        private boolean loaded(ServerLevel level) {
            return around.stream().allMatch(pos -> level.getChunkSource().getChunkNow(pos.x, pos.z) != null);
        }

        /**
         * Builds the room, all at once, under the shop standing over it, unless its choice is made; a room standing already
         * is only marked built, and where no shop stands nothing is chosen unless the shop was built there. Server thread.
         *
         * @return whether the room was built
         */
        public boolean ensureBuilt(ServerLevel level) {
            if (mark.decided()) {
                return false;
            }
            covered.forEach(pos -> level.getChunk(pos.x, pos.z));
            BlockPos hatch = at(HATCH);
            if (level.getBlockState(hatch).is(Blocks.SPRUCE_TRAPDOOR)
                    && level.getBlockState(hatch.below()).is(Blocks.LADDER)) {
                // Laid already, as an operator's /place lays it.
                mark.decide(true);
                return false;
            }
            boolean shop = level.getBlockState(hatch).is(Blocks.SPRUCE_PLANKS)
                    && level.getBlockState(at(COUNTER_END)).is(Blocks.STRIPPED_SPRUCE_LOG);
            if (!shop && !shopBuilt.getAsBoolean()) {
                return false;
            }
            String unfit = unfit(level);
            String why = unfit != null ? unfit
                    : shop ? whyNot(level) : "the shop's floor where its hatch would open is not as the shop was built";
            if (why != null) {
                warn.accept(String.format(Locale.ROOT, "The training room under the Urahara Shop was not built in chunk %s,"
                        + " at %d, %d: %s. %s", new ChunkPos(corner), corner.getX(), corner.getZ(), why, unfit != null
                        ? "No room fits under the shop there" : "An operator can build it under the shop there with"
                        + " /place structure " + STRUCTURE.location() + " " + corner.getX() + " ~ " + corner.getZ()));
                mark.decide(false);
                return false;
            }
            // Chosen before anything is set, so it is never laid twice.
            mark.decide(true);
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            for (ChunkPos pos : covered) {
                start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), new BoundingBox(
                        pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(), pos.getMaxBlockX(),
                        level.getMaxBuildHeight(), pos.getMaxBlockZ()), pos);
            }
            // What lived in the ground the room took, buried or not, is set down before the shop's door.
            BlockPos door = at(UraharaShop.DOOR);
            for (BoundingBox box : List.of(room, shaft)) {
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, AABB.of(box))) {
                    living.getRootVehicle().teleportTo(door.getX() + 0.5, level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door.getX(), door.getZ()), door.getZ() + 0.5);
                }
            }
            return true;
        }

        /** Why no room fits under this shop, or null: it would come within 5 blocks of the world's floor, or cut bedrock. */
        @Nullable
        private String unfit(ServerLevel level) {
            if (room.minY() < level.getMinBuildHeight() + FLOOR) {
                return "it would come within " + FLOOR + " blocks of the world's floor";
            }
            for (BlockPos pos : cells()) {
                if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                    return "it would cut into bedrock at " + pos.toShortString();
                }
            }
            return null;
        }

        /**
         * Why the room may not be built under the shop standing here, or null: players have spent a minute near it and
         * anything but ground as the world makes it stands where it would go, or the land over it lies more than 6 blocks
         * below the level the shop's floor rests on. Public for tests.
         */
        @Nullable
        public String whyNot(ServerLevel level) {
            long most = covered.stream().mapToLong(pos -> level.getChunk(pos.x, pos.z).getInhabitedTime()).max().orElse(0L);
            if (most >= UraharaShop.LIVED_IN_TICKS) {
                for (BlockPos pos : cells()) {
                    BlockState state = level.getBlockState(pos);
                    if (!natural(state)) {
                        return "players have spent " + most + " ticks near where it would go, and "
                                + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " stands at " + pos.toShortString();
                    }
                }
            }
            int lowest = room.maxY() + COVER;
            for (int x = room.minX(); x <= room.maxX(); x++) {
                for (int z = room.minZ(); z <= room.maxZ(); z++) {
                    BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x,
                            level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                    // Past what the shop's own walk passes, and water or lava, to the land under them.
                    while (at.getY() >= lowest && (UraharaShop.open(level.getBlockState(at))
                            || !level.getBlockState(at).getFluidState().isEmpty())) {
                        at.move(Direction.DOWN);
                    }
                    if (at.getY() < lowest) {
                        return "the land over it lies more than 6 blocks below the level the shop's floor rests on at "
                                + x + ", " + z;
                    }
                }
            }
            return null;
        }

        /**
         * Every cell the room sets but its hatch, its rock and air then its shaft's, each x fastest, then y, then z; each is
         * copied, since the stream holds a box's cells before the first is read and the box's walk moves one position.
         */
        private Iterable<BlockPos> cells() {
            return () -> Stream.of(room, shaft).flatMap(BlockPos::betweenClosedStream).map(BlockPos::immutable).iterator();
        }
    }

    /** This server's room in its Overworld: no stamp with structures off, no ring, the shop given up, or once it chose. */
    private record Plan(ServerLevel level, @Nullable Stamp stamp, UraharaShop.Mark mark) {
        /** The plan for this Overworld, from its saved marks and the room laid out under the ring's spot for the shop. */
        static Plan of(ServerLevel overworld) {
            UraharaShop.Mark mark = UraharaShop.Mark.get(overworld, MARK), shop = UraharaShop.Mark.get(overworld);
            // A shop given up has no room to build under it, until an operator builds both.
            if (mark.decided() || shop.decided() && !shop.built()
                    || !overworld.getServer().getWorldData().worldGenOptions().generateStructures()) {
                return new Plan(overworld, null, mark);
            }
            ChunkPos spot = UraharaShop.spot(overworld, overworld.getChunkSource().getGeneratorState());
            StructureStart start = spot == null ? null : startAt(overworld, spot);
            // The shop's mark as saved, read live, so a shop its stamp builds meanwhile counts.
            return new Plan(overworld, start == null ? null : new Stamp(start, mark, shop::built, BleachMod.LOGGER::warn),
                    mark);
        }

        /** Hands this chunk's load to the stamp while the room is neither built nor given up. */
        void look(ChunkEvent.Load event) {
            if (stamp != null && !mark.decided()) {
                stamp.onChunkLoad(event);
            }
        }
    }
}
