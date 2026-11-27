package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;

/**
 * A soul's road to Bankai and, once it is reached, the Bankai's name: the record its zanpakutō bond keeps under
 * {@code "bankai"} (see {@link ZanpakutoBond#bankai}). Saved with the bond and read leniently key by key, so a save from
 * before Bankai, which has no {@code "bankai"}, reads as {@link #NONE}, and one bad value falls back to its own default.
 * Sent to the owner's client whole: the name is kept here because a client has no spirits to read it from. Times are
 * the overworld's game time in ticks, the clock every world reads and neither sleep nor {@code /time} moves;
 * {@link ZanpakutoBond#NEVER} means it never happened.
 *
 * @param achievedAt   when the soul reached Bankai, by subjugating its spirit or by command; NEVER while it has not
 * @param name         the Bankai's name in its spirit's colour, set when it is reached; empty until then
 * @param windowEndsAt when the spirit a Tenshintai drew out goes back into the blade; NEVER while none is out
 * @param doll         where that Tenshintai stands, while one is out
 * @param spiritHealth the share of its health that spirit has kept between bouts, from 0 to 1; 1 while none is out
 * @param tries        bouts begun against the spirit, won or not
 * @param lastTryAt    when the last of them began
 * @param shikaiTicks  ticks the soul has spent with its Shikai released, in all, towards Bankai by Jinzen alone
 */
public record BankaiRecord(long achievedAt, Component name, long windowEndsAt, Optional<GlobalPos> doll,
                           float spiritHealth, int tries, long lastTryAt, long shikaiTicks) {
    public static final BankaiRecord NONE = new BankaiRecord(ZanpakutoBond.NEVER, CommonComponents.EMPTY,
            ZanpakutoBond.NEVER, Optional.empty(), 1.0F, 0, ZanpakutoBond.NEVER, 0L);

    public static final Codec<BankaiRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.lenientOptionalFieldOf("achieved_at", ZanpakutoBond.NEVER).forGetter(BankaiRecord::achievedAt),
            ComponentSerialization.FLAT_CODEC.lenientOptionalFieldOf("name", CommonComponents.EMPTY)
                    .forGetter(BankaiRecord::name),
            Codec.LONG.lenientOptionalFieldOf("window_ends_at", ZanpakutoBond.NEVER).forGetter(BankaiRecord::windowEndsAt),
            GlobalPos.CODEC.lenientOptionalFieldOf("doll").forGetter(BankaiRecord::doll),
            Codec.FLOAT.lenientOptionalFieldOf("spirit_health", 1.0F).forGetter(BankaiRecord::spiritHealth),
            Codec.INT.lenientOptionalFieldOf("tries", 0).forGetter(BankaiRecord::tries),
            Codec.LONG.lenientOptionalFieldOf("last_try_at", ZanpakutoBond.NEVER).forGetter(BankaiRecord::lastTryAt),
            Codec.LONG.lenientOptionalFieldOf("shikai_ticks", 0L).forGetter(BankaiRecord::shikaiTicks)
    ).apply(instance, BankaiRecord::new));

    /** Keeps the spirit's share of health within 0 and 1 (a share that is no number is whole), and the counts above 0. */
    public BankaiRecord {
        name = name == null ? CommonComponents.EMPTY : name;
        doll = doll == null ? Optional.empty() : doll;
        spiritHealth = Float.isNaN(spiritHealth) ? 1.0F : Mth.clamp(spiritHealth, 0.0F, 1.0F);
        tries = Math.max(0, tries);
        shikaiTicks = Math.max(0L, shikaiTicks);
    }

    /** Whether the soul has reached Bankai. */
    public boolean achieved() {
        return achievedAt != ZanpakutoBond.NEVER;
    }

    /**
     * Reached now, by this name: a spirit a Tenshintai drew out goes back into the blade, and the bouts fought and the
     * time in Shikai are kept.
     */
    public BankaiRecord reached(long now, Component value) {
        return new BankaiRecord(now, value, ZanpakutoBond.NEVER, Optional.empty(), 1.0F, tries, lastTryAt, shikaiTicks);
    }

    /** A bout against the spirit begins now: one more try, and the last of them now. */
    public BankaiRecord tried(long now) {
        return new BankaiRecord(achievedAt, name, windowEndsAt, doll, spiritHealth, tries + 1, now, shikaiTicks);
    }

    /** A Tenshintai set down here, its window open until {@code endsAt}, its spirit whole; the tries and the last kept. */
    public BankaiRecord dollAt(GlobalPos place, long endsAt) {
        return new BankaiRecord(achievedAt, name, endsAt, Optional.of(place), 1.0F, tries, lastTryAt, shikaiTicks);
    }

    /** The share of its health the doll's spirit kept as a bout by the doll ended, for the next. */
    public BankaiRecord wounded(float share) {
        return new BankaiRecord(achievedAt, name, windowEndsAt, doll, share, tries, lastTryAt, shikaiTicks);
    }

    /** No doll set down, and so no window and no wounds either. */
    public BankaiRecord closed() {
        return new BankaiRecord(achievedAt, name, ZanpakutoBond.NEVER, Optional.empty(), 1.0F, tries, lastTryAt,
                shikaiTicks);
    }
}
