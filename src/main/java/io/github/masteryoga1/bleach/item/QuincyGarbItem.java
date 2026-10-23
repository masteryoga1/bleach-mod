package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModArmorMaterials;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * A piece of Quincy clothing: the white garb trimmed in blue, or the Wandenreich uniform, white with
 * black bands. Like the Shihakushō it protects nothing and never wears out. Worn whole, chest, legs
 * and feet from either or both (see {@link BleachTags#QUINCY_GARB}), it lets a Quincy gather reishi a
 * little faster; the Wandenreich cap is only for the look.
 */
public final class QuincyGarbItem extends ArmorItem {
    /** A piece of the white garb. */
    public QuincyGarbItem(ArmorItem.Type type) {
        this(ModArmorMaterials.QUINCY_GARB, type);
    }

    /** A piece of clothing drawn from this cloth material's layers. */
    public QuincyGarbItem(Holder<ArmorMaterial> material, ArmorItem.Type type) {
        super(material, type, new Item.Properties().stacksTo(1));
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

    /** The set bonus on the pieces that count towards it; the cap says it is only for the look. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.is(BleachTags.QUINCY_GARB)) {
            long percent = Math.round(BleachConfig.get(BleachConfig.QUINCY_GARB_GATHER_BONUS) * 100.0);
            tooltip.add(Component.translatable("tooltip.bleach.quincy_garb_set", percent).withStyle(ChatFormatting.GRAY));
        } else if (stack.is(BleachTags.WANDENREICH_UNIFORM)) {
            tooltip.add(Component.translatable("tooltip.bleach.wandenreich_cap").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Whether the entity wears a whole garb: chest, legs and feet, from the white garb or the Wandenreich uniform. */
    public static boolean isFullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(BleachTags.QUINCY_GARB)
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(BleachTags.QUINCY_GARB)
                && entity.getItemBySlot(EquipmentSlot.FEET).is(BleachTags.QUINCY_GARB);
    }

    /** Whether the entity wears the Wandenreich coat, trousers and boots; the cap is not needed. */
    public static boolean isWandenreichSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(BleachTags.WANDENREICH_UNIFORM)
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(BleachTags.WANDENREICH_UNIFORM)
                && entity.getItemBySlot(EquipmentSlot.FEET).is(BleachTags.WANDENREICH_UNIFORM);
    }
}
