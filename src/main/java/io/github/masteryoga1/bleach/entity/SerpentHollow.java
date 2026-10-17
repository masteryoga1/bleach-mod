package io.github.masteryoga1.bleach.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The standard Hollow: a rearing serpent with a hooded bone mask high on its neck. Its bite poisons,
 * and poison cannot touch it.
 */
public class SerpentHollow extends Hollow {
    public SerpentHollow(EntityType<? extends SerpentHollow> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.SERPENT.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.SERPENT;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }
}
