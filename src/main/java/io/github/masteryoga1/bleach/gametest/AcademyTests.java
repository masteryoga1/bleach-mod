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
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.HomeWorldRespawn;
import io.github.masteryoga1.bleach.soul.KonsoTarget;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulOffer;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.AcademySite;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Shinigami NPCs, their dialogue and what they do for the souls who talk to them, and for the way on to
 * the Academy in Soul Society.
 */
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
                case SUBSTITUTE, SHINIGAMI -> List.of(NpcAction.ASAUCHI, NpcAction.BUTTERFLY);
                case OFFER_POWER -> List.of(NpcAction.ACCEPT_POWER);
                case ELDER_GREETING, ELDER_LESSON -> List.of(NpcAction.LESSON);
                case ELDER_TEACH -> List.of(NpcAction.RANSOTENGAI);
                case ELDER_RESTORE -> List.of(NpcAction.RESTORE);
                case HUNTER_CHALLENGE, HUNTER_RIVALRY -> List.of(NpcAction.CONTEST);
                case HOLLOW_PURIFY -> List.of(NpcAction.PURIFY);
                case LIVING, HOLLOW, RESTING, QUINCY, ELDER_TRIAL, ELDER_IDLE, ELDER_STRANGER, ELDER_HOLLOW,
                        HUNTER_CONTEST, HUNTER_RESTING, HUNTER_PLAIN, HUNTER_HOLLOW, KEEPER_OPEN, KEEPER_BARRED,
                        KEEPER_WON, KEEPER_BEATEN, KEEPER_FOE, HOMEWARD -> List.of();
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

    /** The pieces of the Academy's uniform this player carries or wears, in either trim. */
    private static int uniformPieces(Player player) {
        return player.getInventory().countItem(ModItems.ACADEMY_KOSODE.get())
                + player.getInventory().countItem(ModItems.ACADEMY_KOSODE_RED.get())
                + player.getInventory().countItem(ModItems.ACADEMY_HAKAMA.get())
                + player.getInventory().countItem(ModItems.ACADEMY_HAKAMA_RED.get());
    }

    /**
     * Enrolment hands out the Academy's uniform beside the Asauchi, kosode and hakama trimmed in blue, and marks it given;
     * a soul that already wears a kosode of either trim is handed only the hakama.
     */
    @GameTest(template = "empty")
    public static void enrolmentHandsOutTheUniform(GameTestHelper helper) {
        Player player = visitor(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        helper.assertTrue(ShinigamiEvents.enroll(player) && player.getData(SoulAttachments.UNIFORM_GIVEN),
                "a Rukongai soul could not enroll, or was not marked as handed the uniform");
        for (var piece : List.of(ModItems.ASAUCHI.get(), ModItems.ACADEMY_KOSODE.get(), ModItems.ACADEMY_HAKAMA.get())) {
            helper.assertTrue(player.getInventory().countItem(piece) == 1, "enrolling did not give one " + piece);
        }
        helper.assertTrue(uniformPieces(player) == 2, "enrolling gave " + uniformPieces(player) + " pieces of the uniform");
        Player sewn = visitor(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        sewn.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ACADEMY_KOSODE_RED.get()));
        ShinigamiEvents.enroll(sewn);
        helper.assertTrue(uniformPieces(sewn) == 2 && sewn.getInventory().countItem(ModItems.ACADEMY_HAKAMA.get()) == 1,
                "a soul wearing its own red kosode was handed " + (uniformPieces(sewn) - 1) + " more pieces");
        helper.succeed();
    }

    /**
     * A student enrolled before there was a uniform, whose save holds no mark of one, is handed it with a word as its
     * first talk with the instructor opens, and the mark is saved; its inventory is emptied after every talk, so each
     * later one would hand it again but for the mark, and a Shinigami or a soul not enrolled is handed none.
     */
    @GameTest(template = "empty")
    public static void anOldStudentIsHandedTheUniformOnce(GameTestHelper helper) {
        ServerPlayer student = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(2.5, 1.0, 1.5));
        student.setData(SoulAttachments.SOUL, ShinigamiTests.student(40.0F));
        String given = "bleach:uniform_given";
        helper.assertFalse(student.saveWithoutId(new CompoundTag()).getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                .contains(given), "a student never handed the uniform saves the mark of it");
        AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
        String received = "message.bleach.academy_uniform.received";
        try {
            for (int talk = 1; talk <= 3; talk++) {
                boolean opened = instructor.interact(student, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                        && TestPlayers.dialogues(student).size() == talk;
                int pieces = uniformPieces(student);
                int told = DutyTests.said(student, received).size();
                helper.assertTrue(opened && pieces == (talk == 1 ? 2 : 0) && told == 1,
                        "at talk " + talk + " the student was handed " + pieces + " pieces and told " + told + " times");
                student.getInventory().clearContent();
            }
        } finally {
            // Persistent, and the test grid is never cleared.
            instructor.discard();
        }
        CompoundTag saved = student.saveWithoutId(new CompoundTag());
        Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
        loaded.load(saved);
        helper.assertTrue(saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getBoolean(given)
                        && ShinigamiLogic.isStudent(SoulLogic.get(loaded)) && loaded.getData(SoulAttachments.UNIFORM_GIVEN),
                "the uniform given was not saved, or did not load");
        helper.assertFalse(ShinigamiEvents.uniformAtTalk(visitor(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F)))
                        || ShinigamiEvents.uniformAtTalk(visitor(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F))),
                "a Shinigami or a soul not enrolled was handed the uniform at a talk");
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
            helper.assertBlockPresent(ModBlocks.SHOJI_DOOR.get(), 7, 2, 7);
            helper.assertBlockPresent(ModBlocks.SHOJI_DOOR.get(), 7, 3, 7);
            // Seireitei's palette: white plaster on a black base, tatami underfoot, a tiled roof, and shōji joined to
            // the pillar beside them but not to the door.
            helper.assertBlockPresent(ModBlocks.BLACK_PLASTER.get(), 2, 2, 7);
            helper.assertBlockPresent(ModBlocks.WHITE_PLASTER.get(), 2, 5, 7);
            helper.assertBlockPresent(ModBlocks.TATAMI.get(), 7, 1, 12);
            helper.assertBlockPresent(ModBlocks.ROOF_TILES.get(), 7, 7, 11);
            BlockPos shoji = new BlockPos(6, 3, 7);
            helper.assertBlockPresent(ModBlocks.SHOJI.get(), shoji);
            helper.assertBlockProperty(shoji, CrossCollisionBlock.WEST, true);
            helper.assertBlockProperty(shoji, CrossCollisionBlock.EAST, false);
            helper.assertEntityNotPresent(EntityType.FALLING_BLOCK);
        });
    }

    @GameTest(template = "empty")
    public static void academyWorldgenIsRegistered(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE).containsKey(BleachMod.id("academy")),
                "the Academy structure did not load");
        helper.assertFalse(access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(BleachMod.id("academy")),
                "the Overworld Academy still has a structure set");
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(BleachMod.id("seireitei_academy"))
                        && access.registryOrThrow(Registries.STRUCTURE).containsKey(BleachMod.id("seireitei_academy")),
                "the Seireitei Academy's structure and set did not load");
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

    // ---- The way to the Academy in Soul Society ----

    /**
     * Asserts that the test world's Academy stands, as its chunks' loads build it (see {@link SiteTests}): its start
     * valid, and both of its chunks referring to it.
     */
    static void assertAcademyStands(GameTestHelper helper, ServerLevel soul) {
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(AcademySite.STRUCTURE);
        ChunkPos home = AcademySite.startChunk();
        StructureStart start = structure == null ? null : soul.getChunk(home.x, home.z).getStartForStructure(structure);
        helper.assertTrue(start != null && start.isValid(), "the Academy in Soul Society has no valid start yet");
        for (ChunkPos pos : AcademySite.COVERED) {
            helper.assertTrue(soul.getChunk(pos.x, pos.z).getReferencesForStructure(structure).contains(home.toLong()),
                    "chunk " + pos + " holds no reference to the Academy in Soul Society yet");
        }
    }

    /**
     * A real Plus asks an Overworld instructor for Konsō and is sent on, into Soul Society, to the gates of the Academy,
     * where the server takes the shortcut and where its instructor stands, letting go of the bed it set in the Overworld.
     * The Academy's chunks build it first.
     */
    @GameTest(template = "empty", batch = "konso_move", timeoutTicks = 400)
    public static void anOverworldInstructorSendsASoulToTheAcademy(GameTestHelper helper) {
        helper.assertTrue(AcademyLocator.sendsOnFrom(Level.OVERWORLD)
                && !AcademyLocator.sendsOnFrom(BleachWorlds.SOUL_SOCIETY), "the instructors send on from the wrong worlds");
        ServerLevel soul = SiteTests.holdAcademy(helper);
        ServerPlayer plus = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(2.5, 1.0, 1.5));
        SoulData data = ShinigamiTests.soul(SoulState.PLUS, 40.0F);
        data.startChain(72_000);
        plus.setData(SoulAttachments.SOUL, data);
        plus.setRespawnPosition(Level.OVERWORLD, helper.absolutePos(new BlockPos(1, 1, 3)), 0.0F, true, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertAcademyStands(helper, soul))
                .thenExecute(() -> {
                    AcademyInstructor instructor = helper.spawn(ModEntities.ACADEMY_INSTRUCTOR.get(), 1, 1, 1);
                    KonsoTarget sendsTo = BleachConfig.get(BleachConfig.KONSO_SENDS_TO);
                    try {
                        BleachConfig.KONSO_SENDS_TO.set(KonsoTarget.ACADEMY);
                        instructor.perform(plus, NpcAction.KONSO);
                    } finally {
                        BleachConfig.KONSO_SENDS_TO.set(sendsTo);
                        // The Overworld one is persistent, and the test grid is never cleared.
                        instructor.discard();
                    }
                    // A change of world is made at once, inside the Konsō.
                    helper.assertTrue(plus.level() == soul, "the soul is still in " + plus.level().dimension().location());
                    SoulData sent = SoulLogic.get(plus);
                    helper.assertTrue(sent.state() == SoulState.RUKONGAI && sent.chainMaxTicks() == 0,
                            "Konsō left a " + sent.state() + " with a chain of " + sent.chainMaxTicks());
                    helper.assertTrue(plus.position().distanceTo(AcademyLocator.LANDING) < 0.01,
                            "the soul landed at " + plus.position() + ", not " + AcademyLocator.LANDING);
                    helper.assertTrue(plus.getRespawnPosition() == null,
                            "the soul kept its bed at " + plus.getRespawnPosition());
                })
                .thenWaitUntil(() -> {
                    // The Academy's own instructor, whose entities may load a little after its chunk's blocks.
                    AABB near = plus.getBoundingBox().inflate(16.0);
                    helper.assertFalse(soul.getEntitiesOfClass(AcademyInstructor.class, near).isEmpty(),
                            "no instructor stands within 16 blocks of the landing");
                })
                .thenSucceed();
    }

    /**
     * What a soul senses of the Academy: in Soul Society which way and how far to its gate, and from outside the wall,
     * by the nearest gate, but not on the soul screen at the Academy; anywhere else only that it lies in Soul Society.
     * And the teleport {@code /bleach academy} suggests, to where Konsō's shortcut lands.
     */
    @GameTest(template = "empty")
    public static void sensingTheAcademy(GameTestHelper helper) {
        BlockPos centre = new BlockPos(0, 72, 0);
        assertLine(helper, AcademyLocator.senseLine(BleachWorlds.SOUL_SOCIETY, centre), "message.bleach.academy_sensed",
                "message.bleach.direction.south_west", 300L);
        assertLine(helper, AcademyLocator.senseLine(Level.OVERWORLD, centre), "message.bleach.academy_sensed.elsewhere",
                null, 0L);
        // The second district of the South, outside the wall: the way in is by Shuwaimon, 200 blocks east.
        BlockPos south = new BlockPos(-192, 73, 531);
        assertGateLine(helper, AcademyLocator.remindLine(BleachWorlds.SOUL_SOCIETY, south),
                "message.bleach.academy_direction.gate", "message.bleach.place.gate.south", "message.bleach.direction.east",
                200L);
        assertLine(helper, AcademyLocator.remindLine(Level.NETHER, south), "message.bleach.academy_direction.elsewhere",
                null, 0L);
        BlockPos inside = new BlockPos(-192, 73, 300);
        assertLine(helper, AcademyLocator.remindLine(BleachWorlds.SOUL_SOCIETY, inside), "message.bleach.academy_direction",
                "message.bleach.direction.north", 50L);
        // The seventeenth district of the West: by Hakutōmon, not straight at the Academy through the wall.
        BlockPos west = new BlockPos(-1000, 72, 300);
        assertGateLine(helper, AcademyLocator.senseLine(BleachWorlds.SOUL_SOCIETY, west),
                "message.bleach.academy_sensed.gate", "message.bleach.place.gate.west", "message.bleach.direction.north_east",
                550L);
        assertGateLine(helper, AcademyLocator.wayLine(west), "message.bleach.academy_way.gate",
                "message.bleach.place.gate.west", "message.bleach.direction.north_east", 550L);
        assertLine(helper, AcademyLocator.wayLine(centre), "message.bleach.academy_way",
                "message.bleach.direction.south_west", 300L);

        // The soul screen shows no way within 48 blocks of the gate, where "about 50 blocks away" would mislead.
        BlockPos gate = AcademyLocator.GATE;
        helper.assertTrue(AcademyLocator.AT_ACADEMY_BLOCKS == 48 && AcademyLocator.atAcademy(gate)
                        && AcademyLocator.atAcademy(gate.offset(48, 0, 0))
                        && AcademyLocator.atAcademy(BlockPos.containing(AcademyLocator.LANDING)),
                "a soul at the Academy's gate, 48 blocks east of it or at its landing is not at the Academy");
        helper.assertFalse(AcademyLocator.atAcademy(gate.offset(34, 0, 35)) || AcademyLocator.atAcademy(inside),
                "a soul 48.8 or 69 blocks from the Academy's gate is at the Academy");
        String command = AcademyLocator.teleportCommand();
        helper.assertTrue(command.equals("/execute in bleach:soul_society run tp @s -193.5 73 231.5"),
                "the Academy's command is " + command);
        helper.succeed();
    }

    /** A line with this key and, unless {@code direction} is null, that direction's key and this distance. */
    private static void assertLine(GameTestHelper helper, Component line, String key, @Nullable String direction,
                                   long distance) {
        helper.assertTrue(line.getContents() instanceof TranslatableContents, "the line " + line + " is not translated");
        TranslatableContents contents = (TranslatableContents) line.getContents();
        helper.assertTrue(contents.getKey().equals(key), "the line's key is " + contents.getKey() + ", not " + key);
        Object[] args = contents.getArgs();
        if (direction == null) {
            helper.assertTrue(args.length == 0, "the line far away has " + args.length + " arguments");
            return;
        }
        helper.assertTrue(args.length == 2 && args[0] instanceof Component way
                        && way.getContents() instanceof TranslatableContents towards && towards.getKey().equals(direction)
                        && Long.valueOf(distance).equals(args[1]), "the line said " + line);
    }

    /** A line from outside the wall with this key: the gate's name with {@code gateKey}, this direction and distance. */
    private static void assertGateLine(GameTestHelper helper, Component line, String key, String gateKey, String direction,
                                       long distance) {
        helper.assertTrue(line.getContents() instanceof TranslatableContents, "the line " + line + " is not translated");
        TranslatableContents contents = (TranslatableContents) line.getContents();
        helper.assertTrue(contents.getKey().equals(key), "the line's key is " + contents.getKey() + ", not " + key);
        Object[] args = contents.getArgs();
        helper.assertTrue(args.length == 3 && args[0] instanceof Component gate
                        && gate.getContents() instanceof TranslatableContents named && named.getKey().equals(gateKey)
                        && args[1] instanceof Component way
                        && way.getContents() instanceof TranslatableContents towards && towards.getKey().equals(direction)
                        && Long.valueOf(distance).equals(args[2]), "the line said " + line);
    }

    /** A soul's record saved while it remembered an Overworld Academy reads as before, and is written without it. */
    @GameTest(template = "empty")
    public static void oldAcademyPosIsIgnored(GameTestHelper helper) {
        CompoundTag old = new CompoundTag();
        old.putBoolean("enrolled", true);
        old.putInt("konso_rewards", 7);
        old.put("academy_pos", new IntArrayTag(new int[] {160, 64, -96}));
        ShinigamiProgress read = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(read.enrolled() && read.konsoRewards() == 7 && read.asauchiIssuedAt() == ShinigamiProgress.NEVER,
                "the record from before 7d3 read as " + read);
        Tag written = ShinigamiProgress.CODEC.encodeStart(NbtOps.INSTANCE, read).getOrThrow();
        helper.assertTrue(written instanceof CompoundTag tag && !tag.contains("academy_pos"),
                "the record was written as " + written);
        helper.succeed();
    }

    /**
     * Where each soul wakes on dying, read as it died, with no spawn of its own and with one: the table for every soul.
     * Then the real respawn event for a real player, once the Academy's chunks have built it: a student at the Academy,
     * at its gate facing the hall, unless it is back from the End's fight, the server has the rule off, or it has a
     * spawn of its own; a basic Hollow where vanilla puts it. The player is never killed.
     */
    @GameTest(template = "empty", batch = "konso_move", timeoutTicks = 100)
    public static void respawnWakesAtTheAcademy(GameTestHelper helper) {
        SoulData withPower = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        withPower.setTier(PowerPath.SHINIGAMI, 1);
        SoulData torn = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        torn.markSoulDestroyed();
        SoulData diedOnce = SoulLogic.afterDeath(ShinigamiTests.substitute(60.0F));
        helper.assertTrue(diedOnce.state() == SoulState.SHINIGAMI, "a Substitute's first death left a " + diedOnce.state());
        assertHome(helper, "a Rukongai soul", ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F), HomeWorldRespawn.Home.RUKONGAI);
        assertHome(helper, "a Rukongai soul with a Shinigami tier", withPower, HomeWorldRespawn.Home.RUKONGAI);
        assertHome(helper, "a Quincy who passed on", QuincyTests.in(SoulState.RUKONGAI,
                QuincyTests.quincy(Bloodline.ECHT, 1, 400.0F)), HomeWorldRespawn.Home.RUKONGAI);
        assertHome(helper, "a student", ShinigamiTests.student(40.0F), HomeWorldRespawn.Home.ACADEMY);
        assertHome(helper, "a Rukongai soul being torn apart", torn, HomeWorldRespawn.Home.VANILLA);
        assertHome(helper, "a Shinigami", ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F), HomeWorldRespawn.Home.COURT);
        assertHome(helper, "a Substitute that has died once", diedOnce, HomeWorldRespawn.Home.COURT);
        assertHome(helper, "a Plus", ShinigamiTests.soul(SoulState.PLUS, 40.0F), HomeWorldRespawn.Home.VANILLA);
        assertHome(helper, "a Human", ShinigamiTests.soul(SoulState.HUMAN, 400.0F), HomeWorldRespawn.Home.VANILLA);
        assertHome(helper, "a living Substitute", ShinigamiTests.substitute(60.0F), HomeWorldRespawn.Home.VANILLA);
        assertHome(helper, "a Hollow", ShinigamiTests.soul(SoulState.HOLLOW, 40.0F), HomeWorldRespawn.Home.VANILLA);
        assertHome(helper, "an Arrancar", ShinigamiTests.soul(SoulState.ARRANCAR, 40.0F), HomeWorldRespawn.Home.HUECO_MUNDO);
        KonsoTarget sendsTo = BleachConfig.get(BleachConfig.KONSO_SENDS_TO);
        try {
            BleachConfig.KONSO_SENDS_TO.set(KonsoTarget.ACADEMY);
            assertHome(helper, "a Rukongai soul where Konsō sends souls to the Academy",
                    ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F), HomeWorldRespawn.Home.ACADEMY);
        } finally {
            BleachConfig.KONSO_SENDS_TO.set(sendsTo);
        }

        ServerLevel level = helper.getLevel();
        ServerLevel soul = SiteTests.holdAcademy(helper);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, ShinigamiTests.student(40.0F));
        helper.startSequence()
                .thenWaitUntil(() -> assertAcademyStands(helper, soul))
                .thenExecute(() -> {
                    DimensionTransition spawn = new DimensionTransition(level, player.position(), Vec3.ZERO, 0.0F, 0.0F,
                            DimensionTransition.DO_NOTHING);
                    DimensionTransition woken = respawn(player, spawn, false);
                    helper.assertTrue(woken.newLevel() == soul && woken.pos().distanceTo(AcademyLocator.LANDING) < 0.01
                                    && woken.yRot() == AcademyLocator.LANDING_YAW,
                            "the student wakes in " + woken.newLevel().dimension().location() + " at " + woken.pos());
                    helper.assertTrue(respawn(player, spawn, true) == spawn, "a soul back from the End's fight was moved");
                    boolean rule = BleachConfig.get(BleachConfig.HOME_WORLD_RESPAWN);
                    try {
                        BleachConfig.HOME_WORLD_RESPAWN.set(false);
                        helper.assertTrue(respawn(player, spawn, false) == spawn, "a soul was moved with the rule off");
                    } finally {
                        BleachConfig.HOME_WORLD_RESPAWN.set(rule);
                    }
                    player.setRespawnPosition(Level.OVERWORLD, helper.absolutePos(new BlockPos(1, 1, 3)), 0.0F, true, false);
                    helper.assertTrue(respawn(player, spawn, false) == spawn, "the Academy beat the soul's own spawn");
                    DimensionTransition missing = new DimensionTransition(level, player.position(), Vec3.ZERO, 0.0F, 0.0F,
                            true, DimensionTransition.DO_NOTHING);
                    helper.assertTrue(respawn(player, missing, false).newLevel() == soul,
                            "a soul whose bed is gone woke where it was");
                    player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
                    player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 40.0F));
                    helper.assertTrue(respawn(player, spawn, false) == spawn, "a Hollow was moved to Soul Society");
                })
                .thenSucceed();
    }

    /**
     * Asserts where this soul wakes with no spawn of its own, and that a spawn of its own beats any home but vanilla's.
     */
    private static void assertHome(GameTestHelper helper, String who, SoulData data, HomeWorldRespawn.Home expected) {
        HomeWorldRespawn.Home home = HomeWorldRespawn.homeOf(data, false);
        helper.assertTrue(home == expected, who + " would wake at " + home + ", not " + expected);
        helper.assertTrue(HomeWorldRespawn.homeOf(data, true) == HomeWorldRespawn.Home.VANILLA,
                who + "'s home beat its own spawn");
    }

    /** Where the real respawn event, posted for this soul, wakes it. */
    static DimensionTransition respawn(ServerPlayer player, DimensionTransition original, boolean fromEndFight) {
        PlayerRespawnPositionEvent event = new PlayerRespawnPositionEvent(player, original, fromEndFight);
        return NeoForge.EVENT_BUS.post(event).getDimensionTransition();
    }
}
