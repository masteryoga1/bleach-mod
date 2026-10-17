package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Tags the mod defines in its data pack. */
public final class BleachTags {
    /** Zanpakutō of every kind, from a nameless Asauchi up. */
    public static final TagKey<Item> ZANPAKUTO = TagKey.create(Registries.ITEM, BleachMod.id("zanpakuto"));
    /** The pieces of the Shihakushō, the Shinigami uniform. */
    public static final TagKey<Item> SHINIGAMI_GARB = TagKey.create(Registries.ITEM, BleachMod.id("shinigami_garb"));
    /** The pieces of the white Quincy garb. */
    public static final TagKey<Item> QUINCY_GARB = TagKey.create(Registries.ITEM, BleachMod.id("quincy_garb"));

    /** Every kind of Academy a soul can be sent to. */
    public static final TagKey<Structure> ACADEMY = TagKey.create(Registries.STRUCTURE, BleachMod.id("academy"));

    /** Every kind of Hollow mob, Fishbone D included. */
    public static final TagKey<EntityType<?>> HOLLOWS = TagKey.create(Registries.ENTITY_TYPE, BleachMod.id("hollows"));

    /**
     * Damage that purifies the Hollow it kills, whatever struck the blow, as a zanpakutō's edge does.
     * Getsuga Tenshō is its first member; other techniques join it as they arrive.
     */
    public static final TagKey<DamageType> PURIFIES = TagKey.create(Registries.DAMAGE_TYPE, BleachMod.id("purifies"));

    /**
     * Damage that destroys the Hollow it kills, soul and all, instead of sending it on, as a Quincy's
     * arrows do. The Heilig Pfeil is its first member. Nothing should be in this tag and in
     * {@link #PURIFIES} at once.
     */
    public static final TagKey<DamageType> DESTROYS_SOULS = TagKey.create(Registries.DAMAGE_TYPE, BleachMod.id("destroys_souls"));

    /** Biomes where reishi runs rich, so a Quincy gathers it faster: forests, taiga, meadows and the like. */
    public static final TagKey<Biome> REISHI_RICH = TagKey.create(Registries.BIOME, BleachMod.id("reishi_rich"));
    /** Biomes where reishi runs thin, so a Quincy gathers it more slowly: deserts, badlands and the deepest caves. */
    public static final TagKey<Biome> REISHI_THIN = TagKey.create(Registries.BIOME, BleachMod.id("reishi_thin"));

    private BleachTags() {
    }
}
