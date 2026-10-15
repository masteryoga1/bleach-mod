package io.github.masteryoga1.bleach.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /bleach}: admin tools for testing and running servers.
 *
 * <pre>
 * /bleach info [player]
 * /bleach soul &lt;players&gt; &lt;state&gt;
 * /bleach reiryoku &lt;players&gt; &lt;max&gt; [current]
 * /bleach stat &lt;players&gt; &lt;stat&gt; &lt;level&gt;
 * /bleach tier &lt;players&gt; &lt;path&gt; &lt;tier&gt;
 * /bleach chain &lt;players&gt; &lt;seconds&gt;
 * /bleach reset &lt;players&gt;
 * </pre>
 */
public final class BleachCommand {
    private static final SuggestionProvider<CommandSourceStack> STATES = suggest(SoulState.values());
    private static final SuggestionProvider<CommandSourceStack> STATS = suggest(Stat.values());
    private static final SuggestionProvider<CommandSourceStack> PATHS = suggest(PowerPath.values());

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
                                .executes(BleachCommand::reset))));
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
        return 1;
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
