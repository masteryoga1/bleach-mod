package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.soul.BaitEvents;
import io.github.masteryoga1.bleach.soul.BaitLogic;
import io.github.masteryoga1.bleach.soul.BaitSite;
import io.github.masteryoga1.bleach.soul.BaitSites;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyNpcLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiProgress;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Quincy Hunter: a proud young Quincy who turns up where Hollows come through and hunts them
 * himself. He challenges a Quincy or a Shinigami he meets to a contest, whoever destroys more Hollows
 * (see {@link BaitEvents#start}); steps out of the way of Hollows with Hirenkyaku; hardens his body
 * with Blut Vene once badly hurt, and mends slowly. A player who strikes him drives him off, and he
 * never strikes back at one.
 */
public final class QuincyHunter extends QuincyNpc {
    public static final float MAX_HEALTH = 60.0F;
    /** Blut Vene comes on at or below this share of his health, and goes at or above the second. */
    public static final float VENE_ON = 0.5F;
    public static final float VENE_OFF = 0.75F;
    /** The share of a blow that gets through Blut Vene. */
    public static final float VENE_FACTOR = 0.75F;
    /** At or below this share of his health he retreats. */
    public static final float RETREAT_AT = 0.2F;
    /** Blut Vene goes this long after the last blow. */
    public static final int VENE_LINGER_TICKS = 200;
    /** He mends a point of health every interval, once he has not been hurt for the delay. */
    public static final int REGEN_DELAY_TICKS = 100;
    public static final int REGEN_INTERVAL_TICKS = 40;
    /** Hirenkyaku out of harm's way: no more often than this, from a Hollow this close that hunts him. */
    public static final int DODGE_COOLDOWN_TICKS = 60;
    public static final double DODGE_TRIGGER = 4.0;
    /** As far as a dodge goes; one that would go less than the minimum is not taken. */
    public static final double DODGE_DISTANCE = 6.0;
    public static final double DODGE_MIN = 2.0;
    /** He greets his visitor when they first come this close. */
    public static final double GREET_RANGE = 8.0;

    private static final long NEVER = ShinigamiProgress.NEVER;
    /** How often he looks around for a Hollow upon him, and for his visitor to greet. */
    private static final int DODGE_CHECK_TICKS = 4;
    private static final int GREET_CHECK_TICKS = 10;
    /** A dodge turns up to this far either side of straight away from the threat. */
    private static final float DODGE_SPREAD_DEGREES = 45.0F;
    private static final float REGEN_AMOUNT = 1.0F;

    // Server-side, not saved: a visiting Hunter is never saved, and a contest does not outlive a reload.
    @Nullable
    private UUID contestSite;
    private boolean contestOffered;
    private long lastHurtAt = NEVER;
    private long dodgeReadyAt = NEVER;
    private boolean greeted;

    public QuincyHunter(EntityType<? extends QuincyHunter> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createHunterAttributes() {
        return createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH);
    }

    // ---- Who he is ----

    @Override
    protected Bloodline soulBloodline() {
        return Bloodline.ECHT;
    }

    @Override
    protected int soulTier() {
        return 2;
    }

    @Override
    protected float soulReiryoku() {
        return 150.0F;
    }

    @Override
    protected float arrowDamage() {
        return (float) BleachConfig.get(BleachConfig.HUNTER_ARROW_DAMAGE);
    }

    @Override
    public int shotInterval() {
        return QuincyNpcLogic.shotInterval(level().getDifficulty());
    }

    @Override
    public float shotInaccuracy() {
        return QuincyNpcLogic.hunterInaccuracy(level().getDifficulty());
    }

    @Override
    public float minRange() {
        return 6.0F;
    }

    @Override
    public float maxRange() {
        return 20.0F;
    }

    @Override
    public String dialogueKey() {
        return "hunter";
    }

    /** Any quarry within his reach. */
    @Override
    public boolean wantsToShoot(LivingEntity target) {
        return super.wantsToShoot(target) && distanceToSqr(target) <= (double) maxRange() * maxRange();
    }

    // ---- The contest ----

    /** The contest site he is running, if any. */
    @Nullable
    public UUID contestSite() {
        return contestSite;
    }

    public void setContestSite(@Nullable UUID site) {
        contestSite = site;
    }

    /** Whether he has offered his one contest of this visit. */
    public boolean contestOffered() {
        return contestOffered;
    }

    public void setContestOffered(boolean value) {
        contestOffered = value;
    }

    /** His visit does not run out while a contest is on. */
    @Override
    protected boolean countsDown() {
        return contestSite == null;
    }

    /** The site of the contest he is running, while it lasts. Server side. */
    @Nullable
    private BaitSite contest() {
        MinecraftServer server = level().getServer();
        return contestSite != null && server != null ? BaitSites.get(server).find(contestSite) : null;
    }

    // ---- Talking ----

    @Override
    public DialogueView viewFor(Player visitor, SoulData data, long now) {
        BaitSite site = contest();
        boolean withVisitor = site != null && visitor.getUUID().equals(site.owner());
        return QuincyNpcLogic.hunterView(data, withVisitor, contestOffered);
    }

    /** A contest cannot start where a Hollow Bait could not be crushed, for this visitor, here and now. */
    @Override
    public List<NpcAction> unreadyFor(Player visitor, SoulData data, DialogueView view, long now) {
        if (!NpcAction.CONTEST.allowedIn(view)) {
            return List.of();
        }
        return contestRefusal(visitor, data) != null || !(visitor instanceof ServerPlayer)
                ? List.of(NpcAction.CONTEST) : List.of();
    }

    @Nullable
    private BaitLogic.Refusal contestRefusal(Player visitor, SoulData data) {
        return visitor instanceof ServerPlayer player ? BaitEvents.crushRefusal(player, data, blockPosition(), false) : null;
    }

    /** During a contest, the tally and the time left; otherwise why one cannot start now. */
    @Override
    public List<Component> notesFor(Player visitor, SoulData data, DialogueView view, long now) {
        List<Component> notes = new ArrayList<>();
        if (view == DialogueView.HUNTER_CONTEST) {
            BaitSite site = contest();
            if (site != null) {
                notes.add(Component.translatable("screen.bleach.dialogue.note.tally", site.ownerKills(), site.rivalKills()));
                notes.add(Component.translatable("screen.bleach.dialogue.note.time_left",
                        QuincyArtsLogic.formatTicks((long) site.durationTicks() - site.activeTicks())));
            }
        } else if (NpcAction.CONTEST.allowedIn(view)) {
            BaitLogic.Refusal refusal = contestRefusal(visitor, data);
            if (refusal != null) {
                notes.add(Component.translatable(refusal.key()));
            }
        }
        return notes;
    }

    /**
     * The contest: a site of its own, centred on him, which counts who destroys more Hollows. One a
     * visit: a challenge accepted from a dialogue opened before another soul took him up is refused.
     * A visiting Hunter then keeps to his challenger, wherever the soul he first came for has gone.
     */
    @Override
    protected void act(ServerPlayer visitor, SoulData data, NpcAction action, long now) {
        if (action != NpcAction.CONTEST || contestOffered || contestSite != null) {
            return;
        }
        BaitSite site = BaitEvents.start(visitor, blockPosition(), true, this, now);
        if (site == null) {
            return;
        }
        contestSite = site.id();
        contestOffered = true;
        if (!isPermanent()) {
            setVisitor(visitor.getUUID());
        }
        // They have spoken already; no greeting after the challenge.
        greeted = true;
        visitor.sendSystemMessage(Component.translatable("message.bleach.contest.start",
                BleachConfig.get(BleachConfig.CONTEST_MINUTES)).withStyle(ChatFormatting.AQUA));
    }

    // ---- Fighting ----

    /** Whether Blut Vene hardens his body now. */
    public boolean veneActive() {
        return veneShown();
    }

    private void setVene(boolean on) {
        setFlag(FLAG_VENE, on);
    }

    /**
     * Blut Vene takes a quarter off every blow but those that pass through invulnerability or
     * effects, once his health is down to half. A player's blow drives a visiting Hunter off and ends
     * any contest he runs; a permanent one steps aside instead and never turns on them. He turns on a
     * Hollow that strikes him, steps out of the way of a melee blow, and retreats once badly hurt.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        long now = level().getGameTime();
        if (isAlive() && getHealth() <= VENE_ON * getMaxHealth()) {
            setVene(true);
        }
        boolean hardened = veneActive() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !source.is(DamageTypeTags.BYPASSES_EFFECTS);
        if (!super.hurt(source, hardened ? amount * VENE_FACTOR : amount)) {
            return false;
        }
        lastHurtAt = now;
        if (!isAlive() || isRemoved()) {
            return true;
        }
        if (getHealth() <= VENE_ON * getMaxHealth()) {
            setVene(true);
        }
        Entity cause = source.getEntity();
        if (cause instanceof Player player) {
            // The contest is off at once, even if Gritz keeps him here a while.
            BaitEvents.onRivalLeft(this, LeaveCause.HURT_BY_PLAYER);
            if (isPermanent()) {
                if (getTarget() == player) {
                    setTarget(null);
                }
                tryDodge(player, now);
            } else {
                player.sendSystemMessage(Component.translatable("message.bleach.hunter.leaves.hurt")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                leave(LeaveCause.HURT_BY_PLAYER);
            }
            return true;
        }
        if (cause instanceof LivingEntity attacker) {
            if (isQuarry(attacker)) {
                setTarget(attacker);
            }
            if (source.getDirectEntity() == attacker) {
                tryDodge(attacker, now);
            }
        }
        if (getHealth() <= RETREAT_AT * getMaxHealth()) {
            if (isPermanent()) {
                if (cause instanceof LivingEntity attacker) {
                    tryDodge(attacker, now);
                }
            } else {
                leave(LeaveCause.RETREAT);
            }
        }
        return true;
    }

    /**
     * A Hirenkyaku step out of harm's way: straight away from the threat, give or take 45 degrees, as
     * far as six blocks along the ground. Only on the ground, with AI, no more than once in three
     * seconds, and never while Gritz binds him.
     *
     * @return whether he stepped
     */
    public boolean tryDodge(LivingEntity threat, long now) {
        if (level().isClientSide || !isAlive() || isNoAi() || now < dodgeReadyAt || !onGround()) {
            return false;
        }
        Vec3 away = position().subtract(threat.position());
        away = new Vec3(away.x, 0.0, away.z);
        if (away.lengthSqr() < 1.0E-4) {
            away = behind();
        }
        float turn = (getRandom().nextFloat() * 2.0F - 1.0F) * DODGE_SPREAD_DEGREES * Mth.DEG_TO_RAD;
        double cos = Mth.cos(turn);
        double sin = Mth.sin(turn);
        Vec3 direction = new Vec3(away.x * cos - away.z * sin, 0.0, away.z * cos + away.x * sin);
        if (!hirenkyaku(direction, DODGE_DISTANCE, DODGE_MIN)) {
            return false;
        }
        dodgeReadyAt = now + DODGE_COOLDOWN_TICKS;
        return true;
    }

    /**
     * Server side, every tick: Blut Vene fades once he is mended or left alone, he mends slowly, he
     * steps away from a Hollow upon him, and he greets his visitor when they first come close.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || isRemoved() || !isAlive()) {
            return;
        }
        long now = level().getGameTime();
        if (veneActive() && (getHealth() >= VENE_OFF * getMaxHealth() || now - lastHurtAt >= VENE_LINGER_TICKS)) {
            setVene(false);
        }
        if (tickCount % REGEN_INTERVAL_TICKS == 0 && getHealth() < getMaxHealth()
                && now - lastHurtAt >= REGEN_DELAY_TICKS) {
            heal(REGEN_AMOUNT);
        }
        if (tickCount % DODGE_CHECK_TICKS == 0 && now >= dodgeReadyAt && !isNoAi()) {
            Hollow upon = hollowUpon();
            if (upon != null) {
                tryDodge(upon, now);
            }
        }
        if (!greeted && tickCount % GREET_CHECK_TICKS == 0) {
            greet();
        }
    }

    /** The nearest living Hollow within {@link #DODGE_TRIGGER} blocks that is hunting him, if any. */
    @Nullable
    private Hollow hollowUpon() {
        Hollow nearest = null;
        double best = DODGE_TRIGGER * DODGE_TRIGGER;
        for (Hollow hollow : level().getEntitiesOfClass(Hollow.class, getBoundingBox().inflate(DODGE_TRIGGER),
                hollow -> hollow.isAlive() && hollow.getTarget() == this)) {
            double distance = hollow.distanceToSqr(this);
            if (distance <= best) {
                best = distance;
                nearest = hollow;
            }
        }
        return nearest;
    }

    /**
     * The first time his visitor is close, a word for a fellow Quincy or for a Shinigami; nothing for
     * anyone else. Once a visit.
     */
    private void greet() {
        ServerPlayer visitor = isPermanent() ? null : visitorPlayer();
        if (visitor == null || distanceToSqr(visitor) > GREET_RANGE * GREET_RANGE) {
            return;
        }
        greeted = true;
        if (!SoulLogic.hasSoul(visitor)) {
            return;
        }
        DialogueView view = QuincyNpcLogic.hunterView(SoulLogic.get(visitor), false, false);
        String key = switch (view) {
            case HUNTER_RIVALRY -> "message.bleach.hunter.greet.rivalry";
            case HUNTER_CHALLENGE -> "message.bleach.hunter.greet.challenge";
            default -> null;
        };
        if (key != null) {
            visitor.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Any contest he runs ends with him, on the tally; struck down by a player, it is off. */
    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            BaitEvents.onRivalLeft(this, source.getEntity() instanceof Player ? LeaveCause.HURT_BY_PLAYER : null);
        }
        super.die(source);
    }

    /**
     * Whatever takes him away, any contest he runs hears of it first; a player's blow has told it
     * already (see {@link #hurt}).
     */
    @Override
    protected void onLeave(LeaveCause cause) {
        if (cause != LeaveCause.HURT_BY_PLAYER) {
            BaitEvents.onRivalLeft(this, cause);
        }
    }
}
