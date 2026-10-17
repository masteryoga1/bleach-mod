package io.github.masteryoga1.bleach.mixin.client;

import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sneak-use with the Asauchi in hand gives way to a full off hand (see ZanpakutoItem#use). Only once
 * the off hand has had its turn and done nothing either is the player told to empty it for Jinzen.
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
        if (player != null && SoulLogic.get(player).takeOffHandRefused()) {
            player.displayClientMessage(Component.translatable("message.bleach.jinzen.off_hand")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }
}
