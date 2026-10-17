package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.Hexapodus;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The Hexapodus: the vanilla spider's body with its middle hind legs left out, so it runs on six.
 * Each renderer bakes its own copy of the spider layer, so hiding the legs here leaves spiders alone.
 */
public final class HexapodusModel extends SpiderModel<Hexapodus> {
    public HexapodusModel(ModelPart root) {
        super(root);
        root.getChild("right_middle_hind_leg").visible = false;
        root.getChild("left_middle_hind_leg").visible = false;
    }
}
