package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * What a soul just below the awareness threshold makes out of the spirits it cannot see: now and
 * then a pale mote of light where one stands. Only the local player's own synced soul is read.
 */
public final class SpiritShimmer {
    /** Game ticks between looks around. */
    private static final int INTERVAL = 4;
    /** Each hidden spirit glints on one look in this many, in a fixed turn so a glint is never long in coming. */
    private static final int CADENCE = 3;
    private static final double RANGE = 24.0;
    private static final double DRIFT = 0.01;
    private static final DustParticleOptions MOTE = new DustParticleOptions(new Vector3f(0.85F, 0.92F, 1.0F), 0.6F);

    /** The game time of the last look, so a paused game or a repeated tick does not look twice. */
    private static long lastLook = Long.MIN_VALUE;
    /** Motes shown since the game started, for the smoke test. */
    private static int emitted;

    private SpiritShimmer() {
    }

    /** Called every client tick while a level is loaded. */
    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        long now = level.getGameTime();
        if (Math.floorMod(now, INTERVAL) != 0 || now == lastLook) {
            return;
        }
        lastLook = now;
        if (!SoulLogic.inShimmerBand(player.getData(SoulAttachments.SOUL))) {
            return;
        }
        long turn = now / INTERVAL;
        for (Entity entity : level.entitiesForRendering()) {
            if (Math.floorMod(entity.getId() + turn, CADENCE) != 0 || entity.distanceToSqr(player) > RANGE * RANGE
                    || !ClientSouls.isHiddenFromViewer(entity) || entity instanceof CajaNegacion
                    || CajaNegacion.hidden(entity)) {
                continue;
            }
            AABB box = entity.getBoundingBox();
            level.addParticle(MOTE,
                    box.minX + level.random.nextDouble() * box.getXsize(),
                    box.minY + level.random.nextDouble() * box.getYsize(),
                    box.minZ + level.random.nextDouble() * box.getZsize(),
                    0.0, DRIFT, 0.0);
            emitted++;
        }
    }

    /** How many motes have been shown, for the smoke test. */
    public static int emitted() {
        return emitted;
    }
}
