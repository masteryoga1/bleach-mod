package io.github.masteryoga1.bleach.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.masteryoga1.bleach.soul.RunugangaWater;
import io.github.masteryoga1.bleach.soul.SpiritEvents;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Instant Health and Harming from a splash potion skip the event other effects go through, so they
 * are kept off spirits here when the thrower is unaware. A bottle of water, splash or lingering, bursts
 * through a splash of its own, which touches only what water harms or what burns: Runuganga is soaked
 * there too.
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

    @Inject(method = "applyWater", at = @At("HEAD"))
    private void bleach$soaksRunuganga(CallbackInfo ci) {
        RunugangaWater.splashed((ThrownPotion) (Object) this);
    }
}
