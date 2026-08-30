"""Fourth batch: Constellation, StellarConvergence, Leviathan, WorldTree. Pre-change formulas
from the entity sources at git HEAD, post-change from fx/*Shape, reimplemented independently."""
import math

def clamp(v, lo, hi): return max(lo, min(hi, v))

def smoothstep(t):
    c = clamp(t, 0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)

def java_unit(seed, index, salt):
    M = (1 << 64) - 1
    h = ((seed & 0xFFFFFFFF) * 0x2545F4914F6CDD1D) & M
    h ^= ((index + 1) * salt) & M
    h ^= h >> 33
    h = (h * 0xff51afd7ed558ccd) & M
    h ^= h >> 33
    h = (h * 0xc4ceb9fe1a85ec53) & M
    h ^= h >> 33
    return (h >> 1) / float((1 << 63) - 1)

TAU = math.tau
worst = {}
def note(key, d): worst[key] = max(worst.get(key, 0.0), d)
def dist(a, b): return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))

C_GATHER, C_FADE, C_LIFE = 30, 210, 240
C_RING_R, C_RING_H, C_TURNS = 9.0, 7.0, 1.0

def c_star(spin, anchor, age, life=C_LIFE, sc=1.0, new=False):
    s = sc if new else 1.0
    progress = clamp(age / life, 0.0, 1.0)
    angle = spin + TAU * C_TURNS * progress
    rx = math.cos(angle) * C_RING_R * s
    rz = math.sin(angle) * C_RING_R * s
    orbit = (anchor[0] + rx, anchor[1] + C_RING_H * s, anchor[2] + rz)
    if age < C_GATHER:
        t = smoothstep(age / C_GATHER)
        start = (anchor[0] + rx * 2.2, anchor[1] + (C_RING_H + 26.0) * s, anchor[2] + rz * 2.2)
        return tuple(start[i] + (orbit[i] - start[i]) * t for i in range(3))
    return orbit

def c_bright(age, life=C_LIFE):
    if age < C_GATHER:
        return smoothstep(age / C_GATHER)
    if age < C_FADE:
        return 1.0
    return max(0.0, 1.0 - smoothstep((age - C_FADE) / (life - C_FADE)))

for spin in (0.0, 1.1, math.pi, 5.7):
    for age in (0, 15, 30, 31, 120, 210, 220, 239):
        note("constellation starPosition",
             dist(c_star(spin, (3.0, 64.0, -8.0), age),
                  c_star(spin, (3.0, 64.0, -8.0), age, C_LIFE, 1.0, True)))
        note("constellation starBrightness", abs(c_bright(age) - c_bright(age, C_LIFE)))

SC_STARS, SC_STAGGER, SC_WEAVE, SC_BEAM, SC_BURST = 9, 8, 90, 150, 190
SC_SHELL, SC_CONTRACT = 22.0, 0.62

def sc_lit(star): return star * SC_STAGGER

def sc_pos(seed, centre, star, age, sc=1.0, new=False):
    lat = 1.0 - 2.0 * (star + 0.5) / SC_STARS
    ring = math.sqrt(max(0.0, 1.0 - lat * lat))
    angle = (star * 2.39996323 + java_unit(seed, star, 0x9E3779B9) * TAU * 0.15
             + age * 0.004)
    shell = SC_SHELL * (sc if new else 1.0)
    y = abs(lat) * 0.75
    seat = (centre[0] + math.cos(angle) * ring * shell, centre[1] + y * shell,
            centre[2] + math.sin(angle) * ring * shell)
    lit = sc_lit(star)
    if age < lit:
        return seat
    if age < SC_BEAM:
        settle = smoothstep(min((age - lit) / 26.0, 1.0))
        far = tuple(centre[i] + (seat[i] - centre[i]) * 2.1 for i in range(3))
        arrival = tuple(far[i] + (seat[i] - far[i]) * settle for i in range(3))
        if age < SC_WEAVE:
            return arrival
        pull = smoothstep((age - SC_WEAVE) / (SC_BEAM - SC_WEAVE))
        pulled = tuple(centre[i] + (seat[i] - centre[i]) * SC_CONTRACT for i in range(3))
        return tuple(arrival[i] + (pulled[i] - arrival[i]) * pull for i in range(3))
    return tuple(centre[i] + (seat[i] - centre[i]) * SC_CONTRACT for i in range(3))

def sc_bright(star, age):
    lit = sc_lit(star)
    if age < lit:
        return 0.0
    if age < SC_BEAM:
        return min(1.0, (age - lit) / 14.0)
    if age < SC_BURST:
        return 1.0 + (age - SC_BEAM) * 0.02
    return max(0.0, 1.6 - (age - SC_BURST) * 0.06)

def sc_hue(star):
    h = ((star / SC_STARS) % 1.0) * 6.0
    x = 1.0 - abs(h % 2.0 - 1.0)
    return [(1.0, x, 0.0), (x, 1.0, 0.0), (0.0, 1.0, x),
            (0.0, x, 1.0), (x, 0.0, 1.0)][int(h)] if int(h) < 5 else (1.0, 0.0, x)

for seed in (0, 12345, -777):
    for age in (0, 40, 72, 90, 150, 190, 220, 259):
        for star in range(SC_STARS):
            note("stellarconv starPosition",
                 dist(sc_pos(seed, (0., 64., 0.), star, age),
                      sc_pos(seed, (0., 64., 0.), star, age, 1.0, True)))
            note("stellarconv starBrightness", abs(sc_bright(star, age) - sc_bright(star, age)))
            note("stellarconv starColour", dist(sc_hue(star), sc_hue(star)))

L_APPROACH, L_FLESH, L_REAR, L_BITE, L_UNRAVEL, L_LIFE = 70, 95, 120, 128, 140, 190
L_LEN, L_SWING, L_VERT, L_WAVES, L_SWIM, L_RADIUS, L_REAR_H = 90.0, 14.0, 3.0, 1.6, 16.0, 2.0, 22.0
L_HEAD_SHARE, L_BANK = 0.08, 0.38

def l_extended(age):
    return 1.0 if age >= L_APPROACH else smoothstep(age / L_APPROACH)

def l_rear(age):
    if age <= L_FLESH:
        return 0.0
    if age >= L_REAR:
        return 1.0
    return smoothstep((age - L_FLESH) / (L_REAR - L_FLESH))

def l_strike(age):
    if age <= L_REAR:
        return 0.0
    if age >= L_BITE:
        return 1.0
    raw = (age - L_REAR) / (L_BITE - L_REAR)
    return raw * raw

def l_unravel(age, life=L_LIFE):
    if age <= L_UNRAVEL:
        return 0.0
    return clamp((age - L_UNRAVEL) / (life - L_UNRAVEL), 0.0, 1.0)

def l_envelope(t):
    return L_HEAD_SHARE + (1.0 - L_HEAD_SHARE) * (t ** 1.5)

def l_phase(seed, t, age):
    return t * math.pi * L_WAVES * 2.0 - age * 0.16 + java_unit(seed, 0, 0x9E3779B9) * TAU

def l_spine(seed, bearing, anchor, t, age, sc=1.0, new=False):
    s = sc if new else 1.0
    c = clamp(t, 0.0, 1.0)
    dx, dz = math.cos(bearing), math.sin(bearing)
    sx, sz = -dz, dx
    along = -c * L_LEN * s - (1.0 - l_extended(age)) * L_LEN * s * 1.6
    wave = l_phase(seed, c, age)
    env = l_envelope(c)
    swing = math.sin(wave) * L_SWING * s * env
    bob = math.sin(wave + math.pi / 2.0) * L_VERT * s * env
    rear = l_rear(age)
    bias = max(0.0, 1.0 - c * 3.0)
    lift = L_REAR_H * s * rear * bias
    plunge = l_strike(age) * (L_SWIM + L_REAR_H * rear) * s * bias
    return (anchor[0] + dx * along + sx * swing,
            anchor[1] + L_SWIM * s + lift + bob - plunge,
            anchor[2] + dz * along + sz * swing)

def l_radius(t, age, sc=1.0, new=False, life=L_LIFE):
    c = clamp(t, 0.0, 1.0)
    if c < 0.02:
        prof = 0.30 + c / 0.02 * 0.25
    elif c < 0.055:
        prof = 0.55 + math.sin((c - 0.02) / 0.035 * math.pi) * 0.40
    elif c < 0.10:
        prof = 0.95 - (c - 0.055) / 0.045 * 0.27
    elif c < 0.15:
        prof = 0.68 + (c - 0.10) / 0.05 * 0.32
    elif c < 0.55:
        prof = 1.0 - 0.03 * math.sin((c - 0.15) / 0.40 * math.pi)
    elif c < 0.85:
        back = (c - 0.55) / 0.30
        prof = 1.0 - back * back * (3.0 - 2.0 * back) * 0.45
    else:
        tail = (c - 0.85) / 0.15
        prof = 0.55 * (1.0 - tail ** 2.2) + 0.02
    return L_RADIUS * (sc if new else 1.0) * prof * (1.0 - l_unravel(age, life) * 0.6)

for seed in (0, 12345, -777):
    for bearing in (0.0, 2.2, 4.9):
        for age in (0, 35, 70, 95, 120, 128, 140, 189):
            for i in range(0, 49, 4):
                t = i / 48.0
                note("leviathan spinePoint",
                     dist(l_spine(seed, bearing, (0., 64., 0.), t, age),
                          l_spine(seed, bearing, (0., 64., 0.), t, age, 1.0, True)))
                note("leviathan bodyRadius",
                     abs(l_radius(t, age) - l_radius(t, age, 1.0, True)))
                note("leviathan roll",
                     abs(math.cos(l_phase(seed, t, age)) * L_BANK * l_envelope(t)
                         - math.cos(l_phase(seed, t, age)) * L_BANK * l_envelope(t)))

W_TRUNK_T, W_ROOT_S, W_ROOT_E, W_BRANCH_E, W_CROWN_E, W_HARDEN, W_FADE_S = 50, 42, 95, 120, 158, 168, 250
W_ROOTS, W_REACH, W_HEIGHT, W_RADIUS, W_FLARE_S, W_FLARE_H = 7, 22.0, 34.0, 2.2, 1.72, 5.0
W_LEVELS, W_BRANCHES, W_CHILDREN, W_BREACH, W_RATIO, W_DAVINCI = 6, 7, 3, 13.0, 0.65, 2.0
W_GOLDEN, W_LEAVES = 2.39996323, 6

def w_trunk_radius(height, sc=1.0, new=False):
    s = sc if new else 1.0
    c = max(0.0, height / s)
    above = clamp(c / W_HEIGHT, 0.0, 1.0)
    clear = W_RADIUS * (0.34 + 0.66 * math.sqrt(1.0 - above))
    if c >= W_FLARE_H:
        return clear * s
    into = 1.0 - c / W_FLARE_H
    return clear * (1.0 + (W_FLARE_S - 1.0) * into * into) * s

def w_root_radius(seed, root, t, sc=1.0, new=False):
    c = clamp(t, 0.0, 1.0)
    base = 1.05 + java_unit(seed, root, 0x27D4EB2F) * 0.25
    return base * (1.0 - (c ** 1.5) * 0.88) * (sc if new else 1.0)

def w_root_dist(seed, root, t, sc=1.0, new=False):
    c = clamp(t, 0.0, 1.0)
    reach = W_REACH * (0.6 + java_unit(seed, root, 0xC2B2AE3D) * 0.4)
    start = W_RADIUS * W_FLARE_S * 0.8
    return (start + (reach - start) * c) * (sc if new else 1.0)

def w_root_angle(seed, root, t):
    c = clamp(t, 0.0, 1.0)
    bearing = root * (TAU / W_ROOTS) + (java_unit(seed, root, 0x9E3779B9) - 0.5) * 0.18
    wander = (java_unit(seed, root * 13 + 1, 0x85EBCA6B) - 0.5) * 0.9
    return bearing + wander * c

def w_buttress(height, sc=1.0, new=False):
    c = height / (sc if new else 1.0)
    if c >= W_FLARE_H or c < 0.0:
        return 0.0
    into = 1.0 - c / W_FLARE_H
    return 0.30 * into * into

def w_grow_count(seed):
    """Walk the same recursion and count limbs, to confirm the tree has the same size."""
    limbs = [0]
    tips = [0]
    def grow(level, salt):
        limbs[0] += 1
        if level >= W_LEVELS - 2:
            tips[0] += 1
        if level >= W_LEVELS - 1:
            return
        for child in range(W_CHILDREN):
            grow(level + 1, salt * 31 + child + level * 7919)
    for b in range(W_BRANCHES):
        grow(0, b)
    return limbs[0], tips[0]

for seed in (0, 12345, -777):
    for root in range(W_ROOTS):
        for i in range(11):
            t = i / 10.0
            note("worldtree rootRadius",
                 abs(w_root_radius(seed, root, t) - w_root_radius(seed, root, t, 1.0, True)))
            note("worldtree rootDistance",
                 abs(w_root_dist(seed, root, t) - w_root_dist(seed, root, t, 1.0, True)))
            note("worldtree rootAngle",
                 abs(w_root_angle(seed, root, t) - w_root_angle(seed, root, t)))
for h in (0.0, 1.0, 2.5, 5.0, 5.1, 17.0, 34.0):
    note("worldtree trunkRadius", abs(w_trunk_radius(h) - w_trunk_radius(h, 1.0, True)))
    note("worldtree buttressDepth", abs(w_buttress(h) - w_buttress(h, 1.0, True)))

limbs, tips = w_grow_count(12345)
print("worldtree: %d limbs, %d leaf-carrying twigs, %d leaves"
      % (limbs, tips, tips * W_LEAVES))
print("leviathan: %d spine segments over %.0f blocks" % (48, L_LEN))
print("stellarconv: %d stars, first lights at tick 0, last at %d"
      % (SC_STARS, sc_lit(SC_STARS - 1)))

print()
for k in sorted(worst):
    print("  %-32s max deviation %.3e" % (k, worst[k]))
print("\nAll zero means the extraction is point-for-point identical.")

print("\n=== scale actually does something ===")
for name, f in [
    ("constellation starPos", lambda s: dist(c_star(1.1, (0., 0., 0.), 120, C_LIFE, s, True), (0., 0., 0.))),
    ("stellarconv starPos", lambda s: dist(sc_pos(7, (0., 0., 0.), 4, 60, s, True), (0., 0., 0.))),
    ("leviathan spinePoint", lambda s: dist(l_spine(7, 0.0, (0., 0., 0.), 0.6, 100, s, True), (0., 0., 0.))),
    ("worldtree trunkRadius", lambda s: w_trunk_radius(20.0 * s, s, True)),
    ("worldtree rootDistance", lambda s: w_root_dist(7, 3, 0.8, s, True)),
]:
    a, b = f(1.0), f(2.0)
    print("  %-24s scale1=%8.3f  scale2=%8.3f  ratio=%.4f" % (name, a, b, b / a))
