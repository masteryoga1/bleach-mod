package io.github.masteryoga1.bleach.advancement;

import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.Stat;
import net.minecraft.server.level.ServerPlayer;

/** The milestone names advancements can ask for, and the checks that grant them. */
public final class Milestones {
    /** Any of the four Zankensoki arts at level 5. */
    public static final String TRAINED = "trained";
    /** 100 maximum reiryoku. */
    public static final String RISING_REIATSU = "rising_reiatsu";
    /** Released reiatsu weighed down a weaker creature. Granted when it happens, not by {@link #check}. */
    public static final String CRUSHING_PRESSURE = "crushing_pressure";
    /** Down to the last links of the Chain of Fate. */
    public static final String ENCROACHMENT = "encroachment";
    /** Studying at the Academy. */
    public static final String ENROLLED = "enrolled";
    /** A living Human holding a Shinigami's power. */
    public static final String SUBSTITUTE = "substitute";
    /** Wearing the full Shihakushō. */
    public static final String SHIHAKUSHO = "shihakusho";
    /** Passed the Academy's exams. Granted when it happens, as are the three below. */
    public static final String GRADUATED = "graduated";
    /** Sent a Plus on. */
    public static final String KONSO = "konso";
    /** Flash stepped. */
    public static final String SHUNPO = "shunpo";
    /** Stood on hardened reishi in mid-air. */
    public static final String REISHI_FOOTING = "reishi_footing";
    /** Saw a Garganta open. Granted when it happens, as are the three below. */
    public static final String GARGANTA = "garganta";
    /** Lived through the first Hollow, Fishbone D. */
    public static final String FIRST_HOLLOW = "first_hollow";
    /** Struck a Hollow's mask. */
    public static final String MASK_STRIKE = "mask_strike";
    /** Purified Fishbone D. */
    public static final String FISHBONE_D = "fishbone_d";
    /** Purified a Hollow with a zanpakutō. Granted when it happens and by {@link #check}. */
    public static final String PURIFIED = "purified";
    /** Sent ten Pluses on. */
    public static final String SOUL_BURIAL = "soul_burial";
    // Each soul state is also a milestone under its own name: "plus", "hollow", "rukongai" and so on.

    private static final int TRAINED_LEVEL = 5;
    private static final float RISING_REIATSU_AMOUNT = 100.0F;
    private static final int SOUL_BURIAL_COUNT = 10;

    private Milestones() {
    }

    /** Grants every milestone the soul currently meets. Cheap: already-earned criteria are skipped. */
    public static void check(ServerPlayer player, SoulData data) {
        SoulMilestoneTrigger trigger = BleachTriggers.SOUL_MILESTONE.get();
        trigger.trigger(player, data.state().getSerializedName());
        for (Stat stat : Stat.values()) {
            if (stat != Stat.REIATSU_CONTROL && data.level(stat) >= TRAINED_LEVEL) {
                trigger.trigger(player, TRAINED);
                break;
            }
        }
        if (data.maxReiryoku() >= RISING_REIATSU_AMOUNT) {
            trigger.trigger(player, RISING_REIATSU);
        }
        if (SoulLogic.isEncroaching(data)) {
            trigger.trigger(player, ENCROACHMENT);
        }
        if (ShinigamiLogic.isStudent(data)) {
            trigger.trigger(player, ENROLLED);
        }
        if (ShinigamiLogic.isSubstitute(data)) {
            trigger.trigger(player, SUBSTITUTE);
        }
        if (ShihakushoItem.isFullSet(player)) {
            trigger.trigger(player, SHIHAKUSHO);
        }
        HuntRecord hunt = data.hunt();
        if (hunt.purified() >= 1) {
            trigger.trigger(player, PURIFIED);
        }
        if (hunt.sentOn() >= SOUL_BURIAL_COUNT) {
            trigger.trigger(player, SOUL_BURIAL);
        }
    }

    public static void grant(ServerPlayer player, String milestone) {
        BleachTriggers.SOUL_MILESTONE.get().trigger(player, milestone);
    }
}
