package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * A boss's bar: one {@link ServerBossEvent} its boss holds and ticks on the server, shown to the souls within
 * {@link #RANGE} who see spirits, spectators aside, and to no one while the boss is hidden. Its progress follows the
 * boss's health on every tick, so a blow or a heal shows at once; who sees it is looked at again each second, by player
 * object rather than by {@code Entity.equals}, which compares entity ids that a respawn keeps, so a soul that respawned
 * as a new player is shown it afresh and its old self let go. A new phase restyles this bar, never a second one.
 * <p>
 * The bar comes down when its boss dies (the next tick sees it), when the boss calls {@link #forget} from
 * {@code stopSeenByPlayer} or {@link #clear} itself, and when the boss leaves its level for any reason: an unloaded
 * chunk takes an entity away without calling {@code remove}, but NeoForge's {@link EntityLeaveLevelEvent} still fires
 * (ServerLevel.EntityCallbacks.onTrackingEnd), and every bar shown to anyone is listed here for it.
 */
public final class BossBar {
    /** How far a boss's bar reaches, in blocks: as far as the Gillian's. */
    public static final double RANGE = 64.0;
    /** How often who sees the bar is looked at again, in ticks: once a second, as the Gillian's and the bait's. */
    public static final int REFRESH_TICKS = 20;
    /** The bars shown to anyone now, so their bosses leaving a level takes them down. The server thread's only. */
    private static final Set<BossBar> SHOWN = Collections.newSetFromMap(new IdentityHashMap<>());

    private final LivingEntity owner;
    private final ServerBossEvent event;
    private boolean hidden;
    /** Ticks until who sees the bar is looked at again; the first tick looks at once. */
    private int untilRefresh;
    /** Who of those who may see it is shown it: all, unless its boss's fight is one soul's ({@link #onlyFor}). */
    private Predicate<ServerPlayer> shownTo = viewer -> true;

    /** A bar for this boss, named by its lang key, shown from its first tick unless hidden first. */
    public BossBar(LivingEntity owner, String key, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        this.owner = owner;
        this.event = new ServerBossEvent(Component.translatable(key), color, overlay);
    }

    /** Shows the bar only to those of its viewers this lets through, for a fight that is one soul's; this bar. */
    public BossBar onlyFor(Predicate<ServerPlayer> shownTo) {
        this.shownTo = shownTo;
        return this;
    }

    public static void register(IEventBus bus) {
        bus.addListener(BossBar::onLeave);
        bus.addListener(BossBar::onServerStopped);
    }

    /** Called by its boss on the server each tick, its dying ticks too: the health at once, who sees it each second. */
    public void tick() {
        if (gone()) {
            clear();
            return;
        }
        event.setProgress(progress(owner));
        if (--untilRefresh <= 0) {
            refresh();
        }
    }

    /** Shows the bar now to those who should see it and takes it from the rest, as each second's look does. */
    public void refresh() {
        untilRefresh = REFRESH_TICKS;
        if (hidden || gone() || !(owner.level() instanceof ServerLevel level)) {
            clear();
            return;
        }
        event.setProgress(progress(owner));
        List<ServerPlayer> viewers = viewers(level, owner.position());
        viewers.removeIf(shownTo.negate());
        // Those who left go first, by object: a respawned soul's new player has its old self's entity id, so equals would
        // keep the old one and never show the new one the bar (mc PlayerList.java:437, Entity.java:341-348).
        for (ServerPlayer shown : List.copyOf(event.getPlayers())) {
            if (viewers.stream().noneMatch(viewer -> viewer == shown)) {
                event.removePlayer(shown);
            }
        }
        viewers.forEach(event::addPlayer);
        listed();
    }

    /** Who may see a boss's bar from here: the souls within reach who see spirits, spectators aside. */
    public static List<ServerPlayer> viewers(ServerLevel level, Vec3 at) {
        List<ServerPlayer> aware = SpiritSenses.awareNear(level, at, RANGE);
        aware.removeIf(Player::isSpectator);
        return aware;
    }

    /** One soul has stopped seeing the boss (its {@code stopSeenByPlayer}): the bar goes from it at once. */
    public void forget(ServerPlayer player) {
        event.removePlayer(player);
        listed();
    }

    /** Hidden, the bar shows no one, for a boss not yet out; shown again, it reaches those who should see it at once. */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
        refresh();
    }

    public boolean hidden() {
        return hidden;
    }

    /** A new name, colour and overlay for a boss's next phase, on the same bar: its viewers keep it. */
    public void restyle(String key, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        event.setName(Component.translatable(key));
        event.setColor(color);
        event.setOverlay(overlay);
    }

    /** A new name on the same bar, as a clock on it counts down: its viewers keep it. */
    public void rename(Component name) {
        event.setName(name);
    }

    /** Takes the bar from everyone: its boss died or left, or is hidden. */
    public void clear() {
        event.removeAllPlayers();
        SHOWN.remove(this);
    }

    /** Who is shown the bar now. */
    public Collection<ServerPlayer> players() {
        return event.getPlayers();
    }

    /** The bar itself, to read its name, colour, overlay and progress; change it only through this class. */
    public ServerBossEvent event() {
        return event;
    }

    /** A boss whose bar shows more than its one health, such as a body whose health is split between two halves. */
    public interface Gauged {
        /** How full its bar is: 0 empty, 1 full. */
        float gauge();
    }

    /** How full a boss's bar is: its health over its most, or what its own gauge says. */
    public static float progress(LivingEntity boss) {
        float full = boss instanceof Gauged gauged ? gauged.gauge() : boss.getHealth() / boss.getMaxHealth();
        return Mth.clamp(full, 0.0F, 1.0F);
    }

    private boolean gone() {
        return !owner.isAlive() || owner.isRemoved();
    }

    /** Lists the bar while anyone is shown it, and only then, so a bar no one sees holds nothing here. */
    private void listed() {
        if (event.getPlayers().isEmpty()) {
            SHOWN.remove(this);
        } else {
            SHOWN.add(this);
        }
    }

    /** A boss leaving its level, by unloading, death, a change of world or a discard, takes its bar down. */
    private static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || SHOWN.isEmpty()) {
            return;
        }
        for (BossBar bar : List.copyOf(SHOWN)) {
            if (bar.owner == event.getEntity()) {
                bar.clear();
            }
        }
    }

    /** A closed world's bars hold its players and bosses: none outlives it into the next world a game opens. */
    private static void onServerStopped(ServerStoppedEvent event) {
        SHOWN.clear();
    }
}
