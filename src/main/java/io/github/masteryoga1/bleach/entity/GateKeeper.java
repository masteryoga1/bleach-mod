package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * The keeper of one of Seireitei's four great gates: a giant about 8 blocks tall who stands at his post before his gate
 * on the Rukongai's side. No blow kills him: beaten to a tenth of his health he yields, and heals over ten minutes.
 */
public class GateKeeper extends PathfinderMob {
    public static final float WIDTH = 3.0F, HEIGHT = 8.0F, EYE_HEIGHT = 7.2F;
    /** The share of its health no blow takes it below: there, it yields. */
    public static final float YIELD_SHARE = 0.1F;
    /** How long it stands with its arms raised after it yields, and from the yield until it is whole again. */
    public static final int HOLD_TICKS = 600, HEAL_TICKS = 12_000;
    /** How long no blow must land on it, wounded but not beaten, before it heals, so that a fight is not undone. */
    public static final int REST_TICKS = 600;
    /** The line the souls about hear when it yields. */
    public static final String YIELDS_KEY = "message.bleach.gate_keeper.yields";
    private static final double TOLD_RANGE = 32.0;
    private static final long NEVER = Long.MIN_VALUE;
    /** What each keeper holds, by gate in the plan's order, for the look only. */
    private static final Item[][] WEAPONS = {{Items.IRON_SWORD}, {Items.MACE}, {Items.IRON_AXE, Items.IRON_AXE},
            {Items.TRIDENT}};
    private static final EntityDataAccessor<Byte> DATA_GATE =
            SynchedEntityData.defineId(GateKeeper.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_ARMS_UP =
            SynchedEntityData.defineId(GateKeeper.class, EntityDataSerializers.BOOLEAN);

    /** Saved: the game time it last yielded, until it is whole again. */
    private long beatenAt = NEVER;
    /** Not saved, so a keeper read from disk may start its heal at once: the game time a blow last landed on it. */
    private long struckAt = NEVER;

    public GateKeeper(EntityType<? extends GateKeeper> type, Level level) {
        super(type, level);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    /** 400 health, armour 10, a blow of 8, slow, and never knocked back. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    /** A keeper of this gate, whole and armed, standing at its post facing out, not yet added. */
    public static GateKeeper at(ServerLevel level, int gate) {
        GateKeeper keeper = ModEntities.GATE_KEEPER.get().create(level);
        keeper.entityData.set(DATA_GATE, (byte) gate);
        for (int hand = 0; hand < WEAPONS[gate].length; hand++) {
            ItemStack weapon = new ItemStack(WEAPONS[gate][hand]);
            // Held for the look: no weapon adds to a keeper's blow.
            weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            keeper.setItemSlot(hand == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, weapon);
        }
        Vec3 post = GateState.post(gate);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(post.x), Mth.floor(post.z));
        keeper.moveTo(post.x, ground, post.z, GateState.yaw(gate), 0.0F);
        keeper.setYHeadRot(keeper.getYRot());
        keeper.setYBodyRot(keeper.getYRot());
        return keeper;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GATE, (byte) 0);
        builder.define(DATA_ARMS_UP, false);
    }

    /** The gate it keeps, 0 to 3 in the plan's order. Both sides. */
    public int gate() {
        return Mth.clamp(entityData.get(DATA_GATE), 0, GateState.NAMES.size() - 1);
    }

    /** Whether its arms are raised, as they are for half a minute after it yields. Both sides. */
    public boolean armsUp() {
        return entityData.get(DATA_ARMS_UP);
    }

    /** Whether it is beaten: from its yield until it is whole again. Server side. */
    public boolean isBeaten() {
        return beatenAt != NEVER && level().getGameTime() - beatenAt < HEAL_TICKS;
    }

    /** Its canon name, by its gate. */
    @Override
    protected Component getTypeName() {
        return Component.translatable(getType().getDescriptionId() + "." + GateState.NAMES.get(gate()));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Gillian.Navigation(this, level);
    }

    /** Its own goals: nothing moves it from its post, and it watches whoever comes near. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
    }

    /** Hurt only by a blow: of a living attacker not itself, never while beaten; {@code /kill} and the void kill it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (isBeaten() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == this);
    }

    /** A blow that lands puts off its heal by half a minute; one that leaves it at a tenth of its health makes it yield. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean landed = super.hurt(source, amount);
        if (landed && level() instanceof ServerLevel server && isAlive() && !isBeaten()) {
            struckAt = server.getGameTime();
            if (getHealth() <= floor() + 1.0E-3F) {
                yieldGate(server);
            }
        }
        return landed;
    }

    /** The health no blow takes it below. */
    private float floor() {
        return getMaxHealth() * YIELD_SHARE;
    }

    /**
     * The blow that would take a keeper to a tenth of its health or below is cut to leave it there, after armour and
     * every other change; a blow that passes invulnerability is left alone. Registered at the lowest priority.
     */
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof GateKeeper keeper && !keeper.level().isClientSide()
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setNewDamage(VisoredLogic.clampedBlow(event.getNewDamage(), keeper.getHealth(),
                    keeper.getAbsorptionAmount(), keeper.floor()));
        }
    }

    /** Beaten: it stops where it stands, raises its arms, and the souls about are told. */
    private void yieldGate(ServerLevel level) {
        beatenAt = level.getGameTime();
        getNavigation().stop();
        entityData.set(DATA_ARMS_UP, true);
        Component line = Component.translatable(YIELDS_KEY, getDisplayName()).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer soul : SpiritSenses.awareNear(level, position(), TOLD_RANGE)) {
            soul.displayClientMessage(line, true);
        }
    }

    /**
     * Once a second on the server, AI or none: a keeper away from Soul Society or not its gate's goes; a wounded one heals
     * as a beaten one does once no blow has landed for half a minute; a beaten one heals towards whole by the time since
     * its yield, holds its arms up for half a minute, then kneels until whole.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || tickCount % 20 != 0 || isRemoved() || !isAlive()) {
            return;
        }
        if (!Shakonmaku.in(level) || !getUUID().equals(GateState.get(level).keeper(gate()))) {
            discard();
            return;
        }
        if (beatenAt == NEVER) {
            if (getHealth() < getMaxHealth() && level.getGameTime() >= struckAt + REST_TICKS) {
                setHealth(getHealth() + (getMaxHealth() - floor()) * 20 / HEAL_TICKS);
            }
            return;
        }
        long since = level.getGameTime() - beatenAt;
        float due = floor() + (getMaxHealth() - floor()) * Math.min(1.0F, since / (float) HEAL_TICKS);
        setHealth(Math.max(getHealth(), due));
        if (since >= HEAL_TICKS) {
            beatenAt = NEVER;
        }
        entityData.set(DATA_ARMS_UP, beatenAt != NEVER && since < HOLD_TICKS);
        setPose(beatenAt != NEVER && since >= HOLD_TICKS ? Pose.CROUCHING : Pose.STANDING);
    }

    /** On the server it keeps to its footing, out of the gap. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            Vec3 kept = GateState.footing(gate(), position());
            if (!kept.equals(position())) {
                setPos(kept);
            }
        }
    }

    /** Its arms and weapons reach out of its box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.5, 2.0, 1.5);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Gate", (byte) gate());
        if (beatenAt != NEVER) {
            tag.putLong("BeatenAt", beatenAt);
        }
    }

    /** A missing gate is the first, a missing yield none. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_GATE, (byte) Mth.clamp(tag.getByte("Gate"), 0, GateState.NAMES.size() - 1));
        beatenAt = tag.contains("BeatenAt", Tag.TAG_ANY_NUMERIC) ? tag.getLong("BeatenAt") : NEVER;
    }
}
