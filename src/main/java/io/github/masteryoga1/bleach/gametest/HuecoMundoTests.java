package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hueco Mundo: its world on the test server, held at midnight on the Overworld's day count; its ground, read from its
 * own files and worked out by the generator's own code at three seeds, and as the real world generates it; its biomes
 * by radius and depth; and its blocks, each with its item, drop, tool and tags, white sand's fall, and what is crafted
 * and smelted.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HuecoMundoTests {
    /** How near a computed value must come to the plan's: rounding only. */
    private static final double EXACT = 1.0E-6;
    /** Three seeds, so that a check passed by one seed's luck would show. */
    private static final long[] SEEDS = {0L, 4242L, 90210L};
    /** Hueco Mundo's biomes: the dunes, the cavern under them, Las Noches' grounds and Las Noches. */
    private static final List<String> BIOMES = List.of("hueco_mundo", "forest_of_menos", "las_noches_grounds", "las_noches");

    private HuecoMundoTests() {
    }

    @GameTest(template = "empty")
    public static void huecoMundoTypeAndWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel hueco = server.getLevel(BleachWorlds.HUECO_MUNDO);
        helper.assertTrue(hueco != null && BleachWorlds.level(server, BleachWorlds.HUECO_MUNDO) == hueco
                        && hueco.dimensionTypeRegistration().is(BleachWorlds.HUECO_MUNDO_TYPE)
                        && hueco.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator
                        && generator.generatorSettings().is(BleachWorlds.HUECO_MUNDO_NOISE),
                "the test server has no Hueco Mundo made from its files");
        DimensionType type = hueco.dimensionType();
        helper.assertTrue(type.ultraWarm() && !type.natural() && type.coordinateScale() == 1.0 && type.hasSkyLight()
                        && !type.hasCeiling() && type.ambientLight() == 0.2F
                        && type.fixedTime().equals(OptionalLong.of(18_000L)) && !type.piglinSafe() && type.bedWorks()
                        && !type.respawnAnchorWorks() && !type.hasRaids() && type.minY() == HuecoMundoPlan.MIN_Y
                        && type.height() == HuecoMundoPlan.HEIGHT && type.logicalHeight() == HuecoMundoPlan.HEIGHT
                        && type.infiniburn().equals(BlockTags.INFINIBURN_OVERWORLD)
                        && type.effectsLocation().equals(BleachMod.id("hueco_mundo"))
                        && type.monsterSpawnLightTest().getMaxValue() == 0 && type.monsterSpawnBlockLightLimit() == 0,
                "Hueco Mundo's type is not a hot, unnatural world from y 0 to 256 held at midnight, with its own sky");
        // Held at midnight, so neither day nor night, the sky darkened by 11 of 15, with the Overworld's day and moon.
        helper.assertTrue(!hueco.isDay() && !hueco.isNight() && hueco.getSkyDarken() == 11
                        && Math.abs(hueco.getTimeOfDay(1.0F) - 0.5F) < 1.0E-6F
                        && hueco.getDayTime() == server.overworld().getDayTime()
                        && hueco.getMoonPhase() == server.overworld().getMoonPhase(),
                "Hueco Mundo is not held at midnight on the Overworld's day count");
        // A bed there neither blows up nor sets a spawn: it turns the sleeper away before any spawn is set, saying nothing
        // (a giant Menos is told it is far too large, as anywhere); a charged anchor blows up, as in the Overworld.
        helper.assertTrue(BedBlock.canSetSpawn(hueco) && Player.BedSleepingProblem.NOT_POSSIBLE_HERE.getMessage() == null
                && !RespawnAnchorBlock.canSetSpawn(hueco), "a bed or an anchor in Hueco Mundo does not work as planned");
        // Water poured there boils away, as in the Nether, and not in the Overworld.
        FluidStack bucket = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
        FluidType water = NeoForgeMod.WATER_TYPE.value();
        helper.assertTrue(water.isVaporizedOnPlacement(hueco, BlockPos.ZERO, bucket)
                        && !water.isVaporizedOnPlacement(helper.getLevel(), BlockPos.ZERO, bucket),
                "water poured in Hueco Mundo does not boil away, or water poured in the Overworld does");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void huecoMundoGroundFollowsThePlan(GameTestHelper helper) {
        LevelStem stem = SoulSocietyTests.stem(helper, "hueco_mundo");
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) stem.generator();
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        helper.assertTrue(!settings.isAquifersEnabled() && !settings.oreVeinsEnabled() && settings.defaultFluid().isAir()
                        && settings.defaultBlock().is(ModBlocks.WHITE_SANDSTONE.get()),
                "Hueco Mundo's noise settings hold water or ore veins, or a ground other than white sandstone");
        int level = HuecoMundoPlan.LEVEL_Y, low = HuecoMundoPlan.DUNE_LOW_Y, high = HuecoMundoPlan.DUNE_HIGH_Y;
        int floor = HuecoMundoPlan.CAVERN_FLOOR_Y, ceiling = HuecoMundoPlan.CAVERN_CEILING_Y;
        for (long seed : SEEDS) {
            RandomState noise = SoulSocietyTests.noise(helper, BleachWorlds.HUECO_MUNDO_NOISE, seed);
            // On the level ground the density is exactly the height left to y 95.5; no noise reaches it.
            assertDensity(helper, noise, 0, level - 1, 0, 0.5);
            assertDensity(helper, noise, 199, level, 0, -0.5);
            assertDensity(helper, noise, 0, floor - 1, 0, level - 0.5 - (floor - 1));
            assertDensity(helper, noise, 0, ceiling, 0, level - 0.5 - ceiling);
            // Anywhere, for any seed: the rock under the cavern and the crust over it solid by at least the dunes' deepest
            // dip below the level ground (less a float spline's rounding), every column solid to y 87 and open from 108.
            double dip = level - low + EXACT;
            for (int x : new int[] {300, -1000, 2500}) {
                helper.assertTrue(SoulSocietyTests.density(noise, x, floor - 1, 0) >= level - 0.5 - (floor - 1) - dip
                                && SoulSocietyTests.density(noise, x, ceiling, 0) >= level - 0.5 - ceiling - dip
                                && SoulSocietyTests.density(noise, x, low - 1, 0) > 0.0
                                && SoulSocietyTests.density(noise, x, high, 0) < 0.0,
                        "the ground at x " + x + " breaks the plan's bounds for seed " + seed);
            }
            for (int[] at : new int[][] {{0, 0}, {150, 0}, {0, -150}, {140, 140}, {-141, -141}, {199, 0}, {0, -200}}) {
                SoulSocietyTests.assertStands(helper, generator, noise, at[0], at[1], level);
            }
            IntSummaryStatistics edge = SoulSocietyTests.ring(generator, noise, HuecoMundoPlan.LEVEL_RADIUS - 1);
            helper.assertTrue(edge.getMin() == level && edge.getMax() == level, "the level ground's edge: " + edge);
            // Halfway up the slope the dunes count for between 0.47 and 0.53 of their height, so 92 to 102.
            IntSummaryStatistics slope = SoulSocietyTests.ring(generator, noise, 216);
            helper.assertTrue(slope.getMin() >= 92 && slope.getMax() <= 102, "halfway up the slope: " + slope);
            for (int radius : new int[] {HuecoMundoPlan.SLOPE_END_RADIUS + 8, 400, 1000, 3000}) {
                IntSummaryStatistics dunes = SoulSocietyTests.ring(generator, noise, radius);
                helper.assertTrue(dunes.getMin() >= low && dunes.getMax() <= high, "the dunes at r " + radius + ": " + dunes);
                // By the noise's spread, not a bound: the prototype gives this ring a spread of 20 at each of the three
                // seeds, and never under 17 over 300 stand-in seeds.
                helper.assertTrue(radius != 1000 || dunes.getMax() - dunes.getMin() >= 10, "flat dunes at r 1,000: " + dunes);
            }
            // The cavern is the trunk noise less the plan's threshold, the same from floor to ceiling. By the noise's
            // spread, not a bound: the prototype makes 6.2% to 8.5% of these 4,096 columns, 8 blocks apart, trunk at the
            // three seeds, and 5.4% to 11.1% over 300 stand-in seeds.
            NormalNoise trunkNoise =
                    noise.getOrCreateNoise(ResourceKey.create(Registries.NOISE, BleachMod.id("hueco_mundo/trunks")));
            int trunks = 0;
            for (int i = 0; i < 4096; i++) {
                int x = 2000 + i % 64 * 8, z = i / 64 * 8;
                double bottom = SoulSocietyTests.density(noise, x, floor, z);
                double cut = trunkNoise.getValue(x, 0.0, z) - HuecoMundoPlan.TRUNK_THRESHOLD;
                helper.assertTrue(bottom == SoulSocietyTests.density(noise, x, ceiling - 1, z)
                                && Math.abs(bottom - cut) < EXACT,
                        "the cavern at (" + x + ", " + z + ") is not the trunk noise less 0.45 from floor to ceiling");
                trunks += bottom > 0.0 ? 1 : 0;
            }
            helper.assertTrue(trunks >= 4096 * 3 / 100 && trunks <= 4096 * 14 / 100,
                    trunks + " of 4,096 columns are trunk at seed " + seed);
        }
        ServerLevel hueco = helper.getLevel().getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        helper.assertTrue(hueco != null, "the test server has no Hueco Mundo");
        // Three chunks made in full where they are, trees and all: the dunes at r 1,000, the centre, and the level
        // ground's edge, where the smoke test arrives. Each keeps only the one-tick ticket loading puts on it.
        for (ChunkPos chunk : List.of(new ChunkPos(62, 0), new ChunkPos(0, 0), new ChunkPos(0, 12))) {
            LevelChunk made = hueco.getChunk(chunk.x, chunk.z);
            for (int i = 0; i < 256; i++) {
                assertColumn(helper, made, chunk.getMinBlockX() + i % 16, chunk.getMinBlockZ() + i / 16);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void huecoMundoBiomesByDepthAndRadius(GameTestHelper helper) {
        LevelStem stem = SoulSocietyTests.stem(helper, "hueco_mundo");
        helper.assertTrue(stem.type().is(BleachWorlds.HUECO_MUNDO_TYPE)
                        && stem.generator() instanceof NoiseBasedChunkGenerator generator
                        && generator.generatorSettings().is(BleachWorlds.HUECO_MUNDO_NOISE)
                        && generator.getBiomeSource() instanceof MultiNoiseBiomeSource,
                "Hueco Mundo's world is not its type and noise settings over a multi-noise biome source");
        // Throws on a feature order cycle among the biomes the world can hold, as the first chunk decorated there would.
        stem.generator().validate();
        BiomeSource source = stem.generator().getBiomeSource();
        Set<String> possible = source.possibleBiomes().stream().map(Holder::getRegisteredName).collect(Collectors.toSet());
        helper.assertTrue(possible.equals(BIOMES.stream().map(name -> "bleach:" + name).collect(Collectors.toSet())),
                "Hueco Mundo can hold " + possible);
        Registry<Biome> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (String name : BIOMES) {
            Biome biome = registry.getOrThrow(ResourceKey.create(Registries.BIOME, BleachMod.id(name)));
            // No rain or snow, no caves cut, nothing spawning, the dunes' quartz trees the only feature, a black sky, and
            // a fog all but black, the cavern's a little warmer.
            helper.assertFalse(biome.hasPrecipitation(), name + " has rain or snow");
            for (GenerationStep.Carving step : GenerationStep.Carving.values()) {
                helper.assertFalse(biome.getGenerationSettings().getCarvers(step).iterator().hasNext(), name + " is carved");
            }
            for (MobCategory category : MobCategory.values()) {
                helper.assertTrue(biome.getMobSettings().getMobs(category).isEmpty(), name + " has " + category + " spawns");
            }
            List<String> features = biome.getGenerationSettings().features().stream().flatMap(HolderSet::stream)
                    .map(Holder::getRegisteredName).toList();
            helper.assertTrue(features.equals(name.equals("hueco_mundo") ? List.of("bleach:quartz_tree") : List.of()),
                    name + " places " + features);
            helper.assertTrue(biome.getSkyColor() == 0 && biome.getFogColor() == (name.equals("forest_of_menos")
                    ? 0x0E0C0A : 0x050507), name + "'s sky or fog is not its planned near-black");
        }
        // {x, y, z, biome}, on the 4-block grid the biomes are stored on: Las Noches under its dome top, its grounds round
        // and above it, the dunes from r 216, and the forest under them all, whatever the radius. The four after the first
        // six are the cells nearest each edge on either side: r 63.94 and 64.07, then 215.96 and 216.02.
        int[][] points = {{0, 96, 0, 3}, {60, 140, 0, 3}, {0, 140, 0, 3}, {0, 144, 0, 2}, {0, 252, 0, 2}, {64, 96, 0, 2},
                {36, 96, 52, 3}, {-64, 96, 8, 2}, {144, 96, 160, 2}, {56, 96, 208, 0}, {0, 96, 212, 2}, {216, 96, 0, 0},
                {-1000, 100, 0, 0}, {6000, 200, 0, 0}, {300, 80, 0, 0}, {0, 76, 0, 1}, {300, 40, 0, 1}, {0, 0, 0, 1}};
        int reach = QuartPos.fromBlock(HuecoMundoPlan.GROUNDS_RADIUS) + 4;
        // The climate reads no noise, so every seed's is the same; three are read all the same.
        for (long seed : SEEDS) {
            RandomState noise = SoulSocietyTests.noise(helper, BleachWorlds.HUECO_MUNDO_NOISE, seed);
            for (int[] at : points) {
                Holder<Biome> found = source.getNoiseBiome(QuartPos.fromBlock(at[0]), QuartPos.fromBlock(at[1]),
                        QuartPos.fromBlock(at[2]), noise.sampler());
                helper.assertTrue(found.is(BleachMod.id(BIOMES.get(at[3]))),
                        "(" + at[0] + ", " + at[1] + ", " + at[2] + ") is " + found.getRegisteredName() + " at seed " + seed);
            }
            // Every cell at y 96 out past the dunes' edge: Las Noches where its sample lies within r 64, its grounds from
            // there to r 216 and the dunes beyond, exactly, since the climate's ranges leave a quantum between them and
            // no sample reads two.
            for (int qx = -reach; qx <= reach; qx++) {
                for (int qz = -reach; qz <= reach; qz++) {
                    double r = HuecoMundoPlan.radius(QuartPos.toBlock(qx), QuartPos.toBlock(qz));
                    String expected = r < HuecoMundoPlan.LAS_NOCHES_RADIUS ? "las_noches"
                            : r < HuecoMundoPlan.GROUNDS_RADIUS ? "las_noches_grounds" : "hueco_mundo";
                    Holder<Biome> found = source.getNoiseBiome(qx, QuartPos.fromBlock(HuecoMundoPlan.LEVEL_Y), qz,
                            noise.sampler());
                    helper.assertTrue(found.is(BleachMod.id(expected)), "the cell at r " + r + " holds "
                            + found.getRegisteredName() + ", not bleach:" + expected + ", at seed " + seed);
                }
            }
            // The router's climate: warmth the radius over the climate scale, clamped at 2; depth 1 below the cavern's
            // ceiling and 0 from it; humidity 0 below the dome top and 1 from it.
            NoiseRouter router = noise.router();
            double warmth = HuecoMundoPlan.radius(1000, 0) / HuecoMundoPlan.CLIMATE_SCALE;
            assertValue(helper, router.temperature(), 1000, HuecoMundoPlan.LEVEL_Y, warmth);
            assertValue(helper, router.temperature(), 6000, HuecoMundoPlan.LEVEL_Y, 2.0);
            assertValue(helper, router.depth(), 0, HuecoMundoPlan.CAVERN_CEILING_Y - 1, 1.0);
            assertValue(helper, router.depth(), 0, HuecoMundoPlan.CAVERN_CEILING_Y, 0.0);
            assertValue(helper, router.vegetation(), 0, HuecoMundoPlan.DOME_TOP_Y - 1, 0.0);
            assertValue(helper, router.vegetation(), 0, HuecoMundoPlan.DOME_TOP_Y, 1.0);
        }
        // A block reads a cell at most √50 blocks off and a branch reaches 5 from its trunk, so no tree grows within
        // 203.9 of the centre, outside the level ground.
        helper.assertTrue(HuecoMundoPlan.GROUNDS_RADIUS - Math.sqrt(50.0) - HuecoMundoPlan.TREE_REACH
                > HuecoMundoPlan.LEVEL_RADIUS, "a quartz tree could reach the level ground");
        // The dunes' tree grown by hand on the level ground, white sand at y 95 for any seed and far from the chunks the
        // ground test reads: it reports that it grew, with an upright Hueco quartz trunk at least 3 tall, branches, and
        // no leaves, its foliage only air. Then taken away, whatever the checks found.
        ServerLevel hueco = helper.getLevel().getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        helper.assertTrue(hueco != null, "the test server has no Hueco Mundo");
        Block quartz = ModBlocks.HUECO_QUARTZ.get();
        BlockPos root = new BlockPos(-150, HuecoMundoPlan.LEVEL_Y, 0);
        Iterable<BlockPos> room = BlockPos.betweenClosed(root.offset(-6, 0, -6), root.offset(6, 12, 6));
        ConfiguredFeature<?, ?> tree = hueco.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, BleachMod.id("quartz_tree")));
        try {
            helper.assertTrue(hueco.getBlockState(root.below()).is(ModBlocks.WHITE_SAND.get())
                            && tree.place(hueco, hueco.getChunkSource().getGenerator(), hueco.getRandom(), root),
                    "the quartz tree would not grow on white sand");
            int branches = 0;
            for (BlockPos pos : room) {
                BlockState state = hueco.getBlockState(pos);
                helper.assertFalse(state.is(BlockTags.LEAVES), "the quartz tree grew leaves at " + pos.toShortString());
                branches += state.is(quartz) && (pos.getX() != root.getX() || pos.getZ() != root.getZ()) ? 1 : 0;
            }
            for (int up = 0; up < 3; up++) {
                BlockState trunk = hueco.getBlockState(root.above(up));
                helper.assertTrue(trunk.is(quartz) && trunk.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y,
                        "the quartz tree's trunk is " + trunk + " " + up + " above its root");
            }
            helper.assertTrue(branches >= 2, "the quartz tree has no branch");
        } finally {
            for (BlockPos pos : room) {
                if (hueco.getBlockState(pos).is(quartz)) {
                    hueco.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void huecoMundoBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A spot in the test's own box, for the calls that ask where the block would be; none of them reads it.
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        List<String> names = List.of("white_sand", "white_sandstone", "smooth_white_sandstone", "hueco_quartz",
                "hueco_quartz_bricks");
        for (String name : names) {
            ResourceLocation id = BleachMod.id(name);
            Block block = BuiltInRegistries.BLOCK.get(id);
            BlockState state = block.defaultBlockState();
            boolean shovel = name.equals("white_sand");
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id) && block.asItem() != Items.AIR
                    && BuiltInRegistries.ITEM.getKey(block.asItem()).equals(id), id + " is not a block with its item");
            // Its loot table gives one of itself, as its kin's does; without the right tool a player gets none of the
            // four stone ones, which is the tool check below, not the table's.
            List<ItemStack> drops = Block.getDrops(state, level, at, null);
            helper.assertTrue(drops.size() == 1 && ItemStack.matches(drops.get(0), new ItemStack(block)),
                    id + " drops " + drops);
            helper.assertTrue(state.getLightEmission(level, at) == 0, id + " glows");
            // White sand needs no tool, as sand does; the stone ones need a pickaxe, and the least of them will do.
            ItemStack tool = new ItemStack(shovel ? Items.WOODEN_SHOVEL : Items.WOODEN_PICKAXE);
            helper.assertTrue(state.is(shovel ? BlockTags.MINEABLE_WITH_SHOVEL : BlockTags.MINEABLE_WITH_PICKAXE)
                            && state.requiresCorrectToolForDrops() != shovel && tool.isCorrectToolForDrops(state),
                    id + " is not mined as its kin is");
        }
        BlockState sand = ModBlocks.WHITE_SAND.get().defaultBlockState();
        helper.assertTrue(BuiltInRegistries.BLOCK.get(BleachMod.id("white_sand")) instanceof ColoredFallingBlock falling
                        && falling.getDustColor(sand, level, at) == ModBlocks.WHITE_SAND_DUST
                        && sand.getSoundType(level, at, null) == SoundType.SAND,
                "white sand is not a falling block with white dust and sand's sound");
        // A camel treads it and an Enderman carries it as sand; nothing that grows on sand takes root in it.
        helper.assertTrue(sand.is(BlockTags.CAMEL_SAND_STEP_SOUND_BLOCKS) && sand.is(BlockTags.ENDERMAN_HOLDABLE)
                && !sand.is(BlockTags.SAND), "white sand is not trodden or carried as sand, or is planted as sand");
        BlockState quartz = ModBlocks.HUECO_QUARTZ.get().defaultBlockState();
        helper.assertTrue(quartz.getBlock() instanceof RotatedPillarBlock
                && quartz.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y, "Hueco quartz is not an upright pillar");
        helper.assertTrue(ModBlocks.SMOOTH_WHITE_SANDSTONE.get().defaultDestroyTime() == 2.0F
                        && ModBlocks.SMOOTH_WHITE_SANDSTONE.get().getExplosionResistance() == 6.0F
                        && ModBlocks.WHITE_SANDSTONE.get().defaultDestroyTime() == 0.8F,
                "white sandstone is not as hard as sandstone, or smooth white sandstone as smooth sandstone");
        // Four white sand make a white sandstone, which smelts smooth; four Hueco quartz make four bricks; white sand
        // smelts to glass through vanilla's own recipe, as sand does.
        ItemStack grain = new ItemStack(ModItems.WHITE_SAND.get()), trunk = new ItemStack(ModItems.HUECO_QUARTZ.get());
        assertCrafts(helper, List.of(grain, grain, grain, grain), new ItemStack(ModItems.WHITE_SANDSTONE.get()));
        assertCrafts(helper, List.of(trunk, trunk, trunk, trunk), new ItemStack(ModItems.HUECO_QUARTZ_BRICKS.get(), 4));
        assertSmelts(helper, new ItemStack(ModItems.WHITE_SANDSTONE.get()), ModItems.SMOOTH_WHITE_SANDSTONE.get());
        assertSmelts(helper, grain, Items.GLASS);
        // The recipe book offers each of the mod's three once its ingredient is first held, as vanilla's are offered.
        for (String recipe : List.of("white_sandstone", "smooth_white_sandstone", "hueco_quartz_bricks")) {
            ResourceLocation unlock = BleachMod.id("recipes/building_blocks/" + recipe);
            helper.assertTrue(level.getServer().getAdvancements().get(unlock) != null, recipe + " has no recipe unlock");
        }
        helper.succeed();
    }

    /**
     * White sand over a block of air falls onto the white sandstone below it, and white sand laid on white sandstone stays.
     * Both columns start in the template's lowest layer, relative y 1, since y 0 is the layer of its structure block; the
     * sandstone there stands over air, as sandstone does not fall.
     */
    @GameTest(template = "empty")
    public static void whiteSandFalls(GameTestHelper helper) {
        Block sand = ModBlocks.WHITE_SAND.get(), sandstone = ModBlocks.WHITE_SANDSTONE.get();
        // Every block the test sets or the fall leaves, each column from the top down, so clearing it leaves no white
        // sand over air to fall again.
        List<BlockPos> laid = List.of(new BlockPos(1, 3, 1), new BlockPos(1, 2, 1), new BlockPos(1, 1, 1),
                new BlockPos(0, 2, 0), new BlockPos(0, 1, 0));
        Runnable clear = () -> {
            QuincyArtsTests.discardAll(helper.getEntities(EntityType.FALLING_BLOCK));
            laid.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
        };
        // Its tick comes 2 after it is set, and the one-block fall lands 7 ticks after that. The check ends the test
        // either way, long before its timeout, and clears up first.
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertBlockPresent(sand, 1, 2, 1);
                helper.assertBlockNotPresent(sand, 1, 3, 1);
                helper.assertBlockPresent(sand, 0, 2, 0);
                helper.assertEntityNotPresent(EntityType.FALLING_BLOCK);
            } finally {
                clear.run();
            }
            helper.succeed();
        });
        HollowBaitTests.orClear(() -> {
            helper.setBlock(1, 1, 1, sandstone);
            helper.setBlock(1, 3, 1, sand);
            helper.setBlock(0, 1, 0, sandstone);
            helper.setBlock(0, 2, 0, sand);
        }, clear).run();
    }

    /**
     * Asserts that this generated column is laid as the plan says: bedrock at y 0, white sandstone to the cavern, the
     * cavern all air or all upright Hueco quartz, the crust white sandstone, then white sandstone and above it white sand
     * to a white sand top among the dunes' heights, at 96 on the level ground, with nothing wet anywhere. Above the top
     * only a quartz tree may stand, and none on the level ground.
     */
    private static void assertColumn(GameTestHelper helper, LevelChunk chunk, int x, int z) {
        Block sand = ModBlocks.WHITE_SAND.get(), sandstone = ModBlocks.WHITE_SANDSTONE.get();
        Block quartz = ModBlocks.HUECO_QUARTZ.get();
        boolean levelGround = HuecoMundoPlan.radius(x, z) <= HuecoMundoPlan.LEVEL_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, HuecoMundoPlan.CAVERN_FLOOR_Y, z);
        boolean trunk = chunk.getBlockState(pos).is(quartz);
        int top = -1;
        boolean sandstoneAbove = false;
        for (int y = HuecoMundoPlan.MIN_Y + HuecoMundoPlan.HEIGHT - 1; y >= HuecoMundoPlan.MIN_Y; y--) {
            BlockState state = chunk.getBlockState(pos.setY(y));
            if (!state.getFluidState().isEmpty()) {
                helper.fail("Hueco Mundo holds " + state + " at " + pos.toShortString());
            }
            if (top < 0 && y >= HuecoMundoPlan.SAND_FROM_Y && (state.isAir() || state.is(quartz) && !levelGround)) {
                continue;
            }
            top = Math.max(top, y);
            boolean laid;
            if (y >= HuecoMundoPlan.SAND_FROM_Y) {
                laid = y == top ? state.is(sand) : state.is(sandstone) || state.is(sand) && !sandstoneAbove;
                sandstoneAbove |= state.is(sandstone);
            } else if (y >= HuecoMundoPlan.CAVERN_CEILING_Y) {
                laid = state.is(sandstone);
            } else if (y >= HuecoMundoPlan.CAVERN_FLOOR_Y) {
                laid = trunk ? state.is(quartz) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y
                        : state.isAir();
            } else if (y >= HuecoMundoPlan.BEDROCK_TOP_Y) {
                laid = state.is(sandstone);
            } else {
                laid = state.is(Blocks.BEDROCK) || y > HuecoMundoPlan.MIN_Y && state.is(sandstone);
            }
            if (!laid) {
                helper.fail("Hueco Mundo has " + state + " at " + pos.toShortString() + " against its plan");
            }
        }
        int stands = top + 1;
        helper.assertTrue(levelGround ? stands == HuecoMundoPlan.LEVEL_Y
                        : stands >= HuecoMundoPlan.DUNE_LOW_Y && stands <= HuecoMundoPlan.DUNE_HIGH_Y,
                "(" + x + ", " + z + ") stands at " + stands);
    }

    /** Asserts that the router's final density at this block is this value, for this seed's noise, to 1e-6. */
    private static void assertDensity(GameTestHelper helper, RandomState noise, int x, int y, int z, double expected) {
        SoulSocietyTests.near(helper, SoulSocietyTests.density(noise, x, y, z), expected,
                "the density at (" + x + ", " + y + ", " + z + ")");
    }

    /** Asserts that this climate function gives this value at (x, y, 0), to 1e-6. */
    private static void assertValue(GameTestHelper helper, DensityFunction function, int x, int y, double expected) {
        SoulSocietyTests.near(helper, function.compute(new DensityFunction.SinglePointContext(x, y, 0)), expected,
                "the climate at (" + x + ", " + y + ", 0)");
    }

    /** Asserts that these four stacks, two by two in a crafting grid, make exactly this. */
    private static void assertCrafts(GameTestHelper helper, List<ItemStack> grid, ItemStack expected) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 2, grid);
        ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(ItemStack.matches(made, expected), grid + " crafts " + made + ", not " + expected);
    }

    /** Asserts that this stack smelts in a furnace to one of this item. */
    private static void assertSmelts(GameTestHelper helper, ItemStack stack, Item expected) {
        ServerLevel level = helper.getLevel();
        SingleRecipeInput input = new SingleRecipeInput(stack);
        ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(made.is(expected) && made.getCount() == 1, stack + " smelts to " + made + ", not " + expected);
    }
}
