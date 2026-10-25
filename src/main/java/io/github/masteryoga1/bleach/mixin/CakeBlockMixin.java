package io.github.masteryoga1.bleach.mixin;

import io.github.masteryoga1.bleach.soul.HollowPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Hollow cannot bite a cake (see {@link HollowPlayerEvents#refusesCake}). Only the bite is refused, as if
 * the Hollow were full, so the rest of the click goes on as for anyone: a candle put on the cake, candles
 * lit or put out, a block placed against it, or the item in hand used. A candle cake bites through here
 * too. Runs on both sides, so the client never predicts the bite.
 */
@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
    @Inject(method = "eat", at = @At("HEAD"), cancellable = true)
    private static void bleach$noCakeForHollows(LevelAccessor level, BlockPos pos, BlockState state, Player player,
                                                CallbackInfoReturnable<InteractionResult> cir) {
        if (HollowPlayerEvents.refusesCake(player)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
