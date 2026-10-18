package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.quincy.QuincyStage;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Everything the Quincy arts do in the world: the Sanrei Glove's training and its tearing, Letzt
 * Stil and the seal that follows, the rite and the arrow that bring the power back, Ginrei Kojaku's
 * stream and Licht Regen, firing a Seele Schneider, and Vollständig with its halo and Sklaverei.
 * The rules are {@link QuincyArtsLogic}'s. Every method takes a {@link Player}, so game tests can
 * drive them with mock players; titles, syncing and milestones only reach real connections.
 */
public final class QuincyArtsEvents {
    public static final ResourceLocation LETZT_SPEED_ID = BleachMod.id("letzt_stil_speed");
    public static final ResourceLocation VOLL_FLIGHT_ID = BleachMod.id("vollstandig_flight");
    public static final ResourceKey<DamageType> SKLAVEREI =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("sklaverei"));

    /** The same "never" {@link SoulData} uses for its transient clocks, far enough from the end to subtract from. */
    private static final long NEVER = Long.MIN_VALUE / 2;
    private static final String FORM_KEY = "key.bleach.quincy_form";

    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 50;
    private static final int TITLE_FADE_OUT = 20;

    private static final int FULL_SHOT_GLOVE_POINTS = 2;
    private static final int HOLLOW_HIT_GLOVE_POINTS = 1;
    private static final int DESTROYED_GLOVE_POINTS = 5;
    private static final float FLANK_DEGREES = 4.0F;
    /** 4a's shots leave just under the eyes. */
    private static final double SHOT_DROP = 0.1;

    /** Licht Regen's arrows rise from above and behind the shoulders, spread across this far each way. */
    private static final double LICHT_RISE = 0.6;
    private static final double LICHT_BACK = 0.4;
    private static final double LICHT_SPREAD = 1.2;

    private static final int RITE_MOTE_INTERVAL_TICKS = 10;
    private static final int RITE_MOTES = 3;
    private static final int LETZT_PARTICLES = 40;
    private static final int SEAL_PARTICLES = 16;
    private static final int VOLL_PARTICLES = 30;
    /** How fast Sklaverei's motes drift from what they were drawn from towards the user, as a share of the gap a tick. */
    private static final double SKLAVEREI_MOTE_SPEED = 0.04;
    private static final float HALO_BREAK_PITCH = 1.4F;
    private static final float STREAM_VOLUME = 0.4F;
    private static final float STREAM_PITCH = 1.4F;

    private QuincyArtsEvents() {
    }

    public static void register(IEventBus bus) {
        // Normal priority, beside the other armour and damage changes in QuincyEvents.
        bus.addListener(EventPriority.NORMAL, QuincyArtsEvents::onIncomingDamage);
        bus.addListener(QuincyArtsEvents::onDamagePost);
        bus.addListener(QuincyArtsEvents::onFall);
        bus.addListener(QuincyArtsEvents::onChangedDimension);
        bus.addListener(QuincyArtsEvents::onLoggedOut);
        bus.addListener(GritzEffect::onJump);
    }

    // ---- Every tick ----

    /**
     * Runs every tick for every living player, from {@link QuincyEvents#tick}: keeps the attribute
     * modifiers in step with the forms, burns a spent Letzt Stil out into the seal, pays for
     * Vollständig or ends it, pulses Sklaverei, clears a landing grace no longer needed, and gathers
     * and looses Licht Regen.
     */
    public static void tick(Player p, SoulData d, long now) {
        if (QuincyArtsLogic.arts(d).equals(QuincyArts.NONE) && d.lichtVolley == null && d.lichtGatherFrom == NEVER) {
            return;
        }
        if (!p.isAlive() || p.isRemoved()) {
            return;
        }
        keepLetztSpeed(p, d);
        boolean flight = hasFlight(p);
        if (QuincyArtsLogic.inVollstandig(d) && !flight) {
            // The form is saved, the flight is not: after a relog the form ends quietly (with a landing grace in the air).
            endVollstandig(p, d, now, true);
        } else if (!QuincyArtsLogic.inVollstandig(d) && flight) {
            removeFlight(p);
        }
        QuincyArts arts = QuincyArtsLogic.arts(d);
        if (arts.stage() == QuincyStage.LETZT_STIL && now >= arts.letztEndsAt()) {
            sealAt(p, d, now, Math.min(now, arts.letztEndsAt()));
        }
        if (QuincyArtsLogic.inVollstandig(d)) {
            tickVollstandig(p, d, now);
        }
        QuincyForms forms = QuincyArtsLogic.arts(d).forms();
        // On the ground with nothing fallen (a landing with a fall goes through onFall), in water, or flying again.
        if (forms.landingGrace() && ((p.onGround() && p.fallDistance <= 0.0F) || p.isInWater() || p.getAbilities().flying)) {
            setForms(d, forms.withLandingGrace(false));
        }
        tickLicht(p, d, now);
    }

    /** Ends Vollständig when it can no longer go on, pays a second of it, and pulses Sklaverei. */
    private static void tickVollstandig(Player p, SoulData d, long now) {
        String reason = null;
        boolean ends = false;
        if (!QuincyArtsLogic.isRestored(d) || !QuincyLogic.canUsePowers(d)) {
            ends = true;
            reason = "message.bleach.vollstandig.powerless";
        } else if (d.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
            ends = true;
            reason = "message.bleach.vollstandig.poisoned";
        } else if (p.isSleeping() || p.isSpectator()) {
            ends = true;
        }
        if (ends) {
            endVollstandig(p, d, now, true);
            if (reason != null) {
                tell(p, Component.translatable(reason));
            }
            return;
        }
        if (now % 20L == 0L && !QuincyLogic.drainReishi(d, QuincyArtsLogic.vollUpkeepPerSecond(d, p.getAbilities().flying))) {
            endVollstandig(p, d, now, true);
            tell(p, Component.translatable("message.bleach.vollstandig.dry"));
            return;
        }
        if (QuincyArtsLogic.sklavereiOn(d) && now % QuincyArtsLogic.SKLAVEREI_INTERVAL_TICKS == 0L) {
            sklavereiPulse(p, d);
        }
    }

    /**
     * Licht Regen: this tick's arrows of a volley already called, then the gather, which ends in the
     * volley once it has gathered long enough, or scatters when the Cross is no longer drawn. A
     * spectator, or a soul that cannot use its power, loses both.
     */
    private static void tickLicht(Player p, SoulData d, long now) {
        if (d.lichtVolley == null && !d.lichtGathering()) {
            return;
        }
        if (p.isSpectator() || !QuincyLogic.canUsePowers(d)) {
            clearLicht(d);
            return;
        }
        if (d.lichtVolley != null) {
            loose(p, d, d.lichtVolley);
        }
        if (d.lichtGathering()) {
            if (!drawingCross(p)) {
                d.lichtGatherFrom = NEVER;
                tell(p, Component.translatable("message.bleach.licht_regen.scattered"));
            } else if (now - d.lichtGatherFrom >= QuincyArtsLogic.LICHT_GATHER_TICKS) {
                fireLichtRegen(p, d, now);
            }
        }
    }

    // ---- Logging in and respawning ----

    /**
     * Runs on every refresh, from {@link QuincyEvents#onRefresh}: puts the saved clocks back in reach
     * of the game time, hands down a glove an old save is owed, ends a saved Vollständig whose flight
     * did not survive the relog, and burns out a Letzt Stil whose time ran out meanwhile.
     */
    public static void onRefresh(Player p, SoulData d, long now) {
        QuincyArts arts = QuincyArtsLogic.arts(d);
        QuincyArts normal = QuincyArtsLogic.normalise(arts, d.quincy().training(), now);
        if (!normal.equals(arts)) {
            QuincyArtsLogic.setArts(d, normal);
        }
        if (p.isAlive() && owedGlove(d)) {
            giveGlove(p, d);
        }
        if (QuincyArtsLogic.inVollstandig(d) && !hasFlight(p)) {
            endVollstandig(p, d, now, true);
        }
        QuincyArts after = QuincyArtsLogic.arts(d);
        if (after.stage() == QuincyStage.LETZT_STIL && now >= after.letztEndsAt()) {
            // Sealed from when it would have sealed, as a death during it is (see QuincyArtsLogic.afterDeath).
            sealAt(p, d, now, Math.min(now, after.letztEndsAt()));
        }
    }

    /** An awakened, living third-tier soul that was never handed its glove (a save from before the glove). */
    private static boolean owedGlove(SoulData d) {
        return QuincyLogic.isAwakened(d) && (d.state() == SoulState.HUMAN || d.state() == SoulState.SHINIGAMI)
                && QuincyLogic.tier(d) >= QuincyLogic.MAX_TIER && !QuincyArtsLogic.arts(d).glove().given();
    }

    // ---- The Heilig Bogen in each form ----

    /**
     * Each tick the Cross is drawn, before 4a's own handling (see {@link QuincyEvents#onBowHeld}). A
     * sealed soul holds the rite: weighed on the first tick, motes as it is held, and a cue once it
     * has been held long enough. Otherwise: the Letzt Stil charge cue, the unmastered glove's strain,
     * the Licht Regen gather, and Ginrei Kojaku's stream past a full draw.
     *
     * @return true when this handled the tick, so 4a's hold upkeep is skipped
     */
    public static boolean onBowHeld(Player p, SoulData d, int ticks) {
        long now = p.level().getGameTime();
        if (QuincyArtsLogic.isSealed(d)) {
            holdRite(p, d, ticks, now);
            return true;
        }
        if (!QuincyLogic.canUsePowers(d)) {
            return false;
        }
        int draw = QuincyArtsLogic.drawTicks(d);
        if (QuincyArtsLogic.inLetzt(d) && ticks == draw + QuincyArtsLogic.LETZT_CHARGE_EXTRA_TICKS) {
            SpiritSenses.play(p, ModSounds.QUINCY_CROSS_CHARGED.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        }
        if (QuincyArtsLogic.handicapped(d) && ticks > draw + QuincyArtsLogic.GLOVE_STRAIN_EXTRA_TICKS) {
            p.stopUsingItem();
            tell(p, Component.translatable("message.bleach.sanrei_glove.strain"));
            return true;
        }
        if (d.lichtGathering()) {
            // Gathering costs nothing and streams nothing.
            return true;
        }
        if (QuincyArtsLogic.isRestored(d) && ticks > draw && QuincyArtsLogic.streamAllowed(p, d)) {
            int start = draw + QuincyArtsLogic.STREAM_DELAY_TICKS;
            if (ticks >= start && (ticks - start) % QuincyArtsLogic.STREAM_INTERVAL_TICKS == 0) {
                stream(p, d);
            }
            // A held Ginrei Kojaku pays by the arrow, not by the tick.
            return true;
        }
        return false;
    }

    /** One tick of the solo rite: weighed on the first tick, motes as it is held, and a cue once it has been held long enough. */
    private static void holdRite(Player p, SoulData d, int ticks, long now) {
        if (ticks == 1) {
            String refusal = QuincyArtsLogic.riteRefusal(p, d, now);
            if (refusal != null) {
                p.stopUsingItem();
                tell(p, riteMessage(refusal, d, now));
                return;
            }
        }
        if (ticks % RITE_MOTE_INTERVAL_TICKS == 0 && p.level() instanceof ServerLevel level) {
            Vec3 hand = p.getEyePosition().add(p.getViewVector(1.0F).scale(0.5)).subtract(0.0, 0.3, 0.0);
            SpiritSenses.particles(level, ParticleTypes.END_ROD, hand.x, hand.y, hand.z, RITE_MOTES, 0.15, 0.15, 0.15, 0.01);
        }
        if (ticks == BleachConfig.get(BleachConfig.RESTORE_RITE_TICKS)) {
            SpiritSenses.play(p, ModSounds.QUINCY_CROSS_CHARGED.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        }
    }

    /** One arrow of Ginrei Kojaku's stream, paid for, or the bow comes apart when it cannot be. */
    private static void stream(Player p, SoulData d) {
        if (!QuincyLogic.trySpendReishi(d, (float) BleachConfig.get(BleachConfig.GINREI_STREAM_COST))) {
            p.stopUsingItem();
            tell(p, Component.translatable("message.bleach.heilig_bogen.collapsed"));
            return;
        }
        float damage = (float) (BleachConfig.get(BleachConfig.GINREI_STREAM_DAMAGE_FRACTION) * QuincyArtsLogic.boostedFullDamage(d));
        HeiligPfeil.launchVolley(p.level(), p, shotOrigin(p), p.getViewVector(1.0F), damage, QuincyArtsLogic.STREAM_SPEED,
                QuincyArtsLogic.STREAM_INACCURACY, BleachConfig.get(BleachConfig.PFEIL_LIFE_TICKS));
        SpiritSenses.play(p, ModSounds.HEILIG_PFEIL_FIRE.get(), SoundSource.PLAYERS, STREAM_VOLUME, STREAM_PITCH, false);
    }

    /**
     * The Cross was let go, before 4a fires anything (see {@link QuincyEvents#release}): the end of
     * the rite, a Licht Regen gather scattering, the end of a stream, or a Letzt Pfeil from a charged
     * Letzt Stil draw.
     *
     * @return true when this handled the release, so nothing more is fired
     */
    public static boolean onRelease(Player p, SoulData d, int ticks) {
        long now = p.level().getGameTime();
        if (QuincyArtsLogic.isSealed(d)) {
            if (ticks < BleachConfig.get(BleachConfig.RESTORE_RITE_TICKS)) {
                tell(p, Component.translatable("message.bleach.rite.broken"));
                return true;
            }
            String refusal = QuincyArtsLogic.riteRefusal(p, d, now);
            if (refusal == null) {
                restore(p, d, now, null);
            } else {
                tell(p, riteMessage(refusal, d, now));
            }
            return true;
        }
        if (!QuincyLogic.canUsePowers(d)) {
            return false;
        }
        if (d.lichtGathering()) {
            // Letting go mid-gather costs nothing.
            d.lichtGatherFrom = NEVER;
            tell(p, Component.translatable("message.bleach.licht_regen.scattered"));
            return true;
        }
        int draw = QuincyArtsLogic.drawTicks(d);
        // The held ticks seen count from 0, so the stream's first arrow has gone only after this many.
        if (QuincyArtsLogic.isRestored(d) && ticks > draw + QuincyArtsLogic.STREAM_DELAY_TICKS
                && QuincyArtsLogic.streamAllowed(p, d)) {
            return true;
        }
        if (QuincyArtsLogic.inLetzt(d) && ticks >= draw + QuincyArtsLogic.LETZT_CHARGE_EXTRA_TICKS
                && QuincyLogic.trySpendReishi(d, (float) BleachConfig.get(BleachConfig.LETZT_PFEIL_COST))) {
            fireLetztPfeil(p, d);
            return true;
        }
        return false;
    }

    /** A Letzt Pfeil, already paid for: three times a full draw, up to its cap, burning through what is in its way. */
    private static void fireLetztPfeil(Player p, SoulData d) {
        float damage = (float) Math.min(BleachConfig.get(BleachConfig.LETZT_PFEIL_MAX_DAMAGE),
                BleachConfig.get(BleachConfig.LETZT_PFEIL_DAMAGE_MULTIPLIER) * QuincyArtsLogic.boostedFullDamage(d));
        LetztPfeil.launch(p.level(), p, shotOrigin(p), p.getViewVector(1.0F), damage,
                BleachConfig.get(BleachConfig.LETZT_PFEIL_PIERCE), (float) BleachConfig.get(BleachConfig.LETZT_PFEIL_SPEED));
        SpiritSenses.play(p, ModSounds.HEILIG_PFEIL_FIRE.get(), SoundSource.PLAYERS, 1.0F, 0.7F, false);
        p.awardStat(Stats.ITEM_USED.get(ModItems.QUINCY_CROSS.get()));
    }

    /**
     * A restored Quincy's near-full draw with a Seele Schneider in the other hand fires the blade
     * instead of an arrow, for 2 reishi more than the shot already paid. Without the 2 reishi it is
     * a plain shot.
     *
     * @return true when the blade was fired instead
     */
    public static boolean fireSeele(Player p, SoulData d, float power) {
        Level level = p.level();
        if (level.isClientSide() || !QuincyArtsLogic.isRestored(d) || !QuincyLogic.canUsePowers(d)
                || power < QuincyLogic.PIERCE_POWER || !QuincyArtsLogic.holdsSeeleOpposite(p)) {
            return false;
        }
        if (!QuincyLogic.trySpendReishi(d, (float) BleachConfig.get(BleachConfig.SEELE_FIRED_COST))) {
            return false;
        }
        InteractionHand other = p.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack blade = p.getItemInHand(other);
        float damage = (float) (QuincyLogic.arrowDamage(d, power) * BleachConfig.get(BleachConfig.SEELE_FIRED_DAMAGE_MULTIPLIER));
        float speed = QuincyLogic.shotSpeed(power) * QuincyArtsLogic.shotSpeedMultiplier(d)
                * QuincyArtsLogic.SEELE_FIRED_SPEED_MULTIPLIER;
        SeeleSchneiderPfeil.launch(level, p, shotOrigin(p), p.getViewVector(1.0F), damage,
                QuincyLogic.pierceFor(d, power) + 1, speed);
        blade.consume(1, p);
        SpiritSenses.play(p, ModSounds.HEILIG_PFEIL_FIRE.get(), SoundSource.PLAYERS, 1.0F, 0.8F + 0.3F * power, false);
        p.awardStat(Stats.ITEM_USED.get(ModItems.QUINCY_CROSS.get()));
        return true;
    }

    /**
     * After 4a looses an arrow (see {@link QuincyEvents#fire}): a near-full draw with the mastered
     * glove adds two flank arrows, paid for together, at half the damage; one with the unmastered
     * glove counts towards the day's training.
     */
    public static void afterFire(Player p, SoulData d, float power, Vec3 from, Vec3 direction, float damage, float speed,
                                 float inaccuracy) {
        if (power < QuincyLogic.PIERCE_POWER || p.level().isClientSide()) {
            return;
        }
        if (QuincyArtsLogic.gloveMastered(d) && QuincyArtsLogic.stage(d) == QuincyStage.UNTOUCHED) {
            if (QuincyLogic.trySpendReishi(d, (float) BleachConfig.get(BleachConfig.SANREI_FLANK_COST))) {
                float flank = (float) (damage * BleachConfig.get(BleachConfig.SANREI_FLANK_DAMAGE_FRACTION));
                for (float side : new float[] {-FLANK_DEGREES, FLANK_DEGREES}) {
                    HeiligPfeil.launch(p.level(), p, from, direction.yRot((float) Math.toRadians(side)), power, flank, 0,
                            speed, inaccuracy);
                }
            }
        } else if (QuincyArtsLogic.handicapped(d)) {
            addGlovePoints(p, d, FULL_SHOT_GLOVE_POINTS);
        }
    }

    /** Just under the eyes, where 4a's shots leave from. */
    private static Vec3 shotOrigin(Player p) {
        return p.getEyePosition().subtract(0.0, SHOT_DROP, 0.0);
    }

    // ---- Training with the Sanrei Glove ----

    /** A Heilig Pfeil hit a Hollow-like creature, inside 4a's hit-training throttle: a point of glove training. */
    public static void onArrowHit(Player owner, SoulData d, LivingEntity target) {
        if (QuincyLogic.isHollowLike(target)) {
            addGlovePoints(owner, d, HOLLOW_HIT_GLOVE_POINTS);
        }
    }

    /** A soul destroyed by this Quincy: five points of glove training. */
    public static void onSoulDestroyed(Player p, SoulData d) {
        addGlovePoints(p, d, DESTROYED_GLOVE_POINTS);
    }

    /** A tier was reached in training (see {@link QuincyEvents#train}): crossing into the third hands down the glove. */
    public static void onTierReached(Player p, SoulData d, int before, int tier) {
        if (before < QuincyLogic.MAX_TIER && tier >= QuincyLogic.MAX_TIER && !QuincyArtsLogic.arts(d).glove().given()) {
            giveGlove(p, d);
        }
    }

    /**
     * Adds glove training while the unmastered glove is worn. The day's points reached count the day,
     * with a day's strain (Mining Fatigue) and a word in chat; enough days master the glove.
     */
    public static void addGlovePoints(Player p, SoulData d, int points) {
        QuincyArts arts = QuincyArtsLogic.arts(d);
        if (points <= 0 || arts.stage() != QuincyStage.UNTOUCHED || !QuincyArtsLogic.handicapped(d)) {
            return;
        }
        Level level = p.level();
        long day = QuincyArtsLogic.gloveDay(level.getGameTime(), level.getDayTime());
        QuincyArtsLogic.GloveGain gain = QuincyArtsLogic.addGlovePoints(arts.glove(), points, day);
        if (gain.glove().equals(arts.glove())) {
            return;
        }
        QuincyArtsLogic.setArts(d, arts.withGlove(gain.glove()));
        if (gain.dayCounted()) {
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, QuincyArtsLogic.GLOVE_FATIGUE_TICKS, 0));
            p.sendSystemMessage(Component.translatable("message.bleach.sanrei_glove.day", gain.glove().days(),
                    BleachConfig.get(BleachConfig.SANREI_DAYS_TO_MASTER)).withStyle(ChatFormatting.GRAY));
        }
        if (gain.masteredNow()) {
            title(p, "title.bleach.sanrei_mastered");
            p.sendSystemMessage(Component.translatable("message.bleach.sanrei_glove.mastered", Component.keybind(FORM_KEY))
                    .withStyle(ChatFormatting.AQUA));
            sync(p, d);
        }
    }

    /** Hands down a Sanrei Glove: into the inventory, or at the feet when it is full. */
    public static void giveGlove(Player p, SoulData d) {
        QuincyArts arts = QuincyArtsLogic.arts(d);
        QuincyArtsLogic.setArts(d, arts.withGlove(arts.glove().withGiven(true)));
        handOver(p, new ItemStack(ModItems.SANREI_GLOVE.get()));
        p.sendSystemMessage(Component.translatable("message.bleach.sanrei_glove.given").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Puts the glove on, if the soul can bear it. The glove becomes part of the soul and stays on
     * through death and logging out.
     *
     * @return null when it is now worn (the caller uses the item up), else the refusal's message key
     */
    @Nullable
    public static String wearGlove(Player p, SoulData d) {
        String refusal = QuincyArtsLogic.gloveRefusal(d);
        if (refusal != null) {
            return refusal;
        }
        QuincyArts arts = QuincyArtsLogic.arts(d);
        // A tailored glove counts as handed down, so the catch-up at login never hands down another.
        QuincyArtsLogic.setArts(d, arts.withGlove(arts.glove().withGiven(true).withWorn(true)));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
        p.sendSystemMessage(Component.translatable("message.bleach.sanrei_glove.put_on", Component.keybind(FORM_KEY))
                .withStyle(ChatFormatting.GRAY));
        sync(p, d);
        return null;
    }

    /** Takes the unmastered glove off: the item comes back and the training so far is kept. */
    public static void takeOffGlove(Player p, SoulData d) {
        QuincyArts arts = QuincyArtsLogic.arts(d);
        if (!arts.glove().worn()) {
            return;
        }
        QuincyArtsLogic.setArts(d, arts.withGlove(arts.glove().withWorn(false)));
        handOver(p, new ItemStack(ModItems.SANREI_GLOVE.get()));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 0.8F);
        tell(p, Component.translatable("message.bleach.sanrei_glove.removed"));
        sync(p, d);
    }

    private static void handOver(Player p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) {
            p.drop(stack, false);
        }
    }

    // ---- The Quincy Form key ----

    /**
     * The Quincy Form key (H), sneaking or not. Presses too close together are ignored; otherwise the
     * first rule that fits decides: Sklaverei (sneaking, in Vollständig), Licht Regen (drawing a
     * restored Cross), a word that Letzt Stil already burns, the unmastered glove off, the mastered
     * glove armed and torn, Vollständig ended or started, or a word on what is still to come.
     */
    public static void onFormKey(Player p, boolean sneak, long now) {
        if (!SoulLogic.hasSoul(p)) {
            return;
        }
        SoulData d = SoulLogic.get(p);
        if (now - d.lastFormPressAt < QuincyArtsLogic.FORM_DEBOUNCE_TICKS) {
            return;
        }
        d.lastFormPressAt = now;
        if (sneak && QuincyArtsLogic.inVollstandig(d)) {
            setSklaverei(p, d, !QuincyArtsLogic.sklavereiOn(d));
        } else if (!sneak && drawingCross(p) && QuincyArtsLogic.isRestored(d) && QuincyLogic.canUsePowers(d)) {
            startLichtRegen(p, d, now);
        } else if (QuincyArtsLogic.inLetzt(d)) {
            tell(p, Component.translatable("message.bleach.quincy_form.letzt"));
        } else if (QuincyArtsLogic.handicapped(d)) {
            takeOffGlove(p, d);
        } else if (QuincyArtsLogic.gloveMastered(d)) {
            if (now - d.formArmedAt <= QuincyArtsLogic.TEAR_WINDOW_TICKS) {
                d.formArmedAt = NEVER;
                startLetzt(p, d, now, false);
            } else {
                d.formArmedAt = now;
                tell(p, Component.translatable("message.bleach.letzt_stil.armed", Component.keybind(FORM_KEY)));
            }
        } else if (QuincyArtsLogic.inVollstandig(d)) {
            endVollstandig(p, d, now, false);
        } else if (QuincyArtsLogic.vollUnlocked(d)) {
            startVollstandig(p, d, now, false);
        } else if (QuincyArtsLogic.isSealed(d)) {
            tell(p, QuincyArtsLogic.sealedLongEnough(d, now, false)
                    ? Component.translatable("message.bleach.quincy_form.sealed_ready")
                    : Component.translatable("message.bleach.quincy_form.sealed",
                            QuincyArtsLogic.formatTicks(sealedTicksLeft(d, now))));
        } else if (QuincyArtsLogic.isRestored(d)) {
            tell(p, Component.translatable("message.bleach.quincy_form.locked", QuincyArtsLogic.vollTrainingLeft(d)));
        } else {
            tell(p, Component.translatable("message.bleach.quincy_form.nothing"));
        }
    }

    /**
     * Sneak-use of a filled Gintō: steps to the next spell the soul knows, in {@link GintoSpell}
     * order. A soul that knows none is told why.
     */
    public static void cycleSpell(Player p, SoulData d) {
        List<GintoSpell> known = QuincyArtsLogic.knownSpells(d);
        GintoSpell current = QuincyArtsLogic.selectedSpell(d);
        if (known.isEmpty()) {
            String refusal = QuincyArtsLogic.castRefusal(d, current);
            tell(p, Component.translatable(refusal != null ? refusal : "message.bleach.ginto.unknown", current.displayName()));
            return;
        }
        int at = known.indexOf(current);
        GintoSpell next = known.get((at + 1) % known.size());
        QuincyArts arts = QuincyArtsLogic.arts(d);
        QuincyArtsLogic.setArts(d, arts.withGinto(next));
        p.displayClientMessage(Component.translatable("message.bleach.ginto.selected", next.displayName())
                .withStyle(ChatFormatting.AQUA), true);
        sync(p, d);
    }

    // ---- Quincy: Letzt Stil and the seal ----

    /**
     * Tears the mastered glove off and burns the power out: Letzt Stil for its full time, with the
     * pool filled to its new size. Refused (with the reason on the action bar) unless forced, as the
     * command does.
     *
     * @return whether Letzt Stil started
     */
    public static boolean startLetzt(Player p, SoulData d, long now, boolean force) {
        if (!force) {
            String refusal = QuincyArtsLogic.letztRefusal(d);
            if (refusal != null) {
                tell(p, Component.translatable(refusal));
                return false;
            }
        }
        QuincyArts arts = QuincyArtsLogic.arts(d);
        boolean torn = arts.glove().worn();
        // Torn off for good; whether it was mastered stays on record.
        QuincyArtsLogic.setArts(d, arts.withGlove(arts.glove().withWorn(false)).withStage(QuincyStage.LETZT_STIL, now)
                .withLetztEndsAt(now + BleachConfig.get(BleachConfig.LETZT_DURATION_TICKS)));
        d.formArmedAt = NEVER;
        QuincyLogic.setReishi(d, QuincyLogic.maxReishi(d));
        keepLetztSpeed(p, d);
        if (torn) {
            // The glove is real cloth, so everyone hears it tear.
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SANREI_GLOVE_TEAR.get(), SoundSource.PLAYERS,
                    1.0F, 1.0F);
        }
        title(p, "title.bleach.letzt_stil");
        SpiritSenses.play(p, ModSounds.PLAYER_LETZT_STIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (p.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, ParticleTypes.END_ROD, p.getX(), p.getY(0.5), p.getZ(), LETZT_PARTICLES,
                    0.4, 0.8, 0.4, 0.15);
        }
        sync(p, d);
        return true;
    }

    /** Burns the power out now: from Letzt Stil, or (by command) straight from the untouched power. */
    public static void seal(Player p, SoulData d, long now) {
        sealAt(p, d, now, now);
    }

    /**
     * Seals the power, counting the seal from {@code since}: the reishi is gone, the speed with it, a
     * draw comes apart, and a released reiatsu that only the Quincy power held up is let go. Blut,
     * footing and gathering stop on their own next tick, as the power can no longer be used.
     */
    private static void sealAt(Player p, SoulData d, long now, long since) {
        if (QuincyArtsLogic.inVollstandig(d)) {
            endVollstandig(p, d, now, true);
        }
        QuincyArts arts = QuincyArtsLogic.arts(d);
        QuincyArtsLogic.setArts(d, arts.withStage(QuincyStage.SEALED, since));
        QuincyLogic.setReishi(d, 0.0F);
        removeModifier(p.getAttribute(Attributes.MOVEMENT_SPEED), LETZT_SPEED_ID);
        clearLicht(d);
        d.formArmedAt = NEVER;
        if (drawingCross(p)) {
            p.stopUsingItem();
        }
        if (p instanceof ServerPlayer serverPlayer && d.isReiatsuReleased() && !ShinigamiLogic.hasShinigamiPower(d)) {
            ReiatsuEvents.seal(serverPlayer, d);
        }
        title(p, "title.bleach.quincy_sealed");
        SpiritSenses.play(p, ModSounds.PLAYER_QUINCY_SEALED.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (p.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, ParticleTypes.ASH, p.getX(), p.getY(0.5), p.getZ(), SEAL_PARTICLES, 0.3, 0.6, 0.3, 0.02);
        }
        p.sendSystemMessage(Component.translatable("message.bleach.letzt_stil.sealed_now",
                QuincyArtsLogic.formatTicks(sealedTicksLeft(d, now))).withStyle(ChatFormatting.GRAY));
        sync(p, d);
    }

    /** How long until a sealed power can return by the rite. */
    private static long sealedTicksLeft(SoulData d, long now) {
        long since = QuincyArtsLogic.arts(d).stageSince();
        long elapsed = now <= since ? 0L : now - since;
        return Math.max(0L, QuincyArtsLogic.sealedTicksNeeded(false) - elapsed);
    }

    // ---- Restoration ----

    /**
     * The sealed power returns, changed: Ginrei Kojaku, with the pool full and the training counted
     * from here for Vollständig. The body pays for it with a while of Slowness and Weakness.
     *
     * @param by the Quincy whose arrow brought it back, told so, or null for the rite or a command
     */
    public static void restore(Player target, SoulData d, long now, @Nullable Player by) {
        QuincyArts arts = QuincyArtsLogic.arts(d);
        QuincyArtsLogic.setArts(d, arts.withStage(QuincyStage.RESTORED, now).withTrainingAtRestore(d.quincy().training()));
        QuincyLogic.setReishi(d, QuincyLogic.maxReishi(d));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, QuincyArtsLogic.RESTORE_EFFECT_TICKS, 0));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, QuincyArtsLogic.RESTORE_EFFECT_TICKS, 0));
        title(target, "title.bleach.quincy_restored");
        SpiritSenses.play(target, ModSounds.PLAYER_QUINCY_RESTORED.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (target.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, ParticleTypes.END_ROD, target.getX(), target.getY(0.5), target.getZ(), LETZT_PARTICLES,
                    0.4, 0.8, 0.4, 0.05);
        }
        target.sendSystemMessage(Component.translatable("message.bleach.quincy.restored", Component.keybind(FORM_KEY))
                .withStyle(ChatFormatting.AQUA));
        if (by != null && by != target) {
            by.sendSystemMessage(Component.translatable("message.bleach.quincy.restored_other", target.getDisplayName())
                    .withStyle(ChatFormatting.AQUA));
        }
        sync(target, d);
    }

    /**
     * Another Quincy's full-draw arrow found the place beside a sealed Quincy's heart (see
     * {@link HeiligPfeil#heartTarget}): brings the power back if everything still holds.
     *
     * @return whether the power came back, in which case the arrow is spent on it
     */
    public static boolean tryRestoreByArrow(Player owner, Player target, long now) {
        if (owner == target || !SoulLogic.hasSoul(target) || !QuincyArtsLogic.canRestoreOthers(owner)) {
            return false;
        }
        SoulData d = SoulLogic.get(target);
        if (!QuincyArtsLogic.restorable(target, d, now, true)) {
            return false;
        }
        restore(target, d, now, owner);
        return true;
    }

    // ---- Licht Regen ----

    /**
     * H while drawing a restored Cross: starts gathering Licht Regen, unless a gather already runs,
     * the last volley is not long enough ago, or the reishi is short.
     *
     * @return whether the gather started
     */
    public static boolean startLichtRegen(Player p, SoulData d, long now) {
        if (d.lichtGathering()) {
            return false;
        }
        long readyAt = QuincyArtsLogic.arts(d).forms().lichtRegenReadyAt();
        if (now < readyAt) {
            tell(p, Component.translatable("message.bleach.licht_regen.cooldown", QuincyArtsLogic.formatTicks(readyAt - now)));
            return false;
        }
        float cost = (float) BleachConfig.get(BleachConfig.LICHT_REGEN_COST);
        if (QuincyLogic.reishi(d) < cost) {
            tell(p, Component.translatable("message.bleach.licht_regen.reishi", Mth.ceil(cost)));
            return false;
        }
        d.lichtGatherFrom = now;
        p.displayClientMessage(Component.translatable("message.bleach.licht_regen.gathering").withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /**
     * The gather is complete: pays for the volley, picks what it falls on (the creature looked at, or
     * the place), lets the bow go and calls the volley, which the next ticks loose.
     */
    private static void fireLichtRegen(Player p, SoulData d, long now) {
        d.lichtGatherFrom = NEVER;
        float cost = (float) BleachConfig.get(BleachConfig.LICHT_REGEN_COST);
        if (!QuincyLogic.trySpendReishi(d, cost)) {
            tell(p, Component.translatable("message.bleach.licht_regen.reishi", Mth.ceil(cost)));
            return;
        }
        setForms(d, QuincyArtsLogic.arts(d).forms()
                .withLichtRegenReadyAt(now + BleachConfig.get(BleachConfig.LICHT_REGEN_COOLDOWN_TICKS)));
        HitResult hit = GintoSpells.look(p, QuincyArtsLogic.LICHT_RANGE);
        int targetId = -1;
        Vec3 point = p.getEyePosition().add(p.getViewVector(1.0F).scale(QuincyArtsLogic.LICHT_RANGE));
        if (hit instanceof EntityHitResult entityHit) {
            targetId = entityHit.getEntity().getId();
            point = entityHit.getEntity().getBoundingBox().getCenter();
        } else if (hit != null && hit.getType() != HitResult.Type.MISS) {
            point = hit.getLocation();
        }
        d.lichtVolley = new LichtVolley(BleachConfig.get(BleachConfig.LICHT_REGEN_ARROWS), targetId, point);
        p.stopUsingItem();
        SpiritSenses.play(p, ModSounds.LICHT_REGEN.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        milestone(p, Milestones.LICHT_REGEN);
        sync(p, d);
    }

    /**
     * Looses this tick's arrows of a volley, from just above and behind the shoulders, at the target
     * as it is now, or where it was last seen once it is gone.
     */
    private static void loose(Player p, SoulData d, LichtVolley volley) {
        Level level = p.level();
        Vec3 aim = volley.point();
        int targetId = volley.targetId();
        if (targetId >= 0) {
            Entity target = level.getEntity(targetId);
            if (target != null && target.isAlive()) {
                aim = target.getBoundingBox().getCenter();
            } else {
                targetId = -1;
            }
        }
        int count = Math.min(QuincyArtsLogic.LICHT_PER_TICK, volley.left());
        float damage = (float) (BleachConfig.get(BleachConfig.LICHT_REGEN_DAMAGE_FRACTION) * QuincyArtsLogic.boostedFullDamage(d));
        Vec3 eye = p.getEyePosition();
        double yaw = Math.toRadians(p.getYRot());
        Vec3 facing = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        for (int i = 0; i < count; i++) {
            double across = (p.getRandom().nextDouble() * 2.0 - 1.0) * LICHT_SPREAD;
            Vec3 origin = eye.add(0.0, LICHT_RISE, 0.0).subtract(facing.scale(LICHT_BACK)).add(left.scale(across));
            // Never from inside a wall or ceiling: then from the eyes.
            if (level.clip(new ClipContext(eye, origin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType()
                    != HitResult.Type.MISS) {
                origin = eye;
            }
            HeiligPfeil.launchVolley(level, p, origin, aim.subtract(origin), damage, QuincyArtsLogic.LICHT_SPEED,
                    QuincyArtsLogic.LICHT_INACCURACY, QuincyArtsLogic.LICHT_LIFE_TICKS);
        }
        int remaining = volley.left() - count;
        d.lichtVolley = remaining > 0 ? new LichtVolley(remaining, targetId, aim) : null;
    }

    private static void clearLicht(SoulData d) {
        d.lichtVolley = null;
        d.lichtGatherFrom = NEVER;
    }

    // ---- Quincy: Vollständig and Sklaverei ----

    /**
     * Takes the full form: wings, a halo and flight as in creative, for a reishi upkeep every second.
     * Refused (with the reason on the action bar) unless forced, as the command does, which also
     * skips the cost.
     *
     * @return whether Vollständig started
     */
    public static boolean startVollstandig(Player p, SoulData d, long now, boolean force) {
        AttributeInstance flight = p.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null) {
            return false;
        }
        if (!force) {
            String refusal = QuincyArtsLogic.vollRefusal(d, now);
            if (refusal != null) {
                tell(p, vollMessage(refusal, d, now));
                return false;
            }
            if (!QuincyLogic.trySpendReishi(d, (float) BleachConfig.get(BleachConfig.VOLL_START_COST))) {
                tell(p, vollMessage("message.bleach.vollstandig.reishi", d, now));
                return false;
            }
        }
        setForms(d, QuincyArtsLogic.arts(d).forms().withVollstandig(true).withSklaverei(false).withHaloBroken(false));
        if (!flight.hasModifier(VOLL_FLIGHT_ID)) {
            flight.addTransientModifier(new AttributeModifier(VOLL_FLIGHT_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
        title(p, "title.bleach.vollstandig");
        SpiritSenses.play(p, ModSounds.PLAYER_VOLLSTANDIG.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (p.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, ParticleTypes.END_ROD, p.getX(), p.getY(0.6), p.getZ(), VOLL_PARTICLES,
                    0.8, 0.6, 0.8, 0.05);
        }
        milestone(p, Milestones.VOLLSTANDIG);
        sync(p, d);
        return true;
    }

    /**
     * Lets the full form go: the flight goes, the form settles before it can be taken again, the
     * pool shrinks back (keeping what still fits), and a player left in the air is spared the
     * landing.
     *
     * @param quiet whether to say nothing (the caller has its own word, or none is wanted)
     */
    public static void endVollstandig(Player p, SoulData d, long now, boolean quiet) {
        removeFlight(p);
        QuincyForms forms = QuincyArtsLogic.arts(d).forms();
        if (!forms.vollstandig()) {
            return;
        }
        boolean airborne = !p.onGround() && !p.isInWater() && !p.getAbilities().mayfly;
        setForms(d, forms.withVollstandig(false).withSklaverei(false).withHaloBroken(false)
                .withVollReadyAt(now + BleachConfig.get(BleachConfig.VOLL_COOLDOWN_TICKS)).withLandingGrace(airborne));
        QuincyLogic.setReishi(d, QuincyLogic.reishi(d));
        if (!quiet) {
            tell(p, Component.translatable("message.bleach.vollstandig.ended"));
        }
        sync(p, d);
    }

    /**
     * Turns Sklaverei on or off, in Vollständig only. It cannot come on again once the halo has
     * broken in this form.
     *
     * @return whether Sklaverei is now as asked
     */
    public static boolean setSklaverei(Player p, SoulData d, boolean on) {
        QuincyForms forms = QuincyArtsLogic.arts(d).forms();
        if (on && !forms.vollstandig()) {
            return false;
        }
        if (on && forms.haloBroken()) {
            tell(p, Component.translatable("message.bleach.vollstandig.halo_broken"));
            return false;
        }
        if (forms.sklaverei() == on) {
            return true;
        }
        setForms(d, forms.withSklaverei(on));
        SpiritSenses.play(p, ModSounds.SKLAVEREI.get(), SoundSource.PLAYERS, 1.0F, on ? 1.0F : 0.7F, false);
        p.displayClientMessage(Component.translatable(on ? "message.bleach.sklaverei.on" : "message.bleach.sklaverei.off")
                .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
        if (on) {
            milestone(p, Milestones.SKLAVEREI);
        }
        sync(p, d);
        return true;
    }

    /**
     * One pulse of Sklaverei: the reishi of what is near is drawn in. Hollows are hurt by it (and
     * their reishi poisons), aware players lose reishi or reiryoku to it, and others' arrows and
     * Getsuga in the air come apart into it, up to a cap a pulse.
     */
    private static void sklavereiPulse(Player p, SoulData d) {
        if (!(p.level() instanceof ServerLevel level)) {
            return;
        }
        double radius = BleachConfig.get(BleachConfig.SKLAVEREI_RADIUS);
        double radiusSqr = radius * radius;
        float cap = (float) BleachConfig.get(BleachConfig.SKLAVEREI_MAX_GAIN_PER_PULSE);
        AABB box = p.getBoundingBox().inflate(radius);
        Vec3 chest = new Vec3(p.getX(), p.getY(0.6), p.getZ());
        List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && e.distanceToSqr(p) <= radiusSqr && sklavereiTarget(p, e)));
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        float gained = 0.0F;
        for (LivingEntity target : near.subList(0, Math.min(near.size(), QuincyArtsLogic.SKLAVEREI_MAX_TARGETS))) {
            if (gained >= cap) {
                break;
            }
            float got;
            if (QuincyLogic.isHollowLike(target)) {
                if (!target.hurt(sklavereiSource(level, p), (float) BleachConfig.get(BleachConfig.SKLAVEREI_HOLLOW_DAMAGE))) {
                    continue;
                }
                got = Math.min(1.0F, cap - gained);
                QuincyEvents.addPoison(p, d, (float) BleachConfig.get(BleachConfig.SKLAVEREI_POISON_PER_HOLLOW));
            } else if (target instanceof Player other) {
                got = drain(other, Math.min((float) BleachConfig.get(BleachConfig.SKLAVEREI_PLAYER_DRAIN), cap - gained));
            } else {
                continue;
            }
            if (got > 0.0F) {
                gained += got;
                drawIn(level, target.getBoundingBox().getCenter(), chest);
            }
        }
        float perProjectile = (float) BleachConfig.get(BleachConfig.SKLAVEREI_PROJECTILE_GAIN);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box,
                pr -> pr.isAlive() && pr.distanceToSqr(p) <= radiusSqr && absorbable(p, pr))) {
            if (gained >= cap) {
                break;
            }
            Vec3 at = projectile.position();
            projectile.discard();
            gained += Math.min(perProjectile, cap - gained);
            drawIn(level, at, chest);
        }
        if (gained > 0.0F) {
            QuincyLogic.setReishi(d, QuincyLogic.reishi(d) + gained);
        }
    }

    /** A Hollow-like creature, or an aware player who is neither Hollow-like nor someone the user may not harm. */
    private static boolean sklavereiTarget(Player p, LivingEntity e) {
        if (!SpiritProjectiles.mayStrike(p, e)) {
            return false;
        }
        if (QuincyLogic.isHollowLike(e)) {
            return true;
        }
        return e instanceof Player other && SoulLogic.hasSoul(other) && SoulLogic.seesSpirits(other);
    }

    /**
     * Draws up to {@code amount} from an aware player: their Quincy reishi if they hold any, else
     * their reiryoku, never below the floor. Written directly, so it never tires them out.
     *
     * @return how much was drawn
     */
    private static float drain(Player other, float amount) {
        if (!(amount > 0.0F)) {
            return 0.0F;
        }
        SoulData theirs = SoulLogic.get(other);
        float reishi = QuincyLogic.reishi(theirs);
        if (reishi > 0.0F) {
            float taken = Math.min(amount, reishi);
            QuincyLogic.setReishi(theirs, reishi - taken);
            return taken;
        }
        float taken = Math.min(amount, Math.max(0.0F, theirs.reiryoku() - QuincyArtsLogic.SKLAVEREI_REIRYOKU_FLOOR));
        if (taken > 0.0F) {
            theirs.setReiryoku(theirs.reiryoku() - taken);
        }
        return taken;
    }

    /**
     * Someone else's Heilig Pfeil (not a blade, which is a thing and stays), Letzt Pfeil or Getsuga
     * in the air, whose owner is gone, not a player, or a player the user may harm.
     */
    private static boolean absorbable(Player p, Projectile projectile) {
        boolean kind = projectile.getClass() == HeiligPfeil.class || projectile instanceof LetztPfeil
                || projectile instanceof GetsugaTensho;
        if (!kind) {
            return false;
        }
        Entity owner = projectile.getOwner();
        return owner != p && (!(owner instanceof Player player) || SpiritProjectiles.mayStrike(p, player));
    }

    /** A mote of reishi drifting from where it was drawn towards the user's chest, for the aware. */
    private static void drawIn(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 gap = to.subtract(from);
        // A count of 0 sends one particle moving along the given offset times the speed.
        SpiritSenses.particles(level, ParticleTypes.END_ROD, from.x, from.y, from.z, 0, gap.x, gap.y, gap.z, SKLAVEREI_MOTE_SPEED);
    }

    private static DamageSource sklavereiSource(ServerLevel level, Player user) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SKLAVEREI),
                user, user);
    }

    /** Breaks a Vollständig halo: Sklaverei ends and cannot come back in this form. */
    private static void breakHalo(Player p, SoulData d) {
        setForms(d, QuincyArtsLogic.arts(d).forms().withHaloBroken(true).withSklaverei(false));
        if (p.level() instanceof ServerLevel level) {
            SpiritSenses.play(level, p.getX(), p.getEyeY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F,
                    HALO_BREAK_PITCH, false);
        }
        tell(p, Component.translatable("message.bleach.vollstandig.halo_broken"));
        sync(p, d);
    }

    // ---- Ending everything ----

    /**
     * Ends every running art at once, for a reset: the modifiers come off, Vollständig ends (with a
     * landing grace in the air), Sklaverei stops, and Licht Regen and the tear's first tap are
     * forgotten. The stage itself is left as it is.
     */
    public static void endAll(Player p, SoulData d) {
        if (QuincyArtsLogic.inVollstandig(d)) {
            endVollstandig(p, d, p.level().getGameTime(), true);
        } else {
            removeFlight(p);
        }
        QuincyForms forms = QuincyArtsLogic.arts(d).forms();
        if (forms.sklaverei()) {
            setForms(d, forms.withSklaverei(false));
        }
        removeModifier(p.getAttribute(Attributes.MOVEMENT_SPEED), LETZT_SPEED_ID);
        clearLicht(d);
        d.formArmedAt = NEVER;
    }

    // ---- Events ----

    /** A formed Seele Schneider, swung or fired, cuts through half of the armour it meets. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof SeeleSchneiderPfeil || formedMelee(source) != null) {
            float kept = (float) (1.0 - BleachConfig.get(BleachConfig.SEELE_ARMOR_IGNORED));
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * kept);
        }
    }

    /**
     * Fires only for hits that got past shields and invulnerability frames. A formed Seele's cut
     * draws reishi back into the wielder, twice as much from a Hollow or a released reiatsu; a hard
     * hit, or anything that comes in at the head, breaks a Vollständig halo.
     */
    private static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        float dealt = event.getNewDamage();
        if (target.level().isClientSide() || dealt <= 0.0F) {
            return;
        }
        DamageSource source = event.getSource();
        Player wielder = formedMelee(source);
        if (wielder != null && wielder != target && SoulLogic.hasSoul(wielder)) {
            SoulData theirs = SoulLogic.get(wielder);
            boolean doubled = QuincyLogic.isHollowLike(target)
                    || (target instanceof Player player && SoulLogic.hasSoul(player) && SoulLogic.get(player).isReiatsuReleased());
            double back = Math.min(BleachConfig.get(BleachConfig.SEELE_DRAIN_MAX),
                    dealt * BleachConfig.get(BleachConfig.SEELE_DRAIN_FRACTION) * (doubled ? 2.0 : 1.0));
            QuincyLogic.setReishi(theirs, QuincyLogic.reishi(theirs) + (float) back);
        }
        if (target instanceof Player player && SoulLogic.hasSoul(player)) {
            SoulData d = SoulLogic.get(player);
            QuincyForms forms = QuincyArtsLogic.arts(d).forms();
            if (forms.vollstandig() && !forms.haloBroken()) {
                boolean heavy = dealt >= BleachConfig.get(BleachConfig.VOLL_HALO_BREAK_FRACTION) * player.getMaxHealth();
                boolean atTheHead = source.getDirectEntity() instanceof Projectile projectile
                        && projectile.getY() >= player.getEyeY() - QuincyArtsLogic.HALO_HEAD_MARGIN;
                if (heavy || atTheHead) {
                    breakHalo(player, d);
                }
            }
        }
    }

    /**
     * The player who swung a formed Seele Schneider for this hit, or null. Only a melee swing counts,
     * not Sklaverei or thorns, which also come straight from the player.
     */
    @Nullable
    private static Player formedMelee(DamageSource source) {
        return source.is(DamageTypeTags.IS_PLAYER_ATTACK) && source.getDirectEntity() instanceof Player attacker
                && source.getEntity() == attacker && SeeleSchneiderItem.isFormed(source.getWeaponItem()) ? attacker : null;
    }

    /**
     * No fall damage for a landing still spared after Vollständig (once), in Vollständig itself (for
     * a flier Gritz brings down), or onto a Wolke (the cloud also stops the fall itself).
     */
    private static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof Player player && SoulLogic.hasSoul(player)) {
            SoulData d = SoulLogic.get(player);
            QuincyForms forms = QuincyArtsLogic.arts(d).forms();
            if (forms.landingGrace()) {
                event.setDamageMultiplier(0.0F);
                setForms(d, forms.withLandingGrace(false));
                return;
            }
            if (forms.vollstandig()) {
                event.setDamageMultiplier(0.0F);
                return;
            }
        }
        double reach = BleachConfig.get(BleachConfig.WOLKE_RADIUS) + 1.0;
        if (!entity.level().getEntitiesOfClass(GintoCast.class, entity.getBoundingBox().inflate(reach, 4.0, reach),
                cast -> cast.cushions(entity)).isEmpty()) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /** Licht Regen does not follow its caster between dimensions. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (SoulLogic.hasSoul(player)) {
            clearLicht(SoulLogic.get(player));
        }
    }

    /**
     * Sklaverei and Licht Regen end with the session. Vollständig is saved, and ends quietly at the
     * next login, when its flight is found missing.
     */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData d = SoulLogic.get(player);
        QuincyForms forms = QuincyArtsLogic.arts(d).forms();
        if (forms.sklaverei()) {
            setForms(d, forms.withSklaverei(false));
        }
        clearLicht(d);
        d.formArmedAt = NEVER;
    }

    // ---- Helpers ----

    /** Keeps Letzt Stil's speed on the player exactly while it burns, at its current share. */
    private static void keepLetztSpeed(Player p, SoulData d) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(LETZT_SPEED_ID);
        if (!QuincyArtsLogic.inLetzt(d)) {
            if (current != null) {
                speed.removeModifier(LETZT_SPEED_ID);
            }
            return;
        }
        double bonus = QuincyArtsLogic.movementBonus(d);
        // Only when it changes: replacing a modifier marks the attribute for syncing.
        if (current == null || current.amount() != bonus) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(LETZT_SPEED_ID, bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static boolean hasFlight(Player p) {
        AttributeInstance flight = p.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        return flight != null && flight.hasModifier(VOLL_FLIGHT_ID);
    }

    /**
     * Takes Vollständig's flight away. NeoForge grounds a server player whose flight is gone on its
     * next tick; this grounds them now, so a mock player is grounded too.
     */
    private static void removeFlight(Player p) {
        AttributeInstance flight = p.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null && flight.removeModifier(VOLL_FLIGHT_ID) && p.getAbilities().flying && !p.mayFly()) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
    }

    private static void removeModifier(@Nullable AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }

    private static void setForms(SoulData d, QuincyForms forms) {
        QuincyArts arts = QuincyArtsLogic.arts(d);
        if (!forms.equals(arts.forms())) {
            QuincyArtsLogic.setArts(d, arts.withForms(forms));
        }
    }

    /** Whether the player is drawing a Quincy Cross right now. */
    private static boolean drawingCross(Player p) {
        return p.isUsingItem() && QuincyCrossItem.is(p.getUseItem());
    }

    /** A rite refusal's message, with the time still to wait when it is too soon. */
    private static MutableComponent riteMessage(String key, SoulData d, long now) {
        return "message.bleach.rite.too_soon".equals(key)
                ? Component.translatable(key, QuincyArtsLogic.formatTicks(sealedTicksLeft(d, now)))
                : Component.translatable(key);
    }

    /** A Vollständig refusal's message, with what it is short of. */
    private static MutableComponent vollMessage(String key, SoulData d, long now) {
        return switch (key) {
            case "message.bleach.vollstandig.locked" -> Component.translatable(key, QuincyArtsLogic.vollTrainingLeft(d));
            case "message.bleach.vollstandig.cooldown" -> Component.translatable(key,
                    QuincyArtsLogic.formatTicks(QuincyArtsLogic.arts(d).forms().vollReadyAt() - now));
            case "message.bleach.vollstandig.reishi" -> Component.translatable(key,
                    Mth.ceil(Math.max(BleachConfig.get(BleachConfig.VOLL_START_MIN_REISHI),
                            BleachConfig.get(BleachConfig.VOLL_START_COST))));
            default -> Component.translatable(key);
        };
    }

    /** A grey line on the action bar, as 4a's refusals are. */
    private static void tell(Player p, MutableComponent message) {
        p.displayClientMessage(message.withStyle(ChatFormatting.GRAY), true);
    }

    /** A title and its subtitle ({@code key.subtitle}), shown as 4a shows a new tier. */
    private static void title(Player p, String key) {
        if (p instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(key + ".subtitle")));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(key)));
        }
    }

    /** Sends the soul at once, for a change the player should see straight away. */
    private static void sync(Player p, SoulData d) {
        if (p instanceof ServerPlayer serverPlayer) {
            BleachNetwork.syncSoul(serverPlayer, d);
        }
    }

    private static void milestone(Player p, String milestone) {
        if (p instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, milestone);
        }
    }
}
