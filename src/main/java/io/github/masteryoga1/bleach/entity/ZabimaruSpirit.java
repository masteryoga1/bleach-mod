package io.github.masteryoga1.bleach.entity;

import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Zabimaru drawn out of its wielder's inner world: a great baboon with a snake for a tail, empty-handed. The snake is a
 * part of it from before its first tick ({@link Snake}), on both sides, standing out behind the baboon's box, so a blow
 * from behind finds it first: whatever strikes the snake strikes the baboon, as a giant's parts do, and the blow is
 * marked as the snake's ({@link #lastStruck}) before its events run, until the next blow that lands. The two share one
 * body and one pool of health; the test of its two halves, still to come, reads the mark.
 *
 * <p>A lookup in the world finds the snake by its own box, wherever the baboon's box is, as the level keeps every part
 * apart from its entity sections; a player's pick and an arrow strike it so. One that asks only for living creatures
 * finds the baboon alone, and a blow found through {@link SpiritProjectiles#creaturesIn}, which takes a part for its
 * body, falls on the baboon: a Getsuga Tenshō, a Cero or Hihiō Zabimaru's passage through the snake is the baboon's.
 */
public class ZabimaruSpirit extends MaterialisedSpirit {
    /** How far behind the baboon's feet the snake's box stands, and how far up, in blocks: over its back. */
    public static final double SNAKE_BEHIND = 1.0, SNAKE_LIFT = 1.2;
    private static final float SNAKE_WIDTH = 1.0F, SNAKE_HEIGHT = 1.2F;
    /** How far its box for drawing reaches past its own, in blocks, so the snake is drawn when only it is in view. */
    private static final double DRAWN_REACH = 1.2;

    /** The half of Zabimaru a blow fell on. */
    public enum Half {
        BABOON, SNAKE
    }

    private final Snake snake;
    private final PartEntity<?>[] parts;
    @Nullable
    private Half lastStruck;

    public ZabimaruSpirit(EntityType<? extends ZabimaruSpirit> type, Level level) {
        super(type, level);
        snake = new Snake(this);
        parts = new PartEntity<?>[] {snake};
        // An id for itself and one for the snake, as the giants take them.
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    /** Where the snake's box stands for a baboon at these feet turned this way: behind it, and up over its back. */
    public static Vec3 snakeAt(double x, double y, double z, float bodyYaw) {
        float turn = bodyYaw * Mth.DEG_TO_RAD;
        return new Vec3(x + Mth.sin(turn) * SNAKE_BEHIND, y + SNAKE_LIFT, z - Mth.cos(turn) * SNAKE_BEHIND);
    }

    public Snake snake() {
        return snake;
    }

    /** The half the last blow that landed fell on, or the one falling now while its events run; null before any. */
    @Nullable
    public Half lastStruck() {
        return lastStruck;
    }

    @Override
    protected BossEvent.BossBarColor barColour() {
        return BossEvent.BossBarColor.RED;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return hurtAs(Half.BABOON, source, amount);
    }

    /** A blow on one half: marked as that half's while it lands, and kept so only if it did. */
    boolean hurtAs(Half half, DamageSource source, float amount) {
        Half before = lastStruck;
        lastStruck = half;
        boolean landed = super.hurt(source, amount);
        if (!landed) {
            lastStruck = before;
        }
        return landed;
    }

    /** The snake follows the baboon on both sides, after it has moved. */
    @Override
    public void aiStep() {
        super.aiStep();
        placeSnake();
    }

    /** It stands where it should from the first. */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        placeSnake();
    }

    private void placeSnake() {
        Vec3 at = snakeAt(getX(), getY(), getZ(), yBodyRot);
        snake.placeAt(at.x, at.y, at.z);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        // Null while the entity is being made; the constructor sets the ids again once the snake exists.
        if (parts != null) {
            parts[0].setId(id + 1);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(DRAWN_REACH);
    }

    /** Zabimaru's snake: a blow on it strikes the baboon, marked as the snake's (see {@link GiantPart}). */
    public static final class Snake extends GiantPart<ZabimaruSpirit> {
        Snake(ZabimaruSpirit parent) {
            super(parent, SNAKE_WIDTH, SNAKE_HEIGHT);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return !isInvulnerableTo(source) && getParent().hurtAs(Half.SNAKE, source, amount);
        }
    }
}
