package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.FlashStep;
import io.github.masteryoga1.bleach.soul.Hirenkyaku;
import io.github.masteryoga1.bleach.soul.QuincyNpcLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A living Quincy of the Human World, in white with a Quincy Cross: a Human, not a spirit, so anyone
 * can see and talk to one, the unaware included, yet one who sees spirits and whose body and arrows
 * meet Hollows. Each hunts Hollows ({@link #isQuarry}) with Heilig Pfeil from the Cross, never a
 * player, and talks through the same dialogue as the Shinigami ({@link DialogueNpc}).
 * <p>
 * One placed by an egg, a command or a structure stays ({@link #PERMANENT}). One that comes on a
 * visit has a {@link #lifetime()} and a {@link #visitor()}: when the time runs out, or the visitor
 * has been gone a while, it steps away with Hirenkyaku, and it is never saved. Where a visiting one
 * would leave, a permanent one only steps aside.
 * <p>
 * Goals and navigation are made inside the constructor of {@code Mob}, before any field here is set,
 * so {@link #registerGoals} reads only constants.
 */
public abstract class QuincyNpc extends PathfinderMob implements RangedAttackMob, DialogueNpc {
    /** How close a visitor must stand to talk, and to have their answer count, squared: 8 blocks. */
    public static final double TALK_RANGE_SQR = 64.0;
    /** How long a shot is drawn before it is loosed, as a player's default look draws it. */
    public static final int DRAW_TICKS = 20;
    /** How fast the arrows fly, in blocks a tick. */
    public static final float ARROW_SPEED = 3.2F;
    /** The lifetime of one who stays. */
    public static final int PERMANENT = -1;
    /** Farther than this from a visiting Quincy, the visitor counts as gone. */
    public static final double VISITOR_RANGE = 48.0;
    /** A visiting Quincy leaves once the visitor has been gone this long. */
    public static final int VISITOR_ABSENT_TICKS = 200;
    /** The Hunter's Blut Vene is showing; synced, for the veins on him. */
    public static final int FLAG_VENE = 1;

    protected static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(QuincyNpc.class, EntityDataSerializers.BYTE);

    /** How far a Quincy steps with Hirenkyaku when leaving or stepping aside. */
    private static final double STEP_DISTANCE = 6.0;
    /** A step shorter than this goes nowhere, and is not taken. */
    private static final double STEP_MIN = 2.0;
    /** How often the visitor is looked for. */
    private static final int VISITOR_CHECK_TICKS = 20;
    /** How many visitors' last views are remembered before the memory is cleared. */
    private static final int MAX_REMEMBERED_VIEWS = 8;

    /** Why a Quincy NPC leaves. */
    public enum LeaveCause {
        /** Its visit is over. */
        TIME,
        /** Its business with the visitor is done. */
        DONE,
        /** A player struck it. */
        HURT_BY_PLAYER,
        /** Too badly hurt to fight on. */
        RETREAT,
        /** The visitor has gone. */
        VISITOR_GONE,
        /** Sent away by command: the one cause that takes even a permanent one. */
        COMMAND
    }

    // Server-side state. Read only in tick and later, never while the goals are made.
    private int lifetime = PERMANENT;
    @Nullable
    private UUID visitor;
    private int visitorAbsentTicks;
    /** A leave put off while Gritz held the body; tried again every tick. Not saved. */
    @Nullable
    private LeaveCause pendingLeave;
    /** The last view each visitor was shown, so a choice offered then still counts. Not saved. */
    private final Map<UUID, DialogueView> shownViews = new HashMap<>();

    protected QuincyNpc(EntityType<? extends QuincyNpc> type, Level level) {
        super(type, level);
        if (!level.isClientSide) {
            // Saved equipment replaces this when the NPC is loaded. No equip sound on the first tick.
            equip(EquipmentSlot.MAINHAND, new ItemStack(ModItems.QUINCY_CROSS.get()));
            equip(EquipmentSlot.CHEST, new ItemStack(ModItems.QUINCY_TUNIC.get()));
            equip(EquipmentSlot.LEGS, new ItemStack(ModItems.QUINCY_TROUSERS.get()));
            equip(EquipmentSlot.FEET, new ItemStack(ModItems.QUINCY_SHOES.get()));
        }
    }

    private void equip(EquipmentSlot slot, ItemStack stack) {
        setItemSlot(slot, stack);
        setDropChance(slot, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    // ---- What each Quincy is ----

    /** The blood of this Quincy's soul. */
    protected abstract Bloodline soulBloodline();

    /** The tier of this Quincy's power. */
    protected abstract int soulTier();

    /** This Quincy's maximum reiryoku, which sets its reiatsu. */
    protected abstract float soulReiryoku();

    /** The damage of each arrow, read from the config when it is loosed. */
    protected abstract float arrowDamage();

    /** Ticks to wait after a shot before drawing again. */
    public abstract int shotInterval();

    /** How far the shots stray, in the arrow's inaccuracy units. */
    public abstract float shotInaccuracy();

    /** Nearer than this, the archer backs away from its target. */
    public abstract float minRange();

    /** Farther than this, the archer walks in towards its target. */
    public abstract float maxRange();

    /** What this Quincy says to a visitor with this soul now. */
    public abstract DialogueView viewFor(Player visitor, SoulData data, long now);

    /**
     * The choices this visitor cannot have yet, which the screen shows greyed out. Only those the
     * view offers matter.
     */
    public abstract List<NpcAction> unreadyFor(Player visitor, SoulData data, DialogueView view, long now);

    /** A few short lines under the speech, at most four, such as how many lessons are left. */
    public abstract List<Component> notesFor(Player visitor, SoulData data, DialogueView view, long now);

    /** Does what the visitor chose, once {@link #perform} has checked they may have it. */
    protected abstract void act(ServerPlayer visitor, SoulData data, NpcAction action, long now);

    /** Called once a visitor has been shown the dialogue. */
    protected void onDialogueOpened(ServerPlayer visitor, SoulData data, long now) {
    }

    /** Called just before this Quincy leaves for good, whatever the cause. Server side. */
    protected void onLeave(LeaveCause cause) {
    }

    // ---- Targets ----

    /** Whether this Quincy would shoot this creature now. Every Quincy hunts only quarry. */
    public boolean wantsToShoot(LivingEntity target) {
        return isQuarry(target);
    }

    /**
     * Only what this Quincy would shoot. The target goal asks this every other tick and puts back a
     * target that was let go while this still holds, so a Hollow no longer worth an arrow must fail
     * here to be dropped for good.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        return wantsToShoot(target) && super.canAttack(target);
    }

    /**
     * What a living Quincy hunts: a living Hollow that is not leaving or dissolving. Never Fishbone D,
     * which stays the soul's own first fight, and never a player.
     */
    public static boolean isQuarry(LivingEntity target) {
        return target instanceof Hollow hollow && hollow.isAlive() && !hollow.isDeparting() && !hollow.isPurifying()
                && !(hollow instanceof FishboneD);
    }

    // ---- Lifetime and visitor ----

    /** Ticks left of this visit, or {@link #PERMANENT}. */
    public int lifetime() {
        return lifetime;
    }

    public void setLifetime(int ticks) {
        lifetime = Math.max(PERMANENT, ticks);
    }

    public boolean isPermanent() {
        return lifetime < 0;
    }

    /** The soul this Quincy came for, if any. */
    @Nullable
    public UUID visitor() {
        return visitor;
    }

    public void setVisitor(@Nullable UUID uuid) {
        visitor = uuid;
        visitorAbsentTicks = 0;
    }

    /**
     * The visitor, if they are here: online, alive, in this level and within {@link #VISITOR_RANGE}.
     * Server side; null on the client.
     */
    @Nullable
    public ServerPlayer visitorPlayer() {
        if (visitor == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(visitor);
        if (player == null || player.level() != level() || !player.isAlive()
                || player.distanceToSqr(this) > VISITOR_RANGE * VISITOR_RANGE) {
            return null;
        }
        return player;
    }

    /** Whether Blut Vene shows on this Quincy, on either side. */
    public boolean veneShown() {
        return (entityData.get(DATA_FLAGS) & FLAG_VENE) != 0;
    }

    protected void setFlag(int flag, boolean on) {
        byte flags = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? flags | flag : flags & ~flag);
        if (next != flags) {
            entityData.set(DATA_FLAGS, next);
        }
    }

    /** Whether the visit counts down now. */
    protected boolean countsDown() {
        return true;
    }

    /**
     * Leaves with Hirenkyaku: a flash of reishi to a point six blocks behind, and gone. A permanent
     * Quincy only steps aside, unless sent away by command. A body Gritz holds cannot step away, so
     * the leaving waits until it is free. Server side.
     */
    public void leave(LeaveCause cause) {
        if (!(level() instanceof ServerLevel server) || isRemoved() || !isAlive()) {
            return;
        }
        if (isPermanent() && cause != LeaveCause.COMMAND) {
            pendingLeave = null;
            stepAside(behind());
            return;
        }
        if (cause != LeaveCause.COMMAND && hasEffect(ModEffects.GRITZ)) {
            pendingLeave = cause;
            return;
        }
        pendingLeave = null;
        onLeave(cause);
        if (isRemoved()) {
            return;
        }
        Vec3 from = position();
        Hirenkyaku.effects(server, from, from.add(behind().scale(STEP_DISTANCE)));
        SpiritSenses.play(server, from.x, from.y, from.z, ModSounds.HIRENKYAKU.get(), getSoundSource(), 0.8F, 1.0F,
                false);
        discard();
    }

    /** Flat, the way this Quincy's back is turned. */
    protected Vec3 behind() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
    }

    /**
     * A Hirenkyaku step of up to six blocks along the ground in this direction, as far as the body
     * fits: none without AI or while Gritz holds it, and none that would go less than two blocks.
     *
     * @return whether it stepped
     */
    protected boolean stepAside(Vec3 direction) {
        return hirenkyaku(direction, STEP_DISTANCE, STEP_MIN);
    }

    /**
     * A Hirenkyaku step of up to {@code distance} blocks along the ground in this direction, as far as
     * the body fits safely, leaving a trail of reishi. Not taken without AI, while Gritz holds the
     * body, or when it would go less than {@code minDistance}. Server side.
     *
     * @return whether it stepped
     */
    protected boolean hirenkyaku(Vec3 direction, double distance, double minDistance) {
        if (!(level() instanceof ServerLevel server) || isNoAi() || hasEffect(ModEffects.GRITZ)) {
            return false;
        }
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        if (flat.lengthSqr() < 1.0E-6) {
            return false;
        }
        Vec3 start = position();
        Vec3 end = FlashStep.sweep(server, this, getBoundingBox(), flat.normalize(), distance, true).end();
        if (!Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z)
                || end.distanceTo(start) < minDistance) {
            return false;
        }
        teleportTo(end.x, end.y, end.z);
        getNavigation().stop();
        resetFallDistance();
        Hirenkyaku.effects(server, start, end);
        SpiritSenses.play(server, start.x, start.y, start.z, ModSounds.HIRENKYAKU.get(), getSoundSource(), 0.8F, 1.0F,
                false);
        return true;
    }

    // ---- Life ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new QuincyBowGoal(this, 1.0));
        goalSelector.addGoal(4, new NpcAttendGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Hollow.class, 10, true, false,
                this::wantsToShoot));
    }

    /**
     * Server side, every tick, so it all still happens with NoAI: the soul on the first tick, a target
     * no longer worth shooting let go, a leave that Gritz put off, the visit counting down, and the
     * visitor being looked for.
     */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (!SoulLogic.hasSoul(this)) {
            QuincyNpcLogic.initSoul(getData(SoulAttachments.SOUL), soulBloodline(), soulTier(), soulReiryoku());
        }
        if (!isAlive()) {
            return;
        }
        // The target goal keeps what it picked; one no longer worth an arrow is let go, so he can attend.
        LivingEntity target = getTarget();
        if (target != null && !wantsToShoot(target)) {
            setTarget(null);
        }
        if (pendingLeave != null) {
            leave(pendingLeave);
            if (isRemoved()) {
                return;
            }
        }
        if (lifetime > 0 && countsDown()) {
            lifetime--;
        }
        if (lifetime == 0) {
            leave(LeaveCause.TIME);
            if (isRemoved()) {
                return;
            }
        }
        // Paused with the visit itself: a Hunter in a contest stays for it, wherever his visitor goes.
        if (!isPermanent() && visitor != null && countsDown() && tickCount % VISITOR_CHECK_TICKS == 0) {
            if (visitorPlayer() != null) {
                visitorAbsentTicks = 0;
            } else {
                visitorAbsentTicks += VISITOR_CHECK_TICKS;
                if (visitorAbsentTicks >= VISITOR_ABSENT_TICKS) {
                    leave(LeaveCause.VISITOR_GONE);
                }
            }
        }
    }

    // ---- Talking ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // A living Quincy can be seen by anyone, so awareness is not needed to talk.
        if (player instanceof ServerPlayer visitor && !visitor.isSpectator() && SoulLogic.hasSoul(visitor)
                && distanceToSqr(visitor) <= TALK_RANGE_SQR) {
            openDialogue(visitor);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void openDialogue(ServerPlayer visitor) {
        SoulData data = SoulLogic.get(visitor);
        long now = level().getGameTime();
        getLookControl().setLookAt(visitor, 30.0F, 30.0F);
        DialogueView view = viewFor(visitor, data, now);
        if (shownViews.size() >= MAX_REMEMBERED_VIEWS && !shownViews.containsKey(visitor.getUUID())) {
            shownViews.clear();
        }
        shownViews.put(visitor.getUUID(), view);
        BleachNetwork.openDialogue(visitor, this, view, List.of(), unreadyFor(visitor, data, view, now),
                notesFor(visitor, data, view, now));
        onDialogueOpened(visitor, data, now);
    }

    /**
     * Does what a visitor asked for in the dialogue, if they may still have it: this Quincy is alive,
     * the visitor is close, not a spectator and has a soul, and the choice is offered by what this
     * Quincy would say to them now, or by what it said when they opened the dialogue (talking can
     * change that, as a first greeting does), and is not one they cannot have yet.
     */
    @Override
    public void perform(Player visitor, NpcAction action) {
        if (!isAlive() || visitor.isSpectator() || distanceToSqr(visitor) > TALK_RANGE_SQR
                || !SoulLogic.hasSoul(visitor)) {
            return;
        }
        SoulData data = SoulLogic.get(visitor);
        long now = level().getGameTime();
        DialogueView view = viewFor(visitor, data, now);
        if (!action.allowedIn(view)) {
            DialogueView shown = shownViews.get(visitor.getUUID());
            if (shown == null || !action.allowedIn(shown)) {
                return;
            }
            view = shown;
        }
        if (unreadyFor(visitor, data, view, now).contains(action) || !(visitor instanceof ServerPlayer player)) {
            return;
        }
        shownViews.remove(visitor.getUUID());
        act(player, data, action, now);
    }

    // ---- Archery ----

    /**
     * Looses a Heilig Pfeil from the eyes at the target: at a Hollow's mask, or the middle of anything
     * else, led by how far the target will have moved while the arrow flies.
     */
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (level().isClientSide) {
            return;
        }
        Vec3 eye = getEyePosition();
        Vec3 aim = target instanceof Hollow hollow ? HollowMask.maskCenter(hollow) : target.getBoundingBox().getCenter();
        Vec3 motion = target.getDeltaMovement();
        if (target.onGround()) {
            // Standing on the ground, it is not falling, whatever gravity's pull on its motion says.
            motion = new Vec3(motion.x, 0.0, motion.z);
        }
        aim = aim.add(motion.scale(eye.distanceTo(aim) / ARROW_SPEED));
        HeiligPfeil.launch(level(), this, eye, aim.subtract(eye), 1.0F, arrowDamage(), 0, ARROW_SPEED,
                shotInaccuracy());
        SpiritSenses.play(this, ModSounds.HEILIG_PFEIL_FIRE.get(), getSoundSource(), 1.0F,
                0.9F + getRandom().nextFloat() * 0.2F, false);
    }

    // ---- Saving and the world ----

    /** A visiting Quincy is never saved: it is gone when its chunk unloads or the world closes. */
    @Override
    public boolean shouldBeSaved() {
        return lifetime < 0 && super.shouldBeSaved();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return isPermanent();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", lifetime);
        if (visitor != null) {
            tag.putUUID("Visitor", visitor);
        }
    }

    /** Data without a "Lifetime", such as a structure's or a summon's, makes one who stays. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifetime = tag.contains("Lifetime") ? Math.max(PERMANENT, tag.getInt("Lifetime")) : PERMANENT;
        visitor = tag.hasUUID("Visitor") ? tag.getUUID("Visitor") : null;
        visitorAbsentTicks = 0;
    }
}
