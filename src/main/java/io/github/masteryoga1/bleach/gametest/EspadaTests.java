package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.ArrancarNpcEvents;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.EspadaLedger;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.MenosEvolution;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Caja Negación and a player's Fracción: the hold the cube shares with Negación's light, how long a
 * soul is held and what turns the cube away, a hold that sets its rider down unharmed where it was taken, the one
 * cube a seated Espada keeps, the beaten Número that kneels and pledges, the cap, the dismissal and the release,
 * whom a Fracción fights for its Espada, the post it holds while its Espada is away, and Negación lifting the
 * Arrancar NPCs. Mock players are plain {@link Player}s that are not in the world and never tick, so the rules are
 * called directly, and a Fracción finds its mock Espada only through the leader its pledge remembers. A mock riding
 * a Caja would be ticked by it, so none is left riding past the tick it was shut away in.
 *
 * <p>The tests use the server's own Espada ledger under the mocks' random ids and take their entries out again;
 * only the cube's upkeep, which does nothing for a mock against the server's ledger, is driven against a fresh one.
 * A test that turns PvP off or on puts it back before its tick ends. An NPC spawned without free will is made still
 * as well, as having no free will leaves its target goals and its own server step running; an NPC whose own step
 * must run (a Fracción's upkeep, a kneel's end, a departure) is spawned with its goals. Every creature a test spawns
 * is taken away before the test ends, with the lights, cages and tears it opened, and a test that waits takes its
 * own away even when a step fails.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class EspadaTests {
    /** Where a mock player stands when where it stands matters to nothing. */
    private static final Vec3 MOCK_SPOT = new Vec3(1.5, 2.0, 1.5);
    /** A seated Espada's maximum reiryoku, above the {@code espada_min_max_reiryoku} line of 1,500. */
    private static final float ESPADA_REIRYOKU = 1600.0F;
    /** The Número every NPC here is set up as; any number would do. */
    private static final int NUMERO = 34;
    /** The Privaron Espada's number: the Tres's lost seat. */
    private static final int PRIVARON_SEAT = 103;
    /** A beaten NPC's health before each blow: at or below the {@code yield_health} share (0.3 of 60) already. */
    private static final float BEATEN_HEALTH = 16.0F;
    /** The hold of the hold-and-release test, in ticks: the closing and the tear's lead, and a little more. */
    private static final int SHORT_HOLD_TICKS = 60;
    /** How far a set-down creature may be from where it was taken, and a post's centre from the Fracción. */
    private static final double LANDING_EPSILON = 0.5;
    private static final double POST_EPSILON = 1.0;
    /** How far the Espada walks away from its Fracción: past the {@code follow_leash} of 64. */
    private static final double AWAY_BLOCKS = 70.0;
    /** How near the cage its quiet tear must stand. */
    private static final double TEAR_NEAR = 3.0;

    private EspadaTests() {
    }

    // ---- Helpers ----

    /** A mock player in this mode, an Arrancar with this rank and this much reiryoku. */
    private static Player arrancar(GameTestHelper helper, GameType mode, ArrancarRank rank, float maxReiryoku, Vec3 at) {
        Player player = HollowPlayerTests.soulPlayer(helper, mode, SoulState.ARRANCAR, maxReiryoku, at);
        giveRank(SoulLogic.get(player), rank);
        return player;
    }

    /** A mock seated Espada in survival, facing south. */
    private static Player espada(GameTestHelper helper, int seat, Vec3 at) {
        return arrancar(helper, GameType.SURVIVAL, new ArrancarRank(0, seat), ESPADA_REIRYOKU, at);
    }

    /** A mock player in survival with a fresh soul of this state. */
    private static Player mock(GameTestHelper helper, SoulState state, float maxReiryoku, Vec3 at) {
        return HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, state, maxReiryoku, at);
    }

    /** Writes this rank into the soul's own Arrancar record, as the roster would for a player. */
    private static void giveRank(SoulData data, ArrancarRank rank) {
        data.setHollow(data.hollow().withArrancar(data.hollow().arrancar().withRank(rank)));
    }

    /** A still Número at this spot, set up to stay until killed: no goals, no AI, no server step of its own. */
    private static ArrancarNpc numero(GameTestHelper helper, int x, int y, int z) {
        return still(helper, new ArrancarRank(NUMERO, 0), x, y, z);
    }

    /** A still NPC of this rank at this spot, set up to stay until killed. */
    private static ArrancarNpc still(GameTestHelper helper, ArrancarRank rank, int x, int y, int z) {
        ArrancarNpc npc = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.ARRANCAR.get(), x, y, z));
        npc.setUp(rank, helper.getLevel().getRandom(), ArrancarNpc.NEVER);
        return npc;
    }

    /** A Número with its goals, so it follows, kneels, keeps its pledge and leaves by its own step. */
    private static ArrancarNpc fighter(GameTestHelper helper, int x, int y, int z) {
        ArrancarNpc npc = helper.spawn(ModEntities.ARRANCAR.get(), x, y, z);
        npc.setUp(new ArrancarRank(NUMERO, 0), helper.getLevel().getRandom(), ArrancarNpc.NEVER);
        return npc;
    }

    /** Makes this NPC this Espada's Fracción and lists it in the server's ledger, as a pledge would. */
    private static void pledge(GameTestHelper helper, ArrancarNpc npc, Player espada) {
        npc.pledgeTo(espada);
        ledger(helper).addMember(espada.getUUID(), npc.getUUID(), npc.rank().numero());
    }

    private static EspadaLedger ledger(GameTestHelper helper) {
        return EspadaLedger.get(helper.getLevel().getServer());
    }

    private static long now(GameTestHelper helper) {
        return HollowPlayerTests.now(helper);
    }

    /**
     * Takes these mock Espada out of the server's ledger: every Número listed, the cube's rest, and the cube given
     * and missing, so their entries go. A list once forgotten keeps its time, which nothing can take out.
     */
    private static void clearLedger(GameTestHelper helper, Player... espadas) {
        EspadaLedger ledger = ledger(helper);
        for (Player espada : espadas) {
            UUID id = espada.getUUID();
            for (EspadaLedger.Member member : ledger.fraccion(id)) {
                ledger.removeMember(id, member.id());
            }
            ledger.setCajaReadyAt(id, 0L);
            ledger.setCajaIssued(id, false);
            ledger.setCajaMissingSince(id, -1L);
        }
    }

    /** How many cubes this player carries. */
    private static int cubes(Player player) {
        return player.getInventory().countItem(ModItems.CAJA_NEGACION.get());
    }

    /** Takes every cube out of this player's inventory. */
    private static void takeCubes(Player player) {
        Inventory inventory = player.getInventory();
        Item cube = ModItems.CAJA_NEGACION.get();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(cube)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void assertRefusal(GameTestHelper helper, @Nullable CajaNegacion.Refusal actual,
                                      @Nullable CajaNegacion.Refusal expected, String what) {
        helper.assertTrue(actual == expected, "the Caja Negación " + what + " gave " + actual + ", not " + expected);
    }

    private static void assertHold(GameTestHelper helper, int actual, int expected, String what) {
        helper.assertTrue(actual == expected, what + " is held " + actual + " ticks, not " + expected);
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** Every creature on this list, for a test that waits: some are added to it as the test goes. */
    private static void discardAll(List<Entity> spawned) {
        discard(spawned.toArray(new Entity[0]));
    }

    /** One step of a test that waits. Should it fail, every creature on the list is taken away first. */
    private static Runnable step(List<Entity> spawned, Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (RuntimeException e) {
                discardAll(spawned);
                throw e;
            }
        };
    }

    /** Every light and cage in this test's structure, and the tears they and the leaving NPCs opened. */
    private static void discardHolds(GameTestHelper helper) {
        AABB bounds = helper.getBounds().inflate(1.0, 16.0, 1.0);
        helper.getLevel().getEntitiesOfClass(NegacionLight.class, bounds).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(CajaNegacion.class, bounds).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(GargantaRift.class, bounds).forEach(Entity::discard);
    }

    /** Every dropped item and orb about this test's structure. */
    private static void discardDrops(GameTestHelper helper) {
        AABB bounds = helper.getBounds().inflate(2.0);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, bounds).forEach(Entity::discard);
    }

    /** A blow from this player on a beaten NPC: past its last blow's immunity, at a health that kneels it. */
    private static boolean beat(ArrancarNpc npc, Player attacker) {
        npc.setHealth(BEATEN_HEALTH);
        npc.invulnerableTime = 0;
        return npc.hurt(npc.damageSources().playerAttack(attacker), 2.0F);
    }

    // ---- The hold ----

    @GameTest(template = "field")
    public static void holdingVehicleCoversBothHolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 4, 8);
        ArrancarNpc npc = numero(helper, 11, 2, 8);
        try {
            NegacionLight light = Negacion.lift(level, spider);
            CajaNegacion caja = CajaNegacion.close(level, npc, null, 200);
            helper.assertTrue(light != null, "Negación's light did not lift the Spider Hollow");
            helper.assertTrue(caja != null, "the Caja Negación did not close on the Número");
            helper.assertTrue(Negacion.held(spider) && Negacion.held(npc),
                    "held: the Spider Hollow " + Negacion.held(spider) + ", the Número " + Negacion.held(npc));
            helper.assertTrue(Negacion.holderOf(spider) == light,
                    "the Spider Hollow is held by " + Negacion.holderOf(spider));
            helper.assertTrue(Negacion.holderOf(npc) == caja, "the Número is held by " + Negacion.holderOf(npc));
            helper.assertTrue(Negacion.lightOf(npc) == null, "the Número in the Caja is taken for one in a light");
            helper.assertFalse(npc.hurt(level.damageSources().generic(), 5.0F), "a Número in the Caja was hurt");
            helper.assertFalse(Negacion.mayHold(npc), "a Número in the Caja may be held again");

            helper.assertTrue(caja.shouldBeSaved(), "a Caja holding a Número is not saved with the world");
            CompoundTag tag = new CompoundTag();
            helper.assertTrue(caja.saveAsPassenger(tag), "a Caja holding a Número wrote nothing in its place");
            helper.assertTrue(tag.getString("id").equals("bleach:arrancar"),
                    "a Caja holding a Número wrote " + tag.getString("id") + " in its place");
            helper.assertFalse(tag.contains("Passengers"), "the Número written in the Caja's place carries a rider");
            helper.assertFalse(npc.save(new CompoundTag()), "a Número in the Caja was saved on its own as well");
        } finally {
            discard(spider, npc);
            discardHolds(helper);
        }

        EntityType<CajaNegacion> type = ModEntities.CAJA_NEGACION.get();
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("caja_negacion")) == type,
                "bleach:caja_negacion is not the Caja Negación");
        helper.assertTrue(type.is(SoulLogic.SPIRITS), "the Caja Negación is not in bleach:spirits");
        helper.assertTrue(BuiltInRegistries.ITEM.get(BleachMod.id("caja_negacion")) == ModItems.CAJA_NEGACION.get(),
                "bleach:caja_negacion is not the cube");
        helper.assertTrue(BuiltInRegistries.SOUND_EVENT.get(BleachMod.id("entity.caja_negacion.close"))
                        == ModSounds.CAJA_NEGACION_CLOSE.get(), "the Caja Negación's closing sound is not registered");
        for (String name : List.of(Milestones.CAJA_NEGACION, Milestones.FRACCION)) {
            helper.assertTrue(server.getAdvancements().get(BleachMod.id(name)) != null,
                    "the advancement bleach:" + name + " did not load");
        }
        helper.succeed();
    }

    /** The hold is read from the creature alone: the method takes no user, which is the claim. */
    @GameTest(template = "field")
    public static void cajaHoldTimeReadsOnlyTheTarget(GameTestHelper helper) {
        float[] reiryoku = {600.0F, 750.0F, 1125.0F, 1500.0F, 3000.0F};
        int[] expected = {1200, 1200, 700, 200, 200};
        List<ArrancarNpc> npcs = new ArrayList<>();
        try {
            for (int i = 0; i < reiryoku.length; i++) {
                ArrancarNpc npc = numero(helper, 2 + 2 * i, 2, 8);
                npcs.add(npc);
                npc.soul().setMaxReiryoku(reiryoku[i]);
                assertHold(helper, CajaNegacion.holdTicks(npc), expected[i],
                        "a Número with " + reiryoku[i] + " maximum reiryoku");
            }
        } finally {
            discard(npcs.toArray(new Entity[0]));
        }
        assertHold(helper, CajaNegacion.holdTicks(espada(helper, 4, MOCK_SPOT)), 100, "a seated Espada");
        Player unseated = arrancar(helper, GameType.SURVIVAL, new ArrancarRank(40, 0), ESPADA_REIRYOKU, MOCK_SPOT);
        assertHold(helper, CajaNegacion.holdTicks(unseated), 200, "a Número player as strong as an Espada");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void cajaRefusals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        long now = now(helper);
        boolean pvp = server.isPvpAllowed();
        Player espada = espada(helper, 6, new Vec3(2.5, 2.0, 2.5));
        Player numero = arrancar(helper, GameType.SURVIVAL, new ArrancarRank(40, 0), ESPADA_REIRYOKU, MOCK_SPOT);
        Player shinigami = mock(helper, SoulState.SHINIGAMI, 100.0F, new Vec3(2.5, 2.0, 3.5));
        // South of the Espada and facing south, away from it, so it cannot see the Espada coming.
        Player hollow = mock(helper, SoulState.HOLLOW, 100.0F, new Vec3(3.5, 2.0, 3.5));
        Player creative = HollowPlayerTests.soulPlayer(helper, GameType.CREATIVE, SoulState.HOLLOW, 100.0F,
                new Vec3(2.5, 2.0, 5.5));
        SpiderHollow facing = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 2, 4);
        SpiderHollow second = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 7, 4);
        SpiderHollow third = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 12, 4);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, 12, 2, 10);
        try {
            // Facing north, towards the Espada.
            facing.setYHeadRot(180.0F);
            assertRefusal(helper, CajaNegacion.refusal(numero, facing, now), CajaNegacion.Refusal.NOT_ESPADA,
                    "in a Número's hands");
            assertRefusal(helper, CajaNegacion.refusal(espada, shinigami, now), CajaNegacion.Refusal.NO_HOLE,
                    "on a Shinigami");
            assertRefusal(helper, CajaNegacion.refusal(espada, facing, now), CajaNegacion.Refusal.SEES_IT,
                    "on a Spider Hollow that faces the Espada");

            facing.setYHeadRot(0.0F);
            assertRefusal(helper, CajaNegacion.use(espada, facing, now), null, "on a Spider Hollow that faces away");
            helper.assertTrue(Negacion.holderOf(facing) instanceof CajaNegacion, "the Spider Hollow was not shut away");
            assertRefusal(helper, CajaNegacion.refusal(espada, second, now), CajaNegacion.Refusal.COOLDOWN,
                    "used again at once");
            long seconds = CajaNegacion.secondsToReady(espada, now);
            helper.assertTrue(seconds == 300L, "the cube is ready again in " + seconds + " seconds, not 300");

            // The rest is checked before anything busy, so it is put aside to see the rest.
            ledger(helper).setCajaReadyAt(espada.getUUID(), 0L);
            assertRefusal(helper, CajaNegacion.refusal(espada, facing, now), CajaNegacion.Refusal.BUSY,
                    "on a Spider Hollow already shut away");
            helper.assertTrue(stand.startRiding(third, true), "the armour stand could not ride the third Spider Hollow");
            assertRefusal(helper, CajaNegacion.refusal(espada, third, now), CajaNegacion.Refusal.BUSY,
                    "on a Spider Hollow carrying an armour stand");

            server.setPvpAllowed(false);
            assertRefusal(helper, CajaNegacion.refusal(espada, hollow, now), CajaNegacion.Refusal.PROTECTED,
                    "on a Hollow player with PvP off");
            server.setPvpAllowed(true);
            // Before the Hollow player is shut away, which would set the cube's rest again.
            assertRefusal(helper, CajaNegacion.refusal(espada, creative, now), CajaNegacion.Refusal.PROTECTED,
                    "on a Hollow player in creative mode");
            assertRefusal(helper, CajaNegacion.use(espada, hollow, now), null, "on a Hollow player with PvP on");
            helper.assertTrue(Negacion.holderOf(hollow) instanceof CajaNegacion, "the Hollow player was not shut away");
        } finally {
            server.setPvpAllowed(pvp);
            hollow.stopRiding();
            discard(facing, stand, second, third);
            discardHolds(helper);
            clearLedger(helper, espada);
        }
        helper.succeed();
    }

    /**
     * A Número shut away for three seconds, with a Spider Hollow hunting it, is held frozen behind a quiet tear and
     * set down unharmed where it was taken. Where it lands is noted the tick it is let go, as a Número with its goals
     * may walk off before the last check.
     */
    @GameTest(template = "field", timeoutTicks = 100)
    public static void cajaHoldsAndSetsDownUnharmed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ArrancarNpc npc = fighter(helper, 4, 2, 8);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 10, 8);
        List<Entity> spawned = new ArrayList<>(List.of(npc, spider));
        Vec3 stood = npc.position();
        CajaNegacion[] caja = new CajaNegacion[1];
        Vec3[] landed = new Vec3[1];
        step(spawned, () -> {
            helper.assertTrue(npc.getHealth() == npc.getMaxHealth(), "the Número is not at full health");
            spider.setTarget(npc);
            helper.assertTrue(spider.getTarget() == npc, "the Spider Hollow would not hunt the Número");
            caja[0] = CajaNegacion.close(level, npc, null, SHORT_HOLD_TICKS);
            helper.assertTrue(caja[0] != null, "the Caja Negación did not close on the Número");
            spawned.add(caja[0]);
            helper.assertTrue(spider.getTarget() == null, "the Spider Hollow still hunts the Número shut away");
            // What ArrancarNpc.isImmobile reads, so it stands frozen.
            helper.assertTrue(Negacion.holderOf(npc) == caja[0], "the Número is held by " + Negacion.holderOf(npc));
            helper.assertTrue(caja[0].phase() == CajaNegacion.Phase.CLOSING, "a new Caja is " + caja[0].phase());
            // A pearl thrown before the Caja closed is lost on landing, rather than carrying its thrower out.
            BlockHitResult ground = BlockHitResult.miss(stood, Direction.UP, npc.blockPosition());
            ThrownEnderpearl pearl = new ThrownEnderpearl(level, npc);
            helper.assertTrue(EventHooks.onProjectileImpact(pearl, ground) && pearl.isRemoved(),
                    "a landing pearl would take the Número out of the Caja");
            ThrownEnderpearl free = new ThrownEnderpearl(level, spider);
            helper.assertFalse(EventHooks.onProjectileImpact(free, ground), "a free thrower's pearl was lost on landing");

            helper.assertTrue(caja[0].landsHere(npc), "the Número would not be set down where it was taken");
            ServerLevel nether = server.getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "the game test server has no Nether");
            ArrancarNpc elsewhere = ModEntities.ARRANCAR.get().create(nether);
            helper.assertTrue(elsewhere != null, "no Arrancar NPC could be made in the Nether");
            helper.assertFalse(caja[0].landsHere(elsewhere), "a rider already in another world would be pulled back");
        }).run();
        helper.onEachTick(() -> {
            if (landed[0] == null && caja[0] != null && !npc.isPassenger()) {
                landed[0] = npc.position();
            }
        });

        helper.startSequence()
                .thenExecuteAfter(16, step(spawned, () -> {
                    helper.assertTrue(caja[0].phase() == CajaNegacion.Phase.HOLDING,
                            "the Caja is " + caja[0].phase() + " 16 ticks after it closed");
                    helper.assertTrue(caja[0].hidesRider() && CajaNegacion.hidden(npc), "the Caja does not hide the Número");
                }))
                .thenExecuteAfter(4, step(spawned, () -> {
                    boolean tear = !level.getEntitiesOfClass(GargantaRift.class, caja[0].getBoundingBox().inflate(TEAR_NEAR),
                            rift -> rift.distanceTo(caja[0]) <= TEAR_NEAR).isEmpty();
                    helper.assertTrue(tear, "no tear stands by the Caja while it holds the Número");
                }))
                .thenExecuteAfter(55, step(spawned, () -> {
                    try {
                        helper.assertTrue(caja[0].isRemoved(), "the Caja is still there, " + caja[0].phase());
                        helper.assertFalse(npc.isPassenger(), "the Número is still held by " + npc.getVehicle());
                        helper.assertTrue(npc.getHealth() == npc.getMaxHealth(),
                                "the Número came out with " + npc.getHealth() + " of " + npc.getMaxHealth() + " health");
                        helper.assertTrue(landed[0] != null && landed[0].distanceTo(stood) <= LANDING_EPSILON,
                                "the Número was set down at " + landed[0] + ", not where it was taken, " + stood);
                        if (BleachConfig.get(BleachConfig.NEGACION_FALL_GUARD_SECONDS) > 0) {
                            helper.assertTrue(npc.hasEffect(MobEffects.SLOW_FALLING),
                                    "the Número was let go without Slow Falling");
                        }
                    } finally {
                        discardAll(spawned);
                        discardHolds(helper);
                    }
                }))
                .thenSucceed();
    }

    // ---- The cube ----

    /**
     * The upkeep against a fresh ledger, as against the server's it does nothing for a mock. The time is taken far
     * enough on that a cube missing for the whole of {@code reissue_minutes} went missing after the world began.
     */
    @GameTest(template = "empty")
    public static void onlyASeatedEspadaKeepsTheCube(GameTestHelper helper) {
        int minutes = BleachConfig.get(BleachConfig.CAJA_REISSUE_MINUTES);
        long now = now(helper) + (long) minutes * MenosEvolution.MINUTE_TICKS;
        Player espada = espada(helper, 2, MOCK_SPOT);
        SoulData data = SoulLogic.get(espada);
        Player spectator = arrancar(helper, GameType.SPECTATOR, new ArrancarRank(0, 3), ESPADA_REIRYOKU, MOCK_SPOT);
        Player shinigami = mock(helper, SoulState.SHINIGAMI, 100.0F, MOCK_SPOT);
        EspadaLedger ledger = new EspadaLedger();
        UUID id = espada.getUUID();
        Item cube = ModItems.CAJA_NEGACION.get();

        EspadaLedger.keep(espada, data, now);
        helper.assertTrue(cubes(espada) == 0, "a mock player was given a cube against the server's ledger");

        EspadaLedger.keep(spectator, SoulLogic.get(spectator), ledger, now);
        helper.assertTrue(cubes(spectator) == 0 && !ledger.cajaIssued(spectator.getUUID()),
                "a spectating Espada was given a cube");

        EspadaLedger.keep(espada, data, ledger, now);
        helper.assertTrue(cubes(espada) == 1 && ledger.cajaIssued(id), "a new Espada holds " + cubes(espada) + " cubes");
        espada.getInventory().add(new ItemStack(cube));
        EspadaLedger.keep(espada, data, ledger, now);
        helper.assertTrue(cubes(espada) == 1, "an Espada given a second cube keeps " + cubes(espada));

        takeCubes(espada);
        EspadaLedger.keep(espada, data, ledger, now);
        helper.assertTrue(cubes(espada) == 0, "a lost cube came straight back");
        helper.assertTrue(ledger.cajaMissingSince(id) == now,
                "a lost cube was noted missing since " + ledger.cajaMissingSince(id) + ", not " + now);
        ledger.setCajaMissingSince(id, now - (long) minutes * MenosEvolution.MINUTE_TICKS);
        EspadaLedger.keep(espada, data, ledger, now);
        helper.assertTrue(cubes(espada) == 1, "a cube lost for " + minutes + " minutes was not given again");

        giveRank(data, new ArrancarRank(50, 0));
        EspadaLedger.keep(espada, data, ledger, now);
        helper.assertTrue(cubes(espada) == 0, "an Espada that lost its seat keeps its cube");
        helper.assertFalse(ledger.cajaIssued(id), "an Espada that lost its seat is still counted as given a cube");

        // Not an Arrancar: the cube crumbles, and a Fracción listed for it is forgotten.
        UUID member = UUID.randomUUID();
        ledger.addMember(shinigami.getUUID(), member, NUMERO);
        shinigami.getInventory().add(new ItemStack(cube));
        EspadaLedger.keep(shinigami, SoulLogic.get(shinigami), ledger, now);
        helper.assertTrue(cubes(shinigami) == 0, "a Shinigami keeps a cube");
        helper.assertTrue(ledger.fraccion(shinigami.getUUID()).isEmpty()
                        && ledger.fraccionForgottenAt(shinigami.getUUID()) == now,
                "the Fracción of a soul no longer an Arrancar was not forgotten");
        helper.succeed();
    }

    // ---- A player's Fracción ----

    /**
     * Only a seated Espada's blow kneels a beaten Número, and only one whose leader is gone; the kneel's Espada
     * takes it with an empty hand. The mock players stand beyond the reach of a Sonído dodge.
     */
    @GameTest(template = "field", timeoutTicks = 100)
    public static void aBeatenNumeroYieldsAndPledges(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Player espada = espada(helper, 4, new Vec3(8.5, 2.0, 13.5));
        Player shinigami = mock(helper, SoulState.SHINIGAMI, 100.0F, new Vec3(2.5, 2.0, 13.5));
        ArrancarNpc fraccion = fighter(helper, 2, 2, 5);
        ArrancarNpc privaron = still(helper, new ArrancarRank(0, PRIVARON_SEAT), 6, 2, 5);
        ArrancarNpc follower = numero(helper, 9, 2, 5);
        ArrancarNpc kneeling = fighter(helper, 13, 2, 5);
        List<Entity> spawned = new ArrayList<>(List.of(fraccion, privaron, follower, kneeling));
        step(spawned, () -> {
            try {
                long now = now(helper);
                beat(fraccion, shinigami);
                helper.assertFalse(fraccion.isYielding(), "a Número kneels to a Shinigami");

                helper.assertTrue(beat(fraccion, espada), "the Espada's blow never reached the Número");
                helper.assertTrue(fraccion.isYielding() && fraccion.isCrouching(), "a beaten Número does not kneel");
                helper.assertTrue(fraccion.getTarget() == null, "a kneeling Número fights " + fraccion.getTarget());
                helper.assertFalse(fraccion.canAttack(espada), "a kneeling Número may attack the Espada it kneels to");
                beat(fraccion, espada);
                helper.assertFalse(fraccion.isYielding(), "a second blow did not end the kneel");
                beat(fraccion, espada);
                helper.assertTrue(fraccion.isYielding(), "a third blow did not kneel it again");

                int before = ArrancarNpcEvents.aliveCount(server);
                InteractionResult result = fraccion.interact(espada, InteractionHand.MAIN_HAND);
                int after = ArrancarNpcEvents.aliveCount(server);
                helper.assertTrue(result.consumesAction(), "taking a kneeling Número gave " + result);
                helper.assertTrue(fraccion.isPledged(), "the Número was not pledged");
                helper.assertTrue(espada.getUUID().equals(fraccion.leaderId()), "the Fracción does not follow its Espada");
                helper.assertTrue(fraccion.isPermanent(), "the Fracción will leave at " + fraccion.leaveAt());
                helper.assertTrue(espada.getUUID().equals(fraccion.getOwnerUUID()), "the Fracción has no owner");
                helper.assertTrue(ledger(helper).isMember(espada.getUUID(), fraccion.getUUID()),
                        "the Fracción is not listed in the ledger");
                helper.assertTrue(after == before - 1, "the Arrancar alive went from " + before + " to " + after);

                privaron.markRecallRolled();
                beat(privaron, espada);
                helper.assertFalse(privaron.isYielding(), "a Privaron Espada kneels");
                follower.setLeader(privaron.getUUID());
                beat(follower, espada);
                helper.assertFalse(follower.isYielding(), "a Número kneels while its leader lives");

                helper.assertTrue(beat(kneeling, espada), "the Espada's blow never reached the second Número");
                helper.assertTrue(kneeling.isYielding(), "the second beaten Número does not kneel");
                kneeling.setLeaveAt(now + 5L);
                ArrancarNpcEvents.Pledge pledge = ArrancarNpcEvents.pledge(kneeling, shinigami, now);
                helper.assertTrue(pledge == ArrancarNpcEvents.Pledge.NOT_KNEELING,
                        "a Número kneeling to the Espada pledged to a Shinigami: " + pledge);
            } finally {
                discard(fraccion, privaron, follower);
            }
        }).run();

        helper.startSequence()
                .thenExecuteAfter(25, step(spawned, () -> {
                    helper.assertTrue(kneeling.isYielding(), "the kneel ran out within 25 ticks");
                    helper.assertFalse(kneeling.isDeparting(), "a kneeling Número left when its time came");
                    kneeling.endYield();
                }))
                .thenExecuteAfter(25, step(spawned, () -> {
                    try {
                        helper.assertTrue(kneeling.isDeparting(), "a Número whose time ran out while it knelt never left");
                    } finally {
                        discardAll(spawned);
                        discardHolds(helper);
                        clearLedger(helper, espada);
                    }
                }))
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 100)
    public static void theFraccionIsCappedDismissedAndReleased(GameTestHelper helper) {
        long now = now(helper);
        Player espada = espada(helper, 5, new Vec3(8.5, 2.0, 8.5));
        ArrancarNpc first = fighter(helper, 3, 2, 3);
        ArrancarNpc second = fighter(helper, 12, 2, 3);
        ArrancarNpc third = fighter(helper, 3, 2, 12);
        ArrancarNpc fourth = fighter(helper, 12, 2, 12);
        List<Entity> spawned = new ArrayList<>(List.of(first, second, third, fourth));
        step(spawned, () -> {
            pledge(helper, first, espada);
            pledge(helper, second, espada);
            pledge(helper, third, espada);
            fourth.yieldTo(espada, now + 600L);
            ArrancarNpcEvents.Pledge full = ArrancarNpcEvents.pledge(fourth, espada, now);
            helper.assertTrue(full == ArrancarNpcEvents.Pledge.FULL, "a fourth Número's pledge gave " + full);
            helper.assertFalse(fourth.isPledged(), "a fourth Número was pledged past the cap");

            espada.setShiftKeyDown(true);
            first.interact(espada, InteractionHand.MAIN_HAND);
            helper.assertFalse(first.isDeparting(), "one Sneak-use sent the Fracción away");
            // The use key held down repeats within the same press: that is no second use.
            first.interact(espada, InteractionHand.MAIN_HAND);
            espada.setShiftKeyDown(false);
            helper.assertFalse(first.isDeparting(), "a held Sneak-use sent the Fracción away");
        }).run();

        helper.startSequence()
                .thenExecuteAfter(ArrancarNpc.DISMISS_PRESS_GAP_TICKS + 5, step(spawned, () -> {
                    EspadaLedger ledger = ledger(helper);
                    UUID id = espada.getUUID();
                    espada.setShiftKeyDown(true);
                    first.interact(espada, InteractionHand.MAIN_HAND);
                    espada.setShiftKeyDown(false);
                    helper.assertTrue(first.isDeparting() && !first.isPledged(), "a dismissed Fracción did not leave");
                    helper.assertFalse(ledger.isMember(id, first.getUUID()), "a dismissed Fracción is still listed");

                    helper.assertTrue(ledger.forgetFraccion(id, now(helper)), "the Fracción list held no one to forget");
                }))
                .thenExecuteAfter(25, step(spawned, () -> {
                    try {
                        helper.assertTrue(second.isDeparting() && third.isDeparting(),
                                "a Fracción whose list was forgotten stayed: " + second.isDeparting() + ", "
                                        + third.isDeparting());
                        helper.assertFalse(second.isPledged() || third.isPledged(), "a released Fracción is still pledged");

                        pledge(helper, fourth, espada);
                        helper.assertTrue(ledger(helper).isMember(espada.getUUID(), fourth.getUUID()),
                                "the Número pledged on its own is not listed");
                        fourth.kill();
                        helper.assertTrue(fourth.isDeadOrDying(), "the Fracción did not die");
                        helper.assertFalse(ledger(helper).isMember(espada.getUUID(), fourth.getUUID()),
                                "a Fracción killed is still listed");
                    } finally {
                        discardAll(spawned);
                        discardHolds(helper);
                        discardDrops(helper);
                        clearLedger(helper, espada);
                    }
                }))
                .thenSucceed();
    }

    @GameTest(template = "field")
    public static void aPledgedFraccionFightsOnlyForItsEspada(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        Player espada = espada(helper, 7, new Vec3(4.5, 2.0, 4.5));
        Player human = mock(helper, SoulState.HUMAN, 100.0F, new Vec3(6.5, 2.0, 10.5));
        ArrancarNpc fraccion = fighter(helper, 6, 2, 4);
        ArrancarNpc wild = numero(helper, 9, 2, 4);
        ArrancarNpc privaron = still(helper, new ArrancarRank(0, PRIVARON_SEAT), 10, 2, 9);
        ArrancarNpc follower = numero(helper, 12, 2, 9);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 3, 12);
        try {
            pledge(helper, fraccion, espada);
            follower.setLeader(privaron.getUUID());
            // With PvP on, nothing but the no-prey rule keeps a Fracción off a player its Espada may harm.
            server.setPvpAllowed(true);

            // On Peaceful no mob may attack a player at all.
            boolean peaceful = HollowPlayerTests.peaceful(helper);
            helper.assertTrue(peaceful || wild.canAttack(human), "a wild Número may not attack an aware Human");
            helper.assertFalse(fraccion.canAttack(human), "a Fracción hunts an aware Human of its own accord");
            helper.assertFalse(CeroBeam.mayStrike(fraccion, human), "a Fracción's Cero may strike an aware Human");
            helper.assertFalse(fraccion.canAttack(espada), "a Fracción may attack its Espada");
            helper.assertTrue(wild.isPreventingPlayerRest(espada), "a wild Número lets an Arrancar sleep beside it");
            helper.assertFalse(fraccion.isPreventingPlayerRest(espada), "a Fracción keeps its own Espada from sleeping");
            // An empty main hand gives a Fracción the click; the cube in the off hand must not close on it as well.
            ItemStack cube = new ItemStack(ModItems.CAJA_NEGACION.get());
            espada.setItemInHand(InteractionHand.OFF_HAND, cube);
            InteractionResult offHand = cube.interactLivingEntity(espada, fraccion, InteractionHand.OFF_HAND);
            espada.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            helper.assertTrue(offHand == InteractionResult.PASS && !Negacion.held(fraccion),
                    "the off-hand cube closed on a Fracción its Espada clicked with an empty hand: " + offHand);

            helper.assertFalse(fraccion.canAttack(spider), "a Fracción may attack a Spider Hollow no one struck");
            espada.setLastHurtMob(spider);
            helper.assertTrue(fraccion.canAttack(spider), "a Fracción may not attack what its Espada struck just now");
            privaron.setLastHurtMob(spider);
            helper.assertFalse(follower.canAttack(spider),
                    "a Privaron Espada's own Fracción may attack what its leader struck, which is a pledged one's rule");

            espada.setLastHurtByMob(human);
            server.setPvpAllowed(false);
            helper.assertFalse(fraccion.canAttack(human), "a Fracción may attack a player with PvP off");
            server.setPvpAllowed(true);
            helper.assertTrue(peaceful || fraccion.canAttack(human),
                    "a Fracción may not attack the player that struck its Espada");

            helper.assertFalse(SpiritProjectiles.mayStrike(espada, fraccion), "an Espada's own Cero may strike its Fracción");
            helper.assertTrue(espada.getUUID().equals(fraccion.getOwnerUUID()), "the Fracción's owner is not its Espada");
        } finally {
            server.setPvpAllowed(pvp);
            discard(fraccion, wild, privaron, follower, spider);
            clearLedger(helper, espada);
        }
        helper.succeed();
    }

    /**
     * The Espada walks out of reach, and its Fracción holds its post and lists itself again in a ledger that lost it,
     * as one lost with its file would be; then the Espada comes back, and the Fracción follows once more. The Espada
     * leaves on the tick before the Fracción's once-a-second upkeep, so the post is taken where it stood.
     */
    @GameTest(template = "field", timeoutTicks = 220)
    public static void fraccionHoldTheirPostWhileTheirEspadaIsAway(GameTestHelper helper) {
        Vec3 home = new Vec3(4.5, 2.0, 4.5);
        Player espada = espada(helper, 8, home);
        Vec3 homeAbsolute = espada.position();
        ArrancarNpc fraccion = fighter(helper, 6, 2, 4);
        List<Entity> spawned = new ArrayList<>(List.of(fraccion));
        Vec3[] post = new Vec3[1];
        step(spawned, () -> {
            pledge(helper, fraccion, espada);
            fraccion.setHealth(18.0F);
        }).run();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fraccion.tickCount % 20 == 19, "waiting for the upkeep"))
                .thenExecute(step(spawned, () -> {
                    ledger(helper).removeMember(espada.getUUID(), fraccion.getUUID());
                    post[0] = fraccion.position();
                    espada.moveTo(homeAbsolute.x + AWAY_BLOCKS, homeAbsolute.y, homeAbsolute.z);
                }))
                .thenExecuteAfter(25, step(spawned, () -> {
                    helper.assertTrue(fraccion.leader() == null, "a Fracción still finds its Espada 70 blocks away");
                    helper.assertTrue(fraccion.hasRestriction(), "a Fracción whose Espada is away holds no post");
                    helper.assertTrue(fraccion.getRestrictRadius() == BleachConfig.get(BleachConfig.FRACCION_POST_RADIUS),
                            "the post's radius is " + fraccion.getRestrictRadius());
                    BlockPos centre = fraccion.getRestrictCenter();
                    double across = Math.hypot(centre.getX() + 0.5 - post[0].x, centre.getZ() + 0.5 - post[0].z);
                    helper.assertTrue(across <= POST_EPSILON && Math.abs(centre.getY() - post[0].y) <= POST_EPSILON,
                            "the post is at " + centre + ", not where the Fracción stood, " + post[0]);
                    helper.assertTrue(fraccion.isPledged() && !fraccion.isDeparting(),
                            "a Fracción whose Espada is away left: pledged " + fraccion.isPledged());
                    helper.assertTrue(ledger(helper).isMember(espada.getUUID(), fraccion.getUUID()),
                            "a Fracción missing from the ledger did not list itself again");
                    espada.moveTo(homeAbsolute.x, homeAbsolute.y, homeAbsolute.z);
                }))
                .thenExecuteAfter(25, step(spawned, () -> {
                    helper.assertFalse(fraccion.hasRestriction(), "a Fracción whose Espada came back still holds its post");
                    helper.assertTrue(fraccion.leader() == espada, "a Fracción does not find its Espada once it is back");
                }))
                .thenExecuteAfter(100, step(spawned, () -> {
                    try {
                        helper.assertTrue(fraccion.getHealth() > 18.0F, "a Fracción with no fight did not heal");
                        helper.assertTrue(ledger(helper).isMember(espada.getUUID(), fraccion.getUUID()),
                                "the Fracción fell off the ledger");
                    } finally {
                        discardAll(spawned);
                        clearLedger(helper, espada);
                    }
                }))
                .thenSucceed();
    }

    // ---- Negación ----

    @GameTest(template = "field")
    public static void negacionLiftsArrancarNpcs(GameTestHelper helper) {
        long now = now(helper);
        Player caster = mock(helper, SoulState.HOLLOW, 400.0F, new Vec3(8.5, 2.0, 2.5));
        SoulData data = SoulLogic.get(caster);
        data.setTier(PowerPath.HOLLOW, HollowTier.GILLIAN.tier());
        Player espada = espada(helper, 9, MOCK_SPOT);
        ArrancarNpc wild = numero(helper, 3, 2, 8);
        ArrancarNpc pledged = numero(helper, 8, 2, 8);
        ArrancarNpc departing = numero(helper, 13, 2, 8);
        try {
            pledge(helper, pledged, espada);
            departing.depart();
            helper.assertTrue(departing.isDeparting(), "the third Número is not leaving");

            helper.assertTrue(Negacion.targetRefusal(caster, wild) == null,
                    "Negación on a wild Número gave " + Negacion.targetRefusal(caster, wild));
            Negacion.Refusal cast = Negacion.castOn(caster, data, wild, now);
            helper.assertTrue(cast == null, "Negación's cast on a wild Número gave " + cast);
            helper.assertTrue(Negacion.lightOf(wild) != null, "Negación did not lift the wild Número");
            helper.assertTrue(Negacion.targetRefusal(caster, pledged) == Negacion.Refusal.NOT_ALLY,
                    "Negación on someone's Fracción gave " + Negacion.targetRefusal(caster, pledged));
            helper.assertTrue(Negacion.targetRefusal(caster, departing) == Negacion.Refusal.BUSY,
                    "Negación on a Número leaving through its Garganta gave " + Negacion.targetRefusal(caster, departing));
        } finally {
            discard(wild, pledged, departing);
            discardHolds(helper);
            clearLedger(helper, espada);
        }
        helper.succeed();
    }
}
