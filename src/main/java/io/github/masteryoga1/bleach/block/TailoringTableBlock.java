package io.github.masteryoga1.bleach.block;

import com.mojang.serialization.MapCodec;
import io.github.masteryoga1.bleach.item.AcademyUniformItem;
import io.github.masteryoga1.bleach.menu.TailoringMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Where the Shihakushō and the Academy's uniform are sewn (see {@link TailoringMenu}), and the uniform's trim changed. */
public final class TailoringTableBlock extends Block {
    public static final MapCodec<TailoringTableBlock> CODEC = simpleCodec(TailoringTableBlock::new);
    private static final Component TITLE = Component.translatable("container.bleach.tailoring");
    private static final String RETRIMMED_KEY = "message.bleach.academy_uniform.retrimmed";
    /** Ticks before a piece, in either trim, turns again, so a held click turns it under twice a second, not five times. */
    private static final int RETRIM_COOLDOWN_TICKS = 10;

    public TailoringTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(state.getMenuProvider(level, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A piece of the Academy's uniform used on the table from the main hand is sewn anew in the other trim, blue to red or
     * back, for nothing, unless it was just turned; anything else opens the table's window as before.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof AcademyUniformItem piece)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.getCooldowns().isOnCooldown(piece)) {
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            ItemStack retrimmed = piece.retrimmed(stack);
            player.setItemInHand(hand, retrimmed);
            player.getCooldowns().addCooldown(piece, RETRIM_COOLDOWN_TICKS);
            player.getCooldowns().addCooldown(retrimmed.getItem(), RETRIM_COOLDOWN_TICKS);
            level.playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable(RETRIMMED_KEY, retrimmed.getItem().getDescription()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new TailoringMenu(containerId, inventory,
                        ContainerLevelAccess.create(level, pos)), TITLE);
    }
}
