package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.entity.ZangetsuSpirit;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.Materialisation;
import io.github.masteryoga1.bleach.soul.ReiatsuFleeGoal;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the materialised spirits: a spirit's body set up from its wielder, drawn out by its call, gone home at each
 * of its wielder's ends, fighting its wielder alone down to its floor, and kept out of the inner Hollow's fight. Each
 * test asks only about its own wielders and spirits.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankaiSpiritTests {
    /** Where the tests stand their wielders on the field's floor, as a test sees it: a corner, and the middle. */
    private static final Vec3 FLOOR = new Vec3(1.5, 2.0, 1.5), MIDDLE = new Vec3(8.5, 2.0, 8.5);
    /** T2's pillar, six blocks up under a wielder at the middle, and the block straight ahead of it on the floor. */
    private static final BlockPos PILLAR = new BlockPos(8, 7, 8), AHEAD = new BlockPos(8, 2, 12);

    private BankaiSpiritTests() {
    }

    /** A mock Shinigami who knows Zangetsu's name, standing at this spot of the test and facing south. */
    private static Player wielder(GameTestHelper helper, Vec3 relative) {
        Player player = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        stand(helper, player, relative);
        return player;
    }

    /** Moves this player to a spot of the test, keeping its facing; a joined one is told, so it stays there. */
    private static void stand(GameTestHelper helper, Player player, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        player.teleportTo(at.x, at.y, at.z);
    }

    /** The wielder's spirit drawn out by its call, or the test fails saying why not. */
    private static MaterialisedSpirit drawOut(GameTestHelper helper, Player wielder) {
        Materialisation.Call call = Materialisation.callOut(wielder);
        MaterialisedSpirit spirit = Materialisation.of(wielder.getUUID());
        helper.assertTrue(call == Materialisation.Call.CALLED && spirit != null, "the spirit was not drawn out: " + call);
        return spirit;
    }

    /** On Peaceful no spirit comes out: true, once that is checked, when the test has nothing more to ask. */
    private static boolean refusedOnPeaceful(GameTestHelper helper, Player wielder) {
        if (!HollowPlayerTests.peaceful(helper)) {
            return false;
        }
        Materialisation.Call call = Materialisation.callOut(wielder);
        helper.assertTrue(call == Materialisation.Call.PEACEFUL && !Materialisation.holds(wielder.getUUID()),
                "a spirit came out on Peaceful: " + call);
        helper.succeed();
        return true;
    }

    /** Sends home every spirit these wielders have out, so none outlives its test, passed or failed. */
    private static void sendSpiritsHome(Player... wielders) {
        for (Player wielder : wielders) {
            MaterialisedSpirit spirit = Materialisation.of(wielder.getUUID());
            if (spirit != null) {
                Materialisation.sendHome(spirit);
            }
        }
    }

    /** This spirit's file with its Bankai's {@code entity} replaced, by none for null. */
    private static ZanpakutoSpirit withEntity(ZanpakutoSpirit spirit, @Nullable ResourceLocation entity) {
        ZanpakutoSpirit.Bankai b = spirit.bankai().orElseThrow();
        ZanpakutoSpirit.Bankai bankai = new ZanpakutoSpirit.Bankai(b.name(), b.form(), b.model(), b.releaseCost(),
                b.drain(), b.ability(), b.test(), Optional.ofNullable(entity));
        return new ZanpakutoSpirit(spirit.type(), spirit.name(), spirit.command(), spirit.colour(), spirit.weights(),
                spirit.sealed(), spirit.shikai(), spirit.ability(), spirit.releaseCost(), spirit.drain(),
                spirit.innerWorld(), spirit.tests(), spirit.script(), Optional.of(bankai));
    }

    /** Whether a spirit is gone: no longer held for its wielder, and out of the world. */
    private static boolean gone(Player wielder, MaterialisedSpirit spirit) {
        return !Materialisation.holds(wielder.getUUID()) && spirit.isRemoved();
    }

    /** Whether this joined player was shown a line of this key on its action bar. */
    private static boolean told(ServerPlayer player, String key) {
        return TestPlayers.actionBar(player).stream().anyMatch(line -> GateTests.says(line, key, -1));
    }

    /** A mock host as VisoredTests makes one: a Shinigami who knows Zangetsu's name, 900 reiryoku, full, at the middle. */
    private static Player host(GameTestHelper helper) {
        Player player = wielder(helper, MIDDLE);
        SoulLogic.get(player).setMaxReiryoku(900.0F);
        SoulLogic.get(player).setReiryoku(900.0F);
        return player;
    }

    /** A Hollow rooted in this soul a day ago, ready to be faced now, as VisoredTests readies one. */
    private static void ready(SoulData data, long now) {
        VisoredLogic.setRecord(data, VisoredLogic.record(data).withRootedAt(now - 24_000L).withDuelReadyAt(now));
    }

    /** Ends the host's fight with its inner Hollow, as VisoredTests does, so none is left in the table for another test. */
    private static void end(long now, Player host) {
        VisoredEvents.endAll(host, SoulLogic.get(host), now);
    }

    /**
     * T1. Zangetsu's body, drawn out for a Shinigami who knows his name, is set up from it: a soul as strong, full, no
     * armour, and the wielder, its UUID and the spirit's id kept. Reiatsu never frightens it off, and no spawner makes
     * one. One summoned bare has no wielder.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aSpiritIsSetUpFromItsWielder(GameTestHelper helper) {
        Player ichigo = wielder(helper, FLOOR);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        MaterialisedSpirit spirit = drawOut(helper, ichigo);
        ZangetsuSpirit bare = helper.spawn(ModEntities.ZANGETSU_SPIRIT.get(), new BlockPos(12, 1, 12));
        try {
            float reiryoku = SoulLogic.get(ichigo).maxReiryoku();
            helper.assertTrue(spirit instanceof ZangetsuSpirit && spirit.wielder() == ichigo
                            && ichigo.getUUID().equals(spirit.wielderId()) && spirit.getArmorValue() == 0
                            && ZanpakutoSpirits.ZANGETSU.location().equals(spirit.spiritId()) && bare.wielder() == null,
                    "the body is not Zangetsu's, of Ichigo's, with no armour, or a bare one has a wielder");
            SoulData its = SoulLogic.get(spirit);
            helper.assertTrue(its.state() == SoulState.SHINIGAMI && its.maxReiryoku() == reiryoku
                            && its.reiryoku() == reiryoku,
                    "Zangetsu's soul is " + its.state() + " at " + its.reiryoku() + " of " + its.maxReiryoku());
            helper.assertTrue(spirit.goalSelector.getAvailableGoals().stream()
                            .noneMatch(goal -> goal.getGoal() instanceof ReiatsuFleeGoal)
                            && !spirit.checkSpawnRules(helper.getLevel(), MobSpawnType.SPAWNER),
                    "Zangetsu runs from reiatsu, or a spawner may make him");
        } finally {
            Materialisation.sendHome(spirit);
            Materialisation.sendHome(bare);
        }
        helper.succeed();
    }

    /**
     * T2. {@code /bleach zanpakuto … bankai spirit} draws a named Shinigami's Zangetsu out, four blocks before it and
     * facing it, and passes over, saying why, a Human and a Shinigami who does not know the name. A second call is
     * refused, one out already, and a dead wielder's before that; a file naming no type, a type not registered or a pig
     * is no body. A wielder on a pillar, with nothing to stand on within two blocks below any point, draws none, and on
     * the floor draws one; with the point straight ahead blocked, it steps out at the next, 45° round.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void aSpiritIsDrawnOutBesideItsWielder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer ichigo = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true,
                "bleach_spirit_t2", "bleach_spirit_t2_ichigo");
        BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.HUMAN, 500.0F), false, "bleach_spirit_t2");
        BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), false, "bleach_spirit_t2");
        stand(helper, ichigo, FLOOR);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player boxed = wielder(helper, MIDDLE.add(0.0, 6.0, 0.0));
        String drawn = "commands.bleach.zanpakuto.bankai.spirit";
        try {
            List<Component> said = GateTests.run(level, "bleach zanpakuto @a[tag=bleach_spirit_t2] bankai spirit");
            helper.assertTrue(said.size() == 3 && said.stream().anyMatch(line -> GateTests.says(line, drawn, -1))
                            && said.stream().anyMatch(line -> BankaiTests.refused(line, Materialisation.Call.NO_POWER.key()))
                            && said.stream().anyMatch(line -> BankaiTests.refused(line, Materialisation.Call.NAMELESS.key())),
                    "the call said " + said.stream().map(Component::getString).toList());
            MaterialisedSpirit spirit = Materialisation.of(ichigo.getUUID());
            helper.assertTrue(spirit instanceof ZangetsuSpirit && spirit.wielder() == ichigo
                    && ZanpakutoSpirits.ZANGETSU.location().equals(spirit.spiritId()), "no Zangetsu of Ichigo's came out");
            Vec3 before = ichigo.position().add(0.0, 0.0, Materialisation.CALL_DISTANCE);
            helper.assertTrue(spirit.position().distanceTo(before) < 1.0E-3
                            && Math.abs(Mth.wrapDegrees(spirit.getYRot() - 180.0F)) < 1.0F,
                    "Zangetsu came out at " + spirit.position() + " facing " + spirit.getYRot() + ", not before Ichigo");

            said = GateTests.run(level, "bleach zanpakuto @a[tag=bleach_spirit_t2_ichigo,limit=1] bankai spirit");
            helper.assertTrue(said.size() == 1 && BankaiTests.refused(said.get(0), Materialisation.Call.ALREADY.key())
                            && level.getEntitiesOfClass(ZangetsuSpirit.class, new AABB(ichigo.blockPosition()).inflate(16.0),
                                    each -> each.wielder() == ichigo).size() == 1,
                    "a second call said " + said + " or drew a second spirit out");
            ZanpakutoSpirit zangetsu = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU);
            for (ResourceLocation entity : new ResourceLocation[] {null, ResourceLocation.withDefaultNamespace("pig"),
                    BleachMod.id("missing")}) {
                helper.assertTrue(Materialisation.create(level, withEntity(zangetsu, entity)) == null,
                        "a Bankai naming " + entity + " gave a spirit a body");
            }

            helper.setBlock(PILLAR, Blocks.STONE);
            Materialisation.Call call = Materialisation.callOut(boxed);
            helper.assertTrue(call == Materialisation.Call.NO_ROOM && !Materialisation.holds(boxed.getUUID()),
                    "on a pillar with nothing to stand on about it, the call came to " + call);
            stand(helper, boxed, MIDDLE);
            Materialisation.sendHome(drawOut(helper, boxed));
            helper.setBlock(AHEAD, Blocks.STONE);
            MaterialisedSpirit turned = drawOut(helper, boxed);
            Vec3 round = boxed.position().add(Vec3.directionFromRotation(0.0F, 45.0F).scale(Materialisation.CALL_DISTANCE));
            helper.assertTrue(turned.position().distanceTo(round) < 1.0E-3,
                    "with the way ahead blocked, the spirit came out at " + turned.position() + ", not " + round);
            boxed.setHealth(0.0F);
            call = Materialisation.callOut(boxed);
            helper.assertTrue(call == Materialisation.Call.DEAD, "a dead wielder with its spirit out was told " + call);
        } finally {
            helper.setBlock(PILLAR, Blocks.AIR);
            helper.setBlock(AHEAD, Blocks.AIR);
            sendSpiritsHome(ichigo, boxed);
        }
        helper.succeed();
    }

    /**
     * T3. Every end of its wielder takes a spirit home: death, a removed (logged out) player and straying 40 blocks off,
     * binding another spirit and losing its Shinigami power, on the spirit's next tick; another world at once, on its
     * event. One unloaded with its chunk is forgotten as it goes, so its wielder may draw it out again at once; one
     * summoned bare goes on its first tick. Reaching Bankai and having it revoked end nothing. A spirit gone is held for
     * no one.
     */
    @GameTest(template = "field", timeoutTicks = 30)
    public static void aSpiritGoesWithItsWielder(GameTestHelper helper) {
        Player[] wielders = new Player[8];
        for (int i = 0; i < wielders.length; i++) {
            wielders[i] = wielder(helper, FLOOR);
        }
        if (refusedOnPeaceful(helper, wielders[0])) {
            return;
        }
        // Each wielder's spirit, and at the end the one drawn out again after the unload.
        MaterialisedSpirit[] spirits = new MaterialisedSpirit[wielders.length + 1];
        SoulData reached = SoulLogic.get(wielders[6]);
        ZangetsuSpirit bare = helper.spawn(ModEntities.ZANGETSU_SPIRIT.get(), new BlockPos(12, 2, 12));
        Runnable home = () -> {
            sendSpiritsHome(wielders);
            Materialisation.sendHome(bare);
        };
        HihioZabimaruTests.clearingOnFailure(home, () -> {
            for (int i = 0; i < wielders.length; i++) {
                spirits[i] = drawOut(helper, wielders[i]);
            }
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(wielders[0], Level.OVERWORLD, Level.NETHER));
            helper.assertTrue(gone(wielders[0], spirits[0]), "a spirit outlasted its wielder's change of world");
            wielders[1].setHealth(0.0F);
            wielders[2].discard();
            wielders[3].setPos(wielders[3].position().add(40.0, 0.0, 0.0));
            SoulData rebound = SoulLogic.get(wielders[4]);
            rebound.setZanpakuto(rebound.zanpakuto().withSpirit(Optional.of(ZanpakutoSpirits.ZABIMARU.location())));
            wielders[5].setData(SoulAttachments.SOUL, ZanpakutoTests.soul(SoulState.HUMAN, 510.0F));
            BankaiLogic.reach(reached, Component.literal("Tensa Zangetsu"), helper.getLevel().getGameTime());
            spirits[7].setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(gone(wielders[7], spirits[7]), "a spirit unloaded with its chunk is still held");
            spirits[8] = drawOut(helper, wielders[7]);
        });
        helper.runAfterDelay(3, () -> HihioZabimaruTests.clearingOnFailure(home, () -> {
            for (int i = 1; i <= 5; i++) {
                helper.assertTrue(gone(wielders[i], spirits[i]), "spirit " + i + " outlasted its wielder's end");
            }
            helper.assertTrue(bare.isRemoved(), "a spirit summoned bare is still here");
            helper.assertTrue(Materialisation.of(wielders[6].getUUID()) == spirits[6], "reaching Bankai sent it home");
            BankaiLogic.forget(reached);
        }));
        helper.runAfterDelay(6, () -> HihioZabimaruTests.clearingOnFailure(home, () -> {
            helper.assertTrue(Materialisation.of(wielders[6].getUUID()) == spirits[6]
                            && Materialisation.of(wielders[7].getUUID()) == spirits[8],
                    "revoking Bankai sent a spirit home, or the one drawn out again after its unload went");
            home.run();
            helper.succeed();
        }));
    }

    /**
     * T4. A spirit fights its wielder alone: Zangetsu, with his cleaver, which has nothing of a blade's power, at full
     * health by the wielder's maximum reiryoku and with a blow by that and its Zanjutsu. No one else's blow reaches it (a
     * bystander's melee, arrow and Getsuga Tenshō, a zombie's, an explosion's and the fire's), while its wielder's
     * melee, arrow and Getsuga Tenshō land; it may attack its wielder and no one else, and keeps its wielder, invisible,
     * for its target; one whose wielder is in creative mode waits with none.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void aSpiritFightsOnlyItsWielder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player ichigo = wielder(helper, FLOOR);
        SoulData soul = SoulLogic.get(ichigo);
        soul.setLevel(Stat.ZANJUTSU, 10);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, soul.copy());
        stand(helper, creative, MIDDLE);
        Player bystander = wielder(helper, MIDDLE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 12);
        Runnable home = () -> {
            sendSpiritsHome(ichigo, creative);
            zombie.discard();
        };
        HihioZabimaruTests.clearingOnFailure(home, () -> {
            MaterialisedSpirit spirit = drawOut(helper, ichigo);
            drawOut(helper, creative);
            double root = Math.sqrt(soul.maxReiryoku());
            double health = BleachConfig.get(BleachConfig.BANKAI_SPIRIT_HEALTH_BASE)
                    + BleachConfig.get(BleachConfig.BANKAI_SPIRIT_HEALTH_PER_ROOT) * root;
            double blow = (BleachConfig.get(BleachConfig.BANKAI_SPIRIT_BLOW_BASE)
                    + BleachConfig.get(BleachConfig.BANKAI_SPIRIT_BLOW_PER_ROOT) * root) * SoulLogic.zanjutsuMultiplier(soul);
            double attack = spirit.getAttributeValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(Math.abs(spirit.getMaxHealth() - health) < 1.0E-3 && spirit.getHealth() == spirit.getMaxHealth()
                            && Math.abs(attack - blow) < 1.0E-3 && SoulLogic.zanjutsuMultiplier(soul) > 1.0F,
                    "Zangetsu's health " + spirit.getMaxHealth() + " or blow " + attack + " is not " + health + ", " + blow);
            ItemStack blade = spirit.getMainHandItem();
            helper.assertTrue(blade.is(ModItems.ASAUCHI.get()) && ZanpakutoTests.model(blade) == 1
                            && ItemAttributeModifiers.EMPTY.equals(blade.get(DataComponents.ATTRIBUTE_MODIFIERS)),
                    "Zangetsu holds " + blade + ", not the cleaver with nothing of its power");

            DamageSources sources = level.damageSources();
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            float whole = spirit.getHealth();
            for (DamageSource source : List.of(sources.playerAttack(bystander), sources.arrow(arrow, bystander),
                    getsuga(level, arrow, bystander), sources.mobAttack(zombie), sources.explosion(null, null),
                    sources.inFire())) {
                helper.assertTrue(!spirit.hurt(source, 5.0F) && spirit.getHealth() == whole,
                        "a blow of " + source.getMsgId() + " from " + source.getEntity() + " reached the spirit");
            }
            for (DamageSource source : List.of(sources.playerAttack(ichigo), sources.arrow(arrow, ichigo),
                    getsuga(level, arrow, ichigo))) {
                spirit.invulnerableTime = 0;
                helper.assertTrue(spirit.hurt(source, 5.0F) && spirit.getHealth() < whole,
                        "its wielder's blow of " + source.getMsgId() + " did not land");
                whole = spirit.getHealth();
            }
            helper.assertTrue(spirit.canAttack(ichigo) && !spirit.canAttack(bystander) && !spirit.canAttack(zombie),
                    "the spirit may attack someone besides its wielder");
            ichigo.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200));
        });
        helper.runAfterDelay(20, () -> HihioZabimaruTests.clearingOnFailure(home, () -> {
            MaterialisedSpirit spirit = Materialisation.of(ichigo.getUUID());
            MaterialisedSpirit waiting = Materialisation.of(creative.getUUID());
            helper.assertTrue(spirit != null && spirit.getTarget() == ichigo,
                    "the spirit lost its invisible wielder: " + (spirit == null ? null : spirit.getTarget()));
            helper.assertTrue(waiting != null && waiting.getTarget() == null, "a creative wielder's spirit went, or took "
                    + (waiting == null ? null : waiting.getTarget()) + " for its target");
            home.run();
            helper.succeed();
        }));
    }

    /** A Getsuga Tenshō's blow, its wave (here this projectile) the direct cause and this soul the one who loosed it. */
    private static DamageSource getsuga(ServerLevel level, Entity wave, Player owner) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(GetsugaTensho.DAMAGE_TYPE), wave, owner);
    }

    /**
     * T5. A spirit is beaten down to its floor and no further: a blow past it leaves it there, alive, however many more
     * come, and on its next tick it goes home, its wielder told so as it was told it came out. Its bar, named for it,
     * shows its wielder alone, with another soul who sees spirits beside it. {@code /kill} passes the floor.
     */
    @GameTest(template = "field", timeoutTicks = 30)
    public static void aSpiritStopsAtItsFloorAndGoesHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer ichigo = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true);
        ServerPlayer other = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true);
        stand(helper, ichigo, FLOOR);
        stand(helper, other, MIDDLE);
        if (refusedOnPeaceful(helper, ichigo)) {
            return;
        }
        Player killed = wielder(helper, MIDDLE);
        Runnable home = () -> sendSpiritsHome(ichigo, killed);
        MaterialisedSpirit spirit = drawOut(helper, ichigo);
        HihioZabimaruTests.clearingOnFailure(home, () -> {
            helper.assertTrue(told(ichigo, "message.bleach.spirit.out"), "the wielder was not told its spirit came out");
            spirit.bar().refresh();
            helper.assertTrue(spirit.bar().players().size() == 1 && spirit.bar().players().contains(ichigo)
                            && GateTests.says(spirit.bar().event().getName(), "bossbar.bleach.zangetsu_spirit", -1),
                    "Zangetsu's bar shows " + spirit.bar().players() + " as " + spirit.bar().event().getName());
            float floor = spirit.floor();
            double share = BleachConfig.get(BleachConfig.BANKAI_SPIRIT_FLOOR_SHARE);
            helper.assertTrue(Math.abs(floor - spirit.getMaxHealth() * share) < 1.0E-3, "the floor is " + floor);
            spirit.hurt(level.damageSources().playerAttack(ichigo), 10_000.0F);
            helper.assertTrue(spirit.isAlive() && Math.abs(spirit.getHealth() - floor) < 1.0E-2 && spirit.beaten(),
                    "a blow past the floor left " + spirit.getHealth() + " of a floor of " + floor);
            spirit.invulnerableTime = 0;
            spirit.hurt(level.damageSources().playerAttack(ichigo), 10_000.0F);
            helper.assertTrue(spirit.isAlive() && Math.abs(spirit.getHealth() - floor) < 1.0E-2,
                    "a second blow at the floor took it to " + spirit.getHealth());
            MaterialisedSpirit victim = drawOut(helper, killed);
            victim.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(victim.isDeadOrDying(), "/kill did not pass the spirit's floor");
        });
        helper.runAfterDelay(2, () -> HihioZabimaruTests.clearingOnFailure(home, () -> {
            helper.assertTrue(gone(ichigo, spirit) && spirit.bar().players().isEmpty() && other.isAlive(),
                    "a beaten spirit did not go home, or left its bar up, or the soul beside it died");
            helper.assertTrue(told(ichigo, "message.bleach.spirit.beaten"), "the wielder was not told its spirit was beaten");
            home.run();
            helper.succeed();
        }));
    }

    /**
     * T7. No spirit is drawn out while its wielder faces its inner Hollow, and none is out for it then; once that fight
     * ends, one is, and a fight begun again sends it home on its next tick, run here by hand so that no fight outlives
     * this tick of the test.
     */
    @GameTest(template = "field")
    public static void aSpiritIsRefusedAndSentHomeInADuel(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = host(helper);
        SoulData data = SoulLogic.get(host);
        ready(data, now);
        if (refusedOnPeaceful(helper, host)) {
            return;
        }
        try {
            VisoredEvents.DuelStart start = VisoredEvents.beginDuel(host, data, now, false);
            Materialisation.Call call = Materialisation.callOut(host);
            helper.assertTrue(start == VisoredEvents.DuelStart.STARTED && call == Materialisation.Call.BUSY
                    && Materialisation.of(host.getUUID()) == null, "the fight " + start + ", and the call in it " + call);
            end(now, host);
            MaterialisedSpirit spirit = drawOut(helper, host);
            ready(data, now);
            start = VisoredEvents.beginDuel(host, data, now, false);
            spirit.tick();
            helper.assertTrue(start == VisoredEvents.DuelStart.STARTED && gone(host, spirit),
                    "a spirit out stayed as its wielder's fight with its inner Hollow came to " + start);
        } finally {
            end(now, host);
            sendSpiritsHome(host);
        }
        helper.succeed();
    }
}
