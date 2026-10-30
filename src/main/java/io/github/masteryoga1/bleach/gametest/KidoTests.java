package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.network.KidoKeyPayload;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Kidō: the spells as data (the four shipped Hadō, two with incantations, their order, and a broken or empty
 * file read leniently and clamped, and each spell sent as the sync sends it), who may cast and who may only choose, the
 * spells a Kidō level knows and the one chosen, the gate and the share of casting by name alone, a spell's power and
 * cost, what a Hadō strikes and what it spares, Kidō training up to the Academy's exam, how a line said in chat is heard
 * as an incantation, the chant's time and what breaks it, the misfire of a cast by name, and the burst, burn and blast
 * of Shakkahō and Sōkatsui. The by-name gate and share are also tested on a spell built in code ({@link #chantSpell}).
 * Mock players are not in the world and never tick, so the Kidō key is pressed by calling {@link KidoEvents#onKey} with
 * the time it would come at, and the game time stands still within a test: a later time passed in stands for a later
 * tick. Chat cannot be sent in a game test, so a line is heard by calling {@link KidoEvents#chant}, and a pending chant
 * goes off by calling {@link KidoEvents#tick} with the time it would come at; a misfire's roll is passed to the
 * five-argument {@code KidoEvents.cast}. Casters stand at {@link #LANE} in the {@code field} template, facing east, and
 * every cast goes east down the lane into a creature or the dirt wall; every creature, trace, light, takeover, mask,
 * gathering and team a test makes is put away within its first tick.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class KidoTests {
    /** Where a caster stands in the field: the middle of block (1, 2, 8), at the west end of a lane running east. */
    private static final Vec3 LANE = new Vec3(1.5, 2.0, 8.5);
    /** The yaw that faces east, down the lane. */
    private static final float EAST = -90.0F;
    private static final long DAY = 24_000L;
    /** How near a spell's numbers must come to those worked out by hand. */
    private static final double NUMBER_EPSILON = 0.01;
    private static final String TEAM = "bleach_kido_test";

    private KidoTests() {
    }

    // ---- Helpers ----

    /** A mock player in this game mode with this soul, on the ground at {@link #LANE}, facing east, a while in the world. */
    private static Player caster(GameTestHelper helper, GameType mode, SoulData data) {
        Player player = helper.makeMockPlayer(mode);
        player.setData(SoulAttachments.SOUL, data);
        Vec3 at = helper.absoluteVec(LANE);
        player.moveTo(at.x, at.y, at.z, EAST, 0.0F);
        player.setOnGround(true);
        player.tickCount = 1000;
        return player;
    }

    /** A Shinigami full of this much reiryoku, at this Kidō level. */
    private static SoulData shinigami(float maxReiryoku, int kido) {
        SoulData data = ShinigamiTests.soul(SoulState.SHINIGAMI, maxReiryoku);
        data.setLevel(Stat.KIDO, kido);
        return data;
    }

    /** The world's spells. */
    private static Registry<KidoSpell> spells(GameTestHelper helper) {
        return KidoSpell.registry(helper.getLevel()).orElseThrow();
    }

    private static Holder.Reference<KidoSpell> spell(GameTestHelper helper, ResourceKey<KidoSpell> key) {
        return spells(helper).getHolderOrThrow(key);
    }

    /**
     * A Hadō #99 built in code, a beam with one incantation ("test chant"), known from this Kidō level and cast by
     * name from that one: a spell with an incantation whose numbers a test can rely on whatever the data says. Its
     * numbers are those drafted for Shakkahō: 4 damage and 0.3 per root of reiryoku up to 28, 6% of the maximum (at
     * least 3), a cooldown of 30 ticks and 3 xp.
     */
    private static KidoSpell chantSpell(int level, int byName) {
        return chantSpell(level, byName, List.of("test chant"));
    }

    /** {@link #chantSpell(int, int)} with these incantations instead. */
    private static KidoSpell chantSpell(int level, int byName, List<String> incantations) {
        return new KidoSpell(KidoSpell.Kind.HADO, 99, Component.literal("Test"), KidoSpell.Shape.BEAM, level, byName,
                30, incantations, 0.06F, 3.0F, 30, 3,
                new KidoSpell.Look(0xE0301E, Optional.empty(), 0.9F, Optional.empty()),
                new KidoSpell.Effect(4.0F, 0.3F, 28.0F, 24.0, 0.35F, 1, 1.5F, 0.5F, 0.0F, 3.0F));
    }

    /** The spell's first incantation: canon's romaji. */
    private static String romaji(GameTestHelper helper, ResourceKey<KidoSpell> key) {
        return spell(helper, key).value().incantations().get(0);
    }

    /** The spell's second incantation: the mod's own English. */
    private static String english(GameTestHelper helper, ResourceKey<KidoSpell> key) {
        return spell(helper, key).value().incantations().get(1);
    }

    /** The chant this soul is saying, or null. */
    @Nullable
    private static KidoLogic.Chant pending(SoulData data) {
        return KidoLogic.pending(data);
    }

    /** The spells' keys, in their order. */
    private static List<ResourceKey<KidoSpell>> ids(List<Holder.Reference<KidoSpell>> spells) {
        return spells.stream().map(spell -> spell.key()).toList();
    }

    /** The spell's key, if there is one. */
    private static Optional<ResourceKey<KidoSpell>> id(Optional<Holder.Reference<KidoSpell>> spell) {
        return spell.map(found -> found.key());
    }

    private static void assertRefusal(GameTestHelper helper, @Nullable KidoLogic.Refusal actual,
                                      @Nullable KidoLogic.Refusal expected, String what) {
        helper.assertTrue(actual == expected, what + " gave " + actual + ", not " + expected);
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < NUMBER_EPSILON, what + " is " + actual + ", not " + expected);
    }

    /** The Kidō key pressed alone at this time: the chosen spell, cast by name. */
    @Nullable
    private static KidoLogic.Refusal press(Player player, long now) {
        return KidoEvents.onKey(player, KidoKeyPayload.KidoKey.PRESS, Optional.empty(), now);
    }

    /** Sneak and the Kidō key at this time: the next spell chosen. */
    @Nullable
    private static KidoLogic.Refusal next(Player player, long now) {
        return KidoEvents.onKey(player, KidoKeyPayload.KidoKey.NEXT, Optional.empty(), now);
    }

    /** The spell this soul has stored as its choice, if any. */
    private static Optional<ResourceKey<KidoSpell>> stored(SoulData data) {
        return data.shinigami().kido().chosen().map(location -> ResourceKey.create(KidoSpell.REGISTRY, location));
    }

    /** How far the soul's Asauchi leans to a kidō-type spirit. */
    private static int kidoLeaning(SoulData data) {
        return data.zanpakuto().affinity(ZanpakutoType.KIDO);
    }

    /** Whether nothing has hurt this creature: it is at full health. */
    private static boolean unhurt(LivingEntity creature) {
        return creature.getHealth() >= creature.getMaxHealth();
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    /** Every Cero in this test's structure: the traces the spells left, which no mock player is there to own. */
    private static void discardBeams(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds()).forEach(Entity::discard);
    }

    /** Runs the body with this setting on or off, and puts it back, so it changes only within this one tick. */
    private static void withConfig(ModConfigSpec.BooleanValue value, boolean set, Runnable body) {
        boolean before = BleachConfig.get(value);
        value.set(set);
        try {
            body.run();
        } finally {
            value.set(before);
        }
    }

    /** Turns the caster level and east, down the lane, so a beam that meets no creature stops at the wall. */
    private static void downTheLane(Player player) {
        player.setYRot(EAST);
        player.setYHeadRot(EAST);
        player.setXRot(0.0F);
    }

    /**
     * Lands this on the player as a blow comes, through every listener, with the invulnerability frames of the last
     * one over, as {@code VisoredHoldTests.strike} does; it must land.
     */
    private static void land(GameTestHelper helper, Player player, DamageSource source, float damage, String what) {
        player.invulnerableTime = 0;
        helper.assertTrue(player.hurt(source, damage), what + " did not land");
    }

    /** What a chat line was heard as: the outcome, the spell it was heard as (null for none) and the refusal. */
    private static void heardAs(GameTestHelper helper, KidoEvents.Heard actual, KidoEvents.Heard.Outcome outcome,
                                @Nullable ResourceKey<KidoSpell> spell, @Nullable KidoLogic.Refusal refusal, String what) {
        Optional<ResourceLocation> id = Optional.ofNullable(spell).map(ResourceKey::location);
        helper.assertTrue(actual.outcome() == outcome && actual.spell().equals(id) && actual.refusal() == refusal,
                what + " was heard as " + actual + ", not " + outcome + " " + id + " " + refusal);
    }

    /**
     * Starts a chant of Shakkahō, puts the soul in this state, and ticks it: the chant must go, with nothing spent or
     * trained.
     */
    private static void assertChantEnds(GameTestHelper helper, Player player, long now, Runnable state, String what) {
        SoulData data = SoulLogic.get(player);
        heardAs(helper, KidoEvents.chant(player, romaji(helper, KidoSpell.SHAKKAHO), now), KidoEvents.Heard.Outcome.STARTED,
                KidoSpell.SHAKKAHO, null, "the chant of " + what);
        helper.assertTrue(pending(data) != null, "the chant of " + what + " did not begin");
        state.run();
        float before = data.reiryoku();
        KidoEvents.tick(player, data, now + 1L);
        helper.assertTrue(pending(data) == null, "the chant of " + what + " was kept: " + pending(data));
        helper.assertTrue(data.xp(Stat.KIDO) == 0, "the chant of " + what + " trained " + data.xp(Stat.KIDO) + " Kidō xp");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), before, "the reiryoku of " + what);
    }

    private static void removeTeam(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    /** A dirt wall across the lane, so a beam that missed or pierced through cannot fly on into another test. */
    private static void buildWall(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(11, y, z, Blocks.DIRT);
            }
        }
    }

    /** The dirt wall across the lane, its middle column whole. */
    private static void assertWallStands(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) {
            helper.assertBlockPresent(Blocks.DIRT, 11, y, 8);
        }
    }

    /**
     * What a shipped spell with an incantation is: a Hadō beam known from its level and cast by name alone from a higher
     * one, said for its chant time, with two incantations (canon's romaji, then the mod's own English) that still fit
     * chat with the spell's call after them, and each heard whole, alone and with that call. {@code what} is the
     * spell's name as a player types it in the call ("Hadō #31, Shakkahō!").
     */
    private static void assertChanted(GameTestHelper helper, KidoSpell spell, ResourceLocation id, String what,
                                      String nameKey, int number, int level, int byName, int chantTicks, int colour) {
        helper.assertTrue(spell.kind() == KidoSpell.Kind.HADO && spell.number() == number,
                what + " is " + spell.kind() + " #" + spell.number());
        helper.assertTrue(spell.name().getContents() instanceof TranslatableContents name && nameKey.equals(name.getKey()),
                what + " is named " + spell.name());
        helper.assertTrue(spell.shape() == KidoSpell.Shape.BEAM && spell.castable(), what + " is of shape " + spell.shape());
        helper.assertTrue(spell.level() == level && spell.byNameLevel() == byName,
                what + " needs Kidō " + spell.level() + ", and " + spell.byNameLevel() + " by name");
        helper.assertTrue(spell.chantTicks() == chantTicks, what + " is chanted for " + spell.chantTicks() + " ticks");
        helper.assertTrue(spell.look().colour() == colour,
                what + "'s colour is " + Integer.toHexString(spell.look().colour()));
        helper.assertTrue(spell.chantable() && spell.incantations().size() == 2,
                what + " has incantations " + spell.incantations());
        String call = " Hadō #" + number + ", " + what + "!";
        for (String line : spell.incantations()) {
            helper.assertTrue((line + call).length() <= KidoSpell.MAX_INCANTATION_LENGTH,
                    what + "'s incantation with its call is " + (line + call).length() + " characters: " + line);
            helper.assertTrue(KidoLogic.hears(spell, id, line) && KidoLogic.hears(spell, id, line + call),
                    what + " does not hear its own incantation, alone or with its call: " + line);
        }
    }

    /** What a shipped spell without an incantation is: a Hadō beam with no blast, cast by name from its own level. */
    private static void assertShipped(GameTestHelper helper, KidoSpell spell, String what, String nameKey, int number,
                                      int level, int colour) {
        helper.assertTrue(spell.kind() == KidoSpell.Kind.HADO && spell.number() == number,
                what + " is " + spell.kind() + " #" + spell.number());
        helper.assertTrue(spell.name().getContents() instanceof TranslatableContents name && nameKey.equals(name.getKey()),
                what + " is named " + spell.name());
        helper.assertTrue(spell.shape() == KidoSpell.Shape.BEAM && spell.castable(), what + " is of shape " + spell.shape());
        helper.assertTrue(spell.level() == level && spell.byNameLevel() == level,
                what + " needs Kidō " + spell.level() + ", and " + spell.byNameLevel() + " by name");
        helper.assertTrue(spell.incantations().isEmpty() && !spell.chantable(), what + " has incantations "
                + spell.incantations());
        helper.assertTrue(spell.effect().blastRadius() == 0.0F, what + " bursts " + spell.effect().blastRadius() + " blocks");
        helper.assertTrue(spell.look().colour() == colour,
                what + "'s colour is " + Integer.toHexString(spell.look().colour()));
    }

    // ---- The spells as data ----

    @GameTest(template = "empty")
    public static void kidoSpellsLoadInOrderAndClamp(GameTestHelper helper) {
        Registry<KidoSpell> spells = spells(helper);
        helper.assertTrue(spells.containsKey(KidoSpell.SHO) && spells.containsKey(KidoSpell.BYAKURAI)
                && spells.containsKey(KidoSpell.SHAKKAHO) && spells.containsKey(KidoSpell.SOKATSUI),
                "the shipped spells did not load: " + spells.keySet());
        // A data pack may add its own, so only the mod's are held to their order.
        List<ResourceKey<KidoSpell>> shipped = ids(KidoSpell.sorted(spells)).stream()
                .filter(key -> BleachMod.MODID.equals(key.location().getNamespace()))
                .toList();
        helper.assertTrue(shipped.equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI, KidoSpell.SHAKKAHO, KidoSpell.SOKATSUI)),
                "the mod's spells sort as " + shipped);

        KidoSpell sho = spell(helper, KidoSpell.SHO).value();
        assertShipped(helper, sho, "Shō", "kido.bleach.sho", 1, 0, 0xCFE6FF);
        assertNear(helper, sho.costShare(), 0.02, "Shō's share of reiryoku");
        assertNear(helper, sho.minCost(), 1.0, "Shō's least cost");
        helper.assertTrue(sho.cooldownTicks() == 10 && sho.xp() == 1,
                "Shō waits " + sho.cooldownTicks() + " ticks and trains " + sho.xp() + " xp");
        helper.assertTrue(sho.effect().pierce() == 1, "Shō stops after " + sho.effect().pierce() + " creatures");
        assertNear(helper, sho.effect().knockback(), 1.2, "Shō's push");

        KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
        assertShipped(helper, byakurai, "Byakurai", "kido.bleach.byakurai", 4, 3, 0xEEF4FF);
        assertNear(helper, byakurai.costShare(), 0.04, "Byakurai's share of reiryoku");
        assertNear(helper, byakurai.minCost(), 2.0, "Byakurai's least cost");
        helper.assertTrue(byakurai.cooldownTicks() == 20 && byakurai.xp() == 2,
                "Byakurai waits " + byakurai.cooldownTicks() + " ticks and trains " + byakurai.xp() + " xp");
        helper.assertTrue(byakurai.effect().pierce() == 2,
                "Byakurai stops after " + byakurai.effect().pierce() + " creatures");

        // The two with incantations: chanted from their level, and cast by name alone only from a higher one.
        KidoSpell shakkaho = spell(helper, KidoSpell.SHAKKAHO).value();
        assertChanted(helper, shakkaho, KidoSpell.SHAKKAHO.location(), "Shakkahō", "kido.bleach.shakkaho", 31, 5, 10, 30,
                0xE0301E);
        assertNear(helper, shakkaho.costShare(), 0.06, "Shakkahō's share of reiryoku");
        assertNear(helper, shakkaho.minCost(), 3.0, "Shakkahō's least cost");
        helper.assertTrue(shakkaho.cooldownTicks() == 30 && shakkaho.xp() == 3,
                "Shakkahō waits " + shakkaho.cooldownTicks() + " ticks and trains " + shakkaho.xp() + " xp");
        helper.assertTrue(shakkaho.effect().pierce() == 1,
                "Shakkahō stops after " + shakkaho.effect().pierce() + " creatures");
        assertNear(helper, shakkaho.effect().blastRadius(), 1.5, "Shakkahō's burst");
        assertNear(helper, shakkaho.effect().blastShare(), 0.5, "the share of Shakkahō's burst");
        assertNear(helper, shakkaho.effect().burnSeconds(), 3.0, "Shakkahō's burn");
        assertNear(helper, shakkaho.effect().knockback(), 0.0, "Shakkahō's push");

        KidoSpell sokatsui = spell(helper, KidoSpell.SOKATSUI).value();
        assertChanted(helper, sokatsui, KidoSpell.SOKATSUI.location(), "Sōkatsui", "kido.bleach.sokatsui", 33, 8, 14, 40,
                0x3A7BFF);
        assertNear(helper, sokatsui.costShare(), 0.08, "Sōkatsui's share of reiryoku");
        assertNear(helper, sokatsui.minCost(), 4.0, "Sōkatsui's least cost");
        helper.assertTrue(sokatsui.cooldownTicks() == 40 && sokatsui.xp() == 4,
                "Sōkatsui waits " + sokatsui.cooldownTicks() + " ticks and trains " + sokatsui.xp() + " xp");
        helper.assertTrue(sokatsui.effect().pierce() == 1,
                "Sōkatsui stops after " + sokatsui.effect().pierce() + " creatures");
        assertNear(helper, sokatsui.effect().blastRadius(), 2.5, "Sōkatsui's burst");
        assertNear(helper, sokatsui.effect().blastShare(), 0.6, "the share of Sōkatsui's burst");
        assertNear(helper, sokatsui.effect().burnSeconds(), 0.0, "Sōkatsui's burn");
        assertNear(helper, sokatsui.effect().knockback(), 0.6, "Sōkatsui's push");

        List<ResourceKey<KidoSpell>> fourth = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.HADO, 4));
        helper.assertTrue(fourth.equals(List.of(KidoSpell.BYAKURAI)), "Hadō #4 is " + fourth);
        List<ResourceKey<KidoSpell>> thirtyFirst = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.HADO, 31));
        helper.assertTrue(thirtyFirst.equals(List.of(KidoSpell.SHAKKAHO)), "Hadō #31 is " + thirtyFirst);
        helper.assertTrue(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 1).isEmpty(), "a Bakudō #1 loaded");

        // Every value wrong in its own way: each falls back or is clamped alone, and the rest of the spell stays.
        String broken = "{\"number\": -5, \"shape\": \"gust\", \"level\": 7, \"by_name_level\": 2, \"cost_share\": 9,"
                + " \"cooldown_ticks\": 0, \"incantations\": [\"\", \"" + "x".repeat(300) + "\", \"ok\"],"
                + " \"effect\": {\"range\": 1000, \"pierce\": 0, \"damage\": \"lots\"}}";
        KidoSpell parsed = KidoSpell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(broken)).getOrThrow();
        helper.assertTrue(parsed.number() == 0, "a number of -5 read as " + parsed.number());
        helper.assertTrue(parsed.shape() == KidoSpell.Shape.NONE && !parsed.castable(),
                "an unknown shape read as " + parsed.shape());
        helper.assertTrue(parsed.level() == 7 && parsed.byNameLevel() == 7,
                "a level of 7 by name from 2 read as " + parsed.level() + " by name from " + parsed.byNameLevel());
        assertNear(helper, parsed.costShare(), 1.0, "a share of reiryoku of 9");
        helper.assertTrue(parsed.cooldownTicks() == 1, "a cooldown of 0 read as " + parsed.cooldownTicks());
        helper.assertTrue(parsed.incantations().equals(List.of("ok")) && parsed.chantable(),
                "a blank, an over-long and a good incantation read as " + parsed.incantations());
        assertNear(helper, parsed.effect().range(), 128.0, "a reach of 1000");
        helper.assertTrue(parsed.effect().pierce() == 1, "a pierce of 0 read as " + parsed.effect().pierce());
        assertNear(helper, parsed.effect().damage(), 0.0, "a damage of \"lots\"");
        KidoSpell empty = KidoSpell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        helper.assertFalse(empty.castable(), "an empty spell file can be cast, as a " + empty.shape());

        // Sent to a client as the sync sends it: encoded with the world's registries to NBT, and read back.
        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (KidoSpell spell : List.of(sho, byakurai, shakkaho, sokatsui, parsed)) {
            Tag sent = KidoSpell.CODEC.encodeStart(ops, spell).getOrThrow();
            KidoSpell read = KidoSpell.CODEC.parse(ops, sent).getOrThrow();
            helper.assertTrue(read.equals(spell), "sent " + spell + " but read " + read);
        }
        helper.succeed();
    }

    // ---- Who may cast ----

    @GameTest(template = "field")
    public static void onlyTheTrainedMayCastKido(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        // Alive, in survival and held by nothing: for every soul on it only the soul decides.
        Player anyone = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 0));
        SoulData giver = shinigami(400.0F, 0);
        SoulData receiver = ShinigamiTests.soul(SoulState.HUMAN, 100.0F);
        ShinigamiLogic.transferFromShinigami(giver, receiver, now);
        SoulData fallen = ShinigamiTests.soul(SoulState.HOLLOW, 400.0F);
        fallen.setTier(PowerPath.SHINIGAMI, 1);
        SoulData quincy = QuincyTests.withShinigamiPower(QuincyTests.quincy(Bloodline.ECHT, 1, 400.0F));

        Player masked = caster(helper, GameType.SURVIVAL, shinigami(800.0F, 0));
        SoulData maskedData = SoulLogic.get(masked);
        VisoredLogic.setRecord(maskedData, VisoredLogic.record(maskedData).withRootedAt(now - DAY).withSubduedAt(now - 1L));
        Player host = caster(helper, GameType.SURVIVAL, shinigami(900.0F, 0));
        SoulData hostData = SoulLogic.get(host);
        VisoredLogic.setRecord(hostData, VisoredLogic.record(hostData).withRootedAt(now - DAY));
        Player arrancar = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, LANE);
        SoulData arrancarData = SoulLogic.get(arrancar);
        Player dead = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 0));
        dead.setHealth(0.0F);
        Player spectator = caster(helper, GameType.SPECTATOR, shinigami(400.0F, 0));
        Player held = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 0));
        NegacionLight light = NegacionLight.place(level, held, null);
        Player creative = caster(helper, GameType.CREATIVE, shinigami(400.0F, 3));
        SoulData creativeData = SoulLogic.get(creative);
        try {
            assertRefusal(helper, KidoLogic.mayCast(anyone, shinigami(400.0F, 0), now), null, "a Shinigami's Kidō");
            helper.assertTrue(VisoredEvents.putOnMask(masked, maskedData, now, true) == VisoredEvents.MaskStart.PUT_ON
                    && VisoredLogic.isMasked(maskedData), "the Visored's mask did not go on");
            assertRefusal(helper, KidoLogic.mayCast(masked, maskedData, now), null, "a masked Visored's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(anyone, ShinigamiTests.student(60.0F), now), null,
                    "an Academy student's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(anyone, ShinigamiTests.substitute(60.0F), now), null,
                    "a Substitute's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(anyone, receiver, now), null,
                    "the Kidō of a Human given a Shinigami's power");
            assertRefusal(helper, KidoLogic.mayCast(anyone, giver, now), KidoLogic.Refusal.LENT,
                    "the Kidō of a Shinigami whose power is lent");
            helper.assertTrue(VisoredEvents.startTakeover(host, hostData, false, now) && VisoredLogic.takenOver(hostData),
                    "the rooted Hollow did not seize its host");
            assertRefusal(helper, KidoLogic.mayCast(host, hostData, now), KidoLogic.Refusal.TAKEN,
                    "the Kidō of a soul whose Hollow holds its body");

            assertRefusal(helper, KidoLogic.mayCast(anyone, ShinigamiTests.soul(SoulState.HOLLOW, 400.0F), now),
                    KidoLogic.Refusal.UNTRAINED, "a Hollow's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(anyone, fallen, now), KidoLogic.Refusal.UNTRAINED,
                    "the Kidō of a Hollow with a Shinigami tier");
            assertRefusal(helper, KidoLogic.mayCast(arrancar, arrancarData, now), KidoLogic.Refusal.UNTRAINED,
                    "an Arrancar's Kidō");
            arrancarData.setTier(PowerPath.SHINIGAMI, 1);
            assertRefusal(helper, KidoLogic.mayCast(arrancar, arrancarData, now), KidoLogic.Refusal.UNTRAINED,
                    "the Kidō of an Arrancar with a Shinigami tier");
            assertRefusal(helper, KidoLogic.mayCast(anyone, QuincyTests.quincy(Bloodline.ECHT, 1, 400.0F), now),
                    KidoLogic.Refusal.UNTRAINED, "an awakened Quincy's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(anyone, quincy, now), KidoLogic.Refusal.UNTRAINED,
                    "the Kidō of an awakened Quincy with a Shinigami tier");
            withConfig(BleachConfig.QUINCY_ALLOW_HYBRIDS, true, () -> assertRefusal(helper,
                    KidoLogic.mayCast(anyone, quincy, now), null,
                    "the Kidō of an awakened Quincy with a Shinigami tier, with hybrids allowed"));
            assertRefusal(helper, KidoLogic.mayCast(anyone, ShinigamiTests.soul(SoulState.HUMAN, 400.0F), now),
                    KidoLogic.Refusal.UNTRAINED, "a Human's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(anyone, ShinigamiTests.soul(SoulState.PLUS, 40.0F), now),
                    KidoLogic.Refusal.UNTRAINED, "a Plus's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(anyone, ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F), now),
                    KidoLogic.Refusal.UNTRAINED, "the Kidō of a soul not enrolled at the Academy");

            assertRefusal(helper, KidoLogic.mayCast(dead, SoulLogic.get(dead), now), KidoLogic.Refusal.UNABLE,
                    "a dead Shinigami's Kidō");
            assertRefusal(helper, KidoLogic.mayCast(spectator, SoulLogic.get(spectator), now), KidoLogic.Refusal.UNABLE,
                    "a spectator's Kidō");
            helper.assertTrue(light != null && held.startRiding(light, true) && Negacion.held(held),
                    "the mock Shinigami could not be held");
            assertRefusal(helper, KidoLogic.mayCast(held, SoulLogic.get(held), now), KidoLogic.Refusal.UNABLE,
                    "the Kidō of a Shinigami held by Negación");

            // Kept from casting only for a while, a soul may still choose the spell it will cast.
            helper.assertTrue(KidoLogic.mayChoose(null) && KidoLogic.mayChoose(KidoLogic.Refusal.TAKEN)
                    && KidoLogic.mayChoose(KidoLogic.Refusal.LENT),
                    "a soul that may cast, or is kept from it a while, may not choose");
            helper.assertFalse(KidoLogic.mayChoose(KidoLogic.Refusal.UNTRAINED)
                    || KidoLogic.mayChoose(KidoLogic.Refusal.UNABLE), "an untrained or unable soul may choose");

            // Creative pays nothing, waits for nothing and trains nothing. The wall stops both bolts in this test.
            buildWall(helper);
            KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
            assertRefusal(helper, KidoEvents.cast(creative, byakurai, KidoLogic.Form.NAME, now), null,
                    "a creative Shinigami's Byakurai");
            assertRefusal(helper, KidoEvents.cast(creative, byakurai, KidoLogic.Form.NAME, now), null,
                    "a creative Shinigami's second Byakurai in the same tick");
            HollowPlayerTests.assertClose(helper, creativeData.reiryoku(), 400.0, "a creative caster's reiryoku");
            helper.assertTrue(creativeData.level(Stat.KIDO) == 3 && creativeData.xp(Stat.KIDO) == 0,
                    "a creative caster trained Kidō to " + creativeData.level(Stat.KIDO) + " and "
                            + creativeData.xp(Stat.KIDO) + " xp");
            assertWallStands(helper);
        } finally {
            VisoredEvents.endTakeover(host, hostData, VisoredEvents.TakeoverEnd.REMOVED, now);
            VisoredEvents.endAll(masked, maskedData, now);
            held.stopRiding();
            discard(light);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- Knowing and choosing ----

    @GameTest(template = "empty")
    public static void kidoLevelGatesAndChooses(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Registry<KidoSpell> spells = spells(helper);
        KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
        KidoSpell chant = chantSpell(5, 10);
        List<ResourceKey<KidoSpell>> shoOnly = List.of(KidoSpell.SHO);
        Player player = ZanpakutoTests.mock(helper, shinigami(400.0F, 0));
        SoulData data = SoulLogic.get(player);
        helper.assertTrue(data.shinigami().kido().equals(ShinigamiProgress.Kido.NONE),
                "a new soul has chosen " + data.shinigami().kido());

        // Kidō 0 knows Shō alone, and the one spell it knows comes next after itself.
        helper.assertTrue(ids(KidoLogic.known(data, spells)).equals(shoOnly),
                "Kidō 0 knows " + ids(KidoLogic.known(data, spells)));
        helper.assertTrue(id(KidoLogic.chosen(data, spells)).equals(Optional.of(KidoSpell.SHO)),
                "with no choice at Kidō 0, the chosen spell is " + id(KidoLogic.chosen(data, spells)));
        helper.assertTrue(id(KidoLogic.next(data, spells)).equals(Optional.of(KidoSpell.SHO)),
                "at Kidō 0, the next spell is " + id(KidoLogic.next(data, spells)));
        data.setLevel(Stat.KIDO, 2);
        helper.assertTrue(ids(KidoLogic.known(data, spells)).equals(shoOnly),
                "Kidō 2 knows " + ids(KidoLogic.known(data, spells)));
        data.setLevel(Stat.KIDO, 3);
        helper.assertTrue(ids(KidoLogic.known(data, spells)).equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI)),
                "Kidō 3 knows " + ids(KidoLogic.known(data, spells)));
        assertRefusal(helper, next(player, now), null, "choosing at Kidō 3");
        helper.assertTrue(stored(data).equals(Optional.of(KidoSpell.BYAKURAI)), "Sneak and the key chose " + stored(data));
        helper.assertTrue(id(KidoLogic.next(data, spells)).equals(Optional.of(KidoSpell.SHO)),
                "after Byakurai, the next spell is " + id(KidoLogic.next(data, spells)));

        // A choice the soul does not know falls back to the first it knows, and is kept as it was.
        KidoLogic.choose(data, BleachMod.id("missing"));
        helper.assertTrue(id(KidoLogic.chosen(data, spells)).equals(Optional.of(KidoSpell.SHO)),
                "a spell that is not there is cast as " + id(KidoLogic.chosen(data, spells)));
        helper.assertTrue(data.shinigami().kido().chosen().equals(Optional.of(BleachMod.id("missing"))),
                "the missing choice was rewritten as " + data.shinigami().kido());
        KidoLogic.choose(data, KidoSpell.BYAKURAI.location());
        data.setLevel(Stat.KIDO, 2);
        helper.assertTrue(id(KidoLogic.chosen(data, spells)).equals(Optional.of(KidoSpell.SHO)),
                "Byakurai at Kidō 2 is cast as " + id(KidoLogic.chosen(data, spells)));
        helper.assertTrue(stored(data).equals(Optional.of(KidoSpell.BYAKURAI)),
                "falling below Byakurai rewrote the choice as " + stored(data));

        // The level, and for a spell with an incantation the higher level by name, are asked before any cost.
        data.setLevel(Stat.KIDO, 4);
        assertRefusal(helper, KidoEvents.cast(player, chant, KidoLogic.Form.NAME, now), KidoLogic.Refusal.NEEDS_LEVEL,
                "a spell of Kidō 5 cast by name at Kidō 4");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after a cast below its level");
        helper.assertTrue(KidoLogic.ready(data, now), "a cast below its level kept the hands busy");
        data.setLevel(Stat.KIDO, 7);
        assertRefusal(helper, KidoEvents.cast(player, chant, KidoLogic.Form.NAME, now), KidoLogic.Refusal.NEEDS_NAME_LEVEL,
                "a spell by name from Kidō 10 cast by name at Kidō 7");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku after a cast below its by-name level");
        helper.assertTrue(KidoLogic.ready(data, now), "a cast below its by-name level kept the hands busy");
        assertRefusal(helper, KidoLogic.gate(chant, KidoLogic.Form.CHANT, 4), KidoLogic.Refusal.NEEDS_LEVEL,
                "a spell of Kidō 5 chanted at Kidō 4");
        assertRefusal(helper, KidoLogic.gate(chant, KidoLogic.Form.CHANT, 7), null, "a spell of Kidō 5 chanted at Kidō 7");
        assertRefusal(helper, KidoLogic.gate(chant, KidoLogic.Form.NAME, 10), null,
                "a spell by name from Kidō 10 cast by name at Kidō 10");
        assertRefusal(helper, KidoLogic.gate(byakurai, KidoLogic.Form.NAME, 2), KidoLogic.Refusal.NEEDS_LEVEL,
                "Byakurai cast by name at Kidō 2");

        // One art at a time: no Kidō while a Getsuga Tenshō gathers, and nothing spent on the press.
        Player zangetsu = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData zangetsuData = SoulLogic.get(zangetsu);
        try {
            helper.assertTrue(ShikaiEvents.release(zangetsu) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
            helper.assertTrue(ShikaiEvents.gather(zangetsu) == ShikaiEvents.GatherResult.GATHERING,
                    "released Zangetsu did not gather");
            float before = zangetsuData.reiryoku();
            assertRefusal(helper, press(zangetsu, now), KidoLogic.Refusal.GATHERING,
                    "a press while a Getsuga Tenshō gathers");
            HollowPlayerTests.assertClose(helper, zangetsuData.reiryoku(), before,
                    "the reiryoku after a press while gathering");
            helper.assertTrue(KidoLogic.ready(zangetsuData, now), "a press while gathering kept the hands busy");
        } finally {
            ShikaiEvents.cancelGather(zangetsu, zangetsuData);
        }

        // A Hollow may not even choose; a soul whose power is lent may choose, but not cast.
        Player hollow = ZanpakutoTests.mock(helper, ShinigamiTests.soul(SoulState.HOLLOW, 400.0F));
        SoulData hollowData = SoulLogic.get(hollow);
        hollowData.setLevel(Stat.KIDO, 3);
        assertRefusal(helper, next(hollow, now), KidoLogic.Refusal.UNTRAINED, "a Hollow choosing");
        helper.assertTrue(hollowData.shinigami().kido().equals(ShinigamiProgress.Kido.NONE),
                "a Hollow's choice was stored as " + hollowData.shinigami().kido());
        SoulData lent = shinigami(400.0F, 0);
        ShinigamiLogic.lend(lent, now);
        Player lender = ZanpakutoTests.mock(helper, lent);
        assertRefusal(helper, next(lender, now), null, "a Shinigami whose power is lent choosing");
        helper.assertTrue(stored(lent).equals(Optional.of(KidoSpell.SHO)),
                "the lender's choice was stored as " + stored(lent));
        assertRefusal(helper, press(lender, now), KidoLogic.Refusal.LENT, "a press by a Shinigami whose power is lent");

        // The choice is saved and sent with the soul; a save from before Kidō, or a broken choice, reads as none.
        Tag saved = SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        SoulData loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        helper.assertTrue(stored(loaded).equals(Optional.of(KidoSpell.BYAKURAI)),
                "the saved choice loaded as " + stored(loaded));
        Tag progress = ShinigamiProgress.CODEC.encodeStart(NbtOps.INSTANCE, data.shinigami()).getOrThrow();
        helper.assertTrue(progress instanceof CompoundTag compound && compound.contains("kido"),
                "the Shinigami progress saved no Kidō: " + progress);
        CompoundTag older = ((CompoundTag) progress).copy();
        older.remove("kido");
        ShinigamiProgress before = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, older).getOrThrow();
        helper.assertTrue(before.kido().equals(ShinigamiProgress.Kido.NONE),
                "a save from before Kidō read as " + before.kido());
        CompoundTag unreadable = new CompoundTag();
        unreadable.putString("chosen", "Not a spell!");
        older.put("kido", unreadable);
        ShinigamiProgress broken = ShinigamiProgress.CODEC.parse(NbtOps.INSTANCE, older).getOrThrow();
        helper.assertTrue(broken.kido().equals(ShinigamiProgress.Kido.NONE), "an unreadable choice read as " + broken.kido());
        helper.succeed();
    }

    // ---- Strength, cost and training ----

    @GameTest(template = "empty")
    public static void eishohakiShareAndPower(GameTestHelper helper) {
        KidoSpell sho = spell(helper, KidoSpell.SHO).value();
        KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
        KidoSpell chant = chantSpell(5, 10);

        // Whole when chanted, or for a spell with no incantation; by name, half at the by-name level, up to 80%.
        assertNear(helper, KidoLogic.share(byakurai, KidoLogic.Form.NAME, 3), 1.0, "Byakurai's share by name at Kidō 3");
        assertNear(helper, KidoLogic.share(chant, KidoLogic.Form.CHANT, 5), 1.0, "the chanted share at Kidō 5");
        int[] levels = {10, 20, 25, 40};
        double[] shares = {0.5, 0.7, 0.8, 0.8};
        for (int i = 0; i < levels.length; i++) {
            assertNear(helper, KidoLogic.share(chant, KidoLogic.Form.NAME, levels[i]), shares[i],
                    "the share by name, from Kidō 10, at Kidō " + levels[i]);
        }

        // 2% stronger a Kidō level, up to the spell's cap, then the share.
        assertNear(helper, KidoLogic.power(sho, shinigami(60.0F, 5), 1.0F), 1.526, "Shō at 60 and Kidō 5");
        assertNear(helper, KidoLogic.power(sho, shinigami(1_500.0F, 40), 1.0F), 4.0, "Shō at 1,500 and Kidō 40");
        assertNear(helper, KidoLogic.power(byakurai, shinigami(150.0F, 10), 1.0F), 5.339, "Byakurai at 150 and Kidō 10");
        assertNear(helper, KidoLogic.power(byakurai, shinigami(1_500.0F, 40), 1.0F), 16.0, "Byakurai at 1,500 and Kidō 40");
        float chanted = KidoLogic.share(chant, KidoLogic.Form.CHANT, 25);
        assertNear(helper, KidoLogic.power(chant, shinigami(60.0F, 5), chanted), 6.956, "the chant at 60 and Kidō 5");
        assertNear(helper, KidoLogic.power(chant, shinigami(600.0F, 25), chanted), 17.023, "the chant at 600 and Kidō 25");
        assertNear(helper, KidoLogic.power(chant, shinigami(1_500.0F, 40), chanted), 28.0, "the chant at 1,500 and Kidō 40");
        float byName = KidoLogic.share(chant, KidoLogic.Form.NAME, 25);
        assertNear(helper, KidoLogic.power(chant, shinigami(600.0F, 25), byName), 13.618,
                "the spell by name at 600 and Kidō 25");
        float untrained = KidoLogic.power(byakurai, shinigami(150.0F, 0), 1.0F);
        float trained = KidoLogic.power(byakurai, shinigami(150.0F, 40), 1.0F);
        assertNear(helper, untrained, 4.449, "Byakurai at 150 and Kidō 0");
        assertNear(helper, trained, 8.009, "Byakurai at 150 and Kidō 40");
        assertNear(helper, trained / untrained, 1.8, "what 40 Kidō levels make of Byakurai below its cap");

        // A share of the maximum, never below the least cost.
        assertNear(helper, KidoLogic.cost(sho, shinigami(60.0F, 0)), 1.2, "Shō's cost at 60");
        assertNear(helper, KidoLogic.cost(sho, shinigami(20.0F, 0)), 1.0, "Shō's cost at 20");
        assertNear(helper, KidoLogic.cost(byakurai, shinigami(800.0F, 0)), 32.0, "Byakurai's cost at 800");
        helper.assertTrue(KidoLogic.xp(byakurai, false) == 2 && KidoLogic.xp(byakurai, true) == 3, "Byakurai trains "
                + KidoLogic.xp(byakurai, false) + " xp, and " + KidoLogic.xp(byakurai, true) + " on a strike");
        helper.succeed();
    }

    // ---- What a Hadō strikes ----

    /** In the Cero's griefing batch, so no other test sees mob griefing change. */
    @GameTest(template = "field", batch = "cero_griefing")
    public static void hadoStrikesWhatItMayAndBreaksNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Player player = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 3));
        SoulData data = SoulLogic.get(player);
        KidoLogic.choose(data, KidoSpell.BYAKURAI.location());
        buildWall(helper);
        PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 4, 8);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 7, 8);
        Pig pig = null;
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean savedGriefing = griefing.get();
        try {
            griefing.set(true, level.getServer());
            // Byakurai passes the Plus in its way, which it may not strike, and goes on to the Spider Hollow.
            player.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getBoundingBox().getCenter());
            assertRefusal(helper, press(player, now), null, "Byakurai at the Spider Hollow");
            helper.assertFalse(unhurt(spider), "Byakurai did not hurt the Spider Hollow");
            DamageSource source = spider.getLastDamageSource();
            helper.assertTrue(source != null && source.is(KidoEvents.HADO), "the Spider Hollow was struck by " + source);
            // The spell, not the caster's hand, strikes, so a shield never takes it for a blow.
            helper.assertTrue(source.getEntity() == player && source.getDirectEntity() != null
                    && !(source.getDirectEntity() instanceof LivingEntity),
                    "the Hadō struck from " + source.getDirectEntity() + " for " + source.getEntity());
            helper.assertTrue(unhurt(plus), "Byakurai hurt the Plus in its way");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 384.0, "the reiryoku after Byakurai");
            assertWallStands(helper);
            assertRefusal(helper, press(player, now + 5L), KidoLogic.Refusal.COOLDOWN,
                    "Byakurai a quarter of a second later");

            // A teammate with friendly fire off is spared, as the cast asks the same of every creature.
            Player teammate = caster(helper, GameType.SURVIVAL, ShinigamiTests.soul(SoulState.HUMAN, 400.0F));
            Scoreboard scoreboard = level.getScoreboard();
            try {
                removeTeam(scoreboard);
                helper.assertTrue(CeroBeam.mayStrike(player, teammate), "with no team, a Hadō spares another player");
                PlayerTeam team = scoreboard.addPlayerTeam(TEAM);
                team.setAllowFriendlyFire(false);
                // Every mock player has the same name, so the other player is on the caster's team too.
                scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                helper.assertFalse(CeroBeam.mayStrike(player, teammate), "a Hadō strikes a teammate with friendly fire off");
                team.setAllowFriendlyFire(true);
                helper.assertTrue(CeroBeam.mayStrike(player, teammate), "a Hadō spares a teammate with friendly fire on");
            } finally {
                removeTeam(scoreboard);
            }

            // Shō pushes what it strikes along it: vanilla's 0.4, halved, and its own 1.2.
            discard(plus, spider);
            pig = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 8);
            KidoLogic.choose(data, KidoSpell.SHO.location());
            player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
            assertRefusal(helper, press(player, now + 40L), null, "Shō at the pig");
            helper.assertFalse(unhurt(pig), "Shō did not hurt the pig");
            helper.assertTrue(pig.getDeltaMovement().x > 1.0, "Shō pushed the pig " + pig.getDeltaMovement());
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 376.0, "the reiryoku after Shō");
            assertWallStands(helper);
        } finally {
            griefing.set(savedGriefing, level.getServer());
            discard(plus, spider, pig);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- Training and the exam ----

    @GameTest(template = "field")
    public static void kidoTrainsAndIsExamined(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        SoulData data = ShinigamiTests.student(60.0F);
        data.setLevel(Stat.ZANJUTSU, 5);
        data.setLevel(Stat.HAKUDA, 5);
        data.setLevel(Stat.HOHO, 5);
        data.setLevel(Stat.KIDO, 4);
        data.addXp(Stat.KIDO, 179, SoulLogic.maxStatLevel());
        Player student = caster(helper, GameType.SURVIVAL, data);
        student.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
        helper.assertTrue(ShinigamiLogic.graduationRequirements().get(Stat.KIDO) == 5,
                "the Kidō exam asks for level " + ShinigamiLogic.graduationRequirements().get(Stat.KIDO));
        helper.assertTrue(ShinigamiLogic.graduationShortfall(data).equals(List.of(Stat.KIDO)),
                "a student a point short of Kidō 5 is short of " + ShinigamiLogic.graduationShortfall(data));
        SpiderHollow spider = null;
        Pig pig = null;
        try {
            // At the air: Shō's own xp, and the point it lacked.
            assertRefusal(helper, press(student, now), null, "a student's Shō at the air");
            helper.assertTrue(data.level(Stat.KIDO) == 5 && data.xp(Stat.KIDO) == 0,
                    "Shō at the air left Kidō " + data.level(Stat.KIDO) + " with " + data.xp(Stat.KIDO) + " xp");
            helper.assertTrue(ShinigamiLogic.graduationShortfall(data).isEmpty() && ShinigamiLogic.canGraduate(data),
                    "a student at Kidō 5 is still short of " + ShinigamiLogic.graduationShortfall(data));
            helper.assertTrue(kidoLeaning(data) == 0,
                    "Shō at the air leaned the Asauchi " + kidoLeaning(data) + " to kidō");

            // At a Hollow: one more for the strike, and the blade leans to kidō.
            spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 4, 8);
            student.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getBoundingBox().getCenter());
            assertRefusal(helper, press(student, now + 20L), null, "a student's Shō at the Spider Hollow");
            helper.assertFalse(unhurt(spider), "the student's Shō did not hurt the Spider Hollow");
            helper.assertTrue(data.xp(Stat.KIDO) == 2, "Shō on the Spider Hollow left " + data.xp(Stat.KIDO) + " Kidō xp");
            helper.assertTrue(kidoLeaning(data) == 1,
                    "Shō on a Hollow leaned the Asauchi " + kidoLeaning(data) + " to kidō");
            discard(spider);

            // At a pig: the strike counts, but only a Hollow struck leans the blade.
            pig = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 8);
            student.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
            assertRefusal(helper, press(student, now + 40L), null, "a student's Shō at the pig");
            helper.assertFalse(unhurt(pig), "the student's Shō did not hurt the pig");
            helper.assertTrue(data.xp(Stat.KIDO) == 4, "Shō on the pig left " + data.xp(Stat.KIDO) + " Kidō xp");
            helper.assertTrue(kidoLeaning(data) == 1, "Shō on a pig leaned the Asauchi " + kidoLeaning(data) + " to kidō");

            // In creative, a strike on a Hollow pays, trains and leans nothing.
            discard(pig);
            spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 4, 8);
            SoulData creativeData = ShinigamiTests.student(60.0F);
            Player creative = caster(helper, GameType.CREATIVE, creativeData);
            creative.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
            creative.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getBoundingBox().getCenter());
            assertRefusal(helper, press(creative, now + 40L), null, "a creative student's Shō at the Spider Hollow");
            helper.assertFalse(unhurt(spider), "the creative student's Shō did not hurt the Spider Hollow");
            helper.assertTrue(creativeData.level(Stat.KIDO) == 0 && creativeData.xp(Stat.KIDO) == 0,
                    "a creative student trained Kidō to " + creativeData.level(Stat.KIDO) + " and "
                            + creativeData.xp(Stat.KIDO) + " xp");
            helper.assertTrue(kidoLeaning(creativeData) == 0,
                    "a creative student's Shō leaned the Asauchi " + kidoLeaning(creativeData) + " to kidō");
            HollowPlayerTests.assertClose(helper, creativeData.reiryoku(), creativeData.maxReiryoku(),
                    "a creative student's reiryoku");
        } finally {
            discard(spider, pig);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- Incantations ----

    @GameTest(template = "empty")
    public static void chantsAreHeardWholeAfterFolding(GameTestHelper helper) {
        Registry<KidoSpell> spells = spells(helper);
        KidoSpell shakkaho = spell(helper, KidoSpell.SHAKKAHO).value();
        KidoSpell sokatsui = spell(helper, KidoSpell.SOKATSUI).value();
        ResourceLocation shakkahoId = KidoSpell.SHAKKAHO.location();
        ResourceLocation sokatsuiId = KidoSpell.SOKATSUI.location();
        String romaji = romaji(helper, KidoSpell.SHAKKAHO);
        String english = english(helper, KidoSpell.SHAKKAHO);
        String sokatsuiRomaji = romaji(helper, KidoSpell.SOKATSUI);
        String sokatsuiEnglish = english(helper, KidoSpell.SOKATSUI);

        // Marks, capitals, spaces and punctuation fall away, and a long vowel written out reads as one.
        for (String typed : List.of("Shakkahō", "Shakkaho", "SHAKKAHOU!!", "Shakkahoo")) {
            helper.assertTrue(KidoLogic.fold(typed).equals("shakkaho"),
                    "\"" + typed + "\" folds to " + KidoLogic.fold(typed));
        }
        helper.assertTrue(KidoLogic.fold("umi hedate").equals(KidoLogic.fold("umihedate")),
                "\"umi hedate\" folds to " + KidoLogic.fold("umi hedate") + ", but \"umihedate\" to "
                        + KidoLogic.fold("umihedate"));
        helper.assertTrue(KidoLogic.fold("Hadō #31, Shakkahō!").equals("hado31shakkaho"),
                "the call folds to " + KidoLogic.fold("Hadō #31, Shakkahō!"));
        helper.assertTrue(KidoLogic.fold("!!! …").isEmpty(), "punctuation alone folds to " + KidoLogic.fold("!!! …"));
        for (String line : List.of(romaji, english, sokatsuiRomaji, sokatsuiEnglish)) {
            String folded = KidoLogic.fold(line);
            helper.assertTrue(KidoLogic.fold(folded).equals(folded), "folding " + folded + " again changed it");
        }

        // Heard whole, in either rendering, with its call or without, however it is written.
        String call = " Hadō #31, Shakkahō!";
        List<String> heard = List.of(romaji, english, romaji + call, english + call, english + " Hado 31 Shakkahou",
                romaji.toUpperCase(Locale.ROOT), romaji.replace("ō", "o"), romaji.replace("umihedate", "umi hedate"));
        for (String line : heard) {
            helper.assertTrue(KidoLogic.hears(shakkaho, shakkahoId, line), "Shakkahō did not hear \"" + line + "\"");
        }

        // Not heard: the other chant of the same opening, part of a chant, a word more or less, the call alone or the
        // other spell's call, and nothing at all.
        helper.assertFalse(KidoLogic.hears(shakkaho, shakkahoId, sokatsuiRomaji), "Shakkahō heard Sōkatsui's chant");
        helper.assertFalse(KidoLogic.hears(sokatsui, sokatsuiId, romaji), "Sōkatsui heard Shakkahō's chant");
        List<String> unheard = List.of(romaji.substring(0, 76), romaji + " Hadō #33, Sōkatsui!", romaji + " please",
                romaji.replace("minami", "minam"), call.strip(), "", "!!!");
        for (String line : unheard) {
            helper.assertFalse(KidoLogic.hears(shakkaho, shakkahoId, line), "Shakkahō heard \"" + line + "\"");
        }
        // An incantation that folds to nothing is never heard, not even as itself.
        KidoSpell mute = chantSpell(0, 0, List.of("!!!"));
        helper.assertTrue(mute.chantable(), "a spell whose incantation is \"!!!\" has none: " + mute.incantations());
        helper.assertFalse(KidoLogic.hears(mute, BleachMod.id("test_chant"), "!!!")
                || KidoLogic.hears(mute, BleachMod.id("test_chant"), ""), "an incantation of \"!!!\" was heard");

        // Of the world's spells, the one that hears the line; the chosen one only when it hears it too, and the Kidō
        // level is the cast's to refuse.
        SoulData novice = shinigami(400.0F, 0);
        SoulData adept = shinigami(400.0F, 10);
        KidoLogic.choose(adept, KidoSpell.SOKATSUI.location());
        for (SoulData data : List.of(novice, adept)) {
            String who = data == novice ? "at Kidō 0" : "at Kidō 10 with Sōkatsui chosen";
            Optional<ResourceKey<KidoSpell>> asSokatsui = id(KidoLogic.heard(data, spells, sokatsuiEnglish));
            helper.assertTrue(asSokatsui.equals(Optional.of(KidoSpell.SOKATSUI)),
                    "Sōkatsui's English " + who + " is heard as " + asSokatsui);
            Optional<ResourceKey<KidoSpell>> asShakkaho = id(KidoLogic.heard(data, spells, romaji));
            helper.assertTrue(asShakkaho.equals(Optional.of(KidoSpell.SHAKKAHO)),
                    "Shakkahō's romaji " + who + " is heard as " + asShakkaho);
            Optional<ResourceKey<KidoSpell>> hello = id(KidoLogic.heard(data, spells, "hello"));
            helper.assertTrue(hello.isEmpty(), "\"hello\" " + who + " is heard as " + hello);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void aChantCastsAtFullStrengthAfterItsTime(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        String romaji = romaji(helper, KidoSpell.SHAKKAHO);
        // Kidō 5 knows Shakkahō, and cannot cast it by name alone.
        Player player = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 5));
        SoulData data = SoulLogic.get(player);
        downTheLane(player);
        Pig pig = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 8);
        try {
            // Heard and begun: it goes off a chant's time later, and nothing is spent until then.
            heardAs(helper, KidoEvents.chant(player, romaji, now), KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO,
                    null, "Shakkahō's romaji at Kidō 5");
            KidoLogic.Chant chant = pending(data);
            helper.assertTrue(chant != null && chant.firesAt() == now + 30L,
                    "the chant goes off as " + chant + ", not at " + (now + 30L));
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 150.0, "the reiryoku once the chant began");
            helper.assertTrue(KidoLogic.ready(data, now), "beginning a chant kept the hands busy");

            // One art at a time: the key and a second chant wait for it, and choosing goes on.
            assertRefusal(helper, press(player, now + 1L), KidoLogic.Refusal.CHANTING, "the Kidō key while chanting");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 150.0, "the reiryoku after the key while chanting");
            heardAs(helper, KidoEvents.chant(player, romaji, now + 20L), KidoEvents.Heard.Outcome.REFUSED,
                    KidoSpell.SHAKKAHO, KidoLogic.Refusal.CHANTING, "a second chant while chanting");
            helper.assertTrue(pending(data) == chant, "a second chant changed the first to " + pending(data));
            assertRefusal(helper, next(player, now + 21L), null, "Sneak and the key while chanting");
            helper.assertTrue(stored(data).equals(Optional.of(KidoSpell.BYAKURAI)),
                    "Sneak and the key while chanting chose " + stored(data));
            helper.assertTrue(pending(data) == chant, "choosing while chanting left the chant as " + pending(data));

            // A tick short of its time it still waits.
            KidoEvents.tick(player, data, now + 29L);
            helper.assertTrue(pending(data) == chant, "a tick short of its time, the chant is " + pending(data));
            helper.assertTrue(unhurt(pig), "the chant went off at the pig before its time");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 150.0, "the reiryoku a tick short of the chant's time");

            // On time, where the caster then looks, at full strength: struck, burning and trained twice over.
            player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
            KidoEvents.tick(player, data, now + 30L);
            helper.assertTrue(pending(data) == null, "the chant was still pending once it went off: " + pending(data));
            // 4 + 0.3 per root of 150, 10% stronger at Kidō 5: 8.44, all of it chanted.
            assertNear(helper, pig.getHealth(), 1.558, "the pig's health after the chanted Shakkahō");
            DamageSource source = pig.getLastDamageSource();
            helper.assertTrue(source != null && source.is(KidoEvents.HADO) && source.getEntity() == player,
                    "the pig was struck by " + source);
            helper.assertTrue(pig.getRemainingFireTicks() == 60,
                    "the chanted Shakkahō set the pig alight for " + pig.getRemainingFireTicks() + " ticks, not 60");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 141.0, "the reiryoku after the chanted Shakkahō");
            helper.assertFalse(KidoLogic.ready(data, now + 59L), "the hands were free a tick before Shakkahō's cooldown");
            helper.assertTrue(KidoLogic.ready(data, now + 60L), "the hands were not free after Shakkahō's cooldown");
            helper.assertTrue(data.xp(Stat.KIDO) == 8, "the chanted Shakkahō on the pig trained " + data.xp(Stat.KIDO)
                    + " Kidō xp, not twice 3 and 1");
            helper.assertTrue(stored(data).equals(Optional.of(KidoSpell.BYAKURAI)),
                    "the chanted Shakkahō changed the choice to " + stored(data));

            // The English is a chant too: at the wall it strikes nothing, and trains twice its own xp.
            discard(pig);
            downTheLane(player);
            heardAs(helper, KidoEvents.chant(player, english(helper, KidoSpell.SHAKKAHO), now + 60L),
                    KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO, null, "Shakkahō's English");
            KidoLogic.Chant again = pending(data);
            helper.assertTrue(again != null && again.firesAt() == now + 90L,
                    "the English chant goes off as " + again + ", not at " + (now + 90L));
            KidoEvents.tick(player, data, now + 90L);
            helper.assertTrue(pending(data) == null, "the English chant was still pending once it went off");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 132.0, "the reiryoku after the English chant");
            helper.assertTrue(data.xp(Stat.KIDO) == 14, "the English chant at the wall left " + data.xp(Stat.KIDO)
                    + " Kidō xp, not 8 and twice 3");
            assertWallStands(helper);
        } finally {
            discard(pig);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void aBlowOrAnotherArtBreaksAChant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        String romaji = romaji(helper, KidoSpell.SHAKKAHO);
        Player player = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 10));
        SoulData data = SoulLogic.get(player);
        downTheLane(player);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 3);
        // Its soul made now, so the reiatsu gap weighs each of its blows alike.
        spider.soul();
        Player dead = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 10));
        Player held = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 10));
        NegacionLight light = null;
        Player taken = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 10));
        SoulData takenData = SoulLogic.get(taken);
        VisoredLogic.setRecord(takenData, VisoredLogic.record(takenData).withRootedAt(now - DAY));
        Player gatherer = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData gathererData = SoulLogic.get(gatherer);
        gathererData.setLevel(Stat.KIDO, 10);
        try {
            // A creature's blow that lands breaks the chant, and nothing was spent.
            heardAs(helper, KidoEvents.chant(player, romaji, now), KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO,
                    null, "the first chant");
            land(helper, player, player.damageSources().mobAttack(spider), 2.0F, "the Spider Hollow's blow");
            helper.assertTrue(pending(data) == null, "the Spider Hollow's blow left the chant pending");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku once the blow broke the chant");
            helper.assertTrue(KidoLogic.ready(data, now), "a broken chant kept the hands busy");
            KidoEvents.tick(player, data, now + 30L);
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku when the broken chant was due");
            helper.assertTrue(data.xp(Stat.KIDO) == 0, "a broken chant trained " + data.xp(Stat.KIDO) + " Kidō xp");

            // Fire and magic come from no creature; a blow that absorption hearts soak up still lands.
            heardAs(helper, KidoEvents.chant(player, romaji, now + 40L), KidoEvents.Heard.Outcome.STARTED,
                    KidoSpell.SHAKKAHO, null, "the second chant");
            land(helper, player, player.damageSources().onFire(), 1.0F, "the fire");
            helper.assertTrue(pending(data) != null, "burning broke the chant");
            land(helper, player, player.damageSources().magic(), 1.0F, "the magic");
            helper.assertTrue(pending(data) != null, "magic broke the chant");
            AttributeInstance most = player.getAttribute(Attributes.MAX_ABSORPTION);
            helper.assertTrue(most != null, "a player has no most absorption");
            most.setBaseValue(4.0);
            player.setAbsorptionAmount(4.0F);
            float health = player.getHealth();
            land(helper, player, player.damageSources().mobAttack(spider), 2.0F, "the soaked blow");
            helper.assertTrue(pending(data) == null, "a blow soaked by absorption hearts left the chant pending");
            helper.assertTrue(player.getAbsorptionAmount() < 4.0F,
                    "the soaked blow took no absorption: " + player.getAbsorptionAmount());
            HollowPlayerTests.assertClose(helper, player.getHealth(), health, "the health after the soaked blow");

            // Inside the frames that blow left, one no stronger never lands; a stronger one lands what it has over.
            heardAs(helper, KidoEvents.chant(player, romaji, now + 60L), KidoEvents.Heard.Outcome.STARTED,
                    KidoSpell.SHAKKAHO, null, "the third chant");
            player.setAbsorptionAmount(0.0F);
            health = player.getHealth();
            helper.assertFalse(player.hurt(player.damageSources().mobAttack(spider), 2.0F),
                    "a blow no stronger than the last landed inside its invulnerability frames");
            helper.assertTrue(pending(data) != null, "a blow that did not land broke the chant");
            helper.assertTrue(player.hurt(player.damageSources().mobAttack(spider), 4.0F),
                    "a stronger blow inside the invulnerability frames did not land");
            helper.assertTrue(pending(data) == null, "a stronger blow inside the frames left the chant pending");
            helper.assertTrue(player.getHealth() < health, "a stronger blow inside the frames took no health");

            // A chant does not pass through a portal with its soul.
            heardAs(helper, KidoEvents.chant(player, romaji, now + 80L), KidoEvents.Heard.Outcome.STARTED,
                    KidoSpell.SHAKKAHO, null, "the fourth chant");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(pending(data) == null, "the chant went through the portal: " + pending(data));

            // Each begun first, so its end is not for want of beginning: death, a hold, a takeover and another art.
            assertChantEnds(helper, dead, now, () -> dead.setHealth(0.0F), "a Shinigami who died");
            NegacionLight placed = NegacionLight.place(level, held, null);
            light = placed;
            assertChantEnds(helper, held, now, () -> helper.assertTrue(placed != null && held.startRiding(placed, true)
                    && Negacion.held(held), "the mock Shinigami could not be held"), "a Shinigami held by Negación");
            assertChantEnds(helper, taken, now, () -> helper.assertTrue(VisoredEvents.startTakeover(taken, takenData,
                    false, now) && VisoredLogic.takenOver(takenData), "the rooted Hollow did not seize its host"),
                    "a soul whose Hollow took its body");
            helper.assertTrue(ShikaiEvents.release(gatherer) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
            assertChantEnds(helper, gatherer, now, () -> helper.assertTrue(ShikaiEvents.gather(gatherer)
                    == ShikaiEvents.GatherResult.GATHERING, "released Zangetsu did not gather"),
                    "a Shinigami gathering a Getsuga Tenshō");
        } finally {
            ShikaiEvents.cancelGather(gatherer, gathererData);
            VisoredEvents.endTakeover(taken, takenData, VisoredEvents.TakeoverEnd.REMOVED, now);
            held.stopRiding();
            discard(light, spider);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void whatAChatLineIsHeardAs(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell sokatsui = spell(helper, KidoSpell.SOKATSUI).value();
        String romaji = romaji(helper, KidoSpell.SHAKKAHO);
        Player human = caster(helper, GameType.SURVIVAL, ShinigamiTests.soul(SoulState.HUMAN, 100.0F));
        SoulData hollowData = ShinigamiTests.soul(SoulState.HOLLOW, 400.0F);
        hollowData.setLevel(Stat.KIDO, 10);
        Player hollow = caster(helper, GameType.SURVIVAL, hollowData);
        Player spectator = caster(helper, GameType.SPECTATOR, shinigami(400.0F, 10));
        Player shinigami = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 10));
        SoulData shinigamiData = SoulLogic.get(shinigami);
        Player novice = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 4));
        SoulData spentData = shinigami(400.0F, 10);
        spentData.setReiryoku(2.0F);
        Player spent = caster(helper, GameType.SURVIVAL, spentData);
        Player host = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 10));
        SoulData hostData = SoulLogic.get(host);
        VisoredLogic.setRecord(hostData, VisoredLogic.record(hostData).withRootedAt(now - DAY));
        SoulData lent = shinigami(400.0F, 5);
        SoulData given = ShinigamiTests.soul(SoulState.HUMAN, 100.0F);
        given.setLevel(Stat.KIDO, 5);
        ShinigamiLogic.transferFromShinigami(lent, given, now);
        lent.setReiryoku(lent.maxReiryoku());
        Player lender = caster(helper, GameType.SURVIVAL, lent);
        downTheLane(lender);
        Player receiver = caster(helper, GameType.SURVIVAL, given);
        Player busy = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 10));
        SoulData busyData = SoulLogic.get(busy);
        downTheLane(busy);
        try {
            // A soul that cannot cast Kidō at all is only saying the words.
            heardAs(helper, KidoEvents.chant(human, romaji, now), KidoEvents.Heard.Outcome.PLAIN, null, null,
                    "Shakkahō's romaji from a Human");
            heardAs(helper, KidoEvents.chant(hollow, romaji, now), KidoEvents.Heard.Outcome.PLAIN, null, null,
                    "Shakkahō's romaji from a Hollow at Kidō 10");
            heardAs(helper, KidoEvents.chant(spectator, romaji, now), KidoEvents.Heard.Outcome.PLAIN, null, null,
                    "Shakkahō's romaji from a spectator");
            helper.assertTrue(VisoredEvents.startTakeover(host, hostData, false, now) && VisoredLogic.takenOver(hostData),
                    "the rooted Hollow did not seize its host");
            heardAs(helper, KidoEvents.chant(host, romaji, now), KidoEvents.Heard.Outcome.PLAIN, null, null,
                    "Shakkahō's romaji from a soul whose Hollow holds its body");
            for (Player soul : List.of(human, hollow, spectator, host)) {
                helper.assertTrue(pending(SoulLogic.get(soul)) == null, "a soul that cannot cast Kidō is chanting");
            }

            // From a soul that may chant, only a whole incantation is a chant, and only while chat casting is on.
            for (String line : List.of("hello", "Hadō #31, Shakkahō!", romaji + " please")) {
                heardAs(helper, KidoEvents.chant(shinigami, line, now), KidoEvents.Heard.Outcome.PLAIN, null, null,
                        "\"" + line + "\"");
            }
            withConfig(BleachConfig.KIDO_CHAT_CASTING, false, () -> heardAs(helper, KidoEvents.chant(shinigami, romaji,
                    now), KidoEvents.Heard.Outcome.PLAIN, null, null, "Shakkahō's romaji with chat casting off"));
            helper.assertTrue(pending(shinigamiData) == null, "a chant began with chat casting off");
            heardAs(helper, KidoEvents.chant(shinigami, romaji, now), KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO,
                    null, "Shakkahō's romaji with chat casting on");
            // Within a second of the last, a chant is hushed rather than spoken.
            heardAs(helper, KidoEvents.chant(shinigami, romaji, now + 5L), KidoEvents.Heard.Outcome.HUSHED,
                    KidoSpell.SHAKKAHO, null, "Shakkahō's romaji a quarter of a second later");

            // Heard and refused out loud: below the spell's level, or too weak to pay for it, with nothing spent.
            heardAs(helper, KidoEvents.chant(novice, romaji, now), KidoEvents.Heard.Outcome.REFUSED, KidoSpell.SHAKKAHO,
                    KidoLogic.Refusal.NEEDS_LEVEL, "Shakkahō's romaji at Kidō 4");
            heardAs(helper, KidoEvents.chant(spent, romaji, now), KidoEvents.Heard.Outcome.REFUSED, KidoSpell.SHAKKAHO,
                    KidoLogic.Refusal.TOO_WEAK, "Shakkahō's romaji with 2 reiryoku");
            HollowPlayerTests.assertClose(helper, spentData.reiryoku(), 2.0, "the reiryoku after a chant too weak to pay");

            // A Shinigami whose power is lent casts by its whole incantation alone, from its halved pool.
            HollowPlayerTests.assertClose(helper, lent.maxReiryoku(), 200.0, "the lender's halved most reiryoku");
            assertRefusal(helper, press(lender, now), KidoLogic.Refusal.LENT,
                    "the Kidō key of a Shinigami whose power is lent");
            heardAs(helper, KidoEvents.chant(lender, romaji, now), KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO,
                    null, "Shakkahō's romaji from a Shinigami whose power is lent");
            KidoLogic.Chant lenders = pending(lent);
            helper.assertTrue(lenders != null, "the lender's chant did not begin");
            KidoEvents.tick(lender, lent, lenders.firesAt());
            helper.assertTrue(pending(lent) == null, "the lender's chant did not go off");
            HollowPlayerTests.assertClose(helper, lent.reiryoku(), 188.0, "the lender's reiryoku after Shakkahō");
            heardAs(helper, KidoEvents.chant(receiver, romaji, now), KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO,
                    null, "Shakkahō's romaji from the Human given that power");

            // A chant begun while the hands are busy goes off once they are free.
            assertRefusal(helper, KidoEvents.cast(busy, sokatsui, KidoLogic.Form.CHANT, now, 1.0), null,
                    "Sōkatsui chanted at the wall");
            heardAs(helper, KidoEvents.chant(busy, romaji, now + 1L), KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO,
                    null, "Shakkahō's romaji with the hands busy");
            KidoLogic.Chant waiting = pending(busyData);
            helper.assertTrue(waiting != null && waiting.firesAt() == now + 40L,
                    "the chant begun with busy hands goes off as " + waiting + ", not at " + (now + 40L));
            assertWallStands(helper);
        } finally {
            VisoredEvents.endTakeover(host, hostData, VisoredEvents.TakeoverEnd.REMOVED, now);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- The misfire ----

    @GameTest(template = "field")
    public static void anUnskilledEishohakiMisfires(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell shakkaho = spell(helper, KidoSpell.SHAKKAHO).value();
        KidoSpell sokatsui = spell(helper, KidoSpell.SOKATSUI).value();
        KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
        Player player = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 10));
        SoulData data = SoulLogic.get(player);
        player.setHealth(20.0F);
        Player creative = caster(helper, GameType.CREATIVE, shinigami(150.0F, 10));
        SoulData creativeData = SoulLogic.get(creative);
        downTheLane(creative);
        Player master = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 20));
        downTheLane(master);
        Pig pig = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 8);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        try {
            // 30% at the by-name level, 3% less for each level above, none ten levels on; none without an incantation.
            int[] levels = {10, 11, 15, 20, 25};
            double[] chances = {0.3, 0.27, 0.15, 0.0, 0.0};
            for (int i = 0; i < levels.length; i++) {
                assertNear(helper, KidoLogic.misfireChance(shakkaho, levels[i]), chances[i],
                        "Shakkahō's chance to misfire at Kidō " + levels[i]);
            }
            assertNear(helper, KidoLogic.misfireChance(byakurai, 3), 0.0, "Byakurai's chance to misfire at Kidō 3");
            assertNear(helper, KidoLogic.misfireChance(sokatsui, 14), 0.3, "Sōkatsui's chance to misfire at Kidō 14");

            // A quarter of the cast's damage (half of 9.21 by name at Kidō 10), never leaving less than a heart.
            assertNear(helper, KidoLogic.misfireDamage(shakkaho, data, 0.5F, 20.0F, 0.0F), 1.151, "a misfire at 20 health");
            assertNear(helper, KidoLogic.misfireDamage(shakkaho, data, 0.5F, 2.5F, 0.0F), 0.5, "a misfire at 2.5 health");
            assertNear(helper, KidoLogic.misfireDamage(shakkaho, data, 0.5F, 2.0F, 0.0F), 0.0, "a misfire at 2 health");
            assertNear(helper, KidoLogic.misfireDamage(shakkaho, data, 0.5F, 2.0F, 1.0F), 1.0,
                    "a misfire at 2 health and 1 absorption");

            // A roll under the chance: it bursts in the hand, paid for, and nothing is cast.
            assertRefusal(helper, KidoEvents.cast(player, shakkaho, KidoLogic.Form.NAME, now, 0.29),
                    KidoLogic.Refusal.MISFIRED, "Shakkahō by name at Kidō 10, rolling 0.29");
            helper.assertTrue(unhurt(pig), "the misfire hurt the pig");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds()).isEmpty(),
                    "the misfire left a trace");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 141.0, "the reiryoku after the misfire");
            helper.assertFalse(KidoLogic.ready(data, now + 29L), "the hands were free a tick before the misfire's cooldown");
            assertNear(helper, player.getHealth(), 18.849, "the health after the misfire");
            helper.assertTrue(data.xp(Stat.KIDO) == 0, "the misfire trained " + data.xp(Stat.KIDO) + " Kidō xp");

            // The floor: a misfire never leaves its caster below one heart.
            player.setHealth(2.5F);
            player.invulnerableTime = 0;
            assertRefusal(helper, KidoEvents.cast(player, shakkaho, KidoLogic.Form.NAME, now + 30L, 0.0),
                    KidoLogic.Refusal.MISFIRED, "Shakkahō by name at 2.5 health, rolling 0");
            assertNear(helper, player.getHealth(), 2.0, "the health after a misfire at 2.5");
            assertRefusal(helper, KidoEvents.cast(player, shakkaho, KidoLogic.Form.NAME, now + 60L, 0.0),
                    KidoLogic.Refusal.MISFIRED, "Shakkahō by name at 2 health, rolling 0");
            assertNear(helper, player.getHealth(), 2.0, "the health after a misfire at 2");

            // A roll at the chance is not under it: cast, at the by-name share.
            player.setHealth(20.0F);
            assertRefusal(helper, KidoEvents.cast(player, shakkaho, KidoLogic.Form.NAME, now + 90L, 0.3), null,
                    "Shakkahō by name at Kidō 10, rolling 0.3");
            assertNear(helper, pig.getHealth(), 5.395, "the pig's health after Shakkahō by name");

            // A chant, a spell without an incantation, creative and mastery never misfire. Each goes into the wall.
            discard(pig);
            downTheLane(player);
            assertRefusal(helper, KidoEvents.cast(player, shakkaho, KidoLogic.Form.CHANT, now + 120L, 0.0), null,
                    "Shakkahō chanted, rolling 0");
            HollowPlayerTests.assertClose(helper, player.getHealth(), 20.0, "the health after the chanted Shakkahō");
            assertRefusal(helper, KidoEvents.cast(player, byakurai, KidoLogic.Form.NAME, now + 150L, 0.0), null,
                    "Byakurai by name, rolling 0");
            assertRefusal(helper, KidoEvents.cast(creative, shakkaho, KidoLogic.Form.NAME, now, 0.0), null,
                    "a creative Shinigami's Shakkahō by name, rolling 0");
            HollowPlayerTests.assertClose(helper, creativeData.reiryoku(), 150.0, "a creative caster's reiryoku");
            HollowPlayerTests.assertClose(helper, creative.getHealth(), 20.0, "a creative caster's health");
            assertRefusal(helper, KidoEvents.cast(master, shakkaho, KidoLogic.Form.NAME, now, 0.0), null,
                    "Shakkahō by name at Kidō 20, rolling 0");
            HollowPlayerTests.assertClose(helper, master.getHealth(), 20.0, "the health of a caster at Kidō 20");
            assertWallStands(helper);
        } finally {
            discard(pig);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- Shakkahō and Sōkatsui ----

    /** In the Cero's griefing batch, so no other test sees mob griefing change. */
    @GameTest(template = "field", batch = "cero_griefing")
    public static void shakkahoBurstsAndBurnsSokatsuiBlastsWide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell shakkaho = spell(helper, KidoSpell.SHAKKAHO).value();
        KidoSpell sokatsui = spell(helper, KidoSpell.SOKATSUI).value();
        Player player = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 10));
        Pig struck = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 8);
        Pig beside = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 9);
        Pig beyond = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 10);
        Pig wideStruck = null;
        Pig wideBeyond = null;
        Pig weighty = null;
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean savedGriefing = griefing.get();
        try {
            griefing.set(true, level.getServer());
            // By name at Kidō 10, half strength: the pig it strikes burns, the one beside it takes half in the burst,
            // and one whose box is 1.59 blocks from where the beam met the first is beyond the burst's 1.5.
            player.lookAt(EntityAnchorArgument.Anchor.EYES, struck.getBoundingBox().getCenter());
            assertRefusal(helper, KidoEvents.cast(player, shakkaho, KidoLogic.Form.NAME, now, 1.0), null,
                    "Shakkahō by name at the first pig");
            assertNear(helper, struck.getHealth(), 5.395, "the struck pig's health");
            helper.assertTrue(struck.getRemainingFireTicks() == 30,
                    "Shakkahō by name set the struck pig alight for " + struck.getRemainingFireTicks() + " ticks, not 30");
            assertNear(helper, beside.getHealth(), 7.698, "the health of the pig beside it");
            helper.assertTrue(beside.getRemainingFireTicks() <= 0, "the burst set the pig beside it alight");
            helper.assertTrue(unhurt(beyond), "the burst reached the pig beyond it");
            HollowPlayerTests.assertClose(helper, player.getHealth(), 20.0, "the caster's health after its own burst");
            for (int z = 8; z <= 10; z++) {
                helper.assertBlockNotPresent(Blocks.FIRE, 6, 2, z);
            }
            assertWallStands(helper);

            // Chanted at Kidō 10, Sōkatsui's 11.14 kills the pig it strikes, and its wider blast reaches the one beyond.
            discard(struck, beside, beyond);
            wideStruck = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 8);
            wideBeyond = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 10);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, wideStruck.getBoundingBox().getCenter());
            assertRefusal(helper, KidoEvents.cast(player, sokatsui, KidoLogic.Form.CHANT, now + 40L, 1.0), null,
                    "Sōkatsui chanted at the first pig");
            helper.assertTrue(wideStruck.isDeadOrDying(), "Sōkatsui left the pig it struck at " + wideStruck.getHealth());
            assertNear(helper, wideBeyond.getHealth(), 3.314, "the health of the pig two blocks off");
            helper.assertTrue(wideBeyond.getRemainingFireTicks() <= 0, "Sōkatsui set the pig two blocks off alight");

            // A soul of 1,500 against the caster's 150, ten times over: the gap lets through 3 parts in 10 of the
            // blow, and of the burn.
            discard(wideStruck, wideBeyond);
            weighty = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 8);
            weighty.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, 1_500.0F));
            player.lookAt(EntityAnchorArgument.Anchor.EYES, weighty.getBoundingBox().getCenter());
            assertRefusal(helper, KidoEvents.cast(player, shakkaho, KidoLogic.Form.CHANT, now + 80L, 1.0), null,
                    "Shakkahō chanted at the pig with a soul");
            helper.assertTrue(Math.abs(weighty.getHealth() - 7.237) < 0.02,
                    "the health of the pig with a soul is " + weighty.getHealth() + ", not 7.237");
            helper.assertTrue(weighty.getRemainingFireTicks() == 18,
                    "Shakkahō set the pig with a soul alight for " + weighty.getRemainingFireTicks() + " ticks, not 18");
        } finally {
            griefing.set(savedGriefing, level.getServer());
            discard(struck, beside, beyond, wideStruck, wideBeyond, weighty);
            // What the slain pig dropped.
            level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).forEach(Entity::discard);
            level.getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).forEach(Entity::discard);
            discardBeams(helper);
        }
        helper.succeed();
    }
}
