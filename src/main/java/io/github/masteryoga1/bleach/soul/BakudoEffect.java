package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.registry.ModEffects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3f;

/**
 * The two binds of Bakudō, one class for both: {@link ModEffects#BAKUDO_BOUND}, the arms tied behind the back (no
 * blow, no item, no art of the hands, and a slower walk), and {@link ModEffects#BAKUDO_HELD}, the body held where it
 * stands (no step, jump, swim, glide, ride or flash step, but the arms free). A spell that does both gives both.
 *
 * <p>Both are hidden: no swirl, no icon on the HUD or in the inventory. Those who see spirits see the bind on the
 * creature instead, a pair of pale gold motes at the wrists or a crackling yellow ring at the waist. Nothing cures
 * them but their time, {@code /effect clear}, death and Negación, whose light lifts a bind away; milk and a totem do
 * not. The listeners here refuse what a bound creature would do with its hands, on both sides for a player, whose own
 * client knows its effects and so predicts nothing, and tell a bound player why on the server alone. The arts the
 * binds stop elsewhere (Kidō, the Getsuga Tenshō, a Bala, the flash steps) ask for the effect in their own checks.
 */
public final class BakudoEffect extends MobEffect {
    /** The bound arms' colour: the pale gold Sai glows as it breaks. */
    public static final int BOUND_COLOUR = 0xE6D7A3;
    /** The held body's colour: Hainawa's yellow rope. */
    public static final int HELD_COLOUR = 0xF2C230;

    /** How much slower the bound walk and swim: 30% of the total. */
    private static final double BOUND_SLOW = -0.3;
    /** Ticks between the looks of a bound and of a held creature. */
    private static final int BOUND_LOOK_TICKS = 5;
    private static final int HELD_LOOK_TICKS = 4;
    private static final int HELD_RING_POINTS = 6;
    private static final float DUST_SIZE = 0.6F;
    /** How far behind the body the bound wrists are, past its edge, and how far apart either side of its middle. */
    private static final double WRISTS_BEHIND = 0.1;
    private static final double WRISTS_APART = 0.15;
    /** How far out from the body's edge the held ring is. */
    private static final double RING_OUT = 0.15;
    private static final DustParticleOptions BOUND_DUST = dust(BOUND_COLOUR);
    private static final DustParticleOptions HELD_DUST = dust(HELD_COLOUR);
    /** What a bound player is told when an act of its hands is refused. */
    private static final String REFUSED_KEY = "message.bleach.bakudo.bound";

    private final boolean held;

    /**
     * The held body has Gritz's five modifiers under its own ids, each taking the whole of the total, so nothing walks,
     * jumps, flies or swims it anywhere; the bound arms slow the walk and the swim by {@link #BOUND_SLOW}. Two effects
     * never share an id on one attribute, so the two binds and Gritz come and go apart.
     */
    public BakudoEffect(boolean held) {
        super(MobEffectCategory.HARMFUL, held ? HELD_COLOUR : BOUND_COLOUR);
        this.held = held;
        if (held) {
            addAttributeModifier(Attributes.MOVEMENT_SPEED, BleachMod.id("bakudo_held_speed"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.JUMP_STRENGTH, BleachMod.id("bakudo_held_jump"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.FLYING_SPEED, BleachMod.id("bakudo_held_flying_speed"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(NeoForgeMod.SWIM_SPEED, BleachMod.id("bakudo_held_swim_speed"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, BleachMod.id("bakudo_held_flight"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            addAttributeModifier(Attributes.MOVEMENT_SPEED, BleachMod.id("bakudo_bound_speed"), BOUND_SLOW,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(NeoForgeMod.SWIM_SPEED, BleachMod.id("bakudo_bound_swim_speed"), BOUND_SLOW,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /** Whether this is the held body, not the bound arms. */
    public boolean held() {
        return held;
    }

    /** Whether this creature's arms are bound. Both sides, though a client knows only its own player's and mount's. */
    public static boolean bound(LivingEntity entity) {
        return entity.hasEffect(ModEffects.BAKUDO_BOUND);
    }

    /** Whether this creature is held where it stands. Both sides, as {@link #bound}. */
    public static boolean held(LivingEntity entity) {
        return entity.hasEffect(ModEffects.BAKUDO_HELD);
    }

    /** A bind is reiatsu, not a poison or a sickness: neither milk nor a totem lifts it. */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /**
     * A body held while it rides is pulled off at once, on the server, rather than at its next tick, so it is never
     * saved riding a mount it could not get back on ({@link #onMount}), which a player's login would then throw away.
     * One Negación already holds is left where it is: the light ends the hold.
     */
    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (held && !entity.level().isClientSide() && entity.isPassenger() && !Negacion.held(entity)) {
            entity.stopRiding();
        }
    }

    /**
     * Each tick, before the creature moves. On the server, a creature Negación's light or a Caja has taken up is let
     * go of the bind, which vanilla then removes. A held body is rooted ({@link #hold}); bound arms let go of whatever
     * they were using, a drawn bow, a raised shield or food, and a held Bala key. Then those who see spirits are shown
     * the bind, now and then.
     */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        ServerLevel level = entity.level() instanceof ServerLevel server ? server : null;
        if (level != null && Negacion.held(entity)) {
            return false;
        }
        if (held) {
            hold(entity, level != null);
        } else {
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
            if (level != null && entity instanceof Player player && SoulLogic.hasSoul(player)) {
                Bala.release(SoulLogic.get(player));
            }
        }
        if (level != null) {
            look(level, entity);
        }
        return true;
    }

    /**
     * Roots a held body. Wherever it is moved from ({@link LivingEntity#isControlledByLocalInstance}: a player's own
     * client, and on the server that same player and a mob with its AI) it neither swims nor glides, and keeps no
     * motion but a fall, so neither ice, a push nor a current slides it and no ladder, water or jump lifts it. The
     * server's run for a player is meant: the server owns the glide, so its stop is the one that ends it, and the
     * server's copy of a player's motion moves nothing. Then on the server a rider is pulled off its mount, and a body
     * no one there moves (a mob with no AI, or one a player rides and steers) has its motion held still as well.
     */
    private static void hold(LivingEntity entity, boolean server) {
        boolean controlled = entity.isControlledByLocalInstance();
        if (controlled) {
            entity.setSwimming(false);
            if (entity instanceof Player player && player.isFallFlying()) {
                player.stopFallFlying();
            }
            root(entity);
        }
        if (server) {
            if (entity.isPassenger()) {
                entity.stopRiding();
            }
            if (!controlled) {
                root(entity);
            }
        }
    }

    /** No motion left but a fall. */
    private static void root(LivingEntity entity) {
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
    }

    /**
     * The bind as those who see spirits see it: two pale gold motes at the small of the back, one at each wrist, or a
     * ring of yellow motes at the waist with a spark crackling round it.
     */
    private void look(ServerLevel level, LivingEntity entity) {
        if (!held) {
            if (entity.tickCount % BOUND_LOOK_TICKS != 0) {
                return;
            }
            float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
            double behind = entity.getBbWidth() * 0.5 + WRISTS_BEHIND;
            // Facing (-sin, cos), so behind is (sin, -cos) and the side across the back (cos, sin).
            double x = entity.getX() + Mth.sin(yaw) * behind;
            double z = entity.getZ() - Mth.cos(yaw) * behind;
            double y = entity.getY(0.45);
            for (int side = -1; side <= 1; side += 2) {
                SpiritSenses.particles(level, BOUND_DUST, x + Mth.cos(yaw) * WRISTS_APART * side, y,
                        z + Mth.sin(yaw) * WRISTS_APART * side, 1, 0.0, 0.0, 0.0, 0.0);
            }
            return;
        }
        if (entity.tickCount % HELD_LOOK_TICKS != 0) {
            return;
        }
        double radius = entity.getBbWidth() * 0.5 + RING_OUT;
        double y = entity.getY(0.5);
        // The spark goes round the ring, a point further each time.
        int spark = (entity.tickCount / HELD_LOOK_TICKS) % HELD_RING_POINTS;
        for (int i = 0; i < HELD_RING_POINTS; i++) {
            double angle = Math.PI * 2.0 * i / HELD_RING_POINTS;
            double x = entity.getX() + Math.cos(angle) * radius;
            double z = entity.getZ() + Math.sin(angle) * radius;
            SpiritSenses.particles(level, HELD_DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i == spark) {
                SpiritSenses.particles(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    // ---- Listeners ----

    /**
     * The blows of the bound are cancelled first of all, before any other rule weighs them, and a bound player's
     * doings before any listener at the usual priority acts on them (Konsō's offer, the lending of power), so a bound
     * click does nothing at all; its item use and its missiles, the held jump and the held mount at the usual priority.
     */
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, BakudoEffect::onIncomingDamage);
        bus.addListener(EventPriority.HIGH, BakudoEffect::onAttack);
        bus.addListener(EventPriority.HIGH, BakudoEffect::onRightClickItem);
        bus.addListener(EventPriority.HIGH, BakudoEffect::onRightClickBlock);
        bus.addListener(EventPriority.HIGH, BakudoEffect::onEntityInteract);
        bus.addListener(EventPriority.HIGH, BakudoEffect::onEntityInteractAt);
        bus.addListener(EventPriority.HIGH, BakudoEffect::onBreakBlock);
        bus.addListener(BakudoEffect::onUseItem);
        bus.addListener(BakudoEffect::onReleaseItem);
        bus.addListener(BakudoEffect::onJoinLevel);
        bus.addListener(BakudoEffect::onJump);
        bus.addListener(BakudoEffect::onMount);
    }

    /**
     * A bound creature's own blow lands nothing: a player's or a mob's melee, whose causing and direct creature are
     * both the bound one. What it shot before the bind still flies. The event fires only on the server.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && bound(attacker)
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            event.setCanceled(true);
        }
    }

    /** A bound player strikes nothing, though its arm may still swing. Both sides. */
    private static void onAttack(AttackEntityEvent event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Nor uses what it holds. Both sides. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Nor uses a block, or places one. Both sides. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Nor deals with a creature: no Konsō, and no lending of power. Both sides. But it may step into a Negación light
     * offered to it ({@link #boarding}), so a Gillian's rescue frees a bound player as it frees a bound mob.
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!boarding(event.getEntity(), event.getTarget()) && refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Nor with a spot on one, such as an armour stand's gear, which would swap for what it holds. Both sides. The
     * spot of a light offered to it is let be, as the light itself is, so boarding it is not told it was refused.
     */
    private static void onEntityInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!boarding(event.getEntity(), event.getTarget()) && refused(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Nor breaks a block. Fired on the server only: a break the client foresaw is put back by the server's answer. The
     * ask whether a beam or a blast it shot may break one ({@link GetsugaTensho#asking}) is not its hands' doing, so a
     * bound Hollow's Cero still carves the wall it strikes, and its owner is not told otherwise.
     */
    private static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!GetsugaTensho.asking() && refused(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * No bound creature, player or mob, begins to use an item: no bow drawn, no shield raised, nothing eaten. Both
     * sides, though a client knows no mob's effects but its own mount's, so for a mob the server's test is the one
     * that counts.
     */
    private static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (bound(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Nor lets fly what it drew before its arms were bound: a bow or a trident let go before the bind's next tick has
     * made it let go fires nothing, so neither the arrow nor the trident is spent on a missile that never joins the
     * world. Both sides, as the start.
     */
    private static void onReleaseItem(LivingEntityUseItemEvent.Stop event) {
        if (bound(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * No missile a bound creature looses joins the world: an arrow, a fireball, a potion, a Heilig Pfeil or a Getsuga
     * Tenshō. Server only, so a client never drops what its server sent; one loaded from disk is left alone.
     */
    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && !event.loadedFromDisk() && event.getEntity() instanceof Projectile missile
                && missile.getOwner() instanceof LivingEntity owner && bound(owner)) {
            event.setCanceled(true);
        }
    }

    /**
     * A held creature that jumps anyway goes nowhere, Jump Boost and all, which vanilla adds after the attribute. Both
     * sides, as Gritz's, since a player's own client moves it.
     */
    private static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (held(entity)) {
            root(entity);
        }
    }

    /**
     * A held body mounts nothing, a horse, a boat or a minecart that would carry it off, as it is pulled off what it
     * rode; but Negación's light and a Caja, which take it up, still may. Getting off is never refused. Server only,
     * where a mount is decided: a client mounts as its server tells it.
     */
    private static void onMount(EntityMountEvent event) {
        if (event.isMounting() && !event.getLevel().isClientSide()
                && event.getEntityMounting() instanceof LivingEntity entity && held(entity)
                && !(event.getEntityBeingMounted() instanceof Negacion.HoldingVehicle)) {
            event.setCanceled(true);
        }
    }

    // ---- Helpers ----

    /**
     * Whether this player is using the Negación light offered to it, to step in: the rescue a bind gives way to, which
     * asks nothing of the hands. Both sides, which both know the light's offer.
     */
    private static boolean boarding(Player player, Entity target) {
        return target instanceof NegacionLight light && light.phase() == NegacionLight.Phase.OFFERED
                && player.getUUID().equals(light.offeredTo());
    }

    /**
     * Whether this player's arms are bound, so its doing is refused; on the server it is told so, on the action bar.
     */
    private static boolean refused(@Nullable Player player) {
        if (player == null || !bound(player)) {
            return false;
        }
        if (!player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable(REFUSED_KEY).withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    /** A dust mote of this colour, in the size of every bind's. */
    private static DustParticleOptions dust(int colour) {
        return new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F), DUST_SIZE);
    }
}
