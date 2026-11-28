package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.Runuganga;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runuganga as a boss of the ledger, one at a time and away return_days after a death, and the rules he rises by: out of
 * Hueco Mundo's level sand ({@link Runuganga#onTheSand}) for an intruder on it ({@link #draws}) past its landing grace,
 * 8 to 16 blocks from it, over three seconds with sand thrown up and a rumble; never on Peaceful, with mob spawning off,
 * or for a minute after he sank away. He loads no chunk and rises only where creatures tick. Now that water lets a blow
 * end him, a look is made for him every five seconds while a player is in Hueco Mundo ({@link #look}). A summon raises
 * him near its player as a look would, keeping none of the rules of when.
 */
public final class RunugangaComing implements Boss {
    public static final String ID = "runuganga";
    public static final RunugangaComing BOSS = new RunugangaComing();

    /** How near and how far from the intruder he rises: in sight, out of reach. */
    public static final double NEAREST = 8.0, FURTHEST = 16.0;
    /** How long after he sinks away no look brings him: a minute. */
    public static final int REST_TICKS = 1_200;
    /** How often a look is made for him: every five seconds. */
    public static final int LOOK_TICKS = 100;
    private static final int TRIES = 16;
    /** The ledger's game time until which he rests; the server's one, put back as it stops. */
    private static long restUntil;

    private RunugangaComing() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isBody(LivingEntity entity) {
        return entity instanceof Runuganga;
    }

    @Override
    public int returnDays() {
        return BleachConfig.get(BleachConfig.RUNUGANGA_RETURN_DAYS);
    }

    /** His bodies alive in every world, standing, rising or lying as sand, and the tears that still carry one. */
    @Override
    public int present(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(ModEntities.RUNUGANGA.get(), Runuganga::isAlive).size();
            count += level.getEntities(ModEntities.GARGANTA.get(), rift -> rift.pending().contains(HollowKind.RUNUGANGA))
                    .size();
        }
        return count;
    }

    /** He rises near the player as a look would bring him, in whatever world the player stands. */
    @Nullable
    @Override
    public Component summon(MinecraftServer server, @Nullable ServerPlayer near) {
        if (near == null) {
            return Component.translatable("commands.bleach.boss.summon.no_player", name());
        }
        return comeNear(near.serverLevel(), near.getRandom(), near) != null ? null
                : Component.translatable("commands.bleach.boss.summon.no_ground", near.getDisplayName(), name());
    }

    /** Each server tick, a look for the souls in Hueco Mundo ({@link #look}). */
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(BleachWorlds.HUECO_MUNDO);
        if (level != null) {
            look(server, level, level.players(), server.getTickCount());
        }
    }

    /**
     * The look on this tick of the server's for these souls in Hueco Mundo: a roll every {@link #LOOK_TICKS}, on a server
     * that looks for him by itself, while a soul is there; what rose, or null.
     */
    @Nullable
    public static Runuganga look(MinecraftServer server, ServerLevel level, List<? extends Player> souls, int tick) {
        return lookNow(tick) && looksBy(server) && !souls.isEmpty() ? roll(level, level.getRandom(), souls) : null;
    }

    /** Whether a look is made on this tick of the server's. */
    public static boolean lookNow(int tickCount) {
        return tickCount % LOOK_TICKS == 0;
    }

    /** Whether this server looks for him by itself: never a game test's, whose worlds he would rise in unasked. */
    public static boolean looksBy(MinecraftServer server) {
        return !(server instanceof GameTestServer);
    }

    /** One look: he rises near one of these souls that draws him if the world allows, he may come and is not resting. */
    @Nullable
    public static Runuganga roll(ServerLevel level, RandomSource random, List<? extends Player> souls) {
        MinecraftServer server = level.getServer();
        List<? extends Player> drawn = souls.stream().filter(RunugangaComing::draws).toList();
        if (drawn.isEmpty() || resting(server) || !worldAllows(level) || !BOSS.mayCome(server, BossLedger.now(server))) {
            return null;
        }
        return comeNear(level, random, drawn.get(random.nextInt(drawn.size())));
    }

    /** Whether this soul draws him: an intruder ({@link Runuganga#intrudes}) on his sand, above the Forest of Menos. */
    public static boolean draws(Player soul) {
        return Runuganga.intrudes(soul) && soul.getY() >= HuecoMundoPlan.CAVERN_CEILING_Y
                && Runuganga.onTheSand(soul.getBlockX(), soul.getBlockZ());
    }

    /** Whether this world lets him come by himself: Hueco Mundo, not on Peaceful, with mobs spawning. */
    public static boolean worldAllows(ServerLevel level) {
        return level.dimension() == BleachWorlds.HUECO_MUNDO && level.getDifficulty() != Difficulty.PEACEFUL
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    /** On the first of {@link #TRIES} columns about this soul whose top fits ({@link #fits}) he rises; or null. */
    @Nullable
    public static Runuganga comeNear(ServerLevel level, RandomSource random, Player soul) {
        for (int i = 0; i < TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0, out = Mth.nextDouble(random, NEAREST, FURTHEST);
            int x = Mth.floor(soul.getX() + Math.cos(angle) * out), z = Mth.floor(soul.getZ() + Math.sin(angle) * out);
            LevelChunk chunk = ticking(level, x, z);
            BlockPos spot = chunk == null ? null
                    : new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1, z);
            Runuganga made = spot != null && fits(level, spot) ? rise(level, spot, soul.position()) : null;
            if (made != null) {
                return made;
            }
        }
        return null;
    }

    /**
     * Whether he may rise on this cell: in a ticking chunk, every chunk his box reads made; in Hueco Mundo on his sand's
     * ground, elsewhere on a sturdy top; with no fluid and room for his height.
     */
    public static boolean fits(ServerLevel level, BlockPos spot) {
        boolean hueco = level.dimension() == BleachWorlds.HUECO_MUNDO;
        if (hueco && !Runuganga.onTheSand(spot.getX(), spot.getZ()) || ticking(level, spot.getX(), spot.getZ()) == null) {
            return false;
        }
        // noCollision reads a block past his box all round, and would wait on a chunk there still being made.
        int reach = Mth.ceil(Runuganga.WIDTH / 2.0F) + 1;
        Vec3 feet = Vec3.atBottomCenterOf(spot);
        return ChunkPos.rangeClosed(new ChunkPos(spot.offset(-reach, 0, -reach)), new ChunkPos(spot.offset(reach, 0, reach)))
                        .allMatch(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null)
                && (hueco ? HuecoMundoSpawns.onHollowGround(level, spot)
                        : level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP))
                && level.getFluidState(spot).isEmpty()
                && level.noCollision(ModEntities.RUNUGANGA.get().getSpawnAABB(feet.x, feet.y, feet.z));
    }

    /** He rises out of the ground on this cell, facing this point; null if the world would not take him. */
    @Nullable
    private static Runuganga rise(ServerLevel level, BlockPos spot, Vec3 toward) {
        Runuganga body = ModEntities.RUNUGANGA.get().create(level);
        if (body == null) {
            return null;
        }
        Vec3 feet = Vec3.atBottomCenterOf(spot);
        float yaw = (float) (Mth.atan2(toward.z - feet.z, toward.x - feet.x) * Mth.RAD_TO_DEG) - 90.0F;
        body.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        body.setYHeadRot(yaw);
        EventHooks.finalizeMobSpawn(body, level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        body.rise();
        return level.addFreshEntity(body) ? body : null;
    }

    /** This column's chunk, if it is made now and its creatures tick; never loaded for the asking. */
    @Nullable
    private static LevelChunk ticking(ServerLevel level, int x, int z) {
        return level.isPositionEntityTicking(new BlockPos(x, 0, z)) ? level.getChunkSource().getChunkNow(x >> 4, z >> 4)
                : null;
    }

    /**
     * A soul a Garganta has just set down: in Hueco Mundo its landing is noted, for its grace. A grace still running keeps
     * its first landing, so going back and forth lengthens none.
     */
    public static void landed(Entity entity) {
        if (entity instanceof Player soul && soul.level().dimension() == BleachWorlds.HUECO_MUNDO
                && !inLandingGrace(soul, soul.level().getGameTime())) {
            soul.setData(SoulAttachments.HUECO_LANDING, soul.level().getGameTime());
        }
    }

    /** Whether this soul is within landing_grace_seconds of its last landing: never if it never landed. */
    public static boolean inLandingGrace(Player soul, long now) {
        long landed = soul.getData(SoulAttachments.HUECO_LANDING);
        return landed <= now && now - landed < BleachConfig.get(BleachConfig.RUNUGANGA_LANDING_GRACE_SECONDS) * 20L;
    }

    /** Whether he rests now, after sinking away, so that no look brings him. */
    public static boolean resting(MinecraftServer server) {
        return BossLedger.now(server) < restUntil;
    }

    /** He rests this many ticks from now, as {@link #REST_TICKS} after sinking away; 0 ends a rest. */
    public static void rest(MinecraftServer server, int ticks) {
        restUntil = BossLedger.now(server) + ticks;
    }

    /** A closed world's rest ends with it, so none outlives it into the next world a game opens. */
    static void onServerStopped(ServerStoppedEvent event) {
        restUntil = 0L;
    }
}
