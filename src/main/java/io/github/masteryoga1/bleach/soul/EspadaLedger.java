package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The Espada's side of what they carry and who serves them, in the overworld's data like {@link ArrancarRoster}:
 * for each Espada by its soul's id, its Caja Negación (whether one was given since it took its seat, when it can
 * next be used, and since when it has gone without one) and its Fracción (each Número by id, in pledge order, and
 * when the list was last forgotten). It is never sent to a client.
 *
 * <p>The cube is given, given again and crumbled once a second by {@link #keep}. A pledge itself lives on the
 * Número ({@code Pledged}, {@code PledgedAt} and {@code Leader}); this list is the count for the cap, and the one
 * way an Espada that stops being an Arrancar reaches a Fracción in a chunk that is not loaded: its list is dropped
 * and the time stamped, and each Número pledged at or before that time leaves when it next looks
 * ({@link io.github.masteryoga1.bleach.entity.ArrancarNpc}). A Número that finds itself missing for any other
 * reason (a lost file, entity data from a backup) lists itself again while there is room.
 */
public final class EspadaLedger extends SavedData {
    public static final String NAME = "bleach_espada_ledger";

    private static final String ESPADA = "Espada";
    private static final String ID = "Id";
    private static final String CAJA_ISSUED = "CajaIssued";
    private static final String CAJA_READY_AT = "CajaReadyAt";
    private static final String CAJA_MISSING_SINCE = "CajaMissingSince";
    private static final String FRACCION_FORGOTTEN_AT = "FraccionForgottenAt";
    private static final String FRACCION = "Fraccion";
    private static final String NUMERO = "Numero";

    private static final String GIVEN = "message.bleach.caja_negacion.given";
    private static final String REISSUED = "message.bleach.caja_negacion.reissued";
    private static final String CRUMBLED = "message.bleach.caja_negacion.crumbled";
    private static final String RELEASED = "message.bleach.fraccion.released";

    /** Unset times: the cube has not gone missing, the list was never forgotten. */
    private static final long UNSET = -1L;

    // In the order the Espada were first noted, which is the order they are saved in.
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /** One Número serving an Espada: its id, and its number when it pledged. */
    public record Member(UUID id, int numero) {
    }

    /** An empty ledger. The server keeps its own ({@link #get}); game tests make fresh ones. */
    public EspadaLedger() {
    }

    public static SavedData.Factory<EspadaLedger> factory() {
        return new SavedData.Factory<>(EspadaLedger::new, EspadaLedger::load, null);
    }

    public static EspadaLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /**
     * Reads the Espada one by one, leaving out (and logging) any entry whose id cannot be read or was read already
     * and any member whose id cannot be read; a member listed twice counts once, and a missing or damaged field takes
     * its default (not issued, ready, not missing, never forgotten). Never throws.
     */
    public static EspadaLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        EspadaLedger loaded = new EspadaLedger();
        // Empty when the list is missing or not a list of compounds.
        ListTag list = tag.getList(ESPADA, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            if (!saved.hasUUID(ID)) {
                BleachMod.LOGGER.warn("Left out an Espada in the ledger with no id that could be read");
                continue;
            }
            UUID id = saved.getUUID(ID);
            if (loaded.entries.containsKey(id)) {
                BleachMod.LOGGER.warn("Left out a second entry in the Espada ledger for {}", id);
                continue;
            }
            Entry entry = new Entry();
            entry.cajaIssued = saved.getBoolean(CAJA_ISSUED);
            entry.cajaReadyAt = Math.max(0L, readLong(saved, CAJA_READY_AT, 0L));
            entry.cajaMissingSince = Math.max(UNSET, readLong(saved, CAJA_MISSING_SINCE, UNSET));
            entry.fraccionForgottenAt = Math.max(UNSET, readLong(saved, FRACCION_FORGOTTEN_AT, UNSET));
            ListTag members = saved.getList(FRACCION, Tag.TAG_COMPOUND);
            for (int j = 0; j < members.size(); j++) {
                CompoundTag member = members.getCompound(j);
                if (!member.hasUUID(ID)) {
                    BleachMod.LOGGER.warn("Left out a Fracción of {} in the Espada ledger with no id that could be read",
                            id);
                    continue;
                }
                UUID npc = member.getUUID(ID);
                if (indexOf(entry.fraccion, npc) < 0) {
                    entry.fraccion.add(new Member(npc, numero(member.getInt(NUMERO))));
                }
            }
            if (!entry.isEmpty()) {
                loaded.entries.put(id, entry);
            }
        }
        return loaded;
    }

    /** Writes every Espada with anything to remember; an entry with nothing in it is left out. */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> pair : entries.entrySet()) {
            Entry entry = pair.getValue();
            if (entry.isEmpty()) {
                continue;
            }
            CompoundTag saved = new CompoundTag();
            saved.putUUID(ID, pair.getKey());
            saved.putBoolean(CAJA_ISSUED, entry.cajaIssued);
            saved.putLong(CAJA_READY_AT, entry.cajaReadyAt);
            saved.putLong(CAJA_MISSING_SINCE, entry.cajaMissingSince);
            saved.putLong(FRACCION_FORGOTTEN_AT, entry.fraccionForgottenAt);
            ListTag members = new ListTag();
            for (Member member : entry.fraccion) {
                CompoundTag written = new CompoundTag();
                written.putUUID(ID, member.id());
                written.putInt(NUMERO, member.numero());
                members.add(written);
            }
            saved.put(FRACCION, members);
            list.add(saved);
        }
        tag.put(ESPADA, list);
        return tag;
    }

    // ---- The upkeep ----

    /** Whether this player has a soul whose rank is a seat among the Espada. */
    public static boolean isSeatedEspada(Player player) {
        return SoulLogic.hasSoul(player) && seated(SoulLogic.get(player));
    }

    /**
     * The once-a-second upkeep for a real player (from {@link ArrancarEvents#reconcile}), against the server's
     * ledger. Mock players, another mod's fake players and clients: no-op, so game tests that tick older mocks never
     * give them a cube or touch the server's ledger.
     */
    public static void keep(Player player, SoulData data, long now) {
        if (player instanceof ServerPlayer serverPlayer && !(player instanceof FakePlayer)) {
            keep(player, data, get(serverPlayer.server), now);
        }
    }

    /**
     * The same against this ledger, for any player (tests pass a mock and a fresh ledger): the cube is kept to one in
     * the hands of a seated Espada and crumbles in anyone else's ({@link #keepCube}), and a soul that is no longer an
     * Arrancar has its Fracción list forgotten, so every one of them leaves, and is told. Server side.
     */
    public static void keep(Player player, SoulData data, EspadaLedger ledger, long now) {
        if (player.level().isClientSide()) {
            return;
        }
        keepCube(player, data, ledger, now);
        // Dead or alive: the Fracción of a soul cleansed while dead leave as surely.
        if (!ArrancarLogic.isArrancar(data) && ledger.forgetFraccion(player.getUUID(), now)) {
            player.displayClientMessage(Component.translatable(RELEASED).withStyle(ChatFormatting.GRAY), false);
        }
    }

    /**
     * Keeps the cube right, as {@link ArrancarEvents#reconcileBlades} keeps the blade: a seated Espada (while the
     * Caja is on) keeps its first cube and loses any other, and anyone else loses every one, told once. A seated
     * Espada alive and not spectating that holds none is given one the first time, and given another after
     * {@code reissue_minutes} without (never at 0). Losing the seat forgets that one was given, so a new seat brings a
     * new cube.
     */
    private static void keepCube(Player player, SoulData data, EspadaLedger ledger, long now) {
        UUID id = player.getUUID();
        boolean seated = BleachConfig.get(BleachConfig.CAJA_ENABLED) && seated(data);
        Inventory inventory = player.getInventory();
        boolean found = false;
        boolean crumbled = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(ModItems.CAJA_NEGACION.get())) {
                continue;
            }
            if (!seated || found) {
                inventory.setItem(slot, ItemStack.EMPTY);
                crumbled = true;
                continue;
            }
            found = true;
            // Only one cube each, even in a stack a command made bigger.
            if (stack.getCount() > 1) {
                stack.setCount(1);
                crumbled = true;
            }
        }
        if (crumbled) {
            player.displayClientMessage(Component.translatable(CRUMBLED).withStyle(ChatFormatting.GRAY), true);
        }
        if (!seated) {
            ledger.setCajaIssued(id, false);
            ledger.setCajaMissingSince(id, UNSET);
            return;
        }
        if (found) {
            // One held while seated counts as the one given, a creative copy too, so losing it starts the clock.
            ledger.setCajaIssued(id, true);
            ledger.setCajaMissingSince(id, UNSET);
            return;
        }
        // Nothing is given into a dead or spectating inventory, and the clock neither starts nor runs out.
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        if (!ledger.cajaIssued(id)) {
            inventory.placeItemBackInInventory(new ItemStack(ModItems.CAJA_NEGACION.get()));
            ledger.setCajaIssued(id, true);
            ledger.setCajaMissingSince(id, UNSET);
            player.displayClientMessage(Component.translatable(GIVEN).withStyle(ChatFormatting.GOLD), false);
            return;
        }
        int minutes = BleachConfig.get(BleachConfig.CAJA_REISSUE_MINUTES);
        if (minutes <= 0) {
            ledger.setCajaMissingSince(id, UNSET);
            return;
        }
        long since = ledger.cajaMissingSince(id);
        // A time ahead of now (a damaged file) starts again from now.
        if (since < 0L || since > now) {
            ledger.setCajaMissingSince(id, now);
            return;
        }
        if (now - since < (long) minutes * MenosEvolution.MINUTE_TICKS) {
            return;
        }
        inventory.placeItemBackInInventory(new ItemStack(ModItems.CAJA_NEGACION.get()));
        ledger.setCajaMissingSince(id, UNSET);
        player.displayClientMessage(Component.translatable(REISSUED).withStyle(ChatFormatting.GRAY), true);
    }

    // ---- The cube ----

    /** Whether this Espada has been given a cube since it took its seat. */
    public boolean cajaIssued(UUID espada) {
        Entry entry = entries.get(espada);
        return entry != null && entry.cajaIssued;
    }

    public void setCajaIssued(UUID espada, boolean issued) {
        if (cajaIssued(espada) == issued) {
            return;
        }
        entryFor(espada).cajaIssued = issued;
        changed(espada);
    }

    /** The game time this Espada's cube can next be used; 0 when it is ready. */
    public long cajaReadyAt(UUID espada) {
        Entry entry = entries.get(espada);
        return entry != null ? entry.cajaReadyAt : 0L;
    }

    public void setCajaReadyAt(UUID espada, long time) {
        if (cajaReadyAt(espada) == time) {
            return;
        }
        entryFor(espada).cajaReadyAt = time;
        changed(espada);
    }

    /** The game time this seated Espada was first seen without its cube; -1 while it has one or is not missing it. */
    public long cajaMissingSince(UUID espada) {
        Entry entry = entries.get(espada);
        return entry != null ? entry.cajaMissingSince : UNSET;
    }

    public void setCajaMissingSince(UUID espada, long time) {
        if (cajaMissingSince(espada) == time) {
            return;
        }
        entryFor(espada).cajaMissingSince = time;
        changed(espada);
    }

    // ---- The Fracción ----

    /** This Espada's Fracción, in the order they pledged: an unmodifiable copy, empty for none. */
    public List<Member> fraccion(UUID espada) {
        Entry entry = entries.get(espada);
        return entry != null ? List.copyOf(entry.fraccion) : List.of();
    }

    /** Whether this Número is listed as this Espada's Fracción. */
    public boolean isMember(UUID espada, UUID npc) {
        Entry entry = entries.get(espada);
        return entry != null && indexOf(entry.fraccion, npc) >= 0;
    }

    /**
     * Lists this Número as this Espada's Fracción, at the end, or puts its number right if it is listed already. A
     * Número serves one Espada, so it is taken off any other's list. The cap is the callers' to check.
     */
    public void addMember(UUID espada, UUID npc, int numero) {
        Iterator<Map.Entry<UUID, Entry>> others = entries.entrySet().iterator();
        while (others.hasNext()) {
            Map.Entry<UUID, Entry> pair = others.next();
            int other = pair.getKey().equals(espada) ? -1 : indexOf(pair.getValue().fraccion, npc);
            if (other >= 0) {
                pair.getValue().fraccion.remove(other);
                if (pair.getValue().isEmpty()) {
                    others.remove();
                }
                setDirty();
            }
        }
        Member member = new Member(npc, numero(numero));
        Entry entry = entryFor(espada);
        int index = indexOf(entry.fraccion, npc);
        if (index < 0) {
            entry.fraccion.add(member);
        } else if (!entry.fraccion.get(index).equals(member)) {
            entry.fraccion.set(index, member);
        } else {
            return;
        }
        setDirty();
    }

    /** Takes this Número off this Espada's list, returning whether it was on it. */
    public boolean removeMember(@Nullable UUID espada, UUID npc) {
        Entry entry = espada != null ? entries.get(espada) : null;
        if (entry == null) {
            return false;
        }
        int index = indexOf(entry.fraccion, npc);
        if (index < 0) {
            return false;
        }
        entry.fraccion.remove(index);
        changed(espada);
        return true;
    }

    /**
     * Drops this Espada's whole Fracción list, stamping {@code fraccionForgottenAt = now} if any were listed, so each
     * one pledged at or before now leaves when it next looks, loaded or not. Returns whether any were listed.
     */
    public boolean forgetFraccion(UUID espada, long now) {
        Entry entry = entries.get(espada);
        if (entry == null || entry.fraccion.isEmpty()) {
            return false;
        }
        entry.fraccion.clear();
        entry.fraccionForgottenAt = now;
        setDirty();
        return true;
    }

    /** When this Espada's list was last forgotten; -1 if never. A Fracción pledged at or before it departs. */
    public long fraccionForgottenAt(UUID espada) {
        Entry entry = entries.get(espada);
        return entry != null ? entry.fraccionForgottenAt : UNSET;
    }

    // ---- Helpers ----

    private static boolean seated(SoulData data) {
        return ArrancarRank.of(data).kind() == ArrancarRank.Kind.ESPADA;
    }

    private Entry entryFor(UUID espada) {
        return entries.computeIfAbsent(espada, id -> new Entry());
    }

    /** Marks the ledger to be saved, and forgets an entry left with nothing in it, so the map holds only what counts. */
    private void changed(UUID espada) {
        Entry entry = entries.get(espada);
        if (entry != null && entry.isEmpty()) {
            entries.remove(espada);
        }
        setDirty();
    }

    private static int indexOf(List<Member> fraccion, UUID npc) {
        for (int i = 0; i < fraccion.size(); i++) {
            if (fraccion.get(i).id().equals(npc)) {
                return i;
            }
        }
        return -1;
    }

    /** A Número, 11 to 99, or 0 for anything else. */
    private static int numero(int numero) {
        return numero >= ArrancarRank.FIRST_NUMERO && numero <= ArrancarRank.LAST_NUMERO ? numero : 0;
    }

    /** Any number will do, so a hand-edited int still reads; a missing or non-numeric one is the fallback. */
    private static long readLong(CompoundTag tag, String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    /** One Espada's record, as it stands. Only the ledger changes it. */
    private static final class Entry {
        private boolean cajaIssued;
        private long cajaReadyAt;
        private long cajaMissingSince = UNSET;
        private final List<Member> fraccion = new ArrayList<>();
        private long fraccionForgottenAt = UNSET;

        /** Nothing to remember: never given a cube, ready, not missing one, no Fracción and never forgotten. */
        private boolean isEmpty() {
            return !cajaIssued && cajaReadyAt <= 0L && cajaMissingSince == UNSET && fraccion.isEmpty()
                    && fraccionForgottenAt == UNSET;
        }
    }
}
