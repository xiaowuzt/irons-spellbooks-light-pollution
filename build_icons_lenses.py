"""Generates the lens and jet spell icons — Gargantua, the Cosmic Horseshoe and the
microquasar —
in the same style as build_icons.py: transparent background, saturated strokes with a
bright inner highlight, and an outer glow. Drawn at 4x and downsampled.

Both are drawn from the same physics the shaders use, so the icon and the effect agree:
Gargantua is a dark shadow with the disk's far side lensed over and under it, and the
Horseshoe is a ~300 degree blue arc with a ~60 degree gap around a red core.
"""
import math
from PIL import Image, ImageDraw, ImageFilter

S = 1024
OUT = 256
DEST = ("src/main/resources/assets/irons_spellbooks_light_pollution"
        "/textures/gui/spell_icons/")
CX = CY = S // 2

WHITE = (245, 250, 255)
ARC_BLUE = (120, 175, 255)
ARC_PALE = (210, 232, 255)
LRG_ORANGE = (255, 140, 66)
LRG_DEEP = (198, 74, 30)
DISK_HOT = (255, 248, 230)
DISK_WARM = (255, 214, 150)
DISK_AMBER = (252, 170, 86)
DISK_EMBER = (214, 108, 38)


def layer():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def glow(src, radius, gain):
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


def ramp(stops, t):
    if t <= stops[0][0]:
        return stops[0][1]
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        if t <= p1:
            k = (t - p0) / (p1 - p0)
            return tuple(int(c0[j] + (c1[j] - c0[j]) * k) for j in range(3))
    return stops[-1][1]


def blob(draw, x, y, r, colour, alpha=255):
    draw.ellipse([x - r, y - r, x + r, y + r], fill=colour + (alpha,))


def falloff(draw, x, y, outer, colour, power=1.9, steps=34):
    """A centrally concentrated blob, drawn as nested rings of rising alpha."""
    for i in range(steps, 0, -1):
        t = i / steps
        r = outer * t
        a = int(255 * math.exp(-power * t * 2.4))
        blob(draw, x, y, r, colour, max(0, min(255, a)))


def tidal_disruption():
    """A star drawn out into a wrapping stream, hot at the leading tip.

    Blue-white at the tip because that end sits deepest in the tidal field, cooling to red
    at the trailing end. The wrap is the bound debris coming back round.
    """
    base = layer()
    stream = layer()
    d = ImageDraw.Draw(stream)
    turns, reach = 1.65, 215.0
    for k in range(1, 320):
        t = k / 320.0
        phase = t * turns * 2.0 * math.pi
        r = reach * (0.16 + 0.84 * t)
        x = CX + math.cos(phase) * r
        y = CY + math.sin(phase) * r * 0.52 - 40.0 * math.sin(t * math.pi * 1.1)
        hue = ramp([(0.0, (200, 226, 255)), (1.0, (255, 100, 44))], t)
        width = 4.0 + 9.0 * t
        lump = math.exp(-((t * 9.0 % 1.0) - 0.5) ** 2 * 20.0)
        blob(d, x, y, width * (0.75 + 0.5 * lump), hue, 235)
    base = Image.alpha_composite(base, stream.filter(ImageFilter.GaussianBlur(4)))
    # The hole: dark, with only the debris to show where it is.
    dark = layer()
    blob(ImageDraw.Draw(dark), CX, CY, 46, (5, 3, 9), 255)
    base = Image.alpha_composite(base, dark)
    finish("tidal_disruption", base, [glow(base, 40, 1.5), glow(base, 13, 1.25)])


def quasar_jet():
    """One straight needle with knots racing along it into a terminal lobe.

    Single-sided, because beaming makes the counter-jet invisible — that asymmetry is the
    whole visual signature and the reason it is not the microquasar.
    """
    base = layer()
    ang = math.radians(-22.0)
    dx, dy = math.cos(ang), math.sin(ang)
    length = 430.0
    ox, oy = CX - dx * length * 0.42, CY - dy * length * 0.42

    beam = layer()
    d = ImageDraw.Draw(beam)
    for k in range(300):
        t = k / 299.0
        x, y = ox + dx * length * t, oy + dy * length * t
        blob(d, x, y, 5.0 * (1.0 + t * 0.5), (150, 196, 255), 120)
    base = Image.alpha_composite(base, beam.filter(ImageFilter.GaussianBlur(4)))

    knots = layer()
    d = ImageDraw.Draw(knots)
    for frac in (0.18, 0.40, 0.62, 0.82):
        x, y = ox + dx * length * frac, oy + dy * length * frac
        blob(d, x, y, 30, (150, 200, 255), 130)
        blob(d, x, y, 17, (215, 235, 255), 245)
        blob(d, x, y, 8, WHITE, 255)
    # Terminal hotspot inside a diffuse lobe.
    lx, ly = ox + dx * length, oy + dy * length
    falloff(d, lx, ly, 120, (255, 190, 130), power=2.0)
    blob(d, lx, ly, 26, (255, 235, 200), 255)
    # The nucleus.
    falloff(d, ox, oy, 66, WHITE, power=2.8)
    base = Image.alpha_composite(base, knots.filter(ImageFilter.GaussianBlur(3)))
    finish("quasar_jet", base, [glow(base, 40, 1.55), glow(base, 12, 1.3)])


def pinwheel():
    """Two Archimedean dust arms with empty space between them.

    Constant pitch and red-brown dust colours, both chosen so it cannot be read as a third
    accretion disk. The gaps are as important as the arms.
    """
    base = layer()
    arms = layer()
    d = ImageDraw.Draw(arms)
    reach, turns = 400.0, 2.2
    for arm in range(2):
        for k in range(1, 300):
            t = k / 300.0
            a = t * turns * 2.0 * math.pi + arm * math.pi
            r = reach * t
            x = CX + math.cos(a) * r
            y = CY + math.sin(a) * r * 0.94
            hue = ramp([(0.0, (255, 168, 96)), (1.0, (148, 56, 36))], t)
            lump = 0.55 + 0.6 * abs(math.sin(t * 21.0))
            blob(d, x, y, (5.0 + 8.0 * t) * lump, hue, int(215 * (1.0 - t * 0.45)))
    base = Image.alpha_composite(base, arms.filter(ImageFilter.GaussianBlur(5)))
    core = layer()
    falloff(ImageDraw.Draw(core), CX, CY, 84, (255, 236, 200), power=2.6)
    base = Image.alpha_composite(base, core)
    finish("pinwheel", base, [glow(base, 38, 1.45), glow(base, 12, 1.2)])


def crab_nebula():
    """A hollow cage of filaments with a smooth blue interior glow.

    The cage is drawn as arcs on a sphere with nothing between them, and the interior is a
    structureless synchrotron glow — two different materials, which is the point.
    """
    base = layer()
    # The wind nebula first, so the cage reads as in front of it.
    wind = layer()
    falloff(ImageDraw.Draw(wind), CX, CY, 268, (150, 196, 255), power=1.5)
    base = Image.alpha_composite(base, wind)

    cage = layer()
    d = ImageDraw.Draw(cage)
    radius = 372.0
    for f in range(16):
        lean = (f * 0.618) % 1.0 * math.pi
        spin = (f * 0.379) % 1.0 * 2.0 * math.pi
        span = math.pi * (0.55 + 0.7 * ((f * 7) % 5) / 4.0)
        start = ((f * 11) % 7) / 7.0 * 2.0 * math.pi
        green = (f % 3) == 0
        hue = (110, 255, 140) if green else (255, 92, 78)
        for k in range(70):
            t = k / 69.0
            a = start + span * t
            ripple = 1.0 + 0.09 * math.sin(a * 3.0 + f)
            ux, uz = math.cos(spin), math.sin(spin)
            wx = -math.sin(spin) * math.cos(lean)
            wy = math.sin(lean)
            x = CX + (math.cos(a) * ux + math.sin(a) * wx) * radius * ripple
            y = CY + (math.sin(a) * wy) * radius * ripple * 0.9
            ends = math.sin(t * math.pi) ** 0.4
            blob(d, x, y, 7.0 * ends, hue, int(230 * ends))
    base = Image.alpha_composite(base, cage.filter(ImageFilter.GaussianBlur(3)))
    finish("crab_nebula", base, [glow(base, 40, 1.5), glow(base, 12, 1.25)])


def magnetar():
    """Closed dipole loops around a neutron star, wound and about to let go.

    Built from the dipole relation the shader and entity use, r = r0 sin^2(theta), taken
    to Cartesian the straightforward way: z = r cos(theta) along the axis and rho =
    r sin(theta) out from it. An earlier attempt mangled the axial term and swept azimuth
    over only half a turn using its sine for the in-plane component, which is non-negative
    there — so every loop went to the same side and the whole thing bunched into a blob.

    Colour runs violet at the outside to white-hot near the star, because a dipole's
    strength goes as 1/r^3 and the equatorial bulge really is the weak part.
    """
    base = layer()
    reach = 250.0
    tilt = math.radians(22.0)
    # Axis on screen, and the in-plane direction perpendicular to it. y grows downward.
    axis_x, axis_y = math.sin(tilt), -math.cos(tilt)
    perp_x, perp_y = math.cos(tilt), math.sin(tilt)

    loops = layer()
    d = ImageDraw.Draw(loops)
    lines = 12
    for line in range(lines):
        az = 2.0 * math.pi * line / lines
        scale = 0.52 + 0.48 * ((line % 3) / 2.0)
        for k in range(1, 100):
            theta = math.pi * k / 100.0
            sin = math.sin(theta)
            r = reach * scale * sin * sin
            z = r * math.cos(theta)
            rho = r * sin
            inPlane = rho * math.cos(az)
            depth = rho * math.sin(az)

            x = CX + axis_x * z + perp_x * inPlane
            y = CY + axis_y * z + perp_y * inPlane * 0.55

            near = 1.0 - min(1.0, r / reach)
            hue = ramp([(0.0, (128, 92, 255)), (1.0, (255, 226, 176))], near)
            # Depth only dims and thins; it never moves the point.
            front = 0.55 + 0.45 * (depth / max(reach, 1.0))
            alpha = int(max(0, min(255, 210 * front * (0.35 + 0.65 * near))))
            blob(d, x, y, (4.5 + 4.5 * near) * front, hue, alpha)
    base = Image.alpha_composite(base, loops.filter(ImageFilter.GaussianBlur(4)))

    # The star: small, and the only genuinely white thing here.
    core = layer()
    falloff(ImageDraw.Draw(core), CX, CY, 104, (200, 190, 255), power=2.6)
    falloff(ImageDraw.Draw(core), CX, CY, 40, WHITE, power=3.0)
    base = Image.alpha_composite(base, core)

    finish("magnetar", base, [glow(base, 42, 1.6), glow(base, 13, 1.3)])


def helix_nebula():
    """Two nested rings of cometary knots, tails pointing radially outward.

    The inner ring is doubly ionised oxygen (blue-green) and the outer hydrogen and
    nitrogen (red) — the real object's two shells, not a gradient. Every knot's tail
    points away from the centre, which is the geometry photoevaporation produces and the
    single feature that makes it read as comets rather than dots.
    """
    base = layer()
    inner_r = 210.0
    outer_r = inner_r * 1.42
    tilt = 0.62          # squash of the far axis, giving the oval eye

    knots = layer()
    d = ImageDraw.Draw(knots)
    for ring, (radius, hue, count) in enumerate((
            (inner_r, (92, 240, 210), 54),
            (outer_r, (255, 92, 76), 66))):
        for i in range(count):
            a = 2.0 * math.pi * i / count + ring * 0.4
            # Jitter through the shell's thickness: the knots fill a layer, not a wire.
            jitter = ((i * 37 + ring * 11) % 7 - 3) * 7.0
            r = radius + jitter
            cx = CX + math.cos(a) * r
            cy = CY + math.sin(a) * r * tilt
            # Head, then a tail stepping outward along the same radial direction.
            blob(d, cx, cy, 13, hue, 255)
            for k in range(1, 7):
                t = k / 6.0
                tx = cx + math.cos(a) * 30.0 * t
                ty = cy + math.sin(a) * 30.0 * t * tilt
                blob(d, tx, ty, 11 * (1.0 - t * 0.7), hue, int(190 * (1.0 - t)))
    base = Image.alpha_composite(base, knots.filter(ImageFilter.GaussianBlur(3)))

    # The white dwarf: small, hot, and the source of everything above.
    core = layer()
    falloff(ImageDraw.Draw(core), CX, CY, 120, (190, 235, 255), power=2.8)
    falloff(ImageDraw.Draw(core), CX, CY, 44, WHITE, power=3.2)
    base = Image.alpha_composite(base, core)

    finish("helix_nebula", base, [glow(base, 40, 1.55), glow(base, 13, 1.25)])


def microquasar():
    """Twin corkscrew jets, one beamed blue toward the viewer and one red away.

    A helix seen from the side projects to a sinusoid, so the sine is right — but a bare
    sine reads as a zigzag, which is what the first attempt looked like. What makes it
    read as a coil is depth: the phase also says whether that part of the turn is near
    the viewer or behind, so stroke width and brightness are modulated by it. The near
    half of every turn is thick and bright, the far half thin and dim.

    Transverse amplitude is tan(20 degrees) of the distance travelled, from the cone
    half-angle, rather than picked by eye.
    """
    base = layer()
    turns = 2.6
    cone = math.radians(20.0)
    spread = math.tan(cone)
    reach = 400.0
    steps = 260

    jets = layer()
    d = ImageDraw.Draw(jets)
    for sign in (1.0, -1.0):
        approaching = sign > 0.0
        hue = (150, 205, 255) if approaching else (255, 118, 92)
        # Relativistic beaming: the jet coming at the viewer is the bright one.
        peak = 250 if approaching else 140
        thick = 15.0 if approaching else 11.0
        for k in range(steps + 1):
            t = k / steps
            travel = reach * t
            phase = turns * 2.0 * math.pi * t
            # Axis runs out to the side and slightly up; the two jets are opposed, so
            # the pair is point-symmetric about the disk.
            ax = sign * travel * 0.86
            ay = -sign * travel * 0.26
            # One transverse axis is visible, the other is depth.
            offset = spread * travel
            across = math.sin(phase) * offset
            depth = math.cos(phase)
            x = CX + ax + across * 0.30
            y = CY + ay + across * 0.92
            near = 0.45 + 0.55 * (depth * 0.5 + 0.5)
            # Knots, because the ejecta leave the disk as discrete bullets.
            knot = math.exp(-((t * 10.0 % 1.0) - 0.5) ** 2 * 24.0)
            width = thick * near * (0.70 + 0.55 * knot) * (1.0 - t * 0.42)
            blob(d, x, y, width, hue, int(peak * near))
    base = Image.alpha_composite(base, jets.filter(ImageFilter.GaussianBlur(4)))

    # The disk at the origin, seen close to edge-on, and its hot centre.
    disk = layer()
    dd = ImageDraw.Draw(disk)
    for k in range(140):
        a = 2.0 * math.pi * k / 140.0
        blob(dd, CX + math.cos(a) * 104, CY + math.sin(a) * 27, 15,
             (255, 224, 165), 255)
    base = Image.alpha_composite(base, disk.filter(ImageFilter.GaussianBlur(6)))
    hot = layer()
    falloff(ImageDraw.Draw(hot), CX, CY, 92, WHITE, power=2.4)
    base = Image.alpha_composite(base, hot)

    finish("microquasar", base, [glow(base, 38, 1.5), glow(base, 12, 1.25)])


def cosmic_horseshoe():
    """A 300 degree blue arc with a 60 degree gap, around a red elliptical core.

    Belokurov et al. measure the real arc at about 300 degrees, so the gap is drawn at
    60 and centred at the bottom. The four bright knots are the four star-forming
    regions Jones et al. resolve, and the short stub inside the ring opposite the arc's
    midpoint is the counter-image — the lens map's second branch.
    """
    ring = 352
    base = layer()

    # The lens galaxy: red, old, centrally concentrated, well inside the ring.
    core = layer()
    falloff(ImageDraw.Draw(core), CX, CY, 205, LRG_ORANGE, power=2.2)
    falloff(ImageDraw.Draw(core), CX, CY, 58, WHITE, power=3.4)
    base = Image.alpha_composite(base, core)

    # The counter-image: faint, inside the ring, opposite the arc.
    counter = layer()
    d = ImageDraw.Draw(counter)
    for k in range(-10, 11):
        a = math.radians(90.0 + k * 1.5)
        blob(d, CX + math.cos(a) * 232, CY + math.sin(a) * 232, 15,
             ARC_BLUE, 150)
    base = Image.alpha_composite(base, counter.filter(ImageFilter.GaussianBlur(6)))

    # The arc. Gap centred at +90 degrees (screen bottom), so it runs -60 to +240.
    arc = layer()
    d = ImageDraw.Draw(arc)
    span = 300.0
    start = 120.0
    stops = [(0.0, ARC_BLUE), (0.5, ARC_PALE), (1.0, ARC_BLUE)]
    steps = 460
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(start + span * t)
        x, y = CX + math.cos(a) * ring, CY + math.sin(a) * ring
        # Thinner toward the ends, because the magnification falls off there.
        taperEnd = math.sin(math.pi * t) ** 0.35
        blob(d, x, y, 21 * taperEnd, ramp(stops, t), 255)

    # The four star-forming knots.
    for frac in (0.14, 0.38, 0.63, 0.87):
        a = math.radians(start + span * frac)
        x, y = CX + math.cos(a) * ring, CY + math.sin(a) * ring
        blob(d, x, y, 40, ARC_BLUE, 130)
        blob(d, x, y, 27, ARC_PALE, 235)
        blob(d, x, y, 13, WHITE, 255)
    base = Image.alpha_composite(base, arc)

    finish("cosmic_horseshoe", base,
           [glow(base, 40, 1.5), glow(base, 13, 1.25)])


def gargantua():
    """The shadow, the disk seen nearly edge-on, and the far side lensed over and under.

    The proportions are the shader's: the shadow an observer sees is sqrt(27) = 5.196
    r_g against a horizon of 1.8, so the dark patch is 2.6 times the hole itself, and
    the disk starts at the innermost stable circular orbit, 3.83 r_g. No blue anywhere,
    because the disk runs about 4500 K.
    """
    rg = 66.0
    shadow = rg * 5.196 * 0.42
    # How far the disk reaches to either side, as a multiple of the shadow. Shared by the
    # near-side bar and the far-side arcs so the surface joins up.
    #
    # Much wider than tall, which is the whole character of the thing: the disk is seen
    # from close to its own plane, so it spreads sideways while the lensed far side only
    # just clears the top of the shadow. Earlier passes had the vertical extent nearly as
    # large as the horizontal, which closed the silhouette into an eye.
    reach = 2.30
    base = layer()

    # The far side of the disk, wrapping over the shadow and under it. Drawn first so the
    # near side passes in front.
    #
    # These arcs deliberately do NOT reach the near side's tips. Several earlier attempts
    # had them span a full 180 degrees at the bar's own width, which joined up into an
    # unbroken elliptical outline around a black circle — and an outline around a dark
    # circle with a bright line across it reads as an eye, not as a black hole. The disk's
    # far side is a broad band that merges into the near side well inside its reach, so
    # the bar sticks out past it on both sides and the silhouette never closes.
    far = layer()
    d = ImageDraw.Draw(far)
    for a0, a1, rx, ry, thick, alpha in ((188.0, 352.0, 1.52, 1.22, 27.0, 245),
                                         (16.0, 164.0, 1.34, 1.06, 15.0, 150)):
        for k in range(190):
            t = k / 189.0
            a = math.radians(a0 + (a1 - a0) * t)
            x = CX + math.cos(a) * shadow * rx
            y = CY + math.sin(a) * shadow * ry
            hue = ramp([(0.0, DISK_EMBER), (0.5, DISK_WARM), (1.0, DISK_EMBER)], t)
            blob(d, x, y, thick * math.sin(math.pi * t) ** 0.42, hue, alpha)
    base = Image.alpha_composite(base, far.filter(ImageFilter.GaussianBlur(6)))

    # The shadow. Genuinely dark, and opaque, so the far-side arcs read as passing
    # behind it rather than through it.
    dark = layer()
    falloff(ImageDraw.Draw(dark), CX, CY, shadow * 1.04, (6, 3, 10), power=0.55)
    blob(ImageDraw.Draw(dark), CX, CY, shadow * 0.80, (6, 3, 10), 255)
    base = Image.alpha_composite(base, dark)

    # The near side, crossing in front like the edge of a blade.
    near = layer()
    d = ImageDraw.Draw(near)
    for k in range(220):
        t = k / 219.0
        x = CX - shadow * reach + shadow * 2.0 * reach * t
        y = CY + shadow * 0.24
        hue = ramp([(0.0, DISK_AMBER), (0.35, DISK_HOT),
                    (0.65, DISK_WARM), (1.0, DISK_AMBER)], t)
        blob(d, x, y, 17 * math.sin(math.pi * t) ** 0.30, hue, 255)
    base = Image.alpha_composite(base, near)

    # The photon ring, on the shadow's edge. Faint and warm — it is the disk's own light
    # wrapped round the hole, so it cannot outshine the disk or differ in colour.
    ring = layer()
    d = ImageDraw.Draw(ring)
    for k in range(240):
        a = 2.0 * math.pi * k / 240.0
        blob(d, CX + math.cos(a) * shadow, CY + math.sin(a) * shadow, 7,
             DISK_WARM, 190)
    base = Image.alpha_composite(base, ring.filter(ImageFilter.GaussianBlur(3)))

    finish("gargantua", base, [glow(base, 42, 1.45), glow(base, 14, 1.2)])


for icon in (cosmic_horseshoe, gargantua, microquasar, helix_nebula, magnetar,
             tidal_disruption, quasar_jet, pinwheel, crab_nebula):
    icon()
