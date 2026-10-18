package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Hirenkyaku, the Quincy's flying screen step: the Quincy gathers reishi under their feet and rides
 * the current several blocks in an instant. It shares the flash step key and the moment its cooldown
 * counts from (see {@link QuincyLogic#movementFor}), and the way Shunpo finds its path and lands (see
 * {@link FlashStep}), but it is paid in reishi, trains the Quincy power instead of Hohō, and leaves a
 * pale blue trail that only those who see spirits see.
 */
public final class Hirenkyaku {
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.7F, 0.85F, 1.0F), 1.0F);
    /** How far apart the trail's specks are. */
    private static final double TRAIL_STEP = 0.5;
    /** How high over the feet the trail runs: the current is ridden, not run along. */
    private static final double TRAIL_HEIGHT = 0.15;
    private static final int RING_POINTS = 8;
    private static final double RING_RADIUS = 0.6;
    private static final double RING_SPEED = 0.12;

    private Hirenkyaku() {
    }

    /**
     * The player pressed the flash step key while steering this way (each from -1 to 1, as the
     * movement keys give them), and {@link QuincyLogic#movementFor} chose Hirenkyaku. During the
     * cooldown nothing happens; otherwise the player is told why it cannot happen, or it does.
     */
    public static void perform(ServerPlayer player, SoulData data, float forward, float left) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now - data.lastFlashStepAt < QuincyLogic.hirenkyakuCooldownTicks(data)) {
            return;
        }
        // Said before the training check: a sealed Quincy knows the dash, the power is what is missing.
        if (QuincyArtsLogic.isSealed(data)) {
            FlashStep.refuse(player, "message.bleach.letzt_stil.sealed");
            return;
        }
        if (player.hasEffect(ModEffects.GRITZ)) {
            FlashStep.refuse(player, "message.bleach.gritz.bound");
            return;
        }
        if (!QuincyLogic.knowsHirenkyaku(data)) {
            FlashStep.refuse(player, "message.bleach.hirenkyaku.untrained");
            return;
        }
        if (data.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
            FlashStep.refuse(player, "message.bleach.hirenkyaku.poisoned");
            return;
        }
        float cost = QuincyLogic.hirenkyakuCost(data);
        if (QuincyLogic.reishi(data) < cost) {
            FlashStep.refuse(player, "message.bleach.hirenkyaku.too_weak");
            return;
        }
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        double distance = QuincyLogic.hirenkyakuDistance(data, slowness != null ? slowness.getAmplifier() : -1);
        if (distance < FlashStep.MIN_DISTANCE) {
            FlashStep.refuse(player, "message.bleach.flash_step.weighed_down");
            return;
        }

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

        QuincyLogic.trySpendReishi(data, cost);
        data.lastFlashStepAt = now;
        if (now - data.lastHirenTrainingAt >= QuincyLogic.HIRENKYAKU_TRAINING_COOLDOWN_TICKS) {
            data.lastHirenTrainingAt = now;
            QuincyEvents.train(player, data, QuincyLogic.HIRENKYAKU_TRAINING);
        }
        Milestones.grant(player, Milestones.HIRENKYAKU);
        effects(level, start, end);
        SpiritSenses.play(level, start.x, start.y, start.z, ModSounds.HIRENKYAKU.get(), SoundSource.PLAYERS,
                0.8F, 1.0F, false);
        FlashStep.land(player, data, path, onFooting, grounded);
    }

    /** A ring of light bursting out where the step began, and a trail of reishi all the way to its end. */
    private static void effects(ServerLevel level, Vec3 start, Vec3 end) {
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / RING_POINTS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            // A count of 0 sends one particle moving along (dx, 0, dz), so the ring bursts outwards.
            SpiritSenses.particles(level, ParticleTypes.END_ROD, start.x + dx * RING_RADIUS, start.y + TRAIL_HEIGHT,
                    start.z + dz * RING_RADIUS, 0, dx, 0.0, dz, RING_SPEED);
        }
        Vec3 path = end.subtract(start);
        int specks = (int) Math.floor(path.length() / TRAIL_STEP);
        for (int i = 0; i <= specks; i++) {
            Vec3 at = specks == 0 ? start : start.add(path.scale(i / (double) specks));
            SpiritSenses.particles(level, TRAIL, at.x, at.y + TRAIL_HEIGHT, at.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }
}
