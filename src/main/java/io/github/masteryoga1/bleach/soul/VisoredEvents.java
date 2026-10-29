package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.joml.Vector3f;

/**
 * The Visored on the server: the Hollow taking root in a Shinigami's wounds, its voice, and the fight with it.
 *
 * <p>Near-fatal blows from Menos-class Hollows ({@link VisoredLogic#isWoundingSource}) are counted as wounds, and
 * enough of them root the Hollow ({@link #takeRoot}), which then speaks to its host every 5 minutes. Once it is
 * ready, sitting in Jinzen brings it out ({@link #beginDuel}, from {@link JinzenLogic}) as an {@link InnerHollow}
 * in a ring around where the host sat: the host is kept in, everyone else is kept out and cannot touch the host,
 * and the fight has a time limit. Beating it makes the soul a Visored ({@link #subdue}). Losing never kills: the
 * Hollow's last blow stops at {@code loss_health}. Death, logging out, another world, Peaceful, the power lost or
 * lent, spectator or creative mode, a server stop or getting far out of the ring break it off with no loss
 * counted. Every end goes through {@link #endDuel}.
 *
 * <p>The fights are held only in memory, in a table on the server thread, and the Hollow is never saved, so a
 * save holds nothing of a fight but its host's record, which at the start already says when the next try may
 * come. Everything takes a {@link Player}, so game tests can use mock players; the boss bar is for a real
 * {@link ServerPlayer} only.
 */
public final class VisoredEvents {
    private static final String PREFIX = "message.bleach.visored.";
    private static final String SPEECH = PREFIX + "speech.";
    /** Chat styles: the Hollow's stirring, the fight's own lines, and the win. */
    private static final ChatFormatting[] MUTED = {ChatFormatting.GRAY, ChatFormatting.ITALIC};
    private static final ChatFormatting[] PLAIN = {ChatFormatting.GRAY};
    private static final ChatFormatting[] GOLDEN = {ChatFormatting.GOLD};

    /** Points tried around the host for the Hollow to step out at, turning by {@link #SPAWN_TURN_DEGREES}. */
    private static final int SPAWN_TRIES = 8;
    private static final float SPAWN_TURN_DEGREES = 45.0F;
    /** How far below a spot the Hollow may find its footing, in blocks. */
    private static final int SPAWN_DROP_BLOCKS = 2;
    private static final int KEEP_OUT_INTERVAL_TICKS = 5;
    private static final int RING_INTERVAL_TICKS = 10;
    private static final int SECOND_TICKS = 20;
    /** Height above and below the centre in which others are kept out, in blocks. */
    private static final double KEEP_OUT_HEIGHT = 8.0;
    /** How far from the centre a creature's grudge against either fighter is dropped, in blocks. */
    private static final double AGGRO_RANGE = 32.0;
    private static final double PUSH_LIFT = 0.1;
    private static final int RING_POINTS = 36;
    private static final double RING_LIFT = 0.2;
    private static final int BURST = 30;
    private static final int ROOT_BURST = 40;
    private static final double BURST_SPREAD = 0.4;
    private static final double BURST_SPREAD_UP = 1.0;
    private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.2F);
    private static final DustParticleOptions RING_DUST = new DustParticleOptions(new Vector3f(0.92F, 0.92F, 0.96F), 1.0F);

    /** Every fight under way, by its host's UUID. Server thread only. */
    private static final Map<UUID, Duel> DUELS = new HashMap<>();

    /** What came of trying to bring the Hollow out. Every one but {@link #STARTED} and {@link #NOT_READY} is told. */
    public enum DuelStart {
        STARTED(null), ALREADY("duel.already"), DISABLED("disabled"), NO_POWER("no_power"), SUBDUED("duel.subdued"),
        NOT_READY(null), PEACEFUL("duel.peaceful"), CROWDED("duel.crowded");

        @Nullable
        private final String line;

        DuelStart(@Nullable String line) {
            this.line = line;
        }

        /** The action-bar key, or null for STARTED and NOT_READY. */
        @Nullable
        public String messageKey() {
            return line == null ? null : PREFIX + line;
        }
    }

    /** How a fight ended: the host beat its Hollow, the Hollow beat its host, or it stopped with neither. */
    public enum DuelEnd { WIN, LOSS, BROKEN_OFF }

    /** One fight, held only in memory on the server thread. */
    public static final class Duel {
        private final Player host;
        private final UUID hostId;
        private final InnerHollow hollow;
        private final ResourceKey<Level> level;
        private final Vec3 centre;
        private final long startedAt;
        private final long endsAt;
        /** Shown to a real player only. */
        @Nullable
        private final ServerBossEvent bar;
        private long lastHostBlowAt;

        private Duel(Player host, InnerHollow hollow, Vec3 centre, long startedAt, long endsAt,
                     @Nullable ServerBossEvent bar) {
            this.host = host;
            this.hostId = host.getUUID();
            this.hollow = hollow;
            this.level = host.level().dimension();
            this.centre = centre;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.bar = bar;
            this.lastHostBlowAt = startedAt;
        }

        /** The player object that began it; a respawn or an End exit makes a new one, which breaks it off. */
        public Player host() {
            return host;
        }

        public UUID hostId() {
            return hostId;
        }

        public InnerHollow hollow() {
            return hollow;
        }

        public ResourceKey<Level> level() {
            return level;
        }

        /** Where the host stood: the middle of the ring. */
        public Vec3 centre() {
            return centre;
        }

        public long startedAt() {
            return startedAt;
        }

        /** When the time runs out, and the fight is lost. */
        public long endsAt() {
            return endsAt;
        }

        /** When a blow from the host last landed on the Hollow; its start until then. */
        public long lastHostBlowAt() {
            return lastHostBlowAt;
        }
    }

    private VisoredEvents() {
    }

    /**
     * Wounds after they land; the host's shield first of all, so nothing else weighs a blow that should not land; the
     * loss clamp last of all, after every other change to the blow; and the ends that come from outside the host's
     * tick: deaths, logging out and the server stopping.
     */
    public static void register(IEventBus bus) {
        bus.addListener(VisoredEvents::onDamageTaken);
        bus.addListener(EventPriority.HIGHEST, VisoredEvents::onIncomingDamage);
        bus.addListener(EventPriority.LOWEST, VisoredEvents::onDamagePre);
        bus.addListener(VisoredEvents::onDeath);
        bus.addListener(VisoredEvents::onLoggedOut);
        bus.addListener(VisoredEvents::onServerStopping);
    }

    /**
     * Each tick of a living player, server side: the fight's upkeep for its host, then, outside a fight, the
     * Hollow's voice.
     */
    public static void tick(Player player, SoulData data, long now) {
        if (player.level().isClientSide()) {
            return;
        }
        Duel duel = DUELS.get(player.getUUID());
        if (duel != null) {
            upkeep(player, data, duel, now);
            // A fight that ended this tick has had its last words.
            return;
        }
        String voice = VisoredLogic.voiceKey(data, player.getUUID(), now);
        if (voice != null) {
            speak(player, voice);
        }
    }

    // ---- The route ----

    /**
     * Counts a wound if it may; true when it counted. Roots the Hollow at the count. A wound counts only in a soul
     * that may take a Hollow in ({@link VisoredLogic#mayTakeRoot}) and only {@code wound_spacing_seconds} after the
     * last one; the earlier ones are told in chat.
     */
    public static boolean noteWound(Player player, SoulData data, long now) {
        if (!VisoredLogic.mayTakeRoot(data, now)) {
            return false;
        }
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        if (!VisoredLogic.woundSpaced(record, now)) {
            return false;
        }
        int wounds = record.wounds() + 1;
        VisoredLogic.setRecord(data, record.withWounds(wounds).withLastWoundAt(now));
        int needed = VisoredLogic.woundsToRoot(data);
        if (wounds >= needed) {
            takeRoot(player, data, now);
        } else {
            chat(player, "wound", MUTED, wounds, needed);
        }
        return true;
    }

    /**
     * The Hollow takes root, with its signs: Nausea and Weakness, a white haze about the body, a heartbeat only the
     * aware hear, a line in chat and its first words. It can be faced {@code stir_minutes} later. The Bosses step's
     * Hollowfication comes in here too, with no wounds.
     */
    public static void takeRoot(Player player, SoulData data, long now) {
        long stir = BleachConfig.get(BleachConfig.VISORED_STIR_MINUTES) * 1200L;
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now).withDuelReadyAt(now + stir));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, VisoredLogic.ROOT_NAUSEA_TICKS));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, VisoredLogic.ROOT_WEAKNESS_TICKS));
        if (player.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, WHITE_DUST, player.getX(), player.getY() + player.getBbHeight() * 0.5,
                    player.getZ(), ROOT_BURST, BURST_SPREAD, BURST_SPREAD_UP, BURST_SPREAD, 0.0);
        }
        SpiritSenses.play(player, ModSounds.HOLLOW_POISON.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        chat(player, "rooted", MUTED);
        speak(player, "rooted");
    }

    /** Brings the record's clocks into this world's time, on login, respawn and reset (see {@link SoulEvents#refresh}). */
    public static void onRefresh(Player player, SoulData data) {
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        ShinigamiProgress.Visored normal = VisoredLogic.normalise(record, player.level().getGameTime());
        if (!normal.equals(record)) {
            VisoredLogic.setRecord(data, normal);
        }
    }

    /** Ends a fight (BROKEN_OFF): for resets and commands. 5k adds the mask's end. */
    public static void endAll(Player player, SoulData data, long now) {
        if (isDuelling(player)) {
            endDuel(player, data, DuelEnd.BROKEN_OFF, now);
        }
    }

    // ---- The fight ----

    /**
     * Brings the Hollow out to fight its host where the host stands. The first check that fails gives the result: a
     * fight already under way, the path turned off (even when forced), no Shinigami power or lent away, the Hollow
     * already beaten, not ready yet (skipped when forced), Peaceful, and another fight too close by. A forced fight
     * roots an unrooted soul silently first. On the client, nothing happens and the result is {@link
     * DuelStart#NOT_READY}.
     */
    public static DuelStart beginDuel(Player host, SoulData data, long now, boolean force) {
        if (!(host.level() instanceof ServerLevel level)) {
            return DuelStart.NOT_READY;
        }
        if (DUELS.containsKey(host.getUUID())) {
            return DuelStart.ALREADY;
        }
        if (!VisoredLogic.enabled()) {
            return DuelStart.DISABLED;
        }
        if (!ShinigamiLogic.hasShinigamiPower(data) || ShinigamiLogic.isPowerLent(data, now)) {
            return DuelStart.NO_POWER;
        }
        if (VisoredLogic.record(data).subdued()) {
            return DuelStart.SUBDUED;
        }
        if (!force && !VisoredLogic.duelReady(data, now)) {
            return DuelStart.NOT_READY;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return DuelStart.PEACEFUL;
        }
        if (crowded(level, host.position())) {
            return DuelStart.CROWDED;
        }
        EntityType<InnerHollow> type = ModEntities.INNER_HOLLOW.get();
        InnerHollow hollow = type.create(level);
        if (hollow == null) {
            return DuelStart.NOT_READY;
        }
        hollow.setUp(host);
        Vec3 spot = spawnSpot(level, host, type);
        Vec3 toHost = host.position().subtract(spot);
        float yaw = toHost.horizontalDistanceSqr() < 1.0E-6 ? host.getYRot() + 180.0F
                : (float) (Mth.atan2(toHost.z, toHost.x) * Mth.RAD_TO_DEG) - 90.0F;
        hollow.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
        hollow.setYHeadRot(yaw);
        hollow.setYBodyRot(yaw);

        long endsAt = now + VisoredLogic.timeLimitTicks();
        ServerPlayer shown = host instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        ServerBossEvent bar = shown == null ? null : new ServerBossEvent(barName(endsAt, now),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        // In the table before it joins the world, so its first tick finds its fight.
        Duel duel = new Duel(host, hollow, host.position(), now, endsAt, bar);
        DUELS.put(duel.hostId, duel);
        if (!level.addFreshEntity(hollow)) {
            DUELS.remove(duel.hostId);
            return DuelStart.NOT_READY;
        }
        hollow.setTarget(host);
        if (bar != null) {
            bar.setProgress(1.0F);
            bar.addPlayer(shown);
        }

        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        if (!record.rooted()) {
            record = record.withRootedAt(now);
        }
        // Until the fight's own end says otherwise, so sitting in Jinzen mid-fight never reads as ready, and a fight
        // lost to a crash can be tried again once its time would have run out.
        VisoredLogic.setRecord(data, record.withDuelReadyAt(endsAt));

        SpiritSenses.play(hollow, ModSounds.HOLLOW_AMBIENT.get(), hollow.getSoundSource(), 1.0F, 1.0F, false);
        burst(level, hollow);
        ring(level, duel);
        chat(host, "begun", PLAIN);
        speak(host, "begun");
        return DuelStart.STARTED;
    }

    /** Tells the player why the Hollow did not come out, on the action bar; nothing for a start or not being ready. */
    public static void tell(Player player, DuelStart start) {
        String key = start.messageKey();
        if (key != null) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Ends the host's fight, if it has one: off the table, the bar taken down, and the Hollow gone in a burst of white.
     * A win makes the soul a Visored; a loss is counted, leaves the host weak and puts the next fight off by
     * {@code retry_minutes}; a fight broken off puts it off as long, and counts nothing.
     */
    public static void endDuel(Player host, SoulData data, DuelEnd end, long now) {
        Duel duel = DUELS.remove(host.getUUID());
        if (duel == null) {
            return;
        }
        if (duel.bar != null) {
            duel.bar.removeAllPlayers();
        }
        InnerHollow hollow = duel.hollow;
        if (hollow.level() instanceof ServerLevel level) {
            // A Hollow cut down has just screamed its own death; one beaten by command screams here.
            if (end == DuelEnd.WIN && !hollow.isDeadOrDying()) {
                SpiritSenses.play(hollow, ModSounds.HOLLOW_DEATH.get(), hollow.getSoundSource(), 1.0F, 0.8F, false);
            }
            burst(level, hollow);
        }
        if (!hollow.isRemoved()) {
            hollow.discard();
        }
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        switch (end) {
            case WIN -> subdue(host, data, now);
            case LOSS -> {
                VisoredLogic.setRecord(data, record.withLosses(record.losses() + 1)
                        .withDuelReadyAt(now + VisoredLogic.retryTicks()));
                host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, VisoredLogic.LOSS_EFFECT_TICKS));
                host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, VisoredLogic.LOSS_EFFECT_TICKS));
                chat(host, "lost", PLAIN, BleachConfig.get(BleachConfig.INNER_HOLLOW_RETRY_MINUTES));
                speak(host, "lost");
            }
            case BROKEN_OFF -> {
                VisoredLogic.setRecord(data, record.withDuelReadyAt(now + VisoredLogic.retryTicks()));
                bar(host, "duel.broken_off");
            }
        }
    }

    /**
     * The Hollow is beaten and the soul a Visored: said in chat, and the Hollow's last words, "for now". The
     * milestone follows from the record.
     */
    public static void subdue(Player player, SoulData data, long now) {
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withSubduedAt(now));
        chat(player, "won", GOLDEN);
        speak(player, "won");
    }

    /** The fight of the host with this UUID, or null when it has none. */
    @Nullable
    public static Duel duel(@Nullable UUID hostId) {
        return hostId == null ? null : DUELS.get(hostId);
    }

    /** The Hollow fighting the host with this UUID, or null when it has no fight. */
    @Nullable
    public static InnerHollow duelOf(@Nullable UUID hostId) {
        Duel duel = duel(hostId);
        return duel == null ? null : duel.hollow;
    }

    /** Whether this player is fighting its Hollow. */
    public static boolean isDuelling(Player host) {
        return DUELS.containsKey(host.getUUID());
    }

    /** A blow from its host landed on this Hollow, which puts off its mending. */
    public static void noteHostBlow(InnerHollow hollow, long now) {
        Duel duel = duel(hollow.hostId());
        if (duel != null && duel.hollow == hollow) {
            duel.lastHostBlowAt = now;
        }
    }

    /**
     * Breaks off the fight whose Hollow this is, as if the host had walked away: for a Hollow that has outlived any
     * fight it could be in. One in no fight simply goes.
     */
    public static void forget(InnerHollow hollow, long now) {
        Duel duel = duel(hollow.hostId());
        if (duel != null && duel.hollow == hollow) {
            endDuel(duel.host, SoulLogic.get(duel.host), DuelEnd.BROKEN_OFF, now);
        } else if (!hollow.isRemoved()) {
            hollow.discard();
        }
    }

    /** Ends every fight BROKEN_OFF at its host's game time and clears the table: the server-stop path. */
    public static void breakOffAll() {
        for (Duel duel : List.copyOf(DUELS.values())) {
            endDuel(duel.host, SoulLogic.get(duel.host), DuelEnd.BROKEN_OFF, duel.host.level().getGameTime());
        }
        DUELS.clear();
    }

    /**
     * One tick of the host's fight, in this order: the reasons it breaks off, the time limit, the ring keeping the
     * host and its Hollow in, then every 5 ticks keeping everyone else out, every 10 the ring's light, every second
     * other creatures' grudges against the two dropped, the time left on the bar and the Hollow's mending, and every
     * tick the bar's progress.
     */
    private static void upkeep(Player host, SoulData data, Duel duel, long now) {
        if (!(host.level() instanceof ServerLevel level)) {
            return;
        }
        int radius = VisoredLogic.arenaRadius();
        if (brokenOff(host, data, duel, radius, now)) {
            endDuel(host, data, DuelEnd.BROKEN_OFF, now);
            return;
        }
        if (now >= duel.endsAt) {
            endDuel(host, data, DuelEnd.LOSS, now);
            return;
        }
        InnerHollow hollow = duel.hollow;
        if (horizontalDistance(host.position(), duel.centre) > radius) {
            push(host, duel.centre, true);
            bar(host, "duel.arena");
        }
        if (horizontalDistance(hollow.position(), duel.centre) > radius) {
            push(hollow, duel.centre, true);
        }
        long elapsed = now - duel.startedAt;
        if (Math.floorMod(elapsed, KEEP_OUT_INTERVAL_TICKS) == 0) {
            keepOut(level, host, duel, radius);
        }
        if (Math.floorMod(elapsed, RING_INTERVAL_TICKS) == 0) {
            ring(level, duel);
        }
        if (Math.floorMod(elapsed, SECOND_TICKS) == 0) {
            dropGrudges(level, host, duel);
            if (duel.bar != null) {
                duel.bar.setName(barName(duel.endsAt, now));
            }
            if (now - duel.lastHostBlowAt >= VisoredLogic.MEND_DELAY_TICKS && hollow.isAlive()
                    && hollow.getHealth() < hollow.getMaxHealth()) {
                hollow.heal((float) BleachConfig.get(BleachConfig.INNER_HOLLOW_MEND_PER_SECOND));
            }
        }
        if (duel.bar != null) {
            duel.bar.setProgress(Mth.clamp((float) (duel.endsAt - now) / Math.max(1L, duel.endsAt - duel.startedAt),
                    0.0F, 1.0F));
        }
    }

    /**
     * Whether the fight breaks off this tick: the table holds another player object for this host (a respawn or an
     * End exit made a new one), the host is dead, a spectator or in creative mode, has no Shinigami power or has lent
     * it, the path is off, the host is in another world, the Hollow has gone, the host got more than
     * {@link VisoredLogic#LEAVE_MARGIN} blocks past the ring, or the world is on Peaceful.
     */
    private static boolean brokenOff(Player host, SoulData data, Duel duel, int radius, long now) {
        return duel.host != host || !host.isAlive() || host.isSpectator() || host.isCreative()
                || !ShinigamiLogic.hasShinigamiPower(data) || ShinigamiLogic.isPowerLent(data, now)
                || !VisoredLogic.enabled() || host.level().dimension() != duel.level || duel.hollow.isRemoved()
                || horizontalDistance(host.position(), duel.centre) > radius + VisoredLogic.LEAVE_MARGIN
                || host.level().getDifficulty() == Difficulty.PEACEFUL;
    }

    /**
     * Pushes every creature and player but the two fighters (and what the host rides) back out of the ring, and
     * tells a pushed player why. Spectators and creative players pass.
     */
    private static void keepOut(ServerLevel level, Player host, Duel duel, int radius) {
        double reach = radius + 2.0;
        Vec3 centre = duel.centre;
        AABB box = new AABB(centre.x - reach, centre.y - KEEP_OUT_HEIGHT, centre.z - reach,
                centre.x + reach, centre.y + KEEP_OUT_HEIGHT, centre.z + reach);
        double inside = radius + 1.0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, creature -> creature != duel.hollow
                && creature.isAlive() && creature.getRootVehicle() != host.getRootVehicle())) {
            if (horizontalDistance(mob.position(), centre) <= inside) {
                push(mob, centre, false);
            }
        }
        for (Player other : level.getEntitiesOfClass(Player.class, box, player -> player != host && player.isAlive()
                && !player.isSpectator() && !player.isCreative())) {
            if (horizontalDistance(other.position(), centre) <= inside) {
                push(other, centre, false);
                bar(other, "duel.barrier");
            }
        }
    }

    /** Creatures nearby that have taken either fighter as their target drop it, the brain's memory too. */
    private static void dropGrudges(ServerLevel level, Player host, Duel duel) {
        AABB box = new AABB(duel.centre, duel.centre).inflate(AGGRO_RANGE);
        InnerHollow hollow = duel.hollow;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, creature -> creature != hollow)) {
            LivingEntity target = mob.getTarget();
            if (target == host || target == hollow) {
                mob.setTarget(null);
            }
            if (mob.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, host)
                    || mob.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, hollow)) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    /**
     * Sets the entity moving at {@link VisoredLogic#PUSH_STRENGTH} straight towards the centre, or straight away from
     * it, along the ground, and a little up, and has it sent to the clients.
     */
    private static void push(Entity entity, Vec3 centre, boolean inward) {
        Vec3 away = new Vec3(entity.getX() - centre.x, 0.0, entity.getZ() - centre.z);
        Vec3 direction = away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        if (inward) {
            direction = direction.reverse();
        }
        entity.setDeltaMovement(direction.x * VisoredLogic.PUSH_STRENGTH, PUSH_LIFT,
                direction.z * VisoredLogic.PUSH_STRENGTH);
        entity.hurtMarked = true;
    }

    /** Whether another fight in this world stands so close that the two rings would meet. */
    private static boolean crowded(ServerLevel level, Vec3 centre) {
        double apart = 2.0 * VisoredLogic.arenaRadius() + 2.0;
        for (Duel other : DUELS.values()) {
            if (other.level == level.dimension() && horizontalDistance(other.centre, centre) <= apart) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where the Hollow steps out: the first of eight points {@link VisoredLogic#SPAWN_DISTANCE} blocks from the host,
     * starting straight ahead and turning by 45°, with room for its body and footing within two blocks below; the
     * host's own spot if there is none.
     */
    private static Vec3 spawnSpot(ServerLevel level, Player host, EntityType<?> type) {
        for (int i = 0; i < SPAWN_TRIES; i++) {
            Vec3 ahead = Vec3.directionFromRotation(0.0F, host.getYRot() + i * SPAWN_TURN_DEGREES);
            Vec3 spot = host.position().add(ahead.x * VisoredLogic.SPAWN_DISTANCE, 0.0,
                    ahead.z * VisoredLogic.SPAWN_DISTANCE);
            if (level.noCollision(type.getSpawnAABB(spot.x, spot.y, spot.z)) && hasFooting(level, spot)) {
                return spot;
            }
        }
        return host.position();
    }

    private static boolean hasFooting(ServerLevel level, Vec3 spot) {
        BlockPos feet = BlockPos.containing(spot);
        for (int down = 1; down <= SPAWN_DROP_BLOCKS; down++) {
            BlockPos below = feet.below(down);
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return true;
            }
        }
        return false;
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- Listeners ----

    /**
     * A blow that landed on a player may be a wound: only one with damage from a creature, read once the health
     * after it is known.
     */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || !SoulLogic.hasSoul(player)
                || event.getNewDamage() <= 0.0F || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (VisoredLogic.isWoundingSource(attacker, data)
                && VisoredLogic.isWound(player.getHealth(), player.getMaxHealth())) {
            noteWound(player, data, player.level().getGameTime());
        }
    }

    /**
     * Nothing living but its own Hollow can touch a host while it fights: the blow of any other creature or player is
     * stopped before anything else weighs it. Blocks, lava and falls still hurt. A mundane creature's blow would
     * otherwise land, as spirit sight never shields a player (see {@link SpiritEvents#blindToSpirit}).
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player host) || host.level().isClientSide()) {
            return;
        }
        Duel duel = DUELS.get(host.getUUID());
        if (duel != null && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != host
                && attacker != duel.hollow) {
            event.setCanceled(true);
        }
    }

    /**
     * The Hollow's losing blow, once armour and every other listener have had their say and before absorption: cut
     * so it leaves its host at exactly {@code loss_health}, and the fight is lost. A loss never kills.
     */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player host) || host.level().isClientSide()) {
            return;
        }
        Duel duel = DUELS.get(host.getUUID());
        DamageSource source = event.getSource();
        if (duel == null || source.getEntity() != duel.hollow) {
            return;
        }
        float health = host.getHealth();
        float absorption = host.getAbsorptionAmount();
        if (VisoredLogic.isLosingBlow(health, absorption, event.getNewDamage())) {
            event.setNewDamage(VisoredLogic.clampedBlow(health, absorption,
                    (float) BleachConfig.get(BleachConfig.INNER_HOLLOW_LOSS_HEALTH)));
            endDuel(host, SoulLogic.get(host), DuelEnd.LOSS, host.level().getGameTime());
        }
    }

    /**
     * A host that dies breaks its fight off. A Hollow killed by its host is beaten; one that died any other way
     * ({@code /kill}) breaks the fight off.
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        long now = victim.level().getGameTime();
        if (victim instanceof InnerHollow hollow) {
            Duel duel = duel(hollow.hostId());
            if (duel == null || duel.hollow != hollow) {
                return;
            }
            if (event.getSource().getEntity() instanceof Player killer && killer.getUUID().equals(duel.hostId)) {
                endDuel(killer, SoulLogic.get(killer), DuelEnd.WIN, now);
            } else {
                endDuel(duel.host, SoulLogic.get(duel.host), DuelEnd.BROKEN_OFF, now);
            }
        } else if (victim instanceof Player player && isDuelling(player)) {
            endDuel(player, SoulLogic.get(player), DuelEnd.BROKEN_OFF, now);
        }
    }

    /** A fight does not outlast the session. This runs before the player is saved, so the wait for the next one is. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        endAll(player, SoulLogic.get(player), player.level().getGameTime());
    }

    /**
     * A stop saves every player before any of them logs out, so every fight is broken off here first, or the saves
     * would hold the fight's end as the next try instead of the wait.
     */
    private static void onServerStopping(ServerStoppingEvent event) {
        breakOffAll();
    }

    // ---- Lines ----

    /** The Hollow speaks to its host, on the action bar in white italics. */
    private static void speak(Player player, String key) {
        player.displayClientMessage(Component.translatable(SPEECH + key)
                .withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC), true);
    }

    /** A line about the fight on the action bar, in grey, as Jinzen's are. */
    private static void bar(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(PREFIX + key, args).withStyle(ChatFormatting.GRAY), true);
    }

    /** A line in chat. */
    private static void chat(Player player, String key, ChatFormatting[] style, Object... args) {
        player.sendSystemMessage(Component.translatable(PREFIX + key, args).withStyle(style));
    }

    private static Component barName(long endsAt, long now) {
        return Component.translatable("bossbar.bleach.inner_hollow", VisoredLogic.secondsUntil(endsAt, now));
    }

    /** White dust bursting from the Hollow as it steps out or goes. */
    private static void burst(ServerLevel level, InnerHollow hollow) {
        SpiritSenses.particles(level, WHITE_DUST, hollow.getX(), hollow.getY() + hollow.getBbHeight() * 0.5,
                hollow.getZ(), BURST, BURST_SPREAD, BURST_SPREAD_UP, BURST_SPREAD, 0.0);
    }

    /** The ring's pale light, a little above where the host first stood. */
    private static void ring(ServerLevel level, Duel duel) {
        int radius = VisoredLogic.arenaRadius();
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = i * (Math.PI * 2.0 / RING_POINTS);
            SpiritSenses.particles(level, RING_DUST, duel.centre.x + Math.cos(angle) * radius,
                    duel.centre.y + RING_LIFT, duel.centre.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
