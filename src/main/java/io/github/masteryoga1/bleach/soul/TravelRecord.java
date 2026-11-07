package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.GlobalPos;

/**
 * A soul's travel between the worlds: the block it last left the Human World from by a Senkaimon, and the game day it
 * was last handed a Jigokuchō (-1 for never). Saved and kept through death but never sent, with every field lenient so
 * that later steps can add theirs and a bad field reads as its default.
 */
public record TravelRecord(Optional<GlobalPos> humanWorldReturn, long butterflyDay) {
    public static final TravelRecord NONE = new TravelRecord(Optional.empty(), -1L);
    public static final Codec<TravelRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.lenientOptionalFieldOf("human_world_return").forGetter(TravelRecord::humanWorldReturn),
            Codec.LONG.lenientOptionalFieldOf("butterfly_day", -1L).forGetter(TravelRecord::butterflyDay))
            .apply(instance, TravelRecord::new));

    /** This record, with the block a gate was just taken from in the Human World. */
    public TravelRecord withHumanWorldReturn(GlobalPos at) {
        return new TravelRecord(Optional.of(at), butterflyDay);
    }

    /** This record, with the day a Jigokuchō was last handed over. */
    public TravelRecord withButterflyDay(long day) {
        return new TravelRecord(humanWorldReturn, day);
    }
}
