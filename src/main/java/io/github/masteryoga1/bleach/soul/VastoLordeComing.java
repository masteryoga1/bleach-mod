package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.VastoLorde;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Where a Vasto Lorde may come by itself, and how many may be about. Only in Hueco Mundo, on its dunes at least
 * {@code [bosses.vasto_lorde] min_radius} from the centre column and never in the Forest of Menos under them; with no
 * soul within {@link #NEAREST} and none that could see it stand up ({@link #unseen}); and while that world holds fewer
 * than {@code max_alive} alive, counted apart from the Menos and from the players who became Vasto Lorde. It reads only
 * chunks made now and stands only in those whose creatures tick; it loads or waits on none. Each {@code look_seconds}
 * while any soul is there, on the {@code chance}, one comes up to {@link #FURTHEST} from a soul far out ({@link #roll});
 * never on a game-test server, where tests roll. Commands keep none of these rules.
 */
public final class VastoLordeComing {
    /** How near a soul one may stand up: as near as vanilla lets a monster spawn by a player. */
    public static final double NEAREST = 24.0;
    /** How far from the soul it comes to: as far as it looks for prey, so the soul is in its mind as it stands. */
    public static final double FURTHEST = 48.0;
    /** The columns tried in a look, twice a resident's: the band is wide, and much of it may lie where nothing ticks. */
    private static final int TRIES = 24;
    /** How far a soul is asked whether it would see one stand up: as far as a Vasto Lorde is ever sent to a client. */
    private static final double SIGHT = 160.0;

    private VastoLordeComing() {
    }

    /** Each {@code look_seconds}, never on a game-test server, while any soul is in Hueco Mundo, it takes a look. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(BleachWorlds.HUECO_MUNDO);
        if (level != null && lookNow(server.getTickCount(), BleachConfig.get(BleachConfig.VASTO_LORDE_LOOK_SECONDS))
                && !(server instanceof GameTestServer) && !level.players().isEmpty()) {
            roll(level, level.getRandom(), false, level.players());
        }
    }

    /** Whether a look falls on this server tick, when one comes every this many seconds. */
    public static boolean lookNow(int tickCount, int seconds) {
        return tickCount % (seconds * 20) == 0;
    }

    /**
     * One look: one of these souls that draws one ({@link #draws}) is drawn, and on the {@code chance} a Vasto Lorde
     * comes near it if one may come, unseen by all of them ({@link #comeNear}). Forced, the chance is not drawn, and
     * every other rule holds. Only the soul and the chance are drawn with this random, the columns with the world's. The
     * one that came, or null.
     */
    @Nullable
    public static VastoLorde roll(ServerLevel level, RandomSource random, boolean forced, List<? extends Player> souls) {
        List<? extends Player> far = souls.stream().filter(VastoLordeComing::draws).toList();
        if (far.isEmpty() || !forced && random.nextDouble() >= BleachConfig.get(BleachConfig.VASTO_LORDE_CHANCE)) {
            return null;
        }
        return comeNear(level, level.getRandom(), far.get(random.nextInt(far.size())).position(), souls);
    }

    /**
     * Whether this soul draws one: alive, playing by survival's rules, seeing spirits, far enough out ({@link #farOut})
     * and not down in the Forest of Menos. A Hollow or an Arrancar draws one too, though by default it leaves them be,
     * so two near one still hold the count.
     */
    public static boolean draws(Player soul) {
        return soul.isAlive() && HollowPlayerLogic.survivalRules(soul) && SoulLogic.seesSpirits(soul)
                && soul.getY() >= HuecoMundoPlan.CAVERN_CEILING_Y && farOut(soul.getBlockX(), soul.getBlockZ());
    }

    /**
     * Whether one may come now: this is Hueco Mundo, not in Peaceful, with mob spawning on, and fewer than
     * {@code max_alive} live there now ({@link VastoLorde#aliveIn}). Never the Menos's count, nor the players' cap.
     */
    public static boolean mayCome(ServerLevel level) {
        return level.dimension() == BleachWorlds.HUECO_MUNDO && level.getDifficulty() != Difficulty.PEACEFUL
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                && VastoLorde.aliveIn(level) < BleachConfig.get(BleachConfig.VASTO_LORDE_MAX_ALIVE);
    }

    /** Whether this column lies far enough out on the dunes: at least {@code min_radius} from the centre column. */
    public static boolean farOut(int x, int z) {
        return HuecoMundoPlan.radius(x, z) >= BleachConfig.get(BleachConfig.VASTO_LORDE_MIN_RADIUS);
    }

    /**
     * One look about this point, if one may come ({@link #mayCome}): up to {@link #TRIES} columns are drawn between
     * {@link #NEAREST} and {@link #FURTHEST} across from it, each read only in a chunk made now whose creatures tick, and
     * the first whose top fits, with these souls about ({@link #spawnAt}), gets one standing on it. The one that came,
     * or null.
     */
    @Nullable
    public static VastoLorde comeNear(ServerLevel level, RandomSource random, Vec3 about, List<? extends Player> souls) {
        if (!mayCome(level)) {
            return null;
        }
        for (int i = 0; i < TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0, out = Mth.nextDouble(random, NEAREST, FURTHEST);
            int x = Mth.floor(about.x + Math.cos(angle) * out), z = Mth.floor(about.z + Math.sin(angle) * out);
            LevelChunk chunk = ticking(level, x, z);
            VastoLorde made = chunk == null ? null : place(level,
                    new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1, z), souls);
            if (made != null) {
                return made;
            }
        }
        return null;
    }

    /**
     * A Vasto Lorde set down standing on this cell, if one may come ({@link #mayCome}) and may come here, else null.
     * Here is at or above the Forest of Menos's ceiling, far enough out ({@link #farOut}), in a chunk made now whose
     * creatures tick, on white sand or white sandstone with room to stand and no fluid, with no soul's eyes within
     * {@link #NEAREST}, behind it or not, and out of every soul's sight ({@link #unseen}).
     */
    @Nullable
    public static VastoLorde spawnAt(ServerLevel level, BlockPos spot, List<? extends Player> souls) {
        return mayCome(level) ? place(level, spot, souls) : null;
    }

    /**
     * Whether no soul whose eyes lie within {@link #SIGHT} of these feet could see one stand up on them: each has them
     * behind it, in the half of the world its head turns from, or has the body's head and chest both hidden from its
     * eyes, by a block or by a chunk not made now.
     */
    public static boolean unseen(ServerLevel level, Vec3 feet, List<? extends Player> souls) {
        Vec3 head = feet.add(0.0, 2.0, 0.0), chest = feet.add(0.0, 0.9, 0.0);
        return souls.stream().filter(soul -> soul.getEyePosition().distanceToSqr(feet) < SIGHT * SIGHT)
                .allMatch(soul -> behind(soul, feet)
                        || blocked(level, soul.getEyePosition(), head) && blocked(level, soul.getEyePosition(), chest));
    }

    @Nullable
    private static VastoLorde place(ServerLevel level, BlockPos spot, List<? extends Player> souls) {
        VastoLorde body = fits(level, spot, souls) ? ModEntities.VASTO_LORDE.get().create(level) : null;
        if (body == null) {
            return null;
        }
        body.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(body, level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        return level.addFreshEntity(body) ? body : null;
    }

    private static boolean fits(ServerLevel level, BlockPos spot, List<? extends Player> souls) {
        if (spot.getY() < HuecoMundoPlan.CAVERN_CEILING_Y || !farOut(spot.getX(), spot.getZ())
                || ticking(level, spot.getX(), spot.getZ()) == null) {
            return false;
        }
        Vec3 feet = Vec3.atBottomCenterOf(spot);
        // noCollision reads a block past the body's box all round, and would wait on a chunk there still being made.
        return HuecoMundoSpawns.onHollowGround(level, spot) && level.getFluidState(spot).isEmpty()
                && ChunkPos.rangeClosed(new ChunkPos(spot.offset(-1, 0, -1)), new ChunkPos(spot.offset(1, 0, 1)))
                        .allMatch(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null)
                && level.noCollision(ModEntities.VASTO_LORDE.get().getSpawnAABB(feet.x, feet.y, feet.z))
                && souls.stream().noneMatch(soul -> soul.getEyePosition().distanceToSqr(feet) < NEAREST * NEAREST)
                && unseen(level, feet, souls);
    }

    /** This column's chunk, if it is made now and its creatures tick; never loaded for the asking. */
    @Nullable
    private static LevelChunk ticking(ServerLevel level, int x, int z) {
        return level.isPositionEntityTicking(new BlockPos(x, 0, z)) ? level.getChunkSource().getChunkNow(x >> 4, z >> 4)
                : null;
    }

    /** Whether these feet lie behind the soul across, by the way its head is turned. */
    private static boolean behind(Player soul, Vec3 feet) {
        Vec3 look = Vec3.directionFromRotation(0.0F, soul.getYHeadRot()), to = feet.subtract(soul.position());
        return look.x * to.x + look.z * to.z < 0.0;
    }

    /**
     * Whether a block's visual shape, or a cell of a chunk not made now, lies on the sight line from one to the other.
     * A chunk held but still being made hides what lies past it, as reading it would wait until it was made.
     */
    private static boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level, (world, pos) -> {
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null || chunk.getBlockState(pos).getVisualShape(world, pos, CollisionContext.empty())
                    .clip(from, to, pos) != null ? Boolean.TRUE : null;
        }, world -> Boolean.FALSE);
    }
}
