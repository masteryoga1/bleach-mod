package io.github.masteryoga1.bleach.kido;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * A Kidō spell, read from a data pack at {@code data/<namespace>/bleach/kido_spell/<id>.json} when a world loads
 * (not on {@code /reload}). Its id is its key: {@code bleach:byakurai} is Hadō #4 Byakurai. It says what kind of
 * spell it is and its number in that kind, its name, what it does ({@link Shape}, which is Java, so data cannot
 * invent behaviour), the Kidō levels it needs, what it costs and how long the hands stay busy after it, the Kidō
 * xp it trains, how it looks, its numbers, and how long it gathers in the palm cast by name. The rules of casting
 * are in {@link io.github.masteryoga1.bleach.soul.KidoLogic}.
 *
 * <p>Every field may be left out and every number is clamped to its range, so a mistake in one spell file falls
 * back field by field instead of stopping the world from loading, as any error in a data pack registry would. A
 * shape this version does not know reads as {@link Shape#NONE}, which is never cast.
 *
 * <p>The spells are sent to clients as they join (see {@link #register}), so a client may look them up from its
 * own level once logged in: it names and colours them.
 *
 * @param number        its number within its kind, from 0 to {@link #MAX_NUMBER}
 * @param name          its name, usually a translation such as {@code kido.bleach.byakurai}
 * @param level         the Kidō level to cast it at all: by its incantation, or by name if it has none
 * @param byNameLevel   the Kidō level to cast it by name alone, never below {@code level}; read only for a spell
 *                      with an incantation
 * @param chantTicks    ticks of chanting before a chanted cast goes off
 * @param incantations  the whole chants that cast it in chat, at most {@link #MAX_INCANTATIONS}, each at most
 *                      {@link #MAX_INCANTATION_LENGTH} characters (chat's limit); none means it has no known
 *                      incantation and loses nothing cast by name
 * @param costShare     the share of maximum reiryoku a cast costs, from 0 to 1
 * @param minCost       the least reiryoku a cast costs
 * @param cooldownTicks ticks before the caster's hands can cast again, at least 1
 * @param xp            the Kidō xp a cast trains
 * @param gatherTicks   ticks a cast by name gathers in the palm, the Kidō key held, before it goes off (0 for at
 *                      once), at most five seconds
 */
public record KidoSpell(Kind kind, int number, Component name, Shape shape, int level, int byNameLevel,
                        int chantTicks, List<String> incantations, float costShare, float minCost,
                        int cooldownTicks, int xp, Look look, Effect effect, int gatherTicks) {
    public static final int MAX_NUMBER = 999;
    public static final int MAX_LEVEL = 1_000;
    public static final int MAX_INCANTATIONS = 8;
    public static final int MAX_INCANTATION_LENGTH = 256;
    /** The farthest a gust reaches, whatever its range says, so the box it looks in stays small. */
    public static final double GUST_MAX_RANGE = 32.0;
    private static final int MAX_CHANT_TICKS = 1_200;
    /** The longest a spell gathers in the palm: five seconds of a held key. */
    private static final int MAX_GATHER_TICKS = 100;
    private static final int MAX_COOLDOWN_TICKS = 6_000;
    private static final int MAX_XP = 1_000;
    private static final float MAX_MIN_COST = 10_000.0F;

    // Every field is read leniently, so one bad value falls back to its own default and the rest of the spell stays.
    public static final Codec<KidoSpell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Kind.CODEC.lenientOptionalFieldOf("kind", Kind.HADO).forGetter(KidoSpell::kind),
            Codec.INT.lenientOptionalFieldOf("number", 0).forGetter(KidoSpell::number),
            ComponentSerialization.CODEC.lenientOptionalFieldOf("name", Component.literal("?")).forGetter(KidoSpell::name),
            Shape.CODEC.lenientOptionalFieldOf("shape", Shape.NONE).forGetter(KidoSpell::shape),
            Codec.INT.lenientOptionalFieldOf("level", 0).forGetter(KidoSpell::level),
            Codec.INT.lenientOptionalFieldOf("by_name_level", 0).forGetter(KidoSpell::byNameLevel),
            Codec.INT.lenientOptionalFieldOf("chant_ticks", 0).forGetter(KidoSpell::chantTicks),
            Codec.STRING.listOf().lenientOptionalFieldOf("incantations", List.of()).forGetter(KidoSpell::incantations),
            Codec.FLOAT.lenientOptionalFieldOf("cost_share", 0.05F).forGetter(KidoSpell::costShare),
            Codec.FLOAT.lenientOptionalFieldOf("min_cost", 1.0F).forGetter(KidoSpell::minCost),
            Codec.INT.lenientOptionalFieldOf("cooldown_ticks", 20).forGetter(KidoSpell::cooldownTicks),
            Codec.INT.lenientOptionalFieldOf("xp", 1).forGetter(KidoSpell::xp),
            Look.CODEC.lenientOptionalFieldOf("look", Look.NONE).forGetter(KidoSpell::look),
            Effect.CODEC.lenientOptionalFieldOf("effect", Effect.NONE).forGetter(KidoSpell::effect),
            Codec.INT.lenientOptionalFieldOf("gather_ticks", 0).forGetter(KidoSpell::gatherTicks)
    ).apply(instance, KidoSpell::new));

    // ---- The registry ----

    public static final ResourceKey<Registry<KidoSpell>> REGISTRY =
            ResourceKey.createRegistryKey(BleachMod.id("kido_spell"));

    /** Hadō #1 Shō, a push from the fingertip. */
    public static final ResourceKey<KidoSpell> SHO = ResourceKey.create(REGISTRY, BleachMod.id("sho"));
    /** Hadō #4 Byakurai, a narrow pale bolt from the index finger. */
    public static final ResourceKey<KidoSpell> BYAKURAI = ResourceKey.create(REGISTRY, BleachMod.id("byakurai"));
    /** Hadō #31 Shakkahō, a crimson blast that bursts and burns. */
    public static final ResourceKey<KidoSpell> SHAKKAHO = ResourceKey.create(REGISTRY, BleachMod.id("shakkaho"));
    /** Hadō #33 Sōkatsui, a wide blue blast. */
    public static final ResourceKey<KidoSpell> SOKATSUI = ResourceKey.create(REGISTRY, BleachMod.id("sokatsui"));
    /** Hadō #58 Tenran, a gust widening from the hand. */
    public static final ResourceKey<KidoSpell> TENRAN = ResourceKey.create(REGISTRY, BleachMod.id("tenran"));
    /** Hadō #63 Raikōhō, a yellow bolt gathered in the palm. */
    public static final ResourceKey<KidoSpell> RAIKOHO = ResourceKey.create(REGISTRY, BleachMod.id("raikoho"));

    /** Kind, then number, then id: the order spells are listed and chosen in. */
    public static final Comparator<Holder.Reference<KidoSpell>> ORDER =
            Comparator.<Holder.Reference<KidoSpell>, Kind>comparing(spell -> spell.value().kind())
                    .thenComparingInt(spell -> spell.value().number())
                    .thenComparing(spell -> spell.key().location());

    /** Keeps every number in its range and the incantations clean; anything missing takes its default. */
    public KidoSpell {
        kind = kind == null ? Kind.HADO : kind;
        number = Mth.clamp(number, 0, MAX_NUMBER);
        name = name == null ? Component.literal("?") : name;
        shape = shape == null ? Shape.NONE : shape;
        level = Mth.clamp(level, 0, MAX_LEVEL);
        byNameLevel = Mth.clamp(byNameLevel, level, MAX_LEVEL);
        chantTicks = Mth.clamp(chantTicks, 0, MAX_CHANT_TICKS);
        incantations = cleaned(incantations);
        costShare = within(costShare, 0.0F, 1.0F);
        minCost = within(minCost, 0.0F, MAX_MIN_COST);
        cooldownTicks = Mth.clamp(cooldownTicks, 1, MAX_COOLDOWN_TICKS);
        xp = Mth.clamp(xp, 0, MAX_XP);
        look = look == null ? Look.NONE : look;
        effect = effect == null ? Effect.NONE : effect;
        gatherTicks = Mth.clamp(gatherTicks, 0, MAX_GATHER_TICKS);
    }

    /**
     * Registers the spells as a data pack registry synced to clients, with the codec as its own network codec: a
     * client needs them to name and colour spells, so it cannot join without them.
     */
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY, CODEC, CODEC);
    }

    /** The world's spells: the server's own, or on a client the copy it was sent on joining. */
    public static Optional<Registry<KidoSpell>> registry(Level level) {
        return level.registryAccess().registry(REGISTRY);
    }

    /** The spell with this id, if the world has it. */
    public static Optional<Holder.Reference<KidoSpell>> get(Level level, ResourceLocation id) {
        return registry(level).flatMap(spells -> spells.getHolder(id));
    }

    /** Every spell, in {@link #ORDER}. */
    public static List<Holder.Reference<KidoSpell>> sorted(Registry<KidoSpell> spells) {
        return spells.holders().sorted(ORDER).toList();
    }

    /** The spells of this kind and number, in {@link #ORDER}: a list, as Bakudō #9 holds two. */
    public static List<Holder.Reference<KidoSpell>> byNumber(Registry<KidoSpell> spells, Kind kind, int number) {
        return spells.holders()
                .filter(spell -> spell.value().kind() == kind && spell.value().number() == number)
                .sorted(ORDER)
                .toList();
    }

    // ---- The spell ----

    /** Whether it does anything: a spell of {@link Shape#NONE} is never cast or listed. */
    public boolean castable() {
        return shape != Shape.NONE;
    }

    /** Whether it has a known incantation, so casting it by name alone weakens it and needs a higher level. */
    public boolean chantable() {
        return !incantations.isEmpty();
    }

    /** Whether a cast by name gathers in the palm, the Kidō key held, before it goes off. */
    public boolean gathers() {
        return gatherTicks > 0;
    }

    /** Its title, such as "Hadō #4 Byakurai": its kind's name, its number and its name. */
    public Component title() {
        return Component.translatable("kido.bleach.title", Component.translatable(kind.translationKey()), number, name);
    }

    /** What its caster calls out by name, such as "Hadō #4, Byakurai!". */
    public Component call() {
        return Component.translatable("kido.bleach.call", Component.translatable(kind.translationKey()), number, name);
    }

    // ---- Helpers ----

    /** Incantations stripped, with blank ones and those longer than chat allows dropped, and at most the most kept. */
    private static List<String> cleaned(List<String> incantations) {
        List<String> kept = new ArrayList<>();
        if (incantations != null) {
            for (String line : incantations) {
                String stripped = line == null ? "" : line.strip();
                if (!stripped.isEmpty() && stripped.length() <= MAX_INCANTATION_LENGTH && kept.size() < MAX_INCANTATIONS) {
                    kept.add(stripped);
                }
            }
        }
        return List.copyOf(kept);
    }

    /** A value kept between the two, with not a number read as the low end. */
    private static float within(float value, float min, float max) {
        return Float.isNaN(value) ? min : Mth.clamp(value, min, max);
    }

    /** The three kinds of Kidō, each numbered by difficulty. */
    public enum Kind implements StringRepresentable {
        /** Destruction: the spells that strike. */
        HADO,
        /** Binding: the spells that hold, seal and ward. */
        BAKUDO,
        /** Healing. */
        KAIDO;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The key of its name, such as {@code kido_kind.bleach.hado} ("Hadō"). */
        public String translationKey() {
            return "kido_kind.bleach." + getSerializedName();
        }
    }

    /** What a spell does. Later shapes are appended; written by name, so their order is free. */
    public enum Shape implements StringRepresentable {
        /** Nothing: the default, and what an unknown shape reads as. Never cast. */
        NONE,
        /** A beam struck at once along the caster's look, through the Cero's engine. */
        BEAM,
        /** A gust widening from the hand, striking and pushing every creature in it at once, and blinding the players. */
        GUST;

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * How a spell is seen and heard. The sound and the particle are looked up by id when it is cast, and left out
     * when there is none by that id (or the particle needs options of its own).
     *
     * @param colour   the colour of its trace, written as {@code "#RRGGBB"}
     * @param sound    the sound played at the hand, for those who see spirits
     * @param pitch    that sound's pitch, from 0.5 to 2
     * @param particle the simple particle puffed at the hand and where it strikes, for those who see spirits
     */
    public record Look(int colour, Optional<ResourceLocation> sound, float pitch, Optional<ResourceLocation> particle) {
        // Built before NONE, with plain defaults: making NONE calls the spell's clamp, which may set the spell up,
        // and the spell's codec reads this one.
        public static final Codec<Look> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ZanpakutoSpirit.COLOUR_CODEC.lenientOptionalFieldOf("colour", 0xFFFFFF).forGetter(Look::colour),
                ResourceLocation.CODEC.lenientOptionalFieldOf("sound").forGetter(Look::sound),
                Codec.FLOAT.lenientOptionalFieldOf("pitch", 1.0F).forGetter(Look::pitch),
                ResourceLocation.CODEC.lenientOptionalFieldOf("particle").forGetter(Look::particle)
        ).apply(instance, Look::new));

        /** White, silent and with no particle. */
        public static final Look NONE = new Look(0xFFFFFF, Optional.empty(), 1.0F, Optional.empty());

        public Look {
            colour &= 0xFFFFFF;
            sound = sound == null ? Optional.empty() : sound;
            pitch = within(pitch, 0.5F, 2.0F);
            particle = particle == null ? Optional.empty() : particle;
        }
    }

    /**
     * A spell's numbers: one bag shared by every shape, each shape reading the ones it needs.
     *
     * @param damage        damage before reiryoku is added
     * @param damagePerRoot damage added per square root of the caster's maximum reiryoku
     * @param maxDamage     the most damage once the Kidō level has grown it, before the form's share is taken
     * @param range         how far it reaches, in blocks (a gust at most {@link KidoSpell#GUST_MAX_RANGE})
     * @param radius        its half-width, in blocks (for a gust, only its streak's)
     * @param pierce        how many creatures it may strike that it meets before it stops
     * @param blastRadius   how far about where it bursts the blast reaches, in blocks (0 for none)
     * @param blastShare    the blast's share of the damage
     * @param knockback     push along it (a gust's, away from the caster), on top of the blow's own
     * @param burnSeconds   seconds a creature it strikes burns
     * @param angle         a gust's whole width, in degrees, from 1 to 180
     * @param blindSeconds  seconds a player it reaches is blinded, from 0 to 30
     * @param bareShare     the share of its strength left to a caster with no zanpakutō at hand, from 0 to 1
     */
    public record Effect(float damage, float damagePerRoot, float maxDamage, double range, float radius, int pierce,
                         float blastRadius, float blastShare, float knockback, float burnSeconds, float angle,
                         float blindSeconds, float bareShare) {
        // Built before NONE, with plain defaults: making NONE calls the spell's clamp, which may set the spell up,
        // and the spell's codec reads this one.
        public static final Codec<Effect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.lenientOptionalFieldOf("damage", 0.0F).forGetter(Effect::damage),
                Codec.FLOAT.lenientOptionalFieldOf("damage_per_root", 0.0F).forGetter(Effect::damagePerRoot),
                Codec.FLOAT.lenientOptionalFieldOf("max_damage", 1_000.0F).forGetter(Effect::maxDamage),
                Codec.DOUBLE.lenientOptionalFieldOf("range", 16.0).forGetter(Effect::range),
                Codec.FLOAT.lenientOptionalFieldOf("radius", 0.2F).forGetter(Effect::radius),
                Codec.INT.lenientOptionalFieldOf("pierce", 1).forGetter(Effect::pierce),
                Codec.FLOAT.lenientOptionalFieldOf("blast_radius", 0.0F).forGetter(Effect::blastRadius),
                Codec.FLOAT.lenientOptionalFieldOf("blast_share", 0.5F).forGetter(Effect::blastShare),
                Codec.FLOAT.lenientOptionalFieldOf("knockback", 0.0F).forGetter(Effect::knockback),
                Codec.FLOAT.lenientOptionalFieldOf("burn_seconds", 0.0F).forGetter(Effect::burnSeconds),
                Codec.FLOAT.lenientOptionalFieldOf("angle", 60.0F).forGetter(Effect::angle),
                Codec.FLOAT.lenientOptionalFieldOf("blind_seconds", 0.0F).forGetter(Effect::blindSeconds),
                Codec.FLOAT.lenientOptionalFieldOf("bare_share", 1.0F).forGetter(Effect::bareShare)
        ).apply(instance, Effect::new));

        /**
         * No damage, a reach of 16 blocks, a fifth of a block either side of its line, stopping at the first creature;
         * a sixty-degree gust that blinds no one, and no loss without a blade.
         */
        public static final Effect NONE = new Effect(0.0F, 0.0F, 1_000.0F, 16.0, 0.2F, 1, 0.0F, 0.5F, 0.0F, 0.0F, 60.0F,
                0.0F, 1.0F);

        public Effect {
            damage = within(damage, 0.0F, 1_000.0F);
            damagePerRoot = within(damagePerRoot, 0.0F, 50.0F);
            maxDamage = within(maxDamage, 0.0F, 1_000.0F);
            range = Double.isNaN(range) ? 1.0 : Mth.clamp(range, 1.0, 128.0);
            radius = within(radius, 0.05F, 8.0F);
            pierce = Mth.clamp(pierce, 1, 64);
            blastRadius = within(blastRadius, 0.0F, 16.0F);
            blastShare = within(blastShare, 0.0F, 1.0F);
            knockback = within(knockback, 0.0F, 10.0F);
            burnSeconds = within(burnSeconds, 0.0F, 60.0F);
            angle = within(angle, 1.0F, 180.0F);
            blindSeconds = within(blindSeconds, 0.0F, 30.0F);
            bareShare = within(bareShare, 0.0F, 1.0F);
        }
    }
}
