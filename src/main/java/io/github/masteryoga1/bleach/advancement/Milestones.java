package io.github.masteryoga1.bleach.advancement;

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
    // Each soul state is also a milestone under its own name: "plus", "hollow" and so on.

    private static final int TRAINED_LEVEL = 5;
    private static final float RISING_REIATSU_AMOUNT = 100.0F;

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
    }

    public static void grant(ServerPlayer player, String milestone) {
        BleachTriggers.SOUL_MILESTONE.get().trigger(player, milestone);
    }
}
