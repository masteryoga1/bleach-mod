package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Kisuke Urahara, who keeps the Urahara Shop: a Shinigami in a gigai that every player sees, the unaware living too,
 * and that hides him from Pesquisa, so he is in neither spirit tag. Only {@code /kill} and the void harm him; nothing
 * moves him, he never despawns, no creature hunts him, and he talks to each soul by what it is, sells it what it can
 * use at his counter ({@link UraharaCounter}) and opens it his gates in the room under the shop ({@link UraharaGates}).
 */
public class KisukeUrahara extends Mob implements DialogueNpc {
    /** A template's keeper carries this: he takes his UUID from where he stands, so a second set there is refused. */
    private static final String SPOT = "Spot";
    /** How near a visitor must stand to talk, squared, as the dialogue screen keeps it open. */
    private static final double TALK_RANGE_SQR = 64.0;
    /** Each view's asides, one shown under his words at each talk. */
    private static final int ASIDES = 2;
    /** How far his body turns back a tick towards where he was set, as fast as his head follows it. */
    private static final float SETTLE_DEGREES = 10.0F;

    /** Souls that asked him for a gate, each waiting until it comes down into the room; never saved. */
    final Map<UUID, UraharaGates.Ask> asks = new HashMap<>();

    public KisukeUrahara(EntityType<? extends KisukeUrahara> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    /** A mob's own, unmoved by a blow or a blast. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    /** He only turns to a player near him: no step, and no idle glance about. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 1.0F));
    }

    /** With no player to look at, his body turns back to where he was set and his head follows it there. */
    @Override
    protected void customServerAiStep() {
        if (!getLookControl().isLookingAtTarget()) {
            yBodyRot = Mth.approachDegrees(yBodyRot, getYRot(), SETTLE_DEGREES);
        }
    }

    /** Each tick on the server, he opens the gate a soul asked for once it has come down into the room, even with no AI. */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && !asks.isEmpty()) {
            UraharaGates.arrivals(this, server);
        }
    }

    /** Set down, by a template's turn, a command or an egg, his body and head face as he does, with no tick. */
    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        super.moveTo(x, y, z, yRot, xRot);
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
    }

    /**
     * What he says to this soul. A Hollow, an Arrancar and a Plus are what they are; then a Visored, a student, an
     * awakened Quincy, a Shinigami (a Substitute too), a Rukongai soul, and a living Human, aware or not.
     */
    public static DialogueView viewFor(SoulData soul) {
        return switch (soul.state()) {
            case HOLLOW -> DialogueView.SHOP_HOLLOW;
            case ARRANCAR -> DialogueView.SHOP_ARRANCAR;
            case PLUS -> DialogueView.SHOP_PLUS;
            default -> VisoredLogic.isVisored(soul) ? DialogueView.SHOP_VISORED
                    : ShinigamiLogic.isStudent(soul) ? DialogueView.SHOP_STUDENT
                    : QuincyLogic.isAwakened(soul) ? DialogueView.SHOP_QUINCY
                    : ShinigamiLogic.hasShinigamiPower(soul) ? DialogueView.SHOP_SHINIGAMI
                    : soul.state() == SoulState.RUKONGAI ? DialogueView.SHOP_SOUL
                    : SoulLogic.isAware(soul) ? DialogueView.SHOP_AWARE : DialogueView.SHOP_STRANGER;
        };
    }

    /** In his gigai anyone sees him, so a visitor need not be aware to talk, only near, with a soul, and in the game. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer visitor && !visitor.isSpectator() && SoulLogic.hasSoul(visitor)
                && distanceToSqr(visitor) <= TALK_RANGE_SQR) {
            SoulData soul = SoulLogic.get(visitor);
            DialogueView view = viewFor(soul);
            getLookControl().setLookAt(visitor, 30.0F, 30.0F);
            Component aside = Component.translatable("screen.bleach.dialogue." + dialogueKey() + "." + view.key()
                    + ".aside" + (1 + getRandom().nextInt(ASIDES)));
            List<NpcAction> unready = new ArrayList<>(unreadyFor(soul, level().getGameTime()));
            unready.addAll(UraharaGates.spent(visitor, level()));
            BleachNetwork.openDialogue(visitor, this, view, List.of(), unready, List.of(aside));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * His trade button greyed for a soul whose view trades but that he sells nothing now: one he would sell only bait,
     * while the server keeps bait turned off.
     */
    public static List<NpcAction> unreadyFor(SoulData soul, long now) {
        return NpcAction.TRADE.allowedIn(viewFor(soul)) && UraharaCounter.waresFor(soul, now).isEmpty()
                ? List.of(NpcAction.TRADE) : List.of();
    }

    @Override
    public String dialogueKey() {
        return "urahara";
    }

    /**
     * Opens his wares, or takes an ask for one of his gates ({@link UraharaGates}), for a soul that pressed that button:
     * he checks again that he serves it and that its view offers it, so a client cannot ask for what it was never offered.
     */
    @Override
    public void perform(Player visitor, NpcAction action) {
        if (!(visitor instanceof ServerPlayer soul) || !serves(soul) || !action.allowedIn(viewFor(SoulLogic.get(soul)))) {
            return;
        }
        if (action == NpcAction.TRADE) {
            UraharaCounter.open(this, soul);
        } else if (action == NpcAction.SENKAIMON || action == NpcAction.GARGANTA) {
            UraharaGates.ask(this, soul.serverLevel(), soul, action);
        }
    }

    /**
     * Whether he serves this soul now: he stands, and it is alive, in the game, in his world, within 8 blocks and has a
     * soul. A trade lasts only while this holds.
     */
    boolean serves(Player visitor) {
        return isAlive() && visitor.isAlive() && !visitor.isSpectator() && visitor.level() == level()
                && distanceToSqr(visitor) <= TALK_RANGE_SQR && SoulLogic.hasSoul(visitor);
    }

    /** Only {@code /kill} and the void reach him: no blow, a creative player's too, and no fire, fall, blast or art. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return isRemoved() || !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }

    /** No creature takes him for a foe; the invulnerable flag keeps the Warden off too. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    /** The UUID a template's keeper takes standing at this block of this world. */
    public static UUID spotId(Level level, BlockPos at) {
        return UUID.nameUUIDFromBytes((level.dimension().location() + "@" + at.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    /** Data without an "Invulnerable" entry keeps him invulnerable; a template's keeper takes his UUID from his spot. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("Invulnerable")) {
            setInvulnerable(true);
        }
        if (tag.getBoolean(SPOT)) {
            setUUID(spotId(level(), blockPosition()));
        }
    }

    /** He never moves: no step, fall, knockback, blast, push or current carries him, though he pushes back. */
    @Override
    public void travel(Vec3 input) {
        setDeltaMovement(Vec3.ZERO);
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
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    /** Nothing carries him off: not a boat or a cart, a lead, Negación's light or a Caja, nor a portal. */
    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
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
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }
}
