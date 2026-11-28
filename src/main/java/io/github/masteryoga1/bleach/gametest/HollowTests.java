package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HollowMask;
import io.github.masteryoga1.bleach.entity.HoundHollow;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SerpentHollow;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.item.DenreishinkiItem;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.HuntRecord;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Hollows: their numbers, who they hunt and who can touch them, the mask, purification and
 * what a kill is worth, Garganta arrivals, the first Hollow, the shimmer band and the Denreishinki.
 * The rules in {@link HollowLogic} are called directly. The rest use real Hollows, on the
 * {@code field} template where they need room: a 16 by 16 stone floor they stand on at relative y 2.
 * Mock players are not in the world, so no Hollow ever hunts them.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HollowTests {
    private static final float EPSILON = 1.0E-3F;
    private static final double CHANCE_EPSILON = 1.0E-6;
    private static final long NOW = 100_000L;
    private static final int TICKS_PER_MINUTE = 1200;
    /** How far any Hollow may fall unharmed, and a Plus too. */
    private static final double SAFE_FALL = 1024.0;

    /** The numbers each kind is registered with on Normal: health, attack, armour and speed. */
    private record KindStats(HollowKind kind, double health, double attack, double armour, double speed) {
    }

    private static final List<KindStats> STATS = List.of(
            new KindStats(HollowKind.SPIDER, 60.0, 5.0, 2.0, 0.30),
            new KindStats(HollowKind.HEXAPODUS, 50.0, 4.0, 2.0, 0.32),
            new KindStats(HollowKind.HOUND, 40.0, 4.0, 1.0, 0.33),
            new KindStats(HollowKind.SERPENT, 70.0, 6.0, 3.0, 0.26),
            new KindStats(HollowKind.FISHBONE_D, 80.0, 6.0, 4.0, 0.25),
            new KindStats(HollowKind.GILLIAN, 300.0, 14.0, 8.0, 0.18),
            new KindStats(HollowKind.ADJUCHAS, 140.0, 9.0, 10.0, 0.34),
            new KindStats(HollowKind.SHRIEKER, 120.0, 7.0, 4.0, 0.25),
            new KindStats(HollowKind.GRAND_FISHER, 180.0, 8.0, 4.0, 0.25),
            new KindStats(HollowKind.VASTO_LORDE, 400.0, 16.0, 14.0, 0.35));

    private HollowTests() {
    }

    /** A mock player in survival with a soul in this state, full of this much reiryoku. */
    static Player soulPlayer(GameTestHelper helper, SoulState state, float maxReiryoku) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(SoulAttachments.SOUL, ShinigamiTests.soul(state, maxReiryoku));
        return player;
    }

    /** A mock Shinigami of tier one. */
    static Player shinigami(GameTestHelper helper, float maxReiryoku) {
        return soulPlayer(helper, SoulState.SHINIGAMI, maxReiryoku);
    }

    /** Puts this item in the player's main hand. */
    static void arm(Player player, Item item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    /** A soul whose first Hollow has come and gone, so Garganta may open for it. */
    private static SoulData hunted(SoulState state, float maxReiryoku) {
        SoulData data = ShinigamiTests.soul(state, maxReiryoku);
        data.setHunt(data.hunt().withFirstHollowDone(true));
        return data;
    }

    /** Turns a Hollow, head and body too, to face south (+Z), so its mask is on that side. */
    private static void faceSouth(Hollow hollow) {
        hollow.setYRot(0.0F);
        hollow.setYHeadRot(0.0F);
        hollow.setYBodyRot(0.0F);
    }

    private static void assertBase(GameTestHelper helper, LivingEntity entity, Holder<Attribute> attribute,
                                   double expected, String what) {
        double actual = entity.getAttributeBaseValue(attribute);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-6, what + " was " + actual + ", not " + expected);
    }

    private static void assertClose(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) < CHANCE_EPSILON, what + " was " + actual + ", not " + expected);
    }

    /** An arrow at this absolute position, flying this far each tick, that is not in the world. */
    static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 motion) {
        Arrow arrow = new Arrow(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(motion);
        return arrow;
    }

    /** The translatable contents of a message, which must have this key. */
    static TranslatableContents translatable(GameTestHelper helper, Component message, String key) {
        helper.assertTrue(message.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key),
                "the message was " + message.getContents() + ", not " + key);
        return (TranslatableContents) message.getContents();
    }

    @GameTest(template = "field")
    public static void hollowKindsCarryTheirNumbers(GameTestHelper helper) {
        for (KindStats expected : STATS) {
            EntityType<? extends Hollow> type = ModEntities.hollowType(expected.kind());
            String name = expected.kind().getSerializedName();
            Hollow hollow = helper.spawnWithNoFreeWill(type, 1, 2, 1);
            helper.assertTrue(hollow.kind() == expected.kind(), name + " is registered as a " + hollow.kind());
            assertBase(helper, hollow, Attributes.MAX_HEALTH, expected.health(), name + " health");
            assertBase(helper, hollow, Attributes.ATTACK_DAMAGE, expected.attack(), name + " attack");
            assertBase(helper, hollow, Attributes.ARMOR, expected.armour(), name + " armour");
            assertBase(helper, hollow, Attributes.MOVEMENT_SPEED, expected.speed(), name + " speed");
            assertBase(helper, hollow, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, name + " safe fall distance");
            helper.assertTrue(type.fireImmune(), name + " burns");
            helper.assertTrue(type.is(BleachTags.HOLLOWS), name + " is not in bleach:hollows");
            helper.assertTrue(type.is(SoulLogic.SPIRITS), name + " is not in bleach:spirits");
            helper.assertTrue(type.is(EntityTypeTags.CAN_BREATHE_UNDER_WATER), name + " drowns");
            hollow.discard();
        }

        EntityType<PlusSpirit> plusType = ModEntities.PLUS.get();
        PlusSpirit plus = helper.spawnWithNoFreeWill(plusType, 1, 2, 1);
        assertBase(helper, plus, Attributes.MAX_HEALTH, 15.0, "Plus health");
        helper.assertTrue(plusType.is(SoulLogic.SPIRITS), "a Plus is not in bleach:spirits");
        helper.assertFalse(plusType.is(BleachTags.HOLLOWS), "a Plus is in bleach:hollows");
        plus.discard();

        helper.assertFalse(ModEntities.GARGANTA.get().canSerialize(), "a Garganta is saved with the world");
        helper.assertFalse(ModEntities.FISHBONE_D.get().canSerialize(), "Fishbone D is saved with the world");
        helper.assertFalse(ModEntities.GILLIAN.get().canSerialize(), "a Gillian is saved with the world");
        helper.assertFalse(ModEntities.ADJUCHAS.get().canSerialize(), "an Adjuchas is saved with the world");
        helper.assertFalse(ModEntities.SHRIEKER.get().canSerialize(), "a Shrieker is saved with the world");
        helper.assertFalse(ModEntities.GRAND_FISHER.get().canSerialize(), "Grand Fisher is saved with the world");
        helper.assertFalse(ModEntities.VASTO_LORDE.get().canSerialize(), "a Vasto Lorde is saved with the world");
        helper.assertFalse(ModEntities.CERO_BEAM.get().canSerialize(), "a Cero is saved with the world");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void hollowsGetASoulOnTheirFirstTick(GameTestHelper helper) {
        List<Hollow> hollows = new ArrayList<>();
        int x = 1;
        // Two blocks apart, so all eight that are no Menos and not Runuganga fit the field's row, the human-sized Vasto
        // Lorde at its end.
        for (HollowKind kind : HollowKind.values()) {
            if (kind.isMenos() || kind == HollowKind.RUNUGANGA) {
                // Too big for this row; MenosTests checks a Gillian's soul, and RunugangaTests his.
                continue;
            }
            hollows.add(helper.spawnWithNoFreeWill(ModEntities.hollowType(kind), x, 2, 8));
            x += 2;
        }
        helper.runAfterDelay(1, () -> {
            for (Hollow hollow : hollows) {
                String name = hollow.kind().getSerializedName();
                // Read without soul(), which would make one.
                helper.assertTrue(SoulLogic.hasSoul(hollow), "a " + name + " has no soul after its first tick");
                SoulData soul = SoulLogic.get(hollow);
                helper.assertTrue(soul.state() == SoulState.HOLLOW, "a " + name + "'s soul is a " + soul.state());
                helper.assertTrue(Math.abs(soul.maxReiryoku() - hollow.kind().reiatsu) < EPSILON,
                        "a " + name + " has reiatsu " + soul.maxReiryoku() + ", not " + hollow.kind().reiatsu);
                hollow.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void unawareHitsPassThroughHollows(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 1, 1, 1);
        Player unaware = soulPlayer(helper, SoulState.HUMAN, 10.0F);
        Player aware = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        DamageSources damage = helper.getLevel().damageSources();
        float full = spider.getHealth();

        spider.hurt(damage.playerAttack(unaware), 5.0F);
        helper.assertTrue(spider.getHealth() == full, "an unaware soul's blow took a Hollow to " + spider.getHealth());
        spider.hurt(damage.playerAttack(aware), 5.0F);
        helper.assertTrue(spider.getHealth() < full, "an aware soul's blow passed through a Hollow");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void mundaneMobsIgnoreSpirits(GameTestHelper helper) {
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, 3, 2, 3);
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 10, 2, 3);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 10);

        golem.setTarget(spider);
        helper.assertTrue(golem.getTarget() == null, "an iron golem took a Hollow as its target");
        golem.setTarget(zombie);
        helper.assertTrue(golem.getTarget() == zombie, "an iron golem could not take a zombie as its target");

        float full = spider.getHealth();
        spider.hurt(helper.getLevel().damageSources().mobAttack(golem), 5.0F);
        helper.assertTrue(spider.getHealth() == full, "an iron golem's blow took a Hollow to " + spider.getHealth());
        helper.succeed();
    }

    /**
     * An iron golem left to itself looks past a Hollow standing next to it to the zombie further off.
     * Were the Hollow only refused as a target, the golem would pick it again and again instead.
     */
    @GameTest(template = "field")
    public static void golemsLookPastHollows(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new Vec3(3.5, 2.0, 3.5));
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), new Vec3(6.5, 2.0, 3.5));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5, 2.0, 12.5));
        helper.assertFalse(TargetingConditions.forCombat().test(golem, spider), "an iron golem can pick out a Hollow");
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.getTarget() == zombie, "an iron golem with a Hollow beside it never went for the zombie");
            golem.discard();
            spider.discard();
            zombie.discard();
        });
    }

    /** A Warden picks out what to sniff, grow angry at and roar at through its own check, not TargetingConditions. */
    @GameTest(template = "empty")
    public static void wardensSenseNoHollows(GameTestHelper helper) {
        Warden warden = helper.spawn(EntityType.WARDEN, 1, 1, 1);
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 1, 1, 1);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        helper.assertFalse(warden.canTargetEntity(spider), "a Warden can sense a Hollow");
        helper.assertTrue(warden.canTargetEntity(zombie), "a Warden cannot sense a zombie");
        warden.increaseAngerAt(spider, 100, false);
        helper.assertTrue(warden.getEntityAngryAt().isEmpty(), "a Warden grew angry at a Hollow");
        warden.increaseAngerAt(zombie, 100, false);
        helper.assertTrue(warden.getEntityAngryAt().orElse(null) == zombie, "a Warden did not grow angry at a zombie");
        warden.discard();
        spider.discard();
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void maskHitsDealDoubleDamage(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 8);
        faceSouth(spider);
        Vec3 at = spider.position();
        // One behind the spider, aiming at the middle of its body; one in front, aiming at the mask.
        Player behind = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        behind.moveTo(at.add(0.0, 0.0, -2.6));
        behind.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getBoundingBox().getCenter());
        Player front = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        front.moveTo(at.add(0.0, 0.0, 2.5));
        front.lookAt(EntityAnchorArgument.Anchor.EYES, HollowMask.maskCenter(spider));
        DamageSources damage = helper.getLevel().damageSources();

        float full = spider.getHealth();
        spider.hurt(damage.playerAttack(behind), 4.0F);
        float bodyDrop = full - spider.getHealth();
        spider.invulnerableTime = 0;
        float beforeMask = spider.getHealth();
        spider.hurt(damage.playerAttack(front), 4.0F);
        float maskDrop = beforeMask - spider.getHealth();
        long now = helper.getLevel().getGameTime();

        helper.assertTrue(bodyDrop > 0.0F, "the blow to the body did no harm");
        // Both blows are big enough that the armour takes the same share of each, so the ratio is the multiplier.
        double multiplier = BleachConfig.get(BleachConfig.MASK_DAMAGE_MULTIPLIER);
        double ratio = maskDrop / bodyDrop;
        helper.assertTrue(Math.abs(ratio - multiplier) < 0.05,
                "a blow to the mask did " + maskDrop + " against " + bodyDrop + " to the body, " + ratio + " times as much");
        helper.assertTrue(spider.isMaskCracked(), "a blow to the mask did not crack it");
        helper.assertTrue(spider.wasMaskStruckAt(now), "the blow to the mask was not noted");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void arrowsStrikeTheMaskOnlyFromTheFront(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 8);
        faceSouth(spider);
        Player archer = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        DamageSources damage = helper.getLevel().damageSources();
        Vec3 mask = HollowMask.maskCenter(spider);

        Arrow front = arrow(helper, mask.add(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.6));
        Arrow back = arrow(helper, spider.position().add(0.0, 0.65, -2.0), new Vec3(0.0, 0.0, 2.0));
        helper.assertTrue(HollowMask.struck(spider, damage.arrow(front, archer)), "an arrow flying into the mask missed it");
        helper.assertFalse(HollowMask.struck(spider, damage.arrow(back, archer)), "an arrow from behind struck the mask");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void zanpakutoKillsPurify(GameTestHelper helper) {
        SpiderHollow first = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 2, 2, 2);
        SpiderHollow second = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 2);
        SpiderHollow third = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 14, 2, 2);
        Player purifier = shinigami(helper, 40.0F);
        arm(purifier, ModItems.ASAUCHI.get());
        Player human = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        arm(human, ModItems.ASAUCHI.get());
        Player swordsman = shinigami(helper, 40.0F);
        arm(swordsman, Items.IRON_SWORD);
        Vec3 where = first.position();
        DamageSources damage = helper.getLevel().damageSources();

        first.hurt(damage.playerAttack(purifier), 1000.0F);
        second.hurt(damage.playerAttack(human), 1000.0F);
        third.hurt(damage.playerAttack(swordsman), 1000.0F);

        // A Spider's reiatsu (50) is more than half the killers' (40), so each grows by the whole share.
        double share = HollowKind.SPIDER.reiatsu * BleachConfig.get(BleachConfig.SPIRIT_KILL_GROWTH_PER_REIATSU);
        float purifiedMax = (float) (40.0 + share * BleachConfig.get(BleachConfig.PURIFICATION_GROWTH_MULTIPLIER));
        float slainMax = (float) (40.0 + share);
        SoulData data = SoulLogic.get(purifier);
        helper.assertTrue(data.hunt().purified() == 1 && data.hunt().slain() == 0,
                "an Asauchi kill by a Shinigami counted " + data.hunt().purified() + " purified and " + data.hunt().slain() + " slain");
        helper.assertTrue(Math.abs(data.maxReiryoku() - purifiedMax) < EPSILON,
                "purifying grew max reiryoku to " + data.maxReiryoku() + ", not " + purifiedMax);
        int zanjutsuXp = BleachConfig.get(BleachConfig.PURIFICATION_ZANJUTSU_XP);
        helper.assertTrue(data.xp(Stat.ZANJUTSU) == zanjutsuXp,
                "purifying gave " + data.xp(Stat.ZANJUTSU) + " Zanjutsu XP, not " + zanjutsuXp);
        // Only a swing of the blade purifies, not another blow that comes straight from its wielder.
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(HollowLogic.purifies(damage.playerAttack(purifier), data, now), "an Asauchi cut does not purify");
        helper.assertFalse(HollowLogic.purifies(damage.thorns(purifier), data, now),
                "thorns from a Shinigami holding an Asauchi purify");
        for (Player killer : List.of(human, swordsman)) {
            String who = killer == human ? "a Human with an Asauchi" : "a Shinigami with an iron sword";
            SoulData killerData = SoulLogic.get(killer);
            helper.assertTrue(killerData.hunt().slain() == 1 && killerData.hunt().purified() == 0,
                    who + " counted " + killerData.hunt().purified() + " purified and " + killerData.hunt().slain() + " slain");
            helper.assertTrue(Math.abs(killerData.maxReiryoku() - slainMax) < EPSILON,
                    "slaying by " + who + " grew max reiryoku to " + killerData.maxReiryoku() + ", not " + slainMax);
        }

        helper.succeedWhen(() -> {
            helper.assertTrue(first.getRemovalReason() == Entity.RemovalReason.KILLED,
                    "the purified Hollow has not dissolved: " + first.getRemovalReason());
            helper.assertTrue(second.isRemoved() && third.isRemoved(), "a slain Hollow is still there");
            List<PlusSpirit> echoes = helper.getLevel().getEntitiesOfClass(PlusSpirit.class, AABB.ofSize(where, 6.0, 6.0, 6.0),
                    plus -> plus.isDeparting() && plus.position().distanceTo(where) <= 3.0);
            helper.assertFalse(echoes.isEmpty(), "no soul rose from the purified Hollow");
        });
    }

    @GameTest(template = "field")
    public static void onlySpiritKillsGrowReiryoku(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 3);
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 11, 2, 11);
        Player hunter = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        SoulData data = SoulLogic.get(hunter);
        DamageSources damage = helper.getLevel().damageSources();

        zombie.hurt(damage.playerAttack(hunter), 1000.0F);
        helper.assertTrue(zombie.isDeadOrDying(), "the zombie survived");
        // Off by default: mundane kills grow nothing.
        float afterZombie = (float) (40.0 + zombie.getMaxHealth() * BleachConfig.get(BleachConfig.MUNDANE_KILL_GROWTH_PER_MAX_HEALTH));
        helper.assertTrue(Math.abs(data.maxReiryoku() - afterZombie) < EPSILON,
                "killing a zombie grew max reiryoku to " + data.maxReiryoku() + ", not " + afterZombie);

        spider.hurt(damage.playerAttack(hunter), 1000.0F);
        double perReiatsu = BleachConfig.get(BleachConfig.SPIRIT_KILL_GROWTH_PER_REIATSU);
        float afterSpider = (float) (afterZombie + HollowKind.SPIDER.reiatsu * perReiatsu
                * Math.min(1.0, 2.0 * HollowKind.SPIDER.reiatsu / afterZombie));
        helper.assertTrue(Math.abs(data.maxReiryoku() - afterSpider) < EPSILON,
                "slaying a Hollow grew max reiryoku to " + data.maxReiryoku() + ", not " + afterSpider);

        // A share of the victim's reiatsu, down to a tenth against one far weaker, and more for purifying.
        double purified = BleachConfig.get(BleachConfig.PURIFICATION_GROWTH_MULTIPLIER);
        assertClose(helper, HollowLogic.killGrowth(50.0F, 40.0F, false), 50.0 * perReiatsu, "growth for slaying 50 at 40");
        assertClose(helper, HollowLogic.killGrowth(50.0F, 40.0F, true), 50.0 * perReiatsu * purified, "growth for purifying 50 at 40");
        assertClose(helper, HollowLogic.killGrowth(60.0F, 1000.0F, false), 60.0 * perReiatsu * 0.12, "growth for slaying 60 at 1000");
        assertClose(helper, HollowLogic.killGrowth(10.0F, 1000.0F, false), 10.0 * perReiatsu * 0.1, "growth for slaying 10 at 1000");

        int hollowXp = BleachConfig.get(BleachConfig.HOLLOW_HIT_STAT_XP);
        int maskBonus = BleachConfig.get(BleachConfig.MASK_HIT_STAT_XP_BONUS);
        helper.assertTrue(HollowLogic.hitXp(false, false) == 1, "a hit on anything else gave " + HollowLogic.hitXp(false, false) + " XP");
        helper.assertTrue(HollowLogic.hitXp(true, false) == hollowXp, "a hit on a Hollow gave " + HollowLogic.hitXp(true, false) + " XP");
        helper.assertTrue(HollowLogic.hitXp(true, true) == hollowXp + maskBonus,
                "a hit on a mask gave " + HollowLogic.hitXp(true, true) + " XP");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void hollowsHuntTheAwareAndPluses(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 3, 2, 3);
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 7, 2, 3);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 11, 2, 3);
        WoundedShinigami wounded = helper.spawnWithNoFreeWill(ModEntities.WOUNDED_SHINIGAMI.get(), 7, 2, 10);
        Player aware = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        Player unaware = soulPlayer(helper, SoulState.HUMAN, 10.0F);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, 40.0F));
        Player hollow = soulPlayer(helper, SoulState.HOLLOW, 40.0F);

        helper.assertTrue(HollowLogic.isPrey(aware), "Hollows do not hunt an aware soul");
        helper.assertTrue(HollowLogic.isPrey(plus), "Hollows do not hunt a Plus");
        helper.assertFalse(HollowLogic.isPrey(unaware), "Hollows hunt an unaware soul");
        helper.assertFalse(HollowLogic.isPrey(creative), "Hollows hunt a player in creative mode");
        helper.assertFalse(HollowLogic.isPrey(hollow), "Hollows hunt a Hollow player");
        helper.assertFalse(HollowLogic.isPrey(villager), "Hollows hunt villagers");
        helper.assertFalse(HollowLogic.isPrey(wounded), "Hollows hunt the wounded Shinigami");
        helper.assertFalse(HollowLogic.isPrey(spider), "Hollows hunt one another");
        plus.startDeparture(PlusSpirit.SINKING);
        helper.assertFalse(HollowLogic.isPrey(plus), "Hollows hunt a Plus that is passing on");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hollowsOnlyKeepTheAwareAwake(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 1, 1, 1);
        helper.assertTrue(spider.isPreventingPlayerRest(soulPlayer(helper, SoulState.HUMAN, 40.0F)),
                "a Hollow does not keep an aware soul from sleeping");
        helper.assertFalse(spider.isPreventingPlayerRest(soulPlayer(helper, SoulState.HUMAN, 10.0F)),
                "a Hollow keeps an unaware soul from sleeping");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void spiderHollowsClimbAndSerpentsShrugOffPoison(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 4, 2, 4);
        SerpentHollow serpent = helper.spawnWithNoFreeWill(ModEntities.SERPENT_HOLLOW.get(), 11, 2, 11);
        helper.assertTrue(spider.getNavigation() instanceof WallClimberNavigation, "a Spider Hollow cannot climb walls");
        helper.assertFalse(serpent.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "a Serpent Hollow was poisoned");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void houndsShareTheirQuarry(GameTestHelper helper) {
        HoundHollow first = helper.spawnWithNoFreeWill(ModEntities.HOUND_HOLLOW.get(), 1, 2, 1);
        HoundHollow near = helper.spawnWithNoFreeWill(ModEntities.HOUND_HOLLOW.get(), 4, 2, 1);
        HoundHollow far = helper.spawnWithNoFreeWill(ModEntities.HOUND_HOLLOW.get(), 15, 2, 14);
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 2, 2, 3);

        // All in one tick: target goals survive removeFreeWill and would pick their own prey later.
        first.setTarget(plus);
        helper.assertTrue(first.getTarget() == plus, "a hound could not take a Plus as its quarry");
        first.shareTarget();
        helper.assertTrue(near.getTarget() == plus, "a hound 3 blocks away did not join the hunt");
        helper.assertTrue(far.getTarget() == null, "a hound 19 blocks away joined the hunt");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 200, batch = "garganta")
    public static void gargantaDropsItsHollows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GargantaRift rift = GargantaRift.arrival(level, helper.absoluteVec(new Vec3(8.0, 8.0, 8.0)), level.getGameTime(),
                List.of(HollowKind.SPIDER, HollowKind.SPIDER), null, 1.0F);
        helper.assertTrue(level.addFreshEntity(rift), "the world refused the Garganta");
        // A batch of its own, so the only Hollows in the field are the ones it drops.
        helper.succeedWhen(() -> {
            helper.assertTrue(rift.isRemoved(), "the Garganta is still open");
            List<Hollow> spiders = level.getEntitiesOfClass(Hollow.class, helper.getBounds(),
                    hollow -> hollow.getType() == ModEntities.SPIDER_HOLLOW.get());
            helper.assertTrue(spiders.size() == 2, "the Garganta left " + spiders.size() + " Spider Hollows, not 2");
            for (Hollow spider : spiders) {
                helper.assertTrue(Math.abs(spider.getHealth() - spider.getMaxHealth()) < EPSILON,
                        "a dropped Hollow has " + spider.getHealth() + " of " + spider.getMaxHealth() + " health");
                helper.assertTrue(spider.getTarget() == null, "a Hollow drawn by no one hunts " + spider.getTarget());
            }
            spiders.forEach(Entity::discard);
        });
    }

    @GameTest(template = "empty")
    public static void arrivalsNeedTheRightWorld(GameTestHelper helper) {
        helper.assertTrue(HollowLogic.worldAllowsArrivals(Difficulty.EASY, true, true), "no arrivals in the Overworld on Easy");
        helper.assertFalse(HollowLogic.worldAllowsArrivals(Difficulty.PEACEFUL, true, true), "arrivals on Peaceful");
        helper.assertFalse(HollowLogic.worldAllowsArrivals(Difficulty.NORMAL, false, true), "arrivals with mob spawning off");
        helper.assertFalse(HollowLogic.worldAllowsArrivals(Difficulty.NORMAL, true, false), "arrivals outside the Overworld");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrivalsGrowWithReiatsu(GameTestHelper helper) {
        double base = BleachConfig.get(BleachConfig.ARRIVAL_CHANCE_PER_MINUTE);
        double cap = BleachConfig.get(BleachConfig.MAX_ARRIVAL_CHANCE_PER_MINUTE);
        double night = BleachConfig.get(BleachConfig.ARRIVAL_NIGHT_MULTIPLIER);
        double plus = BleachConfig.get(BleachConfig.ARRIVAL_PLUS_MULTIPLIER);
        double released = BleachConfig.get(BleachConfig.ARRIVAL_RELEASED_MULTIPLIER);
        double atForty = Math.min(cap, base);

        // With the defaults: 0.06, 0.09, 0.12, 0.4, 0.135 and 0.12 a minute.
        SoulData human = hunted(SoulState.HUMAN, 40.0F);
        assertClose(helper, HollowLogic.arrivalChancePerMinute(human, false), atForty, "the chance for 40 by day");
        assertClose(helper, HollowLogic.arrivalChancePerMinute(human, true), Math.min(1.0, atForty * night),
                "the chance for 40 at night");
        assertClose(helper, HollowLogic.arrivalChancePerMinute(hunted(SoulState.HUMAN, 160.0F), false),
                Math.min(cap, base * 2.0), "the chance for 160");
        assertClose(helper, HollowLogic.arrivalChancePerMinute(hunted(SoulState.HUMAN, 2000.0F), false),
                Math.min(cap, base * Math.sqrt(50.0)), "the chance for 2000");
        assertClose(helper, HollowLogic.arrivalChancePerMinute(hunted(SoulState.PLUS, 40.0F), true),
                Math.min(1.0, atForty * night * plus), "the chance for a Plus of 40 at night");
        SoulData releasing = hunted(SoulState.HUMAN, 40.0F);
        releasing.setReiatsuReleased(true);
        assertClose(helper, HollowLogic.arrivalChancePerMinute(releasing, false), Math.min(1.0, atForty * released),
                "the chance while releasing reiatsu");
        assertClose(helper, HollowLogic.arrivalChancePerCheck(human, true), HollowLogic.arrivalChancePerMinute(human, true) * 0.5,
                "the chance for one check, every half minute");

        helper.assertTrue(HollowLogic.arrivalChancePerMinute(hunted(SoulState.HUMAN, 20.0F), true) == 0.0,
                "a Garganta may open for an unaware soul");
        helper.assertTrue(HollowLogic.arrivalChancePerMinute(hunted(SoulState.ARRANCAR, 40.0F), true) == 0.0,
                "a Garganta may open for an Arrancar");
        // A Hollow player draws Garganta as a Human of the same reiatsu would (times a multiplier of 1).
        assertClose(helper, HollowLogic.arrivalChancePerMinute(hunted(SoulState.HOLLOW, 40.0F), true),
                HollowLogic.arrivalChancePerMinute(hunted(SoulState.HUMAN, 40.0F), true), "the chance for a Hollow");
        helper.assertTrue(HollowLogic.arrivalChancePerMinute(ShinigamiTests.soul(SoulState.HUMAN, 40.0F), true) == 0.0,
                "a Garganta may open before the first Hollow");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrivalPoolUnlocksWithReiatsu(GameTestHelper helper) {
        List<HollowKind> forty = HollowLogic.arrivalPool(40.0F);
        helper.assertTrue(forty.size() == 2 && Set.copyOf(forty).equals(Set.of(HollowKind.SPIDER, HollowKind.HEXAPODUS)),
                "the pool at 40 is " + forty);
        List<HollowKind> sixty = HollowLogic.arrivalPool(60.0F);
        helper.assertTrue(sixty.size() == 3
                        && Set.copyOf(sixty).equals(Set.of(HollowKind.SPIDER, HollowKind.HEXAPODUS, HollowKind.HOUND)),
                "the pool at 60 is " + sixty);
        List<HollowKind> eighty = HollowLogic.arrivalPool(80.0F);
        helper.assertTrue(eighty.size() == 4 && Set.copyOf(eighty).equals(
                        Set.of(HollowKind.SPIDER, HollowKind.HEXAPODUS, HollowKind.HOUND, HollowKind.SERPENT)),
                "the pool at 80 is " + eighty);
        helper.assertFalse(HollowLogic.arrivalPool(Float.MAX_VALUE).contains(HollowKind.FISHBONE_D),
                "Fishbone D can come by chance");

        helper.assertTrue(HollowLogic.packSize(100.0F) == 2, "a pack at 100 is " + HollowLogic.packSize(100.0F));
        helper.assertTrue(HollowLogic.packSize(250.0F) == 3, "a pack at 250 is " + HollowLogic.packSize(250.0F));
        helper.assertTrue(HollowLogic.packSize(450.0F) == 4, "a pack at 450 is " + HollowLogic.packSize(450.0F));
        assertClose(helper, HollowLogic.secondHollowChance(100.0F), 0.0, "the chance of a second Hollow at 100");
        assertClose(helper, HollowLogic.secondHollowChance(300.0F), 0.25, "the chance of a second Hollow at 300");
        assertClose(helper, HollowLogic.secondHollowChance(900.0F), 0.5, "the chance of a second Hollow at 900");
        helper.assertTrue(HollowLogic.groupSize(HollowKind.HOUND, 450.0F, 2, 0.0) == 2,
                "a pack with room for 2 brought " + HollowLogic.groupSize(HollowKind.HOUND, 450.0F, 2, 0.0));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstHollowComesOnceAfterADelay(GameTestHelper helper) {
        SoulData soul = ShinigamiTests.soul(SoulState.HUMAN, 40.0F);
        helper.assertTrue(soul.hunt().equals(HuntRecord.NONE), "a new soul's hunt record is " + soul.hunt());
        soul.setHunt(HollowLogic.noteAwareness(soul.hunt(), soul, 1000));
        helper.assertTrue(soul.hunt().awareSincePlayTicks() == 1000, "awareness was stamped at " + soul.hunt().awareSincePlayTicks());
        soul.setHunt(HollowLogic.noteAwareness(soul.hunt(), soul, 2000));
        helper.assertTrue(soul.hunt().awareSincePlayTicks() == 1000, "awareness was stamped again, at " + soul.hunt().awareSincePlayTicks());

        // With the defaults the delay and the wait between tries are both ten minutes.
        int due = 1000 + BleachConfig.get(BleachConfig.FIRST_HOLLOW_DELAY_MINUTES) * TICKS_PER_MINUTE;
        long retry = (long) BleachConfig.get(BleachConfig.FIRST_HOLLOW_RETRY_MINUTES) * TICKS_PER_MINUTE;
        helper.assertFalse(HollowLogic.firstHollowDue(soul, due - 1, NOW, true), "the first Hollow came a tick early");
        helper.assertTrue(HollowLogic.firstHollowDue(soul, due, NOW, true), "the first Hollow did not come on time");
        helper.assertFalse(HollowLogic.firstHollowDue(soul, due, NOW, false), "the first Hollow came by day");

        soul.setHunt(soul.hunt().withLastArrivalAt(NOW));
        helper.assertFalse(HollowLogic.firstHollowDue(soul, due, NOW + retry - 1, true), "the first Hollow came back too soon");
        helper.assertTrue(HollowLogic.firstHollowDue(soul, due, NOW + retry, true), "the first Hollow did not come back");

        SoulData done = soul.copy();
        done.setHunt(done.hunt().withFirstHollowDone(true));
        helper.assertFalse(HollowLogic.firstHollowDue(done, due, NOW + retry, true), "the first Hollow came twice");
        SoulData shinigami = ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F);
        shinigami.setHunt(soul.hunt());
        helper.assertFalse(HollowLogic.firstHollowDue(shinigami, due, NOW + retry, true), "the first Hollow came for a Shinigami");

        SoulData unaware = ShinigamiTests.soul(SoulState.HUMAN, 10.0F);
        unaware.setHunt(HollowLogic.noteAwareness(unaware.hunt(), unaware, 1000));
        helper.assertTrue(unaware.hunt().awareSincePlayTicks() < 0, "an unaware soul was stamped as aware");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 120)
    public static void fishboneDLeavesWhenItsTimeIsUp(GameTestHelper helper) {
        FishboneD fishbone = helper.spawnWithNoFreeWill(ModEntities.FISHBONE_D.get(), 8, 2, 8);
        fishbone.setLifetime(5);
        helper.succeedWhen(() -> {
            helper.assertTrue(fishbone.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                    "Fishbone D has not left: " + fishbone.getRemovalReason());
            helper.assertFalse(helper.getLevel().getEntitiesOfClass(GargantaRift.class, helper.getBounds(),
                    GargantaRift::isDeparture).isEmpty(), "Fishbone D left without a tear");
        });
    }

    @GameTest(template = "empty")
    public static void awarenessBandsAreRight(GameTestHelper helper) {
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, 10.0F));
        helper.assertTrue(SoulLogic.seesSpirits(soulPlayer(helper, SoulState.HUMAN, 40.0F)), "an aware soul cannot see spirits");
        helper.assertTrue(SoulLogic.seesSpirits(creative), "an unaware soul in creative mode cannot see spirits");
        helper.assertFalse(SoulLogic.seesSpirits(soulPlayer(helper, SoulState.HUMAN, 10.0F)), "an unaware soul sees spirits");

        // With the defaults the band runs from 20.1 up to the threshold of 30.
        helper.assertTrue(SoulLogic.inShimmerBand(ShinigamiTests.soul(SoulState.HUMAN, 22.0F)), "a Human of 22 sees no shimmer");
        helper.assertFalse(SoulLogic.inShimmerBand(ShinigamiTests.soul(SoulState.HUMAN, 30.0F)), "an aware Human of 30 sees a shimmer");
        helper.assertFalse(SoulLogic.inShimmerBand(ShinigamiTests.soul(SoulState.HUMAN, 20.0F)), "a Human of 20 sees a shimmer");
        helper.assertFalse(SoulLogic.inShimmerBand(ShinigamiTests.soul(SoulState.PLUS, 22.0F)), "a Plus sees a shimmer");
        helper.assertFalse(SoulLogic.inShimmerBand(ShinigamiTests.substitute(22.0F)),
                "a Human of 22 with Shinigami power sees a shimmer");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void denreishinkiReadsTheNearestHollow(GameTestHelper helper) {
        helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 8, 2, 8);
        // The spider stands at (8.5, 2, 8.5): six blocks south of the reader, which a pager rounds to five.
        Vec3 north = helper.absoluteVec(new Vec3(8.5, 2.0, 2.5));
        Player reader = shinigami(helper, 40.0F);
        reader.moveTo(north);
        Object[] args = translatable(helper, DenreishinkiItem.reading(reader), "message.bleach.denreishinki.hollow").getArgs();
        helper.assertTrue(args.length == 2, "the reading has " + args.length + " arguments");
        helper.assertTrue(args[0] instanceof Component way && way.getContents() instanceof TranslatableContents compass
                        && compass.getKey().equals("message.bleach.direction.south"),
                "the reading points " + args[0] + ", not south");
        helper.assertTrue(String.valueOf(args[1]).equals("5"), "the reading says " + args[1] + " blocks, not 5");

        Player human = soulPlayer(helper, SoulState.HUMAN, 40.0F);
        human.moveTo(north);
        translatable(helper, DenreishinkiItem.reading(human), "message.bleach.denreishinki.static");
        Player far = shinigami(helper, 40.0F);
        far.moveTo(north.add(300.0, 0.0, 0.0));
        translatable(helper, DenreishinkiItem.reading(far), "message.bleach.denreishinki.quiet");

        helper.assertTrue(DenreishinkiItem.canRead(ShinigamiTests.student(40.0F), NOW), "a student cannot read a Denreishinki");
        SoulData giver = ShinigamiTests.soul(SoulState.SHINIGAMI, 200.0F);
        ShinigamiLogic.lend(giver, NOW);
        helper.assertFalse(DenreishinkiItem.canRead(giver, NOW + 1), "a Shinigami who lent their power can read a Denreishinki");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 60)
    public static void hollowsDevourPluses(GameTestHelper helper) {
        SpiderHollow spider = helper.spawnWithNoFreeWill(ModEntities.SPIDER_HOLLOW.get(), 4, 2, 4);
        spider.setHealth(10.0F);
        PlusSpirit plus = helper.spawnWithNoFreeWill(ModEntities.PLUS.get(), 6, 2, 4);

        plus.hurt(helper.getLevel().damageSources().mobAttack(spider), 1000.0F);
        // A quarter of its 60 health back, and a tenth of a Spider's 50 reiatsu more.
        helper.assertTrue(Math.abs(spider.getHealth() - 25.0F) < EPSILON, "devouring healed the Hollow to " + spider.getHealth());
        helper.assertTrue(Math.abs(spider.soul().maxReiryoku() - 55.0F) < EPSILON,
                "devouring grew the Hollow's reiatsu to " + spider.soul().maxReiryoku());
        helper.succeedWhen(() -> helper.assertTrue(plus.isRemoved(), "the devoured Plus is still there"));
    }

    @GameTest(template = "empty")
    public static void regenerationWaitsAfterAMaskHit(GameTestHelper helper) {
        long never = ShinigamiProgress.NEVER;
        helper.assertFalse(HollowLogic.mayRegenerate(1000L, 901L, never), "a Hollow healed 99 ticks after it was hurt");
        helper.assertTrue(HollowLogic.mayRegenerate(1000L, 900L, never), "a Hollow did not heal 100 ticks after it was hurt");
        helper.assertFalse(HollowLogic.mayRegenerate(1000L, 900L, 1001L), "a Hollow healed while a blow to its mask stops it");
        helper.succeed();
    }
}
