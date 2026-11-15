package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Seireiheki's four gate houses, the structure {@code bleach:seireitei_gate} at the plan's gate sites: each fills its
 * gap from the lawn to the wall's top, with one doorway through it. Old worlds have them stamped as the Academy is, and a
 * soul or player a house is built over is moved out of its blocks.
 */
public final class GateHouses {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("seireitei_gate"));
    /** A house's template before its turn: the gap's width, the lawn to the wall's top, and the wall's thickness. */
    public static final Vec3i SIZE = new Vec3i(2 * SeireiteiPlan.GATE_HALF_WIDTH,
            SeireiteiPlan.WALL_TOP_Y - SeireiteiPlan.PLATEAU_Y, SeireiteiPlan.WALL_THICKNESS);
    /** The doorway's two posts on the inner face, at the ground: one in each of the two chunks a house covers. */
    public static final BlockPos LEFT_POST = new BlockPos(
            SeireiteiPlan.GATE_HALF_WIDTH - SeireiteiPlan.GATE_DOOR_HALF_WIDTH - 1, 0, 0),
            RIGHT_POST = new BlockPos(SeireiteiPlan.GATE_HALF_WIDTH + SeireiteiPlan.GATE_DOOR_HALF_WIDTH, 0, 0);
    public static final SiteStamp STAMP = new SiteStamp(BleachWorlds.SOUL_SOCIETY, STRUCTURE,
            "A gate house of the Seireiheki", SeireiteiPlan.sites("gate"), SIZE,
            List.of(new SiteStamp.Marker(LEFT_POST, () -> Blocks.STRIPPED_DARK_OAK_LOG),
                    new SiteStamp.Marker(RIGHT_POST, () -> Blocks.STRIPPED_DARK_OAK_LOG)),
            GateHouses::freeAll);

    private GateHouses() {
    }

    /** Stamps a house whose chunks, and those beside them, have all loaded; see {@link SiteStamp#onChunkLoad}. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        STAMP.onChunkLoad(event);
    }

    /** The one further try a stamp's check asked for. */
    public static void onServerTick(ServerTickEvent.Post event) {
        STAMP.onServerTick(event);
    }

    /**
     * A soul or player joining Soul Society in a house's box, saved there before the house stood, is moved out of its
     * blocks once the tick is done. One joining anywhere else costs a few comparisons.
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living && nearGap(living)
                && event.getLevel() instanceof ServerLevel level && level.dimension() == BleachWorlds.SOUL_SOCIETY
                && house(living.getBoundingBox()) != null) {
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount(), () -> free(level, living)));
        }
    }

    /** Whether this body stands under the wall's top within a block of a gap's columns, by its feet and width alone. */
    private static boolean nearGap(Entity entity) {
        double x = Math.abs(entity.getX()), z = Math.abs(entity.getZ()), r = Math.sqrt(x * x + z * z);
        double pad = 1.0 + entity.getBbWidth() / 2.0;
        return entity.getY() < SeireiteiPlan.WALL_TOP_Y + 1 && Math.min(x, z) < SeireiteiPlan.GATE_HALF_WIDTH + pad
                && r > SeireiteiPlan.SEIREITEI_RADIUS - pad && r < SeireiteiPlan.WALL_OUTER_RADIUS + pad;
    }

    /** This house's box: its template turned on the lawn of its gap, where the noise lays the ground. */
    public static BoundingBox box(int index) {
        return STAMP.box(index, SeireiteiPlan.PLATEAU_Y);
    }

    /** The box of the house this box meets, or null. */
    @Nullable
    private static BoundingBox house(AABB at) {
        for (int i = 0; i < STAMP.sites().size(); i++) {
            if (AABB.of(box(i)).intersects(at)) {
                return box(i);
            }
        }
        return null;
    }

    /** Moves every soul or player in this part's column that a house's blocks now bury. */
    private static void freeAll(ServerLevel level, BoundingBox part) {
        level.getEntitiesOfClass(LivingEntity.class, AABB.of(part)).forEach(living -> free(level, living));
    }

    /**
     * If a house's blocks bury this soul or player, moves it with what it rides out on its own half of the gap: to the
     * doorway, or from the lawn's half to the roof if nearer, else, too big or blocked, to the ground beyond the house.
     */
    private static void free(ServerLevel level, LivingEntity living) {
        BoundingBox box = house(living.getBoundingBox());
        if (box == null || living.isRemoved() || living.level() != level || level.noCollision(living)) {
            return;
        }
        // A rider moved alone is set back on its seat by its vehicle's next tick, so the whole stack goes.
        Entity moved = living.getRootVehicle();
        AABB body = moved.getBoundingBox();
        for (Entity rider : moved.getIndirectPassengers()) {
            body = body.minmax(rider.getBoundingBox());
        }
        Vec3 at = moved.position();
        double reach = SeireiteiPlan.GATE_DOOR_HALF_WIDTH - Math.max(body.getXsize(), body.getZsize()) / 2.0 - 0.01;
        double head = SeireiteiPlan.GATE_DOOR_TOP_Y - (body.maxY - at.y) - 0.01;
        // A north or south house runs across x, an east or west one across z: its doorway is the axis's middle.
        boolean acrossX = box.getXSpan() > box.getZSpan();
        boolean lawn = SeireiteiPlan.radius(Mth.floor(at.x), Mth.floor(at.z)) < SeireiteiPlan.GATE_RADIUS;
        double y = Mth.clamp(at.y, SeireiteiPlan.PLATEAU_Y, head);
        Vec3 doorway = acrossX ? new Vec3(Mth.clamp(at.x, -reach, reach), y, at.z)
                : new Vec3(at.x, y, Mth.clamp(at.z, -reach, reach));
        Vec3 roof = new Vec3(at.x, SeireiteiPlan.WALL_TOP_Y, at.z);
        // The roof is Seireitei's side of the membrane, so only a soul on the lawn's half of the gap may go up to it.
        List<Vec3> ways = reach < 0 || head < SeireiteiPlan.PLATEAU_Y ? List.of() : !lawn ? List.of(doorway)
                : Stream.of(doorway, roof).sorted(Comparator.comparingDouble(at::distanceToSqr)).toList();
        Vec3 to = null;
        for (Vec3 way : ways) {
            if (level.noCollision(moved, body.move(way.subtract(at)))) {
                to = way;
                break;
            }
        }
        if (to == null) {
            to = beyond(level, at, body, lawn, acrossX);
        }
        moved.teleportTo(to.x, to.y, to.z);
    }

    /** The ground just beyond a house on this half of its gap, the lawn or the wall's outer foot, on the body's column. */
    private static Vec3 beyond(ServerLevel level, Vec3 at, AABB body, boolean lawn, boolean acrossX) {
        double half = Math.max(body.getXsize(), body.getZsize()) / 2.0 + 0.01;
        double along = Math.signum(acrossX ? at.z : at.x)
                * (lawn ? SeireiteiPlan.SEIREITEI_RADIUS - half : SeireiteiPlan.WALL_OUTER_RADIUS + half);
        double x = acrossX ? at.x : along, z = acrossX ? along : at.z;
        // A chunk not loaded reads as the bottom of the world: the plateau's height stands in for it.
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return new Vec3(x, Math.max(ground, SeireiteiPlan.PLATEAU_Y), z);
    }
}
