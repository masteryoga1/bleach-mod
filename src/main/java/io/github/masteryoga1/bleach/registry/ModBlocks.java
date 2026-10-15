package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.TailoringTableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BleachMod.MODID);

    public static final DeferredBlock<TailoringTableBlock> TAILORING_TABLE = BLOCKS.register("tailoring_table",
            () -> new TailoringTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LOOM)));

    private ModBlocks() {
    }
}
