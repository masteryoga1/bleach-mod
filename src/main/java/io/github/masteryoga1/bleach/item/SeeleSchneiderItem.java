package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.SeeleSchneiderPfeil;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.soul.QuincyArtsLogic;
import io.github.masteryoga1.bleach.soul.QuincyLogic;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.SpiritSenses;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A Seele Schneider: a silver hilt that, in the main hand of a restored Quincy whose power works and
 * is not too poisoned, forms a blade of reishi for a little reishi and keeps it for less each second. A
 * formed blade cuts like a good sword, a quarter harder against Hollows, and goes when the hand, the
 * power or the reishi fails, or the hilt is dropped. Held in the hand that is not drawing Ginrei
 * Kojaku it is fired as an arrow (see {@link SeeleSchneiderPfeil}); sneak-used on the top of a block it
 * is planted there, point down, for Sprenger. The forming rules are {@link QuincyArtsLogic}'s.
 */
public class SeeleSchneiderItem extends Item {
    public SeeleSchneiderItem(Item.Properties properties) {
        super(properties);
    }

    /** Whether the stack is a Seele Schneider, formed or not. */
    public static boolean isSeele(ItemStack stack) {
        return stack.getItem() instanceof SeeleSchneiderItem;
    }

    /** Whether the stack is a Seele Schneider with its blade formed. */
    public static boolean isFormed(@Nullable ItemStack stack) {
        return stack != null && stack.getOrDefault(ModComponents.SEELE_FORMED.get(), false);
    }

    /** Gives the hilt its blade: marked formed, with the blade's stats. */
    public static void form(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        stack.set(ModComponents.SEELE_FORMED.get(), true);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, formedAttributes());
    }

    /**
     * Takes the blade away: the mark goes, and setting the item's own stats removes the stack's entry,
     * so the bare hilt carries no stats of its own.
     */
    public static void unform(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        stack.remove(ModComponents.SEELE_FORMED.get());
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS));
    }

    /**
     * A formed blade's stats in the main hand: total attack damage and attacks per second from the
     * config, written as a sword's are (on top of a player's 1 damage and 4 attacks a second).
     */
    public static ItemAttributeModifiers formedAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        BleachConfig.get(BleachConfig.SEELE_DAMAGE) - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        BleachConfig.get(BleachConfig.SEELE_ATTACK_SPEED) - 4.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /**
     * Each tick, on the server, for a player carrying it: in the main hand of a soul that can form it
     * (and not a spectator), an unformed blade forms if the reishi is there and a formed one pays its
     * upkeep each second, falling away when that runs dry; anywhere else, or for a soul that cannot,
     * a formed blade falls away.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        boolean formed = isFormed(stack);
        if (player.getMainHandItem() != stack || player.isSpectator() || !SoulLogic.hasSoul(player)
                || !QuincyArtsLogic.canFormSeele(SoulLogic.get(player))) {
            if (formed) {
                unform(stack);
            }
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (!formed) {
            if (QuincyLogic.trySpendReishi(data, (float) BleachConfig.get(BleachConfig.SEELE_FORM_COST))) {
                form(stack);
                SpiritSenses.play(player, ModSounds.SEELE_SCHNEIDER_FORM.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
                if (player instanceof ServerPlayer serverPlayer) {
                    Milestones.grant(serverPlayer, Milestones.SEELE_SCHNEIDER);
                }
            }
            return;
        }
        if (player.tickCount % 20 == 0
                && !QuincyLogic.drainReishi(data, (float) BleachConfig.get(BleachConfig.SEELE_UPKEEP_PER_SECOND))) {
            unform(stack);
        }
    }

    /**
     * A formed blade falls away on the ground, however it got there. The bare copy replaces the stack
     * so watching clients see the change.
     */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide() && isFormed(stack)) {
            ItemStack bare = stack.copy();
            unform(bare);
            entity.setItem(bare);
        }
        return false;
    }

    /** A formed blade's blow cuts a Hollow, or anything like one, harder. */
    @Override
    public float getAttackDamageBonus(Entity target, float baseDamage, DamageSource source) {
        if (isFormed(source.getWeaponItem()) && target instanceof LivingEntity living && QuincyLogic.isHollowLike(living)) {
            return baseDamage * (float) (BleachConfig.get(BleachConfig.SEELE_HOLLOW_MULTIPLIER) - 1.0);
        }
        return 0.0F;
    }

    /**
     * Sneak-used on the top of a block by a restored Quincy whose power works: plants a blade there,
     * point down, using one up. Anything else is left to the block and the item's other uses. The
     * client agrees on the synced soul and the blocks it sees; the server plants.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        SoulData data = SoulLogic.get(player);
        if (!QuincyArtsLogic.isRestored(data) || !QuincyLogic.canUsePowers(data)) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos ground = context.getClickedPos();
        if (!SeeleSchneiderPfeil.canPlantAt(level, ground)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            if (SeeleSchneiderPfeil.plant(server, player, ground) == null) {
                return InteractionResult.FAIL;
            }
            context.getItemInHand().consume(1, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
