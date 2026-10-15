package io.github.masteryoga1.bleach.advancement;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BleachTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, BleachMod.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, SoulMilestoneTrigger> SOUL_MILESTONE =
            TRIGGERS.register("soul_milestone", () -> new SoulMilestoneTrigger());

    private BleachTriggers() {
    }
}
