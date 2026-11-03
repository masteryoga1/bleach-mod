package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.soul.SpiritEvents;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mundane creatures never pick a spirit mob out as a target, or as anything else to look at or run
 * from: an iron golem looks past a Hollow to the zombie behind it. Cancelling the target change alone
 * would leave it picking the Hollow again and again, and never the zombie. Nor does a creature pick out
 * one it has lost in a Kidō smoke (see KidoSmoke).
 */
@Mixin(TargetingConditions.class)
public abstract class TargetingConditionsMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void bleach$blindToSpirits(@Nullable LivingEntity looker, LivingEntity target,
            CallbackInfoReturnable<Boolean> cir) {
        if (looker != null && SpiritEvents.cannotPick(looker, target)) {
            cir.setReturnValue(false);
        }
    }
}
