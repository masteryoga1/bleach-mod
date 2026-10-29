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
 * @param visored         the Hollow that took root in this soul's wounds, the fights with it and its mask (see
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
     * The Hollow inside a Shinigami: how it took root, the fights with it and, once it is beaten, its mask. Saved
     * with the rest and read leniently key by key, so a save from before the Visored (or the mask) reads as
     * {@link #NONE} (or with no mask yet) and one bad value falls back to its own default. The rules are in
     * {@link VisoredLogic}, and the fight and the mask in {@link VisoredEvents}. The mask is on while
     * {@code maskEndsAt} is set ({@link #masked}), so the owner's client knows it from this record alone.
     *
     * @param wounds           wounds from Hollows counted towards rooting it, at most one per spacing
     * @param lastWoundAt      when the last of those wounds was counted
     * @param rootedAt         when the Hollow took root in the soul ({@link ShinigamiProgress#NEVER} while it has not)
     * @param losses           fights the Hollow has won against its host
     * @param duelReadyAt      no fight with the Hollow before this game time: the stirring after rooting, the rest
     *                         after a loss or a broken-off fight, or the end of a fight under way
     * @param subduedAt        when its host beat it and became a Visored ({@link ShinigamiProgress#NEVER} while it has
     *                         not)
     * @param maskChangedAt    when the mask last went on or came off ({@link ShinigamiProgress#NEVER} before the
     *                         first): the start of a wearing, or of the wait after it
     * @param maskEndsAt       when the mask on crumbles, or {@link ShinigamiProgress#NEVER} while it is off
     * @param maskReadyAt      no mask before this game time: the wait after the last one
     * @param maskTrainedTicks ticks masks have been worn in all, outside creative, which lengthens the next; at most
     *                         {@link #MAX_TRAINED_TICKS}
     */
    public record Visored(int wounds, long lastWoundAt, long rootedAt, int losses, long duelReadyAt,
                          long subduedAt, long maskChangedAt, long maskEndsAt, long maskReadyAt,
                          long maskTrainedTicks) {
        /** The most mask time that counts toward growth: 24 hours. */
        public static final long MAX_TRAINED_TICKS = 1_728_000L;

        public static final Visored NONE = new Visored(0, NEVER, NEVER, 0, NEVER, NEVER, NEVER, NEVER, NEVER, 0L);

        public static final Codec<Visored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.lenientOptionalFieldOf("wounds", 0).forGetter(Visored::wounds),
                Codec.LONG.lenientOptionalFieldOf("last_wound_at", NEVER).forGetter(Visored::lastWoundAt),
                Codec.LONG.lenientOptionalFieldOf("rooted_at", NEVER).forGetter(Visored::rootedAt),
                Codec.INT.lenientOptionalFieldOf("losses", 0).forGetter(Visored::losses),
                Codec.LONG.lenientOptionalFieldOf("duel_ready_at", NEVER).forGetter(Visored::duelReadyAt),
                Codec.LONG.lenientOptionalFieldOf("subdued_at", NEVER).forGetter(Visored::subduedAt),
                Codec.LONG.lenientOptionalFieldOf("mask_changed_at", NEVER).forGetter(Visored::maskChangedAt),
                Codec.LONG.lenientOptionalFieldOf("mask_ends_at", NEVER).forGetter(Visored::maskEndsAt),
                Codec.LONG.lenientOptionalFieldOf("mask_ready_at", NEVER).forGetter(Visored::maskReadyAt),
                Codec.LONG.lenientOptionalFieldOf("mask_trained_ticks", 0L).forGetter(Visored::maskTrainedTicks)
        ).apply(instance, Visored::new));

        /** Keeps the counts from going below zero, and the mask's training within 0 and its cap. */
        public Visored {
            wounds = Math.max(0, wounds);
            losses = Math.max(0, losses);
            maskTrainedTicks = Math.max(0L, Math.min(MAX_TRAINED_TICKS, maskTrainedTicks));
        }

        /** The 5j shape: no mask yet. */
        public Visored(int wounds, long lastWoundAt, long rootedAt, int losses, long duelReadyAt, long subduedAt) {
            this(wounds, lastWoundAt, rootedAt, losses, duelReadyAt, subduedAt, NEVER, NEVER, NEVER, 0L);
        }

        /** Whether a Hollow has taken root in the soul. */
        public boolean rooted() {
            return rootedAt != NEVER;
        }

        /** Whether its host has beaten it, which makes a soul with Shinigami power a Visored. */
        public boolean subdued() {
            return subduedAt != NEVER;
        }

        /** Whether the Hollow's mask is on. */
        public boolean masked() {
            return maskEndsAt != NEVER;
        }

        public Visored withWounds(int value) {
            return new Visored(value, lastWoundAt, rootedAt, losses, duelReadyAt, subduedAt, maskChangedAt, maskEndsAt,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withLastWoundAt(long value) {
            return new Visored(wounds, value, rootedAt, losses, duelReadyAt, subduedAt, maskChangedAt, maskEndsAt,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withRootedAt(long value) {
            return new Visored(wounds, lastWoundAt, value, losses, duelReadyAt, subduedAt, maskChangedAt, maskEndsAt,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withLosses(int value) {
            return new Visored(wounds, lastWoundAt, rootedAt, value, duelReadyAt, subduedAt, maskChangedAt, maskEndsAt,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withDuelReadyAt(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, value, subduedAt, maskChangedAt, maskEndsAt,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withSubduedAt(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, duelReadyAt, value, maskChangedAt, maskEndsAt,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withMaskChangedAt(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, duelReadyAt, subduedAt, value, maskEndsAt,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withMaskEndsAt(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, duelReadyAt, subduedAt, maskChangedAt, value,
                    maskReadyAt, maskTrainedTicks);
        }

        public Visored withMaskReadyAt(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, duelReadyAt, subduedAt, maskChangedAt, maskEndsAt,
                    value, maskTrainedTicks);
        }

        public Visored withMaskTrainedTicks(long value) {
            return new Visored(wounds, lastWoundAt, rootedAt, losses, duelReadyAt, subduedAt, maskChangedAt, maskEndsAt,
                    maskReadyAt, value);
        }
    }
}
