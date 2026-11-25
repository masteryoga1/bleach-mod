package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.KisukeUrahara;
import io.github.masteryoga1.bleach.entity.Kototsu;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.entity.UraharaGates;
import io.github.masteryoga1.bleach.item.HellButterflyItem;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.soul.TravelRecord;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.DangaiPlan;
import io.github.masteryoga1.bleach.world.UraharaRoom;
import io.github.masteryoga1.bleach.world.UraharaShop;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Kisuke Urahara's gates in the training room under his shop: who his Senkaimon takes where, his Garganta to Hueco Mundo
 * and its far end home, one of each a day for each soul, and what he refuses. They run in a batch of their own, and each
 * takes away what it set down, its gates and tickets too.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class UraharaGateTests {
    private static final String BATCH = "urahara_gates";
    private static final double SAME = 1.0E-6;
    /** Before his counter, 2.2 to 3.6 blocks from him; places from the shop's corner before its turn. */
    private static final BlockPos COUNTER = new BlockPos(3, 1, 7), BESIDE = new BlockPos(5, 1, 7),
            ASIDE = new BlockPos(6, 1, 6);
    /** On the ladder down, 9 over the room's floor: not yet come down. */
    private static final BlockPos LADDER = new BlockPos(2, -12, 9);
    /** On the room's floor, 7 blocks west of his Senkaimon: come down. */
    private static final BlockPos FLOOR = new BlockPos(10, -21, 9);
    /** A cell of his Senkaimon's, one of his Garganta's, and the top of the Keikaigi's south post. */
    private static final BlockPos GATE_CELL = new BlockPos(17, -19, 5), TEAR_CELL = new BlockPos(18, -19, 13),
            POST_TOP = new BlockPos(18, -16, 14);

    private UraharaGateTests() {
    }

    /**
     * Test 1. A Rukongai soul asked at the counter finds no gate from the ladder, and his Senkaimon opens only as it comes
     * down, in its cells, along the hall, to Soul Society for its time, its day spent. A guided Shinigami that asked too
     * is refused beside it but steps in with an aware Human: it comes out at the court, the others in the gate's lane.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 900)
    public static void hisSenkaimonTakesTheUnguidedIntoTheDangaiAndTheGuidedToTheCourt(GameTestHelper helper) {
        UraharaRoomTests.Room room = UraharaRoomTests.roomAt(helper, 540, 420, UraharaRoomTests.LIFT, true);
        ServerLevel level = room.level();
        ServerLevel soulSociety = TravelTests.holdCourt(helper);
        ChunkPos court = new ChunkPos(BlockPos.containing(TravelLogic.COURT));
        // Its own batch, so no other test shares the court's ticket while this one runs.
        room.held().atEnd(() -> soulSociety.getChunkSource().removeRegionTicket(TicketType.FORCED, court, 2, court));
        ServerPlayer asker = joined(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        ServerPlayer shinigami = joined(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F));
        HellButterflyItem.give(shinigami);
        ServerPlayer human = joined(helper, awareHuman(helper));
        Built[] built = new Built[1];
        Senkaimon[] gate = new Senkaimon[1];
        helper.startSequence()
                .thenWaitUntil(() -> UraharaShopTests.entitiesLoaded(helper, level, room.box()))
                .thenExecute(() -> {
                    Built here = built[0] = build(helper, room);
                    KisukeUrahara keeper = here.keeper();
                    standAt(asker, here, COUNTER);
                    standAt(shinigami, here, BESIDE);
                    keeper.perform(asker, NpcAction.SENKAIMON);
                    keeper.perform(shinigami, NpcAction.SENKAIMON);
                    helper.assertTrue(told(keeper, asker).equals(List.of("senkaimon.shop_soul", "senkaimon.way"))
                            && told(keeper, shinigami).equals(List.of("senkaimon.shop_shinigami", "senkaimon.way_guided")),
                            "the soul was told " + told(keeper, asker) + " and the Shinigami " + told(keeper, shinigami));
                    standAt(asker, here, LADDER);
                })
                // His ticks, which look for souls come down, run before each step's.
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(gates(built[0]).isEmpty()
                            && asker.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == ShinigamiProgress.NEVER,
                            "his Senkaimon opened at the word or for a soul on the ladder, or the day was spent first");
                    standAt(asker, built[0], FLOOR);
                })
                .thenExecuteAfter(1, () -> {
                    Built here = built[0];
                    List<Senkaimon> open = gates(here);
                    helper.assertTrue(open.size() == 1, "the soul come down found " + open.size() + " gates");
                    Senkaimon opened = gate[0] = open.get(0);
                    Vec3 foot = here.gates().foot(UraharaGates.SENKAIMON);
                    long ends = level.getGameTime() + BleachConfig.get(BleachConfig.SENKAIMON_OPEN_SECONDS) * 20L;
                    helper.assertTrue(opened.position().distanceTo(foot) < SAME && opened.facing() == here.gates().facing()
                            && opened.leadsTo() == BleachWorlds.SOUL_SOCIETY && opened.endsAt() == ends
                            && opened.trialFor() == null, "his gate stands at " + opened.position() + " facing "
                            + opened.facing() + " to " + opened.leadsTo() + " until " + opened.endsAt() + ", not at "
                            + foot + " facing " + here.gates().facing() + " to Soul Society until " + ends);
                    helper.assertTrue(asker.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == today(level)
                            && asker.getData(SoulAttachments.SHOP_GARGANTA_DAY) == ShinigamiProgress.NEVER,
                            "the soul's day was not spent as its gate opened, or its Garganta's was");
                    int lane = opened.lane();
                    ServerLevel dangai = DangaiTests.holdLane(helper, lane, 0);
                    ChunkPos start = new ChunkPos(lane, 0);
                    room.held().atEnd(() -> {
                        TravelTests.discard(Kototsu.inLane(dangai, lane).toArray(new Entity[0]));
                        dangai.getChunkSource().removeRegionTicket(TicketType.FORCED, start, 2, start);
                    });
                    for (ServerPlayer soul : List.of(asker, shinigami, human)) {
                        soul.teleportTo(level, opened.getX(), opened.getY(), opened.getZ(), 0.0F, 0.0F);
                    }
                })
                // In from the gate's first tick, the next, each passes on its 21st inside.
                .thenExecuteAfter(15, () -> helper.assertTrue(Stream.of(asker, shinigami, human)
                        .allMatch(soul -> soul.level() == level), "a soul passed his gate within 15 ticks"))
                .thenExecuteAfter(15, () -> {
                    KisukeUrahara keeper = built[0].keeper();
                    helper.assertTrue(shinigami.level() == soulSociety
                            && shinigami.position().distanceTo(TravelLogic.COURT) < SAME
                            && last(keeper, shinigami).equals("standing")
                            && shinigami.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == ShinigamiProgress.NEVER,
                            "the guided Shinigami is at " + TravelTests.where(shinigami) + ", told " + told(keeper, shinigami)
                                    + ", not at the court and refused beside the open gate");
                    helper.assertTrue(inLane(asker, gate[0]) && inLane(human, gate[0]), "the soul is at "
                            + TravelTests.where(asker) + " and the Human at " + TravelTests.where(human)
                            + ", not at the start of lane " + gate[0].lane() + " crossing to Soul Society");
                })
                .thenSucceed();
    }

    /**
     * Test 2. An aware Human asks for his Garganta and comes down: the Keikaigi's posts stand, a quiet Garganta opens
     * between them to Hueco Mundo for its time, and its far end on the ground 4 blocks further out than the Human will land
     * for ten minutes, its chunk ticking by his ticket alone. Through the first the Human lands on its own column, and
     * through the far end it comes home to the floor it left, where the first, still open, does not take it again.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 900)
    public static void hisGargantaTakesAHumanToHuecoMundoAndItsFarEndBringsItHome(GameTestHelper helper) {
        UraharaRoomTests.Room room = UraharaRoomTests.roomAt(helper, 580, 420, UraharaRoomTests.LIFT, true);
        ServerLevel level = room.level();
        ServerLevel hueco = level.getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        // No test waits ten minutes: the ticket's own timeout is what lets the far end's chunk go.
        helper.assertTrue(hueco != null && UraharaGates.WAY_BACK.timeout() == 12_000L, "the test server has no Hueco"
                + " Mundo, or the far end's ticket times out after " + UraharaGates.WAY_BACK.timeout() + " ticks");
        ServerPlayer human = joined(helper, awareHuman(helper));
        Built[] built = new Built[1];
        GargantaRift[] rifts = new GargantaRift[2];
        // Where the Human will land in Hueco Mundo, and the ground 4 blocks further out.
        Vec3[] landed = new Vec3[2];
        BlockPos[] inside = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> UraharaShopTests.entitiesLoaded(helper, level, room.box()))
                .thenExecute(() -> {
                    Built here = built[0] = build(helper, room);
                    Vec3 foot = here.gates().foot(UraharaGates.GARGANTA);
                    landed[0] = TravelEvents.huecoMundoLanding(human, hueco, foot.x, foot.z);
                    standAt(human, here, COUNTER);
                    here.keeper().perform(human, NpcAction.GARGANTA);
                    helper.assertTrue(landed[0] != null && told(here.keeper(), human).equals(List.of("garganta.shop_aware",
                            "garganta.way")), "nowhere in Hueco Mundo holds the Human, or it was told "
                            + told(here.keeper(), human));
                    standAt(human, here, FLOOR);
                })
                .thenExecuteAfter(1, () -> {
                    Built here = built[0];
                    List<GargantaRift> near = tears(here);
                    helper.assertTrue(near.size() == 1, "the Human come down found " + near.size() + " Garganta");
                    GargantaRift tear = rifts[0] = near.get(0);
                    helper.assertTrue(tear.position().distanceTo(here.gates().foot(UraharaGates.GARGANTA)) < SAME
                            && tear.size() == 1.0F && tear.leadsTo() == BleachWorlds.HUECO_MUNDO && tear.isQuiet()
                            && tear.hold() == TravelLogic.gargantaOpenTicks() - GargantaRift.END_AGE
                            && here.keeper().getUUID().equals(tear.opener()), "his Garganta stands at " + tear.position()
                            + ", size " + tear.size() + ", to " + tear.leadsTo() + ", held " + tear.hold());
                    for (BoundingBox post : UraharaGates.POSTS) {
                        BlockPos.betweenClosedStream(here.gates().box(post)).forEach(pos -> helper.assertTrue(
                                level.getBlockState(pos).is(Blocks.DARK_OAK_LOG), "no post at " + pos));
                    }
                    helper.assertTrue(human.getData(SoulAttachments.SHOP_GARGANTA_DAY) == today(level)
                            && human.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == ShinigamiProgress.NEVER,
                            "the Garganta did not spend its own day alone");
                    // Its ground 4 blocks further out than the landing, and the chunks round it made by no ticket of
                    // the test's, so only his can make the far end tick.
                    double further = 1.0 + 4.0 / Math.hypot(landed[0].x, landed[0].z);
                    landed[1] = TravelEvents.groundUnder(human, hueco, Mth.floor(landed[0].x * further),
                            Mth.floor(landed[0].z * further));
                    helper.assertTrue(landed[1] != null, "no ground 4 blocks further out than the Human lands");
                    ChunkPos ahead = new ChunkPos(BlockPos.containing(landed[1]));
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            hueco.getChunk(ahead.x + x, ahead.z + z);
                        }
                    }
                })
                // A tear takes no soul that stood in it at its first tick, so the Human steps in only after that tick.
                .thenWaitUntil(() -> {
                    hueco.getChunkSource().pollTask();
                    List<GargantaRift> far = farEnds(built[0].keeper());
                    helper.assertTrue(far.size() == 1 && hueco.isPositionEntityTicking(far.get(0).blockPosition())
                            && rifts[0].tickCount > 0, "Hueco Mundo lists " + far.size() + " far ends, not one ticking,"
                            + " or his Garganta has not ticked");
                    rifts[1] = far.get(0);
                })
                .thenExecute(() -> {
                    GargantaRift home = rifts[1], tear = rifts[0];
                    helper.assertTrue(home.position().distanceTo(landed[1]) < SAME && home.leadsTo() == Level.OVERWORLD
                            && home.size() == 1.0F && home.isQuiet()
                            && home.hold() == UraharaGates.WAY_BACK_TICKS - GargantaRift.END_AGE, "the far end stands at "
                            + home.position() + " to " + home.leadsTo() + ", held " + home.hold() + ", not at " + landed[1]);
                    human.teleportTo(level, tear.getX(), tear.getY(), tear.getZ(), 0.0F, 0.0F);
                    inside[0] = human.blockPosition();
                })
                // Inside from now, it passes on its 11th tick there; its wait after the pass runs out by the 25th.
                .thenExecuteAfter(8, () -> helper.assertTrue(human.level() == level,
                        "the Human passed his Garganta within 8 ticks"))
                .thenExecuteAfter(17, () -> {
                    Optional<GlobalPos> left = human.getData(SoulAttachments.TRAVEL).humanWorldReturn();
                    helper.assertTrue(human.level() == hueco && human.position().distanceTo(landed[0]) < SAME
                            && !human.isChangingDimension() && human.getPortalCooldown() == 0
                            && left.equals(Optional.of(GlobalPos.of(Level.OVERWORLD, inside[0]))), "the Human is at "
                            + TravelTests.where(human) + ", not " + landed[0] + ", remembers leaving " + left
                            + ", or still waits " + human.getPortalCooldown() + "; told " + TravelTests.said(human));
                    // Hueco Mundo ticks its creatures while a player is there, so the far end has had its first tick.
                    helper.assertTrue(rifts[1].tickCount > 0, "the far end has not ticked");
                    human.teleportTo(hueco, rifts[1].getX(), rifts[1].getY(), rifts[1].getZ(), 0.0F, 0.0F);
                })
                .thenExecuteAfter(8, () -> helper.assertTrue(human.level() == hueco,
                        "the far end took the Human within 8 ticks"))
                .thenExecuteAfter(18, () -> helper.assertTrue(human.level() == level
                        && human.position().distanceTo(Vec3.atBottomCenterOf(inside[0])) < SAME,
                        "the far end brought the Human to " + TravelTests.where(human) + ", not to " + inside[0]))
                // The first stands open round it, and the wait it renews each tick keeps it from passing again.
                .thenExecuteAfter(25, () -> helper.assertTrue(human.level() == level && !rifts[0].isRemoved()
                        && !rifts[1].isRemoved(), "the Human was taken again, to " + TravelTests.where(human)
                        + ", or a tear closed early"))
                .thenSucceed();
    }

    /**
     * Test 3. A student has his Senkaimon once a day: asked again it is told so, nothing opens as it comes down, his button
     * is greyed, while his Garganta, another soul or another day is not held to it. A block in the gate's cells
     * refuses an ask, dropping the one before, and one set after an ask keeps the gate shut and the day unspent.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 900)
    public static void eachSoulHasHisSenkaimonOnceADayAndNoneIntoABlock(GameTestHelper helper) {
        UraharaRoomTests.Room room = UraharaRoomTests.roomAt(helper, 620, 420, UraharaRoomTests.LIFT, true);
        ServerLevel level = room.level();
        ServerPlayer student = joined(helper, ShinigamiTests.student(40.0F));
        ServerPlayer other = joined(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        Built[] built = new Built[1];
        // The day is read where it is compared, since a day can turn while the room's chunks load.
        long[] yesterday = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> UraharaShopTests.entitiesLoaded(helper, level, room.box()))
                .thenExecute(() -> {
                    Built here = built[0] = build(helper, room);
                    standAt(student, here, COUNTER);
                    here.keeper().perform(student, NpcAction.SENKAIMON);
                    standAt(student, here, FLOOR);
                })
                .thenExecuteAfter(1, () -> {
                    Built here = built[0];
                    List<Senkaimon> open = gates(here);
                    helper.assertTrue(open.size() == 1 && student.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == today(level),
                            "the student's Senkaimon did not open, or its day was not spent");
                    TravelTests.discard(open.toArray(new Entity[0]));
                    standAt(student, here, COUNTER);
                    here.keeper().perform(student, NpcAction.SENKAIMON);
                    helper.assertTrue(last(here.keeper(), student).equals("spent"), "asked again, the student was told "
                            + told(here.keeper(), student));
                    standAt(student, here, FLOOR);
                })
                .thenExecuteAfter(1, () -> {
                    Built here = built[0];
                    KisukeUrahara keeper = here.keeper();
                    helper.assertTrue(gates(here).isEmpty(), "a gate opened for a spent day");
                    standAt(student, here, COUNTER);
                    helper.assertTrue(keeper.interact(student, InteractionHand.MAIN_HAND).consumesAction(),
                            "he would not talk to the student");
                    List<OpenDialoguePayload> shown = TestPlayers.dialogues(student);
                    List<NpcAction> greyed = shown.get(shown.size() - 1).unready();
                    helper.assertTrue(greyed.equals(List.of(NpcAction.SENKAIMON)), "his dialogue greyed " + greyed);
                    keeper.perform(student, NpcAction.GARGANTA);
                    helper.assertTrue(last(keeper, student).equals("garganta.way"), "its Senkaimon had, the student's"
                            + " Garganta was refused: " + told(keeper, student));
                    standAt(other, here, BESIDE);
                    keeper.perform(other, NpcAction.SENKAIMON);
                    helper.assertTrue(told(keeper, other).equals(List.of("senkaimon.shop_soul", "senkaimon.way")),
                            "another soul was told " + told(keeper, other));
                    yesterday[0] = today(level) - 1;
                    student.setData(SoulAttachments.SHOP_SENKAIMON_DAY, yesterday[0]);
                    keeper.perform(student, NpcAction.SENKAIMON);
                    helper.assertTrue(last(keeper, student).equals("senkaimon.way"), "on another day the student was told "
                            + told(keeper, student));
                    level.setBlockAndUpdate(here.gates().at(GATE_CELL), Blocks.STONE.defaultBlockState());
                    keeper.perform(student, NpcAction.SENKAIMON);
                    helper.assertTrue(last(keeper, student).equals("blocked"), "with stone in the gate the student was told "
                            + told(keeper, student));
                    standAt(other, here, FLOOR);
                })
                .thenExecuteAfter(1, () -> {
                    Built here = built[0];
                    helper.assertTrue(gates(here).isEmpty() && last(here.keeper(), other).equals("blocked")
                            && other.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == ShinigamiProgress.NEVER,
                            "blocked after its ask, the soul was told " + told(here.keeper(), other)
                                    + " and a gate opened, or its day was spent");
                    level.setBlockAndUpdate(here.gates().at(GATE_CELL), Blocks.AIR.defaultBlockState());
                    standAt(student, here, FLOOR);
                })
                // The refused ask dropped the student's taken one, so the cleared floor opens nothing for it.
                .thenExecuteAfter(1, () -> helper.assertTrue(gates(built[0]).isEmpty()
                        && student.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == yesterday[0],
                        "a gate opened for the ask before a refused one"))
                .thenSucceed();
    }

    /**
     * Test 4. A Kisuke set down on the empty floor finds no room under him and says so; a gate asked outside its views gets
     * not a word; and a soul that has had that gate today, or whose Hollow holds its body, is told so first.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void heRefusesWithNoRoomUnderHimOrOutsideAView(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        KisukeUrahara keeper = helper.spawn(ModEntities.KISUKE_URAHARA.get(), new Vec3(1.5, 1.0, 1.5));
        try {
            helper.assertTrue(UraharaGates.roomUnder(keeper) == null, "a room was found under the empty floor");
            ServerPlayer asker = joined(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
            Vec3 near = helper.absoluteVec(new Vec3(3.5, 1.0, 1.5));
            asker.teleportTo(level, near.x, near.y, near.z, 0.0F, 0.0F);
            keeper.perform(asker, NpcAction.SENKAIMON);
            helper.assertTrue(told(keeper, asker).equals(List.of("no_room")), "with no room he said "
                    + told(keeper, asker));
            // Outside his Senkaimon's views: an aware Human and a Plus, whom his Garganta answers, a Hollow, an Arrancar
            // and a stranger, whom neither does.
            for (SoulData outside : List.of(awareHuman(helper), ShinigamiTests.soul(SoulState.PLUS, 40.0F),
                    ShinigamiTests.soul(SoulState.HOLLOW, 40.0F), ShinigamiTests.soul(SoulState.ARRANCAR, 40.0F),
                    ShinigamiTests.soul(SoulState.HUMAN, 1.0F))) {
                asker.setData(SoulAttachments.SOUL, outside);
                keeper.perform(asker, NpcAction.SENKAIMON);
                keeper.perform(asker, NpcAction.GARGANTA);
            }
            asker.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
            helper.assertTrue(told(keeper, asker).equals(List.of("no_room", "no_room", "no_room")),
                    "asked in and outside his gates' views, he said " + told(keeper, asker));
            asker.setData(SoulAttachments.SHOP_GARGANTA_DAY, today(level));
            keeper.perform(asker, NpcAction.GARGANTA);
            helper.assertTrue(last(keeper, asker).equals("spent"), "a spent Garganta was told " + told(keeper, asker));
            SoulData seized = ShinigamiTests.soul(SoulState.SHINIGAMI, 900.0F);
            VisoredLogic.setRecord(seized, VisoredLogic.record(seized).withRootedAt(0L));
            asker.setData(SoulAttachments.SOUL, seized);
            helper.assertTrue(VisoredEvents.startTakeover(asker, seized, false, now), "the rooted Hollow did not seize");
            keeper.perform(asker, NpcAction.GARGANTA);
            helper.assertTrue(last(keeper, asker).equals("taken"), "a seized body was told " + told(keeper, asker));
        } finally {
            keeper.discard();
        }
        helper.succeed();
    }

    /**
     * Test 5. He opens no Garganta into stone in its cells, a creature between the posts or stone in a post's place,
     * though dark oak there will do, and a soul's later ask takes its earlier one's place. Beside his open Senkaimon he
     * opens no other, though his Garganta may stand with it; and stone set in a post's place while the soul comes down
     * keeps the tear shut and the day unspent.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 900)
    public static void heOpensNothingIntoABlockACreatureOrAnotherGate(GameTestHelper helper) {
        UraharaRoomTests.Room room = UraharaRoomTests.roomAt(helper, 660, 420, UraharaRoomTests.LIFT, true);
        ServerLevel level = room.level();
        ServerPlayer first = joined(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F));
        ServerPlayer second = joined(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
        ServerPlayer third = joined(helper, ShinigamiTests.student(40.0F));
        Built[] built = new Built[1];
        helper.startSequence()
                .thenWaitUntil(() -> UraharaShopTests.entitiesLoaded(helper, level, room.box()))
                .thenExecute(() -> {
                    Built here = built[0] = build(helper, room);
                    KisukeUrahara keeper = here.keeper();
                    standAt(first, here, COUNTER);
                    standAt(second, here, BESIDE);
                    standAt(third, here, ASIDE);
                    BlockPos cell = here.gates().at(TEAR_CELL), post = here.gates().at(POST_TOP);
                    level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
                    keeper.perform(first, NpcAction.GARGANTA);
                    level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
                    Pig pig = EntityType.PIG.create(level);
                    Vec3 foot = here.gates().foot(UraharaGates.GARGANTA);
                    pig.moveTo(foot.x, foot.y, foot.z, 0.0F, 0.0F);
                    room.held().add(pig);
                    keeper.perform(first, NpcAction.GARGANTA);
                    pig.discard();
                    level.setBlockAndUpdate(post, Blocks.STONE.defaultBlockState());
                    keeper.perform(first, NpcAction.GARGANTA);
                    level.setBlockAndUpdate(post, Blocks.DARK_OAK_LOG.defaultBlockState());
                    keeper.perform(first, NpcAction.GARGANTA);
                    helper.assertTrue(told(keeper, first).equals(List.of("blocked", "blocked", "blocked",
                            "garganta.shop_shinigami", "garganta.way")), "stone in the tear, a pig in it, then stone and"
                            + " dark oak in a post were told " + told(keeper, first));
                    keeper.perform(first, NpcAction.SENKAIMON);
                    standAt(first, here, FLOOR);
                })
                .thenExecuteAfter(1, () -> {
                    Built here = built[0];
                    KisukeUrahara keeper = here.keeper();
                    helper.assertTrue(gates(here).size() == 1 && tears(here).isEmpty()
                            && first.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == today(level)
                            && first.getData(SoulAttachments.SHOP_GARGANTA_DAY) == ShinigamiProgress.NEVER,
                            "the soul's later ask did not take its Garganta's place: " + gates(here) + ", " + tears(here));
                    keeper.perform(second, NpcAction.SENKAIMON);
                    keeper.perform(second, NpcAction.GARGANTA);
                    helper.assertTrue(told(keeper, second).equals(List.of("standing", "garganta.shop_soul", "garganta.way")),
                            "beside his open Senkaimon the soul was told " + told(keeper, second));
                    keeper.perform(third, NpcAction.GARGANTA);
                    level.setBlockAndUpdate(here.gates().at(POST_TOP), Blocks.STONE.defaultBlockState());
                    standAt(third, here, FLOOR);
                })
                .thenExecuteAfter(1, () -> helper.assertTrue(tears(built[0]).isEmpty()
                        && told(built[0].keeper(), third).equals(List.of("garganta.shop_student", "garganta.way", "blocked"))
                        && third.getData(SoulAttachments.SHOP_GARGANTA_DAY) == ShinigamiProgress.NEVER,
                        "stone set in a post after its ask, the student was told " + told(built[0].keeper(), third)
                                + " and a Garganta opened, or its day was spent"))
                .thenSucceed();
    }

    // ---- Helpers ----

    /**
     * Builds this laid-out shop and its room as worldgen does, finds the shop's keeper and the room he finds under him,
     * and has every gate in the room, and every far end of his with its ticket, taken away when the test ends.
     */
    private static Built build(GameTestHelper helper, UraharaRoomTests.Room room) {
        ServerLevel level = room.level();
        UraharaShopTests.placeEach(level, room.shop());
        List<String> warned = new ArrayList<>();
        helper.assertTrue(new UraharaRoom.Stamp(room.start(), new UraharaShop.Mark(), () -> true, warned::add)
                .ensureBuilt(level), "the room was not built under the shop: " + warned);
        List<KisukeUrahara> keepers = level.getEntitiesOfClass(KisukeUrahara.class,
                AABB.of(UraharaShopTests.box(room.shop())));
        helper.assertTrue(keepers.size() == 1, "the shop holds " + keepers);
        UraharaGates.Room gates = UraharaGates.roomUnder(keepers.get(0));
        helper.assertTrue(gates != null && gates.corner().equals(room.at(BlockPos.ZERO))
                && gates.turn() == room.site().rotation(), "the keeper finds " + gates + " under him, not the room"
                + " turned " + room.site().rotation() + " at " + room.at(BlockPos.ZERO));
        room.held().atEnd(() -> TravelTests.discard(level.getEntitiesOfClass(Entity.class, AABB.of(room.box()),
                gate -> gate instanceof Senkaimon || gate instanceof GargantaRift).toArray(new Entity[0])));
        room.held().atEnd(() -> farEnds(keepers.get(0)).forEach(far -> {
            ChunkPos held = new ChunkPos(far.blockPosition());
            ((ServerLevel) far.level()).getChunkSource().removeRegionTicket(UraharaGates.WAY_BACK, held,
                    UraharaGates.WAY_BACK_RADIUS, held);
            far.discard();
        }));
        return new Built(room, keepers.get(0), gates);
    }

    /** A soul joined in survival on the test's floor with this soul; it leaves when the test ends. */
    private static ServerPlayer joined(GameTestHelper helper, SoulData soul) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, soul);
        return player;
    }

    /** A living Human aware of spirits who met a wounded Shinigami just now, so none comes down by it in the test. */
    private static SoulData awareHuman(GameTestHelper helper) {
        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 400.0F);
        human.setShinigami(human.shinigami().withWoundedMetAt(helper.getLevel().getGameTime()));
        return human;
    }

    /** The mod's day in this world, as his gate counts it. */
    private static long today(Level level) {
        return KidoLogic.lessonDay(level.getGameTime(), level.getDayTime());
    }

    /** Sets this soul down at this place of the shop's, from its corner before the turn. */
    private static void standAt(ServerPlayer soul, Built built, BlockPos place) {
        Vec3 at = Vec3.atBottomCenterOf(built.gates().at(place));
        soul.teleportTo(built.room().level(), at.x, at.y, at.z, 0.0F, 0.0F);
    }

    /** What follows his own key in each line of his this joined soul has been told in chat, oldest first. */
    private static List<String> told(KisukeUrahara keeper, ServerPlayer soul) {
        String his = "message.bleach." + keeper.dialogueKey() + ".";
        return TestPlayers.chat(soul).stream().map(line -> line.getContents() instanceof TranslatableContents words
                ? words.getKey() : "").filter(key -> key.startsWith(his)).map(key -> key.substring(his.length())).toList();
    }

    /** The last of his lines to this soul, as {@link #told} gives it, or "" if none. */
    private static String last(KisukeUrahara keeper, ServerPlayer soul) {
        List<String> told = told(keeper, soul);
        return told.isEmpty() ? "" : told.get(told.size() - 1);
    }

    /** Whether this soul stands at the start of this gate's Dangai lane, crossing it to Soul Society. */
    private static boolean inLane(ServerPlayer soul, Senkaimon gate) {
        Vec3 start = DangaiPlan.entry(gate.lane());
        TravelRecord.Crossing crossing = soul.getData(SoulAttachments.TRAVEL).crossing().orElse(null);
        return soul.level().dimension() == BleachWorlds.DANGAI && crossing != null
                && Math.hypot(soul.getX() - start.x, soul.getZ() - start.z) < 0.5
                && crossing.to() == BleachWorlds.SOUL_SOCIETY && crossing.lane() == gate.lane();
    }

    /** His Senkaimon standing in the built room. */
    private static List<Senkaimon> gates(Built built) {
        return built.room().level().getEntitiesOfClass(Senkaimon.class, AABB.of(built.room().box()));
    }

    /** His Garganta standing in the built room. */
    private static List<GargantaRift> tears(Built built) {
        return built.room().level().getEntitiesOfClass(GargantaRift.class, AABB.of(built.room().box()));
    }

    /** The Garganta he has opened in Hueco Mundo: his far ends. */
    private static List<GargantaRift> farEnds(KisukeUrahara keeper) {
        ServerLevel hueco = keeper.getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        return hueco == null ? List.of() : List.copyOf(hueco.getEntities(ModEntities.GARGANTA.get(),
                rift -> keeper.getUUID().equals(rift.opener())));
    }

    /** A built shop and room: the test's room, his keeper and the room he finds under him. */
    private record Built(UraharaRoomTests.Room room, KisukeUrahara keeper, UraharaGates.Room gates) {
    }
}
