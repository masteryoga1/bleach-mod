package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.TrainingDummy;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarNpcLogic;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.DummyDrill;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.world.AcademySite;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.SiteStamp;
import io.github.masteryoga1.bleach.world.gen.SitePiece;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Academy's grounds in Seireitei, the yard, the dojo and the Kidō range west and south of its hall, and the training
 * dummies standing in them: where the plan lays them, how a blow on a dummy trains, up to the day's cap, and that no harm
 * ends one or moves it, though {@code /kill} takes it away. Test 5's chunks build the grounds in the test world's Soul
 * Society, so it runs in a batch of its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class AcademyGroundsTests {
    /** The dummies each work's template sets, in {@link AcademySite#GROUNDS}' order: the yard's, the dojo's, the range's. */
    private static final List<Integer> DUMMIES = List.of(8, 2, 2);
    /** The markers of each work, in the same order. */
    private static final List<List<SiteStamp.Marker>> MARKERS = List.of(AcademySite.YARD_MARKERS,
            AcademySite.DOJO_MARKERS, AcademySite.RANGE_MARKERS);

    private AcademyGroundsTests() {
    }

    // ---- Helpers ----

    /** A student joined in survival with this in hand, and a dummy beside it. */
    private static TrainingDummy dummyBy(GameTestHelper helper, ServerPlayer player, ItemStack held) {
        player.setData(SoulAttachments.SOUL, ShinigamiTests.student(40.0F));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return helper.spawn(ModEntities.TRAINING_DUMMY.get(), new Vec3(2.5, 1.0, 1.5));
    }

    /** One blow of this player's that lands on the target, past its frames and the swing's own wait. */
    private static void blow(ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), 4.0F);
        player.tickCount += 10;
    }

    /** The dummies standing in this work's box in Soul Society. */
    private static List<TrainingDummy> standing(ServerLevel soul, SiteStamp stamp) {
        return soul.getEntities(ModEntities.TRAINING_DUMMY.get(), AABB.of(AcademySite.box(stamp)), dummy -> true);
    }

    /** Where this work's template is set from: its corner, on the lawn. */
    private static BlockPos corner(SiteStamp stamp) {
        SeireiteiPlan.Site site = stamp.sites().get(0);
        return new BlockPos(site.x(), SeireiteiPlan.PLATEAU_Y, site.z());
    }

    /** The chunks this work covers and those beside them, all of which its stamp waits to see loaded. */
    private static List<ChunkPos> ring(SiteStamp stamp) {
        return stamp.covered(0).stream().flatMap(pos -> ChunkPos.rangeClosed(pos, 1)).distinct().toList();
    }

    /** Each work of the grounds not yet standing with its chunks' creatures loaded, and what it lacks; empty once none. */
    private static List<String> lacking(ServerLevel soul, List<SiteStamp> stamps) {
        List<String> lacking = new ArrayList<>();
        for (SiteStamp stamp : stamps) {
            boolean stands = stamp.stands(soul, 0, corner(stamp));
            List<ChunkPos> joining = stamp.covered(0).stream().filter(pos -> !soul.areEntitiesLoaded(pos.toLong())).toList();
            if (!stands || !joining.isEmpty()) {
                lacking.add(stamp.sites().get(0).template().getPath() + (stands ? " stands" : " does not stand")
                        + ", its creatures still to load in " + joining);
            }
        }
        return lacking;
    }

    // ---- The plan ----

    /**
     * Test 1. The yard, dojo and range follow the hall in the plan's academy group, each on the lawn and alone in its
     * start chunk, its reserve inside the Academy's and west or south of the hall's row, so clear of the forecourt and
     * the landing too; each is laid out at its corner as its stamp reads it, with a marker in each chunk it covers, and
     * its template sets its dummies, each to take its UUID from its spot and none unhurtable.
     */
    @GameTest(template = "empty")
    public static void theGroundsFollowThePlan(GameTestHelper helper) {
        ServerLevel soul = SiteTests.soul(helper);
        List<SeireiteiPlan.Site> academy = SeireiteiPlan.sites("academy");
        helper.assertTrue(academy.size() == 4 && AcademySite.GROUNDS.stream().map(stamp -> stamp.sites().get(0))
                .toList().equals(academy.subList(1, 4)), "the academy's sites are " + academy);
        SeireiteiPlan.Reserve hall = academy.get(0).reserve(), grounds = SeireiteiPlan.RESERVES.get(1);
        Structure structure = soul.registryAccess().registryOrThrow(Registries.STRUCTURE).get(AcademySite.STRUCTURE);
        ChunkGenerator generator = soul.getChunkSource().getGenerator();
        for (int i = 0; i < DUMMIES.size(); i++) {
            SiteStamp stamp = AcademySite.GROUNDS.get(i);
            SeireiteiPlan.Site site = stamp.sites().get(0);
            SeireiteiPlan.Reserve own = site.reserve();
            helper.assertTrue(site.y() == SeireiteiPlan.Site.GROUND && SeireiteiPlan.SITES.stream()
                    .filter(other -> other.chunk().equals(site.chunk())).count() == 1, site + " is not alone on the lawn");
            helper.assertTrue(grounds.group().equals("academy") && grounds.contains(own.minX(), own.minZ())
                    && grounds.contains(own.maxX(), own.maxZ()) && (own.maxX() < hall.minX() || own.minZ() > hall.maxZ()),
                    site + "'s reserve leaves the Academy's or meets the hall's row");
            StructureStart start = structure.generate(soul.registryAccess(), generator, generator.getBiomeSource(),
                    soul.getChunkSource().randomState(), soul.getStructureManager(), soul.getSeed(), site.chunk(), 0,
                    soul, structure.biomes()::contains);
            BoundingBox box = AcademySite.box(stamp);
            helper.assertTrue(start.isValid() && start.getPieces().size() == 1 && start.getPieces().get(0) instanceof
                    SitePiece piece && piece.templatePosition().equals(corner(stamp)) && piece.getBoundingBox()
                    .equals(box), site + " is laid out as " + start.getPieces());
            StructureTemplate template = soul.getStructureManager().get(site.template()).orElseThrow();
            StructurePlaceSettings turn = new StructurePlaceSettings().setRotation(site.rotation());
            Map<ChunkPos, Integer> marked = new HashMap<>();
            for (SiteStamp.Marker marker : MARKERS.get(i)) {
                BlockPos at = stamp.markerAt(0, marker, corner(stamp));
                helper.assertTrue(template.filterBlocks(corner(stamp), turn, marker.block().get()).stream()
                        .anyMatch(info -> info.pos().equals(at)), site + "'s marker at " + at + " is not in its template");
                marked.merge(new ChunkPos(at), 1, Integer::sum);
            }
            helper.assertTrue(marked.keySet().equals(Set.copyOf(stamp.covered(0)))
                    && marked.values().stream().allMatch(n -> n == 1), site + "'s markers lie in " + marked);
            List<CompoundTag> dummies = template.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND).stream()
                    .map(entry -> ((CompoundTag) entry).getCompound("nbt")).toList();
            helper.assertTrue(dummies.size() == DUMMIES.get(i) && dummies.stream().allMatch(nbt -> nbt.getString("id")
                    .equals(BleachMod.id("training_dummy").toString()) && nbt.getBoolean("Spot")
                    && !nbt.contains("Invulnerable")), site + " sets " + dummies);
        }
        helper.succeed();
    }

    // ---- Training ----

    /**
     * Test 2. A student's sword blows on a dummy train Zanjutsu a quarter of a Hollow hit's xp each, one a swing's wait,
     * up to the day's cap, of which it is told once in chat; the next day's blows train again, and an empty drill, as a
     * save from before the dummies has, reads as none.
     */
    @GameTest(template = "empty")
    public static void aDummyTrainsUpToTheDaysCap(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        TrainingDummy dummy = dummyBy(helper, player, new ItemStack(Items.IRON_SWORD));
        try {
            KidoTests.withConfig(BleachConfig.HOLLOW_HIT_STAT_XP, 2, () -> KidoTests.withConfig(
                    BleachConfig.DUMMY_XP_PER_DAY, 3, () -> {
                SoulData data = SoulLogic.get(player);
                List<Integer> seen = new ArrayList<>();
                blow(player, dummy);
                player.tickCount -= 10;
                blow(player, dummy);
                seen.add(data.xp(Stat.ZANJUTSU));
                for (int i = 0; i < 7; i++) {
                    blow(player, dummy);
                    seen.add(data.xp(Stat.ZANJUTSU));
                }
                helper.assertTrue(seen.equals(List.of(0, 1, 1, 2, 2, 3, 3, 3)), "a quarter a blow gave " + seen);
                helper.assertTrue(TestPlayers.chat(player).stream().filter(line -> line.getContents() instanceof
                        TranslatableContents said && said.getKey().equals("message.bleach.training_dummy.spent"))
                        .count() == 1, "the student was not told once that the dummies were spent");
                DummyDrill drill = player.getData(SoulAttachments.DUMMY_DRILL);
                player.setData(SoulAttachments.DUMMY_DRILL, new DummyDrill(drill.day() - 1, drill.today(), 0));
                blow(player, dummy);
                blow(player, dummy);
                helper.assertTrue(data.xp(Stat.ZANJUTSU) == 4, "the next day's two blows gave "
                        + (data.xp(Stat.ZANJUTSU) - 3) + ", not 1");
            }));
            helper.assertTrue(DummyDrill.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).result()
                    .equals(Optional.of(DummyDrill.NONE)), "an empty drill does not read as none");
            helper.succeed();
        } finally {
            dummy.discard();
        }
    }

    /**
     * Test 3. Bare fists on a dummy train Hakuda at the same quarter; a blow on a pig still gives its one; a Hollow's
     * blade on a dummy trains nothing and starts no drill.
     */
    @GameTest(template = "empty")
    public static void fistsTrainHakudaAndAHollowsBladeNothing(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        TrainingDummy dummy = dummyBy(helper, player, ItemStack.EMPTY);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(1.5, 1.0, 2.5));
        try {
            // Pinned, so four blows give one Hollow hit's xp and stay under the day's cap.
            KidoTests.withConfig(BleachConfig.HOLLOW_HIT_STAT_XP, 2, () -> KidoTests.withConfig(
                    BleachConfig.DUMMY_XP_PER_DAY, 80, () -> {
                SoulData data = SoulLogic.get(player);
                for (int i = 0; i < 4; i++) {
                    blow(player, dummy);
                }
                int hollowHit = HollowLogic.hitXp(true, false);
                helper.assertTrue(data.xp(Stat.HAKUDA) == hollowHit && data.xp(Stat.ZANJUTSU) == 0,
                        "four bare blows gave Hakuda " + data.xp(Stat.HAKUDA) + " and Zanjutsu " + data.xp(Stat.ZANJUTSU));
                blow(player, pig);
                helper.assertTrue(data.xp(Stat.HAKUDA) == hollowHit + 1, "a blow on a pig did not give its 1");
                player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 40.0F));
                player.setData(SoulAttachments.DUMMY_DRILL, DummyDrill.NONE);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                for (int i = 0; i < 4; i++) {
                    blow(player, dummy);
                }
                SoulData hollow = SoulLogic.get(player);
                helper.assertTrue(hollow.xp(Stat.ZANJUTSU) == 0 && hollow.xp(Stat.HAKUDA) == 0
                        && player.getData(SoulAttachments.DUMMY_DRILL).equals(DummyDrill.NONE), "a Hollow's blade trained");
            }));
            helper.succeed();
        } finally {
            dummy.discard();
            pig.discard();
        }
    }

    // ---- The dummy itself ----

    /**
     * Test 4. No harm a dummy is dealt fells or moves it, no piston shoves it, and it is no creature's prey or foe; it is
     * saved and read back the same; {@code /kill} takes it away, with no death.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aDummyStandsUntilKilled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrainingDummy dummy = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new Vec3(2.5, 1.0, 2.5));
        Vec3 at = dummy.position();
        float yaw = dummy.getYRot();
        DamageSources sources = level.damageSources();
        for (DamageSource source : List.of(sources.generic(), sources.fall(), sources.explosion(null, null),
                sources.magic(), sources.lava(), sources.wither(), sources.genericKill(), sources.fellOutOfWorld())) {
            dummy.invulnerableTime = 0;
            dummy.hurt(source, 1000.0F);
        }
        dummy.knockback(5.0, 1.0, 0.0);
        dummy.push(2.0, 1.0, 0.0);
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            try {
                helper.assertTrue(dummy.isAlive() && dummy.getHealth() == dummy.getMaxHealth()
                        && dummy.position().equals(at) && dummy.getYRot() == yaw, "the dummy fell or moved: "
                        + dummy.getHealth() + " at " + dummy.position());
                helper.assertTrue(dummy.getPistonPushReaction() == PushReaction.IGNORE, "pistons push the dummy");
                helper.assertFalse(dummy.canUsePortal(false), "the dummy goes through portals");
                helper.assertTrue(!dummy.canBeSeenAsEnemy() && !HollowLogic.isPrey(dummy)
                        && !ArrancarNpcLogic.isPrey(dummy) && DevourLogic.preyOf(dummy) == null, "the dummy can be hunted");
                CompoundTag saved = dummy.saveWithoutId(new CompoundTag());
                TrainingDummy copy = ModEntities.TRAINING_DUMMY.get().create(level);
                copy.load(saved);
                helper.assertTrue(copy.getUUID().equals(dummy.getUUID()) && copy.getYRot() == yaw,
                        "the dummy is not read back as saved");
                MinecraftServer server = level.getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "kill " + dummy.getStringUUID());
                helper.assertTrue(dummy.isRemoved() && dummy.getRemovalReason() == Entity.RemovalReason.KILLED
                        && dummy.deathTime == 0, "/kill did not take the dummy away at once");
            } finally {
                dummy.discard();
            }
        }).thenSucceed();
    }

    // ---- In the world ----

    /**
     * Test 5. Built in a world made before them by the checks their chunks' loads queue, the grounds stand once with
     * every dummy their templates set, none saving its spot's flag: building again or setting the yard's template again
     * where it stands doubles none, and a dummy killed is not set back when its chunk loads, only by that template set
     * again. It leaves the grounds built in the shared test world with all 12 dummies, which no later test counts.
     */
    @GameTest(template = "empty", batch = "grounds", timeoutTicks = 400)
    public static void theGroundsAreBuiltOnce(GameTestHelper helper) {
        ServerLevel soul = ShakonmakuTests.hold(helper, -14, 15).soul;
        List<SiteStamp> stamps = AcademySite.GROUNDS;
        // Nothing here builds them: the checks a chunk's load queues do, each once its work's ring is loaded. A chunk an
        // earlier test made full and let fall back, kept loaded by the Academy's own ticket, fires no second load event as
        // it is made full again, so once every ring chunk is loaded each work's check is queued as its first chunk's load
        // queues it. SiteTests' Academy, built by the same listener, shows real loads queue these checks.
        // The waits are patient, since the test server's ticks outrun the loading threads that lay those chunks.
        StructureTemplate yard = soul.getStructureManager().get(AcademySite.YARD.sites().get(0).template()).orElseThrow();
        BlockPos corner = corner(AcademySite.YARD);
        Runnable placeYard = () -> yard.placeInWorld(soul, corner, corner, new StructurePlaceSettings(),
                soul.getRandom(), Block.UPDATE_CLIENTS);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<ChunkPos> unloaded = stamps.stream().flatMap(stamp -> ring(stamp).stream()).distinct()
                            .filter(pos -> soul.getChunkSource().getChunkNow(pos.x, pos.z) == null).toList();
                    GateTests.patiently(helper, unloaded.isEmpty(), "the grounds' chunks and those beside them are not all"
                            + " loaded: " + unloaded);
                })
                .thenExecute(() -> stamps.forEach(stamp -> AcademySite.onChunkLoad(new ChunkEvent.Load(
                        soul.getChunk(stamp.startChunk(0).x, stamp.startChunk(0).z), false))))
                .thenWaitUntil(() -> {
                    List<String> lacking = lacking(soul, stamps);
                    GateTests.patiently(helper, lacking.isEmpty(), "the grounds are not all built: " + lacking);
                })
                .thenExecute(() -> {
                    for (int i = 0; i < stamps.size(); i++) {
                        SiteStamp stamp = stamps.get(i);
                        List<TrainingDummy> held = standing(soul, stamp);
                        helper.assertTrue(held.size() == DUMMIES.get(i) && held.stream().noneMatch(each -> each
                                .saveWithoutId(new CompoundTag()).contains("Spot")) && !stamp.ensureBuilt(soul, 0)
                                && standing(soul, stamp).size() == DUMMIES.get(i), stamp.sites().get(0) + " holds " + held);
                    }
                    placeYard.run();
                    helper.assertTrue(standing(soul, AcademySite.YARD).size() == 8, "the yard set again doubled a dummy");
                    TrainingDummy killed = standing(soul, AcademySite.YARD).get(0);
                    killed.kill();
                    helper.assertFalse(AcademySite.YARD.ensureBuilt(soul, 0), "the yard was built again");
                    AcademySite.onChunkLoad(new ChunkEvent.Load(soul.getChunkAt(killed.blockPosition()), false));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(standing(soul, AcademySite.YARD).size() == 7, "the killed dummy was set back");
                    placeYard.run();
                    helper.assertTrue(standing(soul, AcademySite.YARD).size() == 8, "the yard set again did not restore it");
                })
                .thenSucceed();
    }
}
