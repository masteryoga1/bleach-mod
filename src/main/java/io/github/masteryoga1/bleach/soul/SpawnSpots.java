package io.github.masteryoga1.bleach.soul;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where something can come down near a soul: a wounded Shinigami, a Shinigami on duty, a Garganta's Hollows or a
 * wandering Plus. Every spot is in a chunk that is ticking, and open ground under the sky but for {@link #floorNear}'s.
 */
public final class SpawnSpots {
    private SpawnSpots() {
    }

    /**
     * A random spot of open ground between {@code minDistance} and {@code maxDistance} blocks from
     * {@code around}: the top of the ground, dry, on a block with a solid top, inside the world
     * border, with room for the creature to stand, and at most {@code maxRise} blocks above or below
     * it. The last rule keeps arrivals out of caves and away from souls high in the air.
     *
     * @return the spot's bottom centre, or null if none of the tries found one
     */
    @Nullable
    public static Vec3 openGround(ServerLevel level, Entity around, EntityType<?> type,
                                  double minDistance, double maxDistance, int tries, int maxRise) {
        return openGround(level, around, type, 1.0F, minDistance, maxDistance, tries, maxRise);
    }

    /** As {@link #openGround(ServerLevel, Entity, EntityType, double, double, int, int)}, with room for the creature at this size. */
    @Nullable
    public static Vec3 openGround(ServerLevel level, Entity around, EntityType<?> type, float scale,
                                  double minDistance, double maxDistance, int tries, int maxRise) {
        return search(level, around, type, scale, minDistance, maxDistance, tries, maxRise, false);
    }

    /**
     * As {@link #openGround(ServerLevel, Entity, EntityType, double, double, int, int)}, but on any floor at most
     * {@code maxRise} blocks above or below {@code around}, under a roof or not: in a cave or a tunnel, or on a tower's
     * top. Each column tried is searched from the soul's own height outward, one below, one above and so on, so the
     * floor nearest that height wins over a roof above it.
     */
    @Nullable
    public static Vec3 floorNear(ServerLevel level, Entity around, EntityType<?> type, double minDistance,
                                 double maxDistance, int tries, int maxRise) {
        return search(level, around, type, 1.0F, minDistance, maxDistance, tries, maxRise, true);
    }

    /** The search both make: each column's top, or under a roof every height in reach, nearest the soul's first. */
    @Nullable
    private static Vec3 search(ServerLevel level, Entity around, EntityType<?> type, float scale, double minDistance,
                               double maxDistance, int tries, int maxRise, boolean underRoof) {
        RandomSource random = around.getRandom();
        int feet = Mth.floor(around.getY());
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double distance = Mth.nextDouble(random, minDistance, maxDistance);
            int x = Mth.floor(around.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(around.getZ() + Math.sin(angle) * distance);
            if (!level.isPositionEntityTicking(BlockPos.containing(x, around.getY(), z))) {
                continue;
            }
            int top = underRoof ? feet : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            for (int step = 0; step <= (underRoof ? 2 * maxRise : 0); step++) {
                // 0, -1, +1, -2, +2 and so on from the soul's feet; just the top for open ground.
                int y = top + (step % 2 == 0 ? step / 2 : -(step + 1) / 2);
                BlockPos pos = new BlockPos(x, y, z);
                BlockPos below = pos.below();
                if (!level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()
                        || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        || Math.abs(y - around.getY()) > maxRise) {
                    continue;
                }
                AABB box = box(type, scale, x + 0.5, y, z + 0.5);
                if (level.noCollision(box) && !level.containsAnyLiquid(box)) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    /**
     * How many blocks above the landing spot are clear for this creature, at this size, to drop from,
     * up to {@code wanted}: where a rift can open over it.
     */
    public static int clearAbove(ServerLevel level, EntityType<?> type, float scale, Vec3 landing, int wanted) {
        int clear = 0;
        for (int rise = 1; rise <= wanted; rise++) {
            AABB box = box(type, scale, landing.x, landing.y + rise, landing.z);
            if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
                break;
            }
            clear = rise;
        }
        return clear;
    }

    /** The box this creature takes up at this size, standing at this bottom centre. */
    public static AABB box(EntityType<?> type, float scale, double x, double y, double z) {
        return type.getDimensions().scale(scale).makeBoundingBox(x, y, z);
    }
}
