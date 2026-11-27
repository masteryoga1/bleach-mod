package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A Vasto Lorde: the highest class of Menos Grande, as small as a person and stronger than a captain. It is no
 * {@link Menos}, so none of their cap, bait or lifetime rules is its, and no Menos count, arrival or meal takes it (see
 * {@link HollowKind#isVasto}). It hunts and strikes as any Hollow does, with its wits about it, and released reiatsu
 * never frightens it off. Its health and blow are the server's to set; its armour is the heaviest of any Hollow's,
 * toughened against heavy blows. It is no sinner and never saved, so it goes with its chunk, or through a tear for lack
 * of prey. Only a command brings one yet: {@code /summon}, or {@code /bleach garganta}, whose tear brings it alone.
 *
 * <p>Its white bar shows to the souls about it who see spirits. Each has one of three looks of the mod's own, rolled as
 * it spawns ({@link #look}): bone plates over its shoulders and chest, a horned mask, or a crested one. The look is sent
 * to every client, so each draws the same, and is never saved, as the body is never saved; a summon's data may name it
 * ({@code Look}).
 */
public class VastoLorde extends Hollow {
    /** How much of a heavy blow its armour still turns, on top of the armour itself. */
    public static final double TOUGHNESS = 2.0;
    /** Its three looks, and how many there are. */
    public static final int PLATED = 0;
    public static final int HORNED = 1;
    public static final int CRESTED = 2;
    public static final int LOOKS = 3;
    /** The tag of a summon's data that names its look. Read, never written. */
    public static final String LOOK_TAG = "Look";
    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(VastoLorde.class,
            EntityDataSerializers.INT);

    private final BossBar bar = new BossBar(this, "bossbar.bleach.vasto_lorde", BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS);

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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOOK, PLATED);
    }

    /** Spawning rolls its look, each of the three alike; one that skipped spawning keeps the plated look. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setLook(level.getRandom().nextInt(LOOKS));
        return data;
    }

    /** Which of its three looks it has: {@link #PLATED}, {@link #HORNED} or {@link #CRESTED}. */
    public int look() {
        return entityData.get(DATA_LOOK);
    }

    /** Gives it this look, any number taken round the three. */
    public void setLook(int look) {
        entityData.set(DATA_LOOK, Math.floorMod(look, LOOKS));
    }

    /** A summon's data may name its look; nothing writes it, as the body is never saved. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(LOOK_TAG, Tag.TAG_ANY_NUMERIC)) {
            setLook(tag.getInt(LOOK_TAG));
        }
    }

    /** Moved to another world by command, it comes there as a copy of itself, in its look. */
    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof VastoLorde old) {
            setLook(old.look());
        }
    }

    /** Its health and the damage of its blow are the config's ([bosses.vasto_lorde]). */
    @Override
    protected ConfigBases configBases() {
        return new ConfigBases(BleachConfig.get(BleachConfig.VASTO_LORDE_HEALTH),
                BleachConfig.get(BleachConfig.VASTO_LORDE_DAMAGE));
    }

    /** Server side, every tick, so it runs with NoAI too: its bar, last, so the bar shows the health this tick left. */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            bar.tick();
        }
    }

    /** A soul that stops seeing it loses its bar at once. */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.forget(player);
    }

    /** Its bar. */
    public BossBar bossBar() {
        return bar;
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
