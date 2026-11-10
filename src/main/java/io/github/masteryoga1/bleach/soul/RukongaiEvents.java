package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.Rukongai;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Soul Society's places as a soul walks them: the action bar names Seireitei, the Seireiheki, or the Rukongai district
 * and region a soul comes into, once nothing else has been on its action bar for {@link #QUIET_TICKS}.
 */
public final class RukongaiEvents {
    /** How often a soul's place is read, in ticks of its own. */
    private static final int CHECK_TICKS = 20;
    /** How long a place's name waits behind any other action-bar line: as long as the game shows one. */
    public static final int QUIET_TICKS = 60;

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
}
