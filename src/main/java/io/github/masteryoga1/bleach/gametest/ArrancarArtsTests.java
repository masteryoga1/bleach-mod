package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.Bala;
import io.github.masteryoga1.bleach.soul.Hierro;
import io.github.masteryoga1.bleach.soul.HollowBody;
import io.github.masteryoga1.bleach.soul.HollowCero;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.Pesquisa;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ResurreccionEvents;
import io.github.masteryoga1.bleach.soul.Sonido;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for the Arrancar's own arts: how far Sonído goes and why it is refused, how much of a blow Hierro
 * turns aside and how it gives way to a stronger attacker without doubling the reiatsu difference, what
 * Pesquisa feels through walls and how its focus pulses or breaks, and Bala's numbers, refusals and rules. Mock
 * players are plain {@link Player}s that are not in the world and never tick, so every test calls the rules
 * directly ({@link Sonido}, {@link Hierro}, {@link Pesquisa}, {@link Bala} and {@link CeroBeam#fire}), and no
 * test can see a milestone, which only a real player earns. The game time stands still within a test, so a
 * later time passed in stands for a later tick. Players stand at {@link #ROOM} in the {@code empty} template
 * and at {@link #SPOT} in the {@code field}, facing south.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class ArrancarArtsTests {
    /** Where a player stands in the {@code empty} template. */
    private static final Vec3 ROOM = new Vec3(1.5, 1.0, 1.5);
    /** Where a player stands in the field: in the middle of block (8, 2, 6), facing south. */
    private static final Vec3 SPOT = new Vec3(8.5, 2.0, 6.5);
    /** The forms an Arrancar may rise from, weakest first. */
    private static final List<HollowTier> ORIGINS = List.of(HollowTier.GILLIAN, HollowTier.ADJUCHAS, HollowTier.VASTO_LORDE);
    /** The health a victim has before each blow, so what a blow took is read off it. */
    private static final float BLOW_HEALTH = 20.0F;

    private ArrancarArtsTests() {
    }

    // ---- Helpers ----

    /** A bare Arrancar soul risen from this form, with this much reiryoku and full. */
    private static SoulData soul(HollowTier origin, float maxReiryoku) {
        SoulData data = ShinigamiTests.soul(SoulState.ARRANCAR, maxReiryoku);
        data.setTier(PowerPath.HOLLOW, origin.tier());
        return data;
    }

    /** Calls out the Resurrección of the blade in the Arrancar's hand, which must answer. */
    private static void release(GameTestHelper helper, Player player, SoulData data, long now) {
        ResurreccionEvents.Result result = ResurreccionEvents.release(player, data, now);
        helper.assertTrue(result == ResurreccionEvents.Result.RELEASED, "the release gave " + result);
    }

    /**
     * The health this blow takes from the victim, struck at {@link #BLOW_HEALTH} and with no hit just before, so
     * the moment after a hit in which only a harder one gets through never comes into it.
     */
    private static float blow(LivingEntity victim, DamageSource source, float amount) {
        victim.setHealth(BLOW_HEALTH);
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
        return BLOW_HEALTH - victim.getHealth();
    }

    private static String name(HollowTier origin) {
        return origin.name().toLowerCase(Locale.ROOT) + "-born";
    }

    private static void assertSonidoRefusal(GameTestHelper helper, Player player, SoulData data, long now,
                                            @Nullable Sonido.Refusal expected, String what) {
        Sonido.Refusal refusal = Sonido.refusal(player, data, now);
        helper.assertTrue(refusal == expected, "a Sonído " + what + " gave " + refusal + ", not " + expected);
    }

    /** Whether nothing has hurt this creature: it is at full health. */
    private static boolean unhurt(LivingEntity creature) {
        return creature.getHealth() >= creature.getMaxHealth();
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }
    }

    /**
     * Every Cero in this test's structure: a Bala's trace and a gathering Cero. All of them, as a trace's owner
     * is looked up in the world, where no mock player is.
     */
    private static void discardBeams(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(CeroBeam.class, helper.getBounds()).forEach(Entity::discard);
    }

    // ---- Sonído ----

    @GameTest(template = "empty")
    public static void sonidoReachesByOriginAndRelease(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        // Each origin's own reach and 0.15 for each of the 20 in the root of 400.
        double[] reach = {10.0, 11.0, 12.0};
        for (int i = 0; i < ORIGINS.size(); i++) {
            HollowTier origin = ORIGINS.get(i);
            HollowPlayerTests.assertClose(helper, Sonido.distance(soul(origin, 400.0F), -1), reach[i],
                    "a " + name(origin) + " Arrancar's Sonído at 400");
        }
        // 9 and 7.5 make 16.5, past the most.
        HollowPlayerTests.assertClose(helper, Sonido.distance(soul(HollowTier.VASTO_LORDE, 2_500.0F), -1), 16.0,
                "a Vasto Lorde-born Arrancar's Sonído at 2,500");

        Player player = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        HollowPlayerTests.assertClose(helper, Sonido.distance(data, 1), 5.5, "the Sonído under Slowness II");
        HollowPlayerTests.assertClose(helper, Sonido.cost(data), 12.0, "the Sonído's cost at 400");
        helper.assertTrue(Sonido.cooldownTicks(data) == 20,
                "the sealed Sonído's cooldown is " + Sonido.cooldownTicks(data));

        release(helper, player, data, now);
        HollowPlayerTests.assertClose(helper, Sonido.distance(data, -1), 13.75, "the released Sonído");
        helper.assertTrue(Sonido.cooldownTicks(data) == 12,
                "the released Sonído's cooldown is " + Sonido.cooldownTicks(data));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sonidoRefusesInOrder(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player player = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        assertSonidoRefusal(helper, player, data, now, null, "by a fresh Arrancar");

        Sonido.stepped(data, now);
        Sonido.Refusal cooling = Sonido.refusal(player, data, now);
        helper.assertTrue(cooling == Sonido.Refusal.COOLDOWN && cooling.messageKey() == null,
                "a Sonído straight after another gave " + cooling + ", told "
                        + (cooling == null ? null : cooling.messageKey()));
        long later = now + 20L;
        assertSonidoRefusal(helper, player, data, later, null, "a second after the last");

        player.addEffect(new MobEffectInstance(ModEffects.GRITZ, 100));
        assertSonidoRefusal(helper, player, data, later, Sonido.Refusal.BOUND, "bound by Gritz");
        player.removeEffect(ModEffects.GRITZ);

        // 10 less the cost of 12 leaves less than half a point.
        data.setReiryoku(10.0F);
        assertSonidoRefusal(helper, player, data, later, Sonido.Refusal.TOO_WEAK, "at 10 reiryoku");
        data.setReiryoku(400.0F);

        // Slowness IV takes all four quarters of the reach.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
        assertSonidoRefusal(helper, player, data, later, Sonido.Refusal.WEIGHED_DOWN, "under Slowness IV");
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        assertSonidoRefusal(helper, player, data, later, null, "with nothing in the way");

        helper.assertTrue(Sonido.unsensed(data, now + 5L), "Pesquisa feels an Arrancar just after its Sonído");
        helper.assertFalse(Sonido.unsensed(data, now + 25L), "Pesquisa still misses an Arrancar a while after its Sonído");
        SoulData human = ShinigamiTests.soul(SoulState.HUMAN, 400.0F);
        Sonido.stepped(human, now);
        helper.assertFalse(Sonido.unsensed(human, now), "a Human's flash step hides it from Pesquisa");
        helper.succeed();
    }

    // ---- Hierro ----

    @GameTest(template = "empty")
    public static void hierroScalesAndGivesWay(GameTestHelper helper) {
        // Each origin's own share and 0.004 for each of the 20 in the root of 400; 1.6 times that released.
        double[] sealed = {0.23, 0.33, 0.43};
        double[] released = {0.368, 0.528, 0.688};
        for (int i = 0; i < ORIGINS.size(); i++) {
            HollowTier origin = ORIGINS.get(i);
            SoulData data = soul(origin, 400.0F);
            HollowPlayerTests.assertClose(helper, Hierro.share(data, false), sealed[i],
                    "a " + name(origin) + " Arrancar's Hierro at 400");
            HollowPlayerTests.assertClose(helper, Hierro.share(data, true), released[i],
                    "a released " + name(origin) + " Arrancar's Hierro at 400");
        }
        SoulData strong = soul(HollowTier.VASTO_LORDE, 2_500.0F);
        HollowPlayerTests.assertClose(helper, Hierro.share(strong, false), 0.55, "a Vasto Lorde-born Hierro at 2,500");
        // 0.88 released, held to the most.
        HollowPlayerTests.assertClose(helper, Hierro.share(strong, true), 0.75,
                "a released Vasto Lorde-born Hierro at 2,500");

        // All of it holds up to the Arrancar's own reiatsu, half of it at 1.5 times, none at twice.
        float[] attackers = {400.0F, 600.0F, 800.0F, 100.0F, 0.0F};
        double[] multipliers = {0.67, 0.835, 1.0, 0.67, 0.67};
        for (int i = 0; i < attackers.length; i++) {
            HollowPlayerTests.assertClose(helper, Hierro.multiplier(0.33F, attackers[i], 400.0F), multipliers[i],
                    "a Hierro of 0.33 against an attacker of " + attackers[i]);
        }
        HollowPlayerTests.assertClose(helper, Hierro.share(ShinigamiTests.soul(SoulState.HUMAN, 400.0F), false), 0.0,
                "a Human's Hierro");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hierroCutsBlowsWithoutDoubling(GameTestHelper helper) {
        Player victim = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        // 0.33 of the blow turned aside, less as the attacker passes 400; at 40 the difference's 0.3 is the larger
        // cut, and only it is made (not 0.3 times 0.67).
        float[] strengths = {400.0F, 600.0F, 800.0F, 40.0F};
        double[] taken = {6.7, 8.35, 10.0, 3.0};
        Player equal = null;
        for (int i = 0; i < strengths.length; i++) {
            Player attacker = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, strengths[i], ROOM);
            if (equal == null) {
                equal = attacker;
            }
            HollowPlayerTests.assertClose(helper, blow(victim, victim.damageSources().playerAttack(attacker), 10.0F),
                    taken[i], "a blow of 10 from a Human at " + strengths[i] + " on the Arrancar");
        }
        // What armour lets through, Hierro lets through too.
        HollowPlayerTests.assertClose(helper, blow(victim, victim.damageSources().indirectMagic(equal, equal), 10.0F), 10.0,
                "magic of 10 from an equal on the Arrancar");
        HollowPlayerTests.assertClose(helper, blow(victim, victim.damageSources().fall(), 10.0F), 10.0,
                "a fall of 10 on the Arrancar");

        Player human = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 400.0F, ROOM);
        HollowPlayerTests.assertClose(helper, blow(human, human.damageSources().playerAttack(equal), 10.0F), 10.0,
                "a blow of 10 from an equal on a Human");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void releasedHierroHoldsHarderInPlaceOfTheHide(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player player = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData data = SoulLogic.get(player);
        release(helper, player, data, now);
        ArrancarTests.tickBody(helper, player, 1);

        helper.assertTrue(HollowPlayerTests.modifier(player, Attributes.ARMOR, HollowBody.HIDE_ID) == null,
                "the released Arrancar wears a Hollow hide");
        HollowPlayerTests.assertClose(helper, HollowPlayerLogic.hideArmour(data), 0.0, "the released Arrancar's hide");
        // 0.528 of an equal's blow turned aside, and no armour.
        Player equal = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 400.0F, ROOM);
        HollowPlayerTests.assertClose(helper, blow(player, player.damageSources().playerAttack(equal), 10.0F), 4.72,
                "a blow of 10 from an equal on the released Arrancar");
        helper.succeed();
    }

    // ---- Pesquisa ----

    /** In a batch of its own, as a pulse reaches 32 blocks, past this structure into any test beside it. */
    @GameTest(template = "field", batch = "pesquisa")
    public static void pesquisaFindsSoulsThroughWalls(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player caster = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, 400.0F, new Vec3(8.5, 2.0, 2.5));
        Cow cow = HollowPlayerTests.placed(helper, EntityType.COW, 11, 3);
        for (int x = 0; x < 16; x++) {
            for (int y = 2; y <= 5; y++) {
                helper.setBlock(x, y, 7, Blocks.STONE);
            }
        }
        PlusSpirit plus = HollowPlayerTests.placed(helper, ModEntities.PLUS.get(), 6, 11);
        plus.soul();
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 10, 12);
        try {
            Pesquisa.Reading reading = Pesquisa.sense(caster, now);
            List<LivingEntity> sensed = reading.sensed();
            HollowPlayerTests.assertClose(helper, reading.range(), 32.0, "the pulse's reach");
            helper.assertTrue(sensed.contains(plus) && sensed.contains(spider),
                    "Pesquisa did not feel the Plus and the Spider Hollow behind the wall: " + sensed);
            helper.assertFalse(sensed.contains(cow), "Pesquisa felt a cow");
            // The mock caster is not in the level, so ask about it directly: only the caster check can refuse it.
            helper.assertFalse(Pesquisa.senses(caster, caster, now), "Pesquisa felt its caster");

            // Anything left about from earlier tests may be felt too, so the strongest is checked against all.
            LivingEntity strongest = reading.strongest();
            helper.assertTrue(strongest != null && sensed.contains(strongest),
                    "the strongest soul felt is " + strongest);
            float most = SoulLogic.reiatsu(strongest);
            HollowPlayerTests.assertClose(helper, reading.strongestReiatsu(), most, "the strongest soul's reiatsu");
            for (LivingEntity felt : sensed) {
                helper.assertTrue(SoulLogic.reiatsu(felt) <= most, felt + " is stronger than the strongest, " + strongest);
            }
            float pair = Math.max(SoulLogic.reiatsu(plus), SoulLogic.reiatsu(spider));
            helper.assertTrue(most >= pair, "the strongest felt, at " + most + ", is weaker than the Plus or the Hollow");

            Component told = Pesquisa.describe(caster, reading);
            helper.assertTrue(told.getContents() instanceof TranslatableContents contents
                            && "message.bleach.pesquisa.found".equals(contents.getKey()),
                    "a pulse that felt several souls told " + told.getContents());
        } finally {
            discard(cow, plus, spider);
        }

        Vec3 near = new Vec3(4.5, 2.0, 4.5);
        Player creative = HollowPlayerTests.soulPlayer(helper, GameType.CREATIVE, SoulState.HUMAN, 400.0F, near);
        helper.assertFalse(Pesquisa.senses(caster, creative, now), "Pesquisa felt a player in creative mode");
        Player spectator = HollowPlayerTests.soulPlayer(helper, GameType.SPECTATOR, SoulState.HUMAN, 400.0F, near);
        helper.assertFalse(Pesquisa.senses(caster, spectator, now), "Pesquisa felt a spectator");
        Player stepped = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, near);
        Sonido.stepped(SoulLogic.get(stepped), now);
        helper.assertFalse(Pesquisa.senses(caster, stepped, now), "Pesquisa felt an Arrancar fresh from its Sonído");
        helper.assertTrue(Pesquisa.senses(caster, stepped, now + 25L), "Pesquisa missed an Arrancar long after its Sonído");
        Player human = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HUMAN, 40.0F, near);
        helper.assertTrue(Pesquisa.senses(caster, human, now), "Pesquisa missed a Human at 40");

        // 1 block more for each Reiatsu Control level, a quarter further released, and never past 96.
        SoulData sealed = soul(HollowTier.VASTO_LORDE, 400.0F);
        HollowPlayerTests.assertClose(helper, Pesquisa.range(sealed), 32.0, "the reach at no Reiatsu Control");
        sealed.setLevel(Stat.REIATSU_CONTROL, 20);
        HollowPlayerTests.assertClose(helper, Pesquisa.range(sealed), 52.0, "the reach at Reiatsu Control 20");
        sealed.setLevel(Stat.REIATSU_CONTROL, 50);
        HollowPlayerTests.assertClose(helper, Pesquisa.range(sealed), 82.0, "the reach at Reiatsu Control 50");
        Player releasedCaster = ArrancarTests.arrancar(helper, HollowTier.VASTO_LORDE, 400.0F, near);
        SoulData released = SoulLogic.get(releasedCaster);
        release(helper, releasedCaster, released, now);
        released.setLevel(Stat.REIATSU_CONTROL, 20);
        HollowPlayerTests.assertClose(helper, Pesquisa.range(released), 65.0, "the released reach at Reiatsu Control 20");
        released.setLevel(Stat.REIATSU_CONTROL, 50);
        HollowPlayerTests.assertClose(helper, Pesquisa.range(released), 96.0, "the released reach at Reiatsu Control 50");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pesquisaFocusesPulsesAndBreaks(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player still = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData stillData = SoulLogic.get(still);
        Pesquisa.Refusal pressed = Pesquisa.press(still, stillData, now);
        helper.assertTrue(pressed == null && Pesquisa.focusing(stillData), "the press began no focus: " + pressed);
        Pesquisa.tick(still, stillData, now + 19L);
        HollowPlayerTests.assertClose(helper, stillData.reiryoku(), 400.0, "the reiryoku before the pulse");
        helper.assertTrue(Pesquisa.focusing(stillData), "the focus ended before a second");
        // The pulse costs 5% of 400.
        Pesquisa.tick(still, stillData, now + 20L);
        helper.assertFalse(Pesquisa.focusing(stillData), "the focus went on after the pulse");
        HollowPlayerTests.assertClose(helper, stillData.reiryoku(), 380.0, "the reiryoku after the pulse");
        Pesquisa.Refusal again = Pesquisa.press(still, stillData, now + 21L);
        helper.assertTrue(again == Pesquisa.Refusal.COOLDOWN, "a press just after a pulse gave " + again);

        Player moving = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, new Vec3(0.5, 1.0, 1.5));
        SoulData movingData = SoulLogic.get(moving);
        Pesquisa.press(moving, movingData, now);
        Vec3 away = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
        moving.moveTo(away.x, away.y, away.z, 0.0F, 0.0F);
        Pesquisa.tick(moving, movingData, now + 1L);
        helper.assertFalse(Pesquisa.focusing(movingData), "moving two blocks did not break the focus");
        HollowPlayerTests.assertClose(helper, movingData.reiryoku(), 400.0, "the reiryoku after a broken focus");

        Player letGo = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, ROOM);
        SoulData letGoData = SoulLogic.get(letGo);
        Pesquisa.press(letGo, letGoData, now);
        Pesquisa.release(letGoData);
        Pesquisa.tick(letGo, letGoData, now + 20L);
        helper.assertFalse(Pesquisa.focusing(letGoData), "letting go of the key did not end the focus");
        HollowPlayerTests.assertClose(helper, letGoData.reiryoku(), 400.0, "the reiryoku after letting go");
        letGoData.setReiryoku(10.0F);
        Pesquisa.Refusal weak = Pesquisa.press(letGo, letGoData, now + 30L);
        helper.assertTrue(weak == Pesquisa.Refusal.TOO_WEAK, "a press at 10 reiryoku gave " + weak);

        Player hollow = HollowPlayerTests.soulPlayer(helper, GameType.SURVIVAL, SoulState.HOLLOW, 400.0F, ROOM);
        SoulData hollowData = SoulLogic.get(hollow);
        helper.assertTrue(Pesquisa.press(hollow, hollowData, now) == null, "a Hollow's press was refused");
        Pesquisa.tick(hollow, hollowData, now + 20L);
        helper.assertFalse(Pesquisa.focusing(hollowData), "a Hollow focused for Pesquisa");
        helper.succeed();
    }

    // ---- Bala ----

    @GameTest(template = "field")
    public static void balaIsQuickAndWeak(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        // Each origin's own damage and 0.1 for each of the 20 in the root of 400.
        double[] damage = {5.0, 5.5, 6.0};
        for (int i = 0; i < ORIGINS.size(); i++) {
            HollowTier origin = ORIGINS.get(i);
            HollowPlayerTests.assertClose(helper, Bala.damage(soul(origin, 400.0F)), damage[i],
                    "a " + name(origin) + " Arrancar's Bala at 400");
        }
        Player releasedShooter = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        SoulData released = SoulLogic.get(releasedShooter);
        release(helper, releasedShooter, released, now);
        HollowPlayerTests.assertClose(helper, Bala.damage(released), 6.875, "a released Adjuchas-born Bala at 400");

        Player shooter = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        SoulData data = SoulLogic.get(shooter);
        HollowPlayerTests.assertClose(helper, Bala.cost(data), 16.0, "a Bala's cost at 400");
        helper.assertTrue(Bala.damage(data) < HollowCero.damage(data, HollowTier.ADJUCHAS) / 3.0F,
                "a Bala of " + Bala.damage(data) + " is no weaker than a third of a Cero");
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 12);
        // A Spider Hollow stands lower than the eyes, so the shot is aimed down at it.
        shooter.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getBoundingBox().getCenter());
        try {
            Bala.Result first = Bala.fire(shooter, data, now);
            helper.assertTrue(first == Bala.Result.FIRED, "the first Bala gave " + first);
            helper.assertFalse(unhurt(spider), "the Bala did not hurt the Spider Hollow");
            HollowPlayerTests.assertClose(helper, data.reiryoku(), 384.0, "the reiryoku after a Bala");
            Bala.Result soon = Bala.fire(shooter, data, now + 5L);
            helper.assertTrue(soon == Bala.Result.COOLDOWN, "a Bala a quarter of a second later gave " + soon);
            spider.invulnerableTime = 0;
            Bala.Result next = Bala.fire(shooter, data, now + 10L);
            helper.assertTrue(next == Bala.Result.FIRED, "a Bala half a second later gave " + next);

            HollowCero.Refusal cero = HollowCero.start(shooter, data, now + 20L);
            try {
                helper.assertTrue(cero == null && HollowCero.charging(data), "the Arrancar's Cero did not gather: " + cero);
                Bala.Result gathering = Bala.fire(shooter, data, now + 20L);
                helper.assertTrue(gathering == Bala.Result.GATHERING, "a Bala while a Cero gathers gave " + gathering);
            } finally {
                HollowCero.cancel(shooter, data);
            }

            data.setReiryoku(10.0F);
            Bala.Result weak = Bala.fire(shooter, data, now + 30L);
            helper.assertTrue(weak == Bala.Result.TOO_WEAK, "a Bala at 10 reiryoku gave " + weak);
        } finally {
            discard(spider);
            discardBeams(helper);
        }
        helper.succeed();
    }

    /** In the Cero's griefing batch, so no other test sees mob griefing change. */
    @GameTest(template = "field", batch = "cero_griefing")
    public static void balaKeepsToRulesAndBreaksNoBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player shooter = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, new Vec3(1.5, 2.0, 8.5));
        SoulData data = SoulLogic.get(shooter);
        CeroBeam.Spec spec = Bala.spec(data);
        helper.assertTrue(spec.maxBlocks() == 0 && spec.blastRadius() == 0.0F && !spec.breaksBlocks(),
                "a Bala may break blocks or burst: " + spec);
        Wolf wolf = HollowPlayerTests.placed(helper, EntityType.WOLF, 4, 8);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(shooter.getUUID());
        SpiderHollow spider = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 7, 8);
        for (int y = 2; y <= 5; y++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(11, y, z, Blocks.DIRT);
            }
        }
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean savedGriefing = griefing.get();
        try {
            griefing.set(true, level.getServer());
            helper.assertFalse(CeroBeam.mayStrike(shooter, wolf), "a Bala strikes the shooter's own wolf");
            Vec3 from = helper.absoluteVec(new Vec3(2.5, 2.5, 8.5));
            Vec3 east = new Vec3(1.0, 0.0, 0.0);

            // The wolf in the way is passed, not met: the Spider Hollow beyond it is the first the Bala may strike.
            CeroBeam.Shot struck = CeroBeam.fire(level, shooter, null, from, east, spec, Bala.KIND);
            helper.assertTrue(struck.struck().equals(List.of(spider)), "the Bala struck " + struck.struck());
            helper.assertTrue(unhurt(wolf), "the Bala hurt the shooter's own wolf");
            DamageSource source = spider.getLastDamageSource();
            helper.assertTrue(source != null && source.is(Bala.DAMAGE), "the Spider Hollow was struck by " + source);

            discard(spider);
            CeroBeam.Shot wall = CeroBeam.fire(level, shooter, null, from, east, spec, Bala.KIND);
            helper.assertTrue(wall.hitBlock() && wall.blocksBroken() == 0,
                    "a Bala at the wall hit a block: " + wall.hitBlock() + ", and broke " + wall.blocksBroken());
            helper.assertBlockPresent(Blocks.DIRT, 11, 2, 8);
        } finally {
            griefing.set(savedGriefing, level.getServer());
            discard(wolf, spider);
        }
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void balaStopsAtTheFirstAndTheArtsRegister(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        ServerLevel level = helper.getLevel();
        Player shooter = ArrancarTests.arrancar(helper, HollowTier.ADJUCHAS, 400.0F, SPOT);
        SoulData data = SoulLogic.get(shooter);
        SpiderHollow near = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 10);
        SpiderHollow far = HollowPlayerTests.placed(helper, ModEntities.SPIDER_HOLLOW.get(), 8, 13);
        // Aimed down at the far one, so the line runs through the near one first.
        shooter.lookAt(EntityAnchorArgument.Anchor.EYES, far.getBoundingBox().getCenter());
        try {
            Bala.Result fired = Bala.fire(shooter, data, now);
            helper.assertTrue(fired == Bala.Result.FIRED, "the Bala gave " + fired);
            helper.assertFalse(unhurt(near), "the Bala did not hurt the near Spider Hollow");
            helper.assertTrue(unhurt(far), "the Bala went on through the near Spider Hollow to the far one");

            // The old path: a Cero along the same line strikes both.
            near.invulnerableTime = 0;
            CeroBeam.Shot cero = CeroBeam.fire(level, shooter, null, CeroBeam.muzzleOf(shooter, 1.0F),
                    shooter.getLookAngle(), Bala.spec(data));
            helper.assertTrue(cero.struck().contains(near) && cero.struck().contains(far),
                    "a Cero along the Bala's line struck " + cero.struck());
            DamageSource source = far.getLastDamageSource();
            helper.assertTrue(source != null && source.is(CeroBeam.DAMAGE), "the Cero struck the far Hollow with " + source);
        } finally {
            discard(near, far);
            discardBeams(helper);
        }

        Registry<DamageType> damageTypes = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        DamageType bala = damageTypes.get(Bala.DAMAGE);
        helper.assertTrue(bala != null, "there is no Bala damage type");
        helper.assertTrue("bleach.bala".equals(bala.msgId()), "the Bala's damage message is " + bala.msgId());
        helper.assertTrue(bala.scaling() == DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                "the Bala's damage scales " + bala.scaling());
        // In no tag, as a Cero: armour, shields, Hierro and the reiatsu difference all count against it.
        helper.assertFalse(damageTypes.getHolderOrThrow(Bala.DAMAGE).is(DamageTypeTags.BYPASSES_ARMOR),
                "a Bala passes armour");

        List<DeferredHolder<SoundEvent, SoundEvent>> sounds = List.of(ModSounds.SONIDO, ModSounds.HIERRO, ModSounds.PESQUISA,
                ModSounds.BALA);
        for (DeferredHolder<SoundEvent, SoundEvent> sound : sounds) {
            helper.assertTrue(sound.isBound(), "the sound " + sound.getId() + " is not registered");
        }
        helper.assertTrue(level.getServer().getAdvancements().get(BleachMod.id("arrancar_arts")) != null,
                "the Arrancar Arts advancement did not load");
        helper.succeed();
    }
}
