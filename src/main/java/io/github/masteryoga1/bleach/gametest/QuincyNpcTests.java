package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.QuincyElder;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.item.QuincyGarbItem;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BaitRecord;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyNpcEvents;
import io.github.masteryoga1.bleach.soul.QuincyNpcLogic;
import io.github.masteryoga1.bleach.soul.QuincyTeaching;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritEvents;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Quincy Elder and the Quincy Hunter: what they are and how they are seen, whom their
 * arrows strike, how they take a blow and when they are saved, when the Elder comes and what he
 * teaches, what each says to whom, when the Hunter follows a Garganta, and the dialogue payload and
 * enums the client relies on. The rules in {@link QuincyNpcLogic} are called directly; the NPCs stand
 * and shoot for real on the {@code field} template, a 16 by 16 stone floor with creatures at relative
 * y 2. Mock players from {@link ZanpakutoTests} are not in the world, so arrows pass them by and the
 * time is passed in wherever a method takes it. A stone wall behind each target catches the arrows
 * that fly on, and any shot that still nears the edge of the box is taken away, so no test's arrows
 * reach another's.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuincyNpcTests {
    private static final long NOW = 100_000L;
    private static final long DAY = 24_000L;
    /** Room for the float steps of a health bar. */
    private static final double HEALTH_EPSILON = 1.0E-3;
    /** How far past the box's edge a shot is still looked for, to be taken away. */
    private static final double SHOT_SEARCH = 8.0;

    private QuincyNpcTests() {
    }

    // ---- Shared helpers ----

    /** The same soul with the Elder's record replaced. */
    static SoulData teaching(SoulData data, QuincyTeaching teaching) {
        data.setQuincy(data.quincy().withTeaching(teaching));
        return data;
    }

    /** The same soul having met the Elder. */
    static SoulData met(SoulData data) {
        return teaching(data, data.quincy().teaching().withElderMet(true));
    }

    /** The same soul having learned Ransōtengai. */
    static SoulData known(SoulData data) {
        return teaching(data, data.quincy().teaching().withRansotengaiKnown(true));
    }

    /** A creature made for the test's level but never added to it, so there is nothing to clear away. */
    static <T extends Entity> T created(GameTestHelper helper, EntityType<T> type) {
        return Objects.requireNonNull(type.create(helper.getLevel()), "could not make " + type);
    }

    /** A row of stone across the box at this z, from the floor up three blocks, for stray arrows to burst on. */
    static void wall(GameTestHelper helper, int z) {
        for (int x = 0; x < 16; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
    }

    /** Every tick, takes away this owner's arrows once they come within a block of the box's edge. */
    static void keepShotsInside(GameTestHelper helper, Entity owner) {
        AABB inside = helper.getBounds().deflate(1.0);
        helper.onEachTick(() -> {
            for (HeiligPfeil arrow : shotsOf(helper, owner)) {
                if (!inside.contains(arrow.position())) {
                    arrow.discard();
                }
            }
        });
    }

    /** This owner's arrows in and around the box. */
    static List<HeiligPfeil> shotsOf(GameTestHelper helper, Entity owner) {
        return helper.getLevel().getEntitiesOfClass(HeiligPfeil.class, helper.getBounds().inflate(SHOT_SEARCH),
                arrow -> arrow.getOwner() == owner);
    }

    /** Turns a creature to face north, the way {@code Rotation:[180f,0f]} would. */
    private static void faceNorth(Mob mob) {
        mob.setYRot(180.0F);
        mob.setYHeadRot(180.0F);
        mob.setYBodyRot(180.0F);
    }

    /** The chance this mob drops what it holds or wears in this slot, as it saves it. */
    private static float dropChance(Mob mob, EquipmentSlot slot) {
        CompoundTag saved = mob.saveWithoutId(new CompoundTag());
        ListTag chances = saved.getList(slot.getType() == EquipmentSlot.Type.HAND ? "HandDropChances" : "ArmorDropChances",
                Tag.TAG_FLOAT);
        return chances.getFloat(slot.getIndex());
    }

    private static void assertView(GameTestHelper helper, DialogueView actual, DialogueView expected, String who) {
        helper.assertTrue(actual == expected, who + " was shown " + actual + ", not " + expected);
    }

    // ---- Registration and setup ----

    @GameTest(template = "empty")
    public static void quincyNpcsAreRegistered(GameTestHelper helper) {
        for (String name : List.of("quincy_elder", "quincy_hunter")) {
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(BleachMod.id(name)), "bleach:" + name + " is not an entity type");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(BleachMod.id(name + "_spawn_egg")),
                    "bleach:" + name + "_spawn_egg is not an item");
        }
        for (EntityType<?> type : List.<EntityType<?>>of(ModEntities.QUINCY_ELDER.get(), ModEntities.QUINCY_HUNTER.get())) {
            helper.assertFalse(type.is(SoulLogic.SPIRITS), type + " is a spirit, so the unaware could not see a living Quincy");
            helper.assertTrue(type.is(SoulLogic.SPIRIT_SIGHTED), type + " is not in #bleach:spirit_sighted, so it is blind to Hollows");
        }
        double elder = created(helper, ModEntities.QUINCY_ELDER.get()).getAttributeValue(Attributes.MAX_HEALTH);
        double hunter = created(helper, ModEntities.QUINCY_HUNTER.get()).getAttributeValue(Attributes.MAX_HEALTH);
        helper.assertTrue(elder == 40.0, "the Elder has " + elder + " health, not 40");
        helper.assertTrue(hunter == QuincyHunter.MAX_HEALTH, "the Hunter has " + hunter + " health, not " + QuincyHunter.MAX_HEALTH);
        helper.succeed();
    }

    /**
     * Each NPC's soul is given on its first tick, NoAI or not. Only the slots they are given something
     * in are set never to drop; the head and offhand keep vanilla's chance, but hold nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void npcSoulsAreQuincy(GameTestHelper helper) {
        QuincyElder elder = QuincyTests.still(helper.spawn(ModEntities.QUINCY_ELDER.get(), 1, 1, 1));
        QuincyHunter hunter = QuincyTests.still(helper.spawn(ModEntities.QUINCY_HUNTER.get(), 1, 1, 2));
        helper.runAfterDelay(2, () -> {
            Map<QuincyNpc, int[]> expected = new LinkedHashMap<>();
            expected.put(elder, new int[] {3, 400});
            expected.put(hunter, new int[] {2, 150});
            expected.forEach((npc, tierAndReiryoku) -> {
                String who = npc.getType().toString();
                helper.assertTrue(SoulLogic.hasSoul(npc), who + " has no soul after two ticks");
                SoulData soul = SoulLogic.get(npc);
                helper.assertTrue(soul.state() == SoulState.HUMAN, who + " is " + soul.state() + ", not a living Human");
                helper.assertTrue(QuincyLogic.bloodline(soul) == Bloodline.ECHT, who + " is " + QuincyLogic.bloodline(soul));
                helper.assertTrue(QuincyLogic.tier(soul) == tierAndReiryoku[0],
                        who + " is of tier " + QuincyLogic.tier(soul) + ", not " + tierAndReiryoku[0]);
                helper.assertTrue(soul.maxReiryoku() == tierAndReiryoku[1],
                        who + " has " + soul.maxReiryoku() + " reiryoku, not " + tierAndReiryoku[1]);
                helper.assertTrue(npc.getMainHandItem().is(ModItems.QUINCY_CROSS.get()), who + " holds " + npc.getMainHandItem());
                helper.assertTrue(QuincyGarbItem.isFullSet(npc), who + " does not wear the whole Quincy garb");
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR || npc.getItemBySlot(slot).isEmpty()) {
                        continue;
                    }
                    helper.assertTrue(dropChance(npc, slot) == 0.0F,
                            who + " may drop its " + slot.getName() + " (" + dropChance(npc, slot) + ")");
                }
            });
            elder.discard();
            hunter.discard();
            helper.succeed();
        });
    }

    // ---- Spirit sight and arrows ----

    @GameTest(template = "empty")
    public static void npcsSeeSpiritsAndAreSeen(GameTestHelper helper) {
        QuincyHunter hunter = created(helper, ModEntities.QUINCY_HUNTER.get());
        SpiderHollow spider = created(helper, ModEntities.SPIDER_HOLLOW.get());
        Zombie zombie = created(helper, EntityType.ZOMBIE);
        helper.assertFalse(SpiritEvents.blindToSpirit(hunter, spider), "the Hunter is blind to a Hollow");
        helper.assertTrue(SpiritEvents.blindToSpirit(zombie, spider), "a zombie sees a Hollow");
        helper.assertTrue(SpiritSenses.bodiesTouch(spider, hunter), "a Hollow walks through the Hunter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void npcArrowsStrikeOnlyHollows(GameTestHelper helper) {
        QuincyHunter hunter = created(helper, ModEntities.QUINCY_HUNTER.get());
        QuincyElder elder = created(helper, ModEntities.QUINCY_ELDER.get());
        SpiderHollow spider = created(helper, ModEntities.SPIDER_HOLLOW.get());
        Zombie zombie = created(helper, EntityType.ZOMBIE);
        FishboneD fishbone = created(helper, ModEntities.FISHBONE_D.get());
        PlusSpirit plus = created(helper, ModEntities.PLUS.get());
        Player mock = ZanpakutoTests.mock(helper, QuincyTests.quincy(Bloodline.ECHT, 1, 100.0F));

        Map<String, Boolean> strikes = new LinkedHashMap<>();
        strikes.put("the Hunter's arrow a Spider Hollow", SpiritProjectiles.mayStrike(hunter, spider));
        strikes.put("a player's arrow a Spider Hollow", SpiritProjectiles.mayStrike(mock, spider));
        strikes.forEach((what, may) -> helper.assertTrue(may, what + " cannot strike"));

        Map<String, Boolean> passes = new LinkedHashMap<>();
        passes.put("the Hunter's arrow a player", SpiritProjectiles.mayStrike(hunter, mock));
        passes.put("the Hunter's arrow a zombie", SpiritProjectiles.mayStrike(hunter, zombie));
        passes.put("the Hunter's arrow Fishbone D", SpiritProjectiles.mayStrike(hunter, fishbone));
        passes.put("the Elder's arrow a Plus", SpiritProjectiles.mayStrike(elder, plus));
        passes.put("a player's arrow the Hunter", SpiritProjectiles.mayStrike(mock, hunter));
        passes.put("a player's arrow the Elder", SpiritProjectiles.mayStrike(mock, elder));
        passes.forEach((what, may) -> helper.assertFalse(may, what + " can strike"));
        helper.succeed();
    }

    /**
     * The Hunter's AI finds a Hollow and shoots it. Its own batch, so no Hollow of another test is
     * near enough for him to pick instead.
     */
    @GameTest(template = "field", batch = "quincy_npc", timeoutTicks = 200)
    public static void hunterShootsAHollow(GameTestHelper helper) {
        wall(helper, 14);
        QuincyHunter hunter = helper.spawn(ModEntities.QUINCY_HUNTER.get(), 2, 2, 2);
        SpiderHollow spider = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), 2, 2, 12));
        Player bystander = QuincyTests.archer(helper, QuincyTests.quincy(Bloodline.ECHT, 1, 100.0F), 4.5, 7.0);
        float health = bystander.getHealth();
        keepShotsInside(helper, hunter);
        helper.succeedWhen(() -> {
            helper.assertTrue(!spider.isAlive() || spider.getHealth() < spider.getMaxHealth(),
                    "the Hunter never struck the Spider Hollow");
            helper.assertTrue(bystander.getHealth() == health, "a player beside the line was hurt");
            QuincyArtsTests.discardAll(shotsOf(helper, hunter));
            hunter.discard();
            spider.discard();
        });
    }

    @GameTest(template = "field", timeoutTicks = 120)
    public static void elderMissDoesNoHarm(GameTestHelper helper) {
        wall(helper, 1);
        QuincyElder elder = QuincyTests.still(helper.spawn(ModEntities.QUINCY_ELDER.get(), 8, 2, 9));
        faceNorth(elder);
        SoulData data = QuincyArtsTests.thirdTier();
        Player mock = QuincyTests.archer(helper, data, 8.5, 4.5);
        QuincyArtsTests.face(mock, elder.getEyePosition());
        float health = mock.getHealth();
        helper.assertFalse(elder.beginRestore(mock, helper.getLevel().getGameTime()),
                "the Elder set about restoring a power that is not sealed");
        keepShotsInside(helper, elder);
        elder.loose(mock);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(mock.getHealth() == health, "the Elder's shot left the mock at " + mock.getHealth());
            helper.assertFalse(QuincyArtsLogic.isRestored(data), "the Elder's shot restored a power that was never sealed");
            QuincyArtsTests.discardAll(shotsOf(helper, elder));
            elder.discard();
            helper.succeed();
        });
    }

    // ---- Damage, saving and visits ----

    @GameTest(template = "field", timeoutTicks = 40)
    public static void hunterHurtByAPlayer(GameTestHelper helper) {
        QuincyHunter visiting = QuincyTests.still(helper.spawn(ModEntities.QUINCY_HUNTER.get(), 4, 2, 8));
        QuincyHunter staying = QuincyTests.still(helper.spawn(ModEntities.QUINCY_HUNTER.get(), 12, 2, 8));
        visiting.setLifetime(QuincyNpcLogic.VISIT_LIFETIME);
        Player mock = QuincyTests.archer(helper, QuincyArtsTests.thirdTier(), 8.5, 8.5);
        helper.runAfterDelay(1, () -> {
            visiting.hurt(helper.getLevel().damageSources().playerAttack(mock), 2.0F);
            staying.setTarget(mock);
            staying.hurt(helper.getLevel().damageSources().playerAttack(mock), 2.0F);
            helper.assertTrue(staying.getTarget() != mock, "the Hunter who stays turned on the player who struck him");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(visiting.isRemoved(), "a visiting Hunter struck by a player did not leave");
                helper.assertFalse(staying.isRemoved(), "a Hunter who stays left when a player struck him");
                helper.assertTrue(staying.getTarget() != mock, "the Hunter who stays turned on the player who struck him");
                staying.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hunterVeneSoftensBelowHalf(GameTestHelper helper) {
        QuincyHunter hunter = QuincyTests.still(helper.spawn(ModEntities.QUINCY_HUNTER.get(), 1, 1, 1));
        helper.runAfterDelay(2, () -> {
            float before = hunter.getHealth();
            hunter.hurt(helper.getLevel().damageSources().generic(), 8.0F);
            helper.assertTrue(Math.abs(before - hunter.getHealth() - 8.0F) < HEALTH_EPSILON,
                    "at full health the Hunter took " + (before - hunter.getHealth()) + " of 8");
            helper.assertFalse(hunter.veneShown(), "Blut Vene came on above half health");

            hunter.setHealth(25.0F);
            hunter.invulnerableTime = 0;
            hunter.hurt(helper.getLevel().damageSources().generic(), 8.0F);
            helper.assertTrue(Math.abs(25.0F - hunter.getHealth() - 8.0F * QuincyHunter.VENE_FACTOR) < HEALTH_EPSILON,
                    "below half health the Hunter took " + (25.0F - hunter.getHealth()) + " of 8, not 6");
            helper.assertTrue(hunter.veneShown(), "Blut Vene did not come on below half health");

            before = hunter.getHealth();
            hunter.invulnerableTime = 0;
            hunter.hurt(helper.getLevel().damageSources().genericKill(), 4.0F);
            helper.assertTrue(Math.abs(before - hunter.getHealth() - 4.0F) < HEALTH_EPSILON,
                    "Blut Vene softened a blow that passes through invulnerability: " + (before - hunter.getHealth()) + " of 4");
            hunter.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void elderCannotBeHurtOrHunted(GameTestHelper helper) {
        QuincyElder elder = created(helper, ModEntities.QUINCY_ELDER.get());
        QuincyHunter hunter = created(helper, ModEntities.QUINCY_HUNTER.get());
        SpiderHollow spider = created(helper, ModEntities.SPIDER_HOLLOW.get());
        helper.assertTrue(elder.isInvulnerable(), "the Elder is not invulnerable");
        helper.assertFalse(elder.hurt(helper.getLevel().damageSources().generic(), 5.0F), "the Elder was hurt");
        helper.assertFalse(spider.canAttack(elder), "a Hollow may attack the Elder");
        helper.assertFalse(HollowLogic.isPrey(elder), "the Elder is a Hollow's prey");
        helper.assertTrue(HollowLogic.isPrey(hunter) == BleachConfig.get(BleachConfig.HOLLOWS_HUNT_HUNTER),
                "whether the Hunter is a Hollow's prey does not follow the config");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void npcsAreSavedOnlyWhenPlaced(GameTestHelper helper) {
        QuincyHunter hunter = created(helper, ModEntities.QUINCY_HUNTER.get());
        hunter.setLifetime(QuincyNpcLogic.VISIT_LIFETIME);
        helper.assertFalse(hunter.shouldBeSaved(), "a visiting Hunter would be saved");
        hunter.setLifetime(QuincyNpc.PERMANENT);
        helper.assertTrue(hunter.shouldBeSaved(), "a Hunter who stays would not be saved");

        hunter.setLifetime(4321);
        CompoundTag tag = hunter.saveWithoutId(new CompoundTag());
        QuincyHunter loaded = created(helper, ModEntities.QUINCY_HUNTER.get());
        loaded.load(tag);
        helper.assertTrue(loaded.lifetime() == 4321, "a lifetime of 4321 loaded as " + loaded.lifetime());

        tag.remove("Lifetime");
        QuincyHunter placed = created(helper, ModEntities.QUINCY_HUNTER.get());
        placed.load(tag);
        helper.assertTrue(placed.lifetime() == QuincyNpc.PERMANENT && placed.isPermanent(),
                "data with no lifetime loaded with " + placed.lifetime());

        CompoundTag elderTag = created(helper, ModEntities.QUINCY_ELDER.get()).saveWithoutId(new CompoundTag());
        elderTag.remove("Invulnerable");
        QuincyElder summoned = created(helper, ModEntities.QUINCY_ELDER.get());
        summoned.load(elderTag);
        helper.assertTrue(summoned.isInvulnerable(), "an Elder loaded from data with no Invulnerable can be hurt");
        elderTag.putBoolean("Invulnerable", false);
        QuincyElder mortal = created(helper, ModEntities.QUINCY_ELDER.get());
        mortal.load(elderTag);
        helper.assertFalse(mortal.isInvulnerable(), "an Elder saved as mortal loaded invulnerable");
        helper.succeed();
    }

    /** A soul with no Quincy blood, or a Hollow, is turned away by {@code elderMayVisit} (see {@link #elderViewsByVisitor}). */
    @GameTest(template = "empty")
    public static void visitReasonsFollowThePriority(GameTestHelper helper) {
        long day = 4L;
        Map<String, SoulData> souls = new LinkedHashMap<>();
        Map<String, QuincyNpcLogic.Visit> expected = new LinkedHashMap<>();
        souls.put("sealed a day ago", QuincyArtsTests.sealed(QuincyArtsTests.thirdTier(), NOW - DAY));
        expected.put("sealed a day ago", QuincyNpcLogic.Visit.RESTORE);
        souls.put("sealed a moment ago", QuincyArtsTests.sealed(QuincyArtsTests.thirdTier(), NOW - 100L));
        expected.put("sealed a moment ago", QuincyNpcLogic.Visit.NONE);
        souls.put("restored, Ransōtengai unknown", QuincyArtsTests.restored(QuincyArtsTests.thirdTier()));
        expected.put("restored, Ransōtengai unknown", QuincyNpcLogic.Visit.TEACH);
        souls.put("third tier, Ransōtengai known", known(QuincyArtsTests.thirdTier()));
        expected.put("third tier, Ransōtengai known", QuincyNpcLogic.Visit.LESSON);
        souls.put("latent", QuincyTests.quincy(Bloodline.ECHT, 0, 100.0F));
        expected.put("latent", QuincyNpcLogic.Visit.LESSON);
        SoulData taughtAll = QuincyTests.quincy(Bloodline.ECHT, 0, 100.0F);
        souls.put("taught every lesson", teaching(taughtAll, taughtAll.quincy().teaching().withLessons(12)));
        expected.put("taught every lesson", QuincyNpcLogic.Visit.NONE);
        SoulData taughtToday = QuincyTests.quincy(Bloodline.ECHT, 0, 100.0F);
        souls.put("taught today", teaching(taughtToday, taughtToday.quincy().teaching().withLesson(day)));
        expected.put("taught today", QuincyNpcLogic.Visit.NONE);
        souls.forEach((who, soul) -> {
            QuincyNpcLogic.Visit visit = QuincyNpcLogic.visitReason(soul, NOW, day, 12, DAY);
            helper.assertTrue(visit == expected.get(who), "a soul " + who + " was visited for " + visit + ", not "
                    + expected.get(who));
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void visitTimingGates(GameTestHelper helper) {
        long cooldown = 12_000L;
        helper.assertFalse(QuincyNpcLogic.visitDue(QuincyTeaching.NONE, 11_999L, cooldown),
                "the Elder came before a new world's first wait was over");
        helper.assertTrue(QuincyNpcLogic.visitDue(QuincyTeaching.NONE, 12_000L, cooldown),
                "the Elder did not come once a new world's first wait was over");
        QuincyTeaching visited = QuincyTeaching.NONE.withLastVisitAt(1_000L);
        helper.assertFalse(QuincyNpcLogic.visitDue(visited, 12_999L, cooldown), "the Elder came back a tick too soon");
        helper.assertTrue(QuincyNpcLogic.visitDue(visited, 13_000L, cooldown), "the Elder did not come back once the wait was over");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lessonsTrainOncePerDay(GameTestHelper helper) {
        SoulData data = QuincyTests.quincy(Bloodline.ECHT, 0, 100.0F);
        Player mock = ZanpakutoTests.mock(helper, data);
        int points = BleachConfig.get(BleachConfig.ELDER_LESSON_TRAINING);
        helper.assertTrue(QuincyNpcEvents.giveLesson(mock, data, 100L, 100L), "the first lesson was not given");
        helper.assertTrue(data.quincy().training() == points, "the lesson trained " + data.quincy().training() + ", not " + points);
        helper.assertTrue(data.quincy().teaching().lessons() == 1, "the lesson count is " + data.quincy().teaching().lessons());
        helper.assertFalse(QuincyNpcEvents.giveLesson(mock, data, 200L, 200L), "a second lesson was given the same day");
        helper.assertTrue(data.quincy().training() == points, "a refused lesson trained the soul");
        helper.assertTrue(QuincyNpcEvents.giveLesson(mock, data, DAY + 100L, DAY + 100L), "no lesson was given the next day");
        helper.assertTrue(data.quincy().training() == 2 * points, "the next day's lesson trained " + data.quincy().training());

        teaching(data, data.quincy().teaching().withLessons(BleachConfig.get(BleachConfig.ELDER_MAX_LESSONS)));
        helper.assertFalse(QuincyNpcEvents.giveLesson(mock, data, 2 * DAY + 100L, 2 * DAY + 100L),
                "a lesson was given past the last of them");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void elderRestoresBesideTheHeart(GameTestHelper helper) {
        wall(helper, 1);
        long now = helper.getLevel().getGameTime();
        SoulData data = QuincyArtsTests.sealed(QuincyArtsTests.thirdTier(), now - DAY);
        Player mock = QuincyTests.archer(helper, data, 8.5, 4.5);
        QuincyArtsTests.exhaust(mock);
        QuincyElder elder = QuincyTests.still(helper.spawn(ModEntities.QUINCY_ELDER.get(), 8, 2, 9));
        faceNorth(elder);
        QuincyArtsTests.face(mock, elder.getEyePosition());
        elder.setLifetime(QuincyNpcLogic.VISIT_LIFETIME);
        helper.assertTrue(elder.beginRestore(mock, now), "the Elder would not set about restoring a sealed power");
        keepShotsInside(helper, elder);
        elder.loose(mock);
        helper.succeedWhen(() -> {
            helper.assertTrue(QuincyArtsLogic.isRestored(data), "the Elder's heart shot left the power "
                    + QuincyArtsLogic.stage(data));
            helper.assertTrue(elder.lifetime() <= QuincyNpcLogic.DONE_LIFETIME,
                    "the Elder means to stay " + elder.lifetime() + " ticks after the restoration");
            QuincyArtsTests.discardAll(shotsOf(helper, elder));
            elder.discard();
        });
    }

    @GameTest(template = "empty")
    public static void elderViewsByVisitor(GameTestHelper helper) {
        long day = 4L;
        int max = 12;
        assertView(helper, QuincyNpcLogic.elderView(QuincyTests.quincy(Bloodline.NONE, 0, 100.0F), NOW, day, max),
                DialogueView.ELDER_STRANGER, "a soul with no Quincy blood");
        assertView(helper, QuincyNpcLogic.elderView(QuincyTests.in(SoulState.HOLLOW, QuincyTests.quincy(Bloodline.ECHT, 1,
                100.0F)), NOW, day, max), DialogueView.ELDER_HOLLOW, "a Hollow");
        assertView(helper, QuincyNpcLogic.elderView(QuincyArtsTests.sealed(met(QuincyArtsTests.thirdTier()), 0L), NOW, day, max),
                DialogueView.ELDER_RESTORE, "a sealed Quincy");
        assertView(helper, QuincyNpcLogic.elderView(QuincyArtsTests.thirdTier(), NOW, day, max),
                DialogueView.ELDER_GREETING, "a Quincy meeting him for the first time");
        assertView(helper, QuincyNpcLogic.elderView(met(QuincyArtsTests.thirdTier()), NOW, day, max),
                DialogueView.ELDER_TEACH, "a third-tier Quincy who does not know Ransōtengai");
        SoulData inTrial = met(QuincyArtsTests.thirdTier());
        teaching(inTrial, inTrial.quincy().teaching().withTrialUntil(NOW + 100L));
        assertView(helper, QuincyNpcLogic.elderView(inTrial, NOW, day, max), DialogueView.ELDER_TRIAL, "a Quincy in the trial");
        assertView(helper, QuincyNpcLogic.elderView(known(met(QuincyArtsTests.thirdTier())), NOW, day, max),
                DialogueView.ELDER_LESSON, "a Quincy with a lesson to come today");
        SoulData taught = known(met(QuincyArtsTests.thirdTier()));
        teaching(taught, taught.quincy().teaching().withLesson(day));
        assertView(helper, QuincyNpcLogic.elderView(taught, NOW, day, max), DialogueView.ELDER_IDLE,
                "a Quincy taught today");

        QuincyElder elder = created(helper, ModEntities.QUINCY_ELDER.get());
        SoulData sealed = QuincyArtsTests.sealed(met(QuincyArtsTests.thirdTier()), NOW - DAY);
        Player visitor = ZanpakutoTests.mock(helper, sealed);
        List<NpcAction> healthy = elder.unreadyFor(visitor, sealed, DialogueView.ELDER_RESTORE, NOW);
        helper.assertTrue(healthy.contains(NpcAction.RESTORE), "a sealed Quincy at full strength may be restored: " + healthy);
        QuincyArtsTests.exhaust(visitor);
        List<NpcAction> worn = elder.unreadyFor(visitor, sealed, DialogueView.ELDER_RESTORE, NOW);
        helper.assertFalse(worn.contains(NpcAction.RESTORE), "a worn-down sealed Quincy may not be restored: " + worn);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hunterViewsByVisitor(GameTestHelper helper) {
        SoulData awakened = QuincyTests.quincy(Bloodline.ECHT, 1, 100.0F);
        assertView(helper, QuincyNpcLogic.hunterView(ShinigamiTests.soul(SoulState.HOLLOW, 100.0F), false, false),
                DialogueView.HUNTER_HOLLOW, "a Hollow");
        assertView(helper, QuincyNpcLogic.hunterView(awakened, true, true), DialogueView.HUNTER_CONTEST,
                "the soul in his contest");
        assertView(helper, QuincyNpcLogic.hunterView(awakened, false, true), DialogueView.HUNTER_RESTING,
                "a soul after his contest was offered");
        assertView(helper, QuincyNpcLogic.hunterView(awakened, false, false), DialogueView.HUNTER_RIVALRY,
                "an awakened Quincy");
        assertView(helper, QuincyNpcLogic.hunterView(QuincyTests.withShinigamiPower(QuincyTests.quincy(Bloodline.ECHT, 1,
                100.0F)), false, false), DialogueView.HUNTER_RIVALRY, "a hybrid");
        assertView(helper, QuincyNpcLogic.hunterView(ShinigamiTests.soul(SoulState.SHINIGAMI, 100.0F), false, false),
                DialogueView.HUNTER_CHALLENGE, "a Shinigami");
        assertView(helper, QuincyNpcLogic.hunterView(ShinigamiTests.soul(SoulState.HUMAN, 0.0F), false, false),
                DialogueView.HUNTER_PLAIN, "an unaware Human");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hunterArrivalRules(GameTestHelper helper) {
        double chance = 0.10;
        int guarantee = 10;
        long cooldown = DAY;
        SoulData data = QuincyTests.quincy(Bloodline.ECHT, 1, 100.0F);
        data.setHunt(data.hunt().withSlain(guarantee));
        helper.assertTrue(QuincyNpcLogic.hunterArrives(data, NOW, 0.99, chance, guarantee, cooldown),
                "the Hunter did not surely come the first time after ten Hollows");
        data.setHunt(data.hunt().withBait(BaitRecord.NONE.withHunterMet(true)));
        helper.assertFalse(QuincyNpcLogic.hunterArrives(data, NOW, 0.99, chance, guarantee, cooldown),
                "the Hunter surely came again once met");
        helper.assertTrue(QuincyNpcLogic.hunterArrives(data, NOW, 0.05, chance, guarantee, cooldown),
                "the Hunter did not come on a roll under the chance");
        data.setHunt(data.hunt().withBait(data.hunt().bait().withLastHunterAt(NOW - 100L)));
        helper.assertFalse(QuincyNpcLogic.hunterArrives(data, NOW, 0.05, chance, guarantee, cooldown),
                "the Hunter came back a hundred ticks after his last visit");
        helper.assertFalse(QuincyNpcLogic.hunterMayCome(HollowKind.FISHBONE_D, false), "the Hunter may follow the first Hollow");
        helper.assertFalse(QuincyNpcLogic.hunterMayCome(HollowKind.SPIDER, true), "the Hunter may follow a summoned Garganta");
        helper.succeed();
    }

    // ---- Payload and enums ----

    @GameTest(template = "empty")
    public static void dialoguePayloadRoundTrips(GameTestHelper helper) {
        OpenDialoguePayload sent = new OpenDialoguePayload(5, DialogueView.ELDER_RESTORE, List.of(1, 2),
                List.of(NpcAction.RESTORE), List.of(Component.literal("x")));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            OpenDialoguePayload.STREAM_CODEC.encode(buffer, sent);
            OpenDialoguePayload received = OpenDialoguePayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(received.equals(sent), "the dialogue payload came back as " + received);
            helper.assertTrue(buffer.readableBytes() == 0, "the dialogue payload left " + buffer.readableBytes() + " bytes unread");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    /** Both enums go over the network by their ordinal, so new values only ever come last. */
    @GameTest(template = "empty")
    public static void dialogueEnumsAppendOnly(GameTestHelper helper) {
        Map<Enum<?>, Integer> ordinals = new LinkedHashMap<>();
        ordinals.put(DialogueView.QUINCY, 9);
        ordinals.put(DialogueView.ELDER_GREETING, 10);
        ordinals.put(DialogueView.HUNTER_HOLLOW, 23);
        ordinals.put(NpcAction.ACCEPT_POWER, 4);
        ordinals.put(NpcAction.CONTEST, 8);
        ordinals.forEach((value, ordinal) -> helper.assertTrue(value.ordinal() == ordinal,
                value + " is number " + value.ordinal() + ", not " + ordinal));
        List<NpcAction> teach = NpcAction.offeredIn(DialogueView.ELDER_TEACH);
        helper.assertTrue(teach.equals(List.of(NpcAction.RANSOTENGAI)), "the Elder's teaching offers " + teach);
        helper.succeed();
    }
}
