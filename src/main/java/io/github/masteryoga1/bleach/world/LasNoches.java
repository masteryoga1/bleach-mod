package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Las Noches at Hueco Mundo's centre, the structure {@code bleach:las_noches}: worldgen lays its wall, dome and towers,
 * from {@link HuecoMundoPlan}'s table, in the chunks of a Hueco Mundo made since it was added with structures on.
 */
public final class LasNoches {
    /** Its structure, whose one piece lays the plan's shell. */
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("las_noches"));
    /** The chunk it starts in, the centre's: its references reach every chunk the shell covers, 8 at most away. */
    public static final ChunkPos HOME = new ChunkPos(0, 0);

    private LasNoches() {
    }
}
