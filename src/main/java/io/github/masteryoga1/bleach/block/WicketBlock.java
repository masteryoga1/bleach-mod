package io.github.masteryoga1.bleach.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The wicket, the small door through a great gate's house that lets one soul by: dark oak, opened by hand as a wooden
 * door is, but broken by no tool, blast, piston or mob, and standing on whatever lies under it.
 */
public class WicketBlock extends DoorBlock {
    public static final MapCodec<WicketBlock> CODEC = simpleCodec(WicketBlock::new);

    public WicketBlock(BlockBehaviour.Properties properties) {
        super(BlockSetType.DARK_OAK, properties);
    }

    @Override
    public MapCodec<WicketBlock> codec() {
        return CODEC;
    }

    /** No mob breaks it: a zombie at a door, a Wither and the dragon all ask this first. */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    /** The lower half stands whatever is dug or blasted from under it; the upper still needs the lower. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER || level.getBlockState(pos.below()).is(this);
    }
}
