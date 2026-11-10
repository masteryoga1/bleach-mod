package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Soul Society's places as a soul walks them: the action bar names Seireitei, the Seireiheki, or the Rukongai district
 * and region a soul comes into, once nothing else has been on its action bar for {@link #QUIET_TICKS}. Konsō sends a
 * soul to a district of the Rukongai, which becomes its home (see {@link #passedOn}).
 */
public final class RukongaiEvents {
    /** How often a soul's place is read, in ticks of its own. */
    private static final int CHECK_TICKS = 20;
    /** How long a place's name waits behind any other action-bar line: as long as the game shows one. */
    public static final int QUIET_TICKS = 60;
    /** How many columns of the drawn district a landing tries before it gives up on the Rukongai. */
    public static final int LANDING_TRIES = 4;

    private RukongaiEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(RukongaiEvents::onPlayerTick);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        // A dead soul is told nothing: its data dies with it, and the line would show over the death screen.
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()
                || player.tickCount % CHECK_TICKS != 0 || !SoulLogic.hasSoul(player)) {
            return;
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int place = player.level().dimension() == BleachWorlds.SOUL_SOCIETY ? Rukongai.place(x, z) : Rukongai.NOWHERE;
        if (tells(SoulLogic.get(player), place, player.level().getGameTime())) {
            player.displayClientMessage(Rukongai.placeName(x, z).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Whether a soul now in this place is told of it, which marks it told: only a place other than the last it was told
     * of, with the server naming places, once {@link #QUIET_TICKS} have passed since its last action-bar line. Outside
     * Soul Society the last place is forgotten, so coming back is told. Public for game tests.
     */
    public static boolean tells(SoulData data, int place, long now) {
        if (place == data.placeShown) {
            return false;
        }
        if (place == Rukongai.NOWHERE) {
            data.placeShown = Rukongai.NOWHERE;
            return false;
        }
        if (!BleachConfig.get(BleachConfig.DISTRICT_NAMES) || now - data.actionBarAt < QUIET_TICKS) {
            return false;
        }
        data.placeShown = place;
        return true;
    }

    /** Marks that a line was just put on this soul's action bar, so a place's name waits behind it. Public for tests. */
    public static void noteActionBar(SoulData data, long now) {
        data.actionBarAt = now;
    }

    /** Every line the server puts on a player's action bar, from {@code ServerPlayerMixin}, the place's own included. */
    public static void actionBarShown(ServerPlayer player) {
        if (SoulLogic.hasSoul(player)) {
            noteActionBar(SoulLogic.get(player), player.level().getGameTime());
        }
    }

    /**
     * Where a soul Konsō has just freed goes. In Soul Society it stays, and a Rukongai district it stands in becomes
     * its home; elsewhere, if it is sent on, it goes where the server's {@code [konso] sends_to} says.
     *
     * @return whether it was moved
     */
    public static boolean passedOn(ServerPlayer soul, boolean sendOn) {
        if (soul.level().dimension() == BleachWorlds.SOUL_SOCIETY) {
            int district = Rukongai.district(soul.getBlockX(), soul.getBlockZ());
            if (district >= 1 && district <= SeireiteiPlan.DISTRICTS) {
                setHome(soul, soul.blockPosition());
            }
            return false;
        }
        if (!sendOn) {
            return false;
        }
        return switch (BleachConfig.get(BleachConfig.KONSO_SENDS_TO)) {
            case RUKONGAI -> toRukongai(soul, soul.getRandom());
            case ACADEMY -> AcademyLocator.sendTo(soul);
            case NOWHERE -> false;
        };
    }

    /**
     * Sends a soul just passed on to a district of the Rukongai drawn by {@code [soul_society] konso_inner_bias}, which
     * becomes its home, and tells it where it woke; if no column there will hold it, to the Academy as the shortcut
     * does. Public for game tests, which seed the draw.
     *
     * @return whether it was moved
     */
    public static boolean toRukongai(ServerPlayer soul, RandomSource random) {
        ServerLevel soulSociety = BleachWorlds.level(soul.server, BleachWorlds.SOUL_SOCIETY);
        Vec3 spot = soulSociety != null ? drawHome(soul, soulSociety, random) : null;
        if (spot == null) {
            return AcademyLocator.sendTo(soul);
        }
        AcademyLocator.setDown(soul, soulSociety, spot, facingSeireitei(spot));
        Component where = Rukongai.placeName(Mth.floor(spot.x), Mth.floor(spot.z));
        soul.sendSystemMessage(Component.translatable("message.bleach.konso.rukongai", where)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (ShinigamiLogic.canEnroll(SoulLogic.get(soul))) {
            soul.sendSystemMessage(AcademyLocator.wayLine(BlockPos.containing(spot)).copy()
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return true;
    }

    /**
     * Draws a district and tries up to {@link #LANDING_TRIES} columns of it, each of which may load and make a chunk;
     * the first that will hold the soul is saved as its home and returned. Null if none will.
     */
    @Nullable
    static Vec3 drawHome(ServerPlayer soul, ServerLevel soulSociety, RandomSource random) {
        Rukongai.Draw draw = Rukongai.draw(random, BleachConfig.get(BleachConfig.KONSO_INNER_BIAS));
        for (int i = 0; i < LANDING_TRIES; i++) {
            if (i > 0) {
                draw = Rukongai.pointIn(draw.region(), draw.district(), random);
            }
            Vec3 spot = groundAt(soul, soulSociety, draw.x(), draw.z());
            if (spot != null) {
                setHome(soul, BlockPos.containing(spot));
                return spot;
            }
        }
        return null;
    }

    /**
     * Where a soul set down on this column would stand, on its ground, the column's chunk loaded (and made, if never
     * made) first; null where the ground will not hold it (see {@link SafeLanding#landingSpot}).
     */
    @Nullable
    public static Vec3 groundAt(Entity soul, ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return SafeLanding.landingSpot(soul, level, new Vec3(x + 0.5, top, z + 0.5));
    }

    private static void setHome(ServerPlayer soul, BlockPos home) {
        soul.setData(SoulAttachments.TRAVEL, soul.getData(SoulAttachments.TRAVEL).withRukongaiHome(home));
    }

    /** The yaw that faces the centre column, Seireitei's way, from this spot. */
    public static float facingSeireitei(Vec3 spot) {
        return (float) (Mth.atan2(-spot.z, -spot.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** The Konsō offer's lang key for a Plus in this world: where the server will send it, if anywhere. */
    public static String offerKey(ResourceKey<Level> world) {
        if (world == BleachWorlds.SOUL_SOCIETY) {
            return "message.bleach.offer.konso.nowhere";
        }
        return switch (BleachConfig.get(BleachConfig.KONSO_SENDS_TO)) {
            case RUKONGAI -> "message.bleach.offer.konso.rukongai";
            case ACADEMY -> "message.bleach.offer.konso";
            case NOWHERE -> "message.bleach.offer.konso.nowhere";
        };
    }
}
