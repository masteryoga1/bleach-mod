package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.entity.Tenshintai;
import io.github.masteryoga1.bleach.entity.ZabimaruSpirit;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.BankaiTrial;
import io.github.masteryoga1.bleach.soul.Materialisation;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Tenshintai (8k1): the doll set down, refused and crumbling, stood again by its soul's ticks, after a
 * logout too, and crumbling as its window ends; struck, drawing its soul's spirit out for a bout by it; refused where
 * no bout could begin by it; and its spirit keeping its wounds from one bout by it to the next. Each test asks only
 * about its own souls and dolls: every bout ends within the tick it began in, and every doll a test set down is taken
 * away however it ends, by its end's clean-up. Their dolls are set down and their bouts fought as by a spirit with no
 * test of its own ({@code spirit_tests} off), so no ground about a doll is asked for Zangetsu's blades, but where K5
 * asks for it and K6 asks Zabimaru's two halves what they kept. On Peaceful no doll is set down, as no bout could begin
 * by it. A mock soul is told nothing, so a line is asked of a joined soul.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class TenshintaiTests {
    /** Where a test's doll stands: a block north of the field's middle, where its soul stands. */
    static final BlockPos DOLL = new BlockPos(8, 2, 7);
    static final String SET_DOWN = "message.bleach.tenshintai.set_down", STANDS = "message.bleach.tenshintai.stands";
    static final String NO_SPOT = "message.bleach.tenshintai.no_spot", NO_POWER = "message.bleach.tenshintai.no_power";
    static final String REACHED = "message.bleach.tenshintai.reached", CRUMBLED = "message.bleach.tenshintai.crumbled";
    static final String NOT_YOURS = "message.bleach.tenshintai.not_yours", WATCHING = "message.bleach.tenshintai.watching";
    static final String UNRELEASED = "message.bleach.tenshintai.unreleased", ALREADY = "message.bleach.tenshintai.already";
    static final String CROWDED = "message.bleach.tenshintai.crowded", NO_GROUND = "message.bleach.tenshintai.no_ground";
    static final String PEACEFUL = "message.bleach.tenshintai.peaceful";
    static final String WINDOW = "screen.bleach.soul.tenshintai", AT = "screen.bleach.soul.tenshintai.at";
    static final String AWAY = "screen.bleach.soul.tenshintai.away", SPRING = "screen.bleach.soul.tenshintai.spring";

    private TenshintaiTests() {
    }

    /** Sets this soul's doll down at this spot of the test, or fails saying why not; the doll. */
    static Tenshintai setDown(GameTestHelper helper, Player soul, BlockPos relative) {
        String refusal = Tenshintai.setDown(helper.getLevel(), soul, helper.absolutePos(relative));
        Tenshintai doll = Tenshintai.standing(soul.getUUID());
        helper.assertTrue(refusal == null && doll != null, "the doll was not set down: " + refusal);
        return doll;
    }

    /** This soul uses this stack from its off hand on this face of this block of the test, as a click does. */
    static InteractionResult useOn(GameTestHelper helper, Player soul, ItemStack stack, BlockPos relative, Direction face) {
        BlockPos block = helper.absolutePos(relative);
        soul.setItemInHand(InteractionHand.OFF_HAND, stack);
        return stack.useOn(new UseOnContext(soul, InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(block), face, block, false)));
    }

    /** The doll's place in the test's world, as a record names it. */
    static GlobalPos place(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    /** Whether this doll stands at this spot of the test, facing south, as toward a soul at the field's middle. */
    static boolean facesMiddle(GameTestHelper helper, Tenshintai doll, BlockPos relative) {
        return doll.blockPosition().equals(helper.absolutePos(relative)) && Math.abs(Mth.wrapDegrees(doll.getYRot())) < 1.0F;
    }

    /** Ends these souls' bouts, and lets their dolls go from their records and the world, so none outlives its test. */
    static void clear(List<Player> souls) {
        for (Player soul : souls) {
            BankaiTrialTests.clear(soul);
            SoulData data = SoulLogic.get(soul);
            data.setZanpakuto(data.zanpakuto().withBankai(data.zanpakuto().bankai().closed()));
            Tenshintai doll = Tenshintai.standing(soul.getUUID());
            if (doll != null) {
                doll.discard();
            }
        }
    }

    /** The test's chunks held entity-ticking, and these souls' dolls cleared as it ends, passed or failed. */
    static void clearedAtEnd(GameTestHelper helper, List<Player> souls) {
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ShakonmakuTests.hold(helper, helper.getLevel(), chunk.x, chunk.z).atEnd(() -> clear(souls));
    }

    /** The dolls standing within a block of this spot of the test. */
    static List<Tenshintai> dollsAt(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getEntitiesOfClass(Tenshintai.class, new AABB(helper.absolutePos(relative)).inflate(1.0));
    }

    /** Whether this line is this key's, with this one argument: the days a doll stands, the seconds a bout lasts. */
    static boolean says(Component line, String key, Object arg) {
        return line.getContents() instanceof TranslatableContents words && words.getKey().equals(key)
                && words.getArgs().length == 1 && words.getArgs()[0].equals(arg);
    }

    /** This soul's blade in its main hand released into Zangetsu's Shikai, as its first release leaves it. */
    static void release(GameTestHelper helper, Player soul) {
        ZanpakutoItem.setReleased(soul.getMainHandItem(), ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), true);
    }

    /** Runs these steps as a spirit with no test of its own is fought, with {@code spirit_tests} off. */
    static void plain(Runnable steps) {
        KidoTests.withConfig(BleachConfig.BANKAI_TRIAL_SPIRIT_TESTS, false, steps);
    }

    /** On Peaceful no doll is set down, as no bout could begin by it: true, once that is checked, when the test is done. */
    static boolean refusedOnPeaceful(GameTestHelper helper, Player soul) {
        if (!HollowPlayerTests.peaceful(helper)) {
            return false;
        }
        String said = Tenshintai.setDown(helper.getLevel(), soul, helper.absolutePos(DOLL));
        helper.assertTrue(PEACEFUL.equals(said) && Tenshintai.standing(soul.getUUID()) == null,
                "a doll was set down on Peaceful: " + said);
        helper.succeed();
        return true;
    }

    /** This soul strikes its doll and a bout by the doll begins, or the test fails; the bout's spirit. */
    static MaterialisedSpirit struck(GameTestHelper helper, Player soul, Tenshintai doll) {
        soul.attack(doll);
        BankaiTrial.Trial trial = BankaiTrial.trial(soul.getUUID());
        helper.assertTrue(trial != null && trial.byDoll(), "the strike began no bout by the doll: " + trial);
        return trial.spirit();
    }

    /** This soul's bout runs out: its tick at the bout's end, which ends it on the time. */
    static void runOut(Player soul) {
        BankaiTrial.tick(soul, SoulLogic.get(soul), BankaiTrial.trial(soul.getUUID()).endsAt());
    }

    /**
     * K1. A named Shinigami's doll, set down from its item on the top of the field's floor, stands there facing it, the
     * item spent and its record naming the spot, and it is told so; used on a block's side, the item does nothing. A
     * soul that may not hold Bankai, or holds it, is refused one wherever it clicks, and so is a spot over air, in stone
     * or in water, and a second doll while the first stands, wherever, the item kept and the soul told; a strike with
     * the blade sealed, or by another soul, does nothing to the doll. A doll whose soul's record no longer names it,
     * Bankai revoked or reached, crumbles on its next tick, and one whose soul is gone goes.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void aDollSetDownStandsUntilItsRecordForgetsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        ServerPlayer ichigo = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), true);
        BankaiSpiritTests.stand(helper, ichigo, BankaiTrialTests.MIDDLE);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player other = BankaiSpiritTests.wielder(helper, BankaiTrialTests.FLOOR);
        Player past = BankaiSpiritTests.wielder(helper, BankaiTrialTests.FLOOR);
        BankaiLogic.reach(SoulLogic.get(past), Component.literal("Tensa Zangetsu"), now);
        Map<Player, String> refused = new LinkedHashMap<>();
        refused.put(ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.HUMAN, 500.0F)), NO_POWER);
        refused.put(ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F)), NO_POWER);
        refused.put(past, REACHED);
        // Each stands where the spirit it would draw out, four blocks south of it, would come out on the field.
        Map<Player, BlockPos> spots = new LinkedHashMap<>();
        spots.put(BankaiSpiritTests.wielder(helper, BankaiTrialTests.FLOOR), new BlockPos(1, 2, 3));
        spots.put(BankaiSpiritTests.wielder(helper, new Vec3(1.5, 2.0, 8.5)), new BlockPos(1, 2, 10));
        spots.put(BankaiSpiritTests.wielder(helper, new Vec3(14.5, 2.0, 1.5)), new BlockPos(14, 2, 3));
        List<Player> souls = new ArrayList<>(List.of(ichigo, other));
        souls.addAll(refused.keySet());
        souls.addAll(spots.keySet());
        clearedAtEnd(helper, souls);
        BlockPos air = DOLL.above(2), pool = new BlockPos(8, 2, 5);
        List<Tenshintai> dolls = new ArrayList<>();
        List<Player> owners = List.copyOf(spots.keySet());
        plain(() -> {
            refused.forEach((soul, line) -> {
                String said = Tenshintai.setDown(level, soul, helper.absolutePos(air));
                helper.assertTrue(line.equals(said) && BankaiTrialTests.record(soul).doll().isEmpty(),
                        "a soul to be told " + line + " over air was told " + said + ", or its record names a doll");
            });
            HihioZabimaruTests.clearingOnFailure(() -> helper.setBlock(pool, Blocks.AIR), () -> {
                // Over air, then in a block of stone and in water on the floor.
                for (Block filling : List.of(Blocks.AIR, Blocks.STONE, Blocks.WATER)) {
                    helper.setBlock(pool, filling);
                    String said = Tenshintai.setDown(level, ichigo, helper.absolutePos(filling == Blocks.AIR ? air : pool));
                    helper.assertTrue(NO_SPOT.equals(said) && BankaiTrialTests.record(ichigo).doll().isEmpty(),
                            "a doll set down over air, in stone or in water was told " + said);
                }
                helper.setBlock(pool, Blocks.AIR);
            });
            ItemStack folded = new ItemStack(ModItems.TENSHINTAI.get());
            InteractionResult sideways = useOn(helper, ichigo, folded, DOLL.below(), Direction.NORTH);
            helper.assertTrue(sideways == InteractionResult.PASS && folded.getCount() == 1
                            && BankaiTrialTests.record(ichigo).doll().isEmpty(),
                    "used on a block's side, the doll came to " + sideways + " and left " + folded);
            InteractionResult used = useOn(helper, ichigo, folded, DOLL.below(), Direction.UP);
            Tenshintai doll = Tenshintai.standing(ichigo.getUUID());
            BankaiRecord set = BankaiTrialTests.record(ichigo);
            helper.assertTrue(used.consumesAction() && folded.isEmpty() && doll != null && facesMiddle(helper, doll, DOLL)
                            && set.doll().equals(Optional.of(place(helper, DOLL))) && set.tries() == 0
                            && TestPlayers.chat(ichigo).stream().anyMatch(line -> GateTests.says(line, SET_DOWN, -1)),
                    "setting the doll down came to " + used + " and left " + folded + ", the record " + set
                            + ", or the soul was not told");
            ItemStack second = new ItemStack(ModItems.TENSHINTAI.get());
            InteractionResult again = useOn(helper, ichigo, second, new BlockPos(6, 1, 8), Direction.UP);
            helper.assertTrue(again == InteractionResult.FAIL && second.getCount() == 1
                            && Tenshintai.standing(ichigo.getUUID()) == doll
                            && STANDS.equals(Tenshintai.setDown(level, ichigo, helper.absolutePos(air)))
                            && TestPlayers.actionBar(ichigo).stream().anyMatch(line -> GateTests.says(line, STANDS, -1)),
                    "a second doll came to " + again + " and left " + second
                            + " with one standing, or the soul was not told");
            ichigo.attack(doll);
            other.attack(doll);
            helper.assertTrue(doll.skipAttackInteraction(other) && !doll.isRemoved()
                            && BankaiTrialTests.record(ichigo).equals(set),
                    "a strike reached the doll, or moved it or its record");

            // Three more dolls: one whose soul's Bankai is revoked, one whose soul reaches it, and one whose soul goes.
            spots.forEach((soul, spot) -> dolls.add(setDown(helper, soul, spot)));
            // The last two souls stand north of their dolls, so those face north, not the way an unturned doll faces.
            helper.assertTrue(dolls.subList(1, 3).stream()
                            .allMatch(north -> Math.abs(Mth.wrapDegrees(north.getYRot())) > 179.0F),
                    "a doll set down south of its soul does not face it");
        });
        Tenshintai doll = Tenshintai.standing(ichigo.getUUID());
        BankaiLogic.forget(SoulLogic.get(owners.get(0)));
        BankaiLogic.reach(SoulLogic.get(owners.get(1)), Component.literal("Tensa Zangetsu"), now);
        owners.get(2).discard();
        helper.assertTrue(dolls.stream().noneMatch(Entity::isRemoved), "a doll went before its own tick");
        for (String key : List.of(SET_DOWN, STANDS, NO_SPOT, NO_POWER, REACHED, "tooltip.bleach.tenshintai",
                ModItems.TENSHINTAI.get().getDescriptionId(), ModEntities.TENSHINTAI.get().getDescriptionId())) {
            helper.assertTrue(Language.getInstance().has(key), key + " has no words");
        }
        // Once the others have had their tick, Ichigo's doll, still named, has had its own and stands.
        helper.succeedWhen(() -> helper.assertTrue(dolls.stream().allMatch(Entity::isRemoved)
                        && owners.stream().allMatch(soul -> Tenshintai.standing(soul.getUUID()) == null)
                        && doll != null && !doll.isRemoved() && doll.tickCount > 0
                        && Tenshintai.standing(ichigo.getUUID()) == doll,
                "a doll its soul's record no longer names, or whose soul is gone, still stands, or one it names fell"));
    }

    /**
     * K2. A doll and its window outlast its soul's logging out. The bout by the doll breaks off as the soul goes, and the
     * doll goes with the player object; the soul saved and read back into a new player object has its record as it was,
     * window and all, and its own blade, and its tick stands the doll again where the record says, once, facing it, the
     * window untouched, but not where no chunk ticks; struck again, the doll begins a bout in the same window, the try
     * counted twice. The doll's type is never saved, so a restart leaves the record alone to stand it again.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void aDollStandsAgainAfterItsSoulsLogout(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.wielder(helper, BankaiTrialTests.MIDDLE);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player away = BankaiSpiritTests.wielder(helper, BankaiTrialTests.FLOOR);
        Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
        clearedAtEnd(helper, List.of(ichigo, away, loaded));
        List<Tenshintai> first = new ArrayList<>(1);
        List<BankaiRecord> saved = new ArrayList<>(1);
        helper.startSequence()
                .thenExecute(() -> plain(() -> {
                    Tenshintai doll = setDown(helper, ichigo, DOLL);
                    first.add(doll);
                    release(helper, ichigo);
                    ichigo.attack(doll);
                    saved.add(BankaiTrialTests.record(ichigo));
                    helper.assertTrue(BankaiTrial.trial(ichigo.getUUID()) != null && saved.get(0).tries() == 1,
                            "the strike began no bout by the doll, its record " + saved.get(0));
                    // A logout: its event, then the player taken out of its world (mc PlayerList.java:349-362).
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(ichigo));
                    ichigo.discard();
                    loaded.load(ichigo.saveWithoutId(new CompoundTag()));
                    // Reading takes a mock's own profile's UUID (mc Player.java:765); a login reads the soul's own.
                    loaded.setUUID(ichigo.getUUID());
                    helper.assertTrue(BankaiTrial.trial(ichigo.getUUID()) == null && saved.get(0).windowEndsAt() > now
                                    && BankaiTrialTests.record(loaded).equals(saved.get(0))
                                    && ZanpakutoLogic.isOwnBlade(loaded.getMainHandItem(), loaded)
                                    && !ModEntities.TENSHINTAI.get().canSerialize(),
                            "the logout left the bout up, or the soul read back has " + BankaiTrialTests.record(loaded)
                                    + ", not " + saved.get(0) + ", or another's blade, or the doll is saved");
                    SoulData far = SoulLogic.get(away);
                    GlobalPos nowhere = GlobalPos.of(level.dimension(), new BlockPos(30_000, 70, 30_000));
                    far.setZanpakuto(far.zanpakuto().withBankai(far.zanpakuto().bankai()
                            .dollAt(nowhere, now + Tenshintai.windowTicks())));
                    Tenshintai.keep(away, far);
                    helper.assertTrue(Tenshintai.standing(away.getUUID()) == null
                                    && BankaiTrialTests.record(away).doll().equals(Optional.of(nowhere)),
                            "a doll stood where no chunk ticks, or its window closed while open");
                }))
                .thenWaitUntil(() -> helper.assertTrue(first.get(0).isRemoved()
                        && Tenshintai.standing(ichigo.getUUID()) == null, "the doll outlived its soul's logout"))
                .thenExecute(() -> plain(() -> {
                    Tenshintai.keep(loaded, SoulLogic.get(loaded));
                    Tenshintai again = Tenshintai.standing(loaded.getUUID());
                    Tenshintai.keep(loaded, SoulLogic.get(loaded));
                    List<Tenshintai> there = dollsAt(helper, DOLL);
                    helper.assertTrue(again != null && Tenshintai.standing(loaded.getUUID()) == again
                                    && there.equals(List.of(again)) && facesMiddle(helper, again, DOLL)
                                    && BankaiTrialTests.record(loaded).equals(saved.get(0)),
                            "the soul read back had " + there + " stood again for its doll, and its record reads "
                                    + BankaiTrialTests.record(loaded));
                    // Released, as a soul would after a logout that sealed its blade, and struck.
                    release(helper, loaded);
                    loaded.attack(again);
                    BankaiRecord after = BankaiTrialTests.record(loaded);
                    helper.assertTrue(BankaiTrial.trial(loaded.getUUID()) != null
                                    && after.windowEndsAt() == saved.get(0).windowEndsAt() && after.tries() == 2,
                            "struck after the relog, the doll came to " + after);
                    clear(List.of(loaded));
                }))
                .thenSucceed();
    }

    /**
     * K4. A joined soul's own ticks keep its doll for its window and then let it go. Set down, the doll's window lasts
     * {@code window_days} game days, and the soul is told so. Gone as with its chunk, the doll its record names stands
     * again by the soul's own tick, facing it, the window untouched; once the window is over, the soul's tick closes it:
     * the record forgets the doll, the soul is told in chat, and the doll crumbles.
     */
    @GameTest(template = "field", timeoutTicks = 120)
    public static void aDollStandsByItsSoulsTicksUntilItsWindowEnds(GameTestHelper helper) {
        ServerPlayer ichigo = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), true);
        BankaiSpiritTests.stand(helper, ichigo, BankaiTrialTests.MIDDLE);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        clearedAtEnd(helper, List.of(ichigo));
        long now = HollowPlayerTests.now(helper);
        List<Tenshintai> dolls = new ArrayList<>(2);
        plain(() -> KidoTests.withConfig(BleachConfig.BANKAI_TENSHINTAI_WINDOW_DAYS, 1,
                () -> dolls.add(setDown(helper, ichigo, DOLL))));
        BankaiRecord set = BankaiTrialTests.record(ichigo);
        helper.assertTrue(set.windowEndsAt() == now + 24_000L && set.doll().equals(Optional.of(place(helper, DOLL)))
                        && TestPlayers.chat(ichigo).stream().anyMatch(line -> says(line, SET_DOWN, 1)),
                "set down with a window of one game day, the record reads " + set + ", or the soul was not told so");
        // Its doll goes as its chunk's unloading would take it.
        dolls.get(0).discard();
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, Tenshintai.standing(ichigo.getUUID()) != null,
                        "the soul's own tick never stood its doll again"))
                .thenExecute(() -> {
                    Tenshintai again = Tenshintai.standing(ichigo.getUUID());
                    helper.assertTrue(dollsAt(helper, DOLL).equals(List.of(again)) && facesMiddle(helper, again, DOLL)
                                    && BankaiTrialTests.record(ichigo).equals(set),
                            "the soul's tick stood " + dollsAt(helper, DOLL) + " again, its record "
                                    + BankaiTrialTests.record(ichigo));
                    dolls.add(again);
                    // Its window nearly over: it ends 30 ticks on.
                    SoulData data = SoulLogic.get(ichigo);
                    data.setZanpakuto(data.zanpakuto().withBankai(set.dollAt(place(helper, DOLL),
                            HollowPlayerTests.now(helper) + 30L)));
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, dolls.get(1).isRemoved()
                                && Tenshintai.standing(ichigo.getUUID()) == null,
                        "the doll still stood as its window ended, its record " + BankaiTrialTests.record(ichigo)))
                .thenExecute(() -> helper.assertTrue(BankaiTrialTests.record(ichigo).equals(set.closed())
                                && TestPlayers.chat(ichigo).stream().anyMatch(line -> GateTests.says(line, CRUMBLED, -1))
                                && Language.getInstance().has(CRUMBLED),
                        "its window over, the soul's record reads " + BankaiTrialTests.record(ichigo)
                                + ", or the soul was not told its doll crumbled"))
                .thenSucceed();
    }

    /**
     * K3. Struck with its soul's blade sealed, or by another soul, the doll begins nothing, and the soul is told why.
     * Its soul's screen shows the days and hours its window has left, rounded up to the hour, and where it stands, or
     * that it stands in another world, then where to heal; a soul with no doll has no such lines. Struck with the blade
     * released, the spirit comes out for a bout about the doll, its 300 seconds well inside the
     * window, which the strike leaves as it was; in the bout a second doll is refused, the soul told so as the trial
     * refuses it, and a second strike begins nothing. A bout struck near the window's end ends with the window, and a
     * window over waits for the bout under way, then closes at the next strike, its soul told the doll crumbled.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aDollStruckDrawsTheSpiritOutForABoutByIt(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ServerPlayer ichigo = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), true);
        BankaiSpiritTests.stand(helper, ichigo, BankaiTrialTests.MIDDLE);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player other = BankaiSpiritTests.wielder(helper, BankaiTrialTests.FLOOR);
        release(helper, other);
        List<Player> souls = List.of(ichigo, other);
        plain(() -> HihioZabimaruTests.clearingOnFailure(() -> clear(souls), () -> {
            Tenshintai doll = setDown(helper, ichigo, DOLL);
            BankaiRecord set = BankaiTrialTests.record(ichigo);
            ichigo.attack(doll);
            other.attack(doll);
            helper.assertTrue(BankaiTrial.trial(ichigo.getUUID()) == null && BankaiTrial.trial(other.getUUID()) == null
                            && BankaiTrialTests.record(ichigo).equals(set) && BankaiSpiritTests.told(ichigo, UNRELEASED),
                    "a strike with the blade sealed, or by another soul, began a bout, or the soul was not told why");
            // An hour is 1,000 ticks: the window's whole days as it opens, and one hour with 999 ticks left.
            ResourceKey<Level> here = helper.getLevel().dimension();
            BlockPos at = helper.absolutePos(DOLL);
            Component there = Component.translatable(AT, at.getX(), at.getY(), at.getZ());
            Component away = Component.translatable(AWAY), spring = Component.translatable(SPRING);
            long days = Tenshintai.windowDays();
            SoulData soul = SoulLogic.get(ichigo);
            helper.assertTrue(BankaiLogic.dollLines(soul, now, here)
                            .equals(List.of(Component.translatable(WINDOW, days, 0L, there), spring))
                            && BankaiLogic.dollLines(soul, set.windowEndsAt() - 999L, here)
                                    .equals(List.of(Component.translatable(WINDOW, 0L, 1L, there), spring))
                            && BankaiLogic.dollLines(soul, now, Level.NETHER)
                                    .equals(List.of(Component.translatable(WINDOW, days, 0L, away), spring))
                            && BankaiLogic.dollLines(SoulLogic.get(other), now, here).isEmpty()
                            && List.of(WINDOW, AT, AWAY, SPRING).stream().allMatch(Language.getInstance()::has),
                    "set down, the soul screen reads " + BankaiLogic.dollLines(soul, now, here));

            release(helper, ichigo);
            ichigo.attack(doll);
            BankaiTrial.Trial trial = BankaiTrial.trial(ichigo.getUUID());
            BankaiRecord struck = BankaiTrialTests.record(ichigo);
            helper.assertTrue(trial != null && trial.centre().equals(doll.position()) && trial.endsAt() == now + 6_000L
                            && trial.spirit() == Materialisation.of(ichigo.getUUID())
                            && struck.windowEndsAt() == set.windowEndsAt() && struck.doll().equals(set.doll())
                            && struck.tries() == 1 && struck.lastTryAt() == now,
                    "the strike came to " + trial + ", and the record to " + struck);
            ItemStack second = new ItemStack(ModItems.TENSHINTAI.get());
            InteractionResult refused = useOn(helper, ichigo, second, new BlockPos(6, 1, 8), Direction.UP);
            helper.assertTrue(refused == InteractionResult.FAIL && BankaiSpiritTests.told(ichigo, ALREADY),
                    "in the bout a second doll came to " + refused + ", or the soul was not told it fights already");
            ichigo.attack(doll);
            helper.assertTrue(BankaiTrial.trial(ichigo.getUUID()) == trial && BankaiTrialTests.record(ichigo).equals(struck),
                    "a strike in the bout began another, or wrote the record");

            // The window a hundred ticks from its end: the next bout ends with it, and leaves it as it was.
            BankaiTrialTests.clear(ichigo);
            SoulData data = SoulLogic.get(ichigo);
            data.setZanpakuto(data.zanpakuto().withBankai(struck.dollAt(place(helper, DOLL), now + 100L)));
            ichigo.attack(doll);
            BankaiTrial.Trial bounded = BankaiTrial.trial(ichigo.getUUID());
            BankaiRecord near = BankaiTrialTests.record(ichigo);
            helper.assertTrue(bounded != null && bounded.endsAt() == now + 100L && near.windowEndsAt() == now + 100L
                            && near.tries() == 2
                            && TestPlayers.chat(ichigo).stream().anyMatch(line -> says(line, BankaiTrialTests.BEGUN, 5L)),
                    "a bout struck near the window's end came to " + bounded + ", the record " + near
                            + ", or the soul was not told its five seconds");

            // Then the window over under that bout: it waits for the bout to end, and the next strike closes it.
            data.setZanpakuto(data.zanpakuto().withBankai(near.dollAt(place(helper, DOLL), now)));
            ichigo.attack(doll);
            boolean waited = BankaiTrial.trial(ichigo.getUUID()) == bounded
                    && BankaiTrialTests.record(ichigo).doll().isPresent();
            BankaiTrialTests.clear(ichigo);
            ichigo.attack(doll);
            BankaiRecord closed = BankaiTrialTests.record(ichigo);
            helper.assertTrue(waited && BankaiTrial.trial(ichigo.getUUID()) == null && closed.doll().isEmpty()
                            && closed.windowEndsAt() == ZanpakutoBond.NEVER && closed.tries() == 2 && !doll.isRemoved()
                            && TestPlayers.chat(ichigo).stream().anyMatch(line -> GateTests.says(line, CRUMBLED, -1)),
                    "the window over came to " + closed + ", the bout waited for " + waited + ", or the soul was not told");
            clear(souls);
        }));
        helper.succeed();
    }

    /**
     * K5. A doll is refused, nothing written, where no bout could begin by it, each with its line: to a soul in creative
     * mode, one whose power is lent away, one in a bout already and one whose doll's ring would meet another bout's;
     * and to Zangetsu's soul where no ground lies about the doll for his blades, on a spot that would do. Every refusal
     * of the trial's has a line of the doll's.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aDollIsRefusedWhereNoBoutCouldBeginByIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.wielder(helper, BankaiTrialTests.MIDDLE);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, SoulLogic.get(ichigo).copy());
        Player lent = BankaiSpiritTests.wielder(helper, BankaiTrialTests.FLOOR);
        SoulLogic.get(lent).setShinigami(SoulLogic.get(lent).shinigami().withPowerLentUntil(now + 1_200L));
        Player far = BankaiTrialTests.farWielder(helper);
        List<Player> souls = List.of(ichigo, creative, lent, far);
        List<BlockPos> pillar = List.of(new BlockPos(3, 2, 8), new BlockPos(3, 3, 8), new BlockPos(3, 4, 8));
        Runnable clear = () -> {
            clear(souls);
            pillar.forEach(block -> helper.setBlock(block, Blocks.AIR));
        };
        HihioZabimaruTests.clearingOnFailure(clear, () -> {
            plain(() -> {
                // A bout by command at the far corner: its soul is in it, and a ring about the doll would meet its ring.
                BankaiTrialTests.begun(helper, far, now);
                Map<Player, String> refused = new LinkedHashMap<>();
                refused.put(creative, WATCHING);
                refused.put(lent, NO_POWER);
                refused.put(far, ALREADY);
                refused.put(ichigo, CROWDED);
                refused.forEach((soul, line) -> {
                    String said = Tenshintai.setDown(level, soul, helper.absolutePos(DOLL));
                    helper.assertTrue(line.equals(said) && Tenshintai.standing(soul.getUUID()) == null
                                    && BankaiTrialTests.record(soul).doll().isEmpty(),
                            "a soul to be told " + line + " was told " + said + ", or its doll stood");
                });
                BankaiTrialTests.clear(far);
            });
            // Zangetsu's soul with spirit_tests on: a doll on a pillar three blocks high has no ground about it.
            pillar.forEach(block -> helper.setBlock(block, Blocks.STONE));
            String said = Tenshintai.setDown(level, ichigo, helper.absolutePos(new BlockPos(3, 5, 8)));
            helper.assertTrue(NO_GROUND.equals(said) && Tenshintai.standing(ichigo.getUUID()) == null,
                    "a doll where no ground lies for Zangetsu's blades was told " + said);
            for (BankaiTrial.Start start : BankaiTrial.Start.values()) {
                helper.assertTrue(start == BankaiTrial.Start.STARTED
                                || Language.getInstance().has(Tenshintai.refusalKey(start)),
                        "the doll's refusal " + start + " has no line");
            }
            for (String key : List.of(NOT_YOURS, UNRELEASED)) {
                helper.assertTrue(Language.getInstance().has(key), key + " has no words");
            }
            clear.run();
        });
        helper.succeed();
    }

    /**
     * K6. A spirit fought by the doll keeps its wounds from one bout by it to the next: the share of its health it had
     * as a bout by the doll ran out or broke off (a logout among them) it has as the next begins, never below its floor,
     * so one killed comes back at its floor, unbeaten. A bout broken off by a new player object for the soul, as a
     * respawn makes, keeps them on that object's record, not the old one's. A command bout begins whole and keeps
     * nothing, and a win leaves no wounds, window or doll on the record, and the doll crumbles. Zabimaru keeps the mean
     * of its two halves, and both begin at it.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void aSecondBoutFindsItsWoundsKept(GameTestHelper helper) {
        DamageSources sources = helper.getLevel().damageSources();
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.wielder(helper, BankaiTrialTests.MIDDLE);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player renji = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        BankaiSpiritTests.stand(helper, renji, BankaiTrialTests.MIDDLE);
        Player respawned = helper.makeMockPlayer(GameType.SURVIVAL);
        clearedAtEnd(helper, List.of(ichigo, renji, respawned));
        float floorShare = (float) BleachConfig.get(BleachConfig.BANKAI_SPIRIT_FLOOR_SHARE);
        List<Tenshintai> dolls = new ArrayList<>(1);
        plain(() -> {
            Tenshintai doll = setDown(helper, ichigo, DOLL);
            dolls.add(doll);
            release(helper, ichigo);
            MaterialisedSpirit first = struck(helper, ichigo, doll);
            helper.assertTrue(first.getHealth() == first.getMaxHealth()
                    && BankaiTrialTests.record(ichigo).spiritHealth() == 1.0F, "the first bout's spirit was not whole");
            first.setHealth(0.6F * first.getMaxHealth());
            runOut(ichigo);
            BankaiRecord timed = BankaiTrialTests.record(ichigo);
            MaterialisedSpirit second = struck(helper, ichigo, doll);
            helper.assertTrue(Math.abs(timed.spiritHealth() - 0.6F) < 1.0E-4F
                            && Math.abs(second.getHealth() - 0.6F * second.getMaxHealth()) < 1.0E-3F,
                    "a time-out kept " + timed.spiritHealth() + ", and the next bout began at " + second.getHealth());
            second.setHealth(0.3F * second.getMaxHealth());
            // A logout's event, its soul kept, so the doll stands on; it seals the release, so the soul releases again.
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(ichigo));
            BankaiRecord left = BankaiTrialTests.record(ichigo);
            release(helper, ichigo);
            MaterialisedSpirit third = struck(helper, ichigo, doll);
            helper.assertTrue(Math.abs(left.spiritHealth() - 0.3F) < 1.0E-4F
                            && Math.abs(third.getHealth() - 0.3F * third.getMaxHealth()) < 1.0E-3F,
                    "a logout kept " + left.spiritHealth() + ", and the next bout began at " + third.getHealth());
            third.setHealth(0.45F * third.getMaxHealth());
            // A new player object with the soul's UUID and a copy of its record, as K2 reads one back, ticks.
            respawned.load(ichigo.saveWithoutId(new CompoundTag()));
            respawned.setUUID(ichigo.getUUID());
            BankaiTrial.tick(respawned, SoulLogic.get(respawned), now);
            float moved = BankaiTrialTests.record(respawned).spiritHealth();
            helper.assertTrue(BankaiTrial.trial(ichigo.getUUID()) == null && Math.abs(moved - 0.45F) < 1.0E-4F
                            && BankaiTrialTests.record(ichigo).spiritHealth() == left.spiritHealth(),
                    "broken off by a new player object, the bout kept " + moved + " on it and "
                            + BankaiTrialTests.record(ichigo).spiritHealth() + " on the old one");
            // Killed, as /kill leaves it: it reads 0 and comes back at its floor.
            MaterialisedSpirit fourth = struck(helper, ichigo, doll);
            fourth.setHealth(0.0F);
            runOut(ichigo);
            helper.assertTrue(BankaiTrialTests.record(ichigo).spiritHealth() == floorShare
                    && BankaiTrial.keptShare(0.0F) == floorShare, "a spirit killed kept less than its floor");
            BankaiTrial.Trial command = BankaiTrialTests.begun(helper, ichigo, now);
            helper.assertTrue(!command.byDoll() && command.spirit().getHealth() == command.spirit().getMaxHealth()
                    && command.centre().equals(ichigo.position()), "the command's bout read the doll's wounds");
            command.spirit().setHealth(0.5F * command.spirit().getMaxHealth());
            BankaiTrialTests.clear(ichigo);
            MaterialisedSpirit last = struck(helper, ichigo, doll);
            helper.assertTrue(BankaiTrialTests.record(ichigo).spiritHealth() == floorShare
                            && Math.abs(last.getHealth() - last.floor()) < 1.0E-3F && !last.beaten(),
                    "after a command bout the doll's spirit began at " + last.getHealth() + ", or beaten");
            helper.assertTrue(BankaiTrial.yields(last), "the doll's spirit did not yield");
            BankaiRecord won = BankaiTrialTests.record(ichigo);
            helper.assertTrue(won.achieved() && won.windowEndsAt() == ZanpakutoBond.NEVER && won.doll().isEmpty()
                    && won.spiritHealth() == 1.0F, "the win left the record " + won);
        });
        // Zabimaru, its halves split: the snake beaten down, the baboon whole.
        KidoTests.withConfig(BleachConfig.BANKAI_TRIAL_SPIRIT_TESTS, true, () -> {
            Tenshintai doll = setDown(helper, renji, new BlockPos(8, 2, 5));
            ZanpakutoItem.setReleased(renji.getMainHandItem(), ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU),
                    true);
            ZabimaruSpirit first = (ZabimaruSpirit) struck(helper, renji, doll);
            float mean = (first.getMaxHealth() + first.floor()) / (2.0F * first.getMaxHealth());
            first.snake().hurt(sources.playerAttack(renji), 10_000.0F);
            helper.assertTrue(first.split() && first.down(ZabimaruSpirit.Half.SNAKE), "the snake was not beaten down");
            runOut(renji);
            float kept = BankaiTrialTests.record(renji).spiritHealth();
            ZabimaruSpirit next = (ZabimaruSpirit) struck(helper, renji, doll);
            float share = kept * next.getMaxHealth();
            helper.assertTrue(Math.abs(kept - mean) < 1.0E-4F && next.split()
                            && Math.abs(next.health(ZabimaruSpirit.Half.BABOON) - share) < 1.0E-3F
                            && Math.abs(next.health(ZabimaruSpirit.Half.SNAKE) - share) < 1.0E-3F && !next.beaten()
                            && !next.down(ZabimaruSpirit.Half.SNAKE) && !next.down(ZabimaruSpirit.Half.BABOON),
                    "Zabimaru kept " + kept + ", not " + mean + ", or its halves began at "
                            + next.health(ZabimaruSpirit.Half.BABOON) + " and " + next.health(ZabimaruSpirit.Half.SNAKE));
            BankaiTrialTests.clear(renji);
        });
        helper.succeedWhen(() -> helper.assertTrue(dolls.get(0).isRemoved()
                && Tenshintai.standing(ichigo.getUUID()) == null, "the doll outlived its spirit's yielding"));
    }
}
