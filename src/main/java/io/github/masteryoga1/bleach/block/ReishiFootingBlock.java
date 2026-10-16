package io.github.masteryoga1.bleach.block;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.soul.FootingState;
import io.github.masteryoga1.bleach.soul.ReishiFooting;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hardened reishi: an invisible plate in the air that holds up only the player standing on it, at
 * the height their footing is anchored to, while they are not sneaking. To everything else it is
 * air. It fades a moment after its user moves on (see {@link ReishiFooting}).
 *
 * <p>It is not an air block, so the server's anti-flying check sees the player standing on something.
 */
public final class ReishiFootingBlock extends Block {
    public static final MapCodec<ReishiFootingBlock> CODEC = simpleCodec(ReishiFootingBlock::new);
    /** The top sixteenth of the block. */
    public static final VoxelShape PLATE = box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);
    /** A faint chime underfoot. */
    public static final SoundType QUIET = new SoundType(0.3F, 1.6F, SoundEvents.AMETHYST_BLOCK_BREAK,
            SoundEvents.AMETHYST_BLOCK_STEP, SoundEvents.AMETHYST_BLOCK_PLACE, SoundEvents.AMETHYST_BLOCK_HIT,
            SoundEvents.AMETHYST_BLOCK_FALL);
    /** How often an unused plate checks whether to fade. */
    public static final int FADE_CHECK_TICKS = 10;

    public ReishiFootingBlock(Properties properties) {
        super(properties);
    }

    public static Properties properties() {
        return Properties.of()
                .noOcclusion()
                .dynamicShape()
                .noLootTable()
                .noTerrainParticles()
                .replaceable()
                .pushReaction(PushReaction.DESTROY)
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .sound(QUIET);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at or outline. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return holds(pos, context) ? PLATE : Shapes.empty();
    }

    /** Whether the plate holds up whoever this collision is for: its user, from above, not sneaking. */
    private static boolean holds(BlockPos pos, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext) || !(entityContext.getEntity() instanceof Player player)
                || !player.hasData(SoulAttachments.FOOTING)) {
            return false;
        }
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        return footing.active && pos.getY() == footing.anchorY && context.isAbove(PLATE, pos, false)
                && !context.isDescending();
    }

    /** Hardened reishi breaks the fall. */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, FADE_CHECK_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (ReishiFooting.inUse(level, pos)) {
            level.scheduleTick(pos, this, FADE_CHECK_TICKS);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        }
    }
}
