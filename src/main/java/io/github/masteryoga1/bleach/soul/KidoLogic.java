package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The rules of Kidō: who may cast, the spells a soul knows by its Kidō level and the one it has chosen, the gate
 * on casting by name alone, and a spell's strength, cost and training. Like {@link ShinigamiLogic}, these only
 * read and change a {@link SoulData}, a spell and the config ({@link #mayCast} also asks the player whether it
 * lives, watches or is held), so game tests can call them directly. Casting itself is {@link KidoEvents#cast}.
 * {@code now} is the level's game time.
 *
 * <p>There is no list of learned spells: the Kidō level alone unlocks a spell, at its {@code level}. A spell
 * with an incantation needs its higher {@code by_name_level} to be cast by name alone (Eishōhaki), and is weaker
 * so; one with none has nothing to abandon and casts by name at full strength.
 */
public final class KidoLogic {
    /** Below this much reiryoku left, a cast is refused, so Kidō never exhausts its caster (Bala's rule). */
    public static final float MIN_LEFT = 0.5F;

    /** How a spell is cast: by name alone with the key (Eishōhaki), or by its whole incantation (6b). */
    public enum Form { NAME, CHANT }

    /** Why a soul may not cast, or choose, now. Those with a {@link #messageKey} are told on the action bar. */
    public enum Refusal {
        /** Dead, a spectator or held by Negación or a Caja; or a spell of no shape, or no server world. Told nothing. */
        UNABLE(null),
        /** Neither a Shinigami's power nor an Academy student, or a Quincy's power in the way. */
        UNTRAINED("message.bleach.kido.untrained"),
        /** Its Hollow holds its body; told the way back, as every sealed Shinigami art is. */
        TAKEN("message.bleach.visored.takeover.sealed"),
        /** Its power is lent to a Human: Kidō will not answer to its name alone. */
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
        TOO_WEAK("message.bleach.kido.too_weak");

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

    private KidoLogic() {
    }

    // ---- Who may cast ----

    /**
     * Null when this soul may cast now, else why not, the first that fails winning: unable (dead, a spectator, or
     * held by Negación or a Caja), untrained (no Shinigami's power and not an Academy student, or an awakened Quincy
     * where hybrids are not allowed), its Hollow holding its body, and its power lent. Server side: on a client the
     * taken-over row is always clear, since {@link VisoredLogic#takenOver} reads a time that is not saved or sent.
     * (6e's page, to grey spells out while taken over, reads {@code ClientSouls.appearance(player).hollowLook()} and
     * {@link HollowPlayerLogic#LOOK_TAKEN_OVER}, as the soul screen does.)
     */
    @Nullable
    public static Refusal mayCast(Player player, SoulData data, long now) {
        if (!player.isAlive() || player.isSpectator() || Negacion.held(player)) {
            return Refusal.UNABLE;
        }
        if (!ShinigamiLogic.spirituallyTrained(data) || QuincyLogic.blocksShinigamiPower(data)) {
            return Refusal.UNTRAINED;
        }
        if (VisoredLogic.takenOver(data)) {
            return Refusal.TAKEN;
        }
        if (ShinigamiLogic.isPowerLent(data, now)) {
            return Refusal.LENT;
        }
        return null;
    }

    /**
     * Whether a soul refused so may still choose its spell: one that may cast, or one kept from casting only for a
     * while, by its Hollow or its lent power (a lender picks the spell it will chant, from 6b).
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

    /** The Kidō xp a cast trains: the spell's own, and {@code strike_xp_bonus} more when it struck a creature. */
    public static int xp(KidoSpell spell, boolean struck) {
        return spell.xp() + (struck ? BleachConfig.get(BleachConfig.KIDO_STRIKE_XP_BONUS) : 0);
    }

    // ---- Helpers ----

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
