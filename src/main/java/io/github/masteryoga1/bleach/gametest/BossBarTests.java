package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.BossBar;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for {@link BossBar}, on a still pig standing in for a boss: who is shown the bar, how it follows the pig's health
 * on the tick, its restyle and hidden state, and its coming down when the pig dies or unloads. Each test ticks the bar
 * itself, as a boss would, so a test's ticks are the bar's and no game tick need pass. Other tests' players may stand
 * within a bar's reach, so each test asks only about its own players, and by object: a respawned player equals its old
 * self (mc Entity.java:341-348).
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BossBarTests {
    private static final Vec3 SPOT = new Vec3(1.5, 1.0, 1.5);
    private static final String KEY = "bossbar.bleach.menos_grande";
    private static final float AWARE = 40.0F;
    private static final float UNAWARE = 10.0F;

    private BossBarTests() {
    }

    private static Pig stand(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.PIG, 1, 1, 1);
    }

    private static BossBar bar(Pig stand) {
        return new BossBar(stand, KEY, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    }

    /** A player joined at the spot in this mode, with a Human soul of this much reiryoku. */
    private static ServerPlayer soul(GameTestHelper helper, GameType mode, float reiryoku) {
        ServerPlayer player = TestPlayers.join(helper, mode, SPOT);
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, reiryoku));
        return player;
    }

    /** Whether the bar is shown to this very player object. */
    private static boolean holds(BossBar bar, ServerPlayer player) {
        return bar.players().stream().anyMatch(shown -> shown == player);
    }

    private static void ticks(BossBar bar, int count) {
        for (int i = 0; i < count; i++) {
            bar.tick();
        }
    }

    @GameTest(template = "empty")
    public static void aBossBarShowsToTheAwareOnly(GameTestHelper helper) {
        Pig stand = stand(helper);
        BossBar bar = bar(stand);
        ServerPlayer aware = soul(helper, GameType.SURVIVAL, AWARE);
        ServerPlayer creative = soul(helper, GameType.CREATIVE, UNAWARE);
        ServerPlayer unaware = soul(helper, GameType.SURVIVAL, UNAWARE);
        ServerPlayer spectator = soul(helper, GameType.SPECTATOR, AWARE);
        try {
            bar.tick();
            helper.assertTrue(holds(bar, aware) && holds(bar, creative),
                    "an aware soul or a creative player was not shown the bar on its first tick");
            helper.assertFalse(holds(bar, unaware) || holds(bar, spectator),
                    "an unaware soul or a spectator was shown the bar");
        } finally {
            stand.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBossBarLeavesTheOneWhoGoes(GameTestHelper helper) {
        Pig stand = stand(helper);
        BossBar bar = bar(stand);
        ServerPlayer stays = soul(helper, GameType.SURVIVAL, AWARE);
        ServerPlayer goes = soul(helper, GameType.SURVIVAL, AWARE);
        try {
            bar.tick();
            helper.assertTrue(holds(bar, stays) && holds(bar, goes),
                    "two aware souls by the boss were not both shown its bar");
            // Straight up and out of reach: the same chunks, so nothing need load where it goes.
            Vec3 far = stand.position().add(0.0, BossBar.RANGE + 6.0, 0.0);
            goes.teleportTo(helper.getLevel(), far.x, far.y, far.z, 0.0F, 0.0F);
            ticks(bar, BossBar.REFRESH_TICKS);
            helper.assertTrue(holds(bar, stays) && !holds(bar, goes),
                    "within a second, the soul out of reach kept the bar or the one by it lost it");
            bar.forget(stays);
            helper.assertFalse(holds(bar, stays), "a soul that stopped seeing the boss kept its bar");
        } finally {
            stand.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBossBarFindsARespawnedSoul(GameTestHelper helper) {
        Pig stand = stand(helper);
        BossBar bar = bar(stand);
        ServerPlayer old = soul(helper, GameType.SURVIVAL, AWARE);
        try {
            bar.tick();
            // As leaving the End does (mc ServerGamePacketListenerImpl.java:1573): a new player object for the same soul
            // and connection, while the old one stays wherever it was held.
            ServerPlayer fresh = helper.getLevel().getServer().getPlayerList()
                    .respawn(old, true, Entity.RemovalReason.CHANGED_DIMENSION);
            old.connection.player = fresh;
            fresh.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, AWARE));
            Vec3 at = helper.absoluteVec(SPOT);
            fresh.teleportTo(helper.getLevel(), at.x, at.y, at.z, 0.0F, 0.0F);
            helper.assertTrue(fresh != old && fresh.getUUID().equals(old.getUUID()) && holds(bar, old) && !holds(bar, fresh),
                    "the respawn did not make a new player for the same soul, or the bar changed hands unasked");
            bar.refresh();
            helper.assertTrue(holds(bar, fresh) && !holds(bar, old),
                    "the respawned soul was not shown the bar afresh, or its old self kept it");
        } finally {
            stand.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBossBarFollowsBlowsAndHeals(GameTestHelper helper) {
        Pig stand = stand(helper);
        BossBar bar = bar(stand);
        ServerPlayer aware = soul(helper, GameType.SURVIVAL, AWARE);
        try {
            bar.tick();
            // A pig has 10 health: a blow of 4 leaves 0.6 of it, and a heal of 2 brings it to 0.8, each on the next tick.
            stand.hurt(helper.getLevel().damageSources().generic(), 4.0F);
            bar.tick();
            float struck = bar.event().getProgress();
            stand.heal(2.0F);
            bar.tick();
            float healed = bar.event().getProgress();
            helper.assertTrue(Math.abs(struck - 0.6F) < 1.0E-4F && Math.abs(healed - 0.8F) < 1.0E-4F,
                    "the bar read " + struck + " after the blow and " + healed + " after the heal, not 0.6 and 0.8");
            helper.assertTrue(holds(bar, aware), "the aware soul lost the bar to a blow or a heal");
            stand.kill();
            bar.tick();
            helper.assertTrue(!stand.isAlive() && bar.players().isEmpty(), "the bar stayed up as its boss died");
        } finally {
            stand.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aRestyledBossBarStaysOne(GameTestHelper helper) {
        Pig stand = stand(helper);
        BossBar bar = bar(stand);
        ServerPlayer aware = soul(helper, GameType.SURVIVAL, AWARE);
        try {
            bar.tick();
            UUID id = bar.event().getId();
            bar.restyle("bossbar.bleach.finale", BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
            bar.tick();
            helper.assertTrue(bar.event().getId().equals(id) && holds(bar, aware),
                    "restyled, the bar was made anew or lost its viewer");
            helper.assertTrue(bar.event().getName().equals(Component.translatable("bossbar.bleach.finale"))
                            && bar.event().getColor() == BossEvent.BossBarColor.PURPLE
                            && bar.event().getOverlay() == BossEvent.BossBarOverlay.NOTCHED_10,
                    "the restyle did not take: " + bar.event().getName() + ", " + bar.event().getColor() + ", "
                            + bar.event().getOverlay());
        } finally {
            stand.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aHiddenBossBarShowsNoOne(GameTestHelper helper) {
        Pig stand = stand(helper);
        BossBar bar = bar(stand);
        ServerPlayer aware = soul(helper, GameType.SURVIVAL, AWARE);
        try {
            bar.setHidden(true);
            ticks(bar, 2 * BossBar.REFRESH_TICKS);
            helper.assertTrue(bar.hidden() && bar.players().isEmpty(), "a hidden bar was shown over two seconds");
            bar.setHidden(false);
            helper.assertTrue(holds(bar, aware), "shown again, the bar did not reach the aware soul at once");
        } finally {
            stand.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anUnloadedBossTakesItsBarDown(GameTestHelper helper) {
        Pig stand = stand(helper);
        BossBar bar = bar(stand);
        ServerPlayer aware = soul(helper, GameType.SURVIVAL, AWARE);
        try {
            bar.tick();
            helper.assertTrue(holds(bar, aware), "the aware soul was not shown the bar");
            // As an unloading chunk takes it (mc PersistentEntitySectionManager.java:219-222): no remove, no die, and
            // the bar not ticked again, so only the leave event can take the bar down.
            stand.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(bar.players().isEmpty(), "the bar stayed up when its boss unloaded");
        } finally {
            stand.discard();
        }
        helper.succeed();
    }
}
