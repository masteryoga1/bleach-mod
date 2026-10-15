package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Death and the Chain of Fate: an aware Human who dies lingers as a Plus where they fell, their
 * chain wears away, Encroachment sets in near the end, and a Plus whose chain runs out becomes a
 * Hollow. Konsō, which sends a Plus on before that, arrives with the Shinigami path.
 */
public final class ChainEvents {
    public static final ResourceKey<DamageType> ENCROACHMENT =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("encroachment"));

    /** Ticks between Encroachment pangs. */
    private static final int PANG_INTERVAL_TICKS = 600;
    private static final double SHINIGAMI_RANGE = 16.0;
    private static final float LOW_HEALTH_FRACTION = 0.3F;
    /** How far below a mid-air death the return looks for ground. */
    private static final int MAX_DROP = 32;

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
        if (data.state() != SoulState.PLUS || data.chainMaxTicks() <= 0
                || player.isCreative() || player.isSpectator()) {
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

    private static boolean shinigamiNearby(ServerPlayer player) {
        double rangeSqr = SHINIGAMI_RANGE * SHINIGAMI_RANGE;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.distanceToSqr(player) <= rangeSqr && SoulLogic.hasSoul(other)
                    && SoulLogic.get(other).state() == SoulState.SHINIGAMI) {
                return true;
            }
        }
        return false;
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
        player.getLastDeathLocation().ifPresent(location -> returnToDeathPoint(player, location, diedAt));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.bleach.became_plus")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /**
     * Moves the player to where they died, settled onto the ground below so a death in mid-air does
     * not end in a long fall. Leaves them at their respawn point if that spot is in a wall, in a
     * liquid or fire, or over the void.
     */
    private static void returnToDeathPoint(ServerPlayer player, GlobalPos location, @Nullable Vec3 exact) {
        ServerLevel level = player.server.getLevel(location.dimension());
        if (level == null) {
            return;
        }
        Vec3 target = exact != null ? exact : Vec3.atBottomCenterOf(location.pos());
        if (target.y < level.getMinBuildHeight() || target.y >= level.getMaxBuildHeight()) {
            return;
        }
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(target);
        if (!level.noCollision(player, box)) {
            return;
        }
        int dropped = 0;
        while (dropped < MAX_DROP && level.noCollision(player, box.move(0.0, -1.0, 0.0))) {
            box = box.move(0.0, -1.0, 0.0);
            dropped++;
        }
        if (dropped >= MAX_DROP || box.minY < level.getMinBuildHeight()) {
            return;
        }
        BlockPos feet = BlockPos.containing(target.x, box.minY, target.z);
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                || level.getBlockState(feet).is(BlockTags.FIRE)) {
            return;
        }
        player.teleportTo(level, target.x, box.minY, target.z, player.getYRot(), player.getXRot());
    }
}
