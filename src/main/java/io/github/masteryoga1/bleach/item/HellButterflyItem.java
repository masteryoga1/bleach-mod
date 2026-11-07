package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Jigokuchō, a Hell Butterfly: a Shinigami who carries one is guided through a Senkaimon, and no one else is, so a
 * stolen one is only a butterfly. The Academy hands them out (see
 * {@link io.github.masteryoga1.bleach.soul.TravelEvents}).
 */
public final class HellButterflyItem extends Item {
    public HellButterflyItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bleach.hell_butterfly").withStyle(ChatFormatting.GRAY));
    }

    /** Whether one is anywhere in the player's own inventory, the off hand and armour included. */
    public static boolean carries(Player player) {
        return player.getInventory().contains(stack -> stack.is(ModItems.HELL_BUTTERFLY.get()));
    }

    /** Hands the player one, or drops it at their feet if the inventory is full. */
    public static void give(Player player) {
        ItemStack stack = new ItemStack(ModItems.HELL_BUTTERFLY.get());
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
}
