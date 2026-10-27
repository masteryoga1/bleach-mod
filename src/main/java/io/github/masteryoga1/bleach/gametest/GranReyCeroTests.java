package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.Bala;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowKey;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ResurreccionEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for the Gran Rey Cero: its numbers by origin, release and setting, the seat among the Espada it needs, the
 * order of its refusals, the blood it spills, the full gather it needs before it fires and what firing costs, the
 * Cero rules it keeps on pets and griefing, and the wait it saves. Mock players are plain {@link Player}s that are not
 * in the world and never tick, so every test calls the rules directly ({@link GranReyCero} and {@link CeroBeam#fire}),
 * no test can see a milestone, which only a real player earns, and no mock reaches the server's roster of ranks: a
 * seat is written into the record ({@link ArrancarRankTests#seat}). The game time stands still within a test, and a
 * gathering beam does not tick between calls, so a later time passed in stands for a later tick. Players stand at
 * {@link #ROOM} in the {@code empty} template and at {@link #SPOT} in the {@code field}, facing south. A setting
 * changed for a test is put back before the test's tick ends.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GranReyCeroTests {
    /** Where a player stands in the {@code empty} template. */
    private static final Vec3 ROOM = new Vec3(1.5, 1.0, 1.5);
    /** Where a player stands in the field: in the middle of block (8, 2, 6), facing south. */
    private static final Vec3 SPOT = new Vec3(8.5, 2.0, 6.5);
    /** The forms an Arrancar may rise from, weakest first. */
    private static final List<HollowTier> ORIGINS = List.of(HollowTier.GILLIAN, HollowTier.ADJUCHAS, HollowTier.VASTO_LORDE);
    /** Strong enough to pay for a Gran Rey Cero; a seat comes from {@link ArrancarRankTests#seat}. */
    private static final float STRONG = 1_600.0F;

    private GranReyCeroTests() {
    }

    // ---- Helpers ----

    /** A bare Arrancar soul risen from this form, with this much reiryoku and full. */
    private static SoulData soul(HollowTier origin, float maxReiryoku) {
        SoulData data = ShinigamiTests.soul(SoulState.ARRANCAR, maxReiryoku);
        data.setTier(PowerPath.HOLLOW, origin.tier());
        return data;
    }

    /** Calls out the Resurrección of the blade in the Arrancar's hand, which must answer. */
    private static void release(GameTestHelper helper, Player player, SoulData data, long now) {
        ResurreccionEvents.Result result = ResurreccionEvents.release(player, data, now);
        helper.assertTrue(result == ResurreccionEvents.Result.RELEASED, "the release gave " + result);
    }

    private static String name(HollowTier origin) {
        return origin.name().toLowerCase(Locale.ROOT) + "-born";
    }

    /** Sets when the next Gran Rey Cero may begin, as the soul's saved record keeps it. */
    private static void readyAt(SoulData data, long time) {
        HollowProgress hollow = data.hollow();
        data.setHollow(hollow.withArrancar(hollow.arrancar().withGranReyCeroReadyAt(time)));
    }

    private static long readyAt(SoulData data) {
        return data.hollow().arrancar().granReyCeroReadyAt();
    }

    private static void assertRefusal(GameTestHelper helper, Player player, SoulData data, long now,
                                      @Nullable GranReyCero.Refusal expected, String what) {
        GranReyCero.Refusal refusal = GranReyCero.refusal(player, data, now);
        helper.assertTrue(refusal == expected, "a Gran Rey Cero " + what + " gave " + refusal + ", not " + expected);
    }

    /** Whether nothing has hurt this creature: it is at full health. */
    private static boolean unhurt(LivingEntity creature) {
        return creature.getHealth() >= creature.getMaxHealth();
    }

    /** The only gathering or fired beam in this test's structure, which there must be. */
    private static CeroBeam onlyBeam(GameTestHelper helper) {
        List<CeroBeam> beams = helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds());
        helper.assertTrue(beams.size() == 1, "there are " + beams.size() + " beams, not one");
        return beams.get(0);
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /**
     * Every beam in this test's structure, gathering or fired, and whatever a creature the beam killed left behind.
     * All of them, as a beam's owner is looked up in the world, where no mock player is.
     */
    private static void discardBeams(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getEntitiesOfClass(CeroBeam.class, helper.getBounds()).forEach(Entity::discard);
        level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).forEach(Entity::discard);
        level.getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).forEach(Entity::discard);
    }

    // ---- The numbers ----

    @GameTest(template = "empty")
    public static void granReyCeroNumbersFollowOriginAndRelease(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        // Three times each origin's Cero at 1600: 10, 12 or 16, and 0.6 for each of the 40 in its root.
        double[] damage = {102.0, 108.0, 120.0};
        int[] charge = {50, 25, 15};
        for (int i = 0; i < ORIGINS.size(); i++) {
            HollowTier origin = ORIGINS.get(i);
            SoulData data = soul(origin, STRONG);
            HollowPlayerTests.assertClose(helper, GranReyCero.damage(data), damage[i],
                    "a " + name(origin) + " Gran Rey Cero at 1600");
            helper.assertTrue(GranReyCero.chargeTicks(data) == charge[i],
                    "a " + name(origin) + " Gran Rey Cero gathers for " + GranReyCero.chargeTicks(data) + " ticks");
        }
        Player released = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, STRONG, ROOM);
        SoulData releasedData = SoulLogic.get(released);
        release(helper, released, releasedData, now);
        HollowPlayerTests.assertClose(helper, GranReyCero.damage(releasedData), 150.0,
                "a released Vasto Lorde-born Gran Rey Cero at 1600");

        SoulData vasto = soul(HollowTier.VASTO_LORDE, STRONG);
        HollowPlayerTests.assertClose(helper, GranReyCero.cost(vasto), 640.0, "a Gran Rey Cero's cost at 1600");
        HollowPlayerTests.assertClose(helper, GranReyCero.cost(soul(HollowTier.VASTO_LORDE, 100.0F)), 50.0,
                "a Gran Rey Cero's cost at 100");
        helper.assertFalse(GranReyCero.eligible(soul(HollowTier.VASTO_LORDE, STRONG)), "an Arrancar with no seat has it");
        SoulData seated = soul(HollowTier.VASTO_LORDE, 100.0F);
        ArrancarRankTests.seat(seated, 10);
        helper.assertTrue(GranReyCero.eligible(seated), "the Diez Espada lacks it");

        Player sealed = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, STRONG, ROOM);
        CeroBeam.Spec spec = GranReyCero.spec(sealed, SoulLogic.get(sealed));
        helper.assertTrue(spec.radius() == 2.0F && spec.range() == 64.0 && spec.blastRadius() == 4.0F
                && spec.maxBlocks() == 48 && Math.abs(spec.knockback() - 1.6F) < 1.0E-4F && spec.aimLockTicks() == 0,
                "the Gran Rey Cero's beam is " + spec);
        int colour = ArrancarLogic.blade(sealed.getUUID()).colour();
        helper.assertTrue(spec.colour() == colour && GranReyCero.colour(sealed) == colour,
                "the Gran Rey Cero is " + Integer.toHexString(spec.colour()) + ", not the blade's "
                        + Integer.toHexString(colour));
        helper.assertTrue(GranReyCero.KIND.damage() == GranReyCero.DAMAGE && !GranReyCero.KIND.quiet()
                && GranReyCero.KIND.pierce() == Integer.MAX_VALUE, "the Gran Rey Cero strikes as " + GranReyCero.KIND);

        // Set for these calls, in this one tick, and put back before any other test runs.
        double chargeMultiplier = BleachConfig.get(BleachConfig.GRAN_REY_CERO_CHARGE_MULTIPLIER);
        double damageMultiplier = BleachConfig.get(BleachConfig.GRAN_REY_CERO_DAMAGE_MULTIPLIER);
        try {
            BleachConfig.GRAN_REY_CERO_CHARGE_MULTIPLIER.set(2.0);
            helper.assertTrue(GranReyCero.chargeTicks(vasto) == 30,
                    "at twice the charge, a Vasto Lorde-born one gathers for " + GranReyCero.chargeTicks(vasto) + " ticks");
            BleachConfig.GRAN_REY_CERO_DAMAGE_MULTIPLIER.set(10.0);
            HollowPlayerTests.assertClose(helper, GranReyCero.damage(vasto), 240.0,
                    "a sealed Vasto Lorde-born Gran Rey Cero at ten times");
            HollowPlayerTests.assertClose(helper, GranReyCero.damage(releasedData), 300.0,
                    "a released Vasto Lorde-born Gran Rey Cero at ten times");
        } finally {
            BleachConfig.GRAN_REY_CERO_CHARGE_MULTIPLIER.set(chargeMultiplier);
            BleachConfig.GRAN_REY_CERO_DAMAGE_MULTIPLIER.set(damageMultiplier);
        }
        helper.succeed();
    }

    // ---- The refusals ----

    @GameTest(template = "empty")
    public static void granReyCeroRefusesInOrder(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ServerLevel level = helper.getLevel();
        Player player = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, 1_000.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        try {
            // Held: mounted and let go in this one tick, as a mock left riding would be ticked by its light.
            NegacionLight light = NegacionLight.place(level, player, null);
            helper.assertTrue(light != null && player.startRiding(light, true), "the mock could not mount a light");
            try {
                assertRefusal(helper, player, data, now, GranReyCero.Refusal.HELD, "while held");
                GranReyCero.Refusal start = GranReyCero.start(player, data, now);
                helper.assertTrue(start == GranReyCero.Refusal.HELD && !GranReyCero.charging(data),
                        "a Gran Rey Cero begun while held gave " + start);
            } finally {
                player.stopRiding();
                discard(light);
            }

            assertRefusal(helper, player, data, now, GranReyCero.Refusal.LOCKED, "at 1000");
            Component locked = GranReyCero.message(GranReyCero.Refusal.LOCKED, data, now);
            helper.assertTrue(locked.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("message.bleach.gran_rey_cero.locked") && contents.getArgs().length == 2
                    && contents.getArgs()[0] instanceof Component rank
                    && rank.getContents() instanceof TranslatableContents label
                    && label.getKey().equals("rank.bleach.unnumbered")
                    && String.valueOf(contents.getArgs()[1]).equals("1500"), "the locked message is " + locked);
            data.setMaxReiryoku(STRONG);
            data.setReiryoku(STRONG);
            ArrancarRankTests.seat(data, 1);
            assertRefusal(helper, player, data, now, null, "at 1600, ready");

            readyAt(data, now + 100L);
            assertRefusal(helper, player, data, now, GranReyCero.Refusal.COOLDOWN, "5 seconds before it is ready");
            helper.assertTrue(GranReyCero.secondsToReady(data, now) == 5L,
                    "the wait left is " + GranReyCero.secondsToReady(data, now) + " seconds");
            readyAt(data, HollowProgress.NEVER);

            ItemStack blade = player.getMainHandItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            assertRefusal(helper, player, data, now, GranReyCero.Refusal.NO_BLADE, "bare-handed");
            player.setItemInHand(InteractionHand.MAIN_HAND, blade);
            release(helper, player, data, now);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            GranReyCero.Refusal releasedBare = GranReyCero.refusal(player, data, now);
            helper.assertTrue(releasedBare != GranReyCero.Refusal.NO_BLADE,
                    "a released Arrancar with an empty hand was told it has no blade");
            // Sealed again, with the blade back in hand, as before the release.
            ResurreccionEvents.seal(player, data, now);
            player.setItemInHand(InteractionHand.MAIN_HAND, blade);

            data.setReiryoku(100.0F);
            assertRefusal(helper, player, data, now, GranReyCero.Refusal.TOO_WEAK, "at 100 reiryoku");
            data.setReiryoku(STRONG);

            player.setHealth(7.0F);
            assertRefusal(helper, player, data, now, GranReyCero.Refusal.TOO_HURT, "at 7 health");

            // The blood: at 8 health it leaves exactly the least allowed.
            player.setHealth(8.0F);
            GranReyCero.Refusal started = GranReyCero.start(player, data, now);
            helper.assertTrue(started == null && GranReyCero.charging(data), "a Gran Rey Cero at 8 health gave " + started);
            HollowPlayerTests.assertClose(helper, player.getHealth(), 4.0, "the health after the blood");
            GranReyCero.cancel(player, data);
            helper.assertFalse(GranReyCero.charging(data), "a cancelled Gran Rey Cero still gathers");

            // Infinite materials pay no blood, and so may gather at any health.
            player.setHealth(3.0F);
            player.getAbilities().instabuild = true;
            try {
                GranReyCero.Refusal free = GranReyCero.start(player, data, now);
                helper.assertTrue(free == null && GranReyCero.charging(data),
                        "a creative Gran Rey Cero at 3 health gave " + free);
                HollowPlayerTests.assertClose(helper, player.getHealth(), 3.0, "a creative player's health after the gather");
                GranReyCero.cancel(player, data);
            } finally {
                player.getAbilities().instabuild = false;
            }
            helper.assertTrue(readyAt(data) == HollowProgress.NEVER, "a cancel started a wait until " + readyAt(data));
            HollowPlayerTests.assertClose(helper, data.reiryoku(), STRONG, "the reiryoku after two cancels");

            Player hollow = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, STRONG, ROOM);
            SoulData hollowData = SoulLogic.get(hollow);
            GranReyCero.Refusal hollowStart = GranReyCero.start(hollow, hollowData, now);
            helper.assertTrue(hollowStart == null && !GranReyCero.charging(hollowData),
                    "a Hollow's Gran Rey Cero gave " + hollowStart + ", gathering " + GranReyCero.charging(hollowData));
        } finally {
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- The blood and the fire ----

    @GameTest(template = "field")
    public static void granReyCeroSpillsBloodAndFiresOnlyFull(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player player = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, STRONG, SPOT);
        SoulData data = SoulLogic.get(player);
        ArrancarRankTests.seat(data, 1);
        player.setHealth(20.0F);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 12);
        // A Spider Hollow stands lower than the eyes, so the shot is aimed down at it.
        player.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getBoundingBox().getCenter());
        // The shot goes on into the floor near the edge of the field, and its blast must break nothing outside it.
        boolean breaks = BleachConfig.get(BleachConfig.GRAN_REY_CERO_BREAKS_BLOCKS);
        BleachConfig.GRAN_REY_CERO_BREAKS_BLOCKS.set(false);
        try {
            GranReyCero.Refusal first = GranReyCero.start(player, data, now);
            helper.assertTrue(first == null && GranReyCero.charging(data), "the Gran Rey Cero did not gather: " + first);
            HollowPlayerTests.assertClose(helper, player.getHealth(), 16.0, "the health after the blood");
            CeroBeam gathering = onlyBeam(helper);
            helper.assertTrue(gathering.kind() == GranReyCero.KIND && gathering.radius() == 2.0F
                    && gathering.chargeTicks() == 15 && gathering.colour() == ArrancarLogic.blade(player.getUUID()).colour(),
                    "the gathering beam is " + gathering.kind() + ", " + gathering.radius() + " wide, "
                            + gathering.chargeTicks() + " ticks, " + Integer.toHexString(gathering.colour()));

            // One beam at a time.
            HollowCero.Refusal cero = HollowCero.start(player, data, now);
            helper.assertTrue(cero == null && !HollowCero.charging(data),
                    "a Cero while a Gran Rey Cero gathers gave " + cero + ", gathering " + HollowCero.charging(data));
            Bala.Result bala = Bala.fire(player, data, now);
            helper.assertTrue(bala == Bala.Result.GATHERING, "a Bala while a Gran Rey Cero gathers gave " + bala);

            // Let go a third of the way: it scatters, the blood spent but nothing else.
            GranReyCero.release(player, data, now + 5L);
            helper.assertTrue(!GranReyCero.charging(data) && gathering.isRemoved(),
                    "a Gran Rey Cero let go early did not scatter");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), STRONG, "the reiryoku after a scattered one");
            helper.assertTrue(readyAt(data) == HollowProgress.NEVER, "a scattered one started a wait until " + readyAt(data));
            HollowPlayerTests.assertClose(helper, player.getHealth(), 16.0, "the health after a scattered one");

            GranReyCero.Refusal second = GranReyCero.start(player, data, now + 6L);
            helper.assertTrue(second == null && GranReyCero.charging(data), "the second Gran Rey Cero gave " + second);
            HollowPlayerTests.assertClose(helper, player.getHealth(), 12.0, "the health after the second blood");
            CeroBeam full = onlyBeam(helper);
            GranReyCero.release(player, data, now + 21L);
            helper.assertTrue(!GranReyCero.charging(data) && full.isFired(), "a fully gathered Gran Rey Cero did not fire");
            // The kill grows the maximum a little before the fire is paid for, as with the Cero, so the cost is read
            // after it: 640 and a twentieth.
            helper.assertTrue(data.maxReiryoku() > STRONG, "killing the Spider Hollow did not grow the maximum");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), STRONG - GranReyCero.cost(data),
                    "the reiryoku after a Gran Rey Cero");
            // Settled as it fires, by the game's own clock: this whole test runs within one tick.
            helper.assertTrue(readyAt(data) == HollowPlayerTests.now(helper) + 1_200L,
                    "the next is ready at " + readyAt(data) + ", not a minute after it fired");
            CeroBeam.Shot shot = full.lastShot();
            helper.assertTrue(shot != null && shot.struck().contains(spider),
                    "the Gran Rey Cero struck " + (shot == null ? null : shot.struck()));
            DamageSource source = spider.getLastDamageSource();
            helper.assertTrue(source != null && source.is(GranReyCero.DAMAGE), "the Spider Hollow was struck by " + source);
            GranReyCero.Refusal soon = GranReyCero.start(player, data, now + 22L);
            helper.assertTrue(soon == GranReyCero.Refusal.COOLDOWN, "a Gran Rey Cero a tick later gave " + soon);
        } finally {
            BleachConfig.GRAN_REY_CERO_BREAKS_BLOCKS.set(breaks);
            discard(spider);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- The Cero's rules ----

    /** In the Cero's griefing batch, so no other test sees mob griefing change. */
    @GameTest(template = "field", batch = "cero_griefing")
    public static void granReyCeroKeepsTheCeroRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player shooter = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, STRONG, new Vec3(1.5, 2.0, 8.5));
        SoulData data = SoulLogic.get(shooter);
        Wolf wolf = HollowPlayerTests.placed(helper, EntityType.WOLF, 4, 8);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(shooter.getUUID());
        SpiderHollow near = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 6, 8);
        SpiderHollow far = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        buildWall(helper);
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean savedGriefing = griefing.get();
        boolean breaks = BleachConfig.get(BleachConfig.GRAN_REY_CERO_BREAKS_BLOCKS);
        try {
            helper.assertFalse(CeroBeam.mayStrike(shooter, wolf), "a Gran Rey Cero strikes the shooter's own wolf");
            Vec3 from = helper.absoluteVec(new Vec3(2.5, 2.5, 8.5));
            Vec3 east = new Vec3(1.0, 0.0, 0.0);

            griefing.set(false, level.getServer());
            CeroBeam.Shot pierced = CeroBeam.fire(level, shooter, null, from, east, GranReyCero.spec(shooter, data),
                    GranReyCero.KIND);
            List<LivingEntity> struck = pierced.struck();
            helper.assertTrue(struck.size() == 2 && struck.contains(near) && struck.contains(far),
                    "the Gran Rey Cero struck " + struck);
            helper.assertTrue(unhurt(wolf), "the Gran Rey Cero hurt the shooter's own wolf");
            DamageSource source = far.getLastDamageSource();
            helper.assertTrue(source != null && source.is(GranReyCero.DAMAGE),
                    "the far Spider Hollow was struck by " + source);
            helper.assertTrue(pierced.hitBlock() && pierced.blocksBroken() == 0,
                    "without mob griefing the Gran Rey Cero hit the wall " + pierced.hitBlock() + " and broke "
                            + pierced.blocksBroken());
            helper.assertBlockPresent(Blocks.DIRT, 11, 2, 8);

            griefing.set(true, level.getServer());
            CeroBeam.Shot carved = CeroBeam.fire(level, shooter, null, from, east, GranReyCero.spec(shooter, data),
                    GranReyCero.KIND);
            helper.assertTrue(carved.hitBlock() && carved.blocksBroken() > 0 && carved.blocksBroken() <= 48,
                    "with mob griefing the Gran Rey Cero broke " + carved.blocksBroken() + " blocks");

            buildWall(helper);
            BleachConfig.GRAN_REY_CERO_BREAKS_BLOCKS.set(false);
            // The spec is built again, as each press builds it, to read the setting.
            CeroBeam.Shot spared = CeroBeam.fire(level, shooter, null, from, east, GranReyCero.spec(shooter, data),
                    GranReyCero.KIND);
            helper.assertTrue(spared.hitBlock() && spared.blocksBroken() == 0,
                    "with breaks_blocks off the Gran Rey Cero broke " + spared.blocksBroken() + " blocks");
            helper.assertBlockPresent(Blocks.DIRT, 11, 2, 8);
        } finally {
            griefing.set(savedGriefing, level.getServer());
            BleachConfig.GRAN_REY_CERO_BREAKS_BLOCKS.set(breaks);
            discard(wolf, near, far);
            discardBeams(helper);
        }
        helper.succeed();
    }

    /** A dirt wall across the beam's line, at x 11. */
    private static void buildWall(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(11, y, z, Blocks.DIRT);
            }
        }
    }

    // ---- The saved wait and the registrations ----

    @GameTest(template = "empty")
    public static void granReyCeroWaitSurvivesSavesAndTheKeyIsAppended(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HollowProgress.Arrancar record = new HollowProgress.Arrancar(true, 2, 1234L, 5678L, 3, 4321L);
        Tag saved = HollowProgress.Arrancar.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
        HollowProgress.Arrancar loaded = HollowProgress.Arrancar.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(record), "the Arrancar record came back as " + loaded);
        helper.assertTrue(saved instanceof CompoundTag tag && tag.contains("gran_rey_cero_ready_at"),
                "the record was saved without its Gran Rey Cero wait: " + saved);

        // A save from before the Gran Rey Cero has no wait in it; a bad one falls back on its own.
        HollowProgress.Arrancar none = record.withGranReyCeroReadyAt(HollowProgress.NEVER);
        CompoundTag old = ((CompoundTag) saved).copy();
        old.remove("gran_rey_cero_ready_at");
        HollowProgress.Arrancar oldLoaded = HollowProgress.Arrancar.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(oldLoaded.equals(none), "a save from before the Gran Rey Cero read as " + oldLoaded);
        CompoundTag broken = ((CompoundTag) saved).copy();
        broken.putString("gran_rey_cero_ready_at", "x");
        HollowProgress.Arrancar brokenLoaded = HollowProgress.Arrancar.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
        helper.assertTrue(brokenLoaded.equals(none), "a record whose wait is a string read as " + brokenLoaded);

        HollowProgress.Arrancar fiveParts = new HollowProgress.Arrancar(true, 2, 1234L, 5678L, 3);
        helper.assertTrue(fiveParts.granReyCeroReadyAt() == HollowProgress.NEVER,
                "the five-part record waits until " + fiveParts.granReyCeroReadyAt());
        HollowProgress.Arrancar again = record.becoming(false);
        helper.assertTrue(again.granReyCeroReadyAt() == HollowProgress.NEVER,
                "becoming again kept the wait until " + again.granReyCeroReadyAt());

        helper.assertTrue(HollowKey.GRAN_REY_CERO.ordinal() == 6,
                "the Gran Rey Cero key is " + HollowKey.GRAN_REY_CERO.ordinal());
        helper.assertTrue(HollowKey.CERO.ordinal() == 3 && HollowKey.CERO_RELEASE.ordinal() == 4
                && HollowKey.CERO_CANCEL.ordinal() == 5, "the Cero keys moved");

        Registry<DamageType> damageTypes = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        DamageType grand = damageTypes.get(GranReyCero.DAMAGE);
        helper.assertTrue(grand != null, "there is no Gran Rey Cero damage type");
        helper.assertTrue("bleach.gran_rey_cero".equals(grand.msgId()),
                "the Gran Rey Cero's damage message is " + grand.msgId());
        helper.assertTrue(grand.scaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                "the Gran Rey Cero's damage scales " + grand.scaling());
        // In no tag, as a Cero: armour, shields, Hierro and the reiatsu difference all count against it.
        helper.assertFalse(damageTypes.getHolderOrThrow(GranReyCero.DAMAGE).is(DamageTypeTags.BYPASSES_ARMOR),
                "a Gran Rey Cero passes armour");

        List<DeferredHolder<SoundEvent, SoundEvent>> sounds = List.of(ModSounds.GRAN_REY_CERO_CUT,
                ModSounds.GRAN_REY_CERO_FIRE, ModSounds.NEGACION_DESCEND, ModSounds.NEGACION_DEPART);
        for (DeferredHolder<SoundEvent, SoundEvent> sound : sounds) {
            helper.assertTrue(sound.isBound(), "the sound " + sound.getId() + " is not registered");
        }
        for (String name : List.of("gran_rey_cero", "negacion")) {
            helper.assertTrue(level.getServer().getAdvancements().get(BleachMod.id(name)) != null,
                    "advancement bleach:" + name + " did not load");
        }
        helper.succeed();
    }
}
