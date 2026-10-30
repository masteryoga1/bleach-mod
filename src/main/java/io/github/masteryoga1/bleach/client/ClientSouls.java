package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** What this client knows about souls: its own in full, and how the players around it look. */
public final class ClientSouls {
    private ClientSouls() {
    }

    /**
     * How an entity's soul looks: the local player's from its own data, others' from what the server sent.
     * The local player's Hollow jaw (and how long its hold is), cracked mask and the half mask its inner Hollow
     * forms when it seizes the body come from the server too, which alone knows them, and so does an Arrancar's
     * Resurrección, with the Vasto Lorde's bone it brings back to one that rose from a Vasto Lorde.
     */
    public static SoulAppearance appearance(Entity entity) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (entity == self) {
            SoulAppearance own = SoulAppearance.of(self, self.getData(SoulAttachments.SOUL));
            // The devouring, cracked and taken-over bits live only on the server, which sends them to the owner too,
            // and so does an Arrancar's release, a transient, with the Vasto Lorde's bone it brings back. Those two
            // are taken for an Arrancar only: a Hollow's own data tells its form more freshly than a look sent
            // before the form changed.
            int fromServer = HollowPlayerLogic.LOOK_DEVOURING | HollowPlayerLogic.LOOK_DEVOURING_PLUS
                    | HollowPlayerLogic.LOOK_MASK_CRACKED | HollowPlayerLogic.LOOK_TAKEN_OVER;
            if (own.state() == SoulState.ARRANCAR) {
                fromServer |= HollowPlayerLogic.LOOK_RESURRECCION | HollowPlayerLogic.LOOK_VASTO_LORDE;
            }
            int sent = self.hasData(SoulAttachments.APPEARANCE)
                    ? self.getData(SoulAttachments.APPEARANCE).hollowLook() & fromServer
                    : 0;
            return sent == 0 ? own : own.withHollowLook(own.hollowLook() | sent);
        }
        return entity.hasData(SoulAttachments.APPEARANCE)
                ? entity.getData(SoulAttachments.APPEARANCE)
                : SoulAppearance.NONE;
    }

    /**
     * Whether the local player should not see this entity: it is shut away in a Caja Negación, which hides it from
     * everyone but itself and spectators, creative players included, or it is a spirit and they are spiritually
     * unaware. Players in creative or spectator mode see every spirit, and none is hidden before the server has sent
     * the player's soul.
     */
    public static boolean isHiddenFromViewer(Entity entity) {
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || entity == viewer) {
            return false;
        }
        // Shut away in a Caja Negación, it is out of this world for all but spectators, creative players too.
        if (CajaNegacion.hidden(entity)) {
            return !viewer.isSpectator();
        }
        if (viewer.isCreative() || viewer.isSpectator()) {
            return false;
        }
        SoulData own = viewer.getData(SoulAttachments.SOUL);
        if (!own.isInitialized() || SoulLogic.isAware(own)) {
            return false;
        }
        if (entity instanceof Player) {
            return appearance(entity).state().isSpirit();
        }
        return entity.getType().is(SoulLogic.SPIRITS);
    }
}
