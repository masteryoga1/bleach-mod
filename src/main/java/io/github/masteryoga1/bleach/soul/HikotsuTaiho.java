package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Hikotsu Taihō, Hihiō Zabimaru's blast: the reiatsu its wielder gathers on the technique key, as for a Getsuga Tenshō
 * and at its cost and cooldown ({@code ShikaiEvents.gather}, {@code ShikaiEvents.fire}), goes out of the serpent's
 * jaws as a red beam. The serpent lashes out to {@link #HEAD_AHEAD} blocks before its wielder's eyes, and the beam goes
 * on from its head along the wielder's look: a Cero in every rule it keeps ({@link CeroBeam#fire} with {@link #KIND}),
 * on PvP, teams and pets, walls, shields, wards and a Dankū, through every creature in its way and bursting where it
 * meets a block, which it never breaks. It strikes nothing nearer the wielder than the head.
 */
public final class HikotsuTaiho {
    /** Its damage type, which purifies a Hollow it kills, as a Getsuga Tenshō does. */
    public static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("hikotsu_taiho"));
    /** A loud blast, on through every creature in its way. */
    public static final CeroBeam.Kind KIND = new CeroBeam.Kind(DAMAGE, false, Integer.MAX_VALUE);
    /** How far before its wielder's eyes the serpent's head opens its jaws, wall or no. */
    public static final double HEAD_AHEAD = 3.0;
    /** How far short of a wall right before the face the head stops. */
    private static final double WALL_MARGIN = 0.5;
    private static final float RADIUS = 0.75F;
    private static final float BLAST_RADIUS = 2.0F;
    private static final float BLAST_SHARE = 0.5F;
    private static final float KNOCKBACK = 0.6F;
    /** How long the beam is seen, in ticks. */
    private static final int TRACE_TICKS = 10;

    private HikotsuTaiho() {
    }

    /**
     * Where the serpent's head opens its jaws: {@link #HEAD_AHEAD} blocks along the wielder's look, or short of a wall
     * in the way, so the beam never starts on its far side.
     */
    public static Vec3 head(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getLookAngle();
        Vec3 ahead = eye.add(view.scale(HEAD_AHEAD));
        BlockHitResult wall = SpiritClip.clip(player.level(), new ClipContext(eye, ahead, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).block();
        if (wall.getType() == HitResult.Type.MISS) {
            return ahead;
        }
        return eye.add(view.scale(Math.max(0.0, wall.getLocation().distanceTo(eye) - WALL_MARGIN)));
    }

    /**
     * Looses Hikotsu Taihō of this charge (0 to 1), already paid for, in the blade's colour: the serpent lashes out to
     * its {@link #head}, striking nothing on its way, and the blast goes on from there, seen for {@link #TRACE_TICKS}
     * ticks. Null, loosing nothing, off a server.
     */
    @Nullable
    public static CeroBeam.Shot fire(Player player, SoulData data, float charge, int colour) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 head = head(player);
        ZabimaruLash.serpent(level, player, head);
        // The blast, not the wielder's hand, is what a shield meets, as with a Bala.
        Marker jaws = new Marker(EntityType.MARKER, level);
        jaws.setPos(head);
        CeroBeam.Shot shot = CeroBeam.fire(level, player, jaws, head, player.getLookAngle(), spec(data, charge, colour),
                KIND);
        // Drawn whole from the jaws, as no one's own, so the wielder sees it start at the head too.
        CeroBeam.trace(level, null, shot, RADIUS, colour, TRACE_TICKS);
        return shot;
    }

    /** The blast at this charge: as hard as {@link ZanpakutoLogic#hikotsuTaihoDamage} gives, as far as the config's. */
    private static CeroBeam.Spec spec(SoulData data, float charge, int colour) {
        return new CeroBeam.Spec(ZanpakutoLogic.hikotsuTaihoDamage(data, charge),
                BleachConfig.get(BleachConfig.BANKAI_SERPENT_BLAST_RANGE), RADIUS, BLAST_RADIUS, BLAST_SHARE, 0, KNOCKBACK,
                colour, 1, 0, false);
    }
}
