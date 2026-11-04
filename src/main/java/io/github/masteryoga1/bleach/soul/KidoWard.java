package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.registry.BleachTags;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Bakudō of {@link KidoSpell.Shape#SHIELD}, as #39 Enkōsen is: a round shield of reiryoku held before its wearer for its
 * ward's {@code seconds}, taking the blows that come at it from the front. Its strength is the spell's power at the share
 * it was cast with ({@link KidoLogic#power}), and it covers a blow whose source stands within half the spell's
 * {@code angle} of where the wearer's head faces, measured across the ground ({@link #faces}). A blow it covers is weighed
 * as it would land before armour, after the reiatsu gap and every multiplier: taken whole while it costs less than what is
 * left, else the shield breaks and only what the blow has over lands. As a body's own frames would, a blow within
 * {@link #MOMENT_TICKS} of the last one the shield took costs only what it has over that one ({@link #absorb}).
 *
 * <p>What passes it ({@link #covers}): magic, fire, falls and all else a raised shield lets by
 * ({@code #minecraft:bypasses_shield}), an art that rises about its victim ({@link BleachTags#PASSES_WARDS}), a blow from
 * no place, a Visored's inner Hollow, every blow while the wearer's arms are bound, and the binds, which deal none. A
 * raised shield in hand takes what it blocks first. The engines that stop at a raised shield (a Cero, a Hadō beam, the
 * Getsuga Tenshō, the Heilig Pfeil) stop at this one too, and so does a piercing arrow, which a raised shield lets by.
 *
 * <p>It ends at its time, silently; under a blow that breaks it, or once the wearer's arms are bound, with the sound of
 * breaking glass and a line on a player wearer's action bar; and silently once the wearer may no longer cast (dead, a
 * spectator, held by Negación, untrained, taken by its Hollow or its power lent), in another world, at death and with a
 * reset. Those who see spirits see it as a spinning ring of motes in the spell's colour before the eyes, and hear the
 * blows it takes. Server only, and never saved: a transient in the soul. A mob wearer never ticks, so its shield shows
 * nothing after it is raised and ends by its time alone, which {@link #covers} and {@link #up} read.
 */
public final class KidoWard {
    /** Ticks after a blow the shield took in which another costs only what it has over that one: a body's frames. */
    public static final int MOMENT_TICKS = 10;
    /** Ticks between the ring's draws, how many motes draw it, and how much further round it is turned each time. */
    private static final int RING_TICKS = 3;
    private static final int RING_MOTES = 12;
    private static final double RING_TURN_DEGREES = 20.0;
    /** How far ahead of the eyes the ring stands, and how far below them. */
    private static final double RING_AHEAD = 0.7;
    private static final double RING_BELOW = 0.35;
    private static final float MOTE_SIZE = 0.8F;
    /** How many of the spell's particles flare about the ring as it is raised, and how fast they drift. */
    private static final int FLARE = 6;
    private static final double FLARE_SPEED = 0.02;
    /** A hundredth of a block, squared: a source closer than that across the ground is above or below, not before. */
    private static final double NEAR_SQR = 1.0E-4;
    /** How loud and how high the clang of a blow the shield takes is, and the breaking glass of one that breaks it. */
    private static final float BLOCK_VOLUME = 0.8F;
    private static final float BLOCK_PITCH = 1.3F;
    private static final float BREAK_VOLUME = 1.0F;
    private static final float BREAK_PITCH = 1.4F;
    private static final float TICKS_PER_SECOND = 20.0F;
    /** What a player wearer reads as its shield breaks: the spell's title. */
    private static final String BROKEN_KEY = "message.bleach.kido.ward_broken";

    private KidoWard() {
    }

    /**
     * A round shield standing before its wearer. Transient: never saved or sent.
     *
     * @param spell      the spell as it was raised, so a data pack cannot change it halfway
     * @param strength   what it could take when raised
     * @param left       what it can take still
     * @param endsAt     the game time it is gone
     * @param lastBlowAt the game time of the last blow it took, for the moment after it ({@link #absorb}); a moment
     *                   before the raise when it has taken none
     * @param lastBlow   the biggest blow it took in that moment, or 0
     */
    public record Shield(KidoSpell spell, float strength, float left, long endsAt, long lastBlowAt, float lastBlow) {
    }

    /**
     * What came of one blow on a shield: the shield as it stands after it, or null when the blow broke it, and how much
     * of the blow passes it to land, 0 for one taken whole or one that just empties it.
     */
    public record Absorbed(@Nullable Shield shield, float passes) {
    }

    /**
     * The shield's listener, at the lowest priority, so it weighs a blow as it will land after the reiatsu gap and every
     * multiplier, and never sees one an earlier listener cancelled.
     */
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, KidoWard::onIncomingDamage);
    }

    /**
     * Raises this spell's shield before this creature now, in place of any it held: its strength the spell's power at
     * this share, standing for the ward's {@code seconds}, with no blow taken yet. The hand is thrown up, and those who
     * see spirits hear the spell's sound and see its particle flare about the ring, which is drawn at once. Takes any
     * creature with a soul, so a game test can raise one before a mob that a beam can strike; only a player's ticks
     * draw the ring after that.
     */
    public static void raise(LivingEntity wearer, SoulData data, KidoSpell spell, float share, long now) {
        float strength = KidoLogic.power(spell, data, share);
        long endsAt = now + Math.round(spell.effect().ward().seconds() * TICKS_PER_SECOND);
        // A whole moment before the raise, so the first blow is never taken for one in the moment of another.
        Shield shield = new Shield(spell, strength, strength, endsAt, now - MOMENT_TICKS, 0.0F);
        data.kidoShield = shield;
        if (!(wearer.level() instanceof ServerLevel level)) {
            return;
        }
        wearer.swing(InteractionHand.MAIN_HAND, true);
        KidoSpell.Look look = spell.look();
        Vec3 centre = ringCentre(wearer);
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                centre.x, centre.y, centre.z, sound, SoundSource.PLAYERS, 1.0F, look.pitch(), false));
        SimpleParticleType particle = KidoEvents.particleOf(look);
        if (particle != null) {
            Vec3 facing = facing(wearer);
            double radius = spell.effect().radius();
            for (int i = 0; i < FLARE; i++) {
                Vec3 at = onRing(centre, facing, radius, Math.PI * 2.0 * i / FLARE);
                SpiritSenses.particles(level, particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, FLARE_SPEED);
            }
        }
        drawRing(level, wearer, shield, now);
    }

    /**
     * A warding player's tick. The shield goes at its time, silently; breaks, with the sound and the line, once the
     * wearer's arms are bound, as the hand that holds it is tied; and goes silently once the wearer may no longer cast
     * by name for any other reason ({@link KidoLogic#mayCast}). Else every {@link #RING_TICKS} ticks it is drawn for
     * those who see spirits. Under a frozen tick rate the clock stands still, so it stands too. Called by the tick
     * listener and by tests.
     */
    public static void tick(Player wearer, SoulData data, long now) {
        Shield shield = data.kidoShield;
        if (shield == null) {
            return;
        }
        if (now >= shield.endsAt()) {
            data.kidoShield = null;
            return;
        }
        KidoLogic.Refusal refusal = KidoLogic.mayCast(wearer, data, now);
        if (refusal == KidoLogic.Refusal.BOUND) {
            shatter(wearer, data, shield.spell());
            return;
        }
        if (refusal != null) {
            data.kidoShield = null;
            return;
        }
        // By the time left: a frozen clock may draw it every tick, which is only a look.
        if ((shield.endsAt() - now) % RING_TICKS == 0 && wearer.level() instanceof ServerLevel level) {
            drawRing(level, wearer, shield, now);
        }
    }

    /** The shield this soul holds up, or null: as it was left, even past its time until its tick drops it. */
    @Nullable
    public static Shield shield(SoulData data) {
        return data.kidoShield;
    }

    /** Whether this soul holds a shield up at this game time: one is raised and its time is not yet over. */
    public static boolean up(SoulData data, long now) {
        Shield shield = data.kidoShield;
        return shield != null && now < shield.endsAt();
    }

    /**
     * Whether this creature's shield stands in this blow's way, the first that fails ruling it out: a soul with a shield
     * up at its level's game time; a blow of no kind that passes a raised shield or a Kidō ward; one from a place; not
     * the blow of a Visored's inner Hollow, which is the host's own fight; arms not bound; and the place within the arc
     * the shield faces ({@link #faces}). Cheap and changes nothing, so the engines that stop at a shield ask it before
     * each blow, as a blow may break the shield. A piercing arrow is not let by: the shield is reiryoku, not an item.
     */
    public static boolean covers(LivingEntity target, DamageSource source) {
        if (!SoulLogic.hasSoul(target)) {
            return false;
        }
        Shield shield = SoulLogic.get(target).kidoShield;
        if (shield == null || target.level().getGameTime() >= shield.endsAt()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(BleachTags.PASSES_WARDS)) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null || source.getEntity() instanceof InnerHollow || BakudoEffect.bound(target)) {
            return false;
        }
        return faces(target, from, shield.spell().effect().angle());
    }

    /**
     * Whether a point lies before this creature, within half of this arc (in degrees) of where its head faces, both
     * measured across the ground, as a raised shield's own test is but with the arc given. A point within a hundredth
     * of a block of it across the ground, straight above or below or inside it, is not before it.
     */
    public static boolean faces(LivingEntity wearer, Vec3 from, float angle) {
        double dx = from.x - wearer.getX();
        double dz = from.z - wearer.getZ();
        double lengthSqr = dx * dx + dz * dz;
        if (lengthSqr < NEAR_SQR) {
            return false;
        }
        Vec3 facing = facing(wearer);
        return (dx * facing.x + dz * facing.z) / Math.sqrt(lengthSqr) >= Math.cos(Math.toRadians(angle / 2.0));
    }

    /**
     * One blow of this amount on this shield now. Unless the blow passes the moment (a volley's arrows, which pass a
     * body's frames too), one within {@link #MOMENT_TICKS} of the last blow the shield took costs only what it has over
     * the biggest blow of that moment, and nothing when it has none; any other costs its whole amount and begins a new
     * moment. A cost less than what is left is taken, and nothing passes; any other breaks the shield, and what the cost
     * has over what was left passes, nothing for a blow that just empties it. Changes nothing.
     */
    public static Absorbed absorb(Shield shield, float amount, long now, boolean passesTheMoment) {
        boolean recent = !passesTheMoment && now - shield.lastBlowAt() < MOMENT_TICKS;
        float cost = recent ? Math.max(0.0F, amount - shield.lastBlow()) : amount;
        if (cost < shield.left()) {
            return new Absorbed(new Shield(shield.spell(), shield.strength(), shield.left() - cost,
                    shield.endsAt(), recent ? shield.lastBlowAt() : now,
                    recent ? Math.max(shield.lastBlow(), amount) : amount), 0.0F);
        }
        return new Absorbed(null, Math.max(0.0F, cost - shield.left()));
    }

    // ---- Listener ----

    /**
     * A blow of some harm on a creature whose shield covers it ({@link #covers}), and that a raised shield in hand does
     * not already block, so the item alone takes what it stops. Taken whole, it is cancelled: no harm, no frames, no
     * knockback and none of what a landed blow does (a chant's break, a mask's crack, training), and those who see
     * spirits hear it ring on the shield. One that breaks the shield lands only what it has over, and is cancelled when
     * that is nothing. Fired only on the server.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity wearer = event.getEntity();
        DamageSource source = event.getSource();
        float amount = event.getAmount();
        if (wearer.level().isClientSide() || !(amount > 0.0F) || !covers(wearer, source)
                || wearer.isDamageSourceBlocked(source)) {
            return;
        }
        SoulData data = SoulLogic.get(wearer);
        // Covered, so it stands.
        Shield shield = data.kidoShield;
        Absorbed absorbed = absorb(shield, amount, wearer.level().getGameTime(),
                source.is(DamageTypeTags.BYPASSES_COOLDOWN));
        if (absorbed.shield() != null) {
            data.kidoShield = absorbed.shield();
            SpiritSenses.play(wearer, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, BLOCK_VOLUME, BLOCK_PITCH, false);
            event.setCanceled(true);
            return;
        }
        shatter(wearer, data, shield.spell());
        if (absorbed.passes() > 0.0F) {
            event.setAmount(absorbed.passes());
        } else {
            event.setCanceled(true);
        }
    }

    // ---- Helpers ----

    /**
     * The shield breaks: it is gone, those who see spirits hear glass break at the wearer, and a player wearer reads
     * that it broke on its action bar. Nothing on a client.
     */
    private static void shatter(LivingEntity wearer, SoulData data, KidoSpell spell) {
        data.kidoShield = null;
        SpiritSenses.play(wearer, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, BREAK_VOLUME, BREAK_PITCH, false);
        if (wearer instanceof Player player && !player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable(BROKEN_KEY, spell.title()).withStyle(ChatFormatting.GRAY),
                    true);
        }
    }

    /**
     * The shield as those who see spirits see it: a ring of motes in the spell's colour, as wide as the spell's radius,
     * standing square to where the head faces, a little ahead of the eyes and below them, and turned a little further
     * round at each draw, so it spins.
     */
    private static void drawRing(ServerLevel level, LivingEntity wearer, Shield shield, long now) {
        KidoSpell spell = shield.spell();
        DustParticleOptions dust = new DustParticleOptions(Vec3.fromRGB24(spell.look().colour()).toVector3f(), MOTE_SIZE);
        Vec3 centre = ringCentre(wearer);
        Vec3 facing = facing(wearer);
        double radius = spell.effect().radius();
        double turn = Math.toRadians(RING_TURN_DEGREES * ((shield.endsAt() - now) / RING_TICKS));
        for (int i = 0; i < RING_MOTES; i++) {
            Vec3 at = onRing(centre, facing, radius, turn + Math.PI * 2.0 * i / RING_MOTES);
            SpiritSenses.particles(level, dust, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Where the head faces, across the ground. */
    private static Vec3 facing(LivingEntity wearer) {
        return Vec3.directionFromRotation(0.0F, wearer.getYHeadRot());
    }

    /** The ring's middle: a little ahead of the eyes, the way the head faces, and a little below them. */
    private static Vec3 ringCentre(LivingEntity wearer) {
        return wearer.getEyePosition().add(facing(wearer).scale(RING_AHEAD)).subtract(0.0, RING_BELOW, 0.0);
    }

    /**
     * A point on a ring of this radius about this middle, standing square to this facing across the ground: this far
     * round, in radians, from the side across the facing, going up.
     */
    private static Vec3 onRing(Vec3 centre, Vec3 facing, double radius, double angle) {
        double across = Math.cos(angle) * radius;
        // Facing (x, z), so the side across it is (-z, x).
        return new Vec3(centre.x - facing.z * across, centre.y + Math.sin(angle) * radius, centre.z + facing.x * across);
    }
}
