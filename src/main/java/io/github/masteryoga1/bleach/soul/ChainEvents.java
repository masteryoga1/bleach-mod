package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Death and the Chain of Fate: an aware Human who dies lingers as a Plus where they fell, their
 * chain wears away (more slowly with a Shinigami nearby), Encroachment sets in near the end, and a
 * Plus whose chain runs out becomes a Hollow. Konsō, from a Shinigami, sends a Plus on before that
 * (see {@link ShinigamiEvents#konso}).
 */
public final class ChainEvents {
    public static final ResourceKey<DamageType> ENCROACHMENT =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("encroachment"));

    /** Ticks between Encroachment pangs. */
    private static final int PANG_INTERVAL_TICKS = 600;
    private static final double SHINIGAMI_RANGE = 16.0;
    private static final float LOW_HEALTH_FRACTION = 0.3F;

    private ChainEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ChainEvents::onPlayerTick);
        bus.addListener(ChainEvents::onRespawn);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (data.state() != SoulState.PLUS) {
            return;
        }
        SoulLogic.ensureChain(data);
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        if (player.tickCount % 20 == 0) {
            data.nearShinigami = shinigamiNearby(player);
        }
        double speed = 1.0;
        if (player.getHealth() < player.getMaxHealth() * LOW_HEALTH_FRACTION) {
            speed *= BleachConfig.get(BleachConfig.LOW_HEALTH_EROSION_MULTIPLIER);
        }
        if (data.nearShinigami) {
            speed *= BleachConfig.get(BleachConfig.NEAR_SHINIGAMI_EROSION_MULTIPLIER);
        }
        if (SoulLogic.erodeChain(data, speed)) {
            hollowfy(player, data);
            return;
        }

        boolean encroaching = SoulLogic.isEncroaching(data);
        if (encroaching && !data.wasEncroaching) {
            player.sendSystemMessage(Component.translatable("message.bleach.encroachment_begins")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        data.wasEncroaching = encroaching;
        if (encroaching) {
            if (++data.encroachmentTicks >= PANG_INTERVAL_TICKS) {
                data.encroachmentTicks = 0;
                pang(player);
            }
        } else {
            data.encroachmentTicks = 0;
        }
    }

    /** Whether a Shinigami, a Substitute or a Shinigami NPC is close enough to slow the chain. */
    private static boolean shinigamiNearby(ServerPlayer player) {
        double rangeSqr = SHINIGAMI_RANGE * SHINIGAMI_RANGE;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.distanceToSqr(player) <= rangeSqr && ShinigamiLogic.slowsErosion(other)) {
                return true;
            }
        }
        return !player.serverLevel().getEntitiesOfClass(ShinigamiNpc.class, player.getBoundingBox().inflate(SHINIGAMI_RANGE),
                npc -> npc.distanceToSqr(player) <= rangeSqr && ShinigamiLogic.slowsErosion(npc)).isEmpty();
    }

    /** The links eat each other: a moment of blindness and a little pain that never kills. */
    private static void pang(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
        if (player.getHealth() > 2.0F) {
            player.hurt(encroachment(player.serverLevel()), 1.0F);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.bleach.encroachment_pang")
                .withStyle(ChatFormatting.DARK_RED), true);
    }

    public static DamageSource encroachment(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ENCROACHMENT));
    }

    private static void hollowfy(ServerPlayer player, SoulData data) {
        SoulLogic.hollowfy(player, data);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.sendSystemMessage(Component.translatable("message.bleach.hollowfied")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    /** A Human who just became a Plus comes back where they fell, if that spot is safe to stand in. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (!data.becamePlus) {
            return;
        }
        data.becamePlus = false;
        Vec3 diedAt = data.diedAt;
        data.diedAt = null;
        ServerLevel respawnLevel = player.serverLevel();
        boolean usedAnchor = respawnedAtAnchor(player);
        boolean returned = player.getLastDeathLocation()
                .map(location -> returnToDeathPoint(player, location, diedAt))
                .orElse(false);
        if (returned && usedAnchor) {
            refundAnchorCharge(respawnLevel, player.getRespawnPosition());
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable(returned
                        ? "message.bleach.became_plus" : "message.bleach.became_plus.elsewhere")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /**
     * Moves the player to where they died, settled onto the ground below so a death in mid-air does
     * not end in a long fall.
     *
     * @return false, leaving them at their respawn point, if that spot is in a wall, a liquid or
     *         something harmful, outside the world border, or over the void
     */
    private static boolean returnToDeathPoint(ServerPlayer player, GlobalPos location, @Nullable Vec3 exact) {
        ServerLevel level = player.server.getLevel(location.dimension());
        if (level == null) {
            return false;
        }
        Vec3 target = exact != null ? exact : Vec3.atBottomCenterOf(location.pos());
        if (target.y < level.getMinBuildHeight() || target.y >= level.getMaxBuildHeight()) {
            return false;
        }
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(target);
        // The spot may have unloaded while the player sat on the death screen, and collision checks
        // treat unloaded chunks as empty, so load it first.
        for (int chunkX = Mth.floor(box.minX) >> 4; chunkX <= Mth.floor(box.maxX) >> 4; chunkX++) {
            for (int chunkZ = Mth.floor(box.minZ) >> 4; chunkZ <= Mth.floor(box.maxZ) >> 4; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        if (!level.noCollision(player, box)) {
            return false;
        }
        while (level.noCollision(player, box.move(0.0, -1.0, 0.0))) {
            box = box.move(0.0, -1.0, 0.0);
            if (box.minY < level.getMinBuildHeight()) {
                return false;
            }
        }
        // The drop above moves in whole blocks; settle the rest of the way onto what the feet will rest on.
        box = box.move(Entity.collideBoundingBox(player, new Vec3(0.0, -1.0, 0.0), box, level, List.of()));
        if (!level.getWorldBorder().isWithinBounds(box) || level.containsAnyLiquid(box) || isHarmful(level, player, box)) {
            return false;
        }
        player.teleportTo(level, target.x, box.minY, target.z, player.getYRot(), player.getXRot());
        return true;
    }

    /**
     * Whether the spot would hurt or trap whoever stands in it: fire, powder snow, wither roses and the
     * like where the body would be, or magma underfoot.
     */
    private static boolean isHarmful(ServerLevel level, ServerPlayer player, AABB box) {
        // Shrunk a hair, as vanilla does, so blocks the box only touches from outside do not count.
        if (BlockPos.betweenClosedStream(box.deflate(1.0E-7)).anyMatch(pos -> isHarmful(level.getBlockState(pos)))) {
            return true;
        }
        // Underfoot only magma hurts, and only through the block vanilla would treat as the support:
        // the colliding block whose centre is nearest the feet (Entity.checkSupportingBlock, measured
        // from the spot instead of where the player is now), or what lies right under it, so magma
        // under a carpet counts too.
        Vec3 feet = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        AABB sole = new AABB(box.minX, box.minY - 1.0E-6, box.minZ, box.maxX, box.minY, box.maxZ);
        BlockCollisions<BlockPos> under = new BlockCollisions<>(level, player, sole, false, (pos, shape) -> pos.immutable());
        BlockPos support = null;
        double nearest = Double.MAX_VALUE;
        while (under.hasNext()) {
            BlockPos pos = under.next();
            double distance = pos.distToCenterSqr(feet);
            if (distance < nearest || distance == nearest && (support == null || support.compareTo(pos) < 0)) {
                support = pos;
                nearest = distance;
            }
        }
        return support != null
                && level.getBlockState(support.atY(Mth.floor(box.minY - 0.2))).is(Blocks.MAGMA_BLOCK);
    }

    private static boolean isHarmful(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS);
    }

    /** Whether vanilla respawned the player at a respawn anchor, which spends one of its charges. */
    private static boolean respawnedAtAnchor(ServerPlayer player) {
        BlockPos anchor = player.getRespawnPosition();
        return anchor != null && !player.isRespawnForced()
                && player.level().dimension() == player.getRespawnDimension()
                && player.level().getBlockState(anchor).getBlock() instanceof RespawnAnchorBlock
                && player.position().closerThan(Vec3.atCenterOf(anchor), 3.0);
    }

    /** The Plus never used the anchor it would have respawned at, so it gets back the charge. */
    private static void refundAnchorCharge(ServerLevel level, @Nullable BlockPos anchor) {
        if (anchor == null) {
            return;
        }
        BlockState state = level.getBlockState(anchor);
        if (state.getBlock() instanceof RespawnAnchorBlock
                && state.getValue(RespawnAnchorBlock.CHARGE) < RespawnAnchorBlock.MAX_CHARGES) {
            level.setBlock(anchor, state.setValue(RespawnAnchorBlock.CHARGE, state.getValue(RespawnAnchorBlock.CHARGE) + 1), 3);
        }
    }
}
