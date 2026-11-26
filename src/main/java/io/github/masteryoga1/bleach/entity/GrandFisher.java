package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.BossLedger;
import io.github.masteryoga1.bleach.soul.GrandFisherComing;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Grand Fisher: a great Hollow, fur-covered and rodent-like, with red, bird-like claws on its hands and feet, and a boss.
 * It walks, hunts and bites as any Hollow does, its health and the damage of its blow the server's to set; its bar shows
 * to the souls about it who see spirits. It hides its reiatsu: its own cries never reach the shimmer band, and none
 * carries further than a mob's sounds do. It is no sinner. It is never saved, and no Garganta brings it
 * ({@link HollowKind#comesByTear}): it bursts out of the ground beside its lure, laid first to wait for its prey
 * ({@code GrandFisherComing}), or comes by {@code /summon}, and leaves for lack of prey back into the ground.
 *
 * <p>On its first tick it grows its lure ({@link GrandFisherLure}) on a stalk from its brow, unless one was linked to it
 * first, and it never grows a second: a lure cut off, faded or slain leaves it with that lure or none.
 *
 * <p>While a lure of its lives, on her stalk or cut off, no blow takes it below the config's share of its health (a
 * {@link BossFloor}); there it escapes into her, as canon's does (Ch. 21): she runs off and fades, and its body goes with
 * no death, so it is credited to no one and away only {@code escape_days}. With no lure left it dies as any Hollow.
 */
public class GrandFisher extends Hollow implements BossFloor.Floored {
    /** Where the stalk leaves its brow, in blocks at size 1: this high, and this far ahead along its head's turn. */
    public static final double BROW_UP = 4.1;
    public static final double BROW_FORWARD = 1.0;
    /** How far past the front of its body its lure grows. */
    private static final double LURE_AHEAD = 1.0;

    private final BossBar bar = new BossBar(this, "bossbar.bleach.grand_fisher", BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);
    /** Its lure, once grown or linked, and whether it has had its one. Server side, and never saved. */
    @Nullable
    private GrandFisherLure lure;
    private boolean lureTaken;
    /** Whether it has escaped into its lure, its body to go on its next tick. Server side. */
    private boolean escaped;

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

    /**
     * Server side, every tick, so it runs with NoAI too: its lure grown on its first tick, and its bar, last, so the bar
     * shows the health this tick left. Escaped into its lure, its body breaks up into smoke only the aware see, and goes.
     */
    @Override
    public void tick() {
        if (escaped && level() instanceof ServerLevel server && !isRemoved()) {
            SpiritSenses.particles(server, ParticleTypes.SQUID_INK, getX(), getY() + getBbHeight() * 0.5, getZ(), 24,
                    getBbWidth() * 0.5, getBbHeight() * 0.5, getBbWidth() * 0.5, 0.02);
            discard();
            return;
        }
        super.tick();
        if (level() instanceof ServerLevel server) {
            if (!lureTaken && isAlive() && !isRemoved()) {
                Vec3 at = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(getBbWidth() * 0.5 + LURE_AHEAD));
                if (!server.noCollision(ModEntities.GRAND_FISHER_LURE.get().getSpawnAABB(at.x, at.y, at.z))) {
                    at = position();
                }
                GrandFisherLure grown = GrandFisherLure.grow(server, at, getYRot());
                lureTaken = true;
                if (grown != null) {
                    takeLure(grown);
                }
            }
            bar.tick();
        }
    }

    /** Links this lure to it as its own, in place of growing one: its first tick's, or one laid first to wait for it. */
    public void takeLure(GrandFisherLure lure) {
        this.lure = lure;
        lureTaken = true;
        lure.attachTo(this);
    }

    /** Its lure while she lives, on her stalk or cut off; null once she is dying or gone. Server side. */
    @Nullable
    public GrandFisherLure lure() {
        return lure != null && lure.isAlive() ? lure : null;
    }

    /**
     * The config's share of its health while a lure of its lives, on her stalk or cut off, and once it has escaped into
     * her, so no later blow in the same tick kills a body already gone; none once she is gone and it has not.
     */
    @Override
    public float floor() {
        return escaped || lure() != null
                ? (float) (getMaxHealth() * BleachConfig.get(BleachConfig.GRAND_FISHER_FLOOR_SHARE)) : 0.0F;
    }

    /**
     * At its floor it escapes into its lure, once: the server marks it away {@code escape_days}, beaten by no one, and she
     * runs off with it; its body goes on its next tick.
     */
    @Override
    public void atFloor(DamageSource source) {
        GrandFisherLure into = lure();
        if (escaped || into == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        escaped = true;
        MinecraftServer host = server.getServer();
        BossLedger.get(host).markAway(GrandFisherComing.ID, BossLedger.now(host),
                BleachConfig.get(BleachConfig.GRAND_FISHER_ESCAPE_DAYS), List.of());
        into.flee();
    }

    /** Where the stalk leaves its brow at this partial tick, on both sides, along its head's turn. */
    public Vec3 brow(float partialTick) {
        float yaw = Mth.rotLerp(partialTick, yHeadRotO, yHeadRot) * Mth.DEG_TO_RAD;
        double ahead = BROW_FORWARD * getScale();
        return getPosition(partialTick).add(-Mth.sin(yaw) * ahead, BROW_UP * getScale(), Mth.cos(yaw) * ahead);
    }

    /**
     * Moved to another world by command, it comes there as a copy of itself: the copy keeps that it had its one lure, so
     * grows none, and the lure left behind, finding no body, is cut off.
     */
    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof GrandFisher old) {
            lureTaken = old.lureTaken;
        }
    }

    /** The ground breaks about its feet, as it bursts out or sinks back, and it laughs: seen and heard by the aware. */
    public void breakGround(ServerLevel server) {
        BlockParticleOption ground = new BlockParticleOption(ParticleTypes.BLOCK,
                server.getBlockState(BlockPos.containing(getX(), getY() - 0.5, getZ())));
        SpiritSenses.particles(server, ground, getX(), getY() + 0.25, getZ(), 60, getBbWidth() * 0.5, 0.25,
                getBbWidth() * 0.5, 0.15);
        playSound(ModSounds.GRAND_FISHER_AMBIENT.get(), 2.0F, 1.0F);
    }

    /** It leaves for lack of prey as it came, back into the ground, never by a tear; its lure fades with it. */
    @Override
    protected void leave() {
        if (level() instanceof ServerLevel server) {
            GrandFisherLure own = lure();
            if (own != null) {
                own.fadeNow();
            }
            breakGround(server);
            discard();
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
