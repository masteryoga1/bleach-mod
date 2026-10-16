"""Builds the mod's structure templates (.nbt) from code, so they can be reviewed and rebuilt.

Run from the repository root:

    python3 tools/structures/make_structures.py          # write every template
    python3 tools/structures/make_structures.py --check  # fail if a committed template differs

The check compares the uncompressed NBT, not the gzip bytes, so a different zlib build does not trip
it. Templates are written for Minecraft 1.21.1 (data version 3955) and list every position in their
box, air included, so placing one clears whatever was there.

Uses only the standard library.
"""
import gzip
import struct
import sys
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
    slab = dict(block="minecraft:deepslate_tile_slab", type="bottom", waterlogged="false")

    # Ground: a calcite courtyard with a stone path, the hall on stone with tatami inside.
    t.fill(0, 0, 0, 14, 0, 5, "minecraft:calcite")
    t.fill(6, 0, 0, 8, 0, 5, "minecraft:stone_bricks")
    t.fill(0, 0, 6, 14, 0, 16, "minecraft:stone_bricks")
    t.fill(2, 0, 8, 12, 0, 14, "minecraft:bamboo_mosaic")

    # The gate: two posts, a lintel and a tiled cap.
    for x in (5, 9):
        t.fill(x, 1, 0, x, 3, 0, **log)
    t.fill(4, 4, 0, 10, 4, 0, "minecraft:dark_oak_planks")
    t.fill(4, 5, 0, 10, 5, 0, **slab)
    for x in (3, 11):
        t.set(x, 1, 3, "minecraft:lantern", hanging="false", waterlogged="false")

    # Hall walls: plaster between pillars, with shōji in the middle two rows of each bay.
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
                    t.set(x, y, z, "minecraft:white_stained_glass")
                else:
                    t.set(x, y, z, "minecraft:white_concrete")
    door = dict(block="minecraft:bamboo_door", facing="south", hinge="left", open="false", powered="false")
    t.set(7, 1, 7, half="lower", **door)
    t.set(7, 2, 7, half="upper", **door)

    # Inside: a ceiling, the Tailoring Table and two hanging lanterns.
    t.fill(1, 5, 7, 13, 5, 15, "minecraft:dark_oak_planks")
    t.set(3, 1, 13, "bleach:tailoring_table")
    for x in (4, 10):
        t.set(x, 4, 10, "minecraft:lantern", hanging="true", waterlogged="false")

    # The roof, stepping in over the veranda.
    ring(t, 6, 0, 6, 14, 16, **slab)
    t.fill(1, 6, 7, 13, 6, 15, "minecraft:deepslate_tiles")
    ring(t, 7, 2, 8, 12, 14, **slab)
    t.fill(3, 7, 9, 11, 7, 13, "minecraft:deepslate_tiles")
    t.fill(4, 8, 10, 10, 8, 12, **slab)

    t.add_entity(7.5, 1.0, 12.5, id=string("bleach:academy_instructor"), Rotation=floats(180.0, 0.0),
                 PersistenceRequired=byte(1), Invulnerable=byte(1))
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


TEMPLATES = {
    "academy/academy.nbt": academy,
    "field.nbt": field,
    "lane.nbt": lane,
}


def main(argv):
    check = "--check" in argv
    stale = []
    for name, build in TEMPLATES.items():
        expected = build().to_nbt()
        path = STRUCTURES / name
        if check:
            try:
                actual = gzip.decompress(path.read_bytes())
            except OSError as error:
                stale.append("%s: %s" % (path, error))
                continue
            if actual != expected:
                stale.append("%s: differs from what %s() builds" % (path, build.__name__))
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
