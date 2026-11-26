package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.soul.BankaiForms;
import io.github.masteryoga1.bleach.soul.FlashStep;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.BankaiLook;
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
    static byte[] asset(GameTestHelper helper, String path) {
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

    /** Asserts the flash step this soul would take now: how far, and what it costs against what Hohō alone makes it. */
    private static void assertShunpo(GameTestHelper helper, Player player, double distance, double share, String when) {
        SoulData data = SoulLogic.get(player);
        double plainCost = Math.max(1.0, ShinigamiLogic.flashStepCost(data) * share);
        helper.assertTrue(near(FlashStep.distanceFor(player, data), distance)
                        && near(FlashStep.costFor(player, data), plainCost),
                when + " a flash step goes " + FlashStep.distanceFor(player, data) + ", not " + distance + ", and costs "
                        + FlashStep.costFor(player, data) + ", not " + plainCost);
    }

    /**
     * In Tensa Zangetsu its owner flash steps half again as far, after Hohō's cap, so past Hohō's longest, for half
     * what Hohō alone makes it cost, never under 1: 12 blocks at Hohō 10 where Shikai's is 8, and 24 at Hohō 50 where
     * Hohō's longest is 16. Stepped down, and in Hihiō Zabimaru, whose art is another, a flash step is Hohō's alone.
     */
    @GameTest(template = "empty")
    public static void tensaZangetsuFlashStepsFurtherForLess(GameTestHelper helper) {
        Player ichigo = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(ichigo);
        data.setLevel(Stat.HOHO, 10);
        BankaiReleaseTests.released(helper, ichigo, ShikaiEvents.Result.RELEASED, "Shikai");
        assertShunpo(helper, ichigo, 8.0, 1.0, "in Shikai at Hohō 10");
        ichigo.tickCount += ShikaiEvents.SETTLE_TICKS;
        BankaiReleaseTests.released(helper, ichigo, ShikaiEvents.Result.BANKAI, "Tensa Zangetsu");
        assertShunpo(helper, ichigo, 12.0, 0.5, "in Tensa Zangetsu at Hohō 10");
        data.setLevel(Stat.HOHO, 50);
        assertShunpo(helper, ichigo, 24.0, 0.5, "in Tensa Zangetsu at Hohō 50");
        // A small soul's step, which half would put under 1, costs 1.
        float most = data.maxReiryoku();
        data.setMaxReiryoku(40.0F);
        helper.assertTrue(ShinigamiLogic.flashStepCost(data) * 0.5F < 1.0F && FlashStep.costFor(ichigo, data) == 1.0F,
                "a small soul's flash step in Tensa Zangetsu costs " + FlashStep.costFor(ichigo, data) + ", not 1");
        data.setMaxReiryoku(most);
        helper.assertTrue(ShikaiEvents.seal(ichigo) == ShikaiEvents.Result.STEPPED_DOWN, "the seal key did not step down");
        assertShunpo(helper, ichigo, 16.0, 1.0, "stepped down at Hohō 50");

        Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
        SoulLogic.get(renji).setLevel(Stat.HOHO, 10);
        BankaiReleaseTests.toBankai(helper, renji);
        assertShunpo(helper, renji, 8.0, 1.0, "in Hihiō Zabimaru at Hohō 10");
        helper.succeed();
    }

    /**
     * Has this joined soul flash step on the ground at full reiryoku, steering this way, and asserts the step went more
     * than {@code over} and at most {@code most} blocks along the ground for {@code cost}. A refused step spends nothing,
     * so fails.
     */
    private static void assertStepped(GameTestHelper helper, ServerPlayer player, float forward, double over,
            double most, double cost, String when) {
        SoulData data = SoulLogic.get(player);
        data.setReiryoku(data.maxReiryoku());
        Vec3 start = player.position();
        player.setOnGround(true);
        FlashStep.perform(player, forward, 0.0F);
        double moved = Math.hypot(player.getX() - start.x, player.getZ() - start.z);
        double spent = data.maxReiryoku() - data.reiryoku();
        helper.assertTrue(moved > over && moved <= most && Math.abs(spent - cost) < 0.01,
                when + " a flash step went " + moved + " for " + spent + ", not over " + over + " and at most " + most
                        + " for " + cost);
    }

    /**
     * A flash step taken in Tensa Zangetsu goes well past Hohō's 8 at Hohō 10, at most 12, for half Hohō's cost, as
     * {@link FlashStep#perform} takes it; stepped down to Shikai and the cooldown waited out, the next goes no further
     * than 8, at the full cost. On the field's open floor, facing along it, so neither step is cut short or refused.
     */
    @GameTest(template = "field", timeoutTicks = 400)
    public static void aBankaiFlashStepGoesFurtherForLess(GameTestHelper helper) {
        ServerPlayer ichigo = BankaiReleaseTests.joined(helper, ZanpakutoSpirits.ZANGETSU, new Vec3(1.5, 2.0, 1.5));
        SoulData data = SoulLogic.get(ichigo);
        data.setLevel(Stat.HOHO, 10);
        BankaiReleaseTests.toBankai(helper, ichigo);
        int from = ichigo.tickCount;
        long[] stepped = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, ichigo.tickCount >= from + 2 && sped(ichigo),
                        "the joined Ichigo has not been watched in Tensa Zangetsu yet"))
                .thenExecute(() -> {
                    double half = Math.max(1.0, ShinigamiLogic.flashStepCost(data) * 0.5);
                    // Well past Hohō's 8, so a step that went Hohō's way cannot pass.
                    assertStepped(helper, ichigo, 1.0F, 11.0, 12.5, half, "in Tensa Zangetsu");
                    helper.assertTrue(ShikaiEvents.seal(ichigo) == ShikaiEvents.Result.STEPPED_DOWN,
                            "the seal key did not step down");
                    stepped[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getGameTime() - stepped[0]
                                >= ShinigamiLogic.flashStepCooldownTicks(data), "the flash step is still cooling down"))
                // The field's floor runs 16 blocks, so back the way it came; a step of a block is the least that goes.
                .thenExecute(() -> assertStepped(helper, ichigo, -1.0F, 1.0, 8.0 + EPSILON,
                        ShinigamiLogic.flashStepCost(data), "stepped down to Shikai"))
                .thenSucceed();
    }

    /**
     * A Getsuga Tenshō loosed in Bankai is the black one with a red edge, in its blade's colour bits, and half again as
     * hard as the same soul's in Shikai, after the cap: at the cap of 60, 72 a named full swing out of Bankai, and 108
     * in it. It costs what it would in Shikai.
     */
    @GameTest(template = "empty")
    public static void aBankaiGetsugaTenshoIsBlackAndHarder(GameTestHelper helper) {
        Player shikai = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
        Player bankai = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
        BankaiReleaseTests.released(helper, shikai, ShikaiEvents.Result.RELEASED, "Shikai");
        BankaiReleaseTests.toBankai(helper, bankai);
        GetsugaTensho pale = null;
        GetsugaTensho black = null;
        try {
            float[] paid = new float[2];
            Player[] both = {shikai, bankai};
            for (int i = 0; i < both.length; i++) {
                SoulData data = SoulLogic.get(both[i]);
                data.setReiryoku(data.maxReiryoku());
                ShikaiEvents.GatherResult gather = ShikaiEvents.gather(both[i]);
                helper.assertTrue(gather == ShikaiEvents.GatherResult.GATHERING, "the gathering gave " + gather);
                paid[i] = data.reiryoku();
            }
            pale = ShikaiEvents.fire(shikai);
            black = ShikaiEvents.fire(bankai);
            paid[0] -= SoulLogic.get(shikai).reiryoku();
            paid[1] -= SoulLogic.get(bankai).reiryoku();
            // Mocks never tick, so the game time is the same at the gather and the fire: both are at no charge. Both
            // souls are the same.
            float plain = ZanpakutoLogic.getsugaDamage(SoulLogic.get(bankai), 0.0F);
            int colour = ZanpakutoItem.blade(bankai.getMainHandItem()).colour() & 0xFFFFFF;
            helper.assertTrue(pale != null && !pale.isBlack() && near(pale.damage(), plain),
                    "Zangetsu's Shikai loosed " + described(pale) + ", not a pale one of " + plain);
            helper.assertTrue(black != null && black.isBlack() && (black.colour() & 0xFFFFFF) == colour
                            && near(black.damage(), 1.5 * plain) && near(paid[1], paid[0]),
                    "Tensa Zangetsu loosed " + described(black) + ", not a black one of " + 1.5 * plain + " in "
                            + Integer.toHexString(colour) + ", for " + paid[1] + " against Shikai's " + paid[0]);
        } finally {
            for (GetsugaTensho crescent : new GetsugaTensho[] {pale, black}) {
                if (crescent != null) {
                    crescent.discard();
                }
            }
        }
        SoulData strongest = ZanpakutoTests.soul(SoulState.SHINIGAMI, 1.0E6F);
        strongest.setZanpakuto(strongest.zanpakuto().withGetsugaNamed(true));
        HollowPlayerTests.assertClose(helper, ZanpakutoLogic.getsugaDamage(strongest, 1.0F, false), 72.0,
                "a named full swing at the cap, out of Bankai");
        HollowPlayerTests.assertClose(helper, ZanpakutoLogic.getsugaDamage(strongest, 1.0F, true), 108.0,
                "a named full swing at the cap, in Bankai");
        helper.succeed();
    }

    /** What a Getsuga Tenshō is, for a failure: black or pale, how hard and in what colour, or nothing. */
    private static String described(GetsugaTensho crescent) {
        return crescent == null ? "nothing" : (crescent.isBlack() ? "a black one" : "a pale one") + " of "
                + crescent.damage() + " in " + Integer.toHexString(crescent.colour() & 0xFFFFFF);
    }

    /**
     * Tensa Zangetsu's coat is worn by its owner with it in his main hand in Bankai, and by nobody else: not by another
     * soul with a copy of it in hand, not with it only in the off hand, not in Hihiō Zabimaru, whose Bankai has no look
     * yet, not by a blade of its model out of Bankai, and not once it is stepped down to Shikai. The coat goes with the
     * model Zangetsu's file gives its Bankai. Every client draws the coat by this same reading of the held stack.
     */
    @GameTest(template = "empty")
    public static void theCoatIsWornOnlyWithOwnBladeInBankai(GameTestHelper helper) {
        Player ichigo = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
        ItemStack blade = ichigo.getMainHandItem();
        BankaiReleaseTests.toBankai(helper, ichigo);
        helper.assertTrue(BankaiLook.worn(ichigo) == BankaiLook.TENSA_ZANGETSU,
                "in Tensa Zangetsu its owner wears " + BankaiLook.worn(ichigo));

        Player other = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        other.setItemInHand(InteractionHand.MAIN_HAND, blade.copy());
        helper.assertTrue(BankaiLook.worn(other) == BankaiLook.NONE,
                "another soul with Tensa Zangetsu in hand wears " + BankaiLook.worn(other));

        ichigo.setItemInHand(InteractionHand.OFF_HAND, blade);
        ichigo.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(BankaiLook.worn(ichigo) == BankaiLook.NONE,
                "with Tensa Zangetsu in the off hand its owner wears " + BankaiLook.worn(ichigo));
        // Tensa Zangetsu's model on a blade that is not in Bankai, as a datapack's Shikai might give it.
        ItemStack staged = blade.copy();
        staged.set(ModComponents.ZANPAKUTO_BLADE.get(), ZanpakutoItem.blade(staged).withStage(ZanpakutoStage.SHIKAI));
        ichigo.setItemInHand(InteractionHand.MAIN_HAND, staged);
        helper.assertTrue(BankaiLook.worn(ichigo) == BankaiLook.NONE,
                "a blade of Tensa Zangetsu's model in Shikai gave its owner " + BankaiLook.worn(ichigo));
        ichigo.setItemInHand(InteractionHand.MAIN_HAND, blade);
        ichigo.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
        BankaiReleaseTests.toBankai(helper, renji);
        helper.assertTrue(BankaiLook.worn(renji) != BankaiLook.TENSA_ZANGETSU,
                "in Hihiō Zabimaru its owner wears Tensa Zangetsu's coat");

        ZanpakutoSpirit zangetsu = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU);
        int tensa = ZanpakutoLogic.form(zangetsu, ZanpakutoStage.BANKAI).model();
        helper.assertTrue(BankaiLook.TENSA_ZANGETSU.model() == tensa && ZanpakutoTests.model(blade) == tensa
                        && BankaiLook.forModel(tensa) == BankaiLook.TENSA_ZANGETSU
                        && BankaiLook.forModel(BankaiLook.NONE.model()) == BankaiLook.NONE,
                "the coat goes with model " + BankaiLook.TENSA_ZANGETSU.model() + ", the blade has "
                        + ZanpakutoTests.model(blade) + " and Zangetsu's file gives its Bankai " + tensa);

        helper.assertTrue(ShikaiEvents.seal(ichigo) == ShikaiEvents.Result.STEPPED_DOWN
                        && BankaiLook.worn(ichigo) == BankaiLook.NONE,
                "stepped down to Shikai, its owner wears " + BankaiLook.worn(ichigo));
        helper.succeed();
    }
}
