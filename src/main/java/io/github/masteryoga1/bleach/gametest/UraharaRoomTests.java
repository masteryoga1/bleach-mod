package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.UraharaRoom;
import io.github.masteryoga1.bleach.world.UraharaShop;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The training room under the Urahara Shop: its data and template, laid under the shop at every turn; built once under a
 * shop with its hatch and ladder leading down and back; given up where players have built, below the world's floor, in
 * bedrock or under thin land; and laid over a wet site holding no water or lava. Each test takes away what it set down.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class UraharaRoomTests {
    /**
     * How far a test lifts a shop and its room: the flat test world's floor is 3 blocks over its bottom, so a room 22 under
     * a shop there would leave the world.
     */
    static final int LIFT = 40;

    private UraharaRoomTests() {
    }

    /**
     * Test 1. The room's structure and pool load, no set lays it, and its template lists exactly the room's rock and air,
     * its shaft and the hatch; at a chunk of each turn it lies under the shop laid out there, 22 below its floor.
     */
    @GameTest(template = "empty")
    public static void theRoomsDataAndTemplateLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        Structure room = access.registryOrThrow(Registries.STRUCTURE).get(UraharaRoom.STRUCTURE);
        helper.assertTrue(room != null && room.terrainAdaptation() == TerrainAdjustment.NONE
                && room.step() == GenerationStep.Decoration.UNDERGROUND_STRUCTURES
                && access.registryOrThrow(Registries.TEMPLATE_POOL).containsKey(BleachMod.id("urahara_training_room/start")),
                "the room's structure or pool did not load as written");
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE_SET).stream().noneMatch(set -> set.structures()
                .stream().anyMatch(entry -> entry.structure().is(UraharaRoom.STRUCTURE))), "a structure set lays the room");
        StructureTemplate template = level.getStructureManager().get(UraharaRoom.TEMPLATE).orElse(null);
        helper.assertTrue(template != null && template.getSize().equals(new Vec3i(33, 23, 32)),
                "the room's template did not load at 33 by 23 by 32");
        // Laid at the shop's corner, unturned, the template's places are the shop's own.
        Map<BlockPos, BlockState> listed = listed(template, UraharaRoom.ROOM_MIN, Rotation.NONE);
        List<BlockPos> places = new ArrayList<>();
        for (BoundingBox box : List.of(BoundingBox.fromCorners(UraharaRoom.ROOM_MIN, UraharaRoom.ROOM_MAX),
                BoundingBox.fromCorners(UraharaRoom.SHAFT_MIN, UraharaRoom.SHAFT_MAX))) {
            BlockPos.betweenClosedStream(box).forEach(pos -> places.add(pos.immutable()));
        }
        places.add(UraharaRoom.HATCH);
        helper.assertTrue(listed.size() == 14_857 && listed.keySet().containsAll(places),
                "the template lists " + listed.size() + " places, not the room's rock and air, its shaft and the hatch");
        BlockState hatch = listed.get(UraharaRoom.HATCH);
        helper.assertTrue(hatch.is(Blocks.SPRUCE_TRAPDOOR) && hatch.getValue(TrapDoorBlock.FACING) == Direction.EAST
                && !hatch.getValue(TrapDoorBlock.OPEN), "the hatch is " + hatch);
        for (int y = -21; y < 0; y++) {
            BlockState ladder = listed.get(UraharaRoom.HATCH.atY(y));
            helper.assertTrue(ladder.is(Blocks.LADDER) && ladder.getValue(LadderBlock.FACING) == Direction.EAST
                    && listed.get(UraharaRoom.HATCH.atY(y).west()).isSolid(), "the ladder at y " + y + " is " + ladder);
        }
        // The first chunk of each turn east of (300, 460), laid out only.
        Map<Rotation, ChunkPos> turns = new EnumMap<>(Rotation.class);
        for (int x = 300; x < 364 && turns.size() < 4; x += 2) {
            ChunkPos chunk = new ChunkPos(x, 460);
            turns.putIfAbsent(UraharaShopTests.startAt(level, chunk).getPieces().get(0).getRotation(), chunk);
        }
        helper.assertTrue(turns.size() == 4, "the shops east of chunk (300, 460) turn only " + turns.keySet());
        turns.forEach((turn, chunk) -> {
            PoolElementStructurePiece shop = (PoolElementStructurePiece) UraharaShopTests.startAt(level, chunk).getPieces()
                    .get(0);
            StructureStart laid = UraharaRoom.startAt(level, chunk);
            helper.assertTrue(laid != null, "the room lays out nothing in chunk " + chunk);
            PoolElementStructurePiece piece = (PoolElementStructurePiece) laid.getPieces().get(0);
            BlockPos corner = shop.getPosition();
            helper.assertTrue(piece.getRotation() == turn && piece.getPosition().equals(corner.offset(
                    UraharaRoom.ROOM_MIN.rotate(turn))) && piece.getBoundingBox().getCenter().getX() == shop
                    .getBoundingBox().getCenter().getX() && piece.getBoundingBox().getCenter().getZ() == shop
                    .getBoundingBox().getCenter().getZ(), "turned " + turn + ", the room at " + piece.getPosition()
                    + " is not under the shop at " + corner);
        });
        helper.succeed();
    }

    /**
     * Test 2. Under a shop laid out as worldgen lays it, its 25 chunks loaded, the room waits while no shop stands, then is
     * built once: every block as its template lists it and the rest of its box kept, a pig in its rock set down before the
     * shop's door, the way over the counter, the open hatch and the ladder climbable, the keeper and sign as they were;
     * asked again, or by a mark finding it standing, nothing more is built.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aShopGainsItsRoomOnce(GameTestHelper helper) {
        Room room = roomAt(helper, 300, 420, LIFT, true);
        ServerLevel level = room.level();
        Asked asked = asked(room, () -> false);
        UraharaRoom.Stamp stamp = asked.stamp();
        helper.startSequence()
                .thenWaitUntil(() -> UraharaShopTests.entitiesLoaded(helper, level, room.box()))
                .thenExecute(() -> {
                    helper.assertTrue(ChunkPos.rangeClosed(new ChunkPos(stamp.room().getCenter()), 2).allMatch(pos -> level
                            .getChunkSource().getChunkNow(pos.x, pos.z) != null), "the room's 25 chunks are not all loaded");
                    Map<BlockPos, BlockState> bare = UraharaShopTests.blocks(level, room.box());
                    stamp.ensureBuiltIfLoaded(level, true);
                    helper.assertTrue(!asked.mark().decided() && stamp.triesLeft() > 0 && asked.warned().isEmpty()
                            && UraharaShopTests.blocks(level, room.box()).equals(bare),
                            "with no shop standing, the room did not wait");
                    UraharaShopTests.placeEach(level, room.shop());
                    // In the rock of the room's west wall.
                    Pig pig = EntityType.PIG.create(level);
                    pig.moveTo(Vec3.atBottomCenterOf(stamp.at(new BlockPos(-10, -15, 5))));
                    room.held().add(pig);
                    Map<BlockPos, BlockState> before = UraharaShopTests.blocks(level, room.box());
                    stamp.ensureBuiltIfLoaded(level, true);
                    helper.assertTrue(asked.mark().built() && stamp.triesLeft() == 0 && asked.warned().isEmpty(),
                            "the room was not built under the shop, or warned " + asked.warned());
                    PoolElementStructurePiece piece = (PoolElementStructurePiece) room.start().getPieces().get(0);
                    Map<BlockPos, BlockState> listed = listed(level.getStructureManager().getOrCreate(UraharaRoom.TEMPLATE),
                            piece.getPosition(), piece.getRotation());
                    // The jigsaw becomes the roof's own rock.
                    BlockPos jigsaw = stamp.at(new BlockPos(0, -9, 0));
                    listed.forEach((pos, state) -> helper.assertTrue(pos.equals(jigsaw) || level.getBlockState(pos)
                            .equals(state), "the room has " + level.getBlockState(pos) + " at " + pos + ", not " + state));
                    before.forEach((pos, state) -> helper.assertTrue(listed.containsKey(pos)
                            || level.getBlockState(pos).equals(state), "the room changed " + pos));
                    BlockPos front = stamp.at(UraharaShop.DOOR);
                    BlockPos door = front.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, front.getX(),
                            front.getZ()));
                    helper.assertTrue(pig.blockPosition().equals(door), "the pig the room was built over stands at "
                            + pig.blockPosition() + ", not before the shop's door at " + door);
                    // Up from the shop's floor onto the counter, over its lantern (9/16 high, within a step) to its west
                    // end, and down onto the hatch behind it.
                    for (BlockPos way : List.of(new BlockPos(3, 1, 7), new BlockPos(3, 2, 7), new BlockPos(3, 3, 8),
                            new BlockPos(2, 2, 8), new BlockPos(2, 3, 8), new BlockPos(2, 1, 9), new BlockPos(2, 2, 9),
                            new BlockPos(3, 1, 9))) {
                        helper.assertTrue(level.getBlockState(stamp.at(way)).isAir(), "the way to the hatch is shut at "
                                + way);
                    }
                    helper.assertTrue(level.getBlockState(stamp.at(new BlockPos(3, 0, 7))).isSolid()
                            && level.getBlockState(stamp.at(new BlockPos(3, 0, 9))).is(Blocks.SPRUCE_PLANKS),
                            "no floor before the counter, or beside the hatch to step out onto");
                    // Down and back: the open hatch over the ladder climbs as a ladder does, and the ladder down the shaft.
                    BlockPos hatch = stamp.at(UraharaRoom.HATCH);
                    level.setBlock(hatch, level.getBlockState(hatch).setValue(TrapDoorBlock.OPEN, true),
                            Block.UPDATE_CLIENTS);
                    for (int y : new int[] {0, -1, -9, -21}) {
                        pig.teleportTo(hatch.getX() + 0.5, hatch.getY() + y, hatch.getZ() + 0.5);
                        helper.assertTrue(pig.onClimbable(), "the pig cannot climb at " + pig.blockPosition());
                    }
                    UraharaShopTests.holdsItsKeeper(helper, level, room.site(), UraharaShopTests.box(room.shop()));
                    Map<BlockPos, BlockState> built = UraharaShopTests.blocks(level, room.box());
                    UraharaShop.Mark standing = new UraharaShop.Mark();
                    helper.assertTrue(!stamp.ensureBuilt(level) && !new UraharaRoom.Stamp(room.start(), standing, () -> true,
                            asked.warned()::add).ensureBuilt(level) && standing.built() && asked.warned().isEmpty()
                            && UraharaShopTests.blocks(level, room.box()).equals(built),
                            "asked again, or found standing, the room was built again or not marked built");
                })
                .thenSucceed();
    }

    /**
     * Test 3. Where players have spent a minute near it, ground as the world makes it does not keep the room out, nor planks
     * a tick short of the minute, but planks at the minute do. The room is given up, asked twice, with one line in the log
     * naming the spot and why, and nothing set: there (and built by a fresh mark once the planks are gone); where the shop
     * was built but its floor over the hatch is not as built; under land more than 6 below where the shop's floor rests;
     * each with the command that builds it; and, with none, within 5 blocks of the world's floor and in bedrock.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aRoomIsLeftForAnOperator(GameTestHelper helper) {
        Room lived = roomAt(helper, 340, 420, LIFT, true);
        Room low = roomAt(helper, 380, 420, 0, false);
        Room bedrock = roomAt(helper, 420, 420, LIFT, true);
        Room thin = roomAt(helper, 460, 420, LIFT, false);
        ServerLevel level = helper.getLevel();
        Asked livedIn = asked(lived, () -> false), notAsBuilt = asked(thin, () -> true), bare = asked(thin, () -> false),
                under = asked(low, () -> true), cut = asked(bedrock, () -> false);
        ChunkPos first = livedIn.stamp().covered().get(0);
        LevelChunk home = level.getChunk(first.x, first.z);
        long was = home.getInhabitedTime();
        lived.held().atEnd(() -> home.setInhabitedTime(was));
        BlockPos planks = lived.at(new BlockPos(17, -21, 9)), stone = bedrock.at(new BlockPos(5, -15, 5));
        // Ground as the world makes it, in the room's box.
        Map<BlockPos, Block> ground = Map.of(new BlockPos(-10, -22, -8), Blocks.STONE, new BlockPos(0, -21, 0),
                Blocks.DEEPSLATE, new BlockPos(5, -15, 5), Blocks.COAL_ORE, new BlockPos(5, -15, 15), Blocks.WATER,
                new BlockPos(16, -21, 9), Blocks.GLOW_LICHEN);
        String why = "players have spent " + UraharaShop.LIVED_IN_TICKS + " ticks near where it would go, and"
                + " minecraft:oak_planks stands at " + planks.toShortString();
        helper.startSequence()
                .thenWaitUntil(() -> List.of(lived, low, bedrock, thin).forEach(room -> UraharaShopTests.entitiesLoaded(
                        helper, level, room.box())))
                .thenExecute(() -> {
                    List.of(lived, bedrock).forEach(room -> UraharaShopTests.placeEach(level, room.shop()));
                    // Set quietly, and built over before any of it flows or falls.
                    ground.forEach((place, block) -> level.setBlock(lived.at(place), block.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
                    home.setInhabitedTime(UraharaShop.LIVED_IN_TICKS);
                    String natural = livedIn.stamp().whyNot(level);
                    level.setBlock(planks, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                    home.setInhabitedTime(UraharaShop.LIVED_IN_TICKS - 1);
                    String early = livedIn.stamp().whyNot(level);
                    home.setInhabitedTime(UraharaShop.LIVED_IN_TICKS);
                    helper.assertTrue(natural == null && early == null && why.equals(livedIn.stamp().whyNot(level)),
                            "lived in, the room is kept out by " + natural + "; a tick short, by " + early);
                    refused(helper, lived, livedIn, true, why);
                    level.setBlock(planks, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    Asked fresh = asked(lived, () -> false);
                    helper.assertTrue(fresh.stamp().ensureBuilt(level) && fresh.mark().built() && fresh.warned().isEmpty(),
                            "where players lived but built nothing, the room was not built");
                    refused(helper, thin, notAsBuilt, true, "the shop's floor where its hatch would open is not as the"
                            + " shop was built");
                    UraharaShopTests.placeEach(level, thin.shop());
                    refused(helper, thin, bare, true, "the land over it lies more than 6 blocks below the level the"
                            + " shop's floor rests on at " + bare.stamp().room().minX() + ", " + bare.stamp().room().minZ());
                    refused(helper, low, under, false, "it would come within 5 blocks of the world's floor");
                    level.setBlock(stone, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                    refused(helper, bedrock, cut, false, "it would cut into bedrock at " + stone.toShortString());
                })
                .thenSucceed();
    }

    /**
     * Test 4. Laid as {@code /place structure} lays it over a wet site, water and lava standing in its hall, its shaft and
     * its ladder's places and pressing on its walls, floor, roof and shaft from outside, the room holds no fluid, its
     * ladder stays dry beside water at its shaft's wall, and what stood outside stays there.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theRoomKeepsOutWaterAndLava(GameTestHelper helper) {
        Room room = roomAt(helper, 500, 420, LIFT, false);
        ServerLevel level = room.level();
        BoundingBox hall = BoundingBox.fromCorners(room.at(UraharaRoom.ROOM_MIN), room.at(UraharaRoom.ROOM_MAX));
        BoundingBox shaft = BoundingBox.fromCorners(room.at(UraharaRoom.SHAFT_MIN), room.at(UraharaRoom.SHAFT_MAX));
        // Inside: the hall, its east end, the ladder's foot and the shaft. Outside: a pocket past each face of the hall
        // and one against the shaft's west wall under the shop's floor, each walled but on the room's side.
        List<BlockPos> inside = List.of(new BlockPos(5, -15, 15), new BlockPos(17, -21, 9), new BlockPos(2, -21, 9),
                new BlockPos(2, -5, 9)).stream().map(room::at).toList();
        List<BlockPos> outside = List.of(new BlockPos(-11, -15, 5), new BlockPos(23, -15, 5), new BlockPos(5, -15, -9),
                new BlockPos(5, -15, 24), new BlockPos(5, -23, 5), new BlockPos(5, -8, 5), new BlockPos(0, -2, 9)).stream()
                .map(room::at).toList();
        BlockPos ladder = room.at(new BlockPos(2, -2, 9));
        helper.startSequence()
                .thenWaitUntil(() -> UraharaShopTests.entitiesLoaded(helper, level, room.box()))
                .thenExecute(() -> {
                    UraharaShopTests.placeEach(level, room.shop());
                    // Set quietly, so nothing flows before the room is laid.
                    for (int i = 0; i < inside.size(); i++) {
                        level.setBlock(inside.get(i), (i % 2 == 0 ? Blocks.WATER : Blocks.LAVA).defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                    for (int i = 0; i < outside.size(); i++) {
                        BlockPos pocket = outside.get(i);
                        for (Direction side : Direction.values()) {
                            if (!hall.isInside(pocket.relative(side)) && !shaft.isInside(pocket.relative(side))) {
                                level.setBlock(pocket.relative(side), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                            }
                        }
                        level.setBlock(pocket, (i % 2 == 0 ? Blocks.WATER : Blocks.LAVA).defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                    UraharaShopTests.placeEach(level, room.start());
                    // Woken, each pocket presses on the room's rock.
                    outside.forEach(pocket -> level.scheduleTick(pocket, level.getFluidState(pocket).getType(), 1));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    for (BoundingBox box : List.of(hall, shaft)) {
                        BlockPos.betweenClosedStream(box).forEach(pos -> helper.assertTrue(level.getFluidState(pos)
                                .isEmpty(), "the room holds " + level.getFluidState(pos) + " at " + pos));
                    }
                    helper.assertTrue(level.getBlockState(ladder).is(Blocks.LADDER) && level.getFluidState(ladder).isEmpty(),
                            "the ladder beside the water at the shaft's wall is " + level.getBlockState(ladder));
                    outside.forEach(pocket -> helper.assertTrue(level.getFluidState(pocket).isSource(),
                            "the pocket at " + pocket + " ran out"));
                })
                .thenSucceed();
    }

    // ---- Helpers ----

    /**
     * The shop and its room as worldgen lays them out at this far chunk, lifted, with the room's chunks and those beside
     * them held, and with land over the room if asked; every block of the shop's box and of the room's, with a rim of 2
     * about it, is put back when the test ends.
     */
    static Room roomAt(GameTestHelper helper, int chunkX, int chunkZ, int lift, boolean covered) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        StructureStart shop = UraharaShopTests.startAt(level, chunk), start = UraharaRoom.startAt(level, chunk);
        helper.assertTrue(start != null, "the room lays out nothing in chunk " + chunk);
        // Moved before anything reads either start's box, which each keeps once read.
        shop.getPieces().forEach(part -> part.move(0, lift, 0));
        start.getPieces().forEach(part -> part.move(0, lift, 0));
        BoundingBox box = UraharaShopTests.box(start).inflatedBy(2);
        // The room's middle chunk, at every turn, so the 25 its stamp waits on are held.
        ChunkPos middle = new ChunkPos(box.getCenter());
        ShakonmakuTests.Cleanup held = ShakonmakuTests.hold(helper, level, middle.x, middle.z);
        UraharaShopTests.putBackAtEnd(held, level, UraharaShopTests.box(shop));
        UraharaShopTests.putBackAtEnd(held, level, box);
        PoolElementStructurePiece piece = (PoolElementStructurePiece) shop.getPieces().get(0);
        BlockPos corner = piece.getPosition();
        Room room = new Room(level, held, new SeireiteiPlan.Site("urahara_shop", corner.getX(), corner.getZ(),
                UraharaShop.TEMPLATE, piece.getRotation(), UraharaShop.SIZE.getX(), UraharaShop.SIZE.getZ(), corner.getY()),
                shop, start, box);
        if (covered) {
            // The level the shop's floor rests on, over the whole room.
            BlockPos.betweenClosedStream(BoundingBox.fromCorners(room.at(UraharaRoom.ROOM_MIN.atY(-1)),
                    room.at(UraharaRoom.ROOM_MAX.atY(-1)))).forEach(pos -> level.setBlock(pos,
                    Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
        return room;
    }

    /** Every block the room's template lists, where and as it lays them with its corner here, turned so. */
    private static Map<BlockPos, BlockState> listed(StructureTemplate template, BlockPos corner, Rotation turn) {
        Map<BlockPos, BlockState> listed = new HashMap<>();
        for (Block block : List.of(Blocks.AIR, Blocks.STONE, Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.TUFF,
                Blocks.COBBLESTONE, Blocks.SHROOMLIGHT, Blocks.LADDER, Blocks.SPRUCE_TRAPDOOR, Blocks.JIGSAW)) {
            template.filterBlocks(corner, new StructurePlaceSettings().setRotation(turn), block)
                    .forEach(info -> listed.put(info.pos(), info.state()));
        }
        return listed;
    }

    /** A stamp for this room, keeping a mark never saved and a log of its own, asking this whether the shop was built. */
    private static Asked asked(Room room, BooleanSupplier shopBuilt) {
        UraharaShop.Mark mark = new UraharaShop.Mark();
        List<String> warned = new ArrayList<>();
        return new Asked(new UraharaRoom.Stamp(room.start(), mark, shopBuilt, warned::add), mark, warned);
    }

    /**
     * Asserts this stamp, asked twice, set nothing down and gave the room up, and told the log once where it is, why, and
     * the command that builds it, or that none can.
     */
    private static void refused(GameTestHelper helper, Room room, Asked asked, boolean operator, String why) {
        ServerLevel level = room.level();
        Map<BlockPos, BlockState> before = UraharaShopTests.blocks(level, room.box());
        for (int ask = 1; ask <= 2; ask++) {
            helper.assertTrue(!asked.stamp().ensureBuilt(level) && asked.mark().decided() && !asked.mark().built()
                    && UraharaShopTests.blocks(level, room.box()).equals(before),
                    "asked " + ask + " times, the stamp set something down at " + room.site() + ", or did not give it up");
        }
        String place = operator ? "/place structure bleach:urahara_training_room " + room.site().x() + " ~ "
                + room.site().z() : "No room fits under the shop there";
        List<String> warned = asked.warned();
        helper.assertTrue(warned.size() == 1 && warned.get(0).contains(room.site().chunk().toString())
                && warned.get(0).contains(why) && warned.get(0).contains(place),
                "the log was told " + warned + ", not once of " + why + " and " + place);
    }

    /** A test's shop and room: its world and hold, the shop's site and start, the room's start and the box put back. */
    record Room(ServerLevel level, ShakonmakuTests.Cleanup held, SeireiteiPlan.Site site, StructureStart shop,
                StructureStart start, BoundingBox box) {
        /** Where this place, before the turn and from the shop's corner on its floor, lies in the world. */
        BlockPos at(BlockPos place) {
            return new BlockPos(site.x(), site.y(), site.z()).offset(place.rotate(site.rotation()));
        }
    }

    /** A stamp of a test's room, with the mark and the log it keeps. */
    private record Asked(UraharaRoom.Stamp stamp, UraharaShop.Mark mark, List<String> warned) {
    }
}
