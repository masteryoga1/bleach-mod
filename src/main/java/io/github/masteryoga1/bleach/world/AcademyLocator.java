package io.github.masteryoga1.bleach.world;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Finding the Academy: the nearest one that generated, or the one this world was guaranteed (see
 * {@link AcademySites}). A Plus senses which way it lies, and Konsō can send a soul there. The search
 * is slow, so it runs only when a soul becomes a Plus, on Konsō, and by command, and its answer is
 * kept in the soul's {@code academy_pos}.
 */
public final class AcademyLocator {
    /** The spots tried around an Academy's corner when sending a soul there, the corner first. */
    private static final int[][] LANDING_OFFSETS = {
            {0, 0}, {1, 1}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {-1, -1}, {1, -1}, {-1, 1}};

    private AcademyLocator() {
    }

    /**
     * The nearest Academy to a spot in the Overworld, building the guaranteed one if there is none and
     * the config allows it. Slow: never call it every tick.
     */
    @Nullable
    public static BlockPos nearest(ServerLevel overworld, BlockPos from) {
        int radius = BleachConfig.get(BleachConfig.ACADEMY_LOCATE_RADIUS_CHUNKS);
        BlockPos found = radius > 0 ? overworld.findNearestMapStructure(BleachTags.ACADEMY, from, radius, false) : null;
        AcademySites sites = AcademySites.get(overworld);
        BlockPos guaranteed = sites.guaranteed().orElse(null);
        if (found == null && guaranteed == null && BleachConfig.get(BleachConfig.ACADEMY_GUARANTEE)) {
            guaranteed = sites.ensureGuaranteed(overworld);
        }
        if (found == null || guaranteed == null) {
            return found != null ? found : guaranteed;
        }
        return horizontalDistanceSqr(from, found) <= horizontalDistanceSqr(from, guaranteed) ? found : guaranteed;
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
     * Sends a soul that has just passed on to the gates of the Academy, landing on safe ground beside
     * its corner.
     *
     * @return false, leaving the soul where it is, if there is no Academy or nowhere safe to land
     */
    public static boolean sendTo(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        SoulData data = SoulLogic.get(player);
        BlockPos target = data.shinigami().academyPos().orElse(null);
        if (target == null) {
            BlockPos from = player.level().dimension() == Level.OVERWORLD ? player.blockPosition() : overworld.getSharedSpawnPos();
            target = nearest(overworld, from);
        }
        if (target != null) {
            data.setShinigami(data.shinigami().withAcademyPos(Optional.of(target)));
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
