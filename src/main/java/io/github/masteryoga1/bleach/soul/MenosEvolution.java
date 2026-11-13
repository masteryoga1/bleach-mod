package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A Hollow player rising through the Menos Grande by what it devours: a Hollow holding enough souls
 * swells into a Gillian; a Gillian that devours enough Hollows, Menos among them, wins its own will back
 * as an Adjuchas; and an Adjuchas destined for it that devours enough Adjuchas becomes a Vasto Lorde, while
 * the server has room for one more. An Adjuchas must keep devouring Hollows, or it sinks back into a
 * Gillian (see {@link HollowTier.Regression}). The counts live in {@link HollowProgress.Evolution}; what each
 * form does to the body is in {@link MenosBody}. Server side; everything takes a {@link Player}, so game
 * tests can use mock players, and only messages, sounds and advancements need a real one.
 *
 * <p>In canon a Gillian is countless Hollows fused into one, until one will among them devours its way out
 * on top; the Adjuchas that reach Vasto Lorde are few enough to count on one's hands, and were never simply
 * the ones that ate the most.
 */
public final class MenosEvolution {
    /** Ticks in a minute of the regression clock. */
    public static final int MINUTE_TICKS = 1_200;
    /** Minutes left at which an unfed Adjuchas is warned. */
    private static final int[] WARN_AT_MINUTES_LEFT = {5, 1};
    private static final int EVOLVE_PARTICLES = 40;

    private MenosEvolution() {
    }

    // ---- Meals and the gates ----

    /**
     * What a meal does for the road as a Menos, at the end of {@link DevourEvents#reward}: a Hollow of any
     * sort counts for the form and resets the regression clock, a Menos of the devourer's own class or above
     * counts as one, and the next form is then tried.
     */
    public static void afterMeal(Player devourer, SoulData data, DevourLogic.Meal meal, long now) {
        if (!HollowPlayerLogic.isHollow(data)) {
            return;
        }
        HollowProgress.Evolution evolution = data.hollow().evolution();
        if (meal.hollowLike()) {
            evolution = evolution.withTierHollows(evolution.tierHollows() + 1).withHungerMinutes(0);
            data.menosHungerTicks = 0;
        }
        if (countsAsMenos(HollowTier.of(data), meal.kind())) {
            evolution = evolution.withTierMenos(evolution.tierMenos() + 1);
        }
        data.setHollow(data.hollow().withEvolution(evolution));
        tryEvolve(devourer, data, now);
    }

    /**
     * The form this soul's counts and the server's rules let it rise to now, or null. Only the counts and
     * the regression mode are asked here: destiny and the server's room for a Vasto Lorde are
     * {@link #tryEvolve}'s.
     */
    @Nullable
    public static HollowTier nextTier(SoulData data) {
        // HollowTier.of reads a plain Hollow for any other soul, whose kept record must never pass the Gillian gate.
        if (!HollowPlayerLogic.isHollow(data)) {
            return null;
        }
        HollowProgress hollow = data.hollow();
        HollowProgress.Evolution evolution = hollow.evolution();
        HollowTier.Regression mode = BleachConfig.get(BleachConfig.ADJUCHAS_REGRESSION);
        return switch (HollowTier.of(data)) {
            case HOLLOW -> hollow.souls() >= BleachConfig.get(BleachConfig.GILLIAN_SOULS)
                    && hollow.totalHollows() >= BleachConfig.get(BleachConfig.GILLIAN_HOLLOWS) ? HollowTier.GILLIAN : null;
            case GILLIAN -> {
                if (mode == HollowTier.Regression.CANON && evolution.regressions() >= 1) {
                    // In canon a will that has drowned among the souls within never rises again.
                    yield null;
                }
                // Each fall makes the climb longer, so sinking back and rising again is no way to farm the rise.
                long needed = (long) BleachConfig.get(BleachConfig.ADJUCHAS_HOLLOWS)
                        * (mode == HollowTier.Regression.SOFT ? 1L + evolution.regressions() : 1L);
                boolean met = evolution.tierHollows() >= needed
                        && evolution.tierMenos() >= BleachConfig.get(BleachConfig.ADJUCHAS_MENOS);
                yield met ? HollowTier.ADJUCHAS : null;
            }
            case ADJUCHAS -> evolution.tierHollows() >= BleachConfig.get(BleachConfig.VASTO_HOLLOWS)
                    && evolution.tierMenos() >= BleachConfig.get(BleachConfig.VASTO_ADJUCHAS) ? HollowTier.VASTO_LORDE : null;
            case VASTO_LORDE -> null;
        };
    }

    /** Whether a meal of this kind counts as a Menos for this form: any Menos for a Gillian, only an Adjuchas for one. */
    private static boolean countsAsMenos(HollowTier devourer, @Nullable HollowKind kind) {
        return switch (devourer) {
            case GILLIAN -> kind == HollowKind.GILLIAN || kind == HollowKind.ADJUCHAS;
            case ADJUCHAS -> kind == HollowKind.ADJUCHAS;
            case HOLLOW, VASTO_LORDE -> false;
        };
    }

    /**
     * Whether the soul with this id is destined to become a Vasto Lorde in the world with this seed, when
     * this share of souls is: fixed for each player in each world, so devouring more never changes it, and
     * a soul destined at one share is destined at every greater one.
     */
    public static boolean destined(UUID id, long seed, double share) {
        long mixed = seed ^ id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 32);
        return RandomSource.create(mixed).nextDouble() < share;
    }

    /** Whether this soul is destined in the world with this seed at the server's share: by default, every soul. */
    public static boolean destined(UUID id, long seed) {
        return destined(id, seed, BleachConfig.get(BleachConfig.VASTO_DESTINED_SHARE));
    }

    /** Whether this player is destined to become a Vasto Lorde in its world; false off the server. */
    public static boolean destined(Player player) {
        return player.level() instanceof ServerLevel level && destined(player.getUUID(), level.getSeed());
    }

    /** Whether the server has room for this soul as a Vasto Lorde: it is one already, or fewer than the most are. */
    public static boolean vastoRoom(@Nullable MinecraftServer server, UUID id) {
        if (server == null) {
            return false;
        }
        VastoLordes lordes = VastoLordes.get(server);
        return lordes.contains(id) || lordes.count() < BleachConfig.get(BleachConfig.VASTO_MAX);
    }

    /**
     * Raises the soul to its next form if its counts allow it. A Vasto Lorde also needs a destined soul and
     * room on the server; without either the Adjuchas is told once why it grows no further.
     */
    public static void tryEvolve(Player player, SoulData data, long now) {
        HollowTier next = nextTier(data);
        if (next == null) {
            return;
        }
        if (next == HollowTier.VASTO_LORDE) {
            if (!destined(player)) {
                stall(player, data, HollowProgress.Evolution.STALL_UNDESTINED);
                return;
            }
            if (!vastoRoom(player.level().getServer(), player.getUUID())) {
                stall(player, data, HollowProgress.Evolution.STALL_FULL);
                return;
            }
        }
        evolve(player, data, next, now);
    }

    /**
     * The soul becomes this form: its counts start again, the first visit to a form adds to its maximum
     * reiryoku once, the body is put right (the size follows tick by tick) and healed in full, and it is
     * told, heard and seen by those who can see spirits. An Adjuchas learns at once whether it can go
     * further. Commands set the form without this (see {@link #tick}).
     */
    public static void evolve(Player player, SoulData data, HollowTier to, long now) {
        HollowProgress.Evolution before = data.hollow().evolution();
        boolean firstVisit = to.tier() > before.peakTier();
        boolean undestined = to == HollowTier.ADJUCHAS && !destined(player);
        HollowProgress.Evolution after = before.startedAt(to.tier());
        data.setTier(PowerPath.HOLLOW, to.tier());
        data.setHollow(data.hollow().withEvolution(undestined ? after.withStall(HollowProgress.Evolution.STALL_UNDESTINED)
                : after));
        data.menosHungerTicks = 0;
        if (firstVisit) {
            SoulLogic.grow(data, data.maxReiryoku() * to.reiryokuGrowth());
        }
        HollowBody.reconcile(player, data);
        player.setHealth(player.getMaxHealth());
        MinecraftServer server = player.level().getServer();
        if (to == HollowTier.VASTO_LORDE && server != null) {
            VastoLordes.get(server).add(player.getUUID());
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.sendSystemMessage(Component.translatable("message.bleach.menos." + to.getSerializedName())
                .withStyle(ChatFormatting.DARK_PURPLE));
        if (undestined) {
            serverPlayer.sendSystemMessage(Component.translatable("message.bleach.menos.adjuchas.undestined")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        SpiritSenses.play(serverPlayer, sound(to), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        SpiritSenses.particles(serverPlayer.serverLevel(), ParticleTypes.SCULK_SOUL, serverPlayer.getX(),
                serverPlayer.getY(0.5), serverPlayer.getZ(), EVOLVE_PARTICLES, serverPlayer.getBbWidth() / 2.0,
                serverPlayer.getBbHeight() / 2.0, serverPlayer.getBbWidth() / 2.0, 0.02);
        Milestones.grant(serverPlayer, milestone(to));
        Milestones.check(serverPlayer, data);
    }

    /**
     * An Adjuchas starved of Hollows sinks back into a Gillian: the form and its counts start again, the
     * fall is counted, and the body grows back to a Gillian's over the next few seconds, room allowing.
     */
    public static void regress(Player player, SoulData data, long now) {
        HollowProgress.Evolution evolution = data.hollow().evolution();
        data.setTier(PowerPath.HOLLOW, HollowTier.GILLIAN.tier());
        data.setHollow(data.hollow().withEvolution(evolution.startedAt(HollowTier.GILLIAN.tier())
                .withRegressions(evolution.regressions() + 1)));
        data.menosHungerTicks = 0;
        HollowBody.reconcile(player, data);
        if (player instanceof ServerPlayer server) {
            boolean lost = BleachConfig.get(BleachConfig.ADJUCHAS_REGRESSION) == HollowTier.Regression.CANON;
            server.sendSystemMessage(Component.translatable(lost ? "message.bleach.menos.regressed.lost"
                    : "message.bleach.menos.regressed").withStyle(ChatFormatting.DARK_RED));
            SpiritSenses.play(server, ModSounds.GILLIAN_AMBIENT.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        }
    }

    // ---- The record and the regression clock ----

    /**
     * Each body tick of a Hollow, from {@link HollowBody#tickBody}: a record kept for another form than the
     * soul's (a command set it, or it sank back) starts again, an Adjuchas learning its destiny; then an
     * unfed Adjuchas's clock runs, in played ticks and only where Hollows can come, a minute at a time.
     */
    static void tick(Player player, SoulData data, long now) {
        HollowTier tier = HollowTier.of(data);
        HollowProgress.Evolution evolution = data.hollow().evolution();
        if (evolution.atTier() != tier.tier()) {
            HollowProgress.Evolution restarted = evolution.startedAt(tier.tier());
            if (tier == HollowTier.ADJUCHAS && !destined(player)) {
                restarted = restarted.withStall(HollowProgress.Evolution.STALL_UNDESTINED);
            }
            data.setHollow(data.hollow().withEvolution(restarted));
            data.menosHungerTicks = 0;
        }
        if (!clockApplies(data) || !clockRuns(player)) {
            return;
        }
        if (++data.menosHungerTicks >= MINUTE_TICKS) {
            data.menosHungerTicks = 0;
            onMinute(player, data, now);
        }
    }

    /**
     * Whether an Adjuchas's clock may run for this player now: alive, under survival's rules, while the
     * game runs, and where Hollows come for it: the Overworld, off Peaceful, with mob spawning, Garganta
     * arrivals and Hollow players' arrivals on, or Hueco Mundo's dunes and Forest of Menos while its own
     * Hollows spawn there. So it is never punished where there is nothing to eat.
     */
    public static boolean clockRuns(Player player) {
        Level level = player.level();
        if (!HollowPlayerLogic.survivalRules(player) || !player.isAlive() || !level.tickRateManager().runsNormally()) {
            return false;
        }
        boolean mobSpawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        if (level.dimension() == BleachWorlds.HUECO_MUNDO) {
            // A server that spawns no monsters (Peaceful, or its spawn-monsters property off) spawns none there either.
            MinecraftServer server = level.getServer();
            return HuecoMundoSpawns.allowed(level.getDifficulty(), mobSpawning)
                    && server != null && server.isSpawningMonsters()
                    && HuecoMundoSpawns.hollowsLiveAt(level, player.blockPosition());
        }
        return HollowLogic.worldAllowsArrivals(level.getDifficulty(), mobSpawning, level.dimension() == Level.OVERWORLD)
                && BleachConfig.get(BleachConfig.HOLLOW_ARRIVAL_MULTIPLIER) > 0.0;
    }

    /**
     * A whole minute without a Hollow, counted into the saved record: the Adjuchas is warned as the end
     * draws near, and at the end sinks back into a Gillian. Nothing for any other form, or with regression off.
     */
    public static void onMinute(Player player, SoulData data, long now) {
        if (!clockApplies(data)) {
            return;
        }
        HollowProgress.Evolution evolution = data.hollow().evolution();
        data.setHollow(data.hollow().withEvolution(evolution.withHungerMinutes(evolution.hungerMinutes() + 1)));
        int left = minutesLeft(data);
        if (left <= 0) {
            regress(player, data, now);
            return;
        }
        for (int warning : WARN_AT_MINUTES_LEFT) {
            if (left == warning && player instanceof ServerPlayer server) {
                server.sendSystemMessage(Component.translatable("message.bleach.menos.fading", left)
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    /** Whole minutes of play an Adjuchas has left to devour a Hollow before it sinks back. */
    public static int minutesLeft(SoulData data) {
        return Math.max(0, BleachConfig.get(BleachConfig.REGRESS_MINUTES) - data.hollow().evolution().hungerMinutes());
    }

    /**
     * Keeps this player's place among the server's Vasto Lorde right: taken while it is one, or an Arrancar
     * risen from one, given up once it is neither (purified, reset or set lower by command). Every so often
     * for every soul, so no rule that changes a form needs to remember the count.
     */
    static void noteVasto(Player player, SoulData data) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        VastoLordes lordes = VastoLordes.get(server);
        if (HollowTier.of(data) == HollowTier.VASTO_LORDE || ArrancarLogic.origin(data) == HollowTier.VASTO_LORDE) {
            lordes.add(player.getUUID());
        } else {
            lordes.remove(player.getUUID());
        }
    }

    /** Whether the regression clock is kept for this soul at all: an Adjuchas, with regression on. */
    private static boolean clockApplies(SoulData data) {
        return HollowTier.of(data) == HollowTier.ADJUCHAS
                && BleachConfig.get(BleachConfig.ADJUCHAS_REGRESSION) != HollowTier.Regression.OFF;
    }

    /** Records why an Adjuchas grows no further, telling it only when that changes. */
    private static void stall(Player player, SoulData data, int stall) {
        HollowProgress.Evolution evolution = data.hollow().evolution();
        if (evolution.stall() == stall) {
            return;
        }
        data.setHollow(data.hollow().withEvolution(evolution.withStall(stall)));
        if (player instanceof ServerPlayer server) {
            String key = stall == HollowProgress.Evolution.STALL_FULL
                    ? "message.bleach.menos.vasto_full" : "message.bleach.menos.adjuchas.undestined";
            server.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static SoundEvent sound(HollowTier form) {
        return switch (form) {
            case HOLLOW, GILLIAN -> ModSounds.GILLIAN_AMBIENT.get();
            case ADJUCHAS -> ModSounds.ADJUCHAS_AMBIENT.get();
            case VASTO_LORDE -> ModSounds.FISHBONE_D_ROAR.get();
        };
    }

    private static String milestone(HollowTier form) {
        return switch (form) {
            case HOLLOW, GILLIAN -> Milestones.GILLIAN;
            case ADJUCHAS -> Milestones.ADJUCHAS;
            case VASTO_LORDE -> Milestones.VASTO_LORDE;
        };
    }

    /**
     * The souls who are Vasto Lorde on this server, online or not, kept in the Overworld's saved data so
     * the server's limit holds across restarts. Each player keeps its own place right (see
     * {@link #noteVasto}); one who never comes back keeps it until the limit is raised.
     */
    public static final class VastoLordes extends SavedData {
        public static final String NAME = "bleach_vasto_lordes";
        private static final String SOULS = "souls";

        private final Set<UUID> souls = new LinkedHashSet<>();

        public VastoLordes() {
        }

        public static SavedData.Factory<VastoLordes> factory() {
            return new SavedData.Factory<>(VastoLordes::new, VastoLordes::load, null);
        }

        public static VastoLordes get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
        }

        /** Reads the souls one by one, leaving out (and logging) any that cannot be read. Never throws. */
        public static VastoLordes load(CompoundTag tag, HolderLookup.Provider registries) {
            VastoLordes loaded = new VastoLordes();
            ListTag list = tag.getList(SOULS, Tag.TAG_INT_ARRAY);
            for (Tag entry : list) {
                try {
                    loaded.souls.add(NbtUtils.loadUUID(entry));
                } catch (IllegalArgumentException e) {
                    BleachMod.LOGGER.warn("Left out a Vasto Lorde that could not be read: {}", e.getMessage());
                }
            }
            return loaded;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (UUID id : souls) {
                list.add(NbtUtils.createUUID(id));
            }
            tag.put(SOULS, list);
            return tag;
        }

        public int count() {
            return souls.size();
        }

        public boolean contains(UUID id) {
            return souls.contains(id);
        }

        /** @return whether the soul was not counted until now */
        public boolean add(UUID id) {
            boolean added = souls.add(id);
            if (added) {
                setDirty();
            }
            return added;
        }

        /** @return whether the soul was counted until now */
        public boolean remove(UUID id) {
            boolean removed = souls.remove(id);
            if (removed) {
                setDirty();
            }
            return removed;
        }
    }
}
