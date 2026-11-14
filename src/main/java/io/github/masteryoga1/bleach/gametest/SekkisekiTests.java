package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.network.GateKeyPayload;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.Bala;
import io.github.masteryoga1.bleach.soul.GintoSpells;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.Pesquisa;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Sekkiseki's rules: a spirit art comes undone at the first Sekkiseki block it meets, in a spray of pale
 * light and a hiss, and at stone does as it always has; Pesquisa feels no soul through it, and no gate opens into it.
 * They use the {@code field} template, a 16 by 16 stone floor at relative y 1, and run in a batch of their own, so no
 * other test's sprays, arrows, creatures or pulses share their run.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class SekkisekiTests {
    /** The way every art is fired, the same in the box and in the world, since no test turns its box. */
    private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
    /** The table's two lanes (relative z): one ends at a Sekkiseki wall, the other at a stone one. */
    private static final double[] LANES = {3.5, 12.5};
    /** The table's first row, the ticks between rows, and how long after its row each row is counted. */
    private static final int FIRST_ROW = 5;
    private static final int ROW_TICKS = 10;
    private static final int COUNT_AT = 8;
    /** Room for the float steps of a position. */
    private static final double EPSILON = 1.0E-3;
    /** Where the gate tests' openers stand (relative), facing south. */
    private static final Vec3 GATE_SPOT = new Vec3(8.5, 2.0, 2.5);

    private SekkisekiTests() {
    }

    // ---- Helpers ----

    /** This point of the box, in the world. */
    private static Vec3 abs(GameTestHelper helper, double x, double y, double z) {
        return helper.absoluteVec(new Vec3(x, y, z));
    }

    /** The part of the box between these two corners, in the world. */
    private static AABB box(GameTestHelper helper, double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(abs(helper, x0, y0, z0), abs(helper, x1, y1, z1));
    }

    /** How many sprays of pale light, and how many hisses, among these packets were set off inside this part. */
    private static int[] fizzles(List<Packet<?>> effects, AABB part) {
        int[] seen = new int[2];
        for (Packet<?> packet : effects) {
            if (packet instanceof ClientboundLevelParticlesPacket spray
                    && spray.getParticle().getType() == ParticleTypes.WAX_OFF
                    && part.contains(spray.getX(), spray.getY(), spray.getZ())) {
                seen[0]++;
            } else if (packet instanceof ClientboundSoundPacket hiss && hiss.getSound().value() == SoundEvents.FIRE_EXTINGUISH
                    && part.contains(hiss.getX(), hiss.getY(), hiss.getZ())) {
                seen[1]++;
            }
        }
        return seen;
    }

    /** A spectator beside the walls, who sees spirits and is struck by nothing, its effects watched. */
    private static ServerPlayer watcher(GameTestHelper helper) {
        ServerPlayer watcher = TestPlayers.join(helper, GameType.SPECTATOR, new Vec3(8.5, 2.0, 8.0));
        TestPlayers.watchEffects(watcher);
        return watcher;
    }

    /** A mock in this game mode with this soul, a while in the world, on the ground at this spot of the box, facing east. */
    private static Player shooter(GameTestHelper helper, GameType mode, SoulData data, Vec3 relative) {
        Player player = KidoTests.caster(helper, mode, data);
        Vec3 at = helper.absoluteVec(relative);
        player.setPos(at.x, at.y, at.z);
        QuincyArtsTests.face(player, player.getEyePosition().add(EAST));
        return player;
    }

    /** How one row fires its art down the lane at this relative z. */
    private interface Firing {
        void fire(GameTestHelper helper, double z);
    }

    /** One art of the table, and how it is fired. */
    private record Row(String art, Firing firing) {
    }

    /** Every spirit art that meets blocks, each fired the way a player or a creature fires it. */
    private static List<Row> rows() {
        List<Row> rows = new ArrayList<>(List.of(
                new Row("Getsuga Tenshō", SekkisekiTests::getsuga),
                new Row("Cero", (h, z) -> cero(h, z, (p, d) -> HollowCero.spec(d, HollowTier.ADJUCHAS), CeroBeam.Kind.CERO)),
                new Row("gathered Cero", SekkisekiTests::gatheredCero),
                new Row("Bala", (h, z) -> cero(h, z, (p, d) -> Bala.spec(d), Bala.KIND)),
                new Row("Gran Rey Cero", (h, z) -> cero(h, z, GranReyCero::spec, GranReyCero.KIND))));
        for (ResourceKey<KidoSpell> key : List.of(KidoSpell.SHO, KidoSpell.BYAKURAI, KidoSpell.SHAKKAHO,
                KidoSpell.SOKATSUI, KidoSpell.RAIKOHO, KidoSpell.TENRAN, KidoSpell.SAI, KidoSpell.HAINAWA, KidoSpell.GEKI,
                KidoSpell.RIKUJOKORO)) {
            rows.add(new Row(key.location().getPath(), (h, z) -> kido(h, z, key)));
        }
        rows.addAll(List.of(
                new Row("Heilig Pfeil", (h, z) -> QuincyTests.loose(h, quincy(h, z), arrowFrom(h, z), EAST, 4.0F, 0, 3.0F)),
                new Row("Licht Regen", (h, z) -> HeiligPfeil.launchVolley(h.getLevel(), quincy(h, z), arrowFrom(h, z), EAST,
                        1.5F, 2.6F, 0.0F, 30)),
                new Row("Letzt Pfeil", (h, z) -> LetztPfeil.launch(h.getLevel(), quincy(h, z), arrowFrom(h, z), EAST, 10.0F,
                        0, 3.5F)),
                new Row("Seele Schneider", (h, z) -> SeeleSchneiderPfeil.launch(h.getLevel(), quincy(h, z), arrowFrom(h, z),
                        EAST, 1.0F, 0, 3.0F)),
                new Row("Heizen", (h, z) -> ginto(h, GintoSpell.HEIZEN, new Vec3(6.5, 2.0, z))),
                // Standing on the wall's top, as Wolke is cast from the ground.
                new Row("Wolke", (h, z) -> ginto(h, GintoSpell.WOLKE, new Vec3(11.5, 6.0, z)))));
        return rows;
    }

    /** A crescent swung by a Shinigami in the lane: it reaches the walls on its third tick. */
    private static void getsuga(GameTestHelper helper, double z) {
        Player owner = shooter(helper, GameType.SURVIVAL, ZanpakutoTests.soul(SoulState.SHINIGAMI, 400.0F),
                new Vec3(4.5, 2.0, z));
        GetsugaTensho.launch(helper.getLevel(), owner, abs(helper, 5.5, 2.5, z), EAST, 0.0F, 10.0F, 9,
                ZanpakutoTests.asauchi(), 0xFFFFFF);
    }

    /** An Adjuchas-born Arrancar standing in the lane, facing the walls. */
    private static Player arrancar(GameTestHelper helper, double z) {
        Player player = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, new Vec3(6.5, 2.0, z));
        QuincyArtsTests.face(player, player.getEyePosition().add(EAST));
        return player;
    }

    /** A beam of this spec and kind fired at once from the Arrancar's mouth, as a Bala is. */
    private static void cero(GameTestHelper helper, double z, BiFunction<Player, SoulData, CeroBeam.Spec> spec,
                             CeroBeam.Kind kind) {
        Player player = arrancar(helper, z);
        CeroBeam.fire(helper.getLevel(), player, null, CeroBeam.muzzleOf(player, 1.0F), player.getLookAngle(),
                spec.apply(player, SoulLogic.get(player)), kind);
    }

    /** A Hollow Cero gathered at the Arrancar's mouth and let go at once, as every gathered Cero fires. */
    private static void gatheredCero(GameTestHelper helper, double z) {
        Player player = arrancar(helper, z);
        CeroBeam beam = CeroBeam.charge(helper.getLevel(), player,
                HollowCero.spec(SoulLogic.get(player), HollowTier.ADJUCHAS));
        helper.assertTrue(beam != null, "the Cero did not gather");
        beam.fireNow();
    }

    /** This Kidō cast by name by a creative Shinigami at Kidō 40, who knows all of them and never misfires. */
    private static void kido(GameTestHelper helper, double z, ResourceKey<KidoSpell> key) {
        Player player = shooter(helper, GameType.CREATIVE, KidoTests.shinigami(400.0F, 40), new Vec3(6.5, 2.0, z));
        KidoLogic.Refusal refusal = KidoEvents.cast(player, KidoTests.spells(helper).getHolderOrThrow(key).value(),
                KidoLogic.Form.NAME, HollowPlayerTests.now(helper), 1.0);
        helper.assertTrue(refusal == null, "refused: " + refusal);
    }

    /** Where the lanes' arrows are loosed from, in the world: before the walls, at the height of the eyes. */
    private static Vec3 arrowFrom(GameTestHelper helper, double z) {
        return abs(helper, 8.5, 3.5, z);
    }

    /** A third-tier Echt Quincy standing in the lane, to own its arrows. */
    private static Player quincy(GameTestHelper helper, double z) {
        return shooter(helper, GameType.SURVIVAL, QuincyArtsTests.thirdTier(), new Vec3(6.5, 2.0, z));
    }

    /** This Gintō spell cast from this spot by a third-tier Quincy, using up its tubes even where it comes undone. */
    private static void ginto(GameTestHelper helper, GintoSpell spell, Vec3 at) {
        Player player = shooter(helper, GameType.SURVIVAL, GintoTests.choosing(QuincyArtsTests.thirdTier(), spell), at);
        ItemStack tubes = GintoTests.tubes(player, spell.tubes());
        helper.assertTrue(GintoSpells.cast(player, SoulLogic.get(player), tubes) && tubes.isEmpty(), "not cast");
    }

    /**
     * Puts back every block a test set through it, and clears what the test left in its box, when the test passes or
     * fails: in 1.21.1 the framework does neither for a failed test, nor puts blocks back for a passed one.
     */
    private static final class Restore implements GameTestListener {
        private final GameTestHelper helper;
        private final Map<BlockPos, BlockState> before = new HashMap<>();

        private Restore(GameTestHelper helper) {
            this.helper = helper;
        }

        /** A restorer for this test, put back when it ends. */
        static Restore on(GameTestHelper helper) {
            Restore restore = new Restore(helper);
            helper.testInfo.addListener(restore);
            return restore;
        }

        /** Sets every block between these two corners of the box (inclusive) to this one, keeping what stood first. */
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
            for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
                before.putIfAbsent(helper.absolutePos(pos), helper.getBlockState(pos));
                helper.setBlock(pos, block);
            }
        }

        /** Discards every entity in and about the box but the players. */
        void clearEntities() {
            for (Entity entity : helper.getLevel().getEntities((Entity) null, helper.getBounds().inflate(1.0),
                    other -> !(other instanceof Player))) {
                entity.discard();
            }
        }

        private void restore() {
            clearEntities();
            before.forEach((pos, state) -> helper.getLevel().setBlockAndUpdate(pos, state));
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {
        }

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            restore();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            restore();
        }

        @Override
        public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
        }
    }

    // ---- The arts ----

    /**
     * Every spirit art that meets blocks, a row every half second, fired at a Sekkiseki wall and at a stone one: at the
     * Sekkiseki it comes undone in a spray of pale light and a hiss, and at the stone it does neither. The misses are
     * gathered, so one failure names every art the rules miss.
     */
    @GameTest(template = "field", batch = "sekkiseki", timeoutTicks = 240)
    public static void everySpiritArtComesUndoneAtSekkisekiButNotAtStone(GameTestHelper helper) {
        Restore restore = Restore.on(helper);
        ServerPlayer watcher = watcher(helper);
        AABB sekkiseki = box(helper, 10, 1, 0, 14, 7, 7);
        AABB stone = box(helper, 10, 1, 9, 14, 7, 16);
        List<String> missed = new ArrayList<>();
        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int tick = FIRST_ROW + ROW_TICKS * i;
            helper.runAtTickTime(tick, () -> {
                restore.clearEntities();
                // Built again for each row: the stone a Letzt Pfeil carved comes back, and the obsidian stops it.
                restore.fill(11, 2, 1, 11, 5, 5, ModBlocks.SEKKISEKI.get());
                restore.fill(11, 2, 10, 11, 5, 14, Blocks.STONE);
                restore.fill(13, 2, 10, 13, 5, 14, Blocks.OBSIDIAN);
                TestPlayers.takeEffects(watcher);
                for (double z : LANES) {
                    try {
                        row.firing().fire(helper, z);
                    } catch (RuntimeException e) {
                        missed.add(row.art() + ": " + e.getMessage());
                    }
                }
            });
            helper.runAtTickTime(tick + COUNT_AT, () -> {
                List<Packet<?>> effects = TestPlayers.takeEffects(watcher);
                int[] atSekkiseki = fizzles(effects, sekkiseki);
                int[] atStone = fizzles(effects, stone);
                if (atSekkiseki[0] == 0 || atSekkiseki[1] == 0) {
                    missed.add(row.art() + " did not come undone at Sekkiseki");
                }
                if (atStone[0] + atStone[1] > 0) {
                    missed.add(row.art() + " came undone at stone");
                }
            });
        }
        helper.runAtTickTime(FIRST_ROW + ROW_TICKS * rows.size(), () -> {
            helper.assertTrue(missed.isEmpty(), "missed: " + missed);
            helper.succeed();
        });
    }

    /**
     * A crescent cuts the dirt and the cow before a low Sekkiseki wall and comes undone at it: the cow just behind the
     * wall, which its last sweep would have reached, is not touched, and neither is the wall.
     */
    @GameTest(template = "field", batch = "sekkiseki", timeoutTicks = 60)
    public static void aGetsugaComesUndoneAtSekkisekiAndSparesWhatIsBehind(GameTestHelper helper) {
        Restore restore = Restore.on(helper);
        restore.fill(5, 2, 8, 5, 2, 8, Blocks.DIRT);
        restore.fill(11, 2, 7, 11, 3, 9, ModBlocks.SEKKISEKI.get());
        Cow before = HollowPlayerTests.placed(helper, EntityType.COW, 9, 8);
        Cow behind = HollowPlayerTests.placed(helper, EntityType.COW, 12, 8);
        Player owner = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 400.0F));
        // From x 1.5, 1.6 a tick: the sixth tick, from x 9.5, meets the wall at 11.0, short of the cow behind (12.5).
        GetsugaTensho crescent = GetsugaTensho.launch(helper.getLevel(), owner, abs(helper, 0.5, 2.5, 8.5), EAST, 0.0F,
                4.0F, 9, ZanpakutoTests.asauchi(), 0xFFFFFF);
        helper.succeedWhen(() -> {
            helper.assertTrue(crescent.isRemoved(), "the crescent still flies");
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 8)).isAir(), "the dirt before the wall was not cut");
            helper.assertTrue(before.getHealth() < before.getMaxHealth(), "the cow before the wall was not cut");
            helper.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the cow behind the wall was cut");
            helper.assertTrue(helper.getBlockState(new BlockPos(11, 2, 8)).is(ModBlocks.SEKKISEKI.get()), "the wall was cut");
        });
    }

    /**
     * A Cero ends at Sekkiseki with no blast and no carving, sparing the golem beside it and the dirt above it, where
     * at stone the same Cero blasts both.
     */
    @GameTest(template = "field", batch = "sekkiseki")
    public static void aCeroEndsAtSekkisekiWithNoBlastOrCarving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Restore restore = Restore.on(helper);
        restore.fill(11, 3, 4, 11, 3, 4, ModBlocks.SEKKISEKI.get());
        restore.fill(11, 4, 4, 11, 4, 4, Blocks.DIRT);
        restore.fill(11, 3, 11, 11, 3, 11, Blocks.STONE);
        restore.fill(11, 4, 11, 11, 4, 11, Blocks.DIRT);
        // 2 blocks off each beam's line: outside its width, inside its blast.
        IronGolem spared = HollowPlayerTests.placed(helper, EntityType.IRON_GOLEM, 10, 6);
        IronGolem blasted = HollowPlayerTests.placed(helper, EntityType.IRON_GOLEM, 10, 13);
        Player shooter = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, new Vec3(1.5, 2.0, 8.5));
        CeroBeam.Spec spec = new CeroBeam.Spec(10.0F, 16.0, 0.5F, 3.0F, 0.5F, 8, 0.0F, 0xFFFFFF, 1, 0, true);
        CeroBeam.Kind kind = CeroBeam.Kind.CERO;

        CeroBeam.Shot undone = CeroBeam.fire(level, shooter, null, abs(helper, 2.5, 3.5, 4.5), EAST, spec, kind);
        helper.assertTrue(undone.negated() && !undone.hitBlock() && !undone.blocked() && undone.blocksBroken() == 0,
                "the Cero at Sekkiseki: " + undone);
        helper.assertTrue(undone.to().distanceTo(abs(helper, 11.0, 3.5, 4.5)) < EPSILON, "it ended at " + undone.to());
        helper.assertTrue(spared.getHealth() == spared.getMaxHealth(), "a blast at Sekkiseki hurt the golem");
        helper.assertTrue(helper.getBlockState(new BlockPos(11, 4, 4)).is(Blocks.DIRT), "the dirt over Sekkiseki was carved");

        CeroBeam.Shot burst = CeroBeam.fire(level, shooter, null, abs(helper, 2.5, 3.5, 11.5), EAST, spec, kind);
        helper.assertTrue(burst.hitBlock() && !burst.negated() && burst.blocksBroken() >= 1, "the Cero at stone: " + burst);
        helper.assertTrue(blasted.getHealth() < blasted.getMaxHealth(), "the blast at stone missed its golem");
        helper.assertTrue(helper.getBlockState(new BlockPos(11, 4, 11)).isAir(), "the dirt over the stone was not carved");
        helper.succeed();
    }

    /**
     * Quincy arrows come undone at Sekkiseki: a piercing Heilig Pfeil strikes the cow before the wall and goes no
     * further, a Seele Schneider drops its hilt instead of sticking, and no blade is planted on Sekkiseki ground.
     */
    @GameTest(template = "field", batch = "sekkiseki", timeoutTicks = 60)
    public static void quincyArrowsComeUndoneAtSekkiseki(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Restore restore = Restore.on(helper);
        restore.fill(11, 2, 3, 11, 4, 13, ModBlocks.SEKKISEKI.get());
        restore.fill(4, 1, 8, 4, 1, 8, ModBlocks.SEKKISEKI.get());
        Cow before = HollowPlayerTests.placed(helper, EntityType.COW, 10, 4);
        Cow behind = HollowPlayerTests.placed(helper, EntityType.COW, 12, 4);
        Player archer = QuincyTests.archer(helper, QuincyArtsTests.thirdTier(), 2.5, 8.5);
        ServerPlayer watcher = watcher(helper);

        BlockPos sekkiseki = helper.absolutePos(new BlockPos(4, 1, 8));
        helper.assertFalse(SeeleSchneiderPfeil.canPlantAt(level, sekkiseki), "a blade may be planted on Sekkiseki");
        helper.assertTrue(SeeleSchneiderPfeil.plant(level, archer, sekkiseki) == null, "a blade was planted on Sekkiseki");
        helper.assertTrue(SeeleSchneiderPfeil.canPlantAt(level, helper.absolutePos(new BlockPos(5, 1, 8))),
                "no blade may be planted on stone");
        // Pierce 1: the cow before the wall makes vanilla forget the wall that tick, and the arrow's own second look
        // finds it.
        HeiligPfeil heilig = QuincyTests.loose(helper, archer, abs(helper, 8.5, 2.5, 4.5), EAST, 4.0F, 1, 3.0F);
        SeeleSchneiderPfeil seele = SeeleSchneiderPfeil.launch(level, archer, abs(helper, 6.5, 3.5, 11.5), EAST, 1.0F, 0,
                3.0F);
        List<Packet<?>> seen = new ArrayList<>();
        helper.succeedWhen(() -> {
            seen.addAll(TestPlayers.takeEffects(watcher));
            helper.assertTrue(heilig.isRemoved() && seele.isRemoved(), "an arrow still flies");
            helper.assertTrue(before.getHealth() < before.getMaxHealth(), "the Heilig Pfeil missed the cow before the wall");
            helper.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the cow behind the wall was struck");
            helper.assertTrue(fizzles(seen, box(helper, 10, 1, 2, 12, 5, 7))[0] > 0, "the Heilig Pfeil did not come undone");
            helper.assertTrue(fizzles(seen, box(helper, 10, 2, 9, 12, 6, 14))[0] > 0, "the blade did not come undone");
            helper.assertFalse(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                    item -> item.getItem().is(ModItems.SEELE_SCHNEIDER.get())).isEmpty(), "the Seele Schneider left no hilt");
        });
    }

    // ---- Sensing and gates ----

    /**
     * Pesquisa does not feel a Plus with Sekkiseki on the line from the caster's eyes, and still feels one behind stone;
     * stone set where the Sekkiseki stood hides nothing.
     */
    @GameTest(template = "field", batch = "sekkiseki")
    public static void pesquisaFeelsNoSoulThroughSekkiseki(GameTestHelper helper) {
        Restore restore = Restore.on(helper);
        long now = HollowPlayerTests.now(helper);
        Player caster = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, 400.0F, new Vec3(8.5, 2.0, 2.5));
        restore.fill(0, 2, 7, 7, 5, 7, ModBlocks.SEKKISEKI.get());
        restore.fill(8, 2, 7, 15, 5, 7, Blocks.STONE);
        // From the eyes at (8.5, 3.62, 2.5) the line to the hidden one crosses the wall at x 6.0 to 5.4, to the felt
        // one at x 11.0 to 11.6.
        PlusSpirit hidden = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 3, 11);
        hidden.soul();
        PlusSpirit felt = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 13, 11);
        felt.soul();
        List<LivingEntity> sensed = Pesquisa.sense(caster, now).sensed();
        helper.assertTrue(sensed.contains(felt) && !sensed.contains(hidden), "Pesquisa felt " + sensed);
        helper.assertFalse(Pesquisa.senses(caster, hidden, now), "Pesquisa felt the Plus behind Sekkiseki");
        restore.fill(0, 2, 7, 7, 5, 7, Blocks.STONE);
        helper.assertTrue(Pesquisa.senses(caster, hidden, now), "Pesquisa missed the Plus behind stone");
        helper.succeed();
    }

    /** A mock in this game mode with this soul at the gate spot, facing south, a nameless Asauchi in hand. */
    private static Player opener(GameTestHelper helper, GameType mode, SoulData data) {
        Player player = helper.makeMockPlayer(mode);
        player.setData(SoulAttachments.SOUL, data);
        Vec3 at = helper.absoluteVec(GATE_SPOT);
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        return player;
    }

    /** Asserts the rules' answers at the gate spot now: a Shinigami's Senkaimon, in survival and creative, and a Garganta. */
    private static void gates(GameTestHelper helper, long now, String here, @Nullable TravelLogic.Refusal senkaimon,
                              @Nullable TravelLogic.Refusal garganta) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            Player shinigami = opener(helper, mode, ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F));
            GargantaTests.refuses(helper, TravelLogic.mayOpenSenkaimon(shinigami, SoulLogic.get(shinigami),
                    Level.OVERWORLD, now), senkaimon, "a " + mode.getName() + " Senkaimon with " + here);
        }
        Player arrancar = opener(helper, GameType.SURVIVAL, GargantaTests.arrancar());
        GargantaTests.refuses(helper, TravelLogic.mayOpenGarganta(arrancar, SoulLogic.get(arrancar), Level.OVERWORLD,
                now), garganta, "a Garganta with " + here);
    }

    /**
     * No Senkaimon or Garganta opens where its box would hold Sekkiseki, creative or not, and a real Shinigami's turn is
     * refused with its line; stone there, or Sekkiseki only under the gate or against its face, refuses nothing.
     */
    @GameTest(template = "field", batch = "sekkiseki")
    public static void noGateOpensIntoSekkiseki(GameTestHelper helper) {
        Restore restore = Restore.on(helper);
        long now = HollowPlayerTests.now(helper);
        // The Senkaimon would fill x 7 to 10, y 2 to 6, z 4 to 5; the Garganta x 7.5 to 9.5, y 2 to 6, z 3.3 to 5.3.
        gates(helper, now, "nothing there", null, null);
        restore.fill(8, 4, 4, 8, 4, 4, ModBlocks.SEKKISEKI.get());
        gates(helper, now, "Sekkiseki in both", TravelLogic.Refusal.SEKKISEKI, TravelLogic.Refusal.SEKKISEKI);
        restore.fill(8, 4, 4, 8, 4, 4, Blocks.STONE);
        gates(helper, now, "stone in both", null, null);
        restore.fill(8, 4, 4, 8, 4, 4, Blocks.AIR);
        restore.fill(8, 4, 5, 8, 4, 5, ModBlocks.SEKKISEKI.get());
        gates(helper, now, "Sekkiseki against the Senkaimon's face", null, TravelLogic.Refusal.SEKKISEKI);
        restore.fill(8, 4, 5, 8, 4, 5, Blocks.AIR);
        restore.fill(8, 1, 4, 8, 1, 4, ModBlocks.SEKKISEKI.get());
        gates(helper, now, "a Sekkiseki floor", null, null);

        restore.fill(8, 4, 4, 8, 4, 4, ModBlocks.SEKKISEKI.get());
        ServerPlayer joined = TestPlayers.join(helper, GameType.SURVIVAL, GATE_SPOT);
        joined.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, 800.0F));
        joined.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        GargantaTests.refuses(helper, TravelEvents.onKey(joined, GateKeyPayload.GateKey.PRESS, now),
                TravelLogic.Refusal.SEKKISEKI, "a real Shinigami's turn");
        helper.assertTrue(TravelTests.lines(joined, "message.bleach.senkaimon.sekkiseki") == 1, "the Shinigami was not told");
        helper.assertTrue("message.bleach.garganta.sekkiseki".equals(
                TravelLogic.Refusal.SEKKISEKI.messageKey(TravelLogic.Gate.GARGANTA)), "the Garganta's line");
        helper.succeed();
    }
}
