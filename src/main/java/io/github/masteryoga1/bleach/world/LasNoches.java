package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.SafeLanding;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Las Noches at Hueco Mundo's centre, the structures {@code bleach:las_noches} and {@code bleach:las_noches_halls}:
 * worldgen lays its wall, dome and towers, then the halls under the dome, from {@link HuecoMundoPlan}'s tables, in chunks
 * made since each was added with structures on, and two stamps lay them a chunk at a time into chunks made before, or
 * with structures off, the halls once the shell stands about the chunk. A soul or creature the shell or the halls are
 * laid over, or that the shell shuts in where the halls do not yet stand, is set down outside the wall, as a Garganta
 * sets one down. An Arrancar with no spawn of its own wakes on the court's floor ({@link #wakeSpot}).
 */
public final class LasNoches {
    /** Its structure, whose one piece lays the plan's shell. */
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("las_noches"));
    /** Its halls, whose one piece lays them under the dome and cuts its doors and the stair through the crown. */
    public static final ResourceKey<Structure> HALLS =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("las_noches_halls"));
    /** The chunk both start in, the centre's: its references reach every chunk either covers, 8 at most away. */
    public static final ChunkPos HOME = new ChunkPos(0, 0);
    /** The stamp that lays each of the shell's 256 chunks alone, once it and those beside it have loaded. */
    public static final SiteStamp STAMP = SiteStamp.chunkwise(BleachWorlds.HUECO_MUNDO, STRUCTURE,
            "Las Noches in Hueco Mundo", HOME, HuecoMundoPlan.SHELL_BOX, LasNoches::placed);
    /** The stamp that lays each of the halls' 64 chunks alone, once the shell stands in it and in the 8 beside it. */
    public static final SiteStamp HALLS_STAMP = SiteStamp.chunkwise(BleachWorlds.HUECO_MUNDO, HALLS,
            "Las Noches' halls in Hueco Mundo", HOME, HuecoMundoPlan.HALLS_BOX, LasNoches::hallsPlaced,
            LasNoches::shellAround);
    /** Where an Arrancar wakes inside, in this order: the court's floor in the centre's chunk, about the tower's foot. */
    public static final List<Vec3> WAKE_SPOTS = List.of(new Vec3(12.5, HuecoMundoPlan.LEVEL_Y, 12.5),
            new Vec3(0.5, HuecoMundoPlan.LEVEL_Y, 12.5), new Vec3(12.5, HuecoMundoPlan.LEVEL_Y, 0.5));
    /** How many ticks a soul that joined in the shell's box waits for the chunks under it to load before it is let be. */
    private static final int JOIN_WAIT = 600;
    /**
     * Souls that joined in the shell's box, each with the ticks it may still wait: over a chunk not loaded nothing reads as
     * solid, so one is judged only once every chunk under it is. Server thread only.
     */
    private static final Map<LivingEntity, Integer> JOINED = new HashMap<>();

    private LasNoches() {
    }

    /** Lays a chunk of the shell, then of the halls, once it and the chunks beside it have loaded (see {@link SiteStamp}). */
    public static void onChunkLoad(ChunkEvent.Load event) {
        STAMP.onChunkLoad(event);
        HALLS_STAMP.onChunkLoad(event);
    }

    /** The further tries the stamps' checks asked for, and the souls that joined in the shell whose chunks have loaded. */
    public static void onServerTick(ServerTickEvent.Post event) {
        STAMP.onServerTick(event);
        HALLS_STAMP.onServerTick(event);
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
        due.forEach(living -> free((ServerLevel) living.level(), living, true));
    }

    /**
     * A soul or creature joining Hueco Mundo in the shell's box, as one saved there before the shell stood, is judged at
     * the end of a tick once the chunks under it have loaded, and freed if buried or still shut in. Elsewhere it costs a few
     * comparisons.
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living && event.getLevel() instanceof ServerLevel level
                && level.dimension() == BleachWorlds.HUECO_MUNDO
                && AABB.of(HuecoMundoPlan.SHELL_BOX).intersects(living.getBoundingBox())) {
            JOINED.put(living, JOIN_WAIT);
        }
    }

    /**
     * A chunk's part of the shell the stamp has just laid: every soul or creature in its column it buries or shuts in is
     * moved, and the halls about the chunk are checked again, as they wait for the shell.
     */
    private static void placed(ServerLevel level, BoundingBox part) {
        level.getEntitiesOfClass(LivingEntity.class, AABB.of(part)).forEach(living -> free(level, living, true));
        HALLS_STAMP.recheck(level, new ChunkPos(part.minX() >> 4, part.minZ() >> 4));
    }

    /** A chunk's part of the halls their stamp has just laid, which opens it: every soul or creature they bury is moved. */
    private static void hallsPlaced(ServerLevel level, BoundingBox part) {
        level.getEntitiesOfClass(LivingEntity.class, AABB.of(part)).forEach(living -> free(level, living, false));
    }

    /** Whether the shell stands, by its reference, in each chunk bare of the halls and in the 8 beside it, all loaded. */
    private static boolean shellAround(ServerLevel level, List<? extends ChunkAccess> bare) {
        Structure shell = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        return shell != null && bare.stream().allMatch(chunk -> ChunkPos.rangeClosed(chunk.getPos(), 1).allMatch(pos -> {
            ChunkAccess beside = level.getChunkSource().getChunkNow(pos.x, pos.z);
            return beside != null && beside.getReferencesForStructure(shell).contains(HOME.toLong());
        }));
    }

    /**
     * Whether this cell is air the shell still shuts in, so that a soul there could leave only by digging: under the dome
     * or in the fifth tower's room, in a chunk whose halls are not laid yet. The chunk is loaded first if it is not.
     */
    public static boolean shutIn(ServerLevel level, int x, int y, int z) {
        if (!HuecoMundoPlan.enclosed(x, y, z)) {
            return false;
        }
        return !hallsStand(level, level.getChunk(x >> 4, z >> 4));
    }

    /** Whether the halls stand in this chunk, by its reference to them; never where their structure is missing. */
    public static boolean hallsStand(ServerLevel level, ChunkAccess chunk) {
        Structure halls = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(HALLS);
        return halls != null && chunk.getReferencesForStructure(halls).contains(HOME.toLong());
    }

    /**
     * Where a soul wakes inside the dome: the shell in the centre's chunk and the 8 beside it, then the halls in the
     * centre's chunk, laid now where they are not yet, then the first wake spot where a soul can stand on the floor under
     * the dome; null if none will hold it.
     */
    @Nullable
    public static Vec3 wakeSpot(Entity soul, ServerLevel level) {
        ChunkPos.rangeClosed(HOME, 1).forEach(pos -> STAMP.layNow(level, pos));
        HALLS_STAMP.layNow(level, HOME);
        for (Vec3 spot : WAKE_SPOTS) {
            Vec3 landed = SafeLanding.landingSpot(soul, level, spot);
            // Through a floor dug out, it would drop into the Forest of Menos.
            if (landed != null && HuecoMundoPlan.enclosed(Mth.floor(landed.x), Mth.floor(landed.y), Mth.floor(landed.z))) {
                return landed;
            }
        }
        return null;
    }

    /**
     * If the shell's or the halls' blocks bury this soul or creature, or it stands where the shell still shuts it in and
     * {@code shutInToo}, moves it with what it rides to where a Garganta from there sets one down, past the wall on its
     * bearing, else to the top of its own column.
     */
    private static void free(ServerLevel level, LivingEntity living, boolean shutInToo) {
        BlockPos feet = living.blockPosition();
        if (living.isRemoved() || living.level() != level || living.isSpectator()
                || !(buried(level, living.getBoundingBox())
                        || shutInToo && shutIn(level, feet.getX(), feet.getY(), feet.getZ()))) {
            return;
        }
        // A rider moved alone is set back on its seat by its vehicle's next tick, so the whole stack goes.
        Entity moved = living.getRootVehicle();
        AABB body = moved.getBoundingBox();
        for (Entity rider : moved.getIndirectPassengers()) {
            body = body.minmax(rider.getBoundingBox());
        }
        Vec3 at = moved.position();
        // The one place the stamps may load a chunk: the landing's, at r 136, made first if it never was.
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

    /**
     * Whether this box reaches into a block the shell or the halls laid that still stands: a body on a step or a seat is
     * not buried by it, and the ground or a dug hole buries no one.
     */
    private static boolean buried(ServerLevel level, AABB box) {
        AABB inner = box.deflate(1.0E-7);
        for (BlockPos cell : BlockPos.betweenClosed(Mth.floor(inner.minX), Mth.floor(inner.minY), Mth.floor(inner.minZ),
                Mth.floor(inner.maxX), Mth.floor(inner.maxY), Mth.floor(inner.maxZ))) {
            HuecoMundoPlan.Hall hall = HuecoMundoPlan.hallsAt(cell.getX(), cell.getY(), cell.getZ());
            if (HuecoMundoPlan.shellAt(cell.getX(), cell.getY(), cell.getZ()) == null
                    && (hall == null || hall == HuecoMundoPlan.Hall.CUT)) {
                continue;
            }
            for (AABB part : level.getBlockState(cell).getCollisionShape(level, cell).toAabbs()) {
                if (part.move(cell).intersects(inner)) {
                    return true;
                }
            }
        }
        return false;
    }
}
