package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.quincy.QuincyStage;

/**
 * The Quincy arts past the Heilig Bogen: the Sanrei Glove, the stage the power has reached (burnt out
 * in Letzt Stil, sealed, restored), the clocks of that stage, the training the power came back at,
 * the Gintō spell chosen, and the forms of a restored Quincy. Part of {@link QuincyProgress}, so it is
 * saved with the soul and sent to its owner's client with the rest. The rules are in
 * {@code QuincyArtsLogic}.
 *
 * @param glove             the Sanrei Glove's week of training
 * @param stage             how far the power has come through burning out and returning
 * @param stageSince        game time the soul entered that stage
 * @param letztEndsAt       game time Letzt Stil burns out and the power is sealed. Kept on game time,
 *                          so it runs on through a logout
 * @param trainingAtRestore the soul's training when its power returned, which Vollständig counts from
 * @param ginto             the spell a filled Gintō casts
 * @param forms             Vollständig, Sklaverei and the waits between them
 */
public record QuincyArts(SanreiProgress glove, QuincyStage stage, long stageSince, long letztEndsAt,
                         int trainingAtRestore, GintoSpell ginto, QuincyForms forms) {
    public static final QuincyArts NONE = new QuincyArts(SanreiProgress.NONE, QuincyStage.UNTOUCHED, 0L, 0L, 0,
            GintoSpell.GRITZ, QuincyForms.NONE);

    // Every field is read leniently, so one bad value falls back to its default on its own, and a 4a
    // save, which has no arts at all, reads as NONE.
    public static final Codec<QuincyArts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SanreiProgress.CODEC.lenientOptionalFieldOf("glove", SanreiProgress.NONE).forGetter(QuincyArts::glove),
            QuincyStage.CODEC.lenientOptionalFieldOf("stage", QuincyStage.UNTOUCHED).forGetter(QuincyArts::stage),
            Codec.LONG.lenientOptionalFieldOf("stage_since", 0L).forGetter(QuincyArts::stageSince),
            Codec.LONG.lenientOptionalFieldOf("letzt_ends_at", 0L).forGetter(QuincyArts::letztEndsAt),
            Codec.INT.lenientOptionalFieldOf("training_at_restore", 0).forGetter(QuincyArts::trainingAtRestore),
            GintoSpell.CODEC.lenientOptionalFieldOf("ginto", GintoSpell.GRITZ).forGetter(QuincyArts::ginto),
            QuincyForms.CODEC.lenientOptionalFieldOf("forms", QuincyForms.NONE).forGetter(QuincyArts::forms)
    ).apply(instance, QuincyArts::new));

    /** Never null, and the training it came back at is never negative. */
    public QuincyArts {
        glove = glove == null ? SanreiProgress.NONE : glove;
        stage = stage == null ? QuincyStage.UNTOUCHED : stage;
        ginto = ginto == null ? GintoSpell.GRITZ : ginto;
        forms = forms == null ? QuincyForms.NONE : forms;
        trainingAtRestore = Math.max(0, trainingAtRestore);
    }

    public QuincyArts withGlove(SanreiProgress value) {
        return new QuincyArts(value, stage, stageSince, letztEndsAt, trainingAtRestore, ginto, forms);
    }

    /** Moves to a stage, noting when. */
    public QuincyArts withStage(QuincyStage value, long since) {
        return new QuincyArts(glove, value, since, letztEndsAt, trainingAtRestore, ginto, forms);
    }

    public QuincyArts withLetztEndsAt(long value) {
        return new QuincyArts(glove, stage, stageSince, value, trainingAtRestore, ginto, forms);
    }

    public QuincyArts withTrainingAtRestore(int value) {
        return new QuincyArts(glove, stage, stageSince, letztEndsAt, value, ginto, forms);
    }

    public QuincyArts withGinto(GintoSpell value) {
        return new QuincyArts(glove, stage, stageSince, letztEndsAt, trainingAtRestore, value, forms);
    }

    public QuincyArts withForms(QuincyForms value) {
        return new QuincyArts(glove, stage, stageSince, letztEndsAt, trainingAtRestore, ginto, value);
    }
}
