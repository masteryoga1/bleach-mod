package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Sōkyoku Hill's works, the structure {@code bleach:seireitei_hill} at the plan's hill site: the Senzaikyū on the lawn
 * east of the hill, its bridge to the hill's cut-back east end, and the Sōkyoku's stand there. Old worlds have them
 * stamped as the gate houses are, and a soul they are built over is moved out beside them.
 */
public final class HillWorks {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("seireitei_hill"));
    /** The template before its turn: the hill's end to the tower's east face, the lawn to the crown, the tower's width. */
    public static final Vec3i SIZE = new Vec3i(53, 95, 12);
    /** A block in each chunk the works cover, before the turn: the stand's posts, the bridge's rails, the tower's foot. */
    public static final List<SiteStamp.Marker> MARKERS = List.of(
            new SiteStamp.Marker(new BlockPos(50, 64, 9), () -> Blocks.STRIPPED_DARK_OAK_LOG),
            new SiteStamp.Marker(new BlockPos(50, 64, 2), () -> Blocks.STRIPPED_DARK_OAK_LOG),
            new SiteStamp.Marker(new BlockPos(39, 64, 7), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(39, 64, 4), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(23, 64, 7), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(23, 64, 4), ModBlocks.WHITE_PLASTER),
            new SiteStamp.Marker(new BlockPos(6, 0, 11), ModBlocks.SEKKISEKI),
            new SiteStamp.Marker(new BlockPos(6, 0, 0), ModBlocks.SEKKISEKI));
    public static final SiteStamp STAMP = new SiteStamp(BleachWorlds.SOUL_SOCIETY, STRUCTURE,
            "The Senzaikyū and the Sōkyoku's stand", SeireiteiPlan.sites("hill"), SIZE, MARKERS, HillWorks::placed);

    private HillWorks() {
    }

    /** A chunk of the works, or one beside them, has loaded: see {@link SiteStamp#onChunkLoad}. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        STAMP.onChunkLoad(event);
    }

    /** The one further try a stamp's check asked for. */
    public static void onServerTick(ServerTickEvent.Post event) {
        STAMP.onServerTick(event);
    }

    /** Builds what was never placed of the works, as {@link SiteStamp#ensureBuilt} does. Public for game tests. */
    public static boolean ensureBuilt(ServerLevel level) {
        return STAMP.ensureBuilt(level, 0);
    }

    /**
     * Moves each soul or player the works now bury in this part, with what it rides, out past their nearer long side,
     * clear of it by half the width of the stack.
     */
    private static void placed(ServerLevel level, BoundingBox part) {
        BoundingBox works = STAMP.box(0, SeireiteiPlan.PLATEAU_Y);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, AABB.of(part).intersect(AABB.of(works)))) {
            if (!living.isRemoved() && !level.noCollision(living)) {
                Entity moved = living.getRootVehicle();
                AABB body = moved.getBoundingBox();
                for (Entity rider : moved.getIndirectPassengers()) {
                    body = body.minmax(rider.getBoundingBox());
                }
                double half = Math.max(0.5, Math.max(body.getXsize(), body.getZsize()) / 2.0 + 0.01);
                double z = moved.getZ() < works.getCenter().getZ() ? works.minZ() - half : works.maxZ() + 1 + half;
                int x = Mth.floor(moved.getX());
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, Mth.floor(z));
                moved.teleportTo(moved.getX(), y, z);
            }
        }
    }
}
