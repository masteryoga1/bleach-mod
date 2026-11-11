package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.DangaiPlan;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Senkaimon: two lit shōji a Shinigami turns open with its blade, 3 wide and 4 tall, a block deep across the way it
 * faced. An aware soul passes after a second inside ({@link TravelEvents#destination}), into the Dangai unless a
 * Jigokuchō guides it, and the gate closes at its time. The server decides everything; a client draws it from its
 * synced facing and end. Never saved (its type is {@code noSave}), and hidden from the unaware as a spirit
 * ({@code #bleach:spirits}).
 */
public final class Senkaimon extends Entity implements Portal {
    public static final float HALF_WIDTH = 1.5F;
    public static final float HEIGHT = 4.0F;
    public static final float THICKNESS = 1.0F;
    /** How far before its opener's feet it stands. */
    public static final double DISTANCE = 2.0;
    /** Ticks inside before a soul passes; vanilla's count makes it one more. */
    public static final int PASS_TICKS = 20;
    private static final int TICKS_PER_SECOND = 20;
    private static final String MOUNT_KEY = "message.bleach.senkaimon.mount";
    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(Senkaimon.class, EntityDataSerializers.DIRECTION);
    /** The game time it closes. */
    private static final EntityDataAccessor<Long> DATA_ENDS_AT =
            SynchedEntityData.defineId(Senkaimon.class, EntityDataSerializers.LONG);
    /** The chime as it opens and closes, and the motes that spill from it as it opens. */
    private static final float CHIME_VOLUME = 1.0F;
    private static final float CHIME_PITCH = 1.4F;
    private static final int OPEN_MOTES = 12;
    private static final double MOTE_SPREAD = 0.75;
    private static final double MOTE_RISE = 1.0;
    private static final double MOTE_SPEED = 0.02;

    // Server side only, never synced or saved: who opened it, the world it leads to, and the Dangai lane those it does
    // not guide cross.
    @Nullable
    private UUID opener;
    @Nullable
    private ResourceKey<Level> leadsTo;
    private int lane;

    public Senkaimon(EntityType<? extends Senkaimon> type, Level level) {
        super(type, level);
    }

    // ---- Opening it ----

    /**
     * Opens a gate where {@link #standing} puts it, for {@code open_seconds}, heard and seen by those who see spirits.
     * Null where no gate leads on, or if the level would not take it; crowding is the caller's rule.
     */
    @Nullable
    public static Senkaimon open(ServerLevel level, Entity opener, long now) {
        ResourceKey<Level> to = TravelLogic.leadsTo(level.dimension());
        if (to == null) {
            return null;
        }
        Direction facing = opener.getDirection();
        Vec3 at = standing(opener, facing);
        Senkaimon gate = new Senkaimon(ModEntities.SENKAIMON.get(), level);
        gate.entityData.set(DATA_FACING, facing);
        gate.entityData.set(DATA_ENDS_AT,
                now + (long) BleachConfig.get(BleachConfig.SENKAIMON_OPEN_SECONDS) * TICKS_PER_SECOND);
        gate.opener = opener.getUUID();
        gate.leadsTo = to;
        gate.lane = level.getRandom().nextInt(DangaiPlan.LANES);
        gate.moveTo(at.x, at.y, at.z, facing.toYRot(), 0.0F);
        if (!level.addFreshEntity(gate)) {
            return null;
        }
        // Made ahead in the background, so one who passes unguided is not held while the lane's start is made.
        ServerLevel dangai = BleachWorlds.level(level.getServer(), BleachWorlds.DANGAI);
        if (dangai != null) {
            BlockPos start = BlockPos.containing(DangaiPlan.entry(gate.lane));
            dangai.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(start), 0, start);
        }
        if (opener instanceof LivingEntity living) {
            living.swing(InteractionHand.MAIN_HAND, true);
        }
        double middle = at.y + HEIGHT / 2.0;
        SpiritSenses.play(level, at.x, middle, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, CHIME_VOLUME,
                CHIME_PITCH, false);
        SpiritSenses.particles(level, ParticleTypes.END_ROD, at.x, middle, at.z, OPEN_MOTES, MOTE_SPREAD, MOTE_RISE,
                MOTE_SPREAD, MOTE_SPEED);
        return gate;
    }

    /** Whether a gate this opener opened now would stand within one soul's reach of a gate already open. */
    public static boolean crowded(Entity opener) {
        Direction facing = opener.getDirection();
        // Grown by a player's size, so no soul's box could meet both gates at once.
        AABB reach = boxAt(standing(opener, facing), facing)
                .inflate(EntityType.PLAYER.getWidth(), EntityType.PLAYER.getHeight(), EntityType.PLAYER.getWidth());
        return !opener.level().getEntitiesOfClass(Senkaimon.class, reach).isEmpty();
    }

    /** Where a gate this opener opens stands: {@link #DISTANCE} before its feet, square to the nearest compass point. */
    private static Vec3 standing(Entity opener, Direction facing) {
        return opener.position().add(facing.getStepX() * DISTANCE, 0.0, facing.getStepZ() * DISTANCE);
    }

    /** A gate's box at these feet: {@link #HALF_WIDTH} either side across its facing, {@link #THICKNESS} deep. */
    static AABB boxAt(Vec3 at, Direction facing) {
        boolean eastWest = facing.getAxis() == Direction.Axis.X;
        double along = THICKNESS / 2.0;
        double dx = eastWest ? along : HALF_WIDTH;
        double dz = eastWest ? HALF_WIDTH : along;
        return new AABB(at.x - dx, at.y, at.z - dz, at.x + dx, at.y + HEIGHT, at.z + dz);
    }

    /** The way it faces, which its depth runs along: the way its opener faced. */
    public Direction facing() {
        return entityData.get(DATA_FACING);
    }

    /** The game time it closes. */
    public long endsAt() {
        return entityData.get(DATA_ENDS_AT);
    }

    /** Who opened it. Server side only. */
    @Nullable
    public UUID opener() {
        return opener;
    }

    /** The world it leads to. Server side only. */
    @Nullable
    public ResourceKey<Level> leadsTo() {
        return leadsTo;
    }

    /** The Dangai lane those this gate does not guide cross, picked as it opened. Server side only. */
    public int lane() {
        return lane;
    }

    // ---- The entity ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FACING, Direction.NORTH);
        builder.define(DATA_ENDS_AT, 0L);
    }

    /** Its box follows its facing, so a client builds it as it learns the facing with the gate. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_FACING.equals(key)) {
            setBoundingBox(makeBoundingBox());
        }
    }

    /** {@link #boxAt} its feet. Reads only the synced facing, which the entity has before its first box is made. */
    @Override
    protected AABB makeBoundingBox() {
        return boxAt(position(), facing());
    }

    /**
     * Server side: it closes at its time; until then each aware soul in it is let in, or told it rides, once a second.
     * Nothing else: it never moves, and no water, fire or portal touches it.
     */
    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() >= endsAt()) {
            SpiritSenses.play(level, getX(), getY() + HEIGHT / 2.0, getZ(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH, false);
            discard();
            return;
        }
        boolean tellRiders = tickCount % TICKS_PER_SECOND == 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox(), Senkaimon::mayEnter)) {
            if (!player.isPassenger()) {
                player.setAsInsidePortal(this, blockPosition());
            } else if (tellRiders && !Negacion.held(player)) {
                // A held soul's holder already speaks to it.
                player.displayClientMessage(Component.translatable(MOUNT_KEY).withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** Alive, no spectator, and aware: the unaware walk through it as if it were not there. */
    private static boolean mayEnter(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && SoulLogic.hasSoul(player)
                && SoulLogic.isAware(SoulLogic.get(player));
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return PASS_TICKS;
    }

    /** Where it takes this player, or null, the player told why, when it may not pass ({@link TravelEvents}). */
    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        return entity instanceof ServerPlayer player ? TravelEvents.destination(this, player) : null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    /** Never saved (see the type's noSave). */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
