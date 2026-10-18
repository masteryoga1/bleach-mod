package io.github.masteryoga1.bleach.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who a soul's own projectiles may strike: the rules the Getsuga Tenshō and the Heilig Pfeil share,
 * so a crescent and an arrow from the same soul spare the same creatures.
 */
public final class SpiritProjectiles {
    private SpiritProjectiles() {
    }

    /**
     * Whether a projectile fired by {@code owner} may strike this creature: never its owner, anything
     * riding with them or their tamed animals, never a Plus or a Shinigami, nor a player in creative
     * or spectator mode or one the owner may not harm (a teammate, with friendly fire off). A
     * projectile whose owner is gone keeps only the rules that do not need one.
     */
    public static boolean mayStrike(@Nullable Entity owner, LivingEntity target) {
        if (!target.isAlive()) {
            return false;
        }
        if (owner != null) {
            if (target == owner || target.getRootVehicle() == owner.getRootVehicle()) {
                return false;
            }
            if (target instanceof OwnableEntity ownable) {
                UUID tamer = ownable.getOwnerUUID();
                if (tamer != null && tamer.equals(owner.getUUID())) {
                    return false;
                }
            }
        }
        if (target instanceof PlusSpirit || target instanceof ShinigamiNpc) {
            return false;
        }
        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            if (owner instanceof Player attacker && !attacker.canHarmPlayer(player)) {
                return false;
            }
        }
        return true;
    }
}
