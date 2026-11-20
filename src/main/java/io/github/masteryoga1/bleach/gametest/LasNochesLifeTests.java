package io.github.masteryoga1.bleach.gametest;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.world.HuecoMundoPlan;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Las Noches lived in: the sky a soul sees under its dome. */
@GameTestHolder(BleachMod.MODID)
@PrefixGameTestTemplate(false)
public final class LasNochesLifeTests {
    private LasNochesLifeTests() {
    }

    /**
     * Where a camera sees the dome's sky: under the dome and in the fifth tower's room, and in that tower's column from the
     * floor, so every step of the stair through the crown sees it; not in a door's cut, on the sand, under the floor or in
     * the tower's roof. Pure, as the client asks it.
     */
    @GameTest(template = "empty")
    public static void theDomesSkyIsSeenFromUnderIt(GameTestHelper helper) {
        Object[][] cells = {
            {12, 97, 12, true, "the court"}, {0, 97, 40, true, "the south corridor"}, {0, 100, -28, true, "the meeting hall"},
            {0, 97, 58, true, "the south door's inner cell"}, {0, 97, 59, false, "the south door's cut"},
            {0, 97, 70, false, "the sand outside"}, {12, 95, 12, false, "under the floor"},
            {-58, 97, 12, true, "the lining's cell west of the court"}, {61, 97, 0, false, "the dome's tiles"},
            {0, 150, 0, true, "the throne room"}, {7, 150, 0, true, "the throne room's edge"},
            {8, 150, 0, false, "the fifth tower's wall"}, {0, 205, 0, true, "under the tower's roof"},
            {0, 206, 0, false, "the tower's roof"}, {0, 143, -4, true, "the stair's last steps through the crown"}};
        for (Object[] cell : cells) {
            int x = (int) cell[0], y = (int) cell[1], z = (int) cell[2];
            helper.assertTrue(HuecoMundoPlan.underSky(x, y, z) == (boolean) cell[3], cell[4] + " (" + x + ", " + y + ", "
                    + z + ") " + ((boolean) cell[3] ? "does not see" : "sees") + " the dome's sky");
        }
        helper.assertFalse(HuecoMundoPlan.enclosed(0, 143, -4), "the crown's cut is under the dome without the column");
        // The feet and eyes of a soul on every step and landing of the stair, in its well, see it.
        Set<HuecoMundoPlan.Hall> treads = EnumSet.range(HuecoMundoPlan.Hall.STAIR_NORTH, HuecoMundoPlan.Hall.STAIR_WEST);
        int steps = 0;
        for (int x = -HuecoMundoPlan.WELL_HALF; x < HuecoMundoPlan.WELL_HALF; x++) {
            for (int z = -HuecoMundoPlan.WELL_HALF; z < HuecoMundoPlan.WELL_HALF; z++) {
                for (int y = HuecoMundoPlan.LEVEL_Y; y < HuecoMundoPlan.DOME_TOP_Y; y++) {
                    HuecoMundoPlan.Hall here = HuecoMundoPlan.hallsAt(x, y, z);
                    if (treads.contains(here) || here == HuecoMundoPlan.Hall.TILES) {
                        steps += treads.contains(here) ? 1 : 0;
                        helper.assertTrue(HuecoMundoPlan.underSky(x, y + 1, z) && HuecoMundoPlan.underSky(x, y + 2, z),
                                "a soul on the stair at (" + x + ", " + y + ", " + z + ") loses the dome's sky");
                    }
                }
            }
        }
        // The column adds to the dome's sky only the crown's three courses about the stair's way up.
        int added = 0;
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = HuecoMundoPlan.LEVEL_Y; y < HuecoMundoPlan.FIFTH_TOWER_TOP_Y; y++) {
                    if (HuecoMundoPlan.underSky(x, y, z) && !HuecoMundoPlan.enclosed(x, y, z)) {
                        added++;
                        helper.assertTrue(y >= 141 && y <= 143,
                                "(" + x + ", " + y + ", " + z + ") sees the sky past the crown");
                    }
                }
            }
        }
        helper.assertTrue(steps == 96, "the tower's stair has " + steps + " step cells, not 96");
        helper.assertTrue(added == 432, "the tower's column adds " + added + " cells to the dome's sky, not 432");
        helper.succeed();
    }
}
