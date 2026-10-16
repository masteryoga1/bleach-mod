package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.soul.ChainEvents;
import io.github.masteryoga1.bleach.soul.HollowLogic;
import io.github.masteryoga1.bleach.soul.PlusEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Plus: the whole soul of someone who died, lingering with its Chain of Fate hanging from its chest.
 * It looks like the villager it was, keeps near the home it had, and murmurs to an aware soul who
 * speaks to it. Only the aware can see one. Its chain wears away, faster when it is hurt and slower
 * with a Shinigami near, and a Plus whose chain runs out becomes a Hollow on the spot (see
 * {@link PlusEvents#hollowfy}). Hollows hunt Pluses, and a Plus flees them and Hollow players.
 *
 * <p>Konsō sends one on: it sinks away into the light. The soul that rises from a purified Hollow is a
 * Plus too, rising as it passes on. A departing Plus is never saved, cannot be hurt, and is no one's prey.
 */
public class PlusSpirit extends PathfinderMob implements VillagerDataHolder {
    private static final EntityDataAccessor<Float> DATA_CHAIN =
            SynchedEntityData.defineId(PlusSpirit.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA =
            SynchedEntityData.defineId(PlusSpirit.class, EntityDataSerializers.VILLAGER_DATA);
    private static final EntityDataAccessor<Byte> DATA_DEPARTING =
            SynchedEntityData.defineId(PlusSpirit.class, EntityDataSerializers.BYTE);

    /** How a Plus is leaving, if it is: not at all, rising as a purified Hollow's soul, or sinking away after Konsō. */
    public static final byte STAYING = 0;
    public static final byte RISING = 1;
    public static final byte SINKING = 2;

    public static final float REIRYOKU = 12.0F;
    public static final int RISE_TICKS = 40;
    public static final int SINK_TICKS = 30;
    /** How many lines there are of {@code message.bleach.plus.speech.N} and {@code message.bleach.plus.farewell.N}. */
    public static final int SPEECH_LINES = 6;
    public static final int FAREWELL_LINES = 4;
    /** Ticks between Encroachment pangs, as for a Plus player. */
    public static final int PANG_INTERVAL_TICKS = 600;
    /** How long a Plus whose chain ran out where no Hollow may form waits on its last link before trying again. */
    public static final int CONVERSION_RETRY_TICKS = 200;
    /** A Hollow's bite never takes a chain below this many ticks. */
    public static final int BITE_FLOOR_TICKS = 1200;

    private static final double RISE_PER_TICK = 0.04;
    private static final double SINK_PER_TICK = 0.06;
    private static final int RISE_PARTICLE_INTERVAL = 4;
    private static final int SINK_PARTICLE_INTERVAL = 3;
    private static final int SPEAK_COOLDOWN_TICKS = 20;
    private static final int DEATH_TICKS = 20;
    /** The synced chain changes once it has moved this far, or when it would be drawn with a different number of pieces. */
    private static final float CHAIN_SYNC_STEP = 0.01F;
    /** Chain pieces drawn at full length, as for a Plus player. */
    private static final int CHAIN_PIECES = 5;

    int departTicks;
    int pangTicks;
    int nextConversionTry;
    long lastSpokeAt = ShinigamiProgress.NEVER;
    boolean nearShinigami;
    /** A Plus that wandered in near a player, rather than one a villager left: it despawns like a monster. */
    private boolean ambient;
    /** Where the Plus stays, and how far from it it may wander. Vanilla does not save a mob's restriction. */
    @Nullable
    private BlockPos home;
    private int homeRadius;

    public PlusSpirit(EntityType<? extends PlusSpirit> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 15.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.SAFE_FALL_DISTANCE, HollowKind.SAFE_FALL_DISTANCE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Hollow.class, 12.0F, 1.0, 1.3));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.0, 1.3, PlusSpirit::isHollowPlayer));
        goalSelector.addGoal(2, new PanicGoal(this, 1.3));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    /** A player who is a Hollow or an Arrancar, whom a Plus runs from as from any Hollow. */
    private static boolean isHollowPlayer(LivingEntity entity) {
        if (!(entity instanceof Player player) || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)
                || !SoulLogic.hasSoul(player)) {
            return false;
        }
        SoulState state = SoulLogic.get(player).state();
        return state == SoulState.HOLLOW || state == SoulState.ARRANCAR;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHAIN, 1.0F);
        builder.define(DATA_VILLAGER_DATA, new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
        builder.define(DATA_DEPARTING, STAYING);
    }

    /** A Plus that appears on its own looks like a villager of the biome it is in. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setVillagerData(getVillagerData().setType(VillagerType.byBiome(level.getBiome(blockPosition()))));
        initSoul();
        return data;
    }

    /**
     * A Plus's soul. The state change gives it a player's chain first, so the Plus's own length
     * replaces it after.
     */
    private void initSoul() {
        SoulData soul = getData(SoulAttachments.SOUL);
        SoulLogic.initialize(soul);
        SoulLogic.changeState(soul, SoulState.PLUS);
        soul.startChain(HollowLogic.plusChainTicks());
        soul.setMaxReiryoku(REIRYOKU);
        soul.setReiryoku(REIRYOKU);
        syncChain(soul);
    }

    /** This Plus's soul, made on first use for Pluses that skipped spawning (game tests, summons with data). */
    public SoulData soul() {
        if (!SoulLogic.hasSoul(this)) {
            initSoul();
        }
        return SoulLogic.get(this);
    }

    /** Gives the Plus a fresh chain, worn down to this share of its full length. */
    public void startChain(float fraction) {
        SoulData soul = soul();
        soul.startChain(HollowLogic.plusChainTicks());
        soul.setChainTicks(Math.max(1, Math.round(soul.chainMaxTicks() * Mth.clamp(fraction, 0.0F, 1.0F))));
        nextConversionTry = 0;
        syncChain(soul);
    }

    /** What is left of the chain, from 0 to 1. The client reads the synced value. */
    public float chainFraction() {
        return level().isClientSide ? entityData.get(DATA_CHAIN) : soul().chainFraction();
    }

    private void syncChain(SoulData soul) {
        float fraction = soul.chainFraction();
        float shown = entityData.get(DATA_CHAIN);
        if (Math.abs(fraction - shown) >= CHAIN_SYNC_STEP || chainPieces(fraction) != chainPieces(shown)) {
            entityData.set(DATA_CHAIN, fraction);
        }
    }

    private static int chainPieces(float fraction) {
        return Mth.clamp(Mth.ceil(fraction * CHAIN_PIECES), 1, CHAIN_PIECES);
    }

    public boolean isDeparting() {
        return departing() != STAYING;
    }

    /** {@link #STAYING}, {@link #RISING} or {@link #SINKING}. */
    public byte departing() {
        return entityData.get(DATA_DEPARTING);
    }

    /** The Plus leaves for good: it rises or sinks away and is gone (see {@link #RISE_TICKS}, {@link #SINK_TICKS}). */
    public void startDeparture(byte how) {
        if ((how != RISING && how != SINKING) || isDeparting()) {
            return;
        }
        entityData.set(DATA_DEPARTING, how);
        departTicks = 0;
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
    }

    /** How far through its departure the Plus is, from 0 to 1; 0 while it stays. */
    public float departProgress(float partialTick) {
        int length = departureTicks();
        return length <= 0 ? 0.0F : Mth.clamp((departTicks + partialTick) / length, 0.0F, 1.0F);
    }

    private int departureTicks() {
        return switch (departing()) {
            case RISING -> RISE_TICKS;
            case SINKING -> SINK_TICKS;
            default -> 0;
        };
    }

    /** Keeps the Plus within {@code radius} blocks of {@code pos}, now and after it is loaded again. */
    public void bindTo(BlockPos pos, int radius) {
        home = pos.immutable();
        homeRadius = radius;
        restrictTo(home, radius);
    }

    public boolean isAmbient() {
        return ambient;
    }

    public void setAmbient(boolean ambient) {
        this.ambient = ambient;
    }

    /**
     * Server side, every tick so it still happens with NoAI: the chain wears away, Encroachment pangs,
     * and a chain that runs out turns the Plus into a Hollow. Where none may form, the last link holds
     * and it tries again later. A departing Plus only rises or sinks.
     */
    @Override
    public void tick() {
        super.tick();
        if (isDeparting()) {
            tickDeparture();
            return;
        }
        if (!(level() instanceof ServerLevel server) || isRemoved() || !isAlive()) {
            return;
        }
        SoulData soul = soul();
        if (tickCount % 20 == 0) {
            nearShinigami = ChainEvents.shinigamiNearby(this);
        }
        if (tickCount >= nextConversionTry) {
            double speed = HollowLogic.plusErosionSpeed(getHealth() / getMaxHealth(), nearShinigami);
            if (SoulLogic.erodeChain(soul, speed)) {
                if (PlusEvents.hollowfy(server, this)) {
                    return;
                }
                soul.setChainTicks(1);
                nextConversionTry = tickCount + CONVERSION_RETRY_TICKS;
            }
        }
        if (SoulLogic.isEncroaching(soul)) {
            if (++pangTicks >= PANG_INTERVAL_TICKS) {
                pangTicks = 0;
                pang(server);
            }
        } else {
            pangTicks = 0;
        }
        syncChain(soul);
    }

    /** The links eat each other: a dark puff where the chain meets the chest, and the chain grinding. */
    private void pang(ServerLevel server) {
        SpiritSenses.particles(server, ParticleTypes.SQUID_INK, getX(), getY() + getBbHeight() * 0.65, getZ(), 4,
                0.1, 0.1, 0.1, 0.01);
        playSound(SoundEvents.CHAIN_BREAK, 1.0F, 0.6F);
    }

    /** Rises or sinks a little, trailing soul wisps or light, until it is gone. */
    private void tickDeparture() {
        noPhysics = true;
        departTicks++;
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        boolean rising = departing() == RISING;
        setDeltaMovement(Vec3.ZERO);
        setPos(getX(), getY() + (rising ? RISE_PER_TICK : -SINK_PER_TICK), getZ());
        if (departTicks % (rising ? RISE_PARTICLE_INTERVAL : SINK_PARTICLE_INTERVAL) == 0) {
            SpiritSenses.particles(server, rising ? ParticleTypes.SOUL : ParticleTypes.END_ROD,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.2, 0.4, 0.2, 0.01);
        }
        if (departTicks >= departureTicks()) {
            discard();
        }
    }

    /**
     * Sneaking with a zanpakutō performs Konsō (see {@link ShinigamiEvents#konsoPlus}); an empty hand
     * from an aware soul gets a few murmured words. The client only swings the arm: the server decides.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || isDeparting()) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.is(BleachTags.ZANPAKUTO)) {
            if (level().isClientSide) {
                return InteractionResult.sidedSuccess(true);
            }
            return ShinigamiEvents.konsoPlus(player, this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (held.isEmpty()) {
            if (level().isClientSide) {
                return InteractionResult.sidedSuccess(true);
            }
            return speakTo(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    /** A few murmured words for an aware soul, no more than once a second. */
    private boolean speakTo(Player player) {
        if (!SoulLogic.hasSoul(player) || !SoulLogic.isAware(SoulLogic.get(player))) {
            return false;
        }
        long now = level().getGameTime();
        if (now - lastSpokeAt < SPEAK_COOLDOWN_TICKS) {
            return true;
        }
        lastSpokeAt = now;
        getLookControl().setLookAt(player, 30.0F, 30.0F);
        player.displayClientMessage(Component.translatable("message.bleach.plus.speaks",
                Component.translatable("message.bleach.plus.speech." + random.nextInt(SPEECH_LINES))), true);
        return true;
    }

    @Override
    public VillagerData getVillagerData() {
        return entityData.get(DATA_VILLAGER_DATA);
    }

    @Override
    public void setVillagerData(VillagerData data) {
        entityData.set(DATA_VILLAGER_DATA, data);
    }

    /** A villager's Plus stays where it was left; one that wandered in goes when no one is near. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return ambient && !isPersistenceRequired();
    }

    @Override
    public boolean shouldBeSaved() {
        return !isDeparting() && super.shouldBeSaved();
    }

    /** Falls as a mob does, then fades into soul wisps only the aware can see, without the usual puff of smoke. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= DEATH_TICKS && level() instanceof ServerLevel server && !isRemoved()) {
            deathWisps(server, ParticleTypes.SOUL, 6);
            deathWisps(server, ParticleTypes.SQUID_INK, 2);
            remove(RemovalReason.KILLED);
        }
    }

    private void deathWisps(ServerLevel server, ParticleOptions particle, int count) {
        SpiritSenses.particles(server, particle, getX(), getY() + getBbHeight() * 0.5, getZ(), count,
                0.25, 0.4, 0.25, 0.02);
    }

    /** Every sound goes only to those who can hear spirits. Nothing is played on the client. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!isSilent() && !level().isClientSide) {
            SpiritSenses.play(this, sound, getSoundSource(), volume, pitch, false);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SOUL_ESCAPE.value();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SOUL_ESCAPE.value();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDeparting();
    }

    @Override
    public boolean isPickable() {
        return !isDeparting() && super.isPickable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isDeparting() && super.hurt(source, amount);
    }

    /** A spirit's body: walls and crowds cannot crush it. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING);
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
        VillagerData.CODEC.encodeStart(NbtOps.INSTANCE, getVillagerData())
                .resultOrPartial(BleachMod.LOGGER::error)
                .ifPresent(saved -> tag.put("VillagerData", saved));
        tag.putBoolean("Ambient", ambient);
        if (home != null) {
            tag.putInt("HomeX", home.getX());
            tag.putInt("HomeY", home.getY());
            tag.putInt("HomeZ", home.getZ());
            tag.putInt("HomeRadius", homeRadius);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("VillagerData", Tag.TAG_COMPOUND)) {
            VillagerData.CODEC.parse(NbtOps.INSTANCE, tag.get("VillagerData"))
                    .resultOrPartial(BleachMod.LOGGER::error)
                    .ifPresent(this::setVillagerData);
        }
        ambient = tag.getBoolean("Ambient");
        if (tag.contains("HomeRadius", Tag.TAG_INT)) {
            bindTo(new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ")), tag.getInt("HomeRadius"));
        }
    }
}
