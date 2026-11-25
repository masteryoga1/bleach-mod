package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * Grand Fisher: a great Hollow, fur-covered and rodent-like, with red, bird-like claws on its hands and feet, and a boss.
 * It walks, hunts and bites as any Hollow does, its health and the damage of its blow the server's to set; its bar shows
 * to the souls about it who see spirits. It hides its reiatsu: its own cries never reach the shimmer band, and none
 * carries further than a mob's sounds do. It is no sinner. It is never saved, and for now comes only by command, as no
 * Garganta brings it ({@link HollowKind#comesByTear}).
 */
public class GrandFisher extends Hollow {
    private final BossBar bar = new BossBar(this, "bossbar.bleach.grand_fisher", BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);

    public GrandFisher(EntityType<? extends GrandFisher> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.GRAND_FISHER.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.GRAND_FISHER;
    }

    /** Its health and the damage of its blow are the config's ([bosses.grand_fisher]). */
    @Override
    protected ConfigBases configBases() {
        return new ConfigBases(BleachConfig.get(BleachConfig.GRAND_FISHER_HEALTH),
                BleachConfig.get(BleachConfig.GRAND_FISHER_DAMAGE));
    }

    /** Server side, every tick, so it runs with NoAI too: its bar, last, so the bar shows the health this tick left. */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            bar.tick();
        }
    }

    /** A soul that stops seeing it loses its bar at once. */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.forget(player);
    }

    /** Its bar. */
    public BossBar bossBar() {
        return bar;
    }

    /** It goes when it is killed, or leaves for lack of prey, never because players are far. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Killed or purified by a player's blow: that player has brought Grand Fisher down. */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // A cancelled death leaves it alive, and a second call does nothing: only a real first death counts.
        if (!wasDead && dead && source.getEntity() instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.GRAND_FISHER);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GRAND_FISHER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GRAND_FISHER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GRAND_FISHER_DEATH.get();
    }
}
