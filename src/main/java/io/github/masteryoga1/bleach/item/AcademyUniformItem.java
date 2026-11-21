package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModArmorMaterials;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * A piece of the Shin'ō Academy's uniform, a white kosode trimmed in blue or red or a hakama of that colour, each trim its
 * own item. Cloth like the Shihakushō: no armour, and it never wears out; worn whole, it quickens a student's reiryoku.
 */
public final class AcademyUniformItem extends ArmorItem {
    /** The red trim's armour layer; the blue is the material's own. */
    private static final ArmorMaterial.Layer RED_LAYER = new ArmorMaterial.Layer(BleachMod.id("academy_uniform_red"));

    private final boolean red;
    private final Supplier<? extends Item> otherTrim;

    /** A piece in red trim or blue, and the same piece in the other trim, which the Tailoring Table turns it into. */
    public AcademyUniformItem(ArmorItem.Type type, boolean red, Supplier<? extends Item> otherTrim) {
        super(ModArmorMaterials.ACADEMY_UNIFORM, type, new Item.Properties().stacksTo(1));
        this.red = red;
        this.otherTrim = otherTrim;
    }

    /** This piece in the other trim, keeping its name and anything else set on it. */
    public ItemStack retrimmed(ItemStack stack) {
        return stack.transmuteCopy(otherTrim.get());
    }

    /** Whether the player carries or wears this piece, the top or the hakama, in either trim. */
    public boolean carriedBy(Player player) {
        return player.getInventory().contains(stack -> stack.getItem() instanceof AcademyUniformItem piece
                && piece.getType() == getType());
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

    /** A red piece is drawn from the red layer; a blue one from the material's (NeoForge asks the item first). */
    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer,
                                            boolean innerModel) {
        return red ? RED_LAYER.texture(innerModel) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long percent = Math.round(BleachConfig.get(BleachConfig.SHIHAKUSHO_SET_REGEN_BONUS) * 100.0);
        tooltip.add(Component.translatable("tooltip.bleach.academy_uniform_set", percent).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bleach.academy_uniform_trim").withStyle(ChatFormatting.GRAY));
    }

    /** Whether the entity wears the whole uniform, top and hakama, in either trim and over any feet. */
    public static boolean isFullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(BleachTags.ACADEMY_UNIFORM)
                && entity.getItemBySlot(EquipmentSlot.LEGS).is(BleachTags.ACADEMY_UNIFORM);
    }
}
