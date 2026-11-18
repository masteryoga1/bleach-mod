package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.item.AcademyUniformItem;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The rules of the Shinigami path: the Academy and its exams, Konsō, the Substitute route, Shunpo
 * and reishi footing. Like {@link SoulLogic}, these only read and change a {@link SoulData} and the
 * config, so game tests can call them directly. {@code now} is the level's game time.
 */
public final class ShinigamiLogic {
    private static final int TICKS_PER_MINUTE = 1200;
    private static final long TICKS_PER_DAY = 24000L;
    /** However much Hohō a soul has, reishi footing never costs less than this share of its drain. */
    private static final double FOOTING_DRAIN_FLOOR = 0.25;
    private static final String[] COMPASS = {
            "north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

    private ShinigamiLogic() {
    }

    // ---- The path ----

    /**
     * Whether this soul holds a Shinigami's power: a Shinigami, or anyone with a Shinigami tier. A
     * Hollow's Shinigami power is lost under the mask, and an Arrancar's stays lost when the mask comes
     * away, as its power is a Hollow's; a Visored (a Shinigami with a Hollow inside, see VisoredLogic) keeps it.
     */
    public static boolean hasShinigamiPower(SoulData data) {
        return data.state() != SoulState.HOLLOW && data.state() != SoulState.ARRANCAR
                && (data.state() == SoulState.SHINIGAMI || data.tier(PowerPath.SHINIGAMI) > 0);
    }

    /** A living Human holding a Shinigami's power. */
    public static boolean isSubstitute(SoulData data) {
        return data.state() == SoulState.HUMAN && data.tier(PowerPath.SHINIGAMI) > 0;
    }

    /** A Rukongai soul studying at the Academy. */
    public static boolean isStudent(SoulData data) {
        return data.state() == SoulState.RUKONGAI && data.shinigami().enrolled();
    }

    /** Whether this Shinigami gave their power to a Human and has not got it back yet. */
    public static boolean isPowerLent(SoulData data, long now) {
        return now < data.shinigami().powerLentUntil();
    }

    /**
     * The last part of the {@code screen.bleach.soul.next.*} key that says what this soul can do next.
     * A Shinigami, Substitute or student whose Asauchi bond is full hears {@code .bonded} (sit in
     * Jinzen); a Shinigami or Substitute who knows the name hears {@code .named} (release it). A
     * Quincy who has passed on and whom the Academy will not take hears {@code quincy.rukongai}. A
     * Hollow hears {@code hollow} until it has devoured a soul, and {@code hollow.fed} after. A soul with
     * Shinigami power and a Hollow rooted in it hears about that Hollow instead, until it is beaten, and
     * then about its mask, until it first wears it (see {@link VisoredLogic#nextStepKey}).
     */
    public static String nextStepKey(SoulData data, long now) {
        if (isPowerLent(data, now)) {
            return "lent";
        }
        String visored = VisoredLogic.nextStepKey(data, now);
        if (visored != null) {
            return visored;
        }
        return switch (data.state()) {
            case HUMAN -> isSubstitute(data) ? "substitute" + bondStep(data.zanpakuto()) : QuincyLogic.nextStepKey(data, now);
            case RUKONGAI -> !data.shinigami().enrolled()
                    ? (QuincyLogic.blocksShinigamiPower(data) ? "quincy.rukongai" : "rukongai")
                    : data.zanpakuto().isFull() && !data.zanpakuto().named() ? "student.bonded" : "student";
            case SHINIGAMI -> "shinigami" + bondStep(data.zanpakuto());
            case HOLLOW -> HollowPlayerLogic.nextStepKey(data);
            default -> data.state().getSerializedName();
        };
    }

    /** {@code .named} once the name is known, {@code .bonded} once the bond is full, and nothing before. */
    private static String bondStep(ZanpakutoBond bond) {
        if (bond.named()) {
            return ".named";
        }
        return bond.isFull() ? ".bonded" : "";
    }

    /** The {@code message.bleach.direction.*} key for the compass point from here towards {@code dx, dz}. */
    public static String compassKey(double dx, double dz) {
        // North is towards negative Z, east towards positive X.
        int index = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 4.0)), COMPASS.length);
        return "message.bleach.direction." + COMPASS[index];
    }

    /** A distance rounded to the nearest 50 blocks, and never less than 50, for a soul's rough sense of it. */
    public static long roundedDistance(double distance) {
        return roundedDistance(distance, 50L);
    }

    /** A distance rounded to the nearest {@code step} blocks, and never less than one step. */
    public static long roundedDistance(double distance, long step) {
        return Math.max(step, Math.round(distance / step) * step);
    }

    // ---- The Academy ----

    /** A Rukongai soul not yet enrolled, unless it is an awakened Quincy (see {@link QuincyLogic#blocksShinigamiPower}). */
    public static boolean canEnroll(SoulData data) {
        return data.state() == SoulState.RUKONGAI && !data.shinigami().enrolled()
                && !QuincyLogic.blocksShinigamiPower(data);
    }

    public static void enroll(SoulData data) {
        data.setShinigami(data.shinigami().withEnrolled(true));
    }

    /** The level each of the four Zankensoki must reach for a student to graduate. */
    public static Map<Stat, Integer> graduationRequirements() {
        Map<Stat, Integer> requirements = new EnumMap<>(Stat.class);
        requirements.put(Stat.ZANJUTSU, BleachConfig.get(BleachConfig.GRADUATION_ZANJUTSU_LEVEL));
        requirements.put(Stat.HAKUDA, BleachConfig.get(BleachConfig.GRADUATION_HAKUDA_LEVEL));
        requirements.put(Stat.HOHO, BleachConfig.get(BleachConfig.GRADUATION_HOHO_LEVEL));
        requirements.put(Stat.KIDO, BleachConfig.get(BleachConfig.GRADUATION_KIDO_LEVEL));
        return requirements;
    }

    /** The arts this soul would still fail at the exams, in Zankensoki order. */
    public static List<Stat> graduationShortfall(SoulData data) {
        List<Stat> missing = new ArrayList<>();
        graduationRequirements().forEach((art, level) -> {
            if (data.level(art) < level) {
                missing.add(art);
            }
        });
        return missing;
    }

    public static boolean canGraduate(SoulData data) {
        return isStudent(data) && graduationShortfall(data).isEmpty();
    }

    /** The student passes: they become a Shinigami, their power awakens and their reiryoku grows and refills. */
    public static void graduate(SoulData data) {
        data.setState(SoulState.SHINIGAMI);
        data.clearChain();
        data.setShinigami(data.shinigami().withEnrolled(false));
        awaken(data, BleachConfig.get(BleachConfig.AWAKENING_GROWTH));
    }

    /** Whether enough time has passed since the Academy last handed this soul an Asauchi. */
    public static boolean canReissueAsauchi(SoulData data, long now) {
        return now - data.shinigami().asauchiIssuedAt()
                >= (long) BleachConfig.get(BleachConfig.ASAUCHI_REISSUE_MINUTES) * TICKS_PER_MINUTE;
    }

    // ---- Konsō ----

    /** Whether this creature's presence slows a nearby Plus's chain: a living Shinigami NPC, or a player with Shinigami power. */
    public static boolean slowsErosion(Entity entity) {
        if (entity instanceof ShinigamiNpc npc) {
            return npc.isAlive();
        }
        return entity instanceof Player player && SoulLogic.hasSoul(player) && hasShinigamiPower(SoulLogic.get(player));
    }

    /** Anyone with a Shinigami's power can send a Plus on, unless they gave that power away. */
    public static boolean canPerformKonso(SoulData data, long now) {
        return hasShinigamiPower(data) && !isPowerLent(data, now);
    }

    /**
     * Konsō: the Plus's chain falls away and its soul passes on, becoming a Rukongai soul. It keeps
     * its reiryoku, stats and powers.
     *
     * @return false, changing nothing, if the soul is not a Plus
     */
    public static boolean konso(SoulData plus) {
        if (plus.state() != SoulState.PLUS) {
            return false;
        }
        SoulLogic.changeState(plus, SoulState.RUKONGAI);
        plus.setReiatsuReleased(false);
        return true;
    }

    /**
     * Grows the performer's reiryoku for a Konsō, up to the daily limit. The count starts over on
     * each new game day.
     *
     * @return whether this Konsō was rewarded
     */
    public static boolean rewardKonso(SoulData performer, long now) {
        long day = Math.floorDiv(now, TICKS_PER_DAY);
        ShinigamiProgress progress = performer.shinigami();
        if (progress.konsoDay() != day) {
            progress = progress.withKonsoDay(day).withKonsoRewards(0);
        }
        boolean rewarded = progress.konsoRewards() < BleachConfig.get(BleachConfig.KONSO_REWARDED_PER_DAY);
        if (rewarded) {
            progress = progress.withKonsoRewards(progress.konsoRewards() + 1);
            SoulLogic.grow(performer, BleachConfig.get(BleachConfig.KONSO_GROWTH));
        }
        performer.setShinigami(progress);
        return rewarded;
    }

    // ---- The Substitute route ----

    /** A Shinigami strong enough to spare their power: twice a new soul's reiryoku, and none of it lent already. */
    public static boolean canGivePower(SoulData giver, long now) {
        return giver.state() == SoulState.SHINIGAMI && !isPowerLent(giver, now)
                && giver.maxReiryoku() >= 2.0 * BleachConfig.get(BleachConfig.STARTING_REIRYOKU);
    }

    /**
     * Only a living Human who can sense spirits, and whose Shinigami power has not awakened yet. An
     * awakened Quincy's soul will not take it (see {@link QuincyLogic#blocksShinigamiPower}).
     */
    public static boolean canReceivePower(SoulData receiver) {
        return receiver.state() == SoulState.HUMAN && SoulLogic.isAware(receiver)
                && receiver.tier(PowerPath.SHINIGAMI) <= 0 && !QuincyLogic.blocksShinigamiPower(receiver);
    }

    /**
     * The giver's power flows out: part of their maximum reiryoku is gone until the loan ends, and
     * what they had left is spent.
     *
     * @return the maximum reiryoku lent
     */
    public static float lend(SoulData giver, long now) {
        float lent = (float) (giver.maxReiryoku() * BleachConfig.get(BleachConfig.TRANSFER_FRACTION));
        giver.setMaxReiryoku(giver.maxReiryoku() - lent);
        giver.setReiryoku(0.0F);
        long until = now + (long) BleachConfig.get(BleachConfig.GIVER_RECOVERY_MINUTES) * TICKS_PER_MINUTE;
        giver.setShinigami(giver.shinigami()
                .withPowerLentUntil(until)
                .withLentReiryoku(giver.shinigami().lentReiryoku() + lent)
                .withGavePowerAt(now));
        return lent;
    }

    /** A soul's Shinigami power awakens: its first tier, more reiryoku, and a full refill. */
    public static void awaken(SoulData data, double growth) {
        if (data.tier(PowerPath.SHINIGAMI) <= 0) {
            data.setTier(PowerPath.SHINIGAMI, 1);
        }
        SoulLogic.grow(data, growth);
        data.setReiryoku(data.maxReiryoku());
    }

    /**
     * A Shinigami pours their power into a Human. The Human's own power awakens, and grows by what
     * was lent, up to the same amount graduation gives, so a strong giver cannot raise a new soul
     * past an Academy graduate.
     *
     * @return how much the receiver's maximum reiryoku grew by
     */
    public static float transferFromShinigami(SoulData giver, SoulData receiver, long now) {
        float lent = lend(giver, now);
        float gain = (float) Math.min(BleachConfig.get(BleachConfig.AWAKENING_GROWTH), lent);
        float before = receiver.maxReiryoku();
        awaken(receiver, gain);
        return receiver.maxReiryoku() - before;
    }

    /**
     * Gives a giver their power back once the loan is over.
     *
     * @return whether it came back just now
     */
    public static boolean endLoanIfDue(SoulData data, long now) {
        ShinigamiProgress progress = data.shinigami();
        boolean onLoan = progress.powerLentUntil() > 0L || progress.lentReiryoku() > 0.0F;
        if (!onLoan || now < progress.powerLentUntil()) {
            return false;
        }
        double cap = BleachConfig.get(BleachConfig.MAX_REIRYOKU);
        data.setMaxReiryoku((float) Math.min(cap, data.maxReiryoku() + progress.lentReiryoku()));
        data.setShinigami(progress.withPowerLentUntil(0L).withLentReiryoku(0.0F));
        return true;
    }

    /** Whether a wounded Shinigami could find this Human: aware, strong enough, and not met one lately. */
    public static boolean canMeetWoundedShinigami(SoulData data, long now) {
        return BleachConfig.get(BleachConfig.WOUNDED_ENCOUNTERS) && canReceivePower(data)
                && data.maxReiryoku() >= BleachConfig.get(BleachConfig.ENCOUNTER_MIN_REIRYOKU)
                && now - data.shinigami().woundedMetAt()
                        >= (long) BleachConfig.get(BleachConfig.ENCOUNTER_COOLDOWN_MINUTES) * TICKS_PER_MINUTE;
    }

    // ---- Regeneration ----

    /**
     * How fast this soul regenerates compared with normal: faster in the full Shihakushō for those it suits, or in the
     * Academy's whole uniform for a student, once even in both; slower while its power is lent, while Hollow poison is
     * in a Quincy's blood, or while a Hollow is ravenous or starving.
     */
    public static double regenMultiplier(LivingEntity entity, SoulData data, long now) {
        double multiplier = 1.0;
        if ((garbBonusApplies(data) && ShihakushoItem.isFullSet(entity))
                || (isStudent(data) && AcademyUniformItem.isFullSet(entity))) {
            multiplier *= 1.0 + BleachConfig.get(BleachConfig.SHIHAKUSHO_SET_REGEN_BONUS);
        }
        if (isPowerLent(data, now)) {
            multiplier *= BleachConfig.get(BleachConfig.LENT_REGEN_MULTIPLIER);
        }
        return multiplier * QuincyLogic.poisonRegenMultiplier(data) * HollowPlayerLogic.reiryokuRegenMultiplier(data);
    }

    /** Whether the Shihakushō's set bonus works for this soul: Shinigami power, or an Academy student. */
    public static boolean garbBonusApplies(SoulData data) {
        return hasShinigamiPower(data) || isStudent(data);
    }

    // ---- Shunpo ----

    /** A Shinigami's power, or an Academy student: who may learn the Shinigami arts (Shunpo, reishi footing and Kidō). */
    static boolean spirituallyTrained(SoulData data) {
        return hasShinigamiPower(data) || isStudent(data);
    }

    public static boolean canFlashStep(SoulData data, long now) {
        return spirituallyTrained(data) && !isPowerLent(data, now)
                && data.level(Stat.HOHO) >= BleachConfig.get(BleachConfig.SHUNPO_MIN_HOHO_LEVEL);
    }

    /**
     * How far a flash step goes, in blocks. Slowness shortens it by a quarter per level.
     *
     * @param slownessAmplifier the Slowness amplifier on the soul, or -1 for none
     */
    public static double flashStepDistance(SoulData data, int slownessAmplifier) {
        double distance = Math.min(BleachConfig.get(BleachConfig.SHUNPO_MAX_DISTANCE),
                BleachConfig.get(BleachConfig.SHUNPO_BASE_DISTANCE)
                        + data.level(Stat.HOHO) * BleachConfig.get(BleachConfig.SHUNPO_DISTANCE_PER_HOHO_LEVEL));
        if (slownessAmplifier >= 0) {
            distance *= Math.max(0.0, 1.0 - 0.25 * (slownessAmplifier + 1));
        }
        return distance;
    }

    /** How far this soul's Hohō has come from the first flash step (0) to mastery (1). */
    private static double flashStepMastery(SoulData data) {
        int start = BleachConfig.get(BleachConfig.SHUNPO_MIN_HOHO_LEVEL);
        int mastery = BleachConfig.get(BleachConfig.SHUNPO_MASTERY_HOHO_LEVEL);
        if (mastery <= start) {
            return 1.0;
        }
        return Mth.clamp((data.level(Stat.HOHO) - start) / (double) (mastery - start), 0.0, 1.0);
    }

    /** Reiryoku a flash step costs: a share of the maximum that falls with Hohō, and at least 1. */
    public static float flashStepCost(SoulData data) {
        double fraction = Mth.lerp(flashStepMastery(data), BleachConfig.get(BleachConfig.SHUNPO_COST_FRACTION),
                BleachConfig.get(BleachConfig.SHUNPO_MASTER_COST_FRACTION));
        return (float) Math.max(1.0, fraction * data.maxReiryoku());
    }

    public static int flashStepCooldownTicks(SoulData data) {
        return (int) Math.round(Mth.lerp(flashStepMastery(data), BleachConfig.get(BleachConfig.SHUNPO_COOLDOWN_TICKS),
                BleachConfig.get(BleachConfig.SHUNPO_MASTER_COOLDOWN_TICKS)));
    }

    // ---- Reishi footing ----

    /** Whether this soul can stand on reishi footing: as a Shinigami (see {@link #shinigamiFooting}) or as a Quincy. */
    public static boolean canUseReishiFooting(SoulData data, long now) {
        return shinigamiFooting(data, now) || QuincyLogic.canStandOnReishi(data);
    }

    /** Whether this soul can stand on reishi footing the Shinigami way, paying in reiryoku: Hohō, and its power not lent. */
    public static boolean shinigamiFooting(SoulData data, long now) {
        return spirituallyTrained(data) && !isPowerLent(data, now)
                && data.level(Stat.HOHO) >= BleachConfig.get(BleachConfig.FOOTING_MIN_HOHO_LEVEL);
    }

    /** Reiryoku reishi footing drains each tick: a share of the maximum that falls with Hohō, down to a quarter. */
    public static float footingDrainPerTick(SoulData data) {
        double reduction = Math.max(FOOTING_DRAIN_FLOOR,
                1.0 - data.level(Stat.HOHO) * BleachConfig.get(BleachConfig.FOOTING_HOHO_DRAIN_REDUCTION_PER_LEVEL));
        return (float) (data.maxReiryoku() * BleachConfig.get(BleachConfig.FOOTING_DRAIN_FRACTION_PER_SECOND)
                * reduction / 20.0);
    }

    /** The block height reishi footing hardens at for feet at {@code feetY}: the block just under them. */
    public static int footingAnchor(double feetY) {
        return Mth.floor(feetY) - 1;
    }

    // ---- The sheath ----

    /**
     * Whether a player carries a zanpakutō on their hotbar without holding it, so it is drawn
     * sheathed at their hip.
     */
    public static boolean hasSheathedBlade(LivingEntity entity) {
        return sheathedModel(entity) >= 0;
    }

    /**
     * Which blade a player wears sheathed: the {@code custom_model_data} of the first zanpakutō on
     * their hotbar (0 for a plain Asauchi, or an Arrancar's blade), so others see Zabimaru's red saya or
     * Zangetsu wrapped across the back. -1 when neither hand is free of a blade or the hotbar holds none.
     */
    public static int sheathedModel(LivingEntity entity) {
        if (!(entity instanceof Player player)
                || sheathable(player.getMainHandItem()) || sheathable(player.getOffhandItem())) {
            return -1;
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (sheathable(stack)) {
                CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
                return model == null ? 0 : Math.max(0, model.value());
            }
        }
        return -1;
    }

    /** Whether this stack is a blade worn sheathed: a zanpakutō, or an Arrancar's blade. */
    private static boolean sheathable(ItemStack stack) {
        return stack.is(BleachTags.ZANPAKUTO) || stack.is(ModItems.ARRANCAR_BLADE.get());
    }
}
