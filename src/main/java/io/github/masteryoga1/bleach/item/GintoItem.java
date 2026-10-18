package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.soul.GintoSpells;
import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A filled Gintō: a silver tube of reishi a Quincy pours out to cast a spell. Using it casts the
 * soul's chosen spell, spending as many tubes as the spell takes; sneak-using it chooses the next
 * spell the soul knows. Whether a cast works is {@link GintoSpells}' to decide, on the server.
 */
public class GintoItem extends Item {
    public GintoItem(Item.Properties properties) {
        super(properties);
    }

    /** The hand always swings; the server casts, or chooses the next spell when sneaking, and says why not. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            SoulData data = SoulLogic.get(player);
            if (player.isShiftKeyDown()) {
                QuincyArtsEvents.cycleSpell(player, data);
            } else {
                GintoSpells.cast(player, data, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
