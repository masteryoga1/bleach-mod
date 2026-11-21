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

/** The materials of the mod's outfits: all cloth, worn for the look and for what they do for the soul. */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, BleachMod.MODID);

    /**
     * The Shinigami uniform. Cloth, not armour: it gives no protection, takes no enchantments and
     * needs no repair. Textures are {@code textures/models/armor/shihakusho_layer_1.png} and {@code _2}.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SHIHAKUSHO = cloth("shihakusho");

    /**
     * The white Quincy garb, cloth in the same way as the Shihakushō. Textures are
     * {@code textures/models/armor/quincy_garb_layer_1.png} and {@code _2}.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> QUINCY_GARB = cloth("quincy_garb");

    /**
     * The Wandenreich uniform, white with black bands, cloth like the rest. Textures are
     * {@code textures/models/armor/wandenreich_layer_1.png} (cap, coat and boots) and {@code _2} (trousers).
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WANDENREICH = cloth("wandenreich");

    /**
     * The white Arrancar uniform, cloth like the rest. Textures are
     * {@code textures/models/armor/arrancar_uniform_layer_1.png} (jacket and zōri) and {@code _2} (hakama).
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ARRANCAR_UNIFORM = cloth("arrancar_uniform");

    /**
     * The Shin'ō Academy's uniform, cloth like the rest. Textures are
     * {@code textures/models/armor/academy_uniform_layer_1.png} (the kosode trimmed in blue) and {@code _2} (the blue
     * hakama); a red piece is drawn from {@code academy_uniform_red_layer_1.png} and {@code _2} instead.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ACADEMY_UNIFORM = cloth("academy_uniform");

    private ModArmorMaterials() {
    }

    /** Cloth: no defence, toughness or enchantability and nothing to repair it with, drawn from the named layer. */
    private static DeferredHolder<ArmorMaterial, ArmorMaterial> cloth(String name) {
        return ARMOR_MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : ArmorItem.Type.values()) {
                defense.put(type, 0);
            }
            return new ArmorMaterial(defense, 0, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(BleachMod.id(name))), 0.0F, 0.0F);
        });
    }
}
