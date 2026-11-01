package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.BakudoEffect;
import io.github.masteryoga1.bleach.soul.GritzEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's mob effects. Their icons are in {@code textures/mob_effect} (the Bakudō effects have none: they are hidden,
 * see {@code BleachClient}).
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BleachMod.MODID);

    /** Gritz, the Gintō spell that binds. See {@link GritzEffect}. */
    public static final DeferredHolder<MobEffect, GritzEffect> GRITZ = EFFECTS.register("gritz", GritzEffect::new);
    /** The arms bound behind the back by a Bakudō such as Sai: no blow, item or hand art. See {@link BakudoEffect}. */
    public static final DeferredHolder<MobEffect, BakudoEffect> BAKUDO_BOUND =
            EFFECTS.register("bakudo_bound", () -> new BakudoEffect(false));
    /** The body held where it stands by a Bakudō such as Hainawa, the arms left free. See {@link BakudoEffect}. */
    public static final DeferredHolder<MobEffect, BakudoEffect> BAKUDO_HELD =
            EFFECTS.register("bakudo_held", () -> new BakudoEffect(true));

    private ModEffects() {
    }
}
