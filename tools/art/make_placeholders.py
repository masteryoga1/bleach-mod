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

# The Shin'o Academy's uniform: a white kosode trimmed in blue or red over a hakama of the trim's colour.
ACADEMY_WHITE = rgb(242, 242, 238)
ACADEMY_FOLD = rgb(212, 212, 206)
ACADEMY_BLUE = rgb(44, 78, 160)
ACADEMY_BLUE_FOLD = rgb(32, 58, 124)
ACADEMY_RED = rgb(170, 40, 46)
ACADEMY_RED_FOLD = rgb(128, 28, 34)

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

# The Menos Grande: a Gillian's black robe and long pale hands, and an Adjuchas's dark striped hide.
ROBE = rgb(20, 18, 24)
ROBE_FOLD = rgb(36, 32, 42)
HAND = rgb(200, 196, 188)
ADJ_HIDE = rgb(46, 48, 64)
ADJ_STRIPE = rgb(34, 36, 50)
ADJ_BELLY = rgb(84, 86, 104)

# The Shrieker: dark fur, wings of purple-grey membrane on bone spars, and a red tongue.
SHRIEKER_FUR = rgb(40, 34, 38)
SHRIEKER_WING = rgb(60, 48, 66)
SHRIEKER_TONGUE = rgb(150, 60, 70)

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
# Hihiō Zabimaru: great bone segments, shaded along their backs, red mane at each joint, a dark gum under the fangs.
HIHIO_BONE = rgb(226, 218, 196)
HIHIO_BONE_SHADE = rgb(170, 160, 136)
HIHIO_MANE = rgb(186, 44, 40)
HIHIO_GUM = rgb(90, 24, 28)
# Its baboon pelt: shaggy fur, streaked darker, with paler tips.
PELT_FUR = rgb(148, 116, 84)
PELT_SHADE = rgb(104, 80, 58)
PELT_TIP = rgb(196, 168, 128)
# Tensa Zangetsu: a slim black blade with a pale edge, a black hilt, and the chain broken off its end.
TENSA = rgb(24, 24, 28)
TENSA_SHADE = rgb(10, 10, 12)
TENSA_EDGE = rgb(128, 132, 142)
TENSA_GUARD = rgb(40, 40, 46)
TENSA_WRAP = rgb(32, 30, 36)
TENSA_WRAP_LIGHT = rgb(66, 62, 72)
CHAIN = rgb(92, 92, 100)
CHAIN_LIGHT = rgb(132, 132, 142)
# Its coat: blacker than the garb under it, with a dark grey lining and folds.
COAT = rgb(12, 12, 14)
COAT_FOLD = rgb(36, 34, 40)
COAT_LINING = rgb(58, 54, 62)

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
# The Wandenreich uniform, white with black bands and silver buttons; a fragment of a Hollow's mask,
# and the red glow in a ravenous Hollow player's eyes; a Visored's whiter mask and its red stripes; and
# the Hollow Bait, a pale wafer with a dark red heart.
WANDENREICH_WHITE = rgb(240, 240, 236)
WANDENREICH_BLACK = rgb(34, 34, 40)
WANDENREICH_SILVER = rgb(176, 184, 196)
WANDENREICH_FOLD = rgb(206, 206, 200)
MASK_BONE = rgb(234, 230, 218)
MASK_SHADE = rgb(188, 182, 168)
MASK_LINE = rgb(92, 88, 82)
EYE_RED = rgb(230, 40, 40)
VISORED_BONE = rgb(244, 242, 236)
VISORED_SHADE = rgb(206, 202, 192)
MASK_RED = rgb(176, 30, 36)
BAIT_WHITE = rgb(226, 220, 206)
BAIT_EDGE = rgb(150, 140, 124)
BAIT_RED = rgb(150, 30, 36)
BAIT_CORE = rgb(70, 14, 18)
# The white Arrancar uniform, the Shihakusho in reversed colours: white cloth with a black sash, and black
# tabi under white zori. The number tattoo's digits are pure white, because TattooLayer inks them by vertex colour.
ARRANCAR_WHITE = rgb(242, 242, 238)
ARRANCAR_FOLD = rgb(214, 214, 208)
ARRANCAR_BLACK = rgb(30, 30, 34)
TATTOO_GLYPH = rgb(255, 255, 255)
# An Arrancar NPC's pale skin.
ARRANCAR_SKIN = rgb(226, 214, 200)
# A Shinigami on duty's warm skin and short dark hair.
DUTY_SKIN = rgb(222, 184, 150)
DUTY_HAIR = rgb(44, 34, 30)
# Kisuke Urahara: fair skin, pale hair, a dark green coat over a darker robe, wooden sandals, and the green and white
# striped bucket hat the reference notes give him.
URAHARA_SKIN = rgb(232, 200, 170)
URAHARA_HAIR = rgb(222, 204, 150)
URAHARA_COAT = rgb(46, 90, 58)
URAHARA_ROBE = rgb(40, 44, 40)
URAHARA_GETA = rgb(120, 84, 50)
URAHARA_HAT_GREEN = rgb(46, 110, 64)
URAHARA_HAT_WHITE = rgb(237, 237, 224)
# The Tenshintai: a life-sized doll of pale cloth, stitched with dark seams, its head blank.
TENSHINTAI_CLOTH = rgb(214, 204, 184)
TENSHINTAI_SHADE = rgb(184, 172, 150)
TENSHINTAI_SEAM = rgb(92, 80, 70)
# The gatekeepers, by gate in the plan's order (GateState.NAMES): Kaiwan, Higonyūdō, Jidanbō and Danzōmaru.
GATE_KEEPER_SKIN, GATE_KEEPER_HAIR = rgb(196, 150, 112), rgb(40, 30, 26)
GATE_NAMES = ("shoryumon", "shuwaimon", "hakutomon", "kokuryomon")
# Each keeper's robe and sash.
GATE_KEEPER_ROBES = ((rgb(58, 92, 128), rgb(214, 196, 140)), (rgb(140, 52, 44), rgb(60, 44, 36)),
                     (rgb(232, 228, 214), rgb(52, 48, 60)), (rgb(36, 34, 40), rgb(150, 120, 70)))
# The Caja Negación: a slate cube, lit from above and its left, seamed in Negación's pale gold.
CAJA_FACE = rgb(0x3A, 0x3D, 0x48)
CAJA_SIDE = rgb(0x2A, 0x2C, 0x35)
CAJA_TOP = rgb(0x4A, 0x4E, 0x5A)
CAJA_SEAM = rgb(0xFF, 0xF2, 0xB0)  # Negación's pale gold
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
# A Visored's inner Hollow: its host's white double, with a Hollow's black eyes and yellow irises.
INNER_SKIN = rgb(232, 234, 238)
INNER_ROBE = rgb(208, 210, 216)
HOLLOW_IRIS = rgb(236, 200, 40)
# Zangetsu drawn out: a weathered old man's skin, grey at the jaw, and his shirt, which is the mod's own.
ZANGETSU_SKIN = rgb(206, 172, 140)
ZANGETSU_STUBBLE = rgb(126, 116, 106)
ZANGETSU_SHIRT = rgb(84, 80, 88)
# Zabimaru drawn out: the baboon's bare face, and its dark hands and feet; its fur is Hihiō Zabimaru's mane.
ZABIMARU_FACE = rgb(196, 110, 100)
ZABIMARU_PAW = rgb(70, 30, 30)
# Sekkiseki, the Seireiheki's stone: pale grey, faintly flecked, with darker joints.
SEKKISEKI = rgb(222, 222, 214)
SEKKISEKI_FLECK = rgb(204, 204, 197)
SEKKISEKI_SEAM = rgb(176, 176, 170)
# The Kōryū, the Dangai's current: translucent violet with brighter streaks wavering up it, flecked with light.
KORYU = (112, 64, 196, 120)
KORYU_STREAK = (176, 132, 255, 170)
KORYU_GLINT = (226, 208, 255, 200)
# Hueco Mundo's white sand, its sandstone and Hueco quartz: bone white, the sand a shade warm and the quartz a shade
# cold, so the two read apart.
WHITE_SAND = rgb(236, 234, 226)
WHITE_SAND_GRAIN = rgb(218, 215, 204)
WHITE_SAND_GLINT = rgb(248, 247, 243)
WHITE_SANDSTONE = rgb(226, 223, 212)
WHITE_SANDSTONE_BAND = rgb(208, 204, 191)
HUECO_QUARTZ = rgb(240, 241, 238)
HUECO_QUARTZ_GRAIN = rgb(218, 220, 218)
HUECO_QUARTZ_SEAM = rgb(196, 198, 196)
# Las Noches' stone and tiles, a cold white brighter than the sand, the sandstone, Hueco quartz and plaster, so the
# fortress reads apart from the ground it stands on; and its dark band.
LAS_NOCHES = rgb(246, 247, 250)
LAS_NOCHES_GRAIN = rgb(234, 236, 241)
LAS_NOCHES_LIT = rgb(253, 253, 254)
LAS_NOCHES_SEAM = rgb(210, 213, 221)
LAS_NOCHES_TRIM = rgb(38, 38, 44)
LAS_NOCHES_TRIM_GRAIN = rgb(48, 48, 55)
# Las Noches' sky, the clear day blue that lines the dome, with a paler haze and a near-white wisp.
LAS_NOCHES_SKY = rgb(122, 172, 230)
LAS_NOCHES_SKY_HAZE = rgb(136, 184, 236)
LAS_NOCHES_SKY_WISP = rgb(176, 208, 244)
# Seireitei's building blocks: white plaster; black plaster, a dark grey; and blue-grey fired roof tiles.
PLASTER = rgb(240, 238, 230)
PLASTER_GRAIN = rgb(230, 228, 219)
BLACK_PLASTER = rgb(58, 58, 63)
BLACK_PLASTER_GRAIN = rgb(67, 67, 72)
ROOF_TILE = rgb(80, 87, 99)
ROOF_TILE_RIDGE = rgb(106, 113, 126)
ROOF_TILE_SHADOW = rgb(48, 52, 62)
# Shōji, see-through paper on a dark wooden lattice; and tatami, woven straw with a dark cloth edge.
SHOJI_PAPER = (238, 234, 220, 214)
SHOJI_FRAME = rgb(74, 56, 40)
SHOJI_PANEL = rgb(98, 76, 56)
TATAMI = rgb(198, 192, 124)
TATAMI_WEAVE = rgb(178, 172, 106)
TATAMI_EDGE = rgb(40, 44, 42)
# The division banner: white cloth hung from a dark wooden rod, its division's glyph in black ink.
BANNER_CLOTH = rgb(242, 240, 234)
BANNER_INK = rgb(26, 24, 28)
BANNER_ROD = rgb(62, 46, 34)
# The great gates, faced in Sekkiseki and banded in dark iron.
GATE_IRON = rgb(50, 50, 56)
GATE_STUD = rgb(98, 98, 106)
# The wickets, dark oak boards strapped in the gates' iron.
WICKET_WOOD = rgb(68, 48, 32)
WICKET_JOINT = rgb(48, 34, 24)


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


# The guard's 卍, five pixels square, row by row from the top: the top arm turns left and the left arm down.
MANJI = ["XXX.X", "..X.X", "XXXXX", "X.X..", "X.XXX"]


def tensa_zangetsu():
    """Tensa Zangetsu: a slim black blade two pixels wide, longer than the Asauchi's, with a pale edge; the 卍 guard at
    its root; a black hilt; and a few links of chain trailing off the pommel, broken at the canvas edge."""
    c = Canvas(32, 32)
    for i in range(20):
        x, y = 11 + i, 20 - i
        c.put(x, y, TENSA)
        c.put(x - 1, y, TENSA_SHADE)
        c.put(x, y + 1, TENSA_EDGE if i < 19 else CLEAR)
    c.put(31, 0, TENSA_EDGE)
    for row, cells in enumerate(MANJI):
        for col, cell in enumerate(cells):
            if cell == "X":
                c.put(7 + col, 20 + row, TENSA_GUARD)
    for i in range(5):
        x, y = 7 - i, 25 + i
        c.put(x, y, TENSA_WRAP_LIGHT if i % 2 == 0 else TENSA_WRAP)
        c.put(x + 1, y, TENSA_WRAP)
    for k, (x, y) in enumerate([(2, 30), (1, 30), (1, 31), (0, 31)]):
        c.put(x, y, CHAIN if k % 2 == 0 else CHAIN_LIGHT)
    return c


def ragged(c, x0, x1, y, colour, every=3):
    """A torn hem along row y from x0 to x1 (end exclusive): every few pixels one hangs a row lower, and the one after
    is torn away."""
    for x in range(x0, x1):
        step = (x - x0) % every
        if step == 0:
            c.put(x, y, colour)
            c.put(x, y + 1, colour)
        elif step == 1:
            c.put(x, y, CLEAR)
        else:
            c.put(x, y, colour)


def tensa_zangetsu_coat():
    """Tensa Zangetsu's long black coat on the armour layout of the coat model (HumanoidModel's mesh, 64x64): the
    body closed at the chest and open down the front below it, sleeves to the wrist with torn cuffs, a skirt round
    the thighs open at the front with a torn hem above the knee, and the tails below (a 10x11x1 box at 0, 32), lined
    dark grey on the face against the back and torn along the bottom. The head, the hat and the shins stay clear."""
    c = Canvas(64, 64)
    # Body: shoulders, then the sides, the front and the back.
    c.rect(20, 16, 28, 20, COAT)
    c.rect(16, 20, 40, 32, COAT)
    c.rect(23, 26, 25, 32, CLEAR)  # open down the front from the waist
    for y in range(20, 26):        # the collar's V, in the fold colour
        c.put(20 + (y - 20) // 2, y, COAT_FOLD)
        c.put(27 - (y - 20) // 2, y, COAT_FOLD)
    for x in (17, 34, 38):         # folds down the sides and back
        c.rect(x, 21, x + 1, 32, COAT_FOLD)
    # Arms: the whole sleeve, torn at the cuff.
    c.rect(44, 16, 48, 20, COAT)
    c.rect(40, 20, 56, 30, COAT)
    c.rect(40, 23, 41, 30, COAT_FOLD)
    ragged(c, 40, 56, 30, COAT)
    # Legs: the skirt round the thigh, open at the front, torn above the knee.
    c.rect(0, 20, 4, 25, COAT)
    c.rect(8, 20, 16, 25, COAT)
    ragged(c, 0, 4, 25, COAT)
    ragged(c, 8, 16, 25, COAT)
    # Tails: lined on the face against the back, black outside, its last row torn to the hem's few hanging ends.
    box(c, 0, 32, 10, 11, 1, COAT)
    c.rect(1, 33, 11, 44, COAT_LINING)
    for x in (14, 17, 20):
        c.rect(x, 33, x + 1, 42, COAT_FOLD)
    c.rect(0, 43, 22, 44, CLEAR)
    ragged(c, 12, 22, 42, COAT, every=4)
    ragged(c, 1, 11, 42, COAT_LINING, every=4)
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


def hihio_zabimaru():
    """Hihiō Zabimaru: a great blade of bone on the diagonal, five pixels across, in segments with a tuft of red mane at
    each joint and a fang below each, a broader skull at its tip with a dark eye, and Zabimaru's red-wrapped hilt and
    bronze guard at its root."""
    c = Canvas(32, 32)
    for py in range(32):
        for px in range(32):
            along = (px - 8) - (py - 23)  # twice the steps up the blade from its guard
            across = (px - 8) + (py - 23)  # 0 down its middle, its back above, its edge below
            if -10 <= along < 0 and abs(across) <= 1:
                c.put(px, py, ZABI_WRAP if (along // 2) % 2 else ZABI_WRAP_DARK)
            elif 0 <= along < 2 and abs(across) <= 4:
                c.put(px, py, BRONZE)
            elif 2 <= along < 34 and abs(across) <= 2:
                if along % 9 >= 8:
                    if across <= 0:
                        c.put(px, py, HIHIO_MANE)
                else:
                    c.put(px, py, HIHIO_BONE_SHADE if across == -2 else HIHIO_BONE)
            elif 2 <= along < 34 and across == 3 and along % 9 in (1, 2):
                c.put(px, py, TOOTH)
            elif 2 <= along < 34 and across == -3 and along % 9 >= 7:
                c.put(px, py, HIHIO_MANE)
            elif 34 <= along < 42 and abs(across) <= 3:
                eye = along in (36, 37) and across in (-1, 0)
                c.put(px, py, HIHIO_GUM if eye else HIHIO_BONE_SHADE if abs(across) == 3 else HIHIO_BONE)
    return c


def hihio_zabimaru_segment():
    """One of Hihiō Zabimaru's bone segments, for the serpent: bone shaded down its middle, cracked, with a red mane along
    its back and fangs along a dark gum at its front."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, HIHIO_BONE)
    c.rect(0, 7, 16, 9, HIHIO_BONE_SHADE)
    c.rect(0, 0, 16, 2, HIHIO_MANE)
    for x in range(0, 16, 3):
        c.put(x, 2, HIHIO_MANE)
    for x in (4, 11):
        c.rect(x, 3, x + 1, 7, HIHIO_BONE_SHADE)
    c.rect(0, 12, 16, 13, HIHIO_GUM)
    for x in range(1, 16, 4):
        c.rect(x, 13, x + 2, 16, TOOTH)
    return c


def hihio_zabimaru_pelt():
    """Hihiō Zabimaru's baboon pelt on the pelt model's 64x32 layout: the mane about the neck (a 10x3x6 box at 0, 0), the
    mantle over the shoulders and chest (8x6x4 at 0, 9), the hide down the back (8x10x1 at 24, 9), torn along its foot,
    and a pad on each shoulder (4x4x4 at 44, 0), all in shaggy fur, streaked with its shade and tipped paler."""
    c = Canvas(64, 32)
    for u, v, w, h, d in ((0, 0, 10, 3, 6), (0, 9, 8, 6, 4), (24, 9, 8, 10, 1), (44, 0, 4, 4, 4)):
        box(c, u, v, w, h, d, PELT_FUR)
        for x0, y0, x1, y1 in faces(u, v, w, h, d):
            for x in range(x0, x1):
                if x % 3 == 1:
                    c.rect(x, y0 + (x % 2), x + 1, y1, PELT_SHADE)
                elif x % 3 == 2:
                    c.put(x, y0, PELT_TIP)
    for x in range(24, 42, 3):
        c.put(x, 19, CLEAR)
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


def academy(base, trim, trim_fold, name):
    """The Shihakusho's kosode or hakama in the Academy's colours: the kosode white with its collar in the trim, the
    hakama all in the trim. Layer 1's leg rows (tabi and straw) are drawn only for boots, which the uniform has none of."""
    top = base in (kosode_icon, armor_layer_1)
    mapping = {BLACK: ACADEMY_WHITE, FOLD: ACADEMY_FOLD, WHITE: trim} if top else {BLACK: trim, FOLD: trim_fold}
    return recoloured(base, mapping, name)


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


# The Gillian's boxes (GillianModel). One spike net serves the six collar spikes front and back, and
# another the two that stick out at the sides.
GILLIAN_ROBE_LOWER = (0, 0, 10, 18, 8)
GILLIAN_ROBE_UPPER = (36, 0, 9, 12, 7)
GILLIAN_HEAD = (68, 0, 8, 8, 8)
GILLIAN_NOSE = (100, 0, 2, 2, 7)
GILLIAN_SPIKE = (100, 10, 1, 1, 3)
GILLIAN_SIDE_SPIKE = (108, 10, 3, 1, 1)
GILLIAN_ARM = (0, 26, 3, 20, 3)
GILLIAN_FOOT = (12, 26, 3, 2, 5)


def gillian():
    """A black robe from the collar to the ground, long pale hands, and bone under the mask."""
    c = Canvas(128, 64)
    for part in (GILLIAN_ROBE_LOWER, GILLIAN_ROBE_UPPER, GILLIAN_ARM):
        box(c, *part, ROBE)
    for part in (GILLIAN_HEAD, GILLIAN_NOSE, GILLIAN_SPIKE, GILLIAN_SIDE_SPIKE):
        box(c, *part, BONE_SHADE)
    robe = faces(*GILLIAN_ROBE_LOWER)
    paint_where(c, [robe[3], robe[5]], ROBE_FOLD, lambda x, y: x % 3 == 0)  # folds down the front and back
    paint_where(c, faces(*GILLIAN_ARM)[2:], HAND, lambda x, y: y >= 45)  # the hands: the arms' bottom four rows
    c.rect(6, 26, 9, 29, HAND)  # and their bottom face
    box(c, *GILLIAN_FOOT, HAND)
    return c


def gillian_mask():
    """The long-nosed mask over the whole head, with its collar of spikes."""
    c = Canvas(128, 64)
    for part in (GILLIAN_HEAD, GILLIAN_NOSE, GILLIAN_SPIKE, GILLIAN_SIDE_SPIKE):
        box(c, *part, BONE)
    c.rect(77, 10, 79, 12, VOID)   # eye holes
    c.rect(81, 10, 83, 12, VOID)
    c.rect(77, 14, 83, 15, VOID)   # the mouth, with two teeth
    c.put(78, 14, BONE)
    c.put(81, 14, BONE)
    c.rect(76, 15, 84, 16, BONE_SHADE)  # chin
    c.rect(*faces(*GILLIAN_NOSE)[1], BONE_SHADE)  # underside of the nose
    return c


# The Adjuchas's boxes (AdjuchasModel). The four dorsal spikes share one net, as do the two horns and
# the four legs.
ADJ_BODY = (0, 0, 10, 9, 18)
ADJ_HEAD = (56, 0, 7, 7, 6)
ADJ_HORN = (82, 0, 1, 4, 1)
ADJ_SPIKE = (86, 0, 1, 4, 1)
ADJ_LEG = (0, 27, 3, 10, 3)
ADJ_TAIL = (12, 27, 2, 2, 12)
ADJ_TAIL_TIP = (40, 27, 2, 2, 10)


def adjuchas():
    """A dark, striped beast's hide with a hole through the flank."""
    c = Canvas(128, 64)
    for part in (ADJ_BODY, ADJ_HEAD, ADJ_LEG, ADJ_TAIL, ADJ_TAIL_TIP):
        box(c, *part, ADJ_HIDE)
    box(c, *ADJ_HORN, BONE)       # bone, as the mask draws them too
    box(c, *ADJ_SPIKE, BONE)
    paint_where(c, faces(*ADJ_BODY), ADJ_STRIPE, lambda x, y: (x + y) % 4 == 0)
    paint_where(c, faces(*ADJ_TAIL) + faces(*ADJ_TAIL_TIP), ADJ_STRIPE, lambda x, y: x % 3 == 0)
    c.rect(*faces(*ADJ_BODY)[1], ADJ_BELLY)
    c.rect(0, 37, 12, 40, BONE_SHADE)  # claws, the bottom three rows of the legs
    c.rect(8, 20, 12, 24, VOID)        # the hole, through the right flank
    return c


def adjuchas_mask():
    """A beast's skull over the snout, with slit eyes and a row of teeth."""
    c = Canvas(128, 64)
    c.rect(62, 6, 69, 13, BONE)    # face
    c.rect(62, 3, 69, 6, BONE)     # the front half of the top
    c.rect(59, 6, 62, 13, BONE)    # the front three columns of each side
    c.rect(69, 6, 72, 13, BONE)
    c.rect(63, 8, 65, 9, VOID)     # eye slits, mirrored about the middle column
    c.rect(66, 8, 68, 9, VOID)
    c.rect(62, 11, 69, 12, VOID)   # the mouth, and the teeth in it
    for x in (63, 65, 67):
        c.put(x, 11, BONE)
    box(c, *ADJ_HORN, BONE)
    box(c, *ADJ_SPIKE, BONE)
    return c


# The Shrieker's boxes (ShriekerModel), as (u, v, w, h, d). Each wing is a spar and a flat membrane; the left ones are
# mirrored, so one net serves both, as it does for the arms and legs.
SHRIEKER_BODY = (0, 0, 14, 16, 10)
SHRIEKER_HEAD = (48, 0, 8, 10, 7)
SHRIEKER_JAW = (78, 0, 6, 2, 5)
SHRIEKER_TONGUE_BOX = (100, 0, 2, 1, 8)
SHRIEKER_FORK = (100, 9, 1, 1, 3)   # the tongue's two tips, one net for both
SHRIEKER_ARM = (0, 26, 4, 18, 4)
SHRIEKER_LEG = (16, 26, 4, 9, 4)
SHRIEKER_SPAR = (60, 26, 22, 2, 2)
SHRIEKER_MEMBRANE = (0, 48, 22, 0, 16)


def shrieker():
    """Dark fur over a hunched body, long arms and short legs, bone claws, the hole through the chest, wings of dark
    membrane ribbed with shade on spars of bone, and a red forked tongue."""
    c = Canvas(128, 64)
    for part in (SHRIEKER_BODY, SHRIEKER_HEAD, SHRIEKER_JAW, SHRIEKER_ARM, SHRIEKER_LEG):
        box(c, *part, SHRIEKER_FUR)
    mottle(c, 0, 0, 78, 48, HOLLOW_SHADE, 5)
    box(c, *SHRIEKER_SPAR, BONE_SHADE)
    box(c, *SHRIEKER_MEMBRANE, SHRIEKER_WING)
    paint_where(c, faces(*SHRIEKER_MEMBRANE), HOLLOW_SHADE, lambda x, y: x % 5 == 0)  # the ribs
    box(c, *SHRIEKER_TONGUE_BOX, SHRIEKER_TONGUE)
    box(c, *SHRIEKER_FORK, SHRIEKER_TONGUE)
    c.rect(0, 45, 16, 48, BONE_SHADE)   # claws, round the bottom of the arms
    c.rect(15, 14, 19, 18, VOID)        # the hole, through the chest
    return c


def shrieker_mask():
    """A tall, narrow bone face over the front of the head: dark eyes either side of a ridge, and bared teeth along its
    foot; the front of the crown is bone too, so the mask shows from above."""
    c = Canvas(128, 64)
    x0, y0, x1, y1 = faces(*SHRIEKER_HEAD)[3]   # the front: (55, 7) to (63, 17)
    c.rect(x0, y0, x1, y1, BONE)
    c.rect(x0 + 3, y0 + 1, x1 - 3, y0 + 6, BONE_SHADE)   # the ridge down the middle
    c.rect(x0 + 1, y0 + 2, x0 + 3, y0 + 4, VOID)         # eyes
    c.rect(x1 - 3, y0 + 2, x1 - 1, y0 + 4, VOID)
    c.rect(x0 + 1, y1 - 3, x1 - 1, y1 - 1, VOID)         # the mouth, and the teeth across it
    for x in range(x0 + 1, x1 - 1, 2):
        c.put(x, y1 - 3, BONE)
        c.put(x + 1, y1 - 2, BONE)
    tx0, ty0, tx1, ty1 = faces(*SHRIEKER_HEAD)[0]   # the top: its front rows, nearest v + d
    c.rect(tx0, ty1 - 3, tx1, ty1, BONE)
    return c


# The Gate of Hell (HellGateModel), as (u, v, w, h, d): a post (both share it), the lintel, a door (the right one
# mirrored), the dark behind them, a door's skull and the blade; and its colours, bone-grey stone and darker doors.
HELL_POST = (0, 0, 2, 26, 10)
HELL_LINTEL = (24, 0, 20, 2, 10)
HELL_DOOR_BOX = (24, 12, 8, 26, 1)
HELL_DARK = (42, 12, 16, 26, 1)
HELL_SKULL = (76, 12, 3, 3, 1)
HELL_BLADE = (0, 40, 16, 2, 16)
HELL_STONE = rgb(118, 110, 102)
HELL_STONE_SHADE = rgb(90, 84, 78)
HELL_DOOR = rgb(66, 58, 56)


def hell_gate():
    """Two posts and a lintel of mottled bone-grey stone, two dark doors with a spine and ribs of bone down each, a skull
    on each door, the dark behind them, and the blade, steel with a darker edge down both its long sides."""
    c = Canvas(128, 64)
    for frame in (HELL_POST, HELL_LINTEL):
        box(c, *frame, HELL_STONE)
        u, v, w, h, d = frame
        mottle(c, u, v, u + 2 * (w + d), v + h + d, HELL_STONE_SHADE, 5)
    box(c, *HELL_DOOR_BOX, HELL_DOOR)
    x0, y0, x1, y1 = faces(*HELL_DOOR_BOX)[3]   # the front: a spine down the middle, ribs off it
    line(c, (x0 + x1) // 2, y0 + 2, (x0 + x1) // 2, y1 - 3, BONE_SHADE)
    for y in range(y0 + 4, y1 - 4, 3):
        line(c, x0 + 1, y, x1 - 2, y, BONE_SHADE)
    box(c, *HELL_DARK, VOID)
    box(c, *HELL_SKULL, BONE)
    x0, y0, _, _ = faces(*HELL_SKULL)[3]        # the skull's face: two dark eyes over a dark mouth
    c.put(x0, y0, BLACK)
    c.put(x0 + 2, y0, BLACK)
    c.put(x0 + 1, y0 + 2, BLACK)
    box(c, *HELL_BLADE, STEEL)
    for x0, y0, x1, y1 in faces(*HELL_BLADE)[:2]:   # its two broad faces: the long edges darker
        line(c, x0, y0, x0, y1 - 1, HOLLOW_SHADE)
        line(c, x1 - 1, y0, x1 - 1, y1 - 1, HOLLOW_SHADE)
    return c


# A Shrieker's leech (ShriekerLeechModel), as (u, v, w, h, d): its body, the sucker at its front, and a wing (the left
# one mirrored).
LEECH_BODY = rgb(56, 34, 48)
LEECH_BODY_BOX = (0, 0, 5, 4, 7)
LEECH_SUCKER = (24, 0, 3, 3, 1)
LEECH_WING = (0, 11, 5, 0, 4)


def shrieker_leech():
    """A swollen body of dark, oily hide flecked with shade, a small bone mask over its front about a red sucker, and
    wings of the Shrieker's own membrane."""
    c = Canvas(32, 16)
    box(c, *LEECH_BODY_BOX, LEECH_BODY)
    mottle(c, 0, 0, 24, 11, HOLLOW_SHADE, 4)
    c.rect(*faces(*LEECH_BODY_BOX)[3], BONE)   # the front: a mask about the sucker
    box(c, *LEECH_SUCKER, SHRIEKER_TONGUE)
    box(c, *LEECH_WING, SHRIEKER_WING)
    return c


# Grand Fisher's boxes (GrandFisherModel), as (u, v, w, h, d). One net serves both arms with their claws, and one
# both legs with theirs, the left ones mirrored.
GRAND_FISHER_FUR = rgb(96, 84, 72)
GRAND_FISHER_FUR_SHADE = rgb(70, 60, 52)
GRAND_FISHER_CLAW = rgb(176, 32, 40)
GRAND_FISHER_SKIN = rgb(158, 124, 118)
GRAND_FISHER_BODY = (0, 0, 14, 15, 11)
GRAND_FISHER_HEAD = (52, 0, 12, 10, 9)
GRAND_FISHER_EAR = (96, 0, 3, 3, 1)
GRAND_FISHER_STALK_ROOT = (96, 6, 2, 3, 2)
GRAND_FISHER_ARM = (0, 28, 5, 15, 5)
GRAND_FISHER_HAND = (22, 28, 5, 4, 2)
GRAND_FISHER_FOOT = (22, 36, 6, 2, 2)
GRAND_FISHER_LEG = (40, 28, 6, 8, 6)
GRAND_FISHER_TAIL = (66, 28, 2, 2, 16)


def grand_fisher():
    """Shaggy grey-brown fur over a hunched body, long arms and thick legs, red bird-like claws on the hands and feet,
    bare pink skin inside the ears, on the stalk's root and down the long tail, and the hole through the chest."""
    c = Canvas(128, 64)
    for part in (GRAND_FISHER_BODY, GRAND_FISHER_HEAD, GRAND_FISHER_ARM, GRAND_FISHER_LEG):
        box(c, *part, GRAND_FISHER_FUR)
    mottle(c, 0, 0, 94, 48, GRAND_FISHER_FUR_SHADE, 4)
    box(c, *GRAND_FISHER_EAR, GRAND_FISHER_FUR)
    c.rect(*faces(*GRAND_FISHER_EAR)[3], GRAND_FISHER_SKIN)   # the inside of the ear, at its front
    box(c, *GRAND_FISHER_STALK_ROOT, GRAND_FISHER_SKIN)
    box(c, *GRAND_FISHER_TAIL, GRAND_FISHER_SKIN)
    paint_where(c, faces(*GRAND_FISHER_TAIL), GRAND_FISHER_FUR_SHADE, lambda x, y: (x + y) % 4 == 0)  # its rings
    box(c, *GRAND_FISHER_HAND, GRAND_FISHER_CLAW)
    box(c, *GRAND_FISHER_FOOT, GRAND_FISHER_CLAW)
    x0, y0, x1, y1 = faces(*GRAND_FISHER_BODY)[3]   # the front of the chest: the hole, high in its middle
    c.rect(x0 + 5, y0 + 3, x1 - 5, y0 + 7, VOID)
    return c


def grand_fisher_mask():
    """A broad bone face over the front of the head: two great round eyes, a short ridge between them, and a wide mouth
    of long teeth along its foot; the front of the crown is bone too, so the mask shows from above."""
    c = Canvas(128, 64)
    x0, y0, x1, y1 = faces(*GRAND_FISHER_HEAD)[3]   # the front: (61, 9) to (73, 19)
    c.rect(x0, y0, x1, y1, BONE)
    for left in (x0 + 1, x1 - 5):                   # the eyes, rounded by their corners left as bone
        c.rect(left, y0 + 1, left + 4, y0 + 5, VOID)
        for x, y in ((left, y0 + 1), (left + 3, y0 + 1), (left, y0 + 4), (left + 3, y0 + 4)):
            c.put(x, y, BONE)
    c.rect(x0 + 5, y0 + 1, x1 - 5, y0 + 6, BONE_SHADE)   # the ridge between them
    c.rect(x0 + 1, y1 - 4, x1 - 1, y1 - 1, VOID)         # the mouth, and its long teeth from above and below
    for x in range(x0 + 1, x1 - 1, 2):
        c.rect(x, y1 - 4, x + 1, y1 - 2, BONE)
        c.put(x + 1, y1 - 2, BONE)
    tx0, ty0, tx1, ty1 = faces(*GRAND_FISHER_HEAD)[0]   # the top: its front rows, nearest v + d
    c.rect(tx0, ty1 - 3, tx1, ty1, BONE)
    return c


# A Vasto Lorde: a person's shape, in the humanoid layout the zombie's layer uses (the left arm and leg mirror the right
# ones' nets), each box as (u, v, w, h, d).
VASTO_HEAD = (0, 0, 8, 8, 8)
VASTO_BODY = (16, 16, 8, 12, 4)
VASTO_ARM = (40, 16, 4, 12, 4)
VASTO_LEG = (0, 16, 4, 12, 4)
# The bone of the looks, each net in the texture's lower half, which the humanoid mesh leaves free: the plated look's
# plate over the top of the chest and its pauldron over each shoulder (the left mirroring the right), on the body; the
# horned look's two horns and the crested look's crest, on the mask.
VASTO_PLATE = (36, 32, 8, 3, 1)
VASTO_PAULDRON = (0, 48, 5, 2, 5)
VASTO_HORNS = ((0, 32, 2, 5, 2), (8, 32, 2, 5, 2))
VASTO_CREST = (16, 32, 1, 3, 8)


def vasto_lorde():
    """A Vasto Lorde's hide: black and faintly mottled over the head, body, arm and leg, the hole through the middle of
    the chest, and bone-white claws round the last row of each hand and foot; and the plated look's bone, shaded along
    the foot of each side. The hat layer stays clear."""
    c = Canvas(64, 64)
    for part in (VASTO_HEAD, VASTO_BODY, VASTO_ARM, VASTO_LEG):
        box(c, *part, HOLLOW_BLACK)
    mottle(c, 0, 0, 56, 32, HOLLOW_SHADE, 5)
    x0, y0, x1, y1 = faces(*VASTO_BODY)[3]   # the chest's front: (20, 20) to (28, 32)
    hole(c, x0 + 2, y0 + 5)
    # The corners it rounds off its rim are cleared, which on a hide would be holes right through: hide again.
    paint_where(c, [(x0, y0, x1, y1)], HOLLOW_BLACK, lambda x, y: c.get(x, y)[3] == 0)
    for part in (VASTO_ARM, VASTO_LEG):
        for x0, y0, x1, y1 in faces(*part)[2:]:   # its right, front, left and back, each its last row
            c.rect(x0, y1 - 1, x1, y1, BONE)
    for part in (VASTO_PLATE, VASTO_PAULDRON):
        box(c, *part, BONE)
        for x0, y0, x1, y1 in faces(*part)[2:]:
            c.rect(x0, y1 - 1, x1, y1, BONE_SHADE)
    return c


def vasto_lorde_mask():
    """A narrow bone face over the front of the head: two slits for eyes slanting down towards a shaded ridge between
    them, and a mouth of fangs along its foot; the face wraps a pixel round each side, and the crown's front rows are
    bone too, so the mask shows from aside and above. The horned look's horns and the crested look's crest are bone,
    the horns shaded at the back and the crest along its left."""
    c = Canvas(64, 64)
    x0, y0, x1, y1 = faces(*VASTO_HEAD)[3]   # the front: (8, 8) to (16, 16)
    c.rect(x0, y0, x1, y1, BONE)
    c.rect(x0 + 3, y0 + 1, x0 + 5, y0 + 5, BONE_SHADE)
    for dx, dy in ((1, 3), (2, 3), (2, 4), (5, 3), (6, 3), (5, 4)):
        c.put(x0 + dx, y0 + dy, VOID)
    c.rect(x0 + 1, y0 + 6, x1 - 1, y0 + 7, VOID)
    for dx in (2, 5):                         # the fangs, from above
        c.put(x0 + dx, y0 + 6, BONE)
    rx0, ry0, rx1, ry1 = faces(*VASTO_HEAD)[2]   # the right side: its column nearest the front
    c.rect(rx1 - 1, ry0, rx1, ry1 - 1, BONE)
    lx0, ly0, lx1, ly1 = faces(*VASTO_HEAD)[4]   # the left side: its column nearest the front
    c.rect(lx0, ly0, lx0 + 1, ly1 - 1, BONE)
    tx0, ty0, tx1, ty1 = faces(*VASTO_HEAD)[0]   # the crown: its front rows, nearest v + d
    c.rect(tx0, ty1 - 2, tx1, ty1, BONE)
    for horn in VASTO_HORNS:
        box(c, *horn, BONE)
        c.rect(*faces(*horn)[5], BONE_SHADE)
    box(c, *VASTO_CREST, BONE)
    c.rect(*faces(*VASTO_CREST)[4], BONE_SHADE)
    return c


# Runuganga's boxes (RunugangaModel) as (u, v, w, h, d), and his colours: grey sand grained darker, a brown cloth,
# darker bands, and the paler packed sand of his face.
RUNU_BASE = (0, 0, 16, 14, 12)
RUNU_WAIST = (56, 0, 17, 4, 13)
RUNU_TURRET = (116, 0, 3, 3, 3)
RUNU_ARM = (116, 6, 3, 9, 3)
RUNU_TORSO = (0, 26, 14, 18, 10)
RUNU_HEAD = (48, 26, 10, 11, 10)
RUNU_FOREARM = (88, 26, 7, 12, 7)
RUNU_BAND = (48, 47, 8, 2, 8)
RUNU_SAND = rgb(176, 172, 160)
RUNU_GRAIN = rgb(150, 146, 134)
RUNU_CLOTH = rgb(112, 92, 70)
RUNU_CLOTH_FOLD = rgb(88, 72, 56)
RUNU_BAND_GREY = rgb(96, 92, 84)
RUNU_FACE = rgb(214, 210, 198)


def runuganga():
    """Grained grey sand, a folded brown cloth at the waist, darker wrist bands, and the hole through the chest."""
    c = Canvas(128, 64)
    for part in (RUNU_BASE, RUNU_TURRET, RUNU_ARM, RUNU_TORSO, RUNU_HEAD, RUNU_FOREARM):
        box(c, *part, RUNU_SAND)
    mottle(c, 0, 0, 128, 64, RUNU_GRAIN, 5, seed=3)
    box(c, *RUNU_WAIST, RUNU_CLOTH)
    paint_where(c, faces(*RUNU_WAIST)[2:], RUNU_CLOTH_FOLD, lambda x, y: x % 4 == 0)
    box(c, *RUNU_BAND, RUNU_BAND_GREY)
    for x0, y0, x1, y1 in (faces(*RUNU_TORSO)[3], faces(*RUNU_TORSO)[5]):   # the chest's front and its back
        c.rect(x0 + 4, y0 + 5, x1 - 4, y0 + 11, VOID)
    return c


def runuganga_mask():
    """His face, the mask a Hollow is struck in: pale packed sand under a shaded brow, dark eyes and mouth."""
    c = Canvas(128, 64)
    x0, y0, x1, y1 = faces(*RUNU_HEAD)[3]   # the front: (58, 36) to (68, 47)
    c.rect(x0, y0, x1, y1, RUNU_FACE)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 2, RUNU_GRAIN)
    c.rect(x0 + 2, y0 + 3, x0 + 4, y0 + 5, VOID)
    c.rect(x1 - 4, y0 + 3, x1 - 2, y0 + 5, VOID)
    c.rect(x0 + 3, y0 + 7, x1 - 3, y0 + 9, VOID)
    return c


# Grand Fisher's lure: a small girl, on the slim player skin's layout, and the stalk she hangs from.
LURE_SKIN = rgb(238, 206, 186)
LURE_HAIR = rgb(58, 40, 34)
LURE_DRESS = rgb(176, 198, 224)
LURE_SHOES = rgb(120, 52, 58)
LURE_TEAR = rgb(150, 196, 236)
STALK_FLESH = rgb(150, 118, 112)
STALK_RING = rgb(112, 84, 80)


def grand_fisher_lure():
    """A small girl in the slim player skin's layout: pale skin, long dark hair over the top, back and sides of the head
    and a fringe down the face, her eyes shut tight over tears running down her cheeks, a pale blue dress with short
    sleeves and a skirt to the knee, and red shoes. Every overlay layer stays clear."""
    c = Canvas(64, 64)
    # Head, body, right arm, left arm (slim, three wide), right leg, left leg: where each net starts, and the box's size.
    for u, v, w, h, d in ((0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 3, 12, 4), (32, 48, 3, 12, 4),
                          (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)):
        box(c, u, v, w, h, d, LURE_SKIN)
    c.rect(8, 0, 16, 8, LURE_HAIR)        # the top of the head
    c.rect(0, 8, 8, 16, LURE_HAIR)        # its right side, the hair down to the jaw
    c.rect(16, 8, 32, 16, LURE_HAIR)      # its left side and back
    c.rect(8, 8, 16, 10, LURE_HAIR)       # the fringe, two rows down the face
    c.rect(9, 11, 11, 12, VOID)           # eyes shut tight, and the tears beneath them
    c.rect(13, 11, 15, 12, VOID)
    for x in (9, 14):
        c.rect(x, 12, x + 1, 15, LURE_TEAR)
    # The dress: over the whole body and the shoulders, the sleeves' top three rows and the skirt's top five on each leg.
    for u, v, w, d, rows in ((16, 16, 8, 4, 12), (40, 16, 3, 4, 3), (32, 48, 3, 4, 3), (0, 16, 4, 4, 5),
                             (16, 48, 4, 4, 5)):
        for x0, y0, x1, y1 in faces(u, v, w, 12, d)[2:]:
            c.rect(x0, y0, x1, y0 + rows, LURE_DRESS)
        c.rect(*faces(u, v, w, 12, d)[0], LURE_DRESS)
    for u, v in ((0, 16), (16, 48)):      # the shoes, round the bottom of each leg and under it
        for x0, y0, x1, y1 in faces(u, v, 4, 12, 4)[2:]:
            c.rect(x0, y1 - 2, x1, y1, LURE_SHOES)
        c.rect(*faces(u, v, 4, 12, 4)[1], LURE_SHOES)
    return c


def grand_fisher_stalk():
    """The lure's stalk: dull pink flesh in rings, drawn whole on each face of each segment."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, STALK_FLESH)
    for y in range(1, 16, 4):
        c.rect(0, y, 16, y + 1, STALK_RING)
    mottle(c, 0, 0, 16, 16, STALK_RING, 7)
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


def hell_butterfly():
    """A Jigokuchō: a black body between purple wings edged in black, two antennae curling up from the head."""
    c = Canvas(16, 16)
    rows = [(2, 11, 14), (3, 10, 15), (4, 9, 16), (5, 9, 16), (6, 9, 15), (7, 9, 14), (8, 9, 13),
            (9, 9, 13), (10, 9, 14), (11, 9, 14), (12, 10, 13)]
    wing = set()
    for y, x0, x1 in rows:  # the right wings, mirrored to the left about the body
        for x in range(x0, x1):
            wing |= {(x, y), (15 - x, y)}
    for x, y in wing:
        inside = all((x + dx, y + dy) in wing for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c.put(x, y, PURPLE if inside else BLACK)
    c.rect(7, 4, 9, 13, BLACK)  # the body
    for x, y in [(6, 3), (5, 2), (9, 3), (10, 2)]:  # the antennae
        c.put(x, y, BLACK)
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


def arrancar_jacket_icon():
    c = Canvas(16, 16)
    c.rect(4, 2, 12, 15, ARRANCAR_WHITE)      # body
    c.rect(1, 3, 4, 12, ARRANCAR_WHITE)       # sleeves, with black cuffs
    c.rect(12, 3, 15, 12, ARRANCAR_WHITE)
    c.rect(1, 11, 4, 12, ARRANCAR_BLACK)
    c.rect(12, 11, 15, 12, ARRANCAR_BLACK)
    for x, y in [(5, 2), (6, 3), (7, 4), (10, 2), (9, 3), (8, 4)]:  # the lapels' edges, meeting in a V
        c.put(x, y, ARRANCAR_BLACK)
    c.rect(4, 10, 12, 12, ARRANCAR_BLACK)     # sash
    c.rect(5, 12, 6, 15, ARRANCAR_FOLD)       # a fold below it
    return c


def arrancar_hakama_icon():
    c = Canvas(16, 16)
    c.rect(3, 2, 13, 4, ARRANCAR_WHITE)       # waist, under the black sash
    c.rect(3, 2, 13, 3, ARRANCAR_BLACK)
    c.rect(2, 4, 7, 15, ARRANCAR_WHITE)       # wide legs
    c.rect(9, 4, 14, 15, ARRANCAR_WHITE)
    c.rect(7, 4, 9, 7, ARRANCAR_WHITE)        # crotch
    c.rect(4, 6, 5, 15, ARRANCAR_FOLD)        # pleats
    c.rect(11, 6, 12, 15, ARRANCAR_FOLD)
    return c


def arrancar_zori_icon():
    c = Canvas(16, 16)
    for left in (2, 9):
        c.rect(left + 1, 6, left + 4, 12, ARRANCAR_BLACK)  # tabi
        c.rect(left, 12, left + 5, 14, ARRANCAR_WHITE)     # sole
        c.put(left + 1, 11, ARRANCAR_WHITE)                # the thong over the foot
        c.put(left + 3, 11, ARRANCAR_WHITE)
    return c


def arrancar_uniform_layer_1():
    """The jacket on the body and arms, and the zori over black tabi on the legs (the zori use this layer). The head
    stays clear: the uniform has no headpiece."""
    c = Canvas(64, 32)
    # Body: a white jacket whose lapels meet in a black-edged V at the front, a black sash low round the waist,
    # and folds at the sides.
    c.rect(20, 16, 36, 20, ARRANCAR_WHITE)
    c.rect(16, 20, 40, 32, ARRANCAR_WHITE)
    for x, y in [(21, 20), (22, 21), (23, 22), (23, 23), (26, 20), (25, 21), (24, 22), (24, 23)]:
        c.put(x, y, ARRANCAR_BLACK)
    c.rect(16, 28, 40, 30, ARRANCAR_BLACK)
    c.rect(17, 22, 18, 28, ARRANCAR_FOLD)
    c.rect(33, 22, 34, 28, ARRANCAR_FOLD)
    # Arms: sleeves to the wrist, with black cuffs.
    c.rect(44, 16, 52, 20, ARRANCAR_WHITE)
    c.rect(40, 20, 56, 32, ARRANCAR_WHITE)
    c.rect(40, 31, 56, 32, ARRANCAR_BLACK)
    # Legs: black tabi over the bottom rows, the white edge and underside of the sole, and a white thong across
    # the front of the foot.
    c.rect(0, 28, 16, 31, ARRANCAR_BLACK)
    c.rect(0, 31, 16, 32, ARRANCAR_WHITE)
    c.rect(8, 16, 12, 20, ARRANCAR_WHITE)
    for x, y in [(4, 29), (5, 30), (6, 30), (7, 29)]:
        c.put(x, y, ARRANCAR_WHITE)
    return c


def arrancar_uniform_layer_2():
    """The hakama, from a black sash low on the waist down to the tabi, pleated all round."""
    c = Canvas(64, 32)
    c.rect(16, 26, 40, 32, ARRANCAR_WHITE)    # the waist, low on the body
    c.rect(16, 27, 40, 29, ARRANCAR_BLACK)    # sash
    c.rect(4, 16, 8, 20, ARRANCAR_WHITE)      # top of the legs
    c.rect(0, 20, 16, 30, ARRANCAR_WHITE)
    for u in range(1, 16, 2):                 # pleats, every other column
        c.rect(u, 22, u + 1, 30, ARRANCAR_FOLD)
    return c


def caja_negacion_icon():
    """The Caja Negación: a small slate cube in three-quarter view, its edges seamed in Negación's pale gold."""
    c = Canvas(16, 16)
    c.rect(3, 6, 10, 14, CAJA_FACE)     # the front face
    c.rect(10, 4, 13, 12, CAJA_SIDE)    # the right side
    c.rect(5, 3, 12, 6, CAJA_TOP)       # the top, short of the side's corner below the seam
    for x0, y0, x1, y1 in [(3, 6, 10, 6), (3, 13, 10, 13), (3, 6, 3, 13), (10, 6, 10, 13),
                           (5, 3, 13, 3), (13, 3, 13, 11), (3, 6, 5, 3), (10, 6, 13, 3), (10, 13, 13, 11)]:
        line(c, x0, y0, x1, y1, CAJA_SEAM)
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


def hole(c, x, y):
    """A hole four pixels across at x..x+3, y..y+3, its corners rounded off into a dark rim around it."""
    c.rect(x - 1, y - 1, x + 5, y + 5, HOLLOW_SHADE)
    c.rect(x, y, x + 4, y + 4, VOID)
    for dx, dy in ((0, 0), (3, 0), (0, 3), (3, 3)):
        c.put(x + dx, y + dy, HOLLOW_SHADE)
    for dx, dy in ((-1, -1), (4, -1), (-1, 4), (4, 4)):
        c.put(x + dx, y + dy, CLEAR)


def forearm(c, u, v, front):
    """The lower half of an arm (or sleeve) whose net starts at u, v: dark all round, a bone plate on the front
    and three claw tips on the front's last row. The columns used fit the wide and the slim arm alike."""
    c.rect(u, v + 10, u + 16, v + 16, HOLLOW_BLACK)
    mottle(c, u, v + 10, u + 16, v + 16, HOLLOW_SHADE, 5, seed=u)
    c.rect(front, v + 10, front + 3, v + 13, BONE)
    c.rect(front, v + 13, front + 3, v + 14, BONE_SHADE)
    c.put(front, v + 15, BONE)
    c.put(front + 1, v + 15, VOID)
    c.put(front + 2, v + 15, BONE)


def hollow_player():
    """A Hollow player's body over its skin, in the player skin layout: a hole through the chest, front and back,
    on the body and on the jacket layer; dark forearms with bone plates and claws, on the arms and the sleeves.
    Everything else stays clear, so the skin shows through."""
    c = Canvas(64, 64)
    for x, y in ((22, 23), (34, 23), (22, 39), (34, 39)):  # body front, body back, jacket front, jacket back
        hole(c, x, y)
    # Right arm, right sleeve, left arm, left sleeve: where each net starts, and the first column of its front.
    for u, v, front in ((40, 16, 44), (40, 32, 44), (32, 48, 36), (48, 48, 52)):
        forearm(c, u, v, front)
    return c


def bone_plate(c, x0, y0, x1, y1):
    """A plate of bone over x0..x1-1, y0..y1-1, shaded along its bottom row."""
    c.rect(x0, y0, x1, y1 - 1, BONE)
    c.rect(x0, y1 - 1, x1, y1, BONE_SHADE)


def hollow_vasto_lorde():
    """A Vasto Lorde's bone, drawn over a Hollow player's body (hollow_player) in the player skin layout: three
    ribs each side of the chest hole, curving in round it, caps on the tops of the shoulders and plates down the
    fronts of the shins, on the skin and on the jacket, sleeve and trouser layers alike. The hole itself and
    everything else stay clear, so the body and the skin show through."""
    c = Canvas(64, 64)
    for top in (20, 36):  # body front, jacket front
        holed = Canvas(64, 64)
        hole(holed, 22, top + 3)  # where hollow_player draws the hole, which the ribs leave clear
        for y in (top + 2, top + 5, top + 8):
            for x in (20, 21, 22, 25, 26, 27):
                if holed.get(x, y) == CLEAR:
                    c.put(x, y, BONE if x in (20, 27) else BONE_SHADE)
    # Right arm, right sleeve, left arm, left sleeve: the top face of each.
    for u, v in ((44, 16), (44, 32), (36, 48), (52, 48)):
        bone_plate(c, u, v, u + 4, v + 4)
    # Right leg, right trouser leg, left leg, left trouser leg: the lower half of each front.
    for x, y in ((4, 26), (4, 42), (20, 58), (4, 58)):
        bone_plate(c, x, y, x + 4, y + 6)
    return c


def arrancar_hole():
    """An Arrancar's hole over its skin, in the player skin layout: hollow_player's hole alone, through the chest
    front and back, on the body and on the jacket layer. SoulLayer moves it to the throat or the belly by player."""
    c = Canvas(64, 64)
    for x, y in ((22, 23), (34, 23), (22, 39), (34, 39)):  # body front, body back, jacket front, jacket back
        hole(c, x, y)
    return c


# The digits of an Arrancar's number tattoo, 0 to 9, each three pixels wide and five tall, a row to a string.
DIGIT_GLYPHS = [
    ("###", "#.#", "#.#", "#.#", "###"),
    (".#.", "##.", ".#.", ".#.", "###"),
    ("###", "..#", "###", "#..", "###"),
    ("###", "..#", ".##", "..#", "###"),
    ("#.#", "#.#", "###", "..#", "..#"),
    ("###", "#..", "###", "..#", "###"),
    ("###", "#..", "###", "#.#", "###"),
    ("###", "..#", "..#", ".#.", ".#."),
    ("###", "#.#", "###", "#.#", "###"),
    ("###", "#.#", "###", "..#", "###"),
]


def arrancar_digits():
    """The atlas TattooLayer draws an Arrancar's number from: digit d at x 4d to 4d+2, y 1 to 5, in white, which
    the layer inks by vertex colour (dark on skin, pale on a released forearm). Everything else stays clear."""
    c = Canvas(40, 8)
    for d, glyph in enumerate(DIGIT_GLYPHS):
        for y, row in enumerate(glyph):
            for x, cell in enumerate(row):
                if cell == "#":
                    c.put(4 * d + x, 1 + y, TATTOO_GLYPH)
    return c


def arrancar_npc():
    """The skin every Arrancar NPC wears under its uniform, in the player skin layout with wide arms: pale skin on the
    head, body, arms and legs, black hair over the top, back and sides of the head and down the face to its third row,
    and two dark eyes beneath, with no mouth. Every overlay layer (hat, jacket, sleeves, trousers) stays clear. The
    hole, the mask remnant and the tattoo are drawn over it by the renderer, so a bare chest shows them."""
    c = Canvas(64, 64)
    # Head, body, right arm, left arm, right leg, left leg: where each net starts, and the box's size.
    for u, v, w, h, d in ((0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (32, 48, 4, 12, 4),
                          (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)):
        box(c, u, v, w, h, d, ARRANCAR_SKIN)
    c.rect(8, 0, 16, 8, HOLLOW_BLACK)     # the top of the head
    c.rect(0, 8, 8, 16, HOLLOW_BLACK)     # its right side
    c.rect(16, 8, 32, 16, HOLLOW_BLACK)   # its left side and back
    c.rect(8, 8, 16, 11, HOLLOW_BLACK)    # the hair down the face, to its third row
    c.rect(9, 12, 11, 13, VOID)           # the eyes, two pixels wide, either side of the middle
    c.rect(13, 12, 15, 13, VOID)
    return c


def duty_shinigami():
    """The skin a Shinigami on duty wears under its Shihakushō, in the player skin layout with wide arms: warm skin on
    the head, body, arms and legs, short dark hair over the top, back and sides of the head and two rows down the face,
    and two dark eyes beneath. Every overlay layer (hat, jacket, sleeves, trousers) stays clear: the renderer draws the
    Shihakushō and the blade at the hip over it."""
    c = Canvas(64, 64)
    # Head, body, right arm, left arm, right leg, left leg: where each net starts, and the box's size.
    for u, v, w, h, d in ((0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (32, 48, 4, 12, 4),
                          (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)):
        box(c, u, v, w, h, d, DUTY_SKIN)
    c.rect(8, 0, 16, 8, DUTY_HAIR)        # the top of the head
    c.rect(0, 8, 8, 16, DUTY_HAIR)        # its right side
    c.rect(16, 8, 32, 16, DUTY_HAIR)      # its left side and back
    c.rect(8, 8, 16, 10, DUTY_HAIR)       # the fringe, two rows down the face
    c.rect(9, 12, 11, 13, VOID)           # the eyes, two pixels wide, either side of the middle
    c.rect(13, 12, 15, 13, VOID)
    return c


def kisuke_urahara():
    """Kisuke Urahara's placeholder, in the player skin layout with wide arms: a dark robe on the body and legs under a
    dark green coat open down the front, its sleeves to the wrist, bare hands, wooden sandals, pale hair over a fair
    face, and on the hat layer his striped bucket hat over the crown and the top three rows. The other overlays stay
    clear."""
    c = Canvas(64, 64)
    box(c, 0, 0, 8, 8, 8, URAHARA_SKIN)
    box(c, 16, 16, 8, 12, 4, URAHARA_ROBE)
    # The coat: the body's sides and back, and two columns down its front either side of the robe.
    body = faces(16, 16, 8, 12, 4)
    for face in (2, 4, 5):
        c.rect(*body[face], URAHARA_COAT)
    x0, y0, x1, y1 = body[3]
    c.rect(x0, y0, x0 + 2, y1, URAHARA_COAT)
    c.rect(x1 - 2, y0, x1, y1, URAHARA_COAT)
    # Arms in the coat's sleeves, the last two rows and the end bare; legs robed to the last row, sandals beneath.
    for (u, v), cloth, end, rows in (((40, 16), URAHARA_COAT, URAHARA_SKIN, 2), ((32, 48), URAHARA_COAT, URAHARA_SKIN, 2),
                                     ((0, 16), URAHARA_ROBE, URAHARA_GETA, 1), ((16, 48), URAHARA_ROBE, URAHARA_GETA, 1)):
        box(c, u, v, 4, 12, 4, cloth)
        nets = faces(u, v, 4, 12, 4)
        c.rect(*nets[1], end)
        for x0, y0, x1, y1 in nets[2:]:
            c.rect(x0, y1 - rows, x1, y1, end)
    # Pale hair over the top, sides and back of the head and a ragged fringe; two dark eyes beneath it.
    c.rect(8, 0, 16, 8, URAHARA_HAIR)
    c.rect(0, 8, 8, 14, URAHARA_HAIR)
    c.rect(16, 8, 32, 14, URAHARA_HAIR)
    for x in range(8, 16):
        c.rect(x, 8, x + 1, 10 if x % 3 else 11, URAHARA_HAIR)
    c.rect(9, 12, 11, 13, VOID)
    c.rect(13, 12, 15, 13, VOID)
    # The hat layer (net at 32, 0): stripes two pixels wide over the crown and down the top three rows of each side.
    top, _, *sides = faces(32, 0, 8, 8, 8)
    for x0, y0, x1, y1 in [top] + [(x0, y0, x1, y0 + 3) for x0, y0, x1, _ in sides]:
        for x in range(x0, x1):
            c.rect(x, y0, x + 1, y1, URAHARA_HAT_GREEN if (x // 2) % 2 == 0 else URAHARA_HAT_WHITE)
    return c


def training_dummy():
    """A training dummy of bound straw, in the humanoid layout the zombie's layer uses (the left arm and leg mirror the
    right ones' nets): straw on the head, body, arm and leg, dark straw cords round the neck, waist, wrist and ankle,
    and a dark cross on the face for a target. The hat layer stays clear."""
    c = Canvas(64, 64)
    # Head, body, arm and leg: where each net starts, and the box's size.
    for u, v, w, h, d in ((0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)):
        box(c, u, v, w, h, d, STRAW)
        mottle(c, u, v, u + 2 * (w + d), v + d + h, STRAW_DARK, 7)
    c.rect(16, 20, 40, 21, STRAW_DARK)    # the cord round the neck
    c.rect(16, 30, 40, 31, STRAW_DARK)    # round the waist
    c.rect(40, 30, 56, 31, STRAW_DARK)    # round the wrist
    c.rect(0, 30, 16, 31, STRAW_DARK)     # round the ankle
    line(c, 9, 9, 14, 14, STRAW_DARK)     # the cross on the face
    line(c, 14, 9, 9, 14, STRAW_DARK)
    return c


def tenshintai():
    """A Tenshintai, a doll faintly like a person, in the humanoid layout the zombie's layer uses (the left arm and leg
    mirror the right ones' nets): pale cloth on the head, body, arm and leg, shaded on the back and underneath, a dark
    seam round the neck, shoulder and hip and round the waist, wrist and ankle, and down the middle of the body before
    and behind, and a head with no face. The hat layer stays clear."""
    c = Canvas(64, 64)
    for u, v, w, h, d in ((0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)):
        box(c, u, v, w, h, d, TENSHINTAI_CLOTH)
        net = faces(u, v, w, h, d)
        for x0, y0, x1, y1 in (net[1], net[5]):    # underneath and the back
            c.rect(x0, y0, x1, y1, TENSHINTAI_SHADE)
    for y in (20, 30):
        c.rect(0, y, 56, y + 1, TENSHINTAI_SEAM)   # round the hip, neck and shoulder, then the ankle, waist and wrist
    c.rect(23, 21, 25, 30, TENSHINTAI_SEAM)       # down the middle of the body's front
    c.rect(35, 21, 37, 30, TENSHINTAI_SEAM)       # and of its back
    return c


def tenshintai_icon():
    """The doll in the hand, seen from the front: a pale cloth figure with a blank head, its arms held a little off its
    sides, its far edges in shade, and a dark seam down its middle and round its waist."""
    c = Canvas(16, 16)
    for x0, y0, x1, y1 in ((6, 1, 10, 5), (5, 5, 11, 11), (3, 6, 4, 12), (12, 6, 13, 12), (4, 6, 5, 7), (11, 6, 12, 7),
                           (5, 11, 7, 15), (9, 11, 11, 15)):
        c.rect(x0, y0, x1, y1, TENSHINTAI_CLOTH)
    for x0, y0, x1, y1 in ((9, 1, 10, 5), (10, 5, 11, 11), (12, 6, 13, 12), (10, 11, 11, 15)):
        c.rect(x0, y0, x1, y1, TENSHINTAI_SHADE)
    c.rect(7, 5, 9, 11, TENSHINTAI_SEAM)
    c.rect(5, 9, 11, 10, TENSHINTAI_SEAM)
    return c


def gate_keeper(gate):
    """A drawing function for a gatekeeper's skin, in the player skin layout with wide arms: weathered skin on the head
    and arms, dark hair over the top, back and sides of the head and two rows down the face, two dark eyes, the keeper's
    robe over the body and legs, and a sash across the waist. Every overlay layer stays clear."""
    robe, sash = GATE_KEEPER_ROBES[gate]

    def draw():
        c = Canvas(64, 64)
        # Head, right arm and left arm in skin; body, right leg and left leg in the robe.
        for u, v, w, h, d in ((0, 0, 8, 8, 8), (40, 16, 4, 12, 4), (32, 48, 4, 12, 4)):
            box(c, u, v, w, h, d, GATE_KEEPER_SKIN)
        for u, v, w, h, d in ((16, 16, 8, 12, 4), (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)):
            box(c, u, v, w, h, d, robe)
        c.rect(16, 28, 40, 30, sash)              # the sash, round the body's net at the waist
        c.rect(8, 0, 16, 8, GATE_KEEPER_HAIR)     # the top of the head
        c.rect(0, 8, 8, 16, GATE_KEEPER_HAIR)     # its right side
        c.rect(16, 8, 32, 16, GATE_KEEPER_HAIR)   # its left side and back
        c.rect(8, 8, 16, 10, GATE_KEEPER_HAIR)    # two rows down the face
        c.rect(9, 12, 11, 13, VOID)               # the eyes, two pixels wide, either side of the middle
        c.rect(13, 12, 15, 13, VOID)
        return c
    draw.__name__ = "gate_keeper_" + GATE_NAMES[gate]
    return draw


def inner_hollow():
    """A Visored's inner Hollow, its host's white double, in the player skin layout with wide arms: white skin, and
    white hair over the top, back and sides of the head and down the face to its third row, over two black eyes with
    a yellow iris each, on the side nearest the nose. A white kosode with a black collar V on the chest and a black
    sash at the waist, its sleeves ending above white hands, and a white hakama down to straw soles. Every overlay
    layer (hat, jacket, sleeves, trousers) stays clear. It wears no mask: in canon its inner Hollow meets its host
    unmasked."""
    c = Canvas(64, 64)
    box(c, 0, 0, 8, 8, 8, INNER_SKIN)       # the head
    c.rect(8, 0, 16, 8, WHITE)              # the top of the head
    c.rect(0, 8, 8, 16, WHITE)              # its right side
    c.rect(16, 8, 32, 16, WHITE)            # its left side and back
    c.rect(8, 8, 16, 11, WHITE)             # the hair down the face, to its third row
    c.rect(9, 12, 11, 13, VOID)             # the eyes, two pixels wide, either side of the middle
    c.rect(13, 12, 15, 13, VOID)
    c.put(10, 12, HOLLOW_IRIS)
    c.put(13, 12, HOLLOW_IRIS)
    # Body: the kosode, its collars meeting in a black V with bare skin between, and a black sash all round.
    box(c, 16, 16, 8, 12, 4, INNER_ROBE)
    for x, y in [(21, 20), (22, 21), (23, 22), (23, 23), (26, 20), (25, 21), (24, 22), (24, 23)]:
        c.put(x, y, BLACK)
    c.rect(22, 20, 26, 21, INNER_SKIN)
    c.rect(23, 21, 25, 22, INNER_SKIN)
    c.rect(16, 28, 40, 30, BLACK)
    # Arms: sleeves to just above the wrist, so the hands show, and the palms beneath.
    box(c, 40, 16, 4, 12, 4, INNER_ROBE)
    c.rect(40, 30, 56, 32, INNER_SKIN)
    c.rect(48, 16, 52, 20, INNER_SKIN)
    box(c, 32, 48, 4, 12, 4, INNER_ROBE)
    c.rect(32, 62, 48, 64, INNER_SKIN)
    c.rect(40, 48, 44, 52, INNER_SKIN)
    # Legs: the hakama down to straw soles, under the feet and round their last row.
    box(c, 0, 16, 4, 12, 4, INNER_ROBE)
    c.rect(0, 31, 16, 32, STRAW)
    c.rect(8, 16, 12, 20, STRAW)
    box(c, 16, 48, 4, 12, 4, INNER_ROBE)
    c.rect(16, 63, 32, 64, STRAW)
    c.rect(24, 48, 28, 52, STRAW)
    return c


def zangetsu_spirit():
    """Zangetsu drawn out, in the player skin layout with wide arms: a weathered face under long, dark, wavy hair that
    falls past the jaw at the sides and down the back of the neck, dark glasses across the eyes and grey stubble on the
    jaw; a dark shirt, hands bare, and black boots. His long ragged coat is drawn on the overlays in Tensa Zangetsu's
    colours: the jacket closed to the chest and open below, its back under the hair, sleeves to the wrist with torn
    cuffs, and its skirt on the trouser layers down past the knee, torn along the hem and open at the front."""
    c = Canvas(64, 64)
    box(c, 0, 0, 8, 8, 8, ZANGETSU_SKIN)    # the head
    c.rect(8, 0, 16, 8, BLACK)              # the top of the head
    c.rect(0, 8, 8, 16, BLACK)              # the right side, hair to the jaw
    c.rect(16, 8, 32, 16, BLACK)            # the left side and the back
    c.rect(8, 8, 16, 10, BLACK)             # hair over the brow
    for x in range(8, 16, 3):               # its waves along the brow
        c.put(x, 10, BLACK)
    c.rect(9, 11, 15, 13, VOID)             # the glasses, the bridge between them
    c.put(12, 11, ZANGETSU_SKIN)
    mottle(c, 9, 14, 15, 16, ZANGETSU_STUBBLE, 2)
    box(c, 16, 16, 8, 12, 4, ZANGETSU_SHIRT)
    box(c, 40, 16, 4, 12, 4, ZANGETSU_SHIRT)
    c.rect(40, 30, 56, 32, ZANGETSU_SKIN)   # the hands
    c.rect(48, 16, 52, 20, ZANGETSU_SKIN)
    box(c, 32, 48, 4, 12, 4, ZANGETSU_SHIRT)
    c.rect(32, 62, 48, 64, ZANGETSU_SKIN)
    c.rect(40, 48, 44, 52, ZANGETSU_SKIN)
    for u, v in ((0, 16), (16, 48)):        # the legs, dark, in black boots from the shin
        box(c, u, v, 4, 12, 4, ZANGETSU_SHIRT)
        c.rect(u, v + 10, u + 16, v + 16, BLACK)
        c.rect(u + 8, v, u + 12, v + 4, BLACK)
    # The coat: the jacket layer, closed at the chest and open from the waist, the hair down its back's top rows.
    box(c, 16, 32, 8, 12, 4, COAT)
    c.rect(23, 42, 25, 48, CLEAR)
    for x in (17, 34, 38):
        c.rect(x, 37, x + 1, 48, COAT_FOLD)
    c.rect(32, 36, 40, 39, BLACK)
    # The sleeves, torn at the cuff.
    for u, v in ((40, 32), (48, 48)):
        box(c, u, v, 4, 12, 4, COAT)
        c.rect(u, v + 4, u + 1, v + 14, COAT_FOLD)
        ragged(c, u, u + 16, v + 14, COAT)
        c.rect(u, v + 15, u + 16, v + 16, CLEAR)
    # The skirt on the trouser layers, past the knee, torn at the hem and open at the front of each leg.
    for u, v in ((0, 32), (0, 48)):
        box(c, u, v, 4, 12, 4, COAT)
        c.rect(u + 5, v + 4, u + 7, v + 16, CLEAR)
        ragged(c, u, u + 16, v + 12, COAT)
        c.rect(u, v + 13, u + 16, v + 16, CLEAR)
        c.rect(u + 4, v, u + 12, v + 4, CLEAR)
    return c


def zabimaru_spirit():
    """Zabimaru drawn out, on its model's 128x64 layout: red fur, in Hihiō Zabimaru's mane colour streaked with its gum,
    over the legs (a 5x12x5 box at 0, 0) and the long arms (5x25x5 at 0, 17), with dark palms and soles, the body
    (14x16x10 at 20, 0) and the shaggy mane over its shoulders (16x7x12 at 20, 26); the head (8x8x7 at 68, 0) with a
    bare face and the muzzle (5x4x4 at 98, 0); and the snake in Hihiō Zabimaru's segment, scaled down to each face of a
    tail segment (4x4x5 at 76, 15) and of its head (5x4x7 at 94, 15), with the segment's gum and teeth on its jaw (4x1x6
    at 98, 26)."""
    c = Canvas(128, 64)
    fur = ((0, 0, 5, 12, 5), (0, 17, 5, 25, 5), (20, 0, 14, 16, 10), (20, 26, 16, 7, 12), (68, 0, 8, 8, 7))
    for u, v, w, h, d in fur:
        box(c, u, v, w, h, d, HIHIO_MANE)
        mottle(c, u, v, u + 2 * (w + d), v + d + h, HIHIO_GUM, 5, u)
    c.rect(10, 0, 15, 5, ZABIMARU_PAW)      # a sole, and a palm
    c.rect(10, 17, 15, 22, ZABIMARU_PAW)
    c.rect(76, 8, 82, 13, ZABIMARU_FACE)    # the face under the brow, about the eyes
    c.rect(77, 9, 78, 10, VOID)
    c.rect(80, 9, 81, 10, VOID)
    box(c, 98, 0, 5, 4, 4, ZABIMARU_FACE)
    c.rect(102, 7, 107, 8, HIHIO_GUM)       # the mouth along the muzzle's foot
    segment = hihio_zabimaru_segment()
    for u, v, w, h, d in ((76, 15, 4, 4, 5), (94, 15, 5, 4, 7)):
        for x0, y0, x1, y1 in faces(u, v, w, h, d):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    c.put(x, y, segment.get((x - x0) * 16 // (x1 - x0), (y - y0) * 16 // (y1 - y0)))
    box(c, 98, 26, 4, 1, 6, HIHIO_GUM)
    for x in range(104, 108, 2):            # teeth along the jaw's top, the snake's mouth
        c.put(x, 26, TOOTH)
    return c


def hollow_player_mask():
    """A Hollow player's mask (HollowMaskModel): a bone plate with dark eye holes, a ridge down the brow and gaps
    between the teeth along its lower edge; a jaw with teeth along its top; fangs, horns and a beak for the
    other three shapes."""
    c = Canvas(64, 32)
    box(c, 0, 0, 9, 7, 1, BONE)          # plate: front x 1-9, y 1-7
    c.rect(11, 1, 20, 8, BONE_SHADE)     # the plate's back, against the face
    box(c, 0, 8, 7, 2, 1, BONE_SHADE)    # jaw: front x 1-7, y 9-10
    box(c, 20, 5, 1, 3, 1, TOOTH)        # a fang (both fangs share the net)
    box(c, 20, 0, 1, 4, 1, BONE_SHADE)   # a horn (both horns share the net)
    c.put(21, 0, BONE)                   # the horn's tip (its top face)
    box(c, 24, 0, 3, 2, 3, BONE)         # beak
    c.rect(27, 3, 30, 5, BONE_SHADE)     # the beak's front
    c.rect(2, 3, 4, 5, VOID)             # eye holes, two pixels square, either side of x 5
    c.rect(7, 3, 9, 5, VOID)
    c.put(5, 1, MASK_LINE)               # a ridge down the brow
    c.put(5, 2, MASK_LINE)
    for x in (2, 4, 6, 8):               # gaps between the upper teeth, on the plate's last row
        c.put(x, 7, VOID)
    for x in (2, 4, 6):                  # and between the lower ones, on the jaw's top row
        c.put(x, 9, VOID)
    return c


def hollow_player_mask_eyes():
    """The ravenous glow: the mask's eye holes in red, drawn emissive over the mask. The rest stays clear."""
    c = Canvas(64, 32)
    c.rect(2, 3, 4, 5, EYE_RED)
    c.rect(7, 3, 9, 5, EYE_RED)
    return c


def visored_mask():
    """A Visored's mask (HollowMaskModel's skull: the plate and the shut jaw): whiter bone than a Hollow's, with two
    red stripes down over the wearer's left eye, which is the texture's right (the plate's front is x 1-9, y 1-7;
    its eye holes are x 2-3 and x 7-8 at y 3-4; the jaw's front, x 1-7, sits under the plate's x 2-8)."""
    c = recoloured(hollow_player_mask, {BONE: VISORED_BONE, BONE_SHADE: VISORED_SHADE}, "visored_mask_base")()
    for y in (1, 2, 5, 6, 7):            # through the eye hole, above and below it
        c.put(7, y, MASK_RED)
    for y in range(1, 8):                # beside it, at the plate's edge
        c.put(9, y, MASK_RED)
    c.put(6, 10, MASK_RED)               # on, under the first stripe (the jaw's x 6, y 9 is a gap)
    return c


# The wearer's right half of the Visored mask's nets: the plate (texOffs 0, 0; 9 x 7 x 1) and the jaw (0, 8; 7 x 2 x 1).
# Top, bottom and front run from the wearer's right at the texture's left; the back runs the other way; the right-side
# face goes whole. The middle columns (the plate's x 5, the jaw's x 4, and their mirrors) stay.
HALF_MASK_CLEARED = [
    (1, 0, 5, 1), (10, 0, 14, 1), (0, 1, 1, 8), (1, 1, 5, 8), (16, 1, 20, 8),   # plate: top, bottom, side, front, back
    (1, 8, 4, 9), (8, 8, 11, 9), (0, 9, 1, 11), (1, 9, 4, 11), (13, 9, 16, 11),  # jaw: the same five
]


def visored_half_mask():
    """The half mask the Hollow forms when it seizes a soul before it is beaten (Ch. 165): the Visored's mask on the
    wearer's left half only, with the red stripes over the left eye. Every face of the plate and the jaw loses its
    wearer's-right half (see HALF_MASK_CLEARED), so the cut-out skull shows only the left."""
    c = visored_mask()
    for x0, y0, x1, y1 in HALF_MASK_CLEARED:
        c.rect(x0, y0, x1, y1, CLEAR)
    return c


def mask_edge_reach(x, y):
    """Where a pixel of the edge lies against the oval a Hollow sees through: above 1 is outside it."""
    return ((x + 0.5 - 32) / 29) ** 2 + ((y + 0.5 - 16) / 13) ** 2


def hollow_mask_edge():
    """The inside of a Hollow's mask, seen in first person over the whole screen: bone outside an oval, shaded
    where it meets it, a bridge between the eye holes at the top middle and the ridge of the nose at the bottom.
    The middle stays clear."""
    c = Canvas(64, 32)
    for y in range(32):
        for x in range(64):
            reach = mask_edge_reach(x, y)
            if reach > 1.15:
                c.put(x, y, BONE)
            elif reach > 1.0:
                c.put(x, y, BONE_SHADE)
    c.rect(30, 0, 34, 5, BONE_SHADE)     # the bridge between the eye holes
    c.rect(31, 0, 33, 4, BONE)
    c.rect(30, 27, 34, 32, BONE_SHADE)   # the ridge of the nose
    c.rect(31, 28, 33, 32, BONE)
    return c


def hollow_mask_edge_cracked():
    """The same edge, cracked across the top left and the bottom right."""
    c = hollow_mask_edge()
    crack(c, [(3, 1), (7, 3), (10, 3), (13, 2)])
    crack(c, [(60, 30), (56, 28), (53, 30)])
    return c


def sekkiseki():
    """Sekkiseki: pale grey cut stone in two courses, the lower one's joint offset half a block, faintly flecked."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, SEKKISEKI)
    mottle(c, 0, 0, 16, 16, SEKKISEKI_FLECK, 5, seed=3)
    c.rect(0, 7, 16, 8, SEKKISEKI_SEAM)    # the joint between the courses
    c.rect(0, 15, 16, 16, SEKKISEKI_SEAM)  # the bottom joint, which meets the next block's top course
    c.rect(7, 0, 8, 7, SEKKISEKI_SEAM)     # the upper course's upright joint
    c.rect(15, 8, 16, 15, SEKKISEKI_SEAM)  # the lower course's, half a block along
    return c


def koryu():
    """The Kōryū: a see-through violet current, three brighter streaks wavering up the block, flecked with light."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, KORYU)
    for x0 in (2, 7, 12):
        for y in range(16):
            c.put(x0 + (1 if (y // 3 + x0) % 2 else 0), y, KORYU_STREAK)
    mottle(c, 0, 0, 16, 16, KORYU_GLINT, 11, seed=5)
    return c


def white_sand():
    """White sand: bone-pale grains, flecked darker and lighter in a fixed scatter."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, WHITE_SAND)
    mottle(c, 0, 0, 16, 16, WHITE_SAND_GRAIN, 11, seed=1)
    mottle(c, 0, 0, 16, 16, WHITE_SAND_GLINT, 17, seed=4)
    return c


def pressed_white_sand():
    """White sand pressed level: the sandstone's ground, faintly flecked."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, WHITE_SANDSTONE)
    mottle(c, 0, 0, 16, 16, WHITE_SAND_GRAIN, 11, seed=2)
    return c


def white_sandstone():
    """White sandstone's side: pressed sand in three layers, a darker band under each, its top rim smooth."""
    c = pressed_white_sand()
    for y in (3, 9, 15):
        c.rect(0, y, 16, y + 1, WHITE_SANDSTONE_BAND)
    c.rect(0, 0, 16, 2, WHITE_SAND)
    return c


def white_sandstone_top():
    """White sandstone's ends, and smooth white sandstone all over: level pressed sand with a rim on two edges."""
    c = pressed_white_sand()
    c.rect(0, 0, 16, 1, WHITE_SANDSTONE_BAND)
    c.rect(0, 0, 1, 16, WHITE_SANDSTONE_BAND)
    return c


def hueco_quartz():
    """Hueco quartz's side: a white mineral grained lengthways like wood, darker at its two edges."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, HUECO_QUARTZ)
    for x in (3, 7, 12):
        for y in range(16):
            if (y + x) % 7 != 0:
                c.put(x + (1 if (y // 4 + x) % 3 == 0 else 0), y, HUECO_QUARTZ_GRAIN)
    c.rect(0, 0, 1, 16, HUECO_QUARTZ_SEAM)
    c.rect(15, 0, 16, 16, HUECO_QUARTZ_SEAM)
    return c


def hueco_quartz_top():
    """Hueco quartz's ends: rings of growth like a cut trunk's, white on white."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, HUECO_QUARTZ)
    for inset, colour in ((0, HUECO_QUARTZ_SEAM), (3, HUECO_QUARTZ_GRAIN), (6, HUECO_QUARTZ_GRAIN)):
        c.rect(inset, inset, 16 - inset, inset + 1, colour)
        c.rect(inset, 15 - inset, 16 - inset, 16 - inset, colour)
        c.rect(inset, inset, inset + 1, 16 - inset, colour)
        c.rect(15 - inset, inset, 16 - inset, 16 - inset, colour)
    return c


def hueco_quartz_bricks():
    """Hueco quartz bricks: four courses of white bricks, each course's joints half a brick from the last's."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, HUECO_QUARTZ)
    mottle(c, 0, 0, 16, 16, HUECO_QUARTZ_GRAIN, 11, seed=6)
    for y in (3, 7, 11, 15):
        c.rect(0, y, 16, y + 1, HUECO_QUARTZ_SEAM)
        x = 7 if y % 8 == 3 else 15
        c.rect(x, y - 3, x + 1, y, HUECO_QUARTZ_SEAM)
    return c


def las_noches_stone():
    """Las Noches stone: long white blocks in two courses, the lower one's joint a quarter along, with a faint grain."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, LAS_NOCHES)
    mottle(c, 0, 0, 16, 16, LAS_NOCHES_GRAIN, 9, seed=4)
    c.rect(0, 7, 16, 8, LAS_NOCHES_SEAM)    # the joint between the courses
    c.rect(0, 15, 16, 16, LAS_NOCHES_SEAM)  # the bottom joint, which meets the next block's top course
    c.rect(11, 0, 12, 7, LAS_NOCHES_SEAM)   # the upper course's upright joint
    c.rect(3, 8, 4, 15, LAS_NOCHES_SEAM)    # the lower course's, a quarter along
    return c


def las_noches_tiles():
    """Las Noches tiles: four by four square white tiles, each lit along its top edge, with pale joints between."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, LAS_NOCHES)
    for i in (0, 4, 8, 12):
        c.rect(0, i, 16, i + 1, LAS_NOCHES_LIT)
    for i in (3, 7, 11, 15):
        c.rect(0, i, 16, i + 1, LAS_NOCHES_SEAM)
        c.rect(i, 0, i + 1, 16, LAS_NOCHES_SEAM)
    return c


def las_noches_trim():
    """Las Noches trim: a near-black stone with a faint grain and no joint, so two courses read as one band."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, LAS_NOCHES_TRIM)
    mottle(c, 0, 0, 16, 16, LAS_NOCHES_TRIM_GRAIN, 7, seed=2)
    return c


def las_noches_sky():
    """Las Noches sky: a clear day blue crossed by fine diagonal stripes of haze, every fourth fleck a pale wisp."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, LAS_NOCHES_SKY)
    mottle(c, 0, 0, 16, 16, LAS_NOCHES_SKY_HAZE, 4, seed=1)
    mottle(c, 0, 0, 16, 16, LAS_NOCHES_SKY_WISP, 16, seed=5)
    return c


def white_plaster():
    """White plaster: one flat white with a faint grain in a fixed scatter."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, PLASTER)
    mottle(c, 0, 0, 16, 16, PLASTER_GRAIN, 11, seed=3)
    return c


def roof_tiles():
    """Roof tiles: rounded tiles in columns, each with a lit ridge and a dark channel, laid in four courses, each
    course's lower edge throwing a shadow on the next."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, ROOF_TILE)
    for x in (0, 4, 8, 12):
        c.rect(x + 1, 0, x + 2, 16, ROOF_TILE_RIDGE)
        c.rect(x + 3, 0, x + 4, 16, ROOF_TILE_SHADOW)
    for y in (3, 7, 11, 15):
        c.rect(0, y, 16, y + 1, ROOF_TILE_SHADOW)
    return c


def shoji():
    """Shōji: see-through paper on a dark wooden lattice of three by three panes, its frame on every edge, so shōji in a
    row read as one screen through the pane's cut-out."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, SHOJI_PAPER)
    for i in (0, 5, 10, 15):
        c.rect(i, 0, i + 1, 16, SHOJI_FRAME)
        c.rect(0, i, 16, i + 1, SHOJI_FRAME)
    return c


def shoji_top():
    """The top and bottom edge of a shōji pane, and its post: the frame's bare wood."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, SHOJI_FRAME)
    return c


def shoji_door(lower):
    """A half of the shōji door: a shōji panel in a frame two pixels wide, its lattice running on from half to half;
    the lower half ends in a wooden kick panel, with the pull above it."""
    def draw():
        c = shoji()
        c.rect(0, 0, 2, 16, SHOJI_FRAME)
        c.rect(14, 0, 16, 16, SHOJI_FRAME)
        if lower:
            c.rect(0, 10, 16, 16, SHOJI_PANEL)
            c.rect(0, 15, 16, 16, SHOJI_FRAME)
            c.rect(12, 6, 13, 8, SHOJI_FRAME)
        else:
            c.rect(0, 0, 16, 2, SHOJI_FRAME)
        return c
    draw.__name__ = "shoji_door_" + ("bottom" if lower else "top")
    return draw


def shoji_door_icon():
    """The shōji door's item: a narrow shōji panel with its kick panel."""
    c = Canvas(16, 16)
    c.rect(3, 0, 13, 16, SHOJI_FRAME)
    c.rect(4, 1, 12, 11, SHOJI_PAPER)
    for x in (6, 9):
        c.rect(x, 1, x + 1, 11, SHOJI_FRAME)
    for y in (4, 7):
        c.rect(4, y, 12, y + 1, SHOJI_FRAME)
    c.rect(4, 12, 12, 15, SHOJI_PANEL)
    return c


def great_gate():
    """A great gate's face: pale Sekkiseki slabs, a joint down the middle, crossed by an iron band with two studs, so a
    gate ten blocks across reads as boards banded at every block."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, SEKKISEKI)
    mottle(c, 0, 0, 16, 16, SEKKISEKI_FLECK, 7, seed=2)
    c.rect(7, 0, 8, 16, SEKKISEKI_SEAM)
    c.rect(0, 6, 16, 9, GATE_IRON)
    for x in (3, 11):
        c.put(x, 7, GATE_STUD)
    return c


def wicket(lower):
    """A half of the wicket: dark oak boards with joints between them and a darker frame at the sides, an iron strap
    across each half; the lower one has a ring to pull it by."""
    def draw():
        c = Canvas(16, 16)
        c.rect(0, 0, 16, 16, WICKET_WOOD)
        for x in (0, 5, 10, 15):
            c.rect(x, 0, x + 1, 16, WICKET_JOINT)
        if lower:
            c.rect(0, 11, 16, 13, GATE_IRON)
            c.rect(0, 15, 16, 16, WICKET_JOINT)
            c.rect(11, 3, 13, 5, GATE_STUD)
        else:
            c.rect(0, 3, 16, 5, GATE_IRON)
            c.rect(0, 0, 16, 1, WICKET_JOINT)
        return c
    draw.__name__ = "wicket_" + ("bottom" if lower else "top")
    return draw


def wicket_icon():
    """The wicket's item: a narrow door of dark boards with two iron straps and the ring."""
    c = Canvas(16, 16)
    c.rect(4, 0, 12, 16, WICKET_WOOD)
    c.rect(7, 0, 8, 16, WICKET_JOINT)
    for y in (3, 12):
        c.rect(4, y, 12, y + 1, GATE_IRON)
    c.put(10, 8, GATE_STUD)
    return c


def tatami():
    """Tatami's face: straw woven in rows across the mat, with a dark cloth edge down its two long sides."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, TATAMI)
    for y in range(0, 16, 2):
        c.rect(2, y, 14, y + 1, TATAMI_WEAVE)
    c.rect(0, 0, 2, 16, TATAMI_EDGE)
    c.rect(14, 0, 16, 16, TATAMI_EDGE)
    return c


def tatami_side():
    """Tatami's sides: the pressed straw of its core, in layers."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, TATAMI_WEAVE)
    for y in (1, 5, 9, 13):
        c.rect(0, y, 16, y + 2, TATAMI)
    return c


# Each division's glyph, in the divisions' order, as straight strokes (x0, y0, x1, y1), both ends included, in the
# 9 by 12 box inside the banner's cloth: the numerals 一 to 十; 十一 to 十三 written 十 over the units, as a hanging
# banner reads top to bottom; 鬼 for the Kidō Corps, 隠 for the Onmitsukidō and 〇 for Zero.
DIVISION_GLYPHS = {
    "first": [(0, 5, 8, 5)],  # 一
    "second": [(1, 3, 7, 3), (0, 8, 8, 8)],  # 二
    "third": [(1, 2, 7, 2), (2, 5, 6, 5), (0, 8, 8, 8)],  # 三
    "fourth": [(0, 2, 8, 2), (0, 2, 0, 9), (8, 2, 8, 9), (0, 9, 8, 9),
               (3, 2, 3, 5), (3, 5, 1, 7), (5, 2, 5, 7), (5, 7, 7, 7)],  # 四
    "fifth": [(1, 2, 7, 2), (3, 2, 3, 9), (3, 5, 7, 5), (7, 5, 7, 9), (0, 9, 8, 9)],  # 五
    "sixth": [(4, 1, 5, 2), (0, 4, 8, 4), (3, 6, 0, 9), (5, 6, 8, 9)],  # 六
    "seventh": [(0, 4, 8, 4), (3, 1, 3, 9), (4, 10, 7, 10), (8, 10, 8, 9)],  # 七
    "eighth": [(3, 1, 3, 4), (3, 4, 0, 9), (5, 1, 6, 4), (6, 4, 8, 9)],  # 八
    "ninth": [(3, 1, 3, 4), (3, 4, 0, 10), (0, 3, 5, 3), (5, 3, 5, 9), (6, 10, 7, 10), (8, 10, 8, 8)],  # 九
    "tenth": [(4, 1, 4, 9), (0, 4, 8, 4)],  # 十
    "eleventh": [(4, 0, 4, 4), (0, 2, 8, 2), (0, 9, 8, 9)],  # 十一
    "twelfth": [(4, 0, 4, 4), (0, 2, 8, 2), (1, 7, 7, 7), (0, 11, 8, 11)],  # 十二
    "thirteenth": [(4, 0, 4, 4), (0, 2, 8, 2), (1, 7, 7, 7), (2, 9, 6, 9), (0, 11, 8, 11)],  # 十三
    "kido_corps": [(4, 0, 3, 1), (0, 2, 6, 2), (0, 4, 6, 4), (0, 6, 6, 6), (0, 2, 0, 6), (3, 2, 3, 6), (6, 2, 6, 6),
                   (2, 7, 0, 11), (4, 7, 4, 10), (5, 11, 8, 11), (7, 7, 6, 8), (8, 8, 8, 8), (6, 9, 8, 9)],  # 鬼
    "onmitsukido": [(0, 0, 0, 11), (0, 0, 2, 0), (2, 1, 1, 2), (2, 3, 2, 4), (1, 5, 1, 5),
                    (5, 0, 8, 0), (4, 1, 4, 1), (6, 1, 6, 1), (8, 1, 8, 1),
                    (4, 3, 8, 3), (8, 3, 8, 7), (5, 5, 8, 5), (4, 7, 8, 7),
                    (6, 9, 6, 9), (8, 9, 8, 10), (4, 10, 5, 10), (5, 11, 7, 11)],  # 隠
    "zero": [(2, 1, 6, 1), (6, 1, 8, 3), (8, 3, 8, 7), (8, 7, 6, 9),
             (6, 9, 2, 9), (2, 9, 0, 7), (0, 7, 0, 3), (0, 3, 2, 1)],  # 〇
}


def division_banner(name):
    """A division's banner, for its block and its item: white cloth eleven pixels wide on a dark rod a pixel longer
    each side, the division's glyph in black inside a pixel's margin of cloth, and clear around them so the wall
    behind shows."""
    def draw():
        c = Canvas(16, 16)
        c.rect(1, 0, 14, 2, BANNER_ROD)
        c.rect(2, 2, 13, 16, BANNER_CLOTH)
        for x0, y0, x1, y1 in DIVISION_GLYPHS[name]:
            line(c, 3 + x0, 3 + y0, 3 + x1, 3 + y1, BANNER_INK)
        return c
    draw.__name__ = "division_banner_" + name
    return draw


TEXTURES = {
    "item/asauchi.png": asauchi,
    "item/zangetsu.png": zangetsu,
    "item/zabimaru.png": zabimaru,
    "item/zabimaru_shikai.png": zabimaru_shikai,
    "item/tensa_zangetsu.png": tensa_zangetsu,
    "item/hihio_zabimaru.png": hihio_zabimaru,
    "item/shihakusho_kosode.png": kosode_icon,
    "item/shihakusho_hakama.png": hakama_icon,
    "item/waraji.png": waraji_icon,
    "models/armor/shihakusho_layer_1.png": armor_layer_1,
    "models/armor/shihakusho_layer_2.png": armor_layer_2,
    "item/academy_kosode.png": academy(kosode_icon, ACADEMY_BLUE, ACADEMY_BLUE_FOLD, "academy_kosode_icon"),
    "item/academy_kosode_red.png": academy(kosode_icon, ACADEMY_RED, ACADEMY_RED_FOLD, "academy_kosode_red_icon"),
    "item/academy_hakama.png": academy(hakama_icon, ACADEMY_BLUE, ACADEMY_BLUE_FOLD, "academy_hakama_icon"),
    "item/academy_hakama_red.png": academy(hakama_icon, ACADEMY_RED, ACADEMY_RED_FOLD, "academy_hakama_red_icon"),
    "models/armor/academy_uniform_layer_1.png": academy(armor_layer_1, ACADEMY_BLUE, ACADEMY_BLUE_FOLD, "academy_layer_1"),
    "models/armor/academy_uniform_layer_2.png": academy(armor_layer_2, ACADEMY_BLUE, ACADEMY_BLUE_FOLD, "academy_layer_2"),
    "models/armor/academy_uniform_red_layer_1.png": academy(armor_layer_1, ACADEMY_RED, ACADEMY_RED_FOLD,
                                                            "academy_red_layer_1"),
    "models/armor/academy_uniform_red_layer_2.png": academy(armor_layer_2, ACADEMY_RED, ACADEMY_RED_FOLD,
                                                            "academy_red_layer_2"),
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
    "entity/hollow/gillian.png": gillian,
    "entity/hollow/gillian_mask.png": gillian_mask,
    "entity/hollow/gillian_mask_cracked.png": cracked(gillian_mask, [(77, 8), (78, 11), (80, 13), (79, 15)]),
    "entity/hollow/adjuchas.png": adjuchas,
    "entity/hollow/adjuchas_mask.png": adjuchas_mask,
    "entity/hollow/adjuchas_mask_cracked.png": cracked(adjuchas_mask, [(63, 6), (64, 9), (66, 10), (65, 12)]),
    "entity/hollow/shrieker.png": shrieker,
    "entity/hollow/shrieker_mask.png": shrieker_mask,
    "entity/hollow/shrieker_mask_cracked.png": cracked(shrieker_mask, [(57, 7), (58, 10), (57, 13), (59, 15)]),
    "entity/hell_gate.png": hell_gate,
    "entity/hollow/shrieker_leech.png": shrieker_leech,
    "entity/hollow/grand_fisher.png": grand_fisher,
    "entity/hollow/grand_fisher_mask.png": grand_fisher_mask,
    "entity/hollow/grand_fisher_mask_cracked.png": cracked(grand_fisher_mask, [(66, 9), (67, 12), (65, 15), (67, 17)]),
    "entity/hollow/vasto_lorde.png": vasto_lorde,
    "entity/hollow/vasto_lorde_mask.png": vasto_lorde_mask,
    "entity/hollow/vasto_lorde_mask_cracked.png": cracked(vasto_lorde_mask, [(10, 9), (11, 11), (10, 13), (12, 15)]),
    "entity/hollow/runuganga.png": runuganga,
    "entity/hollow/runuganga_mask.png": runuganga_mask,
    "entity/hollow/runuganga_mask_cracked.png": cracked(runuganga_mask, [(62, 36), (61, 38), (59, 41), (60, 44)]),
    "entity/grand_fisher_lure.png": grand_fisher_lure,
    "entity/grand_fisher_stalk.png": grand_fisher_stalk,
    "entity/garganta.png": garganta,
    "entity/getsuga_tensho.png": getsuga_tensho,
    "entity/zabimaru_segment.png": zabimaru_segment,
    "entity/hihio_zabimaru_segment.png": hihio_zabimaru_segment,
    "item/denreishinki.png": denreishinki,
    "item/hell_butterfly.png": hell_butterfly,
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
    "item/arrancar_jacket.png": arrancar_jacket_icon,
    "item/arrancar_hakama.png": arrancar_hakama_icon,
    "item/arrancar_zori.png": arrancar_zori_icon,
    "models/armor/arrancar_uniform_layer_1.png": arrancar_uniform_layer_1,
    "models/armor/arrancar_uniform_layer_2.png": arrancar_uniform_layer_2,
    "item/caja_negacion.png": caja_negacion_icon,
    "item/mask_fragment.png": mask_fragment_icon,
    "item/hollow_bait.png": hollow_bait_icon,
    "item/tenshintai.png": tenshintai_icon,
    "entity/heilig_pfeil.png": heilig_pfeil,
    "entity/blut_vene.png": blut_vene,
    "entity/tensa_zangetsu_coat.png": tensa_zangetsu_coat,
    "entity/hihio_zabimaru_pelt.png": hihio_zabimaru_pelt,
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
    "entity/hollow_player.png": hollow_player,
    "entity/hollow_vasto_lorde.png": hollow_vasto_lorde,
    "entity/arrancar_hole.png": arrancar_hole,
    "entity/arrancar_digits.png": arrancar_digits,
    "entity/arrancar.png": arrancar_npc,
    "entity/duty_shinigami.png": duty_shinigami,
    "entity/kisuke_urahara.png": kisuke_urahara,
    "entity/training_dummy.png": training_dummy,
    "entity/tenshintai.png": tenshintai,
    **{"entity/gate_keeper/%s.png" % name: gate_keeper(gate) for gate, name in enumerate(GATE_NAMES)},
    "entity/inner_hollow.png": inner_hollow,
    "entity/zangetsu_spirit.png": zangetsu_spirit,
    "entity/zabimaru_spirit.png": zabimaru_spirit,
    "entity/hollow_player_mask.png": hollow_player_mask,
    "entity/hollow_player_mask_cracked.png": cracked(hollow_player_mask, [(8, 1), (6, 2), (6, 5), (5, 6)]),
    "entity/hollow_player_mask_eyes.png": hollow_player_mask_eyes,
    "entity/visored_mask.png": visored_mask,
    "entity/visored_half_mask.png": visored_half_mask,
    "misc/hollow_mask_edge.png": hollow_mask_edge,
    "misc/hollow_mask_edge_cracked.png": hollow_mask_edge_cracked,
    "block/sekkiseki.png": sekkiseki,
    "block/koryu.png": koryu,
    "block/white_sand.png": white_sand,
    "block/white_sandstone.png": white_sandstone,
    "block/white_sandstone_top.png": white_sandstone_top,
    "block/hueco_quartz.png": hueco_quartz,
    "block/hueco_quartz_top.png": hueco_quartz_top,
    "block/hueco_quartz_bricks.png": hueco_quartz_bricks,
    "block/las_noches_stone.png": las_noches_stone,
    "block/las_noches_tiles.png": las_noches_tiles,
    "block/las_noches_trim.png": las_noches_trim,
    "block/las_noches_sky.png": las_noches_sky,
    "block/white_plaster.png": white_plaster,
    "block/black_plaster.png": recoloured(white_plaster, {PLASTER: BLACK_PLASTER, PLASTER_GRAIN: BLACK_PLASTER_GRAIN},
                                          "black_plaster"),
    "block/roof_tiles.png": roof_tiles,
    "block/shoji.png": shoji,
    "block/shoji_top.png": shoji_top,
    "block/shoji_door_top.png": shoji_door(False),
    "block/shoji_door_bottom.png": shoji_door(True),
    "item/shoji_door.png": shoji_door_icon,
    "block/tatami.png": tatami,
    "block/tatami_side.png": tatami_side,
    **{"block/division_banner_%s.png" % name: division_banner(name) for name in DIVISION_GLYPHS},
    "block/great_gate.png": great_gate,
    "block/wicket_top.png": wicket(False),
    "block/wicket_bottom.png": wicket(True),
    "item/wicket.png": wicket_icon,
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
