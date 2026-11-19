package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.soul.GateLogic;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritEvents;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.GateState;
import io.github.masteryoga1.bleach.world.Shakonmaku;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * The keeper of one of Seireitei's four great gates: a giant about 8 blocks tall, in two hit boxes as the Gillian is
 * ({@link Part}), who stands before his gate on the Rukongai's side, speaks to a soul that uses him, and fights, on his
 * own ground only, a soul the gate bars or anyone who strikes him. No blow kills him: beaten to a tenth of his health he
 * yields, holds his gate up for half a minute and heals over ten minutes, and whoever struck him since he was last whole
 * may pass his wicket for a day.
 */
public class GateKeeper extends PathfinderMob implements DialogueNpc {
    public static final float WIDTH = 3.0F, HEIGHT = 8.0F, EYE_HEIGHT = 7.2F;
    /** The body part, hips to shoulders, from this high above the feet and this tall; the head part a cube above it. */
    public static final double BODY_BOTTOM = 3.0;
    public static final float BODY_HEIGHT = 3.0F, HEAD_SIZE = 2.0F;
    public static final int PART_BODY = 0, PART_HEAD = 1;
    /** How far past its sides a blow reaches, and how far above its head. */
    public static final double REACH = 3.5, REACH_UP = 2.0;
    /** The share of its health no blow takes it below: there, it yields. */
    public static final float YIELD_SHARE = 0.1F;
    /** How long it holds its gate up after it yields, and from the yield until it is whole again: ten minutes. */
    public static final int HOLD_TICKS = 600, HEAL_TICKS = 12_000;
    /** How long no blow must land on it, wounded but not beaten, before it heals, so that a fight is not undone. */
    public static final int REST_TICKS = 600;
    /** A blow's wind-up with its arms raised, and the wait from one blow to the next wind-up. */
    public static final int WIND_UP_TICKS = 10, COOLDOWN_TICKS = 40;
    /** How long after his last blow a foe it is not fighting stays its foe, so an old fight is not taken up on his return. */
    public static final int FORGET_TICKS = 100;
    /** The lines a soul striking it from beyond its ground is told, the souls about hear as it yields, and its winners. */
    public static final String FAR_KEY = "message.bleach.gate_keeper.far", YIELDS_KEY = "message.bleach.gate_keeper.yields",
            WON_KEY = "message.bleach.gate_keeper.won";
    /** A winner's note: how long its pass has to run. */
    private static final String TIME_LEFT_KEY = "screen.bleach.dialogue.note.time_left";
    /** Hakutōmon's, whose keeper speaks in his own manner. */
    private static final String HAKUTOMON = "hakutomon";
    private static final double TOLD_RANGE = 32.0;
    private static final long NEVER = Long.MIN_VALUE;
    /** What each keeper holds, by gate in the plan's order, for the look only. */
    private static final Item[][] WEAPONS = {{Items.IRON_SWORD}, {Items.MACE}, {Items.IRON_AXE, Items.IRON_AXE},
            {Items.TRIDENT}};
    private static final EntityDataAccessor<Byte> DATA_GATE =
            SynchedEntityData.defineId(GateKeeper.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_ARMS_UP =
            SynchedEntityData.defineId(GateKeeper.class, EntityDataSerializers.BOOLEAN);

    private final Part[] parts;
    /** Saved: the game time it last yielded, until it is whole again. */
    private long beatenAt = NEVER;
    /** Not saved, so a keeper read from disk may start its heal at once: the game time a blow last landed on it. */
    private long struckAt = NEVER;
    /** Not saved, so a keeper read from disk starts its fight over: who struck it last and when, and where its blow is. */
    @Nullable
    private UUID foe;
    private long foeAt;
    private int windUp = -1;
    private long readyAt;
    /** Not saved: every player whose blow has landed on it since it was last whole, who win its pass should it yield. */
    private final Set<UUID> strikers = new HashSet<>();

    public GateKeeper(EntityType<? extends GateKeeper> type, Level level) {
        super(type, level);
        parts = new Part[] {new Part(this, WIDTH, BODY_HEIGHT), new Part(this, HEAD_SIZE, HEAD_SIZE)};
        // Ids for itself and both parts, as the Gillian takes them.
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
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

    /** Whether its arms are raised, holding its gate up or winding up a blow. Both sides. */
    public boolean armsUp() {
        return entityData.get(DATA_ARMS_UP);
    }

    /** Whether it is beaten: from its yield until it is whole again. Server side. */
    public boolean isBeaten() {
        return beatenAt != NEVER && level().getGameTime() - beatenAt < HEAL_TICKS;
    }

    public Part body() {
        return parts[PART_BODY];
    }

    public Part head() {
        return parts[PART_HEAD];
    }

    /** Its canon name, by its gate. */
    @Override
    protected Component getTypeName() {
        return Component.translatable(getType().getDescriptionId() + "." + GateState.NAMES.get(gate()));
    }

    /** Jidanbō speaks in his own manner and the other three alike, each naming himself. Both sides. */
    @Override
    public String dialogueKey() {
        return GateState.NAMES.get(gate()).equals(HAKUTOMON) ? "gate_keeper." + HAKUTOMON : "gate_keeper";
    }

    /**
     * Used with the main hand by a soul that can touch him, he says what he has to say to it ({@link #viewFor}), with
     * the reason the gate bars it or how long its pass has to run; talking starts and ends no fight.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer visitor) || visitor.isSpectator() || !SoulLogic.hasSoul(visitor)
                || SpiritEvents.passesThrough(visitor, this)) {
            return InteractionResult.PASS;
        }
        SoulData data = SoulLogic.get(visitor);
        DialogueView view = viewFor(visitor);
        List<Component> notes = switch (view) {
            case KEEPER_BARRED -> List.of(Component.translatable(GateLogic.barredKey(data)));
            case KEEPER_WON -> List.of(Component.translatable(TIME_LEFT_KEY, QuincyArtsLogic.formatTicks(
                    data.shinigami().gates().until(gate()) - level().getGameTime())));
            default -> List.of();
        };
        getLookControl().setLookAt(visitor, 30.0F, 30.0F);
        BleachNetwork.openDialogue(visitor, this, view, List.of(), List.of(), notes);
        return InteractionResult.SUCCESS;
    }

    /** What he says to this soul now, by its soul, whether he is beaten, and whether he fights it for a blow. Server side. */
    public DialogueView viewFor(Player visitor) {
        return viewFor(SoulLogic.get(visitor), HollowPlayerLogic.survivalRules(visitor), gate(), level().getGameTime(),
                isBeaten(), visitor.getUUID().equals(foe) && canAttack(visitor));
    }

    /**
     * What a keeper of this gate says to a soul, the first that holds: to one that may pass and is fought for its blow,
     * that the fight ends off his ground; to one whose pass here counts, that he keeps his word; beaten, that he is;
     * else as the gates' rule answers, open or barred. A soul in creative or spectator passes.
     */
    public static DialogueView viewFor(SoulData data, boolean survival, int gate, long now, boolean beaten, boolean fought) {
        boolean passes = !survival || GateLogic.mayPass(data, gate, now);
        if (fought && passes) {
            return DialogueView.KEEPER_FOE;
        }
        if (passes && GateLogic.holdsPass(data, gate, now)) {
            return DialogueView.KEEPER_WON;
        }
        if (beaten) {
            return DialogueView.KEEPER_BEATEN;
        }
        return passes ? DialogueView.KEEPER_OPEN : DialogueView.KEEPER_BARRED;
    }

    /** No view of his offers an action, so whatever a client asks there is nothing to do. */
    @Override
    public void perform(Player visitor, NpcAction action) {
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Gillian.Navigation(this, level);
    }

    /** Its own goals: it strides straight at whom it fights and back to its post, and finds a soul it bars unseen. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FightGoal(this));
        goalSelector.addGoal(2, new PostGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null) {
            {
                // Its ground runs into the wicket's way, out of its sight; no invisibility or crouch hides a soul there.
                targetConditions = targetConditions.ignoreLineOfSight().ignoreInvisibilityTesting();
            }
        });
    }

    /**
     * Whom it fights, asked by every goal each tick: no one while beaten, nor a soul that cannot touch it, and on its
     * ground only a soul the gate bars, unless it holds his pass, or the one who struck it last; never a player in
     * creative or spectator mode, nor on Peaceful.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && target != this && !isBeaten() && standsOnGround(target)
                && !(target instanceof Player p && SpiritEvents.passesThrough(p, this))
                && (target instanceof Player q && !GateLogic.mayPass(q, gate()) || target.getUUID().equals(foe));
    }

    /**
     * Whether this entity's box meets its ground, before its house and the gap's Rukongai half, within its reach's height,
     * from its own feet to 2 above its head: a soul in a hole two deep below its feet is off it, however uneven the strip.
     */
    public boolean standsOnGround(Entity entity) {
        AABB ground = GateState.ground(gate());
        return new AABB(ground.minX, getY(), ground.minZ, ground.maxX, getY() + getBbHeight() + REACH_UP, ground.maxZ)
                .intersects(entity.getBoundingBox());
    }

    /** Hurt only by a blow: of a living attacker not itself, never while beaten; {@code /kill} and the void kill it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (isBeaten() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == this);
    }

    /**
     * A blow from beyond its ground does not land, and a player who struck it is told to come and face it; one that lands
     * makes the striker its foe (and a player in survival one of those who win his pass) and puts off its heal by half a
     * minute; one that leaves it at a tenth of its health makes it yield.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isBeaten()
                && source.getEntity() instanceof LivingEntity attacker && attacker != this && !standsOnGround(attacker)) {
            // Not a soul whose blows pass through it unseen: it has nothing to come and face.
            if (attacker instanceof Player player && !SpiritEvents.passesThrough(player, this)) {
                player.displayClientMessage(Component.translatable(FAR_KEY, getDisplayName())
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        boolean landed = super.hurt(source, amount);
        if (landed && level() instanceof ServerLevel server && isAlive() && !isBeaten()) {
            struckAt = server.getGameTime();
            if (source.getEntity() instanceof LivingEntity attacker && attacker != this) {
                foe = attacker.getUUID();
                foeAt = server.getGameTime();
                // Only a soul playing by survival's rules at the blow wins: in creative or spectator it passes anyway.
                if (attacker instanceof Player striker && amount > 0.0F && HollowPlayerLogic.survivalRules(striker)) {
                    strikers.add(striker.getUUID());
                }
            }
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

    /**
     * Beaten: it stops fighting, lifts its gate for half a minute and the souls about are told, and every player whose
     * blow landed since it was last whole wins its pass for a day.
     */
    private void yieldGate(ServerLevel level) {
        beatenAt = level.getGameTime();
        setTarget(null);
        foe = null;
        windUp = -1;
        getNavigation().stop();
        entityData.set(DATA_ARMS_UP, true);
        GateState.get(level).liftFor(level, gate(), HOLD_TICKS);
        Component line = Component.translatable(YIELDS_KEY, getDisplayName()).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer soul : SpiritSenses.awareNear(level, position(), TOLD_RANGE)) {
            soul.displayClientMessage(line, true);
        }
        Component won = Component.translatable(WON_KEY, getDisplayName()).withStyle(ChatFormatting.YELLOW);
        for (UUID each : strikers) {
            ServerPlayer winner = level.getServer().getPlayerList().getPlayer(each);
            // Only a soul playing by survival's rules wins: in creative or spectator it passes anyway.
            if (winner != null && HollowPlayerLogic.survivalRules(winner) && SoulLogic.hasSoul(winner)) {
                GateLogic.grantPass(SoulLogic.get(winner), gate(), level.getGameTime());
                if (GateLogic.mayPass(winner, gate())) {
                    winner.displayClientMessage(won, true);
                }
            }
        }
        strikers.clear();
    }

    /**
     * Once a second on the server, AI or none: a keeper away from Soul Society or not its gate's goes; a foe it is not
     * fighting is forgotten five seconds after his last blow; a whole one forgets its strikers, and a wounded one heals as
     * a beaten one does once no blow has landed for half a minute; a beaten one heals towards whole by the time since its
     * yield, holds its arms up while it holds its gate, then kneels until whole.
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
        if (foe != null && level.getGameTime() >= foeAt + FORGET_TICKS
                && (getTarget() == null || !foe.equals(getTarget().getUUID()))) {
            foe = null;
        }
        if (beatenAt == NEVER) {
            if (getHealth() >= getMaxHealth()) {
                strikers.clear();
            } else if (level.getGameTime() >= struckAt + REST_TICKS) {
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
        if (windUp < 0) {
            entityData.set(DATA_ARMS_UP, beatenAt != NEVER && since < HOLD_TICKS);
        }
        setPose(beatenAt != NEVER && since >= HOLD_TICKS ? Pose.CROUCHING : Pose.STANDING);
    }

    /** The parts follow the body on both sides, after it has moved; on the server it keeps to its footing. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            Vec3 kept = GateState.footing(gate(), position());
            if (!kept.equals(position())) {
                setPos(kept);
            }
        }
        tickParts();
    }

    /** Its parts stand where it does from the first, a keeper read from disk too. */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        tickParts();
    }

    private void tickParts() {
        float scale = getScale();
        parts[PART_BODY].resize(scale);
        parts[PART_HEAD].resize(scale);
        parts[PART_BODY].placeAt(getX(), getY() + BODY_BOTTOM * scale, getZ());
        parts[PART_HEAD].placeAt(getX(), getY() + (BODY_BOTTOM + BODY_HEIGHT) * scale, getZ());
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        // Null while the entity is being made; the constructor sets the ids again once the parts exist.
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    /** Its arms and weapons reach out of its box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.5, 2.0, 1.5);
    }

    /** Its arms reach past its sides and a little above its head. */
    @Override
    protected AABB getAttackBoundingBox() {
        return getBoundingBox().inflate(REACH, 0.0, REACH).expandTowards(0.0, REACH_UP, 0.0);
    }

    /** A heavy blow breaks any guard, whatever it holds. */
    @Override
    public boolean canDisableShield() {
        return true;
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

    /** One of the keeper's two hit boxes: its body from the hips to the shoulders, and its head (see {@link GiantPart}). */
    public static final class Part extends GiantPart<GateKeeper> {
        Part(GateKeeper parent, float width, float height) {
            super(parent, width, height);
        }

        /** A hand used on a part is used on the keeper, as a blow on it strikes him. */
        @Override
        public InteractionResult interact(Player player, InteractionHand hand) {
            return getParent().interact(player, hand);
        }
    }

    /**
     * The fight: it strides straight at its foe, kept to its footing, and when the foe is in reach it winds up with its
     * arms raised and brings one heavy blow down, which lands if the foe is in reach still. It needs no sight to strike.
     */
    static final class FightGoal extends Goal {
        private final GateKeeper keeper;

        FightGoal(GateKeeper keeper) {
            this.keeper = keeper;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = keeper.getTarget();
            return target != null && target.isAlive() && keeper.canAttack(target);
        }

        /** The fight over, whoever struck it is forgiven and its arms come down, unless it has just yielded. */
        @Override
        public void stop() {
            keeper.foe = null;
            keeper.windUp = -1;
            if (!keeper.isBeaten()) {
                keeper.entityData.set(DATA_ARMS_UP, false);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = keeper.getTarget();
            // Between the target goals' looks, a soul that has just left its ground or come to pass is let be too.
            if (target == null || !keeper.canAttack(target)) {
                return;
            }
            keeper.getLookControl().setLookAt(target, 30.0F, 30.0F);
            boolean inReach = keeper.isWithinMeleeAttackRange(target);
            if (!inReach) {
                Vec3 to = GateState.footing(keeper.gate(), target.position());
                keeper.getMoveControl().setWantedPosition(to.x, keeper.getY(), to.z, 1.0);
            }
            long now = keeper.level().getGameTime();
            if (keeper.windUp < 0 && inReach && now >= keeper.readyAt) {
                keeper.windUp = WIND_UP_TICKS;
                keeper.entityData.set(DATA_ARMS_UP, true);
            } else if (keeper.windUp > 0 && --keeper.windUp == 0) {
                keeper.windUp = -1;
                keeper.readyAt = now + COOLDOWN_TICKS;
                keeper.entityData.set(DATA_ARMS_UP, false);
                keeper.swing(InteractionHand.MAIN_HAND);
                if (keeper.isWithinMeleeAttackRange(target)) {
                    keeper.doHurtTarget(target);
                }
            }
        }
    }

    /** With no fight, it walks straight back to its post and stands there facing out of its gate. */
    static final class PostGoal extends Goal {
        private static final double CLOSE_SQR = 0.25;
        private final GateKeeper keeper;

        PostGoal(GateKeeper keeper) {
            this.keeper = keeper;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return keeper.getTarget() == null;
        }

        /** Its stride is asked for again each tick, as a path would ask for it, or it would walk one tick in two. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 post = GateState.post(keeper.gate());
            if (keeper.position().distanceToSqr(post.x, keeper.getY(), post.z) > CLOSE_SQR) {
                keeper.getMoveControl().setWantedPosition(post.x, keeper.getY(), post.z, 0.8);
            } else {
                keeper.setYRot(GateState.yaw(keeper.gate()));
                keeper.setYBodyRot(keeper.getYRot());
            }
        }
    }
}
