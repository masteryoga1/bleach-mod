package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Kaidō, channelled on the Kidō key: while it is held, a pulse a second heals the patient chosen at the press, its
 * reiryoku first if it is below half, then its body by Kubo's four tiers, or from Kidō 40 the healer's own wounds. Each
 * pulse that does something is paid, trains Kidō and rests the healer's own reiryoku for a second. The channel is a
 * transient in the soul; everything takes a Player, so game tests can use mock players.
 */
public final class KidoHeal {
    /** Ticks between pulses: one a second. */
    public static final int PULSE_TICKS = 20;
    /** The Kidō level from which a healer with no one in reach heals its own wounds. */
    public static final int SELF_LEVEL = 40;
    /** The share of the tier's healing a healer gives its own wounds. */
    public static final float SELF_SHARE = 0.5F;
    /** Below this share of its maximum, a patient's reiryoku is refilled before its body is healed. */
    public static final float REFILL_BELOW = 0.5F;
    /** How many times a pulse's cost a refill gives, at the patient's own rate of regeneration. */
    public static final float REFILL_FACTOR = 2.0F;
    /** How much further than the spell's reach a patient may stand once chosen, in blocks. */
    public static final double REACH_SLACK = 0.5;
    /** What the healer reads as it begins on another: the spell's title. */
    public static final String BEGUN_KEY = "message.bleach.kaido.begun";
    /** What the healer reads as it begins on its own wounds: the spell's title. */
    public static final String BEGUN_SELF_KEY = "message.bleach.kaido.begun.self";
    /** What the healer reads when its patient is lost. */
    public static final String LOST_KEY = "message.bleach.kaido.lost";
    /** What the healer reads when a blow, bound arms or another art breaks the heal. */
    public static final String BROKEN_KEY = "message.bleach.kaido.broken";
    /** Ticks between the palms' motes, how far below the hand they glow, and their size. */
    private static final int PALM_TICKS = 4;
    private static final double PALM_BELOW = 0.4;
    private static final float MOTE_SIZE = 0.8F;
    /** How loud the spell's sound is at each pulse that does something. */
    private static final float PULSE_VOLUME = 0.5F;

    private KidoHeal() {
    }

    /**
     * Kubo's four levels of Kaidō, chosen by the healer's Kidō level: how much each pulse heals, the share of maximum
     * health it heals up to, and the effects it lifts; each lifts only venom and withering, never the penalties the mod
     * lays on purpose (Slowness, Weakness, Mining Fatigue, Nausea).
     */
    public enum Tier {
        /** Stopping bleeding, from Kidō 3: half a heart a second, up to half health. */
        STOP_BLEEDING(3, 1.0F, 0.5F, List.of()),
        /** Relieving pain, from Kidō 12: a heart a second, up to three quarters, and Poison lifted. */
        RELIEVE_PAIN(12, 2.0F, 0.75F, List.of(MobEffects.POISON)),
        /** Healing wounds, from Kidō 25: a heart and a half a second, up to full health, and Poison lifted. */
        HEAL_INJURIES(25, 3.0F, 1.0F, List.of(MobEffects.POISON)),
        /** Restoring organs, from Kidō 40: two hearts a second, up to full health, and Poison and Wither lifted. */
        RESTORE_ORGANS(40, 4.0F, 1.0F, List.of(MobEffects.POISON, MobEffects.WITHER));

        private final int from;
        private final float perSecond;
        private final float upTo;
        private final List<Holder<MobEffect>> clears;

        Tier(int from, float perSecond, float upTo, List<Holder<MobEffect>> clears) {
            this.from = from;
            this.perSecond = perSecond;
            this.upTo = upTo;
            this.clears = clears;
        }

        /** The Kidō level it is reached at. */
        public int from() {
            return from;
        }

        /** The health a pulse heals. */
        public float perSecond() {
            return perSecond;
        }

        /** The share of maximum health it heals up to. */
        public float upTo() {
            return upTo;
        }

        /** The effects each pulse lifts. */
        public List<Holder<MobEffect>> clears() {
            return clears;
        }
    }

    /** The heal a soul channels: its spell, its patient (null: the healer itself) and when its next pulse comes. */
    public record Channel(KidoSpell spell, @Nullable LivingEntity patient, long nextPulseAt) {
    }

    // ---- The channel ----

    /**
     * Begins this heal on this patient, or on the healer itself when null, checked as a cast by name is, and refused to
     * a soul in Jinzen and with no one to heal. A press refused only because the hands are busy for at most a pulse
     * more begins anyway, its first pulse when they are free; else the first pulse comes at once. Tells the begun line
     * but not a refusal, which it returns (the caller tells it), or null. Public for tests.
     */
    @Nullable
    public static KidoLogic.Refusal begin(Player healer, @Nullable LivingEntity patient, KidoSpell spell, long now) {
        SoulData data = SoulLogic.get(healer);
        KidoLogic.Refusal refusal = KidoEvents.check(healer, data, spell, KidoLogic.Form.NAME, now, true);
        long firstPulseAt = now;
        // A press within the second begins the heal, its first pulse when the hands are free; a longer wait is refused.
        if (refusal == KidoLogic.Refusal.COOLDOWN && data.kidoReadyAt - now <= PULSE_TICKS) {
            firstPulseAt = data.kidoReadyAt;
            refusal = KidoEvents.check(healer, data, spell, KidoLogic.Form.NAME, now, false);
        }
        if (refusal == null && data.jinzen() != null) {
            refusal = KidoLogic.Refusal.GATHERING;
        }
        if (refusal == null && (patient == null ? !maySelfHeal(healer, data) : !keeps(healer, patient, spell))) {
            refusal = KidoLogic.Refusal.NO_PATIENT;
        }
        if (refusal != null) {
            return refusal;
        }
        data.kidoHeal = new Channel(spell, patient, firstPulseAt);
        healer.swing(InteractionHand.MAIN_HAND, true);
        healer.displayClientMessage(Component.translatable(patient == null ? BEGUN_SELF_KEY : BEGUN_KEY, spell.title())
                .withStyle(ChatFormatting.GRAY), true);
        if (firstPulseAt <= now) {
            pulse(healer, data, now);
        }
        return null;
    }

    /**
     * A healing soul's tick: dropped silently once it may not cast by name, its Kidō falls below the spell's or it may
     * no longer heal itself; broken by another art gathering or Jinzen; lost with its patient. Else the palms glow,
     * and a pulse comes when it is due.
     */
    static void tick(Player healer, SoulData data, long now) {
        Channel channel = data.kidoHeal;
        if (channel == null) {
            return;
        }
        KidoSpell spell = channel.spell();
        // The gather's rule: what stops every cast drops the heal without a word.
        if (KidoLogic.mayCast(healer, data, now, KidoLogic.Form.NAME) != null || data.level(Stat.KIDO) < spell.level()
                || channel.patient() == null && !maySelfHeal(healer, data)) {
            data.kidoHeal = null;
            return;
        }
        if (KidoLogic.otherArtGathering(data) || data.jinzen() != null) {
            stop(healer, data, BROKEN_KEY);
            return;
        }
        if (channel.patient() != null && !keeps(healer, channel.patient(), spell)) {
            stop(healer, data, LOST_KEY);
            return;
        }
        if (now % PALM_TICKS == 0 && healer.level() instanceof ServerLevel level) {
            palms(level, healer, spell);
        }
        if (now >= channel.nextPulseAt()) {
            pulse(healer, data, now);
        }
    }

    /** Ends the heal, telling this line in grey if there is one; does nothing without a heal. */
    static void stop(Player healer, SoulData data, @Nullable String line) {
        if (data.kidoHeal == null) {
            return;
        }
        data.kidoHeal = null;
        if (line != null) {
            healer.displayClientMessage(Component.translatable(line).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * One pulse: ends the heal, told, when its cost would leave less than {@link KidoLogic#MIN_LEFT}; else the next is
     * due a pulse from now, and one that mends ({@link #mends}) rests the healer's reiryoku, is paid, busies the hands,
     * trains Kidō and is seen and heard. Creative pays, waits and trains nothing.
     */
    private static void pulse(Player healer, SoulData data, long now) {
        Channel channel = data.kidoHeal;
        KidoSpell spell = channel.spell();
        float cost = KidoLogic.cost(spell, data);
        boolean creative = healer.isCreative();
        if (!creative && data.reiryoku() - cost < KidoLogic.MIN_LEFT) {
            data.kidoHeal = null;
            KidoEvents.tell(healer, KidoLogic.Refusal.TOO_WEAK, spell);
            return;
        }
        data.kidoHeal = new Channel(spell, channel.patient(), now + PULSE_TICKS);
        LivingEntity patient = channel.patient() == null ? healer : channel.patient();
        if (!mends(patient, channel.patient() == null, data.level(Stat.KIDO), cost, now)) {
            return;
        }
        // The healer's own reiryoku rests for the second after, so tapping the key gains nothing over holding it.
        data.kidoHealRestUntil = now + PULSE_TICKS;
        if (!creative) {
            SoulLogic.trySpend(healer, data, cost);
            data.kidoReadyAt = now + spell.cooldownTicks();
            SoulLogic.addStatXp(healer, data, Stat.KIDO, spell.xp());
        }
        look(healer, patient, spell);
    }

    // ---- The rules ----

    /**
     * Mends this patient once at this Kidō level: refills its reiryoku when {@link #refillFor} gives any, and then
     * nothing more; else heals its body by the level's tier and lifts the tier's effects. Returns whether anything
     * changed. A heal or a removal a listener refuses changes nothing.
     */
    public static boolean mends(LivingEntity patient, boolean self, int kidoLevel, float cost, long now) {
        float refill = self ? 0.0F : refillFor(patient, cost, now);
        if (refill > 0.0F) {
            SoulData soul = SoulLogic.get(patient);
            soul.setReiryoku(soul.reiryoku() + refill);
            return true;
        }
        Tier tier = tier(kidoLevel);
        boolean changed = false;
        float amount = healFor(tier, self, patient.getHealth(), patient.getMaxHealth());
        if (amount > 0.0F) {
            float before = patient.getHealth();
            patient.heal(amount);
            changed = patient.getHealth() > before;
        }
        for (Holder<MobEffect> effect : tier.clears()) {
            // A listener may refuse the removal, and the event is posted even for an effect that is not there.
            if (patient.hasEffect(effect) && patient.removeEffect(effect)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * The reiryoku a pulse of this cost gives this patient: none without a soul, at half its maximum or more, or for a
     * player whose own reiryoku may not refill now; else twice the cost at its own rate of regeneration, never above
     * the normal rate. Changes nothing.
     */
    public static float refillFor(LivingEntity patient, float cost, long now) {
        if (!SoulLogic.hasSoul(patient)) {
            return 0.0F;
        }
        SoulData soul = SoulLogic.get(patient);
        if (soul.reiryoku() >= REFILL_BELOW * soul.maxReiryoku()
                || patient instanceof Player player && !SoulEvents.mayRegenerate(player, soul, now)) {
            return 0.0F;
        }
        // No faster than its own regeneration: lent power, Hollow poison and hunger slow it; a garb's bonus does not.
        return (float) (REFILL_FACTOR * cost * Math.min(1.0, ShinigamiLogic.regenMultiplier(patient, soul, now)));
    }

    /**
     * The health a pulse of this tier heals a body at this health of this maximum: its rate (half for oneself), never
     * past the tier's share of the maximum, and 0 there or above.
     */
    public static float healFor(Tier tier, boolean self, float health, float maxHealth) {
        float room = tier.upTo() * maxHealth - health;
        return room <= 0.0F ? 0.0F : Math.min(tier.perSecond() * (self ? SELF_SHARE : 1.0F), room);
    }

    /** The highest tier reached at this Kidō level, and {@link Tier#STOP_BLEEDING} below every other. */
    public static Tier tier(int kidoLevel) {
        Tier found = Tier.STOP_BLEEDING;
        for (Tier tier : Tier.values()) {
            if (kidoLevel >= tier.from()) {
                found = tier;
            }
        }
        return found;
    }

    /**
     * The patient a press finds: the first creature along the healer's look that it may heal and whose eyes it can see,
     * within the spell's reach ({@link KidoEvents#aim}), or null. Changes nothing.
     */
    @Nullable
    public static LivingEntity patientFor(Player healer, KidoSpell spell) {
        // Passing one whose eyes are out of sight, which the channel could not keep (keeps), so the self-heal may apply.
        return KidoEvents.aim(healer, spell, body -> mayHeal(healer, body) && healer.hasLineOfSight(body)).target();
    }

    /**
     * Whether this healer may heal this creature: anyone alive but itself, a spectator, an armour stand, a Visored's
     * inner Hollow and a body Negación or a Caja holds; and no one while the healer, or a player patient, fights its
     * Hollow.
     */
    public static boolean mayHeal(Player healer, LivingEntity patient) {
        return patient != healer && patient.isAlive() && !patient.isSpectator() && !(patient instanceof ArmorStand)
                && !(patient instanceof InnerHollow) && !Negacion.held(patient) && !VisoredEvents.isDuelling(healer)
                && !(patient instanceof Player host && VisoredEvents.isDuelling(host));
    }

    /**
     * Whether a heal keeps this patient: still in the healer's world, one it may heal, its box widened by the aim's
     * margin within the reach and {@link #REACH_SLACK} of the eyes, and in sight. The healer need not look at it.
     */
    public static boolean keeps(Player healer, LivingEntity patient, KidoSpell spell) {
        double reach = spell.effect().range() + REACH_SLACK;
        return !patient.isRemoved() && patient.level() == healer.level() && mayHeal(healer, patient)
                && patient.getBoundingBox().inflate(spell.effect().radius()).distanceToSqr(healer.getEyePosition())
                <= reach * reach
                && healer.hasLineOfSight(patient);
    }

    /** Whether this healer may heal itself: from {@link #SELF_LEVEL}, and not while it fights its Hollow. */
    private static boolean maySelfHeal(Player healer, SoulData data) {
        return data.level(Stat.KIDO) >= SELF_LEVEL && !VisoredEvents.isDuelling(healer);
    }

    /** The heal this soul is channelling, or null. Server only (the field is transient). */
    @Nullable
    public static Channel channel(SoulData data) {
        return data.kidoHeal;
    }

    // ---- The look ----

    /** A green glow before the healer's hands, for those who see spirits: two motes in the spell's colour. */
    private static void palms(ServerLevel level, Player healer, KidoSpell spell) {
        Vec3 at = CeroBeam.muzzleOf(healer, 1.0F).subtract(0.0, PALM_BELOW, 0.0);
        SpiritSenses.particles(level, new DustParticleOptions(Vec3.fromRGB24(spell.look().colour()).toVector3f(),
                MOTE_SIZE), at.x, at.y, at.z, 2, 0.15, 0.05, 0.15, 0.0);
    }

    /** A pulse that mended, for those who see spirits: the spell's particle and quiet sound at the patient's middle. */
    private static void look(Player healer, LivingEntity patient, KidoSpell spell) {
        if (!(healer.level() instanceof ServerLevel level)) {
            return;
        }
        KidoSpell.Look look = spell.look();
        double y = patient.getY(0.5);
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                patient.getX(), y, patient.getZ(), sound, SoundSource.PLAYERS, PULSE_VOLUME, look.pitch(), false));
        SimpleParticleType particle = KidoEvents.particleOf(look);
        if (particle != null) {
            double width = patient.getBbWidth();
            SpiritSenses.particles(level, particle, patient.getX(), y, patient.getZ(), 4, width / 2.0, 0.3, width / 2.0,
                    0.0);
        }
    }
}
