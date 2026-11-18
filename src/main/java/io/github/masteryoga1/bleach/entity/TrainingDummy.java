package io.github.masteryoga1.bleach.entity;

import java.util.List;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * A straw training dummy for the Academy's students: a blow lands on it, and trains as {@link
 * io.github.masteryoga1.bleach.soul.SoulEvents} says, but its health never falls; it never moves, turns or rides, and
 * no creature takes it for a foe. Only {@code /kill} and the void take it away, with no death.
 */
public class TrainingDummy extends LivingEntity {
    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
    }

    /** A living entity's own, unmoved by a blow or a blast. */
    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes().add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Always whole: a blow's events see its damage land, but none of it keeps, and no harm can fell it. */
    @Override
    public void setHealth(float health) {
        super.setHealth(getMaxHealth());
    }

    /** {@code /kill} takes it away at once, as it does a creature that is not alive: no death, drop or orb. */
    @Override
    public void kill() {
        remove(RemovalReason.KILLED);
        gameEvent(GameEvent.ENTITY_DIE);
    }

    /** Below the world it is gone, as a plain entity is, rather than hurt there for ever. */
    @Override
    protected void onBelowWorld() {
        discard();
    }

    /** It never moves: no step, fall, knockback, blast, push or current carries it, though it pushes back. */
    @Override
    public void travel(Vec3 input) {
        setDeltaMovement(Vec3.ZERO);
    }

    /** Nor does a piston shove it, as it shoves an armour stand. */
    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    /** Nor does a portal it stands in take it to another world. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    /** Nothing carries it off: not a boat or a cart, nor Negación's light or a Caja. */
    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    /** Body and head always face where it was set, as an armour stand's do. */
    @Override
    protected float tickHeadTurn(float bodyTarget, float walked) {
        yBodyRotO = yHeadRotO = yRotO;
        yBodyRot = yHeadRot = getYRot();
        return 0.0F;
    }

    /** No creature takes it for a foe: no mob targets it, and no Fracción goes after one its Espada struck. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }
}
