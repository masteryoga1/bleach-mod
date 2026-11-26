package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.entity.ZangetsuSpirit;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the materialised spirits: a spirit's body set up from its wielder. Each test asks only about its own
 * wielders and spirits.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankaiSpiritTests {
    private BankaiSpiritTests() {
    }

    /** Zangetsu's body set up from this wielder, four blocks south of it facing it, and added to the world. */
    private static MaterialisedSpirit drawOut(GameTestHelper helper, Player wielder) {
        ZangetsuSpirit spirit = ModEntities.ZANGETSU_SPIRIT.get().create(helper.getLevel());
        ZanpakutoSpirit zangetsu = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU);
        spirit.setUp(wielder, ZanpakutoSpirits.ZANGETSU.location(), zangetsu);
        Vec3 at = wielder.position().add(0.0, 0.0, 4.0);
        spirit.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
        helper.getLevel().addFreshEntity(spirit);
        return spirit;
    }

    /**
     * T1. Zangetsu's body is set up from a Shinigami who knows his name: a soul as strong, full, no armour, and the
     * wielder, its UUID and the spirit's id kept. Reiatsu never frightens it off, and no spawner makes one. One summoned
     * bare has no wielder.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aSpiritIsSetUpFromItsWielder(GameTestHelper helper) {
        Player ichigo = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        MaterialisedSpirit spirit = drawOut(helper, ichigo);
        ZangetsuSpirit bare = helper.spawn(ModEntities.ZANGETSU_SPIRIT.get(), new BlockPos(12, 1, 12));
        try {
            float reiryoku = SoulLogic.get(ichigo).maxReiryoku();
            helper.assertTrue(spirit instanceof ZangetsuSpirit && spirit.wielder() == ichigo
                            && ichigo.getUUID().equals(spirit.wielderId()) && spirit.getArmorValue() == 0
                            && ZanpakutoSpirits.ZANGETSU.location().equals(spirit.spiritId()) && bare.wielder() == null,
                    "the body is not Zangetsu's, of Ichigo's, with no armour, or a bare one has a wielder");
            SoulData its = SoulLogic.get(spirit);
            helper.assertTrue(its.state() == SoulState.SHINIGAMI && its.maxReiryoku() == reiryoku
                            && its.reiryoku() == reiryoku,
                    "Zangetsu's soul is " + its.state() + " at " + its.reiryoku() + " of " + its.maxReiryoku());
            helper.assertTrue(spirit.goalSelector.getAvailableGoals().stream()
                            .noneMatch(goal -> goal.getGoal() instanceof ReiatsuFleeGoal)
                            && !spirit.checkSpawnRules(helper.getLevel(), MobSpawnType.SPAWNER),
                    "Zangetsu runs from reiatsu, or a spawner may make him");
        } finally {
            spirit.discard();
            bare.discard();
        }
        helper.succeed();
    }
}
