package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Hollow's hide bears no armour. Asked where an item goes, a Hollow player under survival's rules
 * answers "in the hand" for any armour it refuses (see {@link HollowPlayerLogic#refusesArmour}), so
 * every path that asks the entity agrees: the armour slots, shift-clicks, right-click wearing and
 * dispensers. Runs on both sides, so the inventory screen agrees with the server.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    // At RETURN, so NeoForge's early return of the stack's own slot is caught too. Player does not override it.
    @Inject(method = "getEquipmentSlotForItem", at = @At("RETURN"), cancellable = true)
    private void bleach$hollowsWearNoArmour(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
        if (HollowPlayerLogic.refusesArmour((LivingEntity) (Object) this, stack, cir.getReturnValue())) {
            cir.setReturnValue(EquipmentSlot.MAINHAND);
        }
    }
}
