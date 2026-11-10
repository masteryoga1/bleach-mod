package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.soul.RukongaiEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every line the server puts on a player's action bar is noted, so the name of a place in Soul Society waits behind it. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void bleach$noteActionBar(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) {
            RukongaiEvents.actionBarShown((ServerPlayer) (Object) this);
        }
    }
}
