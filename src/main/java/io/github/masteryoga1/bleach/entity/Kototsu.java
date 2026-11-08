package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.DangaiLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.world.DangaiPlan;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Kōtotsu, which sweeps a Dangai lane behind those no Jigokuchō guides. Its position is its front face's bottom
 * middle and its box runs 8 blocks back, filling the lane; it is drawn 24 long with a gold eye. It only moves: the
 * souls it catches are found from their own crossings (DangaiEvents). Never saved or summoned, and hidden from the
 * unaware as a spirit ({@code #bleach:spirits}).
 */
public final class Kototsu extends Entity {
    /** Its box: 8 across, 8 tall, 8 back from its front; and how far back it is drawn. */
    public static final double HALF_WIDTH = 4.0, HEIGHT = 8.0, LENGTH = 8.0, DRAWN_LENGTH = 24.0;
    /** Its roar as it sets out, carrying 64 blocks. */
    private static final float ROAR_VOLUME = 4.0F;
    private static final float ROAR_PITCH = 0.5F;

    // Server side only, never synced or saved: its lane, the game time it sets out, and whether it has looked for a twin.
    private int lane;
    private long setsOutAt = Long.MAX_VALUE;
    private boolean settled;
    // Client side: where the last move packet put it and the ticks left to get there, so it glides as a mob does.
    private double lerpX, lerpY, lerpZ;
    private int lerpSteps;

    public Kototsu(EntityType<? extends Kototsu> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Every Kōtotsu in this lane: waiting at its start, its body in the rock behind, or on its way down. */
    public static List<Kototsu> inLane(ServerLevel level, int lane) {
        // Reaching back past z 0, since boxes that only touch do not meet.
        return level.getEntitiesOfClass(Kototsu.class, DangaiPlan.laneBox(lane).expandTowards(0.0, 0.0, -LENGTH),
                kototsu -> kototsu.lane == lane);
    }

    /** The game time the Kōtotsu a soul entering this lane now meets sets out: one still waiting there, or a new one. */
    public static long meet(ServerLevel level, int lane, long now) {
        for (Kototsu waiting : inLane(level, lane)) {
            if (waiting.setsOutAt > now) {
                return waiting.setsOutAt;
            }
        }
        return send(level, lane, now);
    }

    /** Sends a new Kōtotsu to wait at this lane's start, setting out {@code kototsu_delay_seconds} from now; when it does. */
    public static long send(ServerLevel level, int lane, long now) {
        Kototsu kototsu = new Kototsu(ModEntities.KOTOTSU.get(), level);
        kototsu.lane = lane;
        kototsu.setsOutAt = DangaiLogic.kototsuAt(now);
        kototsu.moveTo(DangaiPlan.laneX(lane), DangaiPlan.FLOOR_Y, 0.0, 0.0F, 0.0F);
        // A soul is caught by the clock, not by this, so a Kōtotsu the level would not take changes nothing.
        level.addFreshEntity(kototsu);
        return kototsu.setsOutAt;
    }

    /** The game time it sets out. Server side only. */
    public long setsOutAt() {
        return setsOutAt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** From its front face back {@link #LENGTH}, {@link #HALF_WIDTH} either side, {@link #HEIGHT} tall. */
    @Override
    protected AABB makeBoundingBox() {
        return new AABB(getX() - HALF_WIDTH, getY(), getZ() - LENGTH, getX() + HALF_WIDTH, getY() + HEIGHT, getZ());
    }

    /** Drawn all its length, so it is not culled while its tail is in view. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().expandTowards(0.0, 0.0, LENGTH - DRAWN_LENGTH);
    }

    /**
     * Server side: it waits with its front at the lane's start until it sets out, roaring as it does, then stands where
     * its speed has brought it since, and goes at the lane's cap. Nothing else: no fire, water or portal touches it. On
     * the client it only glides to where the server last put it.
     */
    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, getYRot(), getXRot());
                lerpSteps--;
            }
            return;
        }
        if (!settled) {
            settled = true;
            // Souls entering a fresh lane in one tick can each send one before either is listed: the later one goes.
            for (Kototsu other : inLane(level, lane)) {
                if (other != this && other.setsOutAt == setsOutAt && other.getId() < getId()) {
                    discard();
                    return;
                }
            }
        }
        long now = level.getGameTime();
        if (now == setsOutAt) {
            SpiritSenses.play(level, getX(), getY() + HEIGHT / 2.0, getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE,
                    ROAR_VOLUME, ROAR_PITCH, false);
        }
        double front = DangaiLogic.front(setsOutAt, now);
        if (front >= DangaiPlan.END_Z) {
            discard();
            return;
        }
        // Its lane's middle every tick, so nothing that nudges it (a blast, a piston) moves it for long.
        setPos(DangaiPlan.laneX(lane), DangaiPlan.FLOOR_Y, front);
    }

    /** Client side: blended to where the server says it is over the packet's steps, rather than jumping there. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (steps <= 0) {
            setPos(x, y, z);
            lerpSteps = 0;
            return;
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = steps;
    }

    /** Never saved (see the type's noSave). */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
