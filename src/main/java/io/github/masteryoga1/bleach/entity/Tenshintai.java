package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.BankaiRecord;
import io.github.masteryoga1.bleach.soul.BankaiTrial;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The Tenshintai, Kisuke's doll that forces a zanpakutō spirit out, set down from its item ({@link #setDown}) by a soul
 * that knows its zanpakutō's name, to stand for {@code [bankai.tenshintai] window_days} game days, its window. Struck by
 * its soul with its own zanpakutō released, it begins a Bankai trial by it, the ring about the doll, as often as the
 * soul likes within the window, the spirit keeping its wounds from one bout to the next. Where it stands and when its
 * window ends are on the soul's record ({@link BankaiRecord#doll}, {@link BankaiRecord#windowEndsAt}), never here: a doll
 * is never saved, goes with its chunk, and stands again by its soul's tick wherever the record says ({@link #keep}). One
 * that record no longer names (its window over, Bankai reached, granted, revoked or reset, another spirit bound) crumbles
 * on its next tick; one whose soul is gone (logged out, respawned) goes quietly. A made thing, not a spirit: everyone sees
 * it, and nothing hurts, pushes or moves it.
 */
public class Tenshintai extends Entity {
    /** Its box, in blocks: a life-sized doll's. */
    public static final float WIDTH = 0.6F, HEIGHT = 1.95F;
    /** How often, in a soul's own ticks, its doll is looked for and stood again. */
    public static final int KEEP_TICKS = 20;
    private static final String SET_DOWN = "message.bleach.tenshintai.set_down", STANDS = "message.bleach.tenshintai.stands";
    private static final String NO_SPOT = "message.bleach.tenshintai.no_spot";
    private static final String CRUMBLED = "message.bleach.tenshintai.crumbled";
    private static final String NOT_YOURS = "message.bleach.tenshintai.not_yours";
    private static final String UNRELEASED = "message.bleach.tenshintai.unreleased";
    private static final int CRUMBLE_PARTICLES = 30;

    /** The doll standing for each soul, by its UUID, while that doll is in its world. Server thread only. */
    private static final Map<UUID, Tenshintai> STANDING = new HashMap<>();

    /** Its soul's player object, which a logout or a respawn replaces. Server side only. */
    @Nullable
    private Player owner;

    public Tenshintai(EntityType<? extends Tenshintai> type, Level level) {
        super(type, level);
    }

    /** A doll leaving its world is no longer its soul's; a stopped server's are gone with it. */
    public static void register(IEventBus bus) {
        bus.addListener(Tenshintai::onLeave);
        bus.addListener(Tenshintai::onServerStopped);
    }

    /**
     * Sets this soul's doll down at {@code at}, on the block under it, facing the soul, its window opening now, and says
     * so; or says why not, changing nothing but a window over, which closes first: no bout could begin by the doll there
     * ({@link BankaiTrial#refusal}: a soul that may not hold Bankai or holds it already among the reasons, and Zangetsu's
     * ground), a doll of its own standing in its window, or no sturdy top or no room for it there.
     *
     * @return null once it stands, else the key of the line that says why not
     */
    @Nullable
    public static String setDown(ServerLevel level, Player owner, BlockPos at) {
        long now = level.getGameTime();
        SoulData data = SoulLogic.get(owner);
        closeIfOver(owner, data, now);
        BankaiTrial.Start refusal = BankaiTrial.refusal(owner, Vec3.atBottomCenterOf(at));
        if (refusal != null) {
            return refusalKey(refusal);
        }
        BankaiRecord record = data.zanpakuto().bankai();
        if (record.doll().isPresent()) {
            return STANDS;
        }
        AABB box = ModEntities.TENSHINTAI.get().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(at));
        if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP) || !level.noCollision(box)
                || level.containsAnyLiquid(box)) {
            return NO_SPOT;
        }
        GlobalPos place = GlobalPos.of(level.dimension(), at);
        write(owner, data, record.dollAt(place, now + windowTicks()));
        stand(level, owner, place);
        level.playSound(null, at, SoundEvents.ARMOR_STAND_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        owner.sendSystemMessage(Component.translatable(SET_DOWN, windowDays()).withStyle(ChatFormatting.GRAY));
        return null;
    }

    /**
     * Each {@link #KEEP_TICKS} of a living soul's ticks: a window over closes, and the doll its record names in the
     * soul's own world stands again there, facing the soul, where that place ticks entities and none of its own stands.
     */
    public static void keep(Player owner, SoulData data) {
        if (!(owner.level() instanceof ServerLevel level) || closeIfOver(owner, data, level.getGameTime())) {
            return;
        }
        Optional<GlobalPos> place = data.zanpakuto().bankai().doll();
        Tenshintai standing = STANDING.get(owner.getUUID());
        if (place.isEmpty() || !place.get().dimension().equals(level.dimension())
                || standing != null && standing.owner == owner && !standing.isRemoved() && standing.named()
                || !level.isPositionEntityTicking(place.get().pos())) {
            return;
        }
        stand(level, owner, place.get());
    }

    /**
     * Closes this soul's window once it is over and no bout of the soul's is under way (one by the doll has its time up
     * with the window at the latest): the record forgets the doll and its window, the soul is told so in chat, and the doll
     * crumbles on its next tick. A doll set down with no window on its record, as one was before windows were kept,
     * closes at once.
     *
     * @return whether it closed now
     */
    private static boolean closeIfOver(Player owner, SoulData data, long now) {
        BankaiRecord record = data.zanpakuto().bankai();
        if (record.doll().isEmpty() || now < record.windowEndsAt() || BankaiTrial.trial(owner.getUUID()) != null) {
            return false;
        }
        write(owner, data, record.closed());
        owner.sendSystemMessage(Component.translatable(CRUMBLED).withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** How many game days a doll stands from its setting down ({@code [bankai.tenshintai] window_days}). */
    public static int windowDays() {
        return BleachConfig.get(BleachConfig.BANKAI_TENSHINTAI_WINDOW_DAYS);
    }

    /** The same in ticks of the game time its record is read by. */
    public static long windowTicks() {
        return windowDays() * (long) Level.TICKS_PER_DAY;
    }

    /** The doll standing for this soul, or null with none in its world. For the tests. */
    @Nullable
    public static Tenshintai standing(UUID owner) {
        return STANDING.get(owner);
    }

    /**
     * The doll's line for a bout the trial refuses: the call's refusals of power and name as one, and of its spirit, and
     * the rest each its own. A bout begun is no refusal, and has ALREADY's.
     */
    public static String refusalKey(BankaiTrial.Start start) {
        return switch (start) {
            case DEAD, NO_POWER, NAMELESS -> "message.bleach.tenshintai.no_power";
            case NO_SPIRIT, NO_TYPE -> "message.bleach.tenshintai.no_spirit";
            case STARTED, ALREADY -> "message.bleach.tenshintai.already";
            case WATCHING -> "message.bleach.tenshintai.watching";
            case REACHED -> "message.bleach.tenshintai.reached";
            case CROWDED -> "message.bleach.tenshintai.crowded";
            case NO_GROUND -> "message.bleach.tenshintai.no_ground";
            case BUSY -> "message.bleach.tenshintai.busy";
            case PEACEFUL -> "message.bleach.tenshintai.peaceful";
            case NO_ROOM -> "message.bleach.tenshintai.no_room";
        };
    }

    /** Stands this soul's doll at its place, facing the soul, the soul's doll before it gone. */
    private static void stand(ServerLevel level, Player owner, GlobalPos place) {
        Tenshintai old = STANDING.remove(owner.getUUID());
        if (old != null) {
            old.discard();
        }
        Vec3 at = Vec3.atBottomCenterOf(place.pos());
        Vec3 toOwner = owner.position().subtract(at);
        Tenshintai doll = new Tenshintai(ModEntities.TENSHINTAI.get(), level);
        doll.moveTo(at, (float) (Mth.atan2(toOwner.z, toOwner.x) * Mth.RAD_TO_DEG) - 90.0F, 0.0F);
        doll.owner = owner;
        if (level.addFreshEntity(doll)) {
            STANDING.put(owner.getUUID(), doll);
        }
    }

    /** Writes the soul's record and sends it to its client. */
    private static void write(Player owner, SoulData data, BankaiRecord record) {
        data.setZanpakuto(data.zanpakuto().withBankai(record));
        if (owner instanceof ServerPlayer player) {
            BleachNetwork.syncSoul(player, data);
        }
    }

    /** Whether its soul is still here and its record names this doll, where it stands. */
    private boolean named() {
        return owner != null && !owner.isRemoved() && SoulLogic.get(owner).zanpakuto().bankai().doll()
                .filter(place -> place.equals(GlobalPos.of(level().dimension(), blockPosition()))).isPresent();
    }

    /** On the server it goes once its soul is gone, and crumbles once its soul's record no longer names it. */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && !isRemoved() && !named()) {
            if (owner != null && !owner.isRemoved()) {
                server.sendParticles(ParticleTypes.POOF, getX(), getY(0.5), getZ(), CRUMBLE_PARTICLES, 0.25, 0.6, 0.25,
                        0.02);
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.PLAYERS,
                        1.0F, 0.8F);
            }
            discard();
        }
    }

    /** A blow stops here, before anything a blow does (mc Player.attack): the doll takes none, but is struck. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player player && level() instanceof ServerLevel level) {
            struck(level, player);
        }
        return true;
    }

    /**
     * Struck by its soul with its own zanpakutō released in its main hand, a bout by the doll begins, unless its window
     * is over, which closes; anyone else, a blade not released or a bout the trial refuses is told why not.
     */
    private void struck(ServerLevel level, Player player) {
        if (player != owner || !named()) {
            tell(player, NOT_YOURS);
            return;
        }
        ZanpakutoBlade blade = ZanpakutoItem.blade(player.getMainHandItem());
        if (!blade.isOwnedBy(player.getUUID()) || !blade.released()) {
            tell(player, UNRELEASED);
            return;
        }
        long now = level.getGameTime();
        SoulData data = SoulLogic.get(player);
        if (closeIfOver(player, data, now)) {
            return;
        }
        BankaiTrial.Start start = BankaiTrial.begin(player, position(), data.zanpakuto().bankai().windowEndsAt(), now);
        if (start != BankaiTrial.Start.STARTED) {
            tell(player, refusalKey(start));
        } else if (player instanceof ServerPlayer server) {
            BleachNetwork.syncSoul(server, data);
        }
    }

    /** A line on this player's action bar, in grey. */
    private static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }

    /** It can be aimed at, as a body can; shots pass through. */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    /** Nothing in the world counts it or moves it: no plate or wire, no piston, no portal, which would take it away. */
    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** Never saved (see the type's noSave): the record keeps where it stands. */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Tenshintai doll) {
            STANDING.values().remove(doll);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        STANDING.clear();
    }
}
