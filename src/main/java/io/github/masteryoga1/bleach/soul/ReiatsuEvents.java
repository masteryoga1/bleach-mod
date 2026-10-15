package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/** Reiatsu as a force: weak attackers barely scratch a strong soul, and released reiatsu crushes the weak. */
public final class ReiatsuEvents {
    private static final int PULSE_INTERVAL_TICKS = 10;
    private static final DustParticleOptions REIATSU_PARTICLE =
            new DustParticleOptions(new Vector3f(0.56F, 0.85F, 1.0F), 1.5F);

    private ReiatsuEvents() {
    }

    public static void register(IEventBus bus) {
        // After Zanjutsu and Hakuda have strengthened the hit.
        bus.addListener(EventPriority.LOW, ReiatsuEvents::onIncomingDamage);
        bus.addListener(ReiatsuEvents::onPlayerTick);
    }

    /** Reiatsu difference: a soul whose reiatsu dwarfs its attacker's shrugs off most of the hit. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!SoulLogic.hasSoul(victim) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        float multiplier = SoulLogic.reiatsuDamageMultiplier(SoulLogic.reiatsu(attacker), SoulLogic.reiatsu(victim));
        if (multiplier < 1.0F) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (!data.isReiatsuReleased()) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            data.setReiatsuReleased(false);
            return;
        }
        if (!SoulLogic.drain(player, data, SoulLogic.releaseDrainPerTick(data))) {
            data.setReiatsuReleased(false);
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_exhausted")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (player.tickCount % PULSE_INTERVAL_TICKS == 0) {
            pulse(player, data);
        }
    }

    /** One beat of the aura: everyone weak enough in range is weighed down, and the weakest flee. */
    private static void pulse(ServerPlayer player, SoulData data) {
        float radius = SoulLogic.pressureRadius(data);
        double radiusSqr = radius * radius;
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity != player && entity.isAlive() && entity.distanceToSqr(player) <= radiusSqr
                        && !(entity instanceof Player other && (other.isCreative() || other.isSpectator())));
        boolean crushedSomeone = false;
        for (LivingEntity target : nearby) {
            int weight = SoulLogic.applyPressure(target, data.maxReiryoku());
            if (weight > 0) {
                crushedSomeone = true;
            }
            if (weight >= 2 && target instanceof PathfinderMob mob) {
                flee(mob, player);
            }
        }
        if (crushedSomeone) {
            Milestones.grant(player, Milestones.CRUSHING_PRESSURE);
        }
        player.serverLevel().sendParticles(REIATSU_PARTICLE, player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.6, 0.9, 0.6, 0.02);
    }

    private static void flee(PathfinderMob mob, ServerPlayer from) {
        if (mob.getTarget() == from) {
            mob.setTarget(null);
        }
        Vec3 away = DefaultRandomPos.getPosAway(mob, 10, 5, from.position());
        if (away != null) {
            mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
        }
    }

    /** Called when the player presses the release key. The server decides whether it can happen. */
    public static void toggleRelease(ServerPlayer player) {
        SoulData data = SoulLogic.get(player);
        if (data.isReiatsuReleased()) {
            data.setReiatsuReleased(false);
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_sealed")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !SoulLogic.canRelease(data)) {
            player.displayClientMessage(Component.translatable("message.bleach.reiatsu_too_weak")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        data.setReiatsuReleased(true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.bleach.reiatsu_released")
                .withStyle(ChatFormatting.AQUA), true);
    }
}
