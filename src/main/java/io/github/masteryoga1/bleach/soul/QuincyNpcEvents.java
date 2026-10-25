package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.QuincyElder;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.registry.ModEntities;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The living Quincy coming and going: the Elder's visits to those with Quincy blood and the lessons
 * he gives, and the young Hunter turning up after a Garganta or at a Hollow Bait. The rules are in
 * {@link QuincyNpcLogic}; the NPCs themselves, their talk and their fighting, are in the entity
 * package. What can be done without a connection takes a {@link Player}, so game tests can use mock
 * players.
 */
public final class QuincyNpcEvents {
    private static final long TICKS_PER_MINUTE = 1200L;
    /** How far behind his landing spot a Quincy seems to step in from. */
    private static final double STEP_IN_DISTANCE = 6.0;

    private QuincyNpcEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(QuincyNpcEvents::onPlayerTickPre);
    }

    /**
     * Before the player moves, so the Elder's spot is searched around where they really stand (see
     * {@link SpiritEvents}). Each soul is checked once a minute, on its own tick of the cycle.
     */
    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || (player.tickCount + player.getId()) % QuincyNpcLogic.VISIT_CHECK_TICKS != 0
                || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (!elderMayVisit(player, data, now)) {
            return;
        }
        QuincyNpcLogic.Visit reason = QuincyNpcLogic.visitReason(data, now,
                QuincyArtsLogic.gloveDay(now, level.getDayTime()));
        if (reason != QuincyNpcLogic.Visit.NONE) {
            spawnElder(player, data, reason, now);
        }
    }

    // ---- The Elder ----

    /**
     * Whether the Elder may come to this soul now, whatever for: visits are on, in the Overworld and
     * not in a game test; the soul is alive in survival or adventure mode, has Quincy blood, and is
     * a living Human or a Shinigami past its grace after joining; no Hollow is close, no other Elder
     * is about, and no Hollow Bait is drawing them here; and his wait since the last visit is over.
     * Whether he has a reason to come is {@link QuincyNpcLogic#visitReason}'s question.
     */
    public static boolean elderMayVisit(ServerPlayer p, SoulData d, long now) {
        ServerLevel level = p.serverLevel();
        if (!BleachConfig.get(BleachConfig.ELDER_VISITS) || level.dimension() != Level.OVERWORLD
                || p.server instanceof GameTestServer) {
            return false;
        }
        if (!p.isAlive() || !p.gameMode.isSurvival() || !SoulLogic.hasSoul(p) || !QuincyNpcLogic.hasQuincyBlood(d)
                || !QuincyNpcLogic.livingOrShinigami(d) || GargantaEvents.inGrace(p)) {
            return false;
        }
        Vec3 at = p.position();
        double range = QuincyNpcLogic.NO_HOLLOW_RADIUS;
        if (!level.getEntitiesOfClass(Hollow.class, new AABB(at, at).inflate(range),
                hollow -> hollow.isAlive() && hollow.distanceToSqr(at) <= range * range).isEmpty()
                || elderNear(level, at, QuincyNpcLogic.NPC_SPACING) != null || BaitEvents.insideActiveSite(level, at)) {
            return false;
        }
        return QuincyNpcLogic.visitDue(d.quincy().teaching(), now,
                BleachConfig.get(BleachConfig.ELDER_COOLDOWN_MINUTES) * TICKS_PER_MINUTE);
    }

    /**
     * The Elder steps in on open ground 8 to 16 blocks from the soul, facing them, for a visit of five
     * minutes, and the soul is told someone is watching. The visit is stamped whether or not they ever
     * talk. No conditions are checked here, so the command uses it too.
     *
     * @return him, or null, changing nothing, if there was no room or he could not be added
     */
    @Nullable
    public static QuincyElder spawnElder(ServerPlayer visitor, SoulData d, QuincyNpcLogic.Visit reason, long now) {
        ServerLevel level = visitor.serverLevel();
        QuincyElder elder = arrive(level, ModEntities.QUINCY_ELDER.get(), visitor, visitor,
                QuincyNpcLogic.ELDER_SPOT_MIN, QuincyNpcLogic.ELDER_SPOT_MAX, npc -> npc.setReason(reason));
        if (elder == null) {
            return null;
        }
        visitor.sendSystemMessage(Component.translatable("message.bleach.elder.arrives")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        d.setQuincy(d.quincy().withTeaching(d.quincy().teaching().withLastVisitAt(now)));
        return elder;
    }

    /**
     * The Elder's lesson: training, as from the bow, once a training day and up to the configured
     * number in all. Never for a sealed soul.
     *
     * @return false, changing nothing, if he has no lesson for the soul today
     */
    public static boolean giveLesson(Player p, SoulData d, long now, long dayTime) {
        long day = QuincyArtsLogic.gloveDay(now, dayTime);
        if (!QuincyNpcLogic.lessonAvailable(d, day, BleachConfig.get(BleachConfig.ELDER_MAX_LESSONS))) {
            return false;
        }
        int points = BleachConfig.get(BleachConfig.ELDER_LESSON_TRAINING);
        QuincyEvents.train(p, d, points);
        // Read again: the training changed the record.
        d.setQuincy(d.quincy().withTeaching(d.quincy().teaching().withLesson(day)));
        p.displayClientMessage(Component.translatable("message.bleach.elder.lesson", points)
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    // ---- The Hunter ----

    /**
     * A Garganta opened for this soul of its own accord (not by command, and not the first Hollow):
     * the Hunter may follow it, if he is about at all, not already near, and not here too recently
     * (see {@link QuincyNpcLogic#hunterArrives}). The soul is told someone else is hunting here. He
     * never comes for a Hollow or an Arrancar: the soul that drew them is his quarry's kind.
     */
    public static void onNaturalArrival(ServerPlayer drawer, SoulData d, HollowKind kind, long now) {
        if (d.state() == SoulState.HOLLOW || d.state() == SoulState.ARRANCAR) {
            return;
        }
        ServerLevel level = drawer.serverLevel();
        if (!BleachConfig.get(BleachConfig.HUNTER_ENABLED) || !QuincyNpcLogic.hunterMayCome(kind, false)
                || hunterNear(level, drawer.position(), QuincyNpcLogic.NPC_SPACING) != null
                || !QuincyNpcLogic.hunterArrives(d, now, drawer.getRandom().nextDouble())) {
            return;
        }
        if (spawnHunter(level, drawer, drawer, now) != null) {
            met(drawer, d, now);
        }
    }

    /**
     * A Hollow Bait's third wave came down around this soul: once in a while the Hunter comes to fight
     * its Hollows, his usual wait aside, if no Hunter is near already. Contests, which have their own
     * Hunter, never call this.
     */
    public static void onBaitWave(ServerLevel level, Player anchor, long now) {
        if (!BleachConfig.get(BleachConfig.HUNTER_ENABLED)
                || hunterNear(level, anchor.position(), QuincyNpcLogic.NPC_SPACING) != null
                || level.getRandom().nextDouble() >= BleachConfig.get(BleachConfig.HUNTER_IN_BAIT_CHANCE)) {
            return;
        }
        ServerPlayer visitor = anchor instanceof ServerPlayer player ? player : null;
        if (spawnHunter(level, anchor, visitor, now) != null && SoulLogic.hasSoul(anchor)) {
            met(anchor, SoulLogic.get(anchor), now);
        }
    }

    /** The soul has met the Hunter now: noted, and told. */
    private static void met(Player soul, SoulData d, long now) {
        HuntRecord hunt = d.hunt();
        d.setHunt(hunt.withBait(hunt.bait().withHunterMet(true).withLastHunterAt(now)));
        soul.sendSystemMessage(Component.translatable("message.bleach.hunter.arrives")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /**
     * The Hunter steps in on open ground 16 to 24 blocks from {@code near}, facing it, for a visit of
     * five minutes with {@code visitor}, if any. No conditions are checked here, so the command and
     * the bait use it too.
     *
     * @return him, or null if there was no room or he could not be added
     */
    @Nullable
    public static QuincyHunter spawnHunter(ServerLevel level, Entity near, @Nullable ServerPlayer visitor, long now) {
        return arrive(level, ModEntities.QUINCY_HUNTER.get(), near, visitor, QuincyNpcLogic.HUNTER_SPOT_MIN,
                QuincyNpcLogic.HUNTER_SPOT_MAX, hunter -> {
                });
    }

    /**
     * A Quincy NPC of this type comes down on open ground around {@code near}, facing it, set up as
     * a visitor of five minutes, and steps in with Hirenkyaku. {@code setUp} runs before it is added.
     */
    @Nullable
    private static <T extends QuincyNpc> T arrive(ServerLevel level, EntityType<T> type, Entity near,
                                                  @Nullable ServerPlayer visitor, int minDistance, int maxDistance,
                                                  Consumer<T> setUp) {
        Vec3 spot = SpawnSpots.openGround(level, near, type, 1.0F, minDistance, maxDistance,
                QuincyNpcLogic.SPOT_TRIES, QuincyNpcLogic.SPOT_MAX_RISE);
        T npc = spot != null ? type.create(level) : null;
        if (npc == null) {
            return null;
        }
        float yaw = (float) (Mth.atan2(near.getZ() - spot.z, near.getX() - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
        npc.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        EventHooks.finalizeMobSpawn(npc, level, level.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.EVENT, null);
        npc.setLifetime(QuincyNpcLogic.VISIT_LIFETIME);
        npc.setVisitor(visitor != null ? visitor.getUUID() : null);
        setUp.accept(npc);
        if (!level.addFreshEntity(npc)) {
            return null;
        }
        // He seems to step in from a little farther off, on the side away from the one he came for.
        Vec3 away = new Vec3(spot.x - near.getX(), 0.0, spot.z - near.getZ());
        Vec3 from = away.lengthSqr() < 1.0E-4 ? spot : spot.add(away.normalize().scale(STEP_IN_DISTANCE));
        Hirenkyaku.effects(level, from, spot);
        return npc;
    }

    // ---- Lookups ----

    /** The nearest living Quincy Elder within range of this point, if any. */
    @Nullable
    public static QuincyElder elderNear(ServerLevel level, Vec3 pos, double range) {
        return nearest(level, QuincyElder.class, pos, range);
    }

    /** The nearest living Quincy Hunter within range of this point, if any. */
    @Nullable
    public static QuincyHunter hunterNear(ServerLevel level, Vec3 pos, double range) {
        return nearest(level, QuincyHunter.class, pos, range);
    }

    @Nullable
    private static <T extends QuincyNpc> T nearest(ServerLevel level, Class<T> type, Vec3 pos, double range) {
        List<T> near = level.getEntitiesOfClass(type, new AABB(pos, pos).inflate(range),
                npc -> npc.isAlive() && npc.distanceToSqr(pos) <= range * range);
        T nearest = null;
        double best = Double.MAX_VALUE;
        for (T npc : near) {
            double distance = npc.distanceToSqr(pos);
            if (distance < best) {
                best = distance;
                nearest = npc;
            }
        }
        return nearest;
    }
}
