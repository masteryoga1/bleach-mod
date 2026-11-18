package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.gen.SiteStructure;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Soul Society's land, read from its own files and worked out by the generator's own code, so each check holds for any
 * seed: the plan's heights, the wall and its gates, the Rukongai roughening outward, and the biomes by radius.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SoulSocietyTests {
    private static final double EXACT = 1.0E-6;
    /** Two seeds, so that a check passed by one seed's luck would show. */
    private static final long[] SEEDS = {0L, 4242L};
    private static final LevelHeightAccessor HEIGHTS = LevelHeightAccessor.create(0, 320);
    /** Soul Society's biomes, from the centre outward. */
    private static final List<String> BIOMES =
            List.of("sokyoku_hill", "seireitei", "seireitei_wall", "rukongai", "rukongai_rough", "rukongai_wilds");

    private SoulSocietyTests() {
    }

    @GameTest(template = "empty")
    public static void soulSocietyDataLoads(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess access = server.registryAccess();
        DimensionType type = access.registryOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BleachWorlds.SOUL_SOCIETY_TYPE);
        helper.assertTrue(type.hasSkyLight() && !type.hasCeiling() && type.natural() && type.bedWorks()
                        && !type.respawnAnchorWorks() && !type.hasRaids() && !type.ultraWarm() && !type.piglinSafe()
                        && type.fixedTime().isEmpty() && type.minY() == 0 && type.height() == 320
                        && type.effectsLocation().equals(BuiltinDimensionTypes.OVERWORLD_EFFECTS),
                "Soul Society is not an open, natural world from y 0 to 320 on the Overworld's clock");
        List<Holder<Biome>> biomes = biomes(access);
        for (Holder<Biome> biome : biomes) {
            String name = biome.getRegisteredName();
            for (MobCategory category : MobCategory.values()) {
                boolean none = biome.value().getMobSettings().getMobs(category).isEmpty();
                helper.assertTrue(none != (name.startsWith("bleach:rukongai") && category == MobCategory.CREATURE),
                        name + (none ? " has no " : " has ") + category.getName() + " spawns");
            }
            // No rain or snow, so no lightning either: no trap horse, and no pig struck into a zombified piglin.
            helper.assertFalse(biome.value().hasPrecipitation(), name + " has rain or snow, and so lightning");
            boolean carved = biome.value().getGenerationSettings().getCarvers(GenerationStep.Carving.AIR).iterator()
                    .hasNext();
            helper.assertTrue(carved == (name.endsWith("_rough") || name.endsWith("_wilds")),
                    name + (carved ? " is carved" : " is not carved"));
        }
        int inside = features(biomes.get(1)), wall = features(biomes.get(2));
        helper.assertTrue(inside == 0 && wall == 1, "Seireitei has " + inside + " features and its wall " + wall);
        Set<Holder<Biome>> ours = Set.copyOf(biomes);
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        // Soul Society's own sites stand there by its plan (see SiteStructure); nothing else may, and so far two do, each
        // placed by a set of its own.
        for (Structure structure : structures) {
            helper.assertFalse(!(structure instanceof SiteStructure) && structure.biomes().stream().anyMatch(ours::contains),
                    structures.getKey(structure) + " can generate in Soul Society");
        }
        List<String> sited = structures.stream().filter(structure -> structure.biomes().stream().anyMatch(ours::contains))
                .map(structure -> String.valueOf(structures.getKey(structure))).sorted().toList();
        List<String> allowed =
                List.of(BleachMod.id("seireitei_academy").toString(), BleachMod.id("seireitei_gate").toString());
        helper.assertTrue(sited.equals(allowed),
                "the structures that may stand in Soul Society are " + sited + ", not the Academy and the gate houses");
        List<String> sets = access.registryOrThrow(Registries.STRUCTURE_SET).entrySet().stream()
                .filter(set -> set.getValue().structures().stream()
                        .anyMatch(entry -> sited.contains(entry.structure().getRegisteredName())))
                .map(set -> set.getKey().location().toString()).sorted().toList();
        helper.assertTrue(sets.equals(allowed), "the sets that place them are " + sets + ", not " + allowed);

        // The seam travel looks worlds up through: a stand-in while a test sets one, the real world again after.
        ServerLevel real = BleachWorlds.level(server, BleachWorlds.SOUL_SOCIETY);
        BleachWorlds.standIn(BleachWorlds.SOUL_SOCIETY, Level.OVERWORLD);
        try {
            helper.assertTrue(BleachWorlds.level(server, BleachWorlds.SOUL_SOCIETY) == server.overworld(),
                    "Soul Society's stand-in was not used");
        } finally {
            BleachWorlds.standIn(BleachWorlds.SOUL_SOCIETY, null);
        }
        helper.assertTrue(BleachWorlds.level(server, BleachWorlds.SOUL_SOCIETY) == real, "the stand-in outlasted its test");
        helper.assertTrue(BleachWorlds.level(server, BleachWorlds.HUECO_MUNDO) != null, "Hueco Mundo has no world");
        helper.assertTrue(BleachWorlds.isSpiritWorld(BleachWorlds.DANGAI) && !BleachWorlds.isSpiritWorld(Level.OVERWORLD),
                "the spirit worlds are not the mod's three");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void seireiteiStandsOnThePlan(GameTestHelper helper) {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) stem(helper, "soul_society").generator();
        int plateau = SeireiteiPlan.PLATEAU_Y, top = SeireiteiPlan.WALL_TOP_Y, mesa = SeireiteiPlan.MESA_TOP_Y;
        int floor = SeireiteiPlan.CAVERN_FLOOR_Y, ceiling = SeireiteiPlan.CAVERN_CEILING_Y, rim = SeireiteiPlan.CAVERN_RADIUS;
        int g = SeireiteiPlan.GATE_RADIUS, w = SeireiteiPlan.GATE_HALF_WIDTH;
        // {x, y, density}: a column is solid below its stand height by exactly half a block either side, and the cavern is
        // air from its floor up to its ceiling and out to its radius, and only there.
        double[][] exact = {{300, plateau - 1, 0.5}, {300, plateau, -0.5}, {50, mesa - 1, 0.5}, {50, mesa, -0.5},
                {20, floor, -1.0}, {20, floor - 1, 64.0}, {20, ceiling - 1, -1.0}, {20, ceiling, mesa - 0.5 - ceiling},
                {rim - 1, 80, -1.0}, {rim, 80, mesa - 0.5 - 80}};
        for (long seed : SEEDS) {
            RandomState noise = noise(helper, BleachWorlds.SOUL_SOCIETY_NOISE, seed);
            for (double[] at : exact) {
                int x = (int) at[0], y = (int) at[1];
                near(helper, density(noise, x, y, 0), at[2], "the density at (" + x + ", " + y + ", 0)");
            }
            assertStands(helper, generator, noise, 0, 0, mesa);
            assertStands(helper, generator, noise, SeireiteiPlan.MESA_RADIUS - 1, 0, mesa);
            assertStands(helper, generator, noise, SeireiteiPlan.MESA_RADIUS, 0, plateau);
            // Out along the diagonal: lawn up to the wall's inner face, then wall to its outer face.
            for (int d = 340; SeireiteiPlan.radius(d, d) < SeireiteiPlan.WALL_OUTER_RADIUS; d++) {
                assertStands(helper, generator, noise, d, d, SeireiteiPlan.inWall(d, d) ? top : plateau);
            }
            // Across each gate's line mid-wall: wall, a gap 16 blocks wide at lawn height, wall.
            for (int[] gate : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                int open = 0;
                for (int across = -w - 4; across < w + 4; across++) {
                    int x = gate[0] == 0 ? across : gate[0] > 0 ? g : -g - 1;
                    int z = gate[1] == 0 ? across : gate[1] > 0 ? g : -g - 1;
                    assertStands(helper, generator, noise, x, z, SeireiteiPlan.inWall(x, z) ? top : plateau);
                    open += SeireiteiPlan.inWall(x, z) ? 0 : 1;
                }
                helper.assertTrue(open == 2 * w, "a gate's gap is " + open + " blocks wide");
            }
            // Where the Academy and the Senkaimon court will stand: level lawn 48 blocks across.
            for (int[] site : new int[][] {{-192, 224}, {192, -288}}) {
                for (int i = 0; i < 49; i++) {
                    assertStands(helper, generator, noise, site[0] - 24 + i % 7 * 8, site[1] - 24 + i / 7 * 8, plateau);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rukongaiRoughensOutward(GameTestHelper helper) {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) stem(helper, "soul_society").generator();
        int plateau = SeireiteiPlan.PLATEAU_Y, depth = SeireiteiPlan.DISTRICT_DEPTH;
        for (long seed : SEEDS) {
            RandomState noise = noise(helper, BleachWorlds.SOUL_SOCIETY_NOISE, seed);
            IntSummaryStatistics outside = ring(generator, noise, SeireiteiPlan.WALL_OUTER_RADIUS + 10);
            IntSummaryStatistics fifth = ring(generator, noise, SeireiteiPlan.districtStart(5) + depth / 2);
            IntSummaryStatistics sixtieth = ring(generator, noise, SeireiteiPlan.districtStart(60) + depth / 2);
            IntSummaryStatistics wilds = ring(generator, noise, SeireiteiPlan.RUKONGAI_END_RADIUS + 16 * depth + 8);
            // Bounds for any seed: |N1| is at most 2.41, and A at most 0.82 at the cell corners the first ring is
            // smoothed from and 5 in district 5; and the noise settings hold every column at the floor or above.
            helper.assertTrue(outside.getMin() >= plateau - 2 && outside.getMax() <= plateau + 2,
                    "outside the wall: " + outside);
            helper.assertTrue(fifth.getMin() >= plateau - 12 && fifth.getMax() <= plateau + 12, "district 5: " + fifth);
            helper.assertTrue(wilds.getMin() >= SeireiteiPlan.RUKONGAI_FLOOR_Y, "the wilds fall below the floor: " + wilds);
            // By the noise's spread, not a bound: over 3,000 seeds the prototype never gave district 60 a spread under 25,
            // or the wilds a mean less than 10.9 above it.
            int spread5 = fifth.getMax() - fifth.getMin(), spread60 = sixtieth.getMax() - sixtieth.getMin();
            helper.assertTrue(spread60 >= 16 && spread60 > spread5, "district 5: " + fifth + "; district 60: " + sixtieth);
            helper.assertTrue(wilds.getAverage() >= sixtieth.getAverage() + 6.0, "the wilds: " + wilds + "; 60: " + sixtieth);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void biomesFollowTheRadius(GameTestHelper helper) {
        LevelStem stem = stem(helper, "soul_society");
        helper.assertTrue(stem.type().is(BleachWorlds.SOUL_SOCIETY_TYPE)
                        && stem.generator() instanceof NoiseBasedChunkGenerator generator
                        && generator.generatorSettings().is(BleachWorlds.SOUL_SOCIETY_NOISE)
                        && generator.getBiomeSource() instanceof MultiNoiseBiomeSource,
                "Soul Society's world is not its type and noise settings over a multi-noise biome source");
        // Throws on a feature order cycle among the biomes the world can hold, as the first chunk decorated there would.
        stem.generator().validate();
        BiomeSource source = stem.generator().getBiomeSource();
        Set<String> possible = source.possibleBiomes().stream().map(Holder::getRegisteredName).collect(Collectors.toSet());
        helper.assertTrue(possible.equals(BIOMES.stream().map(name -> "bleach:" + name).collect(Collectors.toSet())),
                "Soul Society can hold " + possible);
        RandomState noise = noise(helper, BleachWorlds.SOUL_SOCIETY_NOISE, SEEDS[1]);
        // {x, z, biome}, on the 4-block grid the biomes are stored on; two heights, since depth plays no part.
        int[][] points = {{20, 0, 0}, {300, 0, 1}, {SeireiteiPlan.GATE_RADIUS, 0, 2}, {0, -1000, 3}, {2400, 0, 4},
                {0, 3200, 5}, {6000, 0, 5}};
        for (int[] at : points) {
            for (int y : new int[] {40, 120}) {
                Holder<Biome> found = source.getNoiseBiome(QuartPos.fromBlock(at[0]), QuartPos.fromBlock(y),
                        QuartPos.fromBlock(at[1]), noise.sampler());
                helper.assertTrue(found.is(BleachMod.id(BIOMES.get(at[2]))),
                        "(" + at[0] + ", " + y + ", " + at[1] + ") is " + found.getRegisteredName());
            }
        }
        // Every 4 x 4 cell with a wall column in it stores the wall's biome, so every chunk with wall in it lays the wall.
        int reach = QuartPos.fromBlock(SeireiteiPlan.WALL_OUTER_RADIUS) + 1;
        for (int qx = -reach; qx <= reach; qx++) {
            for (int qz = -reach; qz <= reach; qz++) {
                int x = QuartPos.toBlock(qx), z = QuartPos.toBlock(qz);
                helper.assertTrue(IntStream.range(0, 16).noneMatch(i -> SeireiteiPlan.inWall(x + i % 4, z + i / 4))
                        || source.getNoiseBiome(qx, 16, qz, noise.sampler()).is(BleachMod.id("seireitei_wall")),
                        "the cell at (" + x + ", " + z + ") holds wall but not the wall's biome");
            }
        }
        // The warmth that chooses them: the radius over the climate scale, clamped at 2.
        DensityFunction warmth = noise.router().temperature();
        near(helper, warmth.compute(new DensityFunction.SinglePointContext(1000, 64, 0)),
                SeireiteiPlan.radius(1000, 0) / SeireiteiPlan.CLIMATE_SCALE, "the warmth at r 1,000");
        near(helper, warmth.compute(new DensityFunction.SinglePointContext(6000, 64, 0)), 2.0, "the warmth at r 6,000");
        helper.succeed();
    }

    /** One of the mod's worlds as its file reads, decoded as the server decodes world files; shared with Hueco Mundo's. */
    static LevelStem stem(GameTestHelper helper, String world) {
        MinecraftServer server = helper.getLevel().getServer();
        try (Reader reader = server.getResourceManager().getResourceOrThrow(BleachMod.id("dimension/" + world + ".json"))
                .openAsReader()) {
            return LevelStem.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                    JsonParser.parseReader(reader)).getOrThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A world's router and climate, from these noise settings, as a world of this seed has them. */
    static RandomState noise(GameTestHelper helper, ResourceKey<NoiseGeneratorSettings> settings, long seed) {
        return RandomState.create(helper.getLevel().registryAccess().asGetterLookup(), settings, seed);
    }

    private static List<Holder<Biome>> biomes(RegistryAccess access) {
        Registry<Biome> registry = access.registryOrThrow(Registries.BIOME);
        return BIOMES.stream().<Holder<Biome>>map(name -> registry.getHolderOrThrow(
                ResourceKey.create(Registries.BIOME, BleachMod.id(name)))).toList();
    }

    private static int features(Holder<Biome> biome) {
        return biome.value().getGenerationSettings().features().stream().mapToInt(HolderSet::size).sum();
    }

    static double density(RandomState noise, int x, int y, int z) {
        return noise.router().finalDensity().compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    /** The y a player would stand at on this column, as the generator works it out for a world of this seed. */
    private static int stand(NoiseBasedChunkGenerator generator, RandomState noise, int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, HEIGHTS, noise);
    }

    static void assertStands(GameTestHelper helper, NoiseBasedChunkGenerator generator, RandomState noise, int x,
            int z, int y) {
        int stands = stand(generator, noise, x, z);
        helper.assertTrue(stands == y, "(" + x + ", " + z + ") stands at " + stands + ", not " + y);
    }

    /** The stand heights of 64 columns evenly round a circle of this radius about the centre column. */
    static IntSummaryStatistics ring(NoiseBasedChunkGenerator generator, RandomState noise, int radius) {
        return IntStream.range(0, 64).map(i -> stand(generator, noise, Mth.floor(radius * Math.cos(Math.PI * i / 32)),
                Mth.floor(radius * Math.sin(Math.PI * i / 32)))).summaryStatistics();
    }

    static void near(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EXACT, what + " is " + actual + ", not " + expected);
    }
}
