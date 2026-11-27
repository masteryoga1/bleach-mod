package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Tenshintai;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
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
 * logout too, and crumbling as its window ends. Each test asks only about its own souls and dolls, and every doll a test
 * set down is taken away however it ends, by its end's clean-up. A mock soul is told nothing, so a line is asked of a
 * joined soul.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class TenshintaiTests {
    /** Where a test's doll stands: a block north of the field's middle, where its soul stands. */
    static final BlockPos DOLL = new BlockPos(8, 2, 7);
    static final String SET_DOWN = "message.bleach.tenshintai.set_down", STANDS = "message.bleach.tenshintai.stands";
    static final String NO_SPOT = "message.bleach.tenshintai.no_spot", NO_POWER = "message.bleach.tenshintai.no_power";
    static final String REACHED = "message.bleach.tenshintai.reached", CRUMBLED = "message.bleach.tenshintai.crumbled";

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

    /** Lets these souls' dolls go from their records and takes the dolls away, so none outlives its test. */
    static void clear(List<Player> souls) {
        for (Player soul : souls) {
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

    /** Whether this line says a doll was set down to stand for these game days. */
    static boolean setDownFor(Component line, int days) {
        return line.getContents() instanceof TranslatableContents words && words.getKey().equals(SET_DOWN)
                && words.getArgs().length == 1 && words.getArgs()[0].equals(days);
    }

    /**
     * K1. A named Shinigami's doll, set down from its item on the top of the field's floor, stands there facing it, the
     * item spent and its record naming the spot, and it is told so; used on a block's side, the item does nothing. A
     * soul that may not hold Bankai, or holds it, is refused one wherever it clicks, and so is a spot over air, in stone
     * or in water, and a second doll while the first stands, wherever, the item kept and the soul told; a strike does
     * nothing to the doll. A doll whose soul's record no longer names it, Bankai revoked or reached, crumbles on its next
     * tick, and one whose soul is gone goes.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void aDollSetDownStandsUntilItsRecordForgetsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        ServerPlayer ichigo = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), true);
        BankaiSpiritTests.stand(helper, ichigo, BankaiTrialTests.MIDDLE);
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
                "a second doll came to " + again + " and left " + second + " with one standing, or the soul was not told");
        ichigo.attack(doll);
        other.attack(doll);
        helper.assertTrue(doll.skipAttackInteraction(other) && !doll.isRemoved()
                        && BankaiTrialTests.record(ichigo).equals(set),
                "a strike reached the doll, or moved it or its record");

        // Three more dolls: one whose soul's Bankai is revoked, one whose soul reaches it, and one whose soul goes.
        List<Tenshintai> dolls = new ArrayList<>();
        spots.forEach((soul, spot) -> dolls.add(setDown(helper, soul, spot)));
        // The last two souls stand north of their dolls, so those face north, not the way an unturned doll faces.
        helper.assertTrue(dolls.subList(1, 3).stream().allMatch(north -> Math.abs(Mth.wrapDegrees(north.getYRot())) > 179.0F),
                "a doll set down south of its soul does not face it");
        List<Player> owners = List.copyOf(spots.keySet());
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
                        && !doll.isRemoved() && doll.tickCount > 0 && Tenshintai.standing(ichigo.getUUID()) == doll,
                "a doll its soul's record no longer names, or whose soul is gone, still stands, or one it names fell"));
    }

    /**
     * K2. A doll and its window outlast its soul's logging out. The doll goes with the player object; the soul saved and
     * read back into a new player object has its record as it was, window and all, and its tick stands the doll again
     * where the record says, once, facing it, the window untouched, but not where no chunk ticks. The doll's type is
     * never saved, so a restart leaves the record alone to stand it again.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void aDollStandsAgainAfterItsSoulsLogout(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.wielder(helper, BankaiTrialTests.MIDDLE);
        Player away = BankaiSpiritTests.wielder(helper, BankaiTrialTests.FLOOR);
        Player loaded = helper.makeMockPlayer(GameType.SURVIVAL);
        clearedAtEnd(helper, List.of(ichigo, away, loaded));
        List<Tenshintai> first = new ArrayList<>(1);
        List<BankaiRecord> saved = new ArrayList<>(1);
        helper.startSequence()
                .thenExecute(() -> {
                    first.add(setDown(helper, ichigo, DOLL));
                    saved.add(BankaiTrialTests.record(ichigo));
                    // A logout: its event, then the player taken out of its world (mc PlayerList.java:349-362).
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(ichigo));
                    ichigo.discard();
                    loaded.load(ichigo.saveWithoutId(new CompoundTag()));
                    // Reading takes a mock's own profile's UUID (mc Player.java:765); a login reads the soul's own.
                    loaded.setUUID(ichigo.getUUID());
                    helper.assertTrue(saved.get(0).windowEndsAt() > now
                                    && BankaiTrialTests.record(loaded).equals(saved.get(0))
                                    && !ModEntities.TENSHINTAI.get().canSerialize(),
                            "the soul read back has " + BankaiTrialTests.record(loaded) + ", not " + saved.get(0)
                                    + ", or the doll is saved");
                    SoulData far = SoulLogic.get(away);
                    GlobalPos nowhere = GlobalPos.of(level.dimension(), new BlockPos(30_000, 70, 30_000));
                    far.setZanpakuto(far.zanpakuto().withBankai(far.zanpakuto().bankai()
                            .dollAt(nowhere, now + Tenshintai.windowTicks())));
                    Tenshintai.keep(away, far);
                    helper.assertTrue(Tenshintai.standing(away.getUUID()) == null
                                    && BankaiTrialTests.record(away).doll().equals(Optional.of(nowhere)),
                            "a doll stood where no chunk ticks, or its window closed while open");
                })
                .thenWaitUntil(() -> helper.assertTrue(first.get(0).isRemoved()
                        && Tenshintai.standing(ichigo.getUUID()) == null, "the doll outlived its soul's logout"))
                .thenExecute(() -> {
                    Tenshintai.keep(loaded, SoulLogic.get(loaded));
                    Tenshintai again = Tenshintai.standing(loaded.getUUID());
                    Tenshintai.keep(loaded, SoulLogic.get(loaded));
                    List<Tenshintai> there = dollsAt(helper, DOLL);
                    helper.assertTrue(again != null && Tenshintai.standing(loaded.getUUID()) == again
                                    && there.equals(List.of(again)) && facesMiddle(helper, again, DOLL)
                                    && BankaiTrialTests.record(loaded).equals(saved.get(0)),
                            "the soul read back had " + there + " stood again for its doll, and its record reads "
                                    + BankaiTrialTests.record(loaded));
                })
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
        clearedAtEnd(helper, List.of(ichigo));
        long now = HollowPlayerTests.now(helper);
        List<Tenshintai> dolls = new ArrayList<>(2);
        KidoTests.withConfig(BleachConfig.BANKAI_TENSHINTAI_WINDOW_DAYS, 1, () -> dolls.add(setDown(helper, ichigo, DOLL)));
        BankaiRecord set = BankaiTrialTests.record(ichigo);
        helper.assertTrue(set.windowEndsAt() == now + 24_000L && set.doll().equals(Optional.of(place(helper, DOLL)))
                        && TestPlayers.chat(ichigo).stream().anyMatch(line -> setDownFor(line, 1)),
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
}
