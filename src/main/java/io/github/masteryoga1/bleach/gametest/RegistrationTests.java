package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
    public static void creativeTabIsRegistered(GameTestHelper helper) {
        if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(BleachMod.id("bleach"))) {
            helper.fail("bleach:bleach is not in the creative tab registry");
        }
        helper.succeed();
    }
}
