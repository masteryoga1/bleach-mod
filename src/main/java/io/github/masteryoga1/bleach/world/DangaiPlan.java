package io.github.masteryoga1.bleach.world;

import net.minecraft.world.phys.Vec3;

/**
 * The Dangai's plan: one lane down each chunk column with chunk x from 0 to 255, carved out of solid bedrock, walled by
 * the Kōryū. Every size Java needs and the lane feature lays; x and z are blocks, and a lane runs towards +z.
 */
public final class DangaiPlan {
    /** How many lanes there are: lane k runs down chunk column x = k. */
    public static final int LANES = 256;
    /** The y a soul stands at in a lane, one above the floor's top block, and how many blocks of air rise from it. */
    public static final int FLOOR_Y = 3, LANE_HEIGHT = 8;
    /** The Kōryū's two walls, as x within the lane's chunk; the lane is the air between them. */
    public static final int WEST_WALL = 3, EAST_WALL = 12;
    /** Where the lane's end lets a soul out, and where the rock caps the lane. */
    public static final int EXIT_Z = 160, END_Z = 168;

    /** What a block of the Dangai is. */
    public enum Cell { ROCK, AIR, KORYU }

    private DangaiPlan() {
    }

    /** The x down the middle of this lane. */
    public static int laneX(int lane) {
        return 16 * lane + 8;
    }

    /** Where a soul enters this lane: its middle, on the floor, half a block from the rock behind. */
    public static Vec3 entry(int lane) {
        return new Vec3(laneX(lane), FLOOR_Y, 0.5);
    }

    /** What lies at this block: rock everywhere but the lanes' air and their Kōryū. */
    public static Cell cell(int x, int y, int z) {
        int lane = x >> 4;
        if (lane < 0 || lane >= LANES || y < FLOOR_Y || y >= FLOOR_Y + LANE_HEIGHT || z < 0 || z >= END_Z) {
            return Cell.ROCK;
        }
        int local = x & 15;
        if (local == WEST_WALL || local == EAST_WALL) {
            return Cell.KORYU;
        }
        return local < WEST_WALL || local > EAST_WALL ? Cell.ROCK : Cell.AIR;
    }
}
