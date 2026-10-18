package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The Quincy Cross, the silver pendant handed down in Quincy families. Held in either hand, holding
 * use draws reishi into it: for an awakened Quincy it forms the Heilig Bogen, a bow of reishi drawn
 * like a vanilla bow, and letting go looses a Heilig Pfeil; for a latent one the reishi slips away
 * again, and each long draw trains the blood towards awakening. To anyone without Quincy blood it is
 * only silver. The rules are {@link QuincyLogic}'s and {@link QuincyEvents}'.
 */
public final class QuincyCrossItem extends Item {
    /** How long use may be held: far longer than any draw, which the holder ends by letting go. */
    public static final int USE_TICKS = 72000;

    public QuincyCrossItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Starts drawing, unless the soul cannot draw at all. Both sides run the same check, on facts the
     * client already has (blood, state, tiers and Shinigami power), so the client only starts drawing
     * when the server will too; the server says why not. Whether there is reishi enough to fire is
     * settled when the draw is let go.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String refusal = QuincyLogic.drawRefusal(SoulLogic.get(player));
        if (refusal != null) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Drawn and held like a bow, so the arms and the first-person view follow vanilla's. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    /** Each tick of the draw, on the server: the draw sound, and the reishi a held full draw costs. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide() && entity instanceof Player player) {
            QuincyEvents.onBowHeld(player, getUseDuration(stack, entity) - remainingUseDuration);
        }
    }

    /** Letting go, on the server: fire, train or fizzle, depending on the soul and how long it drew. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide() && entity instanceof Player player) {
            QuincyEvents.release(player, getUseDuration(stack, entity) - timeLeft);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bleach.quincy_cross").withStyle(ChatFormatting.GRAY));
    }

    /** Whether the stack is a Quincy Cross. */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof QuincyCrossItem;
    }
}
