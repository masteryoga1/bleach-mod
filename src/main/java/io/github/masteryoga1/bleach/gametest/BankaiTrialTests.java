package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.BankaiTrial;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.Materialisation;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the Bankai trial, a bout with one's own zanpakutō spirit. Each test asks only about its own wielders and
 * spirits, and every bout ends within the tick it began in, so none outlives its test: on a failure, every soul the
 * test began or may have begun a bout for is cleared, the refused ones too. So the server stop's {@code breakOffAll},
 * which T2 calls by hand and which ends every bout there is, finds only its own. A bout ticked by hand at a multiple
 * of 5 ticks from its start keeps others out of its ring and 2 blocks more, which at the default radius reaches past
 * the field into the next test's, so such a test begins its bout at the middle with the ring at its smallest, 6 blocks
 * ({@link #inSmallRing}). T7's creatures, placed at tick 0, are found by the ring, as they are inside the template's
 * own chunks, which tick entities before a test begins (mc GameTestInfo.java:101).
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankaiTrialTests {
    /** Where the tests stand their wielders on the field's floor, as a test sees it: a corner, the middle, the far corner. */
    static final Vec3 FLOOR = new Vec3(1.5, 2.0, 1.5), MIDDLE = new Vec3(8.5, 2.0, 8.5), FAR = new Vec3(14.5, 2.0, 14.5);
    static final String BEGUN = "message.bleach.bankai.trial.begun", BROKEN = "message.bleach.bankai.trial.broken_off";
    static final String WON = "message.bleach.bankai.trial.won", YIELDED = "message.bleach.bankai.trial.yielded";
    static final String NEAR = "message.bleach.bankai.trial.duel_near", BARRIER = "message.bleach.bankai.trial.barrier";

    private BankaiTrialTests() {
    }

    /** Begins this wielder's bout now, or fails saying why not. */
    static BankaiTrial.Trial begun(GameTestHelper helper, Player wielder, long now) {
        BankaiTrial.Start start = BankaiTrial.begin(wielder, now);
        BankaiTrial.Trial trial = BankaiTrial.trial(wielder.getUUID());
        helper.assertTrue(start == BankaiTrial.Start.STARTED && trial != null, "the bout did not begin: " + start);
        return trial;
    }

    /**
     * Runs these steps with the ring at its smallest, 6 blocks, which from the middle stays inside the field: for a test
     * that ticks a bout by hand at a multiple of 5 ticks from its start, when the ring keeps others out.
     */
    static void inSmallRing(Runnable steps) {
        KidoTests.withConfig(BleachConfig.BANKAI_TRIAL_RING_RADIUS, 6, steps);
    }

    /** A wielder at the far corner, facing north, so its spirit steps out inside the field. */
    static Player farWielder(GameTestHelper helper) {
        Player wielder = BankaiSpiritTests.wielder(helper, FAR);
        wielder.setYRot(180.0F);
        return wielder;
    }

    static BankaiRecord record(Player wielder) {
        return SoulLogic.get(wielder).zanpakuto().bankai();
    }

    /** Whether this wielder's bout is over, its spirit gone, with nothing written but the try it began at now. */
    static boolean over(Player wielder, MaterialisedSpirit spirit, long now) {
        BankaiRecord record = record(wielder);
        return BankaiTrial.trial(wielder.getUUID()) == null && BankaiSpiritTests.gone(wielder, spirit)
                && record.tries() == 1 && record.lastTryAt() == now && !record.achieved();
    }

    /** On Peaceful no bout begins and nothing is counted: true, once that is checked, when the test is done. */
    static boolean refusedOnPeaceful(GameTestHelper helper, Player wielder, long now) {
        if (!HollowPlayerTests.peaceful(helper)) {
            return false;
        }
        HihioZabimaruTests.clearingOnFailure(() -> clear(wielder), () -> {
            BankaiTrial.Start start = BankaiTrial.begin(wielder, now);
            helper.assertTrue(start == BankaiTrial.Start.PEACEFUL && record(wielder).tries() == 0,
                    "a bout began on Peaceful, or counted its try: " + start);
        });
        helper.succeed();
        return true;
    }

    /** Sends home any spirit these wielders have out, which ends any bout of theirs, so none outlives its test. */
    static void clear(Player... wielders) {
        for (Player wielder : wielders) {
            MaterialisedSpirit spirit = Materialisation.of(wielder.getUUID());
            if (spirit != null) {
                Materialisation.sendHome(spirit);
            }
        }
    }

    /** Whether this spirit's bar reads its own name, by this key, and these seconds left, as a bout's clock shows them. */
    static boolean clock(MaterialisedSpirit spirit, String name, long seconds) {
        return spirit.bar().event().getName().getContents() instanceof TranslatableContents words
                && words.getKey().equals("bossbar.bleach.bankai_trial") && words.getArgs().length == 2
                && words.getArgs()[0] instanceof Component named && GateTests.says(named, name, -1)
                && Long.valueOf(seconds).equals(words.getArgs()[1]);
    }

    /**
     * The damage a blow of 1,000 from this source comes to on this joined soul once every listener has weighed it, the
     * loss among them: posted by hand, as a soul just joined is never hurt (mc ServerPlayer.java:758).
     */
    static float weighed(ServerPlayer soul, DamageSource source) {
        return NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(soul, new DamageContainer(source, 1_000.0F))).getNewDamage();
    }

    /**
     * T1. A bout begins for a named Shinigami where it stands: the spirit it had out goes home for a fresh, whole one
     * that may fight it alone, the try is counted, and the soul is told its time. On Peaceful the spirit out stays out
     * and nothing is counted. A second bout and a second call are refused while it lasts; reaching Bankai another way
     * breaks it off, and so do creative mode and spectating, the soul told so and trying again at once, counted twice.
     * The dead, the creative, one past Bankai and the call's own refusals (a Human, a nameless soul) are refused with
     * nothing counted.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aTrialBeginsCountsItsTryAndFightsItsWielder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.wielder(helper, MIDDLE);
        SoulData data = SoulLogic.get(ichigo);
        if (refusedOnPeaceful(helper, ichigo, now)) {
            return;
        }
        ServerPlayer joined = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true);
        BankaiSpiritTests.stand(helper, joined, FLOOR);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setData(SoulAttachments.SOUL, data.copy());
        Player dead = BankaiSpiritTests.wielder(helper, FLOOR);
        dead.setHealth(0.0F);
        Player reached = BankaiSpiritTests.wielder(helper, FLOOR);
        BankaiLogic.reach(SoulLogic.get(reached), Component.literal("Tensa Zangetsu"), now);
        Map<Player, BankaiTrial.Start> refused = Map.of(dead, BankaiTrial.Start.DEAD, creative,
                BankaiTrial.Start.WATCHING, reached, BankaiTrial.Start.REACHED,
                ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.HUMAN, 500.0F)), BankaiTrial.Start.NO_POWER,
                ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F)), BankaiTrial.Start.NAMELESS);
        HihioZabimaruTests.clearingOnFailure(() -> {
            clear(ichigo, joined);
            refused.keySet().forEach(BankaiTrialTests::clear);
        }, () -> {
            refused.forEach((soul, reason) -> {
                BankaiTrial.Start start = BankaiTrial.begin(soul, now);
                helper.assertTrue(start == reason && BankaiTrial.trial(soul.getUUID()) == null
                                && !Materialisation.holds(soul.getUUID()) && record(soul).tries() == 0,
                        "a soul to be refused " + reason + " came to " + start + ", or its try was counted");
            });

            MaterialisedSpirit free = BankaiSpiritTests.drawOut(helper, ichigo);
            // Peaceful within this one call, so no tick sees it.
            MinecraftServer server = level.getServer();
            Difficulty difficulty = server.getWorldData().getDifficulty();
            BankaiTrial.Start calm;
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                calm = BankaiTrial.begin(ichigo, now);
            } finally {
                server.setDifficulty(difficulty, true);
            }
            helper.assertTrue(calm == BankaiTrial.Start.PEACEFUL && Materialisation.of(ichigo.getUUID()) == free
                    && record(ichigo).tries() == 0, "on Peaceful a bout came to " + calm + ", or the spirit out went");
            free.hurt(level.damageSources().playerAttack(ichigo), 5.0F);
            BankaiTrial.Trial trial = begun(helper, ichigo, now);
            MaterialisedSpirit spirit = trial.spirit();
            helper.assertTrue(free.isRemoved() && spirit != free && Materialisation.of(ichigo.getUUID()) == spirit
                            && spirit.wielder() == ichigo && spirit.getHealth() == spirit.getMaxHealth()
                            && spirit.canAttack(ichigo) && !spirit.canAttack(joined),
                    "the spirit out did not go home for a fresh, whole one that fights its wielder alone");
            helper.assertTrue(record(ichigo).tries() == 1 && record(ichigo).lastTryAt() == now
                            && trial.centre().equals(ichigo.position()) && trial.startedAt() == now
                            && BankaiTrial.boutSeconds() == 300 && trial.endsAt() == now + 300 * 20L,
                    "the bout began as " + record(ichigo) + ", from " + trial.centre() + " until " + trial.endsAt());
            BankaiTrial.Start again = BankaiTrial.begin(ichigo, now);
            Materialisation.Call call = Materialisation.callOut(ichigo);
            helper.assertTrue(again == BankaiTrial.Start.ALREADY && call == Materialisation.Call.ALREADY
                            && Materialisation.of(ichigo.getUUID()) == spirit && record(ichigo).tries() == 1,
                    "in a bout, a second came to " + again + " and a call to " + call);
            BankaiLogic.reach(data, Component.literal("Tensa Zangetsu"), now);
            BankaiTrial.tick(ichigo, data, now);
            helper.assertTrue(BankaiTrial.trial(ichigo.getUUID()) == null && BankaiSpiritTests.gone(ichigo, spirit)
                    && record(ichigo).tries() == 1, "reaching Bankai another way left the bout going");

            BankaiSpiritTests.drawOut(helper, joined);
            GameType[] modes = {GameType.CREATIVE, GameType.SPECTATOR};
            for (int i = 0; i < modes.length; i++) {
                joined.setGameMode(GameType.SURVIVAL);
                MaterialisedSpirit its = begun(helper, joined, now + i).spirit();
                joined.setGameMode(modes[i]);
                BankaiTrial.tick(joined, SoulLogic.get(joined), now + i);
                helper.assertTrue(BankaiTrial.trial(joined.getUUID()) == null && BankaiSpiritTests.gone(joined, its),
                        modes[i] + " did not break the bout off");
            }
            BankaiRecord tried = record(joined);
            helper.assertTrue(tried.tries() == 2 && tried.lastTryAt() == now + 1L && !tried.achieved()
                            && TestPlayers.chat(joined).stream().anyMatch(line -> GateTests.says(line, BEGUN, -1))
                            && BankaiSpiritTests.told(joined, BROKEN)
                            && !BankaiSpiritTests.told(joined, "message.bleach.spirit.home"),
                    "the tries came to " + tried + ", or the soul was not told its time and its break-offs alone");
        });
        helper.succeed();
    }

    /**
     * T2. Every break-off ends a bout with nothing written but its try, and its spirit gone: its wielder's death and
     * logging out, each on its event; another player object of the same UUID, on that one's tick; losing the power, as
     * the spirit's own tick sends it home; another world, on its event; straying 40 blocks from where it began, on the
     * wielder's tick; the spirit unloaded with its chunk, or killed by {@code /kill}; and the time running out. The
     * server stop's {@code breakOffAll} ends two at once, at the field's opposite corners with the ring at its smallest,
     * so the two rings do not meet. It is called by hand: posting a stop would run every stop listener on the live
     * server, so of the bout's own listeners only the death's and the logout's are posted through the bus, and the
     * stop's wiring is read, not tested. A spirit carried into an unloaded chunk ends its bout too, left in the world for
     * the world to finish moving.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void everyBreakOffEndsTheTrialWithNothingWritten(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        if (refusedOnPeaceful(helper, BankaiSpiritTests.wielder(helper, MIDDLE), now)) {
            return;
        }
        Map<String, BiConsumer<Player, MaterialisedSpirit>> ends = new LinkedHashMap<>();
        ends.put("its death", (wielder, spirit) -> {
            // At no health, as a player is when its death is posted.
            wielder.setHealth(0.0F);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(wielder, level.damageSources().generic()));
        });
        ends.put("logging out", (wielder, spirit) -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(wielder)));
        ends.put("another body", (wielder, spirit) -> {
            Player twin = helper.makeMockPlayer(GameType.SURVIVAL);
            twin.setUUID(wielder.getUUID());
            twin.setPos(wielder.position());
            BankaiTrial.tick(twin, SoulLogic.get(wielder), now);
        });
        ends.put("losing the power", (wielder, spirit) -> {
            SoulData data = SoulLogic.get(wielder);
            data.setShinigami(data.shinigami().withPowerLentUntil(now + 1_000L));
            Materialisation.upkeep(spirit);
        });
        ends.put("another world", (wielder, spirit) -> NeoForge.EVENT_BUS.post(
                new PlayerEvent.PlayerChangedDimensionEvent(wielder, Level.OVERWORLD, Level.NETHER)));
        ends.put("straying", (wielder, spirit) -> {
            wielder.setPos(wielder.position().add(40.0, 0.0, 0.0));
            BankaiTrial.tick(wielder, SoulLogic.get(wielder), now);
        });
        ends.put("its unload", (wielder, spirit) -> {
            boolean tracked = level.getEntity(spirit.getUUID()) == spirit;
            spirit.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.assertTrue(tracked && level.getEntity(spirit.getUUID()) == null, "the spirit was not tracked, or stayed");
        });
        ends.put("/kill", (wielder, spirit) -> {
            spirit.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            Materialisation.upkeep(spirit);
        });
        ends.put("the time", (wielder, spirit) -> BankaiTrial.tick(wielder, SoulLogic.get(wielder),
                now + BankaiTrial.boutSeconds() * 20L));
        ends.forEach((end, cause) -> {
            Player wielder = BankaiSpiritTests.wielder(helper, MIDDLE);
            HihioZabimaruTests.clearingOnFailure(() -> clear(wielder), () -> {
                MaterialisedSpirit spirit = begun(helper, wielder, now).spirit();
                cause.accept(wielder, spirit);
                helper.assertTrue(over(wielder, spirit, now), "the bout outlasted " + end + ", or wrote " + record(wielder));
            });
        });

        Player[] pair = {BankaiSpiritTests.wielder(helper, FLOOR), farWielder(helper)};
        inSmallRing(() -> HihioZabimaruTests.clearingOnFailure(() -> clear(pair), () -> {
            MaterialisedSpirit first = begun(helper, pair[0], now).spirit(), second = begun(helper, pair[1], now).spirit();
            BankaiTrial.breakOffAll();
            helper.assertTrue(over(pair[0], first, now) && over(pair[1], second, now), "breakOffAll left a bout going");
        }));
        Player carried = BankaiSpiritTests.wielder(helper, MIDDLE);
        MaterialisedSpirit spirit = begun(helper, carried, now).spirit();
        try {
            spirit.setPos(spirit.getX() + 10_000.0, spirit.getY(), spirit.getZ());
            helper.assertTrue(BankaiTrial.trial(carried.getUUID()) == null && !Materialisation.holds(carried.getUUID())
                            && !spirit.isRemoved() && record(carried).tries() == 1,
                    "a spirit carried into an unloaded chunk left its bout going, or was sent home as it moved");
        } finally {
            Materialisation.sendHome(spirit);
        }
        helper.succeed();
    }

    /**
     * T3. A spirit's blow that would leave its wielder at {@code loss_health} (2 where no config is set) or below stops
     * there and the bout is lost: through absorption, which goes first, with a totem in hand, which is kept, on a
     * wielder already below it, whose health stands, and on one it leaves at exactly the floor, a joined soul told so in
     * chat; a blow that absorption alone keeps above the floor lands whole. Out of a bout the spirit's last blow
     * stops the same way, and it goes home saying it won, with no try counted. While a bout lasts the wielder's blow,
     * arrow and Getsuga Tenshō on a zombie or another soul, and theirs on it, are stopped, while a fall and its spirit's
     * lighter blow land and the bout runs on.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aLossStopsAtTheFloorAndNeverKills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DamageSources sources = level.damageSources();
        long now = HollowPlayerTests.now(helper);
        float floor = BankaiTrial.lossHealth();
        Player ichigo = BankaiSpiritTests.wielder(helper, MIDDLE);
        if (refusedOnPeaceful(helper, ichigo, now)) {
            return;
        }
        ServerPlayer joined = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true);
        BankaiSpiritTests.stand(helper, joined, FLOOR);
        Player low = BankaiSpiritTests.wielder(helper, MIDDLE), light = BankaiSpiritTests.wielder(helper, MIDDLE);
        Player kept = BankaiSpiritTests.wielder(helper, MIDDLE), edge = BankaiSpiritTests.wielder(helper, MIDDLE);
        Player other = BankaiSpiritTests.wielder(helper, FLOOR);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 12);
        Runnable cleared = () -> {
            clear(ichigo, joined, low, light, kept, edge);
            zombie.discard();
        };
        HihioZabimaruTests.clearingOnFailure(cleared, () -> {
            helper.assertTrue(floor == 2.0F, "the loss health is " + floor + " where no config is set");
            ichigo.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4.0);
            ichigo.setAbsorptionAmount(4.0F);
            ichigo.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            MaterialisedSpirit spirit = begun(helper, ichigo, now).spirit();
            ichigo.hurt(sources.mobAttack(spirit), 1_000.0F);
            helper.assertTrue(ichigo.isAlive() && Math.abs(ichigo.getHealth() - floor) < 1.0E-3
                            && ichigo.getAbsorptionAmount() == 0.0F && ichigo.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                            && over(ichigo, spirit, now),
                    "the losing blow left " + ichigo.getHealth() + " and " + ichigo.getAbsorptionAmount()
                            + " absorption, spent the totem, or left the bout going");
            low.setHealth(1.5F);
            MaterialisedSpirit lowSpirit = begun(helper, low, now).spirit();
            low.hurt(sources.mobAttack(lowSpirit), 5.0F);
            helper.assertTrue(low.isAlive() && low.getHealth() == 1.5F && over(low, lowSpirit, now),
                    "a blow on a wielder below the floor left " + low.getHealth() + ", or the bout going");
            // On Normal a creature's blow is not scaled (mc Player.java:873-885). A fresh mock has 20 health and no
            // armour, effects or enchantments, and its spirit's reiatsu matches its own, so nothing cuts the blow before
            // it reaches the loss at LivingDamageEvent.Pre, where the absorption is still to be taken (nfp
            // Player.java.patch, actuallyHurt). So 19 through 4 absorption leaves 20 - (19 - 4) = 5, above the floor,
            // and lands whole, and 18 with none leaves exactly the floor, 2, and loses.
            MinecraftServer server = level.getServer();
            Difficulty difficulty = server.getWorldData().getDifficulty();
            server.setDifficulty(Difficulty.NORMAL, true);
            try {
                kept.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4.0);
                kept.setAbsorptionAmount(4.0F);
                MaterialisedSpirit keptSpirit = begun(helper, kept, now).spirit();
                kept.hurt(sources.mobAttack(keptSpirit), 19.0F);
                helper.assertTrue(kept.getHealth() == 5.0F && kept.getAbsorptionAmount() == 0.0F
                                && BankaiTrial.trial(kept.getUUID()) != null && !keptSpirit.isRemoved(),
                        "a blow its absorption kept above the floor left " + kept.getHealth() + ", or ended the bout");
                Materialisation.sendHome(keptSpirit);
                MaterialisedSpirit edgeSpirit = begun(helper, edge, now).spirit();
                edge.hurt(sources.mobAttack(edgeSpirit), 18.0F);
                helper.assertTrue(edge.getHealth() == floor && over(edge, edgeSpirit, now),
                        "a blow to exactly the floor left " + edge.getHealth() + ", or the bout going");
            } finally {
                server.setDifficulty(difficulty, true);
            }

            MaterialisedSpirit its = begun(helper, joined, now).spirit();
            float lost = weighed(joined, sources.mobAttack(its));
            helper.assertTrue(Math.abs(joined.getHealth() - lost - floor) < 1.0E-3 && over(joined, its, now)
                            && TestPlayers.chat(joined).stream().anyMatch(line -> GateTests.says(line,
                                    "message.bleach.bankai.trial.lost", -1)),
                    "a joined soul's losing blow came to " + lost + ", left the bout going, or was not told");
            MaterialisedSpirit free = BankaiSpiritTests.drawOut(helper, joined);
            float won = weighed(joined, sources.mobAttack(free));
            helper.assertTrue(Math.abs(joined.getHealth() - won - floor) < 1.0E-3 && BankaiSpiritTests.gone(joined, free)
                            && BankaiSpiritTests.told(joined, "message.bleach.spirit.won") && record(joined).tries() == 1,
                    "a spirit out of a bout dealt its wielder " + won + ", stayed out, said nothing or counted a try");

            MaterialisedSpirit lightSpirit = begun(helper, light, now).spirit();
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            for (LivingEntity victim : List.of(zombie, other)) {
                for (DamageSource source : List.of(sources.playerAttack(light), sources.arrow(arrow, light),
                        BankaiSpiritTests.getsuga(level, arrow, light))) {
                    helper.assertTrue(!victim.hurt(source, 5.0F) && victim.getHealth() == victim.getMaxHealth(),
                            "the wielder's " + source.getMsgId() + " landed on " + victim + " in a bout");
                }
            }
            for (DamageSource source : List.of(sources.playerAttack(other), sources.mobAttack(zombie))) {
                helper.assertTrue(!light.hurt(source, 5.0F) && light.getHealth() == light.getMaxHealth(),
                        "a blow of " + source.getMsgId() + " from " + source.getEntity() + " landed on a wielder in a bout");
            }
            helper.assertTrue(light.hurt(sources.fall(), 3.0F) && light.getHealth() < light.getMaxHealth(),
                    "a fall did not hurt a wielder in a bout");
            light.invulnerableTime = 0;
            float before = light.getHealth();
            light.hurt(sources.mobAttack(lightSpirit), 3.0F);
            helper.assertTrue(light.getHealth() < before && light.getHealth() > floor
                            && BankaiTrial.trial(light.getUUID()) != null && !lightSpirit.isRemoved(),
                    "its spirit's lighter blow left " + light.getHealth() + ", or ended the bout");
            Materialisation.sendHome(lightSpirit);
        });
        zombie.discard();
        helper.succeed();
    }

    /**
     * T4. The command begins a joined soul's bout, counting one, and passes over a Human, saying why. Its spirit beaten
     * to its floor yields on its tick: the soul reaches Bankai by the name the grant gives, its try kept, told so in gold
     * in chat with the key that calls it, and on its action bar as the spirit goes. A beaten spirit yields nothing to a
     * wielder gone into creative mode before the spirit's tick, or one whose power was lent after the blow, nor out of
     * a bout; and it strikes no more, so a blow on a wielder low enough to lose by it lands nothing, and it still yields.
     * A bout's bar counts it down by the second, and the time running out on a beaten spirit waits for it to yield.
     * The ring is at its smallest throughout, as the clock's tick a second in keeps others out.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void beatingTheSpiritAchievesAndNamesBankai(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DamageSources sources = level.damageSources();
        long now = HollowPlayerTests.now(helper);
        ServerPlayer ichigo = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true,
                "bleach_trial_t4");
        BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.HUMAN, 500.0F), false, "bleach_trial_t4");
        BankaiSpiritTests.stand(helper, ichigo, MIDDLE);
        if (refusedOnPeaceful(helper, ichigo, now)) {
            return;
        }
        ServerPlayer watcher = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 800.0F), true);
        BankaiSpiritTests.stand(helper, watcher, FLOOR);
        Player lent = BankaiSpiritTests.wielder(helper, FLOOR), free = BankaiSpiritTests.wielder(helper, FLOOR);
        Player low = BankaiSpiritTests.wielder(helper, FLOOR), late = BankaiSpiritTests.wielder(helper, MIDDLE);
        inSmallRing(() -> HihioZabimaruTests.clearingOnFailure(() -> clear(ichigo, watcher, lent, free, low, late), () -> {
            List<Component> said = GateTests.run(level, "bleach zanpakuto @a[tag=bleach_trial_t4] bankai trial");
            String count = "commands.bleach.zanpakuto.bankai.trial";
            BankaiTrial.Trial commanded = BankaiTrial.trial(ichigo.getUUID());
            helper.assertTrue(commanded != null && said.size() == 2
                            && said.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents words
                                    && words.getKey().equals(count) && Integer.valueOf(1).equals(words.getArgs()[0]))
                            && said.stream().anyMatch(line -> BankaiTests.refused(line, BankaiTrial.Start.NO_POWER.key())),
                    "the command said " + said.stream().map(Component::getString).toList());
            MaterialisedSpirit spirit = commanded.spirit();
            spirit.hurt(sources.playerAttack(ichigo), 10_000.0F);
            Materialisation.upkeep(spirit);
            BankaiRecord record = record(ichigo);
            Component name = ZanpakutoSpirits.get(level, ZanpakutoSpirits.ZANGETSU.location())
                    .flatMap(BankaiLogic::nameOf).orElseThrow();
            helper.assertTrue(record.achieved() && record.achievedAt() == now && record.name().equals(name)
                            && record.tries() == 1 && BankaiTrial.trial(ichigo.getUUID()) == null
                            && BankaiSpiritTests.gone(ichigo, spirit),
                    "beating the spirit left " + record + ", or the bout or the spirit");
            helper.assertTrue(TestPlayers.chat(ichigo).stream().anyMatch(line -> GateTests.says(line, WON, -1)
                            && TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(line.getStyle().getColor())
                            && line.getContents() instanceof TranslatableContents words && words.getArgs().length == 2
                            && words.getArgs()[0].equals(name)) && BankaiSpiritTests.told(ichigo, YIELDED),
                    "the soul was not told in gold that its spirit yielded its Bankai, or not as it went");

            MaterialisedSpirit watched = begun(helper, watcher, now).spirit();
            watched.hurt(sources.playerAttack(watcher), 10_000.0F);
            watcher.setGameMode(GameType.CREATIVE);
            Materialisation.upkeep(watched);
            helper.assertTrue(BankaiTrial.trial(watcher.getUUID()) == null && BankaiSpiritTests.gone(watcher, watched)
                            && !record(watcher).achieved() && BankaiSpiritTests.told(watcher, BROKEN),
                    "a beaten spirit yielded to a wielder gone into creative mode, or stayed");
            MaterialisedSpirit lentSpirit = begun(helper, lent, now).spirit();
            lentSpirit.hurt(sources.playerAttack(lent), 10_000.0F);
            SoulData lentSoul = SoulLogic.get(lent);
            lentSoul.setShinigami(lentSoul.shinigami().withPowerLentUntil(now + 1_000L));
            Materialisation.upkeep(lentSpirit);
            helper.assertTrue(over(lent, lentSpirit, now), "a beaten spirit yielded to a wielder whose power was lent");
            low.setHealth(3.0F);
            MaterialisedSpirit lowSpirit = begun(helper, low, now).spirit();
            lowSpirit.hurt(sources.playerAttack(low), 10_000.0F);
            helper.assertTrue(!low.hurt(sources.mobAttack(lowSpirit), 5.0F) && low.getHealth() == 3.0F
                            && BankaiTrial.trial(low.getUUID()) != null,
                    "a beaten spirit's blow landed on its wielder at 3, or lost the bout");
            Materialisation.upkeep(lowSpirit);
            helper.assertTrue(record(low).achieved() && BankaiTrial.trial(low.getUUID()) == null
                    && BankaiSpiritTests.gone(low, lowSpirit), "a beaten spirit that struck at its wielder did not yield");
            MaterialisedSpirit beaten = BankaiSpiritTests.drawOut(helper, free);
            beaten.hurt(sources.playerAttack(free), 10_000.0F);
            Materialisation.upkeep(beaten);
            helper.assertTrue(BankaiSpiritTests.gone(free, beaten) && !record(free).achieved(),
                    "a spirit beaten out of a bout gave its Bankai, or stayed out");

            BankaiTrial.Trial trial = begun(helper, late, now);
            String bar = "bossbar.bleach.zangetsu_spirit";
            helper.assertTrue(clock(trial.spirit(), bar, BankaiTrial.boutSeconds()),
                    "the spirit's bar reads " + trial.spirit().bar().event().getName() + " as the bout begins");
            BankaiTrial.tick(late, SoulLogic.get(late), now + 20L);
            helper.assertTrue(clock(trial.spirit(), bar, BankaiTrial.boutSeconds() - 1L),
                    "the spirit's bar reads " + trial.spirit().bar().event().getName() + " a second in");
            trial.spirit().hurt(sources.playerAttack(late), 10_000.0F);
            BankaiTrial.tick(late, SoulLogic.get(late), trial.endsAt() + 1L);
            helper.assertTrue(BankaiTrial.trial(late.getUUID()) == trial, "the time ran out on a beaten spirit");
            Materialisation.upkeep(trial.spirit());
            helper.assertTrue(record(late).achieved() && BankaiTrial.trial(late.getUUID()) == null,
                    "a spirit beaten as the time ran out did not yield");
        }));
        helper.succeed();
    }

    /**
     * T5. No bout begins while its wielder faces its inner Hollow, and nothing is counted; a fight begun in a bout
     * sends the spirit home on its tick, which ends the bout with nothing written but the try.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void aVisoredDuelRefusesAndBreaksOffATrial(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player host = BankaiSpiritTests.host(helper);
        SoulData data = SoulLogic.get(host);
        BankaiSpiritTests.ready(data, now);
        if (refusedOnPeaceful(helper, host, now)) {
            return;
        }
        try {
            VisoredEvents.DuelStart start = VisoredEvents.beginDuel(host, data, now, false);
            BankaiTrial.Start refused = BankaiTrial.begin(host, now);
            helper.assertTrue(start == VisoredEvents.DuelStart.STARTED && refused == BankaiTrial.Start.BUSY
                            && BankaiTrial.trial(host.getUUID()) == null && record(host).tries() == 0,
                    "the fight came to " + start + ", and a bout in it to " + refused);
            BankaiSpiritTests.end(now, host);
            MaterialisedSpirit spirit = begun(helper, host, now).spirit();
            BankaiSpiritTests.ready(data, now);
            start = VisoredEvents.beginDuel(host, data, now, false);
            Materialisation.upkeep(spirit);
            helper.assertTrue(start == VisoredEvents.DuelStart.STARTED && over(host, spirit, now),
                    "a bout went on as its wielder's fight with its inner Hollow came to " + start);
        } finally {
            BankaiSpiritTests.end(now, host);
            clear(host);
        }
        helper.succeed();
    }

    /**
     * T6. No two rings meet: a bout is refused within twice the ring and 2 blocks of another, here at its smallest,
     * and begins farther off; and within the two rings and 2 of a soul's fight with its inner Hollow, found by its host
     * among the server's players, with nothing counted, while the host itself is told it is busy. No inner Hollow comes
     * out for a soul in a bout, or as near one: Jinzen opens the inner world instead, and the forced command says why.
     * Where no config is set the ring is 12 blocks.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void twoRingsMayNotMeet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.host(helper);
        SoulData data = SoulLogic.get(ichigo);
        if (refusedOnPeaceful(helper, ichigo, now)) {
            return;
        }
        BankaiSpiritTests.stand(helper, ichigo, FLOOR);
        Player beside = BankaiSpiritTests.wielder(helper, MIDDLE), far = farWielder(helper);
        ServerPlayer host = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 900.0F), true,
                "bleach_trial_t6");
        BankaiSpiritTests.stand(helper, host, FAR);
        SoulData hostData = SoulLogic.get(host);
        BankaiSpiritTests.ready(hostData, now);
        BankaiSpiritTests.ready(data, now);
        int radius = BankaiTrial.ringRadius();
        Runnable clear = () -> {
            clear(ichigo, beside, far);
            BankaiSpiritTests.end(now, host);
            BankaiSpiritTests.end(now, ichigo);
        };
        inSmallRing(() -> HihioZabimaruTests.clearingOnFailure(clear, () -> {
            helper.assertTrue(radius == 12, "the ring is " + radius + " blocks where no config is set");
            // At 6 blocks, two bouts meet within 14 of each other, and a bout and a fight within 18.
            MaterialisedSpirit spirit = begun(helper, ichigo, now).spirit();
            BankaiTrial.Start near = BankaiTrial.begin(beside, now), apart = BankaiTrial.begin(far, now);
            clear(far);
            helper.assertTrue(near == BankaiTrial.Start.CROWDED && record(beside).tries() == 0
                            && !Materialisation.holds(beside.getUUID()) && apart == BankaiTrial.Start.STARTED,
                    "a bout 9.9 blocks from another came to " + near + ", and one 18.4 off to " + apart);
            helper.assertTrue("message.bleach.bankai.trial.duel_in_trial".equals(BankaiTrial.duelRefusal(ichigo))
                            && NEAR.equals(BankaiTrial.duelRefusal(beside)) && BankaiTrial.duelRefusal(host) == null,
                    "a soul in a bout, or 9.9 blocks from one, may bring its inner Hollow out, or one 18.4 off may not");
            BankaiSpiritTests.stand(helper, host, MIDDLE);
            List<Component> said = GateTests.run(level, "bleach visored @a[tag=bleach_trial_t6] duel");
            helper.assertTrue(said.size() == 1 && BankaiTests.refused(said.get(0), NEAR) && !VisoredEvents.isDuelling(host),
                    "the forced fight beside a bout said " + said.stream().map(Component::getString).toList());
            ichigo.setShiftKeyDown(true);
            ichigo.setOnGround(true);
            JinzenLogic.begin(ichigo, data, now);
            JinzenLogic.tick(ichigo, data, ichigo.getMainHandItem(), JinzenLogic.SETTLE_TICKS, now);
            helper.assertTrue(!VisoredEvents.isDuelling(ichigo) && BankaiTrial.trial(ichigo.getUUID()) != null
                            && data.jinzen() != null && data.jinzen().opened(),
                    "Jinzen in a bout brought the ready Hollow out, or opened no inner world");
            Materialisation.sendHome(spirit);

            VisoredEvents.DuelStart duel = VisoredEvents.beginDuel(host, hostData, now, true);
            BankaiTrial.Start start = BankaiTrial.begin(ichigo, now), own = BankaiTrial.begin(host, now);
            helper.assertTrue(duel == VisoredEvents.DuelStart.STARTED && start == BankaiTrial.Start.CROWDED
                            && own == BankaiTrial.Start.BUSY && record(ichigo).tries() == 1 && record(host).tries() == 0,
                    "the fight came to " + duel + ", a bout 9.9 blocks off to " + start + " and one in it to " + own);
        }));
        clear.run();
        helper.succeed();
    }

    /**
     * T7. The ring keeps the two in and everyone else out, here at its smallest, 6 blocks: the two inside it are let be;
     * a creature inside is pushed out, unless the push would drop it off a pillar, and drops its grudge against the
     * wielder; a joined soul inside is pushed out and told why, and one in creative mode is let be; a mounted wielder is
     * taken off; and the wielder and its spirit past the ring are pushed back in.
     */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void theRingKeepsTheTwoInAndOthersOut(GameTestHelper helper) {
        long now = HollowPlayerTests.now(helper);
        Player ichigo = BankaiSpiritTests.wielder(helper, MIDDLE);
        SoulData data = SoulLogic.get(ichigo);
        if (refusedOnPeaceful(helper, ichigo, now)) {
            return;
        }
        BlockPos[] pillar = {new BlockPos(12, 2, 8), new BlockPos(12, 3, 8), new BlockPos(12, 4, 8)};
        for (BlockPos block : pillar) {
            helper.setBlock(block, Blocks.STONE);
        }
        Zombie inside = HollowPlayerTests.placed(helper, EntityType.ZOMBIE, 8, 4);
        Zombie perched = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(12.5, 5.0, 8.5)));
        ServerPlayer bystander = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), false);
        BankaiSpiritTests.stand(helper, bystander, new Vec3(4.5, 2.0, 8.5));
        ServerPlayer watching = BankaiTests.join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), false);
        watching.setGameMode(GameType.CREATIVE);
        BankaiSpiritTests.stand(helper, watching, new Vec3(4.5, 2.0, 4.5));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(15.5, 2.0, 8.5));
        Runnable clear = () -> {
            clear(ichigo);
            inside.discard();
            perched.discard();
            boat.discard();
            for (BlockPos block : pillar) {
                helper.setBlock(block, Blocks.AIR);
            }
        };
        inSmallRing(() -> HihioZabimaruTests.clearingOnFailure(clear, () -> {
            inside.setTarget(ichigo);
            BankaiTrial.Trial trial = begun(helper, ichigo, now);
            MaterialisedSpirit spirit = trial.spirit();
            BankaiTrial.tick(ichigo, data, now);
            helper.assertTrue(inside.getDeltaMovement().z < -0.5 && inside.getTarget() == null
                            && perched.getDeltaMovement().horizontalDistanceSqr() == 0.0
                            && ichigo.getDeltaMovement().horizontalDistanceSqr() == 0.0
                            && spirit.getDeltaMovement().horizontalDistanceSqr() == 0.0,
                    "the ring pushed the creature inside by " + inside.getDeltaMovement() + ", the one on the pillar by "
                            + perched.getDeltaMovement() + ", the wielder by " + ichigo.getDeltaMovement()
                            + " and its spirit by " + spirit.getDeltaMovement() + ", or the one inside kept its grudge");
            helper.assertTrue(bystander.getDeltaMovement().x < -0.5 && BankaiSpiritTests.told(bystander, BARRIER)
                            && watching.getDeltaMovement().horizontalDistanceSqr() == 0.0
                            && !BankaiSpiritTests.told(watching, BARRIER),
                    "the ring pushed the soul inside by " + bystander.getDeltaMovement() + " and the one in creative mode by "
                            + watching.getDeltaMovement() + ", or told the wrong one why");
            helper.assertTrue(ichigo.startRiding(boat, true), "the wielder could not get into the boat");
            BankaiTrial.tick(ichigo, data, now + 1L);
            helper.assertTrue(!ichigo.isPassenger(), "the wielder rode on in its bout");
            Vec3 past = helper.absoluteVec(new Vec3(15.5, 2.0, 8.5));
            ichigo.teleportTo(past.x, past.y, past.z);
            ichigo.setDeltaMovement(Vec3.ZERO);
            spirit.setPos(trial.centre().add(0.0, 0.0, 7.0));
            BankaiTrial.tick(ichigo, data, now + 2L);
            helper.assertTrue(ichigo.getDeltaMovement().x < -0.5 && spirit.getDeltaMovement().z < -0.5
                            && BankaiTrial.trial(ichigo.getUUID()) == trial,
                    "past the ring, the wielder was pushed by " + ichigo.getDeltaMovement() + " and its spirit by "
                            + spirit.getDeltaMovement());
        }));
        clear.run();
        helper.succeed();
    }
}
