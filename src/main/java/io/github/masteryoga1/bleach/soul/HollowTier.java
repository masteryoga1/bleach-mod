package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.HollowKind;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The forms of a Hollow player: a plain Hollow, then the three classes of Menos Grande it rises through
 * by devouring (see {@link MenosEvolution}). The form is the soul's {@link PowerPath#HOLLOW} tier, which
 * is already saved, sent to its owner and set by {@code /bleach tier}, so the form needs no flag of its
 * own; it is read only for a soul that is a Hollow, so a Shinigami who kept a Hollow tier is never a
 * Menos. Each Menos form reads its numbers from its own section of the server config; a plain Hollow
 * keeps the numbers of the Hollow body as they were before the Menos.
 */
public enum HollowTier implements StringRepresentable {
    HOLLOW("hollow"),
    /** The commonest Menos Grande: a towering, mindless giant made of countless fused souls. */
    GILLIAN("gillian"),
    /** A Menos whose own will has won out over the souls within: smaller, quick, and always hungry. */
    ADJUCHAS("adjuchas"),
    /** The rarest Menos: human-sized again, with a captain's strength. */
    VASTO_LORDE("vasto_lorde");

    /** What an Adjuchas risks when it stops devouring Hollows (the server config's {@code hollow_evolution.regression}). */
    public enum Regression {
        /** It sinks back into a Gillian and may rise again, for more Hollows each time. */
        SOFT,
        /** It sinks back into a Gillian for good, as in canon. */
        CANON,
        /** It never sinks back. */
        OFF
    }

    private static final HollowTier[] BY_TIER = values();

    private final String name;

    HollowTier(String name) {
        this.name = name;
    }

    /** The form of this soul: its Hollow tier, from 1 to 4, while it is a Hollow, and a plain Hollow for any other soul. */
    public static HollowTier of(SoulData data) {
        return HollowPlayerLogic.isHollow(data) ? byTier(data.tier(PowerPath.HOLLOW)) : HOLLOW;
    }

    /** The form of this Hollow tier, a tier out of range counting as the nearest form. */
    public static HollowTier byTier(int tier) {
        return BY_TIER[Mth.clamp(tier, 1, BY_TIER.length) - 1];
    }

    /** The {@link PowerPath#HOLLOW} tier of this form, from 1 to 4. */
    public int tier() {
        return ordinal() + 1;
    }

    /** Whether this form is a Menos Grande: anything but a plain Hollow. */
    public boolean isMenos() {
        return this != HOLLOW;
    }

    /** The {@link SoulAppearance} look bit that draws this form, or 0 for a plain Hollow. */
    public int lookBit() {
        return switch (this) {
            case HOLLOW -> 0;
            case GILLIAN -> HollowPlayerLogic.LOOK_GILLIAN;
            case ADJUCHAS -> HollowPlayerLogic.LOOK_ADJUCHAS;
            case VASTO_LORDE -> HollowPlayerLogic.LOOK_VASTO_LORDE;
        };
    }

    /** This form's section of the server config, or null for a plain Hollow, whose numbers are the Hollow body's own. */
    @Nullable
    public BleachConfig.MenosForm form() {
        return switch (this) {
            case HOLLOW -> null;
            case GILLIAN -> BleachConfig.GILLIAN_FORM;
            case ADJUCHAS -> BleachConfig.ADJUCHAS_FORM;
            case VASTO_LORDE -> BleachConfig.VASTO_LORDE_FORM;
        };
    }

    /**
     * The class of Menos this form is, as a meal (see {@link DevourLogic#meal}): a Vasto Lorde counts as
     * an Adjuchas, the highest class among the mobs. Null for a plain Hollow.
     */
    @Nullable
    public HollowKind menosKind() {
        return switch (this) {
            case HOLLOW -> null;
            case GILLIAN -> HollowKind.GILLIAN;
            case ADJUCHAS, VASTO_LORDE -> HollowKind.ADJUCHAS;
        };
    }

    /** How many times a person's size this form stands. */
    public double scale() {
        return read(BleachConfig.MenosForm::scale, 1.0);
    }

    /** Maximum health this form adds. */
    public double healthBonus() {
        return read(BleachConfig.MenosForm::healthBonus, 0.0);
    }

    /** What this form's walking speed is multiplied by. */
    public double speedMultiplier() {
        return read(BleachConfig.MenosForm::speedMultiplier, 1.0);
    }

    /** What the claws' damage is multiplied by. */
    public double clawMultiplier() {
        return read(BleachConfig.MenosForm::clawMultiplier, 1.0);
    }

    /** Bare-handed blows a second. */
    public double clawAttacksPerSecond() {
        BleachConfig.MenosForm form = form();
        return form == null ? BleachConfig.get(BleachConfig.CLAW_ATTACKS_PER_SECOND)
                : BleachConfig.get(form.clawAttacksPerSecond());
    }

    /** Armour this form adds on top of the hide. */
    public double hideBonus() {
        return read(BleachConfig.MenosForm::hideBonus, 0.0);
    }

    /** What the regeneration is multiplied by. */
    public double regenerationMultiplier() {
        return read(BleachConfig.MenosForm::regenerationMultiplier, 1.0);
    }

    /** Blocks this form steps up beyond a person's step. */
    public double stepBonus() {
        return read(BleachConfig.MenosForm::stepBonus, 0.0);
    }

    /** The share of maximum reiryoku that reaching this form for the first time adds. */
    public double reiryokuGrowth() {
        return read(BleachConfig.MenosForm::reiryokuGrowth, 0.0);
    }

    public Component displayName() {
        return Component.translatable("hollow_form.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The configured number for a Menos form, or the plain Hollow's own. */
    private double read(Function<BleachConfig.MenosForm, ModConfigSpec.DoubleValue> key, double plain) {
        BleachConfig.MenosForm form = form();
        return form == null ? plain : BleachConfig.get(key.apply(form));
    }
}
