package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.QuincyCrossItem;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyNpcEvents;
import io.github.masteryoga1.bleach.soul.QuincyNpcLogic;
import io.github.masteryoga1.bleach.soul.QuincyRansotengai;
import io.github.masteryoga1.bleach.soul.QuincyTeaching;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Quincy Elder: an old master in white who comes to those with Quincy blood. He greets them,
 * gives a lesson a day, teaches Ransōtengai to one at the third tier, and gives a power sealed after
 * Letzt Stil back with an arrow just beside the heart (see {@link #beginRestore}). He shoots the
 * Hollows that hunt his visitor. Nothing but what passes through invulnerability can hurt him, and no
 * Hollow hunts him.
 */
public final class QuincyElder extends QuincyNpc {
    /** Shots at the heart he tries before he gives up for now. */
    public static final int RESTORE_ATTEMPTS = 3;
    /** How long he tries for, from when he is asked. */
    public static final int RESTORE_WINDOW_TICKS = 600;
    /** How far in front of the soul he stands to shoot. */
    public static final double RESTORE_STAND_OFF = 5.0;
    /** How long he waits after a miss before drawing again. */
    public static final int RETRY_TICKS = 40;

    /** He stays at least this long once asked to restore a power. */
    private static final int RESTORE_LIFETIME = 700;
    /** He stays this long past the end of the trial he set, to see how it went. */
    private static final int TRIAL_LINGER_TICKS = 200;
    /** A heart shot not heard of this long after its flight should have ended counts as a miss. */
    private static final int ARROW_GRACE_TICKS = 2;

    // Server-side, not saved: a reload, or a soul that logs out, ends a restoration.
    @Nullable
    private Player restoreTarget;
    private int restoreAttempts;
    private long restoreUntil;
    private long nextShotAt;
    @Nullable
    private HeiligPfeil restoreArrow;
    private long restoreArrowUntil;
    @Nullable
    private QuincyNpcLogic.Visit reason;

    public QuincyElder(EntityType<? extends QuincyElder> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new ElderRestoreGoal(this));
    }

    // ---- Who he is ----

    @Override
    protected Bloodline soulBloodline() {
        return Bloodline.ECHT;
    }

    @Override
    protected int soulTier() {
        return 3;
    }

    @Override
    protected float soulReiryoku() {
        return 400.0F;
    }

    @Override
    protected float arrowDamage() {
        return (float) BleachConfig.get(BleachConfig.ELDER_ARROW_DAMAGE);
    }

    @Override
    public int shotInterval() {
        return 40;
    }

    @Override
    public float shotInaccuracy() {
        return 0.5F;
    }

    @Override
    public float minRange() {
        return 4.0F;
    }

    @Override
    public float maxRange() {
        return 24.0F;
    }

    @Override
    public String dialogueKey() {
        return "elder";
    }

    /** Why he came, if he came on a visit; not saved. */
    @Nullable
    public QuincyNpcLogic.Visit reason() {
        return reason;
    }

    public void setReason(@Nullable QuincyNpcLogic.Visit value) {
        reason = value;
    }

    /**
     * Only the Hollows that hunt the soul he came for. One who stays, and came for no one, guards
     * every player.
     */
    @Override
    public boolean wantsToShoot(LivingEntity target) {
        if (!super.wantsToShoot(target) || !(target instanceof Hollow hollow)) {
            return false;
        }
        LivingEntity prey = hollow.getTarget();
        if (prey == null) {
            return false;
        }
        UUID visitor = visitor();
        return visitor != null ? visitor.equals(prey.getUUID()) : prey instanceof Player;
    }

    // ---- Talking ----

    @Override
    public DialogueView viewFor(Player visitor, SoulData data, long now) {
        return QuincyNpcLogic.elderView(data, now, trainingDay(now), BleachConfig.get(BleachConfig.ELDER_MAX_LESSONS));
    }

    /** Which day of training it is, as the lessons count them. */
    private long trainingDay(long now) {
        return QuincyArtsLogic.gloveDay(now, level().getDayTime());
    }

    /**
     * No lesson when there is none today (sealed, today's given, or all of them given); no
     * restoration until the body is at the end of its strength; no trial while the power cannot
     * weave the threads.
     */
    @Override
    public List<NpcAction> unreadyFor(Player visitor, SoulData data, DialogueView view, long now) {
        List<NpcAction> unready = new ArrayList<>();
        if (!QuincyNpcLogic.lessonAvailable(data, trainingDay(now), BleachConfig.get(BleachConfig.ELDER_MAX_LESSONS))) {
            unready.add(NpcAction.LESSON);
        }
        if (!QuincyArtsLogic.restorable(visitor, data, now, true)) {
            unready.add(NpcAction.RESTORE);
        }
        if (QuincyRansotengai.trialRefusal(visitor, data, now) != null) {
            unready.add(NpcAction.RANSOTENGAI);
        }
        return unready;
    }

    /**
     * The lessons given and whether today's is, for those he teaches; what a restoration needs; and
     * why the trial cannot be taken now.
     */
    @Override
    public List<Component> notesFor(Player visitor, SoulData data, DialogueView view, long now) {
        List<Component> notes = new ArrayList<>();
        switch (view) {
            case ELDER_GREETING, ELDER_LESSON, ELDER_IDLE -> {
                int max = BleachConfig.get(BleachConfig.ELDER_MAX_LESSONS);
                QuincyTeaching teaching = data.quincy().teaching();
                notes.add(Component.translatable("screen.bleach.dialogue.note.lessons",
                        Math.min(teaching.lessons(), max), max));
                if (teaching.lessons() < max && teaching.lessonDay() == trainingDay(now)) {
                    notes.add(Component.translatable("screen.bleach.dialogue.note.next_lesson"));
                }
            }
            case ELDER_RESTORE -> {
                if (!QuincyArtsLogic.restorable(visitor, data, now, true)) {
                    notes.add(Component.translatable("screen.bleach.dialogue.note.restore_needs",
                            Math.round(BleachConfig.get(BleachConfig.RESTORE_MAX_HEALTH_FRACTION) * 100.0),
                            BleachConfig.get(BleachConfig.RESTORE_MAX_FOOD) + 1));
                }
            }
            case ELDER_TEACH -> {
                String refusal = QuincyRansotengai.trialRefusal(visitor, data, now);
                if (refusal != null) {
                    notes.add(Component.translatable(refusal));
                }
            }
            default -> {
            }
        }
        return notes;
    }

    @Override
    protected void act(ServerPlayer visitor, SoulData data, NpcAction action, long now) {
        switch (action) {
            case LESSON -> {
                if (QuincyNpcEvents.giveLesson(visitor, data, now, level().getDayTime())) {
                    businessDone();
                }
            }
            case RANSOTENGAI -> {
                // A choice from a dialogue opened before the trial began, or before it was passed.
                if (QuincyRansotengai.trialOpen(data, now) || data.quincy().teaching().ransotengaiKnown()) {
                    return;
                }
                QuincyRansotengai.beginTrial(visitor, data, now);
                if (!isPermanent() && QuincyRansotengai.trialOpen(data, now)) {
                    long stay = data.quincy().teaching().trialUntil() - now + TRIAL_LINGER_TICKS;
                    setLifetime((int) Math.min(Integer.MAX_VALUE, Math.max(lifetime(), stay)));
                }
            }
            case RESTORE -> beginRestore(visitor, now);
            default -> {
            }
        }
    }

    /**
     * Once spoken to he stays two minutes at most; the first time, the soul has met him, if he
     * speaks to them as one of his own.
     */
    @Override
    protected void onDialogueOpened(ServerPlayer visitor, SoulData data, long now) {
        if (!isPermanent()) {
            setLifetime(Math.min(lifetime(), QuincyNpcLogic.TALKED_LIFETIME));
        }
        QuincyTeaching teaching = data.quincy().teaching();
        if (!teaching.elderMet() && QuincyNpcLogic.hasQuincyBlood(data)
                && data.state() != SoulState.HOLLOW && data.state() != SoulState.ARRANCAR) {
            data.setQuincy(data.quincy().withTeaching(teaching.withElderMet(true)));
            Milestones.grant(visitor, Milestones.ELDER_MET);
        }
    }

    /** His business with the visitor is done: he stays half a minute more at most. */
    private void businessDone() {
        if (!isPermanent()) {
            setLifetime(Math.min(lifetime(), QuincyNpcLogic.DONE_LIFETIME));
        }
    }

    // ---- Restoration ----

    /** The soul whose sealed power he is trying to bring back, if any. Server side. */
    @Nullable
    public Player restoreTarget() {
        return restoreTarget;
    }

    /**
     * He sets about bringing this soul's sealed power back: he tells them to stand still and face
     * him, walks to five blocks in front of them and shoots just beside the heart, up to three times
     * in thirty seconds. The arrow does it ({@link HeiligPfeil}), if it passes where it must while
     * the soul is at the end of its strength.
     *
     * @return false, changing nothing, if the soul's power is not sealed
     */
    public boolean beginRestore(Player target, long now) {
        if (!SoulLogic.hasSoul(target) || !QuincyArtsLogic.isSealed(SoulLogic.get(target))) {
            return false;
        }
        restoreTarget = target;
        restoreAttempts = RESTORE_ATTEMPTS;
        restoreUntil = now + RESTORE_WINDOW_TICKS;
        nextShotAt = now;
        restoreArrow = null;
        if (!isPermanent()) {
            setLifetime(Math.max(lifetime(), RESTORE_LIFETIME));
        }
        say(target, "message.bleach.elder.restore_stand");
        return true;
    }

    /**
     * Looses the heart shot at this soul now, from his eyes to just beside their heart, dead true. It
     * harms no one but Hollows. A shot at the soul he is restoring is watched, so a miss is noticed.
     */
    public void loose(Player target) {
        if (level().isClientSide) {
            return;
        }
        Vec3 eye = getEyePosition();
        Vec3 heart = QuincyArtsLogic.heartPoint(target.position(), target.yBodyRot, target.getBbHeight());
        HeiligPfeil arrow = HeiligPfeil.launch(level(), this, eye, heart.subtract(eye), 1.0F, arrowDamage(), 0,
                ARROW_SPEED, 0.0F);
        SpiritSenses.play(this, ModSounds.HEILIG_PFEIL_FIRE.get(), getSoundSource(), 1.0F, 0.8F, false);
        if (target == restoreTarget) {
            restoreArrow = arrow;
            restoreArrowUntil = level().getGameTime() + arrow.lifeTicks() + ARROW_GRACE_TICKS;
        }
    }

    /** The power came back by his arrow: the restoration is over, and so, nearly, is his visit. */
    public void onRestored(Player target) {
        clearRestore();
        businessDone();
    }

    /**
     * A heart shot went by without bringing the power back: he tries again after a moment, or, his
     * three tries spent, gives up for now. The soul may ask again.
     */
    public void restoreMissed(long now) {
        Player target = restoreTarget;
        if (target == null) {
            return;
        }
        restoreArrow = null;
        if (--restoreAttempts <= 0) {
            giveUp(target);
            return;
        }
        say(target, "message.bleach.elder.restore_missed");
        nextShotAt = now + RETRY_TICKS;
    }

    private void giveUp(Player target) {
        say(target, "message.bleach.elder.restore_gave_up");
        clearRestore();
    }

    private void clearRestore() {
        restoreTarget = null;
        restoreArrow = null;
        restoreAttempts = 0;
    }

    /** He speaks to the soul, in chat. */
    private static void say(Player target, String key) {
        target.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /**
     * Server side: a restoration ends when its soul is gone, dead, elsewhere or no longer sealed; a
     * heart shot that is gone without bringing the power back is a miss; and past the window, with no
     * shot in the air, he gives up.
     */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved() || restoreTarget == null) {
            return;
        }
        Player target = restoreTarget;
        if (target.isRemoved() || !target.isAlive() || target.level() != level() || !SoulLogic.hasSoul(target)
                || !QuincyArtsLogic.isSealed(SoulLogic.get(target))) {
            clearRestore();
            return;
        }
        long now = level().getGameTime();
        if (restoreArrow != null && (restoreArrow.isRemoved() || now > restoreArrowUntil)) {
            restoreMissed(now);
            if (restoreTarget == null) {
                return;
            }
        }
        if (restoreArrow == null && now >= restoreUntil) {
            giveUp(target);
        }
    }

    /** Data without an "Invulnerable" entry, such as a structure's or a summon's, keeps him invulnerable. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("Invulnerable")) {
            setInvulnerable(true);
        }
    }

    /**
     * Walks to five blocks in front of the soul he is restoring, as they face, looks at their heart,
     * draws for {@link QuincyNpc#DRAW_TICKS} and looses, then waits for the shot to land or miss.
     */
    private static final class ElderRestoreGoal extends Goal {
        /** Within this of his spot, squared, he stops and shoots: 1.5 blocks. */
        private static final double ARRIVE_SQR = 1.5 * 1.5;
        private static final int REPATH_TICKS = 10;
        private static final float LOOK_SPEED = 30.0F;

        private final QuincyElder elder;
        private int repathIn;
        private int drawn;

        ElderRestoreGoal(QuincyElder elder) {
            this.elder = elder;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return elder.restoreTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            return elder.restoreTarget != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            repathIn = 0;
            drawn = 0;
        }

        @Override
        public void stop() {
            drawn = 0;
            elder.stopUsingItem();
            elder.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player target = elder.restoreTarget;
            if (target == null) {
                return;
            }
            Vec3 feet = target.position();
            float yaw = target.yBodyRot;
            Vec3 heart = QuincyArtsLogic.heartPoint(feet, yaw, target.getBbHeight());
            elder.getLookControl().setLookAt(heart.x, heart.y, heart.z, LOOK_SPEED, LOOK_SPEED);
            // Recomputed every tick from how the soul faces now, in case they turn.
            float radians = yaw * Mth.DEG_TO_RAD;
            Vec3 spot = feet.add(-Mth.sin(radians) * RESTORE_STAND_OFF, 0.0, Mth.cos(radians) * RESTORE_STAND_OFF);
            double dx = elder.getX() - spot.x;
            double dz = elder.getZ() - spot.z;
            if (dx * dx + dz * dz > ARRIVE_SQR) {
                lower();
                if (--repathIn <= 0) {
                    repathIn = REPATH_TICKS;
                    elder.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
                }
                return;
            }
            elder.getNavigation().stop();
            if (elder.restoreArrow != null || elder.level().getGameTime() < elder.nextShotAt) {
                lower();
                return;
            }
            if (!elder.isUsingItem() && QuincyCrossItem.is(elder.getMainHandItem())) {
                elder.startUsingItem(InteractionHand.MAIN_HAND);
                SpiritSenses.play(elder, ModSounds.QUINCY_CROSS_DRAW.get(), elder.getSoundSource(), 0.6F, 1.0F, false);
            }
            // Counted here, so the shot comes even if the Cross has been taken from his hand.
            if (++drawn >= DRAW_TICKS) {
                lower();
                elder.loose(target);
            }
        }

        private void lower() {
            drawn = 0;
            if (elder.isUsingItem()) {
                elder.stopUsingItem();
            }
        }
    }
}
