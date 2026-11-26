package io.github.masteryoga1.bleach.item;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.soul.JinzenLogic;
import io.github.masteryoga1.bleach.soul.ShikaiEvents;
import io.github.masteryoga1.bleach.soul.SoulData;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.ZanpakutoLogic;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * The Asauchi and the zanpakutō it becomes: one item whose {@link ZanpakutoBlade} component says
 * whose blade it is, which spirit lives in it and whether it is released. The spirit's forms set the
 * model (through {@code custom_model_data}) and the attack stats; the plain Asauchi keeps the item's
 * own. Sneaking and holding use with it in hand sits the wielder in Jinzen (see {@link JinzenLogic}),
 * and a released blade that is not a constant release seals as soon as it lies on the ground.
 */
public final class ZanpakutoItem extends SwordItem {
    /** The extra reach a form adds, on the item in the main hand. */
    public static final ResourceLocation REACH_ID = BleachMod.id("zanpakuto_reach");
    /** How long use may be held: far longer than any Jinzen, which ends itself. */
    public static final int USE_TICKS = 72000;

    public ZanpakutoItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    // ---- The blade on a stack ----

    /** The stack's blade: whose it is, its spirit and whether it is released. Unbound if it has none. */
    public static ZanpakutoBlade blade(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ZANPAKUTO_BLADE.get(), ZanpakutoBlade.UNBOUND);
    }

    /** Whether the blade belongs to someone: it has learned its name for them. */
    public static boolean isBound(ItemStack stack) {
        return blade(stack).isBound();
    }

    public static boolean isOwnedBy(ItemStack stack, UUID owner) {
        return blade(stack).isOwnedBy(owner);
    }

    /**
     * Binds the blade to its owner's spirit: it takes the spirit's name in its colour (an anvil's name
     * goes), and the sealed form, or the Shikai form if {@code released}.
     */
    public static void bind(ItemStack stack, Player owner, ResourceLocation id, ZanpakutoSpirit spirit, boolean released) {
        if (stack.isEmpty()) {
            return;
        }
        stack.set(ModComponents.ZANPAKUTO_BLADE.get(), new ZanpakutoBlade(Optional.of(owner.getUUID()),
                owner.getGameProfile().getName(), Optional.of(id), released ? ZanpakutoStage.SHIKAI : ZanpakutoStage.SEALED,
                spirit.isConstant(), spirit.colour()));
        stack.set(DataComponents.ITEM_NAME, spirit.name().copy().withColor(spirit.colour()));
        stack.remove(DataComponents.CUSTOM_NAME);
        applyForm(stack, released ? spirit.shikai() : spirit.sealed());
    }

    /** Makes the blade a nameless Asauchi again: its owner, name, model and stats all go back to the item's own. */
    public static void unbind(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        stack.remove(ModComponents.ZANPAKUTO_BLADE.get());
        stack.remove(DataComponents.ITEM_NAME);
        stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        restoreAttributes(stack);
    }

    /**
     * Gives the blade a form's model and stats. The plain Asauchi's form puts the item's own stats
     * back, which clears them from the stack, rather than writing stats that only nearly match.
     */
    public static void applyForm(ItemStack stack, ZanpakutoSpirit.Form form) {
        if (stack.isEmpty()) {
            return;
        }
        if (form.model() == 0) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(form.model()));
        }
        if (form.damage() == 6.0F && form.speed() == 1.6F && form.reach() == 0.0F) {
            restoreAttributes(stack);
        } else {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes(form));
        }
    }

    /**
     * Releases or seals the blade: the spirit's Shikai or sealed form, and the blade marked to match. A blade released
     * already keeps its stage and that stage's form, so no release steps a Bankai down to Shikai.
     */
    public static void setReleased(ItemStack stack, ZanpakutoSpirit spirit, boolean released) {
        ZanpakutoBlade blade = blade(stack);
        ZanpakutoStage kept = blade.released() ? blade.stage() : ZanpakutoStage.SHIKAI;
        setStage(stack, spirit, released ? kept : ZanpakutoStage.SEALED);
    }

    /** Puts the blade at this stage, in its spirit's form for it (see {@link ZanpakutoLogic#form}). */
    public static void setStage(ItemStack stack, ZanpakutoSpirit spirit, ZanpakutoStage stage) {
        if (stack.isEmpty()) {
            return;
        }
        applyForm(stack, ZanpakutoLogic.form(spirit, stage));
        stack.set(ModComponents.ZANPAKUTO_BLADE.get(), blade(stack).withStage(stage));
    }

    /**
     * A form's stats in the main hand: total attack damage and attacks per second, as a sword's are
     * written (on top of a player's 1 damage and 4 attacks a second), and any extra reach.
     */
    public static ItemAttributeModifiers attributes(ZanpakutoSpirit.Form form) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, form.damage() - 1.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, (double) form.speed() - 4.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (form.reach() > 0.0F) {
            builder.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, form.reach(),
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }

    /** Setting the item's own value removes the stack's entry, so the stack carries no stats of its own. */
    private static void restoreAttributes(ItemStack stack) {
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS));
    }

    // ---- Jinzen ----

    /**
     * Sneak and use with the blade in the main hand: Jinzen, if nothing stands in the way. Both sides
     * run the same check on the synced soul, so the client only starts using the blade when the server
     * will too; the server says why not. A full off hand is the exception: it has its turn next, and
     * the client says why only if the off hand then does nothing either (see {@code MinecraftMixin}).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        SoulData data = SoulLogic.get(player);
        long now = level.getGameTime();
        String refusal = JinzenLogic.refusal(player, data, stack, now);
        if (refusal != null) {
            if (refusal.equals("off_hand")) {
                if (level.isClientSide()) {
                    data.noteOffHandRefused();
                }
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.bleach.jinzen." + refusal)
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide()) {
            JinzenLogic.begin(player, data, now);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    /** The blade held across the body. Only the pose: a zanpakutō cannot block like a shield. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide() && entity instanceof Player player) {
            JinzenLogic.tick(player, SoulLogic.get(player), stack, USE_TICKS - remaining, level.getGameTime());
        }
    }

    /** Letting go, being hurt or closing the Jinzen screen all stop the use, and so end Jinzen. */
    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (!entity.level().isClientSide() && entity instanceof Player player) {
            JinzenLogic.end(player, SoulLogic.get(player));
        }
    }

    // ---- On the ground and in the tooltip ----

    /**
     * A released blade that can seal does so on the ground, however it got there: tossed, dropped on
     * death or dispensed, and a constant release in Bankai falls back to Shikai. The changed copy
     * replaces the stack so watching clients see the change.
     */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide()) {
            ZanpakutoBlade blade = blade(stack);
            if (ShikaiEvents.lapses(blade)) {
                ItemStack lapsed = stack.copy();
                ShikaiEvents.lapseStack(lapsed, entity.level());
                if (ShikaiEvents.lapses(blade(lapsed))) {
                    // Never left as it was, or this would run, and resend the stack, every tick.
                    lapsed.set(ModComponents.ZANPAKUTO_BLADE.get(), blade(lapsed).withStage(
                            blade.constant() ? ZanpakutoStage.SHIKAI : ZanpakutoStage.SEALED));
                }
                entity.setItem(lapsed);
            }
        }
        return false;
    }

    /**
     * A blade in Bankai carried by anyone but its owner in Bankai falls back at once: to Shikai if it is a constant
     * release, sealed otherwise. Only a player's own inventory ticks its stacks, so one kept in a chest, a frame or a
     * mob's hand falls back when a player next carries it; its owner's own Bankai is kept in line by the upkeep.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ZanpakutoBlade blade = blade(stack);
        if (level.isClientSide() || blade.stage() != ZanpakutoStage.BANKAI) {
            return;
        }
        if (entity instanceof Player player && blade.isOwnedBy(player.getUUID()) && SoulLogic.get(player).isBankaiActive()) {
            return;
        }
        ShikaiEvents.lapseStack(stack, level);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        ZanpakutoBlade blade = blade(stack);
        if (!blade.isBound()) {
            lines.add(Component.translatable("item.bleach.asauchi.nameless").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        lines.add(Component.translatable("item.bleach.zanpakuto.bound", blade.ownerName()).withStyle(ChatFormatting.GRAY));
        if (blade.released()) {
            String state = blade.stage() == ZanpakutoStage.BANKAI ? "item.bleach.zanpakuto.bankai"
                    : blade.constant() ? "item.bleach.zanpakuto.constant" : "item.bleach.zanpakuto.released";
            lines.add(Component.translatable(state).withColor(blade.colour()));
        }
    }
}
