package io.github.masteryoga1.bleach.mixin;

import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads whether a firework rocket is boosting a glider, which vanilla keeps private. */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
    /** Whether the rocket rides the creature it boosts rather than flying on its own. */
    @Invoker("isAttachedToEntity")
    boolean bleach$isAttachedToEntity();
}
