package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * The subjugation (kuppuku): a soul's Bankai trial, a bout of {@code [bankai.trial] bout_seconds} with its own
 * zanpakutō spirit, drawn out afresh to be beaten down. Its try is counted as it begins, and no end but the win
 * writes anything more: the time, the loss, a break-off seen on the wielder's tick, its logging out or death, the
 * server stopping, or the spirit leaving its world for any reason, so whatever sends a drawn-out spirit home ends the
 * bout. The bouts are held only in memory, by their wielders' UUIDs, on the server thread.
 *
 * <p>A spirit beaten to its floor in a bout yields ({@link #yields}): the soul reaches Bankai by its spirit's name for
 * it, as the grant gives it. A spirit's blow that would leave its wielder at {@code loss_health} stops there, and the
 * bout is lost, so no spirit ever kills its wielder, in a bout or out of one. While a bout lasts the two can touch
 * nothing living but each other, and the spirit's bar counts its seconds down.
 */
public final class BankaiTrial {
    private static final String BEGUN = "message.bleach.bankai.trial.begun", TIME_UP = "message.bleach.bankai.trial.time";
    private static final String BROKEN = "message.bleach.bankai.trial.broken_off", HOME = "message.bleach.spirit.home";
    private static final String LOST = "message.bleach.bankai.trial.lost", WON = "message.bleach.bankai.trial.won";
    private static final String YIELDED = "message.bleach.bankai.trial.yielded", SPIRIT_WON = "message.bleach.spirit.won";
    private static final String KEY = "commands.bleach.zanpakuto.bankai.trial", CLOCK = "bossbar.bleach.bankai_trial";
    private static final String RELEASE_KEY = "key.bleach.zanpakuto_release";
    private static final int SECOND_TICKS = 20;

    /** Every bout under way, by its wielder's UUID. Server thread only. */
    private static final Map<UUID, Trial> TRIALS = new HashMap<>();

    private BankaiTrial() {
    }

    /**
     * What came of beginning a bout: STARTED, a refusal of its own, or one of the call's, whose names they share, each
     * but STARTED with the reason a command says, the call's own for its own.
     */
    public enum Start {
        STARTED(false), DEAD(true), WATCHING(false), ALREADY(false), REACHED(false), NO_POWER(true), NAMELESS(true),
        NO_SPIRIT(true), NO_TYPE(true), BUSY(true), PEACEFUL(true), NO_ROOM(true);

        private final boolean called;

        Start(boolean called) {
            this.called = called;
        }

        public String key() {
            return called ? Materialisation.Call.valueOf(name()).key() : KEY + "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a bout ended: the spirit yielded, it beat its wielder down, the time ran out, or it was broken off. */
    public enum End { WIN, LOSS, TIME, BROKEN_OFF }

    /**
     * One bout: the player object that began it (a respawn or an End exit makes a new one, which breaks it off), its
     * spirit, its world, where the wielder stood, when it began and when its time runs out, and its spirit's bar's own
     * name, which the bar shows beside the seconds left.
     */
    public record Trial(Player wielder, MaterialisedSpirit spirit, ResourceKey<Level> level, Vec3 centre, long startedAt,
            long endsAt, Component name) {
    }

    /**
     * The shield first of all, so nothing else weighs a blow that should not land; the loss last of all, after every
     * other change to the blow; and a spirit leaving its world ends its bout, as do its wielder's logging out and death
     * and the server stopping.
     */
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, BankaiTrial::onIncomingDamage);
        bus.addListener(EventPriority.LOWEST, BankaiTrial::onDamagePre);
        bus.addListener(BankaiTrial::onLeave);
        bus.addListener(BankaiTrial::onLoggedOut);
        bus.addListener(BankaiTrial::onDeath);
        bus.addListener(BankaiTrial::onServerStopping);
        bus.addListener(BankaiTrial::onServerStopped);
    }

    /**
     * Begins the wielder's bout where it stands, or says why not: the wielder a spectator or in creative mode, in a
     * bout already, or past Bankai, then the call's own refusals, a dead wielder's among them. A spirit it has out is
     * replaced by one afresh and whole, and only once the new one has room, so a refused bout leaves it out. The try is
     * counted only once the bout has begun. On the client: NO_SPIRIT.
     */
    public static Start begin(Player wielder, long now) {
        if (!(wielder.level() instanceof ServerLevel)) {
            return Start.NO_SPIRIT;
        }
        if (wielder.isSpectator() || wielder.isCreative()) {
            return Start.WATCHING;
        }
        if (TRIALS.containsKey(wielder.getUUID())) {
            return Start.ALREADY;
        }
        SoulData data = SoulLogic.get(wielder);
        if (data.zanpakuto().bankai().achieved()) {
            return Start.REACHED;
        }
        Materialisation.Call refusal = Materialisation.refusal(wielder, true);
        if (refusal != null) {
            return Start.valueOf(refusal.name());
        }
        // No room, or a rung that changed since; never ALREADY, as the call replaces a spirit out.
        Materialisation.Call call = Materialisation.callOut(wielder, true);
        if (call != Materialisation.Call.CALLED) {
            return Start.valueOf(call.name());
        }
        // Held by its call from before it joined the world, so it is there.
        return open(wielder, data, Materialisation.of(wielder.getUUID()), now);
    }

    /** The bout begins with this spirit, out before its wielder: held, its try counted, and its clock on its bar. */
    private static Start open(Player wielder, SoulData data, MaterialisedSpirit spirit, long now) {
        int seconds = boutSeconds();
        Trial trial = new Trial(wielder, spirit, wielder.level().dimension(), wielder.position(), now,
                now + seconds * (long) SECOND_TICKS, spirit.bar().event().getName());
        TRIALS.put(wielder.getUUID(), trial);
        ZanpakutoBond bond = data.zanpakuto();
        data.setZanpakuto(bond.withBankai(bond.bankai().tried(now)));
        showTime(trial, now);
        wielder.sendSystemMessage(Component.translatable(BEGUN, seconds).withStyle(ChatFormatting.GRAY));
        return Start.STARTED;
    }

    /**
     * Each server tick of a living player in a bout: a break-off ends it, then the time, which waits for a spirit
     * beaten as it runs out to yield on its own tick, and each second the clock.
     */
    public static void tick(Player wielder, SoulData data, long now) {
        Trial trial = TRIALS.get(wielder.getUUID());
        if (trial == null || wielder.level().isClientSide()) {
            return;
        }
        if (brokenOff(wielder, data, trial)) {
            end(trial, End.BROKEN_OFF, false);
        } else if (now >= trial.endsAt && !trial.spirit.beaten()) {
            end(trial, End.TIME, false);
        } else if (Math.floorMod(now - trial.startedAt, SECOND_TICKS) == 0) {
            showTime(trial, now);
        }
    }

    /**
     * The break-offs a drawn-out spirit's own ends lack: another player object for the wielder, which is a spectator
     * or in creative mode, past Bankai, or more than {@link Materialisation#LEAVE_DISTANCE} blocks from where it began.
     */
    private static boolean brokenOff(Player wielder, SoulData data, Trial trial) {
        return trial.wielder != wielder || wielder.isSpectator() || wielder.isCreative()
                || data.zanpakuto().bankai().achieved()
                || wielder.position().subtract(trial.centre).horizontalDistance() > Materialisation.LEAVE_DISTANCE;
    }

    /** Ends every bout broken off: the server-stop path, before any player is saved; public for the tests. */
    public static void breakOffAll() {
        for (Trial trial : List.copyOf(TRIALS.values())) {
            end(trial, End.BROKEN_OFF, false);
        }
    }

    /** Ends this wielder's bout broken off, if it has one. */
    private static void breakOff(Player wielder) {
        Trial trial = TRIALS.get(wielder.getUUID());
        if (trial != null) {
            end(trial, End.BROKEN_OFF, false);
        }
    }

    /**
     * Ends this bout if it is still under way: off the table first, so its spirit's going home does not end it again;
     * then the win's record or the loss's or the time's line in chat; then its spirit sent home with the end's line,
     * unless the spirit is leaving its world already, where a discard would change the world as it moves the spirit:
     * then only the line.
     */
    private static void end(Trial trial, End end, boolean leaving) {
        if (!TRIALS.remove(trial.wielder.getUUID(), trial)) {
            return;
        }
        Player wielder = trial.wielder;
        String line = end == End.WIN ? YIELDED : end == End.BROKEN_OFF ? BROKEN : HOME;
        if (end == End.WIN) {
            won(wielder);
        } else if (end != End.BROKEN_OFF) {
            wielder.sendSystemMessage(Component.translatable(end == End.TIME ? TIME_UP : LOST)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!leaving && !trial.spirit.isRemoved()) {
            Materialisation.sendHome(trial.spirit, line);
        } else if (wielder.isAlive() && !wielder.isRemoved()) {
            wielder.displayClientMessage(Component.translatable(line).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * A spirit beaten to its floor in a bout yields, unless its wielder broke the bout off this tick (its own tick has
     * not seen it yet): either way the bout ends, and true, so the spirit is not sent home beaten as well. False for a
     * spirit in no bout, which goes home beaten ({@link Materialisation#upkeep}).
     */
    public static boolean yields(MaterialisedSpirit spirit) {
        Trial trial = trialOf(spirit);
        if (trial == null) {
            return false;
        }
        end(trial, brokenOff(trial.wielder, SoulLogic.get(trial.wielder), trial) ? End.BROKEN_OFF : End.WIN, false);
        return true;
    }

    /**
     * The soul reaches Bankai by its spirit's name for it, as the grant gives it, and is told so in gold with the key
     * that calls it; a spirit with no Bankai in this world, which the grant refuses too, gives nothing. A soul past
     * Bankai keeps it as it is: {@link #yields} breaks such a bout off before it comes here, so this guards only a
     * later way to win one.
     */
    private static void won(Player wielder) {
        SoulData data = SoulLogic.get(wielder);
        Optional<Component> name = ZanpakutoSpirits.get(wielder.level(), data.zanpakuto().spirit())
                .flatMap(BankaiLogic::nameOf);
        if (data.zanpakuto().bankai().achieved() || name.isEmpty()) {
            return;
        }
        BankaiLogic.reach(data, name.get(), wielder.level().getGameTime());
        if (wielder instanceof ServerPlayer player) {
            BleachNetwork.syncSoul(player, data);
        }
        wielder.sendSystemMessage(Component.translatable(WON, name.get(), Component.keybind(RELEASE_KEY))
                .withStyle(ChatFormatting.GOLD));
    }

    /** The bout of the wielder with this UUID, or null when it has none. */
    @Nullable
    public static Trial trial(@Nullable UUID wielderId) {
        return TRIALS.get(wielderId);
    }

    /** The bout this spirit is in, or null for a spirit in none. */
    @Nullable
    private static Trial trialOf(MaterialisedSpirit spirit) {
        Trial trial = trial(spirit.wielderId());
        return trial != null && trial.spirit == spirit ? trial : null;
    }

    /** The spirit's bar shows its own name and the seconds left. */
    private static void showTime(Trial trial, long now) {
        trial.spirit.bar().rename(Component.translatable(CLOCK, trial.name, VisoredLogic.secondsUntil(trial.endsAt, now)));
    }

    /** How long a bout lasts, in seconds. */
    public static int boutSeconds() {
        return BleachConfig.get(BleachConfig.BANKAI_TRIAL_BOUT_SECONDS);
    }

    /** The wielder's health at which its spirit's last blow stops. */
    public static float lossHealth() {
        return (float) BleachConfig.get(BleachConfig.BANKAI_TRIAL_LOSS_HEALTH);
    }

    /**
     * While a bout lasts, nothing living but its spirit can touch the wielder, and the wielder can touch nothing living
     * but its spirit: the blow of any other creature or player on the wielder, and any blow of the wielder's (its
     * arrows, waves and blasts too) on anything else, is stopped before anything else weighs it, as at the inner
     * Hollow's fight. Blocks, lava and falls still hurt the wielder; the spirit's own guard keeps others' blows off it.
     * A beaten spirit strikes no more, in a bout or out of one: a blow before its next tick would turn a won bout to a loss.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == victim) {
            return;
        }
        Trial guarded = victim instanceof Player wielder ? TRIALS.get(wielder.getUUID()) : null;
        Trial striking = attacker instanceof Player striker ? TRIALS.get(striker.getUUID()) : null;
        if (guarded != null && attacker != guarded.spirit || striking != null && victim != striking.spirit
                || attacker instanceof MaterialisedSpirit spirit && spirit.beaten()) {
            event.setCanceled(true);
        }
    }

    /**
     * A spirit's blow on its own wielder, once armour and every other listener have had their say and before
     * absorption, that would leave the wielder at {@code loss_health} or below: cut so it leaves the wielder there (or
     * where it was, if lower), and the spirit goes home, the bout lost, or, out of one, saying it won. So no spirit
     * ever kills its wielder, and no totem is spent on one. Only a spirit's blows: a fall or lava still can.
     */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player wielder) || wielder.level().isClientSide()
                || !(event.getSource().getEntity() instanceof MaterialisedSpirit spirit)
                || !wielder.getUUID().equals(spirit.wielderId())) {
            return;
        }
        float health = wielder.getHealth();
        float absorption = wielder.getAbsorptionAmount();
        float floor = lossHealth();
        if (health - Math.max(0.0F, event.getNewDamage() - absorption) > floor) {
            return;
        }
        event.setNewDamage(VisoredLogic.clampedBlow(event.getNewDamage(), health, absorption, floor));
        Trial trial = trialOf(spirit);
        if (trial != null) {
            end(trial, End.LOSS, false);
        } else {
            Materialisation.sendHome(spirit, SPIRIT_WON);
        }
    }

    /**
     * A spirit leaving its world for any reason breaks its bout off. It is keyed on the spirit alone and never sent
     * home here, as an unload or a move into an unloaded chunk fires this while the world is still moving it.
     */
    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof MaterialisedSpirit spirit) {
            Trial trial = trialOf(spirit);
            if (trial != null) {
                end(trial, End.BROKEN_OFF, true);
            }
        }
    }

    /** A bout does not outlast the session: this runs before the player is saved. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        breakOff(event.getEntity());
    }

    /** A wielder that dies breaks its bout off, before its soul is copied to the respawned player. */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player wielder && !wielder.level().isClientSide()) {
            breakOff(wielder);
        }
    }

    /** A stop saves every player before any of them logs out, so every bout is broken off here first. */
    private static void onServerStopping(ServerStoppingEvent event) {
        breakOffAll();
    }

    /** A closed world's bouts went with it, unsaved: none is held into the next world a game opens. */
    private static void onServerStopped(ServerStoppedEvent event) {
        TRIALS.clear();
    }
}
