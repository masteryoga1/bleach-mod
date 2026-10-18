package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.item.SeeleSchneiderItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.quincy.QuincyStage;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.QuincyArts;
import io.github.masteryoga1.bleach.soul.QuincyArtsEvents;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyEvents;
import io.github.masteryoga1.bleach.soul.QuincyForms;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.QuincyProgress;
import io.github.masteryoga1.bleach.soul.SanreiProgress;
import io.github.masteryoga1.bleach.soul.SoulAppearance;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Quincy arts past the third tier: their saved form and the look other players see,
 * the numbers each form gives the bow, the Sanrei Glove and its training, Letzt Stil and the seal,
 * the rite and the arrow that bring the power back, Ginrei Kojaku's stream and Licht Regen, the
 * Seele Schneider, and Vollständig with its halo and Sklaverei. The rules in {@link QuincyArtsLogic}
 * are called directly, and {@link QuincyArtsEvents} runs on mock players from {@link ZanpakutoTests},
 * which are not in the world, so the time is passed in wherever a method takes it. Arrows and blades
 * fly for real on the {@code field} template, a 16 by 16 stone floor with creatures at relative y 2.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuincyArtsTests {
    private static final float EPSILON = 1.0E-4F;
    /** Room for the float steps of a health bar that took several hits. */
    private static final double HEALTH_EPSILON = 1.0E-3;
    private static final Vec3 SOUTH = new Vec3(0.0, 0.0, 1.0);
    private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 DOWN = new Vec3(0.0, -1.0, 0.0);
    /** How far round a mock its own shots are looked for, the moment they are loosed. */
    private static final double NEAR = 4.0;

    private QuincyArtsTests() {
    }

    // ---- Shared helpers ----

    /** A living Echt Human of the third tier with 200 reiryoku, the arts untouched. */
    static SoulData thirdTier() {
        return QuincyTests.quincy(Bloodline.ECHT, 3, 200.0F);
    }

    /** The same soul with its power come back as Ginrei Kojaku, at tick 0. */
    static SoulData restored(SoulData data) {
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withStage(QuincyStage.RESTORED, 0L));
        return data;
    }

    /** The same soul with its power sealed since {@code since}. */
    static SoulData sealed(SoulData data, long since) {
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withStage(QuincyStage.SEALED, since));
        return data;
    }

    /** The same soul in Letzt Stil since tick 0, burning until {@code ends}. */
    static SoulData letzt(SoulData data, long ends) {
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withStage(QuincyStage.LETZT_STIL, 0L).withLetztEndsAt(ends));
        return data;
    }

    /** The same soul in Vollständig on its record only: no flight is given, so a tick would end it. */
    static SoulData voll(SoulData data) {
        return forms(data, QuincyArtsLogic.arts(data).forms().withVollstandig(true));
    }

    /** The same soul with these forms. */
    static SoulData forms(SoulData data, QuincyForms forms) {
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withForms(forms));
        return data;
    }

    /** The same soul wearing the Sanrei Glove, mastered with every day counted, or fresh. */
    static SoulData gloved(SoulData data, boolean mastered) {
        int days = mastered ? BleachConfig.get(BleachConfig.SANREI_DAYS_TO_MASTER) : 0;
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withGlove(new SanreiProgress(true, true, days, 0L, 0,
                mastered)));
        return data;
    }

    /** Worn down to near collapse, as the rite and a restoring arrow need. */
    static void exhaust(Player player) {
        player.setHealth(4.0F);
        player.getFoodData().setFoodLevel(4);
    }

    /**
     * Turns a mock's eyes, head and body to a point. The last tick's head turn is set too, since a
     * look along the view (as Gintō and Licht Regen take) reads it, and a mock is never ticked.
     */
    static void face(Player player, Vec3 target) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        player.yHeadRotO = player.yHeadRot;
        player.yBodyRot = player.getYRot();
        player.yBodyRotO = player.yBodyRot;
    }

    /** Puts a Quincy Cross in the mock's main hand and draws it. */
    static void drawCross(Player player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.QUINCY_CROSS.get()));
        player.startUsingItem(InteractionHand.MAIN_HAND);
    }

    /** How many of this item the player carries. */
    static int carried(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** This owner's shots of this kind (and its kinds) a few blocks round them, such as those just loosed. */
    static <T extends HeiligPfeil> List<T> shotsNear(GameTestHelper helper, Player owner, Class<T> type) {
        return helper.getLevel().getEntitiesOfClass(type, owner.getBoundingBox().inflate(NEAR),
                shot -> shot.getOwner() == owner);
    }

    /** This owner's volley arrows a few blocks round them. */
    static List<HeiligPfeil> volleyNear(GameTestHelper helper, Player owner) {
        List<HeiligPfeil> volley = new ArrayList<>();
        for (HeiligPfeil arrow : shotsNear(helper, owner, HeiligPfeil.class)) {
            if (arrow.isVolley()) {
                volley.add(arrow);
            }
        }
        return volley;
    }

    static void discardAll(List<? extends Entity> entities) {
        for (Entity entity : entities) {
            entity.discard();
        }
    }

    /** Every dropped item in the test's box, so none is left lying about. */
    static void clearItems(GameTestHelper helper) {
        discardAll(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()));
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String what) {
        QuincyTests.assertNear(helper, actual, expected, what);
    }

    private static void assertKey(GameTestHelper helper, String actual, String expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + " gave " + actual + ", not " + expected);
    }

    private static boolean hasLetztSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(QuincyArtsEvents.LETZT_SPEED_ID);
    }

    private static boolean hasVollFlight(Player player) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        return flight != null && flight.hasModifier(QuincyArtsEvents.VOLL_FLIGHT_ID);
    }

    // ---- Saving and showing ----

    @GameTest(template = "empty")
    public static void artsSurviveSaving(GameTestHelper helper) {
        QuincyArts arts = new QuincyArts(new SanreiProgress(true, true, 3, 12L, 40, false), QuincyStage.LETZT_STIL, 1234L,
                5678L, 77, GintoSpell.HEIZEN, new QuincyForms(true, true, true, true, 900L, 950L));
        QuincyProgress progress = new QuincyProgress(Bloodline.ECHT, true, 77, 12.5F, 30.0F, BlutMode.VENE, 3, true, arts);
        Tag saved = QuincyProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow();
        QuincyProgress loaded = QuincyProgress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(progress), "the Quincy arts loaded as " + loaded.arts());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourASaveLoadsWithNoArts(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putString("bloodline", "echt");
        tag.putBoolean("decided", true);
        tag.putInt("training", 77);
        tag.putFloat("reishi", 12.5F);
        tag.putFloat("poison", 30.0F);
        tag.putString("blut", "vene");
        tag.putInt("destroyed", 3);
        tag.putBoolean("torn_apart", true);
        QuincyProgress loaded = QuincyProgress.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        helper.assertTrue(loaded.arts().equals(QuincyArts.NONE), "a save from before the arts loaded " + loaded.arts());
        QuincyProgress expected = new QuincyProgress(Bloodline.ECHT, true, 77, 12.5F, 30.0F, BlutMode.VENE, 3, true);
        helper.assertTrue(loaded.equals(expected), "a save from before the arts loaded as " + loaded);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void badArtsFieldsFallBack(GameTestHelper helper) {
        CompoundTag arts = new CompoundTag();
        arts.putInt("stage", 5);
        arts.putString("glove", "x");
        arts.putLong("letzt_ends_at", 99L);
        arts.putString("ginto", "nope");
        CompoundTag tag = new CompoundTag();
        tag.putString("bloodline", "echt");
        tag.put("arts", arts);
        QuincyArts loaded = QuincyProgress.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().arts();
        helper.assertTrue(loaded.stage() == QuincyStage.UNTOUCHED, "a bad stage loaded as " + loaded.stage());
        helper.assertTrue(loaded.glove().equals(SanreiProgress.NONE), "a bad glove loaded as " + loaded.glove());
        helper.assertTrue(loaded.letztEndsAt() == 99L, "a bad field wiped the good one beside it: " + loaded);
        helper.assertTrue(loaded.ginto() == GintoSpell.GRITZ, "a bad spell loaded as " + loaded.ginto());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quincyLookBitsAndStream(GameTestHelper helper) {
        int glove = QuincyArtsLogic.LOOK_GLOVE;
        int ginrei = QuincyArtsLogic.LOOK_GINREI;
        int voll = QuincyArtsLogic.LOOK_VOLL;
        QuincyForms inVoll = QuincyForms.NONE.withVollstandig(true);
        Map<String, Integer> expected = Map.of(
                "untouched", 0,
                "glove", glove,
                "mastered glove", glove | QuincyArtsLogic.LOOK_GLOVE_MASTERED,
                "Letzt Stil", QuincyArtsLogic.LOOK_LETZT,
                "sealed", QuincyArtsLogic.LOOK_SEALED,
                "restored", ginrei,
                "Vollständig", ginrei | voll,
                "Sklaverei", ginrei | voll | QuincyArtsLogic.LOOK_SKLAVEREI,
                "broken halo", ginrei | voll | QuincyArtsLogic.LOOK_HALO_BROKEN,
                "unawakened", 0);
        Map<String, SoulData> souls = Map.of(
                "untouched", thirdTier(),
                "glove", gloved(thirdTier(), false),
                "mastered glove", gloved(thirdTier(), true),
                "Letzt Stil", letzt(thirdTier(), 5000L),
                "sealed", sealed(thirdTier(), 0L),
                "restored", restored(thirdTier()),
                "Vollständig", forms(restored(thirdTier()), inVoll),
                "Sklaverei", forms(restored(thirdTier()), inVoll.withSklaverei(true)),
                "broken halo", forms(restored(thirdTier()), inVoll.withHaloBroken(true)),
                "unawakened", forms(letzt(QuincyTests.quincy(Bloodline.ECHT, 0, 200.0F), 5000L), inVoll));
        expected.forEach((who, bits) -> {
            int look = QuincyArtsLogic.quincyLook(souls.get(who));
            helper.assertTrue(look == bits, "a soul with " + who + " looks like " + look + ", not " + bits);
        });
        // Sklaverei only shows inside Vollständig.
        SoulData stray = forms(restored(thirdTier()), QuincyForms.NONE.withSklaverei(true));
        helper.assertTrue(QuincyArtsLogic.quincyLook(stray) == ginrei, "Sklaverei showed outside Vollständig");

        SoulAppearance appearance = new SoulAppearance(SoulState.HUMAN, 0.5F, true, 2, BlutMode.VENE, 0b10101010);
        ByteBuf buffer = Unpooled.buffer();
        SoulAppearance.STREAM_CODEC.encode(buffer, appearance);
        SoulAppearance read = SoulAppearance.STREAM_CODEC.decode(buffer);
        helper.assertTrue(read.equals(appearance), "the appearance came through the network as " + read);
        helper.assertTrue(new SoulAppearance(SoulState.HUMAN, 0.0F, false, -1).quincyLook() == 0,
                "the four-part appearance shows Quincy arts");
        helper.assertTrue(new SoulAppearance(SoulState.HUMAN, 0.0F, false, -1, BlutMode.VENE).quincyLook() == 0,
                "the five-part appearance shows Quincy arts");
        helper.succeed();
    }

    // ---- The numbers of each form ----

    @GameTest(template = "empty")
    public static void fourANumbersUnchanged(GameTestHelper helper) {
        SoulData data = thirdTier();
        helper.assertTrue(QuincyArtsLogic.drawTicks(data) == QuincyLogic.FULL_DRAW_TICKS,
                "an untouched draw takes " + QuincyArtsLogic.drawTicks(data) + " ticks");
        for (int ticks = 0; ticks <= 40; ticks++) {
            float power = QuincyArtsLogic.drawPower(ticks, QuincyLogic.FULL_DRAW_TICKS);
            helper.assertTrue(Float.compare(power, BowItem.getPowerForTime(ticks)) == 0,
                    "a draw of " + ticks + " ticks has power " + power + ", not the bow's " + BowItem.getPowerForTime(ticks));
        }
        for (float damage : new float[] {1.0F, 20.0F, 35.0F}) {
            helper.assertTrue(QuincyArtsLogic.boostDamage(data, damage) == damage, "an untouched soul boosted " + damage);
        }
        helper.assertTrue(QuincyArtsLogic.damageCap(data) >= 35.0F, "the cap fell to " + QuincyArtsLogic.damageCap(data));
        assertNear(helper, QuincyArtsLogic.poolMultiplier(data), 1.0, "an untouched pool multiplier");
        assertNear(helper, QuincyArtsLogic.reiatsuFactor(data), 1.0, "an untouched reiatsu factor");
        assertNear(helper, QuincyArtsLogic.gatherMultiplier(data), 1.0, "an untouched gathering multiplier");
        assertNear(helper, QuincyLogic.maxReishi(data), 30.0 + 60.0 + 20.0, "an untouched third-tier reishi pool");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gloveHandicapNumbers(GameTestHelper helper) {
        SoulData fresh = gloved(thirdTier(), false);
        helper.assertTrue(QuincyArtsLogic.drawTicks(fresh) == 40, "the fresh glove's draw takes " + QuincyArtsLogic.drawTicks(fresh));
        assertNear(helper, QuincyArtsLogic.gatherMultiplier(fresh), 0.5, "the fresh glove's gathering");
        assertNear(helper, QuincyArtsLogic.holdUpkeepMultiplier(fresh), 2.0, "the fresh glove's hold upkeep");
        assertNear(helper, QuincyArtsLogic.damageCap(fresh), 35.0, "the fresh glove's cap");

        SoulData mastered = gloved(thirdTier(), true);
        helper.assertTrue(QuincyArtsLogic.drawTicks(mastered) == 20,
                "the mastered glove's draw takes " + QuincyArtsLogic.drawTicks(mastered));
        assertNear(helper, QuincyArtsLogic.boostDamage(mastered, 38.0F), 40.0, "38 boosted by the mastered glove");
        assertNear(helper, QuincyArtsLogic.boostDamage(mastered, 10.0F), 11.0, "10 boosted by the mastered glove");
        helper.assertTrue(QuincyArtsLogic.drawPower(40, 40) == 1.0F, "a full 40-tick draw is not full");
        helper.assertTrue(QuincyArtsLogic.drawPower(10, 10) == 1.0F, "a full 10-tick draw is not full");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void letztNumbers(GameTestHelper helper) {
        SoulData pure = letzt(thirdTier(), 5000L);
        helper.assertTrue(QuincyArtsLogic.drawTicks(pure) == 10, "a Letzt Stil draw takes " + QuincyArtsLogic.drawTicks(pure));
        assertNear(helper, QuincyArtsLogic.poolMultiplier(pure), 3.0, "the Letzt Stil pool multiplier");
        assertNear(helper, QuincyArtsLogic.gatherMultiplier(pure), 4.0, "the Letzt Stil gathering multiplier");
        assertNear(helper, QuincyArtsLogic.gatherDensity(pure, 0.5), 2.0, "thin reishi gathered in Letzt Stil");
        assertNear(helper, QuincyArtsLogic.boostDamage(pure, 50.0F), 60.0, "50 boosted in Letzt Stil");
        assertNear(helper, QuincyArtsLogic.boostDamage(pure, 10.0F), 16.0, "10 boosted in Letzt Stil");
        helper.assertTrue(QuincyArtsLogic.extraPierce(pure, 1.0F) == 1, "a full Letzt Stil draw pierces no more");
        helper.assertTrue(QuincyArtsLogic.extraPierce(pure, 0.5F) == 0, "a half Letzt Stil draw pierces more");
        assertNear(helper, QuincyArtsLogic.reiatsuFactor(pure), 2.0, "the Letzt Stil reiatsu factor");
        assertNear(helper, QuincyArtsLogic.hirenkyakuDistanceMultiplier(pure), 1.5, "the Letzt Stil Hirenkyaku distance");
        assertNear(helper, QuincyArtsLogic.hirenkyakuCostMultiplier(pure), 0.5, "the Letzt Stil Hirenkyaku cost");
        assertNear(helper, QuincyArtsLogic.hirenkyakuCooldownMultiplier(pure), 0.5, "the Letzt Stil Hirenkyaku cooldown");
        assertNear(helper, QuincyArtsLogic.movementBonus(pure), 0.2, "the Letzt Stil speed");

        SoulData hybrid = QuincyTests.withShinigamiPower(letzt(thirdTier(), 5000L));
        assertNear(helper, QuincyArtsLogic.poolMultiplier(hybrid), 2.0, "a hybrid's Letzt Stil pool multiplier");
        assertNear(helper, QuincyArtsLogic.boostDamage(hybrid, 10.0F), 13.0, "10 boosted in a hybrid's Letzt Stil");
        assertNear(helper, QuincyArtsLogic.reiatsuFactor(hybrid), 1.5, "a hybrid's Letzt Stil reiatsu factor");
        assertNear(helper, QuincyArtsLogic.movementBonus(hybrid), 0.1, "a hybrid's Letzt Stil speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vollstandigNumbers(GameTestHelper helper) {
        SoulData pure = voll(restored(thirdTier()));
        assertNear(helper, QuincyArtsLogic.poolMultiplier(pure), 1.5, "the Vollständig pool multiplier");
        assertNear(helper, QuincyArtsLogic.boostDamage(pure, 10.0F), 13.0, "10 boosted in Vollständig");
        assertNear(helper, QuincyArtsLogic.damageCap(pure), 55.0, "the Vollständig cap");
        assertNear(helper, QuincyArtsLogic.reiatsuFactor(pure), 1.5, "the Vollständig reiatsu factor");
        assertNear(helper, QuincyArtsLogic.vollUpkeepPerSecond(pure, false), 1.0, "Vollständig's upkeep on the ground");
        assertNear(helper, QuincyArtsLogic.vollUpkeepPerSecond(pure, true), 2.5, "Vollständig's upkeep in flight");
        forms(pure, QuincyArtsLogic.arts(pure).forms().withSklaverei(true));
        assertNear(helper, QuincyArtsLogic.vollUpkeepPerSecond(pure, true), 3.5, "Vollständig's upkeep in flight with Sklaverei");

        SoulData hybrid = QuincyTests.withShinigamiPower(voll(restored(thirdTier())));
        assertNear(helper, QuincyArtsLogic.poolMultiplier(hybrid), 1.25, "a hybrid's Vollständig pool multiplier");
        assertNear(helper, QuincyArtsLogic.reiatsuFactor(hybrid), 1.25, "a hybrid's Vollständig reiatsu factor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sealedNumbers(GameTestHelper helper) {
        SoulData data = sealed(thirdTier(), 0L);
        helper.assertFalse(QuincyLogic.canUsePowers(data), "a sealed soul can use its power");
        helper.assertTrue(QuincyLogic.gatherPerSecond(data, 1.0, true, true) == 0.0F, "a sealed soul gathers reishi");
        assertNear(helper, QuincyArtsLogic.reiatsuFactor(data), 0.5, "the sealed reiatsu factor");
        assertNear(helper, QuincyArtsLogic.reiatsuFactor(QuincyTests.withShinigamiPower(sealed(thirdTier(), 0L))), 1.0,
                "a sealed hybrid's reiatsu factor");
        Player player = ZanpakutoTests.mock(helper, data);
        assertNear(helper, SoulLogic.reiatsu(player), 200.0 * 0.5, "a sealed soul's reiatsu");

        assertKey(helper, QuincyLogic.drawRefusal(data, 100L), "message.bleach.letzt_stil.sealed", "a draw too soon after the seal");
        assertKey(helper, QuincyLogic.drawRefusal(data, 48_000L), null, "a draw once the rite is open");
        helper.assertTrue(QuincyLogic.drawRefusal(data) != null, "a draw with no time given was allowed while sealed");
        helper.assertTrue(QuincyLogic.movementFor(data, false, 0L) == QuincyLogic.Movement.HIRENKYAKU,
                "a sealed Quincy's movement key is " + QuincyLogic.movementFor(data, false, 0L));
        helper.assertFalse(QuincyLogic.knowsHirenkyaku(data), "a sealed Quincy can still use Hirenkyaku");
        helper.assertTrue(QuincyLogic.blocksShinigamiPower(data) == QuincyLogic.blocksShinigamiPower(thirdTier()),
                "the seal changed whether the Quincy power blocks a Shinigami's");
        helper.succeed();
    }

    // ---- The Sanrei Glove ----

    @GameTest(template = "empty")
    public static void gloveRefusals(GameTestHelper helper) {
        String prefix = "message.bleach.sanrei_glove.";
        assertKey(helper, QuincyArtsLogic.gloveRefusal(QuincyTests.quincy(Bloodline.ECHT, 2, 200.0F)), prefix + "tier",
                "the glove on the second tier");
        assertKey(helper, QuincyArtsLogic.gloveRefusal(sealed(thirdTier(), 0L)), prefix + "stage", "the glove on a sealed soul");
        assertKey(helper, QuincyArtsLogic.gloveRefusal(gloved(thirdTier(), false)), prefix + "already_worn",
                "a second glove");
        assertKey(helper, QuincyArtsLogic.gloveRefusal(QuincyTests.in(SoulState.PLUS, thirdTier())), prefix + "powerless",
                "the glove on a Plus");
        assertKey(helper, QuincyArtsLogic.gloveRefusal(thirdTier()), null, "the glove on an untouched third tier");
        helper.succeed();
    }

    /** Glove training counts each day once, when the day's points are reached; enough days master it. */
    @GameTest(template = "empty")
    public static void gloveDaysCountOncePerDay(GameTestHelper helper) {
        SanreiProgress glove = new SanreiProgress(true, true, 0, 0L, 0, false);
        QuincyArtsLogic.GloveGain gain = QuincyArtsLogic.addGlovePoints(glove, 70, 5L);
        helper.assertTrue(gain.glove().today() == 70 && !gain.dayCounted() && gain.glove().days() == 0,
                "70 points on day 5 gave " + gain);
        gain = QuincyArtsLogic.addGlovePoints(gain.glove(), 20, 5L);
        helper.assertTrue(gain.glove().today() == 80 && gain.dayCounted() && gain.glove().days() == 1,
                "20 more points on day 5 gave " + gain);
        SanreiProgress counted = gain.glove();
        helper.assertTrue(QuincyArtsLogic.gloveToday(counted, 5L * 24_000L, 0L) == 80, "day 5 does not show its 80 points");
        helper.assertTrue(QuincyArtsLogic.gloveToday(counted, 6L * 24_000L, 0L) == 0, "day 6 shows day 5's points");
        gain = QuincyArtsLogic.addGlovePoints(counted, 10, 5L);
        helper.assertTrue(gain.glove().equals(counted) && !gain.dayCounted() && !gain.masteredNow(),
                "points past the day's count changed the glove: " + gain);
        gain = QuincyArtsLogic.addGlovePoints(gain.glove(), 80, 6L);
        helper.assertTrue(gain.dayCounted() && gain.glove().days() == 2, "a full day 6 gave " + gain);
        for (long day = 7L; day <= 11L; day++) {
            gain = QuincyArtsLogic.addGlovePoints(gain.glove(), 80, day);
            helper.assertTrue(gain.dayCounted(), "a full day " + day + " did not count");
            helper.assertTrue(gain.masteredNow() == (day == 11L), "day " + day + " gave " + gain);
        }
        helper.assertTrue(gain.glove().mastered() && gain.glove().days() == 7, "seven days left the glove at " + gain.glove());
        SanreiProgress mastered = gain.glove();
        gain = QuincyArtsLogic.addGlovePoints(mastered, 80, 12L);
        helper.assertTrue(gain.glove().equals(mastered) && !gain.dayCounted() && !gain.masteredNow(),
                "training a mastered glove changed it: " + gain);
        helper.assertTrue(QuincyArtsLogic.gloveDay(30_000L, 10L) == 1L, "the game time did not set the day");
        helper.assertTrue(QuincyArtsLogic.gloveDay(10L, 50_000L) == 2L, "the time of day did not set the day");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gloveHandedDownAtTierThree(GameTestHelper helper) {
        SoulData data = QuincyTests.quincy(Bloodline.ECHT, 2, 200.0F);
        data.setQuincy(data.quincy().withTraining(499));
        Player player = ZanpakutoTests.mock(helper, data);
        Item glove = ModItems.SANREI_GLOVE.get();
        QuincyEvents.train(player, data, 1);
        helper.assertTrue(QuincyLogic.tier(data) == 3, "the training did not reach the third tier");
        helper.assertTrue(QuincyArtsLogic.arts(data).glove().given(), "the third tier did not hand down the glove");
        helper.assertTrue(carried(player, glove) == 1, "the third tier handed down " + carried(player, glove) + " gloves");
        QuincyEvents.train(player, data, 1);
        helper.assertTrue(carried(player, glove) == 1, "more training handed down another glove");

        // A third-tier soul from before the glove is handed one when it next logs in.
        SoulData older = thirdTier();
        Player owed = ZanpakutoTests.mock(helper, older);
        QuincyArtsEvents.onRefresh(owed, older, 100L);
        helper.assertTrue(QuincyArtsLogic.arts(older).glove().given() && carried(owed, glove) == 1,
                "logging in did not hand down the glove an old third tier is owed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gloveWearAndTakeOff(GameTestHelper helper) {
        SoulData data = thirdTier();
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withGlove(new SanreiProgress(true, false, 3, 0L, 0, false)));
        Player player = ZanpakutoTests.mock(helper, data);
        assertKey(helper, QuincyArtsEvents.wearGlove(player, data), null, "putting the glove on");
        helper.assertTrue(QuincyArtsLogic.gloveWorn(data), "the glove is not worn");
        helper.assertTrue((QuincyArtsLogic.quincyLook(data) & QuincyArtsLogic.LOOK_GLOVE) != 0, "the glove does not show");

        QuincyArtsEvents.onFormKey(player, false, 1000L);
        helper.assertFalse(QuincyArtsLogic.gloveWorn(data), "the Quincy Form key did not take the glove off");
        helper.assertTrue(carried(player, ModItems.SANREI_GLOVE.get()) == 1, "the glove taken off did not come back");
        helper.assertTrue(QuincyArtsLogic.arts(data).glove().days() == 3, "taking the glove off lost the days trained");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gloveStrainBreaksDraw(GameTestHelper helper) {
        SoulData data = gloved(thirdTier(), false);
        Player player = ZanpakutoTests.mock(helper, data);
        drawCross(player);
        helper.assertFalse(QuincyArtsEvents.onBowHeld(player, data, 60), "the glove took over a draw within its strain");
        helper.assertTrue(player.isUsingItem(), "the glove broke a draw within its strain");
        helper.assertTrue(QuincyArtsEvents.onBowHeld(player, data, 81), "the glove let a draw run past its strain");
        helper.assertFalse(player.isUsingItem(), "a draw held past the glove's strain did not break");
        helper.succeed();
    }

    /** A full draw in the mastered glove looses two flank arrows of half the damage beside the shot, for 1 reishi more. */
    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void flankArrowsOnMasteredGlove(GameTestHelper helper) {
        SoulData data = gloved(thirdTier(), true);
        QuincyLogic.setReishi(data, 200.0F);
        Player player = QuincyTests.archer(helper, data, 8.5, 2.5);
        face(player, player.getEyePosition().add(helper.absoluteVec(SOUTH).subtract(helper.absoluteVec(Vec3.ZERO))));
        float before = QuincyLogic.reishi(data);
        QuincyEvents.release(player, 24);

        List<HeiligPfeil> arrows = shotsNear(helper, player, HeiligPfeil.class);
        helper.assertTrue(arrows.size() == 3, "a mastered full draw loosed " + arrows.size() + " arrows, not 3");
        float main = 0.0F;
        for (HeiligPfeil arrow : arrows) {
            main = Math.max(main, arrow.damage());
        }
        int flanks = 0;
        for (HeiligPfeil arrow : arrows) {
            if (Math.abs(arrow.damage() - main * 0.5F) < EPSILON) {
                flanks++;
            }
        }
        helper.assertTrue(flanks == 2, flanks + " of the arrows did half the shot's damage of " + main);
        float cost = QuincyLogic.shotCost(1.0F) + (float) BleachConfig.get(BleachConfig.SANREI_FLANK_COST);
        assertNear(helper, QuincyLogic.reishi(data), before - cost, "the reishi left after the shot and its flanks");
        discardAll(arrows);
        helper.succeed();
    }

    // ---- Quincy: Letzt Stil and the seal ----

    /** The glove is torn by two taps of the Quincy Form key no more than three seconds apart. */
    @GameTest(template = "empty")
    public static void tearNeedsTwoTapsInTime(GameTestHelper helper) {
        SoulData data = gloved(thirdTier(), true);
        QuincyLogic.setReishi(data, 10.0F);
        Player player = ZanpakutoTests.mock(helper, data);
        QuincyArtsEvents.onFormKey(player, false, 1000L);
        helper.assertTrue(QuincyArtsLogic.stage(data) == QuincyStage.UNTOUCHED, "one tap tore the glove");
        QuincyArtsEvents.onFormKey(player, false, 1070L);
        helper.assertTrue(QuincyArtsLogic.stage(data) == QuincyStage.UNTOUCHED, "two taps 70 ticks apart tore the glove");
        QuincyArtsEvents.onFormKey(player, false, 1100L);
        QuincyArts arts = QuincyArtsLogic.arts(data);
        helper.assertTrue(arts.stage() == QuincyStage.LETZT_STIL, "two taps 30 ticks apart left the power " + arts.stage());
        helper.assertTrue(arts.letztEndsAt() == 2900L, "Letzt Stil ends at " + arts.letztEndsAt() + ", not 2900");
        helper.assertFalse(arts.glove().worn(), "the torn glove is still worn");
        assertNear(helper, QuincyLogic.reishi(data), QuincyLogic.maxReishi(data), "the reishi as Letzt Stil begins");
        assertNear(helper, QuincyLogic.maxReishi(data), 3.0 * 110.0, "the Letzt Stil pool");
        helper.assertTrue(hasLetztSpeed(player), "Letzt Stil gave no speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void letztRefusals(GameTestHelper helper) {
        String prefix = "message.bleach.letzt_stil.";
        SoulData poisoned = gloved(thirdTier(), true);
        poisoned.setQuincy(poisoned.quincy().withPoison(60.0F));
        assertKey(helper, QuincyArtsLogic.letztRefusal(poisoned), prefix + "poisoned", "Letzt Stil when poisoned");
        assertKey(helper, QuincyArtsLogic.letztRefusal(restored(gloved(thirdTier(), true))), prefix + "once",
                "Letzt Stil a second time");
        assertKey(helper, QuincyArtsLogic.letztRefusal(thirdTier()), prefix + "no_glove", "Letzt Stil with no glove on");
        Player player = ZanpakutoTests.mock(helper, poisoned);
        helper.assertFalse(QuincyArtsEvents.startLetzt(player, poisoned, 1000L, false), "a poisoned soul tore the glove");
        helper.assertTrue(QuincyArtsLogic.stage(poisoned) == QuincyStage.UNTOUCHED, "a refused tear changed the stage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void letztEndsInSeal(GameTestHelper helper) {
        SoulData data = thirdTier();
        Player player = ZanpakutoTests.mock(helper, data);
        helper.assertTrue(QuincyArtsEvents.startLetzt(player, data, 1000L, true), "a forced Letzt Stil did not start");
        helper.assertTrue(hasLetztSpeed(player), "Letzt Stil gave no speed");
        QuincyArtsEvents.tick(player, data, 2799L);
        helper.assertTrue(QuincyArtsLogic.inLetzt(data), "Letzt Stil ended a tick early");
        QuincyArtsEvents.tick(player, data, 2800L);
        QuincyArts arts = QuincyArtsLogic.arts(data);
        helper.assertTrue(arts.stage() == QuincyStage.SEALED, "a spent Letzt Stil left the power " + arts.stage());
        helper.assertTrue(arts.stageSince() == 2800L, "the seal counts from " + arts.stageSince() + ", not 2800");
        helper.assertTrue(QuincyLogic.reishi(data) == 0.0F, "the seal left " + QuincyLogic.reishi(data) + " reishi");
        helper.assertFalse(hasLetztSpeed(player), "the seal left Letzt Stil's speed");
        helper.assertFalse(QuincyLogic.canUsePowers(data), "a sealed soul can use its power");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void letztDeathSeals(GameTestHelper helper) {
        QuincyArts letzt = new QuincyArts(SanreiProgress.NONE, QuincyStage.LETZT_STIL, 3200L, 5000L, 0, GintoSpell.GRITZ,
                new QuincyForms(true, true, true, true, 500L, 0L));
        QuincyArts after = QuincyArtsLogic.afterDeath(letzt);
        helper.assertTrue(after.stage() == QuincyStage.SEALED && after.stageSince() == 5000L,
                "death in Letzt Stil left " + after.stage() + " since " + after.stageSince());
        QuincyForms forms = after.forms();
        helper.assertFalse(forms.vollstandig() || forms.sklaverei() || forms.landingGrace(),
                "death left a form running: " + forms);
        helper.assertTrue(forms.vollReadyAt() == 500L, "death changed the Vollständig cooldown to " + forms.vollReadyAt());
        // The respawn soon after puts the clocks in reach of the game time, and keeps that seal as it is.
        long respawn = 3300L;
        helper.assertTrue(QuincyArtsLogic.normalise(after, 0, respawn).stageSince() == 5000L,
                "the respawn moved the seal from a death in Letzt Stil to " + QuincyArtsLogic.normalise(after, 0, respawn).stageSince());
        long tooFar = respawn + BleachConfig.get(BleachConfig.LETZT_DURATION_TICKS) + 1L;
        long pulled = QuincyArtsLogic.normalise(after.withStage(QuincyStage.SEALED, tooFar), 0, respawn).stageSince();
        helper.assertTrue(pulled == respawn, "a seal starting further ahead than a Letzt Stil lasts was left at " + pulled);

        QuincyArts restored = QuincyArts.NONE.withStage(QuincyStage.RESTORED, 100L).withTrainingAtRestore(40);
        helper.assertTrue(QuincyArtsLogic.afterDeath(restored).equals(restored), "death changed a restored power");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void letztResumesAfterReload(GameTestHelper helper) {
        long now = 10_000L;
        SoulData data = thirdTier();
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withStage(QuincyStage.LETZT_STIL, now - 1200L)
                .withLetztEndsAt(now + 600L));
        Tag saved = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        SoulData loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(QuincyArtsLogic.arts(loaded).letztEndsAt() == now + 600L,
                "saving moved the end of Letzt Stil to " + QuincyArtsLogic.arts(loaded).letztEndsAt());
        QuincyArts arts = QuincyArtsLogic.arts(data);
        int training = data.quincy().training();
        helper.assertTrue(QuincyArtsLogic.normalise(arts, training, now).equals(arts), "loading cut a Letzt Stil short");

        QuincyArts tooLong = QuincyArtsLogic.normalise(arts.withLetztEndsAt(now + 5000L), training, now);
        helper.assertTrue(tooLong.letztEndsAt() == now, "a Letzt Stil with more than its time left ends at "
                + tooLong.letztEndsAt());
        QuincyArtsLogic.setArts(data, tooLong);
        Player player = ZanpakutoTests.mock(helper, data);
        QuincyArtsEvents.tick(player, data, now);
        helper.assertTrue(QuincyArtsLogic.isSealed(data), "a Letzt Stil whose time ran out was not sealed");
        helper.succeed();
    }

    /** A sealed soul is no longer poisoned and can wear, fill or form nothing, but still casts a filled Gintō. */
    @GameTest(template = "empty")
    public static void sealedRefusals(GameTestHelper helper) {
        SoulData data = sealed(thirdTier(), 0L);
        Player player = ZanpakutoTests.mock(helper, data);
        QuincyEvents.addPoison(player, data, 10.0F);
        helper.assertTrue(data.quincy().poison() == 0.0F, "a sealed soul was poisoned to " + data.quincy().poison());
        assertKey(helper, QuincyArtsEvents.wearGlove(player, data), "message.bleach.sanrei_glove.stage", "the glove when sealed");
        assertKey(helper, QuincyArtsLogic.fillRefusal(data), "message.bleach.ginto.powerless", "filling a Gintō when sealed");
        helper.assertFalse(QuincyArtsLogic.canFormSeele(data), "a sealed soul can form a Seele Schneider");
        assertKey(helper, QuincyArtsLogic.castRefusal(data, GintoSpell.WOLKE), null, "casting Wolke when sealed");
        helper.succeed();
    }

    // ---- Restoration ----

    @GameTest(template = "empty")
    public static void riteRefusals(GameTestHelper helper) {
        SoulData data = sealed(thirdTier(), 0L);
        Player player = ZanpakutoTests.mock(helper, data);
        assertKey(helper, QuincyArtsLogic.riteRefusal(player, data, 1000L), "message.bleach.rite.too_soon",
                "the rite a moment after the seal");
        assertKey(helper, QuincyArtsLogic.riteRefusal(player, data, 48_000L), "message.bleach.rite.not_exhausted",
                "the rite at full health");
        exhaust(player);
        assertKey(helper, QuincyArtsLogic.riteRefusal(player, data, 48_000L), null, "the rite worn down");
        helper.succeed();
    }

    /** The rite, held through the real draw: let go too soon it breaks; held long enough it restores the power. */
    @GameTest(template = "empty")
    public static void riteRestores(GameTestHelper helper) {
        // The draw reads the level's own clock, so the seal is dated by it.
        long now = helper.getLevel().getGameTime();
        SoulData data = sealed(thirdTier(), now - QuincyArtsLogic.sealedTicksNeeded(false));
        data.setQuincy(data.quincy().withTraining(600));
        Player player = ZanpakutoTests.mock(helper, data);
        exhaust(player);
        drawCross(player);
        helper.assertTrue(QuincyArtsEvents.onBowHeld(player, data, 1), "the sealed draw was not taken as the rite");
        helper.assertTrue(player.isUsingItem(), "the rite was refused at its first tick");
        helper.assertTrue(QuincyArtsEvents.onRelease(player, data, 99), "a rite let go too soon fired");
        helper.assertTrue(QuincyArtsLogic.isSealed(data), "a rite let go too soon restored the power");
        helper.assertTrue(QuincyArtsEvents.onRelease(player, data, 100), "the full rite was not taken as the rite");
        QuincyArts arts = QuincyArtsLogic.arts(data);
        helper.assertTrue(arts.stage() == QuincyStage.RESTORED, "the full rite left the power " + arts.stage());
        helper.assertTrue(arts.trainingAtRestore() == 600, "the restoration noted training " + arts.trainingAtRestore());
        assertNear(helper, QuincyLogic.reishi(data), QuincyLogic.maxReishi(data), "the reishi after the restoration");
        for (var effect : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS)) {
            MobEffectInstance instance = player.getEffect(effect);
            helper.assertTrue(instance != null && instance.getDuration() == QuincyArtsLogic.RESTORE_EFFECT_TICKS,
                    "the restoration gave " + instance + " for " + effect.getRegisteredName());
        }
        assertNear(helper, player.getHealth(), 4.0, "the health after the restoration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void besideTheHeartGeometry(GameTestHelper helper) {
        Vec3 feet = Vec3.ZERO;
        Vec3 north = new Vec3(0.0, 0.0, -1.0);
        Map<String, Boolean> shots = new LinkedHashMap<>();
        shots.put("just beside the heart", QuincyArtsLogic.besideTheHeart(new Vec3(0.05, 1.26, 5.0), north, feet, 0.0F, 1.8F));
        shots.put("through the middle", QuincyArtsLogic.besideTheHeart(new Vec3(0.0, 1.26, 5.0), north, feet, 0.0F, 1.8F));
        shots.put("beside a sneaking heart", QuincyArtsLogic.besideTheHeart(new Vec3(0.05, 1.05, 5.0), north, feet, 0.0F, 1.5F));
        shots.put("in from the side", QuincyArtsLogic.besideTheHeart(new Vec3(3.55, 1.26, 3.5),
                new Vec3(-1.0, 0.0, -1.0).normalize(), feet, 0.0F, 1.8F));
        shots.forEach((what, hit) -> helper.assertTrue(hit, "a shot " + what + " missed the heart"));

        double off = Math.toRadians(70.0);
        Vec3 slanted = new Vec3(-Math.sin(off), 0.0, -Math.cos(off));
        Vec3 slantedFrom = new Vec3(0.05, 1.26, 0.0).subtract(slanted.scale(5.0));
        Map<String, Boolean> misses = new LinkedHashMap<>();
        misses.put("through the right side", QuincyArtsLogic.besideTheHeart(new Vec3(-0.2, 1.26, 5.0), north, feet, 0.0F, 1.8F));
        misses.put("too far left", QuincyArtsLogic.besideTheHeart(new Vec3(0.25, 1.26, 5.0), north, feet, 0.0F, 1.8F));
        misses.put("at the head", QuincyArtsLogic.besideTheHeart(new Vec3(0.05, 1.62, 5.0), north, feet, 0.0F, 1.8F));
        misses.put("at the legs", QuincyArtsLogic.besideTheHeart(new Vec3(0.05, 0.5, 5.0), north, feet, 0.0F, 1.8F));
        misses.put("from behind", QuincyArtsLogic.besideTheHeart(new Vec3(0.05, 1.26, -5.0), new Vec3(0.0, 0.0, 1.0), feet,
                0.0F, 1.8F));
        misses.put("70 degrees off the front", QuincyArtsLogic.besideTheHeart(slantedFrom, slanted, feet, 0.0F, 1.8F));
        misses.put("with no heading", QuincyArtsLogic.besideTheHeart(new Vec3(0.05, 1.26, 5.0), Vec3.ZERO, feet, 0.0F, 1.8F));
        misses.forEach((what, hit) -> helper.assertFalse(hit, "a shot " + what + " was taken as beside the heart"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrowRestorePreconditions(GameTestHelper helper) {
        Player owner = ZanpakutoTests.mock(helper, thirdTier());
        helper.assertTrue(QuincyArtsLogic.canRestoreOthers(owner), "a third-tier Quincy cannot restore others");
        helper.assertFalse(QuincyArtsLogic.canRestoreOthers(ZanpakutoTests.mock(helper, QuincyTests.quincy(Bloodline.ECHT, 2,
                200.0F))), "a second-tier Quincy can restore others");
        helper.assertFalse(QuincyArtsLogic.canRestoreOthers(ZanpakutoTests.mock(helper, sealed(thirdTier(), 0L))),
                "a sealed Quincy can restore others");

        SoulData sealed = sealed(thirdTier(), 0L);
        Player target = ZanpakutoTests.mock(helper, sealed);
        target.setYRot(0.0F);
        target.yBodyRot = 0.0F;
        exhaust(target);
        Vec3 feet = target.position();
        Vec3 from = feet.add(0.05, 1.26, 5.0);
        Vec3 to = feet.add(0.05, 1.26, -5.0);
        List<Player> both = List.of(owner, target);
        helper.assertTrue(HeiligPfeil.heartTarget(owner, from, to, both, 6000L) == target,
                "a shot beside the heart did not find the sealed Quincy");
        helper.assertTrue(HeiligPfeil.heartTarget(owner, from, to, both, 5999L) == null,
                "a shot found a Quincy sealed a tick too briefly");
        target.setHealth(target.getMaxHealth());
        helper.assertTrue(HeiligPfeil.heartTarget(owner, from, to, both, 6000L) == null,
                "a shot found a sealed Quincy who is not worn down");
        exhaust(target);
        helper.assertTrue(QuincyArtsEvents.tryRestoreByArrow(owner, target, 6000L), "the arrow did not restore the power");
        helper.assertTrue(QuincyArtsLogic.isRestored(sealed), "the arrow left the power " + QuincyArtsLogic.stage(sealed));
        helper.assertTrue(QuincyArtsLogic.justRestored(target, 6000L + QuincyArtsLogic.RESTORE_GRACE_TICKS),
                "the restoring shot's flank arrows may strike the Quincy it restored");
        helper.assertFalse(QuincyArtsLogic.justRestored(target, 6001L + QuincyArtsLogic.RESTORE_GRACE_TICKS),
                "a restored Quincy is passed by arrows long after");
        helper.succeed();
    }

    // ---- Ginrei Kojaku and Licht Regen ----

    /** A stream's arrows land through the hurt cooldown, so every one counts; plain arrows that close do not. */
    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 80)
    public static void streamArrowsAllLand(GameTestHelper helper) {
        Pig streamed = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 8, 2, 10));
        Pig plain = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 12, 2, 10));
        Player player = QuincyTests.archer(helper, restored(thirdTier()), 8.5, 2.5);
        Vec3 south = helper.absoluteVec(SOUTH).subtract(helper.absoluteVec(Vec3.ZERO));
        for (int tick : new int[] {1, 3, 5, 7}) {
            helper.runAtTickTime(tick, () -> {
                HeiligPfeil.launchVolley(helper.getLevel(), player, helper.absoluteVec(new Vec3(8.5, 2.5, 2.5)), south, 1.5F,
                        2.6F, 0.0F, 30);
                QuincyTests.loose(helper, player, helper.absoluteVec(new Vec3(12.5, 2.5, 2.5)), SOUTH, 1.5F, 0, 2.6F);
            });
        }
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(Math.abs(streamed.getHealth() - (10.0F - 6.0F)) < HEALTH_EPSILON,
                    "four stream arrows left the pig at " + streamed.getHealth() + ", not 4");
            helper.assertTrue(Math.abs(plain.getHealth() - (10.0F - 1.5F)) < HEALTH_EPSILON,
                    "four plain arrows close together left the pig at " + plain.getHealth() + ", not 8.5");
            streamed.discard();
            plain.discard();
            helper.succeed();
        });
    }

    /** A held Ginrei Kojaku streams an arrow every 5 ticks from 10 past the full draw, paid by the arrow. */
    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void streamFromRestoredDraw(GameTestHelper helper) {
        SoulData data = restored(thirdTier());
        QuincyLogic.setReishi(data, 50.0F);
        Player player = QuincyTests.archer(helper, data, 8.5, 8.5);
        drawCross(player);
        for (int ticks = 1; ticks <= 40; ticks++) {
            QuincyEvents.onBowHeld(player, ticks);
        }
        List<HeiligPfeil> stream = volleyNear(helper, player);
        helper.assertTrue(stream.size() == 4, "40 ticks of a held Ginrei Kojaku streamed " + stream.size() + " arrows, not 4");
        assertNear(helper, QuincyLogic.reishi(data), 50.0 - 4 * BleachConfig.get(BleachConfig.GINREI_STREAM_COST),
                "the reishi left after the stream");
        helper.assertTrue(QuincyArtsEvents.onRelease(player, data, 40), "letting go of the stream fired a shot");
        helper.assertTrue(shotsNear(helper, player, HeiligPfeil.class).size() == 4, "letting go of the stream loosed an arrow");
        discardAll(stream);
        helper.succeed();
    }

    /** The Quincy Form key while drawing gathers Licht Regen for 30 ticks, then 20 arrows fall, 4 a tick. */
    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void lichtRegenVolley(GameTestHelper helper) {
        SoulData data = restored(thirdTier());
        QuincyLogic.setReishi(data, 100.0F);
        Player player = QuincyTests.archer(helper, data, 8.5, 8.5);
        drawCross(player);
        long now = helper.getLevel().getGameTime();
        QuincyArtsEvents.onFormKey(player, false, now);
        QuincyArtsEvents.tick(player, data, now + 29L);
        helper.assertTrue(player.isUsingItem() && volleyNear(helper, player).isEmpty(), "Licht Regen fell before it gathered");
        QuincyArtsEvents.tick(player, data, now + 30L);
        helper.assertFalse(player.isUsingItem(), "the bow was still drawn when Licht Regen was called");
        assertNear(helper, QuincyLogic.reishi(data), 75.0, "the reishi left after Licht Regen");
        for (long tick = now + 31L; tick <= now + 35L; tick++) {
            QuincyArtsEvents.tick(player, data, tick);
        }
        List<HeiligPfeil> volley = volleyNear(helper, player);
        helper.assertTrue(volley.size() == 20, "Licht Regen loosed " + volley.size() + " arrows, not 20");

        drawCross(player);
        QuincyArtsEvents.onFormKey(player, false, now + 60L);
        QuincyArtsEvents.tick(player, data, now + 95L);
        assertNear(helper, QuincyLogic.reishi(data), 75.0, "the reishi left after Licht Regen was called again in its cooldown");
        helper.assertTrue(player.isUsingItem(), "Licht Regen let the bow go again before its cooldown ended");
        QuincyArtsEvents.tick(player, data, now + 96L);
        helper.assertTrue(volleyNear(helper, player).size() == 20, "Licht Regen fell again before its cooldown ended");
        discardAll(volleyNear(helper, player));
        helper.succeed();
    }

    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void letztChargedShot(GameTestHelper helper) {
        SoulData data = letzt(thirdTier(), 100_000L);
        Player player = QuincyTests.archer(helper, data, 8.5, 8.5);
        drawCross(player);
        QuincyLogic.setReishi(data, 10.0F);
        helper.assertFalse(QuincyArtsEvents.onRelease(player, data, 60), "a Letzt Pfeil was fired without its reishi");
        QuincyLogic.setReishi(data, 100.0F);
        helper.assertTrue(QuincyArtsEvents.onRelease(player, data, 60), "a charged Letzt Stil draw fired no Letzt Pfeil");
        List<LetztPfeil> shots = shotsNear(helper, player, LetztPfeil.class);
        helper.assertTrue(shots.size() == 1, "a charged draw fired " + shots.size() + " Letzt Pfeil");
        assertNear(helper, QuincyLogic.reishi(data), 100.0 - BleachConfig.get(BleachConfig.LETZT_PFEIL_COST),
                "the reishi left after a Letzt Pfeil");
        discardAll(shots);
        helper.succeed();
    }

    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void letztPfeilCarves(GameTestHelper helper) {
        for (int x = 6; x <= 8; x++) {
            helper.setBlock(x, 2, 8, Blocks.DIRT);
        }
        helper.setBlock(10, 2, 8, Blocks.OBSIDIAN);
        Player player = QuincyTests.archer(helper, letzt(thirdTier(), 100_000L), 2.5, 8.5);
        Vec3 east = helper.absoluteVec(EAST).subtract(helper.absoluteVec(Vec3.ZERO));
        LetztPfeil.launch(helper.getLevel(), player, helper.absoluteVec(new Vec3(2.5, 2.5, 8.5)), east, 50.0F, 10, 3.5F);
        helper.runAtTickTime(20, () -> {
            for (int x = 6; x <= 8; x++) {
                helper.assertBlockPresent(Blocks.AIR, x, 2, 8);
            }
            helper.assertBlockPresent(Blocks.OBSIDIAN, 10, 2, 8);
            clearItems(helper);
            helper.succeed();
        });
    }

    // ---- Seele Schneider ----

    @GameTest(template = "empty")
    public static void seeleFormsOnlyWhenRestored(GameTestHelper helper) {
        SoulData data = thirdTier();
        QuincyLogic.setReishi(data, 50.0F);
        Player player = ZanpakutoTests.mock(helper, data);
        ItemStack blade = new ItemStack(ModItems.SEELE_SCHNEIDER.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        blade.getItem().inventoryTick(blade, helper.getLevel(), player, 0, true);
        helper.assertFalse(SeeleSchneiderItem.isFormed(blade), "an untouched Quincy formed a Seele Schneider");

        restored(data);
        blade.getItem().inventoryTick(blade, helper.getLevel(), player, 0, true);
        helper.assertTrue(SeeleSchneiderItem.isFormed(blade), "a restored Quincy did not form the Seele Schneider");
        ItemAttributeModifiers formed = blade.get(DataComponents.ATTRIBUTE_MODIFIERS);
        boolean damage = false;
        for (ItemAttributeModifiers.Entry entry : formed == null ? List.<ItemAttributeModifiers.Entry>of() : formed.modifiers()) {
            if (entry.matches(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID)
                    && Math.abs(entry.modifier().amount() - 6.0) < EPSILON) {
                damage = true;
            }
        }
        helper.assertTrue(damage, "the formed blade's attack damage is not 6 over the hand: " + formed);
        assertNear(helper, QuincyLogic.reishi(data), 50.0 - BleachConfig.get(BleachConfig.SEELE_FORM_COST),
                "the reishi left after forming the blade");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, blade);
        blade.getItem().inventoryTick(blade, helper.getLevel(), player, 0, false);
        helper.assertFalse(SeeleSchneiderItem.isFormed(blade), "a blade moved to the offhand stayed formed");
        helper.assertTrue(Objects.equals(blade.get(DataComponents.ATTRIBUTE_MODIFIERS),
                        blade.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS)),
                "the bare hilt kept stats of its own: " + blade.get(DataComponents.ATTRIBUTE_MODIFIERS));

        ItemStack dropped = new ItemStack(ModItems.SEELE_SCHNEIDER.get());
        SeeleSchneiderItem.form(dropped);
        Vec3 at = player.position();
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, dropped);
        dropped.getItem().onEntityItemUpdate(dropped, item);
        helper.assertFalse(SeeleSchneiderItem.isFormed(item.getItem()), "a formed blade on the ground stayed formed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void seeleDestroysSouls(GameTestHelper helper) {
        // A hybrid with Shinigami power, who can purify, so the purify checks below weigh the weapon.
        SoulData data = QuincyTests.withShinigamiPower(restored(thirdTier()));
        Player player = ZanpakutoTests.mock(helper, data);
        ItemStack blade = new ItemStack(ModItems.SEELE_SCHNEIDER.get());
        SeeleSchneiderItem.form(blade);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        long now = helper.getLevel().getGameTime();
        DamageSource cut = player.damageSources().playerAttack(player);
        helper.assertTrue(HollowLogic.destroysSouls(cut), "a formed Seele Schneider's cut does not destroy souls");
        helper.assertFalse(HollowLogic.purifies(cut, data, now), "a Seele Schneider's cut purifies");
        helper.assertFalse(HollowLogic.destroysSouls(player.damageSources().thorns(player)),
                "thorns from a Quincy holding a formed Seele Schneider destroy souls");
        helper.assertTrue(SoulEvents.isZanjutsuWeapon(blade), "a formed Seele Schneider is no blade for Zanjutsu");
        DamageSource arrow = new DamageSource(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(HeiligPfeil.DAMAGE_TYPE), null, player);
        helper.assertTrue(HollowLogic.destroysSouls(arrow), "a Heilig Pfeil does not destroy souls");
        helper.assertFalse(HollowLogic.purifies(arrow, data, now), "a Heilig Pfeil purifies");

        SeeleSchneiderItem.unform(blade);
        helper.assertFalse(HollowLogic.destroysSouls(player.damageSources().playerAttack(player)),
                "a bare Seele Schneider hilt destroys souls");
        helper.assertFalse(SoulEvents.isZanjutsuWeapon(blade), "a bare Seele Schneider hilt counts for Zanjutsu");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ASAUCHI.get()));
        helper.assertTrue(HollowLogic.purifies(player.damageSources().playerAttack(player), data, now),
                "the same hybrid's Asauchi cut does not purify");
        helper.succeed();
    }

    /** A formed Seele Schneider cuts through half the armour it meets, and draws reishi back from the cut. */
    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void seeleArmourAndDrain(GameTestHelper helper) {
        SoulData data = restored(thirdTier());
        QuincyLogic.setReishi(data, 10.0F);
        Player player = QuincyTests.archer(helper, data, 8.5, 4.5);
        Zombie cut = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 2, 8));
        Zombie struck = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 10, 2, 8));
        cut.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        struck.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        // The chestplates' armour counts from the zombies' next tick.
        helper.runAtTickTime(5, () -> {
            ItemStack blade = new ItemStack(ModItems.SEELE_SCHNEIDER.get());
            SeeleSchneiderItem.form(blade);
            player.setItemInHand(InteractionHand.MAIN_HAND, blade);
            float reishi = QuincyLogic.reishi(data);
            // Each loss is measured within the one call, so daylight burning between ticks does not count.
            float bySeele = loss(cut, player);
            float gained = QuincyLogic.reishi(data) - reishi;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            float bySword = loss(struck, player);
            helper.assertTrue(bySeele > bySword, "the Seele Schneider's cut took " + bySeele
                    + " through the armour, no more than the sword's " + bySword);
            helper.assertTrue(gained > 0.0F && gained <= 5.0F, "the cut drew back " + gained + " reishi");
            cut.discard();
            struck.discard();
            helper.succeed();
        });
    }

    /** What a blow of 7 from this player takes from a creature, its last blow forgotten. */
    private static float loss(Zombie target, Player attacker) {
        target.invulnerableTime = 0;
        float before = target.getHealth();
        target.hurt(attacker.damageSources().playerAttack(attacker), 7.0F);
        return before - target.getHealth();
    }

    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void seeleSticksAndIsPickedUp(GameTestHelper helper) {
        Player player = QuincyTests.archer(helper, restored(thirdTier()), 2.5, 2.5);
        Vec3 down = helper.absoluteVec(DOWN).subtract(helper.absoluteVec(Vec3.ZERO));
        SeeleSchneiderPfeil blade = SeeleSchneiderPfeil.launch(helper.getLevel(), player,
                helper.absoluteVec(new Vec3(8.5, 5.0, 8.5)), down, 5.0F, 0, 1.0F);
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(blade.planted(), "the Seele Schneider fired at the floor did not stick in it");
            helper.assertTrue(blade.pickup == AbstractArrow.Pickup.ALLOWED, "the stuck blade cannot be picked up: " + blade.pickup);
        });
        helper.runAtTickTime(25, () -> {
            blade.playerTouch(player);
            helper.assertTrue(blade.isRemoved(), "touching the stuck blade did not pick it up");
            helper.assertTrue(carried(player, ModItems.SEELE_SCHNEIDER.get()) == 1,
                    "picking up the blade gave " + carried(player, ModItems.SEELE_SCHNEIDER.get()) + " Seele Schneider");
            helper.succeed();
        });
    }

    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 40)
    public static void seeleDespawnDropsHilt(GameTestHelper helper) {
        Player player = QuincyTests.archer(helper, restored(thirdTier()), 2.5, 2.5);
        SeeleSchneiderPfeil blade = SeeleSchneiderPfeil.plant(helper.getLevel(), player,
                helper.absolutePos(new BlockPos(8, 1, 8)));
        helper.assertTrue(blade != null && blade.planted(), "no blade was planted in the floor");
        blade.setStuckTicksForTest(BleachConfig.get(BleachConfig.SEELE_STUCK_DESPAWN_TICKS) - 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(blade.isRemoved(), "the blade stuck its full time is still standing");
            List<ItemEntity> hilts = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                    item -> SeeleSchneiderItem.isSeele(item.getItem()));
            helper.assertTrue(hilts.size() == 1 && hilts.get(0).getItem().getCount() == 1,
                    "the faded blade left " + hilts.size() + " hilts");
            clearItems(helper);
        });
    }

    /** A Heilig Pfeil still bursts on the block it meets; only a Seele Schneider sticks there. */
    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 40)
    public static void heiligPfeilHooksDefault(GameTestHelper helper) {
        Player player = QuincyTests.archer(helper, 2.5, 2.5);
        HeiligPfeil arrow = QuincyTests.loose(helper, player, helper.absoluteVec(new Vec3(8.5, 4.0, 8.5)), DOWN, 1.0F, 0, 1.0F);
        helper.assertFalse(arrow.isVolley(), "a plain Heilig Pfeil is a volley arrow");
        Vec3 down = helper.absoluteVec(DOWN).subtract(helper.absoluteVec(Vec3.ZERO));
        SeeleSchneiderPfeil blade = SeeleSchneiderPfeil.launch(helper.getLevel(), player,
                helper.absoluteVec(new Vec3(4.5, 4.0, 8.5)), down, 1.0F, 0, 1.0F);
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(arrow.isRemoved(), "the Heilig Pfeil did not burst on the floor");
            helper.assertFalse(blade.isRemoved(), "the Seele Schneider was gone after meeting the floor");
            blade.discard();
            helper.succeed();
        });
    }

    // ---- Quincy: Vollständig and Sklaverei ----

    @GameTest(template = "empty")
    public static void vollstandigUnlockAndStart(GameTestHelper helper) {
        long now = 1000L;
        SoulData data = restored(thirdTier());
        QuincyArtsLogic.setArts(data, QuincyArtsLogic.arts(data).withTrainingAtRestore(600));
        data.setQuincy(data.quincy().withTraining(1099));
        assertKey(helper, QuincyArtsLogic.vollRefusal(data, now), "message.bleach.vollstandig.locked",
                "Vollständig a point of training short");
        helper.assertTrue(QuincyArtsLogic.vollTrainingLeft(data) == 1, QuincyArtsLogic.vollTrainingLeft(data) + " training left, not 1");

        data.setQuincy(data.quincy().withTraining(1100));
        QuincyLogic.setReishi(data, 40.0F);
        Player player = ZanpakutoTests.mock(helper, data);
        helper.assertTrue(QuincyArtsEvents.startVollstandig(player, data, now, false), "Vollständig did not start");
        assertNear(helper, QuincyLogic.reishi(data), 20.0, "the reishi left after taking Vollständig");
        helper.assertTrue(player.mayFly(), "Vollständig gave no flight");
        helper.assertTrue(hasVollFlight(player), "Vollständig's flight is not on the creative flight attribute");
        helper.assertTrue((QuincyArtsLogic.quincyLook(data) & QuincyArtsLogic.LOOK_VOLL) != 0, "Vollständig does not show");

        QuincyArtsEvents.endVollstandig(player, data, now, true);
        helper.assertFalse(player.mayFly(), "the flight outlasted Vollständig");
        long readyAt = QuincyArtsLogic.arts(data).forms().vollReadyAt();
        helper.assertTrue(readyAt == now + 600L, "Vollständig is ready again at " + readyAt + ", not " + (now + 600L));
        assertKey(helper, QuincyArtsLogic.vollRefusal(data, now), "message.bleach.vollstandig.cooldown",
                "Vollständig straight after it ended");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vollstandigEndsWhenDryOrPoisoned(GameTestHelper helper) {
        SoulData data = restored(thirdTier());
        Player player = ZanpakutoTests.mock(helper, data);
        helper.assertTrue(QuincyArtsEvents.startVollstandig(player, data, 980L, true), "a forced Vollständig did not start");
        QuincyLogic.setReishi(data, 0.5F);
        // Vollständig is paid for on each whole second.
        QuincyArtsEvents.tick(player, data, 1000L);
        helper.assertFalse(QuincyArtsLogic.inVollstandig(data), "Vollständig went on with no reishi to pay for it");

        helper.assertTrue(QuincyArtsEvents.startVollstandig(player, data, 1000L, true), "a forced Vollständig did not start again");
        QuincyLogic.setReishi(data, 50.0F);
        data.setQuincy(data.quincy().withPoison(60.0F));
        QuincyArtsEvents.tick(player, data, 1001L);
        helper.assertFalse(QuincyArtsLogic.inVollstandig(data), "Vollständig went on through heavy poison");
        helper.assertFalse(player.mayFly(), "the flight outlasted Vollständig");
        helper.succeed();
    }

    /** Vollständig let go in the air spares the next landing, once. */
    @GameTest(template = "empty")
    public static void landingGraceAfterAirborneEnd(GameTestHelper helper) {
        SoulData data = restored(thirdTier());
        Player player = ZanpakutoTests.mock(helper, data);
        helper.assertTrue(QuincyArtsEvents.startVollstandig(player, data, 1000L, true), "a forced Vollständig did not start");
        player.setOnGround(false);
        QuincyArtsEvents.endVollstandig(player, data, 1000L, true);
        helper.assertTrue(QuincyArtsLogic.arts(data).forms().landingGrace(), "ending Vollständig in the air gave no grace");
        // A soul reset in the air keeps the grace, and only that.
        SoulData fresh = new SoulData();
        QuincyArtsLogic.keepLandingGrace(QuincyArtsLogic.arts(data), fresh);
        helper.assertTrue(QuincyArtsLogic.arts(fresh).equals(QuincyArts.NONE.withForms(QuincyForms.NONE.withLandingGrace(true))),
                "a reset in the air left the arts " + QuincyArtsLogic.arts(fresh));
        float health = player.getHealth();
        player.causeFallDamage(10.0F, 1.0F, player.damageSources().fall());
        helper.assertTrue(player.getHealth() == health, "the spared landing hurt, leaving " + player.getHealth());
        helper.assertFalse(QuincyArtsLogic.arts(data).forms().landingGrace(), "the grace outlasted its landing");
        player.causeFallDamage(10.0F, 1.0F, player.damageSources().fall());
        helper.assertTrue(player.getHealth() < health, "the next landing was spared too");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void haloBreaksOnHeavyHit(GameTestHelper helper) {
        SoulData data = restored(thirdTier());
        Player player = ZanpakutoTests.mock(helper, data);
        helper.assertTrue(QuincyArtsEvents.startVollstandig(player, data, 1000L, true), "a forced Vollständig did not start");
        helper.assertTrue(QuincyArtsEvents.setSklaverei(player, data, true), "Sklaverei did not start");
        player.setHealth(20.0F);
        player.hurt(player.damageSources().generic(), 4.0F);
        helper.assertFalse(QuincyArtsLogic.arts(data).forms().haloBroken(), "a light blow broke the halo");
        // Otherwise the second blow lands only its excess over the first, and the halo holds.
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), 7.0F);
        QuincyForms forms = QuincyArtsLogic.arts(data).forms();
        helper.assertTrue(forms.haloBroken(), "a blow of 7 did not break the halo");
        helper.assertFalse(forms.sklaverei(), "Sklaverei outlasted the halo");
        helper.assertFalse(QuincyArtsEvents.setSklaverei(player, data, true), "Sklaverei came back with the halo broken");
        helper.succeed();
    }

    /**
     * One pulse of Sklaverei draws in what is near: it hurts a Hollow (its reishi poisoning a little)
     * and takes in someone else's Heilig Pfeil, but leaves a Seele Schneider, which is a thing.
     */
    @GameTest(template = "field", batch = "quincy_arts", timeoutTicks = 60)
    public static void sklavereiPulse(GameTestHelper helper) {
        SoulData data = restored(thirdTier());
        Player player = QuincyTests.archer(helper, data, 8.5, 8.5);
        SpiderHollow hollow = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 11));
        Zombie archer = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 4));
        Vec3 east = helper.absoluteVec(EAST).subtract(helper.absoluteVec(Vec3.ZERO));
        HeiligPfeil arrow = HeiligPfeil.launch(helper.getLevel(), archer, helper.absoluteVec(new Vec3(9.5, 3.5, 8.5)), east,
                1.0F, 1.0F, 0, 0.01F, 0.0F);
        SeeleSchneiderPfeil blade = new SeeleSchneiderPfeil(ModEntities.SEELE_SCHNEIDER.get(), helper.getLevel());
        blade.setOwner(archer);
        Vec3 bladeAt = helper.absoluteVec(new Vec3(7.5, 3.5, 8.5));
        blade.moveTo(bladeAt.x, bladeAt.y, bladeAt.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(blade);
        helper.assertTrue(QuincyArtsEvents.startVollstandig(player, data, 1000L, true), "a forced Vollständig did not start");
        helper.assertTrue(QuincyArtsEvents.setSklaverei(player, data, true), "Sklaverei did not start");
        QuincyLogic.setReishi(data, 50.0F);
        helper.runAtTickTime(3, () -> {
            helper.assertFalse(arrow.isRemoved() || blade.isRemoved(), "the zombie's shots were gone before the pulse");
            float health = hollow.getHealth();
            float reishi = QuincyLogic.reishi(data);
            float poison = data.quincy().poison();
            // A pulse every half second, and not on a whole second, when Vollständig is paid for.
            QuincyArtsEvents.tick(player, data, 1010L);
            float gained = QuincyLogic.reishi(data) - reishi;
            helper.assertTrue(hollow.getHealth() < health, "Sklaverei did not hurt the Hollow");
            helper.assertTrue(gained > 0.0F && gained <= (float) BleachConfig.get(BleachConfig.SKLAVEREI_MAX_GAIN_PER_PULSE),
                    "one pulse drew in " + gained + " reishi");
            helper.assertTrue(data.quincy().poison() > poison, "the Hollow's reishi did not poison");
            helper.assertTrue(arrow.isRemoved(), "Sklaverei did not take in the zombie's Heilig Pfeil");
            helper.assertFalse(blade.isRemoved(), "Sklaverei took in a Seele Schneider");
            discardAll(List.of(hollow, archer, blade));
            arrow.discard();
            helper.succeed();
        });
    }

    /** A saved Vollständig whose flight did not survive the relog ends quietly on the first tick. */
    @GameTest(template = "empty")
    public static void loginEndsVollstandigQuietly(GameTestHelper helper) {
        SoulData data = voll(restored(thirdTier()));
        Player player = ZanpakutoTests.mock(helper, data);
        QuincyArtsEvents.tick(player, data, 1000L);
        helper.assertFalse(QuincyArtsLogic.inVollstandig(data), "a Vollständig with no flight went on");
        long readyAt = QuincyArtsLogic.arts(data).forms().vollReadyAt();
        helper.assertTrue(readyAt == 1600L, "the ended Vollständig is ready again at " + readyAt + ", not 1600");
        helper.assertFalse(player.mayFly(), "the ended Vollständig left flight");
        helper.succeed();
    }
}
