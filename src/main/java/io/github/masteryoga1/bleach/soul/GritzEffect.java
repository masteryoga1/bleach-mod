package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Gritz, the Gintō spell that binds: whatever it holds cannot walk, swim, jump, climb or fly, and
 * cannot dash away with Hirenkyaku or Shunpo or stand on reishi either (those check for it
 * themselves). Each of its attribute modifiers multiplies the total by zero, a jump that still
 * leaves the ground (on Jump Boost, which vanilla adds after the attribute) is cancelled
 * ({@link #onJump}), and nothing carries it up a ladder or through water. Flight given by an
 * attribute is switched off with it, but a creative player's own flight is not. Only those who see
 * spirits see the ring of light around a bound creature.
 */
public final class GritzEffect extends MobEffect {
    /** The effect's colour: the pale blue-white of reishi. */
    private static final int COLOUR = 0xCFE8FF;
    /** Ticks between rings of light around the bound creature. */
    private static final int RING_INTERVAL_TICKS = 10;
    private static final int RING_POINTS = 8;

    public GritzEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
        // At the effect's first level each modifier is -1 of the total, which leaves nothing.
        addAttributeModifier(Attributes.MOVEMENT_SPEED, BleachMod.id("gritz_speed"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, BleachMod.id("gritz_jump"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FLYING_SPEED, BleachMod.id("gritz_flying_speed"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(NeoForgeMod.SWIM_SPEED, BleachMod.id("gritz_swim_speed"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, BleachMod.id("gritz_flight"), -1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /**
     * A bound creature that jumps anyway goes nowhere: neither up nor, sprinting, ahead. On both
     * sides, since a player's own client moves them.
     */
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ModEffects.GRITZ)) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /**
     * On whichever side moves the creature (a player's own client), before it moves this tick: it
     * rises neither up a ladder nor through water, which vanilla lifts it by whatever its speed.
     * Then, on the server, holds a creature in place, since a mob's own pathing and knockback can
     * still slide it about with no speed of its own; a player's movement is their client's, and
     * their speed is already gone.
     */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.isControlledByLocalInstance()) {
            Vec3 motion = entity.getDeltaMovement();
            if (motion.y > 0.0 && (entity.onClimbable() || entity.isInWater())) {
                entity.setDeltaMovement(motion.x, 0.0, motion.z);
            }
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return true;
        }
        if (!(entity instanceof Player)) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0, motion.y, 0.0);
        }
        if (entity.tickCount % RING_INTERVAL_TICKS == 0) {
            double radius = entity.getBbWidth() * 0.5 + 0.3;
            double y = entity.getY(0.5);
            for (int i = 0; i < RING_POINTS; i++) {
                double angle = Math.PI * 2.0 * i / RING_POINTS;
                SpiritSenses.particles(level, ParticleTypes.END_ROD, entity.getX() + Math.cos(angle) * radius, y,
                        entity.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        return true;
    }
}
