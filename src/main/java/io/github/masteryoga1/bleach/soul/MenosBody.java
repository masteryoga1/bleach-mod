package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The body of a Hollow player that has become a Menos Grande: its size, health, speed, reach, step,
 * steadiness and fall, all scaled by its form (see {@link HollowTier}), and the guards a giant needs.
 * The size is {@link Attributes#SCALE}, which vanilla already applies to the box, the eyes, the camera
 * and the drawn body; it grows a little each tick, only into room, and shrinks to fit when the body is
 * pushed into blocks. Everything else vanilla leaves absolute, so it is scaled here. The health is a
 * saved modifier, as vanilla loads the attributes before the health and clamps it to the maximum; the
 * rest are transient, put back by {@link HollowBody#reconcile} on logging in and respawning. Server side,
 * except the lookup depth, which each side keeps for its own entity lookups (see EntitySectionStorageMixin).
 */
public final class MenosBody {
    public static final ResourceLocation HEALTH_ID = BleachMod.id("menos_health");
    public static final ResourceLocation SPEED_ID = BleachMod.id("menos_speed");
    public static final ResourceLocation REACH_ID = BleachMod.id("menos_reach");
    public static final ResourceLocation STEP_ID = BleachMod.id("menos_step");
    public static final ResourceLocation STEADY_ID = BleachMod.id("menos_steady");
    public static final ResourceLocation FALL_ID = BleachMod.id("menos_fall");
    public static final ResourceLocation SCALE_ID = BleachMod.id("menos_scale");

    /** Scale a Menos grows by each tick while there is room: five seconds from a person's size to a full Gillian. */
    public static final double GROW_PER_TICK = 0.05;
    /** Scale a Menos shrinks by each tick while its body is inside blocks. */
    public static final double SQUEEZE_PER_TICK = 0.25;
    /** Above this a player is a giant (its box over 3.96 blocks tall), too big for beds, mounts and portals. */
    public static final double GIANT_SCALE = 2.2;
    /** How far a person's eyes, height and half-width are from the feet and middle, per unit of scale. */
    public static final double EYE_PER_SCALE = 1.62;
    public static final double HEIGHT_PER_SCALE = 1.8;
    public static final double HALF_WIDTH_PER_SCALE = 0.3;
    /** Knockback resistance per unit of scale above a person's, so a full Gillian shrugs off most of a blow. */
    public static final double STEADY_PER_SCALE = 0.12;
    /** Ticks between two "no room" messages while the body waits for room to grow. */
    public static final int NO_ROOM_MESSAGE_TICKS = 100;
    /** How far below a query box vanilla's entity lookups look for the feet of what they find. */
    public static final double VANILLA_LOOKUP_MARGIN = 4.0;
    /** Ticks between two measures of the tallest giant online. */
    public static final int DEPTH_REFRESH_TICKS = 20;

    /** Knockback resistance is a share: at most all of it. */
    private static final double MOST_STEADY = 1.0;
    /** The box is tested a hair inside its faces, so standing on the ground or against a wall is not inside them. */
    private static final double FIT_MARGIN = 1.0E-5;
    private static final double SQUEEZE_MARGIN = 1.0E-3;
    /** The portal cooldown a giant is held at, which keeps any portal from starting to take it. */
    private static final int GIANT_PORTAL_COOLDOWN = 2;

    // The extra depth for the entity lookups, one for each side, as a single player game runs both in one
    // process: each is the tallest giant's height above the vanilla margin, or 0 with none online.
    private static volatile double serverDepth;
    private static volatile double clientDepth;

    private MenosBody() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(MenosBody::onMount);
        bus.addListener(MenosBody::onSleep);
        bus.addListener(MenosBody::onServerTick);
    }

    // ---- The body ----

    /**
     * Puts the Menos modifiers right for the soul's form and the body's present size, writing each only
     * when it changes: the health (saved), the speed and the step by form, and the reach, steadiness and
     * fall by size. For a plain Hollow at a person's size all of them are off. Server side, from
     * {@link HollowBody#reconcile}, after the Hollow body's own modifiers.
     */
    static void reconcile(Player player, SoulData data) {
        HollowTier tier = HollowTier.of(data);
        double grown = grownScale(player) - 1.0;
        HollowBody.setModifier(player, Attributes.MAX_HEALTH, HEALTH_ID, tier.healthBonus(),
                AttributeModifier.Operation.ADD_VALUE, true);
        HollowBody.setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, tier.speedMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        HollowBody.setModifier(player, Attributes.STEP_HEIGHT, STEP_ID, tier.stepBonus(),
                AttributeModifier.Operation.ADD_VALUE);
        // The eyes rise with the size, so the reach grows with them and still meets the ground at the feet.
        double reach = EYE_PER_SCALE * grown;
        HollowBody.setModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, reach,
                AttributeModifier.Operation.ADD_VALUE);
        HollowBody.setModifier(player, Attributes.BLOCK_INTERACTION_RANGE, REACH_ID, reach,
                AttributeModifier.Operation.ADD_VALUE);
        // Explosions push through their own resistance, so both are held.
        double steady = Math.min(MOST_STEADY, STEADY_PER_SCALE * grown);
        HollowBody.setModifier(player, Attributes.KNOCKBACK_RESISTANCE, STEADY_ID, steady,
                AttributeModifier.Operation.ADD_VALUE);
        HollowBody.setModifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, STEADY_ID, steady,
                AttributeModifier.Operation.ADD_VALUE);
        // A fall as many times the height as the body is a person's, as a person falls three blocks unharmed.
        HollowBody.setModifier(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID, HEIGHT_PER_SCALE * grown,
                AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * One tick of the size, from {@link HollowBody#tickBody}, before {@link #reconcile}: a body above its
     * form's size shrinks to it at once; one pushed into blocks shrinks a step at a time until it fits; a
     * living one below its size grows a step if the standing body at the next size would fit, and is told
     * now and then that there is no room. It never moves the player. Then a giant is held off mounts and
     * portals. Not for spectators, who pass through blocks.
     */
    static void tick(Player player, SoulData data, long now) {
        if (player.level().isClientSide() || player.isSpectator()) {
            return;
        }
        double target = targetScale(data);
        double scale = grownScale(player);
        if (scale > target) {
            setScale(player, target);
        } else if (scale > 1.0 && !roomFor(player, player.getBoundingBox().deflate(SQUEEZE_MARGIN))) {
            setScale(player, Math.max(1.0, scale - SQUEEZE_PER_TICK));
        } else if (player.isAlive() && scale < target) {
            double next = Math.min(target, scale + GROW_PER_TICK);
            if (roomFor(player, standingBox(player, next))) {
                setScale(player, next);
            } else if (player.tickCount % NO_ROOM_MESSAGE_TICKS == 0 && player instanceof ServerPlayer server) {
                server.displayClientMessage(Component.translatable("message.bleach.menos.no_room")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (isGiant(player)) {
            if (player.isPassenger()) {
                player.stopRiding();
            }
            // On cooldown, standing in a portal only renews the cooldown, so no portal starts to take it and
            // no exit portal is ever built on the far side for a body that could never fit through.
            player.setPortalCooldown(Math.max(player.getPortalCooldown(), GIANT_PORTAL_COOLDOWN));
        }
    }

    /** Takes off every Menos modifier, the size too, so a soul that stops being a Hollow is a person's size at once. */
    static void remove(Player player) {
        removeModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        removeModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
        removeModifier(player, Attributes.BLOCK_INTERACTION_RANGE, REACH_ID);
        removeModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
        removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, STEADY_ID);
        removeModifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, STEADY_ID);
        removeModifier(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID);
        if (removeModifier(player, Attributes.SCALE, SCALE_ID)) {
            player.refreshDimensions();
            player.resetFallDistance();
        }
    }

    /** The size this soul's form stands at, as a multiple of a person's. */
    public static double targetScale(SoulData data) {
        return HollowTier.of(data).scale();
    }

    /** The size the Menos body has grown to: a person's, and whatever the Menos modifier adds. */
    public static double grownScale(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.SCALE);
        AttributeModifier grown = instance == null ? null : instance.getModifier(SCALE_ID);
        return grown == null ? 1.0 : 1.0 + grown.amount();
    }

    /** The player's box standing up at this size, from where its feet are now, a hair inside its faces. */
    public static AABB standingBox(Player player, double scale) {
        double halfWidth = HALF_WIDTH_PER_SCALE * scale;
        return new AABB(player.getX() - halfWidth, player.getY(), player.getZ() - halfWidth,
                player.getX() + halfWidth, player.getY() + HEIGHT_PER_SCALE * scale, player.getZ() + halfWidth)
                .deflate(FIT_MARGIN);
    }

    /** Whether this player is a giant: too big for beds, mounts and portals. */
    public static boolean isGiant(Player player) {
        return player.getScale() > GIANT_SCALE;
    }

    /** Whether this player is a mindless Gillian, refused items, placing and dealing with creatures. Both sides. */
    public static boolean mindless(Player player) {
        return BleachConfig.get(BleachConfig.GILLIAN_MINDLESS) && SoulLogic.hasSoul(player)
                && HollowTier.of(SoulLogic.get(player)) == HollowTier.GILLIAN;
    }

    /**
     * How much further below a query box the entity lookups must look, on top of vanilla's margin, to
     * find the feet of the tallest giant player online; 0 while there is none. The larger of the two
     * sides', as both may run in one process.
     */
    public static double lookupDepth() {
        return Math.max(serverDepth, clientDepth);
    }

    /** Measures the tallest giant among these players, for one side's lookup depth: every so often, on each side. */
    public static void noteGiants(Iterable<? extends Player> players, boolean client) {
        double deepest = 0.0;
        for (Player player : players) {
            deepest = Math.max(deepest, depthFor(player.getScale()));
        }
        if (client) {
            clientDepth = deepest;
        } else {
            serverDepth = deepest;
        }
    }

    // ---- Events ----

    /** A giant cannot ride: no mount is big enough. Getting off is never refused. */
    private static void onMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof Player player && isGiant(player)) {
            event.setCanceled(true);
            tooBig(player);
        }
    }

    /** A giant cannot sleep in a bed, which would leave it lying in a person's space and waking inside the walls. */
    private static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (isGiant(player)) {
            // A problem with no message of its own, so the player hears only ours.
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            tooBig(player);
        }
    }

    /** Measures the tallest giant online every so often, so the lookups go deeper only while one is. */
    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % DEPTH_REFRESH_TICKS == 0) {
            noteGiants(server.getPlayerList().getPlayers(), false);
        }
    }

    // ---- Helpers ----

    /**
     * Sets the size and rebuilds the box at once, which vanilla would only do at the end of the player's
     * next tick (and never for a player that does not tick), so the next test of room reads the new box.
     * Rebuilding the box never moves a player. A smaller body forgets its fall, so a shrink in mid-air does
     * not land a fall its new size is not safe from; a larger one deepens the lookups at once.
     */
    private static void setScale(Player player, double scale) {
        double before = grownScale(player);
        HollowBody.setModifier(player, Attributes.SCALE, SCALE_ID, scale - 1.0, AttributeModifier.Operation.ADD_VALUE);
        player.refreshDimensions();
        if (scale < before) {
            player.resetFallDistance();
        } else {
            serverDepth = Math.max(serverDepth, depthFor(scale));
        }
    }

    /**
     * Whether this box is clear of blocks and inside the world border. Other creatures are left out on
     * purpose: a boat or a minecart beside a giant would otherwise squeeze it small or keep it from growing.
     */
    private static boolean roomFor(Player player, AABB box) {
        return player.level().noBlockCollision(player, box) && player.level().getWorldBorder().isWithinBounds(box);
    }

    /** How far below vanilla's margin a lookup must reach for the feet of a player this size. */
    private static double depthFor(double scale) {
        return Math.max(0.0, HEIGHT_PER_SCALE * scale - VANILLA_LOOKUP_MARGIN);
    }

    /** Tells a giant, not too often, that it is too big for what it tried. */
    private static void tooBig(Player player) {
        if (!player.level().isClientSide() && SoulLogic.hasSoul(player)) {
            HollowBody.actionBar(player, SoulLogic.get(player), Component.translatable("message.bleach.menos.too_big")
                    .withStyle(ChatFormatting.GRAY), player.level().getGameTime());
        }
    }

    /** @return whether the modifier was on */
    private static boolean removeModifier(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null && instance.removeModifier(id);
    }
}
