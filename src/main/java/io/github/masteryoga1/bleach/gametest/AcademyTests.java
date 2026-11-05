package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.item.DenreishinkiItem;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulOffer;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.world.AcademySites;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
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
                ShinigamiTests.soul(SoulState.HOLLOW, 40.0F),
                new DialogueView[] {DialogueView.HOLLOW_PURIFY, DialogueView.HOLLOW},
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
                case STUDENT -> List.of(NpcAction.GRADUATE, NpcAction.ASAUCHI, NpcAction.LESSON);
                case SUBSTITUTE, SHINIGAMI -> List.of(NpcAction.ASAUCHI);
                case OFFER_POWER -> List.of(NpcAction.ACCEPT_POWER);
                case ELDER_GREETING, ELDER_LESSON -> List.of(NpcAction.LESSON);
                case ELDER_TEACH -> List.of(NpcAction.RANSOTENGAI);
                case ELDER_RESTORE -> List.of(NpcAction.RESTORE);
                case HUNTER_CHALLENGE, HUNTER_RIVALRY -> List.of(NpcAction.CONTEST);
                case HOLLOW_PURIFY -> List.of(NpcAction.PURIFY);
                case LIVING, HOLLOW, RESTING, QUINCY, ELDER_TRIAL, ELDER_IDLE, ELDER_STRANGER, ELDER_HOLLOW,
                        HUNTER_CONTEST, HUNTER_RESTING, HUNTER_PLAIN, HUNTER_HOLLOW -> List.of();
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

        ShinigamiLogic.graduationRequirements().forEach((art, level) -> data.setLevel(art, level));
        float before = data.maxReiryoku();
        helper.assertTrue(ShinigamiEvents.graduate(player), "a trained student failed the exams");
        helper.assertTrue(data.state() == SoulState.SHINIGAMI && data.tier(PowerPath.SHINIGAMI) == 1,
                "graduating made a " + data.state() + " of tier " + data.tier(PowerPath.SHINIGAMI));
        helper.assertTrue(Math.abs(data.maxReiryoku() - before - BleachConfig.get(BleachConfig.AWAKENING_GROWTH)) < EPSILON,
                "graduating grew reiryoku from " + before + " to " + data.maxReiryoku());
        for (var piece : List.of(ModItems.SHIHAKUSHO_KOSODE.get(), ModItems.SHIHAKUSHO_HAKAMA.get(), ModItems.WARAJI.get())) {
            helper.assertTrue(player.getInventory().countItem(piece) == 1, "graduating did not give " + piece);
        }
        int pagers = player.getInventory().countItem(ModItems.DENREISHINKI.get());
        helper.assertTrue(pagers == 1, "graduating gave " + pagers + " Denreishinki");
        DenreishinkiItem.giveIfMissing(player);
        pagers = player.getInventory().countItem(ModItems.DENREISHINKI.get());
        helper.assertTrue(pagers == 1, "a Shinigami who carries a Denreishinki was given another, and has " + pagers);
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
    public static void playerTransferMakesASubstitute(GameTestHelper helper) {
        Player shinigami = visitor(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F));
        Player human = visitor(helper, awareHuman());
        Player bystander = visitor(helper, awareHuman());
        SoulData giver = SoulLogic.get(shinigami);
        SoulData data = SoulLogic.get(human);
        helper.assertTrue(ShinigamiEvents.offer(shinigami, human, SoulOffer.Kind.POWER), "the offer was not made");
        SoulOffer offer = ShinigamiEvents.pendingOffer(human);
        helper.assertTrue(offer != null && offer.kind() == SoulOffer.Kind.POWER, "the Human was not offered power");
        helper.assertFalse(ShinigamiEvents.offer(shinigami, bystander, SoulOffer.Kind.POWER), "a second offer came at once");
        helper.assertFalse(ShinigamiEvents.accept(human, bystander), "an offer was accepted from someone who never made it");
        helper.assertTrue(ShinigamiEvents.accept(human, shinigami), "accepting found no offer");

        helper.assertTrue(ShinigamiLogic.isSubstitute(data), "taking a Shinigami's power did not make a Substitute");
        helper.assertTrue(human.getInventory().countItem(ModItems.ASAUCHI.get()) == 1, "no blade formed in their hands");
        helper.assertTrue(ShinigamiLogic.isPowerLent(giver, helper.getLevel().getGameTime()), "the giver's power is not lent");
        float kept = (float) (200.0 * (1.0 - BleachConfig.get(BleachConfig.TRANSFER_FRACTION)));
        helper.assertTrue(Math.abs(giver.maxReiryoku() - kept) < EPSILON && giver.reiryoku() == 0.0F,
                "the giver kept " + giver.reiryoku() + " of " + giver.maxReiryoku() + " reiryoku");
        helper.assertTrue(shinigami.hasEffect(MobEffects.WEAKNESS), "the giver was not left drained");
        helper.assertTrue(ShinigamiEvents.pendingOffer(human) == null, "the offer still stands");
        helper.assertFalse(ShinigamiEvents.accept(human, shinigami), "the offer was accepted twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerKonsoRewardsThePerformer(GameTestHelper helper) {
        Player shinigami = visitor(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F));
        Player plus = visitor(helper, ShinigamiTests.soul(SoulState.PLUS, 40.0F));
        SoulData performer = SoulLogic.get(shinigami);
        float before = performer.maxReiryoku();
        helper.assertTrue(ShinigamiEvents.offer(shinigami, plus, SoulOffer.Kind.KONSO), "the offer was not made");
        helper.assertTrue(ShinigamiEvents.accept(plus, shinigami), "accepting found no offer");
        SoulData data = SoulLogic.get(plus);
        helper.assertTrue(data.state() == SoulState.RUKONGAI, "accepting Konsō left a " + data.state());
        helper.assertTrue(Math.abs(performer.maxReiryoku() - before - BleachConfig.get(BleachConfig.KONSO_GROWTH)) < EPSILON,
                "Konsō grew the performer's reiryoku from " + before + " to " + performer.maxReiryoku());

        Player lent = visitor(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F));
        Player another = visitor(helper, ShinigamiTests.soul(SoulState.PLUS, 40.0F));
        SoulData lentData = SoulLogic.get(lent);
        lentData.setShinigami(lentData.shinigami().withPowerLentUntil(helper.getLevel().getGameTime() + TICKS_PER_MINUTE));
        helper.assertTrue(ShinigamiEvents.offer(lent, another, SoulOffer.Kind.KONSO), "the offer was not made");
        helper.assertTrue(ShinigamiEvents.accept(another, lent), "accepting found no offer");
        helper.assertTrue(SoulLogic.get(another).state() == SoulState.PLUS, "a Shinigami whose power is lent performed Konsō");
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

    @GameTest(template = "empty")
    public static void instructorStaysWhereHeStands(GameTestHelper helper) {
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        helper.assertFalse(instructor.canBeLeashed(), "the instructor can be leashed");
        helper.assertTrue(instructor.getPistonPushReaction() == PushReaction.IGNORE, "pistons push the instructor");
        helper.assertFalse(instructor.isPushedByFluid(), "water pushes the instructor");
        helper.assertFalse(instructor.canUsePortal(false), "the instructor goes through portals");
        Vec3[] home = new Vec3[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(instructor.home() != null, "the instructor never settled"))
                .thenExecute(() -> {
                    home[0] = instructor.home();
                    // A fishing rod's pull, then dragged a block: twice the slack he allows, and still inside
                    // the template, whose chunks are the only ones a test keeps ticking entities in.
                    instructor.setDeltaMovement(1.5, 0.4, 0.0);
                    instructor.setPos(home[0].x + 1.0, home[0].y, home[0].z);
                })
                .thenWaitUntil(() -> helper.assertTrue(instructor.position().distanceTo(home[0]) < 1.0E-3,
                        "the instructor stayed where he was dragged, at " + instructor.position()))
                .thenExecute(() -> {
                    CompoundTag saved = instructor.saveWithoutId(new CompoundTag());
                    AcademyInstructor reloaded = ModEntities.ACADEMY_INSTRUCTOR.get().create(helper.getLevel());
                    reloaded.load(saved);
                    helper.assertTrue(home[0].equals(reloaded.home()), "the instructor forgot where he stands");
                    // A copy saved somewhere else, as a structure block makes, finds its own place to stand.
                    ListTag elsewhere = new ListTag();
                    elsewhere.add(DoubleTag.valueOf(home[0].x + 40.0));
                    elsewhere.add(DoubleTag.valueOf(home[0].y));
                    elsewhere.add(DoubleTag.valueOf(home[0].z));
                    saved.put("Pos", elsewhere);
                    AcademyInstructor copy = ModEntities.ACADEMY_INSTRUCTOR.get().create(helper.getLevel());
                    copy.load(saved);
                    helper.assertTrue(copy.home() == null, "a copied instructor walks back to the original's place");
                })
                .thenSucceed();
    }

    @GameTest(template = "academy/academy")
    public static void academyTemplateHoldsTheInstructor(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            helper.assertEntityPresent(ModEntities.ACADEMY_INSTRUCTOR.get());
            AcademyInstructor instructor = helper.getEntities(ModEntities.ACADEMY_INSTRUCTOR.get()).get(0);
            helper.assertFalse(instructor.isInWall(), "the instructor is stuck in a wall");
            helper.assertTrue(instructor.isInvulnerable(), "the Academy's instructor can be hurt");
            int[] tables = {0};
            helper.forEveryBlockInStructure(pos -> {
                if (helper.getBlockState(pos).is(ModBlocks.TAILORING_TABLE.get())) {
                    tables[0]++;
                }
            });
            helper.assertTrue(tables[0] == 1, "the Academy has " + tables[0] + " Tailoring Tables");
            // Blocks that need support survived placement: hanging and standing lanterns, both door
            // halves. Test positions sit one above the template's, which starts above the structure block.
            helper.assertBlockPresent(Blocks.LANTERN, 4, 5, 10);
            helper.assertBlockPresent(Blocks.LANTERN, 3, 2, 3);
            helper.assertBlockPresent(Blocks.BAMBOO_DOOR, 7, 2, 7);
            helper.assertBlockPresent(Blocks.BAMBOO_DOOR, 7, 3, 7);
            helper.assertEntityNotPresent(EntityType.FALLING_BLOCK);
        });
    }

    @GameTest(template = "empty")
    public static void academyWorldgenIsRegistered(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE).containsKey(BleachMod.id("academy")),
                "the Academy structure did not load");
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(BleachMod.id("academy")),
                "the Academy structure set did not load");
        helper.assertTrue(access.registryOrThrow(Registries.TEMPLATE_POOL).containsKey(BleachMod.id("academy/start")),
                "the Academy template pool did not load");
        TagKey<Biome> biomes = TagKey.create(Registries.BIOME, BleachMod.id("has_structure/academy"));
        helper.assertTrue(access.registryOrThrow(Registries.BIOME).getTag(biomes).map(set -> set.size() > 0).orElse(false),
                "the Academy can generate in no biome");
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE).getTag(BleachTags.ACADEMY)
                        .map(set -> set.size() == 1).orElse(false), "the Academy structure tag is wrong");
        helper.assertTrue(helper.getLevel().getStructureManager().get(BleachMod.id("academy/academy")).isPresent(),
                "the Academy template did not load");
        helper.succeed();
    }

    /** Far from the other tests, so building it disturbs nothing. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void guaranteedAcademyIsPlaced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = AcademySites.placeAt(level, new ChunkPos(1000, 1000));
        helper.assertTrue(corner != null, "the Academy could not be built");
        helper.assertTrue(corner.getX() == 16000 && corner.getZ() == 16000, "the Academy reports its place as " + corner);
        AABB around = new AABB(corner).inflate(32.0, 0.0, 32.0).setMinY(level.getMinBuildHeight()).setMaxY(level.getMaxBuildHeight());
        List<AcademyInstructor> instructors = level.getEntitiesOfClass(AcademyInstructor.class, around);
        helper.assertTrue(instructors.size() == 1, "the built Academy has " + instructors.size() + " instructors");
        BlockPos at = instructors.get(0).blockPosition();
        helper.assertTrue(BlockPos.betweenClosedStream(at.offset(-8, -2, -8), at.offset(8, 2, 8))
                        .anyMatch(pos -> level.getBlockState(pos).is(ModBlocks.TAILORING_TABLE.get())),
                "the built Academy has no Tailoring Table near the instructor");
        instructors.forEach(instructor -> instructor.discard());
        helper.succeed();
    }

    /** Around a spot far from the other tests and from the Academy above, standing in for world spawn. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void guaranteedAcademyIsBuiltNearSpawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AcademySites sites = new AcademySites();
        BlockPos spawn = new BlockPos(-16000, 64, 16000);
        BlockPos built = sites.ensureGuaranteed(level, spawn);
        helper.assertTrue(built != null, "no guaranteed Academy was built");
        helper.assertTrue(sites.guaranteed().filter(built::equals).isPresent(), "the guaranteed Academy was not remembered");
        double dx = built.getX() - spawn.getX();
        double dz = built.getZ() - spawn.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        helper.assertTrue(distance > 64.0 && distance < 1320.0, "the guaranteed Academy is " + distance + " blocks from spawn");
        helper.assertTrue(built.equals(sites.ensureGuaranteed(level, spawn)), "a second Academy was built");
        AABB around = new AABB(built).inflate(32.0, 0.0, 32.0).setMinY(level.getMinBuildHeight()).setMaxY(level.getMaxBuildHeight());
        List<AcademyInstructor> instructors = level.getEntitiesOfClass(AcademyInstructor.class, around);
        helper.assertTrue(instructors.size() == 1, "the guaranteed Academy has " + instructors.size() + " instructors");
        instructors.forEach(instructor -> instructor.discard());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void academySitesSurviveSaving(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        AcademySites empty = AcademySites.load(new CompoundTag(), registries);
        helper.assertTrue(empty.guaranteed().isEmpty(), "a new world remembers an Academy");
        CompoundTag tag = new CompoundTag();
        BlockPos site = new BlockPos(160, 0, -96);
        tag.put("guaranteed", NbtUtils.writeBlockPos(site));
        AcademySites loaded = AcademySites.load(AcademySites.load(tag, registries).save(new CompoundTag(), registries), registries);
        helper.assertTrue(loaded.guaranteed().equals(Optional.of(site)), "the guaranteed Academy moved to " + loaded.guaranteed());
        helper.succeed();
    }
}
