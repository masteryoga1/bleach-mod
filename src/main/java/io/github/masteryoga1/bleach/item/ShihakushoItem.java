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
 * A piece of the Shihakushō, the black uniform of the Shinigami. It protects nothing and never wears
 * out; worn whole by a Shinigami or an Academy student, it quickens their reiryoku a little.
 */
public final class ShihakushoItem extends ArmorItem {
    public ShihakushoItem(ArmorItem.Type type) {
        super(ModArmorMaterials.SHIHAKUSHO, type, new Item.Properties().stacksTo(1));
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
        long percent = Math.round(BleachConfig.get(BleachConfig.SHIHAKUSHO_SET_REGEN_BONUS) * 100.0);
        tooltip.add(Component.translatable("tooltip.bleach.shihakusho_set", percent).withStyle(ChatFormatting.GRAY));
    }

    /** Whether the entity wears the whole uniform: kosode, hakama, and waraji with tabi. */
    public static boolean isFullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(BleachTags.SHINIGAMI_GARB)
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(BleachTags.SHINIGAMI_GARB)
                && entity.getItemBySlot(EquipmentSlot.FEET).is(BleachTags.SHINIGAMI_GARB);
    }
}
