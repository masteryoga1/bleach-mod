package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.DialogueView;
import io.github.masteryoga1.bleach.entity.KisukeUrahara;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.network.OpenDialoguePayload;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.ArrancarNpcLogic;
import io.github.masteryoga1.bleach.soul.DevourLogic;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Urahara Shop's keeper (7n): Kisuke Urahara's rules, his one place where a template sets him, and his words to each
 * kind of soul. Each test takes away the keepers it set down and the players it joined, however it ends.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class UraharaShopTests {
    private UraharaShopTests() {
    }

    /**
     * Test 6. Kisuke Urahara is in neither spirit tag; no blow, a creative player's too, fall, blast, fire or magic harms
     * him, nothing moves, hunts or binds him, a soul's art passes him by and he never despawns; a keeper set down turned
     * faces that way, body and head too; two set at one spot are one; the void and {@code /kill} take him.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void uraharaIsUnharmedAndUnmoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<KisukeUrahara> type = ModEntities.KISUKE_URAHARA.get();
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(BleachMod.id("kisuke_urahara_spawn_egg"))
                && !type.is(SoulLogic.SPIRITS) && !type.is(SoulLogic.SPIRIT_SIGHTED),
                "Urahara has no egg, or is hidden from the unaware, or Pesquisa senses him");
        ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ShakonmakuTests.Cleanup held = ShakonmakuTests.hold(helper, level, chunk.x, chunk.z);
        Vec3 spot = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        KisukeUrahara first = fromTemplate(level, spot);
        KisukeUrahara second = fromTemplate(level, spot);
        KisukeUrahara urahara = helper.spawn(type, new Vec3(1.5, 1.0, 1.5));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(1.8, 1.0, 1.5));
        held.atEnd(() -> TravelTests.discard(urahara, zombie, first, second));
        ServerPlayer creative = TestPlayers.join(helper, GameType.CREATIVE, new Vec3(2.5, 1.0, 3.5));
        DamageSources sources = level.damageSources();
        for (DamageSource source : List.of(sources.mobAttack(zombie), sources.playerAttack(creative), sources.generic(),
                sources.fall(), sources.explosion(null, null), sources.lava(), sources.magic(), sources.wither())) {
            helper.assertFalse(urahara.hurt(source, 1000.0F), "Urahara was hurt by " + source.getMsgId());
        }
        Vec3 at = urahara.position();
        urahara.knockback(5.0, 1.0, 0.0);
        zombie.push(urahara);
        urahara.setDeltaMovement(1.0, 0.5, 0.0);
        helper.assertTrue(!urahara.isPushable() && !urahara.isPushedByFluid()
                && urahara.getPistonPushReaction() == PushReaction.IGNORE && !urahara.canUsePortal(false)
                && !urahara.canBeLeashed() && !urahara.startRiding(zombie, true)
                && !urahara.removeWhenFarAway(1.0E6) && urahara.requiresCustomPersistence(),
                "Urahara can be pushed, carried off or despawned");
        helper.assertTrue(!urahara.canBeSeenAsEnemy() && urahara.isInvulnerable() && !HollowLogic.isPrey(urahara)
                && !ArrancarNpcLogic.isPrey(urahara) && DevourLogic.preyOf(urahara) == null
                && !TargetingConditions.forCombat().test(zombie, urahara), "Urahara can be hunted");
        helper.assertTrue(!SpiritProjectiles.mayStrike(creative, urahara) && !KidoLogic.mayBind(creative, urahara),
                "a soul's art may strike Urahara, or a Bakudō bind him");
        // As a template turned a quarter clockwise sets its keeper down.
        float turned = first.rotate(Rotation.CLOCKWISE_90);
        first.moveTo(spot.x, spot.y, spot.z, turned, first.getXRot());
        helper.assertTrue(first.getYRot() == turned && first.yBodyRot == turned && first.getYHeadRot() == turned,
                "a keeper turned to " + turned + " faces " + first.getYRot() + ", his body " + first.yBodyRot
                        + " and his head " + first.getYHeadRot());
        helper.assertTrue(first.isInvulnerable() && level.addFreshEntity(first) && !level.addFreshEntity(second)
                && first.getUUID().equals(KisukeUrahara.spotId(level, BlockPos.containing(spot))),
                "two keepers set at one spot are not one, or a template's keeper can be hurt");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertTrue(urahara.isAlive() && urahara.getHealth() == urahara.getMaxHealth()
                    && urahara.position().distanceToSqr(at) < 1.0E-6, "Urahara was harmed or moved, to "
                    + urahara.position());
            helper.assertTrue(first.hurt(sources.fellOutOfWorld(), 4.0F), "the void does not reach Urahara");
            urahara.kill();
            helper.assertFalse(urahara.isAlive(), "/kill did not kill Urahara");
        }).thenSucceed();
    }

    /**
     * Test 7. He speaks to each kind of soul in its own view, with one of its two asides; to a joined player within
     * 8 blocks, unaware or a Hollow, but not to one further off or to a spectator.
     */
    @GameTest(template = "empty")
    public static void uraharaSpeaksToEachSoul(GameTestHelper helper) {
        SoulData visored = ShinigamiTests.soul(SoulState.SHINIGAMI, 40.0F);
        VisoredLogic.setRecord(visored, VisoredLogic.record(visored).withRootedAt(0L).withSubduedAt(1L));
        Map<SoulData, DialogueView> expected = new LinkedHashMap<>();
        expected.put(ShinigamiTests.soul(SoulState.HUMAN, 1.0F), DialogueView.SHOP_STRANGER);
        expected.put(ShinigamiTests.soul(SoulState.HUMAN,
                (float) (double) BleachConfig.get(BleachConfig.AWARENESS_THRESHOLD)), DialogueView.SHOP_AWARE);
        expected.put(ShinigamiTests.soul(SoulState.PLUS, 40.0F), DialogueView.SHOP_PLUS);
        expected.put(ShinigamiTests.soul(SoulState.RUKONGAI, 40.0F), DialogueView.SHOP_SOUL);
        expected.put(ShinigamiTests.student(40.0F), DialogueView.SHOP_STUDENT);
        // A living Substitute, whose Shinigami power comes before the Human's awareness.
        expected.put(ShinigamiTests.substitute(60.0F), DialogueView.SHOP_SHINIGAMI);
        expected.put(QuincyTests.quincy(Bloodline.ECHT, 1, 40.0F), DialogueView.SHOP_QUINCY);
        expected.put(ShinigamiTests.soul(SoulState.HOLLOW, 40.0F), DialogueView.SHOP_HOLLOW);
        expected.put(ShinigamiTests.soul(SoulState.ARRANCAR, 40.0F), DialogueView.SHOP_ARRANCAR);
        expected.put(visored, DialogueView.SHOP_VISORED);
        expected.forEach((soul, view) -> helper.assertTrue(KisukeUrahara.viewFor(soul) == view,
                "Urahara shows " + KisukeUrahara.viewFor(soul) + " to a " + soul.state() + ", not " + view));
        KisukeUrahara urahara = helper.spawn(ModEntities.KISUKE_URAHARA.get(), new Vec3(1.5, 1.0, 1.5));
        try {
            ServerPlayer visitor = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(3.5, 1.0, 1.5));
            visitor.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HUMAN, 1.0F));
            talks(helper, urahara, visitor, DialogueView.SHOP_STRANGER, 1);
            visitor.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.HOLLOW, 40.0F));
            talks(helper, urahara, visitor, DialogueView.SHOP_HOLLOW, 2);
            Vec3 far = helper.absoluteVec(new Vec3(10.5, 1.0, 1.5));
            visitor.teleportTo(far.x, far.y, far.z);
            helper.assertTrue(urahara.interact(visitor, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                    && TestPlayers.dialogues(visitor).size() == 2, "Urahara talked to a soul 9 blocks off");
            visitor.teleportTo(urahara.getX() + 2.0, urahara.getY(), urahara.getZ());
            visitor.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(urahara.interact(visitor, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                    && TestPlayers.dialogues(visitor).size() == 2, "Urahara talked to a spectator");
        } finally {
            urahara.discard();
        }
        helper.succeed();
    }

    // ---- Helpers ----

    /** The visitor talks to him: his dialogue opens in this view, its one note an aside of that view's. */
    private static void talks(GameTestHelper helper, KisukeUrahara urahara, ServerPlayer visitor, DialogueView view,
                              int talks) {
        boolean opened = urahara.interact(visitor, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS;
        List<OpenDialoguePayload> shown = TestPlayers.dialogues(visitor);
        OpenDialoguePayload last = shown.isEmpty() ? null : shown.get(shown.size() - 1);
        String aside = "screen.bleach.dialogue." + urahara.dialogueKey() + "." + view.key() + ".aside";
        helper.assertTrue(opened && shown.size() == talks && last.entityId() == urahara.getId() && last.view() == view
                && last.notes().size() == 1 && last.notes().get(0).getContents() instanceof TranslatableContents note
                && (note.getKey().equals(aside + 1) || note.getKey().equals(aside + 2)),
                "talk " + talks + " opened " + shown + ", not " + view + " with an aside");
    }

    /** A keeper made from a template's data standing here, facing its door before the turn; not yet in the world. */
    private static KisukeUrahara fromTemplate(ServerLevel level, Vec3 at) {
        CompoundTag data = new CompoundTag();
        data.putString("id", BleachMod.id("kisuke_urahara").toString());
        data.putBoolean("Spot", true);
        ListTag pos = new ListTag();
        Stream.of(at.x, at.y, at.z).map(DoubleTag::valueOf).forEach(pos::add);
        data.put("Pos", pos);
        ListTag rotation = new ListTag();
        Stream.of(180.0F, 0.0F).map(FloatTag::valueOf).forEach(rotation::add);
        data.put("Rotation", rotation);
        return (KisukeUrahara) EntityType.create(data, level).orElseThrow();
    }
}
