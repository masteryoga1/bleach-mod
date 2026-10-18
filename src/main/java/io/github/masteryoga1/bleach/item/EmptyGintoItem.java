package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * An empty Gintō: a silver tube a Quincy of the second tier fills with reishi by holding use, drawn
 * like a bow, for a moment and some reishi. The filled tube ({@link GintoItem}) keeps its reishi
 * whatever later becomes of its maker's power.
 */
public class EmptyGintoItem extends Item {
    public EmptyGintoItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Starts filling, unless the soul cannot fill a tube now. Both sides run the same check on the
     * synced soul, so the client only starts when the server will too; the server says why not.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String refusal = QuincyArtsLogic.fillRefusal(SoulLogic.get(player));
        if (refusal != null) {
            if (!level.isClientSide()) {
                refuse(player, refusal);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return QuincyArtsLogic.FILL_TICKS;
    }

    /**
     * Held long enough, on the server: checks again and pays the reishi, then one tube of the stack
     * becomes a filled Gintō. A soul that can no longer fill it is told why and keeps the tube empty.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return stack;
        }
        SoulData data = SoulLogic.get(player);
        String refusal = QuincyArtsLogic.fillRefusal(data);
        if (refusal == null && !QuincyLogic.trySpendReishi(data, fillCost())) {
            refusal = "message.bleach.ginto.fill_reishi";
        }
        if (refusal != null) {
            refuse(player, refusal);
            return stack;
        }
        SpiritSenses.play(player, ModSounds.GINTO_FILL.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        return ItemUtils.createFilledResult(stack, player, new ItemStack(ModItems.GINTO.get()));
    }

    private static float fillCost() {
        return (float) BleachConfig.get(BleachConfig.GINTO_FILL_COST);
    }

    /** Why not, on the action bar; a refusal for want of reishi says how much it takes. */
    private static void refuse(Player player, String refusal) {
        player.displayClientMessage(Component.translatable(refusal, Mth.ceil(fillCost())).withStyle(ChatFormatting.GRAY),
                true);
    }
}
