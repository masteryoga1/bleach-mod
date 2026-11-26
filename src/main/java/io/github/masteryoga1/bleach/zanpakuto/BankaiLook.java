package io.github.masteryoga1.bleach.zanpakuto;

import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * What a soul wears for its Bankai, as everyone who sees it draws it: read from the blade in its main hand alone (its
 * owner, its stage and its model number, which every client is sent with the held stack), never from a payload or the
 * spirit's file, which no client is sent. A look belongs to a Bankai's model number, as a sheath does to a sealed or
 * Shikai blade's. Tensa Zangetsu's coat is the first; Hihiō Zabimaru's pelt is to come, by its own model number.
 */
public enum BankaiLook {
    /** Nothing: no blade of one's own in Bankai in hand, or a Bankai with no look of its own. */
    NONE(-1),
    /** Tensa Zangetsu's long black coat, its hem in tatters, by the model Zangetsu's file gives its Bankai. */
    TENSA_ZANGETSU(4);

    private final int model;

    BankaiLook(int model) {
        this.model = model;
    }

    /** The model number of the Bankai this look goes with, as its spirit's file gives it; -1 for {@link #NONE}. */
    public int model() {
        return model;
    }

    /** The look of a blade of this model number in Bankai, or {@link #NONE}. */
    public static BankaiLook forModel(int model) {
        for (BankaiLook look : values()) {
            if (look != NONE && look.model == model) {
                return look;
            }
        }
        return NONE;
    }

    /**
     * What this player wears for its Bankai: the look of its own blade in Bankai in its main hand, or {@link #NONE}
     * with no blade there, another's blade, a blade in Shikai or one only in the off hand. The same on the server and
     * on every client, as it reads only the held stack and the player's own id.
     */
    public static BankaiLook worn(Player player) {
        ItemStack held = player.getMainHandItem();
        ZanpakutoBlade blade = ZanpakutoItem.blade(held);
        if (!(held.getItem() instanceof ZanpakutoItem) || !blade.isOwnedBy(player.getUUID())
                || blade.stage() != ZanpakutoStage.BANKAI) {
            return NONE;
        }
        CustomModelData model = held.get(DataComponents.CUSTOM_MODEL_DATA);
        return model == null ? NONE : forModel(model.value());
    }
}
