package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's sound events. Their files are vanilla sounds, pitched and spread out in
 * {@code assets/bleach/sounds.json}. A Hollow's howl, Fishbone D's roar and the tearing sky carry
 * much further than a normal mob's sounds, so they are fixed-range events; the rest reach as far as
 * their volume takes them, as usual.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, BleachMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_AMBIENT = fixed("entity.hollow.ambient", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_HURT = variable("entity.hollow.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_DEATH = variable("entity.hollow.death");
    /** A blow to a Hollow's mask, its weak point. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MASK_CRACK = variable("entity.hollow.mask_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHBONE_D_ROAR = fixed("entity.fishbone_d.roar", 64.0F);
    /** The sky tearing open as a Garganta forms. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GARGANTA_OPEN = fixed("entity.garganta.open", 64.0F);
    /** The soul pager's beep, played only to its holder. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DENREISHINKI_PING = variable("item.denreishinki.ping");

    private ModSounds() {
    }

    /** A sound heard out to {@code range} blocks whatever its volume. */
    private static DeferredHolder<SoundEvent, SoundEvent> fixed(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(BleachMod.id(name), range));
    }

    /** A sound whose range grows with its volume, 16 blocks at full volume. */
    private static DeferredHolder<SoundEvent, SoundEvent> variable(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BleachMod.id(name)));
    }
}
