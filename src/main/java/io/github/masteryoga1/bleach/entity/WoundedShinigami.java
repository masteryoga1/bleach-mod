package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A Shinigami hurt by a Hollow, crouched where she fell. A living Human who can see her may take
 * her power and become a Substitute; after that, or when no one comes, she slips away.
 */
public final class WoundedShinigami extends ShinigamiNpc {
    private static final float REIRYOKU = 150.0F;
    /** How long she waits for someone, in ticks: five minutes. */
    public static final int DEFAULT_LIFETIME = 6000;
    /** Talking to her keeps her for at least this long, so a visitor has time to decide. */
    private static final int LIFETIME_AFTER_TALKING = 1200;
    /** How long she lingers after giving her power away. */
    public static final int LIFETIME_AFTER_GIVING = 60;
    private static final double DEPARTURE_MESSAGE_RANGE = 32.0;
    private static final float WOUNDED_HEALTH = 12.0F;

    private int lifetime = DEFAULT_LIFETIME;
    private boolean gavePower;

    public WoundedShinigami(EntityType<? extends WoundedShinigami> type, Level level) {
        super(type, level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setHealth(WOUNDED_HEALTH);
        return data;
    }

    /**
     * She offers her power to a Human who can take it. An awakened Quincy, who cannot, she sends away;
     * anyone else finds her resting.
     */
    @Override
    public DialogueView viewFor(SoulData visitor, long now) {
        DialogueView common = commonView(visitor);
        if (common != null) {
            return common;
        }
        if (!gavePower && ShinigamiLogic.canReceivePower(visitor)) {
            return DialogueView.OFFER_POWER;
        }
        if (QuincyLogic.blocksShinigamiPower(visitor) && visitor.state() == SoulState.HUMAN
                && !ShinigamiLogic.hasShinigamiPower(visitor)) {
            return DialogueView.QUINCY;
        }
        return DialogueView.RESTING;
    }

    @Override
    public String dialogueKey() {
        return "wounded";
    }

    @Override
    protected float soulReiryoku() {
        return REIRYOKU;
    }

    /** Hurt as she is, she cannot keep a Plus here, so she sends it on. */
    @Override
    protected boolean sendsOn() {
        return true;
    }

    @Override
    protected void onDialogueOpened() {
        lifetime = Math.max(lifetime, LIFETIME_AFTER_TALKING);
    }

    public int lifetime() {
        return lifetime;
    }

    public void setLifetime(int ticks) {
        lifetime = ticks;
    }

    public boolean gavePower() {
        return gavePower;
    }

    /** She has given her power away: nothing more to offer, and she leaves shortly. */
    public void markGavePower() {
        gavePower = true;
        lifetime = Math.min(lifetime, LIFETIME_AFTER_GIVING);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (getPose() != Pose.CROUCHING) {
            setPose(Pose.CROUCHING);
        }
        if (--lifetime <= 0 && isAlive()) {
            depart();
        }
    }

    private void depart() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.8, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL,
                    1.0F, 1.0F);
            Player nearest = serverLevel.getNearestPlayer(getX(), getY(), getZ(), DEPARTURE_MESSAGE_RANGE,
                    entity -> entity instanceof Player player && SoulLogic.hasSoul(player)
                            && SoulLogic.isAware(SoulLogic.get(player)));
            if (nearest != null) {
                nearest.displayClientMessage(Component.translatable("message.bleach.wounded_departs", getDisplayName()), true);
            }
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
        tag.putBoolean("GavePower", gavePower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Lifetime")) {
            lifetime = tag.getInt("Lifetime");
        }
        gavePower = tag.getBoolean("GavePower");
    }
}
