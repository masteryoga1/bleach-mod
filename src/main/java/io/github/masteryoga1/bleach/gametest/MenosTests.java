package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Gillian;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.HoundHollow;
import io.github.masteryoga1.bleach.entity.Menos;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for the Menos Grande: what is registered, when one comes, the Gillian's hit boxes, its
 * descent, retreat, tongue and swat, the Adjuchas's hunger and pack, and the Cero, fired by hand
 * through {@link CeroBeam#fire} or charged through {@link CeroBeam#charge}. Tests with a Gillian whose
 * AI or physics runs leave the sky open, as a fifteen-block body would sit in the barrier roof.
 *
 * <p>A husk stands where a mundane monster is needed: a zombie would burn in the daylight that comes
 * through the barrier, and its health is what these tests watch. Every Menos goes at the end of its
 * test, so none is left to lead another test's pack or reach for another test's Hollows.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class MenosTests {
    private static final float EPSILON = 1.0E-3F;
    /** How close a point or a box's middle must be to where it should be. */
    private static final double PLACE_EPSILON = 0.01;
    /** Facing east (+X): yaw, head and body. */
    private static final float EAST = -90.0F;

    private MenosTests() {
    }

    /** A test Cero: these numbers, no knockback, and no blast or blocks unless asked. */
    private static CeroBeam.Spec spec(float damage, double range, float radius, float blastRadius, int maxBlocks,
                                      int chargeTicks, int aimLockTicks, boolean breaksBlocks) {
        return new CeroBeam.Spec(damage, range, radius, blastRadius, 0.5F, maxBlocks, 0.0F, CeroBeam.MENOS_RED,
                chargeTicks, aimLockTicks, breaksBlocks);
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, double epsilon, String what) {
        helper.assertTrue(Math.abs(actual - expected) < epsilon, what + " was " + actual + ", not " + expected);
    }

    private static void assertNear(GameTestHelper helper, Vec3 actual, Vec3 expected, double epsilon, String what) {
        helper.assertTrue(actual.distanceTo(expected) < epsilon, what + " was " + actual + ", not " + expected);
    }

    private static void assertFullHealth(GameTestHelper helper, LivingEntity entity, String what) {
        helper.assertTrue(entity.getHealth() == entity.getMaxHealth(), what + " was hurt, to " + entity.getHealth());
    }

    /** Turns a mob, head and body too, to this yaw. */
    static void face(LivingEntity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
    }

    /** A still husk here. */
    static Husk husk(GameTestHelper helper, int x, int z) {
        return QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.HUSK, x, 2, z));
    }

    /** A still Adjuchas here, facing east, as the caster of a Cero: with NoAI no goal can change its target. */
    private static Adjuchas caster(GameTestHelper helper, int x, int z) {
        Adjuchas adjuchas = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.ADJUCHAS.get(), x, 2, z));
        face(adjuchas, EAST);
        return adjuchas;
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    // ---- Registration and arrivals ----

    @GameTest(template = "empty")
    public static void menosAreRegistered(GameTestHelper helper) {
        List<EntityType<?>> types = List.of(ModEntities.GILLIAN.get(), ModEntities.ADJUCHAS.get(),
                ModEntities.CERO_BEAM.get());
        for (EntityType<?> type : types) {
            String name = EntityType.getKey(type).toString();
            helper.assertFalse(type.canSerialize(), name + " is saved with the world");
            helper.assertTrue(type.fireImmune(), name + " burns");
            helper.assertTrue(type.is(SoulLogic.SPIRITS), name + " is not in bleach:spirits");
        }
        for (EntityType<?> type : List.of(ModEntities.GILLIAN.get(), ModEntities.ADJUCHAS.get())) {
            String name = EntityType.getKey(type).toString();
            helper.assertTrue(type.is(BleachTags.HOLLOWS), name + " is not in bleach:hollows");
            helper.assertTrue(type.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER), name + " drowns");
        }
        helper.assertFalse(ModEntities.CERO_BEAM.get().is(BleachTags.HOLLOWS), "a Cero is in bleach:hollows");

        EntityDimensions gillian = ModEntities.GILLIAN.get().getDimensions();
        assertNear(helper, gillian.width(), 3.4, EPSILON, "a Gillian's width");
        assertNear(helper, gillian.height(), 15.0, EPSILON, "a Gillian's height");
        assertNear(helper, gillian.eyeHeight(), 13.0, EPSILON, "a Gillian's eye height");
        EntityDimensions adjuchas = ModEntities.ADJUCHAS.get().getDimensions();
        assertNear(helper, adjuchas.width(), 2.0, EPSILON, "an Adjuchas's width");
        assertNear(helper, adjuchas.height(), 2.5, EPSILON, "an Adjuchas's height");
        assertNear(helper, adjuchas.eyeHeight(), 1.9, EPSILON, "an Adjuchas's eye height");

        helper.assertTrue(ModEntities.hollowType(HollowKind.GILLIAN) == ModEntities.GILLIAN.get(),
                "the Gillian kind has the wrong entity type");
        helper.assertTrue(ModEntities.hollowType(HollowKind.ADJUCHAS) == ModEntities.ADJUCHAS.get(),
                "the Adjuchas kind has the wrong entity type");
        // The Vasto Lorde is a Menos class in canon, but none of the Menos rules is its: only isVasto names it.
        for (HollowKind kind : HollowKind.values()) {
            boolean menos = kind == HollowKind.GILLIAN || kind == HollowKind.ADJUCHAS;
            helper.assertTrue(kind.isMenos() == menos, kind.getSerializedName() + (menos ? " is not" : " is") + " a Menos");
            boolean vasto = kind == HollowKind.VASTO_LORDE;
            helper.assertTrue(kind.isVasto() == vasto,
                    kind.getSerializedName() + (vasto ? " is not" : " is") + " a Vasto Lorde");
        }
        List<HollowKind> pool = HollowLogic.arrivalPool(100_000.0F);
        helper.assertFalse(pool.contains(HollowKind.GILLIAN) || pool.contains(HollowKind.ADJUCHAS),
                "a Menos is in the Garganta's arrival pool: " + pool);

        List<DeferredHolder<SoundEvent, SoundEvent>> sounds = List.of(ModSounds.GILLIAN_AMBIENT, ModSounds.GILLIAN_HURT,
                ModSounds.GILLIAN_DEATH, ModSounds.GILLIAN_STEP, ModSounds.ADJUCHAS_AMBIENT, ModSounds.ADJUCHAS_HURT,
                ModSounds.ADJUCHAS_DEATH, ModSounds.CERO_CHARGE, ModSounds.CERO_FIRE);
        for (DeferredHolder<SoundEvent, SoundEvent> sound : sounds) {
            helper.assertTrue(sound.isBound(), "the sound " + sound.getId() + " is not registered");
        }

        DamageType cero = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).get(CeroBeam.DAMAGE);
        helper.assertTrue(cero != null, "there is no Cero damage type");
        helper.assertTrue("bleach.cero".equals(cero.msgId()), "the Cero's damage message is " + cero.msgId());
        helper.assertTrue(cero.scaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                "the Cero's damage scales " + cero.scaling());

        helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id("menos_grande")) != null,
                "the Menos Grande advancement did not load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void menosArrivalRules(GameTestHelper helper) {
        helper.assertTrue(Menos.arrivalKind(299.0F, 0.0, 0.0) == null, "a soul too weak drew a Menos");
        helper.assertTrue(Menos.arrivalKind(300.0F, 0.049, 0.9) == HollowKind.GILLIAN,
                "a strong soul's low roll drew no Gillian");
        helper.assertTrue(Menos.arrivalKind(300.0F, 0.05, 0.0) == null, "a roll past the share drew a Menos");
        helper.assertTrue(Menos.arrivalKind(499.0F, 0.0, 0.0) == HollowKind.GILLIAN,
                "a soul short of an Adjuchas's need drew no Gillian");
        helper.assertTrue(Menos.arrivalKind(500.0F, 0.0, 0.349) == HollowKind.ADJUCHAS,
                "a strong soul drew no Adjuchas pack");
        helper.assertTrue(Menos.arrivalKind(500.0F, 0.0, 0.35) == HollowKind.GILLIAN,
                "a roll past the Adjuchas share drew no Gillian");

        helper.assertTrue(Menos.packSize(500.0F, 10) == 2, "a pack for 500 is " + Menos.packSize(500.0F, 10));
        helper.assertTrue(Menos.packSize(800.0F, 10) == 3, "a pack for 800 is " + Menos.packSize(800.0F, 10));
        helper.assertTrue(Menos.packSize(800.0F, 1) == 1, "a pack with room for one is " + Menos.packSize(800.0F, 1));
        helper.assertTrue(Menos.packSize(800.0F, 0) == 1, "a pack with no room is " + Menos.packSize(800.0F, 0));

        CeroBeam.Spec clamped = new CeroBeam.Spec(-5.0F, 0.0, 0.0F, -1.0F, 2.0F, -3, -1.0F, 0x1FFFFFF, 0, 50, true);
        assertNear(helper, clamped.damage(), 0.0, 1.0E-6, "the clamped damage");
        assertNear(helper, clamped.range(), 1.0, 1.0E-6, "the clamped range");
        assertNear(helper, clamped.radius(), 0.05, 1.0E-6, "the clamped radius");
        assertNear(helper, clamped.blastRadius(), 0.0, 1.0E-6, "the clamped blast radius");
        assertNear(helper, clamped.blastShare(), 1.0, 1.0E-6, "the clamped blast share");
        helper.assertTrue(clamped.maxBlocks() == 0, "the clamped block count is " + clamped.maxBlocks());
        assertNear(helper, clamped.knockback(), 0.0, 1.0E-6, "the clamped knockback");
        helper.assertTrue(clamped.colour() == 0xFFFFFF, "the clamped colour is " + Integer.toHexString(clamped.colour()));
        helper.assertTrue(clamped.chargeTicks() == 1, "the clamped charge is " + clamped.chargeTicks());
        helper.assertTrue(clamped.aimLockTicks() == 1, "the clamped aim lock is " + clamped.aimLockTicks());
        helper.succeed();
    }

    // ---- The Gillian ----

    @GameTest(template = "field", skyAccess = true, timeoutTicks = 40)
    public static void gillianHasPartsWhereItsBodyIs(GameTestHelper helper) {
        Gillian gillian = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.GILLIAN.get(), 8, 2, 8));
        face(gillian, 0.0F);
        // The parts are placed in aiStep, which runs for a NoAI mob too, so after one tick.
        helper.runAfterDelay(1, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(gillian.isMultipartEntity() && gillian.getParts().length == 2,
                    "a Gillian does not have two parts");
            AABB body = gillian.body().getBoundingBox();
            assertNear(helper, body.minY, gillian.getY() + 4.0, EPSILON, "the bottom of the body part");
            assertNear(helper, body.maxY, gillian.getY() + 13.0, EPSILON, "the top of the body part");
            assertNear(helper, body.getXsize(), 3.4, EPSILON, "the width of the body part");
            Vec3 mask = HollowMask.maskCenter(gillian);
            assertNear(helper, gillian.head().getBoundingBox().getCenter(), mask, PLACE_EPSILON,
                    "the middle of the head part");

            AABB atMask = AABB.ofSize(mask, 0.5, 0.5, 0.5);
            helper.assertTrue(level.getEntities((Entity) null, atMask, entity -> true).contains(gillian.head()),
                    "a lookup at the mask did not find the head part");
            List<LivingEntity> atMaskCreatures = SpiritProjectiles.creaturesIn(level, atMask, entity -> true);
            helper.assertTrue(atMaskCreatures.equals(List.of(gillian)), "the creatures at the mask were " + atMaskCreatures);
            List<LivingEntity> inBody = SpiritProjectiles.creaturesIn(level, gillian.getBoundingBox(), entity -> true);
            helper.assertTrue(inBody.size() == 1, "the creatures in a Gillian's box were " + inBody);

            Vec3 from = mask.add(0.0, 0.0, 8.0);
            Arrow arrow = HollowTests.arrow(helper, from, mask.subtract(from));
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, arrow, from, mask,
                    new AABB(from, mask).inflate(1.0), entity -> entity.isPickable() && !entity.isSpectator());
            helper.assertTrue(hit != null && hit.getEntity() instanceof Gillian.Part part && part.getParent() == gillian,
                    "an arrow at the mask struck " + (hit != null ? hit.getEntity() : "nothing"));
            helper.assertTrue(HollowMask.struck(gillian, from, mask, HollowMask.PROJECTILE_INFLATE),
                    "an arrow at the mask missed it");

            assertNear(helper, SoulLogic.get(gillian).maxReiryoku(), 450.0, EPSILON, "a Gillian's reiatsu");

            helper.assertTrue(gillian.head().hurt(level.damageSources().generic(), 5.0F), "the head part took no hurt");
            helper.assertTrue(gillian.getHealth() < gillian.getMaxHealth(),
                    "a hurt to the head part did not reach the Gillian");
            gillian.body().kill();
            helper.assertFalse(gillian.isAlive(), "killing the body part left the Gillian alive");
            discard(gillian);
            helper.succeed();
        });
    }

    @GameTest(template = "field", skyAccess = true, timeoutTicks = 200)
    public static void gillianComesDownOnlyWithRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(x, 10, z, Blocks.STONE);
            }
        }
        Vec3 tear = helper.absoluteVec(new Vec3(8.5, 15.0, 8.5));
        // Every column of the spiral meets the roof or the barrier wall.
        helper.assertTrue(Gillian.skyLanding(level, tear) == null, "a Gillian found room under a roof");

        for (int x = 6; x <= 11; x++) {
            for (int z = 6; z <= 11; z++) {
                helper.setBlock(x, 10, z, Blocks.AIR);
            }
        }
        Vec3 feet = Gillian.skyLanding(level, tear);
        helper.assertTrue(feet != null, "a Gillian found no room through the opened roof");
        assertNear(helper, feet, helper.absoluteVec(new Vec3(8.5, 6.0, 8.5)), EPSILON, "where the Gillian comes down");
        Gillian gillian = Gillian.emerge(level, feet, null);
        helper.assertTrue(gillian != null, "the Gillian could not come through");
        helper.assertTrue(gillian.isDescending(), "the Gillian is not coming down");
        helper.assertTrue(gillian.hasEffect(MobEffects.SLOW_FALLING), "the Gillian is not falling slowly");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(gillian.onGround() && !gillian.isDescending(), "the Gillian never landed");
                    helper.assertFalse(gillian.hasEffect(MobEffects.SLOW_FALLING), "the landed Gillian still falls slowly");
                    helper.assertTrue(gillian.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                            "the landing did not slow the Gillian");
                    helper.assertTrue(gillian.baitSite() == null, "a Gillian that came by itself is held to a bait");
                })
                .thenExecute(() -> discard(gillian))
                .thenSucceed();
    }

    @GameTest(template = "field", skyAccess = true, timeoutTicks = 200)
    public static void gillianRetreatsIntoTheSky(GameTestHelper helper) {
        Gillian gillian = helper.spawn(ModEntities.GILLIAN.get(), 8, 2, 8);
        gillian.setHealth(0.2F * gillian.getMaxHealth());
        double startY = gillian.getY();
        GargantaRift[] tear = new GargantaRift[1];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(gillian.isRetreating(), "a beaten Gillian did not pull back");
                    List<GargantaRift> rifts = helper.getLevel().getEntitiesOfClass(GargantaRift.class,
                            gillian.getBoundingBox().inflate(4.0, 16.0, 4.0));
                    helper.assertFalse(rifts.isEmpty(), "no tear opened above the retreating Gillian");
                    tear[0] = rifts.get(0);
                    helper.assertTrue(tear[0].size() == 2.5F, "the Gillian's tear is " + tear[0].size() + " across");
                    helper.assertTrue(tear[0].isQuiet(), "the Gillian's tear beeps the pagers");
                    float before = gillian.getHealth();
                    helper.assertTrue(gillian.hurt(helper.getLevel().damageSources().generic(), 1.0F),
                            "a retreating Gillian took no hurt");
                    helper.assertTrue(gillian.getHealth() < before, "a retreating Gillian lost no health");
                })
                .thenExecuteAfter(40, () -> helper.assertTrue(gillian.getY() > startY + 2.0,
                        "the Gillian rose only to " + (gillian.getY() - startY)))
                .thenWaitUntil(() -> helper.assertTrue(gillian.isRemoved(), "the Gillian never left"))
                .thenExecute(() -> {
                    if (tear[0] != null) {
                        discard(tear[0]);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "field", skyAccess = true, timeoutTicks = 200)
    public static void gillianEatsLesserHollowsWithItsTongue(GameTestHelper helper) {
        Gillian gillian = helper.spawn(ModEntities.GILLIAN.get(), 3, 2, 8);
        gillian.setHealth(200.0F);
        // Nine blocks away, within the reach of ten, and 50 reiatsu against 450.
        SpiderHollow spider = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 12, 2, 8));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(gillian.feedingOn() == spider && spider.isSeized(),
                        "the Gillian never drew the Spider Hollow up"))
                .thenWaitUntil(() -> helper.assertFalse(spider.isAlive(), "the Gillian never swallowed the Spider Hollow"))
                .thenExecute(() -> {
                    // 200 and a quarter of 300; regeneration only adds.
                    helper.assertTrue(gillian.getHealth() >= 274.9F,
                            "eating healed the Gillian only to " + gillian.getHealth());
                    assertNear(helper, SoulLogic.reiatsu(gillian), 495.0, EPSILON, "the fed Gillian's reiatsu");
                    helper.assertTrue(gillian.feedingOn() == null, "the Gillian still holds a Hollow");
                    discard(gillian, spider);
                })
                .thenSucceed();
    }

    @GameTest(template = "field", skyAccess = true, timeoutTicks = 200)
    public static void gillianSwatsWithoutAPath(GameTestHelper helper) {
        Gillian gillian = helper.spawn(ModEntities.GILLIAN.get(), 4, 2, 8);
        face(gillian, EAST);
        // Four blocks between the middles: inside the swat's reach and closer than a Cero would fire.
        PlusSpirit plus = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 8, 2, 8));
        gillian.setTarget(plus);
        helper.assertTrue(gillian.getNavigation().createPath(plus, 0) == null, "a Gillian found a path");
        // 15 health and 14 a swat: two swats, about 50 ticks apart.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(plus.isAlive(), "the Gillian never swatted the Plus down"))
                .thenExecute(() -> discard(gillian, plus))
                .thenSucceed();
    }

    // ---- The Adjuchas ----

    @GameTest(template = "field", timeoutTicks = 300)
    public static void adjuchasEatWeakerHollowsToHeal(GameTestHelper helper) {
        // Spawned without finalizeSpawn, so at size 1 and reiatsu 900, well above the Hound's.
        Adjuchas adjuchas = helper.spawn(ModEntities.ADJUCHAS.get(), 3, 2, 8);
        HoundHollow hound = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.HOUND_HOLLOW.get(), 12, 2, 8));
        // Made now rather than on the first tick, so the reiatsu compared below is the souls' and not their health.
        adjuchas.soul();
        hound.soul();
        helper.assertFalse(adjuchas.isHungry(), "an Adjuchas at full health is hungry");
        helper.assertFalse(adjuchas.isEdible(hound), "an Adjuchas at full health would eat the Hound");

        adjuchas.setHealth(40.0F);
        helper.assertTrue(adjuchas.isHungry(), "an Adjuchas at 40 health is not hungry");
        helper.assertTrue(adjuchas.isEdible(hound), "a hungry Adjuchas would not eat a weaker Hound");
        helper.assertFalse(adjuchas.isEdible(adjuchas), "an Adjuchas would eat a Menos");
        helper.assertFalse(adjuchas.isAlliedTo(hound), "a hungry Adjuchas is allied to its meal");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(hound.isAlive(), "the hungry Adjuchas never killed the Hound"))
                .thenExecute(() -> {
                    // 40 and a quarter of 140.
                    helper.assertTrue(adjuchas.getHealth() >= 74.9F,
                            "eating healed the Adjuchas only to " + adjuchas.getHealth());
                    assertNear(helper, SoulLogic.reiatsu(adjuchas), 990.0, EPSILON, "the fed Adjuchas's reiatsu");
                    discard(adjuchas, hound);
                })
                .thenSucceed();
    }

    /** In a batch of its own, so no Adjuchas of another test is near enough to lead this pack. */
    @GameTest(template = "field", batch = "adjuchas_pack")
    public static void adjuchasShareTheirPrey(GameTestHelper helper) {
        Adjuchas first = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.ADJUCHAS.get(), 4, 2, 4));
        Adjuchas second = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.ADJUCHAS.get(), 10, 2, 10));
        PlusSpirit plus = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 8, 2, 2));
        try {
            // Made first, the first has the lower id.
            helper.assertTrue(first.packLeader() == first, "the first Adjuchas does not lead itself");
            helper.assertTrue(second.packLeader() == first, "the second Adjuchas does not follow the first");
            first.setTarget(plus);
            first.shareTarget();
            helper.assertTrue(second.getTarget() == plus, "the pack's prey was not shared");
        } finally {
            discard(first, second, plus);
        }
        helper.succeed();
    }

    // ---- Cero ----

    @GameTest(template = "field")
    public static void ceroStrikesOnlyWhatAHollowMay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Adjuchas adjuchas = caster(helper, 1, 8);
        Husk husk = husk(helper, 4, 8);
        SpiderHollow spider = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 6, 2, 8));
        PlusSpirit plus = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 8, 2, 8));
        AcademyInstructor instructor = QuincyTests.still(
                helper.spawnWithNoFreeWill(ModEntities.ACADEMY_INSTRUCTOR.get(), 10, 2, 8));
        Pig pig = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 12, 2, 8));
        adjuchas.setLastHurtByMob(husk);
        try {
            Vec3 from = helper.absoluteVec(new Vec3(2.5, 3.0, 8.5));
            CeroBeam.Shot shot = CeroBeam.fire(level, adjuchas, null, from, new Vec3(1.0, 0.0, 0.0),
                    spec(6.0F, 12.0, 0.6F, 0.0F, 0, 1, 0, false));
            helper.assertTrue(shot.struck().equals(List.of(husk)), "the Cero struck " + shot.struck());
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the Cero did not hurt the husk that hurt its caster");
            assertFullHealth(helper, spider, "a Spider Hollow");
            assertFullHealth(helper, plus, "a Plus");
            assertFullHealth(helper, instructor, "the instructor");
            assertFullHealth(helper, pig, "a pig");
            helper.assertFalse(shot.hitBlock(), "the Cero burst on a block in the open");
            assertNear(helper, shot.to().x, from.x + 12.0, PLACE_EPSILON, "where the Cero ended");

            Player aware = HollowTests.soulPlayer(helper, SoulState.HUMAN, 40.0F);
            Player plusPlayer = HollowTests.soulPlayer(helper, SoulState.PLUS, 40.0F);
            Player creative = helper.makeMockPlayer(GameType.CREATIVE);
            creative.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, 40.0F));
            helper.assertTrue(CeroBeam.mayStrike(adjuchas, aware), "an Adjuchas's Cero spares an aware soul");
            helper.assertFalse(CeroBeam.mayStrike(adjuchas, plusPlayer), "an Adjuchas's Cero strikes a Plus player");
            helper.assertFalse(CeroBeam.mayStrike(adjuchas, creative), "an Adjuchas's Cero strikes a player in creative");
            helper.assertFalse(CeroBeam.mayStrike(adjuchas, spider), "an Adjuchas's Cero strikes a Hollow");
            helper.assertFalse(CeroBeam.mayStrike(adjuchas, plus), "an Adjuchas's Cero strikes a Plus");
            helper.assertFalse(CeroBeam.mayStrike(adjuchas, instructor), "an Adjuchas's Cero strikes the instructor");
            helper.assertFalse(CeroBeam.mayStrike(adjuchas, pig), "an Adjuchas's Cero strikes a pig");
            helper.assertTrue(CeroBeam.mayStrike(null, pig), "a Cero of no one's spares a pig");
            helper.assertFalse(CeroBeam.mayStrike(null, plus), "a Cero of no one's strikes a Plus");
        } finally {
            discard(adjuchas, husk, spider, plus, instructor, pig);
        }
        helper.succeed();
    }

    /** In a batch of its own, so no other test sees mob griefing change. */
    @GameTest(template = "field", batch = "cero_griefing")
    public static void ceroStopsAtWallsAndCarvesWithGriefing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 2; y <= 5; y++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(8, y, z, Blocks.DIRT);
                helper.setBlock(9, y, z, Blocks.STONE);
            }
        }
        Adjuchas adjuchas = caster(helper, 1, 8);
        Husk husk = husk(helper, 11, 8);
        adjuchas.setLastHurtByMob(husk);
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean saved = griefing.get();
        Vec3 from = helper.absoluteVec(new Vec3(2.5, 3.0, 8.5));
        Vec3 east = new Vec3(1.0, 0.0, 0.0);
        CeroBeam.Spec spec = spec(6.0F, 12.0, 0.5F, 1.5F, 6, 1, 0, true);
        try {
            griefing.set(false, level.getServer());
            CeroBeam.Shot held = CeroBeam.fire(level, adjuchas, null, from, east, spec);
            helper.assertTrue(held.hitBlock(), "the Cero went through a dirt wall");
            assertNear(helper, held.to().x, helper.absoluteVec(new Vec3(8.0, 3.0, 8.5)).x, PLACE_EPSILON,
                    "where the Cero met the wall");
            helper.assertTrue(held.blocksBroken() == 0,
                    "the Cero broke " + held.blocksBroken() + " blocks without mob griefing");
            helper.assertBlockPresent(Blocks.DIRT, 8, 3, 8);
            assertFullHealth(helper, husk, "the husk behind the walls");

            griefing.set(true, level.getServer());
            CeroBeam.Shot carved = CeroBeam.fire(level, adjuchas, null, from, east, spec);
            helper.assertTrue(carved.blocksBroken() >= 1 && carved.blocksBroken() <= 6,
                    "the Cero broke " + carved.blocksBroken() + " blocks with mob griefing");
            helper.assertBlockPresent(Blocks.AIR, 8, 3, 8);
            // Stone is too hard for a Cero to blast.
            helper.assertBlockPresent(Blocks.STONE, 9, 3, 8);
            assertFullHealth(helper, husk, "the husk behind the walls");
        } finally {
            griefing.set(saved, level.getServer());
            discard(adjuchas, husk);
        }
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 60)
    public static void ceroAimLocksBeforeItFires(GameTestHelper helper) {
        Adjuchas adjuchas = caster(helper, 2, 8);
        Husk target = husk(helper, 10, 8);
        Husk attacker = husk(helper, 12, 3);
        adjuchas.setTarget(target);
        // Remembered for 100 ticks, so the Cero may strike it when it fires.
        adjuchas.setLastHurtByMob(attacker);
        CeroBeam beam = CeroBeam.charge(helper.getLevel(), adjuchas, spec(6.0F, 16.0, 0.5F, 0.0F, 0, 20, 10, false));
        helper.assertTrue(beam != null && beam.isCharging(), "the Cero did not start to gather");
        helper.startSequence()
                // The aim locked at tick 10. After it the target steps out of the line and the other husk into it.
                .thenExecuteAfter(14, () -> {
                    helper.moveTo(target, 10.5F, 2.0F, 3.5F);
                    helper.moveTo(attacker, 12.5F, 2.0F, 8.5F);
                })
                .thenExecuteAfter(12, () -> {
                    helper.assertTrue(beam.isFired(), "the Cero never fired");
                    CeroBeam.Shot shot = beam.lastShot();
                    helper.assertTrue(shot != null && shot.struck().equals(List.of(attacker)),
                            "the Cero along the locked aim struck " + (shot != null ? shot.struck() : "nothing"));
                    assertFullHealth(helper, target, "the husk that left the line");
                    helper.assertTrue(attacker.getHealth() < attacker.getMaxHealth(),
                            "the husk that stepped into the line was spared");
                    discard(beam, adjuchas, target, attacker);
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 40)
    public static void ceroFizzlesWhenItsCasterFalls(GameTestHelper helper) {
        Adjuchas adjuchas = caster(helper, 2, 8);
        Husk target = husk(helper, 10, 8);
        adjuchas.setTarget(target);
        CeroBeam beam = CeroBeam.charge(helper.getLevel(), adjuchas, spec(6.0F, 16.0, 0.5F, 0.0F, 0, 20, 5, false));
        helper.assertTrue(beam != null && beam.isCharging(), "the Cero did not start to gather");
        helper.startSequence()
                .thenExecuteAfter(5, () -> adjuchas.kill())
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(beam.isRemoved(), "the Cero outlived its caster");
                    helper.assertFalse(beam.isFired(), "the Cero fired for a dead caster");
                    helper.assertTrue(beam.lastShot() == null, "a fizzled Cero has a shot");
                })
                .thenExecuteAfter(22, () -> {
                    assertFullHealth(helper, target, "the dead caster's target");
                    discard(adjuchas, target);
                })
                .thenSucceed();
    }
}
