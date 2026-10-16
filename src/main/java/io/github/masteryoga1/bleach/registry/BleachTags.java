package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Tags the mod defines in its data pack. */
public final class BleachTags {
    /** Zanpakutō of every kind, from a nameless Asauchi up. */
    public static final TagKey<Item> ZANPAKUTO = TagKey.create(Registries.ITEM, BleachMod.id("zanpakuto"));
    /** The pieces of the Shihakushō, the Shinigami uniform. */
    public static final TagKey<Item> SHINIGAMI_GARB = TagKey.create(Registries.ITEM, BleachMod.id("shinigami_garb"));

    /** Every kind of Academy a soul can be sent to. */
    public static final TagKey<Structure> ACADEMY = TagKey.create(Registries.STRUCTURE, BleachMod.id("academy"));

    private BleachTags() {
    }
}
