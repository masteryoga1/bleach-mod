package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.menu.TailoringMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BleachMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TailoringMenu>> TAILORING = MENUS.register("tailoring",
            () -> new MenuType<TailoringMenu>(TailoringMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
