package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Bankai's records: the blade's stage on the stack and on the wire, read from the flag saved before it, and
 * the soul's {@link BankaiRecord} saved with its bond. Every test runs in one call; none waits on the world or
 * hurts a player.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankaiTests {
    private static final long NOW = ZanpakutoTests.NOW;
    private static final UUID OWNER = new UUID(11L, 13L);
    private static final int ZANGETSU_COLOUR = 0xBFEFFF;

    private BankaiTests() {
    }

    /** A blade of Zangetsu's at this stage, as the codecs see it. */
    private static ZanpakutoBlade blade(ZanpakutoStage stage, boolean constant) {
        return new ZanpakutoBlade(Optional.of(OWNER), "Ichigo", Optional.of(ZanpakutoSpirits.ZANGETSU.location()), stage,
                constant, ZANGETSU_COLOUR);
    }

    /** A saved blade read back through the component's own codec. */
    private static ZanpakutoBlade read(CompoundTag tag) {
        return ZanpakutoBlade.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    /**
     * Every stage, constant or not, saves to NBT and JSON and goes over the wire unchanged; the stage is written by name
     * and not at all while sealed, and the old flag is never written. A blade saved before Bankai, with
     * {@code released} set, reads as Shikai (a released Zangetsu stays released), and unset or missing as sealed, through
     * the stack's own codec too; a stage that cannot be read falls back to that flag, keeping the rest; an ordinal the
     * wire does not know reads as sealed. {@code released()} is anything but sealed.
     */
    @GameTest(template = "empty")
    public static void theBladesStageSavesSendsAndReadsTheOldFlag(GameTestHelper helper) {
        for (ZanpakutoStage stage : ZanpakutoStage.values()) {
            for (boolean constant : new boolean[] {false, true}) {
                ZanpakutoBlade blade = blade(stage, constant);
                CompoundTag tag = (CompoundTag) ZanpakutoBlade.CODEC.encodeStart(NbtOps.INSTANCE, blade).getOrThrow();
                JsonElement json = ZanpakutoBlade.CODEC.encodeStart(JsonOps.INSTANCE, blade).getOrThrow();
                ByteBuf buffer = Unpooled.buffer();
                ZanpakutoBlade.STREAM_CODEC.encode(buffer, blade);
                ZanpakutoBlade sent = ZanpakutoBlade.STREAM_CODEC.decode(buffer);
                helper.assertTrue(read(tag).equals(blade) && sent.equals(blade)
                                && ZanpakutoBlade.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(blade),
                        "a blade " + blade + " did not come back as it went");
                boolean named = stage.isReleased() && tag.getString("stage").equals(stage.getSerializedName());
                helper.assertTrue(!tag.contains("released") && (named || !stage.isReleased() && !tag.contains("stage")),
                        "a blade " + stage + " saved as " + tag);
                helper.assertTrue(blade.stage() == stage && blade.released() == (stage != ZanpakutoStage.SEALED),
                        "a blade " + stage + " answers released() " + blade.released());
            }
        }

        // A released Zangetsu saved before Bankai.
        CompoundTag older = (CompoundTag) ZanpakutoBlade.CODEC.encodeStart(NbtOps.INSTANCE,
                blade(ZanpakutoStage.SHIKAI, true)).getOrThrow();
        older.remove("stage");
        older.putBoolean("released", true);
        helper.assertTrue(read(older).equals(blade(ZanpakutoStage.SHIKAI, true)), "an old released blade read as "
                + read(older));
        CompoundTag rewritten = (CompoundTag) ZanpakutoBlade.CODEC.encodeStart(NbtOps.INSTANCE, read(older)).getOrThrow();
        helper.assertTrue(!rewritten.contains("released") && rewritten.getString("stage").equals("shikai"),
                "an old released blade saved again as " + rewritten);
        CompoundTag badStage = older.copy();
        badStage.putString("stage", "x");
        helper.assertTrue(read(badStage).equals(blade(ZanpakutoStage.SHIKAI, true)),
                "a blade with an unreadable stage and the old flag read as " + read(badStage));
        older.putBoolean("released", false);
        helper.assertTrue(read(older).equals(blade(ZanpakutoStage.SEALED, true)), "an old sealed blade read as "
                + read(older));
        older.remove("released");
        older.putInt("stage", 5);
        helper.assertTrue(read(older).equals(blade(ZanpakutoStage.SEALED, true)),
                "a blade with neither a readable stage nor the flag read as " + read(older));

        // The same, as a stack is saved: through the item's own codec and the component's.
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack stack = ZanpakutoTests.asauchi();
        stack.set(ModComponents.ZANPAKUTO_BLADE.get(), blade(ZanpakutoStage.SHIKAI, true));
        CompoundTag saved = (CompoundTag) stack.save(registries);
        CompoundTag component = saved.getCompound("components").getCompound("bleach:zanpakuto_blade");
        component.remove("stage");
        component.putBoolean("released", true);
        Optional<ItemStack> loaded = ItemStack.parse(registries, saved);
        helper.assertTrue(loaded.isPresent() && ZanpakutoItem.blade(loaded.get()).stage() == ZanpakutoStage.SHIKAI,
                "a stack saved with the old flag loaded as " + loaded.map(ZanpakutoItem::blade));

        ByteBuf unknown = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(unknown, 7);
        helper.assertTrue(ZanpakutoStage.STREAM_CODEC.decode(unknown) == ZanpakutoStage.SEALED,
                "an unknown stage on the wire did not read as sealed");
        helper.succeed();
    }

    /**
     * Shikai's own writers make Shikai, never Bankai: Zabimaru's release puts it in Shikai and its seal back to sealed,
     * and Zangetsu's release is Shikai for good. A blade in Bankai, which only a test or a later build makes, is released
     * to every reader that asks: set released again it stays in Bankai, release says it is already released, and the
     * seal and the ground seal put it back to sealed in its sealed form.
     */
    @GameTest(template = "empty")
    public static void shikaiIsShikaiAndABankaiBladeStillAnswersReleased(GameTestHelper helper) {
        Player renji = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZABIMARU);
        ItemStack zabimaru = renji.getMainHandItem();
        helper.assertTrue(ShikaiEvents.release(renji) == ShikaiEvents.Result.RELEASED, "Zabimaru would not release");
        ZanpakutoBlade released = ZanpakutoItem.blade(zabimaru);
        helper.assertTrue(released.stage() == ZanpakutoStage.SHIKAI && released.released(),
                "released Zabimaru is " + released.stage());
        helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED
                && ZanpakutoItem.blade(zabimaru).stage() == ZanpakutoStage.SEALED, "Zabimaru did not seal");

        Player ichigo = ZanpakutoTests.named(helper, ZanpakutoSpirits.ZANGETSU);
        helper.assertTrue(ShikaiEvents.release(ichigo) == ShikaiEvents.Result.RELEASED, "Zangetsu would not release");
        ZanpakutoBlade constant = ZanpakutoItem.blade(ichigo.getMainHandItem());
        helper.assertTrue(constant.stage() == ZanpakutoStage.SHIKAI && constant.constant(),
                "released Zangetsu is " + constant.stage() + (constant.constant() ? "" : ", not for good"));

        renji.tickCount += ShikaiEvents.SETTLE_TICKS + 1;
        zabimaru.set(ModComponents.ZANPAKUTO_BLADE.get(), ZanpakutoItem.blade(zabimaru).withStage(ZanpakutoStage.BANKAI));
        ItemStack lying = zabimaru.copy();
        ZanpakutoItem.setReleased(zabimaru, ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU), true);
        helper.assertTrue(ZanpakutoItem.blade(zabimaru).stage() == ZanpakutoStage.BANKAI,
                "a blade in Bankai set released again is in " + ZanpakutoItem.blade(zabimaru).stage());
        ShikaiEvents.Result again = ShikaiEvents.release(renji);
        helper.assertTrue(again == ShikaiEvents.Result.ALREADY, "a blade in Bankai was released again: " + again);
        helper.assertTrue(ShikaiEvents.seal(renji) == ShikaiEvents.Result.SEALED
                        && ZanpakutoItem.blade(zabimaru).stage() == ZanpakutoStage.SEALED
                        && ZanpakutoTests.model(zabimaru) == 2,
                "a blade in Bankai did not seal to sealed Zabimaru");
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        ItemEntity dropped = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, lying);
        lying.getItem().onEntityItemUpdate(lying, dropped);
        helper.assertTrue(ZanpakutoItem.blade(dropped.getItem()).stage() == ZanpakutoStage.SEALED,
                "a blade in Bankai lay on the ground " + ZanpakutoItem.blade(dropped.getItem()).stage());
        helper.succeed();
    }

    /** A road to Bankai with every field set apart from its default. */
    private static BankaiRecord road() {
        return new BankaiRecord(NOW - 100L, Component.translatable("zanpakuto.bleach.zabimaru.bankai").withColor(0xE0483A),
                NOW + 72_000L, Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 64, -7))), 0.25F, 3, NOW - 50L,
                12_345L);
    }

    /**
     * The bond's Bankai record saves with the soul, through the soul's own codec, and goes whole to the owner's client.
     * Not begun, it saves no "bankai"; a soul saved before Bankai reads as not begun and keeps the rest of its bond; one
     * unreadable field falls back alone, and a record that is no record at all falls back whole, keeping the bond. Odd
     * numbers are held in range, and the bond made at the old arity has no road begun.
     */
    @GameTest(template = "empty")
    public static void theBondsBankaiRecordSavesAndReadsLeniently(GameTestHelper helper) {
        SoulData data = ZanpakutoTests.soul(SoulState.SHINIGAMI, 300.0F);
        ZanpakutoBond bond = ZanpakutoBond.NONE.withBond(ZanpakutoBond.FULL).withBankai(road());
        data.setZanpakuto(bond);
        CompoundTag tag = (CompoundTag) SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        BankaiRecord loaded = SoulData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().zanpakuto().bankai();
        helper.assertTrue(loaded.equals(road()) && loaded.achieved(), "saved " + road() + " but loaded " + loaded);
        helper.assertTrue(data.forClient().zanpakuto().bankai().equals(road()),
                "the owner's client was sent " + data.forClient().zanpakuto().bankai());

        data.setZanpakuto(bond.withBankai(BankaiRecord.NONE));
        CompoundTag none = (CompoundTag) SoulData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        helper.assertFalse(none.getCompound("zanpakuto").contains("bankai"), "a road not begun was saved as "
                + none.getCompound("zanpakuto").get("bankai"));
        helper.assertFalse(BankaiRecord.NONE.achieved(), "a road not begun has reached Bankai");

        CompoundTag older = tag.copy();
        older.getCompound("zanpakuto").remove("bankai");
        ZanpakutoBond before = SoulData.CODEC.parse(NbtOps.INSTANCE, older).getOrThrow().zanpakuto();
        helper.assertTrue(before.equals(bond.withBankai(BankaiRecord.NONE)),
                "a bond saved before Bankai loaded as " + before);
        CompoundTag oneBad = tag.copy();
        oneBad.getCompound("zanpakuto").getCompound("bankai").putString("tries", "x");
        BankaiRecord mended = SoulData.CODEC.parse(NbtOps.INSTANCE, oneBad).getOrThrow().zanpakuto().bankai();
        helper.assertTrue(mended.tries() == 0 && mended.achievedAt() == road().achievedAt()
                && mended.name().equals(road().name()), "one bad count took more with it: " + mended);
        CompoundTag broken = tag.copy();
        broken.getCompound("zanpakuto").putString("bankai", "x");
        ZanpakutoBond kept = SoulData.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow().zanpakuto();
        helper.assertTrue(kept.bankai().equals(BankaiRecord.NONE) && kept.isFull(), "a broken record read as " + kept);

        BankaiRecord odd = new BankaiRecord(NOW, Component.empty(), NOW, Optional.empty(), Float.NaN, -3, NOW, -9L);
        helper.assertTrue(odd.spiritHealth() == 1.0F && odd.tries() == 0 && odd.shikaiTicks() == 0L,
                "odd numbers were kept: " + odd);
        helper.assertTrue(new BankaiRecord(NOW, Component.empty(), NOW, Optional.empty(), 5.0F, 0, NOW, 0L)
                .spiritHealth() == 1.0F, "a share of health above whole was kept");
        ZanpakutoBond oldArity = new ZanpakutoBond(0, Map.of(), Optional.empty(), false, Component.empty(),
                false, false, 0, ZanpakutoBond.NEVER, ZanpakutoBond.NEVER, 0, 0, 0, false, ZanpakutoBond.NEVER);
        helper.assertTrue(oldArity.equals(ZanpakutoBond.NONE), "the bond at the old arity is " + oldArity);
        helper.succeed();
    }
}
