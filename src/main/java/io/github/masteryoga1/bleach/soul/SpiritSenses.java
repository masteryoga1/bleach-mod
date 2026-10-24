package io.github.masteryoga1.bleach.soul;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What only the spiritually aware perceive. Spirit mobs make their sounds and particles through
 * here, so a player who cannot see a Hollow does not hear its footsteps or see its dust either. A
 * Human just below the awareness threshold (see {@link SoulLogic#inShimmerBand}) still hears the
 * screams, faint and far off.
 */
public final class SpiritSenses {
    /** How far spirit particles are seen. */
    private static final double PARTICLE_RANGE = 64.0;
    /** How far a scream carries to a soul in the shimmer band. */
    private static final double SCREAM_RANGE = 64.0;
    /** A scream is heard this many blocks away towards its source, however close the source is. */
    private static final double SCREAM_DISTANCE = 8.0;
    private static final float SCREAM_VOLUME = 0.3F;
    private static final float SCREAM_PITCH = 0.8F;

    private SpiritSenses() {
    }

    /**
     * Whether a spirit's body meets another's, to push or be pushed: only another spirit, a creature
     * that sees spirits (see {@link SoulLogic#SPIRIT_SIGHTED}), or a player who can see them.
     * Everything else, unaware players and mundane creatures alike, passes through.
     */
    public static boolean bodiesTouch(Entity spirit, Entity other) {
        if (other instanceof Player player) {
            return SoulLogic.seesSpirits(player);
        }
        return other.getType().is(SoulLogic.SPIRITS) || other.getType().is(SoulLogic.SPIRIT_SIGHTED);
    }

    /** Plays a spirit's sound at its position. Does nothing on the client. */
    public static void play(Entity source, SoundEvent sound, SoundSource category, float volume, float pitch,
                            boolean scream) {
        if (source.level() instanceof ServerLevel level) {
            play(level, source.getX(), source.getY(), source.getZ(), sound, category, volume, pitch, scream);
        }
    }

    /**
     * Plays a sound only those who see spirits hear, as far as the sound carries. A scream also
     * reaches Humans in the shimmer band within {@link #SCREAM_RANGE} blocks, quietly, lower and from
     * a little way off, as if from far away.
     */
    public static void play(ServerLevel level, double x, double y, double z, SoundEvent sound, SoundSource category,
                            float volume, float pitch, boolean scream) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        long seed = level.getRandom().nextLong();
        double range = sound.getRange(volume);
        Vec3 at = new Vec3(x, y, z);
        for (ServerPlayer player : level.players()) {
            double distanceSqr = player.distanceToSqr(at);
            if (SoulLogic.seesSpirits(player)) {
                if (distanceSqr <= range * range) {
                    player.connection.send(new ClientboundSoundPacket(holder, category, x, y, z, volume, pitch, seed));
                }
            } else if (scream && distanceSqr <= SCREAM_RANGE * SCREAM_RANGE && SoulLogic.hasSoul(player)
                    && SoulLogic.inShimmerBand(SoulLogic.get(player))) {
                Vec3 eyes = player.getEyePosition();
                Vec3 toward = at.subtract(eyes);
                Vec3 heard = toward.lengthSqr() < 1.0E-6 ? eyes : eyes.add(toward.normalize().scale(SCREAM_DISTANCE));
                player.connection.send(new ClientboundSoundPacket(holder, category, heard.x, heard.y, heard.z,
                        SCREAM_VOLUME, pitch * SCREAM_PITCH, seed));
            }
        }
    }

    /** Sends particles to the players within 64 blocks who can see spirits, and no one else. */
    public static void particles(ServerLevel level, ParticleOptions options, double x, double y, double z,
                                 int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (SoulLogic.seesSpirits(player) && player.distanceToSqr(x, y, z) <= PARTICLE_RANGE * PARTICLE_RANGE) {
                level.sendParticles(player, options, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    /**
     * The spray of a spirit splashing into water, as vanilla's but for those who can see spirits only;
     * everyone else would see water thrown up by nothing.
     */
    public static void splash(ServerLevel level, Entity spirit) {
        double width = spirit.getBbWidth();
        int count = 1 + (int) (width * 20.0);
        double y = Math.floor(spirit.getY()) + 1.0;
        particles(level, ParticleTypes.BUBBLE, spirit.getX(), y, spirit.getZ(), count, width * 0.5, 0.0, width * 0.5, 0.2);
        particles(level, ParticleTypes.SPLASH, spirit.getX(), y, spirit.getZ(), count, width * 0.5, 0.0, width * 0.5, 0.2);
    }

    /** The players within range who can see spirits, nearest or not, in no particular order. */
    public static List<ServerPlayer> awareNear(ServerLevel level, Vec3 at, double range) {
        List<ServerPlayer> aware = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= range * range && SoulLogic.seesSpirits(player)) {
                aware.add(player);
            }
        }
        return aware;
    }
}
