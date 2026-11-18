"""Builds the mod's structure templates (.nbt) from code, so they can be reviewed and rebuilt.

Run from the repository root:

    python3 tools/structures/make_structures.py          # write every template
    python3 tools/structures/make_structures.py --check  # fail if a committed template differs

The check compares the uncompressed NBT, not the gzip bytes, so a different zlib build does not trip
it. Templates are written for Minecraft 1.21.1 (data version 3955) and list every position in their
box, air included, so placing one clears whatever was there.

Uses only the standard library.
"""
import functools
import gzip
import math
import struct
import sys
import zlib
from pathlib import Path

STRUCTURES = Path("src/main/resources/data/bleach/structure")
DATA_VERSION = 3955

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND = range(11)


class Tag:
    def __init__(self, kind, value, element=None):
        self.kind, self.value, self.element = kind, value, element


def byte(v):
    return Tag(BYTE, v)


def integer(v):
    return Tag(INT, v)


def string(v):
    return Tag(STRING, v)


def ints(*values):
    return Tag(LIST, [integer(v) for v in values], INT)


def doubles(*values):
    return Tag(LIST, [Tag(DOUBLE, v) for v in values], DOUBLE)


def floats(*values):
    return Tag(LIST, [Tag(FLOAT, v) for v in values], FLOAT)


def compounds(items):
    return Tag(LIST, items, COMPOUND)


def compound(**entries):
    return Tag(COMPOUND, entries)


def encode_string(text):
    raw = text.encode("utf-8")  # every name here is ASCII, where Java's modified UTF-8 is the same
    return struct.pack(">H", len(raw)) + raw


def payload(tag):
    kind, value = tag.kind, tag.value
    if kind == BYTE:
        return struct.pack(">b", value)
    if kind == INT:
        return struct.pack(">i", value)
    if kind == FLOAT:
        return struct.pack(">f", value)
    if kind == DOUBLE:
        return struct.pack(">d", value)
    if kind == STRING:
        return encode_string(value)
    if kind == LIST:
        element = tag.element if value else END
        return struct.pack(">bi", element, len(value)) + b"".join(payload(item) for item in value)
    if kind == COMPOUND:
        out = b""
        for name, child in value.items():
            out += struct.pack(">b", child.kind) + encode_string(name) + payload(child)
        return out + b"\x00"
    raise ValueError(kind)


def nbt(root):
    """An unnamed root compound, uncompressed."""
    return b"\x0a\x00\x00" + payload(root)


class Template:
    """A box of block states, all air to begin with, and the entities in it."""

    def __init__(self, width, height, depth):
        self.size = (width, height, depth)
        self.blocks = {}
        self.entities = []

    def set(self, x, y, z, block, **properties):
        width, height, depth = self.size
        if not (0 <= x < width and 0 <= y < height and 0 <= z < depth):
            raise ValueError("%s at %s is outside the template" % (block, (x, y, z)))
        self.blocks[(x, y, z)] = (block, tuple(sorted(properties.items())))

    def fill(self, x0, y0, z0, x1, y1, z1, block, **properties):
        """Fills the box between two corners, both included."""
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, block, **properties)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z), ("minecraft:air", ()))[0]

    def add_entity(self, x, y, z, **entity_nbt):
        self.entities.append(compound(
            pos=doubles(x, y, z),
            blockPos=ints(int(x // 1), int(y // 1), int(z // 1)),
            nbt=compound(**entity_nbt)))

    def to_nbt(self):
        width, height, depth = self.size
        palette, index, blocks = [], {}, []
        for x in range(width):
            for y in range(height):
                for z in range(depth):
                    state = self.blocks.get((x, y, z), ("minecraft:air", ()))
                    if state not in index:
                        index[state] = len(palette)
                        name, properties = state
                        entry = {"Name": string(name)}
                        if properties:
                            entry["Properties"] = compound(**{k: string(v) for k, v in properties})
                        palette.append(compound(**entry))
                    blocks.append(compound(pos=ints(x, y, z), state=integer(index[state])))
        return nbt(compound(
            DataVersion=integer(DATA_VERSION),
            size=ints(*self.size),
            palette=compounds(palette),
            blocks=compounds(blocks),
            entities=compounds(self.entities)))


def ring(template, y, x0, z0, x1, z1, block, **properties):
    """The edge of the rectangle between two corners, both included."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                template.set(x, y, z, block, **properties)


def academy():
    """
    The stand-in for the Shin'ō Academy: an open courtyard behind a gate on the north (low z) side,
    and a hall with a veranda, white plaster walls between dark pillars, shōji windows and a tiled
    roof. The instructor stands inside facing the door. The north-west corner of the courtyard
    (x0-2, z0-2) is kept clear: souls sent here by Konsō arrive there.
    """
    t = Template(15, 9, 17)
    log = dict(block="minecraft:stripped_dark_oak_log", axis="y")
    slab = dict(block="bleach:roof_tile_slab", type="bottom", waterlogged="false")

    # Ground: a calcite courtyard with a stone path, the hall on stone with tatami inside, their weave crossed.
    t.fill(0, 0, 0, 14, 0, 5, "minecraft:calcite")
    t.fill(6, 0, 0, 8, 0, 5, "minecraft:stone_bricks")
    t.fill(0, 0, 6, 14, 0, 16, "minecraft:stone_bricks")
    for x in range(2, 13):
        for z in range(8, 15):
            t.set(x, 0, z, "bleach:tatami", facing="north" if (x + z) % 2 == 0 else "east")

    # The gate: two posts, a lintel and a tiled cap.
    for x in (5, 9):
        t.fill(x, 1, 0, x, 3, 0, **log)
    t.fill(4, 4, 0, 10, 4, 0, "minecraft:dark_oak_planks")
    t.fill(4, 5, 0, 10, 5, 0, **slab)
    for x in (3, 11):
        t.set(x, 1, 3, "minecraft:lantern", hanging="false", waterlogged="false")

    # Hall walls: white plaster on a black base between pillars, with shōji in the middle two rows of each bay, each
    # joined to its neighbours along the wall but not to the door.
    for y in range(1, 5):
        for x in range(1, 14):
            for z in range(7, 16):
                on_x_wall = x in (1, 13)
                on_z_wall = z in (7, 15)
                if not (on_x_wall or on_z_wall):
                    continue
                pillar = (on_z_wall and x in (1, 5, 9, 13)) or (on_x_wall and z in (7, 11, 15))
                if pillar:
                    t.set(x, y, z, **log)
                elif y in (2, 3):
                    beside_door = z == 7 and y == 2
                    t.set(x, y, z, "bleach:shoji", east=str(on_z_wall and not (beside_door and x == 6)).lower(),
                          north=str(on_x_wall).lower(), south=str(on_x_wall).lower(),
                          waterlogged="false", west=str(on_z_wall and not (beside_door and x == 8)).lower())
                else:
                    t.set(x, y, z, "bleach:black_plaster" if y == 1 else "bleach:white_plaster")
    door = dict(block="bleach:shoji_door", facing="south", hinge="left", open="false", powered="false")
    t.set(7, 1, 7, half="lower", **door)
    t.set(7, 2, 7, half="upper", **door)

    # Inside: a ceiling, the Tailoring Table and two hanging lanterns.
    t.fill(1, 5, 7, 13, 5, 15, "minecraft:dark_oak_planks")
    t.set(3, 1, 13, "bleach:tailoring_table")
    for x in (4, 10):
        t.set(x, 4, 10, "minecraft:lantern", hanging="true", waterlogged="false")

    # The roof, stepping in over the veranda.
    ring(t, 6, 0, 6, 14, 16, **slab)
    t.fill(1, 6, 7, 13, 6, 15, "bleach:roof_tiles")
    ring(t, 7, 2, 8, 12, 14, **slab)
    t.fill(3, 7, 9, 11, 7, 13, "bleach:roof_tiles")
    t.fill(4, 8, 10, 10, 8, 12, **slab)

    t.add_entity(7.5, 1.0, 12.5, id=string("bleach:academy_instructor"), Rotation=floats(180.0, 0.0),
                 PersistenceRequired=byte(1), Invulnerable=byte(1))
    return t


# Soul Society's plan, as SeireiteiPlan.java has it; GateHouseTests checks a stamped house against the Java.
PLATEAU_Y, WALL_TOP_Y = 72, 112
SEIREITEI_RADIUS, WALL_THICKNESS, GATE_HALF_WIDTH = 512, 8, 8
GATE_DOOR_HALF_WIDTH, GATE_DOOR_TOP_Y = 5, PLATEAU_Y + 25
GATE_ROW, GATE_LIFT, GATE_WICKET_ROWS = WALL_THICKNESS // 2, 12, 2


def in_wall(x, z):
    """Whether this column is the Seireiheki's, not a gate's gap: SeireiteiPlan.inWall."""
    r = math.hypot(x + 0.5, z + 0.5)
    axis = min(abs(x + 0.5), abs(z + 0.5))
    return SEIREITEI_RADIUS <= r < SEIREITEI_RADIUS + WALL_THICKNESS and axis >= GATE_HALF_WIDTH


def gate_house():
    """
    A gate house of the Seireiheki, filling the south gate's gap from the lawn to the wall's top; the plan turns it
    into the others. x runs west to east across the gap, z from the wall's inner face out to the Rukongai. Piers of
    white plaster on a black plinth stand either side of one doorway, framed by dark posts, under a lintel and an
    upper storey with a tiled roof. Left open for the great gate (7j3) and its wicket (7j4): a slot in the gate's
    row over the doorway, as tall as the gate's lift, and a passage through the right-hand pier, coming in, at the
    ground.
    Every position is listed, so the box must be the gap alone: a column of the wall in it fails the build.
    """
    width, height, depth = 2 * GATE_HALF_WIDTH, WALL_TOP_Y - PLATEAU_Y, WALL_THICKNESS
    walled = [(x, z) for x in range(width) for z in range(depth) if in_wall(x - GATE_HALF_WIDTH, SEIREITEI_RADIUS + z)]
    if walled:
        raise ValueError(f"the gate house would be built over the Seireiheki's columns {walled}")
    t = Template(width, height, depth)
    door_top = GATE_DOOR_TOP_Y - PLATEAU_Y
    door = range(GATE_HALF_WIDTH - GATE_DOOR_HALF_WIDTH, GATE_HALF_WIDTH + GATE_DOOR_HALF_WIDTH)
    posts = (door[0] - 1, door[-1] + 1)
    wicket = (door[-1] + 1 + width - 1) // 2
    post = dict(block="minecraft:stripped_dark_oak_log", axis="y")
    beam = dict(block="minecraft:stripped_dark_oak_log", axis="x")
    faces = (0, depth - 1)

    for x in range(width):
        for z in range(depth):
            face_post = x in posts and z in faces
            for y in range(height - 2):
                if y < door_top and x in door:
                    continue  # the doorway
                if face_post and y not in (door_top, height - 3):
                    t.set(x, y, z, **post)
                elif y in (door_top, height - 3):
                    if z in faces:
                        t.set(x, y, z, **beam)
                    else:
                        t.set(x, y, z, "minecraft:dark_oak_planks")
                else:
                    t.set(x, y, z, "bleach:black_plaster" if y < 2 else "bleach:white_plaster")
            # The roof: tiles, and at each face a row of upside-down stairs, an eave under a top flat at the wall's:
            # nowhere on the roof stands lower, in the gate's cells, as a way round the doorway.
            t.set(x, height - 2, z, "bleach:roof_tiles")
            if z in faces:
                t.set(x, height - 1, z, "bleach:roof_tile_stairs", facing="south" if z == 0 else "north", half="top",
                      shape="straight", waterlogged="false")
            else:
                t.set(x, height - 1, z, "bleach:roof_tiles")

    # The slot the great gate rises into, and the wicket's passage.
    for x in door:
        for y in range(door_top, door_top + GATE_LIFT):
            t.set(x, y, GATE_ROW, "minecraft:air")
    for y in range(GATE_WICKET_ROWS):
        for z in range(depth):
            t.set(wicket, y, z, "minecraft:air")
    return t


def lane():
    """A game-test lane for flash steps: 12 long (+X), 3 wide, a stone floor and room above."""
    t = Template(12, 4, 3)
    t.fill(0, 0, 0, 11, 0, 2, "minecraft:stone")
    return t


def field():
    """A game-test field for Hollows: 16 by 16 with a stone floor and 11 blocks of air above."""
    t = Template(16, 12, 16)
    t.fill(0, 0, 0, 15, 0, 15, "minecraft:stone")
    return t


# The Gotei 13's divisions in DivisionBannerBlock.Division's order: the names a banner's division takes, and a file's.
DIVISIONS = ("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh",
             "twelfth", "thirteenth")
# A compound's side, as SeireiteiPlan.BARRACKS_SIZE has it (2 by 2 chunks on the plan's chunk lines), and its height.
BARRACKS_SIZE, BARRACKS_HEIGHT = 32, 10
# The gateway's columns in the north wall; a banner hangs on the post either side of it.
GATEWAY = range(14, 18)
# Blocks full on every face: water stays beside them, and a shōji joins them.
SOLID = frozenset({"bleach:black_plaster", "bleach:white_plaster", "bleach:roof_tiles", "minecraft:calcite",
                   "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:stripped_dark_oak_log",
                   "minecraft:dark_oak_planks"})
SIDES = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


def hip_roof(t, x0, z0, x1, z1, y):
    """A roof over the walls between two corners: tiles inside an eave of slabs one beyond, stepping in to a ridge."""
    slab = dict(block="bleach:roof_tile_slab", type="bottom", waterlogged="false")
    x0, z0, x1, z1 = x0 - 1, z0 - 1, x1 + 1, z1 + 1
    while x0 <= x1 and z0 <= z1:
        ring(t, y, x0, z0, x1, z1, **slab)
        t.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, "bleach:roof_tiles")
        x0, z0, x1, z1, y = x0 + 2, z0 + 2, x1 - 2, z1 - 2, y + 1


def hall(t, x0, z0, x1, z1, top, lanterns):
    """
    A building of the Academy's kind between two corners: tatami on stone, and walls of dark posts at the corners and
    every fourth block in from them, black plaster at the foot, shōji above and white plaster under a ceiling at top + 1.
    Shōji doors open in the middle bay of its north and south walls, lanterns hang at these x and a tiled roof tops it.
    """
    log = dict(block="minecraft:stripped_dark_oak_log", axis="y")
    t.fill(x0, 0, z0, x1, 0, z1, "minecraft:stone_bricks")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            t.set(x, 0, z, "bleach:tatami", facing="north" if (x + z) % 2 == 0 else "east")
    for y in range(1, top + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                on_x, on_z = x in (x0, x1), z in (z0, z1)
                if on_z and min(x - x0, x1 - x) % 4 == 0 or on_x and min(z - z0, z1 - z) % 4 == 0:
                    t.set(x, y, z, **log)
                elif on_x or on_z:
                    t.set(x, y, z, "bleach:black_plaster" if y == 1 else "bleach:shoji" if y < top
                          else "bleach:white_plaster")
    doors = range((x0 + x1) // 2, (x0 + x1 + 1) // 2 + 1)
    for z, facing in ((z0, "south"), (z1, "north")):
        for x in doors:
            hinge = "left" if len(doors) == 1 or (x == doors[-1]) == (facing == "south") else "right"
            for y, half in ((1, "lower"), (2, "upper")):
                t.set(x, y, z, "bleach:shoji_door", facing=facing, half=half, hinge=hinge, open="false", powered="false")
    t.fill(x0, top + 1, z0, x1, top + 1, z1, "minecraft:dark_oak_planks")
    for x in lanterns:
        t.set(x, top, (z0 + z1) // 2, "minecraft:lantern", hanging="true", waterlogged="false")
    hip_roof(t, x0, z0, x1, z1, top + 2)


def join_panes(t):
    """Joins each shōji to the shōji and full blocks beside it, as the game joins a pane it places."""
    for (x, y, z), (block, _) in list(t.blocks.items()):
        if block == "bleach:shoji":
            joined = {side: str(t.get(x + dx, y, z + dz) in SOLID | {block}).lower() for side, (dx, dz) in SIDES.items()}
            t.set(x, y, z, block, waterlogged="false", **joined)


def barracks(number):
    """
    A division's barracks, 2 by 2 chunks where the plan sets it: a walled calcite yard entered through a roofed gate in
    the north wall with the division's banner on each post, a main hall and two quarters, and the First's larger hall
    with a dais, the Fourth's beds, the Twelfth's lab benches or the Thirteenth's pond. Row z 0 lies outside the wall,
    and each bed and the pond lie in one chunk, so each stands whole or not at all where a compound meets older ground.
    """
    size, last, gate = BARRACKS_SIZE, BARRACKS_SIZE - 1, GATEWAY
    t = Template(size, BARRACKS_HEIGHT, size)
    log = "minecraft:stripped_dark_oak_log"
    stair = dict(block="bleach:roof_tile_stairs", half="bottom", shape="straight", waterlogged="false")
    slab = dict(type="bottom", waterlogged="false")

    # The yard, the path from the gate to the quarters, and the step up to the gate from the lawn.
    t.fill(1, 0, 2, last - 1, 0, last - 1, "minecraft:calcite")
    t.fill(gate[0], 0, 2, gate[-1], 0, last - 1, "minecraft:stone_bricks")
    t.fill(gate[0], 0, 0, gate[-1], 0, 0, "minecraft:stone_brick_slab", **slab)
    # The wall from z 1 round: two courses of black plaster, two of white and one of tiles.
    for y, block in enumerate(("bleach:black_plaster",) * 2 + ("bleach:white_plaster",) * 2 + ("bleach:roof_tiles",)):
        ring(t, y, 0, 1, last, last, block)
    # The gate, four wide and three high between dark posts, a banner on each, under a lintel and a little roof.
    t.fill(gate[0], 1, 1, gate[-1], 3, 1, "minecraft:air")
    for x in (gate[0] - 1, gate[-1] + 1):
        t.fill(x, 1, 1, x, 3, 1, log, axis="y")
        t.set(x, 2, 0, "bleach:division_banner", division=DIVISIONS[number - 1], facing="north")
        t.set(x - 1 if x < gate[0] else x + 1, 1, 3, "minecraft:lantern", hanging="false", waterlogged="false")
    t.fill(gate[0] - 1, 4, 1, gate[-1] + 1, 4, 1, log, axis="x")
    for z, facing in ((0, "south"), (2, "north")):
        t.fill(gate[0] - 3, 5, z, gate[-1] + 3, 5, z, facing=facing, **stair)
    t.fill(gate[0] - 3, 5, 1, gate[-1] + 3, 5, 1, "bleach:roof_tiles")
    t.fill(gate[0] - 3, 6, 1, gate[-1] + 3, 6, 1, "bleach:roof_tile_slab", **slab)

    # The main hall, the First's the larger, and the two quarters behind it.
    if number == 1:
        hall(t, 2, 9, last - 2, 21, 4, (7, 12, 19, 24))
        t.fill(11, 1, 18, 20, 1, 19, "minecraft:dark_oak_slab", **slab)  # the dais, short of the back door
    else:
        hall(t, 6, 11, last - 6, 19, 4, (10, 21))
    for x0 in (2, last - 12):
        hall(t, x0, 25, x0 + 10, 29, 3, (x0 + 5,))
    if number == 4:  # six beds, heads to the hall's back wall, either side of the aisle
        for x in (8, 10, 12, 19, 21, 23):
            for z, part in ((17, "foot"), (18, "head")):
                t.set(x, 1, z, "minecraft:white_bed", facing="south", occupied="false", part=part)
    if number == 12:  # four lab benches, each with a brewing stand and a cauldron on it
        for z in (14, 16):
            for x0, stand, cauldron in ((8, 9, 11), (19, 22, 20)):
                t.fill(x0, 1, z, x0 + 4, 1, z, "minecraft:smooth_stone_slab", type="top", waterlogged="false")
                t.set(stand, 2, z, "minecraft:brewing_stand", has_bottle_0="false", has_bottle_1="false",
                      has_bottle_2="false")
                t.set(cauldron, 2, z, "minecraft:cauldron")
    if number == 13:  # the pond west of the path, on the lawn under the yard, in a rim of mossy stone
        ring(t, 0, 2, 3, 10, 9, "minecraft:mossy_stone_bricks")
        t.fill(3, 0, 4, 9, 0, 8, "minecraft:water", level="0")
        for x, z in ((4, 5), (7, 7), (8, 4)):
            t.set(x, 1, z, "minecraft:lily_pad")
    join_panes(t)
    # Water stays where it is set, and a bed whole: beside the water only water or full blocks, under it the lawn,
    # and each bed and the pond in one chunk, each half of a bed with its partner.
    for (x, y, z), (block, properties) in t.blocks.items():
        square = (x // 16, z // 16)
        if block == "minecraft:water":
            for nx, nz in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
                if t.get(nx, y, nz) not in SOLID | {block} or (nx // 16, nz // 16) != square or y != 0:
                    raise ValueError(f"the pond's water at {(x, y, z)} could run out by {(nx, y, nz)}")
        if block == "minecraft:white_bed":
            state = dict(properties)
            dx, dz = SIDES[state["facing"]]
            if state["part"] == "head":  # the foot lies behind the head, the head ahead of the foot
                dx, dz = -dx, -dz
            if t.get(x + dx, y, z + dz) != block or ((x + dx) // 16, (z + dz) // 16) != square:
                raise ValueError(f"the bed at {(x, y, z)} has no partner beside it in its chunk")
    return t


TEMPLATES = {
    "academy/academy.nbt": academy,
    "field.nbt": field,
    "gate/house.nbt": gate_house,
    "lane.nbt": lane,
}
TEMPLATES.update((f"barracks/{name}.nbt", functools.partial(barracks, number))
                 for number, name in enumerate(DIVISIONS, 1))


def main(argv):
    check = "--check" in argv
    stale = []
    for name, build in TEMPLATES.items():
        expected = build().to_nbt()
        path = STRUCTURES / name
        if check:
            try:
                actual = gzip.decompress(path.read_bytes())
            except (OSError, EOFError, zlib.error) as error:
                stale.append("%s: %s" % (path, error))
                continue
            if actual != expected:
                stale.append("%s: differs from what this script builds" % path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(gzip.compress(expected, mtime=0))
            print("wrote", path)
    if stale:
        print("Structure templates are out of date. Run python3 tools/structures/make_structures.py", file=sys.stderr)
        for line in stale:
            print("  " + line, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
