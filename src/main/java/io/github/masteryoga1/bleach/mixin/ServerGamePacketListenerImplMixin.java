package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.soul.ShakonmakuEvents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's own moves, and its moves of what it steers, are judged against the Shakonmaku once the server has taken
 * them: from where it stood as the packet came in, on the server's thread, to where the server let it go.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;
    @Shadow
    private boolean clientIsFloating;
    @Shadow
    private int aboveGroundTickCount;
    @Shadow
    private double vehicleLastGoodX;
    @Shadow
    private double vehicleLastGoodY;
    @Shadow
    private double vehicleLastGoodZ;
    @Shadow
    private boolean clientVehicleIsFloating;
    @Shadow
    private int aboveGroundVehicleTickCount;
    /** Where the player stood as its move packet came in; null between packets. */
    @Unique
    private Vec3 bleach$walkedFrom;
    /** How far the player had fallen as its move packet came in. */
    @Unique
    private float bleach$fallenBefore;
    /** What the player steered as its vehicle's packet came in; null between packets. */
    @Unique
    private Entity bleach$steered;
    /** Where that stood then. */
    @Unique
    private Vec3 bleach$steeredFrom;
    /** How far that had fallen then. */
    @Unique
    private float bleach$steeredFallen;

    /** NeoForge's own step after it moves a vehicle back: the player is set on it again, keeping where it looks. */
    @Shadow
    private void resyncPlayerWithVehicle(Entity vehicle) {
    }

    /** Notes where the player stands, and how far it has fallen, before the server takes its move. */
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/"
                    + "Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void bleach$noteWalk(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        bleach$walkedFrom = player.position();
        bleach$fallenBefore = player.fallDistance;
    }

    /** Once the server has taken the move, or refused it, the membrane judges where it went. */
    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void bleach$judgeWalk(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        Vec3 from = bleach$walkedFrom;
        bleach$walkedFrom = null;
        if (from != null && ShakonmakuEvents.walked(player, from, bleach$fallenBefore)) {
            // The move vanilla took marked the soul as floating; set back, it is not, so the membrane never counts as flight.
            clientIsFloating = false;
            aboveGroundTickCount = 0;
        }
    }

    /** Notes what the player steers, where, and how far it has fallen, before the server takes its move. */
    @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/"
                    + "Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void bleach$noteRide(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        bleach$steered = player.getRootVehicle();
        bleach$steeredFrom = bleach$steered.position();
        bleach$steeredFallen = bleach$steered.fallDistance;
    }

    /** Once the server has taken the vehicle's move, the membrane judges it, unless the player has left it since. */
    @Inject(method = "handleMoveVehicle", at = @At("RETURN"))
    private void bleach$judgeRide(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        Entity vehicle = bleach$steered;
        Vec3 from = bleach$steeredFrom;
        bleach$steered = null;
        bleach$steeredFrom = null;
        if (vehicle != null && vehicle != player && vehicle == player.getRootVehicle()
                && ShakonmakuEvents.rode(player, vehicle, from, bleach$steeredFallen)) {
            // As after NeoForge's own refusal: the rider is set on its mount again, the next move this tick is measured
            // from where the stack went back to, and the floating that move marked is forgotten.
            resyncPlayerWithVehicle(vehicle);
            vehicleLastGoodX = from.x;
            vehicleLastGoodY = from.y;
            vehicleLastGoodZ = from.z;
            clientVehicleIsFloating = false;
            aboveGroundVehicleTickCount = 0;
        }
    }
}
