package io.github.masteryoga1.bleach.mixin.client;

import io.github.masteryoga1.bleach.client.JinzenOverlay;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneak-use with the Asauchi in hand gives way to a full off hand (see ZanpakutoItem#use). Only once
 * the off hand has had its turn and done nothing either is the player told to empty it for Jinzen:
 * here when the click runs to its end, and in {@link MultiPlayerGameModeMixin} when an off-hand block
 * that cannot be placed ends it early.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void bleach$forgetOffHandRefusal(CallbackInfo ci) {
        LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null) {
            SoulLogic.get(player).takeOffHandRefused();
        }
    }

    /** The end of the method is only reached when neither hand did anything with the click. */
    @Inject(method = "startUseItem", at = @At("TAIL"))
    private void bleach$explainOffHandRefusal(CallbackInfo ci) {
        LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null) {
            JinzenOverlay.explainFullOffHand(player);
        }
    }
}
