"""Builds the mod's structure templates (.nbt) from code, so they can be reviewed and rebuilt.

Run from the repository root:

    python3 tools/structures/make_structures.py          # write every template
    python3 tools/structures/make_structures.py --check  # fail if a committed template differs

The check compares the uncompressed NBT, not the gzip bytes, so a different zlib build does not trip
it. Templates are written for Minecraft 1.21.1 (data version 3955) and list every position in their
box, air included, so placing one clears whatever was there, but for any position left as structure_void, which keeps
the ground it is set over. A block may carry block entity data, as a structure block saves a sign's text.

Uses only the standard library.
"""
import functools
import gzip
import json
import math
import struct
import sys
import zlib
from pathlib import Path

STRUCTURES = Path("src/main/resources/data/bleach/structure")
DATA_VERSION = 3955
VOID = "minecraft:structure_void"  # left out of a template, as a structure block leaves it: the ground there stays

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
        self.block_data = {}
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

    def set_data(self, x, y, z, data):
        """Gives the block set here this block entity data, a compound, as a structure block saves it."""
        self.block_data[(x, y, z)] = data

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
                    if state[0] == VOID:
                        continue
                    if state not in index:
                        index[state] = len(palette)
                        name, properties = state
                        entry = {"Name": string(name)}
                        if properties:
                            entry["Properties"] = compound(**{k: string(v) for k, v in properties})
                        palette.append(compound(**entry))
                    entry = dict(pos=ints(x, y, z), state=integer(index[state]))
                    if (x, y, z) in self.block_data:
                        entry["nbt"] = self.block_data[(x, y, z)]
                    blocks.append(compound(**entry))
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


# The Academy's grounds, set on the lawn where SeireiteiPlan.java's academy rows put them. A dummy faces the way given,
# in the game's degrees (0 south, 90 west, 180 north, 270 east), before its site's turn.
SLAB_STEP = dict(block="minecraft:stone_brick_slab", type="bottom", waterlogged="false")


def dummy(t, x, z, facing, y=1):
    """A training dummy on its post of dark oak at (x, y - 1, z). Spot tells it to take its UUID from where it stands."""
    t.set(x, y - 1, z, "minecraft:stripped_dark_oak_log", axis="y")
    t.add_entity(x + 0.5, float(y), z + 0.5, id=string("bleach:training_dummy"), Rotation=floats(float(facing), 0.0),
                 Spot=byte(1))


def lamp(t, x, z, y=1):
    """A stone lantern: a lantern on a post of stone brick wall."""
    t.set(x, y, z, "minecraft:stone_brick_wall", east="none", north="none", south="none", up="true",
          waterlogged="false", west="none")
    t.set(x, y + 1, z, "minecraft:lantern", hanging="false", waterlogged="false")


def dojo():
    """
    The dojo west of the hall: a building of the Academy's kind with a step before the door in each long side, and a
    training dummy in each end bay, facing the middle once the site is turned.
    """
    t = Template(21, 10, 15)
    hall(t, 1, 1, 19, 13, 4, (5, 15))
    for z in (0, 14):
        t.set(10, 0, z, **SLAB_STEP)
    dummy(t, 4, 7, 270)
    dummy(t, 16, 7, 90)
    join_panes(t)
    return t


def yard():
    """
    The yard south of the hall: calcite on the lawn with a step of slabs along its north edge, the side the hall is on, a
    stone path down its middle, a stone lantern at each corner and eight training dummies in two rows, facing north.
    """
    t = Template(24, 5, 16)
    t.fill(0, 0, 0, 23, 0, 0, **SLAB_STEP)
    t.fill(0, 0, 1, 23, 0, 15, "minecraft:calcite")
    t.fill(11, 0, 1, 12, 0, 15, "minecraft:stone_bricks")
    for x in (0, 23):
        for z in (1, 15):
            lamp(t, x, z)
    for x in (5, 9, 14, 18):
        for z in (5, 10):
            dummy(t, x, z, 180)
    return t


def kido_range():
    """
    The Kidō range west of the yard, unturned: a platform of dark oak at its east end with a lantern at each outer
    corner, two dummies on the lawn 5 and 10 blocks out between plaster walls, and 19 out a backstop of mud brick with a
    target for each of three lanes.
    """
    t = Template(26, 8, 15)
    t.fill(0, 0, 0, 1, 6, 14, "minecraft:mud_bricks")
    t.fill(0, 7, 0, 1, 7, 14, "bleach:roof_tile_slab", type="bottom", waterlogged="false")
    for z in (2, 7, 12):
        t.set(1, 2, z, "minecraft:target", power="0")
    for y, block in enumerate(("bleach:black_plaster", "bleach:white_plaster", "bleach:white_plaster",
                               "bleach:roof_tiles")):
        for z in (0, 14):
            t.fill(2, y, z, 20, y, z, block)
    t.fill(21, 0, 0, 25, 0, 14, "minecraft:dark_oak_slab", type="bottom", waterlogged="false")
    for z in (0, 14):
        lamp(t, 25, z, 0)
    dummy(t, 16, 4, 270)
    dummy(t, 11, 10, 270)
    return t


# Sōkyoku Hill's works, as SeireiteiPlan.java's hill row has them: one template at the corner (111, 72, 5), turned
# CLOCKWISE_180, so world x = 111 - template x and world z = 5 - template z; the hill's cliff at radius 64, its top at 136.
HILL_CORNER, HILL_SIZE = (111, 5), (53, 95, 12)
MESA_RADIUS, MESA_TOP_Y = 64, 136
TURNED = {"north": "south", "south": "north", "east": "west", "west": "east"}


def senzaikyu():
    """
    Sōkyoku Hill's works, set out in the world's blocks from the hill's east end to the lawn east of it: the stand on
    the end, whose underside is cut back, a bridge at the hill's height, and the Senzaikyū, a Sekkiseki tower whose
    stair climbs round a solid core from the bridge's door to the cell, its window high and barred toward the stand.
    The hill's own blocks are left as they stand, but for that cut and the stand's floor.
    """
    t = Template(*HILL_SIZE)

    def put(x, y, z, block, **properties):
        t.set(HILL_CORNER[0] - x, y - PLATEAU_Y, HILL_CORNER[1] - z, block, **properties)

    def box(x0, y0, z0, x1, y1, z1, block, **properties):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    put(x, y, z, block, **properties)

    sek, air, log = "bleach:sekkiseki", "minecraft:air", "minecraft:stripped_dark_oak_log"
    west, north, south = HILL_CORNER[0] - HILL_SIZE[0] + 1, HILL_CORNER[1] - HILL_SIZE[2] + 1, HILL_CORNER[1]
    # The hill's end: its cliff cut back under a lip from y 130, 1 deeper each 4 rows from y 102 to 5 at y 118.
    for x in range(west, MESA_RADIUS):
        for z in range(north, south + 1):
            if math.hypot(x + 0.5, z + 0.5) >= MESA_RADIUS:
                raise ValueError(f"the hill's end at {(x, z)} is off the hill")
            for y in range(PLATEAU_Y, MESA_TOP_Y - 1):
                cut = y < 130 and MESA_RADIUS - x <= min(5, max(0, (y - 98) // 4))
                put(x, y, z, air if cut else VOID)
            put(x, MESA_TOP_Y - 1, z, "minecraft:stone_bricks")
    # The Sōkyoku's stand, for show: two posts under a beam and a tiled cap, the halberd upright between them.
    for z in (-4, 3):
        box(61, 136, z, 61, 152, z, log, axis="y")
    box(61, 153, -4, 61, 153, 3, log, axis="z")
    box(60, 154, -5, 62, 154, 4, "bleach:roof_tile_slab", type="bottom", waterlogged="false")
    box(61, 136, -1, 61, 149, 0, log, axis="y")
    box(61, 150, -2, 61, 151, 1, "minecraft:smooth_stone")
    box(61, 152, -1, 61, 152, 0, "minecraft:smooth_stone")
    # The bridge: a calcite deck level with the hill's top from its cliff to the tower, a plaster rail each side.
    box(MESA_RADIUS, MESA_TOP_Y - 1, -2, 99, MESA_TOP_Y - 1, 1, "minecraft:calcite")
    for z in (-2, 1):
        box(MESA_RADIUS, MESA_TOP_Y, z, 99, MESA_TOP_Y, z, "bleach:white_plaster")
    # The tower, 12 square from the lawn to the cell's roof: a stairwell round a core 6 square, the cell over both.
    box(100, PLATEAU_Y, north, HILL_CORNER[0], 164, south, sek)
    box(102, 136, -4, 109, 158, 3, air)
    box(103, 136, -3, 108, 158, 2, sek)
    box(102, 160, -4, 109, 163, 3, air)
    # The stair, from the landing inside the door round the core, Sekkiseki under each step, the cell's floor opened
    # over the last; each step faces the way up, written turned back for the template's turn.
    steps = [(102, z) for z in range(1, 4)] + [(x, 3) for x in range(103, 110)] \
        + [(109, z) for z in range(2, -5, -1)] + [(x, -4) for x in range(108, 101, -1)]
    last = (102, 0)
    for k, (x, z) in enumerate(steps, 1):
        way = {(0, 1): "south", (1, 0): "east", (0, -1): "north", (-1, 0): "west"}[(x - last[0], z - last[1])]
        box(x, 136, z, x, 134 + k, z, sek)
        put(x, 135 + k, z, "minecraft:polished_diorite_stairs", facing=TURNED[way], half="bottom", shape="straight",
            waterlogged="false")
        if 156 <= 135 + k < 159:
            put(x, 159, z, air)
        last = (x, z)
    for z in (-1, 0):
        box(100, 136, z, 101, 138, z, air)  # the bridge's door, 2 wide and 3 high
        put(101, 163, z, air)  # the window, one row high under the ceiling in the west wall, barred in its outer course
        put(100, 163, z, "minecraft:iron_bars", east="false", north="true", south="true", waterlogged="false",
            west="false")
    # The crown: a parapet round the roof, a merlon on every other block.
    for x in range(100, HILL_CORNER[0] + 1):
        for z in range(north, south + 1):
            if x in (100, 111) or z in (north, south):
                put(x, 165, z, sek)
                if (x + z) % 2 == 0:
                    put(x, 166, z, sek)
    return t


# Sōkyoku Hill's other works, each unturned at its corner (world = corner + template), as SeireiteiPlan.java's hill rows
# set them: the zigzag stair in the north cliff, the cave in the west cliff with its stair down, and the cavern's dressing.
STAIR_CORNER, STAIR_SIZE = (-10, PLATEAU_Y, -64), (20, 67, 5)
CAVE_CORNER, CAVE_SIZE = (-64, 56, 0), (20, 19, 2)
CAVERN_CORNER, CAVERN_SIZE = (-31, 54, -31), (62, 54, 62)
CAVERN_FLOOR_Y = 56  # the cavern's air: r < 44 from here up to, not including, y 120
STEP = dict(block="minecraft:stone_stairs", half="bottom", shape="straight", waterlogged="false")


def carved(size, corner):
    """A template that keeps the ground wherever it sets nothing, and a put that sets a world block from this corner."""
    t = Template(*size)
    t.fill(0, 0, 0, size[0] - 1, size[1] - 1, size[2] - 1, VOID)

    def put(x, y, z, block, **properties):
        t.set(x - corner[0], y - corner[1], z - corner[2], block, **properties)
    return t, put


def stair():
    """
    The zigzag stair cut into the hill's north cliff from the lawn to the top: four flights of 16 steps turning on
    landings at each end, behind the cliff's own rock with a slit at eye height. A flight's first steps run in a lane cut
    behind the one below it, so the lower flight keeps its headroom.
    """
    t, put = carved(STAIR_SIZE, STAIR_CORNER)
    west, east, inner = STAIR_CORNER[0], STAIR_CORNER[0] + STAIR_SIZE[0] - 1, STAIR_CORNER[2] + STAIR_SIZE[2] - 1
    face = {x: min(z for z in range(STAIR_CORNER[2], inner + 1) if math.hypot(x + 0.5, z + 0.5) < MESA_RADIUS)
            for x in range(west, east + 1)}

    def cut(x, feet, lanes, slit):
        for z in lanes:
            for y in range(feet, feet + 3):
                put(x, y, z, "minecraft:air")
        if slit and feet + 2 < MESA_TOP_Y:  # rock under it and over it, so no soul climbs out
            put(x, feet + 1, face[x], "minecraft:air")

    for x in (east - 1, east):  # the foot, open to the lawn
        cut(x, PLATEAU_Y, range(face[x], inner + 1), False)
    for feet in range(PLATEAU_Y + 16, MESA_TOP_Y, 16):  # a landing at the west end, then the east, then the west
        for x in (west, west + 1) if feet % 32 == 24 else (east - 1, east):
            cut(x, feet, range(face[x] + 1, inner + 1), True)
    for flight in range(4):  # west on the first and third, east on the second and fourth
        for k, x in enumerate(range(east - 2, west + 1, -1) if flight % 2 == 0 else range(west + 2, east - 1), 1):
            feet = PLATEAU_Y + 16 * flight + k
            lane = range(face[x] + 1, face[x] + 3) if flight == 0 or k > 3 \
                else range(face[x] + 3, inner + 1) if k < 3 else range(face[x] + 1, inner + 1)
            cut(x, feet, lane, lane[0] == face[x] + 1)
            for z in lane:
                put(x, feet - 1, z, facing="west" if flight % 2 == 0 else "east", **STEP)
    return t


def cave():
    """
    The small cave in the hill's west cliff at the lawn and the stair from it down to the cavern's floor: a mouth 2 wide
    and 3 high, 16 steps of the hill's stone down to the east and a level way out onto the floor, lit from its roof.
    """
    t, put = carved(CAVE_SIZE, CAVE_CORNER)
    mouth = CAVE_CORNER[0]
    for x in range(mouth, mouth + CAVE_SIZE[0]):
        feet = max(CAVERN_FLOOR_Y, min(PLATEAU_Y, PLATEAU_Y + mouth + 1 - x))
        for z in range(CAVE_CORNER[2], CAVE_CORNER[2] + CAVE_SIZE[2]):
            for y in range(feet, feet + 3):
                put(x, y, z, "minecraft:air")
            if feet > CAVERN_FLOOR_Y and x > mouth:  # a step under each rise, so a soul walks up it as down
                put(x, feet - 1, z, facing="west", **STEP)
            if (x - mouth) % 5 == 3:
                put(x, feet + 3, z, "minecraft:shroomlight")
    return t


# The cavern's spires, each (x, z, radius at the floor, height); the hot spring's water (x0, z0, x1, z1), sunk into the
# floor; the floor's patches; and what holds the spring's water in: its rim and its bed.
SPIRES = ((-22, -18, 4, 46), (-14, 14, 3, 30), (-6, -26, 3, 22), (2, 4, 5, 52), (10, -14, 3, 34), (12, 22, 4, 40),
          (22, -2, 3, 26), (24, -22, 4, 44), (-24, 8, 3, 18), (27, 14, 2, 16))
SPRING = (17, 4, 22, 9)
PATCHES = ("minecraft:andesite", "minecraft:tuff", "minecraft:cobblestone")
HOLDS_WATER = frozenset({"minecraft:mossy_cobblestone", "minecraft:stone", "minecraft:shroomlight"})


def scatter(x, z):
    """A fixed hash of a column, for patches that fall the same on every run."""
    v = (x * 374761393 + z * 668265263) & 0xFFFFFFFF
    v = (v ^ (v >> 13)) * 1274126177 & 0xFFFFFFFF
    return v ^ (v >> 16)


def cavern():
    """
    The cavern's dressing: patches of andesite, tuff and cobblestone in its stone floor, thinning out toward the edge,
    spires of banded rock with lights round their feet, and the hot spring sunk in the floor in a rim of mossy stone,
    lit from beneath. The cavern's air, walls and roof are left as they stand.
    """
    t, put = carved(CAVERN_SIZE, CAVERN_CORNER)
    x0, y0, z0 = CAVERN_CORNER
    floor, last = CAVERN_FLOOR_Y - 1, CAVERN_SIZE[0] - 1
    for x in range(x0, x0 + CAVERN_SIZE[0]):
        for z in range(z0, z0 + CAVERN_SIZE[2]):
            edge = min(x - x0, last - (x - x0), z - z0, last - (z - z0))
            h = scatter(x // 2, z // 2) % 9  # 2-by-2 patches, a third of the floor away from the edge
            if h < min(3, edge // 4):
                put(x, floor, z, PATCHES[h])
    for cx, cz, base, height in SPIRES:
        for y in range(CAVERN_FLOOR_Y, CAVERN_FLOOR_Y + height):
            r = base * (1 - (y - CAVERN_FLOOR_Y) / height) ** 0.7 + 0.5
            for x in range(cx - base, cx + base + 1):
                for z in range(cz - base, cz + base + 1):
                    if (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                        put(x, y, z, "minecraft:andesite" if (y // 4) % 3 == 1 else "minecraft:stone")
        reach = int(base * (1 - 1 / height) ** 0.7 + 0.5)  # its half-width a block up, where a light faces each way
        for dx, dz in SIDES.values():
            put(cx + dx * reach, CAVERN_FLOOR_Y + 1, cz + dz * reach, "minecraft:shroomlight")
    sx0, sz0, sx1, sz1 = SPRING
    for x in range(sx0 - 1, sx1 + 2):
        for z in range(sz0 - 1, sz1 + 2):
            if sx0 <= x <= sx1 and sz0 <= z <= sz1:
                put(x, floor, z, "minecraft:water", level="0")
                put(x, floor - 1, z, "minecraft:shroomlight" if (x + z) % 3 == 0 else "minecraft:stone")
            else:
                put(x, floor, z, "minecraft:mossy_cobblestone")
    for i in range(16):  # a marker in the floor of each chunk the dressing covers
        t.set(1 + 16 * (i % 4), floor - y0, 1 + 16 * (i // 4), "minecraft:polished_andesite")
    # The spring stays where it is set: beside and under its water only water, its rim or its bed, all in one chunk.
    for (x, y, z), (block, _) in t.blocks.items():
        if block == "minecraft:water":
            for nx, ny, nz in ((x - 1, y, z), (x + 1, y, z), (x, y, z - 1), (x, y, z + 1), (x, y - 1, z)):
                if t.get(nx, ny, nz) not in HOLDS_WATER | {block} \
                        or ((nx + x0) >> 4, (nz + z0) >> 4) != ((x + x0) >> 4, (z + z0) >> 4):
                    raise ValueError(f"the spring's water at {(x + x0, y + y0, z + z0)} could run out")
    return t


def sign_lines(*lines):
    """A sign face's four lines as SignText saves them, each a component as JSON: a dict, or a string as plain text."""
    return compound(messages=Tag(LIST, [string(json.dumps(line)) for line in lines], STRING), color=string("black"),
                    has_glowing_text=byte(0))


def urahara_shop():
    """
    The stand-in for the Urahara Shop: a small two-storey sweets shop of dark posts, plaster and shōji under a tiled hip
    roof, its open front (low z) on a paved strip under a lower eave, with a waxed sign over the door. Inside, shelves
    of barrels and bookcases line a packed-mud floor before a counter, behind which Kisuke Urahara stands facing the door;
    a shōji door leads to a tatami room with a ladder up to the room above. The floor at (2, 0, 9), behind the counter's
    west end, is kept clear above for a hatch to a room beneath. The ground beside and behind the walls stays.
    """
    t = Template(13, 13, 16)
    log = dict(block="minecraft:stripped_dark_oak_log", axis="y")
    pane = dict(block="minecraft:glass_pane", east="true", north="false", south="false", west="true",
                waterlogged="false")
    hang = dict(block="minecraft:lantern", hanging="true", waterlogged="false")

    # Ground: a paved strip before the shop; its walls' footing; packed mud in the shop, planks behind the counter and
    # tatami in the back room, the weave crossed. Under the side and back eaves the land is kept.
    t.fill(0, 0, 0, 12, 0, 1, "minecraft:stone_bricks")
    t.fill(1, 0, 2, 11, 0, 14, "minecraft:stone_bricks")
    t.fill(2, 0, 3, 10, 0, 7, "minecraft:packed_mud")
    t.fill(2, 0, 8, 10, 0, 10, "minecraft:spruce_planks")
    for x in range(2, 11):
        for z in range(11, 14):
            t.set(x, 0, z, "bleach:tatami", facing="north" if (x + z) % 2 == 0 else "east")
    for z in range(2, 16):
        t.set(0, 0, z, VOID)
        t.set(12, 0, z, VOID)
    t.fill(1, 0, 15, 11, 0, 15, VOID)

    # Both storeys' walls: posts at the corners and between bays, black plaster at the foot, white plaster above.
    for y in range(1, 9):
        if y == 5:
            continue
        for x in range(1, 12):
            for z in range(2, 15):
                on_x, on_z = x in (1, 11), z in (2, 14)
                if not (on_x or on_z):
                    continue
                post = on_x and on_z or on_z and x in ((4, 8) if z == 2 and y < 5 else (6,)) or on_x and z in (6, 10)
                if post:
                    t.set(x, y, z, **log)
                else:
                    t.set(x, y, z, "bleach:black_plaster" if y == 1 else "bleach:white_plaster")
    # The band between the storeys, and the lower eave over the paved strip.
    ring(t, 5, 1, 2, 11, 14, "minecraft:dark_oak_planks")
    t.fill(0, 5, 1, 12, 5, 1, "bleach:roof_tile_slab", type="bottom", waterlogged="false")

    # The shopfront: glass either side of a doorway three wide and three high, under a lintel, and the sign over it.
    for x in (2, 3, 9, 10):
        t.fill(x, 2, 2, x, 3, 2, **pane)
    t.fill(5, 1, 2, 7, 3, 2, "minecraft:air")
    for x in (2, 3, 5, 6, 7, 9, 10):
        t.set(x, 4, 2, "minecraft:dark_oak_planks")
    t.set(6, 4, 1, "minecraft:spruce_wall_sign", facing="north", waterlogged="false")
    t.set_data(6, 4, 1, compound(id=string("minecraft:sign"), is_waxed=byte(1),
                                 front_text=sign_lines("", {"translate": "sign.bleach.urahara_shop"},
                                                       {"translate": "sign.bleach.urahara_shop.sweets"}, "")))

    # Windows of shōji: the back room's sides and back, and the upper room's front, sides and back.
    for z in range(11, 14):
        for x in (1, 11):
            t.fill(x, 2, z, x, 3, z, "bleach:shoji")
    for x in (3, 4, 5, 7, 8, 9):
        t.fill(x, 2, 14, x, 3, 14, "bleach:shoji")
    for x in (2, 3, 4, 5, 7, 8, 9, 10):
        t.set(x, 7, 2, "bleach:shoji")
    for z in (4, 5, 11, 12):
        for x in (1, 11):
            t.set(x, 7, z, "bleach:shoji")
    for x in (3, 4, 5):
        t.set(x, 7, 14, "bleach:shoji")

    # The shop: shelves of barrels under empty bookcases along both sides, two bins on the floor, a lantern overhead.
    for z in range(3, 8):
        t.set(2, 1, z, "minecraft:barrel", facing="up", open="false")
        t.set(10, 1, z, "minecraft:barrel", facing="up", open="false")
        for x, facing in ((2, "east"), (10, "west")):
            t.set(x, 2, z, "minecraft:chiseled_bookshelf", facing=facing,
                  **{"slot_%d_occupied" % slot: "false" for slot in range(6)})
    for x in (4, 8):
        t.set(x, 1, 5, "minecraft:barrel", facing="up", open="false")
    t.set(6, 4, 5, **hang)

    # The counter, open at its east end, with a lantern and a cake on it; behind it the keeper's floor and the hatch's.
    t.fill(2, 1, 8, 7, 1, 8, "minecraft:stripped_spruce_log", axis="x")
    t.set(3, 2, 8, "minecraft:lantern", hanging="false", waterlogged="false")
    t.set(6, 2, 8, "minecraft:cake", bites="0")

    # The partition of shōji with its door, and the back room: a low table, a lantern and the ladder up.
    for x in range(2, 11):
        if x != 6:
            t.fill(x, 1, 10, x, 3, 10, "bleach:shoji")
    t.set(6, 3, 10, "bleach:shoji")
    door = dict(block="bleach:shoji_door", facing="south", hinge="left", open="false", powered="false")
    t.set(6, 1, 10, half="lower", **door)
    t.set(6, 2, 10, half="upper", **door)
    t.fill(2, 4, 10, 10, 4, 10, "minecraft:dark_oak_planks")
    t.fill(5, 1, 12, 7, 1, 12, "minecraft:dark_oak_slab", type="bottom", waterlogged="false")
    t.set(6, 4, 12, **hang)

    # The upper floor of tatami, the ladder through it, and the upper room: a barrel, a futon and a lantern.
    for x in range(2, 11):
        for z in range(3, 14):
            t.set(x, 5, z, "bleach:tatami", facing="north" if (x + z) % 2 == 0 else "east")
    for y in range(1, 6):
        t.set(10, y, 13, "minecraft:ladder", facing="north", waterlogged="false")
    t.set(2, 6, 13, "minecraft:barrel", facing="up", open="false")
    t.fill(7, 6, 11, 8, 6, 12, "minecraft:white_carpet")
    t.set(6, 8, 8, **hang)

    hip_roof(t, 1, 2, 11, 14, 9)
    join_panes(t)
    t.add_entity(4.5, 1.0, 9.5, id=string("bleach:kisuke_urahara"), Rotation=floats(180.0, 0.0),
                 PersistenceRequired=byte(1), Invulnerable=byte(1), Spot=byte(1))
    return t


TEMPLATES = {
    "academy/academy.nbt": academy,
    "academy/dojo.nbt": dojo,
    "academy/range.nbt": kido_range,
    "academy/yard.nbt": yard,
    "field.nbt": field,
    "gate/house.nbt": gate_house,
    "hill/cave.nbt": cave,
    "hill/cavern.nbt": cavern,
    "hill/senzaikyu.nbt": senzaikyu,
    "hill/stair.nbt": stair,
    "lane.nbt": lane,
    "urahara_shop.nbt": urahara_shop,
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
