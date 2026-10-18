package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Sanrei Glove, handed down to a Quincy of the third tier: a training glove that fights every
 * draw. Using it puts it on, which uses the item up; the glove is then worn on the soul, not carried,
 * until the Quincy Form key takes it off again. The rules are {@link QuincyArtsLogic}'s and
 * {@link QuincyArtsEvents}'.
 */
public class SanreiGloveItem extends Item {
    public SanreiGloveItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Puts the glove on, unless the soul cannot wear it. The client agrees on the synced soul, so its
     * hand only swings when the server will put the glove on; the server says why not.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return QuincyArtsLogic.gloveRefusal(SoulLogic.get(player)) == null
                    ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        }
        String refusal = QuincyArtsEvents.wearGlove(player, SoulLogic.get(player));
        if (refusal != null) {
            player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bleach.sanrei_glove.desc").withStyle(ChatFormatting.GRAY));
    }
}
