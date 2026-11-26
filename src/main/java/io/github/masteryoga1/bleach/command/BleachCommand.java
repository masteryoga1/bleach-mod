package io.github.masteryoga1.bleach.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.item.HollowBaitItem;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.quincy.QuincyStage;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.ArrancarEvents;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.ArrancarNpcEvents;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.ArrancarRoster;
import io.github.masteryoga1.bleach.soul.BaitEvents;
import io.github.masteryoga1.bleach.soul.BaitLogic;
import io.github.masteryoga1.bleach.soul.BaitSite;
import io.github.masteryoga1.bleach.soul.BaitSites;
import io.github.masteryoga1.bleach.soul.Boss;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.DevourEvents;
import io.github.masteryoga1.bleach.soul.FieldTrialEvents;
import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.MenosEvolution;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyArts;
import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.QuincyForms;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyNpcEvents;
import io.github.masteryoga1.bleach.soul.QuincyNpcLogic;
import io.github.masteryoga1.bleach.soul.QuincyProgress;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import io.github.masteryoga1.bleach.soul.QuincyTeaching;
import io.github.masteryoga1.bleach.soul.ResurreccionEvents;
import io.github.masteryoga1.bleach.soul.SanreiProgress;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.world.AcademyLocator;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /bleach}: admin tools for testing and running servers.
 *
 * <pre>
 * /bleach info [player]
 * /bleach soul &lt;players&gt; &lt;state&gt;      (a Shinigami also gets their first tier; arrancar tears the mask, below)
 * /bleach enroll &lt;players&gt;           (Rukongai souls only)
 * /bleach trial &lt;players&gt;            (a student's field trial begins where it stands in the Human World)
 * /bleach reiryoku &lt;players&gt; &lt;max&gt; [current]
 * /bleach stat &lt;players&gt; &lt;stat&gt; &lt;level&gt;
 * /bleach tier &lt;players&gt; &lt;path&gt; &lt;tier&gt;   (on an Arrancar, hollow sets the form it rose from)
 * /bleach chain &lt;players&gt; &lt;seconds&gt;
 * /bleach reset &lt;players&gt;
 * /bleach academy                   (where the Academy stands in Soul Society; a click suggests the teleport there)
 * /bleach gate &lt;gate&gt; &lt;lift|drop|status&gt;   (hakutomon, kokuryomon, shoryumon or shuwaimon, from any world)
 * /bleach boss &lt;boss&gt; &lt;status|reset|summon [player]&gt;   (shrieker; summon skips the wait, never on Peaceful
 *                                    or with one about; reset lets it come now and forgets who beat it)
 * /bleach garganta &lt;players&gt; [kind|numero|privaron]   (a tear now; not on Peaceful)
 *                                    (kind: spider|hexapodus|hound|serpent|fishbone_d|gillian|adjuchas|shrieker)
 *                                    (numero: a lone Número; privaron: a Privaron Espada and its Fracción)
 * /bleach firsthollow &lt;players&gt; &lt;reset|skip|start&gt;   (the Fishbone D encounter)
 * /bleach hollow &lt;players&gt; satiety &lt;0..100&gt;   (soul hunger, started if it had not; Hollow players only)
 * /bleach hollow &lt;players&gt; souls &lt;0..100000&gt;   (the souls devoured, for any soul)
 * /bleach hollow &lt;players&gt; reset   (the Hollow record starts over; a Hollow's hunger starts again on its next tick)
 *                                    (an Arrancar's failed tears and release cooldown go too, but not its blade or its rank)
 * /bleach rank list   (every Arrancar on the server's roster, with its rank and maximum reiryoku)
 * /bleach rank &lt;players&gt; espada &lt;1..10&gt;   (one Arrancar, seated there whatever its strength until cleared)
 * /bleach rank &lt;players&gt; clear   (the roster ranks them by their strength again, and forgets a lost seat)
 * /bleach bait &lt;players&gt; &lt;start|stop|wave|finale|status&gt;   (start skips every refusal but the world and Peaceful, and counts as a crush)
 * /bleach bait list
 * /bleach npc &lt;players&gt; &lt;elder|hunter&gt;   (a visit now, with no gates, chance or cooldown)
 * /bleach zanpakuto &lt;players&gt; bond &lt;0..1000&gt;   (the Asauchi bond, with no gates or multiplier)
 * /bleach zanpakuto &lt;players&gt; affinity &lt;power|kido|constant_release&gt; &lt;0..10000&gt;   (the hidden leaning)
 * /bleach zanpakuto &lt;players&gt; spirit &lt;id&gt;
 * /bleach zanpakuto &lt;players&gt; name    (as if the spirit's test was passed)
 * /bleach zanpakuto &lt;players&gt; seal    (a constant release can be released for the first time again)
 * /bleach zanpakuto &lt;players&gt; reset
 * /bleach visored &lt;players&gt; &lt;root|ready|duel|subdue|mask|unmask|takeover|reset&gt;
 *                                    (duel fights here, rooting as needed)
 * /bleach visored &lt;players&gt; train &lt;0..86400&gt;   (seconds the mask has been worn)
 * /bleach quincy &lt;players&gt; bloodline &lt;none|gemischt|echt&gt;   (as if chosen, with no word and no Cross)
 * /bleach quincy &lt;players&gt; training &lt;0..100000&gt;   (raises the tier to match, never lowers it)
 * /bleach quincy &lt;players&gt; reishi &lt;amount&gt;   (up to the soul's maximum)
 * /bleach quincy &lt;players&gt; poison &lt;0..100&gt;
 * /bleach quincy &lt;players&gt; blut &lt;off|vene|arterie&gt;   (skips the unlock, cost and poison checks; upkeep still applies)
 * /bleach quincy &lt;players&gt; glove &lt;none|worn|mastered&gt;   (none takes it away and starts its days over; worn and mastered need tier 3)
 * /bleach quincy &lt;players&gt; letzt &lt;start|seal|ready|restore|reset&gt;   (ready skips the sealed wait, restore every condition)
 * /bleach quincy &lt;players&gt; vollstandig &lt;on|off&gt;   (on skips the unlock, cost and cooldown; restored power only)
 * /bleach quincy &lt;players&gt; ginto &lt;gritz|wolke|heizen|sprenger&gt;   (the spell Gintō cast, known or not)
 * /bleach quincy &lt;players&gt; ransotengai &lt;learn|forget|start|stop&gt;   (start skips whether it is known and the wait after a collapse)
 * /bleach quincy &lt;players&gt; lessons reset   (the Elder's lessons and visits start over; Ransōtengai stays known)
 * /bleach quincy &lt;players&gt; reset   (no Quincy power, and the bloodline is asked again)
 * </pre>
 *
 * <p>{@code /bleach soul … arrancar} makes an Arrancar, with its blade, from the Hollow form the player holds,
 * as if it had torn its own mask: anything below a Gillian (a plain Hollow, or a soul with no Hollow tier)
 * counts as Gillian-born. Setting any other state on an Arrancar ends its Resurrección first, and its blade
 * crumbles within a second.
 */
public final class BleachCommand {
    /** The most Quincy training the command sets. */
    private static final int MAX_TRAINING = 100000;
    /** The most souls devoured the command sets. */
    private static final int MAX_SOULS = 100000;
    /** The most seconds of mask time {@code /bleach visored … train} sets: a day, all of it counted. */
    private static final int MAX_TRAIN_SECONDS = 86_400;
    /** The {@code /bleach garganta} kinds that bring Arrancar: a lone Número, or a Privaron Espada and its Fracción. */
    private static final String NUMERO = "numero";
    private static final String PRIVARON = "privaron";
    private static final SuggestionProvider<CommandSourceStack> STATES = suggest(SoulState.values());
    private static final SuggestionProvider<CommandSourceStack> STATS = suggest(Stat.values());
    private static final SuggestionProvider<CommandSourceStack> PATHS = suggest(PowerPath.values());
    /** What a Garganta can bring: the Hollow kinds that come by tear, and the Arrancar. */
    private static final SuggestionProvider<CommandSourceStack> GARGANTA_KINDS = (context, builder) ->
            SharedSuggestionProvider.suggest(Stream.concat(Arrays.stream(HollowKind.values()).filter(HollowKind::comesByTear)
                    .map(HollowKind::getSerializedName), Stream.of(NUMERO, PRIVARON)), builder);
    private static final SuggestionProvider<CommandSourceStack> TYPES = suggest(ZanpakutoType.values());
    private static final SuggestionProvider<CommandSourceStack> BLOODLINES = suggest(Bloodline.values());
    private static final SuggestionProvider<CommandSourceStack> BLUT_MODES = suggest(BlutMode.values());
    private static final SuggestionProvider<CommandSourceStack> GLOVE_SETTINGS = suggest(GloveSetting.values());
    private static final SuggestionProvider<CommandSourceStack> LETZT_ACTIONS = suggest(LetztAction.values());
    private static final SuggestionProvider<CommandSourceStack> VISORED_ACTIONS = suggest(VisoredAction.values());
    private static final SuggestionProvider<CommandSourceStack> TOGGLES = suggest(Toggle.values());
    private static final SuggestionProvider<CommandSourceStack> GINTO_SPELLS = suggest(GintoSpell.values());
    private static final SuggestionProvider<CommandSourceStack> BAIT_ACTIONS = suggest(BaitAction.values());
    private static final SuggestionProvider<CommandSourceStack> RANSOTENGAI_ACTIONS = suggest(RansotengaiAction.values());
    /** The great gates, by the names a command takes. */
    private static final SuggestionProvider<CommandSourceStack> GATES = (context, builder) ->
            SharedSuggestionProvider.suggest(GateState.NAMES, builder);
    /** The bosses the ledger lists, by the ids a command takes. */
    private static final SuggestionProvider<CommandSourceStack> BOSSES = (context, builder) ->
            SharedSuggestionProvider.suggest(BossLedger.bosses().stream().map(Boss::id), builder);
    /** The spirits the world loaded, read from the server's registry. */
    private static final SuggestionProvider<CommandSourceStack> SPIRITS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(ZanpakutoSpirits.registry(context.getSource().getLevel())
                    .map(Registry::keySet).orElse(Set.of()), builder);

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
                .then(Commands.literal("trial")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(BleachCommand::trial)))
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
                .then(Commands.literal("gate")
                        .then(Commands.argument("gate", StringArgumentType.word())
                                .suggests(GATES)
                                .then(Commands.literal("lift").executes(context -> gate(context, true, true)))
                                .then(Commands.literal("drop").executes(context -> gate(context, true, false)))
                                .then(Commands.literal("status").executes(context -> gate(context, false, false)))))
                .then(Commands.literal("boss")
                        .then(Commands.argument("boss", StringArgumentType.word())
                                .suggests(BOSSES)
                                .then(Commands.literal("status").executes(BleachCommand::bossStatus))
                                .then(Commands.literal("reset").executes(BleachCommand::bossReset))
                                .then(Commands.literal("summon")
                                        .executes(context -> bossSummon(context, context.getSource().getPlayer()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> bossSummon(context,
                                                        EntityArgument.getPlayer(context, "player")))))))
                .then(Commands.literal("garganta")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> garganta(context, null))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests(GARGANTA_KINDS)
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
                                        .executes(BleachCommand::startFirstHollow))))
                .then(Commands.literal("hollow")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("satiety")
                                        .then(Commands.argument("satiety",
                                                        FloatArgumentType.floatArg(0.0F, HollowProgress.MAX_SATIETY))
                                                .executes(BleachCommand::setSatiety)))
                                .then(Commands.literal("souls")
                                        .then(Commands.argument("souls", IntegerArgumentType.integer(0, MAX_SOULS))
                                                .executes(BleachCommand::setSouls)))
                                .then(Commands.literal("reset")
                                        .executes(BleachCommand::resetHollow))))
                .then(Commands.literal("rank")
                        .then(Commands.literal("list")
                                .executes(context -> rankList(context.getSource())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("espada")
                                        .then(Commands.argument("seat", IntegerArgumentType.integer(1, ArrancarRank.SEATS))
                                                .executes(BleachCommand::seatEspada)))
                                .then(Commands.literal("clear")
                                        .executes(BleachCommand::clearRank))))
                .then(Commands.literal("bait")
                        .then(Commands.literal("list")
                                .executes(context -> baitList(context.getSource())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .suggests(BAIT_ACTIONS)
                                        .executes(context -> bait(context,
                                                StringArgumentType.getString(context, "action"))))))
                .then(Commands.literal("npc")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("elder")
                                        .executes(context -> spawnNpc(context, true)))
                                .then(Commands.literal("hunter")
                                        .executes(context -> spawnNpc(context, false)))))
                .then(Commands.literal("zanpakuto")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("bond")
                                        .then(Commands.argument("bond", IntegerArgumentType.integer(0, ZanpakutoBond.FULL))
                                                .executes(BleachCommand::setBond)))
                                .then(Commands.literal("affinity")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(TYPES)
                                                .then(Commands.argument("value",
                                                                IntegerArgumentType.integer(0, ZanpakutoBond.MAX_AFFINITY))
                                                        .executes(BleachCommand::setAffinity))))
                                .then(Commands.literal("spirit")
                                        .then(Commands.argument("spirit", ResourceLocationArgument.id())
                                                .suggests(SPIRITS)
                                                .executes(BleachCommand::setSpirit)))
                                .then(Commands.literal("name")
                                        .executes(BleachCommand::learnName))
                                .then(Commands.literal("seal")
                                        .executes(BleachCommand::sealZanpakuto))
                                .then(Commands.literal("reset")
                                        .executes(BleachCommand::resetZanpakuto))))
                .then(Commands.literal("visored")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("train")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, MAX_TRAIN_SECONDS))
                                                .executes(BleachCommand::visoredTrain)))
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .suggests(VISORED_ACTIONS)
                                        .executes(BleachCommand::visored))))
                .then(Commands.literal("quincy")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("bloodline")
                                        .then(Commands.argument("bloodline", StringArgumentType.word())
                                                .suggests(BLOODLINES)
                                                .executes(BleachCommand::setBloodline)))
                                .then(Commands.literal("training")
                                        .then(Commands.argument("training",
                                                        IntegerArgumentType.integer(0, MAX_TRAINING))
                                                .executes(BleachCommand::setTraining)))
                                .then(Commands.literal("reishi")
                                        .then(Commands.argument("reishi", FloatArgumentType.floatArg(0.0F))
                                                .executes(BleachCommand::setReishi)))
                                .then(Commands.literal("poison")
                                        .then(Commands.argument("poison",
                                                        FloatArgumentType.floatArg(0.0F, QuincyProgress.MAX_POISON))
                                                .executes(BleachCommand::setPoison)))
                                .then(Commands.literal("blut")
                                        .then(Commands.argument("blut", StringArgumentType.word())
                                                .suggests(BLUT_MODES)
                                                .executes(BleachCommand::setBlut)))
                                .then(Commands.literal("glove")
                                        .then(Commands.argument("glove", StringArgumentType.word())
                                                .suggests(GLOVE_SETTINGS)
                                                .executes(BleachCommand::setGlove)))
                                .then(Commands.literal("letzt")
                                        .then(Commands.argument("action", StringArgumentType.word())
                                                .suggests(LETZT_ACTIONS)
                                                .executes(BleachCommand::letzt)))
                                .then(Commands.literal("vollstandig")
                                        .then(Commands.argument("vollstandig", StringArgumentType.word())
                                                .suggests(TOGGLES)
                                                .executes(BleachCommand::setVollstandig)))
                                .then(Commands.literal("ginto")
                                        .then(Commands.argument("spell", StringArgumentType.word())
                                                .suggests(GINTO_SPELLS)
                                                .executes(BleachCommand::setGinto)))
                                .then(Commands.literal("ransotengai")
                                        .then(Commands.argument("action", StringArgumentType.word())
                                                .suggests(RANSOTENGAI_ACTIONS)
                                                .executes(context -> ransotengai(context,
                                                        StringArgumentType.getString(context, "action")))))
                                .then(Commands.literal("lessons")
                                        .then(Commands.literal("reset")
                                                .executes(BleachCommand::resetLessons)))
                                .then(Commands.literal("reset")
                                        .executes(BleachCommand::resetQuincy)))));
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
        if (!data.hollow().equals(HollowProgress.NONE)) {
            line.accept(hollowLine(data));
        }
        if (ArrancarLogic.isArrancar(data)) {
            line.accept(arrancarLine(player, data, now));
        } else if (HollowPlayerLogic.isHollow(data) && data.hollow().arrancar().failures() > 0) {
            line.accept(tearLine(data, now));
        }
        ZanpakutoBond bond = data.zanpakuto();
        line.accept(Component.translatable("commands.bleach.info.zanpakuto", bond.bond(), ZanpakutoBond.FULL,
                bond.spirit().map(ResourceLocation::toString).orElse("-"),
                bond.named() ? Component.translatable("commands.bleach.info.zanpakuto.named", bond.name())
                        : Component.translatable("commands.bleach.info.zanpakuto.unnamed")));
        Map<ZanpakutoType, Integer> effective = ZanpakutoLogic.effectiveAffinity(data);
        line.accept(Component.translatable("commands.bleach.info.affinity",
                bond.affinity(ZanpakutoType.POWER), effective.get(ZanpakutoType.POWER),
                bond.affinity(ZanpakutoType.KIDO), effective.get(ZanpakutoType.KIDO),
                bond.affinity(ZanpakutoType.CONSTANT_RELEASE), effective.get(ZanpakutoType.CONSTANT_RELEASE)));
        QuincyProgress quincy = data.quincy();
        line.accept(Component.translatable("commands.bleach.info.quincy", QuincyLogic.bloodline(data).displayName(),
                Component.translatable(quincy.decided()
                        ? "commands.bleach.info.quincy.chosen" : "commands.bleach.info.quincy.undecided"),
                quincy.training(), format(quincy.reishi()), format(QuincyLogic.maxReishi(data)),
                format(quincy.poison()), quincy.blut().displayName(), quincy.destroyed()));
        QuincyArts arts = quincy.arts();
        SanreiProgress glove = arts.glove();
        line.accept(Component.translatable("commands.bleach.info.quincy_arts", arts.stage().getSerializedName(),
                gloveWord(glove), glove.days(), BleachConfig.get(BleachConfig.SANREI_DAYS_TO_MASTER),
                QuincyArtsLogic.gloveToday(glove, now, player.level().getDayTime()),
                artsClock(arts, now), formsWord(data), QuincyArtsLogic.formatTicks(arts.forms().vollReadyAt() - now),
                QuincyArtsLogic.formatTicks(arts.forms().lichtRegenReadyAt() - now)));
        return 1;
    }

    /**
     * The Hollow record for {@code /bleach info}: soul hunger as it stands now, less what is still owed,
     * and its band (or a dash and "not started"), the souls devoured, the diet and the sight.
     */
    private static Component hollowLine(SoulData data) {
        HollowProgress hollow = data.hollow();
        float satiety = HollowBody.satiety(data);
        String band = hollow.hungerStarted() ? HollowPlayerLogic.band(satiety).name().toLowerCase(Locale.ROOT) : "unset";
        return Component.translatable("commands.bleach.info.hollow", hollow.hungerStarted() ? format(satiety) : "-",
                Component.translatable("commands.bleach.info.hollow.band." + band), hollow.souls(), hollow.pluses(),
                hollow.totalHollows(), hollow.players(), Component.translatable(hollow.sight()
                        ? "commands.bleach.info.hollow.sight.on" : "commands.bleach.info.hollow.sight.off"));
    }

    /**
     * The Arrancar for {@code /bleach info}: the form it rose from, whether it tore free or was perfected, its
     * blade, whether it is released, settling after a release (with the seconds left) or sealed, and its rank.
     */
    private static Component arrancarLine(ServerPlayer player, SoulData data, long now) {
        HollowProgress.Arrancar record = data.hollow().arrancar();
        Component release;
        if (ArrancarLogic.released(data)) {
            release = Component.translatable("commands.bleach.info.arrancar.released");
        } else if (now < record.releaseReadyAt()) {
            release = Component.translatable("commands.bleach.info.arrancar.settling",
                    secondsUntil(record.releaseReadyAt(), now));
        } else {
            release = Component.translatable("commands.bleach.info.arrancar.sealed");
        }
        return Component.translatable("commands.bleach.info.arrancar", ArrancarLogic.origin(data).displayName(),
                Component.translatable(record.perfected()
                        ? "commands.bleach.info.arrancar.perfected" : "commands.bleach.info.arrancar.natural"),
                ArrancarLogic.blade(player.getUUID()).displayName(), release, ArrancarRank.of(data).label());
    }

    /** A Hollow's failed tries at its own mask for {@code /bleach info}, and the seconds before it may try again. */
    private static Component tearLine(SoulData data, long now) {
        HollowProgress.Arrancar record = data.hollow().arrancar();
        long retryAt = record.lastTearAt()
                + BleachConfig.get(BleachConfig.TEAR_RETRY_MINUTES) * (long) MenosEvolution.MINUTE_TICKS;
        return Component.translatable("commands.bleach.info.tear", record.failures(), secondsUntil(retryAt, now));
    }

    /** Whole seconds, rounded up, from now until a game time; 0 once it has passed. */
    private static long secondsUntil(long time, long now) {
        long ticks = time - now;
        return ticks <= 0L ? 0L : (ticks + 19L) / 20L;
    }

    /** The glove in a word for {@code /bleach info}: none, given, worn, mastered (on the hand) or torn (off it, after Letzt Stil). */
    private static String gloveWord(SanreiProgress glove) {
        if (glove.worn()) {
            return glove.mastered() ? "mastered" : "worn";
        }
        if (glove.mastered()) {
            return "torn";
        }
        return glove.given() ? "given" : "none";
    }

    /** The time Letzt Stil has left, or the time sealed against the wait before the power can return, or a dash. */
    private static String artsClock(QuincyArts arts, long now) {
        return switch (arts.stage()) {
            case LETZT_STIL -> QuincyArtsLogic.formatTicks(arts.letztEndsAt() - now) + " left";
            case SEALED -> QuincyArtsLogic.formatTicks(now - arts.stageSince()) + " / "
                    + QuincyArtsLogic.formatTicks(QuincyArtsLogic.sealedWaitTicks()) + " sealed";
            case UNTOUCHED, RESTORED -> "-";
        };
    }

    /** Vollständig in a few words: on with what goes with it, off, or locked with the training left. */
    private static String formsWord(SoulData data) {
        QuincyForms forms = QuincyArtsLogic.arts(data).forms();
        StringBuilder words = new StringBuilder();
        if (forms.vollstandig()) {
            words.append("on");
            if (forms.sklaverei()) {
                words.append(", Sklaverei");
            }
            if (forms.haloBroken()) {
                words.append(", halo broken");
            }
        } else if (QuincyArtsLogic.isRestored(data) && !QuincyArtsLogic.vollUnlocked(data)) {
            words.append("locked, ").append(QuincyArtsLogic.vollTrainingLeft(data)).append(" training to go");
        } else {
            words.append("off");
        }
        if (forms.landingGrace()) {
            words.append(", landing cushioned");
        }
        return words.toString();
    }

    /** Reports where the Academy stands in Soul Society, with a click that suggests the command to go there. */
    private static int academy(CommandSourceStack source) {
        if (BleachWorlds.level(source.getServer(), BleachWorlds.SOUL_SOCIETY) == null) {
            source.sendFailure(Component.translatable("commands.bleach.academy.none"));
            return 0;
        }
        BlockPos gate = AcademyLocator.GATE;
        Component coordinates = ComponentUtils.wrapInSquareBrackets(
                        Component.translatable("chat.coordinates", gate.getX(), gate.getY(), gate.getZ()))
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, AcademyLocator.teleportCommand()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(() -> Component.translatable("commands.bleach.academy.success", coordinates), false);
        return 1;
    }

    /**
     * Lifts or drops one of the Seireiheki's great gates, or says where it is, from any world: a gate not yet laid, or
     * already lifted or rising, or down or falling, is only reported.
     */
    private static int gate(CommandContext<CommandSourceStack> context, boolean move, boolean lift) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "gate");
        int gate = GateState.NAMES.indexOf(name.toLowerCase(Locale.ROOT));
        if (gate < 0) {
            source.sendFailure(Component.translatable("commands.bleach.unknown_gate", name));
            return 0;
        }
        // The level the gates tick in, as the stamp has it: no test's stand-in.
        ServerLevel soulSociety = source.getServer().getLevel(BleachWorlds.SOUL_SOCIETY);
        if (soulSociety == null) {
            source.sendFailure(Component.translatable("commands.bleach.academy.none"));
            return 0;
        }
        GateState gates = GateState.get(soulSociety);
        Component gateName = Rukongai.Region.values()[gate].gateName();
        if (move && gates.laid(gate) && (lift ? gates.lift(soulSociety, gate) : gates.drop(soulSociety, gate))) {
            source.sendSuccess(() -> Component.translatable(lift ? "commands.bleach.gate.lift" : "commands.bleach.gate.drop",
                    gateName), true);
            return 1;
        }
        String key = switch (gates.status(gate)) {
            case DOWN -> "commands.bleach.gate.status.down";
            case LIFTED -> "commands.bleach.gate.status.lifted";
            case RISING -> "commands.bleach.gate.status.rising";
            case FALLING -> "commands.bleach.gate.status.falling";
            case WAITING -> "commands.bleach.gate.status.waiting";
            case NOT_LAID -> "commands.bleach.gate.status.not_laid";
        };
        source.sendSuccess(() -> Component.translatable(key, gateName), false);
        return 1;
    }

    /** The boss this command names, in any case, or null, the operator told so. */
    @Nullable
    private static Boss boss(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "boss");
        Boss boss = BossLedger.boss(name.toLowerCase(Locale.ROOT));
        if (boss == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_boss", name));
        }
        return boss;
    }

    /**
     * Says how a boss stands, to the one who asked only: how many are about, whether it may come again or how long it
     * is away, how many souls have beaten it, its kept id if it has one, then its own lines.
     */
    private static int bossStatus(CommandContext<CommandSourceStack> context) {
        Boss boss = boss(context);
        if (boss == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        BossLedger ledger = BossLedger.get(server);
        Consumer<Component> line = text -> source.sendSuccess(() -> text, false);
        line.accept(Component.translatable("commands.bleach.boss.status", boss.name(), boss.present(server)));
        long left = ledger.waitLeft(boss.id(), BossLedger.now(server));
        line.accept(left <= 0L ? Component.translatable("commands.bleach.boss.status.free")
                : Component.translatable("commands.bleach.boss.status.away",
                        String.format(Locale.ROOT, "%.1f", left / (double) BossLedger.TICKS_PER_DAY), left));
        int beaten = ledger.beatenBy(boss.id()).size();
        line.accept(beaten == 0 ? Component.translatable("commands.bleach.boss.status.unbeaten")
                : Component.translatable("commands.bleach.boss.status.beaten", beaten));
        UUID kept = ledger.kept(boss.id());
        if (kept != null) {
            line.accept(Component.translatable("commands.bleach.boss.status.kept", kept.toString()));
        }
        boss.status(server).forEach(line);
        return 1;
    }

    /** Lets a boss come again now and forgets who beat it; a kept boss keeps its one id. */
    private static int bossReset(CommandContext<CommandSourceStack> context) {
        Boss boss = boss(context);
        if (boss == null) {
            return 0;
        }
        BossLedger.get(context.getSource().getServer()).reset(boss.id());
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.boss.reset", boss.name()), true);
        return 1;
    }

    /**
     * Brings a boss now, for the player given or else the one who ran it (none from the console), whatever its wait,
     * which it leaves as it was. Peaceful refuses it, since it would vanish at once, and so does whatever the boss
     * itself refuses for ({@link Boss#summonRefusal}): by default one already about, as the server holds one at a time.
     */
    private static int bossSummon(CommandContext<CommandSourceStack> context, @Nullable ServerPlayer near) {
        Boss boss = boss(context);
        CommandSourceStack source = context.getSource();
        if (boss == null || peaceful(source, "commands.bleach.boss.peaceful")) {
            return 0;
        }
        Component refused = boss.summonRefusal(source.getServer());
        if (refused == null) {
            refused = boss.summon(source.getServer(), near);
        }
        if (refused != null) {
            source.sendFailure(refused);
            return 0;
        }
        source.sendSuccess(() -> near != null
                ? Component.translatable("commands.bleach.boss.summon.success", boss.name(), near.getDisplayName())
                : Component.translatable("commands.bleach.boss.summon.success_alone", boss.name()), true);
        return 1;
    }

    /**
     * Opens a Garganta near each player now, with Hollows of the kind given or of one their reiatsu
     * draws. It ignores the chance, the cooldown, the cap on nearby Hollows and whether the soul could
     * draw one at all, and leaves the cooldown as it was; it only needs open ground to land on. Peaceful
     * refuses it, since the Hollows would vanish at once. {@code numero} and {@code privaron} bring
     * Arrancar instead (see {@link #arrancarGarganta}).
     */
    private static int garganta(CommandContext<CommandSourceStack> context, @Nullable String name)
            throws CommandSyntaxException {
        if (NUMERO.equals(name) || PRIVARON.equals(name)) {
            return arrancarGarganta(context, PRIVARON.equals(name));
        }
        HollowKind kind = null;
        if (name != null) {
            kind = byName(HollowKind.values(), name);
            // Grand Fisher comes by no tear: it is no kind a Garganta knows.
            if (kind == null || !kind.comesByTear()) {
                context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_hollow", name));
                return 0;
            }
        }
        if (peaceful(context.getSource(), "commands.bleach.garganta.peaceful")) {
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

    /**
     * Opens a Garganta near each player now that brings Arrancar: a lone Número, or with {@code group} a
     * Privaron Espada and its Fracción. It ignores the chance, the night, the soul's reiatsu, the cooldown,
     * the server's limit and the spacing, and leaves the cooldown as it was; they still leave after their
     * lifetime, or once idle. Peaceful refuses it, since they would vanish at once.
     */
    private static int arrancarGarganta(CommandContext<CommandSourceStack> context, boolean group)
            throws CommandSyntaxException {
        if (peaceful(context.getSource(), "commands.bleach.garganta.peaceful")) {
            return 0;
        }
        int opened = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            if (ArrancarNpcEvents.openArrival(player, SoulLogic.get(player), group, player.level().getGameTime(), true)) {
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
        if (peaceful(context.getSource(), "commands.bleach.garganta.peaceful")) {
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

    /** Refuses, with this key's line, when the world is on Peaceful, where every Hollow and boss would vanish at once. */
    private static boolean peaceful(CommandSourceStack source, String key) {
        if (source.getLevel().getDifficulty() != Difficulty.PEACEFUL) {
            return false;
        }
        source.sendFailure(Component.translatable(key));
        return true;
    }

    // ---- /bleach hollow ----

    /**
     * Sets a Hollow's soul hunger, starting it if it had not started, with nothing still owed. Anyone
     * who is not a Hollow is left alone, and if no one is, the command fails. The band and its
     * messages follow on the next tick.
     */
    private static int setSatiety(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float satiety = FloatArgumentType.getFloat(context, "satiety");
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            if (!HollowPlayerLogic.isHollow(data)) {
                continue;
            }
            HollowBody.setSatiety(data, satiety);
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        if (changed == 0) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.hollow.not_hollow"));
            return 0;
        }
        int count = changed;
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.hollow.satiety.success",
                format(satiety), count), true);
        return count;
    }

    /** Sets the count of souls devoured, for any soul, so the milestones it gates can be tried. */
    private static int setSouls(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int souls = IntegerArgumentType.getInteger(context, "souls");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            data.setHollow(data.hollow().withSouls(souls));
            Milestones.check(player, data);
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.hollow.souls.success",
                souls, players.size()), true);
        return players.size();
    }

    /**
     * Starts the Hollow record over: no hunger, no souls, no meals, sight on. A Hollow's hunger starts
     * again at the start value on its next tick. An Arrancar's failed tears and release cooldown go too,
     * but its blade's forging is kept, so the blade it holds stays its own, and its rank, so the roster
     * does not number it again as if it were newly born.
     */
    private static int resetHollow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            // Settle what is still owed first, so none of it comes off the fresh hunger.
            HollowBody.flushDebt(data);
            int forging = data.hollow().arrancar().forging();
            ArrancarRank rank = data.hollow().arrancar().rank();
            data.setHollow(HollowProgress.NONE.withArrancar(HollowProgress.Arrancar.NONE.withForging(forging)
                    .withRank(rank)));
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.hollow.reset.success",
                players.size()), true);
        return players.size();
    }

    // ---- /bleach rank ----

    /**
     * Lists every Arrancar on the server's roster, online or not: its name, rank and maximum reiryoku, and whether
     * it is seated by command or away. The Espada come first by seat, then the Privaron Espada, the Números and
     * the unnumbered. Only the one who asked sees it.
     */
    private static int rankList(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        long now = server.overworld().getGameTime();
        List<ArrancarRoster.Entry> entries = ArrancarRoster.get(server).entries(now);
        int needs = Math.round(ArrancarRank.espadaMinMaxReiryoku());
        source.sendSuccess(() -> Component.translatable("commands.bleach.rank.list", entries.size(), needs), false);
        for (ArrancarRoster.Entry entry : entries) {
            Component suffix = entry.pinned() ? Component.translatable("commands.bleach.rank.list.pinned")
                    : entry.away() ? Component.translatable("commands.bleach.rank.list.away") : Component.empty();
            Component line = Component.translatable("commands.bleach.rank.list.entry", entry.name(),
                    entry.rank().label(), entry.strength(), suffix);
            source.sendSuccess(() -> line, false);
        }
        return entries.size();
    }

    /**
     * Seats one Arrancar in this seat among the Espada whatever its strength, standing in for Aizen's own choice.
     * It holds the seat until it is cleared, stops being an Arrancar or stays away too long, and whoever was seated
     * there by command before is unpinned. The record is put right and sent at once, not a second later.
     */
    private static int seatEspada(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        if (players.size() > 1) {
            source.sendFailure(Component.translatable("commands.bleach.rank.one_player"));
            return 0;
        }
        ServerPlayer player = players.iterator().next();
        SoulData data = SoulLogic.get(player);
        if (!ArrancarLogic.isArrancar(data)) {
            source.sendFailure(Component.translatable("commands.bleach.rank.not_arrancar"));
            return 0;
        }
        int seat = IntegerArgumentType.getInteger(context, "seat");
        MinecraftServer server = source.getServer();
        long now = server.overworld().getGameTime();
        ArrancarRoster roster = ArrancarRoster.get(server);
        // On the roster first, so there is a place to pin.
        ArrancarRoster.keep(player, data, roster, now);
        roster.pin(player.getUUID(), seat);
        ArrancarRank rank = ArrancarRoster.keep(player, data, roster, now);
        BleachNetwork.syncSoul(player, data);
        source.sendSuccess(() -> Component.translatable("commands.bleach.rank.espada.success", player.getDisplayName(),
                rank.label()), true);
        return 1;
    }

    /**
     * Lets the roster rank each Arrancar among these players by its strength again: unpinned, and with any seat
     * it lost forgotten, so a Privaron Espada is a Número again unless it is strong enough for a seat. Anyone who
     * is not an Arrancar is left alone, and if no one is, the command fails.
     */
    private static int clearRank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long now = server.overworld().getGameTime();
        ArrancarRoster roster = ArrancarRoster.get(server);
        int cleared = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            if (!ArrancarLogic.isArrancar(data)) {
                continue;
            }
            ArrancarRoster.keep(player, data, roster, now);
            roster.clear(player.getUUID());
            ArrancarRoster.keep(player, data, roster, now);
            BleachNetwork.syncSoul(player, data);
            cleared++;
        }
        if (cleared == 0) {
            source.sendFailure(Component.translatable("commands.bleach.rank.not_arrancar"));
            return 0;
        }
        int count = cleared;
        source.sendSuccess(() -> Component.translatable("commands.bleach.rank.clear.success", count), true);
        return count;
    }

    // ---- /bleach bait ----

    /**
     * A Hollow Bait action for each player's site. {@code start} opens one where they stand, as if
     * they had crushed a bait: only another world or Peaceful refuses it, a site they already own is
     * stopped first, and it counts as a crush. {@code stop} ends their site; {@code wave} sends a wave
     * now, still within the cap on Hollows, at them if they are hunted there; {@code finale} cracks
     * the sky over their bait now; {@code status} says how it stands.
     */
    private static int bait(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        BaitAction action = byName(BaitAction.values(), name);
        CommandSourceStack source = context.getSource();
        if (action == null) {
            source.sendFailure(Component.translatable("argument.enum.invalid", name));
            return 0;
        }
        // A wave's Hollows, like the ones a bait draws, would vanish at once.
        if ((action == BaitAction.START || action == BaitAction.WAVE)
                && peaceful(source, "commands.bleach.garganta.peaceful")) {
            return 0;
        }
        ServerLevel overworld = source.getServer().overworld();
        BaitSites sites = BaitSites.get(source.getServer());
        int done = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            if (baitFor(source, overworld, sites, player, action)) {
                done++;
            }
        }
        String key = switch (action) {
            case START -> "commands.bleach.bait.started";
            case STOP -> "commands.bleach.bait.stopped";
            case WAVE -> "commands.bleach.bait.wave";
            case FINALE -> "commands.bleach.bait.finale";
            case STATUS -> null;
        };
        int count = done;
        if (count > 0 && key != null) {
            source.sendSuccess(() -> Component.translatable(key, count), true);
        }
        return count;
    }

    /** Does one bait action for one player, or says why it cannot. Sites live in the Overworld. */
    private static boolean baitFor(CommandSourceStack source, ServerLevel overworld, BaitSites sites,
                                   ServerPlayer player, BaitAction action) {
        long now = overworld.getGameTime();
        if (action == BaitAction.START) {
            SoulData data = SoulLogic.get(player);
            if (BaitEvents.crushRefusal(player, data, player.blockPosition(), true) != null
                    || BaitEvents.start(player, player.blockPosition(), false, null, now) == null) {
                source.sendFailure(Component.translatable("commands.bleach.bait.failed", player.getDisplayName()));
                return false;
            }
            HollowBaitItem.countCrush(player, data);
            BleachNetwork.syncSoul(player, data);
            return true;
        }
        BaitSite site = sites.ownedBy(player.getUUID());
        // A contest is a site too, but has no sky to crack.
        if (site == null || (action == BaitAction.FINALE && site.contest())) {
            source.sendFailure(Component.translatable("commands.bleach.bait.none", player.getDisplayName()));
            return false;
        }
        switch (action) {
            case STOP -> BaitEvents.end(overworld, sites, site, BaitLogic.End.STOPPED);
            case WAVE -> {
                if (BaitEvents.wave(overworld, sites, site, now, waveSouls(overworld, site, player)) == 0) {
                    source.sendFailure(Component.translatable("commands.bleach.garganta.failed", player.getDisplayName()));
                    return false;
                }
            }
            case FINALE -> {
                if (!BaitEvents.startFinale(overworld, sites, site, now)) {
                    source.sendFailure(Component.translatable("commands.bleach.garganta.failed", player.getDisplayName()));
                    return false;
                }
            }
            case STATUS -> source.sendSuccess(() -> baitStatus(player, site), false);
            case START -> {
                // Handled above.
            }
        }
        return true;
    }

    /**
     * The souls a commanded wave counts as hunted: the site's own, all of them the player when the
     * player is among them, so the wave comes for them. The list keeps its length, so the cap on the
     * site's Hollows is the one those souls give.
     */
    private static List<? extends Player> waveSouls(ServerLevel level, BaitSite site, ServerPlayer player) {
        List<Player> hunted = BaitEvents.hunted(level, site);
        boolean huntedHere = hunted.stream().anyMatch(soul -> soul.getUUID().equals(player.getUUID()));
        return huntedHere ? Collections.nCopies(hunted.size(), player) : hunted;
    }

    private static Component baitStatus(ServerPlayer player, BaitSite site) {
        return Component.translatable("commands.bleach.bait.status", player.getDisplayName(), site.arrived(),
                site.waves(), site.ownerKills(), site.rivalKills(), baitTimeLeft(site),
                site.frozenTicks() > 0 ? Component.translatable("commands.bleach.bait.frozen") : Component.empty());
    }

    /** Lists every open site: whose it is, where, what kind, how many Hollows have come and how long it has left. */
    private static int baitList(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<BaitSite> all = List.copyOf(BaitSites.get(server).all());
        source.sendSuccess(() -> Component.translatable("commands.bleach.bait.list", all.size()), false);
        for (BaitSite site : all) {
            ServerPlayer owner = server.getPlayerList().getPlayer(site.owner());
            Component who = owner != null ? owner.getDisplayName() : Component.literal(site.owner().toString());
            BlockPos centre = site.centre();
            String kind = site.contest() ? "contest" : site.inFinale() ? "finale" : "bait";
            Component entry = Component.translatable("commands.bleach.bait.list_entry", who,
                    Component.translatable("chat.coordinates", centre.getX(), centre.getY(), centre.getZ()), kind,
                    site.arrived(), baitTimeLeft(site));
            source.sendSuccess(() -> entry, false);
        }
        return all.size();
    }

    private static String baitTimeLeft(BaitSite site) {
        return QuincyArtsLogic.formatTicks(site.durationTicks() - site.activeTicks());
    }

    // ---- /bleach npc ----

    /**
     * Sends a visiting Quincy to each player now, with none of a visit's gates, chance or cooldown:
     * the Elder, for whatever reason he would come (with none he still comes, with nothing to give),
     * or the Hunter, near them but not for a contest. Either way the visit counts as a natural one:
     * the player is told, and the wait before the next one starts. Each needs open ground near the
     * player.
     */
    private static int spawnNpc(CommandContext<CommandSourceStack> context, boolean elder) throws CommandSyntaxException {
        int sent = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            QuincyNpc npc;
            if (elder) {
                QuincyNpcLogic.Visit reason = QuincyNpcLogic.visitReason(data, now,
                        QuincyArtsLogic.gloveDay(now, level.getDayTime()));
                npc = QuincyNpcEvents.spawnElder(player, data, reason, now);
            } else {
                npc = QuincyNpcEvents.spawnHunter(level, player, player, now);
            }
            if (npc == null) {
                context.getSource().sendFailure(Component.translatable("commands.bleach.npc.failed",
                        player.getDisplayName()));
                continue;
            }
            if (!elder) {
                // Met as when he follows a Garganta: noted, and told. The Elder's visit does both itself.
                HuntRecord hunt = data.hunt();
                data.setHunt(hunt.withBait(hunt.bait().withHunterMet(true).withLastHunterAt(now)));
                player.sendSystemMessage(Component.translatable("message.bleach.hunter.arrives")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            BleachNetwork.syncSoul(player, data);
            sent++;
        }
        if (sent == 0) {
            return 0;
        }
        int count = sent;
        Component kind = elder ? ModEntities.QUINCY_ELDER.get().getDescription()
                : ModEntities.QUINCY_HUNTER.get().getDescription();
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.npc.spawned", kind, count), true);
        return count;
    }

    /**
     * Sets each player's state. {@code arrancar} makes one through the tear's own road, blade and all, unless it
     * is one already; any other state ends an Arrancar's release first and puts its body right after.
     */
    private static int setState(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "state");
        SoulState state = byName(SoulState.values(), name);
        if (state == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_state", name));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            if (state == SoulState.ARRANCAR) {
                if (!ArrancarLogic.isArrancar(data)) {
                    ArrancarEvents.become(player, data, ArrancarLogic.originFor(data), false);
                }
                continue;
            }
            boolean was = ArrancarLogic.isArrancar(data);
            if (was) {
                ResurreccionEvents.end(player, data, player.level().getGameTime(), null);
            }
            SoulLogic.changeState(data, state);
            if (was) {
                HollowBody.reconcile(player, data);
            }
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

    /**
     * Begins each student's field trial where it stands, past the exams' mark and a failed day's wait; refuses, saying
     * why, a soul that is no student, one on the death screen, one out of the Human World and one whose trial is under
     * way.
     */
    private static int trial(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int begun = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            String refusal = !ShinigamiLogic.isStudent(SoulLogic.get(player)) ? "commands.bleach.trial.not_student"
                    : !player.isAlive() ? "commands.bleach.trial.dead"
                    : player.level().dimension() != Level.OVERWORLD ? "commands.bleach.trial.not_human_world"
                    : FieldTrialEvents.running(player) ? "commands.bleach.trial.running" : null;
            if (refusal != null) {
                context.getSource().sendFailure(Component.translatable(refusal, player.getDisplayName()));
                continue;
            }
            FieldTrialEvents.begin(player, player.level().getGameTime());
            begun++;
        }
        int count = begun;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.trial.success", count), true);
        }
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
            // What the old soul had released goes with it, so nothing it held lingers on the body.
            ShikaiEvents.forceSeal(player, SoulLogic.get(player), null);
            QuincyArtsEvents.endAll(player, SoulLogic.get(player));
            QuincyRansotengai.closeTrial(player, SoulLogic.get(player));
            DevourEvents.stopDevour(player, SoulLogic.get(player), null, player.level().getGameTime());
            DevourEvents.stopGnaw(SoulLogic.get(player));
            HollowCero.cancel(player, SoulLogic.get(player));
            GranReyCero.cancel(player, SoulLogic.get(player));
            KidoEvents.dropGather(player, SoulLogic.get(player));
            HollowBody.leaveBodyIfWorn(player, SoulLogic.get(player));
            VisoredEvents.endAll(player, SoulLogic.get(player), player.level().getGameTime());
            FieldTrialEvents.forget(player);
            SoulData fresh = new SoulData();
            QuincyArtsLogic.keepLandingGrace(QuincyArtsLogic.arts(SoulLogic.get(player)), fresh);
            player.setData(SoulAttachments.SOUL, fresh);
            SoulEvents.refresh(player);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.reset.success",
                players.size()), true);
        return players.size();
    }

    // ---- /bleach zanpakuto ----

    /** Sets the Asauchi bond straight, with no gates or multiplier. A full bond locks in the spirit. */
    private static int setBond(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "bond");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            data.setZanpakuto(data.zanpakuto().withBond(value).withWhispered(ZanpakutoLogic.quarterOf(value)));
            if (value >= ZanpakutoBond.FULL) {
                ZanpakutoEvents.ensureSpirit(player, data);
            }
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.zanpakuto.bond",
                value, players.size()), true);
        return players.size();
    }

    /** Sets the raw leaning towards one kind of spirit. */
    private static int setAffinity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "type");
        ZanpakutoType type = byName(ZanpakutoType.values(), name);
        if (type == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_type", name));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            data.setZanpakuto(data.zanpakuto().withAffinity(type, value));
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.zanpakuto.affinity",
                type.displayName(), value, players.size()), true);
        return players.size();
    }

    /**
     * Sets the spirit the blade becomes. A soul that already knows its name learns the new one's
     * instead, and its blades take the new spirit's form at once.
     */
    private static int setSpirit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(context, "spirit");
        Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(context.getSource().getLevel(), id);
        if (spirit.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_spirit", id.toString()));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            boolean changed = !data.zanpakuto().spirit().equals(Optional.of(id));
            ZanpakutoBond bond = data.zanpakuto().withSpirit(Optional.of(id));
            if (bond.named()) {
                ShikaiEvents.forceSeal(player, data, null);
                if (changed) {
                    // A new spirit starts over: its first release and its technique are still to come.
                    bond = bond.withFirstReleaseDone(false).withGetsugaFired(0).withGetsugaNamed(false);
                }
                bond = bond.asNamed(spirit.get().name().copy().withColor(spirit.get().colour()), spirit.get().isConstant());
            }
            data.setZanpakuto(bond);
            ZanpakutoEvents.reconcile(player, data);
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.zanpakuto.spirit",
                id.toString(), players.size()), true);
        return players.size();
    }

    /**
     * Teaches the name as if the spirit's test was passed: the bond fills, a spirit is chosen if there
     * is none, and the blade in hand, the first nameless Asauchi carried or a new one is bound.
     */
    private static int learnName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int named = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            data.setZanpakuto(data.zanpakuto().withBond(ZanpakutoBond.FULL).withWhispered(4));
            ZanpakutoEvents.ensureSpirit(player, data);
            Optional<ResourceLocation> id = data.zanpakuto().spirit();
            Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(player.level(), id);
            if (spirit.isEmpty()) {
                BleachNetwork.syncSoul(player, data);
                context.getSource().sendFailure(Component.translatable("commands.bleach.zanpakuto.name.failed",
                        player.getDisplayName()));
                continue;
            }
            ItemStack stack = bladeToName(player);
            boolean fresh = stack.isEmpty();
            if (fresh) {
                stack = new ItemStack(ModItems.ASAUCHI.get());
            }
            ZanpakutoEvents.learnName(player, data, id.get(), spirit.get(), stack);
            if (fresh && !player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
            BleachNetwork.syncSoul(player, data);
            named++;
        }
        if (named == 0) {
            return 0;
        }
        int count = named;
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.zanpakuto.name", count), true);
        return count;
    }

    /** The nameless Asauchi or own blade in hand, else the first one carried, else none. */
    private static ItemStack bladeToName(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (ZanpakutoLogic.isUnboundAsauchi(held) || ZanpakutoLogic.isOwnBlade(held, player)) {
            return held;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ZanpakutoLogic.isUnboundAsauchi(stack) || ZanpakutoLogic.isOwnBlade(stack, player)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Seals the zanpakutō. A constant release, which never seals otherwise, goes back to waiting for
     * its first release, so it can be replayed.
     */
    private static int sealZanpakuto(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            ShikaiEvents.forceSeal(player, data, "message.bleach.shikai.sealed");
            if (data.zanpakuto().constant()) {
                data.setZanpakuto(data.zanpakuto().withFirstReleaseDone(false));
                Inventory inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    unreleaseConstant(player, inventory.getItem(slot));
                }
            }
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.zanpakuto.seal",
                players.size()), true);
        return players.size();
    }

    /** Puts a released constant blade of the player's own back in its sealed form. */
    private static void unreleaseConstant(ServerPlayer player, ItemStack stack) {
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        if (!ZanpakutoLogic.isOwnBlade(stack, player) || !blade.released() || !blade.constant()) {
            return;
        }
        Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(player.level(), blade.spirit());
        if (spirit.isPresent()) {
            ZanpakutoItem.setReleased(stack, spirit.get(), false);
        } else {
            ZanpakutoItem.applyForm(stack, ZanpakutoSpirit.Form.ASAUCHI);
            stack.set(ModComponents.ZANPAKUTO_BLADE.get(), blade.withStage(ZanpakutoStage.SEALED));
        }
    }

    /** Forgets the bond, the leaning and the name. The soul's blades go back to nameless Asauchi. */
    private static int resetZanpakuto(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            ShikaiEvents.forceSeal(player, data, null);
            if (data.jinzen() != null) {
                player.stopUsingItem();
                JinzenLogic.end(player, data);
            }
            data.setZanpakuto(ZanpakutoBond.NONE);
            ZanpakutoEvents.reconcile(player, data);
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.zanpakuto.reset",
                players.size()), true);
        return players.size();
    }

    // ---- /bleach visored ----

    /**
     * Drives the road to a Visored one step at a time: {@code root} plants the Hollow now, with its signs;
     * {@code ready} also lets it be faced now, or a Visored call its mask again now; {@code duel} starts the fight
     * where the player stands, rooting and readying as needed, though never on Peaceful or with the path turned off;
     * {@code subdue} makes the soul a Visored, winning a fight under way; {@code mask} puts a Visored's mask on with
     * no cost and no wait, and {@code unmask} takes it off; {@code takeover} lets the Hollow seize the body now, with no
     * roll, health or spacing: the 5-second kind behind a mask that is on; never with the path off, unrooted, lent, in a
     * fight, in creative or spectator mode or held by Negación, nor on a beaten Hollow without its mask on; and
     * {@code reset} ends any takeover and fight, takes any mask off and takes the Hollow away.
     */
    private static int visored(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "action");
        VisoredAction action = byName(VisoredAction.values(), name);
        if (action == null) {
            context.getSource().sendFailure(Component.translatable("argument.enum.invalid", name));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            Component refusal = visoredFor(player, data, action, player.level().getGameTime());
            if (refusal != null) {
                refuseArts(context.getSource(), player, refusal);
                continue;
            }
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        int count = changed;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.visored.success",
                    action.getSerializedName(), count), true);
        }
        return count;
    }

    /** Does one {@code visored} action for one player, or says why it cannot. */
    @Nullable
    private static Component visoredFor(ServerPlayer player, SoulData data, VisoredAction action, long now) {
        if (action != VisoredAction.DUEL && action != VisoredAction.RESET && !ShinigamiLogic.hasShinigamiPower(data)) {
            return Component.translatable("message.bleach.visored.no_power");
        }
        switch (action) {
            case ROOT, READY -> {
                if (!VisoredLogic.isRooted(data)) {
                    VisoredEvents.takeRoot(player, data, now);
                }
                ShinigamiProgress.Visored record = VisoredLogic.record(data);
                if (action == VisoredAction.READY) {
                    // A beaten Hollow is never faced again, but its mask can be called now.
                    VisoredLogic.setRecord(data, record.subdued() ? record.withMaskReadyAt(now)
                            : record.withDuelReadyAt(now));
                }
            }
            case DUEL -> {
                // The line the player would have seen on its action bar; a forced start is never NOT_READY.
                VisoredEvents.DuelStart start = VisoredEvents.beginDuel(player, data, now, true);
                if (start != VisoredEvents.DuelStart.STARTED) {
                    String key = start.messageKey();
                    return key != null
                            ? Component.translatable(key)
                            : Component.literal(start.name().toLowerCase(Locale.ROOT));
                }
            }
            case SUBDUE -> {
                if (VisoredEvents.isDuelling(player)) {
                    VisoredEvents.endDuel(player, data, VisoredEvents.DuelEnd.WIN, now);
                } else if (!VisoredLogic.record(data).subdued()) {
                    // The record only: a Hollow beaten by command never spoke, so it takes root without its signs.
                    if (!VisoredLogic.isRooted(data)) {
                        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now));
                    }
                    VisoredEvents.subdue(player, data, now);
                }
            }
            case MASK -> {
                // The line the player would have seen; a forced mask is never HELD, WAITING or TOO_WEAK.
                VisoredEvents.MaskStart start = VisoredEvents.putOnMask(player, data, now, true);
                if (start != VisoredEvents.MaskStart.PUT_ON) {
                    String key = start.messageKey();
                    return key != null
                            ? Component.translatable(key)
                            : Component.literal(start.name().toLowerCase(Locale.ROOT));
                }
            }
            case UNMASK -> {
                if (!VisoredLogic.isMasked(data)) {
                    return Component.translatable("message.bleach.visored.mask.not_worn");
                }
                VisoredEvents.endMask(player, data, VisoredEvents.MaskEnd.TAKEN_OFF, now);
            }
            case TAKEOVER -> {
                if (!VisoredLogic.enabled()) {
                    return Component.translatable("message.bleach.visored.disabled");
                }
                // The kind that fits the soul: behind its mask if one is on, else the half mask, which a beaten Hollow
                // never forms.
                if (!VisoredEvents.startTakeover(player, data, VisoredLogic.isMasked(data), now)) {
                    return Component.literal("refused");
                }
            }
            case RESET -> {
                VisoredEvents.endAll(player, data, now);
                VisoredLogic.setRecord(data, ShinigamiProgress.Visored.NONE);
            }
        }
        return null;
    }

    /**
     * Sets how many seconds a soul's masks have been worn, which sets how long its next mask lasts; a mask on keeps
     * its length. Refused for a soul with no Shinigami power.
     */
    private static int visoredTrain(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long ticks = IntegerArgumentType.getInteger(context, "seconds") * 20L;
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            if (!ShinigamiLogic.hasShinigamiPower(data)) {
                refuseArts(context.getSource(), player, Component.translatable("message.bleach.visored.no_power"));
                continue;
            }
            VisoredLogic.setRecord(data, VisoredLogic.record(data).withMaskTrainedTicks(ticks));
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        int count = changed;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.visored.success",
                    "train", count), true);
        }
        return count;
    }

    // ---- /bleach quincy ----

    /**
     * Settles the bloodline as if it had been chosen, but quietly: no word to the player and no Cross.
     * Awakened power cannot be left without blood, so taking the blood from an awakened Quincy is
     * refused, pointing to the reset.
     */
    private static int setBloodline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "bloodline");
        Bloodline bloodline = byName(Bloodline.values(), name);
        if (bloodline == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_bloodline", name));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        int changed = 0;
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            if (bloodline == Bloodline.NONE && QuincyLogic.isAwakened(data)) {
                context.getSource().sendFailure(Component.translatable("commands.bleach.quincy.bloodline.awakened",
                        player.getDisplayName()));
                continue;
            }
            QuincyEvents.setBloodline(player, data, bloodline, false);
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        int count = changed;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.bloodline",
                    bloodline.displayName(), count), true);
        }
        return count;
    }

    /**
     * Sets Quincy training, then raises the tier to match as if it had been earned (with its title and
     * growth); a tier is never lowered. A soul without the blood, or one that may not awaken, only
     * keeps the points.
     */
    private static int setTraining(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int training = IntegerArgumentType.getInteger(context, "training");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            data.setQuincy(data.quincy().withTraining(training));
            QuincyEvents.train(player, data, 0);
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.training",
                training, players.size()), true);
        return players.size();
    }

    /** Sets reishi, up to each soul's maximum: none at all before the power awakens. */
    private static int setReishi(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float reishi = FloatArgumentType.getFloat(context, "reishi");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            QuincyLogic.setReishi(data, reishi);
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.reishi",
                format(reishi), players.size()), true);
        return players.size();
    }

    /** Sets Hollow poison straight. Its stages, and anything they bring, follow on the next tick. */
    private static int setPoison(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float poison = FloatArgumentType.getFloat(context, "poison");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            data.setQuincy(data.quincy().withPoison(poison));
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.poison",
                format(poison), players.size()), true);
        return players.size();
    }

    /**
     * Calls on Blut, or lets it go, without the unlock, settling, poison and start-cost checks. Its
     * upkeep still applies: the next tick ends it for a soul that cannot use Quincy powers, is poisoned
     * past the block or has no reishi left.
     */
    private static int setBlut(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "blut");
        BlutMode mode = byName(BlutMode.values(), name);
        if (mode == null) {
            context.getSource().sendFailure(Component.translatable("commands.bleach.unknown_blut", name));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            QuincyEvents.setBlut(player, data, mode, true);
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.blut",
                mode.displayName(), players.size()), true);
        return players.size();
    }

    /**
     * Sets the Sanrei Glove. {@code none} takes it off the hand with no glove given back and starts
     * its training over, though it still counts as handed down; {@code worn} puts it on, keeping its
     * progress; {@code mastered} puts it on with every day counted. Both need a third-tier soul
     * whose power is untouched, and skip whether it can use its power now.
     */
    private static int setGlove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "glove");
        GloveSetting setting = byName(GloveSetting.values(), name);
        if (setting == null) {
            context.getSource().sendFailure(Component.translatable("argument.enum.invalid", name));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            QuincyArts arts = QuincyArtsLogic.arts(data);
            SanreiProgress glove = arts.glove();
            if (setting == GloveSetting.NONE) {
                glove = glove.withWorn(false).withDays(0).withToday(0).withMastered(false);
            } else if (QuincyLogic.tier(data) < QuincyLogic.MAX_TIER) {
                refuseArts(context.getSource(), player, Component.translatable("message.bleach.sanrei_glove.tier"));
                continue;
            } else if (arts.stage() != QuincyStage.UNTOUCHED) {
                refuseArts(context.getSource(), player, Component.translatable("message.bleach.sanrei_glove.stage"));
                continue;
            } else {
                glove = glove.withGiven(true).withWorn(true);
                if (setting == GloveSetting.MASTERED) {
                    glove = glove.withDays(BleachConfig.get(BleachConfig.SANREI_DAYS_TO_MASTER)).withMastered(true);
                }
            }
            QuincyArtsLogic.setArts(data, arts.withGlove(glove));
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        int count = changed;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.glove",
                    setting.getSerializedName(), count), true);
        }
        return count;
    }

    /**
     * Moves the soul along the Letzt Stil path without the waits and rules: {@code start} tears the
     * glove (worn or not) for an untouched power that works now; {@code seal} burns it out now, from
     * Letzt Stil or straight from the untouched power; {@code ready} makes a sealed power able to
     * return now, by the rite or by an arrow; {@code restore} brings it back now with no conditions;
     * and {@code reset} ends every form and leaves the power untouched, with the glove still counted
     * as handed down.
     */
    private static int letzt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "action");
        LetztAction action = byName(LetztAction.values(), name);
        if (action == null) {
            context.getSource().sendFailure(Component.translatable("argument.enum.invalid", name));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            Component refusal = letztFor(player, data, action, player.level().getGameTime());
            if (refusal != null) {
                refuseArts(context.getSource(), player, refusal);
                continue;
            }
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        int count = changed;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.letzt",
                    action.getSerializedName(), count), true);
        }
        return count;
    }

    /** Does one {@code letzt} action for one player, or says why it cannot. */
    @Nullable
    private static Component letztFor(ServerPlayer player, SoulData data, LetztAction action, long now) {
        QuincyArts arts = QuincyArtsLogic.arts(data);
        QuincyStage stage = arts.stage();
        switch (action) {
            case START -> {
                if (stage != QuincyStage.UNTOUCHED) {
                    return Component.translatable("message.bleach.letzt_stil.once");
                }
                if (!QuincyLogic.canUsePowers(data) || !QuincyArtsEvents.startLetzt(player, data, now, true)) {
                    return Component.translatable("message.bleach.letzt_stil.powerless");
                }
            }
            case SEAL -> {
                if (!QuincyLogic.isAwakened(data)) {
                    return Component.translatable("message.bleach.letzt_stil.powerless");
                }
                if (stage == QuincyStage.SEALED) {
                    return Component.translatable("message.bleach.letzt_stil.sealed");
                }
                if (stage == QuincyStage.RESTORED) {
                    return Component.translatable("message.bleach.letzt_stil.once");
                }
                if (arts.glove().worn()) {
                    // Straight from the untouched power, as if Letzt Stil had come and gone: the glove was torn.
                    QuincyArtsLogic.setArts(data, arts.withGlove(arts.glove().withWorn(false)));
                }
                QuincyArtsEvents.seal(player, data, now);
            }
            case READY -> {
                if (stage != QuincyStage.SEALED) {
                    return Component.literal(stage.getSerializedName());
                }
                long wait = Math.max(QuincyArtsLogic.sealedTicksNeeded(false), QuincyArtsLogic.sealedTicksNeeded(true));
                QuincyArtsLogic.setArts(data, arts.withStage(QuincyStage.SEALED, now - wait));
            }
            case RESTORE -> {
                if (stage != QuincyStage.SEALED) {
                    return Component.literal(stage.getSerializedName());
                }
                QuincyArtsEvents.restore(player, data, now, null);
            }
            case RESET -> {
                QuincyArtsEvents.endAll(player, data);
                QuincyArts ended = QuincyArtsLogic.arts(data);
                // Only the glove's handing down stays, and a landing still to cushion, so a reset in the air does not hurt.
                QuincyArtsLogic.setArts(data, QuincyArts.NONE
                        .withGlove(SanreiProgress.NONE.withGiven(ended.glove().given()))
                        .withForms(QuincyForms.NONE.withLandingGrace(ended.forms().landingGrace())));
            }
        }
        return null;
    }

    /** Starts Vollständig for a restored power that works now, skipping the unlock, cost and cooldown, or ends it. */
    private static int setVollstandig(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "vollstandig");
        Toggle value = byName(Toggle.values(), name);
        if (value == null) {
            context.getSource().sendFailure(Component.translatable("argument.enum.invalid", name));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            long now = player.level().getGameTime();
            if (value == Toggle.ON) {
                if (!QuincyArtsLogic.isRestored(data)) {
                    refuseArts(context.getSource(), player, Component.literal(QuincyArtsLogic.stage(data).getSerializedName()));
                    continue;
                }
                if (!QuincyLogic.canUsePowers(data) || (!QuincyArtsLogic.inVollstandig(data)
                        && !QuincyArtsEvents.startVollstandig(player, data, now, true))) {
                    refuseArts(context.getSource(), player, Component.translatable("message.bleach.vollstandig.powerless"));
                    continue;
                }
            } else if (QuincyArtsLogic.inVollstandig(data)) {
                QuincyArtsEvents.endVollstandig(player, data, now, false);
            }
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        int count = changed;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.vollstandig",
                    value.getSerializedName(), count), true);
        }
        return count;
    }

    /** Chooses the spell Gintō cast, known or not: one not known yet casts the first known instead. */
    private static int setGinto(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "spell");
        GintoSpell spell = byName(GintoSpell.values(), name);
        if (spell == null) {
            context.getSource().sendFailure(Component.translatable("argument.enum.invalid", name));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withGinto(spell));
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.ginto",
                spell.displayName(), players.size()), true);
        return players.size();
    }

    /**
     * Ransōtengai by command: {@code learn} or {@code forget} the art (forgetting also closes an open
     * trial and lets a weave go); {@code start} weaves it, known or not, skipping the wait after a
     * collapse but no other refusal, which the player is told; {@code stop} lets it go. A weave let go
     * by command never collapses the body or starts the wait.
     */
    private static int ransotengai(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        RansotengaiAction action = byName(RansotengaiAction.values(), name);
        if (action == null) {
            context.getSource().sendFailure(Component.translatable("argument.enum.invalid", name));
            return 0;
        }
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            SoulData data = SoulLogic.get(player);
            long now = player.level().getGameTime();
            switch (action) {
                case LEARN -> {
                    // Learned outright: an open trial has nothing left to teach, so it closes and lets go.
                    QuincyRansotengai.closeTrial(player, data);
                    data.setQuincy(data.quincy().withTeaching(data.quincy().teaching().withRansotengaiKnown(true)));
                    Milestones.check(player, data);
                }
                case FORGET -> {
                    if (QuincyRansotengai.isWoven(data)) {
                        QuincyRansotengai.end(player, data, now, QuincyRansotengai.End.COMMAND);
                    }
                    QuincyRansotengai.closeTrial(player, data);
                    // Read again: letting the weave go and closing the trial changed the record.
                    data.setQuincy(data.quincy().withTeaching(data.quincy().teaching().withRansotengaiKnown(false)));
                }
                case START -> {
                    if (!QuincyRansotengai.start(player, data, now, true)) {
                        context.getSource().sendFailure(Component.translatable("commands.bleach.ransotengai.failed",
                                player.getDisplayName()));
                        continue;
                    }
                }
                case STOP -> {
                    if (QuincyRansotengai.isWoven(data)) {
                        QuincyRansotengai.end(player, data, now, QuincyRansotengai.End.COMMAND);
                    }
                }
            }
            BleachNetwork.syncSoul(player, data);
            changed++;
        }
        int count = changed;
        if (count > 0) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.bleach.ransotengai." + action.getSerializedName(), count), true);
        }
        return count;
    }

    /**
     * Starts the Elder's teaching over: no lessons given and none today, never visited and never met,
     * so his next visit can come as soon as the gates allow. Ransōtengai stays known.
     */
    private static int resetLessons(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            SoulData data = SoulLogic.get(player);
            QuincyTeaching teaching = data.quincy().teaching();
            data.setQuincy(data.quincy().withTeaching(teaching.withLessons(0).withLessonDay(-1L)
                    .withLastVisitAt(QuincyTeaching.NEVER).withElderMet(false)));
            BleachNetwork.syncSoul(player, data);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.lessons.reset",
                players.size()), true);
        return players.size();
    }

    /** Says a Quincy arts command was refused for this player, and why. */
    private static void refuseArts(CommandSourceStack source, ServerPlayer player, Component reason) {
        source.sendFailure(Component.translatable("commands.bleach.quincy.arts_refused", player.getDisplayName(), reason));
    }

    /** Takes the Quincy power away and leaves the bloodline open, so the soul is asked about it again. */
    private static int resetQuincy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            QuincyEvents.resetQuincy(player);
            BleachNetwork.syncSoul(player, SoulLogic.get(player));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.bleach.quincy.reset",
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

    /** What {@code /bleach quincy <players> glove} sets the Sanrei Glove to. */
    private enum GloveSetting implements StringRepresentable {
        NONE, WORN, MASTERED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What {@code /bleach quincy <players> letzt} does. */
    private enum LetztAction implements StringRepresentable {
        START, SEAL, READY, RESTORE, RESET;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What {@code /bleach visored <players>} does. */
    private enum VisoredAction implements StringRepresentable {
        ROOT, READY, DUEL, SUBDUE, MASK, UNMASK, TAKEOVER, RESET;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** On or off, for {@code /bleach quincy <players> vollstandig}. */
    private enum Toggle implements StringRepresentable {
        ON, OFF;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What {@code /bleach bait <players>} does to each player's site. */
    private enum BaitAction implements StringRepresentable {
        START, STOP, WAVE, FINALE, STATUS;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What {@code /bleach quincy <players> ransotengai} does. */
    private enum RansotengaiAction implements StringRepresentable {
        LEARN, FORGET, START, STOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
