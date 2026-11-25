package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
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

    /** Where the soul last left the Human World by a Senkaimon, and its last Jigokuchō's day. Saved; kept through death. */
    public static final Supplier<AttachmentType<TravelRecord>> TRAVEL = ATTACHMENT_TYPES.register("travel",
            () -> AttachmentType.builder(() -> TravelRecord.NONE)
                    .serialize(TravelRecord.CODEC, record -> !record.equals(TravelRecord.NONE)).copyOnDeath().build());

    /**
     * The game time a Shinigami on duty last came down to this soul, so the next waits (see {@link DutyEvents}). Saved
     * once one has come; kept through death; never sent.
     */
    public static final Supplier<AttachmentType<Long>> DUTY_VISIT = ATTACHMENT_TYPES.register("duty_visit",
            () -> AttachmentType.builder(() -> ShinigamiProgress.NEVER)
                    .serialize(Codec.LONG, at -> at != ShinigamiProgress.NEVER).copyOnDeath().build());

    /**
     * A soul's drill at the Academy's training dummies: its day, that day's points and the quarter carried (see {@link
     * DummyDrill}). Saved once it has one; kept through death; never sent.
     */
    public static final Supplier<AttachmentType<DummyDrill>> DUMMY_DRILL = ATTACHMENT_TYPES.register("dummy_drill",
            () -> AttachmentType.builder(() -> DummyDrill.NONE)
                    .serialize(DummyDrill.CODEC, drill -> !drill.equals(DummyDrill.NONE)).copyOnDeath().build());

    /**
     * Whether the Academy has handed this soul its uniform, at enrolment or at the instructor's next talk (see {@link
     * ShinigamiEvents#uniformAtTalk}). Saved once given, so an old save reads false; kept through death; never sent.
     */
    public static final Supplier<AttachmentType<Boolean>> UNIFORM_GIVEN = ATTACHMENT_TYPES.register("uniform_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL, given -> given).copyOnDeath().build());

    /**
     * A student's field trial (see {@link FieldTrial}): saved once it has one, so an old save reads none; kept through
     * death, so a pass outlives it; never sent.
     */
    public static final Supplier<AttachmentType<FieldTrial>> FIELD_TRIAL = ATTACHMENT_TYPES.register("field_trial",
            () -> AttachmentType.builder(() -> FieldTrial.NONE)
                    .serialize(FieldTrial.CODEC, trial -> !trial.equals(FieldTrial.NONE)).copyOnDeath().build());

    /**
     * The day (the mod's, {@link KidoLogic#lessonDay}) this soul last bought Hollow Bait from Kisuke Urahara, who sells
     * each soul one a day. Saved once bought; kept through death, so dying buys no more; never sent.
     */
    public static final Supplier<AttachmentType<Long>> SHOP_BAIT_DAY = ATTACHMENT_TYPES.register("shop_bait_day",
            () -> AttachmentType.builder(() -> ShinigamiProgress.NEVER)
                    .serialize(Codec.LONG, day -> day != ShinigamiProgress.NEVER).copyOnDeath().build());

    /**
     * The day (the mod's, {@link KidoLogic#lessonDay}) Kisuke Urahara last opened this soul his Senkaimon, which he opens
     * each soul once a day. Saved once set; kept through death, so dying opens no more; never sent.
     */
    public static final Supplier<AttachmentType<Long>> SHOP_SENKAIMON_DAY = ATTACHMENT_TYPES.register(
            "shop_senkaimon_day", () -> AttachmentType.builder(() -> ShinigamiProgress.NEVER)
                    .serialize(Codec.LONG, day -> day != ShinigamiProgress.NEVER).copyOnDeath().build());

    /** As {@link #SHOP_SENKAIMON_DAY}, for his Garganta between the Keikaigi's posts. */
    public static final Supplier<AttachmentType<Long>> SHOP_GARGANTA_DAY = ATTACHMENT_TYPES.register(
            "shop_garganta_day", () -> AttachmentType.builder(() -> ShinigamiProgress.NEVER)
                    .serialize(Codec.LONG, day -> day != ShinigamiProgress.NEVER).copyOnDeath().build());

    /** Client side only: how another player's soul looks, as last sent by the server. Never saved. */
    public static final Supplier<AttachmentType<SoulAppearance>> APPEARANCE = ATTACHMENT_TYPES.register("appearance",
            () -> AttachmentType.builder(() -> SoulAppearance.NONE).build());

    /** Server side only: until when a creature is fleeing released reiatsu, and from where. Never saved. */
    public static final Supplier<AttachmentType<Fright>> FRIGHT = ATTACHMENT_TYPES.register("fright",
            () -> AttachmentType.builder(() -> Fright.NONE).build());

    /**
     * Server side only: whom a creature lost in a Kidō smoke cannot pick out, and until when (see {@link KidoSmoke}).
     * Never saved.
     */
    public static final Supplier<AttachmentType<KidoSmoke.Lost>> LOST = ATTACHMENT_TYPES.register("kido_lost",
            () -> AttachmentType.builder(() -> KidoSmoke.Lost.NONE).build());

    /** Reishi footing: whether the player stands on hardened reishi, and at what height. Never saved. */
    public static final Supplier<AttachmentType<FootingState>> FOOTING = ATTACHMENT_TYPES.register("footing",
            () -> AttachmentType.builder(() -> new FootingState()).build());

    private SoulAttachments() {
    }
}
