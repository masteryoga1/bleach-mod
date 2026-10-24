package io.github.masteryoga1.bleach.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Natural regeneration leaves a body woven with Ransōtengai alone. Nothing heals it while woven, and
 * the regeneration would otherwise burn its saturation and food on heals that never land; seen as
 * unhurt, it skips both kinds and food still runs down and starves as usual.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
    private boolean bleach$noRegenerationWhileWoven(Player player, Operation<Boolean> original) {
        return original.call(player) && !QuincyRansotengai.woven(player);
    }
}
