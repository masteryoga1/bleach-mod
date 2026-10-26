package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Gillian;
import io.github.masteryoga1.bleach.entity.Menos;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.Bala;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Negación: the held who can neither be hurt nor hurt, the rise and the carry-off that frees a Menos's
 * room, the Gillian mob that lifts only a beaten ally, the offer a player may take or leave, and the aim along the
 * caster's look. Mock players are plain {@link Player}s that are not in the world and never tick, so the casts are
 * called directly ({@link Negacion#castOn}, {@link Negacion#cast}). A mock riding a light would be ticked by it, so
 * no test waits while a mock rides: it is mounted, checked and let go in one tick. A setting changed for a test is
 * put back before the test's tick ends, or, for one the light reads each tick, in the test's last step.
 *
 * <p>A husk stands where a mundane monster is needed: a zombie would burn in the daylight that comes through the
 * barrier.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class NegacionTests {
    /** A team every mock player joins at once, as they all share one name. Made and taken away in one tick. */
    private static final String TEAM = "bleach_negacion_test";
    /** How close a creature must stand to where it should be. */
    private static final double PLACE_EPSILON = 1.0E-3;

    private NegacionTests() {
    }

    // ---- Helpers ----

    /** A mock Gillian-form Hollow in survival, with this much reiryoku and full. */
    private static Player gillian(GameTestHelper helper, float maxReiryoku, Vec3 at) {
        Player player = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, maxReiryoku, at);
        SoulLogic.get(player).setTier(PowerPath.HOLLOW, HollowTier.GILLIAN.tier());
        return player;
    }

    /** A mock living Human who sees spirits: 100 reiryoku is above the awareness threshold of 30. */
    private static Player aware(GameTestHelper helper, Vec3 at) {
        return HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 100.0F, at);
    }

    /** The Slow Falling a creature has left, in ticks, or 0 for none. */
    private static int slowFalling(LivingEntity creature) {
        MobEffectInstance effect = creature.getEffect(MobEffects.SLOW_FALLING);
        return effect == null ? 0 : effect.getDuration();
    }

    /** The least Slow Falling a creature let go of keeps: the fall guard, less the tick it may already have run. */
    private static int fallGuard() {
        return BleachConfig.get(BleachConfig.NEGACION_FALL_GUARD_SECONDS) * 20 - 1;
    }

    private static void assertRefusal(GameTestHelper helper, @Nullable Negacion.Refusal actual,
                                      @Nullable Negacion.Refusal expected, String what) {
        helper.assertTrue(actual == expected, "a Negación " + what + " gave " + actual + ", not " + expected);
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** Every light in this test's structure, and the tears they opened. */
    private static void discardLights(GameTestHelper helper) {
        AABB bounds = helper.getBounds().inflate(1.0, 16.0, 1.0);
        helper.getLevel().getEntitiesOfClass(NegacionLight.class, bounds).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(GargantaRift.class, bounds).forEach(Entity::discard);
    }

    private static void removeTeam(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    // ---- The held ----

    @GameTest(template = "field")
    public static void negacionShieldsTheHeldBothWays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        SpiderHollow other = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 12, 8);
        Husk husk = HollowPlayerTests.placed(helper, EntityType.HUSK, 8, 10);
        // Aware, so the spirit rules let its blows through and only the held cancel can stop them.
        Player attacker = aware(helper, new Vec3(8.5, 2.0, 4.5));
        NegacionLight light = Negacion.lift(level, spider);
        helper.assertTrue(light != null && Negacion.held(spider) && Negacion.lightOf(spider) == light
                && light.phase() == NegacionLight.Phase.HOLDING, "the Spider Hollow was not lifted into a holding light");

        helper.assertTrue(other.hurt(level.damageSources().playerAttack(attacker), 5.0F),
                "an aware player's blow did not reach a Spider Hollow that is not held");
        helper.assertFalse(spider.hurt(level.damageSources().playerAttack(attacker), 5.0F),
                "an aware player's blow reached the held Spider Hollow");
        helper.assertTrue(spider.getHealth() >= spider.getMaxHealth(), "the held Spider Hollow was hurt");
        // The spirit rules never stop a Hollow's blow on a mundane mob: this is the held striker's own cancel.
        helper.assertFalse(husk.hurt(level.damageSources().mobAttack(spider), 5.0F),
                "the held Spider Hollow's blow reached the husk");
        helper.assertTrue(spider.hurt(level.damageSources().fellOutOfWorld(), 1.0F), "the void did not reach the held");

        helper.runAfterDelay(1, () -> {
            helper.assertTrue(spider.position().distanceTo(light.position()) < PLACE_EPSILON,
                    "the held Spider Hollow stands at " + spider.position() + ", not on its light at " + light.position());

            // A mock Arrancar on its own light: mounted, checked and let go in this one tick.
            Player arrancar = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, new Vec3(3.5, 2.0, 3.5));
            SoulData data = SoulLogic.get(arrancar);
            long now = HollowPlayerTests.now(helper);
            NegacionLight own = NegacionLight.place(level, arrancar, null);
            try {
                helper.assertTrue(own != null && arrancar.startRiding(own, true) && Negacion.held(arrancar),
                        "the mock Arrancar could not be held");
                HollowCero.Refusal cero = HollowCero.start(arrancar, data, now);
                helper.assertTrue(cero == HollowCero.Refusal.HELD, "a held Arrancar's Cero gave " + cero);
                GranReyCero.Refusal grand = GranReyCero.start(arrancar, data, now);
                helper.assertTrue(grand == GranReyCero.Refusal.HELD, "a held Arrancar's Gran Rey Cero gave " + grand);
                Bala.Result bala = Bala.fire(arrancar, data, now);
                helper.assertTrue(bala == Bala.Result.UNABLE, "a held Arrancar's Bala gave " + bala);
                helper.assertTrue(arrancar.hasEffect(MobEffects.SLOW_FALLING), "the held Arrancar does not fall slowly");
                arrancar.stopRiding();
                helper.assertFalse(Negacion.held(arrancar), "the Arrancar is still held after letting go");
                helper.assertTrue(slowFalling(arrancar) >= fallGuard(),
                        "the Arrancar let go falls slowly for only " + slowFalling(arrancar) + " ticks");
                helper.assertTrue(own.phase() == NegacionLight.Phase.ENDING, "the light let go of is " + own.phase());
            } finally {
                arrancar.stopRiding();
                discard(own);
            }

            EntityType<NegacionLight> type = ModEntities.NEGACION.get();
            helper.assertFalse(type.canSerialize(), "a Negación light would be saved");
            helper.assertTrue(type.is(SoulLogic.SPIRITS), "a Negación light is not a spirit");
            // The held one first, so its light ends and takes its tear with it.
            discard(spider, light, other, husk);
            discardLights(helper);
            helper.succeed();
        });
    }

    // ---- The carry-off ----

    /**
     * In a batch of its own, as it counts the Menos alive in every world. Each light lands at tick 0, lifts at 10
     * and carries off at 50 (a two-second hold), and has faded by 60.
     */
    @GameTest(template = "field", skyAccess = true, batch = "negacion_carry", timeoutTicks = 120)
    public static void negacionCarriesAHollowOffAndFreesItsRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Adjuchas open = HollowPlayerTests.placed(helper, ModEntities.ADJUCHAS.get(), 4, 8);
        Adjuchas roofed = HollowPlayerTests.placed(helper, ModEntities.ADJUCHAS.get(), 12, 8);
        // A stone roof 4 blocks over the roofed one's feet: 1.5 blocks of room over its 2.5-block body, too few for a tear.
        int roofY = 6;
        for (int x = 10; x <= 14; x++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(x, roofY, z, Blocks.STONE);
            }
        }
        // The roof's underside, which the roofed one's head may touch but never pass.
        double roofUnderside = helper.absolutePos(new BlockPos(12, roofY, 8)).getY();
        double openFrom = open.getY();
        int before = Menos.aliveCount(level.getServer());
        // Read only as the hold begins, so it is set for the two lifts and put back in this tick.
        int hold = BleachConfig.get(BleachConfig.NEGACION_HOLD_SECONDS);
        NegacionLight openLight;
        NegacionLight roofedLight;
        BleachConfig.NEGACION_HOLD_SECONDS.set(2);
        try {
            openLight = Negacion.lift(level, open);
            roofedLight = Negacion.lift(level, roofed);
        } finally {
            BleachConfig.NEGACION_HOLD_SECONDS.set(hold);
        }
        helper.assertTrue(openLight != null && roofedLight != null, "an Adjuchas was not lifted");
        helper.assertTrue(openLight.carryAt() == 50 && roofedLight.carryAt() == 50,
                "the carry-offs are at " + openLight.carryAt() + " and " + roofedLight.carryAt() + ", not 50");

        helper.onEachTick(() -> {
            if (!roofed.isRemoved()) {
                helper.assertTrue(roofed.getBoundingBox().maxY <= roofUnderside + 1.0E-6, "the roofed Adjuchas rose to "
                        + roofed.getBoundingBox().maxY + ", into the roof at " + roofUnderside);
            }
        });
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    double rise = open.getY() - openFrom;
                    helper.assertTrue(rise >= 1.0, "the open Adjuchas rose only " + rise + " blocks by tick 25");
                    // 3.2 blocks of rise (1.6 a second for 2 seconds), the body's 2.5 and half a block over it.
                    List<GargantaRift> rifts = level.getEntitiesOfClass(GargantaRift.class, helper.getBounds().inflate(1.0));
                    helper.assertTrue(rifts.size() == 1,
                            "there are " + rifts.size() + " tears, not one over the open Adjuchas");
                    GargantaRift rift = rifts.get(0);
                    helper.assertTrue(Math.abs(rift.getY() - (openFrom + 6.2)) < 0.05
                            && Math.abs(rift.getX() - open.getX()) < PLACE_EPSILON
                            && Math.abs(rift.getZ() - open.getZ()) < PLACE_EPSILON,
                            "the tear is at " + rift.position() + ", not 6.2 blocks over the open Adjuchas");
                    helper.assertTrue(helper.getBounds().contains(rift.position()), "the tear is outside the field");
                })
                .thenExecuteAfter(35, () -> {
                    helper.assertTrue(open.getRemovalReason() == Entity.RemovalReason.DISCARDED
                            && roofed.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                            "the Adjuchas went as " + open.getRemovalReason() + " and " + roofed.getRemovalReason());
                    int after = Menos.aliveCount(level.getServer());
                    helper.assertTrue(after == before - 2, "the Menos alive went from " + before + " to " + after);
                    helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty()
                            && level.getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).isEmpty(),
                            "the carried-off Adjuchas left drops or experience");
                })
                .thenExecuteAfter(15, () -> {
                    helper.assertTrue(openLight.isRemoved() && roofedLight.isRemoved(), "a light had not faded by tick 75");
                    discardLights(helper);
                })
                .thenSucceed();
    }

    // ---- The Gillian mob ----

    @GameTest(template = "field", skyAccess = true, timeoutTicks = 60)
    public static void gillianRescuesOnlyABeatenAlly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Gillian gillian = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.GILLIAN.get(), 8, 2, 8));
        Gillian second = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.GILLIAN.get(), 3, 2, 3));
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 12, 12);
        SpiderHollow otherSpider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 12, 4);
        Husk husk = HollowPlayerTests.placed(helper, EntityType.HUSK, 3, 12);
        double chance = BleachConfig.get(BleachConfig.NEGACION_MOB_CHANCE);
        boolean rescue = BleachConfig.get(BleachConfig.NEGACION_MOB_RESCUE);
        try {
            helper.assertTrue(gillian.rescueTarget() == null, "a Gillian would rescue an ally at full health");
            spider.setHealth(spider.getMaxHealth() * 0.2F);
            helper.assertTrue(gillian.rescueTarget() == null, "a Gillian would rescue an ally nothing has hurt");
            spider.setHealth(spider.getMaxHealth());
            second.setHealth(second.getMaxHealth() * 0.1F);
            second.setLastHurtByMob(husk);
            helper.assertTrue(gillian.rescueTarget() == null, "a Gillian would rescue another Gillian");

            spider.setHealth(spider.getMaxHealth() * 0.2F);
            spider.setLastHurtByMob(husk);
            helper.assertTrue(gillian.rescueTarget() == spider, "a Gillian would rescue " + gillian.rescueTarget());

            BleachConfig.NEGACION_MOB_CHANCE.set(1.0);
            helper.assertTrue(gillian.tryRescue(level, now) && Negacion.held(spider),
                    "a Gillian sure to rescue did not lift the beaten Spider Hollow");
            otherSpider.setHealth(otherSpider.getMaxHealth() * 0.2F);
            otherSpider.setLastHurtByMob(husk);
            helper.assertTrue(gillian.rescueTarget() == otherSpider, "the second beaten Spider Hollow is not in reach");
            helper.assertFalse(gillian.tryRescue(level, now + 1L), "a Gillian rescued again a tick after its last");
            helper.assertFalse(Negacion.held(otherSpider), "the second Spider Hollow was lifted during the wait");

            second.setHealth(second.getMaxHealth());
            helper.assertTrue(second.rescueTarget() == otherSpider,
                    "the second Gillian would rescue " + second.rescueTarget());
            BleachConfig.NEGACION_MOB_RESCUE.set(false);
            helper.assertFalse(second.tryRescue(level, now), "a Gillian rescued with the rescue turned off");
            helper.assertFalse(Negacion.held(otherSpider), "the second Spider Hollow was lifted with the rescue off");
        } finally {
            BleachConfig.NEGACION_MOB_CHANCE.set(chance);
            BleachConfig.NEGACION_MOB_RESCUE.set(rescue);
            discard(spider, otherSpider, husk, gillian, second);
            discardLights(helper);
        }
        helper.succeed();
    }

    // ---- The offer ----

    @GameTest(template = "field", timeoutTicks = 80)
    public static void negacionOffersAPlayerWhoMayLeave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Scoreboard scoreboard = level.getScoreboard();
        Player caster = gillian(helper, 400.0F, new Vec3(4.5, 2.0, 4.5));
        SoulData data = SoulLogic.get(caster);
        Player target = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, new Vec3(7.5, 2.0, 4.5));
        Player stranger = aware(helper, new Vec3(4.5, 2.0, 10.5));
        Player giant = aware(helper, new Vec3(10.5, 2.0, 10.5));
        Player teammate = aware(helper, new Vec3(12.5, 2.0, 4.5));
        int offerSeconds = BleachConfig.get(BleachConfig.NEGACION_OFFER_SECONDS);
        // Whether the late checks, which put the offer time back, have been handed the test.
        boolean handedOn = false;
        try {
            assertRefusal(helper, Negacion.castOn(caster, data, target, now), null, "on an Arrancar");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 340.0, "the caster's reiryoku after a Negación at 400");
            List<NegacionLight> offers = level.getEntitiesOfClass(NegacionLight.class, helper.getBounds(),
                    light -> target.getUUID().equals(light.offeredTo()));
            helper.assertTrue(offers.size() == 1, "there are " + offers.size() + " lights offered to the Arrancar");
            NegacionLight light = offers.get(0);
            helper.assertTrue(light.phase() == NegacionLight.Phase.OFFERED && !Negacion.held(target),
                    "the offer is " + light.phase() + ", the Arrancar held " + Negacion.held(target));

            assertRefusal(helper, Negacion.castOn(caster, data, target, now), Negacion.Refusal.BUSY,
                    "on one already offered");
            assertRefusal(helper, Negacion.cast(caster, data, now + 10L), Negacion.Refusal.COOLDOWN, "half a second later");
            assertRefusal(helper, Negacion.castOn(caster, data, stranger, now), Negacion.Refusal.NOT_ALLY,
                    "on an aware Human on no team");

            // Every mock player has the same name, so all of them are on the caster's team now.
            removeTeam(scoreboard);
            PlayerTeam team = scoreboard.addPlayerTeam(TEAM);
            scoreboard.addPlayerToTeam(caster.getScoreboardName(), team);
            helper.assertTrue(caster.isAlliedTo(giant), "the mock players did not share the team");
            AttributeInstance scale = giant.getAttribute(Attributes.SCALE);
            helper.assertTrue(scale != null, "a player has no scale");
            scale.setBaseValue(5.0);
            giant.refreshDimensions();
            // The size is asked before the ally rule.
            assertRefusal(helper, Negacion.castOn(caster, data, giant, now), Negacion.Refusal.TOO_BIG,
                    "on a team-mate five times a player's size");

            helper.assertTrue(light.interact(stranger, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                    "someone else boarded the Arrancar's offer");
            InteractionResult boarded = light.interact(target, InteractionHand.MAIN_HAND);
            try {
                helper.assertTrue(boarded.consumesAction() && Negacion.held(target)
                        && light.phase() == NegacionLight.Phase.HOLDING,
                        "the Arrancar boarding its offer gave " + boarded + ", held " + Negacion.held(target));
                // Letting go stands in for the sneak, in this same tick.
                target.stopRiding();
                helper.assertFalse(Negacion.held(target), "the Arrancar is still held after letting go");
                helper.assertTrue(slowFalling(target) >= fallGuard(),
                        "the Arrancar let go falls slowly for only " + slowFalling(target) + " ticks");
                helper.assertTrue(light.phase() == NegacionLight.Phase.ENDING, "the light let go of is " + light.phase());
            } finally {
                target.stopRiding();
            }

            // Read by the offer each tick, so it stays set until the last check.
            BleachConfig.NEGACION_OFFER_SECONDS.set(2);
            assertRefusal(helper, Negacion.castOn(caster, data, teammate, now + 1L), null, "on a team-mate");
            List<NegacionLight> late = level.getEntitiesOfClass(NegacionLight.class, helper.getBounds(),
                    offer -> teammate.getUUID().equals(offer.offeredTo()));
            helper.assertTrue(late.size() == 1, "there are " + late.size() + " lights offered to the team-mate");
            NegacionLight teamLight = late.get(0);
            boolean[] everHeld = {false};
            boolean[] offeredAt30 = {false};
            helper.onEachTick(() -> everHeld[0] |= teamLight.phase() == NegacionLight.Phase.HOLDING);
            helper.runAtTickTime(30, () -> offeredAt30[0] = !teamLight.isRemoved()
                    && teamLight.phase() == NegacionLight.Phase.OFFERED);
            // The offer ends at 40 and fades by 50.
            helper.runAtTickTime(55, () -> {
                BleachConfig.NEGACION_OFFER_SECONDS.set(offerSeconds);
                helper.assertTrue(offeredAt30[0], "the team-mate's offer was not waiting at tick 30");
                helper.assertTrue(teamLight.isRemoved(), "the team-mate's offer had not gone by tick 55");
                helper.assertFalse(everHeld[0], "the team-mate's offer held someone");
                discardLights(helper);
                helper.succeed();
            });
            handedOn = true;
        } finally {
            removeTeam(scoreboard);
            if (!handedOn) {
                BleachConfig.NEGACION_OFFER_SECONDS.set(offerSeconds);
            }
        }
    }

    // ---- The aim ----

    @GameTest(template = "field")
    public static void negacionAimsAlongTheLook(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        // At the north edge facing south, so the far one is as far as the field allows.
        Player caster = gillian(helper, 400.0F, new Vec3(8.5, 2.0, 0.5));
        SoulData data = SoulLogic.get(caster);
        SpiderHollow near = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        SpiderHollow far = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 14);
        // Aimed down at the far one, so the line runs through the near one first.
        caster.lookAt(EntityAnchorArgument.Anchor.EYES, far.getBoundingBox().getCenter());
        // The field is 16 blocks long, so the reach is cut to 12 for this tick: the near one 8 blocks off, the far 14.
        double range = BleachConfig.get(BleachConfig.NEGACION_PLAYER_RANGE);
        BleachConfig.NEGACION_PLAYER_RANGE.set(12.0);
        try {
            LivingEntity both = Negacion.aimed(caster);
            helper.assertTrue(both == near, "the aim along the line found " + both);
            helper.setBlock(8, 2, 4, Blocks.STONE);
            helper.setBlock(8, 3, 4, Blocks.STONE);
            LivingEntity walled = Negacion.aimed(caster);
            helper.assertTrue(walled == null, "the aim went through a stone block to " + walled);
            helper.setBlock(8, 2, 4, Blocks.AIR);
            helper.setBlock(8, 3, 4, Blocks.AIR);

            discard(near);
            LivingEntity alone = Negacion.aimed(caster);
            helper.assertTrue(alone == null, "the aim reached " + alone + " beyond its range");
            // The control: in reach, the same one is found.
            BleachConfig.NEGACION_PLAYER_RANGE.set(32.0);
            LivingEntity inReach = Negacion.aimed(caster);
            helper.assertTrue(inReach == far, "the aim at 32 blocks found " + inReach);
            BleachConfig.NEGACION_PLAYER_RANGE.set(12.0);

            discard(far);
            assertRefusal(helper, Negacion.cast(caster, data, now), Negacion.Refusal.NO_TARGET, "at nothing");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after a Negación at nothing");
            // Nor does it start the wait.
            assertRefusal(helper, Negacion.cast(caster, data, now + 1L), Negacion.Refusal.NO_TARGET, "at nothing again");
        } finally {
            BleachConfig.NEGACION_PLAYER_RANGE.set(range);
            discard(near, far);
        }
        helper.succeed();
    }
}
