package io.github.masteryoga1.bleach.zanpakuto;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything the Jinzen screen shows at one moment, built by the server as finished text so the
 * client never needs the spirit's data. Each answer brings a new page.
 *
 * @param title    the spirit's name once it is known, otherwise a placeholder
 * @param lines    what the spirit says or the soul sees, in order
 * @param step     the step of the test waiting for an answer, if any
 * @param finished whether nothing is left to do but open one's eyes
 */
public record JinzenPage(JinzenScene scene, Component title, List<Component> lines, Optional<JinzenStep> step,
                         JinzenLook look, boolean finished) {
    /** The most lines a page can carry; the server's pages stay well under this. */
    public static final int MAX_LINES = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, JinzenPage> STREAM_CODEC = StreamCodec.composite(
            JinzenScene.STREAM_CODEC, JinzenPage::scene,
            ComponentSerialization.STREAM_CODEC, JinzenPage::title,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), JinzenPage::lines,
            ByteBufCodecs.optional(JinzenStep.STREAM_CODEC), JinzenPage::step,
            JinzenLook.STREAM_CODEC, JinzenPage::look,
            ByteBufCodecs.BOOL, JinzenPage::finished,
            JinzenPage::new);

    /** Never carries more lines than the wire allows, and never a list that can change afterwards. */
    public JinzenPage {
        lines = List.copyOf(lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines);
    }
}
