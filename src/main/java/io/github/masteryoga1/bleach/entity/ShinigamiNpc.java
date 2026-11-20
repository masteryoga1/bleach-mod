package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.soul.HollowPlayerEvents;
import io.github.masteryoga1.bleach.soul.KidoEvents;
import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.ShinigamiEvents;
import io.github.masteryoga1.bleach.soul.ShinigamiLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SoulState;
import io.github.masteryoga1.bleach.soul.Stat;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Shinigami who talks: dressed in the Shihakushō with a blade at the hip, visible only to the
 * spiritually aware. Using one opens a dialogue chosen from the visitor's soul
 * ({@link DialogueView}), whose buttons come back as {@link NpcAction}s.
 */
public abstract class ShinigamiNpc extends PathfinderMob implements DialogueNpc {
    /** How close a visitor must stay for their answer to count, squared: 8 blocks. */
    private static final double TALK_RANGE_SQR = 64.0;
    /** How near the nearest aware player must stand to be told a Shinigami NPC has left. */
    private static final double DEPARTURE_MESSAGE_RANGE = 32.0;
    /** The Zankensoki in the order the exams list them. */
    public static final List<Stat> EXAMINED_ARTS = List.of(Stat.ZANJUTSU, Stat.HAKUDA, Stat.HOHO, Stat.KIDO);

    protected ShinigamiNpc(EntityType<? extends ShinigamiNpc> type, Level level) {
        super(type, level);
        if (!level.isClientSide) {
            // Saved equipment replaces this when the NPC is loaded. No equip sound on the first tick.
            equip(EquipmentSlot.CHEST, new ItemStack(ModItems.SHIHAKUSHO_KOSODE.get()));
            equip(EquipmentSlot.LEGS, new ItemStack(ModItems.SHIHAKUSHO_HAKAMA.get()));
            equip(EquipmentSlot.FEET, new ItemStack(ModItems.WARAJI.get()));
        }
    }

    private void equip(EquipmentSlot slot, ItemStack stack) {
        setItemSlot(slot, stack);
        setDropChance(slot, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    /** What this NPC says to a visitor with this soul. */
    public abstract DialogueView viewFor(SoulData visitor, long now);

    /** What this NPC says to this visitor: by its soul alone, unless the NPC knows the visitor (see DutyShinigami). */
    public DialogueView viewFor(Player visitor, SoulData data, long now) {
        return viewFor(data, now);
    }

    /** The part of {@code screen.bleach.dialogue.<npc>.<view>} that names this NPC. */
    @Override
    public abstract String dialogueKey();

    /** This NPC's maximum reiryoku, which sets its reiatsu. */
    protected abstract float soulReiryoku();

    /** Whether a Plus this NPC performs Konsō on is sent on from outside Soul Society, where the server sends souls. */
    protected abstract boolean sendsOn();

    /** Called when a visitor opens the dialogue. */
    protected void onDialogueOpened() {
    }

    /** The motes of light and the sound of a Shinigami passing between worlds, where this one stands. */
    public void showPassage() {
        if (level() instanceof ServerLevel serverLevel) {
            showPassage(serverLevel, getX(), getY(), getZ());
        }
    }

    /** The same motes and sound of a passing between worlds at this spot: a Shinigami's, or a soul's it sends home. */
    public static void showPassage(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.8, z, 12, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /**
     * Leaves as a Shinigami does, in motes of light, telling the nearest aware player within 32 blocks with this line,
     * which takes the NPC's name.
     */
    protected void depart(String lineKey) {
        showPassage();
        Player nearest = level().getNearestPlayer(getX(), getY(), getZ(), DEPARTURE_MESSAGE_RANGE,
                entity -> entity instanceof Player player && SoulLogic.hasSoul(player)
                        && SoulLogic.isAware(SoulLogic.get(player)));
        if (nearest != null) {
            nearest.displayClientMessage(Component.translatable(lineKey, getDisplayName()), true);
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !SoulLogic.hasSoul(this)) {
            SoulData soul = getData(SoulAttachments.SOUL);
            SoulLogic.initialize(soul);
            SoulLogic.changeState(soul, SoulState.SHINIGAMI);
            soul.setMaxReiryoku(soulReiryoku());
            soul.setReiryoku(soulReiryoku());
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer visitor && !visitor.isSpectator() && SoulLogic.hasSoul(visitor)
                && SoulLogic.isAware(SoulLogic.get(visitor))) {
            openDialogue(visitor);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void openDialogue(ServerPlayer visitor) {
        SoulData data = SoulLogic.get(visitor);
        long now = level().getGameTime();
        onDialogueOpened();
        getLookControl().setLookAt(visitor, 30.0F, 30.0F);
        List<Integer> required = EXAMINED_ARTS.stream()
                .map(art -> ShinigamiLogic.graduationRequirements().get(art))
                .toList();
        List<NpcAction> unready = unreadyFor(visitor, data, now);
        BleachNetwork.openDialogue(visitor, this, viewFor(visitor, data, now), required, unready, List.of());
    }

    /**
     * What the dialogue offers greyed out: a new Asauchi until the visitor may have one, a lesson given today, and a
     * Jigokuchō to one that carries one or was handed one today.
     */
    public List<NpcAction> unreadyFor(Player visitor, SoulData data, long now) {
        List<NpcAction> unready = new ArrayList<>();
        if (!ShinigamiEvents.mayReceiveAsauchi(visitor, data, now)) {
            unready.add(NpcAction.ASAUCHI);
        }
        if (KidoLogic.lessonTaken(data, KidoLogic.lessonDay(now, level().getDayTime()))) {
            unready.add(NpcAction.LESSON);
        }
        if (!TravelEvents.mayReceiveButterfly(visitor, data, now, level().getDayTime())) {
            unready.add(NpcAction.BUTTERFLY);
        }
        return unready;
    }

    /**
     * Does what a visitor asked for in the dialogue, if they may still ask for it: the NPC is alive,
     * the visitor is close and aware, and what they asked for is offered to a soul like theirs now.
     */
    @Override
    public void perform(Player visitor, NpcAction action) {
        if (!isAlive() || visitor.isSpectator() || distanceToSqr(visitor) > TALK_RANGE_SQR || !SoulLogic.hasSoul(visitor)) {
            return;
        }
        SoulData data = SoulLogic.get(visitor);
        if (!SoulLogic.isAware(data) || !action.allowedIn(viewFor(visitor, data, level().getGameTime()))) {
            return;
        }
        switch (action) {
            case KONSO -> ShinigamiEvents.konso(this, visitor, sendsOn());
            case ENROLL -> ShinigamiEvents.enroll(visitor);
            case GRADUATE -> ShinigamiEvents.graduate(visitor);
            case ASAUCHI -> ShinigamiEvents.reissueAsauchi(visitor);
            case ACCEPT_POWER -> {
                if (this instanceof WoundedShinigami wounded) {
                    ShinigamiEvents.acceptPowerFrom(wounded, visitor);
                }
            }
            case LESSON -> KidoEvents.lesson(visitor, level().getGameTime(), level().getDayTime());
            case PURIFY -> HollowPlayerEvents.purifyByInstructor(this, visitor);
            case BUTTERFLY -> TravelEvents.issueButterfly(visitor, level().getGameTime(), level().getDayTime());
        }
    }

    /** The view every Shinigami takes of Hollows and Pluses; null when the NPC decides for itself. */
    protected static DialogueView commonView(SoulData visitor) {
        return switch (visitor.state()) {
            case HOLLOW, ARRANCAR -> DialogueView.HOLLOW;
            case PLUS -> DialogueView.PLUS;
            default -> null;
        };
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }
}
