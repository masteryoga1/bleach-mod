package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Vector3f;

/**
 * An Arrancar's Pesquisa, its sense for reiatsu, sealed or released. Held still on the Devour key for a moment
 * (canon has its users settle into stillness to sharpen it, Ch. 199), it sends out a pulse that feels every
 * soul and spirit about it, outlines them through walls for the Arrancar alone for a few seconds, and says how
 * many it found, where the strongest is and how strong that one is beside it. It is a trained skill, not a
 * gift of the form (Ch. 191), so it reaches further with Reiatsu Control, which it trains, and a quarter
 * further in Resurrección. It is no Hollow sight: it brings no night vision and shows no spirit the Arrancar
 * could not see already.
 *
 * <p>The press and letting go come through {@link DevourEvents#onKey}, and {@link ArrancarEvents#tick} keeps
 * the focus. Letting go, or a step of more than a block, ends it for free; the reiryoku is paid when the
 * pulse goes out, and the cooldown starts then. Mundane creatures, the caster, players in creative or
 * spectator mode and an Arrancar fresh from a Sonído ({@link Sonido#unsensed}) are not felt, nor is a soul
 * with a block that undoes reiryoku, or the Shakonmaku, between ({@link SpiritClip}), though stone hides
 * nothing. The focus and the cooldown are transients in the soul, so nothing is saved. Everything takes a
 * {@link Player}, so game tests can use mock players; the milestone and the outlines need a real one.
 */
public final class Pesquisa {
    /** The most souls one pulse outlines: the nearest. */
    public static final int MAX_MARKS = 32;

    private static final String MESSAGE = "message.bleach.pesquisa.";
    /** How far the caster may drift from where it began, squared: a step of more than a block breaks it. */
    private static final double STILL_DISTANCE_SQR = 1.0;
    /** The least reiryoku a pulse may leave, as for Shunpo. */
    private static final float MIN_LEFT = 0.5F;
    /** Closer than this across, a soul mostly above or below is told so rather than by the compass. */
    private static final double OVERHEAD = 2.0;
    /** Distances are told to the nearest so many blocks. */
    private static final long DISTANCE_STEP = 5L;
    private static final int RING_POINTS = 24;
    private static final double RING_RADIUS = 1.5;
    private static final float RING_DUST_SIZE = 1.0F;
    private static final float VOLUME = 0.6F;

    /** Why a press sent no focus out. Each is told on the action bar ({@link #messageKey}). */
    public enum Refusal {
        /** The last pulse went out too short a while ago. */
        COOLDOWN,
        /** Paying for a pulse would leave too little reiryoku. */
        TOO_WEAK;

        /** {@code message.bleach.pesquisa.<name>}. */
        public String messageKey() {
            return MESSAGE + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How the strongest soul a pulse felt stands beside the caster, by the ratio of their reiatsu. */
    public enum Strength {
        FAR_WEAKER, WEAKER, EQUAL, STRONGER, FAR_STRONGER;

        /** The band of this ratio of the other's reiatsu to the caster's: below 0.5, 0.9, to 1.1, 2, and above. */
        public static Strength of(float ratio) {
            if (ratio < 0.5F) {
                return FAR_WEAKER;
            }
            if (ratio < 0.9F) {
                return WEAKER;
            }
            if (ratio <= 1.1F) {
                return EQUAL;
            }
            // Past 2, or not a number at all (nothing to weigh it by).
            return ratio <= 2.0F ? STRONGER : FAR_STRONGER;
        }

        /** {@code message.bleach.pesquisa.strength.<name>}. */
        public String messageKey() {
            return MESSAGE + "strength." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a pulse found: its reach, the souls it felt (nearest first) and the strongest of them. */
    public record Reading(double range, List<LivingEntity> sensed, @Nullable LivingEntity strongest,
                          float strongestReiatsu) {
        public Reading {
            sensed = List.copyOf(sensed);
        }
    }

    private Pesquisa() {
    }

    // ---- The numbers ----

    /**
     * How far this soul's pulse reaches, in blocks: the base reach and more for each Reiatsu Control level, a
     * quarter more in Resurrección, but never past the most. The release is known only on the server.
     */
    public static double range(SoulData data) {
        double range = BleachConfig.get(BleachConfig.PESQUISA_RANGE)
                + BleachConfig.get(BleachConfig.PESQUISA_RANGE_PER_LEVEL) * data.level(Stat.REIATSU_CONTROL);
        if (ArrancarLogic.released(data)) {
            range *= BleachConfig.get(BleachConfig.PESQUISA_RELEASED_MULTIPLIER);
        }
        return Math.min(BleachConfig.get(BleachConfig.PESQUISA_MAX_RANGE), range);
    }

    /** The reiryoku a pulse costs: a share of the maximum, but never less than the least cost. */
    public static float cost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.PESQUISA_MIN_COST),
                BleachConfig.get(BleachConfig.PESQUISA_COST_FRACTION) * data.maxReiryoku());
    }

    /** The ticks between two pulses. */
    public static int cooldownTicks() {
        return BleachConfig.get(BleachConfig.PESQUISA_COOLDOWN_SECONDS) * 20;
    }

    /** Whether this soul is holding still for a pulse now. */
    public static boolean focusing(SoulData data) {
        return data.pesquisaFrom != null;
    }

    // ---- The key and the focus ----

    /**
     * The Devour key went down: an Arrancar begins to still itself where it stands, and the pulse goes out
     * once it has held still long enough (at once with no focus time). Nothing happens for anyone else, for a
     * dead or spectating player, or while a focus is on. The cooldown and the reiryoku are asked first, and a
     * refusal is told.
     *
     * @return the refusal, or null when the focus began (or nothing was asked of it)
     */
    @Nullable
    public static Refusal press(Player player, SoulData data, long now) {
        if (!ArrancarLogic.isArrancar(data) || !player.isAlive() || player.isSpectator() || focusing(data)) {
            return null;
        }
        Refusal refusal = refusal(data, now);
        if (refusal != null) {
            tell(player, data, refusal, now);
            return refusal;
        }
        data.pesquisaStartedAt = now;
        data.pesquisaFrom = player.position();
        if (focusTicks() <= 0) {
            pulse(player, data, now);
        } else {
            player.displayClientMessage(Component.translatable(MESSAGE + "focus").withStyle(ChatFormatting.GRAY), true);
        }
        return null;
    }

    /** The Devour key came up: a focus under way ends, silently and for free. */
    public static void release(SoulData data) {
        data.pesquisaStartedAt = HollowProgress.NEVER;
        data.pesquisaFrom = null;
    }

    /**
     * One body tick of a focusing Arrancar, from {@link ArrancarEvents#tick}. The focus ends silently for a
     * soul that died, watches as a spectator or is no longer an Arrancar, and with a word for one that moved
     * more than a block from where it began; once it has held still for the focus time, the pulse goes out.
     * Under a frozen tick rate the clock stands still, so it waits.
     */
    public static void tick(Player player, SoulData data, long now) {
        Vec3 from = data.pesquisaFrom;
        if (from == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !ArrancarLogic.isArrancar(data)) {
            release(data);
            return;
        }
        if (player.position().distanceToSqr(from) > STILL_DISTANCE_SQR) {
            release(data);
            player.displayClientMessage(Component.translatable(MESSAGE + "broken").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (now - data.pesquisaStartedAt >= focusTicks()) {
            pulse(player, data, now);
        }
    }

    /**
     * The pulse goes out, ending the focus: it is paid for (told and let go if the reiryoku has run too low
     * since the press), the cooldown starts, Reiatsu Control trains, and the caster hears and sees it go and
     * is told what it felt, with any level the training gained on the same line. A real player is also sent
     * the souls to outline, the nearest {@link #MAX_MARKS}.
     */
    static void pulse(Player player, SoulData data, long now) {
        release(data);
        float cost = cost(data);
        if (data.reiryoku() - cost < MIN_LEFT || !SoulLogic.trySpend(player, data, cost)) {
            tell(player, data, Refusal.TOO_WEAK, now);
            return;
        }
        data.lastPesquisaAt = now;
        // Felt before the training, so a level gained by this pulse widens only the next.
        Reading reading = sense(player, now);
        int level = data.level(Stat.REIATSU_CONTROL);
        SoulLogic.addStatXp(player, data, Stat.REIATSU_CONTROL, BleachConfig.get(BleachConfig.PESQUISA_TRAINING_XP));
        effects(player);
        // The action bar holds one line, so a level gained here rides on the reading's rather than replacing it.
        MutableComponent line = describe(player, reading).copy();
        if (data.level(Stat.REIATSU_CONTROL) > level) {
            line.append(" ").append(Component.translatable("message.bleach.stat_level_up",
                    Stat.REIATSU_CONTROL.displayName(), data.level(Stat.REIATSU_CONTROL)));
        }
        player.displayClientMessage(line, true);
        if (player instanceof ServerPlayer server) {
            Milestones.grant(server, Milestones.PESQUISA);
            List<Integer> ids = reading.sensed().stream().limit(MAX_MARKS).map(Entity::getId).toList();
            BleachNetwork.sendPesquisa(server, ids, BleachConfig.get(BleachConfig.PESQUISA_MARK_SECONDS) * 20);
        }
    }

    // ---- Who is felt ----

    /**
     * Whether this caster's pulse would feel this creature now. Another living one in the same world, within
     * reach (its nearest part counts for a giant), with at least the least reiatsu felt: a player only by
     * survival's rules, with a soul and not fresh from a Sonído; anything else only with a soul, or being a
     * spirit or a creature that sees them; and never with a block that undoes reiryoku or the Shakonmaku
     * ({@link SpiritClip}) between the caster's eyes and the middle of its box (or part) nearest the caster, though
     * stone hides nothing. So mundane animals and monsters are never felt.
     */
    public static boolean senses(Player caster, LivingEntity target, long now) {
        if (!SoulLogic.hasSoul(caster)) {
            return false;
        }
        double range = range(SoulLogic.get(caster));
        return senses(caster, target, now, range * range, BleachConfig.get(BleachConfig.PESQUISA_MIN_REIATSU));
    }

    /**
     * What this caster's pulse feels now (see {@link #senses}), nearest first, and the strongest of them by
     * reiatsu (the nearer of two as strong). A giant is found by any of its parts. A caster without a soul
     * feels nothing.
     */
    public static Reading sense(Player caster, long now) {
        if (!SoulLogic.hasSoul(caster)) {
            return new Reading(0.0, List.of(), null, 0.0F);
        }
        double range = range(SoulLogic.get(caster));
        double rangeSqr = range * range;
        double minReiatsu = BleachConfig.get(BleachConfig.PESQUISA_MIN_REIATSU);
        Vec3 at = caster.position();
        List<LivingEntity> sensed = SpiritProjectiles.creaturesIn(caster.level(), caster.getBoundingBox().inflate(range),
                target -> senses(caster, target, now, rangeSqr, minReiatsu));
        sensed.sort(Comparator.comparingDouble(target -> distanceSqr(at, target)));
        LivingEntity strongest = null;
        float most = 0.0F;
        for (LivingEntity target : sensed) {
            float reiatsu = SoulLogic.reiatsu(target);
            if (strongest == null || reiatsu > most) {
                strongest = target;
                most = reiatsu;
            }
        }
        return new Reading(range, sensed, strongest, most);
    }

    /**
     * The line a pulse leaves on the action bar: none felt within its reach, or how many, with the strongest's
     * rough distance, its way (by the compass, or above or below when it is nearly overhead) and how strong it
     * is beside the caster.
     */
    public static Component describe(Player caster, Reading reading) {
        long range = Math.round(reading.range());
        LivingEntity strongest = reading.strongest();
        if (reading.sensed().isEmpty() || strongest == null) {
            return Component.translatable(MESSAGE + "none", range);
        }
        double dx = strongest.getX() - caster.getX();
        double dy = strongest.getY(0.5) - caster.getY(0.5);
        double dz = strongest.getZ() - caster.getZ();
        double across = Math.sqrt(dx * dx + dz * dz);
        String way;
        if (across < OVERHEAD && Math.abs(dy) > across) {
            way = MESSAGE + (dy > 0.0 ? "above" : "below");
        } else {
            way = ShinigamiLogic.compassKey(dx, dz);
        }
        long distance = ShinigamiLogic.roundedDistance(Math.sqrt(distanceSqr(caster.position(), strongest)), DISTANCE_STEP);
        float own = SoulLogic.reiatsu(caster);
        float ratio = own > 0.0F ? reading.strongestReiatsu() / own : Float.POSITIVE_INFINITY;
        Component band = Component.translatable(Strength.of(ratio).messageKey());
        Component direction = Component.translatable(way);
        int count = reading.sensed().size();
        if (count == 1) {
            return Component.translatable(MESSAGE + "found_one", range, distance, direction, band);
        }
        return Component.translatable(MESSAGE + "found", count, range, distance, direction, band);
    }

    // ---- Helpers ----

    private static int focusTicks() {
        return BleachConfig.get(BleachConfig.PESQUISA_FOCUS_TICKS);
    }

    /** Why a pulse may not go out now, the cooldown first, or null. */
    @Nullable
    private static Refusal refusal(SoulData data, long now) {
        if (now - data.lastPesquisaAt < cooldownTicks()) {
            return Refusal.COOLDOWN;
        }
        if (data.reiryoku() - cost(data) < MIN_LEFT) {
            return Refusal.TOO_WEAK;
        }
        return null;
    }

    /** A refusal as told above the hotbar, in grey: the cooldown with its seconds left, rounded up. */
    private static void tell(Player player, SoulData data, Refusal refusal, long now) {
        MutableComponent message = refusal == Refusal.COOLDOWN
                ? Component.translatable(refusal.messageKey(), secondsLeft(data, now))
                : Component.translatable(refusal.messageKey());
        player.displayClientMessage(message.withStyle(ChatFormatting.GRAY), true);
    }

    private static long secondsLeft(SoulData data, long now) {
        long left = cooldownTicks() - (now - data.lastPesquisaAt);
        return Math.max(1L, (left + 19L) / 20L);
    }

    private static boolean senses(Player caster, LivingEntity target, long now, double rangeSqr, double minReiatsu) {
        if (target == caster || !target.isAlive() || target.level() != caster.level()) {
            return false;
        }
        if (target instanceof Player player) {
            if (!HollowPlayerLogic.survivalRules(player) || !SoulLogic.hasSoul(player)
                    || Sonido.unsensed(SoulLogic.get(player), now)) {
                return false;
            }
        } else if (!SoulLogic.hasSoul(target) && !target.getType().is(SoulLogic.SPIRITS)
                && !target.getType().is(SoulLogic.SPIRIT_SIGHTED)) {
            return false;
        }
        return SoulLogic.reiatsu(target) >= minReiatsu && distanceSqr(caster.position(), target) <= rangeSqr
                && !walledOff(caster, target);
    }

    /**
     * Whether a block that undoes reiryoku, or the Shakonmaku, lies on the straight line from the caster's eyes to the
     * middle of the soul's box nearest the caster's feet. Asked last, as the costliest rule: a walk of the cells on the
     * line, loading none.
     */
    private static boolean walledOff(Player caster, LivingEntity target) {
        Vec3 soul = nearestBox(caster.position(), target).getCenter();
        return SpiritClip.firstOn(caster.level(), caster.getEyePosition(), soul) != null;
    }

    /** The squared distance from this point to the nearest of the creature's box and its parts' boxes. */
    private static double distanceSqr(Vec3 at, LivingEntity target) {
        return nearestBox(at, target).distanceToSqr(at);
    }

    /** The nearest to this point of the creature's box and its parts' boxes. */
    private static AABB nearestBox(Vec3 at, LivingEntity target) {
        AABB nearest = target.getBoundingBox();
        PartEntity<?>[] parts = target.isMultipartEntity() ? target.getParts() : null;
        if (parts != null) {
            for (PartEntity<?> part : parts) {
                if (part.getBoundingBox().distanceToSqr(at) < nearest.distanceToSqr(at)) {
                    nearest = part.getBoundingBox();
                }
            }
        }
        return nearest;
    }

    /** A ring of pale dust about the caster's waist and the pulse's sound, for those who see spirits. */
    private static void effects(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.85F, 0.9F, 1.0F), RING_DUST_SIZE);
        double y = player.getY(0.5);
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = 2.0 * Math.PI * i / RING_POINTS;
            SpiritSenses.particles(level, dust, player.getX() + RING_RADIUS * Math.cos(angle), y,
                    player.getZ() + RING_RADIUS * Math.sin(angle), 1, 0.0, 0.0, 0.0, 0.0);
        }
        SpiritSenses.play(player, ModSounds.PESQUISA.get(), SoundSource.PLAYERS, VOLUME, 1.0F, false);
    }
}
