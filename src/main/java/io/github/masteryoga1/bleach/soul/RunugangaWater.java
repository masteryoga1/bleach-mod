package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.Runuganga;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Water that reaches Runuganga, and soaks him ({@link Runuganga#soak}). In Hueco Mundo, where water poured from a bucket
 * boils away and no rain falls, that is water carried in from another world: a splash or lingering bottle of water
 * bursting by him ({@link #splashed}), or a bucket or bottle of water used on him ({@link #onInteract}). Standing in
 * water or rain soaks him too, as he finds for himself, and damage in {@code #bleach:soaks} as it lands.
 */
public final class RunugangaWater {
    private RunugangaWater() {
    }

    /**
     * A bottle of water bursts (from ThrownPotionMixin, as its splash begins): each body of his that one of his boxes puts
     * in the splash is soaked, unless an unaware soul threw it. His boxes, not his feet, as vanilla's splash measures,
     * since he stands 12 high.
     */
    public static void splashed(ThrownPotion potion) {
        AABB splash = potion.getBoundingBox().inflate(ThrownPotion.SPLASH_RANGE, 2.0, ThrownPotion.SPLASH_RANGE);
        for (LivingEntity body : SpiritProjectiles.creaturesIn(potion.level(), splash, e -> e instanceof Runuganga)) {
            if (!SpiritEvents.potionPassesThrough(potion.getOwner(), body)) {
                ((Runuganga) body).soak();
            }
        }
    }

    /**
     * A bucket or bottle of water used on him while he can be aimed at (standing, or lying soaked as sand) and soaked
     * ({@link Runuganga#canSoak}), by a soul who can touch him: he is soaked, and the soul keeps the empty bucket or
     * bottle. Answered on both sides, so the client never pours it on the sand behind him; an unaware soul's goes through
     * him as its hand would.
     */
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        boolean bucket = held.is(Items.WATER_BUCKET);
        boolean bottle = held.is(Items.POTION)
                && held.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
        if (!bucket && !bottle || !(SpiritProjectiles.bodyOf(event.getTarget()) instanceof Runuganga giant)
                || !giant.isPickable() || !giant.canSoak() || SpiritEvents.passesThrough(player, giant)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!player.level().isClientSide) {
            giant.soak();
            ItemStack empty = new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE);
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(held, player, empty));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    bucket ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
