package io.github.masteryoga1.bleach.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /bleach}: admin tools for testing and running servers.
 *
 * <pre>
 * /bleach info [player]
 * /bleach soul &lt;players&gt; &lt;state&gt;      (a Shinigami also gets their first tier)
 * /bleach enroll &lt;players&gt;           (Rukongai souls only)
 * /bleach reiryoku &lt;players&gt; &lt;max&gt; [current]
 * /bleach stat &lt;players&gt; &lt;stat&gt; &lt;level&gt;
 * /bleach tier &lt;players&gt; &lt;path&gt; &lt;tier&gt;
 * /bleach chain &lt;players&gt; &lt;seconds&gt;
 * /bleach reset &lt;players&gt;
 * /bleach academy                   (the nearest Academy; builds the guaranteed one if there is none)
 * /bleach garganta &lt;players&gt; [spider|hexapodus|hound|serpent|fishbone_d]   (a tear now; not on Peaceful)
 * /bleach firsthollow &lt;players&gt; &lt;reset|skip|start&gt;   (the Fishbone D encounter)
 * </pre>
 */
public final class BleachCommand {
    private static final SuggestionProvider<CommandSourceStack> STATES = suggest(SoulState.values());
    private static final SuggestionProvider<CommandSourceStack> STATS = suggest(Stat.values());
    private static final SuggestionProvider<CommandSourceStack> PATHS = suggest(PowerPath.values());
    private static final SuggestionProvider<CommandSourceStack> HOLLOW_KINDS = suggest(HollowKind.values());

    private BleachCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bleach")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> info(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("soul")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests(STATES)
                                        .executes(BleachCommand::setState))))
                .then(Commands.literal("enroll")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(BleachCommand::enroll)))
                .then(Commands.literal("reiryoku")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("max", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> setReiryoku(context, -1.0F))
                                        .then(Commands.argument("current", FloatArgumentType.floatArg(0.0F))
                                                .executes(context -> setReiryoku(context,
                                                        FloatArgumentType.getFloat(context, "current")))))))
                .then(Commands.literal("stat")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests(STATS)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                .executes(BleachCommand::setStat)))))
                .then(Commands.literal("tier")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("path", StringArgumentType.word())
                                        .suggests(PATHS)
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 100))
                                                .executes(BleachCommand::setTier)))))
                .then(Commands.literal("chain")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(BleachCommand::setChain))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(BleachCommand::reset)))
                .then(Commands.literal("academy")
                        .executes(context -> academy(context.getSource())))
                .then(Commands.literal("garganta")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> garganta(context, null))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests(HOLLOW_KINDS)
                                        .executes(context -> garganta(context,
                                                StringArgumentType.getString(context, "kind"))))))
                .then(Commands.literal("firsthollow")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("reset")
                                        .executes(context -> changeFirstHollow(context, "reset", hunt -> hunt
                                                .withAwareSincePlayTicks(-1)
                                                .withFirstHollowDone(false)
                                                .withLastArrivalAt(HuntRecord.NEVER))))
                                .then(Commands.literal("skip")
                                        .executes(context -> changeFirstHollow(context, "skip",
                                                hunt -> hunt.withFirstHollowDone(true))))
                                .then(Commands.literal("start")
                                        .executes(BleachCommand::startFirstHollow)))));
    }

    private static int info(CommandSourceStack source, ServerPlayer player) {
        SoulData data = SoulLogic.get(player);
        Consumer<Component> line = text -> source.sendSuccess(() -> text, false);
        line.accept(Component.translatable("commands.bleach.info.header", player.getDisplayName()));
        line.accept(Component.translatable("commands.bleach.info.state", data.state().displayName()));
        line.accept(Component.translatable("commands.bleach.info.reiryoku",
                format(data.reiryoku()), format(data.maxReiryoku())));
        if (data.chainMaxTicks() > 0) {
            line.accept(Component.translatable("commands.bleach.info.chain",
                    data.chainTicks() / 20, data.chainMaxTicks() / 20));
        }
        if (data.isReiatsuReleased()) {
            line.accept(Component.translatable("commands.bleach.info.released"));
        }
        if (ShinigamiLogic.isStudent(data)) {
            line.accept(Component.translatable("commands.bleach.info.enrolled"));
        }
        long now = player.level().getGameTime();
        if (ShinigamiLogic.isPowerLent(data, now)) {
            line.accept(Component.translatable("commands.bleach.info.power_lent",
                    (data.shinigami().powerLentUntil() - now) / 20));
        }
        for (Stat stat : Stat.values()) {
            int level = data.level(stat);
            line.accept(Component.translatable("commands.bleach.info.stat", stat.displayName(), level,
                    data.xp(stat), SoulData.xpToNextLevel(level)));
        }
        for (PowerPath path : PowerPath.values()) {
            if (data.tier(path) > 0) {
                line.accept(Component.translatable("commands.bleach.info.tier", path.displayName(), data.tier(path)));
            }
        }
        HuntRecord hunt = data.hunt();
        line.accept(Component.translatable("commands.bleach.info.hunt", hunt.purified(), hunt.slain(), hunt.sentOn(),
                Component.translatable(hunt.firstHollowDone()
                        ? "commands.bleach.info.first_hollow.done" : "commands.bleach.info.first_hollow.pending")));
        return 1;
    }

    /** Reports the nearest Academy, like {@code /locate}, searching from the source or, outside the Overworld, from spawn. */
    private static int academy(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        BlockPos from = source.getLevel().dimension() == Level.OVERWORLD
                ? BlockPos.containing(source.getPosition()) : overworld.getSharedSpawnPos();
        BlockPos academy = AcademyLocator.nearest(overworld, from);
        if (academy == null) {
            source.sendFailure(Component.translatable("commands.bleach.academy.none"));
            return 0;
        }
        Component coordinates = ComponentUtils.wrapInSquareBrackets(
                        Component.translatable("chat.coordinates", academy.getX(), "~", academy.getZ()))
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/execute in minecraft:overworld run tp @s " + academy.getX() + " ~ " + academy.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(() -> Component.translatable("commands.bleach.academy.success", coordinates), false);
        return 1;
    }

    /**
     * Opens a Garganta near each player now, with Hollows of the kind given or of one their reiatsu
     * draws. It ignores the chance, the cooldown, the cap on nearby Hollows and whether the soul could
     * draw one at all, and leaves the cooldown as it was; it only needs open ground to land on. Peaceful
     * refuses it, since the Hollows would vanish at once.
     */
    private static int garganta(CommandContext<CommandSourceStack> context, @Nullable String name)
            throws CommandSyntaxException {
        HollowKind kind = null;
        if (name != null) {
            kind = byName(HollowKind.values(), name);
            if (kind == null) {
                context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_hollow", name));
                return 0;
            }
        }
        if (peaceful(context.getSource())) {
            return 0;
        }
        int opened = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            if (GargantaEvents.openArrival(player, SoulLogic.get(player), kind, player.level().getGameTime(), true)) {
                opened++;
            } else {
                context.getSource().sendFailure(Component.translatable("commands.bleach.garganta.failed",
                        player.getDisplayName()));
            }
        }
        if (opened == 0) {
            return 0;
        }
        int count = opened;
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.garganta.success", count), true);
        return count;
    }

    /** Resets or skips the first Hollow: {@code key} is the last part of the success message's key. */
    private static int changeFirstHollow(CommandContext<CommandSourceStack> context, String key,
                                         UnaryOperator<HuntRecord> change) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            data.setHunt(change.apply(data.hunt()));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.firsthollow." + key,
                players.size()), true);
        return players.size();
    }

    /** Sends Fishbone D after each player now, as if their first Hollow were due. */
    private static int startFirstHollow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (peaceful(context.getSource())) {
            return 0;
        }
        int started = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            if (GargantaEvents.startFirstHollow(player, SoulLogic.get(player), player.level().getGameTime(), false)) {
                started++;
            } else {
                context.getSource().sendFailure(Component.translatable("commands.bleach.firsthollow.failed",
                        player.getDisplayName()));
            }
        }
        if (started == 0) {
            return 0;
        }
        int count = started;
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.firsthollow.start", count), true);
        return count;
    }

    /** Refuses, with a message, when the world is on Peaceful, where every Hollow would vanish at once. */
    private static boolean peaceful(CommandSourceStack source) {
        if (source.getLevel().getDifficulty() != Difficulty.PEACEFUL) {
            return false;
        }
        source.sendFailure(Component.translatable("commands.bleach.garganta.peaceful"));
        return true;
    }

    private static int setState(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "state");
        SoulState state = byName(SoulState.values(), name);
        if (state == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_state", name));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulLogic.changeState(SoulLogic.get(player), state);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.soul.success",
                state.displayName(), players.size()), true);
        return players.size();
    }

    private static int enroll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int enrolled = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            if (data.state() == SoulState.RUKONGAI) {
                ShinigamiLogic.enroll(data);
                Milestones.check(player, data);
                enrolled++;
            }
        }
        if (enrolled == 0) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.enroll.not_soul"));
            return 0;
        }
        int count = enrolled;
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.enroll.success", count), true);
        return count;
    }

    /** Sets maximum reiryoku and either refills it ({@code current < 0}) or sets the current amount too. */
    private static int setReiryoku(CommandContext<CommandSourceStack> context, float current)
            throws CommandSyntaxException {
        float max = FloatArgumentType.getFloat(context, "max");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            if (!data.isInitialized()) {
                data.markInitialized();
            }
            data.setMaxReiryoku(max);
            data.setReiryoku(current < 0.0F ? max : current);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.reiryoku.success",
                format(max), players.size()), true);
        return players.size();
    }

    private static int setStat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "stat");
        Stat stat = byName(Stat.values(), name);
        if (stat == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_stat", name));
            return 0;
        }
        int level = Math.min(IntegerArgumentType.getInteger(context, "level"), SoulLogic.maxStatLevel());
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            data.setLevel(stat, level);
            SoulLogic.applyStatModifiers(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.stat.success",
                stat.displayName(), level, players.size()), true);
        return players.size();
    }

    private static int setTier(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "path");
        PowerPath path = byName(PowerPath.values(), name);
        if (path == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_path", name));
            return 0;
        }
        int tier = IntegerArgumentType.getInteger(context, "tier");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulLogic.get(player).setTier(path, tier);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.tier.success",
                path.displayName(), tier, players.size()), true);
        return players.size();
    }

    /** Sets how long a Plus's Chain of Fate has left, lengthening the whole chain if needed. */
    private static int setChain(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        int ticks = (int) Math.min(Integer.MAX_VALUE, seconds * 20L);
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            if (data.state() != SoulState.PLUS) {
                continue;
            }
            if (ticks > data.chainMaxTicks()) {
                data.startChain(ticks);
            } else {
                data.setChainTicks(ticks);
            }
            changed++;
        }
        if (changed == 0) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.chain.not_plus"));
            return 0;
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.chain.success",
                seconds, count), true);
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            player.setData(SoulAttachments.SOUL, new SoulData());
            SoulEvents.refresh(player);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.reset.success",
                players.size()), true);
        return players.size();
    }

    private static String format(float value) {
        return Integer.toString((int) Math.floor(value));
    }

    private static <T extends StringRepresentable> T byName(T[] values, String name) {
        for (T value : values) {
            if (value.getSerializedName().equals(name)) {
                return value;
            }
        }
        return null;
    }

    private static SuggestionProvider<CommandSourceStack> suggest(StringRepresentable[] values) {
        return (context, builder) -> SharedSuggestionProvider.suggest(
                Arrays.stream(values).map(StringRepresentable::getSerializedName), builder);
    }
}
