package io.github.masteryoga1.bleach.zanpakuto;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * A zanpakutō spirit, read from a data pack at {@code data/<namespace>/bleach/zanpakuto_spirit/<id>.json}
 * (see {@link ZanpakutoSpirits}). It says what kind of blade it becomes, its name and release
 * command, how its sealed and Shikai forms look and hit, what its Shikai costs, the world the soul
 * meets it in and the test it sets there. Only the server reads spirits; clients get the finished
 * text and the blade's colour and model through the item and the Jinzen pages.
 *
 * <p>A spirit file that breaks a rule below fails to load with an error naming the field. Fields
 * with a default may be left out.
 *
 * @param command     the words that release it, such as "Howl"; only a constant release may have none
 * @param colour      its RGB colour, written as {@code "#RRGGBB"}
 * @param weights     how well it fits each leaning, which picks among spirits of the same kind
 * @param releaseCost the share of maximum reiryoku a release costs, from 0 to 1 (default 0.05)
 * @param drain       the share of maximum reiryoku Shikai drains each second, from 0 to 1 (default 0.004)
 * @param tests       the steps of the test it sets in Jinzen, in order; none means it gives its name freely
 * @param bankai      its Bankai, if it has one (see {@link Bankai}); a file written before that record may give only
 *                    {@code "bankai_name"}
 */
public record ZanpakutoSpirit(ZanpakutoType type, Component name, Optional<Component> command, int colour,
                              Weights weights, Form sealed, Form shikai, Ability ability, float releaseCost,
                              float drain, InnerWorld innerWorld, List<TestStep> tests, JinzenScript script,
                              Optional<Bankai> bankai) {
    /** A colour written as {@code "#RRGGBB"} (or a chat colour's name), read as its RGB value. */
    public static final Codec<Integer> COLOUR_CODEC = TextColor.CODEC.xmap(TextColor::getValue, TextColor::fromRgb);

    public static final Codec<ZanpakutoSpirit> CODEC = RecordCodecBuilder.<ZanpakutoSpirit>create(instance -> instance.group(
            ZanpakutoType.CODEC.fieldOf("type").forGetter(ZanpakutoSpirit::type),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(ZanpakutoSpirit::name),
            ComponentSerialization.CODEC.optionalFieldOf("command").forGetter(ZanpakutoSpirit::command),
            COLOUR_CODEC.fieldOf("colour").forGetter(ZanpakutoSpirit::colour),
            Weights.CODEC.fieldOf("weights").forGetter(ZanpakutoSpirit::weights),
            Form.CODEC.fieldOf("sealed").forGetter(ZanpakutoSpirit::sealed),
            Form.CODEC.fieldOf("shikai").forGetter(ZanpakutoSpirit::shikai),
            Ability.CODEC.optionalFieldOf("ability", Ability.NONE).forGetter(ZanpakutoSpirit::ability),
            Codec.FLOAT.optionalFieldOf("release_cost", 0.05F).forGetter(ZanpakutoSpirit::releaseCost),
            Codec.FLOAT.optionalFieldOf("drain", 0.004F).forGetter(ZanpakutoSpirit::drain),
            InnerWorld.CODEC.fieldOf("inner_world").forGetter(ZanpakutoSpirit::innerWorld),
            TestStep.CODEC.listOf().optionalFieldOf("tests", List.of()).forGetter(ZanpakutoSpirit::tests),
            JinzenScript.CODEC.fieldOf("script").forGetter(ZanpakutoSpirit::script),
            Bankai.CODEC.optionalFieldOf("bankai").forGetter(ZanpakutoSpirit::bankai),
            ComponentSerialization.CODEC.optionalFieldOf("bankai_name").forGetter(spirit -> Optional.<Component>empty())
    ).apply(instance, (type, name, command, colour, weights, sealed, shikai, ability, releaseCost, drain, innerWorld,
            tests, script, bankai, bankaiName) -> new ZanpakutoSpirit(type, name, command, colour, weights, sealed, shikai,
            ability, releaseCost, drain, innerWorld, tests, script, bankai.or(() -> bankaiName.map(Bankai::named)))))
            .validate(ZanpakutoSpirit::validate);

    public ZanpakutoSpirit {
        tests = List.copyOf(tests);
    }

    /** Whether it is a constant release: released once and for good, with no command and no drain. */
    public boolean isConstant() {
        return type == ZanpakutoType.CONSTANT_RELEASE;
    }

    private static DataResult<ZanpakutoSpirit> validate(ZanpakutoSpirit spirit) {
        if (spirit.command().isEmpty() && !spirit.isConstant()) {
            return DataResult.error(() -> "command: a " + spirit.type().getSerializedName()
                    + " spirit needs a release command; only a constant_release spirit may leave it out");
        }
        if (!isFraction(spirit.releaseCost())) {
            return DataResult.error(() -> "release_cost must be between 0 and 1, not " + spirit.releaseCost());
        }
        if (!isFraction(spirit.drain())) {
            return DataResult.error(() -> "drain must be between 0 and 1, not " + spirit.drain());
        }
        return DataResult.success(spirit);
    }

    private static boolean isFraction(float value) {
        return value >= 0.0F && value <= 1.0F;
    }

    /**
     * How well the spirit fits a soul leaning each way. Among the spirits of the kind the soul leans
     * to most, the one with the highest sum of weight times leaning is chosen. Each defaults to 0.
     */
    public record Weights(float power, float kido, float constantRelease) {
        public static final Codec<Weights> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("power", 0.0F).forGetter(Weights::power),
                Codec.FLOAT.optionalFieldOf("kido", 0.0F).forGetter(Weights::kido),
                Codec.FLOAT.optionalFieldOf("constant_release", 0.0F).forGetter(Weights::constantRelease)
        ).apply(instance, Weights::new));

        public float of(ZanpakutoType type) {
            return switch (type) {
                case POWER -> power;
                case KIDO -> kido;
                case CONSTANT_RELEASE -> constantRelease;
            };
        }
    }

    /**
     * One form of the blade: its look and how it hits in the main hand.
     *
     * @param model  the {@code custom_model_data} it shows; 0 is the plain Asauchi, with none
     * @param damage total attack damage, as a sword's tooltip shows it
     * @param speed  total attacks per second
     * @param reach  extra reach for melee blows, in blocks (default 0)
     */
    public record Form(int model, float damage, float speed, float reach) {
        /** The plain Asauchi: an iron sword's look and stats. */
        public static final Form ASAUCHI = new Form(0, 6.0F, 1.6F, 0.0F);

        public static final Codec<Form> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 1_000_000).fieldOf("model").forGetter(Form::model),
                Codec.floatRange(1.0F, 1000.0F).fieldOf("damage").forGetter(Form::damage),
                Codec.floatRange(0.1F, 10.0F).fieldOf("speed").forGetter(Form::speed),
                Codec.floatRange(0.0F, 8.0F).optionalFieldOf("reach", 0.0F).forGetter(Form::reach)
        ).apply(instance, Form::new));
    }

    /**
     * The world inside the blade, where Jinzen meets the spirit.
     *
     * @param tint the RGB colour the backdrop is drawn in, written as {@code "#RRGGBB"}
     */
    public record InnerWorld(JinzenBackdrop backdrop, int tint) {
        // The same reading as COLOUR_CODEC, built here so this record's set-up never waits on the spirit's.
        private static final Codec<Integer> TINT_CODEC = TextColor.CODEC.xmap(TextColor::getValue, TextColor::fromRgb);

        public static final Codec<InnerWorld> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                JinzenBackdrop.CODEC.fieldOf("backdrop").forGetter(InnerWorld::backdrop),
                TINT_CODEC.fieldOf("tint").forGetter(InnerWorld::tint)
        ).apply(instance, InnerWorld::new));
    }

    /**
     * One step of the spirit's test.
     *
     * @param seconds how long a timed step allows, from 1 to 120 (default 10); a question has no limit
     * @param prompt  what the spirit asks or shows; each kind has a default line
     * @param answers a question's answers, 2 to 6, the right one first (the screen shuffles them)
     */
    public record TestStep(JinzenStepKind kind, int seconds, Optional<Component> prompt, List<Component> answers) {
        public static final int MIN_ANSWERS = 2;
        public static final int MAX_ANSWERS = 6;

        public static final Codec<TestStep> CODEC = RecordCodecBuilder.<TestStep>create(instance -> instance.group(
                JinzenStepKind.CODEC.fieldOf("kind").forGetter(TestStep::kind),
                Codec.intRange(1, 120).optionalFieldOf("seconds", 10).forGetter(TestStep::seconds),
                ComponentSerialization.CODEC.optionalFieldOf("prompt").forGetter(TestStep::prompt),
                ComponentSerialization.CODEC.listOf().optionalFieldOf("answers", List.of()).forGetter(TestStep::answers)
        ).apply(instance, TestStep::new)).validate(TestStep::validate);

        public TestStep {
            answers = List.copyOf(answers);
        }

        private static DataResult<TestStep> validate(TestStep step) {
            int count = step.answers().size();
            if (step.kind() == JinzenStepKind.QUESTION && (count < MIN_ANSWERS || count > MAX_ANSWERS)) {
                return DataResult.error(() -> "answers: a question needs " + MIN_ANSWERS + " to " + MAX_ANSWERS
                        + " answers, not " + count);
            }
            return DataResult.success(step);
        }
    }

    /**
     * What the spirit says in Jinzen, each list in order and each at most {@link #MAX_LINES} lines.
     * Every list defaults to empty.
     *
     * @param intro  when the soul first meets it, above the test
     * @param reveal when the soul passes, before its name
     * @param fail   when the soul fails, before it is told when to come back
     * @param visit  when a soul that knows its name comes back
     */
    public record JinzenScript(List<Component> intro, List<Component> reveal, List<Component> fail,
                               List<Component> visit) {
        public static final int MAX_LINES = 12;
        public static final JinzenScript EMPTY = new JinzenScript(List.of(), List.of(), List.of(), List.of());

        private static final Codec<List<Component>> LINES = ComponentSerialization.CODEC.listOf();

        public static final Codec<JinzenScript> CODEC = RecordCodecBuilder.<JinzenScript>create(instance -> instance.group(
                LINES.optionalFieldOf("intro", List.of()).forGetter(JinzenScript::intro),
                LINES.optionalFieldOf("reveal", List.of()).forGetter(JinzenScript::reveal),
                LINES.optionalFieldOf("fail", List.of()).forGetter(JinzenScript::fail),
                LINES.optionalFieldOf("visit", List.of()).forGetter(JinzenScript::visit)
        ).apply(instance, JinzenScript::new)).validate(JinzenScript::validate);

        public JinzenScript {
            intro = List.copyOf(intro);
            reveal = List.copyOf(reveal);
            fail = List.copyOf(fail);
            visit = List.copyOf(visit);
        }

        private static DataResult<JinzenScript> validate(JinzenScript script) {
            String tooLong = script.intro().size() > MAX_LINES ? "intro"
                    : script.reveal().size() > MAX_LINES ? "reveal"
                    : script.fail().size() > MAX_LINES ? "fail"
                    : script.visit().size() > MAX_LINES ? "visit" : null;
            if (tooLong != null) {
                return DataResult.error(() -> "script." + tooLong + ": at most " + MAX_LINES + " lines are allowed");
            }
            return DataResult.success(script);
        }
    }

    /** The technique a released blade has, on top of its Shikai form. */
    public enum Ability implements StringRepresentable {
        NONE("none"),
        /** Zangetsu's crescent of reiatsu, gathered on the Technique key and loosed with a swing. */
        GETSUGA_TENSHO("getsuga_tensho"),
        /** Zabimaru's blade lashing out in segments at a foe beyond arm's length. */
        SEGMENT_LASH("segment_lash");

        public static final Codec<Ability> CODEC = StringRepresentable.fromEnum(Ability::values);

        private final String name;

        Ability(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * The spirit's Bankai, as the file's {@code "bankai"} gives it. Only the name must be given. A file that gives only
     * the older {@code "bankai_name"} has a Bankai of that name with every other field at its default; one that gives
     * both reads {@code "bankai"} and passes over the other.
     *
     * @param name        what the Bankai is called, which is not the spirit's own name
     * @param form        its look and how it hits; left out, the Shikai's, made stronger by the server's config
     * @param model       the look a Bankai with no form takes; left out, Shikai's
     * @param releaseCost the share of maximum reiryoku a Bankai release costs, from 0 to 1; left out, the Shikai's cost
     *                    made dearer by the server's config
     * @param drain       the share of maximum reiryoku Bankai drains each second, from 0 to 1; left out, the Shikai's
     *                    drain made heavier by the server's config
     * @param ability     the art the Bankai brings (default none)
     * @param test        how the spirit tests the soul that would subjugate it (default none: a plain duel)
     * @param entity      the entity type the spirit takes when it is drawn out into the world; without one it cannot be
     */
    public record Bankai(Component name, Optional<Form> form, Optional<Integer> model, Optional<Float> releaseCost,
                         Optional<Float> drain, BankaiAbility ability, BankaiTest test, Optional<ResourceLocation> entity) {
        public static final Codec<Bankai> CODEC = RecordCodecBuilder.<Bankai>create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("name").forGetter(Bankai::name),
                Form.CODEC.optionalFieldOf("form").forGetter(Bankai::form),
                Codec.intRange(0, 1_000_000).optionalFieldOf("model").forGetter(Bankai::model),
                Codec.FLOAT.optionalFieldOf("release_cost").forGetter(Bankai::releaseCost),
                Codec.FLOAT.optionalFieldOf("drain").forGetter(Bankai::drain),
                BankaiAbility.CODEC.optionalFieldOf("ability", BankaiAbility.NONE).forGetter(Bankai::ability),
                BankaiTest.CODEC.optionalFieldOf("test", BankaiTest.NONE).forGetter(Bankai::test),
                ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(Bankai::entity)
        ).apply(instance, Bankai::new)).validate(Bankai::validate);

        /** A Bankai of this name and nothing more, as a file that gives only {@code "bankai_name"} has. */
        public static Bankai named(Component name) {
            return new Bankai(name, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    BankaiAbility.NONE, BankaiTest.NONE, Optional.empty());
        }

        private static DataResult<Bankai> validate(Bankai bankai) {
            if (bankai.form().isPresent() && bankai.model().isPresent()) {
                return DataResult.error(() -> "bankai gives a form or a model, not both");
            }
            if (bankai.releaseCost().isPresent() && !isFraction(bankai.releaseCost().get())) {
                return DataResult.error(() -> "bankai.release_cost must be between 0 and 1, not "
                        + bankai.releaseCost().get());
            }
            if (bankai.drain().isPresent() && !isFraction(bankai.drain().get())) {
                return DataResult.error(() -> "bankai.drain must be between 0 and 1, not " + bankai.drain().get());
            }
            return DataResult.success(bankai);
        }
    }

    /** The art a Bankai brings with its form. */
    public enum BankaiAbility implements StringRepresentable {
        NONE("none"),
        /** Tensa Zangetsu's: all its power packed into one small blade, for speed. */
        SPEED("speed"),
        /** Hihiō Zabimaru's: a vast serpent of bone segments, with a blast from its jaws. */
        SERPENT("serpent");

        public static final Codec<BankaiAbility> CODEC = StringRepresentable.fromEnum(BankaiAbility::values);

        private final String name;

        BankaiAbility(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** How a spirit drawn out into the world tests the soul that would subjugate it. */
    public enum BankaiTest implements StringRepresentable {
        /** A plain duel: it yields once it is beaten down. */
        NONE("none"),
        /** Zangetsu's: blades rise from the ground, and only the true one can hurt him. */
        SWORDS("swords"),
        /** Zabimaru's, the mod's own: the baboon and the snake must be beaten down together. */
        TWO_HALVES("two_halves");

        public static final Codec<BankaiTest> CODEC = StringRepresentable.fromEnum(BankaiTest::values);

        private final String name;

        BankaiTest(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
