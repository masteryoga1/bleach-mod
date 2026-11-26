package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import io.github.masteryoga1.bleach.soul.BankaiForms;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.BankaiLook;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Hihiō Zabimaru, Zabimaru's Bankai, whose art is the serpent. The numbers are the server config's defaults:
 * a reach of 15 blocks (the base 3, Shikai's 3 and the serpent's 9), drawn back to 3 for a second after each lash.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HihioZabimaruTests {
    private static final double EPSILON = 1.0E-3;

    private HihioZabimaruTests() {
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    private static double reach(Player player) {
        return player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    /** The serpent's own reach on the player, or NaN without it. */
    private static double serpentReach(Player player) {
        AttributeModifier modifier = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE)
                .getModifier(BankaiForms.SERPENT_REACH_ID);
        return modifier == null ? Double.NaN : modifier.amount();
    }

    /** The drawn-back reach on the player, or NaN without it. */
    private static double retract(Player player) {
        AttributeModifier modifier =
                player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getModifier(ShikaiEvents.RETRACT_ID);
        return modifier == null ? Double.NaN : modifier.amount();
    }

    /** The one lash within a block of this point, or null for none or more than one. */
    private static ZabimaruLash lashAt(GameTestHelper helper, Vec3 at) {
        List<ZabimaruLash> lashes = helper.getLevel().getEntitiesOfClass(ZabimaruLash.class, AABB.ofSize(at, 2.0, 2.0, 2.0));
        return lashes.size() == 1 ? lashes.get(0) : null;
    }

    /**
     * Zabimaru's Shikai reaches 6 blocks and Hihiō Zabimaru 15, the serpent's 9 riding its owner and never the blade.
     * A blow nine blocks off brings out the serpent, out its own 12 ticks and not Shikai's 8; its reach draws back to 3
     * at once and it lashes no more until it is back, a second later, when the 15 blocks are back too. Zabimaru's Shikai
     * lash is no serpent, and Tensa Zangetsu has no serpent's reach. The blade's item model draws model 5 as Hihiō
     * Zabimaru, its last override, and its model and texture are shipped.
     */
    @GameTest(template = "field", timeoutTicks = 400)
    public static void hihioZabimaruReachesFarAndDrawsBack(GameTestHelper helper) {
        ServerPlayer renji = BankaiReleaseTests.joined(helper, ZanpakutoSpirits.ZABIMARU, new Vec3(2.5, 2.0, 8.5));
        ItemStack blade = renji.getMainHandItem();
        Pig far = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 11, 2, 8));
        Vec3 struck = far.getBoundingBox().getCenter();
        BankaiReleaseTests.released(helper, renji, ShikaiEvents.Result.RELEASED, "Shikai");
        int[] from = {renji.tickCount};
        ZabimaruLash[] serpent = new ZabimaruLash[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, renji.tickCount >= from[0] + 2 && near(reach(renji), 6.0),
                        "in Shikai Zabimaru reaches " + reach(renji)))
                .thenExecute(() -> {
                    helper.assertTrue(Double.isNaN(serpentReach(renji)), "Zabimaru's Shikai has the serpent's reach");
                    renji.tickCount += ShikaiEvents.SETTLE_TICKS;
                    BankaiReleaseTests.released(helper, renji, ShikaiEvents.Result.BANKAI, "Hihiō Zabimaru");
                    helper.assertTrue(ZanpakutoTests.model(blade) == 5 && near(serpentReach(renji), 9.0)
                                    && Double.isNaN(BankaiReleaseTests.modifier(blade, Attributes.ENTITY_INTERACTION_RANGE,
                                    BankaiForms.SERPENT_REACH_ID)),
                            "Hihiō Zabimaru has model " + ZanpakutoTests.model(blade) + " and its owner a serpent's reach"
                                    + " of " + serpentReach(renji));
                    from[0] = renji.tickCount;
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, renji.tickCount >= from[0] + 2 && near(reach(renji), 15.0),
                        "in Hihiō Zabimaru its owner reaches " + reach(renji)))
                .thenExecute(() -> {
                    helper.assertTrue(ShikaiEvents.lash(renji, far, blade), "a blow nine blocks off brought out no serpent");
                    serpent[0] = lashAt(helper, struck);
                    helper.assertTrue(serpent[0] != null && serpent[0].isSerpent() && serpent[0].owner() == renji
                                    && serpent[0].lifeTicks() == 12,
                            "the lash nine blocks off is " + serpent[0]);
                    helper.assertTrue(near(reach(renji), 3.0) && Double.isNaN(serpentReach(renji))
                                    && near(retract(renji), -3.0),
                            "as the serpent draws back its owner reaches " + reach(renji) + ", the serpent's own "
                                    + serpentReach(renji) + " and drawn back " + retract(renji));
                    helper.assertFalse(ShikaiEvents.lash(renji, far, blade), "the serpent lashed again as it drew back");
                })
                // Still out at 10 ticks, past Shikai's 8, and called in at 12: a removed lash stops counting.
                .thenWaitUntil(() -> GateTests.patiently(helper, serpent[0].age() >= 10 || serpent[0].isRemoved(),
                        "the serpent has been out only " + serpent[0].age() + " ticks"))
                .thenExecute(() -> helper.assertFalse(serpent[0].isRemoved(),
                        "the serpent was called in at age " + serpent[0].age()))
                .thenWaitUntil(() -> GateTests.patiently(helper, serpent[0].isRemoved(),
                        "the serpent is still out at age " + serpent[0].age()))
                .thenExecute(() -> helper.assertTrue(serpent[0].age() == 12,
                        "the serpent was out " + serpent[0].age() + " ticks, not 12"))
                .thenWaitUntil(() -> GateTests.patiently(helper, near(reach(renji), 15.0) && Double.isNaN(retract(renji)),
                        "a second after its lash Hihiō Zabimaru's owner reaches " + reach(renji)))
                .thenExecute(() -> {
                    Player shikai = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
                    BankaiReleaseTests.released(helper, shikai, ShikaiEvents.Result.RELEASED, "Zabimaru's Shikai");
                    shikai.setPos(helper.absoluteVec(new Vec3(2.5, 2.0, 4.5)));
                    Pig other = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 9, 2, 4));
                    helper.assertTrue(ShikaiEvents.lash(shikai, other, shikai.getMainHandItem()),
                            "Zabimaru's Shikai did not lash");
                    ZabimaruLash lash = lashAt(helper, other.getBoundingBox().getCenter());
                    helper.assertTrue(lash != null && !lash.isSerpent() && lash.lifeTicks() == ZabimaruLash.LIFE_TICKS,
                            "Zabimaru's Shikai lashed out " + lash);
                    Player ichigo = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
                    BankaiReleaseTests.toBankai(helper, ichigo);
                    helper.assertTrue(Double.isNaN(serpentReach(ichigo)), "Tensa Zangetsu gave its owner a serpent's reach");

                    ZanpakutoSpirit zabimaru = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU);
                    int hihio = ZanpakutoLogic.form(zabimaru, ZanpakutoStage.BANKAI).model();
                    String asauchi =
                            new String(BankaiFormTests.asset(helper, "models/item/asauchi.json"), StandardCharsets.UTF_8);
                    JsonArray overrides = JsonParser.parseString(asauchi).getAsJsonObject().getAsJsonArray("overrides");
                    JsonObject last = overrides.get(overrides.size() - 1).getAsJsonObject();
                    int model = last.getAsJsonObject("predicate").get("custom_model_data").getAsInt();
                    helper.assertTrue(hihio == 5 && model == hihio
                                    && "bleach:item/hihio_zabimaru".equals(last.get("model").getAsString()),
                            "Zabimaru's Bankai has model " + hihio + " and the Asauchi's last override is " + last);
                    BankaiFormTests.asset(helper, "models/item/hihio_zabimaru.json");
                    BankaiFormTests.asset(helper, "textures/item/hihio_zabimaru.png");
                    lash.discard();
                    other.discard();
                    far.discard();
                    helper.succeed();
                });
    }

    /**
     * Every end of Hihiō Zabimaru takes the serpent's reach off at once and calls its serpent in by its next tick, long
     * before its 12 ticks are out: the seal key, the Bankai ended as a revoke ends it, death, logging out, and the blade
     * put out of hand. Each end is tried on a soul that has not lashed, whose reach is the serpent's, and on one whose
     * serpent is out. Ended to Shikai, the blade is in Shikai and its reach still drawn back, as Shikai's would be.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void theSerpentGoesWithEveryEnd(GameTestHelper helper) {
        int ends = 5;
        ZabimaruLash[] serpents = new ZabimaruLash[ends];
        Pig[] pigs = new Pig[ends];
        for (int end = 0; end < ends; end++) {
            double z = 1.5 + 2.5 * end;
            Player reaching = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
            BankaiReleaseTests.toBankai(helper, reaching);
            helper.assertTrue(near(serpentReach(reaching), 9.0), "called on a mock, the serpent's reach did not come on");
            String how = end(helper, reaching, end);
            helper.assertTrue(Double.isNaN(serpentReach(reaching))
                            && !BankaiForms.serpentine(reaching, SoulLogic.get(reaching)),
                    how + " left the serpent's reach of " + serpentReach(reaching) + " on its owner");

            Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
            BankaiReleaseTests.toBankai(helper, renji);
            renji.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, z)));
            ItemStack blade = renji.getMainHandItem();
            pigs[end] = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(12.5, 2.0, z)));
            helper.assertTrue(ShikaiEvents.lash(renji, pigs[end], blade), "Hihiō Zabimaru did not lash before " + how);
            serpents[end] = lashAt(helper, pigs[end].getBoundingBox().getCenter());
            helper.assertTrue(serpents[end] != null && serpents[end].isSerpent(), "no serpent lashed out before " + how);
            end(helper, renji, end);
            helper.assertTrue(end != 1
                            || BankaiReleaseTests.stage(blade) == ZanpakutoStage.SHIKAI && near(retract(renji), -3.0),
                    "ended as a revoke ends it, the blade is " + BankaiReleaseTests.stage(blade) + " and drawn back "
                            + retract(renji));
        }
        helper.succeedWhen(() -> {
            for (int end = 0; end < ends; end++) {
                helper.assertTrue(serpents[end].isRemoved() && serpents[end].age() < ZabimaruLash.SERPENT_LIFE_TICKS,
                        "after end " + end + " the serpent is " + (serpents[end].isRemoved() ? "called in" : "still out")
                                + " at age " + serpents[end].age());
            }
            for (Pig pig : pigs) {
                pig.discard();
            }
        });
    }

    /** Ends this soul's Hihiō Zabimaru the way numbered, and names it. */
    private static String end(GameTestHelper helper, Player renji, int end) {
        SoulData data = SoulLogic.get(renji);
        return switch (end) {
            case 0 -> {
                helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED, "the seal key did not seal");
                yield "the seal key";
            }
            case 1 -> {
                ShikaiEvents.endBankai(renji, data, ShikaiEvents.BANKAI_ENDED);
                yield "the Bankai ended as a revoke ends it";
            }
            case 2 -> {
                // At no health, as a player is when its death is posted.
                renji.setHealth(0.0F);
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(renji, renji.damageSources().generic()));
                yield "death";
            }
            case 3 -> {
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(renji));
                yield "logging out";
            }
            default -> {
                renji.getInventory().setItem((renji.getInventory().selected + 1) % 9, renji.getMainHandItem());
                renji.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                // A mock never ticks: its next tick would do this.
                BankaiForms.keep(renji, data);
                yield "the blade out of hand";
            }
        };
    }

    /**
     * Hihiō Zabimaru's pelt is worn by its owner with it in his main hand in Bankai, and by nobody else: not by another
     * soul with a copy of it in hand, and not once it is sealed. The pelt goes with the model Zabimaru's file gives its
     * Bankai, and its texture and the serpent's bone segment are shipped. Every client draws the pelt by this same
     * reading of the held stack.
     */
    @GameTest(template = "empty")
    public static void thePeltIsWornOnlyWithOwnBladeInBankai(GameTestHelper helper) {
        Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
        ItemStack blade = renji.getMainHandItem();
        BankaiReleaseTests.toBankai(helper, renji);
        helper.assertTrue(BankaiLook.worn(renji) == BankaiLook.HIHIO_ZABIMARU,
                "in Hihiō Zabimaru its owner wears " + BankaiLook.worn(renji));
        Player other = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        other.setItemInHand(InteractionHand.MAIN_HAND, blade.copy());
        helper.assertTrue(BankaiLook.worn(other) == BankaiLook.NONE,
                "another soul with Hihiō Zabimaru in hand wears " + BankaiLook.worn(other));
        ZanpakutoSpirit zabimaru = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU);
        int hihio = ZanpakutoLogic.form(zabimaru, ZanpakutoStage.BANKAI).model();
        helper.assertTrue(BankaiLook.HIHIO_ZABIMARU.model() == hihio && ZanpakutoTests.model(blade) == hihio
                        && BankaiLook.forModel(hihio) == BankaiLook.HIHIO_ZABIMARU,
                "the pelt goes with model " + BankaiLook.HIHIO_ZABIMARU.model() + " and Zabimaru's file gives " + hihio);
        BankaiFormTests.asset(helper, "textures/entity/hihio_zabimaru_pelt.png");
        BankaiFormTests.asset(helper, "textures/entity/hihio_zabimaru_segment.png");
        helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED && BankaiLook.worn(renji) == BankaiLook.NONE,
                "sealed, its owner wears " + BankaiLook.worn(renji));
        helper.succeed();
    }
}
