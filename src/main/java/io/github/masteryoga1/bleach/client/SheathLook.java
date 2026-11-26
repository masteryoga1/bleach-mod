package io.github.masteryoga1.bleach.client;

import javax.annotation.Nullable;

/**
 * How a sheathed zanpakutō looks, chosen by the model number of the blade (its
 * {@code custom_model_data}, see {@code ShinigamiLogic.sheathedModel}). Most blades hang at the hip
 * in a saya; Zangetsu has none and is carried across the back, wrapped in white cloth. Colours are
 * plain RGB.
 *
 * @param onBack    carried across the back instead of at the hip
 * @param saya      the scabbard, or for a blade on the back the cloth wrapped round it
 * @param fitting   the guard, the end caps and the bands round a wrapped blade
 * @param tsuka     the hilt wrap
 * @param length    how long the scabbard (or the wrapped blade) is, in blocks
 * @param thickness how deep the scabbard is, or how broad the wrapped blade, in blocks
 */
public record SheathLook(boolean onBack, int saya, int fitting, int tsuka, float length, float thickness) {
    /** The plain Asauchi: a black saya, gold fittings and a dark blue-grey hilt. */
    public static final SheathLook ASAUCHI = new SheathLook(false, 0x16161A, 0xC49C3A, 0x28243E, 0.70F, 0.09F);
    /** Zabimaru, sealed or released: a dark red saya, bronze fittings and a red hilt. */
    public static final SheathLook ZABIMARU = new SheathLook(false, 0x5A1418, 0xAA783C, 0x962424, 0.72F, 0.09F);
    /** Zangetsu in Shikai: the broad cleaver across the back, wrapped in white cloth with the hilt over the shoulder. */
    public static final SheathLook ZANGETSU = new SheathLook(true, 0xECE8DC, 0xBEB8AA, 0xECE8DC, 1.25F, 0.30F);

    /** The look for a sheathed blade of this model number, or null for -1 (no blade sheathed) and a Bankai's. */
    @Nullable
    public static SheathLook forModel(int model) {
        if (model < 0) {
            return null;
        }
        return switch (model) {
            case 1 -> ZANGETSU;
            case 2, 3 -> ZABIMARU;
            // Out of hand it is back in Shikai within a second, and has no sheath until then.
            case 4, 5 -> null;
            default -> ASAUCHI;
        };
    }
}
