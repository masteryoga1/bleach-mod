package io.github.masteryoga1.bleach.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Game tests only: the test server makes its worlds from the flat preset alone, so datapack worlds never load. Handing
 * the bake the datapack's worlds too adds the mod's worlds and leaves the flat Overworld, Nether and End as they were.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
    // Matches every lambda of the class, whatever the compiler named it; only one holds the bake call, so exactly one
    // injection is made and require 1 holds.
    @WrapOperation(method = "/^lambda\\$/", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)"
                    + "Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
    private static WorldDimensions.Complete bleach$withDatapackWorlds(WorldDimensions preset, Registry<LevelStem> none,
            Operation<WorldDimensions.Complete> original, @Local(argsOnly = true) WorldLoader.DataLoadContext context) {
        return original.call(preset, context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
    }
}
