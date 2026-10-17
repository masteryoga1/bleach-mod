package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.soul.SpiritEvents;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Warden never senses a spirit mob. It picks targets through its own check rather than through
 * TargetingConditions, and that one check decides what it sniffs out, grows angry at, roars at and
 * fires its sonic boom at.
 */
@Mixin(Warden.class)
public abstract class WardenMixin {
    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void bleach$blindToSpirits(@Nullable Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity target && SpiritEvents.blindToSpirit((Warden) (Object) this, target)) {
            cir.setReturnValue(false);
        }
    }
}
