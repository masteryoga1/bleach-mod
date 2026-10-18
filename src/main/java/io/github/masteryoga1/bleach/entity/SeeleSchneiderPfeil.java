package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.GintoSpells;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Seele Schneider fired from Ginrei Kojaku, or planted by hand. In flight it is a Heilig Pfeil
 * with its own damage type (which half ignores armour; see {@code QuincyArtsEvents}); where a Heilig
 * Pfeil would end (its last pierce spent, a shield, its time up, its owner gone) it is <em>spent</em>
 * instead: it drops back a little and falls as a vanilla arrow, striking no one. It sticks into the
 * first block it meets, spent from then on, and tells {@link GintoSpells} so the owner's blades can
 * bind what stands among them. A stuck blade can be picked up as a fresh Seele Schneider by its
 * owner, or by anyone aware of spirits once the owner is gone; one left alone long enough drops its
 * hilt and goes, and so does one that never lands. Unlike a Heilig Pfeil it is saved, and a blade
 * loaded mid-flight comes back spent.
 */
public class SeeleSchneiderPfeil extends HeiligPfeil {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("seele_schneider"));

    /** Synced, so a client gives a spent blade the same gravity the server does. */
    private static final EntityDataAccessor<Boolean> DATA_SPENT =
            SynchedEntityData.defineId(SeeleSchneiderPfeil.class, EntityDataSerializers.BOOLEAN);

    /** A spent blade falls as a vanilla arrow does. */
    private static final double SPENT_GRAVITY = 0.05;
    /** How much of its speed a blade keeps, turned back, when its flight ends against something. */
    private static final double SPENT_BOUNCE = -0.1;
    /** How far above the block it is planted in a planted blade stands: just clear of the top face. */
    private static final double PLANT_HEIGHT = 1.03;
    /** A planted blade points straight down. */
    private static final float PLANT_PITCH = -90.0F;
    /**
     * The drift a planted blade is given, which only a client acts on: its own flight then turns it
     * point down and sticks it into the top face, as the server's already is.
     */
    private static final Vec3 PLANT_SETTLE = new Vec3(0.0, -0.05, 0.0);

    /** The owner's id, kept apart from {@link #getOwner}'s (which is private), for finding an owner's blades. */
    @Nullable
    private UUID ownerId;
    /** How long it has been stuck, towards dropping its hilt. Server only. */
    private int stuckTicks;
    /** How long it has been spent and in the air. Server only. */
    private int airTicks;
    /**
     * Whether it stuck during this tick's flight and has yet to ring and be looked over for a
     * formation, which waits until the flight has carried it to where it stuck. Server only.
     */
    private boolean settling;
    /** Whether a Sprenger cast holds it until the blast: no one may take it, and it does not drop its hilt. Server only. */
    private boolean armed;

    public SeeleSchneiderPfeil(EntityType<? extends SeeleSchneiderPfeil> type, Level level) {
        super(type, level);
    }

    /**
     * Fires a blade from {@code from} along {@code direction} at {@code speed} blocks a tick, dead
     * straight, at full size, with this damage and pierce, and adds it to the level. The owner may
     * pick it up again once it has stuck.
     */
    public static SeeleSchneiderPfeil launch(Level level, Player owner, Vec3 from, Vec3 direction, float damage,
                                             int pierce, float speed) {
        SeeleSchneiderPfeil blade = aim(new SeeleSchneiderPfeil(ModEntities.SEELE_SCHNEIDER.get(), level), owner, from,
                direction, 1.0F, damage, pierce, speed, 0.0F);
        // Aiming leaves every arrow one nobody may pick up; a blade is the owner's to take back.
        blade.pickup = pickupFor(owner);
        level.addFreshEntity(blade);
        return blade;
    }

    /**
     * Plants a blade point down in the top of this block, standing in the space above it, as if it
     * had been fired straight down into it, and adds it to the level. Null, with nothing planted, if
     * there is no room above the block or nothing solid to plant it in (see {@link #canPlantAt}).
     */
    @Nullable
    public static SeeleSchneiderPfeil plant(ServerLevel level, Player owner, BlockPos ground) {
        if (!canPlantAt(level, ground)) {
            return null;
        }
        SeeleSchneiderPfeil blade = new SeeleSchneiderPfeil(ModEntities.SEELE_SCHNEIDER.get(), level);
        blade.setOwner(owner);
        blade.moveTo(ground.getX() + 0.5, ground.getY() + PLANT_HEIGHT, ground.getZ() + 0.5, owner.getYRot(), PLANT_PITCH);
        // Spent and drifting down before it is added, so a client that sees it arrive sticks it the same way.
        blade.spend();
        blade.setDeltaMovement(PLANT_SETTLE);
        if (!level.addFreshEntity(blade)) {
            return null;
        }
        blade.onHitBlock(new BlockHitResult(blade.position(), Direction.UP, ground, false));
        // Sticking stilled it; the drift is kept for clients that start watching later.
        blade.setDeltaMovement(PLANT_SETTLE);
        // Already where it stuck, so it settles at once.
        blade.settle();
        return blade;
    }

    /**
     * Whether a blade can be planted in the top of this block: the block is solid enough to hold it
     * and the space above it is free.
     */
    public static boolean canPlantAt(BlockGetter level, BlockPos ground) {
        BlockPos above = ground.above();
        return !level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                && level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    /** Whether it is spent and stuck in a block, as a planted blade is: one a Sprenger can be cast with. */
    public boolean planted() {
        return isSpent() && inGround;
    }

    /** The id of the player who fired or planted it, or null. Kept with the blade when it is saved. */
    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    /** For game tests: sets how long it has been stuck, so dropping its hilt need not be waited for. */
    public void setStuckTicksForTest(int ticks) {
        stuckTicks = ticks;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPENT, false);
    }

    @Override
    protected ResourceKey<DamageType> damageType() {
        return DAMAGE_TYPE;
    }

    @Override
    protected boolean sticks() {
        return true;
    }

    /**
     * Stuck, on the server: spent from now on, and settling (see {@link #settle}) once the flight has
     * carried it to where it stuck, since vanilla moves it there only after the hit.
     */
    @Override
    protected void onStuck(BlockHitResult hit) {
        if (level().isClientSide()) {
            return;
        }
        spend();
        settling = true;
    }

    /** Where it stuck at last: a ring of steel for the aware, and the owner's blades looked over for a Sprenger formation. */
    private void settle() {
        settling = false;
        SpiritSenses.play(this, ModSounds.SEELE_SCHNEIDER_STICK.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        GintoSpells.onSeeleStuck(this);
    }

    /** Whether a Sprenger cast holds it until the blast (see {@link #arm}). */
    public boolean armed() {
        return armed;
    }

    /** Held for a Sprenger's blast, or let go again if the cast ends without one. Not saved, as the cast is not. */
    public void arm(boolean held) {
        armed = held;
    }

    /** Its flight is over: spent, and turned back a little off whatever ended it, to fall from there. */
    @Override
    protected void fade() {
        if (isSpent()) {
            return;
        }
        spend();
        setDeltaMovement(getDeltaMovement().scale(SPENT_BOUNCE));
    }

    @Override
    protected boolean isSpent() {
        return entityData.get(DATA_SPENT);
    }

    /** Marks it spent, leaving its motion alone (sticking has already placed it). */
    private void spend() {
        entityData.set(DATA_SPENT, true);
    }

    @Override
    protected double getDefaultGravity() {
        return isSpent() ? SPENT_GRAVITY : 0.0;
    }

    /**
     * What picking it up gives: a fresh Seele Schneider. Called while the arrow is still being built,
     * so it reads nothing of the blade itself.
     */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.SEELE_SCHNEIDER.get());
    }

    /** A spent blade strikes no one: it only falls and sticks. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return !isSpent() && super.canHitEntity(entity);
    }

    /**
     * Keeps its own copy of the owner's id, and makes a player's blade theirs to pick up again (only
     * as a creative player picks things up, if they need no items). Setting no owner changes nothing.
     */
    @Override
    public void setOwner(@Nullable Entity owner) {
        Pickup before = pickup;
        super.setOwner(owner);
        if (owner == null) {
            pickup = before;
            return;
        }
        ownerId = owner.getUUID();
        pickup = owner instanceof Player player ? pickupFor(player) : before;
    }

    private static Pickup pickupFor(Player owner) {
        return owner.hasInfiniteMaterials() ? Pickup.CREATIVE_ONLY : Pickup.ALLOWED;
    }

    /**
     * Only a spent blade, and only by its owner, or by anyone aware of spirits once the owner has left
     * the server; then as vanilla hands out an arrow.
     */
    @Override
    protected boolean tryPickup(Player player) {
        if (!isSpent() || armed || !(ownedBy(player) || ownerGone() && SoulLogic.seesSpirits(player))) {
            return false;
        }
        return switch (pickup) {
            case ALLOWED -> player.getInventory().add(getPickupItem());
            case CREATIVE_ONLY -> player.hasInfiniteMaterials();
            case DISALLOWED -> false;
        };
    }

    /**
     * Whether the owner has left the server. An owner online in another level is not gone, though a
     * blade loaded again from its chunk cannot find them from here.
     */
    private boolean ownerGone() {
        if (getOwner() != null) {
            return false;
        }
        MinecraftServer server = level().getServer();
        return ownerId == null || server == null || server.getPlayerList().getPlayer(ownerId) == null;
    }

    /** Stuck too long: it drops its hilt, if it is one to pick up, and goes; but not while a Sprenger holds it. */
    @Override
    protected void tickDespawn() {
        if (!armed && ++stuckTicks >= BleachConfig.get(BleachConfig.SEELE_STUCK_DESPAWN_TICKS)) {
            dropHiltAndGo();
        }
    }

    /**
     * As a Heilig Pfeil (or a vanilla arrow once spent); then, on the server, a blade that stuck this
     * tick settles where it came to rest, and a spent blade that never lands drops its hilt and goes.
     */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        if (settling) {
            settle();
        }
        if (!isSpent() || inGround) {
            airTicks = 0;
        } else if (++airTicks >= QuincyArtsLogic.SEELE_SPENT_AIR_TICKS) {
            dropHiltAndGo();
        }
    }

    private void dropHiltAndGo() {
        if (pickup == Pickup.ALLOWED) {
            spawnAtLocation(getPickupItemStackOrigin().copy(), 0.1F);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Spent", isSpent());
        tag.putInt("StuckTicks", stuckTicks);
        if (ownerId != null) {
            tag.putUUID("SeeleOwner", ownerId);
        }
    }

    /** Whatever it was doing when saved, it comes back spent: its flight's damage and rules were never saved. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spend();
        stuckTicks = tag.getInt("StuckTicks");
        ownerId = tag.hasUUID("SeeleOwner") ? tag.getUUID("SeeleOwner") : null;
    }
}
