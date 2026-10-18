package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.item.DenreishinkiItem;
import io.github.masteryoga1.bleach.item.EmptyGintoItem;
import io.github.masteryoga1.bleach.item.GintoItem;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.item.QuincyGarbItem;
import io.github.masteryoga1.bleach.item.SanreiGloveItem;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's items. The creative tab lists them all, in this order. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BleachMod.MODID);

    /**
     * The nameless blade every Shinigami starts with, issued by the Academy and formed for a new
     * Substitute: an iron-tier katana that never breaks. Carried long enough it takes on its wielder's
     * soul, and once they learn its name it becomes their own zanpakutō, with the spirit's name, look
     * and Shikai. See {@link ZanpakutoItem}.
     */
    public static final DeferredItem<ZanpakutoItem> ASAUCHI = ITEMS.register("asauchi",
            () -> new ZanpakutoItem(Tiers.IRON, new Item.Properties()
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

    /** The soul pager every Shinigami carries. See {@link DenreishinkiItem}. */
    public static final DeferredItem<DenreishinkiItem> DENREISHINKI = ITEMS.register("denreishinki",
            () -> new DenreishinkiItem(new Item.Properties().stacksTo(1)));

    /**
     * The silver cross handed down in Quincy families. In Quincy hands it draws the Heilig Bogen, a bow
     * of reishi; to anyone else it is only silver. See {@link QuincyCrossItem}.
     */
    public static final DeferredItem<QuincyCrossItem> QUINCY_CROSS = ITEMS.register("quincy_cross",
            () -> new QuincyCrossItem(new Item.Properties().stacksTo(1)));

    /** The white Quincy garb, trimmed in blue: tunic, trousers and shoes. See {@link QuincyGarbItem}. */
    public static final DeferredItem<QuincyGarbItem> QUINCY_TUNIC = ITEMS.register("quincy_tunic",
            () -> new QuincyGarbItem(ArmorItem.Type.CHESTPLATE));
    public static final DeferredItem<QuincyGarbItem> QUINCY_TROUSERS = ITEMS.register("quincy_trousers",
            () -> new QuincyGarbItem(ArmorItem.Type.LEGGINGS));
    public static final DeferredItem<QuincyGarbItem> QUINCY_SHOES = ITEMS.register("quincy_shoes",
            () -> new QuincyGarbItem(ArmorItem.Type.BOOTS));

    /**
     * The Sanrei Glove, handed down to a Quincy of the third tier: worn, it fights every draw until a
     * week of training masters it, and torn off it burns the power out in Letzt Stil. Wearing it puts
     * it on the soul, not in a slot. See {@link SanreiGloveItem}.
     */
    public static final DeferredItem<SanreiGloveItem> SANREI_GLOVE = ITEMS.register("sanrei_glove",
            () -> new SanreiGloveItem(new Item.Properties().stacksTo(1)));

    /**
     * A Seele Schneider hilt. In a restored Quincy's main hand it forms a blade of reishi; in the
     * other hand it is loosed from Ginrei Kojaku. See {@link SeeleSchneiderItem}.
     */
    public static final DeferredItem<SeeleSchneiderItem> SEELE_SCHNEIDER = ITEMS.register("seele_schneider",
            () -> new SeeleSchneiderItem(new Item.Properties().stacksTo(16)));

    /** A silver tube waiting to be filled with reishi. See {@link EmptyGintoItem}. */
    public static final DeferredItem<EmptyGintoItem> EMPTY_GINTO = ITEMS.register("empty_ginto",
            () -> new EmptyGintoItem(new Item.Properties().stacksTo(16)));

    /** A Gintō full of stored reishi, broken to cast a spell, even with the power sealed. See {@link GintoItem}. */
    public static final DeferredItem<GintoItem> GINTO = ITEMS.register("ginto",
            () -> new GintoItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<DeferredSpawnEggItem> ACADEMY_INSTRUCTOR_SPAWN_EGG = ITEMS.register(
            "academy_instructor_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ACADEMY_INSTRUCTOR, 0x1A1A1A, 0xF2F2F2, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> WOUNDED_SHINIGAMI_SPAWN_EGG = ITEMS.register(
            "wounded_shinigami_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WOUNDED_SHINIGAMI, 0x1A1A1A, 0x8B1A1A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SPIDER_HOLLOW_SPAWN_EGG = ITEMS.register(
            "spider_hollow_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SPIDER_HOLLOW, 0x1E1C22, 0xEAE6DA, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> HEXAPODUS_SPAWN_EGG = ITEMS.register(
            "hexapodus_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.HEXAPODUS, 0x68745C, 0xD6782A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> HOUND_HOLLOW_SPAWN_EGG = ITEMS.register(
            "hound_hollow_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.HOUND_HOLLOW, 0x2A282E, 0xEAE6DA, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SERPENT_HOLLOW_SPAWN_EGG = ITEMS.register(
            "serpent_hollow_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SERPENT_HOLLOW, 0x28342C, 0xEAE6DA, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FISHBONE_D_SPAWN_EGG = ITEMS.register(
            "fishbone_d_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FISHBONE_D, 0x464E5A, 0xEAE6DA, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> PLUS_SPAWN_EGG = ITEMS.register(
            "plus_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.PLUS, 0xB4C8D8, 0x6E6E6E, new Item.Properties()));

    private ModItems() {
    }
}
