package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.TrialBlade;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Zangetsu's test in a Bankai trial (a file's {@code bankai.test} {@code "swords"}): as the bout begins, blades rise from
 * open ground in its ring ({@link #ground}), one of them true, picked at random, and go with the bout. Server thread only.
 *
 * <p>All look alike, and nothing in the world shows which is true: the server alone knows ({@link #real}). The soul
 * takes one up by using it ({@link #takeUp}), one at a time, and nothing reaches its spirit but a blow of its own hand
 * with a blade taken up ({@link #lands}): a false one shatters at that first clash and the blow is lost; the true one
 * holds, the blow lands, every other blade crumbles, and from then on every blow of the soul's lands.
 */
public final class BankaiSwords {
    /** How near the centre no blade rises, so none stands at the soul's feet, in blocks. */
    private static final double CLEAR = 1.5;
    /** How far a blade's spot may stand above and below the soul's feet, in blocks: a step up, two down. */
    private static final int UP = 1, DOWN = 2;
    /** How far above its feet the soul's eyes are taken to be as it looks for open ground, in blocks. */
    private static final double EYES = 1.5;
    /** The blades of bouts ended as their spirits left the world, which go at the end of the server tick. */
    static final List<TrialBlade> PENDING = new ArrayList<>();
    private static final String TAKEN = "message.bleach.bankai.trial.blade_taken";
    private static final String SHATTERED = "message.bleach.bankai.trial.blade_shattered";
    private static final String FOUND = "message.bleach.bankai.trial.blade_found";
    private static final String UNTOUCHED = "message.bleach.bankai.trial.untouched";

    private final List<TrialBlade> blades;
    private final TrialBlade real;
    /** The blade its soul has taken up, if any; and whether it has found the true one. */
    @Nullable
    private TrialBlade held;
    private boolean found;

    private BankaiSwords(List<TrialBlade> blades, TrialBlade real) {
        this.blades = blades;
        this.real = real;
    }

    /**
     * Where in a ring of this radius a blade may rise, in a random order: one a block, a block inside the ring and 1.5
     * from the centre or more, on open footing ({@link #footing}). Only where the world ticks entities, so each blade ticks.
     */
    public static List<Vec3> ground(ServerLevel level, Player wielder, Vec3 centre, int radius) {
        List<Vec3> spots = new ArrayList<>();
        BlockPos feet = BlockPos.containing(centre);
        Vec3 eyes = centre.add(0.0, EYES, 0.0);
        int reach = radius - 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                BlockPos column = feet.offset(dx, 0, dz);
                double away = Math.hypot(column.getX() + 0.5 - centre.x, column.getZ() + 0.5 - centre.z);
                Vec3 spot = away > reach || away < CLEAR ? null : footing(level, wielder, eyes, column);
                if (spot != null) {
                    spots.add(spot);
                }
            }
        }
        Util.shuffle(spots, level.random);
        return spots;
    }

    /**
     * The highest footing in this column a step up to two down from the centre's feet, on a sturdy top with open room
     * above for a blade, if the eyes there see it, so none rises in a wall, behind one, over a drop or on a roof; or null.
     */
    @Nullable
    private static Vec3 footing(ServerLevel level, Player wielder, Vec3 eyes, BlockPos column) {
        if (!level.isPositionEntityTicking(column)) {
            return null;
        }
        for (int up = UP; up >= -DOWN; up--) {
            BlockPos at = column.above(up);
            BlockPos below = at.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && open(level, at)
                    && open(level, at.above())) {
                Vec3 spot = Vec3.atBottomCenterOf(at);
                ClipContext sight = new ClipContext(eyes, spot.add(0.0, 0.5, 0.0), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, wielder);
                return SpiritClip.clip(level, sight).block().getType() == HitResult.Type.MISS ? spot : null;
            }
        }
        return null;
    }

    /**
     * Whether a blade may stand in this block: air, or what a placed block would replace, with no fluid and nothing to
     * bump into, so none stands in a portal, in water or on a rail, a plate or a torch.
     */
    private static boolean open(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty()
                && state.getCollisionShape(level, at).isEmpty();
    }

    /**
     * Raises blades at the first {@code count} of these spots, each turned its own way, in this look, and picks the true
     * one among them at random; null if none could stand, so the bout is a plain one rather than one no blow can win.
     */
    @Nullable
    static BankaiSwords raise(ServerLevel level, Player wielder, List<Vec3> spots, int count, int model) {
        List<TrialBlade> blades = new ArrayList<>();
        for (Vec3 spot : spots.subList(0, Math.min(count, spots.size()))) {
            TrialBlade blade = TrialBlade.at(level, spot, level.random.nextFloat() * 360.0F, wielder.getUUID(), model);
            if (level.addFreshEntity(blade)) {
                blades.add(blade);
            }
        }
        return blades.isEmpty() ? null : new BankaiSwords(blades, blades.get(level.random.nextInt(blades.size())));
    }

    /** Whether this blade still stands in a bout under way: false once its bout is over, or once it has gone from it. */
    public static boolean stands(TrialBlade blade) {
        BankaiTrial.Trial trial = BankaiTrial.trial(blade.wielderId());
        return trial != null && trial.swords() != null && trial.swords().blades.contains(blade);
    }

    /**
     * The bout is over, and off the table: its blades go now; or, as its spirit leaves the world, perhaps while an unload
     * walks its chunk's entities, at the end of this server tick, when nothing moves the world ({@link #onServerTick}).
     */
    void clear(boolean leaving) {
        if (leaving) {
            PENDING.addAll(blades);
        } else {
            blades.forEach(Entity::discard);
        }
    }

    /** At the end of each server tick the blades of bouts ended as their spirits left the world go. */
    static void onServerTick(ServerTickEvent.Post event) {
        PENDING.forEach(Entity::discard);
        PENDING.clear();
    }

    /** The blades still in the bout, on the ground or held. */
    public List<TrialBlade> blades() {
        return Collections.unmodifiableList(blades);
    }

    /** The true blade, which nothing in the world marks: for the tests. */
    public TrialBlade real() {
        return real;
    }

    @Nullable
    public TrialBlade held() {
        return held;
    }

    /**
     * The soul takes up this blade of its own bout, still on the ground, by using it: the one it held goes back to its
     * spot, so it holds one at a time. False for anyone else's use, a spectator's, or a blade of no bout of the user's.
     */
    public static boolean takeUp(Player player, TrialBlade blade) {
        BankaiTrial.Trial trial = BankaiTrial.trial(player.getUUID());
        BankaiSwords swords = trial == null || trial.wielder() != player ? null : trial.swords();
        if (swords == null || player.isSpectator() || blade.held() || !swords.blades.contains(blade)) {
            return false;
        }
        if (swords.held != null) {
            swords.held.setHeld(false);
        }
        swords.held = blade;
        blade.setHeld(true);
        tell(player, TAKEN);
        return true;
    }

    /**
     * Whether this blow of its soul's reaches the spirit: every blow once the true blade is found, or once it has gone
     * with its chunk, so no unload leaves a spirit none can beat, and one that passes every guard with the soul as its
     * attacker ({@code /damage} by it; {@code /kill} names none and never comes here); until then, only a blow of the
     * soul's own hand with a blade taken up, its first clash. A false blade shatters and the
     * blow is lost; the true one holds, the blow lands, and the rest crumble away. A shot, a wave or any other blow from
     * afar breaks nothing and lands nothing.
     */
    boolean lands(Player wielder, DamageSource source) {
        if (found || real.isRemoved() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        TrialBlade blade = held;
        if (blade == null || source.getDirectEntity() != wielder) {
            tell(wielder, UNTOUCHED);
            return false;
        }
        if (blade != real) {
            held = null;
            blades.remove(blade);
            gone(blade, true);
            tell(wielder, SHATTERED);
            return false;
        }
        found = true;
        for (TrialBlade other : List.copyOf(blades)) {
            if (other != real) {
                blades.remove(other);
                gone(other, false);
            }
        }
        tell(wielder, FOUND);
        return true;
    }

    /** A blade shatters, as it breaks on its spirit with a sound, or crumbles away with a puff; both only to the aware. */
    private static void gone(TrialBlade blade, boolean shattered) {
        if (blade.level() instanceof ServerLevel level) {
            double y = blade.getY() + TrialBlade.HEIGHT / 2.0;
            SpiritSenses.particles(level, shattered ? ParticleTypes.CRIT : ParticleTypes.POOF, blade.getX(), y,
                    blade.getZ(), shattered ? 12 : 4, 0.1, 0.3, 0.1, shattered ? 0.3 : 0.02);
            if (shattered) {
                SpiritSenses.play(level, blade.getX(), y, blade.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS,
                        1.0F, 0.8F, false);
            }
        }
        blade.discard();
    }

    /** A line to the soul's action bar, in grey, so blow after blow overwrites it rather than filling the chat. */
    private static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }
}
