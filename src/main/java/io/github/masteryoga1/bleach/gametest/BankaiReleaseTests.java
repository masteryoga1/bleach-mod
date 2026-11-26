package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private static Player reached(GameTestHelper helper, ResourceKey<ZanpakutoSpirit> key) {
        Player player = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        nameAndReach(helper, player, key);
        return player;
    }

    /** A joined Shinigami (max 510) that has learned this spirit's name for the blade in its hand and reached its Bankai. */
    private static ServerPlayer joined(GameTestHelper helper, ResourceKey<ZanpakutoSpirit> key, Vec3 at) {
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
    private static void released(GameTestHelper helper, Player player, ShikaiEvents.Result expected, String what) {
        ShikaiEvents.Result result = ShikaiEvents.release(player);
        helper.assertTrue(result == expected, what + " came to " + result + ", not " + expected);
    }

    /** Releases Shikai, lets it settle and calls Bankai; the test fails if either does not come. */
    private static void toBankai(GameTestHelper helper, Player player) {
        released(helper, player, ShikaiEvents.Result.RELEASED, "Shikai");
        player.tickCount += ShikaiEvents.SETTLE_TICKS;
        released(helper, player, ShikaiEvents.Result.BANKAI, "Bankai");
    }

    private static ZanpakutoStage stage(ItemStack stack) {
        return ZanpakutoItem.blade(stack).stage();
    }

    /** What the blade's own main-hand modifier of this kind adds, or NaN without one. */
    private static double modifier(ItemStack stack, Holder<Attribute> attribute, ResourceLocation id) {
        ItemAttributeModifiers modifiers =
                stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.matches(attribute, id) && entry.slot() == EquipmentSlotGroup.MAINHAND) {
                return entry.modifier().amount();
            }
        }
        return Double.NaN;
    }

    private static double damageOf(ItemStack stack) {
        return modifier(stack, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
    }

    private static boolean translates(Component line, String key) {
        return line.getContents() instanceof TranslatableContents words && words.getKey().equals(key);
    }

    /** The keys of these lines, oldest first. */
    private static List<String> keys(List<Component> lines) {
        return lines.stream().<String>map(line -> line.getContents() instanceof TranslatableContents words
                ? words.getKey() : line.getString()).toList();
    }

    /** Whether the last line on this joined player's action bar is this key's. */
    private static boolean lastSaid(ServerPlayer player, String key) {
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
     * in Shikai's look and reach with half again its damage, which the blades kept in line do not undo and which
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
        helper.assertTrue(ZanpakutoTests.model(blade) == 3 && Math.abs(damageOf(blade) - 11.0) < EPSILON
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
     * Death and logging out end a Bankai as they end a Shikai: the blade seals all the way and the soul is released no
     * more, read from whichever soul the player has by then.
     */
    @GameTest(template = "empty")
    public static void deathAndLogoutEndABankai(GameTestHelper helper) {
        for (boolean death : new boolean[] {true, false}) {
            for (ResourceKey<ZanpakutoSpirit> key : List.of(ZanpakutoSpirits.ZABIMARU)) {
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
                helper.assertTrue(stage(blade) == ZanpakutoStage.SEALED && !data.isShikaiActive(),
                        (death ? "death" : "logging out") + " left " + key.location() + "'s Bankai " + stage(blade));
            }
        }
        helper.succeed();
    }

    /**
     * A spirit file's own Bankai form and cost stand in for the server config's: in Bankai the blade takes that form,
     * model and damage alike, and the call costs that share of the soul's maximum. Left to the config, a Shikai that
     * costs the whole bar calls its Bankai for no more than the whole, at the ratio's most.
     */
    @GameTest(template = "empty")
    public static void aSpiritFilesBankaiOverridesTheConfig(GameTestHelper helper) {
        JsonObject file = BankaiTests.shippedFile(helper, "zabimaru");
        JsonObject bankai = file.getAsJsonObject("bankai");
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
}
