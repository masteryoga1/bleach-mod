package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.item.HellButterflyItem;
import io.github.masteryoga1.bleach.network.GateKeyPayload;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Senkaimon and the Garganta in play: the Gate key's turn, each gate's opening, who the Senkaimon takes where, and
 * the Jigokuchō handed to a new Shinigami, or by the instructor to one that has lost its own. The rules are
 * {@link TravelLogic}'s. The turn and the waits before the next gate are transients in the soul, so a death or a relog
 * starts them over. The key and the Jigokuchō take any {@link Player}, so game tests can use mock players.
 */
public final class TravelEvents {
    private static final String TURNING_KEY = "message.bleach.senkaimon.turning";
    private static final String TEARING_KEY = "message.bleach.garganta.turning";
    private static final String NO_LANDING_KEY = "message.bleach.senkaimon.no_landing";
    private static final String RECEIVED_KEY = "message.bleach.hell_butterfly.received";
    private static final String ISSUED_KEY = "message.bleach.hell_butterfly.issued";
    private static final String ALREADY_KEY = "message.bleach.hell_butterfly.already";
    private static final String NOT_YET_KEY = "message.bleach.hell_butterfly.not_yet";
    /** The lock's turn, heard as the key goes down, and the chime of a soul coming out on the far side. */
    private static final float TURN_VOLUME = 0.8F;
    private static final float TURN_PITCH = 1.2F;
    private static final float ARRIVAL_VOLUME = 1.0F;
    private static final float ARRIVAL_PITCH = 1.6F;
    /** Ticks after a turn is seen and heard begun before another is, so a key pressed again and again stays quiet. */
    private static final int TURN_SHOWN_TICKS = 20;

    private TravelEvents() {
    }

    /** The turn's tick, the blow that breaks it and the world change that drops it, and the Jigokuchō at login. */
    public static void register(IEventBus bus) {
        bus.addListener(TravelEvents::onPlayerTick);
        bus.addListener(TravelEvents::onDamageTaken);
        bus.addListener(TravelEvents::onChangedDimension);
        bus.addListener(TravelEvents::onLoggedIn);
    }

    // ---- The key ----

    /**
     * The Gate key, as sent by {@link GateKeyPayload} (and called directly by game tests): PRESS begins the turn,
     * RELEASE opens the gate, the turner's Senkaimon or Garganta, if the turn is done or else ends it, and CANCEL ends
     * it. Returns the refusal told, or null.
     */
    @Nullable
    public static TravelLogic.Refusal onKey(Player player, GateKeyPayload.GateKey key, long now) {
        if (!(player.level() instanceof ServerLevel level) || !SoulLogic.hasSoul(player)) {
            return null;
        }
        SoulData data = SoulLogic.get(player);
        boolean turning = data.senkaimonOpensAt != SoulData.NEVER;
        return switch (key) {
            case PRESS -> turning ? null : press(player, data, level, now);
            // A key's now is the tick just ended, so a release one tick short of the turn's end still opens.
            case RELEASE -> !turning ? null
                    : now + 1 >= data.senkaimonOpensAt ? open(player, data, level, now) : slip(player, data);
            case CANCEL -> turning ? slip(player, data) : null;
        };
    }

    /** The key alone: the turn begins, heard and told, or why not, told. Nothing is paid until the gate opens. */
    @Nullable
    private static TravelLogic.Refusal press(Player player, SoulData data, ServerLevel level, long now) {
        TravelLogic.Gate gate = TravelLogic.gateFor(data);
        TravelLogic.Refusal refusal = mayOpen(gate, player, data, level, now);
        if (refusal != null) {
            tell(player, gate, refusal, data, now);
            return refusal;
        }
        data.senkaimonOpensAt = now + TravelLogic.turnTicks(gate);
        // Those nearby see the turn and hear it at most once a second, however often the key goes down.
        if (data.senkaimonTurnShownAt == SoulData.NEVER || now - data.senkaimonTurnShownAt >= TURN_SHOWN_TICKS) {
            data.senkaimonTurnShownAt = now;
            player.swing(InteractionHand.MAIN_HAND, true);
            SoundEvent sound = gate == TravelLogic.Gate.GARGANTA
                    ? SoundEvents.SCULK_CLICKING : SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER;
            SpiritSenses.play(player, sound, SoundSource.PLAYERS, TURN_VOLUME, TURN_PITCH, false);
        }
        String line = gate == TravelLogic.Gate.GARGANTA ? TEARING_KEY : TURNING_KEY;
        player.displayClientMessage(Component.translatable(line).withStyle(ChatFormatting.GRAY), true);
        return null;
    }

    /** This gate's rules for this soul here and now. */
    @Nullable
    private static TravelLogic.Refusal mayOpen(TravelLogic.Gate gate, Player player, SoulData data, ServerLevel level,
                                               long now) {
        return gate == TravelLogic.Gate.GARGANTA
                ? TravelLogic.mayOpenGarganta(player, data, level.dimension(), now)
                : TravelLogic.mayOpenSenkaimon(player, data, level.dimension(), now);
    }

    /**
     * The turn done: every rule of the soul's gate now is checked again, and the gate opens, paid for and the wait begun
     * outside creative. A refusal is told, with nothing paid. Returns it, or null.
     */
    @Nullable
    private static TravelLogic.Refusal open(Player player, SoulData data, ServerLevel level, long now) {
        data.senkaimonOpensAt = SoulData.NEVER;
        TravelLogic.Gate gate = TravelLogic.gateFor(data);
        TravelLogic.Refusal refusal = mayOpen(gate, player, data, level, now);
        if (refusal != null) {
            tell(player, gate, refusal, data, now);
            return refusal;
        }
        if (gate == TravelLogic.Gate.GARGANTA) {
            float cost = TravelLogic.gargantaCost(data);
            if (openPassage(level, player, data, now) != null && !player.isCreative()) {
                SoulLogic.trySpend(player, data, cost);
                TravelLogic.startGargantaCooldown(data, now);
            }
            return null;
        }
        float cost = TravelLogic.openCost(data);
        if (Senkaimon.open(level, player, now) != null && !player.isCreative()) {
            SoulLogic.trySpend(player, data, cost);
            TravelLogic.startCooldown(data, now);
        }
        return null;
    }

    /** The turn ends before the gate opens, and the soul is told its gate stays shut. Returns that refusal. */
    private static TravelLogic.Refusal slip(Player player, SoulData data) {
        data.senkaimonOpensAt = SoulData.NEVER;
        tell(player, TravelLogic.gateFor(data), TravelLogic.Refusal.SLIPPED, data, player.level().getGameTime());
        return TravelLogic.Refusal.SLIPPED;
    }

    /** Tells the soul why on its action bar: a held soul by its holder, the wait with its seconds, and some not at all. */
    private static void tell(Player player, TravelLogic.Gate gate, TravelLogic.Refusal refusal, SoulData data, long now) {
        if (refusal == TravelLogic.Refusal.HELD) {
            Negacion.refused(player);
            return;
        }
        String key = refusal.messageKey(gate);
        if (key == null) {
            return;
        }
        long seconds = gate == TravelLogic.Gate.GARGANTA
                ? TravelLogic.gargantaCooldownSeconds(data, now) : TravelLogic.cooldownSeconds(data, now);
        MutableComponent line = refusal == TravelLogic.Refusal.COOLDOWN
                ? Component.translatable(key, seconds) : Component.translatable(key);
        player.displayClientMessage(line.withStyle(ChatFormatting.GRAY), true);
    }

    // ---- The gate ----

    /**
     * Where this gate takes this soul, or null, telling it why, when it may not pass. A soul it does not guide goes into
     * the Dangai ({@link DangaiEvents#into}) unless its Hollow holds its body; any soul is refused if nowhere on the far
     * side will hold it. The far side's chunks load here.
     */
    @Nullable
    public static DimensionTransition destination(Senkaimon gate, ServerPlayer passer) {
        if (!TravelLogic.guided(passer)) {
            if (SoulLogic.hasSoul(passer) && VisoredLogic.takenOver(SoulLogic.get(passer))) {
                passer.displayClientMessage(Component.translatable(TravelLogic.Refusal.TAKEN.messageKey())
                        .withStyle(ChatFormatting.GRAY), true);
                return null;
            }
            return DangaiEvents.into(gate, passer);
        }
        ResourceKey<Level> to = gate.leadsTo();
        ServerLevel target = to == null ? null : BleachWorlds.level(passer.server, to);
        if (target == null) {
            return null;
        }
        Vec3 spot = to == BleachWorlds.SOUL_SOCIETY ? landing(passer, target, TravelLogic.COURT) : homeward(passer, target);
        if (spot == null) {
            passer.displayClientMessage(Component.translatable(NO_LANDING_KEY).withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        GlobalPos left = GlobalPos.of(passer.level().dimension(), passer.blockPosition());
        return new DimensionTransition(target, spot, Vec3.ZERO, passer.getYRot(), passer.getXRot(),
                entity -> arrived(entity, left));
    }

    /**
     * The first spot in the Human World that will hold this soul, tried in {@link TravelLogic#returnOrder}'s order and
     * worked out only when it is reached; null if none will.
     */
    @Nullable
    static Vec3 homeward(ServerPlayer passer, ServerLevel target) {
        TravelRecord record = passer.getData(SoulAttachments.TRAVEL);
        ResourceKey<Level> respawnWorld = passer.getRespawnPosition() == null ? null : passer.getRespawnDimension();
        for (TravelLogic.Return way : TravelLogic.returnOrder(record, respawnWorld)) {
            Vec3 wanted = switch (way) {
                case RECORD -> Vec3.atBottomCenterOf(record.humanWorldReturn().orElseThrow().pos());
                case RESPAWN -> {
                    // True keeps an anchor's charge; a bed or anchor that is gone is skipped.
                    DimensionTransition respawn =
                            passer.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
                    yield respawn.missingRespawnBlock() ? null : respawn.pos();
                }
                case WORLD_SPAWN -> new DimensionTransition(target, passer, DimensionTransition.DO_NOTHING).pos();
            };
            Vec3 spot = wanted == null ? null : landing(passer, target, wanted);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Where the soul comes out for this wanted spot: there, settled by {@link SafeLanding#landingSpot}, or on the floor
     * a soul stood on in the spot's block, or else on top of its column, so a spot built over still lands; null if none
     * will hold it. Public for game tests.
     */
    @Nullable
    public static Vec3 landing(Entity passer, ServerLevel level, Vec3 wanted) {
        Vec3 spot = SafeLanding.landingSpot(passer, level, wanted);
        if (spot != null) {
            return spot;
        }
        double floor = floorTop(passer, level, wanted);
        if (!Double.isNaN(floor)) {
            spot = SafeLanding.landingSpot(passer, level, new Vec3(wanted.x, floor, wanted.z));
            if (spot != null) {
                return spot;
            }
        }
        int x = Mth.floor(wanted.x);
        int z = Mth.floor(wanted.z);
        // A column not loaded would read as the world's floor.
        level.getChunk(x >> 4, z >> 4);
        double top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return SafeLanding.landingSpot(passer, level, new Vec3(wanted.x, top, wanted.z));
    }

    /**
     * The top of the floor a soul stood on at this spot, or NaN if nothing is underfoot: the highest of the shapes its
     * body there would meet that reach down to its feet. A return point is the block the soul stood in, so its floor may
     * rise into that block: a carpet, slab, step or path there, or a fence or wall below, with anything sitting on it.
     * Read after a landing try, which loaded the chunk unless the spot lay outside the world, where all is air.
     */
    private static double floorTop(Entity passer, ServerLevel level, Vec3 wanted) {
        AABB body = passer.getDimensions(Pose.STANDING).makeBoundingBox(wanted);
        double top = Double.NaN;
        for (VoxelShape shape : level.getBlockCollisions(passer, body)) {
            double reach = shape.max(Direction.Axis.Y);
            if (shape.min(Direction.Axis.Y) <= wanted.y && (Double.isNaN(top) || reach > top)) {
                top = reach;
            }
        }
        return top;
    }

    /**
     * After the pass: a soul that left the Human World remembers the block it left from, and the far side chimes. Also
     * run as a soul comes into or out of the Dangai.
     */
    static void arrived(Entity entity, GlobalPos left) {
        if (left.dimension() == Level.OVERWORLD && entity instanceof Player player) {
            player.setData(SoulAttachments.TRAVEL, player.getData(SoulAttachments.TRAVEL).withHumanWorldReturn(left));
        }
        SpiritSenses.play(entity, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, ARRIVAL_VOLUME, ARRIVAL_PITCH,
                false);
    }

    // ---- The Garganta ----

    /**
     * Tears a Garganta open before this soul, to where it leads from here (Soul Society, when the soul sneaks in Hueco
     * Mundo), sized to the soul. Null where no Garganta leads on, or if the level would not take it; crowding is the
     * caller's rule. Its first tick sounds and shows it (see {@link GargantaEvents#onRiftOpened}). Public for game tests.
     */
    @Nullable
    public static GargantaRift openPassage(ServerLevel level, Player opener, SoulData data, long now) {
        ResourceKey<Level> to = TravelLogic.gargantaLeadsTo(level.dimension(), opener.isShiftKeyDown());
        if (to == null) {
            return null;
        }
        GargantaRift rift = GargantaRift.passage(level, opener, now, to, TravelLogic.passageSize(opener, data),
                TravelLogic.gargantaOpenTicks());
        if (!level.addFreshEntity(rift)) {
            return null;
        }
        opener.swing(InteractionHand.MAIN_HAND, true);
        return rift;
    }

    // ---- The Jigokuchō ----

    /** At graduation: a Jigokuchō for a new Shinigami who carries none, and today marked as the day it was given. */
    public static void butterflyAtGraduation(Player player) {
        if (!HellButterflyItem.carries(player)) {
            HellButterflyItem.give(player);
            player.sendSystemMessage(Component.translatable(RECEIVED_KEY)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        Level level = player.level();
        long today = KidoLogic.lessonDay(level.getGameTime(), level.getDayTime());
        player.setData(SoulAttachments.TRAVEL, player.getData(SoulAttachments.TRAVEL).withButterflyDay(today));
    }

    /**
     * At login: a Shinigami never handed a Jigokuchō, one from before there were any, is given one as a graduate is,
     * and today is marked, whether or not it already carried one.
     */
    public static void butterflyAtLogin(Player player) {
        if (SoulLogic.hasSoul(player) && TravelLogic.shinigami(SoulLogic.get(player))
                && player.getData(SoulAttachments.TRAVEL).butterflyDay() < 0) {
            butterflyAtGraduation(player);
        }
    }

    /** Whether the instructor would hand this soul a Jigokuchō now; the dialogue greys the button otherwise. */
    public static boolean mayReceiveButterfly(Player player, SoulData data, long now, long dayTime) {
        return TravelLogic.shinigami(data) && !HellButterflyItem.carries(player)
                && TravelLogic.butterflyDue(player.getData(SoulAttachments.TRAVEL), KidoLogic.lessonDay(now, dayTime));
    }

    /**
     * The instructor's Jigokuchō, for a soul with a Shinigami's power that carries none, at most once every
     * {@code butterfly_reissue_days}. A soul with that power is told on the action bar why not.
     *
     * @return whether one was handed over
     */
    public static boolean issueButterfly(Player player, long now, long dayTime) {
        if (!SoulLogic.hasSoul(player) || !TravelLogic.shinigami(SoulLogic.get(player))) {
            return false;
        }
        if (HellButterflyItem.carries(player)) {
            player.displayClientMessage(Component.translatable(ALREADY_KEY), true);
            return false;
        }
        long today = KidoLogic.lessonDay(now, dayTime);
        TravelRecord record = player.getData(SoulAttachments.TRAVEL);
        if (!TravelLogic.butterflyDue(record, today)) {
            player.displayClientMessage(Component.translatable(NOT_YET_KEY), true);
            return false;
        }
        HellButterflyItem.give(player);
        player.setData(SoulAttachments.TRAVEL, record.withButterflyDay(today));
        player.displayClientMessage(Component.translatable(ISSUED_KEY), true);
        return true;
    }

    // ---- Listeners ----

    /**
     * A turning soul's tick, before it moves: its gate opens once the turn is done, the key still held. A fault drops
     * the turn and is logged, as a throw here would kick the player.
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = level.getGameTime();
        if (data.senkaimonOpensAt == SoulData.NEVER || now < data.senkaimonOpensAt) {
            return;
        }
        try {
            open(player, data, level, now);
        } catch (RuntimeException e) {
            data.senkaimonOpensAt = SoulData.NEVER;
            BleachMod.LOGGER.warn("Gate: {}'s turn failed, and is dropped", player.getName().getString(), e);
        }
    }

    /**
     * A creature's blow that lands breaks the turn, by Kidō's rule: one from a living attacker other than the soul, that
     * took health or absorption hearts.
     */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        // Damage soaked by absorption hearts still lands.
        boolean landed = event.getNewDamage() > 0.0F
                || event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0.0F;
        if (landed && SoulLogic.hasSoul(player)) {
            SoulData data = SoulLogic.get(player);
            if (data.senkaimonOpensAt != SoulData.NEVER) {
                slip(player, data);
            }
        }
    }

    /** A turn does not follow its soul into another world: it is dropped, without a word. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && SoulLogic.hasSoul(player)) {
            SoulLogic.get(player).senkaimonOpensAt = SoulData.NEVER;
        }
    }

    /** A Shinigami from before this version is handed its Jigokuchō at its next login. */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            butterflyAtLogin(player);
        }
    }
}
