package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Reishi footing: a trained soul hardens the spirit particles under their feet and stands on the
 * air. The server lays invisible plates ({@link io.github.masteryoga1.bleach.block.ReishiFootingBlock})
 * at the anchor height under and just ahead of the player while it lasts, and drains reiryoku every
 * tick (a Quincy's footing drains reishi instead, see {@link QuincyLogic#footingOnReishi}). Sneaking
 * lets the player sink through; letting go holds them again where they are. Pressing the key (or
 * jump) again while falling raises the anchor to the new height, so a soul climbs one block per
 * jump. Footing ends on solid ground or a ladder, when what pays for it runs out, or when the soul
 * may no longer use it.
 */
public final class ReishiFooting {
    /** Footing ends if the player gets this far above it, launched or otherwise. */
    private static final double MAX_RISE = 7.0;
    /** Below this height over the anchor the player has sunk past the plates, and the anchor follows them down. */
    public static final double SUNK = 0.9;
    /** How far ahead plates are laid, in steps of one tick's movement. */
    private static final int LOOK_AHEAD_STEPS = 6;
    /** Plates are laid under the player's box widened by this much on each side. */
    private static final double FOOTPRINT_MARGIN = 0.3;
    private static final int PARTICLE_INTERVAL_TICKS = 10;
    /** The patch under the middle of the feet checked for real ground. */
    private static final double SOLE_WIDTH = 0.02;
    private static final double SOLE_DEPTH = 1.0E-3;
    /** How long after logging in footing waits for the ground around the player to load before giving up. */
    private static final int RESUME_WAIT_TICKS = 200;

    private ReishiFooting() {
    }

    /** Whether this player is standing on reishi footing (or would be, were they lower). */
    public static boolean isActive(Player player) {
        return player.hasData(SoulAttachments.FOOTING) && player.getData(SoulAttachments.FOOTING).active;
    }

    /**
     * Everything footing needs apart from being in the air: alive, not a spectator, not in a liquid,
     * riding, gliding or flying, and trained enough to use it.
     */
    private static boolean mayStand(Player player, SoulData data, long now) {
        return player.isAlive() && !player.isSpectator() && !player.isInLiquid() && !player.isPassenger()
                && !player.isFallFlying() && !player.getAbilities().flying && SoulLogic.hasSoul(player)
                && ShinigamiLogic.canUseReishiFooting(data, now);
    }

    /**
     * The player asked for footing, with the key or by pressing jump in the air. Starting costs one
     * second's drain up front; asking again while it holds raises or lowers the anchor to where the
     * player is now. The player is always told the result, so their prediction is corrected.
     */
    public static void activate(ServerPlayer player, boolean viaJump) {
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        SoulData data = SoulLogic.get(player);
        ServerLevel level = player.serverLevel();
        int anchor = ShinigamiLogic.footingAnchor(player.getY());
        boolean allowed = mayStand(player, data, level.getGameTime()) && !player.onGround()
                && !level.isOutsideBuildHeight(anchor)
                && (!viaJump || noGroundBelow(level, player));
        if (allowed && !footing.active && !payUpfront(player, data, level.getGameTime())) {
            player.displayClientMessage(Component.translatable("message.bleach.reishi_footing.too_weak")
                    .withStyle(ChatFormatting.GRAY), true);
            allowed = false;
        }
        if (allowed) {
            start(player, footing, anchor);
        }
        sendState(player, footing);
    }

    /**
     * Whether nothing solid lies within two blocks under the player, not counting reishi footing,
     * so pressing jump just before landing does not start it.
     */
    public static boolean noGroundBelow(Level level, Player player) {
        return level.noCollision((Entity) null, player.getBoundingBox().move(0.0, -2.0, 0.0));
    }

    private static void start(ServerPlayer player, FootingState footing, int anchor) {
        boolean starting = !footing.active;
        footing.active = true;
        footing.anchorY = anchor;
        footing.hasLast = false;
        player.resetFallDistance();
        place(player.serverLevel(), player, footing);
        if (starting) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 1.6F);
            Milestones.grant(player, Milestones.REISHI_FOOTING);
        }
    }

    /**
     * Footing straight after a flash step that ends in the air: the soul is moved to where the step
     * ends and lands on reishi there. Pays the upfront cost like {@link #activate}.
     *
     * @return false, leaving the soul where it is and charging nothing, if it may not stand there, the
     *         ground is within a block below anyway, or it cannot pay
     */
    public static boolean startAfterDash(ServerPlayer player, SoulData data, Vec3 end) {
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        ServerLevel level = player.serverLevel();
        int anchor = ShinigamiLogic.footingAnchor(end.y);
        AABB endBox = player.getDimensions(player.getPose()).makeBoundingBox(end);
        if (!mayStand(player, data, level.getGameTime()) || level.isOutsideBuildHeight(anchor)
                || !level.noCollision((Entity) null, endBox.expandTowards(0.0, -1.0, 0.0))
                || !footing.active && !payUpfront(player, data, level.getGameTime())) {
            return false;
        }
        player.teleportTo(end.x, end.y, end.z);
        start(player, footing, anchor);
        sendState(player, footing);
        return true;
    }

    /**
     * A soul who logged out standing on reishi in mid-air stands on it again once the ground around
     * them has loaded, rather than falling. Footing itself is never saved, so this goes by where they
     * are: any soul who may use footing and logs in with no ground within two blocks below stands on
     * reishi, without paying to start it.
     */
    public static void resumeAfterLogin(ServerPlayer player) {
        player.getData(SoulAttachments.FOOTING).resumePending = true;
    }

    /**
     * Runs at the start of every player tick, before the server moves the player: resumes footing
     * after login. Before the move, the player is still where they logged out, and the new plates
     * hold them through it.
     */
    public static void tickBeforeMove(ServerPlayer player, SoulData data) {
        if (player.hasData(SoulAttachments.FOOTING)) {
            FootingState footing = player.getData(SoulAttachments.FOOTING);
            if (footing.resumePending) {
                tryResume(player, data, footing);
            }
        }
    }

    /** The check {@link #resumeAfterLogin} waits for: once the chunks under the player are loaded, starts footing for free. */
    private static void tryResume(ServerPlayer player, SoulData data, FootingState footing) {
        ServerLevel level = player.serverLevel();
        AABB below = player.getBoundingBox().expandTowards(0.0, -2.0, 0.0);
        if (!level.hasChunksAt(Mth.floor(below.minX), Mth.floor(below.minY), Mth.floor(below.minZ),
                Mth.floor(below.maxX), Mth.floor(below.maxY), Mth.floor(below.maxZ))) {
            footing.resumePending = player.tickCount < RESUME_WAIT_TICKS;
            return;
        }
        footing.resumePending = false;
        int anchor = ShinigamiLogic.footingAnchor(player.getY());
        if (footing.active || !mayStand(player, data, level.getGameTime()) || player.getAbilities().mayfly
                || level.isOutsideBuildHeight(anchor) || !noGroundBelow(level, player)) {
            return;
        }
        // The saved OnGround was the plates, which are gone, and the saved fall starts again from rest.
        player.setOnGround(false);
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        player.resetFallDistance();
        footing.active = true;
        footing.anchorY = anchor;
        footing.hasLast = false;
        place(level, player, footing);
        sendState(player, footing);
    }

    /** After a flash step while standing on footing, the footing moves to the new height. */
    public static void reanchorAfterDash(ServerPlayer player) {
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        if (footing.active) {
            footing.anchorY = ShinigamiLogic.footingAnchor(player.getY());
            footing.hasLast = false;
            player.resetFallDistance();
            place(player.serverLevel(), player, footing);
            sendState(player, footing);
        }
    }

    /** Ends footing and tells the player. */
    public static void end(ServerPlayer player) {
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        if (footing.active) {
            footing.active = false;
            sendState(player, footing);
        }
    }

    /** Runs every tick for every player: keeps the plates under them, drains what pays for it, or ends footing. */
    public static void tick(ServerPlayer player, SoulData data) {
        if (!isActive(player)) {
            return;
        }
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        ServerLevel level = player.serverLevel();
        if (!mayStand(player, data, level.getGameTime()) || player.onClimbable() || onRealGround(level, player)
                || player.getY() > footing.anchorY + MAX_RISE || level.isOutsideBuildHeight(footing.anchorY)) {
            end(player);
            return;
        }
        if (!payTick(player, data, level.getGameTime())) {
            end(player);
            player.displayClientMessage(Component.translatable("message.bleach.reishi_footing.exhausted")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!player.isShiftKeyDown()) {
            if (player.getY() < footing.anchorY + SUNK) {
                footing.anchorY = ShinigamiLogic.footingAnchor(player.getY());
                footing.hasLast = false;
                player.resetFallDistance();
                sendState(player, footing);
            }
            place(level, player, footing);
        }
        if (player.tickCount % PARTICLE_INTERVAL_TICKS == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), footing.anchorY + 1.0, player.getZ(),
                    1, 0.2, 0.0, 0.2, 0.0);
        }
        footing.lastX = player.getX();
        footing.lastZ = player.getZ();
        footing.hasLast = true;
    }

    /**
     * Whether the player stands on real ground rather than reishi. The server also looks under the
     * middle of their feet itself, so a client that never reports landing does not keep footing, and
     * its reiryoku draining, on the ground.
     */
    private static boolean onRealGround(Level level, Player player) {
        if (player.onGround() && !player.getBlockStateOn().is(ModBlocks.REISHI_FOOTING.get())) {
            return true;
        }
        AABB sole = AABB.ofSize(new Vec3(player.getX(), player.getY() - SOLE_DEPTH / 2.0, player.getZ()),
                SOLE_WIDTH, SOLE_DEPTH, SOLE_WIDTH);
        // With no entity, the plates are not solid.
        return !level.noCollision((Entity) null, sole);
    }

    /** Lays plates at the anchor under the player, and a few ahead of where they are heading. */
    private static void place(ServerLevel level, Player player, FootingState footing) {
        AABB box = player.getBoundingBox();
        int y = footing.anchorY;
        for (int x = Mth.floor(box.minX - FOOTPRINT_MARGIN); x <= Mth.floor(box.maxX + FOOTPRINT_MARGIN); x++) {
            for (int z = Mth.floor(box.minZ - FOOTPRINT_MARGIN); z <= Mth.floor(box.maxZ + FOOTPRINT_MARGIN); z++) {
                placeAt(level, new BlockPos(x, y, z));
            }
        }
        if (!footing.hasLast) {
            return;
        }
        double dx = player.getX() - footing.lastX;
        double dz = player.getZ() - footing.lastZ;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed < 1.0E-3) {
            return;
        }
        if (speed > 1.0) {
            dx /= speed;
            dz /= speed;
        }
        for (int step = 1; step <= LOOK_AHEAD_STEPS; step++) {
            placeAt(level, BlockPos.containing(player.getX() + dx * step, y, player.getZ() + dz * step));
        }
    }

    private static void placeAt(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, ModBlocks.REISHI_FOOTING.get().defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Whether someone is still standing on, or just above, the plate here, so it should not fade yet. */
    public static boolean inUse(Level level, BlockPos pos) {
        AABB around = new AABB(pos).inflate(1.5, 0.0, 1.5).expandTowards(0.0, 3.0, 0.0);
        for (Player player : level.players()) {
            if (isActive(player) && player.getData(SoulAttachments.FOOTING).anchorY == pos.getY()
                    && player.getBoundingBox().intersects(around)) {
                return true;
            }
        }
        return false;
    }

    /** Pays the second's drain that starting footing costs, in reishi for a Quincy's footing and reiryoku for the rest. */
    private static boolean payUpfront(ServerPlayer player, SoulData data, long now) {
        if (QuincyLogic.footingOnReishi(data, now)) {
            return QuincyLogic.trySpendReishi(data, (float) BleachConfig.get(BleachConfig.QUINCY_FOOTING_REISHI_PER_SECOND));
        }
        return SoulLogic.trySpend(player, data, ShinigamiLogic.footingDrainPerTick(data) * 20.0F);
    }

    /**
     * Pays one tick of footing, the same way as {@link #payUpfront}.
     *
     * @return false when it ran dry
     */
    private static boolean payTick(ServerPlayer player, SoulData data, long now) {
        if (QuincyLogic.footingOnReishi(data, now)) {
            return QuincyLogic.drainReishi(data,
                    (float) (BleachConfig.get(BleachConfig.QUINCY_FOOTING_REISHI_PER_SECOND) / 20.0));
        }
        return SoulLogic.drain(player, data, ShinigamiLogic.footingDrainPerTick(data));
    }

    private static void sendState(ServerPlayer player, FootingState footing) {
        BleachNetwork.sendFooting(player, footing.active, footing.anchorY);
    }
}
