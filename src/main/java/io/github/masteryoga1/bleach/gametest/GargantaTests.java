package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.network.GateKeyPayload;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.RukongaiEvents;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.soul.TravelRecord;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Garganta a Menos or an Arrancar tears open with the Gate key: who may open one, what it costs, the tear,
 * where it sets a soul down, and a real Arrancar's trip there and back.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GargantaTests {
    /** Where a test's soul stands, facing south, so its tear stands inside the structure 1.8 blocks on. */
    private static final Vec3 SPOT = new Vec3(1.5, 1.0, 0.5);
    /** How near a spot worked out from the same numbers must be. */
    private static final double SAME = 1.0E-6;

    private GargantaTests() {
    }

    // ---- Shared helpers ----

    /** A Hollow of this form, full of this much reiryoku. */
    static SoulData menos(HollowTier form, float max) {
        SoulData data = ShinigamiTests.soul(SoulState.HOLLOW, max);
        data.setTier(PowerPath.HOLLOW, form.tier());
        return data;
    }

    /** An Arrancar full of 800 reiryoku, whom a Garganta costs 40. */
    static SoulData arrancar() {
        return ShinigamiTests.soul(SoulState.ARRANCAR, 800.0F);
    }

    /** A mock player with this soul at {@link #SPOT}, facing south. */
    private static Player mock(GameTestHelper helper, GameType mode, SoulData data) {
        Player player = helper.makeMockPlayer(mode);
        player.setData(SoulAttachments.SOUL, data);
        Vec3 at = helper.absoluteVec(SPOT);
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** Asserts the answer to opening a Garganta: null for a tear, or the refusal. */
    static void refuses(GameTestHelper helper, @Nullable TravelLogic.Refusal actual, @Nullable TravelLogic.Refusal expected,
                        String what) {
        helper.assertTrue(actual == expected, what + " gave " + actual + ", not " + expected);
    }

    /** The rules' answer to this player, with this soul, opening a Garganta in this world now. */
    @Nullable
    private static TravelLogic.Refusal ask(Player player, SoulData data, ResourceKey<Level> world, long now) {
        return TravelLogic.mayOpenGarganta(player, data, world, now);
    }

    /** The passages standing in this test's structure. */
    private static List<GargantaRift> passages(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(GargantaRift.class, helper.getBounds(), GargantaRift::isPassage);
    }

    /** Asserts that a spot worked out is the one expected, within {@link #SAME}. */
    private static void at(GameTestHelper helper, @Nullable Vec3 actual, Vec3 expected, String what) {
        helper.assertTrue(actual != null && actual.distanceTo(expected) < SAME, what + " is " + actual + ", not " + expected);
    }

    // ---- Who may open a Garganta ----

    /**
     * Each rule for opening a Garganta, each tried on a soul that fails it alone: the dead, the held, the powerless, the
     * bound, the wrong world, the wait and the cost, then creative's pass and, last, a gate already in reach. Then the
     * tear one opens, its size by its opener's height, where a Garganta leads from each world, sneaking or not, and which
     * gate the key opens for each soul. It runs in a batch of its own, so no other test's gate stands within the reach it
     * checks.
     */
    @GameTest(template = "empty", batch = "garganta_open")
    public static void onlyAMenosOrAnArrancarOpensAGarganta(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        ResourceKey<Level> human = Level.OVERWORLD;
        ResourceKey<Level> hueco = BleachWorlds.HUECO_MUNDO;
        Player anyone = mock(helper, GameType.SURVIVAL, menos(HollowTier.ADJUCHAS, 800.0F));
        Player held = mock(helper, GameType.SURVIVAL, arrancar());
        NegacionLight light = NegacionLight.place(level, held, null);
        GargantaRift rift = null;
        GargantaRift under = null;
        GargantaRift up = null;
        Senkaimon gate = null;
        try {
            for (HollowTier form : List.of(HollowTier.GILLIAN, HollowTier.ADJUCHAS, HollowTier.VASTO_LORDE)) {
                refuses(helper, ask(anyone, menos(form, 800.0F), human, now), null, form.getSerializedName());
            }
            refuses(helper, ask(anyone, arrancar(), human, now), null, "an Arrancar in the Human World");
            refuses(helper, ask(anyone, menos(HollowTier.ADJUCHAS, 800.0F), BleachWorlds.SOUL_SOCIETY, now), null,
                    "an Adjuchas in Soul Society");
            refuses(helper, ask(anyone, menos(HollowTier.ADJUCHAS, 800.0F), hueco, now), null, "an Adjuchas in Hueco Mundo");

            Player dead = mock(helper, GameType.SURVIVAL, arrancar());
            dead.setHealth(0.0F);
            refuses(helper, ask(dead, SoulLogic.get(dead), human, now), TravelLogic.Refusal.UNABLE, "a dead Arrancar");
            Player spectator = mock(helper, GameType.SPECTATOR, arrancar());
            refuses(helper, ask(spectator, SoulLogic.get(spectator), human, now), TravelLogic.Refusal.UNABLE,
                    "a spectating Arrancar");
            helper.assertTrue(light != null && held.startRiding(light, true) && Negacion.held(held),
                    "the mock Arrancar could not be held");
            refuses(helper, ask(held, SoulLogic.get(held), human, now), TravelLogic.Refusal.HELD,
                    "an Arrancar held by Negación");
            for (SoulData powerless : List.of(ShinigamiTests.soul(SoulState.HOLLOW, 800.0F),
                    ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F), ShinigamiTests.soul(SoulState.HUMAN, 400.0F),
                    ShinigamiTests.soul(SoulState.PLUS, 40.0F))) {
                refuses(helper, ask(anyone, powerless, human, now), TravelLogic.Refusal.NO_POWER,
                        powerless.state().getSerializedName());
            }
            Player bound = mock(helper, GameType.SURVIVAL, arrancar());
            bound.addEffect(new MobEffectInstance(ModEffects.BAKUDO_BOUND, 100));
            refuses(helper, ask(bound, SoulLogic.get(bound), human, now), TravelLogic.Refusal.BOUND,
                    "an Arrancar bound by a Bakudō");
            for (ResourceKey<Level> world : List.of(Level.NETHER, Level.END, BleachWorlds.DANGAI)) {
                refuses(helper, ask(anyone, arrancar(), world, now), TravelLogic.Refusal.WRONG_WORLD,
                        "an Arrancar in " + world.location());
            }

            SoulData waiting = arrancar();
            TravelLogic.startGargantaCooldown(waiting, now);
            refuses(helper, ask(anyone, waiting, human, now + 1L), TravelLogic.Refusal.COOLDOWN, "a tear a tick after one");
            long seconds = TravelLogic.gargantaCooldownSeconds(waiting, now + 1L);
            long last = TravelLogic.gargantaCooldownSeconds(waiting, now + 581L);
            long senkaimon = TravelLogic.cooldownSeconds(waiting, now + 1L);
            helper.assertTrue(seconds == 30L && last == 1L && senkaimon == 0L, "a tick into the wait " + seconds
                    + " seconds are left, not 30; 19 ticks from its end " + last + ", not 1; the Senkaimon's wait is "
                    + senkaimon + " seconds, not 0");
            refuses(helper, ask(anyone, waiting, human, now + 600L), null, "a tear once the wait has run");
            float cost = TravelLogic.gargantaCost(arrancar());
            float least = TravelLogic.gargantaCost(ShinigamiTests.soul(SoulState.ARRANCAR, 60.0F));
            helper.assertTrue(cost == 40.0F && least == 5.0F, "a Garganta costs an Arrancar of 800 " + cost
                    + ", not 40, and one of 60 " + least + ", not the least cost of 5");
            SoulData weak = arrancar();
            // 40.4 less the cost of 40 leaves under the half a point every soul keeps.
            weak.setReiryoku(40.4F);
            refuses(helper, ask(anyone, weak, human, now), TravelLogic.Refusal.TOO_WEAK, "an Arrancar with 40.4 reiryoku");
            weak.setReiryoku(40.5F);
            refuses(helper, ask(anyone, weak, human, now), null, "an Arrancar with 40.5 reiryoku");
            Player creative = mock(helper, GameType.CREATIVE, arrancar());
            SoulData spent = SoulLogic.get(creative);
            spent.setReiryoku(0.0F);
            TravelLogic.startGargantaCooldown(spent, now);
            refuses(helper, ask(creative, spent, human, now), null, "a creative Arrancar, spent and waiting");
            long hold = TravelLogic.turnTicks(TravelLogic.Gate.GARGANTA);
            long turn = TravelLogic.turnTicks(TravelLogic.Gate.SENKAIMON);
            helper.assertTrue(hold == 20L && turn == 30L && TravelLogic.gargantaOpenTicks() == 400, "the key is held "
                    + hold + " ticks for a Garganta and " + turn + " for a Senkaimon, and a Garganta stays "
                    + TravelLogic.gargantaOpenTicks());

            rift = TravelEvents.openPassage(level, anyone, SoulLogic.get(anyone), now);
            helper.assertTrue(rift != null, "no Garganta opened for the Adjuchas");
            helper.assertTrue(rift.isPassage() && rift.leadsTo() == hueco && anyone.getUUID().equals(rift.opener())
                            && rift.size() == 1.0F && rift.isQuiet(), "the Garganta leads to " + rift.leadsTo()
                            + ", was torn by " + rift.opener() + ", is size " + rift.size() + ", quiet " + rift.isQuiet());
            helper.assertTrue(rift.hold() == 320 && rift.closeAge() == 380 && rift.endAge() == 400, "the Garganta holds "
                    + rift.hold() + " ticks, closes at " + rift.closeAge() + " and is gone at " + rift.endAge());
            Vec3 ahead = helper.absoluteVec(SPOT.add(0.0, 0.0, 1.8));
            helper.assertTrue(rift.position().distanceTo(ahead) < SAME, "the Garganta stands at " + rift.position()
                    + ", not 1.8 blocks before the Adjuchas at " + ahead);
            Player second = mock(helper, GameType.SURVIVAL, arrancar());
            refuses(helper, ask(second, SoulLogic.get(second), human, now), TravelLogic.Refusal.CROWDED,
                    "a second Garganta where one stands open");
            rift.discard();
            refuses(helper, ask(second, SoulLogic.get(second), human, now), null, "a Garganta once the first is gone");
            gate = Senkaimon.open(level, mock(helper, GameType.SURVIVAL, ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F)),
                    now);
            helper.assertTrue(gate != null, "no Senkaimon opened for the crowding row");
            refuses(helper, ask(second, SoulLogic.get(second), human, now), TravelLogic.Refusal.CROWDED,
                    "a Garganta before an open Senkaimon");

            float gillian = TravelLogic.passageSize(10.8);
            float adjuchas = TravelLogic.passageSize(2.7);
            float person = TravelLogic.passageSize(1.8);
            float ungrown = TravelLogic.passageSize(anyone, menos(HollowTier.GILLIAN, 800.0F));
            helper.assertTrue(gillian == 3.0F && adjuchas == 1.0F && person == 1.0F && ungrown == 3.0F, "a Garganta is "
                    + gillian + " for a Gillian's 10.8 blocks, " + adjuchas + " for an Adjuchas's 2.7, " + person
                    + " for a person's 1.8 and " + ungrown + " for a Gillian not yet grown");

            ResourceKey<Level> society = BleachWorlds.SOUL_SOCIETY;
            helper.assertTrue(TravelLogic.gargantaLeadsTo(human, false) == hueco
                            && TravelLogic.gargantaLeadsTo(human, true) == hueco
                            && TravelLogic.gargantaLeadsTo(society, false) == hueco
                            && TravelLogic.gargantaLeadsTo(society, true) == hueco
                            && TravelLogic.gargantaLeadsTo(hueco, false) == human
                            && TravelLogic.gargantaLeadsTo(hueco, true) == society,
                    "a Garganta does not lead to Hueco Mundo from the Human World and Soul Society, and from there home,"
                            + " or to Soul Society torn sneaking");
            for (ResourceKey<Level> world : List.of(Level.NETHER, Level.END, BleachWorlds.DANGAI)) {
                helper.assertTrue(TravelLogic.gargantaLeadsTo(world, false) == null
                        && TravelLogic.gargantaLeadsTo(world, true) == null, "a Garganta leads out of " + world.location());
            }
            // Torn in Hueco Mundo, at the mock's own x and z: no chunk need be loaded for a tear never ticked.
            ServerLevel huecoMundo = level.getServer().getLevel(hueco);
            helper.assertTrue(huecoMundo != null, "the test server has no Hueco Mundo");
            Player sneaker = mock(helper, GameType.SURVIVAL, arrancar());
            sneaker.setShiftKeyDown(true);
            under = TravelEvents.openPassage(huecoMundo, sneaker, SoulLogic.get(sneaker), now);
            sneaker.setShiftKeyDown(false);
            up = TravelEvents.openPassage(huecoMundo, sneaker, SoulLogic.get(sneaker), now);
            helper.assertTrue(under != null && under.leadsTo() == society && up != null && up.leadsTo() == human,
                    "a Garganta torn in Hueco Mundo leads to " + (under == null ? null : under.leadsTo())
                            + " sneaking and " + (up == null ? null : up.leadsTo()) + " standing");

            TravelLogic.Gate senkaimonGate = TravelLogic.Gate.SENKAIMON;
            TravelLogic.Gate garganta = TravelLogic.Gate.GARGANTA;
            helper.assertTrue(TravelLogic.gateFor(ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F)) == senkaimonGate
                            && TravelLogic.gateFor(ShinigamiTests.soul(SoulState.HUMAN, 400.0F)) == senkaimonGate
                            && TravelLogic.gateFor(ShinigamiTests.soul(SoulState.HOLLOW, 800.0F)) == garganta
                            && TravelLogic.gateFor(menos(HollowTier.GILLIAN, 800.0F)) == garganta
                            && TravelLogic.gateFor(arrancar()) == garganta,
                    "the Gate key does not open a Senkaimon for a Shinigami and a Human and a Garganta for every Hollow");
            Player basic = mock(helper, GameType.SURVIVAL, ShinigamiTests.soul(SoulState.HOLLOW, 800.0F));
            refuses(helper, TravelEvents.onKey(basic, GateKeyPayload.GateKey.PRESS, now), TravelLogic.Refusal.NO_POWER,
                    "a basic Hollow's key");
            helper.assertTrue("message.bleach.garganta.no_power".equals(
                            TravelLogic.Refusal.NO_POWER.messageKey(TravelLogic.Gate.GARGANTA))
                            && "message.bleach.senkaimon.no_power".equals(TravelLogic.Refusal.NO_POWER.messageKey())
                            && TravelLogic.Refusal.LENT.messageKey(TravelLogic.Gate.GARGANTA) == null,
                    "a basic Hollow is not told the Garganta's own line, or a Shinigami's lent power is a Garganta's");
        } finally {
            held.stopRiding();
            TravelTests.discard(light, rift, under, up, gate);
        }
        helper.succeed();
    }

    // ---- Where a Garganta sets a soul down ----

    /**
     * Where a Garganta sets a soul down: pushed out past Las Noches in Hueco Mundo, on the Rukongai's first district in
     * Soul Society, and in the Human World at the block the soul last left from, else on its own column. Then which
     * bodies fit which tear. It loads four chunks of the other worlds and holds none.
     */
    @GameTest(template = "empty", skyAccess = true, timeoutTicks = 100)
    public static void aGargantaLandsWhereItShould(GameTestHelper helper) {
        at(helper, TravelLogic.outsideLasNoches(0.0, 0.0), new Vec3(0.0, 0.0, 136.0), "the centre pushed out");
        at(helper, TravelLogic.outsideLasNoches(10.0, 0.0), new Vec3(136.0, 0.0, 0.0), "(10, 0) pushed out");
        at(helper, TravelLogic.outsideLasNoches(30.0, 40.0), new Vec3(81.6, 0.0, 108.8), "(30, 40) pushed out");
        at(helper, TravelLogic.outsideLasNoches(0.3, 0.4), new Vec3(81.6, 0.0, 108.8), "(0.3, 0.4) pushed out");
        at(helper, TravelLogic.outsideLasNoches(-6.0, 8.0), new Vec3(-81.6, 0.0, 108.8), "(-6, 8) pushed out");
        at(helper, TravelLogic.outsideLasNoches(200.0, 0.0), new Vec3(200.0, 0.0, 0.0), "(200, 0), already outside");
        at(helper, TravelLogic.outsideLasNoches(136.0, 0.0), new Vec3(136.0, 0.0, 0.0), "(136, 0), on the radius");
        at(helper, TravelLogic.rukongaiOnBearing(0.0, 0.0), new Vec3(0.0, 0.0, 544.0), "the centre's Rukongai");
        at(helper, TravelLogic.rukongaiOnBearing(3.0, 4.0), new Vec3(326.4, 0.0, 435.2), "(3, 4)'s Rukongai");
        at(helper, TravelLogic.rukongaiOnBearing(-600.0, 0.0), new Vec3(-544.0, 0.0, 0.0), "(-600, 0)'s Rukongai");

        ServerLevel level = helper.getLevel();
        ServerLevel hueco = level.getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        ServerLevel society = level.getServer().getLevel(BleachWorlds.SOUL_SOCIETY);
        helper.assertTrue(hueco != null && society != null, "the test server lacks Hueco Mundo or Soul Society");
        Player soul = mock(helper, GameType.SURVIVAL, arrancar());
        Player giant = mock(helper, GameType.SURVIVAL, arrancar());
        giant.getAttribute(Attributes.SCALE).setBaseValue(6.0);
        giant.refreshDimensions();
        helper.assertTrue(GargantaRift.fits(soul, 1.0F) && !GargantaRift.fits(giant, 1.0F) && GargantaRift.fits(giant, 3.0F),
                "a person fits a small tear " + GargantaRift.fits(soul, 1.0F) + "; a body " + giant.getBbHeight()
                        + " tall fits it " + GargantaRift.fits(giant, 1.0F) + " and a Gillian's "
                        + GargantaRift.fits(giant, 3.0F));
        // Every column within r 200 of Hueco Mundo's centre stands at y 96, open to the sky.
        Vec3 levelGround = new Vec3(0.5, 96.0, 136.5);
        at(helper, TravelEvents.huecoMundoLanding(soul, hueco, 0.0, 0.0), levelGround, "a soul from the centre lands");
        at(helper, TravelEvents.huecoMundoLanding(giant, hueco, 0.0, 0.0), levelGround, "a Gillian's body lands");
        Vec3 rukongai = TravelEvents.rukongaiLanding(soul, society, 0.0, 0.0);
        helper.assertTrue(rukongai != null && Math.abs(rukongai.x - 0.5) < SAME && Math.abs(rukongai.z - 544.5) < SAME
                        && Math.abs(rukongai.y - SeireiteiPlan.PLATEAU_Y) <= 8.0 && Rukongai.district(0, 544) == 1
                        && !society.getBlockState(BlockPos.containing(rukongai).below()).isAir(),
                "a soul from the centre comes out in Soul Society at " + rukongai + ", in district "
                        + Rukongai.district(0, 544));

        // The block left from, over stone and inside the structure, whose barrier wall stands at z 3.
        BlockPos left = helper.absolutePos(new BlockPos(1, 1, 2));
        helper.setBlock(1, 0, 2, Blocks.STONE);
        soul.setData(SoulAttachments.TRAVEL, TravelRecord.NONE.withHumanWorldReturn(GlobalPos.of(Level.OVERWORLD, left)));
        at(helper, TravelEvents.gargantaHomeward(soul, level), Vec3.atBottomCenterOf(left), "the way home with a record");
        at(helper, TravelEvents.gargantaLanding(soul, level, Level.OVERWORLD), Vec3.atBottomCenterOf(left),
                "the way home by the landing");
        Vec3 own = RukongaiEvents.groundAt(soul, level, soul.getBlockX(), soul.getBlockZ());
        helper.assertTrue(own != null, "the soul's own column holds nothing");
        soul.setData(SoulAttachments.TRAVEL, TravelRecord.NONE.withHumanWorldReturn(GlobalPos.of(Level.NETHER, left)));
        at(helper, TravelEvents.gargantaHomeward(soul, level), own, "the way home with a record in the Nether");
        soul.setData(SoulAttachments.TRAVEL, TravelRecord.NONE);
        at(helper, TravelEvents.gargantaHomeward(soul, level), own, "the way home with no record");
        Vec3 far = TravelEvents.huecoMundoLanding(soul, hueco, soul.getX(), soul.getZ());
        helper.assertTrue(far != null, "Hueco Mundo holds nothing on the soul's own bearing");
        at(helper, TravelEvents.gargantaLanding(soul, hueco, BleachWorlds.HUECO_MUNDO), far, "the way into Hueco Mundo");
        helper.assertTrue(Rukongai.district(326, 435) == 1, "(3, 4)'s Rukongai is in district "
                + Rukongai.district(326, 435));
        // Torn sneaking at Hueco Mundo's centre, the way leads to the Rukongai column the centre's soul came out on.
        soul.moveTo(0.0, soul.getY(), 0.0);
        at(helper, TravelEvents.gargantaLanding(soul, society, BleachWorlds.SOUL_SOCIETY), rukongai,
                "the way into Soul Society from the centre");
        helper.succeed();
    }

    /**
     * A giant set down by a Garganta stands on the highest ground its wide body covers, so a step beside its column
     * does not turn it away, while a person stands on its own column. It runs in the field, open to the sky, so no
     * barrier roof counts as ground.
     */
    @GameTest(template = "field", skyAccess = true)
    public static void aGiantLandsOnTheHighestGroundUnderIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A step a block high one column east of the middle: under a Gillian's 3.6 blocks, beside a person's 0.6.
        helper.setBlock(9, 2, 8, Blocks.STONE);
        Player soul = mock(helper, GameType.SURVIVAL, arrancar());
        Player giant = mock(helper, GameType.SURVIVAL, arrancar());
        giant.getAttribute(Attributes.SCALE).setBaseValue(6.0);
        giant.refreshDimensions();
        BlockPos middle = helper.absolutePos(new BlockPos(8, 2, 8));
        Vec3 floor = Vec3.atBottomCenterOf(middle);
        at(helper, TravelEvents.groundUnder(soul, level, middle.getX(), middle.getZ()), floor, "a person beside the step");
        at(helper, TravelEvents.groundUnder(giant, level, middle.getX(), middle.getZ()), floor.add(0.0, 1.0, 0.0),
                "a Gillian over the step");
        helper.succeed();
    }

    // ---- A real trip ----

    /**
     * A real Arrancar holds the Gate key a second and a Garganta opens 1.8 blocks south; walked into, it passes on its
     * 11th tick inside into the real Hueco Mundo, on the ground outside Las Noches, remembering the block it left. A
     * Garganta torn there, once its chunk ticks, opens round it and leaves it be until it steps out and in again, then
     * takes it home to that block, where the first still stands open and only renews its wait. It runs in a batch of its
     * own, so no other test's tear, Hollow or player ticks beside its trip.
     */
    @GameTest(template = "empty", batch = "garganta_trip", timeoutTicks = 400)
    public static void anArrancarTearsAGargantaThereAndBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel hueco = level.getServer().getLevel(BleachWorlds.HUECO_MUNDO);
        helper.assertTrue(hueco != null, "the test server has no Hueco Mundo");
        // The floor under the tear, which the way home lands on.
        helper.setBlock(1, 0, 2, Blocks.STONE);
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, SPOT);
        player.setData(SoulAttachments.SOUL, arrancar());
        Cleanup cleanup = new Cleanup(hueco, new ArrayList<>(), new ChunkPos[1]);
        helper.testInfo.addListener(cleanup);
        // The far side's chunks within 2 of the held one, made a row a tick from tick 26, so the tear home there ticks
        // at once: the test server never waits between ticks, far outrunning chunks made in the background.
        for (int row = -2; row <= 2; row++) {
            int dz = row;
            helper.runAtTickTime(28 + dz, () -> cleanup.load(dz));
        }
        BlockPos[] inside = new BlockPos[1];
        Vec3[] riftAt = new Vec3[1];
        refuses(helper, TravelEvents.onKey(player, GateKeyPayload.GateKey.PRESS, HollowPlayerTests.now(helper)), null,
                "the Arrancar's turn");

        // The turn ends on tick 20, when the player's tick opens the tear; the tear first ticks on 21.
        helper.runAtTickTime(15, () -> helper.assertTrue(passages(helper).isEmpty(),
                "a Garganta opened before the key had been held a second"));
        helper.runAtTickTime(25, () -> {
            List<GargantaRift> open = passages(helper);
            cleanup.spawned().addAll(open);
            helper.assertTrue(open.size() == 1, "the held key tore " + open.size() + " Garganta; the action bar said "
                    + TravelTests.said(player));
            GargantaRift rift = open.get(0);
            Vec3 ahead = player.position().add(0.0, 0.0, 1.8);
            helper.assertTrue(rift.position().distanceTo(ahead) < SAME && rift.size() == 1.0F
                            && rift.leadsTo() == BleachWorlds.HUECO_MUNDO && player.getUUID().equals(rift.opener()),
                    "the Garganta stands at " + rift.position() + ", size " + rift.size() + ", to " + rift.leadsTo());
            long seconds = TravelLogic.gargantaCooldownSeconds(SoulLogic.get(player), HollowPlayerTests.now(helper));
            // Paid 40 of 800 on tick 20; five ticks of regaining since give back at most 8.
            float left = SoulLogic.get(player).reiryoku();
            helper.assertTrue(seconds == 30L && left <= 770.0F, "after the tear the wait is " + seconds
                    + " s, not 30, and " + left + " reiryoku is left, not about 760");
            // The far side's chunk, held from now so that it ticks by the time the tear home opens there.
            riftAt[0] = rift.position();
            Vec3 far = TravelLogic.outsideLasNoches(rift.getX(), rift.getZ());
            cleanup.hold(new ChunkPos(Mth.floor(far.x) >> 4, Mth.floor(far.z) >> 4));
            player.teleportTo(level, rift.getX(), rift.getY(), rift.getZ(), 0.0F, 0.0F);
            inside[0] = player.blockPosition();
        });
        // Inside from tick 26, it passes on its 11th tick there, 36.
        helper.runAtTickTime(33, () -> helper.assertTrue(player.level() == level,
                "the Arrancar passed after 8 ticks in the Garganta, to " + TravelTests.where(player)));
        helper.runAtTickTime(50, () -> {
            // The soul passed standing where the tear stands, so it comes out on that column, or the one it is pushed to.
            Vec3 far = TravelLogic.outsideLasNoches(riftAt[0].x, riftAt[0].z);
            Vec3 ground = RukongaiEvents.groundAt(player, hueco, Mth.floor(far.x), Mth.floor(far.z));
            helper.assertTrue(player.level() == hueco && ground != null && player.position().distanceTo(ground) < SAME,
                    "the Arrancar came out at " + TravelTests.where(player) + ", not on the ground at " + ground
                            + "; the action bar said " + TravelTests.said(player));
            helper.assertTrue(!player.isChangingDimension() && player.getPortalCooldown() == 0,
                    "the arrival is still changing world (" + player.isChangingDimension() + ") or waiting "
                            + player.getPortalCooldown() + " ticks");
            Optional<GlobalPos> left = player.getData(SoulAttachments.TRAVEL).humanWorldReturn();
            helper.assertTrue(left.equals(Optional.of(GlobalPos.of(Level.OVERWORLD, inside[0]))),
                    "the record of where it left is " + left + ", not " + inside[0]);
            AdvancementHolder into = level.getServer().getAdvancements().get(BleachMod.id("hueco_mundo"));
            helper.assertTrue(into != null && player.getAdvancements().getOrStartProgress(into).isDone(),
                    "the Arrancar was not given Into Hueco Mundo");
            // The tear home, torn from the middle of the held chunk so that it stands in that chunk.
            ChunkPos held = cleanup.held()[0];
            Vec3 middle = RukongaiEvents.groundAt(player, hueco, held.getMiddleBlockX(), held.getMiddleBlockZ());
            helper.assertTrue(middle != null, "nowhere in the held chunk holds the Arrancar");
            player.teleportTo(hueco, middle.x, middle.y, middle.z, 0.0F, 0.0F);
            BlockPos home = BlockPos.containing(GargantaRift.passageStanding(player, 1.0F));
            GargantaRift[] tearHome = new GargantaRift[1];
            helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(hueco.isPositionEntityTicking(home),
                            "the chunk the tear home would stand in never ticked"))
                    .thenExecute(() -> {
                        GargantaRift back = TravelEvents.openPassage(hueco, player, SoulLogic.get(player),
                                hueco.getGameTime());
                        // Listed before it is checked, so a tear leading the wrong way goes too.
                        cleanup.spawned().add(back);
                        helper.assertTrue(back != null && back.leadsTo() == Level.OVERWORLD, "the Garganta home leads to "
                                + (back == null ? "nowhere" : back.leadsTo()));
                        tearHome[0] = back;
                        // In it before its first tick, as if it had opened round the Arrancar.
                        player.teleportTo(hueco, back.getX(), back.getY(), back.getZ(), 0.0F, 0.0F);
                    })
                    // Past the tick it would have passed on had it stepped in, it stands there still.
                    .thenExecuteAfter(15, () -> {
                        helper.assertTrue(player.level() == hueco, "the Garganta home took the Arrancar it opened round, to "
                                + TravelTests.where(player));
                        player.teleportTo(hueco, middle.x, middle.y, middle.z, 0.0F, 0.0F);
                    })
                    // Out of it for a tick, then back in: the tear home counts it from the next tick, and it passes ten
                    // after that.
                    .thenExecuteAfter(1, () -> player.teleportTo(hueco, tearHome[0].getX(), tearHome[0].getY(),
                            tearHome[0].getZ(), 0.0F, 0.0F))
                    .thenExecuteAfter(7, () -> helper.assertTrue(player.level() == hueco,
                            "the Arrancar passed home after 7 ticks in the Garganta, to " + TravelTests.where(player)))
                    .thenExecuteAfter(18, () -> {
                        Vec3 back = Vec3.atBottomCenterOf(inside[0]);
                        helper.assertTrue(player.level() == level && player.position().distanceTo(back) < SAME,
                                "the Garganta home brought the Arrancar to " + TravelTests.where(player)
                                        + ", not where it left, " + back + "; the action bar said "
                                        + TravelTests.said(player));
                    })
                    // The first tear stands open round it, and the wait it re-arms each tick keeps it from passing.
                    .thenExecuteAfter(25, () -> helper.assertTrue(player.level() == level,
                            "the first Garganta took the Arrancar again, to " + TravelTests.where(player)))
                    .thenSucceed();
        });
    }

    /**
     * Lets go, as a test ends, passed or failed, of the chunk it held in Hueco Mundo and of everything it spawned, so a
     * failure or a timeout leaves nothing behind.
     */
    private record Cleanup(ServerLevel huecoMundo, List<Entity> spawned, ChunkPos[] held) implements GameTestListener {
        /** Holds this chunk of Hueco Mundo, and the two rings round it, until the test ends. */
        void hold(ChunkPos chunk) {
            held[0] = chunk;
            huecoMundo.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk);
        }

        /**
         * Makes the five chunks this many rows from the held one, south positive, as a chunk's entities tick only once
         * every chunk within 2 of it is whole. Nothing is made before a chunk is held.
         */
        void load(int row) {
            if (held[0] == null) {
                return;
            }
            for (int x = -2; x <= 2; x++) {
                huecoMundo.getChunk(held[0].x + x, held[0].z + row);
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {
        }

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            release();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            release();
        }

        @Override
        public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
        }

        private void release() {
            TravelTests.discard(spawned.toArray(new Entity[0]));
            if (held[0] != null) {
                huecoMundo.getChunkSource().removeRegionTicket(TicketType.FORCED, held[0], 2, held[0]);
                held[0] = null;
            }
        }
    }
}
