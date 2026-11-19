package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.KidoLogic;
import io.github.masteryoga1.bleach.soul.SoulAttachments;
import io.github.masteryoga1.bleach.soul.SoulLogic;
import io.github.masteryoga1.bleach.soul.TravelLogic;
import io.github.masteryoga1.bleach.soul.VisoredLogic;
import io.github.masteryoga1.bleach.world.UraharaRoom;
import io.github.masteryoga1.bleach.world.UraharaShop;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kisuke Urahara's Senkaimon in the training room under his shop: asked for at his counter, a plain gate to Soul Society
 * opens once the soul has come down onto the room's floor, free and once a game day for each soul. Nothing here is saved
 * but those days.
 */
public final class UraharaGates {
    /** How long he waits, after a soul asks, for it to come down onto the room's floor: a minute. */
    public static final int ASK_TICKS = 1200;
    /** His Senkaimon's cells, from the shop's corner before its turn: 1 deep, 3 across and 4 tall on the gates' floor. */
    public static final BoundingBox SENKAIMON = new BoundingBox(17, -21, 4, 17, -18, 6);
    /** The room's floor, wall to wall and up to 2 blocks over it: a soul here has come down, one higher up the ladder not. */
    private static final BoundingBox FLOOR = new BoundingBox(-9, -21, -7, 21, -19, 22);
    /** The ladder's foot, under the hatch. */
    private static final BlockPos LADDER_FOOT = new BlockPos(2, -21, 9);

    private UraharaGates() {
    }

    /** Why he opens nothing, in his order: a seized body, a day spent, no room, a gate in reach, its place not clear. */
    private enum Refusal {
        TAKEN,
        SPENT,
        NO_ROOM,
        STANDING,
        BLOCKED;

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

    /** A soul's ask: the room his gate opens in, and the game time he stops waiting. */
    record Ask(Room room, long until) {
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
     * A soul asked him for his Senkaimon: he tells it why not, or the way down, and waits a minute for it to come down.
     * Each ask drops the soul's last, whether he takes this one or not.
     */
    static void ask(KisukeUrahara keeper, ServerLevel level, ServerPlayer soul) {
        keeper.asks.remove(soul.getUUID());
        Room room = roomUnder(keeper);
        Refusal why = refusal(level, soul, room);
        if (why != null) {
            say(keeper, soul, why.line());
            return;
        }
        keeper.asks.put(soul.getUUID(), new Ask(room, level.getGameTime() + ASK_TICKS));
        say(keeper, soul, "senkaimon." + KisukeUrahara.viewFor(SoulLogic.get(soul)).key());
        say(keeper, soul, TravelLogic.guided(soul) ? "senkaimon.way_guided" : "senkaimon.way");
    }

    /**
     * On each of his ticks: an ask a minute old, or whose soul has left the game, is dropped; a soul come down alive onto
     * the room's floor has its gate opened and its day spent, or is told why not.
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
                Room room = roomUnder(keeper);
                Refusal why = refusal(level, soul, room);
                if (why == null) {
                    why = open(level, room, now);
                }
                if (why == null) {
                    soul.setData(SoulAttachments.SHOP_SENKAIMON_DAY, today(level));
                } else {
                    say(keeper, soul, why.line());
                }
            }
        }
    }

    /** Why he would open no Senkaimon for this soul now, in the order he checks, or null. */
    @Nullable
    private static Refusal refusal(ServerLevel level, ServerPlayer soul, @Nullable Room room) {
        if (VisoredLogic.takenOver(SoulLogic.get(soul))) {
            return Refusal.TAKEN;
        }
        if (spentToday(soul, level)) {
            return Refusal.SPENT;
        }
        return room == null ? Refusal.NO_ROOM : floor(level, room);
    }

    /**
     * What keeps his Senkaimon off the room's floor now, or null: a gate within a soul's reach of it, as a Senkaimon's own
     * crowding reaches; else anything but air in its cells, a floor under them not whole, or a creature in them.
     */
    @Nullable
    private static Refusal floor(ServerLevel level, Room room) {
        AABB reach = Senkaimon.boxAt(room.foot(SENKAIMON), room.facing()).inflate(EntityType.PLAYER.getWidth(),
                EntityType.PLAYER.getHeight(), EntityType.PLAYER.getWidth());
        if (!level.getEntitiesOfClass(Senkaimon.class, reach).isEmpty()
                || !GargantaRift.passagesMeeting(level, reach).isEmpty()) {
            return Refusal.STANDING;
        }
        BoundingBox cells = room.box(SENKAIMON);
        boolean clear = BlockPos.betweenClosedStream(cells).allMatch(pos -> level.getBlockState(pos).isAir()
                && (pos.getY() > cells.minY() || level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
                Direction.UP)));
        return clear && level.getEntitiesOfClass(LivingEntity.class, AABB.of(cells), living -> !living.isSpectator())
                .isEmpty() ? null : Refusal.BLOCKED;
    }

    /** Opens his Senkaimon in its cells, facing along the hall, with no blade turned: null, or why not. */
    @Nullable
    private static Refusal open(ServerLevel level, Room room, long now) {
        Vec3 foot = room.foot(SENKAIMON);
        Direction facing = room.facing();
        // Never added: a marker the gate's distance behind its foot, facing it, for the gate to stand before.
        Marker before = new Marker(EntityType.MARKER, level);
        before.moveTo(foot.x - facing.getStepX() * Senkaimon.DISTANCE, foot.y,
                foot.z - facing.getStepZ() * Senkaimon.DISTANCE, facing.toYRot(), 0.0F);
        return Senkaimon.open(level, before, null, now) != null ? null : Refusal.BLOCKED;
    }

    /** Whether this soul has had his Senkaimon on this world's day, the mod's own ({@link KidoLogic#lessonDay}). */
    public static boolean spentToday(Player soul, Level level) {
        return soul.getData(SoulAttachments.SHOP_SENKAIMON_DAY) == today(level);
    }

    /** His gates this soul has had today, which his dialogue greys. */
    static List<NpcAction> spent(Player soul, Level level) {
        return spentToday(soul, level) ? List.of(NpcAction.SENKAIMON) : List.of();
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
