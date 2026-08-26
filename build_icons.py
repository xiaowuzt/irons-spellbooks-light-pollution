"""Generates the six missing spell icons in the style of the existing ones:
transparent background, saturated neon strokes with a bright inner highlight, an
outer glow, and small rhombic accents. Drawn at 4x and downsampled for smooth edges.
"""
import math
from PIL import Image, ImageDraw, ImageFilter

S = 1024
OUT = 256
DEST = ("src/main/resources/assets/irons_spellbooks_light_pollution"
        "/textures/gui/spell_icons/")

WHITE = (245, 250, 255)
CYAN = (140, 240, 255)
BLUE = (60, 150, 255)
VIOLET = (110, 60, 220)
INDIGO = (58, 28, 148)
RED = (230, 40, 60)
GOLD = (255, 205, 90)
ORANGE = (250, 140, 40)
TEAL = (60, 220, 170)
MAGENTA = (215, 45, 130)


def layer():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def glow(src, radius, gain):
    """A blurred copy of src, used underneath it so strokes bloom."""
    g = src.filter(ImageFilter.GaussianBlur(radius))
    alpha = g.getchannel("A").point(lambda v: min(255, int(v * gain)))
    g.putalpha(alpha)
    return g


def finish(name, base, glows=()):
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for g in glows:
        canvas = Image.alpha_composite(canvas, g)
    canvas = Image.alpha_composite(canvas, base)
    canvas.resize((OUT, OUT), Image.LANCZOS).save(DEST + name + ".png")
    print("wrote", name)


def rhombus(draw, cx, cy, w, h, fill, angle=0.0):
    pts = [(0, -h), (w, 0), (0, h), (-w, 0)]
    cos, sin = math.cos(angle), math.sin(angle)
    draw.polygon([(cx + x * cos - y * sin, cy + x * sin + y * cos)
                  for x, y in pts], fill=fill)


def taper(draw, points, widths, fill):
    """A tapered stroke: a run of circles is enough at this resolution."""
    for i in range(len(points) - 1):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        w0, w1 = widths[i], widths[i + 1]
        steps = max(2, int(math.hypot(x1 - x0, y1 - y0) / 4))
        for s in range(steps + 1):
            t = s / steps
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            r = (w0 + (w1 - w0) * t) * 0.5
            draw.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def ramp(stops, t):
    """Colour at position t along a list of (position, rgb) stops."""
    if t <= stops[0][0]:
        return stops[0][1]
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        if t <= p1:
            k = (t - p0) / (p1 - p0)
            return tuple(int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3))
    return stops[-1][1]


def taper_ramp(draw, points, widths, stops, alpha=255):
    """A tapered stroke whose colour travels along its length."""
    total = len(points) - 1
    for i in range(total):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        w0, w1 = widths[i], widths[i + 1]
        steps = max(2, int(math.hypot(x1 - x0, y1 - y0) / 4))
        for s in range(steps + 1):
            t = s / steps
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            r = (w0 + (w1 - w0) * t) * 0.5
            colour = ramp(stops, (i + t) / total)
            draw.ellipse([x - r, y - r, x + r, y + r], fill=colour + (alpha,))


def spark(draw, x, y, size, colour):
    """A four-pointed star."""
    k = size * 0.20
    draw.polygon([(x, y - size), (x + k, y - k), (x + size, y), (x + k, y + k),
                  (x, y + size), (x - k, y + k), (x - size, y), (x - k, y - k)],
                 fill=colour)


def sky_collapse():
    """A crack torn across the sky, light pouring out, slabs tumbling loose."""
    base = layer()
    d = ImageDraw.Draw(base)
    mid = 400
    xs = [60, 200, 340, 512, 680, 820, 964]
    jog = [0, -26, 18, -40, 22, -18, 0]
    swell = [8, 40, 66, 92, 62, 36, 8]
    top = [(x, mid + j - s) for x, j, s in zip(xs, jog, swell)]
    bottom = [(x, mid + j + s) for x, j, s in zip(xs, jog, swell)]
    # Rim first, then the hot interior over it.
    d.polygon([(x, y - 16) for x, y in top] + [(x, y + 16) for x, y in reversed(bottom)],
              fill=VIOLET + (255,))
    d.polygon(top + list(reversed(bottom)), fill=GOLD + (255,))
    d.polygon([(x, y * 0.55 + mid * 0.45) for x, y in top]
              + [(x, y * 0.55 + mid * 0.45) for x, y in reversed(bottom)],
              fill=WHITE + (255,))

    # Light spilling downward out of the split.
    for i, x in enumerate(range(140, 900, 92)):
        w = 26 - abs(i - 4) * 3
        d.polygon([(x - w, mid + 40), (x + w, mid + 40),
                   (x + w * 2.4, 980), (x - w * 2.4, 980)],
                  fill=GOLD + (96,))

    # Slabs of sky shearing off, each a different shape.
    slabs = [(210, 690, 120, 62, 0.38), (470, 800, 92, 48, -0.22),
             (700, 660, 108, 54, 0.55), (830, 840, 70, 38, -0.5)]
    for cx, cy, w, h, a in slabs:
        cos, sin = math.cos(a), math.sin(a)
        quad = [(-w, -h), (w * 0.8, -h * 0.7), (w, h), (-w * 0.7, h * 0.85)]
        d.polygon([(cx + x * cos - y * sin, cy + x * sin + y * cos)
                   for x, y in quad], fill=INDIGO + (255,))
        inner = [(x * 0.62, y * 0.62) for x, y in quad]
        d.polygon([(cx + x * cos - y * sin, cy + x * sin + y * cos)
                   for x, y in inner], fill=BLUE + (255,))
    rhombus(d, 120, 900, 20, 42, RED + (255,), 0.4)
    rhombus(d, 940, 560, 16, 34, CYAN + (255,), -0.3)
    finish("sky_collapse", base, [glow(base, 34, 1.5), glow(base, 12, 1.3)])


def bolt(draw, x0, y0, x1, y1, width, fill, jag=26, seed=0):
    """A branchless lightning polyline between two points."""
    span = math.hypot(x1 - x0, y1 - y0)
    nx, ny = -(y1 - y0) / span, (x1 - x0) / span
    steps = 6
    pts = []
    for i in range(steps + 1):
        t = i / steps
        off = 0.0 if i in (0, steps) else math.sin((i + seed) * 2.399) * jag
        pts.append((x0 + (x1 - x0) * t + nx * off, y0 + (y1 - y0) * t + ny * off))
    draw.line(pts, fill=fill, width=width, joint="curve")


def stellar_convergence():
    """Nine stars of different colours pouring into one beam."""
    base = layer()
    d = ImageDraw.Draw(base)
    hues = [GOLD, ORANGE, MAGENTA, VIOLET, BLUE, CYAN, TEAL, GOLD, BLUE]
    stars = []
    for i in range(9):
        a = math.pi * (0.06 + 0.88 * i / 8)
        r = 340 if i % 2 == 0 else 262
        stars.append((512 - math.cos(a) * r, 402 - math.sin(a) * r * 0.80))

    throat = (512, 452)
    # Each star feeding the throat. This is what makes it a convergence rather
    # than a row of lamps above a column.
    for (x, y), hue in zip(stars, hues):
        d.line([(x, y), throat], fill=hue + (120,), width=22)
        d.line([(x, y), throat], fill=WHITE + (70,), width=8)
    for i in range(8):
        bolt(d, *stars[i], *stars[i + 1], 15, CYAN + (255,), 34, i)

    # The beam: narrow at the throat, flaring down, soft edges from nesting.
    for half_top, half_bottom, colour, alpha in (
            (86, 168, VIOLET, 150), (60, 122, BLUE, 190),
            (36, 76, CYAN, 225), (15, 34, WHITE, 255)):
        d.polygon([(512 - half_top, 452), (512 + half_top, 452),
                   (512 + half_bottom, 1010), (512 - half_bottom, 1010)],
                  fill=colour + (alpha,))

    for (x, y), hue in zip(stars, hues):
        d.ellipse([x - 58, y - 58, x + 58, y + 58], fill=hue + (110,))
        d.ellipse([x - 38, y - 38, x + 38, y + 38], fill=hue + (255,))
        d.ellipse([x - 16, y - 16, x + 16, y + 16], fill=WHITE + (255,))
    finish("stellar_convergence", base, [glow(base, 40, 1.6), glow(base, 14, 1.25)])


def second_sun():
    """A vast disc clearing the horizon, white at the core and red at the limb."""
    base = layer()
    d = ImageDraw.Draw(base)
    cx, cy, r = 512, 620, 380

    # Corona, before the disc so the rays sit behind it.
    for i in range(28):
        a = math.tau * i / 28
        reach = r * (1.42 + 0.3 * math.sin(i * 2.4))
        w = 20 if i % 2 == 0 else 11
        d.line([(cx + math.cos(a) * r * 0.92, cy + math.sin(a) * r * 0.92),
                (cx + math.cos(a) * reach, cy + math.sin(a) * reach)],
               fill=ORANGE + (150,), width=w)

    for radius, colour in ((r, RED), (r * 0.86, ORANGE), (r * 0.66, GOLD),
                           (r * 0.4, WHITE)):
        d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                  fill=colour + (255,))

    # The horizon it is climbing out of, which is what makes it read as a sun
    # rising rather than as a ball floating.
    d.rectangle([0, 838, S, S], fill=(0, 0, 0, 0))
    d.polygon([(0, 838), (S, 838), (S, S), (0, S)], fill=INDIGO + (255,))
    d.line([(0, 838), (S, 838)], fill=CYAN + (255,), width=16)
    # A second shadow being cast, the spell's whole point.
    d.polygon([(430, 852), (600, 852), (760, 1010), (250, 1010)],
              fill=(8, 6, 26, 210))
    rhombus(d, 140, 250, 18, 40, CYAN + (255,), 0.3)
    rhombus(d, 880, 190, 14, 32, GOLD + (255,), -0.4)
    finish("second_sun", base, [glow(base, 44, 1.7), glow(base, 16, 1.3)])


def singularity():
    """A dark core, refraction rings around it, lightning whipping outward."""
    base = layer()
    d = ImageDraw.Draw(base)
    cx, cy = 512, 500

    for i in range(7):
        a = math.tau * i / 7 + 0.3
        bolt(d, cx + math.cos(a) * 210, cy + math.sin(a) * 210,
             cx + math.cos(a) * 470, cy + math.sin(a) * 470,
             15, CYAN + (230,), 34, i)

    # Rings: bright, thin, and stacked, the way a lens shows up.
    for radius, width, colour, alpha in ((330, 12, VIOLET, 200), (270, 16, BLUE, 230),
                                         (218, 20, CYAN, 255)):
        d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                  outline=colour + (alpha,), width=width)
    # The core is genuinely black, not dark blue: that contrast is the effect.
    d.ellipse([cx - 190, cy - 190, cx + 190, cy + 190], fill=WHITE + (255,))
    d.ellipse([cx - 172, cy - 172, cx + 172, cy + 172], fill=(4, 2, 14, 255))
    rhombus(d, 210, 830, 20, 44, RED + (255,), 0.5)
    rhombus(d, 830, 810, 16, 36, CYAN + (255,), -0.35)
    finish("singularity", base, [glow(base, 40, 1.7), glow(base, 14, 1.3)])


def leviathan():
    """A serpent coiling across the frame, jaws open at the head.

    The first attempt was far too thick for its length and read as a tadpole. A
    snake reads as a snake because it is long relative to its girth and because the
    body crosses itself; both of those need the body slim.
    """
    base = layer()
    d = ImageDraw.Draw(base)

    spine, widths = [], []
    for i in range(61):
        t = i / 60
        x = 205 + t * 700
        y = 400 + math.sin(t * math.pi * 2.7 + 0.35) * 240 * (0.22 + 0.78 * t)
        spine.append((x, y))
        if t < 0.10:
            w = 40 + t / 0.10 * 12
        elif t < 0.55:
            w = 52
        elif t < 0.85:
            w = 52 - (t - 0.55) / 0.30 * 28
        else:
            w = 24 * (1 - ((t - 0.85) / 0.15) ** 1.8) + 3
        widths.append(w)

    taper(d, spine, [w + 30 for w in widths], (20, 8, 60) + (255,))
    # The body's colour travels along it: hot white at the skull cooling through
    # blue to violet and magenta at the tail. Flat blue was legible but inert, and
    # an astral serpent should look like it is burning along its own length.
    taper_ramp(d, spine, [w + 12 for w in widths],
               [(0.0, VIOLET), (0.35, (70, 40, 200)), (0.7, (150, 40, 190)),
                (1.0, MAGENTA)])
    taper_ramp(d, spine, widths,
               [(0.0, CYAN), (0.3, BLUE), (0.62, VIOLET), (1.0, MAGENTA)])
    taper_ramp(d, spine, [w * 0.46 for w in widths],
               [(0.0, WHITE), (0.4, CYAN), (0.75, (190, 150, 255)), (1.0, (255, 170, 220))])
    taper(d, spine, [w * 0.16 for w in widths], WHITE + (250,))

    # Sparks shed along the back, brightest toward the head.
    for i in range(4, 58, 5):
        x, y = spine[i]
        t = i / 60
        size = 30 - t * 16
        spark(d, x, y - widths[i] * 0.62, size,
              ramp([(0.0, WHITE), (0.5, CYAN), (1.0, (255, 180, 230))], t) + (255,))
    # Motion streaks trailing off the tail.
    tx, ty = spine[-1]
    for k, (dx, dy) in enumerate(((70, 34), (86, -18), (54, 74))):
        d.line([(tx, ty), (tx + dx, ty + dy)], fill=MAGENTA + (170 - k * 40,),
               width=12 - k * 3)

    # Head, aligned with the body's own heading so it does not look stuck on.
    hx, hy = spine[0]
    ax, ay = spine[3]
    ang = math.atan2(hy - ay, hx - ax)
    cos, sin = math.cos(ang), math.sin(ang)

    def place(px, py):
        return hx + px * cos - py * sin, hy + px * sin + py * cos

    # Broad flat spade, wider than the neck behind it.
    d.polygon([place(-44, -52), place(44, -60), place(124, -32), place(140, 0),
               place(124, 32), place(44, 60), place(-44, 52)],
              fill=BLUE + (255,))
    d.polygon([place(-22, -30), place(66, -32), place(114, 0), place(66, 32),
               place(-22, 30)], fill=CYAN + (255,))
    # Jaws, wide open, with the throat showing between them. Kept deliberately
    # simple: at 256 pixels a full row of teeth turns into stripes.
    d.polygon([place(104, 0), place(300, -126), place(304, 108)],
              fill=(70, 10, 46, 255))
    d.polygon([place(112, -20), place(300, -132), place(290, -76), place(140, -8)],
              fill=VIOLET + (255,))
    d.polygon([place(112, 20), place(306, 116), place(296, 62), place(140, 8)],
              fill=VIOLET + (255,))
    # Two fangs, one per jaw, curving back toward the throat.
    d.polygon([place(214, -96), place(246, -110), place(200, -40)],
              fill=WHITE + (255,))
    d.polygon([place(216, 84), place(248, 98), place(202, 34)],
              fill=WHITE + (255,))
    # Eye, high and forward under the brow.
    ex, ey = place(56, -32)
    d.ellipse([ex - 30, ey - 30, ex + 30, ey + 30], fill=WHITE + (255,))
    d.ellipse([ex - 18, ey - 18, ex + 18, ey + 18], fill=RED + (255,))
    rhombus(d, 132, 838, 18, 40, RED + (255,), 0.45)
    rhombus(d, 906, 862, 15, 34, CYAN + (255,), -0.4)
    spark(d, 168, 720, 26, GOLD + (255,))
    spark(d, 858, 236, 22, CYAN + (255,))
    finish("leviathan", base, [glow(base, 46, 1.8), glow(base, 20, 1.5),
                               glow(base, 8, 1.3)])


LEAF_HUES = [GOLD, TEAL, MAGENTA, BLUE]
GOLDEN = 2.39996323


def limb(d, x, y, angle, length, width, level, tips):
    """Recursive branch: golden-angle children, bent toward vertical further out."""
    ex = x + math.cos(angle) * length
    ey = y + math.sin(angle) * length
    taper(d, [(x, y), (ex, ey)], [width, width * 0.60], INDIGO + (255,))
    if width > 9:
        taper(d, [(x, y), (ex, ey)], [width * 0.46, width * 0.28], GOLD + (255,))
    if level >= 4:
        tips.append((ex, ey))
        return
    if level >= 2:
        tips.append((ex, ey))
    for child in range(3):
        spread = (child - 1) * 0.55 + math.sin(level * 2.399 + child * 1.7) * 0.20
        a = angle + spread
        a += (-math.pi / 2 - a) * 0.12 * (level + 1)
        limb(d, ex, ey, a, length * 0.65, width * 0.56, level + 1, tips)


def world_tree():
    """A tall buttressed trunk carrying limbs all the way up it.

    Two earlier attempts read as a mushroom, and the second one for an instructive
    reason: bending every child toward vertical piled all the tips at the same
    height, which is a cap on a stick. A crown is tall because the limbs leave the
    trunk over a long stretch of its height — low ones long and nearly horizontal,
    high ones short and nearly upright, which is apical control. That is the same
    rule the in-game model uses, 75 degrees off vertical at the bottom to 32 at the
    top, so the icon and the spell now describe the same tree.
    """
    base = layer()
    d = ImageDraw.Draw(base)
    ground = 962
    top = 300

    # Trunk: tall and slim, flaring at the foot into buttresses.
    d.polygon([(486, top), (538, top), (588, 800), (654, ground),
               (370, ground), (436, 800)], fill=INDIGO + (255,))
    d.polygon([(497, top), (527, top), (562, 800), (598, ground - 6),
               (426, ground - 6), (462, 800)], fill=GOLD + (255,))
    for side, reach in ((-1, 224), (1, 206), (-1, 128), (1, 142)):
        taper(d, [(512 + side * 102, 906),
                  (512 + side * (102 + reach * 0.55), ground - 18),
                  (512 + side * (102 + reach), ground + 12)],
              [38, 21, 7], INDIGO + (255,))

    tips = []
    for i in range(9):
        up = i / 8.0
        y = 800 - up * 500
        # Low limbs nearly horizontal, high limbs nearly upright.
        off = 1.31 - up * 0.78
        # Azimuth on the golden angle, then projected: a limb pointing toward or
        # away from the viewer reads as nearly vertical and foreshortened. That is
        # what fills the middle of the silhouette. Strict left-right alternation
        # gave two separate bushes flanking a bare post.
        azimuth = i * GOLDEN
        lateral = math.cos(azimuth)
        dx = math.sin(off) * lateral
        dy = -math.cos(off)
        a = math.atan2(dy, dx)
        squash = 0.58 + 0.42 * abs(lateral)
        length = 196 * (1.06 - 0.38 * up) * squash
        width = 40 * (1.0 - 0.34 * up)
        limb(d, 512 + lateral * 22 * (1.0 - up * 0.5), y, a, length, width, 0, tips)
    # A leader continuing out of the trunk's apex, so the crown closes over the top
    # instead of leaving bare trunk sticking through it.
    limb(d, 512, top + 10, -math.pi / 2 + 0.05, 150, 26, 1, tips)

    # Foliage: a soft halo behind, then an opaque core on top in the same hue.
    # Semi-transparent clumps alone washed into one gold mass, because overlapping
    # translucent layers average toward the brightest hue. An opaque core is what
    # lets each patch keep its own colour.
    halo = layer()
    cores = layer()
    h = ImageDraw.Draw(halo)
    c = ImageDraw.Draw(cores)
    for i, (x, y) in enumerate(tips):
        hue = LEAF_HUES[(i * 3) % len(LEAF_HUES)]
        for k in range(3):
            r = 26 + ((i * 7 + k * 13) % 5) * 8
            ox = math.sin(i * 2.4 + k * 1.9) * 30
            oy = math.cos(i * 1.7 + k * 2.6) * 26
            h.ellipse([x + ox - r * 1.5, y + oy - r * 1.5,
                       x + ox + r * 1.5, y + oy + r * 1.5], fill=hue + (105,))
            c.ellipse([x + ox - r * 0.62, y + oy - r * 0.62,
                       x + ox + r * 0.62, y + oy + r * 0.62], fill=hue + (255,))
    base = Image.alpha_composite(base, halo.filter(ImageFilter.GaussianBlur(13)))
    base = Image.alpha_composite(base, cores.filter(ImageFilter.GaussianBlur(2)))
    finish("world_tree", base, [glow(base, 38, 1.5), glow(base, 12, 1.2)])


for icon in (sky_collapse, stellar_convergence, second_sun, singularity,
             leviathan, world_tree):
    icon()
