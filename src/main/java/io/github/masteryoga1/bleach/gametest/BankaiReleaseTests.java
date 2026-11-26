package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Bankai's release: called from Shikai on the release key once the soul has reached it, after the checks a
 * release makes; what it costs, how it looks and hits, and how it ends. The numbers are the server config's defaults.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankaiReleaseTests {
    private static final double EPSILON = 1.0E-3;
    private static final long DAY = 24_000L;

    private BankaiReleaseTests() {
    }

    /** A mock Shinigami (max 510) that has learned this spirit's name for the blade in its hand and reached its Bankai. */
    static Player reached(GameTestHelper helper, ResourceKey<ZanpakutoSpirit> key) {
        Player player = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        nameAndReach(helper, player, key);
        return player;
    }

    /** A joined Shinigami (max 510) that has learned this spirit's name for the blade in its hand and reached its Bankai. */
    static ServerPlayer joined(GameTestHelper helper, ResourceKey<ZanpakutoSpirit> key, Vec3 at) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, at);
        player.setData(SoulAttachments.SOUL, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        nameAndReach(helper, player, key);
        return player;
    }

    /** The player learns this spirit's name for a fresh Asauchi put in its main hand, then reaches its Bankai. */
    private static void nameAndReach(GameTestHelper helper, Player player, ResourceKey<ZanpakutoSpirit> key) {
        SoulData data = SoulLogic.get(player);
        ZanpakutoSpirit spirit = ZanpakutoTests.spirit(helper, key);
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        ZanpakutoEvents.learnName(player, data, key.location(), spirit, player.getMainHandItem());
        BankaiLogic.reach(data, BankaiLogic.nameOf(spirit).orElseThrow(), helper.getLevel().getGameTime());
    }

    /** Asserts that a release now comes to this. */
    static void released(GameTestHelper helper, Player player, ShikaiEvents.Result expected, String what) {
        ShikaiEvents.Result result = ShikaiEvents.release(player);
        helper.assertTrue(result == expected, what + " came to " + result + ", not " + expected);
    }

    /** Releases Shikai, lets it settle and calls Bankai; the test fails if either does not come. */
    static void toBankai(GameTestHelper helper, Player player) {
        released(helper, player, ShikaiEvents.Result.RELEASED, "Shikai");
        player.tickCount += ShikaiEvents.SETTLE_TICKS;
        released(helper, player, ShikaiEvents.Result.BANKAI, "Bankai");
    }

    static ZanpakutoStage stage(ItemStack stack) {
        return ZanpakutoItem.blade(stack).stage();
    }

    /** What the blade's own main-hand modifier of this kind adds, or NaN without one. */
    static double modifier(ItemStack stack, Holder<Attribute> attribute, ResourceLocation id) {
        ItemAttributeModifiers modifiers =
                stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.matches(attribute, id) && entry.slot() == EquipmentSlotGroup.MAINHAND) {
                return entry.modifier().amount();
            }
        }
        return Double.NaN;
    }

    static double damageOf(ItemStack stack) {
        return modifier(stack, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
    }

    private static boolean translates(Component line, String key) {
        return line.getContents() instanceof TranslatableContents words && words.getKey().equals(key);
    }

    /** The keys of these lines, oldest first. */
    static List<String> keys(List<Component> lines) {
        return lines.stream().<String>map(line -> line.getContents() instanceof TranslatableContents words
                ? words.getKey() : line.getString()).toList();
    }

    /** Whether the last line on this joined player's action bar is this key's. */
    static boolean lastSaid(ServerPlayer player, String key) {
        List<Component> said = TestPlayers.actionBar(player);
        return !said.isEmpty() && translates(said.get(said.size() - 1), key);
    }

    /** Whether this chat holds someone saying Bankai aloud, its one argument the words of {@code named}. */
    private static boolean heard(List<Component> chat, String named) {
        return chat.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents said
                && said.getKey().equals("chat.type.text") && said.getArgs().length == 2
                && said.getArgs()[1] instanceof Component text && translates(text, "speech.bleach.bankai")
                && text.getContents() instanceof TranslatableContents words && words.getArgs().length == 1
                && words.getArgs()[0] instanceof Component name && translates(name, named));
    }

    /**
     * Shikai's release key, pressed again once its soul has reached Bankai, calls the Bankai out. Short of its cost, it
     * is refused and told so. Paid for, it costs 2.4 times a Shikai release; the soul says "Bankai" and the Bankai's
     * name aloud, heard by itself and a soul nearby that sees spirits, it is heard lower than a Shikai and seen in twice
     * the motes, and the action bar names it, as a release and not a refusal; the blade is in Bankai, tooltip and all,
     * in its own look, at Shikai's reach with half again its damage, which the blades kept in line do not undo and which
     * reaches the player's own attack on the next tick; the soul does not refill while it lasts; and the release earns
     * the Bankai advancement, which the soul nearby does not. Its title is not read here: no test player keeps titles.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theReleaseKeyCallsBankaiFromShikai(GameTestHelper helper) {
        ServerPlayer renji = joined(helper, ZanpakutoSpirits.ZABIMARU, new Vec3(1.5, 1.0, 1.5));
        ServerPlayer near = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(3.5, 1.0, 1.5));
        near.setData(SoulAttachments.SOUL, ZanpakutoTests.soul(SoulState.SHINIGAMI, 300.0F));
        SoulData data = SoulLogic.get(renji);
        ItemStack blade = renji.getMainHandItem();
        ShikaiEvents.onKey(renji, ZanpakutoKey.RELEASE);
        renji.tickCount += ShikaiEvents.SETTLE_TICKS;
        float cost = data.maxReiryoku() * 0.10F * 2.4F;
        data.setReiryoku(cost);
        ShikaiEvents.onKey(renji, ZanpakutoKey.RELEASE);
        helper.assertTrue(stage(blade) == ZanpakutoStage.SHIKAI && lastSaid(renji, "message.bleach.shikai.too_weak"),
                "short of the cost, the blade is " + stage(blade) + " and was shown " + keys(TestPlayers.actionBar(renji)));

        helper.assertTrue(!DangaiTests.earned(renji, "bankai"), "the Bankai advancement came before any Bankai");

        data.setReiryoku(data.maxReiryoku());
        float before = data.reiryoku();
        TestPlayers.watchEffects(renji);
        ShikaiEvents.onKey(renji, ZanpakutoKey.RELEASE);
        List<Packet<?>> effects = TestPlayers.takeEffects(renji);
        helper.assertTrue(stage(blade) == ZanpakutoStage.BANKAI && Math.abs(before - data.reiryoku() - cost) < 0.01F
                        && lastSaid(renji, "message.bleach.shikai.released"),
                "the Bankai is " + stage(blade) + " for " + (before - data.reiryoku()) + " reiryoku, not " + cost
                        + ", and was shown " + keys(TestPlayers.actionBar(renji)));
        helper.assertTrue(effects.stream().anyMatch(packet -> packet instanceof ClientboundSoundPacket sound
                        && Math.abs(sound.getPitch() - 0.7F) < EPSILON)
                        && effects.stream().anyMatch(packet -> packet instanceof ClientboundLevelParticlesPacket motes
                        && motes.getCount() == 48),
                "the Bankai was not heard at pitch 0.7 and seen in 48 motes among " + effects.size() + " effects");
        String hihio = "zanpakuto.bleach.zabimaru.bankai";
        helper.assertTrue(heard(TestPlayers.chat(renji), hihio) && heard(TestPlayers.chat(near), hihio),
                "Bankai was said " + keys(TestPlayers.chat(renji)) + " and heard " + keys(TestPlayers.chat(near)));
        blade.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        ZanpakutoEvents.reconcile(renji, data);
        double reach = modifier(blade, Attributes.ENTITY_INTERACTION_RANGE, ZanpakutoItem.REACH_ID);
        helper.assertTrue(ZanpakutoTests.model(blade) == 5 && Math.abs(damageOf(blade) - 11.0) < EPSILON
                        && Math.abs(reach - 3.0) < EPSILON,
                "Hihiō Zabimaru has model " + ZanpakutoTests.model(blade) + ", damage " + damageOf(blade) + ", reach "
                        + reach);
        List<Component> tooltip = new ArrayList<>();
        blade.getItem().appendHoverText(blade, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(data.isShikaiActive() && !SoulEvents.mayRegenerate(renji, data)
                        && tooltip.stream().anyMatch(line -> translates(line, "item.bleach.zanpakuto.bankai")),
                "in Bankai the soul " + (SoulEvents.mayRegenerate(renji, data) ? "refills" : "does not refill")
                        + " and the tooltip reads " + keys(tooltip));
        helper.assertTrue(DangaiTests.earned(renji, "bankai") && !DangaiTests.earned(near, "bankai"),
                "the Bankai advancement went to the wrong soul, or to none");
        helper.succeedWhen(() -> {
            AttributeModifier worn = renji.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Item.BASE_ATTACK_DAMAGE_ID);
            GateTests.patiently(helper, worn != null && Math.abs(worn.amount() - 11.0) < EPSILON,
                    "the player's attack carries " + (worn == null ? "nothing" : worn.amount()) + " from the blade");
        });
    }

    /**
     * Bankai is asked what Shikai is asked, in turn: a soul that has not reached it is told its blade is released
     * already, as before; it waits for the Shikai to settle; a soul whose power is lent, or whose Hollow holds its body,
     * cannot call it; and one that would keep less than half a point is too weak. Called, it is released already too,
     * and the seal key seals it all the way, to its sealed form. A Substitute calls it as a Shinigami does. A blade
     * holding a spirit the soul no longer has names no Bankai, so the key answers as it did before.
     */
    @GameTest(template = "empty")
    public static void bankaiIsAskedInTurnAndSealsAllTheWay(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        Player unreached = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        released(helper, unreached, ShikaiEvents.Result.RELEASED, "Shikai");
        unreached.tickCount += ShikaiEvents.SETTLE_TICKS;
        released(helper, unreached, ShikaiEvents.Result.ALREADY, "the key again without Bankai");

        Player renji = reached(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = SoulLogic.get(renji);
        ItemStack blade = renji.getMainHandItem();
        released(helper, renji, ShikaiEvents.Result.RELEASED, "Shikai");
        renji.tickCount += 10;
        released(helper, renji, ShikaiEvents.Result.SETTLING, "Bankai half a second after Shikai");
        renji.tickCount += ShikaiEvents.SETTLE_TICKS;
        ShinigamiProgress progress = data.shinigami();
        data.setShinigami(progress.withPowerLentUntil(now + 1200L));
        released(helper, renji, ShikaiEvents.Result.LENT, "Bankai with power lent");
        data.setShinigami(progress);
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now - DAY));
        helper.assertTrue(VisoredEvents.startTakeover(renji, data, false, now), "the rooted Hollow did not seize its host");
        released(helper, renji, ShikaiEvents.Result.SILENT, "Bankai with the Hollow holding the body");
        VisoredEvents.endTakeover(renji, data, VisoredEvents.TakeoverEnd.REMOVED, now);
        data.setReiryoku(ZanpakutoLogic.bankaiReleaseCost(data, ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU))
                + 0.4F);
        released(helper, renji, ShikaiEvents.Result.TOO_WEAK, "Bankai leaving 0.4 reiryoku");
        helper.assertTrue(stage(blade) == ZanpakutoStage.SHIKAI && data.isShikaiActive(),
                "the refusals left the blade " + stage(blade));

        data.setReiryoku(data.maxReiryoku());
        released(helper, renji, ShikaiEvents.Result.BANKAI, "Bankai");
        renji.tickCount += ShikaiEvents.SETTLE_TICKS;
        released(helper, renji, ShikaiEvents.Result.ALREADY, "the key again in Bankai");
        helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED && stage(blade) == ZanpakutoStage.SEALED
                        && ZanpakutoTests.model(blade) == 2 && !data.isShikaiActive()
                        && blade.getComponentsPatch().get(DataComponents.ATTRIBUTE_MODIFIERS) == null,
                "the seal left Hihiō Zabimaru " + stage(blade) + " with model " + ZanpakutoTests.model(blade));

        Player substitute = ZanpakutoTests.mock(helper, ShinigamiTests.substitute(500.0F));
        nameAndReach(helper, substitute, ZanpakutoSpirits.ZABIMARU);
        toBankai(helper, substitute);

        Player lost = reached(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData lostData = SoulLogic.get(lost);
        released(helper, lost, ShikaiEvents.Result.RELEASED, "Shikai");
        lost.tickCount += ShikaiEvents.SETTLE_TICKS;
        lostData.setZanpakuto(lostData.zanpakuto().withSpirit(Optional.of(BleachMod.id("missing"))));
        released(helper, lost, ShikaiEvents.Result.ALREADY, "the key from a blade holding a spirit the soul has not");
        helper.succeed();
    }

    /**
     * Death and logging out end a Bankai as they end a Shikai: a blade that can seal seals all the way and Zangetsu falls
     * back to its Shikai, and the soul is in neither any more, read from whichever soul the player has by then.
     */
    @GameTest(template = "empty")
    public static void deathAndLogoutEndABankai(GameTestHelper helper) {
        for (boolean death : new boolean[] {true, false}) {
            for (ResourceKey<ZanpakutoSpirit> key : List.of(ZanpakutoSpirits.ZABIMARU, ZanpakutoSpirits.ZANGETSU)) {
                Player player = reached(helper, key);
                ItemStack blade = player.getMainHandItem();
                toBankai(helper, player);
                if (death) {
                    // At no health, as a player is when its death is posted.
                    player.setHealth(0.0F);
                    NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
                } else {
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                }
                SoulData data = SoulLogic.get(player);
                ZanpakutoStage after = key == ZanpakutoSpirits.ZANGETSU ? ZanpakutoStage.SHIKAI : ZanpakutoStage.SEALED;
                helper.assertTrue(stage(blade) == after && !data.isShikaiActive() && !data.isBankaiActive(),
                        (death ? "death" : "logging out") + " left " + key.location() + "'s Bankai " + stage(blade));
            }
        }
        helper.succeed();
    }

    /**
     * A spirit file's own Bankai form, cost and drain stand in for the server config's: in Bankai the blade takes that
     * form, model and damage alike, the call costs that share of the soul's maximum, and it drains that share of it a
     * second. A model alone keeps the config's numbers, and a file may not give both. Left to the config, a Shikai that
     * costs the whole bar calls its Bankai for no more than the whole, at the ratio's most.
     */
    @GameTest(template = "empty")
    public static void aSpiritFilesBankaiOverridesTheConfig(GameTestHelper helper) {
        JsonObject file = BankaiTests.shippedFile(helper, "zabimaru");
        JsonObject bankai = file.getAsJsonObject("bankai");
        // The shipped file gives a model, and a file gives a model or a form, not both.
        bankai.remove("model");
        bankai.add("form", JsonParser.parseString("{\"model\": 5, \"damage\": 20.0, \"speed\": 1.0}"));
        bankai.addProperty("release_cost", 0.5);
        bankai.addProperty("drain", 0.02);
        ZanpakutoSpirit given = BankaiTests.parse(helper, file).orElseThrow();
        SoulData data = ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F);
        ZanpakutoSpirit.Form form = ZanpakutoLogic.form(given, ZanpakutoStage.BANKAI);
        float cost = ZanpakutoLogic.bankaiReleaseCost(data, given);
        ItemStack blade = ZanpakutoTests.asauchi();
        ZanpakutoItem.setStage(blade, given, ZanpakutoStage.BANKAI);
        helper.assertTrue(form.equals(new ZanpakutoSpirit.Form(5, 20.0F, 1.0F, 0.0F)) && Math.abs(cost - 250.0F) < 0.01F
                        && ZanpakutoTests.model(blade) == 5 && Math.abs(damageOf(blade) - 19.0) < EPSILON,
                "the file's Bankai is " + form + " for " + cost + ", on a blade of model " + ZanpakutoTests.model(blade)
                        + " and damage " + damageOf(blade));
        float drain = ZanpakutoLogic.bankaiDrainPerTick(data, given);
        double share = data.maxReiryoku() * 0.02 * ZanpakutoLogic.drainFraction(data) / 20.0;
        helper.assertTrue(Math.abs(drain - share) < 1.0E-4, "the file's Bankai drains " + drain + " a tick, not " + share);
        JsonObject modelled = BankaiTests.shippedFile(helper, "zabimaru");
        modelled.getAsJsonObject("bankai").addProperty("model", 5);
        ZanpakutoSpirit.Form looks =
                ZanpakutoLogic.form(BankaiTests.parse(helper, modelled).orElseThrow(), ZanpakutoStage.BANKAI);
        bankai.addProperty("model", 5);
        String refused = ZanpakutoSpirit.CODEC.parse(helper.getLevel().registryAccess()
                .createSerializationContext(JsonOps.INSTANCE), file).error().map(error -> error.message()).orElse("nothing");
        helper.assertTrue(looks.equals(new ZanpakutoSpirit.Form(5, 12.0F, 1.4F, 3.0F))
                        && refused.contains("bankai gives a form or a model, not both"),
                "a file's Bankai model alone gives " + looks + ", and with a form too it was refused for " + refused);

        JsonObject dear = BankaiTests.shippedFile(helper, "zabimaru");
        dear.addProperty("release_cost", 1.0);
        ZanpakutoSpirit whole = BankaiTests.parse(helper, dear).orElseThrow();
        // Set for this one call, in this one tick, and put back before any other test runs.
        double ratio = BleachConfig.get(BleachConfig.BANKAI_RELEASE_COST_RATIO);
        BleachConfig.BANKAI_RELEASE_COST_RATIO.set(10.0);
        float capped;
        try {
            capped = ZanpakutoLogic.bankaiReleaseCost(data, whole);
        } finally {
            BleachConfig.BANKAI_RELEASE_COST_RATIO.set(ratio);
        }
        helper.assertTrue(Math.abs(capped - data.maxReiryoku()) < 0.01F,
                "a Shikai of the whole bar called its Bankai for " + capped + " at ten times the cost");
        helper.succeed();
    }

    /**
     * A Bankai drains 2.5 times its Shikai's share while it lasts. Run dry, the soul is exhausted and a Bankai that can
     * seal seals all the way at once, told as a Shikai run dry is, with no fall to Shikai told between.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aBankaiRunsDryAndSeals(GameTestHelper helper) {
        ServerPlayer renji = joined(helper, ZanpakutoSpirits.ZABIMARU, new Vec3(1.5, 1.0, 1.5));
        SoulData data = SoulLogic.get(renji);
        ItemStack blade = renji.getMainHandItem();
        toBankai(helper, renji);
        float perTick = ZanpakutoLogic.bankaiDrainPerTick(data, ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU));
        double expected = data.maxReiryoku() * 0.006 * BleachConfig.get(BleachConfig.SHIKAI_DRAIN_MULTIPLIER) * 2.5
                * ZanpakutoLogic.drainFraction(data) / 20.0;
        helper.assertTrue(Math.abs(perTick - expected) < 1.0E-4, "Hihiō Zabimaru drains " + perTick + ", not " + expected);
        // Three ticks paid, and the fourth cannot be.
        data.setReiryoku(0.5F + 3.5F * perTick);
        helper.succeedWhen(() -> {
            List<String> said = keys(TestPlayers.actionBar(renji));
            GateTests.patiently(helper, lastSaid(renji, "message.bleach.shikai.exhausted"), "running dry was shown " + said);
            helper.assertTrue(!said.contains("message.bleach.bankai.exhausted") && stage(blade) == ZanpakutoStage.SEALED
                            && ZanpakutoTests.model(blade) == 2 && renji.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && !data.isShikaiActive() && !data.isBankaiActive(),
                    "run dry, the blade is " + stage(blade) + " and the soul was shown " + said
                            + (renji.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? "" : ", not exhausted"));
        });
    }

    /**
     * Zangetsu, released for good, calls its Bankai too, for 2.4 times its Shikai release's cost; it drains 1% of
     * maximum a second at least, though its Shikai drains nothing, and the soul does not refill; Tensa Zangetsu, in its
     * own model, hits half again as hard as Zangetsu's Shikai, and no Jinzen is sat in it. The seal key steps it down
     * to Shikai, where it cannot seal, and the release key calls it again.
     */
    @GameTest(template = "empty")
    public static void zangetsuCallsBankaiAndStepsDownToShikai(GameTestHelper helper) {
        Player ichigo = reached(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(ichigo);
        ItemStack blade = ichigo.getMainHandItem();
        released(helper, ichigo, ShikaiEvents.Result.RELEASED, "Shikai");
        ichigo.tickCount += ShikaiEvents.SETTLE_TICKS;
        float before = data.reiryoku();
        released(helper, ichigo, ShikaiEvents.Result.BANKAI, "Tensa Zangetsu");
        float cost = data.maxReiryoku() * 0.05F * 2.4F;
        helper.assertTrue(Math.abs(before - data.reiryoku() - cost) < 0.01F && ZanpakutoItem.blade(blade).constant()
                        && ZanpakutoTests.model(blade) == 4 && Math.abs(damageOf(blade) - 12.5) < EPSILON,
                "Tensa Zangetsu cost " + (before - data.reiryoku()) + " and has damage " + damageOf(blade));
        double floor = data.maxReiryoku() * 0.01 * ZanpakutoLogic.drainFraction(data) / 20.0;
        float drain = ZanpakutoLogic.bankaiDrainPerTick(data, ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU));
        helper.assertTrue(data.isBankaiActive() && !data.isShikaiActive() && !SoulEvents.mayRegenerate(ichigo, data)
                        && Math.abs(drain - floor) < 1.0E-4,
                "Tensa Zangetsu drains " + drain + ", not " + floor + ", and the soul "
                        + (SoulEvents.mayRegenerate(ichigo, data) ? "refills" : "is in Bankai: " + data.isBankaiActive()));
        String sitting = JinzenLogic.refusal(ichigo, data, blade, helper.getLevel().getGameTime());
        helper.assertTrue("bankai".equals(sitting), "Jinzen in Tensa Zangetsu was refused as " + sitting);

        helper.assertTrue(ShikaiEvents.seal(ichigo) == ShikaiEvents.Result.STEPPED_DOWN
                        && stage(blade) == ZanpakutoStage.SHIKAI && Math.abs(damageOf(blade) - 8.0) < EPSILON
                        && !data.isBankaiActive(),
                "the seal key left Tensa Zangetsu " + stage(blade) + " with damage " + damageOf(blade));
        ichigo.tickCount += ShikaiEvents.SETTLE_TICKS;
        helper.assertTrue(ShikaiEvents.seal(ichigo) == ShikaiEvents.Result.CANNOT_SEAL
                && stage(blade) == ZanpakutoStage.SHIKAI, "Zangetsu's Shikai sealed, or would not say it cannot");
        released(helper, ichigo, ShikaiEvents.Result.BANKAI, "Tensa Zangetsu again");
        helper.succeed();
    }

    /**
     * On the ground a blade in Bankai falls back at once: Zabimaru seals and Zangetsu is in Shikai, in Shikai's form,
     * and it is changed once, not again on every tick after.
     */
    @GameTest(template = "empty")
    public static void aBankaiBladeOnTheGroundFallsBack(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        for (ResourceKey<ZanpakutoSpirit> key : List.of(ZanpakutoSpirits.ZABIMARU, ZanpakutoSpirits.ZANGETSU)) {
            Player player = reached(helper, key);
            toBankai(helper, player);
            ItemStack lying = player.getMainHandItem().copy();
            ItemEntity dropped = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, lying);
            lying.getItem().onEntityItemUpdate(lying, dropped);
            ItemStack fallen = dropped.getItem();
            helper.assertTrue(key == ZanpakutoSpirits.ZANGETSU
                            ? stage(fallen) == ZanpakutoStage.SHIKAI && Math.abs(damageOf(fallen) - 8.0) < EPSILON
                            : stage(fallen) == ZanpakutoStage.SEALED && ZanpakutoTests.model(fallen) == 2,
                    key.location() + "'s Bankai lay on the ground " + stage(fallen) + " with damage " + damageOf(fallen));
            fallen.getItem().onEntityItemUpdate(fallen, dropped);
            helper.assertTrue(dropped.getItem() == fallen, key.location() + "'s fallen blade was changed again");
        }
        helper.succeed();
    }

    /**
     * A blade in Bankai carried by anyone but its owner in Bankai falls back on that player's first tick with it:
     * another's Zangetsu to Shikai, which nothing else would do, and another's Zabimaru sealed; and its owner's own
     * spare once the Bankai has ended, as after a login or the way out of the End, which keep no Bankai. The owner's
     * Bankai in hand holds, tick after tick.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aBankaiBladeFallsBackInAnyHandsButItsOwners(GameTestHelper helper) {
        Player renji = reached(helper, ZanpakutoSpirits.ZABIMARU);
        toBankai(helper, renji);
        Player other = reached(helper, ZanpakutoSpirits.ZANGETSU);
        toBankai(helper, other);
        ItemStack spare = other.getMainHandItem().copy();
        ItemStack zangetsu = other.getMainHandItem().copy();
        spare.inventoryTick(helper.getLevel(), other, 3, false);
        helper.assertTrue(stage(spare) == ZanpakutoStage.BANKAI, "a spare blade fell back while its owner is in Bankai");
        ShikaiEvents.endBankai(other, SoulLogic.get(other), null);
        spare.inventoryTick(helper.getLevel(), other, 3, false);
        helper.assertTrue(stage(spare) == ZanpakutoStage.SHIKAI, "a spare stayed " + stage(spare) + " after its Bankai");

        ServerPlayer holder = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        holder.setData(SoulAttachments.SOUL, ZanpakutoTests.soul(SoulState.SHINIGAMI, 300.0F));
        holder.getInventory().setItem(5, zangetsu);
        holder.getInventory().setItem(6, renji.getMainHandItem().copy());
        int start = holder.tickCount;
        ServerPlayer ichigo = joined(helper, ZanpakutoSpirits.ZANGETSU, new Vec3(3.5, 1.0, 1.5));
        toBankai(helper, ichigo);
        ItemStack own = ichigo.getMainHandItem();
        int from = ichigo.tickCount;
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, holder.tickCount > start, "the holder has not ticked yet"))
                .thenExecute(() -> {
                    ZanpakutoBlade kept = ZanpakutoItem.blade(holder.getInventory().getItem(5));
                    ZanpakutoStage sealed = stage(holder.getInventory().getItem(6));
                    helper.assertTrue(kept.stage() == ZanpakutoStage.SHIKAI && kept.constant()
                                    && sealed == ZanpakutoStage.SEALED,
                            "on the holder's first tick, Zangetsu is " + kept.stage() + " and Zabimaru " + sealed);
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, ichigo.tickCount >= from + 5,
                        "the owner has not held his Bankai five ticks yet"))
                .thenExecute(() -> helper.assertTrue(stage(own) == ZanpakutoStage.BANKAI
                        && SoulLogic.get(ichigo).isBankaiActive(), "the owner's own Bankai fell back to " + stage(own)))
                .thenSucceed();
    }

    /**
     * A Bankai is held only by its own blade in Bankai: with a Shikai copy of it in hand and the Bankai blade put away,
     * the Bankai ends as a release left out of hand ends, all the way, and is told so.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aShikaiCopyInHandDoesNotHoldABankai(GameTestHelper helper) {
        ServerPlayer renji = joined(helper, ZanpakutoSpirits.ZABIMARU, new Vec3(1.5, 1.0, 1.5));
        SoulData data = SoulLogic.get(renji);
        ItemStack bankai = renji.getMainHandItem();
        toBankai(helper, renji);
        ItemStack copy = bankai.copy();
        ShikaiEvents.toShikai(copy, helper.getLevel());
        renji.setItemInHand(InteractionHand.MAIN_HAND, copy);
        renji.getInventory().setItem(3, bankai);
        int from = renji.tickCount;
        helper.succeedWhen(() -> {
            GateTests.patiently(helper, renji.tickCount >= from + 25, "Renji has not been watched 25 ticks yet");
            helper.assertTrue(!data.isBankaiActive() && stage(bankai) == ZanpakutoStage.SEALED
                            && lastSaid(renji, "message.bleach.shikai.away"),
                    "with a Shikai copy in hand the soul is " + (data.isBankaiActive() ? "still" : "no longer")
                            + " in Bankai, its blade " + stage(bankai) + ", shown " + keys(TestPlayers.actionBar(renji)));
        });
    }

    /**
     * Zangetsu's seal key steps its Bankai down to Shikai and says so in words of its own. Called again and run dry, it
     * falls back to Shikai, exhausted and told so, and stays there, told nothing as a Shikai is: it never seals.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void zangetsuRunsDryBackToShikaiAndStays(GameTestHelper helper) {
        ServerPlayer ichigo = joined(helper, ZanpakutoSpirits.ZANGETSU, new Vec3(1.5, 1.0, 1.5));
        SoulData data = SoulLogic.get(ichigo);
        ItemStack blade = ichigo.getMainHandItem();
        toBankai(helper, ichigo);
        ShikaiEvents.onKey(ichigo, ZanpakutoKey.SEAL);
        helper.assertTrue(lastSaid(ichigo, "message.bleach.bankai.ended") && stage(blade) == ZanpakutoStage.SHIKAI,
                "the seal key left Tensa Zangetsu " + stage(blade) + " and showed " + keys(TestPlayers.actionBar(ichigo)));
        ichigo.tickCount += ShikaiEvents.SETTLE_TICKS;
        released(helper, ichigo, ShikaiEvents.Result.BANKAI, "Tensa Zangetsu again");
        float perTick = ZanpakutoLogic.bankaiDrainPerTick(data, ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU));
        // One tick paid, and the second cannot be.
        data.setReiryoku(0.5F + 1.5F * perTick);
        int[] from = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, lastSaid(ichigo, "message.bleach.bankai.exhausted"),
                        "Tensa Zangetsu has not run dry: " + keys(TestPlayers.actionBar(ichigo))))
                .thenExecute(() -> {
                    helper.assertTrue(stage(blade) == ZanpakutoStage.SHIKAI && ZanpakutoItem.blade(blade).constant()
                                    && !data.isBankaiActive() && ichigo.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                            "run dry, Zangetsu is " + stage(blade) + (data.isBankaiActive() ? " in Bankai" : ""));
                    from[0] = ichigo.tickCount;
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, ichigo.tickCount >= from[0] + 20,
                        "Zangetsu's Shikai has not been watched twenty ticks yet"))
                .thenExecute(() -> helper.assertTrue(stage(blade) == ZanpakutoStage.SHIKAI
                                && lastSaid(ichigo, "message.bleach.bankai.exhausted")
                                && !keys(TestPlayers.actionBar(ichigo)).contains("message.bleach.shikai.exhausted"),
                        "after running dry Zangetsu is " + stage(blade) + ", shown " + keys(TestPlayers.actionBar(ichigo))))
                .thenExecute(() -> {
                    // The operator's seal puts Tensa Zangetsu back to waiting for its first release, told nothing on
                    // the way, as a constant Shikai is.
                    data.setReiryoku(data.maxReiryoku());
                    ichigo.tickCount += ShikaiEvents.SETTLE_TICKS;
                    released(helper, ichigo, ShikaiEvents.Result.BANKAI, "Tensa Zangetsu before the seal command");
                    int shown = TestPlayers.actionBar(ichigo).size();
                    ichigo.addTag("bleach_bankai_sealed");
                    GateTests.run(helper.getLevel(), "bleach zanpakuto @a[tag=bleach_bankai_sealed,limit=1] seal");
                    helper.assertTrue(stage(blade) == ZanpakutoStage.SEALED && !data.isBankaiActive()
                                    && TestPlayers.actionBar(ichigo).size() == shown,
                            "the seal command left Zangetsu " + stage(blade) + " and showed "
                                    + keys(TestPlayers.actionBar(ichigo)));
                })
                .thenSucceed();
    }

    /**
     * Learning the name again ends a Bankai as a seal does, before the blade is bound anew, so no release drains on over
     * it. {@code /bleach zanpakuto … bankai revoke} ends a Bankai that is released, back to the Shikai under it, which
     * still drains, and tells its soul so; the release key then finds the blade released already.
     */
    @GameTest(template = "empty")
    public static void revokeAndLearningTheNameAgainEndABankai(GameTestHelper helper) {
        ServerPlayer renji = joined(helper, ZanpakutoSpirits.ZABIMARU, new Vec3(1.5, 1.0, 1.5));
        renji.addTag("bleach_bankai_revoked");
        SoulData data = SoulLogic.get(renji);
        ItemStack blade = renji.getMainHandItem();
        toBankai(helper, renji);
        ZanpakutoEvents.learnName(renji, data, ZanpakutoSpirits.ZABIMARU.location(),
                ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU), blade);
        helper.assertTrue(!data.isBankaiActive() && !data.isShikaiActive() && stage(blade) == ZanpakutoStage.SEALED,
                "learning the name again left the blade " + stage(blade) + " and the soul released");

        renji.tickCount += ShikaiEvents.SETTLE_TICKS;
        toBankai(helper, renji);
        List<Component> said = GateTests.run(helper.getLevel(),
                "bleach zanpakuto @a[tag=bleach_bankai_revoked,limit=1] bankai revoke");
        helper.assertTrue(said.size() == 1 && GateTests.says(said.get(0), "commands.bleach.zanpakuto.bankai.revoke", -1),
                "revoke said " + keys(said));
        helper.assertTrue(stage(blade) == ZanpakutoStage.SHIKAI && Math.abs(damageOf(blade) - 7.0) < EPSILON
                        && data.isShikaiActive() && !data.isBankaiActive() && !data.zanpakuto().bankai().achieved()
                        && lastSaid(renji, "message.bleach.bankai.ended"),
                "revoked, the blade is " + stage(blade) + " with damage " + damageOf(blade) + " and the soul was shown "
                        + keys(TestPlayers.actionBar(renji)));
        renji.tickCount += ShikaiEvents.SETTLE_TICKS;
        released(helper, renji, ShikaiEvents.Result.ALREADY, "the key after revoke");
        helper.succeed();
    }
}
