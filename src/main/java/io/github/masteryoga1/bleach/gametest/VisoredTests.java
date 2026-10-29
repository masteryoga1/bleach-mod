package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
 * voice and the soul screen while it waits, and the fight with it, which only the host can win and which never
 * kills. Mock players are never in the world and never tick, so every fight is driven by calling
 * {@link VisoredEvents#tick} with the time it needs. Every Hollow that steps out is made still, and every fight a
 * test begins is ended within that test's first tick, so the server's table of fights is empty whenever another
 * test runs. The test server runs on Normal; on Peaceful, where no Hollow comes out, the parts that need a fight
 * or hurt a mock are skipped.
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

    /** Ends every fight of these hosts, so none is left in the table for another test. */
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

        ShinigamiProgress nine = new ShinigamiProgress(false, NEVER, 0L, 0.0F, NEVER, NEVER, -1L, 0, Optional.empty());
        helper.assertTrue(nine.visored().equals(ShinigamiProgress.Visored.NONE), "the 5i constructor gave " + nine.visored());

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

        // The soul screen: the Hollow's lines while it waits, and the usual ones otherwise.
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
            end(now, host, early, neighbour);
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

            hollow.setHealth(1.0F);
            hollow.invulnerableTime = 0;
            helper.assertTrue(hollow.hurt(sources.playerAttack(host), 5.0F), "the host's last blow did not land");
            ShinigamiProgress.Visored record = VisoredLogic.record(data);
            helper.assertTrue(record.subduedAt() == now && VisoredLogic.isVisored(data),
                    "cutting the Hollow down did not make a Visored: " + record);
            helper.assertFalse(VisoredEvents.isDuelling(host), "the fight went on after the Hollow was cut down");
            helper.assertTrue(hollow.isRemoved(), "the beaten Hollow is still there");
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
        HollowPlayerTests.assertClose(helper, VisoredLogic.clampedBlow(10.0F, 4.0F, 2.0F), 12.0, "the blow cut for 10 and 4");
        HollowPlayerTests.assertClose(helper, VisoredLogic.clampedBlow(1.5F, 0.0F, 2.0F), 0.0, "the blow cut for 1.5");
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
            Player spectator = helper.makeMockPlayer(GameType.SPECTATOR);
            SoulData spectatorData = ZanpakutoTests.soul(SoulState.SHINIGAMI, 900.0F);
            spectator.setData(SoulAttachments.SOUL, spectatorData);
            spectator.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
            ZanpakutoEvents.learnName(spectator, spectatorData, ZanpakutoSpirits.ZANGETSU.location(),
                    ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), spectator.getMainHandItem());
            stand(helper, spectator);
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
}
