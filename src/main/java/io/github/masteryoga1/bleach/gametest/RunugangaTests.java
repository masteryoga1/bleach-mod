package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.Runuganga;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Runuganga: his body (T1). Every body is discarded as the test ends; he stands past a field's top, so his
 * test runs in a batch of its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class RunugangaTests {
    private static final float EPSILON = 1.0E-3F;

    private RunugangaTests() {
    }

    /** A still body of his standing at this spot of the field, his goals kept but never run, discarded as the test ends. */
    private static Runuganga still(GameTestHelper helper, double x, double z) {
        return kept(helper, QuincyTests.still(helper.spawn(ModEntities.RUNUGANGA.get(), new Vec3(x, 2.0, z))));
    }

    /** He is discarded as the test ends, passed or failed: a hold of no chunks keeps him for that. */
    private static Runuganga kept(GameTestHelper helper, Runuganga giant) {
        LasNochesHallsTests.Hold.hold(helper, helper.getLevel(), List.of(), 0, List.of()).placed().add(giant);
        return giant;
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    /**
     * A Hollow and a spirit 4 by 12, never saved, summoned by command, alone through a tear and never by chance or a
     * sinner, not frightened by released reiatsu, burnt, pushed, portalled or despawned, with the config's 300 health over
     * a stale base and his kind's reiatsu. His body part stands 4 to 9 blocks up, his head 9 to 12 with his mask in it. A
     * lookup at his head finds him alone by the head part, as a plain one there may miss his feet's entity section; a blow
     * on the head part reaching him is what fails if the parts stop forwarding.
     */
    @GameTest(template = "field", skyAccess = true, batch = "runuganga", timeoutTicks = 40)
    public static void heIsAGiantInTwoParts(GameTestHelper helper) {
        HollowKind kind = HollowKind.RUNUGANGA;
        EntityType<Runuganga> type = ModEntities.RUNUGANGA.get();
        EntityDimensions size = type.getDimensions();
        helper.assertTrue(ModEntities.hollowType(kind) == type && type.is(SoulLogic.SPIRITS) && type.is(BleachTags.HOLLOWS)
                        && type.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER) && !type.canSerialize() && type.canSummon()
                        && near(size.width(), Runuganga.WIDTH) && near(size.height(), Runuganga.HEIGHT),
                "bleach:runuganga is registered wrongly, or is " + size);
        helper.assertTrue(kind.arrivalWeight == 0 && !HollowLogic.arrivalPool(10_000.0F).contains(kind) && !kind.isMenos()
                        && !kind.isVasto() && kind.comesByTear()
                        && HollowLogic.groupSize(kind, 10_000.0F, Integer.MAX_VALUE, 0.0) == 1,
                "Runuganga comes by chance, by no tear or not alone, or is a Menos");
        KidoTests.withConfig(BleachConfig.SINNER_SHARE, 1.0,
                () -> helper.assertFalse(Hollow.rollsSinner(kind, 0.0), "Runuganga may be a sinner"));
        helper.assertTrue(BleachConfig.RUNUGANGA_HEALTH.getDefault() == 300.0, "his health's default changed");
        Runuganga giant = still(helper, 8.0, 8.0);
        helper.assertFalse(ShriekerTests.frightens(giant), "released reiatsu frightens him");
        helper.assertTrue(type.fireImmune() && !giant.isPushable() && !giant.canUsePortal(false)
                && !giant.removeWhenFarAway(1_000.0), "he burns, is pushed, takes a portal or despawns far from players");
        // Stale strength, as a summon with data would leave: his first tick puts the config's in.
        giant.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
        // His parts are placed in his AI step, which runs for a NoAI mob too.
        helper.runAfterDelay(1, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(near(giant.getMaxHealth(), 300.0) && near(giant.getHealth(), 300.0)
                            && near(SoulLogic.get(giant).maxReiryoku(), kind.reiatsu),
                    "he has " + giant.getHealth() + " of " + giant.getMaxHealth() + " health, or the wrong reiatsu");
            AABB body = giant.getParts()[Runuganga.PART_BODY].getBoundingBox(), head = giant.head().getBoundingBox();
            double feet = giant.getY();
            helper.assertTrue(near(body.minY, feet + 4.0) && near(body.maxY, feet + 9.0) && near(body.getXsize(), 4.0)
                            && near(head.minY, feet + 9.0) && near(head.maxY, feet + 12.0) && near(head.getXsize(), 3.0)
                            && head.inflate(0.01).contains(HollowMask.maskCenter(giant)),
                    "his parts stand at " + body + " and " + head + ", or his mask is off his head");
            List<LivingEntity> found = SpiritProjectiles.creaturesIn(level, AABB.ofSize(head.getCenter(), 0.5, 0.5, 0.5),
                    entity -> true);
            helper.assertTrue(found.equals(List.of(giant)), "the creatures at his head were " + found);
            helper.assertTrue(giant.head().hurt(level.damageSources().generic(), 10.0F)
                    && giant.getHealth() < giant.getMaxHealth(), "a blow on his head did not reach him");
            helper.succeed();
        });
    }
}
