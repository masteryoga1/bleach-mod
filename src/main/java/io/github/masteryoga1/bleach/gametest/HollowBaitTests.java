package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BaitEvents;
import io.github.masteryoga1.bleach.soul.BaitLogic;
import io.github.masteryoga1.bleach.soul.BaitRecord;
import io.github.masteryoga1.bleach.soul.BaitSite;
import io.github.masteryoga1.bleach.soul.BaitSites;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.MaskFragments;
import io.github.masteryoga1.bleach.soul.QuincyForms;
import io.github.masteryoga1.bleach.soul.QuincyNpcLogic;
import io.github.masteryoga1.bleach.soul.QuincyProgress;
import io.github.masteryoga1.bleach.soul.QuincyTeaching;
import io.github.masteryoga1.bleach.soul.ReiatsuEvents;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Hollow Bait: the mask fragments a fresh crack breaks off, the bait's recipe, why one
 * cannot be crushed, the numbers its waves follow, its sites as saved, the soul records from before
 * it, how a site holds its Hollows, a wave and a cracked sky for real, and the contest with the
 * Hunter. Sites never step on their own on the game test server, so each test drives its site
 * itself, and every site a test opens is removed again before it ends: they live in the Overworld's
 * shared saved data. Mock players from {@link ZanpakutoTests} are not in the world.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HollowBaitTests {
    private static final float EPSILON = 1.0E-4F;
    private static final int TICKS_PER_SECOND = 20;
    /** How far from its soul a wave's rifts may open (24 blocks), with room for the Hollow's box. */
    private static final double LANDING_REACH = 28.0;
    /** How many times a wave is tried before the test gives up on finding ground for it. */
    private static final int WAVE_TRIES = 5;
    /** How far a site's own Hollows and rifts are looked for when it is cleared away. */
    private static final double SITE_SEARCH = 96.0;
    /** The tick by which a site test must have finished; past it, everything is cleared away and it fails. */
    private static final int GIVE_UP_TICK = 290;

    private HollowBaitTests() {
    }

    // ---- Helpers ----

    private static void assertNear(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON, what + " is " + actual + ", not " + expected);
    }

    /** A living Echt Human of the first tier with this much reiryoku, aware of spirits. */
    private static SoulData aware(float maxReiryoku) {
        return QuincyTests.quincy(Bloodline.ECHT, 1, maxReiryoku);
    }

    /** The first field of two sites that differs, or null if they are the same in every one. */
    private static String difference(BaitSite a, BaitSite b) {
        Map<String, Boolean> same = new LinkedHashMap<>();
        same.put("id", a.id().equals(b.id()));
        same.put("centre", a.centre().equals(b.centre()));
        same.put("owner", a.owner().equals(b.owner()));
        same.put("rival", Objects.equals(a.rival(), b.rival()));
        same.put("contest", a.contest() == b.contest());
        same.put("active ticks", a.activeTicks() == b.activeTicks());
        same.put("frozen ticks", a.frozenTicks() == b.frozenTicks());
        same.put("next wave", a.nextWaveIn() == b.nextWaveIn());
        same.put("arrived", a.arrived() == b.arrived());
        same.put("waves", a.waves() == b.waves());
        same.put("owner's kills", a.ownerKills() == b.ownerKills());
        same.put("rival's kills", a.rivalKills() == b.rivalKills());
        same.put("finale ticks", a.finaleTicks() == b.finaleTicks());
        same.put("Plus lost", a.plusLost() == b.plusLost());
        same.put("last ping", a.lastPingAt() == b.lastPingAt());
        for (Map.Entry<String, Boolean> field : same.entrySet()) {
            if (!field.getValue()) {
                return field.getKey();
            }
        }
        return null;
    }

    /** Forces every chunk within this reach of the point that is not forced already, and returns those it forced. */
    private static List<ChunkPos> forceAround(ServerLevel level, Vec3 centre, double reach) {
        List<ChunkPos> forced = new ArrayList<>();
        for (ChunkPos chunk : chunksAround(centre, reach)) {
            if (!level.getForcedChunks().contains(chunk.toLong())) {
                level.setChunkForced(chunk.x, chunk.z, true);
                forced.add(chunk);
            }
        }
        return forced;
    }

    private static List<ChunkPos> chunksAround(Vec3 centre, double reach) {
        List<ChunkPos> chunks = new ArrayList<>();
        int minX = (int) Math.floor(centre.x - reach) >> 4;
        int maxX = (int) Math.floor(centre.x + reach) >> 4;
        int minZ = (int) Math.floor(centre.z - reach) >> 4;
        int maxZ = (int) Math.floor(centre.z + reach) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    /** Whether creatures tick everywhere within this reach of the point, as a wave's landing needs. */
    private static boolean allTicking(ServerLevel level, Vec3 centre, double reach) {
        for (ChunkPos chunk : chunksAround(centre, reach)) {
            if (!level.isPositionEntityTicking(chunk.getMiddleBlockPosition((int) centre.y))) {
                return false;
            }
        }
        return true;
    }

    /** The site's own Hollows about it, alive. */
    private static List<Hollow> siteHollows(ServerLevel level, BaitSite site) {
        Vec3 centre = Vec3.atBottomCenterOf(site.centre());
        return level.getEntitiesOfClass(Hollow.class, new AABB(centre, centre).inflate(SITE_SEARCH),
                hollow -> hollow.isAlive() && site.id().equals(hollow.baitSite()));
    }

    /** Takes away the site, its Hollows and its rifts, the sky's among them. Safe to call more than once. */
    private static void clearSite(ServerLevel level, BaitSites sites, BaitSite site) {
        Vec3 centre = Vec3.atBottomCenterOf(site.centre());
        AABB around = new AABB(centre, centre).inflate(SITE_SEARCH);
        QuincyArtsTests.discardAll(level.getEntitiesOfClass(Hollow.class, around, hollow -> site.id().equals(hollow.baitSite())));
        QuincyArtsTests.discardAll(level.getEntitiesOfClass(GargantaRift.class, around, rift -> site.id().equals(rift.baitSite())));
        if (sites.find(site.id()) != null) {
            sites.remove(site.id());
        }
    }

    /** Runs a step of a site test, clearing everything away first if it fails. */
    private static Runnable orClear(Runnable step, Runnable clear) {
        return () -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                clear.run();
                throw e;
            }
        };
    }

    // ---- Mask fragments ----

    @GameTest(template = "empty")
    public static void maskFragmentRules(GameTestHelper helper) {
        double chance = 0.25;
        helper.assertFalse(MaskFragments.yields(false, false, true, true, 0.0, chance), "a mask already cracked broke off a fragment");
        helper.assertFalse(MaskFragments.yields(true, true, true, true, 0.0, chance), "a Hollow tried before broke off a fragment");
        helper.assertFalse(MaskFragments.yields(true, false, false, true, 0.0, chance), "a dissolving Hollow broke off a fragment");
        helper.assertTrue(MaskFragments.yields(true, false, true, true, 0.99, chance), "Fishbone D's fresh crack broke nothing off");
        helper.assertFalse(MaskFragments.yields(true, false, true, false, 0.3, chance), "a roll over the chance broke off a fragment");
        helper.assertTrue(MaskFragments.yields(true, false, true, false, 0.2, chance), "a roll under the chance broke nothing off");
        helper.succeed();
    }

    /**
     * Each blow to the mask keeps it cracked for {@link Hollow#CRACK_TICKS} from that blow, so after a
     * second blow a tick later the mask is whole again a tick later too.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void freshCrackGivesOneFragment(GameTestHelper helper) {
        FishboneD fishbone = QuincyTests.still(helper.spawn(ModEntities.FISHBONE_D.get(), 8, 2, 8));
        Player mock = QuincyTests.archer(helper, aware(100.0F), 4.5, 4.5);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(fishbone.onMaskStruck(now), "the first blow to a whole mask was not fresh");
        helper.assertFalse(fishbone.onMaskStruck(now + 1L), "a blow to a cracked mask was fresh");
        helper.assertTrue(fishbone.onMaskStruck(now + 1L + Hollow.CRACK_TICKS), "the mask never closed again");

        helper.assertTrue(MaskFragments.onFreshCrack(mock, fishbone, now), "Fishbone D's fresh crack gave no fragment");
        int carried = QuincyArtsTests.carried(mock, ModItems.MASK_FRAGMENT.get());
        helper.assertTrue(carried == 1, "the fresh crack gave " + carried + " fragments");
        helper.assertFalse(MaskFragments.onFreshCrack(mock, fishbone, now + 300L), "one Hollow gave two fragments");
        carried = QuincyArtsTests.carried(mock, ModItems.MASK_FRAGMENT.get());
        helper.assertTrue(carried == 1, "after a second crack the soul carries " + carried + " fragments");

        // The roll is saved with the Hollow, so reloading it gives no second chance.
        FishboneD reloaded = ModEntities.FISHBONE_D.get().create(helper.getLevel());
        helper.assertTrue(reloaded != null, "no Fishbone D could be made to reload into");
        reloaded.load(fishbone.saveWithoutId(new CompoundTag()));
        helper.assertFalse(MaskFragments.onFreshCrack(mock, reloaded, now + 600L), "a reloaded Hollow gave a second fragment");
        carried = QuincyArtsTests.carried(mock, ModItems.MASK_FRAGMENT.get());
        helper.assertTrue(carried == 1, "after a reload the soul carries " + carried + " fragments");
        fishbone.discard();
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 40)
    public static void fullInventoryDropsTheFragment(GameTestHelper helper) {
        FishboneD fishbone = QuincyTests.still(helper.spawn(ModEntities.FISHBONE_D.get(), 8, 2, 8));
        Player mock = QuincyTests.archer(helper, aware(100.0F), 4.5, 4.5);
        for (int slot = 0; slot < mock.getInventory().items.size(); slot++) {
            mock.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(MaskFragments.onFreshCrack(mock, fishbone, helper.getLevel().getGameTime()),
                "Fishbone D's fresh crack gave no fragment");
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, mock.getBoundingBox().inflate(2.0),
                item -> item.getItem().is(ModItems.MASK_FRAGMENT.get()));
        helper.assertFalse(dropped.isEmpty(), "with no room, the fragment was not dropped at the soul's feet");
        QuincyArtsTests.discardAll(dropped);
        QuincyArtsTests.clearItems(helper);
        fishbone.discard();
        helper.succeed();
    }

    // ---- Recipe and crush rules ----

    @GameTest(template = "empty")
    public static void baitRecipeLoads(GameTestHelper helper) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager().byKey(BleachMod.id("hollow_bait")).orElse(null);
        helper.assertTrue(holder != null, "bleach:hollow_bait did not load");
        ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.HOLLOW_BAIT.get()), "the bait recipe makes " + result);
        List<Ingredient> ingredients = holder.value().getIngredients();
        for (ItemStack wanted : List.of(new ItemStack(ModItems.MASK_FRAGMENT.get()), new ItemStack(ModItems.GINTO.get()),
                new ItemStack(Items.BONE_MEAL))) {
            helper.assertTrue(ingredients.stream().anyMatch(ingredient -> ingredient.test(wanted)),
                    "the bait recipe does not take " + wanted);
        }
        helper.assertTrue(ingredients.size() == 3, "the bait recipe takes " + ingredients.size() + " ingredients, not 3");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crushRefusalsInOrder(GameTestHelper helper) {
        BaitLogic.CrushContext clear = new BaitLogic.CrushContext(true, false, true, false, false, false, false, false, 0, 4,
                false);
        helper.assertTrue(BaitLogic.refusal(clear, false) == null, "an all-clear crush was refused: " + BaitLogic.refusal(clear, false));
        Map<BaitLogic.Refusal, BaitLogic.CrushContext> each = new LinkedHashMap<>();
        each.put(BaitLogic.Refusal.UNAWARE, new BaitLogic.CrushContext(false, false, true, false, false, false, false, false, 0, 4, false));
        each.put(BaitLogic.Refusal.HOLLOW, new BaitLogic.CrushContext(true, true, true, false, false, false, false, false, 0, 4, false));
        each.put(BaitLogic.Refusal.WRONG_WORLD, new BaitLogic.CrushContext(true, false, false, false, false, false, false, false, 0, 4,
                false));
        each.put(BaitLogic.Refusal.QUIET, new BaitLogic.CrushContext(true, false, true, true, false, false, false, false, 0, 4, false));
        each.put(BaitLogic.Refusal.FIRST_HOLLOW, new BaitLogic.CrushContext(true, false, true, false, true, false, false, false, 0, 4,
                false));
        each.put(BaitLogic.Refusal.OWNED, new BaitLogic.CrushContext(true, false, true, false, false, true, false, false, 0, 4, false));
        each.put(BaitLogic.Refusal.ACADEMY, new BaitLogic.CrushContext(true, false, true, false, false, false, true, false, 0, 4,
                false));
        each.put(BaitLogic.Refusal.TOO_CLOSE, new BaitLogic.CrushContext(true, false, true, false, false, false, false, true, 0, 4,
                false));
        each.put(BaitLogic.Refusal.TOO_MANY, new BaitLogic.CrushContext(true, false, true, false, false, false, false, false, 4, 4,
                false));
        each.put(BaitLogic.Refusal.OTHERS, new BaitLogic.CrushContext(true, false, true, false, false, false, false, false, 0, 4, true));
        each.forEach((expected, context) -> {
            BaitLogic.Refusal refusal = BaitLogic.refusal(context, false);
            helper.assertTrue(refusal == expected, "a crush that is only " + expected + " was refused as " + refusal);
        });
        BaitLogic.CrushContext everything = new BaitLogic.CrushContext(false, true, false, true, true, true, true, true, 4, 4, true);
        helper.assertTrue(BaitLogic.refusal(everything, false) == BaitLogic.Refusal.UNAWARE,
                "with every refusal, the first was " + BaitLogic.refusal(everything, false));
        helper.assertTrue(BaitLogic.refusal(everything, true) == BaitLogic.Refusal.WRONG_WORLD,
                "the command with every refusal was refused as " + BaitLogic.refusal(everything, true));
        BaitLogic.CrushContext quiet = new BaitLogic.CrushContext(true, false, true, true, false, false, false, false, 0, 4, false);
        helper.assertTrue(BaitLogic.refusal(quiet, true) == null, "the command was refused where no Hollows come");
        helper.succeed();
    }

    // ---- Numbers and saving ----

    @GameTest(template = "empty")
    public static void waveNumbersEscalate(GameTestHelper helper) {
        Map<String, int[]> ints = new LinkedHashMap<>();
        ints.put("the first wait", new int[] {BaitLogic.waveIntervalTicks(0.0F, 30, 12), 600});
        ints.put("the last wait", new int[] {BaitLogic.waveIntervalTicks(1.0F, 30, 12), 240});
        ints.put("the halfway wait", new int[] {BaitLogic.waveIntervalTicks(0.5F, 30, 12), 420});
        ints.put("the tears just under 0.4", new int[] {BaitLogic.picksPerWave(0.39F), 1});
        ints.put("the tears at 0.4", new int[] {BaitLogic.picksPerWave(0.4F), 2});
        ints.put("the tears at 0.8", new int[] {BaitLogic.picksPerWave(0.8F), 3});
        ints.put("the cap for one soul", new int[] {BaitLogic.aliveCap(1, 6, 2, 12), 6});
        ints.put("the cap for two souls", new int[] {BaitLogic.aliveCap(2, 6, 2, 12), 8});
        ints.put("the cap for five souls", new int[] {BaitLogic.aliveCap(5, 6, 2, 12), 12});
        ints.forEach((what, pair) -> helper.assertTrue(pair[0] == pair[1], what + " is " + pair[0] + ", not " + pair[1]));
        assertNear(helper, BaitLogic.lureReiatsu(20.0F, 0.5F, 40.0F, 60.0F), 70.0, "the lure halfway for a weak soul");
        assertNear(helper, BaitLogic.lureReiatsu(500.0F, 0.5F, 40.0F, 60.0F), 500.0, "the lure halfway for a strong soul");
        assertNear(helper, BaitLogic.progress(45, 30), 1.0, "the progress past the finale's arrivals");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void baitSitesSurviveSaving(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BaitSite bait = new BaitSite(UUID.randomUUID(), new BlockPos(10, 64, -20), UUID.randomUUID(), null, false, 100, 5,
                300, 7, 3, 0, 0, 0, true, 1234L);
        BaitSite contest = new BaitSite(UUID.randomUUID(), new BlockPos(-300, 70, 400), UUID.randomUUID(), UUID.randomUUID(),
                true, 2400, 0, 120, 12, 6, 4, 5, 380, false, BaitSite.NEVER);
        BaitSites sites = new BaitSites();
        sites.add(bait);
        sites.add(contest);
        BaitSites loaded = BaitSites.load(sites.save(new CompoundTag(), registries), registries);
        helper.assertTrue(loaded.all().size() == 2, "two sites saved loaded as " + loaded.all().size());
        for (BaitSite site : List.of(bait, contest)) {
            BaitSite back = loaded.find(site.id());
            helper.assertTrue(back != null, "a site was lost in saving");
            String differs = difference(site, back);
            helper.assertTrue(differs == null, "a site's " + differs + " changed in saving");
        }

        ListTag list = new ListTag();
        list.add(BaitSite.CODEC.encodeStart(NbtOps.INSTANCE, bait).getOrThrow());
        list.add(new CompoundTag());
        CompoundTag tag = new CompoundTag();
        tag.put("sites", list);
        BaitSites partial = BaitSites.load(tag, registries);
        helper.assertTrue(partial.all().size() == 1 && partial.find(bait.id()) != null,
                "a list with one bad entry loaded " + partial.all().size() + " sites");
        helper.assertTrue(BaitSites.load(new CompoundTag(), registries).all().isEmpty(), "an empty tag loaded sites");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldSoulRecordsLoad(GameTestHelper helper) {
        HuntRecord oldHunt = new HuntRecord(100, true, 5L, 2, 3, 1, true, 9L);
        CompoundTag hunt = (CompoundTag) HuntRecord.CODEC.encodeStart(NbtOps.INSTANCE, oldHunt).getOrThrow();
        hunt.remove("bait");
        HuntRecord huntLoaded = HuntRecord.CODEC.parse(NbtOps.INSTANCE, hunt).getOrThrow();
        helper.assertTrue(huntLoaded.bait().equals(BaitRecord.NONE), "a hunt record with no bait loaded " + huntLoaded.bait());
        helper.assertTrue(huntLoaded.equals(oldHunt), "a hunt record from before the bait loaded as " + huntLoaded);

        QuincyProgress oldQuincy = new QuincyProgress(Bloodline.ECHT, true, 77, 12.5F, 30.0F, BlutMode.VENE, 3, true);
        CompoundTag quincy = (CompoundTag) QuincyProgress.CODEC.encodeStart(NbtOps.INSTANCE, oldQuincy).getOrThrow();
        quincy.remove("teaching");
        QuincyProgress quincyLoaded = QuincyProgress.CODEC.parse(NbtOps.INSTANCE, quincy).getOrThrow();
        helper.assertTrue(quincyLoaded.teaching().equals(QuincyTeaching.NONE),
                "Quincy progress with no teaching loaded " + quincyLoaded.teaching());
        helper.assertTrue(quincyLoaded.equals(oldQuincy), "Quincy progress from before the Elder loaded as " + quincyLoaded);

        CompoundTag forms = new CompoundTag();
        forms.putBoolean("vollstandig", true);
        forms.putLong("voll_ready_at", 50L);
        forms.putLong("licht_regen_ready_at", 60L);
        QuincyForms formsLoaded = QuincyForms.CODEC.parse(NbtOps.INSTANCE, forms).getOrThrow();
        helper.assertTrue(formsLoaded.ransotengaiSince() == ShinigamiProgress.NEVER,
                "forms from before Ransōtengai loaded woven since " + formsLoaded.ransotengaiSince());
        helper.assertTrue(formsLoaded.ransotengaiReadyAt() == 0L,
                "forms from before Ransōtengai loaded a wait until " + formsLoaded.ransotengaiReadyAt());
        QuincyForms six = new QuincyForms(true, false, false, false, 50L, 60L);
        helper.assertTrue(six.equals(new QuincyForms(true, false, false, false, 50L, 60L, ShinigamiProgress.NEVER, 0L)),
                "the forms as 4b knew them are " + six);
        helper.assertTrue(formsLoaded.equals(six), "forms from before Ransōtengai loaded as " + formsLoaded);
        helper.succeed();
    }

    // ---- Site behaviour ----

    /**
     * A site's hold lasts {@link BaitEvents#HOLD_TICKS} from its last renewal. The default timeout,
     * since the hold is looked at again past 60 ticks.
     */
    @GameTest(template = "field")
    public static void baitHollowsHoldAndGather(GameTestHelper helper) {
        SpiderHollow spider = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 8));
        Player mock = QuincyTests.archer(helper, aware(100.0F), 4.5, 4.5);
        long now = helper.getLevel().getGameTime();
        Vec3 centre = spider.position().add(20.0, 0.0, 0.0);
        spider.setBaitSite(UUID.randomUUID());
        spider.holdForBait(centre, now, false);
        helper.assertTrue(spider.isBaitHeld(), "a Hollow the site holds is not held");
        helper.assertFalse(spider.removeWhenFarAway(200.0), "a Hollow the site holds would despawn far from players");
        ReiatsuEvents.frighten(spider, mock, now + 200L);
        helper.assertTrue(ReiatsuFleeGoal.isFrightened(spider), "the Hollow was not frightened");
        helper.assertFalse(new ReiatsuFleeGoal(spider).canUse(), "a Hollow the site holds runs from released reiatsu");
        helper.assertTrue(spider.canAttack(mock), "a Hollow held outside the finale will not attack an aware soul");
        spider.holdForBait(centre, now, true);
        helper.assertFalse(spider.canAttack(mock), "a Hollow gathered for the finale may attack");
        helper.runAfterDelay(BaitEvents.HOLD_TICKS + 1, () -> {
            helper.assertFalse(spider.isBaitHeld(), "the hold outlasted " + BaitEvents.HOLD_TICKS + " ticks with no renewal");
            spider.discard();
            helper.succeed();
        });
    }

    /**
     * A Hollow gathered for the finale gives up prey set straight on it, as the rift it came through
     * sets it, which no target goal would let go of once the finale stops them running.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void finaleHollowsGiveUpTheirPrey(GameTestHelper helper) {
        SpiderHollow spider = helper.spawn(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 8);
        Husk prey = QuincyTests.still(helper.spawn(EntityType.HUSK, 3, 2, 8));
        spider.setBaitSite(UUID.randomUUID());
        spider.setTarget(prey);
        helper.assertTrue(spider.getTarget() == prey, "the Hollow did not take the husk as its prey");
        spider.holdForBait(spider.position(), helper.getLevel().getGameTime(), true);
        helper.succeedWhen(() -> {
            helper.assertTrue(spider.getTarget() == null, "a Hollow gathered for the finale still hunts " + spider.getTarget());
            spider.discard();
            prey.discard();
        });
    }

    /**
     * A wave and a cracked sky, for real. A wave's rifts open 12 to 24 blocks from its soul, past the
     * box, and only over ground where creatures tick, so the chunks around are forced while the test
     * runs (and let go after). Ground is picked at random, so the wave is tried a few times. Its own
     * batch, so no other test's Hollows are about.
     */
    @GameTest(template = "field", batch = "bait", timeoutTicks = 300)
    public static void siteWaveAndFinale(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BaitSites sites = BaitSites.get(level.getServer());
        Player mock = QuincyTests.archer(helper, aware(30.0F), 8.5, 8.5);
        BaitSite site = BaitSite.create(mock.getUUID(), helper.absolutePos(new BlockPos(8, 2, 8)), false, null, 0);
        sites.add(site);
        List<ChunkPos> forced = forceAround(level, mock.position(), LANDING_REACH);
        Runnable clear = () -> {
            clearSite(level, sites, site);
            for (ChunkPos chunk : forced) {
                level.setChunkForced(chunk.x, chunk.z, false);
            }
            forced.clear();
        };
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("the bait site never got through its wave and finale");
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(allTicking(level, mock.position(), LANDING_REACH),
                        "the ground around the site never started ticking"))
                .thenExecute(orClear(() -> {
                    long now = level.getGameTime();
                    int opened = 0;
                    for (int tries = 0; tries < WAVE_TRIES && opened == 0; tries++) {
                        opened = BaitEvents.wave(level, sites, site, now, List.of(mock));
                    }
                    helper.assertTrue(opened >= 1, "the wave opened no rift in " + WAVE_TRIES + " tries");
                    List<GargantaRift> rifts = level.getEntitiesOfClass(GargantaRift.class, mock.getBoundingBox().inflate(32.0),
                            rift -> site.id().equals(rift.baitSite()));
                    helper.assertFalse(rifts.isEmpty(), "no rift of the site's opened within 32 blocks");
                }, clear))
                .thenWaitUntil(() -> {
                    helper.assertTrue(site.arrived() >= 1, "no Hollow came down at the site");
                    helper.assertFalse(siteHollows(level, site).isEmpty(), "no Hollow of the site's is about");
                })
                .thenExecute(orClear(() -> {
                    long now = level.getGameTime();
                    helper.assertTrue(BaitEvents.startFinale(level, sites, site, now), "the sky over the bait did not crack");
                    UUID skyId = sites.skyRift(site.id());
                    GargantaRift sky = skyId != null && level.getEntity(skyId) instanceof GargantaRift rift ? rift : null;
                    helper.assertTrue(sky != null, "the cracked sky has no rift");
                    assertNear(helper, sky.size(), 3.0, "the sky rift's size");
                    int least = BleachConfig.get(BleachConfig.BAIT_FINALE_MIN_SECONDS) * TICKS_PER_SECOND;
                    int most = BleachConfig.get(BleachConfig.BAIT_FINALE_MAX_SECONDS) * TICKS_PER_SECOND;
                    helper.assertTrue(sky.hold() >= least && sky.hold() <= most,
                            "the sky holds open " + sky.hold() + " ticks, not " + least + " to " + most);
                    BaitEvents.end(level, sites, site, BaitLogic.End.STOPPED);
                    helper.assertTrue(sky.isRemoved(), "the sky stayed cracked over a stopped site");
                    helper.assertTrue(sites.find(site.id()) == null, "the stopped site is still open");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void contestOutcomes(GameTestHelper helper) {
        Map<String, BaitLogic.Outcome> outcomes = new LinkedHashMap<>();
        Map<String, BaitLogic.Outcome> expected = new LinkedHashMap<>();
        outcomes.put("more Hollows", BaitLogic.contestOutcome(3, 1, true, null, false));
        expected.put("more Hollows", BaitLogic.Outcome.WIN);
        outcomes.put("fewer Hollows", BaitLogic.contestOutcome(1, 3, true, null, false));
        expected.put("fewer Hollows", BaitLogic.Outcome.LOSS);
        outcomes.put("as many Hollows", BaitLogic.contestOutcome(2, 2, true, null, false));
        expected.put("as many Hollows", BaitLogic.Outcome.TIE);
        outcomes.put("the challenger offline", BaitLogic.contestOutcome(3, 1, false, null, false));
        expected.put("the challenger offline", BaitLogic.Outcome.VOID);
        outcomes.put("the Hunter driven off by a player", BaitLogic.contestOutcome(3, 1, true, QuincyNpc.LeaveCause.HURT_BY_PLAYER,
                true));
        expected.put("the Hunter driven off by a player", BaitLogic.Outcome.VOID);
        outcomes.put("a tie when the Hunter's time ran out", BaitLogic.contestOutcome(2, 2, true, QuincyNpc.LeaveCause.TIME, true));
        expected.put("a tie when the Hunter's time ran out", BaitLogic.Outcome.WIN);
        outcomes.put("none each when the Hunter left", BaitLogic.contestOutcome(0, 0, true, QuincyNpc.LeaveCause.TIME, true));
        expected.put("none each when the Hunter left", BaitLogic.Outcome.TIE);
        outcomes.put("behind when the Hunter left", BaitLogic.contestOutcome(0, 1, true, QuincyNpc.LeaveCause.TIME, true));
        expected.put("behind when the Hunter left", BaitLogic.Outcome.LOSS);
        outcomes.forEach((what, outcome) -> helper.assertTrue(outcome == expected.get(what),
                "a contest with " + what + " ended in a " + outcome + ", not a " + expected.get(what)));
        helper.succeed();
    }

    /**
     * A site frozen too long is given up, a contest called off and its Hunter let go. Game tests
     * have no mob spawning, so every step of a site here is a frozen one.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void frozenSitesAreGivenUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuincyHunter hunter = QuincyTests.still(helper.spawn(ModEntities.QUINCY_HUNTER.get(), 12, 2, 12));
        hunter.setLifetime(QuincyNpcLogic.VISIT_LIFETIME);
        BaitSites sites = BaitSites.get(level.getServer());
        BaitSite bait = BaitSite.create(UUID.randomUUID(), helper.absolutePos(new BlockPos(4, 2, 4)), false, null, 0);
        BaitSite contest = BaitSite.create(UUID.randomUUID(), helper.absolutePos(new BlockPos(12, 2, 12)), true,
                hunter.getUUID(), 0);
        int limit = BleachConfig.get(BleachConfig.BAIT_ABANDON_MINUTES) * 1200;
        try {
            for (BaitSite site : List.of(bait, contest)) {
                sites.add(site);
                site.setFrozenTicks(limit - 2 * BaitEvents.STEP_TICKS);
            }
            hunter.setContestSite(contest.id());
            long now = level.getGameTime();
            for (BaitSite site : List.of(bait, contest)) {
                BaitEvents.step(level, sites, site, now);
                helper.assertTrue(sites.find(site.id()) == site, "a site frozen short of the limit was given up");
                BaitEvents.step(level, sites, site, now + BaitEvents.STEP_TICKS);
                helper.assertTrue(sites.find(site.id()) == null, "a site frozen past the limit went on");
            }
            helper.assertTrue(hunter.contestSite() == null, "the Hunter still runs the contest called off");
            helper.assertTrue(hunter.lifetime() < QuincyNpcLogic.VISIT_LIFETIME, "the Hunter was not let go");
        } finally {
            hunter.discard();
            for (BaitSite site : List.of(bait, contest)) {
                if (sites.find(site.id()) != null) {
                    sites.remove(site.id());
                }
            }
        }
        helper.succeed();
    }

    /**
     * Kills inside a contest count to whoever made them. The kill out of reach is the Hunter's, so no
     * experience is dropped where no chunk is loaded.
     */
    @GameTest(template = "field", timeoutTicks = 60)
    public static void contestCountsWhoKilled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuincyHunter hunter = QuincyTests.still(helper.spawn(ModEntities.QUINCY_HUNTER.get(), 12, 2, 12));
        Player mock = QuincyTests.archer(helper, aware(100.0F), 2.5, 12.5);
        List<SpiderHollow> spiders = new ArrayList<>();
        for (int x : new int[] {4, 8, 12}) {
            SpiderHollow spider = QuincyTests.still(helper.spawn(ModEntities.SPIDER_HOLLOW.get(), x, 2, 4));
            spider.setHealth(1.0F);
            spiders.add(spider);
        }
        BaitSites sites = BaitSites.get(level.getServer());
        BaitSite site = BaitSite.create(mock.getUUID(), helper.absolutePos(new BlockPos(8, 2, 8)), true, hunter.getUUID(), 0);
        sites.add(site);
        try {
            spiders.get(0).hurt(level.damageSources().playerAttack(mock), 100.0F);
            helper.assertTrue(site.ownerKills() == 1 && site.rivalKills() == 0,
                    "the owner's kill counted as " + site.ownerKills() + " to " + site.rivalKills());
            spiders.get(1).hurt(level.damageSources().mobAttack(hunter), 100.0F);
            helper.assertTrue(site.ownerKills() == 1 && site.rivalKills() == 1,
                    "the Hunter's kill counted as " + site.ownerKills() + " to " + site.rivalKills());
            SpiderHollow far = spiders.get(2);
            far.teleportTo(far.getX() + 100.0, far.getY(), far.getZ());
            far.hurt(level.damageSources().mobAttack(hunter), 100.0F);
            helper.assertFalse(far.isAlive(), "the Spider Hollow sent away did not die");
            helper.assertTrue(site.ownerKills() == 1 && site.rivalKills() == 1,
                    "a kill 100 blocks away counted: " + site.ownerKills() + " to " + site.rivalKills());
        } finally {
            QuincyArtsTests.discardAll(spiders);
            hunter.discard();
            QuincyArtsTests.clearItems(helper);
            QuincyArtsTests.discardAll(level.getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()));
            sites.remove(site.id());
        }
        helper.assertTrue(BaitLogic.noLossFinale(10, false), "a finale with ten Hollows and no Plus lost did not count");
        helper.assertFalse(BaitLogic.noLossFinale(9, false), "a finale with nine Hollows counted");
        helper.assertFalse(BaitLogic.noLossFinale(10, true), "a finale with a Plus lost counted");
        helper.succeed();
    }
}
