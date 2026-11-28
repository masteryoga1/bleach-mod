package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The server's bosses of a kind it holds one of at a time, in the overworld's data: for each, by its id, when it may
 * come again, the souls who have beaten it, and for a boss kept at a post the one id its body always takes, so the
 * level itself refuses a second. A fight's progress is never kept here. Times are the overworld's game time, the one
 * clock every world shares and neither {@code /time} nor sleep moves ({@link #now}).
 * <p>
 * The table of bosses is here too ({@link #boss}, {@link #bosses}): one line each in the static block. A row's id need
 * not be in the table, so a world keeps the rows of a boss this jar does not know, and loading never waits on the
 * table. Every death of a listed boss's body marks it away, read at {@link EventPriority#LOWEST} and only if not
 * cancelled, so a death something else undid never counts.
 */
public final class BossLedger extends SavedData {
    public static final String NAME = "bleach_bosses";
    /** A game day: twenty minutes of the server running, whether anyone sleeps or not. */
    public static final long TICKS_PER_DAY = Level.TICKS_PER_DAY;
    /** The longest wait any boss's config allows. A row further off came from another world's clock and is over. */
    public static final int LONGEST_WAIT_DAYS = 365;

    private static final String BOSSES = "bosses";
    private static final String ID = "id";
    private static final String UNTIL = "until";
    private static final String BEATEN_BY = "beaten_by";
    private static final String KEPT = "kept";

    /** Every boss the ledger and {@code /bleach boss} know, by id, in the order listed. Filled once, below. */
    private static final Map<String, Boss> TABLE = new LinkedHashMap<>();

    static {
        // One line per boss. A boss's own class holds its Boss; it must not read this table while it is made.
        add(ShriekerComing.BOSS);
        add(GrandFisherComing.BOSS);
        add(RunugangaComing.BOSS);
    }

    // In the order the bosses were first noted, which is the order they are saved in.
    private final Map<String, Row> rows = new LinkedHashMap<>();

    /** An empty ledger. The server keeps its own ({@link #get}); game tests make fresh ones. */
    public BossLedger() {
    }

    public static SavedData.Factory<BossLedger> factory() {
        return new SavedData.Factory<>(BossLedger::new, BossLedger::load, null);
    }

    /** The server's ledger. Never hold it past a call: each server, an integrated one too, has its own. */
    public static BossLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** The overworld's game time: the one clock every row is read and written in. */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---- The table ----

    private static void add(Boss boss) {
        if (TABLE.putIfAbsent(boss.id(), boss) != null) {
            throw new IllegalStateException("Two bosses listed as " + boss.id());
        }
    }

    /** The boss listed by this id, or null. */
    @Nullable
    public static Boss boss(String id) {
        return TABLE.get(id);
    }

    /** Every listed boss, in the order listed. */
    public static Collection<Boss> bosses() {
        return Collections.unmodifiableCollection(TABLE.values());
    }

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, BossLedger::onDeath);
    }

    /**
     * A listed boss's body died: away its days, credited to the player whose blow it was, an arrow's owner or the soul
     * who turned a leech back on its Shrieker among them. {@code /kill} credits none.
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity body = event.getEntity();
        if (!(body.level() instanceof ServerLevel level)) {
            return;
        }
        for (Boss boss : TABLE.values()) {
            if (boss.isBody(body)) {
                MinecraftServer server = level.getServer();
                List<UUID> victors = event.getSource().getEntity() instanceof ServerPlayer player
                        ? List.of(player.getUUID()) : List.of();
                get(server).markAway(boss.id(), now(server), boss.returnDays(), victors);
                return;
            }
        }
    }

    // ---- Reading and writing ----

    /**
     * Reads the rows one by one, leaving out (and logging) one with no id or an id read already; a missing or damaged
     * field takes its default (may come now, beaten by none, not kept), and a soul whose id cannot be read is left out
     * of its row. Never throws.
     */
    public static BossLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        BossLedger loaded = new BossLedger();
        // Empty when the list is missing or not a list of compounds.
        ListTag list = tag.getList(BOSSES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            String id = saved.getString(ID);
            if (id.isEmpty()) {
                BleachMod.LOGGER.warn("Left out a boss in the ledger with no id");
                continue;
            }
            if (loaded.rows.containsKey(id)) {
                BleachMod.LOGGER.warn("Left out a second entry in the boss ledger for {}", id);
                continue;
            }
            Row row = new Row();
            row.until = saved.contains(UNTIL, Tag.TAG_ANY_NUMERIC) ? Math.max(0L, saved.getLong(UNTIL)) : 0L;
            ListTag souls = saved.getList(BEATEN_BY, Tag.TAG_INT_ARRAY);
            for (int j = 0; j < souls.size(); j++) {
                try {
                    row.beatenBy.add(NbtUtils.loadUUID(souls.get(j)));
                } catch (IllegalArgumentException e) {
                    BleachMod.LOGGER.warn("Left out a soul who beat {} that could not be read: {}", id, e.getMessage());
                }
            }
            row.kept = saved.hasUUID(KEPT) ? saved.getUUID(KEPT) : null;
            if (!row.isEmpty()) {
                loaded.rows.put(id, row);
            }
        }
        return loaded;
    }

    /** Writes every row with anything to remember; one with nothing in it is left out. */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        rows.forEach((id, row) -> {
            if (row.isEmpty()) {
                return;
            }
            CompoundTag saved = new CompoundTag();
            saved.putString(ID, id);
            saved.putLong(UNTIL, row.until);
            ListTag souls = new ListTag();
            row.beatenBy.forEach(soul -> souls.add(NbtUtils.createUUID(soul)));
            saved.put(BEATEN_BY, souls);
            if (row.kept != null) {
                saved.putUUID(KEPT, row.kept);
            }
            list.add(saved);
        });
        tag.put(BOSSES, list);
        return tag;
    }

    // ---- A boss's row ----

    /**
     * Ticks until this boss may come again, 0 if it may now. A wait further off than {@link #LONGEST_WAIT_DAYS} came from
     * another world's clock (a ledger copied into a younger world) and counts as over, so it never waits for ages.
     */
    public long waitLeft(String boss, long now) {
        Row row = rows.get(boss);
        if (row == null) {
            return 0L;
        }
        long left = row.until - now;
        return left > LONGEST_WAIT_DAYS * TICKS_PER_DAY ? 0L : Math.max(0L, left);
    }

    /** Whether this boss's wait is over, or it never had one. */
    public boolean mayCome(String boss, long now) {
        return waitLeft(boss, now) <= 0L;
    }

    /**
     * This boss went (died, escaped, was lifted away): it may come again {@code days} game days from now, or later if a
     * wait already set runs longer, and these souls have beaten it. A wait is never shortened: a second mark later on
     * ends it at the later of the two ends, so a death after an escape keeps the boss away its full days from the death.
     */
    public void markAway(String boss, long now, int days, Collection<UUID> beatenBy) {
        long wait = Mth.clamp(days, 0, LONGEST_WAIT_DAYS) * TICKS_PER_DAY;
        long left = waitLeft(boss, now);
        Row row = rows.computeIfAbsent(boss, id -> new Row());
        row.until = now + Math.max(left, wait);
        row.beatenBy.addAll(beatenBy);
        setDirty();
    }

    /**
     * The souls who have beaten this boss, in the order they first did; a view, empty for none. Never capped: it holds
     * each soul once, so no more than the players who ever won.
     */
    public Set<UUID> beatenBy(String boss) {
        Row row = rows.get(boss);
        return row != null ? Collections.unmodifiableSet(row.beatenBy) : Set.of();
    }

    /** This kept boss's one id, or null if it was never asked for. */
    @Nullable
    public UUID kept(String boss) {
        Row row = rows.get(boss);
        return row != null ? row.kept : null;
    }

    /** The one id this kept boss's body always takes, made and saved the first time it is asked for. */
    public UUID keptId(String boss) {
        Row row = rows.computeIfAbsent(boss, id -> new Row());
        if (row.kept == null) {
            row.kept = UUID.randomUUID();
            setDirty();
        }
        return row.kept;
    }

    /** This boss may come again now, and who beat it is forgotten. A kept boss keeps its id, so a second never comes. */
    public void reset(String boss) {
        Row row = rows.get(boss);
        if (row != null) {
            row.until = 0L;
            row.beatenBy.clear();
            setDirty();
        }
    }

    /** One boss's row as it stands. Only the ledger changes it. */
    private static final class Row {
        private long until;
        private final Set<UUID> beatenBy = new LinkedHashSet<>();
        @Nullable
        private UUID kept;

        private boolean isEmpty() {
            return until <= 0L && beatenBy.isEmpty() && kept == null;
        }
    }
}
