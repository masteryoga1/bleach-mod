package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.world.LasNochesResidents;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Grand Fisher as a boss of the ledger, and its ambush. One about at a time, counted as its body or, once that is gone, a
 * lure of its that has not yet faded; away {@code [bosses.grand_fisher] return_days} once its body dies, killed or
 * purified alike. No tear brings it, and for now it comes only by a summon: only its lure, laid out of the soul's sight
 * to sob and wait, as its body lies hidden in the ground. When the soul comes near her or strikes her, the body bursts
 * out of the ground beside her.
 */
public final class GrandFisherComing implements Boss {
    public static final String ID = "grand_fisher";
    public static final GrandFisherComing BOSS = new GrandFisherComing();

    /** Spots tried for a laid lure, the first by water winning. Water within this many blocks across is by water. */
    private static final int LAY_TRIES = 24;
    private static final int BY_WATER = 2;
    /** How far from its lure the body bursts out, and how high above or below her at most. */
    private static final double BURST_MIN = 2.0;
    private static final double BURST_MAX = 6.0;
    private static final int BURST_TRIES = 24;
    private static final int BURST_MAX_RISE = 6;

    private GrandFisherComing() {
    }

    @Override
    public String id() {
        return ID;
    }

    /** Its body only: its lure dies on her own, and her death is never the boss's. */
    @Override
    public boolean isBody(LivingEntity entity) {
        return entity instanceof GrandFisher;
    }

    @Override
    public int returnDays() {
        return BleachConfig.get(BleachConfig.GRAND_FISHER_RETURN_DAYS);
    }

    /** Its bodies alive in every world, and its lures alive with no body left to them, which still hold it. */
    @Override
    public int present(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(ModEntities.GRAND_FISHER.get(), GrandFisher::isAlive).size();
            count += level.getEntities(ModEntities.GRAND_FISHER_LURE.get(), GrandFisherComing::alone).size();
        }
        return count;
    }

    private static boolean alone(GrandFisherLure lure) {
        GrandFisher body = lure.body();
        return lure.isAlive() && (body == null || !body.isAlive());
    }

    /** Its lure laid for the player, 12 to 24 blocks off and out of their sight, as it comes for now; no body yet. */
    @Nullable
    @Override
    public Component summon(MinecraftServer server, @Nullable ServerPlayer near) {
        if (near == null) {
            return Component.translatable("commands.bleach.boss.summon.no_player", name());
        }
        return lay(near, GargantaEvents.SPOT_MIN_DISTANCE, GargantaEvents.SPOT_MAX_DISTANCE) != null ? null
                : Component.translatable("commands.bleach.boss.summon.no_ground", near.getDisplayName(), name());
    }

    // ---- The ambush ----

    /**
     * Lays its lure for this soul: on open ground between these distances from it, out of its sight, by water if any
     * spot tried is (a river's bank), facing it. Null if no spot was found or the world refused her.
     */
    @Nullable
    public static GrandFisherLure lay(ServerPlayer prey, double minDistance, double maxDistance) {
        ServerLevel level = prey.serverLevel();
        EntityType<GrandFisherLure> type = ModEntities.GRAND_FISHER_LURE.get();
        Vec3 spot = null;
        for (int i = 0; i < LAY_TRIES; i++) {
            Vec3 at = SpawnSpots.openGround(level, prey, type, minDistance, maxDistance, 1, GargantaEvents.SPOT_MAX_RISE);
            if (at == null || !outOfSight(level, prey, at)) {
                continue;
            }
            if (byWater(level, at)) {
                spot = at;
                break;
            }
            spot = spot == null ? at : spot;
        }
        return spot != null ? GrandFisherLure.grow(level, spot, facing(spot, prey.position())) : null;
    }

    /** Whether she would stand up here unseen by this soul: behind it, or hidden from its eyes by a block. */
    public static boolean outOfSight(ServerLevel level, Player prey, Vec3 at) {
        Vec3 look = Vec3.directionFromRotation(0.0F, prey.getYHeadRot());
        Vec3 to = at.subtract(prey.position());
        return look.x * to.x + look.z * to.z < 0.0
                || LasNochesResidents.hiddenFrom(level, at, List.of(prey.getEyePosition()));
    }

    /** Whether water lies within {@link #BY_WATER} blocks across of this spot, at its height or a block below. */
    private static boolean byWater(ServerLevel level, Vec3 at) {
        BlockPos centre = BlockPos.containing(at);
        // Read in place: the cells walked are one moving position.
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BY_WATER, -1, -BY_WATER),
                centre.offset(BY_WATER, 0, BY_WATER))) {
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Its body bursts out of the ground beside this lure, at the prey that came near her or struck her: on open ground 2
     * to 6 blocks from her, facing the prey and set on it, with her on her stalk. On Peaceful, where its body would
     * vanish at once, or with no ground there, nothing comes, and she fades. Null if nothing came.
     */
    @Nullable
    public static GrandFisher burst(ServerLevel level, GrandFisherLure lure, LivingEntity prey) {
        EntityType<GrandFisher> type = ModEntities.GRAND_FISHER.get();
        Vec3 at = level.getDifficulty() == Difficulty.PEACEFUL ? null
                : SpawnSpots.openGround(level, lure, type, BURST_MIN, BURST_MAX, BURST_TRIES, BURST_MAX_RISE);
        GrandFisher fisher = at != null ? type.create(level) : null;
        if (fisher == null) {
            lure.fadeNow();
            return null;
        }
        float yaw = facing(at, prey.position());
        fisher.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        fisher.setYHeadRot(yaw);
        fisher.setYBodyRot(yaw);
        EventHooks.finalizeMobSpawn(fisher, level, level.getCurrentDifficultyAt(BlockPos.containing(at)),
                MobSpawnType.EVENT, null);
        fisher.takeLure(lure);
        if (!level.addFreshEntity(fisher)) {
            lure.fadeNow();
            return null;
        }
        fisher.setTarget(prey);
        fisher.breakGround(level);
        return fisher;
    }

    /** The turn that faces from here to there. */
    private static float facing(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }
}
