package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Visored's masked arts: the masked Cero, which waits for ten minutes of masks worn, then gathers, fires
 * and is paid for as a Gillian player's does, in the Visored's crimson and wider, which fizzles for free whenever the
 * mask goes, which shares the soul's reiatsu with the Getsuga Tenshō one art at a time, and which poisons a Quincy it
 * hits; the black Getsuga Tenshō, half again as strong, loosed from behind the mask; and the advancement for the first
 * mask. As in {@link VisoredTests}, mock players are never in the world and never tick, so every upkeep
 * ({@link VisoredEvents#tick}, {@link HollowBody#tickBody}, {@link HollowCero#tick}) and every key is driven by hand
 * with the time it needs, while a hit's own listeners run as they always do; every mask a test puts on and every Cero
 * it gathers is ended within the test's first tick or its sequence, and every beam, crescent and Hollow discarded. A
 * Visored stands at {@link #SPOT}, which only the {@code field} holds, so every test whose Cero gathers uses it: a beam
 * outside a test's box may be in a chunk not yet loaded, where the world cannot find it. The test server runs on
 * Normal, and nothing here is skipped on Peaceful: a player's blows and beams do not scale with the difficulty.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class VisoredArtsTests {
    private static final long DAY = 24_000L;
    /** Ten minutes of masks worn in all: the masked Cero's gate at the default {@code cero_after_minutes_worn}. */
    private static final long TRAINED = 12_000L;
    /** Where a Visored stands: in the field, four blocks north of block (8, 2, 8), facing south. */
    private static final Vec3 SPOT = new Vec3(8.5, 2.0, 4.5);
    /** How far about a player its own Cero is looked for: the beam gathers, and stays as it fires, ahead of the eyes. */
    private static final double BEAM_SEARCH = 4.0;

    private VisoredArtsTests() {
    }

    // ---- Helpers ----

    /**
     * A Visored at {@link #SPOT}, on the ground and facing south, its mask off: a mock Shinigami who knows Zangetsu's
     * name, its Asauchi in hand and this much reiryoku, full, whose Hollow took root a day ago and was beaten a tick
     * ago, and whose masks have been worn this many ticks in all.
     */
    private static Player visored(GameTestHelper helper, float maxReiryoku, long trainedTicks) {
        long now = HollowPlayerTests.now(helper);
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(player);
        data.setMaxReiryoku(maxReiryoku);
        data.setReiryoku(maxReiryoku);
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now - DAY).withSubduedAt(now - 1L)
                .withMaskTrainedTicks(trainedTicks));
        Vec3 at = helper.absoluteVec(SPOT);
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.setOnGround(true);
        return player;
    }

    /**
     * A Visored as {@link #visored} makes one, its mask put on now as the command puts it on: forced, so free and
     * whatever the wait. Only with the path on, as a forced mask is still refused with it off.
     */
    private static Player masked(GameTestHelper helper, float maxReiryoku, long trainedTicks) {
        Player player = visored(helper, maxReiryoku, trainedTicks);
        assertMaskOn(helper, player, HollowPlayerTests.now(helper), "a Visored");
        return player;
    }

    /** Puts the player's mask on now, forced, which must go on. */
    private static void assertMaskOn(GameTestHelper helper, Player player, long now, String what) {
        VisoredEvents.MaskStart start = VisoredEvents.putOnMask(player, SoulLogic.get(player), now, true);
        helper.assertTrue(start == VisoredEvents.MaskStart.PUT_ON, "the mask of " + what + ", by command, gave " + start);
    }

    /** Sets how long the soul's masks have been worn in all, as its saved record keeps it. */
    private static void train(SoulData data, long ticks) {
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withMaskTrainedTicks(ticks));
    }

    /**
     * Runs the body with this config value set, and puts it back afterwards, so it is changed only within this one
     * tick of the test.
     */
    private static void withConfig(ModConfigSpec.BooleanValue value, boolean set, Runnable body) {
        boolean before = BleachConfig.get(value);
        value.set(set);
        try {
            body.run();
        } finally {
            value.set(before);
        }
    }

    /**
     * Lets each player's Cero fizzle (one that fired is paid for) and takes off its mask, so nothing a test began is
     * left gathering or worn.
     */
    private static void end(long now, Player... players) {
        for (Player player : players) {
            SoulData data = SoulLogic.get(player);
            HollowCero.cancel(player, data);
            VisoredEvents.endAll(player, data, now);
        }
    }

    /** The Cero beams this player gathers or fired, looked for about it. */
    private static List<CeroBeam> ownBeams(GameTestHelper helper, Player player) {
        return helper.getLevel().getEntitiesOfClass(CeroBeam.class, player.getBoundingBox().inflate(BEAM_SEARCH),
                beam -> beam.owner() == player);
    }

    /** This player's own beams that still gather in the masked Visored's crimson. */
    private static List<CeroBeam> crimson(GameTestHelper helper, Player player) {
        return ownBeams(helper, player).stream()
                .filter(beam -> beam.isCharging() && beam.colour() == HollowCero.VISORED_CRIMSON).toList();
    }

    /** Ends each player's Cero and mask, then discards whatever beams of theirs are left. */
    private static void endWithBeams(GameTestHelper helper, long now, Player... players) {
        end(now, players);
        for (Player player : players) {
            ownBeams(helper, player).forEach(Entity::discard);
        }
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** Asks for the player's Cero now, which must be refused so, leaving nothing gathering. */
    private static void assertRefused(GameTestHelper helper, Player player, long now, HollowCero.Refusal expected,
                                      String what) {
        SoulData data = SoulLogic.get(player);
        HollowCero.Refusal refusal = HollowCero.start(player, data, now);
        helper.assertTrue(refusal == expected, "the Cero of " + what + " gave " + refusal + ", not " + expected);
        helper.assertFalse(HollowCero.charging(data), "the refused Cero of " + what + " is gathering");
    }

    /** Asks for the player's Cero now, which must begin gathering. */
    private static void assertGathers(GameTestHelper helper, Player player, long now, String what) {
        SoulData data = SoulLogic.get(player);
        HollowCero.Refusal refusal = HollowCero.start(player, data, now);
        helper.assertTrue(refusal == null && HollowCero.charging(data),
                "the Cero of " + what + " gave " + refusal + ", gathering " + HollowCero.charging(data));
    }

    /** To every rule but the Cero's the soul is still the Shinigami it is: no Hollow, and no Hollow source to a Quincy. */
    private static void assertNoHollow(GameTestHelper helper, Player player, String what) {
        SoulData data = SoulLogic.get(player);
        helper.assertFalse(HollowPlayerLogic.isHollow(data) || HollowPlayerLogic.isHollowOrArrancar(data)
                || QuincyLogic.isHollowSource(player), what + " counts as a Hollow");
    }

    /** A Cero's hit from this shooter, as its beam deals it, with the shooter standing in for the beam. */
    private static DamageSource cero(GameTestHelper helper, Player shooter) {
        return new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CeroBeam.DAMAGE), shooter, shooter);
    }

    /** Hits the Quincy for 4, which must land, and asserts whether the hit left Hollow poison in it. */
    private static void assertPoison(GameTestHelper helper, Player quincy, DamageSource source, boolean poisons,
                                     String what) {
        quincy.hurt(source, 4.0F);
        helper.assertTrue(quincy.getHealth() < quincy.getMaxHealth(),
                what + " did not land on the Quincy, at " + quincy.getHealth() + " health");
        float poison = SoulLogic.get(quincy).quincy().poison();
        helper.assertTrue(poisons ? poison > 0.0F : poison == 0.0F, what + " left the Quincy with " + poison + " poison");
    }

    // ---- The masked Cero ----

    @GameTest(template = "empty")
    public static void theMaskedCeroWaitsForTenMinutesWorn(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = visored(helper, 800.0F, 0L);
        SoulData data = SoulLogic.get(host);
        Player plain = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        try {
            helper.assertTrue(VisoredLogic.ceroUnlockTicks() == TRAINED,
                    "the masked Cero waits for " + VisoredLogic.ceroUnlockTicks() + " ticks worn, not " + TRAINED);

            // Untrained and unmasked, it is told first what it lacks the most.
            helper.assertFalse(VisoredLogic.maskedCero(data), "an untrained Visored without its mask has the masked Cero");
            helper.assertTrue(ArrancarLogic.ceroForm(data) == HollowTier.HOLLOW,
                    "an untrained Visored without its mask gathers as a " + ArrancarLogic.ceroForm(data));
            assertRefused(helper, host, now, HollowCero.Refusal.UNTRAINED, "an untrained Visored without its mask");
            assertNoHollow(helper, host, "a Visored without its mask");

            // Masked a tick short of ten minutes: still untrained, but its Getsuga Tenshō is already the black one.
            train(data, TRAINED - 1L);
            assertMaskOn(helper, host, now, "a Visored a tick short of ten minutes worn");
            assertRefused(helper, host, now, HollowCero.Refusal.UNTRAINED, "a masked Visored a tick short of ten minutes");
            helper.assertTrue(VisoredLogic.blackGetsuga(data), "a masked Visored's Getsuga Tenshō is not the black one");
            assertNoHollow(helper, host, "a masked Visored a tick short of ten minutes");

            // Ten minutes worn: it gathers a Gillian's Cero, and reads as a Gillian to the Cero alone.
            train(data, TRAINED);
            helper.assertTrue(VisoredLogic.maskedCero(data), "a masked Visored ten minutes worn has no masked Cero");
            helper.assertTrue(ArrancarLogic.ceroForm(data) == HollowTier.GILLIAN,
                    "a masked Visored ten minutes worn gathers as a " + ArrancarLogic.ceroForm(data));
            assertNoHollow(helper, host, "a masked Visored ten minutes worn");

            // The mask off, taken off this tick: worn no time, so the training stays, and it is told to put it on.
            VisoredEvents.endMask(host, data, VisoredEvents.MaskEnd.TAKEN_OFF, now);
            helper.assertFalse(VisoredLogic.isMasked(data), "the trained Visored's mask did not come off");
            assertRefused(helper, host, now, HollowCero.Refusal.UNMASKED, "a trained Visored without its mask");

            // The path off: a forced mask is refused with it off, so it goes on first. Nothing ticks, so no upkeep takes
            // it off again, and only the config differs.
            assertMaskOn(helper, host, now, "a trained Visored again");
            withConfig(BleachConfig.VISORED_ENABLED, false, () -> {
                helper.assertTrue(VisoredLogic.isMasked(data) && VisoredLogic.isVisored(data)
                        && VisoredLogic.record(data).maskTrainedTicks() >= TRAINED,
                        "the Visored changed with the path: " + VisoredLogic.record(data));
                helper.assertFalse(VisoredLogic.maskedCero(data), "a masked Visored has the masked Cero with the path off");
                helper.assertFalse(VisoredLogic.blackGetsuga(data),
                        "a masked Visored's Getsuga Tenshō is the black one with the path off");
                HollowPlayerTests.assertClose(helper, VisoredLogic.getsugaFactor(data), 1.0,
                        "the mask's Getsuga Tenshō factor with the path off");
                helper.assertTrue(ArrancarLogic.ceroForm(data) == HollowTier.HOLLOW,
                        "with the path off a masked Visored gathers as a " + ArrancarLogic.ceroForm(data));
                assertRefused(helper, host, now, HollowCero.Refusal.NOT_MENOS, "a masked Visored with the path off");
                assertNoHollow(helper, host, "a masked Visored with the path off");
            });
            end(now, host);

            // A Shinigami with no Hollow is told only a Menos gathers one, as before.
            assertRefused(helper, plain, now, HollowCero.Refusal.NOT_MENOS, "a Shinigami with no Hollow");
            helper.assertFalse(VisoredLogic.maskedCero(SoulLogic.get(plain)),
                    "a Shinigami with no Hollow has the masked Cero");
            assertNoHollow(helper, host, "a Visored whose mask came off");

            // The first mask's advancement, a leaf under the Visored's.
            AdvancementHolder masked = helper.getLevel().getServer().getAdvancements().get(BleachMod.id(Milestones.MASKED));
            helper.assertTrue(masked != null, "the " + Milestones.MASKED + " advancement did not load");
            helper.assertTrue(masked.value().parent().equals(Optional.of(BleachMod.id(Milestones.VISORED))),
                    "the " + Milestones.MASKED + " advancement's parent is " + masked.value().parent());
        } finally {
            endWithBeams(helper, now, host, plain);
        }
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 100)
    public static void theMaskedCeroGathersFiresAndPays(GameTestHelper helper) {
        long started = HollowPlayerTests.now(helper);
        Player host = masked(helper, 800.0F, TRAINED);
        SoulData data = SoulLogic.get(host);
        // Turned 15° down, so the beam meets the floor about six blocks out, inside the box.
        host.setXRot(15.0F);
        // Four blocks south of the spot, in the beam's way.
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        HollowProgress before = data.hollow();
        final CeroBeam beam;
        try {
            // 15% of 800, and four fifths of a Gillian player's at 800: 10 + 0.6·√800.
            HollowPlayerTests.assertClose(helper, HollowCero.cost(data), 120.0, "the masked Cero's cost at 800");
            HollowPlayerTests.assertClose(helper, HollowCero.damage(data, HollowTier.GILLIAN),
                    0.8 * (10.0 + 0.6 * Math.sqrt(800.0)), "the masked Cero's damage at 800");
            CeroBeam.Spec spec = HollowCero.spec(data, ArrancarLogic.ceroForm(data));
            helper.assertTrue(spec.colour() == HollowCero.VISORED_CRIMSON,
                    "the masked Cero is " + Integer.toHexString(spec.colour()) + ", not crimson");
            HollowPlayerTests.assertClose(helper, spec.radius(), 2.0, "the masked Cero's half-width");
            HollowPlayerTests.assertClose(helper, spec.blastRadius(), 3.6, "the masked Cero's blast");
            HollowPlayerTests.assertClose(helper, spec.knockback(), 1.6, "the masked Cero's knockback");
            HollowPlayerTests.assertClose(helper, spec.range(), 48.0, "the masked Cero's reach");
            helper.assertTrue(spec.maxBlocks() == 20 && spec.chargeTicks() == 50,
                    "the masked Cero breaks " + spec.maxBlocks() + " blocks and gathers in " + spec.chargeTicks() + " ticks");

            assertGathers(helper, host, started, "a masked Visored ten minutes worn");
            List<CeroBeam> gathering = crimson(helper, host);
            helper.assertTrue(gathering.size() == 1 && ownBeams(helper, host).size() == 1,
                    "the masked Visored gathers " + gathering.size() + " crimson beams of " + ownBeams(helper, host).size());
            beam = gathering.get(0);
            HollowCero.Refusal second = HollowCero.start(host, data, started);
            helper.assertTrue(second == null && crimson(helper, host).size() == 1 && ownBeams(helper, host).size() == 1,
                    "a second press gave " + second + " and " + ownBeams(helper, host).size() + " beams");
        } catch (RuntimeException | Error failure) {
            endWithBeams(helper, started, host);
            discard(spider);
            throw failure;
        }
        helper.startSequence()
                // 30 of a Gillian's 50 ticks: it fires at three fifths of its strength.
                .thenExecuteAfter(30, () -> {
                    long now = HollowPlayerTests.now(helper);
                    try {
                        HollowCero.release(host, data, now);
                        helper.assertTrue(beam.isFired(), "letting go after 30 ticks did not fire the masked Cero");
                        CeroBeam.Shot shot = beam.lastShot();
                        helper.assertTrue(shot != null && shot.struck().contains(spider),
                                "the masked Cero struck " + (shot != null ? shot.struck() : "nothing"));
                        helper.assertTrue(spider.getHealth() < spider.getMaxHealth(),
                                "the Spider Hollow in the masked Cero is unhurt");
                        HollowPlayerTests.assertClose(helper, data.reiryoku(), 680.0, "the reiryoku after a masked Cero");
                        helper.assertFalse(HollowCero.charging(data), "the fired masked Cero is still gathering");
                        HollowCero.Refusal soon = HollowCero.start(host, data, now);
                        helper.assertTrue(soon == HollowCero.Refusal.COOLDOWN, "a masked Cero just after one gave " + soon);
                        // Gathered and fired, it is still no Hollow: neither its state nor its Hollow's record moved.
                        helper.assertTrue(data.state() == SoulState.SHINIGAMI && !HollowPlayerLogic.isHollow(data),
                                "the masked Visored who fired a Cero is a " + data.state());
                        helper.assertTrue(data.hollow().equals(before), "the masked Cero wrote the Hollow record "
                                + data.hollow());
                    } finally {
                        endWithBeams(helper, now, host);
                        discard(spider);
                        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, helper.getBounds())
                                .forEach(Entity::discard);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "field")
    public static void theMaskedCeroFizzlesWhenTheMaskGoes(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player keyed = masked(helper, 800.0F, TRAINED);
        SoulData keyedData = SoulLogic.get(keyed);
        Player cracked = masked(helper, 800.0F, TRAINED);
        SoulData crackedData = SoulLogic.get(cracked);
        Player crumbled = masked(helper, 800.0F, TRAINED);
        SoulData crumbledData = SoulLogic.get(crumbled);
        Player lender = masked(helper, 800.0F, TRAINED);
        SoulData lenderData = SoulLogic.get(lender);
        Player disabled = masked(helper, 800.0F, TRAINED);
        SoulData disabledData = SoulLogic.get(disabled);
        Player kept = masked(helper, 800.0F, TRAINED);
        SoulData keptData = SoulLogic.get(kept);
        Player[] all = {keyed, cracked, crumbled, lender, disabled, kept};
        try {
            for (Player player : all) {
                assertGathers(helper, player, now, "a masked Visored ten minutes worn");
            }

            // Taken off with the key: it fizzles, and no cooldown began, so with the mask back on it gathers at once.
            VisoredEvents.onMaskKey(keyed, keyedData, now);
            helper.assertFalse(VisoredLogic.isMasked(keyedData), "the Hollow Mask key did not take the mask off");
            helper.assertFalse(HollowCero.charging(keyedData), "a Cero went on gathering after the mask was taken off");
            assertMaskOn(helper, keyed, now, "a Visored that took its mask off");
            assertGathers(helper, keyed, now, "a Visored whose Cero fizzled with its mask");

            // A crack, half the shatter share of 20 health in one wearing: the mask stays on, and the Cero breaks.
            VisoredEvents.noteMaskBlow(cracked, crackedData, 3.0F, now + 1L);
            helper.assertTrue(VisoredLogic.isMasked(crackedData) && VisoredLogic.maskCracked(crackedData),
                    "a blow of 3 of 20 did not crack the mask, or took it off");
            HollowCero.tick(cracked, crackedData, now + 1L);
            helper.assertFalse(HollowCero.charging(crackedData), "a fresh crack kept the masked Cero");

            // Crumbled at its time.
            long endsAt = VisoredLogic.record(crumbledData).maskEndsAt();
            VisoredEvents.tick(crumbled, crumbledData, endsAt);
            helper.assertFalse(VisoredLogic.isMasked(crumbledData), "the mask did not crumble at its time");
            helper.assertFalse(HollowCero.charging(crumbledData), "a Cero went on gathering after the mask crumbled");

            // The power lent away: the next upkeep takes the mask off.
            lenderData.setShinigami(lenderData.shinigami().withPowerLentUntil(now + 1_000L));
            VisoredEvents.tick(lender, lenderData, now + 1L);
            helper.assertFalse(VisoredLogic.isMasked(lenderData), "lending the power did not take the mask off");
            helper.assertFalse(HollowCero.charging(lenderData), "a Cero went on gathering after the power was lent");

            // The path off, the mask still on for the tick: the body's tick lets it fizzle, as any soul's that is no Menos.
            withConfig(BleachConfig.VISORED_ENABLED, false, () -> HollowBody.tickBody(disabled, disabledData, now + 1L));
            helper.assertFalse(HollowCero.charging(disabledData), "the body's tick kept a Cero with the path off");

            // The path on, the body's tick keeps a masked Visored's Cero gathering.
            HollowBody.tickBody(kept, keptData, now + 1L);
            helper.assertTrue(HollowCero.charging(keptData), "the body's tick let a masked Visored's Cero fizzle");

            for (Player player : all) {
                HollowPlayerTests.assertClose(helper, SoulLogic.get(player).reiryoku(), 800.0,
                        "the reiryoku after a Cero that fizzled");
            }
        } finally {
            endWithBeams(helper, now + 1L, all);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void oneArtGathersAtATime(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = masked(helper, 800.0F, TRAINED);
        SoulData data = SoulLogic.get(host);
        try {
            ShikaiEvents.Result release = ShikaiEvents.release(host);
            helper.assertTrue(release == ShikaiEvents.Result.RELEASED, "Zangetsu's release for the Visored gave " + release);
            ShikaiEvents.GatherResult first = ShikaiEvents.gather(host);
            helper.assertTrue(first == ShikaiEvents.GatherResult.GATHERING, "the Getsuga Tenshō's gathering gave " + first);

            // A Getsuga Tenshō gathering: the Cero is refused, silently, and nothing gathers at the mask.
            HollowCero.Refusal during = HollowCero.start(host, data, now);
            helper.assertTrue(during == null && !HollowCero.charging(data),
                    "a Cero during a Getsuga Tenshō gave " + during + ", gathering " + HollowCero.charging(data));
            ShikaiEvents.cancelGather(host, data);
            assertGathers(helper, host, now, "a masked Visored once its Getsuga Tenshō was let go");

            // A Cero gathering: the Getsuga Tenshō is refused, and told so.
            ShikaiEvents.GatherResult second = ShikaiEvents.gather(host);
            helper.assertTrue(second == ShikaiEvents.GatherResult.ALREADY_GATHERING,
                    "a Getsuga Tenshō during a Cero gave " + second);
            helper.assertFalse(data.isGathering(), "a Getsuga Tenshō gathers beside the Cero");
            // No Hollow and no Arrancar, so the Gran Rey Cero key's gate stays shut.
            helper.assertFalse(HollowPlayerLogic.isHollowOrArrancar(data),
                    "a masked Visored passes the Gran Rey Cero's gate");
        } finally {
            ShikaiEvents.cancelGather(host, data);
            endWithBeams(helper, now, host);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aMaskedCeroPoisonsAQuincy(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = masked(helper, 800.0F, TRAINED);
        Player plain = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData plainData = SoulLogic.get(plain);
        plainData.setMaxReiryoku(800.0F);
        plainData.setReiryoku(800.0F);
        Player struck = ZanpakutoTests.mock(helper, QuincyTests.quincy(Bloodline.GEMISCHT, 1, 40.0F));
        Player cut = ZanpakutoTests.mock(helper, QuincyTests.quincy(Bloodline.GEMISCHT, 1, 40.0F));
        Player spared = ZanpakutoTests.mock(helper, QuincyTests.quincy(Bloodline.GEMISCHT, 1, 40.0F));
        try {
            // The Cero carries Hollow reiatsu; the Visored's blade and fists are a Shinigami's, and so is another's Cero.
            assertPoison(helper, struck, cero(helper, host), true, "a masked Visored's Cero");
            assertPoison(helper, cut, cut.damageSources().playerAttack(host), false, "a masked Visored's blow");
            assertPoison(helper, spared, cero(helper, plain), false, "a Shinigami's Cero");
            helper.assertFalse(QuincyLogic.isHollowSource(host), "a masked Visored is a Hollow source");
        } finally {
            end(now, host, plain);
        }
        helper.succeed();
    }

    // ---- The black Getsuga Tenshō ----

    @GameTest(template = "empty")
    public static void theMaskedGetsugaIsBlack(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        // The black one needs no training: only the mask.
        Player host = masked(helper, 800.0F, 0L);
        SoulData data = SoulLogic.get(host);
        Player bare = visored(helper, 800.0F, 0L);
        SoulData bareData = SoulLogic.get(bare);
        Player strongest = masked(helper, 1.0E6F, 0L);
        SoulData strongestData = SoulLogic.get(strongest);
        strongestData.setZanpakuto(strongestData.zanpakuto().withGetsugaNamed(true));
        Player late = masked(helper, 800.0F, 0L);
        SoulData lateData = SoulLogic.get(late);
        GetsugaTensho black = null;
        GetsugaTensho pale = null;
        GetsugaTensho unmasked = null;
        try {
            // Unmasked, a Visored's swing is anyone's; masked, half again; at the cap of 60, 72 a full swing, and 108.
            SoulData plain = ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F);
            plain.setZanpakuto(plain.zanpakuto().withGetsugaNamed(bareData.zanpakuto().getsugaNamed()));
            float bareDamage = ZanpakutoLogic.getsugaDamage(bareData, 1.0F);
            HollowPlayerTests.assertClose(helper, bareDamage, ZanpakutoLogic.getsugaDamage(plain, 1.0F),
                    "an unmasked Visored's full swing at 800");
            HollowPlayerTests.assertClose(helper, ZanpakutoLogic.getsugaDamage(data, 1.0F), 1.5 * bareDamage,
                    "a masked Visored's full swing at 800");
            HollowPlayerTests.assertClose(helper, ZanpakutoLogic.getsugaDamage(strongestData, 1.0F), 108.0,
                    "a masked Visored's named full swing at the cap");
            withConfig(BleachConfig.VISORED_ENABLED, false, () -> HollowPlayerTests.assertClose(helper,
                    ZanpakutoLogic.getsugaDamage(data, 1.0F), ZanpakutoLogic.getsugaDamage(bareData, 1.0F),
                    "a masked Visored's full swing with the path off"));

            for (Player player : new Player[] {host, bare, late}) {
                ShikaiEvents.Result release = ShikaiEvents.release(player);
                helper.assertTrue(release == ShikaiEvents.Result.RELEASED, "Zangetsu's release gave " + release);
                ShikaiEvents.GatherResult gather = ShikaiEvents.gather(player);
                helper.assertTrue(gather == ShikaiEvents.GatherResult.GATHERING, "the Getsuga Tenshō's gathering gave "
                        + gather);
            }
            black = ShikaiEvents.fire(host);
            pale = ShikaiEvents.fire(bare);
            int colour = ZanpakutoItem.blade(host.getMainHandItem()).colour() & 0xFFFFFF;
            helper.assertTrue(black != null && black.isBlack(), "the masked Visored's Getsuga Tenshō is not the black one");
            helper.assertTrue((black.colour() & 0xFFFFFF) == colour, "the black crescent's colour bits are "
                    + Integer.toHexString(black.colour() & 0xFFFFFF) + ", not the blade's " + Integer.toHexString(colour));
            helper.assertTrue(pale != null && !pale.isBlack(), "an unmasked Visored's Getsuga Tenshō is the black one");

            // Decided as the blade swings: one in flight stays black when the mask comes off, and one gathering does not.
            VisoredEvents.onMaskKey(host, data, now);
            helper.assertTrue(!VisoredLogic.isMasked(data) && black.isBlack(),
                    "the black crescent in flight turned pale when the mask came off");
            VisoredEvents.onMaskKey(late, lateData, now);
            helper.assertFalse(VisoredLogic.isMasked(lateData), "the Hollow Mask key did not take the mask off mid-gather");
            unmasked = ShikaiEvents.fire(late);
            helper.assertTrue(unmasked != null && !unmasked.isBlack(),
                    "a Getsuga Tenshō whose mask came off mid-gather is the black one");
            helper.assertTrue(GetsugaTensho.BLACK == 1 << 24, "the black bit is " + Integer.toHexString(GetsugaTensho.BLACK));
        } finally {
            discard(black, pale, unmasked);
            end(now, host, bare, strongest, late);
        }
        helper.succeed();
    }
}
