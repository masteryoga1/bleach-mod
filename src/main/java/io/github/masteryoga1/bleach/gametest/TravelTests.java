package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.item.HellButterflyItem;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.network.GateKeyPayload;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.soul.TravelRecord;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Senkaimon and the Jigokuchō: who may open a gate and where it leads, the turn of the blade and how it
 * ends, a gate's life, who is given a butterfly, and real players crossing to Soul Society and back or turned away.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class TravelTests {
    /** Where a test's soul stands, facing south, so its gate stands inside the structure 2 blocks on. */
    private static final Vec3 SPOT = new Vec3(1.5, 1.0, 0.5);
    private static final long DAY = 24_000L;
    /** How near a spot worked out from the same numbers must be. */
    private static final double SAME = 1.0E-6;
    private static final String UNGUIDED_KEY = "message.bleach.senkaimon.unguided";
    private static final String MOUNT_KEY = "message.bleach.senkaimon.mount";

    private TravelTests() {
    }

    // ---- Shared helpers ----

    /** A mock player with this soul at {@link #SPOT}, facing south, holding a nameless Asauchi. */
    private static Player mock(GameTestHelper helper, GameType mode, SoulData data) {
        Player player = helper.makeMockPlayer(mode);
        player.setData(SoulAttachments.SOUL, data);
        Vec3 at = helper.absoluteVec(SPOT);
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        return player;
    }

    /** A Shinigami full of 800 reiryoku, whom a gate costs 40. */
    private static SoulData shinigami() {
        return ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F);
    }

    /** Asserts the answer to opening a gate: null for a gate, or the refusal. */
    private static void refuses(GameTestHelper helper, @Nullable TravelLogic.Refusal actual,
                                @Nullable TravelLogic.Refusal expected, String what) {
        helper.assertTrue(actual == expected, what + " gave " + actual + ", not " + expected);
    }

    /** The rules' answer to this player, with this soul, opening a gate in this world now. */
    @Nullable
    private static TravelLogic.Refusal ask(Player player, SoulData data, ResourceKey<Level> world, long now) {
        return TravelLogic.mayOpenSenkaimon(player, data, world, now);
    }

    /** How many times this joined player's action bar has shown the line with this key. */
    private static long lines(ServerPlayer player, String key) {
        return TestPlayers.actionBar(player).stream()
                .filter(line -> line.getContents() instanceof TranslatableContents words && words.getKey().equals(key))
                .count();
    }

    /** The keys of every line this joined player's action bar has shown, oldest first, for a failure to name. */
    private static List<String> said(ServerPlayer player) {
        return TestPlayers.actionBar(player).stream()
                .map(line -> line.getContents() instanceof TranslatableContents words ? words.getKey() : line.getString())
                .toList();
    }

    /** Where a joined player is, for a failure to name. */
    private static String where(ServerPlayer player) {
        return player.level().dimension().location() + " " + player.position();
    }

    /** The gates standing in this test's structure. */
    private static List<Senkaimon> gates(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Senkaimon.class, helper.getBounds());
    }

    /** How many Jigokuchō this player carries. */
    private static int butterflies(Player player) {
        return player.getInventory().countItem(ModItems.HELL_BUTTERFLY.get());
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** One step of a test that waits. Should it fail, everything on the list is taken away first. */
    private static Runnable step(List<Entity> spawned, Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (RuntimeException e) {
                discard(spawned.toArray(new Entity[0]));
                throw e;
            }
        };
    }

    /**
     * The real Soul Society, its 5×5 chunks round the court loaded now and held entity-ticking, so a gate there ticks at
     * once. Never released: every test landing at the court shares the ticket, and the test server ends with the run.
     */
    private static ServerLevel holdCourt(GameTestHelper helper) {
        ServerLevel soul = helper.getLevel().getServer().getLevel(BleachWorlds.SOUL_SOCIETY);
        helper.assertTrue(soul != null, "the test server has no Soul Society");
        ChunkPos court = new ChunkPos(BlockPos.containing(TravelLogic.COURT));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                soul.getChunk(court.x + x, court.z + z);
            }
        }
        soul.getChunkSource().addRegionTicket(TicketType.FORCED, court, 2, court);
        return soul;
    }

    // ---- Who may open a gate ----

    /**
     * Each rule for opening a gate, in its order: the dead, the held, the powerless, the seized, the bound, the lender,
     * the wrong world, the wrong blade, the wait and the cost, then creative's pass and, last, a gate already in reach.
     */
    @GameTest(template = "empty")
    public static void onlyAShinigamiWithABladeOpensASenkaimon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        ResourceKey<Level> human = Level.OVERWORLD;
        Player anyone = mock(helper, GameType.SURVIVAL, shinigami());
        SoulData giver = shinigami();
        SoulData receiver = ShinigamiTests.soul(SoulState.HUMAN, 100.0F);
        ShinigamiLogic.transferFromShinigami(giver, receiver, now);
        Player held = mock(helper, GameType.SURVIVAL, shinigami());
        NegacionLight light = NegacionLight.place(level, held, null);
        Player host = mock(helper, GameType.SURVIVAL, shinigami());
        SoulData hostData = SoulLogic.get(host);
        VisoredLogic.setRecord(hostData, VisoredLogic.record(hostData).withRootedAt(now - DAY));
        HellButterflyItem.give(host);
        Senkaimon gate = null;
        try {
            refuses(helper, ask(anyone, shinigami(), human, now), null, "a Shinigami in the Human World");
            refuses(helper, ask(anyone, shinigami(), BleachWorlds.SOUL_SOCIETY, now), null, "a Shinigami in Soul Society");
            refuses(helper, ask(anyone, ShinigamiTests.substitute(400.0F), human, now), null, "a Substitute");
            refuses(helper, ask(anyone, receiver, human, now), null, "a Human given a Shinigami's power");

            Player dead = mock(helper, GameType.SURVIVAL, shinigami());
            dead.setHealth(0.0F);
            refuses(helper, ask(dead, SoulLogic.get(dead), human, now), TravelLogic.Refusal.UNABLE, "a dead Shinigami");
            Player spectator = mock(helper, GameType.SPECTATOR, shinigami());
            refuses(helper, ask(spectator, SoulLogic.get(spectator), human, now), TravelLogic.Refusal.UNABLE,
                    "a spectating Shinigami");

            helper.assertTrue(light != null && held.startRiding(light, true) && Negacion.held(held),
                    "the mock Shinigami could not be held");
            refuses(helper, ask(held, SoulLogic.get(held), human, now), TravelLogic.Refusal.HELD,
                    "a Shinigami held by Negación");

            SoulData fallen = ShinigamiTests.soul(SoulState.HOLLOW, 400.0F);
            fallen.setTier(PowerPath.SHINIGAMI, 1);
            SoulData arrancar = ShinigamiTests.soul(SoulState.ARRANCAR, 400.0F);
            arrancar.setTier(PowerPath.SHINIGAMI, 1);
            SoulData quincy = QuincyTests.withShinigamiPower(QuincyTests.quincy(Bloodline.ECHT, 1, 400.0F));
            refuses(helper, ask(anyone, ShinigamiTests.soul(SoulState.HUMAN, 400.0F), human, now),
                    TravelLogic.Refusal.NO_POWER, "a Human");
            refuses(helper, ask(anyone, ShinigamiTests.student(60.0F), human, now), TravelLogic.Refusal.NO_POWER,
                    "an Academy student");
            refuses(helper, ask(anyone, fallen, human, now), TravelLogic.Refusal.NO_POWER, "a Hollow with a Shinigami tier");
            refuses(helper, ask(anyone, arrancar, human, now), TravelLogic.Refusal.NO_POWER,
                    "an Arrancar with a Shinigami tier");
            refuses(helper, ask(anyone, ShinigamiTests.soul(SoulState.PLUS, 40.0F), human, now),
                    TravelLogic.Refusal.NO_POWER, "a Plus");
            refuses(helper, ask(anyone, quincy, human, now), TravelLogic.Refusal.NO_POWER,
                    "an awakened Quincy with a Shinigami tier, hybrids barred");

            helper.assertTrue(VisoredEvents.startTakeover(host, hostData, false, now) && VisoredLogic.takenOver(hostData),
                    "the rooted Hollow did not seize its host");
            refuses(helper, ask(host, hostData, human, now), TravelLogic.Refusal.TAKEN, "a soul whose Hollow holds its body");
            helper.assertFalse(TravelLogic.guided(host), "a body its Hollow holds is guided by the Jigokuchō it carries");

            Player bound = mock(helper, GameType.SURVIVAL, shinigami());
            bound.addEffect(new MobEffectInstance(ModEffects.BAKUDO_BOUND, 100));
            refuses(helper, ask(bound, SoulLogic.get(bound), human, now), TravelLogic.Refusal.BOUND,
                    "a Shinigami bound by a Bakudō");
            refuses(helper, ask(anyone, giver, human, now), TravelLogic.Refusal.LENT, "a Shinigami whose power is lent");
            for (ResourceKey<Level> world : List.of(Level.NETHER, Level.END, BleachWorlds.HUECO_MUNDO, BleachWorlds.DANGAI)) {
                refuses(helper, ask(anyone, shinigami(), world, now), TravelLogic.Refusal.WRONG_WORLD,
                        "a Shinigami in " + world.location());
            }

            anyone.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            refuses(helper, ask(anyone, shinigami(), human, now), TravelLogic.Refusal.NO_BLADE, "an empty hand");
            anyone.setItemInHand(InteractionHand.MAIN_HAND,
                    ZanpakutoTests.someoneElsesBlade(helper, ZanpakutoSpirits.ZABIMARU));
            refuses(helper, ask(anyone, shinigami(), human, now), TravelLogic.Refusal.NO_BLADE, "someone else's blade");
            anyone.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            anyone.setItemInHand(InteractionHand.OFF_HAND, ZanpakutoTests.asauchi());
            refuses(helper, ask(anyone, shinigami(), human, now), TravelLogic.Refusal.NO_BLADE,
                    "an Asauchi in the off hand alone");
            anyone.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack own = ZanpakutoTests.asauchi();
            ZanpakutoItem.bind(own, anyone, ZanpakutoSpirits.ZABIMARU.location(),
                    ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU), false);
            anyone.setItemInHand(InteractionHand.MAIN_HAND, own);
            refuses(helper, ask(anyone, shinigami(), human, now), null, "the Shinigami's own named blade");
            anyone.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());

            SoulData waiting = shinigami();
            TravelLogic.startCooldown(waiting, now);
            refuses(helper, ask(anyone, waiting, human, now + 1L), TravelLogic.Refusal.COOLDOWN, "a gate a tick after one");
            long seconds = TravelLogic.cooldownSeconds(waiting, now + 1L);
            helper.assertTrue(seconds == 30L, "a tick into the wait, " + seconds + " seconds are left, not 30");
            refuses(helper, ask(anyone, waiting, human, now + 600L), null, "a gate once the wait has run");
            SoulData weak = shinigami();
            // 40.4 less the cost of 40 leaves under the half a point every soul keeps.
            weak.setReiryoku(40.4F);
            refuses(helper, ask(anyone, weak, human, now), TravelLogic.Refusal.TOO_WEAK, "a Shinigami with 40.4 reiryoku");
            weak.setReiryoku(40.5F);
            refuses(helper, ask(anyone, weak, human, now), null, "a Shinigami with 40.5 reiryoku");
            float cost = TravelLogic.openCost(shinigami());
            helper.assertTrue(cost == 40.0F, "a gate costs a Shinigami of 800 " + cost + ", not 40");
            float least = TravelLogic.openCost(ShinigamiTests.soul(SoulState.SHINIGAMI, 60.0F));
            helper.assertTrue(least == 5.0F, "a gate costs a Shinigami of 60 " + least + ", not the least cost of 5");

            Player creative = mock(helper, GameType.CREATIVE, shinigami());
            SoulData spent = SoulLogic.get(creative);
            spent.setReiryoku(0.0F);
            TravelLogic.startCooldown(spent, now);
            refuses(helper, ask(creative, spent, human, now), null, "a creative Shinigami, spent and waiting");

            gate = Senkaimon.open(level, anyone, now);
            helper.assertTrue(gate != null, "no gate opened for the crowding rows");
            Vec3 ahead = helper.absoluteVec(SPOT.add(0.0, 0.0, Senkaimon.DISTANCE));
            helper.assertTrue(gate.position().distanceTo(ahead) < SAME, "the gate stands at " + gate.position()
                    + ", not " + ahead);
            refuses(helper, ask(creative, spent, human, now), TravelLogic.Refusal.CROWDED,
                    "a creative Shinigami before an open gate");
            Vec3 near = helper.absoluteVec(SPOT.add(3.5, 0.0, 0.0));
            anyone.moveTo(near.x, near.y, near.z, 0.0F, 0.0F);
            refuses(helper, ask(anyone, shinigami(), human, now), TravelLogic.Refusal.CROWDED,
                    "a gate whose box would stand half a block from the open one's");
            Vec3 far = helper.absoluteVec(SPOT.add(3.7, 0.0, 0.0));
            anyone.moveTo(far.x, far.y, far.z, 0.0F, 0.0F);
            refuses(helper, ask(anyone, shinigami(), human, now), null,
                    "a gate whose box would stand 0.7 blocks from the open one's");
        } finally {
            VisoredEvents.endTakeover(host, hostData, VisoredEvents.TakeoverEnd.REMOVED, now);
            held.stopRiding();
            discard(light, gate);
        }
        helper.succeed();
    }

    /** Where a gate leads from each world, the court it comes out at, and the order a gate home tries its landings. */
    @GameTest(template = "empty")
    public static void aSenkaimonLeadsBetweenTwoWorlds(GameTestHelper helper) {
        helper.assertTrue(TravelLogic.leadsTo(Level.OVERWORLD) == BleachWorlds.SOUL_SOCIETY,
                "a gate from the Human World leads to " + TravelLogic.leadsTo(Level.OVERWORLD));
        helper.assertTrue(TravelLogic.leadsTo(BleachWorlds.SOUL_SOCIETY) == Level.OVERWORLD,
                "a gate from Soul Society leads to " + TravelLogic.leadsTo(BleachWorlds.SOUL_SOCIETY));
        for (ResourceKey<Level> world : List.of(Level.NETHER, Level.END, BleachWorlds.HUECO_MUNDO, BleachWorlds.DANGAI)) {
            helper.assertTrue(TravelLogic.leadsTo(world) == null,
                    "a gate from " + world.location() + " leads to " + TravelLogic.leadsTo(world));
        }
        helper.assertTrue(SeireiteiPlan.SENKAIMON_COURT_X == 192 && SeireiteiPlan.SENKAIMON_COURT_Z == -288,
                "the court is at " + SeireiteiPlan.SENKAIMON_COURT_X + ", " + SeireiteiPlan.SENKAIMON_COURT_Z);
        helper.assertTrue(TravelLogic.COURT.equals(new Vec3(192.5, 72.0, -287.5)), "gates come out at " + TravelLogic.COURT);

        TravelRecord home = new TravelRecord(Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, -20))), 7L);
        TravelRecord nether = new TravelRecord(Optional.of(GlobalPos.of(Level.NETHER, new BlockPos(10, 64, -20))), 7L);
        List<TravelLogic.Return> spawn = List.of(TravelLogic.Return.WORLD_SPAWN);
        assertOrder(helper, TravelRecord.NONE, null, spawn, "no record and no respawn point");
        assertOrder(helper, TravelRecord.NONE, Level.OVERWORLD,
                List.of(TravelLogic.Return.RESPAWN, TravelLogic.Return.WORLD_SPAWN), "a respawn point in the Human World");
        assertOrder(helper, TravelRecord.NONE, Level.NETHER, spawn, "a respawn point in the Nether");
        assertOrder(helper, home, Level.OVERWORLD,
                List.of(TravelLogic.Return.RECORD, TravelLogic.Return.RESPAWN, TravelLogic.Return.WORLD_SPAWN),
                "a record and a respawn point in the Human World");
        assertOrder(helper, nether, null, spawn, "a record in the Nether");

        Tag saved = TravelRecord.CODEC.encodeStart(NbtOps.INSTANCE, home).getOrThrow();
        TravelRecord read = TravelRecord.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(read.equals(home), "saved " + home + " but read " + read);
        CompoundTag bad = new CompoundTag();
        bad.put("butterfly_day", StringTag.valueOf("soon"));
        bad.put("human_world_return", IntTag.valueOf(3));
        TravelRecord lenient = TravelRecord.CODEC.parse(NbtOps.INSTANCE, bad).getOrThrow();
        helper.assertTrue(lenient.equals(TravelRecord.NONE), "bad fields read as " + lenient + ", not as no record");
        helper.assertTrue(TravelRecord.NONE.humanWorldReturn().isEmpty() && TravelRecord.NONE.butterflyDay() == -1L,
                "no record is " + TravelRecord.NONE);
        helper.succeed();
    }

    /** Asserts the order a gate home tries its landings in, for this record and respawn world. */
    private static void assertOrder(GameTestHelper helper, TravelRecord record, @Nullable ResourceKey<Level> respawn,
                                    List<TravelLogic.Return> expected, String what) {
        List<TravelLogic.Return> order = TravelLogic.returnOrder(record, respawn);
        helper.assertTrue(order.equals(expected), "with " + what + ", a gate home tries " + order + ", not " + expected);
    }

    /**
     * The way home lands on the carpet a soul left from, though the block it stood in is the carpet's and a roof is
     * over it, and a spot walled in lands on top of its column. Open to the sky, so no barrier roofs the test's columns.
     */
    @GameTest(template = "lane", skyAccess = true)
    public static void theWayHomeLandsOnTheFloorItLeft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = mock(helper, GameType.SURVIVAL, shinigami());
        helper.setBlock(2, 1, 1, Blocks.WHITE_CARPET);
        helper.setBlock(2, 3, 1, Blocks.STONE);
        Vec3 carpet = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
        Vec3 onCarpet = carpet.add(0.0, 1.0 / 16.0, 0.0);
        Vec3 spot = TravelEvents.landing(player, level, carpet);
        helper.assertTrue(spot != null && spot.distanceTo(onCarpet) < SAME, "the soul that left from the carpet came "
                + "back to " + spot + ", not onto it at " + onCarpet);

        helper.setBlock(6, 1, 1, Blocks.STONE);
        helper.setBlock(6, 2, 1, Blocks.STONE);
        Vec3 walled = helper.absoluteVec(new Vec3(6.5, 1.0, 1.5));
        Vec3 onTop = walled.add(0.0, 2.0, 0.0);
        spot = TravelEvents.landing(player, level, walled);
        helper.assertTrue(spot != null && spot.distanceTo(onTop) < SAME, "the soul whose spot was walled in came back to "
                + spot + ", not to the top of its column at " + onTop);
        helper.succeed();
    }

    // ---- Crossing ----

    /**
     * A real player, guided and holding its blade, turns it and opens a gate, stands in it and comes out at the court in
     * Soul Society. A gate opened there brings it home to the block it left from, and the first gate, still open, does
     * not take it again.
     */
    @GameTest(template = "empty", batch = "senkaimon", timeoutTicks = 200)
    public static void aGuidedShinigamiCrossesAndComesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel soul = holdCourt(helper);
        // The floor under the gate, which the way home lands on.
        helper.setBlock(1, 0, 2, Blocks.STONE);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, SPOT);
        player.setData(SoulAttachments.SOUL, shinigami());
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        HellButterflyItem.give(player);
        List<Entity> spawned = new ArrayList<>();
        BlockPos[] inside = new BlockPos[1];
        refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, HollowPlayerTests.now(helper)), null,
                "the guided Shinigami's turn of the blade");

        // The turn ends on tick 30, when the player's tick opens the gate; the gate first ticks on 31.
        helper.runAtTickTime(25, step(spawned, () -> helper.assertTrue(gates(helper).isEmpty(),
                "a gate opened before the blade had turned")));
        helper.runAtTickTime(35, step(spawned, () -> {
            List<Senkaimon> open = gates(helper);
            spawned.addAll(open);
            helper.assertTrue(open.size() == 1, "the turned blade opened " + open.size() + " gates; the action bar said "
                    + said(player));
            Senkaimon gate = open.get(0);
            helper.assertTrue(player.getUUID().equals(gate.opener()) && gate.leadsTo() == BleachWorlds.SOUL_SOCIETY
                    && gate.facing() == Direction.SOUTH, "the gate was opened by " + gate.opener() + ", leads to "
                    + gate.leadsTo() + " and faces " + gate.facing());
            Vec3 ahead = player.position().add(0.0, 0.0, Senkaimon.DISTANCE);
            helper.assertTrue(gate.position().distanceTo(ahead) < SAME, "the gate stands at " + gate.position()
                    + ", not 2 blocks before the player at " + ahead);
            player.teleportTo(level, gate.getX(), gate.getY(), gate.getZ(), 0.0F, 0.0F);
            inside[0] = player.blockPosition();
        }));
        // Inside from tick 36, it passes on its 21st tick there, 56.
        helper.runAtTickTime(45, step(spawned, () -> helper.assertTrue(player.level() == level,
                "the Shinigami passed after 10 ticks in the gate, to " + where(player))));
        helper.runAtTickTime(70, step(spawned, () -> {
            helper.assertTrue(player.level() == soul, "the guided Shinigami is at " + where(player)
                    + "; the action bar said " + said(player));
            helper.assertTrue(player.position().distanceTo(TravelLogic.COURT) < SAME, "the Shinigami came out at "
                    + player.position() + ", not at the court, " + TravelLogic.COURT);
            // The arrival's teleport was answered, so the wait after a pass has run since.
            helper.assertTrue(!player.isChangingDimension() && player.getPortalCooldown() == 0,
                    "the arrival is still changing world (" + player.isChangingDimension() + ") or waiting "
                            + player.getPortalCooldown() + " ticks");
            Optional<GlobalPos> left = player.getData(SoulAttachments.TRAVEL).humanWorldReturn();
            helper.assertTrue(left.equals(Optional.of(GlobalPos.of(Level.OVERWORLD, inside[0]))),
                    "the record of where it left is " + left + ", not " + inside[0]);
            Senkaimon back = Senkaimon.open(soul, player, soul.getGameTime());
            helper.assertTrue(back != null, "no gate would open at the court");
            spawned.add(back);
            helper.assertTrue(back.leadsTo() == Level.OVERWORLD, "the gate at the court leads to " + back.leadsTo());
            player.teleportTo(soul, back.getX(), back.getY(), back.getZ(), 0.0F, 0.0F);
        }));
        // The gate home first ticks on 71, and the player passes on 91.
        helper.runAtTickTime(80, step(spawned, () -> helper.assertTrue(player.level() == soul,
                "the Shinigami passed home after 10 ticks in the gate, to " + where(player))));
        helper.runAtTickTime(110, step(spawned, () -> {
            Vec3 left = Vec3.atBottomCenterOf(inside[0]);
            helper.assertTrue(player.level() == level && player.position().distanceTo(left) < SAME,
                    "the gate home brought the Shinigami to " + where(player) + ", not where it left, " + left
                            + "; the action bar said " + said(player));
        }));
        // The first gate stands open round it, and the wait it re-arms each tick keeps it from passing again.
        helper.runAtTickTime(140, step(spawned, () -> {
            helper.assertTrue(player.level() == level, "the first gate took the Shinigami back, to " + where(player));
            discard(spawned.toArray(new Entity[0]));
            helper.succeed();
        }));
    }

    // ---- The turn and the gate ----

    /**
     * The turn ends with a blow that lands, a change of world, a screen, or a key let go too soon, and a second press is
     * ignored. A turn done opens a gate that is paid for, starts the wait, closes on time and is never saved.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aSenkaimonOpensForItsTimeAndIsNeverSaved(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player player = mock(helper, GameType.SURVIVAL, shinigami());
        SoulData data = SoulLogic.get(player);
        List<Entity> spawned = new ArrayList<>();
        Senkaimon[] gate = new Senkaimon[1];
        step(spawned, () -> {
            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, now - 90L), null, "the first turn");
            Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5, 1.0, 0.5));
            try {
                player.invulnerableTime = 0;
                helper.assertTrue(player.hurt(player.damageSources().mobAttack(villager), 1.0F),
                        "the villager's blow did not land");
            } finally {
                discard(villager);
            }
            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.RELEASE, now - 80L), null,
                    "letting go after a blow broke the turn");

            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, now - 70L), null, "the second turn");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.RELEASE, now - 60L), null,
                    "letting go after a change of world dropped the turn");

            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, now - 50L), null, "the third turn");
            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.CANCEL, now - 45L),
                    TravelLogic.Refusal.SLIPPED, "a screen opened during the turn");

            // Begun 10 ticks after the third, the fourth turn is neither seen nor heard by those nearby.
            player.swinging = false;
            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, now - 40L), null, "the fourth turn");
            helper.assertFalse(player.swinging, "the blade was seen to turn again 10 ticks after it last was");
            // Let go 20 ticks in, short of the 29 a turn of 30 needs.
            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.RELEASE, now - 20L),
                    TravelLogic.Refusal.SLIPPED, "letting go too soon");

            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, now - 30L), null, "the fifth turn");
            helper.assertTrue(player.swinging, "the blade was not seen to turn a second after it last was");
            refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, now - 20L), null,
                    "a second press during the turn");
            helper.assertTrue(gates(helper).isEmpty(), "a gate opened before any turn was done");
            helper.assertTrue(data.reiryoku() == 800.0F, "the turns cost reiryoku, leaving " + data.reiryoku());

            KidoTests.withConfig(BleachConfig.SENKAIMON_OPEN_SECONDS, 5, () -> refuses(helper, TravelEvents.onKey(player,
                    GateKeyPayload.GateKey.RELEASE, now), null, "letting go once the turn is done"));
            List<Senkaimon> open = gates(helper);
            spawned.addAll(open);
            helper.assertTrue(open.size() == 1, "the turn done opened " + open.size() + " gates");
            gate[0] = open.get(0);
            Vec3 ahead = player.position().add(0.0, 0.0, Senkaimon.DISTANCE);
            helper.assertTrue(gate[0].facing() == Direction.SOUTH && gate[0].position().distanceTo(ahead) < SAME,
                    "the gate faces " + gate[0].facing() + " at " + gate[0].position() + ", not south at " + ahead);
            helper.assertTrue(gate[0].endsAt() == now + 100L, "the gate closes at " + gate[0].endsAt() + ", not "
                    + (now + 100L));
            AABB box = gate[0].getBoundingBox();
            HollowPlayerTests.assertClose(helper, box.getXsize(), 3.0, "the gate's width");
            HollowPlayerTests.assertClose(helper, box.getYsize(), 4.0, "the gate's height");
            HollowPlayerTests.assertClose(helper, box.getZsize(), 1.0, "the gate's depth");
            helper.assertTrue(gate[0].leadsTo() == BleachWorlds.SOUL_SOCIETY && player.getUUID().equals(gate[0].opener()),
                    "the gate leads to " + gate[0].leadsTo() + " and was opened by " + gate[0].opener());
            helper.assertTrue(data.reiryoku() == 760.0F, "the gate left " + data.reiryoku() + " reiryoku, not 760");
            refuses(helper, ask(player, data, Level.OVERWORLD, now + 1L), TravelLogic.Refusal.COOLDOWN,
                    "a second gate a tick later");

            // Never written with its chunk, though it goes when the chunk does; never summoned.
            EntityType<?> type = gate[0].getType();
            helper.assertFalse(type.canSerialize(), "the Senkaimon can be saved");
            helper.assertFalse(gate[0].saveAsPassenger(new CompoundTag()), "the Senkaimon was written with its chunk");
            helper.assertTrue(gate[0].shouldBeSaved(), "the Senkaimon would stay loaded with no chunk about it");
            helper.assertTrue(type.is(SoulLogic.SPIRITS), "the Senkaimon is not a spirit");
            helper.assertFalse(type.canSummon(), "the Senkaimon can be summoned");
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("senkaimon")) == ModEntities.SENKAIMON.get(),
                    "bleach:senkaimon is " + BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("senkaimon")));
            int stack = new ItemStack(ModItems.HELL_BUTTERFLY.get()).getMaxStackSize();
            helper.assertTrue(stack == 1, "Jigokuchō stack to " + stack);
        }).run();
        helper.runAtTickTime(95, step(spawned, () -> helper.assertFalse(gate[0].isRemoved(),
                "the gate closed before its 5 seconds were up")));
        helper.runAtTickTime(105, step(spawned, () -> {
            helper.assertTrue(gate[0].isRemoved(), "the gate is still open after its 5 seconds");
            helper.succeed();
        }));
    }

    // ---- The Jigokuchō ----

    /**
     * A graduate is given a Jigokuchō and the day is marked; at login a Shinigami never given one is, once, but one lost
     * is not given again, and a soul without a Shinigami's power is given none. A stolen one guides no one.
     */
    @GameTest(template = "empty")
    public static void aJigokuchoAtGraduationOrFirstLogin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long today = KidoLogic.lessonDay(level.getGameTime(), level.getDayTime());

        Player graduate = mock(helper, GameType.SURVIVAL, ShinigamiTests.student(40.0F));
        SoulData data = SoulLogic.get(graduate);
        ShinigamiLogic.graduationRequirements().forEach((art, at) -> data.setLevel(art, at));
        helper.assertTrue(ShinigamiEvents.graduate(graduate), "a trained student failed the exams");
        helper.assertTrue(butterflies(graduate) == 1, "graduating gave " + butterflies(graduate) + " Jigokuchō");
        assertDay(helper, graduate, today, "a graduate");
        helper.assertTrue(TravelLogic.guided(graduate), "a new Shinigami carrying its Jigokuchō is not guided");

        graduate.getInventory().clearContent();
        helper.assertFalse(TravelLogic.guided(graduate), "a Shinigami who lost its Jigokuchō is still guided");
        TravelEvents.butterflyAtLogin(graduate);
        helper.assertTrue(butterflies(graduate) == 0, "a Shinigami given its Jigokuchō at graduation was given "
                + butterflies(graduate) + " more at login");
        assertDay(helper, graduate, today, "a graduate after login");

        Player elder = mock(helper, GameType.SURVIVAL, shinigami());
        TravelEvents.butterflyAtLogin(elder);
        helper.assertTrue(butterflies(elder) == 1, "a Shinigami never given one was given " + butterflies(elder)
                + " Jigokuchō at login");
        assertDay(helper, elder, today, "a Shinigami given one at login");
        helper.assertTrue(TravelLogic.guided(elder), "a Shinigami given its Jigokuchō at login is not guided");
        TravelEvents.butterflyAtLogin(elder);
        helper.assertTrue(butterflies(elder) == 1, "a second login left " + butterflies(elder) + " Jigokuchō");

        Player carrier = mock(helper, GameType.SURVIVAL, shinigami());
        HellButterflyItem.give(carrier);
        TravelEvents.butterflyAtLogin(carrier);
        helper.assertTrue(butterflies(carrier) == 1, "a Shinigami never given one but carrying one has "
                + butterflies(carrier) + " after login");
        assertDay(helper, carrier, today, "a Shinigami already carrying one at login");

        Player human = mock(helper, GameType.SURVIVAL, ShinigamiTests.soul(SoulState.HUMAN, 400.0F));
        TravelEvents.butterflyAtLogin(human);
        helper.assertTrue(butterflies(human) == 0, "a Human was given " + butterflies(human) + " Jigokuchō at login");
        assertDay(helper, human, -1L, "a Human after login");

        Player thief = mock(helper, GameType.SURVIVAL, ShinigamiTests.soul(SoulState.HUMAN, 400.0F));
        HellButterflyItem.give(thief);
        helper.assertFalse(TravelLogic.guided(thief), "a Human carrying a stolen Jigokuchō is guided");
        SoulData fallen = ShinigamiTests.soul(SoulState.HOLLOW, 400.0F);
        fallen.setTier(PowerPath.SHINIGAMI, 1);
        Player hollow = mock(helper, GameType.SURVIVAL, fallen);
        HellButterflyItem.give(hollow);
        helper.assertFalse(TravelLogic.guided(hollow), "a Hollow with a Shinigami tier carrying a Jigokuchō is guided");
        helper.succeed();
    }

    /** Asserts the day this player was last given a Jigokuchō, -1 for never. */
    private static void assertDay(GameTestHelper helper, Player player, long expected, String what) {
        long day = player.getData(SoulAttachments.TRAVEL).butterflyDay();
        helper.assertTrue(day == expected, what + " was last given a Jigokuchō on day " + day + ", not " + expected);
    }

    // ---- Turned away ----

    /**
     * Real players at an open gate: a Shinigami with no Jigokuchō and a Human carrying one are each told once that they
     * are not guided, and a guided Shinigami on a mount is told once a second to get down. None of them passes.
     */
    @GameTest(template = "empty", batch = "senkaimon")
    public static void theUnguidedAndTheMountedAreTurnedAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        ServerPlayer unguided = TestPlayers.join(helper, GameType.SURVIVAL, SPOT);
        unguided.setData(SoulAttachments.SOUL, shinigami());
        Vec3 left = new Vec3(0.6, 1.0, 2.5);
        Vec3 right = new Vec3(2.4, 1.0, 2.5);
        ServerPlayer human = TestPlayers.join(helper, GameType.SURVIVAL, left);
        SoulData humanData = ShinigamiTests.soul(SoulState.HUMAN, 400.0F);
        // Met one just now, so no wounded Shinigami comes down by this aware Human while the test runs.
        humanData.setShinigami(humanData.shinigami().withWoundedMetAt(now));
        human.setData(SoulAttachments.SOUL, humanData);
        HellButterflyItem.give(human);
        ServerPlayer rider = TestPlayers.join(helper, GameType.SURVIVAL, right);
        rider.setData(SoulAttachments.SOUL, shinigami());
        HellButterflyItem.give(rider);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, right);
        List<Entity> spawned = new ArrayList<>(List.of(stand));
        step(spawned, () -> {
            stand.setNoGravity(true);
            helper.assertTrue(rider.startRiding(stand, true), "the Shinigami would not ride the armour stand");
            Senkaimon gate = Senkaimon.open(level, unguided, now);
            helper.assertTrue(gate != null, "no gate opened");
            spawned.add(gate);
            Vec3 middle = helper.absoluteVec(new Vec3(1.5, 1.0, 2.5));
            helper.assertTrue(gate.position().distanceTo(middle) < SAME, "the gate stands at " + gate.position()
                    + ", not " + middle);
            unguided.teleportTo(level, gate.getX(), gate.getY(), gate.getZ(), 0.0F, 0.0F);
        }).run();
        // Inside from the gate's first tick, the two on foot are asked on tick 21, and the re-armed wait asks no more.
        helper.runAtTickTime(45, step(spawned, () -> {
            assertTurnedAway(helper, unguided, "the Shinigami with no Jigokuchō");
            assertTurnedAway(helper, human, "the Human carrying a Jigokuchō");
            assertTurnedAway(helper, rider, "the mounted Shinigami");
            helper.assertTrue(lines(unguided, UNGUIDED_KEY) == 1L, "the Shinigami with no Jigokuchō was told "
                    + said(unguided));
            helper.assertTrue(lines(human, UNGUIDED_KEY) == 1L, "the Human carrying a Jigokuchō was told " + said(human));
            // Told on the gate's 20th and 40th ticks.
            helper.assertTrue(rider.isPassenger() && lines(rider, MOUNT_KEY) == 2L && lines(rider, UNGUIDED_KEY) == 0L,
                    "the mounted Shinigami (riding: " + rider.isPassenger() + ") was told " + said(rider));
            discard(spawned.toArray(new Entity[0]));
            helper.succeed();
        }));
    }

    /** Asserts this joined player is still in the test's world, with no record of leaving it. */
    private static void assertTurnedAway(GameTestHelper helper, ServerPlayer player, String what) {
        helper.assertTrue(player.level() == helper.getLevel(), what + " passed, to " + where(player));
        TravelRecord record = player.getData(SoulAttachments.TRAVEL);
        helper.assertTrue(record.humanWorldReturn().isEmpty(), what + " has a record of leaving: " + record);
    }
}
