package io.github.masteryoga1.bleach.zanpakuto;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * The kinds of step a spirit's test is made of. Read by name from a spirit's file, and sent to the
 * client by ordinal, so new kinds go last.
 */
public enum JinzenStepKind implements StringRepresentable {
    /** A row of white ribbons with one red one, which must be taken before time runs out. */
    RIBBONS("ribbons", true),
    /** A question with a few answers, shown in a shuffled order. Only the spirit's first answer is right. */
    QUESTION("question", false),
    /** Jaws closing from both sides; the soul must strike through the gap before they meet. */
    FANGS("fangs", true);

    public static final Codec<JinzenStepKind> CODEC = StringRepresentable.fromEnum(JinzenStepKind::values);
    private static final IntFunction<JinzenStepKind> BY_ID =
            ByIdMap.continuous(JinzenStepKind::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, JinzenStepKind> STREAM_CODEC =
            ByteBufCodecs.idMapper(BY_ID, JinzenStepKind::ordinal);

    private final String name;
    private final boolean timed;

    JinzenStepKind(String name, boolean timed) {
        this.name = name;
        this.timed = timed;
    }

    /** Whether the step runs against the clock, and so fails by itself if left unanswered. */
    public boolean timed() {
        return timed;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
