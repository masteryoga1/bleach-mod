package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's item data components. They register before items, so item properties may use them. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BleachMod.MODID);

    /** Whose zanpakutō an Asauchi has become, and whether it is released. Saved, and sent to clients. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ZanpakutoBlade>> ZANPAKUTO_BLADE =
            COMPONENTS.registerComponentType("zanpakuto_blade",
                    builder -> builder.persistent(ZanpakutoBlade.CODEC).networkSynchronized(ZanpakutoBlade.STREAM_CODEC));

    private ModComponents() {
    }
}
