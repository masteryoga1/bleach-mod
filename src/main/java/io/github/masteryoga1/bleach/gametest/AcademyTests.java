package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests for the Shinigami NPCs, their dialogue, and what they do for the souls who talk to them. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class AcademyTests {
    private static final float EPSILON = 1.0E-3F;
    private static final int TICKS_PER_MINUTE = 1200;

    private AcademyTests() {
    }

    /** A mock player standing next to the NPCs these tests spawn, with this soul. */
    private static Player visitor(GameTestHelper helper, SoulData soul) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
        player.setPos(at.x, at.y, at.z);
        player.setData(SoulAttachments.SOUL, soul);
        return player;
    }

    private static SoulData awareHuman() {
        return ShinigamiTests.soul(SoulState.HUMAN, (float) (double) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD));
    }

    @GameTest(template = "empty")
    public static void instructorIsASpiritNpc(GameTestHelper helper) {
        AcademyInstructor npc = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(SoulLogic.isSpirit(npc), "the instructor is not a spirit");
            helper.assertTrue(SoulLogic.hasSoul(npc) && SoulLogic.get(npc).state() == SoulState.SHINIGAMI,
                    "the instructor has no Shinigami soul");
            helper.assertTrue(SoulLogic.get(npc).tier(PowerPath.SHINIGAMI) == 1, "the instructor has no Shinigami tier");
            helper.assertTrue(npc.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof ReiatsuFleeGoal),
                    "the instructor can be frightened away");
            helper.assertTrue(npc.isInvulnerable() && npc.requiresCustomPersistence() && !npc.isPushable(),
                    "the instructor can be hurt, despawned or pushed");
            helper.assertTrue(ShihakushoItem.isFullSet(npc), "the instructor is not in the Shihakushō");
        });
    }

    @GameTest(template = "empty")
    public static void dialogueViewFollowsTheSoul(GameTestHelper helper) {
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        WoundedShinigami wounded = helper.spawn(ModEntities.WOUNDED_SHINIGAMI.get(), 1, 1, 2);
        long now = helper.getLevel().getGameTime();
        Map<SoulData, DialogueView[]> expected = Map.of(
                awareHuman(), new DialogueView[] {DialogueView.LIVING, DialogueView.OFFER_POWER},
                ShinigamiTests.soul(SoulState.HUMAN, 1.0F), new DialogueView[] {DialogueView.LIVING, DialogueView.RESTING},
                ShinigamiTests.substitute(60.0F), new DialogueView[] {DialogueView.SUBSTITUTE, DialogueView.RESTING},
                ShinigamiTests.soul(SoulState.PLUS, 40.0F), new DialogueView[] {DialogueView.PLUS, DialogueView.PLUS},
                ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F), new DialogueView[] {DialogueView.SOUL, DialogueView.RESTING},
                ShinigamiTests.student(40.0F), new DialogueView[] {DialogueView.STUDENT, DialogueView.RESTING},
                ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F), new DialogueView[] {DialogueView.SHINIGAMI, DialogueView.RESTING},
                ShinigamiTests.soul(SoulState.HOLLOW, 40.0F), new DialogueView[] {DialogueView.HOLLOW, DialogueView.HOLLOW},
                ShinigamiTests.soul(SoulState.ARRANCAR, 40.0F), new DialogueView[] {DialogueView.HOLLOW, DialogueView.HOLLOW});
        expected.forEach((soul, views) -> {
            String who = soul.state() + (ShinigamiLogic.isSubstitute(soul) ? " (Substitute)" : "")
                    + (ShinigamiLogic.isStudent(soul) ? " (student)" : "") + " with " + soul.maxReiryoku();
            helper.assertTrue(instructor.viewFor(soul, now) == views[0],
                    "the instructor shows " + instructor.viewFor(soul, now) + " to a " + who);
            helper.assertTrue(wounded.viewFor(soul, now) == views[1],
                    "the wounded Shinigami shows " + wounded.viewFor(soul, now) + " to a " + who);
        });
        wounded.markGavePower();
        helper.assertTrue(wounded.viewFor(awareHuman(), now) == DialogueView.RESTING,
                "the wounded Shinigami offers power she already gave away");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void npcActionsAreGated(GameTestHelper helper) {
        for (DialogueView view : DialogueView.values()) {
            List<NpcAction> offered = NpcAction.offeredIn(view);
            List<NpcAction> expected = switch (view) {
                case PLUS -> List.of(NpcAction.KONSO);
                case SOUL -> List.of(NpcAction.ENROLL);
                case STUDENT -> List.of(NpcAction.GRADUATE, NpcAction.ASAUCHI);
                case SUBSTITUTE, SHINIGAMI -> List.of(NpcAction.ASAUCHI);
                case OFFER_POWER -> List.of(NpcAction.ACCEPT_POWER);
                case LIVING, HOLLOW, RESTING -> List.of();
            };
            helper.assertTrue(offered.equals(expected), view + " offers " + offered);
        }

        // A soul that has not passed on cannot skip straight to enrolment by sending the action anyway.
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        Player plus = visitor(helper, ShinigamiTests.soul(SoulState.PLUS, 40.0F));
        instructor.perform(plus, NpcAction.ENROLL);
        instructor.perform(plus, NpcAction.GRADUATE);
        helper.assertTrue(SoulLogic.get(plus).state() == SoulState.PLUS && !SoulLogic.get(plus).shinigami().enrolled(),
                "an action outside the Plus's view went through");
        helper.assertTrue(plus.getInventory().isEmpty(), "an action outside the Plus's view handed out items");

        // Konsō, then enrolment, through the dialogue's own path.
        instructor.perform(plus, NpcAction.KONSO);
        helper.assertTrue(SoulLogic.get(plus).state() == SoulState.RUKONGAI, "asking the instructor for Konsō made a "
                + SoulLogic.get(plus).state());
        instructor.perform(plus, NpcAction.ENROLL);
        helper.assertTrue(ShinigamiLogic.isStudent(SoulLogic.get(plus)), "asking to enroll did not enroll");

        // Too far away to be heard.
        Player far = visitor(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        far.setPos(far.getX() + 20.0, far.getY(), far.getZ());
        instructor.perform(far, NpcAction.ENROLL);
        helper.assertFalse(SoulLogic.get(far).shinigami().enrolled(), "a soul 20 blocks away enrolled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enrollGivesAnAsauchi(GameTestHelper helper) {
        Player player = visitor(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        helper.assertTrue(ShinigamiEvents.enroll(player), "a Rukongai soul could not enroll");
        SoulData data = SoulLogic.get(player);
        helper.assertTrue(ShinigamiLogic.isStudent(data), "enrolling did not make a student");
        helper.assertTrue(player.getInventory().countItem(ModItems.ASAUCHI.get()) == 1, "enrolling gave no Asauchi");
        helper.assertTrue(data.shinigami().asauchiIssuedAt() == helper.getLevel().getGameTime(),
                "the Asauchi's issue time was not kept");
        helper.assertFalse(ShinigamiEvents.enroll(player), "a student enrolled twice");
        helper.assertTrue(player.getInventory().countItem(ModItems.ASAUCHI.get()) == 1, "enrolling twice gave a second blade");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void graduationGivesTheShihakusho(GameTestHelper helper) {
        Player player = visitor(helper, ShinigamiTests.student(40.0F));
        SoulData data = SoulLogic.get(player);
        helper.assertFalse(ShinigamiEvents.graduate(player), "an untrained student graduated");
        helper.assertTrue(ShinigamiLogic.isStudent(data), "failing the exams ended the studies");

        ShinigamiLogic.graduationRequirements().forEach((art, level) -> data.setLevel(ShinigamiLogic.examinedStat(art), level));
        float before = data.maxReiryoku();
        helper.assertTrue(ShinigamiEvents.graduate(player), "a trained student failed the exams");
        helper.assertTrue(data.state() == SoulState.SHINIGAMI && data.tier(PowerPath.SHINIGAMI) == 1,
                "graduating made a " + data.state() + " of tier " + data.tier(PowerPath.SHINIGAMI));
        helper.assertTrue(Math.abs(data.maxReiryoku() - before - BleachConfig.get(BleachConfig.AWAKENING_GROWTH)) < EPSILON,
                "graduating grew reiryoku from " + before + " to " + data.maxReiryoku());
        for (var piece : List.of(ModItems.SHIHAKUSHO_KOSODE.get(), ModItems.SHIHAKUSHO_HAKAMA.get(), ModItems.WARAJI.get())) {
            helper.assertTrue(player.getInventory().countItem(piece) == 1, "graduating did not give " + piece);
        }
        helper.assertFalse(ShinigamiEvents.graduate(player), "a Shinigami graduated again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void asauchiReissueNeedsCooldownAndNoBlade(GameTestHelper helper) {
        Player player = visitor(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        ShinigamiEvents.enroll(player);
        SoulData data = SoulLogic.get(player);
        long now = helper.getLevel().getGameTime();
        helper.assertFalse(ShinigamiEvents.mayReceiveAsauchi(player, data, now), "a student with a blade may get another");
        helper.assertFalse(ShinigamiEvents.reissueAsauchi(player), "a second blade was handed out");

        player.getInventory().clearContent();
        helper.assertFalse(ShinigamiEvents.reissueAsauchi(player), "a lost blade was replaced at once");

        long wait = (long) BleachConfig.get(BleachConfig.ASAUCHI_REISSUE_MINUTES) * TICKS_PER_MINUTE;
        data.setShinigami(data.shinigami().withAsauchiIssuedAt(now - wait));
        helper.assertTrue(ShinigamiEvents.mayReceiveAsauchi(player, data, now), "the wait for a new blade never ends");
        helper.assertTrue(ShinigamiEvents.reissueAsauchi(player), "a lost blade was not replaced after the wait");
        helper.assertTrue(player.getInventory().countItem(ModItems.ASAUCHI.get()) == 1, "the new blade is not in the inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void instructorKonsoFreesAPlus(GameTestHelper helper) {
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        Player plus = visitor(helper, ShinigamiTests.soul(SoulState.PLUS, 40.0F));
        helper.assertTrue(ShinigamiEvents.konso(instructor, plus, false), "the instructor's Konsō did not take");
        SoulData data = SoulLogic.get(plus);
        helper.assertTrue(data.state() == SoulState.RUKONGAI && data.chainMaxTicks() == 0, "Konsō left a " + data.state());
        helper.assertFalse(ShinigamiEvents.konso(instructor, plus, false), "Konsō worked on a soul with no chain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woundedTransferAwakensAHuman(GameTestHelper helper) {
        WoundedShinigami wounded = helper.spawn(ModEntities.WOUNDED_SHINIGAMI.get(), 1, 1, 1);
        Player human = visitor(helper, awareHuman());
        SoulData data = SoulLogic.get(human);
        float before = data.maxReiryoku();
        wounded.perform(human, NpcAction.ACCEPT_POWER);
        helper.assertTrue(ShinigamiLogic.isSubstitute(data), "taking her power did not make a Substitute");
        helper.assertTrue(Math.abs(data.maxReiryoku() - before - BleachConfig.get(BleachConfig.AWAKENING_GROWTH)) < EPSILON,
                "taking her power grew reiryoku from " + before + " to " + data.maxReiryoku());
        helper.assertTrue(Math.abs(data.reiryoku() - data.maxReiryoku()) < EPSILON, "the new Substitute was not refilled");
        helper.assertTrue(human.getInventory().countItem(ModItems.ASAUCHI.get()) == 1, "no blade formed in their hands");
        helper.assertTrue(wounded.gavePower() && wounded.lifetime() <= WoundedShinigami.LIFETIME_AFTER_GIVING,
                "she stays on after giving her power away");

        Player another = visitor(helper, awareHuman());
        helper.assertFalse(ShinigamiEvents.acceptPowerFrom(wounded, another), "she gave her power twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woundedShinigamiLeavesInTime(GameTestHelper helper) {
        WoundedShinigami wounded = helper.spawn(ModEntities.WOUNDED_SHINIGAMI.get(), 1, 1, 1);
        wounded.setLifetime(5);
        helper.runAfterDelay(2, () -> helper.assertTrue(wounded.isCrouching(), "the wounded Shinigami stands"));
        helper.succeedWhen(() -> helper.assertEntityNotPresent(ModEntities.WOUNDED_SHINIGAMI.get()));
    }

    @GameTest(template = "empty")
    public static void nearbyShinigamiNpcSlowsTheChain(GameTestHelper helper) {
        AcademyInstructor npc = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 2);
        helper.assertTrue(ShinigamiLogic.slowsErosion(npc), "a Shinigami NPC does not slow the chain");
        helper.assertFalse(ShinigamiLogic.slowsErosion(pig), "a pig slows the chain");
        helper.assertTrue(ShinigamiLogic.slowsErosion(visitor(helper, ShinigamiTests.substitute(60.0F))),
                "a Substitute does not slow the chain");
        helper.assertFalse(ShinigamiLogic.slowsErosion(visitor(helper, awareHuman())), "a Human slows the chain");
        helper.assertTrue(ShinigamiNpc.EXAMINED_ARTS.size() == 4 && ShinigamiNpc.EXAMINED_ARTS.contains(Stat.KIDO),
                "the exams do not cover the four Zankensoki");
        helper.succeed();
    }
}
