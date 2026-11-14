package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.KoryuBlock;
import io.github.masteryoga1.bleach.block.ReishiFootingBlock;
import io.github.masteryoga1.bleach.block.TailoringTableBlock;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BleachMod.MODID);

    public static final DeferredBlock<TailoringTableBlock> TAILORING_TABLE = BLOCKS.register("tailoring_table",
            () -> new TailoringTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LOOM)));
    /** Hardened reishi under a player's feet. It has no item: only reishi footing places it. */
    public static final DeferredBlock<ReishiFootingBlock> REISHI_FOOTING = BLOCKS.register("reishi_footing",
            () -> new ReishiFootingBlock(ReishiFootingBlock.footingProperties()));
    /** Sekkiseki, the pale stone of the Seireiheki: no survival tool, blast, Wither or dragon breaks it. */
    public static final DeferredBlock<Block> SEKKISEKI = BLOCKS.registerSimpleBlock("sekkiseki",
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(-1.0F, 3600000.0F).isValidSpawn((state, level, pos, type) -> false));
    /** The Kōryū, the Dangai's walls of current: no collision, unbreakable, and no item, as only the lanes place it. */
    public static final DeferredBlock<KoryuBlock> KORYU = BLOCKS.register("koryu",
            () -> new KoryuBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission()
                    .forceSolidOn().strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> KoryuBlock.LIGHT).emissiveRendering((state, level, pos) -> true)
                    .isValidSpawn((state, level, pos, type) -> false)));
    /** The colour of the dust white sand sheds when nothing holds it up: bone white, a shade warm. */
    public static final int WHITE_SAND_DUST = 0xECE9E0;
    /** Hueco Mundo's white sand, which falls as sand does. */
    public static final DeferredBlock<ColoredFallingBlock> WHITE_SAND = BLOCKS.register("white_sand",
            () -> new ColoredFallingBlock(new ColorRGBA(WHITE_SAND_DUST), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND)));
    /** White sand pressed to stone, as sandstone is sand pressed. */
    public static final DeferredBlock<Block> WHITE_SANDSTONE = BLOCKS.registerSimpleBlock("white_sandstone",
            sandstone(0.8F, 0.8F));
    public static final DeferredBlock<Block> SMOOTH_WHITE_SANDSTONE = BLOCKS.registerSimpleBlock("smooth_white_sandstone",
            sandstone(2.0F, 6.0F));
    /** Hueco quartz, a white mineral laid as a pillar along an axis, as the quartz pillar is. */
    public static final DeferredBlock<RotatedPillarBlock> HUECO_QUARTZ = BLOCKS.register("hueco_quartz",
            () -> new RotatedPillarBlock(quartz()));
    public static final DeferredBlock<Block> HUECO_QUARTZ_BRICKS =
            BLOCKS.registerSimpleBlock("hueco_quartz_bricks", quartz());
    /** Seireitei's white plaster, the walls of its halls and barracks: stone-like, as white concrete is. */
    public static final DeferredBlock<Block> WHITE_PLASTER = BLOCKS.registerSimpleBlock("white_plaster",
            plaster(MapColor.SNOW));
    /** Black plaster, for the dark base of a white wall. */
    public static final DeferredBlock<Block> BLACK_PLASTER = BLOCKS.registerSimpleBlock("black_plaster",
            plaster(MapColor.COLOR_GRAY));
    /** Dark fired roof tiles in overlapping courses, as hard as bricks. */
    public static final DeferredBlock<Block> ROOF_TILES = BLOCKS.registerSimpleBlock("roof_tiles",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_CYAN).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<StairBlock> ROOF_TILE_STAIRS = BLOCKS.register("roof_tile_stairs",
            () -> new StairBlock(ROOF_TILES.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(ROOF_TILES.get())));
    public static final DeferredBlock<SlabBlock> ROOF_TILE_SLAB = BLOCKS.register("roof_tile_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.ofFullCopy(ROOF_TILES.get())));

    private ModBlocks() {
    }

    /** White sandstone's properties, as sandstone's are, at this hardness and blast resistance. */
    private static BlockBehaviour.Properties sandstone(float hardness, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(hardness, resistance);
    }

    /** Hueco quartz's properties, as the quartz block's are. */
    private static BlockBehaviour.Properties quartz() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(0.8F);
    }

    /** Plaster's properties, as white concrete's are, in this colour on a map. */
    private static BlockBehaviour.Properties plaster(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(1.8F);
    }
}
