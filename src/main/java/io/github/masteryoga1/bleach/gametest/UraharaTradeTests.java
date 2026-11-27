package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.KisukeUrahara;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.Tenshintai;
import io.github.masteryoga1.bleach.entity.UraharaCounter;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Kisuke Urahara's trades (7n4): what he sells each kind of soul and for how much, sales paid in emeralds through the
 * vanilla merchant screen with the day's stock of bait, and the press and what ends a trade; and his Tenshintai, one at
 * a time (8k1). Each test closes the screens it opened, takes away what it set down or joined and puts back the bait
 * setting, however it ends.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class UraharaTradeTests {
    /** Where he stands; where the soul he serves stands, 2 blocks off; and 9 blocks off, past his reach. */
    private static final Vec3 HIM = new Vec3(1.5, 1.0, 1.5), NEAR = new Vec3(3.5, 1.0, 1.5), FAR = new Vec3(10.5, 1.0, 1.5);

    private UraharaTradeTests() {
    }

    /**
     * Test 1. Each soul is sold what it can use, cheaper first, for emeralds at prices that never move and with no xp,
     * and his trade button stands in a soul's view just when he sells that soul something; bait sold today is sold out,
     * and with bait turned off he sells none, the button greyed for a soul he would sell only bait.
     */
    @GameTest(template = "empty")
    public static void uraharaSellsEachSoulWhatItCanUse(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        Item pager = ModItems.DENREISHINKI.get();
        Item bait = ModItems.HOLLOW_BAIT.get();
        SoulData lent = ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F);
        lent.setShinigami(lent.shinigami().withPowerLentUntil(now + 1200L));
        SoulData hybrid = QuincyTests.quincy(Bloodline.ECHT, 1, 40.0F);
        hybrid.setTier(PowerPath.SHINIGAMI, 1);
        SoulData visored = ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F);
        VisoredLogic.setRecord(visored, VisoredLogic.record(visored).withRootedAt(0L).withSubduedAt(1L));
        Map<SoulData, List<Item>> expected = new LinkedHashMap<>();
        expected.put(ShinigamiTests.soul(SoulState.HUMAN, 1.0F), List.of());
        expected.put(awareHuman(), List.of(bait));
        expected.put(ShinigamiTests.soul(SoulState.PLUS, 40.0F), List.of(bait));
        expected.put(ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F), List.of(bait));
        expected.put(ShinigamiTests.student(40.0F), List.of(pager, bait));
        expected.put(ShinigamiTests.substitute(60.0F), List.of(pager, bait));
        // A Shinigami whose power is lent away, whose pager will not answer, and a Quincy who also holds that power.
        expected.put(lent, List.of(bait));
        expected.put(QuincyTests.quincy(Bloodline.ECHT, 1, 40.0F), List.of(bait));
        expected.put(hybrid, List.of(pager, bait));
        expected.put(ShinigamiTests.soul(SoulState.HOLLOW, 40.0F), List.of());
        expected.put(ShinigamiTests.soul(SoulState.ARRANCAR, 40.0F), List.of());
        expected.put(visored, List.of(pager, bait));
        expected.forEach((soul, wares) -> {
            DialogueView view = KisukeUrahara.viewFor(soul);
            List<Item> sold = UraharaCounter.waresFor(soul, now);
            helper.assertTrue(sold.equals(wares), "Urahara sells " + sold + " to a soul in " + view + ", not " + wares);
            helper.assertTrue(NpcAction.offeredIn(view).contains(NpcAction.TRADE) == !wares.isEmpty()
                    && KisukeUrahara.unreadyFor(soul, now).isEmpty(), view + " offers " + NpcAction.offeredIn(view)
                    + ", greyed " + KisukeUrahara.unreadyFor(soul, now) + ", to a soul he sells " + wares);
        });
        MerchantOffers fresh = UraharaCounter.offersFor(List.of(pager, bait), false, false);
        MerchantOffers soldOut = UraharaCounter.offersFor(List.of(bait), true, false);
        helper.assertTrue(fresh.size() == 2 && sells(fresh.get(0), pager, 5, UraharaCounter.UNLIMITED, 0)
                && sells(fresh.get(1), bait, 16, 1, 0) && !fresh.get(1).isOutOfStock(),
                "his offers are " + fresh.get(0).getCostA() + " for " + fresh.get(0).getResult() + " and "
                        + fresh.get(1).getCostA() + " for " + fresh.get(1).getResult());
        helper.assertTrue(soldOut.size() == 1 && sells(soldOut.get(0), bait, 16, 1, 1) && soldOut.get(0).isOutOfStock(),
                "bait sold today is not sold out");
        boolean baitOn = BleachConfig.get(BleachConfig.BAIT_ENABLED);
        // Set for these calls, in this one tick, and put back before any other test runs.
        BleachConfig.BAIT_ENABLED.set(false);
        try {
            helper.assertTrue(UraharaCounter.waresFor(awareHuman(), now).isEmpty()
                    && KisukeUrahara.unreadyFor(awareHuman(), now).equals(List.of(NpcAction.TRADE))
                    && UraharaCounter.waresFor(ShinigamiTests.substitute(60.0F), now).equals(List.of(pager))
                    && KisukeUrahara.unreadyFor(ShinigamiTests.substitute(60.0F), now).isEmpty(),
                    "with bait turned off he still sells it, leaves an aware Human's button live, or no longer sells a"
                            + " Substitute the Denreishinki");
        } finally {
            BleachConfig.BAIT_ENABLED.set(baitOn);
        }
        helper.succeed();
    }

    /**
     * Test 2. A soul pays 16 of its 40 emeralds through his merchant screen for one Hollow Bait, a shift-click buying no
     * more, while a creative Substitute's screen stands open beside it and pays its own 5 for a Denreishinki, still on
     * offer after and leaving its bait unbought. The day of the bait is saved with the soul; reopened, it is sold out and
     * the payment buys nothing; on another day the soul buys again.
     */
    @GameTest(template = "empty")
    public static void aTradeWithUraharaIsPaidFor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item pager = ModItems.DENREISHINKI.get();
        Item bait = ModItems.HOLLOW_BAIT.get();
        long today = KidoLogic.lessonDay(level.getGameTime(), level.getDayTime());
        // Held first, so its end closes the screens before the players leave and nothing drops in the world.
        ShakonmakuTests.Cleanup held = hold(helper);
        KisukeUrahara urahara = helper.spawn(ModEntities.KISUKE_URAHARA.get(), HIM);
        held.atEnd(() -> TravelTests.discard(urahara));
        ServerPlayer buyer = customer(helper, held, GameType.SURVIVAL, awareHuman(), 40);
        ServerPlayer second = customer(helper, held, GameType.CREATIVE, ShinigamiTests.substitute(60.0F), 5);

        MerchantMenu menu = shelves(helper, urahara, buyer);
        helper.assertTrue(menu.getOffers().size() == 1 && !menu.getOffers().get(0).isOutOfStock(),
                "an aware Human was not offered bait in stock");
        buy(buyer, menu);
        // The second soul's screen opens at its own counter while the first's stands.
        MerchantMenu other = shelves(helper, urahara, second);
        helper.assertTrue(menu.stillValid(buyer) && other.stillValid(second), "a second soul's screen ended the first's");
        buy(second, other);
        helper.assertTrue(other.getOffers().size() == 2 && !other.getOffers().get(0).isOutOfStock()
                && !other.getOffers().get(1).isOutOfStock() && !UraharaCounter.baitSoldToday(second, level),
                "a Denreishinki sold out, or was taken for the day's bait");
        buyer.closeContainer();
        second.closeContainer();
        helper.assertTrue(has(buyer, bait, 1, 24) && buyer.getData(SoulAttachments.SHOP_BAIT_DAY) == today,
                "the sale left " + carried(buyer) + ", or the day was not noted");
        helper.assertTrue(has(second, pager, 1, 0), "a creative soul's Denreishinki left it " + carried(second));
        // What a logout saves and a login reads.
        CompoundTag saved = buyer.saveWithoutId(new CompoundTag());
        Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
        loaded.load(saved);
        helper.assertTrue(saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getLong(
                BleachMod.id("shop_bait_day").toString()) == today && UraharaCounter.baitSoldToday(loaded, level),
                "the day's sale was not saved with the soul, or did not load");

        menu = shelves(helper, urahara, buyer);
        helper.assertTrue(menu.getOffers().get(0).isOutOfStock(), "reopened, the bait was in stock again");
        buy(buyer, menu);
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "sold out, the payment still bought bait");
        buyer.closeContainer();
        helper.assertTrue(has(buyer, bait, 1, 24), "sold out, the screen left " + carried(buyer));

        buyer.setData(SoulAttachments.SHOP_BAIT_DAY, today - 1);
        buy(buyer, shelves(helper, urahara, buyer));
        buyer.closeContainer();
        helper.assertTrue(has(buyer, bait, 2, 8), "on another day the soul was left " + carried(buyer));
        helper.succeed();
    }

    /**
     * Test 3. Only his own button opens his wares, and only for a soul he sells to that is near him and in the game; the
     * screen stays while the trade stands, and closes, handing back the payment set out, once the soul walks 9 blocks
     * off, turns spectator or becomes a soul he sells other wares to, or once he is gone.
     */
    @GameTest(template = "empty")
    public static void uraharaTradesWhileTheTradeStands(GameTestHelper helper) {
        ShakonmakuTests.Cleanup held = hold(helper);
        KisukeUrahara urahara = helper.spawn(ModEntities.KISUKE_URAHARA.get(), HIM);
        held.atEnd(() -> TravelTests.discard(urahara));
        ServerPlayer visitor = customer(helper, held, GameType.SURVIVAL, awareHuman(), 16);
        Vec3 near = helper.absoluteVec(NEAR);
        Vec3 far = helper.absoluteVec(FAR);

        visitor.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, 1.0F));
        refused(helper, urahara, visitor, NpcAction.TRADE, "an unaware Human");
        visitor.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 40.0F));
        refused(helper, urahara, visitor, NpcAction.TRADE, "a Hollow");
        visitor.setData(SoulAttachments.SOUL, awareHuman());
        refused(helper, urahara, visitor, NpcAction.KONSO, "Konsō, asked of him");
        visitor.setGameMode(GameType.SPECTATOR);
        refused(helper, urahara, visitor, NpcAction.TRADE, "a spectator");
        visitor.setGameMode(GameType.SURVIVAL);
        visitor.teleportTo(far.x, far.y, far.z);
        refused(helper, urahara, visitor, NpcAction.TRADE, "a soul 9 blocks off");
        visitor.teleportTo(near.x, near.y, near.z);

        helper.startSequence()
                .thenExecute(() -> setOut(helper, urahara, visitor))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(visitor.containerMenu instanceof MerchantMenu, "the screen closed as the trade stood");
                    visitor.teleportTo(far.x, far.y, far.z);
                })
                .thenWaitUntil(() -> ended(helper, visitor, "a soul 9 blocks off"))
                .thenExecute(() -> {
                    visitor.teleportTo(near.x, near.y, near.z);
                    setOut(helper, urahara, visitor);
                    visitor.setGameMode(GameType.SPECTATOR);
                })
                .thenWaitUntil(() -> ended(helper, visitor, "a spectator"))
                .thenExecute(() -> {
                    visitor.setGameMode(GameType.SURVIVAL);
                    setOut(helper, urahara, visitor);
                    // Still a soul whose view trades, but he would sell it a Denreishinki too now.
                    visitor.setData(SoulAttachments.SOUL, ShinigamiTests.substitute(60.0F));
                })
                .thenWaitUntil(() -> ended(helper, visitor, "a Human become a Substitute"))
                .thenExecute(() -> {
                    setOut(helper, urahara, visitor);
                    urahara.discard();
                })
                .thenWaitUntil(() -> ended(helper, visitor, "a keeper who is gone"))
                .thenSucceed();
    }

    /**
     * Test 4. A Tenshintai is sold to a soul whose spirit could be drawn out to be fought for its Bankai: one that knows
     * its zanpakutō's name, a Shinigami, a Substitute or a Visored, its power its own, short of Bankai, with no doll on
     * its record; a named Human is sold none. It is sold one at a time, for {@code [bankai.tenshintai] price} emeralds,
     * 32 unless set: sold out while the soul carries one, so a soul that buys it finds it sold out on the screen it
     * bought on, which stays open, and on the next, until the doll is gone from its inventory, and gone from his wares
     * once a doll is set down. He tells the buyer how it is used, and where best.
     */
    @GameTest(template = "empty")
    public static void uraharaSellsOneTenshintaiAtATime(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        Item pager = ModItems.DENREISHINKI.get();
        Item bait = ModItems.HOLLOW_BAIT.get();
        Item doll = ModItems.TENSHINTAI.get();
        GlobalPos spot = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BlockPos.ZERO));
        long ends = now + Tenshintai.windowTicks();
        SoulData lent = named(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 500.0F));
        lent.setShinigami(lent.shinigami().withPowerLentUntil(now + 1200L));
        SoulData reached = named(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 500.0F));
        BankaiLogic.reach(reached, Component.literal("Tensa Zangetsu"), now);
        SoulData standing = named(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 500.0F));
        standing.setZanpakuto(standing.zanpakuto().withBankai(standing.zanpakuto().bankai().dollAt(spot, ends)));
        SoulData visored = named(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 500.0F));
        VisoredLogic.setRecord(visored, VisoredLogic.record(visored).withRootedAt(0L).withSubduedAt(1L));
        Map<SoulData, List<Item>> expected = new LinkedHashMap<>();
        expected.put(named(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 500.0F)), List.of(pager, bait, doll));
        expected.put(named(helper, ShinigamiTests.substitute(500.0F)), List.of(pager, bait, doll));
        expected.put(visored, List.of(pager, bait, doll));
        expected.put(lent, List.of(bait));
        expected.put(reached, List.of(pager, bait));
        expected.put(standing, List.of(pager, bait));
        expected.put(ShinigamiTests.soul(SoulState.SHINIGAMI, 500.0F), List.of(pager, bait));
        // Named, but with no Shinigami's power to draw a spirit out with.
        expected.put(named(helper, awareHuman()), List.of(bait));
        expected.forEach((soul, wares) -> {
            List<Item> sold = UraharaCounter.waresFor(soul, now);
            helper.assertTrue(sold.equals(wares), "Urahara sells " + sold + " to a soul in "
                    + KisukeUrahara.viewFor(soul) + ", not " + wares);
        });
        MerchantOffers fresh = UraharaCounter.offersFor(List.of(pager, bait, doll), false, false);
        MerchantOffers inHand = UraharaCounter.offersFor(List.of(pager, bait, doll), false, true);
        helper.assertTrue(fresh.size() == 3 && sells(fresh.get(2), doll, 32, 1, 0) && !fresh.get(2).isOutOfStock()
                        && sells(inHand.get(1), bait, 16, 1, 0) && sells(inHand.get(2), doll, 32, 1, 1)
                        && inHand.get(2).isOutOfStock(),
                "his Tenshintai is offered as " + fresh.get(2).getCostA() + " for " + fresh.get(2).getResult()
                        + ", or is in stock while the soul carries one");
        KidoTests.withConfig(BleachConfig.BANKAI_TENSHINTAI_PRICE, 5, () -> helper.assertTrue(
                sells(UraharaCounter.offersFor(List.of(doll), false, false).get(0), doll, 5, 1, 0),
                "with its price set to 5, his Tenshintai is offered for " + UraharaCounter.dollPrice()));

        // Held first, so its end closes the screens before the players leave and nothing drops in the world.
        ShakonmakuTests.Cleanup held = hold(helper);
        KisukeUrahara urahara = helper.spawn(ModEntities.KISUKE_URAHARA.get(), HIM);
        held.atEnd(() -> TravelTests.discard(urahara));
        ServerPlayer buyer = customer(helper, held, GameType.SURVIVAL,
                named(helper, ShinigamiTests.soul(SoulState.SHINIGAMI, 500.0F)), 64);
        MerchantMenu menu = shelves(helper, urahara, buyer);
        buy(buyer, menu, 2);
        String word = "message.bleach.urahara.tenshintai";
        helper.assertTrue(menu.stillValid(buyer) && menu.getOffers().get(2).isOutOfStock()
                        && TestPlayers.chat(buyer).stream()
                                .anyMatch(line -> TenshintaiTests.says(line, word, Tenshintai.windowDays()))
                        && Language.getInstance().has(word),
                "a Tenshintai bought closed the screen, stayed in stock on it, or came with no word from him");
        buyer.closeContainer();
        helper.assertTrue(has(buyer, doll, 1, 32), "the Tenshintai's sale left " + carried(buyer));
        menu = shelves(helper, urahara, buyer);
        helper.assertTrue(menu.getOffers().get(2).isOutOfStock(), "reopened, a second Tenshintai was in stock");
        buyer.closeContainer();
        // Its inventory emptied, the doll with it, as a doll set down or put away leaves it.
        buyer.getInventory().clearContent();
        menu = shelves(helper, urahara, buyer);
        helper.assertTrue(!menu.getOffers().get(2).isOutOfStock(), "with its doll gone, none was in stock");
        buyer.closeContainer();
        // A doll set down on its record, then cleared again before the soul's own tick could stand one.
        SoulData bought = buyer.getData(SoulAttachments.SOUL);
        bought.setZanpakuto(bought.zanpakuto().withBankai(bought.zanpakuto().bankai().dollAt(spot, ends)));
        try {
            menu = shelves(helper, urahara, buyer);
            helper.assertTrue(menu.getOffers().size() == 2, "with its doll set down, he still sold the soul another");
            buyer.closeContainer();
        } finally {
            bought.setZanpakuto(bought.zanpakuto().withBankai(bought.zanpakuto().bankai().closed()));
        }
        helper.succeed();
    }

    // ---- Helpers ----

    /** The test's own chunk and the ring about it held entity-ticking, so the joined souls tick; and its end. */
    private static ShakonmakuTests.Cleanup hold(GameTestHelper helper) {
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        return ShakonmakuTests.hold(helper, helper.getLevel(), chunk.x, chunk.z);
    }

    /** A living Human just aware of spirits. */
    private static SoulData awareHuman() {
        return ShinigamiTests.soul(SoulState.HUMAN, (float) (double) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD));
    }

    /** This soul joined 2 blocks from him in this game mode with these emeralds; its screen shut at the end. */
    private static ServerPlayer customer(GameTestHelper helper, ShakonmakuTests.Cleanup held, GameType mode, SoulData soul,
                                         int emeralds) {
        ServerPlayer customer = TestPlayers.join(helper, mode, NEAR);
        held.atEnd(() -> {
            if (customer.containerMenu != customer.inventoryMenu) {
                customer.closeContainer();
            }
        });
        customer.setData(SoulAttachments.SOUL, soul);
        customer.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
        return customer;
    }

    /** Presses his trade button as the dialogue's payload does, and returns the merchant screen it opened. */
    private static MerchantMenu shelves(GameTestHelper helper, KisukeUrahara urahara, ServerPlayer customer) {
        urahara.perform(customer, NpcAction.TRADE);
        helper.assertTrue(customer.containerMenu instanceof MerchantMenu, "his trade button opened no wares");
        return (MerchantMenu) customer.containerMenu;
    }

    /** As the screen does: picks the first offer, which sets out its payment from the inventory, and shift-takes it. */
    private static void buy(ServerPlayer customer, MerchantMenu menu) {
        buy(customer, menu, 0);
    }

    /** As the screen does: picks this offer, which sets out its payment from the inventory, and shift-takes it. */
    private static void buy(ServerPlayer customer, MerchantMenu menu, int offer) {
        customer.connection.handleSelectTrade(new ServerboundSelectTradePacket(offer));
        menu.clicked(2, 0, ClickType.QUICK_MOVE, customer);
    }

    /** This soul once it has learned Zangetsu's name, as a mock player's, for the fresh Asauchi in its hand. */
    private static SoulData named(GameTestHelper helper, SoulData soul) {
        Player player = ZanpakutoTests.mock(helper, soul);
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        ZanpakutoEvents.learnName(player, soul, ZanpakutoSpirits.ZANGETSU.location(),
                ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), player.getMainHandItem());
        return soul;
    }

    /** Opens his wares with the payment set out for the first, ready to be taken, as the screen shows it. */
    private static void setOut(GameTestHelper helper, KisukeUrahara urahara, ServerPlayer customer) {
        MerchantMenu menu = shelves(helper, urahara, customer);
        customer.connection.handleSelectTrade(new ServerboundSelectTradePacket(0));
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "the payment set out shows no ware");
    }

    /** Asks him for this and asserts no screen opened. */
    private static void refused(GameTestHelper helper, KisukeUrahara urahara, ServerPlayer visitor, NpcAction action,
                                String who) {
        urahara.perform(visitor, action);
        helper.assertTrue(visitor.containerMenu == visitor.inventoryMenu, "Urahara showed his wares to " + who);
    }

    /** Asserts the trade has ended: no screen, the payment back in the inventory, nothing bought. */
    private static void ended(GameTestHelper helper, ServerPlayer visitor, String why) {
        helper.assertTrue(visitor.containerMenu == visitor.inventoryMenu && has(visitor, ModItems.HOLLOW_BAIT.get(), 0, 16)
                && visitor.getInventory().countItem(ModItems.DENREISHINKI.get()) == 0,
                "the trade did not end for " + why + ", or kept " + carried(visitor));
    }

    /** Whether the soul carries this much of the ware and this many emeralds. */
    private static boolean has(ServerPlayer customer, Item ware, int wares, int emeralds) {
        return customer.getInventory().countItem(ware) == wares
                && customer.getInventory().countItem(Items.EMERALD) == emeralds;
    }

    private static String carried(ServerPlayer customer) {
        return customer.getInventory().countItem(Items.EMERALD) + " emeralds, "
                + customer.getInventory().countItem(ModItems.HOLLOW_BAIT.get()) + " bait and "
                + customer.getInventory().countItem(ModItems.DENREISHINKI.get()) + " Denreishinki";
    }

    /** Whether this offer sells one of this ware for emeralds alone, at a price nothing moves, with no xp. */
    private static boolean sells(MerchantOffer offer, Item ware, int emeralds, int maxUses, int uses) {
        return offer.getResult().is(ware) && offer.getResult().getCount() == 1 && offer.getCostA().is(Items.EMERALD)
                && offer.getCostA().getCount() == emeralds && offer.getCostB().isEmpty() && offer.getMaxUses() == maxUses
                && offer.getUses() == uses && offer.getXp() == 0 && offer.getPriceMultiplier() == 0.0F
                && offer.getDemand() == 0 && offer.getSpecialPriceDiff() == 0;
    }
}
