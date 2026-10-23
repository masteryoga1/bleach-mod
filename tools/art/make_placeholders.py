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

# Zangetsu: a guardless cleaver with a dark back, a white-wrapped hilt and a trailing cloth.
CLEAVER_EDGE = rgb(236, 240, 244)
CLEAVER = rgb(196, 202, 210)
CLEAVER_SHADE = rgb(150, 156, 166)
CLEAVER_BACK = rgb(44, 44, 50)
CLOTH = rgb(236, 232, 220)
CLOTH_SHADE = rgb(190, 184, 170)
# Zabimaru: a red-wrapped hilt and bronze guard, and fangs along the Shikai's segments.
ZABI_WRAP = rgb(150, 36, 36)
ZABI_WRAP_DARK = rgb(96, 20, 24)
BRONZE = rgb(170, 120, 60)
TOOTH = rgb(240, 236, 226)

# The Quincy: a silver cross, white cloth with blue trim, and pale blue reishi.
SILVER = rgb(206, 212, 222)
SILVER_SHADE = rgb(140, 148, 162)
REISHI = rgb(200, 232, 255)
REISHI_CORE = rgb(250, 254, 255)
REISHI_EDGE = rgb(110, 170, 230)
QUINCY_WHITE = rgb(244, 246, 250)
QUINCY_FOLD = rgb(206, 212, 224)
QUINCY_BLUE = rgb(40, 84, 170)
VEIN = rgb(90, 170, 255)
VEIN_DIM = rgb(60, 120, 220)
# The Wandenreich uniform, white with black bands and silver buttons; a fragment of a Hollow's mask;
# and the Hollow Bait, a pale wafer with a dark red heart.
WANDENREICH_WHITE = rgb(240, 240, 236)
WANDENREICH_BLACK = rgb(34, 34, 40)
WANDENREICH_SILVER = rgb(176, 184, 196)
WANDENREICH_FOLD = rgb(206, 206, 200)
MASK_BONE = rgb(234, 230, 218)
MASK_SHADE = rgb(188, 182, 168)
MASK_LINE = rgb(92, 88, 82)
BAIT_WHITE = rgb(226, 220, 206)
BAIT_EDGE = rgb(150, 140, 124)
BAIT_RED = rgb(150, 30, 36)
BAIT_CORE = rgb(70, 14, 18)
# The later arts: Letzt Stil's pale gold, Ginrei Kojaku's silver-blue, Vollständig's halo and its
# translucent wings, the cloud of a Gintō spell, and the Sanrei Glove's blue cuff.
LETZT = rgb(255, 214, 160)
LETZT_CORE = rgb(255, 246, 228)
LETZT_EDGE = rgb(240, 150, 70)
GINREI = rgb(214, 226, 255)
GINREI_EDGE = rgb(140, 160, 230)
HALO = rgb(255, 232, 150)
WING = (236, 244, 255, 200)
WING_EDGE = (150, 196, 255, 235)
CLOUD = (200, 232, 255, 110)
CLOUD_EDGE = (110, 170, 230, 220)
GLOVE_CUFF = QUINCY_BLUE


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


def recoloured(base, mapping, name=None):
    """A drawing function for base with each colour that is a key of mapping swapped for its value, in
    one pass, so a swap never feeds another. Named after the base unless given a name."""
    def draw():
        c = base()
        for y in range(c.height):
            for x in range(c.width):
                colour = c.get(x, y)
                if colour in mapping:
                    c.put(x, y, mapping[colour])
        return c
    draw.__name__ = name or base.__name__ + "_recoloured"
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


def zangetsu():
    """Zangetsu in Shikai: a broad cleaver with no guard, its hilt wrapped in white cloth that trails off."""
    c = Canvas(32, 32)
    for i in range(22):
        x, y = 9 + i, 22 - i
        width = min(6, 22 - i)  # the edge side slants in over the last five steps
        for w in range(width):
            colour = CLEAVER_BACK if w == 0 else CLEAVER_EDGE if w == width - 1 else CLEAVER_SHADE if w == 1 else CLEAVER
            c.put(x + w // 2, y + (w + 1) // 2, colour)
    for px in range(3, 10):
        c.put(px, 33 - px, CLOTH if px % 2 == 0 else CLOTH_SHADE)
        c.put(px, 34 - px, CLOTH_SHADE if px % 2 == 0 else CLOTH)
    for k, (x, y) in enumerate([(2, 30), (1, 30), (0, 29), (0, 28), (1, 27), (1, 26), (0, 25)]):
        c.put(x, y, CLOTH if k % 2 == 0 else CLOTH_SHADE)
    return c


def zabimaru():
    """Zabimaru sealed: the Asauchi's shape with a red-wrapped hilt and a bronze guard."""
    c = asauchi()
    for y in range(16):
        for x in range(16):
            colour = c.get(x, y)
            if colour == WRAP:
                c.put(x, y, ZABI_WRAP)
            elif colour == DIAMOND:
                c.put(x, y, ZABI_WRAP_DARK)
            elif colour == GUARD:
                c.put(x, y, BRONZE)
    return c


def zabimaru_shikai():
    """Zabimaru in Shikai: a longer blade in six segments with a fang on each, the hilt still wrapped red."""
    c = Canvas(32, 32)
    for i in range(24):
        if i % 4 == 3:
            continue  # the gap between two segments
        x, y = 7 + i, 24 - i
        for w in range(3):
            c.put(x + w // 2, y + (w + 1) // 2, SHADE if w == 0 else STEEL if w == 1 else EDGE)
        if i % 4 == 0:
            c.put(x + 1, y + 2, TOOTH)
    for px in range(1, 7):
        c.put(px, 31 - px, ZABI_WRAP if px % 2 else ZABI_WRAP_DARK)
        c.put(px, 32 - px, ZABI_WRAP_DARK if px % 2 else ZABI_WRAP)
    for x, y in [(4, 23), (5, 24), (7, 26), (8, 27)]:
        c.put(x, y, BRONZE)
    return c


def getsuga_tensho():
    """The crescent: white, brightest along its middle and fading at its tips; the renderer tints it."""
    c = Canvas(32, 16)
    for y in range(16):
        across = max(0.0, 1.0 - abs(y - 7.5) / 8.0)
        for x in range(32):
            along = math.sin(math.pi * (x + 0.5) / 32)
            c.put(x, y, (255, 255, 255, round(255 * across * across * along)))
    return c


def zabimaru_segment():
    """One segment of Zabimaru's blade, for the lash: steel with a bright edge and fangs."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, STEEL)
    c.rect(0, 0, 16, 2, EDGE)
    c.rect(0, 14, 16, 16, SHADE)
    for x in range(2, 16, 5):
        c.rect(x, 2, x + 2, 5, TOOTH)
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


def quincy_cross():
    """The Quincy Cross: a silver cross on a short chain, with a pale blue stone where the arms meet."""
    c = Canvas(16, 16)
    line(c, 5, 0, 7, 2, SILVER_SHADE)   # the chain
    line(c, 10, 0, 8, 2, SILVER_SHADE)
    c.rect(7, 3, 9, 15, SILVER)         # the upright, shaded down its right side
    c.rect(8, 3, 9, 15, SILVER_SHADE)
    c.rect(4, 6, 12, 8, SILVER)         # the crossbar, shaded along its bottom
    c.rect(4, 7, 12, 8, SILVER_SHADE)
    c.rect(7, 6, 9, 8, REISHI_EDGE)     # the stone
    c.put(7, 14, SILVER_SHADE)          # the foot
    c.put(8, 14, SILVER_SHADE)
    return c


def heilig_bogen(stage):
    """A drawing function for the Heilig Bogen at a stage of the draw (0, 1 or 2): a bow of reishi
    curving round the bottom left corner, its string pulled back further and its arrow longer at each
    stage. Used while the Cross is drawn, with the vanilla bow's held transforms."""
    def draw():
        c = Canvas(16, 16)
        d = stage + 1
        for y in range(15):
            for x in range(1, 16):
                r = math.hypot(x + 0.5, 16 - (y + 0.5))
                if 12.5 <= r < 13.5:
                    c.put(x, y, REISHI)
                elif 11.5 <= r < 12.5:
                    c.put(x, y, REISHI_EDGE)
        mx, my = 6 - d, 9 + d                # where the string is drawn back to
        line(c, 1, 3, mx, my, REISHI_CORE)
        line(c, mx, my, 12, 14, REISHI_CORE)
        length = 3 + 2 * d
        ex, ey = mx + length, my - length    # the arrow's tip, with two barbs behind it
        line(c, mx, my, ex, ey, REISHI_CORE)
        c.put(ex - 1, ey, REISHI)
        c.put(ex, ey + 1, REISHI)
        return c
    draw.__name__ = "heilig_bogen_%d" % stage
    return draw


def quincy_tunic_icon():
    c = Canvas(16, 16)
    c.rect(4, 3, 12, 14, QUINCY_WHITE)   # body
    c.rect(1, 4, 4, 10, QUINCY_WHITE)    # sleeves, with blue cuffs
    c.rect(12, 4, 15, 10, QUINCY_WHITE)
    c.rect(1, 9, 4, 10, QUINCY_BLUE)
    c.rect(12, 9, 15, 10, QUINCY_BLUE)
    c.rect(6, 1, 10, 3, QUINCY_FOLD)     # the high collar
    c.rect(8, 3, 9, 14, QUINCY_BLUE)     # the seam down the front
    c.rect(4, 13, 12, 14, QUINCY_BLUE)   # hem
    for x, y in [(10, 5), (9, 6), (10, 6), (11, 6), (10, 7)]:  # a small cross on the chest
        c.put(x, y, QUINCY_BLUE)
    return c


def quincy_trousers_icon():
    c = Canvas(16, 16)
    c.rect(4, 2, 12, 4, QUINCY_WHITE)    # waist, with a blue belt
    c.rect(4, 3, 12, 4, QUINCY_BLUE)
    c.rect(4, 4, 7, 15, QUINCY_WHITE)    # legs
    c.rect(9, 4, 12, 15, QUINCY_WHITE)
    c.rect(7, 4, 9, 6, QUINCY_WHITE)     # crotch
    c.rect(5, 6, 6, 15, QUINCY_FOLD)     # creases
    c.rect(10, 6, 11, 15, QUINCY_FOLD)
    return c


def quincy_shoes_icon():
    c = Canvas(16, 16)
    for left in (2, 9):
        c.rect(left, 9, left + 5, 13, QUINCY_WHITE)
        c.rect(left, 13, left + 5, 14, QUINCY_BLUE)  # sole
        c.put(left + 2, 10, QUINCY_FOLD)             # laces
    return c


def quincy_armor_layer_1():
    """The tunic on the body and arms, and the shoes on the legs (the boots use this layer)."""
    c = Canvas(64, 32)
    # Body: a long white tunic with a high collar, a blue seam down the front and a blue hem.
    c.rect(20, 16, 36, 20, QUINCY_WHITE)
    c.rect(16, 20, 40, 32, QUINCY_WHITE)
    c.rect(20, 20, 28, 21, QUINCY_FOLD)
    c.rect(24, 21, 25, 32, QUINCY_BLUE)
    # A small cross on the wearer's left breast: the right half of the front face as drawn.
    for x, y in [(26, 22), (25, 23), (26, 23), (27, 23), (26, 24), (26, 25)]:
        c.put(x, y, QUINCY_BLUE)
    c.rect(16, 31, 40, 32, QUINCY_BLUE)
    # Arms: sleeves to the wrist, with blue cuffs.
    c.rect(44, 16, 48, 20, QUINCY_WHITE)
    c.rect(40, 20, 56, 32, QUINCY_WHITE)
    c.rect(40, 30, 56, 32, QUINCY_BLUE)
    # Legs: white shoes over the bottom three rows on a blue sole; the rest stays bare.
    c.rect(0, 29, 16, 32, QUINCY_WHITE)
    c.rect(0, 31, 16, 32, QUINCY_BLUE)
    c.rect(8, 16, 12, 20, QUINCY_BLUE)
    return c


def quincy_armor_layer_2():
    """The trousers, from a belted waist to just above the shoes."""
    c = Canvas(64, 32)
    c.rect(16, 27, 40, 32, QUINCY_WHITE)  # the waist, low on the body
    c.rect(16, 27, 40, 28, QUINCY_BLUE)   # belt
    c.rect(4, 16, 8, 20, QUINCY_WHITE)    # top of the legs
    c.rect(0, 20, 16, 29, QUINCY_WHITE)
    c.rect(6, 21, 7, 29, QUINCY_FOLD)     # creases, front and back
    c.rect(14, 21, 15, 29, QUINCY_FOLD)
    return c


def wandenreich_cap_icon():
    c = Canvas(16, 16)
    c.rect(3, 4, 13, 9, WANDENREICH_WHITE)    # crown
    c.rect(3, 8, 13, 10, WANDENREICH_BLACK)   # band
    c.rect(2, 10, 10, 12, WANDENREICH_BLACK)  # peak
    for x, y in [(8, 5), (7, 6), (8, 6), (9, 6), (8, 7)]:  # a silver plus on the front
        c.put(x, y, WANDENREICH_SILVER)
    return c


def wandenreich_coat_icon():
    c = Canvas(16, 16)
    c.rect(4, 2, 12, 15, WANDENREICH_WHITE)   # body
    c.rect(1, 3, 4, 11, WANDENREICH_WHITE)    # sleeves, with black cuffs
    c.rect(12, 3, 15, 11, WANDENREICH_WHITE)
    c.rect(1, 10, 4, 11, WANDENREICH_BLACK)
    c.rect(12, 10, 15, 11, WANDENREICH_BLACK)
    c.rect(5, 1, 11, 3, WANDENREICH_BLACK)    # collar
    c.rect(8, 3, 9, 15, WANDENREICH_BLACK)    # the opening down the front
    for x, y in [(6, 5), (6, 8), (6, 11), (10, 5), (10, 8), (10, 11)]:  # two rows of buttons
        c.put(x, y, WANDENREICH_SILVER)
    c.rect(4, 14, 12, 15, WANDENREICH_BLACK)  # hem
    return c


def wandenreich_trousers_icon():
    c = Canvas(16, 16)
    c.rect(4, 2, 12, 4, WANDENREICH_WHITE)    # waist, with a black belt
    c.rect(4, 3, 12, 4, WANDENREICH_BLACK)
    c.rect(4, 4, 7, 15, WANDENREICH_WHITE)    # legs
    c.rect(9, 4, 12, 15, WANDENREICH_WHITE)
    c.rect(7, 4, 9, 6, WANDENREICH_WHITE)     # crotch
    c.rect(4, 6, 5, 15, WANDENREICH_BLACK)    # stripes down the outer seams
    c.rect(11, 6, 12, 15, WANDENREICH_BLACK)
    return c


def wandenreich_boots_icon():
    c = Canvas(16, 16)
    for left in (2, 9):
        c.rect(left, 6, left + 5, 13, WANDENREICH_WHITE)  # shaft
        c.rect(left, 6, left + 5, 7, WANDENREICH_BLACK)   # top band
        c.rect(left, 13, left + 5, 14, WANDENREICH_BLACK)  # sole
        c.put(left + 2, 9, WANDENREICH_FOLD)               # lace
    return c


def wandenreich_armor_layer_1():
    """The cap on the head, the coat on the body and arms, and the boots on the legs (the boots use
    this layer)."""
    c = Canvas(64, 32)
    # Head: the top face is the crown; each side face is white for three rows above a black band,
    # the rest left clear so the face shows; the front also has the peak and a silver plus.
    c.rect(8, 0, 16, 8, WANDENREICH_WHITE)
    c.rect(0, 8, 32, 11, WANDENREICH_WHITE)
    c.rect(0, 11, 32, 12, WANDENREICH_BLACK)
    c.rect(8, 11, 16, 12, WANDENREICH_BLACK)
    for x, y in [(12, 8), (11, 9), (12, 9), (13, 9), (12, 10)]:
        c.put(x, y, WANDENREICH_SILVER)
    # Body: a long white coat with a black collar, a black opening down the front between two rows of
    # silver buttons, folds at the sides and a black hem.
    c.rect(20, 16, 36, 20, WANDENREICH_WHITE)
    c.rect(16, 20, 40, 32, WANDENREICH_WHITE)
    c.rect(20, 20, 28, 21, WANDENREICH_BLACK)
    c.rect(24, 21, 25, 32, WANDENREICH_BLACK)
    for x, y in [(22, 23), (22, 26), (22, 29), (26, 23), (26, 26), (26, 29)]:
        c.put(x, y, WANDENREICH_SILVER)
    c.rect(16, 31, 40, 32, WANDENREICH_BLACK)
    c.rect(17, 22, 18, 31, WANDENREICH_FOLD)
    c.rect(33, 22, 34, 31, WANDENREICH_FOLD)
    # Arms: sleeves to the wrist, with black cuffs.
    c.rect(44, 16, 48, 20, WANDENREICH_WHITE)
    c.rect(40, 20, 56, 32, WANDENREICH_WHITE)
    c.rect(40, 30, 56, 32, WANDENREICH_BLACK)
    # Legs: white boots over the bottom five rows, a black band at the top and a black sole.
    c.rect(0, 27, 16, 32, WANDENREICH_WHITE)
    c.rect(0, 31, 16, 32, WANDENREICH_BLACK)
    c.rect(8, 16, 12, 20, WANDENREICH_BLACK)
    c.rect(0, 27, 16, 28, WANDENREICH_BLACK)
    return c


def wandenreich_armor_layer_2():
    """The trousers, from a belted waist to the tops of the boots, with black stripes."""
    c = Canvas(64, 32)
    c.rect(16, 27, 40, 32, WANDENREICH_WHITE)  # the waist, low on the body
    c.rect(16, 27, 40, 28, WANDENREICH_BLACK)  # belt
    c.rect(4, 16, 8, 20, WANDENREICH_WHITE)    # top of the legs
    c.rect(0, 20, 16, 29, WANDENREICH_WHITE)
    c.rect(0, 20, 1, 29, WANDENREICH_BLACK)    # stripes
    c.rect(8, 20, 9, 29, WANDENREICH_BLACK)
    c.rect(6, 21, 7, 29, WANDENREICH_FOLD)     # creases
    c.rect(14, 21, 15, 29, WANDENREICH_FOLD)
    return c


def mask_fragment_icon():
    """A jagged shard of bone with a crack across it. The shade falls on its lower right: the
    right-most pixel of each row, and the bottom-most of each column."""
    c = Canvas(16, 16)
    rows = [(3, 7, 10), (4, 6, 11), (5, 5, 12), (6, 5, 13), (7, 4, 13), (8, 4, 12), (9, 5, 12), (10, 5, 11),
            (11, 6, 10), (12, 7, 9)]
    for y, x0, x1 in rows:
        c.rect(x0, y, x1, y + 1, MASK_BONE)
    # Found on the plain shard first, so shading one edge never moves another.
    shaded = {(x1 - 1, y) for y, x0, x1 in rows}
    for x in range(c.width):
        bone = [y for y in range(c.height) if c.get(x, y) == MASK_BONE]
        if bone:
            shaded.add((x, max(bone)))
    for x, y in shaded:
        c.put(x, y, MASK_SHADE)
    line(c, 7, 4, 9, 8, MASK_LINE)
    line(c, 9, 8, 8, 11, MASK_LINE)
    return c


def hollow_bait_icon():
    """A round wafer, pale with a darker rim, a dark red heart and a few red specks."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d2 = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d2 <= 1.5:
                c.put(x, y, BAIT_CORE)
            elif d2 <= 5:
                c.put(x, y, BAIT_RED)
            elif d2 <= 20:
                c.put(x, y, BAIT_WHITE)
            elif d2 <= 30:
                c.put(x, y, BAIT_EDGE)
    for x, y in [(5, 6), (10, 9), (6, 10)]:
        c.put(x, y, BAIT_RED)
    return c


def heilig_pfeil():
    """The arrow of reishi, laid out where vanilla's ArrowRenderer samples: the shaft from x 0..16 and
    y 0..5, the end cap from x 0..5 and y 5..10. It is a spindle, the same from either end, so it
    cannot be drawn the wrong way round; the renderer makes it glow."""
    c = Canvas(32, 32)
    c.rect(0, 2, 16, 3, REISHI_CORE)
    c.rect(2, 1, 14, 2, REISHI)
    c.rect(2, 3, 14, 4, REISHI)
    c.rect(5, 0, 11, 1, REISHI_EDGE)
    c.rect(5, 4, 11, 5, REISHI_EDGE)
    c.rect(2, 5, 3, 10, REISHI_CORE)  # the cap, a small bright cross
    c.rect(0, 7, 5, 8, REISHI_CORE)
    c.put(2, 7, REISHI_CORE)
    return c


def vein(c, points):
    """A vein along straight segments between the points, fading at its last one."""
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        line(c, x0, y0, x1, y1, VEIN)
    c.put(*points[-1], VEIN_DIM)


def blut_vene():
    """Blut Vene's veins, in the player skin layout and drawn over the player's model. Only the base
    layer's arm and body faces get veins, in columns inside both the wide and the slim arms; the
    rest, the outer layer's included, stays clear."""
    c = Canvas(64, 64)
    vein(c, [(45, 20), (46, 23), (45, 26), (46, 29), (45, 31)])  # right arm, front
    vein(c, [(42, 21), (41, 25), (42, 28), (42, 31)])            # right arm, outer side
    vein(c, [(38, 52), (37, 55), (38, 58), (37, 61), (38, 63)])  # left arm, front
    vein(c, [(41, 53), (42, 57), (41, 60), (42, 63)])            # left arm, outer side
    vein(c, [(23, 20), (22, 23), (21, 26)])                      # body, front
    vein(c, [(24, 20), (25, 23), (26, 26), (27, 29)])
    return c


def sanrei_glove_icon():
    """The Sanrei Glove: a white glove with a blue cuff and a small silver cross on its back."""
    c = Canvas(16, 16)
    c.rect(4, 6, 12, 13, QUINCY_WHITE)  # the palm
    for x in (4, 6, 8, 10):             # the fingers
        c.rect(x, 2, x + 1, 6, QUINCY_WHITE)
    c.rect(2, 8, 4, 11, QUINCY_WHITE)   # the thumb
    c.rect(4, 13, 12, 16, GLOVE_CUFF)   # the cuff
    for x in (5, 7, 9, 11):             # folds between the fingers, and where the thumb meets the palm
        c.put(x, 6, QUINCY_FOLD)
    c.rect(4, 8, 5, 11, QUINCY_FOLD)
    c.rect(7, 8, 9, 12, SILVER)         # the cross
    c.rect(6, 9, 10, 11, SILVER)
    return c


def seele_schneider_icon():
    """The Seele Schneider as everyone sees it: a silver hilt and a short guard, slanted like a sword,
    with no blade."""
    c = Canvas(16, 16)
    line(c, 2, 13, 6, 9, SILVER)
    line(c, 2, 14, 6, 10, SILVER_SHADE)
    c.put(5, 8, GUARD)
    c.put(7, 10, GUARD)
    c.put(1, 14, SILVER_SHADE)          # the pommel
    return c


def seele_schneider_formed_icon():
    """The Seele Schneider with its blade of reishi formed, which only those who see spirits see."""
    c = seele_schneider_icon()
    line(c, 7, 8, 14, 1, REISHI_EDGE)
    line(c, 7, 7, 14, 0, REISHI_CORE)   # the upper-left side of the blade
    c.put(15, 0, REISHI_CORE)           # the tip
    return c


def ginto_tube(inside):
    """A Gintō tube upright: silver walls and caps around what it holds."""
    c = Canvas(16, 16)
    c.rect(6, 2, 7, 14, SILVER)
    c.rect(9, 2, 10, 14, SILVER)
    c.rect(7, 2, 9, 14, inside)
    c.rect(6, 1, 10, 2, SILVER_SHADE)
    c.rect(6, 14, 10, 15, SILVER_SHADE)
    return c


def empty_ginto_icon():
    return ginto_tube(SILVER_SHADE)


def ginto_icon():
    """A filled Gintō: reishi to the top, with a bright streak down the glass."""
    c = ginto_tube(REISHI)
    c.rect(7, 2, 9, 3, REISHI_EDGE)     # the meniscus
    c.rect(7, 3, 8, 7, REISHI_CORE)
    return c


def quincy_zeichen_icon():
    """The restored Cross at rest: a silver ring round a five-point star of reishi, on a stub of chain."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 8, y - 8)
            if 6.0 <= r < 7.0:
                c.put(x, y, SILVER)
            elif 5.0 <= r < 6.0:
                c.put(x, y, SILVER_SHADE)
    # Every second vertex of a pentagon in turn, so the lines cross into a star. Rounds half up.
    star = [(math.floor(8 + 5 * math.cos(math.radians(-90 + 144 * k)) + 0.5),
             math.floor(8 + 5 * math.sin(math.radians(-90 + 144 * k)) + 0.5)) for k in range(5)]
    for (x0, y0), (x1, y1) in zip(star, star[1:] + star[:1]):
        line(c, x0, y0, x1, y1, REISHI_EDGE)
    c.put(8, 8, REISHI_CORE)
    c.rect(8, 0, 9, 2, SILVER_SHADE)    # the chain
    return c


def letzt_bogen(stage):
    """A drawing function for Letzt Stil's bow at a stage of the draw: the Heilig Bogen in pale gold."""
    return recoloured(heilig_bogen(stage), {REISHI: LETZT, REISHI_EDGE: LETZT_EDGE, REISHI_CORE: LETZT_CORE},
                      "letzt_bogen_%d" % stage)


def ginrei_kojaku(stage):
    """A drawing function for Ginrei Kojaku at a stage of the draw: the Heilig Bogen in silver-blue,
    with a silver pentacle in the corner the bow curves round."""
    base = recoloured(heilig_bogen(stage), {REISHI: GINREI, REISHI_EDGE: GINREI_EDGE})

    def draw():
        c = base()
        for x, y in [(1, 13), (2, 12), (3, 13), (2, 14)]:
            c.put(x, y, SILVER)
        c.put(2, 13, REISHI_CORE)
        return c
    draw.__name__ = "ginrei_kojaku_%d" % stage
    return draw


def gritz_cross(c, ox, oy, size):
    """Gritz's cross with its top left corner at (ox, oy), drawn as on the 18-pixel effect icon and
    scaled to size: reishi in a 1-pixel outline, brightest where the arms meet."""
    def scaled(x0, y0, x1, y1):
        return [math.floor(v * size / 18 + 0.5) for v in (x0, y0, x1, y1)]
    cross = set()
    for x0, y0, x1, y1 in (scaled(8, 2, 10, 16), scaled(3, 6, 15, 8)):
        cross.update((x, y) for y in range(y0, y1) for x in range(x0, x1))
    for y in range(size):
        for x in range(size):
            if (x, y) in cross:
                c.put(ox + x, oy + y, REISHI)
            elif any((x + dx, y + dy) in cross for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                c.put(ox + x, oy + y, REISHI_EDGE)
    x0, y0, x1, y1 = scaled(8, 6, 10, 8)
    c.rect(ox + x0, oy + y0, ox + x1, oy + y1, REISHI_CORE)


def gritz_icon():
    """The Gritz effect's icon."""
    c = Canvas(18, 18)
    gritz_cross(c, 0, 0, 18)
    return c


def sanrei_glove_layer():
    """The glove on the player's hand (SanreiGloveModel, one 4x6x4 box): white, with a blue cuff along
    the top row at the wrist, a line of knuckles low on the front, and a silver cross on the back."""
    c = Canvas(32, 16)
    box(c, 0, 0, 4, 6, 4, QUINCY_WHITE)
    c.rect(0, 4, 16, 5, GLOVE_CUFF)
    c.rect(4, 9, 8, 10, QUINCY_FOLD)
    c.rect(13, 5, 15, 9, SILVER)
    c.rect(12, 6, 16, 7, SILVER)
    return c


def letzt_stil():
    """Letzt Stil's glow, in the player skin layout and drawn lit over the player's model: thin upright
    lines of pale gold, brighter every fourth row, over the front and back of the body and the fronts
    of both arms. Every second column, so the slim arms get the same lines; the rest, the outer layer
    included, stays clear."""
    c = Canvas(64, 64)
    for x0, y0, x1, y1 in [(20, 20, 28, 32), (32, 20, 40, 32), (44, 20, 48, 32), (36, 52, 40, 64)]:
        for y in range(y0, y1):
            for x in range(x0, x1, 2):
                c.put(x, y, LETZT_CORE if (y - y0) % 4 == 0 else LETZT)
    return c


def letzt_pfeil():
    """Letzt Stil's great arrow, in heilig_pfeil's layout: a shaft of pale gold, brightest along its
    middle, and a small diamond for the end cap. The same from either end, like the Heilig Pfeil."""
    c = Canvas(32, 32)
    for y, colour in enumerate([LETZT_EDGE, LETZT, LETZT_CORE, LETZT, LETZT_EDGE]):
        c.rect(0, y, 16, y + 1, colour)
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if abs(dx) + abs(dy) <= 2:
                c.put(2 + dx, 7 + dy, LETZT)
    c.put(2, 7, LETZT_CORE)
    return c


def seele_schneider_entity():
    """The Seele Schneider fired or stuck, in heilig_pfeil's layout. ArrowRenderer draws the right end
    of the shaft (x 16) forward, so the blade is on the right, leading in flight and buried when
    stuck; then the guard and the hilt. The end cap, at the back, is the pommel."""
    c = Canvas(32, 32)
    for y, (hilt, blade) in enumerate([(SILVER_SHADE, REISHI_EDGE), (SILVER, REISHI), (SILVER, REISHI_CORE),
                                       (SILVER, REISHI), (SILVER_SHADE, REISHI_EDGE)]):
        c.rect(0, y, 5, y + 1, hilt)
        c.rect(6, y, 16, y + 1, blade)
    c.rect(5, 0, 6, 5, GUARD)
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            distance = dx * dx + dy * dy
            if distance <= 5:  # a round 5x5 disc, its outer ring shaded
                c.put(2 + dx, 7 + dy, SILVER_SHADE if distance >= 4 else SILVER)
    return c


def halo_ring(c, left, top, gaps=()):
    """A ring of light on a 12x12 face of the halo, with these spans of angle (degrees, clockwise from
    the right as drawn) left clear."""
    cx, cy = left + 6, top + 6
    for y in range(top, top + 12):
        for x in range(left, left + 12):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            angle = math.degrees(math.atan2(dy, dx)) % 360
            if 4.5 <= math.hypot(dx, dy) < 6.0 and not any(start <= angle < end for start, end in gaps):
                c.put(x, y, HALO)


def vollstandig():
    """Vollständig's wings and halo (VollstandigModel). Each wing is one flat 16x24 box, front face
    (0,0)-(16,24) and back face (16,0)-(32,24). On both faces, and on both wings (the left one is
    mirrored), the root by the shoulder is at the side where the faces meet, so five feather spines
    fan down and out from there and the far edge is drawn in full. The halo is a flat 12x12 box, its
    two faces drawn the same way up, so the gaps of the broken halo line up above and below."""
    c = Canvas(64, 64)
    c.rect(0, 0, 32, 24, WING)
    for k in range(5):
        line(c, 14, 1, 12 - 3 * k, 23, WING_EDGE)  # front face
        line(c, 17, 1, 19 + 3 * k, 23, WING_EDGE)  # back face, its mirror image
    c.rect(0, 0, 1, 24, WING_EDGE)
    c.rect(31, 0, 32, 24, WING_EDGE)
    for left in (12, 24):
        halo_ring(c, left, 32)
        halo_ring(c, left, 48, gaps=((20, 60), (200, 230)))
    return c


def ginto_cast():
    """A Gintō spell's shapes, where GintoCastRenderer samples them: a disc of cloud for Wolke and
    Sprenger's ring (u 0..0.5, v 0..0.5), a column of reishi fading as it rises for Heizen and
    Sprenger (u 0.5..0.75, v 0..1), and Gritz's cross (u 0.75..1, v 0..0.25)."""
    c = Canvas(64, 64)
    for y in range(32):
        for x in range(32):
            r = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if 14.0 <= r < 15.5:
                c.put(x, y, CLOUD_EDGE)
            elif r < 15.0:
                c.put(x, y, CLOUD)
    for y in range(64):
        alpha = round(40 + (230 - 40) * y / 63)  # the bottom row is the column's foot
        for x in range(32, 48):
            out = abs(x + 0.5 - 40) / 8          # 0 at the middle, 1 at the edges
            mixed = (round(core + (edge - core) * out) for core, edge in zip(REISHI_CORE[:3], REISHI_EDGE[:3]))
            c.put(x, y, (*mixed, alpha))
    gritz_cross(c, 48, 0, 16)
    return c


TEXTURES = {
    "item/asauchi.png": asauchi,
    "item/zangetsu.png": zangetsu,
    "item/zabimaru.png": zabimaru,
    "item/zabimaru_shikai.png": zabimaru_shikai,
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
    "entity/getsuga_tensho.png": getsuga_tensho,
    "entity/zabimaru_segment.png": zabimaru_segment,
    "item/denreishinki.png": denreishinki,
    "item/quincy_cross.png": quincy_cross,
    "item/heilig_bogen_pulling_0.png": heilig_bogen(0),
    "item/heilig_bogen_pulling_1.png": heilig_bogen(1),
    "item/heilig_bogen_pulling_2.png": heilig_bogen(2),
    "item/quincy_tunic.png": quincy_tunic_icon,
    "item/quincy_trousers.png": quincy_trousers_icon,
    "item/quincy_shoes.png": quincy_shoes_icon,
    "models/armor/quincy_garb_layer_1.png": quincy_armor_layer_1,
    "models/armor/quincy_garb_layer_2.png": quincy_armor_layer_2,
    "item/wandenreich_cap.png": wandenreich_cap_icon,
    "item/wandenreich_coat.png": wandenreich_coat_icon,
    "item/wandenreich_trousers.png": wandenreich_trousers_icon,
    "item/wandenreich_boots.png": wandenreich_boots_icon,
    "models/armor/wandenreich_layer_1.png": wandenreich_armor_layer_1,
    "models/armor/wandenreich_layer_2.png": wandenreich_armor_layer_2,
    "item/mask_fragment.png": mask_fragment_icon,
    "item/hollow_bait.png": hollow_bait_icon,
    "entity/heilig_pfeil.png": heilig_pfeil,
    "entity/blut_vene.png": blut_vene,
    "item/sanrei_glove.png": sanrei_glove_icon,
    "item/seele_schneider.png": seele_schneider_icon,
    "item/seele_schneider_formed.png": seele_schneider_formed_icon,
    "item/empty_ginto.png": empty_ginto_icon,
    "item/ginto.png": ginto_icon,
    "item/quincy_zeichen.png": quincy_zeichen_icon,
    "item/letzt_bogen_pulling_0.png": letzt_bogen(0),
    "item/letzt_bogen_pulling_1.png": letzt_bogen(1),
    "item/letzt_bogen_pulling_2.png": letzt_bogen(2),
    "item/ginrei_kojaku_pulling_0.png": ginrei_kojaku(0),
    "item/ginrei_kojaku_pulling_1.png": ginrei_kojaku(1),
    "item/ginrei_kojaku_pulling_2.png": ginrei_kojaku(2),
    "mob_effect/gritz.png": gritz_icon,
    "entity/sanrei_glove.png": sanrei_glove_layer,
    "entity/letzt_stil.png": letzt_stil,
    "entity/letzt_pfeil.png": letzt_pfeil,
    "entity/seele_schneider.png": seele_schneider_entity,
    "entity/vollstandig.png": vollstandig,
    "entity/ginto_cast.png": ginto_cast,
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
