package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GintoCast;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.entity.SpiritProjectiles;
import io.github.masteryoga1.bleach.quincy.GintoSpell;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Casting a filled Gintō: which spell, whether it can be cast, where it lands, and what it costs.
 * The reishi was poured in when the tube was filled, so a sealed or poisoned Quincy may still cast.
 * What a spell then does is {@link GintoCast}'s. Also the Seele Schneider formation that Sprenger
 * sets off, and the bind that comes when its fifth blade stands.
 */
public final class GintoSpells {
    /** Gritz's cast is only seen, for a moment, about the creature bound. */
    private static final int GRITZ_CAST_LIFE_TICKS = 20;
    private static final float GRITZ_CAST_RADIUS = 1.0F;
    /** Heizen's pillar and Sprenger's blast are over this soon after they strike. */
    private static final int STRIKE_CAST_LIFE_TICKS = 20;
    /** A Wolke cast in the air also slows the caster's own fall down onto it. */
    private static final int WOLKE_SLOW_FALLING_TICKS = 60;

    private GintoSpells() {
    }

    /**
     * Use of a filled Gintō, not sneaking, on the server: casts the chosen spell (see
     * {@link QuincyArtsLogic#selectedSpell}) if the soul knows it, holds enough tubes and the spell
     * finds what it needs. A cast uses the tubes up and starts the Gintō cooldown; a failed one keeps
     * them and starts nothing, with the reason on the action bar.
     *
     * @return whether the spell was cast
     */
    public static boolean cast(Player p, SoulData d, ItemStack tubes) {
        if (!(p.level() instanceof ServerLevel level)) {
            return false;
        }
        GintoSpell spell = QuincyArtsLogic.selectedSpell(d);
        String refusal = QuincyArtsLogic.castRefusal(d, spell);
        if (refusal != null) {
            tell(p, "message.bleach.ginto.unknown".equals(refusal)
                    ? Component.translatable(refusal, spell.displayName()) : Component.translatable(refusal));
            return false;
        }
        if (!p.hasInfiniteMaterials() && tubes.getCount() < spell.tubes()) {
            tell(p, Component.translatable("message.bleach.ginto.tubes", spell.displayName(), spell.tubes()));
            return false;
        }
        if (p.getCooldowns().isOnCooldown(tubes.getItem())) {
            return false;
        }
        // Never more than the Heilig Bogen's unboosted full draw: the tube holds what was poured in, not the form.
        float strength = QuincyLogic.fullDamage(d);
        boolean cast = switch (spell) {
            case GRITZ -> castGritz(level, p, strength);
            case WOLKE -> castWolke(level, p, strength);
            case HEIZEN -> castHeizen(level, p, strength);
            case SPRENGER -> castSprenger(level, p, strength);
        };
        if (!cast) {
            return false;
        }
        tubes.consume(spell.tubes(), p);
        p.getCooldowns().addCooldown(ModItems.GINTO.get(), BleachConfig.get(BleachConfig.GINTO_COOLDOWN_TICKS));
        SpiritSenses.play(p, ModSounds.GINTO_CAST.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        p.awardStat(Stats.ITEM_USED.get(ModItems.GINTO.get()));
        milestone(p, Milestones.GINTO);
        return true;
    }

    /** Gritz: binds the creature looked at, for less time against stronger reiatsu and far less on a boss. */
    private static boolean castGritz(ServerLevel level, Player p, float strength) {
        if (!(look(p, range()) instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
            tell(p, Component.translatable("message.bleach.ginto.no_target"));
            return false;
        }
        int ticks = QuincyArtsLogic.gritzTicks(SoulLogic.reiatsu(p), SoulLogic.reiatsu(target),
                target.getType().is(Tags.EntityTypes.BOSSES));
        target.addEffect(gritz(ticks), p);
        GintoCast.spawn(level, p, GintoSpell.GRITZ, target.getBoundingBox().getCenter(), GRITZ_CAST_RADIUS, 0,
                GRITZ_CAST_LIFE_TICKS, strength, null, List.of());
        return true;
    }

    /** Wolke: a cloud on the ground straight below (or at the feet, with none in reach) that breaks every fall onto it. */
    private static boolean castWolke(ServerLevel level, Player p, float strength) {
        Vec3 feet = p.position();
        BlockHitResult below = level.clip(new ClipContext(feet, feet.subtract(0.0, range(), 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, p));
        Vec3 centre = below.getType() == HitResult.Type.MISS ? feet : below.getLocation();
        GintoCast.spawn(level, p, GintoSpell.WOLKE, centre, (float) BleachConfig.get(BleachConfig.WOLKE_RADIUS), 0,
                BleachConfig.get(BleachConfig.WOLKE_TICKS), strength, null, List.of());
        if (!p.onGround()) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, WOLKE_SLOW_FALLING_TICKS, 0));
        }
        return true;
    }

    /** Heizen: a pillar rises under the creature looked at, or where the look meets a block. */
    private static boolean castHeizen(ServerLevel level, Player p, float strength) {
        HitResult hit = look(p, range());
        Vec3 centre;
        if (hit instanceof EntityHitResult entityHit) {
            centre = entityHit.getEntity().position();
        } else if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            centre = hit.getLocation();
        } else {
            tell(p, Component.translatable("message.bleach.ginto.no_target"));
            return false;
        }
        GintoCast.spawn(level, p, GintoSpell.HEIZEN, centre, (float) QuincyArtsLogic.HEIZEN_HALF_WIDTH,
                QuincyArtsLogic.HEIZEN_FUSE_TICKS, STRIKE_CAST_LIFE_TICKS, strength, null, List.of());
        return true;
    }

    /** Sprenger: sets off the five Seele Schneider the caster has standing in a pentagon nearby. */
    private static boolean castSprenger(ServerLevel level, Player p, float strength) {
        List<SeeleSchneiderPfeil> blades = formation(level, p.getUUID(), p.position());
        SprengerShape shape = blades.size() == SprengerShape.CORNERS ? SprengerShape.of(positions(blades)) : null;
        if (shape == null) {
            tell(p, Component.translatable("message.bleach.sprenger.no_formation"));
            return false;
        }
        GintoCast.spawn(level, p, GintoSpell.SPRENGER, new Vec3(shape.centreX(), shape.minY(), shape.centreZ()),
                (float) shape.meanRadius(), QuincyArtsLogic.SPRENGER_FUSE_TICKS, STRIKE_CAST_LIFE_TICKS, strength, shape,
                blades);
        milestone(p, Milestones.SPRENGER);
        return true;
    }

    /**
     * This owner's Seele Schneider standing stuck within reach of {@code around}, nearest first, at
     * most five: the blades a Sprenger would use. Matched on the blades' own saved owner, so blades
     * planted before a relog still count.
     */
    public static List<SeeleSchneiderPfeil> formation(Level level, UUID owner, Vec3 around) {
        double range = QuincyArtsLogic.SPRENGER_RANGE;
        List<SeeleSchneiderPfeil> found = new ArrayList<>(level.getEntitiesOfClass(SeeleSchneiderPfeil.class,
                new AABB(around, around).inflate(range),
                blade -> blade.isAlive() && blade.planted() && owner.equals(blade.ownerId())
                        && blade.distanceToSqr(around) <= range * range));
        found.sort(Comparator.comparingDouble(blade -> blade.distanceToSqr(around)));
        return List.copyOf(found.subList(0, Math.min(found.size(), SprengerShape.CORNERS)));
    }

    /**
     * One of an owner's blades just stuck. If it completes a pentagon with the nearest four of theirs,
     * and the owner knows Sprenger, the fifth blade binds every creature inside with Gritz, and the
     * owner is told the formation stands.
     */
    public static void onSeeleStuck(SeeleSchneiderPfeil blade) {
        UUID ownerId = blade.ownerId();
        if (!(blade.level() instanceof ServerLevel level) || ownerId == null || !(blade.getOwner() instanceof Player owner)
                || !SoulLogic.hasSoul(owner) || !QuincyArtsLogic.knowsSpell(SoulLogic.get(owner), GintoSpell.SPRENGER)) {
            return;
        }
        List<SeeleSchneiderPfeil> blades = formation(level, ownerId, blade.position());
        if (blades.size() < SprengerShape.CORNERS || !blades.contains(blade)) {
            return;
        }
        SprengerShape shape = SprengerShape.of(positions(blades));
        if (shape == null) {
            return;
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, reach(shape),
                e -> e != owner && e.isAlive() && shape.contains(e.position()) && SpiritProjectiles.mayStrike(owner, e))) {
            target.addEffect(gritz(QuincyArtsLogic.SPRENGER_BIND_TICKS), owner);
        }
        owner.displayClientMessage(Component.translatable("message.bleach.sprenger.formed").withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * What the player looks at within {@code range}: the first living creature they may strike, else
     * the block, else a miss at the end of the range.
     */
    @Nullable
    static HitResult look(Player p, double range) {
        return ProjectileUtil.getHitResultOnViewVector(p,
                e -> e instanceof LivingEntity living && living != p && living.isAlive() && SpiritProjectiles.mayStrike(p, living),
                range);
    }

    /** Gritz for this long, with no swirl and with its icon shown. */
    private static MobEffectInstance gritz(int ticks) {
        return new MobEffectInstance(ModEffects.GRITZ, ticks, 0, false, false, true);
    }

    private static double range() {
        return BleachConfig.get(BleachConfig.GINTO_RANGE);
    }

    private static List<Vec3> positions(List<SeeleSchneiderPfeil> blades) {
        List<Vec3> points = new ArrayList<>(blades.size());
        for (SeeleSchneiderPfeil blade : blades) {
            points.add(blade.position());
        }
        return points;
    }

    /** A box around everything the shape can hold: its corners, a block below the lowest and five above the highest. */
    private static AABB reach(SprengerShape shape) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : shape.corners()) {
            minX = Math.min(minX, corner.x);
            maxX = Math.max(maxX, corner.x);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AABB(minX, shape.minY() - 1.0, minZ, maxX, shape.maxY() + 5.0, maxZ);
    }

    /** A grey line on the action bar. */
    private static void tell(Player p, MutableComponent message) {
        p.displayClientMessage(message.withStyle(ChatFormatting.GRAY), true);
    }

    private static void milestone(Player p, String milestone) {
        if (p instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, milestone);
        }
    }
}
