package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.QuincyElder;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.BleachTags;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.joml.Vector3f;

/**
 * Ransōtengai: threads of reishi woven through a failing body, moving its limbs from above like a
 * puppet's. While woven, slowness, fatigue and weakness are shaken off and kept off (reiatsu
 * pressure included), the body cannot be knocked back and nothing heals it, and the weave costs a
 * share of the maximum reiryoku every second, which does not refill meanwhile. Letting it go, or
 * running dry, leaves the body slowed and weakened, with a wait before the threads hold again;
 * slipping out of it by leaving the world brings the wait without the collapse.
 * A Quincy Elder teaches it in a trial: bound, the soul must weave the threads unbroken for a few
 * seconds. Sneak and press the Quincy Form key to weave it (see {@link QuincyArtsEvents#onFormKey}).
 * Server side; every method takes a {@link Player}, so game tests can drive it with mock players.
 */
public final class QuincyRansotengai {
    /** The hold against knockback, on both kinds: from blows and arrows, and from blasts. */
    public static final ResourceLocation KNOCKBACK_ID = BleachMod.id("ransotengai_knockback");
    public static final List<Holder<Attribute>> HELD_AGAINST = List.of(Attributes.KNOCKBACK_RESISTANCE,
            Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
    /** The effects the threads shake off and keep off: slowness, mining fatigue and weakness. */
    public static final TagKey<MobEffect> FREES = BleachTags.RANSOTENGAI_FREES;
    public static final int PARTICLE_INTERVAL = 4, DRAIN_INTERVAL = 20;

    private static final long NEVER = ShinigamiProgress.NEVER;
    private static final String FORM_KEY = "key.bleach.quincy_form";
    private static final String REFUSED = "message.bleach.ransotengai.refused.";
    private static final int TICKS_PER_SECOND = 20;
    /** What is left of the pool below this counts as dry, as for any technique paid by the tick. */
    private static final float DRY_BELOW = 0.5F;
    /** The trial binds the body this hard: Slowness IV and Weakness II. */
    private static final int TRIAL_SLOWNESS = 3, TRIAL_WEAKNESS = 1;
    /**
     * The binding is laid on this long at a time, and laid on again once it has half that left, for
     * as long as the trial is open and the threads are not woven; never past the trial's end.
     */
    public static final int BIND_TICKS = 40;
    /** The trial stays open at least this much longer than the threads must hold in it: 5 seconds. */
    private static final long TRIAL_SPARE_TICKS = 100L;
    /** The collapse after the threads let go: Slowness II and Weakness II. */
    private static final int COLLAPSE_SLOWNESS = 1, COLLAPSE_WEAKNESS = 1;
    /** How far from the soul passing the trial an Elder still notices it, and cuts his visit short. */
    private static final double ELDER_NOTICES = 32.0;

    // The threads: pale blue dust on four lines from above the head down to the hands and feet.
    private static final DustParticleOptions THREAD = new DustParticleOptions(new Vector3f(0.75F, 0.9F, 1.0F), 0.6F);
    private static final int THREAD_POINTS = 6;
    private static final double THREAD_RISE = 1.2;
    private static final double HAND_SPREAD = 0.4, HAND_HEIGHT = 0.45;
    private static final double FOOT_SPREAD = 0.15, FOOT_HEIGHT = 0.1;

    private QuincyRansotengai() {
    }

    /** Why the threads let go, and whether the body pays for it with the collapse and the wait. */
    public enum End {
        PRESSED, DRY, SEALED, POISON, VOLLSTANDIG, MODE, DEATH, LOGOUT, TRIAL_PASSED, COMMAND;

        /** Let go by the soul or torn away from it: the collapse and the wait follow. */
        public boolean collapses() {
            return switch (this) {
                case PRESSED, DRY, SEALED, POISON, VOLLSTANDIG -> true;
                default -> false;
            };
        }

        /**
         * Whether the wait follows: after a collapse, and after leaving the world woven, so the threads
         * cannot be let go for free by logging out and back in (unless the soul left dead, see
         * {@link #end}).
         */
        public boolean waits() {
            return collapses() || this == LOGOUT;
        }
    }

    public static void register(IEventBus bus) {
        bus.addListener(QuincyRansotengai::onEffectApplicable);
        bus.addListener(QuincyRansotengai::onHeal);
    }

    /** Slowness, fatigue and weakness cannot take hold of a woven body. */
    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect != null && effect.getEffect().is(FREES) && woven(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * Nothing heals a woven body: it is the threads that move it. A Totem of Undying still saves it,
     * as it sets the health rather than healing. Natural regeneration does not even start, so it
     * burns no food for nothing (see {@code FoodDataMixin}).
     */
    private static void onHeal(LivingHealEvent event) {
        if (woven(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ---- Queries ----

    /** Whether this is a player whose body the threads move now. Server side; false on the client. */
    public static boolean woven(LivingEntity entity) {
        return !entity.level().isClientSide() && entity instanceof Player player && SoulLogic.hasSoul(player)
                && isWoven(SoulLogic.get(player));
    }

    public static boolean isWoven(SoulData d) {
        return QuincyArtsLogic.arts(d).forms().ransotengaiSince() != NEVER;
    }

    public static boolean isKnown(SoulData d) {
        return d.quincy().teaching().ransotengaiKnown();
    }

    /** Whether the Elder's trial is open: the soul is bound and must weave the threads to pass. */
    public static boolean trialOpen(SoulData d, long now) {
        return d.quincy().teaching().trialUntil() > now;
    }

    /** A body at the end of its strength: health this low, or slowed, fatigued or weakened. */
    public static boolean failing(LivingEntity entity, double healthFraction) {
        if (entity.getHealth() <= healthFraction * entity.getMaxHealth()) {
            return true;
        }
        for (Holder<MobEffect> effect : entity.getActiveEffectsMap().keySet()) {
            if (effect.is(FREES)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether sneak and the Quincy Form key mean Ransōtengai for this soul now: to let the threads go,
     * to weave them in the trial, or to weave them once learned when the body is failing. Otherwise
     * the key does what it always did.
     */
    public static boolean claimsKey(Player p, SoulData d, long now) {
        return isWoven(d) || trialOpen(d, now)
                || (isKnown(d) && failing(p, BleachConfig.get(BleachConfig.RANSOTENGAI_FAILING_HEALTH)));
    }

    /**
     * Why the threads cannot be woven now, as a {@code message.bleach.ransotengai.refused.*} key, or
     * null if they can: in creative or spectator mode ({@code mode}), a power that cannot be used
     * ({@code sealed}), Hollow poison ({@code poison}), Vollständig ({@code vollstandig}), the wait
     * after a collapse ({@code cooldown}, which takes the time left; waived in the trial) or too
     * little reiryoku ({@code reiryoku}). {@code force}, for the command, skips the wait.
     */
    @Nullable
    public static String startRefusal(Player p, SoulData d, long now, boolean force) {
        String early = bodyRefusal(p, d);
        if (early != null) {
            return early;
        }
        if (!force && !trialOpen(d, now) && now < QuincyArtsLogic.arts(d).forms().ransotengaiReadyAt()) {
            return REFUSED + "cooldown";
        }
        return reiryokuRefusal(d);
    }

    /** Why the Elder's trial cannot be taken now: as {@link #startRefusal}, but the wait never counts. */
    @Nullable
    public static String trialRefusal(Player p, SoulData d, long now) {
        String early = bodyRefusal(p, d);
        return early != null ? early : reiryokuRefusal(d);
    }

    /** The mode, the power, the poison and Vollständig, in that order. */
    @Nullable
    private static String bodyRefusal(Player p, SoulData d) {
        if (p.isCreative() || p.isSpectator()) {
            return REFUSED + "mode";
        }
        if (!QuincyLogic.canUsePowers(d)) {
            return REFUSED + "sealed";
        }
        if (d.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
            return REFUSED + "poison";
        }
        if (QuincyArtsLogic.inVollstandig(d)) {
            return REFUSED + "vollstandig";
        }
        return null;
    }

    @Nullable
    private static String reiryokuRefusal(SoulData d) {
        double least = BleachConfig.get(BleachConfig.RANSOTENGAI_MIN_REIRYOKU) * d.maxReiryoku();
        return d.reiryoku() < least ? REFUSED + "reiryoku" : null;
    }

    /** What a second of the weave costs: a share of the maximum reiryoku, more for a hybrid. */
    public static float drainPerSecond(SoulData d) {
        double cost = d.maxReiryoku() * BleachConfig.get(BleachConfig.RANSOTENGAI_DRAIN_PERCENT) / 100.0;
        if (QuincyArtsLogic.isHybrid(d)) {
            cost *= BleachConfig.get(BleachConfig.RANSOTENGAI_HYBRID_DRAIN);
        }
        return (float) Math.max(0.0, cost);
    }

    /** The wait after a collapse before the threads hold again, in ticks. */
    static long cooldownTicks() {
        return Math.max(0L, (long) (BleachConfig.get(BleachConfig.RANSOTENGAI_COOLDOWN_SECONDS) * (long) TICKS_PER_SECOND));
    }

    private static int collapseTicks() {
        return Math.max(0, (int) (BleachConfig.get(BleachConfig.RANSOTENGAI_COLLAPSE_SECONDS) * TICKS_PER_SECOND));
    }

    /**
     * How long the Elder's trial stays open: as set, but always somewhat longer than the threads must
     * hold, so that a soul who weaves them at once can pass it, whatever the settings.
     */
    public static int trialTicks() {
        long set = Math.max(1L, (long) BleachConfig.get(BleachConfig.RANSOTENGAI_TRIAL_SECONDS) * TICKS_PER_SECOND);
        return (int) Math.max(set, trialHoldTicks() + TRIAL_SPARE_TICKS);
    }

    private static long trialHoldTicks() {
        return Math.max(1L, (long) (BleachConfig.get(BleachConfig.RANSOTENGAI_TRIAL_HOLD_SECONDS) * (long) TICKS_PER_SECOND));
    }

    // ---- Actions ----

    /**
     * Weaves the threads: the effects they free from come off at once, the body holds against
     * knockback, and the weave is paid for from the next second. Only for a soul that knows the art
     * or is in the Elder's trial, unless forced, as the command does; refused (with the reason on the
     * action bar) as {@link #startRefusal} says.
     *
     * @return whether the threads are now woven
     */
    public static boolean start(Player p, SoulData d, long now, boolean force) {
        if (p.level().isClientSide() || !p.isAlive() || isWoven(d)) {
            return false;
        }
        if (!force && !isKnown(d) && !trialOpen(d, now)) {
            return false;
        }
        String refusal = startRefusal(p, d, now, force);
        if (refusal != null) {
            tell(p, refusalMessage(refusal, d, now));
            return false;
        }
        setForms(d, QuincyArtsLogic.arts(d).forms().withRansotengaiSince(now));
        for (Holder<Attribute> attribute : HELD_AGAINST) {
            AttributeInstance held = p.getAttribute(attribute);
            if (held != null && !held.hasModifier(KNOCKBACK_ID)) {
                held.addTransientModifier(new AttributeModifier(KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        free(p);
        p.displayClientMessage(Component.translatable("message.bleach.ransotengai.woven").withStyle(ChatFormatting.AQUA), true);
        if (p.level() instanceof ServerLevel level) {
            threads(level, p);
        }
        sync(p, d);
        return true;
    }

    /**
     * Lets the threads go. The hold against knockback comes off and the weave is marked ended first,
     * so the collapse can take hold: Slowness II and Weakness II for a while, and a wait before the
     * threads will hold again, for every end the body pays for (see {@link End#collapses}). The rest
     * end quietly, though leaving the world still brings the wait (see {@link End#waits}). Nothing
     * happens for a soul that is not woven, beyond making sure the hold is off.
     */
    public static void end(Player p, SoulData d, long now, End why) {
        removeKnockback(p);
        QuincyForms forms = QuincyArtsLogic.arts(d).forms();
        if (forms.ransotengaiSince() == NEVER) {
            return;
        }
        QuincyForms ended = forms.withRansotengaiSince(NEVER);
        // A soul that leaves the world dead lost the threads to its death, which brings no wait.
        if (why.waits() && (why != End.LOGOUT || p.isAlive())) {
            ended = ended.withRansotengaiReadyAt(now + cooldownTicks());
        }
        setForms(d, ended);
        if (why.collapses()) {
            int ticks = collapseTicks();
            if (ticks > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, COLLAPSE_SLOWNESS));
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, COLLAPSE_WEAKNESS));
            }
            tell(p, Component.translatable(why == End.DRY ? "message.bleach.ransotengai.dry" : "message.bleach.ransotengai.collapse"));
        } else if (why == End.MODE || why == End.COMMAND) {
            tell(p, Component.translatable("message.bleach.ransotengai.ended"));
        }
        // A soul logging out is saved as it is; nothing can be sent to it any more.
        if (why != End.LOGOUT) {
            sync(p, d);
        }
    }

    /**
     * Runs every tick for every living player, from {@link QuincyArtsEvents#tick}: closes a trial
     * that ran out unwoven, keeps the binding on while the trial is open and the threads are not
     * woven, and keeps a weave going or ends it. A woven body sheds what it is freed
     * from, passes the trial once it has held long enough, pays a second of the weave every twenty
     * ticks (running dry ends it, except in the Elder's trial, where the threads hold on what is
     * left), and shows its threads to those who see spirits. A weave found
     * without its hold on the body (after a crash, say) is let go as a logout lets it go: no
     * collapse, but the wait.
     */
    public static void tick(Player p, SoulData d, long now) {
        if (p.level().isClientSide()) {
            return;
        }
        QuincyTeaching teaching = d.quincy().teaching();
        if (teaching.trialUntil() != NEVER && now >= teaching.trialUntil() && !isWoven(d)) {
            closeTrial(p, d);
            p.sendSystemMessage(Component.translatable("message.bleach.ransotengai.trial_failed").withStyle(ChatFormatting.GRAY));
            sync(p, d);
        }
        if (!isWoven(d)) {
            // A soul reset or replaced while woven leaves the hold behind; it goes with the weave.
            removeKnockback(p);
            if (trialOpen(d, now) && p.isAlive()) {
                bind(p, d.quincy().teaching().trialUntil() - now);
            }
            return;
        }
        if (!p.isAlive() || p.isRemoved()) {
            return;
        }
        if (!holdsAgainstKnockback(p)) {
            end(p, d, now, End.LOGOUT);
            // Unlike a real logout, the soul is still here to be told.
            sync(p, d);
            return;
        }
        End ends = ends(p, d);
        if (ends != null) {
            end(p, d, now, ends);
            return;
        }
        free(p);
        long woven = now - QuincyArtsLogic.arts(d).forms().ransotengaiSince();
        if (trialOpen(d, now) && woven >= trialHoldTicks()) {
            passTrial(p, d, now);
            return;
        }
        // On whole seconds of the weave, timed off game time, which stands still under /tick freeze
        // while players still tick.
        boolean running = p.level().tickRateManager().runsNormally();
        if (running && woven > 0L && woven % DRAIN_INTERVAL == 0L) {
            float left = d.reiryoku() - drainPerSecond(d);
            if (left < DRY_BELOW && trialOpen(d, now)) {
                // In the trial the threads hold on what is left, so a hold no pool could pay for can still pass.
                d.setReiryoku(Math.max(0.0F, left));
            } else if (left < DRY_BELOW) {
                // Paid here rather than through SoulLogic.drain, whose exhaustion the threads would
                // only shed: the collapse stands in for it.
                d.setReiryoku(0.0F);
                end(p, d, now, End.DRY);
                return;
            }
            d.setReiryoku(left);
        }
        if (running && woven % PARTICLE_INTERVAL == 0L && p.level() instanceof ServerLevel level) {
            threads(level, p);
        }
    }

    /** What tears a weave away now, if anything: the mode, a power that cannot be used, the poison or Vollständig. */
    @Nullable
    private static End ends(Player p, SoulData d) {
        if (p.isCreative() || p.isSpectator()) {
            return End.MODE;
        }
        if (!QuincyLogic.canUsePowers(d)) {
            return End.SEALED;
        }
        if (d.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
            return End.POISON;
        }
        // Starting Vollständig ends the weave itself; this only catches a form taken some other way.
        return QuincyArtsLogic.inVollstandig(d) ? End.VOLLSTANDIG : null;
    }

    /**
     * The Elder's trial, from his TEACH offer: the soul is bound (Slowness IV and Weakness II, for as
     * long as the trial lasts and the threads are not woven; see {@link #tick}) and must free itself
     * by weaving the threads unbroken for a few seconds before it closes. Not for a soul that already
     * knows the art; refused (with the reason on the action bar) as {@link #trialRefusal} says.
     */
    public static void beginTrial(Player p, SoulData d, long now) {
        if (p.level().isClientSide() || isKnown(d)) {
            return;
        }
        String refusal = trialRefusal(p, d, now);
        if (refusal != null) {
            tell(p, Component.translatable(refusal));
            return;
        }
        int ticks = trialTicks();
        d.setQuincy(d.quincy().withTeaching(d.quincy().teaching().withTrialUntil(now + ticks)));
        bind(p, ticks);
        p.sendSystemMessage(Component.translatable("message.bleach.ransotengai.trial_open", Component.keybind(FORM_KEY))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        sync(p, d);
    }

    /**
     * The threads held long enough in the trial: the art is learned and the trial closes, and the
     * weave lets go with no collapse and no wait. An Elder close by has nothing more to teach today.
     */
    private static void passTrial(Player p, SoulData d, long now) {
        QuincyTeaching teaching = d.quincy().teaching();
        d.setQuincy(d.quincy().withTeaching(teaching.withRansotengaiKnown(true).withTrialUntil(NEVER)));
        end(p, d, now, End.TRIAL_PASSED);
        p.sendSystemMessage(Component.translatable("message.bleach.ransotengai.learned").withStyle(ChatFormatting.AQUA));
        if (p instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.RANSOTENGAI);
        }
        if (p.level() instanceof ServerLevel level) {
            QuincyElder elder = QuincyNpcEvents.elderNear(level, p.position(), ELDER_NOTICES);
            if (elder != null && !elder.isPermanent()) {
                elder.setLifetime(Math.min(elder.lifetime(), QuincyNpcLogic.DONE_LIFETIME));
            }
        }
    }

    /**
     * Closes the Elder's trial unpassed, if it is open, and takes its binding off with it: a trial
     * that ran out while the soul was away, or that a command or a reset cut short, leaves no binding
     * that no threads could shake off any more.
     */
    public static void closeTrial(Player p, SoulData d) {
        QuincyTeaching teaching = d.quincy().teaching();
        if (teaching.trialUntil() == NEVER) {
            return;
        }
        d.setQuincy(d.quincy().withTeaching(teaching.withTrialUntil(NEVER)));
        unbind(p, MobEffects.MOVEMENT_SLOWDOWN, TRIAL_SLOWNESS);
        unbind(p, MobEffects.WEAKNESS, TRIAL_WEAKNESS);
    }

    /**
     * Puts a saved trial's end back in reach of the game time, as {@link QuincyArtsLogic#normalise}
     * does the forms' waits: a soul brought from a world whose clock ran further would otherwise stay
     * in the trial, and bound, until this world's clock caught up.
     */
    public static void normaliseTrial(SoulData d, long now) {
        QuincyTeaching teaching = d.quincy().teaching();
        long latest = now + trialTicks();
        if (teaching.trialUntil() != NEVER && teaching.trialUntil() > latest) {
            d.setQuincy(d.quincy().withTeaching(teaching.withTrialUntil(latest)));
        }
    }

    // ---- Helpers ----

    /** Lays the trial's binding on again where it is wearing thin, for no longer than {@code left} ticks. */
    private static void bind(Player p, long left) {
        int ticks = (int) Math.min(BIND_TICKS, left);
        if (ticks <= 0) {
            return;
        }
        bind(p, MobEffects.MOVEMENT_SLOWDOWN, TRIAL_SLOWNESS, ticks);
        bind(p, MobEffects.WEAKNESS, TRIAL_WEAKNESS, ticks);
    }

    private static void bind(Player p, Holder<MobEffect> effect, int amplifier, int ticks) {
        MobEffectInstance on = p.getEffect(effect);
        if (on == null || on.getAmplifier() < amplifier
                || (on.getAmplifier() == amplifier && on.getDuration() < Math.min(ticks, BIND_TICKS / 2))) {
            p.addEffect(new MobEffectInstance(effect, ticks, amplifier));
        }
    }

    /** Takes off the binding's own effect: as strong as it binds, and no longer than it is laid on. */
    private static void unbind(Player p, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance on = p.getEffect(effect);
        if (on != null && on.getAmplifier() == amplifier && on.getDuration() <= BIND_TICKS) {
            p.removeEffect(effect);
        }
    }

    /** Whether the hold against both kinds of knockback is on the body. */
    public static boolean holdsAgainstKnockback(Player p) {
        for (Holder<Attribute> attribute : HELD_AGAINST) {
            AttributeInstance held = p.getAttribute(attribute);
            if (held != null && !held.hasModifier(KNOCKBACK_ID)) {
                return false;
            }
        }
        return true;
    }

    /** Takes off every effect the threads free from. */
    private static void free(Player p) {
        List<Holder<MobEffect>> freed = new ArrayList<>();
        for (Holder<MobEffect> effect : p.getActiveEffectsMap().keySet()) {
            if (effect.is(FREES)) {
                freed.add(effect);
            }
        }
        // Gathered first: removing an effect changes the map being read.
        for (Holder<MobEffect> effect : freed) {
            p.removeEffect(effect);
        }
    }

    private static void removeKnockback(Player p) {
        for (Holder<Attribute> attribute : HELD_AGAINST) {
            AttributeInstance held = p.getAttribute(attribute);
            if (held != null && held.hasModifier(KNOCKBACK_ID)) {
                held.removeModifier(KNOCKBACK_ID);
            }
        }
    }

    /**
     * A few motes of the threads, on lines from above the head down to each hand and foot, facing the
     * way the body does. Only those who see spirits see them.
     */
    private static void threads(ServerLevel level, Player p) {
        RandomSource random = p.getRandom();
        double yaw = Math.toRadians(p.yBodyRot);
        Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        Vec3 feet = p.position();
        double height = p.getBbHeight();
        Vec3[] limbs = {
                feet.add(left.scale(HAND_SPREAD)).add(0.0, HAND_HEIGHT * height, 0.0),
                feet.add(left.scale(-HAND_SPREAD)).add(0.0, HAND_HEIGHT * height, 0.0),
                feet.add(left.scale(FOOT_SPREAD)).add(0.0, FOOT_HEIGHT, 0.0),
                feet.add(left.scale(-FOOT_SPREAD)).add(0.0, FOOT_HEIGHT, 0.0)
        };
        for (int i = 0; i < THREAD_POINTS; i++) {
            Vec3 limb = limbs[random.nextInt(limbs.length)];
            // Each line hangs from straight above its limb, a little closer in.
            Vec3 top = new Vec3((feet.x + limb.x) / 2.0, feet.y + height + THREAD_RISE, (feet.z + limb.z) / 2.0);
            Vec3 at = top.lerp(limb, random.nextDouble());
            SpiritSenses.particles(level, THREAD, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void setForms(SoulData d, QuincyForms forms) {
        QuincyArts arts = QuincyArtsLogic.arts(d);
        if (!forms.equals(arts.forms())) {
            QuincyArtsLogic.setArts(d, arts.withForms(forms));
        }
    }

    /** A start refusal's message, with the time still to wait after a collapse. */
    private static MutableComponent refusalMessage(String key, SoulData d, long now) {
        return (REFUSED + "cooldown").equals(key)
                ? Component.translatable(key, QuincyArtsLogic.formatTicks(QuincyArtsLogic.arts(d).forms().ransotengaiReadyAt() - now))
                : Component.translatable(key);
    }

    /** A grey line on the action bar, as the other arts' refusals are. */
    private static void tell(Player p, MutableComponent message) {
        p.displayClientMessage(message.withStyle(ChatFormatting.GRAY), true);
    }

    private static void sync(Player p, SoulData d) {
        if (p instanceof ServerPlayer serverPlayer) {
            BleachNetwork.syncSoul(serverPlayer, d);
        }
    }
}
