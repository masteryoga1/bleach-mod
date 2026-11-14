package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.entity.Gillian;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Negación, the Gillian's pillar of light (Ch. 178): it holds a comrade untouchable and lifts it away into a
 * Garganta. A Gillian mob calls it down on a beaten Hollow ally ({@link Gillian#tryRescue}), and a Gillian-form
 * Hollow player calls it down with the Technique key on a Hollow, an Arrancar or a team-mate it looks at, for a
 * share of its reiryoku and a short wait. A mob is lifted at once; a player is offered the light and must step
 * into it by using it, for the light never takes the unwilling.
 *
 * <p>The light itself, its rise and its carry-off are {@link NegacionLight}. Being held is riding a
 * {@link HoldingVehicle}, that light or an Espada's Caja Negación, and {@link #held} is the one place that knows
 * it, on both sides. The held cannot be hurt but by what passes every protection (the void and {@code /kill}),
 * and cannot hurt, use or build: their blows and shots are cancelled here, as are a held player's attacks, item
 * and block use, dealings with creatures and block breaking, and the Hollow arts refuse them in their own checks.
 * A player held at logging out is set down first, before it is saved. The cooldown is a transient in the soul, so
 * nothing is saved; everything but the milestone takes a {@link Player}, so game tests can use mock players.
 */
public final class Negacion {
    /** The tallest and widest creature the light can hold, in blocks. */
    public static final float MAX_HEIGHT = 4.0F;
    public static final float MAX_WIDTH = 3.0F;
    /** What a held player is told when its attack or use is cancelled. */
    public static final String HELD = "message.bleach.negacion.held";

    private static final String OFFERED = "message.bleach.negacion.offered";
    private static final String OFFER_SENT = "message.bleach.negacion.offer_sent";
    /** An offer this close to a creature is already waiting for it. */
    private static final double OFFER_NEAR = 2.0;
    /** How far to either side of the look line a creature may be and still be aimed at, in blocks. */
    private static final float AIM_MARGIN = 0.5F;

    /** Why a Negación was not called down. Each is told to the caster. */
    public enum Refusal {
        COOLDOWN, TOO_WEAK, NO_TARGET, NOT_ALLY, TOO_BIG, BUSY;

        /** {@code message.bleach.negacion.<name>}. */
        public String messageKey() {
            return "message.bleach.negacion." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A vehicle that holds its rider untouchable: Negación's light or the Caja Negación. */
    public interface HoldingVehicle {
        Level level();

        /** Tells this held player, above the hotbar and at most once a second, that it can touch nothing. Server only. */
        void tellHeld(Player player);

        /** Lets this held player off where it was found, falling slowly, and ends the hold. Server only. */
        void setDown(Player player);
    }

    private Negacion() {
    }

    /**
     * The damage cancel goes first of all, before the blows of the unaware are cancelled or any blow is weighed;
     * the cancels of a held player's doings at the usual priority.
     */
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, Negacion::onIncomingDamage);
        bus.addListener(Negacion::onAttack);
        bus.addListener(Negacion::onRightClickItem);
        bus.addListener(Negacion::onRightClickBlock);
        bus.addListener(Negacion::onEntityInteract);
        bus.addListener(Negacion::onEntityInteractAt);
        bus.addListener(Negacion::onBreakBlock);
        bus.addListener(Negacion::onPearlLands);
        bus.addListener(Negacion::onLoggedOut);
        bus.addListener(Negacion::onChangedDimension);
    }

    // ---- The held state ----

    /** The one place the held state is known: riding a {@link HoldingVehicle}, a light or a Caja. Both sides. */
    public static boolean held(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof HoldingVehicle;
    }

    /** The light that holds this creature, or null for one not held. */
    @Nullable
    public static NegacionLight lightOf(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof NegacionLight light ? light : null;
    }

    /** Whatever holds this creature, a light or a Caja, or null for one not held. */
    @Nullable
    public static HoldingVehicle holderOf(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof HoldingVehicle holder ? holder : null;
    }

    /**
     * The shape and state rules every Negación keeps, for mobs and players alike: a living creature not held
     * already, no taller than {@link #MAX_HEIGHT} and no wider than {@link #MAX_WIDTH}. A Hollow must be no
     * Gillian (it leaves by its own retreat) and not held in jaws, leaving, dissolving or held by a bait; a
     * player must be no spectator and no giant; an Arrancar NPC must not be leaving through its Garganta.
     */
    public static boolean mayHold(LivingEntity target) {
        if (!target.isAlive() || held(target) || target.getBbHeight() > MAX_HEIGHT || target.getBbWidth() > MAX_WIDTH) {
            return false;
        }
        if (target instanceof Hollow hollow) {
            return !(hollow instanceof Gillian) && !hollow.isSeized() && !hollow.isDeparting() && !hollow.isPurifying()
                    && !hollow.isBaitHeld();
        }
        if (target instanceof Player player) {
            return !player.isSpectator() && !MenosBody.isGiant(player);
        }
        if (target instanceof ArrancarNpc npc) {
            return !npc.isDeparting();
        }
        return true;
    }

    // ---- The numbers ----

    /** Whether this soul casts Negación: a Gillian-form Hollow, while the server lets players cast it. */
    public static boolean casts(SoulData data) {
        return HollowTier.of(data) == HollowTier.GILLIAN && BleachConfig.get(BleachConfig.NEGACION_PLAYER_CAST);
    }

    /** The reiryoku a Negación costs: a share of the maximum, but never less than the least cost. */
    public static float cost(SoulData data) {
        return (float) Math.max(BleachConfig.get(BleachConfig.NEGACION_PLAYER_MIN_COST),
                BleachConfig.get(BleachConfig.NEGACION_PLAYER_COST_FRACTION) * data.maxReiryoku());
    }

    /** Whole seconds, rounded up, until this soul may call down Negación again. */
    public static long secondsToReady(SoulData data, long now) {
        return Math.max(1L, (long) Math.ceil((data.lastNegacionAt + cooldownTicks() - now) / 20.0));
    }

    // ---- The cast ----

    /**
     * The Technique key went down for a Hollow (from {@link ArrancarEvents#onKey}): a Negación is called down
     * where it looks, and a refusal is told above the hotbar, the cooldown with the seconds left.
     */
    public static void press(Player caster, SoulData data, long now) {
        Refusal refusal = cast(caster, data, now);
        if (refusal == null) {
            return;
        }
        MutableComponent message = refusal == Refusal.COOLDOWN
                ? Component.translatable(refusal.messageKey(), secondsToReady(data, now))
                : Component.translatable(refusal.messageKey());
        caster.displayClientMessage(message.withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Calls down a Negación on the creature the caster looks at ({@link #aimed}). Nothing at all, and nothing
     * told, for a soul that does not cast it, a dead or spectating caster, or one not in a server world; then
     * the cooldown, then everything {@link #castOn} asks.
     */
    @Nullable
    public static Refusal cast(Player caster, SoulData data, long now) {
        if (!casts(data) || !caster.isAlive() || caster.isSpectator() || !(caster.level() instanceof ServerLevel)) {
            return null;
        }
        if (now - data.lastNegacionAt < cooldownTicks()) {
            return Refusal.COOLDOWN;
        }
        return castOn(caster, data, aimed(caster), now);
    }

    /**
     * Calls down a Negación on this creature, the first check that fails winning: the target
     * ({@link #targetRefusal}), then enough reiryoku. It is paid for and the cooldown starts. A player is offered
     * the light and the caster told it fell; anything else is lifted at once. A light that could not come down
     * costs nothing and starts no cooldown, and the target counts as busy.
     */
    @Nullable
    public static Refusal castOn(Player caster, SoulData data, @Nullable LivingEntity target, long now) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return null;
        }
        Refusal refusal = targetRefusal(caster, target);
        if (refusal != null) {
            return refusal;
        }
        float cost = cost(data);
        float before = data.reiryoku();
        if (before - cost < ZanpakutoLogic.RESERVE || !SoulLogic.trySpend(caster, data, cost)) {
            return Refusal.TOO_WEAK;
        }
        long lastCast = data.lastNegacionAt;
        data.lastNegacionAt = now;
        NegacionLight light;
        if (target instanceof Player player) {
            light = offer(level, player, caster.getDisplayName());
            if (light != null) {
                caster.displayClientMessage(Component.translatable(OFFER_SENT, player.getDisplayName())
                        .withStyle(ChatFormatting.GRAY), true);
            }
        } else {
            light = lift(level, target);
        }
        if (light == null) {
            data.setReiryoku(before);
            data.lastNegacionAt = lastCast;
            return Refusal.BUSY;
        }
        if (caster instanceof ServerPlayer server) {
            Milestones.grant(server, Milestones.NEGACION);
        }
        return null;
    }

    /**
     * Why this creature may not be given a Negación by this caster, or null when it may, the first that applies
     * winning: none to give it to (none, dead or in another world); too big (taller or wider than the light, a
     * Gillian or a giant player); busy (held, with an offer already waiting by it, or a creature the light may
     * not hold now); and not an ally. A player is an ally when it sees spirits and is a Hollow or an Arrancar or
     * on the caster's team; a Hollow mob always is, and an Arrancar NPC is an ally unless it is someone's
     * Fracción; no other creature is. The PvP rule is not asked: the light never harms, and a player must step
     * into it.
     */
    @Nullable
    public static Refusal targetRefusal(Player caster, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != caster.level()) {
            return Refusal.NO_TARGET;
        }
        if (target.getBbHeight() > MAX_HEIGHT || target.getBbWidth() > MAX_WIDTH || target instanceof Gillian
                || target instanceof Player giant && MenosBody.isGiant(giant)) {
            return Refusal.TOO_BIG;
        }
        if (held(target) || offeredNear(target) || !(target instanceof Player) && !mayHold(target)) {
            return Refusal.BUSY;
        }
        if (target instanceof Player player) {
            boolean hollowSoul = SoulLogic.hasSoul(player) && HollowPlayerLogic.isHollowOrArrancar(SoulLogic.get(player));
            return SoulLogic.seesSpirits(player) && (hollowSoul || caster.isAlliedTo(player)) ? null : Refusal.NOT_ALLY;
        }
        if (target instanceof ArrancarNpc npc) {
            return npc.isPledged() ? Refusal.NOT_ALLY : null;
        }
        return target instanceof Hollow ? null : Refusal.NOT_ALLY;
    }

    /**
     * The creature the caster looks at: the first along its look line, out to the reach and short of the first
     * block in the way, a little to either side counting. Spectators, lights and Cajas and the caster itself are
     * passed by, and a part of a great creature stands for the whole.
     */
    @Nullable
    public static LivingEntity aimed(Player caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getViewVector(1.0F).scale(BleachConfig.get(BleachConfig.NEGACION_PLAYER_RANGE)));
        end = SpiritClip.clip(caster.level(), new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                caster)).block().getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, end,
                new AABB(eye, end).inflate(1.0), entity -> !entity.isSpectator() && entity.isPickable()
                        && !(entity instanceof HoldingVehicle), AIM_MARGIN);
        Entity entity = hit == null ? null
                : hit.getEntity() instanceof PartEntity<?> part ? part.getParent() : hit.getEntity();
        return entity instanceof LivingEntity living ? living : null;
    }

    /**
     * Lands a light on a creature and mounts it at once. Null, with nothing left behind, if the light may not
     * hold it or it could not mount. A Menos's gathering Cero breaks by itself once it is held.
     */
    @Nullable
    public static NegacionLight lift(ServerLevel level, LivingEntity target) {
        if (!mayHold(target)) {
            return null;
        }
        NegacionLight light = NegacionLight.place(level, target, null);
        if (light == null) {
            return null;
        }
        if (!target.startRiding(light, true)) {
            light.discard();
            return null;
        }
        return light;
    }

    /** Lands a light offered to this player, and tells it in chat who calls it down and how to step in. */
    @Nullable
    public static NegacionLight offer(ServerLevel level, Player target, Component casterName) {
        NegacionLight light = NegacionLight.place(level, target, target);
        if (light != null) {
            target.displayClientMessage(Component.translatable(OFFERED, casterName, Component.keybind("key.use"),
                    BleachConfig.get(BleachConfig.NEGACION_OFFER_SECONDS)).withStyle(ChatFormatting.YELLOW), false);
        }
        return light;
    }

    // ---- Listeners ----

    /**
     * Nothing hurts the held, and the held hurt nothing, with their own blows or with anything they shot or
     * threw: all but what passes every protection, the void and {@code /kill}, so a held creature can never be
     * trapped past them.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (held(event.getEntity()) || held(source.getEntity()) || held(source.getDirectEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * An ender pearl thrown before a Caja closed is lost when it lands, rather than pulling its thrower out of the
     * Caja (a landing pearl gets its owner off any mount before it teleports). Negación's light, which its rider
     * may leave at will, keeps the vanilla landing.
     */
    private static void onPearlLands(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof ThrownEnderpearl pearl && !pearl.level().isClientSide()
                && CajaNegacion.shutAway(pearl.getOwner())) {
            event.setCanceled(true);
            pearl.discard();
        }
    }

    /** A held player strikes nothing. */
    private static void onAttack(AttackEntityEvent event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Nor uses what it holds: buckets and flint and steel are items too, which never ask to place a block. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Nor uses a block, or places one. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Nor deals with a creature. */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Nor with a spot on one, the way an armour stand's gear is taken: a separate event that comes first. */
    private static void onEntityInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Nor breaks a block. */
    private static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (refused(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * A held player logging out is set down where it was taken before it is saved, so it never loads mid-air or
     * shut away.
     */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        HoldingVehicle holder = holderOf(player);
        if (holder != null) {
            holder.setDown(player);
        }
    }

    /**
     * Vanilla takes a player to another world still riding (a teleport by command or pearl gets off first, but
     * not every way does): it gets off its light or Caja in the world it left, where the hold then ends, and it
     * falls slowly where it arrived. Got off from afar, with no dismount spot, as the hold is a world away; a
     * Caja never moves a rider that is no longer in its world.
     */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        HoldingVehicle holder = holderOf(player);
        if (holder != null && holder.level() != player.level()) {
            player.removeVehicle();
        }
    }

    // ---- Helpers ----

    /**
     * Whether this player is held, so its doing is refused; on the server it is told so, now and then. Also asked
     * by {@link ArrancarEvents#onKey}, so the held call out nothing.
     */
    static boolean refused(@Nullable Player player) {
        HoldingVehicle holder = holderOf(player);
        if (holder == null) {
            return false;
        }
        if (!player.level().isClientSide()) {
            holder.tellHeld(player);
        }
        return true;
    }

    /** Whether a light offered to this creature already waits close by it. */
    private static boolean offeredNear(LivingEntity target) {
        return !target.level().getEntitiesOfClass(NegacionLight.class, target.getBoundingBox().inflate(OFFER_NEAR),
                light -> light.phase() == NegacionLight.Phase.OFFERED && target.getUUID().equals(light.offeredTo()))
                .isEmpty();
    }

    private static long cooldownTicks() {
        return BleachConfig.get(BleachConfig.NEGACION_PLAYER_COOLDOWN_SECONDS) * 20L;
    }
}
