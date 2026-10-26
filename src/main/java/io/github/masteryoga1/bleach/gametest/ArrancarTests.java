package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.ArrancarEvents;
import io.github.masteryoga1.bleach.soul.ArrancarLogic;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowProgress;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.MenosBody;
import io.github.masteryoga1.bleach.soul.MenosEvolution;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ResurreccionEvents;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulEvents;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Arrancar: the saved record of its road, a Menos tearing at its own mask and what a failure
 * costs, the soul a torn mask makes and the blade it holds, what a sealed Arrancar keeps of the Hollow and
 * loses, its Resurrección and how that ends, its blades crumbling and forming again, and its Cero. Mock
 * players are plain {@link Player}s that are not in the world and never tick, so every test calls the
 * rules directly: {@link HollowBody#tickBody}, {@link ArrancarEvents#pressTear}, {@link ArrancarEvents#tickTear},
 * {@link ArrancarEvents#finishTear}, {@link ArrancarEvents#become} and {@link ArrancarEvents#reconcileBlades},
 * {@link ResurreccionEvents#release} and {@link ResurreccionEvents#seal}, and the rules in
 * {@link ArrancarLogic} and {@link HollowCero}. The game time stands still within a test, so a later time
 * passed in stands for a later tick. Players stand at {@link #ROOM} in the {@code empty} template and at
 * {@link #SPOT} in the {@code field}, facing south.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ArrancarTests {
    /** Where a player stands in the {@code empty} template. */
    private static final Vec3 ROOM = new Vec3(1.5, 1.0, 1.5);
    /** Where a player stands in the field: in the middle of block (8, 2, 6), facing south. */
    private static final Vec3 SPOT = new Vec3(8.5, 2.0, 6.5);
    /** A fed Hollow's soul hunger, so neither ravenous claws nor slower healing change the numbers. */
    private static final float FED = 60.0F;
    /** How far twenty ticks of a release's drain may stray from twenty times one tick's, as floats add up. */
    private static final double DRAIN_EPSILON = 0.01;

    private ArrancarTests() {
    }

    // ---- Helpers ----

    /** A mock Hollow in survival of this form, with this much reiryoku and fed, its record begun at that form. */
    private static Player menos(GameTestHelper helper, HollowTier form, float maxReiryoku, Vec3 at) {
        Player player = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, maxReiryoku, at);
        SoulData data = SoulLogic.get(player);
        data.setTier(PowerPath.HOLLOW, form.tier());
        data.setHollow(data.hollow().withEvolution(data.hollow().evolution().startedAt(form.tier())));
        HollowBody.setSatiety(data, FED);
        return player;
    }

    /**
     * A mock Arrancar in survival risen from this form, made from a living Human, with its blade in the main
     * hand. The maximum reiryoku is put back to the one asked for after becoming, so the one-time growth does
     * not change the numbers, and the reiryoku is full.
     */
    private static Player arrancar(GameTestHelper helper, HollowTier origin, float maxReiryoku, Vec3 at) {
        Player player = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, maxReiryoku, at);
        SoulData data = SoulLogic.get(player);
        ArrancarEvents.become(player, data, origin, false);
        data.setMaxReiryoku(maxReiryoku);
        data.setReiryoku(maxReiryoku);
        return player;
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

    /** The player's own blade of its record's current forging, wherever it is in the inventory, or an empty stack. */
    private static ItemStack ownBlade(Player player) {
        int forging = SoulLogic.get(player).hollow().arrancar().forging();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ArrancarLogic.isOwnBlade(stack, player) && ArrancarLogic.forging(stack) == forging) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** How many Arrancar blades forged for someone the player carries, of any forging and anyone's. */
    private static int bladesHeld(Player player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ArrancarLogic.isArrancarBlade(stack) && ZanpakutoItem.isBound(stack)) {
                count++;
            }
        }
        return count;
    }

    /** Moves what the main hand holds into this inventory slot, leaving the hand bare. */
    private static void handToSlot(Player player, int slot) {
        ItemStack held = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(slot, held);
    }

    /** Moves what this inventory slot holds into the main hand. */
    private static void slotToHand(Player player, int slot) {
        ItemStack held = player.getInventory().getItem(slot);
        player.getInventory().setItem(slot, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
    }

    /** The attack damage a blade's own main-hand modifier adds, or NaN when it has none. */
    private static double bladeDamage(ItemStack stack) {
        ItemAttributeModifiers modifiers =
                stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.matches(Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID)
                    && entry.slot() == EquipmentSlotGroup.MAINHAND) {
                return entry.modifier().amount();
            }
        }
        return Double.NaN;
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

    /** Takes away every Cero beam in the structure that this player gathers or fired. */
    private static void discardBeams(GameTestHelper helper, Player player) {
        helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds(), beam -> beam.owner() == player)
                .forEach(Entity::discard);
    }

    private static void assertTearRefusal(GameTestHelper helper, SoulData data, long now,
                                          @Nullable ArrancarLogic.TearRefusal expected, String what) {
        ArrancarLogic.TearRefusal refusal = ArrancarLogic.tearRefusal(data, now);
        helper.assertTrue(refusal == expected, what + " may not tear at its mask for " + refusal + ", not " + expected);
    }

    private static void assertRelease(GameTestHelper helper, Player player, SoulData data, long now,
                                      ResurreccionEvents.Result expected, String what) {
        ResurreccionEvents.Result result = ResurreccionEvents.release(player, data, now);
        helper.assertTrue(result == expected, "a release with " + what + " gave " + result + ", not " + expected);
    }

    private static void assertWithin(GameTestHelper helper, double actual, double expected, double epsilon, String what) {
        helper.assertTrue(Math.abs(actual - expected) <= epsilon, what + " is " + actual + ", not " + expected);
    }

    // ---- The record ----

    @GameTest(template = "empty")
    public static void arrancarRecordSavesAndOldSavesLoad(GameTestHelper helper) {
        HollowProgress.Evolution evolution = new HollowProgress.Evolution(4, 4, 0, 0, 0, 0, 0);
        HollowProgress.Arrancar arrancar = new HollowProgress.Arrancar(true, 2, 1234L, 5678L, 3);
        HollowProgress progress = new HollowProgress(42.5F, 17, 3, Map.of(HollowKind.SPIDER, 2), 1, true, 1234L, false,
                evolution, arrancar);
        Tag saved = HollowProgress.CODEC.encodeStart(NbtOps.INSTANCE, progress).getOrThrow();
        HollowProgress loaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.equals(progress), "the record with its Arrancar came back as " + loaded);
        ByteBuf buffer = Unpooled.buffer();
        try {
            HollowProgress.STREAM_CODEC.encode(buffer, progress);
            HollowProgress received = HollowProgress.STREAM_CODEC.decode(buffer);
            helper.assertTrue(received.equals(progress), "the record came through the network as " + received);
            helper.assertTrue(buffer.readableBytes() == 0, "the record left " + buffer.readableBytes() + " bytes");
        } finally {
            buffer.release();
        }

        // A save from before the Arrancar has only the keys it had then; a bad record falls back on its own.
        helper.assertTrue(saved instanceof CompoundTag tag && tag.contains("arrancar"),
                "the record was saved without its Arrancar: " + saved);
        HollowProgress none = progress.withArrancar(HollowProgress.Arrancar.NONE);
        CompoundTag old = ((CompoundTag) saved).copy();
        old.remove("arrancar");
        HollowProgress oldLoaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(oldLoaded.equals(none), "a save from before the Arrancar read as " + oldLoaded);
        CompoundTag broken = ((CompoundTag) saved).copy();
        broken.putString("arrancar", "x");
        HollowProgress brokenLoaded = HollowProgress.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
        helper.assertTrue(brokenLoaded.equals(none), "a record whose Arrancar is a string read as " + brokenLoaded);
        HollowProgress eightFields = new HollowProgress(42.5F, 17, 3, Map.of(HollowKind.SPIDER, 2), 1, true, 1234L, false);
        HollowProgress nineFields = new HollowProgress(42.5F, 17, 3, Map.of(HollowKind.SPIDER, 2), 1, true, 1234L, false,
                evolution);
        helper.assertTrue(eightFields.arrancar().equals(HollowProgress.Arrancar.NONE)
                && nineFields.arrancar().equals(HollowProgress.Arrancar.NONE),
                "the records built without an Arrancar have " + eightFields.arrancar() + " and " + nineFields.arrancar());
        helper.assertTrue(progress.restarted().arrancar().equals(arrancar),
                "a restarted record's Arrancar is " + progress.restarted().arrancar());

        HollowProgress.Arrancar clamped = new HollowProgress.Arrancar(false, -3, 0L, 0L, -1);
        helper.assertTrue(clamped.failures() == 0 && clamped.forging() == 0 && !clamped.forged(),
                "an Arrancar record out of range kept " + clamped);
        HollowProgress.Arrancar first = HollowProgress.Arrancar.NONE.becoming(true);
        helper.assertTrue(first.perfected() && first.forging() == 1 && first.forged()
                && first.releaseReadyAt() == HollowProgress.NEVER, "a first becoming by the Hōgyoku made " + first);
        HollowProgress.Arrancar again = arrancar.becoming(false);
        helper.assertTrue(again.equals(new HollowProgress.Arrancar(true, 2, 1234L, HollowProgress.NEVER, 4)),
                "becoming again by tearing made " + again);
        for (String name : List.of("arrancar", "resurreccion")) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id(name)) != null,
                    "advancement bleach:" + name + " did not load");
        }
        helper.succeed();
    }

    // ---- Tearing the mask ----

    @GameTest(template = "empty")
    public static void tearRefusalsComeInOrder(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player plain = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 400.0F, ROOM);
        assertTearRefusal(helper, SoulLogic.get(plain), now, ArrancarLogic.TearRefusal.NOT_MENOS, "a plain Hollow");
        Player human = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 400.0F, ROOM);
        assertTearRefusal(helper, SoulLogic.get(human), now, ArrancarLogic.TearRefusal.NOT_MENOS, "a living Human");
        Player gillian = menos(helper, HollowTier.GILLIAN, 400.0F, ROOM);
        assertTearRefusal(helper, SoulLogic.get(gillian), now, ArrancarLogic.TearRefusal.MINDLESS, "a Gillian");

        Player adjuchas = menos(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(adjuchas);
        // Set for this one call, in this one tick, and put back before any other test runs.
        boolean natural = BleachConfig.get(BleachConfig.NATURAL_ARRANCAR);
        BleachConfig.NATURAL_ARRANCAR.set(false);
        try {
            assertTearRefusal(helper, data, now, ArrancarLogic.TearRefusal.OFF, "an Adjuchas on a server that forbids it");
        } finally {
            BleachConfig.NATURAL_ARRANCAR.set(natural);
        }

        HollowProgress.Arrancar record = data.hollow().arrancar();
        data.setHollow(data.hollow().withArrancar(record.withLastTearAt(now - 100L)));
        assertTearRefusal(helper, data, now, ArrancarLogic.TearRefusal.WAITING, "an Adjuchas that tried 5 seconds ago");
        helper.assertTrue(ArrancarLogic.minutesToRetry(data, now) == 20,
                "an Adjuchas that tried 5 seconds ago waits " + ArrancarLogic.minutesToRetry(data, now) + " minutes");
        data.setHollow(data.hollow().withArrancar(record));

        HollowBody.onHurt(data, now - 20L);
        assertTearRefusal(helper, data, now, ArrancarLogic.TearRefusal.FIGHTING, "an Adjuchas hurt a second ago");
        helper.assertTrue(ArrancarLogic.secondsToCalm(data, now) == 29,
                "an Adjuchas hurt a second ago waits " + ArrancarLogic.secondsToCalm(data, now) + " seconds");
        HollowBody.onHurt(data, HollowProgress.NEVER);

        data.setReiryoku(0.89F * 400.0F);
        assertTearRefusal(helper, data, now, ArrancarLogic.TearRefusal.TOO_WEAK, "an Adjuchas at 89% of its reiryoku");
        data.setReiryoku(400.0F);

        HollowCero.Refusal cero = HollowCero.start(adjuchas, data, now);
        try {
            helper.assertTrue(cero == null && HollowCero.charging(data), "the Adjuchas's Cero did not gather: " + cero);
            assertTearRefusal(helper, data, now, ArrancarLogic.TearRefusal.BUSY, "an Adjuchas gathering a Cero");
        } finally {
            HollowCero.cancel(adjuchas, data);
            discardBeams(helper, adjuchas);
        }
        helper.assertFalse(HollowCero.charging(data), "the cancelled Cero is still gathering");

        Player fresh = menos(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        assertTearRefusal(helper, SoulLogic.get(fresh), now, null, "a fresh, full Adjuchas");
        Player vasto = menos(helper, HollowTier.VASTO_LORDE, 400.0F, ROOM);
        assertTearRefusal(helper, SoulLogic.get(vasto), now, null, "a fresh, full Vasto Lorde");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tearArmsChannelsAndBreaksForFree(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);

        // The first press arms the tear, and the second begins it, at no cost yet.
        ArrancarLogic.TearRefusal first = ArrancarEvents.pressTear(player, data, now);
        helper.assertTrue(first == null && ArrancarEvents.tearArmed(data, now) && !ArrancarEvents.tearing(data),
                "the first press gave " + first + " and did not only arm the tear");
        ArrancarLogic.TearRefusal second = ArrancarEvents.pressTear(player, data, now);
        helper.assertTrue(second == null && ArrancarEvents.tearing(data), "the second press gave " + second
                + " and did not begin the tear");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku once the tear began");

        // A blow that lands after it began breaks the grip, for free.
        HollowBody.onHurt(data, now + 1L);
        helper.assertFalse(ArrancarEvents.tickTear(player, data, now + 1L), "a tear broken by a blow made an Arrancar");
        helper.assertFalse(ArrancarEvents.tearing(data), "a blow did not break the tear");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after a broken tear");
        helper.assertTrue(data.hollow().arrancar().failures() == 0, "a broken tear counted as a failure");
        // The only way to forget the blow, so the next presses are not refused as fighting.
        HollowBody.onHurt(data, HollowProgress.NEVER);

        // Seal lets go of a tear under way, and of nothing the second time.
        ArrancarEvents.pressTear(player, data, now + 2L);
        ArrancarEvents.pressTear(player, data, now + 2L);
        helper.assertTrue(ArrancarEvents.tearing(data), "two presses did not begin the tear again");
        helper.assertTrue(ArrancarEvents.cancelTear(player, data) && !ArrancarEvents.tearing(data),
                "Seal did not let go of the tear");
        helper.assertFalse(ArrancarEvents.cancelTear(player, data), "Seal let go of a tear twice");

        // Held still, the mask cracks and the soul is held down every second until the channel is done.
        ArrancarEvents.pressTear(player, data, now + 2L);
        ArrancarEvents.pressTear(player, data, now + 2L);
        helper.assertFalse(ArrancarEvents.tickTear(player, data, now + 22L), "a tear 20 ticks in made an Arrancar");
        helper.assertTrue(ArrancarEvents.tearing(data), "a still tear stopped 20 ticks in");
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now + 22L) & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0,
                "the mask torn at is not drawn cracked");
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slowness != null && slowness.getAmplifier() == 3, "the tear's slowness is " + slowness);
        helper.assertFalse(ArrancarEvents.tickTear(player, data, now + 101L), "a tear 99 ticks in made an Arrancar");
        helper.assertTrue(ArrancarEvents.tearing(data) && HollowPlayerLogic.isHollow(data),
                "a tear 99 ticks in ended, or left a " + data.state());

        // Swaying in place keeps the grip; a step of more than a block breaks it, for free. Both stay in the box.
        Vec3 from = player.position();
        player.moveTo(from.x + 0.5, from.y, from.z, player.getYRot(), player.getXRot());
        ArrancarEvents.tickTear(player, data, now + 101L);
        helper.assertTrue(ArrancarEvents.tearing(data), "half a block of sway broke the tear");
        player.moveTo(from.x + 1.25, from.y, from.z, player.getYRot(), player.getXRot());
        helper.assertFalse(ArrancarEvents.tickTear(player, data, now + 101L), "a tear broken by a step made an Arrancar");
        helper.assertFalse(ArrancarEvents.tearing(data), "a step of a block and a quarter did not break the tear");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after a tear broken by a step");
        helper.assertTrue(data.hollow().arrancar().failures() == 0, "a tear broken by a step counted as a failure");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFailedTearCostsAndCounts(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);
        tickBody(helper, player, 1);
        HollowPlayerTests.assertClose(helper, player.getMaxHealth(), 60.0, "the Adjuchas's maximum health");
        player.setHealth(30.0F);

        helper.assertFalse(ArrancarEvents.finishTear(player, data, now, 0.99), "a roll of 0.99 tore an Adjuchas's mask away");
        helper.assertTrue(HollowPlayerLogic.isHollow(data) && HollowTier.of(data) == HollowTier.ADJUCHAS,
                "a failed tear left a " + data.state() + " of form " + HollowTier.of(data));
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 200.0, "the reiryoku after a failed tear");
        HollowPlayerTests.assertClose(helper, player.getHealth(), 1.0, "the health after a failed tear at 30 of 60");
        HollowProgress.Arrancar record = data.hollow().arrancar();
        helper.assertTrue(record.failures() == 1 && record.lastTearAt() == now, "a failed tear left the record " + record);
        helper.assertTrue((HollowPlayerLogic.hollowLook(data, now) & HollowPlayerLogic.LOOK_MASK_CRACKED) != 0,
                "the mask is not drawn cracked after a failed tear");
        HollowPlayerTests.assertClose(helper, HollowBody.regenerate(player, data, now + 20L, 1.0F), 0.0,
                "the health regenerated a second after a failed tear");

        HollowPlayerTests.assertClose(helper, ArrancarLogic.tearChance(HollowTier.ADJUCHAS, 1), 0.20,
                "an Adjuchas's chance after a failure");
        HollowPlayerTests.assertClose(helper, ArrancarLogic.tearChance(HollowTier.VASTO_LORDE, 2), 1.0,
                "a Vasto Lorde's chance after two failures");
        HollowPlayerTests.assertClose(helper, ArrancarLogic.tearChance(HollowTier.GILLIAN, 5), 0.0,
                "a Gillian's chance after five failures");
        ArrancarLogic.TearRefusal refusal = ArrancarEvents.pressTear(player, data, now + 1L);
        helper.assertTrue(refusal == ArrancarLogic.TearRefusal.WAITING, "a press just after a failure gave " + refusal);

        // Once the pause is over it heals again, so the nothing above was the pause's doing.
        if (helper.getLevel().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            long pause = BleachConfig.get(BleachConfig.TEAR_FAILURE_PAUSE_SECONDS) * 20L;
            helper.assertTrue(HollowBody.regenerate(player, data, now + pause, 1.0F) > 0.0F,
                    "the Adjuchas did not heal once the failure's pause was over");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTornMaskMakesAnArrancar(GameTestHelper helper) {
        Player player = menos(helper, HollowTier.VASTO_LORDE, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);
        tickBody(helper, player, 1);
        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) != null
                && HollowPlayerTests.modifier(player, Attributes.ARMOR, HollowBody.HIDE_ID) != null,
                "the bare-handed Vasto Lorde has no claws or no hide");
        HollowPlayerTests.assertModifier(helper, player, Attributes.MAX_HEALTH, MenosBody.HEALTH_ID, 60.0,
                AttributeModifier.Operation.ADD_VALUE, "the Vasto Lorde's health");

        helper.assertTrue(ArrancarEvents.finishTear(player, data, now, 0.0), "a roll of 0 did not tear the mask away");
        helper.assertTrue(data.state() == SoulState.ARRANCAR && data.tier(PowerPath.HOLLOW) == 4
                && ArrancarLogic.origin(data) == HollowTier.VASTO_LORDE,
                "the torn mask made a " + data.state() + " of Hollow tier " + data.tier(PowerPath.HOLLOW));
        HollowProgress.Arrancar record = data.hollow().arrancar();
        helper.assertTrue(record.forging() == 1 && !record.perfected(), "the new Arrancar's record is " + record);
        // 400 less the tear's 200, then a quarter more of the maximum, once, and full.
        HollowPlayerTests.assertClose(helper, data.maxReiryoku(), 500.0, "the new Arrancar's maximum reiryoku");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 500.0, "the new Arrancar's reiryoku");

        ItemStack blade = player.getMainHandItem();
        helper.assertTrue(ArrancarLogic.isOwnBlade(blade, player) && ArrancarLogic.forging(blade) == 1,
                "the bare hand does not hold the new Arrancar's blade: " + blade);
        Component name = blade.get(DataComponents.ITEM_NAME);
        helper.assertTrue(ArrancarLogic.blade(player.getUUID()).displayName().equals(name), "the blade is named " + name);
        // A Vasto Lorde-born blade deals 8: the player's own 1, and 7 from the blade.
        HollowPlayerTests.assertClose(helper, bladeDamage(blade), 7.0, "the blade's attack damage modifier");

        helper.assertTrue(HollowPlayerTests.bodyOff(player) && MenosPlayerTests.menosOff(player),
                "the Hollow's or the Menos's body stayed on the Arrancar");
        HollowPlayerTests.assertModifier(helper, player, Attributes.MAX_HEALTH, ArrancarLogic.HEALTH_ID, 20.0,
                AttributeModifier.Operation.ADD_VALUE, "the sealed Vasto Lorde-born Arrancar's health");
        helper.assertTrue(savedWith(player, Attributes.MAX_HEALTH, ArrancarLogic.HEALTH_ID),
                "the Arrancar's health is not saved with the player");
        HollowPlayerTests.assertClose(helper, player.getMaxHealth(), 40.0, "the new Arrancar's maximum health");
        HollowPlayerTests.assertClose(helper, player.getHealth(), 40.0, "the new Arrancar's health");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "the new Arrancar's food is "
                + player.getFoodData().getFoodLevel());
        helper.assertTrue(HollowBody.satiety(data) == HollowProgress.UNSET, "the new Arrancar still has soul hunger: "
                + HollowBody.satiety(data));

        // The Hōgyoku's road, for a later round: any soul, perfected, at least once.
        Player human = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 400.0F, ROOM);
        SoulData perfected = SoulLogic.get(human);
        ArrancarEvents.become(human, perfected, HollowTier.ADJUCHAS, true);
        helper.assertTrue(perfected.state() == SoulState.ARRANCAR && perfected.tier(PowerPath.HOLLOW) == 3
                && perfected.hollow().arrancar().perfected(),
                "the Hōgyoku made a " + perfected.state() + " with the record " + perfected.hollow().arrancar());
        HollowPlayerTests.assertClose(helper, perfected.maxReiryoku(), 460.0, "a perfected Adjuchas-born maximum reiryoku");

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(ModItems.ARRANCAR_BLADE.get());
        helper.assertTrue(key.equals(BleachMod.id("arrancar_blade")), "the Arrancar's blade is registered as " + key);
        helper.succeed();
    }

    // ---- The sealed Arrancar ----

    @GameTest(template = "field")
    public static void sealedArrancarKeepsAndLoses(GameTestHelper helper) {
        Player player = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);
        handToSlot(player, 1);
        tickBody(helper, player, 20);
        player.setHealth(10.0F);

        helper.assertTrue(HollowPlayerTests.bodyOff(player), "a sealed Arrancar grew a Hollow's body on a bare hand");
        helper.assertTrue(ArrancarLogic.isOwnBlade(player.getInventory().getItem(1), player),
                "the blade left slot 1: " + player.getInventory().getItem(1));
        HollowPlayerTests.assertClose(helper, HollowBody.regenerate(player, data, now + 200L, 1.0F), 0.0,
                "a sealed Arrancar's Hollow regeneration");
        HollowPlayerTests.assertModifier(helper, player, Attributes.MAX_HEALTH, ArrancarLogic.HEALTH_ID, 10.0,
                AttributeModifier.Operation.ADD_VALUE, "the sealed Adjuchas-born Arrancar's health");
        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.MOVEMENT_SPEED, ArrancarLogic.SPEED_ID) == null
                && HollowPlayerTests.modifier(player, Attributes.ATTACK_DAMAGE, ArrancarLogic.DAMAGE_ID) == null,
                "a sealed Arrancar has its release's speed or damage");

        // It eats and wears armour as a person does, but the living still see a Hollow.
        helper.assertFalse(HollowPlayerLogic.isHollowPlayer(player), "the Arrancar counts as a Hollow player");
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        helper.assertFalse(HollowPlayerLogic.refusesArmour(player, chest, EquipmentSlot.CHEST),
                "a sealed Arrancar's body refuses an iron chestplate");
        Player hollow = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 400.0F, SPOT);
        helper.assertTrue(HollowPlayerLogic.refusesArmour(hollow, chest, EquipmentSlot.CHEST)
                == !BleachConfig.get(BleachConfig.HOLLOW_WEARS_ARMOR), "a Hollow's hide does not follow the server's rule");
        helper.assertTrue(HollowPlayerLogic.shunnedByVillagers(player)
                == !BleachConfig.get(BleachConfig.VILLAGERS_TRADE_WITH_HOLLOWS),
                "villagers do not treat the Arrancar as the server's rule says");

        // A Shinigami tier gives an Arrancar no Shinigami power and no zanpakutō to wield.
        data.setTier(PowerPath.SHINIGAMI, 1);
        helper.assertFalse(ShinigamiLogic.hasShinigamiPower(data), "an Arrancar with a Shinigami tier has Shinigami power");
        helper.assertFalse(ZanpakutoLogic.canWield(data), "an Arrancar with a Shinigami tier can wield a zanpakutō");
        helper.assertTrue(SoulEvents.mayRegenerate(player, data), "a sealed Arrancar's reiryoku does not refill");
        helper.succeed();
    }

    // ---- Resurrección ----

    @GameTest(template = "empty")
    public static void releaseChecksComeInOrder(GameTestHelper helper) {
        Player player = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);
        ItemStack own = player.getMainHandItem();
        helper.assertTrue(ArrancarLogic.isOwnBlade(own, player), "the Arrancar does not hold its blade: " + own);

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.NO_BLADE, "a bare hand");
        Player other = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        player.setItemInHand(InteractionHand.MAIN_HAND, other.getMainHandItem().copy());
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.NO_BLADE, "another Arrancar's blade");

        player.setItemInHand(InteractionHand.MAIN_HAND, own);
        HollowProgress.Arrancar record = data.hollow().arrancar();
        data.setHollow(data.hollow().withArrancar(record.withReleaseReadyAt(now + 100L)));
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.COOLDOWN, "5 seconds of the wait left");
        data.setHollow(data.hollow().withArrancar(record.withReleaseReadyAt(HollowProgress.NEVER)));
        data.setReiryoku(199.0F);
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.TOO_WEAK, "199 of 400 reiryoku");
        data.setReiryoku(400.0F);
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.RELEASED, "its own blade at full reiryoku");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 340.0, "the reiryoku after a release at 400");
        helper.assertTrue(ArrancarLogic.released(data), "the released Arrancar is not released");
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.ALREADY, "a release already on");

        ResurreccionEvents.Result sealed = ResurreccionEvents.seal(player, data, now);
        helper.assertTrue(sealed == ResurreccionEvents.Result.SEALED && !ArrancarLogic.released(data),
                "sealing gave " + sealed);
        ResurreccionEvents.Result twice = ResurreccionEvents.seal(player, data, now);
        helper.assertTrue(twice == ResurreccionEvents.Result.NOT_RELEASED, "sealing again gave " + twice);

        // To a Shinigami, an Arrancar's blade is no zanpakutō.
        Player shinigami = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 400.0F, ROOM);
        shinigami.setItemInHand(InteractionHand.MAIN_HAND, own.copy());
        ShikaiEvents.Result shikai = ShikaiEvents.release(shinigami);
        helper.assertTrue(shikai == ShikaiEvents.Result.NO_BLADE, "a Shinigami releasing an Arrancar's blade gave " + shikai);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseFreesTheHollowForm(GameTestHelper helper) {
        Player player = arrancar(helper, HollowTier.VASTO_LORDE, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);
        player.setHealth(10.0F);
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.RELEASED, "its own blade");
        handToSlot(player, 1);
        tickBody(helper, player, 1);

        // The release's health went on before the heal, which filled it.
        HollowPlayerTests.assertClose(helper, player.getMaxHealth(), 80.0, "the released Arrancar's maximum health");
        HollowPlayerTests.assertClose(helper, player.getHealth(), 80.0, "the released Arrancar's health");
        HollowPlayerTests.assertModifier(helper, player, Attributes.MAX_HEALTH, ArrancarLogic.HEALTH_ID, 60.0,
                AttributeModifier.Operation.ADD_VALUE, "the released Vasto Lorde-born Arrancar's health");
        HollowPlayerTests.assertModifier(helper, player, Attributes.MOVEMENT_SPEED, ArrancarLogic.SPEED_ID, 0.35,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, "the released Arrancar's speed");
        HollowPlayerTests.assertModifier(helper, player, Attributes.ATTACK_DAMAGE, ArrancarLogic.DAMAGE_ID, 0.5,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "the released Arrancar's melee damage");

        // A Vasto Lorde's claws and hide: 3 and a quarter of the root of 400, times 1.6; the 5a hide and 10 more.
        float claws = HollowPlayerLogic.clawBonus(data);
        HollowPlayerTests.assertClose(helper, claws, 12.8, "the released Vasto Lorde-born Arrancar's claws");
        HollowPlayerTests.assertModifier(helper, player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID, claws,
                AttributeModifier.Operation.ADD_VALUE, "the claws on the released Arrancar's bare hand");
        HollowPlayerTests.assertModifier(helper, player, Attributes.ATTACK_SPEED, HollowBody.CLAW_SPEED_ID,
                HollowTier.VASTO_LORDE.clawAttacksPerSecond() / 4.0 - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                "the released Arrancar's claw speed");
        float plainHide = HollowPlayerLogic.hideArmour(ShinigamiTests.soul(SoulState.HOLLOW, 400.0F));
        HollowPlayerTests.assertModifier(helper, player, Attributes.ARMOR, HollowBody.HIDE_ID,
                plainHide + HollowTier.VASTO_LORDE.hideBonus(), AttributeModifier.Operation.ADD_VALUE,
                "the released Arrancar's hide");
        HollowPlayerTests.assertModifier(helper, player, Attributes.SAFE_FALL_DISTANCE, HollowBody.LEAP_ID,
                BleachConfig.get(BleachConfig.HOLLOW_SAFE_FALL), AttributeModifier.Operation.ADD_VALUE,
                "the released Arrancar's leap");

        // Drawn released, with a Vasto Lorde's bone, and never as a Menos body.
        int look = HollowPlayerLogic.hollowLook(data, now);
        int released = HollowPlayerLogic.LOOK_RESURRECCION | HollowPlayerLogic.LOOK_VASTO_LORDE;
        helper.assertTrue((look & released) == released
                && (look & (HollowPlayerLogic.LOOK_GILLIAN | HollowPlayerLogic.LOOK_ADJUCHAS)) == 0,
                "the released Vasto Lorde-born Arrancar's look is " + look);

        // Neither reiryoku nor a Hollow's healing comes back while released.
        helper.assertFalse(SoulEvents.mayRegenerate(player, data), "a released Arrancar's reiryoku refills");
        player.setHealth(40.0F);
        HollowPlayerTests.assertClose(helper, HollowBody.regenerate(player, data, now + 200L, 1.0F), 0.0,
                "a released Arrancar's Hollow regeneration");

        // A Gillian-born Arrancar takes back a plain Hollow's body, not a giant's.
        Player gillianBorn = arrancar(helper, HollowTier.GILLIAN, 400.0F, ROOM);
        SoulData gillianData = SoulLogic.get(gillianBorn);
        assertRelease(helper, gillianBorn, gillianData, now, ResurreccionEvents.Result.RELEASED, "a Gillian-born blade");
        helper.assertTrue(HollowPlayerLogic.bodyForm(gillianData) == HollowTier.HOLLOW,
                "a released Gillian-born Arrancar has the body of a " + HollowPlayerLogic.bodyForm(gillianData));
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.clawSpeedModifier(gillianData), -0.5,
                "a released Gillian-born Arrancar's claw speed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseDrainsEndsAndWaits(GameTestHelper helper) {
        Player player = arrancar(helper, HollowTier.ADJUCHAS, 1000.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        long now = HollowPlayerTests.now(helper);
        long cooldown = BleachConfig.get(BleachConfig.RESURRECCION_COOLDOWN_SECONDS) * 20L;
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.RELEASED, "its own blade at 1000");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 850.0, "the reiryoku after a release at 1000");

        // 0.6% of 1000 a second, and a fifth less at Reiatsu Control 10.
        HollowPlayerTests.assertClose(helper, ArrancarLogic.releaseDrainPerTick(data), 0.3, "the drain a tick at 1000");
        data.setLevel(Stat.REIATSU_CONTROL, 10);
        float drain = ArrancarLogic.releaseDrainPerTick(data);
        HollowPlayerTests.assertClose(helper, drain, 0.24, "the drain a tick at Reiatsu Control 10");

        // Bare-handed, so the claws grow while it lasts and can be seen to go when it ends.
        handToSlot(player, 1);
        float before = data.reiryoku();
        tickBody(helper, player, 20);
        assertWithin(helper, before - data.reiryoku(), 20.0 * drain, DRAIN_EPSILON, "the reiryoku twenty ticks drained");
        helper.assertTrue(ArrancarLogic.released(data), "the release ended within a second");
        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) != null,
                "no claws grew on the released Arrancar's bare hand");

        // Below 5% of the maximum it seals itself, and the wait begins.
        data.setReiryoku(50.1F);
        tickBody(helper, player, 1);
        helper.assertFalse(ArrancarLogic.released(data), "the release outlasted its reiryoku");
        helper.assertTrue(data.hollow().arrancar().releaseReadyAt() == now + cooldown,
                "the next release may come at " + data.hollow().arrancar().releaseReadyAt() + ", not " + (now + cooldown));
        HollowPlayerTests.assertModifier(helper, player, Attributes.MAX_HEALTH, ArrancarLogic.HEALTH_ID, 10.0,
                AttributeModifier.Operation.ADD_VALUE, "the sealed health after the release ran dry");
        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.ATTACK_DAMAGE, HollowBody.CLAWS_ID) == null,
                "the claws stayed on after the release ran dry");

        // Released again with the wait cleared by hand, death ends it, and the wait outlives the death and a save.
        data.setHollow(data.hollow().withArrancar(data.hollow().arrancar().withReleaseReadyAt(HollowProgress.NEVER)));
        data.setReiryoku(1000.0F);
        slotToHand(player, 1);
        assertRelease(helper, player, data, now, ResurreccionEvents.Result.RELEASED, "its own blade again");
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        helper.assertFalse(ArrancarLogic.released(data), "death did not end the release");
        long ready = data.hollow().arrancar().releaseReadyAt();
        helper.assertTrue(ready == now + cooldown, "after a death the next release may come at " + ready);
        SoulData respawned = SoulLogic.afterDeath(data);
        helper.assertTrue(respawned.state() == SoulState.ARRANCAR && !ArrancarLogic.released(respawned)
                && respawned.hollow().arrancar().releaseReadyAt() == ready,
                "the respawned soul is a " + respawned.state() + " with the record " + respawned.hollow().arrancar());
        Tag saved = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        SoulData loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(loaded.hollow().arrancar().equals(data.hollow().arrancar()),
                "the soul's Arrancar record came back as " + loaded.hollow().arrancar());
        helper.succeed();
    }

    // ---- The rules ----

    @GameTest(template = "field")
    public static void arrancarRulesFollowTheState(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);

        // A zanpakutō kill never purifies an Arrancar: the Shinigami counts it as slain. A Hollow it purifies.
        Player killer = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.SHINIGAMI, 40.0F, SPOT);
        HollowTests.arm(killer, ModItems.ASAUCHI.get());
        SoulData killerData = SoulLogic.get(killer);
        Player victim = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(victim, killer.damageSources().playerAttack(killer)));
        helper.assertTrue(killerData.hunt().slain() == 1 && killerData.hunt().purified() == 0,
                "an Asauchi kill of an Arrancar counted " + killerData.hunt().purified() + " purified and "
                        + killerData.hunt().slain() + " slain");
        helper.assertFalse(SoulLogic.get(victim).hollow().purifiedOnDeath(), "the slain Arrancar was marked purified");
        Player hollow = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 400.0F, SPOT);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(hollow, killer.damageSources().playerAttack(killer)));
        helper.assertTrue(killerData.hunt().purified() == 1 && killerData.hunt().slain() == 1,
                "an Asauchi kill of a Hollow counted " + killerData.hunt().purified() + " purified and "
                        + killerData.hunt().slain() + " slain");

        // A Vasto Lorde-born Arrancar keeps its place among the server's Vasto Lorde; an Adjuchas-born one has none.
        MenosEvolution.VastoLordes lordes = MenosEvolution.VastoLordes.get(helper.getLevel().getServer());
        Player vasto = arrancar(helper, HollowTier.VASTO_LORDE, 400.0F, SPOT);
        Player adjuchas = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        try {
            vasto.tickCount = 19;
            tickBody(helper, vasto, 1);
            helper.assertTrue(lordes.contains(vasto.getUUID()), "a Vasto Lorde-born Arrancar gave its place up");
            adjuchas.tickCount = 19;
            tickBody(helper, adjuchas, 1);
            helper.assertFalse(lordes.contains(adjuchas.getUUID()), "an Adjuchas-born Arrancar took a Vasto Lorde's place");
        } finally {
            lordes.remove(vasto.getUUID());
            lordes.remove(adjuchas.getUUID());
        }

        // Its next step, no Garganta for it, Hollows hunt it only as they would a Hollow player, and no Devour.
        Player player = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        SoulData data = SoulLogic.get(player);
        String step = ShinigamiLogic.nextStepKey(data, now);
        helper.assertTrue(step.equals("arrancar"), "an Arrancar's next step is " + step);
        data.setHunt(data.hunt().withFirstHollowDone(true));
        helper.assertTrue(HollowLogic.arrivalChancePerMinute(data, false) == 0.0
                && HollowLogic.arrivalChancePerMinute(data, true) == 0.0, "a Garganta may open for an Arrancar");
        helper.assertTrue(HollowLogic.isPrey(player) == BleachConfig.get(BleachConfig.HUNT_HOLLOW_PLAYERS),
                "Hollows do not hunt an Arrancar as the server's rule for Hollow players says");
        DevourLogic.Refusal devour = DevourLogic.refusal(player, data, null, now);
        helper.assertTrue(devour == DevourLogic.Refusal.NOT_HOLLOW, "an Arrancar's devour gave " + devour);
        helper.succeed();
    }

    // ---- The blade ----

    @GameTest(template = "empty")
    public static void bladesCrumbleAndFormAgain(GameTestHelper helper) {
        Player owner = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(owner);
        Player human = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 400.0F, ROOM);
        SoulData humanData = SoulLogic.get(human);
        long now = HollowPlayerTests.now(helper);
        ItemStack first = owner.getMainHandItem();
        helper.assertTrue(ArrancarLogic.isOwnBlade(first, owner) && ArrancarLogic.forging(first) == 1
                && data.hollow().arrancar().forging() == 1, "the Arrancar's first blade is " + first);

        // Someone else's blade crumbles in anyone's inventory; an unowned one, as from the creative tab, stays.
        human.getInventory().setItem(3, first.copy());
        human.getInventory().setItem(4, new ItemStack(ModItems.ARRANCAR_BLADE.get()));
        ArrancarEvents.reconcileBlades(human, humanData, now);
        helper.assertTrue(human.getInventory().getItem(3).isEmpty(), "the Arrancar's blade did not crumble for a Human");
        helper.assertTrue(ArrancarLogic.isArrancarBlade(human.getInventory().getItem(4)), "the unowned blade crumbled");

        // Without its blade, a new one forms after the server's minutes (one here, at one call a second).
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        int minutes = BleachConfig.get(BleachConfig.BLADE_REFORM_MINUTES);
        BleachConfig.BLADE_REFORM_MINUTES.set(1);
        try {
            for (int second = 0; second < 59; second++) {
                ArrancarEvents.reconcileBlades(owner, data, now);
            }
            helper.assertTrue(bladesHeld(owner) == 0, "a blade formed again within 59 seconds");
            ArrancarEvents.reconcileBlades(owner, data, now);
        } finally {
            BleachConfig.BLADE_REFORM_MINUTES.set(minutes);
        }
        ItemStack reformed = ownBlade(owner);
        helper.assertTrue(!reformed.isEmpty() && ArrancarLogic.forging(reformed) == 2
                && data.hollow().arrancar().forging() == 2,
                "after a minute the blade formed again as " + reformed + ", with the record " + data.hollow().arrancar());

        // The older blade put back crumbles, and so does a second copy of the current one.
        owner.getInventory().setItem(5, first);
        owner.getInventory().setItem(6, reformed.copy());
        ArrancarEvents.reconcileBlades(owner, data, now);
        helper.assertTrue(owner.getInventory().getItem(5).isEmpty(), "the older blade did not crumble");
        helper.assertTrue(bladesHeld(owner) == 1 && !ownBlade(owner).isEmpty(),
                "the Arrancar carries " + bladesHeld(owner) + " blades, not its one current blade");

        // The current blade takes the damage of the form it rose from, set later by command.
        data.setTier(PowerPath.HOLLOW, HollowTier.VASTO_LORDE.tier());
        ArrancarEvents.reconcileBlades(owner, data, now);
        HollowPlayerTests.assertClose(helper, bladeDamage(ownBlade(owner)), 7.0, "the blade's damage as Vasto Lorde-born");

        // Kept through death, as a zanpakutō is.
        helper.assertTrue(BleachConfig.get(BleachConfig.KEEP_ON_DEATH), "keep_on_death is off by default");
        Vec3 at = helper.absoluteVec(ROOM);
        List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(helper.getLevel(), at.x, at.y, at.z,
                ownBlade(owner).copy())));
        ZanpakutoEvents.keepBlades(owner, drops);
        helper.assertTrue(drops.isEmpty(), "the Arrancar's own blade was left in its death drops");
        List<ItemStack> kept = owner.getData(SoulAttachments.KEPT_BLADES);
        helper.assertTrue(kept.size() == 1 && ArrancarLogic.isOwnBlade(kept.get(0), owner)
                && ArrancarLogic.forging(kept.get(0)) == 2, "the blades kept through death are " + kept);

        // A soul that is no longer an Arrancar holds no blade of its own.
        SoulLogic.changeState(data, SoulState.HUMAN);
        ArrancarEvents.reconcileBlades(owner, data, now);
        helper.assertTrue(bladesHeld(owner) == 0, "a Human kept its old Arrancar's blade");

        // An Arrancar with no record of a blade (an old save, or a state set outright) gets its first at once.
        Player old = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.ARRANCAR, 400.0F, ROOM);
        SoulData oldData = SoulLogic.get(old);
        helper.assertTrue(oldData.hollow().arrancar().equals(HollowProgress.Arrancar.NONE),
                "the old Arrancar's record is " + oldData.hollow().arrancar());
        ArrancarEvents.reconcileBlades(old, oldData, now);
        ItemStack given = ownBlade(old);
        helper.assertTrue(!given.isEmpty() && ArrancarLogic.forging(given) == 1 && oldData.hollow().arrancar().forging() == 1,
                "the old Arrancar's first second gave " + given + ", with the record " + oldData.hollow().arrancar());
        HollowPlayerTests.assertClose(helper, oldData.maxReiryoku(), 400.0, "the old Arrancar's maximum reiryoku");
        helper.succeed();
    }

    // ---- The Cero ----

    @GameTest(template = "empty")
    public static void anArrancarFiresItsOriginsCero(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        SoulData gillianBorn = SoulLogic.get(arrancar(helper, HollowTier.GILLIAN, 400.0F, ROOM));
        Player adjuchas = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData adjuchasBorn = SoulLogic.get(adjuchas);
        Player released = arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData releasedData = SoulLogic.get(released);
        assertRelease(helper, released, releasedData, now, ResurreccionEvents.Result.RELEASED, "an Adjuchas-born blade");

        helper.assertTrue(ArrancarLogic.ceroForm(gillianBorn) == HollowTier.GILLIAN
                && ArrancarLogic.ceroForm(adjuchasBorn) == HollowTier.ADJUCHAS
                && ArrancarLogic.ceroForm(releasedData) == HollowTier.ADJUCHAS,
                "the Arrancar's Cero forms are " + ArrancarLogic.ceroForm(gillianBorn) + " and "
                        + ArrancarLogic.ceroForm(adjuchasBorn));
        helper.assertTrue(HollowCero.chargeTicks(ArrancarLogic.ceroForm(gillianBorn)) == 50
                && HollowCero.chargeTicks(ArrancarLogic.ceroForm(adjuchasBorn)) == 25,
                "the Arrancar's Cero gathers in " + HollowCero.chargeTicks(ArrancarLogic.ceroForm(gillianBorn)) + " and "
                        + HollowCero.chargeTicks(ArrancarLogic.ceroForm(adjuchasBorn)) + " ticks");
        HollowPlayerTests.assertClose(helper, HollowCero.damage(adjuchasBorn, HollowTier.ADJUCHAS), 24.0,
                "a sealed Adjuchas-born Arrancar's Cero at 400");
        HollowPlayerTests.assertClose(helper, HollowCero.damage(releasedData, HollowTier.ADJUCHAS), 30.0,
                "a released Adjuchas-born Arrancar's Cero at 400");

        HollowCero.Refusal refusal = HollowCero.start(adjuchas, adjuchasBorn, now);
        try {
            helper.assertTrue(refusal == null && HollowCero.charging(adjuchasBorn),
                    "the Arrancar's Cero did not gather: " + refusal);
            HollowCero.cancel(adjuchas, adjuchasBorn);
            helper.assertFalse(HollowCero.charging(adjuchasBorn), "the cancelled Cero is still gathering");
        } finally {
            HollowCero.cancel(adjuchas, adjuchasBorn);
            discardBeams(helper, adjuchas);
        }

        Player human = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 400.0F, ROOM);
        HollowCero.Refusal plain = HollowCero.start(human, SoulLogic.get(human), now);
        try {
            helper.assertTrue(plain == HollowCero.Refusal.NOT_MENOS, "a Human's Cero gave " + plain);
        } finally {
            discardBeams(helper, human);
        }
        helper.succeed();
    }
}
