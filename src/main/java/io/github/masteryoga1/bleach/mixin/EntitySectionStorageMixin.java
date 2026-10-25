package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.soul.MenosBody;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Entity lookups find a creature by the section its feet are in, and look only four blocks below the
 * box they are asked about, so an arrow or a blow aimed at a giant player's mask, high above its feet,
 * finds nothing whenever the feet sit in a lower section. A Menos mob is found through its parts, which a
 * player cannot have, so the lookups reach down as far as the tallest giant player online needs, and no
 * further than vanilla while there is none (see {@link MenosBody#lookupDepth}). Runs on both sides.
 */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin {
    /** Reaches far enough below a query to find a giant player's feet; 0 extra while none is online. */
    @ModifyConstant(method = "forEachAccessibleNonEmptySection", constant = @Constant(doubleValue = 4.0))
    private double bleach$reachGiantFeet(double margin) {
        return margin + MenosBody.lookupDepth();
    }
}
