package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The forms a restored Quincy can take and the waits between them: Vollständig with its wings and
 * halo, Sklaverei inside it, whether the halo is broken, whether the next landing is cushioned, and
 * when Vollständig and Licht Regen can be called on again; and Ransōtengai's threads, woven or
 * resting. Part of {@link QuincyArts}; the rules are in {@code QuincyArtsLogic} and
 * {@link QuincyRansotengai}.
 *
 * @param vollstandig        whether the soul is in Vollständig. The flight it gives is not saved, so
 *                           a form found on at login is ended quietly
 * @param sklaverei          whether Sklaverei is drawing in the reishi around the soul
 * @param haloBroken         whether the halo was shattered in this Vollständig, which ends Sklaverei
 *                           until the form is taken again
 * @param landingGrace       whether the next fall does no harm, after a form that ended in mid-air
 * @param vollReadyAt        game time Vollständig can be taken again
 * @param lichtRegenReadyAt  game time Licht Regen can be called on again
 * @param ransotengaiSince   game time Ransōtengai was woven, or {@link ShinigamiProgress#NEVER} when it
 *                           is not. The threads' hold on the body is not saved, so a weave found on at
 *                           login is let go quietly
 * @param ransotengaiReadyAt game time the threads will hold again after a collapse
 */
public record QuincyForms(boolean vollstandig, boolean sklaverei, boolean haloBroken, boolean landingGrace,
                          long vollReadyAt, long lichtRegenReadyAt, long ransotengaiSince, long ransotengaiReadyAt) {
    public static final QuincyForms NONE = new QuincyForms(false, false, false, false, 0L, 0L);

    // Every field is read leniently, so one bad value falls back to its default on its own.
    public static final Codec<QuincyForms> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.lenientOptionalFieldOf("vollstandig", false).forGetter(QuincyForms::vollstandig),
            Codec.BOOL.lenientOptionalFieldOf("sklaverei", false).forGetter(QuincyForms::sklaverei),
            Codec.BOOL.lenientOptionalFieldOf("halo_broken", false).forGetter(QuincyForms::haloBroken),
            Codec.BOOL.lenientOptionalFieldOf("landing_grace", false).forGetter(QuincyForms::landingGrace),
            Codec.LONG.lenientOptionalFieldOf("voll_ready_at", 0L).forGetter(QuincyForms::vollReadyAt),
            Codec.LONG.lenientOptionalFieldOf("licht_regen_ready_at", 0L).forGetter(QuincyForms::lichtRegenReadyAt),
            Codec.LONG.lenientOptionalFieldOf("ransotengai_since", ShinigamiProgress.NEVER)
                    .forGetter(QuincyForms::ransotengaiSince),
            Codec.LONG.lenientOptionalFieldOf("ransotengai_ready_at", 0L).forGetter(QuincyForms::ransotengaiReadyAt)
    ).apply(instance, QuincyForms::new));

    /** The forms as 4b knew them: no weave, and the threads ready. */
    public QuincyForms(boolean vollstandig, boolean sklaverei, boolean haloBroken, boolean landingGrace,
                       long vollReadyAt, long lichtRegenReadyAt) {
        this(vollstandig, sklaverei, haloBroken, landingGrace, vollReadyAt, lichtRegenReadyAt, ShinigamiProgress.NEVER, 0L);
    }

    public QuincyForms withVollstandig(boolean value) {
        return new QuincyForms(value, sklaverei, haloBroken, landingGrace, vollReadyAt, lichtRegenReadyAt,
                ransotengaiSince, ransotengaiReadyAt);
    }

    public QuincyForms withSklaverei(boolean value) {
        return new QuincyForms(vollstandig, value, haloBroken, landingGrace, vollReadyAt, lichtRegenReadyAt,
                ransotengaiSince, ransotengaiReadyAt);
    }

    public QuincyForms withHaloBroken(boolean value) {
        return new QuincyForms(vollstandig, sklaverei, value, landingGrace, vollReadyAt, lichtRegenReadyAt,
                ransotengaiSince, ransotengaiReadyAt);
    }

    public QuincyForms withLandingGrace(boolean value) {
        return new QuincyForms(vollstandig, sklaverei, haloBroken, value, vollReadyAt, lichtRegenReadyAt,
                ransotengaiSince, ransotengaiReadyAt);
    }

    public QuincyForms withVollReadyAt(long value) {
        return new QuincyForms(vollstandig, sklaverei, haloBroken, landingGrace, value, lichtRegenReadyAt,
                ransotengaiSince, ransotengaiReadyAt);
    }

    public QuincyForms withLichtRegenReadyAt(long value) {
        return new QuincyForms(vollstandig, sklaverei, haloBroken, landingGrace, vollReadyAt, value,
                ransotengaiSince, ransotengaiReadyAt);
    }

    public QuincyForms withRansotengaiSince(long value) {
        return new QuincyForms(vollstandig, sklaverei, haloBroken, landingGrace, vollReadyAt, lichtRegenReadyAt,
                value, ransotengaiReadyAt);
    }

    public QuincyForms withRansotengaiReadyAt(long value) {
        return new QuincyForms(vollstandig, sklaverei, haloBroken, landingGrace, vollReadyAt, lichtRegenReadyAt,
                ransotengaiSince, value);
    }
}
