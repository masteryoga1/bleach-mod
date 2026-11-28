package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.Runuganga;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.AABB;

/**
 * Water that reaches Runuganga, and soaks him ({@link Runuganga#soak}). In Hueco Mundo, where water poured from a bucket
 * boils away and no rain falls, that is water carried in from another world: a splash or lingering bottle of water
 * bursting by him ({@link #splashed}).
 */
public final class RunugangaWater {
    private RunugangaWater() {
    }

    /**
     * A bottle of water bursts (from ThrownPotionMixin, as its splash begins): each body of his that one of his boxes puts
     * in the splash is soaked, unless an unaware soul threw it. His boxes, not his feet, as vanilla's splash measures,
     * since he stands 12 high.
     */
    public static void splashed(ThrownPotion potion) {
        AABB splash = potion.getBoundingBox().inflate(ThrownPotion.SPLASH_RANGE, 2.0, ThrownPotion.SPLASH_RANGE);
        for (LivingEntity body : SpiritProjectiles.creaturesIn(potion.level(), splash, e -> e instanceof Runuganga)) {
            if (!SpiritEvents.potionPassesThrough(potion.getOwner(), body)) {
                ((Runuganga) body).soak();
            }
        }
    }
}
