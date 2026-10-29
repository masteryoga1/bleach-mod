package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.soul.VisoredEvents;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * A Shinigami's inner Hollow, stepped out of its host to fight it for the body: the host's white double, holding a
 * copy of the host's blade with the blade's own power taken out of it, and with a Hollow's soul as strong as the
 * host's (see {@link #setUp}). It fights only its host and can be hurt only by its host, half as much from afar.
 * It lives only while its fight does: {@link VisoredEvents} keeps the fight, and one that finds itself in none goes
 * at once. It is never saved, drops nothing, gives nothing for the kill and is no {@link Hollow}, so no Hollow rule
 * (purification, kill counts, hunts) touches it. It has a spirit's body: only those who see spirits hear it or
 * touch it.
 */
public class InnerHollow extends Monster {
    private static final double BLADE_SPEED = 1.2;
    private static final float LEAP_HEIGHT = 0.35F;
    private static final float LOOK_RANGE = 8.0F;
    private static final int AMBIENT_INTERVAL_TICKS = 200;

    // Kept in the tag only for /data and the like: the type is never saved.
    @Nullable
    private UUID hostId;
    /** The host it came out of, set by {@link #setUp}; a loaded or summoned one has none and goes on its first tick. */
    @Nullable
    private Player host;

    public InnerHollow(EntityType<? extends InnerHollow> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    /**
     * Its body before {@link #setUp} scales it to its host: 15 hearts, an Arrancar NPC's walk, a bare blow and 32
     * blocks of notice. No armour is given, so it stays at 0 and every blow on it lands in full.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /**
     * Makes it its host's Hollow, before it is added to the world: a Hollow's soul with the host's maximum reiryoku,
     * full, so neither outweighs the other; health and a blow scaled by the root of that reiryoku, at full health;
     * and a copy of the host's main-hand item, stripped of its attribute modifiers and enchantments so the blade
     * adds nothing to the blow, which never drops. An empty hand stays empty.
     */
    public void setUp(Player host) {
        this.host = host;
        this.hostId = host.getUUID();
        float reiryoku = SoulLogic.hasSoul(host) ? SoulLogic.get(host).maxReiryoku() : 0.0F;
        SoulData soul = getData(SoulAttachments.SOUL);
        SoulLogic.initialize(soul);
        SoulLogic.changeState(soul, SoulState.HOLLOW);
        soul.setMaxReiryoku(reiryoku);
        soul.setReiryoku(reiryoku);
        setBase(Attributes.MAX_HEALTH, VisoredLogic.hollowHealth(reiryoku));
        setBase(Attributes.ATTACK_DAMAGE, VisoredLogic.hollowBlow(reiryoku));
        setHealth(getMaxHealth());
        ItemStack held = host.getMainHandItem();
        ItemStack blade = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
        if (!blade.isEmpty()) {
            blade.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            blade.remove(DataComponents.ENCHANTMENTS);
        }
        setItemSlot(EquipmentSlot.MAINHAND, blade);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** The host it came out of; null for one never set up, and on the client. */
    @Nullable
    public Player host() {
        return host;
    }

    /** Its host's UUID; null for one never set up, and on the client. */
    @Nullable
    public UUID hostId() {
        return hostId;
    }

    /** Whether this is its host: the cause of a blow, or a creature it may fight. */
    private boolean isHost(@Nullable Entity entity) {
        return entity != null && hostId != null && hostId.equals(entity.getUUID());
    }

    // ---- Fighting ----

    /** It closes in, leaps and cuts once a second; it picks no target itself, as it has only ever had one. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LeapAtTargetGoal(this, LEAP_HEIGHT));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, BLADE_SPEED, true));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, LOOK_RANGE));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    /**
     * Its host is its target while the host lives, is in this world and is neither a spectator nor in creative mode;
     * otherwise it has none. A NoAI one never comes here.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Player found = host;
        if (found != null && found.isAlive() && !found.isSpectator() && !found.isCreative() && found.level() == level()) {
            if (getTarget() != found) {
                setTarget(found);
            }
        } else if (getTarget() != null) {
            setTarget(null);
        }
    }

    /** Only its host. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return isHost(target) && super.canAttack(target);
    }

    /** Nothing but its host's blows reaches it, save what passes every guard ({@code /kill}, the void). */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source)
                || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isHost(source.getEntity());
    }

    /**
     * A blow from afar (its direct cause is not its host: an arrow, a Getsuga Tenshō's wave) counts for
     * {@code [visored.duel] ranged_share} of itself, so the fight cannot be won by keeping away. A host blow that
     * lands puts off its mending (see {@link VisoredEvents#noteHostBlow}).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean server = !level().isClientSide();
        float dealt = amount;
        if (server && source.getDirectEntity() != source.getEntity()) {
            dealt = (float) (amount * BleachConfig.get(BleachConfig.INNER_HOLLOW_RANGED_SHARE));
        }
        boolean hurt = super.hurt(source, dealt);
        if (hurt && server && isHost(source.getEntity())) {
            VisoredEvents.noteHostBlow(this, level().getGameTime());
        }
        return hurt;
    }

    /**
     * On the server, after its tick: one that is not its fight's own Hollow (never set up, summoned, or its fight
     * over) goes at once, and one that has outlived any fight it could be in ends it, so it is never left behind.
     */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && !isRemoved()) {
            if (VisoredEvents.duelOf(hostId) != this) {
                discard();
            } else if (tickCount > VisoredLogic.timeLimitTicks() + VisoredLogic.LIFE_MARGIN_TICKS) {
                VisoredEvents.forget(this, server.getGameTime());
            }
        }
    }

    // ---- A piece of its host, never a creature of its own ----

    /** It goes with its fight, never because players are far. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Peaceful ends its fight instead (see {@link VisoredEvents}), which takes it away. */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    /** Taking up the copied blade makes no vibration, as its steps make none. */
    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return false;
    }

    /** It rides nothing, so no boat or cart carries it out of its ring. */
    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    // ---- A spirit's senses ----

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.HOLLOW_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HOLLOW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.HOLLOW_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return AMBIENT_INTERVAL_TICKS;
    }

    /** Only those who can see it are kept from sleeping by one. */
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

    /** Pushed only by what can touch a spirit. */
    @Override
    public void push(Entity entity) {
        if (SpiritSenses.bodiesTouch(this, entity)) {
            super.push(entity);
        }
    }

    /** Pushes only what can touch a spirit. */
    @Override
    protected void doPush(Entity entity) {
        if (SpiritSenses.bodiesTouch(this, entity)) {
            super.doPush(entity);
        }
    }

    /** Only the aware see it splash into water. The sound goes through {@link #playSound}. */
    @Override
    protected void doWaterSplashEffect() {
        if (level() instanceof ServerLevel server) {
            super.doWaterSplashEffect();
            SpiritSenses.splash(server, this);
        }
    }

    /** No vibrations from its steps. */
    @Override
    public boolean isSteppingCarefully() {
        return true;
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    /** Pressure plates and tripwires never feel it. */
    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    // ---- Saving ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hostId != null) {
            tag.putUUID("Host", hostId);
        }
    }

    /** The host's UUID only: the host itself is never found again, so a loaded one is in no fight and goes. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hostId = tag.hasUUID("Host") ? tag.getUUID("Host") : null;
        host = null;
    }
}
