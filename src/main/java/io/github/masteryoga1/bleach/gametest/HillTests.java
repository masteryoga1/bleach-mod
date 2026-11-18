package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModBlocks;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sōkyoku Hill's works and Sekkiseki's hold: the works keep to the plan and stand on the hill's end, and a soul shut in
 * the Senzaikyū's cell, but nowhere at a gate, is held down.
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
        var template = soul.getStructureManager().get(SeireiteiPlan.sites("hill").get(0).template());
        helper.assertTrue(template.isPresent(), "the works' template did not load");
        return template.get();
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
     * One hill site in the hill's reserve, its own start chunk, its box clear of the cavern and under the build's top, laid
     * out on the lawn turned half round, a marker in each chunk it covers, and no column within a birch's reach of it in
     * the hill's biome for any seed.
     */
    @GameTest(template = "empty")
    public static void theHillsWorksKeepToThePlan(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        List<SeireiteiPlan.Site> hill = SeireiteiPlan.sites("hill");
        SeireiteiPlan.Site site = hill.get(0);
        helper.assertTrue(hill.size() == 1 && HillWorks.STAMP.sites().equals(hill)
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
        // A birch's leaves reach 2 from its trunk, and a column's biome is one of 4 quarts about it (BiomeManager.getBiome).
        // The margin is small: the nearest quart reads 262 against the hill's 260 in Climate's units.
        BiomeSource biomes = generator.getBiomeSource();
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
