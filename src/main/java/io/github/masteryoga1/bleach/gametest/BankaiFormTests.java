package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.soul.BankaiForms;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for what a Bankai's form brings its owner, as Tensa Zangetsu shows it. The numbers are the server config's
 * defaults.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankaiFormTests {
    private static final double EPSILON = 1.0E-3;

    private BankaiFormTests() {
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    /** What this player's own modifier of this kind adds as a share of the base, or NaN without one filed so. */
    private static double worn(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeModifier modifier = player.getAttribute(attribute).getModifier(id);
        return modifier == null || modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ? Double.NaN : modifier.amount();
    }

    /** Whether the player wears both of a Bankai of speed's modifiers, at the config's defaults. */
    private static boolean sped(Player player) {
        return near(worn(player, Attributes.MOVEMENT_SPEED, BankaiForms.SPEED_ID), 0.3)
                && near(worn(player, Attributes.ATTACK_SPEED, BankaiForms.ATTACK_SPEED_ID), 0.5);
    }

    /** Whether the player wears neither. */
    private static boolean unsped(Player player) {
        return player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(BankaiForms.SPEED_ID) == null
                && player.getAttribute(Attributes.ATTACK_SPEED).getModifier(BankaiForms.ATTACK_SPEED_ID) == null;
    }

    /** Whether the blade itself carries a modifier of a Bankai's art, which it never should. */
    private static boolean onBlade(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.modifier().id().equals(BankaiForms.SPEED_ID)
                        || entry.modifier().id().equals(BankaiForms.ATTACK_SPEED_ID));
    }

    /** This asset of the mod's, read from its own resources, as a server loads no assets; the test fails without it. */
    private static byte[] asset(GameTestHelper helper, String path) {
        try (InputStream in = BleachMod.class.getResourceAsStream("/assets/bleach/" + path)) {
            helper.assertTrue(in != null, "the mod's resources hold no assets/bleach/" + path);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Tensa Zangetsu takes the model Zangetsu's file gives its Bankai, with the config's numbers: half again Shikai's
     * damage, at Shikai's speed and reach. Its art rides its owner as he holds it, never the blade: he moves 30% of his
     * base speed faster and swings half again as fast. Zangetsu's Shikai brings none of it, and nor does Hihiō
     * Zabimaru, whose art is another; stepped down, it all goes. The blade's item model draws that model as Tensa
     * Zangetsu among overrides that rise, as the highest number met wins, and its model and texture are shipped.
     */
    @GameTest(template = "empty")
    public static void tensaZangetsuTakesItsOwnFormAndSpeed(GameTestHelper helper) {
        Player ichigo = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(ichigo);
        ItemStack blade = ichigo.getMainHandItem();
        AttributeInstance movement = ichigo.getAttribute(Attributes.MOVEMENT_SPEED);
        BankaiReleaseTests.released(helper, ichigo, ShikaiEvents.Result.RELEASED, "Shikai");
        helper.assertTrue(BankaiForms.held(ichigo, data).isEmpty() && unsped(ichigo),
                "Zangetsu's Shikai holds " + BankaiForms.held(ichigo, data) + " and its owner is sped");
        double walking = movement.getValue();

        ichigo.tickCount += ShikaiEvents.SETTLE_TICKS;
        BankaiReleaseTests.released(helper, ichigo, ShikaiEvents.Result.BANKAI, "Tensa Zangetsu");
        double swing = BankaiReleaseTests.modifier(blade, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID);
        double reach = BankaiReleaseTests.modifier(blade, Attributes.ENTITY_INTERACTION_RANGE, ZanpakutoItem.REACH_ID);
        helper.assertTrue(ZanpakutoTests.model(blade) == 4 && near(BankaiReleaseTests.damageOf(blade), 12.5)
                        && near(swing, 1.2 - 4.0) && near(reach, 0.5) && !onBlade(blade),
                "Tensa Zangetsu has model " + ZanpakutoTests.model(blade) + ", damage " + BankaiReleaseTests.damageOf(blade)
                        + ", speed " + swing + " and reach " + reach + (onBlade(blade) ? ", and its art on the blade" : ""));
        helper.assertTrue(BankaiForms.held(ichigo, data).equals(Optional.of(ZanpakutoSpirit.BankaiAbility.SPEED))
                        && sped(ichigo) && near(movement.getValue() - walking, 0.3 * movement.getBaseValue()),
                "in Tensa Zangetsu the soul holds " + BankaiForms.held(ichigo, data) + ", wears "
                        + worn(ichigo, Attributes.MOVEMENT_SPEED, BankaiForms.SPEED_ID) + " and "
                        + worn(ichigo, Attributes.ATTACK_SPEED, BankaiForms.ATTACK_SPEED_ID) + ", and walks at "
                        + movement.getValue() + " from " + walking);

        helper.assertTrue(ShikaiEvents.seal(ichigo) == ShikaiEvents.Result.STEPPED_DOWN && ZanpakutoTests.model(blade) == 1
                        && unsped(ichigo) && BankaiForms.held(ichigo, data).isEmpty(),
                "stepped down, the blade has model " + ZanpakutoTests.model(blade) + " and the soul holds "
                        + BankaiForms.held(ichigo, data) + (unsped(ichigo) ? "" : ", sped"));

        Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
        BankaiReleaseTests.toBankai(helper, renji);
        helper.assertTrue(BankaiForms.held(renji, SoulLogic.get(renji))
                        .equals(Optional.of(ZanpakutoSpirit.BankaiAbility.SERPENT)) && unsped(renji),
                "Hihiō Zabimaru holds " + BankaiForms.held(renji, SoulLogic.get(renji)) + " and its owner is sped");

        ZanpakutoSpirit zangetsu = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU);
        int tensa = ZanpakutoLogic.form(zangetsu, ZanpakutoStage.BANKAI).model();
        String asauchi = new String(asset(helper, "models/item/asauchi.json"), StandardCharsets.UTF_8);
        int last = -1;
        boolean rising = true;
        String drawn = "nothing";
        for (JsonElement override : JsonParser.parseString(asauchi).getAsJsonObject().getAsJsonArray("overrides")) {
            JsonObject entry = override.getAsJsonObject();
            int model = entry.getAsJsonObject("predicate").get("custom_model_data").getAsInt();
            rising &= model > last;
            last = model;
            if (model == tensa) {
                drawn = entry.get("model").getAsString();
            }
        }
        helper.assertTrue(rising && "bleach:item/tensa_zangetsu".equals(drawn),
                "the Asauchi's overrides " + (rising ? "rise" : "do not rise") + " and draw model " + tensa + " as " + drawn);
        asset(helper, "models/item/tensa_zangetsu.json");
        asset(helper, "textures/item/tensa_zangetsu.png");
        helper.succeed();
    }

    /**
     * Every end of Tensa Zangetsu takes its speed off: the seal key's step down, death and logging out, at once. Another's
     * Tensa Zangetsu in hand gives the soul none, nor does its own once its soul holds another spirit, nor a Shikai copy of
     * its own, and its own puts it back. Held out of hand, it is off by the owner's next tick while the Bankai itself waits
     * out its second away, back on with the blade in hand, and gone with the Bankai once it lapses. Held, the joined soul
     * swings at 1.8 a second: its blade's own 1.2, half again.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theSpeedGoesWithEveryEnd(GameTestHelper helper) {
        for (int end = 0; end < 3; end++) {
            Player ichigo = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
            BankaiReleaseTests.toBankai(helper, ichigo);
            helper.assertTrue(sped(ichigo), "called on a mock, Tensa Zangetsu's speed did not come on at once");
            String how = switch (end) {
                case 0 -> {
                    ShikaiEvents.Result result = ShikaiEvents.seal(ichigo);
                    helper.assertTrue(result == ShikaiEvents.Result.STEPPED_DOWN, "the seal key gave " + result);
                    yield "the seal key";
                }
                case 1 -> {
                    // At no health, as a player is when its death is posted.
                    ichigo.setHealth(0.0F);
                    NeoForge.EVENT_BUS.post(new LivingDeathEvent(ichigo, ichigo.damageSources().generic()));
                    yield "death";
                }
                default -> {
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(ichigo));
                    yield "logging out";
                }
            };
            helper.assertTrue(unsped(ichigo) && !SoulLogic.get(ichigo).isBankaiActive(),
                    how + " left Tensa Zangetsu's speed on its owner");
        }

        ServerPlayer ichigo = BankaiReleaseTests.joined(helper, ZanpakutoSpirits.ZANGETSU, new Vec3(1.5, 1.0, 1.5));
        SoulData data = SoulLogic.get(ichigo);
        ItemStack blade = ichigo.getMainHandItem();
        BankaiReleaseTests.toBankai(helper, ichigo);
        Player other = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
        BankaiReleaseTests.toBankai(helper, other);
        ItemStack others = other.getMainHandItem().copy();
        int spare = (ichigo.getInventory().selected + 1) % 9;
        int[] from = {ichigo.tickCount};
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, ichigo.tickCount >= from[0] + 2 && sped(ichigo)
                                && near(ichigo.getAttributeValue(Attributes.ATTACK_SPEED), 1.8),
                        "in Tensa Zangetsu the joined soul swings at " + ichigo.getAttributeValue(Attributes.ATTACK_SPEED)))
                .thenExecute(() -> {
                    ichigo.setItemInHand(InteractionHand.MAIN_HAND, others);
                    BankaiForms.keep(ichigo, data);
                    helper.assertTrue(unsped(ichigo), "another's Tensa Zangetsu in hand sped Ichigo");
                    // His own blade still holds Zangetsu, a spirit of speed, but his soul's spirit is now another.
                    ichigo.setItemInHand(InteractionHand.MAIN_HAND, blade);
                    data.setZanpakuto(data.zanpakuto().withSpirit(Optional.of(BleachMod.id("missing"))));
                    BankaiForms.keep(ichigo, data);
                    helper.assertTrue(unsped(ichigo), "his Tensa Zangetsu sped Ichigo once his soul held another spirit");
                    data.setZanpakuto(data.zanpakuto().withSpirit(Optional.of(ZanpakutoSpirits.ZANGETSU.location())));
                    // A Shikai copy of his own blade is his, but it is not his Bankai.
                    ItemStack shikai = blade.copy();
                    ShikaiEvents.toShikai(shikai, helper.getLevel());
                    ichigo.setItemInHand(InteractionHand.MAIN_HAND, shikai);
                    BankaiForms.keep(ichigo, data);
                    helper.assertTrue(unsped(ichigo), "a Shikai copy of his own blade in hand sped Ichigo");
                    ichigo.setItemInHand(InteractionHand.MAIN_HAND, blade);
                    BankaiForms.keep(ichigo, data);
                    helper.assertTrue(sped(ichigo), "his own Tensa Zangetsu back in hand did not speed Ichigo at once");
                    ichigo.getInventory().setItem(spare, blade);
                    ichigo.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    from[0] = ichigo.tickCount;
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, ichigo.tickCount >= from[0] + 2 && unsped(ichigo)
                                && data.isBankaiActive(),
                        "with Tensa Zangetsu out of hand its owner is " + (unsped(ichigo) ? "" : "still ") + "sped and "
                                + (data.isBankaiActive() ? "still" : "no longer") + " in Bankai"))
                .thenExecute(() -> {
                    ichigo.setItemInHand(InteractionHand.MAIN_HAND, blade);
                    ichigo.getInventory().setItem(spare, ItemStack.EMPTY);
                    from[0] = ichigo.tickCount;
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, ichigo.tickCount >= from[0] + 2 && sped(ichigo),
                        "Tensa Zangetsu back in hand did not speed its owner again"))
                .thenExecute(() -> {
                    ichigo.getInventory().setItem(spare, blade);
                    ichigo.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    from[0] = ichigo.tickCount;
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, ichigo.tickCount >= from[0] + 25, "Ichigo has not been"
                        + " watched 25 ticks yet"))
                .thenExecute(() -> helper.assertTrue(!data.isBankaiActive()
                                && BankaiReleaseTests.stage(blade) == ZanpakutoStage.SHIKAI && unsped(ichigo)
                                && BankaiReleaseTests.lastSaid(ichigo, ShikaiEvents.BANKAI_ENDED),
                        "left out of hand, Tensa Zangetsu is " + BankaiReleaseTests.stage(blade) + ", its owner "
                                + (unsped(ichigo) ? "unsped" : "sped") + ", and shown "
                                + BankaiReleaseTests.keys(TestPlayers.actionBar(ichigo))))
                .thenSucceed();
    }
}
