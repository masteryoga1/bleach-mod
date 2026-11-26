package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * Zangetsu drawn out of his wielder's inner world: the old man in a long ragged coat, on a player's model, holding his
 * blade as it is in Shikai, the great cleaver: a plain Asauchi in that look, bound to no one and adding nothing to his
 * blow, which his wielder sets.
 */
public class ZangetsuSpirit extends MaterialisedSpirit {
    public ZangetsuSpirit(EntityType<? extends ZangetsuSpirit> type, Level level) {
        super(type, level);
    }

    /** An Asauchi in his Shikai's model, with no attributes of its own: the look of the cleaver, and nothing more. */
    @Override
    protected ItemStack held(ZanpakutoSpirit spirit) {
        ItemStack blade = new ItemStack(ModItems.ASAUCHI.get());
        if (spirit.shikai().model() != 0) {
            blade.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(spirit.shikai().model()));
        }
        blade.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return blade;
    }
}
