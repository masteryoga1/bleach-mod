package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A Vasto Lorde: the highest class of Menos Grande, as small as a person and stronger than a captain. It is no
 * {@link Menos}, so none of their cap, bait or lifetime rules is its, and no Menos count, arrival or meal takes it (see
 * {@link HollowKind#isVasto}). It hunts and strikes as any Hollow does, with its wits about it, and released reiatsu
 * never frightens it off. Its health and blow are the server's to set; its armour is the heaviest of any Hollow's,
 * toughened against heavy blows. It is no sinner and never saved, so it goes with its chunk, or through a tear for lack
 * of prey. Only a command brings one yet: {@code /summon}, or {@code /bleach garganta}, whose tear brings it alone.
 */
public class VastoLorde extends Hollow {
    /** How much of a heavy blow its armour still turns, on top of the armour itself. */
    public static final double TOUGHNESS = 2.0;

    public VastoLorde(EntityType<? extends VastoLorde> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.VASTO_LORDE.attributes().add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS);
    }

    @Override
    public HollowKind kind() {
        return HollowKind.VASTO_LORDE;
    }

    /** Its health and the damage of its blow are the config's ([bosses.vasto_lorde]). */
    @Override
    protected ConfigBases configBases() {
        return new ConfigBases(BleachConfig.get(BleachConfig.VASTO_LORDE_HEALTH),
                BleachConfig.get(BleachConfig.VASTO_LORDE_DAMAGE));
    }

    /** It goes when it is killed, leaves for lack of prey or its chunk unloads, never because players are far. */
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
}
