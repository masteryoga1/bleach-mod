package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.DivisionBannerBlock;
import io.github.masteryoga1.bleach.block.KoryuBlock;
import io.github.masteryoga1.bleach.block.ReishiFootingBlock;
import io.github.masteryoga1.bleach.block.TailoringTableBlock;
import io.github.masteryoga1.bleach.block.WicketBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
    /** Las Noches' white stone, of its wall and towers: a fortress's, harder than any art carves at its defaults. */
    public static final DeferredBlock<Block> LAS_NOCHES_STONE = BLOCKS.registerSimpleBlock("las_noches_stone",
            lasNoches(MapColor.QUARTZ));
    /** Its white tiles, of the dome and the courses that cap a wall or a tower. */
    public static final DeferredBlock<Block> LAS_NOCHES_TILES = BLOCKS.registerSimpleBlock("las_noches_tiles",
            lasNoches(MapColor.QUARTZ));
    /** Its dark trim, the band under the top of a wall or a tower. */
    public static final DeferredBlock<Block> LAS_NOCHES_TRIM = BLOCKS.registerSimpleBlock("las_noches_trim",
            lasNoches(MapColor.COLOR_BLACK));
    /** Its sky, lining the dome's inner face and ringing the halls' floor: the fullest light, drawn unshaded. */
    public static final DeferredBlock<Block> LAS_NOCHES_SKY = BLOCKS.registerSimpleBlock("las_noches_sky",
            lasNoches(MapColor.COLOR_LIGHT_BLUE).lightLevel(state -> 15).emissiveRendering((state, level, pos) -> true));
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
    /** Shōji, paper on a wooden lattice: a pane that joins panes, walls and full faces as glass does, and takes water. */
    public static final DeferredBlock<IronBarsBlock> SHOJI = BLOCKS.register("shoji", () -> new IronBarsBlock(paper(1.0F)));
    /** The shōji door's set: opened by hand, by redstone and by a wind charge, with bamboo's sounds. */
    public static final BlockSetType SHOJI_SET = new BlockSetType("bleach:shoji", true, true, true,
            BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.BAMBOO_WOOD, SoundEvents.BAMBOO_WOOD_DOOR_CLOSE,
            SoundEvents.BAMBOO_WOOD_DOOR_OPEN, SoundEvents.BAMBOO_WOOD_TRAPDOOR_CLOSE, SoundEvents.BAMBOO_WOOD_TRAPDOOR_OPEN,
            SoundEvents.BAMBOO_WOOD_PRESSURE_PLATE_CLICK_OFF, SoundEvents.BAMBOO_WOOD_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_OFF, SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON);
    /**
     * The shōji door, a shōji panel hung as a door: it swings on its hinge as any door does. Its set is registered as
     * the block is made, on the one thread NeoForge registers blocks on, as the set's map is not safe to share.
     */
    public static final DeferredBlock<DoorBlock> SHOJI_DOOR = BLOCKS.register("shoji_door",
            () -> new DoorBlock(BlockSetType.register(SHOJI_SET), paper(2.0F).pushReaction(PushReaction.DESTROY)));
    /** Tatami, a straw mat laid as a block, its weave turned along the look of whoever laid it. */
    public static final DeferredBlock<GlazedTerracottaBlock> TATAMI = BLOCKS.register("tatami",
            () -> new GlazedTerracottaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .instrument(NoteBlockInstrument.BANJO).strength(0.5F).sound(SoundType.GRASS).ignitedByLava()));
    /** A division's banner: cloth hung on the side of a block, as a wall banner is, with cloth's colour and sound. */
    public static final DeferredBlock<DivisionBannerBlock> DIVISION_BANNER = BLOCKS.register("division_banner",
            () -> new DivisionBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).forceSolidOn()
                    .instrument(NoteBlockInstrument.BASS).noCollission().strength(1.0F).sound(SoundType.WOOL)
                    .ignitedByLava().pushReaction(PushReaction.DESTROY)));
    /**
     * A great gate of the Seireiheki, faced in Sekkiseki: as hard as the wall and as deaf to arts, and no piston moves
     * it. It drops nothing; only GateState lays it.
     */
    public static final DeferredBlock<Block> GREAT_GATE = BLOCKS.registerSimpleBlock("great_gate",
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false));
    /**
     * The wicket through a gate house's right-hand pier: a dark wooden door that nothing breaks, opened by hand but not
     * from the Rukongai's side by a soul the gates bar.
     */
    public static final DeferredBlock<WicketBlock> WICKET = BLOCKS.register("wicket",
            () -> new WicketBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .instrument(NoteBlockInstrument.BASS).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false)));

    private ModBlocks() {
    }

    /** Lets fire take the paper, cloth and straw blocks, with vanilla's odds for their kin. Run on the main thread. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            // Paper and cloth catch and burn as wool does; straw as a hay bale does.
            fire.setFlammable(SHOJI.get(), 30, 60);
            fire.setFlammable(SHOJI_DOOR.get(), 30, 60);
            fire.setFlammable(DIVISION_BANNER.get(), 30, 60);
            fire.setFlammable(TATAMI.get(), 60, 20);
        });
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

    /** Las Noches' properties: stone of this colour on a map, a little harder than stone and as strong against blasts. */
    private static BlockBehaviour.Properties lasNoches(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(3.5F, 9.0F);
    }

    /** Plaster's properties, as white concrete's are, in this colour on a map. */
    private static BlockBehaviour.Properties plaster(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(1.8F);
    }

    /** Shōji's properties and its door's: paper on thin wood, at this hardness, lit by lava. */
    private static BlockBehaviour.Properties paper(float hardness) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).instrument(NoteBlockInstrument.BASS)
                .strength(hardness).sound(SoundType.BAMBOO_WOOD).noOcclusion().ignitedByLava();
    }
}
