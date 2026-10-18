package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BlutMode;

/**
 * A soul's Quincy blood and what it has made of it: whether the blood runs in it at all, whether
 * that question is settled, how far its training has come, the reishi it has gathered, the Hollow
 * poison in it, the Blut it is calling on, how many souls its arrows have destroyed, whether the
 * soul itself is being torn apart, and the arts past the Heilig Bogen. The tier itself is the soul's
 * {@link PowerPath#QUINCY} tier. Saved with the soul and sent to its owner's client. The rules are in
 * {@link QuincyLogic}, and those of the arts in {@link QuincyArtsLogic}.
 *
 * @param bloodline the blood: none, mixed (Gemischt) or pure (Echt)
 * @param decided   whether the bloodline question is settled, by the player, the server or an old save
 * @param training  training points earned with the Quincy Cross, which raise the tier
 * @param reishi    reishi gathered and not yet spent, up to {@link QuincyLogic#maxReishi}
 * @param poison    Hollow poison, from 0 to {@link #MAX_POISON}
 * @param blut      the Blut the soul is calling on, if any
 * @param destroyed souls destroyed by this soul's Heilig Pfeil, Hollow mobs and Hollow players alike
 * @param tornApart whether the soul is being torn apart, from just before that death until the
 *                  respawn it knits itself together for (see {@link QuincyEvents#destroySoul}), so
 *                  logging out on the death screen does not lose it
 * @param arts      the Sanrei Glove, Letzt Stil and what follows it, Gintō and the restored forms
 */
public record QuincyProgress(Bloodline bloodline, boolean decided, int training, float reishi,
                             float poison, BlutMode blut, int destroyed, boolean tornApart, QuincyArts arts) {
    public static final float MAX_POISON = 100.0F;

    public static final QuincyProgress NONE = new QuincyProgress(Bloodline.NONE, false, 0, 0.0F, 0.0F, BlutMode.OFF,
            0, false, QuincyArts.NONE);

    // Every field is read leniently, so one bad value falls back to its default on its own. A save
    // from before the arts has no "arts" and reads with none. Nine of the builder's sixteen fields.
    public static final Codec<QuincyProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Bloodline.CODEC.lenientOptionalFieldOf("bloodline", Bloodline.NONE).forGetter(QuincyProgress::bloodline),
            Codec.BOOL.lenientOptionalFieldOf("decided", false).forGetter(QuincyProgress::decided),
            Codec.INT.lenientOptionalFieldOf("training", 0).forGetter(QuincyProgress::training),
            Codec.FLOAT.lenientOptionalFieldOf("reishi", 0.0F).forGetter(QuincyProgress::reishi),
            Codec.FLOAT.lenientOptionalFieldOf("poison", 0.0F).forGetter(QuincyProgress::poison),
            BlutMode.CODEC.lenientOptionalFieldOf("blut", BlutMode.OFF).forGetter(QuincyProgress::blut),
            Codec.INT.lenientOptionalFieldOf("destroyed", 0).forGetter(QuincyProgress::destroyed),
            Codec.BOOL.lenientOptionalFieldOf("torn_apart", false).forGetter(QuincyProgress::tornApart),
            QuincyArts.CODEC.lenientOptionalFieldOf("arts", QuincyArts.NONE).forGetter(QuincyProgress::arts)
    ).apply(instance, QuincyProgress::new));

    /**
     * Keeps every value in range. Reishi and poison that are not a positive finite number become
     * exactly zero, so equal records compare equal (a record compares -0.0 and 0.0 as different).
     */
    public QuincyProgress {
        bloodline = bloodline == null ? Bloodline.NONE : bloodline;
        blut = blut == null ? BlutMode.OFF : blut;
        arts = arts == null ? QuincyArts.NONE : arts;
        training = Math.max(0, training);
        destroyed = Math.max(0, destroyed);
        reishi = Float.isFinite(reishi) && reishi > 0.0F ? reishi : 0.0F;
        poison = Float.isFinite(poison) && poison > 0.0F ? Math.min(poison, MAX_POISON) : 0.0F;
    }

    /** Progress from before the arts (step 4a), with none of them yet. */
    public QuincyProgress(Bloodline bloodline, boolean decided, int training, float reishi, float poison, BlutMode blut,
                          int destroyed, boolean tornApart) {
        this(bloodline, decided, training, reishi, poison, blut, destroyed, tornApart, QuincyArts.NONE);
    }

    public QuincyProgress withBloodline(Bloodline value) {
        return new QuincyProgress(value, decided, training, reishi, poison, blut, destroyed, tornApart, arts);
    }

    public QuincyProgress withDecided(boolean value) {
        return new QuincyProgress(bloodline, value, training, reishi, poison, blut, destroyed, tornApart, arts);
    }

    public QuincyProgress withTraining(int value) {
        return new QuincyProgress(bloodline, decided, value, reishi, poison, blut, destroyed, tornApart, arts);
    }

    public QuincyProgress withReishi(float value) {
        return new QuincyProgress(bloodline, decided, training, value, poison, blut, destroyed, tornApart, arts);
    }

    public QuincyProgress withPoison(float value) {
        return new QuincyProgress(bloodline, decided, training, reishi, value, blut, destroyed, tornApart, arts);
    }

    public QuincyProgress withBlut(BlutMode value) {
        return new QuincyProgress(bloodline, decided, training, reishi, poison, value, destroyed, tornApart, arts);
    }

    public QuincyProgress withDestroyed(int value) {
        return new QuincyProgress(bloodline, decided, training, reishi, poison, blut, value, tornApart, arts);
    }

    public QuincyProgress withTornApart(boolean value) {
        return new QuincyProgress(bloodline, decided, training, reishi, poison, blut, destroyed, value, arts);
    }

    public QuincyProgress withArts(QuincyArts value) {
        return new QuincyProgress(bloodline, decided, training, reishi, poison, blut, destroyed, tornApart, value);
    }
}
