package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
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
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Casting Kidō. The Kidō key (U) casts the chosen spell by name alone, Eishōhaki in canon's terms, and Sneak and the
 * key choose the next spell the soul's Kidō level allows; the action bar names it. A cast is one whole act within a
 * key press: it is paid for, the hands are busy for the spell's cooldown, the spell's shape strikes, the caster calls
 * it out on its own action bar and Kidō trains, one xp more for a strike. The rules are in {@link KidoLogic}.
 *
 * <p>A Hadō of {@link KidoSpell.Shape#BEAM} strikes through the Cero's engine as a Bala does ({@link CeroBeam#fire}
 * with a quiet {@link CeroBeam.Kind} of {@link #HADO}), so it keeps every rule a Cero keeps on PvP, teams, pets,
 * shields and the reiatsu gap, and it breaks no blocks. Its trace, sound and particles are seen and heard only by
 * those who see spirits; what it does to a creature anyone can see. Creative pays nothing, waits for nothing and
 * trains nothing. One art at a time: no Kidō while a Getsuga Tenshō or a Cero gathers.
 *
 * <p>The keys come from {@link KidoKeyPayload}; nothing here listens to an event, and the cooldown is a transient in
 * the soul, so a respawn or a relog starts it over. Everything takes a {@link Player}, so game tests can use mock
 * players.
 */
public final class KidoEvents {
    public static final ResourceKey<DamageType> HADO = ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("hado"));
    /** Ticks a spell's trace stays, fading. */
    public static final int TRACE_TICKS = 6;
    private static final int HAND_PARTICLES = 6;
    private static final int IMPACT_PARTICLES = 10;
    private static final double HAND_SPREAD = 0.08;
    private static final double IMPACT_SPREAD = 0.25;
    private static final double PARTICLE_SPEED = 0.02;

    private KidoEvents() {
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
            // RELEASE and CANCEL wait for a spell that is held (6b's Raikōhō), SELECT for the Kidō page (6e).
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
     * that is still to come. A soul kept from casting only by its Hollow or its lent power may still choose.
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
        return null;
    }

    // ---- The cast ----

    /**
     * Casts this spell in this form now, if it may, the first check that fails winning: whether the soul may cast
     * ({@link KidoLogic#mayCast}), a server world and a spell that does something, the Kidō level
     * ({@link KidoLogic#gate}), the cooldown, a Getsuga Tenshō or a Cero gathering (creative too), and enough reiryoku
     * to leave {@link KidoLogic#MIN_LEFT}. Then it is paid for and the hands are busy for the spell's cooldown, its
     * shape strikes, the call goes on the caster's action bar, and Kidō trains, with the strike bonus when a creature
     * was struck; when a Hollow was among those struck, an unbound Asauchi leans to kidō. Creative pays, waits, trains
     * and leans nothing.
     *
     * <p>The one entry for every way of casting. Tells nothing; the caller does. Returns the refusal, or null when
     * cast. Takes the spell itself, not its holder, so a game test can cast a spell built in code.
     */
    @Nullable
    public static KidoLogic.Refusal cast(Player player, KidoSpell spell, KidoLogic.Form form, long now) {
        SoulData data = SoulLogic.get(player);
        KidoLogic.Refusal refusal = KidoLogic.mayCast(player, data, now);
        if (refusal != null) {
            return refusal;
        }
        if (!(player.level() instanceof ServerLevel level) || !spell.castable()) {
            return KidoLogic.Refusal.UNABLE;
        }
        int kido = data.level(Stat.KIDO);
        refusal = KidoLogic.gate(spell, form, kido);
        if (refusal != null) {
            return refusal;
        }
        boolean creative = player.isCreative();
        if (!creative && !KidoLogic.ready(data, now)) {
            return KidoLogic.Refusal.COOLDOWN;
        }
        // One art at a time, as Bala and the Getsuga Tenshō keep it: the reiatsu is already gathering elsewhere.
        if (data.isGathering() || HollowCero.charging(data) || GranReyCero.charging(data)) {
            return KidoLogic.Refusal.GATHERING;
        }
        if (!creative) {
            float cost = KidoLogic.cost(spell, data);
            if (data.reiryoku() - cost < KidoLogic.MIN_LEFT || !SoulLogic.trySpend(player, data, cost)) {
                return KidoLogic.Refusal.TOO_WEAK;
            }
            data.kidoReadyAt = now + spell.cooldownTicks();
        }
        List<LivingEntity> struck = switch (spell.shape()) {
            case BEAM -> beam(level, player, data, spell, KidoLogic.share(spell, form, kido)).struck();
            // Never reached: a spell of no shape is not castable, and was refused above.
            case NONE -> List.of();
        };
        player.displayClientMessage(spell.call().copy().withStyle(ChatFormatting.GRAY), true);
        if (!creative) {
            // After the call, so a level-up line takes its place on the action bar.
            SoulLogic.addStatXp(player, data, Stat.KIDO, KidoLogic.xp(spell, !struck.isEmpty()));
            if (struck.stream().anyMatch(target -> target instanceof Hollow)) {
                ZanpakutoEvents.lean(player, data, ZanpakutoType.KIDO, 1, now);
            }
        }
        return null;
    }

    /**
     * The BEAM shape: struck at once from just ahead of the eyes where the caster looks, at the form's share of its
     * damage and push (not of its reach, width or blast), stopping after the spell's pierce, breaking no blocks. Its
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
        CeroBeam.Shot shot = CeroBeam.fire(level, player, bolt, from, player.getLookAngle(), spec, kind);
        CeroBeam.trace(level, player, shot, spec.radius(), look.colour(), TRACE_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
        look.sound().flatMap(BuiltInRegistries.SOUND_EVENT::getOptional).ifPresent(sound -> SpiritSenses.play(level,
                from.x, from.y, from.z, sound, SoundSource.PLAYERS, 1.0F, look.pitch(), false));
        // Only a particle that needs no options of its own can be named by id alone.
        if (look.particle().flatMap(BuiltInRegistries.PARTICLE_TYPE::getOptional).orElse(null)
                instanceof SimpleParticleType particle) {
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

    // ---- Helpers ----

    /**
     * Tells the player a refusal on the action bar, in grey, unless it is a silent one: the spell's title and the level
     * it needs for a level, and its title for reiryoku.
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
        } else {
            line = Component.translatable(key);
        }
        player.displayClientMessage(line.withStyle(ChatFormatting.GRAY), true);
    }
}
