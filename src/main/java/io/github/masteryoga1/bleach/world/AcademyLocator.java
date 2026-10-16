package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.soul.SafeLanding;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.Vec3;

/**
 * Finding the Academy: the nearest one that generated, or the one this world was guaranteed (see
 * {@link AcademySites}). A Plus senses which way it lies, and Konsō can send a soul there. The search
 * is slow, so it runs only when a world is new, when a soul becomes a Plus (or logs in as one that
 * does not know the way), when Konsō is offered or performed, and by command, and its answer is kept
 * in the soul's {@code academy_pos}.
 */
public final class AcademyLocator {
    /** The spots tried around an Academy's corner when sending a soul there, the corner first. */
    private static final int[][] LANDING_OFFSETS = {
            {0, 0}, {1, 1}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {-1, -1}, {1, -1}, {-1, 1}};
    private static final ResourceKey<StructureSet> ACADEMY_SET = ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("academy"));
    /** The spacing in the Academy's structure set, if it cannot be read. */
    private static final int DEFAULT_SPACING = 24;

    private AcademyLocator() {
    }

    /**
     * The nearest Academy to a spot in the Overworld, building the guaranteed one if none generated
     * within the configured radius and the config allows it. Slow: never call it every tick.
     */
    @Nullable
    public static BlockPos nearest(ServerLevel overworld, BlockPos from) {
        int radius = BleachConfig.get(BleachConfig.ACADEMY_LOCATE_RADIUS_CHUNKS);
        // The search counts in rings of placement cells, not chunks.
        BlockPos found = radius > 0 ? overworld.findNearestMapStructure(BleachTags.ACADEMY, from,
                Mth.positiveCeilDiv(radius, spacing(overworld)), false) : null;
        double reach = radius * 16.0;
        boolean inReach = found != null && horizontalDistanceSqr(from, found) <= reach * reach;
        AcademySites sites = AcademySites.get(overworld);
        BlockPos guaranteed = sites.guaranteed().orElse(null);
        if (!inReach && guaranteed == null && BleachConfig.get(BleachConfig.ACADEMY_GUARANTEE)) {
            guaranteed = sites.ensureGuaranteed(overworld);
        }
        if (found == null || guaranteed == null) {
            return found != null ? found : guaranteed;
        }
        return horizontalDistanceSqr(from, found) <= horizontalDistanceSqr(from, guaranteed) ? found : guaranteed;
    }

    /** How many chunks apart Academies are spread, as their structure set says. */
    private static int spacing(ServerLevel level) {
        StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ACADEMY_SET);
        return set != null && set.placement() instanceof RandomSpreadStructurePlacement spread
                ? Math.max(1, spread.spacing()) : DEFAULT_SPACING;
    }

    /**
     * In a world just created, the guaranteed Academy is built straight away if none generated in
     * reach, before anyone has built on the ground it takes.
     */
    public static void prepareNewWorld(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld.getGameTime() == 0L && BleachConfig.get(BleachConfig.ACADEMY_GUARANTEE)
                && AcademySites.get(overworld).guaranteed().isEmpty()) {
            nearest(overworld, overworld.getSharedSpawnPos());
        }
    }

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** "to the north-east", for the way from one spot to another. */
    public static Component direction(BlockPos from, BlockPos to) {
        return Component.translatable(ShinigamiLogic.compassKey(to.getX() - from.getX(), to.getZ() - from.getZ()));
    }

    /** Roughly how far, rounded to 50 blocks. */
    public static long distance(BlockPos from, BlockPos to) {
        return ShinigamiLogic.roundedDistance(Math.sqrt(horizontalDistanceSqr(from, to)));
    }

    /**
     * A new Plus senses the nearest Academy and remembers where it is. Outside the Overworld they only
     * learn that it is far away.
     */
    public static void sense(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        boolean inOverworld = player.level().dimension() == Level.OVERWORLD;
        BlockPos from = inOverworld ? player.blockPosition() : overworld.getSharedSpawnPos();
        BlockPos academy = nearest(overworld, from);
        if (academy == null) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        data.setShinigami(data.shinigami().withAcademyPos(Optional.of(academy)));
        Component message = inOverworld
                ? Component.translatable("message.bleach.academy_sensed", direction(from, academy), distance(from, academy))
                : Component.translatable("message.bleach.academy_sensed.elsewhere");
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** A meditating Plus is reminded which way the Academy lies. */
    public static void remind(ServerPlayer player, SoulData data) {
        Optional<BlockPos> academy = data.shinigami().academyPos();
        if (data.state() != SoulState.PLUS || academy.isEmpty() || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos from = player.blockPosition();
        player.displayClientMessage(Component.translatable("message.bleach.academy_direction",
                direction(from, academy.get()), distance(from, academy.get())), true);
    }

    /**
     * The Academy this soul would be sent to: the one it was last told about, or else the nearest,
     * which it then remembers. Slow when the soul knows of none yet.
     */
    @Nullable
    public static BlockPos academyFor(ServerPlayer player) {
        SoulData data = SoulLogic.get(player);
        Optional<BlockPos> known = data.shinigami().academyPos();
        if (known.isPresent()) {
            return known.get();
        }
        ServerLevel overworld = player.server.overworld();
        BlockPos from = player.level().dimension() == Level.OVERWORLD ? player.blockPosition() : overworld.getSharedSpawnPos();
        BlockPos academy = nearest(overworld, from);
        if (academy != null) {
            data.setShinigami(data.shinigami().withAcademyPos(Optional.of(academy)));
        }
        return academy;
    }

    /**
     * Sends a soul that has just passed on to the gates of the Academy, landing on safe ground beside
     * its corner.
     *
     * @return false, leaving the soul where it is, if there is no Academy or nowhere safe to land
     */
    public static boolean sendTo(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos target = academyFor(player);
        if (target != null) {
            for (int[] offset : LANDING_OFFSETS) {
                int x = target.getX() + offset[0];
                int z = target.getZ() + offset[1];
                overworld.getChunk(x >> 4, z >> 4);
                int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (SafeLanding.tryMoveTo(player, overworld, new Vec3(x + 0.5, y, z + 0.5))) {
                    player.sendSystemMessage(Component.translatable("message.bleach.konso.arrived")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    return true;
                }
            }
        }
        player.sendSystemMessage(Component.translatable("message.bleach.konso.no_academy")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return false;
    }
}
