package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.GrandFisherLure;
import io.github.masteryoga1.bleach.registry.ModEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Grand Fisher as a boss of the ledger: one about at a time, counted as its body or, once that is gone, a lure of its
 * that has not yet faded; away {@code [bosses.grand_fisher] return_days} once its body dies, killed or purified alike;
 * and summoned on open ground beside a player, where it grows its lure on its first tick. No tear brings it.
 */
public final class GrandFisherComing implements Boss {
    public static final String ID = "grand_fisher";
    public static final GrandFisherComing BOSS = new GrandFisherComing();

    /** How far from the player a summoned Grand Fisher lands: beside them, with room for its lure between. */
    private static final double SUMMON_MIN = 4.0;
    private static final double SUMMON_MAX = 8.0;
    private static final int SUMMON_TRIES = 24;
    private static final int SUMMON_MAX_RISE = 6;

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

    /** On open ground a few blocks from the player, facing them; it grows its lure on its first tick. */
    @Nullable
    @Override
    public Component summon(MinecraftServer server, @Nullable ServerPlayer near) {
        if (near == null) {
            return Component.translatable("commands.bleach.boss.summon.no_player", name());
        }
        ServerLevel level = near.serverLevel();
        EntityType<GrandFisher> type = ModEntities.GRAND_FISHER.get();
        Vec3 at = SpawnSpots.openGround(level, near, type, SUMMON_MIN, SUMMON_MAX, SUMMON_TRIES, SUMMON_MAX_RISE);
        GrandFisher fisher = at != null ? type.create(level) : null;
        if (fisher == null) {
            return Component.translatable("commands.bleach.boss.summon.no_ground", near.getDisplayName(), name());
        }
        float yaw = (float) (Mth.atan2(near.getZ() - at.z, near.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
        fisher.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        fisher.setYHeadRot(yaw);
        fisher.setYBodyRot(yaw);
        EventHooks.finalizeMobSpawn(fisher, level, level.getCurrentDifficultyAt(BlockPos.containing(at)),
                MobSpawnType.COMMAND, null);
        return level.addFreshEntity(fisher) ? null
                : Component.translatable("commands.bleach.boss.summon.no_ground", near.getDisplayName(), name());
    }
}
