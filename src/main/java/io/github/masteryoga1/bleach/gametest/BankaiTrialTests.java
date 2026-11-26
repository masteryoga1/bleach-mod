package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.BankaiTrial;
import io.github.masteryoga1.bleach.soul.Materialisation;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Bankai trial, a bout with one's own zanpakutō spirit. Each test asks only about its own wielders and
 * spirits, and every bout ends within the tick it began in, so none outlives its test: on a failure, every soul the
 * test began or may have begun a bout for is cleared, the refused ones too. So the server stop's {@code breakOffAll},
 * which T2 calls by hand and which ends every bout there is, finds only its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankaiTrialTests {
    /** Where the tests stand their wielders on the field's floor, as a test sees it: a corner, and the middle. */
    static final Vec3 FLOOR = new Vec3(1.5, 2.0, 1.5), MIDDLE = new Vec3(8.5, 2.0, 8.5);
    static final String BEGUN = "message.bleach.bankai.trial.begun", BROKEN = "message.bleach.bankai.trial.broken_off";

    private BankaiTrialTests() {
    }

    /** Begins this wielder's bout now, or fails saying why not. */
    static BankaiTrial.Trial begun(GameTestHelper helper, Player wielder, long now) {
        BankaiTrial.Start start = BankaiTrial.begin(wielder, now);
        BankaiTrial.Trial trial = BankaiTrial.trial(wielder.getUUID());
        helper.assertTrue(start == BankaiTrial.Start.STARTED && trial != null, "the bout did not begin: " + start);
        return trial;
    }

    static BankaiRecord record(Player wielder) {
        return SoulLogic.get(wielder).zanpakuto().bankai();
    }

    /** Whether this wielder's bout is over, its spirit gone, with nothing written but the try it began at now. */
    static boolean over(Player wielder, MaterialisedSpirit spirit, long now) {
        BankaiRecord record = record(wielder);
        return BankaiTrial.trial(wielder.getUUID()) == null && BankaiSpiritTests.gone(wielder, spirit)
                && record.tries() == 1 && record.lastTryAt() == now && !record.achieved();
    }

    /** On Peaceful no bout begins and nothing is counted: true, once that is checked, when the test is done. */
    static boolean refusedOnPeaceful(GameTestHelper helper, Player wielder, long now) {
        if (!HollowPlayerTests.peaceful(helper)) {
            return false;
        }
        HihioZabimaruTests.clearingOnFailure(() -> clear(wielder), () -> {
            BankaiTrial.Start start = BankaiTrial.begin(wielder, now);
            helper.assertTrue(start == BankaiTrial.Start.PEACEFUL && record(wielder).tries() == 0,
                    "a bout began on Peaceful, or counted its try: " + start);
        });
        helper.succeed();
        return true;
    }

    /** Sends home any spirit these wielders have out, which ends any bout of theirs, so none outlives its test. */
    static void clear(Player... wielders) {
        for (Player wielder : wielders) {
            MaterialisedSpirit spirit = Materialisation.of(wielder.getUUID());
            if (spirit != null) {
                Materialisation.sendHome(spirit);
            }
        }
    }

    /**
     * T1. A bout begins for a named Shinigami where it stands: the spirit it had out goes home for a fresh, whole one
     * that may fight it alone, the try is counted, and the soul is told its time. On Peaceful the spirit out stays out
     * and nothing is counted. A second bout and a second call are refused while it lasts; reaching Bankai another way
     * breaks it off, and so do creative mode and spectating, the soul told so and trying again at once, counted twice.
     * The dead, the creative, one past Bankai and the call's own refusals (a Human, a nameless soul) are refused with
     * nothing counted.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aTrialBeginsCountsItsTryAndFightsItsWielder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.wielder(helper, MIDDLE);
        SoulData data = SoulLogic.get(ichigo);
        if (refusedOnPeaceful(helper, ichigo, now)) {
            return;
        }
        ServerPlayer joined = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true);
        BankaiSpiritTests.stand(helper, joined, FLOOR);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, data.copy());
        Player dead = BankaiSpiritTests.wielder(helper, FLOOR);
        dead.setHealth(0.0F);
        Player reached = BankaiSpiritTests.wielder(helper, FLOOR);
        BankaiLogic.reach(SoulLogic.get(reached), Component.literal("Tensa Zangetsu"), now);
        Map<Player, BankaiTrial.Start> refused = Map.of(dead, BankaiTrial.Start.DEAD, creative,
                BankaiTrial.Start.WATCHING, reached, BankaiTrial.Start.REACHED,
                ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.HUMAN, 500.0F)), BankaiTrial.Start.NO_POWER,
                ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F)), BankaiTrial.Start.NAMELESS);
        HihioZabimaruTests.clearingOnFailure(() -> {
            clear(ichigo, joined);
            refused.keySet().forEach(BankaiTrialTests::clear);
        }, () -> {
            refused.forEach((soul, reason) -> {
                BankaiTrial.Start start = BankaiTrial.begin(soul, now);
                helper.assertTrue(start == reason && BankaiTrial.trial(soul.getUUID()) == null
                                && !Materialisation.holds(soul.getUUID()) && record(soul).tries() == 0,
                        "a soul to be refused " + reason + " came to " + start + ", or its try was counted");
            });

            MaterialisedSpirit free = BankaiSpiritTests.drawOut(helper, ichigo);
            // Peaceful within this one call, so no tick sees it.
            MinecraftServer server = level.getServer();
            Difficulty difficulty = server.getWorldData().getDifficulty();
            BankaiTrial.Start calm;
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                calm = BankaiTrial.begin(ichigo, now);
            } finally {
                server.setDifficulty(difficulty, true);
            }
            helper.assertTrue(calm == BankaiTrial.Start.PEACEFUL && Materialisation.of(ichigo.getUUID()) == free
                    && record(ichigo).tries() == 0, "on Peaceful a bout came to " + calm + ", or the spirit out went");
            free.hurt(level.damageSources().playerAttack(ichigo), 5.0F);
            BankaiTrial.Trial trial = begun(helper, ichigo, now);
            MaterialisedSpirit spirit = trial.spirit();
            helper.assertTrue(free.isRemoved() && spirit != free && Materialisation.of(ichigo.getUUID()) == spirit
                            && spirit.wielder() == ichigo && spirit.getHealth() == spirit.getMaxHealth()
                            && spirit.canAttack(ichigo) && !spirit.canAttack(joined),
                    "the spirit out did not go home for a fresh, whole one that fights its wielder alone");
            helper.assertTrue(record(ichigo).tries() == 1 && record(ichigo).lastTryAt() == now
                            && trial.centre().equals(ichigo.position()) && trial.startedAt() == now
                            && BankaiTrial.boutSeconds() == 300 && trial.endsAt() == now + 300 * 20L,
                    "the bout began as " + record(ichigo) + ", from " + trial.centre() + " until " + trial.endsAt());
            BankaiTrial.Start again = BankaiTrial.begin(ichigo, now);
            Materialisation.Call call = Materialisation.callOut(ichigo);
            helper.assertTrue(again == BankaiTrial.Start.ALREADY && call == Materialisation.Call.ALREADY
                            && Materialisation.of(ichigo.getUUID()) == spirit && record(ichigo).tries() == 1,
                    "in a bout, a second came to " + again + " and a call to " + call);
            BankaiLogic.reach(data, Component.literal("Tensa Zangetsu"), now);
            BankaiTrial.tick(ichigo, data, now);
            helper.assertTrue(BankaiTrial.trial(ichigo.getUUID()) == null && BankaiSpiritTests.gone(ichigo, spirit)
                    && record(ichigo).tries() == 1, "reaching Bankai another way left the bout going");

            BankaiSpiritTests.drawOut(helper, joined);
            GameType[] modes = {GameType.CREATIVE, GameType.SPECTATOR};
            for (int i = 0; i < modes.length; i++) {
                joined.setGameMode(GameType.SURVIVAL);
                MaterialisedSpirit its = begun(helper, joined, now + i).spirit();
                joined.setGameMode(modes[i]);
                BankaiTrial.tick(joined, SoulLogic.get(joined), now + i);
                helper.assertTrue(BankaiTrial.trial(joined.getUUID()) == null && BankaiSpiritTests.gone(joined, its),
                        modes[i] + " did not break the bout off");
            }
            BankaiRecord tried = record(joined);
            helper.assertTrue(tried.tries() == 2 && tried.lastTryAt() == now + 1L && !tried.achieved()
                            && TestPlayers.chat(joined).stream().anyMatch(line -> GateTests.says(line, BEGUN, -1))
                            && BankaiSpiritTests.told(joined, BROKEN)
                            && !BankaiSpiritTests.told(joined, "message.bleach.spirit.home"),
                    "the tries came to " + tried + ", or the soul was not told its time and its break-offs alone");
        });
        helper.succeed();
    }

    /**
     * T2. Every break-off ends a bout with nothing written but its try, and its spirit gone: its wielder's death and
     * logging out, each on its event; another player object of the same UUID, on that one's tick; losing the power, as
     * the spirit's own tick sends it home; another world, on its event; straying 40 blocks from where it began, on the
     * wielder's tick; the spirit unloaded with its chunk, or killed by {@code /kill}; and the time running out. The
     * server stop's {@code breakOffAll} ends two at once, called by hand: posting a stop would run every stop listener
     * on the live server, so of the bout's own listeners only the death's and the logout's are posted through the bus,
     * and the stop's wiring is read, not tested. A spirit carried into an unloaded chunk ends its bout too, left in the
     * world for the world to finish moving.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void everyBreakOffEndsTheTrialWithNothingWritten(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        if (refusedOnPeaceful(helper, BankaiSpiritTests.wielder(helper, MIDDLE), now)) {
            return;
        }
        Map<String, BiConsumer<Player, MaterialisedSpirit>> ends = new LinkedHashMap<>();
        ends.put("its death", (wielder, spirit) -> {
            // At no health, as a player is when its death is posted.
            wielder.setHealth(0.0F);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(wielder, level.damageSources().generic()));
        });
        ends.put("logging out", (wielder, spirit) -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(wielder)));
        ends.put("another body", (wielder, spirit) -> {
            Player twin = helper.makeMockPlayer(GameType.SURVIVAL);
            twin.setUUID(wielder.getUUID());
            twin.setPos(wielder.position());
            BankaiTrial.tick(twin, SoulLogic.get(wielder), now);
        });
        ends.put("losing the power", (wielder, spirit) -> {
            SoulData data = SoulLogic.get(wielder);
            data.setShinigami(data.shinigami().withPowerLentUntil(now + 1_000L));
            Materialisation.upkeep(spirit);
        });
        ends.put("another world", (wielder, spirit) -> NeoForge.EVENT_BUS.post(
                new PlayerEvent.PlayerChangedDimensionEvent(wielder, Level.OVERWORLD, Level.NETHER)));
        ends.put("straying", (wielder, spirit) -> {
            wielder.setPos(wielder.position().add(40.0, 0.0, 0.0));
            BankaiTrial.tick(wielder, SoulLogic.get(wielder), now);
        });
        ends.put("its unload", (wielder, spirit) -> {
            boolean tracked = level.getEntity(spirit.getUUID()) == spirit;
            spirit.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(tracked && level.getEntity(spirit.getUUID()) == null, "the spirit was not tracked, or stayed");
        });
        ends.put("/kill", (wielder, spirit) -> {
            spirit.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            Materialisation.upkeep(spirit);
        });
        ends.put("the time", (wielder, spirit) -> BankaiTrial.tick(wielder, SoulLogic.get(wielder),
                now + BankaiTrial.boutSeconds() * 20L));
        ends.forEach((end, cause) -> {
            Player wielder = BankaiSpiritTests.wielder(helper, MIDDLE);
            HihioZabimaruTests.clearingOnFailure(() -> clear(wielder), () -> {
                MaterialisedSpirit spirit = begun(helper, wielder, now).spirit();
                cause.accept(wielder, spirit);
                helper.assertTrue(over(wielder, spirit, now), "the bout outlasted " + end + ", or wrote " + record(wielder));
            });
        });

        Player[] pair = {BankaiSpiritTests.wielder(helper, MIDDLE), BankaiSpiritTests.wielder(helper, FLOOR)};
        HihioZabimaruTests.clearingOnFailure(() -> clear(pair), () -> {
            MaterialisedSpirit first = begun(helper, pair[0], now).spirit(), second = begun(helper, pair[1], now).spirit();
            BankaiTrial.breakOffAll();
            helper.assertTrue(over(pair[0], first, now) && over(pair[1], second, now), "breakOffAll left a bout going");
        });
        Player carried = BankaiSpiritTests.wielder(helper, MIDDLE);
        MaterialisedSpirit spirit = begun(helper, carried, now).spirit();
        try {
            spirit.setPos(spirit.getX() + 10_000.0, spirit.getY(), spirit.getZ());
            helper.assertTrue(BankaiTrial.trial(carried.getUUID()) == null && !Materialisation.holds(carried.getUUID())
                            && !spirit.isRemoved() && record(carried).tries() == 1,
                    "a spirit carried into an unloaded chunk left its bout going, or was sent home as it moved");
        } finally {
            Materialisation.sendHome(spirit);
        }
        helper.succeed();
    }
}
