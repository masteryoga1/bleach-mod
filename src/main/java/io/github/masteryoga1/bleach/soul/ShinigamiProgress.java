package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Where a soul stands on the road to becoming a Shinigami: Academy enrolment, the Asauchi it was
 * issued, power lent to or taken from a Shinigami, Konsō performed, and the Hollow a Visored carries
 * inside. Saved with the soul and sent to its owner's client. Times are game times in ticks;
 * {@link #NEVER} means it never happened.
 *
 * @param enrolled        whether a Rukongai soul is studying at the Academy
 * @param asauchiIssuedAt when the Academy last handed this soul an Asauchi
 * @param powerLentUntil  when a Shinigami who gave their power away gets it back (0 if they never did)
 * @param lentReiryoku    the maximum reiryoku that comes back then
 * @param gavePowerAt     when this soul last gave its power to a Human, a crime that is never forgotten
 * @param woundedMetAt    when this soul last met a wounded Shinigami
 * @param konsoDay        the game day the Konsō rewards below were counted on
 * @param konsoRewards    how many Konsō grew this soul's reiryoku on that day
 * @param academyPos      the nearest Academy this soul was told about, if any
 * @param visored         the Hollow that took root in this soul's wounds and the fights with it (see
 *                        {@link VisoredLogic}); it belongs to the soul's Shinigami power, so it is kept here
 */
public record ShinigamiProgress(boolean enrolled, long asauchiIssuedAt, long powerLentUntil, float lentReiryoku,
                                long gavePowerAt, long woundedMetAt, long konsoDay, int konsoRewards,
                                Optional<BlockPos> academyPos, Visored visored) {
    /** Far enough in the past that {@code now - NEVER} cannot overflow. */
    public static final long NEVER = Long.MIN_VALUE / 2;

    public static final ShinigamiProgress NONE =
            new ShinigamiProgress(false, NEVER, 0L, 0.0F, NEVER, NEVER, -1L, 0, Optional.empty(), Visored.NONE);

    // Every field is read leniently, so one bad value falls back to its default on its own, and a save from
    // before the Visored has no "visored" at all and reads as having no Hollow inside.
    public static final Codec<ShinigamiProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("enrolled", false).forGetter(ShinigamiProgress::enrolled),
            Codec.LONG.lenientOptionalFieldOf("asauchi_issued_at", NEVER).forGetter(ShinigamiProgress::asauchiIssuedAt),
            Codec.LONG.lenientOptionalFieldOf("power_lent_until", 0L).forGetter(ShinigamiProgress::powerLentUntil),
            Codec.FLOAT.lenientOptionalFieldOf("lent_reiryoku", 0.0F).forGetter(ShinigamiProgress::lentReiryoku),
            Codec.LONG.lenientOptionalFieldOf("gave_power_at", NEVER).forGetter(ShinigamiProgress::gavePowerAt),
            Codec.LONG.lenientOptionalFieldOf("wounded_met_at", NEVER).forGetter(ShinigamiProgress::woundedMetAt),
            Codec.LONG.lenientOptionalFieldOf("konso_day", -1L).forGetter(ShinigamiProgress::konsoDay),
            Codec.INT.lenientOptionalFieldOf("konso_rewards", 0).forGetter(ShinigamiProgress::konsoRewards),
            BlockPos.CODEC.lenientOptionalFieldOf("academy_pos").forGetter(ShinigamiProgress::academyPos),
            Visored.CODEC.lenientOptionalFieldOf("visored", Visored.NONE).forGetter(ShinigamiProgress::visored)
    ).apply(instance, ShinigamiProgress::new));

    /** A missing Visored record is no Hollow inside. */
    public ShinigamiProgress {
        visored = visored == null ? Visored.NONE : visored;
    }

    /** The nine-part record of 5i, with no Hollow inside. */
    public ShinigamiProgress(boolean enrolled, long asauchiIssuedAt, long powerLentUntil, float lentReiryoku,
                             long gavePowerAt, long woundedMetAt, long konsoDay, int konsoRewards,
                             Optional<BlockPos> academyPos) {
        this(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt, woundedMetAt, konsoDay,
                konsoRewards, academyPos, Visored.NONE);
    }

    public ShinigamiProgress withEnrolled(boolean value) {
        return new ShinigamiProgress(value, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos, visored);
    }

    public ShinigamiProgress withAsauchiIssuedAt(long value) {
        return new ShinigamiProgress(enrolled, value, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos, visored);
    }

    public ShinigamiProgress withPowerLentUntil(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, value, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos, visored);
    }

    public ShinigamiProgress withLentReiryoku(float value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, value, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos, visored);
    }

    public ShinigamiProgress withGavePowerAt(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, value,
                woundedMetAt, konsoDay, konsoRewards, academyPos, visored);
    }

    public ShinigamiProgress withWoundedMetAt(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                value, konsoDay, konsoRewards, academyPos, visored);
    }

    public ShinigamiProgress withKonsoDay(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, value, konsoRewards, academyPos, visored);
    }

    public ShinigamiProgress withKonsoRewards(int value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, value, academyPos, visored);
    }

    public ShinigamiProgress withAcademyPos(Optional<BlockPos> value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, value, visored);
    }

    public ShinigamiProgress withVisored(Visored value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos, value);
    }

    /**
     * The Hollow inside a Shinigami: how it took root and the fights with it. Saved with the rest and read
     * leniently key by key, so a save from before the Visored reads as {@link #NONE} and one bad value falls
     * back to its own default. The rules are in {@link VisoredLogic}, and the fight in {@link VisoredEvents}.
     *
     * @param wounds      wounds from Hollows counted towards rooting it, at most one per spacing
     * @param lastWoundAt when the last of those wounds was counted
     * @param rootedAt    when the Hollow took root in the soul ({@link ShinigamiProgress#NEVER} while it has not)
     * @param losses      fights the Hollow has won against its host
     * @param duelReadyAt no fight with the Hollow before this game time: the stirring after rooting, the rest
     *                    after a loss or a broken-off fight, or the end of a fight under way
     * @param subduedAt   when its host beat it and became a Visored ({@link ShinigamiProgress#NEVER} while it has not)
     */
    public record Visored(int wounds, long lastWoundAt, long rootedAt, int losses, long duelReadyAt,
                          long subduedAt) {
        public static final Visored NONE = new Visored(0, NEVER, NEVER, 0, NEVER, NEVER);

        public static final Codec<Visored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.lenientOptionalFieldOf("wounds", 0).forGetter(Visored::wounds),
                Codec.LONG.lenientOptionalFieldOf("last_wound_at", NEVER).forGetter(Visored::lastWoundAt),
                Codec.LONG.lenientOptionalFieldOf("rooted_at", NEVER).forGetter(Visored::rootedAt),
                Codec.INT.lenientOptionalFieldOf("losses", 0).forGetter(Visored::losses),
                Codec.LONG.lenientOptionalFieldOf("duel_ready_at", NEVER).forGetter(Visored::duelReadyAt),
                Codec.LONG.lenientOptionalFieldOf("subdued_at", NEVER).forGetter(Visored::subduedAt)
        ).apply(instance, Visored::new));

        /** Keeps the counts from going below zero. */
        public Visored {
            wounds = Math.max(0, wounds);
            losses = Math.max(0, losses);
        }

        /** Whether a Hollow has taken root in the soul. */
        public boolean rooted() {
            return rootedAt != NEVER;
        }

        /** Whether its host has beaten it, which makes a soul with Shinigami power a Visored. */
        public boolean subdued() {
            return subduedAt != NEVER;
        }

        public Visored withWounds(int value) {
            return new Visored(value, lastWoundAt, rootedAt, losses, duelReadyAt, subduedAt);
        }

        public Visored withLastWoundAt(long value) {
            return new Visored(wounds, value, rootedAt, losses, duelReadyAt, subduedAt);
        }

        public Visored withRootedAt(long value) {
            return new Visored(wounds, lastWoundAt, value, losses, duelReadyAt, subduedAt);
        }

        public Visored withLosses(int value) {
            return new Visored(wounds, lastWoundAt, rootedAt, value, duelReadyAt, subduedAt);
        }

        public Visored withDuelReadyAt(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, value, subduedAt);
        }

        public Visored withSubduedAt(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, duelReadyAt, value);
        }
    }
}
