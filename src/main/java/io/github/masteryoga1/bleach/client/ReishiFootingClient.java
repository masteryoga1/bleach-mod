package io.github.masteryoga1.bleach.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.network.ReishiFootingStatePayload;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.soul.FootingState;
import io.github.masteryoga1.bleach.soul.ReishiFooting;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The client half of reishi footing. Asking for it (with the key, or by pressing jump while
 * falling) predicts where it will hold, and the fall is held at that height until the plates
 * arrive from the server, so the player stops where they asked instead of a little lower.
 */
public final class ReishiFootingClient {
    /** Unbound at first: pressing jump while falling does the same. */
    public static final KeyMapping REISHI_FOOTING_KEY = new KeyMapping("key.bleach.reishi_footing",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, BleachClient.KEY_CATEGORY);

    private static boolean jumpWasDown;

    private ReishiFootingClient() {
    }

    /** Before this tick's movement: asks for footing if the key or jump was just pressed in the air. */
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean keyPressed = false;
        while (REISHI_FOOTING_KEY.consumeClick()) {
            keyPressed = true;
        }
        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || minecraft.getConnection() == null
                || !(keyPressed || jumpPressed) || !mayAsk(player)) {
            return;
        }
        boolean viaJump = !keyPressed;
        if (viaJump && !(BleachClientConfig.footingOnJump() && player.getDeltaMovement().y < 0.0
                && !player.getAbilities().mayfly && !player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player)
                && ReishiFooting.noGroundBelow(player.level(), player))) {
            return;
        }
        BleachNetwork.requestFooting(viaJump);
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        footing.anchorY = ShinigamiLogic.footingAnchor(player.getY());
        footing.clampUntilTick = player.tickCount + ReishiFootingStatePayload.CLAMP_TICKS;
    }

    /** The checks the server makes too, so a request it would refuse is not sent. */
    private static boolean mayAsk(LocalPlayer player) {
        return !player.onGround() && !player.isInLiquid() && !player.isFallFlying() && !player.isPassenger()
                && !player.onClimbable() && !player.isSpectator() && !player.getAbilities().flying
                && !player.hasEffect(ModEffects.GRITZ) && SoulLogic.hasSoul(player)
                && ShinigamiLogic.canUseReishiFooting(SoulLogic.get(player), player.level().getGameTime());
    }

    /**
     * Before the local player moves: while footing holds, or for a moment after it is asked for, a
     * fall stops at the footing's top until a plate is there to land on. Once one is, the plate
     * itself holds the player. Where no plate can form, because the blocks at the footing's height
     * under the player are not air, the hold lapses on its own, so a player over grass, water or a
     * slab still lands. Like the server, sinking below the footing while not sneaking moves it down
     * to the new height.
     */
    static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.hasData(SoulAttachments.FOOTING)) {
            return;
        }
        FootingState footing = player.getData(SoulAttachments.FOOTING);
        if (!footing.active && player.tickCount > footing.clampUntilTick || player.isShiftKeyDown() || player.onClimbable()) {
            return;
        }
        if (footing.active && player.getY() < footing.anchorY + ReishiFooting.SUNK) {
            footing.anchorY = ShinigamiLogic.footingAnchor(player.getY());
            footing.clampUntilTick = player.tickCount + ReishiFootingStatePayload.CLAMP_TICKS;
        }
        if (footing.active && plateUnder(player, footing.anchorY)) {
            footing.clampUntilTick = player.tickCount + ReishiFootingStatePayload.CLAMP_TICKS;
            return;
        }
        if (footing.active && airUnder(player, footing.anchorY)) {
            // Plates can still form here, however late they arrive.
            footing.clampUntilTick = player.tickCount + ReishiFootingStatePayload.CLAMP_TICKS;
        }
        Vec3 motion = player.getDeltaMovement();
        double top = footing.anchorY + 1.0;
        if (player.tickCount <= footing.clampUntilTick && motion.y < 0.0 && player.getY() >= top - 1.0E-4
                && !groundHolds(player, top)) {
            player.setDeltaMovement(motion.x, Math.max(motion.y, top - player.getY()), motion.z);
        }
    }

    /** Whether real blocks would stop the fall at or above the footing's top anyway, so it needs no hold. */
    private static boolean groundHolds(LocalPlayer player, double top) {
        AABB box = player.getBoundingBox();
        // With no entity, the plates are not solid.
        return !player.level().noCollision((Entity) null, box.expandTowards(0.0, top - box.minY - 1.0E-3, 0.0));
    }

    /** Whether any block at the footing's height under the player is air, where a plate can still be laid. */
    private static boolean airUnder(LocalPlayer player, int anchorY) {
        AABB box = player.getBoundingBox();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                if (player.level().getBlockState(new BlockPos(x, anchorY, z)).isAir()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean plateUnder(LocalPlayer player, int anchorY) {
        AABB box = player.getBoundingBox();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                if (player.level().getBlockState(new BlockPos(x, anchorY, z)).is(ModBlocks.REISHI_FOOTING.get())) {
                    return true;
                }
            }
        }
        return false;
    }
}
