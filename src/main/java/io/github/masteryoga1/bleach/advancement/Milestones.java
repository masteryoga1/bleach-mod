package io.github.masteryoga1.bleach.advancement;

import io.github.masteryoga1.bleach.item.QuincyGarbItem;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.quincy.QuincyStage;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.QuincyArts;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyTeaching;
import io.github.masteryoga1.bleach.soul.SanreiProgress;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
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
    /** Sat in Jinzen until the world inside the blade opened. */
    public static final String JINZEN = "jinzen";
    /** Bonded with an Asauchi until its voice came through clearly. */
    public static final String ASAUCHI_BOND = "asauchi_bond";
    /** Passed the spirit's test and learned the zanpakutō's name. */
    public static final String ZANPAKUTO_NAME = "zanpakuto_name";
    /** Released a zanpakutō for the first time. */
    public static final String SHIKAI = "shikai";
    /** Loosed enough Getsuga Tenshō that Zangetsu named the technique. */
    public static final String GETSUGA_TENSHO = "getsuga_tensho";
    /** Awakened the Quincy power: Quincy tier 1. */
    public static final String QUINCY = "quincy";
    /** Struck a spirit with a Heilig Pfeil. Granted when it happens, as are the four below. */
    public static final String HEILIG_PFEIL = "heilig_pfeil";
    /** Rode a current of reishi with Hirenkyaku. */
    public static final String HIRENKYAKU = "hirenkyaku";
    /** Called on Blut Vene. */
    public static final String BLUT_VENE = "blut_vene";
    /** Called on Blut Arterie. */
    public static final String BLUT_ARTERIE = "blut_arterie";
    /** Let Hollow poison reach halfway. */
    public static final String HOLLOW_POISON = "hollow_poison";
    /** Wearing the full Quincy garb. */
    public static final String QUINCY_GARB = "quincy_garb";
    /** Reached the third Quincy tier, the highest. */
    public static final String LAST_QUINCY = "last_quincy";
    /** Put on the Sanrei Glove (and still counted once it has been torn off). */
    public static final String SANREI_GLOVE = "sanrei_glove";
    /** Trained in the Sanrei Glove on enough days to master it. */
    public static final String SANREI_MASTERED = "sanrei_mastered";
    /** Tore the mastered glove off and burned the power out in Letzt Stil (and counted ever after). */
    public static final String LETZT_STIL = "letzt_stil";
    /** Won the sealed power back as Ginrei Kojaku. */
    public static final String RESTORED = "restored";
    /** Cast a spell from a Gintō. Granted when it happens, as are the five below. */
    public static final String GINTO = "ginto";
    /** Rained a volley of arrows from Ginrei Kojaku. */
    public static final String LICHT_REGEN = "licht_regen";
    /** Formed a Seele Schneider's blade. */
    public static final String SEELE_SCHNEIDER = "seele_schneider";
    /** Set off five Seele Schneider with Sprenger. */
    public static final String SPRENGER = "sprenger";
    /** Took the full Quincy form. */
    public static final String VOLLSTANDIG = "vollstandig";
    /** Bent the reishi around them to their will with Sklaverei. */
    public static final String SKLAVEREI = "sklaverei";
    /** Crushed a Hollow Bait, or had a site opened by command. Granted when it happens, as are the three below. */
    public static final String HOLLOW_BAIT = "hollow_bait";
    /** Was hunted at a bait when the sky above it cracked, and saw it through. */
    public static final String SKY_CRACKED = "sky_cracked";
    /** Saw a bait through to the cracked sky, with ten Hollows come and no Plus devoured. */
    public static final String BAIT_NO_LOSS = "bait_no_loss";
    /** Beat the young Quincy Hunter at his contest. */
    public static final String RIVAL_BESTED = "rival_bested";
    /** Talked with the Quincy Elder. Granted when it happens and by {@link #check}, as is the one below. */
    public static final String ELDER_MET = "elder_met";
    /** Learned Ransōtengai in the Elder's trial. */
    public static final String RANSOTENGAI = "ransotengai";
    /** Wearing the Wandenreich coat, trousers and boots. */
    public static final String WANDENREICH = "wandenreich";
    /** Devoured a soul as a Hollow. Granted when it happens and by {@link #check}, as is the one below. */
    public static final String DEVOURED = "devoured";
    /** Devoured a Hollow, mob or player. */
    public static final String CANNIBAL = "cannibal";
    /** Holds {@link HollowPlayerLogic#SOULS_WITHIN} devoured souls. */
    public static final String SOULS_WITHIN = "souls_within";
    /** A Hollow cleansed back into a soul, by the Academy's instructor or a Shinigami's blade. Granted when it happens. */
    public static final String CLEANSED = "cleansed";
    /**
     * Bested a Menos Grande: killed, purified or devoured a Gillian or Adjuchas, or drove a Gillian back into the sky.
     * Granted when it happens.
     */
    public static final String MENOS_GRANDE = "menos_grande";
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
        ZanpakutoBond bond = data.zanpakuto();
        if (bond.jinzens() > 0) {
            trigger.trigger(player, JINZEN);
        }
        if (bond.isFull()) {
            trigger.trigger(player, ASAUCHI_BOND);
        }
        if (bond.named()) {
            trigger.trigger(player, ZANPAKUTO_NAME);
        }
        if (bond.firstReleaseDone()) {
            trigger.trigger(player, SHIKAI);
        }
        if (bond.getsugaNamed()) {
            trigger.trigger(player, GETSUGA_TENSHO);
        }
        int quincyTier = QuincyLogic.tier(data);
        if (quincyTier >= 1) {
            trigger.trigger(player, QUINCY);
        }
        if (quincyTier >= QuincyLogic.MAX_TIER) {
            trigger.trigger(player, LAST_QUINCY);
        }
        if (QuincyGarbItem.isFullSet(player)) {
            trigger.trigger(player, QUINCY_GARB);
        }
        if (QuincyGarbItem.isWandenreichSet(player)) {
            trigger.trigger(player, WANDENREICH);
        }
        // Read from the record, so a soul taught or met by command counts too.
        QuincyTeaching teaching = data.quincy().teaching();
        if (teaching.elderMet()) {
            trigger.trigger(player, ELDER_MET);
        }
        if (teaching.ransotengaiKnown()) {
            trigger.trigger(player, RANSOTENGAI);
        }
        QuincyArts arts = data.quincy().arts();
        SanreiProgress glove = arts.glove();
        // Every later stage comes through the glove, so it counts for a soul set past it by command too.
        if (glove.worn() || glove.mastered() || arts.stage() != QuincyStage.UNTOUCHED) {
            trigger.trigger(player, SANREI_GLOVE);
        }
        if (glove.mastered()) {
            trigger.trigger(player, SANREI_MASTERED);
        }
        if (arts.stage() != QuincyStage.UNTOUCHED) {
            trigger.trigger(player, LETZT_STIL);
        }
        if (arts.stage() == QuincyStage.RESTORED) {
            trigger.trigger(player, RESTORED);
        }
        // Read from the record, so souls set by command count too.
        HollowProgress hollow = data.hollow();
        if (hollow.meals() > 0) {
            trigger.trigger(player, DEVOURED);
        }
        if (hollow.totalHollows() > 0) {
            trigger.trigger(player, CANNIBAL);
        }
        if (hollow.souls() >= HollowPlayerLogic.SOULS_WITHIN) {
            trigger.trigger(player, SOULS_WITHIN);
        }
    }

    public static void grant(ServerPlayer player, String milestone) {
        BleachTriggers.SOUL_MILESTONE.get().trigger(player, milestone);
    }
}
