package io.github.masteryoga1.bleach.item;

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
 * A piece of the white Arrancar uniform: the Shihakushō in reversed colours, white with a black sash, and
 * black tabi under white zōri. Cloth: no armour, no bonus, and it never wears out. Anyone may wear it but a
 * Hollow, whose hide refuses all armour; it hides an Arrancar's hole and number as any clothing does.
 */
public final class ArrancarUniformItem extends ArmorItem {
    public ArrancarUniformItem(ArmorItem.Type type) {
        super(ModArmorMaterials.ARRANCAR_UNIFORM, type, new Item.Properties().stacksTo(1));
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

    /** It does nothing for the soul, so the tooltip says it is only for the look. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bleach.arrancar_uniform").withStyle(ChatFormatting.GRAY));
    }

    /** Whether the entity wears the whole uniform: jacket, hakama, and zōri with tabi. */
    public static boolean isFullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(BleachTags.ARRANCAR_UNIFORM)
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(BleachTags.ARRANCAR_UNIFORM)
                && entity.getItemBySlot(EquipmentSlot.FEET).is(BleachTags.ARRANCAR_UNIFORM);
    }
}
