package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.config.BleachConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/**
 * An Arrancar's rank, as its saved record caches it (the server's {@link ArrancarRoster} decides it): a
 * Número, numbered from 11 in birth order; an Espada seat, 1 to 10; or a Privaron Espada, 100 to 109, the
 * mark of a seat lost. Pure and client-safe: it reads only the synced soul and the server's settings.
 *
 * <p>It also says where an Espada's number is tattooed. The spot comes from the soul's id, among the canon
 * spots that miss its hole, so every client works out the same one, as it does the hole and the blade; only
 * the number travels, in the Hollow look ({@link #lookBits}).
 *
 * @param numero its Número, 11 to 99, or 0 for none yet
 * @param seat   its Espada seat, 1 to 10; its Privaron Espada number, 100 to 109; or 0 for neither
 */
public record ArrancarRank(int numero, int seat) {
    /** The Espada's seats, Primera (1) to Diez (10): canon's ten. */
    public static final int SEATS = 10;
    /** The first and last Número: two digits, given in birth order. */
    public static final int FIRST_NUMERO = 11;
    public static final int LAST_NUMERO = 99;
    /** A Privaron Espada is numbered this plus the last digit of the seat it lost: 100 to 109. */
    public static final int PRIVARON_BASE = 100;
    /** Not an Arrancar, or one the roster has not seen yet. */
    public static final ArrancarRank NONE = new ArrancarRank(0, 0);

    // Read leniently key by key, so a bad number falls back to none on its own.
    public static final Codec<ArrancarRank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("numero", 0).forGetter(ArrancarRank::numero),
            Codec.INT.lenientOptionalFieldOf("seat", 0).forGetter(ArrancarRank::seat)
    ).apply(instance, ArrancarRank::new));

    /**
     * Body-face rows each hole place takes, top and bottom: the sealed hole's rim wherever SoulLayer shifts it for
     * that place, and the released body's rim, together. Read-only.
     */
    public static final float[][] HOLE_ROWS = {{0.0F, 8.0F}, {2.0F, 8.0F}, {2.0F, 11.0F}};
    /** The spots each hole place allows, in the spots' own order: none crosses the hole. Read-only. */
    public static final Spot[][] SPOTS_BY_HOLE = {spotsMissing(0), spotsMissing(1), spotsMissing(2)};

    /** Anything out of range reads as none: a numero outside 11..99, a seat outside 1..10 and 100..109. */
    public ArrancarRank {
        numero = numero >= FIRST_NUMERO && numero <= LAST_NUMERO ? numero : 0;
        seat = (seat >= 1 && seat <= SEATS) || (seat >= PRIVARON_BASE && seat < PRIVARON_BASE + 10) ? seat : 0;
    }

    /** What sort of rank it is. */
    public enum Kind {
        /** Not an Arrancar, or one without a number while every Número is taken. */
        NONE,
        /** A Número: an Arrancar outside the Espada. */
        NUMERO,
        /** One of the ten Espada. */
        ESPADA,
        /** A Privaron Espada: one that lost its seat and has not won one back. */
        PRIVARON
    }

    /** ESPADA for seats 1..10, PRIVARON for 100..109, else NUMERO with a number, else NONE. */
    public Kind kind() {
        if (seat >= 1 && seat <= SEATS) {
            return Kind.ESPADA;
        }
        if (seat >= PRIVARON_BASE) {
            return Kind.PRIVARON;
        }
        return numero > 0 ? Kind.NUMERO : Kind.NONE;
    }

    /** The number it goes by: the seat (or Privaron number) if it has one, else its Número (0 for none). */
    public int number() {
        return seat > 0 ? seat : numero;
    }

    /**
     * The number tattooed on it: an Espada's seat; a Privaron's lost seat (100 reads as 10); a Número's own
     * number only with {@code [arrancar.ranks] numero_tattoo}; else 0.
     */
    public int tattooNumber() {
        return switch (kind()) {
            case ESPADA -> seat;
            // Nelliel keeps her 3 after losing the seat (Ch. 291); 100 was the Diez's, so it shows 10.
            case PRIVARON -> seat == PRIVARON_BASE ? SEATS : seat - PRIVARON_BASE;
            case NUMERO -> BleachConfig.get(BleachConfig.NUMERO_TATTOO) ? numero : 0;
            case NONE -> 0;
        };
    }

    /**
     * "Número 12" (rank.bleach.numero, arg numero), "Cuatro Espada (4)" (rank.bleach.espada, args seat and
     * seatName(seat)), "Privaron Espada 103" (rank.bleach.privaron, arg seat), or "without a number"
     * (rank.bleach.unnumbered).
     */
    public Component label() {
        return switch (kind()) {
            case ESPADA -> Component.translatable("rank.bleach.espada", seat, seatName(seat));
            case PRIVARON -> Component.translatable("rank.bleach.privaron", seat);
            case NUMERO -> Component.translatable("rank.bleach.numero", numero);
            case NONE -> Component.translatable("rank.bleach.unnumbered");
        };
    }

    // ---- Rules ----

    /** The rank in the soul's record, or NONE for a soul that is not an Arrancar. */
    public static ArrancarRank of(SoulData data) {
        // A record left from an earlier life as an Arrancar, or one a client has not caught up on, counts for nothing.
        return ArrancarLogic.isArrancar(data) ? data.hollow().arrancar().rank() : NONE;
    }

    /** rank.bleach.seat.&lt;1..10&gt;: Primera … Diez. */
    public static Component seatName(int seat) {
        return Component.translatable("rank.bleach.seat." + seat);
    }

    /** 100 + lostSeat % 10 for a lost seat 1..10 (seat 10 gives 100); 0 for anything else. */
    public static int privaronNumber(int lostSeat) {
        return lostSeat >= 1 && lostSeat <= SEATS ? PRIVARON_BASE + lostSeat % 10 : 0;
    }

    /** {@code [arrancar.ranks] espada_min_max_reiryoku}, as a float. */
    public static float espadaMinMaxReiryoku() {
        return (float) BleachConfig.get(BleachConfig.ESPADA_MIN_MAX_REIRYOKU);
    }

    /** Whether this soul may gather a Gran Rey Cero: an Arrancar that is an Espada, or a Privaron with privaron_fires. */
    public static boolean firesGranReyCero(SoulData data) {
        Kind kind = of(data).kind();
        return kind == Kind.ESPADA
                || (kind == Kind.PRIVARON && BleachConfig.get(BleachConfig.GRAN_REY_CERO_PRIVARON_FIRES));
    }

    // ---- The tattoo ----

    /** The tattooed number in the hollowLook bits: (tattooNumber of of(data)) << LOOK_NUMBER_SHIFT, 0 for none. */
    public static int lookBits(SoulData data) {
        int number = of(data).tattooNumber();
        return number <= 0 ? 0 : (number << HollowPlayerLogic.LOOK_NUMBER_SHIFT) & HollowPlayerLogic.LOOK_NUMBER_MASK;
    }

    /** The number back out of a hollowLook value, 0 for none. */
    public static int lookNumber(int hollowLook) {
        return (hollowLook & HollowPlayerLogic.LOOK_NUMBER_MASK) >>> HollowPlayerLogic.LOOK_NUMBER_SHIFT;
    }

    /**
     * The faces of a humanoid model a spot can be on. A spot's {@code a} runs along the face's reading direction
     * and {@code y} downward, in model pixels: the body's faces are 8 wide and 12 tall, and the arm's outer face is
     * 4 wide and 12 tall, from y -2 to 10 about the arm's pivot.
     */
    public enum Face {
        /** The front of the body, read from the player's right to its left. */
        BODY_FRONT,
        /** The back of the body, read from its left to its right. */
        BODY_BACK,
        /** The outer face of the left arm, read from front to back. */
        LEFT_ARM_OUTER
    }

    /** The canon spots: the box a number fills, on one face, from a0 to a1 along it and y0 to y1 down it. */
    public enum Spot {
        /** Ulquiorra's, over the heart (Ch. 271). */
        CHEST_LEFT(Face.BODY_FRONT, 4.4F, 0.25F, 7.8F, 1.95F),
        /** Harribel's (Ch. 339). */
        BREAST_RIGHT(Face.BODY_FRONT, 0.2F, 0.25F, 3.6F, 1.95F),
        /** Luppi's (Ch. 230). */
        HIP_RIGHT(Face.BODY_FRONT, 0.2F, 10.15F, 3.6F, 11.85F),
        /** Grimmjow's (Ch. 240). */
        LOWER_BACK_RIGHT(Face.BODY_BACK, 4.4F, 10.15F, 7.8F, 11.85F),
        /** Nelliel's, large across the back (Ch. 291). */
        MID_BACK(Face.BODY_BACK, 1.5F, 8.3F, 6.5F, 11.7F),
        /** Yammy's (MASKED). */
        SHOULDER_LEFT(Face.LEFT_ARM_OUTER, 0.2F, -1.7F, 3.8F, 1.3F),
        /** Starrk's, on the back of the left hand (Ch. 339). */
        HAND_LEFT(Face.LEFT_ARM_OUTER, 0.2F, 6.7F, 3.8F, 9.7F);

        public final Face face;
        public final float a0, y0, a1, y1;

        Spot(Face face, float a0, float y0, float a1, float y1) {
            this.face = face;
            this.a0 = a0;
            this.y0 = y0;
            this.a1 = a1;
            this.y1 = y1;
        }

        /** rank.bleach.spot.&lt;lower-case name&gt;, a phrase such as "on your left shoulder". */
        public Component phrase() {
            return Component.translatable("rank.bleach.spot." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** SPOTS_BY_HOLE[ArrancarLogic.holePlace(id)][floorMod(id.getLeastSignificantBits() >>> 20, length)]. */
    public static Spot tattooSpot(UUID id) {
        Spot[] spots = SPOTS_BY_HOLE[ArrancarLogic.holePlace(id)];
        // Shifted off the lowest bits, so the spot does not simply follow the blade (the low half, mod 12); the
        // hole comes from the high half.
        return spots[Math.floorMod(id.getLeastSignificantBits() >>> 20, spots.length)];
    }

    /**
     * The spots, in their own order, whose rows miss the rows this hole place takes: every arm spot, since the
     * hole is on the body, and every body spot wholly above or below it.
     */
    private static Spot[] spotsMissing(int holePlace) {
        float[] rows = HOLE_ROWS[holePlace];
        List<Spot> spots = new ArrayList<>();
        for (Spot spot : Spot.values()) {
            if (spot.face == Face.LEFT_ARM_OUTER || spot.y1 < rows[0] || spot.y0 > rows[1]) {
                spots.add(spot);
            }
        }
        return spots.toArray(Spot[]::new);
    }
}
