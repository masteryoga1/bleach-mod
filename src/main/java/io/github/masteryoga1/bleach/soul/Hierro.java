package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Hierro, the Arrancar's steel skin: reiatsu packed into the body so hard that a blow from a soul no stronger
 * than the Arrancar loses part of its force. Every Arrancar has it, sealed or released, with no key and no cost.
 * The share it turns aside starts from the form the Arrancar rose from and grows with maximum reiryoku (Ch. 203,
 * 306), and is far greater in Resurrección (Ch. 281, 346). It is never immunity: it gives way as the attacker's
 * reiatsu rises past the Arrancar's, and is gone at {@code pierce_ratio} times it (Ch. 308).
 *
 * <p>Hierro and the reiatsu difference never stack. Both are the victim's reiatsu blunting a weaker blow, so
 * multiplying one by the other would count the same gap twice and let a strong Arrancar shrug off nearly
 * everything. They are weighed together in the one damage handler ({@link ReiatsuEvents}), before the shield
 * and the moment after a hit in which nothing more gets through, and the larger cut wins.
 *
 * <p>Like armour, Hierro stops nothing that armour lets through ({@code minecraft:bypasses_armor}: falls, fire,
 * drowning, magic, the void, starving and the rest) and nothing without a living attacker. Thorns and a
 * creeper's blast do have one, so it cuts them. It stacks with worn armour, and it takes the place of the Hollow
 * hide in Resurrección ({@link HollowPlayerLogic#hideArmour}).
 */
public final class Hierro {
    /** The least damage Hierro must turn aside to ring, so a scratch makes no sound. */
    public static final float RING_MIN_STOPPED = 1.0F;
    /** Above this many ticks of a victim's invulnerability, a hit only adds what it has over the last one. */
    private static final int INVULNERABLE_ABOVE = 10;
    private static final int SPARKS = 6;
    private static final double SPARK_SPREAD = 0.25;
    /** How far up the body the sparks fly: about the chest. */
    private static final double SPARK_HEIGHT = 0.6;

    private Hierro() {
    }

    /**
     * The share of a blow from a soul as strong as this one that its Hierro turns aside: the origin's share and
     * more for each square root of maximum reiryoku, more again in Resurrección, up to the cap. 0 for anyone but
     * an Arrancar. Whether it is released is asked rather than read, because only the server knows (see
     * {@link ArrancarLogic#released}): the client's stats screen passes the look it was sent.
     */
    public static float share(SoulData data, boolean released) {
        if (!ArrancarLogic.isArrancar(data)) {
            return 0.0F;
        }
        double root = Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        double share = BleachConfig.get(ArrancarLogic.numbers(ArrancarLogic.origin(data)).hierroShare())
                + BleachConfig.get(BleachConfig.HIERRO_SHARE_PER_ROOT) * root;
        if (released) {
            share *= BleachConfig.get(BleachConfig.HIERRO_RELEASED_MULTIPLIER);
        }
        return (float) Mth.clamp(share, 0.0, BleachConfig.get(BleachConfig.HIERRO_MAX_SHARE));
    }

    /**
     * What a blow is multiplied by through a Hierro of this share: all of the share holds against an attacker no
     * stronger than the victim, none at {@code pierce_ratio} times the victim's reiatsu, and it gives way evenly
     * in between. 1 when the victim has no reiatsu.
     */
    public static float multiplier(float share, float attackerReiatsu, float victimReiatsu) {
        if (share <= 0.0F || victimReiatsu <= 0.0F) {
            return 1.0F;
        }
        double pierce = BleachConfig.get(BleachConfig.HIERRO_PIERCE_RATIO);
        double ratio = attackerReiatsu / (double) victimReiatsu;
        double hold = Mth.clamp((pierce - ratio) / (pierce - 1.0), 0.0, 1.0);
        return (float) (1.0 - Math.min(1.0, share) * hold);
    }

    /**
     * What this blow is multiplied by through the victim's Hierro: 1 unless the victim is an Arrancar player or NPC
     * and the blow comes from a living attacker other than itself and is not one armour lets through. Reads whether
     * it is released, so it means something on the server only, where blows land.
     */
    public static float multiplier(LivingEntity victim, DamageSource source, float attackerReiatsu, float victimReiatsu) {
        if (!(victim instanceof Player || victim instanceof ArrancarNpc) || !SoulLogic.hasSoul(victim)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return 1.0F;
        }
        SoulData data = SoulLogic.get(victim);
        if (!ArrancarLogic.isArrancar(data)) {
            return 1.0F;
        }
        return multiplier(share(data, ArrancarLogic.released(data)), attackerReiatsu, victimReiatsu);
    }

    /**
     * The blow rings off the Arrancar's Hierro, which turned this much of it aside: a chain's clang and a few
     * sparks at the chest, for those who see spirits. Nothing for less than {@link #RING_MIN_STOPPED}, nor while
     * the victim is still invulnerable after a hit (when only what a blow has over the last one gets through),
     * nor against a raised shield, so a flurry rings at most once every half second.
     */
    static void ring(LivingEntity victim, DamageSource source, float stopped) {
        if (stopped < RING_MIN_STOPPED || !(victim.level() instanceof ServerLevel level)
                || (victim.invulnerableTime > INVULNERABLE_ABOVE && !source.is(DamageTypeTags.BYPASSES_COOLDOWN))
                || victim.isDamageSourceBlocked(source)) {
            return;
        }
        SpiritSenses.particles(level, ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY(SPARK_HEIGHT), victim.getZ(),
                SPARKS, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD, 0.0);
        SpiritSenses.play(victim, ModSounds.HIERRO.get(), SoundSource.PLAYERS, 0.7F, 1.0F, false);
    }
}
