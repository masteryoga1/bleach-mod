package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SoulAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BleachMod.MODID);

    /**
     * A soul: a player's, or a spirit mob's (Shinigami NPCs, Hollows, Pluses). Not copied on death
     * automatically: {@link SoulEvents} copies a player's so it can change what carries over.
     */
    public static final Supplier<AttachmentType<SoulData>> SOUL = ATTACHMENT_TYPES.register("soul",
            () -> AttachmentType.builder(() -> new SoulData()).serialize(SoulData.CODEC).build());

    /** Client side only: how another player's soul looks, as last sent by the server. Never saved. */
    public static final Supplier<AttachmentType<SoulAppearance>> APPEARANCE = ATTACHMENT_TYPES.register("appearance",
            () -> AttachmentType.builder(() -> SoulAppearance.NONE).build());

    /** Server side only: until when a creature is fleeing released reiatsu, and from where. Never saved. */
    public static final Supplier<AttachmentType<Fright>> FRIGHT = ATTACHMENT_TYPES.register("fright",
            () -> AttachmentType.builder(() -> Fright.NONE).build());

    /** Reishi footing: whether the player stands on hardened reishi, and at what height. Never saved. */
    public static final Supplier<AttachmentType<FootingState>> FOOTING = ATTACHMENT_TYPES.register("footing",
            () -> AttachmentType.builder(() -> new FootingState()).build());

    private SoulAttachments() {
    }
}
