package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.FlyingAnimal;
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
 * The Visored on the server: the Hollow taking root in a Shinigami's wounds, its voice, the fight with it, and its mask.
 *
 * <p>Near-fatal blows from Menos-class Hollows ({@link VisoredLogic#isWoundingSource}) are counted as wounds, and
 * enough of them root the Hollow ({@link #takeRoot}), which then speaks to its host every 5 minutes. Once it is
 * ready, sitting in Jinzen brings it out ({@link #beginDuel}, from {@link JinzenLogic}) as an {@link InnerHollow}
 * in a ring around where the host sat: the host is kept in and everyone else out, the two can touch nothing
 * living but each other, and the fight has a time limit. Beating it makes the soul a Visored ({@link #subdue}).
 * Losing never kills: the Hollow's last blow stops at {@code loss_health}. Death, logging out, another world,
 * Peaceful, the power lost or lent, spectator or creative mode, a server stop or getting far out of the ring break
 * it off with no loss counted. Every end goes through {@link #endDuel}.
 *
 * <p>The fights are held only in memory, in a table on the server thread, and the Hollow is never saved, so a
 * save holds nothing of a fight but its host's record, which at the start already says when the next try may
 * come. Everything takes a {@link Player}, so game tests can use mock players; the boss bar is for a real
 * {@link ServerPlayer} only.
 *
 * <p>A Visored calls its beaten Hollow's mask with the Hollow Mask key ({@link #onMaskKey}, {@link #putOnMask}): it
 * costs a share of maximum reiryoku and then drains it, stops regeneration, and gives speed, melee damage and
 * weightier reiatsu, for {@code base_seconds} at first and longer the more masks have been worn. The key again takes
 * it off; it also crumbles when its time is up, runs dry with the reiryoku, shatters under blows, and is taken off
 * on death, logging out, a server stop and whenever its soul may no longer wear it. Every end goes through
 * {@link #endMask}, which starts the wait for the next. The mask is on while the saved record says so, so the
 * owner's client sees it, and a save that still holds one (a crash) has it taken off at the next login.
 *
 * <p>Before the win, a blow that brings its host near death may let the Hollow seize the body for a few seconds
 * ({@link #startTakeover}), and a Visored who looses the black Getsuga Tenshō at low health loses control the same way:
 * it hits harder and heals, no blow takes it below {@code loss_health}, and the host's Shinigami arts do not answer it
 * until the Hollow Mask key tears the mask off or the time runs out. Every end goes through {@link #endTakeover}. With
 * a server's leave, a fight lost once too often makes the soul a Hollow ({@link #fall}). A takeover is held only in
 * transient fields of the soul, so nothing of it is saved or sent but its look.
 */
public final class VisoredEvents {
    private static final String PREFIX = "message.bleach.visored.";
    private static final String SPEECH = PREFIX + "speech.";
    /** Chat styles: the Hollow's stirring, the fight's own lines, and the win. */
    private static final ChatFormatting[] MUTED = {ChatFormatting.GRAY, ChatFormatting.ITALIC};
    private static final ChatFormatting[] PLAIN = {ChatFormatting.GRAY};
    private static final ChatFormatting[] GOLDEN = {ChatFormatting.GOLD};
    /** Chat styles: the Hollow seizing the body, and the soul falling to it. */
    private static final ChatFormatting[] SEIZED = {ChatFormatting.RED};
    private static final ChatFormatting[] FALLEN = {ChatFormatting.DARK_RED, ChatFormatting.BOLD};
    private static final String DEVOUR_KEY = "key.bleach.devour";

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
    /** How far out a push is followed to see where it ends, in blocks; it carries a body about this far. */
    private static final int PUSH_CHECK_BLOCKS = 3;
    /** How far below a pushed body's feet the ground may be, in blocks: a drop too short to hurt. */
    private static final double PUSH_DROP_BLOCKS = 2.0;
    private static final int RING_POINTS = 36;
    private static final double RING_LIFT = 0.2;
    private static final int BURST = 30;
    private static final int ROOT_BURST = 40;
    private static final double BURST_SPREAD = 0.4;
    private static final double BURST_SPREAD_UP = 1.0;
    private static final DustParticleOptions WHITE_DUST = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.2F);
    private static final DustParticleOptions RING_DUST = new DustParticleOptions(new Vector3f(0.92F, 0.92F, 0.96F), 1.0F);
    /** Black reiatsu drawn across the face as the mask forms; its endings shed {@link #WHITE_DUST}. */
    private static final DustParticleOptions BLACK_DUST = new DustParticleOptions(new Vector3f(0.08F, 0.04F, 0.08F), 1.0F);
    private static final int MASK_ON_DUST = 16;
    private static final int MASK_OFF_DUST = 8;
    private static final int MASK_CRUMBLE_DUST = 16;
    private static final int MASK_SHATTER_DUST = 24;
    private static final double MASK_DUST_SPREAD = 0.25;
    /** How far in front of the eyes, along the look, the black reiatsu gathers, in blocks. */
    private static final double MASK_FACE_AHEAD = 0.3;
    /** The sculk's charge as the Hollow's mask forms by itself, lower than a mask called. */
    private static final float TAKEOVER_PITCH = 0.5F;

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
     * Wounds and blows near death after they land; the host's shield first of all, so nothing else weighs a blow that
     * should not land; the loss clamp and a takeover's floor last of all, after every other change to the blow; and the
     * ends that come from outside the host's tick: deaths, logging out, another world and the server stopping.
     */
    public static void register(IEventBus bus) {
        bus.addListener(VisoredEvents::onDamageTaken);
        bus.addListener(EventPriority.HIGHEST, VisoredEvents::onIncomingDamage);
        bus.addListener(EventPriority.LOWEST, VisoredEvents::onDamagePre);
        bus.addListener(VisoredEvents::onDeath);
        bus.addListener(VisoredEvents::onLoggedOut);
        bus.addListener(VisoredEvents::onChangedDimension);
        bus.addListener(VisoredEvents::onServerStopping);
    }

    /**
     * Each tick of a living player, server side: the fight's upkeep for its host, then, outside a fight, the fall a
     * fight lost once too often left, the takeover's upkeep while the Hollow holds the body, the mask's upkeep while it
     * is on and the Hollow's voice, silent while it holds the body. A masked soul is beaten, so it never has a fight.
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
        if (data.fallPending) {
            // Lost once too often, out of the damage pipeline the losing blow may have come from.
            data.fallPending = false;
            fall(player, data, now);
            return;
        }
        boolean taken = VisoredLogic.takenOver(data);
        if (taken) {
            takeoverUpkeep(player, data, now);
        }
        if (VisoredLogic.isMasked(data)) {
            maskUpkeep(player, data, now);
        }
        String voice = VisoredLogic.voiceKey(data, player.getUUID(), now);
        if (voice != null && !taken) {
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

    /**
     * Brings the record's clocks into this world's time, on login, respawn and reset (see {@link SoulEvents#refresh}),
     * and takes off, silently, a mask the save still holds (a crash, an autosave, or leaving the End): it counts as
     * worn up to now, and its wait starts now. A mask never resumes.
     */
    public static void onRefresh(Player player, SoulData data) {
        long now = player.level().getGameTime();
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        ShinigamiProgress.Visored normal = VisoredLogic.normalise(record, now);
        if (!normal.equals(record)) {
            VisoredLogic.setRecord(data, normal);
        }
        if (normal.masked()) {
            endMask(player, data, MaskEnd.REMOVED, now);
        }
    }

    /**
     * Ends a takeover (REMOVED) and a fight (BROKEN_OFF), and takes the mask off (REMOVED): for logging out, resets and
     * commands.
     */
    public static void endAll(Player player, SoulData data, long now) {
        endTakeover(player, data, TakeoverEnd.REMOVED, now);
        if (isDuelling(player)) {
            endDuel(player, data, DuelEnd.BROKEN_OFF, now);
        }
        endMask(player, data, MaskEnd.REMOVED, now);
    }

    // ---- The fight ----

    /**
     * Brings the Hollow out to fight its host where the host stands. The first check that fails gives the result: a
     * fight already under way, the path turned off (even when forced), no Shinigami power or lent away, the Hollow
     * already beaten, not ready yet or in creative or spectator mode (both skipped when forced), Peaceful, and another
     * fight too close by. A forced fight roots an unrooted soul silently first. A Hollow holding its host's body lets go
     * of it once it has stepped out. On the client, nothing happens and the result is {@link DuelStart#NOT_READY}.
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
        // Either would break the fight off on its first tick, so Jinzen's inner world opens instead.
        if (!force && (host.isCreative() || host.isSpectator())) {
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
        // The Hollow comes out to fight, and lets go of the body.
        endTakeover(host, data, TakeoverEnd.REMOVED, now);
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
     * {@code retry_minutes}, and, with {@code fall_into_hollow} on, the loss that brings the count to
     * {@code losses_to_fall} makes the soul a Hollow at its next tick ({@link #fall}) instead of being told; a fight
     * broken off puts the next off as long, and counts nothing.
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
                int losses = record.losses() + 1;
                VisoredLogic.setRecord(data, record.withLosses(losses).withDuelReadyAt(now + VisoredLogic.retryTicks()));
                host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, VisoredLogic.LOSS_EFFECT_TICKS));
                host.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, VisoredLogic.LOSS_EFFECT_TICKS));
                if (VisoredLogic.fallsOnLoss(losses)) {
                    // The fall comes at the host's next tick (see tick): this may be inside the losing blow.
                    data.fallPending = true;
                } else {
                    chat(host, "lost", PLAIN, BleachConfig.get(BleachConfig.INNER_HOLLOW_RETRY_MINUTES));
                    speak(host, "lost");
                }
            }
            case BROKEN_OFF -> {
                VisoredLogic.setRecord(data, record.withDuelReadyAt(now + VisoredLogic.retryTicks()));
                bar(host, "duel.broken_off");
            }
        }
    }

    /**
     * The Hollow is beaten and the soul a Visored: said in chat, with the key that calls its mask, and the Hollow's
     * last words, "for now". The milestone follows from the record. The mask is ready at once, as a record that has
     * never had one waits for nothing.
     */
    public static void subdue(Player player, SoulData data, long now) {
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withSubduedAt(now));
        chat(player, "won", GOLDEN, Component.keybind("key.bleach.visored_mask"));
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
     * One tick of the host's fight, in this order: the reasons it breaks off, the time limit, the host got off
     * anything it rides, the ring keeping the host and its Hollow in, then every 5 ticks keeping everyone else out,
     * every 10 the ring's light, every second other creatures' grudges against the two dropped, the time left on the
     * bar and the Hollow's mending, and every tick the bar's progress.
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
        // A rider's own motion is thrown away each tick, so a mount would carry the host past the ring's push.
        if (host.isPassenger()) {
            host.stopRiding();
        }
        if (horizontalDistance(host.position(), duel.centre) > radius) {
            push(host, outward(host, duel.centre).reverse());
            bar(host, "duel.arena");
        }
        if (horizontalDistance(hollow.position(), duel.centre) > radius) {
            push(hollow, outward(hollow, duel.centre).reverse());
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
     * Pushes every creature and player but the two fighters back out of the ring, and tells a pushed player why,
     * unless the push would leave it somewhere unsafe ({@link #safeOutward}). Spectators, creative players and
     * players the host may not harm (PvP off, or a team without friendly fire) pass. Whoever is left inside can
     * touch neither fighter.
     */
    private static void keepOut(ServerLevel level, Player host, Duel duel, int radius) {
        double reach = radius + 2.0;
        Vec3 centre = duel.centre;
        AABB box = new AABB(centre.x - reach, centre.y - KEEP_OUT_HEIGHT, centre.z - reach,
                centre.x + reach, centre.y + KEEP_OUT_HEIGHT, centre.z + reach);
        double inside = radius + 1.0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, creature -> creature != duel.hollow
                && creature.isAlive())) {
            Vec3 out = outward(mob, centre);
            if (horizontalDistance(mob.position(), centre) <= inside && safeOutward(level, mob, out)) {
                push(mob, out);
            }
        }
        for (Player other : level.getEntitiesOfClass(Player.class, box, player -> player != host && player.isAlive()
                && !player.isSpectator() && !player.isCreative() && host.canHarmPlayer(player))) {
            Vec3 out = outward(other, centre);
            if (horizontalDistance(other.position(), centre) <= inside && safeOutward(level, other, out)) {
                push(other, out);
                bar(other, "duel.barrier");
            }
        }
    }

    /**
     * Whether a push this way leaves this body somewhere safe. Whatever flies always is. Anything else is followed a
     * block at a time for {@link #PUSH_CHECK_BLOCKS} blocks, until a wall that would stop it: each step needs ground
     * within {@link #PUSH_DROP_BLOCKS} below, with no lava, fire or the like where it would land. So no one is pushed
     * off a ledge, into the void or into lava.
     */
    private static boolean safeOutward(ServerLevel level, Entity entity, Vec3 direction) {
        if (entity.isNoGravity() || entity instanceof FlyingMob || entity instanceof FlyingAnimal) {
            return true;
        }
        AABB box = entity.getBoundingBox();
        Vec3 drop = new Vec3(0.0, -PUSH_DROP_BLOCKS - 1.0, 0.0);
        for (int step = 1; step <= PUSH_CHECK_BLOCKS; step++) {
            AABB ahead = box.move(direction.x * step, 0.0, direction.z * step);
            if (!level.noCollision(entity, ahead)) {
                return true;
            }
            Vec3 fall = Entity.collideBoundingBox(entity, drop, ahead, level, List.of());
            AABB landed = ahead.move(fall);
            if (-fall.y > PUSH_DROP_BLOCKS || SafeLanding.touchesLava(level, landed)
                    || SafeLanding.isHarmful(level, entity, landed)) {
                return false;
            }
        }
        return true;
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

    /** The way straight out from the centre along the ground, as a unit vector; east for a body right on it. */
    private static Vec3 outward(Entity entity, Vec3 centre) {
        Vec3 away = new Vec3(entity.getX() - centre.x, 0.0, entity.getZ() - centre.z);
        return away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
    }

    /**
     * Sets the entity moving this way along the ground at {@link VisoredLogic#PUSH_STRENGTH}, and a little up, and has
     * it sent to the clients.
     */
    private static void push(Entity entity, Vec3 direction) {
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

    // ---- The mask ----

    /** What came of calling the mask. Every one but PUT_ON, NO_HOLLOW and HELD is told here. */
    public enum MaskStart {
        PUT_ON(null), NO_HOLLOW(null), NOT_SUBDUED("mask.not_subdued"), DISABLED("disabled"),
        NO_POWER("no_power"), ALREADY("mask.already"), HELD(null), WAITING("mask.waiting"),
        TOO_WEAK("mask.too_weak");

        @Nullable
        private final String line;

        MaskStart(@Nullable String line) {
            this.line = line;
        }

        /** The action-bar key (PREFIX + line), or null. */
        @Nullable
        public String messageKey() {
            return line == null ? null : PREFIX + line;
        }
    }

    /** How a mask came off. */
    public enum MaskEnd {
        TAKEN_OFF, CRUMBLED, DRY, SHATTERED, REMOVED;

        /** CRUMBLED, DRY and SHATTERED: the long wait, and winded. */
        public boolean crumbles() {
            return this == CRUMBLED || this == DRY || this == SHATTERED;
        }
    }

    /**
     * The M key, server side: first of all it tears off the mask a Hollow holding the body has formed, and a Visored's
     * own mask with it; otherwise off if on, otherwise on, telling a refusal. Taking it off is always allowed, held,
     * lent or not.
     */
    public static void onMaskKey(Player player, SoulData data, long now) {
        if (VisoredLogic.takenOver(data)) {
            // Torn off, as Ichigo tore his (Ch. 166); a Visored's own mask comes away with it.
            endTakeover(player, data, TakeoverEnd.TORN_OFF, now);
            endMask(player, data, MaskEnd.TAKEN_OFF, now);
            return;
        }
        if (VisoredLogic.isMasked(data)) {
            endMask(player, data, MaskEnd.TAKEN_OFF, now);
            return;
        }
        MaskStart start = putOnMask(player, data, now, false);
        if (start != MaskStart.PUT_ON) {
            tell(player, data, start, now);
        }
    }

    /**
     * Calls the mask. Forced (the command) skips HELD, WAITING and TOO_WEAK and pays nothing.
     *
     * <p>The first check that fails gives the result: no Hollow at all (a soul that presses an unused key), one not
     * yet beaten (which covers a soul in its fight), the path off, no Shinigami power or lent away, the mask already
     * on, held by Negación or a Caja, the wait after the last mask, and a cost that would leave less than
     * {@link ZanpakutoLogic#RESERVE}; creative skips the wait and the cost. Then it pays, writes the record, puts the
     * boosts on, and the hand is drawn across the face in black reiatsu, with the sculk's charge, pitched low.
     */
    public static MaskStart putOnMask(Player player, SoulData data, long now, boolean force) {
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        if (!record.rooted() && !record.subdued()) {
            return MaskStart.NO_HOLLOW;
        }
        if (!record.subdued()) {
            return MaskStart.NOT_SUBDUED;
        }
        if (!VisoredLogic.enabled()) {
            return MaskStart.DISABLED;
        }
        if (!ShinigamiLogic.hasShinigamiPower(data) || ShinigamiLogic.isPowerLent(data, now)) {
            return MaskStart.NO_POWER;
        }
        if (record.masked()) {
            return MaskStart.ALREADY;
        }
        // Tells the held player its own line.
        if (!force && Negacion.refused(player)) {
            return MaskStart.HELD;
        }
        boolean free = force || player.isCreative();
        if (!free && now < record.maskReadyAt()) {
            return MaskStart.WAITING;
        }
        float cost = VisoredLogic.maskCost(data);
        if (!free && data.reiryoku() - cost < ZanpakutoLogic.RESERVE) {
            return MaskStart.TOO_WEAK;
        }
        if (!free) {
            SoulLogic.trySpend(player, data, cost);
        }
        VisoredLogic.setRecord(data, record.withMaskChangedAt(now)
                .withMaskEndsAt(now + VisoredLogic.maskDurationTicks(record.maskTrainedTicks())));
        data.maskDamageShare = 0.0F;
        applyMaskBody(player);
        // The hand drawn across the face; the swinging player sees it too.
        player.swing(InteractionHand.MAIN_HAND, true);
        if (player.level() instanceof ServerLevel level) {
            Vec3 face = player.getEyePosition().add(player.getLookAngle().scale(MASK_FACE_AHEAD));
            SpiritSenses.particles(level, BLACK_DUST, face.x, face.y, face.z, MASK_ON_DUST, MASK_DUST_SPREAD,
                    MASK_DUST_SPREAD, MASK_DUST_SPREAD, 0.0);
        }
        SpiritSenses.play(player, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.PLAYERS, 1.0F, 0.7F, false);
        return MaskStart.PUT_ON;
    }

    /** Tells a refusal on the action bar, in grey; WAITING with the whole seconds left. */
    public static void tell(Player player, SoulData data, MaskStart start, long now) {
        String key = start.messageKey();
        if (key == null) {
            return;
        }
        Object[] args = start == MaskStart.WAITING
                ? new Object[] {VisoredLogic.secondsUntil(VisoredLogic.record(data).maskReadyAt(), now)}
                : new Object[0];
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * The single exit: every way a mask comes off goes through here. Nothing when the mask is off.
     *
     * <p>The record is written once ({@link VisoredLogic#maskEnded}): the wait for the next mask starts, as long as
     * this one was worn and at least {@code min_wait_seconds}, or {@code crumble_wait_seconds} when it crumbled, ran
     * dry or shattered, and the time worn trains the next. Then the crack share is cleared and the boosts come off,
     * a masked Cero still gathering fizzles for free (one already fired is paid for), a crumbled, dry or shattered
     * mask leaves its wearer winded, and the ending is heard, seen and told. Creative waits for nothing, trains
     * nothing and is never winded. REMOVED, every way that is not the mask's own doing, is silent.
     */
    public static void endMask(Player player, SoulData data, MaskEnd end, long now) {
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        if (!record.masked()) {
            return;
        }
        boolean creative = player.isCreative();
        VisoredLogic.setRecord(data, VisoredLogic.maskEnded(record, now, end.crumbles(), creative));
        data.maskDamageShare = 0.0F;
        removeMaskBody(player);
        // A Cero gathering in the mask fizzles with it, for free; one already fired is paid for.
        HollowCero.cancel(player, data);
        if (end.crumbles() && !creative) {
            SoulLogic.exhaust(player);
        }
        switch (end) {
            case TAKEN_OFF -> maskFalls(player, 0.6F, 1.3F, MASK_OFF_DUST, null);
            case CRUMBLED -> maskFalls(player, 1.0F, 0.9F, MASK_CRUMBLE_DUST, "mask.crumbled");
            case DRY -> maskFalls(player, 1.0F, 0.9F, MASK_CRUMBLE_DUST, "mask.dry");
            case SHATTERED -> maskFalls(player, 1.0F, 0.7F, MASK_SHATTER_DUST, "mask.shattered");
            case REMOVED -> {
            }
        }
    }

    /**
     * A creature's blow landed on a masked wearer. The health it took, after armour and absorption, counts as a share
     * of maximum health; half of {@code shatter_health_share} in one wearing cracks the mask, heard once, which breaks
     * a masked Cero's gathering, and all of it shatters it. A new wearing starts whole.
     */
    public static void noteMaskBlow(Player player, SoulData data, float damage, long now) {
        if (!VisoredLogic.isMasked(data) || !(damage > 0.0F)) {
            return;
        }
        boolean wasCracked = VisoredLogic.maskCracked(data);
        data.maskDamageShare += damage / Math.max(1.0F, player.getMaxHealth());
        if (data.maskDamageShare >= BleachConfig.get(BleachConfig.VISORED_MASK_SHATTER_HEALTH_SHARE)) {
            endMask(player, data, MaskEnd.SHATTERED, now);
        } else if (!wasCracked && VisoredLogic.maskCracked(data)) {
            // A fresh crack breaks a masked Cero's gathering, as a Hollow's does (HollowCero#tick).
            data.hollowFreshCrackAt = now;
            SpiritSenses.play(player, ModSounds.MASK_CRACK.get(), SoundSource.PLAYERS, 0.8F, 1.2F, false);
        }
    }

    /** Ends each player's mask REMOVED at its own level's game time: the server-stop path. */
    public static void endMasks(Iterable<? extends Player> players) {
        for (Player player : players) {
            endMask(player, SoulLogic.get(player), MaskEnd.REMOVED, player.level().getGameTime());
        }
    }

    /**
     * One tick of the mask on, in this order: it is taken off at once when its soul may no longer wear it (no
     * longer a Visored, lent, the path off, a spectator, asleep or dead); it crumbles when its time is up; it drains
     * its share of reiryoku, outside creative and while the world's time runs (a frozen server still ticks its
     * players), and runs dry when that would leave less than half a point; and the boosts are set again, which puts
     * back anything that took them off.
     */
    private static void maskUpkeep(Player player, SoulData data, long now) {
        if (!VisoredLogic.isVisored(data) || ShinigamiLogic.isPowerLent(data, now) || !VisoredLogic.enabled()
                || player.isSpectator() || player.isSleeping() || !player.isAlive()) {
            endMask(player, data, MaskEnd.REMOVED, now);
            return;
        }
        if (now >= VisoredLogic.record(data).maskEndsAt()) {
            endMask(player, data, MaskEnd.CRUMBLED, now);
            return;
        }
        if (!player.isCreative() && player.level().tickRateManager().runsNormally()) {
            float drain = VisoredLogic.maskDrainPerTick(data);
            if (drain > 0.0F && !SoulLogic.drain(player, data, drain)) {
                endMask(player, data, MaskEnd.DRY, now);
                return;
            }
        }
        applyMaskBody(player);
    }

    /**
     * The mask's speed and melee damage, as transient modifiers through {@link HollowBody#setModifier}, which writes
     * only when they change: never saved, so no save holds them.
     */
    private static void applyMaskBody(Player player) {
        HollowBody.setModifier(player, Attributes.MOVEMENT_SPEED, VisoredLogic.MASK_SPEED_ID,
                BleachConfig.get(BleachConfig.VISORED_MASK_SPEED_BONUS), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        HollowBody.setModifier(player, Attributes.ATTACK_DAMAGE, VisoredLogic.MASK_DAMAGE_ID,
                BleachConfig.get(BleachConfig.VISORED_MASK_DAMAGE_MULTIPLIER) - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Takes both of the mask's modifiers off. */
    private static void removeMaskBody(Player player) {
        HollowBody.setModifier(player, Attributes.MOVEMENT_SPEED, VisoredLogic.MASK_SPEED_ID, 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        HollowBody.setModifier(player, Attributes.ATTACK_DAMAGE, VisoredLogic.MASK_DAMAGE_ID, 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /**
     * A mask coming away: bone breaking at this volume and pitch, white dust at the eyes for those who see spirits,
     * and the line, if any, on the action bar.
     */
    private static void maskFalls(Player player, float volume, float pitch, int dust, @Nullable String line) {
        SpiritSenses.play(player, ModSounds.MASK_CRACK.get(), SoundSource.PLAYERS, volume, pitch, false);
        if (player.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, WHITE_DUST, player.getX(), player.getEyeY(), player.getZ(), dust,
                    MASK_DUST_SPREAD, MASK_DUST_SPREAD, MASK_DUST_SPREAD, 0.0);
        }
        if (line != null) {
            bar(player, line);
        }
    }

    // ---- The Hollow's hold ----

    /** How the Hollow let go of the body. */
    public enum TakeoverEnd {
        /** The Hollow Mask key tore its mask off: wearied. */
        TORN_OFF,
        /** Its time ran out: wearied. */
        RAN_OUT,
        /** Every way that is not the takeover's own doing: silent, and no weariness. */
        REMOVED
    }

    /**
     * The Hollow seizes the body, half a mask forming over its host's face, or behind the mask a Visored wears when
     * {@code masked}. False, changing nothing, off a server level, with the path off, unrooted, without Shinigami power or
     * lent, already seized, in a fight with its Hollow, dead, in creative or spectator mode, or held by Negación; and for
     * a kind that does not fit the soul: half a mask once its Hollow is beaten, or {@code masked} without the mask on
     * ({@code masked ? !VisoredLogic.isMasked(data) : VisoredLogic.record(data).subdued()}). Callers decide when (a blow
     * near death, the black Getsuga Tenshō, the command); this checks only what may never be.
     *
     * <p>For {@code seconds} ({@code masked_seconds} behind a mask) it then hits harder, heals each second and no blow
     * takes it below {@code loss_health}, while its Shinigami arts do not answer it; a Getsuga Tenshō gathering is let
     * go. The mask forms in white reiatsu at the face with the sculk's charge, pitched lower than a mask called, and the
     * Hollow's stir, for those who see spirits; the host is told in chat how to tear it off, and the Hollow speaks.
     */
    public static boolean startTakeover(Player player, SoulData data, boolean masked, long now) {
        if (!(player.level() instanceof ServerLevel level) || !VisoredLogic.enabled() || !VisoredLogic.isRooted(data)
                || !ShinigamiLogic.hasShinigamiPower(data) || ShinigamiLogic.isPowerLent(data, now)
                || VisoredLogic.takenOver(data) || isDuelling(player) || !player.isAlive() || player.isCreative()
                || player.isSpectator() || Negacion.held(player)) {
            return false;
        }
        if (masked ? !VisoredLogic.isMasked(data) : VisoredLogic.record(data).subdued()) {
            return false;
        }
        data.lastTakeoverAt = now;
        data.takeoverEndsAt = now + VisoredLogic.takeoverTicks(masked);
        ShikaiEvents.cancelGather(player, data);
        applyTakeoverBody(player);
        Vec3 face = player.getEyePosition().add(player.getLookAngle().scale(MASK_FACE_AHEAD));
        SpiritSenses.particles(level, WHITE_DUST, face.x, face.y, face.z, MASK_ON_DUST, MASK_DUST_SPREAD,
                MASK_DUST_SPREAD, MASK_DUST_SPREAD, 0.0);
        SpiritSenses.play(player, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.PLAYERS, 1.0F, TAKEOVER_PITCH, false);
        SpiritSenses.play(player, ModSounds.HOLLOW_AMBIENT.get(), SoundSource.PLAYERS, 0.8F, 1.2F, false);
        chat(player, "takeover.begun", SEIZED, Component.keybind("key.bleach.visored_mask"));
        speak(player, masked ? "takeover.masked" : "takeover");
        if (player instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.TAKEN_OVER);
        }
        return true;
    }

    /**
     * The single exit: every way the Hollow lets go of the body goes through here. Nothing when it holds none.
     *
     * <p>The boost comes off. REMOVED stops there, silent. A mask torn off or a time run out leaves the host slow and
     * weak for {@code weariness_seconds}, and the half mask falls away in bone and white dust; a tear-off is told, and a
     * Hollow whose time ran out has the last word.
     */
    public static void endTakeover(Player player, SoulData data, TakeoverEnd end, long now) {
        if (!VisoredLogic.takenOver(data)) {
            return;
        }
        data.takeoverEndsAt = ShinigamiProgress.NEVER;
        removeTakeoverBody(player);
        if (end == TakeoverEnd.REMOVED) {
            return;
        }
        int weariness = BleachConfig.get(BleachConfig.VISORED_TAKEOVER_WEARINESS_SECONDS) * SECOND_TICKS;
        if (weariness > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, weariness));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weariness));
        }
        maskFalls(player, 0.8F, 1.1F, MASK_OFF_DUST, end == TakeoverEnd.TORN_OFF ? "takeover.torn_off" : null);
        if (end == TakeoverEnd.RAN_OUT) {
            speak(player, "takeover.ended");
        }
    }

    /** Ends each player's takeover REMOVED at its own level's game time: the server-stop path. */
    public static void endTakeovers(Iterable<? extends Player> players) {
        for (Player player : players) {
            endTakeover(player, SoulLogic.get(player), TakeoverEnd.REMOVED, player.level().getGameTime());
        }
    }

    /**
     * A creature's blow that landed, with the health before it: on a soul whose Hollow may seize it
     * ({@link VisoredLogic#mayBeTakenOver}), one that brought it near death ({@link VisoredLogic#isTakeoverBlow}) lets
     * the Hollow in at the chance {@link VisoredLogic#takeoverChance} gives. Never its own Hollow's blow (the fight's
     * losing blow leaves its host near death), never in a fight, and never on Peaceful, where the Hollow stays quiet, as
     * it does for the fight.
     */
    private static void noteNearDeath(Player player, SoulData data, LivingEntity attacker, float healthBefore, long now) {
        if (attacker instanceof InnerHollow || isDuelling(player) || !VisoredLogic.mayBeTakenOver(data, now)
                || !VisoredLogic.isTakeoverBlow(healthBefore, player.getHealth(), player.getMaxHealth())
                || player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        double chance = VisoredLogic.takeoverChance(data.lastGetsugaAt, now);
        if (VisoredLogic.takeoverRolled(player.getRandom().nextDouble(), chance)) {
            startTakeover(player, data, false, now);
        }
    }

    /**
     * One tick while the Hollow holds the body, in this order: it lets go at once (REMOVED) when its host may no longer
     * be held (the path off, no longer rooted, no Shinigami power or lent, dead, in creative or spectator mode, asleep
     * or held by Negación); it heals its host on each whole second from the start, while the world's time runs (a frozen
     * server still ticks its players); it lets go (RAN_OUT) when its time is up, a last second healed first; and the
     * boost is set again, which puts back anything that took it off. A masked Visored's mask that crumbles meanwhile
     * leaves the takeover going under the half mask: there is no end for that.
     */
    private static void takeoverUpkeep(Player player, SoulData data, long now) {
        if (!VisoredLogic.enabled() || !VisoredLogic.isRooted(data) || !ShinigamiLogic.hasShinigamiPower(data)
                || ShinigamiLogic.isPowerLent(data, now) || !player.isAlive() || player.isCreative()
                || player.isSpectator() || player.isSleeping() || Negacion.held(player)) {
            endTakeover(player, data, TakeoverEnd.REMOVED, now);
            return;
        }
        long elapsed = now - data.lastTakeoverAt;
        if (elapsed > 0L && elapsed % SECOND_TICKS == 0L && player.level().tickRateManager().runsNormally()) {
            float heal = (float) BleachConfig.get(BleachConfig.VISORED_TAKEOVER_HEAL_PER_SECOND);
            if (heal > 0.0F) {
                player.heal(heal);
            }
        }
        if (now >= data.takeoverEndsAt) {
            endTakeover(player, data, TakeoverEnd.RAN_OUT, now);
            return;
        }
        applyTakeoverBody(player);
    }

    /**
     * The Hollow's melee damage, a transient modifier through {@link HollowBody#setModifier}, which writes only when it
     * changes: never saved, so no save holds it. It multiplies with a mask's own.
     */
    private static void applyTakeoverBody(Player player) {
        HollowBody.setModifier(player, Attributes.ATTACK_DAMAGE, VisoredLogic.TAKEOVER_DAMAGE_ID,
                BleachConfig.get(BleachConfig.VISORED_TAKEOVER_DAMAGE_BONUS),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Takes the Hollow's melee damage off. */
    private static void removeTakeoverBody(Player player) {
        HollowBody.setModifier(player, Attributes.ATTACK_DAMAGE, VisoredLogic.TAKEOVER_DAMAGE_ID, 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /**
     * The soul falls to its Hollow and becomes one, at the tick after the fight lost once too often: checked again
     * here (the path and {@code fall_into_hollow} on, rooted and not beaten, with Shinigami power, alive, and not in
     * creative or spectator mode), and otherwise nothing happens. Any takeover ends and a released blade seals without a
     * word, the Hollow is gone from the record (a soul purified later starts over with none), and the soul becomes a plain
     * Hollow as a Plus whose chain ran out does ({@link SoulLogic#hollowfy}), heard by all, with the Hollow's word and
     * the hint to devour. It keeps its strength, its stats, its zanpakutō and every other record; its Hollow body comes
     * on by itself next tick.
     */
    private static void fall(Player player, SoulData data, long now) {
        ShinigamiProgress.Visored record = VisoredLogic.record(data);
        if (!VisoredLogic.enabled() || !BleachConfig.get(BleachConfig.VISORED_FALL_INTO_HOLLOW) || !record.rooted()
                || record.subdued() || !ShinigamiLogic.hasShinigamiPower(data) || !player.isAlive()
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        endTakeover(player, data, TakeoverEnd.REMOVED, now);
        ShikaiEvents.forceSeal(player, data, null);
        VisoredLogic.setRecord(data, ShinigamiProgress.Visored.NONE);
        SoulLogic.hollowfy(player, data);
        // Heard by all, as a Plus's chain breaking is: a Hollow is born where anyone stands.
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 1.5F, 0.6F);
        chat(player, "fallen", FALLEN);
        player.sendSystemMessage(Component.translatable("message.bleach.hollow_player.hint", Component.keybind(DEVOUR_KEY))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        speak(player, "fallen");
    }

    // ---- Listeners ----

    /**
     * A blow that landed on a player may crack its mask, may let a rooted Hollow seize the body, and may be a wound: only
     * one with damage from a creature, read once the health after it is known, so only the health it took counts. A
     * wearer's own blows never crack its mask or let its Hollow in, and a killing blow leaves both to death's ending.
     */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || !SoulLogic.hasSoul(player)
                || event.getNewDamage() <= 0.0F || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        if (attacker != player && !player.isDeadOrDying()) {
            noteMaskBlow(player, data, event.getNewDamage(), now);
            // Before the wound, so the blow that roots a Hollow never lets it in at once. Post's new damage is the
            // health lost.
            noteNearDeath(player, data, attacker, player.getHealth() + event.getNewDamage(), now);
        }
        if (VisoredLogic.isWoundingSource(attacker, data)
                && VisoredLogic.isWound(player.getHealth(), player.getMaxHealth())) {
            noteWound(player, data, now);
        }
    }

    /**
     * While a host fights, nothing living but its own Hollow can touch it, and it can touch nothing living but its
     * Hollow: the blow of any other creature or player on the host, and any blow of the host's (its arrows, waves
     * and blasts too) on anything else, is stopped before anything else weighs it. Blocks, lava and falls still hurt
     * the host. A mundane creature's blow would otherwise land, as spirit sight never shields a player (see
     * {@link SpiritEvents#blindToSpirit}).
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == victim) {
            return;
        }
        Duel guarded = victim instanceof Player host ? DUELS.get(host.getUUID()) : null;
        Duel striking = attacker instanceof Player striker ? DUELS.get(striker.getUUID()) : null;
        if (guarded != null && attacker != guarded.hollow || striking != null && victim != striking.hollow) {
            event.setCanceled(true);
        }
    }

    /**
     * The Hollow's losing blow, once armour and every other listener have had their say and before absorption: cut
     * so it leaves its host at {@code loss_health} (or where it was, if already lower), and the fight is lost. A loss
     * never kills. And, while the Hollow holds a body, any blow but one that passes invulnerability is cut the same
     * way, so a takeover never kills.
     */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player host) || host.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        float health = host.getHealth();
        float absorption = host.getAbsorptionAmount();
        float floor = (float) BleachConfig.get(BleachConfig.INNER_HOLLOW_LOSS_HEALTH);
        Duel duel = DUELS.get(host.getUUID());
        if (duel != null && source.getEntity() == duel.hollow) {
            if (VisoredLogic.isLosingBlow(health, absorption, event.getNewDamage())) {
                event.setNewDamage(VisoredLogic.clampedBlow(event.getNewDamage(), health, absorption, floor));
                endDuel(host, SoulLogic.get(host), DuelEnd.LOSS, host.level().getGameTime());
            }
            return;
        }
        // The Hollow holding the body will not let it die (Ch. 165), though /kill and the void still can.
        if (SoulLogic.hasSoul(host) && VisoredLogic.takenOver(SoulLogic.get(host))
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && VisoredLogic.isLosingBlow(health, absorption, event.getNewDamage())) {
            event.setNewDamage(VisoredLogic.clampedBlow(event.getNewDamage(), health, absorption, floor));
        }
    }

    /**
     * A host that dies breaks its fight off, a Hollow holding a body that dies lets go of it, and a Visored that dies
     * takes its mask off, before the soul is copied to the respawned player. A Hollow killed by its host is beaten; one
     * that died any other way ({@code /kill}) breaks the fight off.
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
        } else if (victim instanceof Player player) {
            endTakeover(player, SoulLogic.get(player), TakeoverEnd.REMOVED, now);
            if (isDuelling(player)) {
                endDuel(player, SoulLogic.get(player), DuelEnd.BROKEN_OFF, now);
            }
            endMask(player, SoulLogic.get(player), MaskEnd.REMOVED, now);
        }
    }

    /**
     * A takeover, a fight and a mask do not outlast the session. This runs before the player is saved, so the waits for
     * the next are.
     */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        endAll(player, SoulLogic.get(player), player.level().getGameTime());
    }

    /** The Hollow holding a body lets go of it in another world, as a fight breaks off there. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !SoulLogic.hasSoul(player)) {
            return;
        }
        endTakeover(player, SoulLogic.get(player), TakeoverEnd.REMOVED, player.level().getGameTime());
    }

    /**
     * A stop saves every player before any of them logs out, so every takeover is ended, every fight broken off and
     * every mask taken off here first, or the saves would hold the fight's end as the next try instead of the wait, and
     * a mask still on.
     */
    private static void onServerStopping(ServerStoppingEvent event) {
        breakOffAll();
        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        endTakeovers(players);
        endMasks(players);
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
