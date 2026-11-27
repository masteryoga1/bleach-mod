package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
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
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Tenshintai (8k1): the doll set down, refused and crumbling. Each test asks only about its own souls and
 * dolls, and every doll a test set down is taken away however it ends, by its end's clean-up. A mock soul is told
 * nothing, so a line is asked of a joined soul.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class TenshintaiTests {
    /** Where a test's doll stands: a block north of the field's middle, where its soul stands. */
    static final BlockPos DOLL = new BlockPos(8, 2, 7);
    static final String SET_DOWN = "message.bleach.tenshintai.set_down", STANDS = "message.bleach.tenshintai.stands";
    static final String NO_SPOT = "message.bleach.tenshintai.no_spot", NO_POWER = "message.bleach.tenshintai.no_power";
    static final String REACHED = "message.bleach.tenshintai.reached";

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
}
