package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BleachMod.MODID);

    /**
     * The nameless blade every Shinigami starts with. In canon it takes on its wielder's soul
     * and becomes their own zanpakuto; for now it is a plain iron-tier katana.
     */
    public static final DeferredItem<SwordItem> ASAUCHI = ITEMS.register("asauchi",
            () -> new SwordItem(Tiers.IRON, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4F))));

    private ModItems() {
    }
}
