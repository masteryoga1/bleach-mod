package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Shrieker;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.ShriekerComing;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for the boss ledger: rows kept and damaged ones left out, and a death's mark. A test that changes the server's
 * own row does it within one call, resetting it first and after, so no test beside it sees the row changed.
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

    private BossLedgerTests() {
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
