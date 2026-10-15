package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
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
    public static void creativeTabIsRegistered(GameTestHelper helper) {
        if (!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(BleachMod.id("bleach"))) {
            helper.fail("bleach:bleach is not in the creative tab registry");
        }
        helper.succeed();
    }
}
