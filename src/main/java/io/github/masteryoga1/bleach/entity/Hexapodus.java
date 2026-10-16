package io.github.masteryoga1.bleach.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * A six-legged insect Hollow that hunts Pluses before anyone else, fast enough to run one down, and
 * comes more often where Pluses are. Its bite slows. It climbs and leaps as a Spider Hollow does.
 */
public class Hexapodus extends SpiderHollow {
    public Hexapodus(EntityType<? extends Hexapodus> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.HEXAPODUS.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.HEXAPODUS;
    }
}
