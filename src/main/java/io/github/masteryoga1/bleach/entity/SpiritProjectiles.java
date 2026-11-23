package io.github.masteryoga1.bleach.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Who a soul's own projectiles may strike: the rules the Getsuga Tenshō and the Heilig Pfeil share,
 * so a crescent and an arrow from the same soul spare the same creatures; and the creatures in a box,
 * giants by their parts too, which every beam and crescent asks for.
 */
public final class SpiritProjectiles {
    private SpiritProjectiles() {
    }

    /**
     * Whether a projectile fired by {@code owner} may strike this creature: never its owner, anything
     * riding with them or their tamed animals, never a Plus, a Shinigami or Kisuke Urahara, nor a player in creative
     * or spectator mode or one the owner may not harm (a teammate, with friendly fire off). A living
     * Quincy of the Human World is never struck, and their own arrows strike only the Hollows they
     * hunt ({@link QuincyNpc#isQuarry}), so a miss passes through everyone else. A practice Hollow is
     * struck by its own student's alone. A projectile whose owner is gone keeps only the rules that do
     * not need one.
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
        if (target instanceof QuincyNpc) {
            return false;
        }
        if (target instanceof Hollow hollow && hollow.isPractice()
                && (owner == null || !owner.getUUID().equals(hollow.trialOf()))) {
            return false;
        }
        if (owner instanceof QuincyNpc) {
            return QuincyNpc.isQuarry(target);
        }
        if (target instanceof PlusSpirit || target instanceof ShinigamiNpc || target instanceof KisukeUrahara) {
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

    /**
     * The creatures in this box that pass the filter, each once, in no particular order, as a new list
     * the caller may sort. A creature counts when its own box or any of its parts' boxes meets this
     * one: a giant such as a Gillian stands in entity sections far below a box about its head, where
     * a plain lookup never looks, but its parts are always found, and each is taken for the body it
     * belongs to. The filter sees only bodies, never parts.
     */
    public static List<LivingEntity> creaturesIn(Level level, AABB box, Predicate<? super LivingEntity> filter) {
        List<LivingEntity> creatures = new ArrayList<>();
        IntOpenHashSet seen = new IntOpenHashSet();
        for (Entity entity : level.getEntities((Entity) null, box,
                entity -> entity instanceof LivingEntity || entity instanceof PartEntity<?>)) {
            LivingEntity body = bodyOf(entity);
            // A body met through its own box and through a part again, or through two parts, counts once.
            if (body != null && seen.add(body.getId()) && filter.test(body)) {
                creatures.add(body);
            }
        }
        return creatures;
    }

    /**
     * The creature a hit on this entity strikes: the entity itself if it is living, the living body a
     * part belongs to (a Gillian's head, an Ender Dragon's wing), or none.
     */
    @Nullable
    public static LivingEntity bodyOf(@Nullable Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (entity instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }
}
