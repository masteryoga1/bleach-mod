package io.github.masteryoga1.bleach.zanpakuto;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The item data on an Asauchi that has become someone's zanpakutō: whose it is, which spirit lives
 * in it, whether it is released and the colour it shows. Clients read the colour and the state from
 * here, never from the spirit's data, which only the server has. A stack without it is a nameless
 * Asauchi.
 *
 * @param owner     the soul the blade answers to; empty while it is nameless
 * @param ownerName that soul's name when the blade was bound, for the tooltip
 * @param spirit    the id of the spirit that lives in it
 * @param released  whether it is in its Shikai form
 * @param constant  whether it is a constant release, which never seals once released
 * @param colour    the spirit's RGB colour, for the name, the HUD outline and the Getsuga Tenshō
 */
public record ZanpakutoBlade(Optional<UUID> owner, String ownerName, Optional<ResourceLocation> spirit,
                             boolean released, boolean constant, int colour) {
    /** Zangetsu's pale blue, used until a blade has a spirit of its own. */
    public static final int DEFAULT_COLOUR = 0xBFEFFF;

    public static final ZanpakutoBlade UNBOUND =
            new ZanpakutoBlade(Optional.empty(), "", Optional.empty(), false, false, DEFAULT_COLOUR);

    // Every field is read leniently: a stack whose blade data is damaged keeps the rest of it.
    public static final Codec<ZanpakutoBlade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.lenientOptionalFieldOf("owner").forGetter(ZanpakutoBlade::owner),
            Codec.STRING.lenientOptionalFieldOf("owner_name", "").forGetter(ZanpakutoBlade::ownerName),
            ResourceLocation.CODEC.lenientOptionalFieldOf("spirit").forGetter(ZanpakutoBlade::spirit),
            Codec.BOOL.lenientOptionalFieldOf("released", false).forGetter(ZanpakutoBlade::released),
            Codec.BOOL.lenientOptionalFieldOf("constant", false).forGetter(ZanpakutoBlade::constant),
            Codec.INT.lenientOptionalFieldOf("colour", DEFAULT_COLOUR).forGetter(ZanpakutoBlade::colour)
    ).apply(instance, ZanpakutoBlade::new));

    public static final StreamCodec<ByteBuf, ZanpakutoBlade> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), ZanpakutoBlade::owner,
            ByteBufCodecs.STRING_UTF8, ZanpakutoBlade::ownerName,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ZanpakutoBlade::spirit,
            ByteBufCodecs.BOOL, ZanpakutoBlade::released,
            ByteBufCodecs.BOOL, ZanpakutoBlade::constant,
            ByteBufCodecs.INT, ZanpakutoBlade::colour,
            ZanpakutoBlade::new);

    /** Keeps only the colour's RGB, so equal blades always compare equal. */
    public ZanpakutoBlade {
        ownerName = ownerName == null ? "" : ownerName;
        colour &= 0xFFFFFF;
    }

    /** Whether the blade has learned its name for someone. */
    public boolean isBound() {
        return owner.isPresent();
    }

    public boolean isOwnedBy(@Nullable UUID id) {
        return id != null && owner.isPresent() && owner.get().equals(id);
    }

    public ZanpakutoBlade withReleased(boolean value) {
        return new ZanpakutoBlade(owner, ownerName, spirit, value, constant, colour);
    }
}
