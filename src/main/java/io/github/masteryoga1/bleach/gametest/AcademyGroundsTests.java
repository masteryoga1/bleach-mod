package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.TrainingDummy;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarNpcLogic;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.DummyDrill;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Academy's training dummies: how a blow on one trains, up to the day's cap, and that no harm ends one or moves
 * it, though {@code /kill} takes it away.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class AcademyGroundsTests {
    private AcademyGroundsTests() {
    }

    // ---- Helpers ----

    /** A student joined in survival with this in hand, and a dummy beside it. */
    private static TrainingDummy dummyBy(GameTestHelper helper, ServerPlayer player, ItemStack held) {
        player.setData(SoulAttachments.SOUL, ShinigamiTests.student(40.0F));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return helper.spawn(ModEntities.TRAINING_DUMMY.get(), new Vec3(2.5, 1.0, 1.5));
    }

    /** One blow of this player's that lands on the target, past its frames and the swing's own wait. */
    private static void blow(ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), 4.0F);
        player.tickCount += 10;
    }

    // ---- Training ----

    /**
     * Test 2. A student's sword blows on a dummy train Zanjutsu a quarter of a Hollow hit's xp each, one a swing's wait,
     * up to the day's cap, of which it is told once in chat; the next day's blows train again, and an empty drill, as a
     * save from before the dummies has, reads as none.
     */
    @GameTest(template = "empty")
    public static void aDummyTrainsUpToTheDaysCap(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        TrainingDummy dummy = dummyBy(helper, player, new ItemStack(Items.IRON_SWORD));
        try {
            KidoTests.withConfig(BleachConfig.HOLLOW_HIT_STAT_XP, 2, () -> KidoTests.withConfig(
                    BleachConfig.DUMMY_XP_PER_DAY, 3, () -> {
                SoulData data = SoulLogic.get(player);
                List<Integer> seen = new ArrayList<>();
                blow(player, dummy);
                player.tickCount -= 10;
                blow(player, dummy);
                seen.add(data.xp(Stat.ZANJUTSU));
                for (int i = 0; i < 7; i++) {
                    blow(player, dummy);
                    seen.add(data.xp(Stat.ZANJUTSU));
                }
                helper.assertTrue(seen.equals(List.of(0, 1, 1, 2, 2, 3, 3, 3)), "a quarter a blow gave " + seen);
                helper.assertTrue(TestPlayers.chat(player).stream().filter(line -> line.getContents() instanceof
                        TranslatableContents said && said.getKey().equals("message.bleach.training_dummy.spent"))
                        .count() == 1, "the student was not told once that the dummies were spent");
                DummyDrill drill = player.getData(SoulAttachments.DUMMY_DRILL);
                player.setData(SoulAttachments.DUMMY_DRILL, new DummyDrill(drill.day() - 1, drill.today(), 0));
                blow(player, dummy);
                blow(player, dummy);
                helper.assertTrue(data.xp(Stat.ZANJUTSU) == 4, "the next day's two blows gave "
                        + (data.xp(Stat.ZANJUTSU) - 3) + ", not 1");
            }));
            helper.assertTrue(DummyDrill.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).result()
                    .equals(Optional.of(DummyDrill.NONE)), "an empty drill does not read as none");
            helper.succeed();
        } finally {
            dummy.discard();
        }
    }

    /**
     * Test 3. Bare fists on a dummy train Hakuda at the same quarter; a blow on a pig still gives its one; a Hollow's
     * blade on a dummy trains nothing and starts no drill.
     */
    @GameTest(template = "empty")
    public static void fistsTrainHakudaAndAHollowsBladeNothing(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        TrainingDummy dummy = dummyBy(helper, player, ItemStack.EMPTY);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(1.5, 1.0, 2.5));
        try {
            // Pinned, so four blows give one Hollow hit's xp and stay under the day's cap.
            KidoTests.withConfig(BleachConfig.HOLLOW_HIT_STAT_XP, 2, () -> KidoTests.withConfig(
                    BleachConfig.DUMMY_XP_PER_DAY, 80, () -> {
                SoulData data = SoulLogic.get(player);
                for (int i = 0; i < 4; i++) {
                    blow(player, dummy);
                }
                int hollowHit = HollowLogic.hitXp(true, false);
                helper.assertTrue(data.xp(Stat.HAKUDA) == hollowHit && data.xp(Stat.ZANJUTSU) == 0,
                        "four bare blows gave Hakuda " + data.xp(Stat.HAKUDA) + " and Zanjutsu " + data.xp(Stat.ZANJUTSU));
                blow(player, pig);
                helper.assertTrue(data.xp(Stat.HAKUDA) == hollowHit + 1, "a blow on a pig did not give its 1");
                player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 40.0F));
                player.setData(SoulAttachments.DUMMY_DRILL, DummyDrill.NONE);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                for (int i = 0; i < 4; i++) {
                    blow(player, dummy);
                }
                SoulData hollow = SoulLogic.get(player);
                helper.assertTrue(hollow.xp(Stat.ZANJUTSU) == 0 && hollow.xp(Stat.HAKUDA) == 0
                        && player.getData(SoulAttachments.DUMMY_DRILL).equals(DummyDrill.NONE), "a Hollow's blade trained");
            }));
            helper.succeed();
        } finally {
            dummy.discard();
            pig.discard();
        }
    }

    // ---- The dummy itself ----

    /**
     * Test 4. No harm a dummy is dealt fells or moves it, no piston shoves it, and it is no creature's prey or foe; it is
     * saved and read back the same; {@code /kill} takes it away, with no death.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aDummyStandsUntilKilled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrainingDummy dummy = helper.spawn(ModEntities.TRAINING_DUMMY.get(), new Vec3(2.5, 1.0, 2.5));
        Vec3 at = dummy.position();
        float yaw = dummy.getYRot();
        DamageSources sources = level.damageSources();
        for (DamageSource source : List.of(sources.generic(), sources.fall(), sources.explosion(null, null),
                sources.magic(), sources.lava(), sources.wither(), sources.genericKill(), sources.fellOutOfWorld())) {
            dummy.invulnerableTime = 0;
            dummy.hurt(source, 1000.0F);
        }
        dummy.knockback(5.0, 1.0, 0.0);
        dummy.push(2.0, 1.0, 0.0);
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            try {
                helper.assertTrue(dummy.isAlive() && dummy.getHealth() == dummy.getMaxHealth()
                        && dummy.position().equals(at) && dummy.getYRot() == yaw, "the dummy fell or moved: "
                        + dummy.getHealth() + " at " + dummy.position());
                helper.assertTrue(dummy.getPistonPushReaction() == PushReaction.IGNORE, "pistons push the dummy");
                helper.assertFalse(dummy.canUsePortal(false), "the dummy goes through portals");
                helper.assertTrue(!dummy.canBeSeenAsEnemy() && !HollowLogic.isPrey(dummy)
                        && !ArrancarNpcLogic.isPrey(dummy) && DevourLogic.preyOf(dummy) == null, "the dummy can be hunted");
                CompoundTag saved = dummy.saveWithoutId(new CompoundTag());
                TrainingDummy copy = ModEntities.TRAINING_DUMMY.get().create(level);
                copy.load(saved);
                helper.assertTrue(copy.getUUID().equals(dummy.getUUID()) && copy.getYRot() == yaw,
                        "the dummy is not read back as saved");
                MinecraftServer server = level.getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "kill " + dummy.getStringUUID());
                helper.assertTrue(dummy.isRemoved() && dummy.getRemovalReason() == Entity.RemovalReason.KILLED
                        && dummy.deathTime == 0, "/kill did not take the dummy away at once");
            } finally {
                dummy.discard();
            }
        }).thenSucceed();
    }
}
