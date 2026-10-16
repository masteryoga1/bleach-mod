package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Where a soul stands on the road to becoming a Shinigami: Academy enrolment, the Asauchi it was
 * issued, power lent to or taken from a Shinigami, and Konsō performed. Saved with the soul and
 * sent to its owner's client. Times are game times in ticks; {@link #NEVER} means it never happened.
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
 */
public record ShinigamiProgress(boolean enrolled, long asauchiIssuedAt, long powerLentUntil, float lentReiryoku,
                                long gavePowerAt, long woundedMetAt, long konsoDay, int konsoRewards,
                                Optional<BlockPos> academyPos) {
    /** Far enough in the past that {@code now - NEVER} cannot overflow. */
    public static final long NEVER = Long.MIN_VALUE / 2;

    public static final ShinigamiProgress NONE =
            new ShinigamiProgress(false, NEVER, 0L, 0.0F, NEVER, NEVER, -1L, 0, Optional.empty());

    // Every field is read leniently, so one bad value falls back to its default on its own.
    public static final Codec<ShinigamiProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("enrolled", false).forGetter(ShinigamiProgress::enrolled),
            Codec.LONG.lenientOptionalFieldOf("asauchi_issued_at", NEVER).forGetter(ShinigamiProgress::asauchiIssuedAt),
            Codec.LONG.lenientOptionalFieldOf("power_lent_until", 0L).forGetter(ShinigamiProgress::powerLentUntil),
            Codec.FLOAT.lenientOptionalFieldOf("lent_reiryoku", 0.0F).forGetter(ShinigamiProgress::lentReiryoku),
            Codec.LONG.lenientOptionalFieldOf("gave_power_at", NEVER).forGetter(ShinigamiProgress::gavePowerAt),
            Codec.LONG.lenientOptionalFieldOf("wounded_met_at", NEVER).forGetter(ShinigamiProgress::woundedMetAt),
            Codec.LONG.lenientOptionalFieldOf("konso_day", -1L).forGetter(ShinigamiProgress::konsoDay),
            Codec.INT.lenientOptionalFieldOf("konso_rewards", 0).forGetter(ShinigamiProgress::konsoRewards),
            BlockPos.CODEC.lenientOptionalFieldOf("academy_pos").forGetter(ShinigamiProgress::academyPos)
    ).apply(instance, ShinigamiProgress::new));

    public ShinigamiProgress withEnrolled(boolean value) {
        return new ShinigamiProgress(value, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos);
    }

    public ShinigamiProgress withAsauchiIssuedAt(long value) {
        return new ShinigamiProgress(enrolled, value, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos);
    }

    public ShinigamiProgress withPowerLentUntil(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, value, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos);
    }

    public ShinigamiProgress withLentReiryoku(float value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, value, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, academyPos);
    }

    public ShinigamiProgress withGavePowerAt(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, value,
                woundedMetAt, konsoDay, konsoRewards, academyPos);
    }

    public ShinigamiProgress withWoundedMetAt(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                value, konsoDay, konsoRewards, academyPos);
    }

    public ShinigamiProgress withKonsoDay(long value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, value, konsoRewards, academyPos);
    }

    public ShinigamiProgress withKonsoRewards(int value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, value, academyPos);
    }

    public ShinigamiProgress withAcademyPos(Optional<BlockPos> value) {
        return new ShinigamiProgress(enrolled, asauchiIssuedAt, powerLentUntil, lentReiryoku, gavePowerAt,
                woundedMetAt, konsoDay, konsoRewards, value);
    }
}
