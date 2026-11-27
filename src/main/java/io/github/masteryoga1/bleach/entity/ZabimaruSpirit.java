package io.github.masteryoga1.bleach.entity;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Zabimaru drawn out of its wielder's inner world: a great baboon with a snake for a tail, empty-handed. The snake is a
 * part of it from before its first tick ({@link Snake}), on both sides, standing out behind the baboon's box, so a blow
 * from behind finds it first: whatever strikes the snake strikes the baboon, as a giant's parts do, and the blow is
 * marked as the snake's ({@link #lastStruck}) before its events run, until the next blow that lands. Out of a Bankai
 * trial the two share one body and one pool of health. In a trial that tests it by its two halves the snake has a pool
 * of its own ({@link #splitHalves}): each half has the floor and is beaten down by the blows marked as its own, and the
 * spirit is beaten only once both are down together; a half down alone rises again ({@link #rise}).
 *
 * <p>A lookup in the world finds the snake by its own box, wherever the baboon's box is, as the level keeps every part
 * apart from its entity sections; a player's pick and an arrow strike it so. One that asks only for living creatures
 * finds the baboon alone, and a blow found through {@link SpiritProjectiles#creaturesIn}, which takes a part for its
 * body, falls on the baboon: a Getsuga Tenshō, a Cero or Hihiō Zabimaru's passage through the snake is the baboon's.
 */
public class ZabimaruSpirit extends MaterialisedSpirit implements BossBar.Gauged {
    /** How far behind the baboon's feet the snake's box stands, and how far up, in blocks: over its back. */
    public static final double SNAKE_BEHIND = 1.0, SNAKE_LIFT = 1.2;
    private static final float SNAKE_WIDTH = 1.0F, SNAKE_HEIGHT = 1.2F;
    /** How far its box for drawing reaches past its own, in blocks, so the snake is drawn when only it is in view. */
    private static final double DRAWN_REACH = 1.2;
    /** When a half that stands went down: never. */
    private static final long UP = Long.MIN_VALUE;
    /** What its wielder is told as each half, by its order, goes down and rises again. */
    private static final String[] DOWN = {"message.bleach.bankai.trial.down.baboon",
            "message.bleach.bankai.trial.down.snake"};
    private static final String[] RISEN = {"message.bleach.bankai.trial.risen.baboon",
            "message.bleach.bankai.trial.risen.snake"};

    /** The half of Zabimaru a blow fell on. */
    public enum Half {
        BABOON, SNAKE
    }

    private final Snake snake;
    private final PartEntity<?>[] parts;
    @Nullable
    private Half lastStruck;
    /** Whether its health is split between its halves, and the snake's own pool while it is. */
    private boolean split;
    private float snakeHealth;
    /** Its bout's time as the bout last gave it, which times each fall, so a fall and a rise read the one clock. */
    private long clock;
    /** When each half, by its order, went down to its floor, or {@link #UP} while it stands. */
    private final long[] downAt = {UP, UP};

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

    /**
     * Splits its health between its halves as a Bankai trial that tests them begins, at this time of the bout's: the
     * baboon keeps the body's, and the snake has a pool of its own, as full as the body is now.
     */
    public void splitHalves(long now) {
        split = true;
        snakeHealth = getHealth();
        clock = now;
    }

    public boolean split() {
        return split;
    }

    /** The health of one half: the body's for the baboon, and for the snake too unless its health is split. */
    public float health(Half half) {
        return split && half == Half.SNAKE ? snakeHealth : getHealth();
    }

    /** Whether this half of a split body is down at its floor; never so for one whose health is not split. */
    public boolean down(Half half) {
        return downAt[half.ordinal()] != UP;
    }

    /**
     * A blow on the snake of a split body lands on the snake's pool, which stands in for the body's health while it
     * lands, so armour, the floor and every listener weigh it as they would the body's; the body's is put back after.
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!split || lastStruck != Half.SNAKE) {
            super.actuallyHurt(source, amount);
            return;
        }
        float body = getHealth();
        setHealth(snakeHealth);
        try {
            super.actuallyHurt(source, amount);
        } finally {
            snakeHealth = getHealth();
            setHealth(body);
        }
    }

    /**
     * At its floor: whole, beaten as any spirit. Split, the half struck is down from its first blow there, by its bout's
     * time (no later one starts its time again), and the spirit is beaten once the other half is down too; until then
     * its wielder is told, and the last half down is told by the yield.
     */
    @Override
    public void atFloor(DamageSource source) {
        Half half = lastStruck == null ? Half.BABOON : lastStruck;
        if (!split) {
            super.atFloor(source);
        } else if (!down(half)) {
            downAt[half.ordinal()] = clock;
            if (down(Half.BABOON) && down(Half.SNAKE)) {
                super.atFloor(source);
            } else {
                tell(DOWN[half.ordinal()]);
            }
        }
    }

    /**
     * Asked on each tick of its bout, with the bout's time, by which its halves fall too: a half down alone for
     * {@code window} ticks rises again with {@code share} of the body's full health, never less than it has or the
     * floor, and its wielder is told. Nothing while neither half is down or both are, as then it yields.
     */
    public void rise(long now, long window, double share) {
        clock = now;
        if (!split || down(Half.BABOON) == down(Half.SNAKE)) {
            return;
        }
        Half half = down(Half.BABOON) ? Half.BABOON : Half.SNAKE;
        if (now - downAt[half.ordinal()] < window) {
            return;
        }
        downAt[half.ordinal()] = UP;
        float risen = Math.max(health(half), Math.max(floor(), (float) (getMaxHealth() * share)));
        if (half == Half.SNAKE) {
            snakeHealth = risen;
        } else {
            setHealth(risen);
        }
        tell(RISEN[half.ordinal()]);
    }

    /** Its bar shows both halves, each as half of it: the two pools' health between them over their full health. */
    @Override
    public float gauge() {
        return split ? (getHealth() + snakeHealth) / (2.0F * getMaxHealth()) : getHealth() / getMaxHealth();
    }

    /** A line to its wielder's action bar, in grey. */
    private void tell(String key) {
        Player wielder = wielder();
        if (wielder != null) {
            wielder.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
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
