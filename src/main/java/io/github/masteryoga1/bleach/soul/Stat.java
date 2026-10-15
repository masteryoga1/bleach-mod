package io.github.masteryoga1.bleach.soul;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** The Zankensoki (the four Shinigami combat arts) plus Reiatsu Control, each levelled by use. */
public enum Stat implements StringRepresentable {
    /** Swordplay. */
    ZANJUTSU("zanjutsu"),
    /** Unarmed combat. */
    HAKUDA("hakuda"),
    /** Footwork and speed. */
    HOHO("hoho"),
    /** Demon arts. */
    KIDO("kido"),
    /** How finely the soul handles its own reiatsu. */
    REIATSU_CONTROL("reiatsu_control");

    public static final Codec<Stat> CODEC = StringRepresentable.fromEnum(Stat::values);

    private final String name;

    Stat(String name) {
        this.name = name;
    }

    public Component displayName() {
        return Component.translatable("stat.bleach." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
