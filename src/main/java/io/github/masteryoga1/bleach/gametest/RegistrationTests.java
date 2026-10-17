package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.item.QuincyGarbItem;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Smoke tests run by {@code ./gradlew runGameTestServer}. They only exist in the game when the
 * {@code bleach} namespace is enabled for game tests, so they never load in normal play.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class RegistrationTests {
    private RegistrationTests() {
    }

    @GameTest(template = "empty")
    public static void asauchiIsRegistered(GameTestHelper helper) {
        if (!BuiltInRegistries.ITEM.containsKey(BleachMod.id("asauchi"))) {
            helper.fail("bleach:asauchi is not in the item registry");
        }
        ItemStack stack = new ItemStack(ModItems.ASAUCHI.get());
        if (stack.getMaxDamage() != Tiers.IRON.getUses()) {
            helper.fail("Asauchi should have iron durability but has " + stack.getMaxDamage());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void garbAndTailoringAreRegistered(GameTestHelper helper) {
        for (String name : List.of("shihakusho_kosode", "shihakusho_hakama", "waraji", "tailoring_table")) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(BleachMod.id(name)), "bleach:" + name + " is not an item");
        }
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(BleachMod.id("tailoring_table")), "the Tailoring Table is not a block");
        helper.assertTrue(BuiltInRegistries.MENU.containsKey(BleachMod.id("tailoring")), "the tailoring menu is not registered");
        helper.assertTrue(BuiltInRegistries.RECIPE_TYPE.containsKey(BleachMod.id("tailoring")), "the tailoring recipe type is not registered");
        helper.assertTrue(BuiltInRegistries.RECIPE_SERIALIZER.containsKey(BleachMod.id("tailoring")),
                "the tailoring recipe serializer is not registered");
        helper.assertTrue(BuiltInRegistries.ARMOR_MATERIAL.containsKey(BleachMod.id("shihakusho")),
                "the Shihakushō armour material is not registered");
        LootTable drops = helper.getLevel().getServer().reloadableRegistries()
                .getLootTable(ModBlocks.TAILORING_TABLE.get().getLootTable());
        helper.assertTrue(drops != LootTable.EMPTY, "the Tailoring Table has no loot table");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reishiFootingHasNoItem(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(BleachMod.id("reishi_footing")), "reishi footing is not a block");
        helper.assertTrue(ModBlocks.REISHI_FOOTING.get().asItem() == Items.AIR, "reishi footing can be held as an item");
        helper.assertFalse(ModBlocks.REISHI_FOOTING.get().defaultBlockState().isAir(),
                "reishi footing counts as air, so the server would think players on it are flying");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hollowsAndPlusesAreRegistered(GameTestHelper helper) {
        for (String name : List.of("spider_hollow", "hexapodus", "hound_hollow", "serpent_hollow", "fishbone_d", "plus",
                "garganta")) {
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(BleachMod.id(name)), "bleach:" + name + " is not an entity type");
        }
        for (String name : List.of("spider_hollow_spawn_egg", "hexapodus_spawn_egg", "hound_hollow_spawn_egg",
                "serpent_hollow_spawn_egg", "fishbone_d_spawn_egg", "plus_spawn_egg", "denreishinki")) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(BleachMod.id(name)), "bleach:" + name + " is not an item");
        }
        for (String name : List.of("entity.hollow.ambient", "entity.hollow.hurt", "entity.hollow.death",
                "entity.hollow.mask_crack", "entity.fishbone_d.roar", "entity.garganta.open", "item.denreishinki.ping")) {
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(BleachMod.id(name)), "bleach:" + name + " is not a sound event");
        }
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(BleachMod.id("denreishinki")).isPresent(),
                "the Denreishinki has no recipe");
        helper.succeed();
    }

    /** The Asauchi as a zanpakutō, its blade data, the Shikai's two entities, Getsuga Tenshō's damage and the new sounds. */
    @GameTest(template = "empty")
    public static void zanpakutoPartsAreRegistered(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.ITEM.get(BleachMod.id("asauchi")) instanceof ZanpakutoItem,
                "bleach:asauchi is not a zanpakutō item");
        helper.assertTrue(ModComponents.ZANPAKUTO_BLADE.isBound()
                        && BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(BleachMod.id("zanpakuto_blade")),
                "the zanpakutō blade component is not registered");
        for (String name : List.of("getsuga_tensho", "zabimaru_lash")) {
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(BleachMod.id(name)), "bleach:" + name + " is not an entity type");
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id(name));
            helper.assertTrue(type.is(SoulLogic.SPIRITS), "bleach:" + name + " is not in #bleach:spirits, so the unaware could see it");
        }
        helper.assertTrue(ModEntities.GETSUGA_TENSHO.get() == BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("getsuga_tensho"))
                        && ModEntities.ZABIMARU_LASH.get() == BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("zabimaru_lash")),
                "the Getsuga Tenshō or Zabimaru's lash is registered under another name");
        Optional<Holder.Reference<DamageType>> damage = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolder(GetsugaTensho.DAMAGE_TYPE);
        helper.assertTrue(damage.isPresent(), "the Getsuga Tenshō damage type did not load");
        helper.assertTrue(damage.get().is(BleachTags.PURIFIES), "the Getsuga Tenshō does not purify the Hollows it kills");
        helper.assertFalse(damage.get().is(DamageTypeTags.IS_PROJECTILE), "the Getsuga Tenshō counts as a projectile");
        for (String name : List.of("item.zanpakuto.whisper", "item.zanpakuto.jinzen", "item.zanpakuto.release",
                "item.zanpakuto.seal", "item.zabimaru.lash", "entity.getsuga_tensho.gather", "entity.getsuga_tensho.fire")) {
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(BleachMod.id(name)), "bleach:" + name + " is not a sound event");
        }
        helper.succeed();
    }

    /**
     * The Quincy Cross and garb, the Heilig Pfeil and its damage, the damage that tears a soul apart,
     * the Quincy garb's armour material, the Cross's recipe and the Quincy sounds.
     */
    @GameTest(template = "empty")
    public static void quincyPartsAreRegistered(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.ITEM.get(BleachMod.id("quincy_cross")) instanceof QuincyCrossItem
                        && ModItems.QUINCY_CROSS.get() == BuiltInRegistries.ITEM.get(BleachMod.id("quincy_cross")),
                "bleach:quincy_cross is not the Quincy Cross");
        List<Item> garb = List.of(ModItems.QUINCY_TUNIC.get(), ModItems.QUINCY_TROUSERS.get(), ModItems.QUINCY_SHOES.get());
        List<String> garbNames = List.of("quincy_tunic", "quincy_trousers", "quincy_shoes");
        for (int i = 0; i < garbNames.size(); i++) {
            Item item = BuiltInRegistries.ITEM.get(BleachMod.id(garbNames.get(i)));
            helper.assertTrue(item instanceof QuincyGarbItem && item == garb.get(i),
                    "bleach:" + garbNames.get(i) + " is not a piece of the Quincy garb");
        }

        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(BleachMod.id("heilig_pfeil")),
                "bleach:heilig_pfeil is not an entity type");
        EntityType<?> arrow = BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("heilig_pfeil"));
        helper.assertTrue(arrow == ModEntities.HEILIG_PFEIL.get(), "the Heilig Pfeil is registered under another name");
        helper.assertTrue(arrow.is(SoulLogic.SPIRITS), "bleach:heilig_pfeil is not in #bleach:spirits, so the unaware could see it");
        helper.assertTrue(BuiltInRegistries.ARMOR_MATERIAL.containsKey(BleachMod.id("quincy_garb")),
                "the Quincy garb's armour material is not registered");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(BleachMod.id("quincy_cross")).isPresent(),
                "the Quincy Cross has no recipe");

        Registry<DamageType> damageTypes = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Optional<Holder.Reference<DamageType>> pfeil = damageTypes.getHolder(HeiligPfeil.DAMAGE_TYPE);
        helper.assertTrue(pfeil.isPresent(), "the Heilig Pfeil damage type did not load");
        helper.assertTrue(pfeil.get().is(DamageTypeTags.IS_PROJECTILE), "the Heilig Pfeil does not count as a projectile");
        helper.assertTrue(pfeil.get().is(BleachTags.DESTROYS_SOULS), "the Heilig Pfeil does not destroy the Hollows it kills");
        helper.assertFalse(pfeil.get().is(BleachTags.PURIFIES), "the Heilig Pfeil purifies the Hollows it kills");

        Optional<Holder.Reference<DamageType>> destruction = damageTypes.getHolder(QuincyEvents.SOUL_DESTRUCTION);
        helper.assertTrue(destruction.isPresent(), "the soul destruction damage type did not load");
        Map<String, TagKey<DamageType>> bypasses = Map.of(
                "invulnerability", DamageTypeTags.BYPASSES_INVULNERABILITY,
                "armour", DamageTypeTags.BYPASSES_ARMOR,
                "shields", DamageTypeTags.BYPASSES_SHIELD,
                "effects", DamageTypeTags.BYPASSES_EFFECTS,
                "enchantments", DamageTypeTags.BYPASSES_ENCHANTMENTS,
                "knockback", DamageTypeTags.NO_KNOCKBACK);
        bypasses.forEach((what, tag) -> helper.assertTrue(destruction.get().is(tag),
                "tearing a soul apart is stopped by " + what + " (not in " + tag.location() + ")"));

        for (String name : List.of("item.quincy_cross.draw", "item.quincy_cross.awaken", "item.quincy_cross.fizzle",
                "entity.heilig_pfeil.fire", "entity.heilig_pfeil.hit", "player.hirenkyaku", "player.blut",
                "player.hollow_poison")) {
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(BleachMod.id(name)), "bleach:" + name + " is not a sound event");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeTabIsRegistered(GameTestHelper helper) {
        if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(BleachMod.id("bleach"))) {
            helper.fail("bleach:bleach is not in the creative tab registry");
        }
        helper.succeed();
    }
}
