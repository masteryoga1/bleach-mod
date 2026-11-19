package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.KisukeUrahara;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarNpcLogic;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.UraharaShop;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Urahara Shop and its keeper (7n, 7n2): the shop's data, its ring's one spot and its template as worldgen places it,
 * in far chunks of the test world held while a test runs, and Kisuke Urahara's rules and words. Each test takes away the
 * keepers it set down, the players it joined and the blocks it placed, however it ends.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class UraharaShopTests {
    private static final TagKey<Biome> LAND = TagKey.create(Registries.BIOME, BleachMod.id("has_structure/urahara_shop"));
    /** No shore, mushroom island, cave, swamp, steep or broken ground or ice spikes, nor the vanilla tags' water. */
    private static final List<ResourceKey<Biome>> NEVER = List.of(Biomes.STONY_SHORE, Biomes.MUSHROOM_FIELDS,
            Biomes.DEEP_DARK, Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.SWAMP, Biomes.MANGROVE_SWAMP,
            Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.SNOWY_SLOPES, Biomes.GROVE,
            Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_SAVANNA,
            Biomes.ERODED_BADLANDS, Biomes.ICE_SPIKES);
    /** The sign over the door, before the template's turn. */
    private static final BlockPos SIGN = new BlockPos(6, 4, 1);

    private UraharaShopTests() {
    }

    /** Test 1. The shop's data: an Overworld jigsaw in a ring of count 1 and distance 8, preferring dry, gentle land only. */
    @GameTest(template = "empty")
    public static void uraharaShopDataLoads(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        Structure shop = access.registryOrThrow(Registries.STRUCTURE).get(UraharaShop.STRUCTURE);
        helper.assertTrue(shop instanceof JigsawStructure
                && shop.biomes().unwrapKey().equals(Optional.of(BiomeTags.IS_OVERWORLD)),
                "the shop is not a jigsaw that may stand anywhere in the Overworld: " + shop);
        StructureSet set = access.registryOrThrow(Registries.STRUCTURE_SET).get(UraharaShop.SET);
        helper.assertTrue(set != null && set.structures().size() == 1
                && set.structures().get(0).structure().is(UraharaShop.STRUCTURE)
                && set.placement() instanceof ConcentricRingsStructurePlacement rings && rings.count() == 1
                && rings.distance() == 8 && rings.preferredBiomes().unwrapKey().equals(Optional.of(LAND)),
                "the shop's set is not one ring of count 1 and distance 8, preferring its land");
        helper.assertTrue(access.registryOrThrow(Registries.TEMPLATE_POOL).containsKey(BleachMod.id("urahara_shop/start")),
                "the shop's template pool did not load");
        List<Holder<Biome>> land = access.registryOrThrow(Registries.BIOME).getTag(LAND)
                .map(tag -> tag.stream().toList()).orElse(List.of());
        helper.assertTrue(land.size() == 22, "the shop prefers " + land.size() + " biomes, not 22");
        for (Holder<Biome> biome : land) {
            helper.assertTrue(biome.is(BiomeTags.IS_OVERWORLD) && Stream.of(BiomeTags.IS_OCEAN, BiomeTags.IS_DEEP_OCEAN,
                    BiomeTags.IS_RIVER, BiomeTags.IS_BEACH).noneMatch(biome::is) && NEVER.stream().noneMatch(biome::is),
                    biome.getRegisteredName() + " is not dry, gentle Overworld land");
        }
        helper.succeed();
    }

    /**
     * Test 2. A default flat world, as the test world is, has no ring and so no spot. A world of plains on seed 0, its
     * state built as a world's own is and as a flat world holding the shop's set builds it, has one ring start, the shop's
     * spot, roughly 170 to 870 blocks out, where its site's corner is the chunk's.
     */
    @GameTest(template = "empty")
    public static void theShopStandsAtTheRingsOneSpot(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        helper.assertTrue(UraharaShop.spot(overworld, overworld.getChunkSource().getGeneratorState()) == null,
                "the default flat test world has a spot for the shop");
        RegistryAccess access = overworld.registryAccess();
        Holder<StructureSet> set = access.registryOrThrow(Registries.STRUCTURE_SET).getHolderOrThrow(UraharaShop.SET);
        ConcentricRingsStructurePlacement rings = (ConcentricRingsStructurePlacement) set.value().placement();
        FixedBiomeSource plains = new FixedBiomeSource(access.registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(Biomes.PLAINS));
        RandomState random = overworld.getChunkSource().randomState();
        // ChunkMap builds a world's own from every set the registry holds, its rings drawn from the world's seed.
        List<ChunkGeneratorStructureState> states = List.of(ChunkGeneratorStructureState.createForNormal(random, 0L,
                plains, access.lookupOrThrow(Registries.STRUCTURE_SET)),
                ChunkGeneratorStructureState.createForFlat(random, 0L, plains, Stream.of(set)));
        for (ChunkGeneratorStructureState state : states) {
            List<ChunkPos> ring = state.getRingPositionsFor(rings);
            ChunkPos spot = UraharaShop.spot(overworld, state);
            helper.assertTrue(ring != null && ring.size() == 1 && ring.get(0).equals(spot),
                    "the ring holds " + ring + ", and the shop's spot is " + spot);
            double out = Math.hypot(spot.getMiddleBlockX(), spot.getMiddleBlockZ());
            helper.assertTrue(out >= 170.0 && out <= 870.0, "the shop's spot " + spot + " is " + out + " blocks out");
            SeireiteiPlan.Site site = UraharaShop.site(overworld, spot);
            helper.assertTrue(site != null && site.chunk().equals(spot) && site.x() == spot.getMinBlockX()
                    && site.z() == spot.getMinBlockZ(), "the shop's site at " + spot + " is " + site);
        }
        helper.succeed();
    }

    /**
     * Test 3. Laid out as worldgen lays it at four far chunks of the test world, turned each of the four ways, and placed
     * there twice chunk by chunk, the shop stands on the ground's top block and holds one keeper, at his spot with its
     * UUID and facing out of its door, body and head too; the sign over the door names it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theTemplateHoldsItsKeeper(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().get(UraharaShop.TEMPLATE).orElse(null);
        helper.assertTrue(template != null && template.getSize().equals(UraharaShop.SIZE),
                "the shop's template did not load at " + UraharaShop.SIZE);
        // Two chunks apart no two shops meet, as each turns about its chunk's corner into the chunks west and north.
        Map<Rotation, SeireiteiPlan.Site> turns = new EnumMap<>(Rotation.class);
        for (int x = 300; x < 364 && turns.size() < 4; x += 2) {
            SeireiteiPlan.Site site = UraharaShop.site(level, new ChunkPos(x, 260));
            helper.assertTrue(site != null, "the shop lays out nothing in chunk (" + x + ", 260)");
            turns.putIfAbsent(site.rotation(), site);
        }
        helper.assertTrue(turns.size() == 4, "the shops east of chunk (300, 260) turn only " + turns.keySet());
        Map<SeireiteiPlan.Site, StructureStart> shops = new LinkedHashMap<>();
        for (SeireiteiPlan.Site site : turns.values()) {
            ShakonmakuTests.Cleanup held = ShakonmakuTests.hold(helper, level, site.chunk().x, site.chunk().z);
            helper.assertTrue(site.y() == level.getHeight(Heightmap.Types.WORLD_SURFACE, site.x(), site.z()) - 1,
                    "the shop at " + site + " does not stand on the ground's top block");
            StructureStart start = startAt(level, site.chunk());
            putBackAtEnd(held, level, box(start));
            shops.put(site, start);
        }
        helper.startSequence()
                .thenWaitUntil(() -> shops.values().forEach(start -> covered(box(start)).forEach(chunk -> helper.assertTrue(
                        level.areEntitiesLoaded(chunk.toLong()), "chunk " + chunk + " has not loaded its creatures"))))
                .thenExecute(() -> shops.forEach((site, start) -> {
                    for (int set = 0; set < 2; set++) {
                        placeEach(level, start);
                    }
                    holdsItsKeeper(helper, level, site, box(start));
                }))
                .thenSucceed();
    }

    /**
     * Test 6. Kisuke Urahara is in neither spirit tag; no blow, a creative player's too, fall, blast, fire or magic harms
     * him, nothing moves, hunts or binds him, a soul's art passes him by and he never despawns; a keeper set down turned
     * faces that way, body and head too; two set at one spot are one; the void and {@code /kill} take him.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void uraharaIsUnharmedAndUnmoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<KisukeUrahara> type = ModEntities.KISUKE_URAHARA.get();
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(BleachMod.id("kisuke_urahara_spawn_egg"))
                && !type.is(SoulLogic.SPIRITS) && !type.is(SoulLogic.SPIRIT_SIGHTED),
                "Urahara has no egg, or is hidden from the unaware, or Pesquisa senses him");
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ShakonmakuTests.Cleanup held = ShakonmakuTests.hold(helper, level, chunk.x, chunk.z);
        Vec3 spot = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        KisukeUrahara first = fromTemplate(level, spot);
        KisukeUrahara second = fromTemplate(level, spot);
        KisukeUrahara urahara = helper.spawn(type, new Vec3(1.5, 1.0, 1.5));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(1.8, 1.0, 1.5));
        held.atEnd(() -> TravelTests.discard(urahara, zombie, first, second));
        ServerPlayer creative = TestPlayers.join(helper, GameType.CREATIVE, new Vec3(2.5, 1.0, 3.5));
        DamageSources sources = level.damageSources();
        for (DamageSource source : List.of(sources.mobAttack(zombie), sources.playerAttack(creative), sources.generic(),
                sources.fall(), sources.explosion(null, null), sources.lava(), sources.magic(), sources.wither())) {
            helper.assertFalse(urahara.hurt(source, 1000.0F), "Urahara was hurt by " + source.getMsgId());
        }
        Vec3 at = urahara.position();
        urahara.knockback(5.0, 1.0, 0.0);
        zombie.push(urahara);
        urahara.setDeltaMovement(1.0, 0.5, 0.0);
        helper.assertTrue(!urahara.isPushable() && !urahara.isPushedByFluid()
                && urahara.getPistonPushReaction() == PushReaction.IGNORE && !urahara.canUsePortal(false)
                && !urahara.canBeLeashed() && !urahara.startRiding(zombie, true)
                && !urahara.removeWhenFarAway(1.0E6) && urahara.requiresCustomPersistence(),
                "Urahara can be pushed, carried off or despawned");
        helper.assertTrue(!urahara.canBeSeenAsEnemy() && urahara.isInvulnerable() && !HollowLogic.isPrey(urahara)
                && !ArrancarNpcLogic.isPrey(urahara) && DevourLogic.preyOf(urahara) == null
                && !TargetingConditions.forCombat().test(zombie, urahara), "Urahara can be hunted");
        helper.assertTrue(!SpiritProjectiles.mayStrike(creative, urahara) && !KidoLogic.mayBind(creative, urahara),
                "a soul's art may strike Urahara, or a Bakudō bind him");
        // As a template turned a quarter clockwise sets its keeper down.
        float turned = first.rotate(Rotation.CLOCKWISE_90);
        first.moveTo(spot.x, spot.y, spot.z, turned, first.getXRot());
        helper.assertTrue(first.getYRot() == turned && first.yBodyRot == turned && first.getYHeadRot() == turned,
                "a keeper turned to " + turned + " faces " + first.getYRot() + ", his body " + first.yBodyRot
                        + " and his head " + first.getYHeadRot());
        helper.assertTrue(first.isInvulnerable() && level.addFreshEntity(first) && !level.addFreshEntity(second)
                && first.getUUID().equals(KisukeUrahara.spotId(level, BlockPos.containing(spot))),
                "two keepers set at one spot are not one, or a template's keeper can be hurt");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertTrue(urahara.isAlive() && urahara.getHealth() == urahara.getMaxHealth()
                    && urahara.position().distanceToSqr(at) < 1.0E-6, "Urahara was harmed or moved, to "
                    + urahara.position());
            helper.assertTrue(first.hurt(sources.fellOutOfWorld(), 4.0F), "the void does not reach Urahara");
            urahara.kill();
            helper.assertFalse(urahara.isAlive(), "/kill did not kill Urahara");
        }).thenSucceed();
    }

    /**
     * Test 7. He speaks to each kind of soul in its own view, with one of its two asides; to a joined player within
     * 8 blocks, unaware or a Hollow, but not to one further off or to a spectator.
     */
    @GameTest(template = "empty")
    public static void uraharaSpeaksToEachSoul(GameTestHelper helper) {
        SoulData visored = ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F);
        VisoredLogic.setRecord(visored, VisoredLogic.record(visored).withRootedAt(0L).withSubduedAt(1L));
        Map<SoulData, DialogueView> expected = new LinkedHashMap<>();
        expected.put(ShinigamiTests.soul(SoulState.HUMAN, 1.0F), DialogueView.SHOP_STRANGER);
        expected.put(ShinigamiTests.soul(SoulState.HUMAN,
                (float) (double) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD)), DialogueView.SHOP_AWARE);
        expected.put(ShinigamiTests.soul(SoulState.PLUS, 40.0F), DialogueView.SHOP_PLUS);
        expected.put(ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F), DialogueView.SHOP_SOUL);
        expected.put(ShinigamiTests.student(40.0F), DialogueView.SHOP_STUDENT);
        // A living Substitute, whose Shinigami power comes before the Human's awareness.
        expected.put(ShinigamiTests.substitute(60.0F), DialogueView.SHOP_SHINIGAMI);
        expected.put(QuincyTests.quincy(Bloodline.ECHT, 1, 40.0F), DialogueView.SHOP_QUINCY);
        expected.put(ShinigamiTests.soul(SoulState.HOLLOW, 40.0F), DialogueView.SHOP_HOLLOW);
        expected.put(ShinigamiTests.soul(SoulState.ARRANCAR, 40.0F), DialogueView.SHOP_ARRANCAR);
        expected.put(visored, DialogueView.SHOP_VISORED);
        expected.forEach((soul, view) -> helper.assertTrue(KisukeUrahara.viewFor(soul) == view,
                "Urahara shows " + KisukeUrahara.viewFor(soul) + " to a " + soul.state() + ", not " + view));
        KisukeUrahara urahara = helper.spawn(ModEntities.KISUKE_URAHARA.get(), new Vec3(1.5, 1.0, 1.5));
        try {
            ServerPlayer visitor = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(3.5, 1.0, 1.5));
            visitor.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, 1.0F));
            talks(helper, urahara, visitor, DialogueView.SHOP_STRANGER, 1);
            visitor.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 40.0F));
            talks(helper, urahara, visitor, DialogueView.SHOP_HOLLOW, 2);
            Vec3 far = helper.absoluteVec(new Vec3(10.5, 1.0, 1.5));
            visitor.teleportTo(far.x, far.y, far.z);
            helper.assertTrue(urahara.interact(visitor, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                    && TestPlayers.dialogues(visitor).size() == 2, "Urahara talked to a soul 9 blocks off");
            visitor.teleportTo(urahara.getX() + 2.0, urahara.getY(), urahara.getZ());
            visitor.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(urahara.interact(visitor, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                    && TestPlayers.dialogues(visitor).size() == 2, "Urahara talked to a spectator");
        } finally {
            urahara.discard();
        }
        helper.succeed();
    }

    // ---- Helpers ----

    /** The shop's start in this chunk, laid out as worldgen lays it there. */
    private static StructureStart startAt(ServerLevel level, ChunkPos chunk) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(UraharaShop.STRUCTURE).generate(
                level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), chunk, 0, level, biome -> true);
    }

    /** The box of this start's one piece, the template turned, without the margin the start keeps for its beard. */
    private static BoundingBox box(StructureStart start) {
        return start.getPieces().get(0).getBoundingBox();
    }

    /** The chunks this box covers. */
    private static List<ChunkPos> covered(BoundingBox box) {
        return ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4),
                new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).toList();
    }

    /** Places this start in each chunk its piece covers, the chunk's whole column at once, as worldgen places it. */
    private static void placeEach(ServerLevel level, StructureStart start) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (ChunkPos chunk : covered(box(start))) {
            start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), new BoundingBox(
                    chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                    level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk);
        }
    }

    /** Remembers every block of this box, and when the test ends takes away the keepers there and sets each block back. */
    private static void putBackAtEnd(ShakonmakuTests.Cleanup held, ServerLevel level, BoundingBox box) {
        Map<BlockPos, BlockState> before = new HashMap<>();
        BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                .forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
        held.atEnd(() -> {
            TravelTests.discard(level.getEntitiesOfClass(KisukeUrahara.class, AABB.of(box.inflatedBy(1)))
                    .toArray(new Entity[0]));
            // Telling no neighbour, so nothing the shop holds up drops when its support goes first.
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        });
    }

    /**
     * Asserts the shop at this site holds one keeper, at his spot with its UUID and facing out of the turned door, body
     * and head too, and that the sign over its door names it in the reader's own language.
     */
    private static void holdsItsKeeper(GameTestHelper helper, ServerLevel level, SeireiteiPlan.Site site, BoundingBox box) {
        BlockPos corner = new BlockPos(site.x(), site.y(), site.z());
        BlockPos at = corner.offset(UraharaShop.KEEPER.rotate(site.rotation()));
        List<KisukeUrahara> keepers = level.getEntitiesOfClass(KisukeUrahara.class, AABB.of(box));
        helper.assertTrue(keepers.size() == 1 && keepers.get(0).blockPosition().equals(at)
                && keepers.get(0).getUUID().equals(KisukeUrahara.spotId(level, at)),
                "turned " + site.rotation() + ", the shop holds " + keepers + ", not one keeper at " + at);
        // The door faces north before the turn. A template sets his yaw a whole number of turns off it, wrapping his 180
        // to -180 and taking 360 more for its mirror, so each yaw is read wrapped.
        float facing = Mth.wrapDegrees(site.rotation().rotate(Direction.NORTH).toYRot());
        KisukeUrahara keeper = keepers.get(0);
        helper.assertTrue(Mth.wrapDegrees(keeper.getYRot()) == facing && Mth.wrapDegrees(keeper.yBodyRot) == facing
                && Mth.wrapDegrees(keeper.getYHeadRot()) == facing, "turned " + site.rotation() + ", the keeper faces "
                + keeper.getYRot() + ", his body " + keeper.yBodyRot + " and his head " + keeper.getYHeadRot() + ", not "
                + facing);
        BlockPos sign = corner.offset(SIGN.rotate(site.rotation()));
        helper.assertTrue(level.getBlockEntity(sign) instanceof SignBlockEntity named && named.isWaxed()
                && named.getFrontText().getMessage(1, false).getContents() instanceof TranslatableContents name
                && name.getKey().equals("sign.bleach.urahara_shop"), "the sign at " + sign + " does not name the shop");
    }

    /** The visitor talks to him: his dialogue opens in this view, its one note an aside of that view's. */
    private static void talks(GameTestHelper helper, KisukeUrahara urahara, ServerPlayer visitor, DialogueView view,
                              int talks) {
        boolean opened = urahara.interact(visitor, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS;
        List<OpenDialoguePayload> shown = TestPlayers.dialogues(visitor);
        OpenDialoguePayload last = shown.isEmpty() ? null : shown.get(shown.size() - 1);
        String aside = "screen.bleach.dialogue." + urahara.dialogueKey() + "." + view.key() + ".aside";
        helper.assertTrue(opened && shown.size() == talks && last.entityId() == urahara.getId() && last.view() == view
                && last.notes().size() == 1 && last.notes().get(0).getContents() instanceof TranslatableContents note
                && (note.getKey().equals(aside + 1) || note.getKey().equals(aside + 2)),
                "talk " + talks + " opened " + shown + ", not " + view + " with an aside");
    }

    /** A keeper made from a template's data standing here, facing its door before the turn; not yet in the world. */
    private static KisukeUrahara fromTemplate(ServerLevel level, Vec3 at) {
        CompoundTag data = new CompoundTag();
        data.putString("id", BleachMod.id("kisuke_urahara").toString());
        data.putBoolean("Spot", true);
        ListTag pos = new ListTag();
        Stream.of(at.x, at.y, at.z).map(DoubleTag::valueOf).forEach(pos::add);
        data.put("Pos", pos);
        ListTag rotation = new ListTag();
        Stream.of(180.0F, 0.0F).map(FloatTag::valueOf).forEach(rotation::add);
        data.put("Rotation", rotation);
        return (KisukeUrahara) EntityType.create(data, level).orElseThrow();
    }
}
