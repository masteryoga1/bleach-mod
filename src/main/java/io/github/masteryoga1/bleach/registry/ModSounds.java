package io.github.masteryoga1.bleach.registry;

import io.github.masteryoga1.bleach.BleachMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's sound events. Their files are vanilla sounds, pitched and spread out in
 * {@code assets/bleach/sounds.json}. A Hollow's howl, a Shrieker's cry and its ringing tongue, the Gate of Hell,
 * Fishbone D's roar, a Menos Grande's cries, a Cero, the tearing sky, a Shikai release, a loosed Getsuga Tenshō, a
 * Quincy's awakening and the great Quincy arts (Letzt Stil, the power's return, Heizen, Sprenger and Vollständig)
 * carry much further than a normal mob's sounds, so they are fixed-range events; the rest reach as far as their
 * volume takes them, as usual.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, BleachMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_AMBIENT = fixed("entity.hollow.ambient", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_HURT = variable("entity.hollow.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_DEATH = variable("entity.hollow.death");
    /** A blow to a Hollow's mask, its weak point. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MASK_CRACK = variable("entity.hollow.mask_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHBONE_D_ROAR = fixed("entity.fishbone_d.roar", 64.0F);
    /** The sky tearing open as a Garganta forms. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GARGANTA_OPEN = fixed("entity.garganta.open", 64.0F);
    /** The soul pager's beep, played only to its holder. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DENREISHINKI_PING = variable("item.denreishinki.ping");
    /** The Asauchi whispering as the bond passes another quarter. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ZANPAKUTO_WHISPER = variable("item.zanpakuto.whisper");
    /** A soul settling into Jinzen. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ZANPAKUTO_JINZEN = variable("item.zanpakuto.jinzen");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZANPAKUTO_RELEASE = fixed("item.zanpakuto.release", 32.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> ZANPAKUTO_SEAL = variable("item.zanpakuto.seal");
    /** Zabimaru's segments whipping out and back. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ZABIMARU_LASH = variable("item.zabimaru.lash");
    /** Reiatsu gathering in the blade for a Getsuga Tenshō. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GETSUGA_GATHER = variable("entity.getsuga_tensho.gather");
    public static final DeferredHolder<SoundEvent, SoundEvent> GETSUGA_FIRE = fixed("entity.getsuga_tensho.fire", 48.0F);
    /** Reishi gathering into the Quincy Cross as a draw begins. */
    public static final DeferredHolder<SoundEvent, SoundEvent> QUINCY_CROSS_DRAW = variable("item.quincy_cross.draw");
    /** A Quincy's power awakening, or growing by a tier. */
    public static final DeferredHolder<SoundEvent, SoundEvent> QUINCY_AWAKEN = fixed("item.quincy_cross.awaken", 24.0F);
    /** Reishi slipping away from a Cross that cannot hold it yet, or from a shot too weak to fire. */
    public static final DeferredHolder<SoundEvent, SoundEvent> QUINCY_CROSS_FIZZLE = variable("item.quincy_cross.fizzle");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEILIG_PFEIL_FIRE = variable("entity.heilig_pfeil.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEILIG_PFEIL_HIT = variable("entity.heilig_pfeil.hit");
    /** The rush of reishi under a Quincy's feet. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HIRENKYAKU = variable("player.hirenkyaku");
    /** Reishi flooding the blood as Blut Vene or Arterie comes on, and ebbing as it ends. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLUT = variable("player.blut");
    /** The pounding heart of a Quincy deep in Hollow poison, played only to them. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_POISON = variable("player.hollow_poison");
    /** The Cross humming once a Letzt Stil draw is charged, or the rite has been held long enough. */
    public static final DeferredHolder<SoundEvent, SoundEvent> QUINCY_CROSS_CHARGED = variable("item.quincy_cross.charged");
    /** The Sanrei Glove torn from the hand. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SANREI_GLOVE_TEAR = variable("item.sanrei_glove.tear");
    /** A Quincy's power flaring up as Letzt Stil begins. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_LETZT_STIL = fixed("player.letzt_stil", 32.0F);
    /** The power burning out into the seal. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_QUINCY_SEALED = variable("player.quincy_sealed");
    /** The sealed power coming back as Ginrei Kojaku. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_QUINCY_RESTORED = fixed("player.quincy_restored", 24.0F);
    /** A blade of reishi forming on a Seele Schneider hilt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEELE_SCHNEIDER_FORM = variable("item.seele_schneider.form");
    /** A fired or planted Seele Schneider sticking fast. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEELE_SCHNEIDER_STICK = variable("entity.seele_schneider.stick");
    /** Reishi running into an empty Gintō. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GINTO_FILL = variable("item.ginto.fill");
    /** A Gintō shattering as its spell is cast. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GINTO_CAST = variable("item.ginto.cast");
    /** Heizen's pillar of light rising. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HEIZEN = fixed("entity.ginto.heizen", 24.0F);
    /** Sprenger setting off the five blades. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRENGER = fixed("entity.ginto.sprenger", 32.0F);
    /** Licht Regen loosing its volley. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LICHT_REGEN = variable("player.licht_regen");
    /** Wings of reishi unfolding as Vollständig is taken. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLAYER_VOLLSTANDIG = fixed("player.vollstandig", 32.0F);
    /** Sklaverei starting to draw in the reishi around its user. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SKLAVEREI = variable("player.sklaverei");
    /** A Hollow player devouring a soul, and gnawing its own chain. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_DEVOUR = variable("player.hollow.devour");
    /** The heartbeat of soul hunger, played to the hungry Hollow alone. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_PANG = variable("player.hollow.pang");
    /** A hungry Hollow smelling a soul, played to it alone. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLLOW_SCENT = variable("player.hollow.scent");
    public static final DeferredHolder<SoundEvent, SoundEvent> GILLIAN_AMBIENT = fixed("entity.gillian.ambient", 96.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> GILLIAN_HURT = variable("entity.gillian.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GILLIAN_DEATH = fixed("entity.gillian.death", 96.0F);
    /** A Gillian's tread, every few blocks it walks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GILLIAN_STEP = variable("entity.gillian.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> ADJUCHAS_AMBIENT = fixed("entity.adjuchas.ambient", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> ADJUCHAS_HURT = variable("entity.adjuchas.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ADJUCHAS_DEATH = variable("entity.adjuchas.death");
    /** A Shrieker's cry, heard from far off. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_AMBIENT = fixed("entity.shrieker.ambient", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_HURT = variable("entity.shrieker.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_DEATH = variable("entity.shrieker.death");
    /** The Gate of Hell rising out of the ground, its doors swinging open, the blade from the dark and what laughs there. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HELL_GATE_RISE = fixed("entity.hell_gate.rise", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> HELL_GATE_OPEN = fixed("entity.hell_gate.open", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> HELL_GATE_BLADE = fixed("entity.hell_gate.blade", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> HELL_GATE_LAUGH = fixed("entity.hell_gate.laugh", 48.0F);
    /** Its doors slamming on what it took, and the Gate crumbling away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HELL_GATE_SLAM = fixed("entity.hell_gate.slam", 48.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> HELL_GATE_CRUMBLE = fixed("entity.hell_gate.crumble", 48.0F);
    /** A Shrieker folding its wings to dive. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_SWOOP = variable("entity.shrieker.swoop");
    /** A Shrieker's wingbeat, every Shrieker.FLAP_TICKS while it flies. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_FLAP = variable("entity.shrieker.flap");
    /** A Shrieker loosing its leeches from its chest, and a leech fastening on a back. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_LOOSE = variable("entity.shrieker.loose");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_LEECH_CLING = variable("entity.shrieker_leech.cling");
    /** A Shrieker's tongue ringing, heard well beyond the fight, and a leech bursting as the ring ends. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_RING = fixed("entity.shrieker.ring", 32.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHRIEKER_LEECH_BURST = variable("entity.shrieker_leech.burst");
    /** Grand Fisher's low laugh. It hides its reiatsu, so its cries carry no further than a mob's. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAND_FISHER_AMBIENT = variable("entity.grand_fisher.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAND_FISHER_HURT = variable("entity.grand_fisher.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAND_FISHER_DEATH = variable("entity.grand_fisher.death");
    /** Its lure's sobbing, and her cry as she is hurt or cut off. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAND_FISHER_LURE_SOB =
            variable("entity.grand_fisher_lure.sob");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAND_FISHER_LURE_CRY =
            variable("entity.grand_fisher_lure.cry");
    /** A Vasto Lorde's rush along the ground. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VASTO_LORDE_DASH = variable("entity.vasto_lorde.dash");
    /** Runuganga rising out of the sand, and slumping back into it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNUGANGA_RISE = variable("entity.runuganga.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNUGANGA_SLUMP = variable("entity.runuganga.slump");
    /** Water soaking Runuganga's sand. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNUGANGA_SOAK = variable("entity.runuganga.soak");
    /** The sand about Runuganga sinking into his pit. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNUGANGA_PIT = variable("entity.runuganga.pit");
    /** Reiatsu gathering at a Menos's mouth for a Cero. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CERO_CHARGE = fixed("entity.cero.charge", 48.0F);
    /** A Cero let go. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CERO_FIRE = fixed("entity.cero.fire", 96.0F);
    /** The low thud of an Arrancar's Sonído, at both ends of the step. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIDO = variable("player.sonido");
    /** A blow ringing off an Arrancar's Hierro. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HIERRO = variable("player.hierro");
    /** An Arrancar's Pesquisa going out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PESQUISA = variable("player.pesquisa");
    /** The crack of a Bala fired. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BALA = variable("player.bala");
    /** An Arrancar cutting itself on its blade or claws for the blood of a Gran Rey Cero. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAN_REY_CERO_CUT = variable("player.gran_rey_cero.cut");
    /** A Gran Rey Cero let go, heard over the Cero's own roar and further. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAN_REY_CERO_FIRE =
            fixed("entity.gran_rey_cero.fire", 128.0F);
    /** The pillar of Negación coming down. */
    public static final DeferredHolder<SoundEvent, SoundEvent> NEGACION_DESCEND = fixed("entity.negacion.descend", 64.0F);
    /** The pillar of Negación carrying what it holds away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> NEGACION_DEPART = fixed("entity.negacion.depart", 64.0F);
    /** A Caja Negación closing round what it holds. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CAJA_NEGACION_CLOSE =
            fixed("entity.caja_negacion.close", 32.0F);

    private ModSounds() {
    }

    /** A sound heard out to {@code range} blocks whatever its volume. */
    private static DeferredHolder<SoundEvent, SoundEvent> fixed(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(BleachMod.id(name), range));
    }

    /** A sound whose range grows with its volume, 16 blocks at full volume. */
    private static DeferredHolder<SoundEvent, SoundEvent> variable(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(BleachMod.id(name)));
    }
}
