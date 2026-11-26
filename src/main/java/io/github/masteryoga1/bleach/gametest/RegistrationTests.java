package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.ShriekerLeech;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import io.github.masteryoga1.bleach.item.EmptyGintoItem;
import io.github.masteryoga1.bleach.item.GintoItem;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.item.QuincyGarbItem;
import io.github.masteryoga1.bleach.item.SanreiGloveItem;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModWorldgen;
import io.github.masteryoga1.bleach.soul.HikotsuTaiho;
import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.world.gen.RadialDensity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
    public static void sekkisekiIsRegistered(GameTestHelper helper) {
        Block sekkiseki = ModBlocks.SEKKISEKI.get();
        helper.assertTrue(BuiltInRegistries.BLOCK.get(BleachMod.id("sekkiseki")) == sekkiseki
                && sekkiseki.asItem() == ModItems.SEKKISEKI.get(), "Sekkiseki is not its block, or has no item");
        helper.assertTrue(sekkiseki.defaultDestroyTime() < 0.0F, "Sekkiseki can be broken in survival");
        helper.assertTrue(sekkiseki.getExplosionResistance() >= 3_600_000.0F, "a blast can break Sekkiseki");
        BlockState state = sekkiseki.defaultBlockState();
        for (TagKey<Block> tag : List.of(BleachTags.NEGATES_REIRYOKU, BlockTags.WITHER_IMMUNE, BlockTags.DRAGON_IMMUNE,
                BlockTags.FEATURES_CANNOT_REPLACE)) {
            helper.assertTrue(state.is(tag), "Sekkiseki is not in #" + tag.location());
        }
        helper.assertFalse(state.isValidSpawn(helper.getLevel(), BlockPos.ZERO, EntityType.ZOMBIE),
                "a zombie may spawn on Sekkiseki");
        LootTable drops = helper.getLevel().getServer().reloadableRegistries().getLootTable(sekkiseki.getLootTable());
        helper.assertTrue(drops != LootTable.EMPTY, "Sekkiseki has no loot table");
        helper.assertTrue(BuiltInRegistries.DENSITY_FUNCTION_TYPE.get(BleachMod.id("radial")) == RadialDensity.DATA_CODEC
                        && BuiltInRegistries.FEATURE.get(BleachMod.id("seireiheki")) == ModWorldgen.SEIREIHEKI.get()
                        && BuiltInRegistries.FEATURE.get(BleachMod.id("seireitei_streets"))
                                == ModWorldgen.SEIREITEI_STREETS.get(),
                "bleach:radial, bleach:seireiheki or bleach:seireitei_streets is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hollowsAndPlusesAreRegistered(GameTestHelper helper) {
        for (String name : List.of("spider_hollow", "hexapodus", "hound_hollow", "serpent_hollow", "fishbone_d", "plus",
                "garganta", "shrieker", "shrieker_leech", "grand_fisher", "grand_fisher_lure", "hell_gate")) {
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(BleachMod.id(name)), "bleach:" + name + " is not an entity type");
        }
        for (String name : List.of("spider_hollow_spawn_egg", "hexapodus_spawn_egg", "hound_hollow_spawn_egg",
                "serpent_hollow_spawn_egg", "fishbone_d_spawn_egg", "plus_spawn_egg", "denreishinki")) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(BleachMod.id(name)), "bleach:" + name + " is not an item");
        }
        for (String name : List.of("entity.hollow.ambient", "entity.hollow.hurt", "entity.hollow.death",
                "entity.hollow.mask_crack", "entity.fishbone_d.roar", "entity.garganta.open", "item.denreishinki.ping",
                "entity.shrieker.ambient", "entity.shrieker.hurt", "entity.shrieker.death", "entity.shrieker.swoop",
                "entity.hell_gate.rise", "entity.hell_gate.open", "entity.hell_gate.blade", "entity.hell_gate.laugh",
                "entity.hell_gate.slam", "entity.hell_gate.crumble",
                "entity.shrieker.flap", "entity.shrieker.loose", "entity.shrieker_leech.cling", "entity.shrieker.ring",
                "entity.shrieker_leech.burst", "entity.grand_fisher.ambient", "entity.grand_fisher.hurt",
                "entity.grand_fisher.death", "entity.grand_fisher_lure.sob", "entity.grand_fisher_lure.cry")) {
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(BleachMod.id(name)), "bleach:" + name + " is not a sound event");
        }
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(BleachMod.id("denreishinki")).isPresent(),
                "the Denreishinki has no recipe");
        EntityType<?> leech = ModEntities.SHRIEKER_LEECH.get();
        helper.assertTrue(leech.is(SoulLogic.SPIRITS) && !leech.canSerialize() && !leech.canSummon(),
                "bleach:shrieker_leech is not a spirit, or is saved or summoned");
        EntityType<?> fisher = ModEntities.GRAND_FISHER.get();
        helper.assertTrue(!fisher.canSerialize() && fisher.canSummon(),
                "bleach:grand_fisher is saved, or cannot be summoned");
        EntityType<?> lure = ModEntities.GRAND_FISHER_LURE.get();
        helper.assertTrue(lure.is(SoulLogic.SPIRITS) && !lure.is(BleachTags.HOLLOWS) && !lure.canSerialize()
                && !lure.canSummon(), "bleach:grand_fisher_lure is no spirit, is a Hollow, or is saved or summoned");
        helper.assertTrue(lure.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER), "bleach:grand_fisher_lure drowns");
        EntityType<?> gate = ModEntities.HELL_GATE.get();
        helper.assertTrue(gate.is(SoulLogic.SPIRITS) && !gate.canSerialize() && !gate.canSummon(),
                "bleach:hell_gate is not a spirit, or is saved or summoned");
        Optional<Holder.Reference<DamageType>> burst = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ShriekerLeech.BURST);
        // Every leech that bursts at once lands, through the hurt cooldown the one before it left.
        helper.assertTrue(burst.isPresent() && burst.get().is(DamageTypeTags.BYPASSES_COOLDOWN)
                        && !burst.get().is(DamageTypeTags.IS_EXPLOSION) && !burst.get().is(BleachTags.PURIFIES),
                "the leech burst's damage type did not load, keeps to the hurt cooldown, or explodes or purifies");
        helper.succeed();
    }

    /**
     * The Asauchi as a zanpakutō, its blade data, the Shikai's two entities, the damage of Getsuga Tenshō, of Hihiō
     * Zabimaru's passage and of Hikotsu Taihō, and the new sounds.
     */
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
        List<ResourceKey<DamageType>> arts =
                List.of(GetsugaTensho.DAMAGE_TYPE, ZabimaruLash.SERPENT_DAMAGE, HikotsuTaiho.DAMAGE);
        for (ResourceKey<DamageType> art : arts) {
            Optional<Holder.Reference<DamageType>> damage = helper.getLevel().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolder(art);
            helper.assertTrue(damage.isPresent(), art.location() + " did not load");
            helper.assertTrue(damage.get().is(BleachTags.PURIFIES), art.location() + " does not purify the Hollows it kills");
            helper.assertFalse(damage.get().is(DamageTypeTags.IS_PROJECTILE), art.location() + " counts as a projectile");
        }
        for (String name : List.of("item.zanpakuto.whisper", "item.zanpakuto.jinzen", "item.zanpakuto.release",
                "item.zanpakuto.seal", "item.zabimaru.lash", "entity.getsuga_tensho.gather", "entity.getsuga_tensho.fire")) {
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(BleachMod.id(name)), "bleach:" + name + " is not a sound event");
        }
        helper.succeed();
    }

    /**
     * The Quincy Cross and garb, the Heilig Pfeil and its damage, the damage that tears a soul apart,
     * the Quincy garb's armour material, the Cross's recipe and the Quincy sounds. Then the arts past
     * the third tier: the Sanrei Glove, the Seele Schneider and the Gintō with their recipes, the
     * Letzt Pfeil, the blade in flight and the Gintō cast (spirits all three), Gritz, and the six
     * new kinds of damage, each of which destroys the Hollow it kills.
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

        Map<String, Item> arts = Map.of(
                "sanrei_glove", ModItems.SANREI_GLOVE.get(),
                "seele_schneider", ModItems.SEELE_SCHNEIDER.get(),
                "empty_ginto", ModItems.EMPTY_GINTO.get(),
                "ginto", ModItems.GINTO.get());
        arts.forEach((name, item) -> helper.assertTrue(BuiltInRegistries.ITEM.get(BleachMod.id(name)) == item,
                "bleach:" + name + " is registered as " + BuiltInRegistries.ITEM.get(BleachMod.id(name))));
        helper.assertTrue(ModItems.SANREI_GLOVE.get() instanceof SanreiGloveItem
                        && ModItems.SEELE_SCHNEIDER.get() instanceof SeeleSchneiderItem
                        && ModItems.EMPTY_GINTO.get() instanceof EmptyGintoItem
                        && ModItems.GINTO.get() instanceof GintoItem,
                "an item of the Quincy arts is not of its own kind");
        Map<String, EntityType<?>> artsEntities = Map.of(
                "seele_schneider", ModEntities.SEELE_SCHNEIDER.get(),
                "letzt_pfeil", ModEntities.LETZT_PFEIL.get(),
                "ginto_cast", ModEntities.GINTO_CAST.get());
        artsEntities.forEach((name, type) -> {
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(BleachMod.id(name))
                    && BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id(name)) == type, "bleach:" + name + " is not its entity type");
            helper.assertTrue(type.is(SoulLogic.SPIRITS), "bleach:" + name + " is not in #bleach:spirits, so the unaware could see it");
        });
        helper.assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(BleachMod.id("gritz"))
                        && BuiltInRegistries.MOB_EFFECT.get(BleachMod.id("gritz")) == ModEffects.GRITZ.get(),
                "bleach:gritz is not the Gritz effect");
        for (String recipe : List.of("seele_schneider", "empty_ginto", "tailoring/sanrei_glove")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(BleachMod.id(recipe)).isPresent(),
                    "bleach:" + recipe + " is not a recipe");
        }

        List<ResourceKey<DamageType>> artsDamage = List.of(HeiligPfeil.VOLLEY_DAMAGE_TYPE, SeeleSchneiderPfeil.DAMAGE_TYPE,
                LetztPfeil.DAMAGE_TYPE, GintoCast.HEIZEN, GintoCast.SPRENGER, QuincyArtsEvents.SKLAVEREI);
        List<ResourceKey<DamageType>> projectiles = List.of(HeiligPfeil.VOLLEY_DAMAGE_TYPE, SeeleSchneiderPfeil.DAMAGE_TYPE,
                LetztPfeil.DAMAGE_TYPE);
        // A stream's arrows and a Sklaverei pulse each land through the last blow's invulnerability frames.
        List<ResourceKey<DamageType>> pastCooldown = List.of(HeiligPfeil.VOLLEY_DAMAGE_TYPE, QuincyArtsEvents.SKLAVEREI);
        for (ResourceKey<DamageType> key : artsDamage) {
            Optional<Holder.Reference<DamageType>> type = damageTypes.getHolder(key);
            String name = key.location().toString();
            helper.assertTrue(type.isPresent(), "the " + name + " damage type did not load");
            helper.assertTrue(type.get().is(BleachTags.DESTROYS_SOULS), name + " does not destroy the Hollows it kills");
            helper.assertFalse(type.get().is(BleachTags.PURIFIES), name + " purifies the Hollows it kills");
            helper.assertTrue(!projectiles.contains(key) || type.get().is(DamageTypeTags.IS_PROJECTILE),
                    name + " does not count as a projectile");
            helper.assertTrue(!pastCooldown.contains(key) || type.get().is(DamageTypeTags.BYPASSES_COOLDOWN),
                    name + " does not pass the hurt cooldown");
        }

        for (String name : List.of("item.quincy_cross.draw", "item.quincy_cross.awaken", "item.quincy_cross.fizzle",
                "entity.heilig_pfeil.fire", "entity.heilig_pfeil.hit", "player.hirenkyaku", "player.blut",
                "player.hollow_poison", "item.quincy_cross.charged", "item.sanrei_glove.tear", "player.letzt_stil",
                "player.quincy_sealed", "player.quincy_restored", "item.seele_schneider.form", "entity.seele_schneider.stick",
                "item.ginto.fill", "item.ginto.cast", "entity.ginto.heizen", "entity.ginto.sprenger", "player.licht_regen",
                "player.vollstandig", "player.sklaverei")) {
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(BleachMod.id(name)), "bleach:" + name + " is not a sound event");
        }
        helper.succeed();
    }

    /**
     * The Quincy in the world: the mask fragment and the Hollow Bait, the Wandenreich uniform and its
     * armour material, the two Quincy NPCs' eggs, the bait's recipe, and the tag of hindrances that
     * Ransōtengai's threads move a body through.
     */
    @GameTest(template = "empty")
    public static void quincyWorldPartsAreRegistered(GameTestHelper helper) {
        Map<String, Item> items = Map.of(
                "mask_fragment", ModItems.MASK_FRAGMENT.get(),
                "hollow_bait", ModItems.HOLLOW_BAIT.get(),
                "wandenreich_cap", ModItems.WANDENREICH_CAP.get(),
                "wandenreich_coat", ModItems.WANDENREICH_COAT.get(),
                "wandenreich_trousers", ModItems.WANDENREICH_TROUSERS.get(),
                "wandenreich_boots", ModItems.WANDENREICH_BOOTS.get(),
                "quincy_elder_spawn_egg", ModItems.QUINCY_ELDER_SPAWN_EGG.get(),
                "quincy_hunter_spawn_egg", ModItems.QUINCY_HUNTER_SPAWN_EGG.get());
        items.forEach((name, item) -> helper.assertTrue(BuiltInRegistries.ITEM.containsKey(BleachMod.id(name))
                        && BuiltInRegistries.ITEM.get(BleachMod.id(name)) == item,
                "bleach:" + name + " is not its item"));
        for (String name : List.of("wandenreich_cap", "wandenreich_coat", "wandenreich_trousers", "wandenreich_boots")) {
            helper.assertTrue(BuiltInRegistries.ITEM.get(BleachMod.id(name)) instanceof QuincyGarbItem,
                    "bleach:" + name + " is not a piece of Quincy garb");
        }
        helper.assertTrue(BuiltInRegistries.ARMOR_MATERIAL.containsKey(BleachMod.id("wandenreich")),
                "the Wandenreich uniform's armour material is not registered");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(BleachMod.id("hollow_bait")).isPresent(),
                "the Hollow Bait has no recipe");
        helper.assertTrue(MobEffects.MOVEMENT_SLOWDOWN.is(BleachTags.RANSOTENGAI_FREES),
                "slowness is not in #bleach:ransotengai_frees, so Ransōtengai would not free a slowed body");
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
