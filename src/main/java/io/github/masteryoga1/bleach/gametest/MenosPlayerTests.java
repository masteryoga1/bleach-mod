package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.network.HollowKeyPayload;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.DevourEvents;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowKey;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.MenosBody;
import io.github.masteryoga1.bleach.soul.MenosEvolution;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for a Hollow player's road through the Menos Grande: the saved record of it, the forms' numbers,
 * the gates that count what was devoured, the destined few and the server's limit on Vasto Lorde, the
 * size that grows only where there is room, the mindless Gillian, the Adjuchas that sinks back unfed,
 * and the player's own Cero. Mock players are plain {@link Player}s that are not in the world and never
 * tick, so every test drives {@link HollowBody#tickBody} and the rules in {@link MenosEvolution},
 * {@link MenosBody} and {@link HollowCero} directly. Creatures stand in the {@code field} template, on its
 * floor at relative y 2; the players there stand at {@link #SPOT}, facing south.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class MenosPlayerTests {
    /** Where a player stands in the field: in the middle of block (8, 2, 6), facing south. */
    private static final Vec3 SPOT = new Vec3(8.5, 2.0, 6.5);
    /** Where a player who is the prey stands: two blocks south of the spot. */
    private static final Vec3 PREY_SPOT = new Vec3(8.5, 2.0, 8.5);
    /** Where a player stands in the {@code empty} template. */
    private static final Vec3 ROOM = new Vec3(1.5, 1.0, 1.5);
    /** A fed Hollow's soul hunger, so neither ravenous claws nor slower healing change the numbers. */
    private static final float FED = 60.0F;
    /** How many random souls are asked whether they are destined, and how far their share may stray. */
    private static final int DESTINY_SAMPLE = 2_000;
    private static final double DESTINY_SHARE_MIN = 0.20, DESTINY_SHARE_MAX = 0.30;
    /** How many random souls may be asked to find one destined and one not. */
    private static final int DESTINY_SEARCH = 10_000;
    /** The scoreboard team the Cero test makes and takes away again. */
    private static final String TEAM = "bleach_cero_test";
    private static final double POSITION_EPSILON = 1.0E-6;

    private MenosPlayerTests() {
    }

    // ---- Helpers ----

    /** A Hollow's soul of this tier, with this much reiryoku, fed. */
    private static SoulData hollowSoul(int tier, float maxReiryoku) {
        SoulData data = ShinigamiTests.soul(SoulState.HOLLOW, maxReiryoku);
        data.setTier(PowerPath.HOLLOW, tier);
        HollowBody.setSatiety(data, FED);
        return data;
    }

    /** A mock Hollow in survival of this form, with this much reiryoku and fed, its record begun at that form. */
    private static Player menos(GameTestHelper helper, HollowTier form, float maxReiryoku, Vec3 at) {
        Player player = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, maxReiryoku, at);
        SoulData data = SoulLogic.get(player);
        data.setTier(PowerPath.HOLLOW, form.tier());
        setEvolution(data, data.hollow().evolution().startedAt(form.tier()));
        HollowBody.setSatiety(data, FED);
        return player;
    }

    private static void setEvolution(SoulData data, HollowProgress.Evolution evolution) {
        data.setHollow(data.hollow().withEvolution(evolution));
    }

    private static HollowProgress.Evolution evolution(SoulData data) {
        return data.hollow().evolution();
    }

    /**
     * Runs the body this many times, as the player's own ticks would, its tick count going on with them.
     * The game time stands still: every call is in this one tick of the test.
     */
    private static void tickBody(GameTestHelper helper, Player player, int ticks) {
        SoulData data = SoulLogic.get(player);
        for (int i = 0; i < ticks; i++) {
            player.tickCount++;
            HollowBody.tickBody(player, data, HollowPlayerTests.now(helper));
        }
    }

    /** Sizes the player by hand, as the Menos modifier would, without rebuilding its box. */
    private static void sizeByHand(Player player, double scale) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(MenosBody.SCALE_ID, scale - 1.0,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Whether none of the Menos body's modifiers is on the player, the size among them (nine checks). */
    static boolean menosOff(Player player) {
        return HollowPlayerTests.modifier(player, Attributes.MAX_HEALTH, MenosBody.HEALTH_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.MOVEMENT_SPEED, MenosBody.SPEED_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, MenosBody.REACH_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.BLOCK_INTERACTION_RANGE, MenosBody.REACH_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.STEP_HEIGHT, MenosBody.STEP_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.KNOCKBACK_RESISTANCE, MenosBody.STEADY_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, MenosBody.STEADY_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.SAFE_FALL_DISTANCE, MenosBody.FALL_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.SCALE, MenosBody.SCALE_ID) == null;
    }

    /** The Cero beams in the structure that this player gathers or fired. */
    private static List<CeroBeam> ownBeams(GameTestHelper helper, Player player) {
        return helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds(), beam -> beam.owner() == player);
    }

    /** This player's one Cero still gathering in the structure, or null when there is none or more than one. */
    @Nullable
    private static CeroBeam gathering(GameTestHelper helper, Player player) {
        List<CeroBeam> beams = ownBeams(helper, player).stream().filter(CeroBeam::isCharging).toList();
        return beams.size() == 1 ? beams.get(0) : null;
    }

    /** Whether a right click with the main hand's item is refused, as the event the game posts says. */
    private static boolean itemRefused(Player player) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND))
                .isCanceled();
    }

    /** Whether a right click on this block, with whatever is in the main hand, is refused. */
    private static boolean blockRefused(Player player, BlockHitResult hit) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND,
                hit.getBlockPos(), hit)).isCanceled();
    }

    /** Whether a right click on this creature is refused. */
    private static boolean entityRefused(Player player, Entity target) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target))
                .isCanceled();
    }

    /** Whether a right click on a part of this creature is refused. */
    private static boolean entityPartRefused(Player player, Entity target) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND,
                target, Vec3.ZERO)).isCanceled();
    }

    /** Whether this attribute's save holds the modifier, which it does only for a saved one. */
    private static boolean savedWith(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        ListTag modifiers = instance.save().getList("modifiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < modifiers.size(); i++) {
            if (modifiers.getCompound(i).getString("id").equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    private static void assertWithin(GameTestHelper helper, double actual, double expected, double epsilon, String what) {
        helper.assertTrue(Math.abs(actual - expected) <= epsilon, what + " is " + actual + ", not " + expected);
    }

    private static void assertNear(GameTestHelper helper, Vec3 actual, Vec3 expected, String what) {
        helper.assertTrue(actual.distanceTo(expected) < POSITION_EPSILON, what + " is " + actual + ", not " + expected);
    }

    /** Takes away any experience a kill dropped in the box, so nothing is left lying there. */
    private static void clearOrbs(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).forEach(Entity::discard);
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    private static void removeTeam(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    // ---- The record ----

    @GameTest(template = "empty")
    public static void evolutionRecordSavesAndOldSavesLoad(GameTestHelper helper) {
        HollowProgress.Evolution evolution = new HollowProgress.Evolution(3, 3, 7, 2, 12, 1,
                HollowProgress.Evolution.STALL_UNDESTINED);
        HollowProgress progress = new HollowProgress(42.5F, 17, 3, Map.of(HollowKind.SPIDER, 2), 1, true, 1234L, false,
                evolution);
        Tag saved = HollowProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow();
        HollowProgress loaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(progress), "the record with its evolution came back as " + loaded);
        ByteBuf buffer = Unpooled.buffer();
        try {
            HollowProgress.STREAM_CODEC.encode(buffer, progress);
            HollowProgress received = HollowProgress.STREAM_CODEC.decode(buffer);
            helper.assertTrue(received.equals(progress), "the record came through the network as " + received);
            helper.assertTrue(buffer.readableBytes() == 0, "the record left " + buffer.readableBytes() + " bytes");
        } finally {
            buffer.release();
        }
        SoulData soul = ShinigamiTests.soul(SoulState.HOLLOW, 90.0F);
        soul.setHollow(progress);
        Tag savedSoul = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, soul).getOrThrow();
        SoulData loadedSoul = SoulData.CODEC.parse(NbtOps.INSTANCE, savedSoul).getOrThrow();
        helper.assertTrue(loadedSoul.hollow().equals(progress), "the soul's record came back as " + loadedSoul.hollow());

        // A save from before the Menos: the same record, with only the keys it had then.
        helper.assertTrue(saved instanceof CompoundTag tag && tag.contains("evolution"),
                "the record was saved without its evolution: " + saved);
        CompoundTag old = ((CompoundTag) saved).copy();
        old.remove("evolution");
        HollowProgress oldLoaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(oldLoaded.evolution().equals(HollowProgress.Evolution.NONE),
                "an old save's evolution read as " + oldLoaded.evolution());
        helper.assertTrue(oldLoaded.souls() == 17 && oldLoaded.hollows().equals(progress.hollows()),
                "an old save's record read as " + oldLoaded);
        HollowProgress eightFields = new HollowProgress(42.5F, 17, 3, Map.of(HollowKind.SPIDER, 2), 1, true, 1234L, false);
        helper.assertTrue(eightFields.evolution().equals(HollowProgress.Evolution.NONE),
                "the record without an evolution has " + eightFields.evolution());
        // Read key by key, so a bad one falls back on its own.
        CompoundTag broken = new CompoundTag();
        broken.putString("at_tier", "x");
        broken.putInt("tier_hollows", 7);
        HollowProgress.Evolution forgiven = HollowProgress.Evolution.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
        helper.assertTrue(forgiven.atTier() == 1 && forgiven.tierHollows() == 7, "a bad form read as " + forgiven);

        HollowProgress.Evolution restarted = evolution.restarted();
        helper.assertTrue(restarted.equals(new HollowProgress.Evolution(1, 3, 0, 0, 0, 0,
                HollowProgress.Evolution.STALL_NONE)), "a purified soul's evolution is " + restarted);
        helper.assertTrue(progress.restarted().evolution().equals(restarted),
                "a restarted record's evolution is " + progress.restarted().evolution());
        HollowProgress.Evolution clamped = new HollowProgress.Evolution(9, 1, -2, 0, 0, 0, 5);
        helper.assertTrue(clamped.atTier() == 4 && clamped.peakTier() == 4 && clamped.tierHollows() == 0
                && clamped.stall() == HollowProgress.Evolution.STALL_FULL, "an evolution out of range kept " + clamped);

        // Sent by ordinal, so new keys only ever come last.
        helper.assertTrue(HollowKey.CERO.ordinal() == 3 && HollowKey.CERO_RELEASE.ordinal() == 4
                && HollowKey.CERO_CANCEL.ordinal() == 5, "the Cero keys' ordinals moved");
        HollowKeyPayload sent = new HollowKeyPayload(HollowKey.CERO_CANCEL, -1);
        ByteBuf keyBuffer = Unpooled.buffer();
        try {
            HollowKeyPayload.STREAM_CODEC.encode(keyBuffer, sent);
            HollowKeyPayload received = HollowKeyPayload.STREAM_CODEC.decode(keyBuffer);
            helper.assertTrue(received.equals(sent), "the Cero key payload came back as " + received);
            helper.assertTrue(keyBuffer.readableBytes() == 0,
                    "the Cero key payload left " + keyBuffer.readableBytes() + " bytes");
        } finally {
            keyBuffer.release();
        }
        for (String name : List.of("gillian", "adjuchas", "vasto_lorde")) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id(name)) != null,
                    "advancement bleach:" + name + " did not load");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void formsAreReadOnlyForHollows(GameTestHelper helper) {
        int forms = HollowPlayerLogic.LOOK_GILLIAN | HollowPlayerLogic.LOOK_ADJUCHAS | HollowPlayerLogic.LOOK_VASTO_LORDE;
        // A soul that is no Hollow keeps its Hollow tier, but has no form.
        SoulData shinigami = ShinigamiTests.soul(SoulState.SHINIGAMI, 90.0F);
        shinigami.setTier(PowerPath.HOLLOW, 3);
        helper.assertTrue(HollowTier.of(shinigami) == HollowTier.HOLLOW, "a Shinigami reads as " + HollowTier.of(shinigami));
        helper.assertTrue((HollowPlayerLogic.hollowLook(shinigami) & forms) == 0, "a Shinigami is drawn as a Menos");
        helper.assertTrue(HollowTier.of(hollowSoul(0, 90.0F)) == HollowTier.HOLLOW, "a Hollow of tier 0 is no plain Hollow");
        helper.assertTrue(HollowTier.of(hollowSoul(9, 90.0F)) == HollowTier.VASTO_LORDE,
                "a Hollow of tier 9 is no Vasto Lorde");

        // A plain Hollow's numbers are bodyNumbersScaleWithReiryoku's.
        SoulData plain = hollowSoul(1, 90.0F);
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.clawBonus(plain), 5.3717, "a plain Hollow's claws at 90");
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.hideArmour(plain), 7.7947, "a plain Hollow's hide at 90");
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.regenPerSecond(plain), 1.225,
                "a plain Hollow's regeneration at 90");
        helper.assertTrue(HollowPlayerLogic.clawSpeedModifier(plain) == HollowPlayerLogic.clawSpeedModifier(),
                "a plain Hollow's claw speed is " + HollowPlayerLogic.clawSpeedModifier(plain));
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.clawSpeedModifier(plain), -0.5,
                "a plain Hollow's claw speed");
        helper.assertTrue((HollowPlayerLogic.hollowLook(plain) & forms) == 0, "a plain Hollow is drawn as a Menos");

        SoulData gillian = hollowSoul(2, 90.0F);
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.clawBonus(gillian), 8.0576, "a Gillian's claws at 90");
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.hideArmour(gillian), 11.7947, "a Gillian's hide at 90");
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.regenPerSecond(gillian), 1.225,
                "a Gillian's regeneration at 90");
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.clawSpeedModifier(gillian), -0.75, "a Gillian's claw speed");
        helper.assertTrue((HollowPlayerLogic.hollowLook(gillian) & HollowPlayerLogic.LOOK_GILLIAN) != 0,
                "a Gillian is not drawn as one");

        SoulData vasto = hollowSoul(4, 90.0F);
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.regenPerSecond(vasto), 2.45,
                "a Vasto Lorde's regeneration at 90");
        helper.assertTrue((HollowPlayerLogic.hollowLook(vasto) & HollowPlayerLogic.LOOK_VASTO_LORDE) != 0,
                "a Vasto Lorde is not drawn as one");
        helper.succeed();
    }

    // ---- The gates ----

    /** A Hollow's soul of this tier holding these souls and Spider Hollows, with this evolution. */
    private static SoulData gate(int tier, int souls, int spiders, HollowProgress.Evolution evolution) {
        SoulData data = hollowSoul(tier, 90.0F);
        data.setHollow(data.hollow().withSouls(souls).withHollows(HollowKind.SPIDER, spiders).withEvolution(evolution));
        return data;
    }

    private static void assertNext(GameTestHelper helper, SoulData data, @Nullable HollowTier expected, String what) {
        HollowTier next = MenosEvolution.nextTier(data);
        helper.assertTrue(next == expected, what + " rises to " + next + ", not " + expected);
    }

    @GameTest(template = "empty")
    public static void gatesCountWhatWasDevoured(GameTestHelper helper) {
        HollowProgress.Evolution none = HollowProgress.Evolution.NONE;
        assertNext(helper, gate(1, 249, 20, none), null, "a Hollow of 249 souls and 20 Hollows");
        assertNext(helper, gate(1, 250, 19, none), null, "a Hollow of 250 souls and 19 Hollows");
        assertNext(helper, gate(1, 250, 20, none), HollowTier.GILLIAN, "a Hollow of 250 souls and 20 Hollows");

        assertNext(helper, gate(2, 0, 0, new HollowProgress.Evolution(2, 2, 29, 2, 0, 0, 0)), null,
                "a Gillian of 29 Hollows and 2 Menos");
        assertNext(helper, gate(2, 0, 0, new HollowProgress.Evolution(2, 2, 30, 1, 0, 0, 0)), null,
                "a Gillian of 30 Hollows and 1 Menos");
        assertNext(helper, gate(2, 0, 0, new HollowProgress.Evolution(2, 2, 30, 2, 0, 0, 0)), HollowTier.ADJUCHAS,
                "a Gillian of 30 Hollows and 2 Menos");
        // A Gillian that sank back once. Soft mode doubles the Hollows, canon never lets it rise, off asks the same.
        HollowTier.Regression mode = BleachConfig.get(BleachConfig.ADJUCHAS_REGRESSION);
        assertNext(helper, gate(2, 0, 0, new HollowProgress.Evolution(2, 3, 59, 2, 0, 1, 0)),
                mode == HollowTier.Regression.OFF ? HollowTier.ADJUCHAS : null,
                "in " + mode + " mode, a fallen Gillian of 59 Hollows and 2 Menos");
        assertNext(helper, gate(2, 0, 0, new HollowProgress.Evolution(2, 3, 60, 2, 0, 1, 0)),
                mode == HollowTier.Regression.CANON ? null : HollowTier.ADJUCHAS,
                "in " + mode + " mode, a fallen Gillian of 60 Hollows and 2 Menos");

        assertNext(helper, gate(3, 0, 0, new HollowProgress.Evolution(3, 3, 39, 3, 0, 0, 0)), null,
                "an Adjuchas of 39 Hollows and 3 Adjuchas");
        assertNext(helper, gate(3, 0, 0, new HollowProgress.Evolution(3, 3, 40, 2, 0, 0, 0)), null,
                "an Adjuchas of 40 Hollows and 2 Adjuchas");
        assertNext(helper, gate(3, 0, 0, new HollowProgress.Evolution(3, 3, 40, 3, 0, 0, 0)), HollowTier.VASTO_LORDE,
                "an Adjuchas of 40 Hollows and 3 Adjuchas");
        assertNext(helper, gate(4, 10_000, 100, new HollowProgress.Evolution(4, 4, 100, 100, 0, 0, 0)), null,
                "a Vasto Lorde");

        // Both of the Gillian's gates met in a record a Shinigami kept: only the guard keeps it from rising.
        SoulData shinigami = ShinigamiTests.soul(SoulState.SHINIGAMI, 90.0F);
        shinigami.setHollow(shinigami.hollow().withSouls(10_000).withHollows(HollowKind.SPIDER, 40));
        assertNext(helper, shinigami, null, "a Shinigami with 10000 souls and 40 Hollows");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vastoLordeIsDestinedAndCounted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        long seed = level.getSeed();
        UUID destinedId = null;
        UUID undestinedId = null;
        for (int i = 0; i < DESTINY_SEARCH && (destinedId == null || undestinedId == null); i++) {
            UUID id = UUID.randomUUID();
            if (MenosEvolution.destined(id, seed)) {
                destinedId = destinedId == null ? id : destinedId;
            } else {
                undestinedId = undestinedId == null ? id : undestinedId;
            }
        }
        helper.assertTrue(destinedId != null && undestinedId != null,
                "no destined soul, or none that is not, among " + DESTINY_SEARCH);
        UUID same = new UUID(destinedId.getMostSignificantBits(), destinedId.getLeastSignificantBits());
        helper.assertTrue(MenosEvolution.destined(same, seed) && MenosEvolution.destined(destinedId, seed)
                && !MenosEvolution.destined(undestinedId, seed), "destiny changed for the same soul in the same world");
        int destined = 0;
        for (int i = 0; i < DESTINY_SAMPLE; i++) {
            if (MenosEvolution.destined(UUID.randomUUID(), seed)) {
                destined++;
            }
        }
        double share = destined / (double) DESTINY_SAMPLE;
        helper.assertTrue(share >= DESTINY_SHARE_MIN && share <= DESTINY_SHARE_MAX,
                share + " of " + DESTINY_SAMPLE + " souls are destined");

        MenosEvolution.VastoLordes lordes = MenosEvolution.VastoLordes.get(server);
        int most = BleachConfig.get(BleachConfig.VASTO_MAX);
        List<UUID> added = new ArrayList<>();
        try {
            helper.assertTrue(lordes.count() < most, "the test world already holds " + lordes.count() + " Vasto Lorde");
            while (lordes.count() < most) {
                UUID other = UUID.randomUUID();
                lordes.add(other);
                added.add(other);
            }
            Player player = menos(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
            player.setUUID(destinedId);
            SoulData data = SoulLogic.get(player);
            setEvolution(data, new HollowProgress.Evolution(3, 3, 40, 3, 0, 0, HollowProgress.Evolution.STALL_NONE));
            helper.assertTrue(MenosEvolution.destined(player), "the destined mock is not destined");

            MenosEvolution.tryEvolve(player, data, HollowPlayerTests.now(helper));
            helper.assertTrue(HollowTier.of(data) == HollowTier.ADJUCHAS, "a full server made a " + HollowTier.of(data));
            helper.assertTrue(evolution(data).stall() == HollowProgress.Evolution.STALL_FULL,
                    "a full server left the stall at " + evolution(data).stall());
            helper.assertFalse(MenosEvolution.vastoRoom(server, destinedId), "a full server has room");

            lordes.remove(added.remove(added.size() - 1));
            MenosEvolution.tryEvolve(player, data, HollowPlayerTests.now(helper));
            helper.assertTrue(HollowTier.of(data) == HollowTier.VASTO_LORDE,
                    "room on the server made a " + HollowTier.of(data));
            helper.assertTrue(lordes.contains(destinedId), "the new Vasto Lorde is not counted");
            helper.assertTrue(MenosEvolution.vastoRoom(server, destinedId), "a counted Vasto Lorde has no room");

            // Purified, it gives its place up on its next note (every 20 ticks, from tick 0).
            HollowBody.purify(data);
            player.tickCount = 0;
            HollowBody.tickBody(player, data, HollowPlayerTests.now(helper));
            helper.assertFalse(lordes.contains(destinedId), "a purified Vasto Lorde is still counted");

            Player undestined = menos(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
            undestined.setUUID(undestinedId);
            SoulData undestinedData = SoulLogic.get(undestined);
            setEvolution(undestinedData, new HollowProgress.Evolution(3, 3, 40, 3, 0, 0,
                    HollowProgress.Evolution.STALL_NONE));
            MenosEvolution.tryEvolve(undestined, undestinedData, HollowPlayerTests.now(helper));
            helper.assertTrue(HollowTier.of(undestinedData) == HollowTier.ADJUCHAS,
                    "a soul not destined became a " + HollowTier.of(undestinedData));
            helper.assertTrue(evolution(undestinedData).stall() == HollowProgress.Evolution.STALL_UNDESTINED,
                    "a soul not destined has the stall " + evolution(undestinedData).stall());
            helper.assertFalse(lordes.contains(undestinedId), "a soul not destined is counted");
        } finally {
            added.forEach(lordes::remove);
            lordes.remove(destinedId);
            lordes.remove(undestinedId);
        }
        helper.succeed();
    }

    // ---- Rising ----

    @GameTest(template = "field")
    public static void devouringRaisesAGillian(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.HOLLOW, 90.0F, SPOT);
        SoulData data = SoulLogic.get(player);
        data.setHollow(data.hollow().withSouls(249).withHollows(HollowKind.SPIDER, 19));
        player.setHealth(10.0F);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        spider.soul();
        spider.setHealth(spider.getMaxHealth() * 0.2F);
        long now = HollowPlayerTests.now(helper);
        try {
            helper.assertTrue(DevourEvents.complete(player, spider, now), "devouring the Spider Hollow did not kill it");
            helper.assertTrue(data.tier(PowerPath.HOLLOW) == 2 && HollowTier.of(data) == HollowTier.GILLIAN,
                    "the 250th soul and 20th Hollow made a " + HollowTier.of(data));
            HollowProgress.Evolution risen = evolution(data);
            helper.assertTrue(risen.atTier() == 2 && risen.peakTier() == 2 && risen.tierHollows() == 0
                    && risen.tierMenos() == 0, "the new Gillian's record is " + risen);
            // 90 and a little growth from the meal, and a fifth more for a Gillian.
            helper.assertTrue(data.maxReiryoku() >= 108.0F, "the new Gillian's maximum reiryoku is " + data.maxReiryoku());
            HollowPlayerTests.assertClose(helper, player.getMaxHealth(), 80.0, "the new Gillian's maximum health");
            HollowPlayerTests.assertClose(helper, player.getHealth(), 80.0, "the new Gillian's health");
            helper.assertTrue(savedWith(player, Attributes.MAX_HEALTH, MenosBody.HEALTH_ID),
                    "the Gillian's health is not saved with the player");
            helper.assertFalse(savedWith(player, Attributes.MOVEMENT_SPEED, MenosBody.SPEED_ID),
                    "the Gillian's speed is saved with the player");

            MenosEvolution.evolve(player, data, HollowTier.ADJUCHAS, now);
            MenosEvolution.regress(player, data, now);
            helper.assertTrue(HollowTier.of(data) == HollowTier.GILLIAN && evolution(data).peakTier() == 3,
                    "sinking back left a " + HollowTier.of(data) + " with the record " + evolution(data));
            float max = data.maxReiryoku();
            MenosEvolution.evolve(player, data, HollowTier.ADJUCHAS, now);
            helper.assertTrue(data.maxReiryoku() == max,
                    "rising again to Adjuchas grew the maximum from " + max + " to " + data.maxReiryoku());
            helper.assertTrue(evolution(data).regressions() == 1, "the falls counted are " + evolution(data).regressions());
        } finally {
            discard(spider);
            clearOrbs(helper);
        }
        helper.succeed();
    }

    // ---- The body ----

    @GameTest(template = "field", skyAccess = true)
    public static void giantGrowsOnlyWithRoomAndSqueezes(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.GILLIAN, 90.0F, SPOT);
        SoulData data = SoulLogic.get(player);
        for (int x = 6; x <= 10; x++) {
            for (int z = 4; z <= 8; z++) {
                helper.setBlock(x, 6, z, Blocks.STONE);
            }
        }
        tickBody(helper, player, 40);
        // The stone is 4 blocks above the feet; at 2.25 the body would stand 4.05 tall.
        assertWithin(helper, MenosBody.grownScale(player), 2.2, 0.01, "the size under the stone");
        // The box follows the size without a tick of the player's own.
        assertWithin(helper, player.getBbHeight(), 3.96, 0.02, "the height under the stone");

        for (int x = 6; x <= 10; x++) {
            for (int z = 4; z <= 8; z++) {
                helper.setBlock(x, 6, z, Blocks.AIR);
            }
        }
        tickBody(helper, player, 80);
        assertWithin(helper, MenosBody.grownScale(player), 6.0, 0.001, "the size in the open");
        HollowPlayerTests.assertModifier(helper, player, Attributes.ENTITY_INTERACTION_RANGE, MenosBody.REACH_ID, 8.1,
                AttributeModifier.Operation.ADD_VALUE, "a Gillian's reach for creatures");
        HollowPlayerTests.assertModifier(helper, player, Attributes.BLOCK_INTERACTION_RANGE, MenosBody.REACH_ID, 8.1,
                AttributeModifier.Operation.ADD_VALUE, "a Gillian's reach for blocks");
        HollowPlayerTests.assertModifier(helper, player, Attributes.STEP_HEIGHT, MenosBody.STEP_ID, 1.4,
                AttributeModifier.Operation.ADD_VALUE, "a Gillian's step");
        HollowPlayerTests.assertModifier(helper, player, Attributes.KNOCKBACK_RESISTANCE, MenosBody.STEADY_ID, 0.6,
                AttributeModifier.Operation.ADD_VALUE, "a Gillian's steadiness");
        HollowPlayerTests.assertModifier(helper, player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, MenosBody.STEADY_ID, 0.6,
                AttributeModifier.Operation.ADD_VALUE, "a Gillian's steadiness in a blast");
        HollowPlayerTests.assertModifier(helper, player, Attributes.SAFE_FALL_DISTANCE, MenosBody.FALL_ID, 9.0,
                AttributeModifier.Operation.ADD_VALUE, "a Gillian's safe fall");
        HollowPlayerTests.assertModifier(helper, player, Attributes.MOVEMENT_SPEED, MenosBody.SPEED_ID, -0.3,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "a Gillian's speed");

        // Seven squeezes to 4.25 bring the head under the stone, then three steps grow it up to 4.40.
        helper.setBlock(8, 10, 6, Blocks.STONE);
        player.fallDistance = 4.0F;
        tickBody(helper, player, 10);
        double squeezed = MenosBody.grownScale(player);
        helper.assertTrue(squeezed >= 4.0 && squeezed <= 4.44, "the stone in the body squeezed it to " + squeezed);
        helper.assertTrue(player.fallDistance == 0.0F, "the squeezed body kept a fall of " + player.fallDistance);

        data.setTier(PowerPath.HOLLOW, HollowTier.HOLLOW.tier());
        tickBody(helper, player, 1);
        helper.assertTrue(MenosBody.grownScale(player) == 1.0 && player.getScale() == 1.0F,
                "a plain Hollow again stands at " + player.getScale());
        helper.assertTrue(menosOff(player), "a plain Hollow kept a Menos's body");
        HollowBody.purify(data);
        tickBody(helper, player, 1);
        helper.assertTrue(HollowPlayerTests.bodyOff(player) && menosOff(player), "a purified soul kept a Hollow's body");
        helper.setBlock(8, 10, 6, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void gillianIsMindlessAndTooBig(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.GILLIAN, 90.0F, SPOT);
        SoulData data = SoulLogic.get(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        sizeByHand(player, 6.0);
        Boat boat = helper.spawn(EntityType.BOAT, 8, 2, 9);
        AcademyInstructor instructor = HollowPlayerTests.placed(helper, ModEntities.ACADEMY_INSTRUCTOR.get(), 11, 9);
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 7));
        BlockHitResult onFloor = new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false);
        try {
            // A mock player is a local player too (isLocalPlayer), so the refusals must hold on both sides.
            helper.assertTrue(MenosBody.mindless(player), "a Gillian is not mindless");
            helper.assertTrue(itemRefused(player), "a mindless Gillian used the dirt it held");
            helper.assertTrue(blockRefused(player, onFloor), "a mindless Gillian used the dirt on a block");
            helper.assertTrue(entityRefused(player, boat), "a mindless Gillian dealt with a boat");
            helper.assertTrue(entityPartRefused(player, boat), "a mindless Gillian dealt with a part of a boat");
            helper.assertFalse(entityRefused(player, instructor), "a mindless Gillian may not deal with a Shinigami");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertFalse(blockRefused(player, onFloor), "a mindless Gillian's bare hand may not use a block");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            helper.assertFalse(player.startRiding(boat), "a giant Gillian boarded a boat");
            HollowBody.tickBody(player, data, HollowPlayerTests.now(helper));
            helper.assertTrue(player.getPortalCooldown() > 0, "a giant Gillian may go through a portal");

            data.setTier(PowerPath.HOLLOW, HollowTier.ADJUCHAS.tier());
            sizeByHand(player, 1.5);
            player.setPortalCooldown(0);
            helper.assertFalse(MenosBody.mindless(player), "an Adjuchas is mindless");
            helper.assertFalse(itemRefused(player), "an Adjuchas may not use the dirt it holds");
            helper.assertFalse(blockRefused(player, onFloor), "an Adjuchas may not use the dirt on a block");
            helper.assertFalse(entityRefused(player, boat), "an Adjuchas may not deal with a boat");
            helper.assertFalse(entityPartRefused(player, boat), "an Adjuchas may not deal with a part of a boat");
            helper.assertTrue(player.startRiding(boat), "an Adjuchas could not board a boat");
            HollowBody.tickBody(player, data, HollowPlayerTests.now(helper));
            helper.assertTrue(player.getPortalCooldown() == 0, "an Adjuchas is held off portals");
            helper.assertTrue(player.getVehicle() == boat, "the body tick took the Adjuchas off its boat");
        } finally {
            player.stopRiding();
            discard(boat, instructor);
        }
        helper.succeed();
    }

    // ---- Sinking back ----

    @GameTest(template = "empty")
    public static void adjuchasRegressesUnlessItEatsHollows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = menos(helper, HollowTier.ADJUCHAS, 90.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);
        int minutes = BleachConfig.get(BleachConfig.REGRESS_MINUTES);
        boolean clock = BleachConfig.get(BleachConfig.ADJUCHAS_REGRESSION) != HollowTier.Regression.OFF;
        setEvolution(data, new HollowProgress.Evolution(3, 3, 0, 0, 24, 0, HollowProgress.Evolution.STALL_NONE));

        // Five minutes left (the warning, told only to a real player), when regression is on at all.
        MenosEvolution.onMinute(player, data, now);
        helper.assertTrue(evolution(data).hungerMinutes() == (clock ? 25 : 24),
                "a minute unfed made " + evolution(data).hungerMinutes() + " minutes");
        if (clock) {
            helper.assertTrue(MenosEvolution.minutesLeft(data) == minutes - 25,
                    "an Adjuchas 25 minutes unfed has " + MenosEvolution.minutesLeft(data) + " left");
        }

        MenosEvolution.afterMeal(player, data, new DevourLogic.Meal(DevourLogic.Prey.HOLLOW, HollowKind.SPIDER, 40.0F, 30.0F,
                4, 0, true), now);
        HollowProgress.Evolution fed = evolution(data);
        helper.assertTrue(fed.hungerMinutes() == 0 && fed.tierHollows() == 1 && HollowTier.of(data) == HollowTier.ADJUCHAS,
                "a Spider Hollow left the record " + fed);
        MenosEvolution.afterMeal(player, data, new DevourLogic.Meal(DevourLogic.Prey.PLUS, null, 12.0F, 30.0F, 1, 0, false),
                now);
        helper.assertTrue(evolution(data).equals(fed), "a Plus changed the record to " + evolution(data));

        setEvolution(data, fed.withHungerMinutes(minutes - 1));
        MenosEvolution.onMinute(player, data, now);
        HollowProgress.Evolution fallen = evolution(data);
        if (clock) {
            helper.assertTrue(HollowTier.of(data) == HollowTier.GILLIAN && data.tier(PowerPath.HOLLOW) == 2,
                    "the last minute unfed left a " + HollowTier.of(data));
            helper.assertTrue(fallen.atTier() == 2 && fallen.peakTier() == 3 && fallen.regressions() == 1
                    && fallen.tierHollows() == 0 && fallen.tierMenos() == 0 && fallen.hungerMinutes() == 0,
                    "sinking back left the record " + fallen);
        } else {
            helper.assertTrue(HollowTier.of(data) == HollowTier.ADJUCHAS && fallen.hungerMinutes() == minutes - 1,
                    "with regression off, the last minute unfed left a " + HollowTier.of(data) + " and " + fallen);
        }

        Player creative = HollowPlayerTests.soulPlayer(helper, GameType.CREATIVE, SoulState.HOLLOW, 90.0F, ROOM);
        SoulLogic.get(creative).setTier(PowerPath.HOLLOW, HollowTier.ADJUCHAS.tier());
        helper.assertFalse(MenosEvolution.clockRuns(creative), "the clock runs in creative mode");
        boolean arrivals = HollowLogic.worldAllowsArrivals(level.getDifficulty(),
                level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING), level.dimension() == Level.OVERWORLD);
        if (HollowPlayerTests.peaceful(helper) || !arrivals) {
            Player survival = menos(helper, HollowTier.ADJUCHAS, 90.0F, ROOM);
            helper.assertFalse(MenosEvolution.clockRuns(survival), "the clock runs where no Hollow comes");
        }
        helper.succeed();
    }

    // ---- Menos as prey ----

    private static DevourLogic.Meal preyMeal(GameTestHelper helper, Player devourer, HollowTier form) {
        return DevourLogic.meal(devourer, menos(helper, form, 90.0F, PREY_SPOT));
    }

    @GameTest(template = "field")
    public static void menosPreyCountsAsMenos(GameTestHelper helper) {
        Player gillian = menos(helper, HollowTier.GILLIAN, 400.0F, SPOT);
        long now = HollowPlayerTests.now(helper);
        DevourLogic.Meal plainMeal = preyMeal(helper, gillian, HollowTier.HOLLOW);
        DevourLogic.Meal gillianMeal = preyMeal(helper, gillian, HollowTier.GILLIAN);
        DevourLogic.Meal adjuchasMeal = preyMeal(helper, gillian, HollowTier.ADJUCHAS);
        DevourLogic.Meal soulMeal = DevourLogic.meal(gillian,
                HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 90.0F, PREY_SPOT));
        helper.assertTrue(plainMeal.kind() == null && plainMeal.hollowLike(),
                "a plain Hollow player is the meal " + plainMeal);
        helper.assertTrue(gillianMeal.kind() == HollowKind.GILLIAN && gillianMeal.hollowLike(),
                "a Gillian player is the meal " + gillianMeal);
        helper.assertTrue(adjuchasMeal.kind() == HollowKind.ADJUCHAS && adjuchasMeal.hollowLike(),
                "an Adjuchas player is the meal " + adjuchasMeal);
        helper.assertTrue(soulMeal.kind() == null && !soulMeal.hollowLike(), "a Shinigami player is the meal " + soulMeal);

        SoulData gillianData = SoulLogic.get(gillian);
        HollowProgress.Evolution before = evolution(gillianData);
        MenosEvolution.afterMeal(gillian, gillianData, gillianMeal, now);
        HollowProgress.Evolution after = evolution(gillianData);
        helper.assertTrue(after.tierMenos() == before.tierMenos() + 1 && after.tierHollows() == before.tierHollows() + 1,
                "a Gillian that devoured a Gillian has the record " + after);

        Player adjuchas = menos(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        SoulData adjuchasData = SoulLogic.get(adjuchas);
        before = evolution(adjuchasData);
        MenosEvolution.afterMeal(adjuchas, adjuchasData, gillianMeal, now);
        after = evolution(adjuchasData);
        helper.assertTrue(after.tierMenos() == before.tierMenos() && after.tierHollows() == before.tierHollows() + 1,
                "an Adjuchas that devoured a Gillian has the record " + after);
        before = after;
        MenosEvolution.afterMeal(adjuchas, adjuchasData, adjuchasMeal, now);
        after = evolution(adjuchasData);
        helper.assertTrue(after.tierMenos() == before.tierMenos() + 1, "an Adjuchas that devoured an Adjuchas has the record "
                + after);
        helper.succeed();
    }

    // ---- The player's Cero ----

    @GameTest(template = "field", timeoutTicks = 220)
    public static void playerCeroGathersFiresAndPays(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.GILLIAN, 400.0F, SPOT);
        SoulData data = SoulLogic.get(player);
        // Six blocks south of the spot, in the line of the Gillian's gaze.
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 12);
        float cost = HollowCero.cost(data);
        HollowPlayerTests.assertClose(helper, cost, 48.0, "the cost of a Cero at 400");

        HollowCero.Refusal first = HollowCero.start(player, data, HollowPlayerTests.now(helper));
        helper.assertTrue(first == null, "the Cero was refused as " + first);
        CeroBeam fired = gathering(helper, player);
        helper.assertTrue(fired != null && fired.owner() == player, "the Cero does not gather at the player's mouth");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, HollowPlayerTests.now(helper))
                & HollowPlayerLogic.LOOK_CERO_CHARGING) != 0, "the gathering Cero is not drawn");
        HollowCero.Refusal second = HollowCero.start(player, data, HollowPlayerTests.now(helper));
        helper.assertTrue(second == null && ownBeams(helper, player).size() == 1,
                "a second press gave " + second + " and " + ownBeams(helper, player).size() + " beams");
        CeroBeam[] later = new CeroBeam[1];
        helper.startSequence()
                // 30 of the Gillian's 50 ticks: it fires at three fifths of its strength.
                .thenExecuteAfter(30, () -> {
                    long now = HollowPlayerTests.now(helper);
                    HollowCero.release(player, data, now);
                    helper.assertTrue(fired.isFired(), "letting go after 30 ticks did not fire the Cero");
                    CeroBeam.Shot shot = fired.lastShot();
                    helper.assertTrue(shot != null && shot.struck().contains(spider),
                            "the Cero struck " + (shot != null ? shot.struck() : "nothing"));
                    helper.assertTrue(spider.getHealth() < spider.getMaxHealth(), "the Spider Hollow in the Cero is unhurt");
                    HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0F - cost, "the reiryoku after a Cero");
                    helper.assertFalse(HollowCero.charging(data), "the fired Cero is still gathering");
                    HollowCero.Refusal soon = HollowCero.start(player, data, now);
                    helper.assertTrue(soon == HollowCero.Refusal.COOLDOWN, "a Cero just after one gave " + soon);
                })
                // Past the four seconds' cooldown.
                .thenExecuteAfter(85, () -> {
                    HollowCero.Refusal again = HollowCero.start(player, data, HollowPlayerTests.now(helper));
                    helper.assertTrue(again == null, "a Cero after the cooldown was refused as " + again);
                    later[0] = gathering(helper, player);
                    helper.assertTrue(later[0] != null, "the second Cero does not gather");
                })
                // It fires by itself at its 50th tick, and goes 12 ticks later: paid in the same tick it is seen fired.
                .thenWaitUntil(() -> helper.assertTrue(later[0].isFired(), "the second Cero has not fired by itself"))
                .thenExecute(() -> {
                    HollowCero.tick(player, data, HollowPlayerTests.now(helper));
                    HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0F - 2.0F * cost,
                            "the reiryoku after a Cero fired by itself");
                    helper.assertFalse(HollowCero.charging(data), "the Cero that fired by itself is still gathering");
                    ownBeams(helper, player).forEach(Entity::discard);
                    discard(spider);
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 60)
    public static void ceroFizzlesAndBreaksForFree(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.GILLIAN, 400.0F, SPOT);
        SoulData data = SoulLogic.get(player);
        HollowCero.Refusal first = HollowCero.start(player, data, HollowPlayerTests.now(helper));
        CeroBeam early = gathering(helper, player);
        helper.assertTrue(first == null && early != null, "the Cero did not gather: " + first);
        helper.startSequence()
                // 10 of 50 ticks is a fifth: too soon.
                .thenExecuteAfter(10, () -> {
                    long now = HollowPlayerTests.now(helper);
                    HollowCero.release(player, data, now);
                    helper.assertTrue(early.isRemoved() && !early.isFired(), "a Cero let go at a fifth did not fizzle");
                    HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after a fizzle");
                    helper.assertFalse(HollowCero.charging(data), "the fizzled Cero is still gathering");
                    HollowCero.Refusal again = HollowCero.start(player, data, now);
                    CeroBeam held = gathering(helper, player);
                    helper.assertTrue(again == null && held != null, "a Cero after a fizzle was refused as " + again);

                    PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 8, 8);
                    plus.soul();
                    try {
                        player.lookAt(EntityAnchorArgument.Anchor.EYES, plus.getBoundingBox().getCenter());
                        DevourLogic.Refusal hold = DevourEvents.startDevour(player, data, plus, now);
                        helper.assertTrue(hold == null, "the hold on a Plus was refused as " + hold);
                        helper.assertTrue(held.isRemoved() && !HollowCero.charging(data), "a devour hold kept the Cero");
                        HollowCero.Refusal jaws = HollowCero.start(player, data, now);
                        helper.assertTrue(jaws == HollowCero.Refusal.DEVOURING, "a Cero during a hold gave " + jaws);
                        DevourEvents.stopDevour(player, data, null, now);
                    } finally {
                        discard(plus);
                    }
                    HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after a broken gather");

                    // A fresh crack breaks the gather.
                    HollowCero.Refusal third = HollowCero.start(player, data, now);
                    CeroBeam cracked = gathering(helper, player);
                    helper.assertTrue(third == null && cracked != null, "a Cero after the hold was refused as " + third);
                    helper.assertTrue(HollowBody.onMaskStruck(data, now + 1), "a blow to a whole mask was no fresh crack");
                    HollowCero.tick(player, data, now + 1);
                    helper.assertTrue(cracked.isRemoved() && !HollowCero.charging(data), "a fresh crack kept the Cero");

                    // A blow to the mask already cracked does not.
                    HollowCero.Refusal fourth = HollowCero.start(player, data, now + 2);
                    CeroBeam whole = gathering(helper, player);
                    helper.assertTrue(fourth == null && whole != null, "a Cero after the crack was refused as " + fourth);
                    helper.assertFalse(HollowBody.onMaskStruck(data, now + 3), "a blow to a cracked mask was a fresh crack");
                    HollowCero.tick(player, data, now + 3);
                    helper.assertTrue(whole.isCharging() && HollowCero.charging(data),
                            "a blow to a cracked mask broke the Cero");
                    HollowCero.cancel(player, data);
                    helper.assertTrue(whole.isRemoved() && !HollowCero.charging(data), "a cancelled Cero is still gathering");
                    HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after the fizzles");

                    Player plain = menos(helper, HollowTier.HOLLOW, 400.0F, SPOT);
                    HollowCero.Refusal notMenos = HollowCero.start(plain, SoulLogic.get(plain), now);
                    helper.assertTrue(notMenos == HollowCero.Refusal.NOT_MENOS, "a plain Hollow's Cero gave " + notMenos);
                    Player weak = menos(helper, HollowTier.GILLIAN, 400.0F, SPOT);
                    SoulLogic.get(weak).setReiryoku(5.0F);
                    HollowCero.Refusal tooWeak = HollowCero.start(weak, SoulLogic.get(weak), now);
                    helper.assertTrue(tooWeak == HollowCero.Refusal.TOO_WEAK,
                            "a Gillian at 5 reiryoku's Cero gave " + tooWeak);
                })
                .thenSucceed();
    }

    /** In the Cero's griefing batch, so no other test sees mob griefing change. */
    @GameTest(template = "field", batch = "cero_griefing")
    public static void ceroKeepsToPlayerRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player shooter = menos(helper, HollowTier.GILLIAN, 400.0F, new Vec3(1.5, 2.0, 8.5));
        SoulData data = SoulLogic.get(shooter);
        Wolf wolf = HollowPlayerTests.placed(helper, EntityType.WOLF, 2, 2);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(shooter.getUUID());
        PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 5, 2);
        AcademyInstructor instructor = HollowPlayerTests.placed(helper, ModEntities.ACADEMY_INSTRUCTOR.get(), 8, 2);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 12, 2);
        Player creative = HollowPlayerTests.soulPlayer(helper, GameType.CREATIVE, SoulState.HUMAN, 90.0F, PREY_SPOT);
        Player teammate = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 90.0F, PREY_SPOT);
        Scoreboard scoreboard = level.getScoreboard();
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean savedGriefing = griefing.get();
        boolean savedBuild = shooter.getAbilities().mayBuild;
        try {
            helper.assertFalse(CeroBeam.mayStrike(shooter, wolf), "the Cero strikes the shooter's own wolf");
            helper.assertFalse(CeroBeam.mayStrike(shooter, plus), "the Cero strikes a Plus");
            helper.assertFalse(CeroBeam.mayStrike(shooter, instructor), "the Cero strikes a Shinigami");
            helper.assertFalse(CeroBeam.mayStrike(shooter, creative), "the Cero strikes a player in creative mode");
            helper.assertTrue(CeroBeam.mayStrike(shooter, teammate), "with no team, the Cero spares another player");
            removeTeam(scoreboard);
            PlayerTeam team = scoreboard.addPlayerTeam(TEAM);
            team.setAllowFriendlyFire(false);
            // Every mock player has the same name, so the other player is on the shooter's team too.
            scoreboard.addPlayerToTeam(shooter.getScoreboardName(), team);
            helper.assertFalse(CeroBeam.mayStrike(shooter, teammate), "the Cero strikes a teammate with friendly fire off");
            team.setAllowFriendlyFire(true);
            helper.assertTrue(CeroBeam.mayStrike(shooter, teammate), "the Cero spares a teammate with friendly fire on");
            helper.assertTrue(CeroBeam.mayStrike(shooter, spider), "the Cero spares a Spider Hollow");
            removeTeam(scoreboard);
            discard(wolf, plus, instructor, spider);

            Vec3 from = helper.absoluteVec(new Vec3(2.5, 3.0, 8.5));
            Vec3 east = new Vec3(1.0, 0.0, 0.0);
            CeroBeam.Spec spec = HollowCero.spec(data, HollowTier.GILLIAN);
            griefing.set(true, level.getServer());
            buildWall(helper);
            CeroBeam.Shot carved = CeroBeam.fire(level, shooter, null, from, east, spec);
            helper.assertTrue(carved.hitBlock() && carved.blocksBroken() >= 1,
                    "the player's Cero broke " + carved.blocksBroken() + " blocks with mob griefing");
            helper.assertBlockPresent(Blocks.AIR, 8, 3, 8);
            buildWall(helper);
            shooter.getAbilities().mayBuild = false;
            CeroBeam.Shot held = CeroBeam.fire(level, shooter, null, from, east, spec);
            helper.assertTrue(held.hitBlock() && held.blocksBroken() == 0,
                    "the Cero of a player who may not build broke " + held.blocksBroken() + " blocks");
            helper.assertBlockPresent(Blocks.DIRT, 8, 3, 8);

            assertNear(helper, CeroBeam.muzzleOf(shooter, 1.0F),
                    shooter.getEyePosition(1.0F).add(shooter.getViewVector(1.0F).scale(0.6)), "a person-sized muzzle");
            sizeByHand(shooter, 6.0);
            assertNear(helper, CeroBeam.muzzleOf(shooter, 1.0F),
                    shooter.getEyePosition(1.0F).add(shooter.getViewVector(1.0F).scale(3.6)), "a Gillian's muzzle");
        } finally {
            griefing.set(savedGriefing, level.getServer());
            shooter.getAbilities().mayBuild = savedBuild;
            removeTeam(scoreboard);
            discard(wolf, plus, instructor, spider);
        }
        helper.succeed();
    }

    /** A dirt wall across the Cero's line, at x 8. */
    private static void buildWall(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(8, y, z, Blocks.DIRT);
            }
        }
    }
}
