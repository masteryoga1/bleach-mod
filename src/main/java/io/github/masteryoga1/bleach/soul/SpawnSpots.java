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
 * Where something can come down near a soul: a wounded Shinigami, a Garganta's Hollows or a
 * wandering Plus. Every spot is open ground under the sky, in a chunk that is ticking.
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
        RandomSource random = around.getRandom();
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double distance = Mth.nextDouble(random, minDistance, maxDistance);
            int x = Mth.floor(around.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(around.getZ() + Math.sin(angle) * distance);
            if (!level.isPositionEntityTicking(BlockPos.containing(x, around.getY(), z))) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockPos below = pos.below();
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    || Math.abs(pos.getY() - around.getY()) > maxRise) {
                continue;
            }
            AABB box = type.getSpawnAABB(x + 0.5, pos.getY(), z + 0.5);
            if (level.noCollision(box) && !level.containsAnyLiquid(box)) {
                return new Vec3(x + 0.5, pos.getY(), z + 0.5);
            }
        }
        return null;
    }

    /**
     * How many blocks above the landing spot are clear for this creature to drop from, up to
     * {@code wanted}: where a rift can open over it.
     */
    public static int clearAbove(ServerLevel level, EntityType<?> type, Vec3 landing, int wanted) {
        int clear = 0;
        for (int rise = 1; rise <= wanted; rise++) {
            AABB box = type.getSpawnAABB(landing.x, landing.y + rise, landing.z);
            if (!level.noCollision(box) || level.containsAnyLiquid(box)) {
                break;
            }
            clear = rise;
        }
        return clear;
    }
}
