package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The rules of Kidō: who may cast, the spells a soul knows by its Kidō level and the one it has chosen, the gate
 * on casting by name alone, a spell's strength, cost and training, how a line said in chat is heard as an
 * incantation, and the misfire of a cast by name. Like {@link ShinigamiLogic}, these only read and change a
 * {@link SoulData}, a spell and the config ({@link #mayCast} also asks the player whether it lives, watches or is
 * held), so game tests can call them directly. Casting itself is {@link KidoEvents#cast}. {@code now} is the
 * level's game time.
 *
 * <p>There is no list of learned spells: the Kidō level alone unlocks a spell, at its {@code level}. A spell
 * with an incantation needs its higher {@code by_name_level} to be cast by name alone (Eishōhaki), and is weaker
 * so, and may burst in its caster's hand until it is mastered; one with none has nothing to abandon and casts by
 * name at full strength.
 */
public final class KidoLogic {
    /** Below this much reiryoku left, a cast is refused, so Kidō never exhausts its caster (Bala's rule). */
    public static final float MIN_LEFT = 0.5F;
    /** The least health a misfire leaves its caster: one heart. */
    public static final float MISFIRE_FLOOR = 2.0F;

    /** Every combining mark, such as a macron or an accent, once a line is decomposed. */
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    /** How a spell is cast: by name alone with the key (Eishōhaki), or by its whole incantation. */
    public enum Form { NAME, CHANT }

    /** Why a soul may not cast, or choose, now. Those with a {@link #messageKey} are told on the action bar. */
    public enum Refusal {
        /** Dead, a spectator or held by Negación or a Caja; or a spell of no shape, or no server world. Told nothing. */
        UNABLE(null),
        /** Neither a Shinigami's power nor an Academy student, or a Quincy's power in the way. */
        UNTRAINED("message.bleach.kido.untrained"),
        /** Its Hollow holds its body; told the way back, as every sealed Shinigami art is. */
        TAKEN("message.bleach.visored.takeover.sealed"),
        /** Its power is lent to a Human: Kidō will not answer to its name alone, only to its whole incantation. */
        LENT("message.bleach.kido.lent"),
        /** It knows no spell: none it can cast at its Kidō level. */
        NONE_KNOWN("message.bleach.kido.none_known"),
        /** Its Kidō level is below the spell's. */
        NEEDS_LEVEL("message.bleach.kido.needs_level"),
        /** Cast by name alone, its Kidō level is below the spell's by-name level. */
        NEEDS_NAME_LEVEL("message.bleach.kido.needs_level.by_name"),
        /** The hands are still busy from the last cast. Told nothing, as a line every half second would bury the rest. */
        COOLDOWN(null),
        /** A Getsuga Tenshō or a Cero is gathering: one art at a time. */
        GATHERING("message.bleach.getsuga.already_gathering"),
        /** The cast would leave it with less than {@link KidoLogic#MIN_LEFT} reiryoku. */
        TOO_WEAK("message.bleach.kido.too_weak"),
        /** A chant is already being said: the key and a second chant wait for it. */
        CHANTING("message.bleach.kido.chanting"),
        /** Cast by name alone, the spell burst in the caster's hand: paid for, and nothing cast. */
        MISFIRED("message.bleach.kido.misfire");

        @Nullable
        private final String messageKey;

        Refusal(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        /** The action-bar line, or null for a silent one. */
        @Nullable
        public String messageKey() {
            return messageKey;
        }
    }

    /**
     * An incantation being said: the spell it was heard as, by its id and as it was then (so a data pack cannot change
     * it halfway), and the game time it goes off. Transient: it is never saved.
     */
    public record Chant(ResourceLocation id, KidoSpell spell, long firesAt) {
    }

    private KidoLogic() {
    }

    // ---- Who may cast ----

    /** As {@link #mayCast(Player, SoulData, long, Form)} for a cast by name: what the Kidō key and choosing ask. */
    @Nullable
    public static Refusal mayCast(Player player, SoulData data, long now) {
        return mayCast(player, data, now, Form.NAME);
    }

    /**
     * Null when this soul may cast now in this form, else why not, the first that fails winning: unable (dead, a
     * spectator, or held by Negación or a Caja), untrained (no Shinigami's power and not an Academy student, or an
     * awakened Quincy where hybrids are not allowed), its Hollow holding its body, and its power lent, which refuses
     * only a cast by name: a lender still chants, as Rukia does after lending hers. Server side: on a client the
     * taken-over row is always clear, since {@link VisoredLogic#takenOver} reads a time that is not saved or sent.
     * (6f's page, to grey spells out while taken over, reads {@code ClientSouls.appearance(player).hollowLook()} and
     * {@link HollowPlayerLogic#LOOK_TAKEN_OVER}, as the soul screen does.)
     */
    @Nullable
    public static Refusal mayCast(Player player, SoulData data, long now, Form form) {
        if (!player.isAlive() || player.isSpectator() || Negacion.held(player)) {
            return Refusal.UNABLE;
        }
        if (!ShinigamiLogic.spirituallyTrained(data) || QuincyLogic.blocksShinigamiPower(data)) {
            return Refusal.UNTRAINED;
        }
        if (VisoredLogic.takenOver(data)) {
            return Refusal.TAKEN;
        }
        if (form == Form.NAME && ShinigamiLogic.isPowerLent(data, now)) {
            return Refusal.LENT;
        }
        return null;
    }

    /**
     * Whether a soul refused so may still choose its spell: one that may cast, or one kept from casting only for a
     * while, by its Hollow or its lent power (a lender picks the spell it will chant).
     */
    public static boolean mayChoose(@Nullable Refusal refusal) {
        return refusal == null || refusal == Refusal.TAKEN || refusal == Refusal.LENT;
    }

    // ---- Knowing and choosing ----

    /** The castable spells at or below its Kidō level, in {@link KidoSpell#ORDER}. */
    public static List<Holder.Reference<KidoSpell>> known(SoulData data, Registry<KidoSpell> spells) {
        int kido = data.level(Stat.KIDO);
        return KidoSpell.sorted(spells).stream()
                .filter(spell -> spell.value().castable() && spell.value().level() <= kido)
                .toList();
    }

    /**
     * Its stored choice if it knows it, else the first it knows; empty when it knows none. A choice it no longer
     * knows (a data pack took the spell away, or its level fell) is kept as it is, so it comes back with the spell.
     */
    public static Optional<Holder.Reference<KidoSpell>> chosen(SoulData data, Registry<KidoSpell> spells) {
        List<Holder.Reference<KidoSpell>> known = known(data, spells);
        if (known.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(known.get(Math.max(0, indexOf(known, data.shinigami().kido().chosen()))));
    }

    /** The known spell after {@link #chosen}, wrapping round to the first; empty when it knows none. */
    public static Optional<Holder.Reference<KidoSpell>> next(SoulData data, Registry<KidoSpell> spells) {
        List<Holder.Reference<KidoSpell>> known = known(data, spells);
        if (known.isEmpty()) {
            return Optional.empty();
        }
        int current = Math.max(0, indexOf(known, data.shinigami().kido().chosen()));
        return Optional.of(known.get((current + 1) % known.size()));
    }

    /** Stores the spell this soul casts with the Kidō key. Saved, and sent to its client. */
    public static void choose(SoulData data, ResourceLocation id) {
        ShinigamiProgress progress = data.shinigami();
        data.setShinigami(progress.withKido(progress.kido().withChosen(Optional.of(id))));
    }

    // ---- The gate and the strength ----

    /**
     * {@link Refusal#NEEDS_LEVEL} below the spell's level; {@link Refusal#NEEDS_NAME_LEVEL} cast by name alone, for a
     * spell with an incantation, below its by-name level; else null.
     */
    @Nullable
    public static Refusal gate(KidoSpell spell, Form form, int kidoLevel) {
        if (kidoLevel < spell.level()) {
            return Refusal.NEEDS_LEVEL;
        }
        if (form == Form.NAME && spell.chantable() && kidoLevel < spell.byNameLevel()) {
            return Refusal.NEEDS_NAME_LEVEL;
        }
        return null;
    }

    /**
     * The share of its strength a spell keeps cast this way: all of it chanted, or for a spell with no incantation.
     * By name alone, a spell with one keeps {@code eishohaki_share} at its by-name level and
     * {@code eishohaki_share_per_level} more for each level above, up to {@code eishohaki_max_share} (never below
     * the first).
     */
    public static float share(KidoSpell spell, Form form, int kidoLevel) {
        if (form == Form.CHANT || !spell.chantable()) {
            return 1.0F;
        }
        double base = BleachConfig.get(BleachConfig.KIDO_EISHOHAKI_SHARE);
        double most = Math.max(BleachConfig.get(BleachConfig.KIDO_EISHOHAKI_MAX_SHARE), base);
        double grown = base + BleachConfig.get(BleachConfig.KIDO_EISHOHAKI_SHARE_PER_LEVEL)
                * Math.max(0, kidoLevel - spell.byNameLevel());
        return (float) Math.min(most, grown);
    }

    /**
     * A spell's damage: its own and more for each square root of maximum reiryoku, made stronger by each Kidō level
     * ({@code power_per_level}), up to its cap; then the form's share of that.
     */
    public static float power(KidoSpell spell, SoulData data, float share) {
        KidoSpell.Effect effect = spell.effect();
        double raw = effect.damage() + effect.damagePerRoot() * Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        double grown = raw * (1.0 + BleachConfig.get(BleachConfig.KIDO_POWER_PER_LEVEL) * data.level(Stat.KIDO));
        return (float) (Math.min(effect.maxDamage(), grown) * share);
    }

    /** The reiryoku a cast costs: the spell's share of the maximum, but never less than its least cost. */
    public static float cost(KidoSpell spell, SoulData data) {
        return Math.max(spell.minCost(), spell.costShare() * data.maxReiryoku());
    }

    /** Whether the hands are free to cast again: the last cast's cooldown is over. Server only (the time is transient). */
    public static boolean ready(SoulData data, long now) {
        return now >= data.kidoReadyAt;
    }

    /** As {@link #xp(KidoSpell, Form, boolean)} for a cast by name. */
    public static int xp(KidoSpell spell, boolean struck) {
        return xp(spell, Form.NAME, struck);
    }

    /**
     * The Kidō xp a cast trains: the spell's own, and {@code strike_xp_bonus} more when it struck a creature; all of it
     * {@code chant_xp_multiplier} times over for a chanted cast.
     */
    public static int xp(KidoSpell spell, Form form, boolean struck) {
        int earned = spell.xp() + (struck ? BleachConfig.get(BleachConfig.KIDO_STRIKE_XP_BONUS) : 0);
        return form == Form.CHANT ? earned * BleachConfig.get(BleachConfig.KIDO_CHANT_XP_MULTIPLIER) : earned;
    }

    // ---- The chant ----

    /** The chant this soul is saying, or null. Server only (the field is transient). */
    @Nullable
    public static Chant pending(SoulData data) {
        return data.kidoChant;
    }

    /**
     * Whether a Getsuga Tenshō, a Cero or a Gran Rey Cero is gathering: the reiatsu is already at work elsewhere, which
     * keeps the hands from Kidō (one art at a time). Server only.
     */
    public static boolean otherArtGathering(SoulData data) {
        return data.isGathering() || HollowCero.charging(data) || GranReyCero.charging(data);
    }

    /**
     * A line as matching sees it: decomposed with every mark (a macron, an accent) dropped, in lower case, with only
     * its letters and digits kept (no spaces, so the word division of a romaji never matters), and with a long vowel
     * written out folded to one: an o or a u after an o is dropped, and a u after a u. So "Shakkahō", "Shakkahou",
     * "SHAKKAHOO!!" and "Shakkaho" all read {@code shakkaho}. Both a line and every incantation are folded so, which
     * also folds English ("you" reads {@code yo}) harmlessly. Never throws, and folding a fold changes nothing.
     */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        String bare = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
        StringBuilder folded = new StringBuilder(bare.length());
        int last = -1;
        for (int i = 0; i < bare.length(); ) {
            int point = bare.codePointAt(i);
            i += Character.charCount(point);
            if (!Character.isLetterOrDigit(point) || (last == 'o' && (point == 'o' || point == 'u'))
                    || (last == 'u' && point == 'u')) {
                continue;
            }
            folded.appendCodePoint(point);
            last = point;
        }
        return folded.toString();
    }

    /**
     * Whether this line, whole, is one of the spell's incantations, alone or followed by its call, such as "Hadō #31,
     * Shakkahō!": both folded ({@link #fold}). There is no matching on part of a line, and a line or incantation that
     * folds to nothing never matches. The call is built from the spell's kind, number and id, since the server cannot
     * translate its name.
     */
    public static boolean hears(KidoSpell spell, ResourceLocation id, String line) {
        return hearsFolded(spell, id, fold(line));
    }

    /**
     * The castable spell with an incantation this line is heard as, or empty for none: when several hear it (two
     * spells may share a chant), the chosen one, else the first in {@link KidoSpell#ORDER}. The Kidō level is not
     * asked: a soul below the spell's is refused when it casts.
     */
    public static Optional<Holder.Reference<KidoSpell>> heard(SoulData data, Registry<KidoSpell> spells, String line) {
        String folded = fold(line);
        if (folded.isEmpty()) {
            return Optional.empty();
        }
        Optional<ResourceLocation> chosen = data.shinigami().kido().chosen();
        Holder.Reference<KidoSpell> first = null;
        for (Holder.Reference<KidoSpell> spell : KidoSpell.sorted(spells)) {
            KidoSpell value = spell.value();
            ResourceLocation id = spell.key().location();
            if (!value.castable() || !value.chantable() || !hearsFolded(value, id, folded)) {
                continue;
            }
            if (chosen.isPresent() && chosen.get().equals(id)) {
                return Optional.of(spell);
            }
            if (first == null) {
                first = spell;
            }
        }
        return Optional.ofNullable(first);
    }

    // ---- The misfire ----

    /**
     * The chance a cast by name alone of this spell bursts in the hand at this Kidō level: {@code misfire_chance} at
     * its by-name level, falling evenly to nothing over {@code misfire_levels} levels above it. Nothing for a spell
     * without an incantation, which has none to abandon.
     */
    public static double misfireChance(KidoSpell spell, int kidoLevel) {
        if (!spell.chantable()) {
            return 0.0;
        }
        double above = Math.max(0, kidoLevel - spell.byNameLevel());
        double levels = Math.max(1, BleachConfig.get(BleachConfig.KIDO_MISFIRE_LEVELS));
        return Math.max(0.0, BleachConfig.get(BleachConfig.KIDO_MISFIRE_CHANCE) * (1.0 - above / levels));
    }

    /**
     * What a misfire at this share does to a caster with this health and absorption: {@code misfire_damage_share} of
     * the damage the cast would have done, but never so much that it leaves less than {@link #MISFIRE_FLOOR}, absorption
     * hearts counted, so it may be nothing.
     */
    public static float misfireDamage(KidoSpell spell, SoulData data, float share, float health, float absorption) {
        float burst = (float) (BleachConfig.get(BleachConfig.KIDO_MISFIRE_DAMAGE_SHARE) * power(spell, data, share));
        return VisoredLogic.clampedBlow(burst, health, absorption, MISFIRE_FLOOR);
    }

    // ---- Helpers ----

    /** {@link #hears} for a line already folded. */
    private static boolean hearsFolded(KidoSpell spell, ResourceLocation id, String folded) {
        if (folded.isEmpty()) {
            return false;
        }
        String call = fold(spell.kind().getSerializedName() + spell.number() + id.getPath());
        for (String incantation : spell.incantations()) {
            String chant = fold(incantation);
            if (!chant.isEmpty() && (folded.equals(chant) || folded.equals(chant + call))) {
                return true;
            }
        }
        return false;
    }

    /** Where the spell with this id stands among those known, or -1 when it is not one of them (or there is none). */
    private static int indexOf(List<Holder.Reference<KidoSpell>> known, Optional<ResourceLocation> id) {
        if (id.isPresent()) {
            for (int i = 0; i < known.size(); i++) {
                if (known.get(i).key().location().equals(id.get())) {
                    return i;
                }
            }
        }
        return -1;
    }
}
