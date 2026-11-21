package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.soul.HotSpring;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SekkisekiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritClip;
import io.github.masteryoga1.bleach.world.GateHouses;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.HillWorks;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.SiteStamp;
import io.github.masteryoga1.bleach.world.gen.SitePiece;
import io.github.masteryoga1.bleach.world.gen.SitePlacement;
import io.github.masteryoga1.bleach.world.gen.SiteStructure;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sōkyoku Hill's works and Sekkiseki's hold: the works keep to the plan and stand on the hill's end, its stair and
 * tunnel can be walked and its spring heals only in the cavern, and a soul shut in the Senzaikyū's cell, but nowhere at
 * a gate, is held down.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HillTests {
    private static final String BATCH = "hill";
    private static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("seireitei_hill"));
    private static final ResourceKey<Biome> HILL = ResourceKey.create(Registries.BIOME, BleachMod.id("sokyoku_hill"));
    /** The works' corner on the lawn, where the plan sets their template, turned half round. */
    private static final BlockPos CORNER = new BlockPos(111, SeireiteiPlan.PLATEAU_Y, 5);
    /** Where a soul may be held: the cell, where a soul stands at 160, and the stair's last nine steps under it. */
    private static final BoundingBox HELD = new BoundingBox(102, 151, -4, 109, 163, 3);
    /** The Sōkyoku's stand on the hill's end, from its floor to the works' top. */
    private static final BoundingBox STAND = new BoundingBox(59, 135, -6, 63, 166, 5);
    private static final Vec3 IN_CELL = new Vec3(105.5, 160.0, 1.5), ON_BRIDGE = new Vec3(80.5, 136.0, -0.5);
    /** Where a soul stands on the lawn at the stair's foot, and in the cave's mouth. */
    private static final BlockPos STAIR_FOOT = new BlockPos(8, SeireiteiPlan.PLATEAU_Y, -65),
            MOUTH = new BlockPos(-65, SeireiteiPlan.PLATEAU_Y, 0);
    /** The hot spring's water; a soul on its bed, and on the cavern's floor clear of it. */
    private static final BoundingBox POOL = new BoundingBox(17, 55, 4, 22, 55, 9);
    private static final Vec3 SPRING = new Vec3(19.5, 55.0, 6.5), DRY_FLOOR = new Vec3(12.5, 56.0, 6.5);
    /** A lawn block far from the cavern that a test fills with water for a while. */
    private static final BlockPos LAWN_POOL = new BlockPos(62, SeireiteiPlan.PLATEAU_Y - 1, -30);
    /** Every block the cavern's dressing sets. */
    private static final Block[] DRESSING = {Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
            Blocks.POLISHED_ANDESITE, Blocks.SHROOMLIGHT, Blocks.STONE, Blocks.TUFF, Blocks.WATER};
    /** How far a walking soul goes in a tick, and room for the float steps of a position. */
    private static final double STRIDE = 0.25, SAME = 1.0E-6;

    private HillTests() {
    }

    // ---- Helpers ----

    /** A player joined in this game mode with this soul, set down here in Soul Society. */
    private static ServerPlayer joined(GameTestHelper helper, ServerLevel soul, GameType mode, SoulData data, Vec3 at) {
        ServerPlayer player = TestPlayers.join(helper, mode, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, data);
        player.teleportTo(soul, at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** A Shinigami of 400 reiryoku at Kidō 40 holding this much now. */
    private static SoulData shinigami(float now) {
        SoulData data = KidoTests.shinigami(400.0F, 40);
        data.setReiryoku(now);
        return data;
    }

    /** The works' template, as the plan names it. */
    private static StructureTemplate template(GameTestHelper helper, ServerLevel soul) {
        return template(helper, soul, SeireiteiPlan.sites("hill").get(0));
    }

    /** Sets this soul here at this gate's house, in the house's template frame, holding 200 reiryoku. */
    private static void stand(ServerPlayer player, ServerLevel soul, int gate, int x, int y, int z) {
        SeireiteiPlan.Site site = GateHouses.STAMP.sites().get(gate);
        BlockPos at = GateHouses.STAMP.turned(gate, new BlockPos(x, y, z), new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y,
                site.z()));
        player.teleportTo(soul, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        SoulLogic.get(player).setReiryoku(200.0F);
    }

    /** Asserts that this soul's reiryoku has refilled past 200 and that it is not held. */
    private static void rose(GameTestHelper helper, ServerPlayer player, String where) {
        SoulData data = SoulLogic.get(player);
        helper.assertTrue(data.reiryoku() > 200.0F && !SekkisekiLogic.held(data), "a soul " + where + " holds "
                + data.reiryoku() + (SekkisekiLogic.held(data) ? ", held down" : ""));
    }

    /** The template of this site of the hill's. */
    private static StructureTemplate template(GameTestHelper helper, ServerLevel soul, SeireiteiPlan.Site site) {
        var template = soul.getStructureManager().get(site.template());
        helper.assertTrue(template.isPresent(), site.template() + " did not load");
        return template.get();
    }

    /** Where this stamp's template sets each of these blocks, laid at its corner. */
    private static Map<BlockPos, Block> listed(GameTestHelper helper, ServerLevel soul, SiteStamp stamp, Block... blocks) {
        BoundingBox box = HillWorks.box(stamp);
        StructureTemplate template = template(helper, soul, stamp.sites().get(0));
        Map<BlockPos, Block> listed = new HashMap<>();
        for (Block block : blocks) {
            template.filterBlocks(new BlockPos(box.minX(), box.minY(), box.minZ()), new StructurePlaceSettings(), block)
                    .forEach(info -> listed.put(info.pos(), block));
        }
        return listed;
    }

    /** Whether this block lies in the cavern's air as the plan sets it. */
    private static boolean cavern(BlockPos pos) {
        return SeireiteiPlan.inCavern(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether the noise left this block solid: the mesa to its top and the lawn to the plateau, but for the cavern. */
    private static boolean ground(BlockPos pos) {
        boolean mesa = SeireiteiPlan.radius(pos.getX(), pos.getZ()) < SeireiteiPlan.MESA_RADIUS;
        return pos.getY() < (mesa ? SeireiteiPlan.MESA_TOP_Y : SeireiteiPlan.PLATEAU_Y) && !cavern(pos);
    }

    /** Whether nothing here stops a soul. */
    private static boolean open(ServerLevel soul, BlockPos pos) {
        return soul.getBlockState(pos).getCollisionShape(soul, pos).isEmpty();
    }

    /** Whether a soul can stand here: footing under it and three blocks clear from its feet up. */
    private static boolean standing(ServerLevel soul, BlockPos feet) {
        return !open(soul, feet.below()) && open(soul, feet) && open(soul, feet.above()) && open(soul, feet.above(2));
    }

    /**
     * The shortest way a soul walks inside this box from here to the first place that passes the goal: a block across at
     * a time, at most one down, and one up only onto a step that faces the way. Empty if there is none.
     */
    private static List<BlockPos> way(ServerLevel soul, BoundingBox inside, BlockPos from, Predicate<BlockPos> goal) {
        Map<BlockPos, BlockPos> came = new HashMap<>(Map.of(from, from));
        ArrayDeque<BlockPos> next = new ArrayDeque<>(List.of(from));
        while (!next.isEmpty()) {
            BlockPos at = next.poll();
            if (goal.test(at)) {
                List<BlockPos> way = new ArrayList<>(List.of(at));
                for (BlockPos back = at; !back.equals(from); back = came.get(back)) {
                    way.add(0, came.get(back));
                }
                return way;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos to = at.relative(side).above(dy);
                    BlockState under = soul.getBlockState(to.below());
                    if (inside.isInside(to) && !came.containsKey(to) && standing(soul, to) && (dy < 1
                            || under.getBlock() instanceof StairBlock && under.getValue(StairBlock.FACING) == side)) {
                        came.put(to, at);
                        next.add(to);
                    }
                }
            }
        }
        return List.of();
    }

    /** Asserts that no place on this way has a drop of two blocks or more beside it. */
    private static void walled(GameTestHelper helper, ServerLevel soul, List<BlockPos> way, String what) {
        for (BlockPos at : way) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = at.relative(side);
                helper.assertFalse(open(soul, beside) && open(soul, beside.below()) && open(soul, beside.below(2)),
                        what + " drops away to the " + side + " at " + at.toShortString());
            }
        }
    }

    /**
     * Asserts that this stamp's work stands, every block its template sets set as it sets it, and every other block in
     * and about its box that the noise made solid still solid.
     */
    private static void builtOver(GameTestHelper helper, ServerLevel soul, SiteStamp stamp, Block... palette) {
        BoundingBox box = HillWorks.box(stamp);
        helper.assertTrue(stamp.stands(soul, 0, new BlockPos(box.minX(), box.minY(), box.minZ())),
                stamp.sites().get(0).template() + " does not stand");
        Map<BlockPos, Block> listed = listed(helper, soul, stamp, palette);
        BlockPos wrong = BlockPos.betweenClosedStream(box.inflatedBy(1)).filter(pos -> listed.containsKey(pos)
                ? !soul.getBlockState(pos).is(listed.get(pos)) : ground(pos) && soul.getBlockState(pos).isAir())
                .findFirst().map(BlockPos::immutable).orElse(null);
        helper.assertTrue(wrong == null, stamp.sites().get(0).template() + " is wrong at " + wrong);
    }

    /** A joined Shinigami in survival here, at 10 health and too hungry to heal by itself. */
    private static ServerPlayer wounded(GameTestHelper helper, ServerLevel soul, Vec3 at) {
        ServerPlayer player = joined(helper, soul, GameType.SURVIVAL, shinigami(400.0F), at);
        player.getFoodData().setFoodLevel(17);
        player.getFoodData().setSaturation(0.0F);
        player.setHealth(10.0F);
        return player;
    }

    /** Asserts that no column within a birch's reach of this box can be the hill's biome, for any seed. */
    private static void birchClear(GameTestHelper helper, ServerLevel soul, BoundingBox box) {
        // A birch's leaves reach 2 from its trunk, and a column's biome is one of 4 quarts about it (BiomeManager.getBiome).
        // About the Senzaikyū the margin is small: the nearest quart reads 262 against the hill's 260 in Climate's units.
        BiomeSource biomes = soul.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = soul.getChunkSource().randomState().sampler();
        for (int x = box.minX() - 2; x <= box.maxX() + 2; x++) {
            for (int z = box.minZ() - 2; z <= box.maxZ() + 2; z++) {
                for (int q = 0; q < 4; q++) {
                    int qx = ((x - 2) >> 2) + (q & 1), qz = ((z - 2) >> 2) + (q >> 1);
                    helper.assertFalse(biomes.getNoiseBiome(qx, QuartPos.fromBlock(136), qz, sampler).is(HILL),
                            "the hill's birch could stand at " + x + ", " + z);
                }
            }
        }
    }

    /** Asserts that no block about this gate's house that Sekkiseki does not fill holds a soul down. */
    private static void scan(GameTestHelper helper, ServerLevel soul, int gate, String when) {
        BoundingBox around = GateHouses.box(gate).inflatedBy(3);
        helper.assertTrue(soul.isLoaded(new BlockPos(around.minX(), 72, around.minZ()))
                && soul.isLoaded(new BlockPos(around.maxX(), 72, around.maxZ())), "gate " + gate + "'s ground is not loaded");
        BlockPos held = BlockPos.betweenClosedStream(around)
                .filter(pos -> !SpiritClip.negates(soul.getBlockState(pos)) && SekkisekiLogic.enclosed(soul, pos))
                .findFirst().map(BlockPos::immutable).orElse(null);
        helper.assertTrue(held == null, "a soul at " + held + " by gate " + gate + " " + when + " is held down");
    }

    // ---- The plan ----

    /**
     * The hill's first site, the Senzaikyū's, in the hill's reserve, its own start chunk, its box clear of the cavern and
     * under the build's top, laid out on the lawn turned half round, a marker in each chunk it covers, and no column within
     * a birch's reach of it in the hill's biome for any seed.
     */
    @GameTest(template = "empty")
    public static void theHillsWorksKeepToThePlan(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        List<SeireiteiPlan.Site> hill = SeireiteiPlan.sites("hill");
        SeireiteiPlan.Site site = hill.get(0);
        helper.assertTrue(HillWorks.STAMP.sites().equals(hill.subList(0, 1))
                && site.template().equals(BleachMod.id("hill/senzaikyu")) && site.rotation() == Rotation.CLOCKWISE_180
                && site.x() == CORNER.getX() && site.z() == CORNER.getZ() && site.width() == 53 && site.depth() == 12,
                "the hill's sites are " + hill);
        ChunkPos home = site.chunk();
        helper.assertTrue(SeireiteiPlan.SITES.stream().filter(other -> other.chunk().equals(home)).count() == 1
                && SeireiteiPlan.siteAt("hill", home.x, home.z) == site, "the works do not start alone in " + home);
        SeireiteiPlan.Reserve own = site.reserve();
        for (int x = own.minX(); x <= own.maxX(); x++) {
            for (int z = own.minZ(); z <= own.maxZ(); z++) {
                helper.assertTrue(SeireiteiPlan.reservedBy(x, z) == SeireiteiPlan.RESERVES.get(0)
                        && SeireiteiPlan.radius(x, z) < SeireiteiPlan.HILL_RESERVE_RADIUS
                        && SeireiteiPlan.street(x, z) == SeireiteiPlan.Street.NONE, "the works' reserve leaves the hill's at "
                        + x + ", " + z);
            }
        }
        BoundingBox box = HillWorks.STAMP.box(0, SeireiteiPlan.PLATEAU_Y);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                helper.assertTrue(SeireiteiPlan.radius(x, z) >= SeireiteiPlan.CAVERN_RADIUS,
                        "the works reach over the cavern at " + x + ", " + z);
            }
        }
        helper.assertTrue(box.maxY() < soul.getMaxBuildHeight() && template(helper, soul).getSize().equals(HillWorks.SIZE),
                "the works' box " + box + " or template is wrong");
        StructureSet set = soul.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(SET).value();
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(HillWorks.STRUCTURE);
        helper.assertTrue(set.placement() instanceof SitePlacement plan && plan.group().equals("hill")
                        && set.structures().size() == 1 && set.structures().get(0).structure().is(HillWorks.STRUCTURE)
                        && structure instanceof SiteStructure laid && laid.group().equals("hill"),
                "the works' set is not the hill group's alone: " + set.placement() + ", " + structure);
        var state = soul.getChunkSource().getGeneratorState();
        for (ChunkPos near : ChunkPos.rangeClosed(home, 1).toList()) {
            helper.assertTrue(set.placement().isStructureChunk(state, near.x, near.z) == near.equals(home),
                    "chunk " + near + " is wrongly where the works start or not");
        }
        ChunkGenerator generator = soul.getChunkSource().getGenerator();
        StructureStart start = structure.generate(soul.registryAccess(), generator, generator.getBiomeSource(),
                soul.getChunkSource().randomState(), soul.getStructureManager(), soul.getSeed(), home, 0, soul,
                structure.biomes()::contains);
        helper.assertTrue(start.isValid() && start.getPieces().size() == 1
                        && start.getPieces().get(0) instanceof SitePiece piece
                        && piece.getRotation() == Rotation.CLOCKWISE_180 && piece.templatePosition().equals(CORNER)
                        && piece.getBoundingBox().equals(box), "the works are laid out as " + start.getPieces());
        Map<ChunkPos, Integer> marked = new HashMap<>();
        for (SiteStamp.Marker marker : HillWorks.MARKERS) {
            marked.merge(new ChunkPos(HillWorks.STAMP.markerAt(0, marker, CORNER)), 1, Integer::sum);
        }
        helper.assertTrue(marked.keySet().equals(Set.copyOf(HillWorks.STAMP.covered(0)))
                && marked.values().stream().allMatch(n -> n == 1), "the works' markers lie in " + marked);
        birchClear(helper, soul, box);
        helper.succeed();
    }

    /**
     * Judged on the template alone: a soul is held only in the cell and on the stair's last steps, everywhere in the cell
     * standing or jumping, and nowhere in it once the cell's roof is gone.
     */
    @GameTest(template = "empty")
    public static void onlyTheCellHoldsASoulDown(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        StructurePlaceSettings turn = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_180);
        Set<BlockPos> sekkiseki = template(helper, soul).filterBlocks(CORNER, turn, ModBlocks.SEKKISEKI.get()).stream()
                .map(StructureBlockInfo::pos).collect(Collectors.toSet());
        // Beyond the box one of a block's six ways leads away from all of it, so only the box can hold a soul.
        BlockPos stray = BlockPos.betweenClosedStream(HillWorks.STAMP.box(0, SeireiteiPlan.PLATEAU_Y))
                .filter(pos -> !sekkiseki.contains(pos) && SekkisekiLogic.enclosed(sekkiseki::contains, pos)
                        && !HELD.isInside(pos)).findFirst().map(BlockPos::immutable).orElse(null);
        helper.assertTrue(stray == null, "a soul at " + stray + " is held outside the cell");
        long cell = BlockPos.betweenClosedStream(102, 160, -4, 109, 161, 3)
                .filter(pos -> SekkisekiLogic.enclosed(sekkiseki::contains, pos)).count();
        helper.assertTrue(cell == 128, cell + " of the cell's 128 standing and jumping places hold a soul");
        Set<BlockPos> roofless = new HashSet<>(sekkiseki);
        roofless.removeIf(pos -> pos.getY() == 164 && HELD.isInside(pos.getX(), HELD.minY(), pos.getZ()));
        helper.assertTrue(BlockPos.betweenClosedStream(102, 160, -4, 109, 160, 3)
                .noneMatch(pos -> SekkisekiLogic.enclosed(roofless::contains, pos)), "a roofless cell holds a soul");
        helper.succeed();
    }

    /**
     * The stair, the cave and the cavern's dressing: a stamp for each of the hill's other sites, each starting alone in its
     * chunk at its own height, unturned, its reserve in the hill's and clear of the Senzaikyū's, laid out where its stamp
     * builds it with a marker in each chunk it covers; no birch can reach the stair, the cave meets the cavern only at
     * its floor, and the dressing keeps off the cavern's walls and roof.
     */
    @GameTest(template = "empty")
    public static void theHillsWaysKeepToThePlan(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        List<SeireiteiPlan.Site> hill = SeireiteiPlan.sites("hill");
        helper.assertTrue(HillWorks.STAMPS.stream().flatMap(stamp -> stamp.sites().stream()).toList().equals(hill)
                && hill.get(1).y() == SeireiteiPlan.PLATEAU_Y && hill.get(2).y() == SeireiteiPlan.CAVERN_FLOOR_Y
                && hill.get(3).y() == SeireiteiPlan.CAVERN_FLOOR_Y - 2 && SeireiteiPlan.SITES.stream()
                .filter(site -> !hill.subList(1, 4).contains(site)).allMatch(site -> site.y() == SeireiteiPlan.Site.GROUND),
                "the hill's sites are " + hill);
        SeireiteiPlan.Reserve senzaikyu = hill.get(0).reserve();
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(HillWorks.STRUCTURE);
        ChunkGenerator generator = soul.getChunkSource().getGenerator();
        Map<SiteStamp, List<SiteStamp.Marker>> ways = Map.of(HillWorks.STAIR, HillWorks.STAIR_MARKERS, HillWorks.CAVE,
                HillWorks.CAVE_MARKERS, HillWorks.CAVERN, HillWorks.CAVERN_MARKERS);
        ways.forEach((stamp, markers) -> {
            SeireiteiPlan.Site site = stamp.sites().get(0);
            ChunkPos home = site.chunk();
            BoundingBox box = HillWorks.box(stamp);
            BlockPos corner = new BlockPos(site.x(), box.minY(), site.z());
            SeireiteiPlan.Reserve own = site.reserve();
            Vec3i size = new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());
            helper.assertTrue(SeireiteiPlan.SITES.stream().filter(other -> other.chunk().equals(home)).count() == 1
                    && SeireiteiPlan.siteAt("hill", home.x, home.z) == site && site.rotation() == Rotation.NONE
                    && template(helper, soul, site).getSize().equals(size) && size.getX() == site.width()
                    && size.getZ() == site.depth(), site + " does not start alone in its chunk, or its size is wrong");
            helper.assertTrue(SeireiteiPlan.radius(own.minX(), own.minZ()) < SeireiteiPlan.HILL_RESERVE_RADIUS
                    && SeireiteiPlan.radius(own.maxX(), own.maxZ()) < SeireiteiPlan.HILL_RESERVE_RADIUS
                    && SeireiteiPlan.radius(own.minX(), own.maxZ()) < SeireiteiPlan.HILL_RESERVE_RADIUS
                    && SeireiteiPlan.radius(own.maxX(), own.minZ()) < SeireiteiPlan.HILL_RESERVE_RADIUS
                    && own.maxX() < senzaikyu.minX(), site + "'s reserve leaves the hill's or meets the Senzaikyū's");
            StructureStart start = structure.generate(soul.registryAccess(), generator, generator.getBiomeSource(),
                    soul.getChunkSource().randomState(), soul.getStructureManager(), soul.getSeed(), home, 0, soul,
                    structure.biomes()::contains);
            helper.assertTrue(start.isValid() && start.getPieces().size() == 1 && start.getPieces().get(0) instanceof
                    SitePiece piece && piece.getRotation() == Rotation.NONE && piece.templatePosition().equals(corner)
                    && piece.getBoundingBox().equals(box), site + " is laid out as " + start.getPieces());
            Map<ChunkPos, Integer> marked = new HashMap<>();
            for (SiteStamp.Marker marker : markers) {
                BlockPos at = stamp.markerAt(0, marker, corner);
                helper.assertTrue(listed(helper, soul, stamp, marker.block().get()).containsKey(at),
                        site + "'s marker at " + at + " is not in its template");
                marked.merge(new ChunkPos(at), 1, Integer::sum);
            }
            helper.assertTrue(marked.keySet().equals(Set.copyOf(stamp.covered(0)))
                    && marked.values().stream().allMatch(n -> n == 1), site + "'s markers lie in " + marked);
        });
        birchClear(helper, soul, HillWorks.box(HillWorks.STAIR));
        listed(helper, soul, HillWorks.CAVE, Blocks.AIR, Blocks.STONE_STAIRS, Blocks.SHROOMLIGHT).forEach((pos, block) ->
                helper.assertTrue(Direction.stream().noneMatch(side -> cavern(pos.relative(side))) || block == Blocks.AIR
                        && pos.getY() < SeireiteiPlan.CAVERN_FLOOR_Y + 3, "the cave meets the cavern at " + pos));
        BoundingBox dressing = HillWorks.box(HillWorks.CAVERN);
        helper.assertTrue(dressing.maxY() < SeireiteiPlan.CAVERN_CEILING_Y - 1
                && cavern(new BlockPos(dressing.minX(), SeireiteiPlan.CAVERN_FLOOR_Y, dressing.minZ()))
                && cavern(new BlockPos(dressing.maxX(), SeireiteiPlan.CAVERN_FLOOR_Y, dressing.maxZ()))
                && cavern(new BlockPos(dressing.minX(), SeireiteiPlan.CAVERN_FLOOR_Y, dressing.maxZ()))
                && cavern(new BlockPos(dressing.maxX(), SeireiteiPlan.CAVERN_FLOOR_Y, dressing.minZ()))
                && listed(helper, soul, HillWorks.CAVERN, Blocks.AIR).isEmpty(), "the dressing's box " + dressing
                + " leaves the cavern, or it cuts air");
        // A spire stands on the floor, so only the cell under its foot may lie outside the cavern's air.
        listed(helper, soul, HillWorks.CAVERN, DRESSING).keySet().stream().filter(pos -> pos.getY()
                >= SeireiteiPlan.CAVERN_FLOOR_Y).forEach(pos -> helper.assertTrue(Direction.stream().allMatch(side ->
                side == Direction.DOWN && pos.getY() == SeireiteiPlan.CAVERN_FLOOR_Y || cavern(pos.relative(side))),
                "the dressing meets the cavern's wall or roof at " + pos));
        helper.succeed();
    }

    // ---- In the world ----

    /**
     * Built where its chunks stand, the works cut the hill's end back under its lip and floor it for the stand, the rest
     * of the hill untouched in and about the box; no birch stands about the stand; the cell holds and the way in does not.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void theWorksStandOnTheHillsEnd(GameTestHelper helper) {
        ServerLevel soul = ShakonmakuTests.hold(helper, 5, 0).soul;
        HillWorks.ensureBuilt(soul);
        helper.assertTrue(HillWorks.STAMP.stands(soul, 0, CORNER) && HillWorks.MARKERS.stream().allMatch(marker ->
                soul.getBlockState(HillWorks.STAMP.markerAt(0, marker, CORNER)).is(marker.block().get())),
                "the works do not stand");
        for (int x = 55; x <= 63; x++) {
            for (int z = -7; z <= 6; z++) {
                for (int y = SeireiteiPlan.PLATEAU_Y; y < SeireiteiPlan.MESA_TOP_Y; y++) {
                    boolean inside = x >= 59 && z >= -6 && z <= 5;
                    boolean cut = inside && y < 130 && 64 - x <= Math.min(5, Math.max(0, (y - 98) / 4));
                    BlockState block = soul.getBlockState(new BlockPos(x, y, z));
                    helper.assertTrue(cut ? block.isAir() : inside && y == SeireiteiPlan.MESA_TOP_Y - 1
                            ? block.is(Blocks.STONE_BRICKS) : !block.isAir(), "the hill's end at " + x + ", " + y + ", "
                            + z + " is " + block);
                }
            }
        }
        BlockPos birch = BlockPos.betweenClosedStream(STAND.inflatedBy(2)).filter(pos -> {
            BlockState block = soul.getBlockState(pos);
            return block.is(Blocks.BIRCH_LOG) || block.is(Blocks.BIRCH_LEAVES) || block.is(Blocks.BEE_NEST);
        }).findFirst().map(BlockPos::immutable).orElse(null);
        helper.assertTrue(birch == null, "a birch stands about the Sōkyoku's stand at " + birch);
        helper.assertTrue(SekkisekiLogic.enclosed(soul, BlockPos.containing(IN_CELL))
                && !SekkisekiLogic.enclosed(soul, new BlockPos(101, 136, -1))
                && !SekkisekiLogic.enclosed(soul, new BlockPos(102, 136, 0))
                && !SekkisekiLogic.enclosed(soul, BlockPos.containing(ON_BRIDGE)), "the cell or its way in is wrong");
        helper.succeed();
    }

    /**
     * In the cell a Shinigami and a Quincy are told once, stop refilling and drain to nothing, and Kidō then fails them;
     * one on the bridge, and one in creative or spectator in the cell, are never held; once out, the drained one refills.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void aSoulShutInTheCellIsDrained(GameTestHelper helper) {
        ServerLevel soul = ShakonmakuTests.hold(helper, 6, 0).soul;
        HillWorks.ensureBuilt(soul);
        ServerPlayer shut = joined(helper, soul, GameType.SURVIVAL, shinigami(400.0F), IN_CELL);
        SoulData blood = QuincyTests.quincy(Bloodline.ECHT, 1, 200.0F);
        QuincyLogic.setReishi(blood, QuincyLogic.maxReishi(blood));
        ServerPlayer quincy = joined(helper, soul, GameType.SURVIVAL, blood, new Vec3(107.5, 160.0, -2.5));
        ServerPlayer builder = joined(helper, soul, GameType.CREATIVE, shinigami(200.0F), new Vec3(103.5, 160.0, 2.5));
        ServerPlayer walker = joined(helper, soul, GameType.SURVIVAL, shinigami(200.0F), ON_BRIDGE);
        ServerPlayer ghost = joined(helper, soul, GameType.SPECTATOR, shinigami(200.0F), new Vec3(106.5, 160.0, -0.5));
        KidoSpell sho = KidoTests.spells(helper).getHolderOrThrow(KidoSpell.SHO).value();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(SoulLogic.get(shut).reiryoku() == 0.0F
                        && SoulLogic.get(quincy).reiryoku() == 0.0F && QuincyLogic.reishi(SoulLogic.get(quincy)) == 0.0F,
                        "the cell's souls still hold reiryoku or reishi"))
                .thenExecute(() -> {
                    for (ServerPlayer held : List.of(shut, quincy)) {
                        helper.assertTrue(TravelTests.lines(held, SekkisekiLogic.ENCLOSED) == 1
                                && SekkisekiLogic.held(SoulLogic.get(held)), "a soul in the cell was not told once or held");
                    }
                    helper.assertTrue(KidoEvents.cast(shut, sho, KidoLogic.Form.NAME, soul.getGameTime(), 1.0)
                            == KidoLogic.Refusal.TOO_WEAK, "a drained soul casts Shō");
                    rose(helper, walker, "on the bridge");
                    SoulData free = SoulLogic.get(builder);
                    helper.assertTrue(free.reiryoku() >= 200.0F && !SekkisekiLogic.held(free)
                            && TravelTests.lines(builder, SekkisekiLogic.ENCLOSED) == 0, "a creative soul is held");
                    helper.assertTrue(!SekkisekiLogic.held(SoulLogic.get(ghost))
                            && TravelTests.lines(ghost, SekkisekiLogic.ENCLOSED) == 0, "a spectator in the cell is held");
                    shut.teleportTo(soul, ON_BRIDGE.x, ON_BRIDGE.y, ON_BRIDGE.z, 0.0F, 0.0F);
                })
                .thenIdle(2 * SekkisekiLogic.CHECK_TICKS)
                .thenExecute(() -> helper.assertTrue(SoulLogic.get(shut).reiryoku() > 0.0F
                        && !SekkisekiLogic.held(SoulLogic.get(shut)) && TravelTests.lines(shut, SekkisekiLogic.ENCLOSED) == 1,
                        "a soul out of the cell does not refill, or was told again"))
                .thenSucceed();
    }

    /**
     * Stamped into this old world, the stair cuts only its own cells from the north cliff, and a soul climbs it from the
     * lawn to the hill's top a block at a time, each rise onto a step, three blocks clear over it and rock beside it.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void theStairClimbsTheHill(GameTestHelper helper) {
        ServerLevel soul = ShakonmakuTests.hold(helper, 0, -4).soul;
        HillWorks.STAIR.ensureBuilt(soul, 0);
        builtOver(helper, soul, HillWorks.STAIR, Blocks.AIR, Blocks.STONE_STAIRS);
        List<BlockPos> way = way(soul, HillWorks.box(HillWorks.STAIR).inflatedBy(1), STAIR_FOOT,
                pos -> pos.getY() == SeireiteiPlan.MESA_TOP_Y);
        long rises = IntStream.range(1, way.size()).filter(i -> way.get(i).getY() > way.get(i - 1).getY()).count();
        helper.assertTrue(rises == SeireiteiPlan.MESA_TOP_Y - SeireiteiPlan.PLATEAU_Y, "the stair's way of " + way.size()
                + " rises " + rises);
        walled(helper, soul, way, "the stair");
        helper.succeed();
    }

    /**
     * Stamped into this old world, the cave cuts only its own cells from the west cliff, and its stair runs from the lawn
     * to the cavern's floor and back as the hill's does; a soul walking in from the mouth goes down it without a bump and
     * ends on the cavern's floor.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void aSoulWalksDownTheTunnel(GameTestHelper helper) {
        ServerLevel soul = ShakonmakuTests.hold(helper, -4, 0).soul;
        HillWorks.CAVE.ensureBuilt(soul, 0);
        builtOver(helper, soul, HillWorks.CAVE, Blocks.AIR, Blocks.STONE_STAIRS, Blocks.SHROOMLIGHT);
        BoundingBox around = HillWorks.box(HillWorks.CAVE).inflatedBy(1);
        List<BlockPos> down = way(soul, around, MOUTH, HillTests::cavern);
        BlockPos foot = down.isEmpty() ? MOUTH : down.get(down.size() - 1);
        List<BlockPos> up = way(soul, around, foot, pos -> pos.getX() == MOUTH.getX());
        helper.assertTrue(foot.getY() == SeireiteiPlan.CAVERN_FLOOR_Y && up.size() == down.size(), "the tunnel's way down "
                + down.size() + " long ends at " + foot + ", and up is " + up.size());
        walled(helper, soul, down, "the tunnel");
        ServerPlayer walker = joined(helper, soul, GameType.SURVIVAL, shinigami(400.0F), new Vec3(MOUTH.getX() + 0.5,
                MOUTH.getY(), 1.0));
        boolean[] bumped = {false};
        helper.startSequence()
                .thenIdle(5)
                .thenWaitUntil(() -> {
                    if (walker.getX() < foot.getX() + 0.5 - SAME) {
                        double y = walker.getY();
                        walker.move(MoverType.SELF, new Vec3(STRIDE, -1.0, 0.0));
                        bumped[0] |= walker.horizontalCollision || walker.getY() > y + SAME;
                    }
                    helper.assertTrue(walker.getX() >= foot.getX() + 0.5 - SAME, "the soul is in the tunnel at "
                            + walker.position());
                })
                .thenExecute(() -> helper.assertTrue(!bumped[0] && walker.onGround()
                        && Math.abs(walker.getY() - SeireiteiPlan.CAVERN_FLOOR_Y) < SAME && cavern(walker.blockPosition()),
                        "the walk down ended at " + walker.position() + (bumped[0] ? " after a bump" : "")))
                .thenSucceed();
    }

    /**
     * A wounded soul standing in the cavern's hot spring has Regeneration II and heals, and loses it within its length
     * once out on the floor; one standing in the same water on the lawn has nothing. The spring stays in its rim.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void theSpringHealsOnlyInTheCavern(GameTestHelper helper) {
        ShakonmakuTests.Cleanup hold = ShakonmakuTests.hold(helper, 1, 0);
        ServerLevel soul = hold.soul;
        HillWorks.CAVERN.ensureBuilt(soul, 0);
        BlockState lawn = soul.getBlockState(LAWN_POOL);
        hold.atEnd(() -> soul.setBlockAndUpdate(LAWN_POOL, lawn));
        soul.setBlockAndUpdate(LAWN_POOL, Blocks.WATER.defaultBlockState());
        ServerPlayer bather = wounded(helper, soul, SPRING);
        ServerPlayer dry = wounded(helper, soul, Vec3.atBottomCenterOf(LAWN_POOL));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bather.hasEffect(MobEffects.REGENERATION)
                        && bather.getHealth() > 10.0F, "the bather has not healed"))
                .thenExecute(() -> {
                    MobEffectInstance held = bather.getEffect(MobEffects.REGENERATION);
                    helper.assertTrue(held != null && held.getAmplifier() == HotSpring.AMPLIFIER
                            && held.getDuration() <= HotSpring.EFFECT_TICKS, "the bather holds " + held);
                    helper.assertTrue(dry.isInWater() && !dry.hasEffect(MobEffects.REGENERATION) && dry.getHealth() == 10.0F,
                            "a soul in water on the lawn holds " + dry.getEffect(MobEffects.REGENERATION) + " at "
                            + dry.getHealth() + " health");
                    bather.teleportTo(soul, DRY_FLOOR.x, DRY_FLOOR.y, DRY_FLOOR.z, 0.0F, 0.0F);
                })
                .thenIdle(HotSpring.EFFECT_TICKS + 1)
                .thenExecute(() -> {
                    helper.assertFalse(bather.hasEffect(MobEffects.REGENERATION), "the bather still heals on the floor");
                    BlockPos spilt = BlockPos.betweenClosedStream(POOL.inflatedBy(1)).filter(pos -> POOL.isInside(pos)
                            ? !soul.getFluidState(pos).isSourceOfType(Fluids.WATER) : !soul.getFluidState(pos).isEmpty())
                            .findFirst().map(BlockPos::immutable).orElse(null);
                    helper.assertTrue(spilt == null, "the spring is not whole in its rim at " + spilt);
                })
                .thenSucceed();
    }

    /** Stamped into this old world, the cavern's dressing stands as its template sets it, the rock about it kept. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void theCavernIsDressedInAnOldWorld(GameTestHelper helper) {
        ServerLevel soul = ShakonmakuTests.hold(helper, -1, -1).soul;
        HillWorks.CAVERN.ensureBuilt(soul, 0);
        builtOver(helper, soul, HillWorks.CAVERN, DRESSING);
        helper.succeed();
    }

    /**
     * At each gate, shut and lifted, no block about its house holds a soul down, and a soul in its passage or under its
     * roof refills: beside the shut gate, in its wicket and in the slot over it, under the lifted gate and at the
     * doorway's head.
     */
    @GameTest(template = "empty", batch = "hill_gates", timeoutTicks = 1200)
    public static void noSoulIsHeldAtAGate(GameTestHelper helper) {
        int gates = GateHouses.STAMP.sites().size();
        ServerLevel soul = GateTests.hold(helper, 0).soul;
        for (int gate = 1; gate < gates; gate++) {
            GateTests.hold(helper, gate);
        }
        GateState state = GateState.get(soul);
        ServerPlayer low = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, new Vec3(0.5, 72.0, 505.5));
        ServerPlayer high = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, new Vec3(0.5, 80.0, 505.5));
        ServerPlayer inWicket = ShakonmakuTests.soulAt(helper, soul, GameType.SURVIVAL, new Vec3(2.5, 72.0, 505.5));
        high.getAbilities().flying = true;
        high.onUpdateAbilities();
        GameTestSequence steps = helper.startSequence();
        for (int gate = 0; gate < gates; gate++) {
            int g = gate;
            steps.thenWaitUntil(() -> GateTests.assertDown(helper, state, g))
                    .thenExecute(() -> {
                        scan(helper, soul, g, "shut");
                        stand(low, soul, g, 8, 0, 3);
                        stand(high, soul, g, 8, 30, 4);
                        stand(inWicket, soul, g, 14, 0, 4);
                    })
                    .thenIdle(2 * SekkisekiLogic.CHECK_TICKS)
                    .thenExecute(() -> {
                        rose(helper, low, "beside gate " + g + ", shut");
                        rose(helper, high, "over gate " + g + ", shut");
                        rose(helper, inWicket, "in gate " + g + "'s wicket, shut");
                        stand(low, soul, g, 8, 0, 0);
                        stand(high, soul, g, 8, 23, 3);
                        state.lift(soul, g);
                    })
                    .thenWaitUntil(() -> GateTests.assertIs(helper, state, g, GateState.Status.LIFTED,
                            SeireiteiPlan.GATE_LIFT))
                    .thenExecute(() -> {
                        scan(helper, soul, g, "lifted");
                        stand(low, soul, g, 8, 0, 4);
                        stand(high, soul, g, 8, 23, 3);
                    })
                    .thenIdle(2 * SekkisekiLogic.CHECK_TICKS)
                    .thenExecute(() -> {
                        rose(helper, low, "under gate " + g + ", lifted");
                        rose(helper, high, "at gate " + g + "'s doorway head");
                        stand(low, soul, g, 8, 0, 0);
                        stand(high, soul, g, 8, 0, 1);
                        state.drop(soul, g);
                    });
        }
        steps.thenSucceed();
    }
}
