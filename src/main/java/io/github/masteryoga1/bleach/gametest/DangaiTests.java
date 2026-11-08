package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.soul.BakudoEffect;
import io.github.masteryoga1.bleach.soul.ChainEvents;
import io.github.masteryoga1.bleach.soul.DangaiEvents;
import io.github.masteryoga1.bleach.soul.SafeLanding;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelRecord;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.DangaiPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Dangai: its lanes as the plan lays them and the real world carved to it, the Kōryū's hold, the lane's
 * end into either world, and a login mid-crossing. Tests 1, 2, 5 and 6 are here; 3 and 4 come with the Kōtotsu.
 * The Dangai is shared by every test, so the tests that set souls in it run in their own batch, each on its own lanes.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class DangaiTests {
    /** Where a test's soul joins, before it is sent to the Dangai. */
    private static final Vec3 SPOT = new Vec3(1.5, 1.0, 0.5);
    /** How near a spot worked out from the same numbers must be. */
    private static final double SAME = 1.0E-6;
    private static final String ENTERED_KEY = "message.bleach.dangai.entered";
    private static final String HELD_KEY = "message.bleach.dangai.held";
    private static final ResourceKey<Biome> DANGAI_BIOME = ResourceKey.create(Registries.BIOME, BleachMod.id("dangai"));

    private DangaiTests() {
    }

    // ---- Shared helpers ----

    /** The test server's own Dangai. */
    private static ServerLevel dangai(GameTestHelper helper) {
        ServerLevel dangai = helper.getLevel().getServer().getLevel(BleachWorlds.DANGAI);
        helper.assertTrue(dangai != null, "the test server has no Dangai: the mixin did not add it");
        return dangai;
    }

    /**
     * The real Dangai, the 5×5 chunks round this chunk of a lane loaded now and held by a forced ticket, so a soul set
     * there has its floor at once and what stands there ticks at once. Never released, as the Senkaimon court's in
     * {@link TravelTests} is not.
     */
    private static ServerLevel holdLane(GameTestHelper helper, int lane, int chunkZ) {
        ServerLevel dangai = dangai(helper);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                dangai.getChunk(lane + x, chunkZ + z);
            }
        }
        ChunkPos held = new ChunkPos(lane, chunkZ);
        dangai.getChunkSource().addRegionTicket(TicketType.FORCED, held, 2, held);
        return dangai;
    }

    /** The landing of this soul's pearl, thrown in this world, as NeoForge asks about it. */
    private static EntityTeleportEvent.EnderPearl pearlFor(ServerPlayer owner, ServerLevel level) {
        ThrownEnderpearl pearl = new ThrownEnderpearl(level, owner);
        Vec3 at = pearl.position();
        return new EntityTeleportEvent.EnderPearl(owner, at.x, at.y, at.z, pearl, 5.0F,
                BlockHitResult.miss(at, Direction.UP, BlockPos.containing(at)));
    }

    /**
     * A real player with this soul and travel record, joined at {@link #SPOT} and only then set in this lane at this z,
     * facing down it, so its first tick there already has its record. Coming in from the test's world clears nothing.
     */
    private static ServerPlayer inLane(GameTestHelper helper, SoulData data, int lane, double z, TravelRecord record) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, SPOT);
        player.setData(SoulAttachments.SOUL, data);
        player.setData(SoulAttachments.TRAVEL, record);
        player.teleportTo(dangai(helper), DangaiPlan.laneX(lane), DangaiPlan.FLOOR_Y, z, 0.0F, 0.0F);
        return player;
    }

    /** A record of nothing but this crossing. */
    private static TravelRecord crossing(ResourceKey<Level> to, int lane, float bearing) {
        return TravelRecord.NONE.withCrossing(Optional.of(new TravelRecord.Crossing(to, lane, bearing)));
    }

    /** A mock player with this soul at {@link #SPOT}. */
    private static Player mock(GameTestHelper helper, GameType mode, SoulData data) {
        Player player = helper.makeMockPlayer(mode);
        player.setData(SoulAttachments.SOUL, data);
        Vec3 at = helper.absoluteVec(SPOT);
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** A Shinigami full of 800 reiryoku. */
    private static SoulData shinigami() {
        return ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F);
    }

    /** Whether this player has earned the advancement {@code bleach:<name>}. */
    private static boolean earned(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.server.getAdvancements().get(BleachMod.id(name));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** The ticks of Slow Falling this player has left, or 0 without it. */
    private static int slowFalling(Player player) {
        MobEffectInstance effect = player.getEffect(MobEffects.SLOW_FALLING);
        return effect == null ? 0 : effect.getDuration();
    }

    /** Asserts what the plan lays at this block. */
    private static void assertCell(GameTestHelper helper, int x, int y, int z, DangaiPlan.Cell expected) {
        DangaiPlan.Cell cell = DangaiPlan.cell(x, y, z);
        if (cell != expected) {
            helper.fail("the plan lays " + cell + " at (" + x + ", " + y + ", " + z + "), not " + expected);
        }
    }

    // ---- Test 1: the lanes ----

    /**
     * Test 1. The plan lays each lane 8 wide and 8 tall on a floor 3 deep, walled by the Kōryū and capped by rock, with
     * nothing but rock outside lanes 0 to 255, and the real Dangai is carved block for block as it says. Its world has
     * no sky, a ceiling, 32 blocks of height, the Nether's look and its own biome; beds and anchors set no spawn there.
     */
    @GameTest(template = "empty")
    public static void eachLaneIsLaidAsThePlanSays(GameTestHelper helper) {
        helper.assertTrue(DangaiPlan.laneX(0) == 8 && DangaiPlan.laneX(255) == 4088, "lanes 0 and 255 run down x "
                + DangaiPlan.laneX(0) + " and " + DangaiPlan.laneX(255) + ", not 8 and 4088");
        helper.assertTrue(DangaiPlan.entry(7).equals(new Vec3(120.0, 3.0, 0.5)), "lane 7 starts at " + DangaiPlan.entry(7)
                + ", not (120, 3, 0.5)");
        for (int lane : new int[] {0, 7, 255}) {
            int x0 = 16 * lane;
            assertCell(helper, x0 + 3, 3, 0, DangaiPlan.Cell.KORYU);
            assertCell(helper, x0 + 12, 10, 167, DangaiPlan.Cell.KORYU);
            assertCell(helper, x0 + 8, 3, 0, DangaiPlan.Cell.AIR);
            assertCell(helper, x0 + 7, 10, 167, DangaiPlan.Cell.AIR);
            assertCell(helper, x0 + 8, 6, 80, DangaiPlan.Cell.AIR);
            // Beside the walls, the floor, the ceiling, behind the start and the cap past the end.
            assertCell(helper, x0 + 2, 6, 80, DangaiPlan.Cell.ROCK);
            assertCell(helper, x0 + 13, 6, 80, DangaiPlan.Cell.ROCK);
            assertCell(helper, x0 + 8, 2, 80, DangaiPlan.Cell.ROCK);
            assertCell(helper, x0 + 8, 11, 80, DangaiPlan.Cell.ROCK);
            assertCell(helper, x0 + 8, 6, -1, DangaiPlan.Cell.ROCK);
            assertCell(helper, x0 + 8, 6, 168, DangaiPlan.Cell.ROCK);
        }
        assertCell(helper, -8, 6, 10, DangaiPlan.Cell.ROCK);
        assertCell(helper, 4104, 6, 10, DangaiPlan.Cell.ROCK);
        // No bulges: every lane is the same straight corridor all its length.
        for (int lane = 0; lane < 256; lane++) {
            for (int y = 3; y <= 10; y++) {
                for (int z = 0; z <= 167; z++) {
                    for (int local = 3; local <= 12; local++) {
                        assertCell(helper, 16 * lane + local, y, z,
                                local == 3 || local == 12 ? DangaiPlan.Cell.KORYU : DangaiPlan.Cell.AIR);
                    }
                }
            }
        }

        ServerLevel dangai = dangai(helper);
        Block koryu = ModBlocks.KORYU.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : List.of(new ChunkPos(200, 0), new ChunkPos(200, 10))) {
            dangai.getChunk(chunk.x, chunk.z);
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int y = 0; y <= 31; y++) {
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                        DangaiPlan.Cell cell = DangaiPlan.cell(x, y, z);
                        BlockState state = dangai.getBlockState(pos.set(x, y, z));
                        boolean laid = switch (cell) {
                            case KORYU -> state.is(koryu);
                            case AIR -> state.isAir();
                            case ROCK -> state.is(Blocks.BEDROCK);
                        };
                        if (!laid) {
                            helper.fail("the Dangai holds " + state + " at (" + x + ", " + y + ", " + z + "), where the "
                                    + "plan lays " + cell);
                        }
                    }
                }
            }
        }

        DimensionType type = dangai.dimensionType();
        helper.assertTrue(!type.hasSkyLight() && type.hasCeiling() && type.minY() == 0 && type.height() == 32,
                "the Dangai's sky, ceiling and height are " + type);
        helper.assertTrue(!type.natural() && type.bedWorks() && !type.respawnAnchorWorks(),
                "a bed or an anchor would blow up or set a spawn in the Dangai: " + type);
        helper.assertTrue(type.effectsLocation().equals(BuiltinDimensionTypes.NETHER_EFFECTS),
                "the Dangai looks like " + type.effectsLocation() + ", not the Nether");
        Holder<Biome> biome = dangai.getBiome(new BlockPos(3208, 6, 8));
        helper.assertTrue(biome.is(DANGAI_BIOME), "the Dangai's lanes lie in " + biome.unwrapKey() + ", not bleach:dangai");
        helper.succeed();
    }

    // ---- Test 2: the Kōryū ----

    /**
     * Test 2. The Kōryū is passable, unbreakable, solid to fluids and never an item. It holds an aware soul for 1½
     * seconds with Hainawa's own effect, glowing in its purple, and cannot take it again until 2 seconds after it lets
     * go; the unaware and creative players pass. A real Shinigami standing in it is held from its first tick there.
     */
    @GameTest(template = "empty")
    public static void theKoryuHoldsTheAwareThenLetsGo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(1, 1, 1, ModBlocks.KORYU.get());
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState koryu = level.getBlockState(at);
        helper.assertTrue(koryu.is(ModBlocks.KORYU.get()) && koryu.getCollisionShape(level, at).isEmpty(),
                "the Kōryū stops a body: its collision shape is " + koryu.getCollisionShape(level, at));
        helper.assertTrue(koryu.getDestroySpeed(level, at) < 0.0F, "the Kōryū breaks in " + koryu.getDestroySpeed(level, at));
        helper.assertTrue(koryu.isSolid(), "water or lava could flow into the Kōryū");
        helper.assertTrue(ModBlocks.KORYU.get().asItem() == Items.AIR, "the Kōryū can be held as an item");

        long t = HollowPlayerTests.now(helper);
        Player soul = mock(helper, GameType.SURVIVAL, shinigami());
        helper.assertTrue(DangaiEvents.touch(soul, t), "the Kōryū did not take hold of an aware Shinigami");
        MobEffectInstance hold = soul.getEffect(ModEffects.BAKUDO_HELD);
        helper.assertTrue(BakudoEffect.held(soul) && hold != null && hold.getDuration() == 30,
                "the Kōryū's hold is " + hold + ", not Hainawa's for 30 ticks");
        BakudoEffect.Drawn drawn = BakudoEffect.drawn(soul, true);
        helper.assertTrue(drawn.equals(new BakudoEffect.Drawn(KidoSpell.BindStyle.GLOW, DangaiEvents.KORYU_COLOUR)),
                "the held soul is drawn as " + drawn.style() + " in " + Integer.toHexString(drawn.colour())
                        + ", not as a glow in the Kōryū's purple");
        // Held to t + 30, then guarded for 40 more.
        helper.assertFalse(DangaiEvents.touch(soul, t + 10L), "the Kōryū took hold again 10 ticks into its hold");
        helper.assertFalse(DangaiEvents.touch(soul, t + 69L), "the Kōryū took hold again 39 ticks after it let go");
        helper.assertTrue(DangaiEvents.touch(soul, t + 70L), "the Kōryū did not take hold again 40 ticks after it let go");
        // The second hold runs to t + 100, its guard to t + 140.
        helper.assertFalse(DangaiEvents.touch(soul, t + 139L), "the Kōryū took hold a third time 39 ticks after its second");
        helper.assertTrue(DangaiEvents.touch(soul, t + 140L), "the Kōryū never took hold a third time");

        Player unaware = mock(helper, GameType.SURVIVAL, ShinigamiTests.soul(SoulState.HUMAN, 0.0F));
        helper.assertFalse(DangaiEvents.touch(unaware, t) || BakudoEffect.held(unaware),
                "the Kōryū took hold of a Human with no reiryoku");
        Player builder = mock(helper, GameType.CREATIVE, shinigami());
        helper.assertFalse(DangaiEvents.touch(builder, t) || BakudoEffect.held(builder),
                "the Kōryū took hold of a creative Shinigami");

        // Its block's tick, through a real player's own move: the guard keeps a second hold off until about tick 71.
        helper.setBlock(1, 0, 1, Blocks.STONE);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, shinigami());
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(BakudoEffect.held(player), "the Kōryū never held the Shinigami standing in it, at "
                    + TravelTests.where(player));
            helper.assertTrue(TravelTests.lines(player, HELD_KEY) == 1L, "the held Shinigami was told "
                    + TravelTests.said(player));
            helper.succeed();
        });
    }

    // ---- Test 5: the lane's end ----

    /**
     * Test 5. A soul past the lane's end comes out at once: over a Rukongai district on its bearing when it crosses to
     * Soul Society, over the block it left from when it crosses home, taken off what it rode, either way 24 blocks up and
     * falling slowly, its crossing done and counted. Open to the sky, so no barrier roofs the way home.
     */
    @GameTest(template = "empty", batch = "dangai", skyAccess = true, timeoutTicks = 200)
    public static void theLanesEndLetsOutIntoEitherWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel soulSociety = level.getServer().getLevel(BleachWorlds.SOUL_SOCIETY);
        helper.assertTrue(soulSociety != null, "the test server has no Soul Society");
        holdLane(helper, 251, 10);
        // Due south of the centre, so it comes out on the z axis.
        ServerPlayer south = inLane(helper, shinigami(), 251, 160.5,
                crossing(BleachWorlds.SOUL_SOCIETY, 251, (float) (Math.PI / 2.0)));
        ServerLevel dangai = holdLane(helper, 252, 10);

        // The way home rises only as far as the body has clean room: the lane's roof, or lava below it, keeps it lower.
        holdLane(helper, 250, 2);
        Vec3 floor = new Vec3(DangaiPlan.laneX(250), DangaiPlan.FLOOR_Y, 40.5);
        BlockPos lava = BlockPos.containing(floor).above(4);
        double open = SafeLanding.risen(south, dangai, floor, 24).y - floor.y;
        dangai.setBlock(lava, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
        double underLava = SafeLanding.risen(south, dangai, floor, 24).y - floor.y;
        dangai.setBlock(lava, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(open == DangaiPlan.LANE_HEIGHT - 2 && underLava == 2.0, "a spot on a lane's floor rose "
                + open + " blocks under its roof and " + underLava + " under lava 4 up, not "
                + (DangaiPlan.LANE_HEIGHT - 2) + " and 2");

        helper.setBlock(1, 0, 1, Blocks.STONE);
        BlockPos left = helper.absolutePos(new BlockPos(1, 1, 1));
        TravelRecord home = crossing(Level.OVERWORLD, 252, 0.0F).withHumanWorldReturn(GlobalPos.of(Level.OVERWORLD, left));
        ServerPlayer rider = inLane(helper, shinigami(), 252, 160.5, home);
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, dangai);
        List<Entity> spawned = new ArrayList<>(List.of(stand));
        TravelTests.step(spawned, () -> {
            stand.setNoGravity(true);
            stand.moveTo(rider.getX(), rider.getY(), rider.getZ(), 0.0F, 0.0F);
            helper.assertTrue(dangai.addFreshEntity(stand), "the armour stand would not stand in the Dangai");
            helper.assertTrue(rider.startRiding(stand, true), "the Shinigami would not ride the armour stand");
        }).run();
        // Each comes out on its first tick there. Nothing in the way out waits on game time, and a stall making Soul
        // Society's column stops the clock with it, so tick 20 leaves 19 ticks of slack.
        helper.runAtTickTime(20, TravelTests.step(spawned, () -> {
            helper.assertTrue(south.level() == soulSociety, "the soul crossing to Soul Society is at "
                    + TravelTests.where(south));
            int district = (int) Math.round((south.getZ() - 504.0) / 32.0);
            helper.assertTrue(Math.abs(south.getX()) < 0.01 && district >= 1 && district <= 10
                            && Math.abs(south.getZ() - (504.0 + 32.0 * district)) < 1.0E-3,
                    "the lane's end let the soul out at " + south.position() + ", not over the middle of one of the "
                            + "first ten districts due south of the centre");
            // The column the way out read, as it reads it.
            int top = soulSociety.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(south.getX()),
                    Mth.floor(south.getZ()));
            assertFallingFrom(helper, south, top, "the soul over the Rukongai");
            helper.assertFalse(south.hasEffect(MobEffects.CONFUSION), "the soul over the Rukongai came out dizzy");

            Vec3 above = Vec3.atBottomCenterOf(left);
            helper.assertTrue(rider.level() == level && Math.hypot(rider.getX() - above.x, rider.getZ() - above.z) < SAME,
                    "the soul crossing home is at " + TravelTests.where(rider) + ", not over where it left, " + above);
            assertFallingFrom(helper, rider, left.getY(), "the soul over its way home");
            helper.assertTrue(!rider.isPassenger() && stand.level() == dangai && !stand.isRemoved()
                            && stand.getPassengers().isEmpty(), "the rider came out riding (" + rider.isPassenger()
                            + "), or its armour stand did not stay behind in the Dangai, empty: " + stand.level().dimension()
                            .location() + ", removed " + stand.isRemoved() + ", carrying " + stand.getPassengers());
            TravelTests.discard(spawned.toArray(new Entity[0]));
            helper.succeed();
        }));
    }

    /**
     * Asserts this joined player came out of the Dangai over this ground: from 22 to 24 blocks above it and not yet
     * down (19 ticks of Slow Falling drop a body under 1.7 blocks), falling slowly, its crossing done and counted.
     */
    private static void assertFallingFrom(GameTestHelper helper, ServerPlayer player, int ground, String what) {
        helper.assertTrue(player.getY() >= ground + 22.0 && player.getY() <= ground + 24.001 && !player.onGround(),
                what + " is at y " + player.getY() + (player.onGround() ? ", on the ground," : "") + " over ground at "
                        + ground + ", not falling from 24 blocks above it");
        helper.assertTrue(slowFalling(player) >= 160 && slowFalling(player) <= 199, what + " has " + slowFalling(player)
                + " ticks of Slow Falling left, not 160 to 199");
        TravelRecord record = player.getData(SoulAttachments.TRAVEL);
        helper.assertTrue(record.crossing().isEmpty(), what + " is still on a crossing: " + record);
        helper.assertTrue(earned(player, "dangai"), what + " has not earned Crossed the Dangai");
    }

    // ---- Test 6: a login, and no crossing ----

    /**
     * Test 6. A soul that logs in mid-crossing is set at its lane's start again, facing down it, still on the same
     * crossing and told it is in the Dangai; a soul found in the Dangai with no crossing is put out at the Human World's
     * spawn. A crossing saved and read back is the same, and one saved without a field reads as none.
     */
    @GameTest(template = "empty", batch = "dangai", timeoutTicks = 200)
    public static void aLoginMidCrossingStartsTheLaneAgain(GameTestHelper helper) {
        holdLane(helper, 253, 5);
        ServerLevel dangai = holdLane(helper, 253, 0);
        TravelRecord.Crossing crossing = new TravelRecord.Crossing(BleachWorlds.SOUL_SOCIETY, 253, 0.5F);
        ServerPlayer midway = inLane(helper, shinigami(), 253, 90.5, TravelRecord.NONE.withCrossing(Optional.of(crossing)));
        // Turned round in the lane, as a soul that looked back would be.
        midway.setYRot(90.0F);
        DangaiEvents.atLogin(midway);
        Vec3 start = DangaiPlan.entry(253);
        helper.assertTrue(midway.level() == dangai && midway.position().distanceTo(start) < SAME
                        && midway.getYRot() == 0.0F, "after its login the soul midway down lane 253 is at "
                        + TravelTests.where(midway) + " facing " + midway.getYRot() + ", not at its start, " + start
                        + ", facing down it");
        TravelRecord.Crossing kept = midway.getData(SoulAttachments.TRAVEL).crossing().orElse(null);
        // Field by field, so a field a later step adds to the crossing changes nothing here.
        helper.assertTrue(kept != null && kept.to() == crossing.to() && kept.lane() == crossing.lane()
                        && kept.bearing() == crossing.bearing(),
                "after its login the soul's crossing is " + kept + ", not " + crossing);
        // The teleport into the lane shows no line; only the login's fresh start does.
        helper.assertTrue(TravelTests.lines(midway, ENTERED_KEY) == 1L, "after its login the soul was told "
                + TravelTests.said(midway));

        holdLane(helper, 254, 3);
        ServerPlayer lost = inLane(helper, shinigami(), 254, 50.5, TravelRecord.NONE);

        TravelRecord saved = new TravelRecord(Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, -20))), 7L,
                Optional.of(crossing));
        Tag tag = TravelRecord.CODEC.encodeStart(NbtOps.INSTANCE, saved).getOrThrow();
        TravelRecord read = TravelRecord.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        helper.assertTrue(read.equals(saved), "saved " + saved + " but read " + read);
        CompoundTag broken = tag instanceof CompoundTag whole ? whole.copy() : new CompoundTag();
        CompoundTag partial = broken.getCompound("crossing");
        helper.assertTrue(partial.contains("bearing"), "a crossing is saved as " + partial + ", with no bearing");
        partial.remove("bearing");
        broken.put("crossing", partial);
        TravelRecord lenient = TravelRecord.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
        helper.assertTrue(lenient.crossing().isEmpty() && lenient.humanWorldReturn().equals(saved.humanWorldReturn())
                        && lenient.butterflyDay() == saved.butterflyDay(),
                "a crossing saved without its bearing read as " + lenient + ", not as no crossing beside the rest");

        // Put out on its first tick there, somewhere within the spawn radius of the shared spawn.
        helper.runAtTickTime(20, () -> {
            ServerLevel overworld = helper.getLevel().getServer().overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            int radius = overworld.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS);
            double across = Math.max(Math.abs(lost.getX() - spawn.getX()), Math.abs(lost.getZ() - spawn.getZ()));
            helper.assertTrue(lost.level() == overworld && across <= radius + 1.0, "the soul with no crossing is at "
                    + TravelTests.where(lost) + ", not within " + (radius + 1) + " blocks of the Human World's spawn, "
                    + spawn.toShortString());
            helper.assertTrue(lost.getData(SoulAttachments.TRAVEL).crossing().isEmpty(), "the soul put out is on a "
                    + "crossing: " + lost.getData(SoulAttachments.TRAVEL));

            // Out of the Dangai, it is not taken back in: not as a Plus to where it fell, nor by a pearl left there.
            GlobalPos fell = GlobalPos.of(BleachWorlds.DANGAI, BlockPos.containing(DangaiPlan.laneX(254),
                    DangaiPlan.FLOOR_Y, 80.5));
            helper.assertFalse(ChainEvents.returnToDeathPoint(lost, fell, null) || lost.level() != overworld,
                    "a Plus that died in the Dangai went back there, to " + TravelTests.where(lost));
            EntityTeleportEvent.EnderPearl left = NeoForge.EVENT_BUS.post(pearlFor(lost, dangai(helper)));
            EntityTeleportEvent.EnderPearl here = NeoForge.EVENT_BUS.post(pearlFor(lost, overworld));
            helper.assertTrue(left.isCanceled() && !here.isCanceled(), "a pearl left in the Dangai "
                    + (left.isCanceled() ? "was stopped" : "pulled the soul back") + ", and one in the Human World "
                    + (here.isCanceled() ? "was stopped too" : "was let land"));
            helper.succeed();
        });
    }
}
