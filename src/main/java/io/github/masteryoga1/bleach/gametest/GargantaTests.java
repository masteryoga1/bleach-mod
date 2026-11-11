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
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Garganta a Menos or an Arrancar tears open with the Gate key: who may open one, what it costs, and the
 * tear.
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

    // ---- Who may open a Garganta ----

    /**
     * Each rule for opening a Garganta, in its order: the dead, the held, the powerless, the bound, the wrong world, the
     * wait and the cost, then creative's pass and, last, a gate already in reach. Then the tear one opens, its size by
     * its opener's height, where a Garganta leads from each world, sneaking or not, and which gate the key opens for
     * each soul. It runs in a batch of its own, so no other test's gate stands within the reach it checks.
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
}
