package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A soul's bond with its Asauchi and, once it is earned, its zanpakutō's name. The bond grows while
 * the soul carries a nameless Asauchi; how the soul fights meanwhile builds a hidden leaning that
 * decides which spirit it meets in Jinzen. Saved with the soul and sent to its owner's client, all but
 * the leaning (see {@link SoulData#forClient}). Times are game times in ticks; {@link #NEVER} means
 * it never happened.
 *
 * @param bond             how far the bond has grown, from 0 to {@link #FULL}
 * @param affinity         the raw leaning towards each kind of spirit, each up to {@link #MAX_AFFINITY}
 * @param spirit           the spirit the blade will become, locked in once the bond is full
 * @param named            whether the soul has learned its zanpakutō's name
 * @param name             that name, in the spirit's colour (empty until then)
 * @param constant         whether the named zanpakutō is a constant-release type, which never seals
 * @param firstReleaseDone whether the soul has released it at least once
 * @param jinzens          how many times the soul has opened Jinzen
 * @param lastJinzenBondAt when a Jinzen last grew the bond
 * @param testFailedAt     when the soul last failed its spirit's test
 * @param testFailures     how many times it has failed it
 * @param whispered        the last quarter of the bond the blade whispered at, from 0 to 4
 * @param getsugaFired     how many Getsuga Tenshō the soul has loosed
 * @param getsugaNamed     whether Zangetsu has named the technique yet
 * @param lastSleepBondAt  when a night's sleep last grew the bond
 * @param bankai           the road to Bankai and, once it is reached, its name (see {@link BankaiRecord}); this is the
 *                         record's sixteenth field, the most a record codec takes, so anything more goes in there
 */
public record ZanpakutoBond(int bond, Map<ZanpakutoType, Integer> affinity, Optional<ResourceLocation> spirit,
                            boolean named, Component name, boolean constant, boolean firstReleaseDone, int jinzens,
                            long lastJinzenBondAt, long testFailedAt, int testFailures, int whispered,
                            int getsugaFired, boolean getsugaNamed, long lastSleepBondAt, BankaiRecord bankai) {
    /** A full bond: the blade is ready to speak. */
    public static final int FULL = 1000;
    public static final int MAX_AFFINITY = 10000;
    /** Far enough in the past that {@code now - NEVER} cannot overflow, as {@link ShinigamiProgress#NEVER}. */
    public static final long NEVER = Long.MIN_VALUE / 2;

    public static final ZanpakutoBond NONE = new ZanpakutoBond(0, Map.of(), Optional.empty(), false,
            CommonComponents.EMPTY, false, false, 0, NEVER, NEVER, 0, 0, 0, false, NEVER, BankaiRecord.NONE);

    // Built on first use, so loading this class never starts SoulData's static set-up halfway through its own.
    private static final Codec<Map<ZanpakutoType, Integer>> AFFINITY_CODEC = Codec.lazyInitialized(
            () -> SoulData.enumKeyedMap(ZanpakutoType.class, ZanpakutoType.values()));

    // Every field is read leniently, so one bad value falls back to its default on its own; a save from before Bankai
    // has no "bankai" and reads as not having reached it.
    public static final Codec<ZanpakutoBond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("bond", 0).forGetter(ZanpakutoBond::bond),
            AFFINITY_CODEC.lenientOptionalFieldOf("affinity", Map.of()).forGetter(ZanpakutoBond::affinity),
            ResourceLocation.CODEC.lenientOptionalFieldOf("spirit").forGetter(ZanpakutoBond::spirit),
            Codec.BOOL.lenientOptionalFieldOf("named", false).forGetter(ZanpakutoBond::named),
            ComponentSerialization.FLAT_CODEC.lenientOptionalFieldOf("name", CommonComponents.EMPTY)
                    .forGetter(ZanpakutoBond::name),
            Codec.BOOL.lenientOptionalFieldOf("constant", false).forGetter(ZanpakutoBond::constant),
            Codec.BOOL.lenientOptionalFieldOf("first_release_done", false).forGetter(ZanpakutoBond::firstReleaseDone),
            Codec.INT.lenientOptionalFieldOf("jinzens", 0).forGetter(ZanpakutoBond::jinzens),
            Codec.LONG.lenientOptionalFieldOf("last_jinzen_bond_at", NEVER).forGetter(ZanpakutoBond::lastJinzenBondAt),
            Codec.LONG.lenientOptionalFieldOf("test_failed_at", NEVER).forGetter(ZanpakutoBond::testFailedAt),
            Codec.INT.lenientOptionalFieldOf("test_failures", 0).forGetter(ZanpakutoBond::testFailures),
            Codec.INT.lenientOptionalFieldOf("whispered", 0).forGetter(ZanpakutoBond::whispered),
            Codec.INT.lenientOptionalFieldOf("getsuga_fired", 0).forGetter(ZanpakutoBond::getsugaFired),
            Codec.BOOL.lenientOptionalFieldOf("getsuga_named", false).forGetter(ZanpakutoBond::getsugaNamed),
            Codec.LONG.lenientOptionalFieldOf("last_sleep_bond_at", NEVER).forGetter(ZanpakutoBond::lastSleepBondAt),
            BankaiRecord.CODEC.lenientOptionalFieldOf("bankai", BankaiRecord.NONE).forGetter(ZanpakutoBond::bankai)
    ).apply(instance, ZanpakutoBond::new));

    /** Keeps every number in range, and drops leanings of nothing so equal bonds compare equal. */
    public ZanpakutoBond {
        bond = Mth.clamp(bond, 0, FULL);
        whispered = Mth.clamp(whispered, 0, 4);
        jinzens = Math.max(0, jinzens);
        testFailures = Math.max(0, testFailures);
        getsugaFired = Math.max(0, getsugaFired);
        spirit = spirit == null ? Optional.empty() : spirit;
        name = name == null ? CommonComponents.EMPTY : name;
        bankai = bankai == null ? BankaiRecord.NONE : bankai;
        EnumMap<ZanpakutoType, Integer> clamped = new EnumMap<>(ZanpakutoType.class);
        if (affinity != null) {
            affinity.forEach((type, value) -> {
                if (type != null && value != null && value > 0) {
                    clamped.put(type, Math.min(value, MAX_AFFINITY));
                }
            });
        }
        affinity = Map.copyOf(clamped);
    }

    /** The bond as it was before Bankai: no road to it begun. */
    public ZanpakutoBond(int bond, Map<ZanpakutoType, Integer> affinity, Optional<ResourceLocation> spirit,
                         boolean named, Component name, boolean constant, boolean firstReleaseDone, int jinzens,
                         long lastJinzenBondAt, long testFailedAt, int testFailures, int whispered,
                         int getsugaFired, boolean getsugaNamed, long lastSleepBondAt) {
        this(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens, lastJinzenBondAt, testFailedAt,
                testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt, BankaiRecord.NONE);
    }

    /** The raw leaning towards one kind of spirit. */
    public int affinity(ZanpakutoType type) {
        return affinity.getOrDefault(type, 0);
    }

    public boolean isFull() {
        return bond >= FULL;
    }

    public ZanpakutoBond withBond(int value) {
        return new ZanpakutoBond(value, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    public ZanpakutoBond withAffinity(ZanpakutoType type, int value) {
        EnumMap<ZanpakutoType, Integer> changed = new EnumMap<>(ZanpakutoType.class);
        changed.putAll(affinity);
        changed.put(type, value);
        return new ZanpakutoBond(bond, changed, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    /** The bond without the hidden leaning, for the owner's client. */
    public ZanpakutoBond withoutAffinity() {
        return new ZanpakutoBond(bond, Map.of(), spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    public ZanpakutoBond withSpirit(Optional<ResourceLocation> value) {
        return new ZanpakutoBond(bond, affinity, value, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    /** The soul has learned the name: the bond is full and the blade is its own. */
    public ZanpakutoBond asNamed(Component value, boolean constantRelease) {
        return new ZanpakutoBond(FULL, affinity, spirit, true, value, constantRelease, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    public ZanpakutoBond withFirstReleaseDone(boolean value) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, value, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    public ZanpakutoBond withJinzens(int value) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, value,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    public ZanpakutoBond withLastJinzenBondAt(long value) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                value, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt, bankai);
    }

    /** The soul failed its spirit's test just now, once more. */
    public ZanpakutoBond failed(long now) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, now, testFailures + 1, whispered, getsugaFired, getsugaNamed, lastSleepBondAt, bankai);
    }

    public ZanpakutoBond withWhispered(int quarter) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, quarter, getsugaFired, getsugaNamed, lastSleepBondAt,
                bankai);
    }

    public ZanpakutoBond withGetsugaFired(int count) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, count, getsugaNamed, lastSleepBondAt, bankai);
    }

    public ZanpakutoBond withGetsugaNamed(boolean value) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, value, lastSleepBondAt, bankai);
    }

    public ZanpakutoBond withLastSleepBondAt(long value) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, value, bankai);
    }

    public ZanpakutoBond withBankai(BankaiRecord value) {
        return new ZanpakutoBond(bond, affinity, spirit, named, name, constant, firstReleaseDone, jinzens,
                lastJinzenBondAt, testFailedAt, testFailures, whispered, getsugaFired, getsugaNamed, lastSleepBondAt,
                value);
    }
}
