package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.entity.ZabimaruSpirit;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * The subjugation (kuppuku): a soul's Bankai trial, a bout of {@code [bankai.trial] bout_seconds} with its own
 * zanpakutō spirit, drawn out afresh to be beaten down. Its try is counted as it begins, and no end but the win
 * writes anything more: the time, the loss, a break-off seen on the wielder's tick, its logging out or death, the
 * server stopping, or the spirit leaving its world for any reason, so whatever sends a drawn-out spirit home ends the
 * bout. The bouts are held only in memory, by their wielders' UUIDs, on the server thread.
 *
 * <p>A spirit beaten to its floor in a bout yields ({@link #yields}): the soul reaches Bankai by its spirit's name for
 * it, as the grant gives it. A spirit's blow that would leave its wielder at {@code loss_health} stops there, and the
 * bout is lost, so no spirit ever kills its wielder, in a bout or out of one. While a bout lasts the two can touch
 * nothing living but each other, and the spirit's bar counts its seconds down.
 *
 * <p>While {@code spirit_tests} is on, a spirit whose file sets a test of its own ({@code bankai.test}) tests its soul
 * so ({@link #testOf}): Zabimaru by its two halves, both to be beaten down together ({@link ZabimaruSpirit#splitHalves}),
 * and Zangetsu with blades risen about the soul, of which only the true one reaches him ({@link BankaiSwords}).
 * Any other spirit, or one whose body cannot take its test, fights a plain bout.
 *
 * <p>A ring of {@code ring_radius} blocks, lit in the spirit's colour, stands where the wielder began: it keeps the
 * two in and everyone else out, as the inner Hollow's ring does, and no bout begins where its ring would meet
 * another's, a bout's or a fight with an inner Hollow's; that fight asks {@link #duelRefusal} the same of a bout.
 */
public final class BankaiTrial {
    private static final String BEGUN = "message.bleach.bankai.trial.begun", TIME_UP = "message.bleach.bankai.trial.time";
    private static final String BROKEN = "message.bleach.bankai.trial.broken_off", HOME = "message.bleach.spirit.home";
    private static final String LOST = "message.bleach.bankai.trial.lost", WON = "message.bleach.bankai.trial.won";
    private static final String YIELDED = "message.bleach.bankai.trial.yielded", SPIRIT_WON = "message.bleach.spirit.won";
    private static final String KEY = "commands.bleach.zanpakuto.bankai.trial", CLOCK = "bossbar.bleach.bankai_trial";
    private static final String RELEASE_KEY = "key.bleach.zanpakuto_release";
    private static final String ARENA = "message.bleach.bankai.trial.arena", BARRIER = "message.bleach.bankai.trial.barrier";
    private static final String IN_TRIAL = "message.bleach.bankai.trial.duel_in_trial";
    private static final String NEAR = "message.bleach.bankai.trial.duel_near";
    private static final String HALVES = "message.bleach.bankai.trial.halves", BLADES = "message.bleach.bankai.trial.blades";
    private static final int SECOND_TICKS = 20, KEEP_OUT_INTERVAL_TICKS = 5, RING_INTERVAL_TICKS = 10, RING_POINTS = 48;
    /** How far above and below the centre others are kept out, and how high above it the ring is lit, in blocks. */
    private static final double KEEP_OUT_HEIGHT = 8.0, RING_LIFT = 0.2;
    /** How far from the centre a creature's grudge against either of the two is dropped, in blocks. */
    private static final double AGGRO_RANGE = 32.0;

    /** Every bout under way, by its wielder's UUID. Server thread only. */
    private static final Map<UUID, Trial> TRIALS = new HashMap<>();

    private BankaiTrial() {
    }

    /**
     * What came of beginning a bout: STARTED, a refusal of its own, or one of the call's, whose names they share, each
     * but STARTED with the reason a command says, the call's own for its own.
     */
    public enum Start {
        STARTED(false), DEAD(true), WATCHING(false), ALREADY(false), REACHED(false), CROWDED(false), NO_GROUND(false),
        NO_POWER(true), NAMELESS(true), NO_SPIRIT(true), NO_TYPE(true), BUSY(true), PEACEFUL(true), NO_ROOM(true);

        private final boolean called;

        Start(boolean called) {
            this.called = called;
        }

        public String key() {
            return called ? Materialisation.Call.valueOf(name()).key() : KEY + "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a bout ended: the spirit yielded, it beat its wielder down, the time ran out, or it was broken off. */
    public enum End { WIN, LOSS, TIME, BROKEN_OFF }

    /**
     * One bout: the player object that began it (a respawn or an End exit makes a new one, which breaks it off), its
     * spirit, its world, where the wielder stood, when it began and when its time runs out, its spirit's bar's own name,
     * which the bar shows beside the seconds left, and the blades of its test, for a spirit that tests with them.
     */
    public record Trial(Player wielder, MaterialisedSpirit spirit, ResourceKey<Level> level, Vec3 centre, long startedAt,
            long endsAt, Component name, @Nullable BankaiSwords swords) {
    }

    /**
     * The shield first of all, so nothing else weighs a blow that should not land; the loss last of all, after every
     * other change to the blow; and a spirit leaving its world ends its bout, as do its wielder's logging out and death
     * and the server stopping; and at each server tick's end the blades of bouts ended as their spirits left go.
     */
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, BankaiTrial::onIncomingDamage);
        bus.addListener(EventPriority.LOWEST, BankaiTrial::onDamagePre);
        bus.addListener(BankaiTrial::onLeave);
        bus.addListener(BankaiTrial::onLoggedOut);
        bus.addListener(BankaiTrial::onDeath);
        bus.addListener(BankaiTrial::onServerStopping);
        bus.addListener(BankaiTrial::onServerStopped);
        bus.addListener(BankaiSwords::onServerTick);
    }

    /**
     * Begins the wielder's bout where it stands, or says why not: the wielder a spectator or in creative mode, in a
     * bout already, or past Bankai, then the call's own refusals, a dead wielder's among them, then a spot where its
     * ring would meet another's ({@link #crowded}), and for a test with blades one where fewer than half of them find
     * ground ({@link BankaiSwords#ground}). A spirit it has out is replaced by one afresh and whole, and only once the
     * new one has room, so a refused bout leaves it out. The try is counted only once the bout has begun. On the
     * client: NO_SPIRIT.
     */
    public static Start begin(Player wielder, long now) {
        if (!(wielder.level() instanceof ServerLevel level)) {
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
        if (crowded(level, wielder.position())) {
            return Start.CROWDED;
        }
        ZanpakutoSpirit.BankaiTest test = testOf(wielder, data);
        boolean withBlades = test == ZanpakutoSpirit.BankaiTest.SWORDS;
        List<Vec3> ground = withBlades ? BankaiSwords.ground(level, wielder, wielder.position(), ringRadius()) : List.of();
        if (withBlades && tooLittleGround(ground.size())) {
            return Start.NO_GROUND;
        }
        // No room, or a rung that changed since; never ALREADY, as the call replaces a spirit out.
        Materialisation.Call call = Materialisation.callOut(wielder, true);
        if (call != Materialisation.Call.CALLED) {
            return Start.valueOf(call.name());
        }
        // Held by its call from before it joined the world, so it is there.
        return open(wielder, data, Materialisation.of(wielder.getUUID()), now, test, ground);
    }

    /**
     * The bout begins with this spirit, out before its wielder: held, its try counted, its clock and ring shown, and its
     * test set: a Zabimaru tested by its two halves has its health split between them, and its wielder is told so; a
     * soul tested with blades has them risen on this ground in its spirit's Shikai look, and is told so.
     */
    private static Start open(Player wielder, SoulData data, MaterialisedSpirit spirit, long now,
            ZanpakutoSpirit.BankaiTest test, List<Vec3> ground) {
        int seconds = boutSeconds();
        if (test == ZanpakutoSpirit.BankaiTest.TWO_HALVES && spirit instanceof ZabimaruSpirit zabimaru) {
            zabimaru.splitHalves(now);
        }
        BankaiSwords swords = null;
        if (test == ZanpakutoSpirit.BankaiTest.SWORDS && wielder.level() instanceof ServerLevel server) {
            int look = ZanpakutoSpirits.get(server, data.zanpakuto().spirit()).map(its -> its.shikai().model()).orElse(0);
            swords = BankaiSwords.raise(server, wielder, ground, blades(), look);
        }
        Trial trial = new Trial(wielder, spirit, wielder.level().dimension(), wielder.position(), now,
                now + seconds * (long) SECOND_TICKS, spirit.bar().event().getName(), swords);
        TRIALS.put(wielder.getUUID(), trial);
        ZanpakutoBond bond = data.zanpakuto();
        data.setZanpakuto(bond.withBankai(bond.bankai().tried(now)));
        showTime(trial, now);
        if (wielder.level() instanceof ServerLevel level) {
            ring(level, trial, ringRadius());
        }
        wielder.sendSystemMessage(Component.translatable(BEGUN, seconds).withStyle(ChatFormatting.GRAY));
        if (spirit instanceof ZabimaruSpirit zabimaru && zabimaru.split()) {
            wielder.sendSystemMessage(Component.translatable(HALVES).withStyle(ChatFormatting.GRAY));
        }
        if (swords != null) {
            wielder.sendSystemMessage(Component.translatable(BLADES).withStyle(ChatFormatting.GRAY));
        }
        return Start.STARTED;
    }

    /**
     * The test this soul's spirit sets it in a Bankai trial, by its file's {@code bankai.test}: none, a plain bout, while
     * {@code spirit_tests} is off, or for a spirit with no Bankai in this world.
     */
    private static ZanpakutoSpirit.BankaiTest testOf(Player wielder, SoulData data) {
        if (!BleachConfig.get(BleachConfig.BANKAI_TRIAL_SPIRIT_TESTS)) {
            return ZanpakutoSpirit.BankaiTest.NONE;
        }
        return ZanpakutoSpirits.get(wielder.level(), data.zanpakuto().spirit()).flatMap(ZanpakutoSpirit::bankai)
                .map(ZanpakutoSpirit.Bankai::test).orElse(ZanpakutoSpirit.BankaiTest.NONE);
    }

    /**
     * Each server tick of a living player in a bout, in this order: a break-off ends it, then the time, which waits a
     * second at most for a spirit beaten as it runs out to yield on its own tick; a half of Zabimaru down alone too long
     * rises again ({@code halves_seconds}, {@code rise_share}); the wielder taken off anything it rides, and the ring
     * keeping the two in; then every 5 ticks from the start the ring keeps everyone else out, every 10 it is lit, and
     * each second creatures drop their grudges against the two and the clock is shown.
     */
    public static void tick(Player wielder, SoulData data, long now) {
        Trial trial = TRIALS.get(wielder.getUUID());
        if (trial == null || !(wielder.level() instanceof ServerLevel level)) {
            return;
        }
        if (brokenOff(wielder, data, trial)) {
            end(trial, End.BROKEN_OFF, false);
            return;
        }
        if (now >= trial.endsAt && (!trial.spirit.beaten() || now >= trial.endsAt + SECOND_TICKS)) {
            end(trial, End.TIME, false);
            return;
        }
        if (trial.spirit instanceof ZabimaruSpirit zabimaru) {
            zabimaru.rise(now, halvesSeconds() * (long) SECOND_TICKS, riseShare());
        }
        double radius = ringRadius();
        // A rider's own motion is thrown away each tick, so a mount would carry the wielder past the ring's push.
        if (wielder.isPassenger()) {
            wielder.stopRiding();
        }
        if (horizontal(wielder.position(), trial.centre) > radius) {
            VisoredEvents.push(wielder, outward(wielder, trial.centre).reverse());
            tell(wielder, ARENA);
        }
        if (horizontal(trial.spirit.position(), trial.centre) > radius) {
            VisoredEvents.push(trial.spirit, outward(trial.spirit, trial.centre).reverse());
        }
        long elapsed = now - trial.startedAt;
        if (Math.floorMod(elapsed, KEEP_OUT_INTERVAL_TICKS) == 0) {
            keepOut(level, trial, radius);
        }
        if (Math.floorMod(elapsed, RING_INTERVAL_TICKS) == 0) {
            ring(level, trial, radius);
        }
        if (Math.floorMod(elapsed, SECOND_TICKS) == 0) {
            dropGrudges(level, trial);
            showTime(trial, now);
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
     * and its blades cleared ({@link BankaiSwords#clear}); then the win's record or the loss's or the time's line in
     * chat; then its spirit sent home with the end's line, unless the spirit is leaving its world already, where a
     * discard would change the world as it moves the spirit: then only the line.
     */
    private static void end(Trial trial, End end, boolean leaving) {
        if (!TRIALS.remove(trial.wielder.getUUID(), trial)) {
            return;
        }
        if (trial.swords != null) {
            trial.swords.clear(leaving);
        }
        Player wielder = trial.wielder;
        String line = end == End.WIN ? YIELDED : end == End.BROKEN_OFF ? BROKEN : HOME;
        if (end == End.WIN) {
            won(wielder);
        } else if (end != End.BROKEN_OFF) {
            wielder.sendSystemMessage(Component.translatable(end == End.TIME ? TIME_UP : LOST)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!leaving && !trial.spirit.isRemoved()) {
            Materialisation.sendHome(trial.spirit, line);
        } else if (wielder.isAlive() && !wielder.isRemoved()) {
            wielder.displayClientMessage(Component.translatable(line).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * A spirit beaten to its floor in a bout yields, unless its wielder broke the bout off this tick (its own tick has
     * not seen it yet): either way the bout ends, and true, so the spirit is not sent home beaten as well. False for a
     * spirit in no bout, which goes home beaten ({@link Materialisation#upkeep}).
     */
    public static boolean yields(MaterialisedSpirit spirit) {
        Trial trial = trialOf(spirit);
        if (trial == null) {
            return false;
        }
        end(trial, brokenOff(trial.wielder, SoulLogic.get(trial.wielder), trial) ? End.BROKEN_OFF : End.WIN, false);
        return true;
    }

    /**
     * The soul reaches Bankai by its spirit's name for it, as the grant gives it, and is told so in gold with the key
     * that calls it; a spirit with no Bankai in this world, which the grant refuses too, gives nothing. A soul past
     * Bankai keeps it as it is: {@link #yields} breaks such a bout off before it comes here, so this guards only a
     * later way to win one.
     */
    private static void won(Player wielder) {
        SoulData data = SoulLogic.get(wielder);
        Optional<Component> name = ZanpakutoSpirits.get(wielder.level(), data.zanpakuto().spirit())
                .flatMap(BankaiLogic::nameOf);
        if (data.zanpakuto().bankai().achieved() || name.isEmpty()) {
            return;
        }
        BankaiLogic.reach(data, name.get(), wielder.level().getGameTime());
        if (wielder instanceof ServerPlayer player) {
            BleachNetwork.syncSoul(player, data);
        }
        wielder.sendSystemMessage(Component.translatable(WON, name.get(), Component.keybind(RELEASE_KEY))
                .withStyle(ChatFormatting.GOLD));
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

    /** The spirit's bar shows its own name and the seconds left. */
    private static void showTime(Trial trial, long now) {
        trial.spirit.bar().rename(Component.translatable(CLOCK, trial.name, VisoredLogic.secondsUntil(trial.endsAt, now)));
    }

    /** How long a bout lasts, in seconds. */
    public static int boutSeconds() {
        return BleachConfig.get(BleachConfig.BANKAI_TRIAL_BOUT_SECONDS);
    }

    /** The wielder's health at which its spirit's last blow stops. */
    public static float lossHealth() {
        return (float) BleachConfig.get(BleachConfig.BANKAI_TRIAL_LOSS_HEALTH);
    }

    /** How many blades rise for a test with blades. */
    public static int blades() {
        return BleachConfig.get(BleachConfig.BANKAI_TRIAL_BLADES);
    }

    /** Whether so few spots were found that fewer than half the blades would rise, which refuses the trial. */
    public static boolean tooLittleGround(int spots) {
        return 2 * spots < blades();
    }

    /** The ring's radius, in blocks. */
    public static int ringRadius() {
        return BleachConfig.get(BleachConfig.BANKAI_TRIAL_RING_RADIUS);
    }

    /** How long two halves have to be beaten down together, in seconds. */
    public static int halvesSeconds() {
        return BleachConfig.get(BleachConfig.BANKAI_TRIAL_HALVES_SECONDS);
    }

    /** The share of its full health with which a half down alone rises again. */
    public static double riseShare() {
        return BleachConfig.get(BleachConfig.BANKAI_TRIAL_RISE_SHARE);
    }

    /**
     * Whether a bout begun here would have its ring meet another: a bout's in this world within twice the ring and 2
     * blocks, or a fight with an inner Hollow's within the two rings and 2. The fights are found by their hosts, who
     * are the server's players.
     */
    private static boolean crowded(ServerLevel level, Vec3 centre) {
        double radius = ringRadius();
        for (Trial other : TRIALS.values()) {
            if (other.level == level.dimension() && horizontal(other.centre, centre) <= 2.0 * radius + 2.0) {
                return true;
            }
        }
        double apart = radius + VisoredLogic.arenaRadius() + 2.0;
        for (ServerPlayer host : level.getServer().getPlayerList().getPlayers()) {
            VisoredEvents.Duel duel = VisoredEvents.duel(host.getUUID());
            if (duel != null && duel.level() == level.dimension() && horizontal(duel.centre(), centre) <= apart) {
                return true;
            }
        }
        return false;
    }

    /**
     * Why this soul's inner Hollow may not come out to fight it now, or null when it may: the soul is in a bout with
     * its spirit, or another's bout stands so close that the two rings would meet. Jinzen and the command ask this
     * before {@link VisoredEvents#beginDuel}, which knows nothing of the bouts.
     */
    @Nullable
    public static String duelRefusal(Player host) {
        if (TRIALS.containsKey(host.getUUID())) {
            return IN_TRIAL;
        }
        double apart = ringRadius() + VisoredLogic.arenaRadius() + 2.0;
        for (Trial trial : TRIALS.values()) {
            if (trial.level == host.level().dimension() && horizontal(trial.centre, host.position()) <= apart) {
                return NEAR;
            }
        }
        return null;
    }

    /**
     * Pushes every creature and player but the two back out of the ring, and tells a pushed player why, unless the
     * push would leave it somewhere unsafe ({@link VisoredEvents#safeOutward}). Spectators, creative players and
     * players the wielder may not harm pass, as at the inner Hollow's ring.
     */
    private static void keepOut(ServerLevel level, Trial trial, double radius) {
        Vec3 centre = trial.centre;
        AABB box = new AABB(centre, centre).inflate(radius + 2.0, KEEP_OUT_HEIGHT, radius + 2.0);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box, each -> keptOut(trial, each))) {
            Vec3 out = outward(other, centre);
            if (horizontal(other.position(), centre) <= radius + 1.0 && VisoredEvents.safeOutward(level, other, out)) {
                VisoredEvents.push(other, out);
                if (other instanceof Player player) {
                    tell(player, BARRIER);
                }
            }
        }
    }

    /** Whether the ring keeps this one out: a creature alive but the spirit, or a player the wielder may harm. */
    private static boolean keptOut(Trial trial, LivingEntity each) {
        return each != trial.spirit && each != trial.wielder && each.isAlive() && (each instanceof Mob
                || each instanceof Player player && !player.isSpectator() && !player.isCreative()
                && trial.wielder.canHarmPlayer(player));
    }

    /** Creatures nearby that have taken either of the two as their target drop it, the brain's memory too. */
    private static void dropGrudges(ServerLevel level, Trial trial) {
        AABB box = new AABB(trial.centre, trial.centre).inflate(AGGRO_RANGE);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, creature -> creature != trial.spirit)) {
            LivingEntity target = mob.getTarget();
            if (target == trial.wielder || target == trial.spirit) {
                mob.setTarget(null);
            }
            if (mob.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, trial.wielder)
                    || mob.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, trial.spirit)) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    /** The way straight out from the centre along the ground, as a unit vector; east for a body right on it. */
    private static Vec3 outward(LivingEntity entity, Vec3 centre) {
        Vec3 away = new Vec3(entity.getX() - centre.x, 0.0, entity.getZ() - centre.z);
        return away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
    }

    /** The ring's light in its spirit's colour, a little above where the wielder first stood. */
    private static void ring(ServerLevel level, Trial trial, double radius) {
        DustParticleOptions dust = new DustParticleOptions(Vec3.fromRGB24(trial.spirit.colour()).toVector3f(), 1.0F);
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = i * (Math.PI * 2.0 / RING_POINTS);
            SpiritSenses.particles(level, dust, trial.centre.x + Math.cos(angle) * radius, trial.centre.y + RING_LIFT,
                    trial.centre.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return a.subtract(b).horizontalDistance();
    }

    /** A line about the bout on this player's action bar, in grey. */
    private static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * While a bout lasts, nothing living but its spirit can touch the wielder, and the wielder can touch nothing living
     * but its spirit: the blow of any other creature or player on the wielder, and any blow of the wielder's (its
     * arrows, waves and blasts too) on anything else, is stopped before anything else weighs it, as at the inner
     * Hollow's fight. Blocks, lava and falls still hurt the wielder; the spirit's own guard keeps others' blows off it.
     * A beaten spirit strikes no more, in a bout or out of one: a blow before its next tick would turn a won bout to a loss.
     * A spirit that tests with blades is reached only as they allow ({@link BankaiSwords#lands}), weighed after the
     * refusals above, so a blow one of them stops breaks no blade.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == victim) {
            return;
        }
        Trial guarded = victim instanceof Player wielder ? TRIALS.get(wielder.getUUID()) : null;
        Trial striking = attacker instanceof Player striker ? TRIALS.get(striker.getUUID()) : null;
        Trial struck = victim instanceof MaterialisedSpirit body ? trialOf(body) : null;
        if (guarded != null && attacker != guarded.spirit || striking != null && victim != striking.spirit
                || attacker instanceof MaterialisedSpirit spirit && spirit.beaten()
                || struck != null && struck.swords != null && !struck.swords.lands(struck.wielder, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /**
     * A spirit's blow on its own wielder, once armour and every other listener have had their say and before
     * absorption, that would leave the wielder at {@code loss_health} or below: cut so it leaves the wielder there (or
     * where it was, if lower), and the spirit goes home, the bout lost, or, out of one, saying it won. So no spirit
     * ever kills its wielder, and no totem is spent on one. Only a spirit's blows: a fall or lava still can.
     */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player wielder) || wielder.level().isClientSide()
                || !(event.getSource().getEntity() instanceof MaterialisedSpirit spirit)
                || !wielder.getUUID().equals(spirit.wielderId())) {
            return;
        }
        float health = wielder.getHealth();
        float absorption = wielder.getAbsorptionAmount();
        float floor = lossHealth();
        if (health - Math.max(0.0F, event.getNewDamage() - absorption) > floor) {
            return;
        }
        event.setNewDamage(VisoredLogic.clampedBlow(event.getNewDamage(), health, absorption, floor));
        Trial trial = trialOf(spirit);
        if (trial != null) {
            end(trial, End.LOSS, false);
        } else {
            Materialisation.sendHome(spirit, SPIRIT_WON);
        }
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

    /** A closed world's bouts and blades went with it, unsaved: none is held into the next world a game opens. */
    private static void onServerStopped(ServerStoppedEvent event) {
        TRIALS.clear();
        BankaiSwords.PENDING.clear();
    }
}
