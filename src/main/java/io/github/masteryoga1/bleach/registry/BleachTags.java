package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Tags the mod defines in its data pack. */
public final class BleachTags {
    /** Zanpakutō of every kind, from a nameless Asauchi up. */
    public static final TagKey<Item> ZANPAKUTO = TagKey.create(Registries.ITEM, BleachMod.id("zanpakuto"));
    /** The pieces of the Shihakushō, the Shinigami uniform. */
    public static final TagKey<Item> SHINIGAMI_GARB = TagKey.create(Registries.ITEM, BleachMod.id("shinigami_garb"));
    /**
     * The pieces that count as Quincy garb for its set bonus: the white garb's tunic, trousers and
     * shoes, and the Wandenreich coat, trousers and boots, which can be worn mixed.
     */
    public static final TagKey<Item> QUINCY_GARB = TagKey.create(Registries.ITEM, BleachMod.id("quincy_garb"));
    /** The pieces of the Wandenreich uniform: cap, coat, trousers and boots. */
    public static final TagKey<Item> WANDENREICH_UNIFORM = TagKey.create(Registries.ITEM, BleachMod.id("wandenreich_uniform"));
    /** The pieces of the white Arrancar uniform: jacket, hakama and zōri. */
    public static final TagKey<Item> ARRANCAR_UNIFORM = TagKey.create(Registries.ITEM, BleachMod.id("arrancar_uniform"));
    /** Food a Hollow player can still eat. Empty: a Hollow feeds on souls. Packs may add to it. */
    public static final TagKey<Item> HOLLOW_EDIBLE = TagKey.create(Registries.ITEM, BleachMod.id("hollow_edible"));
    /** What a Hollow player can wear though its hide refuses armour: elytra, carved pumpkins and heads. */
    public static final TagKey<Item> HOLLOW_WEARABLE = TagKey.create(Registries.ITEM, BleachMod.id("hollow_wearable"));

    /** The Overworld's Academy structures, which no new chunk starts: kept for the old chunks that name them. */
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

    /**
     * Damage no Kidō ward before its victim stops, as an art that rises about it rather than comes from one side:
     * Sprenger.
     */
    public static final TagKey<DamageType> PASSES_WARDS = TagKey.create(Registries.DAMAGE_TYPE, BleachMod.id("passes_wards"));

    /** Biomes where reishi runs rich, so a Quincy gathers it faster: forests, taiga, meadows and the like. */
    public static final TagKey<Biome> REISHI_RICH = TagKey.create(Registries.BIOME, BleachMod.id("reishi_rich"));
    /** Biomes where reishi runs thin, so a Quincy gathers it more slowly: deserts, badlands and the deepest caves. */
    public static final TagKey<Biome> REISHI_THIN = TagKey.create(Registries.BIOME, BleachMod.id("reishi_thin"));

    /** Blocks that undo reiryoku (Sekkiseki): a spirit art comes undone at the first it meets (see {@code SpiritClip}). */
    public static final TagKey<Block> NEGATES_REIRYOKU = TagKey.create(Registries.BLOCK, BleachMod.id("negates_reiryoku"));

    /**
     * The effects Ransōtengai's threads shake off and keep off while woven: slowness, mining fatigue
     * and weakness, the reiatsu pressure aura's among them.
     */
    public static final TagKey<MobEffect> RANSOTENGAI_FREES = TagKey.create(Registries.MOB_EFFECT, BleachMod.id("ransotengai_frees"));

    private BleachTags() {
    }
}
