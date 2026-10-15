package io.github.masteryoga1.bleach.soul;

import net.minecraft.world.phys.Vec3;

/** A creature overwhelmed by released reiatsu: it runs from {@code from} until game time {@code until}. */
public record Fright(long until, Vec3 from) {
    public static final Fright NONE = new Fright(Long.MIN_VALUE, Vec3.ZERO);
}
