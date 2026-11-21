package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.DutyEvents;
import io.github.masteryoga1.bleach.soul.HomeWorldRespawn;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Shinigami on duty in the Human World, come down to a Plus whose chain has begun to wear, or to a soul that has passed
 * on (see {@link DutyEvents}). It offers that Plus Konsō and sends it on, or sends that soul home if it stays near, then
 * returns to Soul Society, as it does when no one asks; it cannot be hurt, takes no portal and is never saved.
 */
public final class DutyShinigami extends ShinigamiNpc {
    private static final float REIRYOKU = 300.0F;
    /** How long it waits, in ticks: three minutes. */
    public static final int DEFAULT_LIFETIME = 3600;
    /** Talking to it keeps it for at least this long, so a Plus has time to decide. */
    private static final int LIFETIME_AFTER_TALKING = 1200;
    /** How long it lingers once it has sent a Plus on. */
    public static final int LIFETIME_AFTER_KONSO = 60;
    /** How long the soul it came to send home has to walk off first, in ticks: half a minute. */
    public static final int HOMEWARD_TICKS = 600;
    /** How near that soul must still be to be sent: twice the 16 blocks it comes down within. */
    public static final double HOMEWARD_REACH = 32.0;
    private static final String DEPARTS_KEY = "message.bleach.duty_departs";

    private int lifetime = DEFAULT_LIFETIME;
    /** The soul it came to send home, if it came for one, and how long it waits; never saved, as the NPC is not. */
    @Nullable
    private UUID charge;
    private int homeward;

    public DutyShinigami(EntityType<? extends DutyShinigami> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    /** A Plus is offered Konsō and a Hollow is warned off; anyone else finds it with nothing to ask of them. */
    @Override
    public DialogueView viewFor(SoulData visitor, long now) {
        DialogueView common = commonView(visitor);
        return common != null ? common : DialogueView.RESTING;
    }

    /** The soul it came to send home is told to stay near or walk off; anyone else is answered by its soul alone. */
    @Override
    public DialogueView viewFor(Player visitor, SoulData data, long now) {
        return visitor.getUUID().equals(charge) && HomeWorldRespawn.passedOnHome(data) != null ? DialogueView.HOMEWARD
                : viewFor(data, now);
    }

    @Override
    public String dialogueKey() {
        return "duty";
    }

    @Override
    protected float soulReiryoku() {
        return REIRYOKU;
    }

    /** It was sent to send a Plus on. */
    @Override
    protected boolean sendsOn() {
        return true;
    }

    @Override
    protected void onDialogueOpened(ServerPlayer visitor) {
        lifetime = Math.max(lifetime, LIFETIME_AFTER_TALKING);
    }

    /** As every Shinigami NPC does; once a Plus it was asked by has passed on, it soon leaves. */
    @Override
    public void perform(Player visitor, NpcAction action) {
        boolean plus = SoulLogic.hasSoul(visitor) && SoulLogic.get(visitor).state() == SoulState.PLUS;
        super.perform(visitor, action);
        if (plus && action == NpcAction.KONSO && SoulLogic.get(visitor).state() != SoulState.PLUS) {
            lifetime = Math.min(lifetime, LIFETIME_AFTER_KONSO);
        }
    }

    public int lifetime() {
        return lifetime;
    }

    public void setLifetime(int ticks) {
        lifetime = ticks;
    }

    /** It came to send this soul, which has passed on, home: it does, if the soul is still near in half a minute. */
    public void sendHomeLater(ServerPlayer soul) {
        charge = soul.getUUID();
        homeward = HOMEWARD_TICKS;
    }

    /** The soul it came to send home, or null. */
    @Nullable
    public UUID charge() {
        return charge;
    }

    /** How long until the soul it came for is sent home or let go, in ticks. */
    public int homeward() {
        return homeward;
    }

    public void setHomeward(int ticks) {
        homeward = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (charge != null && --homeward <= 0) {
            endErrand();
        }
        if (--lifetime <= 0 && isAlive()) {
            depart(DEPARTS_KEY);
        }
    }

    /**
     * Ends the errand: the soul it came for, if it stayed in this world within {@link #HOMEWARD_REACH} and may still be
     * sent, is sent home between ticks, and else let go; either way it leaves soon after, as after a Konsō. Public for
     * game tests.
     */
    public void endErrand() {
        Player found = charge == null ? null : level().getPlayerByUUID(charge);
        charge = null;
        lifetime = Math.min(lifetime, LIFETIME_AFTER_KONSO);
        if (found instanceof ServerPlayer soul && level().dimension() == Level.OVERWORLD
                && distanceToSqr(soul) <= HOMEWARD_REACH * HOMEWARD_REACH && mayBeSent(soul)) {
            // Between ticks: a world part way through ticking its creatures could tick the soul again once it had left.
            Level here = level();
            soul.server.tell(new TickTask(soul.server.getTickCount(), () -> {
                if (!soul.hasDisconnected() && soul.level() == here && mayBeSent(soul)) {
                    HomeWorldRespawn.sendHome(soul);
                }
            }));
        }
    }

    /**
     * Whether this soul may be sent home: alive, outside creative and spectator, not held in a Negación light or a Caja,
     * with no bed or anchor of its own in the Overworld, and still a soul that has passed on, while the server sends home.
     */
    private static boolean mayBeSent(ServerPlayer soul) {
        return soul.isAlive() && !soul.isCreative() && !soul.isSpectator() && !Negacion.held(soul)
                && !DutyEvents.spawnsInOverworld(soul) && HomeWorldRespawn.passedOnHome(SoulLogic.get(soul)) != null
                && DutyEvents.sendsHome();
    }

    /** It stays in the world it was sent to. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    /**
     * A visit is never written down: it is gone when its chunk unloads or the world closes. It still counts as one to
     * save, so an unloading chunk takes it away rather than leaving it behind, frozen, to come back when the chunk loads.
     */
    @Override
    public boolean saveAsPassenger(CompoundTag tag) {
        return false;
    }

    /** Its lifetime, for {@code /data}: the NPC itself is never saved (see {@link #saveAsPassenger}). */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
    }

    /** Data without an "Invulnerable" entry, such as a summon command's, keeps it invulnerable; "Lifetime" sets its stay. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("Invulnerable")) {
            setInvulnerable(true);
        }
        if (tag.contains("Lifetime")) {
            lifetime = tag.getInt("Lifetime");
        }
    }
}
