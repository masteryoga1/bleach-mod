package io.github.masteryoga1.bleach.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Natural regeneration leaves a body woven with Ransōtengai alone. Nothing heals it while woven, and
 * the regeneration would otherwise burn its saturation and food on heals that never land; seen as
 * unhurt, it skips both kinds and food still runs down and starves as usual.
 *
 * <p>A Hollow has no food tick at all: it feeds on souls, and its soul hunger takes the place of the
 * food bar (see {@link HollowBody#feedsOnSouls}).
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    /** A Hollow feeds on souls, not food: soul hunger takes the exhaustion and holds the bar. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bleach$soulHunger(Player player, CallbackInfo ci) {
        if (HollowBody.feedsOnSouls(player, (FoodData) (Object) this)) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean bleach$noRegenerationWhileWoven(Player player, Operation<Boolean> original) {
        return original.call(player) && !QuincyRansotengai.woven(player);
    }
}
