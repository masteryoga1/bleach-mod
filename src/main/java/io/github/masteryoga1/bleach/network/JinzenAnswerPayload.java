package io.github.masteryoga1.bleach.network;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the soul's answer to a step of its spirit's test. The server judges it, and
 * answers with the next page if the answer was for the step that is waiting.
 *
 * @param session the sitting the screen belongs to
 * @param step    the step being answered, from 0
 * @param value   the ribbon taken, the position of the answer chosen on the screen, or 1 for a strike
 *                through the jaws (0 for a miss)
 */
public record JinzenAnswerPayload(int session, int step, int value) implements CustomPacketPayload {
    public static final Type<JinzenAnswerPayload> TYPE = new Type<>(BleachMod.id("jinzen_answer"));
    public static final StreamCodec<ByteBuf, JinzenAnswerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JinzenAnswerPayload::session,
            ByteBufCodecs.VAR_INT, JinzenAnswerPayload::step,
            ByteBufCodecs.VAR_INT, JinzenAnswerPayload::value,
            JinzenAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(JinzenAnswerPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive()) {
            JinzenPage page = JinzenLogic.answer(player, SoulLogic.get(player), payload.session(), payload.step(),
                    payload.value(), player.level().getGameTime());
            if (page != null) {
                BleachNetwork.openJinzen(player, payload.session(), page);
            }
        }
    }
}
