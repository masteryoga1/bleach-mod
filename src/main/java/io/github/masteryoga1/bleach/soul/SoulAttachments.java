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
     * A player's soul. Not copied on death automatically: {@link SoulEvents} copies it so it can
     * change what carries over.
     */
    public static final Supplier<AttachmentType<SoulData>> SOUL = ATTACHMENT_TYPES.register("soul",
            () -> AttachmentType.builder(() -> new SoulData()).serialize(SoulData.CODEC).build());

    private SoulAttachments() {
    }
}
