package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.GargantaEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * Fishbone D, the first Hollow: a hulking fish-masked brute that tears through the sky to hunt one
 * soul, its quarry. It is never saved, so a server stop or an unloaded chunk sends it away quietly. It
 * gives up and retreats through a tear of its own when it loses its quarry for half a minute or the
 * fight drags on past its lifetime; the rest of the script lives in {@link GargantaEvents}.
 */
public class FishboneD extends Hollow {
    /** How long it hunts before it gives up, in ticks: five minutes. */
    public static final int LIFETIME = 6000;
    /** How long its quarry may be out of reach before it gives up. */
    public static final int LOST_LIMIT_TICKS = 600;
    /** How far its quarry may get before it counts as lost, in blocks. */
    public static final int QUARRY_RANGE = 64;
    private static final float ROAR_VOLUME = 2.0F;

    @Nullable
    private UUID quarry;
    private int lifetime = LIFETIME;
    private int lostTicks;
    /** Whether the quarry is told when it retreats; not when the hunt was cut short. */
    private boolean announceRetreat = true;

    public FishboneD(EntityType<? extends FishboneD> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.FISHBONE_D.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.FISHBONE_D;
    }

    /** Sets it on this soul, which also counts as the soul that drew it. */
    public void hunt(ServerPlayer quarry) {
        this.quarry = quarry.getUUID();
        setDrawer(quarry.getUUID());
    }

    @Nullable
    public UUID quarry() {
        return quarry;
    }

    public int lifetime() {
        return lifetime;
    }

    public void setLifetime(int ticks) {
        lifetime = ticks;
    }

    /** Ends the hunt within this many ticks, and retreats without a word to the quarry. */
    public void cutShort(int ticks) {
        lifetime = Math.min(lifetime, ticks);
        announceRetreat = false;
    }

    /** Server side, every tick so it still runs with NoAI: the lifetime, the lost quarry, and the retreat. */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved() || !isAlive() || isDeparting()) {
            return;
        }
        lostTicks = quarry != null && quarryInReach() == null ? lostTicks + 1 : 0;
        if (--lifetime <= 0 || lostTicks >= LOST_LIMIT_TICKS) {
            retreat();
        }
    }

    /** With nothing else to fight, it goes after its quarry. */
    @Override
    protected void customServerAiStep() {
        if (getTarget() == null) {
            ServerPlayer prey = quarryInReach();
            if (prey != null) {
                setTarget(prey);
            }
        }
        super.customServerAiStep();
    }

    /** The quarry, if they are online, wherever they are. */
    @Nullable
    private ServerPlayer quarryPlayer() {
        return quarry != null && level().getServer() != null ? level().getServer().getPlayerList().getPlayer(quarry) : null;
    }

    /** The quarry while they can still be hunted: alive, in this world, within reach and still prey. */
    @Nullable
    private ServerPlayer quarryInReach() {
        ServerPlayer player = quarryPlayer();
        return player != null && player.isAlive() && player.level() == level()
                && distanceToSqr(player) <= (double) QUARRY_RANGE * QUARRY_RANGE && HollowLogic.isPrey(player)
                ? player : null;
    }

    /** Gives up the hunt and leaves through a tear. */
    void retreat() {
        GargantaEvents.onFishboneRetreat(this, quarryPlayer(), announceRetreat);
        depart();
    }

    /** Leaving for lack of prey is a retreat too, so the script hears of it. */
    @Override
    protected void leave() {
        retreat();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected float getSoundVolume() {
        return ROAR_VOLUME;
    }
}
