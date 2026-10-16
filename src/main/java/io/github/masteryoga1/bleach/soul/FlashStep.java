package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Shunpo, the flash step: a burst of speed too fast to follow, a few blocks in an instant. On the
 * ground it runs along the ground the way the player is steering, climbing or dropping a block at a
 * time, and carries on level across a gap rather than following a cliff down. In the air or on
 * reishi footing it follows the player's gaze (at most 45° up or down) when they steer straight
 * ahead. It stops short of walls, lava, anything harmful and the world border. Distance, cost and
 * cooldown come from Hohō (see {@link ShinigamiLogic#flashStepDistance}).
 */
public final class FlashStep {
    /** How finely the path is checked. */
    private static final double STEP = 0.25;
    /** How high a grounded step may climb to get past something, tried in order. */
    private static final double[] LIFTS = {0.5, 1.0};
    /** How far down a grounded step looks for ground before treating the way ahead as a drop. */
    private static final double SETTLE = 1.05;
    private static final float MAX_AIR_PITCH = 45.0F;
    private static final double MIN_DISTANCE = 1.0;

    /**
     * Where a flash step ends.
     *
     * @param end       the feet at the end, centred
     * @param supported whether something solid is under the feet there
     */
    public record DashPath(Vec3 end, boolean supported) {
    }

    private FlashStep() {
    }

    /**
     * The player pressed the flash step key while steering this way (each from -1 to 1, as the
     * movement keys give them).
     */
    public static void perform(ServerPlayer player, float forward, float left) {
        if (!SoulLogic.hasSoul(player) || !player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.isSleeping() || player.isFallFlying()) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (now - data.lastFlashStepAt < ShinigamiLogic.flashStepCooldownTicks(data)) {
            return;
        }
        if (!ShinigamiLogic.canFlashStep(data, now)) {
            refuse(player, ShinigamiLogic.hasShinigamiPower(data) && ShinigamiLogic.isPowerLent(data, now)
                    ? "message.bleach.power_lent" : "message.bleach.flash_step.untrained");
            return;
        }
        float cost = ShinigamiLogic.flashStepCost(data);
        if (data.reiryoku() - cost < 0.5F) {
            refuse(player, "message.bleach.flash_step.too_weak");
            return;
        }
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        double distance = ShinigamiLogic.flashStepDistance(data, slowness != null ? slowness.getAmplifier() : -1);
        if (distance < MIN_DISTANCE) {
            refuse(player, "message.bleach.flash_step.weighed_down");
            return;
        }

        boolean onFooting = ReishiFooting.isActive(player);
        boolean grounded = player.onGround() && !onFooting;
        Vec3 start = player.position();
        Vec3 direction = direction(player.getYRot(), player.getXRot(), forward, left, !grounded);
        DashPath path;
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        try {
            // The dasher's own plates do not stand in the way, so they can dash down off them.
            footing.active = false;
            path = sweep(level, player, player.getBoundingBox(), direction, distance, grounded);
        } finally {
            footing.active = onFooting;
        }
        Vec3 end = path.end();
        if (!Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z)
                || end.distanceTo(start) < MIN_DISTANCE) {
            refuse(player, "message.bleach.flash_step.blocked");
            return;
        }

        SoulLogic.trySpend(player, data, cost);
        data.lastFlashStepAt = now;
        SoulLogic.addStatXp(player, data, Stat.HOHO, BleachConfig.get(BleachConfig.SHUNPO_HOHO_XP));
        Milestones.grant(player, Milestones.SHUNPO);
        effects(level, start);
        effects(level, end);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.5F, 1.6F);

        if (onFooting) {
            player.teleportTo(end.x, end.y, end.z);
            ReishiFooting.reanchorAfterDash(player);
        } else if (grounded && path.supported()) {
            // From the ground to the ground lands as if they had run there.
            player.teleportTo(end.x, end.y, end.z);
            player.resetFallDistance();
        } else if (!ReishiFooting.startAfterDash(player, data, end)) {
            // From the air, or into it with no footing: keep the way they were moving, and how far
            // they have fallen. The move itself is absolute, so the client's lag is not added to it.
            Vec3 motion = player.getKnownMovement();
            player.teleportTo(end.x, end.y, end.z);
            player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
        }
    }

    private static void refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }

    private static void effects(ServerLevel level, Vec3 feet) {
        level.sendParticles(ParticleTypes.WHITE_ASH, feet.x, feet.y + 0.9, feet.z, 6, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y + 0.2, feet.z, 2, 0.2, 0.1, 0.2, 0.02);
    }

    /**
     * The way a flash step goes. Steering is taken relative to where the player faces (yaw 0 faces
     * +Z, and steering left then goes +X). In the air, steering straight ahead (or not at all)
     * follows the gaze instead, tilted at most 45°.
     *
     * @return a unit vector
     */
    public static Vec3 direction(float yaw, float pitch, float forward, float left, boolean air) {
        if (air && left == 0.0F && forward >= 0.0F) {
            return Vec3.directionFromRotation(Mth.clamp(pitch, -MAX_AIR_PITCH, MAX_AIR_PITCH), yaw);
        }
        Vec3 input = new Vec3(left, 0.0, forward);
        input = input.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : input.normalize();
        double radians = yaw * Mth.DEG_TO_RAD;
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3(input.x * cos - input.z * sin, 0.0, input.z * cos + input.x * sin);
    }

    /**
     * Follows the path a flash step would take from {@code start}, in steps of {@link #STEP}, and
     * stops at the last place the body fits safely.
     *
     * @param grounded whether the step runs along the ground, climbing and dropping a block at a
     *                 time; otherwise it flies straight
     */
    public static DashPath sweep(Level level, Entity entity, AABB start, Vec3 direction, double max, boolean grounded) {
        Vec3 step = direction.scale(STEP);
        AABB box = start;
        boolean settling = grounded;
        int steps = (int) Math.floor(max / STEP + 1.0E-6);
        for (int i = 0; i < steps; i++) {
            AABB next = box.move(step);
            // A body already past the world border may move along it or back in, but no further out.
            double border = outsideBorder(level, box) + 1.0E-7;
            if (!isFree(level, entity, next, border)) {
                if (!grounded) {
                    break;
                }
                AABB lifted = null;
                for (double lift : LIFTS) {
                    AABB candidate = next.move(0.0, lift, 0.0);
                    if (isFree(level, entity, candidate, border)) {
                        lifted = candidate;
                        break;
                    }
                }
                if (lifted == null) {
                    break;
                }
                next = lifted;
            } else if (!grounded && SafeLanding.touchesLava(level, next.expandTowards(0.0, -1.0, 0.0))) {
                // Reishi cannot harden in lava, so a step through the air never ends right above it.
                break;
            }
            if (settling) {
                // Never run out over lava, even where the ground is only a step down.
                if (lavaUnderfoot(level, entity, next)) {
                    break;
                }
                Vec3 settle = Entity.collideBoundingBox(entity, new Vec3(0.0, -SETTLE, 0.0), next, level, List.of());
                if (settle.y <= -SETTLE + 1.0E-7) {
                    // Nothing within a block below: a drop, so carry on level from here.
                    settling = false;
                } else {
                    AABB settled = next.move(settle);
                    if (isFree(level, entity, settled, border)) {
                        next = settled;
                    }
                }
            }
            box = next;
        }
        Vec3 end = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        return new DashPath(end, !level.noCollision(entity, box.move(0.0, -0.01, 0.0)));
    }

    /** How far the box sticks out past the world border, or 0 if it is inside. */
    private static double outsideBorder(Level level, AABB box) {
        WorldBorder border = level.getWorldBorder();
        return Math.max(0.0, Math.max(Math.max(border.getMinX() - box.minX, box.maxX - border.getMaxX()),
                Math.max(border.getMinZ() - box.minZ, box.maxZ - border.getMaxZ())));
    }

    /**
     * Whether lava lies within a step down under any part of the body with nothing solid over it, so
     * a foot could go in. Lava under the ground itself, as under a bridge, does not count.
     */
    private static boolean lavaUnderfoot(Level level, Entity entity, AABB box) {
        CollisionContext context = CollisionContext.of(entity);
        int top = Mth.floor(box.minY - 1.0E-7);
        int bottom = Mth.floor(box.minY - SETTLE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX + 1.0E-7); x <= Mth.floor(box.maxX - 1.0E-7); x++) {
            for (int z = Mth.floor(box.minZ + 1.0E-7); z <= Mth.floor(box.maxZ - 1.0E-7); z++) {
                for (int y = top; y >= bottom; y--) {
                    pos.set(x, y, z);
                    if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                        return true;
                    }
                    if (!level.getBlockState(pos).getCollisionShape(level, pos, context).isEmpty()) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether the body fits here: loaded, no further past the world border than {@code border}
     * blocks, nothing in the way, nothing harmful and no lava.
     */
    private static boolean isFree(Level level, Entity entity, AABB box, double border) {
        return level.hasChunksAt(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                        Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))
                && outsideBorder(level, box) <= border
                && level.noCollision(entity, box) && !SafeLanding.isHarmful(level, entity, box)
                && !SafeLanding.touchesLava(level, box);
    }
}
