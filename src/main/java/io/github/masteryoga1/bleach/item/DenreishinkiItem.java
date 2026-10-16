package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Denreishinki, the soul pager every Shinigami carries. Wherever it is in its holder's
 * inventory, it beeps when a Garganta tears open nearby or a Plus turns Hollow, with which way and
 * roughly how far (see {@code GargantaEvents.pingPagers}). Used, it points to the nearest Hollow.
 * It answers only to a Shinigami's power or an Academy student; to anyone else it is static.
 */
public final class DenreishinkiItem extends Item {
    public static final int USE_COOLDOWN_TICKS = 20;
    /** The beep of a pager that will not answer, an octave below its usual one. */
    private static final float STATIC_PITCH = 0.5F;
    /** Distances are rounded to the nearest five blocks, a pager's rough sense of them. */
    private static final long DISTANCE_STEP = 5L;

    public DenreishinkiItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (!readable(player)) {
                player.playNotifySound(ModSounds.DENREISHINKI_PING.get(), SoundSource.PLAYERS, 1.0F, STATIC_PITCH);
            }
            player.displayClientMessage(reading(player), true);
        }
        player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bleach.denreishinki").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Whether this soul can read a Denreishinki: one holding a Shinigami's power it has not lent away,
     * or an Academy student.
     */
    public static boolean canRead(SoulData data, long now) {
        return (ShinigamiLogic.hasShinigamiPower(data) && !ShinigamiLogic.isPowerLent(data, now))
                || ShinigamiLogic.isStudent(data);
    }

    /** Whether the player has a Denreishinki anywhere in their inventory, hands and armour slots included. */
    public static boolean carries(Player player) {
        return player.getInventory().contains(stack -> stack.is(ModItems.DENREISHINKI.get()));
    }

    /**
     * What the player's Denreishinki shows them: which way and roughly how far the nearest Hollow
     * within range is, that none is in range, or only static if it will not answer them. Server side.
     */
    public static Component reading(Player player) {
        if (!readable(player)) {
            return Component.translatable("message.bleach.denreishinki.static");
        }
        int range = BleachConfig.get(BleachConfig.DENREISHINKI_RANGE);
        double rangeSqr = (double) range * range;
        List<Hollow> hollows = player.level().getEntitiesOfClass(Hollow.class, player.getBoundingBox().inflate(range),
                hollow -> hollow.isAlive() && !hollow.isDeparting() && hollow.distanceToSqr(player) <= rangeSqr);
        Hollow nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Hollow hollow : hollows) {
            double distanceSqr = hollow.distanceToSqr(player);
            if (distanceSqr < nearestSqr) {
                nearest = hollow;
                nearestSqr = distanceSqr;
            }
        }
        if (nearest == null) {
            return Component.translatable("message.bleach.denreishinki.quiet", range);
        }
        // As with the beeps, the way and the distance are measured along the ground.
        double dx = nearest.getX() - player.getX();
        double dz = nearest.getZ() - player.getZ();
        return Component.translatable("message.bleach.denreishinki.hollow",
                Component.translatable(ShinigamiLogic.compassKey(dx, dz)),
                ShinigamiLogic.roundedDistance(Math.sqrt(dx * dx + dz * dz), DISTANCE_STEP));
    }

    /**
     * Hands the player a Denreishinki unless they already carry one, with a word about what it is.
     * It goes in their inventory, or at their feet if that is full.
     */
    public static void giveIfMissing(Player player) {
        if (carries(player)) {
            return;
        }
        ItemStack stack = new ItemStack(ModItems.DENREISHINKI.get());
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.translatable("message.bleach.denreishinki.received")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static boolean readable(Player player) {
        return SoulLogic.hasSoul(player) && canRead(SoulLogic.get(player), player.level().getGameTime());
    }
}
