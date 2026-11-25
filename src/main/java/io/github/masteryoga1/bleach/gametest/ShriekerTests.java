package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.BossBar;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for the Shrieker: its row and the rules that bring it alone, the rift that brings it to its drawer, the mask and
 * its bar, purified and slain. Tests that run beside others ask only about their own Shrieker and their own players, by
 * object; the barrier walls about each test keep other tests' souls out of a Shrieker's sight, so it hunts none of them.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ShriekerTests {
    private static final float EPSILON = 1.0E-3F;
    /** A soul aware of spirits, as the boss bar's own tests take one. */
    private static final float AWARE = 40.0F;
    /** Enough reiatsu that a second Hollow of any other kind may come along (HollowLogic.secondHollowChance, a half). */
    private static final float STRONG = 10_000.0F;
    /**
     * The reiatsu of the souls that strike the mask test's Shrieker: its own (150) is less than three times theirs, so
     * nothing is taken off their blows for the difference (SoulLogic.reiatsuDamageMultiplier), and both blows are big
     * enough that its armour takes the same share of each.
     */
    private static final float STRIKER = 100.0F;
    /** Room for the float steps of an attribute. */
    private static final double SAME = 1.0E-6;

    private ShriekerTests() {
    }

    // ---- Helpers ----

    /** A player joined at this spot in survival with a Human soul that sees spirits: prey for any Hollow. */
    private static ServerPlayer aware(GameTestHelper helper, Vec3 at) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, at);
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, AWARE));
        return player;
    }

    /** Whether the bar is shown to this very player object. */
    private static boolean holds(BossBar bar, ServerPlayer player) {
        return bar.players().stream().anyMatch(shown -> shown == player);
    }

    /** Turns a Hollow, head and body too, to face south (+Z), so its mask is on that side. */
    private static void faceSouth(Hollow hollow) {
        hollow.setYRot(0.0F);
        hollow.setYHeadRot(0.0F);
        hollow.setYBodyRot(0.0F);
    }

    /** Whether this creature would run from released reiatsu: it was given the goal as it joined the world. */
    private static boolean frightens(PathfinderMob mob) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof ReiatsuFleeGoal);
    }

    /** Whether this player has earned the advancement {@code bleach:shrieker}. */
    private static boolean broughtDown(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(BleachMod.id("shrieker"));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    // ---- The row and the rift ----

    /**
     * The row: by name, never by chance, no Menos, alone through one rift as wide as the first Hollow's; never saved,
     * with its flying speed kept among its attributes; one that skipped spawning takes the config's health and damage on
     * its first tick, whole; it is a sinner whatever is set, and any other Hollow's mark is saved and read leniently;
     * released reiatsu never frightens it off. Its advancement and sounds.
     */
    @GameTest(template = "field")
    public static void aShriekerIsABossThatComesAlone(GameTestHelper helper) {
        HollowKind kind = HollowKind.SHRIEKER;
        helper.assertTrue(HollowKind.byName("shrieker").equals(Optional.of(kind)), "no kind is named shrieker");
        helper.assertTrue(kind.arrivalWeight == 0 && !kind.isMenos(), "a Shrieker may come by chance, or is a Menos");
        helper.assertFalse(HollowLogic.arrivalPool(STRONG).contains(kind), "a Shrieker is in the arrival pool");
        // The roll that brings a second Spider brings no second Shrieker.
        int alone = HollowLogic.groupSize(kind, STRONG, Integer.MAX_VALUE, 0.0);
        helper.assertTrue(HollowLogic.groupSize(HollowKind.SPIDER, STRONG, Integer.MAX_VALUE, 0.0) == 2 && alone == 1,
                "a strong soul's rift brings " + alone + " Shriekers");
        float rift = GargantaEvents.riftSize(kind);
        helper.assertTrue(rift == GargantaEvents.riftSize(HollowKind.FISHBONE_D)
                && rift > GargantaEvents.riftSize(HollowKind.SPIDER), "a Shrieker's tear is " + rift + " across");

        EntityType<Shrieker> type = ModEntities.SHRIEKER.get();
        helper.assertTrue(ModEntities.hollowType(kind) == type, "the Shrieker kind has the wrong entity type");
        helper.assertFalse(type.canSerialize(), "a Shrieker is saved with the world");
        EntityDimensions size = type.getDimensions();
        helper.assertTrue(Math.abs(size.width() - 1.4F) < EPSILON && Math.abs(size.height() - 2.2F) < EPSILON
                && Math.abs(size.eyeHeight() - 1.9F) < EPSILON, "a Shrieker is " + size);

        // Read as they joined the world, before their wits are taken: what joining gave them goes with their goals.
        Shrieker shrieker = helper.spawn(type, new Vec3(8.5, 2.0, 8.5));
        SpiderHollow spider = helper.spawn(ModEntities.SPIDER_HOLLOW.get(), new Vec3(3.5, 2.0, 3.5));
        // A Spider runs from released reiatsu; a Shrieker, which does not leave its fight, never does.
        helper.assertTrue(frightens(spider) && !frightens(shrieker),
                "released reiatsu frightens a Shrieker, or no longer frightens a Spider");
        shrieker.removeFreeWill();
        spider.removeFreeWill();
        double flying = shrieker.getAttributeBaseValue(Attributes.FLYING_SPEED);
        helper.assertTrue(Math.abs(flying - Shrieker.FLYING_SPEED) < SAME, "a Shrieker's flying speed is " + flying);
        // Stale strength, as a summon with data or an old config would leave: its first tick puts the config's in.
        shrieker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
        shrieker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
        shrieker.setSinner(false);
        helper.assertTrue(shrieker.isSinner(), "a Shrieker was let off being a sinner");

        CompoundTag plain = new CompoundTag();
        spider.addAdditionalSaveData(plain);
        helper.assertFalse(spider.isSinner() || plain.contains("Sinner"), "a new Spider is a sinner, or says it is not");
        spider.setSinner(true);
        CompoundTag marked = new CompoundTag();
        spider.addAdditionalSaveData(marked);
        spider.setSinner(false);
        spider.readAdditionalSaveData(marked);
        helper.assertTrue(marked.getBoolean("Sinner") && spider.isSinner(), "a sinner's mark was not saved and read");
        marked.putString("Sinner", "yes");
        spider.readAdditionalSaveData(marked);
        helper.assertFalse(spider.isSinner(), "an odd Sinner value read as a sinner");
        marked.remove("Sinner");
        spider.setSinner(true);
        spider.readAdditionalSaveData(marked);
        helper.assertFalse(spider.isSinner(), "a Hollow saved with no mark read as a sinner");

        helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id("shrieker")) != null,
                "the Shrieker advancement did not load");
        List<DeferredHolder<SoundEvent, SoundEvent>> sounds = List.of(ModSounds.SHRIEKER_AMBIENT, ModSounds.SHRIEKER_HURT,
                ModSounds.SHRIEKER_DEATH, ModSounds.SHRIEKER_SWOOP, ModSounds.SHRIEKER_FLAP);
        for (DeferredHolder<SoundEvent, SoundEvent> sound : sounds) {
            helper.assertTrue(sound.isBound(), "the sound " + sound.getId() + " is not registered");
        }

        helper.runAfterDelay(1, () -> {
            double health = BleachConfig.get(BleachConfig.SHRIEKER_HEALTH);
            double damage = BleachConfig.get(BleachConfig.SHRIEKER_DAMAGE);
            helper.assertTrue(shrieker.getAttributeBaseValue(Attributes.MAX_HEALTH) == health
                            && shrieker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == damage,
                    "after its first tick a Shrieker has " + shrieker.getAttributeBaseValue(Attributes.MAX_HEALTH)
                            + " health and " + shrieker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + " damage");
            helper.assertTrue(Math.abs(shrieker.getHealth() - health) < EPSILON,
                    "a whole Shrieker came to " + shrieker.getHealth() + " of " + health + " health");
            shrieker.discard();
            spider.discard();
            helper.succeed();
        });
    }

    /**
     * A tear sent with a Shrieker, as openArrival sends one, for a strong soul: exactly one comes out, with the config's
     * strength, whole, hunting the soul that drew it from before its first tick, so only the rift can have set it on that
     * soul. In the Garganta tests' batch, so the only Hollows near are theirs.
     */
    @GameTest(template = "field", timeoutTicks = 200, batch = "garganta")
    public static void aGargantaBringsOneShriekerForItsDrawer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer drawer = aware(helper, new Vec3(8.5, 2.0, 8.5));
        int count = HollowLogic.groupSize(HollowKind.SHRIEKER, STRONG, Integer.MAX_VALUE, 0.0);
        GargantaRift rift = GargantaRift.arrival(level, helper.absoluteVec(new Vec3(8.0, 8.0, 8.0)), level.getGameTime(),
                Collections.nCopies(count, HollowKind.SHRIEKER), drawer, GargantaEvents.riftSize(HollowKind.SHRIEKER));
        helper.assertTrue(level.addFreshEntity(rift), "the world refused the Garganta");
        // The first Shrieker seen, with its age and its target then. Tests tick after the world, and a creature added
        // in the world's tick is not ticked until the next, so the rift's drop is seen before its first tick.
        Shrieker[] first = new Shrieker[1];
        int[] firstAge = new int[1];
        LivingEntity[] firstTarget = new LivingEntity[1];
        helper.succeedWhen(() -> {
            List<Shrieker> shriekers = level.getEntitiesOfClass(Shrieker.class, helper.getBounds(), Entity::isAlive);
            if (first[0] == null && !shriekers.isEmpty()) {
                first[0] = shriekers.get(0);
                firstAge[0] = first[0].tickCount;
                firstTarget[0] = first[0].getTarget();
            }
            helper.assertTrue(first[0] != null, "no Shrieker has come out of the Garganta");
            helper.assertTrue(firstAge[0] == 0 && firstTarget[0] == drawer,
                    "first seen at age " + firstAge[0] + ", the Shrieker hunted " + firstTarget[0]);
            helper.assertTrue(rift.isRemoved(), "the Garganta is still open");
            helper.assertTrue(shriekers.size() == 1, "the Garganta left " + shriekers.size() + " Shriekers, not 1");
            Shrieker shrieker = shriekers.get(0);
            helper.assertTrue(shrieker == first[0], "a second Shrieker came after the first was gone");
            helper.assertTrue(shrieker.getTarget() == drawer && drawer.getUUID().equals(shrieker.drawer()),
                    "the Shrieker hunts " + shrieker.getTarget() + ", drawn by " + shrieker.drawer());
            helper.assertTrue(shrieker.getAttributeBaseValue(Attributes.MAX_HEALTH)
                            == BleachConfig.get(BleachConfig.SHRIEKER_HEALTH)
                            && Math.abs(shrieker.getHealth() - shrieker.getMaxHealth()) < EPSILON,
                    "the Shrieker came with " + shrieker.getHealth() + " of " + shrieker.getMaxHealth() + " health");
            shriekers.forEach(Entity::discard);
        });
    }

    // ---- The mask and the bar ----

    /**
     * A still Shrieker: its bar shows to an aware soul by it, full; a blow to its mask does as many times a body blow's
     * harm as the config says and cracks it; the bar falls with the blows on the next tick, leaves a soul that stops
     * seeing it, and comes down from everyone when it dies.
     */
    @GameTest(template = "field")
    public static void aMaskBlowCracksAShriekerAndItsBarFalls(GameTestHelper helper) {
        ServerPlayer watcher = aware(helper, new Vec3(2.5, 2.0, 2.5));
        // Still shown the bar when the Shrieker dies, so only its death can take the bar from everyone.
        ServerPlayer witness = aware(helper, new Vec3(13.5, 2.0, 2.5));
        Shrieker shrieker = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), 8, 2, 8);
        BossBar bar = shrieker.bossBar();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(holds(bar, watcher) && holds(bar, witness),
                        "an aware soul was not shown the bar"))
                .thenExecute(() -> {
                    helper.assertTrue(bar.event().getProgress() == 1.0F, "a whole Shrieker's bar is at "
                            + bar.event().getProgress());
                    faceSouth(shrieker);
                    Vec3 at = shrieker.position();
                    // One behind it, aiming at the middle of its body; one in front, aiming at the mask.
                    Player behind = HollowTests.soulPlayer(helper, SoulState.HUMAN, STRIKER);
                    behind.moveTo(at.add(0.0, 0.0, -2.6));
                    behind.lookAt(EntityAnchorArgument.Anchor.EYES, shrieker.getBoundingBox().getCenter());
                    Player front = HollowTests.soulPlayer(helper, SoulState.HUMAN, STRIKER);
                    front.moveTo(at.add(0.0, 0.0, 2.5));
                    front.lookAt(EntityAnchorArgument.Anchor.EYES, HollowMask.maskCenter(shrieker));
                    DamageSources damage = helper.getLevel().damageSources();
                    float full = shrieker.getHealth();
                    shrieker.hurt(damage.playerAttack(behind), 8.0F);
                    float bodyDrop = full - shrieker.getHealth();
                    shrieker.invulnerableTime = 0;
                    float beforeMask = shrieker.getHealth();
                    shrieker.hurt(damage.playerAttack(front), 8.0F);
                    float maskDrop = beforeMask - shrieker.getHealth();
                    long now = helper.getLevel().getGameTime();
                    // Its armour (4) takes the same share of both blows, so the ratio is the multiplier.
                    double ratio = maskDrop / bodyDrop;
                    helper.assertTrue(bodyDrop > 0.0F
                                    && Math.abs(ratio - BleachConfig.get(BleachConfig.MASK_DAMAGE_MULTIPLIER)) < 0.05,
                            "a blow to the mask did " + maskDrop + " against " + bodyDrop + " to the body");
                    helper.assertTrue(shrieker.wasMaskStruckAt(now) && shrieker.isMaskCracked(),
                            "the blow to the mask was not noted, or did not crack it");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    float progress = bar.event().getProgress();
                    helper.assertTrue(progress < 1.0F && Math.abs(progress - BossBar.progress(shrieker)) < EPSILON,
                            "the bar is at " + progress + " for " + shrieker.getHealth() + " of " + shrieker.getMaxHealth());
                    shrieker.stopSeenByPlayer(watcher);
                    helper.assertTrue(!holds(bar, watcher) && holds(bar, witness),
                            "a soul that stopped seeing the Shrieker kept its bar, or another soul lost it");
                    shrieker.kill();
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(bar.players().isEmpty(), "a dead Shrieker's bar is still up");
                    shrieker.discard();
                })
                .thenSucceed();
    }

    /**
     * Two still Shriekers cut down at once: one by a Shinigami's Asauchi dissolves and its soul rises where it stood, and
     * the Shinigami has brought a Shrieker down; one by an iron sword is slain.
     */
    @GameTest(template = "field")
    public static void aZanpakutoPurifiesAShriekerAndASwordSlaysIt(GameTestHelper helper) {
        ServerPlayer purifier = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 2.5));
        purifier.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, AWARE));
        HollowTests.arm(purifier, ModItems.ASAUCHI.get());
        Player swordsman = HollowTests.shinigami(helper, AWARE);
        HollowTests.arm(swordsman, Items.IRON_SWORD);
        Shrieker purified = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), 4, 2, 10);
        Shrieker slain = helper.spawnWithNoFreeWill(ModEntities.SHRIEKER.get(), 11, 2, 10);
        Vec3 where = purified.position();
        DamageSources damage = helper.getLevel().damageSources();
        purified.hurt(damage.playerAttack(purifier), 1000.0F);
        slain.hurt(damage.playerAttack(swordsman), 1000.0F);
        helper.assertTrue(purified.isPurifying() && !slain.isPurifying(), "the Asauchi did not purify, or the sword did");
        helper.assertTrue(SoulLogic.get(purifier).hunt().purified() == 1, "the purifier's count did not grow");
        helper.assertTrue(broughtDown(purifier), "the purifier has not brought a Shrieker down");
        helper.assertTrue(SoulLogic.get(swordsman).hunt().slain() == 1, "the swordsman's count did not grow");
        helper.succeedWhen(() -> {
            helper.assertTrue(purified.getRemovalReason() == Entity.RemovalReason.KILLED && slain.isRemoved(),
                    "a Shrieker is still there: " + purified.getRemovalReason() + ", " + slain.getRemovalReason());
            AABB around = AABB.ofSize(where, 6.0, 6.0, 6.0);
            List<PlusSpirit> echoes = helper.getLevel().getEntitiesOfClass(PlusSpirit.class, around,
                    plus -> plus.isDeparting() && plus.position().distanceTo(where) <= 3.0);
            helper.assertFalse(echoes.isEmpty(), "no soul rose where the purified Shrieker stood");
        });
    }
}
