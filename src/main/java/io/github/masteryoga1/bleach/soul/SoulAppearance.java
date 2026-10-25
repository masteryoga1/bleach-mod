package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The part of another player's soul that a client needs in order to draw them: whether they are a
 * spirit, how much Chain of Fate is left, whether their reiatsu is released, which zanpakutō, if
 * any, hangs sheathed at their hip or across their back (other clients never see a player's hotbar),
 * the Blut a Quincy is calling on, how a Quincy's arts show on them, and how a Hollow's mask looks.
 * Players get this about others, never the full {@link SoulData}, and about themselves only for what
 * their own soul does not tell them (the Hollow's jaw and cracked mask, which only the server knows).
 *
 * @param sheathModel the {@code custom_model_data} of the sheathed blade (0 for a plain Asauchi), or -1 for none
 * @param blut        the Blut the soul is calling on and can use, so Blut Vene's veins can be drawn
 * @param quincyLook  the Quincy arts showing on the soul, as the {@code LOOK_} bits of {@link QuincyArtsLogic}:
 *                    the Sanrei Glove, Letzt Stil, Ginrei Kojaku, Vollständig and its halo, the seal
 * @param hollowLook  the Hollow's look, as the {@code LOOK_} bits of {@link HollowPlayerLogic}: devouring (the jaw
 *                    hangs open), the mask cracked, ravenous (red eyes)
 */
public record SoulAppearance(SoulState state, float chainFraction, boolean reiatsuReleased, int sheathModel,
                             BlutMode blut, int quincyLook, int hollowLook) {
    public static final SoulAppearance NONE = new SoulAppearance(SoulState.HUMAN, 0.0F, false, -1, BlutMode.OFF, 0, 0);

    /** Chain shown in steps of a tenth, so trackers only hear about it ten times over its life. */
    public static final int CHAIN_STEPS = 10;

    private static final IntFunction<SoulState> STATE_BY_ID =
            ByIdMap.continuous(SoulState::ordinal, SoulState.values(), ByIdMap.OutOfBoundsStrategy.ZERO);

    /** The two bit sets, sent together so the record codec stays within six pairs. */
    private record Looks(int quincy, int hollow) {
        static final StreamCodec<ByteBuf, Looks> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Looks::quincy,
                ByteBufCodecs.VAR_INT, Looks::hollow,
                Looks::new);
    }

    // Six pairs, the most StreamCodec.composite takes: the two look bit sets travel as one nested pair.
    // The lambdas spell out their (boxed) types, so inference never has to guess through them.
    public static final StreamCodec<ByteBuf, SoulAppearance> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(STATE_BY_ID, SoulState::ordinal), SoulAppearance::state,
            ByteBufCodecs.FLOAT, SoulAppearance::chainFraction,
            ByteBufCodecs.BOOL, SoulAppearance::reiatsuReleased,
            ByteBufCodecs.VAR_INT, SoulAppearance::sheathModel,
            BlutMode.STREAM_CODEC, SoulAppearance::blut,
            Looks.STREAM_CODEC, (SoulAppearance look) -> new Looks(look.quincyLook(), look.hollowLook()),
            (SoulState state, Float chain, Boolean released, Integer sheath, BlutMode blut, Looks looks) ->
                    new SoulAppearance(state, chain, released, sheath, blut, looks.quincy(), looks.hollow()));

    /** An appearance without Blut, as every soul but a Quincy's looks. */
    public SoulAppearance(SoulState state, float chainFraction, boolean reiatsuReleased, int sheathModel) {
        this(state, chainFraction, reiatsuReleased, sheathModel, BlutMode.OFF);
    }

    /** An appearance with none of the Quincy arts showing. */
    public SoulAppearance(SoulState state, float chainFraction, boolean reiatsuReleased, int sheathModel,
                          BlutMode blut) {
        this(state, chainFraction, reiatsuReleased, sheathModel, blut, 0);
    }

    /** An appearance with nothing of a Hollow's look: every soul's but a Hollow's. */
    public SoulAppearance(SoulState state, float chainFraction, boolean reiatsuReleased, int sheathModel,
                          BlutMode blut, int quincyLook) {
        this(state, chainFraction, reiatsuReleased, sheathModel, blut, quincyLook, 0);
    }

    /** Never null: an appearance sent without Blut has none. */
    public SoulAppearance {
        blut = blut == null ? BlutMode.OFF : blut;
    }

    /** Whether a zanpakutō is drawn sheathed on this soul. */
    public boolean bladeSheathed() {
        return sheathModel >= 0;
    }

    /** The same appearance with these Hollow look bits instead. */
    public SoulAppearance withHollowLook(int look) {
        return new SoulAppearance(state, chainFraction, reiatsuReleased, sheathModel, blut, quincyLook, look);
    }

    /**
     * How this soul looks, leaving out what only its body shows (the sheathed blade) and the Hollow look
     * that only the server's moment-to-moment bookkeeping gives (the jaw and the crack): of the Hollow's
     * look, only the red eyes of hunger, which the saved soul tells.
     */
    public static SoulAppearance of(SoulData data) {
        float chain = data.chainMaxTicks() <= 0 ? 0.0F
                : Mth.ceil(data.chainFraction() * CHAIN_STEPS) / (float) CHAIN_STEPS;
        return new SoulAppearance(data.state(), chain, data.isReiatsuReleased(), -1, QuincyLogic.activeBlut(data),
                QuincyArtsLogic.quincyLook(data), HollowPlayerLogic.hollowLook(data));
    }

    /** How this creature looks, with its soul, now: the jaw open while it devours and the mask cracked after a blow. */
    public static SoulAppearance of(LivingEntity entity, SoulData data) {
        SoulAppearance soul = of(data);
        return new SoulAppearance(soul.state(), soul.chainFraction(), soul.reiatsuReleased(),
                ShinigamiLogic.sheathedModel(entity), soul.blut(), soul.quincyLook(),
                HollowPlayerLogic.hollowLook(data, entity.level().getGameTime()));
    }
}
