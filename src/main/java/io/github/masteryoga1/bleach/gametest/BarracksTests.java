package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.block.DivisionBannerBlock;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.SiteStamp;
import io.github.masteryoga1.bleach.world.gen.SitePiece;
import io.github.masteryoga1.bleach.world.gen.SitePlacement;
import io.github.masteryoga1.bleach.world.gen.SiteStructure;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Gotei 13's barracks: the plan's thirteen sites and its reserves, the templates and their banners, turned
 * and not, and one compound laid in the real Soul Society, whose chunks, made with structures off as a world's made
 * before the barracks were, keep their lawn until a test's own stamp lays it, in a batch of its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BarracksTests {
    private static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, BleachMod.id("seireitei_barracks"));
    private static final ResourceKey<StructureSet> SET =
            ResourceKey.create(Registries.STRUCTURE_SET, BleachMod.id("seireitei_barracks"));
    /** The banners on the gate's two posts, before the turn: make_structures.py hangs them there, facing north. */
    private static final Set<BlockPos> BANNERS = Set.of(new BlockPos(13, 2, 0), new BlockPos(18, 2, 0));
    /** The First's ring by the hill (K1), and the other twelve's. */
    private static final double FIRST_FROM = SeireiteiPlan.HILL_RESERVE_RADIUS, FIRST_TO = 200.0, RING = 460.0;
    /** The smoke's lawn spot (ClientSmokeTest.LAWN_TP, client code a test may not load): 16.5, 472.5. */
    private static final int LAWN_X = 16, LAWN_Z = SeireiteiPlan.SEIREITEI_RADIUS - 40;
    /** The Tenth's, the site the real-world test lays: turned clockwise, with no bed, water or entity. */
    private static final int TENTH = 9;

    private BarracksTests() {
    }

    // ---- Helpers ----

    /** This site's template, as Soul Society's server holds it. */
    private static StructureTemplate template(GameTestHelper helper, ServerLevel soul, SeireiteiPlan.Site site) {
        var template = soul.getStructureManager().get(site.template());
        helper.assertTrue(template.isPresent(), "the template " + site.template() + " did not load");
        return template.get();
    }

    /** The division banners this template sets, turned and placed as worldgen sets them at this corner. */
    private static List<StructureBlockInfo> banners(StructureTemplate template, BlockPos corner, Rotation turn) {
        return template.filterBlocks(corner, new StructurePlaceSettings().setRotation(turn), ModBlocks.DIVISION_BANNER.get());
    }

    /** The compass way out of a compound at this centre toward the centre column: the larger part, z on a tie. */
    private static Direction inward(double x, double z) {
        return Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST)
                : (z > 0 ? Direction.NORTH : Direction.SOUTH);
    }

    /** How this site's reserve first breaks the plan, and at which column, or null if it keeps to it. */
    @Nullable
    private static String breach(SeireiteiPlan.Site site, List<SeireiteiPlan.Reserve> named) {
        SeireiteiPlan.Reserve own = site.reserve();
        BoundingBox box = site.footprint();
        for (int x = own.minX(); x <= own.maxX(); x++) {
            for (int z = own.minZ(); z <= own.maxZ(); z++) {
                for (SeireiteiPlan.Reserve row : named) {
                    if (row.contains(x, z) && !row.group().equals(site.group())) {
                        return "has its reserve in " + row + " at " + x + ", " + z;
                    }
                }
                if (site.group().equals("barracks") && SeireiteiPlan.radius(x, z) >= SeireiteiPlan.SEIREITEI_RADIUS) {
                    return "has its reserve at the wall at " + x + ", " + z;
                }
                if (box.isInside(x, box.minY(), z)) {
                    for (SeireiteiPlan.Site other : SeireiteiPlan.SITES) {
                        if (other != site && other.reserve().contains(x, z)) {
                            return "has its box in " + other.template() + "'s reserve at " + x + ", " + z;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Asserts that no barracks reserve keeps this column, and that the named reserve it lies in is this group's. */
    private static void kept(GameTestHelper helper, String what, int x, int z, String group) {
        SeireiteiPlan.Reserve by = SeireiteiPlan.reservedBy(x, z);
        helper.assertTrue(by != null && by.group().equals(group), what + " lies in " + by + ", not a " + group + " reserve");
        for (SeireiteiPlan.Site site : SeireiteiPlan.sites("barracks")) {
            helper.assertFalse(site.reserve().contains(x, z), what + " lies in " + site.template() + "'s reserve");
        }
    }

    // ---- The plan ----

    /**
     * Thirteen barracks, one a division in the banner's order: the First's on the north gate's way in by the hill, its
     * gate up that way, the rest on the ring each 30° on from 15° with their gates to the centre column, all on chunk
     * lines. No site's box enters another's reserve, no site's reserve a named one but its own group's, no barracks' the
     * wall's, and the court's arrival, the smoke's lawn and the Academy's landing keep to theirs.
     */
    @GameTest(template = "empty")
    public static void barracksKeepToThePlan(GameTestHelper helper) {
        List<SeireiteiPlan.Site> sites = SeireiteiPlan.sites("barracks");
        helper.assertTrue(sites.size() == 13, "the plan has " + sites.size() + " barracks, not thirteen");
        for (int i = 0; i < sites.size(); i++) {
            SeireiteiPlan.Site site = sites.get(i);
            BoundingBox box = site.footprint();
            String name = DivisionBannerBlock.Division.values()[i].getSerializedName();
            double x = (box.minX() + box.maxX() + 1) / 2.0, z = (box.minZ() + box.maxZ() + 1) / 2.0;
            double r = Math.sqrt(x * x + z * z), bearing = Math.toDegrees(Math.atan2(x, -z));
            helper.assertTrue(site.template().equals(BleachMod.id("barracks/" + name)) && site.width() == 32
                    && site.depth() == 32 && Math.floorMod(box.minX(), 16) == 0 && Math.floorMod(box.minZ(), 16) == 0
                    && site.equals(SeireiteiPlan.siteAt("barracks", site.chunk().x, site.chunk().z)),
                    "the " + name + " barracks is " + site + " over " + box);
            if (i == 0) {
                SeireiteiPlan.Reserve own = site.reserve();
                helper.assertTrue(box.minX() + box.maxX() == -1 && z < 0 && site.rotation() == Rotation.NONE
                        && SeireiteiPlan.radius(0, own.maxZ()) >= FIRST_FROM
                        && SeireiteiPlan.radius(own.minX(), own.minZ()) < FIRST_TO,
                        "the First's barracks is not on the north gate's way in by the hill, facing north: " + own);
            } else {
                double off = Math.abs(((bearing - 15 - 30 * (i - 1)) % 360 + 540) % 360 - 180);
                helper.assertTrue(Math.abs(r - RING) < 10.0 && off < 2.0 && site.rotation().rotate(Direction.NORTH)
                        == inward(x, z), "the " + name + " barracks stands at r " + r + ", bearing " + bearing
                        + ", its gate " + site.rotation().rotate(Direction.NORTH));
            }
        }
        // The table holds the named reserves, then every site's; the hill's round one is its radius, column by column.
        List<SeireiteiPlan.Reserve> named = SeireiteiPlan.RESERVES.subList(0,
                SeireiteiPlan.RESERVES.size() - SeireiteiPlan.SITES.size());
        helper.assertTrue(SeireiteiPlan.RESERVES.subList(named.size(), SeireiteiPlan.RESERVES.size())
                .equals(SeireiteiPlan.SITES.stream().map(SeireiteiPlan.Site::reserve).toList()) && named.size() == 7,
                "the reserves are " + SeireiteiPlan.RESERVES);
        String differs = null;
        for (int x = -150; x < 150 && differs == null; x++) {
            for (int z = -150; z < 150 && differs == null; z++) {
                if (named.get(0).contains(x, z) != SeireiteiPlan.radius(x, z) < SeireiteiPlan.HILL_RESERVE_RADIUS) {
                    differs = x + ", " + z;
                }
            }
        }
        helper.assertTrue(differs == null, "the hill's reserve and its radius differ at " + differs);
        // V5's proof, column by column, asserted once for each site.
        for (SeireiteiPlan.Site site : SeireiteiPlan.SITES) {
            String breach = breach(site, named);
            helper.assertTrue(breach == null, site.template() + " " + breach);
        }
        BlockPos court = BlockPos.containing(TravelLogic.COURT), landing = BlockPos.containing(AcademyLocator.LANDING);
        kept(helper, "the court's arrival", court.getX(), court.getZ(), "court");
        kept(helper, "the smoke's lawn", LAWN_X, LAWN_Z, "gate");
        kept(helper, "the Academy's landing", landing.getX(), landing.getZ(), "academy");
        helper.succeed();
    }

    /**
     * The barracks' set is placed by the plan, in Soul Society only, at each site's chunk and none about it, and lays out
     * each site's template at its corner and turn on the lawn over its footprint, both gate posts hung with its own
     * division's banner facing out. Every site's template, the Academy's and the gate house's too, is as wide and deep as
     * the plan's row says.
     */
    @GameTest(template = "empty")
    public static void eachBarracksHangsItsOwnBanner(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        StructureSet set = soul.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(SET).value();
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        helper.assertTrue(set.placement() instanceof SitePlacement plan && plan.group().equals("barracks")
                        && set.structures().size() == 1 && set.structures().get(0).structure().is(STRUCTURE)
                        && structure instanceof SiteStructure laid && laid.group().equals("barracks"),
                "the barracks' set is not the barracks group's alone: " + set.placement() + ", " + structure);
        ChunkGeneratorStructureState state = soul.getChunkSource().getGeneratorState();
        helper.assertTrue(state.possibleStructureSets().stream().anyMatch(s -> s.is(SET))
                        && helper.getLevel().getChunkSource().getGeneratorState().possibleStructureSets().stream()
                        .noneMatch(s -> s.is(SET)), "the barracks' set is not Soul Society's alone");
        ChunkGenerator generator = soul.getChunkSource().getGenerator();
        List<SeireiteiPlan.Site> sites = SeireiteiPlan.sites("barracks");
        for (int i = 0; i < sites.size(); i++) {
            SeireiteiPlan.Site site = sites.get(i);
            ChunkPos home = site.chunk();
            for (ChunkPos near : ChunkPos.rangeClosed(home, 1).toList()) {
                helper.assertTrue(set.placement().isStructureChunk(state, near.x, near.z) == near.equals(home),
                        "chunk " + near + " is wrongly where " + site.template() + " starts or not");
            }
            StructureStart start = structure.generate(soul.registryAccess(), generator, generator.getBiomeSource(),
                    soul.getChunkSource().randomState(), soul.getStructureManager(), soul.getSeed(), home, 0, soul,
                    structure.biomes()::contains);
            BoundingBox foot = site.footprint();
            helper.assertTrue(start.isValid() && start.getPieces().get(0) instanceof SitePiece piece
                            && piece.getRotation() == site.rotation()
                            && piece.templatePosition().equals(new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z()))
                            && piece.getBoundingBox().minX() == foot.minX() && piece.getBoundingBox().maxX() == foot.maxX()
                            && piece.getBoundingBox().minZ() == foot.minZ() && piece.getBoundingBox().maxZ() == foot.maxZ(),
                    site.template() + " is laid out as " + start.getPieces() + ", not over " + foot);
            DivisionBannerBlock.Division division = DivisionBannerBlock.Division.values()[i];
            List<StructureBlockInfo> banners = banners(template(helper, soul, site), BlockPos.ZERO, Rotation.NONE);
            helper.assertTrue(banners.stream().map(StructureBlockInfo::pos).collect(Collectors.toSet()).equals(BANNERS)
                    && banners.stream().allMatch(banner -> banner.state().getValue(DivisionBannerBlock.DIVISION) == division
                    && banner.state().getValue(DivisionBannerBlock.FACING) == Direction.NORTH),
                    site.template() + " hangs " + banners + ", not the " + division + "'s on its gate posts");
        }
        for (SeireiteiPlan.Site site : SeireiteiPlan.SITES) {
            Vec3i size = template(helper, soul, site).getSize();
            helper.assertTrue(size.getX() == site.width() && size.getZ() == site.depth(),
                    site.template() + " is " + size.toShortString() + ", not " + site.width() + " by " + site.depth());
        }
        helper.succeed();
    }

    /**
     * Each turn of a template turns its banners with it, as worldgen sets them: each faces the way the gate does, out of
     * the compound from the edge row on that side, hung on its post. Turned clockwise, the gate faces east.
     */
    @GameTest(template = "empty")
    public static void aTurnedBarracksHangsItsBannersOutward(GameTestHelper helper) {
        StructureTemplate template = template(helper, SiteTests.soul(helper), SeireiteiPlan.sites("barracks").get(1));
        for (Rotation turn : List.of(Rotation.CLOCKWISE_90, Rotation.NONE, Rotation.CLOCKWISE_180,
                Rotation.COUNTERCLOCKWISE_90)) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turn);
            BoundingBox box = template.getBoundingBox(settings, BlockPos.ZERO);
            Direction out = turn.rotate(Direction.NORTH);
            Set<BlockPos> posts = template.filterBlocks(BlockPos.ZERO, settings, Blocks.STRIPPED_DARK_OAK_LOG).stream()
                    .map(StructureBlockInfo::pos).collect(Collectors.toSet());
            List<StructureBlockInfo> banners = banners(template, BlockPos.ZERO, turn);
            for (StructureBlockInfo banner : banners) {
                BlockPos at = banner.pos();
                helper.assertTrue(banner.state().getValue(DivisionBannerBlock.FACING) == out && box.isInside(at)
                                && !box.isInside(at.relative(out)) && posts.contains(at.relative(out.getOpposite())),
                        "turned " + turn + ", a banner at " + at.toShortString() + " faces "
                                + banner.state().getValue(DivisionBannerBlock.FACING) + ", not " + out + " off its post");
            }
            helper.assertTrue(banners.size() == 2 && (turn != Rotation.CLOCKWISE_90 || out == Direction.EAST
                            && banners.stream().allMatch(banner -> banner.pos().getX() == box.maxX())),
                    "turned " + turn + ", the template hangs " + banners);
        }
        helper.succeed();
    }

    // ---- In the world ----

    /**
     * The test server makes no structures, so the Tenth's chunks keep their lawn and no stamp builds it, as in a world
     * made before the barracks. A stamp of the test's own then lays it in the real Soul Society by the same piece that
     * worldgen and the stamps use, turned clockwise with both banners at its east gate facing out, and takes its blocks,
     * start and references away when the test ends, however it ends.
     */
    @GameTest(template = "empty", batch = "barracks", timeoutTicks = 100)
    public static void aCompoundStandsAtItsSite(GameTestHelper helper) {
        SeireiteiPlan.Site site = SeireiteiPlan.sites("barracks").get(TENTH);
        ShakonmakuTests.Cleanup held = ShakonmakuTests.hold(helper, site.chunk().x, site.chunk().z);
        ServerLevel soul = held.soul;
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        helper.assertTrue(structure != null, "the structure " + STRUCTURE.location() + " did not load");
        StructureTemplate template = template(helper, soul, site);
        SiteStamp stamp = new SiteStamp(BleachWorlds.SOUL_SOCIETY, STRUCTURE, "A test's barracks", List.of(site),
                template.getSize(), List.of(), SiteStamp.Placed.NOTHING);
        BlockPos corner = new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z());
        List<StructureBlockInfo> banners = banners(template, corner, site.rotation());
        held.atEnd(() -> clear(soul, structure, stamp));
        ChunkPos home = site.chunk();
        // Any stamp check the loads queued runs within a tick or two.
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    StructureStart none = soul.getChunk(home.x, home.z).getStartForStructure(structure);
                    helper.assertTrue((none == null || !none.isValid()) && stamp.covered(0).stream()
                            .allMatch(pos -> soul.getChunk(pos.x, pos.z).getReferencesForStructure(structure).isEmpty())
                            && banners.stream().allMatch(banner -> soul.getBlockState(banner.pos()).isAir()),
                            "the Tenth's barracks stands in chunks made with structures off");
                    helper.assertTrue(stamp.ensureBuilt(soul, 0), "the test's stamp built nothing of the Tenth's");
                    StructureStart start = soul.getChunk(home.x, home.z).getStartForStructure(structure);
                    helper.assertTrue(start != null && start.isValid() && stamp.covered(0).size() == 4
                                    && stamp.covered(0).stream().allMatch(pos -> soul.getChunk(pos.x, pos.z)
                                    .getReferencesForStructure(structure).contains(home.toLong())),
                            "the Tenth's barracks has no valid start, or a chunk of its four no reference");
                    for (StructureBlockInfo banner : banners) {
                        helper.assertTrue(soul.getBlockState(banner.pos()).equals(banner.state())
                                        && banner.state().getValue(DivisionBannerBlock.FACING) == Direction.EAST,
                                "the Tenth's banner at " + banner.pos().toShortString() + " is "
                                        + soul.getBlockState(banner.pos()) + ", not " + banner.state());
                    }
                })
                .thenSucceed();
    }

    /**
     * Sets every block of the stamp's compound to air, telling no neighbour, so neither half of a door drops it, and
     * takes away its start and its chunks' references.
     */
    private static void clear(ServerLevel soul, Structure structure, SiteStamp stamp) {
        BoundingBox box = stamp.box(0, SeireiteiPlan.PLATEAU_Y);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            soul.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (ChunkPos pos : stamp.covered(0)) {
            LevelChunk chunk = soul.getChunk(pos.x, pos.z);
            Map<Structure, LongSet> others = new HashMap<>(chunk.getAllReferences());
            others.remove(structure);
            chunk.setAllReferences(others);
        }
        ChunkPos home = stamp.startChunk(0);
        soul.getChunk(home.x, home.z).setStartForStructure(structure, StructureStart.INVALID_START);
    }
}
