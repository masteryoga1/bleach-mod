package io.github.masteryoga1.bleach.soul;

import net.minecraft.world.phys.Vec3;

/**
 * A Licht Regen volley still falling: how many arrows are left to loose, the creature they fall on,
 * and where to aim once it is gone. Server-side bookkeeping on {@link SoulData}, never saved.
 *
 * @param left     arrows still to loose
 * @param targetId the entity id of the creature they fall on, or -1 for none
 * @param point    where they fall when there is no creature, or once it is gone
 */
record LichtVolley(int left, int targetId, Vec3 point) {
    LichtVolley withLeft(int value) {
        return new LichtVolley(value, targetId, point);
    }
}
