package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The rules of the Arrancar: a Hollow that has torn off its own mask and sealed its Hollow power in a blade.
 * Who may tear at its mask and how likely it is to come away, what an Arrancar is worth by the Menos form it
 * rose from, what its Resurrección gives and costs, its blade, hole and look, and which stacks are its blade.
 * Like {@link HollowPlayerLogic}, these read only their arguments and the config, send nothing and are safe
 * on both sides, so game tests and the client can call them directly. What they do to a player is in
 * {@link ArrancarEvents} and {@link ResurreccionEvents}.
 *
 * <p>The form an Arrancar rose from is not saved apart: it is the soul's {@link PowerPath#HOLLOW} tier, read
 * while the soul is an Arrancar and never below a Gillian's, so {@code /bleach tier} sets it too. The blade,
 * the hole and the shape of the mask fragment all come from the soul's id, so nothing more is saved for them
 * and every client works out the same ones.
 */
public final class ArrancarLogic {
    /** Maximum health an Arrancar gains, sealed or released. Saved, as the health is cut to the maximum on loading. */
    public static final ResourceLocation HEALTH_ID = BleachMod.id("arrancar_health");
    /** Walking speed a released Arrancar gains. */
    public static final ResourceLocation SPEED_ID = BleachMod.id("arrancar_speed");
    /** What a released Arrancar's melee damage is multiplied by, blade or claws. */
    public static final ResourceLocation DAMAGE_ID = BleachMod.id("arrancar_damage");
    /** Where the hole may be: 0 at the throat, 1 at the sternum, 2 at the belly. */
    public static final int HOLE_PLACES = 3;

    private static final Blade[] BLADES = Blade.values();

    private ArrancarLogic() {
    }

    // ---- The state ----

    /** Whether this soul is an Arrancar. */
    public static boolean isArrancar(SoulData data) {
        return data.state() == SoulState.ARRANCAR;
    }

    /** Whether this player is an Arrancar. Never gives a player without one a soul. */
    public static boolean isArrancarPlayer(Player player) {
        return SoulLogic.hasSoul(player) && isArrancar(SoulLogic.get(player));
    }

    /** Whether this soul is an Arrancar in Resurrección. Only the server knows: a client's copy reads false. */
    public static boolean released(SoulData data) {
        return isArrancar(data) && data.isResurreccionActive();
    }

    // ---- The form it rose from ----

    /** The form an Arrancar rose from, at least a Gillian; a plain Hollow for any other soul. */
    public static HollowTier origin(SoulData data) {
        return isArrancar(data) ? originFor(data) : HollowTier.HOLLOW;
    }

    /**
     * The form this soul would rise from as an Arrancar, whatever it is now: its Hollow tier, at least a
     * Gillian, so a plain Hollow or a soul with no Hollow tier counts as Gillian-born.
     */
    public static HollowTier originFor(SoulData data) {
        return HollowTier.byTier(Math.max(HollowTier.GILLIAN.tier(), data.tier(PowerPath.HOLLOW)));
    }

    /**
     * The Hollow body a released Arrancar takes back, for its claws and their speed (its {@link Hierro} takes
     * the hide's place): the form it rose from, but a plain Hollow's for a Gillian-born one, as a Gillian's
     * are a giant's. A plain Hollow for any soul not released.
     */
    public static HollowTier releasedBody(SoulData data) {
        if (!released(data)) {
            return HollowTier.HOLLOW;
        }
        HollowTier origin = origin(data);
        return origin == HollowTier.GILLIAN ? HollowTier.HOLLOW : origin;
    }

    /**
     * The form whose Cero this soul gathers: an Arrancar's origin, a Hollow's own form, and a masked Visored with the
     * training, a Gillian.
     */
    public static HollowTier ceroForm(SoulData data) {
        if (isArrancar(data)) {
            return origin(data);
        }
        // A masked Visored whose masks have been worn long enough gathers a Gillian's Cero (see VisoredLogic#maskedCero).
        return VisoredLogic.maskedCero(data) ? HollowTier.GILLIAN : HollowTier.of(data);
    }

    // ---- Numbers by origin ----

    /** The config section for an Arrancar of this origin; a plain Hollow reads the Gillian-born one. */
    public static BleachConfig.ArrancarOrigin numbers(HollowTier origin) {
        return switch (origin) {
            case VASTO_LORDE -> BleachConfig.VASTO_LORDE_ARRANCAR;
            case ADJUCHAS -> BleachConfig.ADJUCHAS_ARRANCAR;
            case HOLLOW, GILLIAN -> BleachConfig.GILLIAN_ARRANCAR;
        };
    }

    /** Maximum health an Arrancar gains: its released bonus while released, else its sealed one; 0 for anyone else. */
    public static double healthBonus(SoulData data) {
        if (!isArrancar(data)) {
            return 0.0;
        }
        BleachConfig.ArrancarOrigin numbers = numbers(origin(data));
        return BleachConfig.get(released(data) ? numbers.releasedHealthBonus() : numbers.sealedHealthBonus());
    }

    /** The share of walking speed a released Arrancar gains; 0 for anyone not released. */
    public static double speedBonus(SoulData data) {
        return released(data) ? BleachConfig.get(numbers(origin(data)).releasedSpeedBonus()) : 0.0;
    }

    /** What a released Arrancar's melee damage is multiplied by; 1 for anyone not released. */
    public static double damageMultiplier(SoulData data) {
        return released(data) ? BleachConfig.get(numbers(origin(data)).releasedDamageMultiplier()) : 1.0;
    }

    /** The sealed blade's damage for an Arrancar of this origin. */
    public static float bladeDamage(HollowTier origin) {
        return (float) BleachConfig.get(numbers(origin).bladeDamage());
    }

    /** The share of maximum reiryoku becoming an Arrancar of this origin adds, once per soul. */
    public static double reiryokuGrowth(HollowTier origin) {
        return BleachConfig.get(numbers(origin).reiryokuGrowth());
    }

    /**
     * Reiryoku a Resurrección drains each tick: the origin's share of the maximum each second, shrunk by
     * Reiatsu Control as a Shikai's drain is.
     */
    public static float releaseDrainPerTick(SoulData data) {
        double perSecond = BleachConfig.get(numbers(origin(data)).releaseDrainPerSecond());
        return (float) (Math.max(0.0F, data.maxReiryoku()) * perSecond * ZanpakutoLogic.drainFraction(data) / 20.0);
    }

    /** What a Cero's damage is multiplied by: more in Resurrección, after the cap on a Cero's damage. */
    public static float ceroMultiplier(SoulData data) {
        return released(data) ? (float) BleachConfig.get(BleachConfig.RESURRECCION_CERO_MULTIPLIER) : 1.0F;
    }

    // ---- Tearing the mask ----

    /** Whether a Hollow of this form has the will to tear off its own mask: an Adjuchas or a Vasto Lorde. */
    public static boolean canTear(HollowTier form) {
        return form == HollowTier.ADJUCHAS || form == HollowTier.VASTO_LORDE;
    }

    /**
     * The chance a try at tearing the mask succeeds: the form's own, and more for each earlier failure, so no
     * Adjuchas is shut out for good. 0 for a form that cannot tear.
     */
    public static double tearChance(HollowTier form, int failures) {
        if (!canTear(form)) {
            return 0.0;
        }
        double base = BleachConfig.get(form == HollowTier.VASTO_LORDE
                ? BleachConfig.VASTO_TEAR_CHANCE : BleachConfig.ADJUCHAS_TEAR_CHANCE);
        double chance = base + BleachConfig.get(BleachConfig.TEAR_CHANCE_PER_FAILURE) * Math.max(0, failures);
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /** Why this soul may not start tearing at its mask now, the first reason in order; null when it may. */
    @Nullable
    public static TearRefusal tearRefusal(SoulData data, long now) {
        // HollowTier.of reads a plain Hollow for any soul that is not a Hollow.
        HollowTier form = HollowTier.of(data);
        if (form != HollowTier.GILLIAN && !canTear(form)) {
            return TearRefusal.NOT_MENOS;
        }
        if (form == HollowTier.GILLIAN) {
            return TearRefusal.MINDLESS;
        }
        if (!BleachConfig.get(BleachConfig.NATURAL_ARRANCAR)) {
            return TearRefusal.OFF;
        }
        if (minutesToRetry(data, now) > 0) {
            return TearRefusal.WAITING;
        }
        if (secondsToCalm(data, now) > 0) {
            return TearRefusal.FIGHTING;
        }
        if (data.reiryoku() < BleachConfig.get(BleachConfig.TEAR_MIN_REIRYOKU_FRACTION) * data.maxReiryoku()) {
            return TearRefusal.TOO_WEAK;
        }
        if (data.devourTargetId >= 0 || HollowCero.charging(data)) {
            return TearRefusal.BUSY;
        }
        return null;
    }

    /** Whole seconds, rounded up, before this Hollow has been unhurt long enough to tear at its mask; 0 once it has. */
    public static int secondsToCalm(SoulData data, long now) {
        long left = BleachConfig.get(BleachConfig.TEAR_CALM_SECONDS) * 20L - (now - data.hollowHurtAt);
        return (int) Math.max(0L, ceilDiv(left, 20L));
    }

    /** Whole minutes, rounded up, before this soul may try at its mask again after a failure; 0 once it may. */
    public static int minutesToRetry(SoulData data, long now) {
        long left = BleachConfig.get(BleachConfig.TEAR_RETRY_MINUTES) * (long) MenosEvolution.MINUTE_TICKS
                - (now - data.hollow().arrancar().lastTearAt());
        return (int) Math.max(0L, ceilDiv(left, MenosEvolution.MINUTE_TICKS));
    }

    // ---- What comes from the soul's id ----

    /** The blade this soul's torn mask becomes: the same in every session and on every client. */
    public static Blade blade(UUID id) {
        return BLADES[Math.floorMod(id.getLeastSignificantBits(), BLADES.length)];
    }

    /** Where this soul's hole is: 0 at the throat, 1 at the sternum, 2 at the belly. */
    public static int holePlace(UUID id) {
        return Math.floorMod(id.getMostSignificantBits(), HOLE_PLACES);
    }

    /**
     * The look bits an Arrancar's release adds: the released form, and a Vasto Lorde-born one's bone. Never a
     * Gillian's or an Adjuchas's bit, which would draw a Menos body. 0 for anyone not released.
     */
    public static int lookBits(SoulData data) {
        if (!released(data)) {
            return 0;
        }
        int look = HollowPlayerLogic.LOOK_RESURRECCION;
        return origin(data) == HollowTier.VASTO_LORDE ? look | HollowPlayerLogic.LOOK_VASTO_LORDE : look;
    }

    // ---- Which stacks count ----

    /** Whether this is an Arrancar's blade, owned or not. */
    public static boolean isArrancarBlade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.ARRANCAR_BLADE.get());
    }

    /** Whether this is an Arrancar's blade forged for this player, whatever its forging. */
    public static boolean isOwnBlade(ItemStack stack, Player player) {
        return isArrancarBlade(stack) && ZanpakutoItem.isOwnedBy(stack, player.getUUID());
    }

    /** Which forging of its owner's blade this stack is, or 0 if it has none. */
    public static int forging(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ARRANCAR_FORGING.get(), 0);
    }

    /** Rounds a positive quotient up, and leaves anything at or below zero as it is. */
    private static long ceilDiv(long ticks, long per) {
        return ticks <= 0L ? ticks : (ticks + per - 1L) / per;
    }

    /** Why a Hollow may not start tearing at its mask, in the order they are asked. Each is told ({@link #messageKey}). */
    public enum TearRefusal {
        /** Not a Menos of the classes that can: an Adjuchas or a Vasto Lorde. */
        NOT_MENOS,
        /** A Gillian has no will of its own to tear with. */
        MINDLESS,
        /** The server does not let Hollows tear off their own masks. */
        OFF,
        /** The mask has not settled since the last try. */
        WAITING,
        /** Hurt too lately. */
        FIGHTING,
        /** Not nearly all its reiryoku. */
        TOO_WEAK,
        /** Devouring, or gathering a Cero. */
        BUSY;

        /** {@code message.bleach.arrancar.tear.<name>}. */
        public String messageKey() {
            return "message.bleach.arrancar.tear." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The blades a torn mask becomes: a Spanish animal's name, called out with a command in English after it
     * ("Dive, Halcón!"), and a colour for the name and the release.
     */
    public enum Blade implements StringRepresentable {
        /** Halcón, the falcon. */
        HALCON("halcon", 0xD9A441),
        /** Cuervo, the raven. */
        CUERVO("cuervo", 0x3B3B5C),
        /** Jaguar. */
        JAGUAR("jaguar", 0xC88A2E),
        /** Escorpión, the scorpion. */
        ESCORPION("escorpion", 0xB5462A),
        /** Avispa, the wasp. */
        AVISPA("avispa", 0xE3C21E),
        /** Lechuza, the barn owl. */
        LECHUZA("lechuza", 0xD8D2C0),
        /** Hiena, the hyena. */
        HIENA("hiena", 0x9A7B4F),
        /** Buitre, the vulture. */
        BUITRE("buitre", 0x6E5A4A),
        /** Mantarraya, the manta ray. */
        MANTARRAYA("mantarraya", 0x3E78A8),
        /** Carcayú, the wolverine. */
        CARCAYU("carcayu", 0x5C4033),
        /** Caimán, the caiman. */
        CAIMAN("caiman", 0x4F7A3A),
        /** Polilla, the moth. */
        POLILLA("polilla", 0xB9A8D6);

        private final String id;
        private final int colour;

        Blade(String id, int colour) {
            this.id = id;
            this.colour = colour;
        }

        /** The blade's RGB colour. */
        public int colour() {
            return colour;
        }

        /** The blade's name, in its colour. */
        public Component displayName() {
            return Component.translatable("arrancar_blade.bleach." + id).withColor(colour);
        }

        /** The command called out before the name to release it. */
        public Component command() {
            return Component.translatable("arrancar_blade.bleach." + id + ".command");
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
