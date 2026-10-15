package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BleachMod.MODID);

    /**
     * The nameless blade every Shinigami starts with, issued by the Academy and formed for a new
     * Substitute. In canon it takes on its wielder's soul and becomes their own zanpakutō; for now it
     * is a plain iron-tier katana that never breaks.
     */
    public static final DeferredItem<SwordItem> ASAUCHI = ITEMS.register("asauchi",
            () -> new SwordItem(Tiers.IRON, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4F))
                    .component(DataComponents.UNBREAKABLE, new Unbreakable(false))));

    /** The Shihakushō: black kosode, hakama, and waraji over tabi. See {@link ShihakushoItem}. */
    public static final DeferredItem<ShihakushoItem> SHIHAKUSHO_KOSODE = ITEMS.register("shihakusho_kosode",
            () -> new ShihakushoItem(ArmorItem.Type.CHESTPLATE));
    public static final DeferredItem<ShihakushoItem> SHIHAKUSHO_HAKAMA = ITEMS.register("shihakusho_hakama",
            () -> new ShihakushoItem(ArmorItem.Type.LEGGINGS));
    public static final DeferredItem<ShihakushoItem> WARAJI = ITEMS.register("waraji",
            () -> new ShihakushoItem(ArmorItem.Type.BOOTS));

    public static final DeferredItem<BlockItem> TAILORING_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.TAILORING_TABLE);

    private ModItems() {
    }
}
