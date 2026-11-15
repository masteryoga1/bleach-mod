package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.mixin.FireworkRocketEntityAccessor;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The Shakonmaku's hold in Soul Society: a body (any creature, or a player in survival or adventure) that would cross it
 * anywhere but by a gate's doorway or wicket is set back on its own side with whatever it rides or carries, its pearl or
 * chorus leaps across are refused, vanilla's thrown and shot things stop at it and fall (or come undone if weightless or
 * homing), and spirit arts meet it through {@link SpiritClip}. Commands and every other teleport never count.
 */
public final class ShakonmakuEvents {
    /** The line a soul turned back is shown. */
    public static final String TURNED_BACK_KEY = "message.bleach.shakonmaku.turned_back";
    /** Kept with a thrown thing once the membrane has stopped it: it falls where it will after that. */
    static final String FALLEN_KEY = "bleach:shakonmaku_fallen";
    /** A soul is told at most once in this many ticks, however often it pushes. */
    private static final int TELL_EVERY_TICKS = 20;
    /** The membrane ripples where one thing meets it at most once in this many ticks, however often it pushes. */
    private static final int RIPPLE_EVERY_TICKS = 10;
    /** The ripple where something meets the membrane: motes of scrape light about it, for those who see spirits. */
    private static final int RIPPLE_COUNT = 16;
    /** How fast the ripple's motes drift. */
    private static final double RIPPLE_SPEED = 0.02;
    /** How far short of the membrane a stopped thing is left, so it lies on its own side. */
    private static final double SHORT_OF = 0.01;
    /** The game time each entity, by its id, last made the membrane ripple; forgotten when it leaves its level. */
    private static final Map<Integer, Long> RIPPLED_AT = new HashMap<>();

    private ShakonmakuEvents() {
    }

    /** Puts the membrane's listeners on the game's event bus, one for each of the three leaps it refuses. */
    public static void register(IEventBus bus) {
        bus.addListener(ShakonmakuEvents::onTickPre);
        bus.addListener(ShakonmakuEvents::onTickPost);
        bus.addListener(ShakonmakuEvents::onLeave);
        bus.addListener(EntityTeleportEvent.EnderPearl.class, ShakonmakuEvents::onLeap);
        bus.addListener(EntityTeleportEvent.ChorusFruit.class, ShakonmakuEvents::onLeap);
        bus.addListener(EntityTeleportEvent.EnderEntity.class, ShakonmakuEvents::onLeap);
    }

    /** Whether the membrane turns this back: any living creature but a stand, and a player in survival or adventure. */
    public static boolean isBody(Entity entity) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof LivingEntity && !(entity instanceof ArmorStand);
    }

    /** Whether this stack is held back: one a player steers if any player aboard is held, any other if it holds a body. */
    static boolean heldBack(Entity root) {
        boolean steered = root.getControllingPassenger() instanceof Player;
        return root.getSelfAndPassengers().anyMatch(entity -> (!steered || entity instanceof Player) && isBody(entity));
    }

    /** Whether this body, moving from one point to the other in this level, would be turned back by the membrane. */
    public static boolean stops(Level level, Entity body, Vec3 from, Vec3 to) {
        return Shakonmaku.in(level) && isBody(body) && Shakonmaku.bodyCrosses(from, to);
    }

    /**
     * After the server took a player's own move: one that crossed the membrane outside a gate is put back where it
     * stood, fallen no further than it was there, as vanilla puts back a player who moved wrongly, and this answers
     * whether it was. Called from the move packet's handler.
     */
    public static boolean walked(ServerPlayer player, Vec3 from, float fallenBefore) {
        if (player.isPassenger() || !Shakonmaku.in(player.level()) || !isBody(player)) {
            return false;
        }
        BlockHitResult at = Shakonmaku.bodyCrossing(from, player.position());
        if (at == null) {
            return false;
        }
        player.connection.teleport(from.x, from.y, from.z, player.getYRot(), player.getXRot());
        // The refused step adds no fall and forgives none, but a landing vanilla already hurt it for stays at 0.
        player.fallDistance = Math.min(player.fallDistance, fallenBefore);
        turnedBack(player, at.getLocation());
        return true;
    }

    /**
     * After the server took a player's move of what it steers: a stack that crossed the membrane outside a gate goes
     * back, its riders with it, fallen no further than it was there, as vanilla puts back a vehicle that moved wrongly.
     * Answers whether it went back.
     */
    public static boolean rode(ServerPlayer player, Entity vehicle, Vec3 from, float fallenBefore) {
        if (!Shakonmaku.in(vehicle.level()) || !heldBack(vehicle)) {
            return false;
        }
        BlockHitResult at = Shakonmaku.bodyCrossing(from, vehicle.position());
        if (at == null) {
            return false;
        }
        vehicle.absMoveTo(from.x, from.y, from.z, vehicle.getYRot(), vehicle.getXRot());
        // The refused move adds no fall and forgives none, but a landing vanilla already hurt it for stays at 0.
        vehicle.fallDistance = Math.min(vehicle.fallDistance, fallenBefore);
        player.connection.send(new ClientboundMoveVehiclePacket(vehicle));
        turnedBack(vehicle, at.getLocation());
        return true;
    }

    /**
     * Before a thrown thing's own tick: one whose step would cross the membrane flies only to just short of it, so it
     * strikes nothing past it, and stops there if it is already that near; one with no weight comes undone instead.
     */
    private static void onTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Projectile thrown) || !(thrown.level() instanceof ServerLevel level)
                || !Shakonmaku.in(level) || isArt(thrown) || carried(thrown)) {
            return;
        }
        Vec3 from = thrown.position();
        BlockHitResult at = Shakonmaku.crossing(from, from.add(thrown.getDeltaMovement()));
        // The fallen mark is read only at the membrane: asking for it makes the entity's persistent data, which is saved.
        if (at == null || fallen(thrown)) {
            return;
        }
        if (!weightless(thrown)) {
            Vec3 stop = shortOf(from, at.getLocation());
            thrown.setDeltaMovement(stop.subtract(from));
            if (stop == from) {
                fell(level, thrown, at.getLocation());
            }
        } else {
            event.setCanceled(true);
            undo(level, thrown, at.getLocation());
        }
    }

    /**
     * After a lone entity's own tick, the server's moves of it judged from where the tick began: a stack holding a
     * body that crossed goes back there, and a thrown thing at the membrane stops and falls (one that flew past is put
     * back short of it first). Players moving themselves are judged at their packets instead.
     */
    private static void onTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)
                || !Shakonmaku.in(level)
                || entity.getX() == entity.xo && entity.getY() == entity.yo && entity.getZ() == entity.zo) {
            return;
        }
        Vec3 from = new Vec3(entity.xo, entity.yo, entity.zo);
        if (entity instanceof Projectile thrown) {
            if (!isArt(thrown) && !carried(thrown)) {
                stop(level, thrown, from);
            }
            return;
        }
        // An item, an orb, a falling block or a display holds no body unless something rides it.
        if (!(entity instanceof LivingEntity) && !entity.isVehicle()) {
            return;
        }
        BlockHitResult at = Shakonmaku.bodyCrossing(from, entity.position());
        if (at != null && heldBack(entity)) {
            entity.setPos(from);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
            turnedBack(entity, at.getLocation());
        }
    }

    /** An entity leaving its level is forgotten by the ripple's count. */
    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && !RIPPLED_AT.isEmpty()) {
            RIPPLED_AT.remove(event.getEntity().getId());
        }
    }

    /** A thrown thing that has just reached the membrane, or flown past it, this tick stops there. */
    private static void stop(ServerLevel level, Projectile thrown, Vec3 from) {
        Vec3 now = thrown.position();
        Vec3 moved = now.subtract(from);
        if (moved.lengthSqr() < 1.0E-12) {
            return;
        }
        BlockHitResult at = Shakonmaku.crossing(from, now.add(moved.normalize().scale(2.0 * SHORT_OF)));
        if (at == null || fallen(thrown)) {
            return;
        }
        if (weightless(thrown)) {
            undo(level, thrown, at.getLocation());
            return;
        }
        if (Shakonmaku.crosses(from, now)) {
            thrown.setPos(shortOf(from, at.getLocation()));
        }
        fell(level, thrown, at.getLocation());
    }

    /** A thrown thing with weight stops dead at the membrane, which ripples, and falls where it will from then on. */
    private static void fell(ServerLevel level, Projectile thrown, Vec3 at) {
        thrown.setDeltaMovement(Vec3.ZERO);
        // Sent at once, or watchers see it fly on through the membrane until its next timed update.
        thrown.hasImpulse = true;
        thrown.getPersistentData().putBoolean(FALLEN_KEY, true);
        ripple(level, at, thrown);
    }

    /** A weightless thrown thing comes undone at the membrane: the ripple, and it is gone. */
    private static void undo(ServerLevel level, Projectile thrown, Vec3 at) {
        ripple(level, at, thrown);
        thrown.discard();
    }

    /**
     * A leap by ender pearl, chorus fruit or an Enderman's or Shulker's own art that would land a body on the other half
     * of the membrane is refused where it stands, a gate's gap counting as the half it lies on; so is one that would set
     * it down in a great gate or a wicket, through which it could then walk.
     */
    private static void onLeap(EntityTeleportEvent event) {
        Entity body = event.getEntity();
        if (!(body.level() instanceof ServerLevel level) || !Shakonmaku.in(level) || !isBody(body)) {
            return;
        }
        // A pearl landing in another world than its thrower's moves it between worlds, which the membrane never stops.
        if (event instanceof EntityTeleportEvent.EnderPearl pearl && pearl.getPearlEntity().level() != level) {
            return;
        }
        // A random leap drops onto the ground below the point drawn; a pearl, or a Shulker, lands on the point itself.
        boolean drops = !(event instanceof EntityTeleportEvent.EnderPearl) && !(body instanceof Shulker);
        Vec3 landing = drops ? landing(level, event.getTarget()) : event.getTarget();
        Shakonmaku.Side from = Shakonmaku.half(event.getPrev());
        Shakonmaku.Side to = Shakonmaku.half(landing);
        if (from != to || meetsGate(level, body, landing)) {
            event.setCanceled(true);
            turnedBack(body, event.getPrev());
        }
    }

    /** Where vanilla's random leap to this point would set a body down: dropped onto the first solid block below. */
    private static Vec3 landing(ServerLevel level, Vec3 target) {
        BlockPos pos = BlockPos.containing(target);
        if (!level.hasChunkAt(pos)) {
            return target;
        }
        for (double y = target.y; pos.getY() > level.getMinBuildHeight(); y--, pos = pos.below()) {
            if (level.getBlockState(pos.below()).blocksMotion()) {
                return new Vec3(target.x, y, target.z);
            }
        }
        return target;
    }

    /** Whether a body set down here would meet a great gate's block or a wicket's, already part through it. */
    private static boolean meetsGate(ServerLevel level, Entity body, Vec3 landing) {
        AABB box = body.getDimensions(body.getPose()).makeBoundingBox(landing).deflate(1.0E-7);
        return BlockPos.betweenClosedStream(box).anyMatch(pos -> level.isLoaded(pos)
                && (level.getBlockState(pos).is(ModBlocks.GREAT_GATE.get())
                || level.getBlockState(pos).is(ModBlocks.WICKET.get())));
    }

    /** Something turned back here: the ripple about it, and the line for every soul it held that the membrane holds. */
    private static void turnedBack(Entity stack, Vec3 at) {
        if (stack.level() instanceof ServerLevel level) {
            ripple(level, at, stack);
        }
        stack.getSelfAndPassengers().forEach(entity -> {
            if (entity instanceof ServerPlayer player && isBody(player)) {
                tell(player);
            }
        });
    }

    /** The membrane ripples where this met it, over its width and height, unless it rippled for it in the last ten ticks. */
    private static void ripple(ServerLevel level, Vec3 at, Entity met) {
        long now = level.getGameTime();
        Long last = RIPPLED_AT.get(met.getId());
        if (last != null && now - last < RIPPLE_EVERY_TICKS) {
            return;
        }
        RIPPLED_AT.put(met.getId(), now);
        double across = met.getBbWidth() / 2.0;
        double up = met.getBbHeight() / 2.0;
        SpiritSenses.particles(level, ParticleTypes.SCRAPE, at.x, at.y + up, at.z, RIPPLE_COUNT, across, up, across,
                RIPPLE_SPEED);
    }

    /** Tells the soul the membrane turned it back, unless it was told within the last second. */
    private static void tell(ServerPlayer player) {
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        if (now - data.shakonmakuToldAt >= TELL_EVERY_TICKS) {
            data.shakonmakuToldAt = now;
            player.displayClientMessage(Component.translatable(TURNED_BACK_KEY).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** The point this far short of {@code at} on the way from {@code from}, or {@code from} itself if nearer than that. */
    private static Vec3 shortOf(Vec3 from, Vec3 at) {
        Vec3 way = at.subtract(from);
        double length = way.length();
        return length <= SHORT_OF ? from : from.add(way.scale((length - SHORT_OF) / length));
    }

    /** Whether this thrown thing comes undone at the membrane, not falling: one with no weight, or a homing bullet. */
    private static boolean weightless(Projectile thrown) {
        return thrown.getGravity() <= 0.0 || thrown instanceof ShulkerBullet;
    }

    /** Spirit arts meet the membrane by their own rules ({@link SpiritClip}). */
    private static boolean isArt(Projectile thrown) {
        return thrown instanceof HeiligPfeil || thrown instanceof GetsugaTensho;
    }

    /** A rocket boosting a glider goes where the glider goes, and the glider is judged as a body instead. */
    private static boolean carried(Projectile thrown) {
        return thrown instanceof FireworkRocketEntity rocket
                && ((FireworkRocketEntityAccessor) rocket).bleach$isAttachedToEntity();
    }

    /** Whether the membrane has stopped this thrown thing already, so it now falls where it will. */
    private static boolean fallen(Projectile thrown) {
        return thrown.getPersistentData().getBoolean(FALLEN_KEY);
    }
}
