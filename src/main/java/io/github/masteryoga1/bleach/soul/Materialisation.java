package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.entity.MaterialisedSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Materialisation (gushōka): a wielder's zanpakutō spirit drawn out of the inner world into this one, as the
 * {@link MaterialisedSpirit} its file's {@code bankai.entity} names, one at a time ({@link #callOut}), before it and
 * facing it. A file whose type is none or no spirit's is refused at the call, so its Shikai still works.
 *
 * <p>Every end sends the spirit home with nothing kept ({@link #sendHome}), seen on the spirit's tick, which goes on as
 * long as its wielder is near: its wielder dies, is removed (logged out), is in another world (at once, on the event),
 * strays past {@link #LEAVE_DISTANCE}, loses the power or the name, binds another spirit or faces its inner Hollow; a
 * spirit beaten to its floor goes home too. It comes and goes in its colour's dust, with a line to its wielder.
 * Reaching Bankai or having it revoked ends nothing. A spirit that leaves its world any other way (its chunk unloaded,
 * Peaceful, a death) is forgotten as it leaves, so its wielder may call again at once. The spirits out are held only in
 * memory, by their wielders' UUIDs, on the server thread, and none is ever saved.
 */
public final class Materialisation {
    /** How far before its wielder a spirit steps out, in blocks, as the inner Hollow does. */
    public static final int CALL_DISTANCE = VisoredLogic.SPAWN_DISTANCE;
    /** How far its wielder may go from a spirit before it goes home, in blocks: its follow range, 16 past a mob's 16. */
    public static final double LEAVE_DISTANCE = 32.0;
    private static final int SPOT_TRIES = 8, SPOT_DROP_BLOCKS = 2, BURST = 30;
    private static final float SPOT_TURN_DEGREES = 45.0F;
    private static final double BURST_SPREAD = 0.4, BURST_SPREAD_UP = 1.0;
    private static final String KEY = "commands.bleach.zanpakuto.bankai.spirit";
    private static final String HOME = "message.bleach.spirit.home", BEATEN = "message.bleach.spirit.beaten";

    /** Every spirit out, by its wielder's UUID, each only while it is in its world. Server thread only. */
    private static final Map<UUID, MaterialisedSpirit> CALLED = new HashMap<>();

    private Materialisation() {
    }

    /** What came of a call, each but {@link #CALLED} with the reason a command says. */
    public enum Call {
        CALLED, DEAD, ALREADY, NO_POWER, NAMELESS, NO_SPIRIT, NO_TYPE, BUSY, PEACEFUL, NO_ROOM;

        public String key() {
            return KEY + "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Another world sends a spirit home at once, any leaving forgets it, and a stopped server forgets every spirit. */
    public static void register(IEventBus bus) {
        bus.addListener(Materialisation::onChangedDimension);
        bus.addListener(Materialisation::onLeave);
        bus.addListener(Materialisation::onServerStopped);
    }

    /** Whether this soul may have its spirit out: it may hold a Bankai, and its power is neither lent nor seized. */
    public static boolean mayCall(SoulData data, long now) {
        return BankaiLogic.mayHold(data) && hasPower(data, now);
    }

    /** Whether this soul's power is its own now: it can wield a zanpakutō, and its power is neither lent nor seized. */
    private static boolean hasPower(SoulData data, long now) {
        return ZanpakutoLogic.canWield(data) && !ShinigamiLogic.isPowerLent(data, now) && !VisoredLogic.takenOver(data);
    }

    /**
     * Draws the wielder's spirit out, or says why not, in this order: the wielder dead or gone, one out already, no
     * power to (none, lent or seized), the name not known, a spirit this world lacks or one with no Bankai, a type that
     * is none or no spirit's, the inner Hollow faced now, Peaceful, and no room. The power and the name are what
     * {@link #mayCall} asks, so a spirit drawn out is not sent home on its first tick for them. On the client nothing
     * happens: {@link Call#NO_SPIRIT}.
     */
    public static Call callOut(Player wielder) {
        return callOut(wielder, false);
    }

    /**
     * Draws the wielder's spirit out as {@link #callOut(Player)} does, but when {@code replacing} a spirit out already is
     * no refusal: it stays out until the new one has room, then goes home quietly just before the new one comes.
     */
    public static Call callOut(Player wielder, boolean replacing) {
        return draw(wielder, replacing, true);
    }

    /**
     * The first of the call's refusals from the wielder dead to Peaceful, or null when none holds; when
     * {@code replacing}, a spirit out already is none. Nothing joins the world: the body made to learn its type is dropped.
     */
    @Nullable
    public static Call refusal(Player wielder, boolean replacing) {
        Call call = draw(wielder, replacing, false);
        return call == Call.CALLED ? null : call;
    }

    /**
     * The call, its refusals written once: when {@code adding}, the spirit drawn out or no room for it; otherwise
     * CALLED once nothing from the wielder dead to Peaceful refuses, before its spot is sought.
     */
    private static Call draw(Player wielder, boolean replacing, boolean adding) {
        if (!(wielder.level() instanceof ServerLevel level)) {
            return Call.NO_SPIRIT;
        }
        if (!wielder.isAlive()) {
            return Call.DEAD;
        }
        if (!replacing && of(wielder.getUUID()) != null) {
            return Call.ALREADY;
        }
        SoulData data = SoulLogic.get(wielder);
        if (!hasPower(data, level.getGameTime())) {
            return Call.NO_POWER;
        }
        if (!data.zanpakuto().named()) {
            return Call.NAMELESS;
        }
        Optional<ResourceLocation> id = data.zanpakuto().spirit();
        Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(level, id);
        if (spirit.isEmpty() || spirit.get().bankai().isEmpty()) {
            return Call.NO_SPIRIT;
        }
        MaterialisedSpirit body = create(level, spirit.get());
        if (body == null) {
            return Call.NO_TYPE;
        }
        if (VisoredEvents.isDuelling(wielder)) {
            return Call.BUSY;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return Call.PEACEFUL;
        }
        if (!adding) {
            return Call.CALLED;
        }
        Vec3 spot = spot(level, wielder, body.getType());
        if (spot == null) {
            return Call.NO_ROOM;
        }
        body.setUp(wielder, id.get(), spirit.get());
        Vec3 toWielder = wielder.position().subtract(spot);
        float yaw = (float) (Mth.atan2(toWielder.z, toWielder.x) * Mth.RAD_TO_DEG) - 90.0F;
        body.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        MaterialisedSpirit out = of(wielder.getUUID());
        if (out != null) {
            // Replaced, it goes with no line of its own: the new one's says what came.
            burst(level, out);
            out.discard();
        }
        // Held before it joins the world, so its first tick finds itself its wielder's.
        CALLED.put(wielder.getUUID(), body);
        if (!level.addFreshEntity(body)) {
            CALLED.remove(wielder.getUUID());
            return Call.NO_ROOM;
        }
        burst(level, body);
        wielder.displayClientMessage(Component.translatable("message.bleach.spirit.out",
                spirit.get().name().copy().withColor(spirit.get().colour())).withStyle(ChatFormatting.GRAY), true);
        return Call.CALLED;
    }

    /**
     * The body this spirit takes in the world, not yet added to it, or null when its Bankai names no type, a type not
     * registered, or one that is no spirit's. The registry of entity types answers a pig for a name it lacks, so the
     * name is looked up first, and the type is asked for a body, since a type does not say its class.
     */
    @Nullable
    public static MaterialisedSpirit create(Level level, ZanpakutoSpirit spirit) {
        Optional<ResourceLocation> id = spirit.bankai().flatMap(ZanpakutoSpirit.Bankai::entity);
        if (id.isEmpty() || !BuiltInRegistries.ENTITY_TYPE.containsKey(id.get())) {
            return null;
        }
        Entity made = BuiltInRegistries.ENTITY_TYPE.get(id.get()).create(level);
        return made instanceof MaterialisedSpirit body ? body : null;
    }

    /** The spirit out for the wielder with this UUID, alive and in its world, or null when none is. */
    @Nullable
    public static MaterialisedSpirit of(@Nullable UUID wielderId) {
        MaterialisedSpirit spirit = wielderId == null ? null : CALLED.get(wielderId);
        return spirit != null && spirit.isAlive() && !spirit.isRemoved() ? spirit : null;
    }

    /** Whether a spirit is held for the wielder with this UUID, for the tests: one is held only while in its world. */
    public static boolean holds(UUID wielderId) {
        return CALLED.containsKey(wielderId);
    }

    /**
     * Each tick of a spirit: a spirit that is not its wielder's own (never set up, summoned, or dying) goes at once, one
     * whose wielder has met any end goes home, and so does one beaten to its floor.
     */
    public static void upkeep(MaterialisedSpirit spirit) {
        if (of(spirit.wielderId()) != spirit || endCame(spirit)) {
            sendHome(spirit);
        } else if (spirit.beaten()) {
            sendHome(spirit, BEATEN);
        }
    }

    /**
     * Whether its wielder has met an end the spirit's tick sees: gone, dead, in another world, too far, facing its inner
     * Hollow, no longer able to call it, or bound to another spirit.
     */
    private static boolean endCame(MaterialisedSpirit spirit) {
        Player wielder = spirit.wielder();
        if (wielder == null || wielder.isRemoved() || !wielder.isAlive() || wielder.level() != spirit.level()
                || wielder.distanceToSqr(spirit) > LEAVE_DISTANCE * LEAVE_DISTANCE || VisoredEvents.isDuelling(wielder)) {
            return true;
        }
        SoulData data = SoulLogic.get(wielder);
        return !mayCall(data, spirit.level().getGameTime())
                || !data.zanpakuto().spirit().equals(Optional.ofNullable(spirit.spiritId()));
    }

    /** The spirit goes home, its wielder told so: the line for every end but a beating. */
    public static void sendHome(MaterialisedSpirit spirit) {
        sendHome(spirit, HOME);
    }

    /**
     * The spirit goes back into its wielder's inner world: forgotten, gone from this one in its colour's dust, and this
     * line to its wielder, should the wielder still be here to hear it.
     */
    public static void sendHome(MaterialisedSpirit spirit, String line) {
        CALLED.remove(spirit.wielderId(), spirit);
        if (spirit.level() instanceof ServerLevel level) {
            burst(level, spirit);
        }
        spirit.discard();
        Player wielder = spirit.wielder();
        if (wielder != null && wielder.isAlive() && !wielder.isRemoved()) {
            wielder.displayClientMessage(Component.translatable(line).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Where the spirit steps out: the first of eight points {@link #CALL_DISTANCE} blocks from its wielder, starting
     * straight ahead and turning by 45°, with room for its body and footing within two blocks below; null with none.
     */
    @Nullable
    static Vec3 spot(ServerLevel level, Player wielder, EntityType<?> type) {
        for (int i = 0; i < SPOT_TRIES; i++) {
            Vec3 ahead = Vec3.directionFromRotation(0.0F, wielder.getYRot() + i * SPOT_TURN_DEGREES);
            Vec3 spot = wielder.position().add(ahead.x * CALL_DISTANCE, 0.0, ahead.z * CALL_DISTANCE);
            if (level.noCollision(type.getSpawnAABB(spot.x, spot.y, spot.z)) && hasFooting(level, spot)) {
                return spot;
            }
        }
        return null;
    }

    private static boolean hasFooting(ServerLevel level, Vec3 spot) {
        BlockPos feet = BlockPos.containing(spot);
        for (int down = 1; down <= SPOT_DROP_BLOCKS; down++) {
            BlockPos below = feet.below(down);
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return true;
            }
        }
        return false;
    }

    private static void burst(ServerLevel level, MaterialisedSpirit spirit) {
        DustParticleOptions dust = new DustParticleOptions(Vec3.fromRGB24(spirit.colour()).toVector3f(), 1.2F);
        SpiritSenses.particles(level, dust, spirit.getX(), spirit.getY() + spirit.getBbHeight() * 0.5, spirit.getZ(),
                BURST, BURST_SPREAD, BURST_SPREAD_UP, BURST_SPREAD, 0.0);
    }

    // ---- Ends from outside ----

    /** A spirit does not follow its wielder into another world: it goes home. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        MaterialisedSpirit spirit = of(event.getEntity().getUUID());
        if (spirit != null) {
            sendHome(spirit);
        }
    }

    /**
     * A spirit leaving its world for any reason is forgotten, as {@code BossBar} forgets its boss's bar: an unloaded
     * chunk, Peaceful's despawn or a death's removal takes it away without its upkeep, but this still fires.
     */
    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof MaterialisedSpirit spirit) {
            CALLED.remove(spirit.wielderId(), spirit);
        }
    }

    /** A closed world's spirits went with it, unsaved: none is held into the next world a game opens. */
    private static void onServerStopped(ServerStoppedEvent event) {
        CALLED.clear();
    }
}
