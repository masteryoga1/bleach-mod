package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.ReishiFootingBlock;
import io.github.masteryoga1.bleach.block.TailoringTableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BleachMod.MODID);

    public static final DeferredBlock<TailoringTableBlock> TAILORING_TABLE = BLOCKS.register("tailoring_table",
            () -> new TailoringTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LOOM)));
    /** Hardened reishi under a player's feet. It has no item: only reishi footing places it. */
    public static final DeferredBlock<ReishiFootingBlock> REISHI_FOOTING = BLOCKS.register("reishi_footing",
            () -> new ReishiFootingBlock(ReishiFootingBlock.footingProperties()));

    private ModBlocks() {
    }
}
