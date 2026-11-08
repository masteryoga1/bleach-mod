package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.DutyShinigami;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.item.ShihakushoItem;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.DutyEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Shinigami on duty: when a Plus is due one and what it is told when none comes, real arrivals for a
 * joined Plus, and the NPC itself, which sends a Plus on and leaves. The arrivals run in their own batch, so no other
 * test's Shinigami NPC stands within the 128 blocks that keep one away. The config's defaults are assumed.
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
    /** How far above the Plus the perch stands: more than 16 blocks over any ground about, the test's own roof too. */
    private static final int PERCH = 40;
    /** How far above the Plus's feet the roof is laid, and how far out each way: its top is within 16 of the Plus. */
    private static final int ROOF = 10;
    private static final int REACH_BLOCKS = 16;
    /** The tick by which the arrivals must be done; past it, everything is cleared away and the test fails. */
    private static final int GIVE_UP_TICK = 290;
    /** The tick by which the NPC must have left; past it, it is taken away and the test fails. */
    private static final int GIVE_UP_LEAVING_TICK = 190;

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

    /** The compass point an arrival line names, as its lang key, or null if the line is no arrival. */
    @Nullable
    private static String arrivalPoint(@Nullable Component line) {
        if (line != null && line.getContents() instanceof TranslatableContents told
                && told.getKey().equals("message.bleach.duty_arrives") && told.getArgs().length == 1
                && told.getArgs()[0] instanceof Component point
                && point.getContents() instanceof TranslatableContents compass) {
            return compass.getKey();
        }
        return null;
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

        // In the Overworld none could reach it, in another world none comes, and in Soul Society nothing is said.
        helper.assertTrue(DutyEvents.UNREACHABLE_KEY.equals(DutyEvents.unreachedKey(Level.OVERWORLD)),
                "a Plus in the Overworld would be told " + DutyEvents.unreachedKey(Level.OVERWORLD));
        for (ResourceKey<Level> world : List.of(Level.NETHER, Level.END, BleachWorlds.DANGAI, BleachWorlds.HUECO_MUNDO)) {
            helper.assertTrue(DutyEvents.ELSEWHERE_KEY.equals(DutyEvents.unreachedKey(world)),
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
    @GameTest(template = "field", batch = "duty", timeoutTicks = 300)
    public static void dutyShinigamiComesDownNearAPlus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer plus = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 1.0, 8.5));
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
                    helper.assertTrue(way.equals(arrivalPoint(told)),
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

                    // Under a roof, as in a shallow cave: on the floor about the Plus, not on the roof's top 11 blocks up.
                    top.discard();
                    built.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
                    built.clear();
                    plus.teleportTo(start.x, start.y, start.z);
                    BlockPos feet = plus.blockPosition();
                    for (int dx = -REACH_BLOCKS; dx <= REACH_BLOCKS; dx++) {
                        for (int dz = -REACH_BLOCKS; dz <= REACH_BLOCKS; dz++) {
                            build(level, built, feet.offset(dx, ROOF, dz));
                        }
                    }
                    DutyShinigami below = visit(helper, plus, data, now + 3 * WAIT + 2, "the floor under a roof");
                    double under = horizontal(below, plus);
                    // The cleared floor beyond the template's edge lies 2 lower; the roof's top would be 11 up.
                    helper.assertTrue(under >= 2.99 && under <= 10.01 && Math.abs(below.getY() - plus.getY()) <= 4.01,
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
}
