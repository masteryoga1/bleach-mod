package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Shin'ō Academy in Seireitei, the structure {@code bleach:seireitei_academy} at the plan's academy site. A world
 * whose chunks there were made before the site existed, or made with structures off, has whatever part it lacks built
 * once, when both of the Academy's chunks and those beside them are loaded: each that never placed its part places it
 * then. A part an operator placed by command is recognised and kept.
 */
public final class AcademySite {
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("seireitei_academy"));
    /**
     * The Academy's stamp: its template, 15 by 9 by 17 before its turn, and the block each chunk's part is known by, the
     * hall's Tailoring Table and the gate's north post.
     */
    private static final SiteStamp STAMP = new SiteStamp(BleachWorlds.SOUL_SOCIETY, STRUCTURE, "The Academy in Seireitei",
            SeireiteiPlan.sites("academy"), new Vec3i(15, 9, 17),
            List.of(new SiteStamp.Marker(new BlockPos(3, 1, 13), ModBlocks.TAILORING_TABLE),
                    new SiteStamp.Marker(new BlockPos(5, 1, 0), () -> Blocks.STRIPPED_DARK_OAK_LOG)),
            SiteStamp.Placed.NOTHING);
    /** The chunks the Academy covers (x -208 to -192, z 224 to 238), the hall's and then the gate's, where it starts. */
    public static final List<ChunkPos> COVERED = STAMP.covered(0);

    private AcademySite() {
    }

    /** The chunk the Academy's structure starts in, the one holding its corner: (-12, 14). */
    public static ChunkPos startChunk() {
        return STAMP.startChunk(0);
    }

    /**
     * One of Soul Society's Academy chunks, or one beside them, has loaded: once the tick is done, if all of them are
     * loaded, whatever part of the Academy is missing is built, and if one is not, the check runs once more after the
     * next tick.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        STAMP.onChunkLoad(event);
    }

    /** The one further try a check asked for, queued in a tick after the one it ran in. */
    public static void onServerTick(ServerTickEvent.Post event) {
        STAMP.onServerTick(event);
    }

    /**
     * Builds what was never placed of the Academy, as {@link SiteStamp#ensureBuilt(ServerLevel, int)} does. Server thread
     * only. Public for Konsō's landing and the respawn (7d3), and for game tests.
     *
     * @return whether any part was placed
     */
    public static boolean ensureBuilt(ServerLevel level) {
        return STAMP.ensureBuilt(level, 0);
    }
}
