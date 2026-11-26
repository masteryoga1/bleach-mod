package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.Materialisation;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
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
public abstract class MaterialisedSpirit extends Monster {
    private static final float LOOK_RANGE = 8.0F;

    /** Its wielder, set by {@link #setUp}; a summoned one has none, and goes on its first tick. Server side only. */
    @Nullable
    private Player wielder;
    @Nullable
    private UUID wielderId;
    @Nullable
    private ResourceLocation spiritId;

    protected MaterialisedSpirit(EntityType<? extends MaterialisedSpirit> type, Level level) {
        super(type, level);
        xpReward = 0;
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
     * Makes it this wielder's spirit, before it is added to the world: a soul as strong as the wielder's, full, so the
     * reiatsu cut weighs neither's blows, and in its hand what {@link #held} gives, never dropped.
     */
    public void setUp(Player wielder, ResourceLocation spiritId, ZanpakutoSpirit spirit) {
        this.wielder = wielder;
        this.wielderId = wielder.getUUID();
        this.spiritId = spiritId;
        float reiryoku = SoulLogic.get(wielder).maxReiryoku();
        SoulData soul = getData(SoulAttachments.SOUL);
        SoulLogic.initialize(soul);
        SoulLogic.changeState(soul, SoulState.SHINIGAMI);
        soul.setMaxReiryoku(reiryoku);
        soul.setReiryoku(reiryoku);
        setItemSlot(EquipmentSlot.MAINHAND, held(spirit));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    /** What it holds in its main hand: nothing, unless its kind says otherwise. */
    protected ItemStack held(ZanpakutoSpirit spirit) {
        return ItemStack.EMPTY;
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

    /** It looks about, and at whoever is near. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, LOOK_RANGE));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    /** On the server, after its tick: {@link Materialisation#upkeep} sends it home once any end has come. */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && !isRemoved()) {
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
