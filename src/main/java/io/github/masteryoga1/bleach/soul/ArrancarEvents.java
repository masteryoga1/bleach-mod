package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.joml.Vector3f;

/**
 * The Arrancar on the server: a Menos tearing off its own mask, the soul it makes, and that soul's body and
 * blade kept right. An Adjuchas or Vasto Lorde presses Release Zanpakutō twice to begin, holds still while it
 * tears, and Seal lets go; when the channel is done it pays half its maximum reiryoku and the mask comes away
 * or it fails, at a cost (see {@link ArrancarLogic#tearChance}). An Arrancar's Release and Seal call out and
 * seal its Resurrección instead ({@link ResurreccionEvents}), and its Technique key fires a {@link Bala}; a
 * Hollow's Technique key calls down {@link Negacion} as a Gillian. The rules and numbers are in
 * {@link ArrancarLogic}.
 *
 * <p>It registers no listener: {@link HollowBody#tickBody} drives the tear, the Arrancar's own tick and the
 * check every second, and death, logging out and cloning belong to {@link ResurreccionEvents}. The tear is
 * transient, so a relog lets go of it for free; only its failures and the time of the last try are saved, in
 * the soul's {@link HollowProgress.Arrancar} record. Everything takes a {@link Player}, so game tests can use
 * mock players; only messages, sounds and particles need a real one.
 */
public final class ArrancarEvents {
    /** Ticks after a first press of Release Zanpakutō in which a second one begins the tear. */
    public static final int ARM_TICKS = 100;

    private static final long NEVER = HollowProgress.NEVER;
    private static final String RELEASE_KEY = "key.bleach.zanpakuto_release";
    private static final String TEAR = "message.bleach.arrancar.tear.";
    private static final String BECAME = "message.bleach.arrancar.became";
    private static final String REFORMED = "message.bleach.arrancar.blade.reformed";
    private static final String CRUMBLED = "message.bleach.arrancar.blade.crumbled";

    /** Ticks between two pulses of a tear, and how long each pulse's slowness and crack last: past the next. */
    private static final int PULSE_TICKS = 20;
    private static final int PULSE_HOLD_TICKS = 25;
    private static final int TEAR_SLOWNESS_AMPLIFIER = 3;
    /** Ticks between two puffs of souls at the head while it tears, and how many. */
    private static final int SOUL_PUFF_TICKS = 10;
    private static final int SOUL_PUFF = 6;
    /** A channel this far past its end was not ticked to the end: the soul left the Hollow branch and came back. */
    private static final int STALE_TICKS = 20;
    /** How far the soul may drift from where it began, squared: a step of more than a block, or a jump, breaks it. */
    private static final double STILL_DISTANCE_SQR = 1.0;
    /** How often {@link #reconcileBlades} runs, so how much the blade-less clock gains each time. */
    private static final int BLADE_CHECK_TICKS = 20;
    private static final int BECOME_SOULS = 40;
    private static final int BECOME_DUST = 30;
    private static final float BECOME_DUST_SIZE = 1.0F;

    private ArrancarEvents() {
    }

    // ---- The keys ----

    /**
     * Release Zanpakutō, Seal and the Technique key, for a Hollow or an Arrancar (from ShikaiEvents). An Arrancar
     * calls out its Resurrección and seals it, told what came of it unless it worked, and fires its Bala on the
     * Technique key for as long as it holds it ({@link Bala}); a Hollow tears at its mask and lets go, and its
     * Technique key calls down Negación, which only a Gillian can ({@link Negacion#press}).
     */
    public static void onKey(Player player, SoulData data, ZanpakutoKey key, long now) {
        if (ArrancarLogic.isArrancar(data)) {
            switch (key) {
                case RELEASE -> {
                    ResurreccionEvents.Result result = ResurreccionEvents.release(player, data, now);
                    if (result != ResurreccionEvents.Result.RELEASED) {
                        ResurreccionEvents.tell(player, data, result, now);
                    }
                }
                case SEAL -> {
                    ResurreccionEvents.Result result = ResurreccionEvents.seal(player, data, now);
                    if (result != ResurreccionEvents.Result.SEALED) {
                        ResurreccionEvents.tell(player, data, result, now);
                    }
                }
                case TECHNIQUE_PRESS -> Bala.press(player, data, now);
                case TECHNIQUE_RELEASE, TECHNIQUE_CANCEL -> Bala.release(data);
                default -> {
                }
            }
            return;
        }
        if (HollowPlayerLogic.isHollow(data)) {
            switch (key) {
                case RELEASE -> pressTear(player, data, now);
                case SEAL -> cancelTear(player, data);
                case TECHNIQUE_PRESS -> Negacion.press(player, data, now);
                default -> {
                }
            }
        }
    }

    // ---- The tear ----

    /**
     * A press of Release Zanpakutō by a Hollow. The first arms the tear and warns what it means, naming the key;
     * a second within {@link #ARM_TICKS} begins it where the soul stands. Each press asks the refusals of
     * {@link ArrancarLogic#tearRefusal} first, and a refused one is told and disarms. A press while tearing does
     * nothing, and one after the arm has run out arms again.
     *
     * @return why it may not tear now, or null if the press armed or began the tear (or it was already tearing)
     */
    @Nullable
    public static ArrancarLogic.TearRefusal pressTear(Player player, SoulData data, long now) {
        if (tearing(data)) {
            return null;
        }
        ArrancarLogic.TearRefusal refusal = ArrancarLogic.tearRefusal(data, now);
        if (refusal != null) {
            clearTear(data);
            player.displayClientMessage(refusalMessage(refusal, data, now), true);
            return refusal;
        }
        if (tearArmed(data, now)) {
            data.tearArmedAt = NEVER;
            data.tearStartedAt = now;
            data.tearFrom = player.position();
            player.displayClientMessage(Component.translatable(TEAR + "started").withStyle(ChatFormatting.DARK_RED), true);
            return null;
        }
        data.tearArmedAt = now;
        player.displayClientMessage(Component.translatable(TEAR + "armed", Component.keybind(RELEASE_KEY))
                .withStyle(ChatFormatting.DARK_RED), true);
        return null;
    }

    /**
     * Seal, by a Hollow: lets go of an armed tear or one under way, for free, and says so if there was one.
     *
     * @return whether there was one
     */
    public static boolean cancelTear(Player player, SoulData data) {
        boolean had = tearing(data) || tearArmed(data, player.level().getGameTime());
        clearTear(data);
        if (had) {
            player.displayClientMessage(Component.translatable(TEAR + "cancelled").withStyle(ChatFormatting.GRAY), true);
        }
        return had;
    }

    /** Forgets an armed tear and one under way, telling nothing. */
    public static void clearTear(SoulData data) {
        data.tearArmedAt = NEVER;
        data.tearStartedAt = NEVER;
        data.tearFrom = null;
    }

    /** Whether this soul is tearing at its mask now. */
    public static boolean tearing(SoulData data) {
        return data.tearStartedAt != NEVER;
    }

    /** Whether a first press has armed the tear, so a second now would begin it. */
    public static boolean tearArmed(SoulData data, long now) {
        return data.tearArmedAt != NEVER && now - data.tearArmedAt <= ARM_TICKS;
    }

    /**
     * One body tick of a Hollow tearing at its mask, after its Cero's (see {@link HollowBody#tickBody}). Its one
     * clock is the time since the tear began. It is let go of for free when the soul dies or watches as a
     * spectator, devours, gathers a Cero, is no longer an Adjuchas or Vasto Lorde, is hurt after it began, or
     * drifts more than a block from where it began (a step or a jump); a channel left far past its end is
     * forgotten quietly. Every second the mask cracks louder and the soul is held down, every half second souls
     * rise from its head, and at the end of the channel the mask comes away or it fails ({@link #finishTear}).
     * Under a frozen tick rate the clock stands still, so nothing repeats.
     *
     * @return whether the soul has just become an Arrancar, so the rest of the Hollow's tick must not run
     */
    public static boolean tickTear(Player player, SoulData data, long now) {
        if (!tearing(data)) {
            return false;
        }
        long elapsed = now - data.tearStartedAt;
        long channel = channelTicks();
        if (elapsed < 0L || elapsed > channel + STALE_TICKS) {
            clearTear(data);
            return false;
        }
        if (breaks(player, data)) {
            clearTear(data);
            if (player.isAlive()) {
                player.displayClientMessage(Component.translatable(TEAR + "broken").withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        if (!player.level().tickRateManager().runsNormally()) {
            return false;
        }
        if (elapsed % PULSE_TICKS == 0L) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PULSE_HOLD_TICKS, TEAR_SLOWNESS_AMPLIFIER,
                    false, false, true));
            data.hollowCrackedUntil = Math.max(data.hollowCrackedUntil, now + PULSE_HOLD_TICKS);
            float pitch = 0.6F + 0.4F * (channel > 0L ? (float) elapsed / channel : 1.0F);
            SpiritSenses.play(player, ModSounds.MASK_CRACK.get(), SoundSource.PLAYERS, 1.0F, pitch, false);
        }
        if (elapsed % SOUL_PUFF_TICKS == 0L && player.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, ParticleTypes.SCULK_SOUL, player.getX(), player.getEyeY(), player.getZ(),
                    SOUL_PUFF, 0.25, 0.15, 0.25, 0.02);
        }
        if (elapsed >= channel) {
            return finishTear(player, data, now, player.getRandom().nextDouble());
        }
        return false;
    }

    /**
     * The end of a tear's channel: half the maximum reiryoku is paid (never below the reserve), and the mask
     * comes away if the roll is under the chance, making an Arrancar of the form the soul is. Otherwise the try
     * is counted (each one makes the next likelier) and the wait for the next begins; it loses a share of its
     * health (never the last of it), and its mask is cracked and its healing stopped for a while.
     *
     * @param roll a number from 0 to 1; the mask comes away below the chance
     * @return whether the soul has become an Arrancar
     */
    public static boolean finishTear(Player player, SoulData data, long now, double roll) {
        clearTear(data);
        float cost = (float) (BleachConfig.get(BleachConfig.TEAR_COST_FRACTION) * data.maxReiryoku());
        // Never below the reserve, and never more than the soul has.
        data.setReiryoku(Math.min(data.reiryoku(), Math.max(ZanpakutoLogic.RESERVE, data.reiryoku() - cost)));
        HollowTier form = HollowTier.of(data);
        HollowProgress.Arrancar record = data.hollow().arrancar();
        if (roll < ArrancarLogic.tearChance(form, record.failures())) {
            become(player, data, form, false);
            return true;
        }
        data.setHollow(data.hollow().withArrancar(record.withFailures(record.failures() + 1).withLastTearAt(now)));
        float health = player.getHealth();
        float cut = (float) (BleachConfig.get(BleachConfig.TEAR_FAILURE_HEALTH_FRACTION) * player.getMaxHealth());
        player.setHealth(Math.min(health, Math.max(1.0F, health - cut)));
        long pause = BleachConfig.get(BleachConfig.TEAR_FAILURE_PAUSE_SECONDS) * 20L;
        data.hollowCrackedUntil = now + pause;
        data.hollowRegenPausedUntil = Math.max(data.hollowRegenPausedUntil, now + pause);
        SpiritSenses.play(player, ModSounds.HOLLOW_HURT.get(), SoundSource.PLAYERS, 1.0F, 0.7F, false);
        SpiritSenses.play(player, ModSounds.MASK_CRACK.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        player.displayClientMessage(Component.translatable(TEAR + "failed").withStyle(ChatFormatting.DARK_RED), false);
        return false;
    }

    // ---- Becoming ----

    /**
     * Makes this soul an Arrancar risen from this form (a Gillian at least), whatever it was: a Hollow whose
     * mask came away, the command, or later the Hōgyoku ({@code perfected}). Whatever it was doing as a Hollow
     * stops, and its Hollow body and hunger go, food back. Its Hollow tier becomes the form it rose from, the
     * record counts a new forging of its blade, and the first time it gains a share of maximum reiryoku by that
     * form; its reiryoku and health fill. The blade goes into an empty main hand, or else the inventory, and
     * any older copy crumbles within a second. A real player is then numbered on the server's roster at once
     * ({@link ArrancarRoster#keep}), and told its Número, or its seat if it is already strong enough for one.
     */
    public static void become(Player player, SoulData data, HollowTier origin, boolean perfected) {
        long now = player.level().getGameTime();
        HollowCero.cancel(player, data);
        DevourEvents.stopDevour(player, data, null, now);
        DevourEvents.stopGnaw(data);
        HollowBody.leaveBodyIfWorn(player, data);
        clearTear(data);
        // A crack from the tear or an earlier failure is not drawn on the new fragment.
        data.hollowCrackedUntil = NEVER;
        data.hollowRegenPausedUntil = NEVER;
        data.resurreccionActive = false;
        data.resurreccionStartedAt = NEVER;

        HollowTier born = origin.isMenos() ? origin : HollowTier.GILLIAN;
        data.setTier(PowerPath.HOLLOW, born.tier());
        SoulLogic.changeState(data, SoulState.ARRANCAR);
        HollowProgress.Arrancar before = data.hollow().arrancar();
        data.setHollow(data.hollow().withArrancar(before.becoming(perfected)));
        if (!before.forged()) {
            SoulLogic.grow(data, data.maxReiryoku() * ArrancarLogic.reiryokuGrowth(born));
        }
        data.setReiryoku(data.maxReiryoku());

        data.arrancarBladelessTicks = 0;
        ItemStack blade = forgeBlade(player, data);
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        } else {
            player.getInventory().placeItemBackInInventory(blade);
        }
        // The sealed health goes on first, so the heal fills it.
        HollowBody.reconcile(player, data);
        player.setHealth(player.getMaxHealth());

        if (player instanceof ServerPlayer serverPlayer) {
            ArrancarLogic.Blade kind = ArrancarLogic.blade(player.getUUID());
            serverPlayer.sendSystemMessage(Component.translatable(BECAME, kind.displayName())
                    .withStyle(ChatFormatting.WHITE));
            ArrancarRoster.keep(serverPlayer, data, now);
            SpiritSenses.play(player, ModSounds.MASK_CRACK.get(), SoundSource.PLAYERS, 1.5F, 0.5F, false);
            SpiritSenses.play(player, ModSounds.ZANPAKUTO_RELEASE.get(), SoundSource.PLAYERS, 1.0F, 0.8F, false);
            if (player.level() instanceof ServerLevel level) {
                double halfWidth = player.getBbWidth() / 2.0;
                SpiritSenses.particles(level, ParticleTypes.SCULK_SOUL, player.getX(), player.getY(0.5), player.getZ(),
                        BECOME_SOULS, halfWidth, player.getBbHeight() / 2.0, halfWidth, 0.05);
                SpiritSenses.particles(level, dust(kind.colour()), player.getX(), player.getY(0.5), player.getZ(),
                        BECOME_DUST, 0.5, 0.8, 0.5, 0.05);
            }
            Milestones.check(serverPlayer, data);
        }
    }

    // ---- The Arrancar's tick ----

    /**
     * An Arrancar's body tick, in place of a Hollow's (see {@link HollowBody#tickBody}): a Hollow body some road
     * left on comes off, its Cero and its Gran Rey Cero gather ({@link GranReyCero#tick}), a release is kept up or
     * ends (its claws grow there), a Pesquisa focus holds or sends its pulse out ({@link Pesquisa#tick}), and a
     * held Bala key fires again ({@link Bala#tick}).
     */
    public static void tick(Player player, SoulData data, long now) {
        HollowBody.leaveBodyIfWorn(player, data);
        HollowCero.tick(player, data, now);
        GranReyCero.tick(player, data, now);
        ResurreccionEvents.upkeep(player, data, now);
        Pesquisa.tick(player, data, now);
        Bala.tick(player, data, now);
    }

    /**
     * Every second, for every soul (from {@link HollowBody#tickBody}): a release left on a soul that is no longer
     * an Arrancar ends, and so do a Pesquisa focus and a held Bala key, the Arrancar's health, speed and damage
     * are put right (taken off anyone else), the blades are kept right ({@link #reconcileBlades}), and its rank is
     * kept right ({@link ArrancarRoster#keep}). Server side.
     */
    public static void reconcile(Player player, SoulData data, long now) {
        if (player.level().isClientSide()) {
            return;
        }
        if (data.resurreccionActive && !ArrancarLogic.isArrancar(data)) {
            ResurreccionEvents.end(player, data, now, null);
        }
        if (!ArrancarLogic.isArrancar(data)) {
            // Only an Arrancar's tick keeps them, so one left behind would wake again if the soul became one anew.
            Pesquisa.release(data);
            Bala.release(data);
        }
        reconcileBody(player, data);
        reconcileBlades(player, data, now);
        ArrancarRoster.keep(player, data, now);
    }

    /**
     * Keeps the Arrancar blades in this player's inventory right, every second. Each owned one that is not this
     * soul's current blade crumbles: someone else's, any held by a soul that is no longer an Arrancar, an older
     * forging, or a second copy; the player is told once. The current one takes its origin's damage. Then an
     * alive Arrancar that has never had a blade (an old save, or a state set outright) gets its first at once,
     * and one without its blade has a new one form after {@code blade_reform_minutes} (never at 0), which makes
     * every older copy stale. An unowned blade, as from the creative tab, is left alone.
     */
    public static void reconcileBlades(Player player, SoulData data, long now) {
        if (player.level().isClientSide()) {
            return;
        }
        boolean arrancar = ArrancarLogic.isArrancar(data);
        HollowProgress.Arrancar record = data.hollow().arrancar();
        HollowTier origin = ArrancarLogic.origin(data);
        Inventory inventory = player.getInventory();
        boolean found = false;
        boolean crumbled = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ArrancarLogic.isArrancarBlade(stack) || !ZanpakutoItem.isBound(stack)) {
                continue;
            }
            if (found || !arrancar || !ArrancarLogic.isOwnBlade(stack, player)
                    || ArrancarLogic.forging(stack) != record.forging()) {
                inventory.setItem(slot, ItemStack.EMPTY);
                crumbled = true;
                continue;
            }
            found = true;
            refreshBlade(stack, origin);
        }
        if (crumbled) {
            player.displayClientMessage(Component.translatable(CRUMBLED).withStyle(ChatFormatting.GRAY), true);
        }
        if (!arrancar || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (!record.forged()) {
            // No growth: that came with becoming, which this soul skipped.
            data.setHollow(data.hollow().withArrancar(record.withForging(1)));
            data.arrancarBladelessTicks = 0;
            inventory.placeItemBackInInventory(forgeBlade(player, data));
            return;
        }
        int minutes = BleachConfig.get(BleachConfig.BLADE_REFORM_MINUTES);
        if (found || minutes <= 0) {
            data.arrancarBladelessTicks = 0;
            return;
        }
        data.arrancarBladelessTicks += BLADE_CHECK_TICKS;
        if (data.arrancarBladelessTicks < minutes * MenosEvolution.MINUTE_TICKS) {
            return;
        }
        data.arrancarBladelessTicks = 0;
        data.setHollow(data.hollow().withArrancar(record.withForging(record.forging() + 1)));
        ItemStack blade = forgeBlade(player, data);
        inventory.placeItemBackInInventory(blade);
        player.displayClientMessage(Component.translatable(REFORMED, ArrancarLogic.blade(player.getUUID()).displayName())
                .withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * A new blade for this soul, of its record's current forging: owned by it, named and coloured as its blade
     * is, with no spirit and never released, and dealing its origin's damage. It is not given to the player.
     */
    public static ItemStack forgeBlade(Player player, SoulData data) {
        ArrancarLogic.Blade kind = ArrancarLogic.blade(player.getUUID());
        ItemStack stack = new ItemStack(ModItems.ARRANCAR_BLADE.get());
        stack.set(ModComponents.ZANPAKUTO_BLADE.get(), new ZanpakutoBlade(Optional.of(player.getUUID()),
                player.getGameProfile().getName(), Optional.empty(), false, false, kind.colour()));
        stack.set(ModComponents.ARRANCAR_FORGING.get(), data.hollow().arrancar().forging());
        stack.set(DataComponents.ITEM_NAME, kind.displayName());
        refreshBlade(stack, ArrancarLogic.originFor(data));
        return stack;
    }

    /**
     * The Arrancar's three modifiers, through {@link HollowBody#setModifier}: the health it gains, sealed or
     * released (saved, as health is cut to the maximum on loading), and a release's speed and melee damage.
     * Each is taken off anyone who is not an Arrancar, or not released. Server side.
     */
    static void reconcileBody(Player player, SoulData data) {
        if (player.level().isClientSide()) {
            return;
        }
        HollowBody.setModifier(player, Attributes.MAX_HEALTH, ArrancarLogic.HEALTH_ID, ArrancarLogic.healthBonus(data),
                AttributeModifier.Operation.ADD_VALUE, true);
        HollowBody.setModifier(player, Attributes.MOVEMENT_SPEED, ArrancarLogic.SPEED_ID, ArrancarLogic.speedBonus(data),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        HollowBody.setModifier(player, Attributes.ATTACK_DAMAGE, ArrancarLogic.DAMAGE_ID,
                ArrancarLogic.damageMultiplier(data) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Gives a blade its origin's damage at a sword's speed, writing the stack only when that changes. */
    static void refreshBlade(ItemStack stack, HollowTier origin) {
        ItemAttributeModifiers wanted = ZanpakutoItem.attributes(
                new ZanpakutoSpirit.Form(0, ArrancarLogic.bladeDamage(origin), 1.6F, 0.0F));
        if (!wanted.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, wanted);
        }
    }

    // ---- Helpers ----

    /** The tear's channel in ticks. */
    private static long channelTicks() {
        return BleachConfig.get(BleachConfig.TEAR_CHANNEL_SECONDS) * 20L;
    }

    /** Whether something has broken the grip of a soul tearing at its mask (see {@link #tickTear}). */
    private static boolean breaks(Player player, SoulData data) {
        return !player.isAlive() || player.isSpectator() || data.devourTargetId >= 0 || HollowCero.charging(data)
                || !ArrancarLogic.canTear(HollowTier.of(data)) || data.hollowHurtAt > data.tearStartedAt
                || data.tearFrom == null || player.position().distanceToSqr(data.tearFrom) > STILL_DISTANCE_SQR;
    }

    /** A refusal as told above the hotbar, in grey: the wait with its minutes or seconds left. */
    private static Component refusalMessage(ArrancarLogic.TearRefusal refusal, SoulData data, long now) {
        MutableComponent message = switch (refusal) {
            case WAITING -> Component.translatable(refusal.messageKey(), ArrancarLogic.minutesToRetry(data, now));
            case FIGHTING -> Component.translatable(refusal.messageKey(), ArrancarLogic.secondsToCalm(data, now));
            default -> Component.translatable(refusal.messageKey());
        };
        return message.withStyle(ChatFormatting.GRAY);
    }

    private static DustParticleOptions dust(int colour) {
        return new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F), BECOME_DUST_SIZE);
    }
}
