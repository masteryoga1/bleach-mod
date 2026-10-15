package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BleachMod.MODID);

    /** One tab holding every item this mod registers, in registration order. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLEACH = TABS.register("bleach",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bleach"))
                    .icon(() -> ModItems.ASAUCHI.get().getDefaultInstance())
                    .displayItems((parameters, output) ->
                            ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeTabs() {
    }
}
