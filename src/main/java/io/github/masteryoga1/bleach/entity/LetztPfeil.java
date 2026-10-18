package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Letzt Pfeil: the one great arrow of Quincy: Letzt Stil, a Heilig Pfeil of its own damage type
 * that carves through the soft blocks in its path (as a Getsuga Tenshō does, and only where its owner
 * could break them) until it has carved its fill, then bursts on the next block it meets as any
 * Heilig Pfeil does.
 */
public class LetztPfeil extends HeiligPfeil {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("letzt_pfeil"));

    /** How many more blocks it may carve. Server only. */
    private int carveLeft;

    public LetztPfeil(EntityType<? extends LetztPfeil> type, Level level) {
        super(type, level);
    }

    /**
     * Looses a Letzt Pfeil from {@code from} along {@code direction} at {@code speed} blocks a tick,
     * dead straight, at full size, with this damage and pierce, and adds it to the level.
     */
    public static LetztPfeil launch(Level level, Player owner, Vec3 from, Vec3 direction, float damage, int pierce,
                                    float speed) {
        LetztPfeil arrow = aim(new LetztPfeil(ModEntities.LETZT_PFEIL.get(), level), owner, from, direction, 1.0F,
                damage, pierce, speed, 0.0F);
        arrow.carveLeft = BleachConfig.get(BleachConfig.LETZT_PFEIL_CARVE_BLOCKS);
        level.addFreshEntity(arrow);
        return arrow;
    }

    @Override
    protected ResourceKey<DamageType> damageType() {
        return DAMAGE_TYPE;
    }

    /**
     * On the server, it first carves the way this tick's flight will take, if its owner is still in
     * this level (its flight ends at once otherwise); then it flies as a Heilig Pfeil.
     */
    @Override
    public void tick() {
        if (!level().isClientSide() && !isSpent()) {
            Entity owner = getOwner();
            if (owner != null && owner.level() == level()) {
                carve(owner);
            }
        }
        super.tick();
    }

    /**
     * Breaks, one after another, the blocks this tick's path meets first, while it has carving left,
     * block breaking is on, and each is soft enough and its owner's to break. The first one it may not
     * break is left for its flight to burst on. The path ends at a creature that will stop the arrow
     * (see {@link #creatureStop}), so nothing behind that creature is carved.
     */
    private void carve(Entity owner) {
        if (!BleachConfig.get(BleachConfig.LETZT_PFEIL_BREAKS_BLOCKS) || carveLeft <= 0) {
            return;
        }
        double maxHardness = BleachConfig.get(BleachConfig.LETZT_PFEIL_MAX_HARDNESS);
        Vec3 from = position();
        Vec3 to = creatureStop(from, from.add(getDeltaMovement()));
        // Every block a path this long can pass through, even on a slant, at whatever speed it was set to.
        int mostBlocks = Mth.ceil(to.subtract(from).length() * Math.sqrt(3.0)) + 3;
        for (int i = 0; i < mostBlocks && carveLeft > 0; i++) {
            BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = level().getBlockState(pos);
            if (!GetsugaTensho.cuttable(state, level(), pos, maxHardness)
                    || !GetsugaTensho.permitted(level(), pos, state, this, owner, true)
                    || !level().destroyBlock(pos, true, this)) {
                return;
            }
            carveLeft--;
        }
    }
}
