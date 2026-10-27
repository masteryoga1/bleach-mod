package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.ArrancarNpcEvents;
import io.github.masteryoga1.bleach.soul.ArrancarNpcLogic;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.ArrancarRoster;
import io.github.masteryoga1.bleach.soul.Hierro;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Arrancar NPCs: what they are, how a rank sets one up (a Número, or a Privaron Espada with its old
 * seat's number) and that none is ever seated, whom they hunt and whom their Cero and Bala spare, the Hierro that
 * guards them, the Fracción that guards its leader and outlives it, the recall that lifts a beaten Privaron Espada
 * and its Fracción away, and the kill that counts as slain. Mock players are plain {@link Player}s that are not in
 * the world and never tick, so the rules are called directly, and no Bala or Cero ever reaches one. Only the
 * Fracción test waits, with its NPCs' goals on; every other NPC is spawned without free will and checked in the
 * tick it was set up, before a tick of its own could change it.
 *
 * <p>Every creature a test spawns is taken away before the test ends, and the Fracción test takes its own away
 * even when a step fails, as its NPCs would otherwise go on fighting. A husk stands where a mundane target is
 * needed: a zombie would burn in the daylight that comes through the barrier.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ArrancarNpcTests {
    /** How closely a blow through the Hierro must match the share it should let through, in health. */
    private static final double HEALTH_EPSILON = 0.05;
    /** The seed of the rank rolls, so a failure can be run again. */
    private static final long ROLL_SEED = 5_808L;
    private static final int ROLLS = 500;
    /** A Número's band of maximum reiryoku and a Privaron Espada's floor ({@code [arrancar.npc.*]} defaults). */
    private static final float NUMERO_MIN_REIRYOKU = 500.0F;
    private static final float NUMERO_MAX_REIRYOKU = 900.0F;
    private static final float PRIVARON_MIN_REIRYOKU = 1100.0F;
    /** Where every mock player stands; none is in the world, so the spot matters to nothing. */
    private static final Vec3 MOCK_SPOT = new Vec3(1.5, 1.0, 1.5);

    private ArrancarNpcTests() {
    }

    // ---- Helpers ----

    /** An Arrancar NPC without free will at this spot, set up with this rank to stay until killed. */
    private static ArrancarNpc npc(GameTestHelper helper, ArrancarRank rank, int x, int y, int z) {
        ArrancarNpc npc = helper.spawnWithNoFreeWill(ModEntities.ARRANCAR.get(), x, y, z);
        npc.setUp(rank, helper.getLevel().getRandom(), ArrancarNpc.NEVER);
        return npc;
    }

    /** The same with its goals, so it follows, guards and fights. */
    private static ArrancarNpc fighter(GameTestHelper helper, ArrancarRank rank, int x, int y, int z) {
        ArrancarNpc npc = helper.spawn(ModEntities.ARRANCAR.get(), x, y, z);
        npc.setUp(rank, helper.getLevel().getRandom(), ArrancarNpc.NEVER);
        return npc;
    }

    /** A new Arrancar NPC, never set up and not in the world, for a saved one to be loaded into. */
    private static ArrancarNpc unloaded(GameTestHelper helper) {
        ArrancarNpc npc = ModEntities.ARRANCAR.get().create(helper.getLevel());
        helper.assertTrue(npc != null, "no Arrancar NPC could be made");
        return npc;
    }

    /** A mock player in this mode with a fresh soul of this state and this much reiryoku. */
    private static Player mock(GameTestHelper helper, GameType mode, SoulState state, float maxReiryoku) {
        return HollowPlayerTests.soulPlayer(helper, mode, state, maxReiryoku, MOCK_SPOT);
    }

    /** Writes this rank into the soul's own Arrancar record, as the roster would for a player. */
    private static void giveRank(SoulData data, ArrancarRank rank) {
        data.setHollow(data.hollow().withArrancar(data.hollow().arrancar().withRank(rank)));
    }

    /** Whether the server's Arrancar roster lists this creature. */
    private static boolean onRoster(GameTestHelper helper, Entity entity) {
        MinecraftServer server = helper.getLevel().getServer();
        return ArrancarRoster.get(server).entries(HollowPlayerTests.now(helper)).stream()
                .anyMatch(entry -> entry.id().equals(entity.getUUID()));
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** Every creature on this list, for a test that waits: some are added to it as the test goes. */
    private static void discardAll(List<Entity> spawned) {
        discard(spawned.toArray(new Entity[0]));
    }

    /** One step of a test that waits. Should it fail, every creature on the list is taken away first. */
    private static Runnable step(List<Entity> spawned, Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (RuntimeException e) {
                discardAll(spawned);
                throw e;
            }
        };
    }

    /** This test's structure, and a little room about it for what flew or fell out. */
    private static AABB around(GameTestHelper helper) {
        return helper.getBounds().inflate(2.0);
    }

    /** Every dropped item and orb about this test's structure. */
    private static void discardDrops(GameTestHelper helper) {
        AABB bounds = around(helper);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, bounds).forEach(Entity::discard);
    }

    /** Every Negación light in this test's structure, and the tears they opened. */
    private static void discardLights(GameTestHelper helper) {
        AABB bounds = helper.getBounds().inflate(1.0, 16.0, 1.0);
        helper.getLevel().getEntitiesOfClass(NegacionLight.class, bounds).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(GargantaRift.class, bounds).forEach(Entity::discard);
    }

    private static boolean inNumeroBand(float maxReiryoku) {
        return maxReiryoku >= NUMERO_MIN_REIRYOKU && maxReiryoku <= NUMERO_MAX_REIRYOKU;
    }

    // ---- What they are ----

    @GameTest(template = "empty")
    public static void arrancarNpcIsRegistered(GameTestHelper helper) {
        EntityType<ArrancarNpc> type = ModEntities.ARRANCAR.get();
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("arrancar")) == type,
                "bleach:arrancar is not the Arrancar NPC");
        helper.assertTrue(type.is(SoulLogic.SPIRITS), "an Arrancar NPC is not in bleach:spirits");
        helper.assertTrue(type.is(BleachTags.HOLLOWS), "an Arrancar NPC is not in bleach:hollows");
        helper.assertFalse(type.fireImmune(), "an Arrancar NPC cannot burn");
        helper.assertTrue(type.getCategory() == MobCategory.MONSTER, "an Arrancar NPC is a " + type.getCategory());
        helper.assertTrue(type.canSerialize(), "an Arrancar NPC is not saved with the world");

        Item egg = BuiltInRegistries.ITEM.get(BleachMod.id("arrancar_spawn_egg"));
        helper.assertTrue(egg == ModItems.ARRANCAR_SPAWN_EGG.get() && egg instanceof DeferredSpawnEggItem,
                "bleach:arrancar_spawn_egg is not the Arrancar spawn egg");
        helper.assertTrue(((DeferredSpawnEggItem) egg).getType(new ItemStack(egg)) == type,
                "the Arrancar spawn egg hatches something else");

        MinecraftServer server = helper.getLevel().getServer();
        ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE, BleachMod.id("entities/arrancar"));
        helper.assertTrue(type.getDefaultLootTable().equals(table),
                "an Arrancar NPC drops from " + type.getDefaultLootTable().location() + ", not " + table.location());
        helper.assertTrue(server.reloadableRegistries().getLootTable(table) != LootTable.EMPTY,
                "bleach:entities/arrancar is not a loot table");
        for (String name : List.of(Milestones.ARRANCAR_SLAIN, Milestones.ARRANCAR_RECALLED)) {
            helper.assertTrue(server.getAdvancements().get(BleachMod.id(name)) != null,
                    "the advancement bleach:" + name + " did not load");
        }
        helper.succeed();
    }

    // ---- Rank, soul and strength ----

    @GameTest(template = "empty")
    public static void aNumeroIsSetUpFromItsRank(GameTestHelper helper) {
        ArrancarNpc numero = npc(helper, ArrancarRank.NONE, 1, 1, 1);
        try {
            ArrancarRank rank = numero.rank();
            helper.assertTrue(numero.kind() == ArrancarRank.Kind.NUMERO && rank.numero() >= ArrancarRank.FIRST_NUMERO
                    && rank.numero() <= ArrancarRank.LAST_NUMERO, "an Arrancar set up with no rank holds " + rank);
            SoulData soul = numero.soul();
            helper.assertTrue(soul.state() == SoulState.ARRANCAR, "a Número's soul is a " + soul.state());
            int tier = soul.tier(PowerPath.HOLLOW);
            helper.assertTrue(tier >= HollowTier.GILLIAN.tier() && tier <= HollowTier.VASTO_LORDE.tier(),
                    "a Número rose from Hollow tier " + tier);
            helper.assertTrue(inNumeroBand(soul.maxReiryoku()),
                    "a Número has " + soul.maxReiryoku() + " maximum reiryoku");
            helper.assertTrue(soul.hollow().arrancar().rank().equals(ArrancarRank.NONE),
                    "a Número's soul keeps the rank " + soul.hollow().arrancar().rank() + ", which is the roster's");
            HollowPlayerTests.assertClose(helper, numero.getMaxHealth(), 60.0, "a Número's maximum health");
            HollowPlayerTests.assertClose(helper, numero.getAttributeValue(Attributes.ARMOR), 4.0, "a Número's armour");

            helper.assertTrue(ArrancarLogic.isArrancarBlade(numero.getMainHandItem()),
                    "a Número holds " + numero.getMainHandItem() + ", not an Arrancar's blade");
            helper.assertTrue(numero.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.ARRANCAR_HAKAMA.get()),
                    "a Número wears " + numero.getItemBySlot(EquipmentSlot.LEGS) + ", not the hakama");
            helper.assertTrue(numero.getItemBySlot(EquipmentSlot.FEET).is(ModItems.ARRANCAR_ZORI.get()),
                    "a Número wears " + numero.getItemBySlot(EquipmentSlot.FEET) + ", not the zōri");
            // Every slot, the body's too: a kill's one uniform piece is rolled apart, never by these chances.
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                helper.assertTrue(numero.getEquipmentDropChance(slot) == 0.0F,
                        "what a Número has in its " + slot.getName() + " slot may drop");
            }
            helper.assertTrue(numero.tattooNumber() == 0, "a Número is tattooed with " + numero.tattooNumber());
            helper.assertFalse(onRoster(helper, numero), "a Número is on the Arrancar roster");
        } finally {
            discard(numero);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPrivaronIsStrongerAndShowsItsOldSeat(GameTestHelper helper) {
        ArrancarNpc numero = npc(helper, ArrancarRank.NONE, 0, 1, 0);
        ArrancarNpc privaron = npc(helper, new ArrancarRank(0, 103), 2, 1, 2);
        ArrancarNpc diez = npc(helper, new ArrancarRank(0, 100), 0, 1, 2);
        try {
            helper.assertTrue(privaron.kind() == ArrancarRank.Kind.PRIVARON,
                    "an Arrancar set up as Privaron Espada 103 is a " + privaron.kind());
            helper.assertTrue(privaron.tattooNumber() == 3,
                    "Privaron Espada 103 is tattooed with " + privaron.tattooNumber() + ", not the 3 of its old seat");
            HollowPlayerTests.assertClose(helper, privaron.getMaxHealth(), 150.0, "a Privaron Espada's maximum health");
            HollowPlayerTests.assertClose(helper, numero.getMaxHealth(), 60.0, "a Número's maximum health");
            float reiryoku = privaron.soul().maxReiryoku();
            helper.assertTrue(reiryoku >= PRIVARON_MIN_REIRYOKU && reiryoku < ArrancarRank.espadaMinMaxReiryoku(),
                    "a Privaron Espada has " + reiryoku + " maximum reiryoku");
            double privaronBlow = privaron.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double numeroBlow = numero.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(privaronBlow > numeroBlow,
                    "a Privaron Espada's blow of " + privaronBlow + " is no heavier than a Número's " + numeroBlow);
            helper.assertTrue(privaron.isLeader(), "a Privaron Espada leads no Fracción");
            helper.assertFalse(numero.isLeader(), "a Número leads a Fracción");
            helper.assertFalse(ArrancarRank.firesGranReyCero(privaron.soul()),
                    "a Privaron Espada NPC may fire the Gran Rey Cero");
            helper.assertTrue(diez.tattooNumber() == 10,
                    "Privaron Espada 100 is tattooed with " + diez.tattooNumber() + ", not the Diez's 10");
        } finally {
            discard(numero, privaron, diez);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noUnnamedArrancarIsEverSeated(GameTestHelper helper) {
        RandomSource random = RandomSource.create(ROLL_SEED);
        for (int i = 0; i < ROLLS; i++) {
            ArrancarRank numero = ArrancarNpcLogic.rollNumero(random);
            helper.assertTrue(numero.kind() == ArrancarRank.Kind.NUMERO && numero.numero() >= ArrancarRank.FIRST_NUMERO
                    && numero.numero() <= ArrancarRank.LAST_NUMERO, "a Número roll gave " + numero);
            ArrancarRank privaron = ArrancarNpcLogic.rollPrivaron(random);
            helper.assertTrue(privaron.kind() == ArrancarRank.Kind.PRIVARON && privaron.seat() >= ArrancarRank.PRIVARON_BASE
                    && privaron.seat() < ArrancarRank.PRIVARON_BASE + ArrancarRank.SEATS, "a Privaron roll gave " + privaron);
        }
        ArrancarRank cuatro = ArrancarNpcLogic.unseated(new ArrancarRank(0, 4));
        helper.assertTrue(cuatro.seat() == 104 && cuatro.kind() == ArrancarRank.Kind.PRIVARON,
                "the fourth seat, unseated, is " + cuatro);
        ArrancarRank diez = ArrancarNpcLogic.unseated(new ArrancarRank(0, 10));
        helper.assertTrue(diez.seat() == 100 && diez.kind() == ArrancarRank.Kind.PRIVARON,
                "the tenth seat, unseated, is " + diez);

        ArrancarNpc seated = npc(helper, new ArrancarRank(0, 4), 1, 1, 1);
        try {
            helper.assertTrue(seated.rank().seat() == 104 && seated.kind() == ArrancarRank.Kind.PRIVARON,
                    "an Arrancar NPC set up in the fourth seat holds " + seated.rank());
        } finally {
            discard(seated);
        }

        HollowTier weak = ArrancarNpcLogic.rollOrigin(ArrancarRank.Kind.NUMERO, 0.1);
        helper.assertTrue(weak == HollowTier.GILLIAN, "a Número's low origin roll gave " + weak);
        HollowTier strong = ArrancarNpcLogic.rollOrigin(ArrancarRank.Kind.NUMERO, 0.9);
        helper.assertTrue(strong == HollowTier.ADJUCHAS, "a Número's high origin roll gave " + strong);
        HollowTier lord = ArrancarNpcLogic.rollOrigin(ArrancarRank.Kind.PRIVARON, 0.2);
        helper.assertTrue(lord == HollowTier.VASTO_LORDE, "a Privaron Espada's low origin roll gave " + lord);
        helper.succeed();
    }

    // ---- Prey ----

    @GameTest(template = "field")
    public static void huntsBySoulState(GameTestHelper helper) {
        Player human = mock(helper, GameType.SURVIVAL, SoulState.HUMAN, 100.0F);
        Player shinigami = mock(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 100.0F);
        Player plus = mock(helper, GameType.SURVIVAL, SoulState.PLUS, 100.0F);
        Player hollow = mock(helper, GameType.SURVIVAL, SoulState.HOLLOW, 100.0F);
        Player rukongai = mock(helper, GameType.SURVIVAL, SoulState.RUKONGAI, 100.0F);
        Player student = mock(helper, GameType.SURVIVAL, SoulState.RUKONGAI, 100.0F);
        ShinigamiLogic.enroll(SoulLogic.get(student));
        Player numero = mock(helper, GameType.SURVIVAL, SoulState.ARRANCAR, 100.0F);
        giveRank(SoulLogic.get(numero), new ArrancarRank(12, 0));
        Player espada = mock(helper, GameType.SURVIVAL, SoulState.ARRANCAR, 100.0F);
        giveRank(SoulLogic.get(espada), new ArrancarRank(12, 4));
        Player creative = mock(helper, GameType.CREATIVE, SoulState.HUMAN, 100.0F);
        helper.assertTrue(ArrancarRank.of(SoulLogic.get(espada)).kind() == ArrancarRank.Kind.ESPADA,
                "the mock Espada is not seated");

        helper.assertTrue(ArrancarNpcLogic.isPrey(human), "an aware Human is not prey");
        helper.assertTrue(ArrancarNpcLogic.isPrey(shinigami), "a Shinigami is not prey");
        helper.assertTrue(ArrancarNpcLogic.isPrey(student), "an Academy student is not prey");
        helper.assertFalse(ArrancarNpcLogic.isPrey(plus), "a Plus is prey");
        helper.assertFalse(ArrancarNpcLogic.isPrey(rukongai), "a Rukongai soul who is no student is prey");
        helper.assertFalse(ArrancarNpcLogic.isPrey(hollow), "a Hollow is prey by default");
        helper.assertFalse(ArrancarNpcLogic.isPrey(numero), "an Arrancar is prey by default");
        helper.assertFalse(ArrancarNpcLogic.isPrey(espada), "a seated Espada is prey");
        helper.assertFalse(ArrancarNpcLogic.isPrey(creative), "a Human in creative mode is prey");

        helper.assertTrue(ArrancarNpcLogic.huntsSoul(SoulLogic.get(hollow), true, false),
                "a Hollow is not hunted where the server lets Hollows hunt them");
        helper.assertTrue(ArrancarNpcLogic.huntsSoul(SoulLogic.get(numero), true, false),
                "an Arrancar below the Espada is not hunted where the server lets Hollows hunt them");
        helper.assertFalse(ArrancarNpcLogic.huntsSoul(SoulLogic.get(espada), true, false),
                "a seated Espada is hunted where the server lets Hollows hunt them");

        helper.assertFalse(ArrancarNpcLogic.mayComeFor(ShinigamiTests.soul(SoulState.HUMAN, 599.0F)),
                "Arrancar may come for a Human of 599 maximum reiryoku");
        helper.assertTrue(ArrancarNpcLogic.mayComeFor(ShinigamiTests.soul(SoulState.HUMAN, 600.0F)),
                "Arrancar may not come for a Human of 600 maximum reiryoku");
        helper.assertFalse(ArrancarNpcLogic.mayComeFor(ShinigamiTests.soul(SoulState.RUKONGAI, 800.0F)),
                "Arrancar may come for a Rukongai soul who is no student");
        SoulData enrolled = ShinigamiTests.soul(SoulState.RUKONGAI, 800.0F);
        ShinigamiLogic.enroll(enrolled);
        helper.assertTrue(ArrancarNpcLogic.mayComeFor(enrolled), "Arrancar may not come for an Academy student");
        helper.succeed();
    }

    // ---- Fighting ----

    @GameTest(template = "field")
    public static void ceroAndBalaSpareTheirOwn(GameTestHelper helper) {
        // Facing south, as spawned, along the line from the shooter through the other Número to the husk.
        ArrancarNpc shooter = npc(helper, ArrancarRank.NONE, 1, 2, 1);
        ArrancarNpc between = npc(helper, ArrancarRank.NONE, 1, 2, 4);
        Husk target = HollowPlayerTests.placed(helper, EntityType.HUSK, 1, 8);
        PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 10, 12);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 12, 4);
        Player shinigami = mock(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 100.0F);
        Player creative = mock(helper, GameType.CREATIVE, SoulState.HUMAN, 100.0F);
        // A wall behind the husk, so a Bala that missed could not fly on into another test.
        for (int x = 0; x <= 3; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(x, y, 10, Blocks.STONE);
            }
        }
        try {
            shooter.setTarget(target);
            helper.assertTrue(shooter.fireBala(target), "the Número fired no Bala");
            helper.assertTrue(between.getHealth() >= between.getMaxHealth(), "the Bala hurt the Número in its line");
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the Bala missed the husk it was fired at");

            helper.assertFalse(CeroBeam.mayStrike(shooter, between), "an Arrancar NPC's Cero may strike another");
            helper.assertFalse(CeroBeam.mayStrike(shooter, plus), "an Arrancar NPC's Cero may strike a Plus");
            helper.assertFalse(CeroBeam.mayStrike(shooter, creative),
                    "an Arrancar NPC's Cero may strike a player in creative mode");
            helper.assertTrue(CeroBeam.mayStrike(shooter, shinigami), "an Arrancar NPC's Cero spares its Shinigami prey");
            helper.assertFalse(CeroBeam.mayStrike(shooter, spider),
                    "an Arrancar NPC's Cero may strike a Hollow it neither fights nor was hurt by");
            shooter.setLeader(shinigami.getUUID());
            helper.assertFalse(CeroBeam.mayStrike(shooter, shinigami), "an Arrancar NPC's Cero may strike its leader");
        } finally {
            discard(shooter, between, target, plus, spider);
            helper.getLevel().getEntitiesOfClass(CeroBeam.class, around(helper)).forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hierroGuardsTheNpc(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Still, so the blow's dodge (a Sonído, refused without AI) cannot carry it out of the box.
        ArrancarNpc victim = QuincyTests.still(npc(helper, ArrancarRank.NONE, 0, 1, 0));
        ArrancarNpc attacker = QuincyTests.still(npc(helper, ArrancarRank.NONE, 2, 1, 2));
        try {
            AttributeInstance armour = victim.getAttribute(Attributes.ARMOR);
            helper.assertTrue(armour != null, "an Arrancar NPC has no armour attribute");
            armour.setBaseValue(0.0);
            // As strong as each other: the reiatsu gap lets all through, and all of the Hierro's share holds.
            float reiryoku = victim.soul().maxReiryoku();
            attacker.soul().setMaxReiryoku(reiryoku);
            attacker.soul().setReiryoku(reiryoku);
            float reiatsu = SoulLogic.reiatsu(victim);
            helper.assertTrue(SoulLogic.reiatsu(attacker) == reiatsu, "the two Números' reiatsu differ");

            DamageSource blow = level.damageSources().mobAttack(attacker);
            float share = Hierro.share(victim.soul(), false);
            float multiplier = Hierro.multiplier(victim, blow, reiatsu, reiatsu);
            HollowPlayerTests.assertClose(helper, multiplier, 1.0 - share,
                    "what the Hierro lets through of a Número's blow");
            helper.assertTrue(multiplier < 1.0F, "a Número's Hierro holds nothing back");
            float before = victim.getHealth();
            helper.assertTrue(victim.hurt(blow, 10.0F), "the blow never landed");
            double lost = before - victim.getHealth();
            double expected = 10.0 * (1.0 - share);
            helper.assertTrue(Math.abs(lost - expected) < HEALTH_EPSILON,
                    "a blow of 10 took " + lost + " health through the Hierro, not " + expected);
            float magic = Hierro.multiplier(victim, level.damageSources().magic(), reiatsu, reiatsu);
            helper.assertTrue(magic == 1.0F, "the Hierro held back " + (1.0F - magic) + " of magic");
        } finally {
            discard(victim, attacker);
        }
        helper.succeed();
    }

    // ---- The Fracción ----

    /**
     * A mock Hollow strikes the leader, and its Fracción takes the Hollow on; the Fracción is saved and loaded, from
     * good data and from damaged; then the leader goes, and the Fracción fights on. The mock has a thousand health, so
     * the blows it takes while the NPCs fight it cannot kill it; making it invulnerable would not do, as no creature
     * may take an invulnerable one for its target. No Cero is ready this soon after setting up, and a Bala strikes
     * nothing, as the mock is not in the world.
     */
    @GameTest(template = "field", timeoutTicks = 120)
    public static void fraccionGuardItsLeaderAndOutliveIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ArrancarNpc leader = fighter(helper, new ArrancarRank(0, 103), 2, 2, 2);
        ArrancarNpc follower = fighter(helper, ArrancarRank.NONE, 6, 2, 2);
        List<Entity> spawned = new ArrayList<>(List.of(leader, follower));
        Player hollow = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 100.0F,
                new Vec3(10.5, 2.0, 2.5));
        ArrancarNpc[] reloaded = new ArrancarNpc[2];
        step(spawned, () -> {
            follower.setLeader(leader.getUUID());
            AttributeInstance health = hollow.getAttribute(Attributes.MAX_HEALTH);
            helper.assertTrue(health != null, "the mock player has no maximum health");
            health.setBaseValue(1000.0);
            hollow.setHealth(hollow.getMaxHealth());
            helper.assertFalse(ArrancarNpcLogic.isPrey(hollow), "a Hollow is prey by default");
            helper.assertTrue(leader.hurt(level.damageSources().playerAttack(hollow), 1.0F),
                    "the Hollow's blow never reached the Privaron Espada");
        }).run();

        helper.startSequence()
                .thenExecuteAfter(25, step(spawned, () -> {
                    helper.assertTrue(follower.getTarget() == hollow,
                            "the Fracción took " + follower.getTarget() + " for its target, not what struck its leader");
                    helper.assertTrue(follower.leader() == leader, "the Fracción does not know its leader");
                    helper.assertTrue(leader.fraccionNear(16.0).contains(follower), "the leader does not see its Fracción");
                    helper.assertTrue(leader.mayFight(), "the leader, struck just now, still holds back");

                    CompoundTag saved = follower.saveWithoutId(new CompoundTag());
                    helper.assertTrue(saved.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY),
                            "the Fracción's soul was not saved");
                    ArrancarNpc copy = unloaded(helper);
                    copy.load(saved);
                    helper.assertTrue(leader.getUUID().equals(copy.leaderId()), "the loaded Fracción forgot its leader");
                    helper.assertTrue(copy.rank().equals(follower.rank()),
                            "the Fracción was loaded as " + copy.rank() + ", not " + follower.rank());
                    helper.assertTrue(copy.leaveAt() == follower.leaveAt(),
                            "the Fracción was loaded to leave at " + copy.leaveAt() + ", not " + follower.leaveAt());
                    helper.assertTrue(copy.isSetUp() && copy.soul().maxReiryoku() == follower.soul().maxReiryoku(),
                            "the Fracción was loaded with another soul");
                    copy.discard();

                    // A damaged rank and leader, and no UUID, so each copy keeps its own and may join the world.
                    CompoundTag damaged = saved.copy();
                    damaged.put("Rank", StringTag.valueOf("x"));
                    damaged.putString("Leader", "x");
                    damaged.remove("UUID");
                    CompoundTag soulless = damaged.copy();
                    soulless.remove(AttachmentHolder.ATTACHMENTS_NBT_KEY);

                    reloaded[0] = unloaded(helper);
                    reloaded[0].load(damaged);
                    spawned.add(reloaded[0]);
                    helper.assertTrue(reloaded[0].rank().equals(ArrancarRank.NONE),
                            "a damaged rank was loaded as " + reloaded[0].rank());
                    helper.assertTrue(reloaded[0].leaderId() == null,
                            "a damaged leader was loaded as " + reloaded[0].leaderId());
                    helper.assertTrue(reloaded[0].isSetUp(), "an NPC loaded with its soul is not set up");
                    reloaded[1] = unloaded(helper);
                    reloaded[1].load(soulless);
                    spawned.add(reloaded[1]);
                    helper.assertFalse(reloaded[1].isSetUp(), "an NPC loaded without its soul is set up");
                    helper.assertTrue(level.addFreshEntity(reloaded[0]) && level.addFreshEntity(reloaded[1]),
                            "the damaged NPCs could not join the world");
                }))
                .thenExecuteAfter(2, step(spawned, () -> {
                    try {
                        ArrancarNpc repaired = reloaded[0];
                        helper.assertTrue(repaired.kind() == ArrancarRank.Kind.NUMERO,
                                "an NPC whose rank was lost was given " + repaired.rank() + ", not a Número's");
                        helper.assertTrue(repaired.soul().maxReiryoku() == follower.soul().maxReiryoku(),
                                "an NPC whose rank was lost was given a new soul");
                        HollowPlayerTests.assertClose(helper, repaired.getMaxHealth(), 60.0,
                                "the maximum health of an NPC whose rank was lost");
                        helper.assertTrue(repaired.tattooNumber() == 0,
                                "an NPC whose rank was lost is tattooed with " + repaired.tattooNumber());
                        ArrancarNpc remade = reloaded[1];
                        helper.assertTrue(remade.isSetUp() && remade.kind() == ArrancarRank.Kind.NUMERO,
                                "an NPC loaded without its soul was not set up as a Número");
                        helper.assertTrue(inNumeroBand(remade.soul().maxReiryoku()),
                                "an NPC loaded without its soul was given " + remade.soul().maxReiryoku() + " reiryoku");
                    } finally {
                        discard(reloaded);
                    }
                    leader.discard();
                }))
                .thenExecuteAfter(25, step(spawned, () -> {
                    try {
                        helper.assertTrue(follower.isAlive(), "the Fracción did not outlive its leader");
                        helper.assertTrue(follower.leader() == null, "the Fracción still finds its leader, who is gone");
                        helper.assertTrue(leader.getUUID().equals(follower.leaderId()),
                                "the Fracción forgot whom it follows when its leader went");
                        helper.assertTrue(follower.getTarget() == hollow,
                                "the Fracción gave up the fight its leader left it, for " + follower.getTarget());
                    } finally {
                        discardAll(spawned);
                    }
                }))
                .thenSucceed();
    }

    // ---- The recall ----

    @GameTest(template = "field")
    public static void aBeatenPrivaronIsRecalledWithItsFraccion(GameTestHelper helper) {
        ArrancarNpc leader = npc(helper, new ArrancarRank(0, 103), 4, 2, 4);
        ArrancarNpc first = npc(helper, ArrancarRank.NONE, 6, 2, 4);
        ArrancarNpc second = npc(helper, ArrancarRank.NONE, 4, 2, 7);
        // Ten blocks from the leader, within the recall's reach, but following no one.
        ArrancarNpc stranger = npc(helper, ArrancarRank.NONE, 14, 2, 4);
        ArrancarNpc lone = npc(helper, ArrancarRank.NONE, 4, 2, 14);
        try {
            first.setLeader(leader.getUUID());
            second.setLeader(leader.getUUID());
            helper.assertTrue(ArrancarNpcEvents.recall(leader), "the Privaron Espada was not recalled");
            helper.assertTrue(Negacion.held(leader), "the recalled Privaron Espada is not held by a Negación");
            helper.assertTrue(Negacion.held(first) && Negacion.held(second), "its Fracción was not lifted away with it");
            helper.assertFalse(Negacion.held(stranger), "a Número that follows no one was lifted away with the Fracción");
            helper.assertFalse(leader.hurt(helper.getLevel().damageSources().generic(), 5.0F),
                    "the held Privaron Espada was hurt");
            helper.assertFalse(ArrancarNpcEvents.recall(lone), "a lone Número was recalled");
            helper.assertFalse(Negacion.held(lone), "a lone Número is held");

            helper.assertTrue(ArrancarNpcLogic.shouldRecall(40.0F, 150.0F, false, 0.4),
                    "a Privaron Espada beaten to 40 of 150 health missed a recall roll of 0.4");
            helper.assertFalse(ArrancarNpcLogic.shouldRecall(40.0F, 150.0F, true, 0.0),
                    "a Privaron Espada that has rolled already was recalled");
            helper.assertFalse(ArrancarNpcLogic.shouldRecall(60.0F, 150.0F, false, 0.0),
                    "a Privaron Espada with 60 of 150 health was recalled");
            helper.assertFalse(ArrancarNpcLogic.shouldRecall(40.0F, 150.0F, false, 0.6),
                    "a recall roll of 0.6 passed");
        } finally {
            // The held first, so their lights end, then the lights and any tears they opened.
            discard(leader, first, second, stranger, lone);
            discardLights(helper);
        }
        helper.succeed();
    }

    // ---- Death and the kill ----

    @GameTest(template = "empty")
    public static void aSlainArrancarCountsAsSlain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player killer = mock(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 40.0F);
        HollowTests.arm(killer, ModItems.ASAUCHI.get());
        SoulData data = SoulLogic.get(killer);
        // Still, so the struck one's dodge (a Sonído, refused without AI) cannot carry it out of the box.
        ArrancarNpc slain = QuincyTests.still(npc(helper, ArrancarRank.NONE, 0, 1, 0));
        ArrancarNpc beaten = QuincyTests.still(npc(helper, ArrancarRank.NONE, 2, 1, 2));
        int experience = BleachConfig.get(BleachConfig.ARRANCAR_NPC_NUMERO.xp());
        try {
            slain.soul().setMaxReiryoku(800.0F);
            float before = data.maxReiryoku();
            slain.setHealth(0.0F);
            slain.die(killer.damageSources().playerAttack(killer));
            helper.assertTrue(data.hunt().slain() == 1, "slaying a Número counted " + data.hunt().slain() + " slain");
            helper.assertTrue(data.hunt().purified() == 0,
                    "slaying a Número with a zanpakutō counted " + data.hunt().purified() + " purified");
            helper.assertTrue(data.maxReiryoku() > before, "slaying a Número of 800 reiryoku did not grow the slayer's");
            helper.assertTrue(killer.totalExperience == experience,
                    "slaying a Número gave " + killer.totalExperience + " experience, not " + experience);
            helper.assertFalse(onRoster(helper, slain), "the slain Número is on the Arrancar roster");

            // Struck by the player, so vanilla still counts it as the player's, but killed by the fall.
            helper.assertTrue(beaten.hurt(killer.damageSources().playerAttack(killer), 1.0F),
                    "the player's blow never landed");
            beaten.setHealth(0.0F);
            beaten.die(level.damageSources().fall());
            helper.assertTrue(data.hunt().slain() == 1, "a Número the player only struck counted as slain");
            helper.assertTrue(killer.totalExperience == experience,
                    "a Número the player only struck gave its experience");
        } finally {
            discard(slain, beaten);
            discardDrops(helper);
        }
        helper.succeed();
    }
}
