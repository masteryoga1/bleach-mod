package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.joml.Vector3f;

/**
 * An Arrancar's Resurrección on the server. Holding its own sealed blade, it calls out the release, the
 * blade's command and name, and takes back the Hollow it sealed away: the Hollow's skin and full mask, the
 * claws of the form it rose from (its {@link Hierro} stands in for the hide), more health, speed and melee
 * damage by that form (see {@link ArrancarLogic}), a full heal and a stronger Cero, but not a Hollow's fast
 * healing. The release drains reiryoku every tick, and reiryoku does not refill while it lasts (see
 * {@link SoulEvents#mayRegenerate}).
 *
 * <p>It ends on the seal key, when the reiryoku runs low, on death, in bed, in spectator mode, on logging
 * out and on any respawn, and when the soul stops being an Arrancar ({@link ArrancarEvents#reconcile}); a
 * dimension change keeps it, as the player and its transients carry over. Every end goes through
 * {@link #end}, which starts the cooldown. The release itself is a transient, so it is never saved, but the
 * cooldown is a game time in the soul's {@link HollowProgress.Arrancar} record and outlasts a relog.
 * Everything takes a {@link Player}, so game tests can use mock players; the keys reach here through
 * {@link ArrancarEvents#onKey}.
 */
public final class ResurreccionEvents {
    /** The message of a release the soul's reiryoku could no longer hold. */
    public static final String EXHAUSTED = "message.bleach.resurreccion.exhausted";
    /** The message of a release sealed on purpose. */
    public static final String ENDED = "message.bleach.resurreccion.ended";

    private static final int RELEASE_DUST = 60;
    private static final float RELEASE_DUST_SIZE = 1.2F;
    private static final int RELEASE_SOULS = 20;

    /** What came of asking to release or seal. Every one but {@link #RELEASED} and {@link #SEALED} is told to the player. */
    public enum Result {
        RELEASED, SEALED, NO_BLADE, ALREADY, COOLDOWN, TOO_WEAK, NOT_RELEASED;

        /** {@code message.bleach.resurreccion.<name>}. */
        public String messageKey() {
            return "message.bleach.resurreccion." + name().toLowerCase(Locale.ROOT);
        }
    }

    private ResurreccionEvents() {
    }

    /** Ends a release on death and on logging out, and keeps its cooldown through a respawn that is no death. */
    public static void register(IEventBus bus) {
        bus.addListener(ResurreccionEvents::onDeath);
        bus.addListener(ResurreccionEvents::onLoggedOut);
        bus.addListener(ResurreccionEvents::onClone);
    }

    /**
     * Calls out the release of the Arrancar's own blade in hand. The first check that fails wins: the blade
     * (a soul that is no longer an Arrancar holds none of its own, as its old blade is about to crumble),
     * a release already on, the cooldown, and enough reiryoku. On success it pays the cost, frees the
     * Hollow form and heals in full; the claws grow on the next tick, on a bare hand.
     */
    public static Result release(Player player, SoulData data, long now) {
        if (!ArrancarLogic.isArrancar(data) || !ArrancarLogic.isOwnBlade(player.getMainHandItem(), player)) {
            return Result.NO_BLADE;
        }
        if (data.resurreccionActive) {
            return Result.ALREADY;
        }
        if (now < data.hollow().arrancar().releaseReadyAt()) {
            return Result.COOLDOWN;
        }
        double max = data.maxReiryoku();
        if (data.reiryoku() < BleachConfig.get(BleachConfig.RESURRECCION_MIN_FRACTION) * max) {
            return Result.TOO_WEAK;
        }
        float cost = (float) (BleachConfig.get(BleachConfig.RESURRECCION_COST_FRACTION) * max);
        if (!SoulLogic.trySpend(player, data, cost)) {
            return Result.TOO_WEAK;
        }
        data.resurreccionActive = true;
        data.resurreccionStartedAt = now;
        // The released health goes on first, so the heal fills it.
        HollowBody.reconcile(player, data);
        player.setHealth(player.getMaxHealth());

        ArrancarLogic.Blade blade = ArrancarLogic.blade(player.getUUID());
        ShikaiEvents.speak(player, Component.translatable("speech.bleach.release", blade.command(), blade.displayName()));
        if (player.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, dust(blade.colour()), player.getX(), player.getY(0.5), player.getZ(),
                    RELEASE_DUST, 0.5, 0.8, 0.5, 0.05);
            SpiritSenses.particles(level, ParticleTypes.SCULK_SOUL, player.getX(), player.getY(0.5), player.getZ(),
                    RELEASE_SOULS, player.getBbWidth() / 2.0, player.getBbHeight() / 2.0, player.getBbWidth() / 2.0, 0.02);
        }
        SpiritSenses.play(player, ModSounds.ZANPAKUTO_RELEASE.get(), SoundSource.PLAYERS, 1.0F, 0.7F, false);
        SpiritSenses.play(player, ModSounds.MASK_CRACK.get(), SoundSource.PLAYERS, 1.0F, 0.8F, false);
        player.displayClientMessage(Component.translatable(Result.RELEASED.messageKey(), blade.displayName()), true);
        if (player instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.RESURRECCION);
        }
        return Result.RELEASED;
    }

    /** Seals the released Hollow power back into the blade, wherever the blade is. */
    public static Result seal(Player player, SoulData data, long now) {
        if (!data.resurreccionActive) {
            return Result.NOT_RELEASED;
        }
        end(player, data, now, ENDED);
        return Result.SEALED;
    }

    /**
     * Ends the release, if there is one, however it ends: the cooldown starts from now and is saved, the
     * released body comes off and the sealed health comes back. Health above the sealed maximum is cut
     * down by vanilla on the player's next tick. The seal is heard, and the message, if any, shown above
     * the hotbar. Asks only whether a release is on, not whether the soul is still an Arrancar, so a
     * release left over on a soul that has become something else ends here too.
     */
    public static void end(Player player, SoulData data, long now, @Nullable String messageKey) {
        if (!data.resurreccionActive) {
            return;
        }
        data.resurreccionActive = false;
        data.resurreccionStartedAt = HollowProgress.NEVER;
        startCooldown(data, now);
        HollowBody.reconcile(player, data);
        SpiritSenses.play(player, ModSounds.ZANPAKUTO_SEAL.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Tells the player what came of a release or a seal, above the hotbar; a cooldown with the seconds left. */
    public static void tell(Player player, SoulData data, Result result, long now) {
        Component message = switch (result) {
            case RELEASED -> Component.translatable(result.messageKey(),
                    ArrancarLogic.blade(player.getUUID()).displayName());
            case COOLDOWN -> Component.translatable(result.messageKey(), secondsToReady(data, now))
                    .withStyle(ChatFormatting.GRAY);
            default -> Component.translatable(result.messageKey()).withStyle(ChatFormatting.GRAY);
        };
        player.displayClientMessage(message, true);
    }

    /**
     * One tick of a release, from {@link ArrancarEvents#tick}: it ends at once for a dead, spectating or
     * sleeping soul (or one no longer an Arrancar), and otherwise drains its share of reiryoku, ending when
     * that runs dry or falls below the end share. While it lasts the claws grow on a bare hand: this runs in
     * the body's tick, the one place they may. There is no Hollow regeneration to run. Under a frozen tick
     * rate the world's time stands still, and so does the drain.
     */
    static void upkeep(Player player, SoulData data, long now) {
        if (!data.resurreccionActive) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || !ArrancarLogic.isArrancar(data)) {
            end(player, data, now, null);
            return;
        }
        if (player.level().tickRateManager().runsNormally()) {
            double floor = BleachConfig.get(BleachConfig.RESURRECCION_END_FRACTION) * data.maxReiryoku();
            if (!SoulLogic.drain(player, data, ArrancarLogic.releaseDrainPerTick(data)) || data.reiryoku() < floor) {
                end(player, data, now, EXHAUSTED);
                return;
            }
        }
        HollowBody.reconcile(player, data, true);
    }

    // ---- Listeners ----

    /** A soul that dies ends its release first, so the cooldown is saved and the respawned soul starts sealed. */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            end(player, SoulLogic.get(player), player.level().getGameTime(), null);
        }
    }

    /** A release does not outlast the session. This runs before the player is saved, so the cooldown is. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        end(player, SoulLogic.get(player), player.level().getGameTime(), null);
    }

    /**
     * Leaving the End builds the player anew, and no transient comes with it, so a release ends there with
     * no death. The cooldown is written on both copies of the soul: NeoForge copies the soul from the old
     * player to the new one in a listener of its own, which may run before this one or after it.
     */
    private static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        Player original = event.getOriginal();
        SoulData old = SoulLogic.get(original);
        if (!old.resurreccionActive) {
            return;
        }
        long now = original.level().getGameTime();
        old.resurreccionActive = false;
        old.resurreccionStartedAt = HollowProgress.NEVER;
        startCooldown(old, now);
        startCooldown(SoulLogic.get(event.getEntity()), now);
    }

    // ---- Helpers ----

    /** The cooldown from now, in the soul's saved Arrancar record. */
    private static void startCooldown(SoulData data, long now) {
        HollowProgress hollow = data.hollow();
        long cooldown = BleachConfig.get(BleachConfig.RESURRECCION_COOLDOWN_SECONDS) * 20L;
        data.setHollow(hollow.withArrancar(hollow.arrancar().withReleaseReadyAt(now + cooldown)));
    }

    /** Whole seconds, rounded up, until the next release may be called out. */
    private static long secondsToReady(SoulData data, long now) {
        return Math.max(1L, (long) Math.ceil((data.hollow().arrancar().releaseReadyAt() - now) / 20.0));
    }

    private static DustParticleOptions dust(int colour) {
        return new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F), RELEASE_DUST_SIZE);
    }
}
