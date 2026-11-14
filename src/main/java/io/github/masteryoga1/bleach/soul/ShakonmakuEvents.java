package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.world.Shakonmaku;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The Shakonmaku's hold in Soul Society: a body (any creature, or a player in survival or adventure) that walks, flies
 * or falls across it anywhere but through a gate's gap is set back on its own side, with whatever it carries. Commands
 * and every other teleport never count.
 */
public final class ShakonmakuEvents {
    /** The line a soul turned back is shown. */
    public static final String TURNED_BACK_KEY = "message.bleach.shakonmaku.turned_back";
    /** A soul is told at most once in this many ticks, however often it pushes. */
    private static final int TELL_EVERY_TICKS = 20;
    /** The membrane ripples where one thing meets it at most once in this many ticks, however often it pushes. */
    private static final int RIPPLE_EVERY_TICKS = 10;
    /** The ripple where something meets the membrane: motes of scrape light about it, for those who see spirits. */
    private static final int RIPPLE_COUNT = 16;
    /** How fast the ripple's motes drift. */
    private static final double RIPPLE_SPEED = 0.02;
    /** The game time each entity, by its id, last made the membrane ripple; forgotten when it leaves its level. */
    private static final Map<Integer, Long> RIPPLED_AT = new HashMap<>();

    private ShakonmakuEvents() {
    }

    /** Puts the membrane's listeners on the game's event bus. */
    public static void register(IEventBus bus) {
        bus.addListener(ShakonmakuEvents::onTickPost);
        bus.addListener(ShakonmakuEvents::onLeave);
    }

    /** Whether the membrane turns this back: any living creature but a stand, and a player in survival or adventure. */
    public static boolean isBody(Entity entity) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof LivingEntity && !(entity instanceof ArmorStand);
    }

    /** Whether this stack is held back: any that holds a body. */
    static boolean heldBack(Entity root) {
        return root.getSelfAndPassengers().anyMatch(ShakonmakuEvents::isBody);
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
        BlockHitResult at = Shakonmaku.crossing(from, player.position());
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
     * After a lone entity's own tick, the server's moves of it judged from where the tick began: a stack holding a
     * body that crossed goes back there. Players moving themselves are judged at their packets instead.
     */
    private static void onTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        // An item, an orb, a falling block or a display holds no body unless something rides it.
        if (entity instanceof Player || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)
                || !Shakonmaku.in(level) || !(entity instanceof LivingEntity) && !entity.isVehicle()
                || entity.getX() == entity.xo && entity.getY() == entity.yo && entity.getZ() == entity.zo) {
            return;
        }
        Vec3 from = new Vec3(entity.xo, entity.yo, entity.zo);
        BlockHitResult at = Shakonmaku.crossing(from, entity.position());
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
}
