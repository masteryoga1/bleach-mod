package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.KoryuBlock;
import io.github.masteryoga1.bleach.block.ReishiFootingBlock;
import io.github.masteryoga1.bleach.block.TailoringTableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

    private ModBlocks() {
    }
}
