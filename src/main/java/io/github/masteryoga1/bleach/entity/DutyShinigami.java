package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Shinigami on duty in the Human World, come down to a Plus whose chain has begun to wear (see
 * {@link io.github.masteryoga1.bleach.soul.DutyEvents}). It offers that soul Konsō and sends it on, then returns to Soul
 * Society, as it does when no one asks. It cannot be hurt, takes no portal and is never saved.
 */
public final class DutyShinigami extends ShinigamiNpc {
    private static final float REIRYOKU = 300.0F;
    /** How long it waits, in ticks: three minutes. */
    public static final int DEFAULT_LIFETIME = 3600;
    /** Talking to it keeps it for at least this long, so a Plus has time to decide. */
    private static final int LIFETIME_AFTER_TALKING = 1200;
    /** How long it lingers once it has sent a Plus on. */
    public static final int LIFETIME_AFTER_KONSO = 60;
    private static final String DEPARTS_KEY = "message.bleach.duty_departs";

    private int lifetime = DEFAULT_LIFETIME;

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
    protected void onDialogueOpened() {
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

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && --lifetime <= 0 && isAlive()) {
            depart(DEPARTS_KEY);
        }
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
