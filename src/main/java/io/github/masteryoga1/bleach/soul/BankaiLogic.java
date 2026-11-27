package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Bankai's rules that need no world: who may hold one, the name it takes from its spirit, reaching it and starting the
 * road over, and the soul screen's lines. The soul's side is the {@link BankaiRecord} its bond keeps; the spirit's is
 * {@link ZanpakutoSpirit.Bankai}.
 */
public final class BankaiLogic {
    private BankaiLogic() {
    }

    /**
     * Whether this soul may hold a Bankai: it can wield a zanpakutō and knows its name. That is a Shinigami, a Substitute
     * or a Visored of either kind, and never a student (who has no Shinigami power yet), a Hollow, an Arrancar or a Plus.
     * Power lent away, or the Hollow inside holding the body, stops reaching Bankai and releasing it, never the holding.
     */
    public static boolean mayHold(SoulData data) {
        return ZanpakutoLogic.canWield(data) && data.zanpakuto().named();
    }

    /** The spirit's Bankai's name in the spirit's colour, if it has a Bankai. */
    public static Optional<Component> nameOf(ZanpakutoSpirit spirit) {
        return spirit.bankai().map(bankai -> bankai.name().copy().withColor(spirit.colour()));
    }

    /** The soul reaches Bankai now, by this name (see {@link BankaiRecord#reached}). */
    public static void reach(SoulData data, Component name, long now) {
        ZanpakutoBond bond = data.zanpakuto();
        data.setZanpakuto(bond.withBankai(bond.bankai().reached(now, name)));
    }

    /** The road to Bankai starts over: not reached, no spirit drawn out, no bouts and no time in Shikai counted. */
    public static void forget(SoulData data) {
        data.setZanpakuto(data.zanpakuto().withBankai(BankaiRecord.NONE));
    }

    /**
     * The soul screen's Bankai line, under the zanpakutō's: its name once it is reached, for a soul that may hold it, and
     * null otherwise. It reads the soul alone, so the screen and a test ask the same question.
     */
    @Nullable
    public static Component screenLine(SoulData data) {
        BankaiRecord bankai = data.zanpakuto().bankai();
        if (!bankai.achieved() || !mayHold(data)) {
            return null;
        }
        Component name = bankai.name().getString().isEmpty()
                ? Component.translatable("screen.bleach.soul.bankai.unnamed") : bankai.name();
        return Component.translatable("screen.bleach.soul.bankai", name);
    }

    /**
     * The soul screen's lines for a Tenshintai, under the Bankai's, for a soul that may hold one with a doll on its
     * record: the game days and hours its window has left (hours of 1,000 ticks, rounded up) and where the doll stands,
     * or that it stands in another world than {@code here}; then where to heal between bouts, the hot spring under
     * Sōkyoku Hill. None otherwise. It reads the soul and the clock it is given, the client's level's on the screen,
     * which every world shares, so the screen and a test ask the same question.
     */
    public static List<Component> dollLines(SoulData data, long now, ResourceKey<Level> here) {
        Optional<GlobalPos> doll = data.zanpakuto().bankai().doll();
        if (!mayHold(data) || doll.isEmpty()) {
            return List.of();
        }
        BlockPos at = doll.get().pos();
        Component where = doll.get().dimension().equals(here)
                ? Component.translatable("screen.bleach.soul.tenshintai.at", at.getX(), at.getY(), at.getZ())
                : Component.translatable("screen.bleach.soul.tenshintai.away");
        long hours = Math.max(0L, (data.zanpakuto().bankai().windowEndsAt() - now + 999L) / 1_000L);
        return List.of(Component.translatable("screen.bleach.soul.tenshintai", hours / 24L, hours % 24L, where),
                Component.translatable("screen.bleach.soul.tenshintai.spring"));
    }
}
