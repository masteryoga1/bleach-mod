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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Kidō: the spells as data (the two shipped Hadō, their order, and a broken or empty file read leniently and
 * clamped, and each spell sent as the sync sends it), who may cast and who may only choose, the spells a Kidō level
 * knows and the one chosen, the gate and the share of casting by name alone, a spell's power and cost, what a Hadō
 * strikes and what it spares, and Kidō training up to the Academy's exam. No shipped spell has an incantation yet, so the
 * by-name gate and share are tested on one built in code ({@link #chantSpell}). Mock players are not in the world and
 * never tick, so the Kidō key is pressed by calling {@link KidoEvents#onKey} with the time it would come at, and the
 * game time stands still within a test: a later time passed in stands for a later tick. Casters stand at {@link #LANE}
 * in the {@code field} template, facing east; every creature, trace, light, takeover, mask, gathering and team a test
 * makes is put away within its first tick.
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
     * name from that one: a stand-in for a spell with an incantation, which no shipped file has yet. Its numbers are
     * those drafted for Shakkahō: 4 damage and 0.3 per root of reiryoku up to 28, 6% of the maximum (at least 3), a
     * cooldown of 30 ticks and 3 xp.
     */
    private static KidoSpell chantSpell(int level, int byName) {
        return new KidoSpell(KidoSpell.Kind.HADO, 99, Component.literal("Test"), KidoSpell.Shape.BEAM, level, byName,
                30, List.of("test chant"), 0.06F, 3.0F, 30, 3,
                new KidoSpell.Look(0xE0301E, Optional.empty(), 0.9F, Optional.empty()),
                new KidoSpell.Effect(4.0F, 0.3F, 28.0F, 24.0, 0.35F, 1, 1.5F, 0.5F, 0.0F, 3.0F));
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

    /** Runs the body with hybrids allowed or not, and puts the setting back, so it changes only within this one tick. */
    private static void withHybrids(boolean allowed, Runnable body) {
        boolean before = BleachConfig.get(BleachConfig.QUINCY_ALLOW_HYBRIDS);
        BleachConfig.QUINCY_ALLOW_HYBRIDS.set(allowed);
        try {
            body.run();
        } finally {
            BleachConfig.QUINCY_ALLOW_HYBRIDS.set(before);
        }
    }

    private static void removeTeam(Scoreboard scoreboard) {
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM);
        if (team != null) {
            scoreboard.removePlayerTeam(team);
        }
    }

    /** The dirt wall across the lane, its middle column whole. */
    private static void assertWallStands(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) {
            helper.assertBlockPresent(Blocks.DIRT, 11, y, 8);
        }
    }

    /** What every shipped spell is: a Hadō beam with no incantation and no blast, cast by name from its own level. */
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
        helper.assertTrue(spells.containsKey(KidoSpell.SHO) && spells.containsKey(KidoSpell.BYAKURAI),
                "the shipped spells did not load: " + spells.keySet());
        // A data pack may add its own, so only the mod's are held to their order.
        List<ResourceKey<KidoSpell>> shipped = ids(KidoSpell.sorted(spells)).stream()
                .filter(key -> BleachMod.MODID.equals(key.location().getNamespace()))
                .toList();
        helper.assertTrue(shipped.equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI)), "the mod's spells sort as " + shipped);

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

        List<ResourceKey<KidoSpell>> fourth = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.HADO, 4));
        helper.assertTrue(fourth.equals(List.of(KidoSpell.BYAKURAI)), "Hadō #4 is " + fourth);
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
        for (KidoSpell spell : List.of(sho, byakurai, parsed)) {
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
            withHybrids(true, () -> assertRefusal(helper, KidoLogic.mayCast(anyone, quincy, now), null,
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

            // Creative pays nothing, waits for nothing and trains nothing.
            KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
            assertRefusal(helper, KidoEvents.cast(creative, byakurai, KidoLogic.Form.NAME, now), null,
                    "a creative Shinigami's Byakurai");
            assertRefusal(helper, KidoEvents.cast(creative, byakurai, KidoLogic.Form.NAME, now), null,
                    "a creative Shinigami's second Byakurai in the same tick");
            HollowPlayerTests.assertClose(helper, creativeData.reiryoku(), 400.0, "a creative caster's reiryoku");
            helper.assertTrue(creativeData.level(Stat.KIDO) == 3 && creativeData.xp(Stat.KIDO) == 0,
                    "a creative caster trained Kidō to " + creativeData.level(Stat.KIDO) + " and "
                            + creativeData.xp(Stat.KIDO) + " xp");
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
        for (int y = 2; y <= 5; y++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(11, y, z, Blocks.DIRT);
            }
        }
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

            // In creative, a strike pays, trains and leans nothing.
            SoulData creativeData = ShinigamiTests.student(60.0F);
            Player creative = caster(helper, GameType.CREATIVE, creativeData);
            creative.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
            creative.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
            pig.invulnerableTime = 0;
            assertRefusal(helper, press(creative, now + 40L), null, "a creative student's Shō at the pig");
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
}
