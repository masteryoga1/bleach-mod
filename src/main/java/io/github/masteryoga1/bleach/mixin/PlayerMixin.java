package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.entity.CajaNegacion;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sneaking never lets a player out of a Caja Negación: it tears its own way out when its time comes. This is the
 * one place the server's sneak dismount is decided (Player.rideTick asks it, on the server only), so every other way
 * off a mount (logging out, death, another world, {@code /ride}) still frees a held player, and Negación's light,
 * which {@link CajaNegacion#locks} does not read, still lets its rider sneak out.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void bleach$shutInCaja(CallbackInfoReturnable<Boolean> cir) {
        if (CajaNegacion.locks((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
