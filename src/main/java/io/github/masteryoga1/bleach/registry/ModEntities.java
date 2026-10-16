package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BleachMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AcademyInstructor>> ACADEMY_INSTRUCTOR =
            ENTITY_TYPES.register("academy_instructor", () -> EntityType.Builder
                    .<AcademyInstructor>of(AcademyInstructor::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(BleachMod.id("academy_instructor").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<WoundedShinigami>> WOUNDED_SHINIGAMI =
            ENTITY_TYPES.register("wounded_shinigami", () -> EntityType.Builder
                    .<WoundedShinigami>of(WoundedShinigami::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(BleachMod.id("wounded_shinigami").toString()));

    private ModEntities() {
    }

    /** Mod bus: every living entity type needs its attributes. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ACADEMY_INSTRUCTOR.get(), ShinigamiNpc.createAttributes().build());
        event.put(WOUNDED_SHINIGAMI.get(), ShinigamiNpc.createAttributes().build());
    }
}
