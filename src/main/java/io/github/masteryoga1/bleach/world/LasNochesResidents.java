package io.github.masteryoga1.bleach.world;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarNpcEvents;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Números who live in Las Noches' halls: while any soul is in Hueco Mundo, one comes each
 * {@code [hueco_mundo] resident_seconds}, up to {@code residents} alive there, onto the halls' open floor where they
 * stand, with no soul near and none that could see it come. None is ever written: one goes with its chunk, and the halls
 * fill again in time.
 */
public final class LasNochesResidents {
    /** A resident's bound, about the floor under the dome's centre: short of the doors, reaching the throne room. */
    public static final BlockPos BOUND_CENTRE = new BlockPos(0, HuecoMundoPlan.LEVEL_Y, 0);
    public static final int BOUND_RADIUS = 56;
    /** The court, where a resident with nothing to fight walks back to from out of its bound. */
    public static final Vec3 COURT = new Vec3(12.5, HuecoMundoPlan.LEVEL_Y, 12.5);
    /** The centre column's floor, about which the spots are drawn. */
    public static final Vec3 FLOOR_CENTRE = new Vec3(0.5, HuecoMundoPlan.LEVEL_Y, 0.5);
    /** How far from the centre column one may come, leaving it room to walk inside its bound. */
    public static final int SPOT_RADIUS = 48;
    /** How near one may come to a soul: as near as vanilla lets a monster spawn by a player. */
    static final double NEAREST = 24.0;
    /** How far a soul is asked whether it would see one come: as far as an Arrancar is ever sent to a client. */
    static final double SIGHT = 160.0;
    /** The spots drawn in a look, as many as an arrival's. */
    static final int TRIES = 12;

    private LasNochesResidents() {
    }

    /** Takes a look each {@code resident_seconds} while any soul is in Hueco Mundo, at the floor about the centre. */
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        int every = BleachConfig.get(BleachConfig.HUECO_MUNDO_RESIDENT_SECONDS) * 20;
        if (level != null && event.getServer().getTickCount() % every == 0 && !level.players().isEmpty()) {
            look(level, level.getRandom(), FLOOR_CENTRE, SPOT_RADIUS,
                    level.players().stream().map(Entity::getEyePosition).toList());
        }
    }

    /**
     * One look: if residents may come, up to {@link #TRIES} floor spots are drawn within {@code reach} of {@code about},
     * and the first that fits ({@link #spawnAt}) gets a Número. Returns it, or null.
     */
    @Nullable
    public static ArrancarNpc look(ServerLevel level, RandomSource random, Vec3 about, double reach, List<Vec3> eyes) {
        if (!mayCome(level)) {
            return null;
        }
        for (int i = 0; i < TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0, out = Math.sqrt(random.nextDouble()) * reach;
            ArrancarNpc made = place(level, BlockPos.containing(about.x + Math.cos(angle) * out, HuecoMundoPlan.LEVEL_Y,
                    about.z + Math.sin(angle) * out), eyes);
            if (made != null) {
                return made;
            }
        }
        return null;
    }

    /** Whether one may come now: some are allowed, not in Peaceful, with mob spawning on, and fewer than that live here. */
    public static boolean mayCome(ServerLevel level) {
        int allowed = BleachConfig.get(BleachConfig.HUECO_MUNDO_RESIDENTS);
        return allowed > 0 && level.getDifficulty() != Difficulty.PEACEFUL
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && count(level) < allowed;
    }

    /** The residents alive in this world, in its loaded chunks. */
    public static int count(ServerLevel level) {
        return level.getEntities(ModEntities.ARRANCAR.get(), npc -> npc.isAlive() && npc.isResident()).size();
    }

    /**
     * A Número set down standing on this floor cell, if one may come ({@link #mayCome}) and may come here, else null.
     * Here is within {@link #SPOT_RADIUS} of the centre column, under the dome, in a chunk loaded now whose halls stand
     * and whose creatures tick, on a sturdy floor with room to stand and no fluid, with no eye within {@link #NEAREST}
     * and none within {@link #SIGHT} that could see it come.
     */
    @Nullable
    public static ArrancarNpc spawnAt(ServerLevel level, BlockPos spot, List<Vec3> eyes) {
        return mayCome(level) ? place(level, spot, eyes) : null;
    }

    @Nullable
    private static ArrancarNpc place(ServerLevel level, BlockPos spot, List<Vec3> eyes) {
        if (!fits(level, spot, eyes)) {
            return null;
        }
        List<ArrancarNpc> made = ArrancarNpcEvents.makeGroup(level, false, 0, ArrancarNpc.NEVER);
        if (made.isEmpty()) {
            return null;
        }
        ArrancarNpc npc = made.get(0);
        npc.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        npc.makeResident();
        level.addFreshEntity(npc);
        return npc;
    }

    private static boolean fits(ServerLevel level, BlockPos spot, List<Vec3> eyes) {
        if (spot.getY() != HuecoMundoPlan.LEVEL_Y || HuecoMundoPlan.radius(spot.getX(), spot.getZ()) >= SPOT_RADIUS
                || !HuecoMundoPlan.enclosed(spot.getX(), spot.getY(), spot.getZ())) {
            return false;
        }
        ChunkAccess chunk = level.getChunkSource().getChunkNow(spot.getX() >> 4, spot.getZ() >> 4);
        if (chunk == null || !LasNoches.hallsStand(level, chunk) || !level.isPositionEntityTicking(spot)) {
            return false;
        }
        Vec3 feet = Vec3.atBottomCenterOf(spot);
        BlockPos floor = spot.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && level.getFluidState(spot).isEmpty()
                && level.noCollision(ModEntities.ARRANCAR.get().getSpawnAABB(feet.x, feet.y, feet.z))
                && eyes.stream().noneMatch(eye -> eye.distanceToSqr(feet) < NEAREST * NEAREST)
                && hiddenFrom(level, feet, eyes);
    }

    /**
     * Whether no eye within {@link #SIGHT} could see a body stand up here: its head and its middle hidden from each by a
     * block or by a chunk not loaded now. It never loads a chunk.
     */
    public static boolean hiddenFrom(ServerLevel level, Vec3 feet, List<Vec3> eyes) {
        Vec3 head = feet.add(0.0, 1.62, 0.0), middle = feet.add(0.0, 0.9, 0.0);
        return eyes.stream().filter(eye -> eye.distanceToSqr(feet) < SIGHT * SIGHT)
                .allMatch(eye -> blocked(level, eye, head) && blocked(level, eye, middle));
    }

    /** Whether a block's visual shape or a cell of a chunk not loaded now lies on the sight line from one to the other. */
    private static boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level, (world, pos) -> !world.isLoaded(pos)
                || world.getBlockState(pos).getVisualShape(world, pos, CollisionContext.empty()).clip(from, to, pos) != null
                ? Boolean.TRUE : null, world -> Boolean.FALSE);
    }

    /** Whether a resident may end a step here: anywhere out of Hueco Mundo, and in it only under the dome, in its bound. */
    public static boolean keeps(ServerLevel level, Vec3 at) {
        BlockPos cell = BlockPos.containing(at);
        return level.dimension() != BleachWorlds.HUECO_MUNDO || HuecoMundoPlan.enclosed(cell.getX(), cell.getY(), cell.getZ())
                && cell.distSqr(BOUND_CENTRE) < (double) BOUND_RADIUS * BOUND_RADIUS;
    }
}
