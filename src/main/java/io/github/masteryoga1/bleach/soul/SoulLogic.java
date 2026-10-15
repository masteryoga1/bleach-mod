package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** The rules for spiritual energy and stats. Kept apart from the events so game tests can call them directly. */
public final class SoulLogic {
    public static final ResourceLocation HOHO_SPEED_ID = BleachMod.id("hoho_speed");

    /** How long exhaustion lasts after running out of reiryoku, in ticks. */
    public static final int EXHAUSTION_TICKS = 100;

    private SoulLogic() {
    }

    public static SoulData get(LivingEntity entity) {
        return entity.getData(SoulAttachments.SOUL);
    }

    /** Gives a new soul its starting reiryoku, full. */
    public static void initialize(SoulData data) {
        float start = (float) BleachConfig.get(BleachConfig.STARTING_REIRYOKU);
        data.setMaxReiryoku(start);
        data.setReiryoku(start);
        data.markInitialized();
    }

    /** Whether this soul can perceive spirits: every spirit can, and Humans above the threshold can. */
    public static boolean isAware(SoulData data) {
        return data.state().isSpirit()
                || data.maxReiryoku() >= BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD);
    }

    public static float regenPerTick(SoulData data, boolean resting) {
        double perSecond = data.maxReiryoku() * BleachConfig.get(BleachConfig.REGEN_FRACTION_PER_SECOND);
        perSecond *= 1.0 + data.level(Stat.REIATSU_CONTROL)
                * BleachConfig.get(BleachConfig.REIATSU_CONTROL_REGEN_PER_LEVEL);
        if (resting) {
            perSecond *= BleachConfig.get(BleachConfig.REST_REGEN_MULTIPLIER);
        }
        return (float) (perSecond / 20.0);
    }

    /** Regenerates one tick's worth of reiryoku. */
    public static void regenTick(SoulData data, boolean resting) {
        if (data.reiryoku() < data.maxReiryoku()) {
            data.setReiryoku(data.reiryoku() + regenPerTick(data, resting));
        }
    }

    /**
     * Spends reiryoku if there is enough. Spending the last of it exhausts the soul with Slowness
     * and Weakness.
     *
     * @return false, spending nothing, when there is not enough reiryoku
     */
    public static boolean trySpend(LivingEntity entity, SoulData data, float amount) {
        if (amount <= 0.0F) {
            return true;
        }
        if (data.reiryoku() < amount) {
            return false;
        }
        data.setReiryoku(data.reiryoku() - amount);
        if (data.reiryoku() < 0.5F) {
            data.setReiryoku(0.0F);
            exhaust(entity);
        }
        return true;
    }

    public static void exhaust(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTION_TICKS, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUSTION_TICKS, 0));
    }

    /** Raises maximum reiryoku, up to the configured cap. */
    public static void grow(SoulData data, double amount) {
        if (amount <= 0.0) {
            return;
        }
        double cap = BleachConfig.get(BleachConfig.MAX_REIRYOKU);
        if (data.maxReiryoku() < cap) {
            data.setMaxReiryoku((float) Math.min(cap, data.maxReiryoku() + amount));
        }
    }

    public static int maxStatLevel() {
        return BleachConfig.get(BleachConfig.MAX_STAT_LEVEL);
    }

    /** Adds training XP to a stat. Each level gained grows reiryoku and is announced to players. */
    public static void addStatXp(LivingEntity entity, SoulData data, Stat stat, int amount) {
        int gained = data.addXp(stat, amount, maxStatLevel());
        if (gained <= 0) {
            return;
        }
        grow(data, gained * BleachConfig.get(BleachConfig.LEVEL_UP_GROWTH));
        applyStatModifiers(entity, data);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.bleach.stat_level_up",
                    stat.displayName(), data.level(stat)), true);
        }
    }

    /** Extra damage multiplier for sword hits from Zanjutsu. */
    public static float zanjutsuMultiplier(SoulData data) {
        return (float) (1.0 + data.level(Stat.ZANJUTSU) * BleachConfig.get(BleachConfig.ZANJUTSU_DAMAGE_PER_LEVEL));
    }

    /** Extra flat damage for unarmed hits from Hakuda. */
    public static float hakudaBonus(SoulData data) {
        return (float) (data.level(Stat.HAKUDA) * BleachConfig.get(BleachConfig.HAKUDA_DAMAGE_PER_LEVEL));
    }

    /**
     * Applies the stats that map onto vanilla attributes. The modifiers are transient, so this runs
     * again whenever the player logs in, respawns or a stat changes.
     */
    public static void applyStatModifiers(LivingEntity entity, SoulData data) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        int hoho = data.level(Stat.HOHO);
        if (hoho <= 0) {
            speed.removeModifier(HOHO_SPEED_ID);
        } else {
            speed.addOrUpdateTransientModifier(new AttributeModifier(HOHO_SPEED_ID,
                    hoho * BleachConfig.get(BleachConfig.HOHO_SPEED_PER_LEVEL),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
