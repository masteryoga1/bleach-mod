package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.ArrancarRoster;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Arrancar ranks: the rank the soul's record saves and how old saves read, the Números given in
 * birth order and taken back from the longest away, the Espada seats dealt by strength, the seats given up by
 * staying away or leaving, the seats pinned by command and the roster's save, the record kept right from the
 * roster and the Gran Rey Cero it gates, and the number tattoo's spot and look. Every roster test makes its own
 * {@link ArrancarRoster}, so none touches the server's, which every test in the run shares. Mock players are plain
 * {@link Player}s that are not in the world and never tick, so the record is kept through
 * {@link ArrancarRoster#keep(Player, SoulData, ArrancarRoster, long)} with that fresh roster, and no test can see a
 * milestone or a chat line, which only a real player gets. Times passed in stand for later ticks, and a setting
 * changed for a test is put back before the test's tick ends.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ArrancarRankTests {
    /** Where a player stands in the {@code empty} template. */
    private static final Vec3 ROOM = new Vec3(1.5, 1.0, 1.5);

    private ArrancarRankTests() {
    }

    // ---- Helpers ----

    /**
     * Writes this seat into the soul's record with the first Número, as the roster would for an Espada (1 to 10)
     * or a Privaron Espada (100 to 109). For the Gran Rey Cero tests, whose mocks never reach a roster.
     */
    static void seat(SoulData data, int seat) {
        giveRank(data, new ArrancarRank(ArrancarRank.FIRST_NUMERO, seat));
    }

    /** Writes this rank into the soul's record. */
    private static void giveRank(SoulData data, ArrancarRank rank) {
        HollowProgress hollow = data.hollow();
        data.setHollow(hollow.withArrancar(hollow.arrancar().withRank(rank)));
    }

    /** The n-th soul's id. All share the high half, so the ids order by n. */
    private static UUID id(int n) {
        return new UUID(0x5A00L, n);
    }

    /** Notes the n-th soul on the roster, with no number of its own to ask for. */
    private static void note(ArrancarRoster roster, int n, float strength, long now) {
        roster.note(id(n), "soul" + n, 0, strength, now);
    }

    private static ArrancarRank rank(ArrancarRoster roster, int n, long now) {
        return roster.rank(id(n), now);
    }

    /** This soul's line of the roster's list, or null when it is not on it. */
    @Nullable
    private static ArrancarRoster.Entry entry(List<ArrancarRoster.Entry> entries, UUID id) {
        for (ArrancarRoster.Entry entry : entries) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Whether this text is translated from this key with these arguments: a component argument equal to the one
     * given, any other the same written out.
     */
    private static boolean keyed(Component text, String key, Object... args) {
        if (!(text.getContents() instanceof TranslatableContents contents) || !contents.getKey().equals(key)
                || contents.getArgs().length != args.length) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = contents.getArgs()[i];
            boolean same = args[i] instanceof Component
                    ? args[i].equals(arg) : String.valueOf(args[i]).equals(String.valueOf(arg));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static void assertRank(GameTestHelper helper, ArrancarRank rank, ArrancarRank.Kind kind, int number,
                                   String what) {
        helper.assertTrue(rank.kind() == kind && rank.number() == number,
                what + " is " + rank + " (" + rank.kind() + " " + rank.number() + "), not " + kind + " " + number);
    }

    // ---- The record ----

    @GameTest(template = "empty")
    public static void rankRecordSavesAndOldSavesLoad(GameTestHelper helper) {
        HollowProgress.Arrancar record =
                new HollowProgress.Arrancar(true, 2, 1234L, 5678L, 3, 9000L, new ArrancarRank(12, 4));
        HollowProgress progress = HollowProgress.NONE.withArrancar(record);
        Tag saved = HollowProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow();
        HollowProgress loaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(progress), "the record with its rank came back as " + loaded);
        ByteBuf buffer = Unpooled.buffer();
        try {
            HollowProgress.STREAM_CODEC.encode(buffer, progress);
            HollowProgress received = HollowProgress.STREAM_CODEC.decode(buffer);
            helper.assertTrue(received.equals(progress), "the record came through the network as " + received);
            helper.assertTrue(buffer.readableBytes() == 0, "the record left " + buffer.readableBytes() + " bytes");
        } finally {
            buffer.release();
        }

        // A save from before the ranks has no rank, and a bad one falls back on its own: the rest is kept.
        helper.assertTrue(saved instanceof CompoundTag tag && tag.getCompound("arrancar").contains("rank"),
                "the record was saved without its rank: " + saved);
        HollowProgress none = progress.withArrancar(record.withRank(ArrancarRank.NONE));
        CompoundTag old = ((CompoundTag) saved).copy();
        old.getCompound("arrancar").remove("rank");
        HollowProgress oldLoaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(oldLoaded.equals(none), "a save from before the ranks read as " + oldLoaded);
        CompoundTag broken = ((CompoundTag) saved).copy();
        broken.getCompound("arrancar").putString("rank", "x");
        HollowProgress brokenLoaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
        helper.assertTrue(brokenLoaded.equals(none), "a record whose rank is a string read as " + brokenLoaded);
        CompoundTag wild = ((CompoundTag) saved).copy();
        CompoundTag outOfRange = new CompoundTag();
        outOfRange.putInt("numero", 5);
        outOfRange.putInt("seat", 11);
        wild.getCompound("arrancar").put("rank", outOfRange);
        HollowProgress wildLoaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, wild).getOrThrow();
        helper.assertTrue(wildLoaded.equals(none), "a Número 5 in seat 11 read as " + wildLoaded.arrancar().rank());
        CompoundTag privaron = ((CompoundTag) saved).copy();
        CompoundTag lost = new CompoundTag();
        lost.putInt("numero", 12);
        lost.putInt("seat", 104);
        privaron.getCompound("arrancar").put("rank", lost);
        ArrancarRank privaronRank = HollowProgress.CODEC.parse(NbtOps.INSTANCE, privaron).getOrThrow().arrancar().rank();
        helper.assertTrue(privaronRank.equals(new ArrancarRank(12, 104)) && privaronRank.kind() == ArrancarRank.Kind.PRIVARON,
                "a Privaron Espada 104 read as " + privaronRank + ", " + privaronRank.kind());

        // The older records have no rank, and every change to the record keeps it.
        HollowProgress.Arrancar sixParts = new HollowProgress.Arrancar(true, 2, 1234L, 5678L, 3, 9000L);
        HollowProgress.Arrancar fiveParts = new HollowProgress.Arrancar(true, 2, 1234L, 5678L, 3);
        helper.assertTrue(sixParts.rank().equals(ArrancarRank.NONE) && fiveParts.rank().equals(ArrancarRank.NONE),
                "the six- and five-part records have the ranks " + sixParts.rank() + " and " + fiveParts.rank());
        ArrancarRank cuatro = record.rank();
        for (HollowProgress.Arrancar changed : List.of(record.withFailures(5), record.withLastTearAt(1L),
                record.withReleaseReadyAt(2L), record.withForging(7), record.withGranReyCeroReadyAt(3L),
                record.becoming(false))) {
            helper.assertTrue(changed.rank().equals(cuatro), "a change to the record lost its rank: " + changed);
        }

        assertRank(helper, ArrancarRank.NONE, ArrancarRank.Kind.NONE, 0, "no rank");
        assertRank(helper, new ArrancarRank(11, 0), ArrancarRank.Kind.NUMERO, 11, "Número 11");
        assertRank(helper, new ArrancarRank(11, 4), ArrancarRank.Kind.ESPADA, 4, "the Cuatro Espada");
        assertRank(helper, new ArrancarRank(11, 103), ArrancarRank.Kind.PRIVARON, 103, "Privaron Espada 103");
        int[][] privaronNumbers = {{3, 103}, {10, 100}, {1, 101}, {0, 0}, {11, 0}};
        for (int[] pair : privaronNumbers) {
            helper.assertTrue(ArrancarRank.privaronNumber(pair[0]) == pair[1],
                    "seat " + pair[0] + " lost makes Privaron Espada " + ArrancarRank.privaronNumber(pair[0]));
        }

        Component numero = new ArrancarRank(11, 0).label();
        helper.assertTrue(keyed(numero, "rank.bleach.numero", 11), "Número 11 is labelled " + numero);
        Component espada = new ArrancarRank(11, 4).label();
        helper.assertTrue(keyed(espada, "rank.bleach.espada", 4, Component.translatable("rank.bleach.seat.4")),
                "the Cuatro Espada is labelled " + espada);
        Component fallen = new ArrancarRank(11, 103).label();
        helper.assertTrue(keyed(fallen, "rank.bleach.privaron", 103), "Privaron Espada 103 is labelled " + fallen);
        Component unnumbered = ArrancarRank.NONE.label();
        helper.assertTrue(keyed(unnumbered, "rank.bleach.unnumbered"), "an unnumbered Arrancar is labelled " + unnumbered);

        for (String name : List.of("espada", "arrancar_uniform")) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id(name)) != null,
                    "advancement bleach:" + name + " did not load");
        }
        helper.succeed();
    }

    // ---- The Números ----

    @GameTest(template = "empty")
    public static void numerosFollowBirthOrder(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ArrancarRoster roster = new ArrancarRoster();
        for (int n = 1; n <= 3; n++) {
            note(roster, n, 100.0F, now);
            helper.assertTrue(rank(roster, n, now).numero() == 10 + n,
                    "soul " + n + " was born Número " + rank(roster, n, now).numero());
        }
        helper.assertTrue(roster.remove(id(2)), "soul 2 was not on the roster to take off");
        note(roster, 4, 100.0F, now);
        helper.assertTrue(rank(roster, 4, now).numero() == 14, "soul 4 was given " + rank(roster, 4, now).numero()
                + ", not 14: a number is not given again until the count comes round");
        // A number the soul's record asks for is kept while it is free, and does not move the count on.
        roster.note(id(5), "soul5", 12, 100.0F, now);
        helper.assertTrue(rank(roster, 5, now).numero() == 12,
                "soul 5 asked for the free 12 and got " + rank(roster, 5, now));
        roster.note(id(6), "soul6", 11, 100.0F, now);
        helper.assertTrue(rank(roster, 6, now).numero() == 15,
                "soul 6 asked for the held 11 and got " + rank(roster, 6, now));

        Set<Integer> held = new HashSet<>();
        for (int n : new int[] {1, 3, 4, 5, 6}) {
            held.add(rank(roster, n, now).numero());
        }
        for (int n = 100; n <= 183; n++) {
            note(roster, n, 100.0F, now);
            int numero = rank(roster, n, now).numero();
            helper.assertTrue(numero == 16 + (n - 100) && held.add(numero), "soul " + n + " was given " + numero);
        }
        helper.assertTrue(held.size() == 89, held.size() + " Números are held, not all 89");
        note(roster, 200, 100.0F, now);
        ArrancarRank unnumbered = rank(roster, 200, now);
        helper.assertTrue(unnumbered.numero() == 0 && unnumbered.kind() == ArrancarRank.Kind.NONE
                && keyed(unnumbered.label(), "rank.bleach.unnumbered"),
                "with every Número held, soul 200 is " + unnumbered + ", labelled " + unnumbered.label());

        // Soul 1 is seen again a minute on, so souls 4 and 5 are the longest away, last seen in the same tick.
        note(roster, 1, 100.0F, now + ArrancarRoster.SEEN_REFRESH_TICKS);

        // Set for these calls, in this one tick, and put back before any other test runs.
        int hours = BleachConfig.get(BleachConfig.RANK_ABSENT_HOURS);
        BleachConfig.RANK_ABSENT_HOURS.set(1);
        try {
            long later = now + ArrancarRoster.SEEN_REFRESH_TICKS + ArrancarRoster.TICKS_PER_HOUR + 1L;
            note(roster, 3, 100.0F, later);
            note(roster, 6, 100.0F, later);
            for (int n = 100; n <= 183; n++) {
                note(roster, n, 100.0F, later);
            }
            List<ArrancarRoster.Entry> entries = roster.entries(later);
            for (int n : new int[] {1, 4, 5}) {
                ArrancarRoster.Entry away = entry(entries, id(n));
                helper.assertTrue(away != null && away.away(), "soul " + n + ", unseen for an hour, is listed as " + away);
            }
            ArrancarRoster.Entry seen = entry(entries, id(3));
            helper.assertTrue(seen != null && !seen.away(), "soul 3, seen just now, is listed as " + seen);

            // Every Número held: the longest away gives its number up first, and between equal sights the lowest
            // goes. Soul 5 holds 12 though soul 4 (14) came first, so going by the order they came in would differ,
            // and so would going by the number alone, which would take soul 1's 11 first.
            note(roster, 200, 100.0F, later);
            note(roster, 201, 100.0F, later);
            note(roster, 202, 100.0F, later);
            int[][] given = {{200, 12}, {201, 14}, {202, 11}, {1, 0}, {4, 0}, {5, 0}, {3, 13}};
            for (int[] pair : given) {
                int numero = rank(roster, pair[0], later).numero();
                helper.assertTrue(numero == pair[1], "soul " + pair[0] + " is Número " + numero + ", not " + pair[1]);
            }
        } finally {
            BleachConfig.RANK_ABSENT_HOURS.set(hours);
        }
        helper.succeed();
    }

    // ---- The Espada ----

    @GameTest(template = "empty")
    public static void espadaSeatsGoToTheStrongest(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ArrancarRoster roster = new ArrancarRoster();
        // Souls 1 to 11 at 1500 to 2500, numbered 11 to 21; soul 12 just under the line.
        for (int n = 1; n <= 11; n++) {
            note(roster, n, 1_500.0F + 100.0F * (n - 1), now);
        }
        note(roster, 12, 1_499.0F, now);
        for (int n = 2; n <= 11; n++) {
            assertRank(helper, rank(roster, n, now), ArrancarRank.Kind.ESPADA, 12 - n, "soul " + n);
        }
        assertRank(helper, rank(roster, 1, now), ArrancarRank.Kind.NUMERO, 11, "soul 1, the eleventh strongest");
        assertRank(helper, rank(roster, 12, now), ArrancarRank.Kind.NUMERO, 22, "soul 12, below the line");

        // As strong as the Primera: the elder Número keeps the seat, and the Diez falls out.
        note(roster, 13, 2_500.0F, now);
        assertRank(helper, rank(roster, 11, now), ArrancarRank.Kind.ESPADA, 1, "soul 11, the elder at 2500");
        assertRank(helper, rank(roster, 13, now), ArrancarRank.Kind.ESPADA, 2, "soul 13, the younger at 2500");
        assertRank(helper, rank(roster, 3, now), ArrancarRank.Kind.ESPADA, 10, "soul 3, at 1700");
        assertRank(helper, rank(roster, 2, now), ArrancarRank.Kind.PRIVARON, 100, "soul 2, the old Diez");

        // Set for these calls, in this one tick, and put back before any other test runs.
        boolean privaron = BleachConfig.get(BleachConfig.PRIVARON_ESPADA);
        BleachConfig.PRIVARON_ESPADA.set(false);
        try {
            assertRank(helper, rank(roster, 2, now), ArrancarRank.Kind.NUMERO, 12, "soul 2 with no Privaron Espada");
        } finally {
            BleachConfig.PRIVARON_ESPADA.set(privaron);
        }

        // Grown the strongest, it wins the Primera and forgets the seat it lost; the new Diez falls out in turn.
        note(roster, 2, 2_600.0F, now);
        assertRank(helper, rank(roster, 2, now), ArrancarRank.Kind.ESPADA, 1, "soul 2 at 2600");
        assertRank(helper, rank(roster, 3, now), ArrancarRank.Kind.PRIVARON, 100, "soul 3, the next Diez");

        // A tie again, on a roster of its own where the order they came in and their ids both favour the younger:
        // the first asks for Número 15, the second is given 11, and only the elder Número seats the second first.
        ArrancarRoster tied = new ArrancarRoster();
        tied.note(id(1), "first", 15, 2_000.0F, now);
        tied.note(id(2), "second", 0, 2_000.0F, now);
        assertRank(helper, tied.rank(id(2), now), ArrancarRank.Kind.ESPADA, 1, "Número 11 tied at 2000");
        assertRank(helper, tied.rank(id(1), now), ArrancarRank.Kind.ESPADA, 2, "Número 15 tied at 2000");

        double needs = BleachConfig.get(BleachConfig.ESPADA_MIN_MAX_REIRYOKU);
        BleachConfig.ESPADA_MIN_MAX_REIRYOKU.set(3_000.0);
        try {
            for (ArrancarRoster.Entry entry : roster.entries(now)) {
                helper.assertFalse(entry.rank().kind() == ArrancarRank.Kind.ESPADA,
                        entry.name() + " holds a seat below a line of 3000: " + entry.rank());
            }
            for (int n = 2; n <= 13; n++) {
                if (n != 12) {
                    helper.assertTrue(rank(roster, n, now).kind() == ArrancarRank.Kind.PRIVARON,
                            "soul " + n + ", a former Espada, is " + rank(roster, n, now));
                }
            }
            // The seat it lost last is the one it is numbered by: the Primera, not the Diez it lost before.
            assertRank(helper, rank(roster, 2, now), ArrancarRank.Kind.PRIVARON, 101, "soul 2 below a line of 3000");
            assertRank(helper, rank(roster, 4, now), ArrancarRank.Kind.PRIVARON, 100, "soul 4 below a line of 3000");
        } finally {
            BleachConfig.ESPADA_MIN_MAX_REIRYOKU.set(needs);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void absentAndLeavingSoulsGiveUpTheirSeats(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ArrancarRoster roster = new ArrancarRoster();
        note(roster, 1, 2_000.0F, now);
        note(roster, 2, 1_800.0F, now);
        assertRank(helper, rank(roster, 1, now), ArrancarRank.Kind.ESPADA, 1, "A at 2000");
        assertRank(helper, rank(roster, 2, now), ArrancarRank.Kind.ESPADA, 2, "B at 1800");

        // A week of the server running without A: B takes the Primera, and A keeps only the mark of it.
        long later = now + 168L * ArrancarRoster.TICKS_PER_HOUR + 1L;
        note(roster, 2, 1_800.0F, later);
        assertRank(helper, rank(roster, 2, later), ArrancarRank.Kind.ESPADA, 1, "B with A away a week");
        assertRank(helper, rank(roster, 1, later), ArrancarRank.Kind.PRIVARON, 101, "A away a week");
        ArrancarRoster.Entry away = entry(roster.entries(later), id(1));
        helper.assertTrue(away != null && away.away(), "A, away a week, is listed as " + away);

        // Back again and still the stronger, it wins the Primera back.
        note(roster, 1, 2_000.0F, later);
        assertRank(helper, rank(roster, 1, later), ArrancarRank.Kind.ESPADA, 1, "A back again");
        assertRank(helper, rank(roster, 2, later), ArrancarRank.Kind.ESPADA, 2, "B with A back");

        long muchLater = later + 1_000L * ArrancarRoster.TICKS_PER_HOUR;
        // Set for these calls, in this one tick, and put back before any other test runs.
        int hours = BleachConfig.get(BleachConfig.RANK_ABSENT_HOURS);
        BleachConfig.RANK_ABSENT_HOURS.set(0);
        try {
            note(roster, 2, 1_800.0F, muchLater);
            assertRank(helper, rank(roster, 1, muchLater), ArrancarRank.Kind.ESPADA, 1, "A away 1000 hours, kept forever");
        } finally {
            BleachConfig.RANK_ABSENT_HOURS.set(hours);
        }

        // Leaving the Arrancar state gives up the rank: a return is a new birth, with a new number.
        helper.assertTrue(roster.remove(id(1)), "A was not on the roster to take off");
        helper.assertTrue(rank(roster, 1, muchLater).equals(ArrancarRank.NONE),
                "A off the roster is " + rank(roster, 1, muchLater));
        assertRank(helper, rank(roster, 2, muchLater), ArrancarRank.Kind.ESPADA, 1, "B with A gone");
        note(roster, 1, 2_000.0F, muchLater);
        int reborn = rank(roster, 1, muchLater).numero();
        helper.assertTrue(reborn == 13,
                "A born again is Número " + reborn + ", not 13, the next after 12: its old 11 is not given again");

        // A pin ends with a long absence too, so a weak soul pinned by command is not seated again on its return.
        note(roster, 3, 100.0F, muchLater);
        note(roster, 5, 100.0F, muchLater);
        helper.assertTrue(roster.pin(id(3), 3) && roster.pin(id(5), 4), "the weak souls could not be pinned");
        assertRank(helper, rank(roster, 3, muchLater), ArrancarRank.Kind.ESPADA, 3, "C pinned as the Tercera");
        assertRank(helper, rank(roster, 5, muchLater), ArrancarRank.Kind.ESPADA, 4, "E pinned as the Cuatro");
        long weekOn = muchLater + 168L * ArrancarRoster.TICKS_PER_HOUR + 1L;
        // E comes back before anything deals the seats again, so its own return must end its pin.
        note(roster, 5, 100.0F, weekOn);
        assertRank(helper, rank(roster, 5, weekOn), ArrancarRank.Kind.PRIVARON, 104, "E back after a week away");
        assertRank(helper, rank(roster, 3, weekOn), ArrancarRank.Kind.PRIVARON, 103, "C still away after a week");
        for (int n : new int[] {3, 5}) {
            ArrancarRoster.Entry unpinned = entry(roster.entries(weekOn), id(n));
            helper.assertTrue(unpinned != null && !unpinned.pinned(), "soul " + n + ", away a week, is " + unpinned);
        }
        helper.succeed();
    }

    // ---- Pins and the save ----

    @GameTest(template = "empty")
    public static void pinnedSeatsAndTheRosterSave(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ArrancarRoster roster = new ArrancarRoster();
        // S1 to S3 (Números 11 to 13), the weak W (14), and later S4 (15).
        note(roster, 1, 3_000.0F, now);
        note(roster, 2, 2_900.0F, now);
        note(roster, 3, 2_800.0F, now);
        note(roster, 4, 100.0F, now);
        UUID weak = id(4);
        helper.assertTrue(roster.pin(weak, 4), "W could not be seated by command");
        assertRank(helper, roster.rank(weak, now), ArrancarRank.Kind.ESPADA, 4, "W pinned at 100 reiryoku");
        note(roster, 5, 2_700.0F, now);
        for (int n = 1; n <= 3; n++) {
            assertRank(helper, rank(roster, n, now), ArrancarRank.Kind.ESPADA, n, "S" + n);
        }
        assertRank(helper, rank(roster, 5, now), ArrancarRank.Kind.ESPADA, 5, "S4, around W's pinned seat");

        // A new pin on the same seat replaces the old one, and the one it unpins remembers the seat.
        helper.assertTrue(roster.pin(id(5), 4), "S4 could not be seated by command");
        assertRank(helper, rank(roster, 5, now), ArrancarRank.Kind.ESPADA, 4, "S4 pinned to the Cuatro");
        assertRank(helper, roster.rank(weak, now), ArrancarRank.Kind.PRIVARON, 104, "W unpinned");
        List<ArrancarRoster.Entry> entries = roster.entries(now);
        ArrancarRoster.Entry s4 = entry(entries, id(5));
        ArrancarRoster.Entry w = entry(entries, weak);
        helper.assertTrue(s4 != null && s4.pinned() && w != null && !w.pinned(),
                "the pins are listed as " + s4 + " and " + w);
        helper.assertFalse(roster.pin(id(999), 4), "a soul not on the roster was pinned");
        helper.assertFalse(roster.pin(weak, 11), "W was pinned to an eleventh seat");
        helper.assertTrue(roster.clear(weak), "W could not be cleared");
        assertRank(helper, roster.rank(weak, now), ArrancarRank.Kind.NUMERO, 14, "W cleared");

        // Saved and read back, it lists the same and numbers the next soul the same.
        CompoundTag saved = roster.save(new CompoundTag(), registries);
        CompoundTag hand = saved.copy();
        ArrancarRoster loaded = ArrancarRoster.load(saved, registries);
        List<ArrancarRoster.Entry> before = roster.entries(now);
        List<ArrancarRoster.Entry> after = loaded.entries(now);
        helper.assertTrue(after.equals(before), "the roster read back lists " + after + ", not " + before);
        note(roster, 6, 100.0F, now);
        note(loaded, 6, 100.0F, now);
        helper.assertTrue(rank(roster, 6, now).numero() == 16 && rank(loaded, 6, now).numero() == 16,
                "the next soul is Número " + rank(roster, 6, now).numero() + " on the roster and "
                        + rank(loaded, 6, now).numero() + " on the one read back");

        // A damaged file: an entry with no id, a second S1, a second Número 12, a second pin on the Cuatro, and a
        // Número and seat out of range.
        ListTag souls = hand.getList("souls", Tag.TAG_COMPOUND);
        CompoundTag nobody = new CompoundTag();
        nobody.putString("name", "nobody");
        nobody.putInt("numero", 30);
        nobody.putInt("strength", 5_000);
        souls.add(nobody);
        CompoundTag again = souls.getCompound(0).copy();
        again.putString("name", "again");
        again.putInt("strength", 1);
        souls.add(again);
        souls.add(saved(id(7), "twin", 12, 0, 0, now));
        souls.add(saved(id(8), "usurper", 0, 0, 4, now));
        souls.add(saved(id(9), "stray", 5, 50, 0, now));
        hand.put("souls", souls);
        List<ArrancarRoster.Entry> damaged = ArrancarRoster.load(hand, registries).entries(now);
        ArrancarRoster.Entry s1 = entry(damaged, id(1));
        ArrancarRoster.Entry twin = entry(damaged, id(7));
        ArrancarRoster.Entry usurper = entry(damaged, id(8));
        ArrancarRoster.Entry stray = entry(damaged, id(9));
        ArrancarRoster.Entry cuatro = entry(damaged, id(5));
        helper.assertTrue(damaged.size() == before.size() + 3, "the damaged file lists " + damaged);
        helper.assertTrue(s1 != null && s1.name().equals("soul1") && s1.strength() == 3_000,
                "the second S1 was read over the first: " + s1);
        helper.assertTrue(twin != null && twin.rank().numero() == 0, "a second Número 12 was read as " + twin);
        helper.assertTrue(usurper != null && !usurper.pinned(), "a second pin on the Cuatro was read as " + usurper);
        helper.assertTrue(cuatro != null && cuatro.pinned() && cuatro.rank().seat() == 4, "S4 was read back as " + cuatro);
        helper.assertTrue(stray != null && stray.rank().equals(ArrancarRank.NONE),
                "a Número 5 in seat 50 was read as " + stray);

        CompoundTag wrong = new CompoundTag();
        wrong.putString("souls", "x");
        helper.assertTrue(ArrancarRoster.load(wrong, registries).entries(now).isEmpty(),
                "a file whose souls are a string lists someone");
        helper.succeed();
    }

    /** One soul as the roster saves it, at full strength for no seat, seen now. */
    private static CompoundTag saved(UUID id, String name, int numero, int seat, int pin, long now) {
        CompoundTag soul = new CompoundTag();
        soul.put("id", NbtUtils.createUUID(id));
        soul.putString("name", name);
        soul.putInt("numero", numero);
        soul.putInt("strength", 100);
        soul.putLong("seen_at", now);
        soul.putInt("seat", seat);
        soul.putInt("lost_seat", 0);
        soul.putInt("pin", pin);
        return soul;
    }

    // ---- The record kept, and the Gran Rey Cero ----

    @GameTest(template = "empty")
    public static void keepWritesTheRecordAndGatesTheGranReyCero(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ArrancarRoster roster = new ArrancarRoster();
        Player strong = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, 1_600.0F, ROOM);
        SoulData data = SoulLogic.get(strong);
        Player weak = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 1_000.0F, ROOM);
        SoulData weakData = SoulLogic.get(weak);

        ArrancarRank primera = ArrancarRoster.keep(strong, data, roster, now);
        helper.assertTrue(primera.equals(new ArrancarRank(11, 1)) && data.hollow().arrancar().rank().equals(primera),
                "the strong Arrancar was kept as " + primera + ", its record saying " + data.hollow().arrancar().rank());
        helper.assertTrue(GranReyCero.eligible(data), "the Primera Espada has no Gran Rey Cero");
        GranReyCero.Refusal seated = GranReyCero.refusal(strong, data, now);
        helper.assertTrue(seated != GranReyCero.Refusal.LOCKED, "the Primera Espada's Gran Rey Cero is " + seated);

        ArrancarRank numero = ArrancarRoster.keep(weak, weakData, roster, now);
        helper.assertTrue(numero.equals(new ArrancarRank(12, 0)) && weakData.hollow().arrancar().rank().equals(numero),
                "the weak Arrancar was kept as " + numero);
        helper.assertFalse(GranReyCero.eligible(weakData), "Número 12 has the Gran Rey Cero");
        GranReyCero.Refusal locked = GranReyCero.refusal(weak, weakData, now);
        helper.assertTrue(locked == GranReyCero.Refusal.LOCKED, "Número 12's Gran Rey Cero is " + locked);
        Component message = GranReyCero.message(locked, weakData, now);
        helper.assertTrue(keyed(message, "message.bleach.gran_rey_cero.locked",
                Component.translatable("rank.bleach.numero", 12), "1500"), "the locked message is " + message);

        // Set for these calls, in this one tick, and put back before any other test runs.
        double needs = BleachConfig.get(BleachConfig.ESPADA_MIN_MAX_REIRYOKU);
        boolean privaronFires = BleachConfig.get(BleachConfig.GRAN_REY_CERO_PRIVARON_FIRES);
        BleachConfig.ESPADA_MIN_MAX_REIRYOKU.set(2_000.0);
        try {
            ArrancarRank fallen = ArrancarRoster.keep(strong, data, roster, now);
            assertRank(helper, fallen, ArrancarRank.Kind.PRIVARON, 101, "the strong Arrancar below a line of 2000");
            helper.assertFalse(GranReyCero.eligible(data), "a Privaron Espada has the Gran Rey Cero by default");
            BleachConfig.GRAN_REY_CERO_PRIVARON_FIRES.set(true);
            helper.assertTrue(GranReyCero.eligible(data), "a Privaron Espada lacks the Gran Rey Cero with privaron_fires");
        } finally {
            BleachConfig.ESPADA_MIN_MAX_REIRYOKU.set(needs);
            BleachConfig.GRAN_REY_CERO_PRIVARON_FIRES.set(privaronFires);
        }

        // No longer an Arrancar: off the roster, and the record's rank gone.
        SoulLogic.changeState(data, SoulState.HUMAN);
        ArrancarRank human = ArrancarRoster.keep(strong, data, roster, now);
        helper.assertTrue(human.equals(ArrancarRank.NONE) && data.hollow().arrancar().rank().equals(ArrancarRank.NONE),
                "the Arrancar made Human was kept as " + human + ", its record saying " + data.hollow().arrancar().rank());
        helper.assertTrue(entry(roster.entries(now), strong.getUUID()) == null, "the Human is still on the roster");

        // A mock is no real player: the server's roster is never touched, and the record is only read.
        ArrancarRank cached = ArrancarRoster.keep(weak, weakData, now);
        helper.assertTrue(cached.equals(new ArrancarRank(12, 0)) && weakData.hollow().arrancar().rank().equals(cached),
                "keeping a mock against the server's roster gave " + cached);
        List<ArrancarRoster.Entry> server = ArrancarRoster.get(helper.getLevel().getServer()).entries(now);
        helper.assertTrue(entry(server, strong.getUUID()) == null && entry(server, weak.getUUID()) == null,
                "a mock reached the server's roster: " + server);
        helper.succeed();
    }

    // ---- The tattoo ----

    @GameTest(template = "empty")
    public static void tattooSpotsMissTheHoleAndTheNumberTravels(GameTestHelper helper) {
        Random random = new Random(0x5617L);
        Set<ArrancarRank.Spot> seen = EnumSet.noneOf(ArrancarRank.Spot.class);
        for (int i = 0; i < 200; i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            ArrancarRank.Spot spot = ArrancarRank.tattooSpot(id);
            helper.assertTrue(spot == ArrancarRank.tattooSpot(id), "the tattoo of " + id + " moved");
            int place = ArrancarLogic.holePlace(id);
            helper.assertTrue(List.of(ArrancarRank.SPOTS_BY_HOLE[place]).contains(spot),
                    "the tattoo of " + id + " is " + spot + ", not allowed by a hole at " + place);
            if (spot.face != ArrancarRank.Face.LEFT_ARM_OUTER) {
                float[] rows = ArrancarRank.HOLE_ROWS[place];
                helper.assertTrue(spot.y1 < rows[0] || spot.y0 > rows[1],
                        "the tattoo of " + id + " at " + spot + " crosses a hole at " + place);
            }
            seen.add(spot);
        }
        helper.assertTrue(seen.size() == ArrancarRank.Spot.values().length, "only the spots " + seen + " were ever chosen");

        // The number rides in the Hollow look, clear of every older bit, and only an Arrancar's.
        SoulData espada = ShinigamiTests.soul(SoulState.ARRANCAR, 1_600.0F);
        espada.setTier(PowerPath.HOLLOW, 4);
        seat(espada, 4);
        int look = HollowPlayerLogic.hollowLook(espada);
        helper.assertTrue(ArrancarRank.lookNumber(look) == 4 && (look & 511) == 0, "the Cuatro Espada's look is " + look);
        int[][] tattoos = {{103, 3}, {100, 10}};
        for (int[] pair : tattoos) {
            SoulData privaron = ShinigamiTests.soul(SoulState.ARRANCAR, 1_600.0F);
            seat(privaron, pair[0]);
            int number = ArrancarRank.lookNumber(HollowPlayerLogic.hollowLook(privaron));
            helper.assertTrue(number == pair[1], "Privaron Espada " + pair[0] + " is tattooed " + number);
        }
        SoulData numero = ShinigamiTests.soul(SoulState.ARRANCAR, 1_600.0F);
        giveRank(numero, new ArrancarRank(37, 0));
        boolean numeroTattoo = BleachConfig.get(BleachConfig.NUMERO_TATTOO);
        // Set for these calls, in this one tick, and put back before any other test runs.
        BleachConfig.NUMERO_TATTOO.set(false);
        try {
            helper.assertTrue(ArrancarRank.lookNumber(HollowPlayerLogic.hollowLook(numero)) == 0,
                    "Número 37 is tattooed without numero_tattoo");
            BleachConfig.NUMERO_TATTOO.set(true);
            helper.assertTrue(ArrancarRank.lookNumber(HollowPlayerLogic.hollowLook(numero)) == 37,
                    "Número 37 is tattooed " + ArrancarRank.lookNumber(HollowPlayerLogic.hollowLook(numero))
                            + " with numero_tattoo");
        } finally {
            BleachConfig.NUMERO_TATTOO.set(numeroTattoo);
        }
        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 1_600.0F);
        seat(human, 4);
        helper.assertTrue(ArrancarRank.lookNumber(HollowPlayerLogic.hollowLook(human)) == 0,
                "a Human whose old record holds a seat is tattooed");

        SoulAppearance appearance = SoulAppearance.of(espada);
        ByteBuf buffer = Unpooled.buffer();
        try {
            SoulAppearance.STREAM_CODEC.encode(buffer, appearance);
            SoulAppearance received = SoulAppearance.STREAM_CODEC.decode(buffer);
            helper.assertTrue(received.equals(appearance) && ArrancarRank.lookNumber(received.hollowLook()) == 4,
                    "the Cuatro Espada's look came through the network as " + received);
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
}
