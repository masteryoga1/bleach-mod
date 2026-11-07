package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Death and the Chain of Fate: an aware Human who dies lingers as a Plus where they fell, their
 * chain wears away (more slowly with a Shinigami nearby), Encroachment sets in near the end, and a
 * Plus whose chain runs out becomes a Hollow, or, if it is an awakened Quincy, is torn apart (see
 * {@link QuincyEvents#destroySoul}). A Plus can also gnaw at its own chain with the Devour key, and it
 * wears away many times faster while it does (see {@link DevourLogic#gnawSpeed}). Konsō, from a
 * Shinigami, sends a Plus on before that (see {@link ShinigamiEvents#konso}).
 */
public final class ChainEvents {
    public static final ResourceKey<DamageType> ENCROACHMENT =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("encroachment"));

    /** The Devour key, named in the hints so each player sees their own binding. */
    private static final String DEVOUR_KEY = "key.bleach.devour";
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
        // 1 unless the Plus is gnawing at its chain (DevourEvents starts and stops that, and hurts it as it goes).
        speed *= DevourLogic.gnawSpeed(data);
        if (SoulLogic.erodeChain(data, speed)) {
            if (QuincyLogic.isAwakened(data) && !QuincyEvents.canDestroySoulNow(player)) {
                // Nothing can hurt the player right now (between dimensions): the chain stays broken,
                // and the soul tears on a later tick.
                return;
            }
            // An awakened Quincy's soul tears apart instead. If that death is cancelled, it Hollowfies,
            // so the broken chain is not seen again next tick.
            if (!QuincyLogic.isAwakened(data) || !QuincyEvents.destroySoul(player, data)) {
                hollowfy(player, data);
            }
            return;
        }

        boolean encroaching = SoulLogic.isEncroaching(data);
        if (encroaching && !data.wasEncroaching) {
            player.sendSystemMessage(Component.translatable("message.bleach.encroachment_begins")
                    .withStyle(ChatFormatting.DARK_RED));
            // The hunger stirring shows the quicker way, unless the gnaw would be refused or is already under way.
            if (DevourLogic.gnawRefusal(player, data) == DevourLogic.Gnaw.GNAWING && !DevourLogic.gnawing(data)) {
                player.sendSystemMessage(Component.translatable("message.bleach.gnaw.hint", Component.keybind(DEVOUR_KEY))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
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

    /**
     * Whether a Shinigami, a Substitute or a Shinigami NPC is close enough to this Plus, a player or a
     * Plus spirit, to slow its chain. Always false off the server.
     */
    public static boolean shinigamiNearby(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        double rangeSqr = SHINIGAMI_RANGE * SHINIGAMI_RANGE;
        for (ServerPlayer other : level.players()) {
            if (other != entity && other.distanceToSqr(entity) <= rangeSqr && ShinigamiLogic.slowsErosion(other)) {
                return true;
            }
        }
        return !level.getEntitiesOfClass(ShinigamiNpc.class, entity.getBoundingBox().inflate(SHINIGAMI_RANGE),
                npc -> npc.distanceToSqr(entity) <= rangeSqr && ShinigamiLogic.slowsErosion(npc)).isEmpty();
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

    /**
     * The chain broke: the soul becomes a Hollow, with a roar, and is told how a Hollow feeds. Its
     * hunger and its body come on with its next tick (see {@link HollowBody#tickBody}), and a gnaw under
     * way ends by itself now that it is no Plus.
     */
    private static void hollowfy(ServerPlayer player, SoulData data) {
        SoulLogic.hollowfy(player, data);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.sendSystemMessage(Component.translatable("message.bleach.hollowfied")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("message.bleach.hollow_player.hint", Component.keybind(DEVOUR_KEY))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
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
        AcademyLocator.sense(player);
    }

    /**
     * Moves the player to where they died, settled onto the ground below so a death in mid-air does
     * not end in a long fall.
     *
     * @return false, leaving them at their respawn point, if that spot is unsafe (see {@link SafeLanding#tryMoveTo})
     *         or in the Dangai, where a soul with no crossing to follow would only be put out again
     */
    public static boolean returnToDeathPoint(ServerPlayer player, GlobalPos location, @Nullable Vec3 exact) {
        ServerLevel level = player.server.getLevel(location.dimension());
        if (level == null || location.dimension() == BleachWorlds.DANGAI) {
            return false;
        }
        return SafeLanding.tryMoveTo(player, level, exact != null ? exact : Vec3.atBottomCenterOf(location.pos()));
    }

    /** Whether vanilla respawned the player at a respawn anchor, which spends one of its charges. */
    static boolean respawnedAtAnchor(ServerPlayer player) {
        BlockPos anchor = player.getRespawnPosition();
        return anchor != null && !player.isRespawnForced()
                && player.level().dimension() == player.getRespawnDimension()
                && player.level().getBlockState(anchor).getBlock() instanceof RespawnAnchorBlock
                && player.position().closerThan(Vec3.atCenterOf(anchor), 3.0);
    }

    /** The soul never used the anchor it would have respawned at, so it gets back the charge. */
    static void refundAnchorCharge(ServerLevel level, @Nullable BlockPos anchor) {
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
