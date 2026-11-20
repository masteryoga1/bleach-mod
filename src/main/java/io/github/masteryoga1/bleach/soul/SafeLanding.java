package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Moving a player somewhere they have not walked to (back to where they died, to the Academy
 * after Konsō, through a Senkaimon, or out of the Dangai) without dropping them into a wall, a
 * liquid, a long fall or something that hurts.
 */
public final class SafeLanding {
    private SafeLanding() {
    }

    /**
     * Moves the player to the spot, settled onto the ground below it so a spot in mid-air does not end
     * in a long fall.
     *
     * @return false, leaving the player where they are, if that spot is in a wall, a liquid or
     *         something harmful, outside the world border, over the void, or shut in Las Noches
     */
    public static boolean tryMoveTo(ServerPlayer player, ServerLevel level, Vec3 target) {
        Vec3 spot = landingSpot(player, level, target);
        if (spot == null) {
            return false;
        }
        player.teleportTo(level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
        return true;
    }

    /**
     * Where an entity sent to the spot would stand, settled onto the ground below it with its chunks loaded first, or
     * null where {@link #tryMoveTo} would refuse it.
     */
    @Nullable
    public static Vec3 landingSpot(Entity entity, ServerLevel level, Vec3 target) {
        if (target.y < level.getMinBuildHeight() || target.y >= level.getMaxBuildHeight()) {
            return null;
        }
        AABB box = entity.getDimensions(Pose.STANDING).makeBoundingBox(target);
        // The spot may not be loaded (the player may have sat on the death screen, or be headed
        // somewhere far), and collision checks treat unloaded chunks as empty, so load it first.
        for (int chunkX = Mth.floor(box.minX) >> 4; chunkX <= Mth.floor(box.maxX) >> 4; chunkX++) {
            for (int chunkZ = Mth.floor(box.minZ) >> 4; chunkZ <= Mth.floor(box.maxZ) >> 4; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        if (!level.noCollision(entity, box)) {
            return null;
        }
        while (level.noCollision(entity, box.move(0.0, -1.0, 0.0))) {
            box = box.move(0.0, -1.0, 0.0);
            if (box.minY < level.getMinBuildHeight()) {
                return null;
            }
        }
        // The drop above moves in whole blocks; settle the rest of the way onto what the feet will rest on.
        box = box.move(Entity.collideBoundingBox(entity, new Vec3(0.0, -1.0, 0.0), box, level, List.of()));
        if (!level.getWorldBorder().isWithinBounds(box) || level.containsAnyLiquid(box) || isHarmful(level, entity, box)) {
            return null;
        }
        // The air Las Noches' sealed shell shuts in, which a soul could only leave by digging.
        if (level.dimension() == BleachWorlds.HUECO_MUNDO
                && HuecoMundoPlan.enclosed(Mth.floor(target.x), Mth.floor(box.minY), Mth.floor(target.z))) {
            return null;
        }
        return new Vec3(target.x, box.minY, target.z);
    }

    /**
     * Where an entity coming down from the sky over this column starts: {@code rise} blocks over its ground, the
     * column's chunk loaded first; null over the void, over lava or ground that hurts, outside the world border, or
     * where its body would meet a block. Water below is fine: the fall is slow.
     */
    @Nullable
    public static Vec3 skySpot(Entity entity, ServerLevel level, double x, double z, int rise) {
        int column = Mth.floor(x);
        int row = Mth.floor(z);
        level.getChunk(column >> 4, row >> 4);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column, row);
        if (top <= level.getMinBuildHeight()) {
            return null;
        }
        BlockPos ground = new BlockPos(column, top - 1, row);
        BlockState state = level.getBlockState(ground);
        if (level.getFluidState(ground).is(FluidTags.LAVA) || isHarmful(state) || state.is(Blocks.MAGMA_BLOCK)) {
            return null;
        }
        Vec3 spot = new Vec3(x, top + rise, z);
        AABB box = entity.getDimensions(Pose.STANDING).makeBoundingBox(spot);
        return level.getWorldBorder().isWithinBounds(box) && level.noCollision(entity, box) ? spot : null;
    }

    /**
     * This spot raised by up to {@code rise} whole blocks, as far as the body has clean room, so a roof, or lava or
     * something harmful above, keeps it lower.
     */
    public static Vec3 risen(Entity entity, ServerLevel level, Vec3 spot, int rise) {
        AABB box = entity.getDimensions(Pose.STANDING).makeBoundingBox(spot);
        int up = 0;
        while (up < rise) {
            AABB next = box.move(0.0, up + 1, 0.0);
            if (!level.noCollision(entity, next) || touchesLava(level, next) || isHarmful(level, entity, next)) {
                break;
            }
            up++;
        }
        return spot.add(0.0, up, 0.0);
    }

    /**
     * Whether the spot would hurt or trap whoever stands in it: fire, powder snow, wither roses and the
     * like where the body would be, or magma underfoot.
     */
    public static boolean isHarmful(Level level, Entity entity, AABB box) {
        // Shrunk a hair, as vanilla does, so blocks the box only touches from outside do not count.
        if (BlockPos.betweenClosedStream(box.deflate(1.0E-7)).anyMatch(pos -> isHarmful(level.getBlockState(pos)))) {
            return true;
        }
        // Underfoot only magma hurts, and only through the block vanilla would treat as the support:
        // the colliding block whose centre is nearest the feet (Entity.checkSupportingBlock, measured
        // from the spot instead of where the entity is now), or what lies right under it, so magma
        // under a carpet counts too.
        Vec3 feet = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        AABB sole = new AABB(box.minX, box.minY - 1.0E-6, box.minZ, box.maxX, box.minY, box.maxZ);
        BlockCollisions<BlockPos> under = new BlockCollisions<>(level, entity, sole, false, (pos, shape) -> pos.immutable());
        BlockPos support = null;
        double nearest = Double.MAX_VALUE;
        while (under.hasNext()) {
            BlockPos pos = under.next();
            double distance = pos.distToCenterSqr(feet);
            if (distance < nearest || distance == nearest && (support == null || support.compareTo(pos) < 0)) {
                support = pos;
                nearest = distance;
            }
        }
        return support != null
                && level.getBlockState(support.atY(Mth.floor(box.minY - 0.2))).is(Blocks.MAGMA_BLOCK);
    }

    public static boolean isHarmful(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS);
    }

    /** Whether any lava lies inside the box. */
    public static boolean touchesLava(Level level, AABB box) {
        return BlockPos.betweenClosedStream(box.deflate(1.0E-7)).anyMatch(pos -> level.getFluidState(pos).is(FluidTags.LAVA));
    }
}
