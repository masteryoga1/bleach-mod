package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A soul's travel between the worlds: the block it last left the Human World from by a Senkaimon, the game day it was
 * last handed a Jigokuchō (-1 for never), the Dangai crossing it is on, if any, and the block of the Rukongai it calls
 * home, if any. Saved and kept through death but never sent, with every field lenient so that later steps can add
 * theirs and a bad field reads as its default.
 */
public record TravelRecord(Optional<GlobalPos> humanWorldReturn, long butterflyDay, Optional<Crossing> crossing,
                           Optional<BlockPos> rukongaiHome) {
    public static final TravelRecord NONE = new TravelRecord(Optional.empty(), -1L, Optional.empty(), Optional.empty());
    public static final Codec<TravelRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.lenientOptionalFieldOf("human_world_return").forGetter(TravelRecord::humanWorldReturn),
            Codec.LONG.lenientOptionalFieldOf("butterfly_day", -1L).forGetter(TravelRecord::butterflyDay),
            Crossing.CODEC.lenientOptionalFieldOf("crossing").forGetter(TravelRecord::crossing),
            BlockPos.CODEC.lenientOptionalFieldOf("rukongai_home").forGetter(TravelRecord::rukongaiHome))
            .apply(instance, TravelRecord::new));

    /** A record on no crossing and with no home, as every one was before the Dangai. */
    public TravelRecord(Optional<GlobalPos> humanWorldReturn, long butterflyDay) {
        this(humanWorldReturn, butterflyDay, Optional.empty(), Optional.empty());
    }

    /** A record with no Rukongai home, as every one was before Konsō sent souls to the Rukongai. */
    public TravelRecord(Optional<GlobalPos> humanWorldReturn, long butterflyDay, Optional<Crossing> crossing) {
        this(humanWorldReturn, butterflyDay, crossing, Optional.empty());
    }

    /** This record, with the block a gate was just taken from in the Human World. */
    public TravelRecord withHumanWorldReturn(GlobalPos at) {
        return new TravelRecord(Optional.of(at), butterflyDay, crossing, rukongaiHome);
    }

    /** This record, with the day a Jigokuchō was last handed over. */
    public TravelRecord withButterflyDay(long day) {
        return new TravelRecord(humanWorldReturn, day, crossing, rukongaiHome);
    }

    /** This record, on this crossing of the Dangai, or on none. */
    public TravelRecord withCrossing(Optional<Crossing> crossing) {
        return new TravelRecord(humanWorldReturn, butterflyDay, crossing, rukongaiHome);
    }

    /** This record, with the block of the Rukongai its soul now calls home. */
    public TravelRecord withRukongaiHome(BlockPos home) {
        return new TravelRecord(humanWorldReturn, butterflyDay, crossing, Optional.of(home.immutable()));
    }

    /**
     * A crossing of the Dangai: the world it leads to, its lane, the gate's bearing from the Human World's centre
     * (radians from +x towards +z), which a crossing to Soul Society reads, and the game time its lane's Kōtotsu sets
     * out ({@link #UNMET} until it meets one). The first three are needed, so a crossing that reads badly reads as none.
     */
    public record Crossing(ResourceKey<Level> to, int lane, float bearing, long kototsuAt) {
        /** The set-out of a crossing that has not met its Kōtotsu, as every crossing saved before the Kōtotsu came. */
        public static final long UNMET = -1L;
        public static final Codec<Crossing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("to").forGetter(Crossing::to),
                Codec.INT.fieldOf("lane").forGetter(Crossing::lane),
                Codec.FLOAT.fieldOf("bearing").forGetter(Crossing::bearing),
                Codec.LONG.lenientOptionalFieldOf("kototsu_at", UNMET).forGetter(Crossing::kototsuAt))
                .apply(instance, Crossing::new));

        /** A crossing that has not met its Kōtotsu yet. */
        public Crossing(ResourceKey<Level> to, int lane, float bearing) {
            this(to, lane, bearing, UNMET);
        }

        /** Whether this crossing has met its lane's Kōtotsu. */
        public boolean met() {
            return kototsuAt >= 0L;
        }

        /** This crossing, its Kōtotsu setting out at this game time. */
        public Crossing withKototsuAt(long at) {
            return new Crossing(to, lane, bearing, at);
        }
    }
}
