package io.github.masteryoga1.bleach.block;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.soul.DangaiEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Kōryū, the current the Dangai's lanes are walled with: passable, unbreakable and drawn see-through. It holds an
 * aware soul whose body enters it ({@link DangaiEvents#touch}); striking it does nothing.
 */
public final class KoryuBlock extends Block {
    public static final MapCodec<KoryuBlock> CODEC = simpleCodec(KoryuBlock::new);
    /** The light the current gives off. */
    public static final int LIGHT = 6;

    public KoryuBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Server side: an aware soul whose body is in the current is held. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            DangaiEvents.touch(player, level.getGameTime());
        }
    }

    /** Two faces of the current that meet are not drawn, as glass's are not. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }
}
