package io.github.masteryoga1.bleach.mixin.client;

import io.github.masteryoga1.bleach.client.JinzenOverlay;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An off-hand block that cannot be placed where it is aimed fails, which ends the click before
 * {@link MinecraftMixin} can say why Jinzen did not start, so it is said here.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void bleach$explainFailedOffHand(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                             CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.OFF_HAND && cir.getReturnValue() == InteractionResult.FAIL) {
            JinzenOverlay.explainFullOffHand(player);
        }
    }
}
