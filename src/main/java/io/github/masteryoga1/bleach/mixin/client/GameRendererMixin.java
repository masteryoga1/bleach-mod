package io.github.masteryoga1.bleach.mixin.client;

import io.github.masteryoga1.bleach.client.ClientSouls;
import java.util.function.Predicate;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The crosshair passes through spirits the player cannot see, so they can still mine and build behind one. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult("
                    + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
                    + "Lnet/minecraft/world/phys/EntityHitResult;"),
            index = 4)
    private Predicate<Entity> bleach$skipHiddenSpirits(Predicate<Entity> filter) {
        return filter.and(entity -> !ClientSouls.isHiddenFromViewer(entity));
    }
}
