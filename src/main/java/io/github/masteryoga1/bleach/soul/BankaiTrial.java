package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * The subjugation (kuppuku): a soul's Bankai trial, a bout of {@code [bankai.trial] bout_seconds} with its own
 * zanpakutō spirit, drawn out afresh to be beaten down. No end writes anything but the try counted as it began: the
 * time, a break-off seen on the wielder's tick, its logging out or death, the server stopping, or the spirit leaving
 * its world for any reason, so whatever sends a drawn-out spirit home (for now a beating too) ends the bout. The bouts
 * are held only in memory, by their wielders' UUIDs, on the server thread.
 */
public final class BankaiTrial {
    private static final String BEGUN = "message.bleach.bankai.trial.begun", TIME_UP = "message.bleach.bankai.trial.time";
    private static final String BROKEN = "message.bleach.bankai.trial.broken_off", HOME = "message.bleach.spirit.home";
    private static final int SECOND_TICKS = 20;

    /** Every bout under way, by its wielder's UUID. Server thread only. */
    private static final Map<UUID, Trial> TRIALS = new HashMap<>();

    private BankaiTrial() {
    }

    /** What came of beginning a bout: STARTED, a refusal of its own, or one of the call's, whose names they share. */
    public enum Start {
        STARTED, DEAD, WATCHING, ALREADY, REACHED, NO_POWER, NAMELESS, NO_SPIRIT, NO_TYPE, BUSY, PEACEFUL, NO_ROOM
    }

    /** How a bout ended: its time ran out, or it was broken off. */
    public enum End { TIME, BROKEN_OFF }

    /**
     * One bout: the player object that began it (a respawn or an End exit makes a new one, which breaks it off), its
     * spirit, its world, where the wielder stood, and when it began and when its time runs out.
     */
    public record Trial(Player wielder, MaterialisedSpirit spirit, ResourceKey<Level> level, Vec3 centre, long startedAt,
            long endsAt) {
    }

    /** A spirit leaving its world ends its bout, as do its wielder's logging out and death and the server stopping. */
    public static void register(IEventBus bus) {
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

    /** The bout begins with this spirit, out before its wielder: held, and its try counted. */
    private static Start open(Player wielder, SoulData data, MaterialisedSpirit spirit, long now) {
        int seconds = boutSeconds();
        TRIALS.put(wielder.getUUID(), new Trial(wielder, spirit, wielder.level().dimension(), wielder.position(), now,
                now + seconds * (long) SECOND_TICKS));
        ZanpakutoBond bond = data.zanpakuto();
        data.setZanpakuto(bond.withBankai(bond.bankai().tried(now)));
        wielder.sendSystemMessage(Component.translatable(BEGUN, seconds).withStyle(ChatFormatting.GRAY));
        return Start.STARTED;
    }

    /** Each server tick of a living player in a bout: a break-off ends it, and then the time. */
    public static void tick(Player wielder, SoulData data, long now) {
        Trial trial = TRIALS.get(wielder.getUUID());
        if (trial == null || wielder.level().isClientSide()) {
            return;
        }
        if (brokenOff(wielder, data, trial)) {
            end(trial, End.BROKEN_OFF, false);
        } else if (now >= trial.endsAt) {
            end(trial, End.TIME, false);
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
     * Ends this bout if it is still under way: off the table first, so its spirit's going home does not end it again,
     * then its spirit sent home with the end's line, unless the spirit is leaving its world already, where a discard
     * would change the world as it moves the spirit: then only the line, unless a beaten spirit said its own.
     */
    private static void end(Trial trial, End end, boolean leaving) {
        if (!TRIALS.remove(trial.wielder.getUUID(), trial)) {
            return;
        }
        Player wielder = trial.wielder;
        String line = end == End.BROKEN_OFF ? BROKEN : HOME;
        if (end == End.TIME) {
            wielder.sendSystemMessage(Component.translatable(TIME_UP).withStyle(ChatFormatting.GRAY));
        }
        if (!leaving && !trial.spirit.isRemoved()) {
            Materialisation.sendHome(trial.spirit, line);
        } else if (wielder.isAlive() && !wielder.isRemoved() && !trial.spirit.beaten()) {
            wielder.displayClientMessage(Component.translatable(line).withStyle(ChatFormatting.GRAY), true);
        }
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

    /** How long a bout lasts, in seconds. */
    public static int boutSeconds() {
        return BleachConfig.get(BleachConfig.BANKAI_TRIAL_BOUT_SECONDS);
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
