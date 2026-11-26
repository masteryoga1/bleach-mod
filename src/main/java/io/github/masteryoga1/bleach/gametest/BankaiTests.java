package io.github.masteryoga1.bleach.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.JsonOps;
import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.soul.BankaiLogic;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoBond;
import io.github.masteryoga1.bleach.soul.ZanpakutoEvents;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests for Bankai's records: the blade's stage on the stack and on the wire, read from the flag saved before it; the
 * soul's {@link BankaiRecord} saved with its bond; the spirit file's {@code "bankai"} and the older
 * {@code "bankai_name"}; {@code /bleach zanpakuto … bankai}; and the soul screen's line. Every test runs in one call;
 * none waits on the world or hurts a player.
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

    /** The shipped spirit file of this name, as it lies in the mod's data. */
    private static JsonObject shippedFile(GameTestHelper helper, String spirit) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation file = BleachMod.id("bleach/zanpakuto_spirit/" + spirit + ".json");
        try (Reader reader = server.getResourceManager().getResourceOrThrow(file).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A spirit file read as the world reads them, or nothing if it fails to load. */
    private static Optional<ZanpakutoSpirit> parse(GameTestHelper helper, JsonObject file) {
        return ZanpakutoSpirit.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                file).result();
    }

    /** Whether this line is the words of this key. */
    private static boolean translates(Component line, String key) {
        return line.getContents() instanceof TranslatableContents words && words.getKey().equals(key);
    }

    /**
     * The shipped spirits give their Bankai's name, art, test and spirit, and leave its form, cost and drain to the
     * server's config. A file that gives only {@code "bankai_name"}, as both did before, still loads, with a Bankai of
     * that name and nothing more; given both, {@code "bankai"} wins; given neither, there is no Bankai. A Bankai with no
     * name, an unknown test or a drain past 1 fails to load.
     */
    @GameTest(template = "empty")
    public static void spiritFilesGiveTheirBankaiInEitherShape(GameTestHelper helper) {
        ZanpakutoSpirit.Bankai zangetsu = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU).bankai().orElseThrow();
        helper.assertTrue(translates(zangetsu.name(), "zanpakuto.bleach.zangetsu.bankai")
                        && zangetsu.ability() == ZanpakutoSpirit.BankaiAbility.SPEED
                        && zangetsu.test() == ZanpakutoSpirit.BankaiTest.SWORDS
                        && zangetsu.entity().equals(Optional.of(BleachMod.id("zangetsu_spirit")))
                        && zangetsu.form().isEmpty() && zangetsu.releaseCost().isEmpty() && zangetsu.drain().isEmpty(),
                "Zangetsu's Bankai is " + zangetsu);
        ZanpakutoSpirit.Bankai zabimaru = ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZABIMARU).bankai().orElseThrow();
        helper.assertTrue(translates(zabimaru.name(), "zanpakuto.bleach.zabimaru.bankai")
                        && zabimaru.ability() == ZanpakutoSpirit.BankaiAbility.SERPENT
                        && zabimaru.test() == ZanpakutoSpirit.BankaiTest.TWO_HALVES
                        && zabimaru.entity().equals(Optional.of(BleachMod.id("zabimaru_spirit"))),
                "Zabimaru's Bankai is " + zabimaru);

        // Zangetsu's file with another Bankai's name in the older key, so the key read shows in the name.
        JsonObject shipped = shippedFile(helper, "zangetsu");
        JsonObject oldName = new JsonObject();
        oldName.addProperty("translate", "zanpakuto.bleach.zabimaru.bankai");
        JsonObject older = shipped.deepCopy();
        older.remove("bankai");
        older.add("bankai_name", oldName);
        ZanpakutoSpirit.Bankai named = parse(helper, older).flatMap(ZanpakutoSpirit::bankai).orElseThrow();
        helper.assertTrue(named.equals(ZanpakutoSpirit.Bankai.named(named.name()))
                && translates(named.name(), "zanpakuto.bleach.zabimaru.bankai"), "a bankai_name alone read as " + named);
        JsonObject both = shipped.deepCopy();
        both.add("bankai_name", oldName);
        Optional<ZanpakutoSpirit.Bankai> fromBoth = parse(helper, both).flatMap(ZanpakutoSpirit::bankai);
        helper.assertTrue(fromBoth.equals(Optional.of(zangetsu)), "with both, the Bankai read as " + fromBoth);
        JsonObject neither = shipped.deepCopy();
        neither.remove("bankai");
        helper.assertTrue(parse(helper, neither).map(spirit -> spirit.bankai().isEmpty()).orElse(false),
                "a spirit without either loaded with a Bankai, or not at all");
        for (String bad : List.of("{\"ability\": \"speed\"}", "{\"name\": \"x\", \"test\": \"nonsense\"}",
                "{\"name\": \"x\", \"drain\": 2.0}")) {
            JsonObject broken = shipped.deepCopy();
            broken.add("bankai", JsonParser.parseString(bad));
            helper.assertTrue(parse(helper, broken).isEmpty(), "a Bankai of " + bad + " loaded");
        }
        helper.succeed();
    }

    /** Whether a line, or one it holds, refuses one player for this reason. */
    private static boolean refused(Component line, String reason) {
        if (line.getContents() instanceof TranslatableContents words
                && words.getKey().equals("commands.bleach.quincy.arts_refused") && words.getArgs().length == 2
                && words.getArgs()[1] instanceof Component why && translates(why, reason)) {
            return true;
        }
        return line.getSiblings().stream().anyMatch(each -> refused(each, reason));
    }

    /** A joined player with this soul and these tags, to name it by; a named one knows Zangetsu's name for its Asauchi. */
    private static ServerPlayer join(GameTestHelper helper, SoulData soul, boolean named, String... tags) {
        ServerPlayer player = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(1.5, 1.0, 1.5));
        player.setData(SoulAttachments.SOUL, soul);
        for (String tag : tags) {
            player.addTag(tag);
        }
        if (named) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ZanpakutoTests.asauchi());
            ZanpakutoEvents.learnName(player, soul, ZanpakutoSpirits.ZANGETSU.location(),
                    ZanpakutoTests.spirit(helper, ZanpakutoSpirits.ZANGETSU), player.getMainHandItem());
        }
        return player;
    }

    /**
     * {@code /bleach zanpakuto … bankai grant} by an operator gives a named Shinigami its spirit's Bankai, by name and in
     * its colour, now, and passes over, saying why, a Human without Shinigami power, a Shinigami who does not know its
     * zanpakutō's name, a soul that has Bankai already and one whose spirit the world lacks; {@code revoke} starts the
     * whole road over and leaves the zanpakutō's name; a change of spirit takes the Bankai with it, and the same spirit
     * again does not. A soul without operator rights reaches none of it: the command's root is not for it.
     */
    @GameTest(template = "empty")
    public static void grantAndRevokeSetAndClearBankaiForOperatorsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer ichigo = join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), true,
                "bleach_bankai_all", "bleach_bankai_ichigo");
        ServerPlayer human = join(helper, ZanpakutoTests.soul(SoulState.HUMAN, 500.0F), false, "bleach_bankai_all");
        ServerPlayer nameless = join(helper, ZanpakutoTests.soul(SoulState.SHINIGAMI, 500.0F), false, "bleach_bankai_all");
        String all = "bleach zanpakuto @a[tag=bleach_bankai_all] bankai ";
        String one = "bleach zanpakuto @a[tag=bleach_bankai_ichigo,limit=1] ";
        String granted = "commands.bleach.zanpakuto.bankai.grant";
        SoulData soul = SoulLogic.get(ichigo);

        CommandSourceStack plain = ichigo.createCommandSourceStack().withPermission(0);
        List<Component> said = GateTests.run(plain, one + "bankai grant");
        helper.assertTrue(!said.isEmpty() && said.stream().noneMatch(line -> GateTests.says(line, granted, -1))
                && soul.zanpakuto().bankai().equals(BankaiRecord.NONE), "a soul without rights granted Bankai: " + said);
        CommandNode<CommandSourceStack> root = level.getServer().getCommands().getDispatcher().getRoot().getChild("bleach");
        helper.assertTrue(!root.canUse(plain) && root.canUse(level.getServer().createCommandSourceStack()),
                "/bleach is not for operators alone");

        said = GateTests.run(level, all + "grant");
        Component name = Component.translatable("zanpakuto.bleach.zangetsu.bankai").withColor(ZANGETSU_COLOUR);
        BankaiRecord reached = soul.zanpakuto().bankai();
        helper.assertTrue(reached.achievedAt() == level.getGameTime() && reached.name().equals(name),
                "the grant gave " + reached);
        helper.assertTrue(said.size() == 3
                        && said.stream().anyMatch(line -> refused(line, "commands.bleach.zanpakuto.bankai.no_power"))
                        && said.stream().anyMatch(line -> refused(line, "commands.bleach.zanpakuto.bankai.nameless"))
                        && said.stream().anyMatch(line -> GateTests.says(line, granted, -1)),
                "the grant said " + said.stream().map(Component::getString).toList());
        helper.assertTrue(SoulLogic.get(human).zanpakuto().bankai().equals(BankaiRecord.NONE)
                        && SoulLogic.get(nameless).zanpakuto().bankai().equals(BankaiRecord.NONE),
                "a refused soul was granted Bankai");
        said = GateTests.run(level, one + "bankai grant");
        helper.assertTrue(said.size() == 1 && refused(said.get(0), "commands.bleach.zanpakuto.bankai.already")
                && soul.zanpakuto().bankai().equals(reached), "a second grant said " + said);

        soul.setZanpakuto(soul.zanpakuto().withBankai(road()));
        said = GateTests.run(level, one + "bankai revoke");
        helper.assertTrue(said.size() == 1 && GateTests.says(said.get(0), "commands.bleach.zanpakuto.bankai.revoke", -1)
                        && soul.zanpakuto().bankai().equals(BankaiRecord.NONE) && soul.zanpakuto().named(),
                "revoke left " + soul.zanpakuto());

        GateTests.run(level, one + "bankai grant");
        GateTests.run(level, one + "spirit bleach:zangetsu");
        helper.assertTrue(soul.zanpakuto().bankai().achieved(), "the same spirit again took the Bankai away");
        GateTests.run(level, one + "spirit bleach:zabimaru");
        helper.assertTrue(soul.zanpakuto().bankai().equals(BankaiRecord.NONE) && soul.zanpakuto().named(),
                "a new spirit kept the old one's Bankai: " + soul.zanpakuto().bankai());

        soul.setZanpakuto(soul.zanpakuto().withSpirit(Optional.of(BleachMod.id("missing"))));
        said = GateTests.run(level, one + "bankai grant");
        helper.assertTrue(said.size() == 1 && refused(said.get(0), "commands.bleach.zanpakuto.bankai.none")
                && !soul.zanpakuto().bankai().achieved(), "a spirit the world lacks gave a Bankai: " + said);
        helper.succeed();
    }

    /**
     * The soul screen names the Bankai, in the line under the zanpakutō's, once it is reached, for a Shinigami, a
     * Substitute, a Visored Substitute and a Shinigami whose power is lent away, each knowing its zanpakutō's name: lent
     * power stops reaching and releasing Bankai, never holding it, and {@code mayHold} never reads the Visored record, as
     * a Visored holds Bankai as the Shinigami or Substitute it is. Never for a road not reached, a nameless bond, a
     * student, a Hollow, an Arrancar, or a Plus even with a Shinigami's power (barred as a Plus, nothing else). A reached
     * Bankai whose name is lost still has its line. A pure check of the line the screen draws.
     */
    @GameTest(template = "empty")
    public static void theSoulScreenNamesABankaiOnceItIsReached(GameTestHelper helper) {
        Component name = road().name();
        BankaiRecord reached = BankaiRecord.NONE.reached(NOW, name);
        SoulData shinigami = holder(ZanpakutoTests.soul(SoulState.SHINIGAMI, 300.0F), reached);
        Component line = BankaiLogic.screenLine(shinigami);
        helper.assertTrue(line != null && translates(line, "screen.bleach.soul.bankai")
                && ((TranslatableContents) line.getContents()).getArgs()[0].equals(name), "a Shinigami's line is " + line);
        SoulData visored = holder(ShinigamiTests.substitute(300.0F), reached);
        VisoredLogic.setRecord(visored, ShinigamiProgress.Visored.NONE.withRootedAt(NOW - 1L).withSubduedAt(NOW));
        SoulData lent = holder(ShinigamiTests.lentGiver(), reached);
        helper.assertTrue(VisoredLogic.isVisored(visored) && ShinigamiLogic.isSubstitute(visored)
                && ShinigamiLogic.isPowerLent(lent, NOW), "the Visored Substitute or the lent Shinigami is not one");
        Map<String, SoulData> holders = Map.of("Substitute", holder(ShinigamiTests.substitute(300.0F), reached),
                "Visored Substitute", visored, "lent Shinigami", lent);
        holders.forEach((who, holds) -> helper.assertTrue(BankaiLogic.mayHold(holds) && BankaiLogic.screenLine(holds) != null,
                "a " + who + " who may hold Bankai has no line"));
        Component unnamed = BankaiLogic.screenLine(holder(ZanpakutoTests.soul(SoulState.SHINIGAMI, 300.0F),
                BankaiRecord.NONE.reached(NOW, Component.empty())));
        helper.assertTrue(unnamed != null && translates(unnamed, "screen.bleach.soul.bankai")
                && ((TranslatableContents) unnamed.getContents()).getArgs()[0] instanceof Component arg
                && translates(arg, "screen.bleach.soul.bankai.unnamed"), "a Bankai whose name is lost reads " + unnamed);

        SoulData notYet = holder(ZanpakutoTests.soul(SoulState.SHINIGAMI, 300.0F), BankaiRecord.NONE);
        SoulData nameless = ZanpakutoTests.soul(SoulState.SHINIGAMI, 300.0F);
        nameless.setZanpakuto(ZanpakutoBond.NONE.withBankai(reached));
        SoulData plus = holder(ZanpakutoTests.soul(SoulState.PLUS, 300.0F), reached);
        plus.setTier(PowerPath.SHINIGAMI, 1);
        helper.assertTrue(ShinigamiLogic.hasShinigamiPower(plus), "the Plus holds no Shinigami power, so only that bars it");
        for (SoulData none : List.of(notYet, nameless, holder(ShinigamiTests.student(300.0F), reached),
                holder(ZanpakutoTests.soul(SoulState.HOLLOW, 300.0F), reached),
                holder(ZanpakutoTests.soul(SoulState.ARRANCAR, 300.0F), reached), plus)) {
            helper.assertTrue(BankaiLogic.screenLine(none) == null, "a " + none.state() + " soul's screen names "
                    + BankaiLogic.screenLine(none));
        }
        helper.assertFalse(BankaiLogic.mayHold(nameless) || BankaiLogic.mayHold(plus),
                "a nameless bond or a Plus may hold Bankai");
        helper.succeed();
    }

    /** This soul with a bond named for Zabimaru and this road to Bankai. */
    private static SoulData holder(SoulData soul, BankaiRecord bankai) {
        soul.setZanpakuto(ZanpakutoBond.NONE.asNamed(Component.translatable("zanpakuto.bleach.zabimaru"), false)
                .withBankai(bankai));
        return soul;
    }
}
