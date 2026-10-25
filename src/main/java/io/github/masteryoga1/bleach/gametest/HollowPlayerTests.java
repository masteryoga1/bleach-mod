package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.NpcAction;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.network.HollowKeyPayload;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.DevourEvents;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.HollowKey;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for playing as a Hollow: the saved record, the body (claws, hide, leap, sight and the armour it
 * sheds), soul hunger and what it pays for, devouring and what a meal is worth, death, purification,
 * gnawing at the chain, and the Garganta and rivals a Hollow player draws. Mock players are plain
 * {@link Player}s that are not in the world and never tick, so every test calls the rules in
 * {@link HollowPlayerLogic}, {@link HollowBody}, {@link DevourLogic} and {@link DevourEvents} directly,
 * on its first tick. Creatures stand in the {@code field} template, on its floor at relative y 2.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HollowPlayerTests {
    private static final float EPSILON = 1.0E-3F;
    private static final double CHANCE_EPSILON = 1.0E-6;
    /** Where a devourer stands in the field, two blocks north of the prey spawned at block (8, 2, 8). */
    private static final Vec3 DEVOURER_SPOT = new Vec3(8.5, 2.0, 6.5);
    /** Where a player who is the prey stands: in the middle of block (8, 2, 8). */
    private static final Vec3 PREY_SPOT = new Vec3(8.5, 2.0, 8.5);
    private static final List<EquipmentSlot> ARMOUR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private HollowPlayerTests() {
    }

    // ---- Helpers ----

    /** A mock player in this game mode with a fresh soul of this state, standing at a spot in the structure. */
    static Player soulPlayer(GameTestHelper helper, GameType mode, SoulState state, float maxReiryoku, Vec3 at) {
        Player player = helper.makeMockPlayer(mode);
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(state, maxReiryoku));
        Vec3 spot = helper.absoluteVec(at);
        player.moveTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);   // facing south, towards the prey
        return player;
    }

    /** A mock Hollow in survival at the devourer's spot, its hunger not yet started. */
    private static Player hollow(GameTestHelper helper, float maxReiryoku) {
        return soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, maxReiryoku, DEVOURER_SPOT);
    }

    /** A Hollow's soul with this much reiryoku and this much soul hunger. */
    private static SoulData hollowSoul(float maxReiryoku, float satiety) {
        SoulData data = ShinigamiTests.soul(SoulState.HOLLOW, maxReiryoku);
        HollowBody.setSatiety(data, satiety);
        return data;
    }

    /** A soul whose first Hollow has come and gone, so Garganta may open for it. */
    private static SoulData hunted(SoulState state, float maxReiryoku) {
        SoulData data = ShinigamiTests.soul(state, maxReiryoku);
        data.setHunt(data.hunt().withFirstHollowDone(true));
        return data;
    }

    /** A creature at this block of the floor that does nothing at all, not even fall or turn. */
    static <T extends Mob> T placed(GameTestHelper helper, EntityType<T> type, int x, int z) {
        return QuincyTests.still(helper.spawnWithNoFreeWill(type, x, 2, z));
    }

    @Nullable
    static AttributeModifier modifier(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? null : instance.getModifier(id);
    }

    /** Whether none of the Hollow body's modifiers is on the player: claws, claw speed, hide, leap and the jaws' slowness. */
    static boolean bodyOff(Player player) {
        return modifier(player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) == null
                && modifier(player, Attributes.ATTACK_SPEED, HollowBody.CLAW_SPEED_ID) == null
                && modifier(player, Attributes.ARMOR, HollowBody.HIDE_ID) == null
                && modifier(player, Attributes.SAFE_FALL_DISTANCE, HollowBody.LEAP_ID) == null
                && modifier(player, Attributes.MOVEMENT_SPEED, DevourLogic.DEVOURING_ID) == null;
    }

    /** Asserts a modifier is on the player with this amount and operation. */
    static void assertModifier(GameTestHelper helper, Player player, Holder<Attribute> attribute, ResourceLocation id,
                               double amount, AttributeModifier.Operation operation, String what) {
        AttributeModifier found = modifier(player, attribute, id);
        helper.assertTrue(found != null && found.operation() == operation, what + " is " + found);
        assertClose(helper, found.amount(), amount, what);
    }

    static void assertClose(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON, what + " is " + actual + ", not " + expected);
    }

    private static void assertRefusal(GameTestHelper helper, Player devourer, @Nullable Entity target, long now,
                                      @Nullable DevourLogic.Refusal expected, String what) {
        DevourLogic.Refusal refusal = DevourLogic.refusal(devourer, SoulLogic.get(devourer), target, now);
        helper.assertTrue(refusal == expected, "devouring " + what + " gave " + refusal + ", not " + expected);
    }

    static long now(GameTestHelper helper) {
        return helper.getLevel().getGameTime();
    }

    /** Whether the test level is on Peaceful, where soul hunger refills instead of draining. */
    static boolean peaceful(GameTestHelper helper) {
        return helper.getLevel().getDifficulty() == Difficulty.PEACEFUL;
    }

    /**
     * What one body tick does to a fed Hollow's hunger: a second's refill on Peaceful on a tick that ends
     * a second, otherwise a tick's drain. Read from the level, as the test server's difficulty may change.
     */
    private static float oneTick(GameTestHelper helper, Player player) {
        if (peaceful(helper)) {
            return player.tickCount % 20 == 0 ? (float) BleachConfig.get(BleachConfig.PEACEFUL_SATIETY_PER_SECOND) : 0.0F;
        }
        return -HollowPlayerLogic.drainPerTick();
    }

    /** Takes away any experience a kill dropped in the box, so nothing is left lying there. */
    private static void clearOrbs(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).forEach(Entity::discard);
    }

    // ---- The record ----

    @GameTest(template = "empty")
    public static void hollowProgressSavesAndForgives(GameTestHelper helper) {
        HollowProgress progress = new HollowProgress(42.5F, 17, 3, Map.of(HollowKind.SPIDER, 2, HollowKind.HOUND, 1), 1,
                false, 1234L, true);
        Tag saved = HollowProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow();
        HollowProgress loaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(progress), "the Hollow record came back as " + loaded);
        SoulData soul = ShinigamiTests.soul(SoulState.HOLLOW, 90.0F);
        soul.setHollow(progress);
        Tag savedSoul = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, soul).getOrThrow();
        SoulData loadedSoul = SoulData.CODEC.parse(NbtOps.INSTANCE, savedSoul).getOrThrow();
        helper.assertTrue(loadedSoul.hollow().equals(progress),
                "the soul's Hollow record came back as " + loadedSoul.hollow());

        // A bad value falls back on its own, a kind this version does not know is dropped, and so is a count below one.
        CompoundTag kinds = new CompoundTag();
        kinds.putInt("spider", 2);
        kinds.putInt("dragon", 4);
        kinds.putInt("hound", -3);
        CompoundTag broken = new CompoundTag();
        broken.putString("satiety", "x");
        broken.putInt("souls", 5);
        broken.put("hollows", kinds);
        HollowProgress forgiven = HollowProgress.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
        helper.assertTrue(forgiven.satiety() == HollowProgress.UNSET, "a bad satiety read as " + forgiven.satiety());
        helper.assertTrue(forgiven.souls() == 5, "the souls read as " + forgiven.souls());
        helper.assertTrue(forgiven.hollows().equals(Map.of(HollowKind.SPIDER, 2)), "the kinds read as " + forgiven.hollows());

        HollowProgress clamped = new HollowProgress(150.0F, -4, -1, null, -2, true, HollowProgress.NEVER, false);
        helper.assertTrue(clamped.satiety() == HollowProgress.MAX_SATIETY, "satiety 150 was kept as " + clamped.satiety());
        helper.assertTrue(clamped.souls() == 0 && clamped.pluses() == 0 && clamped.players() == 0
                && clamped.totalHollows() == 0, "negative counts were kept: " + clamped);
        HollowProgress notANumber = new HollowProgress(Float.NaN, 0, 0, Map.of(), 0, true, HollowProgress.NEVER, false);
        helper.assertTrue(notANumber.satiety() == HollowProgress.UNSET,
                "a satiety that is no number is " + notANumber.satiety());
        helper.assertFalse(notANumber.hungerStarted(), "a satiety that is no number started the hunger");

        helper.assertTrue(progress.totalHollows() == 3, "the Hollows devoured add up to " + progress.totalHollows());
        helper.assertTrue(progress.meals() == 7, "the meals add up to " + progress.meals());
        helper.assertTrue(progress.hollows(HollowKind.SERPENT) == 0, "a Serpent was devoured");
        HollowProgress restarted = progress.restarted();
        helper.assertTrue(restarted.satiety() == HollowProgress.UNSET && restarted.souls() == 0
                && !restarted.purifiedOnDeath(), "a restarted record kept its hunger, souls or mark: " + restarted);
        helper.assertTrue(restarted.pluses() == 3 && restarted.hollows().equals(progress.hollows())
                && restarted.players() == 1 && !restarted.sight() && restarted.lastMealAt() == 1234L,
                "a restarted record forgot what it ate: " + restarted);
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void oldHollowSavesStartTheHunger(GameTestHelper helper) {
        // Something on the record, so the save has a "hollow" to take away, as one from before Hollow players had none.
        SoulData fresh = ShinigamiTests.soul(SoulState.HOLLOW, 90.0F);
        fresh.setHollow(fresh.hollow().withSouls(5));
        CompoundTag saved = (CompoundTag) SoulData.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
        helper.assertTrue(saved.contains("hollow"), "the soul was saved without its Hollow record");
        saved.remove("hollow");
        SoulData data = SoulData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(data.hollow().equals(HollowProgress.NONE), "an old save read as " + data.hollow());

        Player player = hollow(helper, 90.0F);
        player.setData(SoulAttachments.SOUL, data);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        // A tick on which the hide looks for armour to shed.
        player.tickCount = HollowBody.SHED_INTERVAL_TICKS;
        HollowBody.tickBody(player, data, now(helper));
        helper.assertTrue(data.hollow().hungerStarted(), "the first tick did not start the hunger");
        float start = (float) BleachConfig.get(BleachConfig.SATIETY_START);
        assertClose(helper, HollowBody.satiety(data), start + oneTick(helper, player), "the hunger after the first tick");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the hide did not shed the helmet");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_HELMET)), "the shed helmet was lost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hollowNetworkRoundTrips(GameTestHelper helper) {
        List<HollowKeyPayload> payloads =
                List.of(new HollowKeyPayload(HollowKey.SIGHT, 77), new HollowKeyPayload(HollowKey.PRESS, -1));
        for (HollowKeyPayload sent : payloads) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                HollowKeyPayload.STREAM_CODEC.encode(buffer, sent);
                HollowKeyPayload received = HollowKeyPayload.STREAM_CODEC.decode(buffer);
                helper.assertTrue(received.equals(sent), "the Devour key payload came back as " + received);
                helper.assertTrue(buffer.readableBytes() == 0,
                        "the Devour key payload left " + buffer.readableBytes() + " bytes");
            } finally {
                buffer.release();
            }
        }
        // Sent by ordinal, so new keys only ever come last.
        helper.assertTrue(HollowKey.PRESS.ordinal() == 0 && HollowKey.RELEASE.ordinal() == 1
                        && HollowKey.SIGHT.ordinal() == 2,
                "the Devour key's ordinals moved");

        SoulAppearance appearance = new SoulAppearance(SoulState.HOLLOW, 0.0F, false, -1, BlutMode.OFF, 0b1010, 5);
        ByteBuf buffer = Unpooled.buffer();
        try {
            SoulAppearance.STREAM_CODEC.encode(buffer, appearance);
            SoulAppearance read = SoulAppearance.STREAM_CODEC.decode(buffer);
            helper.assertTrue(read.equals(appearance), "the Hollow's appearance came through the network as " + read);
        } finally {
            buffer.release();
        }
        helper.assertTrue(new SoulAppearance(SoulState.HOLLOW, 0.0F, false, -1, BlutMode.OFF, 0).hollowLook() == 0,
                "the six-part appearance has a Hollow look");
        helper.assertTrue(appearance.withHollowLook(1).hollowLook() == 1, "withHollowLook did not set the look");
        helper.assertTrue(DialogueView.HOLLOW_PURIFY.ordinal() == 24,
                "HOLLOW_PURIFY is number " + DialogueView.HOLLOW_PURIFY.ordinal());
        helper.assertTrue(NpcAction.PURIFY.ordinal() == 9, "PURIFY is number " + NpcAction.PURIFY.ordinal());
        List<NpcAction> offered = NpcAction.offeredIn(DialogueView.HOLLOW_PURIFY);
        helper.assertTrue(offered.equals(List.of(NpcAction.PURIFY)), "the instructor's blade offers " + offered);
        helper.succeed();
    }

    // ---- The numbers ----

    @GameTest(template = "empty")
    public static void bandsLooksAndStarvingFloors(GameTestHelper helper) {
        Map<Float, HollowPlayerLogic.Band> bands = Map.of(
                HollowProgress.UNSET, HollowPlayerLogic.Band.FED,
                100.0F, HollowPlayerLogic.Band.FED,
                30.0F, HollowPlayerLogic.Band.FED,
                29.9F, HollowPlayerLogic.Band.RAVENOUS,
                0.01F, HollowPlayerLogic.Band.RAVENOUS,
                0.0F, HollowPlayerLogic.Band.STARVING);
        bands.forEach((satiety, band) -> helper.assertTrue(HollowPlayerLogic.band(satiety) == band,
                "satiety " + satiety + " is " + HollowPlayerLogic.band(satiety) + ", not " + band));

        helper.assertFalse(HollowPlayerLogic.starves(Difficulty.PEACEFUL, 20.0F), "starving hurts on Peaceful");
        helper.assertTrue(HollowPlayerLogic.starves(Difficulty.EASY, 10.5F), "starving spares 10.5 health on Easy");
        helper.assertFalse(HollowPlayerLogic.starves(Difficulty.EASY, 10.0F), "starving goes below half the hearts on Easy");
        helper.assertTrue(HollowPlayerLogic.starves(Difficulty.NORMAL, 1.5F), "starving spares 1.5 health on Normal");
        helper.assertFalse(HollowPlayerLogic.starves(Difficulty.NORMAL, 1.0F), "starving goes below half a heart on Normal");
        helper.assertTrue(HollowPlayerLogic.starves(Difficulty.HARD, 0.5F), "starving spares the last half heart on Hard");
        // A harder configured hurt still stops at the floor, or one point past it as vanilla's does.
        assertClose(helper, HollowPlayerLogic.starvingHurt(Difficulty.NORMAL, 20.0F, 1.0F), 1.0,
                "a default hurt at 20 health");
        assertClose(helper, HollowPlayerLogic.starvingHurt(Difficulty.NORMAL, 20.0F, 2.0F), 2.0, "a hurt of 2 at 20 health");
        assertClose(helper, HollowPlayerLogic.starvingHurt(Difficulty.NORMAL, 2.5F, 2.0F), 1.5,
                "a hurt of 2 at 2.5 on Normal");
        assertClose(helper, HollowPlayerLogic.starvingHurt(Difficulty.NORMAL, 1.5F, 2.0F), 1.0,
                "a hurt of 2 at 1.5 on Normal");
        assertClose(helper, HollowPlayerLogic.starvingHurt(Difficulty.NORMAL, 1.5F, 0.5F), 0.5,
                "a hurt of 0.5 at 1.5 on Normal");
        assertClose(helper, HollowPlayerLogic.starvingHurt(Difficulty.EASY, 10.5F, 11.0F), 1.0,
                "a hurt of 11 at 10.5 on Easy");
        assertClose(helper, HollowPlayerLogic.starvingHurt(Difficulty.HARD, 1.0F, 5.0F), 5.0, "a hurt of 5 at 1 on Hard");

        SoulData data = hollowSoul(90.0F, 60.0F);
        helper.assertTrue(HollowPlayerLogic.hollowLook(data) == 0,
                "a fed Hollow looks like " + HollowPlayerLogic.hollowLook(data));
        assertClose(helper, HollowPlayerLogic.reiryokuRegenMultiplier(data), 1.0, "a fed Hollow's reiryoku refill");
        HollowBody.setSatiety(data, 20.0F);
        helper.assertTrue(HollowPlayerLogic.hollowLook(data) == HollowPlayerLogic.LOOK_RAVENOUS,
                "a ravenous Hollow looks like " + HollowPlayerLogic.hollowLook(data));
        assertClose(helper, HollowPlayerLogic.reiryokuRegenMultiplier(data), 0.5, "a ravenous Hollow's reiryoku refill");
        // Only a Hollow's hunger shows, whatever the record says.
        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 90.0F);
        human.setHollow(human.hollow().withSatiety(20.0F));
        helper.assertTrue(HollowPlayerLogic.hollowLook(human) == 0,
                "a Human looks like " + HollowPlayerLogic.hollowLook(human));

        long now = now(helper);
        helper.assertTrue(HollowBody.onMaskStruck(data, now), "a blow to a whole mask was not a fresh crack");
        helper.assertFalse(HollowBody.onMaskStruck(data, now), "a blow to a cracked mask was a fresh crack");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0,
                "the struck mask does not look cracked");
        int later = HollowPlayerLogic.hollowLook(data, now + Hollow.CRACK_TICKS);
        helper.assertTrue((later & HollowPlayerLogic.LOOK_MASK_CRACKED) == 0,
                "the mask still looks cracked " + Hollow.CRACK_TICKS + " ticks on");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bodyNumbersScaleWithReiryoku(GameTestHelper helper) {
        assertClose(helper, HollowPlayerLogic.clawBonus(hollowSoul(90.0F, 60.0F)), 5.3717, "the claws at 90");
        assertClose(helper, HollowPlayerLogic.clawBonus(hollowSoul(400.0F, 60.0F)), 8.0, "the claws at 400");
        assertClose(helper, HollowPlayerLogic.clawBonus(hollowSoul(1296.0F, 60.0F)), 12.0, "the claws at 1296");
        assertClose(helper, HollowPlayerLogic.clawBonus(hollowSoul(10000.0F, 60.0F)), 12.0, "the claws at 10000");
        assertClose(helper, HollowPlayerLogic.clawBonus(hollowSoul(400.0F, 20.0F)), 9.6, "the ravenous claws at 400");
        assertClose(helper, HollowPlayerLogic.hideArmour(hollowSoul(90.0F, 60.0F)), 7.7947, "the hide at 90");
        assertClose(helper, HollowPlayerLogic.hideArmour(hollowSoul(400.0F, 60.0F)), 12.0, "the hide at 400");
        assertClose(helper, HollowPlayerLogic.hideArmour(hollowSoul(900.0F, 60.0F)), 16.0, "the hide at 900");
        assertClose(helper, HollowPlayerLogic.hideArmour(hollowSoul(2500.0F, 60.0F)), 16.0, "the hide at 2500");
        helper.assertTrue(HollowPlayerLogic.clawSpeedModifier() == -0.5,
                "the claw speed is " + HollowPlayerLogic.clawSpeedModifier());
        assertClose(helper, HollowPlayerLogic.regenPerSecond(hollowSoul(90.0F, 60.0F)), 1.225, "the regeneration at 90");
        assertClose(helper, HollowPlayerLogic.regenPerSecond(hollowSoul(400.0F, 60.0F)), 2.0, "the regeneration at 400");
        assertClose(helper, HollowPlayerLogic.regenPerSecond(hollowSoul(1200.0F, 60.0F)), 4.0, "the regeneration at 1200");
        assertClose(helper, HollowPlayerLogic.regenPerSecond(hollowSoul(2000.0F, 60.0F)), 4.0, "the regeneration at 2000");
        assertClose(helper, HollowPlayerLogic.regenPerSecond(hollowSoul(400.0F, 20.0F)), 1.0,
                "the ravenous regeneration at 400");
        assertClose(helper, HollowPlayerLogic.regenPerSecond(hollowSoul(400.0F, 0.0F)), 0.0,
                "the starving regeneration at 400");
        helper.succeed();
    }

    // ---- The body ----

    @GameTest(template = "field")
    public static void bodyFollowsTheHand(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        assertModifier(helper, player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID, 5.3717,
                AttributeModifier.Operation.ADD_VALUE, "the claws");
        assertModifier(helper, player, Attributes.ATTACK_SPEED, HollowBody.CLAW_SPEED_ID, -0.5,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the claw speed");
        // Vanilla's four blows a second, halved.
        assertClose(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), 2.0, "the bare-handed attack speed");
        assertModifier(helper, player, Attributes.ARMOR, HollowBody.HIDE_ID, 7.7947,
                AttributeModifier.Operation.ADD_VALUE, "the hide");
        assertModifier(helper, player, Attributes.SAFE_FALL_DISTANCE, HollowBody.LEAP_ID, 3.0,
                AttributeModifier.Operation.ADD_VALUE, "the leap");

        // A blow struck in the same tick as the swap to a sword already has no claws.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        HollowBody.reconcile(player, data);
        helper.assertTrue(modifier(player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) == null,
                "the claws stayed on a sword");
        helper.assertTrue(modifier(player, Attributes.ATTACK_SPEED, HollowBody.CLAW_SPEED_ID) == null,
                "the claw speed stayed on a sword");
        helper.assertTrue(modifier(player, Attributes.ARMOR, HollowBody.HIDE_ID) != null,
                "the hide came off with a sword in hand");

        // A hand emptied between ticks waits for the player's tick to grow the claws, as the sword it held
        // still lends its damage until then: a blow or a stat change in between leaves them off.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        HollowBody.reconcile(player, data);
        SoulLogic.applyStatModifiers(player, data);
        helper.assertTrue(modifier(player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) == null,
                "the claws grew on a hand emptied between ticks");
        HollowBody.setSatiety(data, 20.0F);
        HollowBody.tickBody(player, data, now(helper));
        assertModifier(helper, player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID, 6.446,
                AttributeModifier.Operation.ADD_VALUE, "the ravenous claws");
        // Claws already on a bare hand stay on between ticks.
        HollowBody.reconcile(player, data);
        assertModifier(helper, player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID, 6.446,
                AttributeModifier.Operation.ADD_VALUE, "the claws before a bare-handed blow");

        Player human = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 90.0F, DEVOURER_SPOT);
        HollowBody.reconcile(human, SoulLogic.get(human));
        helper.assertTrue(bodyOff(human), "a Human has a Hollow's body");
        Player creative = soulPlayer(helper, GameType.CREATIVE, SoulState.HOLLOW, 90.0F, DEVOURER_SPOT);
        HollowBody.tickBody(creative, SoulLogic.get(creative), now(helper));
        helper.assertTrue(modifier(creative, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) != null
                && modifier(creative, Attributes.ARMOR, HollowBody.HIDE_ID) != null,
                "a creative Hollow has no claws or hide");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void hollowSightTogglesAndSparesPotions(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(sight != null && HollowBody.isOwnNightVision(sight), "the Hollow sight is " + sight);
        helper.assertTrue(sight.getDuration() == HollowBody.NIGHT_VISION_TICKS,
                "the Hollow sight lasts " + sight.getDuration());

        // Nearly run out: the sight is topped up long before vanilla would make it flicker.
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 250, 0, true, false, false));
        HollowBody.reconcile(player, data);
        MobEffectInstance topped = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(topped != null && topped.getDuration() == HollowBody.NIGHT_VISION_TICKS,
                "the fading sight was topped up to " + topped);

        data.setHollow(data.hollow().withSight(false));
        HollowBody.reconcile(player, data);
        helper.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "the Hollow sight stayed on when turned off");

        // A potion's night vision is never the sight's to take away, or to top up.
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600));
        HollowBody.reconcile(player, data);
        MobEffectInstance potion = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(potion != null && potion.getDuration() == 3600, "turning the sight off took the potion: " + potion);
        data.setHollow(data.hollow().withSight(true));
        HollowBody.reconcile(player, data);
        potion = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(potion != null && potion.getDuration() == 3600 && !HollowBody.isOwnNightVision(potion),
                "the sight replaced the potion: " + potion);
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void leavingTheStateRestoresTheBody(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        data.setHollow(data.hollow().withPluses(3));
        player.getFoodData().setFoodLevel(6);
        // The jaws' slowness too, as if a hold were on when the Hollow was purified.
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed != null, "a player has no movement speed");
        speed.addOrUpdateTransientModifier(new AttributeModifier(DevourLogic.DEVOURING_ID, DevourLogic.DEVOURING_SLOWDOWN,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

        HollowBody.purify(data);
        helper.assertTrue(data.state() == SoulState.RUKONGAI, "a purified Hollow is " + data.state());
        helper.assertTrue(data.tier(PowerPath.HOLLOW) == 0,
                "a purified Hollow kept Hollow tier " + data.tier(PowerPath.HOLLOW));
        helper.assertTrue(data.hollow().satiety() == HollowProgress.UNSET, "a purified Hollow kept its hunger");
        helper.assertTrue(data.hollow().souls() == 0, "a purified Hollow kept " + data.hollow().souls() + " souls");
        helper.assertTrue(data.hollow().pluses() == 3, "a purified Hollow forgot its meals: " + data.hollow().pluses());

        HollowBody.tickBody(player, data, now(helper));
        helper.assertTrue(bodyOff(player), "the Hollow body stayed on a soul");
        helper.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "the Hollow sight stayed on a soul");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 20,
                "the food came back as " + player.getFoodData().getFoodLevel());
        assertClose(helper, player.getFoodData().getSaturationLevel(), 5.0, "the saturation that came back");
        helper.succeed();
    }

    // ---- Soul hunger ----

    @GameTest(template = "field")
    public static void feedsOnSoulsPinsTheFood(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        // Back to the start exactly, so the first tick's drain does not show in the numbers below.
        HollowBody.setSatiety(data, 60.0F);
        FoodData food = player.getFoodData();
        food.setFoodLevel(3);
        food.addExhaustion(4.0F);
        // Through vanilla's own food tick, which the mixin hands to the soul hunger.
        food.tick(player);
        helper.assertTrue(food.getFoodLevel() == 20, "a Hollow's food bar was left at " + food.getFoodLevel());
        assertClose(helper, food.getSaturationLevel(), 0.0, "a Hollow's saturation");
        assertClose(helper, food.getExhaustionLevel(), 0.0, "a Hollow's exhaustion");
        // 4 exhaustion is 2.4 soul hunger; Peaceful spends none.
        assertClose(helper, HollowBody.satiety(data), peaceful(helper) ? 60.0 : 57.6, "the hunger after the effort");

        HollowBody.setSatiety(data, 0.0F);
        food.tick(player);
        helper.assertTrue(food.getFoodLevel() == HollowPlayerLogic.STARVING_FOOD_LEVEL,
                "a starving Hollow's food bar is at " + food.getFoodLevel());

        Player human = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 90.0F, DEVOURER_SPOT);
        FoodData humanFood = human.getFoodData();
        humanFood.setFoodLevel(3);
        helper.assertFalse(HollowBody.feedsOnSouls(human, humanFood), "a Human fed on souls");
        helper.assertTrue(humanFood.getFoodLevel() == 3, "a Human's food changed to " + humanFood.getFoodLevel());

        Player creative = soulPlayer(helper, GameType.CREATIVE, SoulState.HOLLOW, 90.0F, DEVOURER_SPOT);
        SoulData creativeData = SoulLogic.get(creative);
        HollowBody.ensureHunger(creativeData);
        float before = HollowBody.satiety(creativeData);
        FoodData creativeFood = creative.getFoodData();
        creativeFood.addExhaustion(4.0F);
        helper.assertTrue(HollowBody.feedsOnSouls(creative, creativeFood), "a creative Hollow fed on food");
        assertClose(helper, HollowBody.satiety(creativeData), before, "a creative Hollow's hunger");
        helper.assertTrue(creativeFood.getFoodLevel() == 20,
                "a creative Hollow's food bar is at " + creativeFood.getFoodLevel());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hungerDrainsAndFlushes(GameTestHelper helper) {
        SoulData data = ShinigamiTests.soul(SoulState.HOLLOW, 90.0F);
        HollowBody.ensureHunger(data);
        assertClose(helper, data.hollow().satiety(), 60.0, "the hunger a Hollow starts with");
        helper.assertTrue(Math.abs(HollowPlayerLogic.drainPerTick() - 2.0F / 1200.0F) < 1.0E-7F,
                "the drain a tick is " + HollowPlayerLogic.drainPerTick());
        assertClose(helper, HollowPlayerLogic.satietyFromExhaustion(100.0F), 60.0, "the hunger of 100 exhaustion");

        // The debt shows at once, but the saved value only changes on a flush.
        HollowBody.addDebt(data, 0.4F);
        assertClose(helper, HollowBody.satiety(data), 59.6, "the hunger with 0.4 owed");
        assertClose(helper, data.hollow().satiety(), 60.0, "the saved hunger with 0.4 owed");
        HollowBody.addDebt(data, 0.7F);
        assertClose(helper, HollowBody.satiety(data), 58.9, "the hunger with 1.1 owed");
        HollowBody.flushDebt(data);
        assertClose(helper, data.hollow().satiety(), 58.9, "the saved hunger after a flush");
        HollowBody.addSatiety(data, 50.0F);
        assertClose(helper, HollowBody.satiety(data), 100.0, "the hunger after a large meal");
        HollowBody.setSatiety(data, -5.0F);
        assertClose(helper, HollowBody.satiety(data), 0.0, "the hunger set below 0");
        HollowBody.setSatiety(data, 150.0F);
        assertClose(helper, HollowBody.satiety(data), 100.0, "the hunger set above full");

        assertClose(helper, HollowPlayerLogic.respawnSatiety(HollowProgress.NONE), 60.0, "the respawn hunger with none");
        assertClose(helper, HollowPlayerLogic.respawnSatiety(HollowProgress.NONE.withSatiety(12.0F)), 30.0,
                "the respawn hunger from 12");
        assertClose(helper, HollowPlayerLogic.respawnSatiety(HollowProgress.NONE.withSatiety(75.0F)), 75.0,
                "the respawn hunger from 75");
        helper.assertTrue(HollowPlayerLogic.soulsLostOnDeath(40) == 10,
                "a death costs " + HollowPlayerLogic.soulsLostOnDeath(40));
        helper.assertTrue(HollowPlayerLogic.soulsLostOnDeath(3) == 0, "a death costs " + HollowPlayerLogic.soulsLostOnDeath(3)
                + " of 3 souls");
        helper.assertTrue(HollowPlayerLogic.stirring(20, 26) == 25,
                "20 to 26 souls stirs " + HollowPlayerLogic.stirring(20, 26));
        helper.assertTrue(HollowPlayerLogic.stirring(20, 120) == 100,
                "20 to 120 souls stirs " + HollowPlayerLogic.stirring(20, 120));
        helper.assertTrue(HollowPlayerLogic.stirring(26, 40) == 0,
                "26 to 40 souls stirs " + HollowPlayerLogic.stirring(26, 40));
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void regenerationCostsSatietyAndWaits(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        HollowBody.setSatiety(data, 60.0F);
        player.setHealth(10.0F);
        long now = now(helper);

        float gained = HollowBody.regenerate(player, data, now, 1.0F);
        assertClose(helper, gained, 1.225, "a second's regeneration at 90");
        // 1.5 hunger a health point.
        assertClose(helper, HollowBody.satiety(data), 60.0 - 1.8375, "the hunger after a second's regeneration");

        HollowBody.onHurt(data, now);
        helper.assertTrue(HollowBody.regenerate(player, data, now + 50, 1.0F) == 0.0F,
                "a Hollow healed 50 ticks after a hurt");
        helper.assertTrue(HollowBody.regenerate(player, data, now + 100, 1.0F) > 0.0F,
                "a Hollow did not heal 100 ticks after a hurt");
        HollowBody.onMaskStruck(data, now + 100);
        helper.assertTrue(HollowBody.regenerate(player, data, now + 150, 1.0F) == 0.0F,
                "a Hollow healed with its mask cracked");
        helper.assertTrue(HollowBody.regenerate(player, data, now + 300, 1.0F) > 0.0F,
                "a Hollow did not heal once the mask's pause was over");

        player.setHealth(player.getMaxHealth());
        helper.assertTrue(HollowBody.regenerate(player, data, now + 300, 1.0F) == 0.0F, "a Hollow healed at full health");
        player.setHealth(10.0F);
        HollowBody.setSatiety(data, 0.0F);
        helper.assertTrue(HollowBody.regenerate(player, data, now + 300, 1.0F) == 0.0F, "a starving Hollow healed");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void maskStrikesOnlyFromInFront(GameTestHelper helper) {
        // The body is never put on, so no hide stands between the blows and the ratio is the multiplier.
        Player victim = hollow(helper, 90.0F);
        victim.setYHeadRot(0.0F);
        SoulData data = SoulLogic.get(victim);
        Vec3 at = victim.position();
        Vec3 mask = HollowMask.playerMaskBox(victim).getCenter();
        // Two Shinigami, who see the Hollow, of the same reiryoku: one behind it, one in front aiming at the mask.
        Player behind = soulPlayer(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 90.0F, DEVOURER_SPOT);
        behind.moveTo(at.add(0.0, 0.0, -2.0));
        behind.lookAt(EntityAnchorArgument.Anchor.EYES, victim.getEyePosition());
        Player front = soulPlayer(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 90.0F, DEVOURER_SPOT);
        front.moveTo(at.add(0.0, 0.0, 2.0));
        front.lookAt(EntityAnchorArgument.Anchor.EYES, mask);
        DamageSource fromBehind = helper.getLevel().damageSources().playerAttack(behind);
        DamageSource fromFront = helper.getLevel().damageSources().playerAttack(front);
        helper.assertFalse(HollowMask.struckPlayer(victim, fromBehind), "a blow from behind struck the mask");
        helper.assertTrue(HollowMask.struckPlayer(victim, fromFront), "a blow from in front missed the mask");
        // Only a player's own blow is traced along their aim: the same aim under another damage type misses.
        helper.assertFalse(HollowMask.struckPlayer(victim, helper.getLevel().damageSources().mobAttack(front)),
                "a mob's blow struck the mask");

        float full = victim.getHealth();
        victim.hurt(fromBehind, 4.0F);
        float bodyDrop = full - victim.getHealth();
        long now = now(helper);
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_MASK_CRACKED) == 0,
                "a blow from behind cracked the mask");
        victim.invulnerableTime = 0;
        float beforeMask = victim.getHealth();
        victim.hurt(fromFront, 4.0F);
        float maskDrop = beforeMask - victim.getHealth();

        helper.assertTrue(bodyDrop > 0.0F, "the blow from behind did no harm");
        double multiplier = BleachConfig.get(BleachConfig.HOLLOW_PLAYER_MASK_DAMAGE_MULTIPLIER);
        double ratio = maskDrop / bodyDrop;
        helper.assertTrue(Math.abs(ratio - multiplier) < 0.05,
                "a blow to the mask did " + maskDrop + " against " + bodyDrop + " from behind, " + ratio + " times as much");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0,
                "a blow to the mask did not crack it");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void starvingHurtsAsSoulHunger(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        HollowBody.setSatiety(data, 0.0F);
        player.setHealth(20.0F);
        boolean expected = HollowPlayerLogic.starves(helper.getLevel().getDifficulty(), 20.0F);
        boolean hurt = HollowBody.starve(player, data);
        helper.assertTrue(hurt == expected,
                "starving at 20 health hurt: " + hurt + ", on " + helper.getLevel().getDifficulty());
        if (hurt) {
            assertClose(helper, player.getHealth(), 19.0, "the health after a starving hurt");
            DamageSource last = player.getLastDamageSource();
            helper.assertTrue(last != null && last.is(HollowBody.SOUL_HUNGER), "starving hurt as " + last);
        } else {
            assertClose(helper, player.getHealth(), 20.0, "the health after starving did not hurt");
        }
        DamageSource hunger = HollowBody.soulHunger(helper.getLevel());
        helper.assertTrue(hunger.is(HollowBody.SOUL_HUNGER), "soul hunger is " + hunger.typeHolder());
        helper.assertTrue(hunger.is(DamageTypeTags.BYPASSES_ARMOR), "the hide's armour softens soul hunger");
        helper.succeed();
    }

    // ---- What a Hollow cannot wear or eat ----

    @GameTest(template = "field")
    public static void armourIsRefusedAndShed(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        long now = now(helper);
        HollowBody.tickBody(player, data, now);
        Map<ItemStack, EquipmentSlot> slots = Map.of(
                new ItemStack(Items.IRON_CHESTPLATE), EquipmentSlot.MAINHAND,
                new ItemStack(Items.DIAMOND_HELMET), EquipmentSlot.MAINHAND,
                new ItemStack(Items.ELYTRA), EquipmentSlot.CHEST,
                new ItemStack(Items.CARVED_PUMPKIN), EquipmentSlot.HEAD,
                new ItemStack(Items.SKELETON_SKULL), EquipmentSlot.HEAD);
        slots.forEach((stack, slot) -> helper.assertTrue(player.getEquipmentSlotForItem(stack) == slot,
                "a Hollow puts " + stack + " in " + player.getEquipmentSlotForItem(stack) + ", not " + slot));
        Player creative = soulPlayer(helper, GameType.CREATIVE, SoulState.HOLLOW, 90.0F, DEVOURER_SPOT);
        Player human = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 90.0F, DEVOURER_SPOT);
        for (Player wearer : List.of(creative, human)) {
            EquipmentSlot slot = wearer.getEquipmentSlotForItem(new ItemStack(Items.IRON_CHESTPLATE));
            helper.assertTrue(slot == EquipmentSlot.CHEST,
                    "a chestplate goes in " + slot + " for a " + SoulLogic.get(wearer).state()
                    + (wearer.isCreative() ? " in creative" : ""));
        }

        // Armour put on anyway (by a command, or from an old save) is shed; what a Hollow may wear stays.
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        int shed = HollowBody.shedArmour(player, data, now);
        helper.assertTrue(shed == 1, "the hide shed " + shed + " pieces");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the chestplate stayed on");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_CHESTPLATE)),
                "the shed chestplate was lost");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN), "the carved pumpkin was shed");
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack worn = player.getItemBySlot(slot);
            helper.assertTrue(worn.isEmpty() || worn.is(BleachTags.HOLLOW_WEARABLE), "the hide left " + worn + " in " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void foodIsRefusedButPotionsAreNot(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        for (ItemStack food : List.of(new ItemStack(Items.BREAD), new ItemStack(Items.GOLDEN_APPLE),
                new ItemStack(Items.HONEY_BOTTLE), new ItemStack(Items.CHORUS_FRUIT))) {
            helper.assertTrue(HollowPlayerLogic.cannotConsume(food, player), "a Hollow can have " + food);
        }
        for (ItemStack other : List.of(new ItemStack(Items.POTION), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.TOTEM_OF_UNDYING))) {
            helper.assertFalse(HollowPlayerLogic.cannotConsume(other, player), "a Hollow cannot have " + other);
        }
        // Empty in the mod's own data: a pack fills it.
        helper.assertTrue(BuiltInRegistries.ITEM.getTag(BleachTags.HOLLOW_EDIBLE).map(set -> set.size() == 0).orElse(true),
                "#bleach:hollow_edible is not empty");

        // A cake straight from the block: a hungry Hollow takes no bite, where a hungry Human does.
        BlockPos cake = helper.absolutePos(new BlockPos(8, 2, 8));
        helper.getLevel().setBlockAndUpdate(cake, Blocks.CAKE.defaultBlockState());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cake), Direction.UP, cake, false);
        player.getFoodData().setFoodLevel(6);
        helper.getLevel().getBlockState(cake).useWithoutItem(helper.getLevel(), player, hit);
        helper.assertTrue(helper.getLevel().getBlockState(cake).getValue(CakeBlock.BITES) == 0, "a Hollow bit the cake");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 6,
                "a Hollow's food rose to " + player.getFoodData().getFoodLevel());
        Player human = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 90.0F, DEVOURER_SPOT);
        human.getFoodData().setFoodLevel(6);
        helper.getLevel().getBlockState(cake).useWithoutItem(helper.getLevel(), human, hit);
        helper.assertTrue(helper.getLevel().getBlockState(cake).getValue(CakeBlock.BITES) == 1,
                "a hungry Human did not bite the cake");
        helper.getLevel().setBlockAndUpdate(cake, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    // ---- Devouring ----

    @GameTest(template = "field")
    public static void devourRefusesWhatItCannotEat(GameTestHelper helper) {
        Player devourer = hollow(helper, 90.0F);
        long now = now(helper);

        PlusSpirit plus = placed(helper, ModEntities.PLUS.get(), 8, 8);
        plus.soul();
        assertRefusal(helper, devourer, plus, now, null, "a Plus in reach");
        helper.assertTrue(DevourLogic.preyOf(plus) == DevourLogic.Prey.PLUS,
                "a Plus is prey of kind " + DevourLogic.preyOf(plus));
        Player human = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 90.0F, DEVOURER_SPOT);
        assertRefusal(helper, human, plus, now, DevourLogic.Refusal.NOT_HOLLOW, "a Plus as a Human");
        plus.startDeparture(PlusSpirit.SINKING);
        assertRefusal(helper, devourer, plus, now, DevourLogic.Refusal.PROTECTED, "a Plus sinking away");
        plus.discard();

        PlusSpirit far = placed(helper, ModEntities.PLUS.get(), 8, 14);
        far.soul();
        assertRefusal(helper, devourer, far, now, DevourLogic.Refusal.OUT_OF_REACH, "a Plus out of reach");
        far.discard();

        Pig pig = placed(helper, EntityType.PIG, 8, 8);
        assertRefusal(helper, devourer, pig, now, DevourLogic.Refusal.NO_SOUL, "a pig");
        helper.assertTrue(DevourLogic.preyOf(pig) == null, "a pig is prey of kind " + DevourLogic.preyOf(pig));
        pig.discard();
        Villager villager = placed(helper, EntityType.VILLAGER, 8, 8);
        assertRefusal(helper, devourer, villager, now, DevourLogic.Refusal.NO_SOUL, "a villager");
        villager.discard();
        AcademyInstructor instructor = placed(helper, ModEntities.ACADEMY_INSTRUCTOR.get(), 8, 8);
        assertRefusal(helper, devourer, instructor, now, DevourLogic.Refusal.NO_SOUL, "an Academy instructor");
        instructor.discard();

        assertRefusal(helper, devourer, null, now, DevourLogic.Refusal.NO_TARGET, "nothing");
        assertRefusal(helper, devourer, devourer, now, DevourLogic.Refusal.NO_TARGET, "the devourer itself");
        SpiderHollow made = QuincyNpcTests.created(helper, ModEntities.SPIDER_HOLLOW.get());
        helper.assertTrue(DevourLogic.preyOf(made) == DevourLogic.Prey.HOLLOW,
                "a Hollow is prey of kind " + DevourLogic.preyOf(made));

        // A player is worn down like a Hollow with its mask whole, and only an aware one in survival counts.
        Player prey = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 40.0F, PREY_SPOT);
        helper.assertTrue(DevourLogic.preyOf(prey) == DevourLogic.Prey.PLAYER,
                "a player is prey of kind " + DevourLogic.preyOf(prey));
        assertRefusal(helper, devourer, prey, now, DevourLogic.Refusal.NOT_WEAK, "a player at full health");
        prey.setHealth(5.0F);
        assertRefusal(helper, devourer, prey, now, null, "a player at 5 health");
        Player creative = soulPlayer(helper, GameType.CREATIVE, SoulState.HUMAN, 40.0F, PREY_SPOT);
        creative.setHealth(5.0F);
        assertRefusal(helper, devourer, creative, now, DevourLogic.Refusal.PROTECTED, "a creative player");
        Player unaware = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 10.0F, PREY_SPOT);
        unaware.setHealth(5.0F);
        assertRefusal(helper, devourer, unaware, now, DevourLogic.Refusal.PROTECTED, "an unaware player");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void devourWaitsForWeaknessAndReiatsu(GameTestHelper helper) {
        Player devourer = hollow(helper, 90.0F);
        long now = now(helper);
        SpiderHollow spider = placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        spider.soul();
        float max = spider.getMaxHealth();
        assertRefusal(helper, devourer, spider, now, DevourLogic.Refusal.NOT_WEAK, "a Spider Hollow at full health");
        // Not exactly 30%, where float health against the double threshold can land either side.
        spider.setHealth(max * 0.29F);
        assertRefusal(helper, devourer, spider, now, null, "a Spider Hollow at 29%");
        spider.setHealth(max * 0.45F);
        assertRefusal(helper, devourer, spider, now, DevourLogic.Refusal.NOT_WEAK, "a Spider Hollow at 45%");
        spider.onMaskStruck(now);
        assertRefusal(helper, devourer, spider, now, null, "a Spider Hollow at 45% with its mask cracked");
        spider.setHealth(max * 0.55F);
        assertRefusal(helper, devourer, spider, now, DevourLogic.Refusal.NOT_WEAK,
                "a Spider Hollow at 55% with its mask cracked");
        PlusSpirit plus = placed(helper, ModEntities.PLUS.get(), 10, 8);
        plus.soul();
        assertRefusal(helper, devourer, plus, now, null, "a Plus at full health");

        Player weak = hollow(helper, 12.0F);
        spider.setHealth(1.0F);
        float reiatsu = SoulLogic.reiatsu(spider);
        helper.assertTrue(reiatsu >= 45.0F, "the Spider Hollow's reiatsu is only " + reiatsu);
        double most = SoulLogic.reiatsu(weak) * BleachConfig.get(BleachConfig.DEVOUR_REIATSU_RATIO);
        assertRefusal(helper, weak, spider, now, reiatsu > most ? DevourLogic.Refusal.TOO_STRONG : null,
                "a Spider Hollow of " + reiatsu + " as a Hollow of 12");
        spider.discard();
        plus.discard();
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void devourHoldSeizesAndCompletes(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        // Back to the start exactly, so the first tick's drain does not show in the meal's numbers.
        HollowBody.setSatiety(data, 60.0F);
        data.setReiryoku(50.0F);
        player.setHealth(10.0F);
        PlusSpirit plus = placed(helper, ModEntities.PLUS.get(), 8, 8);
        plus.soul();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, plus.getBoundingBox().getCenter());
        long now = now(helper);

        DevourLogic.Refusal start = DevourEvents.startDevour(player, data, plus, now);
        helper.assertTrue(start == null, "the hold on a Plus was refused as " + start);
        helper.assertTrue(plus.isSeized(), "the held Plus is not seized");
        assertModifier(helper, player, Attributes.MOVEMENT_SPEED, DevourLogic.DEVOURING_ID, -0.5,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the devourer's slowness");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_DEVOURING) != 0,
                "the jaw does not hang open during the hold");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_DEVOURING_PLUS) != 0,
                "the hold on a Plus does not say it is on a Plus");
        start = DevourEvents.startDevour(player, data, plus, now);
        helper.assertTrue(start == null, "a second press on the held Plus was refused as " + start);
        helper.assertTrue(plus.isSeized()
                        && (HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_DEVOURING) != 0,
                "a second press on the held Plus let it go");
        assertModifier(helper, player, Attributes.MOVEMENT_SPEED, DevourLogic.DEVOURING_ID, -0.5,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the devourer's slowness after a second press");

        DevourEvents.stopDevour(player, data, null, now);
        helper.assertTrue(modifier(player, Attributes.MOVEMENT_SPEED, DevourLogic.DEVOURING_ID) == null,
                "the devourer stayed slow after letting go");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now)
                        & (HollowPlayerLogic.LOOK_DEVOURING | HollowPlayerLogic.LOOK_DEVOURING_PLUS)) == 0,
                "the jaw still hangs open after letting go");
        helper.assertFalse(plus.isSeized(), "the Plus stayed seized after letting go");
        start = DevourEvents.startDevour(player, data, plus, now + 5);
        helper.assertTrue(start == DevourLogic.Refusal.COOLDOWN, "a hold just after letting go gave " + start);

        helper.assertTrue(DevourEvents.complete(player, plus, now), "devouring the Plus did not kill it");
        helper.assertTrue(plus.isDeadOrDying(), "the devoured Plus lives");
        DamageSource last = plus.getLastDamageSource();
        helper.assertTrue(last != null && DevourLogic.isDevoured(last), "the Plus died of " + last);
        assertClose(helper, HollowBody.satiety(data), 90.0, "the hunger after a Plus");
        helper.assertTrue(data.hollow().pluses() == 1, "the Pluses devoured are " + data.hollow().pluses());
        helper.assertTrue(data.hollow().souls() == 1, "a Plus gave " + data.hollow().souls() + " souls");
        helper.assertTrue(data.hollow().lastMealAt() == now, "the last meal was at " + data.hollow().lastMealAt());
        // A fifth of 90 reiryoku and a quarter of 20 health.
        assertClose(helper, data.reiryoku(), 68.0, "the reiryoku after a Plus");
        assertClose(helper, player.getHealth(), 15.0, "the health after a Plus");
        // 12 reiatsu at 0.025 a point, cut to 24/90 of that against a devourer of 90, and doubled: 0.16.
        helper.assertTrue(data.maxReiryoku() > 90.0F && data.maxReiryoku() < 90.2F,
                "a Plus grew the maximum to " + data.maxReiryoku());
        plus.discard();
        clearOrbs(helper);
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void mealsAreWorthTheirReiatsu(GameTestHelper helper) {
        PlusSpirit plus = placed(helper, ModEntities.PLUS.get(), 8, 8);
        SpiderHollow spider = placed(helper, ModEntities.SPIDER_HOLLOW.get(), 10, 8);
        assertClose(helper, DevourLogic.satietyFor(DevourLogic.Prey.PLUS, 12.0F), 30.0, "a Plus's hunger");
        assertClose(helper, DevourLogic.satietyFor(DevourLogic.Prey.HOLLOW, 35.0F), 29.0, "a Hollow of 35's hunger");
        assertClose(helper, DevourLogic.satietyFor(DevourLogic.Prey.HOLLOW, 60.0F), 39.0, "a Hollow of 60's hunger");
        assertClose(helper, DevourLogic.satietyFor(DevourLogic.Prey.HOLLOW, 90.0F), 51.0, "a Hollow of 90's hunger");
        assertClose(helper, DevourLogic.satietyFor(DevourLogic.Prey.HOLLOW, 200.0F), 60.0, "a Hollow of 200's hunger");
        assertClose(helper, DevourLogic.satietyFor(DevourLogic.Prey.PLAYER, 90.0F), 51.0, "a player of 90's hunger");
        Map<Float, Integer> souls = Map.of(12.0F, 1, 35.0F, 4, 45.0F, 5, 90.0F, 9, 4.0F, 1);
        souls.forEach((reiatsu, count) -> helper.assertTrue(DevourLogic.soulsFor(reiatsu) == count,
                "a meal of " + reiatsu + " reiatsu gives " + DevourLogic.soulsFor(reiatsu) + " souls, not " + count));
        helper.assertTrue(DevourLogic.holdTicks(plus) == 20, "a Plus is held for " + DevourLogic.holdTicks(plus));
        helper.assertTrue(DevourLogic.holdTicks(spider) == 40, "a Hollow is held for " + DevourLogic.holdTicks(spider));
        spider.setHealth(6.0F);
        assertClose(helper, DevourLogic.killingBlow(spider), 80.0, "the blow that swallows a Hollow at 6 health");
        plus.discard();
        spider.discard();
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void devouredHollowIsEatenNotPurified(GameTestHelper helper) {
        Player player = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(player);
        HollowBody.tickBody(player, data, now(helper));
        HollowBody.setSatiety(data, 40.0F);
        SpiderHollow spider = placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        spider.soul();
        spider.setHealth(1.0F);
        float reiatsu = SoulLogic.reiatsu(spider);
        long now = now(helper);

        helper.assertTrue(DevourEvents.complete(player, spider, now), "devouring the Spider Hollow did not kill it");
        helper.assertTrue(spider.isDeadOrDying(), "the devoured Spider Hollow lives");
        helper.assertFalse(spider.isPurifying(), "the devoured Spider Hollow is being purified");
        helper.assertTrue(data.hunt().purified() == 0 && data.hunt().slain() == 0,
                "devouring counted as purifying or slaying: " + data.hunt());
        helper.assertTrue(data.hollow().hollows(HollowKind.SPIDER) == 1, "the Spider Hollows devoured are "
                + data.hollow().hollows(HollowKind.SPIDER));
        assertClose(helper, HollowBody.satiety(data), 40.0F + Math.min(60.0F, 15.0F + 0.4F * reiatsu),
                "the hunger after a Spider Hollow of " + reiatsu);
        int souls = Math.max(1, Math.round(reiatsu / 10.0F));
        helper.assertTrue(data.hollow().souls() == souls, "a Spider Hollow of " + reiatsu + " gave " + data.hollow().souls()
                + " souls, not " + souls);
        helper.assertTrue(player.totalExperience == HollowKind.SPIDER.xp,
                "a devoured Spider Hollow gave " + player.totalExperience + " experience");
        spider.discard();
        clearOrbs(helper);
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void devouringAPlayerTakesItsSouls(GameTestHelper helper) {
        Player devourer = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(devourer);
        Player victim = soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 90.0F, PREY_SPOT);
        SoulData victimData = SoulLogic.get(victim);
        victimData.setHollow(victimData.hollow().withSouls(40));
        victim.setHealth(4.0F);
        long now = now(helper);

        DevourLogic.Meal meal = DevourLogic.meal(devourer, victim);
        helper.assertTrue(meal.prey() == DevourLogic.Prey.PLAYER && meal.stolenSouls() == 10 && meal.hollowLike(),
                "a Hollow player is the meal " + meal);
        assertClose(helper, meal.satiety(), 51.0, "a Hollow player of 90's hunger");
        helper.assertTrue(meal.souls() == 9, "a Hollow player of 90 gives " + meal.souls() + " souls");
        float maxBefore = data.maxReiryoku();
        DevourEvents.reward(devourer, data, meal, now);
        helper.assertTrue(data.hollow().souls() == 19, "the first player gave " + data.hollow().souls() + " souls");
        helper.assertTrue(data.hollow().players() == 1, "the players devoured are " + data.hollow().players());
        helper.assertTrue(data.maxReiryoku() > maxBefore, "the first player grew nothing");
        helper.assertTrue(data.hunt().lastPlayerKillGrowthAt() == now, "the growth was stamped at "
                + data.hunt().lastPlayerKillGrowthAt());
        helper.assertTrue(data.hollow().evolution().tierHollows() == 1,
                "the first player counted " + data.hollow().evolution().tierHollows() + " Hollows for the form");

        // The same soul again, soon after: only the souls it would lose pass over, and nothing grows.
        float maxAfter = data.maxReiryoku();
        DevourEvents.reward(devourer, data, DevourLogic.meal(devourer, victim), now + 20);
        helper.assertTrue(data.hollow().souls() == 29, "the second player gave " + (data.hollow().souls() - 19) + " souls");
        helper.assertTrue(data.hollow().players() == 2, "the players devoured are " + data.hollow().players());
        helper.assertTrue(data.maxReiryoku() == maxAfter, "the second player grew the maximum to " + data.maxReiryoku());
        helper.assertTrue(data.hollow().evolution().tierHollows() == 1,
                "the second player counted for the form: " + data.hollow().evolution().tierHollows());
        helper.succeed();
    }

    // ---- Death and purification ----

    @GameTest(template = "empty")
    public static void deathKeepsTheHollowAndCostsSouls(GameTestHelper helper) {
        SoulData old = ShinigamiTests.soul(SoulState.HOLLOW, 90.0F);
        old.setHollow(old.hollow().withSatiety(12.0F).withSouls(40).withPluses(2));
        SoulData next = SoulLogic.afterDeath(old);
        helper.assertTrue(next.state() == SoulState.HOLLOW, "a Hollow came back as " + next.state());
        assertClose(helper, next.hollow().satiety(), 30.0, "the hunger after dying at 12");
        helper.assertTrue(next.hollow().souls() == 30, "a death left " + next.hollow().souls() + " of 40 souls");
        helper.assertTrue(next.hollow().pluses() == 2, "a death forgot the Pluses: " + next.hollow().pluses());
        old.setHollow(old.hollow().withSatiety(75.0F));
        assertClose(helper, SoulLogic.afterDeath(old).hollow().satiety(), 75.0, "the hunger after dying at 75");
        old.setHollow(old.hollow().withSatiety(HollowProgress.UNSET));
        assertClose(helper, SoulLogic.afterDeath(old).hollow().satiety(), 60.0, "the hunger after dying with none");

        HollowBody.markPurifiedOnDeath(old);
        helper.assertTrue(old.hollow().purifiedOnDeath(), "a purifying blow did not mark the Hollow");
        SoulData cleansed = SoulLogic.afterDeath(old);
        helper.assertTrue(cleansed.state() == SoulState.RUKONGAI, "a purified Hollow came back as " + cleansed.state());
        helper.assertTrue(cleansed.tier(PowerPath.HOLLOW) == 0,
                "a purified Hollow kept tier " + cleansed.tier(PowerPath.HOLLOW));
        helper.assertTrue(cleansed.hollow().satiety() == HollowProgress.UNSET, "a purified Hollow kept its hunger");
        helper.assertTrue(cleansed.hollow().souls() == 0, "a purified Hollow kept " + cleansed.hollow().souls() + " souls");
        helper.assertTrue(cleansed.hollow().pluses() == 2,
                "a purified Hollow forgot the Pluses: " + cleansed.hollow().pluses());
        helper.assertTrue(HollowBody.wasCleansed(cleansed), "the respawned soul does not know it was cleansed");
        helper.assertTrue(HollowBody.consumeCleansed(cleansed), "the cleansing was not told");
        helper.assertFalse(HollowBody.consumeCleansed(cleansed), "the cleansing was told twice");

        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 90.0F);
        HollowProgress before = human.hollow();
        HollowBody.markPurifiedOnDeath(human);
        helper.assertTrue(human.hollow().equals(before), "a purifying blow marked a Human: " + human.hollow());
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void instructorPurifiesAHollow(GameTestHelper helper) {
        AcademyInstructor instructor = placed(helper, ModEntities.ACADEMY_INSTRUCTOR.get(), 8, 8);
        WoundedShinigami wounded = placed(helper, ModEntities.WOUNDED_SHINIGAMI.get(), 10, 8);
        Player visitor = hollow(helper, 90.0F);
        SoulData data = SoulLogic.get(visitor);
        long now = now(helper);
        HollowBody.tickBody(visitor, data, now);
        helper.assertTrue(instructor.viewFor(data, now) == DialogueView.HOLLOW_PURIFY,
                "the instructor shows a Hollow " + instructor.viewFor(data, now));
        helper.assertTrue(wounded.viewFor(data, now) == DialogueView.HOLLOW, "the wounded Shinigami shows a Hollow "
                + wounded.viewFor(data, now));
        SoulData arrancar = ShinigamiTests.soul(SoulState.ARRANCAR, 90.0F);
        helper.assertTrue(instructor.viewFor(arrancar, now) == DialogueView.HOLLOW,
                "the instructor shows an Arrancar " + instructor.viewFor(arrancar, now));

        instructor.perform(visitor, NpcAction.PURIFY);
        helper.assertTrue(data.state() == SoulState.RUKONGAI, "the instructor's blade left a " + data.state());
        helper.assertTrue(data.tier(PowerPath.HOLLOW) == 0,
                "the instructor's blade left Hollow tier " + data.tier(PowerPath.HOLLOW));
        helper.assertTrue(data.hollow().satiety() == HollowProgress.UNSET, "the instructor's blade left the hunger");
        helper.assertTrue(modifier(visitor, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) == null,
                "the instructor's blade left the claws");
        helper.assertFalse(visitor.hasEffect(MobEffects.NIGHT_VISION), "the instructor's blade left the Hollow sight");

        // Asked again, the soul is no Hollow now: the view has moved on and nothing happens.
        HollowProgress after = data.hollow();
        instructor.perform(visitor, NpcAction.PURIFY);
        helper.assertTrue(instructor.viewFor(data, now) == DialogueView.SOUL, "the instructor shows the soul "
                + instructor.viewFor(data, now));
        helper.assertTrue(data.state() == SoulState.RUKONGAI && data.hollow().equals(after),
                "a second purification changed the soul to " + data.state() + " " + data.hollow());
        instructor.discard();
        wounded.discard();
        helper.succeed();
    }

    // ---- The chain and the Garganta ----

    @GameTest(template = "field")
    public static void gnawingSpeedsTheChain(GameTestHelper helper) {
        Player plus = soulPlayer(helper, GameType.SURVIVAL, SoulState.PLUS, 40.0F, DEVOURER_SPOT);
        SoulData data = SoulLogic.get(plus);
        long now = now(helper);
        helper.assertTrue(DevourLogic.gnawRefusal(plus, data) == DevourLogic.Gnaw.GNAWING,
                "a Plus may not gnaw: " + DevourLogic.gnawRefusal(plus, data));
        assertClose(helper, DevourLogic.gnawSpeed(data), 1.0, "the chain's pace before gnawing");
        DevourLogic.Gnaw started = DevourEvents.startGnaw(plus, data, now);
        helper.assertTrue(started == DevourLogic.Gnaw.GNAWING, "starting to gnaw gave " + started);
        assertClose(helper, DevourLogic.gnawSpeed(data), 40.0, "the chain's pace while gnawing");
        helper.assertTrue(DevourLogic.gnawing(data), "the Plus is not gnawing");
        DevourEvents.stopGnaw(data);
        assertClose(helper, DevourLogic.gnawSpeed(data), 1.0, "the chain's pace after gnawing");
        helper.assertFalse(DevourLogic.gnawing(data), "the Plus is still gnawing");

        Player creative = soulPlayer(helper, GameType.CREATIVE, SoulState.PLUS, 40.0F, DEVOURER_SPOT);
        DevourLogic.Gnaw creativeGnaw = DevourLogic.gnawRefusal(creative, SoulLogic.get(creative));
        helper.assertTrue(creativeGnaw == DevourLogic.Gnaw.OFF, "a creative Plus gnaws: " + creativeGnaw);
        Player human = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 40.0F, DEVOURER_SPOT);
        DevourLogic.Gnaw humanGnaw = DevourLogic.gnawRefusal(human, SoulLogic.get(human));
        helper.assertTrue(humanGnaw == DevourLogic.Gnaw.NOT_PLUS, "a Human gnaws: " + humanGnaw);
        Player quincy = soulPlayer(helper, GameType.SURVIVAL, SoulState.PLUS, 40.0F, DEVOURER_SPOT);
        quincy.setData(SoulAttachments.SOUL, QuincyTests.in(SoulState.PLUS, QuincyTests.quincy(Bloodline.ECHT, 1, 40.0F)));
        DevourLogic.Gnaw quincyGnaw = DevourLogic.gnawRefusal(quincy, SoulLogic.get(quincy));
        helper.assertTrue(quincyGnaw == DevourLogic.Gnaw.QUINCY, "an awakened Quincy Plus gnaws: " + quincyGnaw);
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void arrivalsAndRivalsForHollowPlayers(GameTestHelper helper) {
        SpiderHollow spider = placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 8);
        Player first = hollow(helper, 90.0F);
        Player second = hollow(helper, 90.0F);
        spider.setDrawer(first.getUUID());

        for (boolean night : new boolean[] {false, true}) {
            double hollowChance = HollowLogic.arrivalChancePerMinute(hunted(SoulState.HOLLOW, 90.0F), night);
            double humanChance = HollowLogic.arrivalChancePerMinute(hunted(SoulState.HUMAN, 90.0F), night);
            helper.assertTrue(Math.abs(hollowChance - humanChance) < CHANCE_EPSILON, "a Hollow's chance " + hollowChance
                    + " is not a Human's " + humanChance + (night ? " at night" : " by day"));
            helper.assertTrue(hollowChance > 0.0, "no Garganta opens for a Hollow" + (night ? " at night" : " by day"));
        }
        helper.assertTrue(HollowLogic.arrivalChancePerMinute(hunted(SoulState.ARRANCAR, 90.0F), true) == 0.0,
                "a Garganta may open for an Arrancar");

        helper.assertFalse(HollowLogic.isPrey(first), "Hollows hunt a Hollow player");
        helper.assertTrue(HollowLogic.isPreyFor(spider, first), "a Hollow does not hunt the Hollow player that drew it");
        helper.assertTrue(HollowLogic.isRival(spider, first), "the Hollow player that drew a Hollow is no rival");
        helper.assertFalse(HollowLogic.isPreyFor(spider, second), "a Hollow hunts a Hollow player that did not draw it");
        Player creative = soulPlayer(helper, GameType.CREATIVE, SoulState.HOLLOW, 90.0F, DEVOURER_SPOT);
        spider.setDrawer(creative.getUUID());
        helper.assertFalse(HollowLogic.isPreyFor(spider, creative), "a Hollow hunts the creative Hollow that drew it");
        Player human = soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 90.0F, DEVOURER_SPOT);
        helper.assertTrue(HollowLogic.isPreyFor(spider, human), "a Hollow does not hunt an aware Human");
        spider.discard();
        helper.succeed();
    }
}
