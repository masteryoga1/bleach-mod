package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Mask fragments: a blow that cracks a Hollow's whole mask can knock a piece off it, which goes
 * straight to the soul that struck it, or falls at their feet if they have no room. Each Hollow is
 * tried once in its life, on its first fresh crack; Fishbone D always gives one. The fragment's only
 * use is Hollow Bait. Called from {@link HollowEvents}; takes a {@link Player}, so game tests can use
 * mock players.
 */
public final class MaskFragments {
    private MaskFragments() {
    }

    /**
     * Whether this crack breaks a fragment off: the mask was whole ({@code fresh}), the Hollow has not
     * been tried before and is not dying away or leaving, and {@code roll} (from 0 to 1) falls under
     * {@code chance}, or it is Fishbone D.
     */
    public static boolean yields(boolean fresh, boolean rolledBefore, boolean usable, boolean fishbone, double roll,
                                 double chance) {
        return fresh && !rolledBefore && usable && (fishbone || roll < chance);
    }

    /**
     * A player's blow freshly cracked this Hollow's mask: the Hollow's one try at a fragment. One that
     * breaks off goes into the player's inventory, and they are told.
     *
     * @return whether a fragment was given
     */
    public static boolean onFreshCrack(Player player, Hollow hollow, long now) {
        if (!player.isAlive()) {
            return false;
        }
        boolean rolledBefore = hollow.markFragmentRolled();
        boolean usable = hollow.isAlive() && !hollow.isPurifying() && !hollow.isDeparting();
        if (!yields(true, rolledBefore, usable, hollow instanceof FishboneD, hollow.getRandom().nextDouble(),
                BleachConfig.get(BleachConfig.MASK_FRAGMENT_CHANCE))) {
            return false;
        }
        // Drops at the player's feet when there is no room.
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.MASK_FRAGMENT.get()));
        player.displayClientMessage(Component.translatable("message.bleach.mask_fragment")
                .withStyle(ChatFormatting.GRAY), true);
        return true;
    }
}
