package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.DangaiPlan;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Dangai, for every aware soul a Senkaimon does not guide: the way in, the Kōryū's hold, each tick in the lane, a
 * soul found there with no crossing, every way out, and the fresh start at login.
 */
public final class DangaiEvents {
    private static final String ENTERED_KEY = "message.bleach.dangai.entered";
    private static final String HELD_KEY = "message.bleach.dangai.held";
    private static final String LOST_KEY = "message.bleach.dangai.lost";
    private static final String NO_LANDING_KEY = "message.bleach.senkaimon.no_landing";
    /** The Kōryū's purple, in which a soul it holds glows. */
    public static final int KORYU_COLOUR = 0x9C6CFF;
    /** The hold's look: Geki's glow about the whole body, in the current's purple. */
    private static final KidoSpell.Look KORYU_LOOK =
            new KidoSpell.Look(KORYU_COLOUR, Optional.empty(), 1.0F, Optional.empty(), KidoSpell.BindStyle.GLOW);
    private static final float KORYU_VOLUME = 0.8F;
    private static final float HOLD_PITCH = 0.6F;

    private DangaiEvents() {
    }

    /** Each tick in the Dangai, the end of a crossing by any way out or by death, and the fresh start at login. */
    public static void register(IEventBus bus) {
        bus.addListener(DangaiEvents::onPlayerTick);
        bus.addListener(DangaiEvents::onChangedDimension);
        bus.addListener(DangaiEvents::onRespawn);
        bus.addListener(DangaiEvents::onLoggedIn);
    }

    // ---- The way in ----

    /**
     * Where a gate takes a soul it does not guide: the start of the gate's lane in the Dangai, or null, the soul told
     * why, when the lane's start will not hold it. The lane's chunk loads here.
     */
    @Nullable
    public static DimensionTransition into(Senkaimon gate, ServerPlayer passer) {
        ResourceKey<Level> to = gate.leadsTo();
        ServerLevel dangai = BleachWorlds.level(passer.server, BleachWorlds.DANGAI);
        if (to == null || dangai == null) {
            return null;
        }
        int lane = gate.lane();
        Vec3 spot = TravelEvents.landing(passer, dangai, DangaiPlan.entry(lane));
        if (spot == null) {
            passer.displayClientMessage(Component.translatable(NO_LANDING_KEY).withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        GlobalPos left = GlobalPos.of(passer.level().dimension(), passer.blockPosition());
        TravelRecord.Crossing crossing = new TravelRecord.Crossing(to, lane, DangaiLogic.bearing(gate.getX(), gate.getZ()));
        // Facing down the lane, whichever way the gate faced.
        return new DimensionTransition(dangai, spot, Vec3.ZERO, 0.0F, 0.0F, entity -> {
            TravelEvents.arrived(entity, left);
            if (entity instanceof ServerPlayer player) {
                entered(player, crossing);
            }
        });
    }

    /** A soul just set at its lane's start keeps the crossing, and is told where it is. */
    private static void entered(ServerPlayer player, TravelRecord.Crossing crossing) {
        TravelRecord record = player.getData(SoulAttachments.TRAVEL);
        player.setData(SoulAttachments.TRAVEL, record.withCrossing(Optional.of(crossing)));
        player.displayClientMessage(Component.translatable(ENTERED_KEY).withStyle(ChatFormatting.GRAY), true);
    }

    // ---- The Kōryū ----

    /**
     * The Kōryū takes hold of an aware soul whose body is in it, unless it let go of that soul too lately: held where it
     * stands by Hainawa's own effect, glowing purple. Creative players, spectators and a body that refuses the hold pass.
     * Whether it took hold.
     */
    public static boolean touch(Player player, long now) {
        if (player.isCreative() || player.isSpectator() || !SoulLogic.hasSoul(player)) {
            return false;
        }
        SoulData data = SoulLogic.get(player);
        if (!SoulLogic.isAware(data) || now < data.koryuFreeAt) {
            return false;
        }
        int ticks = Math.max(1, DangaiLogic.ticks(BleachConfig.get(BleachConfig.DANGAI_KORYU_HOLD_SECONDS)));
        // A bind already on it keeps its own look; vanilla keeps the longer of the two holds.
        boolean alreadyHeld = BakudoEffect.held(player);
        // Not ambient, no swirl and no icon, as a bind's: the effect draws its own look on the body.
        boolean landed = player.addEffect(new MobEffectInstance(ModEffects.BAKUDO_HELD, ticks, 0, false, false, false));
        if (!landed && !alreadyHeld) {
            // Refused, as a woven Quincy's body refuses every hold: the current lets it through.
            return false;
        }
        data.koryuFreeAt = now + ticks + DangaiLogic.REGRAB_GUARD_TICKS;
        if (!alreadyHeld) {
            BakudoEffect.markLook(player, true, KORYU_LOOK);
        }
        SpiritSenses.play(player, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, KORYU_VOLUME, HOLD_PITCH, false);
        player.displayClientMessage(Component.translatable(HELD_KEY).withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    // ---- In the lane, and out ----

    /**
     * A soul's tick in the Dangai, before it moves: put out if it has no crossing, or let out once it reaches the lane's
     * end; nothing while a way out that was refused waits to be tried again.
     */
    private static void tick(ServerPlayer player, long now) {
        if (now < SoulLogic.get(player).dangaiRetryAt) {
            return;
        }
        TravelRecord.Crossing crossing = player.getData(SoulAttachments.TRAVEL).crossing().orElse(null);
        if (crossing == null) {
            lost(player, now);
        } else if (player.getZ() >= DangaiPlan.EXIT_Z) {
            out(player, crossing, now);
        }
    }

    /**
     * Out of the Dangai at the lane's end into the crossing's world, falling slowly, the crossing counted as made. A soul
     * with nowhere to come out is put out at the Human World's spawn.
     */
    private static void out(ServerPlayer player, TravelRecord.Crossing crossing, long now) {
        ServerLevel target = BleachWorlds.level(player.server, crossing.to());
        Vec3 spot = target == null ? null : laneEnd(player, target, crossing);
        if (spot == null) {
            lost(player, now);
            return;
        }
        GlobalPos left = GlobalPos.of(BleachWorlds.DANGAI, player.blockPosition());
        leave(player, new DimensionTransition(target, spot, Vec3.ZERO, player.getYRot(), 0.0F, entity -> {
            TravelEvents.arrived(entity, left);
            if (entity instanceof ServerPlayer soul) {
                soul.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, DangaiLogic.EXIT_SLOW_FALLING_TICKS));
                Milestones.grant(soul, Milestones.DANGAI);
            }
        }), now);
    }

    /**
     * Where the lane's end lets this soul out: over a Rukongai district on its bearing (a random one of the first ten,
     * then the others in turn), or over its way home in the Human World; null if none will hold it.
     */
    @Nullable
    private static Vec3 laneEnd(ServerPlayer player, ServerLevel target, TravelRecord.Crossing crossing) {
        int rise = BleachConfig.get(BleachConfig.DANGAI_EXIT_HEIGHT);
        if (crossing.to() != BleachWorlds.SOUL_SOCIETY) {
            Vec3 home = TravelEvents.homeward(player, target);
            return home == null ? null : SafeLanding.risen(player, target, home, rise);
        }
        int first = player.getRandom().nextInt(DangaiLogic.EXIT_DISTRICTS);
        for (int i = 0; i < DangaiLogic.EXIT_DISTRICTS; i++) {
            Vec3 over = DangaiLogic.overDistrict(1 + (first + i) % DangaiLogic.EXIT_DISTRICTS, crossing.bearing());
            Vec3 spot = SafeLanding.skySpot(player, target, over.x, over.z, rise);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** A soul in the Dangai with no crossing to follow, or nowhere to come out, is put out at the Human World's spawn. */
    private static void lost(ServerPlayer player, long now) {
        ServerLevel overworld = player.server.overworld();
        if (leave(player, new DimensionTransition(overworld, player, DimensionTransition.DO_NOTHING), now)) {
            player.sendSystemMessage(Component.translatable(LOST_KEY).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Takes this soul out of the Dangai by this way, first off anything it rides and with anything on it set down.
     * Whether it went: a change of world refused (another mod may cancel it) is logged and tried again a second later.
     */
    private static boolean leave(ServerPlayer player, DimensionTransition way, long now) {
        // Vanilla would carry a rider's link to its mount into the new world; setting passengers down is belt and braces.
        player.stopRiding();
        player.ejectPassengers();
        if (player.changeDimension(way) != null) {
            return true;
        }
        SoulLogic.get(player).dangaiRetryAt = now + DangaiLogic.RETRY_TICKS;
        BleachMod.LOGGER.warn("Dangai: {} could not be let out into {}; trying again in a second",
                player.getName().getString(), way.newLevel().dimension().location());
        return false;
    }

    /**
     * At login, a soul inside the Dangai on a crossing starts its lane again; if the lane's start will not hold it, the
     * crossing is dropped and its next tick puts it out. A dead soul and a spectator are left as they are. Public for
     * game tests.
     */
    public static void atLogin(ServerPlayer player) {
        TravelRecord.Crossing crossing = player.getData(SoulAttachments.TRAVEL).crossing().orElse(null);
        if (crossing == null || !(player.level() instanceof ServerLevel dangai)
                || dangai.dimension() != BleachWorlds.DANGAI || !player.isAlive() || player.isSpectator()) {
            return;
        }
        Vec3 start = TravelEvents.landing(player, dangai, DangaiPlan.entry(crossing.lane()));
        if (start == null) {
            clear(player);
            return;
        }
        player.teleportTo(dangai, start.x, start.y, start.z, 0.0F, 0.0F);
        entered(player, crossing);
    }

    /** Ends this soul's crossing, if it is on one. */
    private static void clear(Player player) {
        TravelRecord record = player.getData(SoulAttachments.TRAVEL);
        if (record.crossing().isPresent()) {
            player.setData(SoulAttachments.TRAVEL, record.withCrossing(Optional.empty()));
        }
    }

    // ---- Listeners ----

    /**
     * Each alive soul in the Dangai but a spectator ticks there. A fault drops its crossing, is logged, and waits a
     * second as a refused way out does, so the next try puts the soul out.
     */
    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != BleachWorlds.DANGAI
                || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        try {
            tick(player, now);
        } catch (RuntimeException e) {
            clear(player);
            SoulLogic.get(player).dangaiRetryAt = now + DangaiLogic.RETRY_TICKS;
            BleachMod.LOGGER.warn("Dangai: {}'s crossing failed, and is dropped", player.getName().getString(), e);
        }
    }

    /** A crossing ends whenever its soul leaves the Dangai, by whatever way. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom() == BleachWorlds.DANGAI && !event.getEntity().level().isClientSide()) {
            clear(event.getEntity());
        }
    }

    /** Death ends a crossing too: the soul respawns as it would anywhere. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            clear(event.getEntity());
        }
    }

    /** A soul that logs in mid-crossing starts its lane again. */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            atLogin(player);
        }
    }
}
