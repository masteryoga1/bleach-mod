package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.BleachTags;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The rules for playing as a Hollow: the bands of soul hunger, what the claws, hide and regeneration
 * are worth at a soul's reiryoku, what a death costs, and what a Hollow cannot wear, eat or trade
 * for. Like {@link SoulLogic}, these read only their arguments and the config, send nothing and are
 * safe on both sides, so game tests can call them directly. What they do to a player is in
 * {@link HollowBody}. Every rule for a Hollow player asks {@link #isHollow}, so the states that count
 * as one are decided in one place. An Arrancar is no Hollow to them: only the rules that ask
 * {@link #isHollowOrArrancar} (the villagers', the Cero's) count it as one, and, in Resurrección, the claws
 * it takes back ({@link #wearsHollowBody}, {@link #bodyForm}), with its Hierro in the hide's place; its own
 * rules are in {@link ArrancarLogic}. A Menos form changes the claws, hide and regeneration through
 * {@link HollowTier}'s factors, inside these same rules.
 */
public final class HollowPlayerLogic {
    /** How hungry a Hollow is. Lower in the list is hungrier. Never sent or saved. */
    public enum Band {
        /** Fed: full regeneration and reiryoku. */
        FED,
        /** Below the ravenous mark: slower healing and reiryoku, sharper claws. */
        RAVENOUS,
        /** No soul hunger left: no healing, no sprinting, and hurt as far as the difficulty allows. */
        STARVING
    }

    // The SoulAppearance.hollowLook bits.
    /** The jaw of the mask hangs open: the Hollow is devouring. */
    public static final int LOOK_DEVOURING = 1;
    /** The mask is cracked from a blow. */
    public static final int LOOK_MASK_CRACKED = 2;
    /** The eyes burn red: the Hollow is ravenous or starving. */
    public static final int LOOK_RAVENOUS = 4;
    /** The Hollow is a Gillian, drawn as one. */
    public static final int LOOK_GILLIAN = 8;
    /** The Hollow is an Adjuchas, drawn as one. */
    public static final int LOOK_ADJUCHAS = 16;
    /** The Hollow is a Vasto Lorde: a person's body with bone over it. */
    public static final int LOOK_VASTO_LORDE = 32;
    /** A Cero is gathering at the Menos's mouth. */
    public static final int LOOK_CERO_CHARGING = 64;
    /** With {@link #LOOK_DEVOURING}: the prey is a Plus, so the hold is a Plus's (its owner's devour bar reads it). */
    public static final int LOOK_DEVOURING_PLUS = 128;
    /** An Arrancar in Resurrección: its Hollow skin and full mask (with {@link #LOOK_VASTO_LORDE}, a Vasto Lorde's bone). */
    public static final int LOOK_RESURRECCION = 256;
    /** How many mask shapes there are: 0 skull, 1 fanged, 2 horned, 3 beaked. */
    public static final int MASK_VARIANTS = 4;
    /** The food level a starving Hollow is held at: exactly the client's own limit for sprinting. */
    public static final int STARVING_FOOD_LEVEL = 6;
    /** Souls held within for the Legion Within advancement. */
    public static final int SOULS_WITHIN = 100;
    /** Soul counts at which the souls within stir, and the Hollow is told so. */
    public static final int[] SOUL_STIRRINGS = {25, 50, 100, 200};
    /** A hungry Hollow smells no soul nearer than this, which it could see for itself. */
    public static final double SCENT_MIN_DISTANCE = 12.0;
    /** The most armour any body bears: the attribute's own cap, which a Menos hide may reach. */
    private static final double MOST_ARMOUR = 30.0;

    private HollowPlayerLogic() {
    }

    /** Whether this soul is a Hollow, for every rule of the Hollow body and hunger. */
    public static boolean isHollow(SoulData data) {
        return data.state() == SoulState.HOLLOW;
    }

    /** Whether this player is a Hollow. Never gives a player without one a soul. */
    public static boolean isHollowPlayer(Player player) {
        return SoulLogic.hasSoul(player) && isHollow(SoulLogic.get(player));
    }

    /** Whether this soul is a Hollow or an Arrancar: to the living, and to its Cero, still a Hollow. */
    public static boolean isHollowOrArrancar(SoulData data) {
        return isHollow(data) || ArrancarLogic.isArrancar(data);
    }

    /**
     * Whether this soul wears the Hollow body, its claws and leap (and, for a Hollow, its hide): a Hollow, or an
     * Arrancar in Resurrección.
     */
    public static boolean wearsHollowBody(SoulData data) {
        return isHollow(data) || ArrancarLogic.released(data);
    }

    /**
     * The form whose claws and claw speed this soul's body has (and whose hide a Hollow's has): a Hollow's own, or
     * the body a released Arrancar takes back (see {@link ArrancarLogic#releasedBody}), with its Hierro in the
     * hide's place. Never the regeneration's, which an Arrancar has traded away.
     */
    public static HollowTier bodyForm(SoulData data) {
        return ArrancarLogic.released(data) ? ArrancarLogic.releasedBody(data) : HollowTier.of(data);
    }

    /** Whether the player plays by survival's rules: in survival or adventure mode. */
    public static boolean survivalRules(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    /** The band for this much soul hunger. A hunger not yet started counts as fed. */
    public static Band band(float satiety) {
        if (satiety < 0.0F) {
            return Band.FED;
        }
        if (satiety <= 0.0F) {
            return Band.STARVING;
        }
        return satiety < BleachConfig.get(BleachConfig.RAVENOUS_BELOW) ? Band.RAVENOUS : Band.FED;
    }

    /** The band of a soul's saved hunger. */
    private static Band band(SoulData data) {
        return band(data.hollow().satiety());
    }

    /**
     * Damage the claws add to a bare-handed blow: a base and a share of the square root of maximum
     * reiryoku, up to a cap, sharper still while ravenous or starving, and multiplied by the Menos form's
     * claws.
     */
    public static float clawBonus(SoulData data) {
        double root = Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        double claws = Math.min(BleachConfig.get(BleachConfig.CLAW_DAMAGE_MAX),
                BleachConfig.get(BleachConfig.CLAW_DAMAGE_BASE) + BleachConfig.get(BleachConfig.CLAW_DAMAGE_PER_SQRT) * root);
        if (band(data) != Band.FED) {
            claws *= BleachConfig.get(BleachConfig.RAVENOUS_CLAW_MULTIPLIER);
        }
        return (float) (claws * bodyForm(data).clawMultiplier());
    }

    /** The attack speed modifier that makes bare-handed blows come at the configured rate, from vanilla's four a second. */
    public static double clawSpeedModifier() {
        return BleachConfig.get(BleachConfig.CLAW_ATTACKS_PER_SECOND) / 4.0 - 1.0;
    }

    /** {@link #clawSpeedModifier()} at the rate of this soul's form: a Gillian swings slower, an Adjuchas as fast. */
    public static double clawSpeedModifier(SoulData data) {
        return bodyForm(data).clawAttacksPerSecond() / 4.0 - 1.0;
    }

    /**
     * Armour the hide gives: a base and a share of the square root of maximum reiryoku, up to a cap, and
     * the Menos form's own on top, up to the most armour any body bears. None for an Arrancar, released or
     * not: an Arrancar has Hierro instead (see {@link Hierro}).
     */
    public static float hideArmour(SoulData data) {
        if (ArrancarLogic.isArrancar(data)) {
            return 0.0F;
        }
        double root = Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        double hide = Math.min(BleachConfig.get(BleachConfig.HIDE_ARMOR_MAX),
                BleachConfig.get(BleachConfig.HIDE_ARMOR_BASE) + BleachConfig.get(BleachConfig.HIDE_ARMOR_PER_SQRT) * root);
        return (float) Math.min(MOST_ARMOUR, hide + bodyForm(data).hideBonus());
    }

    /**
     * Health a Hollow regenerates a second: a base and more for every hundred maximum reiryoku, up to a
     * cap, slower while ravenous and none while starving, and multiplied by the Menos form's healing.
     */
    public static float regenPerSecond(SoulData data) {
        Band band = band(data);
        if (band == Band.STARVING) {
            return 0.0F;
        }
        double perSecond = Math.min(BleachConfig.get(BleachConfig.HOLLOW_REGEN_MAX),
                BleachConfig.get(BleachConfig.HOLLOW_REGEN_BASE)
                        + BleachConfig.get(BleachConfig.HOLLOW_REGEN_PER_100) * Math.max(0.0F, data.maxReiryoku()) / 100.0);
        if (band == Band.RAVENOUS) {
            perSecond *= BleachConfig.get(BleachConfig.RAVENOUS_REGEN_MULTIPLIER);
        }
        return (float) (perSecond * HollowTier.of(data).regenerationMultiplier());
    }

    /** How fast reiryoku refills for this soul (see ShinigamiLogic#regenMultiplier): slower for a hungry Hollow. */
    public static double reiryokuRegenMultiplier(SoulData data) {
        if (isHollow(data) && data.hollow().hungerStarted() && band(data) != Band.FED) {
            return BleachConfig.get(BleachConfig.HUNGRY_REIRYOKU_MULTIPLIER);
        }
        return 1.0;
    }

    /**
     * Whether starving hurts at this health, with vanilla's floors for starving: never on Peaceful,
     * down to half the hearts on Easy and to half a heart on Normal, and to death on Hard.
     */
    public static boolean starves(Difficulty difficulty, float health) {
        return switch (difficulty) {
            case PEACEFUL -> false;
            case EASY -> health > 10.0F;
            case NORMAL -> health > 1.0F;
            case HARD -> true;
        };
    }

    /**
     * How much one starving hurt takes at this health: the configured damage, but on Easy and Normal never
     * more than takes the health to the floor, or one point where that is less, as vanilla's one point does.
     * So only Hard starves a Hollow to death, whatever the configured damage.
     */
    public static float starvingHurt(Difficulty difficulty, float health, float damage) {
        return switch (difficulty) {
            case EASY -> Math.min(damage, Math.max(1.0F, health - 10.0F));
            case NORMAL -> Math.min(damage, Math.max(1.0F, health - 1.0F));
            default -> damage;
        };
    }

    /** Soul hunger lost each tick doing nothing. */
    public static float drainPerTick() {
        return (float) (BleachConfig.get(BleachConfig.SATIETY_DRAIN_PER_MINUTE) / 1200.0);
    }

    /** Soul hunger the effort behind this much vanilla exhaustion costs. */
    public static float satietyFromExhaustion(float exhaustion) {
        return (float) (exhaustion * BleachConfig.get(BleachConfig.SATIETY_PER_EXHAUSTION));
    }

    /** The hunger a Hollow comes back from death with: what it had, but never below the floor; the start if it had none. */
    public static float respawnSatiety(HollowProgress old) {
        double had = old.hungerStarted() ? old.satiety() : BleachConfig.get(BleachConfig.SATIETY_START);
        return (float) Math.max(had, BleachConfig.get(BleachConfig.SATIETY_RESPAWN_FLOOR));
    }

    /** The souls a Hollow loses when it dies, rounded down: those its devourer takes, if anything devoured it. */
    public static int soulsLostOnDeath(int souls) {
        return (int) Math.floor(Math.max(0, souls) * BleachConfig.get(BleachConfig.DEATH_SOUL_LOSS_FRACTION));
    }

    /** The highest of {@link #SOUL_STIRRINGS} passed in going from {@code before} to {@code after} souls, or 0. */
    public static int stirring(int before, int after) {
        int passed = 0;
        for (int mark : SOUL_STIRRINGS) {
            if (before < mark && mark <= after) {
                passed = Math.max(passed, mark);
            }
        }
        return passed;
    }

    /**
     * Whether a Hollow's hide refuses this item in this slot: armour, for a Hollow player under survival's
     * rules, unless the server lets Hollows wear it or the item is one a Hollow can always wear (elytra,
     * carved pumpkins and heads). Runs on both sides, so the inventory screen agrees with the server.
     */
    public static boolean refusesArmour(LivingEntity entity, ItemStack stack, @Nullable EquipmentSlot slot) {
        return slot != null && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && entity instanceof Player player
                && survivalRules(player) && !BleachConfig.get(BleachConfig.HOLLOW_WEARS_ARMOR)
                && !stack.is(BleachTags.HOLLOW_WEARABLE) && isHollowPlayer(player);
    }

    /** Whether a Hollow cannot eat or drink this: food of any kind, unless a pack lets Hollows have it. */
    public static boolean cannotConsume(ItemStack stack, LivingEntity eater) {
        return stack.getFoodProperties(eater) != null && !stack.is(BleachTags.HOLLOW_EDIBLE);
    }

    /**
     * Whether villagers and wandering traders turn away from this player: a Hollow or an Arrancar, which to the
     * living is a Hollow still, under survival's rules.
     */
    public static boolean shunnedByVillagers(Player player) {
        return !BleachConfig.get(BleachConfig.VILLAGERS_TRADE_WITH_HOLLOWS) && survivalRules(player)
                && SoulLogic.hasSoul(player) && isHollowOrArrancar(SoulLogic.get(player));
    }

    /** Which shape of mask a Hollow player wears: the same on every client and in every session. */
    public static int maskVariant(UUID id) {
        return Math.floorMod(id.hashCode(), MASK_VARIANTS);
    }

    /**
     * The look bits the soul's saved data gives, so its owner's client can work them out too: red eyes
     * while hungry, and the Menos form.
     */
    public static int hollowLook(SoulData data) {
        int look = isHollow(data) && data.hollow().hungerStarted() && band(data) != Band.FED ? LOOK_RAVENOUS : 0;
        return look | HollowTier.of(data).lookBit();
    }

    /**
     * Every look bit, with those only the server knows: the jaw open while devouring (and whether the
     * prey is a Plus, whose hold has its own length), the mask cracked after a blow (or while it is torn at),
     * a Cero gathering, and an Arrancar's Resurrección (see {@link ArrancarLogic#lookBits}). On a client's copy
     * those are at their defaults and add nothing.
     */
    public static int hollowLook(SoulData data, long now) {
        int look = hollowLook(data) | ArrancarLogic.lookBits(data);
        if (data.devourTargetId >= 0) {
            look |= data.devourPlus ? LOOK_DEVOURING | LOOK_DEVOURING_PLUS : LOOK_DEVOURING;
        }
        if (now < data.hollowCrackedUntil) {
            look |= LOOK_MASK_CRACKED;
        }
        if (data.ceroBeamId >= 0) {
            look |= LOOK_CERO_CHARGING;
        }
        return look;
    }

    /**
     * The soul screen's next step for a Hollow: how to feed before the first meal and what feeding builds
     * after, and for a Menos what its form needs to rise, or why it rises no further.
     */
    public static String nextStepKey(SoulData data) {
        HollowProgress.Evolution evolution = data.hollow().evolution();
        return switch (HollowTier.of(data)) {
            case HOLLOW -> data.hollow().meals() > 0 ? "hollow.fed" : "hollow";
            case GILLIAN -> BleachConfig.get(BleachConfig.ADJUCHAS_REGRESSION) == HollowTier.Regression.CANON
                    && evolution.regressions() >= 1 ? "hollow.gillian.lost" : "hollow.gillian";
            case ADJUCHAS -> switch (evolution.stall()) {
                case HollowProgress.Evolution.STALL_UNDESTINED -> "hollow.adjuchas.stalled";
                case HollowProgress.Evolution.STALL_FULL -> "hollow.adjuchas.full";
                default -> "hollow.adjuchas";
            };
            case VASTO_LORDE -> "hollow.vasto_lorde";
        };
    }
}
