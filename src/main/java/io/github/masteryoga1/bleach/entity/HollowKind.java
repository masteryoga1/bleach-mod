package io.github.masteryoga1.bleach.entity;

import com.mojang.serialization.Codec;
import io.github.masteryoga1.bleach.BleachMod;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

/**
 * The kinds of Hollow and everything that sets them apart, in one table so the balance lives in one
 * place: their Normal-difficulty stats, how hard they chase and bite, their reiatsu, how big they may
 * grow, where the mask sits, how they are drawn and how often they come through a Garganta.
 */
public enum HollowKind implements StringRepresentable {
    SPIDER("spider", new Stats(60.0, 5.0, 2.0, 0.30, 32.0, 0.0, 0.0, 0.6), 20, 1.0, 50.0F, 10,
            new OnHit(MobEffects.CONFUSION, 7, 10, 0), 0.90F, 1.15F,
            new MaskSpot(0.62, 0.65, 0.6), new Look(1.15F, 0.0F, 0.0F, 0.8F), 30, 0.0F),
    HEXAPODUS("hexapodus", new Stats(50.0, 4.0, 2.0, 0.32, 32.0, 0.1, 0.0, 0.6), 20, 1.2, 45.0F, 10,
            new OnHit(MobEffects.MOVEMENT_SLOWDOWN, 4, 6, 0), 1.00F, 1.15F,
            new MaskSpot(0.55, 0.56, 0.55), new Look(1.0F, 0.0F, 0.0F, 0.7F), 15, 0.0F),
    HOUND("hound", new Stats(40.0, 4.0, 1.0, 0.33, 40.0, 0.0, 0.0, 0.6), 20, 1.0, 35.0F, 6,
            null, 0.90F, 1.10F,
            new MaskSpot(0.5, 0.9, 0.6), new Look(1.4F, 0.2F, 0.2F, 0.6F), 20, 60.0F),
    SERPENT("serpent", new Stats(70.0, 6.0, 3.0, 0.26, 32.0, 0.3, 0.0, 1.0), 24, 1.0, 60.0F, 12,
            new OnHit(MobEffects.POISON, 7, 10, 0), 0.90F, 1.20F,
            new MaskSpot(0.25, 2.03, 0.5), new Look(1.0F, 0.0F, 0.1F, 0.6F), 25, 80.0F),
    /** The first Hollow: it only ever comes by the script, never by chance. */
    FISHBONE_D("fishbone_d", new Stats(80.0, 6.0, 4.0, 0.25, 48.0, 0.6, 1.0, 1.0), 30, 1.0, 90.0F, 30,
            null, 1.0F, 1.0F,
            new MaskSpot(0.55, 2.72, 0.8), new Look(1.45F, 0.45F, 0.3F, 1.0F), 0, Float.MAX_VALUE);

    public static final Codec<HollowKind> CODEC = StringRepresentable.fromEnum(HollowKind::values);

    /** How far a Hollow may fall without harm. High enough that it never takes fall damage or raises dust. */
    public static final double SAFE_FALL_DISTANCE = 1024.0;

    /** Base attribute values on Normal, before size, difficulty and the server's multipliers. */
    public record Stats(double maxHealth, double attack, double armor, double speed, double followRange,
                        double knockbackResistance, double attackKnockback, double stepHeight) {
    }

    /** What a landed bite does, and for how many seconds on Normal and on Hard. */
    public record OnHit(Holder<MobEffect> effect, int normalSeconds, int hardSeconds, int amplifier) {
    }

    /**
     * Where the mask sits, in blocks at size 1: {@code forward} along where the head faces, {@code up}
     * from the feet, and the edge of the cube that counts as the mask.
     */
    public record MaskSpot(double forward, double up, double size) {
    }

    /**
     * How the kind is drawn: the model's scale, how far it is shifted back so the hitbox covers it, how
     * much wider than the hitbox the crosshair may aim, and the shadow.
     */
    public record Look(float modelScale, float backShift, float pickRadius, float shadowRadius) {
    }

    private final String name;
    public final Stats stats;
    /** Ticks between bites. */
    public final int attackInterval;
    /** Speed multiplier while chasing prey. */
    public final double chaseSpeed;
    /** Reiatsu at size 1: the soul's maximum reiryoku. */
    public final float reiatsu;
    /** Experience given straight to the player who kills one. */
    public final int xp;
    @Nullable
    public final OnHit onHit;
    /** The range a Hollow's size is rolled from when it spawns. */
    public final float scaleMin;
    public final float scaleMax;
    public final MaskSpot mask;
    public final Look look;
    /** How likely this kind is to come through a Garganta, against the others; 0 never by chance. */
    public final int arrivalWeight;
    /** The reiatsu a soul needs before this kind comes for it. */
    public final float minReiatsu;

    HollowKind(String name, Stats stats, int attackInterval, double chaseSpeed, float reiatsu, int xp,
               @Nullable OnHit onHit, float scaleMin, float scaleMax, MaskSpot mask, Look look,
               int arrivalWeight, float minReiatsu) {
        this.name = name;
        this.stats = stats;
        this.attackInterval = attackInterval;
        this.chaseSpeed = chaseSpeed;
        this.reiatsu = reiatsu;
        this.xp = xp;
        this.onHit = onHit;
        this.scaleMin = scaleMin;
        this.scaleMax = scaleMax;
        this.mask = mask;
        this.look = look;
        this.arrivalWeight = arrivalWeight;
        this.minReiatsu = minReiatsu;
    }

    /** The attributes this kind is registered with: its Normal stats, and no fall damage. */
    public AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, stats.maxHealth())
                .add(Attributes.ATTACK_DAMAGE, stats.attack())
                .add(Attributes.ARMOR, stats.armor())
                .add(Attributes.MOVEMENT_SPEED, stats.speed())
                .add(Attributes.FOLLOW_RANGE, stats.followRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance())
                .add(Attributes.ATTACK_KNOCKBACK, stats.attackKnockback())
                .add(Attributes.STEP_HEIGHT, stats.stepHeight())
                .add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_DISTANCE);
    }

    /** A size for a new Hollow of this kind, anywhere in its range. */
    public float rollScale(RandomSource random) {
        return scaleMin + random.nextFloat() * (scaleMax - scaleMin);
    }

    /** The body texture, {@code bleach:textures/entity/hollow/<name>.png}. */
    public ResourceLocation texture() {
        return BleachMod.id("textures/entity/hollow/" + name + ".png");
    }

    /** The mask, drawn over the body as its own layer: whole, or cracked after a blow to it. */
    public ResourceLocation maskTexture(boolean cracked) {
        return BleachMod.id("textures/entity/hollow/" + name + (cracked ? "_mask_cracked.png" : "_mask.png"));
    }

    /** The kind with this short name, as the commands take it. */
    public static Optional<HollowKind> byName(String name) {
        return Arrays.stream(values()).filter(kind -> kind.name.equals(name)).findFirst();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
