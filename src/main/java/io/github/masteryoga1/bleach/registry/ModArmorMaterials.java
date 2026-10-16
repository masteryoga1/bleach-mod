package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, BleachMod.MODID);

    /**
     * The Shinigami uniform. Cloth, not armour: it gives no protection, takes no enchantments and
     * needs no repair. Textures are {@code textures/models/armor/shihakusho_layer_1.png} and {@code _2}.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SHIHAKUSHO = ARMOR_MATERIALS.register("shihakusho",
            () -> {
                EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                for (ArmorItem.Type type : ArmorItem.Type.values()) {
                    defense.put(type, 0);
                }
                return new ArmorMaterial(defense, 0, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY,
                        List.of(new ArmorMaterial.Layer(BleachMod.id("shihakusho"))), 0.0F, 0.0F);
            });

    private ModArmorMaterials() {
    }
}
