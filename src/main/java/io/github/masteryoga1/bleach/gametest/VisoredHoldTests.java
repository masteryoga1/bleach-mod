package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Hollow's hold: the rules and the roll by which a rooted Hollow seizes its host's body when a blow brings
 * it near death, more readily after a Getsuga Tenshō and never twice within two minutes; what the Hollow does with the
 * body (a harder blow, a heal each second, and no blow but {@code /kill} taking it below one heart) and the arts it
 * seals (Shikai, the Getsuga Tenshō and Konsō); the Hollow Mask key tearing its half mask off, and every other way it
 * lets go; a masked Visored losing control to its own black Getsuga Tenshō at low health; and, with the server's leave,
 * the fight lost once too often that makes the soul a Hollow. As in {@link VisoredTests}, mock players are never in the
 * world and never tick, so every upkeep is driven by calling {@link VisoredEvents#tick} with the time it needs, while a
 * blow's own listeners run as they always do. Every takeover, fight and mask a test begins is ended, and every crescent
 * and creature discarded, within the test's first tick, so the server's table of fights is empty whenever another test
 * runs; every chance a blow rolls against is set to 1 or 0 for that tick, so no test rests on a roll. Shunpo's seal needs
 * a real player ({@code FlashStep} takes a server player) and is read in review. The test server runs on Normal; on
 * Peaceful, where the Hollow stays quiet, the tests of a blow near death and of the fall check only that nothing starts.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class VisoredHoldTests {
    private static final long DAY = 24_000L;
    private static final long NEVER = ShinigamiProgress.NEVER;
    /** Ten minutes of masks worn in all: the masked Cero's gate at the default {@code cero_after_minutes_worn}. */
    private static final long TRAINED = 12_000L;
    /** Where a soul stands: the middle of the field, so a Hollow stepping out 4 blocks south of it is in the box. */
    private static final Vec3 SPOT = new Vec3(8.5, 2.0, 8.5);

    private VisoredHoldTests() {
    }

    // ---- Helpers ----

    /**
     * A mock Shinigami who knows Zangetsu's name, with its Asauchi in hand and this much reiryoku, full, whose Hollow
     * took root a day ago and is not beaten, standing at {@link #SPOT} on the ground and facing south.
     */
    private static Player rooted(GameTestHelper helper, float maxReiryoku) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(player);
        data.setMaxReiryoku(maxReiryoku);
        data.setReiryoku(maxReiryoku);
        root(data, HollowPlayerTests.now(helper));
        stand(helper, player);
        return player;
    }

    /** A soul as {@link #rooted} makes one, whose Hollow can be faced now and has won this many fights. */
    private static Player ready(GameTestHelper helper, float maxReiryoku, int losses) {
        Player player = rooted(helper, maxReiryoku);
        SoulData data = SoulLogic.get(player);
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withDuelReadyAt(HollowPlayerTests.now(helper))
                .withLosses(losses));
        return player;
    }

    /**
     * A Visored at {@link #SPOT}, its mask off: a mock Shinigami as {@link #rooted} makes one, whose Hollow was beaten a
     * tick ago, and whose masks have been worn this many ticks in all.
     */
    private static Player visored(GameTestHelper helper, float maxReiryoku, long trainedTicks) {
        long now = HollowPlayerTests.now(helper);
        Player player = rooted(helper, maxReiryoku);
        SoulData data = SoulLogic.get(player);
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withSubduedAt(now - 1L).withMaskTrainedTicks(trainedTicks));
        return player;
    }

    /**
     * A Visored as {@link #visored} makes one, its mask put on now as the command puts it on: forced, so free and
     * whatever the wait. Only with the path on, as a forced mask is still refused with it off.
     */
    private static Player masked(GameTestHelper helper, float maxReiryoku, long trainedTicks) {
        Player player = visored(helper, maxReiryoku, trainedTicks);
        VisoredEvents.MaskStart start = VisoredEvents.putOnMask(player, SoulLogic.get(player), HollowPlayerTests.now(helper),
                true);
        helper.assertTrue(start == VisoredEvents.MaskStart.PUT_ON, "the mask of a Visored, by command, gave " + start);
        return player;
    }

    /** A mock Shinigami in this game mode who knows Zangetsu's name, with 900 reiryoku, its Hollow rooted a day ago. */
    private static Player inMode(GameTestHelper helper, GameType mode) {
        Player player = helper.makeMockPlayer(mode);
        SoulData data = ZanpakutoTests.soul(SoulState.SHINIGAMI, 900.0F);
        player.setData(SoulAttachments.SOUL, data);
        player.tickCount = 1000;
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        ZanpakutoEvents.learnName(player, data, ZanpakutoSpirits.ZANGETSU.location(),
                ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), player.getMainHandItem());
        root(data, HollowPlayerTests.now(helper));
        stand(helper, player);
        return player;
    }

    /** A Hollow rooted in this soul a day ago, as the wounds would root it but with none of its signs. */
    private static void root(SoulData data, long now) {
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now - DAY));
    }

    /** Puts the player at {@link #SPOT}, on the ground and facing south. */
    private static void stand(GameTestHelper helper, Player player) {
        Vec3 at = helper.absoluteVec(SPOT);
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.setOnGround(true);
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

    /** The same for a number: a chance set to 1 or 0, so no roll decides a test. */
    private static void withConfig(ModConfigSpec.DoubleValue value, double set, Runnable body) {
        double before = BleachConfig.get(value);
        value.set(set);
        try {
            body.run();
        } finally {
            value.set(before);
        }
    }

    /** Brings out the ready host's Hollow, which must come, and makes it stand still. */
    private static InnerHollow begin(GameTestHelper helper, Player host, long now) {
        VisoredEvents.DuelStart start = VisoredEvents.beginDuel(host, SoulLogic.get(host), now, false);
        InnerHollow hollow = VisoredEvents.duelOf(host.getUUID());
        helper.assertTrue(start == VisoredEvents.DuelStart.STARTED && hollow != null,
                "the ready Hollow did not come out: " + start);
        return QuincyTests.still(hollow);
    }

    /**
     * Brings the ready host's Hollow out and lets it land its losing blow, through every listener: 5 on a host at 6,
     * which the fight cuts to leave one heart. Gives back the Hollow, which the loss has taken away.
     */
    private static InnerHollow lose(GameTestHelper helper, Player host, long now) {
        InnerHollow hollow = begin(helper, host, now);
        host.setHealth(6.0F);
        host.hurt(host.damageSources().mobAttack(hollow), 5.0F);
        return hollow;
    }

    /**
     * Puts the victim at this health and lands a blow of this much on it from the attacker, through every listener,
     * which must land. A creature's blow from a player is not scaled by the difficulty, no Zanjutsu is added to it (only
     * a player's own attack gets that), and an attacker of 5,000 against 900 is not cut by the reiatsu difference, so it
     * lands exactly.
     */
    private static void strike(GameTestHelper helper, Player victim, Player attacker, float health, float damage,
                               String what) {
        victim.setHealth(health);
        victim.invulnerableTime = 0;
        helper.assertTrue(victim.hurt(victim.damageSources().mobAttack(attacker), damage), "the blow on " + what
                + " did not land");
    }

    /** Releases Zangetsu and gathers a Getsuga Tenshō, both of which must work, and looses it now. */
    private static GetsugaTensho getsuga(GameTestHelper helper, Player player) {
        ShikaiEvents.Result release = ShikaiEvents.release(player);
        helper.assertTrue(release == ShikaiEvents.Result.RELEASED, "Zangetsu's release gave " + release);
        ShikaiEvents.GatherResult gather = ShikaiEvents.gather(player);
        helper.assertTrue(gather == ShikaiEvents.GatherResult.GATHERING, "the Getsuga Tenshō's gathering gave " + gather);
        GetsugaTensho crescent = ShikaiEvents.fire(player);
        helper.assertTrue(crescent != null, "the gathered Getsuga Tenshō was not loosed");
        return crescent;
    }

    /** Whether the inner Hollow holds this player's body now. */
    private static boolean taken(Player player) {
        return VisoredLogic.takenOver(SoulLogic.get(player));
    }

    /**
     * The Hollow holds this soul's body no more: no takeover, none of its melee damage and no half mask in the look,
     * and the soul slowed only when the Hollow let go of it wearied.
     */
    private static void assertFree(GameTestHelper helper, Player player, boolean wearied, String what) {
        SoulData data = SoulLogic.get(player);
        helper.assertFalse(VisoredLogic.takenOver(data), "the Hollow still holds " + what);
        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.ATTACK_DAMAGE,
                VisoredLogic.TAKEOVER_DAMAGE_ID) == null, what + " kept the Hollow's melee damage");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, HollowPlayerTests.now(helper))
                & HollowPlayerLogic.LOOK_TAKEN_OVER) == 0, what + " still wears the half mask");
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) == wearied,
                what + (wearied ? " was not left wearied" : " was left wearied"));
    }

    /** The Hollow may not seize this soul's body, of this kind, by any caller, and nothing about the soul changed. */
    private static void assertRefused(GameTestHelper helper, Player player, boolean masked, long now, String what) {
        helper.assertFalse(VisoredEvents.startTakeover(player, SoulLogic.get(player), masked, now),
                "the Hollow seized " + what + (masked ? " behind its mask" : ""));
        assertFree(helper, player, false, what);
    }

    /**
     * Lets go of each player's takeover, breaks off its fight, takes off its mask and lets a Cero gathering at it
     * fizzle, so nothing a test began is left in the server's tables or on the soul.
     */
    private static void end(long now, Player... players) {
        for (Player player : players) {
            SoulData data = SoulLogic.get(player);
            HollowCero.cancel(player, data);
            VisoredEvents.endAll(player, data, now);
        }
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    // ---- The rules ----

    @GameTest(template = "empty")
    public static void theHoldsRulesAndTheRoll(GameTestHelper helper) {
        // Near death is a fifth of 20: crossed by the blow, from above the line to it or below, and still alive.
        helper.assertTrue(VisoredLogic.isTakeoverBlow(5.0F, 4.0F, 20.0F), "a blow from 5 to 4 of 20 is not near death");
        helper.assertTrue(VisoredLogic.isTakeoverBlow(4.5F, 4.0F, 20.0F), "a blow from 4.5 to 4 of 20 is not near death");
        helper.assertFalse(VisoredLogic.isTakeoverBlow(4.0F, 3.0F, 20.0F),
                "a blow from 4 to 3 of 20, below the line already, is near death again");
        helper.assertFalse(VisoredLogic.isTakeoverBlow(5.0F, 4.01F, 20.0F), "a blow from 5 to 4.01 of 20 is near death");
        helper.assertFalse(VisoredLogic.isTakeoverBlow(5.0F, 0.0F, 20.0F), "a killing blow is near death");

        // A roll below the chance lets the Hollow in, so a chance of 1 always does and one of 0 never does.
        helper.assertTrue(VisoredLogic.takeoverRolled(0.3499, 0.35), "a roll of 0.3499 at 0.35 failed");
        helper.assertTrue(VisoredLogic.takeoverRolled(0.999, 1.0), "a roll of 0.999 at 1 failed");
        helper.assertFalse(VisoredLogic.takeoverRolled(0.35, 0.35), "a roll of 0.35 at 0.35 let the Hollow in");
        helper.assertFalse(VisoredLogic.takeoverRolled(0.0, 0.0), "a roll of 0 at 0 let the Hollow in");

        // 35%, or 60% for 30 seconds after a Getsuga Tenshō: whichever is higher.
        long now = HollowPlayerTests.now(helper);
        HollowPlayerTests.assertClose(helper, VisoredLogic.takeoverChance(NEVER, now), 0.35,
                "the chance with no Getsuga Tenshō loosed");
        HollowPlayerTests.assertClose(helper, VisoredLogic.takeoverChance(now - 600L, now), 0.6,
                "the chance 30 seconds after a Getsuga Tenshō");
        HollowPlayerTests.assertClose(helper, VisoredLogic.takeoverChance(now - 601L, now), 0.35,
                "the chance a tick past 30 seconds after a Getsuga Tenshō");
        withConfig(BleachConfig.VISORED_TAKEOVER_CHANCE, 0.8, () -> HollowPlayerTests.assertClose(helper,
                VisoredLogic.takeoverChance(now - 1L, now), 0.8, "a chance of 0.8 just after a Getsuga Tenshō"));
        helper.assertTrue(VisoredLogic.takeoverTicks(false) == 200L && VisoredLogic.takeoverTicks(true) == 100L,
                "the Hollow holds the body " + VisoredLogic.takeoverTicks(false) + " ticks, and behind the mask "
                        + VisoredLogic.takeoverTicks(true));

        // The fall is off unless a server turns it on, and then comes with the third loss.
        helper.assertFalse(VisoredLogic.fallsOnLoss(3), "a third loss makes a Hollow with the fall turned off");
        withConfig(BleachConfig.VISORED_FALL_INTO_HOLLOW, true, () -> {
            helper.assertFalse(VisoredLogic.fallsOnLoss(2), "a second loss makes a Hollow");
            helper.assertTrue(VisoredLogic.fallsOnLoss(3) && VisoredLogic.fallsOnLoss(7),
                    "a third or seventh loss makes no Hollow with the fall turned on");
        });

        Player host = rooted(helper, 900.0F);
        SoulData data = SoulLogic.get(host);
        Player beaten = visored(helper, 900.0F, 0L);
        SoulData beatenData = SoulLogic.get(beaten);
        Player wearer = masked(helper, 800.0F, 0L);
        SoulData wearerData = SoulLogic.get(wearer);
        Player plain = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        Player lender = rooted(helper, 900.0F);
        SoulData lent = SoulLogic.get(lender);
        lent.setShinigami(lent.shinigami().withPowerLentUntil(now + 1_000L));
        Player fallen = rooted(helper, 900.0F);
        SoulLogic.get(fallen).setState(SoulState.HOLLOW);
        try {
            // Near death, only a rooted Hollow that is not beaten may seize its host, whose own power is not lent away.
            helper.assertTrue(VisoredLogic.mayBeTakenOver(data, now), "a rooted Hollow may not seize its host");
            helper.assertFalse(VisoredLogic.mayBeTakenOver(beatenData, now), "a beaten Hollow may seize its Visored");
            helper.assertFalse(VisoredLogic.mayBeTakenOver(SoulLogic.get(plain), now), "a soul with no Hollow may be seized");
            helper.assertFalse(VisoredLogic.mayBeTakenOver(lent, now), "a soul whose power is lent may be seized");
            helper.assertFalse(VisoredLogic.mayBeTakenOver(SoulLogic.get(fallen), now), "a Hollow may be seized");
            withConfig(BleachConfig.VISORED_ENABLED, false, () -> helper.assertFalse(VisoredLogic.mayBeTakenOver(data, now),
                    "a rooted Hollow may seize its host with the path off"));

            // Behind the mask, the black Getsuga Tenshō at a quarter of 20 or less, and alive.
            helper.assertTrue(VisoredLogic.losesControl(wearerData, 5.0F, 20.0F, now),
                    "a masked Visored at 5 of 20 keeps control");
            helper.assertFalse(VisoredLogic.losesControl(wearerData, 5.01F, 20.0F, now),
                    "a masked Visored at 5.01 of 20 loses control");
            helper.assertFalse(VisoredLogic.losesControl(wearerData, 0.0F, 20.0F, now), "a dead Visored loses control");
            helper.assertFalse(VisoredLogic.losesControl(beatenData, 5.0F, 20.0F, now)
                    || VisoredLogic.losesControl(data, 5.0F, 20.0F, now), "a soul with no mask on loses control");

            // Every caller is held to who may be seized: half a mask only before the win, and the masked kind only masked.
            assertRefused(helper, plain, false, now, "a soul with no Hollow");
            assertRefused(helper, lender, false, now, "a soul whose power is lent");
            assertRefused(helper, fallen, false, now, "a Hollow");
            assertRefused(helper, beaten, false, now, "a Visored");
            assertRefused(helper, beaten, true, now, "a Visored without its mask");
            assertRefused(helper, wearer, false, now, "a masked Visored");
            helper.assertTrue(VisoredEvents.startTakeover(host, data, false, now) && VisoredLogic.takenOver(data),
                    "the rooted Hollow did not seize its host");
            helper.assertFalse(VisoredEvents.startTakeover(host, data, false, now), "the Hollow seized a body it holds");

            // Let go silently, the next may begin only two minutes after this one began.
            VisoredEvents.endTakeover(host, data, VisoredEvents.TakeoverEnd.REMOVED, now);
            assertFree(helper, host, false, "a host whose Hollow let go of it unseen");
            helper.assertFalse(VisoredLogic.mayBeTakenOver(data, now + 2_399L),
                    "the Hollow may seize its host again a tick short of two minutes");
            helper.assertTrue(VisoredLogic.mayBeTakenOver(data, now + 2_400L),
                    "the Hollow may not seize its host again two minutes on");
            helper.assertTrue(VisoredLogic.losesControl(wearerData, 5.0F, 20.0F, now + 1L),
                    "another soul's takeover kept a masked Visored from losing control");

            // The advancement, beside the Visored's under the Hollow taking root.
            AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
                    .get(BleachMod.id(Milestones.TAKEN_OVER));
            helper.assertTrue(advancement != null, "the " + Milestones.TAKEN_OVER + " advancement did not load");
            helper.assertTrue(advancement.value().parent().equals(Optional.of(BleachMod.id(Milestones.HOLLOWFIED))),
                    "the " + Milestones.TAKEN_OVER + " advancement's parent is " + advancement.value().parent());
        } finally {
            end(now, host, beaten, wearer, plain, lender, fallen);
        }
        helper.succeed();
    }

    // ---- The takeover ----

    @GameTest(template = "field")
    public static void aBlowNearDeathLetsTheHollowIn(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player attacker = rooted(helper, 5_000.0F);
        Player host = rooted(helper, 900.0F);
        SoulData data = SoulLogic.get(host);
        HollowProgress before = data.hollow();
        if (HollowPlayerTests.peaceful(helper)) {
            try {
                withConfig(BleachConfig.VISORED_TAKEOVER_CHANCE, 1.0, () -> {
                    strike(helper, host, attacker, 5.0F, 1.5F, "a rooted Shinigami on Peaceful");
                    helper.assertFalse(VisoredLogic.takenOver(data), "the Hollow seized its host on Peaceful");
                });
            } finally {
                end(now, attacker, host);
            }
            helper.succeed();
            return;
        }
        Player dying = rooted(helper, 900.0F);
        SoulData dyingData = SoulLogic.get(dying);
        Player low = rooted(helper, 900.0F);
        Player beaten = visored(helper, 900.0F, 0L);
        Player unlucky = rooted(helper, 900.0F);
        Player reaching = rooted(helper, 900.0F);
        Player steady = rooted(helper, 900.0F);
        Player[] all = {attacker, host, dying, low, beaten, unlucky, reaching, steady};
        GetsugaTensho crescent = null;
        try {
            withConfig(BleachConfig.VISORED_TAKEOVER_CHANCE, 1.0, () -> {
                // From 5 to 3.5 of 20 crosses the line: the Hollow seizes the body, with half a mask and a harder blow.
                strike(helper, host, attacker, 5.0F, 1.5F, "a rooted Shinigami");
                HollowPlayerTests.assertClose(helper, host.getHealth(), 3.5, "the host's health after the blow");
                helper.assertTrue(VisoredLogic.takenOver(data), "a blow from 5 to 3.5 of 20 did not let the Hollow in");
                HollowPlayerTests.assertModifier(helper, host, Attributes.ATTACK_DAMAGE, VisoredLogic.TAKEOVER_DAMAGE_ID,
                        0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the Hollow's melee damage");
                helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_TAKEN_OVER) != 0,
                        "the half mask is not in the look");
                helper.assertTrue((HollowPlayerLogic.hollowLook(data) & HollowPlayerLogic.LOOK_TAKEN_OVER) == 0,
                        "the half mask is in the look the client could make for itself");
                // To every rule but its own the seized soul is still the Shinigami it is.
                helper.assertTrue(data.state() == SoulState.SHINIGAMI && ShinigamiLogic.hasShinigamiPower(data),
                        "the seized soul is a " + data.state());
                helper.assertFalse(HollowPlayerLogic.isHollow(data) || HollowPlayerLogic.isHollowOrArrancar(data)
                        || HollowPlayerLogic.isHollowPlayer(host) || QuincyLogic.isHollowSource(host),
                        "the seized soul counts as a Hollow");
                helper.assertFalse(ArrancarLogic.ceroForm(data).isMenos(), "the seized soul gathers as a Menos");
                helper.assertTrue(data.hollow().equals(before), "the takeover wrote the Hollow record " + data.hollow());

                // The Hollow will not let its host die: a blow of 30 stops at one heart.
                strike(helper, host, attacker, 3.5F, 30.0F, "the seized host");
                helper.assertTrue(host.isAlive(), "a blow of 30 killed the seized host");
                HollowPlayerTests.assertClose(helper, host.getHealth(), 2.0, "the seized host's health after a blow of 30");

                // Only what passes invulnerability gets past it, and death lets go of the body.
                helper.assertTrue(VisoredEvents.startTakeover(dying, dyingData, false, now),
                        "the Hollow did not seize the second host");
                dying.getInventory().clearContent();
                dying.hurt(dying.damageSources().genericKill(), 1_000.0F);
                helper.assertTrue(dying.isDeadOrDying(), "/kill left a seized host at " + dying.getHealth());
                assertFree(helper, dying, false, "a seized host that died");

                // Let go, the host's next fall near death within two minutes lets nothing in.
                VisoredEvents.endTakeover(host, data, VisoredEvents.TakeoverEnd.REMOVED, now);
                strike(helper, host, attacker, 5.0F, 1.5F, "the host let go");
                helper.assertFalse(VisoredLogic.takenOver(data), "the Hollow seized its host twice within two minutes");

                // A blow below the line already, and a beaten Hollow, let nothing in.
                strike(helper, low, attacker, 3.0F, 1.0F, "a rooted Shinigami below the line");
                helper.assertFalse(taken(low), "a blow from 3 to 2 of 20 let the Hollow in");
                strike(helper, beaten, attacker, 5.0F, 1.5F, "a Visored");
                helper.assertFalse(taken(beaten), "a beaten Hollow seized its Visored near death");
            });

            // A Getsuga Tenshō just loosed, before the blow: reaching for the blade's power lets the Hollow in.
            crescent = getsuga(helper, reaching);
            helper.assertFalse(crescent.isBlack(), "an unbeaten soul's Getsuga Tenshō is the black one");
            withConfig(BleachConfig.VISORED_TAKEOVER_CHANCE, 0.0, () -> {
                strike(helper, unlucky, attacker, 5.0F, 1.5F, "a rooted Shinigami at a chance of 0");
                helper.assertFalse(taken(unlucky), "a chance of 0 let the Hollow in");
                withConfig(BleachConfig.VISORED_TAKEOVER_CHANCE_AFTER_GETSUGA, 1.0, () -> {
                    strike(helper, reaching, attacker, 5.0F, 1.5F, "a rooted Shinigami after its Getsuga Tenshō");
                    helper.assertTrue(taken(reaching), "a Getsuga Tenshō just loosed did not let the Hollow in");
                    strike(helper, steady, attacker, 5.0F, 1.5F, "a rooted Shinigami with no Getsuga Tenshō");
                    helper.assertFalse(taken(steady), "the chance after a Getsuga Tenshō let the Hollow in without one");
                });
            });
        } finally {
            discard(crescent);
            end(now, all);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void theHollowSealsTheBladeAndHeals(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player seized = rooted(helper, 900.0F);
        SoulData seizedData = SoulLogic.get(seized);
        Player released = rooted(helper, 900.0F);
        SoulData releasedData = SoulLogic.get(released);
        Player gathering = rooted(helper, 900.0F);
        SoulData gatheringData = SoulLogic.get(gathering);
        PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 2, 2);
        GetsugaTensho loosed = null;
        try {
            // Seized first: Zangetsu does not answer, and no soul is sent on, though the blade still purifies by its nature.
            helper.assertTrue(VisoredEvents.startTakeover(seized, seizedData, false, now),
                    "the Hollow did not seize its host");
            ShikaiEvents.Result release = ShikaiEvents.release(seized);
            helper.assertTrue(release == ShikaiEvents.Result.SILENT, "a seized host's release gave " + release);
            helper.assertFalse(ZanpakutoItem.blade(seized.getMainHandItem()).released(),
                    "a seized host's blade was released");
            helper.assertFalse(ShinigamiEvents.konsoPlus(seized, plus), "a seized host sent a Plus on");
            helper.assertFalse(plus.isDeparting(), "the Plus sinks away from a seized host's Konsō");
            helper.assertTrue(ShinigamiLogic.canPerformKonso(seizedData, now) && HollowLogic.canPurify(seizedData, now),
                    "the takeover took away what purifies a Hollow cut down");

            // Released first: the blade stays released in the Hollow's hands, but gathers no Getsuga Tenshō.
            ShikaiEvents.Result first = ShikaiEvents.release(released);
            helper.assertTrue(first == ShikaiEvents.Result.RELEASED, "Zangetsu's release gave " + first);
            helper.assertTrue(VisoredEvents.startTakeover(released, releasedData, false, now),
                    "the Hollow did not seize a host with its blade released");
            ShikaiEvents.GatherResult gather = ShikaiEvents.gather(released);
            helper.assertTrue(gather == ShikaiEvents.GatherResult.SILENT && !releasedData.isGathering(),
                    "a seized host's gathering gave " + gather + ", gathering " + releasedData.isGathering());
            helper.assertTrue(ZanpakutoItem.blade(released.getMainHandItem()).released(),
                    "the takeover sealed a released blade");

            // Gathering: the Hollow lets the Getsuga Tenshō go, and there is nothing to loose.
            ShikaiEvents.Result again = ShikaiEvents.release(gathering);
            ShikaiEvents.GatherResult gathered = ShikaiEvents.gather(gathering);
            helper.assertTrue(again == ShikaiEvents.Result.RELEASED && gathered == ShikaiEvents.GatherResult.GATHERING,
                    "Zangetsu's release gave " + again + " and its gathering " + gathered);
            helper.assertTrue(VisoredEvents.startTakeover(gathering, gatheringData, false, now),
                    "the Hollow did not seize a host gathering a Getsuga Tenshō");
            helper.assertFalse(gatheringData.isGathering(), "a Getsuga Tenshō still gathers in a seized host's blade");
            loosed = ShikaiEvents.fire(gathering);
            helper.assertTrue(loosed == null, "a seized host loosed a Getsuga Tenshō");

            // Half a heart on each whole second from the start, the last one too, and then it lets go, wearied.
            seized.setHealth(4.0F);
            VisoredEvents.tick(seized, seizedData, now + 19L);
            HollowPlayerTests.assertClose(helper, seized.getHealth(), 4.0, "the seized host's health 19 ticks on");
            VisoredEvents.tick(seized, seizedData, now + 20L);
            HollowPlayerTests.assertClose(helper, seized.getHealth(), 5.0, "the seized host's health a second on");
            VisoredEvents.tick(seized, seizedData, now + 40L);
            HollowPlayerTests.assertClose(helper, seized.getHealth(), 6.0, "the seized host's health two seconds on");
            VisoredEvents.tick(seized, seizedData, now + 199L);
            helper.assertTrue(VisoredLogic.takenOver(seizedData), "the Hollow let go a tick short of 10 seconds");
            VisoredEvents.tick(seized, seizedData, now + 200L);
            HollowPlayerTests.assertClose(helper, seized.getHealth(), 7.0, "the host's health once the Hollow let go");
            assertFree(helper, seized, true, "a host whose Hollow's time ran out");
            MobEffectInstance slowed = seized.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(slowed != null && slowed.getAmplifier() == 0 && seized.hasEffect(MobEffects.WEAKNESS),
                    "the Hollow let go of its host with " + slowed + " and no Weakness");
            ShikaiEvents.Result freed = ShikaiEvents.release(seized);
            helper.assertTrue(freed == ShikaiEvents.Result.RELEASED, "the freed host's release gave " + freed);
        } finally {
            discard(plus, loosed);
            end(now, seized, released, gathering);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void theMaskKeyAndEveryExitEndIt(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player torn = rooted(helper, 900.0F);
        SoulData tornData = SoulLogic.get(torn);
        Player dying = rooted(helper, 900.0F);
        Player leaving = rooted(helper, 900.0F);
        Player stopped = rooted(helper, 900.0F);
        Player disabled = rooted(helper, 900.0F);
        Player lender = rooted(helper, 900.0F);
        Player fallen = rooted(helper, 900.0F);
        Player fighter = ready(helper, 900.0F, 0);
        SoulData fighterData = SoulLogic.get(fighter);
        Player wearer = masked(helper, 800.0F, 0L);
        SoulData wearerData = SoulLogic.get(wearer);
        Player outlasted = masked(helper, 800.0F, 0L);
        SoulData outlastedData = SoulLogic.get(outlasted);
        Player creative = inMode(helper, GameType.CREATIVE);
        Player spectator = inMode(helper, GameType.SPECTATOR);
        Player[] all = {torn, dying, leaving, stopped, disabled, lender, fallen, fighter, wearer, outlasted, creative,
                spectator};
        try {
            for (Player player : new Player[] {torn, dying, leaving, stopped, disabled, lender, fallen, fighter}) {
                helper.assertTrue(VisoredEvents.startTakeover(player, SoulLogic.get(player), false, now),
                        "the Hollow did not seize a rooted host");
            }
            helper.assertTrue(VisoredEvents.startTakeover(wearer, wearerData, true, now)
                    && VisoredEvents.startTakeover(outlasted, outlastedData, true, now),
                    "the Hollow did not seize a masked Visored");
            assertRefused(helper, creative, false, now, "a host in creative mode");
            assertRefused(helper, spectator, false, now, "a host in spectator mode");

            // The Hollow Mask key tears the half mask off, wearied, and calls no mask of its own.
            ShinigamiProgress.Visored record = VisoredLogic.record(tornData);
            VisoredEvents.onMaskKey(torn, tornData, now + 5L);
            assertFree(helper, torn, true, "a host that tore the half mask off");
            helper.assertFalse(VisoredLogic.isMasked(tornData), "tearing the half mask off called a mask");
            helper.assertTrue(VisoredLogic.record(tornData).equals(record),
                    "tearing the half mask off changed the record to " + VisoredLogic.record(tornData));

            // Behind the mask, the time runs out after 5 seconds and the mask stays on ...
            VisoredEvents.tick(outlasted, outlastedData, now + 99L);
            helper.assertTrue(VisoredLogic.takenOver(outlastedData), "the Hollow let go of a Visored a tick short of 5 s");
            VisoredEvents.tick(outlasted, outlastedData, now + 100L);
            assertFree(helper, outlasted, true, "a Visored whose Hollow's time ran out");
            helper.assertTrue(VisoredLogic.isMasked(outlastedData), "the mask came off when the Hollow's time ran out");
            // ... and the key tears both off: the mask waits as long as it was worn, and at least 3 seconds.
            VisoredEvents.onMaskKey(wearer, wearerData, now + 10L);
            assertFree(helper, wearer, true, "a Visored that tore its mask off");
            helper.assertFalse(VisoredLogic.isMasked(wearerData), "the Hollow Mask key left the Visored's mask on");
            long readyAt = VisoredLogic.record(wearerData).maskReadyAt();
            helper.assertTrue(readyAt == now + 70L,
                    "the torn-off mask may form again at " + readyAt + ", not " + (now + 70L));

            // Every other way out is not the takeover's own doing: silent, and no weariness.
            dying.getInventory().clearContent();
            dying.die(dying.damageSources().generic());
            assertFree(helper, dying, false, "a host that died");
            VisoredEvents.endAll(leaving, SoulLogic.get(leaving), now);
            assertFree(helper, leaving, false, "a host that logged out");
            VisoredEvents.endTakeovers(List.of(stopped));
            assertFree(helper, stopped, false, "a host at the server's stop");
            withConfig(BleachConfig.VISORED_ENABLED, false,
                    () -> VisoredEvents.tick(disabled, SoulLogic.get(disabled), now + 1L));
            assertFree(helper, disabled, false, "a host with the path turned off");
            SoulData lent = SoulLogic.get(lender);
            lent.setShinigami(lent.shinigami().withPowerLentUntil(now + 1_000L));
            VisoredEvents.tick(lender, lent, now + 1L);
            assertFree(helper, lender, false, "a host that lent its power");
            SoulData hollow = SoulLogic.get(fallen);
            hollow.setState(SoulState.HOLLOW);
            VisoredEvents.tick(fallen, hollow, now + 1L);
            assertFree(helper, fallen, false, "a host become a Hollow");

            // A fight with the Hollow beginning: it steps out, and lets go of the body. On Peaceful none begins.
            VisoredEvents.DuelStart start = VisoredEvents.beginDuel(fighter, fighterData, now, true);
            if (HollowPlayerTests.peaceful(helper)) {
                helper.assertTrue(start == VisoredEvents.DuelStart.PEACEFUL && !VisoredEvents.isDuelling(fighter),
                        "a forced fight on Peaceful gave " + start);
            } else {
                InnerHollow out = VisoredEvents.duelOf(fighter.getUUID());
                helper.assertTrue(start == VisoredEvents.DuelStart.STARTED && out != null,
                        "a forced fight for a seized host gave " + start);
                QuincyTests.still(out);
                assertFree(helper, fighter, false, "a host whose Hollow stepped out to fight");
            }
        } finally {
            end(now, all);
        }
        helper.succeed();
    }

    // ---- Behind the mask ----

    @GameTest(template = "field")
    public static void aBlackGetsugaNearDeathLosesControl(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player reckless = masked(helper, 800.0F, TRAINED);
        SoulData recklessData = SoulLogic.get(reckless);
        Player steady = masked(helper, 800.0F, 0L);
        Player bare = visored(helper, 800.0F, 0L);
        GetsugaTensho black = null;
        GetsugaTensho higher = null;
        GetsugaTensho pale = null;
        try {
            // A quarter of 20: the black Getsuga Tenshō lets the Hollow in behind the mask, which stays on.
            reckless.setHealth(5.0F);
            black = getsuga(helper, reckless);
            helper.assertTrue(black.isBlack(), "the masked Visored's Getsuga Tenshō is not the black one");
            helper.assertTrue(VisoredLogic.takenOver(recklessData), "a black Getsuga Tenshō at 5 of 20 kept control");
            HollowPlayerTests.assertModifier(helper, reckless, Attributes.ATTACK_DAMAGE, VisoredLogic.MASK_DAMAGE_ID, 0.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the mask's melee damage");
            HollowPlayerTests.assertModifier(helper, reckless, Attributes.ATTACK_DAMAGE, VisoredLogic.TAKEOVER_DAMAGE_ID,
                    0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the Hollow's melee damage behind the mask");
            helper.assertTrue(VisoredLogic.isMasked(recklessData), "the mask came off as the Hollow took the body");
            // The Hollow's own Cero stays its own; the Getsuga Tenshō does not answer.
            helper.assertTrue(VisoredLogic.maskedCero(recklessData), "the Hollow took the masked Cero away");
            ShikaiEvents.GatherResult second = ShikaiEvents.gather(reckless);
            helper.assertTrue(second == ShikaiEvents.GatherResult.SILENT, "a second Getsuga Tenshō behind the mask gave "
                    + second);

            // 5 seconds on, it lets go, wearied, under a mask 41 seconds long.
            VisoredEvents.tick(reckless, recklessData, now + 100L);
            assertFree(helper, reckless, true, "a Visored whose Hollow's time ran out");
            helper.assertTrue(VisoredLogic.isMasked(recklessData), "the mask came off when the Hollow let go");

            // Above a quarter, or with no mask on and so a pale crescent, it keeps control.
            steady.setHealth(6.0F);
            higher = getsuga(helper, steady);
            helper.assertTrue(higher.isBlack(), "the second masked Visored's Getsuga Tenshō is not the black one");
            helper.assertFalse(taken(steady), "a black Getsuga Tenshō at 6 of 20 lost control");
            bare.setHealth(5.0F);
            pale = getsuga(helper, bare);
            helper.assertFalse(pale.isBlack(), "an unmasked Visored's Getsuga Tenshō is the black one");
            helper.assertFalse(taken(bare), "a pale Getsuga Tenshō at 5 of 20 lost control");
        } finally {
            discard(black, higher, pale);
            end(now, reckless, steady, bare);
        }
        helper.succeed();
    }

    // ---- The fall ----

    @GameTest(template = "field")
    public static void aFightLostOnceTooOftenMakesAHollow(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player falling = ready(helper, 900.0F, 2);
        SoulData data = SoulLogic.get(falling);
        Player first = ready(helper, 900.0F, 0);
        SoulData firstData = SoulLogic.get(first);
        Player old = rooted(helper, 900.0F);
        SoulData oldData = SoulLogic.get(old);
        VisoredLogic.setRecord(oldData, VisoredLogic.record(oldData).withLosses(5));
        Player kept = ready(helper, 900.0F, 2);
        SoulData keptData = SoulLogic.get(kept);
        Player[] all = {falling, first, old, kept};
        if (HollowPlayerTests.peaceful(helper)) {
            try {
                VisoredEvents.DuelStart start = VisoredEvents.beginDuel(falling, data, now, false);
                helper.assertTrue(start == VisoredEvents.DuelStart.PEACEFUL && !VisoredEvents.isDuelling(falling),
                        "a Hollow came out on Peaceful: " + start);
            } finally {
                end(now, all);
            }
            helper.succeed();
            return;
        }
        try {
            withConfig(BleachConfig.VISORED_FALL_INTO_HOLLOW, true, () -> {
                withConfig(BleachConfig.VISORED_TAKEOVER_CHANCE, 1.0, () -> {
                    // The third loss: one heart, the fight over and the loss counted, and, for that tick, still a Shinigami.
                    // Its own Hollow's blow never lets it in, even at a chance of 1.
                    InnerHollow hollow = lose(helper, falling, now);
                    HollowPlayerTests.assertClose(helper, falling.getHealth(), 2.0,
                            "the host's health after the losing blow");
                    helper.assertFalse(VisoredEvents.isDuelling(falling), "the fight went on after the losing blow");
                    helper.assertTrue(hollow.isRemoved(), "the Hollow that won is still there");
                    helper.assertTrue(VisoredLogic.record(data).losses() == 3,
                            "the third loss left " + VisoredLogic.record(data));
                    helper.assertFalse(VisoredLogic.takenOver(data), "the inner Hollow's losing blow let it seize the body");
                    helper.assertTrue(data.state() == SoulState.SHINIGAMI, "the host fell within the losing blow");

                    // At its next tick it falls: a plain Hollow, as strong as before and a little more, its Hollow gone.
                    VisoredEvents.tick(falling, data, now + 1L);
                    helper.assertTrue(data.state() == SoulState.HOLLOW && HollowPlayerLogic.isHollow(data),
                            "the third loss left a " + data.state());
                    helper.assertFalse(ShinigamiLogic.hasShinigamiPower(data), "the fallen soul kept its Shinigami power");
                    helper.assertTrue(data.tier(PowerPath.HOLLOW) == 1 && data.tier(PowerPath.SHINIGAMI) >= 1,
                            "the fallen soul's tiers are " + data.tier(PowerPath.HOLLOW) + " as a Hollow and "
                                    + data.tier(PowerPath.SHINIGAMI) + " as a Shinigami");
                    helper.assertTrue(VisoredLogic.record(data).equals(ShinigamiProgress.Visored.NONE),
                            "the fallen soul kept the Hollow's record " + VisoredLogic.record(data));
                    helper.assertTrue(data.zanpakuto().named(), "the fallen soul forgot its zanpakutō's name");
                    HollowPlayerTests.assertClose(helper, data.maxReiryoku(),
                            900.0 + BleachConfig.get(BleachConfig.HOLLOWFICATION_GROWTH),
                            "the fallen soul's maximum reiryoku");
                    HollowPlayerTests.assertClose(helper, data.reiryoku(), data.maxReiryoku(), "the fallen soul's reiryoku");
                    helper.assertFalse(VisoredLogic.takenOver(data), "the Hollow holds the fallen soul's body");

                    // A first loss only puts the next fight off, and losses from before the switch went on make no Hollow.
                    lose(helper, first, now);
                    VisoredEvents.tick(first, firstData, now + 1L);
                    helper.assertTrue(firstData.state() == SoulState.SHINIGAMI
                            && VisoredLogic.record(firstData).losses() == 1,
                            "a first loss left a " + firstData.state() + " with " + VisoredLogic.record(firstData));
                    VisoredEvents.tick(old, oldData, now + 1L);
                    helper.assertTrue(oldData.state() == SoulState.SHINIGAMI, "five losses from before made a Hollow");
                });
            });

            // With the fall off, as by default, the third loss is a loss like any other.
            lose(helper, kept, now);
            VisoredEvents.tick(kept, keptData, now + 1L);
            helper.assertTrue(keptData.state() == SoulState.SHINIGAMI && VisoredLogic.record(keptData).losses() == 3,
                    "a third loss with the fall off left a " + keptData.state() + " with " + VisoredLogic.record(keptData));
        } finally {
            end(now + 1L, all);
        }
        helper.succeed();
    }
}
