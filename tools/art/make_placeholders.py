"""Draws the mod's placeholder textures, so they can be rebuilt and reviewed as code.

Run from the repository root:

    python3 tools/art/make_placeholders.py          # write every texture
    python3 tools/art/make_placeholders.py --check  # fail if a committed texture differs

The check compares decoded pixels, not bytes, so a different zlib build does not trip it. When a
texture gets real art, drop it from TEXTURES below so the check stops covering it.

Uses only the standard library.
"""
import math
import struct
import sys
import zlib
from pathlib import Path

ASSETS = Path("src/main/resources/assets/bleach/textures")

CLEAR = (0, 0, 0, 0)


def rgb(r, g, b):
    return (r, g, b, 255)


# The Asauchi: a plain katana, blade from bottom left to top right like vanilla swords.
EDGE = rgb(232, 236, 240)
STEEL = rgb(170, 178, 188)
SHADE = rgb(110, 116, 126)
GUARD = rgb(200, 160, 60)
WRAP = rgb(40, 36, 60)
DIAMOND = rgb(220, 220, 220)

# The Shihakusho: black kosode and hakama over a white juban, white tabi in straw waraji.
BLACK = rgb(26, 26, 26)
FOLD = rgb(52, 52, 56)
WHITE = rgb(240, 240, 236)
STRAW = rgb(196, 160, 100)
STRAW_DARK = rgb(150, 118, 66)

# Hollows: black hides, bone-white masks, the hole that goes right through them.
HOLLOW_BLACK = rgb(28, 26, 32)
HOLLOW_SHADE = rgb(48, 44, 56)
BONE = rgb(232, 228, 214)
BONE_SHADE = rgb(188, 182, 166)
VOID = rgb(6, 4, 10)
CRACK = rgb(90, 84, 80)
RIFT_EDGE = rgb(70, 40, 90)

# The Hexapodus: a grey-green insect with orange markings, and a lock of hair over its mask.
GREY_GREEN = rgb(104, 116, 92)
GREEN_SHADE = rgb(80, 90, 70)
ORANGE = rgb(214, 120, 40)
HAIR_BROWN = rgb(110, 70, 40)
PURPLE = rgb(130, 60, 160)

# The Serpent Hollow's scales and Fishbone D's fish-grey hide.
SCALE_DARK = rgb(40, 52, 44)
SCALE_LIGHT = rgb(70, 88, 72)
FISH_GREY = rgb(70, 78, 90)
FISH_STRIPE = rgb(50, 56, 66)

# The Denreishinki: a dark flip phone with a green screen.
DEVICE = rgb(44, 44, 52)
DEVICE_EDGE = rgb(80, 80, 92)
SCREEN = rgb(120, 200, 150)
KEY = rgb(170, 170, 180)


class Canvas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.rows = [[CLEAR] * width for _ in range(height)]

    def put(self, x, y, colour):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.rows[y][x] = colour

    def rect(self, x0, y0, x1, y1, colour):
        """Fills x0 <= x < x1, y0 <= y < y1."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, colour)

    def get(self, x, y):
        return self.rows[y][x] if 0 <= x < self.width and 0 <= y < self.height else CLEAR


# Entity models wrap each box in a net laid out from its texture offset (u, v), for a box w wide,
# h high and d deep. Seen from in front of the creature, "right" is the creature's own right.
def faces(u, v, w, h, d):
    """The six faces of a model box's net as (x0, y0, x1, y1) rectangles, ends exclusive."""
    return [
        (u + d, v, u + d + w, v + d),                    # top: the row nearest v + d is the front edge
        (u + d + w, v, u + d + 2 * w, v + d),            # bottom
        (u, v + d, u + d, v + d + h),                    # right: the column nearest u + d is the front edge
        (u + d, v + d, u + d + w, v + d + h),            # front
        (u + d + w, v + d, u + 2 * d + w, v + d + h),    # left: the column nearest u + d + w is the front edge
        (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),  # back
    ]


def box(c, u, v, w, h, d, colour):
    """Paints every face of a model box's net."""
    for x0, y0, x1, y1 in faces(u, v, w, h, d):
        c.rect(x0, y0, x1, y1, colour)


def paint_where(c, rects, colour, test):
    """Paints the pixels of these rectangles that pass test(x, y)."""
    for x0, y0, x1, y1 in rects:
        for y in range(y0, y1):
            for x in range(x0, x1):
                if test(x, y):
                    c.put(x, y, colour)


def mottle(c, x0, y0, x1, y1, colour, every, seed=0):
    """Scatters colour over the opaque pixels of a rectangle, in a fixed pattern."""
    paint_where(c, [(x0, y0, x1, y1)], colour,
                lambda x, y: (x * 7 + y * 13 + seed) % every == 0 and c.get(x, y)[3] != 0)


def line(c, x0, y0, x1, y1, colour):
    """A straight line of pixels between two points, both included."""
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        # Rounds half up, in integers, so the line is the same everywhere.
        x = x0 + ((x1 - x0) * 2 * i + steps) // (2 * steps)
        y = y0 + ((y1 - y0) * 2 * i + steps) // (2 * steps)
        c.put(x, y, colour)


def crack(c, points):
    """A crack along straight segments between the points, and the two pixels after the last one, on
    its row, chipped out."""
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        line(c, x0, y0, x1, y1, CRACK)
    x, y = points[-1]
    c.put(x + 1, y, CLEAR)
    c.put(x + 2, y, CLEAR)


def cracked(base, points):
    """A drawing function for the cracked version of a mask."""
    def draw():
        c = base()
        crack(c, points)
        return c
    draw.__name__ = base.__name__ + "_cracked"
    return draw


def asauchi():
    c = Canvas(16, 16)
    for i in range(11):
        x, y = 5 + i, 10 - i
        c.put(x, y, STEEL)
        c.put(x - 1, y, SHADE if i < 10 else CLEAR)
        c.put(x, y + 1, EDGE if i < 10 else CLEAR)
    c.put(15, 0, EDGE)
    for x, y in [(3, 10), (4, 11), (5, 12), (4, 9), (6, 11), (3, 11), (5, 11)]:
        c.put(x, y, GUARD)
    for i in range(4):
        x, y = 3 - i, 12 + i
        c.put(x, y, WRAP)
        c.put(x + 1, y, WRAP if i < 3 else CLEAR)
        if i % 2 == 0:
            c.put(x, y, DIAMOND)
    c.put(0, 15, GUARD)
    return c


def kosode_icon():
    c = Canvas(16, 16)
    c.rect(1, 3, 15, 10, BLACK)   # sleeves
    c.rect(4, 2, 12, 15, BLACK)   # body
    c.rect(6, 2, 10, 3, CLEAR)    # neck
    c.rect(1, 9, 4, 10, FOLD)     # sleeve hems
    c.rect(12, 9, 15, 10, FOLD)
    c.rect(3, 6, 4, 9, FOLD)      # where the sleeves meet the body
    c.rect(12, 6, 13, 9, FOLD)
    # The white collar of the juban, crossing left over right.
    for x, y in [(5, 2), (6, 3), (7, 4), (8, 5), (10, 2), (9, 3), (8, 4)]:
        c.put(x, y, WHITE)
    c.rect(4, 14, 12, 15, FOLD)   # hem
    return c


def hakama_icon():
    c = Canvas(16, 16)
    c.rect(4, 2, 12, 4, BLACK)    # waist
    c.rect(4, 3, 12, 4, FOLD)     # himo, the ties
    for y in range(4, 15):
        flare = (y - 4) // 4
        c.rect(4 - flare, y, 8, y + 1, BLACK)
        c.rect(8, y, 12 + flare, y + 1, BLACK)
    c.rect(7, 8, 9, 15, CLEAR)    # between the legs
    for y in range(4, 14):        # pleats
        c.put(5, y, FOLD)
        c.put(10, y, FOLD)
    return c


def waraji_icon():
    c = Canvas(16, 16)
    for left in (1, 8):
        c.rect(left + 1, 3, left + 4, 9, WHITE)    # ankle
        c.rect(left, 9, left + 6, 12, WHITE)       # foot
        c.put(left + 5, 9, CLEAR)
        c.rect(left - 1, 12, left + 7, 13, STRAW)  # sole
        for x in range(left - 1, left + 7, 2):
            c.put(x, 12, STRAW_DARK)
        c.put(left + 2, 10, STRAW)                 # straps
        c.put(left + 3, 11, STRAW)
        c.put(left + 1, 11, STRAW)
    return c


def armor_layer_1():
    """Kosode on the body and arms, waraji and tabi on the legs (the boots use this layer too)."""
    c = Canvas(64, 32)
    # Body: top and bottom faces, then right, front, left and back sides.
    c.rect(20, 16, 36, 20, BLACK)
    c.rect(16, 20, 40, 32, BLACK)
    c.rect(16, 31, 40, 32, FOLD)
    for x, y in [(21, 20), (22, 21), (23, 22), (24, 23), (26, 20), (25, 21), (24, 22)]:
        c.put(x, y, WHITE)
    c.rect(23, 20, 25, 21, CLEAR)  # the neck shows between the collars
    # Arms: wide sleeves to just above the wrist, so the hands show.
    c.rect(44, 16, 48, 20, BLACK)
    c.rect(40, 20, 56, 30, BLACK)
    c.rect(40, 29, 56, 30, FOLD)
    # Legs: the bottom three rows are white tabi on a straw sole with a strap; the rest stays bare.
    c.rect(8, 16, 12, 20, STRAW)
    c.rect(0, 29, 16, 31, WHITE)
    c.rect(0, 31, 16, 32, STRAW)
    for x in range(0, 16, 2):
        c.put(x, 31, STRAW_DARK)
    c.put(5, 30, STRAW)
    c.put(6, 30, STRAW)
    return c


def armor_layer_2():
    """The hakama, from the waist to the ankles."""
    c = Canvas(64, 32)
    c.rect(16, 27, 40, 32, BLACK)  # the waist, low on the body
    c.rect(16, 27, 40, 28, FOLD)   # himo
    c.rect(4, 16, 8, 20, BLACK)    # top of the legs
    c.rect(0, 20, 16, 31, BLACK)
    for x in (1, 5, 9, 13):        # pleats
        c.rect(x, 21, x + 1, 31, FOLD)
    return c


# The vanilla spider's boxes (SpiderModel): body0 the waist, body1 the abdomen, the head, and one net
# shared by all eight legs. The Hexapodus uses the same model with two legs hidden.
SPIDER_WAIST = (0, 0, 6, 6, 6)
SPIDER_ABDOMEN = (0, 12, 10, 8, 12)
SPIDER_HEAD = (32, 4, 8, 8, 8)
SPIDER_LEG = (18, 0, 16, 2, 2)


def spider():
    c = Canvas(64, 32)
    box(c, *SPIDER_WAIST, HOLLOW_BLACK)
    box(c, *SPIDER_ABDOMEN, HOLLOW_BLACK)
    mottle(c, 0, 0, 64, 32, HOLLOW_SHADE, 5)
    box(c, *SPIDER_HEAD, HOLLOW_SHADE)
    box(c, *SPIDER_LEG, BONE_SHADE)
    paint_where(c, faces(*SPIDER_LEG), HOLLOW_BLACK, lambda x, y: x % 4 == 0)  # the joints
    c.rect(15, 16, 19, 20, VOID)  # the Hollow's hole, through the top of the abdomen
    return c


def spider_mask():
    """Over the face and the front of the head, wrapping a little round the sides."""
    c = Canvas(64, 32)
    c.rect(40, 12, 48, 20, BONE)
    c.rect(40, 8, 48, 12, BONE)
    c.rect(38, 12, 40, 20, BONE_SHADE)
    c.rect(48, 12, 50, 20, BONE_SHADE)
    c.rect(41, 14, 43, 16, VOID)  # eye holes
    c.rect(45, 14, 47, 16, VOID)
    for x in (41, 43, 45):        # gaps between the teeth
        c.put(x, 19, VOID)
    return c


def hexapodus():
    c = Canvas(64, 32)
    for part in (SPIDER_WAIST, SPIDER_ABDOMEN, SPIDER_HEAD, SPIDER_LEG):
        box(c, *part, GREY_GREEN)
    mottle(c, 0, 0, 64, 32, GREEN_SHADE, 4)
    paint_where(c, faces(*SPIDER_LEG), ORANGE, lambda x, y: x % 4 == 0)
    for x, y in [(14, 14), (18, 18), (16, 22)]:  # spots on the back
        c.rect(x, y, x + 2, y + 2, ORANGE)
    return c


def hexapodus_mask():
    """A plain mask with a lock of brown hair and a purple clip showing on top."""
    c = Canvas(64, 32)
    c.rect(40, 12, 48, 20, BONE)
    c.rect(40, 8, 48, 12, BONE)
    c.rect(41, 14, 43, 16, VOID)
    c.rect(45, 14, 47, 16, VOID)
    c.rect(40, 8, 48, 10, HAIR_BROWN)
    c.put(44, 10, PURPLE)
    return c


# The vanilla wolf's boxes (WolfModel.createMeshDefinition). The body and the mane are turned to lie
# along the back, so the face of their nets marked "back" is the top of the hound.
HOUND_HEAD = (0, 0, 6, 6, 4)
HOUND_EAR = (16, 14, 2, 2, 1)
HOUND_MUZZLE = (0, 10, 3, 3, 4)
HOUND_BODY = (18, 14, 6, 9, 6)
HOUND_MANE = (21, 0, 8, 6, 7)
HOUND_LEG = (0, 18, 2, 8, 2)
HOUND_TAIL = (9, 18, 2, 8, 2)


def hound():
    c = Canvas(64, 32)
    for part in (HOUND_HEAD, HOUND_EAR, HOUND_MUZZLE, HOUND_BODY, HOUND_MANE, HOUND_LEG, HOUND_TAIL):
        box(c, *part, HOLLOW_BLACK)
    mottle(c, 0, 0, 64, 32, HOLLOW_SHADE, 6)
    c.rect(37, 23, 41, 26, VOID)  # the hole, through the middle of the back
    return c


def hound_mask():
    """Over the face and brow, wrapping round the sides, and down over the muzzle."""
    c = Canvas(64, 32)
    c.rect(4, 4, 10, 10, BONE)
    c.rect(4, 2, 10, 4, BONE)
    c.rect(2, 4, 4, 10, BONE_SHADE)
    c.rect(10, 4, 12, 10, BONE_SHADE)
    c.rect(4, 6, 6, 7, VOID)      # eye holes, out at the edges of the face
    c.rect(8, 6, 10, 7, VOID)
    c.rect(4, 10, 7, 14, BONE_SHADE)  # muzzle top
    c.rect(4, 14, 7, 17, BONE)        # muzzle front, with the corners of the mouth
    c.put(4, 16, VOID)
    c.put(6, 16, VOID)
    return c


# The Serpent Hollow's boxes (SerpentHollowModel): two coils, the thick base of the neck, the net the
# three thinner neck segments share, the head and the hood behind it.
SERPENT_PARTS = [(0, 0, 14, 3, 14), (0, 17, 10, 3, 10), (40, 17, 5, 6, 5), (40, 28, 4, 6, 4),
                 (0, 30, 7, 5, 8), (30, 40, 12, 9, 1)]


def serpent():
    c = Canvas(64, 64)
    for part in SERPENT_PARTS:
        box(c, *part, SCALE_DARK)
    for part in SERPENT_PARTS:
        paint_where(c, faces(*part), SCALE_LIGHT, lambda x, y: (x + y) % 4 == 0)
    c.rect(46, 24, 49, 27, VOID)  # the hole, through the front of the neck
    return c


def serpent_mask():
    """Over the whole top of the head and the face, with slit eyes."""
    c = Canvas(64, 64)
    c.rect(8, 30, 15, 38, BONE)
    c.rect(8, 38, 15, 43, BONE)
    c.rect(6, 38, 8, 43, BONE_SHADE)
    c.rect(15, 38, 17, 43, BONE_SHADE)
    c.put(9, 39, VOID)
    c.put(13, 39, VOID)
    for x in (9, 11, 13):
        c.put(x, 42, VOID)
    return c


# Fishbone D's boxes (FishboneDModel).
FISHBONE_BODY = (0, 0, 16, 16, 10)
FISHBONE_HEAD = (52, 0, 10, 8, 10)
FISHBONE_JAW = (92, 0, 8, 3, 9)
FISHBONE_FIN = (52, 18, 2, 10, 8)
FISHBONE_LEG = (0, 26, 6, 12, 6)
FISHBONE_ARM = (24, 26, 5, 22, 6)


def fishbone_d():
    c = Canvas(128, 64)
    for part in (FISHBONE_BODY, FISHBONE_HEAD, FISHBONE_JAW, FISHBONE_FIN, FISHBONE_LEG, FISHBONE_ARM):
        box(c, *part, FISH_GREY)
    paint_where(c, faces(*FISHBONE_BODY), FISH_STRIPE, lambda x, y: y % 3 == 0)
    paint_where(c, faces(*FISHBONE_FIN), BONE_SHADE, lambda x, y: x % 2 == 0)
    c.rect(24, 52, 46, 54, BONE_SHADE)  # claws, round the bottom of the arms
    c.rect(16, 14, 20, 18, VOID)        # the hole, through the chest
    return c


def fishbone_d_mask():
    """A fish skull over the whole head, its sides ribbed, and bone over the jaw."""
    c = Canvas(128, 64)
    c.rect(62, 0, 72, 10, BONE)        # top of the skull, with a ridge down the middle
    c.rect(66, 0, 68, 10, BONE_SHADE)
    c.rect(62, 10, 72, 18, BONE)       # face
    c.rect(63, 12, 65, 14, VOID)
    c.rect(69, 12, 71, 14, VOID)
    for x in range(62, 72, 2):         # the mouth
        c.put(x, 17, VOID)
    c.rect(52, 10, 62, 18, BONE)       # sides: solid for four columns behind the face, then ribs
    c.rect(72, 10, 82, 18, BONE)
    for back in range(4, 10, 2):
        c.rect(61 - back, 10, 62 - back, 18, VOID)
        c.rect(72 + back, 10, 73 + back, 18, VOID)
    c.rect(101, 0, 109, 9, BONE)       # jaw: top, sides and front
    c.rect(92, 9, 101, 12, BONE)
    c.rect(109, 9, 118, 12, BONE)
    c.rect(101, 9, 109, 12, BONE)
    for x in (102, 104, 106):
        c.put(x, 11, VOID)
    return c


def garganta():
    """A tear in the air: black, ragged at the edges and narrow at both ends."""
    c = Canvas(32, 64)
    for y in range(64):
        half = round(14 * math.sin(math.pi * (y + 0.5) / 64)) + (1 if (y // 3) % 2 else 0)
        for x in range(32):
            off = abs(x - 15.5)
            if off < half - 1:
                c.put(x, y, VOID)
            elif off < half:
                c.put(x, y, RIFT_EDGE)
    return c


def denreishinki():
    """A soul pager: a flip phone with a green screen, a row of keys and a stub antenna."""
    c = Canvas(16, 16)
    c.rect(4, 3, 12, 15, DEVICE_EDGE)
    c.rect(5, 4, 11, 14, DEVICE)
    c.rect(5, 4, 11, 8, SCREEN)
    c.rect(4, 9, 12, 10, DEVICE_EDGE)  # the hinge
    for x, y in [(5, 10), (7, 10), (9, 10), (5, 12), (7, 12), (9, 12)]:
        c.put(x, y, KEY)
    c.rect(11, 0, 12, 3, DEVICE_EDGE)  # the antenna
    return c


TEXTURES = {
    "item/asauchi.png": asauchi,
    "item/shihakusho_kosode.png": kosode_icon,
    "item/shihakusho_hakama.png": hakama_icon,
    "item/waraji.png": waraji_icon,
    "models/armor/shihakusho_layer_1.png": armor_layer_1,
    "models/armor/shihakusho_layer_2.png": armor_layer_2,
    "entity/hollow/spider.png": spider,
    "entity/hollow/spider_mask.png": spider_mask,
    "entity/hollow/spider_mask_cracked.png": cracked(spider_mask, [(40, 9), (43, 13), (44, 17), (46, 19)]),
    "entity/hollow/hexapodus.png": hexapodus,
    "entity/hollow/hexapodus_mask.png": hexapodus_mask,
    "entity/hollow/hexapodus_mask_cracked.png": cracked(hexapodus_mask, [(41, 12), (44, 16), (43, 19)]),
    "entity/hollow/hound.png": hound,
    "entity/hollow/hound_mask.png": hound_mask,
    "entity/hollow/hound_mask_cracked.png": cracked(hound_mask, [(4, 4), (6, 7), (5, 9)]),
    "entity/hollow/serpent.png": serpent,
    "entity/hollow/serpent_mask.png": serpent_mask,
    "entity/hollow/serpent_mask_cracked.png": cracked(serpent_mask, [(9, 32), (11, 36), (10, 41)]),
    "entity/hollow/fishbone_d.png": fishbone_d,
    "entity/hollow/fishbone_d_mask.png": fishbone_d_mask,
    "entity/hollow/fishbone_d_mask_cracked.png": cracked(fishbone_d_mask, [(63, 2), (66, 8), (65, 13), (67, 17)]),
    "entity/garganta.png": garganta,
    "item/denreishinki.png": denreishinki,
}


def encode(canvas):
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in canvas.rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", canvas.width, canvas.height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def decode(data):
    """Returns (width, height, rows of RGBA tuples) for an 8-bit RGBA PNG, or raises ValueError."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, header = 8, b"", None
    while pos < len(data):
        length, tag = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif tag == b"IDAT":
            idat += body
        pos += 12 + length
    if header is None or header[2:5] != (8, 6, 0) or header[6] != 0:
        raise ValueError("not an 8-bit RGBA PNG without interlacing")
    width, height = header[0], header[1]
    raw = zlib.decompress(idat)
    stride = width * 4
    rows, previous, pos = [], bytearray(stride), 0
    for _ in range(height):
        kind, line = raw[pos], bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            left = line[i - 4] if i >= 4 else 0
            up = previous[i]
            corner = previous[i - 4] if i >= 4 else 0
            if kind == 1:
                line[i] = (line[i] + left) & 0xFF
            elif kind == 2:
                line[i] = (line[i] + up) & 0xFF
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 0xFF
            elif kind == 4:
                guess = left + up - corner
                pa, pb, pc = abs(guess - left), abs(guess - up), abs(guess - corner)
                pick = left if pa <= pb and pa <= pc else up if pb <= pc else corner
                line[i] = (line[i] + pick) & 0xFF
            elif kind != 0:
                raise ValueError("unknown PNG filter %d" % kind)
        rows.append([tuple(line[x * 4:x * 4 + 4]) for x in range(width)])
        previous = line
    return width, height, rows


def main(argv):
    check = "--check" in argv
    stale = []
    for name, draw in TEXTURES.items():
        canvas = draw()
        path = ASSETS / name
        if check:
            try:
                width, height, rows = decode(path.read_bytes())
            except (OSError, ValueError) as error:
                stale.append("%s: %s" % (path, error))
                continue
            if (width, height) != (canvas.width, canvas.height) or rows != canvas.rows:
                stale.append("%s: differs from what %s draws" % (path, draw.__name__))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(encode(canvas))
            print("wrote", path)
    if stale:
        print("Placeholder textures are out of date. Run python3 tools/art/make_placeholders.py", file=sys.stderr)
        for line in stale:
            print("  " + line, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
