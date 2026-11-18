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
 * Who may pass Seireitei's gates, the rule the wickets and keepers keep: a question of the soul, and of the game time for
 * the pass a soul wins from a gate's keeper, so a client asks it of its own player as the server does, and lets every
 * other player be. Only an inner Hollow's hold is the server's alone, so a client may briefly mispredict a wicket for a
 * Visored held unmasked.
 */
public final class GateLogic {
    /** How long a keeper's pass holds: a game day, on the game time, which neither {@code /time} nor sleep moves. */
    public static final int PASS_TICKS = 24_000;

    private GateLogic() {
    }

    /** Whether this player may pass a gate into Seireitei, asking of no gate in particular, so by the rule alone. */
    public static boolean mayPass(Player player) {
        return mayPass(player, -1);
    }

    /**
     * Whether this player may pass this gate (0 to 3, or -1 for none known): in creative or spectator anyone, else as its
     * soul may there now.
     */
    public static boolean mayPass(Player player, int gate) {
        return !HollowPlayerLogic.survivalRules(player) || SoulLogic.hasSoul(player)
                && mayPass(SoulLogic.get(player), gate, player.level().getGameTime());
    }

    /**
     * Whether this soul may pass this gate now: as {@link #mayPass(SoulData)}, or by the pass it won there, which no soul
     * that looks a Hollow or is held by its inner one may use, since the keeper fights what he sees.
     */
    public static boolean mayPass(SoulData data, int gate, long now) {
        return mayPass(data) || holdsPass(data, gate, now) && !HollowPlayerLogic.seenAsHollow(data)
                && !VisoredLogic.takenOver(data);
    }

    /** Whether this soul holds this gate's pass now: not lapsed, and lapsing within two days, as every pass won here does. */
    public static boolean holdsPass(SoulData data, int gate, long now) {
        long until = data.shinigami().gates().until(gate);
        return now < until && until - now <= 2L * PASS_TICKS;
    }

    /** Gives this soul this gate's pass for a game day from now, renewing any it holds there. */
    public static void grantPass(SoulData data, int gate, long now) {
        ShinigamiProgress progress = data.shinigami();
        data.setShinigami(progress.withGates(progress.gates().withUntil(gate, now + PASS_TICKS)));
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
     * Whether the gates bar this body at this wicket of this gate (-1 for none known): on its outer side, with a player they
     * bar or a Hollow in its stack, that of whoever threw it if it was thrown. A client knows no soul but its own player's,
     * so there no other player bars.
     */
    public static boolean bars(Entity body, BlockPos wicket, Direction facing, int gate) {
        if (!outside(body.position(), wicket, facing)) {
            return false;
        }
        Entity owner = body instanceof Projectile thrown ? thrown.getOwner() : null;
        return (owner != null ? owner : body).getRootVehicle().getSelfAndPassengers().anyMatch(each ->
                each instanceof Player player ? (player.isLocalPlayer() || !player.level().isClientSide())
                        && !mayPass(player, gate) : each instanceof LivingEntity && each.getType().is(BleachTags.HOLLOWS));
    }
}
