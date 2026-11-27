package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The slow road to Bankai ({@link SlowRoad}): its hours, counted on the soul's own tick and shown on the soul screen.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SlowRoadTests {
    private static final long DAY = Level.TICKS_PER_DAY;
    private static final String TIME = "screen.bleach.soul.shikai_time";

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
}
