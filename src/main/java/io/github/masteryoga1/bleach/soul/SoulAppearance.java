package io.github.masteryoga1.bleach.soul;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The part of another player's soul that a client needs in order to draw them: whether they are a
 * spirit, how much Chain of Fate is left, whether their reiatsu is released, and whether a zanpakutō
 * hangs sheathed at their hip (other clients never see a player's hotbar). Players only get this
 * about others, never the full {@link SoulData}.
 */
public record SoulAppearance(SoulState state, float chainFraction, boolean reiatsuReleased, boolean bladeSheathed) {
    public static final SoulAppearance NONE = new SoulAppearance(SoulState.HUMAN, 0.0F, false, false);

    /** Chain shown in steps of a tenth, so trackers only hear about it ten times over its life. */
    public static final int CHAIN_STEPS = 10;

    private static final IntFunction<SoulState> STATE_BY_ID =
            ByIdMap.continuous(SoulState::ordinal, SoulState.values(), ByIdMap.OutOfBoundsStrategy.ZERO);

    public static final StreamCodec<ByteBuf, SoulAppearance> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(STATE_BY_ID, SoulState::ordinal), SoulAppearance::state,
            ByteBufCodecs.FLOAT, SoulAppearance::chainFraction,
            ByteBufCodecs.BOOL, SoulAppearance::reiatsuReleased,
            ByteBufCodecs.BOOL, SoulAppearance::bladeSheathed,
            SoulAppearance::new);

    /** How this soul looks, leaving out what only its body shows (the sheathed blade). */
    public static SoulAppearance of(SoulData data) {
        float chain = data.chainMaxTicks() <= 0 ? 0.0F
                : Mth.ceil(data.chainFraction() * CHAIN_STEPS) / (float) CHAIN_STEPS;
        return new SoulAppearance(data.state(), chain, data.isReiatsuReleased(), false);
    }

    /** How this creature looks, with its soul. */
    public static SoulAppearance of(LivingEntity entity, SoulData data) {
        SoulAppearance soul = of(data);
        return new SoulAppearance(soul.state(), soul.chainFraction(), soul.reiatsuReleased(),
                ShinigamiLogic.hasSheathedBlade(entity));
    }
}
