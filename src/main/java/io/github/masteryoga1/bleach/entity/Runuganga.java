package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.registry.ModBlocks;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.HollowPlayerLogic;
import io.github.masteryoga1.bleach.soul.RunugangaComing;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.EnumSet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Runuganga, the guardian of the white sand before Las Noches: a giant of grey sand 12 blocks tall in two hit boxes, as
 * the Gillian is ({@link Part}). His health and blow are the config's; he is no sinner and never saved. He rises by
 * himself out of Hueco Mundo's sand, or by command: {@code /summon} standing at once, the boss command rising out of the
 * ground ({@link RunugangaComing}).
 *
 * <p>His bar shows to the souls about him who see spirits. He fights intruders on his ground and whoever strikes him,
 * with a blow of both forearms after a second's wind-up ({@link FightGoal}). At his floor he slumps into sand that
 * nothing strikes while he is dry, and re-forms after reform_seconds with reform_share of his health, so dry no blow
 * ends him; {@code /kill} and the void still do.
 *
 * <p>Water is his weakness ({@link #soak}): for soak_seconds after it last reaches him his bar is blue, he has no floor
 * and takes soaked_harm times a blow's harm, and as sand his head stays out of it to be struck and he does not re-form,
 * so a blow ends him; the player who deals it has beaten him. Slain or purified, as a zanpakutō purifies any Hollow, he
 * crumbles into the sand.
 *
 * <p>With no foe or intruder on his ground for {@link #LONELY_TICKS} he sinks into the sand and is gone, never by a tear:
 * no death, so the ledger keeps no wait, but he rests a minute ({@link RunugangaComing#REST_TICKS}). A body named or kept
 * for good stays.
 */
public class Runuganga extends Hollow implements BossFloor.Floored {
    public static final float WIDTH = 4.0F, HEIGHT = 12.0F, EYE_HEIGHT = 10.5F;
    /** The body part, hips to shoulders, from this high above the feet and this tall; the head part a cube above it. */
    public static final double BODY_BOTTOM = 4.0;
    public static final float BODY_HEIGHT = 5.0F, HEAD_SIZE = 3.0F;
    public static final int PART_BODY = 0, PART_HEAD = 1;
    /** How far past his sides his forearms reach, and how far across from where he first stood his ground goes. */
    public static final double REACH = 3.0, GROUND_RANGE = 48.0;
    /** His blow's wind-up with his forearms raised, and the wait from one blow to the next wind-up. */
    public static final int WIND_UP_TICKS = 20, COOLDOWN_TICKS = 40;
    /** The health no blow takes him below: there, he slumps. */
    public static final float FLOOR = 1.0F;
    /** How long he takes to rise out of the sand and to sink into it, and how long alone before he sinks away. */
    public static final int RISE_TICKS = 60, SINK_TICKS = 20, LONELY_TICKS = 200;
    /** How far under the sand he lies at most while soaked: his head stays out of it, to be seen and struck. */
    public static final float WET_SUNK = 0.75F;
    /** Synced, so the client draws his forearms up while it is set ({@link #armsUp}). */
    private static final EntityDataAccessor<Boolean> DATA_ARMS_UP = SynchedEntityData.defineId(Runuganga.class,
            EntityDataSerializers.BOOLEAN);
    /** Synced, so the client draws him wet and lets his head out of the sand while it is set ({@link #soaked}). */
    private static final EntityDataAccessor<Boolean> DATA_SOAKED = SynchedEntityData.defineId(Runuganga.class,
            EntityDataSerializers.BOOLEAN);
    /** His bar's name: white while he is dry, blue while he is soaked. */
    private static final String BAR = "bossbar.bleach.runuganga";
    /** How often sand is thrown up about him while he is not standing. */
    private static final int SAND_EVERY_TICKS = 4;
    /** The most his head turns toward his foe in a tick, in degrees. */
    private static final float TURN = 20.0F;

    private final Part[] parts;
    private final BossBar bar = new BossBar(this, BAR, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    /** Where he first stood, the middle of his ground; never saved, as he is not. */
    private Vec3 home = Vec3.ZERO;
    /** Ticks left of his wind-up, -1 with none; and the game time from which he may wind up again. */
    private int windUp = -1;
    private long readyAt;
    /** Ticks since his pose changed, on both sides; and a slump a blow asked for, on his next tick. */
    private int poseTicks;
    private boolean slumping;
    /** Ticks since a foe or an intruder last kept him, and whether he is sinking away for good. */
    private int lonely;
    private boolean leaving;
    /** Ticks left of his soak, 0 while he is dry; and how far under he was last tick and as he died, on both sides. */
    private int soakTicks;
    private float lastSunk, fallFrom;

    public Runuganga(EntityType<? extends Runuganga> type, Level level) {
        super(type, level);
        parts = new Part[] {new Part(this, WIDTH, BODY_HEIGHT), new Part(this, HEAD_SIZE, HEAD_SIZE)};
        // Ids for himself and both parts, as the Gillian takes them.
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.RUNUGANGA.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.RUNUGANGA;
    }

    /** The Gillian's navigation, which finds no path: none ever walks a body this big. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Gillian.Navigation(this, level);
    }

    /** His fight and a look about; whom he fights is {@link #canAttack}'s. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FightGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, hurtByTargetGoal());
        targetSelector.addGoal(2, new HollowHuntGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARMS_UP, false);
        builder.define(DATA_SOAKED, false);
    }

    /** Whether his forearms are raised for a blow, for the model. */
    public boolean armsUp() {
        return entityData.get(DATA_ARMS_UP);
    }

    /** His health and his blow's damage are the config's. */
    @Override
    protected ConfigBases configBases() {
        return new ConfigBases(BleachConfig.get(BleachConfig.RUNUGANGA_HEALTH),
                BleachConfig.get(BleachConfig.RUNUGANGA_DAMAGE));
    }

    /**
     * Every tick, so with NoAI too: his pose's count; on the server his changes while he lives, then his bar; on both,
     * how far under he is now, for a death to crumble from.
     */
    @Override
    public void tick() {
        super.tick();
        poseTicks++;
        if (level() instanceof ServerLevel server) {
            if (isAlive()) {
                changes(server);
            }
            bar.tick();
        }
        lastSunk = sunk(0.0F);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        home = position();
        tickParts();
    }

    /** His parts follow him on both sides, after he has moved. */
    @Override
    public void aiStep() {
        super.aiStep();
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

    public Part head() {
        return parts[PART_HEAD];
    }

    public BossBar bossBar() {
        return bar;
    }

    /** His forearms reach out of his box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0, 0.0, 2.0);
    }

    /** Whom he fights: none as sand; on his ground an intruder, or whoever struck him last, as any Hollow strikes back. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return standing() && super.canAttack(target) && onGround(target.position())
                && (target == getLastHurtByMob() || target instanceof Player player && intrudes(player));
    }

    /**
     * Whether this soul is an intruder: alive, by survival's rules, aware, neither Hollow nor Arrancar, and out of the
     * grace a Garganta's landing gives it ({@link RunugangaComing#inLandingGrace}).
     */
    public static boolean intrudes(Player soul) {
        if (!soul.isAlive() || !HollowPlayerLogic.survivalRules(soul) || !SoulLogic.hasSoul(soul)) {
            return false;
        }
        SoulData data = SoulLogic.get(soul);
        return SoulLogic.isAware(data) && !HollowPlayerLogic.isHollowOrArrancar(data)
                && !RunugangaComing.inLandingGrace(soul, soul.level().getGameTime());
    }

    /** His ground: within {@link #GROUND_RANGE} across of his home and, in Hueco Mundo, on his sand above its caverns. */
    public boolean onGround(Vec3 at) {
        return Mth.square(at.x - home.x) + Mth.square(at.z - home.z) <= GROUND_RANGE * GROUND_RANGE
                && (level().dimension() != BleachWorlds.HUECO_MUNDO
                || at.y >= HuecoMundoPlan.CAVERN_CEILING_Y && onTheSand(Mth.floor(at.x), Mth.floor(at.z)));
    }

    /** Whether this column of Hueco Mundo lies on the level sand between Las Noches' wall and the dunes. */
    public static boolean onTheSand(int x, int z) {
        double r = HuecoMundoPlan.radius(x, z);
        return r >= HuecoMundoPlan.WALL_OUTER_RADIUS && r <= HuecoMundoPlan.LEVEL_RADIUS;
    }

    /** Both forearms come down: a blow on this foe if it is in reach still. */
    void slam(LivingEntity target) {
        windUp = -1;
        entityData.set(DATA_ARMS_UP, false);
        swing(InteractionHand.MAIN_HAND);
        if (isWithinMeleeAttackRange(target)) {
            doHurtTarget(target);
        }
    }

    /** His forearms reach past his sides, his whole height. */
    @Override
    protected AABB getAttackBoundingBox() {
        return getBoundingBox().inflate(REACH, 0.0, REACH);
    }

    /** Whether he stands, rather than rising or lying as sand. */
    public boolean standing() {
        Pose pose = getPose();
        return pose != Pose.EMERGING && pose != Pose.DIGGING;
    }

    /** Whether he lies as sand soaked, his head out of it to be aimed at and struck; rising, soaked or not, he is not. */
    public boolean wetMound() {
        return soaked() && getPose() == Pose.DIGGING;
    }

    /** None while he is soaked, so a blow may end him; else {@link #FLOOR}. */
    @Override
    public float floor() {
        return soaked() ? 0.0F : FLOOR;
    }

    /** A blow has left him on his floor: he slumps on his next tick, once it has landed. */
    @Override
    public void atFloor(DamageSource source) {
        slumping = true;
    }

    /**
     * He slumps where he stands into the sand, where nothing strikes him while he is dry, until he re-forms
     * ({@link #changes}); his bar hides unless he is soaked.
     */
    public void slump() {
        if (!isAlive()) {
            return;
        }
        windUp = -1;
        entityData.set(DATA_ARMS_UP, false);
        setPose(Pose.DIGGING);
        if (!soaked()) {
            bar.setHidden(true);
        }
        playSound(ModSounds.RUNUGANGA_SLUMP.get(), 2.0F, 0.8F);
    }

    /** He rises out of the sand with reform_share of his health, or what he has if that is more. */
    public void reform() {
        if (!isAlive()) {
            return;
        }
        setHealth(Math.max(getHealth(), getMaxHealth() * (float) BleachConfig.get(BleachConfig.RUNUGANGA_REFORM_SHARE)));
        bar.setHidden(false);
        rise();
    }

    /** He rises out of the sand where he is, standing after {@link #RISE_TICKS}; nothing reaches him until then. */
    public void rise() {
        if (!isAlive()) {
            return;
        }
        setPose(Pose.EMERGING);
        playSound(ModSounds.RUNUGANGA_RISE.get(), 2.0F, 0.8F);
    }

    /**
     * A soak counts down first; then a slump a blow asked for; as sand, his going once sunk if he sinks away, else his
     * re-forming once dry; risen, his standing.
     */
    private void changes(ServerLevel server) {
        if (soakTicks > 0 && --soakTicks == 0) {
            dry();
        }
        Pose pose = getPose();
        if (slumping) {
            slumping = false;
            slump();
        } else if (pose == Pose.DIGGING && leaving && poseTicks >= SINK_TICKS) {
            RunugangaComing.rest(server.getServer(), RunugangaComing.REST_TICKS);
            discard();
            return;
        } else if (pose == Pose.DIGGING && !leaving && !soaked()
                && poseTicks >= BleachConfig.get(BleachConfig.RUNUGANGA_REFORM_SECONDS) * 20) {
            reform();
        } else if (pose == Pose.EMERGING && poseTicks >= RISE_TICKS) {
            setPose(Pose.STANDING);
        }
        if (!standing() && poseTicks % SAND_EVERY_TICKS == 0) {
            SpiritSenses.particles(server, new BlockParticleOption(ParticleTypes.BLOCK,
                    ModBlocks.WHITE_SAND.get().defaultBlockState()), getX(), getY() + 0.5, getZ(), 12, WIDTH / 3.0, 0.5,
                    WIDTH / 3.0, 0.15);
        }
        if (soaked() && tickCount % SAND_EVERY_TICKS == 0) {
            float above = HEIGHT * (1.0F - sunk(0.0F));
            SpiritSenses.particles(server, ParticleTypes.FALLING_WATER, getX(), getY() + above * 0.5, getZ(), 8,
                    WIDTH / 3.0, above / 4.0, WIDTH / 3.0, 0.0);
        }
    }

    /**
     * How far under the sand he is, for the model: 0 standing, 1 gone; up over RISE_TICKS, down over SINK_TICKS but no
     * further than WET_SUNK while he is soaked; dying, slain or purified, the rest of the way from where he was, over
     * SINK_TICKS.
     */
    public float sunk(float partialTick) {
        float ticks = poseTicks + partialTick;
        return switch (getPose()) {
            case EMERGING -> 1.0F - Mth.clamp(ticks / RISE_TICKS, 0.0F, 1.0F);
            case DIGGING -> Math.min(Mth.clamp(ticks / SINK_TICKS, 0.0F, 1.0F), soaked() ? WET_SUNK : 1.0F);
            case DYING -> Mth.lerp(Mth.clamp(ticks / SINK_TICKS, 0.0F, 1.0F), fallFrom, 1.0F);
            default -> 0.0F;
        };
    }

    /** A new pose restarts his count, on both sides; a death keeps how far under he was, to crumble from. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_POSE.equals(key)) {
            if (getPose() == Pose.DYING) {
                fallFrom = lastSunk;
            }
            poseTicks = 0;
        }
    }

    /** Nothing aims at him while he is not standing, unless he lies soaked as sand: not the crosshair, not an arrow. */
    @Override
    public boolean isPickable() {
        return (standing() || wetMound()) && super.isPickable();
    }

    /** Rising, or as sand and dry, nothing harms him but what passes every guard ({@code /kill}, the void). */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source)
                || !standing() && !wetMound() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** Whether water soaks him now, on both sides. */
    public boolean soaked() {
        return entityData.get(DATA_SOAKED);
    }

    /**
     * Water reaches him: he is soaked for soak_seconds from now, a soak already on lengthened to that. Never once dead
     * or sinking away, and on the server only. A fresh soak turns his bar blue and shows it, on his sand too, and is heard.
     */
    public void soak() {
        if (!isAlive() || leaving || level().isClientSide) {
            return;
        }
        soakTicks = BleachConfig.get(BleachConfig.RUNUGANGA_SOAK_SECONDS) * 20;
        if (!soaked()) {
            entityData.set(DATA_SOAKED, true);
            bar.restyle(BAR, BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
            bar.setHidden(false);
            playSound(ModSounds.RUNUGANGA_SOAK.get(), 2.0F, 0.8F);
        }
    }

    /** His soak has run out: dry, with his floor and his white bar back, the bar hidden again on sand he will rise from. */
    private void dry() {
        entityData.set(DATA_SOAKED, false);
        bar.restyle(BAR, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        if (getPose() == Pose.DIGGING && !leaving) {
            bar.setHidden(true);
        }
    }

    /** Soaked, a blow harms him soaked_harm times over; what passes every guard comes as it is. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean harder = soaked() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return super.hurt(source, harder ? amount * (float) BleachConfig.get(BleachConfig.RUNUGANGA_SOAKED_HARM) : amount);
    }

    /**
     * Ended by a blow, which only a soaked body can be: the player who dealt it has brought Runuganga down, whether it slew
     * him or purified him (begun in the death event, before this).
     */
    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        // A cancelled death leaves him alive, and a second call does nothing: only a real first death counts.
        if (!wasDead && dead && source.getEntity() instanceof ServerPlayer player) {
            Milestones.grant(player, Milestones.RUNUGANGA);
        }
    }

    /**
     * Each second he stands, a foe he may fight or an intruder on his ground keeps him; with neither for LONELY_TICKS he
     * sinks away. Not with NoAI, nor for a body named or kept for good.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!standing() || tickCount % 20 != 0 || isPersistenceRequired() || hasCustomName()) {
            return;
        }
        LivingEntity target = getTarget();
        boolean kept = target != null && target.isAlive() && canAttack(target)
                || level().players().stream().anyMatch(soul -> intrudes(soul) && onGround(soul.position()));
        lonely = kept ? 0 : lonely + 20;
        if (lonely >= LONELY_TICKS) {
            leave();
        }
    }

    /** He sinks into the sand where he stands and is gone once sunk ({@link #changes}), never by a tear. */
    @Override
    protected void leave() {
        leaving = true;
        slump();
    }

    /** A soul that stops seeing him loses his bar at once. */
    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.forget(player);
    }

    /** He goes when his chunk unloads, never because players are far. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    /** One of his two hit boxes: his body from the hips to the shoulders, and his head (see {@link GiantPart}). */
    public static final class Part extends GiantPart<Runuganga> {
        Part(Runuganga parent, float width, float height) {
            super(parent, width, height);
        }

        @Override
        public boolean isPickable() {
            return getParent().isPickable();
        }
    }

    /** He strides at his foe by his move control, as no path walks him; in reach, a second's wind-up, then his blow. */
    static final class FightGoal extends Goal {
        private final Runuganga giant;

        FightGoal(Runuganga giant) {
            this.giant = giant;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = giant.getTarget();
            return target != null && target.isAlive() && giant.canAttack(target);
        }

        @Override
        public void stop() {
            giant.windUp = -1;
            giant.entityData.set(DATA_ARMS_UP, false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = giant.getTarget();
            if (target == null || !giant.canAttack(target)) {
                return;
            }
            giant.getLookControl().setLookAt(target, TURN, TURN);
            boolean inReach = giant.isWithinMeleeAttackRange(target);
            if (!inReach) {
                giant.getMoveControl().setWantedPosition(target.getX(), giant.getY(), target.getZ(), 1.0);
            }
            long now = giant.level().getGameTime();
            if (giant.windUp < 0 && inReach && now >= giant.readyAt) {
                giant.windUp = WIND_UP_TICKS;
                giant.entityData.set(DATA_ARMS_UP, true);
            } else if (giant.windUp > 0 && --giant.windUp == 0) {
                giant.readyAt = now + COOLDOWN_TICKS;
                giant.slam(target);
            }
        }
    }
}
