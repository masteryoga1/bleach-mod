package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Las Noches at Hueco Mundo's centre, the structure {@code bleach:las_noches}: worldgen lays its wall, dome and towers,
 * from {@link HuecoMundoPlan}'s table, in chunks made since it was added with structures on, and a stamp lays them a
 * chunk at a time into chunks made before, or with structures off. A soul or creature the shell is laid over, or shuts
 * in, is set down outside the wall, as a Garganta sets one down.
 */
public final class LasNoches {
    /** Its structure, whose one piece lays the plan's shell. */
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("las_noches"));
    /** The chunk it starts in, the centre's: its references reach every chunk the shell covers, 8 at most away. */
    public static final ChunkPos HOME = new ChunkPos(0, 0);
    /** The stamp that lays each of the shell's 256 chunks alone, once it and those beside it have loaded. */
    public static final SiteStamp STAMP = SiteStamp.chunkwise(BleachWorlds.HUECO_MUNDO, STRUCTURE,
            "Las Noches in Hueco Mundo", HOME, HuecoMundoPlan.SHELL_BOX, LasNoches::placed);
    /** How many ticks a soul that joined in the shell's box waits for the chunks under it to load before it is let be. */
    private static final int JOIN_WAIT = 600;
    /**
     * Souls that joined in the shell's box, each with the ticks it may still wait: over a chunk not loaded nothing reads as
     * solid, so one is judged only once every chunk under it is. Server thread only.
     */
    private static final Map<LivingEntity, Integer> JOINED = new HashMap<>();

    private LasNoches() {
    }

    /** Lays a chunk of the shell once it and the chunks beside it have loaded (see {@link SiteStamp#onChunkLoad}). */
    public static void onChunkLoad(ChunkEvent.Load event) {
        STAMP.onChunkLoad(event);
    }

    /** The further tries the stamp's checks asked for, and the souls that joined in the shell whose chunks have loaded. */
    public static void onServerTick(ServerTickEvent.Post event) {
        STAMP.onServerTick(event);
        MinecraftServer server = event.getServer();
        List<LivingEntity> due = new ArrayList<>();
        for (Iterator<Map.Entry<LivingEntity, Integer>> each = JOINED.entrySet().iterator(); each.hasNext(); ) {
            Map.Entry<LivingEntity, Integer> joined = each.next();
            LivingEntity living = joined.getKey();
            if (living.isRemoved() || living.level().getServer() != server
                    || living.level().dimension() != BleachWorlds.HUECO_MUNDO || joined.getValue() <= 0) {
                each.remove();
            } else if (GateHouses.loadedUnder((ServerLevel) living.level(), living)) {
                each.remove();
                due.add(living);
            } else {
                joined.setValue(joined.getValue() - 1);
            }
        }
        due.forEach(living -> free((ServerLevel) living.level(), living));
    }

    /**
     * A soul or creature joining Hueco Mundo in the shell's box, as one saved there before the shell stood, is judged at
     * the end of a tick once the chunks under it have loaded, and freed if buried or shut in. Elsewhere it costs a few
     * comparisons.
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living && event.getLevel() instanceof ServerLevel level
                && level.dimension() == BleachWorlds.HUECO_MUNDO
                && AABB.of(HuecoMundoPlan.SHELL_BOX).intersects(living.getBoundingBox())) {
            JOINED.put(living, JOIN_WAIT);
        }
    }

    /** A chunk's part of the shell the stamp has just laid: every soul or creature in its column it buries is moved. */
    private static void placed(ServerLevel level, BoundingBox part) {
        level.getEntitiesOfClass(LivingEntity.class, AABB.of(part)).forEach(living -> free(level, living));
    }

    /**
     * If the shell's blocks bury this soul or creature, or it stands in the air the shell shuts in, moves it with what it
     * rides to where a Garganta from there sets one down, past the wall on its bearing, else to the top of its own column.
     */
    private static void free(ServerLevel level, LivingEntity living) {
        BlockPos feet = living.blockPosition();
        if (living.isRemoved() || living.level() != level || living.isSpectator()
                || !(inShell(level, living.getBoundingBox())
                        || HuecoMundoPlan.enclosed(feet.getX(), feet.getY(), feet.getZ()))) {
            return;
        }
        // A rider moved alone is set back on its seat by its vehicle's next tick, so the whole stack goes.
        Entity moved = living.getRootVehicle();
        AABB body = moved.getBoundingBox();
        for (Entity rider : moved.getIndirectPassengers()) {
            body = body.minmax(rider.getBoundingBox());
        }
        Vec3 at = moved.position();
        // The one place the stamp may load a chunk: the landing's, at r 136, made first if it never was.
        Vec3 to = TravelEvents.huecoMundoLanding(moved, level, at.x, at.z);
        if (to == null || !level.noBlockCollision(moved, body.move(to.subtract(at)))) {
            // The open sky over the wall, a tower or the dome, which the shell always leaves: over the highest column
            // the stack covers, then raised until no block holds it.
            int top = level.getMinBuildHeight();
            for (int x = Mth.floor(body.minX); x <= Mth.floor(body.maxX); x++) {
                for (int z = Mth.floor(body.minZ); z <= Mth.floor(body.maxZ); z++) {
                    top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
                }
            }
            to = new Vec3(at.x, top, at.z);
            while (to.y < level.getMaxBuildHeight() && !level.noBlockCollision(moved, body.move(to.subtract(at)))) {
                to = to.add(0.0, 1.0, 0.0);
            }
        }
        moved.teleportTo(to.x, to.y, to.z);
    }

    /** Whether any cell this box takes up is the shell's and still holds a block: the ground or a dug hole buries no one. */
    private static boolean inShell(ServerLevel level, AABB box) {
        for (BlockPos cell : BlockPos.betweenClosed(Mth.floor(box.minX + 1.0E-7), Mth.floor(box.minY + 1.0E-7),
                Mth.floor(box.minZ + 1.0E-7), Mth.floor(box.maxX - 1.0E-7), Mth.floor(box.maxY - 1.0E-7),
                Mth.floor(box.maxZ - 1.0E-7))) {
            if (HuecoMundoPlan.shellAt(cell.getX(), cell.getY(), cell.getZ()) != null
                    && !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
