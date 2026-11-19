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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The training room under the Urahara Shop: its data and template, laid under the shop at every turn, and laid over a wet
 * site holding no water or lava. Each test takes away what it set down, however it ends.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class UraharaRoomTests {
    /**
     * How far a test lifts a shop and its room: the flat test world's floor is 3 blocks over its bottom, so a room 22 under
     * a shop there would leave the world.
     */
    private static final int LIFT = 40;

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
        Map<BlockPos, BlockState> listed = new HashMap<>();
        for (Block block : List.of(Blocks.AIR, Blocks.STONE, Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.TUFF,
                Blocks.COBBLESTONE, Blocks.SHROOMLIGHT, Blocks.LADDER, Blocks.SPRUCE_TRAPDOOR, Blocks.JIGSAW)) {
            template.filterBlocks(UraharaRoom.ROOM_MIN, new StructurePlaceSettings(), block)
                    .forEach(info -> listed.put(info.pos(), info.state()));
        }
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
     * Test 4. Laid as {@code /place structure} lays it over a wet site, water and lava standing in its hall, its shaft and
     * its ladder's places and pressing on its walls, floor, roof and shaft from outside, the room holds no fluid, its
     * ladder stays dry beside water at its shaft's wall, and what stood outside stays there.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theRoomKeepsOutWaterAndLava(GameTestHelper helper) {
        Room room = roomAt(helper, 500, 420);
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
     * The shop and its room as worldgen lays them out at this far chunk, held and lifted; every block of the shop's box and
     * of the room's, with a rim of 2 about it, is put back when the test ends.
     */
    private static Room roomAt(GameTestHelper helper, int chunkX, int chunkZ) {
        ServerLevel level = helper.getLevel();
        ShakonmakuTests.Cleanup held = ShakonmakuTests.hold(helper, level, chunkX, chunkZ);
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        StructureStart shop = UraharaShopTests.startAt(level, chunk), start = UraharaRoom.startAt(level, chunk);
        helper.assertTrue(start != null, "the room lays out nothing in chunk " + chunk);
        // Moved before anything reads either start's box, which each keeps once read.
        shop.getPieces().forEach(part -> part.move(0, LIFT, 0));
        start.getPieces().forEach(part -> part.move(0, LIFT, 0));
        BoundingBox box = UraharaShopTests.box(start).inflatedBy(2);
        UraharaShopTests.putBackAtEnd(held, level, UraharaShopTests.box(shop));
        UraharaShopTests.putBackAtEnd(held, level, box);
        PoolElementStructurePiece piece = (PoolElementStructurePiece) shop.getPieces().get(0);
        BlockPos corner = piece.getPosition();
        return new Room(level, new SeireiteiPlan.Site("urahara_shop", corner.getX(), corner.getZ(), UraharaShop.TEMPLATE,
                piece.getRotation(), UraharaShop.SIZE.getX(), UraharaShop.SIZE.getZ(), corner.getY()), shop, start, box);
    }

    /** A test's shop and room: its world, the shop's site and start, the room's start and the box put back. */
    private record Room(ServerLevel level, SeireiteiPlan.Site site, StructureStart shop, StructureStart start,
                        BoundingBox box) {
        /** Where this place, before the turn and from the shop's corner on its floor, lies in the world. */
        BlockPos at(BlockPos place) {
            return new BlockPos(site.x(), site.y(), site.z()).offset(place.rotate(site.rotation()));
        }
    }
}
