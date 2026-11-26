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
 * in it, how far it is released and the colour it shows. Clients read the colour and the stage from
 * here, never from the spirit's data, which only the server has. A stack without it is a nameless
 * Asauchi.
 *
 * @param owner     the soul the blade answers to; empty while it is nameless
 * @param ownerName that soul's name when the blade was bound, for the tooltip
 * @param spirit    the id of the spirit that lives in it
 * @param stage     whether it is sealed, in its Shikai form or in its Bankai
 * @param constant  whether it is a constant release, which never seals once released
 * @param colour    the spirit's RGB colour, for the name, the HUD outline and the Getsuga Tenshō
 */
public record ZanpakutoBlade(Optional<UUID> owner, String ownerName, Optional<ResourceLocation> spirit,
                             ZanpakutoStage stage, boolean constant, int colour) {
    /** Zangetsu's pale blue, used until a blade has a spirit of its own. */
    public static final int DEFAULT_COLOUR = 0xBFEFFF;

    public static final ZanpakutoBlade UNBOUND =
            new ZanpakutoBlade(Optional.empty(), "", Optional.empty(), ZanpakutoStage.SEALED, false, DEFAULT_COLOUR);

    // Every field is read leniently: a stack whose blade data is damaged keeps the rest of it. The stage is saved by
    // name, and not at all while sealed, as every default is left out. A blade saved before Bankai has no stage but a
    // "released" flag, which is read only when no stage can be, as Shikai if set, and is never written again: a
    // released Zangetsu saved then is still released. A stage that cannot be read falls back to that flag, or to sealed.
    public static final Codec<ZanpakutoBlade> CODEC = RecordCodecBuilder.<ZanpakutoBlade>create(instance -> instance.group(
            UUIDUtil.CODEC.lenientOptionalFieldOf("owner").forGetter(ZanpakutoBlade::owner),
            Codec.STRING.lenientOptionalFieldOf("owner_name", "").forGetter(ZanpakutoBlade::ownerName),
            ResourceLocation.CODEC.lenientOptionalFieldOf("spirit").forGetter(ZanpakutoBlade::spirit),
            ZanpakutoStage.CODEC.lenientOptionalFieldOf("stage")
                    .forGetter(blade -> Optional.of(blade.stage()).filter(ZanpakutoStage::isReleased)),
            Codec.BOOL.lenientOptionalFieldOf("released").forGetter(blade -> Optional.<Boolean>empty()),
            Codec.BOOL.lenientOptionalFieldOf("constant", false).forGetter(ZanpakutoBlade::constant),
            Codec.INT.lenientOptionalFieldOf("colour", DEFAULT_COLOUR).forGetter(ZanpakutoBlade::colour)
    ).apply(instance, (owner, ownerName, spirit, stage, released, constant, colour) -> new ZanpakutoBlade(owner,
            ownerName, spirit, stage.orElse(released.orElse(false) ? ZanpakutoStage.SHIKAI : ZanpakutoStage.SEALED),
            constant, colour)));

    public static final StreamCodec<ByteBuf, ZanpakutoBlade> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), ZanpakutoBlade::owner,
            ByteBufCodecs.STRING_UTF8, ZanpakutoBlade::ownerName,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ZanpakutoBlade::spirit,
            ZanpakutoStage.STREAM_CODEC, ZanpakutoBlade::stage,
            ByteBufCodecs.BOOL, ZanpakutoBlade::constant,
            ByteBufCodecs.INT, ZanpakutoBlade::colour,
            ZanpakutoBlade::new);

    /** Keeps only the colour's RGB, so equal blades always compare equal; a missing stage is sealed. */
    public ZanpakutoBlade {
        ownerName = ownerName == null ? "" : ownerName;
        stage = stage == null ? ZanpakutoStage.SEALED : stage;
        colour &= 0xFFFFFF;
    }

    /** Whether the blade is released at all, in Shikai or in Bankai: anything but sealed. */
    public boolean released() {
        return stage.isReleased();
    }

    /** Whether the blade has learned its name for someone. */
    public boolean isBound() {
        return owner.isPresent();
    }

    public boolean isOwnedBy(@Nullable UUID id) {
        return id != null && owner.isPresent() && owner.get().equals(id);
    }

    public ZanpakutoBlade withStage(ZanpakutoStage value) {
        return new ZanpakutoBlade(owner, ownerName, spirit, value, constant, colour);
    }
}
