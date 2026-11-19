package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.KisukeUrahara;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Urahara Shop, {@code bleach:urahara_shop}: one in each Overworld made with structures on, at the one spot its ring of
 * one gives, roughly 170 to 870 blocks from the world's centre and never on it, on dry, gentle land where the world has
 * land near that spot; a default flat world has no ring, so no shop. Worldgen builds it in chunks made since and a stamp
 * in chunks made before, unless players have lived there or the land will not hold it, when it is given up and logged once.
 */
public final class UraharaShop {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("urahara_shop"));
    public static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("urahara_shop"));
    public static final ResourceLocation TEMPLATE = BleachMod.id("urahara_shop");
    /** The template before its turn: 13 wide, 13 high and 16 deep, its open front at z 0. */
    public static final Vec3i SIZE = new Vec3i(13, 13, 16);
    /** Where Kisuke Urahara stands in the template before its turn: behind the counter, facing the door. */
    public static final BlockPos KEEPER = new BlockPos(4, 1, 9);
    /** Before the doorway, outside the template: where a creature the stamp builds the shop over is set down. */
    public static final BlockPos DOOR = new BlockPos(6, 0, -1);
    /**
     * A chunk players have spent this long near in all, by the game's own count of ticks with a player within 128 blocks
     * of it over every visit, has been lived in, and the stamp does not build the shop over it: a minute.
     */
    public static final long LIVED_IN_TICKS = 1200L;
    /** How far the land under a stamped shop may lie below or above the level its floor rests on. */
    private static final int GROUND_STEP = 6;
    /** This server's shop: its Overworld, its stamp (none where there is no shop to build) and its mark. */
    @Nullable
    private static Plan plan;

    private UraharaShop() {
    }

    /** The ring's one spot for the shop in this world, or null where its set is not placed, as in a default flat world. */
    @Nullable
    public static ChunkPos spot(ServerLevel overworld, ChunkGeneratorStructureState state) {
        StructureSet set = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET);
        // Rings are kept by their placement itself, so it must be the registry's own.
        if (set == null || !(set.placement() instanceof ConcentricRingsStructurePlacement rings)) {
            return null;
        }
        List<ChunkPos> spots = state.getRingPositionsFor(rings);
        return spots == null || spots.isEmpty() ? null : spots.get(0);
    }

    /**
     * The shop's site at this spot, as worldgen lays it out there: at the chunk's corner, turned and set at its height;
     * null if none lays out.
     */
    @Nullable
    public static SeireiteiPlan.Site site(ServerLevel overworld, ChunkPos spot) {
        Structure laid = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        if (laid == null) {
            return null;
        }
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        StructureStart start = laid.generate(overworld.registryAccess(), generator, generator.getBiomeSource(),
                overworld.getChunkSource().randomState(), overworld.getStructureManager(), overworld.getSeed(), spot, 0,
                overworld, biome -> true);
        if (!start.isValid() || !(start.getPieces().get(0) instanceof PoolElementStructurePiece piece)) {
            return null;
        }
        BlockPos corner = piece.getPosition();
        return new SeireiteiPlan.Site("urahara_shop", corner.getX(), corner.getZ(), TEMPLATE, piece.getRotation(),
                SIZE.getX(), SIZE.getZ(), corner.getY());
    }

    /** The stamp for the shop at this site, keeping its choice in this mark and telling a refusal to this line. */
    public static SiteStamp stamp(SeireiteiPlan.Site site, Mark mark, Consumer<String> warn) {
        BlockPos corner = new BlockPos(site.x(), site.y(), site.z());
        BoundingBox box = BoundingBox.fromCorners(corner,
                corner.offset(new BlockPos(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1).rotate(site.rotation())));
        return new SiteStamp(Level.OVERWORLD, STRUCTURE, "The Urahara Shop", List.of(site), SIZE, List.of(),
                (level, part) -> placed(level, part, box, site),
                (level, bare) -> mayBuild(level, site, box, mark, warn, bare));
    }

    /**
     * Whether the shop may be built over these chunks of its site still bare, none when it stands whole: not where one has
     * been lived in or the land will not hold it. The first answer is marked and kept, and a refusal logged once.
     */
    private static boolean mayBuild(ServerLevel level, SeireiteiPlan.Site site, BoundingBox box, Mark mark,
                                    Consumer<String> warn, List<? extends ChunkAccess> bare) {
        if (mark.decided()) {
            return mark.built();
        }
        long most = bare.stream().mapToLong(ChunkAccess::getInhabitedTime).max().orElse(0L);
        String why = most >= LIVED_IN_TICKS ? "players have spent " + most + " ticks near where it would stand"
                : badGround(level, site, box, bare);
        if (why != null) {
            warn.accept(String.format(Locale.ROOT, "The Urahara Shop was not built in chunk %s, at %d, %d: %s. An operator"
                    + " can build it with /place structure %s %d ~ %d, here or at another chunk", site.chunk(), site.x(),
                    site.z(), why, STRUCTURE.location(), site.x(), site.z()));
        }
        mark.decide(why == null);
        return why == null;
    }

    /**
     * Why the land at this site will not hold the shop, or null if it will: at its box's corners and centre in these chunks,
     * the first block down past air, logs, leaves, plants, bamboo, huge mushrooms, ice and snow must be dry and within 6 of
     * where its floor rests.
     */
    @Nullable
    private static String badGround(ServerLevel level, SeireiteiPlan.Site site, BoundingBox box,
                                    List<? extends ChunkAccess> bare) {
        int rests = site.y() - 1;
        int lowest = Math.max(level.getMinBuildHeight(), rests - GROUND_STEP);
        for (BlockPos column : List.of(new BlockPos(box.minX(), 0, box.minZ()), new BlockPos(box.maxX(), 0, box.minZ()),
                new BlockPos(box.minX(), 0, box.maxZ()), new BlockPos(box.maxX(), 0, box.maxZ()), box.getCenter())) {
            // Only where the shop is still to be built, so a part worldgen made is never read as land.
            if (bare.stream().noneMatch(chunk -> chunk.getPos().equals(new ChunkPos(column)))) {
                continue;
            }
            // A loaded chunk's heightmaps are its own, so this reads the land as it stands.
            BlockPos.MutableBlockPos at = column.mutable()
                    .setY(level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1);
            while (at.getY() >= lowest && open(level.getBlockState(at))) {
                at.move(Direction.DOWN);
            }
            String where = " at " + column.getX() + ", " + column.getZ();
            if (at.getY() < lowest) {
                return "there is no land within " + GROUND_STEP + " blocks below the level its floor rests on" + where;
            } else if (!level.getBlockState(at).getFluidState().isEmpty()) {
                return "it would stand over water or lava" + where;
            } else if (at.getY() > rests + GROUND_STEP) {
                return "the land rises more than " + GROUND_STEP + " blocks above the level its floor rests on" + where;
            }
        }
        return null;
    }

    /**
     * Whether the walk down to the land goes on past this: air, a log, leaves or a plant a tree grows through, bamboo or
     * a huge mushroom, which stand taller than the land may rise, all dry, or ice, a lily pad or snow, which may lie over
     * water.
     */
    static boolean open(BlockState state) {
        return state.getFluidState().isEmpty() && (state.isAir() || state.is(BlockTags.LOGS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(BlockTags.ICE) || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.SNOW));
    }

    /**
     * An Overworld chunk has loaded: the stamp looks at it, unless this world has no shop to build or chose already. The
     * load posts this on the server thread inside itself, so the plan is made in a task queued after it.
     */
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
            here.stamp().onServerTick(event);
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

    /** Moves each creature but the keeper that this part of the shop now buries, with what it rides, out before its door. */
    private static void placed(ServerLevel level, BoundingBox part, BoundingBox box, SeireiteiPlan.Site site) {
        BlockPos door = new BlockPos(site.x(), site.y(), site.z()).offset(DOOR.rotate(site.rotation()));
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, AABB.of(box).intersect(AABB.of(part)))) {
            if (!(living instanceof KisukeUrahara) && !living.isRemoved() && !level.noCollision(living)) {
                living.getRootVehicle().teleportTo(door.getX() + 0.5, level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door.getX(), door.getZ()), door.getZ() + 0.5);
            }
        }
    }

    /** This server's shop in its Overworld: no stamp with structures off, with no ring, or once its choice is made. */
    private record Plan(ServerLevel level, @Nullable SiteStamp stamp, Mark mark) {
        /** The plan for this Overworld, from its saved mark, its ring's spot and the shop's site there. */
        static Plan of(ServerLevel overworld) {
            Mark mark = Mark.get(overworld);
            if (mark.decided() || !overworld.getServer().getWorldData().worldGenOptions().generateStructures()) {
                return new Plan(overworld, null, mark);
            }
            ChunkPos spot = spot(overworld, overworld.getChunkSource().getGeneratorState());
            SeireiteiPlan.Site site = spot == null ? null : site(overworld, spot);
            // Named in full: the record's own stamp() would hide it.
            return new Plan(overworld, site == null ? null : UraharaShop.stamp(site, mark, BleachMod.LOGGER::warn), mark);
        }

        /** Hands this chunk's load to the stamp while the shop is neither built nor given up. */
        void look(ChunkEvent.Load event) {
            if (stamp != null && !mark.decided()) {
                stamp.onChunkLoad(event);
            }
        }
    }

    /** What a stamp chose for the shop or the room under it, saved with the Overworld: not yet asked, built, or given up. */
    public static final class Mark extends SavedData {
        private static final String NAME = "bleach_urahara_shop", STATE = "State";
        private static final int UNDECIDED = 0, BUILT = 1, GIVEN_UP = 2;
        private static final SavedData.Factory<Mark> FACTORY = new SavedData.Factory<>(Mark::new, Mark::load, null);
        private int state = UNDECIDED;

        /** This world's mark for its shop, made undecided on first asking. */
        static Mark get(ServerLevel overworld) {
            return get(overworld, NAME);
        }

        /** This world's mark saved under this name, made undecided on first asking. */
        static Mark get(ServerLevel overworld, String name) {
            return overworld.getDataStorage().computeIfAbsent(FACTORY, name);
        }

        /** The mark as saved; a value out of range is undecided. */
        private static Mark load(CompoundTag tag, HolderLookup.Provider registries) {
            Mark mark = new Mark();
            int saved = tag.getInt(STATE);
            mark.state = saved == BUILT || saved == GIVEN_UP ? saved : UNDECIDED;
            return mark;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt(STATE, state);
            return tag;
        }

        /** Whether the stamp has chosen, to build it or to give it up. */
        public boolean decided() {
            return state != UNDECIDED;
        }

        /** Whether the stamp built it, or found it standing whole. */
        public boolean built() {
            return state == BUILT;
        }

        /** Marks it built, or given up; the first choice stands. */
        void decide(boolean build) {
            if (!decided()) {
                state = build ? BUILT : GIVEN_UP;
                setDirty();
            }
        }
    }
}
