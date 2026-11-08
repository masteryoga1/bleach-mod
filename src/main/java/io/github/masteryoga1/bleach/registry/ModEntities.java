package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.entity.AcademyInstructor;
import io.github.masteryoga1.bleach.entity.Adjuchas;
import io.github.masteryoga1.bleach.entity.ArrancarNpc;
import io.github.masteryoga1.bleach.entity.CajaNegacion;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.DankuWall;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.GargantaRift;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.Gillian;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.Hexapodus;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.HoundHollow;
import io.github.masteryoga1.bleach.entity.InnerHollow;
import io.github.masteryoga1.bleach.entity.Kototsu;
import io.github.masteryoga1.bleach.entity.LetztPfeil;
import io.github.masteryoga1.bleach.entity.NegacionLight;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.entity.QuincyElder;
import io.github.masteryoga1.bleach.entity.QuincyHunter;
import io.github.masteryoga1.bleach.entity.QuincyNpc;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.Senkaimon;
import io.github.masteryoga1.bleach.entity.SerpentHollow;
import io.github.masteryoga1.bleach.entity.ShinigamiNpc;
import io.github.masteryoga1.bleach.entity.SpiderHollow;
import io.github.masteryoga1.bleach.entity.WoundedShinigami;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's entity types: Shinigami and Quincy NPCs, Hollows and the Menos Grande, Arrancar NPCs, a
 * Shinigami's inner Hollow, Pluses, the Garganta, zanpakutō techniques, the Cero, Negación's light and the
 * Caja Negación, Dankū's wall, the Senkaimon, and the Quincy's arrows, blades and Gintō spells.
 */
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

    // ---- Living Quincy (see QuincyNpc): seen by everyone, and seeing spirits themselves ----

    /**
     * The old Quincy who visits those of his blood: lessons, the Ransōtengai trial, and an arrow beside
     * the heart for a sealed power. He cannot be hurt. A visiting Elder is never saved; one placed by
     * an egg or {@code /summon} stays.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<QuincyElder>> QUINCY_ELDER =
            ENTITY_TYPES.register("quincy_elder", () -> EntityType.Builder
                    .<QuincyElder>of(QuincyElder::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(BleachMod.id("quincy_elder").toString()));

    /**
     * The proud young Quincy who turns up where Hollows come through and challenges souls to a
     * contest. Saved on the same terms as the Elder.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<QuincyHunter>> QUINCY_HUNTER =
            ENTITY_TYPES.register("quincy_hunter", () -> EntityType.Builder
                    .<QuincyHunter>of(QuincyHunter::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(BleachMod.id("quincy_hunter").toString()));

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

    /**
     * A Gillian, the commonest Menos Grande: fifteen blocks of black robe and a long white mask, with
     * two hit boxes of its own above its knees (see {@link Gillian.Part}). Never saved, and seen from
     * far away.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Gillian>> GILLIAN =
            ENTITY_TYPES.register("gillian", () -> EntityType.Builder
                    .<Gillian>of(Gillian::new, MobCategory.MONSTER)
                    .sized(Gillian.WIDTH, Gillian.HEIGHT)
                    .eyeHeight(Gillian.EYE_HEIGHT)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(16)
                    .build(BleachMod.id("gillian").toString()));

    /** An Adjuchas, a Menos Grande that hunts in packs. Never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<Adjuchas>> ADJUCHAS =
            ENTITY_TYPES.register("adjuchas", () -> EntityType.Builder
                    .<Adjuchas>of(Adjuchas::new, MobCategory.MONSTER)
                    .sized(2.0F, 2.5F)
                    .eyeHeight(1.9F)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(10)
                    .build(BleachMod.id("adjuchas").toString()));

    // ---- Arrancar ----

    /**
     * An Arrancar NPC: a Número, or a Privaron Espada with its Fracción, come through a Garganta. Saved, as
     * players' Arrancar are; a person's size, and not fire-proof.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ArrancarNpc>> ARRANCAR =
            ENTITY_TYPES.register("arrancar", () -> EntityType.Builder
                    .<ArrancarNpc>of(ArrancarNpc::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(BleachMod.id("arrancar").toString()));

    // ---- Visored ----

    /**
     * A Shinigami's inner Hollow, out of its host for their fight (see InnerHollow). It lives only while the fight
     * does, is never saved, and never spawns by itself.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<InnerHollow>> INNER_HOLLOW =
            ENTITY_TYPES.register("inner_hollow", () -> EntityType.Builder
                    .<InnerHollow>of(InnerHollow::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .noSave()
                    .clientTrackingRange(10)
                    .build(BleachMod.id("inner_hollow").toString()));

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

    // ---- Hollow techniques ----

    /**
     * A Cero: the red light a Menos gathers at its mouth and lets go as a beam. It lives a few seconds
     * at most and is never saved; clients follow its caster themselves while it gathers.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CeroBeam>> CERO_BEAM =
            ENTITY_TYPES.register("cero_beam", () -> EntityType.Builder
                    .<CeroBeam>of(CeroBeam::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(BleachMod.id("cero_beam").toString()));

    /**
     * Negación's pillar of light, which a Gillian calls down on a comrade, who rides it as it rises. It lasts
     * seconds and is never saved, so a player saved while riding it loads on foot; clients hear of every step
     * of its rise.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<NegacionLight>> NEGACION =
            ENTITY_TYPES.register("negacion", () -> EntityType.Builder
                    .<NegacionLight>of(NegacionLight::new, MobCategory.MISC)
                    .sized(0.9F, 2.0F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(BleachMod.id("negacion").toString()));

    /**
     * The Caja Negación's cage, closed round a creature with a Hollow hole: it holds it out of the world for a
     * while and sets it down unharmed. It is never saved; a held mob is saved on foot (see CajaNegacion).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<CajaNegacion>> CAJA_NEGACION =
            ENTITY_TYPES.register("caja_negacion", () -> EntityType.Builder
                    .<CajaNegacion>of(CajaNegacion::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build(BleachMod.id("caja_negacion").toString()));

    // ---- Kidō ----

    /**
     * Bakudō #81 Dankū: a wall of light a caster raises before it, which stops what is shot at it. Never saved, and
     * never summoned: a wall with no caster would only vanish.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DankuWall>> DANKU_WALL =
            ENTITY_TYPES.register("danku_wall", () -> EntityType.Builder
                    .<DankuWall>of(DankuWall::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(BleachMod.id("danku_wall").toString()));

    // ---- Zanpakutō techniques ----

    /**
     * Getsuga Tenshō's crescent, swung out from Zangetsu. It flies for a second or so and is never
     * saved; clients move it themselves between the server's position updates.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GetsugaTensho>> GETSUGA_TENSHO =
            ENTITY_TYPES.register("getsuga_tensho", () -> EntityType.Builder
                    .<GetsugaTensho>of(GetsugaTensho::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build(BleachMod.id("getsuga_tensho").toString()));

    /** Zabimaru's blade lashing out to a far blow and back. It is only for show, lasts a few ticks and is never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<ZabimaruLash>> ZABIMARU_LASH =
            ENTITY_TYPES.register("zabimaru_lash", () -> EntityType.Builder
                    .<ZabimaruLash>of(ZabimaruLash::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build(BleachMod.id("zabimaru_lash").toString()));

    // ---- Quincy techniques ----

    /**
     * A Heilig Pfeil, an arrow of reishi loosed from the Heilig Bogen. It is the size of a vanilla arrow
     * and tracked like one, flies straight for a second and a half, and is never saved.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<HeiligPfeil>> HEILIG_PFEIL =
            ENTITY_TYPES.register("heilig_pfeil", () -> EntityType.Builder
                    .<HeiligPfeil>of(HeiligPfeil::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .eyeHeight(0.13F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .noSave()
                    .build(BleachMod.id("heilig_pfeil").toString()));

    /**
     * A Seele Schneider loosed from Ginrei Kojaku, or planted in the ground. It flies as a Heilig Pfeil
     * does, then sticks where it lands to be picked up again or set off by Sprenger, so it is saved.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SeeleSchneiderPfeil>> SEELE_SCHNEIDER =
            ENTITY_TYPES.register("seele_schneider", () -> EntityType.Builder
                    .<SeeleSchneiderPfeil>of(SeeleSchneiderPfeil::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .eyeHeight(0.13F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build(BleachMod.id("seele_schneider").toString()));

    /**
     * Letzt Stil's charged shot: a great arrow that pierces ten and carves through soft blocks. It
     * lives a second or so and is never saved.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<LetztPfeil>> LETZT_PFEIL =
            ENTITY_TYPES.register("letzt_pfeil", () -> EntityType.Builder
                    .<LetztPfeil>of(LetztPfeil::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .eyeHeight(0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .noSave()
                    .build(BleachMod.id("letzt_pfeil").toString()));

    /**
     * A Gintō spell where it was cast: Gritz's light, Wolke's cloud, Heizen's pillar or Sprenger's
     * ring. It stays where it was cast, fades within seconds and is never saved.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GintoCast>> GINTO_CAST =
            ENTITY_TYPES.register("ginto_cast", () -> EntityType.Builder
                    .<GintoCast>of(GintoCast::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build(BleachMod.id("ginto_cast").toString()));

    // ---- Travel ----

    /**
     * A Senkaimon: the gate a Shinigami turns open between the Human World and Soul Society. Never saved, and never
     * summoned: a gate leads on from where its opener stood.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Senkaimon>> SENKAIMON =
            ENTITY_TYPES.register("senkaimon", () -> EntityType.Builder
                    .<Senkaimon>of(Senkaimon::new, MobCategory.MISC)
                    .sized(3.0F, 4.0F)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(BleachMod.id("senkaimon").toString()));

    /**
     * The Kōtotsu, which sweeps a Dangai lane: 8 blocks every way, moving every tick. Never saved, and never summoned: one
     * is sent as souls enter a lane.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Kototsu>> KOTOTSU =
            ENTITY_TYPES.register("kototsu", () -> EntityType.Builder
                    .<Kototsu>of(Kototsu::new, MobCategory.MISC)
                    .sized(8.0F, 8.0F)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(BleachMod.id("kototsu").toString()));

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
            case GILLIAN -> GILLIAN.get();
            case ADJUCHAS -> ADJUCHAS.get();
        };
    }

    /** Mod bus: every living entity type needs its attributes. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ACADEMY_INSTRUCTOR.get(), ShinigamiNpc.createAttributes().build());
        event.put(WOUNDED_SHINIGAMI.get(), ShinigamiNpc.createAttributes().build());
        event.put(QUINCY_ELDER.get(), QuincyNpc.createAttributes().build());
        event.put(QUINCY_HUNTER.get(), QuincyHunter.createHunterAttributes().build());
        event.put(SPIDER_HOLLOW.get(), SpiderHollow.createAttributes().build());
        event.put(HEXAPODUS.get(), Hexapodus.createAttributes().build());
        event.put(HOUND_HOLLOW.get(), HoundHollow.createAttributes().build());
        event.put(SERPENT_HOLLOW.get(), SerpentHollow.createAttributes().build());
        event.put(FISHBONE_D.get(), FishboneD.createAttributes().build());
        event.put(GILLIAN.get(), Gillian.createAttributes().build());
        event.put(ADJUCHAS.get(), Adjuchas.createAttributes().build());
        event.put(ARRANCAR.get(), ArrancarNpc.createAttributes().build());
        event.put(INNER_HOLLOW.get(), InnerHollow.createAttributes().build());
        event.put(PLUS.get(), PlusSpirit.createAttributes().build());
    }
}
