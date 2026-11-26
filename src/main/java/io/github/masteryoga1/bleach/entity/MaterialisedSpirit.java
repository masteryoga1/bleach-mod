package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.soul.Materialisation;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * A zanpakutō's spirit drawn out of its wielder's inner world into this one (materialisation, gushōka). Each spirit
 * that can be drawn out has a type of its own, named by its file's {@code bankai.entity}, and a subclass of this, set
 * up from its wielder as an inner Hollow is from its host ({@link #setUp}), drawn out by its call
 * ({@link Materialisation#callOut}), and lives only while that call holds it: one held by no one (summoned, or its call
 * over) goes at once. It is never saved, drops nothing, gives nothing for the kill, and is in {@code #bleach:spirits}:
 * only those who see spirits see, hear or touch it, and no mundane creature takes it for a target. No spawner or
 * natural spawning makes one, and being far from players never takes it away; Peaceful does.
 */
public abstract class MaterialisedSpirit extends Monster implements BossFloor.Floored {
    /** The most health and blow a spirit is scaled to: the attribute's own top, and 30. */
    public static final float MAX_HEALTH = 1024.0F, MAX_BLOW = 30.0F;
    private static final double CHASE_SPEED = 1.2;
    private static final float LOOK_RANGE = 8.0F;

    /** Its wielder, set by {@link #setUp}; a summoned one has none, and goes on its first tick. Server side only. */
    @Nullable
    private Player wielder;
    @Nullable
    private UUID wielderId;
    @Nullable
    private ResourceLocation spiritId;
    private int colour = 0xFFFFFF;
    /** Its bar, named for its kind and shown to its wielder alone, as the fight is the wielder's own. */
    private final BossBar bar;
    /** Whether a blow has left it on its floor. */
    private boolean beaten;

    protected MaterialisedSpirit(EntityType<? extends MaterialisedSpirit> type, Level level) {
        super(type, level);
        xpReward = 0;
        bar = new BossBar(this, "bossbar.bleach." + EntityType.getKey(type).getPath(), barColour(),
                BossEvent.BossBarOverlay.PROGRESS).onlyFor(viewer -> viewer == wielder);
    }

    /** Its body before {@link #setUp}: 45 health, a light blow and no armour. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /**
     * Health by its wielder's maximum reiryoku: {@code health_base} and {@code health_per_root} for each square root of
     * it, half again the inner Hollow's by default, and never above {@link #MAX_HEALTH}.
     */
    public static float health(float reiryoku) {
        double health = BleachConfig.get(BleachConfig.BANKAI_SPIRIT_HEALTH_BASE)
                + BleachConfig.get(BleachConfig.BANKAI_SPIRIT_HEALTH_PER_ROOT) * Math.sqrt(Math.max(0.0F, reiryoku));
        return (float) Math.min(MAX_HEALTH, health);
    }

    /**
     * A blow by its wielder's maximum reiryoku, as the inner Hollow's by default, times the wielder's own Zanjutsu
     * multiplier, so a better swordsman's spirit cuts harder; never above {@link #MAX_BLOW}.
     */
    public static float blow(float reiryoku, float zanjutsu) {
        double blow = BleachConfig.get(BleachConfig.BANKAI_SPIRIT_BLOW_BASE)
                + BleachConfig.get(BleachConfig.BANKAI_SPIRIT_BLOW_PER_ROOT) * Math.sqrt(Math.max(0.0F, reiryoku));
        return (float) Math.min(MAX_BLOW, blow * zanjutsu);
    }

    /**
     * Makes it this wielder's spirit, before it is added to the world: a soul as strong as the wielder's, full, so the
     * reiatsu cut weighs neither's blows; health and a blow by the wielder's reiryoku ({@link #health}, {@link #blow}),
     * at full health; its spirit's colour; and in its hand what {@link #held} gives, never dropped.
     */
    public void setUp(Player wielder, ResourceLocation spiritId, ZanpakutoSpirit spirit) {
        this.wielder = wielder;
        this.wielderId = wielder.getUUID();
        this.spiritId = spiritId;
        this.colour = spirit.colour();
        float reiryoku = SoulLogic.get(wielder).maxReiryoku();
        SoulData soul = getData(SoulAttachments.SOUL);
        SoulLogic.initialize(soul);
        SoulLogic.changeState(soul, SoulState.SHINIGAMI);
        soul.setMaxReiryoku(reiryoku);
        soul.setReiryoku(reiryoku);
        setBase(Attributes.MAX_HEALTH, health(reiryoku));
        setHealth(getMaxHealth());
        setBase(Attributes.ATTACK_DAMAGE, blow(reiryoku, SoulLogic.zanjutsuMultiplier(SoulLogic.get(wielder))));
        setItemSlot(EquipmentSlot.MAINHAND, held(spirit));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    /** What it holds in its main hand: nothing, unless its kind says otherwise. */
    protected ItemStack held(ZanpakutoSpirit spirit) {
        return ItemStack.EMPTY;
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Its wielder, its wielder's UUID and the spirit it is, as the bond names it: null for one set up by no one. */
    @Nullable
    public Player wielder() {
        return wielder;
    }

    @Nullable
    public UUID wielderId() {
        return wielderId;
    }

    @Nullable
    public ResourceLocation spiritId() {
        return spiritId;
    }

    /** Its spirit's colour, for the dust it comes and goes in. */
    public int colour() {
        return colour;
    }

    /** Whether this is its wielder: the cause of a blow, or a creature it may fight. */
    private boolean isWielder(@Nullable Entity entity) {
        return entity != null && wielderId != null && wielderId.equals(entity.getUUID());
    }

    /** Its bar's colour: white, unless its kind says otherwise. Asked once, while it is being made. */
    protected BossEvent.BossBarColor barColour() {
        return BossEvent.BossBarColor.WHITE;
    }

    public BossBar bar() {
        return bar;
    }

    /** The share of its full health ({@code floor_share}) that no blow takes it below but {@code /kill}. */
    @Override
    public float floor() {
        return (float) (getMaxHealth() * BleachConfig.get(BleachConfig.BANKAI_SPIRIT_FLOOR_SHARE));
    }

    /** Beaten: it goes home on its next tick ({@link Materialisation#upkeep}). */
    @Override
    public void atFloor(DamageSource source) {
        beaten = true;
    }

    public boolean beaten() {
        return beaten;
    }

    /**
     * Only its wielder's blows reach it, the wielder's arts among them, and what passes every guard ({@code /kill}, the
     * void): another soul's, a creature's, a stray Getsuga Tenshō or Cero, a fall, fire and a blast all pass it by.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source)
                || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isWielder(source.getEntity());
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.forget(player);
    }

    /** It closes in and cuts; it picks no target itself, as it has only ever had one. Melee alone: its arts are later. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, CHASE_SPEED, true));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, LOOK_RANGE));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    /**
     * Its wielder is its target while the wielder lives, is in this world and is neither a spectator nor in creative
     * mode, invisible or not, as no targeting conditions are asked; otherwise it waits with none.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Player found = wielder;
        if (found != null && found.isAlive() && !found.isSpectator() && !found.isCreative() && found.level() == level()) {
            if (getTarget() != found) {
                setTarget(found);
            }
        } else if (getTarget() != null) {
            setTarget(null);
        }
    }

    /** Only its wielder. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return isWielder(target) && super.canAttack(target);
    }

    /** On the server, after its tick: its bar, then {@link Materialisation#upkeep}, which sends it home at any end. */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && !isRemoved()) {
            bar.tick();
            Materialisation.upkeep(this);
        }
    }

    // ---- A piece of its wielder's soul, never a creature of its own ----

    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    /** It is set up before it joins the world, where an item's equipping would sound at the world's origin. */
    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    /** No portal takes it from its wielder's world. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    // ---- A spirit's senses, as every spirit's ----

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return SoulLogic.seesSpirits(player);
    }

    /** Every sound, footsteps included, goes only to those who can hear spirits. Nothing is played on the client. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent() && !level().isClientSide()) {
            SpiritSenses.play(this, sound, getSoundSource(), volume, pitch, false);
        }
    }

    @Override
    public void push(Entity entity) {
        if (SpiritSenses.bodiesTouch(this, entity)) {
            super.push(entity);
        }
    }

    @Override
    protected void doPush(Entity entity) {
        if (SpiritSenses.bodiesTouch(this, entity)) {
            super.doPush(entity);
        }
    }

    @Override
    protected void doWaterSplashEffect() {
        if (level() instanceof ServerLevel server) {
            super.doWaterSplashEffect();
            SpiritSenses.splash(server, this);
        }
    }

    @Override
    public boolean isSteppingCarefully() {
        return true;
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
