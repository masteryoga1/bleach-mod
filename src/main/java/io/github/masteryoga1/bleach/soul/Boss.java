package io.github.masteryoga1.bleach.soul;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * One boss of a kind the server holds one of at a time: what {@link BossLedger} keeps a row for and {@code /bleach boss}
 * knows by name. A boss is listed once, by a line in {@link BossLedger}'s table, and its own class implements this.
 * <p>
 * The ledger notes the death of any body this boss owns ({@link #isBody}) by itself: away {@link #returnDays} game days,
 * credited to the player whose blow killed it, if any. Every other way a boss goes (an escape, a lift) is its own code's
 * to mark, with {@link BossLedger#markAway}. Going with its chunk, or leaving for lack of prey, writes nothing: it comes
 * back whole, and none is credited.
 * <p>
 * Methods added here later come with a default, so a boss written against this never needs to change.
 */
public interface Boss {
    /** The boss's id: its row in the ledger and the word {@code /bleach boss} takes. Lower case, digits and _ only. */
    String id();

    /** Its name in command feedback: by default its entity's, {@code entity.bleach.<id>}. */
    default Component name() {
        return Component.translatable("entity.bleach." + id());
    }

    /** Whether this creature's death is this boss's: its body, never a piece of it that dies on its own. */
    boolean isBody(LivingEntity entity);

    /** Game days a death keeps it away, read from its config when it dies. */
    int returnDays();

    /**
     * How many are about now, across every world: alive and loaded, or on their way (a tear that still carries one).
     * Asked on a passed roll, a command or a boss's own check, never every tick.
     */
    int present(MinecraftServer server);

    /**
     * Brings it now for this player, whatever its wait and the world's rules; the command has already refused Peaceful
     * and asked {@link #summonRefusal}. {@code near} is the player named, or the operator who ran it: it is null only
     * from the console with no player named. Changes no row of the ledger.
     *
     * @return null once it is on its way, or what stopped it, said to the operator as a failure
     */
    @Nullable
    Component summon(MinecraftServer server, @Nullable ServerPlayer near);

    /**
     * What refuses a summon now, or null: by default one about, as the server holds one at a time. A boss with a cap of
     * its own, or one kept at a post, says otherwise.
     */
    @Nullable
    default Component summonRefusal(MinecraftServer server) {
        return present(server) > 0 ? Component.translatable("commands.bleach.boss.summon.about", name()) : null;
    }

    /** Lines of its own for {@code /bleach boss <id> status}, after the ledger's. */
    default List<Component> status(MinecraftServer server) {
        return List.of();
    }

    /**
     * Whether it may come by itself now: its wait over (asked first, as it is cheap), then none about. Never the
     * summon's. Pass {@code BossLedger.now(server)}, the ledger's one clock.
     */
    default boolean mayCome(MinecraftServer server, long now) {
        return BossLedger.get(server).mayCome(id(), now) && present(server) <= 0;
    }
}
