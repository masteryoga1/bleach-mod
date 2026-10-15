package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** What this client knows about souls: its own in full, and how the players around it look. */
public final class ClientSouls {
    private ClientSouls() {
    }

    /** How an entity's soul looks: the local player's from its own data, others' from what the server sent. */
    public static SoulAppearance appearance(Entity entity) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (entity == self) {
            return SoulAppearance.of(self.getData(SoulAttachments.SOUL));
        }
        return entity.hasData(SoulAttachments.APPEARANCE)
                ? entity.getData(SoulAttachments.APPEARANCE)
                : SoulAppearance.NONE;
    }

    /**
     * Whether the local player should not see this entity: it is a spirit and they are spiritually
     * unaware. Players in creative or spectator mode see everything, and nothing is hidden before
     * the server has sent the player's soul.
     */
    public static boolean isHiddenFromViewer(Entity entity) {
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer == null || entity == viewer || viewer.isCreative() || viewer.isSpectator()) {
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
