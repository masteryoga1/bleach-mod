package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.entity.Tenshintai;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A Tenshintai folded up: used on the top of a block, it is set down there as the doll ({@link Tenshintai}) and spent,
 * or, refused, it says why on the action bar and is kept. The item holds nothing: the doll's truth is on its soul's
 * record, so any one is as good as another.
 */
public final class TenshintaiItem extends Item {
    public TenshintaiItem(Item.Properties properties) {
        super(properties);
    }

    /** Only a block's top takes it; a client takes the click and leaves the rest to the server. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        String refusal = Tenshintai.setDown(level, player, context.getClickedPos().above());
        if (refusal != null) {
            player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bleach.tenshintai").withStyle(ChatFormatting.GRAY));
    }
}
