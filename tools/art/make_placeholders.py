"""Draws the mod's placeholder textures, so they can be rebuilt and reviewed as code.

Run from the repository root:

    python3 tools/art/make_placeholders.py          # write every texture
    python3 tools/art/make_placeholders.py --check  # fail if a committed texture differs

The check compares decoded pixels, not bytes, so a different zlib build does not trip it. When a
texture gets real art, drop it from TEXTURES below so the check stops covering it.

Uses only the standard library.
"""
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


TEXTURES = {
    "item/asauchi.png": asauchi,
    "item/shihakusho_kosode.png": kosode_icon,
    "item/shihakusho_hakama.png": hakama_icon,
    "item/waraji.png": waraji_icon,
    "models/armor/shihakusho_layer_1.png": armor_layer_1,
    "models/armor/shihakusho_layer_2.png": armor_layer_2,
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
