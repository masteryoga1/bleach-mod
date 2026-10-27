package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Sonído, the Arrancar's flash step, on the flash step key: a burst of speed as far as a Shunpo, its path found
 * and its landing made the same way (see {@link FlashStep}). An Arrancar does it by instinct (Ch. 210), so it
 * needs no training and trains no Hohō. Its reach starts from the form the Arrancar rose from and grows with
 * maximum reiryoku, a quarter further in Resurrección, up to Shunpo's own cap; it costs a little reiryoku, and
 * its cooldown runs from the moment Shunpo's does ({@code lastFlashStepAt}), which an Arrancar has no other
 * use for. With no reishi footing, a Sonído from the air keeps the fall. It leaves no trail: a smudge of dark
 * dust and smoke and a low thud at both ends, for those who see spirits alone ({@link SpiritSenses}).
 *
 * <p>Kubo has said a Sonído turns the body's reishi into something that is not reishi (Q685). We read that as
 * a moment in which the Arrancar cannot be felt: for {@code unsensed_ticks} afterwards, Pesquisa misses it
 * ({@link #unsensed}).
 *
 * <p>Everything but {@link #perform} takes a {@link Player} or the soul alone, so game tests can use mock players.
 */
public final class Sonido {
    /** The dark shimmer at both ends of the step. */
    private static final DustParticleOptions SHIMMER = new DustParticleOptions(new Vector3f(0.12F, 0.1F, 0.14F), 1.2F);
    private static final int SHIMMER_COUNT = 12;
    private static final int SMOKE_COUNT = 4;
    /** How far above the feet the shimmer is centred, so its spread reaches down to them and not into the ground. */
    private static final double SHIMMER_HEIGHT = 0.6;

    /** Why a Sonído did not happen. Every one but {@link #COOLDOWN} is told on the action bar. */
    public enum Refusal {
        /** The last flash step was too short a while ago. Silent, as a Shunpo's is. */
        COOLDOWN(null),
        /** Bound by Gritz. */
        BOUND("message.bleach.gritz.bound"),
        /** Paying for it would leave less than half a point of reiryoku. */
        TOO_WEAK("message.bleach.flash_step.too_weak"),
        /** Slowness has cut the reach below the shortest step. */
        WEIGHED_DOWN("message.bleach.flash_step.weighed_down");

        @Nullable
        private final String messageKey;

        Refusal(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        /** What the player is told, or null for a refusal told to no one. */
        @Nullable
        public String messageKey() {
            return messageKey;
        }
    }

    private Sonido() {
    }

    // ---- Numbers ----

    /**
     * How far this Arrancar's Sonído goes, in blocks: its origin's reach and more for each square root of maximum
     * reiryoku, further in Resurrección, up to the cap; and, as for Shunpo, a quarter less for each level of
     * Slowness.
     *
     * @param slownessAmplifier the Slowness amplifier on the soul, or -1 for none
     */
    public static double distance(SoulData data, int slownessAmplifier) {
        double root = Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        double reach = BleachConfig.get(ArrancarLogic.numbers(ArrancarLogic.origin(data)).sonidoDistance())
                + BleachConfig.get(BleachConfig.SONIDO_DISTANCE_PER_ROOT) * root;
        if (ArrancarLogic.released(data)) {
            reach *= BleachConfig.get(BleachConfig.SONIDO_RELEASED_MULTIPLIER);
        }
        double distance = Math.min(BleachConfig.get(BleachConfig.SONIDO_MAX_DISTANCE), reach);
        if (slownessAmplifier >= 0) {
            distance *= Math.max(0.0, 1.0 - 0.25 * (slownessAmplifier + 1));
        }
        return distance;
    }

    /** Reiryoku a Sonído costs: a share of the maximum, and at least the least. */
    public static float cost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.SONIDO_MIN_COST),
                BleachConfig.get(BleachConfig.SONIDO_COST_FRACTION) * data.maxReiryoku());
    }

    /** Ticks from one Sonído to the next: fewer in Resurrección. */
    public static int cooldownTicks(SoulData data) {
        return BleachConfig.get(ArrancarLogic.released(data) ? BleachConfig.SONIDO_RELEASED_COOLDOWN_TICKS
                : BleachConfig.SONIDO_COOLDOWN_TICKS);
    }

    // ---- The step ----

    /**
     * Why this Arrancar could not Sonído now, the first check that fails winning: the cooldown, Gritz, the
     * reiryoku, then Slowness. Null when it can, though the way may still be blocked (see {@link #perform}).
     */
    @Nullable
    public static Refusal refusal(Player player, SoulData data, long now) {
        if (now - data.lastFlashStepAt < cooldownTicks(data)) {
            return Refusal.COOLDOWN;
        }
        if (player.hasEffect(ModEffects.GRITZ)) {
            return Refusal.BOUND;
        }
        if (data.reiryoku() - cost(data) < 0.5F) {
            return Refusal.TOO_WEAK;
        }
        if (distance(data, slownessAmplifier(player)) < FlashStep.MIN_DISTANCE) {
            return Refusal.WEIGHED_DOWN;
        }
        return null;
    }

    /**
     * The Arrancar pressed the flash step key while steering this way (each from -1 to 1, as the movement keys
     * give them). {@link FlashStep#perform} has already turned away a soul that cannot step at all (dead, riding,
     * asleep, gliding or a spectator). It is told why it cannot happen, or it happens: paid for, with no Hohō
     * trained.
     */
    static void perform(ServerPlayer player, SoulData data, float forward, float left, long now) {
        Refusal refusal = refusal(player, data, now);
        if (refusal != null) {
            if (refusal.messageKey() != null) {
                FlashStep.refuse(player, refusal.messageKey());
            }
            return;
        }
        double distance = distance(data, slownessAmplifier(player));
        float cost = cost(data);

        boolean onFooting = ReishiFooting.isActive(player);
        boolean grounded = player.onGround() && !onFooting;
        Vec3 start = player.position();
        Vec3 direction = FlashStep.direction(player.getYRot(), player.getXRot(), forward, left, !grounded);
        FlashStep.DashPath path = FlashStep.sweepFor(player, direction, distance, grounded, onFooting);
        Vec3 end = path.end();
        if (!FlashStep.reachable(start, end)) {
            FlashStep.refuse(player, "message.bleach.flash_step.blocked");
            return;
        }

        SoulLogic.trySpend(player, data, cost);
        stepped(data, now);
        Milestones.grant(player, Milestones.SONIDO);
        ServerLevel level = player.serverLevel();
        effects(level, start);
        effects(level, end);
        FlashStep.land(player, data, path, onFooting, grounded);
    }

    /** Notes a Sonído now: the flash step cooldown and Pesquisa's blind moment run from here. */
    public static void stepped(SoulData data, long now) {
        data.lastFlashStepAt = now;
    }

    /**
     * Whether this Arrancar Sonído'd within {@code unsensed_ticks}, so Pesquisa misses it. False for anyone else,
     * whose flash step is a Shunpo or a Hirenkyaku and hides nothing.
     */
    public static boolean unsensed(SoulData data, long now) {
        return ArrancarLogic.isArrancar(data)
                && now - data.lastFlashStepAt < BleachConfig.get(BleachConfig.SONIDO_UNSENSED_TICKS);
    }

    private static int slownessAmplifier(Player player) {
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return slowness != null ? slowness.getAmplifier() : -1;
    }

    /**
     * A smudge of dark dust and a puff of smoke at the feet, and the thud, for those who see spirits (an Arrancar
     * NPC's too).
     */
    public static void effects(ServerLevel level, Vec3 feet) {
        SpiritSenses.particles(level, SHIMMER, feet.x, feet.y + SHIMMER_HEIGHT, feet.z, SHIMMER_COUNT, 0.3, 0.6, 0.3, 0.0);
        SpiritSenses.particles(level, ParticleTypes.SMOKE, feet.x, feet.y + 0.1, feet.z, SMOKE_COUNT, 0.25, 0.05, 0.25,
                0.0);
        SpiritSenses.play(level, feet.x, feet.y, feet.z, ModSounds.SONIDO.get(), SoundSource.PLAYERS, 0.8F, 1.0F, false);
    }
}
