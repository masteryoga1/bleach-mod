package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
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

    /**
     * Sealed copies of a player's own blades, taken from their drops on death and handed back when
     * they respawn. Saved, so a soul that logs out or whose server stops before respawning keeps them.
     */
    public static final Supplier<AttachmentType<List<ItemStack>>> KEPT_BLADES = ATTACHMENT_TYPES.register("kept_blades",
            () -> AttachmentType.builder(() -> List.<ItemStack>of())
                    .serialize(ItemStack.OPTIONAL_CODEC.listOf(), list -> !list.isEmpty()).build());

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
