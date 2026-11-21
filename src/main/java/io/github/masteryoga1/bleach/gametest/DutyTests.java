package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.DutyShinigami;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.DutyEvents;
import io.github.masteryoga1.bleach.soul.FieldTrialEvents;
import io.github.masteryoga1.bleach.soul.HomeWorldRespawn;
import io.github.masteryoga1.bleach.soul.KonsoTarget;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelRecord;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Shinigami on duty: when a Plus is due one and what it is told when none comes, real arrivals for a
 * joined Plus, the NPC itself, which sends a Plus on and leaves, and who is sent home, with real souls sent. The arrivals
 * and the send-homes run in batches of their own, so no other test's Shinigami NPC stands within the 128 blocks that keep
 * one away. The config's defaults are assumed.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class DutyTests {
    /** A new Plus's chain at the default hour, in ticks. */
    private static final int CHAIN = 72_000;
    /** How much of it wears before the first visit: {@code duty_after_minutes} at its default of five. */
    private static final int WORN = 6_000;
    /** The wait between two visits, and between two lines telling a Plus why none came: ten minutes. */
    private static final long WAIT = 12_000L;
    /** How far about the Plus arrivals are looked for and the ground is held ticking: 16 blocks out, and a block more. */
    private static final double REACH = 17.0;
    /** How many visits are tried before the test gives up on finding ground for one. */
    private static final int TRIES = 5;
    /** How far above the Plus the perch stands: more than 16 blocks over any ground about, the test's barrier walls too. */
    private static final int PERCH = 40;
    /** How far above the Plus's feet a thin roof is laid, and how far out each way: its top is 4 up, in reach of both. */
    private static final int ROOF = 3;
    private static final int REACH_BLOCKS = 16;
    /** The tick by which the arrivals must be done; past it, everything is cleared away and the test fails. */
    private static final int GIVE_UP_TICK = 290;
    /** The tick by which the NPC must have left; past it, it is taken away and the test fails. */
    private static final int GIVE_UP_LEAVING_TICK = 190;
    private static final String ARRIVES_KEY = "message.bleach.duty_arrives";
    /** How long a test lets the Shinigami wait before it sends home, or lets go, the soul it came for. */
    private static final int SHORT_ERRAND = 20;
    /** The ticks by which a send-home must be done; past them, everything is cleared away and the test fails. */
    private static final int GIVE_UP_HOME_TICK = 390;
    private static final int GIVE_UP_ACADEMY_TICK = 490;

    private DutyTests() {
    }

    // ---- Helpers ----

    /** A mock player standing next to the NPC these tests spawn, with this soul. */
    private static Player visitor(GameTestHelper helper, SoulData soul) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
        player.setPos(at.x, at.y, at.z);
        player.setData(SoulAttachments.SOUL, soul);
        return player;
    }

    /** A Plus whose chain, of this length, has worn this far. */
    private static SoulData wornPlus(int chain, int worn) {
        SoulData data = ShinigamiTests.soul(SoulState.PLUS, 40.0F);
        data.startChain(chain);
        data.setChainTicks(chain - worn);
        return data;
    }

    /** Up to {@link #TRIES} visits until one comes, which must be the one Shinigami on duty within reach. */
    private static DutyShinigami visit(GameTestHelper helper, ServerPlayer plus, SoulData data, long now, String where) {
        int tries = 0;
        while (tries < TRIES && !DutyEvents.tryVisit(plus, data, now)) {
            tries++;
        }
        helper.assertTrue(tries < TRIES, "none came down on " + where + " in " + TRIES + " tries");
        List<DutyShinigami> come = helper.getLevel().getEntitiesOfClass(DutyShinigami.class,
                plus.getBoundingBox().inflate(REACH));
        helper.assertTrue(come.size() == 1, come.size() + " came down on " + where + ", not one");
        return come.get(0);
    }

    private static double horizontal(Entity a, Entity b) {
        return Math.sqrt(Mth.square(a.getX() - b.getX()) + Mth.square(a.getZ() - b.getZ()));
    }

    /** Stone here, remembered so the test takes it away again. */
    private static void build(ServerLevel level, List<BlockPos> built, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        built.add(pos);
    }

    /** The last line this joined player has been shown in chat, or null before any. */
    @Nullable
    private static Component lastLine(ServerPlayer player) {
        List<Component> chat = TestPlayers.chat(player);
        return chat.isEmpty() ? null : chat.get(chat.size() - 1);
    }

    /** The compass point a visit's line with this key names, as its lang key, or null if the line is not that one. */
    @Nullable
    private static String arrivalPoint(@Nullable Component line, String key) {
        if (line != null && line.getContents() instanceof TranslatableContents told
                && told.getKey().equals(key) && told.getArgs().length == 1
                && told.getArgs()[0] instanceof Component point
                && point.getContents() instanceof TranslatableContents compass) {
            return compass.getKey();
        }
        return null;
    }

    /** The chat lines with this key this joined player has been shown, oldest first. */
    static List<Component> said(ServerPlayer player, String key) {
        return TestPlayers.chat(player).stream().filter(line -> line.getContents() instanceof TranslatableContents told
                && told.getKey().equals(key)).toList();
    }

    /** How many times this joined Plus has been told that no Shinigami on duty could reach it. */
    private static long unreachable(ServerPlayer plus) {
        return TestPlayers.chat(plus).stream().filter(line -> line.getContents() instanceof TranslatableContents told
                && told.getKey().equals(DutyEvents.UNREACHABLE_KEY)).count();
    }

    // ---- The rule ----

    /**
     * A Plus is due a Shinigami on duty once its chain has worn five minutes, or a quarter of a shorter chain, and again
     * once ten minutes have passed since the last came; no other soul is. What a due Plus none came to is told depends
     * on its world.
     */
    @GameTest(template = "empty")
    public static void dutyComesWhenTheChainHasWorn(GameTestHelper helper) {
        long now = 100_000L;
        long never = ShinigamiProgress.NEVER;
        helper.assertFalse(DutyEvents.due(wornPlus(CHAIN, 0), never, now), "a Plus with its whole chain was due a visit");
        helper.assertFalse(DutyEvents.due(wornPlus(CHAIN, WORN - 1), never, now), "a Plus was due a visit a tick early");
        SoulData worn = wornPlus(CHAIN, WORN);
        helper.assertTrue(DutyEvents.due(worn, never, now), "a Plus whose chain had worn five minutes was not due a visit");
        helper.assertFalse(DutyEvents.due(worn, now - WAIT + 1, now), "a Plus was due again before the wait was over");
        helper.assertTrue(DutyEvents.due(worn, now - WAIT, now), "a Plus was not due again once the wait was over");

        // A short chain is visited with three quarters of it left.
        int shortChain = 4_000;
        helper.assertFalse(DutyEvents.due(wornPlus(shortChain, shortChain / 4 - 1), never, now),
                "a Plus on a short chain was due a visit before a quarter of it had worn");
        helper.assertTrue(DutyEvents.due(wornPlus(shortChain, shortChain / 4), never, now),
                "a Plus on a short chain was not due a visit once a quarter of it had worn");

        SoulData rukongai = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        rukongai.startChain(CHAIN);
        rukongai.setChainTicks(0);
        helper.assertFalse(DutyEvents.due(rukongai, never, now), "a Rukongai soul with a worn chain was due a visit");
        SoulData unchained = ShinigamiTests.soul(SoulState.PLUS, 40.0F);
        unchained.clearChain();
        helper.assertFalse(DutyEvents.due(unchained, never, now), "a Plus with no chain was due a visit");

        // In the Overworld none could reach it, in the Dangai none comes, elsewhere its chain holds, and in Soul Society
        // nothing is said.
        helper.assertTrue(DutyEvents.UNREACHABLE_KEY.equals(DutyEvents.unreachedKey(Level.OVERWORLD)),
                "a Plus in the Overworld would be told " + DutyEvents.unreachedKey(Level.OVERWORLD));
        helper.assertTrue(DutyEvents.ELSEWHERE_KEY.equals(DutyEvents.unreachedKey(BleachWorlds.DANGAI)),
                "a Plus in the Dangai would be told " + DutyEvents.unreachedKey(BleachWorlds.DANGAI));
        for (ResourceKey<Level> world : List.of(Level.NETHER, Level.END, BleachWorlds.HUECO_MUNDO)) {
            helper.assertTrue(DutyEvents.HOLDS_KEY.equals(DutyEvents.unreachedKey(world)),
                    "a Plus in " + world.location() + " would be told " + DutyEvents.unreachedKey(world));
        }
        helper.assertTrue(DutyEvents.unreachedKey(BleachWorlds.SOUL_SOCIETY) == null,
                "a Plus in Soul Society would be told " + DutyEvents.unreachedKey(BleachWorlds.SOUL_SOCIETY));
        helper.succeed();
    }

    // ---- Arrivals ----

    /**
     * A real joined Plus and real arrivals: on open ground, none more while that one stands near even once the wait is
     * over, again after the wait, none on a perch high over the field, where the Plus is told so once, and one on the
     * perch once there is floor about it, as on a tower's top, and one on the floor under a roof, as in a shallow cave,
     * not on the roof. The chunks about are forced while it runs and let go, with every block and NPC it made, on every
     * way out.
     */
    @GameTest(template = "field", batch = "duty", skyAccess = true, timeoutTicks = 300)
    public static void dutyShinigamiComesDownNearAPlus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Standing on the field's floor, the layer at relative y 1, as the field's mobs stand at y 2.
        ServerPlayer plus = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 8.5));
        SoulData data = wornPlus(CHAIN, WORN);
        plus.setData(SoulAttachments.SOUL, data);
        Vec3 start = plus.position();
        BlockPos perch = plus.blockPosition().above(PERCH);
        AABB around = new AABB(start, start).inflate(REACH).expandTowards(0.0, PERCH, 0.0);
        List<BlockPos> built = new ArrayList<>();
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, start, REACH);
        Runnable clear = () -> {
            plus.teleportTo(start.x, start.y, start.z);
            QuincyArtsTests.discardAll(level.getEntitiesOfClass(DutyShinigami.class, around));
            built.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            built.clear();
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            forced.clear();
        };
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("the Shinigami on duty never came down in time");
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(HollowBaitTests.allTicking(level, start, REACH),
                        "the ground around the Plus never started ticking"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    // Earlier batches leave persistent NPCs on the grid, which is never cleared; none may stand near.
                    QuincyArtsTests.discardAll(level.getEntitiesOfClass(ShinigamiNpc.class,
                            plus.getBoundingBox().inflate(DutyEvents.EXCLUSION_RANGE).expandTowards(0.0, PERCH, 0.0)));
                    long now = level.getGameTime();
                    DutyShinigami duty = visit(helper, plus, data, now, "open ground");
                    double away = horizontal(duty, plus);
                    helper.assertTrue(away >= 7.29 && away <= 16.71, "it came down " + away + " blocks away, not 8 to 16");
                    Vec3 towards = new Vec3(plus.getX() - duty.getX(), 0.0, plus.getZ() - duty.getZ()).normalize();
                    helper.assertTrue(Vec3.directionFromRotation(0.0F, duty.getYRot()).dot(towards) > 0.99,
                            "it faces " + duty.getYRot() + " and not the Plus");
                    helper.assertTrue(plus.getData(SoulAttachments.DUTY_VISIT) == now, "the visit was not marked");
                    String way = ShinigamiLogic.compassKey(duty.getX() - plus.getX(), duty.getZ() - plus.getZ());
                    Component told = lastLine(plus);
                    helper.assertTrue(way.equals(arrivalPoint(told, ARRIVES_KEY)),
                            "the Plus was told " + told + ", not that one came " + way);

                    // Past the wait, only the one still standing near keeps a second away, and nothing is said.
                    int lines = TestPlayers.chat(plus).size();
                    helper.assertFalse(DutyEvents.tryVisit(plus, data, now + WAIT),
                            "a second came while the first stood near");
                    helper.assertTrue(level.getEntitiesOfClass(DutyShinigami.class, around).size() == 1
                                    && TestPlayers.chat(plus).size() == lines
                                    && plus.getData(SoulAttachments.DUTY_VISIT) == now,
                            "a visit kept away by the first left an NPC, a line or a mark behind");
                    duty.discard();
                    helper.assertFalse(DutyEvents.tryVisit(plus, data, now + WAIT - 1), "one came before the wait was over");
                    helper.assertFalse(DutyEvents.tryVisit(plus, ShinigamiTests.soul(SoulState.HUMAN, 400.0F), now + WAIT),
                            "one came to a Human");
                    plus.setGameMode(GameType.CREATIVE);
                    helper.assertFalse(DutyEvents.tryVisit(plus, data, now + WAIT), "one came to a Plus in creative");
                    plus.setGameMode(GameType.SURVIVAL);
                    helper.assertTrue(level.getEntitiesOfClass(DutyShinigami.class, around).isEmpty()
                                    && TestPlayers.chat(plus).size() == lines,
                            "a visit that was not due left an NPC or a line behind");
                    visit(helper, plus, data, now + WAIT, "open ground again").discard();
                    helper.assertTrue(plus.getData(SoulAttachments.DUTY_VISIT) == now + WAIT, "the revisit was not marked");

                    // On a perch with no floor in reach and the ground far below: told once a wait why none came.
                    build(level, built, perch.below());
                    plus.teleportTo(perch.getX() + 0.5, perch.getY(), perch.getZ() + 0.5);
                    long before = unreachable(plus);
                    helper.assertFalse(DutyEvents.tryVisit(plus, data, now + 2 * WAIT), "one came with no ground in reach");
                    helper.assertFalse(DutyEvents.tryVisit(plus, data, now + 2 * WAIT + 1), "one came a tick later");
                    helper.assertTrue(unreachable(plus) == before + 1,
                            "the Plus out of reach was told " + (unreachable(plus) - before) + " times, not once");
                    // A tower's top: floor 3 to 7 blocks round the perch, at its height.
                    for (int dx = -7; dx <= 7; dx++) {
                        for (int dz = -7; dz <= 7; dz++) {
                            if (dx * dx + dz * dz >= 9 && dx * dx + dz * dz <= 49) {
                                build(level, built, perch.below().offset(dx, 0, dz));
                            }
                        }
                    }
                    DutyShinigami top = visit(helper, plus, data, now + 2 * WAIT + 2, "the tower's top");
                    double near = horizontal(top, plus);
                    helper.assertTrue(near >= 2.99 && near <= 7.01 && Math.abs(top.getY() - plus.getY()) < 0.01,
                            "on the tower it came down " + near + " blocks away at y " + top.getY());
                    helper.assertTrue(unreachable(plus) == before + 1, "a Plus on the tower was told none could reach it");

                    // Under a thin roof, as in a house or a shallow cave: on the floor about the Plus, not on the roof's top.
                    top.discard();
                    built.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
                    built.clear();
                    plus.teleportTo(start.x, start.y, start.z);
                    BlockPos feet = plus.blockPosition();
                    for (int dx = -REACH_BLOCKS; dx <= REACH_BLOCKS; dx++) {
                        for (int dz = -REACH_BLOCKS; dz <= REACH_BLOCKS; dz++) {
                            if (level.getBlockState(feet.offset(dx, ROOF, dz)).isAir()) {
                                build(level, built, feet.offset(dx, ROOF, dz));
                            }
                        }
                    }
                    DutyShinigami below = visit(helper, plus, data, now + 3 * WAIT + 2, "the floor under a roof");
                    double under = horizontal(below, plus);
                    // A spot 3 to 10 away lands on a block's centre, up to 0.71 off; the cleared floor beyond the template's
                    // edge lies 2 lower, and the roof's top is 4 up.
                    helper.assertTrue(under >= 2.29 && under <= 10.71 && below.getY() <= plus.getY() + 0.01
                                    && below.getY() >= plus.getY() - 4.01,
                            "under a roof it came down " + under + " blocks away at y " + below.getY());
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    // ---- The NPC ----

    /**
     * The NPC itself: a spirit in the Shihakushō that cannot be hurt, takes no portal and is never saved. It offers a
     * Plus Konsō and anyone else nothing, leaves soon after sending a Plus on, and takes its stay from a summon's data.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dutyShinigamiSendsOnAndLeaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DutyShinigami duty = helper.spawn(ModEntities.DUTY_SHINIGAMI.get(), 1, 1, 1);
        long now = level.getGameTime();
        try {
            helper.assertTrue(SoulLogic.isSpirit(duty) && duty.getType().is(SoulLogic.SPIRITS),
                    "the Shinigami on duty is not in #bleach:spirits, so the unaware could see it");
            helper.assertTrue(duty.isInvulnerable() && duty.requiresCustomPersistence() && !duty.canUsePortal(false),
                    "the Shinigami on duty can be hurt, despawned or taken through a portal");
            helper.assertTrue(ShihakushoItem.isFullSet(duty), "the Shinigami on duty is not in the Shihakushō");
            ItemStack egg = new ItemStack(ModItems.DUTY_SHINIGAMI_SPAWN_EGG.get());
            helper.assertTrue(ModItems.DUTY_SHINIGAMI_SPAWN_EGG.get().getType(egg) == ModEntities.DUTY_SHINIGAMI.get(),
                    "the Shinigami on duty's egg hatches " + ModItems.DUTY_SHINIGAMI_SPAWN_EGG.get().getType(egg));
            helper.assertTrue(duty.shouldBeSaved(), "a Shinigami on duty would be left behind when its chunk unloads");
            helper.assertFalse(duty.save(new CompoundTag()), "a Shinigami on duty would be saved");

            DialogueView toPlus = duty.viewFor(ShinigamiTests.soul(SoulState.PLUS, 40.0F), now);
            helper.assertTrue(toPlus == DialogueView.PLUS, "a Plus is shown " + toPlus + ", not offered Konsō");
            for (SoulState hollow : List.of(SoulState.HOLLOW, SoulState.ARRANCAR)) {
                DialogueView toHollow = duty.viewFor(ShinigamiTests.soul(hollow, 40.0F), now);
                helper.assertTrue(toHollow == DialogueView.HOLLOW, "a " + hollow + " is shown " + toHollow + ", not warned");
            }
            float aware = (float) (double) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD);
            Map<String, SoulData> others = Map.of(
                    "an aware Human", ShinigamiTests.soul(SoulState.HUMAN, aware),
                    "a Substitute", ShinigamiTests.substitute(60.0F),
                    "a Rukongai soul", ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F),
                    "a student", ShinigamiTests.student(40.0F),
                    "a Shinigami", ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F));
            others.forEach((who, other) -> {
                DialogueView shown = duty.viewFor(other, now);
                helper.assertTrue(shown == DialogueView.RESTING, who + " is shown " + shown + ", not RESTING");
            });
            helper.assertTrue("duty".equals(duty.dialogueKey()), "its lines are under " + duty.dialogueKey() + ", not duty");

            int lifetime = duty.lifetime();
            helper.assertTrue(lifetime >= DutyShinigami.DEFAULT_LIFETIME - 2 && lifetime <= DutyShinigami.DEFAULT_LIFETIME,
                    "a Shinigami on duty comes for " + lifetime + " ticks, not " + DutyShinigami.DEFAULT_LIFETIME);
            Player rukongai = visitor(helper, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F));
            duty.perform(rukongai, NpcAction.KONSO);
            helper.assertTrue(duty.lifetime() == lifetime,
                    "a Rukongai soul asking for Konsō cut the stay to " + duty.lifetime());
            SoulData chained = ShinigamiTests.soul(SoulState.PLUS, 40.0F);
            chained.startChain(CHAIN);
            Player plus = visitor(helper, chained);
            duty.perform(plus, NpcAction.KONSO);
            SoulData sent = SoulLogic.get(plus);
            helper.assertTrue(sent.state() == SoulState.RUKONGAI && sent.chainMaxTicks() == 0,
                    "the Shinigami on duty's Konsō left a " + sent.state() + " with a chain of " + sent.chainMaxTicks());
            helper.assertTrue(duty.lifetime() <= DutyShinigami.LIFETIME_AFTER_KONSO,
                    "having sent a Plus on, the Shinigami on duty stays " + duty.lifetime() + " ticks more");

            // A summon's data has no "Invulnerable": it keeps it invulnerable and sets its stay.
            CompoundTag tag = duty.saveWithoutId(new CompoundTag());
            helper.assertTrue(tag.getInt("Lifetime") == duty.lifetime(), "its stay was written as " + tag.getInt("Lifetime"));
            tag.remove("Invulnerable");
            DutyShinigami copy = ModEntities.DUTY_SHINIGAMI.get().create(level);
            helper.assertTrue(copy != null, "no Shinigami on duty could be made to load into");
            copy.load(tag);
            helper.assertTrue(copy.isInvulnerable() && copy.lifetime() == duty.lifetime(),
                    "data with no Invulnerable loaded a Shinigami on duty that can be hurt, or stays " + copy.lifetime());
        } catch (RuntimeException e) {
            duty.discard();
            throw e;
        }
        helper.runAtTickTime(GIVE_UP_LEAVING_TICK, () -> {
            duty.discard();
            helper.fail("the Shinigami on duty never left after sending a Plus on");
        });
        // It leaves through the shared departure, so it is gone from the world.
        helper.succeedWhen(() -> helper.assertTrue(duty.isRemoved(), "the Shinigami on duty never left"));
    }

    // ---- Sent home ----

    /**
     * Who a Shinigami on duty comes to send home: in the Overworld with no bed or anchor of its own there, once the wait
     * is over and while the server sends souls home, a soul that has passed on, a Quincy among them, to the Rukongai, or
     * to the Academy as a student or where Konsō takes that shortcut; no other soul, and none in another world.
     */
    @GameTest(template = "empty")
    public static void whoIsSentHome(GameTestHelper helper) {
        long now = 100_000L;
        long never = ShinigamiProgress.NEVER;
        SoulData rukongai = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        Map<String, SoulData> passedOn = Map.of("a Rukongai soul", rukongai,
                "a Quincy who passed on", QuincyTests.in(SoulState.RUKONGAI, QuincyTests.quincy(Bloodline.ECHT, 1, 400.0F)),
                "a student", ShinigamiTests.student(40.0F));
        passedOn.forEach((who, data) -> {
            HomeWorldRespawn.Home home = HomeWorldRespawn.passedOnHome(data);
            helper.assertTrue(home == (ShinigamiLogic.isStudent(data) ? HomeWorldRespawn.Home.ACADEMY
                    : HomeWorldRespawn.Home.RUKONGAI), who + "'s home in Soul Society is " + home);
            helper.assertTrue(DutyEvents.dueHome(data, Level.OVERWORLD, false, never, now)
                            && !DutyEvents.dueHome(data, Level.OVERWORLD, true, never, now),
                    who + " was not due a visit to send it home, or was with a bed of its own in the Overworld");
        });
        helper.assertFalse(DutyEvents.dueHome(rukongai, Level.OVERWORLD, false, now - WAIT + 1, now),
                "a soul was due a visit to send it home before the wait was over");
        helper.assertTrue(DutyEvents.dueHome(rukongai, Level.OVERWORLD, false, now - WAIT, now),
                "a soul was not due a visit to send it home once the wait was over");
        for (ResourceKey<Level> world : List.of(BleachWorlds.SOUL_SOCIETY, BleachWorlds.DANGAI, Level.NETHER, Level.END,
                BleachWorlds.HUECO_MUNDO)) {
            helper.assertFalse(DutyEvents.dueHome(rukongai, world, false, never, now),
                    "a soul in " + world.location() + " was due a visit to send it home");
        }
        SoulData torn = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        torn.markSoulDestroyed();
        SoulData plus = wornPlus(CHAIN, WORN);
        Map<String, SoulData> others = Map.of("a Human", ShinigamiTests.soul(SoulState.HUMAN, 400.0F),
                "a Substitute", ShinigamiTests.substitute(200.0F), "a Plus", plus,
                "a Shinigami", ShinigamiTests.soul(SoulState.SHINIGAMI, 400.0F),
                "a Hollow", ShinigamiTests.soul(SoulState.HOLLOW, 200.0F),
                "an Arrancar", ShinigamiTests.soul(SoulState.ARRANCAR, 600.0F), "a Rukongai soul being torn apart", torn);
        others.forEach((who, data) -> helper.assertTrue(HomeWorldRespawn.passedOnHome(data) == null
                && !DutyEvents.dueHome(data, Level.OVERWORLD, false, never, now), who + " would be sent home"));
        helper.assertTrue(DutyEvents.due(plus, never, now), "a due Plus is no longer due its Konsō");

        // Each of the three switches stops it; where Konsō takes the shortcut, a Rukongai soul is sent to the Academy.
        helper.assertTrue(DutyEvents.sendsHome(), "the default config sends no soul home");
        for (ModConfigSpec.BooleanValue off : List.of(BleachConfig.KONSO_DUTY_SENDS_HOME, BleachConfig.HOME_WORLD_RESPAWN)) {
            KidoTests.withConfig(off, false, () -> helper.assertFalse(DutyEvents.sendsHome()
                            || DutyEvents.dueHome(rukongai, Level.OVERWORLD, false, never, now),
                    "a soul is sent home with " + off.getPath() + " off"));
        }
        KonsoTarget sendsTo = BleachConfig.get(BleachConfig.KONSO_SENDS_TO);
        try {
            BleachConfig.KONSO_SENDS_TO.set(KonsoTarget.NOWHERE);
            helper.assertFalse(DutyEvents.sendsHome() || DutyEvents.dueHome(rukongai, Level.OVERWORLD, false, never, now),
                    "a soul is sent home while Konsō sends souls nowhere");
            BleachConfig.KONSO_SENDS_TO.set(KonsoTarget.ACADEMY);
            helper.assertTrue(HomeWorldRespawn.passedOnHome(rukongai) == HomeWorldRespawn.Home.ACADEMY
                            && DutyEvents.dueHome(rukongai, Level.OVERWORLD, false, never, now),
                    "a Rukongai soul is not sent to the Academy where Konsō takes that shortcut");
        } finally {
            BleachConfig.KONSO_SENDS_TO.set(sendsTo);
        }
        helper.succeed();
    }

    /**
     * A real joined soul that has passed on, in the Overworld: none comes to it in creative or while its bed is there, and
     * a due Plus is still only offered Konsō. Visited, it is told which way to look and to stay or walk off, the
     * Shinigami's word to stay being for it alone; walking off in time, it is let go and the Shinigami leaves, and visited
     * again after the wait and staying, in a boat, it is sent to its Rukongai home: the same player, alive, off the boat,
     * which stays, with its things, health, bed in Soul Society and home, facing Seireitei, and told why.
     */
    @GameTest(template = "field", batch = "duty_home", skyAccess = true, timeoutTicks = 400)
    public static void aLingeringSoulIsSentHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // District 1 of the South Rukongai, outside the south gate: held first, so the send makes no ground.
        BlockPos home = new BlockPos(0, SeireiteiPlan.PLATEAU_Y, 530);
        ServerLevel soul = SiteTests.soul(helper);
        RukongaiTests.hold(soul, new ChunkPos(home));
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 8.5));
        SoulData data = ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F);
        player.setData(SoulAttachments.SOUL, data);
        player.setData(SoulAttachments.TRAVEL, TravelRecord.NONE.withRukongaiHome(home));
        BlockPos bed = helper.absolutePos(new BlockPos(1, 1, 3));
        // Out of the hand, which stays empty; below 18 food nothing heals and above 0 nothing starves.
        player.getInventory().setItem(9, new ItemStack(Items.APPLE, 3));
        player.getFoodData().setFoodLevel(17);
        player.setHealth(15.0F);
        Vec3 start = player.position();
        AABB around = new AABB(start, start).inflate(REACH).expandTowards(0.0, PERCH, 0.0);
        List<BlockPos> built = new ArrayList<>();
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, start, REACH);
        DutyShinigami[] duty = new DutyShinigami[1];
        Boat[] boat = new Boat[1];
        long[] now = new long[1];
        Runnable clear = () -> {
            QuincyArtsTests.discardAll(level.getEntitiesOfClass(DutyShinigami.class, around));
            QuincyArtsTests.discardAll(level.getEntitiesOfClass(Boat.class, around));
            built.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            built.clear();
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            forced.clear();
        };
        helper.runAtTickTime(GIVE_UP_HOME_TICK, () -> {
            clear.run();
            helper.fail("the soul that stayed was never sent home");
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(HollowBaitTests.allTicking(level, start, REACH),
                        "the ground around the soul never started ticking"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    QuincyArtsTests.discardAll(level.getEntitiesOfClass(ShinigamiNpc.class,
                            player.getBoundingBox().inflate(DutyEvents.EXCLUSION_RANGE).expandTowards(0.0, PERCH, 0.0)));
                    now[0] = level.getGameTime();
                    int lines = TestPlayers.chat(player).size();
                    player.setGameMode(GameType.CREATIVE);
                    helper.assertFalse(DutyEvents.tryVisit(player, data, now[0]), "one came to a soul in creative");
                    player.setGameMode(GameType.SURVIVAL);
                    // With its bed in the Overworld it is at home here, as death would wake it; in Soul Society, not.
                    player.setRespawnPosition(Level.OVERWORLD, bed, 0.0F, true, false);
                    helper.assertFalse(DutyEvents.tryVisit(player, data, now[0]), "one came to a soul with its bed here");
                    player.setRespawnPosition(BleachWorlds.SOUL_SOCIETY, bed, 0.0F, true, false);
                    helper.assertTrue(level.getEntitiesOfClass(DutyShinigami.class, around).isEmpty()
                                    && TestPlayers.chat(player).size() == lines
                                    && player.getData(SoulAttachments.DUTY_VISIT) == ShinigamiProgress.NEVER,
                            "a visit that was not due left an NPC, a line or a mark behind");
                    DutyShinigami forPlus = visit(helper, player, wornPlus(CHAIN, WORN), now[0], "open ground");
                    helper.assertTrue(arrivalPoint(lastLine(player), ARRIVES_KEY) != null && forPlus.charge() == null,
                            "a due Plus was told " + lastLine(player) + " by one come for " + forPlus.charge());
                    forPlus.discard();

                    DutyShinigami first = visit(helper, player, data, now[0] + WAIT, "open ground for the soul");
                    String way = ShinigamiLogic.compassKey(first.getX() - player.getX(), first.getZ() - player.getZ());
                    helper.assertTrue(way.equals(arrivalPoint(lastLine(player), DutyEvents.HOMEWARD_KEY)),
                            "the soul was told " + lastLine(player) + ", not that one came " + way + " to see it home");
                    helper.assertTrue(player.getData(SoulAttachments.DUTY_VISIT) == now[0] + WAIT
                                    && player.getUUID().equals(first.charge())
                                    && first.homeward() == DutyShinigami.HOMEWARD_TICKS,
                            "the visit was not marked, or came for " + first.charge() + " for " + first.homeward());
                    int opened = TestPlayers.dialogues(player).size();
                    helper.assertTrue(first.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                                    && TestPlayers.dialogues(player).size() == opened + 1
                                    && TestPlayers.dialogues(player).get(opened).view() == DialogueView.HOMEWARD,
                            "the soul it came for was not shown its word to stay or walk off");
                    DialogueView toOther = first.viewFor(helper.makeMockPlayer(GameType.SURVIVAL), data, now[0]);
                    helper.assertTrue(toOther == DialogueView.RESTING, "another soul that has passed on is shown " + toOther);

                    // It walks off, onto a stone at the perch's height over the far side of its ground, out of reach.
                    Vec3 away = new Vec3(start.x - first.getX(), 0.0, start.z - first.getZ()).normalize()
                            .scale(REACH_BLOCKS);
                    BlockPos off = BlockPos.containing(start.x + away.x, start.y + PERCH, start.z + away.z);
                    build(level, built, off.below());
                    helper.assertTrue(level.getBlockState(off.below()).is(Blocks.STONE), "no stone was laid at " + off);
                    player.teleportTo(off.getX() + 0.5, off.getY(), off.getZ() + 0.5);
                    helper.assertTrue(first.distanceTo(player) > DutyShinigami.HOMEWARD_REACH,
                            "the soul that walked off is " + first.distanceTo(player) + " blocks from the Shinigami");
                    first.setHomeward(SHORT_ERRAND);
                    duty[0] = first;
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(duty[0].charge() == null, "the Shinigami never ended its errand"))
                .thenExecute(HollowBaitTests.orClear(() -> helper.assertTrue(
                        duty[0].lifetime() <= DutyShinigami.LIFETIME_AFTER_KONSO,
                        "having let the soul go, the Shinigami stays " + duty[0].lifetime() + " ticks more"), clear))
                .thenWaitUntil(() -> helper.assertTrue(duty[0].isRemoved(), "the Shinigami that let the soul go never left"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    helper.assertTrue(player.level() == level && said(player, HomeWorldRespawn.SENT_HOME_KEY).isEmpty()
                                    && said(player, "message.bleach.konso.no_academy").isEmpty(),
                            "the soul that walked off was sent, or told it could not be, to " + TravelTests.where(player));
                    player.teleportTo(start.x, start.y, start.z);
                    duty[0] = visit(helper, player, data, now[0] + 2 * WAIT, "open ground again");
                    boat[0] = new Boat(level, start.x, start.y, start.z);
                    helper.assertTrue(level.addFreshEntity(boat[0]) && player.startRiding(boat[0]),
                            "the soul could not board a boat");
                    duty[0].setHomeward(SHORT_ERRAND);
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(player.level() == soul,
                        "the soul that stayed is still at " + TravelTests.where(player)))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    helper.assertTrue(soul.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                            && player.isAlive(), "the soul sent home is not the same player, alive");
                    int top = soul.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getX(), home.getZ());
                    helper.assertTrue(Math.abs(player.getX() - 0.5) < 0.01 && Math.abs(player.getZ() - 530.5) < 0.01
                                    && Math.abs(player.getY() - top) <= 1.0,
                            "the soul was sent to " + TravelTests.where(player) + ", not its home's ground at y " + top);
                    helper.assertTrue(player.getVehicle() == null && boat[0].level() == level && !boat[0].isRemoved(),
                            "the soul sent home rides " + player.getVehicle() + ", or its boat left the Overworld");
                    helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 3 && player.getHealth() == 15.0F
                                    && player.fallDistance == 0.0F,
                            "the soul sent home has " + player.getInventory().countItem(Items.APPLE) + " apples, "
                                    + player.getHealth() + " health and a fall of " + player.fallDistance);
                    helper.assertTrue(bed.equals(player.getRespawnPosition())
                                    && player.getRespawnDimension() == BleachWorlds.SOUL_SOCIETY
                                    && player.getData(SoulAttachments.TRAVEL).rukongaiHome().equals(Optional.of(home)),
                            "the soul sent home has its spawn at " + player.getRespawnPosition() + " and its home at "
                                    + player.getData(SoulAttachments.TRAVEL).rukongaiHome());
                    double along = Vec3.directionFromRotation(0.0F, player.getYRot())
                            .dot(new Vec3(-player.getX(), 0.0, -player.getZ()).normalize());
                    helper.assertTrue(along > 0.99, "the soul sent home faces " + player.getYRot() + ", not Seireitei");
                    List<Component> told = said(player, HomeWorldRespawn.SENT_HOME_KEY);
                    helper.assertTrue(told.size() == 1, "the soul sent home was told so " + told.size() + " times");
                    RukongaiTests.assertKey(helper, told.get(0), HomeWorldRespawn.SENT_HOME_KEY, Rukongai.placeName(0, 530));
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(duty[0].isRemoved(), "the Shinigami that sent the soul never left"))
                .thenExecute(clear)
                .thenSucceed();
    }

    /**
     * A student the Shinigami on duty came for is let go, and told nothing, when the server stops sending souls home
     * before its half minute is up, or when its field trial has begun. Come for again and still near when it is, it is
     * sent to the gates of the Academy, the same player, alive and still a student, facing the hall, and told why; and
     * none comes for it there.
     */
    @GameTest(template = "empty", batch = "duty_academy", timeoutTicks = 500)
    public static void aLingeringStudentIsSentToTheAcademy(GameTestHelper helper) {
        ServerLevel soul = SiteTests.holdAcademy(helper);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(2.5, 1.0, 1.5));
        SoulData data = ShinigamiTests.student(60.0F);
        player.setData(SoulAttachments.SOUL, data);
        DutyShinigami duty = helper.spawn(ModEntities.DUTY_SHINIGAMI.get(), 1, 1, 1);
        Runnable clear = () -> duty.discard();
        int[] lines = new int[1];
        helper.runAtTickTime(GIVE_UP_ACADEMY_TICK, () -> {
            clear.run();
            helper.fail("the student that stayed was never sent to the Academy");
        });
        helper.startSequence()
                .thenWaitUntil(() -> AcademyTests.assertAcademyStands(helper, soul))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    duty.sendHomeLater(player);
                    DialogueView shown = duty.viewFor(player, data, helper.getLevel().getGameTime());
                    helper.assertTrue(shown == DialogueView.HOMEWARD, "the student it came for is shown " + shown);
                    lines[0] = TestPlayers.chat(player).size();
                    KidoTests.withConfig(BleachConfig.KONSO_DUTY_SENDS_HOME, false, duty::endErrand);
                    helper.assertTrue(duty.charge() == null, "the errand did not end");
                    // Its trial begun, it is let go too; forgotten at once, as the errand's end queued no move.
                    duty.sendHomeLater(player);
                    FieldTrialEvents.begin(player, helper.getLevel().getGameTime());
                    duty.endErrand();
                    FieldTrialEvents.forget(player);
                    lines[0] = TestPlayers.chat(player).size();
                }, clear))
                // Past the ticks a task between ticks may wait, it is still here and was told nothing; then came for again.
                .thenIdle(5)
                .thenExecute(HollowBaitTests.orClear(() -> {
                    helper.assertTrue(player.level() == helper.getLevel() && TestPlayers.chat(player).size() == lines[0],
                            "with the send-home off, the student went to " + TravelTests.where(player) + " or was told "
                                    + lastLine(player));
                    duty.sendHomeLater(player);
                    duty.setHomeward(SHORT_ERRAND);
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(player.level() == soul,
                        "the student that stayed is still at " + TravelTests.where(player)))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    helper.assertTrue(soul.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                            && player.isAlive(), "the student sent home is not the same player, alive");
                    helper.assertTrue(player.position().distanceTo(AcademyLocator.LANDING) < 0.01
                                    && Math.abs(Mth.wrapDegrees(player.getYRot() - AcademyLocator.LANDING_YAW)) < 0.01,
                            "the student was sent to " + TravelTests.where(player) + " facing " + player.getYRot());
                    helper.assertTrue(ShinigamiLogic.isStudent(SoulLogic.get(player))
                                    && said(player, HomeWorldRespawn.SENT_HOME_ACADEMY_KEY).size() == 1,
                            "the student sent to the Academy is no longer one, or was told " + lastLine(player));
                    int told = TestPlayers.chat(player).size();
                    helper.assertFalse(DutyEvents.tryVisit(player, data, helper.getLevel().getGameTime() + WAIT),
                            "one came for the student in Soul Society");
                    helper.assertTrue(TestPlayers.chat(player).size() == told,
                            "the student in Soul Society was told " + lastLine(player));
                }, clear))
                .thenWaitUntil(() -> helper.assertTrue(duty.isRemoved(), "the Shinigami that sent the student never left"))
                .thenSucceed();
    }
}
