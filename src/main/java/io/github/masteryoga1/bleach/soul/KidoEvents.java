package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.DankuWall;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.network.KidoKeyPayload;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.ArrayList;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Casting Kidō. The Kidō key (U) casts the chosen spell by name alone, Eishōhaki in canon's terms, and Sneak and the
 * key choose the next spell the soul's Kidō level allows; the action bar names it, and the chat shows its incantation
 * if it has one. A cast by name is one whole act within a key press, a chant goes off after its chant time, and a
 * spell that gathers is held in the palm on the key until it goes off by itself: it is paid for, the hands are busy
 * for the spell's cooldown, the spell's shape strikes, the caster calls it out on its own action bar and Kidō trains,
 * one xp more for a strike. The rules are in {@link KidoLogic}.
 *
 * <p>A soul that may cast says a spell's whole incantation in chat ({@link #chant}). The line is not sent to chat: it
 * is spoken aloud, to the caster and to those nearby who see spirits, and the spell goes off at full strength once its
 * chant time has passed, aimed where the caster looks then ({@link #tick}). A creature's blow that lands meanwhile
 * breaks the chant, and nothing is spent. A chanted cast trains twice the Kidō. Cast by name alone instead, a spell
 * with an incantation may burst in its caster's hand, until its caster has mastered it.
 *
 * <p>A Hadō of {@link KidoSpell.Shape#BEAM} strikes through the Cero's engine as a Bala does ({@link CeroBeam#fire}
 * with a quiet {@link CeroBeam.Kind} of {@link #HADO}), so it keeps every rule a Cero keeps on PvP, teams, pets,
 * shields and the reiatsu gap, and it breaks no blocks. A Hadō of {@link KidoSpell.Shape#GUST} strikes at once every
 * creature in a cone widening from the hand ({@link CeroBeam#inCone}) under the same rules, pushing it away and
 * blinding a player. A Bakudō of {@link KidoSpell.Shape#BIND} is a bind to the first creature in sight that the caster
 * may bind ({@link #bindAim}), a wall stopping it: its arms bound, its body held where it stands, or both, for a time
 * weighed against its reiatsu, and shrugged off by a soul far stronger than the caster ({@link #bindOne}). A Bakudō of
 * {@link KidoSpell.Shape#SMOKE} leaves a cloud of smoke where the caster stood, in which creatures lose the caster and
 * the players it may harm are blinded, and quickens the caster ({@link KidoSmoke}). A Bakudō of
 * {@link KidoSpell.Shape#SHIELD} raises a round shield before the caster, which takes the blows from the front up to its
 * strength and breaks under a bigger one ({@link KidoWard}). A Bakudō of {@link KidoSpell.Shape#WALL} raises a Dankū
 * before the caster, an entity that stops what is shot at it ({@link DankuWall}). A spell's trace, sound and particles
 * are seen and heard only by those who see spirits; what it does to a creature anyone can see.
 * Creative pays nothing, waits for nothing, never misfires and trains nothing. One art at a time: no Kidō while a
 * Getsuga Tenshō or a Cero gathers, and one that begins to gather breaks a chant or a gather.
 *
 * <p>The keys come from {@link KidoKeyPayload} and the chant from chat ({@link #register}); the cooldown, the pending
 * chant, the key gather, a standing smoke or shield and the spacing between spoken chants are transients in the soul,
 * so a respawn or a relog starts them over. Everything takes a {@link Player}, so game tests can use mock players.
 */
public final class KidoEvents {
    public static final ResourceKey<DamageType> HADO = ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("hado"));
    /** Ticks a spell's trace stays, fading. */
    public static final int TRACE_TICKS = 6;
    /** Ticks after one spoken chant before another is spoken: a matched line sooner is hushed. */
    public static final int CHANT_SPACING_TICKS = 20;
    /** Ticks an orb outlasts its gather, so Kidō, which ticks after the level, always looses it first. */
    public static final int ORB_SLACK_TICKS = 2;
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
    private static final int GUST_RINGS = 4;
    private static final double GUST_RING_SPREAD = 0.5;
    private static final int GATHER_SPARK_TICKS = 2;
    private static final int GATHER_SPARKS = 2;
    private static final SoundEvent GATHER_SOUND = SoundEvents.BEACON_ACTIVATE;
    private static final float GATHER_PITCH = 1.8F;
    private static final float GATHER_VOLUME = 0.6F;
    /** A hundredth of a block, squared: a creature closer to its caster than that is pushed the way the caster faces. */
    private static final double PUSH_EPSILON = 1.0E-4;
    /**
     * How a gust strikes, and the orb a gathering spell shows: a Hadō, quiet, so the orb is drawn and synced as a Cero's
     * but never sounds like one.
     */
    private static final CeroBeam.Kind ORB = new CeroBeam.Kind(HADO, true, 1);
    /** What a caster reads in place of its call when its bind is shrugged off: the spell's title, not the creature's. */
    private static final String SHRUGGED_KEY = "message.bleach.bakudo.shrugged";
    /** The glyphs that flare about a creature as it shrugs a bind off, how far they spread, and the chain's pitch. */
    private static final int SHRUG_MOTES = 8;
    private static final double SHRUG_SPREAD = 0.3;
    private static final float SHRUG_PITCH = 1.2F;

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

    /**
     * Where a bind cast now goes: the first creature it may bind, or null, and where the ray ends, where it meets that
     * creature or else at the first block in the way or the spell's reach.
     */
    public record BindAim(@Nullable LivingEntity target, Vec3 end) {
    }

    /**
     * The chat listener, the tick of a pending chant or gather and of a standing smoke or shield, the blow that breaks a
     * chant or a gather and the dimension change that drops them all; the listeners that refuse what a Bakudō's bind
     * keeps a creature from ({@link BakudoEffect#register}); and the shield's, which takes the blows it covers
     * ({@link KidoWard#register}).
     */
    public static void register(IEventBus bus) {
        bus.addListener(KidoEvents::onChat);
        bus.addListener(KidoEvents::onPlayerTick);
        bus.addListener(KidoEvents::onDamageTaken);
        bus.addListener(KidoEvents::onChangedDimension);
        BakudoEffect.register(bus);
        KidoWard.register(bus);
    }

    // ---- The key ----

    /**
     * The Kidō key, as sent by {@link KidoKeyPayload} (and called directly by game tests): PRESS casts the chosen
     * spell by name, or begins to gather one that gathers, NEXT chooses the next known one; RELEASE looses or scatters
     * a gathering spell and CANCEL scatters it; SELECT does nothing yet. A held soul is told so by its holder and does
     * nothing more. Tells the player why on the action bar. Returns the refusal, or null when done or ignored.
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
            // Every key-up sends RELEASE, and a screen opening CANCEL: without a gather both mean nothing.
            case RELEASE -> release(player, data, now);
            case CANCEL -> data.kidoGather == null ? null : scatter(player, data);
            // SELECT waits for the Kidō page (6f).
            default -> null;
        };
    }

    /** The key alone: the chosen spell, cast by name or begun to gather, or why not, told. */
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
        if (spell.gathers()) {
            return gather(player, data, level, chosen.get(), now);
        }
        refusal = cast(player, spell, KidoLogic.Form.NAME, now);
        if (refusal != null) {
            tell(player, refusal, spell);
        }
        return refusal;
    }

    /**
     * Sneak and the key: the next known spell is chosen and named, with the level it can be cast by name from when
     * that is still to come, and its incantation, if it has one, is shown in the player's own chat. A soul kept from
     * casting only by its Hollow, its lent power or its bound arms may still choose, and so may one that is chanting.
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
     * spectator, is held, can no longer cast Kidō, its Hollow has taken its body, or its arms are bound and the bind
     * did not break it first: the cause is plain enough), and
     * broken, with a word, once a Getsuga Tenshō or a Cero begins to gather or the soul sits in Jinzen. When
     * its time comes it goes off at full strength, every rule checked again, and a refusal then is told, with nothing
     * spent. A spell gathering on the key is dropped, broken and let go alike, by the rules of a cast by name, and
     * sparks in the palm meanwhile. Under a frozen tick rate the clock stands still, so it waits. Called by the tick
     * listener and by tests.
     */
    public static void tick(Player player, SoulData data, long now) {
        KidoLogic.Chant chant = data.kidoChant;
        if (chant == null) {
            // A chant and a gather never wait together: each refuses the other (check).
            if (data.kidoGather != null) {
                tickGather(player, data, now);
            }
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

    // ---- The gather ----

    /**
     * The key on a spell that gathers: checked as a cast by name is, the cooldown minded, and refused to a soul in
     * Jinzen, which would break it at its next tick. Then it gathers in the palm, shown by a quiet orb, and goes off by
     * itself once its gather time has passed ({@link #tick}), or sooner when the key is let go just before. Nothing is
     * paid, cooled, rolled or trained until it goes off, so a gather that comes to nothing costs nothing.
     */
    @Nullable
    private static KidoLogic.Refusal gather(Player player, SoulData data, ServerLevel level,
                                            Holder.Reference<KidoSpell> chosen, long now) {
        KidoSpell spell = chosen.value();
        KidoLogic.Refusal refusal = check(player, data, spell, KidoLogic.Form.NAME, now, true);
        // The check does not read Jinzen, which breaks what is pending at its next tick: so no orb shows for a tick.
        if (refusal == null && data.jinzen() != null) {
            refusal = KidoLogic.Refusal.GATHERING;
        }
        if (refusal != null) {
            tell(player, refusal, spell);
            return refusal;
        }
        // An orb the level refused leaves the gather unseen, not undone.
        CeroBeam orb = CeroBeam.charge(level, player, orbSpec(spell), ORB);
        data.kidoGather = new KidoLogic.Gather(chosen.key().location(), spell, now + spell.gatherTicks(), orb);
        Vec3 hand = CeroBeam.muzzleOf(player, 1.0F);
        SpiritSenses.play(level, hand.x, hand.y, hand.z, GATHER_SOUND, SoundSource.PLAYERS, GATHER_VOLUME, GATHER_PITCH,
                false);
        player.displayClientMessage(Component.translatable("message.bleach.kido.gather_begun", spell.title())
                .withStyle(ChatFormatting.GRAY), true);
        return null;
    }

    /**
     * The orb a gathering spell shows in the palm: the Cero's own charging entity, as wide and in the colour of the
     * spell, striking nothing, pushing nothing and breaking nothing, and outlasting the gather by
     * {@link #ORB_SLACK_TICKS}, so it never fires itself before Kidō looses the spell.
     */
    private static CeroBeam.Spec orbSpec(KidoSpell spell) {
        return new CeroBeam.Spec(0.0F, 1.0, spell.effect().radius(), 0.0F, 0.0F, 0, 0.0F, spell.look().colour(),
                spell.gatherTicks() + ORB_SLACK_TICKS, 0, false);
    }

    /**
     * A gathering soul's tick, as a chanting soul's: the gather is dropped silently once the soul may not cast by name
     * (it died, watches as a spectator, is held, can no longer cast Kidō, its Hollow has taken its body, its arms are
     * bound and the bind did not break it first, or its power is lent), and broken, with a word, once a Getsuga Tenshō
     * or a Cero begins to gather or the soul sits in Jinzen.
     * Until its time comes the palm sparks; then it is let go ({@link #loose}).
     */
    private static void tickGather(Player player, SoulData data, long now) {
        KidoLogic.Gather gather = data.kidoGather;
        if (KidoLogic.mayCast(player, data, now, KidoLogic.Form.NAME) != null) {
            dropGather(player, data);
            return;
        }
        // One art at a time: the newer art wins over the gather, as over a chant.
        if (KidoLogic.otherArtGathering(data) || data.jinzen() != null) {
            breakGather(player, data);
            return;
        }
        if (now < gather.firesAt()) {
            // The caster cannot see its own orb in first person, so the sparks tell it the spell is gathering.
            SimpleParticleType particle = particleOf(gather.spell().look());
            if (now % GATHER_SPARK_TICKS == 0 && particle != null && player.level() instanceof ServerLevel level) {
                Vec3 hand = CeroBeam.muzzleOf(player, 1.0F);
                SpiritSenses.particles(level, particle, hand.x, hand.y, hand.z, GATHER_SPARKS, HAND_SPREAD, HAND_SPREAD,
                        HAND_SPREAD, PARTICLE_SPEED);
            }
            return;
        }
        loose(player, data, now);
    }

    /**
     * Lets the gathered spell go: its orb goes silently, and the spell is cast by name now, as it was when the key was
     * pressed, every rule checked again and the misfire's roll drawn, aimed where the caster looks now. A refusal is
     * told, with nothing spent. Returns it, or null when cast.
     */
    @Nullable
    private static KidoLogic.Refusal loose(Player player, SoulData data, long now) {
        KidoLogic.Gather gather = data.kidoGather;
        if (gather == null) {
            return null;
        }
        // Cleared first, or the cast's own check would find the gather and refuse it.
        dropGather(player, data);
        KidoLogic.Refusal refusal = cast(player, gather.spell(), KidoLogic.Form.NAME, now);
        if (refusal != null) {
            tell(player, refusal, gather.spell());
        }
        return refusal;
    }

    /**
     * The Kidō key let go: a gathering spell goes off now if it would have at the very next tick, since a key's
     * {@code now} is the tick just ended, and scatters if let go any sooner. Without a gather, nothing happens: a tap
     * on any spell sends this too.
     */
    @Nullable
    private static KidoLogic.Refusal release(Player player, SoulData data, long now) {
        KidoLogic.Gather gather = data.kidoGather;
        if (gather == null) {
            return null;
        }
        return now + 1 >= gather.firesAt() ? loose(player, data, now) : scatter(player, data);
    }

    /**
     * Scatters the gathering spell, let go too soon or under a screen that opened: its orb fizzles, the player is told
     * to hold the key, and nothing was spent, so nothing is given back. Returns {@link KidoLogic.Refusal#SCATTERED}.
     */
    private static KidoLogic.Refusal scatter(Player player, SoulData data) {
        KidoLogic.Gather gather = data.kidoGather;
        data.kidoGather = null;
        if (gather != null) {
            // A hiss and a little smoke where it was; nothing, if it has gone already.
            if (gather.orb() != null) {
                gather.orb().cancel();
            }
            tell(player, KidoLogic.Refusal.SCATTERED, gather.spell());
        }
        return KidoLogic.Refusal.SCATTERED;
    }

    /**
     * Breaks the gathering spell, as a creature's blow or another art does: its orb fizzles, the player is told
     * "… is broken before it has gathered", and nothing was spent, so nothing is given back.
     */
    private static void breakGather(Player player, SoulData data) {
        KidoLogic.Gather gather = data.kidoGather;
        data.kidoGather = null;
        if (gather != null) {
            if (gather.orb() != null) {
                gather.orb().cancel();
            }
            player.displayClientMessage(Component.translatable("message.bleach.kido.gather_broken",
                    gather.spell().title()).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Drops the spell this soul is gathering, if any, silently: its orb goes without a sound. */
    public static void dropGather(Player player, SoulData data) {
        KidoLogic.Gather gather = data.kidoGather;
        data.kidoGather = null;
        CeroBeam orb = gather != null ? gather.orb() : null;
        if (orb != null && !orb.isRemoved()) {
            orb.discard();
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
     * this form ({@link KidoLogic#mayCast}), no chant being said or spell gathering, a server world and a spell that
     * does something, the Kidō level ({@link KidoLogic#gate}), the cooldown, a Getsuga Tenshō or a Cero gathering
     * (creative too), and enough reiryoku to leave {@link KidoLogic#MIN_LEFT}. Its strength is the form's share, and
     * less for a spell that wants a zanpakutō at hand when there is none ({@link KidoLogic#handShare}). Then it is paid
     * for and the hands are busy for the spell's cooldown. Cast by name, a spell with an incantation misfires when the
     * roll falls below {@link KidoLogic#misfireChance}: it bursts in the hand, hurting its caster, and nothing more
     * happens. Otherwise its shape strikes (a beam, a gust, or a bind) or wards (a smoke, a shield or a wall, which strike
     * nothing), the call goes on the caster's action bar (for a bind shrugged off, that it was), and Kidō trains, with
     * the strike bonus when a creature was struck or bound and twice over for a chant; when a Hollow was among those
     * struck or bound, an unbound Asauchi leans to kidō. Creative pays, waits, misfires, trains and leans nothing.
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
        float share = KidoLogic.share(spell, form, kido) * KidoLogic.handShare(spell, player);
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
        Component said = spell.call();
        List<LivingEntity> struck = switch (spell.shape()) {
            case BEAM -> beam(level, player, data, spell, share).struck();
            case GUST -> gust(level, player, data, spell, share);
            case BIND -> {
                BindAim aim = bindAim(player, spell);
                drawBind(level, player, spell, aim.end());
                KidoLogic.BindResult result = aim.target() == null ? null
                        : bindOne(player, aim.target(), spell, share, now);
                if (result == KidoLogic.BindResult.SHRUGGED) {
                    // In the call's place: the spell is named, not the creature, so a long name tag cannot overrun.
                    said = Component.translatable(SHRUGGED_KEY, spell.title());
                }
                yield result == KidoLogic.BindResult.BOUND ? List.of(aim.target()) : List.of();
            }
            // A smoke hides, and a shield and a wall stop: none strikes, so none earns the strike bonus or leans the blade.
            case SMOKE -> {
                KidoSmoke.raise(level, player, data, spell, share, now);
                yield List.of();
            }
            case SHIELD -> {
                KidoWard.raise(player, data, spell, share, now);
                yield List.of();
            }
            case WALL -> {
                DankuWall.raise(level, player, spell, share, now);
                yield List.of();
            }
            // Never reached: a spell of no shape is not castable, and was refused above.
            case NONE -> List.of();
        };
        player.displayClientMessage(said.copy().withStyle(ChatFormatting.GRAY), true);
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
        // Nor anything over a spell gathering on the key, which is the reiatsu already at work.
        if (data.kidoGather != null) {
            return KidoLogic.Refusal.GATHERING;
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
     * whichever cuts more ({@link KidoLogic#gap}), as a blow of the caster's would be, so a soul whose reiatsu dwarfs
     * the caster's burns only briefly. A creature without a soul burns in full, and those caught only by the burst do
     * not burn.
     */
    private static void burn(Player player, KidoSpell spell, float share, CeroBeam.Shot shot, DamageSource source) {
        for (LivingEntity target : shot.struck()) {
            float gap = KidoLogic.gap(player, target, source);
            int ticks = Math.round(spell.effect().burnSeconds() * TICKS_PER_SECOND * share * gap);
            if (ticks > 0) {
                target.igniteForTicks(ticks);
            }
        }
    }

    /**
     * The GUST shape: struck at once from just ahead of the eyes, widening along where the caster looks to the spell's
     * angle over its range (at most {@link KidoSpell#GUST_MAX_RANGE}), at the form's share of its damage, push and
     * blindness (not of its reach or width). Every creature the caster may strike that stands in it, in the open from
     * the hand, takes its blow ({@link #gustBlow}), nearest first; a wall stops it, and it breaks no blocks. A pale
     * streak is drawn down its middle to the first block, rings of the spell's particle widen along it, the main hand
     * swings and the spell's sound goes off at the hand. Returns the creatures it hurt, nearest first.
     */
    private static List<LivingEntity> gust(ServerLevel level, Player player, SoulData data, KidoSpell spell,
                                           float share) {
        KidoSpell.Effect effect = spell.effect();
        KidoSpell.Look look = spell.look();
        Vec3 apex = CeroBeam.muzzleOf(player, 1.0F);
        Vec3 axis = player.getLookAngle();
        double range = Math.min(effect.range(), KidoSpell.GUST_MAX_RANGE);
        // As for a beam, the spell and not the hand is what a shield meets: a marker at the muzzle, never added to the
        // world, stands in.
        Marker bolt = new Marker(EntityType.MARKER, level);
        bolt.setPos(apex);
        DamageSource source = CeroBeam.sourceOf(level, player, bolt, ORB);
        float damage = KidoLogic.power(spell, data, share);
        List<LivingEntity> struck = new ArrayList<>();
        for (LivingEntity target : CeroBeam.inCone(level, player, apex, axis, range, effect.angle())) {
            if (gustBlow(player, target, source, spell, share, damage)) {
                struck.add(target);
            }
        }
        Vec3 far = apex.add(axis.scale(range));
        HitResult wall = level.clip(new ClipContext(apex, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        Vec3 end = wall.getType() == HitResult.Type.BLOCK ? wall.getLocation() : far;
        CeroBeam.trace(level, player, new CeroBeam.Shot(apex, end, false, false, List.of(), 0), effect.radius(),
                look.colour(), TRACE_TICKS);
        SimpleParticleType particle = particleOf(look);
        if (particle != null) {
            // Capped, so a data pack's half-circle gust does not throw its puffs off to the horizon.
            double widening = Math.min(1.0, Math.tan(Math.toRadians(effect.angle() / 2.0)) * GUST_RING_SPREAD);
            for (int ring = 1; ring <= GUST_RINGS; ring++) {
                double out = range * ring / GUST_RINGS;
                Vec3 at = apex.add(axis.scale(out));
                double spread = out * widening;
                SpiritSenses.particles(level, particle, at.x, at.y, at.z, 1 + ring, spread, spread, spread,
                        PARTICLE_SPEED);
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                apex.x, apex.y, apex.z, sound, SoundSource.PLAYERS, 1.0F, look.pitch(), false));
        return struck;
    }

    /**
     * A gust's blow on one creature (see {@link #gust}): nothing for one the caster may not strike or that Negación
     * holds; else the blow, and the push away from the caster when it lands, and a player's blindness either way, the
     * push and the blindness cut by the gap ({@link KidoLogic#gap}); none of the blindness through a Visored's duel,
     * nor when a Kidō shield took the whole gust ({@link KidoWard#covers}): a shield that takes the gust screens the
     * eyes too. The blow itself is cut as it lands, as every blow is. Returns whether the blow landed. Public for
     * tests, whose mock players no cone can find.
     */
    public static boolean gustBlow(Player caster, LivingEntity target, DamageSource source, KidoSpell spell, float share,
                                   float damage) {
        if (!CeroBeam.mayStrike(caster, target) || Negacion.held(target)) {
            return false;
        }
        // Weighed as the blow itself is, before it lands.
        float gap = KidoLogic.gap(caster, target, source);
        // Asked before the blow, which may break the shield.
        boolean warded = KidoWard.covers(target, source);
        boolean landed = target.hurt(source, damage);
        if (landed) {
            // As a beam's blow does, so the caster's tamed wolves join in.
            caster.setLastHurtMob(target);
            float push = spell.effect().knockback() * share * gap;
            if (push > 0.0F) {
                // Outward from the caster, so a crowd is cleared as a gust would clear it, not swept down one line.
                Vec3 away = new Vec3(target.getX() - caster.getX(), 0.0, target.getZ() - caster.getZ());
                away = away.lengthSqr() < PUSH_EPSILON ? Vec3.directionFromRotation(0.0F, caster.getYRot())
                        : away.normalize();
                target.knockback(push, -away.x, -away.z);
                // A player's client only learns of the push when its motion is sent.
                target.hurtMarked = true;
            }
        }
        // A gust in the face screens the eyes even when it does no harm; the duel's shield keeps it out, as every blow,
        // and so does a Kidō shield that took it whole.
        if ((landed || !warded) && target instanceof Player player && !VisoredEvents.isDuelling(caster)
                && !VisoredEvents.isDuelling(player)) {
            int ticks = KidoLogic.blindTicks(spell, share, gap);
            if (ticks > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, true), caster);
            }
        }
        return landed;
    }

    /**
     * The ray a bind follows, as Negación's aim does ({@link Negacion#aimed}): from the eyes along the look, out to the
     * spell's range and short of the first block in the way, the spell's radius to either side counting. Spectators,
     * lights and Cajas, and every creature the caster may not bind ({@link KidoLogic#mayBind}: a Plus, a team-mate, a
     * creative player, one Negación holds) are passed by, so the ray goes on to what stands behind them, as a Hadō's
     * beam would; a part of a great creature stands for the whole. On a creature, the ray ends where it meets it. One
     * so close that the caster's eyes are within its widened box, which a projectile's search passes over, is met first
     * of all, where the ray leaves it, if any of it stands ahead of the eyes ({@link #ahead}): one pressed to the
     * caster's back is behind the ray. Of two the eyes are within, the one reaching further ahead is met. Changes
     * nothing. Public for tests.
     */
    public static BindAim bindAim(Player caster, KidoSpell spell) {
        float margin = spell.effect().radius();
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(spell.effect().range()));
        end = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
                .getLocation();
        Entity met = null;
        Vec3 at = end;
        double nearest = Double.MAX_VALUE;
        double farthest = 0.0;
        // Searched a margin wider than the ray, or a data pack's wide one would miss a creature it should meet.
        for (Entity entity : caster.level().getEntities(caster, new AABB(eye, end).inflate(1.0 + margin), candidate -> {
            LivingEntity body = SpiritProjectiles.bodyOf(candidate);
            return candidate.isPickable() && !candidate.isSpectator() && !(candidate instanceof Negacion.HoldingVehicle)
                    && body != null && KidoLogic.mayBind(caster, body);
        })) {
            // The box it met (a part's, for a great creature), widened by the margin.
            AABB box = entity.getBoundingBox().inflate(margin);
            double distance;
            double reach = 0.0;
            Vec3 meets;
            if (box.contains(eye)) {
                reach = ahead(entity.getBoundingBox(), eye, look);
                if (reach <= 0.0) {
                    continue;
                }
                distance = 0.0;
                meets = box.clip(end, eye).orElse(end);
            } else {
                Optional<Vec3> entry = box.clip(eye, end);
                if (entry.isEmpty()) {
                    continue;
                }
                distance = eye.distanceToSqr(entry.get());
                meets = entry.get();
            }
            if (distance < nearest || distance == 0.0 && nearest == 0.0 && reach > farthest) {
                nearest = distance;
                farthest = reach;
                met = entity;
                at = meets;
            }
        }
        return met == null ? new BindAim(null, end) : new BindAim(SpiritProjectiles.bodyOf(met), at);
    }

    /** How far ahead of the eyes, along the look, this box reaches at its furthest corner: below 0 when wholly behind. */
    private static double ahead(AABB box, Vec3 eye, Vec3 look) {
        return Math.max((box.minX - eye.x) * look.x, (box.maxX - eye.x) * look.x)
                + Math.max((box.minY - eye.y) * look.y, (box.maxY - eye.y) * look.y)
                + Math.max((box.minZ - eye.z) * look.z, (box.maxZ - eye.z) * look.z);
    }

    /**
     * Binds this one creature with this spell at this share, as a cast's bind does once its ray has found it, without
     * the cast, its cost or its trace, so a game test can bind a mock player, which no ray can find. Server side.
     *
     * <p>Refused, doing nothing, for a creature the caster may not bind ({@link KidoLogic#mayBind}). An inner Hollow,
     * which is beaten by instinct and never by Kidō, and a creature still wary of this spell from its last bind
     * ({@link KidoLogic#wary}) shrug it off. Anything else is weighed against the caster's strength
     * ({@link KidoLogic#bindRatio}): its arms are bound and its body held for each of the spell's times that its
     * strength allows ({@link KidoLogic#bindTicks}), hidden but for the look drawn on it, with the caster as the
     * source. When neither lands (too strong a soul, a Quincy woven in Ransōtengai, or a creature no effect touches),
     * it is shrugged off, heard and seen by those who see spirits. A bind that lands leaves the creature wary of the
     * spell for twice its time from now, and arms bound break the chant a soul was saying and the spell it was
     * gathering, as a blow would, so it is told why they stopped.
     *
     * <p>Each effect that lands remembers the spell's look on the creature ({@link BakudoEffect#markLook}), so it is
     * drawn in that spell's style and colour while it lasts. A body its Hollow had seized before the bind, held for at
     * least {@code pin_seconds} ({@link KidoLogic#pins}), is pinned at once: the seizure ends
     * ({@link VisoredEvents.TakeoverEnd#PINNED}), and the bind goes on.
     */
    public static KidoLogic.BindResult bindOne(Player caster, LivingEntity target, KidoSpell spell, float share,
                                               long now) {
        if (!KidoLogic.mayBind(caster, target)) {
            return KidoLogic.BindResult.REFUSED;
        }
        // Before the effects, as the contest weighed it: the Hollow's grip is what the hold must pin.
        boolean taken = KidoLogic.takenBody(target);
        int bound = 0;
        int held = 0;
        if (!(target instanceof InnerHollow) && !KidoLogic.wary(target, spell, now)) {
            float ratio = KidoLogic.bindRatio(KidoLogic.bindStrength(spell, caster, SoulLogic.get(caster), share),
                    target);
            bound = KidoLogic.bindTicks(spell.effect().boundSeconds(), ratio);
            held = KidoLogic.bindTicks(spell.effect().heldSeconds(), ratio);
        }
        // Not ambient, no swirl and no icon: the effect draws its own look on the creature.
        boolean arms = bound > 0 && target.addEffect(new MobEffectInstance(ModEffects.BAKUDO_BOUND, bound, 0, false,
                false, false), caster);
        boolean body = held > 0 && target.addEffect(new MobEffectInstance(ModEffects.BAKUDO_HELD, held, 0, false,
                false, false), caster);
        if (!arms && !body) {
            shrug(target);
            return KidoLogic.BindResult.SHRUGGED;
        }
        // Weighed by what landed, so a hold the creature refused does not lengthen the wariness.
        KidoLogic.markWary(target, spell, now, Math.max(arms ? bound : 0, body ? held : 0));
        // Only for what landed: an effect refused keeps the look of the bind that holds it still.
        if (arms) {
            BakudoEffect.markLook(target, false, spell.look());
        }
        if (body) {
            BakudoEffect.markLook(target, true, spell.look());
        }
        if (arms && target instanceof Player player && SoulLogic.hasSoul(player)) {
            SoulData data = SoulLogic.get(player);
            if (data.kidoChant != null) {
                breakChant(player, data);
            }
            if (data.kidoGather != null) {
                breakGather(player, data);
            }
        }
        if (taken && body && KidoLogic.pins(held) && target instanceof Player host) {
            VisoredEvents.endTakeover(host, SoulLogic.get(host), VisoredEvents.TakeoverEnd.PINNED, now);
        }
        return KidoLogic.BindResult.BOUND;
    }

    /**
     * A bind's cast, seen and heard by those who see spirits: a thin trace from the hand to where its ray ended, in the
     * spell's colour and as wide as its margin, the main hand thrown out, and the spell's particle and sound at the
     * hand.
     */
    private static void drawBind(ServerLevel level, Player player, KidoSpell spell, Vec3 end) {
        KidoSpell.Look look = spell.look();
        Vec3 muzzle = CeroBeam.muzzleOf(player, 1.0F);
        // The trace draws only its two ends, so whether the ray met a wall is not its concern.
        CeroBeam.trace(level, player, new CeroBeam.Shot(muzzle, end, false, false, List.of(), 0), spell.effect().radius(),
                look.colour(), TRACE_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
        SimpleParticleType particle = particleOf(look);
        if (particle != null) {
            SpiritSenses.particles(level, particle, muzzle.x, muzzle.y, muzzle.z, HAND_PARTICLES, HAND_SPREAD,
                    HAND_SPREAD, HAND_SPREAD, PARTICLE_SPEED);
        }
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                muzzle.x, muzzle.y, muzzle.z, sound, SoundSource.PLAYERS, 1.0F, look.pitch(), false));
    }

    /**
     * A bind shrugged off, for those who see spirits: the crack of breaking chain at the creature, and glyphs flaring
     * about its chest, as canon's Sai glows as it breaks. Nothing on a client.
     */
    private static void shrug(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        SpiritSenses.play(target, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, SHRUG_PITCH, false);
        SpiritSenses.particles(level, ParticleTypes.ENCHANT, target.getX(), target.getY(0.6), target.getZ(), SHRUG_MOTES,
                SHRUG_SPREAD, SHRUG_SPREAD, SHRUG_SPREAD, PARTICLE_SPEED);
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
     * A chanting, gathering or warding player's tick, before it moves, so a chant or a gather goes off from where a key
     * press would; then its smoke and its shield ({@link KidoSmoke#tick}, {@link KidoWard#tick}). A fault drops the
     * chant and the gather, or the smoke and the shield, and is logged once, as a throw here would kick the player or
     * stop a single-player world; a fault in one pair never drops the other.
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        boolean casting = data.kidoChant != null || data.kidoGather != null;
        boolean warding = data.kidoSmoke != null || data.kidoShield != null;
        if (!casting && !warding) {
            return;
        }
        long now = player.level().getGameTime();
        if (casting) {
            try {
                tick(player, data, now);
            } catch (RuntimeException e) {
                data.kidoChant = null;
                dropGather(player, data);
                BleachMod.LOGGER.warn("Kidō: {}'s chant or gather failed, and is dropped",
                        player.getName().getString(), e);
            }
        }
        if (warding) {
            try {
                KidoSmoke.tick(player, data, now);
                KidoWard.tick(player, data, now);
            } catch (RuntimeException e) {
                data.kidoSmoke = null;
                data.kidoShield = null;
                BleachMod.LOGGER.warn("Kidō: {}'s smoke or shield failed, and is dropped",
                        player.getName().getString(), e);
            }
        }
    }

    /**
     * A creature's blow that lands breaks a chant or a gather: one from a living attacker other than the soul itself,
     * that took health or absorption hearts. Fire, falls, poison, magic and the soul's own misfire come from no
     * creature, and a blow a shield stopped took nothing, so none of those break either.
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
            if (data.kidoGather != null) {
                breakGather(player, data);
            }
        }
    }

    /**
     * A chant or a gather does not follow its soul into another world, nor a smoke or a shield: a portal keeps the same
     * soul, so all are dropped here, the gather's orb with them.
     */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && SoulLogic.hasSoul(player)) {
            SoulData data = SoulLogic.get(player);
            data.kidoChant = null;
            dropGather(player, data);
            data.kidoSmoke = null;
            data.kidoShield = null;
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
    public static SimpleParticleType particleOf(KidoSpell.Look look) {
        return look.particle().flatMap(BuiltInRegistries.PARTICLE_TYPE::getOptional).orElse(null)
                instanceof SimpleParticleType particle ? particle : null;
    }

    /**
     * Tells the player a refusal on the action bar, in grey, unless it is a silent one: the spell's title and the level
     * it needs for a level, and its title for reiryoku, for a misfire and for a gather scattered.
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
        } else if (spell != null && (refusal == KidoLogic.Refusal.TOO_WEAK || refusal == KidoLogic.Refusal.MISFIRED
                || refusal == KidoLogic.Refusal.SCATTERED)) {
            line = Component.translatable(key, spell.title());
        } else {
            line = Component.translatable(key);
        }
        player.displayClientMessage(line.withStyle(ChatFormatting.GRAY), true);
    }
}
