package io.github.masteryoga1.bleach.zanpakuto;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One step of a spirit's test as the client shows it, built by the server as finished text. The
 * client sends back the step's {@code index} with its answer, so a stale screen cannot answer the
 * wrong step.
 *
 * @param index     which step of the spirit's test this is, from 0
 * @param ticks     how long the step allows, or 0 for no limit (a question)
 * @param prompt    what the spirit asks or shows
 * @param answers   a question's answers, in the shuffled order they are shown in; empty for other kinds
 * @param highlight the ribbon the client marks red, or -1 for none
 */
public record JinzenStep(JinzenStepKind kind, int index, int ticks, Component prompt, List<Component> answers,
                         int highlight) {
    /** How many ribbons hang in a row. */
    public static final int RIBBONS = 16;
    /** The most answers a step can carry; a spirit's question has at most 6. */
    public static final int MAX_ANSWERS = 8;

    public static final StreamCodec<RegistryFriendlyByteBuf, JinzenStep> STREAM_CODEC = StreamCodec.composite(
            JinzenStepKind.STREAM_CODEC, JinzenStep::kind,
            ByteBufCodecs.VAR_INT, JinzenStep::index,
            ByteBufCodecs.VAR_INT, JinzenStep::ticks,
            ComponentSerialization.STREAM_CODEC, JinzenStep::prompt,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ANSWERS)), JinzenStep::answers,
            ByteBufCodecs.VAR_INT, JinzenStep::highlight,
            JinzenStep::new);

    /** Never carries more answers than the wire allows, and never a list that can change afterwards. */
    public JinzenStep {
        answers = List.copyOf(answers.size() > MAX_ANSWERS ? answers.subList(0, MAX_ANSWERS) : answers);
    }
}
