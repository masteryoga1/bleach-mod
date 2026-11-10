package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Visored: the Hollow's record on a save, which souls take it into their wounds and from what, its
 * voice and the soul screen while it waits, the fight with it, which only the host can win and which never
 * kills, and the beaten Hollow's mask: who may call it, what it gives and costs, how long it lasts and grows, the
 * waits after it and every way it comes off. Mock players are never in the world and never tick, so every fight
 * and every mask is driven by calling {@link VisoredEvents#tick} with the time it needs. Every Hollow that steps
 * out is made still, and every fight a test begins and every mask it puts on is ended within that test's first
 * tick, so the server's table of fights is empty whenever another test runs. The test server runs on Normal; on
 * Peaceful, where no Hollow comes out, the parts that need a fight or hurt a mock are skipped.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class VisoredTests {
    private static final long DAY = 24_000L;
    private static final long NEVER = ShinigamiProgress.NEVER;
    /** Where a host stands: the middle of the field, so a Hollow stepping out 4 blocks south of it is in the box. */
    private static final Vec3 HOST_SPOT = new Vec3(8.5, 2.0, 8.5);

    private VisoredTests() {
    }

    // ---- Helpers ----

    /**
     * A mock Shinigami who knows Zangetsu's name, with its Asauchi in hand and this much reiryoku, full, standing in
     * the middle of the field on the ground and facing south.
     */
    private static Player host(GameTestHelper helper, float maxReiryoku) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(player);
        data.setMaxReiryoku(maxReiryoku);
        data.setReiryoku(maxReiryoku);
        stand(helper, player);
        return player;
    }

    /** A mock Shinigami in this game mode, set up as {@link #host} sets one up, with 900 reiryoku. */
    private static Player hostIn(GameTestHelper helper, GameType mode) {
        Player player = helper.makeMockPlayer(mode);
        SoulData data = ZanpakutoTests.soul(SoulState.SHINIGAMI, 900.0F);
        player.setData(SoulAttachments.SOUL, data);
        player.tickCount = 1000;
        player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        ZanpakutoEvents.learnName(player, data, ZanpakutoSpirits.ZANGETSU.location(),
                ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), player.getMainHandItem());
        stand(helper, player);
        return player;
    }

    /** A Visored: a mock Shinigami as {@link #host} makes one, whose Hollow took root a day ago and was beaten a tick ago. */
    private static Player visored(GameTestHelper helper, float maxReiryoku) {
        Player player = host(helper, maxReiryoku);
        beaten(SoulLogic.get(player), HollowPlayerTests.now(helper));
        return player;
    }

    /** A Visored in this game mode, set up as {@link #hostIn} sets one up, with 900 reiryoku. */
    private static Player visoredIn(GameTestHelper helper, GameType mode) {
        Player player = hostIn(helper, mode);
        beaten(SoulLogic.get(player), HollowPlayerTests.now(helper));
        return player;
    }

    /** A Hollow rooted in this soul a day ago and beaten a tick ago, its mask never yet worn. */
    private static void beaten(SoulData data, long now) {
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now - DAY).withSubduedAt(now - 1L));
    }

    /** Puts the player in the middle of the field, on the ground and facing south. */
    private static void stand(GameTestHelper helper, Player player) {
        Vec3 at = helper.absoluteVec(HOST_SPOT);
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.setOnGround(true);
    }

    /** A Hollow rooted in this soul a day ago, ready to be faced now. */
    private static void ready(SoulData data, long now) {
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now - DAY).withDuelReadyAt(now));
    }

    /**
     * Runs the body with this config value set, and puts it back afterwards, so it is changed only within this one
     * tick of the test.
     */
    private static void withConfig(ModConfigSpec.BooleanValue value, boolean set, Runnable body) {
        boolean before = BleachConfig.get(value);
        value.set(set);
        try {
            body.run();
        } finally {
            value.set(before);
        }
    }

    /** Brings out the ready host's Hollow, which must come, and makes it stand still. */
    private static InnerHollow begin(GameTestHelper helper, Player host, long now) {
        VisoredEvents.DuelStart start = VisoredEvents.beginDuel(host, SoulLogic.get(host), now, false);
        InnerHollow hollow = VisoredEvents.duelOf(host.getUUID());
        helper.assertTrue(start == VisoredEvents.DuelStart.STARTED && hollow != null,
                "the ready Hollow did not come out: " + start);
        return QuincyTests.still(hollow);
    }

    /**
     * On Peaceful no Hollow comes out, so all a test of the fight can check there is that; true when it did. Any fight
     * that came out anyway is ended again.
     */
    private static boolean refusedOnPeaceful(GameTestHelper helper, Player host, long now) {
        if (!HollowPlayerTests.peaceful(helper)) {
            return false;
        }
        try {
            VisoredEvents.DuelStart start = VisoredEvents.beginDuel(host, SoulLogic.get(host), now, false);
            helper.assertTrue(start == VisoredEvents.DuelStart.PEACEFUL && !VisoredEvents.isDuelling(host),
                    "a Hollow came out on Peaceful: " + start);
        } finally {
            end(now, host);
        }
        return true;
    }

    /** Ends every fight of these hosts and takes off any mask they wear, so none is left in the table for another test. */
    private static void end(long now, Player... hosts) {
        for (Player host : hosts) {
            VisoredEvents.endAll(host, SoulLogic.get(host), now);
        }
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** The host's fight broke off at {@code at}: no fight, no loss, the next try a retry wait later, the Hollow gone. */
    private static void assertBrokenOff(GameTestHelper helper, Player host, InnerHollow hollow, long at, String what) {
        ShinigamiProgress.Visored record = VisoredLogic.record(SoulLogic.get(host));
        helper.assertFalse(VisoredEvents.isDuelling(host), what + " is still fighting");
        helper.assertTrue(record.losses() == 0, what + " counted a loss");
        helper.assertTrue(record.duelReadyAt() == at + VisoredLogic.retryTicks(),
                what + " may try again at " + record.duelReadyAt() + ", not " + (at + VisoredLogic.retryTicks()));
        helper.assertTrue(hollow.isRemoved(), "the Hollow of " + what + " is still there");
    }

    /**
     * Calls the player's mask at {@code now} and asserts what came of it. Only a mask put on, or one already on,
     * leaves the player masked.
     */
    private static void assertStart(GameTestHelper helper, Player player, long now, boolean force,
                                    VisoredEvents.MaskStart expected, String what) {
        SoulData data = SoulLogic.get(player);
        VisoredEvents.MaskStart start = VisoredEvents.putOnMask(player, data, now, force);
        helper.assertTrue(start == expected, "calling the mask for " + what + " gave " + start + ", not " + expected);
        boolean on = expected == VisoredEvents.MaskStart.PUT_ON || expected == VisoredEvents.MaskStart.ALREADY;
        helper.assertTrue(VisoredLogic.isMasked(data) == on,
                what + (on ? " is not masked" : " is masked") + " after calling the mask gave " + start);
    }

    /** The player's mask is off, and neither of its boosts is left on the player. */
    private static void assertUnmasked(GameTestHelper helper, Player player, String what) {
        helper.assertFalse(VisoredLogic.isMasked(SoulLogic.get(player)), what + " is still masked");
        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.MOVEMENT_SPEED, VisoredLogic.MASK_SPEED_ID) == null,
                what + " kept the mask's speed");
        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.ATTACK_DAMAGE, VisoredLogic.MASK_DAMAGE_ID) == null,
                what + " kept the mask's melee damage");
    }

    /** The first tick at or after {@code from} on which the Hollow of the soul with this id speaks. */
    private static long voiceTick(UUID id, long from) {
        return from + Math.floorMod((long) id.hashCode() - from, (long) VisoredLogic.VOICE_TICKS);
    }

    // ---- The record ----

    @GameTest(template = "empty")
    public static void visoredRecordLoadsAndRegisters(GameTestHelper helper) {
        ShinigamiProgress.Visored full = new ShinigamiProgress.Visored(3, 1_000L, 2_000L, 2, 3_000L, 4_000L);
        ShinigamiProgress progress = ShinigamiProgress.NONE.withEnrolled(true).withVisored(full);
        Tag saved = ShinigamiProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow();
        ShinigamiProgress loaded = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(progress), "the Shinigami record came back as " + loaded);

        // A soul with no Hollow saves as in 5i, and a 5i save, with no "visored" at all, reads as no Hollow inside.
        CompoundTag plain = (CompoundTag) ShinigamiProgress.CODEC.encodeStart(NbtOps.INSTANCE, ShinigamiProgress.NONE)
                .getOrThrow();
        helper.assertFalse(plain.contains("visored"), "a record with no Hollow saved one");
        CompoundTag old = (CompoundTag) saved.copy();
        helper.assertTrue(old.contains("visored"), "the record was saved without its Hollow");
        old.remove("visored");
        ShinigamiProgress fromOld = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(fromOld.visored().equals(ShinigamiProgress.Visored.NONE), "a 5i save read as " + fromOld.visored());
        helper.assertTrue(fromOld.enrolled(), "a 5i save lost the rest of its record");

        // One key at a time: a count below zero is put back to zero, a missing time never happened.
        CompoundTag partial = new CompoundTag();
        partial.putInt("wounds", 2);
        partial.putInt("losses", -3);
        CompoundTag holder = new CompoundTag();
        holder.put("visored", partial);
        ShinigamiProgress.Visored read = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, holder).getOrThrow().visored();
        helper.assertTrue(read.wounds() == 2 && read.losses() == 0, "the partial record read as " + read);
        helper.assertTrue(read.lastWoundAt() == NEVER && read.rootedAt() == NEVER && read.duelReadyAt() == NEVER
                && read.subduedAt() == NEVER, "the partial record's missing times read as " + read);
        holder.put("visored", StringTag.valueOf("x"));
        ShinigamiProgress broken = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, holder).getOrThrow();
        helper.assertTrue(broken.visored().equals(ShinigamiProgress.Visored.NONE),
                "a broken record read as " + broken.visored());

        ShinigamiProgress eight = new ShinigamiProgress(false, NEVER, 0L, 0.0F, NEVER, NEVER, -1L, 0);
        helper.assertTrue(eight.visored().equals(ShinigamiProgress.Visored.NONE),
                "the 5i constructor gave " + eight.visored());

        SoulData soul = ShinigamiTests.soul(SoulState.SHINIGAMI, 900.0F);
        soul.setShinigami(progress);
        Tag savedSoul = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, soul).getOrThrow();
        SoulData loadedSoul = SoulData.CODEC.parse(NbtOps.INSTANCE, savedSoul).getOrThrow();
        helper.assertTrue(loadedSoul.shinigami().visored().equals(full),
                "the soul's Hollow came back as " + loadedSoul.shinigami().visored());

        helper.assertTrue(ModEntities.INNER_HOLLOW.get().is(SoulLogic.SPIRITS), "the inner Hollow is not a spirit");
        for (String name : new String[] {Milestones.HOLLOWFIED, Milestones.VISORED}) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id(name)) != null,
                    "the " + name + " advancement did not load");
        }
        helper.succeed();
    }

    // ---- The route ----

    @GameTest(template = "field")
    public static void onlyTheRightSoulsTakeWounds(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player shinigami = host(helper, 500.0F);
        SoulData data = SoulLogic.get(shinigami);
        Player unnamed = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        Player weak = host(helper, 400.0F);
        Player quincy = host(helper, 500.0F);
        SoulLogic.get(quincy).setTier(PowerPath.QUINCY, 1);
        Player lender = host(helper, 500.0F);
        SoulData lent = SoulLogic.get(lender);
        lent.setShinigami(lent.shinigami().withPowerLentUntil(now + 1_000L));
        // A living Human who kept the Shinigami tier the name gave it.
        Player substitute = host(helper, 500.0F);
        SoulData substituteData = SoulLogic.get(substitute);
        substituteData.setState(SoulState.HUMAN);

        helper.assertTrue(VisoredLogic.mayTakeRoot(data, now), "a named Shinigami of 500 cannot take a Hollow in");
        helper.assertTrue(VisoredLogic.mayTakeRoot(substituteData, now), "a named Substitute cannot take a Hollow in");
        helper.assertFalse(VisoredLogic.mayTakeRoot(SoulLogic.get(unnamed), now), "a Shinigami without the name can");
        helper.assertFalse(VisoredLogic.mayTakeRoot(SoulLogic.get(weak), now), "a Shinigami of 400 can");
        helper.assertFalse(VisoredLogic.mayTakeRoot(SoulLogic.get(quincy), now), "an awakened Quincy can");
        helper.assertFalse(VisoredLogic.mayTakeRoot(lent, now), "a Shinigami whose power is lent can");
        withConfig(BleachConfig.VISORED_ENABLED, false, () -> helper.assertFalse(VisoredLogic.mayTakeRoot(data, now),
                "a Shinigami can take a Hollow in with the path off"));

        LivingEntity gillian = ModEntities.GILLIAN.get().create(helper.getLevel());
        LivingEntity spider = ModEntities.SPIDER_HOLLOW.get().create(helper.getLevel());
        LivingEntity zombie = EntityType.ZOMBIE.create(helper.getLevel());
        LivingEntity inner = ModEntities.INNER_HOLLOW.get().create(helper.getLevel());
        Player hollow = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 300.0F,
                new Vec3(2.5, 2.0, 2.5));
        Player menos = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 900.0F,
                new Vec3(4.5, 2.0, 2.5));
        SoulLogic.get(menos).setTier(PowerPath.HOLLOW, 2);
        Player arrancar = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.ARRANCAR, 900.0F,
                new Vec3(6.5, 2.0, 2.5));
        ArrancarNpc numero = helper.spawnWithNoFreeWill(ModEntities.ARRANCAR.get(), 2, 2, 13);
        try {
            numero.setUp(new ArrancarRank(34, 0), helper.getLevel().getRandom(), ArrancarNpc.NEVER);
            helper.assertTrue(gillian != null && spider != null && zombie != null && inner != null,
                    "a creature could not be made");
            helper.assertTrue(VisoredLogic.isWoundingSource(gillian, data), "a Gillian's blow does not wound");
            helper.assertTrue(VisoredLogic.isWoundingSource(numero, data), "a Número's blow does not wound");
            helper.assertTrue(VisoredLogic.isWoundingSource(menos, data), "a Gillian player's blow does not wound");
            helper.assertTrue(VisoredLogic.isWoundingSource(arrancar, data), "an Arrancar player's blow does not wound");
            helper.assertFalse(VisoredLogic.isWoundingSource(spider, data), "a Spider Hollow's blow wounds a Shinigami");
            helper.assertFalse(VisoredLogic.isWoundingSource(hollow, data),
                    "a plain Hollow player's blow wounds a Shinigami");
            helper.assertFalse(VisoredLogic.isWoundingSource(zombie, data), "a zombie's blow wounds");
            helper.assertFalse(VisoredLogic.isWoundingSource(inner, data), "an inner Hollow's blow wounds");
            helper.assertTrue(VisoredLogic.isWoundingSource(spider, substituteData),
                    "a Spider Hollow's blow does not wound a Substitute");
            helper.assertTrue(VisoredLogic.isWoundingSource(hollow, substituteData),
                    "a plain Hollow player's blow does not wound a Substitute");
            helper.assertFalse(VisoredLogic.isWoundingSource(inner, substituteData),
                    "an inner Hollow's blow wounds a Substitute");

            helper.assertTrue(VisoredLogic.isWound(5.0F, 20.0F), "5 of 20 is no wound");
            helper.assertFalse(VisoredLogic.isWound(5.1F, 20.0F), "5.1 of 20 is a wound");
            helper.assertFalse(VisoredLogic.isWound(0.0F, 20.0F), "death is a wound");

            // A wound counts once wound_spacing_seconds have passed since the last, and the third roots the Hollow.
            long spacing = BleachConfig.get(BleachConfig.VISORED_WOUND_SPACING_SECONDS) * 20L;
            helper.assertTrue(VisoredEvents.noteWound(shinigami, data, now) && VisoredLogic.record(data).wounds() == 1,
                    "the first wound did not count");
            helper.assertFalse(VisoredEvents.noteWound(shinigami, data, now + 100L), "a wound 5 seconds later counted");
            helper.assertTrue(VisoredLogic.record(data).wounds() == 1,
                    "the wounds after one too soon are " + VisoredLogic.record(data).wounds());
            helper.assertTrue(VisoredEvents.noteWound(shinigami, data, now + spacing)
                    && VisoredLogic.record(data).wounds() == 2, "the second wound did not count");
            helper.assertFalse(VisoredLogic.isRooted(data), "the Hollow took root at two wounds");
            helper.assertTrue(VisoredEvents.noteWound(shinigami, data, now + 2L * spacing), "the third wound did not count");
            ShinigamiProgress.Visored rooted = VisoredLogic.record(data);
            helper.assertTrue(rooted.wounds() == 3 && rooted.rootedAt() == now + 2L * spacing,
                    "the third wound did not root the Hollow: " + rooted);
            helper.assertFalse(VisoredLogic.mayTakeRoot(data, now + 2L * spacing),
                    "a rooted soul may take another Hollow in");
            helper.assertFalse(VisoredEvents.noteWound(shinigami, data, now + 3L * spacing), "a fourth wound counted");
            helper.assertTrue(VisoredLogic.record(data).equals(rooted), "a fourth wound changed the record");

            helper.assertTrue(VisoredEvents.noteWound(substitute, substituteData, now),
                    "the Substitute's first wound did not count");
            helper.assertFalse(VisoredLogic.isRooted(substituteData), "the Hollow took root in the Substitute at one wound");
            helper.assertTrue(VisoredEvents.noteWound(substitute, substituteData, now + spacing)
                    && VisoredLogic.record(substituteData).rootedAt() == now + spacing,
                    "the Substitute's second wound did not root the Hollow");

            // The real path: a Número's blow that leaves a named Shinigami at 4 of 20 is a wound.
            if (!HollowPlayerTests.peaceful(helper)) {
                Player struck = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
                SoulData struckData = SoulLogic.get(struck);
                struck.setHealth(6.0F);
                helper.assertTrue(struck.hurt(struck.damageSources().mobAttack(numero), 2.0F),
                        "the Número's blow did not land");
                ShinigamiProgress.Visored record = VisoredLogic.record(struckData);
                helper.assertTrue(record.wounds() == 1 && record.lastWoundAt() == now,
                        "the Número's blow at " + struck.getHealth() + " health left " + record);
            }
        } finally {
            discard(numero);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aRootedHollowSpeaksAndWaits(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData data = SoulLogic.get(player);
        VisoredEvents.takeRoot(player, data, now);
        ShinigamiProgress.Visored rooted = VisoredLogic.record(data);
        long readyAt = now + DAY;
        helper.assertTrue(rooted.rootedAt() == now && rooted.duelReadyAt() == readyAt, "rooting left " + rooted);
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION) && player.hasEffect(MobEffects.WEAKNESS),
                "the Hollow took root without Nausea and Weakness");
        helper.assertFalse(VisoredLogic.duelReady(data, readyAt - 1L), "the Hollow was ready a tick early");
        helper.assertTrue(VisoredLogic.duelReady(data, readyAt), "the Hollow was not ready after 20 minutes");

        // The soul screen: the Hollow's lines while it waits, the mask's once it is beaten, and the usual ones otherwise.
        helper.assertTrue("hollowfied".equals(ShinigamiLogic.nextStepKey(data, now)),
                "the stirring Hollow's next step is " + ShinigamiLogic.nextStepKey(data, now));
        helper.assertTrue("hollowfied.ready".equals(ShinigamiLogic.nextStepKey(data, readyAt)),
                "the ready Hollow's next step is " + ShinigamiLogic.nextStepKey(data, readyAt));
        VisoredLogic.setRecord(data, rooted.withLosses(1));
        helper.assertTrue("hollowfied.waiting".equals(ShinigamiLogic.nextStepKey(data, now)),
                "the resting Hollow's next step is " + ShinigamiLogic.nextStepKey(data, now));
        VisoredLogic.setRecord(data, rooted);
        long lentUntil = data.shinigami().powerLentUntil();
        data.setShinigami(data.shinigami().withPowerLentUntil(now + 1_000L));
        helper.assertTrue("lent".equals(ShinigamiLogic.nextStepKey(data, now)),
                "a lender's next step is " + ShinigamiLogic.nextStepKey(data, now));
        data.setShinigami(data.shinigami().withPowerLentUntil(lentUntil));
        withConfig(BleachConfig.VISORED_ENABLED, false, () -> helper.assertTrue(
                "shinigami.named".equals(ShinigamiLogic.nextStepKey(data, now)),
                "with the path off the next step is " + ShinigamiLogic.nextStepKey(data, now)));
        VisoredLogic.setRecord(data, rooted.withSubduedAt(now));
        helper.assertTrue("visored".equals(ShinigamiLogic.nextStepKey(data, now)),
                "a Visored who has never worn its mask hears " + ShinigamiLogic.nextStepKey(data, now));
        VisoredLogic.setRecord(data, rooted.withSubduedAt(now).withMaskChangedAt(now));
        helper.assertTrue("shinigami.named".equals(ShinigamiLogic.nextStepKey(data, now)),
                "a Visored's next step is " + ShinigamiLogic.nextStepKey(data, now));
        VisoredLogic.setRecord(data, rooted);
        data.setState(SoulState.HOLLOW);
        helper.assertTrue("hollow".equals(ShinigamiLogic.nextStepKey(data, now)),
                "a Hollow with a rooted record hears " + ShinigamiLogic.nextStepKey(data, now));
        data.setState(SoulState.SHINIGAMI);

        // The voice: once every 5 minutes on the soul's own tick, the ready line once it can be faced.
        UUID id = player.getUUID();
        long first = voiceTick(id, now);
        String voice = VisoredLogic.voiceKey(data, id, first);
        helper.assertTrue(voice != null && voice.startsWith("voice."), "the stirring Hollow said " + voice);
        helper.assertTrue(VisoredLogic.voiceKey(data, id, first + 1L) == null, "the Hollow spoke on the next tick too");
        long ready = voiceTick(id, readyAt);
        helper.assertTrue("ready".equals(VisoredLogic.voiceKey(data, id, ready)),
                "the ready Hollow said " + VisoredLogic.voiceKey(data, id, ready));
        VisoredLogic.setRecord(data, rooted.withLosses(1));
        helper.assertTrue("taunt".equals(VisoredLogic.voiceKey(data, id, ready)),
                "the Hollow that won said " + VisoredLogic.voiceKey(data, id, ready));
        VisoredLogic.setRecord(data, rooted.withSubduedAt(now));
        helper.assertTrue(VisoredLogic.voiceKey(data, id, first) == null, "the beaten Hollow spoke");
        VisoredLogic.setRecord(data, ShinigamiProgress.Visored.NONE);
        helper.assertTrue(VisoredLogic.voiceKey(data, id, first) == null, "a soul with no Hollow heard one");
        VisoredLogic.setRecord(data, rooted);
        data.setShinigami(data.shinigami().withPowerLentUntil(first + 1L));
        helper.assertTrue(VisoredLogic.voiceKey(data, id, first) == null, "the Hollow spoke while the power was lent");
        data.setShinigami(data.shinigami().withPowerLentUntil(lentUntil));

        // Clocks out of reach of the game time are brought back, and a time that never happened stays so.
        ShinigamiProgress.Visored stale = ShinigamiProgress.Visored.NONE.withWounds(1).withLastWoundAt(now + 5_000L)
                .withDuelReadyAt(now + 1_000_000_000L);
        ShinigamiProgress.Visored normal = VisoredLogic.normalise(stale, now);
        helper.assertTrue(normal.lastWoundAt() == now && normal.duelReadyAt() == now + DAY,
                "a stale record was brought back to " + normal);
        helper.assertTrue(normal.wounds() == 1 && normal.rootedAt() == NEVER && normal.subduedAt() == NEVER,
                "bringing a record back changed what never happened: " + normal);
        helper.assertTrue(VisoredLogic.normalise(rooted, now).equals(rooted), "a record in reach of the time was changed");
        helper.succeed();
    }

    // ---- The fight ----

    @GameTest(template = "field")
    public static void theHollowComesOutInJinzen(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = host(helper, 900.0F);
        SoulData data = SoulLogic.get(host);
        ready(data, now);
        host.setShiftKeyDown(true);
        Player early = host(helper, 900.0F);
        SoulData earlyData = SoulLogic.get(early);
        VisoredLogic.setRecord(earlyData, VisoredLogic.record(earlyData).withRootedAt(now - DAY).withDuelReadyAt(now + 100L));
        early.setShiftKeyDown(true);
        Player neighbour = host(helper, 900.0F);
        SoulData neighbourData = SoulLogic.get(neighbour);
        ready(neighbourData, now);
        Player creative = hostIn(helper, GameType.CREATIVE);
        SoulData creativeData = SoulLogic.get(creative);
        ready(creativeData, now);
        creative.setShiftKeyDown(true);
        try {
            if (HollowPlayerTests.peaceful(helper)) {
                VisoredEvents.DuelStart start = VisoredEvents.beginDuel(host, data, now, false);
                helper.assertTrue(start == VisoredEvents.DuelStart.PEACEFUL && VisoredEvents.duelOf(host.getUUID()) == null,
                        "a Hollow came out on Peaceful: " + start);
                helper.succeed();
                return;
            }
            ItemAttributeModifiers before = host.getMainHandItem().get(DataComponents.ATTRIBUTE_MODIFIERS);
            JinzenLogic.begin(host, data, now);
            JinzenLogic.tick(host, data, host.getMainHandItem(), JinzenLogic.SETTLE_TICKS, now);
            helper.assertTrue(VisoredEvents.isDuelling(host), "settling in Jinzen did not bring the ready Hollow out");
            helper.assertTrue(data.jinzen() == null, "Jinzen went on with the Hollow out");
            InnerHollow hollow = VisoredEvents.duelOf(host.getUUID());
            helper.assertTrue(hollow != null && !hollow.isRemoved()
                    && helper.getLevel().getEntity(hollow.getUUID()) == hollow, "the Hollow is not in the world");
            QuincyTests.still(hollow);

            // √900 = 30: 30 + 2·30 health and a blow of 2 + 0.1·30, with no armour.
            HollowPlayerTests.assertClose(helper, hollow.getMaxHealth(), 90.0, "the Hollow's maximum health");
            HollowPlayerTests.assertClose(helper, hollow.getHealth(), 90.0, "the Hollow's health");
            AttributeInstance blow = hollow.getAttribute(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(blow != null, "the Hollow has no attack damage");
            HollowPlayerTests.assertClose(helper, blow.getBaseValue(), 5.0, "the Hollow's blow");
            HollowPlayerTests.assertClose(helper, hollow.getAttributeValue(Attributes.ARMOR), 0.0, "the Hollow's armour");
            helper.assertTrue(host.getUUID().equals(hollow.hostId()), "the Hollow's host is " + hollow.hostId());
            SoulData hollowSoul = SoulLogic.get(hollow);
            helper.assertTrue(hollowSoul.state() == SoulState.HOLLOW, "the Hollow's soul is " + hollowSoul.state());
            HollowPlayerTests.assertClose(helper, hollowSoul.maxReiryoku(), 900.0, "the Hollow's maximum reiryoku");

            ItemStack blade = hollow.getMainHandItem();
            helper.assertTrue(ItemStack.isSameItem(blade, host.getMainHandItem()), "the Hollow holds " + blade);
            ItemAttributeModifiers copied = blade.get(DataComponents.ATTRIBUTE_MODIFIERS);
            helper.assertTrue(copied != null && copied.modifiers().isEmpty(), "the copied blade kept its power: " + copied);
            helper.assertTrue(Objects.equals(host.getMainHandItem().get(DataComponents.ATTRIBUTE_MODIFIERS), before),
                    "copying the blade changed the host's own");
            float apart = hollow.distanceTo(host);
            helper.assertTrue(apart >= 3.5F && apart <= 4.5F, "the Hollow stepped out " + apart + " blocks away");
            long endsAt = now + VisoredLogic.timeLimitTicks();
            helper.assertTrue(VisoredLogic.record(data).duelReadyAt() == endsAt,
                    "mid-fight the next try is at " + VisoredLogic.record(data).duelReadyAt() + ", not " + endsAt);
            VisoredEvents.DuelStart again = VisoredEvents.beginDuel(host, data, now, false);
            helper.assertTrue(again == VisoredEvents.DuelStart.ALREADY, "a second start gave " + again);

            // Not ready yet: Jinzen opens as it always did.
            JinzenLogic.begin(early, earlyData, now);
            JinzenLogic.tick(early, earlyData, early.getMainHandItem(), JinzenLogic.SETTLE_TICKS, now);
            helper.assertFalse(VisoredEvents.isDuelling(early), "a Hollow that was not ready came out");
            helper.assertTrue(earlyData.jinzen() != null && earlyData.jinzen().opened(),
                    "Jinzen did not open for a soul whose Hollow was not ready");
            // Creative mode would break the fight off at once, so the inner world opens instead.
            JinzenLogic.begin(creative, creativeData, now);
            JinzenLogic.tick(creative, creativeData, creative.getMainHandItem(), JinzenLogic.SETTLE_TICKS, now);
            helper.assertFalse(VisoredEvents.isDuelling(creative), "a creative host's Hollow came out");
            helper.assertTrue(creativeData.jinzen() != null && creativeData.jinzen().opened()
                    && VisoredLogic.duelReady(creativeData, now), "a creative host's Jinzen did not open as it always did");
            VisoredEvents.DuelStart crowded = VisoredEvents.beginDuel(neighbour, neighbourData, now, false);
            helper.assertTrue(crowded == VisoredEvents.DuelStart.CROWDED && !VisoredEvents.isDuelling(neighbour),
                    "a fight in the same spot gave " + crowded);
            withConfig(BleachConfig.VISORED_ENABLED, false, () -> {
                VisoredEvents.DuelStart off = VisoredEvents.beginDuel(early, earlyData, now, true);
                helper.assertTrue(off == VisoredEvents.DuelStart.DISABLED && !VisoredEvents.isDuelling(early),
                        "a forced fight with the path off gave " + off);
            });

            VisoredEvents.endDuel(host, data, VisoredEvents.DuelEnd.BROKEN_OFF, now);
            helper.assertTrue(hollow.isRemoved() && !VisoredEvents.isDuelling(host),
                    "breaking the fight off left the Hollow");
        } finally {
            end(now, host, early, neighbour, creative);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void onlyTheHostCanBeatIt(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = host(helper, 900.0F);
        SoulData data = SoulLogic.get(host);
        ready(data, now);
        if (refusedOnPeaceful(helper, host, now)) {
            helper.succeed();
            return;
        }
        Player other = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 900.0F));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 2);
        try {
            InnerHollow hollow = begin(helper, host, now);
            DamageSources sources = host.damageSources();
            helper.assertFalse(hollow.hurt(sources.playerAttack(other), 10.0F),
                    "another Shinigami's blow landed on the Hollow");
            helper.assertFalse(hollow.hurt(sources.mobAttack(zombie), 10.0F), "a zombie's blow landed on the Hollow");
            helper.assertFalse(hollow.hurt(sources.generic(), 10.0F), "a blow with no one behind it landed on the Hollow");
            HollowPlayerTests.assertClose(helper, hollow.getHealth(), 90.0, "the Hollow's health after blows from others");

            // No armour, no Zanjutsu for a mock and equal reiatsu: the blow lands as it is, and from afar half of it.
            helper.assertTrue(hollow.hurt(sources.playerAttack(host), 10.0F), "the host's blow did not land");
            HollowPlayerTests.assertClose(helper, hollow.getHealth(), 80.0, "the Hollow's health after the host's blow");
            VisoredEvents.Duel duel = VisoredEvents.duel(host.getUUID());
            helper.assertTrue(duel != null && duel.lastHostBlowAt() == now, "the host's blow was not noted");
            Arrow arrow = new Arrow(helper.getLevel(), host, new ItemStack(Items.ARROW), null);
            hollow.invulnerableTime = 0;
            helper.assertTrue(hollow.hurt(sources.arrow(arrow, host), 10.0F), "the host's arrow did not land");
            HollowPlayerTests.assertClose(helper, hollow.getHealth(), 75.0, "the Hollow's health after the host's arrow");

            float health = host.getHealth();
            helper.assertFalse(host.hurt(sources.mobAttack(zombie), 4.0F), "a zombie's blow reached the host mid-fight");
            HollowPlayerTests.assertClose(helper, host.getHealth(), health, "the host's health after the zombie's blow");
            helper.assertFalse(zombie.hurt(sources.playerAttack(host), 4.0F), "the host's blow reached a zombie mid-fight");
            helper.assertFalse(other.hurt(sources.playerAttack(host), 4.0F),
                    "the host's blow reached another Shinigami mid-fight");

            hollow.setHealth(1.0F);
            hollow.invulnerableTime = 0;
            helper.assertTrue(hollow.hurt(sources.playerAttack(host), 5.0F), "the host's last blow did not land");
            ShinigamiProgress.Visored record = VisoredLogic.record(data);
            helper.assertTrue(record.subduedAt() == now && VisoredLogic.isVisored(data),
                    "cutting the Hollow down did not make a Visored: " + record);
            helper.assertFalse(VisoredEvents.isDuelling(host), "the fight went on after the Hollow was cut down");
            helper.assertTrue(hollow.isRemoved(), "the beaten Hollow is still there");
            helper.assertTrue(zombie.hurt(sources.playerAttack(host), 1.0F),
                    "the host's blow on a zombie was stopped after the fight");
        } finally {
            end(now, host);
            discard(zombie);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void aLossStopsAtOneHeart(GameTestHelper helper) {
        helper.assertTrue(VisoredLogic.isLosingBlow(20.0F, 0.0F, 18.0F), "18 of 20 is no losing blow");
        helper.assertFalse(VisoredLogic.isLosingBlow(20.0F, 0.0F, 17.9F), "17.9 of 20 is a losing blow");
        helper.assertTrue(VisoredLogic.isLosingBlow(10.0F, 4.0F, 12.0F), "12 against 10 and 4 absorption is no losing blow");
        helper.assertFalse(VisoredLogic.isLosingBlow(10.0F, 4.0F, 11.0F), "11 against 10 and 4 absorption is a losing blow");
        HollowPlayerTests.assertClose(helper, VisoredLogic.clampedBlow(20.0F, 10.0F, 4.0F, 2.0F), 12.0,
                "the blow cut for 10 and 4");
        HollowPlayerTests.assertClose(helper, VisoredLogic.clampedBlow(5.0F, 1.5F, 0.0F, 2.0F), 0.0, "the blow cut for 1.5");
        HollowPlayerTests.assertClose(helper, VisoredLogic.clampedBlow(1.0F, 1.5F, 5.0F, 2.0F), 1.0,
                "a small blow on 1.5 and 5 absorption was raised");
        HollowPlayerTests.assertClose(helper, VisoredLogic.hollowHealth(900.0F), 90.0, "the Hollow's health at 900");
        HollowPlayerTests.assertClose(helper, VisoredLogic.hollowBlow(900.0F), 5.0, "the Hollow's blow at 900");
        HollowPlayerTests.assertClose(helper, VisoredLogic.hollowHealth(0.0F), 30.0, "the Hollow's health at 0");
        HollowPlayerTests.assertClose(helper, VisoredLogic.hollowHealth(1.0E9F), 1024.0, "the Hollow's health at 10^9");
        HollowPlayerTests.assertClose(helper, VisoredLogic.hollowBlow(1.0E9F), 20.0, "the Hollow's blow at 10^9");

        long now = HollowPlayerTests.now(helper);
        Player host = host(helper, 900.0F);
        SoulData data = SoulLogic.get(host);
        ready(data, now);
        if (refusedOnPeaceful(helper, host, now)) {
            helper.succeed();
            return;
        }
        Player late = host(helper, 900.0F);
        SoulData lateData = SoulLogic.get(late);
        ready(lateData, now);
        try {
            InnerHollow hollow = begin(helper, host, now);
            host.setHealth(6.0F);
            host.hurt(host.damageSources().mobAttack(hollow), 5.0F);
            ShinigamiProgress.Visored record = VisoredLogic.record(data);
            helper.assertTrue(host.isAlive(), "the Hollow's losing blow killed its host");
            HollowPlayerTests.assertClose(helper, host.getHealth(), 2.0, "the host's health after the losing blow");
            helper.assertFalse(VisoredEvents.isDuelling(host), "the fight went on after the losing blow");
            helper.assertTrue(record.losses() == 1 && record.duelReadyAt() == now + VisoredLogic.retryTicks(),
                    "the loss left " + record);
            helper.assertTrue(host.hasEffect(MobEffects.WEAKNESS) && host.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "the beaten host was not left weak and slow");
            helper.assertTrue(hollow.isRemoved(), "the Hollow that won is still there");

            // Its own fight, once the first is over: the time runs out.
            InnerHollow waiting = begin(helper, late, now);
            VisoredEvents.tick(late, lateData, now + VisoredLogic.timeLimitTicks());
            helper.assertTrue(VisoredLogic.record(lateData).losses() == 1, "running out of time counted no loss");
            helper.assertFalse(VisoredEvents.isDuelling(late), "the fight went on past its time");
            helper.assertTrue(waiting.isRemoved(), "the Hollow is still there after the time ran out");
        } finally {
            end(now, host, late);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void brokenOffFightsCostNothing(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player walker = host(helper, 900.0F);
        SoulData walkerData = SoulLogic.get(walker);
        ready(walkerData, now);
        if (!refusedOnPeaceful(helper, walker, now)) {
            Player spectator = hostIn(helper, GameType.SPECTATOR);
            SoulData spectatorData = SoulLogic.get(spectator);
            Player dying = host(helper, 900.0F);
            ready(SoulLogic.get(dying), now);
            Player stopped = host(helper, 900.0F);
            ready(SoulLogic.get(stopped), now);
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
            try {
                // Each fight in turn, in the same spot, once the one before has ended.
                InnerHollow first = begin(helper, walker, now);
                walker.moveTo(walker.getX() + 27.0, walker.getY(), walker.getZ(), 0.0F, 0.0F);
                VisoredEvents.tick(walker, walkerData, now + 1L);
                assertBrokenOff(helper, walker, first, now + 1L, "a host 27 blocks from the ring's middle");

                VisoredEvents.DuelStart forced = VisoredEvents.beginDuel(spectator, spectatorData, now, true);
                InnerHollow second = VisoredEvents.duelOf(spectator.getUUID());
                helper.assertTrue(forced == VisoredEvents.DuelStart.STARTED && second != null,
                        "a forced fight for a spectator gave " + forced);
                QuincyTests.still(second);
                VisoredEvents.tick(spectator, spectatorData, now + 1L);
                assertBrokenOff(helper, spectator, second, now + 1L, "a spectator");

                InnerHollow third = begin(helper, dying, now);
                helper.assertTrue(zombie != null, "a zombie could not be made");
                helper.assertFalse(dying.hurt(dying.damageSources().mobAttack(zombie), 4.0F),
                        "a zombie's blow reached the host mid-fight");
                helper.assertTrue(dying.hurt(dying.damageSources().generic(), 1.0F),
                        "a blow from nothing living was stopped");
                dying.die(dying.damageSources().generic());
                helper.assertFalse(VisoredEvents.isDuelling(dying), "the fight outlived its host");
                helper.assertTrue(VisoredLogic.record(SoulLogic.get(dying)).losses() == 0, "dying counted as a loss");
                helper.assertTrue(third.isRemoved(), "the Hollow outlived its host");

                // The server stopping, with this the only fight under way.
                InnerHollow fourth = begin(helper, stopped, now);
                VisoredEvents.breakOffAll();
                assertBrokenOff(helper, stopped, fourth, now, "a host at the server's stop");
                helper.assertTrue(VisoredEvents.duel(stopped.getUUID()) == null, "the fight is still in the table");
            } finally {
                end(now, walker, spectator, dying, stopped);
            }
        }
        // A Hollow in no fight, as a summoned one would be, goes on its first tick.
        InnerHollow bare = helper.spawn(ModEntities.INNER_HOLLOW.get(), 2, 2, 2);
        helper.runAfterDelay(2, () -> helper.assertTrue(bare.isRemoved(), "a Hollow in no fight outlived 2 ticks"));
        helper.succeedWhen(() -> helper.assertTrue(bare.isRemoved(), "a Hollow in no fight is still there"));
    }

    // ---- The mask ----

    @GameTest(template = "empty")
    public static void maskRecordLoadsAndRepairs(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ShinigamiProgress.Visored full =
                new ShinigamiProgress.Visored(3, 1_000L, 2_000L, 2, 3_000L, 4_000L, 5_000L, 6_000L, 7_000L, 8_000L);
        Tag saved = ShinigamiProgress.Visored.CODEC.encodeStart(NbtOps.INSTANCE, full).getOrThrow();
        ShinigamiProgress.Visored loaded = ShinigamiProgress.Visored.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(full), "the record with its mask came back as " + loaded);
        ShinigamiProgress progress = ShinigamiProgress.NONE.withVisored(full);
        Tag savedProgress = ShinigamiProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow();
        ShinigamiProgress loadedProgress = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, savedProgress).getOrThrow();
        helper.assertTrue(loadedProgress.equals(progress), "the Shinigami record came back as " + loadedProgress);

        // A 5j record: a beaten Hollow and no mask yet, which saves none and reads back with none.
        ShinigamiProgress.Visored old = new ShinigamiProgress.Visored(3, 1_000L, 2_000L, 0, 3_000L, 4_000L);
        helper.assertTrue(old.equals(new ShinigamiProgress.Visored(3, 1_000L, 2_000L, 0, 3_000L, 4_000L, NEVER, NEVER,
                NEVER, 0L)), "the 5j constructor gave " + old);
        CompoundTag oldTag = (CompoundTag) ShinigamiProgress.Visored.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow();
        for (String key : new String[] {"mask_changed_at", "mask_ends_at", "mask_ready_at", "mask_trained_ticks"}) {
            helper.assertFalse(oldTag.contains(key), "a record that never wore its mask saved " + key);
        }
        ShinigamiProgress.Visored fromOld = ShinigamiProgress.Visored.CODEC.parse(NbtOps.INSTANCE, oldTag).getOrThrow();
        helper.assertTrue(fromOld.equals(old) && !fromOld.masked() && fromOld.maskChangedAt() == NEVER
                && fromOld.maskEndsAt() == NEVER && fromOld.maskReadyAt() == NEVER && fromOld.maskTrainedTicks() == 0L,
                "a 5j save read as " + fromOld);

        // Damaged tags, one key at a time: each reads on its own, and none wipes the rest.
        CompoundTag noStart = new CompoundTag();
        noStart.putLong("mask_ends_at", now + 100L);
        ShinigamiProgress.Visored startless = ShinigamiProgress.Visored.CODEC.parse(NbtOps.INSTANCE, noStart).getOrThrow();
        helper.assertTrue(startless.masked() && startless.maskChangedAt() == NEVER,
                "a mask on with no start read as " + startless);
        helper.assertTrue(VisoredLogic.maskWorn(startless, now) == 0L,
                "a mask with no start was worn " + VisoredLogic.maskWorn(startless, now) + " ticks");
        helper.assertTrue(VisoredLogic.normalise(startless, now).maskChangedAt() == now,
                "a mask on with no start was repaired to " + VisoredLogic.normalise(startless, now));
        CompoundTag negative = new CompoundTag();
        negative.putLong("mask_trained_ticks", -5L);
        long below = ShinigamiProgress.Visored.CODEC.parse(NbtOps.INSTANCE, negative).getOrThrow().maskTrainedTicks();
        helper.assertTrue(below == 0L, "a training below zero read as " + below);
        CompoundTag huge = new CompoundTag();
        huge.putLong("mask_trained_ticks", 1_000_000_000_000L);
        long above = ShinigamiProgress.Visored.CODEC.parse(NbtOps.INSTANCE, huge).getOrThrow().maskTrainedTicks();
        helper.assertTrue(above == ShinigamiProgress.Visored.MAX_TRAINED_TICKS, "a training of 10^12 read as " + above);
        CompoundTag word = new CompoundTag();
        word.put("mask_ends_at", StringTag.valueOf("soon"));
        word.putInt("wounds", 2);
        ShinigamiProgress.Visored fromWord = ShinigamiProgress.Visored.CODEC.parse(NbtOps.INSTANCE, word).getOrThrow();
        helper.assertTrue(fromWord.maskEndsAt() == NEVER && fromWord.wounds() == 2,
                "a word where the mask's end belongs read as " + fromWord);

        // Clocks out of reach of the game time are brought back to the longest the config gives, at most 180 s.
        ShinigamiProgress.Visored stale = ShinigamiProgress.Visored.NONE.withMaskChangedAt(now + 500L)
                .withMaskEndsAt(now + 1_000_000_000L).withMaskReadyAt(now + 1_000_000_000L);
        ShinigamiProgress.Visored normal = VisoredLogic.normalise(stale, now);
        helper.assertTrue(normal.maskChangedAt() == now && normal.maskEndsAt() == now + 3_600L
                && normal.maskReadyAt() == now + 3_600L, "a stale mask was brought back to " + normal);
        ShinigamiProgress.Visored trained = ShinigamiProgress.Visored.NONE.withMaskTrainedTicks(40L);
        helper.assertTrue(VisoredLogic.normalise(trained, now).equals(trained),
                "bringing a record back changed what never happened: " + VisoredLogic.normalise(trained, now));
        ShinigamiProgress.Visored days = ShinigamiProgress.Visored.NONE.withMaskChangedAt(0L).withMaskEndsAt(2_000_100L);
        helper.assertTrue(VisoredLogic.maskWorn(days, 2_000_000L) == 3_600L,
                "a mask on since tick 0 was worn " + VisoredLogic.maskWorn(days, 2_000_000L) + " ticks");

        // A save that still holds a mask on (a crash) has it taken off at the next login: worn until then, waiting from then.
        Player host = visored(helper, 500.0F);
        SoulData data = SoulLogic.get(host);
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withMaskChangedAt(now - 40L).withMaskEndsAt(now + 180L));
        try {
            VisoredEvents.onRefresh(host, data);
            ShinigamiProgress.Visored refreshed = VisoredLogic.record(data);
            helper.assertTrue(!refreshed.masked() && refreshed.maskTrainedTicks() == 40L
                    && refreshed.maskReadyAt() == now + 60L, "a saved mask on came back as " + refreshed);
        } finally {
            end(now, host);
        }

        // The key rides with the zanpakutō keys, by ordinal. A key the other side does not know reads as Release,
        // which is why the network's version moves with it.
        helper.assertTrue(ZanpakutoKey.MASK.ordinal() == 5, "the Hollow Mask key is ordinal " + ZanpakutoKey.MASK.ordinal());
        ByteBuf buffer = Unpooled.buffer();
        try {
            ZanpakutoKey.STREAM_CODEC.encode(buffer, ZanpakutoKey.MASK);
            ZanpakutoKey received = ZanpakutoKey.STREAM_CODEC.decode(buffer);
            helper.assertTrue(received == ZanpakutoKey.MASK && buffer.readableBytes() == 0,
                    "the Hollow Mask key came through the network as " + received);
            ByteBufCodecs.VAR_INT.encode(buffer, 7);
            ZanpakutoKey unknown = ZanpakutoKey.STREAM_CODEC.decode(buffer);
            helper.assertTrue(unknown == ZanpakutoKey.RELEASE, "an unknown key read as " + unknown);
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void onlyABeatenHollowGivesAMask(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player plain = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        Player rooted = host(helper, 500.0F);
        ready(SoulLogic.get(rooted), now);
        Player host = visored(helper, 500.0F);
        SoulData data = SoulLogic.get(host);
        Player creative = visoredIn(helper, GameType.CREATIVE);
        SoulData creativeData = SoulLogic.get(creative);
        // A living Human who kept the Shinigami tier the name gave it.
        Player substitute = visored(helper, 500.0F);
        SoulData substituteData = SoulLogic.get(substitute);
        substituteData.setState(SoulState.HUMAN);
        Player fresh = visored(helper, 500.0F);
        SoulData freshData = SoulLogic.get(fresh);
        CajaNegacion caja = null;
        try {
            helper.assertTrue(VisoredLogic.isVisored(data) && ShinigamiLogic.isSubstitute(substituteData)
                    && VisoredLogic.isVisored(substituteData), "the Visored were not made as they should be");

            // Each refusal in its turn, one cause at a time.
            assertStart(helper, plain, now, false, VisoredEvents.MaskStart.NO_HOLLOW, "a Shinigami with no Hollow");
            assertStart(helper, rooted, now, false, VisoredEvents.MaskStart.NOT_SUBDUED,
                    "a Shinigami whose Hollow is not beaten");
            withConfig(BleachConfig.VISORED_ENABLED, false, () -> assertStart(helper, host, now, false,
                    VisoredEvents.MaskStart.DISABLED, "a Visored with the path off"));
            long lentUntil = data.shinigami().powerLentUntil();
            data.setShinigami(data.shinigami().withPowerLentUntil(now + 1_000L));
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.NO_POWER, "a Visored whose power is lent");
            data.setShinigami(data.shinigami().withPowerLentUntil(lentUntil));
            caja = helper.spawn(ModEntities.CAJA_NEGACION.get(), HOST_SPOT);
            helper.assertTrue(host.startRiding(caja, true), "the Visored could not be shut in the Caja Negación");
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.HELD, "a Visored shut in a Caja Negación");
            host.stopRiding();
            ShinigamiProgress.Visored before = VisoredLogic.record(data);
            VisoredLogic.setRecord(data, before.withMaskReadyAt(now + 100L));
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.WAITING, "a Visored whose mask still waits");
            float reiryoku = data.reiryoku();
            assertStart(helper, host, now, true, VisoredEvents.MaskStart.PUT_ON, "a waiting Visored, by command");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), reiryoku, "the reiryoku after a mask by command");
            VisoredEvents.endMask(host, data, VisoredEvents.MaskEnd.REMOVED, now);
            VisoredLogic.setRecord(data, VisoredLogic.record(data).withMaskReadyAt(before.maskReadyAt()));

            // At 500 the mask costs 50, and must leave half a point.
            HollowPlayerTests.assertClose(helper, VisoredLogic.maskCost(data), 50.0, "the mask's cost at 500");
            data.setReiryoku(50.4F);
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.TOO_WEAK, "a Visored with 50.4 reiryoku");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 50.4, "the reiryoku after a mask too costly");
            data.setReiryoku(50.5F);
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.PUT_ON, "a Visored with 50.5 reiryoku");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 0.5, "the reiryoku after the mask's cost");
            ShinigamiProgress.Visored on = VisoredLogic.record(data);
            helper.assertTrue(on.maskEndsAt() == now + 220L && on.maskChangedAt() == now, "the first mask is " + on);
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.ALREADY, "a Visored already masked");

            // Creative pays nothing and waits for nothing; a Substitute calls it as a Shinigami does.
            creativeData.setReiryoku(0.0F);
            VisoredLogic.setRecord(creativeData, VisoredLogic.record(creativeData).withMaskReadyAt(now + 5_000L));
            assertStart(helper, creative, now, false, VisoredEvents.MaskStart.PUT_ON, "a creative Visored with nothing");
            HollowPlayerTests.assertClose(helper, creativeData.reiryoku(), 0.0, "a creative Visored's reiryoku");
            assertStart(helper, substitute, now, false, VisoredEvents.MaskStart.PUT_ON, "a Visored Substitute");

            // The soul screen tells a new Visored how to call the mask, until it first has.
            helper.assertTrue("visored".equals(ShinigamiLogic.nextStepKey(freshData, now)),
                    "a new Visored's next step is " + ShinigamiLogic.nextStepKey(freshData, now));
            long freshLent = freshData.shinigami().powerLentUntil();
            freshData.setShinigami(freshData.shinigami().withPowerLentUntil(now + 1_000L));
            helper.assertTrue("lent".equals(ShinigamiLogic.nextStepKey(freshData, now)),
                    "a lending Visored's next step is " + ShinigamiLogic.nextStepKey(freshData, now));
            freshData.setShinigami(freshData.shinigami().withPowerLentUntil(freshLent));
            withConfig(BleachConfig.VISORED_ENABLED, false, () -> helper.assertTrue(
                    "shinigami.named".equals(ShinigamiLogic.nextStepKey(freshData, now)),
                    "with the path off a new Visored's next step is " + ShinigamiLogic.nextStepKey(freshData, now)));
            assertStart(helper, fresh, now, false, VisoredEvents.MaskStart.PUT_ON, "a new Visored");
            VisoredEvents.onMaskKey(fresh, freshData, now);
            helper.assertFalse(VisoredLogic.isMasked(freshData), "the Hollow Mask key did not take the mask off");
            helper.assertTrue("shinigami.named".equals(ShinigamiLogic.nextStepKey(freshData, now)),
                    "a Visored who has worn its mask hears " + ShinigamiLogic.nextStepKey(freshData, now));
        } finally {
            if (host.isPassenger()) {
                host.stopRiding();
            }
            discard(caja);
            end(now, plain, rooted, host, creative, substitute, fresh);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aMaskGivesAHollowsEdge(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = visored(helper, 1_000.0F);
        SoulData data = SoulLogic.get(host);
        Player creative = visoredIn(helper, GameType.CREATIVE);
        SoulData creativeData = SoulLogic.get(creative);
        try {
            assertUnmasked(helper, host, "a Visored before its mask");
            HollowPlayerTests.assertClose(helper, SoulLogic.reiatsu(host), 1_000.0, "the reiatsu before the mask");

            assertStart(helper, host, now, false, VisoredEvents.MaskStart.PUT_ON, "a Visored of 1,000");
            HollowPlayerTests.assertModifier(helper, host, Attributes.MOVEMENT_SPEED, VisoredLogic.MASK_SPEED_ID, 0.25,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, "the mask's speed");
            HollowPlayerTests.assertModifier(helper, host, Attributes.ATTACK_DAMAGE, VisoredLogic.MASK_DAMAGE_ID, 0.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the mask's melee damage");
            HollowPlayerTests.assertClose(helper, SoulLogic.reiatsu(host), 1_500.0, "the masked reiatsu");
            helper.assertFalse(SoulEvents.mayRegenerate(host, data), "a masked Visored's reiryoku refills");
            helper.assertTrue((HollowPlayerLogic.hollowLook(data) & HollowPlayerLogic.LOOK_MASK) != 0,
                    "the mask is not in the look");
            // To the living and the dead it looks a Hollow; to every other rule it is the Shinigami it is.
            helper.assertTrue(HollowPlayerLogic.seenAsHollow(data) && HollowPlayerLogic.shunnedByVillagers(host),
                    "villagers and Pluses do not take a masked Visored for a Hollow");
            helper.assertFalse(HollowPlayerLogic.isHollow(data) || HollowPlayerLogic.isHollowOrArrancar(data)
                    || HollowPlayerLogic.isHollowPlayer(host) || QuincyLogic.isHollowSource(host),
                    "a masked Visored counts as a Hollow");
            helper.assertTrue(data.state() == SoulState.SHINIGAMI && ShinigamiLogic.hasShinigamiPower(data),
                    "a masked Visored is a " + data.state());
            assertStart(helper, creative, now, false, VisoredEvents.MaskStart.PUT_ON, "a creative Visored");
            helper.assertTrue(HollowPlayerLogic.seenAsHollow(creativeData), "a creative masked Visored looks no Hollow");
            helper.assertFalse(HollowPlayerLogic.shunnedByVillagers(creative), "villagers shun a creative masked Visored");

            // Whatever takes a boost off, the next tick puts it back.
            AttributeInstance speed = host.getAttribute(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(speed != null && speed.removeModifier(VisoredLogic.MASK_SPEED_ID),
                    "the mask's speed could not be taken off by hand");
            VisoredEvents.tick(host, data, now + 1L);
            HollowPlayerTests.assertModifier(helper, host, Attributes.MOVEMENT_SPEED, VisoredLogic.MASK_SPEED_ID, 0.25,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, "the mask's speed a tick after it was taken off");

            VisoredEvents.onMaskKey(host, data, now + 2L);
            assertUnmasked(helper, host, "a Visored that took its mask off");
            HollowPlayerTests.assertClose(helper, SoulLogic.reiatsu(host), 1_000.0, "the reiatsu after the mask");
            helper.assertTrue(SoulEvents.mayRegenerate(host, data), "a Visored's reiryoku does not refill after the mask");
            helper.assertTrue((HollowPlayerLogic.hollowLook(data) & HollowPlayerLogic.LOOK_MASK) == 0,
                    "the mask is still in the look");
            helper.assertFalse(HollowPlayerLogic.shunnedByVillagers(host), "villagers shun a Visored without its mask");
        } finally {
            end(now + 2L, host, creative);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aMaskGrowsCrumblesAndRunsDry(GameTestHelper helper) {
        // 11 s at first, 3 s more for every minute worn, and never past 3 minutes.
        long[][] durations = {{0L, 220L}, {1_200L, 280L}, {67_600L, 3_600L}, {10_000_000L, 3_600L}};
        for (long[] duration : durations) {
            helper.assertTrue(VisoredLogic.maskDurationTicks(duration[0]) == duration[1], "after " + duration[0]
                    + " ticks worn a mask lasts " + VisoredLogic.maskDurationTicks(duration[0]) + ", not " + duration[1]);
        }

        long now = HollowPlayerTests.now(helper);
        Player host = visored(helper, 500.0F);
        SoulData data = SoulLogic.get(host);
        Player dry = visored(helper, 500.0F);
        SoulData dryData = SoulLogic.get(dry);
        try {
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.PUT_ON, "a Visored at full reiryoku");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 450.0, "the reiryoku after the mask's cost");
            // 0.4% of 500 a second, less with Reiatsu Control, whatever the mock's level is.
            double drain = 500.0 * 0.004 * ZanpakutoLogic.drainFraction(data) / 20.0;
            HollowPlayerTests.assertClose(helper, VisoredLogic.maskDrainPerTick(data), drain, "the mask's drain a tick");
            VisoredEvents.tick(host, data, now + 1L);
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 450.0 - drain, "the reiryoku after a tick masked");
            VisoredEvents.tick(host, data, now + 219L);
            helper.assertTrue(VisoredLogic.isMasked(data), "the first mask crumbled a tick early");

            VisoredEvents.tick(host, data, now + 220L);
            ShinigamiProgress.Visored crumbled = VisoredLogic.record(data);
            assertUnmasked(helper, host, "a first mask 11 seconds on");
            helper.assertTrue(crumbled.maskReadyAt() == now + 1_420L && crumbled.maskTrainedTicks() == 220L
                    && crumbled.maskChangedAt() == now + 220L, "the crumbled mask left " + crumbled);
            MobEffectInstance slowed = host.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(slowed != null && slowed.getAmplifier() == 1 && host.hasEffect(MobEffects.WEAKNESS),
                    "the crumbled mask left its wearer " + slowed);
            assertStart(helper, host, now + 221L, false, VisoredEvents.MaskStart.WAITING, "a Visored just after a crumble");
            assertStart(helper, host, now + 1_420L, false, VisoredEvents.MaskStart.PUT_ON, "a Visored a minute on");
            long endsAt = VisoredLogic.record(data).maskEndsAt();
            helper.assertTrue(endsAt == now + 1_420L + 231L, "the second mask ends " + (endsAt - now - 1_420L) + " ticks on");
            VisoredEvents.endMask(host, data, VisoredEvents.MaskEnd.TAKEN_OFF, now + 1_420L);

            // A drain that would leave less than half a point runs the mask dry, at any Reiatsu Control.
            assertStart(helper, dry, now, false, VisoredEvents.MaskStart.PUT_ON, "a second Visored");
            dryData.setReiryoku(ZanpakutoLogic.RESERVE + VisoredLogic.maskDrainPerTick(dryData) / 2.0F);
            VisoredEvents.tick(dry, dryData, now + 1L);
            ShinigamiProgress.Visored ranDry = VisoredLogic.record(dryData);
            assertUnmasked(helper, dry, "a mask that ran dry");
            HollowPlayerTests.assertClose(helper, dryData.reiryoku(), 0.0, "the reiryoku after the mask ran dry");
            helper.assertTrue(ranDry.maskReadyAt() == now + 1_201L, "the dry mask's wait ends at " + ranDry.maskReadyAt());
            helper.assertTrue(dry.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the dry mask did not wind its wearer");
        } finally {
            end(now + 1_420L, host, dry);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingItOffSavesTheWait(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = visored(helper, 500.0F);
        SoulData data = SoulLogic.get(host);
        Player creative = visoredIn(helper, GameType.CREATIVE);
        SoulData creativeData = SoulLogic.get(creative);
        try {
            // Taken off, the next mask waits as long as this one was worn, and at least 3 seconds.
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.PUT_ON, "a Visored");
            VisoredEvents.onMaskKey(host, data, now + 40L);
            ShinigamiProgress.Visored first = VisoredLogic.record(data);
            assertUnmasked(helper, host, "a mask taken off after 2 seconds");
            helper.assertTrue(first.maskReadyAt() == now + 100L && first.maskTrainedTicks() == 40L,
                    "a mask taken off after 2 seconds left " + first);
            helper.assertFalse(host.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "taking the mask off winded its wearer");
            assertStart(helper, host, now + 100L, false, VisoredEvents.MaskStart.PUT_ON, "a Visored whose wait is over");
            long endsAt = VisoredLogic.record(data).maskEndsAt();
            helper.assertTrue(endsAt == now + 100L + 222L, "the second mask ends " + (endsAt - now - 100L) + " ticks on");
            VisoredEvents.onMaskKey(host, data, now + 300L);
            ShinigamiProgress.Visored second = VisoredLogic.record(data);
            helper.assertTrue(!second.masked() && second.maskReadyAt() == now + 500L && second.maskTrainedTicks() == 240L,
                    "a mask taken off after 10 seconds left " + second);

            // Creative pays nothing, drains nothing, waits for nothing, trains nothing and is never winded.
            creativeData.setReiryoku(0.0F);
            assertStart(helper, creative, now, false, VisoredEvents.MaskStart.PUT_ON, "a creative Visored with nothing");
            VisoredEvents.tick(creative, creativeData, now + 1L);
            helper.assertTrue(VisoredLogic.isMasked(creativeData), "a creative mask came off at its first tick");
            HollowPlayerTests.assertClose(helper, creativeData.reiryoku(), 0.0, "a creative Visored's reiryoku masked");
            VisoredEvents.onMaskKey(creative, creativeData, now + 2L);
            ShinigamiProgress.Visored taken = VisoredLogic.record(creativeData);
            helper.assertTrue(!taken.masked() && taken.maskReadyAt() == now + 2L && taken.maskTrainedTicks() == 0L,
                    "a creative mask taken off left " + taken);
            assertStart(helper, creative, now + 2L, false, VisoredEvents.MaskStart.PUT_ON, "a creative Visored again");
            VisoredEvents.tick(creative, creativeData, VisoredLogic.record(creativeData).maskEndsAt());
            assertUnmasked(helper, creative, "a creative mask at the end of its time");
            helper.assertFalse(creative.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a creative mask's crumble winded it");
        } finally {
            end(now + 300L, host, creative);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void blowsCrackAndShatterIt(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = visored(helper, 500.0F);
        SoulData data = SoulLogic.get(host);
        Player struck = visored(helper, 500.0F);
        SoulData struckData = SoulLogic.get(struck);
        Player attacker = host(helper, 5_000.0F);
        Player own = visored(helper, 500.0F);
        SoulData ownData = SoulLogic.get(own);
        Player doomed = visored(helper, 500.0F);
        try {
            HollowPlayerTests.assertClose(helper, host.getMaxHealth(), 20.0, "a mock's maximum health");
            assertStart(helper, host, now, false, VisoredEvents.MaskStart.PUT_ON, "a Visored");
            VisoredEvents.noteMaskBlow(host, data, 2.0F, now);
            helper.assertTrue(VisoredLogic.isMasked(data) && !VisoredLogic.maskCracked(data),
                    "a blow of 2 of 20 cracked the mask or took it off");
            VisoredEvents.noteMaskBlow(host, data, 2.0F, now);
            helper.assertTrue(VisoredLogic.isMasked(data) && VisoredLogic.maskCracked(data),
                    "blows of 4 of 20 did not crack the mask, or took it off");
            helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0,
                    "the cracked mask is not drawn cracked");
            // 6.5 of 20 is a share of 0.325, clear of float rounding at 0.30.
            VisoredEvents.noteMaskBlow(host, data, 2.5F, now);
            ShinigamiProgress.Visored shattered = VisoredLogic.record(data);
            assertUnmasked(helper, host, "a mask after blows of 6.5 of 20");
            helper.assertTrue(shattered.maskReadyAt() == now + 1_200L, "the shattered mask's wait ends at "
                    + shattered.maskReadyAt());
            helper.assertTrue(host.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the shattered mask did not wind its wearer");
            helper.assertFalse(VisoredLogic.maskCracked(data), "the mask is still cracked once it is off");

            // A real blow, through the listener: a player's does not scale with the difficulty, and at 5,000 against
            // the masked 750 the reiatsu difference does not cut it.
            assertStart(helper, struck, now, false, VisoredEvents.MaskStart.PUT_ON, "a second Visored");
            helper.assertTrue(struck.hurt(struck.damageSources().playerAttack(attacker), 4.0F),
                    "the blow on the masked Visored did not land");
            helper.assertTrue(struck.getHealth() < 20.0F,
                    "the masked Visored's health after the blow is " + struck.getHealth());
            helper.assertTrue(VisoredLogic.isMasked(struckData) && VisoredLogic.maskCracked(struckData),
                    "a blow of 4 of 20 did not crack the mask, or took it off");

            // The wearer's own blow never cracks its mask.
            assertStart(helper, own, now, false, VisoredEvents.MaskStart.PUT_ON, "a third Visored");
            helper.assertTrue(own.hurt(own.damageSources().playerAttack(own), 4.0F),
                    "the masked Visored's own blow did not land");
            helper.assertTrue(VisoredLogic.isMasked(ownData) && !VisoredLogic.maskCracked(ownData),
                    "the wearer's own blow of 4 of 20 cracked its mask, or took it off");

            // A killing blow leaves the mask to death's ending: off with the short wait, not shattered.
            assertStart(helper, doomed, now, false, VisoredEvents.MaskStart.PUT_ON, "a fourth Visored");
            doomed.setHealth(3.0F);
            doomed.hurt(doomed.damageSources().playerAttack(attacker), 8.0F);
            helper.assertTrue(doomed.isDeadOrDying(), "the killing blow left the masked Visored at " + doomed.getHealth());
            assertUnmasked(helper, doomed, "a Visored killed while masked");
            long doomedReady = VisoredLogic.record(SoulLogic.get(doomed)).maskReadyAt();
            helper.assertTrue(doomedReady == now + 60L, "the wait after a killing blow ends at " + doomedReady
                    + ", not death's " + (now + 60L));
        } finally {
            end(now, host, struck, attacker, own, doomed);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void everyExitTakesTheMaskOff(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player dying = visored(helper, 500.0F);
        Player leaving = visored(helper, 500.0F);
        Player stopped = visored(helper, 500.0F);
        Player alsoStopped = visored(helper, 500.0F);
        Player disabled = visored(helper, 500.0F);
        Player lender = visored(helper, 500.0F);
        Player spectator = visoredIn(helper, GameType.SPECTATOR);
        Player sleeper = visored(helper, 500.0F);
        Player fallen = visored(helper, 500.0F);
        Player[] all = {dying, leaving, stopped, alsoStopped, disabled, lender, spectator, sleeper, fallen};
        try {
            for (Player player : all) {
                // The spectator's mask is put on by command; the refresh path is in maskRecordLoadsAndRepairs.
                boolean forced = player == spectator;
                assertStart(helper, player, now, forced, VisoredEvents.MaskStart.PUT_ON,
                        forced ? "a spectator" : "a Visored");
            }

            // Death, before the soul is copied: nothing to drop in the box, and no wind.
            dying.getInventory().clearContent();
            dying.die(dying.damageSources().generic());
            assertUnmasked(helper, dying, "a Visored that died");
            ShinigamiProgress.Visored died = VisoredLogic.record(SoulLogic.get(dying));
            helper.assertTrue(died.maskReadyAt() == now + 60L, "the dead Visored's wait ends at " + died.maskReadyAt());
            helper.assertFalse(dying.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "dying winded the Visored");

            // Logging out, before the save.
            VisoredEvents.endAll(leaving, SoulLogic.get(leaving), now + 30L);
            assertUnmasked(helper, leaving, "a Visored that logged out");
            ShinigamiProgress.Visored left = VisoredLogic.record(SoulLogic.get(leaving));
            helper.assertTrue(left.maskReadyAt() == now + 90L && left.maskTrainedTicks() == 30L,
                    "logging out after 30 ticks left " + left);

            // The server stopping, before it saves its players.
            VisoredEvents.endMasks(List.of(stopped, alsoStopped));
            assertUnmasked(helper, stopped, "a Visored at the server's stop");
            assertUnmasked(helper, alsoStopped, "a second Visored at the server's stop");

            // Every soul that may no longer wear it, at its next tick.
            withConfig(BleachConfig.VISORED_ENABLED, false,
                    () -> VisoredEvents.tick(disabled, SoulLogic.get(disabled), now + 1L));
            assertUnmasked(helper, disabled, "a Visored with the path turned off");
            SoulData lent = SoulLogic.get(lender);
            lent.setShinigami(lent.shinigami().withPowerLentUntil(now + 1_000L));
            VisoredEvents.tick(lender, lent, now + 1L);
            assertUnmasked(helper, lender, "a Visored that lent its power");
            VisoredEvents.tick(spectator, SoulLogic.get(spectator), now + 1L);
            assertUnmasked(helper, spectator, "a spectator");
            sleeper.startSleeping(helper.absolutePos(new BlockPos(2, 2, 2)));
            helper.assertTrue(sleeper.isSleeping(), "the Visored could not lie down");
            VisoredEvents.tick(sleeper, SoulLogic.get(sleeper), now + 1L);
            sleeper.stopSleeping();
            assertUnmasked(helper, sleeper, "a Visored asleep");
            SoulData hollow = SoulLogic.get(fallen);
            hollow.setState(SoulState.HOLLOW);
            VisoredEvents.tick(fallen, hollow, now + 1L);
            assertUnmasked(helper, fallen, "a Visored become a Hollow");
            helper.assertTrue((HollowPlayerLogic.hollowLook(hollow) & HollowPlayerLogic.LOOK_MASK) == 0,
                    "a Hollow has the Visored's mask in its look");
        } finally {
            if (sleeper.isSleeping()) {
                sleeper.stopSleeping();
            }
            end(now + 30L, all);
        }
        helper.succeed();
    }
}
