package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The way to the Shin'ō Academy, which stands in Seireitei (see {@link SeireiteiPlan} and {@link AcademySite}). In Soul
 * Society a soul senses which way it lies and how far; anywhere else, only that it is in Soul Society. Konsō sends a soul
 * to its gates, and so does dying with no spawn of one's own (see
 * {@link io.github.masteryoga1.bleach.soul.HomeWorldRespawn}).
 */
public final class AcademyLocator {
    /** The middle of the Academy's gate, which a soul's sense points at: (-192, 73, 231). */
    public static final BlockPos GATE = new BlockPos(SeireiteiPlan.ACADEMY_X, SeireiteiPlan.ACADEMY_FLOOR_Y,
            SeireiteiPlan.ACADEMY_Z + 7);
    /** Where a soul sent to the Academy lands, on the path two blocks inside its gate: (-193.5, 73, 231.5). */
    public static final Vec3 LANDING = new Vec3(SeireiteiPlan.ACADEMY_LANDING_X, SeireiteiPlan.ACADEMY_FLOOR_Y,
            SeireiteiPlan.ACADEMY_LANDING_Z);
    /** Facing west, into the courtyard and the hall's door. */
    public static final float LANDING_YAW = 90.0F;

    private AcademyLocator() {
    }

    /** Whether an instructor standing in this world sends the Plus it frees on to the Academy: anywhere but Soul Society. */
    public static boolean sendsOnFrom(ResourceKey<Level> world) {
        return world != BleachWorlds.SOUL_SOCIETY;
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

    /** What a soul here senses of the Academy: which way and how far in Soul Society, and only where it is elsewhere. */
    public static Component senseLine(ResourceKey<Level> world, BlockPos from) {
        return world == BleachWorlds.SOUL_SOCIETY
                ? Component.translatable("message.bleach.academy_sensed", direction(from, GATE), distance(from, GATE))
                : Component.translatable("message.bleach.academy_sensed.elsewhere");
    }

    /** What a meditating Plus here is reminded of: as {@link #senseLine}, for the action bar. */
    public static Component remindLine(ResourceKey<Level> world, BlockPos from) {
        return world == BleachWorlds.SOUL_SOCIETY
                ? Component.translatable("message.bleach.academy_direction", direction(from, GATE), distance(from, GATE))
                : Component.translatable("message.bleach.academy_direction.elsewhere");
    }

    /** A new Plus senses the Academy. */
    public static void sense(ServerPlayer player) {
        player.sendSystemMessage(senseLine(player.level().dimension(), player.blockPosition()).copy()
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** A meditating Plus is reminded where the Academy lies. */
    public static void remind(ServerPlayer player, SoulData data) {
        if (data.state() == SoulState.PLUS) {
            player.displayClientMessage(remindLine(player.level().dimension(), player.blockPosition()), true);
        }
    }

    /** The command {@code /bleach academy} suggests: a teleport to the Academy's landing. */
    public static String teleportCommand() {
        return String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %d %.1f", BleachWorlds.SOUL_SOCIETY.location(),
                LANDING.x, SeireiteiPlan.ACADEMY_FLOOR_Y, LANDING.z);
    }

    /**
     * Where a soul sent to the Academy comes out, the Academy first built where it was never placed (see
     * {@link AcademySite#ensureBuilt}): at its landing, or else at Soul Society's Senkaimon court; null if neither will
     * hold it. Loads, and may make, the chunks there.
     */
    @Nullable
    public static Vec3 landing(Entity soul, ServerLevel soulSociety) {
        AcademySite.ensureBuilt(soulSociety);
        Vec3 spot = TravelEvents.landing(soul, soulSociety, LANDING);
        return spot != null ? spot : TravelEvents.landing(soul, soulSociety, TravelLogic.COURT);
    }

    /**
     * Sends a soul that has just passed on to the gates of the Academy in Soul Society. A bed or anchor it set outside
     * Soul Society before it passed on is let go, so it is not drawn back there when it dies.
     *
     * @return false, leaving the soul where it is, if there is no Soul Society or nowhere safe to land
     */
    public static boolean sendTo(ServerPlayer player) {
        ServerLevel soulSociety = BleachWorlds.level(player.server, BleachWorlds.SOUL_SOCIETY);
        Vec3 spot = soulSociety != null ? landing(player, soulSociety) : null;
        if (spot == null) {
            player.sendSystemMessage(Component.translatable("message.bleach.konso.no_academy")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return false;
        }
        if (player.getRespawnPosition() != null && player.getRespawnDimension() != BleachWorlds.SOUL_SOCIETY) {
            player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
        }
        // A soul sent on as it respawns is not yet its connection's player, which is what the teleport moves.
        player.moveTo(spot.x, spot.y, spot.z, LANDING_YAW, 0.0F);
        player.teleportTo(soulSociety, spot.x, spot.y, spot.z, LANDING_YAW, 0.0F);
        player.sendSystemMessage(Component.translatable("message.bleach.konso.arrived")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return true;
    }
}
