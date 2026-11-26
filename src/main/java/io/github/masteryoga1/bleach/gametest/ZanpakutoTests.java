package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.HollowEvents;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import io.github.masteryoga1.bleach.zanpakuto.JinzenScene;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStep;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStepKind;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Asauchi and the zanpakutō it becomes: who bonds and from what, the hidden leaning and
 * the spirit it picks, Jinzen and its test, learning the name, and the blade kept in line with the
 * soul in the inventory, through death and on the wire. The rules in {@link ZanpakutoLogic} are
 * called directly; the rest run on mock players, which are not in the world and never tick. The
 * shared helpers here are used by {@link ShikaiTests} too.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ZanpakutoTests {
    static final long NOW = 100_000L;
    static final float EPSILON = 1.0E-3F;
    private static final long DAY = 24_000L;

    private ZanpakutoTests() {
    }

    // ---- Shared helpers ----

    /** A soul in this state, full of this much reiryoku, as {@link ShinigamiTests#soul} makes it. */
    static SoulData soul(SoulState state, float maxReiryoku) {
        return ShinigamiTests.soul(state, maxReiryoku);
    }

    /**
     * A mock survival player with this soul, as if it had been in the world a while ({@code tickCount}
     * 1000, so no release or seal is still settling). It stands on the ground inside the test's box,
     * so Jinzen would let it sit and anything it fires starts where the test can see it.
     */
    static Player mock(GameTestHelper helper, SoulData data) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SoulAttachments.SOUL, data);
        player.tickCount = 1000;
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.setPos(at.x, at.y, at.z);
        player.setOnGround(true);
        return player;
    }

    /** The world's zanpakutō spirits. */
    static Registry<ZanpakutoSpirit> spirits(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(ZanpakutoSpirits.REGISTRY);
    }

    static ZanpakutoSpirit spirit(GameTestHelper helper, ResourceKey<ZanpakutoSpirit> key) {
        return spirits(helper).getOrThrow(key);
    }

    /** A mock Shinigami with max 500 that has learned this spirit's name for the fresh Asauchi in its main hand (max 510 after). */
    static Player named(GameTestHelper helper, ResourceKey<ZanpakutoSpirit> key) {
        SoulData data = soul(SoulState.SHINIGAMI, 500.0F);
        Player player = mock(helper, data);
        player.setItemInHand(InteractionHand.MAIN_HAND, asauchi());
        ZanpakutoEvents.learnName(player, data, key.location(), spirit(helper, key), player.getMainHandItem());
        return player;
    }

    /** A fresh, nameless Asauchi. */
    static ItemStack asauchi() {
        return new ItemStack(ModItems.ASAUCHI.get());
    }

    /** A sealed blade bound to another soul, which has learned this spirit's name. */
    static ItemStack someoneElsesBlade(GameTestHelper helper, ResourceKey<ZanpakutoSpirit> key) {
        return named(helper, key).getMainHandItem().copy();
    }

    /** The stack's {@code custom_model_data}, or 0 without one (the plain Asauchi's model). */
    static int model(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null ? 0 : data.value();
    }

    /** A Shinigami with max 500 whose full bond has settled on Zangetsu, holding a nameless Asauchi: Jinzen opens on the meeting. */
    private static Player meetsZangetsu(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 500.0F);
        data.setZanpakuto(ZanpakutoBond.NONE.withBond(ZanpakutoBond.FULL).withWhispered(4)
                .withSpirit(Optional.of(ZanpakutoSpirits.ZANGETSU.location())));
        Player player = mock(helper, data);
        player.setItemInHand(InteractionHand.MAIN_HAND, asauchi());
        return player;
    }

    /** The step a page waits on, failing the test if it waits on none. */
    private static JinzenStep stepOf(GameTestHelper helper, @Nullable JinzenPage page, String problem) {
        helper.assertTrue(page != null && !page.finished() && page.step().isPresent(), problem);
        return page.step().get();
    }

    /** Opens Jinzen on Zangetsu at {@code now}, takes the red ribbon and gives the right answer, and returns the last page. */
    private static JinzenPage passZangetsu(GameTestHelper helper, Player player, SoulData data, long now) {
        JinzenStep ribbons = stepOf(helper, JinzenLogic.open(player, data, now), "Jinzen did not open on Zangetsu's test");
        int session = data.jinzen().id();
        JinzenPage question = JinzenLogic.answer(player, data, session, ribbons.index(), ribbons.highlight(), now + 5L);
        JinzenStep asked = stepOf(helper, question, "taking the red ribbon did not lead to the question");
        int[] order = JinzenLogic.answerOrder(data.jinzen().seed(), asked.answers().size());
        JinzenPage last = JinzenLogic.answer(player, data, session, asked.index(), JinzenLogic.correctPosition(order), now + 10L);
        helper.assertTrue(last != null, "the right answer was not taken");
        return last;
    }

    /** Whether one of the page's lines is this translation key. */
    private static boolean hasLine(JinzenPage page, String key) {
        for (Component line : page.lines()) {
            if (line.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** How many stacks in this inventory are blades bound to {@code owner}. */
    private static int bladesOwnedBy(Player holder, UUID owner) {
        Inventory inventory = holder.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (ZanpakutoItem.isOwnedBy(inventory.getItem(slot), owner)) {
                count++;
            }
        }
        return count;
    }

    private static long retryTicks() {
        return 20L * BleachConfig.get(BleachConfig.JINZEN_RETRY_SECONDS);
    }

    private static void assertConfig(GameTestHelper helper, double actual, double expected, String key) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-9, "the default " + key + " is " + actual + ", not " + expected);
    }

    // ---- Who bonds, and from what ----

    @GameTest(template = "empty")
    public static void onlyShinigamiPowerAndPassedSoulsBond(GameTestHelper helper) {
        helper.assertTrue(ZanpakutoLogic.canBond(soul(SoulState.SHINIGAMI, 200.0F), NOW), "a Shinigami cannot bond");
        helper.assertTrue(ZanpakutoLogic.canBond(ShinigamiTests.substitute(60.0F), NOW), "a Substitute cannot bond");
        helper.assertTrue(ZanpakutoLogic.canBond(ShinigamiTests.student(60.0F), NOW), "a student cannot bond");
        helper.assertTrue(ZanpakutoLogic.canBond(soul(SoulState.RUKONGAI, 60.0F), NOW), "a Rukongai soul cannot bond");
        helper.assertFalse(ZanpakutoLogic.canBond(soul(SoulState.HUMAN, 400.0F), NOW), "a Human with no power bonds");
        helper.assertFalse(ZanpakutoLogic.canBond(soul(SoulState.PLUS, 40.0F), NOW), "a Plus bonds");
        helper.assertFalse(ZanpakutoLogic.canBond(soul(SoulState.HOLLOW, 400.0F), NOW), "a Hollow bonds");
        helper.assertFalse(ZanpakutoLogic.canBond(soul(SoulState.ARRANCAR, 400.0F), NOW), "an Arrancar bonds");
        helper.assertFalse(ZanpakutoLogic.canBond(ShinigamiTests.lentGiver(), NOW + 1L), "a Shinigami who lent their power bonds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bondNeedsAnUnboundAsauchi(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 200.0F);
        Player player = mock(helper, data);
        helper.assertTrue(ZanpakutoEvents.giveBond(player, data, 10, NOW) == 0, "the bond grew with no blade carried");

        player.getInventory().setItem(20, someoneElsesBlade(helper, ZanpakutoSpirits.ZABIMARU));
        helper.assertTrue(ZanpakutoEvents.giveBond(player, data, 10, NOW) == 0, "the bond grew with someone else's blade");

        ItemStack own = asauchi();
        ZanpakutoItem.bind(own, player, ZanpakutoSpirits.ZABIMARU.location(), spirit(helper, ZanpakutoSpirits.ZABIMARU), false);
        player.getInventory().setItem(20, own);
        helper.assertTrue(ZanpakutoEvents.giveBond(player, data, 10, NOW) == 0, "the bond grew with a blade that already has a name");

        player.getInventory().setItem(20, asauchi());
        int gained = ZanpakutoEvents.giveBond(player, data, 10, NOW);
        helper.assertTrue(gained == 10 && data.zanpakuto().bond() == 10,
                "a nameless Asauchi in the inventory gave " + gained + " bond, not 10");

        data.setZanpakuto(data.zanpakuto().asNamed(Component.literal("Zabimaru"), false));
        helper.assertTrue(ZanpakutoEvents.giveBond(player, data, 10, NOW) == 0, "the bond grew after the name was learned");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bondSourcesFollowTheConfig(GameTestHelper helper) {
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_MULTIPLIER), 1.0, "bond_multiplier");
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_PER_MINUTE_CARRIED), 2, "bond_per_minute_carried");
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_PER_MEDITATION), 1, "bond_per_meditation");
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_PER_JINZEN), 25, "bond_per_jinzen");
        assertConfig(helper, BleachConfig.get(BleachConfig.JINZEN_BOND_COOLDOWN_SECONDS), 300, "jinzen_bond_cooldown_seconds");
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_PER_PURIFIED_REIATSU), 0.5, "bond_per_purified_reiatsu");
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_PURIFICATION_CAP), 50, "bond_purification_cap");
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_PER_CLOSE_CALL), 15, "bond_per_close_call");
        assertConfig(helper, BleachConfig.get(BleachConfig.SUBSTITUTE_CLOSE_CALL_MULTIPLIER), 2.0, "substitute_close_call_multiplier");
        assertConfig(helper, BleachConfig.get(BleachConfig.BOND_PER_SLEEP), 30, "bond_per_sleep");
        assertConfig(helper, BleachConfig.get(BleachConfig.SLEEP_BOND_COOLDOWN_MINUTES), 20, "sleep_bond_cooldown_minutes");

        SoulData data = soul(SoulState.SHINIGAMI, 200.0F);
        Player player = mock(helper, data);
        player.getInventory().setItem(9, asauchi());
        ZanpakutoEvents.onMeditationGrant(player, data);
        helper.assertTrue(data.zanpakuto().bond() == 1, "a meditation grant gave " + data.zanpakuto().bond() + " bond, not 1");

        ZanpakutoEvents.creditJinzen(player, data, NOW);
        helper.assertTrue(data.zanpakuto().bond() == 26 && data.zanpakuto().affinity(ZanpakutoType.KIDO) == 1,
                "the first Jinzen gave " + (data.zanpakuto().bond() - 1) + " bond and a kidō lean of "
                        + data.zanpakuto().affinity(ZanpakutoType.KIDO));
        ZanpakutoEvents.creditJinzen(player, data, NOW + 100L);
        helper.assertTrue(data.zanpakuto().bond() == 26 && data.zanpakuto().affinity(ZanpakutoType.KIDO) == 1,
                "a Jinzen inside the cooldown still bonded or leaned");
        ZanpakutoEvents.creditJinzen(player, data, NOW + 6000L);
        helper.assertTrue(data.zanpakuto().bond() == 51 && data.zanpakuto().affinity(ZanpakutoType.KIDO) == 2,
                "a Jinzen after the cooldown left the bond at " + data.zanpakuto().bond() + " and the kidō lean at "
                        + data.zanpakuto().affinity(ZanpakutoType.KIDO));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void whispersComeOnceAtEachQuarter(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 200.0F);
        Player player = mock(helper, data);
        player.getInventory().setItem(9, asauchi());
        ZanpakutoEvents.giveBond(player, data, 300, NOW);
        helper.assertTrue(data.zanpakuto().whispered() == 1, "a bond of 300 whispered " + data.zanpakuto().whispered() + " times");
        ZanpakutoEvents.giveBond(player, data, 300, NOW);
        helper.assertTrue(data.zanpakuto().whispered() == 2, "a bond of 600 whispered " + data.zanpakuto().whispered() + " times");
        int gained = ZanpakutoEvents.giveBond(player, data, 500, NOW);
        helper.assertTrue(gained == 400 && data.zanpakuto().bond() == ZanpakutoBond.FULL,
                "a bond of 600 grew by " + gained + " to " + data.zanpakuto().bond() + ", not to full");
        helper.assertTrue(data.zanpakuto().whispered() == 4, "a full bond whispered " + data.zanpakuto().whispered() + " times");
        helper.assertTrue(data.zanpakuto().spirit().isPresent(), "a full bond chose no spirit");
        helper.assertTrue(ZanpakutoEvents.giveBond(player, data, 10, NOW) == 0, "a full bond grew further");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void purifyingWithTheAsauchiBondsAndLeansPower(GameTestHelper helper) {
        SpiderHollow hollow = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 8);
        hollow.setNoAi(true);
        float reiatsu = hollow.soul().maxReiryoku();
        int expected = (int) Math.min(50L, Math.round(reiatsu * 0.5));
        SoulData data = soul(SoulState.SHINIGAMI, 200.0F);
        Player player = mock(helper, data);
        ItemStack blade = asauchi();
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);

        HollowEvents.onPurified(player, hollow, blade);
        ZanpakutoBond bond = data.zanpakuto();
        helper.assertTrue(bond.bond() == expected,
                "purifying a Hollow of reiatsu " + reiatsu + " gave " + bond.bond() + " bond, not " + expected);
        helper.assertTrue(bond.affinity(ZanpakutoType.POWER) == 2 && bond.affinity(ZanpakutoType.CONSTANT_RELEASE) == 0,
                "purifying leaned " + bond.affinity(ZanpakutoType.POWER) + " to power and "
                        + bond.affinity(ZanpakutoType.CONSTANT_RELEASE) + " to constant release");

        data.setReiatsuReleased(true);
        HollowEvents.onPurified(player, hollow, blade);
        bond = data.zanpakuto();
        helper.assertTrue(bond.bond() == 2 * expected, "a second purification left the bond at " + bond.bond());
        helper.assertTrue(bond.affinity(ZanpakutoType.POWER) == 4 && bond.affinity(ZanpakutoType.CONSTANT_RELEASE) == 2,
                "purifying with reiatsu released leaned " + bond.affinity(ZanpakutoType.POWER) + " to power and "
                        + bond.affinity(ZanpakutoType.CONSTANT_RELEASE) + " to constant release");

        ItemStack own = asauchi();
        ZanpakutoItem.bind(own, player, ZanpakutoSpirits.ZABIMARU.location(), spirit(helper, ZanpakutoSpirits.ZABIMARU), false);
        HollowEvents.onPurified(player, hollow, own);
        helper.assertTrue(data.zanpakuto().equals(bond), "purifying with a blade that already has a name changed the bond");
        hollow.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void closeCallsLeanConstantMostForSubstitutes(GameTestHelper helper) {
        SoulData shinigami = soul(SoulState.SHINIGAMI, 200.0F);
        Player first = mock(helper, shinigami);
        first.getInventory().setItem(9, asauchi());
        ZanpakutoEvents.onCloseCall(first, shinigami, NOW);
        helper.assertTrue(shinigami.zanpakuto().bond() == 15 && shinigami.zanpakuto().affinity(ZanpakutoType.CONSTANT_RELEASE) == 5,
                "a Shinigami's close call gave " + shinigami.zanpakuto().bond() + " bond and a lean of "
                        + shinigami.zanpakuto().affinity(ZanpakutoType.CONSTANT_RELEASE));

        SoulData substitute = ShinigamiTests.substitute(200.0F);
        Player second = mock(helper, substitute);
        second.getInventory().setItem(9, asauchi());
        ZanpakutoEvents.onCloseCall(second, substitute, NOW);
        helper.assertTrue(substitute.zanpakuto().bond() == 30 && substitute.zanpakuto().affinity(ZanpakutoType.CONSTANT_RELEASE) == 10,
                "a Substitute's close call gave " + substitute.zanpakuto().bond() + " bond and a lean of "
                        + substitute.zanpakuto().affinity(ZanpakutoType.CONSTANT_RELEASE));
        helper.succeed();
    }

    // ---- The hidden leaning and the spirit ----

    /** Max 200, Zanjutsu 5, Hakuda 3, Reiatsu Control 2, and a raw leaning of 10 to power and 4 to kidō. */
    private static SoulData trained(SoulData data) {
        data.setLevel(Stat.ZANJUTSU, 5);
        data.setLevel(Stat.HAKUDA, 3);
        data.setLevel(Stat.REIATSU_CONTROL, 2);
        data.setZanpakuto(ZanpakutoBond.NONE.withAffinity(ZanpakutoType.POWER, 10).withAffinity(ZanpakutoType.KIDO, 4));
        return data;
    }

    @GameTest(template = "empty")
    public static void effectiveAffinityAddsTrainingReiryokuAndTheSubstituteLean(GameTestHelper helper) {
        Map<ZanpakutoType, Integer> shinigami = ZanpakutoLogic.effectiveAffinity(trained(soul(SoulState.SHINIGAMI, 200.0F)));
        helper.assertTrue(shinigami.get(ZanpakutoType.POWER) == 23,
                "effective power was " + shinigami.get(ZanpakutoType.POWER) + ", not 23");
        helper.assertTrue(shinigami.get(ZanpakutoType.KIDO) == 8, "effective kidō was " + shinigami.get(ZanpakutoType.KIDO) + ", not 8");
        helper.assertTrue(shinigami.get(ZanpakutoType.CONSTANT_RELEASE) == 16,
                "effective constant release was " + shinigami.get(ZanpakutoType.CONSTANT_RELEASE) + ", not 16");
        Map<ZanpakutoType, Integer> substitute = ZanpakutoLogic.effectiveAffinity(trained(ShinigamiTests.substitute(200.0F)));
        helper.assertTrue(substitute.get(ZanpakutoType.CONSTANT_RELEASE) == 76,
                "a Substitute's effective constant release was " + substitute.get(ZanpakutoType.CONSTANT_RELEASE) + ", not 76");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leaningBreaksTiesAsWritten(GameTestHelper helper) {
        // Under 10 reiryoku, raw reiryoku adds nothing to constant release.
        List<ZanpakutoType> none = ZanpakutoLogic.leaningOrder(soul(SoulState.SHINIGAMI, 5.0F));
        helper.assertTrue(none.equals(List.of(ZanpakutoType.POWER, ZanpakutoType.CONSTANT_RELEASE, ZanpakutoType.KIDO)),
                "with no leaning the order was " + none);

        SoulData substitute = ShinigamiTests.substitute(5.0F);
        substitute.setZanpakuto(ZanpakutoBond.NONE.withAffinity(ZanpakutoType.POWER, 60));
        List<ZanpakutoType> substituteOrder = ZanpakutoLogic.leaningOrder(substitute);
        helper.assertTrue(substituteOrder.equals(List.of(ZanpakutoType.CONSTANT_RELEASE, ZanpakutoType.POWER, ZanpakutoType.KIDO)),
                "a Substitute tied between power and constant release leaned " + substituteOrder);

        SoulData shinigami = soul(SoulState.SHINIGAMI, 5.0F);
        shinigami.setZanpakuto(ZanpakutoBond.NONE.withAffinity(ZanpakutoType.POWER, 60)
                .withAffinity(ZanpakutoType.CONSTANT_RELEASE, 60));
        List<ZanpakutoType> shinigamiOrder = ZanpakutoLogic.leaningOrder(shinigami);
        helper.assertTrue(shinigamiOrder.equals(List.of(ZanpakutoType.POWER, ZanpakutoType.CONSTANT_RELEASE, ZanpakutoType.KIDO)),
                "a Shinigami tied between power and constant release leaned " + shinigamiOrder);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shippedSpiritsLoad(GameTestHelper helper) {
        Registry<ZanpakutoSpirit> spirits = spirits(helper);
        ZanpakutoSpirit zangetsu = spirits.get(ZanpakutoSpirits.ZANGETSU);
        helper.assertTrue(zangetsu != null, "bleach:zangetsu did not load");
        helper.assertTrue(zangetsu.type() == ZanpakutoType.CONSTANT_RELEASE && zangetsu.isConstant(),
                "Zangetsu is a " + zangetsu.type() + " spirit");
        helper.assertTrue(zangetsu.command().isEmpty(), "Zangetsu has a release command");
        helper.assertTrue(zangetsu.ability() == ZanpakutoSpirit.Ability.GETSUGA_TENSHO, "Zangetsu's technique is " + zangetsu.ability());
        helper.assertTrue(zangetsu.shikai().model() == 1, "Zangetsu's Shikai uses model " + zangetsu.shikai().model());
        helper.assertTrue(zangetsu.tests().size() == 2 && zangetsu.tests().get(0).kind() == JinzenStepKind.RIBBONS
                        && zangetsu.tests().get(1).kind() == JinzenStepKind.QUESTION && zangetsu.tests().get(1).answers().size() == 3,
                "Zangetsu's test is not the red ribbon and then a question with three answers");

        ZanpakutoSpirit zabimaru = spirits.get(ZanpakutoSpirits.ZABIMARU);
        helper.assertTrue(zabimaru != null, "bleach:zabimaru did not load");
        helper.assertTrue(zabimaru.type() == ZanpakutoType.POWER, "Zabimaru is a " + zabimaru.type() + " spirit");
        helper.assertTrue(zabimaru.command().isPresent(), "Zabimaru has no release command");
        helper.assertTrue(zabimaru.ability() == ZanpakutoSpirit.Ability.SEGMENT_LASH, "Zabimaru's technique is " + zabimaru.ability());
        helper.assertTrue(Math.abs(zabimaru.shikai().reach() - 3.0F) < EPSILON, "Zabimaru's Shikai reaches " + zabimaru.shikai().reach());
        helper.assertTrue(zabimaru.tests().size() == 2 && zabimaru.tests().get(0).kind() == JinzenStepKind.QUESTION
                        && zabimaru.tests().get(1).kind() == JinzenStepKind.FANGS,
                "Zabimaru's test is not a question and then the fangs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spiritChoiceFollowsTheLeaning(GameTestHelper helper) {
        Registry<ZanpakutoSpirit> spirits = spirits(helper);
        UUID id = new UUID(3L, 5L);
        ResourceLocation zangetsu = ZanpakutoSpirits.ZANGETSU.location();
        ResourceLocation zabimaru = ZanpakutoSpirits.ZABIMARU.location();

        SoulData power = soul(SoulState.SHINIGAMI, 40.0F);
        power.setZanpakuto(ZanpakutoBond.NONE.withAffinity(ZanpakutoType.POWER, 100));
        helper.assertTrue(ZanpakutoLogic.chooseSpirit(power, id, spirits).equals(Optional.of(zabimaru)),
                "a leaning to power chose " + ZanpakutoLogic.chooseSpirit(power, id, spirits));

        SoulData constant = soul(SoulState.SHINIGAMI, 40.0F);
        constant.setZanpakuto(ZanpakutoBond.NONE.withAffinity(ZanpakutoType.CONSTANT_RELEASE, 100));
        helper.assertTrue(ZanpakutoLogic.chooseSpirit(constant, id, spirits).equals(Optional.of(zangetsu)),
                "a leaning to constant release chose " + ZanpakutoLogic.chooseSpirit(constant, id, spirits));

        // No kidō spirit ships, so the next leaning answers.
        SoulData kido = soul(SoulState.SHINIGAMI, 40.0F);
        kido.setZanpakuto(ZanpakutoBond.NONE.withAffinity(ZanpakutoType.KIDO, 100).withAffinity(ZanpakutoType.POWER, 50));
        helper.assertTrue(ZanpakutoLogic.chooseSpirit(kido, id, spirits).equals(Optional.of(zabimaru)),
                "a leaning to kidō, then power, chose " + ZanpakutoLogic.chooseSpirit(kido, id, spirits));

        SoulData substitute = ShinigamiTests.substitute(40.0F);
        helper.assertTrue(ZanpakutoLogic.chooseSpirit(substitute, id, spirits).equals(Optional.of(zangetsu)),
                "a Substitute with no leaning chose " + ZanpakutoLogic.chooseSpirit(substitute, id, spirits));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spiritChoiceIsStableForOneSoul(GameTestHelper helper) {
        Registry<ZanpakutoSpirit> spirits = spirits(helper);
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        SoulData data = soul(SoulState.SHINIGAMI, 120.0F);
        data.setZanpakuto(ZanpakutoBond.NONE.withAffinity(ZanpakutoType.POWER, 7).withAffinity(ZanpakutoType.KIDO, 3));
        Optional<ResourceLocation> first = ZanpakutoLogic.chooseSpirit(data, id, spirits);
        Optional<ResourceLocation> second = ZanpakutoLogic.chooseSpirit(data, id, spirits);
        helper.assertTrue(first.isPresent() && first.equals(second), "the same soul chose " + first + " and then " + second);

        for (UUID soul : List.of(new UUID(0L, 0L), new UUID(1L, 2L), new UUID(-1L, -1L), id,
                new UUID(Long.MIN_VALUE, 7L))) {
            int index = ZanpakutoLogic.tieBreakIndex(soul, 3);
            helper.assertTrue(index >= 0 && index < 3 && index == Math.floorMod(soul.hashCode(), 3),
                    "the tie break for " + soul + " was " + index);
        }
        helper.succeed();
    }

    // ---- Jinzen ----

    @GameTest(template = "empty")
    public static void jinzenScenesFollowTheBond(GameTestHelper helper) {
        Optional<ZanpakutoSpirit> spirit = Optional.of(spirit(helper, ZanpakutoSpirits.ZANGETSU));
        Optional<ZanpakutoSpirit> missing = Optional.empty();
        ZanpakutoBond full = ZanpakutoBond.NONE.withBond(ZanpakutoBond.FULL);

        SoulData silent = soul(SoulState.SHINIGAMI, 500.0F);
        silent.setZanpakuto(ZanpakutoBond.NONE.withBond(100));
        helper.assertTrue(JinzenLogic.sceneFor(silent, spirit, NOW) == JinzenScene.SILENT, "a bond of 100 was not silent");

        SoulData stirring = soul(SoulState.SHINIGAMI, 500.0F);
        stirring.setZanpakuto(ZanpakutoBond.NONE.withBond(600));
        helper.assertTrue(JinzenLogic.sceneFor(stirring, spirit, NOW) == JinzenScene.STIRRING, "a bond of 600 did not stir");

        SoulData student = ShinigamiTests.student(500.0F);
        student.setZanpakuto(full);
        helper.assertTrue(JinzenLogic.sceneFor(student, spirit, NOW) == JinzenScene.STUDENT,
                "a student's full bond showed " + JinzenLogic.sceneFor(student, spirit, NOW));

        SoulData weak = soul(SoulState.SHINIGAMI, 50.0F);
        weak.setZanpakuto(full);
        helper.assertTrue(JinzenLogic.sceneFor(weak, spirit, NOW) == JinzenScene.WEAK,
                "a full bond with 50 reiryoku showed " + JinzenLogic.sceneFor(weak, spirit, NOW));

        SoulData resting = soul(SoulState.SHINIGAMI, 500.0F);
        resting.setZanpakuto(full.failed(NOW - 100L));
        helper.assertTrue(JinzenLogic.sceneFor(resting, spirit, NOW) == JinzenScene.RESTING,
                "a test failed just now showed " + JinzenLogic.sceneFor(resting, spirit, NOW));

        SoulData ready = soul(SoulState.SHINIGAMI, 500.0F);
        ready.setZanpakuto(full);
        helper.assertTrue(JinzenLogic.sceneFor(ready, missing, NOW) == JinzenScene.UNKNOWN, "a full bond with no spirit was not lost");
        helper.assertTrue(JinzenLogic.sceneFor(ready, spirit, NOW) == JinzenScene.MEETING,
                "a full bond with its spirit showed " + JinzenLogic.sceneFor(ready, spirit, NOW));

        SoulData named = soul(SoulState.SHINIGAMI, 500.0F);
        named.setZanpakuto(full.asNamed(Component.literal("Zangetsu"), true));
        helper.assertTrue(JinzenLogic.sceneFor(named, spirit, NOW) == JinzenScene.VISIT, "a named soul did not visit its spirit");
        helper.assertTrue(JinzenLogic.sceneFor(named, missing, NOW) == JinzenScene.UNKNOWN, "a named soul whose spirit is gone found it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void jinzenRefusesWithoutStillGroundOrAnEmptyOffHand(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 500.0F);
        Player player = mock(helper, data);
        ItemStack blade = asauchi();
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        helper.assertTrue("off_hand".equals(JinzenLogic.refusal(player, data, blade, NOW)),
                "a shield in the off hand did not refuse Jinzen");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setOnGround(false);
        helper.assertTrue("not_grounded".equals(JinzenLogic.refusal(player, data, blade, NOW)), "Jinzen in the air was not refused");
        player.setOnGround(true);

        Player human = mock(helper, soul(SoulState.HUMAN, 400.0F));
        helper.assertTrue("cannot".equals(JinzenLogic.refusal(human, SoulLogic.get(human), blade, NOW)), "a Human sat in Jinzen");
        Player giver = mock(helper, ShinigamiTests.lentGiver());
        helper.assertTrue("power_lent".equals(JinzenLogic.refusal(giver, SoulLogic.get(giver), blade, NOW + 1L)),
                "a Shinigami who lent their power sat in Jinzen");
        ItemStack others = someoneElsesBlade(helper, ZanpakutoSpirits.ZABIMARU);
        helper.assertTrue("not_yours".equals(JinzenLogic.refusal(player, data, others, NOW)),
                "Jinzen with someone else's blade was not refused");
        Player owner = named(helper, ZanpakutoSpirits.ZABIMARU);
        ItemStack released = owner.getMainHandItem();
        ZanpakutoItem.setReleased(released, spirit(helper, ZanpakutoSpirits.ZABIMARU), true);
        helper.assertTrue("released".equals(JinzenLogic.refusal(owner, SoulLogic.get(owner), released, NOW)),
                "Jinzen with Zabimaru released was not refused");
        String ready = JinzenLogic.refusal(player, data, blade, NOW);
        helper.assertTrue(ready == null, "a Shinigami ready to sit was refused: " + ready);

        // Sitting still and sneaking keeps Jinzen settling, so only the drift can end it below.
        player.setShiftKeyDown(true);
        JinzenLogic.begin(player, data, NOW);
        helper.assertTrue(data.jinzen() != null, "sitting down started no Jinzen");
        JinzenLogic.tick(player, data, blade, 5, NOW + 5L);
        helper.assertTrue(data.jinzen() != null, "sitting still ended Jinzen");
        player.setPos(player.getX() + 1.0, player.getY(), player.getZ());
        JinzenLogic.tick(player, data, blade, 6, NOW + 6L);
        helper.assertTrue(data.jinzen() == null, "moving a block away did not end Jinzen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFullOffHandGetsTheUseInstead(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 500.0F);
        Player player = mock(helper, data);
        ItemStack blade = asauchi();
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        player.setShiftKeyDown(true);
        // Whatever the off hand holds has its turn next; a client says why only if it then does nothing.
        for (ItemStack offhand : List.of(new ItemStack(Items.SHIELD), new ItemStack(Items.COOKED_BEEF),
                new ItemStack(Items.TORCH), asauchi())) {
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            InteractionResult result = blade.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(result == InteractionResult.PASS, "the blade took the use from " + offhand + ": " + result);
            helper.assertTrue(!player.isUsingItem() && data.jinzen() == null, "Jinzen began past " + offhand);
        }
        helper.assertFalse(data.takeOffHandRefused(), "the server noted a refusal only a client explains");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void jinzenEndsWhenStruck(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 500.0F);
        Player player = mock(helper, data);
        player.setItemInHand(InteractionHand.MAIN_HAND, asauchi());
        player.startUsingItem(InteractionHand.MAIN_HAND);
        JinzenLogic.begin(player, data, NOW);
        JinzenLogic.open(player, data, NOW);
        helper.assertTrue(player.isUsingItem() && data.jinzen() != null, "Jinzen did not open");
        player.hurt(player.damageSources().generic(), 1.0F);
        helper.assertTrue(data.jinzen() == null, "a blow did not end Jinzen");
        helper.assertFalse(player.isUsingItem(), "a blow did not make the soul let go of the blade");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theRedRibbonMustBeTakenInTime(GameTestHelper helper) {
        Player player = meetsZangetsu(helper);
        SoulData data = SoulLogic.get(player);
        JinzenPage first = JinzenLogic.open(player, data, NOW);
        helper.assertTrue(first.scene() == JinzenScene.MEETING, "a full bond with Zangetsu opened on " + first.scene());
        JinzenStep ribbons = stepOf(helper, first, "the meeting set no test");
        helper.assertTrue(ribbons.kind() == JinzenStepKind.RIBBONS && ribbons.index() == 0,
                "Zangetsu's test did not start with the ribbons");
        helper.assertTrue(ribbons.highlight() == JinzenLogic.redRibbon(data.jinzen().seed()),
                "the page marked ribbon " + ribbons.highlight() + ", not the red one");
        helper.assertTrue(ribbons.ticks() == 160, "the ribbons allowed " + ribbons.ticks() + " ticks, not 160");

        int white = (ribbons.highlight() + 1) % JinzenStep.RIBBONS;
        long failedAt = NOW + 5L;
        JinzenPage failed = JinzenLogic.answer(player, data, data.jinzen().id(), 0, white, failedAt);
        helper.assertTrue(failed != null && failed.finished() && failed.step().isEmpty(), "taking a white ribbon did not end the test");
        helper.assertTrue(data.zanpakuto().testFailures() == 1 && data.zanpakuto().testFailedAt() == failedAt,
                "the failure was counted " + data.zanpakuto().testFailures() + " times, at " + data.zanpakuto().testFailedAt());

        long retry = failedAt + retryTicks() + 1L;
        JinzenStep slower = stepOf(helper, JinzenLogic.open(player, data, retry), "the test was not set again after the rest");
        helper.assertTrue(slower.ticks() == 200, "after one failure the ribbons allowed " + slower.ticks() + " ticks, not 200");
        JinzenPage question = JinzenLogic.answer(player, data, data.jinzen().id(), 0, slower.highlight(), retry + 5L);
        JinzenStep asked = stepOf(helper, question, "taking the red ribbon in time did not lead on");
        helper.assertTrue(asked.kind() == JinzenStepKind.QUESTION && asked.index() == 1, "the red ribbon led to " + asked.kind());

        JinzenStep late = stepOf(helper, JinzenLogic.open(player, data, retry), "a third Jinzen set no test");
        JinzenPage tooLate = JinzenLogic.answer(player, data, data.jinzen().id(), 0, late.highlight(),
                retry + late.ticks() + JinzenLogic.GRACE_TICKS + 1L);
        helper.assertTrue(tooLate != null && tooLate.finished() && data.zanpakuto().testFailures() == 2,
                "the red ribbon taken after the time ran out still passed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void questionAnswersAreShuffledOnTheServer(GameTestHelper helper) {
        int[] order = JinzenLogic.answerOrder(42L, 3);
        int[] sorted = order.clone();
        Arrays.sort(sorted);
        helper.assertTrue(Arrays.equals(sorted, new int[] {0, 1, 2}),
                "the order " + Arrays.toString(order) + " is not a shuffle of three");
        helper.assertTrue(Arrays.equals(order, JinzenLogic.answerOrder(42L, 3)), "the same seed shuffled the answers two ways");
        Set<Integer> positions = new HashSet<>();
        for (long seed = 0L; seed < 16L; seed++) {
            positions.add(JinzenLogic.correctPosition(JinzenLogic.answerOrder(seed, 3)));
        }
        helper.assertTrue(positions.size() >= 2, "the right answer was always shown at " + positions);

        Player player = meetsZangetsu(helper);
        SoulData data = SoulLogic.get(player);
        JinzenStep ribbons = stepOf(helper, JinzenLogic.open(player, data, NOW), "Jinzen did not open on Zangetsu's test");
        int session = data.jinzen().id();
        JinzenStep asked = stepOf(helper, JinzenLogic.answer(player, data, session, 0, ribbons.highlight(), NOW + 5L),
                "taking the red ribbon did not lead to the question");
        int[] shown = JinzenLogic.answerOrder(data.jinzen().seed(), 3);
        List<Component> answers = spirit(helper, ZanpakutoSpirits.ZANGETSU).tests().get(1).answers();
        helper.assertTrue(asked.answers().size() == answers.size(), "the question showed " + asked.answers().size() + " answers");
        for (int position = 0; position < shown.length; position++) {
            helper.assertTrue(asked.answers().get(position).equals(answers.get(shown[position])),
                    "answer " + position + " on the page is not the spirit's answer " + shown[position]);
        }
        JinzenPage reveal = JinzenLogic.answer(player, data, session, asked.index(), JinzenLogic.correctPosition(shown), NOW + 10L);
        helper.assertTrue(reveal != null && reveal.finished() && hasLine(reveal, "jinzen.bleach.reveal.name"),
                "the right answer did not bring the name");
        helper.assertTrue(data.zanpakuto().named(), "the soul did not learn the name");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void learningTheNameBindsTheHeldBlade(GameTestHelper helper) {
        Player player = meetsZangetsu(helper);
        SoulData data = SoulLogic.get(player);
        JinzenPage last = passZangetsu(helper, player, data, NOW);
        ZanpakutoSpirit zangetsu = spirit(helper, ZanpakutoSpirits.ZANGETSU);
        ItemStack held = player.getMainHandItem();
        ZanpakutoBlade blade = ZanpakutoItem.blade(held);
        helper.assertTrue(blade.isOwnedBy(player.getUUID()), "the blade in hand was not bound to the soul");
        helper.assertTrue(blade.spirit().equals(Optional.of(ZanpakutoSpirits.ZANGETSU.location())), "the blade holds " + blade.spirit());
        helper.assertTrue(blade.constant() && !blade.released(), "a newly named Zangetsu is not a sealed constant release");
        Component name = held.get(DataComponents.ITEM_NAME);
        helper.assertTrue(name != null && name.getContents().equals(zangetsu.name().getContents()), "the blade is called " + name);
        helper.assertTrue(data.zanpakuto().named(), "the bond is not named");
        helper.assertTrue(Math.abs(data.maxReiryoku() - 510.0F) < EPSILON,
                "learning the name grew max reiryoku to " + data.maxReiryoku());
        helper.assertTrue(last.finished() && last.title().getContents().equals(zangetsu.name().getContents()),
                "the last page was not the name");
        helper.succeed();
    }

    // ---- The blade in the inventory ----

    @GameTest(template = "empty")
    public static void aNamedSoulsAsauchiReforms(GameTestHelper helper) {
        Player player = named(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = SoulLogic.get(player);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, asauchi());
        ZanpakutoEvents.reconcile(player, data);
        ItemStack reformed = player.getInventory().getItem(0);
        helper.assertTrue(ZanpakutoItem.isOwnedBy(reformed, player.getUUID()), "the lost blade did not re-form in the Asauchi");
        helper.assertTrue(model(reformed) == 2, "the re-formed Zabimaru uses model " + model(reformed));
        helper.assertTrue(ZanpakutoItem.blade(reformed).spirit().equals(Optional.of(ZanpakutoSpirits.ZABIMARU.location())),
                "the re-formed blade holds " + ZanpakutoItem.blade(reformed).spirit());

        data.setZanpakuto(ZanpakutoBond.NONE);
        ZanpakutoEvents.reconcile(player, data);
        helper.assertTrue(player.getInventory().getItem(0).getComponentsPatch().isEmpty(),
                "a blade whose name was forgotten kept " + player.getInventory().getItem(0).getComponentsPatch());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownBladesAreKeptThroughDeath(GameTestHelper helper) {
        helper.assertTrue(BleachConfig.get(BleachConfig.KEEP_ON_DEATH), "keep_on_death is off by default");
        Player player = named(helper, ZanpakutoSpirits.ZABIMARU);
        ItemStack own = player.getMainHandItem().copy();
        ZanpakutoItem.setReleased(own, spirit(helper, ZanpakutoSpirits.ZABIMARU), true);
        ItemStack others = someoneElsesBlade(helper, ZanpakutoSpirits.ZANGETSU);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(helper.getLevel(), at.x, at.y, at.z, own),
                new ItemEntity(helper.getLevel(), at.x, at.y, at.z, others)));

        ZanpakutoEvents.keepBlades(player, drops);
        helper.assertTrue(drops.size() == 1 && !ZanpakutoItem.isOwnedBy(drops.get(0).getItem(), player.getUUID()),
                "the drops left were " + drops.size() + ", and not only someone else's blade");

        // Kept through a save as well: a soul that quits on the death screen respawns later from its saved data.
        Player reloaded = helper.makeMockPlayer(GameType.SURVIVAL);
        reloaded.load(player.saveWithoutId(new CompoundTag()));
        Player respawned = helper.makeMockPlayer(GameType.SURVIVAL);
        ZanpakutoEvents.restoreBlades(reloaded, respawned);
        ItemStack kept = ItemStack.EMPTY;
        for (int slot = 0; slot < respawned.getInventory().getContainerSize(); slot++) {
            if (ZanpakutoItem.isOwnedBy(respawned.getInventory().getItem(slot), player.getUUID())) {
                kept = respawned.getInventory().getItem(slot);
            }
        }
        helper.assertTrue(bladesOwnedBy(respawned, player.getUUID()) == 1, "the kept blade did not come back once");
        helper.assertTrue(!ZanpakutoItem.blade(kept).released() && model(kept) == 2, "the kept blade came back released");
        // A second hand-over shows the kept copies were cleared.
        ZanpakutoEvents.restoreBlades(reloaded, respawned);
        helper.assertTrue(bladesOwnedBy(respawned, player.getUUID()) == 1, "the kept blade came back twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void asauchiHandoutsSkipThoseWhoCarryOneOfTheirOwn(GameTestHelper helper) {
        float aware = (float) (double) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD);
        Player giver = mock(helper, soul(SoulState.SHINIGAMI, 200.0F));
        Player human = mock(helper, soul(SoulState.HUMAN, aware));
        human.getInventory().setItem(5, asauchi());
        ShinigamiEvents.transfer(giver, human);
        helper.assertTrue(human.getInventory().countItem(ModItems.ASAUCHI.get()) == 1,
                "a Human carrying an Asauchi was handed another: " + human.getInventory().countItem(ModItems.ASAUCHI.get()));

        Player secondGiver = mock(helper, soul(SoulState.SHINIGAMI, 200.0F));
        Player holder = mock(helper, soul(SoulState.HUMAN, aware));
        holder.getInventory().setItem(5, someoneElsesBlade(helper, ZanpakutoSpirits.ZABIMARU));
        ShinigamiEvents.transfer(secondGiver, holder);
        helper.assertTrue(holder.getInventory().countItem(ModItems.ASAUCHI.get()) == 2,
                "a Human carrying only someone else's blade was not handed one of their own");

        Player keeper = named(helper, ZanpakutoSpirits.ZABIMARU);
        helper.assertFalse(ShinigamiEvents.mayReceiveAsauchi(keeper, SoulLogic.get(keeper), NOW + DAY),
                "the Academy would hand an Asauchi to a soul carrying its own zanpakutō");
        Player borrower = mock(helper, soul(SoulState.SHINIGAMI, 200.0F));
        borrower.getInventory().setItem(5, someoneElsesBlade(helper, ZanpakutoSpirits.ZABIMARU));
        helper.assertTrue(ShinigamiEvents.mayReceiveAsauchi(borrower, SoulLogic.get(borrower), NOW + DAY),
                "someone else's blade kept the Academy from handing an Asauchi over");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bladeComponentAndBondSurviveSavingAndSync(GameTestHelper helper) {
        ZanpakutoBlade blade = new ZanpakutoBlade(Optional.of(new UUID(11L, 13L)), "Ichigo",
                Optional.of(ZanpakutoSpirits.ZANGETSU.location()), ZanpakutoStage.SHIKAI, true, 0xBFEFFF);
        JsonElement json = ZanpakutoBlade.CODEC.encodeStart(JsonOps.INSTANCE, blade).getOrThrow();
        ZanpakutoBlade saved = ZanpakutoBlade.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertTrue(saved.equals(blade), "saved " + blade + " but loaded " + saved);
        ByteBuf buffer = Unpooled.buffer();
        ZanpakutoBlade.STREAM_CODEC.encode(buffer, blade);
        ZanpakutoBlade sent = ZanpakutoBlade.STREAM_CODEC.decode(buffer);
        helper.assertTrue(sent.equals(blade), "sent " + blade + " but read " + sent);

        SoulData data = soul(SoulState.SHINIGAMI, 300.0F);
        ZanpakutoBond bond = new ZanpakutoBond(640,
                Map.of(ZanpakutoType.POWER, 12, ZanpakutoType.KIDO, 3, ZanpakutoType.CONSTANT_RELEASE, 7),
                Optional.of(ZanpakutoSpirits.ZABIMARU.location()), true,
                Component.translatable("zanpakuto.bleach.zabimaru").withColor(0xE0483A), false, true, 4,
                NOW - 50L, NOW - 400L, 2, 3, 11, true, NOW - 900L);
        data.setZanpakuto(bond);
        Tag tag = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        SoulData loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        helper.assertTrue(loaded.zanpakuto().equals(bond), "saved " + bond + " but loaded " + loaded.zanpakuto());

        ZanpakutoBond client = data.forClient().zanpakuto();
        helper.assertTrue(client.affinity().isEmpty(), "the owner's client was sent the hidden leaning " + client.affinity());
        helper.assertTrue(client.equals(bond.withoutAffinity()), "the owner's client lost more than the leaning: " + client);

        helper.assertTrue(tag instanceof CompoundTag, "a soul did not save as a compound tag");
        CompoundTag older = ((CompoundTag) tag).copy();
        older.remove("zanpakuto");
        helper.assertTrue(SoulData.CODEC.parse(NbtOps.INSTANCE, older).getOrThrow().zanpakuto().equals(ZanpakutoBond.NONE),
                "a soul saved before zanpakutō loaded with a bond");
        CompoundTag broken = ((CompoundTag) tag).copy();
        broken.putString("zanpakuto", "x");
        SoulData lenient = SoulData.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
        helper.assertTrue(lenient.zanpakuto().equals(ZanpakutoBond.NONE), "a broken bond did not fall back to none");
        helper.assertTrue(lenient.state() == SoulState.SHINIGAMI && Math.abs(lenient.reiryoku() - 300.0F) < EPSILON,
                "a broken bond took the rest of the soul with it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sheathModelFollowsTheHotbarBlade(GameTestHelper helper) {
        Player player = named(helper, ZanpakutoSpirits.ZABIMARU);
        Inventory inventory = player.getInventory();
        ItemStack zabimaru = player.getMainHandItem();
        inventory.setItem(0, ItemStack.EMPTY);
        inventory.setItem(3, zabimaru);
        inventory.selected = 0;
        helper.assertTrue(ShinigamiLogic.sheathedModel(player) == 2,
                "sealed Zabimaru was sheathed as " + ShinigamiLogic.sheathedModel(player));

        ItemStack zangetsu = someoneElsesBlade(helper, ZanpakutoSpirits.ZANGETSU);
        ZanpakutoItem.setReleased(zangetsu, spirit(helper, ZanpakutoSpirits.ZANGETSU), true);
        inventory.setItem(3, zangetsu);
        helper.assertTrue(ShinigamiLogic.sheathedModel(player) == 1,
                "released Zangetsu was sheathed as " + ShinigamiLogic.sheathedModel(player));
        inventory.setItem(3, asauchi());
        helper.assertTrue(ShinigamiLogic.sheathedModel(player) == 0,
                "a plain Asauchi was sheathed as " + ShinigamiLogic.sheathedModel(player));
        inventory.selected = 3;
        helper.assertTrue(ShinigamiLogic.sheathedModel(player) == -1, "the blade in hand was also sheathed");

        for (int sheath : new int[] {-1, 3}) {
            SoulAppearance look = new SoulAppearance(SoulState.SHINIGAMI, 0.0F, false, sheath);
            ByteBuf buffer = Unpooled.buffer();
            SoulAppearance.STREAM_CODEC.encode(buffer, look);
            SoulAppearance read = SoulAppearance.STREAM_CODEC.decode(buffer);
            helper.assertTrue(read.equals(look) && read.bladeSheathed() == (sheath >= 0), "sent " + look + " but read " + read);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nextStepNamesTheBondAndTheName(GameTestHelper helper) {
        ZanpakutoBond full = ZanpakutoBond.NONE.withBond(ZanpakutoBond.FULL);
        ZanpakutoBond named = full.asNamed(Component.literal("Zabimaru"), false);

        helper.assertTrue(ShinigamiLogic.nextStepKey(soul(SoulState.SHINIGAMI, 200.0F), NOW).equals("shinigami"),
                "wrong step for a Shinigami with no bond");
        SoulData bonded = soul(SoulState.SHINIGAMI, 200.0F);
        bonded.setZanpakuto(full);
        helper.assertTrue(ShinigamiLogic.nextStepKey(bonded, NOW).equals("shinigami.bonded"), "wrong step for a full bond");
        SoulData knows = soul(SoulState.SHINIGAMI, 200.0F);
        knows.setZanpakuto(named);
        helper.assertTrue(ShinigamiLogic.nextStepKey(knows, NOW).equals("shinigami.named"), "wrong step for a named Shinigami");
        SoulData substitute = ShinigamiTests.substitute(60.0F);
        substitute.setZanpakuto(full);
        helper.assertTrue(ShinigamiLogic.nextStepKey(substitute, NOW).equals("substitute.bonded"), "wrong step for a bonded Substitute");
        substitute.setZanpakuto(named);
        helper.assertTrue(ShinigamiLogic.nextStepKey(substitute, NOW).equals("substitute.named"), "wrong step for a named Substitute");
        SoulData student = ShinigamiTests.student(60.0F);
        student.setZanpakuto(full);
        helper.assertTrue(ShinigamiLogic.nextStepKey(student, NOW).equals("student.bonded"), "wrong step for a bonded student");
        SoulData giver = ShinigamiTests.lentGiver();
        giver.setZanpakuto(named);
        helper.assertTrue(ShinigamiLogic.nextStepKey(giver, NOW + 1L).equals("lent"), "a lent power did not come first");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFullNightsSleepBondsOnce(GameTestHelper helper) {
        SoulData data = soul(SoulState.SHINIGAMI, 200.0F);
        Player player = mock(helper, data);
        player.getInventory().setItem(9, asauchi());
        helper.assertTrue(ZanpakutoEvents.onFullNightsSleep(player, data, NOW) == 30, "a night's sleep did not bond 30");
        helper.assertTrue(ZanpakutoEvents.onFullNightsSleep(player, data, NOW + 100L) == 0, "a second sleep in the cooldown bonded");
        helper.assertTrue(ZanpakutoEvents.onFullNightsSleep(player, data, NOW + DAY) == 30, "a sleep after the cooldown did not bond");

        SoulData empty = soul(SoulState.SHINIGAMI, 200.0F);
        Player sleeper = mock(helper, empty);
        helper.assertTrue(ZanpakutoEvents.onFullNightsSleep(sleeper, empty, NOW) == 0, "a sleep with no Asauchi bonded");
        helper.succeed();
    }
}
