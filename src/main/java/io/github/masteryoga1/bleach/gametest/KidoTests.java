package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.DankuWall;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.kido.KidoSpell;
import io.github.masteryoga1.bleach.network.KidoKeyPayload;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BakudoEffect;
import io.github.masteryoga1.bleach.soul.Bala;
import io.github.masteryoga1.bleach.soul.GranReyCero;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.KidoSmoke;
import io.github.masteryoga1.bleach.soul.KidoWard;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
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
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Kidō: the spells as data (the six shipped Hadō, three with incantations, and the seven Bakudō, two of them
 * chanted, their order and their looks, a broken or empty file read leniently and clamped, and each spell sent as the
 * sync sends it), who may cast and who may only choose, the spells a Kidō level knows and the one chosen, the gate and
 * the share of casting by name alone, a spell's power and cost, what a Hadō strikes and what it spares, Kidō training
 * up to the Academy's exam, how a line said in chat is heard as an incantation, the chant's time and what breaks it,
 * the misfire of a cast by name, the burst, burn and blast of Shakkahō and Sōkatsui, Tenran's widening gust (what it
 * reaches and what a wall keeps from it, its push away from the caster, the players it blinds and what spares them, and
 * its loss without a zanpakutō at hand), Raikōhō gathered in the palm on the Kidō key (nothing spent until it goes off,
 * the misfire drawn only then, and what lets it go, scatters, breaks or drops it), and the binds of Sai and Hainawa
 * (the contest of strength that weighs each, what the ray reaches, what bound arms and a held body are kept from and
 * what they still do, who may be bound, and what shrugs a bind off or lifts it), Geki and Rikujōkōrō chanted (how each
 * is heard, gated and shared, and a bind that binds and holds at once, drawn in its spell's look), the pin of a body
 * its Hollow has seized by a hold long enough, Sekienton's smoke (the creatures in it left lost to the caster, their
 * hunt and a creeper's swell stopped, at once and at each pulse, the players it blinds and what spares them, and the
 * caster quickened), Enkōsen's shield (the blows it takes from the front and within the moment of another, what passes
 * once it breaks, what it never covers, what ends it, and a Hadō beam and arrows stopped at it), and Dankū's wall (the
 * Hadō and arrows it stops, the arts it weighs by their shooters' reiatsu against its caster's, the bodies and binds it
 * lets through, and its end, never saved). The by-name gate and share are also tested on a spell built in code
 * ({@link #chantSpell}). Mock players are not in the world and never tick, so the Kidō key is pressed and let go by
 * calling {@link KidoEvents#onKey} with the time it would come at, and the game time stands still within a test: a
 * later time passed in stands for a later tick. Chat cannot be sent in a game test, so a line is heard by calling
 * {@link KidoEvents#chant}, and a pending chant or gather goes off by calling {@link KidoEvents#tick} with the time it
 * would come at, as a smoke pulses and a shield ends by calling {@link KidoSmoke#tick} and {@link KidoWard#tick}; a
 * misfire's roll is passed to the five-argument {@code KidoEvents.cast}, and for a gather, which draws its own roll as
 * it goes off, the misfire chance is set to 1 or 0 for that one call. No cone can find a mock player, so a gust's blow
 * is given to one directly ({@link KidoEvents#gustBlow}); nor can a bind's ray, so a bind is laid on one directly
 * ({@link KidoEvents#bindOne}), its clamp is called by hand and what it refuses is posted as the event the act would
 * fire; nor can a smoke's pulse, so the smoke is laid on one directly ({@link KidoSmoke#smokeOn}). A wall is mostly
 * raised directly ({@link DankuWall#raise}), and a crescent or an arrow shot at it is ticked by hand within the test's
 * one tick. A still golem has no will of its own, so the revenge goal a blow would start is started by hand. Raikōhō's
 * heavy bolt is taken by an iron golem, whose 100 health outlasts it. Casters stand at {@link #LANE} in the
 * {@code field} template, facing east: every beam, bind, crescent and arrow goes east down the lane into a creature, a
 * wall or the dirt wall, and every gust east along it, ending within the field; every creature, trace, orb, light,
 * takeover, mask, gathering, team, bind, look, wary mark, smoke, shield, wall and lost mark a test makes is put away
 * within its first tick, but for the walls one test leaves to go by themselves.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class KidoTests {
    /** Where a caster stands in the field: the middle of block (1, 2, 8), at the west end of a lane running east. */
    private static final Vec3 LANE = new Vec3(1.5, 2.0, 8.5);
    /** Where a player struck by a gust stands: three blocks down the lane, in the middle of the gust. */
    private static final Vec3 DOWN_THE_LANE = new Vec3(4.5, 2.0, 8.5);
    /** The yaw that faces east, down the lane. */
    private static final float EAST = -90.0F;
    private static final long DAY = 24_000L;
    /** How near a spell's numbers must come to those worked out by hand. */
    private static final double NUMBER_EPSILON = 0.01;
    /** How near the health a blow leaves must come, a few blows' rounding allowed. */
    private static final double HEALTH_EPSILON = 0.02;
    /** How near a push must come to the speed worked out by hand. */
    private static final double PUSH_EPSILON = 0.05;
    private static final String TEAM = "bleach_kido_test";
    private static final int TENRAN_COLOUR = 0xD2EEE6;
    private static final int RAIKOHO_COLOUR = 0xFFE23A;
    private static final int SAI_COLOUR = 0xE6D7A3;
    private static final int HAINAWA_COLOUR = 0xF2C230;
    private static final int GEKI_COLOUR = 0xD6303C;
    private static final int RIKUJOKORO_COLOUR = 0xFFF1A8;
    private static final int SEKIENTON_COLOUR = 0xB8262E;
    private static final int ENKOSEN_COLOUR = 0xD8C46A;
    private static final int DANKU_COLOUR = 0xE4DCFF;
    /** Where a creature keeps its wariness of each Bakudō ({@link KidoLogic#markWary}), so a test can clear it. */
    private static final String WARY_TAG = "bleach:bakudo_wary";
    /** The held body's modifier on the walk, which takes the whole of it. */
    private static final ResourceLocation HELD_SPEED = BleachMod.id("bakudo_held_speed");
    /** How near a motion the hold leaves must come: it sets the motion, so only rounding is allowed. */
    private static final double MOTION_EPSILON = 1.0E-6;

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
     * least 3), a cooldown of 30 ticks and 3 xp. It loses nothing without a blade, does not gather in the palm and
     * binds nothing.
     */
    private static KidoSpell chantSpell(int level, int byName) {
        return chantSpell(level, byName, List.of("test chant"));
    }

    /** {@link #chantSpell(int, int)} with these incantations instead. */
    private static KidoSpell chantSpell(int level, int byName, List<String> incantations) {
        return new KidoSpell(KidoSpell.Kind.HADO, 99, Component.literal("Test"), KidoSpell.Shape.BEAM, level, byName,
                30, incantations, 0.06F, 3.0F, 30, 3,
                new KidoSpell.Look(0xE0301E, Optional.empty(), 0.9F, Optional.empty()),
                new KidoSpell.Effect(4.0F, 0.3F, 28.0F, 24.0, 0.35F, 1, 1.5F, 0.5F, 0.0F, 3.0F, 60.0F, 0.0F, 1.0F, 0.0F,
                        0.0F), 0);
    }

    /** The spell's first incantation: its chosen romaji reading. */
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

    /** The spell this soul is gathering in its palm on the Kidō key, or null. */
    @Nullable
    private static KidoLogic.Gather gathering(SoulData data) {
        return KidoLogic.gathering(data);
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
        assertNear(helper, actual, expected, NUMBER_EPSILON, what);
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, double epsilon, String what) {
        helper.assertTrue(Math.abs(actual - expected) < epsilon, what + " is " + actual + ", not " + expected);
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

    /** The Kidō key let go at this time, as a payload's time is: the tick just ended. */
    @Nullable
    private static KidoLogic.Refusal release(Player player, long now) {
        return KidoEvents.onKey(player, KidoKeyPayload.KidoKey.RELEASE, Optional.empty(), now);
    }

    /** A screen opened at this time while the Kidō key was held. */
    @Nullable
    private static KidoLogic.Refusal cancel(Player player, long now) {
        return KidoEvents.onKey(player, KidoKeyPayload.KidoKey.CANCEL, Optional.empty(), now);
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

    /** The same for a number: a chance set to 1 or 0, so no roll decides a test. */
    private static void withConfig(ModConfigSpec.DoubleValue value, double set, Runnable body) {
        double before = BleachConfig.get(value);
        value.set(set);
        try {
            body.run();
        } finally {
            value.set(before);
        }
    }

    /** The same for a whole number, such as a time in seconds. */
    private static void withConfig(ModConfigSpec.IntValue value, int set, Runnable body) {
        int before = BleachConfig.get(value);
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
     * Starts a chant of this spell, puts the soul in this state, and ticks it: the chant must go, with nothing spent or
     * trained.
     */
    private static void assertChantEnds(GameTestHelper helper, Player player, ResourceKey<KidoSpell> spell, long now,
                                        Runnable state, String what) {
        SoulData data = SoulLogic.get(player);
        heardAs(helper, KidoEvents.chant(player, romaji(helper, spell), now), KidoEvents.Heard.Outcome.STARTED, spell,
                null, "the chant of " + what);
        helper.assertTrue(pending(data) != null, "the chant of " + what + " did not begin");
        state.run();
        float before = data.reiryoku();
        KidoEvents.tick(player, data, now + 1L);
        helper.assertTrue(pending(data) == null, "the chant of " + what + " was kept: " + pending(data));
        helper.assertTrue(data.xp(Stat.KIDO) == 0, "the chant of " + what + " trained " + data.xp(Stat.KIDO) + " Kidō xp");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), before, "the reiryoku of " + what);
    }

    /**
     * A survival Shinigami of 1,000 at Kidō 42, where Raikōhō cast by name keeps 70% and never misfires, with Raikōhō
     * chosen, at {@link #LANE} and turned down it.
     */
    private static Player gatherer(GameTestHelper helper) {
        Player player = caster(helper, GameType.SURVIVAL, shinigami(1_000.0F, 42));
        KidoLogic.choose(SoulLogic.get(player), KidoSpell.RAIKOHO.location());
        downTheLane(player);
        return player;
    }

    /** {@link #assertGatherEnds(GameTestHelper, Player, long, String, BiConsumer)} for a new {@link #gatherer}. */
    private static void assertGatherEnds(GameTestHelper helper, long now, String what,
                                         BiConsumer<Player, SoulData> state) {
        assertGatherEnds(helper, gatherer(helper), now, what, state);
    }

    /**
     * Begins Raikōhō on this soul's key, which must gather with its orb, then puts the soul in this state and ticks it:
     * the gather must go, its orb with it, with nothing spent or trained.
     */
    private static void assertGatherEnds(GameTestHelper helper, Player player, long now, String what,
                                         BiConsumer<Player, SoulData> state) {
        SoulData data = SoulLogic.get(player);
        assertRefusal(helper, press(player, now), null, "Raikōhō's key for " + what);
        CeroBeam orb = orbOf(helper, data, "Raikōhō for " + what);
        state.accept(player, data);
        KidoEvents.tick(player, data, now + 1L);
        helper.assertTrue(gathering(data) == null, "the gather of " + what + " was kept: " + gathering(data));
        helper.assertTrue(orb.isRemoved(), "the orb of " + what + " outlived its gather");
        helper.assertTrue(data.xp(Stat.KIDO) == 0, "the gather of " + what + " trained " + data.xp(Stat.KIDO) + " Kidō xp");
        HollowPlayerTests.assertClose(helper, data.reiryoku(), 1_000.0, "the reiryoku of " + what);
    }

    /** An iron golem standing still at this block of the field: 100 health, which outlasts Raikōhō, and no push. */
    private static IronGolem golem(GameTestHelper helper, int x, int z) {
        return HollowPlayerTests.placed(helper, EntityType.IRON_GOLEM, x, z);
    }

    /** The Ceros in this test's structure still charging: the orbs of spells gathering in the palm. */
    private static List<CeroBeam> charging(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds(), CeroBeam::isCharging);
    }

    /** The orb of the spell this soul is gathering, which must be there and still charging. */
    private static CeroBeam orbOf(GameTestHelper helper, SoulData data, String what) {
        KidoLogic.Gather gather = gathering(data);
        CeroBeam orb = gather != null ? gather.orb() : null;
        helper.assertTrue(orb != null && orb.isCharging(), what + " did not gather with its orb: " + gather);
        return orb;
    }

    /** A Shinigami of this much reiryoku in this game mode, with full health, standing {@link #DOWN_THE_LANE}. */
    private static Player victim(GameTestHelper helper, GameType mode, float maxReiryoku) {
        Player player = caster(helper, mode, shinigami(maxReiryoku, 0));
        Vec3 at = helper.absoluteVec(DOWN_THE_LANE);
        player.moveTo(at.x, at.y, at.z, EAST, 0.0F);
        player.setHealth(20.0F);
        return player;
    }

    /** The blindness on this creature, or null for none. */
    @Nullable
    private static MobEffectInstance blindness(LivingEntity creature) {
        return creature.getEffect(MobEffects.BLINDNESS);
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
     * What a shipped spell with an incantation is: a spell of this kind and shape known from its level and cast by name
     * alone from a higher one, said for its chant time, with this many incantations (the chosen romaji reading, then the
     * mod's own English, then any other romaji reading) that still fit chat with the spell's call after them, and each
     * heard whole, alone and with that call. {@code what} is the spell's name as a player types it in the call
     * ("Hadō #31, Shakkahō!", "Bakudō #9, Geki!").
     */
    private static void assertChanted(GameTestHelper helper, KidoSpell spell, ResourceLocation id, String what,
                                      String nameKey, int number, int level, int byName, int chantTicks, int colour,
                                      int incantations, KidoSpell.Kind kind, KidoSpell.Shape shape) {
        helper.assertTrue(spell.kind() == kind && spell.number() == number,
                what + " is " + spell.kind() + " #" + spell.number());
        helper.assertTrue(spell.name().getContents() instanceof TranslatableContents name && nameKey.equals(name.getKey()),
                what + " is named " + spell.name());
        helper.assertTrue(spell.shape() == shape && spell.castable(), what + " is of shape " + spell.shape());
        helper.assertTrue(spell.level() == level && spell.byNameLevel() == byName,
                what + " needs Kidō " + spell.level() + ", and " + spell.byNameLevel() + " by name");
        helper.assertTrue(spell.chantTicks() == chantTicks, what + " is chanted for " + spell.chantTicks() + " ticks");
        helper.assertTrue(spell.look().colour() == colour,
                what + "'s colour is " + Integer.toHexString(spell.look().colour()));
        helper.assertTrue(spell.chantable() && spell.incantations().size() == incantations,
                what + " has incantations " + spell.incantations());
        String call = " " + kindName(kind) + " #" + number + ", " + what + "!";
        for (String line : spell.incantations()) {
            helper.assertTrue((line + call).length() <= KidoSpell.MAX_INCANTATION_LENGTH,
                    what + "'s incantation with its call is " + (line + call).length() + " characters: " + line);
            helper.assertTrue(KidoLogic.hears(spell, id, line) && KidoLogic.hears(spell, id, line + call),
                    what + " does not hear its own incantation, alone or with its call: " + line);
        }
    }

    /** A kind's name as a player types it in a spell's call, such as "Bakudō". */
    private static String kindName(KidoSpell.Kind kind) {
        return switch (kind) {
            case HADO -> "Hadō";
            case BAKUDO -> "Bakudō";
            case KAIDO -> "Kaidō";
        };
    }

    /**
     * What a shipped spell without an incantation is: a spell of this kind and shape with no blast, cast by name from its
     * level.
     */
    private static void assertShipped(GameTestHelper helper, KidoSpell spell, String what, String nameKey, int number,
                                      int level, int colour, KidoSpell.Kind kind, KidoSpell.Shape shape) {
        helper.assertTrue(spell.kind() == kind && spell.number() == number,
                what + " is " + spell.kind() + " #" + spell.number());
        helper.assertTrue(spell.name().getContents() instanceof TranslatableContents name && nameKey.equals(name.getKey()),
                what + " is named " + spell.name());
        helper.assertTrue(spell.shape() == shape && spell.castable(), what + " is of shape " + spell.shape());
        helper.assertTrue(spell.level() == level && spell.byNameLevel() == level,
                what + " needs Kidō " + spell.level() + ", and " + spell.byNameLevel() + " by name");
        helper.assertTrue(spell.incantations().isEmpty() && !spell.chantable(), what + " has incantations "
                + spell.incantations());
        helper.assertTrue(spell.effect().blastRadius() == 0.0F, what + " bursts " + spell.effect().blastRadius() + " blocks");
        helper.assertTrue(spell.look().colour() == colour,
                what + "'s colour is " + Integer.toHexString(spell.look().colour()));
    }

    /** The ticks left of this creature's bound arms, or 0 for none. */
    private static int boundTicks(LivingEntity creature) {
        MobEffectInstance bound = creature.getEffect(ModEffects.BAKUDO_BOUND);
        return bound != null ? bound.getDuration() : 0;
    }

    /** The ticks left of this creature's held body, or 0 for none. */
    private static int heldTicks(LivingEntity creature) {
        MobEffectInstance held = creature.getEffect(ModEffects.BAKUDO_HELD);
        return held != null ? held.getDuration() : 0;
    }

    /** Whether this creature is neither bound nor held. */
    private static boolean unbound(LivingEntity creature) {
        return boundTicks(creature) == 0 && heldTicks(creature) == 0;
    }

    /** How fast this creature walks, every modifier counted. */
    private static double speed(LivingEntity creature) {
        return creature.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /** A villager standing still at this block of the field: a creature anyone may bind, 1.95 blocks tall. */
    private static Villager villager(GameTestHelper helper, int x, int z) {
        return HollowPlayerTests.placed(helper, EntityType.VILLAGER, x, z);
    }

    /** The modifier with this id on this creature's attribute, or null: {@code HollowPlayerTests}'s, for a mob too. */
    @Nullable
    private static AttributeModifier modifier(LivingEntity creature, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = creature.getAttribute(attribute);
        return instance == null ? null : instance.getModifier(id);
    }

    private static void assertBind(GameTestHelper helper, KidoLogic.BindResult actual, KidoLogic.BindResult expected,
                                   String what) {
        helper.assertTrue(actual == expected, what + " gave " + actual + ", not " + expected);
    }

    /** The creature's motion, each part as the hold sets it. */
    private static void assertMotion(GameTestHelper helper, Entity entity, double x, double y, double z, String what) {
        Vec3 motion = entity.getDeltaMovement();
        helper.assertTrue(Math.abs(motion.x - x) < MOTION_EPSILON && Math.abs(motion.y - y) < MOTION_EPSILON
                && Math.abs(motion.z - z) < MOTION_EPSILON, what + " is " + motion + ", not " + new Vec3(x, y, z));
    }

    /**
     * Lifts both binds, every wary mark and every look a bind left off these: a mock player is not in the world, so
     * nothing else clears what a test laid on it.
     */
    private static void unbind(LivingEntity... creatures) {
        for (LivingEntity creature : creatures) {
            creature.removeEffect(ModEffects.BAKUDO_BOUND);
            creature.removeEffect(ModEffects.BAKUDO_HELD);
            creature.getPersistentData().remove(WARY_TAG);
            creature.getPersistentData().remove(BakudoEffect.LOOK_TAG);
        }
    }

    /** How a bind of this kind on this creature is drawn, which must be this style in this colour. */
    private static void assertDrawn(GameTestHelper helper, LivingEntity creature, boolean held, KidoSpell.BindStyle style,
                                    int colour, String what) {
        BakudoEffect.Drawn drawn = BakudoEffect.drawn(creature, held);
        helper.assertTrue(drawn.equals(new BakudoEffect.Drawn(style, colour)), what + " is drawn as " + drawn.style() + " in "
                + Integer.toHexString(drawn.colour()) + ", not " + style + " in " + Integer.toHexString(colour));
    }

    /** This mob's revenge goal, as its target selector holds it: what a blow sets hunting, and a smoke must stop. */
    private static WrappedGoal hurtBy(Mob mob) {
        return mob.targetSelector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof HurtByTargetGoal)
                .findFirst()
                .orElseThrow();
    }

    /** A mob's blow from this place in the world with no creature behind it, as a shield meets one. */
    private static DamageSource attackAt(GameTestHelper helper, Vec3 at) {
        return damageOf(helper, DamageTypes.MOB_ATTACK, at);
    }

    /** A blow of this damage type from this place in the world, or from no place for null, with no creature behind it. */
    private static DamageSource damageOf(GameTestHelper helper, ResourceKey<DamageType> type, @Nullable Vec3 at) {
        Holder<DamageType> holder = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type);
        return at == null ? new DamageSource(holder) : new DamageSource(holder, at);
    }

    /** This golem with this soul, so it may hold up a Kidō shield of its own, which a Hadō beam can meet. */
    private static IronGolem wearerOf(IronGolem golem, SoulData data) {
        golem.setData(SoulAttachments.SOUL, data);
        return golem;
    }

    /** What is left of this shield: nothing when there is none, as a broken one has nothing left. */
    private static float left(@Nullable KidoWard.Shield shield) {
        return shield == null ? 0.0F : shield.left();
    }

    /**
     * Drops the smoke and the shield of each of these, as their ticks drop them once their time is up: a mock player
     * is not in the world, so nothing else ends what a test raised.
     */
    private static void dropWards(Player... players) {
        for (Player player : players) {
            SoulData data = SoulLogic.get(player);
            KidoSmoke.tick(player, data, Long.MAX_VALUE);
            KidoWard.tick(player, data, Long.MAX_VALUE);
        }
    }

    /** Clears the mark a smoke left on each of these, so none is lost to a caster when it goes. */
    private static void unlose(LivingEntity... creatures) {
        for (LivingEntity creature : creatures) {
            if (creature != null) {
                creature.removeData(SoulAttachments.LOST);
            }
        }
    }

    /** Every Dankū wall in this test's structure. */
    private static List<DankuWall> walls(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(DankuWall.class, helper.getBounds());
    }

    /** Takes down every Dankū wall in this test's structure: a mock caster never goes, so its wall would stand its time. */
    private static void dropWalls(GameTestHelper helper) {
        walls(helper).forEach(Entity::discard);
    }

    /** Raises this caster's Dankū now at a share of 1, as the key would, 2 blocks east of {@link #LANE}; it must stand. */
    private static DankuWall raiseWall(GameTestHelper helper, Player caster, KidoSpell danku, long now) {
        DankuWall wall = DankuWall.raise(helper.getLevel(), caster, danku, 1.0F, now);
        helper.assertTrue(wall != null && wall.standing(), "Dankū raised no wall");
        return wall;
    }

    /** Makes this creature whole with its frames over, so a shot that lands shows, even one no bigger than the last. */
    private static void mend(LivingEntity creature) {
        creature.setHealth(creature.getMaxHealth());
        creature.invulnerableTime = 0;
    }

    /**
     * A shot of this kind through the Cero's engine, 10 strong, 12 blocks long and 0.5 wide, east from {@code from} by
     * this shooter, the golem down the lane mended first.
     */
    private static CeroBeam.Shot weighed(GameTestHelper helper, IronGolem golem, Player shooter, Vec3 from,
                                         CeroBeam.Kind kind) {
        mend(golem);
        CeroBeam.Spec spec = new CeroBeam.Spec(10.0F, 12.0, 0.5F, 0.0F, 0.0F, 0, 0.0F, 0xFFFFFF, 0, 0, false);
        return CeroBeam.fire(helper.getLevel(), shooter, null, from, new Vec3(1.0, 0.0, 0.0), spec, kind);
    }

    // ---- The spells as data ----

    @GameTest(template = "empty")
    public static void kidoSpellsLoadInOrderAndClamp(GameTestHelper helper) {
        Registry<KidoSpell> spells = spells(helper);
        helper.assertTrue(spells.containsKey(KidoSpell.SHO) && spells.containsKey(KidoSpell.BYAKURAI)
                && spells.containsKey(KidoSpell.SHAKKAHO) && spells.containsKey(KidoSpell.SOKATSUI)
                && spells.containsKey(KidoSpell.TENRAN) && spells.containsKey(KidoSpell.RAIKOHO)
                && spells.containsKey(KidoSpell.SAI) && spells.containsKey(KidoSpell.HAINAWA)
                && spells.containsKey(KidoSpell.GEKI) && spells.containsKey(KidoSpell.SEKIENTON)
                && spells.containsKey(KidoSpell.ENKOSEN) && spells.containsKey(KidoSpell.RIKUJOKORO)
                && spells.containsKey(KidoSpell.DANKU), "the shipped spells did not load: " + spells.keySet());
        // A data pack may add its own, so only the mod's are held to their order.
        List<ResourceKey<KidoSpell>> shipped = ids(KidoSpell.sorted(spells)).stream()
                .filter(key -> BleachMod.MODID.equals(key.location().getNamespace()))
                .toList();
        // Every Hadō before every Bakudō, each kind by its number.
        helper.assertTrue(shipped.equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI, KidoSpell.SHAKKAHO, KidoSpell.SOKATSUI,
                KidoSpell.TENRAN, KidoSpell.RAIKOHO, KidoSpell.SAI, KidoSpell.HAINAWA, KidoSpell.GEKI,
                KidoSpell.SEKIENTON, KidoSpell.ENKOSEN, KidoSpell.RIKUJOKORO, KidoSpell.DANKU)),
                "the mod's spells sort as " + shipped);

        KidoSpell sho = spell(helper, KidoSpell.SHO).value();
        assertShipped(helper, sho, "Shō", "kido.bleach.sho", 1, 0, 0xCFE6FF, KidoSpell.Kind.HADO, KidoSpell.Shape.BEAM);
        assertNear(helper, sho.costShare(), 0.02, "Shō's share of reiryoku");
        assertNear(helper, sho.minCost(), 1.0, "Shō's least cost");
        helper.assertTrue(sho.cooldownTicks() == 10 && sho.xp() == 1,
                "Shō waits " + sho.cooldownTicks() + " ticks and trains " + sho.xp() + " xp");
        helper.assertTrue(sho.effect().pierce() == 1, "Shō stops after " + sho.effect().pierce() + " creatures");
        assertNear(helper, sho.effect().knockback(), 1.2, "Shō's push");

        KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
        assertShipped(helper, byakurai, "Byakurai", "kido.bleach.byakurai", 4, 3, 0xEEF4FF, KidoSpell.Kind.HADO,
                KidoSpell.Shape.BEAM);
        assertNear(helper, byakurai.costShare(), 0.04, "Byakurai's share of reiryoku");
        assertNear(helper, byakurai.minCost(), 2.0, "Byakurai's least cost");
        helper.assertTrue(byakurai.cooldownTicks() == 20 && byakurai.xp() == 2,
                "Byakurai waits " + byakurai.cooldownTicks() + " ticks and trains " + byakurai.xp() + " xp");
        helper.assertTrue(byakurai.effect().pierce() == 2,
                "Byakurai stops after " + byakurai.effect().pierce() + " creatures");

        // The two with incantations: chanted from their level, and cast by name alone only from a higher one.
        KidoSpell shakkaho = spell(helper, KidoSpell.SHAKKAHO).value();
        assertChanted(helper, shakkaho, KidoSpell.SHAKKAHO.location(), "Shakkahō", "kido.bleach.shakkaho", 31, 5, 10, 30,
                0xE0301E, 2, KidoSpell.Kind.HADO, KidoSpell.Shape.BEAM);
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
                0x3A7BFF, 2, KidoSpell.Kind.HADO, KidoSpell.Shape.BEAM);
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

        // Tenran has no incantation either, and widens from the hand as a gust that blinds, weaker without a blade.
        KidoSpell tenran = spell(helper, KidoSpell.TENRAN).value();
        assertShipped(helper, tenran, "Tenran", "kido.bleach.tenran", 58, 18, TENRAN_COLOUR, KidoSpell.Kind.HADO,
                KidoSpell.Shape.GUST);
        assertNear(helper, tenran.costShare(), 0.1, "Tenran's share of reiryoku");
        assertNear(helper, tenran.minCost(), 6.0, "Tenran's least cost");
        helper.assertTrue(tenran.cooldownTicks() == 60 && tenran.xp() == 5,
                "Tenran waits " + tenran.cooldownTicks() + " ticks and trains " + tenran.xp() + " xp");
        helper.assertTrue(tenran.gatherTicks() == 0 && !tenran.gathers(), "Tenran gathers for " + tenran.gatherTicks()
                + " ticks");
        assertNear(helper, tenran.effect().range(), 8.0, "Tenran's reach");
        assertNear(helper, tenran.effect().angle(), 70.0, "Tenran's width");
        assertNear(helper, tenran.effect().knockback(), 1.0, "Tenran's push");
        assertNear(helper, tenran.effect().blindSeconds(), 2.0, "Tenran's blindness");
        assertNear(helper, tenran.effect().bareShare(), 0.7, "the share of Tenran cast with no blade at hand");

        // Raikōhō is chanted from its level, or cast by name from a higher one after gathering a second in the palm.
        KidoSpell raikoho = spell(helper, KidoSpell.RAIKOHO).value();
        assertChanted(helper, raikoho, KidoSpell.RAIKOHO.location(), "Raikōhō", "kido.bleach.raikoho", 63, 25, 32, 50,
                RAIKOHO_COLOUR, 3, KidoSpell.Kind.HADO, KidoSpell.Shape.BEAM);
        assertNear(helper, raikoho.costShare(), 0.18, "Raikōhō's share of reiryoku");
        assertNear(helper, raikoho.minCost(), 12.0, "Raikōhō's least cost");
        helper.assertTrue(raikoho.cooldownTicks() == 100 && raikoho.xp() == 6,
                "Raikōhō waits " + raikoho.cooldownTicks() + " ticks and trains " + raikoho.xp() + " xp");
        helper.assertTrue(raikoho.gatherTicks() == 20 && raikoho.gathers(), "Raikōhō gathers for "
                + raikoho.gatherTicks() + " ticks");
        assertNear(helper, raikoho.effect().range(), 32.0, "Raikōhō's reach");
        assertNear(helper, raikoho.effect().radius(), 0.5, "Raikōhō's half-width");
        helper.assertTrue(raikoho.effect().pierce() == 1, "Raikōhō stops after " + raikoho.effect().pierce() + " creatures");
        assertNear(helper, raikoho.effect().blastRadius(), 3.0, "Raikōhō's burst");
        assertNear(helper, raikoho.effect().blastShare(), 0.6, "the share of Raikōhō's burst");
        assertNear(helper, raikoho.effect().knockback(), 0.6, "Raikōhō's push");
        assertNear(helper, raikoho.effect().blindSeconds(), 0.0, "Raikōhō's blindness");
        assertNear(helper, raikoho.effect().bareShare(), 1.0, "the share of Raikōhō cast with no blade at hand");

        // Sai and Hainawa are tested as they ship in bakudoSpellsShipAndTheContestWeighs; here they load, sort and travel.
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell hainawa = spell(helper, KidoSpell.HAINAWA).value();

        // Geki and Rikujōkōrō are chanted from their level and cast by name only from a higher one, and bind the arms
        // and hold the body at once; neither does any damage, so a misfire of either hurts no one.
        KidoSpell geki = spell(helper, KidoSpell.GEKI).value();
        assertChanted(helper, geki, KidoSpell.GEKI.location(), "Geki", "kido.bleach.geki", 9, 7, 12, 30, GEKI_COLOUR, 2,
                KidoSpell.Kind.BAKUDO, KidoSpell.Shape.BIND);
        assertNear(helper, geki.costShare(), 0.06, "Geki's share of reiryoku");
        assertNear(helper, geki.minCost(), 3.0, "Geki's least cost");
        helper.assertTrue(geki.cooldownTicks() == 60 && geki.xp() == 3,
                "Geki waits " + geki.cooldownTicks() + " ticks and trains " + geki.xp() + " xp");
        assertNear(helper, geki.effect().range(), 10.0, "Geki's reach");
        assertNear(helper, geki.effect().radius(), 0.3, "Geki's half-width");
        assertNear(helper, geki.effect().boundSeconds(), 2.0, "Geki's bound arms");
        assertNear(helper, geki.effect().heldSeconds(), 2.0, "Geki's held body");
        KidoSpell rikujokoro = spell(helper, KidoSpell.RIKUJOKORO).value();
        assertChanted(helper, rikujokoro, KidoSpell.RIKUJOKORO.location(), "Rikujōkōrō", "kido.bleach.rikujokoro", 61, 22,
                28, 40, RIKUJOKORO_COLOUR, 2, KidoSpell.Kind.BAKUDO, KidoSpell.Shape.BIND);
        assertNear(helper, rikujokoro.costShare(), 0.15, "Rikujōkōrō's share of reiryoku");
        assertNear(helper, rikujokoro.minCost(), 10.0, "Rikujōkōrō's least cost");
        helper.assertTrue(rikujokoro.cooldownTicks() == 120 && rikujokoro.xp() == 5,
                "Rikujōkōrō waits " + rikujokoro.cooldownTicks() + " ticks and trains " + rikujokoro.xp() + " xp");
        assertNear(helper, rikujokoro.effect().range(), 16.0, "Rikujōkōrō's reach");
        assertNear(helper, rikujokoro.effect().radius(), 0.35, "Rikujōkōrō's half-width");
        assertNear(helper, rikujokoro.effect().boundSeconds(), 5.0, "Rikujōkōrō's bound arms");
        assertNear(helper, rikujokoro.effect().heldSeconds(), 5.0, "Rikujōkōrō's held body");
        SoulData master = shinigami(1_000.0F, 60);
        for (KidoSpell chanted : List.of(geki, rikujokoro)) {
            String what = "Bakudō #" + chanted.number();
            helper.assertTrue(chanted.gatherTicks() == 0 && !chanted.gathers(), what + " gathers for "
                    + chanted.gatherTicks() + " ticks");
            assertNear(helper, KidoLogic.power(chanted, master, 1.0F), 0.0, what + "'s damage");
            // A sound or particle that is not there is left out without a word, so each is looked up here.
            KidoSpell.Look look = chanted.look();
            helper.assertTrue(look.sound().isPresent() && BuiltInRegistries.SOUND_EVENT.containsKey(look.sound().get()),
                    what + " sounds as " + look.sound());
            helper.assertTrue(look.particle().flatMap(BuiltInRegistries.PARTICLE_TYPE::getOptional).orElse(null)
                    instanceof SimpleParticleType, what + " puffs " + look.particle());
        }

        // Sekienton, Enkōsen and Dankū have no incantation and strike nothing: a smoke left where the caster stood, a
        // shield held before it and a wall raised before it, each standing for its ward's time.
        KidoSpell sekienton = spell(helper, KidoSpell.SEKIENTON).value();
        assertShipped(helper, sekienton, "Sekienton", "kido.bleach.sekienton", 21, 8, SEKIENTON_COLOUR,
                KidoSpell.Kind.BAKUDO, KidoSpell.Shape.SMOKE);
        assertNear(helper, sekienton.costShare(), 0.06, "Sekienton's share of reiryoku");
        assertNear(helper, sekienton.minCost(), 3.0, "Sekienton's least cost");
        helper.assertTrue(sekienton.cooldownTicks() == 200 && sekienton.xp() == 3,
                "Sekienton waits " + sekienton.cooldownTicks() + " ticks and trains " + sekienton.xp() + " xp");
        assertNear(helper, sekienton.effect().radius(), 4.0, "the radius of Sekienton's cloud");
        assertNear(helper, sekienton.effect().blindSeconds(), 3.0, "Sekienton's blindness");
        KidoSpell.Ward smoke = sekienton.effect().ward();
        helper.assertTrue(smoke.seconds() == 4.0F && smoke.lostSeconds() == 3.0F && smoke.speedSeconds() == 2.0F
                && smoke.speedLevel() == 2, "Sekienton's ward is " + smoke + ", not 4 seconds, 3 lost and 2 of Speed II");
        KidoSpell enkosen = spell(helper, KidoSpell.ENKOSEN).value();
        assertShipped(helper, enkosen, "Enkōsen", "kido.bleach.enkosen", 39, 12, ENKOSEN_COLOUR, KidoSpell.Kind.BAKUDO,
                KidoSpell.Shape.SHIELD);
        assertNear(helper, enkosen.costShare(), 0.08, "Enkōsen's share of reiryoku");
        assertNear(helper, enkosen.minCost(), 4.0, "Enkōsen's least cost");
        helper.assertTrue(enkosen.cooldownTicks() == 160 && enkosen.xp() == 4,
                "Enkōsen waits " + enkosen.cooldownTicks() + " ticks and trains " + enkosen.xp() + " xp");
        assertNear(helper, enkosen.effect().damage(), 4.0, "Enkōsen's strength");
        assertNear(helper, enkosen.effect().damagePerRoot(), 0.3, "Enkōsen's strength per root of reiryoku");
        assertNear(helper, enkosen.effect().maxDamage(), 30.0, "Enkōsen's most strength");
        assertNear(helper, enkosen.effect().angle(), 120.0, "the arc Enkōsen covers");
        assertNear(helper, enkosen.effect().radius(), 0.8, "the ring Enkōsen is drawn as");
        KidoSpell.Ward shield = enkosen.effect().ward();
        helper.assertTrue(shield.seconds() == 3.0F && shield.lostSeconds() == 0.0F && shield.speedSeconds() == 0.0F
                && shield.speedLevel() == 0, "Enkōsen's ward is " + shield + ", not 3 seconds and nothing else");
        KidoSpell danku = spell(helper, KidoSpell.DANKU).value();
        assertShipped(helper, danku, "Dankū", "kido.bleach.danku", 81, 36, DANKU_COLOUR, KidoSpell.Kind.BAKUDO,
                KidoSpell.Shape.WALL);
        assertNear(helper, danku.costShare(), 0.2, "Dankū's share of reiryoku");
        assertNear(helper, danku.minCost(), 20.0, "Dankū's least cost");
        helper.assertTrue(danku.cooldownTicks() == 300 && danku.xp() == 6,
                "Dankū waits " + danku.cooldownTicks() + " ticks and trains " + danku.xp() + " xp");
        assertNear(helper, danku.effect().range(), 2.0, "how far before its caster Dankū rises");
        assertNear(helper, danku.effect().radius(), 2.5, "Dankū's half-width");
        KidoSpell.Ward wall = danku.effect().ward();
        helper.assertTrue(wall.seconds() == 6.0F && wall.lostSeconds() == 0.0F && wall.speedSeconds() == 0.0F
                && wall.speedLevel() == 0 && wall.height() == 4.0F,
                "Dankū's ward is " + wall + ", not 6 seconds and 4 blocks tall");
        for (KidoSpell warding : List.of(sekienton, enkosen, danku)) {
            String what = "Bakudō #" + warding.number();
            helper.assertTrue(warding.gatherTicks() == 0 && !warding.gathers(), what + " gathers for "
                    + warding.gatherTicks() + " ticks");
            KidoSpell.Look look = warding.look();
            helper.assertTrue(look.sound().isPresent() && BuiltInRegistries.SOUND_EVENT.containsKey(look.sound().get()),
                    what + " sounds as " + look.sound());
            helper.assertTrue(look.particle().flatMap(BuiltInRegistries.PARTICLE_TYPE::getOptional).orElse(null)
                    instanceof SimpleParticleType, what + " puffs " + look.particle());
            assertNear(helper, KidoLogic.misfireChance(warding, warding.level()), 0.0,
                    what + "'s misfire chance at Kidō " + warding.level());
            assertNear(helper, KidoLogic.share(warding, KidoLogic.Form.NAME, warding.level()), 1.0,
                    "the share of " + what + " cast by name at Kidō " + warding.level());
        }
        // Every other spell leaves its ward out, and reads none.
        for (KidoSpell other : List.of(sho, byakurai, shakkaho, sokatsui, tenran, raikoho, sai, hainawa, geki, rikujokoro)) {
            helper.assertTrue(other.effect().ward().equals(KidoSpell.Ward.NONE),
                    other.kind() + " #" + other.number() + " has the ward " + other.effect().ward());
        }

        // Each bind is drawn in its own way; Sai, Hainawa, the wards and every Hadō name none, and read the binds' own.
        helper.assertTrue(geki.look().bind() == KidoSpell.BindStyle.GLOW
                && rikujokoro.look().bind() == KidoSpell.BindStyle.RODS,
                "Geki is drawn as " + geki.look().bind() + " and Rikujōkōrō as " + rikujokoro.look().bind());
        for (KidoSpell plain : List.of(sho, byakurai, shakkaho, sokatsui, tenran, raikoho, sai, hainawa, sekienton,
                enkosen, danku)) {
            helper.assertTrue(plain.look().bind() == KidoSpell.BindStyle.DEFAULT,
                    plain.kind() + " #" + plain.number() + " is drawn as " + plain.look().bind());
        }
        // A style that is not known, or none at all, reads as the binds' own, and the rest of the look stays.
        for (String written : List.of("{\"colour\": \"#123456\", \"pitch\": 1.5, \"bind\": \"nonsense\"}",
                "{\"colour\": \"#123456\", \"pitch\": 1.5}")) {
            KidoSpell.Look look = KidoSpell.Look.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(written)).getOrThrow();
            helper.assertTrue(look.bind() == KidoSpell.BindStyle.DEFAULT && look.colour() == 0x123456
                    && Math.abs(look.pitch() - 1.5F) < 1.0E-6F, "the look " + written + " read as " + look);
        }

        // The spells written before gusts and gathers read the defaults, which change nothing for them.
        for (KidoSpell older : List.of(sho, byakurai, shakkaho, sokatsui)) {
            String what = "Hadō #" + older.number();
            helper.assertTrue(older.gatherTicks() == 0 && !older.gathers(), what + " gathers for " + older.gatherTicks()
                    + " ticks");
            assertNear(helper, older.effect().angle(), 60.0, what + "'s width");
            assertNear(helper, older.effect().blindSeconds(), 0.0, what + "'s blindness");
            assertNear(helper, older.effect().bareShare(), 1.0, "the share of " + what + " cast with no blade at hand");
        }
        // Nor does any Hadō bind or hold: the binds' times read their default of none.
        for (KidoSpell hado : List.of(sho, byakurai, shakkaho, sokatsui, tenran, raikoho)) {
            String what = "Hadō #" + hado.number();
            assertNear(helper, hado.effect().boundSeconds(), 0.0, what + "'s bound arms");
            assertNear(helper, hado.effect().heldSeconds(), 0.0, what + "'s held body");
        }

        List<ResourceKey<KidoSpell>> fourth = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.HADO, 4));
        helper.assertTrue(fourth.equals(List.of(KidoSpell.BYAKURAI)), "Hadō #4 is " + fourth);
        List<ResourceKey<KidoSpell>> thirtyFirst = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.HADO, 31));
        helper.assertTrue(thirtyFirst.equals(List.of(KidoSpell.SHAKKAHO)), "Hadō #31 is " + thirtyFirst);
        List<ResourceKey<KidoSpell>> fiftyEighth = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.HADO, 58));
        helper.assertTrue(fiftyEighth.equals(List.of(KidoSpell.TENRAN)), "Hadō #58 is " + fiftyEighth);
        List<ResourceKey<KidoSpell>> sixtyThird = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.HADO, 63));
        helper.assertTrue(sixtyThird.equals(List.of(KidoSpell.RAIKOHO)), "Hadō #63 is " + sixtyThird);
        List<ResourceKey<KidoSpell>> firstBakudo = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 1));
        helper.assertTrue(firstBakudo.equals(List.of(KidoSpell.SAI)), "Bakudō #1 is " + firstBakudo);
        List<ResourceKey<KidoSpell>> fourthBakudo = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 4));
        helper.assertTrue(fourthBakudo.equals(List.of(KidoSpell.HAINAWA)), "Bakudō #4 is " + fourthBakudo);
        List<ResourceKey<KidoSpell>> ninthBakudo = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 9));
        helper.assertTrue(ninthBakudo.equals(List.of(KidoSpell.GEKI)), "Bakudō #9 is " + ninthBakudo);
        List<ResourceKey<KidoSpell>> twentyFirstBakudo = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 21));
        helper.assertTrue(twentyFirstBakudo.equals(List.of(KidoSpell.SEKIENTON)), "Bakudō #21 is " + twentyFirstBakudo);
        List<ResourceKey<KidoSpell>> thirtyNinthBakudo = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 39));
        helper.assertTrue(thirtyNinthBakudo.equals(List.of(KidoSpell.ENKOSEN)), "Bakudō #39 is " + thirtyNinthBakudo);
        List<ResourceKey<KidoSpell>> sixtyFirstBakudo = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 61));
        helper.assertTrue(sixtyFirstBakudo.equals(List.of(KidoSpell.RIKUJOKORO)), "Bakudō #61 is " + sixtyFirstBakudo);
        List<ResourceKey<KidoSpell>> eightyFirstBakudo = ids(KidoSpell.byNumber(spells, KidoSpell.Kind.BAKUDO, 81));
        helper.assertTrue(eightyFirstBakudo.equals(List.of(KidoSpell.DANKU)), "Bakudō #81 is " + eightyFirstBakudo);

        // Every value wrong in its own way: each falls back or is clamped alone, and the rest of the spell stays.
        String broken = "{\"number\": -5, \"shape\": \"spiral\", \"level\": 7, \"by_name_level\": 2, \"cost_share\": 9,"
                + " \"cooldown_ticks\": 0, \"gather_ticks\": 1000,"
                + " \"incantations\": [\"\", \"" + "x".repeat(300) + "\", \"ok\"],"
                + " \"effect\": {\"range\": 1000, \"pierce\": 0, \"damage\": \"lots\", \"angle\": 400,"
                + " \"blind_seconds\": -1, \"bare_share\": 3, \"bound_seconds\": 100, \"held_seconds\": -1,"
                + " \"ward\": {\"seconds\": 100, \"lost_seconds\": -1, \"speed_seconds\": 40, \"speed_level\": 9,"
                + " \"height\": 9}}}";
        KidoSpell parsed = KidoSpell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(broken)).getOrThrow();
        helper.assertTrue(parsed.number() == 0, "a number of -5 read as " + parsed.number());
        helper.assertTrue(parsed.shape() == KidoSpell.Shape.NONE && !parsed.castable(),
                "an unknown shape read as " + parsed.shape());
        helper.assertTrue(parsed.level() == 7 && parsed.byNameLevel() == 7,
                "a level of 7 by name from 2 read as " + parsed.level() + " by name from " + parsed.byNameLevel());
        assertNear(helper, parsed.costShare(), 1.0, "a share of reiryoku of 9");
        helper.assertTrue(parsed.cooldownTicks() == 1, "a cooldown of 0 read as " + parsed.cooldownTicks());
        helper.assertTrue(parsed.gatherTicks() == 100 && parsed.gathers(),
                "a gather of 1000 ticks read as " + parsed.gatherTicks());
        helper.assertTrue(parsed.incantations().equals(List.of("ok")) && parsed.chantable(),
                "a blank, an over-long and a good incantation read as " + parsed.incantations());
        assertNear(helper, parsed.effect().range(), 128.0, "a reach of 1000");
        helper.assertTrue(parsed.effect().pierce() == 1, "a pierce of 0 read as " + parsed.effect().pierce());
        assertNear(helper, parsed.effect().damage(), 0.0, "a damage of \"lots\"");
        assertNear(helper, parsed.effect().angle(), 180.0, "a width of 400 degrees");
        assertNear(helper, parsed.effect().blindSeconds(), 0.0, "a blindness of -1 seconds");
        assertNear(helper, parsed.effect().bareShare(), 1.0, "a share of 3 with no blade at hand");
        assertNear(helper, parsed.effect().boundSeconds(), 60.0, "a bind of 100 seconds");
        assertNear(helper, parsed.effect().heldSeconds(), 0.0, "a hold of -1 seconds");
        KidoSpell.Ward clamped = parsed.effect().ward();
        assertNear(helper, clamped.seconds(), 60.0, "a ward of 100 seconds");
        assertNear(helper, clamped.lostSeconds(), 0.0, "a ward that loses for -1 seconds");
        assertNear(helper, clamped.speedSeconds(), 30.0, "a ward that quickens for 40 seconds");
        helper.assertTrue(clamped.speedLevel() == 5, "a Speed of level 9 read as " + clamped.speedLevel());
        assertNear(helper, clamped.height(), 4.0, "a wall 9 blocks tall");
        KidoSpell.Ward sunk = KidoSpell.Ward.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"height\": -1}"))
                .getOrThrow();
        assertNear(helper, sunk.height(), 0.0, "a wall -1 blocks tall");
        KidoSpell unwarded = KidoSpell.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"effect\": {\"ward\": \"nonsense\"}}")).getOrThrow();
        helper.assertTrue(unwarded.effect().ward().equals(KidoSpell.Ward.NONE),
                "a ward of \"nonsense\" read as " + unwarded.effect().ward());
        KidoSpell empty = KidoSpell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        helper.assertFalse(empty.castable(), "an empty spell file can be cast, as a " + empty.shape());
        KidoSpell gust = KidoSpell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"shape\": \"gust\"}"))
                .getOrThrow();
        helper.assertTrue(gust.shape() == KidoSpell.Shape.GUST && gust.castable(), "a gust read as a " + gust.shape());
        KidoSpell bind = KidoSpell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"shape\": \"bind\"}"))
                .getOrThrow();
        helper.assertTrue(bind.shape() == KidoSpell.Shape.BIND && bind.castable(), "a bind read as a " + bind.shape());
        for (KidoSpell.Shape standing : List.of(KidoSpell.Shape.SMOKE, KidoSpell.Shape.SHIELD, KidoSpell.Shape.WALL)) {
            String written = "{\"shape\": \"" + standing.getSerializedName() + "\"}";
            KidoSpell read = KidoSpell.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(written)).getOrThrow();
            helper.assertTrue(read.shape() == standing && read.castable(), written + " read as a " + read.shape());
        }

        // Sent to a client as the sync sends it: encoded with the world's registries to NBT, and read back.
        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (KidoSpell spell : List.of(sho, byakurai, shakkaho, sokatsui, tenran, raikoho, sai, hainawa, geki, sekienton,
                enkosen, rikujokoro, danku, parsed)) {
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
        // Sai is known from Kidō 1, and every Bakudō comes after every Hadō.
        data.setLevel(Stat.KIDO, 2);
        helper.assertTrue(ids(KidoLogic.known(data, spells)).equals(List.of(KidoSpell.SHO, KidoSpell.SAI)),
                "Kidō 2 knows " + ids(KidoLogic.known(data, spells)));
        data.setLevel(Stat.KIDO, 3);
        helper.assertTrue(ids(KidoLogic.known(data, spells)).equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI,
                KidoSpell.SAI)), "Kidō 3 knows " + ids(KidoLogic.known(data, spells)));
        assertRefusal(helper, next(player, now), null, "choosing at Kidō 3");
        helper.assertTrue(stored(data).equals(Optional.of(KidoSpell.BYAKURAI)), "Sneak and the key chose " + stored(data));
        helper.assertTrue(id(KidoLogic.next(data, spells)).equals(Optional.of(KidoSpell.SAI)),
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

        // Geki is known from Kidō 7 and comes after Hainawa, so the choice wraps round after it; by Kidō 22 Sekienton,
        // Enkōsen and Rikujōkōrō come after Geki, in their numbers' order. A soul of its own, so the choice saved below
        // is left as it is.
        SoulData chooser = shinigami(400.0F, 7);
        helper.assertTrue(ids(KidoLogic.known(chooser, spells)).equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI,
                KidoSpell.SHAKKAHO, KidoSpell.SAI, KidoSpell.HAINAWA, KidoSpell.GEKI)),
                "Kidō 7 knows " + ids(KidoLogic.known(chooser, spells)));
        KidoLogic.choose(chooser, KidoSpell.HAINAWA.location());
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.GEKI)),
                "after Hainawa at Kidō 7, the next spell is " + id(KidoLogic.next(chooser, spells)));
        KidoLogic.choose(chooser, KidoSpell.GEKI.location());
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.SHO)),
                "after Geki at Kidō 7, the next spell is " + id(KidoLogic.next(chooser, spells)));
        chooser.setLevel(Stat.KIDO, 22);
        helper.assertTrue(ids(KidoLogic.known(chooser, spells)).equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI,
                KidoSpell.SHAKKAHO, KidoSpell.SOKATSUI, KidoSpell.TENRAN, KidoSpell.SAI, KidoSpell.HAINAWA, KidoSpell.GEKI,
                KidoSpell.SEKIENTON, KidoSpell.ENKOSEN, KidoSpell.RIKUJOKORO)),
                "Kidō 22 knows " + ids(KidoLogic.known(chooser, spells)));
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.SEKIENTON)),
                "after Geki at Kidō 22, the next spell is " + id(KidoLogic.next(chooser, spells)));
        KidoLogic.choose(chooser, KidoSpell.SEKIENTON.location());
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.ENKOSEN)),
                "after Sekienton at Kidō 22, the next spell is " + id(KidoLogic.next(chooser, spells)));
        KidoLogic.choose(chooser, KidoSpell.ENKOSEN.location());
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.RIKUJOKORO)),
                "after Enkōsen at Kidō 22, the next spell is " + id(KidoLogic.next(chooser, spells)));
        KidoLogic.choose(chooser, KidoSpell.RIKUJOKORO.location());
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.SHO)),
                "after Rikujōkōrō at Kidō 22, the next spell is " + id(KidoLogic.next(chooser, spells)));
        // By Kidō 36 Dankū comes after Rikujōkōrō, the last Bakudō, and the choice wraps round after it.
        chooser.setLevel(Stat.KIDO, 36);
        helper.assertTrue(ids(KidoLogic.known(chooser, spells)).equals(List.of(KidoSpell.SHO, KidoSpell.BYAKURAI,
                KidoSpell.SHAKKAHO, KidoSpell.SOKATSUI, KidoSpell.TENRAN, KidoSpell.RAIKOHO, KidoSpell.SAI,
                KidoSpell.HAINAWA, KidoSpell.GEKI, KidoSpell.SEKIENTON, KidoSpell.ENKOSEN, KidoSpell.RIKUJOKORO,
                KidoSpell.DANKU)), "Kidō 36 knows " + ids(KidoLogic.known(chooser, spells)));
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.DANKU)),
                "after Rikujōkōrō at Kidō 36, the next spell is " + id(KidoLogic.next(chooser, spells)));
        KidoLogic.choose(chooser, KidoSpell.DANKU.location());
        helper.assertTrue(id(KidoLogic.next(chooser, spells)).equals(Optional.of(KidoSpell.SHO)),
                "after Dankū at Kidō 36, the next spell is " + id(KidoLogic.next(chooser, spells)));

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
        // Nor is its call alone, which the empty incantation followed by the call would otherwise be.
        helper.assertFalse(KidoLogic.hears(mute, BleachMod.id("test_chant"), "Hadō #99, Test chant!"),
                "the call alone was heard as the incantation of a spell whose incantation folds to nothing");

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
            assertChantEnds(helper, dead, KidoSpell.SHAKKAHO, now, () -> dead.setHealth(0.0F), "a Shinigami who died");
            NegacionLight placed = NegacionLight.place(level, held, null);
            light = placed;
            assertChantEnds(helper, held, KidoSpell.SHAKKAHO, now, () -> helper.assertTrue(placed != null
                    && held.startRiding(placed, true) && Negacion.held(held), "the mock Shinigami could not be held"),
                    "a Shinigami held by Negación");
            assertChantEnds(helper, taken, KidoSpell.SHAKKAHO, now, () -> helper.assertTrue(
                    VisoredEvents.startTakeover(taken, takenData, false, now) && VisoredLogic.takenOver(takenData),
                    "the rooted Hollow did not seize its host"), "a soul whose Hollow took its body");
            helper.assertTrue(ShikaiEvents.release(gatherer) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
            assertChantEnds(helper, gatherer, KidoSpell.SHAKKAHO, now, () -> helper.assertTrue(
                    ShikaiEvents.gather(gatherer) == ShikaiEvents.GatherResult.GATHERING, "released Zangetsu did not gather"),
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
            // The mock caster is not in the level, so no burst could find it: the rule that spares it is asked
            // directly, and its health shows only that the cast did not misfire.
            helper.assertFalse(CeroBeam.mayStrike(player, player), "a Hadō's burst may strike its own caster");
            HollowPlayerTests.assertClose(helper, player.getHealth(), 20.0, "the caster's health after a cast at roll 1");
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

    // ---- Tenran ----

    /**
     * No wall here: a pig stands where it would go, and the gust ends at x 10.1, short of the field's edge. Nothing behind
     * or beside the hand can be in a cone 70° wide pointing east.
     */
    @GameTest(template = "field")
    public static void tenranBlowsAWideningCone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(4, y, 7, Blocks.STONE);
        }
        // At Kidō 18, Tenran's own level, with no zanpakutō at hand.
        Player player = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 18));
        SoulData data = SoulLogic.get(player);
        KidoLogic.choose(data, KidoSpell.TENRAN.location());
        downTheLane(player);
        Pig near = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 8);
        Pig wide = HollowPlayerTests.placed(helper, EntityType.PIG, 7, 11);
        Pig aside = HollowPlayerTests.placed(helper, EntityType.PIG, 5, 13);
        Pig far = HollowPlayerTests.placed(helper, EntityType.PIG, 11, 8);
        Pig behind = HollowPlayerTests.placed(helper, EntityType.PIG, 6, 6);
        try {
            // From the hand, 70° wide and 8 blocks long: the near pig is 2.5 blocks out at 17° and the wide one 6.0 out
            // at 26°. The pig aside is at 54°, the far one 9.0 blocks out, and the one behind the column, at 21°, is in
            // the cone but not in the open.
            List<LivingEntity> reached = CeroBeam.inCone(level, player, CeroBeam.muzzleOf(player, 1.0F),
                    player.getLookAngle(), 8.0, 70.0F);
            helper.assertTrue(reached.equals(List.of(near, wide)), "Tenran's cone holds " + reached);

            // 1.5 and 0.08 per root of 400, 36% stronger at Kidō 18: 4.216, of which a bare hand keeps 70%, 2.951.
            assertRefusal(helper, press(player, now), null, "Tenran by name at Kidō 18");
            assertNear(helper, near.getHealth(), 7.049, "the near pig's health after Tenran");
            assertNear(helper, wide.getHealth(), 7.049, "the wide pig's health after Tenran");
            for (Pig pig : List.of(aside, far, behind)) {
                helper.assertTrue(unhurt(pig), "Tenran hurt a pig outside its cone, at " + pig.getHealth());
            }
            // The blow's own 0.4 toward the hand, halved, then 70% of Tenran's push of 1 away from the caster.
            Vec3 pushed = near.getDeltaMovement();
            assertNear(helper, pushed.x, 0.9, PUSH_EPSILON, "the near pig's push east");
            assertNear(helper, pushed.z, 0.0, PUSH_EPSILON, "the near pig's push north or south");
            Vec3 outward = wide.getDeltaMovement();
            helper.assertTrue(outward.x > 0.6 && outward.z > 0.3, "the wide pig was pushed " + outward
                    + ", not away from the caster");
            for (Pig pig : List.of(near, wide, aside, far, behind)) {
                helper.assertTrue(blindness(pig) == null, "Tenran blinded a pig");
            }
            helper.assertTrue(player.getLastHurtMob() == wide, "the caster last hurt " + player.getLastHurtMob());
            DamageSource source = near.getLastDamageSource();
            helper.assertTrue(source != null && source.is(KidoEvents.HADO) && source.getEntity() == player
                    && source.getDirectEntity() != null && !(source.getDirectEntity() instanceof LivingEntity),
                    "the near pig was struck by " + source);
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 360.0, "the reiryoku after Tenran");
            helper.assertFalse(KidoLogic.ready(data, now + 59L), "the hands were free a tick before Tenran's cooldown");
            helper.assertTrue(KidoLogic.ready(data, now + 60L), "the hands were not free after Tenran's cooldown");
            helper.assertTrue(data.xp(Stat.KIDO) == 6, "Tenran on two pigs trained " + data.xp(Stat.KIDO)
                    + " Kidō xp, not 5 and 1");
            for (int y = 2; y <= 4; y++) {
                helper.assertBlockPresent(Blocks.STONE, 4, y, 7);
            }
            helper.assertFalse(level.getEntitiesOfClass(CeroBeam.class, helper.getBounds(),
                    beam -> beam.isFired() && beam.colour() == TENRAN_COLOUR).isEmpty(), "Tenran drew no trace");

            assertRefusal(helper, press(player, now + 1L), KidoLogic.Refusal.COOLDOWN, "Tenran again a tick later");
        } finally {
            discard(near, wide, aside, far, behind);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void tenranIsWeakerWithoutABlade(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell tenran = spell(helper, KidoSpell.TENRAN).value();
        KidoSpell byakurai = spell(helper, KidoSpell.BYAKURAI).value();
        KidoSpell raikoho = spell(helper, KidoSpell.RAIKOHO).value();
        Player player = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 18));
        SoulData data = SoulLogic.get(player);
        KidoLogic.choose(data, KidoSpell.TENRAN.location());
        downTheLane(player);
        SoulData lent = shinigami(400.0F, 18);
        ShinigamiLogic.transferFromShinigami(lent, ShinigamiTests.soul(SoulState.HUMAN, 100.0F), now);
        lent.setReiryoku(lent.maxReiryoku());
        KidoLogic.choose(lent, KidoSpell.TENRAN.location());
        Player lender = caster(helper, GameType.SURVIVAL, lent);
        Player novice = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 17));
        Player creative = caster(helper, GameType.CREATIVE, shinigami(400.0F, 18));
        SoulData creativeData = SoulLogic.get(creative);
        downTheLane(creative);
        Pig pig = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 8);
        Pig again = null;
        try {
            // A zanpakutō is at hand in the off hand or on the hotbar, the main hand among it, and not deeper in the
            // inventory; no other sword is one.
            Player bare = helper.makeMockPlayer(GameType.SURVIVAL);
            Player sword = helper.makeMockPlayer(GameType.SURVIVAL);
            sword.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            Player packed = helper.makeMockPlayer(GameType.SURVIVAL);
            packed.getInventory().setItem(20, ZanpakutoTests.asauchi());
            Player offHand = helper.makeMockPlayer(GameType.SURVIVAL);
            offHand.setItemInHand(InteractionHand.OFF_HAND, ZanpakutoTests.asauchi());
            Player sheathed = helper.makeMockPlayer(GameType.SURVIVAL);
            sheathed.getInventory().setItem(5, ZanpakutoTests.asauchi());
            Player drawn = helper.makeMockPlayer(GameType.SURVIVAL);
            drawn.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
            assertNear(helper, KidoLogic.handShare(tenran, bare), 0.7, "Tenran's share with empty hands");
            assertNear(helper, KidoLogic.handShare(tenran, sword), 0.7, "Tenran's share with an iron sword in the hand");
            assertNear(helper, KidoLogic.handShare(tenran, packed), 0.7, "Tenran's share with an Asauchi off the hotbar");
            assertNear(helper, KidoLogic.handShare(tenran, offHand), 1.0, "Tenran's share with an Asauchi in the off hand");
            assertNear(helper, KidoLogic.handShare(tenran, sheathed), 1.0,
                    "Tenran's share with an Asauchi on the hotbar, not chosen");
            assertNear(helper, KidoLogic.handShare(tenran, drawn), 1.0, "Tenran's share with an Asauchi in the hand");
            assertNear(helper, KidoLogic.handShare(byakurai, bare), 1.0, "Byakurai's share with empty hands");
            assertNear(helper, KidoLogic.handShare(raikoho, bare), 1.0, "Raikōhō's share with empty hands");
            // With no incantation, it never misfires and keeps all of itself by name, and its call is only chat.
            assertNear(helper, KidoLogic.misfireChance(tenran, 18), 0.0, "Tenran's chance to misfire at Kidō 18");
            assertNear(helper, KidoLogic.share(tenran, KidoLogic.Form.NAME, 18), 1.0, "the share of Tenran by name");
            helper.assertFalse(tenran.chantable(), "Tenran can be chanted: " + tenran.incantations());
            heardAs(helper, KidoEvents.chant(player, "Hadō #58, Tenran!", now), KidoEvents.Heard.Outcome.PLAIN, null,
                    null, "Tenran's call");

            // An Asauchi in the hand: the whole 4.216, and the blow's 0.4 halved and all of the push of 1.
            player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
            assertRefusal(helper, press(player, now), null, "Tenran with an Asauchi in the hand");
            assertNear(helper, pig.getHealth(), 5.784, "the pig's health after Tenran with a blade at hand");
            assertNear(helper, pig.getDeltaMovement().x, 1.2, PUSH_EPSILON, "the pig's push from Tenran with a blade");

            // The hand empty again: 70% of the blow and of the push.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            discard(pig);
            again = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 8);
            assertRefusal(helper, press(player, now + 60L), null, "Tenran with empty hands");
            assertNear(helper, again.getHealth(), 7.049, "the pig's health after Tenran with no blade at hand");
            assertNear(helper, again.getDeltaMovement().x, 0.9, PUSH_EPSILON, "the pig's push from Tenran with no blade");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 320.0, "the reiryoku after Tenran twice");

            // A lender may not cast it by name, a soul below its level may not cast it at all, and creative pays,
            // waits and trains nothing. The creative gust blows down the empty lane.
            assertRefusal(helper, press(lender, now), KidoLogic.Refusal.LENT,
                    "the Tenran of a Shinigami whose power is lent");
            HollowPlayerTests.assertClose(helper, lent.reiryoku(), 200.0, "the lender's reiryoku");
            assertRefusal(helper, KidoEvents.cast(novice, tenran, KidoLogic.Form.NAME, now), KidoLogic.Refusal.NEEDS_LEVEL,
                    "Tenran at Kidō 17");
            discard(again);
            assertRefusal(helper, KidoEvents.cast(creative, tenran, KidoLogic.Form.NAME, now), null,
                    "a creative Shinigami's Tenran");
            HollowPlayerTests.assertClose(helper, creativeData.reiryoku(), 400.0, "a creative caster's reiryoku");
            helper.assertTrue(creativeData.xp(Stat.KIDO) == 0, "a creative caster trained " + creativeData.xp(Stat.KIDO)
                    + " Kidō xp");
            assertWallStands(helper);
        } finally {
            discard(pig, again);
            discardBeams(helper);
        }
        helper.succeed();
    }

    /** Nothing is cast here: each blow is given directly, since no cone can find a mock player. */
    @GameTest(template = "field")
    public static void tenranBlindsThePlayersItReaches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KidoSpell tenran = spell(helper, KidoSpell.TENRAN).value();
        KidoSpell raikoho = spell(helper, KidoSpell.RAIKOHO).value();
        Player player = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 18));
        SoulData data = SoulLogic.get(player);
        downTheLane(player);
        Player even = victim(helper, GameType.SURVIVAL, 400.0F);
        Player strong = victim(helper, GameType.SURVIVAL, 4_000.0F);
        Player reeling = victim(helper, GameType.SURVIVAL, 400.0F);
        Player held = victim(helper, GameType.SURVIVAL, 400.0F);
        Player creative = victim(helper, GameType.CREATIVE, 400.0F);
        NegacionLight light = NegacionLight.place(level, held, null);
        Pig pig = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 10);
        // As the gust strikes: from a marker at the hand, never added to the world, caused by the caster.
        Marker marker = new Marker(EntityType.MARKER, level);
        marker.setPos(CeroBeam.muzzleOf(player, 1.0F));
        DamageSource source = CeroBeam.sourceOf(level, player, marker, new CeroBeam.Kind(KidoEvents.HADO, true, 1));
        float damage = KidoLogic.power(tenran, data, 0.7F);
        try {
            assertNear(helper, damage, 2.951, "Tenran's blow with no blade at hand");
            helper.assertTrue(light != null && held.startRiding(light, true) && Negacion.held(held),
                    "the mock Shinigami could not be held");

            // As strong as the caster: the whole blow, 70% of 2 seconds' blindness, and the push.
            helper.assertTrue(KidoEvents.gustBlow(player, even, source, tenran, 0.7F, damage),
                    "Tenran's blow did not land on a soul as strong as the caster");
            assertNear(helper, even.getHealth(), 17.049, HEALTH_EPSILON, "the even soul's health");
            MobEffectInstance blind = blindness(even);
            helper.assertTrue(blind != null && blind.getDuration() == 28 && blind.getAmplifier() == 0
                    && !blind.isVisible() && blind.showIcon() && !blind.isAmbient(),
                    "Tenran blinded the even soul with " + blind + ", not 28 ticks with an icon and no swirl");
            assertNear(helper, even.getDeltaMovement().x, 0.9, PUSH_EPSILON, "the even soul's push");

            // Ten times the caster's reiatsu: the gap lets 3 parts in 10 of the blow, the push and the blindness through.
            assertNear(helper, KidoLogic.gap(player, strong, source), 0.3, "the gap to a soul of ten times the reiatsu");
            helper.assertTrue(KidoEvents.gustBlow(player, strong, source, tenran, 0.7F, damage),
                    "Tenran's blow did not land on the strong soul");
            assertNear(helper, strong.getHealth(), 19.115, HEALTH_EPSILON, "the strong soul's health");
            MobEffectInstance dimmed = blindness(strong);
            helper.assertTrue(dimmed != null && dimmed.getDuration() == 8,
                    "Tenran blinded the strong soul with " + dimmed + ", not 8 ticks");
            assertNear(helper, strong.getDeltaMovement().x, 0.41, PUSH_EPSILON, "the strong soul's push");

            // Inside the frames of a stronger blow the gust does not land, so it pushes nothing; it blinds all the same.
            land(helper, reeling, reeling.damageSources().generic(), 5.0F, "the blow of 5");
            reeling.setDeltaMovement(Vec3.ZERO);
            helper.assertFalse(KidoEvents.gustBlow(player, reeling, source, tenran, 0.7F, damage),
                    "Tenran's blow landed inside the frames of a stronger one");
            HollowPlayerTests.assertClose(helper, reeling.getHealth(), 15.0, "the reeling soul's health");
            MobEffectInstance screened = blindness(reeling);
            helper.assertTrue(screened != null && screened.getDuration() == 28,
                    "Tenran blinded the reeling soul with " + screened + ", not 28 ticks");
            helper.assertTrue(reeling.getDeltaMovement().lengthSqr() == 0.0,
                    "Tenran pushed the reeling soul " + reeling.getDeltaMovement());

            // Negación's hold and creative spare a soul wholly.
            helper.assertFalse(KidoEvents.gustBlow(player, held, source, tenran, 0.7F, damage),
                    "Tenran's blow landed on a soul Negación holds");
            helper.assertTrue(blindness(held) == null, "Tenran blinded a soul Negación holds");
            HollowPlayerTests.assertClose(helper, held.getHealth(), 20.0, "the held soul's health");
            helper.assertFalse(KidoEvents.gustBlow(player, creative, source, tenran, 0.7F, damage),
                    "Tenran's blow landed on a creative player");
            helper.assertTrue(blindness(creative) == null, "Tenran blinded a creative player");

            // A creature without a soul feels it all, and only a player is blinded.
            helper.assertTrue(KidoEvents.gustBlow(player, pig, source, tenran, 0.7F, damage),
                    "Tenran's blow did not land on the pig");
            helper.assertTrue(blindness(pig) == null, "Tenran blinded the pig");
            assertNear(helper, KidoLogic.gap(player, pig, source), 1.0, "the gap to a creature without a soul");

            // 2 seconds at the share and through the gap, rounded; Raikōhō blinds no one.
            helper.assertTrue(KidoLogic.blindTicks(tenran, 1.0F, 1.0F) == 40, "Tenran's whole blindness is "
                    + KidoLogic.blindTicks(tenran, 1.0F, 1.0F) + " ticks");
            helper.assertTrue(KidoLogic.blindTicks(tenran, 0.7F, 1.0F) == 28, "Tenran's bare-handed blindness is "
                    + KidoLogic.blindTicks(tenran, 0.7F, 1.0F) + " ticks");
            helper.assertTrue(KidoLogic.blindTicks(tenran, 0.7F, 0.3F) == 8, "Tenran's bare-handed blindness through a"
                    + " gap of 0.3 is " + KidoLogic.blindTicks(tenran, 0.7F, 0.3F) + " ticks");
            helper.assertTrue(KidoLogic.blindTicks(tenran, 1.0F, 0.0F) == 0, "Tenran's blindness through no gap is "
                    + KidoLogic.blindTicks(tenran, 1.0F, 0.0F) + " ticks");
            helper.assertTrue(KidoLogic.blindTicks(raikoho, 1.0F, 1.0F) == 0, "Raikōhō blinds for "
                    + KidoLogic.blindTicks(raikoho, 1.0F, 1.0F) + " ticks");
        } finally {
            held.stopRiding();
            discard(light, pig, marker);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- Raikōhō and the key gather ----

    @GameTest(template = "field")
    public static void raikohoGathersByKeyThenStrikes(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        Player player = gatherer(helper);
        SoulData data = SoulLogic.get(player);
        IronGolem golem = golem(helper, 6, 8);
        IronGolem chanted = null;
        try {
            // Pressed, it gathers in the palm for a second, shown by an orb, with nothing spent, cooled or trained.
            assertRefusal(helper, press(player, now), null, "Raikōhō's key at Kidō 42");
            KidoLogic.Gather gather = gathering(data);
            helper.assertTrue(gather != null && gather.id().equals(KidoSpell.RAIKOHO.location())
                    && gather.firesAt() == now + 20L, "Raikōhō gathers as " + gather + ", not until " + (now + 20L));
            CeroBeam orb = orbOf(helper, data, "Raikōhō's key");
            helper.assertTrue(orb.colour() == RAIKOHO_COLOUR && orb.chargeTicks() == 20 + KidoEvents.ORB_SLACK_TICKS,
                    "Raikōhō's orb is " + Integer.toHexString(orb.colour()) + " for " + orb.chargeTicks() + " ticks");
            List<CeroBeam> ceros = helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds());
            helper.assertTrue(ceros.equals(List.of(orb)), "the Ceros about Raikōhō's gather are " + ceros);
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 1_000.0, "the reiryoku once Raikōhō began to gather");
            helper.assertTrue(KidoLogic.ready(data, now), "beginning to gather kept the hands busy");
            HollowPlayerTests.assertClose(helper, golem.getHealth(), 100.0, "the golem's health as Raikōhō gathers");

            // One art at a time: the key again and a chant wait for it, and choosing goes on.
            assertRefusal(helper, press(player, now + 1L), KidoLogic.Refusal.GATHERING, "the Kidō key while gathering");
            helper.assertTrue(gathering(data) == gather, "the key again changed the gather to " + gathering(data));
            heardAs(helper, KidoEvents.chant(player, romaji(helper, KidoSpell.RAIKOHO), now + 2L),
                    KidoEvents.Heard.Outcome.REFUSED, KidoSpell.RAIKOHO, KidoLogic.Refusal.GATHERING,
                    "Raikōhō's romaji while it gathers");
            helper.assertTrue(pending(data) == null, "a chant began over the gather: " + pending(data));
            assertRefusal(helper, next(player, now + 3L), null, "Sneak and the key while gathering");
            // Sai, the first Bakudō, comes next after the last Hadō.
            helper.assertTrue(stored(data).equals(Optional.of(KidoSpell.SAI)),
                    "Sneak and the key after Raikōhō chose " + stored(data));
            helper.assertTrue(gathering(data) == gather, "choosing while gathering left the gather as " + gathering(data));

            // A tick short of its time it still gathers.
            KidoEvents.tick(player, data, now + 19L);
            helper.assertTrue(gathering(data) == gather, "a tick short of its time, the gather is " + gathering(data));
            HollowPlayerTests.assertClose(helper, golem.getHealth(), 100.0, "the golem's health a tick short of the gather");

            // On time it goes off by name: 8 and 0.5 per root of 1,000, 84% stronger at Kidō 42, of which 70% is
            // 30.669, paid for, cooled and trained with the strike bonus.
            KidoEvents.tick(player, data, now + 20L);
            helper.assertTrue(gathering(data) == null, "the gather was kept once it went off: " + gathering(data));
            helper.assertTrue(orb.isRemoved(), "Raikōhō's orb outlived its gather");
            assertNear(helper, golem.getHealth(), 69.331, HEALTH_EPSILON, "the golem's health after Raikōhō by name");
            DamageSource source = golem.getLastDamageSource();
            helper.assertTrue(source != null && source.is(KidoEvents.HADO) && source.getEntity() == player,
                    "the golem was struck by " + source);
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 820.0, "the reiryoku after Raikōhō by name");
            helper.assertFalse(KidoLogic.ready(data, now + 119L), "the hands were free a tick before Raikōhō's cooldown");
            helper.assertTrue(KidoLogic.ready(data, now + 120L), "the hands were not free after Raikōhō's cooldown");
            helper.assertTrue(data.xp(Stat.KIDO) == 7, "Raikōhō by name on the golem trained " + data.xp(Stat.KIDO)
                    + " Kidō xp, not 6 and 1");
            helper.assertTrue(charging(helper).isEmpty(), "an orb is still charging: " + charging(helper));

            // Let go once it has gone off, the key does nothing.
            assertRefusal(helper, release(player, now + 25L), null, "the Kidō key let go after Raikōhō went off");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 820.0, "the reiryoku once the key was let go");

            // Chanted in English once the hands are free: a chant shows no orb in the palm.
            discard(golem);
            chanted = golem(helper, 6, 8);
            heardAs(helper, KidoEvents.chant(player, english(helper, KidoSpell.RAIKOHO), now + 120L),
                    KidoEvents.Heard.Outcome.STARTED, KidoSpell.RAIKOHO, null, "Raikōhō's English");
            KidoLogic.Chant chant = pending(data);
            helper.assertTrue(chant != null && chant.firesAt() == now + 170L,
                    "the English chant goes off as " + chant + ", not at " + (now + 170L));
            helper.assertTrue(charging(helper).isEmpty(), "the chant shows an orb: " + charging(helper));

            // Over a chant, the key on Raikōhō gathers nothing.
            KidoLogic.choose(data, KidoSpell.RAIKOHO.location());
            helper.assertTrue(stored(data).equals(Optional.of(KidoSpell.RAIKOHO)), "the choice is " + stored(data));
            assertRefusal(helper, press(player, now + 121L), KidoLogic.Refusal.CHANTING, "Raikōhō's key while chanting");
            helper.assertTrue(gathering(data) == null, "the key gathered over a chant: " + gathering(data));
            helper.assertTrue(charging(helper).isEmpty(), "the key over a chant showed an orb: " + charging(helper));
            helper.assertTrue(pending(data) == chant, "the key over a chant changed it to " + pending(data));

            // Chanted, at full strength, 43.813, and trained twice over.
            KidoEvents.tick(player, data, now + 170L);
            helper.assertTrue(pending(data) == null, "the English chant was still pending once it went off");
            assertNear(helper, chanted.getHealth(), 56.187, HEALTH_EPSILON, "the golem's health after the chanted Raikōhō");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 640.0, "the reiryoku after the chanted Raikōhō");
            helper.assertTrue(data.xp(Stat.KIDO) == 21, "the chanted Raikōhō on the golem left " + data.xp(Stat.KIDO)
                    + " Kidō xp, not 7 and twice 7");
        } finally {
            KidoEvents.dropGather(player, data);
            discard(golem, chanted);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void raikohoMisfiresOnlyWhenItGoesOff(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell raikoho = spell(helper, KidoSpell.RAIKOHO).value();
        // At Kidō 32, Raikōhō's by-name level: half strength and a 30% chance to misfire.
        Player player = caster(helper, GameType.SURVIVAL, shinigami(1_000.0F, 32));
        SoulData data = SoulLogic.get(player);
        player.setHealth(20.0F);
        KidoLogic.choose(data, KidoSpell.RAIKOHO.location());
        downTheLane(player);
        IronGolem golem = golem(helper, 6, 8);
        try {
            assertNear(helper, KidoLogic.misfireChance(raikoho, 32), 0.3, "Raikōhō's chance to misfire at Kidō 32");
            assertNear(helper, KidoLogic.misfireChance(raikoho, 37), 0.15, "Raikōhō's chance to misfire at Kidō 37");
            assertNear(helper, KidoLogic.misfireChance(raikoho, 42), 0.0, "Raikōhō's chance to misfire at Kidō 42");
            assertNear(helper, KidoLogic.share(raikoho, KidoLogic.Form.NAME, 32), 0.5,
                    "the share of Raikōhō by name at Kidō 32");

            // Nothing is rolled as the key is pressed: a sure misfire then changes nothing.
            withConfig(BleachConfig.KIDO_MISFIRE_CHANCE, 1.0, () -> assertRefusal(helper, press(player, now), null,
                    "Raikōhō's key with a misfire certain"));
            helper.assertTrue(gathering(data) != null, "Raikōhō did not gather with a misfire certain");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 1_000.0, "the reiryoku once Raikōhō began to gather");
            // Rolled as it goes off, with no chance: it strikes at half strength, 19.525.
            withConfig(BleachConfig.KIDO_MISFIRE_CHANCE, 0.0, () -> KidoEvents.tick(player, data, now + 20L));
            helper.assertTrue(gathering(data) == null, "the gather was kept once it went off: " + gathering(data));
            assertNear(helper, golem.getHealth(), 80.475, HEALTH_EPSILON, "the golem's health after Raikōhō by name");
            HollowPlayerTests.assertClose(helper, player.getHealth(), 20.0, "the caster's health after Raikōhō by name");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 820.0, "the reiryoku after Raikōhō by name");
            helper.assertTrue(data.xp(Stat.KIDO) == 7, "Raikōhō by name on the golem trained " + data.xp(Stat.KIDO)
                    + " Kidō xp, not 6 and 1");

            // Gathered with no chance and gone off with a certain one: it bursts in the hand, paid for, and a quarter
            // of its 19.525 hurts the caster.
            withConfig(BleachConfig.KIDO_MISFIRE_CHANCE, 0.0, () -> assertRefusal(helper, press(player, now + 120L), null,
                    "Raikōhō's key with no chance to misfire"));
            CeroBeam orb = orbOf(helper, data, "Raikōhō's second key");
            withConfig(BleachConfig.KIDO_MISFIRE_CHANCE, 1.0, () -> KidoEvents.tick(player, data, now + 140L));
            helper.assertTrue(gathering(data) == null, "the misfired gather was kept: " + gathering(data));
            helper.assertTrue(orb.isRemoved(), "the misfired Raikōhō's orb outlived its gather");
            assertNear(helper, golem.getHealth(), 80.475, HEALTH_EPSILON, "the golem's health after the misfire");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 640.0, "the reiryoku after the misfire");
            assertNear(helper, player.getHealth(), 15.119, HEALTH_EPSILON, "the caster's health after the misfire");
            helper.assertFalse(KidoLogic.ready(data, now + 239L), "the hands were free a tick before the misfire's cooldown");
            helper.assertTrue(data.xp(Stat.KIDO) == 7, "the misfire trained " + (data.xp(Stat.KIDO) - 7) + " Kidō xp");
            assertWallStands(helper);
        } finally {
            KidoEvents.dropGather(player, data);
            discard(golem);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void whatBreaksOrScattersAGather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 3);
        // Its soul made now, so the reiatsu gap weighs its blow alike each time.
        spider.soul();
        Player player = gatherer(helper);
        SoulData data = SoulLogic.get(player);
        Player held = gatherer(helper);
        NegacionLight light = null;
        Player taken = gatherer(helper);
        SoulData takenData = SoulLogic.get(taken);
        VisoredLogic.setRecord(takenData, VisoredLogic.record(takenData).withRootedAt(now - DAY));
        Player zangetsu = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData zangetsuData = SoulLogic.get(zangetsu);
        Player sitting = gatherer(helper);
        SoulData sittingData = SoulLogic.get(sitting);
        Player seated = gatherer(helper);
        SoulData seatedData = SoulLogic.get(seated);
        try {
            // Let go too soon, it scatters, with nothing spent, cooled or trained.
            assertRefusal(helper, press(player, now), null, "the first key");
            CeroBeam orb = orbOf(helper, data, "the first key");
            assertRefusal(helper, release(player, now + 5L), KidoLogic.Refusal.SCATTERED,
                    "the Kidō key let go a quarter of a second in");
            helper.assertTrue(gathering(data) == null && orb.isRemoved(), "the gather let go too soon was kept");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 1_000.0, "the reiryoku once the gather scattered");
            helper.assertTrue(KidoLogic.ready(data, now + 6L), "a scattered gather kept the hands busy");
            helper.assertTrue(data.xp(Stat.KIDO) == 0, "a scattered gather trained " + data.xp(Stat.KIDO) + " Kidō xp");

            // A screen opened over the held key scatters it too; with nothing gathering it means nothing.
            assertRefusal(helper, press(player, now + 10L), null, "the second key");
            orb = orbOf(helper, data, "the second key");
            assertRefusal(helper, cancel(player, now + 11L), KidoLogic.Refusal.SCATTERED,
                    "a screen opened while Raikōhō gathered");
            helper.assertTrue(gathering(data) == null && orb.isRemoved(), "the gather under a screen was kept");
            assertRefusal(helper, cancel(player, now + 11L), null, "a screen opened with nothing gathering");

            // A creature's blow that lands breaks it.
            assertRefusal(helper, press(player, now + 20L), null, "the third key");
            orb = orbOf(helper, data, "the third key");
            land(helper, player, player.damageSources().mobAttack(spider), 2.0F, "the Spider Hollow's blow");
            helper.assertTrue(gathering(data) == null && orb.isRemoved(), "the Spider Hollow's blow left the gather");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 1_000.0, "the reiryoku once the blow broke the gather");

            // Fire and magic come from no creature; a portal drops it.
            assertRefusal(helper, press(player, now + 30L), null, "the fourth key");
            orb = orbOf(helper, data, "the fourth key");
            land(helper, player, player.damageSources().onFire(), 1.0F, "the fire");
            land(helper, player, player.damageSources().magic(), 1.0F, "the magic");
            helper.assertTrue(gathering(data) != null && orb.isCharging(), "burning or magic broke the gather");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(gathering(data) == null && orb.isRemoved(), "the gather went through the portal");

            // Dropped, its orb goes with it.
            assertRefusal(helper, press(player, now + 40L), null, "the fifth key");
            orb = orbOf(helper, data, "the fifth key");
            KidoEvents.dropGather(player, data);
            helper.assertTrue(gathering(data) == null && orb.isRemoved(), "the dropped gather was kept");

            // Let go two ticks before its time, it scatters; one tick before, it goes off, as the next tick would loose it.
            assertRefusal(helper, press(player, now + 50L), null, "the sixth key");
            KidoLogic.Gather early = gathering(data);
            helper.assertTrue(early != null && early.firesAt() == now + 70L, "the sixth gather goes off as " + early);
            orb = orbOf(helper, data, "the sixth key");
            assertRefusal(helper, release(player, now + 68L), KidoLogic.Refusal.SCATTERED,
                    "the Kidō key let go two ticks before the gather's time");
            helper.assertTrue(gathering(data) == null && orb.isRemoved(), "the gather let go two ticks early was kept");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 1_000.0, "the reiryoku once that gather scattered");
            assertRefusal(helper, press(player, now + 80L), null, "the seventh key");
            KidoLogic.Gather onTime = gathering(data);
            helper.assertTrue(onTime != null && onTime.firesAt() == now + 100L, "the seventh gather goes off as " + onTime);
            orb = orbOf(helper, data, "the seventh key");
            assertRefusal(helper, release(player, now + 99L), null, "the Kidō key let go a tick before the gather's time");
            helper.assertTrue(gathering(data) == null && orb.isRemoved(), "the gather let go on time was kept");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 820.0, "the reiryoku once that gather went off");
            helper.assertTrue(data.xp(Stat.KIDO) == 6, "Raikōhō at the wall trained " + data.xp(Stat.KIDO) + " Kidō xp");
            helper.assertFalse(KidoLogic.ready(data, now + 198L), "the hands were free a tick before Raikōhō's cooldown");
            helper.assertTrue(KidoLogic.ready(data, now + 199L), "the hands were not free after Raikōhō's cooldown");
            assertWallStands(helper);

            // Each gathering first, so its end is not for want of beginning: death, a hold, a takeover, another art
            // and Jinzen.
            assertGatherEnds(helper, now, "a Shinigami who died", (soul, soulData) -> soul.setHealth(0.0F));
            NegacionLight placed = NegacionLight.place(level, held, null);
            light = placed;
            assertGatherEnds(helper, held, now, "a Shinigami held by Negación", (soul, soulData) -> helper.assertTrue(
                    placed != null && soul.startRiding(placed, true) && Negacion.held(soul),
                    "the mock Shinigami could not be held"));
            assertGatherEnds(helper, taken, now, "a soul whose Hollow took its body", (soul, soulData) -> helper.assertTrue(
                    VisoredEvents.startTakeover(soul, soulData, false, now) && VisoredLogic.takenOver(soulData),
                    "the rooted Hollow did not seize its host"));
            Vec3 lane = helper.absoluteVec(LANE);
            zangetsu.moveTo(lane.x, lane.y, lane.z, EAST, 0.0F);
            downTheLane(zangetsu);
            helper.assertTrue(ShikaiEvents.release(zangetsu) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
            // Its release paid for, it is filled up again and taught Kidō 42 and Raikōhō.
            zangetsuData.setMaxReiryoku(1_000.0F);
            zangetsuData.setReiryoku(1_000.0F);
            zangetsuData.setLevel(Stat.KIDO, 42);
            KidoLogic.choose(zangetsuData, KidoSpell.RAIKOHO.location());
            assertGatherEnds(helper, zangetsu, now, "a Shinigami gathering a Getsuga Tenshō", (soul, soulData) ->
                    helper.assertTrue(ShikaiEvents.gather(soul) == ShikaiEvents.GatherResult.GATHERING,
                            "released Zangetsu did not gather"));
            assertGatherEnds(helper, sitting, now, "a Shinigami who sat in Jinzen",
                    (soul, soulData) -> JinzenLogic.begin(soul, soulData, now));
            // Already sitting in Jinzen, the key gathers nothing, so no orb shows for a tick.
            JinzenLogic.begin(seated, seatedData, now);
            assertRefusal(helper, press(seated, now), KidoLogic.Refusal.GATHERING, "the Kidō key in Jinzen");
            helper.assertTrue(gathering(seatedData) == null, "the key gathered in Jinzen: " + gathering(seatedData));
            helper.assertTrue(charging(helper).isEmpty(), "an orb is still charging: " + charging(helper));
        } finally {
            KidoEvents.dropGather(player, data);
            ShikaiEvents.cancelGather(zangetsu, zangetsuData);
            VisoredEvents.endTakeover(taken, takenData, VisoredEvents.TakeoverEnd.REMOVED, now);
            JinzenLogic.end(sitting, sittingData);
            JinzenLogic.end(seated, seatedData);
            held.stopRiding();
            discard(light, spider);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- The binds ----

    @GameTest(template = "empty")
    public static void bakudoSpellsShipAndTheContestWeighs(GameTestHelper helper) {
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell hainawa = spell(helper, KidoSpell.HAINAWA).value();

        // Both binds have no incantation, and are known and cast by name from their level, never misfiring.
        assertShipped(helper, sai, "Sai", "kido.bleach.sai", 1, 1, SAI_COLOUR, KidoSpell.Kind.BAKUDO, KidoSpell.Shape.BIND);
        assertNear(helper, sai.costShare(), 0.03, "Sai's share of reiryoku");
        assertNear(helper, sai.minCost(), 2.0, "Sai's least cost");
        helper.assertTrue(sai.cooldownTicks() == 30 && sai.xp() == 1,
                "Sai waits " + sai.cooldownTicks() + " ticks and trains " + sai.xp() + " xp");
        assertNear(helper, sai.effect().range(), 8.0, "Sai's reach");
        assertNear(helper, sai.effect().radius(), 0.2, "Sai's half-width");
        assertNear(helper, sai.effect().boundSeconds(), 3.0, "Sai's bound arms");
        assertNear(helper, sai.effect().heldSeconds(), 0.0, "Sai's held body");
        helper.assertTrue(sai.gatherTicks() == 0 && !sai.gathers(), "Sai gathers for " + sai.gatherTicks() + " ticks");
        assertNear(helper, KidoLogic.misfireChance(sai, 1), 0.0, "Sai's misfire chance at Kidō 1");
        assertNear(helper, KidoLogic.share(sai, KidoLogic.Form.NAME, 1), 1.0, "the share of Sai cast by name at Kidō 1");
        assertShipped(helper, hainawa, "Hainawa", "kido.bleach.hainawa", 4, 4, HAINAWA_COLOUR, KidoSpell.Kind.BAKUDO,
                KidoSpell.Shape.BIND);
        assertNear(helper, hainawa.costShare(), 0.04, "Hainawa's share of reiryoku");
        assertNear(helper, hainawa.minCost(), 2.0, "Hainawa's least cost");
        helper.assertTrue(hainawa.cooldownTicks() == 40 && hainawa.xp() == 2,
                "Hainawa waits " + hainawa.cooldownTicks() + " ticks and trains " + hainawa.xp() + " xp");
        assertNear(helper, hainawa.effect().range(), 12.0, "Hainawa's reach");
        assertNear(helper, hainawa.effect().radius(), 0.25, "Hainawa's half-width");
        assertNear(helper, hainawa.effect().boundSeconds(), 0.0, "Hainawa's bound arms");
        assertNear(helper, hainawa.effect().heldSeconds(), 3.0, "Hainawa's held body");

        // The contest: 400 reiatsu, 1% more for each number of the spell and each Kidō level, against the target's.
        Player caster = ZanpakutoTests.mock(helper, shinigami(400.0F, 4));
        SoulData data = SoulLogic.get(caster);
        Player six = ZanpakutoTests.mock(helper, shinigami(600.0F, 0));
        Player nine = ZanpakutoTests.mock(helper, shinigami(900.0F, 0));
        Pig pig = QuincyNpcTests.created(helper, EntityType.PIG);
        assertNear(helper, KidoLogic.bindStrength(sai, caster, data, 1.0F), 420.16, "Sai's strength from 400 at Kidō 4");
        assertNear(helper, KidoLogic.bindStrength(hainawa, caster, data, 1.0F), 432.64,
                "Hainawa's strength from 400 at Kidō 4");
        assertNear(helper, KidoLogic.bindStrength(sai, caster, data, 0.5F), 210.08, "Sai's strength at half its share");
        assertNear(helper, KidoLogic.bindRatio(420.16F, six), 0.7003, 0.001, "Sai's strength over a soul of 600");
        // A soulless creature's reiatsu is its health: a pig's 10.
        assertNear(helper, KidoLogic.bindRatio(420.16F, pig), 42.016, "Sai's strength over a pig");

        // The curve: the whole time from an even contest up, less down to half, and nothing below half.
        helper.assertTrue(KidoLogic.bindTicks(3.0F, 1.2F) == 60 && KidoLogic.bindTicks(3.0F, 1.0F) == 60,
                "three seconds at a ratio of 1.2 and of 1 hold " + KidoLogic.bindTicks(3.0F, 1.2F) + " and "
                        + KidoLogic.bindTicks(3.0F, 1.0F) + " ticks");
        helper.assertTrue(KidoLogic.bindTicks(3.0F, 0.7003F) == 42,
                "three seconds at a ratio of 0.7003 hold " + KidoLogic.bindTicks(3.0F, 0.7003F) + " ticks");
        helper.assertTrue(KidoLogic.bindTicks(3.0F, 0.5F) == 30 && KidoLogic.bindTicks(3.0F, 0.4999F) == 0,
                "three seconds at a ratio of a half and just below hold " + KidoLogic.bindTicks(3.0F, 0.5F) + " and "
                        + KidoLogic.bindTicks(3.0F, 0.4999F) + " ticks");
        helper.assertTrue(KidoLogic.bindTicks(0.0F, 2.0F) == 0 && KidoLogic.bindTicks(60.0F, 1.0F) == 1_200,
                "no seconds and sixty hold " + KidoLogic.bindTicks(0.0F, 2.0F) + " and "
                        + KidoLogic.bindTicks(60.0F, 1.0F) + " ticks");
        float overNine = KidoLogic.bindRatio(420.16F, nine);
        helper.assertTrue(overNine < 0.5F, "Sai's strength over a soul of 900 is " + overNine + ", not below a half");

        // Each number of the config moves its part alone.
        withConfig(BleachConfig.KIDO_BAKUDO_BREAK_BELOW, 0.8, () -> helper.assertTrue(KidoLogic.bindTicks(3.0F, 0.7F) == 0,
                "a ratio of 0.7 held " + KidoLogic.bindTicks(3.0F, 0.7F) + " ticks with binds breaking below 0.8"));
        withConfig(BleachConfig.KIDO_BAKUDO_GRADE_PER_NUMBER, 0.0, () -> assertNear(helper,
                KidoLogic.bindStrength(hainawa, caster, data, 1.0F), 416.0,
                "Hainawa's strength with no weight for its number"));
        withConfig(BleachConfig.KIDO_BAKUDO_SKILL_PER_LEVEL, 0.0, () -> assertNear(helper,
                KidoLogic.bindStrength(sai, caster, data, 1.0F), 404.0, "Sai's strength with no weight for Kidō"));

        // The two effects: harmful, cured by neither milk nor a totem, and freed by Ransōtengai's threads.
        for (Holder<MobEffect> bind : List.of(ModEffects.BAKUDO_BOUND, ModEffects.BAKUDO_HELD)) {
            String what = bind == ModEffects.BAKUDO_BOUND ? "Sai's bind" : "Hainawa's hold";
            helper.assertTrue(bind.value().getCategory() == MobEffectCategory.HARMFUL,
                    what + " is " + bind.value().getCategory());
            Set<EffectCure> cures = new HashSet<>();
            bind.value().fillEffectCures(cures, new MobEffectInstance(bind, 60));
            helper.assertTrue(cures.isEmpty(), what + " is cured by " + cures);
            helper.assertTrue(bind.is(BleachTags.RANSOTENGAI_FREES), what + " is not in #bleach:ransotengai_frees");
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void saiBindsAndHainawaHoldsWhatTheyReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell hainawa = spell(helper, KidoSpell.HAINAWA).value();
        Player player = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 4));
        SoulData data = SoulLogic.get(player);
        downTheLane(player);
        IronGolem golem = golem(helper, 5, 8);
        PlusSpirit plus = null;
        IronGolem second = null;
        IronGolem behind = null;
        IronGolem close = null;
        IronGolem ahead = null;
        try {
            // Sai's 420.16 against the golem's 100: its arms bound for the whole three seconds, hidden, and unhurt.
            assertRefusal(helper, KidoEvents.cast(player, sai, KidoLogic.Form.NAME, now), null, "Sai at the golem");
            MobEffectInstance bound = golem.getEffect(ModEffects.BAKUDO_BOUND);
            helper.assertTrue(bound != null && bound.getDuration() == 60 && bound.getAmplifier() == 0,
                    "Sai bound the golem's arms as " + bound);
            helper.assertTrue(!bound.isAmbient() && !bound.isVisible() && !bound.showIcon(),
                    "Sai's bind is ambient, swirls or shows its icon: " + bound);
            helper.assertTrue(heldTicks(golem) == 0, "Sai held the golem's body for " + heldTicks(golem) + " ticks");
            assertNear(helper, speed(golem), 0.175, 0.001, "the bound golem's speed");
            HollowPlayerTests.assertClose(helper, golem.getHealth(), 100.0, "the bound golem's health");
            helper.assertTrue(golem.getLastHurtByMob() == null, "Sai angered the golem at " + golem.getLastHurtByMob());
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 388.0, "the reiryoku after Sai");
            helper.assertFalse(KidoLogic.ready(data, now + 29L), "the hands were free a tick before Sai's cooldown");
            helper.assertTrue(KidoLogic.ready(data, now + 30L), "the hands were not free after Sai's cooldown");
            helper.assertTrue(data.xp(Stat.KIDO) == 2, "Sai on the golem trained " + data.xp(Stat.KIDO)
                    + " Kidō xp, not 1 and 1");
            // The trace runs from the hand to where the ray met the golem, its box widened by the half-width.
            List<CeroBeam> traces = level.getEntitiesOfClass(CeroBeam.class, helper.getBounds(),
                    beam -> beam.isFired() && beam.colour() == SAI_COLOUR);
            helper.assertTrue(traces.size() == 1 && traces.get(0).reach().length() > 1.5
                    && traces.get(0).reach().length() < 4.0, "Sai drew " + traces.stream()
                    .map(trace -> trace.reach().length()).toList() + " blocks of trace");
            helper.assertTrue(KidoLogic.wary(golem, sai, now + 119L) && !KidoLogic.wary(golem, sai, now + 120L),
                    "the golem is not wary of Sai for twice its bind");
            helper.assertFalse(KidoLogic.wary(golem, hainawa, now + 1L), "the bound golem is wary of Hainawa too");

            // Hainawa, once the hands are free, holds the bound body too: nothing walks it or jumps it anywhere.
            assertRefusal(helper, KidoEvents.cast(player, hainawa, KidoLogic.Form.NAME, now + 30L), null,
                    "Hainawa at the bound golem");
            helper.assertTrue(heldTicks(golem) == 60 && boundTicks(golem) == 60,
                    "the golem is held " + heldTicks(golem) + " ticks and bound " + boundTicks(golem));
            assertNear(helper, speed(golem), 0.0, 0.001, "the held golem's speed");
            assertNear(helper, golem.getAttributeValue(Attributes.JUMP_STRENGTH), 0.0, 0.001, "the held golem's jump");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 372.0, "the reiryoku after Hainawa");
            helper.assertTrue(data.xp(Stat.KIDO) == 5, "Hainawa on the golem trained Kidō to " + data.xp(Stat.KIDO)
                    + " xp, not 5");

            // The ray passes a Plus by, as a Hadō spares one, and binds the golem behind it.
            discard(golem);
            plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 4, 8);
            second = golem(helper, 7, 8);
            KidoEvents.BindAim aim = KidoEvents.bindAim(player, sai);
            helper.assertTrue(aim.target() == second, "Sai's ray past the Plus met " + aim.target());
            assertRefusal(helper, KidoEvents.cast(player, sai, KidoLogic.Form.NAME, now + 70L), null,
                    "Sai past the Plus");
            helper.assertTrue(boundTicks(second) == 60, "Sai past the Plus bound the golem " + boundTicks(second) + " ticks");
            helper.assertTrue(unbound(plus), "Sai bound the Plus in its way");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 360.0, "the reiryoku after Sai past the Plus");
            helper.assertTrue(data.xp(Stat.KIDO) == 7, "Sai past the Plus trained Kidō to " + data.xp(Stat.KIDO)
                    + " xp, not 7");

            // A wall stops the ray short of the golem behind it: nothing is bound, and nothing earns the strike bonus.
            discard(plus, second);
            behind = golem(helper, 12, 8);
            aim = KidoEvents.bindAim(player, hainawa);
            helper.assertTrue(aim.target() == null, "Hainawa's ray through the wall met " + aim.target());
            assertNear(helper, helper.relativeVec(aim.end()).x, 11.0, "where Hainawa's ray met the wall");
            assertRefusal(helper, KidoEvents.cast(player, hainawa, KidoLogic.Form.NAME, now + 100L), null,
                    "Hainawa at the wall");
            helper.assertTrue(unbound(behind), "Hainawa through the wall held the golem behind it");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 344.0, "the reiryoku after Hainawa at the wall");
            // Kidō 4 rises only at 180 xp, so no level changes within the test.
            helper.assertTrue(data.xp(Stat.KIDO) == 9 && data.level(Stat.KIDO) == 4, "Hainawa at the wall left Kidō "
                    + data.level(Stat.KIDO) + " with " + data.xp(Stat.KIDO) + " xp, not 4 with 9");
            helper.assertFalse(KidoLogic.ready(data, now + 139L), "the hands were free a tick before Hainawa's cooldown");
            assertWallStands(helper);

            // A creature so close that the caster's eyes are within it is met at once, where the ray leaves it.
            close = golem(helper, 2, 8);
            close.moveTo(player.getX(), player.getY(), player.getZ());
            aim = KidoEvents.bindAim(player, sai);
            helper.assertTrue(aim.target() == close, "Sai's ray from within the golem met " + aim.target());
            assertNear(helper, aim.end().x - player.getX(), 0.9, "where Sai's ray left the golem it began in");
            // But one pressed to the caster's back is behind the ray, which goes on to the golem ahead.
            close.moveTo(player.getX() - 0.75, player.getY(), player.getZ());
            ahead = golem(helper, 6, 8);
            aim = KidoEvents.bindAim(player, sai);
            helper.assertTrue(aim.target() == ahead, "Sai's ray with a golem at the caster's back met " + aim.target());
        } finally {
            discard(golem, plus, second, behind, close, ahead);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void aBoundSoulStrikesAndCastsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        // Nothing here should cast, but the wall keeps a beam that did within the field.
        buildWall(helper);
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell hainawa = spell(helper, KidoSpell.HAINAWA).value();
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(1_000.0F, 42));
        SoulData casterData = SoulLogic.get(caster);
        Player bound = victim(helper, GameType.SURVIVAL, 400.0F);
        SoulData boundData = SoulLogic.get(bound);
        boundData.setLevel(Stat.KIDO, 10);
        IronGolem golem = golem(helper, 6, 4);
        IronGolem free = golem(helper, 6, 12);
        Player struck = victim(helper, GameType.SURVIVAL, 400.0F);
        Player gatherer = gatherer(helper);
        SoulData gathererData = SoulLogic.get(gatherer);
        Player zangetsu = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        SoulData zangetsuData = SoulLogic.get(zangetsu);
        Player held = victim(helper, GameType.SURVIVAL, 400.0F);
        Arrow loosed = null;
        NegacionLight offered = null;
        try {
            helper.assertTrue(ShikaiEvents.release(zangetsu) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");

            // Arms bound in the middle of a chant break it at once, as a blow would: 1434.2 against 400, the whole time.
            heardAs(helper, KidoEvents.chant(bound, romaji(helper, KidoSpell.SHAKKAHO), now),
                    KidoEvents.Heard.Outcome.STARTED, KidoSpell.SHAKKAHO, null, "the chant before the bind");
            assertBind(helper, KidoEvents.bindOne(caster, bound, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on a chanting soul");
            helper.assertTrue(pending(boundData) == null, "the bind left the chant pending: " + pending(boundData));
            helper.assertTrue(boundTicks(bound) == 60, "Sai bound the soul " + boundTicks(bound) + " ticks");
            assertNear(helper, KidoLogic.bindRatio(KidoLogic.bindStrength(sai, caster, casterData, 1.0F), bound), 3.586,
                    "Sai's strength from 1,000 at Kidō 42 over a soul of 400");
            heardAs(helper, KidoEvents.chant(bound, romaji(helper, KidoSpell.SHAKKAHO), now + KidoEvents.CHANT_SPACING_TICKS),
                    KidoEvents.Heard.Outcome.REFUSED, KidoSpell.SHAKKAHO, KidoLogic.Refusal.BOUND, "a chant while bound");
            helper.assertTrue(pending(boundData) == null, "a chant began while bound: " + pending(boundData));

            // No spell by name or by its incantation, and nothing spent; but the soul may still choose.
            assertRefusal(helper, KidoLogic.mayCast(bound, boundData, now, KidoLogic.Form.NAME), KidoLogic.Refusal.BOUND,
                    "a bound soul's Kidō by name");
            assertRefusal(helper, KidoLogic.mayCast(bound, boundData, now, KidoLogic.Form.CHANT), KidoLogic.Refusal.BOUND,
                    "a bound soul's chant");
            helper.assertTrue(KidoLogic.mayChoose(KidoLogic.Refusal.BOUND), "a bound soul may not choose");
            assertRefusal(helper, press(bound, now + 1L), KidoLogic.Refusal.BOUND, "the Kidō key while bound");
            HollowPlayerTests.assertClose(helper, boundData.reiryoku(), 400.0, "the reiryoku after the key while bound");
            assertRefusal(helper, next(bound, now + 1L), null, "Sneak and the key while bound");

            // What the hands would do is refused: a blow, an item, a block used or broken, a creature dealt with.
            helper.assertTrue(NeoForge.EVENT_BUS.post(new AttackEntityEvent(bound, golem)).isCanceled(),
                    "a bound soul struck the golem");
            PlayerInteractEvent.RightClickItem item = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.RightClickItem(bound, InteractionHand.MAIN_HAND));
            helper.assertTrue(item.isCanceled() && item.getCancellationResult() == InteractionResult.FAIL,
                    "a bound soul used what it holds: " + item.getCancellationResult());
            BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
            BlockHitResult face = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
            PlayerInteractEvent.RightClickBlock block = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.RightClickBlock(bound, InteractionHand.MAIN_HAND, floor, face));
            helper.assertTrue(block.isCanceled() && block.getCancellationResult() == InteractionResult.FAIL,
                    "a bound soul used a block: " + block.getCancellationResult());
            PlayerInteractEvent.EntityInteract dealt = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.EntityInteract(bound, InteractionHand.MAIN_HAND, golem));
            helper.assertTrue(dealt.isCanceled() && dealt.getCancellationResult() == InteractionResult.FAIL,
                    "a bound soul dealt with the golem: " + dealt.getCancellationResult());
            PlayerInteractEvent.EntityInteractSpecific spot = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.EntityInteractSpecific(bound, InteractionHand.MAIN_HAND, golem, Vec3.ZERO));
            helper.assertTrue(spot.isCanceled() && spot.getCancellationResult() == InteractionResult.FAIL,
                    "a bound soul dealt with a spot on the golem: " + spot.getCancellationResult());
            helper.assertTrue(NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Start(bound, new ItemStack(Items.BREAD),
                    32)).isCanceled(), "a bound soul began to eat");
            helper.assertTrue(NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, floor, level.getBlockState(floor),
                    bound)).isCanceled(), "a bound soul broke a block");
            helper.assertTrue(NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Stop(bound, new ItemStack(Items.BOW),
                    60)).isCanceled(), "a bound soul loosed the bow it had drawn");
            // But what it shot, a Cero from the mouth among them, still breaks what it may: the ask is not the hands'.
            helper.assertTrue(GetsugaTensho.permitted(level, floor, level.getBlockState(floor), bound, bound, true),
                    "a bound soul's shot may not break the floor");

            // Its blade held out to a player is refused before Konsō's offer weighs it, so nothing is offered.
            bound.setShiftKeyDown(true);
            bound.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ASAUCHI.get()));
            PlayerInteractEvent.EntityInteract offer = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.EntityInteract(bound, InteractionHand.MAIN_HAND, struck));
            bound.setShiftKeyDown(false);
            bound.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(offer.isCanceled() && offer.getCancellationResult() == InteractionResult.FAIL,
                    "a bound soul held its blade out to a player: " + offer.getCancellationResult());

            // A Negación light offered to it may still be stepped into, which frees it, as it frees a mob.
            offered = NegacionLight.place(level, bound, bound);
            helper.assertTrue(offered != null, "no light was offered to the bound soul");
            helper.assertFalse(NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(bound,
                    InteractionHand.MAIN_HAND, offered)).isCanceled(), "a bound soul could not step into its light");
            helper.assertFalse(NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(bound,
                    InteractionHand.MAIN_HAND, offered, Vec3.ZERO)).isCanceled(), "a bound soul could not touch its light");

            // A bound golem's blow lands nothing, and its arrow never joins the world; a free one's do.
            assertBind(helper, KidoEvents.bindOne(caster, golem, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on the golem");
            struck.invulnerableTime = 0;
            helper.assertFalse(struck.hurt(golem.damageSources().mobAttack(golem), 4.0F), "the bound golem's blow landed");
            HollowPlayerTests.assertClose(helper, struck.getHealth(), 20.0, "the health after the bound golem's blow");
            land(helper, struck, free.damageSources().mobAttack(free), 4.0F, "the free golem's blow");
            helper.assertTrue(struck.getHealth() < 20.0F, "the free golem's blow left " + struck.getHealth() + " health");
            helper.assertFalse(level.addFreshEntity(new Arrow(level, golem, new ItemStack(Items.ARROW), null)),
                    "the bound golem's arrow joined the world");
            loosed = new Arrow(level, free, new ItemStack(Items.ARROW), null);
            helper.assertTrue(level.addFreshEntity(loosed), "the free golem's arrow was kept from the world");

            // Nor does a released zanpakutō gather a Getsuga Tenshō in bound hands.
            assertBind(helper, KidoEvents.bindOne(caster, zangetsu, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on Zangetsu's wielder");
            helper.assertTrue(ShikaiEvents.gather(zangetsu) == ShikaiEvents.GatherResult.BOUND,
                    "bound hands gathered a Getsuga Tenshō");

            // A spell gathering in the palm is broken by the bind, and its orb goes with it.
            assertRefusal(helper, press(gatherer, now), null, "Raikōhō's key before the bind");
            orbOf(helper, gathererData, "Raikōhō before the bind");
            assertBind(helper, KidoEvents.bindOne(caster, gatherer, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on a soul gathering Raikōhō");
            helper.assertTrue(gathering(gathererData) == null, "the bind left the gather as " + gathering(gathererData));
            helper.assertTrue(charging(helper).isEmpty(), "an orb is still charging: " + charging(helper));

            // A body held by Hainawa keeps its hands: it casts and strikes as it would.
            assertBind(helper, KidoEvents.bindOne(caster, held, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa on a soul");
            helper.assertTrue(heldTicks(held) == 60 && boundTicks(held) == 0,
                    "Hainawa held the soul " + heldTicks(held) + " ticks and bound it " + boundTicks(held));
            assertRefusal(helper, KidoLogic.mayCast(held, SoulLogic.get(held), now, KidoLogic.Form.NAME), null,
                    "a held soul's Kidō");
            helper.assertFalse(NeoForge.EVENT_BUS.post(new AttackEntityEvent(held, free)).isCanceled(),
                    "a held soul could not strike the golem");
        } finally {
            KidoEvents.dropGather(gatherer, gathererData);
            ShikaiEvents.cancelGather(zangetsu, zangetsuData);
            unbind(bound, gatherer, zangetsu, held);
            discard(golem, free, loosed, offered);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void aHeldBodyIsRootedButFreeHanded(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell hainawa = spell(helper, KidoSpell.HAINAWA).value();
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(1_000.0F, 42));
        IronGolem golem = golem(helper, 5, 8);
        Pig pig = HollowPlayerTests.placed(helper, EntityType.PIG, 8, 4);
        Zombie zombie = HollowPlayerTests.placed(helper, EntityType.ZOMBIE, 8, 4);
        Player held = victim(helper, GameType.SURVIVAL, 400.0F);
        Player guard = victim(helper, GameType.SURVIVAL, 400.0F);
        guard.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        guard.startUsingItem(InteractionHand.MAIN_HAND);
        try {
            helper.assertTrue(zombie.startRiding(pig, true), "the zombie would not ride the pig");

            // Held, the golem neither walks, jumps nor swims: each takes the whole of its total.
            assertBind(helper, KidoEvents.bindOne(caster, golem, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa on the golem");
            assertNear(helper, speed(golem), 0.0, 0.001, "the held golem's speed");
            assertNear(helper, golem.getAttributeValue(Attributes.JUMP_STRENGTH), 0.0, 0.001, "the held golem's jump");
            assertNear(helper, golem.getAttributeValue(NeoForgeMod.SWIM_SPEED), 0.0, 0.001, "the held golem's swim");
            AttributeModifier walk = modifier(golem, Attributes.MOVEMENT_SPEED, HELD_SPEED);
            helper.assertTrue(walk != null && walk.amount() == -1.0
                    && walk.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    "the held golem's walk is changed by " + walk);

            // Each tick the hold keeps no motion but a fall: a push and a rise go, a fall stays.
            golem.setDeltaMovement(0.4, 0.3, -0.2);
            helper.assertTrue(ModEffects.BAKUDO_HELD.value().applyEffectTick(golem, 0), "the hold let the golem go");
            assertMotion(helper, golem, 0.0, 0.0, 0.0, "the held golem's motion after a push and a rise");
            golem.setDeltaMovement(0.1, -0.3, 0.1);
            helper.assertTrue(ModEffects.BAKUDO_HELD.value().applyEffectTick(golem, 0), "the hold let the falling golem go");
            assertMotion(helper, golem, 0.0, -0.3, 0.0, "the held golem's motion as it falls");

            // A jump goes nowhere, whatever vanilla added to it.
            golem.setDeltaMovement(0.2, 0.42, 0.1);
            NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(golem));
            assertMotion(helper, golem, 0.0, 0.0, 0.0, "the held golem's motion after a jump");

            // A held rider is pulled off its mount as the hold lands, and the mount is left free.
            assertBind(helper, KidoEvents.bindOne(caster, zombie, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa on the riding zombie");
            helper.assertFalse(zombie.isPassenger(), "the held zombie still rides " + zombie.getVehicle());
            helper.assertTrue(unbound(pig), "Hainawa on its rider bound the pig");
            // Nor does it mount again, even forced; once let go, it may.
            helper.assertFalse(zombie.startRiding(pig, true), "the held zombie mounted the pig again");
            zombie.removeEffect(ModEffects.BAKUDO_HELD);
            helper.assertTrue(zombie.startRiding(pig, true), "the zombie let go could not mount the pig");
            // A hold laid on with no start, as one read from a save is, still pulls its rider off at its tick.
            zombie.forceAddEffect(new MobEffectInstance(ModEffects.BAKUDO_HELD, 60, 0, false, false, false), null);
            helper.assertTrue(zombie.isPassenger(), "the hold with no start pulled the zombie off at once");
            ModEffects.BAKUDO_HELD.value().applyEffectTick(zombie, 0);
            helper.assertFalse(zombie.isPassenger(), "the hold's tick left the zombie riding " + zombie.getVehicle());

            // A held player flies no more than it walks, and the server roots it as its own client would.
            assertBind(helper, KidoEvents.bindOne(caster, held, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa on a soul");
            AttributeModifier heldWalk = HollowPlayerTests.modifier(held, Attributes.MOVEMENT_SPEED, HELD_SPEED);
            AttributeModifier heldFlight = HollowPlayerTests.modifier(held, NeoForgeMod.CREATIVE_FLIGHT,
                    BleachMod.id("bakudo_held_flight"));
            helper.assertTrue(heldWalk != null && heldWalk.amount() == -1.0 && heldFlight != null
                    && heldFlight.amount() == -1.0, "the held soul's walk and flight are changed by " + heldWalk + " and "
                    + heldFlight);
            assertNear(helper, speed(held), 0.0, 0.001, "the held soul's speed");
            held.setDeltaMovement(0.3, 0.2, 0.1);
            ModEffects.BAKUDO_HELD.value().applyEffectTick(held, 0);
            assertMotion(helper, held, 0.0, 0.0, 0.0, "the held soul's motion");

            // Bound arms let go of the shield they had raised.
            helper.assertTrue(guard.isUsingItem(), "the guard did not raise its shield");
            assertBind(helper, KidoEvents.bindOne(caster, guard, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on the guard");
            ModEffects.BAKUDO_BOUND.value().applyEffectTick(guard, 0);
            helper.assertFalse(guard.isUsingItem(), "the bound guard still holds its shield up");
            // And a server's player lets go of a chest it had open, or a horse's packs: only its own inventory stays.
            ServerPlayer opener = FakePlayerFactory.getMinecraft(helper.getLevel());
            opener.containerMenu = ChestMenu.oneRow(1, opener.getInventory());
            ModEffects.BAKUDO_BOUND.value().applyEffectTick(opener, 0);
            helper.assertTrue(opener.containerMenu == opener.inventoryMenu, "the bound player still has open "
                    + opener.containerMenu);

            // Lifted, the hold's modifiers go with it.
            golem.removeEffect(ModEffects.BAKUDO_HELD);
            assertNear(helper, speed(golem), 0.25, 0.001, "the golem's speed once let go");
            helper.assertTrue(modifier(golem, Attributes.MOVEMENT_SPEED, HELD_SPEED) == null,
                    "the hold's modifier outlived it");
        } finally {
            zombie.stopRiding();
            unbind(held, guard);
            discard(golem, pig, zombie);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void bindsKeepTheRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        // The lender's cast should be refused, but the wall keeps one that was not within the field.
        buildWall(helper);
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(1_000.0F, 42));
        SoulData casterData = SoulLogic.get(caster);
        Player anyone = victim(helper, GameType.SURVIVAL, 400.0F);
        Player dead = victim(helper, GameType.SURVIVAL, 400.0F);
        dead.setHealth(0.0F);
        Player spectator = victim(helper, GameType.SPECTATOR, 400.0F);
        Player creative = victim(helper, GameType.CREATIVE, 400.0F);
        Player held = victim(helper, GameType.SURVIVAL, 400.0F);
        NegacionLight light = NegacionLight.place(level, held, null);
        Player friend = victim(helper, GameType.SURVIVAL, 400.0F);
        SoulData friendData = SoulLogic.get(friend);
        VisoredLogic.setRecord(friendData, VisoredLogic.record(friendData).withRootedAt(now - DAY));
        PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 4, 4);
        IronGolem golem = golem(helper, 6, 4);
        Villager villager = villager(helper, 8, 12);
        InnerHollow inner = HollowPlayerTests.placed(helper, ModEntities.INNER_HOLLOW.get(), 10, 4);
        Player lender = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 4));
        SoulData lenderData = SoulLogic.get(lender);
        ShinigamiLogic.transferFromShinigami(lenderData, ShinigamiTests.soul(SoulState.HUMAN, 100.0F), now);
        Scoreboard scoreboard = level.getScoreboard();
        try {
            removeTeam(scoreboard);
            helper.assertTrue(light != null && held.startRiding(light, true) && Negacion.held(held),
                    "the mock Shinigami could not be held");

            // Never the caster, the dead, a spectator, a creative player, one Negación holds, or a Plus.
            helper.assertFalse(KidoLogic.mayBind(caster, caster), "the caster may bind itself");
            helper.assertFalse(KidoLogic.mayBind(caster, dead), "the caster may bind the dead");
            helper.assertFalse(KidoLogic.mayBind(caster, spectator), "the caster may bind a spectator");
            helper.assertFalse(KidoLogic.mayBind(caster, creative), "the caster may bind a creative player");
            helper.assertFalse(KidoLogic.mayBind(caster, held), "the caster may bind a soul Negación holds");
            helper.assertFalse(KidoLogic.mayBind(caster, plus), "the caster may bind a Plus");
            helper.assertTrue(KidoLogic.mayBind(caster, anyone) && KidoLogic.mayBind(caster, golem)
                    && KidoLogic.mayBind(caster, villager), "the caster may not bind another soul, a golem or a villager");

            // A teammate with friendly fire off is spared. Every mock player has the same name, so the team holds them
            // all: it is made only once every other player has been weighed, and goes once the seized body has been.
            PlayerTeam team = scoreboard.addPlayerTeam(TEAM);
            team.setAllowFriendlyFire(false);
            scoreboard.addPlayerToTeam(caster.getScoreboardName(), team);
            helper.assertFalse(KidoLogic.mayBind(caster, friend), "the caster may bind a teammate with friendly fire off");

            // Seized by its Hollow, the same body may be bound whatever the team says, and counts twice its reiatsu.
            helper.assertTrue(VisoredEvents.startTakeover(friend, friendData, false, now)
                    && VisoredLogic.takenOver(friendData), "the rooted Hollow did not seize its host");
            helper.assertTrue(KidoLogic.takenBody(friend) && KidoLogic.mayBind(caster, friend),
                    "the caster may not bind a body its Hollow has seized");
            float strength = KidoLogic.bindStrength(sai, caster, casterData, 1.0F);
            assertNear(helper, KidoLogic.bindRatio(strength, friend), strength / (2.0 * SoulLogic.reiatsu(friend)), 0.001,
                    "Sai's strength over a seized body");
            withConfig(BleachConfig.KIDO_BAKUDO_TAKEN_OVER_STRENGTH, 1.0, () -> assertNear(helper,
                    KidoLogic.bindRatio(strength, friend), strength / SoulLogic.reiatsu(friend), 0.001,
                    "Sai's strength over a seized body counted once"));
            VisoredEvents.endTakeover(friend, friendData, VisoredEvents.TakeoverEnd.REMOVED, now);
            helper.assertFalse(KidoLogic.mayBind(caster, friend), "the caster may bind its teammate once its body is back");
            removeTeam(scoreboard);

            // One it may not bind is not tried at all.
            assertBind(helper, KidoEvents.bindOne(caster, held, sai, 1.0F, now), KidoLogic.BindResult.REFUSED,
                    "Sai on a soul Negación holds");
            helper.assertTrue(unbound(held), "Sai bound a soul Negación holds");

            // An inner Hollow is beaten by instinct, never by Kidō: it shrugs the bind off, and is left unmarked.
            assertBind(helper, KidoEvents.bindOne(caster, inner, sai, 1.0F, now), KidoLogic.BindResult.SHRUGGED,
                    "Sai on an inner Hollow");
            helper.assertTrue(unbound(inner) && !KidoLogic.wary(inner, sai, now + 1L),
                    "Sai left the inner Hollow bound or wary");

            // A lender's power answers only to an incantation, and Sai has none: refused, with nothing spent.
            float before = lenderData.reiryoku();
            assertRefusal(helper, KidoEvents.cast(lender, sai, KidoLogic.Form.NAME, now), KidoLogic.Refusal.LENT,
                    "Sai cast by a Shinigami whose power is lent");
            HollowPlayerTests.assertClose(helper, lenderData.reiryoku(), before, "the lender's reiryoku after Sai");
        } finally {
            VisoredEvents.endTakeover(friend, friendData, VisoredEvents.TakeoverEnd.REMOVED, now);
            removeTeam(scoreboard);
            held.stopRiding();
            discard(light, plus, golem, villager, inner);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void aBindIsShruggedOffOrLifted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell hainawa = spell(helper, KidoSpell.HAINAWA).value();
        IronGolem golem = golem(helper, 5, 8);
        Player weak = caster(helper, GameType.SURVIVAL, shinigami(40.0F, 1));
        SoulData weakData = SoulLogic.get(weak);
        downTheLane(weak);
        Player fair = caster(helper, GameType.SURVIVAL, shinigami(60.0F, 1));
        downTheLane(fair);
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(1_000.0F, 42));
        Player novice = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 10));
        Player soul = victim(helper, GameType.SURVIVAL, 340.0F);
        SoulData wovenData = QuincyNpcTests.known(QuincyArtsTests.thirdTier());
        QuincyArtsTests.forms(wovenData, QuincyArtsLogic.arts(wovenData).forms().withRansotengaiSince(now));
        Player quincy = ZanpakutoTests.mock(helper, wovenData);
        IronGolem lifted = golem(helper, 6, 4);
        IronGolem cured = golem(helper, 6, 12);
        EnderDragon dragon = QuincyNpcTests.created(helper, EntityType.ENDER_DRAGON);
        NegacionLight light = null;
        try {
            // 40 at Kidō 1 makes 40.8 against the golem's 100, under half: shrugged off, and the golem left unmarked.
            assertRefusal(helper, KidoEvents.cast(weak, sai, KidoLogic.Form.NAME, now), null, "Sai from 40 reiryoku");
            helper.assertTrue(unbound(golem) && !KidoLogic.wary(golem, sai, now),
                    "the golem shrugged off Sai but is bound or wary");
            // A shrug is still a cast: paid for at the least cost, and trained without the strike bonus.
            HollowPlayerTests.assertClose(helper, weakData.reiryoku(), 38.0, "the reiryoku after a shrugged Sai");
            helper.assertTrue(weakData.xp(Stat.KIDO) == 1, "a shrugged Sai trained " + weakData.xp(Stat.KIDO)
                    + " Kidō xp, not 1");
            helper.assertFalse(KidoLogic.ready(weakData, now + 29L), "the hands were free a tick before Sai's cooldown");

            // 60 makes 61.206, a ratio of 0.612: that share of three seconds, and wary for twice as long.
            assertRefusal(helper, KidoEvents.cast(fair, sai, KidoLogic.Form.NAME, now), null, "Sai from 60 reiryoku");
            helper.assertTrue(boundTicks(golem) == 37, "Sai from 60 bound the golem " + boundTicks(golem) + " ticks");
            helper.assertTrue(KidoLogic.wary(golem, sai, now + 73L) && !KidoLogic.wary(golem, sai, now + 74L),
                    "the golem is not wary of Sai for twice its 37 ticks");

            // While wary of Sai the golem shrugs it off from anyone, but Hainawa still takes; once the mark is out, so
            // does Sai, at its whole time.
            assertBind(helper, KidoEvents.bindOne(caster, golem, sai, 1.0F, now + 30L), KidoLogic.BindResult.SHRUGGED,
                    "Sai on a golem wary of it");
            helper.assertTrue(boundTicks(golem) == 37, "a shrugged Sai changed the bind to " + boundTicks(golem) + " ticks");
            assertBind(helper, KidoEvents.bindOne(caster, golem, hainawa, 1.0F, now + 30L), KidoLogic.BindResult.BOUND,
                    "Hainawa on a golem wary of Sai");
            helper.assertTrue(heldTicks(golem) == 60, "Hainawa held the golem " + heldTicks(golem) + " ticks");
            assertBind(helper, KidoEvents.bindOne(caster, golem, sai, 1.0F, now + 74L), KidoLogic.BindResult.BOUND,
                    "Sai once the mark is out");
            helper.assertTrue(boundTicks(golem) == 60, "Sai once the mark is out bound the golem " + boundTicks(golem)
                    + " ticks");

            // 150 at Kidō 10 against a soul of 340: Sai's 166.65 falls under half, Hainawa's 171.6 just clears it.
            assertBind(helper, KidoEvents.bindOne(novice, soul, sai, 1.0F, now), KidoLogic.BindResult.SHRUGGED,
                    "Sai at a ratio of 0.490");
            assertBind(helper, KidoEvents.bindOne(novice, soul, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa at a ratio of 0.505");
            helper.assertTrue(heldTicks(soul) == 30 && boundTicks(soul) == 0,
                    "Hainawa at a ratio of 0.505 held the soul " + heldTicks(soul) + " ticks and bound it "
                            + boundTicks(soul));

            // Ransōtengai's threads move a woven body, so no bind takes hold of it.
            helper.assertTrue(QuincyRansotengai.woven(quincy), "the Quincy's threads are not woven");
            assertBind(helper, KidoEvents.bindOne(caster, quincy, sai, 1.0F, now), KidoLogic.BindResult.SHRUGGED,
                    "Sai on a woven Quincy");
            helper.assertTrue(unbound(quincy), "Sai bound a woven Quincy");

            // Negación's light lifts a bind away: the tick refuses it, and the instance's tick then removes it. A held
            // body, which mounts nothing else, may still be taken up by the light.
            assertBind(helper, KidoEvents.bindOne(caster, lifted, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on the golem Negación will lift");
            assertBind(helper, KidoEvents.bindOne(caster, lifted, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa on the golem Negación will lift");
            light = NegacionLight.place(level, lifted, null);
            helper.assertTrue(light != null && lifted.startRiding(light, true) && Negacion.held(lifted),
                    "the bound and held golem could not be held");
            helper.assertFalse(ModEffects.BAKUDO_BOUND.value().applyEffectTick(lifted, 0),
                    "the bind kept its hold on a golem Negación holds");
            helper.assertFalse(ModEffects.BAKUDO_HELD.value().applyEffectTick(lifted, 0),
                    "the hold kept the golem Negación holds");
            MobEffectInstance bind = lifted.getEffect(ModEffects.BAKUDO_BOUND);
            MobEffectInstance hold = lifted.getEffect(ModEffects.BAKUDO_HELD);
            helper.assertTrue(bind != null && hold != null, "the bind or the hold went before its tick");
            // Their own returns are the time they had left, so they are not asked.
            bind.tick(lifted, () -> { });
            hold.tick(lifted, () -> { });
            helper.assertTrue(boundTicks(lifted) == 0 && heldTicks(lifted) == 0, "Negación's light left the bind for "
                    + boundTicks(lifted) + " ticks and the hold for " + heldTicks(lifted));
            assertNear(helper, speed(lifted), 0.25, 0.001, "the speed of the golem Negación lifted the bind from");
            helper.assertFalse(KidoLogic.mayBind(caster, lifted), "the caster may bind a golem Negación holds");

            // Neither milk nor a totem lifts a bind; only clearing every effect does.
            assertBind(helper, KidoEvents.bindOne(caster, cured, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on the golem to be cured");
            assertBind(helper, KidoEvents.bindOne(caster, cured, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa on the golem to be cured");
            helper.assertFalse(cured.removeEffectsCuredBy(EffectCures.MILK), "milk cured the golem of a bind");
            helper.assertFalse(cured.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM),
                    "a totem cured the golem of a bind");
            helper.assertTrue(boundTicks(cured) == 60 && heldTicks(cured) == 60,
                    "milk and a totem left the golem bound " + boundTicks(cured) + " and held " + heldTicks(cured));
            helper.assertTrue(cured.removeAllEffects() && unbound(cured), "clearing every effect left a bind");
            assertNear(helper, speed(cured), 0.25, 0.001, "the speed of the golem cleared of its binds");

            // The dragon refuses every effect, so it shrugs off even a bind strong enough to hold it.
            assertBind(helper, KidoEvents.bindOne(caster, dragon, sai, 1.0F, now), KidoLogic.BindResult.SHRUGGED,
                    "Sai on the dragon");
            helper.assertTrue(unbound(dragon), "Sai bound the dragon");
        } finally {
            lifted.stopRiding();
            unbind(soul, quincy);
            discard(light, golem, lifted, cured);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- The chanted binds and the pin ----

    @GameTest(template = "empty")
    public static void gekiAndRikujokoroAreChantedBinds(GameTestHelper helper) {
        Registry<KidoSpell> spells = spells(helper);
        KidoSpell geki = spell(helper, KidoSpell.GEKI).value();
        KidoSpell rikujokoro = spell(helper, KidoSpell.RIKUJOKORO).value();
        ResourceLocation gekiId = KidoSpell.GEKI.location();
        ResourceLocation rikujokoroId = KidoSpell.RIKUJOKORO.location();
        String gekiCall = "Bakudō #9, Geki!";
        String rikujokoroCall = "Bakudō #61, Rikujōkōrō!";

        // Each is heard in either tongue, alone or with its call; never by its call alone, nor by the other's words.
        for (String line : geki.incantations()) {
            helper.assertTrue(KidoLogic.hears(geki, gekiId, line) && KidoLogic.hears(geki, gekiId, line + " " + gekiCall),
                    "Geki does not hear its own incantation, alone or with its call: " + line);
            helper.assertFalse(KidoLogic.hears(rikujokoro, rikujokoroId, line)
                    || KidoLogic.hears(rikujokoro, rikujokoroId, line + " " + rikujokoroCall),
                    "Rikujōkōrō hears Geki's incantation: " + line);
        }
        for (String line : rikujokoro.incantations()) {
            helper.assertTrue(KidoLogic.hears(rikujokoro, rikujokoroId, line)
                    && KidoLogic.hears(rikujokoro, rikujokoroId, line + " " + rikujokoroCall),
                    "Rikujōkōrō does not hear its own incantation, alone or with its call: " + line);
            helper.assertFalse(KidoLogic.hears(geki, gekiId, line) || KidoLogic.hears(geki, gekiId, line + " " + gekiCall),
                    "Geki hears Rikujōkōrō's incantation: " + line);
        }
        helper.assertFalse(KidoLogic.hears(geki, gekiId, gekiCall), "Geki hears its call alone");
        helper.assertFalse(KidoLogic.hears(rikujokoro, rikujokoroId, rikujokoroCall), "Rikujōkōrō hears its call alone");

        // Canon gives Geki's chant to Hōrin too, which the mod does not ship: of the mod's spells, only Geki hears it.
        String gekiRomaji = romaji(helper, KidoSpell.GEKI);
        for (Holder.Reference<KidoSpell> other : KidoSpell.sorted(spells)) {
            if (BleachMod.MODID.equals(other.key().location().getNamespace())) {
                boolean hears = KidoLogic.hears(other.value(), other.key().location(), gekiRomaji);
                helper.assertTrue(hears == other.key().equals(KidoSpell.GEKI),
                        other.key().location() + (hears ? " hears" : " does not hear") + " Geki's romaji");
            }
        }
        SoulData soul = shinigami(400.0F, 28);
        helper.assertTrue(id(KidoLogic.heard(soul, spells, gekiRomaji)).equals(Optional.of(KidoSpell.GEKI)),
                "Geki's romaji was heard as " + id(KidoLogic.heard(soul, spells, gekiRomaji)));
        String rikujokoroEnglish = english(helper, KidoSpell.RIKUJOKORO);
        helper.assertTrue(id(KidoLogic.heard(soul, spells, rikujokoroEnglish)).equals(Optional.of(KidoSpell.RIKUJOKORO)),
                "Rikujōkōrō's English was heard as " + id(KidoLogic.heard(soul, spells, rikujokoroEnglish)));

        // Chanted from Kidō 7 and 22, by name alone only from 12 and 28.
        assertRefusal(helper, KidoLogic.gate(geki, KidoLogic.Form.CHANT, 6), KidoLogic.Refusal.NEEDS_LEVEL,
                "Geki chanted at Kidō 6");
        assertRefusal(helper, KidoLogic.gate(geki, KidoLogic.Form.CHANT, 7), null, "Geki chanted at Kidō 7");
        assertRefusal(helper, KidoLogic.gate(geki, KidoLogic.Form.NAME, 11), KidoLogic.Refusal.NEEDS_NAME_LEVEL,
                "Geki by name at Kidō 11");
        assertRefusal(helper, KidoLogic.gate(geki, KidoLogic.Form.NAME, 12), null, "Geki by name at Kidō 12");
        assertRefusal(helper, KidoLogic.gate(rikujokoro, KidoLogic.Form.CHANT, 21), KidoLogic.Refusal.NEEDS_LEVEL,
                "Rikujōkōrō chanted at Kidō 21");
        assertRefusal(helper, KidoLogic.gate(rikujokoro, KidoLogic.Form.CHANT, 22), null, "Rikujōkōrō chanted at Kidō 22");
        assertRefusal(helper, KidoLogic.gate(rikujokoro, KidoLogic.Form.NAME, 27), KidoLogic.Refusal.NEEDS_NAME_LEVEL,
                "Rikujōkōrō by name at Kidō 27");
        assertRefusal(helper, KidoLogic.gate(rikujokoro, KidoLogic.Form.NAME, 28), null, "Rikujōkōrō by name at Kidō 28");

        // By name each keeps half its strength at its by-name level, 2% more a level up to 80%; chanted, all of it.
        assertNear(helper, KidoLogic.share(geki, KidoLogic.Form.NAME, 12), 0.5, "the share of Geki by name at Kidō 12");
        assertNear(helper, KidoLogic.share(geki, KidoLogic.Form.NAME, 26), 0.78, "the share of Geki by name at Kidō 26");
        assertNear(helper, KidoLogic.share(geki, KidoLogic.Form.NAME, 27), 0.8, "the share of Geki by name at Kidō 27");
        assertNear(helper, KidoLogic.share(geki, KidoLogic.Form.NAME, 40), 0.8, "the share of Geki by name at Kidō 40");
        assertNear(helper, KidoLogic.share(rikujokoro, KidoLogic.Form.NAME, 28), 0.5,
                "the share of Rikujōkōrō by name at Kidō 28");
        assertNear(helper, KidoLogic.share(rikujokoro, KidoLogic.Form.NAME, 43), 0.8,
                "the share of Rikujōkōrō by name at Kidō 43");
        assertNear(helper, KidoLogic.share(geki, KidoLogic.Form.CHANT, 7), 1.0, "the share of Geki chanted");
        assertNear(helper, KidoLogic.share(rikujokoro, KidoLogic.Form.CHANT, 22), 1.0, "the share of Rikujōkōrō chanted");

        // Cast by name, each may burst in the hand: 30% at its by-name level, and nothing ten levels on.
        assertNear(helper, KidoLogic.misfireChance(geki, 12), 0.3, "Geki's misfire chance at Kidō 12");
        assertNear(helper, KidoLogic.misfireChance(geki, 22), 0.0, "Geki's misfire chance at Kidō 22");
        assertNear(helper, KidoLogic.misfireChance(rikujokoro, 28), 0.3, "Rikujōkōrō's misfire chance at Kidō 28");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void aChantedBindBindsAndHoldsAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell geki = spell(helper, KidoSpell.GEKI).value();
        KidoSpell rikujokoro = spell(helper, KidoSpell.RIKUJOKORO).value();
        // Kidō 12 chants Geki, and is just able to cast it by name.
        Player player = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 12));
        SoulData data = SoulLogic.get(player);
        downTheLane(player);
        IronGolem golem = golem(helper, 5, 8);
        IronGolem fresh = null;
        IronGolem marked = golem(helper, 6, 12);
        IronGolem rods = golem(helper, 6, 4);
        Player soul = victim(helper, GameType.SURVIVAL, 400.0F);
        Player chanter = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 12));
        try {
            // Heard and begun: it goes off a chant's time later, and nothing is spent or bound until then.
            heardAs(helper, KidoEvents.chant(player, romaji(helper, KidoSpell.GEKI), now), KidoEvents.Heard.Outcome.STARTED,
                    KidoSpell.GEKI, null, "Geki's romaji at Kidō 12");
            KidoLogic.Chant chant = pending(data);
            helper.assertTrue(chant != null && chant.firesAt() == now + 30L,
                    "the chant goes off as " + chant + ", not at " + (now + 30L));
            KidoEvents.tick(player, data, now + 29L);
            helper.assertTrue(pending(data) == chant && unbound(golem), "a tick short of its time, the chant is "
                    + pending(data) + " and the golem bound " + boundTicks(golem) + " and held " + heldTicks(golem));
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 400.0, "the reiryoku a tick short of the chant's time");

            // On time, at full strength: 400 at Kidō 12 makes 488.32 against the golem's 100, so both whole, at once.
            KidoEvents.tick(player, data, now + 30L);
            helper.assertTrue(pending(data) == null, "the chant was still pending once it went off: " + pending(data));
            helper.assertTrue(boundTicks(golem) == 40 && heldTicks(golem) == 40,
                    "the chanted Geki bound the golem " + boundTicks(golem) + " ticks and held it " + heldTicks(golem));
            // Wary for twice the longer of the two from the tick it went off, and of Geki alone.
            helper.assertTrue(KidoLogic.wary(golem, geki, now + 109L) && !KidoLogic.wary(golem, geki, now + 110L),
                    "the golem is not wary of Geki for twice its 40 ticks");
            helper.assertFalse(KidoLogic.wary(golem, sai, now + 31L), "the golem Geki bound is wary of Sai too");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 376.0, "the reiryoku after the chanted Geki");
            helper.assertFalse(KidoLogic.ready(data, now + 89L), "the hands were free a tick before Geki's cooldown");
            helper.assertTrue(KidoLogic.ready(data, now + 90L), "the hands were not free after Geki's cooldown");
            helper.assertTrue(data.xp(Stat.KIDO) == 8, "the chanted Geki on the golem trained " + data.xp(Stat.KIDO)
                    + " Kidō xp, not twice 3 and 1");
            List<CeroBeam> traces = level.getEntitiesOfClass(CeroBeam.class, helper.getBounds(),
                    beam -> beam.isFired() && beam.colour() == GEKI_COLOUR);
            helper.assertTrue(traces.size() == 1 && traces.get(0).reach().length() > 1.5
                    && traces.get(0).reach().length() < 4.0, "Geki drew " + traces.stream()
                    .map(trace -> trace.reach().length()).toList() + " blocks of trace");
            // One spell, one look: the bound arms and the held body both glow in Geki's red.
            assertDrawn(helper, golem, false, KidoSpell.BindStyle.GLOW, GEKI_COLOUR, "Geki's bind of the golem's arms");
            assertDrawn(helper, golem, true, KidoSpell.BindStyle.GLOW, GEKI_COLOUR, "Geki's hold of the golem's body");

            // Sai leaves its own look on the arms, and the body it never held keeps the hold's own default.
            assertBind(helper, KidoEvents.bindOne(player, marked, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on a fresh golem");
            assertDrawn(helper, marked, false, KidoSpell.BindStyle.DEFAULT, SAI_COLOUR, "Sai's bind of the golem's arms");
            assertDrawn(helper, marked, true, KidoSpell.BindStyle.DEFAULT, BakudoEffect.HELD_COLOUR,
                    "the hold of a golem only Sai bound");

            // By name it keeps half its strength: 244.16 over a soul of 400, so both times fall to the same 0.61.
            float share = KidoLogic.share(geki, KidoLogic.Form.NAME, 12);
            assertNear(helper, share, 0.5, "the share of Geki by name at Kidō 12");
            float ratio = KidoLogic.bindRatio(KidoLogic.bindStrength(geki, player, data, share), soul);
            assertNear(helper, ratio, 0.6104, 0.001, "Geki's strength by name over a soul of 400");
            assertBind(helper, KidoEvents.bindOne(player, soul, geki, share, now + 30L), KidoLogic.BindResult.BOUND,
                    "Geki by name on a soul");
            helper.assertTrue(boundTicks(soul) == 24 && heldTicks(soul) == 24,
                    "Geki by name bound the soul " + boundTicks(soul) + " ticks and held it " + heldTicks(soul));

            // A misfire by name is paid for and waited out, and binds nothing; a bind has no damage to hurt with.
            discard(golem);
            fresh = golem(helper, 5, 8);
            assertRefusal(helper, KidoEvents.cast(player, geki, KidoLogic.Form.NAME, now + 90L, 0.0),
                    KidoLogic.Refusal.MISFIRED, "Geki by name at Kidō 12 with a roll of 0");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 352.0, "the reiryoku after a misfired Geki");
            helper.assertFalse(KidoLogic.ready(data, now + 149L), "the hands were free a tick before a misfire's cooldown");
            helper.assertTrue(KidoLogic.ready(data, now + 150L), "the hands were not free after a misfire's cooldown");
            helper.assertTrue(unbound(fresh), "a misfired Geki bound the golem " + boundTicks(fresh) + " ticks and held it "
                    + heldTicks(fresh));
            HollowPlayerTests.assertClose(helper, player.getHealth(), 20.0, "the health after a misfired Geki");
            helper.assertTrue(data.xp(Stat.KIDO) == 8, "a misfired Geki trained Kidō to " + data.xp(Stat.KIDO) + " xp");

            // Arms bound by another's Sai in the middle of the chant break it, with nothing spent.
            assertChantEnds(helper, chanter, KidoSpell.GEKI, now, () -> assertBind(helper,
                    KidoEvents.bindOne(player, chanter, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on a soul chanting Geki"), "a soul bound as it chants Geki");

            // Rikujōkōrō's 721.28 binds and holds a golem for the whole five seconds, drawn once as six rods.
            assertBind(helper, KidoEvents.bindOne(player, rods, rikujokoro, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Rikujōkōrō on a fresh golem");
            helper.assertTrue(boundTicks(rods) == 100 && heldTicks(rods) == 100,
                    "Rikujōkōrō bound the golem " + boundTicks(rods) + " ticks and held it " + heldTicks(rods));
            helper.assertTrue(KidoLogic.wary(rods, rikujokoro, now + 199L) && !KidoLogic.wary(rods, rikujokoro, now + 200L),
                    "the golem is not wary of Rikujōkōrō for twice its 100 ticks");
            assertDrawn(helper, rods, false, KidoSpell.BindStyle.RODS, RIKUJOKORO_COLOUR,
                    "Rikujōkōrō's bind of the golem's arms");
            assertDrawn(helper, rods, true, KidoSpell.BindStyle.RODS, RIKUJOKORO_COLOUR,
                    "Rikujōkōrō's hold of the golem's body");
            assertWallStands(helper);
        } finally {
            unbind(soul, chanter);
            discard(golem, fresh, marked, rods);
            discardBeams(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rikujokoroPinsATakenBody(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        KidoSpell hainawa = spell(helper, KidoSpell.HAINAWA).value();
        KidoSpell geki = spell(helper, KidoSpell.GEKI).value();
        KidoSpell rikujokoro = spell(helper, KidoSpell.RIKUJOKORO).value();
        // The first soul that may chant Rikujōkōrō, against seized bodies of its own reiryoku, each counted twice.
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 22));
        SoulData casterData = SoulLogic.get(caster);
        Player strong = caster(helper, GameType.SURVIVAL, shinigami(1_500.0F, 40));
        Player weak = caster(helper, GameType.SURVIVAL, shinigami(60.0F, 5));
        Player pinned = victim(helper, GameType.SURVIVAL, 150.0F);
        Player bySai = victim(helper, GameType.SURVIVAL, 150.0F);
        Player byHainawa = victim(helper, GameType.SURVIVAL, 150.0F);
        Player byGeki = victim(helper, GameType.SURVIVAL, 150.0F);
        Player byStrong = victim(helper, GameType.SURVIVAL, 150.0F);
        Player byWeak = victim(helper, GameType.SURVIVAL, 150.0F);
        Player unpinned = victim(helper, GameType.SURVIVAL, 150.0F);
        List<Player> seized = List.of(pinned, bySai, byHainawa, byGeki, byStrong, byWeak, unpinned);
        try {
            for (Player host : seized) {
                SoulData hostData = SoulLogic.get(host);
                VisoredLogic.setRecord(hostData, VisoredLogic.record(hostData).withRootedAt(now - DAY));
                helper.assertTrue(VisoredEvents.startTakeover(host, hostData, false, now) && KidoLogic.takenBody(host),
                        "a rooted Hollow did not seize its host");
            }

            // 150 at Kidō 22 makes 294.63 against twice the seized 150: 0.982 of five seconds held, and pinned at once.
            float ratio = KidoLogic.bindRatio(KidoLogic.bindStrength(rikujokoro, caster, casterData, 1.0F), pinned);
            assertNear(helper, ratio, 0.982, 0.005, "Rikujōkōrō's strength from 150 at Kidō 22 over a seized 150");
            assertBind(helper, KidoEvents.bindOne(caster, pinned, rikujokoro, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Rikujōkōrō on a seized body");
            helper.assertTrue(Math.abs(boundTicks(pinned) - 98) <= 1 && Math.abs(heldTicks(pinned) - 98) <= 1,
                    "Rikujōkōrō bound the seized body " + boundTicks(pinned) + " ticks and held it " + heldTicks(pinned));
            helper.assertFalse(VisoredLogic.takenOver(SoulLogic.get(pinned)) || KidoLogic.takenBody(pinned),
                    "the pinned body is still its Hollow's");
            // Wearied as a torn-off mask leaves it, and still bound and held: the pin lifts nothing.
            int weariness = BleachConfig.get(BleachConfig.VISORED_TAKEOVER_WEARINESS_SECONDS) * 20;
            for (Holder<MobEffect> wearied : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS)) {
                MobEffectInstance effect = pinned.getEffect(wearied);
                helper.assertTrue(effect != null && effect.getAmplifier() == 0 && effect.getDuration() == weariness,
                        "the pin left the host " + effect + ", not " + weariness + " ticks of "
                                + wearied.getRegisteredName());
            }
            helper.assertTrue(heldTicks(pinned) > 0 && boundTicks(pinned) > 0, "the pin lifted the bind");

            // Arms alone leave the Hollow its body, and a shorter hold does not pin it: each lands, and the Hollow stays.
            assertBind(helper, KidoEvents.bindOne(caster, bySai, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Sai on a seized body");
            helper.assertTrue(boundTicks(bySai) == 37 && heldTicks(bySai) == 0,
                    "Sai bound the seized body " + boundTicks(bySai) + " ticks and held it " + heldTicks(bySai));
            assertBind(helper, KidoEvents.bindOne(caster, byHainawa, hainawa, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Hainawa on a seized body");
            helper.assertTrue(heldTicks(byHainawa) == 38,
                    "Hainawa held the seized body " + heldTicks(byHainawa) + " ticks, not 38");
            assertBind(helper, KidoEvents.bindOne(caster, byGeki, geki, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "Geki on a seized body");
            helper.assertTrue(boundTicks(byGeki) == 27 && heldTicks(byGeki) == 27,
                    "Geki bound the seized body " + boundTicks(byGeki) + " ticks and held it " + heldTicks(byGeki));
            // However strong, Sai binds only the arms: 2121 ties them the whole three seconds, and pins nothing.
            assertBind(helper, KidoEvents.bindOne(strong, byStrong, sai, 1.0F, now), KidoLogic.BindResult.BOUND,
                    "a strong Sai on a seized body");
            helper.assertTrue(boundTicks(byStrong) == 60 && heldTicks(byStrong) == 0,
                    "the strong Sai bound the seized body " + boundTicks(byStrong) + " ticks and held it "
                            + heldTicks(byStrong));
            for (Player host : List.of(bySai, byHainawa, byGeki, byStrong)) {
                helper.assertTrue(KidoLogic.takenBody(host) && host.getEffect(MobEffects.WEAKNESS) == null
                        && heldTicks(host) < 60, "a bind that holds less than three seconds pinned a seized body");
            }

            // 60 at Kidō 5 makes 101.43 against 300, under half: shrugged off, and the Hollow keeps the body.
            assertBind(helper, KidoEvents.bindOne(weak, byWeak, rikujokoro, 1.0F, now), KidoLogic.BindResult.SHRUGGED,
                    "a weak Rikujōkōrō on a seized body");
            helper.assertTrue(unbound(byWeak) && KidoLogic.takenBody(byWeak), "a shrugged bind freed or held the body");

            // With the pin off, the same hold lands and the takeover goes on.
            withConfig(BleachConfig.KIDO_BAKUDO_PIN_SECONDS, 0, () -> {
                assertBind(helper, KidoEvents.bindOne(caster, unpinned, rikujokoro, 1.0F, now), KidoLogic.BindResult.BOUND,
                        "Rikujōkōrō on a seized body with the pin off");
                helper.assertTrue(Math.abs(heldTicks(unpinned) - 98) <= 1 && KidoLogic.takenBody(unpinned),
                        "with the pin off, Rikujōkōrō held the seized body " + heldTicks(unpinned) + " ticks and freed it "
                                + !KidoLogic.takenBody(unpinned));
                helper.assertFalse(KidoLogic.pins(1_200), "a minute's hold pins with the pin off");
            });
            // Three seconds of hold, and not a tick less; never no hold at all.
            helper.assertFalse(KidoLogic.pins(59), "a hold of 59 ticks pins");
            helper.assertTrue(KidoLogic.pins(60), "a hold of 60 ticks does not pin");
            helper.assertFalse(KidoLogic.pins(0), "no hold pins");
        } finally {
            for (Player host : seized) {
                VisoredEvents.endTakeover(host, SoulLogic.get(host), VisoredEvents.TakeoverEnd.REMOVED, now);
                host.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                host.removeEffect(MobEffects.WEAKNESS);
            }
            unbind(pinned, bySai, byHainawa, byGeki, byStrong, byWeak, unpinned);
        }
        helper.succeed();
    }

    // ---- Sekienton and Enkōsen ----

    @GameTest(template = "field")
    public static void sekientonLosesTheCasterInItsSmoke(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 8));
        SoulData data = SoulLogic.get(caster);
        KidoLogic.choose(data, KidoSpell.SEKIENTON.location());
        // Two blocks from the caster, within the smoke's 4; six, beyond them; and a creeper within its swell's 3.
        IronGolem inside = golem(helper, 3, 8);
        IronGolem outside = golem(helper, 7, 8);
        Creeper creeper = HollowPlayerTests.placed(helper, EntityType.CREEPER, 3, 10);
        IronGolem late = null;
        try {
            // Struck by the caster: each still golem's revenge goal, started as a blow would start it, hunts it.
            for (IronGolem golem : List.of(inside, outside)) {
                golem.setLastHurtByMob(caster);
                hurtBy(golem).start();
                helper.assertTrue(hurtBy(golem).isRunning() && golem.getTarget() == caster,
                        "the struck golem does not hunt the caster: " + golem.getTarget());
            }
            // The still creeper has no goals: its swell, started on the caster, keeps the caster as a target of its own.
            creeper.setTarget(caster);
            creeper.setSwellDir(1);
            creeper.goalSelector.addGoal(2, new SwellGoal(creeper));
            WrappedGoal swell = creeper.goalSelector.getAvailableGoals().stream()
                    .filter(goal -> goal.getGoal() instanceof SwellGoal)
                    .findFirst()
                    .orElseThrow();
            swell.start();
            helper.assertTrue(swell.isRunning() && creeper.getTarget() == caster && creeper.getSwellDir() == 1,
                    "the creeper does not swell at the caster");

            // Cast by name, as it always is: it has no incantation, so it never misfires.
            assertRefusal(helper, press(caster, now), null, "Sekienton");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 141.0, "the reiryoku after Sekienton");
            helper.assertTrue(!KidoLogic.ready(data, now + 199L) && KidoLogic.ready(data, now + 200L),
                    "Sekienton did not keep the hands busy for 200 ticks");
            helper.assertTrue(data.xp(Stat.KIDO) == 3, "Sekienton trained " + data.xp(Stat.KIDO) + " Kidō xp, not 3");
            KidoSmoke.Cloud cloud = KidoSmoke.cloud(data);
            helper.assertTrue(cloud != null && cloud.centre().equals(caster.position()) && cloud.endsAt() == now + 80L
                    && cloud.nextPulseAt() == now + 4L, "Sekienton's cloud is " + cloud);

            // In the smoke, the golem's hunt is stopped and its target dropped, and it is lost to the caster for 3 seconds.
            helper.assertTrue(!hurtBy(inside).isRunning() && inside.getTarget() == null,
                    "the golem in the smoke still hunts " + inside.getTarget());
            helper.assertTrue(KidoSmoke.lost(inside, caster), "the golem in the smoke is not lost to the caster");
            long until = inside.getData(SoulAttachments.LOST).until();
            helper.assertTrue(until == now + 60L, "the golem in the smoke is lost until " + until + ", not " + (now + 60L));
            // Meanwhile it cannot pick the caster out: not by a search, nor by a target set, nor by a blow.
            helper.assertFalse(TargetingConditions.forCombat().ignoreLineOfSight().test(inside, caster),
                    "the lost golem's search finds the caster");
            inside.setTarget(caster);
            helper.assertTrue(inside.getTarget() == null, "the lost golem took the caster as its target");
            inside.tickCount++;
            inside.setLastHurtByMob(caster);
            helper.assertFalse(hurtBy(inside).canUse(), "a blow from the caster sets the lost golem hunting it");
            // Once the mark has run out, the same blow would.
            KidoSmoke.markLost(inside, caster, now);
            helper.assertTrue(hurtBy(inside).canUse(), "a blow from the caster does not set the golem whose mark ran out"
                    + " hunting it");

            // Beyond the smoke, the other golem hunts on.
            helper.assertTrue(hurtBy(outside).isRunning() && outside.getTarget() == caster
                    && !KidoSmoke.lost(outside, caster), "the golem beyond the smoke lost the caster");
            // The creeper's swell is stopped and its fuse dies down; ticked, the swell does not begin again.
            helper.assertTrue(creeper.getTarget() == null && creeper.getSwellDir() == -1 && !swell.isRunning(),
                    "the creeper in the smoke still swells at " + creeper.getTarget() + ": " + creeper.getSwellDir());
            creeper.goalSelector.tick();
            helper.assertTrue(creeper.getSwellDir() == -1 && !swell.isRunning(),
                    "the creeper's swell began again: " + creeper.getSwellDir());

            // One that comes after the cast is caught at the next pulse, 4 ticks on, and not before.
            late = golem(helper, 1, 6);
            late.setLastHurtByMob(caster);
            hurtBy(late).start();
            KidoSmoke.tick(caster, data, now + 3L);
            helper.assertTrue(hurtBy(late).isRunning() && late.getTarget() == caster,
                    "the smoke caught the late golem before its next pulse");
            KidoSmoke.tick(caster, data, now + 4L);
            helper.assertTrue(!hurtBy(late).isRunning() && late.getTarget() == null && KidoSmoke.lost(late, caster),
                    "the smoke's next pulse did not lose the caster to the late golem");
            long lateUntil = late.getData(SoulAttachments.LOST).until();
            helper.assertTrue(lateUntil == now + 64L, "the late golem is lost until " + lateUntil + ", not " + (now + 64L));
            KidoSmoke.Cloud pulsed = KidoSmoke.cloud(data);
            helper.assertTrue(pulsed != null && pulsed.nextPulseAt() == now + 8L,
                    "after its second pulse the cloud is " + pulsed);

            // Its 4 seconds up, the cloud is gone.
            KidoSmoke.tick(caster, data, now + 80L);
            helper.assertTrue(KidoSmoke.cloud(data) == null,
                    "Sekienton's cloud outlived its 4 seconds: " + KidoSmoke.cloud(data));
        } finally {
            caster.removeEffect(MobEffects.MOVEMENT_SPEED);
            dropWards(caster);
            unlose(inside, outside, creeper, late);
            discard(inside, outside, creeper, late);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void sekientonBlindsAndQuickens(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell sekienton = spell(helper, KidoSpell.SEKIENTON).value();
        KidoSpell enkosen = spell(helper, KidoSpell.ENKOSEN).value();
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 8));
        SoulData data = SoulLogic.get(caster);
        KidoLogic.choose(data, KidoSpell.SEKIENTON.location());
        Player even = victim(helper, GameType.SURVIVAL, 150.0F);
        Player strong = victim(helper, GameType.SURVIVAL, 1_500.0F);
        Player creative = victim(helper, GameType.CREATIVE, 150.0F);
        Player negated = victim(helper, GameType.SURVIVAL, 150.0F);
        Player friend = victim(helper, GameType.SURVIVAL, 150.0F);
        NegacionLight light = NegacionLight.place(level, negated, null);
        // Fresh mocks, so neither meets the first caster's cooldown.
        Player heldCaster = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 8));
        Player boundCaster = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 8));
        SoulData boundData = SoulLogic.get(boundCaster);
        KidoLogic.choose(boundData, KidoSpell.SEKIENTON.location());
        SoulData lent = shinigami(400.0F, 12);
        ShinigamiLogic.lend(lent, now);
        Player lender = caster(helper, GameType.SURVIVAL, lent);
        Scoreboard scoreboard = level.getScoreboard();
        Pig pig = null;
        try {
            // Below its level it is refused before any cost; at it, the caster is quickened for 2 seconds at Speed II,
            // with an icon and no swirl.
            data.setLevel(Stat.KIDO, 7);
            assertRefusal(helper, KidoEvents.cast(caster, sekienton, KidoLogic.Form.NAME, now), KidoLogic.Refusal.NEEDS_LEVEL,
                    "Sekienton at Kidō 7");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 150.0, "the reiryoku after Sekienton below its level");
            helper.assertTrue(KidoLogic.ready(data, now), "Sekienton below its level kept the hands busy");
            data.setLevel(Stat.KIDO, 8);
            assertRefusal(helper, press(caster, now), null, "Sekienton at Kidō 8");
            MobEffectInstance quick = caster.getEffect(MobEffects.MOVEMENT_SPEED);
            helper.assertTrue(quick != null && quick.getAmplifier() == 1 && quick.getDuration() == 40 && !quick.isVisible()
                    && quick.showIcon(), "Sekienton quickened its caster with " + quick + ", not Speed II for 40 ticks");
            KidoSmoke.Cloud cloud = KidoSmoke.cloud(data);
            helper.assertTrue(cloud != null, "Sekienton raised no cloud");

            // A player as strong as the caster is blinded for the whole 3 seconds, with an icon and no swirl.
            helper.assertTrue(KidoSmoke.smokeOn(caster, cloud, even, now), "Sekienton's smoke did not take the even soul");
            MobEffectInstance blind = blindness(even);
            helper.assertTrue(blind != null && blind.getDuration() == 60 && blind.getAmplifier() == 0 && !blind.isVisible()
                    && blind.showIcon() && !blind.isAmbient(),
                    "Sekienton blinded the even soul with " + blind + ", not 60 ticks with an icon and no swirl");

            // Ten times the caster's reiatsu lets 3 parts in 10 of the blindness through.
            assertNear(helper, KidoLogic.gap(caster, strong), 0.3, "the gap to a soul of ten times the reiatsu");
            helper.assertTrue(KidoSmoke.smokeOn(caster, cloud, strong, now),
                    "Sekienton's smoke did not take the strong soul");
            MobEffectInstance dimmed = blindness(strong);
            helper.assertTrue(dimmed != null && dimmed.getDuration() == 18,
                    "Sekienton blinded the strong soul with " + dimmed + ", not 18 ticks");

            // Creative, Negación's hold and the caster itself are spared.
            helper.assertFalse(KidoSmoke.smokeOn(caster, cloud, creative, now), "Sekienton's smoke took a creative player");
            helper.assertTrue(blindness(creative) == null, "Sekienton's smoke blinded a creative player");
            helper.assertTrue(light != null && negated.startRiding(light, true) && Negacion.held(negated),
                    "the mock Shinigami could not be held");
            helper.assertFalse(KidoSmoke.smokeOn(caster, cloud, negated, now),
                    "Sekienton's smoke took a soul Negación holds");
            helper.assertTrue(blindness(negated) == null, "Sekienton's smoke blinded a soul Negación holds");
            helper.assertFalse(KidoSmoke.smokeOn(caster, cloud, caster, now), "Sekienton's smoke took its own caster");
            helper.assertTrue(blindness(caster) == null, "Sekienton's smoke blinded its own caster");

            // A creature without a soul feels it whole, and is lost to the caster; the gap cuts the lost time as it cuts
            // the blindness.
            pig = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 10);
            assertNear(helper, KidoLogic.gap(caster, pig), 1.0, "the gap to a creature without a soul");
            helper.assertTrue(KidoSmoke.smokeOn(caster, cloud, pig, now) && KidoSmoke.lost(pig, caster),
                    "Sekienton's smoke did not lose the caster to the pig");
            helper.assertTrue(KidoSmoke.lostTicks(sekienton, 1.0F, 0.3F) == 18, "through a gap of 0.3 Sekienton leaves a"
                    + " creature lost for " + KidoSmoke.lostTicks(sekienton, 1.0F, 0.3F) + " ticks");
            helper.assertTrue(KidoSmoke.lostTicks(sekienton, 1.0F, 1.0F) == 60, "Sekienton leaves a creature lost for "
                    + KidoSmoke.lostTicks(sekienton, 1.0F, 1.0F) + " ticks");

            // A held body may still raise it, and is quickened, but no Speed moves a body the hold roots.
            heldCaster.addEffect(new MobEffectInstance(ModEffects.BAKUDO_HELD, 40));
            assertRefusal(helper, KidoEvents.cast(heldCaster, sekienton, KidoLogic.Form.NAME, now), null,
                    "Sekienton from a held body");
            MobEffectInstance heldQuick = heldCaster.getEffect(MobEffects.MOVEMENT_SPEED);
            helper.assertTrue(heldQuick != null && heldQuick.getAmplifier() == 1,
                    "Sekienton quickened the held caster with " + heldQuick + ", not Speed II");
            assertNear(helper, speed(heldCaster), 0.0, 0.001, "the held caster's speed");

            // Bound arms may not, and pay nothing.
            boundCaster.addEffect(new MobEffectInstance(ModEffects.BAKUDO_BOUND, 40));
            assertRefusal(helper, press(boundCaster, now), KidoLogic.Refusal.BOUND, "Sekienton from bound arms");
            HollowPlayerTests.assertClose(helper, boundData.reiryoku(), 150.0, "the bound caster's reiryoku");
            helper.assertTrue(KidoSmoke.cloud(boundData) == null, "bound arms raised a cloud");

            // A lender may cast neither by name; neither has an incantation, so neither misfires.
            KidoLogic.choose(lent, KidoSpell.SEKIENTON.location());
            assertRefusal(helper, press(lender, now), KidoLogic.Refusal.LENT,
                    "the Sekienton of a Shinigami whose power is lent");
            KidoLogic.choose(lent, KidoSpell.ENKOSEN.location());
            assertRefusal(helper, press(lender, now), KidoLogic.Refusal.LENT,
                    "the Enkōsen of a Shinigami whose power is lent");
            helper.assertTrue(KidoLogic.misfireChance(sekienton, 8) == 0.0 && KidoLogic.misfireChance(enkosen, 12) == 0.0,
                    "Sekienton misfires at " + KidoLogic.misfireChance(sekienton, 8) + " and Enkōsen at "
                            + KidoLogic.misfireChance(enkosen, 12));

            // A teammate with friendly fire off is spared. Every mock player has the same name, so the team holds them
            // all: it is made only once every other player has been weighed.
            PlayerTeam team = scoreboard.addPlayerTeam(TEAM);
            team.setAllowFriendlyFire(false);
            scoreboard.addPlayerToTeam(caster.getScoreboardName(), team);
            helper.assertFalse(KidoSmoke.smokeOn(caster, cloud, friend, now),
                    "Sekienton's smoke took a teammate with friendly fire off");
            helper.assertTrue(blindness(friend) == null, "Sekienton's smoke blinded a teammate with friendly fire off");
        } finally {
            removeTeam(scoreboard);
            negated.stopRiding();
            unbind(heldCaster, boundCaster);
            for (Player player : List.of(caster, heldCaster, boundCaster, even, strong, creative, negated, friend)) {
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
                player.removeEffect(MobEffects.BLINDNESS);
            }
            dropWards(caster, heldCaster, boundCaster);
            unlose(pig);
            discard(light, pig);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void enkosenTakesBlowsFromTheFront(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell enkosen = spell(helper, KidoSpell.ENKOSEN).value();
        KidoSpell sekienton = spell(helper, KidoSpell.SEKIENTON).value();
        KidoSpell tenran = spell(helper, KidoSpell.TENRAN).value();
        Player wearer = caster(helper, GameType.SURVIVAL, shinigami(150.0F, 12));
        SoulData data = SoulLogic.get(wearer);
        KidoLogic.choose(data, KidoSpell.ENKOSEN.location());
        downTheLane(wearer);
        wearer.setHealth(20.0F);
        Vec3 ahead = wearer.position().add(2.0, 0.0, 0.0);
        Vec3 behind = wearer.position().add(-2.0, 0.0, 0.0);
        // Three blocks ahead, where the shield faces.
        InnerHollow inner = HollowPlayerTests.placed(helper, ModEntities.INNER_HOLLOW.get(), 4, 8);
        // A Tenran caster down the lane, facing the wearer, its gust from a marker at the hand as a gust strikes.
        Player gusting = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 18));
        SoulData gustData = SoulLogic.get(gusting);
        Vec3 down = helper.absoluteVec(DOWN_THE_LANE);
        gusting.moveTo(down.x, down.y, down.z, 90.0F, 0.0F);
        gusting.setYHeadRot(90.0F);
        Marker marker = new Marker(EntityType.MARKER, level);
        marker.setPos(CeroBeam.muzzleOf(gusting, 1.0F));
        DamageSource gust = CeroBeam.sourceOf(level, gusting, marker, new CeroBeam.Kind(KidoEvents.HADO, true, 1));
        try {
            // Below its level it is refused before any cost; at it, the shield stands before the wearer for 3 seconds,
            // as strong as the spell's power, with no blow taken yet.
            data.setLevel(Stat.KIDO, 11);
            assertRefusal(helper, KidoEvents.cast(wearer, enkosen, KidoLogic.Form.NAME, now), KidoLogic.Refusal.NEEDS_LEVEL,
                    "Enkōsen at Kidō 11");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 150.0, "the reiryoku after Enkōsen below its level");
            helper.assertTrue(KidoLogic.ready(data, now), "Enkōsen below its level kept the hands busy");
            data.setLevel(Stat.KIDO, 12);
            assertRefusal(helper, press(wearer, now), null, "Enkōsen at Kidō 12");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 138.0, "the reiryoku after Enkōsen");
            helper.assertTrue(!KidoLogic.ready(data, now + 159L) && KidoLogic.ready(data, now + 160L),
                    "Enkōsen did not keep the hands busy for 160 ticks");
            helper.assertTrue(data.xp(Stat.KIDO) == 4, "Enkōsen trained " + data.xp(Stat.KIDO) + " Kidō xp, not 4");
            KidoWard.Shield shield = KidoWard.shield(data);
            helper.assertTrue(shield != null && shield.endsAt() == now + 60L && shield.lastBlowAt() == now - 10L
                    && KidoWard.up(data, now), "Enkōsen's shield is " + shield);
            assertNear(helper, shield.strength(), 9.516, 0.001, "Enkōsen's strength at 150 and Kidō 12");
            assertNear(helper, left(shield), 9.516, 0.001, "what is left of a fresh Enkōsen");

            // A blow from the front is taken whole: no harm and no frames.
            wearer.invulnerableTime = 0;
            helper.assertFalse(wearer.hurt(attackAt(helper, ahead), 4.0F), "a blow of 4 from the front landed");
            HollowPlayerTests.assertClose(helper, wearer.getHealth(), 20.0, "the health after a blow the shield took");
            assertNear(helper, left(KidoWard.shield(data)), 5.516, 0.001, "what is left after a blow of 4");
            helper.assertTrue(wearer.invulnerableTime == 0, "a blow the shield took left frames of "
                    + wearer.invulnerableTime);
            // In the moment of that blow, a smaller one costs nothing.
            helper.assertFalse(wearer.hurt(attackAt(helper, ahead), 3.0F), "a blow of 3 in the moment of one of 4 landed");
            assertNear(helper, left(KidoWard.shield(data)), 5.516, 0.001, "what is left after a blow of 3 in the moment");

            // It covers 120 degrees about where the head faces: 59 off is within, 61 off is not, and behind is not.
            double near = Math.toRadians(59.0);
            double far = Math.toRadians(61.0);
            Vec3 within = wearer.position().add(2.0 * Math.cos(near), 0.0, 2.0 * Math.sin(near));
            Vec3 without = wearer.position().add(2.0 * Math.cos(far), 0.0, 2.0 * Math.sin(far));
            helper.assertTrue(KidoWard.covers(wearer, attackAt(helper, within)),
                    "the shield does not cover a blow from 59 degrees off");
            helper.assertFalse(KidoWard.covers(wearer, attackAt(helper, without)),
                    "the shield covers a blow from 61 degrees off");
            helper.assertFalse(KidoWard.covers(wearer, attackAt(helper, behind)), "the shield covers a blow from behind");
            // Magic passes it, as it passes a shield in hand, and so does a Sprenger; a blow from no place meets nothing;
            // and the host's own Hollow is its own fight, ahead or not.
            helper.assertFalse(KidoWard.covers(wearer, damageOf(helper, DamageTypes.MAGIC, ahead)),
                    "the shield covers magic");
            helper.assertFalse(KidoWard.covers(wearer, damageOf(helper, GintoCast.SPRENGER, ahead)),
                    "the shield covers a Sprenger");
            helper.assertFalse(KidoWard.covers(wearer, damageOf(helper, DamageTypes.MOB_ATTACK, null)),
                    "the shield covers a blow from no place");
            helper.assertTrue(KidoWard.faces(wearer, inner.position(), enkosen.effect().angle()),
                    "the inner Hollow does not stand before the shield");
            helper.assertFalse(KidoWard.covers(wearer, wearer.damageSources().mobAttack(inner)),
                    "the shield covers the inner Hollow's blow");

            // From behind, a blow lands whole and the shield is untouched.
            land(helper, wearer, attackAt(helper, behind), 4.0F, "the blow of 4 from behind");
            HollowPlayerTests.assertClose(helper, wearer.getHealth(), 16.0, "the health after a blow from behind");
            assertNear(helper, left(KidoWard.shield(data)), 5.516, 0.001, "what is left after a blow from behind");
            // A bigger one in the moment costs only what it has over the moment's 4, 6; that is past what is left, so it
            // breaks the shield and lands its 0.484 over.
            wearer.invulnerableTime = 0;
            helper.assertTrue(wearer.hurt(attackAt(helper, ahead), 10.0F), "a blow of 10 from the front did not land");
            assertNear(helper, wearer.getHealth(), 15.516, "the health after the blow that broke the shield");
            helper.assertTrue(KidoWard.shield(data) == null, "the shield outlived a blow bigger than what was left");

            // A moment is 10 ticks: within it a smaller blow is free, after it one costs its whole, and so does a
            // volley's arrow within it. A blow of all that is left breaks the shield with nothing over; a bigger one
            // lets what it has over by.
            KidoWard.Shield struck = new KidoWard.Shield(enkosen, 9.516F, 9.516F, now + 60L, now, 4.0F);
            assertNear(helper, left(KidoWard.absorb(struck, 3.0F, now + 9L, false).shield()), 9.516, 0.001,
                    "what is left after a blow of 3 nine ticks after one of 4");
            assertNear(helper, left(KidoWard.absorb(struck, 3.0F, now + 10L, false).shield()), 6.516, 0.001,
                    "what is left after a blow of 3 ten ticks after one of 4");
            assertNear(helper, left(KidoWard.absorb(struck, 3.0F, now + 1L, true).shield()), 6.516, 0.001,
                    "what is left after a volley's arrow of 3 a tick after a blow of 4");
            KidoWard.Absorbed emptied = KidoWard.absorb(struck, 9.516F, now + 20L, false);
            helper.assertTrue(emptied.shield() == null && emptied.passes() == 0.0F,
                    "a blow of all that was left gave " + emptied);
            KidoWard.Absorbed broken = KidoWard.absorb(struck, 12.0F, now + 20L, false);
            helper.assertTrue(broken.shield() == null, "a blow of 12 did not break the shield: " + broken);
            assertNear(helper, broken.passes(), 2.484, 0.001, "what a blow of 12 lets by");

            // A gust it takes whole neither harms nor blinds.
            KidoWard.raise(wearer, data, enkosen, 1.0F, now);
            wearer.invulnerableTime = 0;
            float blow = KidoLogic.power(tenran, gustData, 1.0F);
            assertNear(helper, blow, 4.216, "Tenran's blow at 400 and Kidō 18");
            helper.assertFalse(KidoEvents.gustBlow(gusting, wearer, gust, tenran, 1.0F, blow),
                    "Tenran's gust landed on the shield");
            helper.assertTrue(blindness(wearer) == null, "Tenran's gust blinded the wearer behind its shield");
            assertNear(helper, left(KidoWard.shield(data)), 5.3, "what is left after Tenran's gust");

            // Bound arms let it fall: it covers nothing, breaks at the tick, and none is raised.
            wearer.addEffect(new MobEffectInstance(ModEffects.BAKUDO_BOUND, 40));
            helper.assertFalse(KidoWard.covers(wearer, attackAt(helper, ahead)), "the shield of bound arms covers a blow");
            KidoWard.tick(wearer, data, now);
            helper.assertTrue(KidoWard.shield(data) == null, "the shield outlived the arms bound");
            assertRefusal(helper, press(wearer, now), KidoLogic.Refusal.BOUND, "Enkōsen from bound arms");
            unbind(wearer);
            // A held body may raise one, once the hands are free.
            wearer.addEffect(new MobEffectInstance(ModEffects.BAKUDO_HELD, 40));
            assertRefusal(helper, KidoEvents.cast(wearer, enkosen, KidoLogic.Form.NAME, now + 160L, 1.0), null,
                    "Enkōsen from a held body");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 126.0, "the reiryoku after Enkōsen twice");
            unbind(wearer);
            // It stands its 3 seconds, and goes at its tick.
            helper.assertTrue(KidoWard.up(data, now + 219L) && !KidoWard.up(data, now + 220L),
                    "the shield raised at " + (now + 160L) + " does not stand until " + (now + 220L));
            KidoWard.tick(wearer, data, now + 220L);
            helper.assertTrue(KidoWard.shield(data) == null, "the shield outlived its 3 seconds");

            // Death leaves no shield on the soul it copies, and a portal drops both the smoke and the shield.
            KidoWard.raise(wearer, data, enkosen, 1.0F, now);
            helper.assertTrue(KidoWard.shield(data.copy()) == null, "the shield passed to the copied soul");
            KidoSmoke.raise(level, wearer, data, sekienton, 1.0F, now);
            helper.assertTrue(KidoSmoke.cloud(data) != null && KidoWard.shield(data) != null,
                    "the smoke and the shield were not both raised");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(wearer, Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(KidoSmoke.cloud(data) == null && KidoWard.shield(data) == null,
                    "the smoke or the shield went through the portal");
        } finally {
            unbind(wearer);
            wearer.removeEffect(MobEffects.MOVEMENT_SPEED);
            wearer.removeEffect(MobEffects.BLINDNESS);
            dropWards(wearer, gusting);
            discard(inner, marker);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void enkosenStopsAHadoBeam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell enkosen = spell(helper, KidoSpell.ENKOSEN).value();
        Player caster = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 3));
        SoulData data = SoulLogic.get(caster);
        KidoLogic.choose(data, KidoSpell.BYAKURAI.location());
        // A golem with a soul of its own, its shield raised towards the caster up the lane; another behind it.
        IronGolem wearer = wearerOf(golem(helper, 4, 8), shinigami(600.0F, 25));
        SoulData wearerData = SoulLogic.get(wearer);
        wearer.setYHeadRot(90.0F);
        IronGolem behind = golem(helper, 7, 8);
        IronGolem archered = null;
        // Never added to the world: only its place, which the blow's is, matters.
        Arrow arrow = new Arrow(EntityType.ARROW, level);
        try {
            KidoWard.raise(wearer, wearerData, enkosen, 1.0F, now);
            KidoWard.Shield raised = KidoWard.shield(wearerData);
            helper.assertTrue(raised != null, "the golem raised no shield");
            assertNear(helper, raised.strength(), 17.023, "Enkōsen's strength at 600 and Kidō 25");

            // Byakurai would pierce both; its beam stops at the shield, which pays its 6.36, and neither golem is hurt.
            caster.lookAt(EntityAnchorArgument.Anchor.EYES, wearer.getBoundingBox().getCenter());
            assertRefusal(helper, press(caster, now), null, "Byakurai at the shield");
            helper.assertTrue(unhurt(wearer) && unhurt(behind), "Byakurai's beam got past the shield: the golems are at "
                    + wearer.getHealth() + " and " + behind.getHealth());
            assertNear(helper, left(KidoWard.shield(wearerData)), 10.663, "what is left of the shield after Byakurai");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 384.0, "the reiryoku after Byakurai");

            // An arrow from the front is taken whole; from behind, the same arrow lands, and the shield is untouched.
            archered = wearerOf(golem(helper, 4, 4), shinigami(600.0F, 25));
            SoulData archeredData = SoulLogic.get(archered);
            archered.setYHeadRot(90.0F);
            KidoWard.raise(archered, archeredData, enkosen, 1.0F, now);
            arrow.setPos(archered.position().add(-2.0, 0.0, 0.0));
            helper.assertFalse(archered.hurt(level.damageSources().arrow(arrow, null), 4.0F),
                    "an arrow from the front struck the golem");
            HollowPlayerTests.assertClose(helper, archered.getHealth(), 100.0,
                    "the golem's health after an arrow from the front");
            assertNear(helper, left(KidoWard.shield(archeredData)), 13.023, "what is left after an arrow of 4");
            arrow.setPos(archered.position().add(2.0, 0.0, 0.0));
            archered.invulnerableTime = 0;
            helper.assertTrue(archered.hurt(level.damageSources().arrow(arrow, null), 4.0F),
                    "an arrow from behind did not strike the golem");
            HollowPlayerTests.assertClose(helper, archered.getHealth(), 96.0,
                    "the golem's health after an arrow from behind");
            assertNear(helper, left(KidoWard.shield(archeredData)), 13.023, "what is left after an arrow from behind");
        } finally {
            discard(wearer, behind, archered, arrow);
            discardBeams(helper);
        }
        helper.succeed();
    }

    // ---- Dankū ----

    @GameTest(template = "field")
    public static void dankuStopsArrowsAndHado(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell sokatsui = spell(helper, KidoSpell.SOKATSUI).value();
        Player wallCaster = caster(helper, GameType.SURVIVAL, shinigami(600.0F, 36));
        SoulData data = SoulLogic.get(wallCaster);
        KidoLogic.choose(data, KidoSpell.DANKU.location());
        // Just behind where the wall rises, and further down the lane.
        Pig pig = HollowPlayerTests.placed(helper, EntityType.PIG, 4, 8);
        IronGolem golem = golem(helper, 7, 8);
        Player shooter = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 8));
        SoulData shooterData = SoulLogic.get(shooter);
        Arrow arrow = null;
        try {
            // By the key, as it always is: a fifth of 600, the hands busy for 15 seconds, and 6 xp, as it strikes nothing.
            assertRefusal(helper, press(wallCaster, now), null, "Dankū at Kidō 36");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 480.0, "the reiryoku after Dankū");
            helper.assertTrue(!KidoLogic.ready(data, now + 299L) && KidoLogic.ready(data, now + 300L),
                    "Dankū did not keep the hands busy for 300 ticks");
            helper.assertTrue(data.xp(Stat.KIDO) == 6, "Dankū trained " + data.xp(Stat.KIDO) + " Kidō xp, not 6");

            // One wall, its middle 2 blocks east of the caster's feet: 5 wide across the lane, 4 tall and a quarter
            // thick, for 6 seconds, as strong as 1¼ times the caster's 600. Nothing aims at it, pushes it or bumps into it.
            List<DankuWall> raised = walls(helper);
            helper.assertTrue(raised.size() == 1, "Dankū raised " + raised.size() + " walls");
            DankuWall wall = raised.get(0);
            helper.assertTrue(wall.facing() == Direction.EAST, "Dankū's wall faces " + wall.facing());
            helper.assertTrue(wall.position().distanceTo(helper.absoluteVec(new Vec3(3.5, 2.0, 8.5))) < 1.0E-6,
                    "Dankū's wall stands at " + helper.relativeVec(wall.position()));
            AABB box = wall.getBoundingBox();
            AABB slab = new AABB(helper.absoluteVec(new Vec3(3.375, 2.0, 6.0)),
                    helper.absoluteVec(new Vec3(3.625, 6.0, 11.0)));
            helper.assertTrue(Math.abs(box.minX - slab.minX) < 1.0E-6 && Math.abs(box.maxX - slab.maxX) < 1.0E-6
                    && Math.abs(box.minY - slab.minY) < 1.0E-6 && Math.abs(box.maxY - slab.maxY) < 1.0E-6
                    && Math.abs(box.minZ - slab.minZ) < 1.0E-6 && Math.abs(box.maxZ - slab.maxZ) < 1.0E-6,
                    "Dankū's wall fills " + box + ", not " + slab);
            helper.assertTrue(wall.endsAt() == now + 120L, "Dankū's wall ends at " + wall.endsAt() + ", not " + (now + 120L));
            helper.assertTrue(wall.strength() == 750.0F && wall.caster() == wallCaster,
                    "Dankū's wall is " + wall.strength() + " strong, raised by " + wall.caster());
            helper.assertTrue(!wall.isPickable() && !wall.isPushable() && !wall.canBeCollidedWith()
                    && wall.canBeHitByProjectile(), "Dankū's wall can be aimed at, pushed or bumped into, or meets no arrow");

            // An arrow flying east meets the wall (grown by 0.3, at 3.075) before the pig (at 3.75), and drops before it.
            arrow = HollowTests.arrow(helper, helper.absoluteVec(new Vec3(2.5, 2.5, 8.5)), new Vec3(3.0, 0.0, 0.0));
            helper.assertTrue(level.addFreshEntity(arrow), "the arrow could not join the world");
            arrow.tick();
            double nearFace = helper.absoluteVec(new Vec3(3.375, 0.0, 0.0)).x;
            helper.assertTrue(arrow.getDeltaMovement().x < 0.0 && arrow.getX() < nearFace,
                    "the arrow went on at " + arrow.getDeltaMovement() + " to " + helper.relativeVec(arrow.position()));
            helper.assertTrue(unhurt(pig) && wall.standing(), "the arrow got past Dankū: the pig is at " + pig.getHealth()
                    + ", the wall standing " + wall.standing());

            // Sōkatsui, chanted at the golem: its beam from the hand (x 2.1) meets the wall 1.275 on, before the golem
            // (4.7 on), and stops there; the wall stands, and takes a second one as well.
            assertRefusal(helper, KidoEvents.cast(shooter, sokatsui, KidoLogic.Form.CHANT, now, 1.0), null,
                    "Sōkatsui at Dankū");
            helper.assertTrue(unhurt(golem) && wall.standing(), "Sōkatsui got past Dankū: the golem is at "
                    + golem.getHealth() + ", the wall standing " + wall.standing());
            HollowPlayerTests.assertClose(helper, shooterData.reiryoku(), 368.0, "the reiryoku after Sōkatsui");
            assertRefusal(helper, KidoEvents.cast(shooter, sokatsui, KidoLogic.Form.CHANT, now + 40L, 1.0), null,
                    "Sōkatsui at Dankū again");
            helper.assertTrue(unhurt(golem) && wall.standing(), "the second Sōkatsui got past Dankū: the golem is at "
                    + golem.getHealth() + ", the wall standing " + wall.standing());
            HollowPlayerTests.assertClose(helper, shooterData.reiryoku(), 336.0, "the reiryoku after two Sōkatsui");

            // Tenran's cone from the same hand finds nothing behind the wall; with the wall gone, it finds the golem.
            Vec3 hand = CeroBeam.muzzleOf(shooter, 1.0F);
            Vec3 east = new Vec3(1.0, 0.0, 0.0);
            List<LivingEntity> sheltered = CeroBeam.inCone(level, shooter, hand, east, 8.0, 70.0F);
            helper.assertTrue(sheltered.isEmpty(), "Tenran's cone reaches " + sheltered + " behind Dankū");
            wall.discard();
            List<LivingEntity> open = CeroBeam.inCone(level, shooter, hand, east, 8.0, 70.0F);
            helper.assertTrue(open.contains(golem), "with Dankū gone, Tenran's cone holds only " + open);
        } finally {
            discard(pig, golem, arrow, wallCaster, shooter);
            dropWalls(helper);
            discardBeams(helper);
        }
        helper.succeed();
    }

    /**
     * Each shot goes east from x 1.0 at the height of the golem's chest: it meets the wall's near face 2.375 on, and the
     * golem (its box grown by the beam's half-width) 5.3 on, its own box beginning 5.8 on. Behind the golem the dirt
     * wall, 10 on, is within the beam's 12.
     */
    @GameTest(template = "field")
    public static void dankuWeighsTheCeroFamily(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell danku = spell(helper, KidoSpell.DANKU).value();
        Player wallCaster = caster(helper, GameType.SURVIVAL, shinigami(600.0F, 36));
        IronGolem golem = golem(helper, 7, 8);
        Vec3 from = helper.absoluteVec(new Vec3(1.0, 2.5, 8.5));
        double nearFace = helper.absoluteVec(new Vec3(3.375, 0.0, 0.0)).x;
        Pig pig = null;
        try {
            // A Cero weighs its shooter's reiatsu whole against the wall's 750: from 500, and from 750 (a tie), it stops at
            // the near face; from 800 the wall shatters, and the beam goes on to the golem and the dirt.
            DankuWall first = raiseWall(helper, wallCaster, danku, now);
            for (float reiatsu : new float[] {500.0F, 750.0F}) {
                CeroBeam.Shot held = weighed(helper, golem, caster(helper, GameType.SURVIVAL, shinigami(reiatsu, 0)), from,
                        CeroBeam.Kind.CERO);
                helper.assertTrue(held.blocked() && !held.hitBlock() && held.struck().isEmpty(),
                        "a Cero from " + reiatsu + " at Dankū gave " + held);
                assertNear(helper, held.to().x, nearFace, 1.0E-6, "where a Cero from " + reiatsu + " stopped");
                helper.assertTrue(unhurt(golem) && first.standing(), "a Cero from " + reiatsu + " got past Dankū: the"
                        + " golem is at " + golem.getHealth() + ", the wall standing " + first.standing());
            }
            CeroBeam.Shot heavy = weighed(helper, golem, caster(helper, GameType.SURVIVAL, shinigami(800.0F, 0)), from,
                    CeroBeam.Kind.CERO);
            helper.assertTrue(first.isRemoved() && heavy.struck().contains(golem) && !unhurt(golem) && heavy.hitBlock(),
                    "a Cero from 800 at Dankū gave " + heavy + ", the wall gone " + first.isRemoved());

            // A shooter whose eyes stand 0.175 before the near face, firing from past the far one, meets the wall
            // between its eyes and its hand, so the beam goes nowhere: it never starts beyond the wall.
            DankuWall pressed = raiseWall(helper, wallCaster, danku, now);
            Player against = caster(helper, GameType.SURVIVAL, shinigami(500.0F, 0));
            Vec3 close = helper.absoluteVec(new Vec3(3.2, 2.0, 8.5));
            against.moveTo(close.x, close.y, close.z, EAST, 0.0F);
            CeroBeam.Shot within = weighed(helper, golem, against, helper.absoluteVec(new Vec3(3.8, 2.5, 8.5)),
                    CeroBeam.Kind.CERO);
            helper.assertTrue(within.blocked() && within.struck().isEmpty() && unhurt(golem) && pressed.standing(),
                    "a Cero fired from against Dankū gave " + within + ", the golem at " + golem.getHealth()
                            + ", the wall standing " + pressed.standing());
            pressed.discard();

            // A Gran Rey Cero weighs twice its shooter's: from 375 a tie, stopped; from 400 it shatters the wall.
            DankuWall second = raiseWall(helper, wallCaster, danku, now);
            CeroBeam.Shot grand = weighed(helper, golem, caster(helper, GameType.SURVIVAL, shinigami(375.0F, 0)), from,
                    GranReyCero.KIND);
            helper.assertTrue(grand.blocked() && grand.struck().isEmpty() && unhurt(golem) && second.standing(),
                    "a Gran Rey Cero from 375 at Dankū gave " + grand + ", the wall standing " + second.standing());
            CeroBeam.Shot grander = weighed(helper, golem, caster(helper, GameType.SURVIVAL, shinigami(400.0F, 0)), from,
                    GranReyCero.KIND);
            helper.assertTrue(second.isRemoved() && grander.struck().contains(golem) && !unhurt(golem),
                    "a Gran Rey Cero from 400 at Dankū gave " + grander + ", the wall gone " + second.isRemoved());

            // A Bala weighs half: from 1,500 a tie, stopped. One from 1,600 would shatter the wall, but a pig before it
            // (met 0.55 on, its box beginning 1.05 on) takes the Bala's one pierce, so the wall is never reached.
            DankuWall third = raiseWall(helper, wallCaster, danku, now);
            CeroBeam.Shot bala = weighed(helper, golem, caster(helper, GameType.SURVIVAL, shinigami(1_500.0F, 0)), from,
                    Bala.KIND);
            helper.assertTrue(bala.blocked() && bala.struck().isEmpty() && unhurt(golem) && third.standing(),
                    "a Bala from 1,500 at Dankū gave " + bala + ", the wall standing " + third.standing());
            pig = HollowPlayerTests.placed(helper, EntityType.PIG, 2, 8);
            CeroBeam.Shot spent = weighed(helper, golem, caster(helper, GameType.SURVIVAL, shinigami(1_600.0F, 0)), from,
                    Bala.KIND);
            helper.assertTrue(spent.struck().equals(List.of(pig)) && !spent.blocked() && third.standing() && unhurt(golem),
                    "a Bala from 1,600 with a pig before Dankū gave " + spent + ", the wall standing " + third.standing());

            // A Cero 2 wide either side, fired from x 1.5, has the wall within its width of the muzzle, yet a pig whose
            // box begins 0.55 on stands before the near face, 1.875 on: the pig is struck, and the wall holds the rest.
            discard(pig);
            pig = HollowPlayerTests.placed(helper, EntityType.PIG, 2, 8);
            mend(golem);
            CeroBeam.Spec wide = new CeroBeam.Spec(10.0F, 12.0, 2.0F, 0.0F, 0.0F, 0, 0.0F, 0xFFFFFF, 0, 0, false);
            CeroBeam.Shot broad = CeroBeam.fire(level, caster(helper, GameType.SURVIVAL, shinigami(500.0F, 0)), null,
                    helper.absoluteVec(new Vec3(1.5, 2.5, 8.5)), new Vec3(1.0, 0.0, 0.0), wide, CeroBeam.Kind.CERO);
            helper.assertTrue(broad.struck().equals(List.of(pig)) && broad.blocked() && third.standing() && unhurt(golem),
                    "a wide Cero with a pig just before Dankū gave " + broad + ", the wall standing " + third.standing());
        } finally {
            discard(golem, pig, wallCaster);
            dropWalls(helper);
            // What the slain pig dropped.
            level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).forEach(Entity::discard);
            level.getEntitiesOfClass(ExperienceOrb.class, helper.getBounds()).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * The crescent and the arrows are launched into the world and ticked by hand within this one tick. A crescent flies
     * from x 1.5, 1.6 a tick, so its second sweep meets the wall's near face at 3.375; an arrow of light from x 1.0, 3 a
     * tick, meets it at once, grown by 0.3, at 3.075.
     */
    @GameTest(template = "field")
    public static void dankuWeighsTheCrescentAndTheArrowsOfLight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell danku = spell(helper, KidoSpell.DANKU).value();
        Player wallCaster = caster(helper, GameType.SURVIVAL, shinigami(600.0F, 36));
        IronGolem golem = golem(helper, 7, 8);
        Vec3 root = helper.absoluteVec(new Vec3(0.5, 2.5, 8.5));
        Vec3 bow = helper.absoluteVec(new Vec3(1.0, 2.5, 8.5));
        Vec3 east = new Vec3(1.0, 0.0, 0.0);
        GetsugaTensho held = null;
        GetsugaTensho cutting = null;
        HeiligPfeil faded = null;
        HeiligPfeil piercing = null;
        try {
            // A Getsuga Tenshō weighs its wielder's reiatsu whole: from 700 the wall holds it on its second sweep.
            DankuWall first = raiseWall(helper, wallCaster, danku, now);
            mend(golem);
            held = GetsugaTensho.launch(level, caster(helper, GameType.SURVIVAL, shinigami(700.0F, 0)), root, east, 0.0F,
                    10.0F, 20, ItemStack.EMPTY, ZanpakutoBlade.DEFAULT_COLOUR);
            for (int tick = 0; tick < 3 && !held.isRemoved(); tick++) {
                held.tick();
            }
            helper.assertTrue(held.isRemoved() && first.standing() && unhurt(golem), "a crescent from 700 at Dankū flies "
                    + !held.isRemoved() + ", the wall standing " + first.standing() + ", the golem at " + golem.getHealth());
            // From 800 it shatters the wall on that sweep, and on the next, its path reaching 7.5, cuts the golem.
            mend(golem);
            cutting = GetsugaTensho.launch(level, caster(helper, GameType.SURVIVAL, shinigami(800.0F, 0)), root, east,
                    0.0F, 10.0F, 20, ItemStack.EMPTY, ZanpakutoBlade.DEFAULT_COLOUR);
            for (int tick = 0; tick < 5 && unhurt(golem) && !cutting.isRemoved(); tick++) {
                cutting.tick();
            }
            helper.assertTrue(first.isRemoved() && !unhurt(golem), "a crescent from 800 left Dankū standing "
                    + !first.isRemoved() + " and the golem at " + golem.getHealth());
            discard(cutting);

            // A Heilig Pfeil weighs half: from 1,400 the wall turns it on its first tick, and it fades.
            DankuWall second = raiseWall(helper, wallCaster, danku, now);
            mend(golem);
            faded = QuincyTests.loose(helper, caster(helper, GameType.SURVIVAL, shinigami(1_400.0F, 0)), bow, east, 10.0F,
                    0, 3.0F);
            faded.tick();
            helper.assertTrue(faded.isRemoved() && second.standing() && unhurt(golem), "a Heilig Pfeil from 1,400 at Dankū"
                    + " flies " + !faded.isRemoved() + ", the wall standing " + second.standing() + ", the golem at "
                    + golem.getHealth());
            // From 1,600 it shatters the wall on its first tick, and strikes the golem behind it on its second.
            mend(golem);
            piercing = QuincyTests.loose(helper, caster(helper, GameType.SURVIVAL, shinigami(1_600.0F, 0)), bow, east,
                    10.0F, 0, 3.0F);
            for (int tick = 0; tick < 3 && unhurt(golem) && !piercing.isRemoved(); tick++) {
                piercing.tick();
            }
            helper.assertTrue(second.isRemoved() && !unhurt(golem), "a Heilig Pfeil from 1,600 left Dankū standing "
                    + !second.isRemoved() + " and the golem at " + golem.getHealth());
        } finally {
            discard(golem, held, cutting, faded, piercing, wallCaster);
            dropWalls(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void dankuLetsBodiesAndBindsThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        buildWall(helper);
        KidoSpell danku = spell(helper, KidoSpell.DANKU).value();
        KidoSpell sai = spell(helper, KidoSpell.SAI).value();
        Player wallCaster = caster(helper, GameType.SURVIVAL, shinigami(600.0F, 36));
        Player binder = caster(helper, GameType.SURVIVAL, shinigami(400.0F, 1));
        SoulData binderData = SoulLogic.get(binder);
        downTheLane(binder);
        Pig pig = HollowPlayerTests.placed(helper, EntityType.PIG, 2, 8);
        IronGolem golem = null;
        try {
            DankuWall wall = raiseWall(helper, wallCaster, danku, now);
            // Nothing in it to bump into: the pig walks 2 blocks east, through the wall, from 2.5 to 4.5.
            helper.assertTrue(level.getEntityCollisions(pig, pig.getBoundingBox().expandTowards(2.0, 0.0, 0.0)).isEmpty(),
                    "the pig would bump into Dankū's wall");
            pig.move(MoverType.SELF, new Vec3(2.0, 0.0, 0.0));
            assertNear(helper, pig.getX(), helper.absoluteVec(new Vec3(4.5, 0.0, 0.0)).x, "where the pig walked to");
            // Out of the way of the bind.
            discard(pig);

            // Sai's ray passes the wall and binds the golem behind it: 400 at Kidō 1 makes 408.04 against the golem's
            // 100, so its arms are bound the whole 3 seconds, and the wall stands.
            golem = golem(helper, 7, 8);
            assertNear(helper, KidoLogic.bindStrength(sai, binder, binderData, 1.0F), 408.04,
                    "Sai's strength from 400 at Kidō 1");
            assertRefusal(helper, KidoEvents.cast(binder, sai, KidoLogic.Form.NAME, now), null, "Sai through Dankū");
            helper.assertTrue(boundTicks(golem) == 60, "Sai through Dankū bound the golem " + boundTicks(golem)
                    + " ticks, not 60");
            HollowPlayerTests.assertClose(helper, binderData.reiryoku(), 388.0, "the reiryoku after Sai");
            helper.assertTrue(wall.standing(), "Sai's ray took down Dankū's wall");
        } finally {
            discard(pig, golem, wallCaster);
            dropWalls(helper);
            discardBeams(helper);
        }
        helper.succeed();
    }

    /**
     * Three walls from three casters: the first left to stand its 120 ticks (6 seconds) and go at its tick, the second's
     * caster gone and the third's dead, so each goes on its first tick.
     */
    @GameTest(template = "field", timeoutTicks = 160)
    public static void dankuGoesAtItsTimeAndIsNeverSaved(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        KidoSpell danku = spell(helper, KidoSpell.DANKU).value();
        Player kept = caster(helper, GameType.SURVIVAL, shinigami(600.0F, 36));
        Player gone = caster(helper, GameType.SURVIVAL, shinigami(600.0F, 36));
        Player slain = caster(helper, GameType.SURVIVAL, shinigami(600.0F, 36));
        try {
            DankuWall first = raiseWall(helper, kept, danku, now);
            DankuWall second = raiseWall(helper, gone, danku, now);
            DankuWall third = raiseWall(helper, slain, danku, now);
            // Never written with its chunk, though it goes when the chunk does; hidden from the unaware; never summoned.
            helper.assertFalse(first.getType().canSerialize(), "Dankū's wall can be saved");
            helper.assertFalse(first.saveAsPassenger(new CompoundTag()), "Dankū's wall was written with its chunk");
            helper.assertTrue(first.shouldBeSaved(), "Dankū's wall would stay loaded with no chunk about it");
            helper.assertTrue(first.getType().is(SoulLogic.SPIRITS), "Dankū's wall is not a spirit");
            helper.assertFalse(first.getType().canSummon(), "Dankū's wall can be summoned");
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("danku_wall")) == ModEntities.DANKU_WALL.get(),
                    "bleach:danku_wall is " + BuiltInRegistries.ENTITY_TYPE.get(BleachMod.id("danku_wall")));
            gone.discard();
            slain.setHealth(0.0F);

            helper.runAtTickTime(110, () -> {
                helper.assertTrue(first.standing() && !first.isRemoved(), "Dankū's wall went before its 6 seconds");
                helper.assertTrue(second.isRemoved() && third.isRemoved(), "Dankū's wall outlived its caster: gone "
                        + second.isRemoved() + ", dead " + third.isRemoved());
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(helper.getTick() > 110L && first.isRemoved(), "Dankū's wall still stands at tick "
                        + helper.getTick());
                dropWalls(helper);
            });
        } catch (RuntimeException failed) {
            dropWalls(helper);
            throw failed;
        }
    }
}
