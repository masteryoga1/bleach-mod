package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.Menos;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.VastoLorde;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for a Vasto Lorde's body: its row and the rules that keep it apart from the Menos, its strength from the config,
 * and the tear a command opens for it. Each test asks only about its own Vasto Lorde and its own players, by object.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class VastoLordeTests {
    private static final float EPSILON = 1.0E-3F;
    /** Enough reiatsu to draw any kind that comes by chance. */
    private static final float STRONG = 10_000.0F;
    /** The tag that names this test's soul in a command. */
    private static final String TAG = "bleach_vasto_lorde_test";
    /** How far about the soul the ground must tick: past the furthest a tear opens, and the Vasto Lorde's fall. */
    private static final double REACH = 28.0;
    private static final int GIVE_UP_TICK = 290;

    private VastoLordeTests() {
    }

    /** The Vasto Lorde this soul's tear brought, within reach of it. */
    static List<VastoLorde> drawnBy(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(VastoLorde.class, player.getBoundingBox().inflate(48.0),
                vasto -> player.getUUID().equals(vasto.drawer()));
    }

    private static boolean said(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> GateTests.says(line, key, -1));
    }

    /**
     * The row: by name, the one Vasto Lorde kind, no Menos, never by chance but by a tear, and alone through it; no boss of
     * {@code /bleach boss}; never saved but summoned, and human-sized; the config's strength 400 and 16. Joined, it is no
     * Menos and counted as none, and released reiatsu never frightens it off; Negación may hold it. With stale strength,
     * its first tick puts the config's health and damage in, whole, over its armour 14, toughness 2 and reiatsu. A
     * starving Adjuchas at the most its devouring grows it to would eat a Spider Hollow but never it, and it outweighs any
     * Gillian's meal.
     */
    @GameTest(template = "field")
    public static void aVastoLordeIsAHumanSizedBossAndNoMenos(GameTestHelper helper) {
        HollowKind kind = HollowKind.VASTO_LORDE;
        helper.assertTrue(HollowKind.byName("vasto_lorde").equals(Optional.of(kind)), "no kind is named vasto_lorde");
        List<HollowKind> vasto = Arrays.stream(HollowKind.values()).filter(HollowKind::isVasto).toList();
        helper.assertTrue(vasto.equals(List.of(kind)) && !kind.isMenos() && kind.comesByTear() && kind.arrivalWeight == 0,
                "the Vasto Lorde kinds are " + vasto + ", or it is a Menos, comes by no tear or may come by chance");
        helper.assertFalse(HollowLogic.arrivalPool(STRONG).contains(kind), "a Vasto Lorde is in the arrival pool");
        int count = HollowLogic.groupSize(kind, STRONG, Integer.MAX_VALUE, 0.0);
        helper.assertTrue(count == 1, "a tear with all the room there is brings " + count + " Vasto Lorde");
        helper.assertTrue(BleachConfig.VASTO_LORDE_HEALTH.getDefault() == 400.0
                        && BleachConfig.VASTO_LORDE_DAMAGE.getDefault() == 16.0,
                "a Vasto Lorde's config defaults are " + BleachConfig.VASTO_LORDE_HEALTH.getDefault() + " health and "
                        + BleachConfig.VASTO_LORDE_DAMAGE.getDefault() + " damage");
        ServerLevel level = helper.getLevel();
        List<Component> boss = GateTests.run(level, "bleach boss vasto_lorde status");
        helper.assertTrue(said(boss, "commands.bleach.unknown_boss"), "/bleach boss knows a Vasto Lorde: "
                + boss.stream().map(Component::getString).toList());

        EntityType<VastoLorde> type = ModEntities.VASTO_LORDE.get();
        helper.assertTrue(ModEntities.hollowType(kind) == type, "the Vasto Lorde kind has the wrong entity type");
        helper.assertTrue(!type.canSerialize() && type.canSummon(), "a Vasto Lorde is saved, or cannot be summoned");
        EntityDimensions size = type.getDimensions();
        helper.assertTrue(Math.abs(size.width() - 0.7F) < EPSILON && Math.abs(size.height() - 2.0F) < EPSILON
                && Math.abs(size.eyeHeight() - 1.75F) < EPSILON, "a Vasto Lorde is " + size);

        // Read now and asserted only in the try whose finally takes every body away: the Menos count just before and
        // after the spawn, in one call, and its goals as it joined the world, before its wits are taken.
        int menos = Menos.aliveIn(level);
        VastoLorde body = helper.spawn(type, new Vec3(8.5, 2.0, 8.5));
        int menosWith = Menos.aliveIn(level);
        boolean frightened = ShriekerTests.frightens(body);
        body.removeFreeWill();
        // Stale strength, as a summon with data or an old config would leave: its first tick puts the config's in.
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
        body.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
        Adjuchas adjuchas = helper.spawnWithNoFreeWill(ModEntities.ADJUCHAS.get(), 3, 2, 3);
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 12, 2, 3);

        helper.runAfterDelay(1, () -> {
            try {
                // A Java instanceof between sibling classes does not compile, so the class is asked.
                helper.assertTrue(!Menos.class.isInstance(body) && menosWith == menos,
                        "a Vasto Lorde is a Menos, or is counted among them");
                helper.assertFalse(frightened, "released reiatsu frightens a Vasto Lorde");
                helper.assertTrue(Negacion.mayHold(body), "Negación may not hold a Vasto Lorde");
                double health = BleachConfig.get(BleachConfig.VASTO_LORDE_HEALTH);
                double damage = BleachConfig.get(BleachConfig.VASTO_LORDE_DAMAGE);
                helper.assertTrue(body.getAttributeBaseValue(Attributes.MAX_HEALTH) == health
                                && body.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == damage
                                && Math.abs(body.getHealth() - health) < EPSILON,
                        "after its first tick a Vasto Lorde has " + body.getHealth() + " of "
                                + body.getAttributeBaseValue(Attributes.MAX_HEALTH) + " health and "
                                + body.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + " damage");
                helper.assertTrue(body.getAttributeBaseValue(Attributes.ARMOR) == 14.0
                                && body.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS) == 2.0,
                        "a Vasto Lorde's hide is " + body.getAttributeBaseValue(Attributes.ARMOR) + " armour and "
                                + body.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS) + " toughness");
                float reiatsu = SoulLogic.reiatsu(body);
                helper.assertTrue(Math.abs(reiatsu - kind.reiatsu) < EPSILON, "a Vasto Lorde's reiatsu is " + reiatsu);

                SoulLogic.get(adjuchas).setMaxReiryoku(HollowKind.ADJUCHAS.reiatsu * HollowKind.ADJUCHAS.scaleMax * 2.0F);
                adjuchas.setHealth(1.0F);
                helper.assertTrue(adjuchas.isEdible(spider) && !adjuchas.isEdible(body),
                        "a starving Adjuchas at its fullest would eat the Spider Hollow " + adjuchas.isEdible(spider)
                                + ", the Vasto Lorde " + adjuchas.isEdible(body));
                float gillian = HollowKind.GILLIAN.reiatsu * HollowKind.GILLIAN.scaleMax * 2.0F;
                helper.assertTrue(reiatsu > gillian, "a Gillian at its fullest, " + gillian + ", would eat a Vasto Lorde");
            } finally {
                body.discard();
                adjuchas.discard();
                spider.discard();
            }
            helper.succeed();
        });
    }

    /**
     * {@code /bleach garganta <soul> vasto_lorde} opens one tear beside the soul carrying one Vasto Lorde and nothing else,
     * and it comes out hunting that soul. A batch of its own, as the Vasto Lorde lands 12 to 24 blocks off, outside the
     * box, where another test's souls could stand.
     */
    @GameTest(template = "field", batch = "vasto_lorde_garganta", timeoutTicks = 300)
    public static void aGargantaByCommandBringsOneVastoLordeAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = ShriekerTests.aware(helper, new Vec3(8.5, 2.0, 8.5));
        // Named by a tag: every test player has the same name.
        player.addTag(TAG);
        String garganta = "bleach garganta @a[tag=" + TAG + ",limit=1] vasto_lorde";
        Vec3 start = player.position();
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, start, REACH);
        Runnable clear = () -> {
            QuincyArtsTests.discardAll(BossLedgerTests.riftsFor(server, player));
            QuincyArtsTests.discardAll(drawnBy(level, player));
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            forced.clear();
        };
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("the Vasto Lorde never came out of its tear");
        });
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, HollowBaitTests.allTicking(level, start, REACH),
                        "the ground around the soul never started ticking"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    List<Component> lines = List.of();
                    // Open ground is sought at random; on the field's flat ground one of three tries finds it.
                    for (int i = 0; i < 3 && !said(lines, "commands.bleach.garganta.success"); i++) {
                        lines = GateTests.run(level, garganta);
                    }
                    helper.assertTrue(said(lines, "commands.bleach.garganta.success"),
                            "the Garganta said " + lines.stream().map(Component::getString).toList());
                    List<GargantaRift> rifts = BossLedgerTests.riftsFor(server, player);
                    helper.assertTrue(rifts.size() == 1 && rifts.get(0).pending().equals(List.of(HollowKind.VASTO_LORDE)),
                            "the command tore " + rifts.size() + " Garganta for the soul, the first carrying "
                                    + (rifts.isEmpty() ? List.of() : rifts.get(0).pending()));
                }, clear))
                .thenWaitUntil(() -> helper.assertFalse(drawnBy(level, player).isEmpty(),
                        "no Vasto Lorde has come out of the tear"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    List<VastoLorde> out = drawnBy(level, player);
                    helper.assertTrue(out.size() == 1 && out.get(0).getTarget() == player,
                            out.size() + " Vasto Lorde came, the first hunting " + out.get(0).getTarget());
                    clear.run();
                }, clear))
                .thenSucceed();
    }
}
