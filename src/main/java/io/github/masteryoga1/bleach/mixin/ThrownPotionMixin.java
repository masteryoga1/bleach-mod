package io.github.masteryoga1.bleach.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.masteryoga1.bleach.soul.SpiritEvents;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Instant Health and Harming from a splash potion skip the event other effects go through, so they
 * are kept off spirits here when the thrower is unaware.
 */
@Mixin(ThrownPotion.class)
public abstract class ThrownPotionMixin {
    @WrapWithCondition(
            method = "applySplash",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect("
                    + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/entity/LivingEntity;ID)V"))
    private boolean bleach$reachesTarget(MobEffect effect, @Nullable Entity source, @Nullable Entity owner,
            LivingEntity target, int amplifier, double health) {
        return !SpiritEvents.potionPassesThrough(owner, target);
    }
}
