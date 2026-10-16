package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.Hexapodus;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HoundHollow;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.SerpentHollow;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's entity types: Shinigami NPCs, Hollows, Pluses and the Garganta. */
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

    // ---- Hollows (see HollowKind for their stats) ----

    public static final DeferredHolder<EntityType<?>, EntityType<SpiderHollow>> SPIDER_HOLLOW =
            ENTITY_TYPES.register("spider_hollow", () -> EntityType.Builder
                    .<SpiderHollow>of(SpiderHollow::new, MobCategory.MONSTER)
                    .sized(1.6F, 1.0F)
                    .eyeHeight(0.75F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(BleachMod.id("spider_hollow").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<Hexapodus>> HEXAPODUS =
            ENTITY_TYPES.register("hexapodus", () -> EntityType.Builder
                    .<Hexapodus>of(Hexapodus::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F)
                    .eyeHeight(0.65F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(BleachMod.id("hexapodus").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<HoundHollow>> HOUND_HOLLOW =
            ENTITY_TYPES.register("hound_hollow", () -> EntityType.Builder
                    .<HoundHollow>of(HoundHollow::new, MobCategory.MONSTER)
                    .sized(1.3F, 1.25F)
                    .eyeHeight(1.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(BleachMod.id("hound_hollow").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<SerpentHollow>> SERPENT_HOLLOW =
            ENTITY_TYPES.register("serpent_hollow", () -> EntityType.Builder
                    .<SerpentHollow>of(SerpentHollow::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.4F)
                    .eyeHeight(2.1F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build(BleachMod.id("serpent_hollow").toString()));

    /** The first Hollow. Never saved: if the world stops mid-hunt, it has simply gone, and comes back later. */
    public static final DeferredHolder<EntityType<?>, EntityType<FishboneD>> FISHBONE_D =
            ENTITY_TYPES.register("fishbone_d", () -> EntityType.Builder
                    .<FishboneD>of(FishboneD::new, MobCategory.MONSTER)
                    .sized(1.6F, 3.2F)
                    .eyeHeight(2.7F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(10)
                    .build(BleachMod.id("fishbone_d").toString()));

    // ---- Pluses and the Garganta ----

    public static final DeferredHolder<EntityType<?>, EntityType<PlusSpirit>> PLUS =
            ENTITY_TYPES.register("plus", () -> EntityType.Builder
                    .<PlusSpirit>of(PlusSpirit::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build(BleachMod.id("plus").toString()));

    /** A tear in the sky that Hollows come through. It lasts a few seconds and is never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<GargantaRift>> GARGANTA =
            ENTITY_TYPES.register("garganta", () -> EntityType.Builder
                    .<GargantaRift>of(GargantaRift::new, MobCategory.MISC)
                    .sized(2.0F, 4.0F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build(BleachMod.id("garganta").toString()));

    private ModEntities() {
    }

    /** The entity type each kind of Hollow is registered as. */
    public static EntityType<? extends Hollow> hollowType(HollowKind kind) {
        return switch (kind) {
            case SPIDER -> SPIDER_HOLLOW.get();
            case HEXAPODUS -> HEXAPODUS.get();
            case HOUND -> HOUND_HOLLOW.get();
            case SERPENT -> SERPENT_HOLLOW.get();
            case FISHBONE_D -> FISHBONE_D.get();
        };
    }

    /** Mod bus: every living entity type needs its attributes. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ACADEMY_INSTRUCTOR.get(), ShinigamiNpc.createAttributes().build());
        event.put(WOUNDED_SHINIGAMI.get(), ShinigamiNpc.createAttributes().build());
        event.put(SPIDER_HOLLOW.get(), SpiderHollow.createAttributes().build());
        event.put(HEXAPODUS.get(), Hexapodus.createAttributes().build());
        event.put(HOUND_HOLLOW.get(), HoundHollow.createAttributes().build());
        event.put(SERPENT_HOLLOW.get(), SerpentHollow.createAttributes().build());
        event.put(FISHBONE_D.get(), FishboneD.createAttributes().build());
        event.put(PLUS.get(), PlusSpirit.createAttributes().build());
    }
}
