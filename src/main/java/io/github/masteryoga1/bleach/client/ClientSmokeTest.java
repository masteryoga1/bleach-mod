package io.github.masteryoga1.bleach.client;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.Gillian;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.QuincyElder;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.item.ArrancarUniformItem;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
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
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.zanpakuto.JinzenScene;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStep;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStepKind;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A check CI runs on a real client (the {@code runClientSmokeTest} Gradle task, which sets
 * {@code -Dbleach.smokeTest=true}). It makes a flat world, answers the bloodline question as a Human,
 * turns the player into a Plus, releases reiatsu, brings on Encroachment, turns them into a Hollow and
 * opens the soul screen. Then it makes them a Shinigami in the Shihakushō with a sheathed Asauchi,
 * opens the Tailoring Table and the instructor's dialogue, flash steps, stands on reishi footing by
 * jumping while falling, places an Academy, summons each Hollow and a Plus, cracks a mask, purifies a
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
 * the client must see it gone and its wait begun, before the run quits.
 *
 * <p>Nearly all of it is seen with the camera behind the player, so the chain, mask, garb, saya,
 * NPCs, Hollows, zanpakutō, bow, arrows, veins, glove, wings, blades, casts, uniform, threads, rifts,
 * Hollow body, Menos, Cero, Arrancar, Arrancar NPCs, Negación light, Caja Negación, inner Hollow, Visored
 * mask and HUD are drawn, and screenshots of the main moments are saved for review. Any crash in the
 * mod's client code fails the run, and so does anything that did not reach the client as expected.
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
    /** How high the Negación light must lift the player before it is left. */
    private static final double NEGACION_MIN_RISE = 2.0;
    /** How far around the player the summoned Hollows and Plus, and the Garganta, are looked for. */
    private static final double SUMMONED_RANGE = 24.0;
    private static final double RIFT_RANGE = 40.0;
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

    private static boolean worldRequested;
    private static int ticksWaiting;
    private static int ticksInWorld;
    private static boolean finished;
    /** How many ticks the bloodline question has been open. */
    private static int bloodlineTicks;
    /** How many ticks the steps have waited on the bloodline answer. */
    private static int bloodlineWaitTicks;
    /** The first tick of each {@link #expectWithin} window that has already passed. */
    private static final Set<Integer> windowsPassed = new HashSet<>();
    /** Where the player stood before the flash step. */
    private static Vec3 dashFrom;
    /** Where the player stood as it was mounted on the Negación light, or null before then. */
    @Nullable
    private static Vec3 negacionFrom;
    /** What the current step acts on: the Hollow to strike, the Plus to send on, or the Quincy to talk to. */
    @Nullable
    private static Entity subject;
    /** How high the player stood when Vollständig's flight began. */
    private static double flightFrom;
    /** The player's health just before Vollständig was let go in the air. */
    private static float healthBefore;
    /** The tick the player landed after Vollständig, or -1 before then. */
    private static int landedAt = -1;

    private ClientSmokeTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("bleach.smokeTest");
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

        // Nothing counts while the world is still arriving, which no other screen shows over.
        if (minecraft.screen instanceof ReceivingLevelScreen) {
            return;
        }
        LocalPlayer player = minecraft.player;
        SoulData soul = player.getData(SoulAttachments.SOUL);
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
        expectWithin(minecraft, 345, 380,
                () -> horizontalDistance(player.position(), dashFrom) >= DASH_MIN_DISTANCE,
                "the flash step did not carry the player " + DASH_MIN_DISTANCE + " blocks");
        if (expectWithin(minecraft, 390, 430,
                () -> player.onGround() && player.getY() > dashFrom.y + FOOTING_MIN_HEIGHT
                        && player.level().getBlockState(player.blockPosition().below()).is(ModBlocks.REISHI_FOOTING.get()),
                "jumping while falling did not stand the player on reishi footing")) {
            shot(minecraft, "footing");
        }
        expectWithin(minecraft, 450, 500, () -> instructorsNear(minecraft, 64.0).size() >= 2,
                "the placed Academy's instructor never reached the client");
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
            case 440 -> command(minecraft, "place structure bleach:academy ~ ~-12 ~20");
            case 505 -> shot(minecraft, "academy");
            case 510 -> minecraft.setScreen(new SoulStatsScreen());
            // A screenshot shows the last frame drawn, so it waits a moment for the screen to be drawn.
            case 515 -> shot(minecraft, "stats");
            case 550 -> minecraft.setScreen(null);
            // Hollows vanish on Peaceful. Back on the ground, off the footing, facing north, and clear of the
            // Academy: placed 20 blocks south, it starts at that chunk's corner and turns at random, so it
            // can reach up to 12 blocks north of this spot, and an arrow shot up under its ceiling bursts
            // at once.
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
            // Hollow Bait, by command: the Academy close by and the quiet game rule are passed over.
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
            case 4430 -> {
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
