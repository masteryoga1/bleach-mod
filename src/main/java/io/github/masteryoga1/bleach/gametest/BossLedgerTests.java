package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.ShriekerComing;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the boss ledger and {@code /bleach boss}: rows kept and damaged ones left out, the command's lines and
 * refusals, a summon, and a death's mark. A test that changes the server's own row does it within one call, resetting it
 * first and after, so no test beside it sees the row changed. The command's test and the summon each run in a batch of
 * their own, as each needs no Shrieker about but its own.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BossLedgerTests {
    private static final String SHRIEKER = ShriekerComing.ID;
    private static final long DAY = BossLedger.TICKS_PER_DAY;
    /** A game time well past a fresh world's start, so a row "now" is plainly not a missing field's 0. */
    private static final long AT = 50_000L;
    /** As the Shrieker's own tests take one. */
    private static final float AWARE = 40.0F;
    /** Further than a tear's ground is sought (24) and its rise, so every column it may try ticks. */
    private static final double REACH = 28.0;
    private static final int GIVE_UP_TICK = 290;

    private BossLedgerTests() {
    }

    /** Whether any line said this key. */
    private static boolean saidKey(List<Component> said, String key) {
        return said.stream().anyMatch(line -> GateTests.says(line, key, -1));
    }

    /** What these lines say, for a failure's message. */
    private static List<String> text(List<Component> said) {
        return said.stream().map(Component::getString).toList();
    }

    /** Whether this line, or one it holds, is one of the mod's command lines, its key under {@code commands.bleach.}. */
    private static boolean ours(Component line) {
        return line.getContents() instanceof TranslatableContents words && words.getKey().startsWith("commands.bleach.")
                || line.getSiblings().stream().anyMatch(BossLedgerTests::ours);
    }

    /** The tears drawn by this player, in every world. */
    static List<GargantaRift> riftsFor(MinecraftServer server, ServerPlayer player) {
        List<GargantaRift> rifts = new ArrayList<>();
        server.getAllLevels().forEach(level -> rifts.addAll(level.getEntities(ModEntities.GARGANTA.get(),
                rift -> player.getUUID().equals(rift.drawer()))));
        return rifts;
    }

    /** The Shriekers this player drew, about it. */
    static List<Shrieker> drawnBy(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(Shrieker.class, player.getBoundingBox().inflate(48.0),
                shrieker -> player.getUUID().equals(shrieker.drawer()));
    }

    /** A row as a world might hold it, by hand. */
    private static CompoundTag row(@Nullable String id, long until) {
        CompoundTag row = new CompoundTag();
        if (id != null) {
            row.putString("id", id);
        }
        row.putLong("until", until);
        return row;
    }

    /**
     * A fresh ledger: nothing waits. Marked twice in a fight, the longer wait stands and both souls are kept in order; a
     * kept id is made once. Saved and read back whole, the wait ends on its tick, and reset frees it and forgets the
     * souls but keeps the kept id, the emptied row left out of the save. Read from a damaged save, a row with no id, a
     * blank id or a second entry for one id is left out, a field of the wrong kind takes its default, a soul that cannot
     * be read is dropped from its row, a wait further off than a year counts as over, and a list of the wrong kind reads
     * as empty, all without throwing.
     */
    @GameTest(template = "empty")
    public static void theLedgerKeepsItsRowsAndLeavesOutDamagedOnes(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BossLedger ledger = new BossLedger();
        helper.assertTrue(ledger.mayCome(SHRIEKER, AT) && ledger.waitLeft(SHRIEKER, AT) == 0L
                && ledger.beatenBy(SHRIEKER).isEmpty() && ledger.kept(SHRIEKER) == null, "a fresh ledger holds a row");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        ledger.markAway(SHRIEKER, AT, 3, List.of(first));
        ledger.markAway(SHRIEKER, AT, 1, List.of(second, first));
        helper.assertTrue(ledger.waitLeft(SHRIEKER, AT) == 3 * DAY, "a second, shorter mark cut the wait to "
                + ledger.waitLeft(SHRIEKER, AT));
        helper.assertTrue(List.copyOf(ledger.beatenBy(SHRIEKER)).equals(List.of(first, second)),
                "the souls who beat it are " + ledger.beatenBy(SHRIEKER));
        UUID kept = ledger.keptId("someday");
        helper.assertTrue(kept.equals(ledger.keptId("someday")), "a kept boss's id changed when asked again");

        BossLedger back = BossLedger.load(ledger.save(new CompoundTag(), registries), registries);
        helper.assertTrue(back.waitLeft(SHRIEKER, AT) == 3 * DAY && !back.mayCome(SHRIEKER, AT + 3 * DAY - 1)
                && back.mayCome(SHRIEKER, AT + 3 * DAY), "read back, the wait is " + back.waitLeft(SHRIEKER, AT));
        helper.assertTrue(List.copyOf(back.beatenBy(SHRIEKER)).equals(List.of(first, second))
                && kept.equals(back.kept("someday")), "read back, the souls or the kept id changed");
        back.reset(SHRIEKER);
        back.reset("someday");
        helper.assertTrue(back.mayCome(SHRIEKER, AT) && back.beatenBy(SHRIEKER).isEmpty()
                && kept.equals(back.kept("someday")), "reset left a wait or a soul, or lost the kept id");
        ListTag rows = back.save(new CompoundTag(), registries).getList("bosses", Tag.TAG_COMPOUND);
        helper.assertTrue(rows.size() == 1 && rows.getCompound(0).getString("id").equals("someday"),
                "after reset the save holds " + rows);

        ListTag damaged = new ListTag();
        damaged.add(row(null, AT + DAY));
        damaged.add(row("", AT + DAY));
        CompoundTag shrieker = row(SHRIEKER, AT + DAY);
        ListTag souls = new ListTag();
        souls.add(new IntArrayTag(new int[] {1, 2, 3}));
        souls.add(NbtUtils.createUUID(first));
        shrieker.put("beaten_by", souls);
        damaged.add(shrieker);
        damaged.add(row(SHRIEKER, AT + 5 * DAY));
        CompoundTag odd = row("odd", AT + DAY);
        odd.putString("until", "soon");
        odd.putString("kept", "nobody");
        ListTag one = new ListTag();
        one.add(NbtUtils.createUUID(second));
        odd.put("beaten_by", one);
        damaged.add(odd);
        damaged.add(row("far", AT + 400 * DAY));
        CompoundTag keeper = row("keeper", 0L);
        keeper.putUUID("kept", kept);
        damaged.add(keeper);
        CompoundTag file = new CompoundTag();
        file.put("bosses", damaged);
        BossLedger mended = BossLedger.load(file, registries);
        helper.assertTrue(mended.waitLeft(SHRIEKER, AT) == DAY
                        && List.copyOf(mended.beatenBy(SHRIEKER)).equals(List.of(first)),
                "the first Shrieker row did not stand whole, less its bad soul: " + mended.waitLeft(SHRIEKER, AT));
        helper.assertTrue(mended.mayCome("odd", AT) && mended.kept("odd") == null
                && List.copyOf(mended.beatenBy("odd")).equals(List.of(second)), "a damaged field did not take its default");
        helper.assertTrue(mended.mayCome("far", AT), "a wait 400 days off still holds");
        helper.assertTrue(kept.equals(mended.kept("keeper")), "a kept id alone was lost");
        int saved = mended.save(new CompoundTag(), registries).getList("bosses", Tag.TAG_COMPOUND).size();
        helper.assertTrue(saved == 4, "the mended ledger saves " + saved + " rows, not shrieker, odd, far and keeper");
        CompoundTag wrong = new CompoundTag();
        wrong.putString("bosses", "nonsense");
        helper.assertTrue(BossLedger.load(wrong, registries).save(new CompoundTag(), registries)
                .getList("bosses", Tag.TAG_COMPOUND).isEmpty(), "a list of the wrong kind read as rows");
        helper.succeed();
    }

    /**
     * Tags this soul and gives the selector that names it alone. A UUID names an entity, which a player argument refuses,
     * and every joined test soul has the same name.
     */
    private static String named(ServerPlayer player, String tag) {
        player.addTag(tag);
        return "@a[tag=" + tag + ",limit=1]";
    }

    /**
     * {@code /bleach boss} by the server: status says the row's wait and its soul; a summon is refused on Peaceful and,
     * from the console, for want of a player; an unknown boss or Hollow is refused by name; reset, in any case of name,
     * frees the row and forgets the soul, as status then says. A soul without operator rights reaches none of
     * {@code /bleach}: the boss's status, reset and summon and a Garganta each say none of the mod's lines, and none of
     * them changes the row, the count or the sky. In one call, and a batch of its own, as the console's summon needs no
     * Shrieker about.
     */
    @GameTest(template = "empty", batch = "boss_command")
    public static void theBossCommandSaysAndResetsTheRowAndKeepsItsRefusals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        CommandSourceStack plain = player.createCommandSourceStack().withPermission(0);
        String target = named(player, "bleach_boss_command");
        String garganta = "bleach garganta " + target + " ";
        BossLedger ledger = BossLedger.get(server);
        long now = BossLedger.now(server);
        try {
            // Another test's Shrieker may have died on an earlier tick, marking the row three days away.
            ledger.reset(SHRIEKER);
            ledger.markAway(SHRIEKER, now, 1, List.of(UUID.randomUUID()));
            int about = ShriekerComing.BOSS.present(server);
            for (String command : List.of("bleach boss shrieker status", "bleach boss shrieker reset",
                    "bleach boss shrieker summon", garganta + "shrieker")) {
                List<Component> refused = GateTests.run(plain, command);
                helper.assertTrue(!refused.isEmpty() && refused.stream().noneMatch(BossLedgerTests::ours),
                        command + " by a soul without rights said " + text(refused));
            }
            helper.assertTrue(ledger.waitLeft(SHRIEKER, now) == DAY && ledger.beatenBy(SHRIEKER).size() == 1,
                    "a refused command changed the row");
            List<Component> said = GateTests.run(level, "bleach boss shrieker status");
            helper.assertTrue(saidKey(said, "commands.bleach.boss.status")
                    && saidKey(said, "commands.bleach.boss.status.away")
                    && saidKey(said, "commands.bleach.boss.status.beaten"), "status said " + text(said));
            // Peaceful within this one call, so no tick sees it.
            Difficulty difficulty = server.getWorldData().getDifficulty();
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                said = GateTests.run(level, "bleach boss shrieker summon " + target);
            } finally {
                server.setDifficulty(difficulty, true);
            }
            helper.assertTrue(said.size() == 1 && saidKey(said, "commands.bleach.boss.peaceful"),
                    "on Peaceful the summon said " + text(said));
            said = GateTests.run(level, "bleach boss shrieker summon");
            helper.assertTrue(said.size() == 1 && saidKey(said, "commands.bleach.boss.summon.no_player"),
                    "from the console the summon said " + text(said));
            helper.assertTrue(ShriekerComing.BOSS.present(server) == about && riftsFor(server, player).isEmpty(),
                    "a refused command or summon opened a tear");
            said = GateTests.run(level, "bleach boss shrekier status");
            helper.assertTrue(said.size() == 1 && saidKey(said, "commands.bleach.unknown_boss"),
                    "an unknown boss gave " + text(said));
            said = GateTests.run(level, garganta + "shriker");
            helper.assertTrue(said.size() == 1 && saidKey(said, "commands.bleach.unknown_hollow"),
                    "an unknown Hollow gave " + text(said));
            said = GateTests.run(level, "bleach boss Shrieker reset");
            helper.assertTrue(said.size() == 1 && saidKey(said, "commands.bleach.boss.reset"), "reset said " + text(said));
            helper.assertTrue(ledger.mayCome(SHRIEKER, now) && ledger.beatenBy(SHRIEKER).isEmpty(),
                    "after reset it waits " + ledger.waitLeft(SHRIEKER, now) + ", beaten by " + ledger.beatenBy(SHRIEKER));
            said = GateTests.run(level, "bleach boss shrieker status");
            helper.assertTrue(saidKey(said, "commands.bleach.boss.status.free")
                    && saidKey(said, "commands.bleach.boss.status.unbeaten"), "status after reset said " + text(said));
        } finally {
            ledger.reset(SHRIEKER);
        }
        helper.succeed();
    }

    /**
     * With none about, {@code /bleach boss shrieker summon <player>} opens one tear beside that player, 12 to 24 blocks
     * off as any arrival, carrying one Shrieker: counted at once, so a second summon is refused, and again once the
     * Shrieker is out and hunting that player. A batch of its own, as it counts every Shrieker on the server.
     */
    @GameTest(template = "field", batch = "boss_summon", timeoutTicks = 300)
    public static void aSummonBringsOneShriekerBesideItsPlayerAndNoSecond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = ShriekerTests.aware(helper, new Vec3(8.5, 2.0, 8.5));
        Vec3 start = player.position();
        String summon = "bleach boss shrieker summon " + named(player, "bleach_boss_summon");
        List<ChunkPos> forced = HollowBaitTests.forceAround(level, start, REACH);
        Runnable clear = () -> {
            QuincyArtsTests.discardAll(riftsFor(server, player));
            QuincyArtsTests.discardAll(drawnBy(level, player));
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            forced.clear();
        };
        helper.runAtTickTime(GIVE_UP_TICK, () -> {
            clear.run();
            helper.fail("the summoned Shrieker never came out for its player");
        });
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper, HollowBaitTests.allTicking(level, start, REACH),
                        "the ground around the player never started ticking"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    int before = ShriekerComing.BOSS.present(server);
                    helper.assertTrue(before == 0, before + " Shriekers are about from earlier tests");
                    List<Component> said = List.of();
                    // Open ground is sought at random; on the field's flat ground one of three summons finds it.
                    for (int i = 0; i < 3 && !saidKey(said, "commands.bleach.boss.summon.success"); i++) {
                        said = GateTests.run(level, summon);
                    }
                    helper.assertTrue(saidKey(said, "commands.bleach.boss.summon.success"), "the summon said " + text(said));
                    List<GargantaRift> rifts = riftsFor(server, player);
                    helper.assertTrue(rifts.size() == 1 && rifts.get(0).pending().equals(List.of(HollowKind.SHRIEKER)),
                            "the summon tore " + rifts.size() + " Garganta for the player");
                    double away = Math.hypot(rifts.get(0).getX() - start.x, rifts.get(0).getZ() - start.z);
                    helper.assertTrue(away >= 11.0 && away <= 25.0, "the tear opened " + away + " blocks off, not 12 to 24");
                    helper.assertTrue(ShriekerComing.BOSS.present(server) == 1, "a tear carrying a Shrieker is not counted");
                    helper.assertTrue(saidKey(GateTests.run(level, summon), "commands.bleach.boss.summon.about")
                            && riftsFor(server, player).size() == 1, "a second summon was not refused for the tear");
                }, clear))
                .thenWaitUntil(() -> helper.assertFalse(drawnBy(level, player).isEmpty(),
                        "no Shrieker has come out of the tear"))
                .thenExecute(HollowBaitTests.orClear(() -> {
                    List<Shrieker> out = drawnBy(level, player);
                    helper.assertTrue(out.size() == 1 && out.get(0).getTarget() == player, out.size()
                            + " Shriekers came, hunting " + out.get(0).getTarget());
                    helper.assertTrue(ShriekerComing.BOSS.present(server) == 1, "the Shrieker and its emptied tear count "
                            + ShriekerComing.BOSS.present(server) + ", not 1");
                    helper.assertTrue(saidKey(GateTests.run(level, summon), "commands.bleach.boss.summon.about"),
                            "a second summon was not refused while the Shrieker lives");
                    clear.run();
                }, clear))
                .thenSucceed();
    }

    /**
     * A Shrieker taken away (discarded) writes nothing, nor does a plain Hollow's death or a practice Shrieker's by a
     * soul's blow. One struck down by a Shinigami's Asauchi marks the server's row away {@code return_days} from this
     * tick, beaten by that Shinigami, and its dying body is no longer counted about; one put down by {@code /kill}'s
     * damage after it leaves the wait and the souls as they were. In one call.
     */
    @GameTest(template = "field")
    public static void aShriekersDeathMarksItsDaysAndWhoBeatIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BossLedger ledger = BossLedger.get(server);
        long now = BossLedger.now(server);
        long days = BleachConfig.get(BleachConfig.SHRIEKER_RETURN_DAYS) * DAY;
        ServerPlayer striker = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 2.5));
        striker.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, AWARE));
        HollowTests.arm(striker, ModItems.ASAUCHI.get());
        DamageSource blow = level.damageSources().playerAttack(striker);
        try {
            // Another test's Shrieker may have died on an earlier tick, marking the row three days away.
            ledger.reset(SHRIEKER);
            helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(8.5, 6.0, 4.5)).discard();
            SpiderHollow plain = helper.spawn(ModEntities.SPIDER_HOLLOW.get(), new Vec3(4.5, 2.0, 6.5));
            plain.hurt(blow, 1000.0F);
            Shrieker practice = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(11.5, 6.0, 4.5));
            practice.makePractice(striker.getUUID(), now);
            practice.hurt(blow, 1000.0F);
            helper.assertTrue(plain.isDeadOrDying() && practice.isDeadOrDying(),
                    "the plain Hollow or the practice Shrieker outlived the blow");
            helper.assertTrue(ledger.mayCome(SHRIEKER, now) && ledger.beatenBy(SHRIEKER).isEmpty(),
                    "a Shrieker taken away, a plain Hollow or a practice Shrieker marked the row");
            int before = ShriekerComing.BOSS.present(server);
            Shrieker struck = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(4.5, 6.0, 10.5));
            struck.hurt(blow, 1000.0F);
            helper.assertTrue(struck.isDeadOrDying() && ledger.waitLeft(SHRIEKER, now) == days,
                    "struck down by the Asauchi, the row waits " + ledger.waitLeft(SHRIEKER, now) + " ticks, not " + days);
            helper.assertTrue(List.copyOf(ledger.beatenBy(SHRIEKER)).equals(List.of(striker.getUUID())),
                    "the row is beaten by " + ledger.beatenBy(SHRIEKER) + ", not the Shinigami who struck it down");
            int after = ShriekerComing.BOSS.present(server);
            helper.assertTrue(after == before, "the struck-down Shrieker still counts: " + after + " about, not " + before);
            Shrieker killed = helper.spawn(ModEntities.SHRIEKER.get(), new Vec3(11.5, 6.0, 10.5));
            killed.kill();
            helper.assertTrue(killed.isDeadOrDying() && ledger.waitLeft(SHRIEKER, now) == days
                    && List.copyOf(ledger.beatenBy(SHRIEKER)).equals(List.of(striker.getUUID())),
                    "a second death by /kill changed the wait or credited a soul");
        } finally {
            ledger.reset(SHRIEKER);
        }
        helper.succeed();
    }
}
