package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.registry.ModEntities;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Shrieker as a boss of the ledger: one about at a time, counted alive or still in the tear that carries it, away
 * {@code [bosses.shrieker] return_days} once it dies, killed or purified alike, and summoned through a Garganta beside a
 * player, as {@code /bleach garganta … shrieker} brings one. That command still brings one whatever this says; this
 * summon keeps to one about. It comes by itself at night for a strong soul ({@link #tryComing}).
 */
public final class ShriekerComing implements Boss {
    public static final String ID = "shrieker";
    public static final ShriekerComing BOSS = new ShriekerComing();

    private ShriekerComing() {
    }

    @Override
    public String id() {
        return ID;
    }

    /** Any Shrieker but a field trial's practice one, whose death is its student's lesson. */
    @Override
    public boolean isBody(LivingEntity entity) {
        return entity instanceof Shrieker shrieker && !shrieker.isPractice();
    }

    @Override
    public int returnDays() {
        return BleachConfig.get(BleachConfig.SHRIEKER_RETURN_DAYS);
    }

    /** The Shriekers alive in every world, and the tears in every world that still carry one: 30 ticks pass first. */
    @Override
    public int present(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(ModEntities.SHRIEKER.get(), Entity::isAlive).size();
            count += level.getEntities(ModEntities.GARGANTA.get(), rift -> rift.pending().contains(HollowKind.SHRIEKER))
                    .size();
        }
        return count;
    }

    /** A tear beside the player now, by the path {@code /bleach garganta} takes: no cooldown set, no Hunter after it. */
    @Nullable
    @Override
    public Component summon(MinecraftServer server, @Nullable ServerPlayer near) {
        if (near == null) {
            return Component.translatable("commands.bleach.boss.summon.no_player", name());
        }
        if (GargantaEvents.openArrival(near, SoulLogic.get(near), HollowKind.SHRIEKER, near.level().getGameTime(), true)) {
            return null;
        }
        return Component.translatable("commands.bleach.boss.summon.no_ground", near.getDisplayName(), name());
    }

    // ---- Its coming at night ----

    /**
     * Called by {@link GargantaEvents} once a soul's roll for a Garganta has passed and Arrancar did not come, before the
     * Menos pick: whether this arrival brings the Shrieker instead. Only at night, for a strong soul that is no Hollow, for
     * a share of the rolls ({@link #draws}), only where a Garganta may open at all ({@link GargantaEvents#mayArriveNear}:
     * never on Peaceful, with mob spawning off, outside the Overworld or on the game-test server), and only while none is
     * about and its days away are over ({@link #come}).
     *
     * @return false, changing nothing, unless a tear bringing it opened
     */
    public static boolean tryComing(ServerPlayer player, SoulData data, long now, boolean night, double roll) {
        return draws(data, night, roll) && GargantaEvents.mayArriveNear(player, data, now) && come(player, data, now);
    }

    /** Whether this soul's passed roll draws the Shrieker: at night, strong enough, no Hollow, the roll under the share. */
    public static boolean draws(SoulData data, boolean night, double roll) {
        return night && data.state() != SoulState.HOLLOW
                && data.maxReiryoku() >= BleachConfig.get(BleachConfig.SHRIEKER_MIN_MAX_REIRYOKU)
                && roll < BleachConfig.get(BleachConfig.SHRIEKER_ARRIVAL_SHARE);
    }

    /**
     * Brings it for this soul as a natural arrival if it may come by itself now ({@link Boss#mayCome}, on the ledger's
     * clock): the soul's cooldown is set from {@code now}, its arrival clock, and the tear beeps every Denreishinki in
     * range, as any does. The rest of {@link #tryComing} past the roll and the world's rules, which tests call directly.
     */
    public static boolean come(ServerPlayer player, SoulData data, long now) {
        return BOSS.mayCome(player.server, BossLedger.now(player.server))
                && GargantaEvents.openArrival(player, data, HollowKind.SHRIEKER, now, false);
    }
}
