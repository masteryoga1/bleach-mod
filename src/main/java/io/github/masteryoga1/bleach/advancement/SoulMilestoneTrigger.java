package io.github.masteryoga1.bleach.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * One advancement criterion for every soul milestone, named in the advancement JSON:
 * {@code {"trigger": "bleach:soul_milestone", "conditions": {"milestone": "plus"}}}.
 * See {@link Milestones} for the names.
 */
public final class SoulMilestoneTrigger extends SimpleCriterionTrigger<SoulMilestoneTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String milestone) {
        trigger(player, instance -> instance.milestone().equals(milestone));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, String milestone)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("milestone").forGetter(TriggerInstance::milestone)
        ).apply(instance, TriggerInstance::new));
    }
}
