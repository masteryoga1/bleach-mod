package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModEffects;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.ArrancarEvents;
import io.github.masteryoga1.bleach.soul.ArrancarNpcEvents;
import io.github.masteryoga1.bleach.soul.ArrancarNpcLogic;
import io.github.masteryoga1.bleach.soul.ArrancarRank;
import io.github.masteryoga1.bleach.soul.Bala;
import io.github.masteryoga1.bleach.soul.EspadaLedger;
import io.github.masteryoga1.bleach.soul.FlashStep;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.HollowTier;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PowerPath;
import io.github.masteryoga1.bleach.soul.Sonido;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritClip;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * An Arrancar NPC: a Hollow that tore off its mask, come through a Garganta for a strong soul. A Número comes
 * alone; a Privaron Espada, the number of the seat it lost tattooed on it, comes with Números as its Fracción.
 * Each wears the white uniform its own way (some without the jacket, showing the hole), keeps a mask remnant and a
 * sealed blade, and has an Arrancar's soul, so the player arts' own rules give it its Hierro, Sonído and Bala.
 *
 * <p>It fights with the blade ({@link BladeGoal}), a charged Cero ({@link CeroGoal}), Bala and Sonído (in
 * {@link #customServerAiStep}). A Fracción follows its leader ({@link FollowLeaderGoal}) and turns on whatever
 * strikes the leader or the leader fights ({@link GuardLeaderGoal}); the leader lets them fight first
 * ({@link HoldBackGoal}). An
 * arrival leaves through a tear of its own on time or once idle ({@link #depart}), and a beaten leader may be lifted
 * away by Negación ({@link ArrancarNpcEvents#maybeRecall}). It has a spirit's body, as a Hollow has: only those who
 * see spirits hear it or touch it.
 *
 * <p>A Número with no living leader, beaten low by a seated Espada player, kneels to it a while ({@link #yieldTo});
 * used with an empty hand it pledges itself as that player's Fracción ({@link #pledgeTo}) and stays until sent away
 * or until its Espada stops being an Arrancar ({@link #keepPledge}, {@link EspadaLedger}). A pledged one hunts
 * nothing of its own: it fights what strikes it, what strikes its Espada and what its Espada strikes, never what its
 * Espada may not harm ({@link #mayHarmForEspada}), and holds its post while its Espada is away.
 *
 * <p>Its rank is its own and never the roster's: no NPC holds an Espada seat ({@link ArrancarNpcLogic#unseated}).
 */
public class ArrancarNpc extends Monster implements CeroBeam.Caster, OwnableEntity {
    /** {@link #leaveAt} for one that stays until killed (a spawn egg, a summons): no timed leave and no idle leave. */
    public static final long NEVER = -1L;
    /** {@link #leaveAt} for an arrival with no lifetime: no timed leave, but it still leaves once idle. */
    public static final long UNTIMED = Long.MAX_VALUE;
    /** One that is leaving and whose tear has gone missing leaves on its own after this long, as a Hollow does. */
    public static final int DEPART_TIMEOUT_TICKS = GargantaRift.DEPART_AGE + 40;
    /** The modifier that carries {@code [arrancar.npc] damage_multiplier} on to its melee blows. */
    public static final ResourceLocation DAMAGE_ID = BleachMod.id("arrancar_npc_damage");
    /** Ticks after a first Sneak-use by its Espada in which a second one sends a pledged Fracción away. */
    public static final int DISMISS_CONFIRM_TICKS = 100;
    /**
     * Sneak-uses closer together than this are one held press (the use key repeats every 4 ticks), so only a fresh
     * press confirms a dismiss.
     */
    public static final int DISMISS_PRESS_GAP_TICKS = 10;
    /** Ticks it lies fallen before it breaks into smoke, as a Hollow does. */
    static final int DEATH_TICKS = 20;
    /** How often a leader not found is looked for again. Plumbing, not balance. */
    static final int LEADER_LOOKUP_TICKS = 20;
    /** How long a blow counts as just now: for guarding the leader, and for the leader's own "hurt recently". */
    static final int GUARD_MEMORY_TICKS = 100;
    /** Up to this many ticks are added at random to the first Cero and each cooldown, so a group does not fire in step. */
    static final int CERO_JITTER_TICKS = 40;
    /** How long a broken charge waits before the next. */
    static final int CERO_RETRY_TICKS = 40;
    /** How far in front of the face the Cero gathers, in blocks. */
    static final double MUZZLE_AHEAD = 0.6;
    /** Shorter Sonído steps are not worth the flash. */
    static final double SONIDO_MIN_STEP = 2.0;
    /** A closing step lands this far short of the prey, within the blade's reach. */
    static final double SONIDO_STOP_SHORT = 2.0;
    /** How long a closing or catching-up Sonído that found no footing (water, a gap) waits before it looks again. */
    static final int SONIDO_RETRY_TICKS = 10;
    /** Only a blow from this close starts a dodge roll. */
    static final double DODGE_REACH = 4.0;
    /** The drop from a tear (at most 6 blocks) does it no harm, but a cliff does. */
    static final double SAFE_FALL_BLOCKS = 10.0;

    /** How far short of a wall right in front of the face the Cero gathers, as {@link CeroBeam#muzzleOf} keeps it. */
    private static final double MUZZLE_WALL_MARGIN = 0.1;
    private static final double BLADE_SPEED = 1.2;
    private static final double FOLLOW_SPEED = 1.1;
    private static final float LOOK_TURN = 30.0F;
    /** How far ahead the head looks along a locked aim when there is no prey to measure by. */
    private static final double LOCKED_LOOK_DISTANCE = 8.0;
    /** Told to anyone but its Espada who uses a pledged Fracción with an empty hand. */
    private static final String NOT_YOURS = "message.bleach.fraccion.not_yours";
    /** The worn pieces a player's kill may drop one of; never the blade. */
    private static final EquipmentSlot[] UNIFORM_SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** The number tattooed on it, 0 for none: rank.tattooNumber() as the server reads its config. Synced. */
    private static final EntityDataAccessor<Byte> DATA_TATTOO =
            SynchedEntityData.defineId(ArrancarNpc.class, EntityDataSerializers.BYTE);

    // Saved.
    private ArrancarRank rank = ArrancarRank.NONE;
    @Nullable
    private UUID leaderId;
    private long leaveAt = NEVER;
    /** Whether a beaten leader has had its one roll for a recall, so it cannot be farmed for more. */
    private boolean recallRolled;
    /** Whether it has been dressed once, so a summons that names its clothes keeps them. */
    private boolean dressed;
    /** Whether it is a player Espada's Fracción, pledged to its leader; see {@link #pledgeTo}. */
    private boolean pledged;
    /** The game time it pledged, read against {@link EspadaLedger#fraccionForgottenAt}; 0 when not pledged. */
    private long pledgedAt;

    // Server side, not saved: a reloaded NPC starts these over.
    /** The seated Espada it kneels to now, beaten; null when it does not kneel. A kneel is never saved. */
    @Nullable
    private UUID yieldedTo;
    private long yieldUntil;
    /** Until when its Espada's first Sneak-use stands, so a second one sends it away; -1 when none is armed. */
    private long dismissArmedUntil = -1L;
    /** When its Espada last Sneak-used it, to tell a held press from a second one; -1 when none. */
    private long lastDismissUse = -1L;
    private boolean departing;
    private boolean statsApplied;
    private long departAt;
    private long idleSince = -1L;
    private long ceroReadyAt;
    private long balaReadyAt;
    private long sonidoReadyAt;
    private long sonidoRetryAt;
    private long leaderLookupAt;
    @Nullable
    private LivingEntity cachedLeader;
    /** The target GuardLeaderGoal took, kept while that goal runs even with no leader left to guard. */
    @Nullable
    private LivingEntity guarding;
    /** The Cero gathering now, set and cleared by CeroGoal and the Caster callbacks. */
    @Nullable
    CeroBeam charging;

    public ArrancarNpc(EntityType<? extends ArrancarNpc> type, Level level) {
        super(type, level);
        // No orbs: the unaware would see them. Its slayer is given its experience directly.
        xpReward = 0;
    }

    /**
     * A Número's body, before the config sets each kind's own on its first tick ({@link #applyRankStats}): 30 hearts,
     * a quick walk, a bare blow (the blade adds its own), 40 blocks of notice, some armour and footing, and a fall
     * from a tear that does not hurt.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_BLOCKS);
    }

    // ---- Rank and set-up ----

    /**
     * Makes it what it is, once in its life (callers check {@link #isSetUp}): its rank (none rolls a Número, and a
     * seat becomes that seat's Privaron number), the form it rose from and its reiryoku, rolled in its kind's band,
     * an Arrancar's soul, its kind's body at full health, its clothes and blade, the wait for its first Cero, and
     * when it leaves ({@link #NEVER}, {@link #UNTIMED} or a game time). Server side.
     */
    public void setUp(ArrancarRank rank, RandomSource random, long leaveAt) {
        this.rank = ArrancarNpcLogic.unseated(rank.kind() == ArrancarRank.Kind.NONE
                ? ArrancarNpcLogic.rollNumero(random) : rank);
        ArrancarRank.Kind kind = this.rank.kind();
        HollowTier origin = ArrancarNpcLogic.rollOrigin(kind, random.nextDouble());
        float reiryoku = ArrancarNpcLogic.rollMaxReiryoku(kind, random.nextDouble());
        SoulData soul = getData(SoulAttachments.SOUL);
        SoulLogic.initialize(soul);
        SoulLogic.changeState(soul, SoulState.ARRANCAR);
        soul.setTier(PowerPath.HOLLOW, origin.tier());
        soul.setMaxReiryoku(reiryoku);
        soul.setReiryoku(reiryoku);
        // The soul's own Arrancar record keeps no rank: that is the roster's, for players only.
        statsApplied = false;
        applyRankStats();
        setHealth(getMaxHealth());
        dress(random, origin);
        ceroReadyAt = level().getGameTime() + BleachConfig.get(BleachConfig.ARRANCAR_NPC_FIRST_CERO_SECONDS) * 20L
                + random.nextInt(CERO_JITTER_TICKS + 1);
        this.leaveAt = leaveAt;
    }

    /** Whether it has been set up: it has its soul. */
    public boolean isSetUp() {
        return SoulLogic.hasSoul(this);
    }

    /** Its rank: a Número or a Privaron Espada once set up, never an Espada seat. */
    public ArrancarRank rank() {
        return rank;
    }

    /** NUMERO or PRIVARON once set up. */
    public ArrancarRank.Kind kind() {
        return rank.kind();
    }

    /** Whether it leads a Fracción: a Privaron Espada. */
    public boolean isLeader() {
        return kind() == ArrancarRank.Kind.PRIVARON;
    }

    /** The number tattooed on it, 0 for none. Both sides. */
    public int tattooNumber() {
        return entityData.get(DATA_TATTOO) & 0xFF;
    }

    /** Its soul. On the server, one never set up (a summons with data, a game test) is set up first. */
    public SoulData soul() {
        if (!isSetUp() && level() instanceof ServerLevel) {
            setUp(rank, random, leaveAt);
        }
        return SoulLogic.get(this);
    }

    /**
     * Its kind's body from the config, again after every load so a changed config reaches it: the base health,
     * armour, speed, footing and notice, a blow of 1 and its kind's bonus (the blade adds its own damage less 1),
     * and the damage multiplier. It changes nothing that is already so, and leaves the current health alone.
     */
    private void applyRankStats() {
        BleachConfig.ArrancarNpcKind numbers = ArrancarNpcLogic.numbers(rank.kind());
        setBase(Attributes.MAX_HEALTH, BleachConfig.get(numbers.health()));
        setBase(Attributes.ARMOR, BleachConfig.get(numbers.armor()));
        setBase(Attributes.MOVEMENT_SPEED, BleachConfig.get(numbers.speed()));
        setBase(Attributes.KNOCKBACK_RESISTANCE, BleachConfig.get(numbers.knockbackResistance()));
        setBase(Attributes.FOLLOW_RANGE, BleachConfig.get(BleachConfig.ARRANCAR_NPC_FOLLOW_RANGE));
        setBase(Attributes.ATTACK_DAMAGE, 1.0 + BleachConfig.get(numbers.attackBonus()));
        setMultiplier(Attributes.ATTACK_DAMAGE, DAMAGE_ID,
                BleachConfig.get(BleachConfig.ARRANCAR_NPC_DAMAGE_MULTIPLIER) - 1.0);
        refreshTattoo();
        statsApplied = true;
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    /** A permanent multiplier on the total of an attribute, or none when it would change nothing. */
    private void setMultiplier(Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (Math.abs(amount) < 1.0E-6) {
            instance.removeModifier(id);
        } else {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * A saved NPC whose rank was lost or damaged keeps its soul, so it is never set up again: it is given a rank of
     * the kind its reiryoku fits, and keeps the rest.
     */
    private void repairRank() {
        double privaronFloor = BleachConfig.get(BleachConfig.ARRANCAR_NPC_PRIVARON.minMaxReiryoku());
        rank = soul().maxReiryoku() >= privaronFloor
                ? ArrancarNpcLogic.rollPrivaron(random) : ArrancarNpcLogic.rollNumero(random);
        statsApplied = false;
        refreshTattoo();
    }

    private void refreshTattoo() {
        entityData.set(DATA_TATTOO, (byte) rank.tattooNumber());
    }

    /**
     * The uniform, only on one never dressed whose armour is all empty, so a summons that names any piece keeps
     * what it gave: zōri, hakama, and the jacket unless it goes bare-chested. A blade in an empty hand whatever it
     * wears, and nothing it wears or holds ever drops by chance (a kill's one uniform piece is rolled apart).
     */
    private void dress(RandomSource random, HollowTier origin) {
        if (!dressed && getItemBySlot(EquipmentSlot.HEAD).isEmpty() && getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && getItemBySlot(EquipmentSlot.LEGS).isEmpty() && getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.ARRANCAR_ZORI.get()));
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.ARRANCAR_HAKAMA.get()));
            if (random.nextDouble() >= BleachConfig.get(BleachConfig.ARRANCAR_NPC_BARE_CHESTED_SHARE)) {
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.ARRANCAR_JACKET.get()));
            }
        }
        if (getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, ArrancarEvents.npcBlade(this, origin));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
        dressed = true;
    }

    // ---- Fracción ----

    /** The leader this one follows, by UUID, kept when the leader is gone; null for a leader or a lone Número. */
    @Nullable
    public UUID leaderId() {
        return leaderId;
    }

    /** Follows this creature from now on, or no one with null. */
    public void setLeader(@Nullable UUID id) {
        leaderId = id;
        cachedLeader = null;
        leaderLookupAt = 0L;
    }

    /**
     * The leader, if it is loaded and alive in this world: looked up by UUID at most once a second, and kept while
     * it stays so. Any living creature may lead, a player too. A pledged Fracción counts its Espada as away beyond
     * {@code [arrancar.fraccion] follow_leash}, though it keeps it in mind for when it comes back. Null on the client.
     */
    @Nullable
    public LivingEntity leader() {
        if (leaderId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        if (cachedLeader != null && (!cachedLeader.isAlive() || cachedLeader.level() != server
                || !leaderId.equals(cachedLeader.getUUID()))) {
            cachedLeader = null;
        }
        long now = server.getGameTime();
        if (cachedLeader == null && now >= leaderLookupAt) {
            leaderLookupAt = now + LEADER_LOOKUP_TICKS;
            if (server.getEntity(leaderId) instanceof LivingEntity found && found != this && found.isAlive()) {
                cachedLeader = found;
            }
        }
        if (pledged && cachedLeader != null) {
            double leash = BleachConfig.get(BleachConfig.FRACCION_FOLLOW_LEASH);
            if (distanceToSqr(cachedLeader) > leash * leash) {
                return null;
            }
        }
        return cachedLeader;
    }

    /** The living Arrancar NPCs within this many blocks that follow this one. */
    public List<ArrancarNpc> fraccionNear(double range) {
        UUID id = getUUID();
        double rangeSqr = range * range;
        return level().getEntitiesOfClass(ArrancarNpc.class, getBoundingBox().inflate(range),
                npc -> npc != this && npc.isAlive() && id.equals(npc.leaderId()) && npc.distanceToSqr(this) <= rangeSqr);
    }

    /**
     * Whether it may fight its target now. A Número always may. A leader that holds back lets its Fracción fight
     * first: it joins once hurt in the last five seconds, once its prey comes within {@code leader_joins_within},
     * or once none of its Fracción is within {@code guard_range}.
     */
    public boolean mayFight() {
        if (!isLeader() || !BleachConfig.get(BleachConfig.ARRANCAR_NPC_LEADER_HOLDS_BACK) || hurtRecently(this)) {
            return true;
        }
        LivingEntity target = getTarget();
        double joins = BleachConfig.get(BleachConfig.ARRANCAR_NPC_LEADER_JOINS_WITHIN);
        if (target != null && distanceToSqr(target) <= joins * joins) {
            return true;
        }
        return fraccionNear(BleachConfig.get(BleachConfig.ARRANCAR_NPC_GUARD_RANGE)).isEmpty();
    }

    /** Whether a creature hurt this one within the last {@link #GUARD_MEMORY_TICKS}, by its own clock. */
    private static boolean hurtRecently(LivingEntity entity) {
        return entity.getLastHurtByMob() != null
                && entity.tickCount - entity.getLastHurtByMobTimestamp() <= GUARD_MEMORY_TICKS;
    }

    /**
     * Whether this creature struck another within the last {@link #GUARD_MEMORY_TICKS}, by its own clock. Vanilla
     * forgets the creature struck only when that one dies, so the time is what keeps an old blow from counting.
     */
    private static boolean hitRecently(LivingEntity entity) {
        return entity.getLastHurtMob() != null
                && entity.tickCount - entity.getLastHurtMobTimestamp() <= GUARD_MEMORY_TICKS;
    }

    // ---- A player's Fracción ----

    /** Whether it is a player Espada's Fracción. */
    public boolean isPledged() {
        return pledged;
    }

    /** The game time it pledged, read against {@link EspadaLedger#fraccionForgottenAt}; 0 when not pledged. */
    public long pledgedAt() {
        return pledgedAt;
    }

    /**
     * Its Espada while pledged, as a tamed creature's owner, so the Espada's own Cero, Bala and aura spare it
     * ({@link SpiritProjectiles#mayStrike} and the aura read this); null otherwise.
     */
    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return pledged ? leaderId : null;
    }

    /** Server side: kneeling to a seated Espada now. Clients read {@link #isCrouching}. */
    public boolean isYielding() {
        return yieldedTo != null;
    }

    /** The seated Espada it kneels to now, by UUID; null when it does not kneel. Server side. */
    @Nullable
    public UUID yieldedTo() {
        return yieldedTo;
    }

    /**
     * Kneels, beaten, to this seated Espada until the game time {@code until}: crouched, its fight dropped, its path
     * stopped and any Cero it gathers let go. While it kneels it fights nothing, never Sonídos aside and never
     * leaves; a blow, the time running out or a Caja Negación ends it. Server side.
     */
    public void yieldTo(Player espada, long until) {
        yieldedTo = espada.getUUID();
        yieldUntil = until;
        setPose(Pose.CROUCHING);
        setTarget(null);
        getNavigation().stop();
        if (charging != null && charging.isCharging()) {
            charging.cancel();
        }
    }

    /** No longer kneels, and stands. */
    public void endYield() {
        yieldedTo = null;
        yieldUntil = 0L;
        // Only the kneel crouches it, so nothing else is undone.
        if (getPose() == Pose.CROUCHING) {
            setPose(Pose.STANDING);
        }
    }

    /**
     * Becomes this player's Fracción for good, pledged at the level's game time: it follows the player from now on
     * (any old leader forgotten), never leaves on its own, and drops any post, its kneel and its fight. Callers check
     * the cap and list it in the {@link EspadaLedger} ({@link ArrancarNpcEvents#pledge}).
     */
    public void pledgeTo(Player espada) {
        pledged = true;
        pledgedAt = level().getGameTime();
        setLeader(espada.getUUID());
        // A mock player in a game test is never in the level, so only the cache can find it.
        cachedLeader = espada;
        setLeaveAt(NEVER);
        idleSince = -1L;
        dismissArmedUntil = -1L;
        lastDismissUse = -1L;
        clearRestriction();
        endYield();
        setTarget(null);
    }

    /**
     * No longer anyone's Fracción: taken off its Espada's list in the {@link EspadaLedger} if it is on it, its leader
     * and post forgotten, and it leaves through a Garganta of its own ({@link #depart}). Server side.
     */
    public void releasePledge() {
        if (pledged && leaderId != null && level() instanceof ServerLevel server) {
            EspadaLedger.get(server.getServer()).removeMember(leaderId, getUUID());
        }
        pledged = false;
        pledgedAt = 0L;
        dismissArmedUntil = -1L;
        lastDismissUse = -1L;
        setLeader(null);
        clearRestriction();
        endYield();
        // Its time is up now rather than never, so if it is saved before the tear takes it, it leaves again on reload.
        setLeaveAt(level().getGameTime());
        depart();
    }

    /**
     * True if a dismiss was already armed and is still within {@link #DISMISS_CONFIRM_TICKS}; otherwise arms it. A use
     * within {@link #DISMISS_PRESS_GAP_TICKS} of the last is the same press held down, so it neither confirms nor
     * re-arms: holding Sneak and the use key never sends a Fracción away on its own.
     */
    public boolean armDismiss(long now) {
        boolean held = lastDismissUse >= 0L && now >= lastDismissUse && now - lastDismissUse < DISMISS_PRESS_GAP_TICKS;
        lastDismissUse = now;
        if (held) {
            return false;
        }
        if (dismissArmedUntil >= 0L && now <= dismissArmedUntil) {
            dismissArmedUntil = -1L;
            lastDismissUse = -1L;
            return true;
        }
        dismissArmedUntil = now + DISMISS_CONFIRM_TICKS;
        return false;
    }

    /**
     * Whether a pledged Fracción may harm this creature for its Espada: never the Espada itself, a creature it owns
     * (a tamed wolf, another of its Fracción), another Arrancar NPC, or a player in creative or spectator mode; and
     * no player at all while the server forbids PvP, nor one its Espada, when online, may not harm (a team-mate).
     * True when it is not pledged.
     */
    public boolean mayHarmForEspada(LivingEntity target) {
        if (!pledged) {
            return true;
        }
        if (target == this || target instanceof ArrancarNpc) {
            return false;
        }
        if (leaderId != null && (leaderId.equals(target.getUUID())
                || (target instanceof OwnableEntity owned && leaderId.equals(owned.getOwnerUUID())))) {
            return false;
        }
        if (!(target instanceof Player player)) {
            return true;
        }
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        MinecraftServer server = level().getServer();
        if (server != null && !server.isPvpAllowed()) {
            return false;
        }
        // The one near is the cached leader (a mock in a game test); one away is looked for among those online.
        Player espada = leader() instanceof Player near ? near
                : server != null && leaderId != null ? server.getPlayerList().getPlayer(leaderId) : null;
        return espada == null || espada.canHarmPlayer(player);
    }

    /**
     * Once a second while pledged, whether or not its Espada is about (see {@link EspadaLedger}): with no Espada
     * recorded it is released; missing from its Espada's list, it is released if the list was forgotten since it
     * pledged (its Espada stopped being an Arrancar) or is full, and else lists itself again. Then it holds its post,
     * within {@code post_radius} of where it stands, while its Espada is away, offline or in another world, and is
     * free again once its Espada is back; and with no target it heals half a heart every {@code heal_seconds}.
     */
    private void keepPledge(ServerLevel server) {
        if (leaderId == null) {
            releasePledge();
            return;
        }
        EspadaLedger ledger = EspadaLedger.get(server.getServer());
        if (!ledger.isMember(leaderId, getUUID())) {
            if (ledger.fraccionForgottenAt(leaderId) >= pledgedAt
                    || ledger.fraccion(leaderId).size() >= BleachConfig.get(BleachConfig.FRACCION_MAX)) {
                releasePledge();
                return;
            }
            // A lost or older ledger file, or this NPC restored from a backup: it is still pledged, so it says so.
            ledger.addMember(leaderId, getUUID(), rank.numero());
        }
        if (leader() == null) {
            if (!hasRestriction()) {
                restrictTo(blockPosition(), BleachConfig.get(BleachConfig.FRACCION_POST_RADIUS));
            }
        } else if (hasRestriction()) {
            clearRestriction();
        }
        int healSeconds = BleachConfig.get(BleachConfig.FRACCION_HEAL_SECONDS);
        if (healSeconds > 0 && getTarget() == null && (tickCount / 20) % healSeconds == 0
                && getHealth() < getMaxHealth()) {
            heal(1.0F);
        }
    }

    // ---- Leaving ----

    /** When it leaves: {@link #NEVER}, {@link #UNTIMED} or a game time. */
    public long leaveAt() {
        return leaveAt;
    }

    public void setLeaveAt(long time) {
        leaveAt = time;
    }

    /** Whether it stays until killed, idle or not. One with no set time ({@link #UNTIMED}) is not. */
    public boolean isPermanent() {
        return leaveAt == NEVER;
    }

    public boolean isDeparting() {
        return departing;
    }

    /**
     * Leaves through a tear of its own, which takes it; a leader takes its Fracción within {@code recall_reach} with
     * it. If the tear cannot open, it simply goes. Server side.
     */
    public void depart() {
        if (departing || !(level() instanceof ServerLevel server)) {
            return;
        }
        departing = true;
        departAt = server.getGameTime();
        getNavigation().stop();
        setTarget(null);
        if (charging != null && charging.isCharging()) {
            charging.cancel();
        }
        if (isLeader()) {
            for (ArrancarNpc follower : fraccionNear(BleachConfig.get(BleachConfig.ARRANCAR_NPC_RECALL_REACH))) {
                // One already in Negación's light is leaving its own way.
                if (!Negacion.held(follower)) {
                    follower.depart();
                }
            }
        }
        if (!server.addFreshEntity(GargantaRift.departure(server, this))) {
            discard();
        }
    }

    /** Whether a beaten leader has had its one roll for a recall. */
    public boolean isRecallRolled() {
        return recallRolled;
    }

    public void markRecallRolled() {
        recallRolled = true;
    }

    /**
     * Once a second, unless it stays until killed: it leaves on time, or after {@code idle_leave_seconds} in a row
     * with no target, no leader about, and no aware soul within {@link HollowLogic#AWARE_NEAR_RANGE}. A Fracción
     * whose leader is here goes when the leader goes.
     */
    private void checkLeaving(ServerLevel server, long now) {
        if (isPermanent()) {
            return;
        }
        if (now >= leaveAt) {
            depart();
            return;
        }
        int idleSeconds = BleachConfig.get(BleachConfig.ARRANCAR_NPC_IDLE_LEAVE_SECONDS);
        LivingEntity target = getTarget();
        // A target that died or left is no reason to stay.
        if (idleSeconds <= 0 || (target != null && target.isAlive()) || leader() != null
                || !SpiritSenses.awareNear(server, position(), HollowLogic.AWARE_NEAR_RANGE).isEmpty()) {
            idleSince = -1L;
            return;
        }
        // Game time is never negative, so -1 is "not idle".
        if (idleSince < 0L) {
            idleSince = now;
        } else if (now - idleSince >= idleSeconds * 20L) {
            depart();
        }
    }

    // ---- Arts ----

    /**
     * A Sonído step along the ground this way, at most {@code distance} and its reach ({@link Sonido#distance}, less
     * under Slowness), landing at least {@code minDistance} away on footing it can stand on, past nothing solid,
     * harmful or unloaded. Refused without AI, while riding, held, leaving, under Gritz or a Bakudō's hold, or before
     * its cooldown is over. Returns whether it stepped.
     */
    public boolean sonido(Vec3 direction, double distance, double minDistance) {
        if (!(level() instanceof ServerLevel server) || isNoAi() || !isAlive() || departing || isPassenger()
                || Negacion.held(this) || hasEffect(ModEffects.GRITZ) || hasEffect(ModEffects.BAKUDO_HELD)
                || server.getGameTime() < sonidoReadyAt) {
            return false;
        }
        Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
        if (flat.lengthSqr() < 1.0E-6) {
            return false;
        }
        MobEffectInstance slowness = getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        double step = Math.min(distance, Sonido.distance(soul(), slowness != null ? slowness.getAmplifier() : -1));
        if (!(step >= minDistance)) {
            return false;
        }
        Vec3 start = position();
        FlashStep.DashPath path = FlashStep.sweep(server, this, getBoundingBox(), flat.normalize(), step, true);
        Vec3 end = path.end();
        if (!path.supported() || !Double.isFinite(end.x) || !Double.isFinite(end.y) || !Double.isFinite(end.z)
                || end.distanceTo(start) < minDistance) {
            return false;
        }
        teleportTo(end.x, end.y, end.z);
        getNavigation().stop();
        resetFallDistance();
        // Dust where it was and where it lands, as a player's Sonído leaves.
        Sonido.effects(server, start);
        Sonido.effects(server, end);
        sonidoReadyAt = server.getGameTime() + BleachConfig.get(ArrancarNpcLogic.numbers(kind()).sonidoCooldownTicks());
        return true;
    }

    /**
     * Fires a Bala at the middle of this creature, now, from where its Cero would gather: its soul's Bala, times the
     * damage multiplier, striking only what its Cero would ({@link CeroBeam#mayStrike}). The cooldown starts from
     * here; this does not wait for it. Refused while dead, held, leaving, or with its arms bound by a Bakudō. Returns
     * whether it fired.
     */
    public boolean fireBala(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || !isAlive() || departing || Negacion.held(this)
                || hasEffect(ModEffects.BAKUDO_BOUND) || target == null || !target.isAlive()) {
            return false;
        }
        Vec3 from = ceroMuzzle(1.0F);
        Vec3 aim = target.getBoundingBox().getCenter().subtract(from);
        if (aim.lengthSqr() < 1.0E-6) {
            return false;
        }
        CeroBeam.Spec spec = ArrancarNpcLogic.balaSpec(soul());
        // A stand-in at the muzzle, never added to the world, is what a shield meets, as for a player's Bala.
        Marker bullet = new Marker(EntityType.MARKER, server);
        bullet.setPos(from);
        CeroBeam.Shot shot = CeroBeam.fire(server, this, bullet, from, aim.normalize(), spec, Bala.KIND);
        CeroBeam.trace(server, this, shot, spec.radius(), Bala.COLOUR, Bala.TRACE_TICKS);
        swing(InteractionHand.MAIN_HAND);
        SpiritSenses.play(server, from.x, from.y, from.z, ModSounds.BALA.get(), getSoundSource(), 1.0F, 1.0F, false);
        balaReadyAt = server.getGameTime() + BleachConfig.get(ArrancarNpcLogic.numbers(kind()).balaCooldownTicks());
        return true;
    }

    /**
     * Each tick it may: a Bala at prey between {@code bala_min_range} and the Bala's reach, or a Sonído step closing
     * on prey between {@code sonido_min_range} and {@code sonido_max_range}, landing a little short. Neither while a
     * Cero gathers or a leader holds back, and only at prey in sight. Once a second, the leaving checks. A kneel
     * holds it still until it runs out, and a pledged Fracción keeps its pledge once a second ({@link #keepPledge}).
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server) || isRemoved() || !isAlive() || departing || Negacion.held(this)) {
            return;
        }
        long now = server.getGameTime();
        // A kneel is not made immobile: an immobile mob never gets here, so the kneel could not run out.
        if (yieldedTo != null) {
            if (now >= yieldUntil) {
                endYield();
            } else {
                getNavigation().stop();
                if (getTarget() != null) {
                    setTarget(null);
                }
            }
        }
        if (pledged && tickCount % 20 == 0) {
            keepPledge(server);
            if (departing) {
                return;
            }
        }
        // Looks the leader up again if it was not found, so the goals see it as soon as it is loaded.
        leader();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && charging == null) {
            useArts(target, now);
        }
        // Never while it kneels: one whose time ran out goes at the first check after the kneel ends.
        if (tickCount % 20 == 0 && yieldedTo == null) {
            checkLeaving(server, now);
        }
    }

    private void useArts(LivingEntity target, long now) {
        if (now >= balaReadyAt) {
            double min = BleachConfig.get(BleachConfig.ARRANCAR_NPC_BALA_MIN_RANGE);
            double max = BleachConfig.get(BleachConfig.BALA_RANGE);
            double reach = ceroMuzzle(1.0F).distanceToSqr(target.getBoundingBox().getCenter());
            if (reach >= min * min && reach <= max * max && mayFight() && hasLineOfSight(target)
                    && CeroBeam.mayStrike(this, target) && fireBala(target)) {
                return;
            }
        }
        if (now >= sonidoReadyAt && now >= sonidoRetryAt) {
            double min = BleachConfig.get(BleachConfig.ARRANCAR_NPC_SONIDO_MIN_RANGE);
            double max = BleachConfig.get(BleachConfig.ARRANCAR_NPC_SONIDO_MAX_RANGE);
            Vec3 toward = target.position().subtract(position());
            double across = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
            if (across >= min && across <= max && mayFight() && hasLineOfSight(target)
                    && !sonido(toward, Math.max(SONIDO_MIN_STEP, across - SONIDO_STOP_SHORT), SONIDO_MIN_STEP)) {
                sonidoRetryAt = now + SONIDO_RETRY_TICKS;
            }
        }
    }

    // ---- CeroBeam.Caster ----

    /**
     * Where the Cero gathers: a little ahead of the eyes, the way the head faces, further the bigger it is, and short
     * of a wall right in front of the face. Both sides.
     */
    @Override
    public Vec3 ceroMuzzle(float partialTick) {
        Vec3 eye = getEyePosition(partialTick);
        Vec3 view = getViewVector(partialTick);
        Vec3 ahead = eye.add(view.scale(MUZZLE_AHEAD * getScale()));
        BlockHitResult wall = SpiritClip.clip(level(), new ClipContext(eye, ahead, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).block();
        if (wall.getType() == HitResult.Type.MISS) {
            return ahead;
        }
        return eye.add(view.scale(Math.max(0.0, wall.getLocation().distanceTo(eye) - MUZZLE_WALL_MARGIN)));
    }

    /**
     * From the muzzle to the middle of its target, or null without a living target in sight. Sight is checked
     * afresh: a NoAI mob's cached sight is never cleared.
     */
    @Override
    @Nullable
    public Vec3 ceroAim() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !hasLineOfSight(target)) {
            return null;
        }
        Vec3 toward = target.getBoundingBox().getCenter().subtract(ceroMuzzle(1.0F));
        return toward.lengthSqr() < 1.0E-6 ? null : toward.normalize();
    }

    /** A charge holds while it is alive, free, not leaving, and its target lives. */
    @Override
    public boolean keepsCharging() {
        LivingEntity target = getTarget();
        return isAlive() && !Negacion.held(this) && !departing && target != null && target.isAlive();
    }

    @Override
    public void onCeroFired(CeroBeam.Shot shot) {
        ceroReadyAt = level().getGameTime() + BleachConfig.get(ArrancarNpcLogic.numbers(kind()).ceroCooldownSeconds()) * 20L
                + random.nextInt(CERO_JITTER_TICKS + 1);
        charging = null;
    }

    @Override
    public void onCeroCancelled() {
        ceroReadyAt = level().getGameTime() + CERO_RETRY_TICKS;
        charging = null;
    }

    // ---- Life ----

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CeroGoal(this));
        goalSelector.addGoal(2, new BladeGoal(this));
        goalSelector.addGoal(3, new HoldBackGoal(this));
        goalSelector.addGoal(4, new FollowLeaderGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, ArrancarNpc.class));
        targetSelector.addGoal(2, new GuardLeaderGoal(this));
        // A pledged Fracción hunts nothing of its own: it fights only for itself and its Espada.
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> !isPledged() && ArrancarNpcLogic.isPrey(target)) {
            // Vanilla fixes the reach for players when the goal is made, before the config's follow_range is applied.
            @Override
            protected void findTarget() {
                targetConditions.range(getFollowDistance());
                super.findTarget();
            }
        });
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TATTOO, (byte) 0);
    }

    /** A spawn egg or a bare summons sets up a Número that stays; an arrival is set up already. */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        if (!isSetUp()) {
            setUp(ArrancarRank.NONE, random, NEVER);
        }
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    /**
     * Server side, every tick, so it all still happens with NoAI: one never set up (a summons with data, a game test)
     * is set up, a saved one whose rank was lost is given one, the config's body is applied after a load, and one
     * whose departing tear went missing leaves. The setting up comes before the tick, so on the first tick its new
     * clothes make no sound.
     */
    @Override
    public void tick() {
        if (level() instanceof ServerLevel && !isRemoved()) {
            if (!isSetUp()) {
                setUp(rank, random, leaveAt);
            } else if (rank.kind() == ArrancarRank.Kind.NONE) {
                repairRank();
            }
            if (!statsApplied) {
                applyRankStats();
            }
        }
        super.tick();
        if (departing && level() instanceof ServerLevel server && !isRemoved()
                && server.getGameTime() - departAt > DEPART_TIMEOUT_TICKS) {
            discard();
        }
    }

    /**
     * Hurt while leaving it cannot be. A blow that lands ends a kneel, or may make a beaten Número kneel to the seated
     * Espada that struck it ({@link ArrancarNpcEvents#maybeYield}); one that does not kneel may Sonído aside, and a
     * beaten leader may have its recall.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (departing) {
            return false;
        }
        if (!super.hurt(source, amount)) {
            return false;
        }
        if (level() instanceof ServerLevel && isAlive()) {
            if (yieldedTo != null) {
                endYield();
            } else {
                ArrancarNpcEvents.maybeYield(this, source);
            }
            if (yieldedTo == null) {
                dodge(source);
            }
            if (isLeader()) {
                ArrancarNpcEvents.maybeRecall(this);
            }
        }
        return true;
    }

    /**
     * A blow from within {@link #DODGE_REACH}, when its Sonído is ready and the {@code sonido_dodge_chance} roll
     * passes, makes it step aside, at right angles to the attacker, to a random side or else the other.
     */
    private void dodge(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == this
                || distanceToSqr(attacker) > DODGE_REACH * DODGE_REACH || level().getGameTime() < sonidoReadyAt
                || random.nextDouble() >= BleachConfig.get(BleachConfig.ARRANCAR_NPC_SONIDO_DODGE_CHANCE)) {
            return;
        }
        Vec3 away = position().subtract(attacker.position());
        Vec3 side = new Vec3(-away.z, 0.0, away.x);
        if (side.lengthSqr() < 1.0E-6) {
            // Standing in the same spot: aside from where it faces.
            side = Vec3.directionFromRotation(0.0F, getYRot() + 90.0F);
        }
        if (random.nextBoolean()) {
            side = side.reverse();
        }
        double blocks = BleachConfig.get(BleachConfig.ARRANCAR_NPC_SONIDO_DODGE_BLOCKS);
        if (!sonido(side, blocks, SONIDO_MIN_STEP)) {
            sonido(side.reverse(), blocks, SONIDO_MIN_STEP);
        }
    }

    /** Killed, not lifted away or gone: its slayer may count it (see {@link ArrancarNpcEvents#onSlain}). */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // A cancelled death leaves it alive, and a second call does nothing: only a real first death counts.
        if (!wasDead && dead && level() instanceof ServerLevel server) {
            ArrancarNpcEvents.onSlain(this, source);
            // Off its Espada's list at once, not only when the body goes a second later (remove).
            if (pledged && leaderId != null) {
                EspadaLedger.get(server.getServer()).removeMember(leaderId, getUUID());
            }
        }
    }

    /**
     * Nothing it wears or holds drops by chance; a player's kill (vanilla's five seconds of "recently hit") may drop
     * one piece of the uniform it wore, taken off it. The blade never drops.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit || random.nextDouble() >= BleachConfig.get(BleachConfig.ARRANCAR_NPC_UNIFORM_DROP_CHANCE)) {
            return;
        }
        List<EquipmentSlot> worn = new ArrayList<>();
        for (EquipmentSlot slot : UNIFORM_SLOTS) {
            if (!getItemBySlot(slot).isEmpty()) {
                worn.add(slot);
            }
        }
        if (worn.isEmpty()) {
            return;
        }
        EquipmentSlot slot = worn.get(random.nextInt(worn.size()));
        spawnAtLocation(getItemBySlot(slot).copy());
        // Off the fallen body too, so the piece is not seen twice.
        setItemSlot(slot, ItemStack.EMPTY);
    }

    /** Public, so a game test can see that nothing it wears or holds drops by chance. */
    @Override
    public float getEquipmentDropChance(EquipmentSlot slot) {
        return super.getEquipmentDropChance(slot);
    }

    /** Putting on or losing a piece makes no vibration, as its steps make none. */
    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return false;
    }

    /**
     * Dying, it never sends the usual puff of smoke, which every player would see: it falls as a mob does and
     * breaks into black smoke only the aware can see.
     */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= DEATH_TICKS && level() instanceof ServerLevel server && !isRemoved()) {
            deathPuff(server, ParticleTypes.POOF, 20);
            deathPuff(server, ParticleTypes.SQUID_INK, 6);
            remove(RemovalReason.KILLED);
        }
    }

    private void deathPuff(ServerLevel server, ParticleOptions particle, int count) {
        SpiritSenses.particles(server, particle, getX(), getY() + getBbHeight() * 0.5, getZ(), count,
                getBbWidth() * 0.5, getBbHeight() * 0.5, getBbWidth() * 0.5, 0.02);
    }

    /** Its rank on the server, so a death message reads "slain by Número 34"; a client knows it as an Arrancar. */
    @Override
    protected Component getTypeName() {
        // The rank is null while the superclasses construct it.
        return rank != null && rank.kind() != ArrancarRank.Kind.NONE && !level().isClientSide() ? rank.label()
                : super.getTypeName();
    }

    /** Arrancar never turn on one another, nor on the leader they follow. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return entity instanceof ArrancarNpc || (leaderId != null && leaderId.equals(entity.getUUID()))
                || super.isAlliedTo(entity);
    }

    /**
     * It goes after its prey ({@link ArrancarNpcLogic#isPrey}), strikes back at whoever just hurt it, and guards its
     * leader: what struck the leader just now, what the leader fights, and what it took on for the leader (kept with
     * no leader left). Never another Arrancar NPC, its leader, or a player in creative or spectator mode. Every goal
     * asks this, so a target that stops being one is dropped. Kneeling, it fights nothing. A pledged Fracción has no
     * prey of its own, also turns on what its Espada struck just now, and never on what its Espada may not harm
     * ({@link #mayHarmForEspada}).
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (!super.canAttack(target) || target == this || target instanceof ArrancarNpc
                || (leaderId != null && leaderId.equals(target.getUUID()))
                || (target instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            return false;
        }
        if (yieldedTo != null || (pledged && !mayHarmForEspada(target))) {
            return false;
        }
        if ((!pledged && ArrancarNpcLogic.isPrey(target)) || target == getLastHurtByMob() || target == guarding) {
            return true;
        }
        LivingEntity leader = leader();
        if (leader == null) {
            return false;
        }
        // What its Espada struck counts only for a pledged one, so an NPC leader's Fracción keep their old rules.
        return (target == leader.getLastHurtByMob() && hurtRecently(leader))
                || (leader instanceof Mob mob && target == mob.getTarget())
                || (pledged && target == leader.getLastHurtMob() && hitRecently(leader));
    }

    /** A spirit's body: walls and crowds cannot crush it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING);
    }

    /** Frozen while a tear takes it and while it is held (Negación's light, a Caja Negación), so none of its goals run. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || departing || Negacion.held(this);
    }

    /** It leaves by its own rules ({@link #depart}), never because players are far. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Kept out of the natural monster cap, and never despawned by distance. Peaceful still removes it, if unpledged. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** Peaceful removes every Arrancar but a pledged Fracción, which stays (fighting nothing, as every mob there). */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return !pledged && super.shouldDespawnInPeaceful();
    }

    /**
     * A pledged Fracción killed or discarded is taken off its Espada's list. Unloading with its chunk and going to
     * another world never come through here, so they keep its place.
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (pledged && reason.shouldDestroy() && leaderId != null && level() instanceof ServerLevel server) {
            EspadaLedger.get(server.getServer()).removeMember(leaderId, getUUID());
        }
        super.remove(reason);
    }

    /**
     * An empty main hand, on one free and not leaving: its own Espada is shown its status, or sends it away with two
     * Sneak-uses ({@link ArrancarNpcEvents#commandFraccion}); anyone else is told it serves another; and the Espada
     * it kneels to takes it as its Fracción ({@link ArrancarNpcEvents#pledge}). Anything else passes, so an item's
     * own use on it (the Caja Negación's) gets its turn.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || Negacion.held(this)
                || departing) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            // The client cannot know who it serves; a kneel it can see. The server is asked either way.
            return isCrouching() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player instanceof FakePlayer) {
            return InteractionResult.PASS;
        }
        long now = level().getGameTime();
        if (pledged) {
            if (player.getUUID().equals(leaderId)) {
                ArrancarNpcEvents.commandFraccion(this, player, now);
            } else {
                player.displayClientMessage(Component.translatable(NOT_YOURS, getDisplayName())
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.getUUID().equals(yieldedTo)) {
            ArrancarNpcEvents.pledge(this, player, now);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Only those who can see it are kept from sleeping by one. */
    @Override
    public boolean isPreventingPlayerRest(Player player) {
        // A Fracción is no threat to rest: it serves an Espada and hunts nothing of its own.
        return !pledged && SoulLogic.seesSpirits(player);
    }

    /** Every sound, footsteps included, goes only to those who can hear spirits. Nothing is played on the client. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent() && !level().isClientSide) {
            SpiritSenses.play(this, sound, getSoundSource(), volume, pitch, false);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
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

    /** It rides nothing; Negación's light mounts it by force. */
    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ArrancarRank.CODEC.encodeStart(NbtOps.INSTANCE, rank)
                .resultOrPartial(BleachMod.LOGGER::error)
                .ifPresent(saved -> tag.put("Rank", saved));
        if (leaderId != null) {
            tag.putUUID("Leader", leaderId);
        }
        tag.putBoolean("Pledged", pledged);
        tag.putLong("PledgedAt", pledgedAt);
        tag.putLong("LeaveAt", leaveAt);
        tag.putBoolean("RecallRolled", recallRolled);
        tag.putBoolean("Dressed", dressed);
    }

    /**
     * Every key is read leniently: a missing or bad rank reads as none (and is repaired on the first tick), an Espada
     * seat as that seat's Privaron number, a missing time as {@link #NEVER}, a missing pledge as none (so an NPC from
     * before the Fracción loads as it was). The config's body is applied again on the first tick. A kneel and an armed
     * dismiss are never saved.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ArrancarRank parsed = tag.contains("Rank")
                ? ArrancarRank.CODEC.parse(NbtOps.INSTANCE, tag.get("Rank")).result().orElse(ArrancarRank.NONE)
                : ArrancarRank.NONE;
        rank = ArrancarNpcLogic.unseated(parsed);
        leaderId = tag.hasUUID("Leader") ? tag.getUUID("Leader") : null;
        pledged = tag.getBoolean("Pledged");
        pledgedAt = pledged && tag.contains("PledgedAt", Tag.TAG_ANY_NUMERIC) ? tag.getLong("PledgedAt") : 0L;
        // Any number will do, so a hand-written /summon of LeaveAt:-1 (an int) still means NEVER.
        leaveAt = tag.contains("LeaveAt", Tag.TAG_ANY_NUMERIC) ? tag.getLong("LeaveAt") : NEVER;
        recallRolled = tag.getBoolean("RecallRolled");
        dressed = tag.getBoolean("Dressed");
        endYield();
        dismissArmedUntil = -1L;
        lastDismissUse = -1L;
        cachedLeader = null;
        leaderLookupAt = 0L;
        statsApplied = false;
        refreshTattoo();
    }

    // ---- Goals ----

    /**
     * The charge: stand, face the prey, gather, and let go. It starts only when it may fight, on the ground, at a
     * target the beam would strike, in sight, within reach but not too close, with nothing solid where the Cero
     * gathers. Once started nothing interrupts it but the charge breaking: the head follows the prey until the aim
     * locks, then holds along the locked aim, so the prey has a moment to step aside.
     */
    static final class CeroGoal extends Goal {
        private final ArrancarNpc npc;
        @Nullable
        private CeroBeam beam;
        @Nullable
        private CeroBeam.Spec spec;
        /** Where the head looks once the aim has locked, so it no longer turns after the prey. */
        @Nullable
        private Vec3 lockedLook;

        CeroGoal(ArrancarNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = npc.getTarget();
            // The cheap checks first: this is asked every other tick while the Cero is cooling down.
            if (target == null || !target.isAlive() || npc.level().getGameTime() < npc.ceroReadyAt || !npc.onGround()
                    || !npc.keepsCharging()) {
                return false;
            }
            if (!npc.canAttack(target) || !CeroBeam.mayStrike(npc, target) || !npc.mayFight()) {
                return false;
            }
            double minRange = BleachConfig.get(BleachConfig.ARRANCAR_NPC_CERO_MIN_RANGE);
            double dx = target.getX() - npc.getX();
            double dz = target.getZ() - npc.getZ();
            if (dx * dx + dz * dz < minRange * minRange) {
                return false;
            }
            CeroBeam.Spec next = ArrancarNpcLogic.ceroSpec(npc.soul(), npc.kind());
            Vec3 muzzle = npc.ceroMuzzle(1.0F);
            if (muzzle.distanceToSqr(target.getBoundingBox().getCenter()) > next.range() * next.range()
                    || !npc.hasLineOfSight(target)) {
                return false;
            }
            BlockPos mouth = BlockPos.containing(muzzle);
            if (!npc.level().getBlockState(mouth).getCollisionShape(npc.level(), mouth).isEmpty()) {
                return false;
            }
            spec = next;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return beam != null && !beam.isRemoved() && beam.isCharging();
        }

        @Override
        public void start() {
            lockedLook = null;
            CeroBeam.Spec use = spec != null ? spec : ArrancarNpcLogic.ceroSpec(npc.soul(), npc.kind());
            spec = use;
            beam = npc.level() instanceof ServerLevel level ? CeroBeam.charge(level, npc, use) : null;
            if (beam == null) {
                npc.onCeroCancelled();
                return;
            }
            npc.charging = beam;
            npc.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (beam == null || spec == null) {
                return;
            }
            LivingEntity target = npc.getTarget();
            if (lockedLook == null && beam.tickCount >= spec.chargeTicks() - spec.aimLockTicks()) {
                Vec3 aim = npc.ceroAim();
                if (aim != null) {
                    Vec3 muzzle = npc.ceroMuzzle(1.0F);
                    double distance = target != null
                            ? Math.max(1.0, muzzle.distanceTo(target.getBoundingBox().getCenter())) : LOCKED_LOOK_DISTANCE;
                    lockedLook = muzzle.add(aim.scale(distance));
                }
            }
            if (lockedLook != null) {
                npc.getLookControl().setLookAt(lockedLook.x, lockedLook.y, lockedLook.z, LOOK_TURN, LOOK_TURN);
            } else if (target != null) {
                npc.getLookControl().setLookAt(target, LOOK_TURN, LOOK_TURN);
            }
        }

        @Override
        public void stop() {
            if (beam != null && beam.isCharging()) {
                beam.cancel();
            }
            npc.charging = null;
            beam = null;
            spec = null;
            lockedLook = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }
    }

    /** The blade, at the vanilla pace of a blow a second, after prey out of sight too; only while it may fight. */
    static final class BladeGoal extends MeleeAttackGoal {
        private final ArrancarNpc npc;

        BladeGoal(ArrancarNpc npc) {
            super(npc, BLADE_SPEED, true);
            this.npc = npc;
        }

        // mayFight first: the melee goal's own check may only run once a second, and is spent once asked.
        @Override
        public boolean canUse() {
            return npc.mayFight() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return npc.mayFight() && super.canContinueToUse();
        }
    }

    /**
     * A leader holding back stands where it is and watches its prey while its Fracción fight, so it neither wanders
     * nor looks away. Once it may fight, this stops and the blade or the Cero takes over.
     */
    static final class HoldBackGoal extends Goal {
        private final ArrancarNpc npc;

        HoldBackGoal(ArrancarNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return holdsBack();
        }

        @Override
        public boolean canContinueToUse() {
            return holdsBack();
        }

        private boolean holdsBack() {
            LivingEntity target = npc.getTarget();
            return npc.isLeader() && target != null && target.isAlive() && !npc.mayFight();
        }

        @Override
        public void tick() {
            npc.getNavigation().stop();
            LivingEntity target = npc.getTarget();
            if (target != null) {
                npc.getLookControl().setLookAt(target, LOOK_TURN, LOOK_TURN);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /**
     * A Fracción with no target walks back to within {@code follow_distance} of its leader (stopping 2 blocks inside
     * it), and catches up by Sonído from beyond {@code catch_up_distance}.
     */
    static final class FollowLeaderGoal extends Goal {
        private final ArrancarNpc npc;
        private int repathIn;

        FollowLeaderGoal(ArrancarNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (npc.getTarget() != null) {
                return false;
            }
            LivingEntity leader = npc.leader();
            double distance = BleachConfig.get(BleachConfig.ARRANCAR_NPC_FOLLOW_DISTANCE);
            return leader != null && npc.distanceToSqr(leader) > distance * distance;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity leader = npc.leader();
            if (leader == null || npc.getTarget() != null) {
                return false;
            }
            double stop = Math.max(1.0, BleachConfig.get(BleachConfig.ARRANCAR_NPC_FOLLOW_DISTANCE) - 2.0);
            return npc.distanceToSqr(leader) > stop * stop;
        }

        @Override
        public void start() {
            repathIn = 0;
        }

        @Override
        public void tick() {
            LivingEntity leader = npc.leader();
            if (leader == null) {
                return;
            }
            double distance = npc.distanceTo(leader);
            long now = npc.level().getGameTime();
            if (distance > BleachConfig.get(BleachConfig.ARRANCAR_NPC_CATCH_UP_DISTANCE) && now >= npc.sonidoRetryAt) {
                double landShort = BleachConfig.get(BleachConfig.ARRANCAR_NPC_FOLLOW_DISTANCE);
                if (npc.sonido(leader.position().subtract(npc.position()), distance - landShort, SONIDO_MIN_STEP)) {
                    // The old path starts where it no longer stands.
                    repathIn = 0;
                } else {
                    npc.sonidoRetryAt = now + SONIDO_RETRY_TICKS;
                }
            }
            if (--repathIn <= 0) {
                repathIn = adjustedTickDelay(10);
                npc.getNavigation().moveTo(leader, FOLLOW_SPEED);
            }
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
        }
    }

    /**
     * A Fracción turns on what threatens its leader: what struck the leader in the last five seconds, else (for a
     * pledged one) what its Espada struck in the last five seconds, else what the leader fights. Looked at every 10
     * ticks. What it takes on is kept in {@code guarding} while this runs, so it
     * fights on if the leader falls, until the target dies, leaves its notice or stops being a lawful target.
     */
    static final class GuardLeaderGoal extends TargetGoal {
        private static final TargetingConditions GUARD_TARGETING =
                TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
        private final ArrancarNpc npc;
        private int recheckIn;
        @Nullable
        private LivingEntity candidate;

        GuardLeaderGoal(ArrancarNpc npc) {
            super(npc, false);
            this.npc = npc;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (--recheckIn > 0) {
                return false;
            }
            recheckIn = reducedTickDelay(10);
            LivingEntity leader = npc.leader();
            if (leader == null) {
                return false;
            }
            // What struck the leader comes first; failing that (none, or one this Fracción may not take on), what it
            // fights. A player has no target to read, so a pledged Fracción takes what its Espada struck just now.
            candidate = guardable(hurtRecently(leader) ? leader.getLastHurtByMob() : null);
            if (candidate == null && npc.isPledged()) {
                candidate = guardable(hitRecently(leader) ? leader.getLastHurtMob() : null);
            }
            if (candidate == null) {
                candidate = guardable(leader instanceof Mob mob ? mob.getTarget() : null);
            }
            return candidate != null;
        }

        /** This creature, if it is one this Fracción may take on for its leader; else null. */
        @Nullable
        private LivingEntity guardable(@Nullable LivingEntity threat) {
            return threat != null && threat != npc && threat.isAlive() && canAttack(threat, GUARD_TARGETING)
                    ? threat : null;
        }

        @Override
        public void start() {
            npc.setTarget(candidate);
            npc.guarding = candidate;
            super.start();
        }

        @Override
        public void stop() {
            super.stop();
            npc.guarding = null;
            candidate = null;
        }
    }
}
