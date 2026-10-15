package io.github.masteryoga1.bleach.soul;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-side hooks for the Shinigami path. The rules themselves are in {@link ShinigamiLogic}. */
public final class ShinigamiEvents {
    private ShinigamiEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ShinigamiEvents::onRespawn);
    }

    /** A Substitute who died is told what their power made of them. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (data.becameShinigami) {
            data.becameShinigami = false;
            player.sendSystemMessage(Component.translatable("message.bleach.became_shinigami")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
