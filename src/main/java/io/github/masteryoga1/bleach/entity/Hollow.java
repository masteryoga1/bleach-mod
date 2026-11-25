package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.BaitEvents;
import io.github.masteryoga1.bleach.soul.FieldTrialEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.Negacion;
import io.github.masteryoga1.bleach.soul.PlusEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Hollow: a soul that lost its heart and became a masked, hungry beast. Only the spiritually aware
 * can see, hear or touch one, and it hunts them and Pluses for their souls (see
 * {@link HollowLogic#isPrey}). Its body is a spirit's, so the mundane world never feels it: it pushes
 * nothing it cannot touch, sets off no pressure plates or sculk, raises no dust when it lands, and dies
 * without the usual puff of smoke.
 *
 * <p>The mask is its weak point (see {@link HollowMask}). A zanpakutō's killing blow purifies it instead
 * of destroying it: it turns white and dissolves into light, and the soul it once was rises and passes on.
 *
 * <p>A Hollow player may seize a weakened one in its jaws and devour it ({@link #seize}): held, it
 * cannot move, bite or heal.
 *
 * <p>One drawn by a Hollow Bait belongs to its site ({@link #baitSite()}). While the site keeps it
 * ({@link #isBaitHeld()}) it never leaves, despawns or flees, and wanders back towards the bait; while
 * the sky cracks it stops fighting and gathers below the tear.
 *
 * <p>Each kind ({@link HollowKind}) is its own subclass, because goals and navigation are made inside
 * the constructor of {@code Mob}, before any field here is set: {@link #kind()}, {@link #leaps()} and
 * {@link #hurtByTargetGoal()} may only return constants.
 */
public abstract class Hollow extends Monster {
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Hollow.class, EntityDataSerializers.BYTE);
    public static final int FLAG_CLIMBING = 1;
    public static final int FLAG_CRACKED = 2;
    public static final int FLAG_MASK_FORMING = 4;
    public static final int FLAG_PURIFYING = 8;
    /** Held for the Gate of Hell, which takes this body (see {@link #isHellbound}). A Shrieker's own are 16 and 32. */
    public static final int FLAG_HELLBOUND = 64;

    /** How long the white dissolve of a purified Hollow lasts, and the tick its soul rises from it. */
    public static final int DISSOLVE_TICKS = 24;
    public static final int ECHO_TICK = 8;
    /** How long a new mask takes to form over the face of a Plus that has just turned. */
    public static final int MASK_FORMING_TICKS = 40;
    /** How long a struck mask stays cracked. */
    public static final int CRACK_TICKS = 200;
    /** A blow to the mask makes the Hollow reel for this long, but no more often than the cooldown. */
    public static final int REEL_TICKS = 15;
    public static final int REEL_COOLDOWN_TICKS = 40;
    /** Ticks without being hurt before the Hollow starts to heal. */
    public static final int REGEN_DELAY_TICKS = 100;
    /** A Hollow hurt by a creature within this long does not despawn when far from players. */
    public static final int MOB_HURT_PROTECTION_TICKS = 2000;

    public static final ResourceLocation SIZE_ID = BleachMod.id("hollow_size");
    public static final ResourceLocation DIFFICULTY_ID = BleachMod.id("hollow_difficulty");
    public static final ResourceLocation CONFIG_ID = BleachMod.id("hollow_config");

    private static final long NEVER = ShinigamiProgress.NEVER;
    private static final int DEATH_TICKS = 20;
    private static final double DEATH_PARTICLE_RANGE = 64.0;
    /** A Plus devoured heals this share of the Hollow's health and adds this share of its kind's reiatsu. */
    private static final float DEVOUR_HEAL_FRACTION = 0.25F;
    private static final float DEVOUR_REIATSU_FRACTION = 0.1F;
    /** Devouring can at most double a Hollow's reiatsu for its size. */
    private static final float DEVOUR_REIATSU_CAP = 2.0F;
    /** A departing Hollow whose tear has gone missing leaves on its own after this long. */
    private static final int DEPART_TIMEOUT_TICKS = GargantaRift.DEPART_AGE + 40;
    private static final int SLOWNESS_III = 2;

    // Server-side state, not saved: a reloaded Hollow starts these over.
    long lastHurtAt = NEVER;
    long regenPausedUntil = NEVER;
    long crackedUntil = NEVER;
    long lastReelAt = NEVER;
    long lastMaskHitAt = NEVER;
    int lastAttackTick = -1000;
    int idleTicks;
    int maskFormingTicks;
    boolean departing;
    private long lastHurtByMobAt = NEVER;
    private int departTicks;
    /** Ticks a hellbound body has been held, so one whose Gate is gone goes by itself. */
    private int hellboundTicks;
    /** Held in a Hollow player's jaws until this game time. 0 is never: game time is never below it. */
    private long seizedUntil;

    /** The soul whose reiatsu drew this Hollow through a Garganta, if any. */
    @Nullable
    private UUID drawer;
    /** The Hollow Bait site whose pull brought this Hollow through, if any. Saved. */
    @Nullable
    private UUID baitSite;
    // Set each second by the site while it runs (see holdForBait); not saved, so a reload lets go.
    private long baitHeldUntil = NEVER;
    @Nullable
    private Vec3 baitCentre;
    private boolean baitFinale;
    /** Whether a blow to the mask has already had its one chance at a fragment. Saved, so a reload cannot re-roll it. */
    private boolean fragmentRolled;
    /** What the villager this Hollow once was looked like, for the soul that rises when it is purified. */
    @Nullable
    private VillagerData formerVillager;
    /** Whether this Hollow was a Plus whose chain ran out. */
    private boolean fromPlus;
    /** Whether this Hollow was a sinner in life. Saved, and read leniently: a missing or odd value is none. */
    private boolean sinner;
    /** The student whose field trial this practice Hollow belongs to; null for a real Hollow. Never saved. */
    @Nullable
    private UUID trialOf;
    /** When that trial began, so one from an earlier trial is never counted. */
    private long trialStart;
    /** Whether its {@link #configBases}, if it has any, are in yet. Not saved: they go in again as it loads. */
    private boolean basesSet;

    /** Ticks into the white dissolve, on both sides, so the renderer can follow it. */
    int dissolveTicks;

    protected Hollow(EntityType<? extends Hollow> type, Level level) {
        super(type, level);
        // No orbs: the unaware would see them. A player who kills one is given its experience directly.
        xpReward = 0;
    }

    /** Which kind this is. A constant, as it is read while the goals are made. */
    public abstract HollowKind kind();

    /** Whether this Hollow leaps at its prey, as spiders do. */
    protected boolean leaps() {
        return false;
    }

    /** What makes this Hollow strike back at whoever hurt it; other Hollows' blows are ignored. */
    protected HurtByTargetGoal hurtByTargetGoal() {
        return new HurtByTargetGoal(this, Hollow.class);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (leaps()) {
            goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.4F));
        }
        goalSelector.addGoal(2, new HollowMeleeGoal(this));
        goalSelector.addGoal(4, new HollowGatherGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, hurtByTargetGoal());
        targetSelector.addGoal(2, new HollowHuntGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        applySpawnStats(level.getDifficulty(), kind().rollScale(level.getRandom()));
        return data;
    }

    /**
     * Sets this Hollow's size and the strength that goes with it and the world: health and reiatsu grow
     * with size, Easy takes away health and Hard doubles armour, and the server's multipliers apply on
     * top. Leaves it at full health with a soul to match. Calling it again replaces what it set before.
     */
    public void applySpawnStats(Difficulty difficulty, float scale) {
        setConfigBases();
        AttributeInstance size = getAttribute(Attributes.SCALE);
        if (size != null) {
            size.setBaseValue(scale);
        }
        setMultiplier(Attributes.MAX_HEALTH, SIZE_ID, scale - 1.0);
        setMultiplier(Attributes.MAX_HEALTH, DIFFICULTY_ID, HollowLogic.difficultyHealthFactor(difficulty) - 1.0);
        setMultiplier(Attributes.ARMOR, DIFFICULTY_ID, HollowLogic.difficultyArmorFactor(difficulty) - 1.0);
        setMultiplier(Attributes.MAX_HEALTH, CONFIG_ID, BleachConfig.get(BleachConfig.HOLLOW_HEALTH_MULTIPLIER) - 1.0);
        setMultiplier(Attributes.ATTACK_DAMAGE, CONFIG_ID, BleachConfig.get(BleachConfig.HOLLOW_DAMAGE_MULTIPLIER) - 1.0);
        setHealth(getMaxHealth());
        initSoul(scale);
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

    /** A boss's base health and attack damage, as the server's config sets them in place of its row's. */
    protected record ConfigBases(double health, double damage) {
    }

    /**
     * The config's base health and damage for a kind whose strength is the server's to set, a boss's; none, the default,
     * for a kind whose row's stand. They go in as it spawns, the base its size, the difficulty and the multipliers work
     * on, and on its first server tick for one that skipped spawning (a summon with data, a game test), which is left
     * whole if it was whole, though without what spawning adds.
     */
    @Nullable
    protected ConfigBases configBases() {
        return null;
    }

    private void setConfigBases() {
        basesSet = true;
        ConfigBases bases = configBases();
        if (bases != null) {
            setBase(Attributes.MAX_HEALTH, bases.health());
            setBase(Attributes.ATTACK_DAMAGE, bases.damage());
        }
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    /** A Hollow's soul: tier one of the Hollow path, with its kind's reiatsu for its size. */
    private void initSoul(float scale) {
        SoulData soul = getData(SoulAttachments.SOUL);
        SoulLogic.initialize(soul);
        SoulLogic.changeState(soul, SoulState.HOLLOW);
        float reiatsu = HollowLogic.reiatsuFor(kind(), scale);
        soul.setMaxReiryoku(reiatsu);
        soul.setReiryoku(reiatsu);
    }

    /** The size this Hollow was given, 1 when it was never rolled. */
    private float spawnScale() {
        return (float) getAttributeBaseValue(Attributes.SCALE);
    }

    /** This Hollow's soul, made on first use for Hollows that skipped spawning (game tests, summons with data). */
    public SoulData soul() {
        if (!SoulLogic.hasSoul(this)) {
            initSoul(spawnScale());
        }
        return SoulLogic.get(this);
    }

    /**
     * Server side, every tick, so it all still happens with NoAI: the soul, a boss's config bases for one that
     * skipped spawning, a practice Hollow its trial no longer wants fading, the crack wearing off, the mask
     * forming, a missing departure tear, and healing when left alone.
     */
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        if (!SoulLogic.hasSoul(this)) {
            initSoul(spawnScale());
        }
        if (!basesSet && isAlive()) {
            boolean whole = getHealth() >= getMaxHealth();
            setConfigBases();
            if (whole) {
                setHealth(getMaxHealth());
            }
        }
        if (isPractice() && tickCount % 10 == 0 && !FieldTrialEvents.keeps(this)) {
            dissolve(server);
            return;
        }
        long now = server.getGameTime();
        setFlag(FLAG_CRACKED, now < crackedUntil);
        if (departing && ++departTicks > DEPART_TIMEOUT_TICKS) {
            discard();
            return;
        }
        if (maskFormingTicks > 0) {
            maskFormingTicks--;
            if (maskFormingTicks % 10 == 0) {
                Vec3 face = HollowMask.maskCenter(this);
                SpiritSenses.particles(server, ParticleTypes.SQUID_INK, face.x, face.y, face.z, 3, 0.15, 0.15, 0.15, 0.01);
            }
            if (maskFormingTicks == 0) {
                setFlag(FLAG_MASK_FORMING, false);
            }
        }
        if (tickCount % 20 == 0 && isAlive() && getHealth() < getMaxHealth()
                && HollowLogic.mayRegenerate(now, lastHurtAt, regenPausedUntil)) {
            float perSecond = (float) BleachConfig.get(BleachConfig.HOLLOW_REGEN_PER_SECOND);
            if (perSecond > 0.0F) {
                heal(perSecond);
            }
        }
    }

    /**
     * Gives up its prey while a bait's finale draws it under the tear: one set straight on it (by the
     * rift it came through, or a pack's call) is not let go by the target goals, which no longer run.
     * A practice Hollow with no prey goes after its student again. Otherwise counts the time without
     * prey, and leaves through a tear of its own when there has been too much.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isBaitFinale() && getTarget() != null) {
            setTarget(null);
        }
        LivingEntity student = isPractice() && getTarget() == null ? FieldTrialEvents.studentOf(this) : null;
        if (student != null && canAttack(student)) {
            setTarget(student);
        }
        if (getTarget() != null) {
            idleTicks = 0;
            return;
        }
        idleTicks++;
        long leaveAfter = (long) (BleachConfig.get(BleachConfig.HOLLOW_LEAVE_AFTER_MINUTES) * 60 * 20);
        if (leaveAfter > 0 && idleTicks >= leaveAfter && idleTicks % 20 == 0 && mayLeave()
                && level() instanceof ServerLevel server
                && SpiritSenses.awareNear(server, position(), HollowLogic.AWARE_NEAR_RANGE).isEmpty()) {
            leave();
        }
    }

    /**
     * Hollows someone wants kept stay: named ones, persistent ones, ones without AI, ones a Hollow Bait
     * still holds, and practice ones, which go with their trial. So does any in Hueco Mundo, which is home:
     * it goes only as any monster does.
     */
    private boolean mayLeave() {
        return !isPractice() && !departing && !isPurifying() && !isHellbound() && !isPersistenceRequired() && !hasCustomName()
                && !isNoAi() && !isBaitHeld() && level().dimension() != BleachWorlds.HUECO_MUNDO;
    }

    /** A Hollow comes in any light: a natural spawn answers to its placement's ground rule alone (HuecoMundoSpawns). */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || super.checkSpawnRules(level, reason);
    }

    /** Leaves for lack of prey. */
    protected void leave() {
        depart();
    }

    /** Hurt while leaving or dissolving, a Hollow cannot be. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (departing || isPurifying()) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            long now = level().getGameTime();
            lastHurtAt = now;
            if (source.getEntity() instanceof LivingEntity) {
                lastHurtByMobAt = now;
            }
        }
        return hurt;
    }

    /**
     * A bite: on a landed one, the kind's effect, for longer on Hard and not at all on Easy. On a
     * spirit it gives off no swirls, which the unaware would see where nothing stands.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        lastAttackTick = tickCount;
        if (!super.doHurtTarget(target)) {
            return false;
        }
        HollowKind.OnHit onHit = kind().onHit;
        if (onHit != null && target instanceof LivingEntity living) {
            int seconds = HollowLogic.onHitSeconds(kind(), level().getDifficulty());
            if (seconds > 0) {
                living.addEffect(new MobEffectInstance(onHit.effect(), seconds * 20, onHit.amplifier(), false,
                        !SoulLogic.isSpirit(living), true), this);
            }
        }
        return true;
    }

    /** Whether the kind's time between bites has passed since the last one. A seized Hollow cannot bite. */
    boolean attackReady() {
        return tickCount - lastAttackTick >= kind().attackInterval && !isSeized();
    }

    /**
     * A blow landed on the mask: it cracks and stops the Hollow healing for a while, and the Hollow
     * reels (no more than once every two seconds). Bone chips fly and the mask cracks audibly, for the
     * aware.
     *
     * @return whether the blow was fresh: the mask was whole until now, not still cracked from before
     */
    public boolean onMaskStruck(long now) {
        boolean fresh = now >= crackedUntil;
        crackedUntil = now + CRACK_TICKS;
        setFlag(FLAG_CRACKED, true);
        regenPausedUntil = Math.max(regenPausedUntil,
                now + (long) (BleachConfig.get(BleachConfig.MASK_REGEN_PAUSE_SECONDS) * 20));
        if (now - lastReelAt >= REEL_COOLDOWN_TICKS) {
            lastReelAt = now;
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, REEL_TICKS, SLOWNESS_III, false, false));
        }
        lastMaskHitAt = now;
        if (level() instanceof ServerLevel server) {
            Vec3 mask = HollowMask.maskCenter(this);
            SpiritSenses.particles(server, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)),
                    mask.x, mask.y, mask.z, 8, 0.15, 0.15, 0.15, 0.08);
        }
        playSound(ModSounds.MASK_CRACK.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        return fresh;
    }

    /**
     * Marks that this Hollow's mask has had its one chance at a fragment.
     *
     * @return whether it had had it before
     */
    public boolean markFragmentRolled() {
        boolean before = fragmentRolled;
        fragmentRolled = true;
        return before;
    }

    /**
     * Makes this a practice Hollow of this student's field trial begun at {@code start}: it hunts and answers only that
     * student, and gives nothing a slain Hollow gives (no count, growth, experience, kill score or fragment), though
     * each blow on it trains as on any Hollow.
     */
    public void makePractice(UUID student, long start) {
        trialOf = student;
        trialStart = start;
        fragmentRolled = true;
        deathScore = -1;
    }

    /** The student whose field trial this practice Hollow belongs to; null for a real Hollow. */
    @Nullable
    public UUID trialOf() {
        return trialOf;
    }

    /** The game time this practice Hollow's trial began. */
    public long trialStart() {
        return trialStart;
    }

    /** Whether this is a practice Hollow of a student's field trial, never saved and gone with it. */
    public boolean isPractice() {
        return trialOf != null;
    }

    /** Its trial is over: it fades into white ash only the aware see, and is gone, with no death and nothing counted. */
    public void dissolve(ServerLevel server) {
        SpiritSenses.particles(server, ParticleTypes.WHITE_ASH, getX(), getY() + getBbHeight() * 0.5, getZ(), 12,
                getBbWidth() * 0.4, getBbHeight() * 0.4, getBbWidth() * 0.4, 0.02);
        discard();
    }

    /** Whether the mask was struck on this very tick, so the blow that did it can count for more. */
    public boolean wasMaskStruckAt(long now) {
        return lastMaskHitAt == now;
    }

    /** Whether the mask shows cracks. The client reads the synced flag; the server knows the time. */
    public boolean isMaskCracked() {
        return level().isClientSide ? getFlag(FLAG_CRACKED) : level().getGameTime() < crackedUntil;
    }

    /** Whether the mask is still forming over the face, after a Plus has turned. No mask yet means no weak point. */
    public boolean isMaskForming() {
        return getFlag(FLAG_MASK_FORMING);
    }

    /** Freezes the Hollow while a mask forms over its face. */
    public void startMaskForming() {
        maskFormingTicks = MASK_FORMING_TICKS;
        setFlag(FLAG_MASK_FORMING, true);
    }

    public boolean isPurifying() {
        return getFlag(FLAG_PURIFYING);
    }

    /** The killing blow was a zanpakutō's: the Hollow dissolves into light instead of falling (see {@link #tickDeath}). */
    public void startPurification() {
        setFlag(FLAG_PURIFYING, true);
        dissolveTicks = 0;
        deathTime = 0;
    }

    /** How far the white dissolve has gone, from 0 to 1; 0 for a Hollow that is not being purified. */
    public float dissolveProgress(float partialTick) {
        return isPurifying() ? Math.min(1.0F, (dissolveTicks + partialTick) / DISSOLVE_TICKS) : 0.0F;
    }

    /** Whether this body is held for the Gate of Hell, which takes it (see {@link #tickDeath}). Synced. */
    public boolean isHellbound() {
        return getFlag(FLAG_HELLBOUND);
    }

    /**
     * The killing blow opened the Gate of Hell for this sinner ({@link HellGate#open}): it is held upright where the blow
     * left it, neither falling nor dissolving, until its Gate takes it.
     */
    public void bindForHell() {
        setFlag(FLAG_HELLBOUND, true);
        hellboundTicks = 0;
        deathTime = 0;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
    }

    /** Its Gate let it go, as it lives again (its death undone after the Gate rose): it stands and falls as before. */
    public void unbindFromHell() {
        setFlag(FLAG_HELLBOUND, false);
        setNoGravity(false);
    }

    /** Whether this Hollow is leaving through a tear. Known on the server only. */
    public boolean isDeparting() {
        return departing;
    }

    /**
     * Leaves through a tear of its own: frozen where it stands until the tear takes it (see
     * {@link GargantaRift#departure}). Without room for the tear, it simply goes.
     */
    public void depart() {
        if (departing || !(level() instanceof ServerLevel server)) {
            return;
        }
        departing = true;
        departTicks = 0;
        getNavigation().stop();
        setTarget(null);
        if (!server.addFreshEntity(GargantaRift.departure(server, this))) {
            discard();
        }
    }

    /**
     * Devours a Plus: heals a quarter of its health, and its reiatsu grows by a tenth of its kind's,
     * up to twice what its kind has at its size.
     */
    public void devour(LivingEntity prey) {
        heal(getMaxHealth() * DEVOUR_HEAL_FRACTION);
        SoulData soul = soul();
        float cap = DEVOUR_REIATSU_CAP * kind().reiatsu * getScale();
        float gain = Math.min(kind().reiatsu * DEVOUR_REIATSU_FRACTION, cap - soul.maxReiryoku());
        if (gain > 0.0F) {
            soul.setMaxReiryoku(soul.maxReiryoku() + gain);
            soul.setReiryoku(soul.reiryoku() + gain);
        }
    }

    /**
     * A Hollow player has this one in its jaws: until {@code until} it cannot move, bite or heal. The
     * devourer renews it every tick of the hold, so it lapses by itself soon after the hold ends.
     */
    public void seize(long until) {
        seizedUntil = Math.max(seizedUntil, until);
        regenPausedUntil = Math.max(regenPausedUntil, until);
        getNavigation().stop();
    }

    /** The jaws let go. */
    public void release() {
        seizedUntil = 0L;
    }

    /** Whether a Hollow player holds this one in its jaws. Known on the server only. */
    public boolean isSeized() {
        return level().getGameTime() < seizedUntil;
    }

    @Nullable
    public UUID drawer() {
        return drawer;
    }

    public void setDrawer(@Nullable UUID uuid) {
        drawer = uuid;
    }

    /** The Hollow Bait site that drew this Hollow, if any. */
    @Nullable
    public UUID baitSite() {
        return baitSite;
    }

    public void setBaitSite(@Nullable UUID site) {
        baitSite = site;
    }

    /**
     * The site still pulls: for the next {@link BaitEvents#HOLD_TICKS} ticks this Hollow stays near
     * {@code centre}, and in the finale it gathers below the tear and fights no one. The site renews
     * it every second while it runs, so it lapses soon after the site ends.
     */
    public void holdForBait(Vec3 centre, long now, boolean finale) {
        baitHeldUntil = now + BaitEvents.HOLD_TICKS;
        baitCentre = centre;
        baitFinale = finale;
    }

    /** Whether a Hollow Bait site holds this Hollow now. Known on the server only. */
    public boolean isBaitHeld() {
        return baitSite != null && level().getGameTime() < baitHeldUntil;
    }

    /** Whether this Hollow is held for a finale, the sky cracked above the bait. */
    public boolean isBaitFinale() {
        return isBaitHeld() && baitFinale;
    }

    /** Where the bait that holds this Hollow lies, once a site has held it. */
    @Nullable
    public Vec3 baitCentre() {
        return baitCentre;
    }

    @Nullable
    public VillagerData formerVillager() {
        return formerVillager;
    }

    public void setFormerVillager(@Nullable VillagerData data) {
        formerVillager = data;
    }

    public boolean fromPlus() {
        return fromPlus;
    }

    public void setFromPlus(boolean value) {
        fromPlus = value;
    }

    /** Whether this Hollow was a sinner in life, whom the Gate of Hell takes. None is yet but the Shrieker, always one. */
    public boolean isSinner() {
        return sinner;
    }

    public void setSinner(boolean value) {
        sinner = value;
    }

    protected boolean getFlag(int flag) {
        return (entityData.get(DATA_FLAGS) & flag) != 0;
    }

    protected void setFlag(int flag, boolean on) {
        byte flags = entityData.get(DATA_FLAGS);
        byte next = (byte) (on ? flags | flag : flags & ~flag);
        if (next != flags) {
            entityData.set(DATA_FLAGS, next);
        }
    }

    /**
     * Dying, a Hollow never sends the usual puff of smoke, which every player would see. A hellbound one is held upright
     * and still for its Gate, which takes it, and goes by itself at {@link HellGate#HOLD_LIMIT} should the Gate be gone
     * first; a purified one stays upright and dissolves, and its soul rises at {@link #ECHO_TICK}; any other falls over
     * as a mob does and breaks into black smoke only the aware can see.
     */
    @Override
    protected void tickDeath() {
        ServerLevel server = level() instanceof ServerLevel serverLevel && !isRemoved() ? serverLevel : null;
        if (isHellbound()) {
            // Neither flashing red nor tipping over: its Gate holds it as the blow left it.
            deathTime = 0;
            hurtTime = 0;
            if (server == null) {
                return;
            }
            setDeltaMovement(Vec3.ZERO);
            if (++hellboundTicks >= HellGate.HOLD_LIMIT) {
                deathPuff(server, ParticleTypes.SQUID_INK, 12);
                remove(RemovalReason.KILLED);
            }
            return;
        }
        if (isPurifying()) {
            // No red flash from the killing blow, so the white of the dissolve shows from the start.
            deathTime = 0;
            hurtTime = 0;
            dissolveTicks++;
            if (server == null) {
                return;
            }
            double y = getY() + getBbHeight() * 0.5;
            if (dissolveTicks % 2 == 0) {
                SpiritSenses.particles(server, ParticleTypes.END_ROD, getX(), y, getZ(), 3,
                        getBbWidth() * 0.4, getBbHeight() * 0.4, getBbWidth() * 0.4, 0.02);
                SpiritSenses.particles(server, ParticleTypes.WHITE_ASH, getX(), y, getZ(), 6,
                        getBbWidth() * 0.4, getBbHeight() * 0.4, getBbWidth() * 0.4, 0.02);
            }
            if (dissolveTicks == ECHO_TICK) {
                PlusEvents.spawnSoulEcho(server, this);
            }
            if (dissolveTicks >= DISSOLVE_TICKS) {
                remove(RemovalReason.KILLED);
            }
            return;
        }
        deathTime++;
        if (deathTime >= DEATH_TICKS && server != null) {
            deathPuff(server, ParticleTypes.POOF, 20);
            deathPuff(server, ParticleTypes.SQUID_INK, 6);
            remove(RemovalReason.KILLED);
        }
    }

    private void deathPuff(ServerLevel server, ParticleOptions particle, int count) {
        SpiritSenses.particles(server, particle, getX(), getY() + getBbHeight() * 0.5, getZ(), count,
                getBbWidth() * 0.5, getBbHeight() * 0.5, getBbWidth() * 0.5, 0.02);
    }

    /** Only those who can see a Hollow are kept from sleeping by one. */
    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return SoulLogic.seesSpirits(player);
    }

    /**
     * As for any monster, except one fighting (hurt by a creature in the last 100 seconds) stays, and
     * so do one a Hollow Bait holds and a practice one, which goes only with its trial.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !isPractice() && !isBaitHeld() && level().getGameTime() - lastHurtByMobAt > MOB_HURT_PROTECTION_TICKS
                && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    /** A practice Hollow stays in a peaceful world, where it cannot hurt its student; its trial takes it away. */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return !isPractice() && super.shouldDespawnInPeaceful();
    }

    /**
     * A practice Hollow is never written, so it goes with its chunk and none is left after a restart; nor is a hellbound
     * body, dead and held for a Gate that is never saved either.
     */
    @Override
    public boolean saveAsPassenger(CompoundTag tag) {
        return !isPractice() && !isHellbound() && super.saveAsPassenger(tag);
    }

    /** Nor does a practice Hollow take a portal away from its trial. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return !isPractice() && super.canUsePortal(allowPassengers);
    }

    /** Every sound, footsteps included, goes only to those who can hear spirits. Nothing is played on the client. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent() && !level().isClientSide) {
            SpiritSenses.play(this, sound, getSoundSource(), volume, pitch, screams(sound));
        }
    }

    /** Whether a soul just below the threshold of awareness hears this sound of this Hollow's faintly, from far away. */
    protected boolean screams(SoundEvent sound) {
        return isScream(sound);
    }

    /** The cries every Hollow's {@link #screams} hears; a kind with cries of its own adds them there. */
    private static boolean isScream(SoundEvent sound) {
        return sound == ModSounds.HOLLOW_AMBIENT.get() || sound == ModSounds.HOLLOW_HURT.get()
                || sound == ModSounds.HOLLOW_DEATH.get() || sound == ModSounds.FISHBONE_D_ROAR.get()
                || sound == ModSounds.GARGANTA_OPEN.get();
    }

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
        return 160;
    }

    /** Hollows never turn on one another. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return entity instanceof Hollow || super.isAlliedTo(entity);
    }

    /**
     * Hollows go only after their prey ({@link HollowLogic#isPreyFor}: what every Hollow hunts, and the
     * Hollow player whose reiatsu drew this one), and strike back at whoever just hurt them, unless that
     * is a player in creative or spectator mode. Every goal asks this, so prey that stops being prey is
     * dropped. While the sky cracks above a bait, a Hollow it holds goes after no one; a practice Hollow
     * goes after its own student alone, and strikes back at no one else.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (isBaitFinale()) {
            return false;
        }
        if (!super.canAttack(target)) {
            return false;
        }
        if (isPractice()) {
            return HollowLogic.isPreyFor(this, target);
        }
        if (HollowLogic.isPreyFor(this, target)) {
            return true;
        }
        return target == getLastHurtByMob() && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /**
     * A practice Hollow takes no target but its own student, whoever would set one: a hurt hound's call to its pack, which
     * asks no {@link #canAttack}, among them.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target == null || !isPractice() || target.getUUID().equals(trialOf)) {
            super.setTarget(target);
        }
    }

    /**
     * A spirit's body: walls and crowds cannot crush it. Nothing but its own student's blows reaches a practice Hollow,
     * save what passes every guard ({@code /kill}, the void).
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)
                || isPractice() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (source.getEntity() == null || !trialOf.equals(source.getEntity().getUUID()));
    }

    /** Big heads stick out of the hitbox, so the crosshair may aim a little wider. */
    @Override
    public float getPickRadius() {
        return kind().look.pickRadius() * getScale();
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

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) {
        return false;
    }

    /**
     * Frozen while the mask forms, while a tear takes it, while a Hollow player's jaws hold it, and while
     * Negación's light holds it, so none of its goals run.
     */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isMaskForming() || departing || isSeized() || Negacion.held(this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (drawer != null) {
            tag.putUUID("Drawer", drawer);
        }
        if (baitSite != null) {
            tag.putUUID("BaitSite", baitSite);
        }
        if (formerVillager != null) {
            VillagerData.CODEC.encodeStart(NbtOps.INSTANCE, formerVillager)
                    .resultOrPartial(BleachMod.LOGGER::error)
                    .ifPresent(saved -> tag.put("FormerVillager", saved));
        }
        tag.putBoolean("FromPlus", fromPlus);
        if (fragmentRolled) {
            tag.putBoolean("FragmentRolled", true);
        }
        if (sinner) {
            tag.putBoolean("Sinner", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        drawer = tag.hasUUID("Drawer") ? tag.getUUID("Drawer") : null;
        baitSite = tag.hasUUID("BaitSite") ? tag.getUUID("BaitSite") : null;
        formerVillager = null;
        if (tag.contains("FormerVillager", Tag.TAG_COMPOUND)) {
            VillagerData.CODEC.parse(NbtOps.INSTANCE, tag.get("FormerVillager"))
                    .resultOrPartial(BleachMod.LOGGER::error)
                    .ifPresent(data -> formerVillager = data);
        }
        fromPlus = tag.getBoolean("FromPlus");
        fragmentRolled = tag.getBoolean("FragmentRolled");
        sinner = tag.getBoolean("Sinner");
    }
}
