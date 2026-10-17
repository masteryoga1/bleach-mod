package io.github.masteryoga1.bleach.zanpakuto;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/**
 * The scenery behind the Jinzen screen: the world inside the blade. The client draws each one from
 * shapes in the page's tint, with no textures. Read by name from a spirit's file, and sent to the
 * client by ordinal, so new backdrops go last.
 */
public enum JinzenBackdrop implements StringRepresentable {
    /** Darkness, for a blade that is silent or whose spirit is gone. */
    VOID("void"),
    /** Drifting mist, for a spirit that is stirring or waiting but not yet met. */
    MIST("mist"),
    /** A city lying on its side under a pale sky, as Zangetsu's world is. */
    SIDEWAYS_CITY("sideways_city"),
    /** Thick jungle, as Zabimaru's world is. */
    JUNGLE("jungle");

    public static final Codec<JinzenBackdrop> CODEC = StringRepresentable.fromEnum(JinzenBackdrop::values);
    private static final IntFunction<JinzenBackdrop> BY_ID =
            ByIdMap.continuous(JinzenBackdrop::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, JinzenBackdrop> STREAM_CODEC =
            ByteBufCodecs.idMapper(BY_ID, JinzenBackdrop::ordinal);

    private final String name;

    JinzenBackdrop(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
