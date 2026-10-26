package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * An Arrancar's Bala, sealed or released: a bullet of its own hardened reiatsu struck out with the fist, far
 * weaker than a Cero but many times quicker to throw (Ch. 234), and crimson for every Arrancar. It is the
 * Technique key (G): a press fires one at once, and holding the key fires again each time the cooldown is
 * over. It strikes the first creature in line it may strike, through the Cero's own engine
 * ({@link CeroBeam#fire} with {@link #KIND}), so it keeps every rule a Cero keeps on PvP, teams, pets and
 * shields; it bursts on nothing, breaks no blocks and knocks back hard. Its damage follows the form the
 * Arrancar rose from and its maximum reiryoku, up to a cap, and a quarter more in Resurrección. It cannot be
 * fired while a Cero gathers, as the hand is busy with it.
 *
 * <p>The keys reach here through {@link ArrancarEvents#onKey}, and {@link ArrancarEvents#tick} keeps a held
 * key firing. The cooldown and the hold are transients in the soul, so nothing is saved. Everything takes a
 * {@link Player}, so game tests can use mock players; only the milestone needs a real one.
 */
public final class Bala {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("bala"));
    /** The crimson of every Arrancar's Bala. */
    public static final int COLOUR = 0xB0101A;
    /** Ticks the shot's trace is seen. */
    public static final int TRACE_TICKS = 4;
    /** A quiet shot that stops at the first creature it may strike. */
    public static final CeroBeam.Kind KIND = new CeroBeam.Kind(DAMAGE, true, 1);

    /** The least reiryoku a Bala may leave, as for Shunpo. */
    private static final float MIN_LEFT = 0.5F;

    /** What came of asking for a Bala. Those with a {@link #messageKey} are told to the player. */
    public enum Result {
        FIRED(null), UNABLE(null), COOLDOWN(null),
        GATHERING("message.bleach.bala.gathering"), TOO_WEAK("message.bleach.bala.too_weak");

        @Nullable
        private final String messageKey;

        Result(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        /** The message told for this, or null for one told nothing. */
        @Nullable
        public String messageKey() {
            return messageKey;
        }
    }

    private Bala() {
    }

    // ---- The numbers ----

    /**
     * A Bala's damage: its origin's own and more for each square root of maximum reiryoku, up to the most;
     * then a quarter more in Resurrección, so a released Bala is the one that can pass the cap.
     */
    public static float damage(SoulData data) {
        double damage = BleachConfig.get(ArrancarLogic.numbers(ArrancarLogic.origin(data)).balaDamage())
                + BleachConfig.get(BleachConfig.BALA_DAMAGE_PER_ROOT) * Math.sqrt(Math.max(0.0F, data.maxReiryoku()));
        return (float) (Math.min(BleachConfig.get(BleachConfig.BALA_MAX_DAMAGE), damage) * releasedMultiplier(data));
    }

    /** The reiryoku a Bala costs: a share of the maximum, but never less than the least cost. */
    public static float cost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.BALA_MIN_COST),
                BleachConfig.get(BleachConfig.BALA_COST_FRACTION) * data.maxReiryoku());
    }

    /**
     * The shot as the engine takes it: its damage, reach and width, no blast and no blocks, the knockback
     * along it (a quarter more in Resurrección), its crimson, and nothing to gather or lock.
     */
    public static CeroBeam.Spec spec(SoulData data) {
        float knockback = (float) (BleachConfig.get(BleachConfig.BALA_KNOCKBACK) * releasedMultiplier(data));
        return new CeroBeam.Spec(damage(data), BleachConfig.get(BleachConfig.BALA_RANGE),
                (float) BleachConfig.get(BleachConfig.BALA_RADIUS), 0.0F, 0.0F, 0, knockback, COLOUR, 1, 0, false);
    }

    // ---- The key ----

    /**
     * The Technique key went down: the key is held from now, and a Bala fires at once if it may. A refusal
     * with a message is told and lets go of the key, as does one that can never fire; the cooldown keeps the
     * hold, so the next shot goes when it is over.
     */
    public static Result press(Player player, SoulData data, long now) {
        data.balaHeld = true;
        Result result = fire(player, data, now);
        settle(player, data, result);
        return result;
    }

    /** The Technique key came up, or the client let go of it (a screen or another hotbar slot): no more shots. */
    public static void release(SoulData data) {
        data.balaHeld = false;
    }

    /**
     * One body tick of an Arrancar, from {@link ArrancarEvents#tick}: while the key is held, a Bala fires each
     * time the cooldown is over. A soul that died, watches as a spectator or is no longer an Arrancar lets go,
     * and so does a refusal, told once, so a held key does not repeat it.
     */
    public static void tick(Player player, SoulData data, long now) {
        if (!data.balaHeld) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !ArrancarLogic.isArrancar(data)) {
            data.balaHeld = false;
            return;
        }
        if (now - data.lastBalaAt >= cooldownTicks()) {
            settle(player, data, fire(player, data, now));
        }
    }

    /** Whether the Technique key is held for Bala, so it goes on firing. */
    public static boolean held(SoulData data) {
        return data.balaHeld;
    }

    // ---- The shot ----

    /**
     * Fires a Bala where the Arrancar looks, from just ahead of its eyes, the first check that fails winning:
     * one that cannot fire at all (not an Arrancar, dead, a spectator or not in a server world), the cooldown,
     * a Cero gathering, and enough reiryoku. It is paid for and the cooldown starts; the shot strikes, its trace
     * is drawn, the main hand swings for the thrown fist and the crack is heard at the muzzle.
     */
    public static Result fire(Player player, SoulData data, long now) {
        if (!ArrancarLogic.isArrancar(data) || !player.isAlive() || player.isSpectator()
                || !(player.level() instanceof ServerLevel level)) {
            return Result.UNABLE;
        }
        if (now - data.lastBalaAt < cooldownTicks()) {
            return Result.COOLDOWN;
        }
        if (HollowCero.charging(data)) {
            return Result.GATHERING;
        }
        float cost = cost(data);
        if (data.reiryoku() - cost < MIN_LEFT || !SoulLogic.trySpend(player, data, cost)) {
            return Result.TOO_WEAK;
        }
        data.lastBalaAt = now;
        CeroBeam.Spec spec = spec(data);
        Vec3 from = CeroBeam.muzzleOf(player, 1.0F);
        Vec3 aim = CeroBeam.aimOf(player);
        // The shot, not the hand, is what a shield meets: with the Arrancar itself as the direct entity, vanilla
        // would take a blocked Bala for a blow from its hand (an axe held would disable the shield from afar, and
        // a Guardian's spikes would answer it). A marker at the muzzle, never added to the world, stands in.
        Marker bullet = new Marker(EntityType.MARKER, level);
        bullet.setPos(from);
        CeroBeam.Shot shot = CeroBeam.fire(level, player, bullet, from, aim != null ? aim : player.getLookAngle(), spec,
                KIND);
        CeroBeam.trace(level, player, shot, spec.radius(), COLOUR, TRACE_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
        SpiritSenses.play(level, from.x, from.y, from.z, ModSounds.BALA.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (player instanceof ServerPlayer server) {
            Milestones.grant(server, Milestones.BALA);
        }
        return Result.FIRED;
    }

    // ---- Helpers ----

    private static int cooldownTicks() {
        return BleachConfig.get(BleachConfig.BALA_COOLDOWN_TICKS);
    }

    /** What damage and knockback are multiplied by: more in Resurrección, after the cap. */
    private static double releasedMultiplier(SoulData data) {
        return ArrancarLogic.released(data) ? BleachConfig.get(BleachConfig.BALA_RELEASED_MULTIPLIER) : 1.0;
    }

    /** After a try: a shot or the cooldown keeps the key held; anything else lets go, told if it has a message. */
    private static void settle(Player player, SoulData data, Result result) {
        if (result == Result.FIRED || result == Result.COOLDOWN) {
            return;
        }
        data.balaHeld = false;
        if (result.messageKey() != null) {
            player.displayClientMessage(Component.translatable(result.messageKey()).withStyle(ChatFormatting.GRAY), true);
        }
    }
}
