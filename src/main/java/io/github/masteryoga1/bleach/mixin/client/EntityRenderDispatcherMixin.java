package io.github.masteryoga1.bleach.mixin.client;

import io.github.masteryoga1.bleach.client.ClientSouls;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spirits are not drawn at all for the spiritually unaware: no body, name tag, shadow, fire or hitbox. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void bleach$hideSpirits(Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
                                    CallbackInfoReturnable<Boolean> callback) {
        if (ClientSouls.isHiddenFromViewer(entity)) {
            callback.setReturnValue(false);
        }
    }
}
