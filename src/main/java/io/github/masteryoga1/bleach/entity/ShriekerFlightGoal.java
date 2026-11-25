package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/**
 * How a Shrieker fights. It circles its prey high in the air, out of reach of a blade swung from the ground, lower only
 * under a roof over the prey. When its claws are ready, after a while on the wing, with its prey near and in sight, it
 * swoops at the prey's middle and strikes as it reaches it. After the strike, or a swoop that comes to the ground, it
 * keeps its claws low a while, its mask near the height of the prey's eyes, where a blade swung at it meets the mask
 * before its body; then it climbs back to its circle. A swoop that misses gives up when it comes to the ground, runs
 * into a wall or takes too long. While a blow to its mask holds it, it sinks where it is to that low height, and any
 * swoop it was in is over. From its circle, before any swoop, it looses its leeches at its prey a few at a time
 * ({@link Shrieker#looseLeeches}).
 *
 * <p>With its prey far off or long out of sight it flies to it by a path, round what stands between; near and in sight
 * it steers straight. Held where it is while it steers (by a hill, a tree, a wall), it wishes itself higher, a little
 * more each tick, until it is free, and lets the wish down again after. The prey is never let go here: losing it is for
 * the target goals to judge.
 */
final class ShriekerFlightGoal extends Goal {
    /** How high over its prey's feet it circles, in blocks: out of reach of a blade swung from the ground. */
    static final double HOVER_HEIGHT = 6.0;
    /** How far out from its prey it circles. */
    static final double HOVER_RADIUS = 4.0;
    /** How far round the circle it goes each tick, in radians: about three degrees. */
    private static final double CIRCLE_STEP = 0.05;
    /** How far on round the circle it looks for room when its point has none, in radians, and how many times. */
    private static final double SKIP_STEP = 0.3;
    private static final int SKIP_TRIES = 21;
    /**
     * Its claws held low after a strike: how high its feet are over the prey's, how far out it circles and for how long.
     * Its mask is then a little over the prey's eyes and within a blade's reach of them, and a blade swung at it meets
     * the mask before its body; any higher, the body comes first.
     */
    static final double CLAWS_LOW_HEIGHT = 1.5;
    static final double CLAWS_LOW_RADIUS = 2.0;
    static final int CLAWS_LOW_TICKS = 20;
    /** The least time on the wing before a swoop. */
    static final int MIN_HOVER_TICKS = 30;
    /** How long a swoop may go on before it gives up. */
    static final int SWOOP_TIMEOUT_TICKS = 40;
    /** How long a swoop runs before meeting the ground or a wall ends it, so it can leave its circle first. */
    private static final int SWOOP_GRACE_TICKS = 5;
    /** No swoop at prey further than this. */
    private static final double SWOOP_RANGE = 12.0;
    /** The speed of a swoop and of the circle, as modifiers of its flying speed. */
    static final double SWOOP_SPEED = 1.8;
    private static final double HOVER_SPEED = 1.0;
    /** How far past its body its claws reach at the end of a swoop. */
    static final double STRIKE_REACH = 0.3;
    /** Further than this from its prey, or out of its sight for longer than this, it flies by a path. */
    private static final double PATH_RANGE = 16.0;
    private static final int LOST_SIGHT_TICKS = 20;
    /** Nearer than this to its prey, and with it in sight, it steers straight again. */
    private static final double DIRECT_RANGE = 12.0;
    /** How often it seeks its path afresh while it flies by one. */
    private static final int REPATH_TICKS = 10;
    /** How often it asks whether it is held, how little it must have moved since to be, and how far off its wish is. */
    private static final int HELD_CHECK_TICKS = 10;
    private static final double HELD_MOVE = 0.05;
    private static final double HELD_WISH = 1.0;
    /** How much higher it wishes itself each tick while held, and at most: a block and a half a second, up to 10. */
    private static final double LIFT_STEP = 1.5 / 20.0;
    private static final double MAX_LIFT = 10.0;
    private static final float LOOK_TURN = 30.0F;
    private static final float SWOOP_VOLUME = 1.5F;

    private final Shrieker shrieker;
    private boolean swooping;
    /** Ticks since it last went back to its circle, or since the swoop began. */
    private int phaseTicks;
    /** Ticks left of its claws held low. */
    private int lowTicks;
    /** Where on its circle it is, as an angle about its prey. */
    private double angle;
    /** Whether it flies to its prey by a path now, rather than straight. */
    private boolean pathing;
    private int unseenTicks;
    private int repathIn;
    /** How much higher than its point it wishes itself, for what holds it. */
    private double lift;
    /** Whether it was held at the last asking, and where it was then. */
    private boolean held;
    @Nullable
    private Vec3 askedAt;
    private int askIn;

    ShriekerFlightGoal(Shrieker shrieker) {
        this.shrieker = shrieker;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity prey = shrieker.getTarget();
        return prey != null && prey.isAlive() && shrieker.canAttack(prey);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** It takes up its circle on the side it is already on, steering straight until it finds it needs a path. */
    @Override
    public void start() {
        shrieker.getNavigation().stop();
        pathing = false;
        unseenTicks = 0;
        lift = 0.0;
        held = false;
        askedAt = null;
        backToCircle(shrieker.getTarget(), false);
    }

    @Override
    public void stop() {
        backToCircle(null, false);
        if (pathing) {
            pathing = false;
            shrieker.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        LivingEntity prey = shrieker.getTarget();
        if (prey == null) {
            return;
        }
        phaseTicks++;
        shrieker.getLookControl().setLookAt(prey, LOOK_TURN, LOOK_TURN);
        if (shrieker.isStalled()) {
            // Reeling from a blow to its mask: no swoop, no path, and down where it is to the height of its claws held low.
            if (swooping) {
                backToCircle(prey, false);
            }
            steerStraight();
            wish(new Vec3(shrieker.getX(), prey.getY() + CLAWS_LOW_HEIGHT, shrieker.getZ()), HOVER_SPEED);
            return;
        }
        if (swooping) {
            swoop(prey);
        } else {
            circle(prey);
        }
    }

    private void circle(LivingEntity prey) {
        boolean seen = shrieker.getSensing().hasLineOfSight(prey);
        unseenTicks = seen ? 0 : unseenTicks + 1;
        double distance = shrieker.distanceTo(prey);
        if (pathing ? seen && distance <= DIRECT_RANGE : distance > PATH_RANGE || unseenTicks > LOST_SIGHT_TICKS) {
            if (pathing) {
                steerStraight();
                angle = bearing(prey);
            } else {
                pathing = true;
                repathIn = 0;
            }
        }
        if (pathing && followPath(prey)) {
            return;
        }
        angle += CIRCLE_STEP;
        boolean low = lowTicks > 0;
        if (low) {
            lowTicks--;
        }
        Vec3 at = low ? circlePoint(prey, CLAWS_LOW_RADIUS, CLAWS_LOW_HEIGHT) : circlePoint(prey, HOVER_RADIUS, HOVER_HEIGHT);
        wish(at.add(0.0, liftFor(at), 0.0), HOVER_SPEED);
        if (low || !seen || distance > SWOOP_RANGE) {
            return;
        }
        // Its next leeches before any swoop, once the last are spent and their wait is over.
        if (shrieker.looseLeeches() == 0 && phaseTicks >= MIN_HOVER_TICKS && shrieker.attackReady()) {
            swooping = true;
            phaseTicks = 0;
            shrieker.setSwooping(true);
            shrieker.playSound(ModSounds.SHRIEKER_SWOOP.get(), SWOOP_VOLUME, 0.9F + shrieker.getRandom().nextFloat() * 0.2F);
        }
    }

    /** Its middle at the prey's middle; the blow lands as its body, claws and all, reaches the prey's. */
    private void swoop(LivingEntity prey) {
        wish(new Vec3(prey.getX(), prey.getY(0.5) - shrieker.getBbHeight() * 0.5, prey.getZ()), SWOOP_SPEED);
        if (shrieker.getBoundingBox().inflate(STRIKE_REACH).intersects(prey.getBoundingBox())) {
            shrieker.swing(InteractionHand.MAIN_HAND);
            shrieker.doHurtTarget(prey);
            backToCircle(prey, true);
        } else if (phaseTicks >= SWOOP_TIMEOUT_TICKS) {
            backToCircle(prey, false);
        } else if (phaseTicks >= SWOOP_GRACE_TICKS && (shrieker.onGround() || shrieker.horizontalCollision)) {
            // Down on the ground beside its prey it keeps its claws low; against a wall it goes back up.
            backToCircle(prey, shrieker.onGround());
        }
    }

    /**
     * Ends any swoop and takes up its circle again, on the side of its prey it is on now, its claws held low first if
     * {@code low}.
     */
    private void backToCircle(@Nullable LivingEntity prey, boolean low) {
        swooping = false;
        phaseTicks = 0;
        lowTicks = low ? CLAWS_LOW_TICKS : 0;
        shrieker.setSwooping(false);
        if (prey != null) {
            angle = bearing(prey);
        }
    }

    /** The angle about its prey of where it is now, so a circle taken up starts on the side it is on. */
    private double bearing(LivingEntity prey) {
        return Math.atan2(shrieker.getZ() - prey.getZ(), shrieker.getX() - prey.getX());
    }

    /** Flies on by its path to its prey, sought afresh now and then; false while it has none to follow. */
    private boolean followPath(LivingEntity prey) {
        PathNavigation navigation = shrieker.getNavigation();
        if (--repathIn <= 0) {
            repathIn = REPATH_TICKS;
            navigation.moveTo(prey, HOVER_SPEED);
        }
        return !navigation.isDone();
    }

    /** Leaves any path, so the point it wishes for is the one it steers to. */
    private void steerStraight() {
        if (pathing) {
            pathing = false;
            shrieker.getNavigation().stop();
        }
    }

    private void wish(Vec3 at, double speed) {
        shrieker.getMoveControl().setWantedPosition(at.x, at.y, at.z, speed);
    }

    /**
     * How much higher than this point it wishes itself: more each tick while something holds it (it has hardly moved
     * since it last asked though its point is off, or it is pressed against a block across), less each tick once it is
     * free, so it climbs over what holds it and comes back down after.
     */
    private double liftFor(Vec3 at) {
        Vec3 here = shrieker.position();
        boolean off = here.distanceTo(at.add(0.0, lift, 0.0)) > HELD_WISH;
        if (--askIn <= 0) {
            askIn = HELD_CHECK_TICKS;
            held = askedAt != null && here.distanceTo(askedAt) < HELD_MOVE && off;
            askedAt = here;
        }
        boolean pressed = shrieker.horizontalCollision && Math.hypot(at.x - here.x, at.z - here.z) > HELD_WISH;
        lift = held || pressed ? Math.min(lift + LIFT_STEP, MAX_LIFT) : Math.max(lift - LIFT_STEP, 0.0);
        return lift;
    }

    /**
     * Where on its circle, this far out, it flies now: up from its prey's height a block at a time while its body has
     * room, to {@code height} at most, so a roof over the prey (a cave's, a tree's, a ceiling's) keeps it under the
     * roof, never above it. Where the circle's point has no room even a block up (in a bank, a trunk, a wall), it looks
     * on round the circle for one that has; with none anywhere, it goes for its prey.
     */
    private Vec3 circlePoint(LivingEntity prey, double radius, double height) {
        for (int i = 0; i < SKIP_TRIES; i++) {
            Vec3 at = pointAt(prey, radius, height);
            if (at != null) {
                return at;
            }
            angle += SKIP_STEP;
        }
        return prey.position();
    }

    /** The circle's point at its angle now, or null where its body has no room there even a block up. */
    @Nullable
    private Vec3 pointAt(LivingEntity prey, double radius, double height) {
        double x = prey.getX() + Math.cos(angle) * radius;
        double z = prey.getZ() + Math.sin(angle) * radius;
        double rise = -1.0;
        for (double up = Math.min(1.0, height); hasRoom(x, prey.getY() + up, z); up = Math.min(up + 1.0, height)) {
            rise = up;
            if (up >= height) {
                break;
            }
        }
        return rise < 0.0 ? null : new Vec3(x, prey.getY() + rise, z);
    }

    private boolean hasRoom(double x, double y, double z) {
        return shrieker.level().noCollision(shrieker,
                shrieker.getDimensions(shrieker.getPose()).makeBoundingBox(new Vec3(x, y, z)));
    }
}
