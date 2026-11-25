package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.TravelEvents;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.BleachWorlds;
import io.github.masteryoga1.bleach.world.UraharaRoom;
import io.github.masteryoga1.bleach.world.UraharaShop;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Kisuke Urahara's gates in the training room under his shop, asked for at his counter and opened once the soul has come
 * down onto the room's floor: a plain Senkaimon to Soul Society, and a Garganta to Hueco Mundo between the Keikaigi's two
 * posts, whose far end stays ten minutes to bring souls home. Free, one of each a game day for each soul; nothing here is
 * saved but those days.
 */
public final class UraharaGates {
    /** How long he waits, after a soul asks, for it to come down onto the room's floor: a minute. */
    public static final int ASK_TICKS = 1200;
    /** How long the far end in Hueco Mundo stays open: ten minutes. */
    public static final int WAY_BACK_TICKS = 12_000;
    /** Keeps the far end's chunk ticking for its ten minutes, then lets go of it by itself. */
    public static final TicketType<ChunkPos> WAY_BACK =
            TicketType.create("bleach:way_back", Comparator.comparingLong(ChunkPos::toLong), WAY_BACK_TICKS);
    /** The ticket's reach: within it the far end's own chunk ticks its creatures. */
    public static final int WAY_BACK_RADIUS = 2;
    /** His Senkaimon's cells, from the shop's corner before its turn: 1 deep, 3 across and 4 tall on the gates' floor. */
    public static final BoundingBox SENKAIMON = new BoundingBox(17, -21, 4, 17, -18, 6);
    /** His Garganta's cells between the posts: 2 by 2 and 4 tall. */
    public static final BoundingBox GARGANTA = new BoundingBox(17, -21, 12, 18, -18, 13);
    /** The Keikaigi's two posts of dark oak either side of his Garganta: each 2 across, 1 deep and 6 tall. */
    public static final List<BoundingBox> POSTS = List.of(new BoundingBox(17, -21, 11, 18, -16, 11),
            new BoundingBox(17, -21, 14, 18, -16, 14));
    /** The room's floor, wall to wall and up to 2 blocks over it: a soul here has come down, one higher up the ladder not. */
    private static final BoundingBox FLOOR = new BoundingBox(-9, -21, -7, 21, -19, 22);
    /** The ladder's foot, under the hatch. */
    private static final BlockPos LADDER_FOOT = new BlockPos(2, -21, 9);
    /** The far end stands this much further out than its soul lands, or twice, three or four times as far. */
    private static final int FAR_STEP = 4, FAR_STEPS = 4;

    private UraharaGates() {
    }

    /** Why he opens nothing, in his order: a seized body, a day spent, no room, a gate in reach, its place not clear. */
    private enum Refusal {
        TAKEN,
        SPENT,
        NO_ROOM,
        STANDING,
        BLOCKED,
        /** Last, and only as the soul arrives: nowhere in Hueco Mundo holds the far end. */
        NO_FAR_SIDE;

        /** The name of his line for it. */
        String line() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The room under one keeper: the shop's corner on its floor, and the turn the shop and room were laid with. */
    public record Room(BlockPos corner, Rotation turn) {
        /** Where this place, from the shop's corner before its turn, lies in the world. */
        public BlockPos at(BlockPos place) {
            return corner.offset(place.rotate(turn));
        }

        /** The world's cells for this box of places. */
        public BoundingBox box(BoundingBox places) {
            return BoundingBox.fromCorners(at(new BlockPos(places.minX(), places.minY(), places.minZ())),
                    at(new BlockPos(places.maxX(), places.maxY(), places.maxZ())));
        }

        /** The middle of the floor of this box of places: where a gate that fills it stands. */
        public Vec3 foot(BoundingBox places) {
            BoundingBox box = box(places);
            return new Vec3((box.minX() + box.maxX() + 1) / 2.0, box.minY(), (box.minZ() + box.maxZ() + 1) / 2.0);
        }

        /** The way his Senkaimon faces: east before the turn, along the hall from the ladder. */
        public Direction facing() {
            return turn.rotate(Direction.EAST);
        }

        /** Whether this soul stands on the room's floor, come down off the ladder. */
        boolean onFloor(Player soul) {
            return box(FLOOR).isInside(soul.blockPosition());
        }
    }

    /** A soul's ask: the gate, the room it opens in, and the game time he stops waiting. */
    record Ask(NpcAction gate, Room room, long until) {
    }

    /**
     * The room under this keeper, judged by its blocks at each turn his shop could be laid with: the hatch, a spruce
     * trapdoor 2 from him and 1 down, over the ladder's foot 21 under it, both facing as that turn faces them. Null
     * outside the Human World, or where none stands.
     */
    @Nullable
    public static Room roomUnder(KisukeUrahara keeper) {
        Level level = keeper.level();
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        for (Rotation turn : Rotation.values()) {
            Room room = new Room(keeper.blockPosition().subtract(UraharaShop.KEEPER.rotate(turn)), turn);
            BlockState hatch = level.getBlockState(room.at(UraharaRoom.HATCH));
            BlockState foot = level.getBlockState(room.at(LADDER_FOOT));
            if (hatch.is(Blocks.SPRUCE_TRAPDOOR) && hatch.getValue(TrapDoorBlock.FACING) == room.facing()
                    && foot.is(Blocks.LADDER) && foot.getValue(LadderBlock.FACING) == room.facing()) {
                return room;
            }
        }
        return null;
    }

    /**
     * A soul asked him for this gate: he tells it why not, or the way down, and waits a minute for it to come down. Each
     * ask drops the soul's last, whether he takes this one or not.
     */
    static void ask(KisukeUrahara keeper, ServerLevel level, ServerPlayer soul, NpcAction gate) {
        keeper.asks.remove(soul.getUUID());
        Room room = roomUnder(keeper);
        Refusal why = refusal(level, soul, gate, room);
        if (why != null) {
            say(keeper, soul, why.line());
            return;
        }
        keeper.asks.put(soul.getUUID(), new Ask(gate, room, level.getGameTime() + ASK_TICKS));
        String lines = gate.key() + ".";
        say(keeper, soul, lines + KisukeUrahara.viewFor(SoulLogic.get(soul)).key());
        say(keeper, soul, lines + (gate == NpcAction.SENKAIMON && TravelLogic.guided(soul) ? "way_guided" : "way"));
    }

    /**
     * On each of his ticks: an ask a minute old, or whose soul has left the game, is dropped; a soul come down alive onto
     * the room's floor has its gate opened and its day for that gate spent, or is told why not.
     */
    static void arrivals(KisukeUrahara keeper, ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Ask>> each = keeper.asks.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<UUID, Ask> ask = each.next();
            ServerPlayer soul = level.getServer().getPlayerList().getPlayer(ask.getKey());
            if (soul == null || now >= ask.getValue().until()) {
                each.remove();
            } else if (soul.level() == level && soul.isAlive() && !soul.isSpectator() && SoulLogic.hasSoul(soul)
                    && ask.getValue().room().onFloor(soul)) {
                each.remove();
                NpcAction gate = ask.getValue().gate();
                Room room = roomUnder(keeper);
                Refusal why = refusal(level, soul, gate, room);
                if (why == null) {
                    why = gate == NpcAction.SENKAIMON ? openSenkaimon(level, room, now)
                            : openGarganta(keeper, level, room, soul, now);
                }
                if (why == null) {
                    soul.setData(day(gate), today(level));
                } else {
                    say(keeper, soul, why.line());
                }
            }
        }
    }

    /** Why he would open no gate of this kind for this soul now, in the order he checks, or null. */
    @Nullable
    private static Refusal refusal(ServerLevel level, ServerPlayer soul, NpcAction gate, @Nullable Room room) {
        if (VisoredLogic.takenOver(SoulLogic.get(soul))) {
            return Refusal.TAKEN;
        }
        if (spentToday(soul, level, gate)) {
            return Refusal.SPENT;
        }
        return room == null ? Refusal.NO_ROOM : floor(level, room, gate);
    }

    /**
     * What keeps this gate off the room's floor now, or null: a gate within reach of it, as that kind's own crowding
     * reaches; else anything in its cells but air, or in its posts' but air or dark oak, a floor under them not whole, or
     * a creature in them.
     */
    @Nullable
    private static Refusal floor(ServerLevel level, Room room, NpcAction gate) {
        boolean senkaimon = gate == NpcAction.SENKAIMON;
        BoundingBox places = senkaimon ? SENKAIMON : GARGANTA, cells = room.box(places);
        Vec3 foot = room.foot(places);
        AABB box = senkaimon ? Senkaimon.boxAt(foot, room.facing()) : GargantaRift.passBoxAt(foot, 1.0F);
        // A Senkaimon's grown by a player's size, a Garganta's by its own.
        AABB reach = senkaimon ? box.inflate(EntityType.PLAYER.getWidth(), EntityType.PLAYER.getHeight(),
                EntityType.PLAYER.getWidth()) : box.inflate(box.getXsize(), box.getYsize(), box.getZsize());
        if (!level.getEntitiesOfClass(Senkaimon.class, reach).isEmpty()
                || !GargantaRift.passagesMeeting(level, reach).isEmpty()) {
            return Refusal.STANDING;
        }
        List<BoundingBox> posts = senkaimon ? List.of() : POSTS.stream().map(room::box).toList();
        AABB space = AABB.of(cells);
        for (BoundingBox post : posts) {
            space = space.minmax(AABB.of(post));
        }
        boolean clear = clear(level, cells, false) && posts.stream().allMatch(post -> clear(level, post, true));
        return clear && level.getEntitiesOfClass(LivingEntity.class, space, living -> !living.isSpectator()).isEmpty()
                ? null : Refusal.BLOCKED;
    }

    /** Whether every cell here is air, or dark oak in a post's, over a floor whole to stand on. */
    private static boolean clear(ServerLevel level, BoundingBox cells, boolean post) {
        return BlockPos.betweenClosedStream(cells).allMatch(pos -> {
            BlockState state = level.getBlockState(pos);
            return (state.isAir() || post && state.is(Blocks.DARK_OAK_LOG)) && (pos.getY() > cells.minY()
                    || level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP));
        });
    }

    /** Opens his Senkaimon in its cells, facing along the hall, with no blade turned: null, or why not. */
    @Nullable
    private static Refusal openSenkaimon(ServerLevel level, Room room, long now) {
        Vec3 foot = room.foot(SENKAIMON);
        Direction facing = room.facing();
        // Never added: a marker the gate's distance behind its foot, facing it, for the gate to stand before.
        Marker before = new Marker(EntityType.MARKER, level);
        before.moveTo(foot.x - facing.getStepX() * Senkaimon.DISTANCE, foot.y,
                foot.z - facing.getStepZ() * Senkaimon.DISTANCE, facing.toYRot(), 0.0F);
        return Senkaimon.open(level, before, null, now) != null ? null : Refusal.BLOCKED;
    }

    /**
     * Opens the far end in Hueco Mundo by where this soul will land, held by its ticket, then his Garganta between the
     * posts, and sets the posts' dark oak where it is missing: null, or why not.
     */
    @Nullable
    private static Refusal openGarganta(KisukeUrahara keeper, ServerLevel level, Room room, ServerPlayer soul,
                                        long now) {
        ServerLevel hueco = BleachWorlds.level(level.getServer(), BleachWorlds.HUECO_MUNDO);
        Vec3 foot = room.foot(GARGANTA);
        GargantaRift far = hueco == null ? null : farEnd(keeper, hueco, soul, foot, now);
        if (far == null || !hueco.addFreshEntity(far)) {
            return Refusal.NO_FAR_SIDE;
        }
        GargantaRift near = GargantaRift.passage(level, keeper, now, BleachWorlds.HUECO_MUNDO, 1.0F,
                TravelLogic.gargantaOpenTicks());
        near.moveTo(foot.x, foot.y, foot.z);
        if (!level.addFreshEntity(near)) {
            far.discard();
            return Refusal.BLOCKED;
        }
        ChunkPos held = new ChunkPos(far.blockPosition());
        hueco.getChunkSource().addRegionTicket(WAY_BACK, held, WAY_BACK_RADIUS, held);
        for (BoundingBox post : POSTS) {
            BlockPos.betweenClosedStream(room.box(post)).map(BlockPos::immutable)
                    .filter(pos -> level.getBlockState(pos).isAir())
                    .forEach(pos -> level.setBlockAndUpdate(pos, Blocks.DARK_OAK_LOG.defaultBlockState()));
        }
        return null;
    }

    /**
     * The far end for a soul passing his Garganta from its foot, not yet added: a Garganta home standing ten minutes on
     * Hueco Mundo's ground, 4 blocks further from Las Noches than the soul lands, or 8, 12 or 16 where a gate or a loaded
     * creature stands nearer (a chunk's saved creatures may not be loaded yet). Null where none of those holds it.
     */
    @Nullable
    private static GargantaRift farEnd(KisukeUrahara keeper, ServerLevel hueco, ServerPlayer soul, Vec3 foot, long now) {
        Vec3 landing = TravelEvents.huecoMundoLanding(soul, hueco, foot.x, foot.z);
        if (landing == null) {
            return null;
        }
        // Never the middle itself: every landing is pushed out past Las Noches.
        double out = Math.hypot(landing.x, landing.z);
        for (int step = 1; step <= FAR_STEPS; step++) {
            double further = 1.0 + FAR_STEP * step / out;
            Vec3 at = TravelEvents.groundUnder(soul, hueco, Mth.floor(landing.x * further), Mth.floor(landing.z * further));
            if (at == null) {
                continue;
            }
            // Clear of any gate's reach, as a Garganta's own crowding reaches, and of every creature already loaded.
            AABB box = GargantaRift.passBoxAt(at, 1.0F);
            if (GargantaRift.passagesMeeting(hueco, box.inflate(box.getXsize(), box.getYsize(), box.getZsize())).isEmpty()
                    && hueco.getEntitiesOfClass(LivingEntity.class, box).isEmpty()) {
                GargantaRift far = GargantaRift.passage(hueco, keeper, now, Level.OVERWORLD, 1.0F, WAY_BACK_TICKS);
                far.moveTo(at.x, at.y, at.z);
                return far;
            }
        }
        return null;
    }

    /** Whether this soul has had this gate of his on this world's day, the mod's own ({@link KidoLogic#lessonDay}). */
    private static boolean spentToday(Player soul, Level level, NpcAction gate) {
        return soul.getData(day(gate)) == today(level);
    }

    /** His gates this soul has had today, which his dialogue greys. */
    static List<NpcAction> spent(Player soul, Level level) {
        return Stream.of(NpcAction.SENKAIMON, NpcAction.GARGANTA).filter(gate -> spentToday(soul, level, gate)).toList();
    }

    /** Where the day a soul last had this gate of his is kept. */
    private static Supplier<AttachmentType<Long>> day(NpcAction gate) {
        return gate == NpcAction.SENKAIMON ? SoulAttachments.SHOP_SENKAIMON_DAY : SoulAttachments.SHOP_GARGANTA_DAY;
    }

    private static long today(Level level) {
        return KidoLogic.lessonDay(level.getGameTime(), level.getDayTime());
    }

    /** He says his line of this name to the soul in chat, as the Quincy Elder speaks: keyed by his dialogue, as his words. */
    private static void say(KisukeUrahara keeper, Player soul, String line) {
        soul.sendSystemMessage(Component.translatable("message.bleach." + keeper.dialogueKey() + "." + line)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
