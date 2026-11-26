package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.Gillian;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BankaiForms;
import io.github.masteryoga1.bleach.soul.HikotsuTaiho;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.BankaiLook;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Hihiō Zabimaru, Zabimaru's Bankai, whose art is the serpent. The numbers are the server config's defaults:
 * a reach of 15 blocks (the base 3, Shikai's 3 and the serpent's 9), drawn back to 3 for a second after each lash.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class HihioZabimaruTests {
    private static final double EPSILON = 1.0E-3;

    private HihioZabimaruTests() {
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    private static double reach(Player player) {
        return player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    /** The serpent's own reach on the player, or NaN without it. */
    private static double serpentReach(Player player) {
        AttributeModifier modifier = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE)
                .getModifier(BankaiForms.SERPENT_REACH_ID);
        return modifier == null ? Double.NaN : modifier.amount();
    }

    /** The drawn-back reach on the player, or NaN without it. */
    private static double retract(Player player) {
        AttributeModifier modifier =
                player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getModifier(ShikaiEvents.RETRACT_ID);
        return modifier == null ? Double.NaN : modifier.amount();
    }

    /** The one lash within a block of this point, or null for none or more than one. */
    private static ZabimaruLash lashAt(GameTestHelper helper, Vec3 at) {
        List<ZabimaruLash> lashes = helper.getLevel().getEntitiesOfClass(ZabimaruLash.class, AABB.ofSize(at, 2.0, 2.0, 2.0));
        return lashes.size() == 1 ? lashes.get(0) : null;
    }

    /** This owner's one serpent within two blocks of this point, or null for none or more than one. */
    private static ZabimaruLash serpentAt(GameTestHelper helper, Player owner, Vec3 at) {
        List<ZabimaruLash> serpents = helper.getLevel().getEntitiesOfClass(ZabimaruLash.class,
                AABB.ofSize(at, 4.0, 4.0, 4.0), lash -> lash.isSerpent() && lash.owner() == owner);
        return serpents.size() == 1 ? serpents.get(0) : null;
    }

    /**
     * The one fired beam drawn from this very point, or null for none or more than one. Hikotsu Taihō's is drawn as
     * no one's, so it is known by where it starts.
     */
    private static CeroBeam beamFrom(GameTestHelper helper, Vec3 from) {
        List<CeroBeam> beams = helper.getLevel().getEntitiesOfClass(CeroBeam.class, AABB.ofSize(from, 4.0, 4.0, 4.0),
                beam -> beam.isFired() && beam.position().closerThan(from, 0.01));
        return beams.size() == 1 ? beams.get(0) : null;
    }

    /** A still mob hung here, at relative coordinates, as no gravity or will moves it. */
    private static <T extends Mob> T hung(GameTestHelper helper, EntityType<T> type, Vec3 at) {
        T mob = QuincyTests.still(helper.spawnWithNoFreeWill(type, at));
        mob.setNoGravity(true);
        return mob;
    }

    /**
     * Zabimaru's Shikai reaches 6 blocks and Hihiō Zabimaru 15, the serpent's 9 riding its owner and never the blade.
     * A blow nine blocks off brings out the serpent, out its own 12 ticks and not Shikai's 8; its reach draws back to 3
     * at once and it lashes no more until it is back, a second later, when the 15 blocks are back too. Zabimaru's Shikai
     * lash is no serpent, and Tensa Zangetsu has no serpent's reach. The blade's item model draws model 5 as Hihiō
     * Zabimaru, its last override, and its model and texture are shipped.
     */
    @GameTest(template = "field", timeoutTicks = 400)
    public static void hihioZabimaruReachesFarAndDrawsBack(GameTestHelper helper) {
        ServerPlayer renji = BankaiReleaseTests.joined(helper, ZanpakutoSpirits.ZABIMARU, new Vec3(2.5, 2.0, 8.5));
        ItemStack blade = renji.getMainHandItem();
        Pig far = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 11, 2, 8));
        Vec3 struck = far.getBoundingBox().getCenter();
        BankaiReleaseTests.released(helper, renji, ShikaiEvents.Result.RELEASED, "Shikai");
        int[] from = {renji.tickCount};
        ZabimaruLash[] serpent = new ZabimaruLash[1];
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, renji.tickCount >= from[0] + 2 && near(reach(renji), 6.0),
                        "in Shikai Zabimaru reaches " + reach(renji)))
                .thenExecute(() -> {
                    helper.assertTrue(Double.isNaN(serpentReach(renji)), "Zabimaru's Shikai has the serpent's reach");
                    renji.tickCount += ShikaiEvents.SETTLE_TICKS;
                    BankaiReleaseTests.released(helper, renji, ShikaiEvents.Result.BANKAI, "Hihiō Zabimaru");
                    helper.assertTrue(ZanpakutoTests.model(blade) == 5 && near(serpentReach(renji), 9.0)
                                    && Double.isNaN(BankaiReleaseTests.modifier(blade, Attributes.ENTITY_INTERACTION_RANGE,
                                    BankaiForms.SERPENT_REACH_ID)),
                            "Hihiō Zabimaru has model " + ZanpakutoTests.model(blade) + " and its owner a serpent's reach"
                                    + " of " + serpentReach(renji));
                    from[0] = renji.tickCount;
                })
                .thenWaitUntil(() -> GateTests.patiently(helper, renji.tickCount >= from[0] + 2 && near(reach(renji), 15.0),
                        "in Hihiō Zabimaru its owner reaches " + reach(renji)))
                .thenExecute(() -> {
                    helper.assertTrue(ShikaiEvents.lash(renji, far, blade), "a blow nine blocks off brought out no serpent");
                    serpent[0] = lashAt(helper, struck);
                    helper.assertTrue(serpent[0] != null && serpent[0].isSerpent() && serpent[0].owner() == renji
                                    && serpent[0].lifeTicks() == 12,
                            "the lash nine blocks off is " + serpent[0]);
                    helper.assertTrue(near(reach(renji), 3.0) && Double.isNaN(serpentReach(renji))
                                    && near(retract(renji), -3.0),
                            "as the serpent draws back its owner reaches " + reach(renji) + ", the serpent's own "
                                    + serpentReach(renji) + " and drawn back " + retract(renji));
                    helper.assertFalse(ShikaiEvents.lash(renji, far, blade), "the serpent lashed again as it drew back");
                })
                // Still out at 10 ticks, past Shikai's 8, and called in at 12: a removed lash stops counting.
                .thenWaitUntil(() -> GateTests.patiently(helper, serpent[0].age() >= 10 || serpent[0].isRemoved(),
                        "the serpent has been out only " + serpent[0].age() + " ticks"))
                .thenExecute(() -> helper.assertFalse(serpent[0].isRemoved(),
                        "the serpent was called in at age " + serpent[0].age()))
                .thenWaitUntil(() -> GateTests.patiently(helper, serpent[0].isRemoved(),
                        "the serpent is still out at age " + serpent[0].age()))
                .thenExecute(() -> helper.assertTrue(serpent[0].age() == 12,
                        "the serpent was out " + serpent[0].age() + " ticks, not 12"))
                .thenWaitUntil(() -> GateTests.patiently(helper, near(reach(renji), 15.0) && Double.isNaN(retract(renji)),
                        "a second after its lash Hihiō Zabimaru's owner reaches " + reach(renji)))
                .thenExecute(() -> {
                    Player shikai = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
                    BankaiReleaseTests.released(helper, shikai, ShikaiEvents.Result.RELEASED, "Zabimaru's Shikai");
                    shikai.setPos(helper.absoluteVec(new Vec3(2.5, 2.0, 4.5)));
                    Pig other = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, 9, 2, 4));
                    helper.assertTrue(ShikaiEvents.lash(shikai, other, shikai.getMainHandItem()),
                            "Zabimaru's Shikai did not lash");
                    ZabimaruLash lash = lashAt(helper, other.getBoundingBox().getCenter());
                    helper.assertTrue(lash != null && !lash.isSerpent() && lash.lifeTicks() == ZabimaruLash.LIFE_TICKS,
                            "Zabimaru's Shikai lashed out " + lash);
                    Player ichigo = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZANGETSU);
                    BankaiReleaseTests.toBankai(helper, ichigo);
                    helper.assertTrue(Double.isNaN(serpentReach(ichigo)), "Tensa Zangetsu gave its owner a serpent's reach");

                    ZanpakutoSpirit zabimaru = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU);
                    int hihio = ZanpakutoLogic.form(zabimaru, ZanpakutoStage.BANKAI).model();
                    String asauchi =
                            new String(BankaiFormTests.asset(helper, "models/item/asauchi.json"), StandardCharsets.UTF_8);
                    JsonArray overrides = JsonParser.parseString(asauchi).getAsJsonObject().getAsJsonArray("overrides");
                    JsonObject last = overrides.get(overrides.size() - 1).getAsJsonObject();
                    int model = last.getAsJsonObject("predicate").get("custom_model_data").getAsInt();
                    helper.assertTrue(hihio == 5 && model == hihio
                                    && "bleach:item/hihio_zabimaru".equals(last.get("model").getAsString()),
                            "Zabimaru's Bankai has model " + hihio + " and the Asauchi's last override is " + last);
                    BankaiFormTests.asset(helper, "models/item/hihio_zabimaru.json");
                    BankaiFormTests.asset(helper, "textures/item/hihio_zabimaru.png");
                    lash.discard();
                    other.discard();
                    far.discard();
                    helper.succeed();
                });
    }

    /**
     * Every end of Hihiō Zabimaru takes the serpent's reach off at once and calls its serpent in by its next tick, long
     * before its 12 ticks are out: the seal key, the Bankai ended as a revoke ends it, death, logging out, and the blade
     * put out of hand. Each end is tried on a soul that has not lashed, whose reach is the serpent's, and on one whose
     * serpent is out, which strikes nothing it would have passed: neither its target nor a pig beside its line. Ended to
     * Shikai, the blade is in Shikai and its reach still drawn back, as Shikai's would be.
     */
    @GameTest(template = "field", timeoutTicks = 40)
    public static void theSerpentGoesWithEveryEnd(GameTestHelper helper) {
        int ends = 5;
        ZabimaruLash[] serpents = new ZabimaruLash[ends];
        Pig[] pigs = new Pig[ends];
        Pig[] beside = new Pig[ends];
        for (int end = 0; end < ends; end++) {
            double z = 1.5 + 2.5 * end;
            Player reaching = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
            BankaiReleaseTests.toBankai(helper, reaching);
            helper.assertTrue(near(serpentReach(reaching), 9.0), "called on a mock, the serpent's reach did not come on");
            String how = end(helper, reaching, end);
            helper.assertTrue(Double.isNaN(serpentReach(reaching))
                            && !BankaiForms.serpentine(reaching, SoulLogic.get(reaching)),
                    how + " left the serpent's reach of " + serpentReach(reaching) + " on its owner");

            Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
            BankaiReleaseTests.toBankai(helper, renji);
            renji.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, z)));
            ItemStack blade = renji.getMainHandItem();
            pigs[end] = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(12.5, 2.0, z)));
            beside[end] = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(7.5, 2.0, z + 0.5)));
            helper.assertTrue(ShikaiEvents.lash(renji, pigs[end], blade), "Hihiō Zabimaru did not lash before " + how);
            serpents[end] = lashAt(helper, pigs[end].getBoundingBox().getCenter());
            helper.assertTrue(serpents[end] != null && serpents[end].isSerpent(), "no serpent lashed out before " + how);
            end(helper, renji, end);
            helper.assertTrue(end != 1
                            || BankaiReleaseTests.stage(blade) == ZanpakutoStage.SHIKAI && near(retract(renji), -3.0),
                    "ended as a revoke ends it, the blade is " + BankaiReleaseTests.stage(blade) + " and drawn back "
                            + retract(renji));
        }
        helper.succeedWhen(() -> {
            for (int end = 0; end < ends; end++) {
                helper.assertTrue(serpents[end].isRemoved() && serpents[end].age() < ZabimaruLash.SERPENT_LIFE_TICKS,
                        "after end " + end + " the serpent is " + (serpents[end].isRemoved() ? "called in" : "still out")
                                + " at age " + serpents[end].age());
                helper.assertTrue(near(pigs[end].getHealth(), pigs[end].getMaxHealth())
                                && near(beside[end].getHealth(), beside[end].getMaxHealth()),
                        "the serpent called in by end " + end + " struck a pig it would have passed");
            }
            for (int end = 0; end < ends; end++) {
                pigs[end].discard();
                beside[end].discard();
            }
        });
    }

    /** Ends this soul's Hihiō Zabimaru the way numbered, and names it. */
    private static String end(GameTestHelper helper, Player renji, int end) {
        SoulData data = SoulLogic.get(renji);
        return switch (end) {
            case 0 -> {
                helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED, "the seal key did not seal");
                yield "the seal key";
            }
            case 1 -> {
                ShikaiEvents.endBankai(renji, data, ShikaiEvents.BANKAI_ENDED);
                yield "the Bankai ended as a revoke ends it";
            }
            case 2 -> {
                // At no health, as a player is when its death is posted.
                renji.setHealth(0.0F);
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(renji, renji.damageSources().generic()));
                yield "death";
            }
            case 3 -> {
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(renji));
                yield "logging out";
            }
            default -> {
                renji.getInventory().setItem((renji.getInventory().selected + 1) % 9, renji.getMainHandItem());
                renji.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                // A mock never ticks: its next tick would do this.
                BankaiForms.keep(renji, data);
                yield "the blade out of hand";
            }
        };
    }

    /**
     * Hihiō Zabimaru's pelt is worn by its owner with it in his main hand in Bankai, and by nobody else: not by another
     * soul with a copy of it in hand, and not once it is sealed. The pelt goes with the model Zabimaru's file gives its
     * Bankai, and its texture and the serpent's bone segment are shipped. Every client draws the pelt by this same
     * reading of the held stack.
     */
    @GameTest(template = "empty")
    public static void thePeltIsWornOnlyWithOwnBladeInBankai(GameTestHelper helper) {
        Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
        ItemStack blade = renji.getMainHandItem();
        BankaiReleaseTests.toBankai(helper, renji);
        helper.assertTrue(BankaiLook.worn(renji) == BankaiLook.HIHIO_ZABIMARU,
                "in Hihiō Zabimaru its owner wears " + BankaiLook.worn(renji));
        Player other = ZanpakutoTests.mock(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F));
        other.setItemInHand(InteractionHand.MAIN_HAND, blade.copy());
        helper.assertTrue(BankaiLook.worn(other) == BankaiLook.NONE,
                "another soul with Hihiō Zabimaru in hand wears " + BankaiLook.worn(other));
        ZanpakutoSpirit zabimaru = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU);
        int hihio = ZanpakutoLogic.form(zabimaru, ZanpakutoStage.BANKAI).model();
        helper.assertTrue(BankaiLook.HIHIO_ZABIMARU.model() == hihio && ZanpakutoTests.model(blade) == hihio
                        && BankaiLook.forModel(hihio) == BankaiLook.HIHIO_ZABIMARU,
                "the pelt goes with model " + BankaiLook.HIHIO_ZABIMARU.model() + " and Zabimaru's file gives " + hihio);
        BankaiFormTests.asset(helper, "textures/entity/hihio_zabimaru_pelt.png");
        BankaiFormTests.asset(helper, "textures/entity/hihio_zabimaru_segment.png");
        helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED && BankaiLook.worn(renji) == BankaiLook.NONE,
                "sealed, its owner wears " + BankaiLook.worn(renji));
        helper.succeed();
    }

    /**
     * The serpent's passage to a blow 13 blocks off strikes, once, all it passes as a Cero would, for half Hihiō
     * Zabimaru's blade damage, 6: the blow's own target, a pig beside its line, and a Gillian whose head it passes
     * through but whose own body it comes nowhere near, by the head's part; a pig two blocks off its line is untouched.
     * A real blow 11 blocks off, through the event every blow posts, draws one serpent and no more, and what it struck
     * takes the larger of the blow and the passage, not both. Walls, shields and wards are {@link CeroBeam#fire}'s own,
     * pinned by its tests: this pins the passage's way to it.
     */
    @GameTest(template = "field", skyAccess = true, timeoutTicks = 40)
    public static void theSerpentStrikesAllItPasses(GameTestHelper helper) {
        Gillian gillian = QuincyTests.still(helper.spawnWithNoFreeWill(ModEntities.GILLIAN.get(), 8, 2, 8));
        gillian.setYRot(0.0F);
        gillian.setYHeadRot(0.0F);
        gillian.setYBodyRot(0.0F);
        // The parts are placed in aiStep, which runs for a NoAI mob too, so after one tick.
        helper.runAfterDelay(1, () -> {
            AABB head = gillian.head().getBoundingBox();
            AABB body = gillian.getBoundingBox();
            // 0.3 inside the head's face. The head reaches 1.6 + 1.6 past the Gillian's middle (the mask's 1.6 forward
            // and half the 3.2 head) and its own box 1.7 (half its 3.4), so the line runs 1.6 + 1.6 - 0.3 - 1.7 = 1.2
            // clear of that box, against the 1.0 asked here: the passage's 0.8 and 0.2 to spare.
            Vec3 line = helper.relativeVec(new Vec3(head.getCenter().x, head.getCenter().y, head.maxZ - 0.3));
            helper.assertTrue(head.maxZ - 0.3 - body.maxZ > ZabimaruLash.SWEEP_RADIUS + 0.2,
                    "the line through the Gillian's head part comes within reach of its own box");
            Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
            BankaiReleaseTests.toBankai(helper, renji);
            // The serpent leaves 0.4 below the eyes. The soul and these pigs hang at the head's height, about 15.5,
            // above the 12-high template and under open sky.
            renji.setPos(helper.absoluteVec(new Vec3(1.0, line.y - renji.getEyeHeight() + 0.4, line.z)));
            Pig target = hung(helper, EntityType.PIG, new Vec3(14.5, line.y - 0.45, line.z));
            Pig beside = hung(helper, EntityType.PIG, new Vec3(4.5, line.y - 0.45, line.z + 0.9));
            Pig apart = hung(helper, EntityType.PIG, new Vec3(3.5, line.y - 0.45, line.z + 2.0));
            float whole = gillian.getHealth();
            helper.assertTrue(ShikaiEvents.lash(renji, target, renji.getMainHandItem()), "a blow 13 blocks off did not lash");
            ZabimaruLash serpent = serpentAt(helper, renji, target.getBoundingBox().getCenter());

            // A real blow, on the ground in a lane of its own. A mock never ticks, so it strikes uncharged and with
            // none of the blade's damage, far below the passage's.
            Player striker = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
            BankaiReleaseTests.toBankai(helper, striker);
            striker.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, 2.5)));
            Pig far = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(12.5, 2.0, 2.5)));
            striker.attack(far);
            float blow = far.getMaxHealth() - far.getHealth();
            List<ZabimaruLash> drawn = helper.getLevel().getEntitiesOfClass(ZabimaruLash.class,
                    new AABB(striker.position(), far.position()).inflate(2.0), lash -> lash.owner() == striker);
            helper.assertTrue(blow > EPSILON && drawn.size() == 1 && drawn.get(0).isSerpent(),
                    "a blow 11 blocks off dealt " + blow + " and drew " + drawn);
            // Called in at 12 ticks: a second strike would land by 11, once the first one's hurt cooldown is out, so a
            // pig still at 4.0 then was struck the once.
            helper.succeedWhen(() -> {
                helper.assertTrue(serpent != null && serpent.isRemoved() && drawn.get(0).isRemoved(),
                        "a serpent is still out");
                helper.assertTrue(near(target.getHealth(), 4.0) && near(beside.getHealth(), 4.0)
                                && near(apart.getHealth(), 10.0),
                        "the serpent left its target at " + target.getHealth() + ", the pig beside its line at "
                                + beside.getHealth() + " and the one apart at " + apart.getHealth());
                helper.assertTrue(gillian.getHealth() < whole, "the serpent passed through the Gillian's head unfelt");
                float lost = far.getMaxHealth() - far.getHealth();
                helper.assertTrue(near(lost, Math.max(blow, 6.0F)),
                        "the pig the blow struck lost " + lost + ", not the larger of the blow's " + blow + " and 6");
                target.discard();
                beside.discard();
                apart.discard();
                far.discard();
                gillian.discard();
            });
        });
    }

    /**
     * Hikotsu Taihō is gathered on the technique key only in Hihiō Zabimaru: in Zabimaru's Shikai there is no such
     * technique. Gathered and let go at once, it costs a tapped Getsuga Tenshō's share, starts the Getsuga's cooldown,
     * lashes the serpent out to three blocks before its wielder's eyes and goes on from there as a beam in the blade's
     * colour: a golem nine blocks off takes a tap's damage, half again a named Getsuga Tenshō's, and a pig a block before
     * its eyes, short of the serpent's head, none. Gathered in full once the cooldown is out, it costs a full Getsuga
     * Tenshō's share and deals twice a tap's. Behind a Visored's mask it is half again as strong. The mask's roll for
     * the Hollow to take the body as it fires is the Getsuga Tenshō's own code, and not tried here.
     */
    @GameTest(template = "field", timeoutTicks = 100)
    public static void hikotsuTaihoBlastsFromTheSerpentsHead(GameTestHelper helper) {
        Player renji = BankaiReleaseTests.reached(helper, ZanpakutoSpirits.ZABIMARU);
        SoulData data = SoulLogic.get(renji);
        renji.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, 8.5)));
        renji.setYRot(-90.0F);
        renji.setYHeadRot(-90.0F);
        renji.setXRot(0.0F);
        BankaiReleaseTests.released(helper, renji, ShikaiEvents.Result.RELEASED, "Shikai");
        ShikaiEvents.GatherResult inShikai = ShikaiEvents.gather(renji);
        helper.assertTrue(inShikai == ShikaiEvents.GatherResult.NO_TECHNIQUE, "Zabimaru's Shikai gathered for " + inShikai);
        renji.tickCount += ShikaiEvents.SETTLE_TICKS;
        BankaiReleaseTests.released(helper, renji, ShikaiEvents.Result.BANKAI, "Hihiō Zabimaru");
        // A named Getsuga Tenshō's base, half again: 0.6 of it at a tap, 1.2 at a full charge.
        double base = Math.min(60.0, 8.0 + 0.8 * Math.sqrt(data.maxReiryoku()));
        double tap = base * 0.6 * 1.5;
        double full = base * 1.2 * 1.5;
        float tapped = ZanpakutoLogic.hikotsuTaihoDamage(data, 0.0F);
        float gathered = ZanpakutoLogic.hikotsuTaihoDamage(data, 1.0F);
        helper.assertTrue(near(tapped, tap) && near(gathered, 2.0 * tapped) && near(gathered, full),
                "Hikotsu Taihō deals " + tapped + " at a tap and " + gathered + " in full, not " + tap + " and " + full);

        double eyes = helper.relativeVec(renji.getEyePosition()).y;
        Pig close = hung(helper, EntityType.PIG, new Vec3(2.5, eyes - 0.45, 8.5));
        IronGolem first = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(10.5, 2.0, 8.5)));
        IronGolem second = QuincyTests.still(helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(10.5, 2.0, 3.5)));
        // Each blast bursts on a stone in its own lane and goes no further.
        BlockPos[] stones = {new BlockPos(14, (int) Math.floor(eyes), 8), new BlockPos(14, (int) Math.floor(eyes), 3)};
        for (BlockPos stone : stones) {
            helper.setBlock(stone, Blocks.STONE);
        }
        // A stone left by a red test would stand in the next test's cell, so a failed step clears them first.
        Runnable unstone = () -> {
            for (BlockPos stone : stones) {
                helper.setBlock(stone, Blocks.AIR);
            }
        };
        long tappedAt;
        try {
            int colour = ZanpakutoItem.blade(renji.getMainHandItem()).colour() & 0xFFFFFF;
            float before = data.reiryoku();
            helper.assertTrue(ShikaiEvents.gather(renji) == ShikaiEvents.GatherResult.GATHERING,
                    "Hihiō Zabimaru did not gather");
            helper.assertTrue(ShikaiEvents.fire(renji) == null, "Hikotsu Taihō loosed a Getsuga Tenshō");
            tappedAt = HollowPlayerTests.now(helper);
            float dealt = first.getMaxHealth() - first.getHealth();
            helper.assertTrue(near(before - data.reiryoku(), ZanpakutoLogic.getsugaCost(data, 0.0F)) && near(dealt, tap)
                            && near(close.getHealth(), close.getMaxHealth()),
                    "a tapped Hikotsu Taihō cost " + (before - data.reiryoku()) + " and dealt the golem " + dealt + ", not "
                            + tap + ", and the pig short of the serpent's head "
                            + (close.getMaxHealth() - close.getHealth()));
            Vec3 head = HikotsuTaiho.head(renji);
            ZabimaruLash serpent = serpentAt(helper, renji, head);
            CeroBeam beam = beamFrom(helper, head);
            helper.assertTrue(serpent != null && serpent.position().closerThan(head, 0.01) && beam != null
                            && beam.colour() == colour,
                    "Hikotsu Taihō's serpent is " + serpent + " and its beam " + beam);
            ShikaiEvents.GatherResult again = ShikaiEvents.gather(renji);
            helper.assertTrue(again == ShikaiEvents.GatherResult.COOLDOWN,
                    "Hikotsu Taihō at once again gathered for " + again);
            serpent.discard();
            beam.discard();
        } catch (RuntimeException failure) {
            unstone.run();
            throw failure;
        }

        // In full, in a second lane, through the same key: a mock never ticks, so a gather never fades.
        long[] gatheredAt = {0L};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(HollowPlayerTests.now(helper)
                                >= tappedAt + BleachConfig.get(BleachConfig.GETSUGA_COOLDOWN_TICKS),
                        "the cooldown is not out"))
                .thenExecute(() -> clearingOnFailure(unstone, () -> {
                    renji.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
                    ShikaiEvents.GatherResult result = ShikaiEvents.gather(renji);
                    helper.assertTrue(result == ShikaiEvents.GatherResult.GATHERING,
                            "after its cooldown Hikotsu Taihō gathered for " + result);
                    gatheredAt[0] = HollowPlayerTests.now(helper);
                }))
                .thenWaitUntil(() -> helper.assertTrue(HollowPlayerTests.now(helper)
                                >= gatheredAt[0] + BleachConfig.get(BleachConfig.GETSUGA_FULL_CHARGE_TICKS),
                        "not yet gathered in full"))
                .thenExecute(() -> clearingOnFailure(unstone, () -> {
                    float held = data.reiryoku();
                    helper.assertTrue(ShikaiEvents.fire(renji) == null, "Hikotsu Taihō in full loosed a Getsuga Tenshō");
                    float lost = second.getMaxHealth() - second.getHealth();
                    helper.assertTrue(near(held - data.reiryoku(), ZanpakutoLogic.getsugaCost(data, 1.0F))
                                    && near(lost, full),
                            "Hikotsu Taihō in full cost " + (held - data.reiryoku()) + " and dealt the golem " + lost
                                    + ", not " + full);
                    Vec3 jaws = HikotsuTaiho.head(renji);
                    ZabimaruLash lashed = serpentAt(helper, renji, jaws);
                    CeroBeam blast = beamFrom(helper, jaws);
                    helper.assertTrue(lashed != null && blast != null,
                            "Hikotsu Taihō in full lashed " + lashed + " and loosed " + blast);
                    lashed.discard();
                    blast.discard();

                    // Behind the mask, put on as the command puts it, and taken off again.
                    long now = HollowPlayerTests.now(helper);
                    VisoredLogic.setRecord(data,
                            VisoredLogic.record(data).withRootedAt(now - 24_000L).withSubduedAt(now - 1L));
                    helper.assertTrue(VisoredEvents.putOnMask(renji, data, now, true) == VisoredEvents.MaskStart.PUT_ON,
                            "the Visored's mask did not go on");
                    float factor = VisoredLogic.getsugaFactor(data);
                    float masked = ZanpakutoLogic.hikotsuTaihoDamage(data, 0.0F);
                    VisoredEvents.endMask(renji, data, VisoredEvents.MaskEnd.REMOVED, now);
                    helper.assertTrue(near(factor, 1.5) && near(masked, 1.5 * tap),
                            "behind the mask Hikotsu Taihō is " + factor + " times as strong, " + masked + " at a tap");
                    close.discard();
                    first.discard();
                    second.discard();
                    unstone.run();
                    helper.succeed();
                }));
    }

    /** Runs a test's step, and if it fails, runs {@code clear} before the failure goes on. */
    static void clearingOnFailure(Runnable clear, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            clear.run();
            throw failure;
        }
    }
}
