package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The instructor at the Academy: performs Konsō for a Plus, enrolls souls that have passed on, sits
 * students' exams, and hands out Asauchi. He cannot be hurt or moved, so the Academy always works.
 */
public final class AcademyInstructor extends ShinigamiNpc {
    private static final float REIRYOKU = 500.0F;

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

    /** Data without an "Invulnerable" entry, such as a structure's, keeps him invulnerable. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("Invulnerable")) {
            setInvulnerable(true);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
