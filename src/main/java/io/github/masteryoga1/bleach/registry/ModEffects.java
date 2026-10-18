package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.GritzEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's mob effects. Their icons are in {@code textures/mob_effect}. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BleachMod.MODID);

    /** Gritz, the Gintō spell that binds. See {@link GritzEffect}. */
    public static final DeferredHolder<MobEffect, GritzEffect> GRITZ = EFFECTS.register("gritz", GritzEffect::new);

    private ModEffects() {
    }
}
