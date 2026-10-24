package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.DataResult;
import io.github.masteryoga1.bleach.BleachMod;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Every Hollow Bait site and Hunter's contest in the world, kept in the Overworld's saved data so a
 * bait goes on drawing Hollows after a restart. Each site is read on its own, so one that cannot be
 * read is left out and the rest load. Alongside, and not saved, each site's boss bar, the rift that
 * cracked the sky over it and the souls who were there when it cracked.
 */
public final class BaitSites extends SavedData {
    public static final String NAME = "bleach_hollow_bait";
    private static final String SITES = "sites";

    private final Map<UUID, BaitSite> sites = new LinkedHashMap<>();
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Map<UUID, UUID> skyRifts = new HashMap<>();
    private final Map<UUID, List<UUID>> finaleWitnesses = new HashMap<>();

    public BaitSites() {
    }

    public static SavedData.Factory<BaitSites> factory() {
        return new SavedData.Factory<>(BaitSites::new, BaitSites::load, null);
    }

    public static BaitSites get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** Reads the sites one by one, leaving out (and logging) any that cannot be read. Never throws. */
    public static BaitSites load(CompoundTag tag, HolderLookup.Provider registries) {
        BaitSites loaded = new BaitSites();
        ListTag list = tag.getList(SITES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                DataResult<BaitSite> read = BaitSite.CODEC.parse(NbtOps.INSTANCE, entry);
                BaitSite site = read.result().orElse(null);
                if (site == null) {
                    read.resultOrPartial(error -> BleachMod.LOGGER.warn("Left out a Hollow Bait site that could not be read: {}",
                            error));
                } else if (!loaded.sites.containsKey(site.id())) {
                    loaded.sites.put(site.id(), site);
                }
            } catch (RuntimeException e) {
                BleachMod.LOGGER.warn("Left out a Hollow Bait site that could not be read", e);
            }
        }
        return loaded;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BaitSite site : sites.values()) {
            BaitSite.CODEC.encodeStart(NbtOps.INSTANCE, site)
                    .resultOrPartial(error -> BleachMod.LOGGER.warn("Could not save the Hollow Bait site {}: {}", site.id(),
                            error))
                    .ifPresent(list::add);
        }
        tag.put(SITES, list);
        return tag;
    }

    // ---- Sites ----

    /** Every site, in the order they were opened. Copy it before ending sites while going through it. */
    public Collection<BaitSite> all() {
        return Collections.unmodifiableCollection(sites.values());
    }

    @Nullable
    public BaitSite find(UUID id) {
        return sites.get(id);
    }

    /** The site this soul owns, bait or contest; a soul has at most one. */
    @Nullable
    public BaitSite ownedBy(UUID owner) {
        for (BaitSite site : sites.values()) {
            if (site.owner().equals(owner)) {
                return site;
            }
        }
        return null;
    }

    /** The first site this point is within, with {@code extra} blocks to spare (see {@link BaitSite#contains}). */
    @Nullable
    public BaitSite containing(Vec3 pos, double extra) {
        for (BaitSite site : sites.values()) {
            if (site.contains(pos, extra)) {
                return site;
            }
        }
        return null;
    }

    /** Whether any site's centre is within this distance of the spot, across the ground. */
    public boolean anyCentreWithin(BlockPos pos, double distance) {
        for (BaitSite site : sites.values()) {
            double dx = site.centre().getX() - pos.getX();
            double dz = site.centre().getZ() - pos.getZ();
            if (dx * dx + dz * dz <= distance * distance) {
                return true;
            }
        }
        return false;
    }

    public void add(BaitSite site) {
        sites.put(site.id(), site);
        setDirty();
    }

    /** Removes the site, and takes down its boss bar and forgets its sky and witnesses with it. */
    public void remove(UUID id) {
        if (sites.remove(id) != null) {
            setDirty();
        }
        dropBar(id);
        skyRifts.remove(id);
        finaleWitnesses.remove(id);
    }

    // ---- Not saved ----

    /** The site's boss bar, made the first time it is asked for: a bait's blue, a contest's white. */
    public ServerBossEvent bar(BaitSite site) {
        return bars.computeIfAbsent(site.id(), id -> site.contest()
                ? new ServerBossEvent(Component.translatable("bossbar.bleach.contest", 0, 0), BossEvent.BossBarColor.WHITE,
                        BossEvent.BossBarOverlay.NOTCHED_10)
                : new ServerBossEvent(Component.translatable("bossbar.bleach.bait"), BossEvent.BossBarColor.BLUE,
                        BossEvent.BossBarOverlay.PROGRESS));
    }

    /** Takes the site's boss bar down from everyone who sees it, and forgets it. */
    public void dropBar(UUID id) {
        ServerBossEvent bar = bars.remove(id);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** Takes this player off every site's boss bar, as they leave. */
    public void removeFromBars(ServerPlayer player) {
        for (ServerBossEvent bar : bars.values()) {
            bar.removePlayer(player);
        }
    }

    /** The rift that cracked the sky over this site, if it is cracked and the rift was opened since the server started. */
    @Nullable
    public UUID skyRift(UUID site) {
        return skyRifts.get(site);
    }

    public void setSkyRift(UUID site, @Nullable UUID rift) {
        if (rift == null) {
            skyRifts.remove(site);
        } else {
            skyRifts.put(site, rift);
        }
    }

    /** The souls who were hunted at this site when its sky cracked, for the milestones; empty if not known. */
    public List<UUID> finaleWitnesses(UUID site) {
        return finaleWitnesses.getOrDefault(site, List.of());
    }

    public void setFinaleWitnesses(UUID site, List<UUID> souls) {
        finaleWitnesses.put(site, List.copyOf(souls));
    }
}
