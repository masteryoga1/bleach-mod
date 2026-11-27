package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.BankaiTrial;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.JinzenSession;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SlowRoad;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The slow road to Bankai ({@link SlowRoad}): its hours, counted on the soul's own tick and shown on the soul screen,
 * and the road in Jinzen. Each soul sits as its blade's use would have it, by {@code JinzenLogic.begin} and one tick at
 * {@code SETTLE_TICKS}, so every bout the road begins starts and ends in its test's first tick and no ring of its meets
 * another test's. Bouts run plain, with {@code spirit_tests} off, but where Zangetsu's blades are the point.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SlowRoadTests {
    private static final long DAY = Level.TICKS_PER_DAY;
    private static final String TIME = "screen.bleach.soul.shikai_time";
    private static final String HOURS = "jinzen.bleach.road.hours", WEAK = "jinzen.bleach.road.weak";
    private static final String RESTING = "jinzen.bleach.road.resting", HELD = "jinzen.bleach.road.held";

    private SlowRoadTests() {
    }

    /**
     * R1. The hours count only while the soul holds its own blade released in Shikai in its main hand, with its power
     * its own and Bankai not reached: Zabimaru's sealed blade, its power lent or its body seized count nothing;
     * released, its ticks are held back and written a second's worth at a time, and what is held back is written on the
     * first tick that does not count, so a seal loses nothing. Zangetsu's constant release counts though no upkeep runs
     * for it; nothing counts with the blade out of hand or in the off hand, with another soul's released blade in hand,
     * for a spectator or a student, or past Bankai. A joined soul's own ticks count, and stop when its blade leaves its
     * hand, all of them written. An hour is 72,000 ticks, and the soul screen shows the time in hours and minutes rounded
     * down, from a minute, to a soul that may hold Bankai and has not reached it. Kills: a write every tick or none on
     * stopping; counting a sealed, out-of-hand or off-hand blade, another's blade, lent or seized power, a spectator, a
     * student, or past Bankai; counting by the Shikai's upkeep; the count not hooked into the soul's tick; a wrong hour;
     * the row shown past Bankai, to a student or under a minute, or rounded up.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theHoursCountOnlyWhileTheShikaiIsReleased(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player renji = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = SoulLogic.get(renji);
        counted(helper, renji, now, 5, 0L, 0L, "sealed");
        BankaiReleaseTests.released(helper, renji, ShikaiEvents.Result.RELEASED, "Zabimaru's Shikai");
        counted(helper, renji, now, 25, 25L, 20L, "released");
        ShinigamiProgress progress = data.shinigami();
        data.setShinigami(progress.withPowerLentUntil(now + 1_200L));
        counted(helper, renji, now, 3, 25L, 25L, "lent");
        data.setShinigami(progress);
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now - DAY));
        helper.assertTrue(VisoredEvents.startTakeover(renji, data, false, now), "the rooted Hollow did not seize its host");
        counted(helper, renji, now, 3, 25L, 25L, "seized");
        VisoredEvents.endTakeover(renji, data, VisoredEvents.TakeoverEnd.REMOVED, now);
        counted(helper, renji, now, 2, 27L, 25L, "freed");
        helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED, "Zabimaru did not seal");
        counted(helper, renji, now, 4, 27L, 27L, "sealed again");

        Player ichigo = zangetsu(helper, GameType.SURVIVAL, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        counted(helper, ichigo, now, 3, 3L, 0L, "constant");
        ichigo.getInventory().setItem(3, ichigo.getMainHandItem());
        ichigo.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        counted(helper, ichigo, now, 2, 3L, 3L, "out of hand");
        ichigo.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.someoneElsesBlade(helper, ZanpakutoSpirits.ZANGETSU));
        TenshintaiTests.release(helper, ichigo);
        counted(helper, ichigo, now, 2, 3L, 3L, "another soul's released blade");
        ichigo.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ichigo.setItemInHand(InteractionHand.OFF_HAND, ichigo.getInventory().removeItemNoUpdate(3));
        counted(helper, ichigo, now, 2, 3L, 3L, "in the off hand");
        ichigo.setItemInHand(InteractionHand.MAIN_HAND, ichigo.getOffhandItem());
        ichigo.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        BankaiLogic.reach(SoulLogic.get(ichigo), Component.empty(), now);
        counted(helper, ichigo, now, 2, 3L, 3L, "past Bankai");
        counted(helper, zangetsu(helper, GameType.SPECTATOR, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F)), now, 2,
                0L, 0L, "a spectator");
        counted(helper, zangetsu(helper, GameType.SURVIVAL, ShinigamiTests.student(500.0F)), now, 2, 0L, 0L, "a student");

        // The soul screen's row, by literal ticks: hours and minutes of play rounded down, from a minute, on the road.
        helper.assertTrue(SlowRoad.HOUR_TICKS == 72_000L, "an hour of play is " + SlowRoad.HOUR_TICKS + " ticks");
        SoulData timed = timed(3L * 72_000L + 20L * 1_200L + 1_199L);
        reads(helper, timed, 3L, 20L, "3 h 20 min 59 s in Shikai");
        reads(helper, timed(1_200L), 0L, 1L, "a minute in Shikai");
        SoulData reached = timed(1_200L);
        BankaiLogic.reach(reached, Component.empty(), now);
        SoulData student = ShinigamiTests.student(500.0F);
        student.setZanpakuto(timed.zanpakuto());
        for (SoulData none : List.of(reached, student, timed(1_199L))) {
            helper.assertTrue(SlowRoad.screenLine(none) == null, "a soul past Bankai, a student or a soul under a minute"
                    + " in Shikai reads " + SlowRoad.screenLine(none));
        }
        helper.assertTrue(Language.getInstance().has(TIME), TIME + " has no words");

        ServerPlayer kon = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), true);
        SoulData konData = SoulLogic.get(kon);
        BankaiReleaseTests.released(helper, kon, ShikaiEvents.Result.RELEASED, "Zangetsu's Shikai");
        long[] held = new long[1];
        int[] from = new int[1];
        // The count is read and the blade put away in one step, or a tick of the soul's between them would move it.
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, SlowRoad.ticks(konData) >= 40L,
                        "the joined soul's Shikai has counted " + SlowRoad.ticks(konData) + " ticks, not 40 yet"))
                .thenExecute(() -> {
                    held[0] = SlowRoad.ticks(konData);
                    from[0] = kon.tickCount;
                    kon.getInventory().setItem(3, kon.getMainHandItem());
                    kon.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, kon.tickCount >= from[0] + 40,
                        "the joined soul has not ticked 40 times with its blade put away"))
                .thenExecute(() -> helper.assertTrue(SlowRoad.ticks(konData) == held[0]
                                && konData.zanpakuto().bankai().shikaiTicks() == held[0],
                        "with its blade put away at " + held[0] + " the soul counts " + SlowRoad.ticks(konData)
                                + ", " + konData.zanpakuto().bankai().shikaiTicks() + " of them written"))
                .thenSucceed();
    }

    /**
     * R2. The road opens only past its gates, read once as the soul settles: its hours, its reiryoku, a full bond, power
     * of the soul's own, a spirit with a Bankai in this world and Bankai not reached, each to the tick and the point, and
     * the hours asked first; a Substitute and a masked Visored walk it, a student does not. It asks 10 hours and 1,000 at
     * the defaults, and what its keys say. Short of the hours or the reiryoku the visit says what is left; closed, it
     * says nothing. A bout refused (no ground for Zangetsu's blades) opens the world and says why, in the trial command's
     * words, counting nothing. Past every gate the spirit steps out where its soul sits, as the command's bout, beside a
     * doll's window it leaves as it was, and the sitting ends. On Peaceful the visit says so. Kills: any gate dropped,
     * off by a tick or a point, or out of order; a key not read or a default changed; the doll's refusal words for the
     * trial's; a refused road closing the world; a road bout centred elsewhere, by the doll, barred or changed by a doll's
     * window, uncounted, or leaving the sitting on.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void theRoadOpensOnlyPastItsGates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        long hours = SlowRoad.hoursTicks();
        Player ichigo = sitter(helper, BankaiTrialTests.MIDDLE, hours);
        if (HollowPlayerTests.peaceful(helper)) {
            try {
                sit(ichigo, now);
                helper.assertTrue(heldFor(ichigo, BankaiTrial.Start.PEACEFUL) && opened(ichigo)
                        && BankaiTrialTests.record(ichigo).tries() == 0, "on Peaceful the road said " + roadLine(ichigo));
            } finally {
                clear(ichigo);
            }
            helper.succeed();
            return;
        }
        Player green = sitter(helper, BankaiTrialTests.FLOOR, hours - 1L);
        Player weak = sitter(helper, BankaiTrialTests.FLOOR, hours);
        SoulLogic.get(weak).setMaxReiryoku(999.0F);
        Player both = sitter(helper, BankaiTrialTests.FLOOR, hours - 1L);
        SoulLogic.get(both).setMaxReiryoku(999.0F);
        // The closed souls are short of the hours too, so the gate must find them closed before it counts the hours.
        Player loose = sitter(helper, BankaiTrialTests.FLOOR, hours - 1L);
        SoulLogic.get(loose).setZanpakuto(SoulLogic.get(loose).zanpakuto().withBond(ZanpakutoBond.FULL - 1));
        Player lent = sitter(helper, BankaiTrialTests.FLOOR, hours - 1L);
        SoulLogic.get(lent).setShinigami(SoulLogic.get(lent).shinigami().withPowerLentUntil(now + 1_200L));
        Player past = sitter(helper, BankaiTrialTests.FLOOR, hours - 1L);
        BankaiLogic.reach(SoulLogic.get(past), Component.empty(), now);
        Player perched = sitter(helper, new Vec3(3.5, 5.0, 8.5), hours);
        List<BlockPos> pillar = List.of(new BlockPos(3, 2, 8), new BlockPos(3, 3, 8), new BlockPos(3, 4, 8));
        Runnable clear = () -> {
            clear(ichigo, perched, green, weak, both, loose, lent, past);
            pillar.forEach(block -> helper.setBlock(block, Blocks.AIR));
        };
        HihioZabimaruTests.clearingOnFailure(clear, () -> {
            helper.assertTrue(hours == 720_000L && SlowRoad.minMaxReiryoku() == 1_000.0, "at the defaults the road asks "
                    + hours + " ticks in Shikai and " + SlowRoad.minMaxReiryoku() + " maximum reiryoku");
            sitsWithNoBout(helper, green, now, 0, HOURS, 0L, 1L);
            sitsWithNoBout(helper, weak, now, 0, WEAK, 1_000);
            sitsWithNoBout(helper, both, now, 0, HOURS, 0L, 1L);
            for (Player closed : List.of(loose, lent, past)) {
                sitsWithNoBout(helper, closed, now, 0, null);
            }
            // The gate alone, on souls given ichigo's bond: who may walk the road, and a spirit no file names closes it.
            SoulData visored = like(ShinigamiTests.substitute(1_000.0F), ichigo, hours);
            VisoredLogic.setRecord(visored, ShinigamiProgress.Visored.NONE.withRootedAt(now - DAY)
                    .withSubduedAt(now - 1L).withMaskEndsAt(now + 1_200L));
            SoulData substitute = like(ShinigamiTests.substitute(1_000.0F), ichigo, hours);
            SoulData student = like(ShinigamiTests.student(1_000.0F), ichigo, 0L);
            SoulData lost = like(ZanpakutoTests.soul(SoulState.SHINIGAMI, 1_000.0F), ichigo, hours);
            lost.setZanpakuto(lost.zanpakuto().withSpirit(Optional.of(BleachMod.id("no_such_spirit"))));
            SoulData tired = like(ZanpakutoTests.soul(SoulState.SHINIGAMI, 999.0F), ichigo, hours);
            tired.setZanpakuto(tired.zanpakuto().withBankai(tired.zanpakuto().bankai().tried(now - 1L)));
            List<SlowRoad.Gate> gates = List.of(SlowRoad.gate(level, substitute, now), SlowRoad.gate(level, visored, now),
                    SlowRoad.gate(level, student, now), SlowRoad.gate(level, lost, now), SlowRoad.gate(level, tired, now));
            helper.assertTrue(VisoredLogic.isMasked(visored) && gates.equals(List.of(SlowRoad.Gate.OPEN,
                            SlowRoad.Gate.OPEN, SlowRoad.Gate.OFF, SlowRoad.Gate.OFF, SlowRoad.Gate.WEAK)),
                    "the road is " + gates + " to a Substitute, a masked Visored, a student with no hours, a soul of no"
                            + " spirit's and a weak soul tried today");
            // An hour and 500 asked: the keys are read, and the gate moves with them to the tick and the point.
            KidoTests.withConfig(BleachConfig.BANKAI_ROAD_HOURS, 1, () -> KidoTests.withConfig(
                    BleachConfig.BANKAI_ROAD_MIN_MAX_REIRYOKU, 500.0, () -> {
                        SoulData under = like(ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), ichigo, 71_999L);
                        SoulData hour = like(ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), ichigo, 72_000L);
                        List<SlowRoad.Gate> asked = List.of(SlowRoad.gate(level, under, now),
                                SlowRoad.gate(level, hour, now));
                        helper.assertTrue(asked.equals(List.of(SlowRoad.Gate.HOURS, SlowRoad.Gate.OPEN)),
                                "with an hour and 500 asked, 71,999 and 72,000 ticks in Shikai gate " + asked);
                    }));
            KidoTests.withConfig(BleachConfig.BANKAI_TRIAL_SPIRIT_TESTS, true, () -> {
                pillar.forEach(block -> helper.setBlock(block, Blocks.STONE));
                sit(perched, now);
                helper.assertTrue(heldFor(perched, BankaiTrial.Start.NO_GROUND) && opened(perched)
                                && BankaiTrial.trial(perched.getUUID()) == null
                                && BankaiTrialTests.record(perched).tries() == 0,
                        "a soul with no ground for Zangetsu's blades was told " + roadLine(perched));
            });
            // A doll's window open on the record alone, with no doll standing: the road's bout is still the command's.
            SoulData data = SoulLogic.get(ichigo);
            GlobalPos doll = TenshintaiTests.place(helper, TenshintaiTests.DOLL);
            data.setZanpakuto(data.zanpakuto().withBankai(data.zanpakuto().bankai().dollAt(doll, now + DAY)));
            TenshintaiTests.plain(() -> sitsToABout(helper, ichigo, now, 1));
            BankaiRecord record = BankaiTrialTests.record(ichigo);
            helper.assertTrue(record.windowEndsAt() == now + DAY && record.doll().equals(Optional.of(doll)),
                    "the road's bout changed the doll's window on the record: " + record);
            for (String line : List.of(HOURS, WEAK, RESTING, HELD)) {
                helper.assertTrue(Language.getInstance().has(line), line + " has no words");
            }
            clear.run();
        });
        helper.succeed();
    }

    /**
     * R3. The spirit steps out once a game day at most, a day after the last bout's start by any road: a tick short of a
     * day the visit says how long is left; a day on, the bout begins, and a second sitting then waits a whole day. A bout
     * by command counts as one by the road; a last bout written after now holds nothing back; a soul short of its hours
     * is told so before the day. On Peaceful no bout begins. Kills: the day dropped, or off by a tick; the day read from
     * another field, or asked before the hours; the road's or the command's try not counted against it; a try after now
     * locking the road.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void theSpiritStepsOutOnceAGameDay(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player ichigo = sitter(helper, BankaiTrialTests.MIDDLE, SlowRoad.hoursTicks());
        if (BankaiTrialTests.refusedOnPeaceful(helper, ichigo, now)) {
            return;
        }
        Player commanded = sitter(helper, BankaiTrialTests.FLOOR, SlowRoad.hoursTicks());
        Player tired = sitter(helper, BankaiTrialTests.FLOOR, SlowRoad.hoursTicks() - 1L);
        tried(tired, now - 1L);
        Runnable clear = () -> clear(ichigo, commanded, tired);
        HihioZabimaruTests.clearingOnFailure(clear, () -> TenshintaiTests.plain(() -> {
            tried(ichigo, now - DAY + 1L);
            sitsWithNoBout(helper, ichigo, now, 1, RESTING, JinzenLogic.duration(1L));
            tried(ichigo, now - DAY);
            sitsToABout(helper, ichigo, now, 3);
            BankaiTrialTests.clear(ichigo);
            sitsWithNoBout(helper, ichigo, now, 3, RESTING, JinzenLogic.duration(DAY));
            sitsWithNoBout(helper, tired, now, 1, HOURS, 0L, 1L);
            BankaiTrialTests.begun(helper, commanded, now);
            BankaiTrialTests.clear(commanded);
            sitsWithNoBout(helper, commanded, now, 1, RESTING, JinzenLogic.duration(DAY));
            tried(commanded, now + 100L);
            SlowRoad.Gate ahead = SlowRoad.gate(helper.getLevel(), SoulLogic.get(commanded), now);
            helper.assertTrue(ahead == SlowRoad.Gate.OPEN, "a bout written after now holds the road back: " + ahead);
            clear.run();
        }));
        helper.succeed();
    }

    /**
     * R4. The inner Hollow comes first: a soul past the road's gates whose Hollow is ready fights it, with no bout begun;
     * one whose Hollow's fight is refused (in creative mode) has its world open with the road left untried; one whose
     * Hollow is rooted but not ready yet takes the road. On Peaceful neither comes out and the world opens. Kills: the
     * road asked before the Hollow; the road tried after the Hollow's fight is refused; the road barred to any soul with a
     * Hollow rooted.
     */
    @GameTest(template = "field")
    public static void theInnerHollowComesFirst(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = BankaiSpiritTests.host(helper);
        host.setShiftKeyDown(true);
        host.setOnGround(true);
        SoulData data = pass(SoulLogic.get(host), SlowRoad.hoursTicks());
        BankaiSpiritTests.ready(data, now);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, data.copy());
        BankaiSpiritTests.stand(helper, creative, BankaiTrialTests.FAR);
        creative.setShiftKeyDown(true);
        creative.setOnGround(true);
        Player early = sitter(helper, BankaiTrialTests.FLOOR, SlowRoad.hoursTicks());
        SoulData earlyData = SoulLogic.get(early);
        VisoredLogic.setRecord(earlyData, VisoredLogic.record(earlyData).withRootedAt(now - DAY).withDuelReadyAt(now + 100L));
        Runnable clear = () -> {
            BankaiSpiritTests.end(now, host);
            clear(host, creative, early);
        };
        HihioZabimaruTests.clearingOnFailure(clear, () -> {
            sit(host, now);
            if (HollowPlayerTests.peaceful(helper)) {
                helper.assertTrue(!VisoredEvents.isDuelling(host) && BankaiTrial.trial(host.getUUID()) == null
                        && opened(host), "on Peaceful a Hollow or the spirit came out, or the world did not open");
                clear.run();
                return;
            }
            helper.assertTrue(VisoredEvents.isDuelling(host) && BankaiTrial.trial(host.getUUID()) == null
                            && BankaiTrialTests.record(host).tries() == 0 && data.jinzen() == null,
                    "a soul whose Hollow was ready did not fight it alone");
            BankaiSpiritTests.end(now, host);
            sit(creative, now);
            helper.assertTrue(!VisoredEvents.isDuelling(creative) && opened(creative) && roadLine(creative) == null,
                    "after the Hollow's fight was refused the road was tried: " + roadLine(creative));
            TenshintaiTests.plain(() -> sitsToABout(helper, early, now, 1));
            clear.run();
        });
        helper.succeed();
    }

    /** A mock in this game mode with this soul, which knows Zangetsu's name for the blade in its main hand, released. */
    private static Player zangetsu(GameTestHelper helper, GameType mode, SoulData soul) {
        Player player = helper.makeMockPlayer(mode);
        player.setData(SoulAttachments.SOUL, soul);
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        ZanpakutoEvents.learnName(player, soul, ZanpakutoSpirits.ZANGETSU.location(),
                ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), player.getMainHandItem());
        TenshintaiTests.release(helper, player);
        return player;
    }

    /** A Shinigami who knows its zanpakutō's name, with these ticks in Shikai written on its record. */
    private static SoulData timed(long ticks) {
        SoulData data = ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F);
        data.setZanpakuto(ZanpakutoBond.NONE.asNamed(Component.literal("Zangetsu"), true)
                .withBankai(BankaiRecord.NONE.trained(ticks)));
        return data;
    }

    /** Asserts that this soul's screen row reads these hours and minutes in Shikai. */
    private static void reads(GameTestHelper helper, SoulData soul, long hours, long minutes, String what) {
        Component line = SlowRoad.screenLine(soul);
        helper.assertTrue(line != null && line.getContents() instanceof TranslatableContents words
                && words.getKey().equals(TIME) && Arrays.equals(words.getArgs(), new Object[] {hours, minutes}),
                what + " reads " + line);
    }

    /** Counts this many of the soul's ticks, and asserts its ticks on the road then, and how many of them are written. */
    private static void counted(GameTestHelper helper, Player soul, long now, int times, long ticks, long written,
            String what) {
        SoulData data = SoulLogic.get(soul);
        for (int i = 0; i < times; i++) {
            SlowRoad.count(soul, data, now);
        }
        helper.assertTrue(SlowRoad.ticks(data) == ticks && data.zanpakuto().bankai().shikaiTicks() == written,
                what + ": " + SlowRoad.ticks(data) + " ticks counted, " + data.zanpakuto().bankai().shikaiTicks()
                        + " written, not " + ticks + " and " + written);
    }

    /** A Zangetsu soul sneaking on the ground here, its bond full, these ticks in Shikai and the road's least reiryoku. */
    private static Player sitter(GameTestHelper helper, Vec3 at, long ticks) {
        Player soul = BankaiSpiritTests.wielder(helper, at);
        soul.setShiftKeyDown(true);
        soul.setOnGround(true);
        pass(SoulLogic.get(soul), ticks);
        return soul;
    }

    /** This named soul's bond full, these ticks in Shikai on a road with no bout yet, and the road's least reiryoku. */
    private static SoulData pass(SoulData data, long ticks) {
        data.setZanpakuto(data.zanpakuto().withBond(ZanpakutoBond.FULL).withBankai(BankaiRecord.NONE.trained(ticks)));
        data.setMaxReiryoku((float) SlowRoad.minMaxReiryoku());
        return data;
    }

    /** This soul given this sitter's bond, full and Zangetsu's, with these ticks in Shikai and no bout yet. */
    private static SoulData like(SoulData data, Player sitter, long ticks) {
        data.setZanpakuto(SoulLogic.get(sitter).zanpakuto().withBankai(BankaiRecord.NONE.trained(ticks)));
        return data;
    }

    /** Ends these souls' bouts and sittings, and lets go any doll's window on their records, so none outlives its test. */
    private static void clear(Player... souls) {
        TenshintaiTests.clear(List.of(souls));
        for (Player soul : souls) {
            JinzenLogic.end(soul, SoulLogic.get(soul));
        }
    }

    /** A bout of this soul's written as begun then, as any road writes one. */
    private static void tried(Player soul, long at) {
        SoulData data = SoulLogic.get(soul);
        data.setZanpakuto(data.zanpakuto().withBankai(data.zanpakuto().bankai().tried(at)));
    }

    /** The soul settles in Jinzen now, as three still seconds with its blade would have it. */
    private static void sit(Player soul, long now) {
        SoulData data = SoulLogic.get(soul);
        JinzenLogic.begin(soul, data, now);
        JinzenLogic.tick(soul, data, soul.getMainHandItem(), JinzenLogic.SETTLE_TICKS, now);
    }

    private static boolean opened(Player soul) {
        JinzenSession session = SoulLogic.get(soul).jinzen();
        return session != null && session.opened();
    }

    /** The visit's last line if it is the road's, or null: no visit, or none of the road's. */
    @Nullable
    private static TranslatableContents roadLine(Player soul) {
        JinzenSession session = SoulLogic.get(soul).jinzen();
        if (session == null || session.page() == null || session.page().lines().isEmpty()) {
            return null;
        }
        List<Component> lines = session.page().lines();
        return lines.get(lines.size() - 1).getContents() instanceof TranslatableContents words
                && List.of(HOURS, WEAK, RESTING, HELD).contains(words.getKey()) ? words : null;
    }

    /** Whether the visit ends on the road held back for this reason, said by its key as the trial's command says it. */
    private static boolean heldFor(Player soul, BankaiTrial.Start start) {
        TranslatableContents line = roadLine(soul);
        return line != null && line.getKey().equals(HELD) && line.getArgs().length == 1
                && line.getArgs()[0] instanceof Component said
                && said.getContents() instanceof TranslatableContents refused && refused.getArgs().length == 2
                && refused.getArgs()[1] instanceof Component why && why.getContents() instanceof TranslatableContents reason
                && reason.getKey().equals(start.key());
    }

    /** The soul sits: the world opens, no bout begins, its tries stay as they were, and its visit ends on this line. */
    private static void sitsWithNoBout(GameTestHelper helper, Player soul, long now, int tries, @Nullable String key,
            Object... args) {
        sit(soul, now);
        TranslatableContents line = roadLine(soul);
        boolean said = key == null ? line == null
                : line != null && line.getKey().equals(key) && Arrays.equals(line.getArgs(), args);
        helper.assertTrue(said && opened(soul) && BankaiTrial.trial(soul.getUUID()) == null
                        && BankaiTrialTests.record(soul).tries() == tries,
                "a soul to be told " + key + " " + Arrays.toString(args) + " was told " + line + ", or a bout began");
    }

    /** The soul sits and its bout begins where it sits, the sitting over, counted now as its {@code tries}th. */
    private static void sitsToABout(GameTestHelper helper, Player soul, long now, int tries) {
        Vec3 at = soul.position();
        sit(soul, now);
        BankaiTrial.Trial trial = BankaiTrial.trial(soul.getUUID());
        BankaiRecord record = BankaiTrialTests.record(soul);
        helper.assertTrue(trial != null && !trial.byDoll() && trial.centre().equals(at)
                        && SoulLogic.get(soul).jinzen() == null && record.tries() == tries && record.lastTryAt() == now,
                "the road's bout did not begin where its soul sat, or did not end the sitting: " + record);
    }
}
