package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.DivisionBannerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BleachMod.MODID);

    /** One tab holding every item this mod registers, in registration order (see {@link #fill}). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLEACH = TABS.register("bleach",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bleach"))
                    .icon(() -> ModItems.ASAUCHI.get().getDefaultInstance())
                    .displayItems(ModCreativeTabs::fill)
                    .build());

    private ModCreativeTabs() {
    }

    /**
     * Lists every item this mod registers, in registration order, with the division banner once for each division in
     * order. It reads only the item registry and writes only to the output it is given.
     */
    public static void fill(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        for (DeferredHolder<Item, ? extends Item> item : ModItems.ITEMS.getEntries()) {
            if (item.get() == ModItems.DIVISION_BANNER.get()) {
                for (DivisionBannerBlock.Division division : DivisionBannerBlock.Division.values()) {
                    output.accept(DivisionBannerBlock.stack(division));
                }
            } else {
                output.accept(item.get());
            }
        }
    }
}
