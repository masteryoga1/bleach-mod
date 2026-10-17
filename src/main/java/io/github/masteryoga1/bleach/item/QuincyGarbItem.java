package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModArmorMaterials;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * A piece of the white Quincy garb, trimmed in blue. Like the Shihakushō it protects nothing and never
 * wears out; worn whole by a Quincy, it lets them gather reishi a little faster.
 */
public final class QuincyGarbItem extends ArmorItem {
    public QuincyGarbItem(ArmorItem.Type type) {
        super(ModArmorMaterials.QUINCY_GARB, type, new Item.Properties().stacksTo(1));
    }

    /** No armour points, so the tooltip gets no empty "When on body" section either. */
    @Override
    @SuppressWarnings("deprecation")
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long percent = Math.round(BleachConfig.get(BleachConfig.QUINCY_GARB_GATHER_BONUS) * 100.0);
        tooltip.add(Component.translatable("tooltip.bleach.quincy_garb_set", percent).withStyle(ChatFormatting.GRAY));
    }

    /** Whether the entity wears the whole garb: tunic, trousers and shoes. */
    public static boolean isFullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(BleachTags.QUINCY_GARB)
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(BleachTags.QUINCY_GARB)
                && entity.getItemBySlot(EquipmentSlot.FEET).is(BleachTags.QUINCY_GARB);
    }
}
