package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.BaitEvents;
import io.github.masteryoga1.bleach.soul.BaitLogic;
import io.github.masteryoga1.bleach.soul.BaitRecord;
import io.github.masteryoga1.bleach.soul.BaitSite;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Hollow Bait: a wafer of mask fragment, a Gintō's reishi and bone meal. Crushed in the hand, it opens
 * a bait site where the soul stands, which draws Hollows to it in waves until the sky cracks (see
 * {@link BaitEvents}). Whether it may be crushed here and now is {@link BaitLogic}'s to decide, on
 * the server; a refusal keeps the bait and says why.
 */
public final class HollowBaitItem extends Item {
    /** Ticks after a crush before another bait in the hand can be crushed. */
    public static final int COOLDOWN_TICKS = 100;

    private static final float CRUSH_PITCH = 0.6F;
    private static final int CRUSH_PARTICLES = 20;

    public HollowBaitItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * The hand always swings. On the server the bait is crushed and the site opened, unless it is
     * refused: then the soul is told why on the action bar and keeps the bait. A creative player keeps
     * it either way.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer crusher) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }
        SoulData data = SoulLogic.get(crusher);
        BaitLogic.Refusal refusal = BaitEvents.crushRefusal(crusher, data, false);
        if (refusal != null) {
            crusher.displayClientMessage(Component.translatable(refusal.key()).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        long now = level.getGameTime();
        BaitSite site = BaitEvents.start(crusher, crusher.blockPosition(), false, null, now);
        if (site == null) {
            crusher.displayClientMessage(Component.translatable(BaitLogic.Refusal.WRONG_WORLD.key())
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.consume(1, crusher);
        crusher.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        // Anyone can hear the wafer break; only those who see spirits see its reiatsu rise.
        level.playSound(null, crusher.getX(), crusher.getY(), crusher.getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.0F, CRUSH_PITCH);
        SpiritSenses.particles(serverLevel, ParticleTypes.SOUL, crusher.getX(), crusher.getY(0.5), crusher.getZ(),
                CRUSH_PARTICLES, 0.4, 0.5, 0.4, 0.03);
        crusher.sendSystemMessage(Component.translatable("message.bleach.bait.crushed")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        countCrush(crusher, data);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Counts a crush in the soul's record and grants its milestone; the command's sites count too. */
    public static void countCrush(ServerPlayer player, SoulData data) {
        HuntRecord hunt = data.hunt();
        BaitRecord bait = hunt.bait();
        data.setHunt(hunt.withBait(bait.withCrushed(bait.crushed() + 1)));
        Milestones.grant(player, Milestones.HOLLOW_BAIT);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bleach.hollow_bait", BleachConfig.get(BleachConfig.BAIT_DURATION_MINUTES))
                .withStyle(ChatFormatting.GRAY));
    }
}
