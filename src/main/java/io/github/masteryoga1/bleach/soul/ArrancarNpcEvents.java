package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Arrancar NPCs in the world: how they come, what slaying one is worth, and how a beaten Privaron Espada is
 * recalled. Now and then, at night, a strong soul draws Arrancar through a Garganta in place of Hollows: a lone
 * Número, or, for a stronger soul, a Privaron Espada with its Fracción ({@link #tryNaturalArrival}). The server
 * allows only so many at once, spaced well apart ({@link #mayArrive}), and a command may bring them past every
 * limit. A slain Arrancar counts as slain, never purified ({@link #onSlain}). A Privaron Espada beaten low may be
 * lifted away by Negación, taking its Fracción with it ({@link #maybeRecall}). A Número with no living leader,
 * beaten low by a seated Espada player, kneels to it, and may be taken as that player's own Fracción
 * ({@link #maybeYield}, {@link #pledge}, {@link #commandFraccion}). The rules are in {@link ArrancarNpcLogic}; the
 * mob itself, and when it calls in here, is {@link ArrancarNpc}. Nothing here listens to events. The kill and the
 * pledge count for any {@link Player}, so game tests can use mock players; only the milestones need a real one.
 */
public final class ArrancarNpcEvents {
    /** Aware souls this close see a Privaron Espada and its Fracción lifted away, for the milestone: as for a Garganta. */
    static final double RECALL_WITNESS_RANGE = 48.0;

    private static final EntityTypeTest<Entity, ArrancarNpc> ANY_ARRANCAR = EntityTypeTest.forClass(ArrancarNpc.class);
    private static final long TICKS_PER_MINUTE = 1200L;
    private static final String FRACCION = "message.bleach.fraccion.";

    /** What came of an Espada taking a kneeling Número as its Fracción ({@link #pledge}). */
    public enum Pledge {
        /** It is the Espada's Fracción now. */
        PLEDGED,
        /** The Espada already has {@code max_fraccion}. */
        FULL,
        /** The Espada has lost its seat since the Número knelt. */
        NOT_ESPADA,
        /** The server has turned the Fracción off ({@code max_fraccion} 0) since it knelt. Told nothing. */
        OFF,
        /** It no longer kneels to this player, or it is departing or held. Told nothing. */
        NOT_KNEELING
    }

    private ArrancarNpcEvents() {
    }

    // ---- Arrival ----

    /**
     * Called by {@link GargantaEvents} once a soul's roll for a Garganta has passed, before the Menos pick: whether
     * this arrival brings Arrancar instead. They come only while the server allows it, only at night unless the
     * server says otherwise, only for a soul they would hunt with the reiatsu to draw them, for a share of the
     * rolls, and only while the server has room for them and none is already near. A soul strong enough may draw a
     * Privaron Espada and its Fracción in place of a lone Número.
     *
     * @return false, changing nothing, unless a tear bringing Arrancar opened
     */
    public static boolean tryNaturalArrival(ServerPlayer player, SoulData data, long now) {
        if (!BleachConfig.get(BleachConfig.ARRANCAR_NPC_ARRIVALS)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (BleachConfig.get(BleachConfig.ARRANCAR_NPC_NIGHT_ONLY) && !level.isNight()) {
            return false;
        }
        if (!ArrancarNpcLogic.mayComeFor(data)
                || player.getRandom().nextDouble() >= BleachConfig.get(BleachConfig.ARRANCAR_NPC_ARRIVAL_SHARE)
                || !mayArrive(level, player.position())) {
            return false;
        }
        boolean group = ArrancarNpcLogic.bringsGroup(data.maxReiryoku(), player.getRandom().nextDouble());
        return openArrival(player, data, group, now, false);
    }

    /**
     * Opens a Garganta near the soul that drops Arrancar a moment later: a lone Número, or with {@code group} a
     * Privaron Espada and two to four Números as its Fracción. A natural arrival never takes the server past its
     * limit: a group is cut to the room left, and becomes a lone Número when there is room for only one. A command
     * ignores the limit and leaves the soul's cooldown alone. Either way they leave on time, or once idle, as every
     * arrival does. No Quincy Hunter follows them.
     *
     * @return false, changing nothing, if there was no room, no open ground or the world refused the tear
     */
    public static boolean openArrival(ServerPlayer player, SoulData data, boolean group, long now, boolean byCommand) {
        ServerLevel level = player.serverLevel();
        int room = byCommand ? Integer.MAX_VALUE
                : BleachConfig.get(BleachConfig.ARRANCAR_NPC_MAX_ALIVE) - aliveCount(level.getServer());
        if (room < 1) {
            return false;
        }
        // A leader with no room for a single Número beside it comes alone, as a Número.
        boolean leads = group && room >= 2;
        int fraccion = leads ? Math.min(ArrancarNpcLogic.fraccionCount(level.getRandom()), room - 1) : 0;
        EntityType<ArrancarNpc> type = ModEntities.ARRANCAR.get();
        Vec3 landing = SpawnSpots.openGround(level, player, type, 1.0F, GargantaEvents.SPOT_MIN_DISTANCE,
                GargantaEvents.SPOT_MAX_DISTANCE, GargantaEvents.SPOT_TRIES, GargantaEvents.SPOT_MAX_RISE);
        if (landing == null) {
            return false;
        }
        Vec3 at = landing.add(0.0, SpawnSpots.clearAbove(level, type, 1.0F, landing, GargantaEvents.RIFT_MAX_HEIGHT), 0.0);
        // Never ArrancarNpc.NEVER: an arrival with no lifetime is UNTIMED, so it still leaves once it has been idle.
        long lifetime = BleachConfig.get(BleachConfig.ARRANCAR_NPC_LIFETIME_MINUTES);
        long leaveAt = lifetime > 0 ? now + lifetime * TICKS_PER_MINUTE : ArrancarNpc.UNTIMED;
        List<ArrancarNpc> mobs = makeGroup(level, leads, fraccion, leaveAt);
        if (mobs.isEmpty()) {
            return false;
        }
        GargantaRift rift = GargantaRift.arrival(level, at, now, List.of(), player, GargantaEvents.ARRIVAL_SIZE)
                .carrying(mobs);
        if (!level.addFreshEntity(rift)) {
            return false;
        }
        if (!byCommand) {
            data.setHunt(data.hunt().withLastArrivalAt(now));
        }
        return true;
    }

    /**
     * Makes the Arrancar of one arrival, each set up but none yet in the world, all leaving at {@code leaveAt}. With
     * {@code group}, the first is a Privaron Espada and the {@code fraccion} after it are Números that follow it;
     * without, the one is a lone Número and {@code fraccion} is not used. Empty if the world made none.
     */
    public static List<ArrancarNpc> makeGroup(ServerLevel level, boolean group, int fraccion, long leaveAt) {
        RandomSource random = level.getRandom();
        EntityType<ArrancarNpc> type = ModEntities.ARRANCAR.get();
        List<ArrancarNpc> mobs = new ArrayList<>();
        ArrancarNpc first = type.create(level);
        if (first == null) {
            return mobs;
        }
        first.setUp(group ? ArrancarNpcLogic.rollPrivaron(random) : ArrancarNpcLogic.rollNumero(random), random, leaveAt);
        mobs.add(first);
        if (!group) {
            return mobs;
        }
        for (int i = 0; i < fraccion; i++) {
            ArrancarNpc member = type.create(level);
            if (member == null) {
                continue;
            }
            member.setUp(ArrancarNpcLogic.rollNumero(random), random, leaveAt);
            member.setLeader(first.getUUID());
            mobs.add(member);
        }
        return mobs;
    }

    /** The Arrancar NPCs alive in every world of the server, in loaded chunks, leaving out those that take no room. */
    public static int aliveCount(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(ANY_ARRANCAR, ArrancarNpcEvents::takesRoom).size();
        }
        return count;
    }

    /**
     * Whether Arrancar may come here by chance: the server has fewer alive than it allows, and none is within the
     * spacing in this world (a pledged Fracción or a resident does not count). It looks through every entity, so it is
     * asked only after the rare roll has passed.
     */
    public static boolean mayArrive(ServerLevel level, Vec3 at) {
        if (aliveCount(level.getServer()) >= BleachConfig.get(BleachConfig.ARRANCAR_NPC_MAX_ALIVE)) {
            return false;
        }
        double spacing = BleachConfig.get(BleachConfig.ARRANCAR_NPC_SPACING);
        double spacingSqr = spacing * spacing;
        return level.getEntities(ANY_ARRANCAR, npc -> takesRoom(npc) && npc.distanceToSqr(at) < spacingSqr).isEmpty();
    }

    /** Whether an NPC takes an arrival's room: alive, and neither a player's Fracción nor a resident of Las Noches. */
    private static boolean takesRoom(ArrancarNpc npc) {
        return npc.isAlive() && !npc.isPledged() && !npc.isResident();
    }

    // ---- Death ----

    /**
     * An Arrancar NPC died, called once from its first real death. Only the killing blow of a player with a soul
     * counts, as for a Hollow: the killer's reiryoku grows as for a slain Hollow of its reiatsu, its slain count
     * goes up, and it earns the experience the rank is worth. An Arrancar is never purified and leaves no soul
     * behind, whoever kills it, and a Quincy's arrow counts the same. A fake player's kill, such as a machine's,
     * counts for nothing, and so does an Espada's kill of its own Fracción.
     */
    public static void onSlain(ArrancarNpc npc, DamageSource source) {
        if (!(source.getEntity() instanceof Player killer) || killer instanceof FakePlayer || !SoulLogic.hasSoul(killer)) {
            return;
        }
        if (npc.isPledged() && killer.getUUID().equals(npc.leaderId())) {
            return;
        }
        SoulData data = SoulLogic.get(killer);
        SoulLogic.grow(data, HollowLogic.killGrowth(npc.soul().maxReiryoku(), data.maxReiryoku(), false));
        data.setHunt(data.hunt().withSlain(data.hunt().slain() + 1));
        killer.giveExperiencePoints(BleachConfig.get(ArrancarNpcLogic.numbers(npc.kind()).xp()));
        if (killer instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.ARRANCAR_SLAIN);
        }
    }

    // ---- The recall ----

    /**
     * Called each time a living Privaron Espada is hurt. The first time it is beaten down to the recall share of
     * its health it rolls once, and on the roll Negación lifts it and its Fracción away. The roll is spent whatever
     * it gives, and saved, so a leader cannot be beaten down again and again for another chance.
     */
    public static void maybeRecall(ArrancarNpc leader) {
        if (!leader.isLeader() || !leader.isAlive() || leader.isRecallRolled() || leader.isDeparting()
                || Negacion.held(leader) || !BleachConfig.get(BleachConfig.ARRANCAR_NPC_RECALL)
                || leader.getHealth() > BleachConfig.get(BleachConfig.ARRANCAR_NPC_RECALL_HEALTH) * leader.getMaxHealth()) {
            return;
        }
        boolean recalled = ArrancarNpcLogic.shouldRecall(leader.getHealth(), leader.getMaxHealth(), false,
                leader.getRandom().nextDouble());
        leader.markRecallRolled();
        if (recalled) {
            recall(leader);
        }
    }

    /**
     * Negación lifts a Privaron Espada away, and with it every living member of its Fracción within reach, beaten
     * or not, as the lights came down on the Arrancar at the end of their mission (Ch. 236). They are carried off
     * as any held mob is: no drops, no experience and no kill, for they escaped. Aware souls nearby saw it. A
     * Número is never recalled.
     *
     * @return true if the leader was lifted; false for a Número, one leaving, or one the light may not hold
     */
    public static boolean recall(ArrancarNpc leader) {
        if (!leader.isLeader() || leader.isDeparting() || !(leader.level() instanceof ServerLevel level)) {
            return false;
        }
        double reach = BleachConfig.get(BleachConfig.ARRANCAR_NPC_RECALL_REACH);
        List<ArrancarNpc> fraccion = leader.fraccionNear(reach);
        if (Negacion.lift(level, leader) == null) {
            return false;
        }
        for (ArrancarNpc member : fraccion) {
            if (member != leader && member.isAlive() && !member.isDeparting() && !Negacion.held(member)
                    && member.distanceToSqr(leader) <= reach * reach) {
                Negacion.lift(level, member);
            }
        }
        for (ServerPlayer witness : SpiritSenses.awareNear(level, leader.position(), RECALL_WITNESS_RANGE)) {
            Milestones.grant(witness, Milestones.ARRANCAR_RECALLED);
        }
        return true;
    }

    // ---- A player's Fracción ----

    /**
     * After a blow landed on it, from {@link ArrancarNpc#hurt}: a living Número free of every hold, not leaving, not
     * pledged and not already kneeling, with no living leader in this world, struck down to {@code yield_health} of
     * its health or less by a seated Espada player (no fake player), kneels to that Espada for {@code yield_seconds},
     * and the Espada is told it may take it with an empty hand. Never a Privaron Espada, and never with the Fracción
     * off.
     */
    public static void maybeYield(ArrancarNpc npc, DamageSource source) {
        if (!(npc.level() instanceof ServerLevel level) || !npc.isAlive() || npc.isDeparting() || Negacion.held(npc)
                || npc.isPledged() || npc.isYielding() || npc.kind() != ArrancarRank.Kind.NUMERO
                || BleachConfig.get(BleachConfig.FRACCION_MAX) <= 0) {
            return;
        }
        if (!(source.getEntity() instanceof Player espada) || espada instanceof FakePlayer
                || !EspadaLedger.isSeatedEspada(espada)) {
            return;
        }
        // Loyalty is personal: one whose leader lives is not taken from it.
        if (npc.getHealth() > BleachConfig.get(BleachConfig.FRACCION_YIELD_HEALTH) * npc.getMaxHealth()
                || npc.leader() != null) {
            return;
        }
        npc.yieldTo(espada, level.getGameTime() + BleachConfig.get(BleachConfig.FRACCION_YIELD_SECONDS) * 20L);
        espada.displayClientMessage(Component.translatable(FRACCION + "yields", npc.getDisplayName())
                .withStyle(ChatFormatting.YELLOW), true);
    }

    /**
     * The kneeling Número takes this Espada as its master, if it may: it must still kneel to this player, free and
     * not leaving (else NOT_KNEELING, told nothing), the Fracción must be on, the player must still hold a seat, and
     * its list in the {@link EspadaLedger} must have room for one more. Then it pledges ({@link ArrancarNpc#pledgeTo}),
     * is listed, and the Espada is told in chat and granted its milestone. A refusal is told above the hotbar, and
     * leaves the Número kneeling until its time runs out.
     */
    public static Pledge pledge(ArrancarNpc npc, Player espada, long now) {
        if (!(npc.level() instanceof ServerLevel level) || !npc.isAlive() || npc.isPledged() || npc.isDeparting()
                || Negacion.held(npc) || !espada.getUUID().equals(npc.yieldedTo())) {
            return Pledge.NOT_KNEELING;
        }
        int most = BleachConfig.get(BleachConfig.FRACCION_MAX);
        if (most <= 0) {
            return Pledge.OFF;
        }
        if (!EspadaLedger.isSeatedEspada(espada)) {
            espada.displayClientMessage(Component.translatable(FRACCION + "not_espada").withStyle(ChatFormatting.GRAY),
                    true);
            return Pledge.NOT_ESPADA;
        }
        EspadaLedger ledger = EspadaLedger.get(level.getServer());
        int held = ledger.fraccion(espada.getUUID()).size();
        if (held >= most) {
            espada.displayClientMessage(Component.translatable(FRACCION + "full", held).withStyle(ChatFormatting.GRAY),
                    true);
            return Pledge.FULL;
        }
        npc.pledgeTo(espada);
        ledger.addMember(espada.getUUID(), npc.getUUID(), npc.rank().numero());
        espada.displayClientMessage(Component.translatable(FRACCION + "pledged", npc.getDisplayName(),
                ledger.fraccion(espada.getUUID()).size(), most).withStyle(ChatFormatting.GOLD), false);
        if (espada instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.FRACCION);
        }
        return Pledge.PLEDGED;
    }

    /**
     * Its own Espada used it with an empty hand: a plain use shows who it is and how many of the cap serve; with
     * Sneak, the first use arms the dismiss and asks for a second, and a second within
     * {@link ArrancarNpc#DISMISS_CONFIRM_TICKS} takes it off the list and sends it away through a Garganta
     * ({@link ArrancarNpc#releasePledge}). Server side.
     */
    public static void commandFraccion(ArrancarNpc npc, Player espada, long now) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        EspadaLedger ledger = EspadaLedger.get(level.getServer());
        Component name = npc.getDisplayName();
        if (espada.isShiftKeyDown()) {
            if (!npc.armDismiss(now)) {
                espada.displayClientMessage(Component.translatable(FRACCION + "confirm", name)
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            ledger.removeMember(espada.getUUID(), npc.getUUID());
            npc.releasePledge();
            espada.displayClientMessage(Component.translatable(FRACCION + "dismissed", name)
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        espada.displayClientMessage(Component.translatable(FRACCION + "status", name,
                ledger.fraccion(espada.getUUID()).size(), BleachConfig.get(BleachConfig.FRACCION_MAX))
                .withStyle(ChatFormatting.GRAY), true);
    }
}
