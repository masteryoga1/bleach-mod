package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The server's Arrancar ranks, in the overworld's data like MenosEvolution.VastoLordes: each Arrancar's Número
 * (from 11, by birth order), its strength and when it was last seen, and the ten Espada seats dealt by
 * strength. The souls' records only cache what this says; keep() puts them right once a second.
 *
 * <p>Canon numbers the Números by birth and never gives a number twice, and seats the Espada by reiryoku, not by
 * killing ability. Here strength is whole maximum reiryoku, which only grows in play, so seats do not flicker. An
 * Arrancar that stays away too long ({@code [arrancar.ranks] absent_hours} of the server running) gives up its
 * seat, and its number once every Número is in use; leaving the Arrancar state gives up both at once. An operator
 * can pin an Arrancar to a seat by command, standing in for Aizen's own choice.
 */
public final class ArrancarRoster extends SavedData {
    public static final String NAME = "bleach_arrancar_roster";
    /** Game ticks in an hour of the server running. */
    public static final long TICKS_PER_HOUR = 72_000L;
    /** A present soul's "last seen" is rewritten at most this often, so the file is not dirtied every second. */
    public static final long SEEN_REFRESH_TICKS = 1_200L;

    /** How many Números there are: 11 to 99. */
    private static final int NUMEROS = ArrancarRank.LAST_NUMERO - ArrancarRank.FIRST_NUMERO + 1;
    private static final String LAST_NUMERO = "last_numero";
    private static final String SOULS = "souls";
    private static final String ID = "id";
    private static final String SOUL_NAME = "name";
    private static final String NUMERO = "numero";
    private static final String STRENGTH = "strength";
    private static final String SEEN_AT = "seen_at";
    private static final String SEAT = "seat";
    private static final String LOST_SEAT = "lost_seat";
    private static final String PIN = "pin";

    /** The order seats are dealt in: strongest first, then the elder Número (none last), then the id. */
    private static final Comparator<Place> BY_STRENGTH = Comparator.<Place>comparingInt(place -> -place.strength)
            .thenComparingInt(place -> place.numero == 0 ? Integer.MAX_VALUE : place.numero)
            .thenComparing(place -> place.id);
    /**
     * The order of the list: Espada by seat, Privaron Espada by number, Números by number, then the unnumbered;
     * between equals (two Privaron of one seat), the elder Número, then the id.
     */
    private static final Comparator<Entry> LISTED = Comparator.<Entry>comparingInt(entry -> listedGroup(entry.rank()))
            .thenComparingInt(entry -> entry.rank().number())
            .thenComparingInt(entry -> entry.rank().numero() == 0 ? Integer.MAX_VALUE : entry.rank().numero())
            .thenComparing(Entry::id);

    // In the order the souls were first seen, which is the order they are saved in.
    private final Map<UUID, Place> places = new LinkedHashMap<>();
    /** The last Número given in turn; the next is the first free one after it, coming round from 99 to 11. */
    private int lastNumero = ArrancarRank.FIRST_NUMERO - 1;

    public ArrancarRoster() {
    }

    public static SavedData.Factory<ArrancarRoster> factory() {
        return new SavedData.Factory<>(ArrancarRoster::new, ArrancarRoster::load, null);
    }

    public static ArrancarRoster get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /**
     * Reads the souls one by one, leaving out (and logging) any whose id cannot be read or was read already, and
     * clearing any number, seat or pin that is out of range or already taken by a soul read earlier. Never throws.
     */
    public static ArrancarRoster load(CompoundTag tag, HolderLookup.Provider registries) {
        ArrancarRoster loaded = new ArrancarRoster();
        loaded.lastNumero = Mth.clamp(tag.getInt(LAST_NUMERO), ArrancarRank.FIRST_NUMERO - 1, ArrancarRank.LAST_NUMERO);
        boolean[] numeros = new boolean[ArrancarRank.LAST_NUMERO + 1];
        boolean[] seats = new boolean[ArrancarRank.SEATS + 1];
        boolean[] pins = new boolean[ArrancarRank.SEATS + 1];
        // Empty when "souls" is missing or not a list of compounds.
        ListTag list = tag.getList(SOULS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Tag raw = entry.get(ID);
            if (raw == null) {
                BleachMod.LOGGER.warn("Left out an Arrancar on the roster with no id");
                continue;
            }
            UUID id;
            try {
                id = NbtUtils.loadUUID(raw);
            } catch (IllegalArgumentException e) {
                BleachMod.LOGGER.warn("Left out an Arrancar on the roster that could not be read: {}", e.getMessage());
                continue;
            }
            if (loaded.places.containsKey(id)) {
                BleachMod.LOGGER.warn("Left out a second entry on the Arrancar roster for {}", id);
                continue;
            }
            Place place = new Place(id, entry.getString(SOUL_NAME));
            int numero = entry.getInt(NUMERO);
            if (numero >= ArrancarRank.FIRST_NUMERO && numero <= ArrancarRank.LAST_NUMERO && !numeros[numero]) {
                place.numero = numero;
                numeros[numero] = true;
            }
            place.strength = Math.max(0, entry.getInt(STRENGTH));
            place.seenAt = entry.getLong(SEEN_AT);
            int seat = entry.getInt(SEAT);
            if (isSeat(seat) && !seats[seat]) {
                place.seat = seat;
                seats[seat] = true;
            }
            int lostSeat = entry.getInt(LOST_SEAT);
            place.lostSeat = isSeat(lostSeat) ? lostSeat : 0;
            int pin = entry.getInt(PIN);
            if (isSeat(pin) && !pins[pin]) {
                place.pin = pin;
                pins[pin] = true;
            }
            loaded.places.put(id, place);
        }
        return loaded;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(LAST_NUMERO, lastNumero);
        ListTag list = new ListTag();
        for (Place place : places.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put(ID, NbtUtils.createUUID(place.id));
            entry.putString(SOUL_NAME, place.name);
            entry.putInt(NUMERO, place.numero);
            entry.putInt(STRENGTH, place.strength);
            entry.putLong(SEEN_AT, place.seenAt);
            entry.putInt(SEAT, place.seat);
            entry.putInt(LOST_SEAT, place.lostSeat);
            entry.putInt(PIN, place.pin);
            list.add(entry);
        }
        tag.put(SOULS, list);
        return tag;
    }

    // ---- The souls ----

    /** Notes this Arrancar now: adds it with a Número if new (the hint if free), refreshes name, strength and sight. */
    public void note(UUID id, String name, int numeroHint, float maxReiryoku, long now) {
        String shown = name == null ? "" : name;
        int strength = Math.max(0, Mth.floor(maxReiryoku));
        Place place = places.get(id);
        if (place == null) {
            // Numbered before it is added, so it can never take its own number back.
            place = new Place(id, shown);
            place.numero = give(numeroHint, now);
            place.strength = strength;
            place.seenAt = now;
            places.put(id, place);
            setDirty();
            return;
        }
        boolean changed = false;
        // A pin ends with a long absence, before the sight below would make the soul present again.
        if (place.pin != 0 && absent(place, now)) {
            place.pin = 0;
            changed = true;
        }
        if (!Objects.equals(place.name, shown)) {
            place.name = shown;
            changed = true;
        }
        if (place.strength != strength) {
            place.strength = strength;
            changed = true;
        }
        // Written as a comparison against now, so a time read from a damaged file cannot overflow the subtraction.
        if (place.seenAt <= now - SEEN_REFRESH_TICKS || now < place.seenAt) {
            place.seenAt = now;
            changed = true;
        }
        if (place.numero == 0) {
            int numero = give(numeroHint, now);
            if (numero != 0) {
                place.numero = numero;
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /** Takes a soul off the roster, returning whether it was on it. */
    public boolean remove(UUID id) {
        boolean removed = places.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /**
     * Deals the ten seats again: a soul away too long loses its pin, each pinned soul takes its pinned seat, then
     * the present, unpinned souls strong enough for a seat fill the free ones, the strongest the lowest. A soul that
     * loses its seat remembers it (the mark of a Privaron Espada), and one that gains a seat forgets any it lost.
     * Marks dirty only on a change.
     */
    public void reseat(long now) {
        boolean[] taken = new boolean[ArrancarRank.SEATS + 1];
        boolean changed = false;
        for (Place place : places.values()) {
            place.dealt = 0;
            // Staying away ends a pin, as it ends a seat: the soul must be pinned again on its return.
            if (place.pin > 0 && absent(place, now)) {
                place.pin = 0;
                changed = true;
            }
        }
        for (Place place : places.values()) {
            if (place.pin > 0 && !taken[place.pin]) {
                place.dealt = place.pin;
                taken[place.pin] = true;
            }
        }
        double needs = BleachConfig.get(BleachConfig.ESPADA_MIN_MAX_REIRYOKU);
        List<Place> candidates = new ArrayList<>();
        for (Place place : places.values()) {
            if (place.pin == 0 && !absent(place, now) && place.strength >= needs) {
                candidates.add(place);
            }
        }
        candidates.sort(BY_STRENGTH);
        int next = 1;
        for (Place place : candidates) {
            while (next <= ArrancarRank.SEATS && taken[next]) {
                next++;
            }
            if (next > ArrancarRank.SEATS) {
                break;
            }
            place.dealt = next;
            taken[next] = true;
        }
        for (Place place : places.values()) {
            if (place.dealt == place.seat) {
                continue;
            }
            if (place.seat > 0 && place.dealt == 0) {
                place.lostSeat = place.seat;
            }
            if (place.dealt > 0) {
                place.lostSeat = 0;
            }
            place.seat = place.dealt;
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    /** Reseats, then this soul's rank: its Número and its seat, Privaron number or 0. NONE if it is not on the roster. */
    public ArrancarRank rank(UUID id, long now) {
        reseat(now);
        Place place = places.get(id);
        return place == null ? ArrancarRank.NONE : rankOf(place, BleachConfig.get(BleachConfig.PRIVARON_ESPADA));
    }

    /**
     * Pins this soul to this seat (1..10), unpinning any other soul pinned there.
     *
     * @return false if not on the roster or out of range
     */
    public boolean pin(UUID id, int seat) {
        Place place = places.get(id);
        if (place == null || !isSeat(seat)) {
            return false;
        }
        for (Place other : places.values()) {
            if (other != place && other.pin == seat) {
                other.pin = 0;
                setDirty();
            }
        }
        if (place.pin != seat) {
            place.pin = seat;
            setDirty();
        }
        return true;
    }

    /** Unpins, and forgets its seat and any lost seat; false if it is not on the roster. */
    public boolean clear(UUID id) {
        Place place = places.get(id);
        if (place == null) {
            return false;
        }
        // Its seat goes too, so the next deal does not count it as a seat lost.
        if (place.pin != 0 || place.seat != 0 || place.lostSeat != 0) {
            place.pin = 0;
            place.seat = 0;
            place.lostSeat = 0;
            setDirty();
        }
        return true;
    }

    /** Reseats, then everyone: Espada by seat, then Privaron by number, then Números by number, then the unnumbered. */
    public List<Entry> entries(long now) {
        reseat(now);
        boolean privaron = BleachConfig.get(BleachConfig.PRIVARON_ESPADA);
        List<Entry> entries = new ArrayList<>(places.size());
        for (Place place : places.values()) {
            entries.add(new Entry(place.id, place.name, rankOf(place, privaron), place.strength, place.pin > 0,
                    absent(place, now)));
        }
        entries.sort(LISTED);
        return entries;
    }

    /**
     * One Arrancar on the roster, as listed.
     *
     * @param id       its soul's id
     * @param name     its player's name when last seen
     * @param rank     its rank now
     * @param strength its whole maximum reiryoku when last seen
     * @param pinned   whether it is seated by command
     * @param away     whether it has been away long enough to give up its seat
     */
    public record Entry(UUID id, String name, ArrancarRank rank, int strength, boolean pinned, boolean away) {
    }

    // ---- The souls' records ----

    /**
     * Keeps a real player's rank right (once a second, at become, after the rank command). Mock players, another
     * mod's fake players and clients: no-op, returning the record's rank, so game tests never touch the server's
     * roster and no machine takes a seat.
     */
    public static ArrancarRank keep(Player player, SoulData data, long now) {
        return player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)
                ? keep(player, data, get(serverPlayer.server), now) : data.hollow().arrancar().rank();
    }

    /**
     * The same against this roster, for any player (tests pass a fresh roster and a mock): an Arrancar is noted and
     * its rank read; any other soul is taken off. The record is rewritten only when the rank changed, which marks
     * the soul to be sent to its owner, and the Arrancar is told; a new Espada is granted its milestone.
     */
    public static ArrancarRank keep(Player player, SoulData data, ArrancarRoster roster, long now) {
        HollowProgress.Arrancar record = data.hollow().arrancar();
        ArrancarRank before = record.rank();
        ArrancarRank after;
        if (!ArrancarLogic.isArrancar(data)) {
            roster.remove(player.getUUID());
            after = ArrancarRank.NONE;
        } else {
            // The record's number is the hint, so a deleted roster file gives each soul its own back while it is free.
            roster.note(player.getUUID(), player.getGameProfile().getName(), before.numero(), data.maxReiryoku(), now);
            after = roster.rank(player.getUUID(), now);
        }
        if (after.equals(before)) {
            return after;
        }
        data.setHollow(data.hollow().withArrancar(record.withRank(after)));
        if (ArrancarLogic.isArrancar(data)) {
            tell(player, before, after);
        }
        if (after.kind() == ArrancarRank.Kind.ESPADA && player instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.ESPADA);
        }
        return after;
    }

    /**
     * Tells the Arrancar its new rank in chat, once: a seat in gold, with where its number is tattooed (it cannot see
     * its own back); a seat lost in grey; a Número in white; no number in grey. Any other change goes untold.
     */
    private static void tell(Player player, ArrancarRank before, ArrancarRank after) {
        Component message;
        if (after.kind() == ArrancarRank.Kind.ESPADA) {
            message = Component.translatable("message.bleach.arrancar.rank.espada", after.seat(),
                    ArrancarRank.seatName(after.seat()), ArrancarRank.tattooSpot(player.getUUID()).phrase())
                    .withStyle(ChatFormatting.GOLD);
        } else if (before.kind() == ArrancarRank.Kind.ESPADA) {
            message = Component.translatable("message.bleach.arrancar.rank.lost", after.label())
                    .withStyle(ChatFormatting.GRAY);
        } else if (after.kind() == ArrancarRank.Kind.NUMERO) {
            message = Component.translatable("message.bleach.arrancar.rank.numero", after.numero())
                    .withStyle(ChatFormatting.WHITE);
        } else if (after.kind() == ArrancarRank.Kind.NONE) {
            message = Component.translatable("message.bleach.arrancar.rank.unnumbered").withStyle(ChatFormatting.GRAY);
        } else {
            return;
        }
        player.displayClientMessage(message, false);
    }

    // ---- Helpers ----

    /** Its rank: its seat if it holds one, else the Privaron number of a seat it lost (where the server keeps them). */
    private static ArrancarRank rankOf(Place place, boolean privaron) {
        int seat = place.seat > 0 ? place.seat
                : privaron && place.lostSeat > 0 ? ArrancarRank.privaronNumber(place.lostSeat) : 0;
        return new ArrancarRank(place.numero, seat);
    }

    /**
     * The next Número for a soul: the hint if no soul holds it; else the first free one after the last given, coming
     * round from 99 to 11; else, with every one held, the number of the soul longest away (the lowest number among
     * those last seen at once), which is left without one; else none (0).
     */
    private int give(int hint, long now) {
        boolean[] held = new boolean[ArrancarRank.LAST_NUMERO + 1];
        for (Place place : places.values()) {
            held[place.numero] = true;
        }
        if (hint >= ArrancarRank.FIRST_NUMERO && hint <= ArrancarRank.LAST_NUMERO && !held[hint]) {
            return hint;
        }
        for (int i = 1; i <= NUMEROS; i++) {
            int numero = ArrancarRank.FIRST_NUMERO + Math.floorMod(lastNumero - ArrancarRank.FIRST_NUMERO + i, NUMEROS);
            if (!held[numero]) {
                lastNumero = numero;
                return numero;
            }
        }
        // Never by map order: between equal sights the lowest number goes, so the outcome does not hang on who came first.
        Place longest = null;
        for (Place place : places.values()) {
            if (place.numero > 0 && absent(place, now) && (longest == null || place.seenAt < longest.seenAt
                    || (place.seenAt == longest.seenAt && place.numero < longest.numero))) {
                longest = place;
            }
        }
        if (longest == null) {
            return 0;
        }
        int numero = longest.numero;
        longest.numero = 0;
        return numero;
    }

    /** Whether this soul has been away longer than the server allows; never, with {@code absent_hours} at 0. */
    private static boolean absent(Place place, long now) {
        int hours = BleachConfig.get(BleachConfig.RANK_ABSENT_HOURS);
        // now - seenAt > hours, written so a time read from a damaged file cannot overflow the subtraction.
        return hours > 0 && place.seenAt < now - hours * TICKS_PER_HOUR;
    }

    /** Where a kind of rank comes in the list. */
    private static int listedGroup(ArrancarRank rank) {
        return switch (rank.kind()) {
            case ESPADA -> 0;
            case PRIVARON -> 1;
            case NUMERO -> 2;
            case NONE -> 3;
        };
    }

    private static boolean isSeat(int seat) {
        return seat >= 1 && seat <= ArrancarRank.SEATS;
    }

    /** One soul on the roster, as it stands. Only the roster changes it. */
    private static final class Place {
        private final UUID id;
        private String name;
        /** Its Número, 11 to 99, or 0 without one. */
        private int numero;
        /** Whole maximum reiryoku when last seen. */
        private int strength;
        private long seenAt;
        /** The seat it holds, 1 to 10, or 0. */
        private int seat;
        /** The seat it last lost and has not won back since, 1 to 10, or 0. */
        private int lostSeat;
        /** The seat an operator pinned it to, 1 to 10, or 0. */
        private int pin;
        /** The seat the deal under way gives it; only {@link ArrancarRoster#reseat} reads it. */
        private int dealt;

        private Place(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
