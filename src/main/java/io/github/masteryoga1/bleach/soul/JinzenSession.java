package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import io.github.masteryoga1.bleach.zanpakuto.JinzenScene;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One sitting in Jinzen, from settling down to opening one's eyes: where the soul sat, whether the
 * world inside the blade has opened yet, which scene it showed, and how far through the spirit's
 * test the soul has got. Server side only, and never saved; {@link JinzenLogic} runs it.
 */
public final class JinzenSession {
    /** Tells pages of this sitting apart from a stale screen's answers. */
    final int id;
    /** Seeds the red ribbon and the order of a question's answers. */
    final long seed;
    /** Where the soul sat down; drifting away from it breaks the sitting. */
    final Vec3 anchor;
    final long begunAt;
    boolean opened;
    long openedAt;
    JinzenScene scene;
    /** The spirit met, for a meeting. */
    @Nullable
    ResourceLocation spirit;
    /** The test step being shown, counted from 0. */
    int step;
    /** Game time the current step was shown. */
    long stepStartedAt;
    /** The order a question's answers are shown in: position i shows the spirit's answer {@code order[i]}. */
    int[] order = new int[0];
    /** Whether nothing more can happen but opening one's eyes. */
    boolean finished;
    /** The page the soul is looking at. */
    JinzenPage page;

    JinzenSession(int id, long seed, Vec3 anchor, long begunAt) {
        this.id = id;
        this.seed = seed;
        this.anchor = anchor;
        this.begunAt = begunAt;
    }

    public int id() {
        return id;
    }

    public long seed() {
        return seed;
    }

    public boolean opened() {
        return opened;
    }

    /** The scene shown, or null while still settling. */
    @Nullable
    public JinzenScene scene() {
        return scene;
    }

    public int step() {
        return step;
    }

    public boolean finished() {
        return finished;
    }

    /** The page shown, or null while still settling. */
    @Nullable
    public JinzenPage page() {
        return page;
    }
}
