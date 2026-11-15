package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.DankuWall;
import io.github.masteryoga1.bleach.entity.DutyShinigami;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.Gillian;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.Kototsu;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.QuincyElder;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.item.ArrancarUniformItem;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.quincy.QuincyKey;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.DangaiLogic;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.KidoHeal;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.DangaiPlan;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import io.github.masteryoga1.bleach.world.MoonPhases;
import io.github.masteryoga1.bleach.world.Rukongai;
import io.github.masteryoga1.bleach.world.SeireiteiPlan;
import io.github.masteryoga1.bleach.zanpakuto.JinzenScene;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStep;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStepKind;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A check CI runs on a real client (the {@code runClientSmokeTest} Gradle task, which sets
 * {@code -Dbleach.smokeTest=true}). It makes a flat world, answers the bloodline question as a Human,
 * turns the player into a Plus, beside whom a summoned Shinigami on duty must be seen and must leave when
 * its time is up, releases reiatsu, brings on Encroachment, turns them into a Hollow and opens the soul
 * screen. Then it makes them a Shinigami in the Shihakushō with a sheathed Asauchi,
 * opens the Tailoring Table and the instructor's dialogue, flash steps, stands on reishi footing by
 * jumping while falling, summons each Hollow and a Plus, cracks a mask, purifies a
 * Hollow, sends a Plus on, opens a Garganta and checks that a soul just below the threshold sees only
 * a shimmer. Next, as a Shinigami again whose Asauchi bond is full, it sits in Jinzen through the real
 * sneak and use keys, takes the red ribbon, closes the screen, learns Zangetsu's name, releases it,
 * looses a Getsuga Tenshō and opens the soul screen. Last, it makes them an Echt Quincy in the white
 * garb, draws the Heilig Bogen through the real use key, fires a Heilig Pfeil into the sky and another
 * at a Hollow, calls Blut Vene, uses Hirenkyaku and opens the soul screen once more.
 *
 * <p>Then come the arts past the third tier. In the mastered Sanrei Glove a full draw looses its two
 * flank arrows; tearing the glove with two taps of the Quincy Form key begins Letzt Stil, whose long
 * draw fires a Letzt Pfeil. The power is sealed and restored by command, and Ginrei Kojaku streams
 * arrows while drawn, then calls down Licht Regen. A Seele Schneider forms in the main hand and is
 * fired from the offhand; Gintō cast Wolke underfoot and Heizen on a Hollow. Last, Vollständig gives
 * flight, Sklaverei is switched on and off, and Vollständig is let go in the air, so the landing must
 * not hurt. The soul screen is opened again.
 *
 * <p>Last comes the Quincy in the world. A Quincy Elder and a Quincy Hunter are summoned and must be
 * seen; talking to the Elder opens his dialogue, and taking his trial binds the player, who weaves
 * Ransōtengai with the Quincy Form key's sneak press to break free and so learns it. The Hunter's
 * dialogue opens too. In the Wandenreich uniform the player opens the Tailoring Table on its
 * second page. A Hollow Bait site is opened by command, its wave comes through a Garganta, and its
 * finale cracks the sky, through which a Gillian comes down; the sky closes again when the site is
 * stopped. Ransōtengai is woven once more and let go with the key, so the collapse follows, and the
 * soul screen is opened again.
 *
 * <p>Last of all the player becomes a Hollow while still in the Wandenreich uniform, which the hide
 * sheds into the inventory; Hollow sight comes on and the food bar is held full. Holding the real
 * Devour key on a summoned Plus opens the mask's jaw and devours it, and Sneak with the key turns the
 * sight off. The soul screen is opened a last time, and a look in first person, bare-handed, shows the
 * claws and the edge of the mask. Then a Gillian and an Adjuchas are summoned to stand still, the
 * Gillian's hit boxes must sit on its body, and a summoned Cero is drawn.
 *
 * <p>At the very end the player rises through the Menos forms by command. In a space cleared for it
 * the body grows to a Gillian's height; holding the real Cero key gathers a Cero at its mouth, and
 * letting it go past half fires it. Then the body shrinks at once to an Adjuchas and to a Vasto Lorde,
 * each drawn as its form.
 *
 * <p>Then the bare-handed Vasto Lorde becomes an Arrancar by command, seen from in front: a person's
 * size again, with the fragment of its mask, its hole and its own blade in hand. Seated as the Cuatro
 * Espada by command, its rank and its tattooed number must reach the client. The soul screen is
 * opened on it, and the real Release key calls out its Resurrección, which must draw the released
 * Vasto Lorde and raise its health; the Seal key seals it again, and the wait before the next release
 * must reach the client. Then, seen from behind, where its tattoo may be, the sealed Arrancar's Sonído,
 * through the real flash step request, must carry it further than a first Shunpo, and holding the real
 * Devour key still sends out Pesquisa, which must outline a Plus standing behind a stone wall. Seated
 * among the Espada and made strong enough to pay by command, the Arrancar sneaks and holds the real Cero
 * key, which gathers a Gran Rey Cero in its blade's colour that fires by itself and is paid for. Then it
 * is mounted by command on a summoned Negación light, which must lift it, and sneaking must leave the
 * light, falling slowly. Then it puts on the white Arrancar uniform by command, seen from in front.
 * Then, seen from behind, a Privaron Espada and a Número are summoned to stand still before it, and both
 * must reach the client dressed and armed, the bare-chested Privaron Espada with the 3 of its old seat
 * tattooed on it. A Caja Negación is summoned and the Número mounted in it by command: the cage must
 * close on it and then hide it from the player, and must let it out when the cage is killed. Then, in
 * first person, the player is mounted in a Caja of its own, which must hold it; sneaking must not get it
 * out, and when that cage is killed too it must let the player go, falling slowly. Then, a Shinigami
 * again by command, it faces its inner Hollow by command, which must come out beside it, and beats it by
 * command, after which the Hollow must be gone and the win must reach the client. Seen from in front, the
 * Visored puts its Hollow mask on by command, which must reach the client with its look, and in first
 * person, framed by the mask's edge, it must still be on; then the real Hollow Mask key takes it off, and
 * the client must see it gone and its wait begun. Last, seen from behind and looking up, the Visored learns
 * Zangetsu's name again and releases it, is given ten minutes of masks worn by command and puts the mask back
 * on by command, which must reach the client with the training. Holding the real Cero key must gather a Cero
 * at its face in the masked Visored's crimson, and letting it go past half must fire it four blocks wide; then
 * the Getsuga Tenshō it looses must reach the client as the black one. The soul screen is opened on the masked
 * Visored. Last, seen from in front, the Hollow is taken away and rooted again by command, and seizes the body by
 * command, which must reach the client as half a mask; in first person, framed by the left half of the mask's edge,
 * it must still hold the body, and then the real Hollow Mask key tears the half mask off, and the client must see it
 * gone and the weariness after it.
 *
 * <p>Finally, seen from behind, the Shinigami is given Kidō 10 by command, and the real Kidō key casts Hadō #1 Shō, the
 * spell a soul that never chose falls back on, at a summoned villager, which must be hurt, and the cast must train
 * Kidō. Sneaking with the key must choose #4 Byakurai, which must reach the client and which the action bar must name;
 * the key alone then casts it, whose white trace must reach the client in the colour of the client's own synced copy
 * of the spell, and it must train Kidō again. Sneaking with the key once more must choose #31 Shakkahō and print its
 * incantation in the chat. Its romaji, sent through the real chat as a player types it, must be spoken back to the
 * player, and the spell must not go off before the chant is said; then it must, its crimson trace must reach the
 * client, and the chanted cast must train Kidō twice over. Then #33 Sōkatsui's incantation in the mod's own English,
 * sent the same way, must cast it, whose blue trace must reach the client. Given Kidō 18 by command, sneaking with the
 * key twice must choose #58 Tenran, and the key alone must blow it at a fresh villager, which must be hurt, and its pale
 * trace must reach the client. Last, given Kidō 42, sneaking with the key must choose #63 Raikōhō, and holding the key
 * must gather its yellow orb at the palm, which must reach the client with no bolt before it; then the bolt must go off
 * by itself, the key still held, and its yellow trace reach the client. Then sneaking with the key must choose Bakudō #1
 * Sai, which comes after every Hadō, and the key alone must cast it at a fresh villager, whose pale gold trace must
 * reach the client and stop at the villager; and the same for #4 Hainawa and its yellow trace. Last, with a fresh
 * villager, sneaking with the key must choose #9 Geki and print its incantation in the chat; its romaji, sent through the
 * real chat, must be spoken back and must not cast it before the chant is said; then it must go off at the villager,
 * binding its arms and holding its body at once, and its red trace must reach the client and stop at it. The spell
 * chosen after Geki is #21 Sekienton, which the action bar must name and which has no incantation, and #61 Rikujōkōrō is
 * chanted all the same: its romaji must be spoken back and must not cast it before the chant is said, and its pale
 * yellow trace must reach the client and stop at the villager. The key alone raises Sekienton's red smoke, which must
 * quicken the player and not blind it; sneaking with the key chooses #39 Enkōsen, which the key raises before the
 * player: a blow of 4 from two blocks ahead must not land, and one from two blocks behind must. Sneaking with the key
 * twice more must choose #81 Dankū, which the action bar must name, and the key alone must raise its wall of light,
 * which must reach the client two blocks south of the player, facing it, 5 wide and 4 tall, and be gone 6 seconds
 * later. Sneaking with the key once more must choose Kaidō, which the action bar must name alone, and the key held must
 * heal a hurt villager two blocks south of the player, which must reach the client whole within three seconds, and
 * then, with no one in reach and natural regeneration off, the player's own wounds, by a heart a second; then the soul
 * screen's Kidō button must open the Kidō page with all 14 spells and Kaidō chosen, a click on Sōkatsui's row must
 * choose it on the server, which the action bar and chat must show, and the page's Back button must go back to the
 * soul screen. Last, handed a Jigokuchō, holding the Gate key must turn the blade, which the action bar must say, and
 * open a Senkaimon that reaches the client two blocks south of the player, facing it, 3 wide and 4 tall. Standing in it
 * must bring the player to the Senkaimon court on Seireitei's lawn at exactly y 72. A tp across the lawn must stand it
 * where the south wall reaches the client in Sekkiseki to its top beside the gate's 16-block gap. A tp must then stand
 * it two blocks inside the Academy's gate in Seireitei, where the Academy and its instructor must reach the client,
 * and another must stand it on the lawn again. Once the first gate's wait has run, a gate opened in Soul Society must
 * bring the player home. Then, its Jigokuchō taken, a third gate must take it into the Dangai, set on a lane's floor
 * between its walls of Kōryū, which the action bar must say, with the lane's Kōtotsu waiting at its start; it looks
 * back at the Kōtotsu, then, moved near the lane's end, it walks out and must come out over the Rukongai, falling
 * slowly. Last, as a soul that has passed on, it is killed, and a new player must wake at a Rukongai home drawn for it,
 * at least 32 blocks from where it died, with the district named on the action bar, and Hueco Mundo's five blocks, set
 * down before it, must reach the client. Made an Arrancar by command on Seireitei's plateau, it holds the Gate key: the
 * action bar must say the air is torn at, and a Garganta of a person's size must open 1.8 blocks ahead, level with its
 * feet. Moved into it, it must come out on white sand on Las Noches' level ground in Hueco Mundo at exactly y 96, under
 * a black sky with the moon full overhead where the Overworld's is new. There, in creative, the key must tear a second
 * Garganta open the same way, and moved into it in survival, the soul must come out in the Overworld on the block it
 * last left the Overworld from by a gate, the third, before the run quits.
 *
 * <p>Nearly all of it is seen with the camera behind the player, so the chain, mask, garb, saya,
 * NPCs, Hollows, zanpakutō, bow, arrows, veins, glove, wings, blades, casts, uniform, threads, rifts,
 * Hollow body, Menos, Cero, Arrancar, Arrancar NPCs, Negación light, Caja Negación, inner Hollow, Visored
 * mask, masked Cero, black Getsuga Tenshō, half mask, Kidō traces, orb, smoke, shield and wall, the Senkaimon, the
 * Seireiheki, the Dangai's lane, Kōryū and Kōtotsu, Hueco Mundo's blocks, dunes and moon, and HUD are drawn, and
 * screenshots of the main moments are saved for review. Any crash in the mod's client code fails the run, and so does
 * anything that did not reach the client as expected.
 */
public final class ClientSmokeTest {
    public static final String PASSED = "Bleach client smoke test passed";
    private static final String FAILED = "Bleach client smoke test FAILED: ";
    /** Give up if the world has not loaded after this many client ticks (five minutes). */
    private static final int WORLD_TIMEOUT_TICKS = 6000;

    /** How far the flash step must carry the player, and how high above the ground footing must hold them. */
    private static final double DASH_MIN_DISTANCE = 4.0;
    private static final double FOOTING_MIN_HEIGHT = 8.0;
    /** How far the Arrancar's Sonído must carry the player: further than a first Shunpo goes. */
    private static final double SONIDO_MIN_DISTANCE = 8.0;
    /** The masks' time worn that {@code bleach visored @s train 600} gives: ten minutes, the masked Cero's gate. */
    private static final long VISORED_TRAINED_TICKS = 12_000L;
    /** How high the Negación light must lift the player before it is left. */
    private static final double NEGACION_MIN_RISE = 2.0;
    /** How far around the player the summoned Hollows and Plus, and the Garganta, are looked for. */
    private static final double SUMMONED_RANGE = 24.0;
    private static final double RIFT_RANGE = 40.0;
    /** Where the player stands in Soul Society: Seireitei's lawn, 40 blocks inside the south wall, east of its gate. */
    private static final double SOUL_SOCIETY_X = 16.5, SOUL_SOCIETY_Z = SeireiteiPlan.SEIREITEI_RADIUS - 39.5;
    /** The tp that stands the player there, at y 72 for any seed, facing the south wall 40 blocks off. */
    private static final String LAWN_TP = String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 -20", SOUL_SOCIETY_X,
            SeireiteiPlan.PLATEAU_Y, SOUL_SOCIETY_Z);
    /** Where the tp sets the player in its Dangai lane: 1.5 blocks short of the lane's end. */
    private static final double LANE_END_Z = DangaiPlan.EXIT_Z - 1.5;
    /** Where the player stands in Hueco Mundo: on Las Noches' level ground, 4.5 blocks inside its edge, due south. */
    private static final double HUECO_MUNDO_X = 0.5, HUECO_MUNDO_Z = HuecoMundoPlan.LEVEL_RADIUS - 4.5;
    /** How far south of an Arrancar its Garganta stands, and how far back it steps to see the whole tear. */
    private static final double RIFT_AHEAD = 0.3 + 1.0 + GargantaRift.PASSAGE_GAP, RIFT_STEP_BACK = 6.0;
    /** Where on Seireitei's plateau the Arrancar tears its Garganta: its tear stands over Hueco Mundo's arrival spot. */
    private static final double TEAR_FROM_Z = HUECO_MUNDO_Z - RIFT_AHEAD;
    /** Summoned creatures stand still, facing south, towards a player who faces north. */
    private static final String STILL = "{NoAI:1b,Rotation:[0f,0f]}";
    /**
     * A still, bare-chested Privaron Espada facing the player: seat 103, so the 3 of its old seat on its chest (this
     * UUID's spot is CHEST_LEFT), its hole at the sternum, a Halcón blade and the second mask remnant.
     */
    private static final String PRIVARON_NPC = "{NoAI:1b,Rotation:[180f,0f],UUID:[I;0,1,0,0],Rank:{seat:103},"
            + "ArmorItems:[{id:\"bleach:arrancar_zori\",count:1},{id:\"bleach:arrancar_hakama\",count:1},{},{}]}";
    /** A still Número 34 facing the player, in the whole uniform. */
    private static final String NUMERO_NPC = "{NoAI:1b,Rotation:[180f,0f],Rank:{numero:34},"
            + "ArmorItems:[{id:\"bleach:arrancar_zori\",count:1},{id:\"bleach:arrancar_hakama\",count:1},"
            + "{id:\"bleach:arrancar_jacket\",count:1},{}]}";
    /** How many ticks the bloodline question stays open before it is answered, so a screenshot shows it. */
    private static final int BLOODLINE_ANSWER_TICKS = 5;
    /** The first step, which turns the player into a Plus and so takes the bloodline question away. */
    private static final int FIRST_STEP_TICK = 40;
    /** How long the steps wait on the bloodline answer before the run fails. */
    private static final int BLOODLINE_WAIT_TICKS = 400;
    /** The last ticks of the Senkaimon's arrival check and of the lawn's wall check, which a slow server can make late. */
    private static final int COURT_BY_TICK = 6800, WALL_BY_TICK = 6985;
    /** How long the clock may wait in all before those ticks for the server to catch up, before the checks fail. */
    private static final int SERVER_WAIT_TICKS = 400;
    /** How many of the server's last chat lines are kept for the checks that read them. */
    private static final int CHAT_LINES_KEPT = 16;
    /** How many of the server's last action bar lines are kept for a failure to name. */
    private static final int ACTION_BAR_LINES_KEPT = 6;

    private static boolean worldRequested;
    private static int ticksWaiting;
    private static int ticksInWorld;
    private static boolean finished;
    /** How many ticks the bloodline question has been open. */
    private static int bloodlineTicks;
    /** How many ticks the steps have waited on the bloodline answer. */
    private static int bloodlineWaitTicks;
    /** How many ticks the clock has waited for the server before the Soul Society checks' last ticks. */
    private static int serverWaitTicks;
    /** The first tick of each {@link #expectWithin} window that has already passed. */
    private static final Set<Integer> windowsPassed = new HashSet<>();
    /** When the player was sent into the third gate, by the clock and by the server's own tick count. */
    private static long thirdGateTpAt;
    private static int thirdGateTpTick;
    /** Where the player stood before the flash step. */
    private static Vec3 dashFrom;
    /** Where the player stood as it was mounted on the Negación light, or null before then. */
    @Nullable
    private static Vec3 negacionFrom;
    /** Where the player stood before going to Soul Society, to come back to. */
    private static Vec3 returnTo = Vec3.ZERO;
    /** The Dangai lane whose end is held for the player, or -1 before then. */
    private static int heldLane = -1;
    /** The tick the player was first seen over the Rukongai out of the Dangai, or -1 before then. */
    private static int skyFrom = -1;
    /** The player as the death beat killed it, to tell it from the one that wakes in its place, or null before then. */
    @Nullable
    private static LocalPlayer killed;
    /** Where the player stood as the death beat killed it. */
    private static Vec3 diedAt = Vec3.ZERO;
    /** Where the player stood as Hueco Mundo's blocks were set down before it, or null before then. */
    @Nullable
    private static BlockPos blocksAt;
    /** The tick the player that woke in the killed one's place was first seen, or -1 before then. */
    private static int wokeAt = -1;
    /** What the current step acts on: the Hollow to strike, the Plus to send on, or the Quincy to talk to. */
    @Nullable
    private static Entity subject;
    /** How high the player stood when Vollständig's flight began. */
    private static double flightFrom;
    /**
     * The player's health just before Vollständig was let go in the air, before the blows on Enkōsen, and after the
     * blow that Kaidō then heals.
     */
    private static float healthBefore;
    /** The tick the player landed after Vollständig, or -1 before then. */
    private static int landedAt = -1;
    /** The last line the server put on the action bar, and the tick it came, for the checks that read it. */
    @Nullable
    private static Component actionBar;
    private static int actionBarAt;
    /** The last lines the server put on the action bar, oldest first, each with its tick, for a failure to name. */
    private static final Deque<String> actionBarLines = new ArrayDeque<>();
    /** The last lines the server put in the chat, oldest first, as plain text, for the checks that read them. */
    private static final Deque<String> chatLines = new ArrayDeque<>();

    private ClientSmokeTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("bleach.smokeTest");
    }

    /**
     * Keeps each line the server puts on the action bar, which the game itself keeps out of reach, and the last few it
     * puts in the chat: a spoken line and a printed incantation come as such system lines, not as player chat.
     */
    static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (event.isOverlay()) {
            actionBar = event.getMessage();
            actionBarAt = ticksInWorld;
            actionBarLines.addLast(ticksInWorld + ": \"" + actionBar.getString() + "\"");
            while (actionBarLines.size() > ACTION_BAR_LINES_KEPT) {
                actionBarLines.removeFirst();
            }
        } else {
            chatLines.addLast(event.getMessage().getString());
            while (chatLines.size() > CHAT_LINES_KEPT) {
                chatLines.removeFirst();
            }
        }
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (finished || minecraft.getOverlay() != null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            if (!worldRequested && minecraft.screen != null) {
                worldRequested = true;
                createWorld(minecraft);
            } else if (++ticksWaiting > WORLD_TIMEOUT_TICKS) {
                fail(minecraft, "the world did not load");
            }
            return;
        }

        // The death beat's respawn makes a new player under its loading screen, during which the place line can come.
        if (killed != null && wokeAt < 0 && minecraft.player != killed) {
            wokeAt = ticksInWorld;
        }
        // Nothing counts while the world is still arriving, which no other screen shows over.
        if (minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }
        LocalPlayer player = minecraft.player;
        SoulData soul = player.getData(SoulAttachments.SOUL);
        if (waitsForServer(minecraft, player)) {
            return;
        }
        // The steps wait for the bloodline answer: the first one makes a Plus, who is never asked.
        if (ticksInWorld < FIRST_STEP_TICK - 1 || soul.quincy().decided()) {
            ++ticksInWorld;
        } else if (++bloodlineWaitTicks > BLOODLINE_WAIT_TICKS) {
            fail(minecraft, "the bloodline answer never reached the server");
            return;
        }
        // The question comes as the world loads. A screenshot shows the last frame drawn, so the screen
        // gets a few ticks to be drawn before it is answered.
        if (minecraft.screen instanceof BloodlineScreen bloodline && ++bloodlineTicks >= BLOODLINE_ANSWER_TICKS) {
            shot(minecraft, "bloodline");
            bloodline.choose(BloodlineChoice.HUMAN);
        }
        expectWithin(minecraft, 2, 230, () -> soul.quincy().decided(), "the bloodline answer never reached the server");
        if (expectWithin(minecraft, 104, 135, () -> count(minecraft, DutyShinigami.class, 16.0) >= 1,
                "the summoned Shinigami on duty never reached the client")) {
            shot(minecraft, "duty");
        }
        expectWithin(minecraft, 200, 218, () -> count(minecraft, DutyShinigami.class, 16.0) == 0,
                "the Shinigami on duty never left when its time was up");
        expectWithin(minecraft, 345, 380,
                () -> horizontalDistance(player.position(), dashFrom) >= DASH_MIN_DISTANCE,
                "the flash step did not carry the player " + DASH_MIN_DISTANCE + " blocks");
        if (expectWithin(minecraft, 390, 430,
                () -> player.onGround() && player.getY() > dashFrom.y + FOOTING_MIN_HEIGHT
                        && player.level().getBlockState(player.blockPosition().below()).is(ModBlocks.REISHI_FOOTING.get()),
                "jumping while falling did not stand the player on reishi footing")) {
            shot(minecraft, "footing");
        }
        expectWithin(minecraft, 565, 620, () -> summonedSeen(minecraft),
                "the summoned Hollows and Plus never reached the client");
        if (expectWithin(minecraft, 660, 700, () -> subject instanceof Hollow hollow && hollow.isMaskCracked(),
                "a blow to the mask did not crack it")) {
            shot(minecraft, "mask");
        }
        if (expectWithin(minecraft, 740, 780, () -> soul.hunt().purified() >= 1,
                "the Asauchi kill did not purify the Hollow")) {
            shot(minecraft, "purify");
        }
        // The Plus summoned at tick 561 stands within 4 blocks too, so this follows the Plus that was sent on.
        expectWithin(minecraft, 815, 860,
                () -> subject instanceof PlusSpirit sent && sent.isRemoved() && soul.hunt().sentOn() >= 1,
                "Konsō did not send the Plus on");
        if (expectWithin(minecraft, 872, 930, () -> openRift(minecraft) != null,
                "the Garganta never opened on the client")) {
            GargantaRift rift = openRift(minecraft);
            if (rift != null) {
                look(minecraft, rift.position().add(0.0, 2.0, 0.0));
            }
        }
        if (expectWithin(minecraft, 940, 980,
                () -> nearest(minecraft, SpiderHollow.class, 6.0,
                        spider -> spider.isAlive() && isSpiderHollow(spider) && ClientSouls.isHiddenFromViewer(spider)) != null
                        && SpiritShimmer.emitted() > 0,
                "an unaware soul just below the threshold saw the Hollow or no shimmer")) {
            shot(minecraft, "unaware");
        }
        expectWithin(minecraft, 1025, 1050, player::isUsingItem, "sneak and use with the Asauchi did not start Jinzen");
        if (expectWithin(minecraft, 1080, 1140, () -> jinzenScene(minecraft) == JinzenScene.MEETING,
                "Jinzen never opened on the meeting")) {
            shot(minecraft, "jinzen");
            // Opening the screen let go of every key already; the blade stays in use while it is open.
            minecraft.options.keyUse.setDown(false);
            minecraft.options.keyShift.setDown(false);
        }
        if (expectWithin(minecraft, 1155, 1200,
                () -> jinzenStep(minecraft).map(JinzenStep::kind).orElse(null) == JinzenStepKind.QUESTION,
                "the red ribbon did not lead to the question")) {
            shot(minecraft, "jinzen_question");
        }
        expectWithin(minecraft, 1206, 1240, () -> !player.isUsingItem() && minecraft.screen == null,
                "closing the Jinzen screen did not end Jinzen");
        expectWithin(minecraft, 1250, 1290,
                () -> soul.zanpakuto().named() && ZanpakutoItem.isOwnedBy(player.getMainHandItem(), player.getUUID()),
                "learning the name did not bind the Asauchi");
        if (expectWithin(minecraft, 1300, 1340,
                () -> ZanpakutoItem.blade(player.getMainHandItem()).released() && modelOf(player.getMainHandItem()) == 1,
                "Zangetsu was not released")) {
            shot(minecraft, "shikai");
        }
        if (expectWithin(minecraft, 1366, 1400, () -> nearest(minecraft, GetsugaTensho.class, 48.0, crescent -> true) != null,
                "the Getsuga Tenshō never reached the client")) {
            shot(minecraft, "getsuga");
        }
        expectWithin(minecraft, 1452, 1475,
                () -> QuincyLogic.tier(soul) == 3 && soul.quincy().bloodline() == Bloodline.ECHT
                        && player.getMainHandItem().is(ModItems.QUINCY_CROSS.get()),
                "the Quincy setup never reached the client");
        if (expectWithin(minecraft, 1503, 1525, () -> nearest(minecraft, HeiligPfeil.class, 48.0, arrow -> true) != null,
                "no Heilig Pfeil reached the client")) {
            shot(minecraft, "heilig_pfeil");
        }
        expectWithin(minecraft, 1563, 1600,
                () -> subject instanceof LivingEntity struck && (!struck.isAlive() || struck.getHealth() < struck.getMaxHealth()),
                "the Heilig Pfeil did not strike the Hollow");
        if (expectWithin(minecraft, 1606, 1640,
                () -> soul.quincy().blut() == BlutMode.VENE && ClientSouls.appearance(player).blut() == BlutMode.VENE,
                "Blut Vene never came on")) {
            shot(minecraft, "blut_vene");
        }
        if (expectWithin(minecraft, 1651, 1680,
                () -> horizontalDistance(player.position(), dashFrom) >= DASH_MIN_DISTANCE,
                "Hirenkyaku did not carry the player " + DASH_MIN_DISTANCE + " blocks")) {
            shot(minecraft, "hirenkyaku");
        }
        expectWithin(minecraft, 1686, 1720, () -> soul.quincy().blut() == BlutMode.OFF, "Blut never ended");
        checkArts(minecraft, player, soul);
        checkQuincyWorld(minecraft, player, soul);
        checkHollowPlayer(minecraft, player, soul);
        checkMenos(minecraft);
        checkMenosPlayer(minecraft, player, soul);
        checkArrancar(minecraft, player, soul);
        checkVisored(minecraft, player, soul);
        checkVisoredMask(minecraft, player, soul);
        checkVisoredArts(minecraft, player, soul);
        checkVisoredTakeover(minecraft, player, soul);
        checkKido(minecraft, player, soul);
        checkSoulSociety(minecraft, player);
        checkDangai(minecraft, player);
        checkWaking(minecraft, player);
        checkHuecoMundoBlocks(minecraft);
        checkHuecoMundo(minecraft, player);
        if (finished) {
            return;
        }

        switch (ticksInWorld) {
            case FIRST_STEP_TICK -> {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                // No Garganta, wandering Plus or first Hollow comes on its own: every Hollow here is summoned.
                command(minecraft, "gamerule doMobSpawning false");
                command(minecraft, "bleach soul @s plus");
            }
            case 80 -> {
                expect(minecraft, soul.state() == SoulState.PLUS && soul.chainMaxTicks() > 0,
                        "the client never saw the Plus and its chain");
                command(minecraft, "bleach reiryoku @s 500");
            }
            // A Shinigami on duty, 4 blocks ahead and facing the Plus. It stays past 180, when the player stops being a
            // Plus: cutting the hour's chain to 30 seconds at 140 makes the Plus due a visit, and none comes down on its
            // own while this one stands within 128 blocks.
            case 82 -> {
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                command(minecraft, "summon bleach:duty_shinigami ~ ~ ~4 {Lifetime:110,Rotation:[180f,0f]}");
            }
            case 100 -> BleachNetwork.requestReiatsuToggle();
            case 140 -> {
                expect(minecraft, soul.isReiatsuReleased(), "the release key did not release reiatsu");
                command(minecraft, "bleach chain @s 30");
            }
            case 180 -> {
                expect(minecraft, SoulLogic.isEncroaching(soul), "a 30-second chain is not encroaching");
                command(minecraft, "bleach soul @s hollow");
            }
            case 220 -> {
                expect(minecraft, soul.state() == SoulState.HOLLOW, "the client never saw the Hollow");
                minecraft.setScreen(new SoulStatsScreen());
            }
            case 240 -> expect(minecraft, minecraft.screen instanceof SoulStatsScreen stats && stats.kidoButton() == null,
                    "the soul screen offered a Hollow the Kidō page");
            case 260 -> {
                // Now a Shinigami, with a full reserve and nothing slowing them down.
                minecraft.setScreen(null);
                if (soul.isReiatsuReleased()) {
                    BleachNetwork.requestReiatsuToggle();
                }
                command(minecraft, "bleach reiryoku @s 500 500");
                command(minecraft, "effect clear @s");
                command(minecraft, "bleach soul @s shinigami");
                command(minecraft, "bleach stat @s hoho 10");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
            }
            case 262 -> {
                // Split over two ticks so the burst of commands reads as two.
                command(minecraft, "item replace entity @s hotbar.8 with bleach:asauchi");
                command(minecraft, "item replace entity @s armor.chest with bleach:shihakusho_kosode");
                command(minecraft, "item replace entity @s armor.legs with bleach:shihakusho_hakama");
                command(minecraft, "item replace entity @s armor.feet with bleach:waraji");
                command(minecraft, "setblock ~2 ~ ~ bleach:tailoring_table");
                command(minecraft, "summon bleach:academy_instructor ~-2 ~ ~");
                command(minecraft, "summon bleach:wounded_shinigami ~-2 ~ ~3");
            }
            case 300 -> {
                expect(minecraft, soul.state() == SoulState.SHINIGAMI && soul.tier(PowerPath.SHINIGAMI) >= 1,
                        "the client never saw the Shinigami");
                expect(minecraft, player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.SHIHAKUSHO_KOSODE.get())
                                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.SHIHAKUSHO_HAKAMA.get())
                                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.WARAJI.get()),
                        "the player is not wearing the Shihakushō");
                expect(minecraft, ClientSouls.appearance(player).bladeSheathed(),
                        "the Asauchi in the hotbar is not sheathed at the hip");
                shot(minecraft, "gear");
                BlockPos table = BlockPos.containing(player.getX() + 2.0, player.getY(), player.getZ());
                minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
            }
            case 320 -> {
                expect(minecraft, minecraft.screen instanceof TailoringScreen, "the Tailoring Table did not open");
                player.closeContainer();
                List<AcademyInstructor> instructors = instructorsNear(minecraft, 6.0);
                expect(minecraft, !instructors.isEmpty(), "the summoned instructor never reached the client");
                if (!instructors.isEmpty()) {
                    minecraft.gameMode.interact(player, instructors.get(0), InteractionHand.MAIN_HAND);
                }
            }
            case 340 -> {
                expect(minecraft, minecraft.screen instanceof NpcDialogueScreen,
                        "talking to the instructor did not open the dialogue");
                minecraft.setScreen(null);
                dashFrom = player.position();
                BleachNetwork.requestFlashStep(1.0F, 0.0F);
            }
            case 380 -> command(minecraft, "tp @s ~ ~12 ~");
            // Pressing jump again while falling, through the real key, asks for footing.
            case 386 -> minecraft.options.keyJump.setDown(true);
            case 388 -> minecraft.options.keyJump.setDown(false);
            case 510 -> minecraft.setScreen(new SoulStatsScreen());
            // A screenshot shows the last frame drawn, so it waits a moment for the screen to be drawn.
            case 515 -> shot(minecraft, "stats");
            case 520 -> expect(minecraft, minecraft.screen instanceof SoulStatsScreen stats && stats.kidoButton() != null,
                    "the soul screen offered a new Shinigami no Kidō page");
            case 550 -> minecraft.setScreen(null);
            // Hollows vanish on Peaceful. Back on the ground, off the footing and facing north, 24 blocks north of
            // here, where every later spot is laid out.
            case 560 -> {
                command(minecraft, "difficulty easy");
                command(minecraft, "tp @s ~ -60 ~-24 180 0");
            }
            case 561 -> {
                command(minecraft, "summon bleach:spider_hollow ~-4 ~ ~-6 " + STILL);
                command(minecraft, "summon bleach:hexapodus ~-1.5 ~ ~-6 " + STILL);
                command(minecraft, "summon bleach:hound_hollow ~1.5 ~ ~-6 " + STILL);
                command(minecraft, "summon bleach:serpent_hollow ~4 ~ ~-7 " + STILL);
                command(minecraft, "summon bleach:fishbone_d ~ ~ ~-11 " + STILL);
                command(minecraft, "summon bleach:plus ~-2 ~ ~-3 " + STILL);
            }
            case 600 -> shot(minecraft, "hollows");
            // Each thing summoned gets twenty ticks or more to reach the client before it is looked for.
            case 625 -> command(minecraft, "summon bleach:spider_hollow ~ ~ ~-2.5 " + STILL);
            case 650 -> aimAtMask(minecraft, "the Spider Hollow in front of the player never reached the client");
            // Slot 0 is empty, so this is a bare hand.
            case 654 -> {
                if (subject != null) {
                    minecraft.gameMode.attack(player, subject);
                }
            }
            case 705 -> command(minecraft, "kill @e[type=bleach:spider_hollow,distance=..4]");
            case 706 -> command(minecraft, "summon bleach:spider_hollow ~ ~ ~-2.5 {NoAI:1b,Rotation:[0f,0f],Health:1f}");
            // The Asauchi, put in the last hotbar slot at tick 262.
            case 708 -> player.getInventory().selected = 8;
            case 730 -> aimAtMask(minecraft, "the weakened Spider Hollow never reached the client");
            case 732 -> {
                if (subject != null) {
                    minecraft.gameMode.attack(player, subject);
                }
            }
            case 785 -> command(minecraft, "summon bleach:plus ~ ~ ~-2 " + STILL);
            case 805 -> minecraft.options.keyShift.setDown(true);
            case 810 -> {
                subject = nearest(minecraft, PlusSpirit.class, 3.0, plus -> plus.isAlive() && !plus.isDeparting());
                expect(minecraft, subject != null, "the Plus in front of the player never reached the client");
                if (subject != null) {
                    look(minecraft, subject.getEyePosition());
                }
            }
            // The interaction carries the sneak with it, so the server sees Konsō.
            case 812 -> {
                if (subject != null) {
                    minecraft.gameMode.interact(player, subject, InteractionHand.MAIN_HAND);
                }
            }
            case 818 -> minecraft.options.keyShift.setDown(false);
            case 870 -> command(minecraft, "bleach garganta @s spider");
            case 915 -> shot(minecraft, "garganta");
            case 932 -> command(minecraft, "kill @e[type=#bleach:hollows]");
            case 935 -> {
                // Just below the threshold with no power awakened. The Hollow state at tick 180 left a Hollow tier.
                command(minecraft, "bleach soul @s human");
                command(minecraft, "bleach tier @s shinigami 0");
                command(minecraft, "bleach tier @s hollow 0");
                command(minecraft, "bleach reiryoku @s 22 22");
                command(minecraft, "summon bleach:spider_hollow ~ ~ ~-3 {NoAI:1b}");
            }
            case 985 -> {
                command(minecraft, "kill @e[type=#bleach:hollows]");
                command(minecraft, "kill @e[type=bleach:plus]");
                command(minecraft, "difficulty peaceful");
            }
            // A Shinigami again, a fresh Asauchi in hand whose bond is full and has settled on Zangetsu,
            // looking up and away so the Getsuga Tenshō carves nothing.
            case 1000 -> {
                command(minecraft, "bleach soul @s shinigami");
                command(minecraft, "bleach reiryoku @s 500 500");
                command(minecraft, "effect clear @s");
                command(minecraft, "clear @s bleach:asauchi");
                command(minecraft, "item replace entity @s weapon.mainhand with bleach:asauchi");
                command(minecraft, "item replace entity @s weapon.offhand with air");
                command(minecraft, "tp @s ~ ~ ~ 180 -30");
                command(minecraft, "bleach zanpakuto @s spirit bleach:zangetsu");
                command(minecraft, "bleach zanpakuto @s bond 1000");
            }
            // Jinzen through the real keys: sneak, then hold use with the Asauchi in hand.
            case 1015 -> minecraft.options.keyShift.setDown(true);
            case 1020 -> {
                expect(minecraft, soul.zanpakuto().bond() == 1000 && soul.zanpakuto().spirit().isPresent(),
                        "the bond never reached the client");
                minecraft.options.keyUse.setDown(true);
            }
            case 1150 -> {
                JinzenScreen screen = jinzenScreen(minecraft);
                Optional<JinzenStep> step = jinzenStep(minecraft);
                expect(minecraft, screen != null && step.isPresent(), "the Jinzen screen showed no ribbons to take");
                if (screen != null && step.isPresent()) {
                    screen.answer(step.get().highlight());
                }
            }
            // Closing the screen lets go of the blade, as pressing Escape would.
            case 1205 -> minecraft.setScreen(null);
            case 1245 -> command(minecraft, "bleach zanpakuto @s name");
            case 1295 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.RELEASE);
            // Held for a second, then let go: a Getsuga Tenshō of two-thirds charge.
            case 1345 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.TECHNIQUE_PRESS);
            case 1365 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.TECHNIQUE_RELEASE);
            // As the soul screen key opens it.
            case 1420 -> {
                if (minecraft.player != null && minecraft.screen == null) {
                    minecraft.setScreen(new SoulStatsScreen());
                }
            }
            case 1425 -> shot(minecraft, "stats_zanpakuto");
            case 1440 -> minecraft.setScreen(null);
            // An Echt Quincy of the third tier, no longer a Shinigami, in the Quincy garb with the Cross in
            // hand, facing north towards a still Hollow. Hollows vanish on Peaceful.
            case 1450 -> {
                command(minecraft, "difficulty easy");
                command(minecraft, "clear @s");
                command(minecraft, "effect clear @s");
                command(minecraft, "bleach soul @s human");
                command(minecraft, "bleach tier @s shinigami 0");
                command(minecraft, "bleach quincy @s bloodline echt");
                command(minecraft, "bleach tier @s quincy 3");
                command(minecraft, "bleach reiryoku @s 200 200");
                command(minecraft, "bleach quincy @s reishi 200");
                command(minecraft, "tp @s ~ ~ ~ 180 0");
            }
            case 1451 -> {
                command(minecraft, "item replace entity @s weapon.mainhand with bleach:quincy_cross");
                command(minecraft, "item replace entity @s armor.chest with bleach:quincy_tunic");
                command(minecraft, "item replace entity @s armor.legs with bleach:quincy_trousers");
                command(minecraft, "item replace entity @s armor.feet with bleach:quincy_shoes");
                command(minecraft, "summon bleach:spider_hollow ~ ~ ~-6 " + STILL);
            }
            // The first arrow goes up and north at 45 degrees, into an empty sky, drawn through the real use key.
            case 1476 -> look(minecraft, player.getEyePosition().add(0.0, 10.0, -10.0));
            case 1478 -> minecraft.options.keyUse.setDown(true);
            case 1490 -> {
                expect(minecraft, player.isUsingItem(), "holding use with the Quincy Cross did not draw the bow");
                shot(minecraft, "heilig_bogen");
            }
            case 1502 -> minecraft.options.keyUse.setDown(false);
            // The second goes at the Hollow, too far off for the use key to reach it instead.
            case 1535 -> {
                subject = nearest(minecraft, SpiderHollow.class, SUMMONED_RANGE,
                        spider -> spider.isAlive() && isSpiderHollow(spider));
                expect(minecraft, subject != null, "the Spider Hollow in front of the Quincy never reached the client");
                if (subject != null) {
                    look(minecraft, subject.getBoundingBox().getCenter());
                }
            }
            case 1538 -> minecraft.options.keyUse.setDown(true);
            case 1562 -> minecraft.options.keyUse.setDown(false);
            case 1605 -> BleachNetwork.requestQuincyKey(QuincyKey.BLUT);
            case 1645 -> {
                command(minecraft, "kill @e[type=#bleach:hollows]");
                command(minecraft, "difficulty peaceful");
            }
            // Holding the Cross, so the movement key's dash is Hirenkyaku.
            case 1650 -> {
                dashFrom = player.position();
                BleachNetwork.requestFlashStep(1.0F, 0.0F);
            }
            case 1685 -> BleachNetwork.requestQuincyKey(QuincyKey.BLUT_OFF);
            case 1690 -> minecraft.setScreen(new SoulStatsScreen());
            case 1695 -> shot(minecraft, "stats_quincy");
            case 1710 -> minecraft.setScreen(null);
            // The arts past the third tier, still facing north with the Cross in hand.
            case 1730 -> {
                command(minecraft, "bleach quincy @s glove mastered");
                command(minecraft, "bleach quincy @s reishi 200");
                command(minecraft, "difficulty easy");
                command(minecraft, "tp @s ~ ~ ~ 180 0");
            }
            // A full draw in the mastered glove, into the sky again, looses two flank arrows beside the shot.
            case 1752 -> look(minecraft, player.getEyePosition().add(0.0, 10.0, -10.0));
            case 1754 -> minecraft.options.keyUse.setDown(true);
            case 1778 -> minecraft.options.keyUse.setDown(false);
            // Two taps of the Quincy Form key, well within three seconds, tear the glove. They are
            // more than the server's five-tick debounce apart, with room for a slow tick between.
            case 1790, 1806 -> BleachNetwork.requestQuincyKey(QuincyKey.FORM);
            // Drawn past Letzt Stil's short draw and the charge after it, for a Letzt Pfeil.
            case 1835 -> minecraft.options.keyUse.setDown(true);
            case 1890 -> minecraft.options.keyUse.setDown(false);
            case 1915 -> command(minecraft, "bleach quincy @s letzt seal");
            case 1940 -> minecraft.options.keyUse.setDown(true);
            case 1948 -> expect(minecraft, !player.isUsingItem(), "a sealed Quincy drew the Heilig Bogen");
            case 1949 -> minecraft.options.keyUse.setDown(false);
            case 1955 -> command(minecraft, "bleach quincy @s letzt restore");
            // Ginrei Kojaku streams while held; the Quincy Form key mid-draw calls down Licht Regen.
            case 1980 -> minecraft.options.keyUse.setDown(true);
            case 1990 -> shot(minecraft, "ginrei_kojaku");
            case 2035 -> BleachNetwork.requestQuincyKey(QuincyKey.FORM);
            case 2100 -> minecraft.options.keyUse.setDown(false);
            case 2105 -> command(minecraft, "item replace entity @s weapon.mainhand with bleach:seele_schneider 4");
            // A Seele Schneider in the offhand is fired in place of the stream, down at the ground ahead.
            case 2135 -> {
                command(minecraft, "item replace entity @s weapon.mainhand with bleach:quincy_cross");
                command(minecraft, "item replace entity @s weapon.offhand with bleach:seele_schneider 4");
            }
            case 2137 -> look(minecraft, player.getEyePosition().add(0.0, -2.0, -6.0));
            case 2139 -> minecraft.options.keyUse.setDown(true);
            case 2161 -> minecraft.options.keyUse.setDown(false);
            case 2190 -> {
                command(minecraft, "item replace entity @s weapon.offhand with minecraft:air");
                command(minecraft, "item replace entity @s weapon.mainhand with bleach:ginto 8");
                command(minecraft, "bleach quincy @s ginto wolke");
            }
            case 2193 -> minecraft.options.keyUse.setDown(true);
            case 2195 -> minecraft.options.keyUse.setDown(false);
            case 2220 -> {
                command(minecraft, "bleach quincy @s ginto heizen");
                command(minecraft, "summon bleach:spider_hollow ~ ~ ~-6 " + STILL);
            }
            case 2236 -> {
                subject = nearest(minecraft, SpiderHollow.class, SUMMONED_RANGE,
                        spider -> spider.isAlive() && isSpiderHollow(spider));
                expect(minecraft, subject != null, "the Spider Hollow for Heizen never reached the client");
                if (subject != null) {
                    look(minecraft, subject.getBoundingBox().getCenter());
                }
            }
            // The Gintō cooldown from the Wolke ended at about 2233.
            case 2240 -> minecraft.options.keyUse.setDown(true);
            case 2242 -> minecraft.options.keyUse.setDown(false);
            case 2295 -> {
                command(minecraft, "kill @e[type=#bleach:hollows]");
                command(minecraft, "bleach quincy @s training 50000");
                command(minecraft, "bleach quincy @s reishi 200");
                command(minecraft, "item replace entity @s weapon.mainhand with bleach:quincy_cross");
            }
            case 2300 -> BleachNetwork.requestQuincyKey(QuincyKey.FORM);
            // Vollständig's flight, taken up as a player takes up creative flight, and climbed with jump.
            case 2325 -> {
                flightFrom = player.getY();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                minecraft.options.keyJump.setDown(true);
            }
            case 2360 -> minecraft.options.keyJump.setDown(false);
            // Sklaverei on and off again, ten ticks before the press that ends Vollständig.
            case 2380, 2400 -> BleachNetwork.requestQuincyKey(QuincyKey.FORM_SNEAK);
            // Let go in the air: the landing that follows must not hurt.
            case 2410 -> {
                healthBefore = player.getHealth();
                BleachNetwork.requestQuincyKey(QuincyKey.FORM);
            }
            case 2490 -> minecraft.setScreen(new SoulStatsScreen());
            case 2495 -> shot(minecraft, "stats_quincy_arts");
            case 2510 -> minecraft.setScreen(null);
            // The Quincy in the world: the Elder and the Hunter, summoned to stand still to the north.
            case 2530 -> {
                command(minecraft, "kill @e[type=#bleach:hollows]");
                command(minecraft, "effect clear @s");
                command(minecraft, "bleach reiryoku @s 200 200");
                command(minecraft, "bleach quincy @s reishi 200");
                command(minecraft, "tp @s ~ ~ ~ 180 0");
                command(minecraft, "summon bleach:quincy_elder ~ ~ ~-3 " + STILL);
            }
            case 2531 -> command(minecraft, "summon bleach:quincy_hunter ~3 ~ ~-3 " + STILL);
            case 2562 -> {
                shot(minecraft, "quincy_npcs");
                subject = nearest(minecraft, QuincyElder.class, 8.0, LivingEntity::isAlive);
                expect(minecraft, subject != null, "the summoned Quincy Elder never reached the client");
                if (subject != null) {
                    minecraft.gameMode.interact(player, subject, InteractionHand.MAIN_HAND);
                }
            }
            case 2586 -> {
                shot(minecraft, "elder_dialogue");
                if (subject != null) {
                    BleachNetwork.requestNpcAction(subject.getId(), NpcAction.RANSOTENGAI);
                }
                minecraft.setScreen(null);
            }
            // The real key: in the trial, the threads are woven with no wait.
            case 2612 -> BleachNetwork.requestQuincyKey(QuincyKey.FORM_SNEAK);
            case 2780 -> {
                subject = nearest(minecraft, QuincyHunter.class, 8.0, LivingEntity::isAlive);
                expect(minecraft, subject != null, "the summoned Quincy Hunter never reached the client");
                if (subject != null) {
                    minecraft.gameMode.interact(player, subject, InteractionHand.MAIN_HAND);
                }
            }
            case 2805 -> {
                command(minecraft, "item replace entity @s armor.head with bleach:wandenreich_cap");
                command(minecraft, "item replace entity @s armor.chest with bleach:wandenreich_coat");
                command(minecraft, "item replace entity @s armor.legs with bleach:wandenreich_trousers");
                command(minecraft, "item replace entity @s armor.feet with bleach:wandenreich_boots");
            }
            case 2822 -> command(minecraft, "setblock ~2 ~ ~ bleach:tailoring_table");
            case 2826 -> {
                BlockPos table = BlockPos.containing(player.getX() + 2.0, player.getY(), player.getZ());
                minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
            }
            case 2852 -> {
                expect(minecraft, minecraft.screen instanceof TailoringScreen tailoring && tailoring.page() == 1,
                        "the Tailoring Table has no second page");
                shot(minecraft, "tailoring_page_2");
            }
            case 2856 -> player.closeContainer();
            // Hollow Bait, by command: the instructor close by and the quiet game rule are passed over.
            case 2860 -> {
                command(minecraft, "kill @e[type=bleach:quincy_hunter]");
                command(minecraft, "kill @e[type=bleach:quincy_elder]");
                command(minecraft, "effect give @s minecraft:resistance 60 4 true");
                command(minecraft, "bleach bait @s start");
            }
            case 2892 -> command(minecraft, "bleach bait @s wave");
            case 3025 -> command(minecraft, "bleach bait @s finale");
            case 3085 -> {
                command(minecraft, "bleach bait @s stop");
                command(minecraft, "kill @e[type=#bleach:hollows]");
            }
            case 3145 -> {
                command(minecraft, "effect clear @s");
                command(minecraft, "bleach quincy @s ransotengai start");
            }
            // The real key again: a woven soul's press lets the threads go, with the collapse.
            case 3175 -> BleachNetwork.requestQuincyKey(QuincyKey.FORM_SNEAK);
            case 3205 -> minecraft.setScreen(new SoulStatsScreen());
            case 3210 -> shot(minecraft, "stats_quincy_world");
            case 3225 -> minecraft.setScreen(null);
            // The Hollow player. The Wandenreich uniform is still on, so the hide has to shed it.
            case 3245 -> {
                command(minecraft, "effect clear @s");
                command(minecraft, "bleach soul @s hollow");
            }
            case 3305 -> command(minecraft, "summon bleach:plus ~ ~ ~-2 " + STILL);
            // The real key, held on the Plus: it is seized, the jaw opens, and it is devoured.
            case 3325 -> {
                subject = nearest(minecraft, PlusSpirit.class, 4.0, LivingEntity::isAlive);
                expect(minecraft, subject != null, "the summoned Plus never reached the client");
                if (subject != null) {
                    look(minecraft, subject.getBoundingBox().getCenter());
                }
                HollowKeys.DEVOUR.setDown(true);
            }
            // Sneak with the key turns the Hollow sight off.
            case 3382 -> {
                HollowKeys.DEVOUR.setDown(false);
                minecraft.options.keyShift.setDown(true);
            }
            case 3384 -> HollowKeys.DEVOUR.setDown(true);
            case 3388 -> {
                HollowKeys.DEVOUR.setDown(false);
                minecraft.options.keyShift.setDown(false);
            }
            case 3412 -> minecraft.setScreen(new SoulStatsScreen());
            case 3417 -> shot(minecraft, "stats_hollow");
            // A last look in first person, bare-handed: the claws and the edge of the mask.
            case 3420 -> {
                minecraft.setScreen(null);
                command(minecraft, "item replace entity @s weapon.mainhand with minecraft:air");
            }
            case 3422 -> minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            case 3432 -> shot(minecraft, "hollow_first_person");
            // The Menos Grande, by summon and standing still, so the shot is the same every run.
            case 3436 -> {
                command(minecraft, "effect give @s minecraft:resistance 30 4 true");
                command(minecraft, "tp @s ~ ~ ~ 180 -25");
                command(minecraft, "summon bleach:gillian ~ ~ ~-14 " + STILL);
                command(minecraft, "summon bleach:adjuchas ~5 ~ ~-7 " + STILL);
            }
            case 3490 -> shot(minecraft, "menos");
            // A Cero as a picture: it strikes nothing.
            case 3495 -> command(minecraft,
                    "summon bleach:cero_beam ~ ~1 ~-3 {Reach:[0f,0f,-30f],Radius:1.2f,Colour:14357535,Life:60}");
            case 3540 -> command(minecraft, "kill @e[type=#bleach:hollows]");
            // The player's own Menos forms, by command, in a space cleared for a Gillian's height.
            case 3548 -> {
                command(minecraft, "fill ~-3 ~ ~-3 ~3 ~12 ~3 air");
                command(minecraft, "bleach reiryoku @s 600 600");
                command(minecraft, "bleach tier @s hollow 2");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                command(minecraft, "tp @s ~ ~ ~ 0 0");
            }
            // The real key: held, the Cero gathers; let go at 35 of its 50 ticks, it fires at 70%.
            case 3690 -> HollowKeys.CERO.setDown(true);
            case 3725 -> HollowKeys.CERO.setDown(false);
            case 3745 -> command(minecraft, "bleach tier @s hollow 3");
            case 3775 -> command(minecraft, "bleach tier @s hollow 4");
            // The Vasto Lorde tears free by command, and is seen from in front: fragment, hole and blade. It is
            // seated as the Cuatro Espada by command, in the same tick, so its number is tattooed.
            case 3800 -> {
                command(minecraft, "item replace entity @s weapon.mainhand with minecraft:air");
                command(minecraft, "bleach soul @s arrancar");
                command(minecraft, "bleach rank @s espada 4");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 3832 -> minecraft.setScreen(new SoulStatsScreen());
            case 3836 -> shot(minecraft, "stats_arrancar");
            case 3838 -> minecraft.setScreen(null);
            // The real keys: Release calls out the Resurrección of the blade in hand, and Seal ends it.
            case 3840 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.RELEASE);
            case 3875 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.SEAL);
            // Sonído through the real flash step request, in a lane cleared south: further than a first Shunpo.
            case 3900 -> {
                command(minecraft, "fill ~-2 ~ ~1 ~2 ~3 ~24 air");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            // Seen from behind before the dash. A tattoo on the chest, hip or back is in this shot or in the arrancar
            // shot from in front; one on the left arm is edge-on to both, so for it these beats only prove it draws.
            case 3901 -> shot(minecraft, "arrancar_back");
            case 3902 -> {
                dashFrom = player.position();
                BleachNetwork.requestFlashStep(1.0F, 0.0F);
            }
            // Pesquisa through the real Devour key: a Plus stands still behind a stone wall, and the pulse marks it.
            case 3920 -> {
                command(minecraft, "fill ~-2 ~ ~3 ~2 ~3 ~3 stone");
                command(minecraft, "summon bleach:plus ~ ~ ~6 " + STILL);
            }
            case 3925 -> HollowKeys.DEVOUR.setDown(true);
            case 3950 -> HollowKeys.DEVOUR.setDown(false);
            case 3975 -> command(minecraft, "kill @e[type=bleach:plus]");
            // The Gran Rey Cero through the real keys: seated by command at 3800, strong enough to pay by command,
            // sneaking, the Cero key held until it fires itself.
            case 3990 -> {
                command(minecraft, "bleach reiryoku @s 2000 2000");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                minecraft.options.keyShift.setDown(true);
            }
            case 3994 -> HollowKeys.CERO.setDown(true);
            case 4020 -> {
                HollowKeys.CERO.setDown(false);
                minecraft.options.keyShift.setDown(false);
            }
            // Negación by command: a summoned light, mounted, rises; sneaking leaves it.
            case 4040 -> command(minecraft, "summon bleach:negacion ~ ~ ~");
            case 4042 -> {
                negacionFrom = player.position();
                command(minecraft, "ride @s mount @e[type=bleach:negacion,limit=1,sort=nearest]");
            }
            case 4095 -> minecraft.options.keyShift.setDown(true);
            case 4099 -> minecraft.options.keyShift.setDown(false);
            // The white uniform by command, seen from in front: jacket and sash, hakama, zōri over black tabi.
            case 4120 -> {
                command(minecraft, "item replace entity @s armor.chest with bleach:arrancar_jacket");
                command(minecraft, "item replace entity @s armor.legs with bleach:arrancar_hakama");
                command(minecraft, "item replace entity @s armor.feet with bleach:arrancar_zori");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            // Arrancar NPCs by command, seen from behind: a bare-chested Privaron Espada and a Número in the whole
            // uniform stand still facing the player, either side of its body so it hides neither, where the stone
            // wall of the Pesquisa beat is cleared away.
            case 4150 -> {
                command(minecraft, "fill ~-2 ~ ~1 ~2 ~3 ~6 air");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                command(minecraft, "summon bleach:arrancar ~-2 ~ ~4 " + PRIVARON_NPC);
                command(minecraft, "summon bleach:arrancar ~2 ~ ~4 " + NUMERO_NPC);
            }
            // A Caja Negación by command: summoned beside the player, it closes on the Número mounted in it and hides
            // it, and lets it out when killed.
            case 4185 -> command(minecraft, "summon bleach:caja_negacion ~2 ~ ~4");
            case 4187 -> command(minecraft, "ride @e[type=bleach:arrancar,limit=1,nbt={Rank:{numero:34}}] mount "
                    + "@e[type=bleach:caja_negacion,limit=1,sort=nearest]");
            case 4222 -> command(minecraft, "kill @e[type=bleach:caja_negacion]");
            // Then the player itself, in first person: sneaking does not get it out, and the kill lets it go.
            case 4237 -> {
                command(minecraft, "summon bleach:caja_negacion ~ ~ ~");
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            }
            case 4239 -> command(minecraft, "ride @s mount @e[type=bleach:caja_negacion,limit=1,sort=nearest]");
            case 4268 -> minecraft.options.keyShift.setDown(true);
            case 4272 -> minecraft.options.keyShift.setDown(false);
            case 4280 -> {
                if (!(player.getVehicle() instanceof CajaNegacion)) {
                    fail(minecraft, "sneaking let the player out of the Caja Negación");
                }
            }
            case 4281 -> command(minecraft, "kill @e[type=bleach:caja_negacion]");
            // A Shinigami again by command, facing south and seen from behind, its blade in hand: the forced fight
            // roots its Hollow, readies it and brings it out ahead of the player, and the command's win beats it.
            case 4300 -> {
                command(minecraft, "kill @e[type=bleach:arrancar]");
                command(minecraft, "bleach soul @s shinigami");
                command(minecraft, "bleach reiryoku @s 800 800");
                command(minecraft, "effect clear @s");
                command(minecraft, "item replace entity @s weapon.mainhand with bleach:asauchi");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case 4305 -> command(minecraft, "bleach visored @s duel");
            case 4315 -> command(minecraft, "bleach visored @s subdue");
            // The Visored's mask by command, seen from in front; it would last past the end, so only the key takes it
            // off. Then in first person, inside the mask's edge, and the real Hollow Mask key, held for two ticks.
            case 4342 -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 4345 -> command(minecraft, "bleach visored @s mask");
            case 4370 -> minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            case 4390 -> ZanpakutoKeys.MASK.setDown(true);
            case 4392 -> ZanpakutoKeys.MASK.setDown(false);
            // The Visored's arts, seen from behind and looking up so neither carves anything. The reset makes the
            // blade's state certain after the run's many changes, and the fresh Asauchi in hand is bound to Zangetsu
            // again; the reiryoku comes after the name, so the name's growth does not matter. Ten minutes of masks
            // worn opens the masked Cero.
            case 4440 -> {
                command(minecraft, "bleach zanpakuto @s reset");
                command(minecraft, "bleach zanpakuto @s spirit bleach:zangetsu");
                command(minecraft, "bleach zanpakuto @s bond 1000");
                command(minecraft, "bleach zanpakuto @s name");
                command(minecraft, "bleach reiryoku @s 800 800");
                command(minecraft, "bleach visored @s train 600");
                command(minecraft, "tp @s ~ ~ ~ 0 -30");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case 4450 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.RELEASE);
            // Forced, so the wait from the key does not matter; at ten minutes trained it lasts 41 seconds, past the end.
            case 4465 -> command(minecraft, "bleach visored @s mask");
            // The real key: held, the masked Cero gathers; let go at 35 of its 50 ticks, it fires at 70%.
            case 4475 -> HollowKeys.CERO.setDown(true);
            case 4510 -> HollowKeys.CERO.setDown(false);
            // Held for a second, then let go: a black Getsuga Tenshō of two-thirds charge.
            case 4540 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.TECHNIQUE_PRESS);
            case 4560 -> BleachNetwork.requestZanpakutoKey(ZanpakutoKey.TECHNIQUE_RELEASE);
            // As the soul screen key opens it: the Visored's state, its mask's time left and the masked Cero's key.
            case 4584 -> minecraft.setScreen(new SoulStatsScreen());
            case 4588 -> {
                expect(minecraft, minecraft.screen instanceof SoulStatsScreen && soul.shinigami().visored().masked()
                                && VisoredLogic.ceroUnlocked(soul),
                        "the soul screen was not open on the masked Visored with the masked Cero");
                shot(minecraft, "stats_visored");
            }
            case 4590 -> minecraft.setScreen(null);
            // The Hollow's hold, seen from in front: the reset takes the mask off and the Hollow away, so the Hollow
            // rooted again by command is not yet beaten, and it seizes the body by command with half a mask, for ten
            // seconds, past the key. The rooting's Nausea and Weakness are cleared first, so the shots are not blurred.
            // Then in first person, and the real Hollow Mask key, held for two ticks, tears the half mask off.
            case 4600 -> {
                command(minecraft, "bleach visored @s reset");
                command(minecraft, "bleach visored @s root");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 4605 -> command(minecraft, "effect clear @s");
            case 4610 -> command(minecraft, "bleach visored @s takeover");
            case 4645 -> minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            case 4670 -> ZanpakutoKeys.MASK.setDown(true);
            case 4672 -> ZanpakutoKeys.MASK.setDown(false);
            // Kidō by name, and Shakkahō and Sōkatsui by incantation, seen from behind and facing south. The run's Quincy
            // power is taken away first, since an awakened Quincy cannot use Kidō (by its tier alone: a reset would leave
            // the bloodline open, and the steps wait for it), and the Hollow too, so its voice never takes the action
            // bar. At Kidō 10 the soul knows Shō, Byakurai, Shakkahō and Sōkatsui, and Sai, Hainawa, Geki and Sekienton,
            // which come after every Hadō, and Kaidō after every Bakudō; having never chosen, it casts Shō first. The
            // villager stands tall enough for a level beam from the eyes to meet it.
            case 4700 -> {
                command(minecraft, "bleach tier @s quincy 0");
                command(minecraft, "bleach visored @s reset");
                command(minecraft, "bleach stat @s kido 10");
                command(minecraft, "bleach reiryoku @s 800 800");
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case 4702 -> command(minecraft, "summon minecraft:villager ~ ~ ~4 {NoAI:1b,Silent:1b}");
            // The real Kidō key, held for two ticks: one press, which casts Shō.
            case 4710 -> KidoKeys.CAST.setDown(true);
            case 4712 -> KidoKeys.CAST.setDown(false);
            // Sneaking, the real key chooses the next spell, Byakurai, and casts nothing; then the key alone casts it.
            case 4735 -> minecraft.options.keyShift.setDown(true);
            case 4738 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 4740 -> KidoKeys.CAST.setDown(false);
            case 4742 -> minecraft.options.keyShift.setDown(false);
            // At full strength, since it has no incantation: about 9 of the villager's 20, so it lives.
            case 4760 -> KidoKeys.CAST.setDown(true);
            case 4762 -> KidoKeys.CAST.setDown(false);
            // Sneaking again, the key chooses Shakkahō, and the server prints its incantation in the chat, to be read.
            case 4770 -> minecraft.options.keyShift.setDown(true);
            case 4773 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 4775 -> KidoKeys.CAST.setDown(false);
            case 4777 -> minecraft.options.keyShift.setDown(false);
            // Its romaji, sent whole through the real chat as a player types it: the server keeps it from the chat and
            // speaks it instead, and casts the spell 30 ticks on, at full strength, where the player then looks. The key
            // is never pressed on a spell with an incantation, whose cast by name may misfire.
            case 4790 -> chant(minecraft, KidoSpell.SHAKKAHO, 0);
            // Shakkahō's 15 or so killed the first villager, so a fresh one stands for Sōkatsui.
            case 4870 -> command(minecraft, "summon minecraft:villager ~ ~ ~4 {NoAI:1b,Silent:1b}");
            // Sōkatsui in the mod's own English, its second incantation. Shakkahō's cooldown let the hands go about 4851.
            case 4880 -> chant(minecraft, KidoSpell.SOKATSUI, 1);
            // Tenran by the key, then Raikōhō gathered with the key held, seen from behind and facing south. The
            // villagers about are killed first, so a hurt one can only be the fresh one Tenran struck. Kidō 18 teaches
            // Tenran, and the reiryoku is filled again.
            case 4960 -> {
                command(minecraft, "kill @e[type=minecraft:villager,distance=..8]");
                command(minecraft, "bleach stat @s kido 18");
                command(minecraft, "bleach reiryoku @s 800 800");
            }
            case 4962 -> command(minecraft, "summon minecraft:villager ~ ~ ~4 {NoAI:1b,Silent:1b}");
            // Sneaking, the real key chooses twice: Sōkatsui after Shakkahō, still chosen as a chant chooses nothing, then
            // Tenran.
            case 4965 -> minecraft.options.keyShift.setDown(true);
            case 4968 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 4970 -> KidoKeys.CAST.setDown(false);
            case 4972 -> KidoKeys.CAST.setDown(true);
            case 4974 -> KidoKeys.CAST.setDown(false);
            case 4976 -> minecraft.options.keyShift.setDown(false);
            // The key alone casts Tenran at once, at full strength, as it has no incantation; its let-go finds nothing
            // gathering, so it does nothing.
            case 4990 -> KidoKeys.CAST.setDown(true);
            case 4992 -> KidoKeys.CAST.setDown(false);
            // At Kidō 42 Raikōhō cast by name never misfires, so no beat is left to chance. Sneaking, the key chooses it.
            case 5020 -> command(minecraft, "bleach stat @s kido 42");
            case 5022 -> minecraft.options.keyShift.setDown(true);
            case 5025 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5027 -> KidoKeys.CAST.setDown(false);
            case 5029 -> minecraft.options.keyShift.setDown(false);
            // The real key, held: one press, and Raikōhō gathers in the palm for a second and goes off by itself, about
            // 5080. The let-go comes only after the bolt's window has closed, so the bolt cannot be the let-go's.
            case 5060 -> KidoKeys.CAST.setDown(true);
            case 5106 -> KidoKeys.CAST.setDown(false);
            // Sai, then Hainawa, by the key at a villager: each trace must stop at it. Raikōhō's bolt may have killed the
            // last villager, and any left are killed, so a fresh one stands alone four blocks south; the reiryoku is
            // filled again.
            case 5120 -> {
                command(minecraft, "kill @e[type=minecraft:villager,distance=..8]");
                command(minecraft, "bleach reiryoku @s 800 800");
            }
            case 5122 -> command(minecraft, "summon minecraft:villager ~ ~ ~4 {NoAI:1b,Silent:1b}");
            // Sneaking, the real key chooses Sai, the first Bakudō, after Raikōhō, the last Hadō: choosing waits for no
            // cooldown.
            case 5130 -> minecraft.options.keyShift.setDown(true);
            case 5133 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5135 -> KidoKeys.CAST.setDown(false);
            case 5137 -> minecraft.options.keyShift.setDown(false);
            // Raikōhō's cooldown lets the hands go about 5180. The key alone casts Sai at once, at full strength, as it has
            // no incantation, and its let-go finds nothing gathering. A villager's 20 binds for the whole 3 seconds.
            case 5190 -> KidoKeys.CAST.setDown(true);
            case 5192 -> KidoKeys.CAST.setDown(false);
            // The motes at the bound villager's wrists, for a person to look at: a mob's effects never reach a client.
            case 5199 -> shot(minecraft, "kido_sai_bound");
            // Sneaking, the key chooses Hainawa, the next Bakudō.
            case 5202 -> minecraft.options.keyShift.setDown(true);
            case 5205 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5207 -> KidoKeys.CAST.setDown(false);
            case 5209 -> minecraft.options.keyShift.setDown(false);
            // Sai's cooldown let the hands go about 5221. The villager is wary of Sai now, but not of Hainawa.
            case 5228 -> KidoKeys.CAST.setDown(true);
            case 5230 -> KidoKeys.CAST.setDown(false);
            // The yellow ring at the held villager's waist.
            case 5240 -> shot(minecraft, "kido_hainawa_held");
            // Geki, then Rikujōkōrō, by incantation at a villager: each binds the arms and holds the body at once, and its
            // trace must stop at it. The held villager is killed, as Hainawa's longer hold would keep Geki's shorter one
            // from landing, and a fresh one stands alone four blocks south; the reiryoku is filled again.
            case 5262 -> {
                command(minecraft, "kill @e[type=minecraft:villager,distance=..8]");
                command(minecraft, "bleach reiryoku @s 800 800");
            }
            case 5264 -> command(minecraft, "summon minecraft:villager ~ ~ ~4 {NoAI:1b,Silent:1b}");
            // Sneaking, the key chooses Geki, the Bakudō after Hainawa, and the server prints its incantation in the chat.
            case 5270 -> minecraft.options.keyShift.setDown(true);
            case 5273 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5275 -> KidoKeys.CAST.setDown(false);
            case 5277 -> minecraft.options.keyShift.setDown(false);
            // Its romaji, sent whole through the real chat. Hainawa's cooldown let the hands go about 5270, so it goes off
            // 30 ticks after it is heard, about 5321, at full strength, where the player then looks.
            case 5290 -> chant(minecraft, KidoSpell.GEKI, 0);
            // The red glow about the bound and held villager, for a person to look at.
            case 5326 -> shot(minecraft, "kido_geki_bound");
            // Sneaking, the key chooses Sekienton, the Bakudō after Geki, and prints nothing: it has none to print.
            // Choosing waits for no cooldown.
            case 5335 -> minecraft.options.keyShift.setDown(true);
            case 5338 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5340 -> KidoKeys.CAST.setDown(false);
            case 5342 -> minecraft.options.keyShift.setDown(false);
            // Rikujōkōrō's romaji, heard though Sekienton is chosen: a chant is heard as the spell whose words it is.
            // Geki's cooldown lets the hands go about 5381, before the chant's 40 ticks are said, so it goes off about
            // 5396. Geki's hold has ended by then, and the villager is wary of Geki but not of Rikujōkōrō.
            case 5355 -> chant(minecraft, KidoSpell.RIKUJOKORO, 0);
            // The six rods of light at the held villager's waist.
            case 5405 -> shot(minecraft, "kido_rikujokoro_held");
            // Sekienton by the key, still chosen, as a chant chooses nothing. Rikujōkōrō's cooldown lets the hands go
            // about 5516. Its red smoke stands where the player stands for 4 seconds, about 5611, and quickens the player
            // for 2, about 5571, but never blinds its own caster; the villager in it loses the player, which no client is
            // told.
            case 5530 -> KidoKeys.CAST.setDown(true);
            case 5532 -> KidoKeys.CAST.setDown(false);
            // Sneaking, the key chooses Enkōsen, the Bakudō after Sekienton: choosing waits for no cooldown.
            case 5570 -> minecraft.options.keyShift.setDown(true);
            case 5573 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5575 -> KidoKeys.CAST.setDown(false);
            case 5577 -> minecraft.options.keyShift.setDown(false);
            // Sekienton's cooldown lets the hands go about 5731. The key alone raises Enkōsen before the player for 3
            // seconds, about 5806, nearly 23 strong at 800 reiryoku and Kidō 42.
            case 5745 -> KidoKeys.CAST.setDown(true);
            case 5747 -> KidoKeys.CAST.setDown(false);
            // A blow of 4 from two blocks ahead, to the south the player has faced since 4700, which nothing has turned:
            // the shield takes it whole, so the hurt is refused, and the command says so in the chat. Coming from no
            // creature, it is not scaled by the difficulty, and the garb is cloth, so nothing else weighs it.
            case 5760 -> healthBefore = player.getHealth() + player.getAbsorptionAmount();
            case 5761 -> command(minecraft, "damage @s 4 minecraft:mob_attack at ^ ^ ^2");
            // The same from two blocks behind, where the shield does not face: it lands.
            case 5776 -> command(minecraft, "damage @s 4 minecraft:mob_attack at ^ ^ ^-2");
            // Sneaking, the key chooses Rikujōkōrō and then Dankū, the Bakudō after Enkōsen: choosing waits for no
            // cooldown.
            case 5810 -> minecraft.options.keyShift.setDown(true);
            case 5813 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5815 -> KidoKeys.CAST.setDown(false);
            case 5817 -> minecraft.options.keyShift.setDown(false);
            case 5830 -> minecraft.options.keyShift.setDown(true);
            case 5833 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 5835 -> KidoKeys.CAST.setDown(false);
            case 5837 -> minecraft.options.keyShift.setDown(false);
            // Enkōsen's cooldown lets the hands go about 5906. The key alone raises Dankū 2 blocks south of the player
            // for 6 seconds, about 6041, for 160 of the 520 or more reiryoku left.
            case 5920 -> KidoKeys.CAST.setDown(true);
            case 5922 -> KidoKeys.CAST.setDown(false);
            // Sneaking, the key chooses Kaidō, which comes after Dankū, the last Bakudō.
            case 6090 -> minecraft.options.keyShift.setDown(true);
            case 6093 -> {
                expect(minecraft, player.isShiftKeyDown(), "the sneak key never made the player sneak for the Kidō key");
                KidoKeys.CAST.setDown(true);
            }
            case 6095 -> KidoKeys.CAST.setDown(false);
            case 6097 -> minecraft.options.keyShift.setDown(false);
            // A fresh villager two blocks south, within Kaidō's reach of 3 from the eyes, hurt by half its health. Every
            // creature within 64 blocks but the player goes first, so the villager is the first one Kaidō meets: the
            // Pluses the earlier villagers left as they died, the flat world's animals (they come with its chunks even
            // without mob spawning) and the wounded Shinigami from the start, which would leave about 6262 with a
            // farewell that takes the action bar from Kaidō's line. A villager leaves its Plus a tick after it dies, so the
            // last one's goes at 6104.
            case 6100 -> command(minecraft, "kill @e[type=!minecraft:player,distance=..64]");
            case 6102 -> command(minecraft, "summon minecraft:villager ~ ~ ~2 {NoAI:1b,Silent:1b}");
            case 6104 -> command(minecraft, "kill @e[type=bleach:plus,distance=..64]");
            case 6110 -> command(minecraft, "damage @e[type=minecraft:villager,limit=1,sort=nearest] 10 minecraft:generic");
            // Dankū's cooldown frees the hands about 6221. The key held heals the villager two hearts a second (Kidō 42):
            // about 6241, 6261 and 6281, whole by the third, which comes 24 ticks before the key is let go.
            case 6240 -> KidoKeys.CAST.setDown(true);
            case 6305 -> KidoKeys.CAST.setDown(false);
            // With no one in reach, Kaidō from Kidō 40 heals the player's own wounds at half the rate, one heart a second,
            // about 6351, 6371 and 6391, the third 24 ticks before the key is let go; natural regeneration is off
            // meanwhile, so only Kaidō heals. A generic blow with no attacker ignores armour and difficulty. Every creature
            // goes again first, the villager's Plus at 6324.
            case 6320 -> {
                command(minecraft, "kill @e[type=!minecraft:player,distance=..64]");
                command(minecraft, "gamerule naturalRegeneration false");
            }
            case 6324 -> command(minecraft, "kill @e[type=bleach:plus,distance=..64]");
            case 6325 -> command(minecraft, "damage @s 8 minecraft:generic");
            case 6335 -> healthBefore = player.getHealth();
            case 6350 -> KidoKeys.CAST.setDown(true);
            case 6415 -> KidoKeys.CAST.setDown(false);
            // Natural regeneration back, once the last window has closed.
            case 6425 -> command(minecraft, "gamerule naturalRegeneration true");
            // The Kidō page, from the soul screen's own button.
            case 6455 -> minecraft.setScreen(new SoulStatsScreen());
            case 6460 -> {
                Button kido = minecraft.screen instanceof SoulStatsScreen stats ? stats.kidoButton() : null;
                expect(minecraft, kido != null, "the soul screen offered a Shinigami of Kidō 42 no Kidō page");
                if (kido != null) {
                    kido.onPress();
                }
            }
            // A click on Sōkatsui's row as the last frame drew it, through the page's own hit test; the chat is cleared
            // so the incantation it prints is new.
            case 6490 -> {
                chatLines.clear();
                expect(minecraft, minecraft.screen instanceof KidoScreen page
                                && page.clickRow(KidoSpell.SOKATSUI.location()),
                        "the Kidō page drew no row for Sōkatsui to click");
            }
            // Sōkatsui marked: the window from 6493 passed by 6520, and the frames since show the synced choice.
            case 6525 -> shot(minecraft, "kido_page_chosen");
            // Soul Society's arrival chunks asked for well before the pass and the tp, without blocking the server (a
            // forceload generates on its thread): one CI run sent the player into chunks not yet made, and it fell into
            // the void.
            case 6526 -> holdArrival(minecraft, true);
            // Back to the soul screen, through the page's own Back button.
            case 6530 -> {
                Button back = minecraft.screen instanceof KidoScreen page ? page.backButton() : null;
                expect(minecraft, back != null, "the Kidō page had no Back button");
                if (back != null) {
                    back.onPress();
                }
            }
            case 6550 -> minecraft.setScreen(null);
            // To Soul Society by Senkaimon: facing south again, handed a Jigokuchō, and full for the gate's cost.
            case 6560 -> {
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                command(minecraft, "give @s bleach:hell_butterfly");
                command(minecraft, "bleach reiryoku @s 800 800");
            }
            // Pressed about 6601, the blade turns for 30 ticks and the gate opens on the server about 6631.
            case 6600 -> {
                expect(minecraft, TravelLogic.turnsKey(player, player.getMainHandItem()),
                        "the Shinigami holds no blade of its own");
                GateKeys.OPEN.setDown(true);
            }
            // Let go well after the gate opens (a late server only lengthens the hold), which the server ignores.
            case 6652 -> GateKeys.OPEN.setDown(false);
            // Faded in by now, its doors folded back.
            case 6650 -> shot(minecraft, "senkaimon");
            // Into the gate: 21 ticks inside pass it, about 6677, to the court.
            case 6655 -> command(minecraft, "tp @s ~ ~ ~2");
            // Where it stands in the gate, whose block the gate home brings it back to.
            case 6660 -> returnTo = player.position();
            // Across the lawn, at y 72 for any seed, facing the south wall 40 blocks off.
            case 6810 -> command(minecraft, LAWN_TP);
            // The wall's chunks have had a hundred ticks or more to be drawn since they reached the client.
            case 6990 -> shot(minecraft, "seireiheki");
            // To the Academy in Seireitei, two blocks inside its gate, facing its hall.
            case 7000 -> command(minecraft, String.format(Locale.ROOT, "tp @s %.1f %d %.1f 90 0",
                    SeireiteiPlan.ACADEMY_LANDING_X, SeireiteiPlan.ACADEMY_FLOOR_Y, SeireiteiPlan.ACADEMY_LANDING_Z));
            case 7095 -> shot(minecraft, "academy");
            // Back to the lawn, where the gate home opens.
            case 7100 -> command(minecraft, LAWN_TP);
            case 7190 -> holdArrival(minecraft, false);
            // Home by a gate opened in Soul Society. The first gate's wait of 600 server ticks from about 6631 has run
            // by about 7231, 89 ticks before the press; the gate opens about 7351, two blocks south.
            case 7310 -> command(minecraft, "bleach reiryoku @s 800 800");
            case 7320 -> GateKeys.OPEN.setDown(true);
            case 7368 -> GateKeys.OPEN.setDown(false);
            // Into it: it passes about 7392, to the bottom centre of the block the first gate was taken from.
            case 7370 -> command(minecraft, "tp @s ~ ~ ~2");
            // Through the Dangai by a third gate, with no Jigokuchō to guide it: a floor and room for the gate 2 blocks
            // south, whatever the earlier beats left there. The wait from the gate at about 7351 runs to about 7951, and
            // creative never waits or pays. The last action-bar line is still the turn's in Soul Society; it is
            // forgotten, so the window from 7508 needs a new one.
            case 7500 -> {
                command(minecraft, "clear @s bleach:hell_butterfly");
                command(minecraft, "fill ~-2 ~-1 ~1 ~2 ~-1 ~3 stone");
                command(minecraft, "fill ~-2 ~ ~1 ~2 ~3 ~3 air");
                command(minecraft, "gamemode creative");
                actionBar = null;
            }
            // Pressed about 7506, the blade turns for 30 ticks and the gate opens about 7536, two blocks south.
            case 7505 -> {
                expect(minecraft, TravelLogic.turnsKey(player, player.getMainHandItem()),
                        "the Shinigami holds no blade of its own for the third gate");
                GateKeys.OPEN.setDown(true);
            }
            case 7557 -> GateKeys.OPEN.setDown(false);
            // Survival again before it steps in, so the pass and the whole crossing run by survival's rules.
            case 7560 -> command(minecraft, "gamemode survival");
            // Into it, unguided now: 21 ticks inside pass it, about 7584, into the Dangai at its lane's start.
            case 7562 -> {
                command(minecraft, "tp @s ~ ~ ~2");
                thirdGateTpAt = System.nanoTime();
                MinecraftServer server = minecraft.getSingleplayerServer();
                thirdGateTpTick = server == null ? -1 : server.getTickCount();
            }
            // Down the lane, south, between its walls of Kōryū.
            case 7615 -> shot(minecraft, "dangai");
            // The lane's end asked for well before the tp, as Soul Society's arrival chunks were.
            case 7620 -> holdLane(minecraft, Mth.floor(player.getX()) >> 4, true);
            // A look back: 12 blocks down the lane, facing its start, so the whole face of the Kōtotsu met on entering
            // is in view. It sets out about 7784, after the player is out, so it waits against the rock all this time.
            case 7622 -> command(minecraft, "tp @s ~ ~ 12 180 0");
            case 7630 -> shot(minecraft, "kototsu");
            // Facing south again, for the tp near the lane's end, which keeps the rotation.
            case 7635 -> command(minecraft, "tp @s ~ ~ ~ 0 0");
            // Near the lane's end, in the same lane and on the same floor.
            case 7640 -> command(minecraft, String.format(Locale.ROOT, "tp @s ~ ~ %.1f", LANE_END_Z));
            // The last 1.5 blocks take 9 ticks from rest at a walk, 8 with Hohō 10: it crosses z 160 about 7654 and comes
            // out over the Rukongai. Coming out lets go of the key anyway, as the loading screen releases every key.
            case 7645 -> minecraft.options.keyUp.setDown(true);
            case 7665 -> minecraft.options.keyUp.setDown(false);
            // Then, as a soul that has passed on, it dies where it stands and wakes at a Rukongai home drawn for it as
            // it dies. Immediate respawn needs no click; the inventory is kept so the screenshot is the soul's own.
            case 7880 -> {
                command(minecraft, "bleach soul @s rukongai");
                command(minecraft, "gamerule keepInventory true");
                command(minecraft, "gamerule doImmediateRespawn true");
            }
            case 7885 -> {
                killed = player;
                diedAt = player.position();
                command(minecraft, "kill @s");
            }
            case 8205 -> shot(minecraft, "rukongai_home");
            // Then Hueco Mundo's five blocks in a row 3 blocks south of where the soul woke, seen through its own eyes
            // looking a little down: facing south, white sand at the row's west end is on the right and the bricks at its
            // east end on the left. A home is drawn by chance, so a floor of stone and a room cleared from the block before
            // the soul make the view the same whatever stands there: a slope, a trunk or tall grass. The hand and the HUD
            // are hidden, as the F1 key hides them, so whatever the right hand holds does not cover the white sand.
            case 8210 -> {
                holdLane(minecraft, heldLane, false);
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.hideGui = true;
                command(minecraft, "tp @s ~ ~ ~ 0 15");
                command(minecraft, "fill ~-3 ~-1 ~1 ~3 ~-1 ~4 minecraft:stone");
                command(minecraft, "fill ~-3 ~ ~1 ~3 ~2 ~4 minecraft:air");
                List<Block> blocks = huecoMundoBlocks();
                for (int i = 0; i < blocks.size(); i++) {
                    command(minecraft, String.format(Locale.ROOT, "setblock ~%d ~ ~3 %s", i - 2,
                            BuiltInRegistries.BLOCK.getKey(blocks.get(i))));
                }
                blocksAt = player.blockPosition();
            }
            // The shot reads the frame already drawn, so the HUD can come back at once for whatever follows.
            case 8230 -> {
                shot(minecraft, "hueco_mundo_blocks");
                minecraft.options.hideGui = false;
            }
            // Seireitei's chunk where the Garganta is torn, Hueco Mundo's arrival chunk and the Overworld's chunk home
            // asked for 150 ticks before the tp, as Soul Society's arrival chunks were, so all are made or loaded in the
            // background first.
            case 8235 -> {
                holdHuecoMundo(minecraft, true);
                // Day 4, whose new moon stands full over Hueco Mundo.
                command(minecraft, "time set 97000");
            }
            // To Seireitei's plateau, facing south, on a floor of stone in a room cleared for the tear, as an Arrancar (a
            // person's size, no soul hunger, drawing no Hollow), full for the tear's cost. The Rukongai's place line
            // names Seireitei within a second, before the key goes down.
            case 8385 -> {
                command(minecraft, "fill -2 71 192 2 71 198 minecraft:stone");
                command(minecraft, "fill -2 72 192 2 76 198 minecraft:air");
                command(minecraft, String.format(Locale.ROOT, "execute in %s run tp @s %.1f %d %.1f 0 0",
                        BleachWorlds.SOUL_SOCIETY.location(), HUECO_MUNDO_X, SeireiteiPlan.PLATEAU_Y, TEAR_FROM_Z));
                command(minecraft, "bleach soul @s arrancar");
                command(minecraft, "bleach reiryoku @s 800 800");
            }
            // Pressed about 8431, the air is torn at for 20 ticks and the Garganta opens on the server about 8451, 1.8
            // blocks south, at (0.5, 72, 195.5).
            case 8430 -> GateKeys.OPEN.setDown(true);
            // Let go well after it opens, which the server ignores.
            case 8470 -> GateKeys.OPEN.setDown(false);
            // Into it: 11 ticks inside pass it, about 8486, into Hueco Mundo at the same x and z, beyond the radius a soul
            // is pushed out to past Las Noches' wall: onto Las Noches' level ground at y 96 for any seed, 4.5 blocks
            // inside its edge.
            case 8475 -> command(minecraft, String.format(Locale.ROOT, "tp @s %.1f %d %.1f", HUECO_MUNDO_X,
                    SeireiteiPlan.PLATEAU_Y, HUECO_MUNDO_Z));
            // Facing south over the slope to the dunes, whatever rotation the landing kept.
            case 8520 -> command(minecraft, "tp @s ~ ~ ~ 0 0");
            // The slope and the dunes beyond have had a hundred and eighty ticks or more to reach the client and be
            // drawn. The hand and the HUD are hidden for the shot, as in the blocks beat, so the held item covers none of
            // the plain.
            // "Into Hueco Mundo", granted on arrival about 8486, is the one advancement new since the soul became an
            // Arrancar. Its toast is long gone, but toasts are cleared anyway: hiding the GUI only stops them being drawn,
            // and one still queued would show again once the GUI returns.
            case 8665 -> {
                minecraft.getToasts().clear();
                minecraft.options.hideGui = true;
            }
            case 8670 -> {
                shot(minecraft, "hueco_mundo");
                minecraft.options.hideGui = false;
            }
            // Looking straight up from the player's own eyes at the moon overhead, full here as the Overworld's is new.
            // The hand and the HUD are hidden for the shot again, so the held item covers none of the sky.
            case 8675 -> command(minecraft, "tp @s ~ ~ ~ 0 -90");
            case 8685 -> minecraft.options.hideGui = true;
            case 8690 -> {
                shot(minecraft, "hueco_mundo_moon");
                minecraft.options.hideGui = false;
            }
            // The tear home: facing south again, in creative, as the first tear's wait still runs and creative never
            // waits or pays. The last action-bar line is forgotten, so the window from 8707 needs a new one.
            case 8695 -> {
                command(minecraft, "tp @s ~ ~ ~ 0 0");
                command(minecraft, "gamemode creative");
                actionBar = null;
            }
            // Pressed about 8706, the Garganta opens about 8726, 1.8 blocks south, at (0.5, 96, 197.3).
            case 8705 -> GateKeys.OPEN.setDown(true);
            case 8745 -> GateKeys.OPEN.setDown(false);
            // Six blocks back, still on the level ground, so the whole tear stands in view against the dunes. The hand
            // and the HUD are hidden for the shot, as in the blocks beat.
            case 8750 -> command(minecraft, String.format(Locale.ROOT, "tp @s ~ ~ ~-%.1f", RIFT_STEP_BACK));
            case 8755 -> minecraft.options.hideGui = true;
            case 8760 -> {
                shot(minecraft, "garganta_passage");
                minecraft.options.hideGui = false;
            }
            // Survival again before it steps in, so the pass runs by survival's rules.
            case 8762 -> command(minecraft, "gamemode survival");
            // Into it: 11 ticks inside pass it, about 8775, to the block the third gate was taken from.
            case 8764 -> command(minecraft, String.format(Locale.ROOT, "tp @s ~ ~ ~%.1f", RIFT_STEP_BACK + RIFT_AHEAD));
            case 8875 -> {
                holdHuecoMundo(minecraft, false);
                finished = true;
                BleachMod.LOGGER.info(PASSED);
                minecraft.stop();
            }
            default -> {
            }
        }
    }

    /** The checks of the Quincy in the world, each window from the step it follows. */
    private static void checkQuincyWorld(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        expectWithin(minecraft, 2535, 2560,
                () -> nearest(minecraft, QuincyElder.class, 8.0,
                        elder -> elder.isAlive() && !ClientSouls.isHiddenFromViewer(elder)) != null
                        && nearest(minecraft, QuincyHunter.class, 8.0,
                        hunter -> hunter.isAlive() && !ClientSouls.isHiddenFromViewer(hunter)) != null,
                "the Quincy NPCs never reached the client");
        expectWithin(minecraft, 2563, 2585, () -> minecraft.screen instanceof NpcDialogueScreen,
                "talking to the Elder did not open the dialogue");
        expectWithin(minecraft, 2587, 2610,
                () -> player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        && QuincyRansotengai.trialOpen(soul, minecraft.level.getGameTime()),
                "the Elder's trial did not begin");
        if (expectWithin(minecraft, 2613, 2635,
                () -> QuincyRansotengai.isWoven(soul) && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "Ransōtengai never wove")) {
            shot(minecraft, "ransotengai");
        }
        expectWithin(minecraft, 2712, 2770,
                () -> soul.quincy().teaching().ransotengaiKnown() && !QuincyRansotengai.isWoven(soul),
                "the trial never taught Ransōtengai");
        if (expectWithin(minecraft, 2781, 2800, () -> minecraft.screen instanceof NpcDialogueScreen,
                "talking to the Hunter did not open the dialogue")) {
            shot(minecraft, "hunter_dialogue");
            minecraft.setScreen(null);
        }
        if (expectWithin(minecraft, 2806, 2820,
                () -> player.getItemBySlot(EquipmentSlot.CHEST).is(BleachTags.QUINCY_GARB)
                        && player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.WANDENREICH_CAP.get()),
                "the player is not wearing the Wandenreich uniform")) {
            shot(minecraft, "wandenreich");
        }
        if (expectWithin(minecraft, 2827, 2850, () -> minecraft.screen instanceof TailoringScreen,
                "the Tailoring Table did not open again")
                && minecraft.screen instanceof TailoringScreen tailoring) {
            tailoring.nextPage();
        }
        expectWithin(minecraft, 2861, 2890, () -> soul.hunt().bait().crushed() >= 1, "the bait site never opened");
        expectWithin(minecraft, 2893, 2960, () -> openRift(minecraft) != null, "the bait's Garganta never opened");
        // The boss bar of the site shows in this one.
        if (expectWithin(minecraft, 2961, 3020, () -> count(minecraft, Hollow.class, 48.0) >= 1,
                "the bait's wave brought no Hollow")) {
            shot(minecraft, "bait_wave");
        }
        if (expectWithin(minecraft, 3026, 3080, () -> crackedSky(minecraft) != null, "the finale never cracked the sky")) {
            GargantaRift sky = crackedSky(minecraft);
            if (sky != null) {
                look(minecraft, sky.getBoundingBoxForCulling().getCenter());
            }
            shot(minecraft, "sky_cracked");
        }
        // A Gillian pushes through the crack and sinks to the ground.
        expectWithin(minecraft, 3030, 3084,
                () -> nearest(minecraft, Gillian.class, 64.0,
                        gillian -> gillian.isAlive() && !ClientSouls.isHiddenFromViewer(gillian)) != null,
                "the finale brought no Gillian down through the sky");
        expectWithin(minecraft, 3086, 3140,
                () -> nearest(minecraft, GargantaRift.class, 64.0, rift -> rift.size() >= 3.0F) == null,
                "the sky crack never closed");
        expectWithin(minecraft, 3146, 3170, () -> QuincyRansotengai.isWoven(soul), "the command did not weave Ransōtengai");
        if (expectWithin(minecraft, 3176, 3200,
                () -> !QuincyRansotengai.isWoven(soul) && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "letting Ransōtengai go did not bring the collapse")) {
            shot(minecraft, "ransotengai_collapse");
        }
    }

    /** The checks of the Hollow player, each window from the step it follows. */
    private static void checkHollowPlayer(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        if (expectWithin(minecraft, 3250, 3300,
                () -> soul.state() == SoulState.HOLLOW && soul.hollow().hungerStarted() && armourShed(player)
                        && player.hasEffect(MobEffects.NIGHT_VISION) && player.getFoodData().getFoodLevel() == 20
                        // Peaceful filled the saturation earlier in the run: only the pin empties it.
                        && player.getFoodData().getSaturationLevel() == 0.0F,
                "the Hollow body never came on: soul hunger, the uniform shed, Hollow sight and a held food bar")) {
            shot(minecraft, "hollow_player");
        }
        if (expectWithin(minecraft, 3327, 3345,
                () -> (ClientSouls.appearance(player).hollowLook() & HollowPlayerLogic.LOOK_DEVOURING) != 0,
                "holding Devour on the Plus never opened the mask's jaw")) {
            shot(minecraft, "devour");
        }
        // The Plus's health reaches the client at once; its body goes 20 ticks later, which this does not wait for.
        expectWithin(minecraft, 3346, 3380,
                () -> subject instanceof PlusSpirit plus && (!plus.isAlive() || plus.isRemoved())
                        && soul.hollow().pluses() >= 1,
                "holding Devour did not devour the Plus");
        expectWithin(minecraft, 3389, 3410,
                () -> !soul.hollow().sight() && !player.hasEffect(MobEffects.NIGHT_VISION),
                "Sneak and Devour did not turn the Hollow sight off");
    }

    /** The checks of the Menos Grande, each window from the step it follows. */
    private static void checkMenos(Minecraft minecraft) {
        if (expectWithin(minecraft, 3438, 3480, () -> {
            Gillian gillian = nearest(minecraft, Gillian.class, 32.0,
                    g -> g.isAlive() && !ClientSouls.isHiddenFromViewer(g));
            Adjuchas adjuchas = nearest(minecraft, Adjuchas.class, 32.0,
                    a -> a.isAlive() && !ClientSouls.isHiddenFromViewer(a));
            return gillian != null && adjuchas != null && gillian.getParts().length == 2
                    && minecraft.level.getPartEntities().containsAll(Arrays.asList(gillian.getParts()))
                    && gillian.head().getBoundingBox().inflate(0.01).contains(HollowMask.maskCenter(gillian));
        }, "the summoned Menos never showed, or the Gillian's parts were not on its body")) {
            Gillian gillian = nearest(minecraft, Gillian.class, 32.0, Gillian::isAlive);
            if (gillian != null) {
                // The whole Gillian, and the Adjuchas below.
                look(minecraft, gillian.position().add(0.0, 6.0, 0.0));
            }
        }
        if (expectWithin(minecraft, 3496, 3530,
                () -> nearest(minecraft, CeroBeam.class, 16.0,
                        beam -> beam.isFired() && beam.reach().length() > 29.0) != null,
                "the summoned Cero never drew")) {
            shot(minecraft, "cero");
        }
    }

    /** The checks of the player's own Menos forms, each window from the step it follows. */
    private static void checkMenosPlayer(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        if (expectWithin(minecraft, 3550, 3680,
                () -> soul.tier(PowerPath.HOLLOW) == 2 && player.getScale() > 5.99F
                        && hasLook(player, HollowPlayerLogic.LOOK_GILLIAN),
                "the player never grew into a Gillian")) {
            shot(minecraft, "gillian_player");
        }
        if (expectWithin(minecraft, 3692, 3712,
                () -> ownCero(minecraft, player, CeroBeam::isCharging) != null && HollowKeys.ceroProgress(0.0F) > 0.0F,
                "holding the Cero key never gathered a Cero")) {
            shot(minecraft, "cero_player_charge");
        }
        if (expectWithin(minecraft, 3726, 3740, () -> ownCero(minecraft, player, CeroBeam::isFired) != null,
                "letting the Cero key go never fired the Cero")) {
            shot(minecraft, "cero_player");
        }
        if (expectWithin(minecraft, 3747, 3770,
                () -> soul.tier(PowerPath.HOLLOW) == 3 && Math.abs(player.getScale() - 1.5F) < 0.01F
                        && hasLook(player, HollowPlayerLogic.LOOK_ADJUCHAS),
                "the player never became an Adjuchas")) {
            shot(minecraft, "adjuchas_player");
        }
        if (expectWithin(minecraft, 3777, 3795,
                () -> soul.tier(PowerPath.HOLLOW) == 4 && Math.abs(player.getScale() - 1.0F) < 0.01F
                        && hasLook(player, HollowPlayerLogic.LOOK_VASTO_LORDE),
                "the player never became a Vasto Lorde")) {
            shot(minecraft, "vasto_lorde_player");
        }
    }

    /** The checks of the Arrancar, its Resurrección and its arts, each window from the step it follows. */
    private static void checkArrancar(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        if (expectWithin(minecraft, 3802, 3830,
                () -> soul.state() == SoulState.ARRANCAR && soul.tier(PowerPath.HOLLOW) == 4
                        && ArrancarLogic.isOwnBlade(player.getMainHandItem(), player)
                        && !hasLook(player, HollowPlayerLogic.LOOK_RESURRECCION)
                        && Math.abs(player.getScale() - 1.0F) < 0.01F
                        && soul.hollow().arrancar().rank().seat() == 4
                        && ArrancarRank.lookNumber(ClientSouls.appearance(player).hollowLook()) == 4,
                "the Vasto Lorde never became an Arrancar with its own blade in hand, seated as the Cuatro Espada")) {
            shot(minecraft, "arrancar");
        }
        if (expectWithin(minecraft, 3842, 3870,
                () -> hasLook(player, HollowPlayerLogic.LOOK_RESURRECCION)
                        && hasLook(player, HollowPlayerLogic.LOOK_VASTO_LORDE)
                        && player.getMaxHealth() > 79.9F,
                "the Release key never called out the Resurrección")) {
            shot(minecraft, "resurreccion");
        }
        expectWithin(minecraft, 3877, 3895,
                () -> !hasLook(player, HollowPlayerLogic.LOOK_RESURRECCION)
                        && soul.hollow().arrancar().releaseReadyAt() > player.level().getGameTime(),
                "the Seal key never sealed the Resurrección, or its wait never reached the client");
        expectWithin(minecraft, 3904, 3915,
                () -> horizontalDistance(player.position(), dashFrom) >= SONIDO_MIN_DISTANCE,
                "the Arrancar's Sonído never went further than a first Shunpo");
        if (expectWithin(minecraft, 3947, 3970,
                () -> nearest(minecraft, PlusSpirit.class, 12.0, HollowKeys::marked) != null,
                "Pesquisa never marked the Plus behind the wall")) {
            shot(minecraft, "pesquisa");
        }
        if (expectWithin(minecraft, 3996, 4015,
                () -> ownCero(minecraft, player, beam -> beam.radius() > 1.9F
                        && beam.colour() == ArrancarLogic.blade(player.getUUID()).colour()) != null,
                "the sneaking Cero key never gathered a Gran Rey Cero in the blade's colour")) {
            shot(minecraft, "gran_rey_cero");
        }
        expectWithin(minecraft, 4012, 4035,
                () -> soul.hollow().arrancar().granReyCeroReadyAt() > player.level().getGameTime()
                        && soul.reiryoku() < 1300.0F,
                "the Gran Rey Cero never fired and was paid for");
        expectWithin(minecraft, 4043, 4050, () -> player.getVehicle() instanceof NegacionLight,
                "the player never stood in the Negación light");
        if (expectWithin(minecraft, 4060, 4092,
                () -> negacionFrom != null && player.getY() - negacionFrom.y >= NEGACION_MIN_RISE,
                "the Negación light never lifted the player")) {
            shot(minecraft, "negacion");
        }
        expectWithin(minecraft, 4097, 4115,
                () -> !player.isPassenger() && player.hasEffect(MobEffects.SLOW_FALLING),
                "sneaking never left the Negación light, or left it without Slow Falling");
        if (expectWithin(minecraft, 4122, 4140, () -> ArrancarUniformItem.isFullSet(player),
                "the Arrancar uniform was never worn")) {
            shot(minecraft, "arrancar_uniform");
        }
        if (expectWithin(minecraft, 4152, 4175,
                () -> seenNpc(minecraft, npc -> npc.tattooNumber() == 3
                        && ArrancarLogic.isArrancarBlade(npc.getMainHandItem())
                        && npc.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.ARRANCAR_HAKAMA.get())
                        && npc.getItemBySlot(EquipmentSlot.CHEST).isEmpty())
                        && seenNpc(minecraft, npc -> npc.tattooNumber() == 0
                        && npc.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.ARRANCAR_JACKET.get())),
                "the Arrancar NPCs never stood dressed and armed with the Privaron's 3")) {
            shot(minecraft, "arrancar_npc");
        }
        if (expectWithin(minecraft, 4188, 4200,
                () -> nearest(minecraft, CajaNegacion.class, 8.0,
                        caja -> caja.phase() == CajaNegacion.Phase.CLOSING && !caja.getPassengers().isEmpty()) != null,
                "the Caja Negación never closed on the Número")) {
            shot(minecraft, "caja_negacion");
        }
        expectWithin(minecraft, 4204, 4220,
                () -> nearest(minecraft, ArrancarNpc.class, 8.0,
                        npc -> npc.getVehicle() instanceof CajaNegacion caja && caja.phase() == CajaNegacion.Phase.HOLDING
                                && ClientSouls.isHiddenFromViewer(npc)) != null,
                "the Número was never hidden in the Caja Negación");
        expectWithin(minecraft, 4224, 4235, () -> seenNpc(minecraft, npc -> npc.tattooNumber() == 0 && !npc.isPassenger()),
                "the Número never came back out of the Caja Negación");
        if (expectWithin(minecraft, 4240, 4265,
                () -> player.getVehicle() instanceof CajaNegacion caja && caja.phase() == CajaNegacion.Phase.HOLDING,
                "the player was never held in the Caja Negación")) {
            shot(minecraft, "caja_inside");
        }
        expectWithin(minecraft, 4283, 4295, () -> !player.isPassenger() && player.hasEffect(MobEffects.SLOW_FALLING),
                "the Caja Negación never let the player go, or let it go without Slow Falling");
    }

    /** The checks of the inner Hollow, each window from the step it follows. */
    private static void checkVisored(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        if (expectWithin(minecraft, 4306, 4330,
                () -> nearest(minecraft, InnerHollow.class, 12.0, hollow -> hollow.isAlive()
                        && !ClientSouls.isHiddenFromViewer(hollow) && !hollow.getMainHandItem().isEmpty()) != null
                        && soul.shinigami().visored().rooted(),
                "the inner Hollow never came out beside the player with its copied blade, or its root never reached"
                        + " the client")) {
            shot(minecraft, "inner_hollow");
        }
        expectWithin(minecraft, 4316, 4340,
                () -> soul.shinigami().visored().subdued()
                        && nearest(minecraft, InnerHollow.class, 12.0, hollow -> true) == null,
                "the beaten inner Hollow never went, or the win never reached the client");
    }

    /** The checks of the Visored's Hollow mask, each window from the step it follows. */
    private static void checkVisoredMask(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        if (expectWithin(minecraft, 4346, 4365,
                () -> soul.shinigami().visored().masked() && hasLook(player, HollowPlayerLogic.LOOK_MASK),
                "the Hollow mask put on by command never reached the client, or its look never did")) {
            shot(minecraft, "visored_mask");
        }
        if (expectWithin(minecraft, 4372, 4385, () -> soul.shinigami().visored().masked(),
                "the Hollow mask came off before the Hollow Mask key was pressed")) {
            shot(minecraft, "visored_mask_edge");
        }
        expectWithin(minecraft, 4393, 4415,
                () -> !soul.shinigami().visored().masked() && !hasLook(player, HollowPlayerLogic.LOOK_MASK)
                        && soul.shinigami().visored().maskReadyAt() > player.level().getGameTime(),
                "the Hollow Mask key never took the mask off, or its wait never reached the client");
    }

    /** The checks of the masked Visored's arts, each window from the step it follows. */
    private static void checkVisoredArts(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        expectWithin(minecraft, 4452, 4464, () -> ZanpakutoItem.blade(player.getMainHandItem()).released(),
                "Zangetsu was not released again for the Visored");
        expectWithin(minecraft, 4466, 4474,
                () -> soul.shinigami().visored().masked()
                        && soul.shinigami().visored().maskTrainedTicks() >= VISORED_TRAINED_TICKS,
                "the Hollow mask put on again by command, or the masks' training, never reached the client");
        if (expectWithin(minecraft, 4477, 4500,
                () -> ownCero(minecraft, player,
                        beam -> beam.isCharging() && beam.colour() == HollowCero.VISORED_CRIMSON) != null
                        && HollowKeys.ceroProgress(0.0F) > 0.0F,
                "holding the Cero key with the mask on never gathered a crimson Cero")) {
            shot(minecraft, "visored_cero_charge");
        }
        if (expectWithin(minecraft, 4511, 4530,
                () -> ownCero(minecraft, player, beam -> beam.isFired() && beam.colour() == HollowCero.VISORED_CRIMSON
                        && beam.radius() > 1.9F) != null,
                "letting the Cero key go never fired the masked Visored's wide crimson Cero")) {
            shot(minecraft, "visored_cero");
        }
        // Once it has faded in, so the shot shows it whole.
        if (expectWithin(minecraft, 4561, 4580,
                () -> nearest(minecraft, GetsugaTensho.class, 48.0, crescent -> crescent.isBlack() && crescent.age() >= 2)
                        != null,
                "the masked Visored's Getsuga Tenshō never reached the client as the black one")) {
            shot(minecraft, "visored_getsuga");
        }
    }

    /** The checks of the Hollow's hold, each window from the step it follows. */
    private static void checkVisoredTakeover(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        if (expectWithin(minecraft, 4612, 4640,
                () -> hasLook(player, HollowPlayerLogic.LOOK_TAKEN_OVER) && !hasLook(player, HollowPlayerLogic.LOOK_MASK)
                        && soul.shinigami().visored().rooted() && !soul.shinigami().visored().subdued(),
                "the Hollow's hold by command never reached the client as the half mask of a Hollow not yet beaten")) {
            shot(minecraft, "visored_half_mask");
        }
        // With no mask on, the edge drawn is the left half the half mask gives.
        if (expectWithin(minecraft, 4650, 4665,
                () -> hasLook(player, HollowPlayerLogic.LOOK_TAKEN_OVER) && !soul.shinigami().visored().masked(),
                "the Hollow let go of the body before the Hollow Mask key was pressed")) {
            shot(minecraft, "visored_half_mask_edge");
        }
        // The weariness comes only with a tear-off or a run-out, and the run-out is still seconds away.
        expectWithin(minecraft, 4673, 4690,
                () -> !hasLook(player, HollowPlayerLogic.LOOK_TAKEN_OVER) && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "the Hollow Mask key never tore the half mask off, or the weariness after it never reached the client");
    }

    /**
     * The checks of Kidō, each window from the step it follows. Two checks after one step start a tick apart, since a
     * window is known by its first tick.
     */
    private static void checkKido(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        expectWithin(minecraft, 4713, 4730, () -> soul.level(Stat.KIDO) == 10 && soul.xp(Stat.KIDO) >= 1,
                "the Kidō key never cast Shō (the action bar last read: " + lastActionBar() + ")");
        // Shō does about 3 of its 20; a villager is no spirit, so anyone sees it hurt.
        expectWithin(minecraft, 4714, 4730,
                () -> nearest(minecraft, Villager.class, 8.0, villager -> villager.getHealth() < villager.getMaxHealth())
                        != null,
                "Shō never struck the villager");
        if (expectWithin(minecraft, 4741, 4757,
                () -> chosenIs(soul, KidoSpell.BYAKURAI) && chosenShown(minecraft, KidoSpell.BYAKURAI),
                "Sneak and the Kidō key never chose Byakurai, or the action bar never named it")) {
            shot(minecraft, "kido_chosen");
        }
        // The colour is the client's own copy of the spell's, so this also proves the spells reached it. Once the trace
        // has been drawn, so the shot shows it.
        if (expectWithin(minecraft, 4761, 4780, () -> ownTrace(minecraft, player, KidoSpell.BYAKURAI) != null,
                "the Kidō key never cast Byakurai, or its trace never reached the client in the synced spell's colour"
                        + " (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_byakurai");
        }
        // Shō gave 2 with its strike, and Byakurai gives at least 2 more. Its 32 reiryoku come back too fast to check.
        expectWithin(minecraft, 4762, 4780, () -> soul.xp(Stat.KIDO) >= 4, "Byakurai never trained Kidō");
        // Choosing a spell with an incantation prints it in the player's own chat, as the client's copy of it reads.
        expectWithin(minecraft, 4776, 4789,
                () -> chosenIs(soul, KidoSpell.SHAKKAHO) && chatShows(incantation(minecraft, KidoSpell.SHAKKAHO, 0)),
                "Sneak and the Kidō key never chose Shakkahō, or its incantation never reached the chat");
        // The chant sent at 4790 takes 30 ticks to say, so a trace this soon would be a cast that never waited for it.
        if (ticksInWorld >= 4791 && ticksInWorld <= 4815) {
            int crimson = colourOf(minecraft, KidoSpell.SHAKKAHO);
            expect(minecraft, ownCero(minecraft, player, beam -> beam.colour() == crimson) == null,
                    "Shakkahō went off before its chant was done");
        }
        // The line is kept from the chat, and the server speaks it instead, to its caster too, as a system line.
        expectWithin(minecraft, 4791, 4815, () -> spokenBack(player, incantation(minecraft, KidoSpell.SHAKKAHO, 0)),
                "the chant was never spoken back to its caster");
        if (expectWithin(minecraft, 4821, 4860, () -> ownTrace(minecraft, player, KidoSpell.SHAKKAHO) != null,
                "the chant of Shakkahō never cast it, or its crimson trace never reached the client"
                        + " (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_shakkaho");
        }
        // Shō and Byakurai left at least 4, a chant of Shakkahō gives 6, or 8 with its strike, and a cast by name at most
        // 4. Kidō 11 is 420 xp from 10, so nothing rolls over into a level and the count only grows.
        expectWithin(minecraft, 4822, 4860, () -> soul.xp(Stat.KIDO) >= 10,
                "the chant of Shakkahō never trained Kidō twice over");
        if (expectWithin(minecraft, 4921, 4955, () -> ownTrace(minecraft, player, KidoSpell.SOKATSUI) != null,
                "the English chant of Sōkatsui never cast it, or its blue trace never reached the client"
                        + " (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_sokatsui");
        }
        // Kidō 18 teaches Tenran, the spell after Sōkatsui.
        expectWithin(minecraft, 4977, 4989, () -> chosenIs(soul, KidoSpell.TENRAN),
                "Sneak and the Kidō key never chose Tenran");
        // The gust's pale streak along its middle is the one part of it a client can be asked about.
        if (expectWithin(minecraft, 4991, 5010, () -> ownTrace(minecraft, player, KidoSpell.TENRAN) != null,
                "the Kidō key never cast Tenran, or its pale trace never reached the client"
                        + " (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_tenran");
        }
        // About 3.6 of its 20 bare-handed, 5.1 with a zanpakutō at hand. A villager with no free will is never seen pushed.
        expectWithin(minecraft, 4992, 5010,
                () -> nearest(minecraft, Villager.class, 8.0,
                        villager -> villager.isAlive() && villager.getHealth() < villager.getMaxHealth()) != null,
                "Tenran never struck the villager");
        expectWithin(minecraft, 5030, 5049, () -> chosenIs(soul, KidoSpell.RAIKOHO),
                "Sneak and the Kidō key never chose Raikōhō");
        // The key held from 5060 gathers it for a second first, so a yellow bolt this soon never waited for the gather.
        if (ticksInWorld >= 5061 && ticksInWorld <= 5075) {
            int yellow = colourOf(minecraft, KidoSpell.RAIKOHO);
            expect(minecraft, ownCero(minecraft, player, beam -> beam.isFired() && beam.colour() == yellow) == null,
                    "Raikōhō went off before it had gathered");
        }
        // The orb is a charging Cero in the spell's colour at the palm, which the camera behind the player sees.
        if (expectWithin(minecraft, 5062, 5075,
                () -> ownCero(minecraft, player,
                        beam -> beam.isCharging() && beam.colour() == colourOf(minecraft, KidoSpell.RAIKOHO)) != null,
                "holding the Kidō key never gathered Raikōhō's orb, or it never reached the client")) {
            shot(minecraft, "kido_raikoho_gather");
        }
        // Shut before the let-go at 5106, and only a bolt longer than the orb's own harmless 1-block shot, which the orb
        // fires if nothing looses the gather: so only the gather going off by itself can pass it.
        if (expectWithin(minecraft, 5076, 5105, () -> ownCero(minecraft, player, beam -> beam.isFired()
                        && beam.colour() == colourOf(minecraft, KidoSpell.RAIKOHO) && beam.firedAge(0.0F) >= 1.0F
                        && beam.reach().length() > 2.0) != null,
                "Raikōhō never went off once gathered, or its yellow trace never reached the client"
                        + " (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_raikoho");
        }
        expectWithin(minecraft, 5138, 5150, () -> chosenIs(soul, KidoSpell.SAI), "Sneak and the Kidō key never chose Sai");
        // A mob's effects never reach a client, so the trace is what a client can be asked about a bind: one that met the
        // villager four blocks south runs about 3 blocks from the hand, and a miss runs on to the ray's end, about 7½
        // blocks for Sai and 11½ for Hainawa.
        if (expectWithin(minecraft, 5191, 5210, () -> ownBind(minecraft, player, KidoSpell.SAI, 2.0, 5.0) != null,
                "the Kidō key never cast Sai at the villager, or its trace never reached the client or did not stop at"
                        + " the villager (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_sai");
        }
        expectWithin(minecraft, 5210, 5222, () -> chosenIs(soul, KidoSpell.HAINAWA),
                "Sneak and the Kidō key never chose Hainawa");
        if (expectWithin(minecraft, 5229, 5250, () -> ownBind(minecraft, player, KidoSpell.HAINAWA, 2.0, 5.0) != null,
                "the Kidō key never cast Hainawa at the villager, or its trace never reached the client or did not stop"
                        + " at the villager (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_hainawa");
        }
        expectWithin(minecraft, 5278, 5289,
                () -> chosenIs(soul, KidoSpell.GEKI) && chatShows(incantation(minecraft, KidoSpell.GEKI, 0)),
                "Sneak and the Kidō key never chose Geki after Hainawa, or its incantation never reached the chat");
        // The chant sent at 5290 takes 30 ticks to say, so a trace this soon would be a cast that never waited for it.
        if (ticksInWorld >= 5291 && ticksInWorld <= 5315) {
            int red = colourOf(minecraft, KidoSpell.GEKI);
            expect(minecraft, ownCero(minecraft, player, beam -> beam.colour() == red) == null,
                    "Geki went off before its chant was done");
        }
        expectWithin(minecraft, 5291, 5315, () -> spokenBack(player, incantation(minecraft, KidoSpell.GEKI, 0)),
                "the chant of Geki was never spoken back to its caster");
        // A bind that holds as well as binds shows the client nothing more than one that binds: its trace stops at the
        // villager, about 3 blocks from the hand, where a miss runs on to about 9½ blocks for Geki and 15½ for Rikujōkōrō.
        if (expectWithin(minecraft, 5320, 5360, () -> ownBind(minecraft, player, KidoSpell.GEKI, 2.0, 5.0) != null,
                "the chant of Geki never cast it, or its red trace never reached the client or did not stop at the"
                        + " villager (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_geki");
        }
        // Sekienton after Geki: so the order of the Bakudō holds on a real client too. With no incantation, nothing is
        // printed, so the action bar is what names it.
        expectWithin(minecraft, 5343, 5352,
                () -> chosenIs(soul, KidoSpell.SEKIENTON) && chosenShown(minecraft, KidoSpell.SEKIENTON),
                "Sneak and the Kidō key never chose Sekienton after Geki, or the action bar never named it");
        // The chant sent at 5355 goes off about 5396, so a trace this soon would be a cast that never waited for it.
        if (ticksInWorld >= 5356 && ticksInWorld <= 5390) {
            int pale = colourOf(minecraft, KidoSpell.RIKUJOKORO);
            expect(minecraft, ownCero(minecraft, player, beam -> beam.colour() == pale) == null,
                    "Rikujōkōrō went off before its chant was done");
        }
        expectWithin(minecraft, 5356, 5380, () -> spokenBack(player, incantation(minecraft, KidoSpell.RIKUJOKORO, 0)),
                "the chant of Rikujōkōrō was never spoken back to its caster");
        if (expectWithin(minecraft, 5395, 5440, () -> ownBind(minecraft, player, KidoSpell.RIKUJOKORO, 2.0, 5.0) != null,
                "the chant of Rikujōkōrō never cast it, or its pale yellow trace never reached the client or did not stop"
                        + " at the villager (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_rikujokoro");
        }
        // The smoke itself is seen only by those who see spirits, and what it does to a mob never reaches a client; the
        // caster's own effects do, and the call.
        if (expectWithin(minecraft, 5533, 5560, () -> {
            MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
            return speed != null && speed.getAmplifier() == 1 && !player.hasEffect(MobEffects.BLINDNESS)
                    && callShown(minecraft, KidoSpell.SEKIENTON);
        }, "the Kidō key never raised Sekienton, or its Speed II never reached the client, or it blinded its own caster"
                + " (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_sekienton");
        }
        expectWithin(minecraft, 5578, 5595,
                () -> chosenIs(soul, KidoSpell.ENKOSEN) && chosenShown(minecraft, KidoSpell.ENKOSEN),
                "Sneak and the Kidō key never chose Enkōsen after Sekienton, or the action bar never named it");
        // The ring before the eyes is drawn for those who see spirits only, so the call is what a client can be asked.
        if (expectWithin(minecraft, 5748, 5759, () -> callShown(minecraft, KidoSpell.ENKOSEN),
                "the Kidō key never raised Enkōsen (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_enkosen");
        }
        // A hurt refused fails the command, whose failure comes to the player as a system line in the chat.
        expectWithin(minecraft, 5762, 5775,
                () -> chatShows(Component.translatable("commands.damage.invulnerable").getString())
                        && player.getHealth() + player.getAbsorptionAmount() >= healthBefore,
                "Enkōsen never took a blow of 4 from two blocks ahead, or the blow landed");
        expectWithin(minecraft, 5777, 5795,
                () -> player.getHealth() + player.getAbsorptionAmount() < healthBefore - 0.5F,
                "a blow of 4 from two blocks behind never landed past Enkōsen");
        // Rikujōkōrō and then Dankū after Enkōsen: so the order of the Bakudō holds on a real client to the last.
        expectWithin(minecraft, 5818, 5827, () -> chosenIs(soul, KidoSpell.RIKUJOKORO),
                "Sneak and the Kidō key never chose Rikujōkōrō after Enkōsen");
        expectWithin(minecraft, 5838, 5855,
                () -> chosenIs(soul, KidoSpell.DANKU) && chosenShown(minecraft, KidoSpell.DANKU),
                "Sneak and the Kidō key never chose Dankū after Rikujōkōrō, or the action bar never named it");
        // The wall is drawn for those who see spirits only, from its synced facing and size, which shape its box too.
        if (expectWithin(minecraft, 5923, 5940, () -> callShown(minecraft, KidoSpell.DANKU)
                        && nearest(minecraft, DankuWall.class, 6.0, wall -> standsAhead(player, wall)) != null,
                "the Kidō key never raised Dankū, or its wall never reached the client 2 blocks south of the player"
                        + " (the action bar last read: " + lastActionBar() + ")")) {
            shot(minecraft, "kido_danku");
        }
        expectWithin(minecraft, 6045, 6070, () -> count(minecraft, DankuWall.class, 8.0) == 0,
                "Dankū's wall still stood long after its 6 seconds");
        // Kaidō after Dankū: so it comes last of all on a real client too, named alone.
        expectWithin(minecraft, 6098, 6125,
                () -> chosenIs(soul, KidoSpell.KAIDO) && chosenShown(minecraft, KidoSpell.KAIDO),
                "Sneak and the Kidō key never chose Kaidō after Dankū, or the action bar never named it");
        expectWithin(minecraft, 6112, 6140,
                () -> nearest(minecraft, Villager.class, 4.0,
                        villager -> villager.isAlive() && villager.getHealth() <= 10.5F) != null,
                "the villager two blocks south never took a blow of 10");
        // Health is sent to every client, so the villager's rise is what a client can be asked; the motes are the shot's.
        if (expectWithin(minecraft, 6243, 6270, () -> healShown(minecraft, false)
                        && nearest(minecraft, Villager.class, 4.0, villager -> villager.getHealth() >= 13.5F) != null,
                "the Kidō key held never began Kaidō on the villager, or never healed it (the villager's health: "
                        + villagerHealth(minecraft) + "; the action bar's last lines: " + recentActionBar()
                        + (ticksInWorld == 6270 ? "; on the server: " + serverHeal(minecraft) : "") + ")")) {
            shot(minecraft, "kido_kaido");
        }
        expectWithin(minecraft, 6285, 6315,
                () -> nearest(minecraft, Villager.class, 4.0, villager -> villager.getHealth() >= 19.5F) != null,
                "three seconds of Kaidō never healed the villager whole");
        expectWithin(minecraft, 6327, 6345, () -> player.getHealth() <= player.getMaxHealth() - 6.0F,
                "a blow of 8 with no attacker never landed on the player");
        expectWithin(minecraft, 6353, 6380, () -> healShown(minecraft, true) && player.getHealth() >= healthBefore + 1.5F,
                "with no one in reach, the Kidō key held never healed the player's own wounds (the action bar's last"
                        + " lines: " + recentActionBar()
                        + (ticksInWorld == 6380 ? "; on the server: " + serverHeal(minecraft) : "") + ")");
        expectWithin(minecraft, 6392, 6420, () -> player.getHealth() >= healthBefore + 5.5F,
                "three seconds of Kaidō never healed the player's own wounds by three hearts");
        // The Kidō page, once drawn: all 14 spells, Shō first and Kaidō last, and Kaidō, chosen, shows its heal.
        if (expectWithin(minecraft, 6463, 6480, () -> minecraft.screen instanceof KidoScreen page
                        && page.listed().size() == 14 && page.listed().get(0).is(KidoSpell.SHO)
                        && page.listed().get(13).is(KidoSpell.KAIDO) && chosenIs(soul, KidoSpell.KAIDO),
                "the soul screen's Kidō button never opened a page of 14 spells with Kaidō chosen")) {
            shot(minecraft, "kido_page");
        }
        // The click reached the server, which chose Sōkatsui and said so as Sneak + U would, and the choice came back.
        expectWithin(minecraft, 6493, 6520, () -> minecraft.screen instanceof KidoScreen
                        && chosenIs(soul, KidoSpell.SOKATSUI) && chosenShown(minecraft, KidoSpell.SOKATSUI)
                        && chatShows(incantation(minecraft, KidoSpell.SOKATSUI, 0))
                        && serverChosen(minecraft, KidoSpell.SOKATSUI),
                "a click on Sōkatsui's row never chose it (the action bar's last lines: " + recentActionBar() + ")");
        // Back on the page went back to the soul screen, which built its buttons again, the Kidō button among them.
        expectWithin(minecraft, 6531, 6548, () -> minecraft.screen instanceof SoulStatsScreen stats
                && stats.kidoButton() != null, "the Kidō page's Back button never went back to the soul screen");
    }

    /**
     * The checks of Soul Society, each window from the step it follows: the blade turned and the gate open, the court,
     * the lawn and the wall seen, the Academy in Seireitei and the lawn again, and home again by a gate from there.
     */
    private static void checkSoulSociety(Minecraft minecraft, LocalPlayer player) {
        Component turning = Component.translatable("message.bleach.senkaimon.turning");
        expectWithin(minecraft, 6603, 6628, () -> actionBarShows(turning),
                "holding the Gate key never turned the blade (the action bar's last lines: " + recentActionBar() + ")");
        // From a little before the turn is done to the tick before the tp into the gate.
        expectWithin(minecraft, 6630, 6654, () -> gateAhead(minecraft, player),
                "the turned blade never opened a Senkaimon two blocks south of the player, facing it, 3 wide and 4 tall"
                        + " (the action bar's last lines: " + recentActionBar() + ")");
        expectWithin(minecraft, 6665, COURT_BY_TICK, () -> atCourt(minecraft, player),
                "the Senkaimon never brought the player to its court on Seireitei's lawn at y 72 (it is in "
                        + minecraft.level.dimension().location() + " at " + player.blockPosition().toShortString()
                        + ", its chunk " + (minecraft.level.hasChunkAt(player.blockPosition()) ? "loaded" : "not loaded")
                        + ", the court's block " + minecraft.level.getBlockState(BlockPos.containing(TravelLogic.COURT)
                                .below()) + "; the action bar's last lines: " + recentActionBar() + ")");
        expectWithin(minecraft, 6812, 6890, () -> onLawn(minecraft, player),
                "the tp never stood the player on Seireitei's lawn at y 72 (it is in "
                        + minecraft.level.dimension().location() + " at " + player.blockPosition().toShortString()
                        + ", its chunk " + (minecraft.level.hasChunkAt(player.blockPosition()) ? "loaded" : "not loaded")
                        + ", the lawn's block " + minecraft.level.getBlockState(BlockPos.containing(SOUL_SOCIETY_X,
                                SeireiteiPlan.PLATEAU_Y - 1, SOUL_SOCIETY_Z)) + ")");
        expectWithin(minecraft, 6850, WALL_BY_TICK, () -> wallStands(minecraft.level),
                "the Seireiheki never reached the client as the plan lays it: Sekkiseki to y 111 by a gate house, its"
                        + " great gate down by its wicket (the client holds " + wallSeen(minecraft.level) + ")");
        expectWithin(minecraft, 7010, 7090, () -> minecraft.level.dimension() == BleachWorlds.SOUL_SOCIETY
                        && player.onGround() && Math.abs(player.getY() - SeireiteiPlan.ACADEMY_FLOOR_Y) < 0.01
                        && academyStands(minecraft.level) && !instructorsNear(minecraft, 32.0).isEmpty(),
                "the Academy never stood in Seireitei with its instructor (the player is in "
                        + minecraft.level.dimension().location() + " at " + player.blockPosition().toShortString() + ")");
        expectWithin(minecraft, 7105, 7180, () -> onLawn(minecraft, player),
                "the tp never stood the player on the lawn again before the gate home");
        // The cooldown line shows here instead if the first gate's wait had not run.
        expectWithin(minecraft, 7323, 7348, () -> actionBarShows(turning),
                "in Soul Society, holding the Gate key never turned the blade (the action bar's last lines: "
                        + recentActionBar() + ")");
        expectWithin(minecraft, 7350, 7369, () -> gateAhead(minecraft, player),
                "in Soul Society, the turned blade never opened a Senkaimon two blocks south of the player (the action"
                        + " bar's last lines: " + recentActionBar() + ")");
        // Home to the bottom centre of the block it left from, at most 0.71 across from where it stood.
        expectWithin(minecraft, 7380, 7480, () -> minecraft.level.dimension() == Level.OVERWORLD && player.onGround()
                        && horizontalDistance(player.position(), returnTo) < 1.0
                        && Math.abs(player.getY() - returnTo.y) < 0.01,
                "the Senkaimon from Soul Society never brought the player home (it is in "
                        + minecraft.level.dimension().location() + " at " + player.position() + ", not by " + returnTo
                        + "; the action bar's last lines: " + recentActionBar() + ")");
    }

    /** The checks of the crossing of the Dangai by a third gate, each window from the step it follows. */
    private static void checkDangai(Minecraft minecraft, LocalPlayer player) {
        // A fresh line: the turn's from Soul Society was forgotten at 7500.
        Component turning = Component.translatable("message.bleach.senkaimon.turning");
        expectWithin(minecraft, 7508, 7533, () -> actionBarShows(turning),
                "with no Jigokuchō, holding the Gate key never turned the blade (the action bar's last lines: "
                        + recentActionBar() + ")");
        expectWithin(minecraft, 7535, 7559, () -> gateAhead(minecraft, player),
                "the third gate never opened two blocks south (the action bar's last lines: " + recentActionBar() + ")");
        // From two ticks after the pass, which the loading screen does not count, to the tick before the screenshot. On
        // its last tick, a failure also reads the pass on the server, which only then is worth the wait.
        boolean failing = ticksInWorld == 7614 && !windowsPassed.contains(7586);
        if (expectWithin(minecraft, 7586, 7614, () -> atLaneStart(minecraft, player),
                "the gate without a Jigokuchō never set the player at a Dangai lane's start (it is in "
                        + minecraft.level.dimension().location() + " at " + player.position() + ", beside it "
                        + laneWalls(minecraft, player) + "; the action bar's last lines: " + recentActionBar()
                        + (failing ? "; on the server: " + serverPass(minecraft) : "") + ")")) {
            // How near the window's end the pass comes, by the clock and by the server's ticks, read without waiting.
            MinecraftServer server = minecraft.getSingleplayerServer();
            BleachMod.LOGGER.info("Smoke test: at a Dangai lane's start at {}, {} ms and {} server ticks after the tp into "
                    + "the third gate", ticksInWorld, (System.nanoTime() - thirdGateTpAt) / 1_000_000L,
                    server == null ? "no" : server.getTickCount() - thirdGateTpTick);
        }
        Component entered = Component.translatable("message.bleach.dangai.entered");
        expectWithin(minecraft, 7587, 7639, () -> actionBarShows(entered),
                "the Dangai's entry line never showed (the action bar's last lines: " + recentActionBar() + ")");
        // Sent as the player entered, the lane's Kōtotsu waits with its front at the lane's start until the look back.
        expectWithin(minecraft, 7588, 7621, () -> count(minecraft, Kototsu.class, 4.0) == 1,
                "no Kōtotsu waited at the lane's start (" + count(minecraft, Kototsu.class, 4.0) + " within 4 blocks of the"
                        + " player at " + player.position() + ")");
        // From the tp to before the walk can reach the lane's end, 9 ticks from 7646 at the slowest.
        expectWithin(minecraft, 7642, 7652, () -> minecraft.level.dimension() == BleachWorlds.DANGAI
                        && player.getZ() > LANE_END_Z - 0.5,
                "the tp never put the player near its lane's end (it is in " + minecraft.level.dimension().location()
                        + " at " + player.position() + ")");
        // From the walk, a tick for the server to see it and the loading screen, to a long stall while the server makes
        // the district's column: the client counts on through that, the player standing in the lane. The slow fall from
        // 24 blocks takes about 92 ticks, so the screenshot 20 ticks after the window passes is taken in the air.
        double fromCentre = horizontalDistance(player.position(), Vec3.ZERO);
        if (expectWithin(minecraft, 7658, 7860, () -> minecraft.level.dimension() == BleachWorlds.SOUL_SOCIETY
                        && player.hasEffect(MobEffects.SLOW_FALLING) && !player.onGround()
                        && fromCentre >= SeireiteiPlan.districtStart(1)
                        && fromCentre <= SeireiteiPlan.districtStart(DangaiLogic.EXIT_DISTRICTS + 1),
                "the lane's end never let the player out over the Rukongai, falling slowly (it is in "
                        + minecraft.level.dimension().location() + " at " + player.position() + ", " + Math.round(fromCentre)
                        + " blocks from the centre, falling slowly " + player.hasEffect(MobEffects.SLOW_FALLING)
                        + ", on the ground " + player.onGround() + "; the action bar's last lines: " + recentActionBar()
                        + ")")) {
            skyFrom = ticksInWorld;
        }
        if (skyFrom >= 0 && ticksInWorld == skyFrom + 20) {
            shot(minecraft, "rukongai_sky");
        }
    }

    /** The check of the death beat: a new player must wake at a Rukongai home, away from where it died, and be told so. */
    private static void checkWaking(Minecraft minecraft, LocalPlayer player) {
        // From the kill: the server draws a home (a column or more made, about a second each) while the client counts
        // on, then the respawn's loading screen, which does not count; the district's line comes within a second of
        // waking. A home within 32 blocks of the death would fail it: about 1 in 450 runs (spec7e 18.12).
        int district = Rukongai.district(player.getBlockX(), player.getBlockZ());
        double moved = horizontalDistance(player.position(), diedAt);
        expectWithin(minecraft, 7890, 8200, () -> wokeAt >= 0 && player != killed && player.isAlive()
                        && minecraft.level.dimension() == BleachWorlds.SOUL_SOCIETY && player.onGround()
                        && moved >= 32.0 && district >= 1 && district <= SeireiteiPlan.DISTRICTS && actionBarAt >= wokeAt
                        && actionBarShows(Rukongai.placeName(player.getBlockX(), player.getBlockZ())),
                "the soul never woke as a new player at a Rukongai home with its district named (woken " + (wokeAt >= 0)
                        + ", in " + minecraft.level.dimension().location() + " at " + player.position() + ", "
                        + Math.round(moved) + " blocks from where it died, district " + district
                        + "; the action bar's last lines: " + recentActionBar() + ")");
    }

    /** The check of the blocks beat: Hueco Mundo's five blocks must reach the client where they were set down. */
    private static void checkHuecoMundoBlocks(Minecraft minecraft) {
        // The commands run on the server's next tick, and the changed blocks reach the client a tick or two later.
        expectWithin(minecraft, 8212, 8228, () -> huecoMundoBlocksShown(minecraft),
                "Hueco Mundo's five blocks never reached the client in their row before the soul (it stands at "
                        + minecraft.player.position() + ", the row 3 blocks south of " + blocksAt + ")");
    }

    /** Hueco Mundo's five blocks, in the order the blocks beat sets them down from west to east. */
    private static List<Block> huecoMundoBlocks() {
        return List.of(ModBlocks.WHITE_SAND.get(), ModBlocks.WHITE_SANDSTONE.get(), ModBlocks.SMOOTH_WHITE_SANDSTONE.get(),
                ModBlocks.HUECO_QUARTZ.get(), ModBlocks.HUECO_QUARTZ_BRICKS.get());
    }

    /** Whether the client holds Hueco Mundo's five blocks in their row, 3 blocks south of where the soul stood. */
    private static boolean huecoMundoBlocksShown(Minecraft minecraft) {
        if (blocksAt == null) {
            return false;
        }
        List<Block> blocks = huecoMundoBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            if (!minecraft.level.getBlockState(blocksAt.offset(i - 2, 0, 3)).is(blocks.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The checks of the trip to Hueco Mundo by Garganta, each window from the step it follows: the tear in Seireitei, on
     * Hueco Mundo's level ground, the tear there, and home again.
     */
    private static void checkHuecoMundo(Minecraft minecraft, LocalPlayer player) {
        // The tear's line comes back a tick or two after the press reaches the server, after the place line.
        expectWithin(minecraft, 8432, 8450, () -> actionBarShows(Component.translatable("message.bleach.garganta.turning")),
                "in Seireitei, holding the Gate key never tore at the air (the action bar's last lines: "
                        + recentActionBar() + ")");
        expectWithin(minecraft, 8453, 8474, () -> riftAhead(minecraft, player),
                "the torn air never opened a Garganta 1.8 blocks south of the Arrancar in Seireitei (the action bar's"
                        + " last lines: " + recentActionBar() + ")");
        // The sky is chosen as the client makes the level on arrival, and the moon reads the day time sent with it.
        expectWithin(minecraft, 8480, 8660, () -> minecraft.level.dimension() == BleachWorlds.HUECO_MUNDO
                        && player.onGround() && Math.abs(player.getY() - HuecoMundoPlan.LEVEL_Y) < 0.01
                        && horizontalDistance(player.position(), new Vec3(HUECO_MUNDO_X, 0.0, HUECO_MUNDO_Z)) < 0.5
                        && minecraft.level.getBlockState(player.blockPosition().below()).is(ModBlocks.WHITE_SAND.get())
                        && minecraft.level.effects() instanceof HuecoMundoEffects
                        && Float.isNaN(minecraft.level.effects().getCloudHeight())
                        && MoonPhases.opposite(minecraft.level.getMoonPhase()) == MoonPhases.FULL,
                "the Garganta never stood the player on white sand on Las Noches' level ground at y 96, under Hueco Mundo's"
                        + " cloudless sky with the moon full (it is in " + minecraft.level.dimension().location() + " at "
                        + player.position() + ", under " + minecraft.level.effects().getClass().getSimpleName()
                        + " in moon phase " + minecraft.level.getMoonPhase() + ")");
        expectWithin(minecraft, 8707, 8725, () -> actionBarShows(Component.translatable("message.bleach.garganta.turning")),
                "on Las Noches' level ground, holding the Gate key never tore at the air (the action bar's last lines: "
                        + recentActionBar() + ")");
        expectWithin(minecraft, 8728, 8749, () -> riftAhead(minecraft, player),
                "the torn air never opened a Garganta 1.8 blocks south of the Arrancar in Hueco Mundo (the action bar's"
                        + " last lines: " + recentActionBar() + ")");
        // Home to the bottom centre of the block the third gate was taken from, 2 blocks south of the first gate's.
        Vec3 home = gargantaHome();
        expectWithin(minecraft, 8768, 8865, () -> minecraft.level.dimension() == Level.OVERWORLD && player.isAlive()
                        && player.onGround() && horizontalDistance(player.position(), home) < 0.01
                        && Math.abs(player.getY() - home.y) < 0.01,
                "the Garganta never brought the Arrancar home to where it last left the Overworld (it is in "
                        + minecraft.level.dimension().location() + " at " + player.position() + ", not at " + home + ")");
    }

    /**
     * Whether the player stands where a gate sets a soul it does not guide: in the Dangai on a lane's floor, down its
     * middle, a step from the rock behind it, the Kōryū's two walls on either side.
     */
    private static boolean atLaneStart(Minecraft minecraft, LocalPlayer player) {
        int lane = Mth.floor(player.getX()) >> 4;
        Block koryu = ModBlocks.KORYU.get();
        return minecraft.level.dimension() == BleachWorlds.DANGAI && player.onGround()
                && Math.abs(player.getY() - DangaiPlan.FLOOR_Y) < 0.01
                && Math.abs(player.getX() - DangaiPlan.laneX(lane)) < 0.01 && player.getZ() < 2.0
                && minecraft.level.getBlockState(laneWall(lane, DangaiPlan.WEST_WALL, player)).is(koryu)
                && minecraft.level.getBlockState(laneWall(lane, DangaiPlan.EAST_WALL, player)).is(koryu);
    }

    /** The block of this wall of the lane level with the player's feet. */
    private static BlockPos laneWall(int lane, int wall, LocalPlayer player) {
        return BlockPos.containing((lane << 4) + wall, DangaiPlan.FLOOR_Y, player.getZ());
    }

    /** The blocks where the lane the player is in should have its walls, for a failure to name. */
    private static String laneWalls(Minecraft minecraft, LocalPlayer player) {
        int lane = Mth.floor(player.getX()) >> 4;
        return minecraft.level.getBlockState(laneWall(lane, DangaiPlan.WEST_WALL, player)) + " and "
                + minecraft.level.getBlockState(laneWall(lane, DangaiPlan.EAST_WALL, player));
    }

    /**
     * Where the Garganta home sets the soul down: the bottom centre of the block it last left the Overworld from by a
     * gate, the third gate's, which stood 2 blocks south of where the gate home had set it.
     */
    private static Vec3 gargantaHome() {
        return Vec3.atBottomCenterOf(BlockPos.containing(returnTo).south(2));
    }

    /**
     * Whether a Garganta of size 1 has begun to open 1.8 blocks south of the player, seen and level with its feet. Its
     * opening is enough, not its full width ten ticks on, so the window allows a server as late as the Senkaimon's do.
     */
    private static boolean riftAhead(Minecraft minecraft, LocalPlayer player) {
        GargantaRift rift = nearest(minecraft, GargantaRift.class, 4.0, seen -> !ClientSouls.isHiddenFromViewer(seen));
        return rift != null && rift.openness(0.0F) > 0.0F && Math.abs(rift.size() - 1.0F) < 0.01
                && Math.abs(rift.getX() - player.getX()) < 0.3 && Math.abs(rift.getZ() - (player.getZ() + RIFT_AHEAD)) < 0.3
                && Math.abs(rift.getY() - player.getY()) < 0.3;
    }

    /**
     * Whether the player's Senkaimon has reached the client as it should stand: seen, facing south as the player does,
     * two blocks ahead at the feet, 3 wide, 4 tall and a block deep.
     */
    private static boolean gateAhead(Minecraft minecraft, LocalPlayer player) {
        Senkaimon gate = nearest(minecraft, Senkaimon.class, 4.0, seen -> !ClientSouls.isHiddenFromViewer(seen));
        if (gate == null) {
            return false;
        }
        AABB box = gate.getBoundingBox();
        return gate.facing() == Direction.SOUTH
                && Math.abs(gate.getX() - player.getX()) < 0.3 && Math.abs(gate.getZ() - (player.getZ() + 2.0)) < 0.3
                && Math.abs(gate.getY() - player.getY()) < 0.3 && Math.abs(box.getXsize() - 3.0) < 0.01
                && Math.abs(box.getYsize() - 4.0) < 0.01 && Math.abs(box.getZsize() - Senkaimon.THICKNESS) < 0.01;
    }

    /**
     * Whether the client holds the south wall as the plan lays it, on the gap's east side: Sekkiseki to its top, and the
     * gate house in the gap, a plaster pier against the wall and its doorway open by a dark post, closed further in by
     * its great gate, down, with the wicket beside it. The west side's chunk is some 40 chunks into what the client is
     * sent from the lawn spot, too late for a slow runner; the game tests read both sides.
     */
    private static boolean wallStands(ClientLevel level) {
        Block sekkiseki = ModBlocks.SEKKISEKI.get(), plaster = ModBlocks.WHITE_PLASTER.get();
        List<BlockPos> at = wallPlaces();
        // Each pair lies in one chunk, so the block beside the air shows that the air was sent, not missing.
        return level.getBlockState(at.get(0)).is(sekkiseki) && level.getBlockState(at.get(1)).is(plaster)
                && level.getBlockState(at.get(2)).is(Blocks.STRIPPED_DARK_OAK_LOG) && level.getBlockState(at.get(3)).isAir()
                && level.getBlockState(at.get(4)).is(sekkiseki) && level.getBlockState(at.get(5)).isAir()
                && level.getBlockState(at.get(6)).is(ModBlocks.GREAT_GATE.get())
                && level.getBlockState(at.get(7)).is(ModBlocks.WICKET.get());
    }

    /**
     * The blocks {@link #wallStands} reads: in pairs, the wall and the pier at the south gap's east side, the doorway's
     * east post and the air inside it, and the wall's top block east of the gap and the air over it; then the great gate
     * in the gap's middle row and its wicket's lower half, which GateState lays once the house stands.
     */
    private static List<BlockPos> wallPlaces() {
        int z = SeireiteiPlan.SEIREITEI_RADIUS, w = SeireiteiPlan.GATE_HALF_WIDTH, top = SeireiteiPlan.WALL_TOP_Y;
        int door = SeireiteiPlan.GATE_DOOR_HALF_WIDTH;
        return List.of(new BlockPos(w, 90, z), new BlockPos(w - 1, 90, z), new BlockPos(door, 90, z),
                new BlockPos(door - 1, 90, z),
                new BlockPos(16, top - 1, z), new BlockPos(16, top, z),
                new BlockPos(0, 90, SeireiteiPlan.GATE_RADIUS), GateState.wicket(Rukongai.Region.SOUTH.ordinal()));
    }

    /**
     * What the client holds at each place {@link #wallStands} reads, or that its chunk has not come, for a failure. A
     * client level says it has every chunk, so its chunk cache is asked.
     */
    private static String wallSeen(ClientLevel level) {
        return wallPlaces().stream().map(pos -> pos.toShortString() + " " + (level.getChunkSource()
                .hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
                ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) : "unloaded"))
                .collect(Collectors.joining(", "));
    }

    /**
     * Whether the clock waits a tick before the last tick of the Senkaimon's arrival check or of the lawn's wall check,
     * for what a server stalled by the crossing or behind sends late. Past {@link #SERVER_WAIT_TICKS} it goes on, and the
     * check fails as before.
     */
    private static boolean waitsForServer(Minecraft minecraft, LocalPlayer player) {
        boolean late = ticksInWorld == COURT_BY_TICK - 1 && !atCourt(minecraft, player)
                || ticksInWorld == WALL_BY_TICK - 1 && !wallStands(minecraft.level);
        return late && ++serverWaitTicks <= SERVER_WAIT_TICKS;
    }

    /** Whether the player stands in the Senkaimon's court on Seireitei's lawn at y 72, where the gate brings it. */
    private static boolean atCourt(Minecraft minecraft, LocalPlayer player) {
        return minecraft.level.dimension() == BleachWorlds.SOUL_SOCIETY && player.onGround()
                && Math.abs(player.getY() - SeireiteiPlan.PLATEAU_Y) < 0.01
                && horizontalDistance(player.position(), TravelLogic.COURT) < 0.5;
    }

    /** Whether the player stands on Seireitei's lawn at y 72, where {@link #LAWN_TP} sets it. */
    private static boolean onLawn(Minecraft minecraft, LocalPlayer player) {
        return minecraft.level.dimension() == BleachWorlds.SOUL_SOCIETY
                && player.onGround() && Math.abs(player.getY() - SeireiteiPlan.PLATEAU_Y) < 0.01
                && horizontalDistance(player.position(), new Vec3(SOUL_SOCIETY_X, 0.0, SOUL_SOCIETY_Z)) < 0.5;
    }

    /** Whether the client holds the Academy as the plan lays it: its Tailoring Table and its gate's north post. */
    private static boolean academyStands(ClientLevel level) {
        int x = SeireiteiPlan.ACADEMY_X, y = SeireiteiPlan.ACADEMY_FLOOR_Y, z = SeireiteiPlan.ACADEMY_Z;
        return level.getBlockState(new BlockPos(x - 13, y, z + 3)).is(ModBlocks.TAILORING_TABLE.get())
                && level.getBlockState(new BlockPos(x, y, z + 5)).is(Blocks.STRIPPED_DARK_OAK_LOG);
    }

    /**
     * Whether this wall stands as the player's Dankū should: seen, facing south as the player does, two blocks ahead at
     * the feet, 5 wide, 4 tall and a quarter of a block thick.
     */
    private static boolean standsAhead(LocalPlayer player, DankuWall wall) {
        AABB box = wall.getBoundingBox();
        return !ClientSouls.isHiddenFromViewer(wall) && wall.facing() == Direction.SOUTH
                && Math.abs(wall.getX() - player.getX()) < 0.3 && Math.abs(wall.getZ() - (player.getZ() + 2.0)) < 0.3
                && Math.abs(wall.getY() - player.getY()) < 0.3 && Math.abs(box.getXsize() - 5.0) < 0.01
                && Math.abs(box.getYsize() - 4.0) < 0.01 && Math.abs(box.getZsize() - DankuWall.THICKNESS) < 0.01;
    }

    /** Whether the soul's chosen spell, as the client's copy of it has it, is this one. */
    private static boolean chosenIs(SoulData soul, ResourceKey<KidoSpell> key) {
        return soul.shinigami().kido().chosen().equals(Optional.of(key.location()));
    }

    /** The spell as this client's copy of the synced Kidō spells has it, or null if the client never had it. */
    @Nullable
    private static KidoSpell synced(Minecraft minecraft, ResourceKey<KidoSpell> key) {
        return KidoSpell.get(minecraft.level, key.location()).map(Holder.Reference::value).orElse(null);
    }

    /** The spell's trace colour as this client's copy of the spells has it, or -1, which no trace has, without it. */
    private static int colourOf(Minecraft minecraft, ResourceKey<KidoSpell> key) {
        KidoSpell spell = synced(minecraft, key);
        return spell != null ? spell.look().colour() : -1;
    }

    /** The player's own trace of the spell, in its colour as this client's copy has it, once drawn for a tick, or null. */
    @Nullable
    private static CeroBeam ownTrace(Minecraft minecraft, LocalPlayer player, ResourceKey<KidoSpell> key) {
        int colour = colourOf(minecraft, key);
        return ownCero(minecraft, player, beam -> beam.isFired() && beam.colour() == colour && beam.firedAge(0.0F) >= 1.0F);
    }

    /**
     * The player's own trace of a bind, as {@link #ownTrace}, if it runs between these lengths from the hand (so it stopped
     * at what it bound, or ran on), or null.
     */
    @Nullable
    private static CeroBeam ownBind(Minecraft minecraft, LocalPlayer player, ResourceKey<KidoSpell> key, double min,
                                    double max) {
        CeroBeam trace = ownTrace(minecraft, player, key);
        if (trace == null) {
            return null;
        }
        double length = trace.reach().length();
        return length >= min && length <= max ? trace : null;
    }

    /** The spell's incantation at this place in its list, as this client's copy of it has it, or null without one. */
    @Nullable
    private static String incantation(Minecraft minecraft, ResourceKey<KidoSpell> key, int index) {
        KidoSpell spell = synced(minecraft, key);
        return spell != null && index < spell.incantations().size() ? spell.incantations().get(index) : null;
    }

    /** Whether the action bar names this spell as the chosen one, in the line built from this client's copy of it. */
    private static boolean chosenShown(Minecraft minecraft, ResourceKey<KidoSpell> key) {
        KidoSpell spell = synced(minecraft, key);
        return spell != null && actionBarShows(Component.translatable("message.bleach.kido.chosen", spell.title()));
    }

    /**
     * Whether the action bar shows this spell's call, as its caster calls it out, in the line built from this client's
     * copy of it; false while the client has no copy.
     */
    private static boolean callShown(Minecraft minecraft, ResourceKey<KidoSpell> key) {
        KidoSpell spell = synced(minecraft, key);
        return spell != null && actionBarShows(spell.call());
    }

    /**
     * Whether the action bar shows Kaidō begun, on another or on oneself, in the line built from this client's copy of
     * it; false while the client has no copy.
     */
    private static boolean healShown(Minecraft minecraft, boolean self) {
        KidoSpell spell = synced(minecraft, KidoSpell.KAIDO);
        return spell != null && actionBarShows(
                Component.translatable(self ? KidoHeal.BEGUN_SELF_KEY : KidoHeal.BEGUN_KEY, spell.title()));
    }

    /** The health of the villager nearest the player within 4 blocks, as this client has it, for a failure to name. */
    private static String villagerHealth(Minecraft minecraft) {
        Villager villager = nearest(minecraft, Villager.class, 4.0, Villager::isAlive);
        return villager == null ? "no villager in 4 blocks" : String.valueOf(villager.getHealth());
    }

    /**
     * What the integrated server says of the pass into the third gate, read on its own thread, for a failure to name: its
     * ticks since the tp, where it has the player, the player's portal state, and each gate within 4 blocks. A server
     * thread busy for 2 seconds, as in a long load, reads as unread.
     */
    private static String serverPass(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        LocalPlayer local = minecraft.player;
        if (server == null || local == null) {
            return "no server";
        }
        try {
            return server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(local.getUUID());
                if (player == null) {
                    return "no server player";
                }
                ServerLevel level = player.serverLevel();
                StringBuilder out = new StringBuilder((server.getTickCount() - thirdGateTpTick) + " ticks since the tp, in "
                        + level.dimension().location() + " at " + player.position() + ", changing world "
                        + player.isChangingDimension() + ", portal cooldown " + player.getPortalCooldown() + ", portal "
                        + (player.portalProcess == null ? "none" : "time " + player.portalProcess.getPortalTime()
                                + (player.portalProcess.isInsidePortalThisTick() ? " inside" : " not inside"))
                        + ", guided " + TravelLogic.guided(player) + ", riding " + player.isPassenger() + ", gates:");
                for (Senkaimon gate : level.getEntitiesOfClass(Senkaimon.class, player.getBoundingBox().inflate(4.0))) {
                    out.append(" at ").append(gate.position()).append(" aged ").append(gate.tickCount).append(", ")
                            .append(gate.endsAt() - level.getGameTime()).append(" ticks left, lane ").append(gate.lane())
                            .append(", ticking ").append(level.isPositionEntityTicking(gate.blockPosition()));
                }
                return out.toString();
            }).get(2L, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "unread (" + e + ")";
        }
    }

    /**
     * What the integrated server says of the player's heal, read on its own thread, for a failure to name: the time,
     * the channel, the eyes and the look, and every creature within 4 blocks with its health.
     */
    private static String serverHeal(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        LocalPlayer local = minecraft.player;
        if (server == null || local == null) {
            return "no server";
        }
        try {
            return server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(local.getUUID());
                if (player == null) {
                    return "no server player";
                }
                StringBuilder out = new StringBuilder("time " + player.level().getGameTime() + ", channel "
                        + KidoHeal.channel(SoulLogic.get(player)) + ", eyes " + player.getEyePosition() + ", look "
                        + player.getViewVector(1.0F) + ", near:");
                for (LivingEntity body : player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(4.0), body -> body != player)) {
                    out.append(' ').append(body).append(" at health ").append(body.getHealth());
                }
                return out.toString();
            }).get(2L, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "unread (" + e + ")";
        }
    }

    /**
     * Holds (or lets go of) the arrival chunks in Soul Society, the Senkaimon court's, the lawn spot's and the Academy's,
     * and the south gap's whose wall and gate house the lawn's wall check reads, on the integrated server with a forced
     * ticket each, which generates them in the background, as the forceload command would not. A ticket reaches a chunk
     * each way, so the Academy's gate and both sides of the gap are held too.
     */
    private static void holdArrival(Minecraft minecraft, boolean hold) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        List<ChunkPos> chunks = List.of(new ChunkPos(BlockPos.containing(TravelLogic.COURT)),
                new ChunkPos(BlockPos.containing(SOUL_SOCIETY_X, SeireiteiPlan.PLATEAU_Y, SOUL_SOCIETY_Z)),
                new ChunkPos(new BlockPos(0, SeireiteiPlan.PLATEAU_Y, SeireiteiPlan.SEIREITEI_RADIUS)),
                new ChunkPos(BlockPos.containing(SeireiteiPlan.ACADEMY_LANDING_X, SeireiteiPlan.ACADEMY_FLOOR_Y,
                        SeireiteiPlan.ACADEMY_LANDING_Z)));
        server.execute(() -> {
            ServerLevel level = server.getLevel(BleachWorlds.SOUL_SOCIETY);
            if (level == null) {
                return;
            }
            for (ChunkPos chunk : chunks) {
                if (hold) {
                    level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 1, chunk);
                } else {
                    level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 1, chunk);
                }
            }
        });
    }

    /**
     * Holds (or lets go of) the end of this Dangai lane on the integrated server with a forced ticket, as
     * {@link #holdArrival} does for Soul Society, so it is made in the background before the player is moved there.
     */
    private static void holdLane(Minecraft minecraft, int lane, boolean hold) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || lane < 0) {
            return;
        }
        heldLane = lane;
        ChunkPos chunk = new ChunkPos(lane, Mth.floor(LANE_END_Z) >> 4);
        server.execute(() -> {
            ServerLevel level = server.getLevel(BleachWorlds.DANGAI);
            if (level == null) {
                return;
            }
            if (hold) {
                level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 1, chunk);
            } else {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 1, chunk);
            }
        });
    }

    /**
     * Holds (or lets go of) Seireitei's chunk where the Garganta is torn, the chunk the player arrives in on Hueco Mundo's
     * level ground and the Overworld's chunk the Garganta brings it home to, on the integrated server with a forced ticket
     * each, as {@link #holdArrival} does for Soul Society.
     */
    private static void holdHuecoMundo(Minecraft minecraft, boolean hold) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ChunkPos there = new ChunkPos(BlockPos.containing(HUECO_MUNDO_X, HuecoMundoPlan.LEVEL_Y, HUECO_MUNDO_Z));
        ChunkPos torn = new ChunkPos(BlockPos.containing(HUECO_MUNDO_X, SeireiteiPlan.PLATEAU_Y, TEAR_FROM_Z));
        ChunkPos home = new ChunkPos(BlockPos.containing(gargantaHome()));
        server.execute(() -> {
            ServerLevel level = server.getLevel(BleachWorlds.HUECO_MUNDO);
            ServerLevel society = server.getLevel(BleachWorlds.SOUL_SOCIETY);
            if (level == null || society == null) {
                return;
            }
            if (hold) {
                society.getChunkSource().addRegionTicket(TicketType.FORCED, torn, 1, torn);
                level.getChunkSource().addRegionTicket(TicketType.FORCED, there, 1, there);
                server.overworld().getChunkSource().addRegionTicket(TicketType.FORCED, home, 1, home);
            } else {
                society.getChunkSource().removeRegionTicket(TicketType.FORCED, torn, 1, torn);
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, there, 1, there);
                server.overworld().getChunkSource().removeRegionTicket(TicketType.FORCED, home, 1, home);
            }
        });
    }

    /** Whether the integrated server's own copy of the soul has this spell chosen, read on its thread. */
    private static boolean serverChosen(Minecraft minecraft, ResourceKey<KidoSpell> key) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        LocalPlayer local = minecraft.player;
        if (server == null || local == null) {
            return false;
        }
        try {
            return server.submit(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(local.getUUID());
                return player != null && chosenIs(SoulLogic.get(player), key);
            }).get(2L, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /** Whether one of the server's last chat lines reads as this one, give or take the spaces about it. */
    private static boolean chatShows(@Nullable String line) {
        return line != null && chatLines.stream().anyMatch(seen -> seen.strip().equals(line.strip()));
    }

    /** Whether the chat shows this said aloud in the player's name, as vanilla shows a chat line and the mod a speech. */
    private static boolean spokenBack(LocalPlayer player, @Nullable String text) {
        return text != null && chatShows(Component.translatable("chat.type.text", player.getDisplayName(),
                Component.literal(text)).getString());
    }

    /** The last line the server put on the action bar, for a failure to name the refusal it may have been. */
    private static String lastActionBar() {
        return actionBar == null ? "nothing" : "\"" + actionBar.getString() + "\"";
    }

    /** The last few lines the server put on the action bar, each with the tick it came, for a failure to name. */
    private static String recentActionBar() {
        return actionBarLines.isEmpty() ? "nothing" : String.join(", ", actionBarLines);
    }

    /**
     * Whether the last line the server put on the action bar reads as this one, and came at least two ticks ago: a
     * screenshot shows the last frame drawn, so one taken as the line comes would miss it.
     */
    private static boolean actionBarShows(Component line) {
        return actionBar != null && ticksInWorld - actionBarAt >= 2 && actionBar.getString().equals(line.getString());
    }

    /** Whether an Arrancar NPC the player can see stands within 8 blocks as asked. */
    private static boolean seenNpc(Minecraft minecraft, Predicate<ArrancarNpc> state) {
        return nearest(minecraft, ArrancarNpc.class, 8.0,
                npc -> npc.isAlive() && !ClientSouls.isHiddenFromViewer(npc) && state.test(npc)) != null;
    }

    /** Whether the player is drawn with this Hollow look bit. */
    private static boolean hasLook(LocalPlayer player, int bit) {
        return (ClientSouls.appearance(player).hollowLook() & bit) != 0;
    }

    /** The player's own Cero near it, in the state asked for, or null. */
    @Nullable
    private static CeroBeam ownCero(Minecraft minecraft, LocalPlayer player, Predicate<CeroBeam> state) {
        return nearest(minecraft, CeroBeam.class, SUMMONED_RANGE, beam -> beam.owner() == player && state.test(beam));
    }

    /** Whether every armour slot is empty. */
    private static boolean armourShed(LocalPlayer player) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The bait finale's cracked sky on the client, once it is at least mostly open, or null. */
    @Nullable
    private static GargantaRift crackedSky(Minecraft minecraft) {
        return nearest(minecraft, GargantaRift.class, 64.0, rift -> rift.size() >= 3.0F && rift.openness(0.0F) >= 0.8F);
    }

    /** The checks of the arts past the third tier, each window from the step it follows. */
    private static void checkArts(Minecraft minecraft, LocalPlayer player, SoulData soul) {
        if (expectWithin(minecraft, 1731, 1750,
                () -> QuincyArtsLogic.gloveMastered(soul) && shows(player, QuincyArtsLogic.LOOK_GLOVE_MASTERED),
                "the mastered Sanrei Glove never reached the client")) {
            shot(minecraft, "sanrei_glove");
        }
        expectWithin(minecraft, 1779, 1795, () -> count(minecraft, HeiligPfeil.class, 48.0) >= 3,
                "the mastered glove loosed no flank arrows");
        if (expectWithin(minecraft, 1807, 1830,
                () -> QuincyArtsLogic.inLetzt(soul) && shows(player, QuincyArtsLogic.LOOK_LETZT), "Letzt Stil never began")) {
            shot(minecraft, "letzt_stil");
        }
        if (expectWithin(minecraft, 1891, 1910, () -> nearest(minecraft, LetztPfeil.class, 64.0, arrow -> true) != null,
                "no Letzt Pfeil reached the client")) {
            shot(minecraft, "letzt_pfeil");
        }
        if (expectWithin(minecraft, 1916, 1935,
                () -> QuincyArtsLogic.isSealed(soul) && shows(player, QuincyArtsLogic.LOOK_SEALED),
                "the seal never reached the client")) {
            shot(minecraft, "quincy_sealed");
        }
        expectWithin(minecraft, 1956, 1975,
                () -> QuincyArtsLogic.isRestored(soul) && shows(player, QuincyArtsLogic.LOOK_GINREI),
                "the restoration never reached the client");
        expectWithin(minecraft, 2006, 2030, () -> count(minecraft, HeiligPfeil.class, 48.0) >= 3,
                "Ginrei Kojaku never streamed");
        if (expectWithin(minecraft, 2066, 2095, () -> count(minecraft, HeiligPfeil.class, 64.0) >= 12,
                "Licht Regen never fell")) {
            shot(minecraft, "licht_regen");
        }
        if (expectWithin(minecraft, 2106, 2130, () -> SeeleSchneiderItem.isFormed(player.getMainHandItem()),
                "the Seele Schneider never formed")) {
            shot(minecraft, "seele_schneider");
        }
        if (expectWithin(minecraft, 2162, 2185, () -> nearest(minecraft, SeeleSchneiderPfeil.class, 32.0, blade -> true) != null,
                "no Seele Schneider was fired")) {
            shot(minecraft, "seele_fired");
        }
        if (expectWithin(minecraft, 2196, 2215,
                () -> nearest(minecraft, GintoCast.class, 16.0, cast -> cast.spell() == GintoSpell.WOLKE) != null,
                "Wolke never formed")) {
            shot(minecraft, "ginto_wolke");
        }
        if (expectWithin(minecraft, 2243, 2265,
                () -> nearest(minecraft, GintoCast.class, 32.0, cast -> cast.spell() == GintoSpell.HEIZEN) != null,
                "Heizen never rose")) {
            shot(minecraft, "heizen");
        }
        expectWithin(minecraft, 2266, 2290,
                () -> subject instanceof LivingEntity struck && (!struck.isAlive() || struck.getHealth() < struck.getMaxHealth()),
                "Heizen did not strike the Hollow");
        if (expectWithin(minecraft, 2301, 2320, () -> QuincyArtsLogic.inVollstandig(soul) && player.mayFly(),
                "Vollständig never gave flight")) {
            shot(minecraft, "vollstandig");
        }
        if (expectWithin(minecraft, 2361, 2375, () -> player.getAbilities().flying && player.getY() > flightFrom + 2.5,
                "Vollständig flight did not lift the player")) {
            shot(minecraft, "vollstandig_flight");
        }
        if (expectWithin(minecraft, 2381, 2400, () -> shows(player, QuincyArtsLogic.LOOK_SKLAVEREI), "Sklaverei never began")) {
            shot(minecraft, "sklaverei");
        }
        expectWithin(minecraft, 2411, 2430, () -> !QuincyArtsLogic.inVollstandig(soul) && !player.mayFly(),
                "Vollständig never ended");
        if (expectWithin(minecraft, 2431, 2480, player::onGround, "the player never landed after Vollständig")) {
            landedAt = ticksInWorld;
        }
        // A few ticks on, so the fall damage, if any, has come back from the server.
        if (landedAt >= 0 && ticksInWorld == landedAt + 5) {
            expect(minecraft, player.getHealth() >= healthBefore, "the landing after Vollständig hurt");
        }
    }

    private static void createWorld(Minecraft minecraft) {
        BleachMod.LOGGER.info("Smoke test: creating a flat world");
        minecraft.options.renderDistance().set(4);
        LevelSettings settings = new LevelSettings("bleach-smoke-test-" + System.currentTimeMillis(),
                GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings,
                WorldOptions.defaultWithRandomSeed(),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                minecraft.screen);
    }

    private static void command(Minecraft minecraft, String command) {
        BleachMod.LOGGER.info("Smoke test: /{}", command);
        minecraft.player.connection.sendCommand(command);
    }

    /**
     * Sends a line to the server's chat as the chat screen does, untrimmed: unsigned on a dev client, which has no chat
     * session, and the integrated server, which enforces no secure profile, takes it so.
     */
    private static void chat(Minecraft minecraft, String text) {
        BleachMod.LOGGER.info("Smoke test: chat {}", text);
        minecraft.player.connection.sendChat(text);
    }

    /** Says the spell's incantation at this place in its list in chat, as the client's copy of it reads, or fails. */
    private static void chant(Minecraft minecraft, ResourceKey<KidoSpell> key, int index) {
        String line = incantation(minecraft, key, index);
        if (line == null) {
            fail(minecraft, "the client's copy of " + key.location() + " has no incantation " + (index + 1) + " to say");
        } else {
            chat(minecraft, line);
        }
    }

    private static void expect(Minecraft minecraft, boolean condition, String problem) {
        if (!condition && !finished) {
            fail(minecraft, problem);
        }
    }

    /**
     * Checks a condition every tick from {@code from} to {@code to}, for something that takes the
     * server a while. It passes the first tick the condition holds, and fails if it never does.
     *
     * @return true on the tick it passes
     */
    private static boolean expectWithin(Minecraft minecraft, int from, int to, BooleanSupplier condition, String problem) {
        if (finished || ticksInWorld < from || ticksInWorld > to || windowsPassed.contains(from)) {
            return false;
        }
        if (condition.getAsBoolean()) {
            windowsPassed.add(from);
            return true;
        }
        if (ticksInWorld == to) {
            fail(minecraft, problem);
        }
        return false;
    }

    /** How many entities of this class are within {@code range} blocks of the player. */
    private static int count(Minecraft minecraft, Class<? extends Entity> type, double range) {
        LocalPlayer player = minecraft.player;
        return minecraft.level.getEntitiesOfClass(type, player.getBoundingBox().inflate(range)).size();
    }

    /** Whether the player's own look, as other players' clients would draw it, has this Quincy arts bit. */
    private static boolean shows(LocalPlayer player, int bit) {
        return (ClientSouls.appearance(player).quincyLook() & bit) != 0;
    }

    private static List<AcademyInstructor> instructorsNear(Minecraft minecraft, double range) {
        LocalPlayer player = minecraft.player;
        return minecraft.level.getEntitiesOfClass(AcademyInstructor.class, player.getBoundingBox().inflate(range),
                instructor -> instructor.distanceTo(player) <= range);
    }

    /** The nearest entity of this class within {@code range} blocks of the player that passes the filter, or null. */
    @Nullable
    private static <T extends Entity> T nearest(Minecraft minecraft, Class<T> type, double range, Predicate<T> filter) {
        LocalPlayer player = minecraft.player;
        T nearest = null;
        double nearestSqr = range * range;
        for (T entity : minecraft.level.getEntitiesOfClass(type, player.getBoundingBox().inflate(range), filter)) {
            double distanceSqr = entity.distanceToSqr(player);
            if (distanceSqr <= nearestSqr) {
                nearest = entity;
                nearestSqr = distanceSqr;
            }
        }
        return nearest;
    }

    /** Turns the player's eyes to a point, as the mouse would. */
    private static void look(Minecraft minecraft, Vec3 target) {
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }

    /** Whether this is a Spider Hollow and not a Hexapodus, which is a kind of Spider Hollow too. */
    private static boolean isSpiderHollow(Entity entity) {
        return entity.getType() == ModEntities.SPIDER_HOLLOW.get();
    }

    /** Takes the living Spider Hollow just in front of the player as the subject, and looks at its mask. */
    private static void aimAtMask(Minecraft minecraft, String problem) {
        subject = nearest(minecraft, SpiderHollow.class, 3.5, spider -> spider.isAlive() && isSpiderHollow(spider));
        expect(minecraft, subject != null, problem);
        if (subject instanceof Hollow hollow) {
            look(minecraft, HollowMask.maskCenter(hollow));
        }
    }

    /** Whether one of each kind of Hollow and a Plus has reached the client nearby, and none is hidden. */
    private static boolean summonedSeen(Minecraft minecraft) {
        List<EntityType<?>> types = List.of(ModEntities.SPIDER_HOLLOW.get(), ModEntities.HEXAPODUS.get(),
                ModEntities.HOUND_HOLLOW.get(), ModEntities.SERPENT_HOLLOW.get(), ModEntities.FISHBONE_D.get(),
                ModEntities.PLUS.get());
        for (EntityType<?> type : types) {
            if (nearest(minecraft, Entity.class, SUMMONED_RANGE,
                    entity -> entity.getType() == type && !ClientSouls.isHiddenFromViewer(entity)) == null) {
                return false;
            }
        }
        return true;
    }

    /** The nearest Garganta on the client that is at least mostly open, or null. */
    @Nullable
    private static GargantaRift openRift(Minecraft minecraft) {
        return nearest(minecraft, GargantaRift.class, RIFT_RANGE, rift -> rift.openness(0.0F) >= 0.8F);
    }

    /** The Jinzen screen, if it is the one open. */
    @Nullable
    private static JinzenScreen jinzenScreen(Minecraft minecraft) {
        return minecraft.screen instanceof JinzenScreen screen ? screen : null;
    }

    /** The scene the open Jinzen screen shows, or null with no Jinzen screen open. */
    @Nullable
    private static JinzenScene jinzenScene(Minecraft minecraft) {
        JinzenScreen screen = jinzenScreen(minecraft);
        return screen == null ? null : screen.page().scene();
    }

    /** The step of the test the open Jinzen screen waits on, if any. */
    private static Optional<JinzenStep> jinzenStep(Minecraft minecraft) {
        JinzenScreen screen = jinzenScreen(minecraft);
        return screen == null ? Optional.empty() : screen.page().step();
    }

    /** The stack's {@code custom_model_data}, or 0 without one (the plain Asauchi's model). */
    private static int modelOf(ItemStack stack) {
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null ? 0 : model.value();
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    /** Saves a screenshot to {@code screenshots/} for a person to look over. A failure here is only logged. */
    private static void shot(Minecraft minecraft, String name) {
        try {
            Screenshot.grab(minecraft.gameDirectory, "bleach-smoke-" + name + ".png", minecraft.getMainRenderTarget(),
                    message -> BleachMod.LOGGER.info("Smoke test: {}", message.getString()));
        } catch (RuntimeException e) {
            BleachMod.LOGGER.warn("Smoke test: could not save the {} screenshot", name, e);
        }
    }

    private static void fail(Minecraft minecraft, String problem) {
        finished = true;
        BleachMod.LOGGER.error(FAILED + problem);
        minecraft.stop();
    }
}
