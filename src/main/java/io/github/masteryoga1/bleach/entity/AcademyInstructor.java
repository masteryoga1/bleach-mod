package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * The instructor at the Academy: performs Konsō for a Plus, enrolls souls that have passed on, sits
 * students' exams, and hands out Asauchi. He cannot be hurt or moved, so the Academy always works:
 * whatever pulls, carries or pushes him away from where he first stood, he is back there the next
 * tick. Only a teleport by command moves him for good.
 */
public final class AcademyInstructor extends ShinigamiNpc {
    private static final float REIRYOKU = 500.0F;
    /** How far he may be nudged before he steps back, squared: half a block. */
    private static final double HOME_SLACK_SQR = 0.25;
    /** A saved home further than this from where he was saved, squared, is a copy's, not his: 2 blocks. */
    private static final double HOME_COPY_SQR = 4.0;
    private static final String HOME_TAG = "Home";

    /** Where he stands, set once he first stands on something. */
    @Nullable
    private Vec3 home;

    public AcademyInstructor(EntityType<? extends AcademyInstructor> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    @Override
    public DialogueView viewFor(SoulData visitor, long now) {
        DialogueView common = commonView(visitor);
        if (common != null) {
            return common;
        }
        return switch (visitor.state()) {
            case HUMAN -> ShinigamiLogic.isSubstitute(visitor) ? DialogueView.SUBSTITUTE : DialogueView.LIVING;
            case RUKONGAI -> ShinigamiLogic.isStudent(visitor) ? DialogueView.STUDENT : DialogueView.SOUL;
            default -> DialogueView.SHINIGAMI;
        };
    }

    @Override
    public String dialogueKey() {
        return "instructor";
    }

    @Override
    protected float soulReiryoku() {
        return REIRYOKU;
    }

    /** A Plus he sends on is already at the Academy. */
    @Override
    protected boolean sendsOn() {
        return false;
    }

    /** Where he stands and returns to, once he has stood somewhere. */
    @Nullable
    public Vec3 home() {
        return home;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            stayHome();
        }
    }

    private void stayHome() {
        if (home == null) {
            if (onGround()) {
                home = position();
            }
            return;
        }
        if (isPassenger()) {
            stopRiding();
        }
        if (position().distanceToSqr(home) > HOME_SLACK_SQR) {
            moveTo(home.x, home.y, home.z, getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
            resetFallDistance();
        }
    }

    /** A teleport by command moves him for good: he stands wherever he lands. */
    @Override
    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements,
            float yRot, float xRot) {
        boolean moved = super.teleportTo(level, x, y, z, relativeMovements, yRot, xRot);
        if (moved) {
            home = null;
        }
        return moved;
    }

    /** Arriving in another world, he stands wherever he lands there. */
    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        home = null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put(HOME_TAG, newDoubleList(home.x, home.y, home.z));
        }
    }

    /**
     * Data without an "Invulnerable" entry, such as a structure's, keeps him invulnerable. A home far
     * from where he was saved belongs to the instructor he was copied from, so he finds his own.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("Invulnerable")) {
            setInvulnerable(true);
        }
        ListTag saved = tag.getList(HOME_TAG, Tag.TAG_DOUBLE);
        Vec3 loaded = saved.size() == 3 ? new Vec3(saved.getDouble(0), saved.getDouble(1), saved.getDouble(2)) : null;
        home = loaded != null && loaded.distanceToSqr(position()) <= HOME_COPY_SQR ? loaded : null;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }
}
