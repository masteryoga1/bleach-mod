package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.network.KidoKeyPayload;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Casting Kidō. The Kidō key (U) casts the chosen spell by name alone, Eishōhaki in canon's terms, and Sneak and the
 * key choose the next spell the soul's Kidō level allows; the action bar names it, and the chat shows its incantation
 * if it has one. A cast by name is one whole act within a key press, and a chant goes off after its chant time: it is
 * paid for, the hands are busy for the spell's cooldown, the spell's shape strikes, the caster calls it out on its own
 * action bar and Kidō trains, one xp more for a strike. The rules are in {@link KidoLogic}.
 *
 * <p>A soul that may cast says a spell's whole incantation in chat ({@link #chant}). The line is not sent to chat: it
 * is spoken aloud, to the caster and to those nearby who see spirits, and the spell goes off at full strength once its
 * chant time has passed, aimed where the caster looks then ({@link #tick}). A creature's blow that lands meanwhile
 * breaks the chant, and nothing is spent. A chanted cast trains twice the Kidō. Cast by name alone instead, a spell
 * with an incantation may burst in its caster's hand, until its caster has mastered it.
 *
 * <p>A Hadō of {@link KidoSpell.Shape#BEAM} strikes through the Cero's engine as a Bala does ({@link CeroBeam#fire}
 * with a quiet {@link CeroBeam.Kind} of {@link #HADO}), so it keeps every rule a Cero keeps on PvP, teams, pets,
 * shields and the reiatsu gap, and it breaks no blocks. Its trace, sound and particles are seen and heard only by
 * those who see spirits; what it does to a creature anyone can see. Creative pays nothing, waits for nothing, never
 * misfires and trains nothing. One art at a time: no Kidō while a Getsuga Tenshō or a Cero gathers, and one that
 * begins to gather breaks a chant.
 *
 * <p>The keys come from {@link KidoKeyPayload} and the chant from chat ({@link #register}); the cooldown, the pending
 * chant and the spacing between spoken chants are transients in the soul, so a respawn or a relog starts them over.
 * Everything takes a {@link Player}, so game tests can use mock players.
 */
public final class KidoEvents {
    public static final ResourceKey<DamageType> HADO = ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("hado"));
    /** Ticks a spell's trace stays, fading. */
    public static final int TRACE_TICKS = 6;
    /** Ticks after one spoken chant before another is spoken: a matched line sooner is hushed. */
    public static final int CHANT_SPACING_TICKS = 20;
    private static final int HAND_PARTICLES = 6;
    private static final int IMPACT_PARTICLES = 10;
    private static final double HAND_SPREAD = 0.08;
    private static final double IMPACT_SPREAD = 0.25;
    private static final double PARTICLE_SPEED = 0.02;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float MISFIRE_VOLUME = 0.8F;
    private static final float MISFIRE_PITCH = 1.4F;
    private static final int MISFIRE_SMOKE = 12;
    private static final double MISFIRE_SMOKE_SPREAD = 0.15;
    private static final int MISFIRE_PARTICLES = 8;
    private static final double MISFIRE_SPREAD = 0.2;

    private KidoEvents() {
    }

    /**
     * What a chat line was heard as, which decides what happens to it: the outcome, the spell it was heard as (empty
     * for plain chat), and why the spell would not answer, for a refused one.
     */
    public record Heard(Outcome outcome, Optional<ResourceLocation> spell, @Nullable KidoLogic.Refusal refusal) {
        /** What becomes of the line. */
        public enum Outcome {
            /** Not a chant, or not one this soul can say: the line stays plain chat. */
            PLAIN,
            /** Heard, spoken and begun. */
            STARTED,
            /** Heard and spoken, and the spell would not answer: the refusal is told. */
            REFUSED,
            /** Heard within a second of the last: cancelled, not spoken, and its sender alone told to wait. */
            HUSHED
        }

        /** A line left as chat. */
        public static final Heard PLAIN_LINE = new Heard(Outcome.PLAIN, Optional.empty(), null);

        /** Whether the line is kept from chat. */
        public boolean cancels() {
            return outcome != Outcome.PLAIN;
        }

        /** Whether the line is spoken aloud. */
        public boolean spoken() {
            return outcome == Outcome.STARTED || outcome == Outcome.REFUSED;
        }
    }

    /** The chat listener, the pending chant's tick, the blow that breaks it and the dimension change that drops it. */
    public static void register(IEventBus bus) {
        bus.addListener(KidoEvents::onChat);
        bus.addListener(KidoEvents::onPlayerTick);
        bus.addListener(KidoEvents::onDamageTaken);
        bus.addListener(KidoEvents::onChangedDimension);
    }

    // ---- The key ----

    /**
     * The Kidō key, as sent by {@link KidoKeyPayload} (and called directly by game tests): PRESS casts the chosen
     * spell by name, NEXT chooses the next known one; RELEASE, CANCEL and SELECT do nothing yet. A held soul is told
     * so by its holder and does nothing more. Tells the player why on the action bar. Returns the refusal, or null
     * when done or ignored.
     */
    @Nullable
    public static KidoLogic.Refusal onKey(Player player, KidoKeyPayload.KidoKey key, Optional<ResourceLocation> spell,
                                          long now) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        if (Negacion.refused(player)) {
            return KidoLogic.Refusal.UNABLE;
        }
        SoulData data = SoulLogic.get(player);
        return switch (key) {
            case PRESS -> press(player, data, level, now);
            case NEXT -> next(player, data, level, now);
            // RELEASE and CANCEL wait for a spell that is held (6c's Raikōhō), SELECT for the Kidō page (6f).
            default -> null;
        };
    }

    /** The key alone: the chosen spell, cast by name, or why not, told. */
    @Nullable
    private static KidoLogic.Refusal press(Player player, SoulData data, ServerLevel level, long now) {
        KidoLogic.Refusal refusal = KidoLogic.mayCast(player, data, now);
        if (refusal != null) {
            tell(player, refusal, null);
            return refusal;
        }
        Optional<Holder.Reference<KidoSpell>> chosen = KidoSpell.registry(level)
                .flatMap(spells -> KidoLogic.chosen(data, spells));
        if (chosen.isEmpty()) {
            tell(player, KidoLogic.Refusal.NONE_KNOWN, null);
            return KidoLogic.Refusal.NONE_KNOWN;
        }
        KidoSpell spell = chosen.get().value();
        refusal = cast(player, spell, KidoLogic.Form.NAME, now);
        if (refusal != null) {
            tell(player, refusal, spell);
        }
        return refusal;
    }

    /**
     * Sneak and the key: the next known spell is chosen and named, with the level it can be cast by name from when
     * that is still to come, and its incantation, if it has one, is shown in the player's own chat. A soul kept from
     * casting only by its Hollow or its lent power may still choose, and so may one that is chanting.
     */
    @Nullable
    private static KidoLogic.Refusal next(Player player, SoulData data, ServerLevel level, long now) {
        KidoLogic.Refusal refusal = KidoLogic.mayCast(player, data, now);
        if (!KidoLogic.mayChoose(refusal)) {
            tell(player, refusal, null);
            return refusal;
        }
        Optional<Holder.Reference<KidoSpell>> following = KidoSpell.registry(level)
                .flatMap(spells -> KidoLogic.next(data, spells));
        if (following.isEmpty()) {
            tell(player, KidoLogic.Refusal.NONE_KNOWN, null);
            return KidoLogic.Refusal.NONE_KNOWN;
        }
        KidoLogic.choose(data, following.get().key().location());
        KidoSpell spell = following.get().value();
        MutableComponent line = KidoLogic.gate(spell, KidoLogic.Form.NAME, data.level(Stat.KIDO))
                == KidoLogic.Refusal.NEEDS_NAME_LEVEL
                ? Component.translatable("message.bleach.kido.chosen.by_name_later", spell.title(), spell.byNameLevel())
                : Component.translatable("message.bleach.kido.chosen", spell.title());
        player.displayClientMessage(line.withStyle(ChatFormatting.GRAY), true);
        // Only where a chant can be heard: with chat casting off, the lines would promise what the server ignores.
        if (spell.chantable() && BleachConfig.get(BleachConfig.KIDO_CHAT_CASTING)) {
            printIncantations(player, spell);
        }
        return null;
    }

    // ---- The chant ----

    /**
     * A line said in chat, heard as a chant if it is one: begun, with the line to be spoken, or refused out loud; or
     * left as chat. A line stays chat with chat casting off, from a soul that cannot cast Kidō at all (dead, a
     * spectator, held, untrained, or its body held by its Hollow), and when it is not one whole incantation of a spell.
     * One heard within {@link #CHANT_SPACING_TICKS} of the soul's last spoken chant is hushed, its sender alone told
     * to wait. Otherwise the line is spoken and the cast is checked as it will be when it goes off, but for the
     * cooldown: a chant begun while the hands are still busy goes off once they are free. Spends nothing. Called by the
     * chat listener and directly by game tests.
     */
    public static Heard chant(Player player, String text, long now) {
        if (!BleachConfig.get(BleachConfig.KIDO_CHAT_CASTING) || !(player.level() instanceof ServerLevel level)) {
            return Heard.PLAIN_LINE;
        }
        SoulData data = SoulLogic.get(player);
        KidoLogic.Refusal may = KidoLogic.mayCast(player, data, now, KidoLogic.Form.CHANT);
        // A soul that cannot cast Kidō at all is only saying the words, as a Hollow would.
        if (may == KidoLogic.Refusal.UNABLE || may == KidoLogic.Refusal.UNTRAINED || may == KidoLogic.Refusal.TAKEN) {
            return Heard.PLAIN_LINE;
        }
        Optional<Holder.Reference<KidoSpell>> heard = KidoSpell.registry(level)
                .flatMap(spells -> KidoLogic.heard(data, spells, text));
        if (heard.isEmpty()) {
            return Heard.PLAIN_LINE;
        }
        ResourceLocation id = heard.get().key().location();
        KidoSpell spell = heard.get().value();
        // A cancelled line never reaches vanilla's spam count, so the spacing stands in for it. A hushed line does
        // not move the clock.
        if (now - data.lastChantHeardAt < CHANT_SPACING_TICKS) {
            player.displayClientMessage(Component.translatable("message.bleach.kido.chant_too_soon")
                    .withStyle(ChatFormatting.GRAY), true);
            return new Heard(Heard.Outcome.HUSHED, Optional.of(id), null);
        }
        data.lastChantHeardAt = now;
        KidoLogic.Refusal refusal = check(player, data, spell, KidoLogic.Form.CHANT, now, false);
        if (refusal != null) {
            tell(player, refusal, spell);
            return new Heard(Heard.Outcome.REFUSED, Optional.of(id), refusal);
        }
        long firesAt = now + spell.chantTicks();
        if (!player.isCreative()) {
            // Nothing else can cast while a chant is pending, so the hands are free by then.
            firesAt = Math.max(firesAt, data.kidoReadyAt);
        }
        data.kidoChant = new KidoLogic.Chant(id, spell, firesAt);
        player.displayClientMessage(Component.translatable("message.bleach.kido.chant_begun", spell.title())
                .withStyle(ChatFormatting.GRAY), true);
        return new Heard(Heard.Outcome.STARTED, Optional.of(id), null);
    }

    /**
     * A chanting soul's tick. The chant is dropped silently once the soul may not chant (it died, watches as a
     * spectator, is held, can no longer cast Kidō, or its Hollow has taken its body: the cause is plain enough), and
     * broken, with a word, once a Getsuga Tenshō or a Cero begins to gather or the soul sits in Jinzen. When
     * its time comes it goes off at full strength, every rule checked again, and a refusal then is told, with nothing
     * spent. Under a frozen tick rate the clock stands still, so it waits. Called by the tick listener and by tests.
     */
    public static void tick(Player player, SoulData data, long now) {
        KidoLogic.Chant chant = data.kidoChant;
        if (chant == null) {
            return;
        }
        if (KidoLogic.mayCast(player, data, now, KidoLogic.Form.CHANT) != null) {
            data.kidoChant = null;
            return;
        }
        // One art at a time: the newer art wins over the words.
        if (KidoLogic.otherArtGathering(data) || data.jinzen() != null) {
            breakChant(player, data);
            return;
        }
        if (now < chant.firesAt()) {
            return;
        }
        data.kidoChant = null;
        KidoLogic.Refusal refusal = cast(player, chant.spell(), KidoLogic.Form.CHANT, now, 1.0);
        if (refusal != null) {
            tell(player, refusal, chant.spell());
        }
    }

    // ---- The cast ----

    /**
     * As {@link #cast(Player, KidoSpell, KidoLogic.Form, long, double)}, with the roll for a misfire drawn from the
     * player's random for a cast by name of a spell that can misfire.
     */
    @Nullable
    public static KidoLogic.Refusal cast(Player player, KidoSpell spell, KidoLogic.Form form, long now) {
        // Only drawn when it can matter, so a spell without an incantation casts exactly as it always has.
        double roll = form == KidoLogic.Form.NAME && spell.chantable() ? player.getRandom().nextDouble() : 1.0;
        return cast(player, spell, form, now, roll);
    }

    /**
     * Casts this spell in this form now, if it may, the first check that fails winning: whether the soul may cast in
     * this form ({@link KidoLogic#mayCast}), no chant already being said, a server world and a spell that does
     * something, the Kidō level ({@link KidoLogic#gate}), the cooldown, a Getsuga Tenshō or a Cero gathering (creative
     * too), and enough reiryoku to leave {@link KidoLogic#MIN_LEFT}. Then it is paid for and the hands are busy for
     * the spell's cooldown. Cast by name, a spell with an incantation misfires when the roll falls below
     * {@link KidoLogic#misfireChance}: it bursts in the hand, hurting its caster, and nothing more happens. Otherwise
     * its shape strikes, the call goes on the caster's action bar, and Kidō trains, with the strike bonus when a
     * creature was struck and twice over for a chant; when a Hollow was among those struck, an unbound Asauchi leans
     * to kidō. Creative pays, waits, misfires, trains and leans nothing.
     *
     * <p>The one entry for every way of casting. Tells nothing; the caller does. Returns the refusal (a misfire is
     * {@link KidoLogic.Refusal#MISFIRED}), or null when cast. Takes the spell itself, not its holder, so a game test can
     * cast a spell built in code, and the roll, so a game test can force a misfire or rule one out.
     */
    @Nullable
    public static KidoLogic.Refusal cast(Player player, KidoSpell spell, KidoLogic.Form form, long now, double roll) {
        SoulData data = SoulLogic.get(player);
        KidoLogic.Refusal refusal = check(player, data, spell, form, now, true);
        // The check refuses anywhere but a server world, so past it the level is always one.
        if (refusal != null || !(player.level() instanceof ServerLevel level)) {
            return refusal == null ? KidoLogic.Refusal.UNABLE : refusal;
        }
        int kido = data.level(Stat.KIDO);
        float share = KidoLogic.share(spell, form, kido);
        boolean creative = player.isCreative();
        if (!creative) {
            if (!SoulLogic.trySpend(player, data, KidoLogic.cost(spell, data))) {
                return KidoLogic.Refusal.TOO_WEAK;
            }
            data.kidoReadyAt = now + spell.cooldownTicks();
            if (form == KidoLogic.Form.NAME && roll < KidoLogic.misfireChance(spell, kido)) {
                misfire(level, player, data, spell, share);
                return KidoLogic.Refusal.MISFIRED;
            }
        }
        List<LivingEntity> struck = switch (spell.shape()) {
            case BEAM -> beam(level, player, data, spell, share).struck();
            // Never reached: a spell of no shape is not castable, and was refused above.
            case NONE -> List.of();
        };
        player.displayClientMessage(spell.call().copy().withStyle(ChatFormatting.GRAY), true);
        if (!creative) {
            // After the call, so a level-up line takes its place on the action bar.
            SoulLogic.addStatXp(player, data, Stat.KIDO, KidoLogic.xp(spell, form, !struck.isEmpty()));
            if (struck.stream().anyMatch(target -> target instanceof Hollow)) {
                ZanpakutoEvents.lean(player, data, ZanpakutoType.KIDO, 1, now);
            }
        }
        return null;
    }

    /**
     * Why this soul may not cast this spell in this form now, or null, the first that fails winning (see
     * {@link #cast}). Changes nothing. A chant that begins is checked without the cooldown, which it waits out instead.
     */
    @Nullable
    private static KidoLogic.Refusal check(Player player, SoulData data, KidoSpell spell, KidoLogic.Form form,
                                           long now, boolean mindCooldown) {
        KidoLogic.Refusal refusal = KidoLogic.mayCast(player, data, now, form);
        if (refusal != null) {
            return refusal;
        }
        // One chant at a time, and no cast by name over one.
        if (data.kidoChant != null) {
            return KidoLogic.Refusal.CHANTING;
        }
        if (!(player.level() instanceof ServerLevel) || !spell.castable()) {
            return KidoLogic.Refusal.UNABLE;
        }
        refusal = KidoLogic.gate(spell, form, data.level(Stat.KIDO));
        if (refusal != null) {
            return refusal;
        }
        boolean creative = player.isCreative();
        if (mindCooldown && !creative && !KidoLogic.ready(data, now)) {
            return KidoLogic.Refusal.COOLDOWN;
        }
        // One art at a time, as Bala and the Getsuga Tenshō keep it: the reiatsu is already gathering elsewhere.
        if (KidoLogic.otherArtGathering(data)) {
            return KidoLogic.Refusal.GATHERING;
        }
        if (!creative && data.reiryoku() - KidoLogic.cost(spell, data) < KidoLogic.MIN_LEFT) {
            return KidoLogic.Refusal.TOO_WEAK;
        }
        return null;
    }

    /**
     * The BEAM shape: struck at once from just ahead of the eyes where the caster looks, at the form's share of its
     * damage and push (not of its reach, width or blast), stopping after the spell's pierce, breaking no blocks,
     * bursting about the last creature it strikes, and setting what it strikes alight for a spell that burns. Its
     * trace is drawn, the main hand thrusts, and the spell's sound and particles go off at the hand, and its particles
     * again where it struck or met a wall. Returns the shot.
     */
    private static CeroBeam.Shot beam(ServerLevel level, Player player, SoulData data, KidoSpell spell, float share) {
        KidoSpell.Effect effect = spell.effect();
        KidoSpell.Look look = spell.look();
        Vec3 from = CeroBeam.muzzleOf(player, 1.0F);
        CeroBeam.Spec spec = new CeroBeam.Spec(KidoLogic.power(spell, data, share), effect.range(), effect.radius(),
                effect.blastRadius(), effect.blastShare(), 0, effect.knockback() * share, look.colour(), 1, 0, false);
        CeroBeam.Kind kind = new CeroBeam.Kind(HADO, true, effect.pierce());
        // The spell, not the hand, is what a shield meets: with the caster itself as the direct entity, vanilla would
        // take a blocked Hadō for a blow from its hand. A marker at the muzzle, never added to the world, stands in.
        Marker bolt = new Marker(EntityType.MARKER, level);
        bolt.setPos(from);
        Vec3 aim = player.getLookAngle();
        CeroBeam.Shot shot = CeroBeam.fire(level, player, bolt, from, aim, spec, kind);
        // A wall already burst it; stopped at its last creature, it bursts there.
        CeroBeam.burstAt(level, player, bolt, shot, aim, spec, kind);
        if (effect.burnSeconds() > 0.0F) {
            burn(player, spell, share, shot, CeroBeam.sourceOf(level, player, bolt, kind));
        }
        CeroBeam.trace(level, player, shot, spec.radius(), look.colour(), TRACE_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                from.x, from.y, from.z, sound, SoundSource.PLAYERS, 1.0F, look.pitch(), false));
        SimpleParticleType particle = particleOf(look);
        if (particle != null) {
            SpiritSenses.particles(level, particle, from.x, from.y, from.z, HAND_PARTICLES, HAND_SPREAD, HAND_SPREAD,
                    HAND_SPREAD, PARTICLE_SPEED);
            if (!shot.struck().isEmpty() || shot.hitBlock()) {
                Vec3 to = shot.to();
                SpiritSenses.particles(level, particle, to.x, to.y, to.z, IMPACT_PARTICLES, IMPACT_SPREAD, IMPACT_SPREAD,
                        IMPACT_SPREAD, PARTICLE_SPEED);
            }
        }
        return shot;
    }

    /**
     * Sets each creature the beam struck alight for the spell's burn, at the form's share. Fire has no attacker, so
     * the reiatsu gap cannot weigh it as it lands: its time is cut here instead, by the gap or the creature's Hierro,
     * whichever cuts more, as a blow of the caster's would be, so a soul whose reiatsu dwarfs the caster's burns only
     * briefly. A creature without a soul burns in full, and those caught only by the burst do not burn.
     */
    private static void burn(Player player, KidoSpell spell, float share, CeroBeam.Shot shot, DamageSource source) {
        float caster = SoulLogic.reiatsu(player) * QuincyLogic.reiatsuFactor(player);
        for (LivingEntity target : shot.struck()) {
            float gap = 1.0F;
            if (SoulLogic.hasSoul(target)) {
                float victim = SoulLogic.reiatsu(target);
                gap = Math.min(SoulLogic.reiatsuDamageMultiplier(caster, victim),
                        Hierro.multiplier(target, source, caster, victim));
            }
            int ticks = Math.round(spell.effect().burnSeconds() * TICKS_PER_SECOND * share * gap);
            if (ticks > 0) {
                target.igniteForTicks(ticks);
            }
        }
    }

    /**
     * A cast by name burst in the caster's hand: smoke and the spell's particle at the hand with a fizzle, for those who
     * see spirits, and a share of the damage the cast would have done to the caster, which anyone sees. The blow comes
     * from no one, so PvP, a team, the reiatsu gap and a chant's break never weigh it, and it never leaves the caster
     * below one heart.
     */
    private static void misfire(ServerLevel level, Player player, SoulData data, KidoSpell spell, float share) {
        Vec3 hand = CeroBeam.muzzleOf(player, 1.0F);
        player.swing(InteractionHand.MAIN_HAND, true);
        SpiritSenses.play(level, hand.x, hand.y, hand.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, MISFIRE_VOLUME,
                MISFIRE_PITCH, false);
        SpiritSenses.particles(level, ParticleTypes.SMOKE, hand.x, hand.y, hand.z, MISFIRE_SMOKE, MISFIRE_SMOKE_SPREAD,
                MISFIRE_SMOKE_SPREAD, MISFIRE_SMOKE_SPREAD, PARTICLE_SPEED);
        SimpleParticleType particle = particleOf(spell.look());
        if (particle != null) {
            SpiritSenses.particles(level, particle, hand.x, hand.y, hand.z, MISFIRE_PARTICLES, MISFIRE_SPREAD,
                    MISFIRE_SPREAD, MISFIRE_SPREAD, PARTICLE_SPEED);
        }
        float hurt = KidoLogic.misfireDamage(spell, data, share, player.getHealth(), player.getAbsorptionAmount());
        if (hurt > 0.0F) {
            player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(HADO)), hurt);
        }
    }

    // ---- Listeners ----

    /**
     * A chat line from a player, heard as a chant if it is one: a chant is kept from chat and spoken aloud instead (a
     * hushed one is only kept from chat), and everything else goes on as chat. The event is posted on the server
     * thread. Any fault here leaves the line as chat, since the line is cancelled last, and is logged once; a chant
     * it began stands, its words having been said.
     */
    private static void onChat(ServerChatEvent event) {
        try {
            Player player = event.getPlayer();
            String text = event.getRawText();
            Heard heard = chant(player, text, player.level().getGameTime());
            if (!heard.cancels()) {
                return;
            }
            if (heard.spoken()) {
                ShikaiEvents.speak(player, Component.literal(text));
                // A cancelled line is not written to the server's log, so a moderator reads it here.
                BleachMod.LOGGER.info("Kidō: {} chants {} ({}): {}", event.getUsername(),
                        heard.spell().map(ResourceLocation::toString).orElse("?"),
                        heard.refusal() != null ? heard.refusal() : heard.outcome(), text);
            }
            event.setCanceled(true);
        } catch (RuntimeException e) {
            BleachMod.LOGGER.warn("Kidō could not hear a chat line from {}; it stays plain chat", event.getUsername(), e);
        }
    }

    /**
     * A chanting player's tick, before it moves, so a chant goes off from where a key press would. A fault drops the
     * chant and is logged once, as a throw here would kick the player or stop a single-player world.
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (data.kidoChant == null) {
            return;
        }
        try {
            tick(player, data, player.level().getGameTime());
        } catch (RuntimeException e) {
            data.kidoChant = null;
            BleachMod.LOGGER.warn("Kidō: {}'s chant failed to go off, and is dropped", player.getName().getString(), e);
        }
    }

    /**
     * A creature's blow that lands breaks a chant: one from a living attacker other than the chanting soul, that took
     * health or absorption hearts. Fire, falls, poison, magic and the soul's own misfire come from no creature, and a
     * blow a shield stopped took nothing, so none of those break it.
     */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        // Damage soaked by absorption hearts still lands.
        boolean landed = event.getNewDamage() > 0.0F
                || event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0.0F;
        if (landed && SoulLogic.hasSoul(player)) {
            SoulData data = SoulLogic.get(player);
            if (data.kidoChant != null) {
                breakChant(player, data);
            }
        }
    }

    /** A chant does not follow its soul into another world: a portal keeps the same soul, so it is dropped here. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && SoulLogic.hasSoul(player)) {
            SoulLogic.get(player).kidoChant = null;
        }
    }

    // ---- Helpers ----

    /** Clears the chant and tells the player "Your chant is broken". Nothing was spent, so nothing is given back. */
    private static void breakChant(Player player, SoulData data) {
        data.kidoChant = null;
        player.displayClientMessage(Component.translatable("message.bleach.kido.chant_broken")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Shows the spell's incantations in the player's own chat under a heading, each on its line, so they can be read
     * while the chat is open to type one. On a mock player, which has no chat, this does nothing.
     */
    private static void printIncantations(Player player, KidoSpell spell) {
        player.sendSystemMessage(Component.translatable("message.bleach.kido.incantation", spell.title())
                .withStyle(ChatFormatting.GRAY));
        for (String incantation : spell.incantations()) {
            player.sendSystemMessage(Component.literal(incantation).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** The spell's particle, or null when it has none or it needs options of its own (only a simple one is named by id). */
    @Nullable
    private static SimpleParticleType particleOf(KidoSpell.Look look) {
        return look.particle().flatMap(BuiltInRegistries.PARTICLE_TYPE::getOptional).orElse(null)
                instanceof SimpleParticleType particle ? particle : null;
    }

    /**
     * Tells the player a refusal on the action bar, in grey, unless it is a silent one: the spell's title and the level
     * it needs for a level, and its title for reiryoku and for a misfire.
     */
    private static void tell(Player player, KidoLogic.Refusal refusal, @Nullable KidoSpell spell) {
        String key = refusal.messageKey();
        if (key == null) {
            return;
        }
        MutableComponent line;
        if (spell != null && refusal == KidoLogic.Refusal.NEEDS_LEVEL) {
            line = Component.translatable(key, spell.title(), spell.level());
        } else if (spell != null && refusal == KidoLogic.Refusal.NEEDS_NAME_LEVEL) {
            line = Component.translatable(key, spell.title(), spell.byNameLevel());
        } else if (spell != null && refusal == KidoLogic.Refusal.TOO_WEAK) {
            line = Component.translatable(key, spell.title());
        } else if (spell != null && refusal == KidoLogic.Refusal.MISFIRED) {
            line = Component.translatable(key, spell.title());
        } else {
            line = Component.translatable(key);
        }
        player.displayClientMessage(line.withStyle(ChatFormatting.GRAY), true);
    }
}
