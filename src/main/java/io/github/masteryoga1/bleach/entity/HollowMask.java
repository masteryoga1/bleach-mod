package io.github.masteryoga1.bleach.entity;

import java.util.Optional;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The mask, a Hollow's weak point: a cube on the face, placed per kind ({@link HollowKind.MaskSpot})
 * and grown with the Hollow's size. A blow or a projectile strikes it when its path meets the mask
 * before the body, or not much after, so a mask at the front edge of the body still counts.
 *
 * <p>A Hollow player's mask is a weak point too: a half-block cube over the face (see
 * {@link #struckPlayer}), struck only from in front.
 */
public final class HollowMask {
    /** How much bigger the mask counts for a sword or fist, and for a projectile, in blocks. */
    public static final double MELEE_INFLATE = 0.1;
    public static final double PROJECTILE_INFLATE = 0.25;
    /** How far past the body, in blocks at size 1, the path may meet the mask and still strike it. */
    private static final double DEPTH_SLACK = 0.5;
    /** A Hollow player's mask, at size 1: how far forward of the eyes its middle sits, and how big it is. */
    private static final double PLAYER_MASK_FORWARD = 0.25;
    private static final double PLAYER_MASK_SIZE = 0.5;

    private HollowMask() {
    }

    /** The middle of the mask: forward along where the head faces, and up from the feet. */
    public static Vec3 maskCenter(Hollow hollow) {
        HollowKind.MaskSpot spot = hollow.kind().mask;
        double scale = hollow.getScale();
        return hollow.position()
                .add(Vec3.directionFromRotation(0.0F, hollow.getYHeadRot()).scale(spot.forward() * scale))
                .add(0.0, spot.up() * scale, 0.0);
    }

    /** The mask as a box, made bigger on every side by {@code inflate}. */
    public static AABB maskBox(Hollow hollow, double inflate) {
        double size = hollow.kind().mask.size() * hollow.getScale();
        return AABB.ofSize(maskCenter(hollow), size, size, size).inflate(inflate);
    }

    /**
     * Whether this damage struck the mask: a player's own blow, traced along where they look, or a
     * projectile, traced along its path this tick (an arrow hurts before it moves). Anything else never
     * does, and neither does anything while the mask is forming or the Hollow is dissolving.
     */
    public static boolean struck(Hollow hollow, DamageSource source) {
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof Player player
                && source.getEntity() == player) {
            Vec3 eye = player.getEyePosition();
            Vec3 reach = eye.add(player.getViewVector(1.0F).scale(player.entityInteractionRange() + 1.0));
            return struck(hollow, eye, reach, MELEE_INFLATE);
        }
        if (source.getDirectEntity() instanceof Projectile projectile) {
            Vec3 from = projectile.position();
            return struck(hollow, from, from.add(projectile.getDeltaMovement()), PROJECTILE_INFLATE);
        }
        return false;
    }

    /**
     * Whether the path from {@code from} to {@code to} strikes the mask: it starts inside the mask, or
     * meets it and either misses the body or meets the mask no more than half a block (at size 1) past
     * where it meets the body.
     */
    public static boolean struck(Hollow hollow, Vec3 from, Vec3 to, double inflate) {
        if (hollow.isMaskForming() || hollow.isPurifying()) {
            return false;
        }
        AABB mask = maskBox(hollow, inflate);
        if (mask.contains(from)) {
            return true;
        }
        Optional<Vec3> maskHit = mask.clip(from, to);
        if (maskHit.isEmpty()) {
            return false;
        }
        Optional<Vec3> bodyHit = hollow.getBoundingBox().inflate(hollow.getPickRadius()).clip(from, to);
        return bodyHit.isEmpty()
                || from.distanceTo(maskHit.get()) <= from.distanceTo(bodyHit.get()) + DEPTH_SLACK * hollow.getScale();
    }

    /**
     * A Hollow player's mask as a box: a half-block cube just in front of the eyes, where the head faces,
     * grown with the player's size.
     */
    public static AABB playerMaskBox(Player victim) {
        double scale = victim.getScale();
        double size = PLAYER_MASK_SIZE * scale;
        Vec3 centre = victim.getEyePosition()
                .add(Vec3.directionFromRotation(0.0F, victim.getYHeadRot()).scale(PLAYER_MASK_FORWARD * scale));
        return AABB.ofSize(centre, size, size, size);
    }

    /**
     * Whether this damage struck a Hollow player's mask: a player's own blow, traced along where they
     * look, or a projectile, traced along its path this tick, that meets the mask, coming from in front
     * of the victim. Anything else never does.
     */
    public static boolean struckPlayer(Player victim, DamageSource source) {
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof Player attacker
                && source.getEntity() == attacker) {
            Vec3 eye = attacker.getEyePosition();
            Vec3 reach = eye.add(attacker.getViewVector(1.0F).scale(attacker.entityInteractionRange() + 1.0));
            return inFront(victim, attacker.position()) && meets(playerMaskBox(victim).inflate(MELEE_INFLATE), eye, reach);
        }
        if (source.getDirectEntity() instanceof Projectile projectile) {
            Vec3 from = projectile.position();
            Vec3 to = from.add(projectile.getDeltaMovement());
            return inFront(victim, from) && meets(playerMaskBox(victim).inflate(PROJECTILE_INFLATE), from, to);
        }
        return false;
    }

    /** Whether this spot lies ahead of the player, where the head faces, rather than behind or beside them. */
    private static boolean inFront(Player victim, Vec3 at) {
        Vec3 facing = Vec3.directionFromRotation(0.0F, victim.getYHeadRot());
        return facing.x * (at.x - victim.getX()) + facing.z * (at.z - victim.getZ()) > 0.0;
    }

    /** Whether the path from {@code from} to {@code to} starts inside the box or passes through it. */
    private static boolean meets(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) || box.clip(from, to).isPresent();
    }
}
