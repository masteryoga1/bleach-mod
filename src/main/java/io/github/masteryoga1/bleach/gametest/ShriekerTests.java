package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.BossBar;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowGatherGoal;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import io.github.masteryoga1.bleach.world.Shakonmaku.Side;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for the Shrieker: its row and the rules that bring it alone, the rift that brings it to its drawer, its fight in
 * the air, the mask, its stall and its bar, purified and slain in the air, and what holds it as it flies. Tests that run
 * beside others ask only about their own Shrieker and their own players, by object; the barrier walls about each test
 * keep other tests' souls out of a Shrieker's sight, so it hunts none of them.
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
    /** Room for the float steps of an attribute or a position. */
    private static final double SAME = 1.0E-6;
    /** A joined player cannot be hurt for its first 60 ticks (ServerPlayer's spawn invulnerability). */
    private static final int SPAWN_SAFE_TICKS = 61;
    /** How high a Shrieker's feet must be over its prey's to be out of reach: the prey's eyes (1.62) and reach (3). */
    private static final double OUT_OF_REACH = 4.6;
    /** How far a Shrieker must climb from where it struck to be back on its way up to its circle. */
    private static final double CLIMB = 3.0;
    /** The least ticks between its strike and its climb that its mask is where its prey's blade finds it. */
    private static final int CLAWS_LOW_REACHED = 10;
    /** How long the membrane's test watches its Shrieker. */
    private static final int HELD_TICKS = 120;
    /** How near the membrane's sphere its Shrieker must come, in blocks: it starts 8.9 in. */
    private static final double PRESSED = 2.0;
    /** How far its prey, set hanging in the air, may have moved up or down by the end. */
    private static final double HUNG = 0.1;
    /** The Sekkiseki roof: relative x and z from and to (inclusive), and its height, 5 over the floor's top. */
    private static final int ROOF_FROM = 4;
    private static final int ROOF_TO = 12;
    private static final int ROOF_Y = 7;

    private ShriekerTests() {
    }

    // ---- Helpers ----

    /** A player joined at this spot in survival with a Human soul that sees spirits: prey for any Hollow. */
    static ServerPlayer aware(GameTestHelper helper, Vec3 at) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, at);
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, AWARE));
        return player;
    }

    /** Whether the bar is shown to this very player object. */
    private static boolean holds(BossBar bar, ServerPlayer player) {
        return bar.players().stream().anyMatch(shown -> shown == player);
    }

    /** Turns a Hollow, head and body too, to face south (+Z), so its mask is on that side. */
    static void faceSouth(Hollow hollow) {
        hollow.setYRot(0.0F);
        hollow.setYHeadRot(0.0F);
        hollow.setYBodyRot(0.0F);
    }

    /** Whether this creature would run from released reiatsu: it was given the goal as it joined the world. */
    static boolean frightens(PathfinderMob mob) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof ReiatsuFleeGoal);
    }

    /** Whether this player has earned the advancement {@code bleach:shrieker}. */
    private static boolean broughtDown(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(BleachMod.id("shrieker"));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /**
     * Whether a blade this player swung at the Hollow's mask now would strike it: the mask within the player's reach of
     * its eyes, and met along the look first, or near enough first, as HollowMask judges a blow.
     */
    private static boolean bladeFindsMask(Hollow hollow, Player player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 mask = HollowMask.maskCenter(hollow);
        double reach = player.entityInteractionRange();
        Vec3 look = mask.subtract(eyes).normalize();
        return eyes.distanceTo(mask) <= reach
                && HollowMask.struck(hollow, eyes, eyes.add(look.scale(reach + 1.0)), HollowMask.MELEE_INFLATE);
    }

    /** How far inside the Shakonmaku's sphere a point lies, in blocks; less than none outside it. */
    private static double withinMembrane(Vec3 at) {
        double up = at.y - SeireiteiPlan.PLATEAU_Y;
        return SeireiteiPlan.SHAKONMAKU_RADIUS - Math.sqrt(at.x * at.x + up * up + at.z * at.z);
    }

    // ---- The row and the rift ----

    /**
     * The row: by name, never by chance, no Menos, alone through one rift as wide as the first Hollow's; never saved, a
     * flyer that finds its way through the air, with no gravity and a flyer's goals in place of a walking Hollow's, its
     * flying speed kept among its attributes; one that skipped spawning takes the config's health and damage on its first
     * tick, whole; it is a sinner whatever is set, and any other Hollow's mark is saved and read leniently; released
     * reiatsu never frightens it off. Its advancement and sounds.
     */
    @GameTest(template = "field")
    public static void aShriekerIsAFlyingBossThatComesAlone(GameTestHelper helper) {
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
        List<Goal> goals = shrieker.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).toList();
        helper.assertTrue(goals.stream().anyMatch(goal -> goal instanceof WaterAvoidingRandomFlyingGoal)
                        && goals.stream().noneMatch(goal -> goal instanceof MeleeAttackGoal
                        || goal instanceof LeapAtTargetGoal || goal instanceof HollowGatherGoal
                        || goal.getClass() == WaterAvoidingRandomStrollGoal.class),
                "a Shrieker keeps a walking Hollow's goals, or has no flyer's wander: " + goals);
        shrieker.removeFreeWill();
        spider.removeFreeWill();
        helper.assertTrue(shrieker.getNavigation() instanceof FlyingPathNavigation && shrieker.isNoGravity(),
                "a Shrieker finds its way on foot, or falls");
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
                ModSounds.SHRIEKER_DEATH, ModSounds.SHRIEKER_SWOOP, ModSounds.SHRIEKER_FLAP, ModSounds.SHRIEKER_LOOSE,
                ModSounds.SHRIEKER_LEECH_CLING, ModSounds.SHRIEKER_RING, ModSounds.SHRIEKER_LEECH_BURST);
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
            helper.assertTrue(shrieker.isNoGravity(), "a living Shrieker's first tick gave it gravity");
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

    // ---- The fight in the air ----

    /**
     * Once its prey can be hurt, a Shrieker set on it climbs over it higher than a blade swung from the ground reaches,
     * swoops and strikes it, keeps its claws low a while, its mask where the prey's blade finds it, and climbs back up.
     * The prey holds its ground against the blow, so the low circle is about the spot it was struck on.
     */
    @GameTest(template = "field", timeoutTicks = 300)
    public static void aShriekerCirclesOutOfReachThenSwoopsAndStrikes(GameTestHelper helper) {
        ServerPlayer prey = aware(helper, new Vec3(8.5, 2.0, 8.5));
        prey.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        Shrieker[] shrieker = new Shrieker[1];
        double[] highest = {Double.NEGATIVE_INFINITY};
        double[] struckAt = new double[1];
        int[] reached = new int[1];
        helper.startSequence()
                .thenExecuteAfter(SPAWN_SAFE_TICKS, () -> {
                    shrieker[0] = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(3.5, 2.0, 8.5));
                    shrieker[0].setTarget(prey);
                    // Its claws alone: it holds no leeches to loose.
                    shrieker[0].setLeechesHeld(0);
                })
                .thenWaitUntil(() -> {
                    boolean struck = prey.getLastHurtByMob() == shrieker[0] && prey.getHealth() < prey.getMaxHealth();
                    if (!struck) {
                        highest[0] = Math.max(highest[0], shrieker[0].getY() - prey.getY());
                    }
                    helper.assertTrue(struck, "the Shrieker has not struck its prey");
                })
                .thenExecute(() -> {
                    helper.assertTrue(highest[0] > OUT_OF_REACH,
                            "before its swoop the Shrieker rose only " + highest[0] + " over its prey");
                    struckAt[0] = shrieker[0].getY();
                })
                .thenWaitUntil(() -> {
                    if (bladeFindsMask(shrieker[0], prey)) {
                        reached[0]++;
                    }
                    helper.assertTrue(shrieker[0].getY() > struckAt[0] + CLIMB,
                            "the Shrieker has climbed only " + (shrieker[0].getY() - struckAt[0]) + " since it struck");
                })
                .thenExecute(() -> {
                    helper.assertTrue(reached[0] >= CLAWS_LOW_REACHED, "between its strike and its climb the Shrieker's "
                            + "mask was where its prey's blade finds it on " + reached[0] + " ticks");
                    shrieker[0].discard();
                })
                .thenSucceed();
    }

    // ---- The mask and the bar ----

    /**
     * A still Shrieker: its bar shows to an aware soul by it, full; a blow to its body leaves it free, and a blow to its
     * mask does as many times a body blow's harm as the config says, cracks it and stalls it; the bar falls with the
     * blows on the next tick, leaves a soul that stops seeing it, and comes down from everyone when it dies.
     */
    @GameTest(template = "field")
    public static void aMaskBlowStallsAShriekerAndItsBarFalls(GameTestHelper helper) {
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
                    helper.assertFalse(shrieker.isStalled(), "a blow to a Shrieker's body stalled it");
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
                    helper.assertTrue(shrieker.isStalled(), "a blow to a Shrieker's mask did not stall it");
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
     * Two Shriekers in the air, with their wits and diving, cut down at once: one by a Shinigami's Asauchi hangs where it
     * was as it dissolves and its soul rises there, and the Shinigami has brought a Shrieker down; one by an iron sword
     * falls. Neither dives in death.
     */
    @GameTest(template = "field", timeoutTicks = 100)
    public static void aPurifiedShriekerHangsAsItDissolvesAndASlainOneFalls(GameTestHelper helper) {
        ServerPlayer purifier = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 2.5));
        purifier.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, AWARE));
        HollowTests.arm(purifier, ModItems.ASAUCHI.get());
        Player swordsman = HollowTests.shinigami(helper, AWARE);
        HollowTests.arm(swordsman, Items.IRON_SWORD);
        // Killed before their first tick, so no wish to fly has moved either: only the air, or its lack, does.
        Shrieker purified = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(4.5, 6.0, 10.5));
        Shrieker slain = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(11.5, 6.0, 10.5));
        purified.setSwooping(true);
        slain.setSwooping(true);
        double hung = purified.getY(), from = slain.getY();
        DamageSources damage = helper.getLevel().damageSources();
        purified.hurt(damage.playerAttack(purifier), 1000.0F);
        slain.hurt(damage.playerAttack(swordsman), 1000.0F);
        helper.assertTrue(purified.isPurifying() && !slain.isPurifying(), "the Asauchi did not purify, or the sword did");
        helper.assertTrue(SoulLogic.get(purifier).hunt().purified() == 1, "the purifier's count did not grow");
        helper.assertTrue(broughtDown(purifier), "the purifier has not brought a Shrieker down");
        helper.assertTrue(SoulLogic.get(swordsman).hunt().slain() == 1, "the swordsman's count did not grow");
        helper.assertFalse(purified.isSwooping() || slain.isSwooping(), "a Shrieker still dives in death");
        double[] drift = {0.0};
        double[] lowest = {from};
        Vec3[] last = {purified.position()};
        helper.succeedWhen(() -> {
            if (!purified.isRemoved()) {
                drift[0] = Math.max(drift[0], Math.abs(purified.getY() - hung));
                last[0] = purified.position();
            }
            if (!slain.isRemoved()) {
                lowest[0] = Math.min(lowest[0], slain.getY());
            }
            helper.assertTrue(purified.getRemovalReason() == Entity.RemovalReason.KILLED && slain.isRemoved(),
                    "a Shrieker is still there: " + purified.getRemovalReason() + ", " + slain.getRemovalReason());
            helper.assertTrue(drift[0] <= 0.5, "the purified Shrieker moved " + drift[0] + " up or down as it dissolved");
            helper.assertTrue(from - lowest[0] > 2.0, "the slain Shrieker fell only " + (from - lowest[0]));
            Vec3 where = last[0];
            AABB around = AABB.ofSize(where, 6.0, 6.0, 6.0);
            List<PlusSpirit> echoes = helper.getLevel().getEntitiesOfClass(PlusSpirit.class, around,
                    plus -> plus.isDeparting() && plus.position().distanceTo(where) <= 3.0);
            helper.assertFalse(echoes.isEmpty(), "no soul rose where the purified Shrieker hung");
        });
    }

    // ---- What holds it ----

    /**
     * Inside Seireitei over the wall's top at the north-west, a Shrieker set on a Shinigami hanging in the air over the
     * Rukongai flies at it and stays inside on every tick, over the wall's top, and comes within 2 blocks of the
     * membrane, living and hunting still. A pin that the membrane's check of every living mover holds a flyer as it does
     * a walker; the north-west is no other test's in the batch.
     */
    @GameTest(template = "empty", batch = ShakonmakuTests.BATCH, timeoutTicks = 200)
    public static void theShakonmakuHoldsAFlyingShrieker(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = ShakonmakuTests.hold(helper, -23, -23);
        Vec3 start = new Vec3(-365.5, 120.0, -365.5), out = new Vec3(-376.5, 120.0, -376.5);
        helper.assertTrue(Shakonmaku.side(start) == Side.INSIDE && Shakonmaku.side(out) == Side.OUTSIDE,
                start + " is " + Shakonmaku.side(start) + " and " + out + " is " + Shakonmaku.side(out));
        ServerPlayer prey = ShakonmakuTests.soulAt(helper, held.soul, GameType.SURVIVAL, out);
        // A joined player falls as any body does: this one hangs where it was set.
        prey.setNoGravity(true);
        Shrieker shrieker = ModEntities.SHRIEKER.get().create(held.soul);
        helper.assertTrue(shrieker != null, "no Shrieker");
        shrieker.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        held.add(shrieker);
        shrieker.setTarget(prey);
        double[] nearest = {withinMembrane(start)};
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, shrieker.tickCount > 0 && prey.level() == held.soul
                                && held.soul.isPositionEntityTicking(prey.blockPosition()),
                        "the Shrieker or its prey has not begun to tick in Soul Society"))
                .thenExecuteFor(HELD_TICKS, () -> {
                    helper.assertTrue(Shakonmaku.side(shrieker.position()) == Side.INSIDE
                            && shrieker.getY() >= SeireiteiPlan.WALL_TOP_Y, "the Shrieker got to " + shrieker.position());
                    nearest[0] = Math.min(nearest[0], withinMembrane(shrieker.position()));
                })
                .thenExecute(() -> {
                    helper.assertTrue(prey.isAlive() && Math.abs(prey.getY() - out.y) < HUNG,
                            "the prey did not hang where it was set: it is at " + prey.position());
                    helper.assertTrue(shrieker.isAlive() && shrieker.getTarget() == prey && nearest[0] < PRESSED,
                            "the Shrieker came no nearer the membrane than " + nearest[0]
                                    + " blocks, or lost its prey or its life");
                })
                .thenSucceed();
    }

    /**
     * Over a soul under a 9 by 9 roof of Sekkiseki, a Shrieker set on it never goes into the roof or through it, as with
     * any block: it goes round by the air and comes in under it. The roof goes when the test ends, passed or failed.
     */
    @GameTest(template = "field", timeoutTicks = 200)
    public static void sekkisekiHoldsAShriekerAsAnyBlockWould(GameTestHelper helper) {
        SekkisekiTests.Restore.on(helper)
                .fill(ROOF_FROM, ROOF_Y, ROOF_FROM, ROOF_TO, ROOF_Y, ROOF_TO, ModBlocks.SEKKISEKI.get());
        Vec3 corner = helper.absoluteVec(new Vec3(ROOF_FROM, ROOF_Y, ROOF_FROM));
        double span = ROOF_TO + 1 - ROOF_FROM;
        AABB roof = new AABB(corner, corner.add(span, 1.0, span));
        ServerPlayer prey = aware(helper, new Vec3(8.5, 2.0, 8.5));
        Shrieker shrieker = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(8.5, ROOF_Y + 2.5, 8.5));
        shrieker.setTarget(prey);
        shrieker.setLeechesHeld(0);
        boolean[] into = {false};
        helper.succeedWhen(() -> {
            AABB body = shrieker.getBoundingBox();
            // Kept once seen: a Shrieker that was ever in the roof fails, wherever it is after.
            into[0] |= body.intersects(roof.deflate(SAME));
            helper.assertFalse(into[0], "the Shrieker went into the Sekkiseki");
            Vec3 middle = body.getCenter();
            helper.assertTrue(body.maxY <= roof.minY + SAME && middle.x > roof.minX && middle.x < roof.maxX
                    && middle.z > roof.minZ && middle.z < roof.maxZ, "the Shrieker has not come in under the roof: "
                    + "it is at " + shrieker.position());
            helper.assertTrue(shrieker.isAlive() && shrieker.getTarget() == prey, "the Shrieker lost its prey or its life");
            shrieker.discard();
        });
    }
}
