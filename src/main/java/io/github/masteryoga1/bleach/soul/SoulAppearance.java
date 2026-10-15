package io.github.masteryoga1.bleach.soul;

import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;

/**
 * The part of another player's soul that a client needs in order to draw them: whether they are a
 * spirit, how much Chain of Fate is left, and whether their reiatsu is released. Players only get
 * this about others, never the full {@link SoulData}.
 */
public record SoulAppearance(SoulState state, float chainFraction, boolean reiatsuReleased) {
    public static final SoulAppearance NONE = new SoulAppearance(SoulState.HUMAN, 0.0F, false);

    /** Chain shown in steps of a tenth, so trackers only hear about it ten times over its life. */
    public static final int CHAIN_STEPS = 10;

    private static final IntFunction<SoulState> STATE_BY_ID =
            ByIdMap.continuous(SoulState::ordinal, SoulState.values(), ByIdMap.OutOfBoundsStrategy.ZERO);

    public static final StreamCodec<ByteBuf, SoulAppearance> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(STATE_BY_ID, SoulState::ordinal), SoulAppearance::state,
            ByteBufCodecs.FLOAT, SoulAppearance::chainFraction,
            ByteBufCodecs.BOOL, SoulAppearance::reiatsuReleased,
            SoulAppearance::new);

    public static SoulAppearance of(SoulData data) {
        float chain = data.chainMaxTicks() <= 0 ? 0.0F
                : Mth.ceil(data.chainFraction() * CHAIN_STEPS) / (float) CHAIN_STEPS;
        return new SoulAppearance(data.state(), chain, data.isReiatsuReleased());
    }
}
