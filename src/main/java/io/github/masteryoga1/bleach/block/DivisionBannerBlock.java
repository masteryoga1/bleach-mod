package io.github.masteryoga1.bleach.block;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A division's banner: white cloth on a dark rod, hung on the side of a block and marked with whose it is. Used where
 * it hangs, it turns to the next division; broken or picked, it gives the banner of the division it shows. Nothing
 * need hold it up, so a banner a template sets never falls for want of a wall not placed yet.
 */
public class DivisionBannerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DivisionBannerBlock> CODEC = simpleCodec(DivisionBannerBlock::new);
    /** Whose banner it is. */
    public static final EnumProperty<Division> DIVISION = EnumProperty.create("division", Division.class);
    /** The cloth's outline for each facing: 2 pixels deep, against the side behind the way it faces. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0), Direction.SOUTH, box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
            Direction.WEST, box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0), Direction.EAST, box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0));

    public DivisionBannerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DIVISION, Division.FIRST));
    }

    /** The banner item of this division: a plain one for the First's. */
    public static ItemStack stack(Division division) {
        ItemStack stack = ModItems.DIVISION_BANNER.toStack();
        // The First's is the item's own default, and a stack keeps no change that equals its item's default.
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(DIVISION, division));
        return stack;
    }

    /** The division a banner item places, the First's where its block state names none it knows. */
    public static Division divisionOf(ItemStack stack) {
        Division division = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(DIVISION);
        return division == null ? Division.FIRST : division;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DIVISION);
    }

    /** Set against the side of a block, it hangs there facing out of it; set on a floor or ceiling, it faces its setter. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING,
                face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Turns it to the next division, Zero's to the First's, for anyone who may build here. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(DIVISION), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Pick-block gives the banner of the division it shows. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return stack(state.getValue(DIVISION));
    }

    /** Names the banner's division, then how to turn a hung one. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.bleach.division_banner." + divisionOf(stack).getSerializedName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bleach.division_banner").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The sixteen a banner can show: the Gotei 13's divisions in order, then the Kidō Corps, the Onmitsukidō and Zero. */
    public enum Division implements StringRepresentable {
        FIRST("first"), SECOND("second"), THIRD("third"), FOURTH("fourth"), FIFTH("fifth"), SIXTH("sixth"),
        SEVENTH("seventh"), EIGHTH("eighth"), NINTH("ninth"), TENTH("tenth"), ELEVENTH("eleventh"), TWELFTH("twelfth"),
        THIRTEENTH("thirteenth"), KIDO_CORPS("kido_corps"), ONMITSUKIDO("onmitsukido"), ZERO("zero");

        private final String name;

        Division(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
