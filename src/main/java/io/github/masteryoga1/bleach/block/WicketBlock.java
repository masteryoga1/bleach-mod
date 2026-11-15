package io.github.masteryoga1.bleach.block;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.soul.GateLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The wicket, the small door through a great gate's house that lets one soul by: dark oak, opened by hand as a wooden
 * door is, but broken by no tool, blast, piston or mob, and standing on whatever lies under it. In Soul Society it keeps
 * the gates' rule ({@link GateLogic#mayPass}), and only a hand moves it: from the Rukongai's side a soul the gates bar
 * can neither open it nor pass it open.
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

    /** Only a hand moves it: no mob at a door, villager or wind charge, each of which asks this. */
    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
    }

    /** No signal opens or shuts it. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from,
                                   boolean moving) {
    }

    /** From the Rukongai's side in Soul Society, a soul the gates bar is told why, and the wicket does not move. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (Shakonmaku.in(level) && GateLogic.outside(player.position(), pos, state.getValue(FACING))
                && !GateLogic.mayPass(player)) {
            if (player instanceof ServerPlayer soul) {
                soul.displayClientMessage(Component.translatable(GateLogic.barredKey(SoulLogic.get(soul)))
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.CONSUME;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /**
     * Open, it stays shut to a body on its outer side that the gates bar, so none walks, rides or throws in behind
     * another; one already in the leaf's edge of its cell goes either way until clear of the leaf.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean barred = state.getValue(OPEN) && level instanceof Level world && Shakonmaku.in(world)
                && context instanceof EntityCollisionContext body && body.getEntity() != null
                && GateLogic.bars(body.getEntity(), pos, state.getValue(FACING));
        return super.getCollisionShape(barred ? state.setValue(OPEN, false) : state, level, pos, context);
    }
}
