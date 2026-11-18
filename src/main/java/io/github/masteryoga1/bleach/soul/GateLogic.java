package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.registry.BleachTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Who may pass Seireitei's gates, the rule the wickets keep: a question of the soul alone, so a client asks it of its own
 * player as the server does, and lets every other player be. Only an inner Hollow's hold is the server's alone, so a
 * client may briefly mispredict a wicket for a Visored held unmasked.
 */
public final class GateLogic {
    private GateLogic() {
    }

    /** Whether this player may pass a gate into Seireitei: in creative or spectator anyone, else as its soul may. */
    public static boolean mayPass(Player player) {
        return !HollowPlayerLogic.survivalRules(player) || SoulLogic.hasSoul(player) && mayPass(SoulLogic.get(player));
    }

    /**
     * Whether this soul may pass: none that looks a Hollow, is held by its inner one, or is a Quincy the Academy would
     * not take; then one with a Shinigami's power, Substitutes too, or a Rukongai soul the Academy would take or is teaching.
     */
    public static boolean mayPass(SoulData data) {
        if (HollowPlayerLogic.seenAsHollow(data) || VisoredLogic.takenOver(data) || QuincyLogic.blocksShinigamiPower(data)) {
            return false;
        }
        return ShinigamiLogic.hasShinigamiPower(data) || ShinigamiLogic.canEnroll(data) || ShinigamiLogic.isStudent(data);
    }

    /** The line a soul the gates bar is told at a wicket, by why they bar it: as a Hollow, a Quincy, a Plus or living. */
    public static String barredKey(SoulData data) {
        if (HollowPlayerLogic.seenAsHollow(data) || VisoredLogic.takenOver(data)) {
            return "message.bleach.gate.barred.hollow";
        }
        return QuincyLogic.isAwakened(data) ? "message.bleach.gate.barred.quincy"
                : data.state() == SoulState.PLUS ? "message.bleach.gate.barred.plus" : "message.bleach.gate.barred.living";
    }

    /** Whether this point lies beyond the wicket's cell on its outer side, the Rukongai's, where its leaf closes. */
    public static boolean outside(Vec3 at, BlockPos wicket, Direction facing) {
        Direction out = facing.getOpposite();
        return (at.x - wicket.getX() - 0.5) * out.getStepX() + (at.z - wicket.getZ() - 0.5) * out.getStepZ() > 0.5;
    }

    /**
     * Whether the gates bar this body at this wicket: on its outer side, with a player they bar or a Hollow in its stack,
     * that of whoever threw it if it was thrown. A client knows no soul but its own player's, so there no other player bars.
     */
    public static boolean bars(Entity body, BlockPos wicket, Direction facing) {
        if (!outside(body.position(), wicket, facing)) {
            return false;
        }
        Entity owner = body instanceof Projectile thrown ? thrown.getOwner() : null;
        return (owner != null ? owner : body).getRootVehicle().getSelfAndPassengers().anyMatch(each ->
                each instanceof Player player ? (player.isLocalPlayer() || !player.level().isClientSide()) && !mayPass(player)
                        : each instanceof LivingEntity && each.getType().is(BleachTags.HOLLOWS));
    }
}
