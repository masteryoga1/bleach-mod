package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.QuincyGarbItem;
import io.github.masteryoga1.bleach.menu.TailoringMenu;
import io.github.masteryoga1.bleach.menu.TailoringPages;
import io.github.masteryoga1.bleach.recipe.TailoringInput;
import io.github.masteryoga1.bleach.recipe.TailoringRecipe;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModRecipes;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Shihakushō, the Quincy garb and the Wandenreich uniform, the Tailoring Table and its
 * pages, and the sheathed blade.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GarbTests {
    private static final double EPSILON = 1.0E-6;
    private static final long NOW = 100_000L;

    private GarbTests() {
    }

    private static TailoringRecipe recipe(GameTestHelper helper, String name) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager().byKey(BleachMod.id("tailoring/" + name))
                .orElseThrow(() -> new IllegalStateException("bleach:tailoring/" + name + " did not load"));
        if (!(holder.value() instanceof TailoringRecipe recipe)) {
            throw new IllegalStateException("bleach:tailoring/" + name + " is not a tailoring recipe");
        }
        return recipe;
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static void wearFullSet(Player player) {
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SHIHAKUSHO_KOSODE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SHIHAKUSHO_HAKAMA.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.WARAJI.get()));
    }

    @GameTest(template = "empty")
    public static void tailoringRecipesLoad(GameTestHelper helper) {
        List<RecipeHolder<TailoringRecipe>> recipes =
                helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.TAILORING.get());
        Set<Item> results = recipes.stream()
                .map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()).getItem())
                .collect(Collectors.toSet());
        helper.assertTrue(results.equals(Set.of(ModItems.SHIHAKUSHO_KOSODE.get(), ModItems.SHIHAKUSHO_HAKAMA.get(),
                ModItems.WARAJI.get(), ModItems.QUINCY_TUNIC.get(), ModItems.QUINCY_TROUSERS.get(),
                ModItems.QUINCY_SHOES.get(), ModItems.SANREI_GLOVE.get(), ModItems.WANDENREICH_CAP.get(),
                ModItems.WANDENREICH_COAT.get(), ModItems.WANDENREICH_TROUSERS.get(), ModItems.WANDENREICH_BOOTS.get())),
                "tailoring makes " + results);
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(BleachMod.id("tailoring_table")).isPresent(),
                "the Tailoring Table cannot be crafted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tailoringNeedsEnoughCloth(GameTestHelper helper) {
        TailoringRecipe kosode = recipe(helper, "shihakusho_kosode");
        helper.assertTrue(kosode.matches(new TailoringInput(stack(Items.BLACK_WOOL, 5), stack(Items.WHITE_WOOL, 1)),
                helper.getLevel()), "5 black and 1 white wool do not make a kosode");
        helper.assertFalse(kosode.matches(new TailoringInput(stack(Items.BLACK_WOOL, 4), stack(Items.WHITE_WOOL, 1)),
                helper.getLevel()), "4 black wool made a kosode");
        helper.assertFalse(kosode.matches(new TailoringInput(stack(Items.BLACK_WOOL, 5), ItemStack.EMPTY),
                helper.getLevel()), "a kosode was made without its white collar");
        helper.assertFalse(kosode.matches(new TailoringInput(stack(Items.BLACK_WOOL, 5), stack(Items.RED_WOOL, 1)),
                helper.getLevel()), "red wool passed for white");

        TailoringRecipe hakama = recipe(helper, "shihakusho_hakama");
        helper.assertTrue(hakama.matches(new TailoringInput(stack(Items.BLACK_WOOL, 4), stack(Items.DIRT, 3)),
                helper.getLevel()), "the hakama, which needs no trim, looked at the trim slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tailoringMenuMakesAKosode(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TailoringMenu menu = new TailoringMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        int kosode = -1;
        for (int i = 0; i < menu.recipes().size(); i++) {
            if (menu.recipes().get(i).value().result().is(ModItems.SHIHAKUSHO_KOSODE.get())) {
                kosode = i;
            }
        }
        helper.assertTrue(kosode >= 0, "the menu has no kosode button");

        menu.getSlot(TailoringMenu.CLOTH_SLOT).set(stack(Items.BLACK_WOOL, 6));
        menu.getSlot(TailoringMenu.TRIM_SLOT).set(stack(Items.WHITE_WOOL, 1));
        helper.assertTrue(menu.getSlot(TailoringMenu.RESULT_SLOT).getItem().isEmpty(), "a garment appeared before one was picked");
        helper.assertTrue(menu.canMake(kosode), "the cloth on the table is not enough for a kosode");
        menu.clickMenuButton(player, kosode);
        helper.assertTrue(menu.getSlot(TailoringMenu.RESULT_SLOT).getItem().is(ModItems.SHIHAKUSHO_KOSODE.get()),
                "picking the kosode made " + menu.getSlot(TailoringMenu.RESULT_SLOT).getItem());

        ItemStack taken = menu.quickMoveStack(player, TailoringMenu.RESULT_SLOT);
        helper.assertTrue(taken.is(ModItems.SHIHAKUSHO_KOSODE.get()), "shift-clicking the result took " + taken);
        helper.assertTrue(menu.getSlot(TailoringMenu.CLOTH_SLOT).getItem().getCount() == 1,
                "the kosode left " + menu.getSlot(TailoringMenu.CLOTH_SLOT).getItem() + " of cloth");
        helper.assertTrue(menu.getSlot(TailoringMenu.TRIM_SLOT).getItem().isEmpty(), "the trim was not used up");
        helper.assertTrue(menu.getSlot(TailoringMenu.RESULT_SLOT).getItem().isEmpty(),
                "a second kosode appeared from one black wool");
        helper.assertTrue(player.getInventory().countItem(ModItems.SHIHAKUSHO_KOSODE.get()) == 1,
                "the kosode did not reach the inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shihakushoIsCosmetic(GameTestHelper helper) {
        for (Item piece : List.of(ModItems.SHIHAKUSHO_KOSODE.get(), ModItems.SHIHAKUSHO_HAKAMA.get(), ModItems.WARAJI.get())) {
            ItemStack stack = new ItemStack(piece);
            helper.assertFalse(stack.isDamageableItem(), stack + " wears out");
            helper.assertTrue(stack.getMaxStackSize() == 1, stack + " stacks to " + stack.getMaxStackSize());
            helper.assertTrue(stack.getAttributeModifiers().modifiers().isEmpty(),
                    stack + " changes attributes: " + stack.getAttributeModifiers().modifiers());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quincyGarbIsCosmetic(GameTestHelper helper) {
        List<Item> pieces = List.of(ModItems.QUINCY_TUNIC.get(), ModItems.QUINCY_TROUSERS.get(), ModItems.QUINCY_SHOES.get());
        for (Item piece : pieces) {
            ItemStack stack = new ItemStack(piece);
            helper.assertTrue(stack.is(BleachTags.QUINCY_GARB), stack + " is not in #bleach:quincy_garb");
            helper.assertFalse(stack.isDamageableItem(), stack + " wears out");
            helper.assertTrue(stack.getMaxStackSize() == 1, stack + " stacks to " + stack.getMaxStackSize());
            helper.assertTrue(stack.getAttributeModifiers().modifiers().isEmpty(),
                    stack + " changes attributes: " + stack.getAttributeModifiers().modifiers());
        }
        ItemStack tunic = new ItemStack(ModItems.QUINCY_TUNIC.get());
        helper.assertTrue(ModItems.QUINCY_TUNIC.get().getDefaultAttributeModifiers(tunic).equals(ItemAttributeModifiers.EMPTY),
                "the Quincy tunic has attributes of its own");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, tunic);
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.QUINCY_TROUSERS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.QUINCY_SHOES.get()));
        helper.assertTrue(QuincyGarbItem.isFullSet(player), "the whole Quincy garb is not a full set");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertFalse(QuincyGarbItem.isFullSet(player), "two pieces of the Quincy garb made a full set");

        // Neither garb counts as the other.
        helper.assertFalse(tunic.is(BleachTags.SHINIGAMI_GARB), "the Quincy tunic counts as the Shihakushō");
        helper.assertFalse(new ItemStack(ModItems.SHIHAKUSHO_KOSODE.get()).is(BleachTags.QUINCY_GARB),
                "the kosode counts as the Quincy garb");
        helper.succeed();
    }

    /** The Wandenreich uniform is a Quincy garb too, but for its cap, which is worn on the head. */
    @GameTest(template = "empty")
    public static void wandenreichUniformCountsAsGarb(GameTestHelper helper) {
        List<Item> worn = List.of(ModItems.WANDENREICH_COAT.get(), ModItems.WANDENREICH_TROUSERS.get(),
                ModItems.WANDENREICH_BOOTS.get());
        for (Item piece : worn) {
            helper.assertTrue(new ItemStack(piece).is(BleachTags.QUINCY_GARB), piece + " is not in #bleach:quincy_garb");
        }
        helper.assertFalse(new ItemStack(ModItems.WANDENREICH_CAP.get()).is(BleachTags.QUINCY_GARB),
                "the Wandenreich cap is in #bleach:quincy_garb");
        for (Item piece : List.of(ModItems.WANDENREICH_CAP.get(), ModItems.WANDENREICH_COAT.get(),
                ModItems.WANDENREICH_TROUSERS.get(), ModItems.WANDENREICH_BOOTS.get())) {
            helper.assertTrue(new ItemStack(piece).is(BleachTags.WANDENREICH_UNIFORM), piece + " is not in #bleach:wandenreich_uniform");
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WANDENREICH_COAT.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.QUINCY_TROUSERS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.WANDENREICH_BOOTS.get()));
        helper.assertTrue(QuincyGarbItem.isFullSet(player), "the Wandenreich coat and boots with the white trousers are not a full garb");
        helper.assertFalse(QuincyGarbItem.isWandenreichSet(player), "the white trousers passed for the Wandenreich's");
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.WANDENREICH_TROUSERS.get()));
        helper.assertTrue(QuincyGarbItem.isWandenreichSet(player), "the whole Wandenreich uniform is not a set");

        for (String name : List.of("wandenreich_cap", "wandenreich_coat", "wandenreich_trousers", "wandenreich_boots")) {
            ItemStack made = recipe(helper, name).getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(made.getItem()).equals(BleachMod.id(name)),
                    "bleach:tailoring/" + name + " makes " + made);
        }
        helper.succeed();
    }

    /** Eight buttons a page: every recipe on the table is on one page, in one place. */
    @GameTest(template = "empty")
    public static void tailoringPagesReachEveryRecipe(GameTestHelper helper) {
        helper.assertTrue(TailoringPages.pageCount(11) == 2, "11 recipes fill " + TailoringPages.pageCount(11) + " pages");
        helper.assertTrue(TailoringPages.pageCount(0) == 1, "an empty table has " + TailoringPages.pageCount(0) + " pages");
        helper.assertTrue(TailoringPages.indexAt(1, 0, 11) == 8, "the second page starts at " + TailoringPages.indexAt(1, 0, 11));
        helper.assertTrue(TailoringPages.indexAt(1, 3, 11) == -1,
                "the fourth button of the second page of 11 is " + TailoringPages.indexAt(1, 3, 11));
        helper.assertTrue(TailoringPages.pageOf(10) == 1, "the eleventh recipe is on page " + TailoringPages.pageOf(10));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int recipes = new TailoringMenu(1, player.getInventory(), ContainerLevelAccess.NULL).recipes().size();
        helper.assertTrue(recipes >= 11, "the Tailoring Table has only " + recipes + " recipes");
        int[] shown = new int[recipes];
        for (int page = 0; page < TailoringPages.pageCount(recipes); page++) {
            for (int slot = 0; slot < TailoringPages.PER_PAGE; slot++) {
                int index = TailoringPages.indexAt(page, slot, recipes);
                if (index >= 0) {
                    shown[index]++;
                }
            }
        }
        for (int index = 0; index < recipes; index++) {
            helper.assertTrue(shown[index] == 1, "recipe " + index + " is shown " + shown[index] + " times");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void asauchiNeverBreaks(GameTestHelper helper) {
        ItemStack asauchi = new ItemStack(ModItems.ASAUCHI.get());
        helper.assertFalse(asauchi.isDamageableItem(), "the Asauchi can be damaged");
        asauchi.hurtAndBreak(1000, helper.getLevel(), (ServerPlayer) null, item -> { });
        helper.assertTrue(asauchi.getDamageValue() == 0 && !asauchi.isEmpty(), "the Asauchi took damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSetRaisesRegeneration(GameTestHelper helper) {
        double bonus = 1.0 + BleachConfig.get(BleachConfig.SHIHAKUSHO_SET_REGEN_BONUS);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        wearFullSet(player);
        SoulData shinigami = ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F);
        helper.assertTrue(Math.abs(ShinigamiLogic.regenMultiplier(player, shinigami, NOW) - bonus) < EPSILON,
                "a Shinigami in the full set regenerates at " + ShinigamiLogic.regenMultiplier(player, shinigami, NOW));
        helper.assertTrue(Math.abs(ShinigamiLogic.regenMultiplier(player, ShinigamiTests.student(40.0F), NOW) - bonus) < EPSILON,
                "a student in the full set misses the bonus");
        helper.assertTrue(Math.abs(ShinigamiLogic.regenMultiplier(player, ShinigamiTests.substitute(40.0F), NOW) - bonus) < EPSILON,
                "a Substitute in the full set misses the bonus");
        helper.assertTrue(ShinigamiLogic.regenMultiplier(player, ShinigamiTests.soul(SoulState.HUMAN, 40.0F), NOW) == 1.0,
                "an ordinary human gets the Shihakushō's bonus");

        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertTrue(ShinigamiLogic.regenMultiplier(player, shinigami, NOW) == 1.0,
                "two pieces of the Shihakushō gave the set bonus");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sheathedBladeShowsOnlyWhenNotHeld(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        SoulData data = ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F);
        helper.assertFalse(ShinigamiLogic.hasSheathedBlade(player), "an empty-handed soul wears a blade");

        inventory.setItem(3, new ItemStack(ModItems.ASAUCHI.get()));
        inventory.selected = 0;
        helper.assertTrue(ShinigamiLogic.hasSheathedBlade(player), "a blade in the hotbar is not worn at the hip");
        helper.assertTrue(SoulAppearance.of(player, data).bladeSheathed(), "the appearance hides the sheathed blade");

        inventory.selected = 3;
        helper.assertFalse(ShinigamiLogic.hasSheathedBlade(player), "the blade in hand is also at the hip");
        helper.assertFalse(SoulAppearance.of(player, data).bladeSheathed(), "the appearance sheathes the drawn blade");

        inventory.selected = 0;
        inventory.setItem(3, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.ASAUCHI.get()));
        helper.assertFalse(ShinigamiLogic.hasSheathedBlade(player), "a blade in the off hand is also at the hip");

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        inventory.setItem(20, new ItemStack(ModItems.ASAUCHI.get()));
        helper.assertFalse(ShinigamiLogic.hasSheathedBlade(player), "a blade packed away in the inventory is at the hip");
        helper.assertFalse(SoulAppearance.of(data).bladeSheathed(), "the soul alone shows a blade");
        helper.succeed();
    }
}
