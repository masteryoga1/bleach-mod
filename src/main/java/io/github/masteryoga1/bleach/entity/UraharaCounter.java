package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.DenreishinkiItem;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * Kisuke Urahara's counter for one soul, the trader behind the vanilla merchant screen: his wares for that soul at fixed
 * prices in emeralds. Each soul that asks gets its own, so many may trade at once; it is never added to the world, and
 * it is an unspawned marker because the screen takes its trader for an entity.
 */
public final class UraharaCounter extends Marker implements Merchant {
    /** The merchant screen's title, the shop's name. */
    private static final String TITLE = "container.bleach.urahara_shop";
    /** Emeralds for a Denreishinki: a librarian's compass costs 4, and the pager takes a little more iron and a shard. */
    public static final int DENREISHINKI_PRICE = 5;
    /** Emeralds for Hollow Bait, as rare as a mask fragment and a filled Gintō make it. */
    public static final int BAIT_PRICE = 16;
    /** How many Hollow Bait he sells a soul in a day. */
    public static final int BAIT_A_DAY = 1;
    /** The Denreishinki's stock: it never runs out. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private final KisukeUrahara keeper;
    private final ServerPlayer customer;
    /** What he would sell the soul as the screen opened, and the one list of offers the screen sells from. */
    private final List<Item> wares;
    private final MerchantOffers offers;

    private UraharaCounter(KisukeUrahara keeper, ServerPlayer customer, List<Item> wares) {
        super(EntityType.MARKER, keeper.level());
        setPos(keeper.getX(), keeper.getY(), keeper.getZ());
        this.keeper = keeper;
        this.customer = customer;
        this.wares = wares;
        this.offers = offersFor(wares, baitSoldToday(customer, keeper.level()));
    }

    /** Opens the merchant screen on his wares for this soul, if he sells it any. Server side, once the press is checked. */
    static void open(KisukeUrahara keeper, ServerPlayer customer) {
        List<Item> wares = waresFor(SoulLogic.get(customer), keeper.level().getGameTime());
        if (!wares.isEmpty()) {
            new UraharaCounter(keeper, customer, wares).openTradingScreen(customer, Component.translatable(TITLE), 0);
        }
    }

    /**
     * What he sells this soul, cheaper first: a Denreishinki to a soul it answers to, and, while the server allows bait,
     * Hollow Bait to one that could crush it somewhere, some day (aware, and no Hollow or Arrancar: its first refusals).
     */
    public static List<Item> waresFor(SoulData soul, long now) {
        List<Item> wares = new ArrayList<>(2);
        if (DenreishinkiItem.canRead(soul, now)) {
            wares.add(ModItems.DENREISHINKI.get());
        }
        if (BleachConfig.get(BleachConfig.BAIT_ENABLED) && SoulLogic.isAware(soul) && soul.state() != SoulState.HOLLOW
                && soul.state() != SoulState.ARRANCAR) {
            wares.add(ModItems.HOLLOW_BAIT.get());
        }
        return wares;
    }

    /**
     * His offers for these wares, one of each for emeralds alone, at prices no demand or reputation moves and with no xp:
     * the Denreishinki without end, the bait one a day, already sold out when the soul bought it today.
     */
    public static MerchantOffers offersFor(List<Item> wares, boolean baitSoldToday) {
        MerchantOffers offers = new MerchantOffers();
        for (Item ware : wares) {
            boolean bait = ware == ModItems.HOLLOW_BAIT.get();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, bait ? BAIT_PRICE : DENREISHINKI_PRICE),
                    Optional.empty(), new ItemStack(ware), bait && baitSoldToday ? BAIT_A_DAY : 0,
                    bait ? BAIT_A_DAY : UNLIMITED, 0, 0.0F));
        }
        return offers;
    }

    /** Whether this soul bought its Hollow Bait on this world's day, the mod's own ({@link KidoLogic#lessonDay}). */
    public static boolean baitSoldToday(Player customer, Level level) {
        return customer.getData(SoulAttachments.SHOP_BAIT_DAY) == day(level);
    }

    private static long day(Level level) {
        return KidoLogic.lessonDay(level.getGameTime(), level.getDayTime());
    }

    /**
     * The soul at the counter while the trade stands: he serves it still and would sell it the same wares. Null once
     * not, and the screen then closes on the soul's next tick as a villager's does.
     */
    @Nullable
    @Override
    public Player getTradingPlayer() {
        return keeper.serves(customer)
                && waresFor(SoulLogic.get(customer), keeper.level().getGameTime()).equals(wares) ? customer : null;
    }

    /** Ignored: the soul at the counter is set once, as it is made, and nothing asks the counter once its screen shuts. */
    @Override
    public void setTradingPlayer(@Nullable Player player) {
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
    }

    /** A sale: its offer's stock goes down, and bait sold is noted on the soul, so it is sold out until another day. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        if (offer.getResult().is(ModItems.HOLLOW_BAIT.get())) {
            customer.setData(SoulAttachments.SHOP_BAIT_DAY, day(keeper.level()));
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    /** Never heard: the screen plays it on the server only, where a local sound does nothing. */
    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }
}
