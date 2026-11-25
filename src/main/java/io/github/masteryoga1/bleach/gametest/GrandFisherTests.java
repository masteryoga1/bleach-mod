package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.BossBar;
import io.github.masteryoga1.bleach.entity.GrandFisher;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Tests for Grand Fisher's body: its row and the rules that keep it from every tear, its strength from the config, and
 * its bar and advancement. Each test asks only about its own Grand Fisher and its own players, by object.
 */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrandFisherTests {
    private static final float EPSILON = 1.0E-3F;
    /** Enough reiatsu to draw any kind that comes by chance. */
    private static final float STRONG = 10_000.0F;
    /** The reiatsu of the Shinigami who purifies it: enough to see spirits. */
    private static final float AWARE = 40.0F;
    /** A blow that leaves it standing, and one that ends it. */
    private static final float BLOW = 20.0F;
    private static final float FATAL = 1000.0F;

    private GrandFisherTests() {
    }

    /**
     * The row: by name, never by chance or by any tear, no Menos; never saved, its size; one that skipped spawning takes
     * the config's health and damage on its first tick, whole; it is no sinner, and released reiatsu never frightens it
     * off. Its advancement and sounds.
     */
    @GameTest(template = "field")
    public static void grandFisherIsABossThatComesByNoTear(GameTestHelper helper) {
        HollowKind kind = HollowKind.GRAND_FISHER;
        helper.assertTrue(HollowKind.byName("grand_fisher").equals(Optional.of(kind)), "no kind is named grand_fisher");
        helper.assertTrue(kind.arrivalWeight == 0 && !kind.isMenos(), "Grand Fisher may come by chance, or is a Menos");
        helper.assertFalse(HollowLogic.arrivalPool(STRONG).contains(kind), "Grand Fisher is in the arrival pool");
        List<HollowKind> tearless = Arrays.stream(HollowKind.values()).filter(each -> !each.comesByTear()).toList();
        helper.assertTrue(tearless.equals(List.of(kind)), "the kinds no tear brings are " + tearless);

        EntityType<GrandFisher> type = ModEntities.GRAND_FISHER.get();
        helper.assertTrue(ModEntities.hollowType(kind) == type, "the Grand Fisher kind has the wrong entity type");
        helper.assertFalse(type.canSerialize(), "Grand Fisher is saved with the world");
        EntityDimensions size = type.getDimensions();
        helper.assertTrue(Math.abs(size.width() - 2.5F) < EPSILON && Math.abs(size.height() - 4.0F) < EPSILON
                && Math.abs(size.eyeHeight() - 3.2F) < EPSILON, "Grand Fisher is " + size);

        // Read as it joined the world, before its wits are taken: what joining gave it goes with its goals.
        GrandFisher fisher = helper.spawn(type, new Vec3(8.5, 2.0, 8.5));
        helper.assertFalse(ShriekerTests.frightens(fisher), "released reiatsu frightens Grand Fisher");
        fisher.removeFreeWill();
        helper.assertFalse(fisher.isSinner(), "Grand Fisher is a sinner");
        // Stale strength, as a summon with data or an old config would leave: its first tick puts the config's in.
        fisher.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
        fisher.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);

        helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(BleachMod.id("grand_fisher")) != null,
                "the Grand Fisher advancement did not load");
        List<DeferredHolder<SoundEvent, SoundEvent>> sounds = List.of(ModSounds.GRAND_FISHER_AMBIENT,
                ModSounds.GRAND_FISHER_HURT, ModSounds.GRAND_FISHER_DEATH);
        for (DeferredHolder<SoundEvent, SoundEvent> sound : sounds) {
            helper.assertTrue(sound.isBound(), "the sound " + sound.getId() + " is not registered");
        }

        helper.runAfterDelay(1, () -> {
            double health = BleachConfig.get(BleachConfig.GRAND_FISHER_HEALTH);
            double damage = BleachConfig.get(BleachConfig.GRAND_FISHER_DAMAGE);
            helper.assertTrue(fisher.getAttributeBaseValue(Attributes.MAX_HEALTH) == health
                            && fisher.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == damage,
                    "after its first tick Grand Fisher has " + fisher.getAttributeBaseValue(Attributes.MAX_HEALTH)
                            + " health and " + fisher.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + " damage");
            helper.assertTrue(Math.abs(fisher.getHealth() - health) < EPSILON,
                    "a whole Grand Fisher came to " + fisher.getHealth() + " of " + health + " health");
            fisher.discard();
            helper.succeed();
        });
    }

    /**
     * A still Grand Fisher: no tear brings one to the Shinigami, not even by command; its red bar shows to the Shinigami
     * by it, full; it falls with a blow on the next tick; the Shinigami's Asauchi purifies it, which brings Grand Fisher
     * down, and the bar comes down from everyone.
     */
    @GameTest(template = "field", timeoutTicks = 100)
    public static void grandFishersBarFallsAndItsPurifierBringsItDown(GameTestHelper helper) {
        ServerPlayer purifier = TestPlayers.join(helper, GameType.SURVIVAL, new Vec3(8.5, 2.0, 3.5));
        purifier.setData(SoulAttachments.SOUL, ShinigamiTests.soul(SoulState.SHINIGAMI, AWARE));
        HollowTests.arm(purifier, ModItems.ASAUCHI.get());
        List<Component> said = GateTests.run(helper.getLevel(),
                "bleach garganta " + purifier.getScoreboardName() + " grand_fisher");
        helper.assertTrue(said.stream().anyMatch(line -> GateTests.says(line, "commands.bleach.unknown_hollow", -1)),
                "/bleach garganta did not refuse grand_fisher: " + said.stream().map(Component::getString).toList());
        helper.assertFalse(GargantaEvents.openArrival(purifier, SoulLogic.get(purifier), HollowKind.GRAND_FISHER,
                helper.getLevel().getGameTime(), true), "a forced tear brought Grand Fisher");
        GrandFisher fisher = helper.spawnWithNoFreeWill(ModEntities.GRAND_FISHER.get(), 8, 2, 9);
        BossBar bar = fisher.bossBar();
        helper.startSequence()
                .thenWaitUntil(() -> GateTests.patiently(helper,
                        bar.players().stream().anyMatch(shown -> shown == purifier), "the Shinigami was not shown the bar"))
                .thenExecute(() -> {
                    BossEvent event = bar.event();
                    helper.assertTrue(event.getColor() == BossEvent.BossBarColor.RED
                                    && event.getOverlay() == BossEvent.BossBarOverlay.PROGRESS
                                    && event.getProgress() == 1.0F,
                            "the bar is " + event.getColor() + ", " + event.getOverlay() + " at " + event.getProgress());
                    fisher.hurt(helper.getLevel().damageSources().playerAttack(purifier), BLOW);
                    helper.assertTrue(fisher.isAlive() && fisher.getHealth() < fisher.getMaxHealth(),
                            "the blow left Grand Fisher at " + fisher.getHealth());
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    float progress = bar.event().getProgress();
                    helper.assertTrue(progress < 1.0F && Math.abs(progress - BossBar.progress(fisher)) < EPSILON,
                            "the bar is at " + progress + " for " + fisher.getHealth() + " of " + fisher.getMaxHealth());
                    fisher.invulnerableTime = 0;
                    fisher.hurt(helper.getLevel().damageSources().playerAttack(purifier), FATAL);
                    helper.assertTrue(fisher.isPurifying(), "the Asauchi did not purify Grand Fisher");
                    helper.assertTrue(SoulLogic.get(purifier).hunt().purified() == 1, "the purifier's count did not grow");
                    helper.assertTrue(DangaiTests.earned(purifier, "grand_fisher"),
                            "the purifier has not brought Grand Fisher down");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(bar.players().isEmpty(), "a dead Grand Fisher's bar is still up");
                    fisher.discard();
                })
                .thenSucceed();
    }
}
