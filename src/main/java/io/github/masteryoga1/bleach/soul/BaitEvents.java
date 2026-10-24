package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Hollow Bait in the world: crushing one opens a site that draws Hollows to it in waves for a while,
 * more and stronger the longer it goes, until enough have come that the sky above it cracks and
 * draws them all up. The young Quincy Hunter's contest is a site too, centred on him, with no
 * cracking sky: whoever destroys more Hollows there wins. The sites are kept in {@link BaitSites}
 * and stepped here once a second; the numbers are {@link BaitLogic}'s.
 *
 * <p>A site whose centre is not loaded, or in a world where no Hollows come, is frozen: its clock
 * stops, and it is given up after a while. Its Hollows are held near it while it runs (see
 * {@link Hollow#holdForBait}); once it ends, they soon go back to being ordinary Hollows.
 */
public final class BaitEvents {
    /** Sites step once a second. */
    public static final int STEP_TICKS = 20;
    /** A wave's rift beeps the pagers and chills the Quincy around at most this often; the rest are quiet. */
    public static final int PING_INTERVAL = 600;
    /** The cracked sky opens this far above the centre. */
    public static final int SKY_HEIGHT = 20;
    /** A wave that could not come tries again this much later. */
    public static final int RETRY_TICKS = 100;
    /** A site's hold on its Hollows lasts this long after each step, so it lapses soon after the site ends. */
    public static final int HOLD_TICKS = 60;
    /** A contest whose Hunter cannot be found on this many steps in a row ends without him. */
    public static final int RIVAL_MISSING_STEPS = 2;
    /** Souls are hunted, and see the bar, this far past the radius; site Hollows are held this far past it. */
    public static final double HUNTED_EXTRA = 16.0, HOLD_EXTRA = 32.0;
    /** A held Hollow with no prey walks back once it strays this far from the centre; in the finale, to within this. */
    public static final double GATHER_RADIUS = 16.0, FINALE_GATHER = 4.0;
    /** No bait may be crushed this close to an Academy instructor. */
    public static final double ACADEMY_SAFE_RADIUS = 64.0;

    /** Each pick of a wave looks this many times for ground inside the site to open over. */
    private static final int LANDING_TRIES = 3;
    /** The wave on which the Hunter may come to a bait. */
    private static final int HUNTER_WAVE = 3;
    /** How big the cracked sky is: three times an ordinary tear. */
    private static final float SKY_SIZE = 3.0F;
    /** The finale outlasts the sky's hold by this much, so the Hollows are gathered when it ends. */
    private static final int FINALE_EXTRA_TICKS = 20;
    /** A Hunter whose contest is over stays this much longer, then leaves. */
    private static final int RELEASED_LIFETIME = 200;
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_MINUTE = 1200;

    private BaitEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(BaitEvents::onLevelTick);
        bus.addListener(BaitEvents::onLoggedOut);
    }

    /** Steps every site once a second, in the Overworld. Game tests step them themselves. */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getServer() instanceof GameTestServer) {
            return;
        }
        long now = level.getGameTime();
        if (now % STEP_TICKS != 0) {
            return;
        }
        BaitSites sites = BaitSites.get(level.getServer());
        if (sites.all().isEmpty()) {
            return;
        }
        // A step can end its site, so go through a copy, skipping any that ended along the way.
        for (BaitSite site : List.copyOf(sites.all())) {
            if (sites.find(site.id()) == site) {
                step(level, sites, site, now);
            }
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            BaitSites.get(player.getServer()).removeFromBars(player);
        }
    }

    // ---- Crushing ----

    /**
     * Why this soul cannot open a site at {@code centre} now, or null if it can (see
     * {@link BaitLogic#refusal}). A command is refused only outside the Overworld.
     */
    @Nullable
    public static BaitLogic.Refusal crushRefusal(ServerPlayer player, SoulData d, BlockPos centre, boolean byCommand) {
        return refusalFor(player, d, centre, byCommand);
    }

    /** As {@link #crushRefusal(ServerPlayer, SoulData, BlockPos, boolean)}, for a bait crushed where the soul stands. */
    @Nullable
    public static BaitLogic.Refusal crushRefusal(Player player, SoulData d, boolean byCommand) {
        return refusalFor(player, d, player.blockPosition(), byCommand);
    }

    @Nullable
    private static BaitLogic.Refusal refusalFor(Player player, SoulData d, BlockPos centre, boolean byCommand) {
        if (!(player.level() instanceof ServerLevel level)) {
            return BaitLogic.Refusal.WRONG_WORLD;
        }
        BaitSites sites = BaitSites.get(level.getServer());
        boolean overworld = level.dimension() == Level.OVERWORLD;
        Vec3 at = Vec3.atBottomCenterOf(centre);
        BaitLogic.CrushContext context = new BaitLogic.CrushContext(
                SoulLogic.hasSoul(player) && SoulLogic.isAware(d),
                d.state() == SoulState.HOLLOW || d.state() == SoulState.ARRANCAR,
                overworld,
                quiet(level),
                HollowLogic.awaitsFirstHollow(d),
                sites.ownedBy(player.getUUID()) != null,
                !level.getEntitiesOfClass(AcademyInstructor.class, new AABB(at, at).inflate(ACADEMY_SAFE_RADIUS),
                        instructor -> instructor.distanceToSqr(at) <= ACADEMY_SAFE_RADIUS * ACADEMY_SAFE_RADIUS).isEmpty(),
                sites.anyCentreWithin(centre, BleachConfig.get(BleachConfig.BAIT_SITE_SPACING)),
                sites.all().size(),
                BleachConfig.get(BleachConfig.BAIT_MAX_SITES),
                othersAtRisk(level, player, at));
        return BaitLogic.refusal(context, byCommand);
    }

    /**
     * Whether, PvP being off, someone else who would be hunted here, aware and in survival or
     * adventure mode and no ally of the crusher's, is within the site's reach. The bait would bring
     * Hollows down on them without their say.
     */
    private static boolean othersAtRisk(ServerLevel level, Player crusher, Vec3 at) {
        if (level.getServer().isPvpAllowed()) {
            return false;
        }
        double reach = BaitSite.radius() + HUNTED_EXTRA;
        for (ServerPlayer other : level.players()) {
            if (other.getUUID().equals(crusher.getUUID()) || !other.isAlive() || !other.gameMode.isSurvival()
                    || !SoulLogic.hasSoul(other) || !SoulLogic.isAware(SoulLogic.get(other))
                    || other.isAlliedTo(crusher)) {
                continue;
            }
            double dx = other.getX() - at.x;
            double dz = other.getZ() - at.z;
            if (dx * dx + dz * dz <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** Whether no Hollows can come through here now: bait turned off, or a world without arrivals. */
    private static boolean quiet(ServerLevel level) {
        return !BleachConfig.get(BleachConfig.BAIT_ENABLED)
                || !HollowLogic.worldAllowsArrivals(level.getDifficulty(),
                        level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING), level.dimension() == Level.OVERWORLD);
    }

    // ---- The site's life ----

    /**
     * Opens a site at {@code centre} for this soul: a bait, or a contest with this Hunter. Checks
     * nothing (see {@link #crushRefusal}); a site the soul already owns, which only a command can
     * get past, is stopped first. The crusher's count and milestone, and telling the soul a contest
     * is on, are the caller's. The Hunter of a contest takes it up.
     *
     * @return the new site, or null outside the Overworld
     */
    @Nullable
    public static BaitSite start(ServerPlayer owner, BlockPos centre, boolean contest, @Nullable QuincyHunter rival,
                                 long now) {
        ServerLevel level = owner.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        BaitSites sites = BaitSites.get(level.getServer());
        BaitSite old = sites.ownedBy(owner.getUUID());
        if (old != null) {
            end(level, sites, old, BaitLogic.End.STOPPED);
        }
        BaitSite site = BaitSite.create(owner.getUUID(), centre, contest, rival != null ? rival.getUUID() : null,
                BleachConfig.get(BleachConfig.BAIT_FIRST_WAVE_SECONDS) * TICKS_PER_SECOND);
        sites.add(site);
        if (contest && rival != null) {
            rival.setContestSite(site.id());
            rival.setContestOffered(true);
        }
        showBar(level, sites, site);
        return site;
    }

    /**
     * One second of a site. Frozen, it only counts the time it has been frozen, and is given up once
     * that is long enough. Otherwise, in order: a contest whose Hunter is gone ends on the tally; the
     * site's Hollows are held; the hunted souls are found; the bar is brought up to date; the cracked
     * sky runs on, or cracks; the time runs out; and the next wave comes nearer, or comes.
     */
    public static void step(ServerLevel level, BaitSites sites, BaitSite site, long now) {
        // A chunk can tick before the creatures saved in it are back: the site waits for them too.
        if (!level.isPositionEntityTicking(site.centre()) || !level.areEntitiesLoaded(ChunkPos.asLong(site.centre()))
                || quiet(level)) {
            site.setFrozenTicks(site.frozenTicks() + STEP_TICKS);
            sites.setDirty();
            if (site.frozenTicks() >= BleachConfig.get(BleachConfig.BAIT_ABANDON_MINUTES) * TICKS_PER_MINUTE) {
                end(level, sites, site, BaitLogic.End.ABANDONED);
                return;
            }
            showBar(level, sites, site);
            return;
        }
        site.setFrozenTicks(0);
        site.setActiveTicks(site.activeTicks() + STEP_TICKS);
        sites.setDirty();
        if (site.contest()) {
            // A visiting Hunter is not saved: once his ground is loaded and he is not there, he is gone.
            // Once missed is not enough, as one who stays may have strolled into a chunk still loading.
            if (!(rivalOf(level, site) instanceof QuincyHunter hunter)) {
                site.setRivalMissing(site.rivalMissing() + 1);
                if (site.rivalMissing() >= RIVAL_MISSING_STEPS) {
                    settleContest(level, sites, site, null, true);
                } else {
                    showBar(level, sites, site);
                }
                return;
            }
            site.setRivalMissing(0);
            // One who stays and was saved before he took this contest up still runs it.
            if (hunter.contestSite() == null) {
                hunter.setContestSite(site.id());
                hunter.setContestOffered(true);
            }
        }
        boolean finale = site.inFinale();
        Vec3 centre = Vec3.atBottomCenterOf(site.centre());
        for (Hollow hollow : siteHollows(level, site)) {
            hollow.holdForBait(centre, now, finale);
        }
        List<Player> hunted = hunted(level, site);
        showBar(level, sites, site);
        if (finale && stepFinale(level, sites, site, now, hunted)) {
            return;
        }
        if (!site.contest() && !site.inFinale() && site.arrived() >= BleachConfig.get(BleachConfig.BAIT_FINALE_ARRIVALS)) {
            startFinale(level, sites, site, now);
        }
        if (site.inFinale()) {
            return;
        }
        if (site.activeTicks() >= site.durationTicks()) {
            if (site.contest()) {
                settleContest(level, sites, site, null, false);
            } else {
                end(level, sites, site, BaitLogic.End.EXPIRED);
            }
            return;
        }
        site.setNextWaveIn(site.nextWaveIn() - STEP_TICKS);
        if (site.nextWaveIn() <= 0) {
            wave(level, sites, site, now, hunted);
        }
    }

    /**
     * The souls Hollows hunt at this site: players within its reach who are prey (see
     * {@link HollowLogic#isPrey}), alive, and not in creative or spectator mode.
     */
    public static List<Player> hunted(ServerLevel level, BaitSite site) {
        List<Player> hunted = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator()
                    && site.contains(player.position(), HUNTED_EXTRA) && HollowLogic.isPrey(player)) {
                hunted.add(player);
            }
        }
        return hunted;
    }

    /**
     * Sends a wave: rifts open on the ground around one of the hunted souls (the owner if none is
     * hunted but they are here), inside the site, with Hollows drawn as if by a soul as strong as the
     * bait has grown, or as that soul, whichever is stronger. Never more than the site may have about
     * at once, counting those still to drop. Without a soul to come for, room for more, or ground to
     * land on, it tries again in a few seconds. A bait's third wave may bring the Hunter.
     *
     * @return how many rifts opened
     */
    public static int wave(ServerLevel level, BaitSites sites, BaitSite site, long now, List<? extends Player> hunted) {
        Player anchor = anchor(level, site, hunted);
        int room = anchor == null ? 0
                : BaitLogic.aliveCap(hunted.size(), BleachConfig.get(BleachConfig.BAIT_MAX_ALIVE),
                        BleachConfig.get(BleachConfig.BAIT_ALIVE_PER_SOUL), BleachConfig.get(BleachConfig.BAIT_ALIVE_CAP))
                        - siteHollows(level, site).size() - pendingFor(level, site);
        if (anchor == null || room <= 0) {
            site.setNextWaveIn(RETRY_TICKS);
            sites.setDirty();
            return 0;
        }
        float p = BaitLogic.progress(site.arrived(), BleachConfig.get(BleachConfig.BAIT_FINALE_ARRIVALS));
        float soul = SoulLogic.hasSoul(anchor) ? SoulLogic.get(anchor).maxReiryoku() : 0.0F;
        float lure = BaitLogic.lureReiatsu(soul, p, (float) BleachConfig.get(BleachConfig.BAIT_LURE_START),
                (float) BleachConfig.get(BleachConfig.BAIT_LURE_GAIN));
        ServerPlayer drawer = anchor instanceof ServerPlayer player ? player : null;
        int opened = 0;
        for (int pick = BaitLogic.picksPerWave(p); pick > 0 && room > 0; pick--) {
            HollowKind kind = HollowLogic.pickArrival(lure, false, level.getRandom());
            if (kind == HollowKind.FISHBONE_D) {
                continue;
            }
            int count = HollowLogic.groupSize(kind, lure, room, level.getRandom().nextDouble());
            EntityType<? extends Hollow> type = ModEntities.hollowType(kind);
            Vec3 landing = landing(level, site, anchor, type, kind.scaleMax);
            if (landing == null) {
                continue;
            }
            Vec3 at = landing.add(0.0, SpawnSpots.clearAbove(level, type, kind.scaleMax, landing,
                    GargantaEvents.RIFT_MAX_HEIGHT), 0.0);
            GargantaRift rift = GargantaRift.arrival(level, at, now, Collections.nCopies(count, kind), drawer,
                    GargantaEvents.ARRIVAL_SIZE);
            rift.setBaitSite(site.id());
            long sincePing = now - site.lastPingAt();
            boolean quiet = sincePing >= 0 && sincePing < PING_INTERVAL;
            rift.setQuiet(quiet);
            if (!level.addFreshEntity(rift)) {
                continue;
            }
            if (!quiet) {
                site.setLastPingAt(now);
            }
            room -= count;
            opened++;
        }
        if (opened == 0) {
            site.setNextWaveIn(RETRY_TICKS);
            sites.setDirty();
            return 0;
        }
        site.setWaves(site.waves() + 1);
        site.setNextWaveIn(BaitLogic.waveIntervalTicks(p, BleachConfig.get(BleachConfig.BAIT_INTERVAL_START_SECONDS),
                BleachConfig.get(BleachConfig.BAIT_INTERVAL_END_SECONDS)));
        sites.setDirty();
        if (!site.contest() && site.waves() == HUNTER_WAVE) {
            QuincyNpcEvents.onBaitWave(level, anchor, now);
        }
        return opened;
    }

    /** Who a wave comes for: one of the hunted at random, or the owner if they are here and not a spectator. */
    @Nullable
    private static Player anchor(ServerLevel level, BaitSite site, List<? extends Player> hunted) {
        if (!hunted.isEmpty()) {
            return hunted.get(level.getRandom().nextInt(hunted.size()));
        }
        Player owner = level.getPlayerByUUID(site.owner());
        return owner != null && !owner.isSpectator() && site.contains(owner.position(), HUNTED_EXTRA) ? owner : null;
    }

    /** Ground 12 to 24 blocks from the soul, and inside the site's reach, for a rift to open over. */
    @Nullable
    private static Vec3 landing(ServerLevel level, BaitSite site, Player anchor, EntityType<?> type, float scale) {
        for (int i = 0; i < LANDING_TRIES; i++) {
            Vec3 spot = SpawnSpots.openGround(level, anchor, type, scale, GargantaEvents.SPOT_MIN_DISTANCE,
                    GargantaEvents.SPOT_MAX_DISTANCE, GargantaEvents.SPOT_TRIES, GargantaEvents.SPOT_MAX_RISE);
            if (spot != null && site.contains(spot, HUNTED_EXTRA)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Cracks the sky over a bait: a great rift opens above it and holds for 15 to 20 seconds, its
     * Hollows stop fighting and gather beneath it, and when it is done they are drawn up. The souls
     * hunted here now are the ones who saw it. Contests have no finale.
     *
     * @return false if this site cannot have one now (a contest, or one already cracked) or the rift could not open
     */
    public static boolean startFinale(ServerLevel level, BaitSites sites, BaitSite site, long now) {
        if (site.contest() || site.inFinale()) {
            return false;
        }
        int hold = BaitLogic.finaleHoldTicks(BleachConfig.get(BleachConfig.BAIT_FINALE_MIN_SECONDS),
                BleachConfig.get(BleachConfig.BAIT_FINALE_MAX_SECONDS), level.getRandom());
        GargantaRift sky = openSky(level, sites, site, now, hold, false);
        if (sky == null) {
            return false;
        }
        site.setFinaleTicks(GargantaRift.GROW_TICKS + hold + FINALE_EXTRA_TICKS);
        sites.setDirty();
        List<UUID> witnesses = new ArrayList<>();
        for (Player soul : hunted(level, site)) {
            witnesses.add(soul.getUUID());
        }
        sites.setFinaleWitnesses(site.id(), witnesses);
        ServerBossEvent bar = showBar(level, sites, site);
        for (ServerPlayer viewer : bar.getPlayers()) {
            viewer.sendSystemMessage(Component.translatable("message.bleach.bait.finale")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        onFinaleOpened(level, site, sky);
        return true;
    }

    /**
     * A second of the cracked sky: the rift is opened again if it is gone (it is not saved), with
     * what is left of the hold, and quietly. When the time is up, the site's Hollows are drawn up,
     * the owner's count and the witnesses' milestones are given, and the site ends.
     *
     * @return whether the site ended
     */
    private static boolean stepFinale(ServerLevel level, BaitSites sites, BaitSite site, long now, List<Player> hunted) {
        // Not in the last second, when it would only open over a site that is ending.
        if (site.finaleTicks() > STEP_TICKS && skyOf(level, sites, site) == null) {
            openSky(level, sites, site, now, site.finaleTicks(), true);
        }
        site.setFinaleTicks(site.finaleTicks() - STEP_TICKS);
        sites.setDirty();
        if (site.inFinale()) {
            return false;
        }
        for (Hollow hollow : siteHollows(level, site)) {
            hollow.depart();
        }
        MinecraftServer server = level.getServer();
        ServerPlayer owner = server.getPlayerList().getPlayer(site.owner());
        if (owner != null && SoulLogic.hasSoul(owner)) {
            SoulData data = SoulLogic.get(owner);
            HuntRecord hunt = data.hunt();
            data.setHunt(hunt.withBait(hunt.bait().withFinales(hunt.bait().finales() + 1)));
        }
        List<UUID> witnesses = sites.finaleWitnesses(site.id());
        if (witnesses.isEmpty()) {
            witnesses = hunted.stream().map(Entity::getUUID).toList();
        }
        boolean noLoss = BaitLogic.noLossFinale(site.arrived(), site.plusLost());
        for (UUID uuid : witnesses) {
            ServerPlayer witness = server.getPlayerList().getPlayer(uuid);
            if (witness != null) {
                Milestones.grant(witness, Milestones.SKY_CRACKED);
                if (noLoss) {
                    Milestones.grant(witness, Milestones.BAIT_NO_LOSS);
                }
            }
        }
        end(level, sites, site, BaitLogic.End.FINALE);
        return true;
    }

    /** Opens the great rift above the site's centre, at most eight blocks below the top of the world. */
    @Nullable
    private static GargantaRift openSky(ServerLevel level, BaitSites sites, BaitSite site, long now, int hold,
                                        boolean quiet) {
        BlockPos centre = site.centre();
        // Over the open sky: a bait crushed underground still cracks the sky above the ground over it.
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        Vec3 at = new Vec3(centre.getX() + 0.5, Math.max(centre.getY(), ground) + SKY_HEIGHT, centre.getZ() + 0.5);
        GargantaRift sky = GargantaRift.sky(level, at, now, SKY_SIZE, hold, site.id());
        sky.setQuiet(quiet);
        if (!level.addFreshEntity(sky)) {
            return null;
        }
        sites.setSkyRift(site.id(), sky.getUUID());
        return sky;
    }

    @Nullable
    private static GargantaRift skyOf(ServerLevel level, BaitSites sites, BaitSite site) {
        UUID id = sites.skyRift(site.id());
        return id != null && level.getEntity(id) instanceof GargantaRift sky && !sky.isRemoved() ? sky : null;
    }

    /**
     * Ends a site: its bar comes down, with word of why to everyone who saw it and to its owner; the
     * cracked sky is closed at once unless it is closing of its own accord after a finale; the Hunter
     * of a contest lets it go and leaves soon after, unless he is one who stays; and it is forgotten.
     */
    public static void end(ServerLevel level, BaitSites sites, BaitSite site, BaitLogic.End why) {
        ServerBossEvent bar = sites.bar(site);
        List<ServerPlayer> told = new ArrayList<>(bar.getPlayers());
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
        if (owner != null && !told.contains(owner)) {
            told.add(owner);
        }
        for (ServerPlayer player : told) {
            player.sendSystemMessage(Component.translatable(why.key()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        sites.dropBar(site.id());
        if (why != BaitLogic.End.FINALE) {
            GargantaRift sky = skyOf(level, sites, site);
            if (sky != null) {
                sky.discard();
            }
        }
        if (rivalOf(level, site) instanceof QuincyHunter hunter && site.id().equals(hunter.contestSite())) {
            hunter.setContestSite(null);
            if (!hunter.isPermanent()) {
                hunter.setLifetime(RELEASED_LIFETIME);
            }
        }
        sites.remove(site.id());
    }

    /**
     * Ends a contest on its result (see {@link BaitLogic#contestOutcome}): the challenger, if they won,
     * gets a Hollow Bait from the Hunter and the milestone; win or lose, it is counted and they are
     * told. {@code gone} is why the Hunter left, if he did; {@code goneAtAll} whether he is gone.
     */
    public static void settleContest(ServerLevel level, BaitSites sites, BaitSite site,
                                     @Nullable QuincyNpc.LeaveCause gone, boolean goneAtAll) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
        boolean online = owner != null && owner.level() == level;
        BaitLogic.Outcome outcome = BaitLogic.contestOutcome(site.ownerKills(), site.rivalKills(), online, gone,
                goneAtAll);
        if (owner != null) {
            Component result = switch (outcome) {
                case WIN -> Component.translatable("message.bleach.contest.won", site.ownerKills(), site.rivalKills());
                case LOSS -> Component.translatable("message.bleach.contest.lost", site.ownerKills(), site.rivalKills());
                case TIE -> Component.translatable("message.bleach.contest.tie", site.ownerKills());
                case VOID -> Component.translatable("message.bleach.contest.void").withStyle(ChatFormatting.GRAY);
            };
            if (outcome == BaitLogic.Outcome.WIN) {
                givePrize(level, owner, new ItemStack(ModItems.HOLLOW_BAIT.get()));
                Milestones.grant(owner, Milestones.RIVAL_BESTED);
            }
            if ((outcome == BaitLogic.Outcome.WIN || outcome == BaitLogic.Outcome.LOSS) && SoulLogic.hasSoul(owner)) {
                SoulData data = SoulLogic.get(owner);
                HuntRecord hunt = data.hunt();
                BaitRecord bait = hunt.bait();
                data.setHunt(hunt.withBait(outcome == BaitLogic.Outcome.WIN ? bait.withWon(bait.won() + 1)
                        : bait.withLost(bait.lost() + 1)));
            }
            owner.sendSystemMessage(result);
        }
        end(level, sites, site, BaitLogic.End.CONTEST);
    }

    /**
     * Hands the winner the Hunter's prize. A challenger still on the death screen has already dropped what
     * they carried, and an inventory filled now would not survive the respawn unless inventories are
     * kept, so the prize is otherwise left by their body with the rest.
     */
    private static void givePrize(ServerLevel level, ServerPlayer owner, ItemStack prize) {
        if (owner.isAlive() || level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            owner.getInventory().placeItemBackInInventory(prize);
            return;
        }
        ItemEntity dropped = new ItemEntity(level, owner.getX(), owner.getY() + 0.5, owner.getZ(), prize);
        dropped.setDefaultPickUpDelay();
        level.addFreshEntity(dropped);
    }

    /** Whether this point is inside a site's reach, where the sky does not tear for anyone on top of its waves. */
    public static boolean insideActiveSite(ServerLevel level, Vec3 pos) {
        return level.dimension() == Level.OVERWORLD
                && BaitSites.get(level.getServer()).containing(pos, HUNTED_EXTRA) != null;
    }

    // ---- The site's Hollows and its bar ----

    /** The site's own Hollows within its hold, alive and not leaving. */
    private static List<Hollow> siteHollows(ServerLevel level, BaitSite site) {
        return level.getEntitiesOfClass(Hollow.class, column(level, site, BaitSite.radius() + HOLD_EXTRA),
                hollow -> hollow.isAlive() && !hollow.isDeparting() && site.id().equals(hollow.baitSite())
                        && site.contains(hollow.position(), HOLD_EXTRA));
    }

    /** Hollows still to drop from the site's open rifts. */
    private static int pendingFor(ServerLevel level, BaitSite site) {
        int pending = 0;
        AABB hold = column(level, site, BaitSite.radius() + HOLD_EXTRA);
        for (GargantaRift rift : level.getEntitiesOfClass(GargantaRift.class, hold,
                rift -> !rift.isDeparture() && site.id().equals(rift.baitSite()))) {
            pending += rift.pending().size();
        }
        return pending;
    }

    /**
     * The whole height of the world within {@code reach} of a site's centre, across: a site reaches
     * as far up and down as it likes, as {@link BaitSite#contains} does, so a bait crushed deep below
     * still holds the Hollows that come down on the ground above it.
     */
    private static AABB column(ServerLevel level, BaitSite site, double reach) {
        Vec3 centre = Vec3.atBottomCenterOf(site.centre());
        return new AABB(centre.x - reach, level.getMinBuildHeight(), centre.z - reach,
                centre.x + reach, level.getMaxBuildHeight(), centre.z + reach);
    }

    @Nullable
    private static Entity rivalOf(ServerLevel level, BaitSite site) {
        Entity rival = site.rival() != null ? level.getEntity(site.rival()) : null;
        return rival != null && rival.isAlive() ? rival : null;
    }

    /**
     * Shows the site's bar to everyone who can see spirits within its reach, spectators aside, and
     * takes it from those who have gone; then sets how it reads: the bait's pull towards the cracking
     * sky, the contest's tally and time left, or the cracked sky's time left.
     */
    private static ServerBossEvent showBar(ServerLevel level, BaitSites sites, BaitSite site) {
        ServerBossEvent bar = sites.bar(site);
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && SoulLogic.seesSpirits(player) && site.contains(player.position(), HUNTED_EXTRA)) {
                viewers.add(player);
            }
        }
        // Those who left go first, so a soul that came back as a new player object is shown it again.
        for (ServerPlayer shown : List.copyOf(bar.getPlayers())) {
            if (!viewers.contains(shown)) {
                bar.removePlayer(shown);
            }
        }
        viewers.forEach(bar::addPlayer);
        styleBar(level, sites, site, bar);
        return bar;
    }

    private static void styleBar(ServerLevel level, BaitSites sites, BaitSite site, ServerBossEvent bar) {
        if (site.inFinale()) {
            GargantaRift sky = skyOf(level, sites, site);
            int whole = GargantaRift.GROW_TICKS + FINALE_EXTRA_TICKS + (sky != null ? sky.hold()
                    : BleachConfig.get(BleachConfig.BAIT_FINALE_MAX_SECONDS) * TICKS_PER_SECOND);
            bar.setName(Component.translatable("bossbar.bleach.finale"));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            bar.setProgress(Mth.clamp((float) site.finaleTicks() / Math.max(1, whole), 0.0F, 1.0F));
        } else if (site.contest()) {
            bar.setName(Component.translatable("bossbar.bleach.contest", site.ownerKills(), site.rivalKills()));
            bar.setColor(BossEvent.BossBarColor.WHITE);
            bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setProgress(Mth.clamp(1.0F - (float) site.activeTicks() / Math.max(1, site.durationTicks()), 0.0F, 1.0F));
        } else {
            bar.setName(Component.translatable("bossbar.bleach.bait"));
            bar.setColor(BossEvent.BossBarColor.BLUE);
            bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            bar.setProgress(BaitLogic.progress(site.arrived(), BleachConfig.get(BleachConfig.BAIT_FINALE_ARRIVALS)));
        }
    }

    // ---- Hooks ----

    /** Hollows came down from one of a site's rifts (see {@link GargantaRift}). */
    public static void onArrived(ServerLevel level, UUID site, int count) {
        if (count <= 0) {
            return;
        }
        BaitSites sites = BaitSites.get(level.getServer());
        BaitSite found = sites.find(site);
        if (found != null) {
            found.setArrived(found.arrived() + count);
            sites.setDirty();
        }
    }

    /**
     * A Hollow died: within a contest's reach, it is counted to whichever of the two destroyed it,
     * whether or not the contest drew it.
     */
    public static void onHollowDeath(Hollow hollow, DamageSource source) {
        Entity killer = source.getEntity();
        if (killer == null || !(hollow.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        BaitSites sites = BaitSites.get(level.getServer());
        UUID who = killer.getUUID();
        for (BaitSite site : sites.all()) {
            if (!site.contest() || !site.contains(hollow.position(), HUNTED_EXTRA)) {
                continue;
            }
            if (who.equals(site.owner())) {
                site.setOwnerKills(site.ownerKills() + 1);
            } else if (who.equals(site.rival())) {
                site.setRivalKills(site.rivalKills() + 1);
            } else {
                continue;
            }
            sites.setDirty();
            styleBar(level, sites, site, sites.bar(site));
        }
    }

    /**
     * A Hollow devoured a Plus: the site that drew it, or failing that the one it is in, has lost
     * one, and its finale will not count as seen through without loss.
     */
    public static void onDevoured(Hollow hollow) {
        if (!(hollow.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        BaitSites sites = BaitSites.get(level.getServer());
        BaitSite site = hollow.baitSite() != null ? sites.find(hollow.baitSite()) : null;
        if (site == null) {
            site = sites.containing(hollow.position(), HUNTED_EXTRA);
        }
        if (site != null && !site.plusLost()) {
            site.setPlusLost(true);
            sites.setDirty();
        }
    }

    /**
     * The Hunter is leaving, for this reason, or dying (no reason): a contest he is running ends now,
     * off if a player drove him away, else on the tally.
     */
    public static void onRivalLeft(QuincyHunter hunter, @Nullable QuincyNpc.LeaveCause cause) {
        UUID id = hunter.contestSite();
        MinecraftServer server = hunter.getServer();
        if (id == null || server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        BaitSites sites = BaitSites.get(server);
        BaitSite site = sites.find(id);
        if (site == null || !site.contest() || !hunter.getUUID().equals(site.rival())) {
            hunter.setContestSite(null);
            return;
        }
        settleContest(level, sites, site, cause, true);
    }

    /**
     * The sky over a bait has just cracked. Nothing more comes through it yet: this is where a Menos
     * Grande will step through, in the round that brings the great Hollows.
     */
    public static void onFinaleOpened(ServerLevel level, BaitSite site, GargantaRift sky) {
    }
}
