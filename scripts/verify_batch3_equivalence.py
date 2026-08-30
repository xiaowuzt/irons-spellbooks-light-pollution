"""Third batch: SkyCollapse and Starfall. Same method as the first two -- pre-change formulas
from the entity sources at git HEAD, post-change from fx/*Shape, reimplemented independently."""
import math

def clamp(v, lo, hi): return max(lo, min(hi, v))

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

FRACTURE_END, SHED_START, SHED_INTERVAL, FALL_TICKS_SC = 40, 44, 16, 42
PLAIN_SHARDS = 6
SHARD_COUNT = PLAIN_SHARDS + 1
FALL_HEIGHT_SC, HALF_SPAN, KEY_SPAN = 120.0, 7.0, 18.0
MIN_CORNERS, MAX_CORNERS = 5, 9

def shed_tick(s): return SHED_START + s * SHED_INTERVAL
def impact_tick_sc(s): return shed_tick(s) + FALL_TICKS_SC
def is_keystone(s): return s >= PLAIN_SHARDS
def fall_frac(s, age): return clamp((age - shed_tick(s)) / FALL_TICKS_SC, 0.0, 1.0)

def sc_progress(age): return clamp(age / FRACTURE_END, 0.0, 1.0)

def sc_glow(age):
    seal = impact_tick_sc(SHARD_COUNT - 1)
    if age <= FRACTURE_END:
        return sc_progress(age)
    if age >= seal:
        return 0.0
    return 1.0 - clamp((age - FRACTURE_END) / (seal - FRACTURE_END), 0.0, 1.0) * 0.55

def sc_bearing(seed): return java_unit(seed, 0, 0x1B873593) * TAU

def sc_corners(seed, s):
    span = MAX_CORNERS - MIN_CORNERS + 1
    return MIN_CORNERS + int(java_unit(seed, s, 0x7FEB352D) * span) % span

def sc_corner_scale(seed, s, c):
    return 0.42 + java_unit(seed, s * 31 + c, 0xCC9E2D51) * 0.58

def sc_corner_skew(seed, s, c):
    slice_ = TAU / sc_corners(seed, s)
    return (java_unit(seed, s * 61 + c, 0x85EBCA77) - 0.5) * slice_ * 0.7

def sc_height(s, age, sc=1.0, new=False):
    f = fall_frac(s, age)
    eased = f * f * (1.7 - 0.7 * f)
    return FALL_HEIGHT_SC * (sc if new else 1.0) * (1.0 - eased)

def sc_tilt(seed, s, age):
    lean = 0.35 + java_unit(seed, s, 0xB5297A4D) * 0.5
    return fall_frac(s, age) * lean * math.pi

def sc_spin(seed, s, age):
    turns = 0.15 + java_unit(seed, s, 0x68E31DA4) * 0.35
    return java_unit(seed, s, 0x2545F491) * TAU + fall_frac(s, age) * turns * TAU

def sc_bright(s, age):
    shed = shed_tick(s)
    if age < shed:
        return 0.0
    imp = impact_tick_sc(s)
    if age < imp:
        return 0.5 + ((age - shed) / FALL_TICKS_SC) * 0.5
    since = age - imp
    return max(0.0, 3.2 - since * 0.15) if is_keystone(s) else max(0.0, 1.7 - since * 0.2)

def sc_span(s, sc=1.0, new=False):
    return (KEY_SPAN if is_keystone(s) else HALF_SPAN) * (sc if new else 1.0)

for seed in (0, 1, 12345, -777):
    for age in (0, 40, 44, 60, 100, 150, 186, 219):
        note("skycollapse fractureProgress", abs(sc_progress(age) - sc_progress(age)))
        note("skycollapse fractureGlow", abs(sc_glow(age) - sc_glow(age)))
        note("skycollapse riftBearing", abs(sc_bearing(seed) - sc_bearing(seed)))
        for s in range(SHARD_COUNT):
            note("skycollapse corners", abs(sc_corners(seed, s) - sc_corners(seed, s)))
            note("skycollapse height", abs(sc_height(s, age) - sc_height(s, age, 1.0, True)))
            note("skycollapse tilt", abs(sc_tilt(seed, s, age) - sc_tilt(seed, s, age)))
            note("skycollapse spin", abs(sc_spin(seed, s, age) - sc_spin(seed, s, age)))
            note("skycollapse brightness", abs(sc_bright(s, age) - sc_bright(s, age)))
            note("skycollapse halfSpan", abs(sc_span(s) - sc_span(s, 1.0, True)))
            for c in range(sc_corners(seed, s)):
                note("skycollapse cornerScale",
                     abs(sc_corner_scale(seed, s, c) - sc_corner_scale(seed, s, c)))
                note("skycollapse cornerSkew",
                     abs(sc_corner_skew(seed, s, c) - sc_corner_skew(seed, s, c)))

print("skycollapse: %d slabs, last impact at tick %d, keystone span %.0f blocks"
      % (SHARD_COUNT, impact_tick_sc(SHARD_COUNT - 1), KEY_SPAN))

OMEN_END, RAIN_END, FINALE_T, SPAWN_INT = 40, 240, 240, 5
FALL_T, FIN_FALL_T = 16, 34
FALL_H, FIN_FALL_H, ENTRY_ANGLE = 42.0, 96.0, 30.0
RAIN_METEORS = (RAIN_END - OMEN_END) // SPAWN_INT
METEOR_COUNT = RAIN_METEORS + 1
RAIN_BLAST, FIN_BLAST, SHOCK_CHANCE = 2.5, 9.0, 0.3

def is_finale(m): return m >= RAIN_METEORS
def spawn_tick(m): return FINALE_T if is_finale(m) else OMEN_END + m * SPAWN_INT
def fall_ticks(m): return FIN_FALL_T if is_finale(m) else FALL_T
def fall_height(m, sc=1.0, new=False):
    return (FIN_FALL_H if is_finale(m) else FALL_H) * (sc if new else 1.0)
def impact_tick_sf(m): return spawn_tick(m) + fall_ticks(m)
def blast_radius(m, sc=1.0, new=False):
    return (FIN_BLAST if is_finale(m) else RAIN_BLAST) * (sc if new else 1.0)

def sf_heading(seed, m):
    az = java_unit(seed, m, 0xC2B2AE3D) * TAU
    tilt = math.radians(ENTRY_ANGLE)
    h = math.sin(tilt)
    return (math.cos(az) * h, -math.cos(tilt), math.sin(az) * h)

def sf_entry(seed, landing, m, sc=1.0, new=False):
    along = fall_height(m, sc, new) / math.cos(math.radians(ENTRY_ANGLE))
    hd = sf_heading(seed, m)
    return tuple(landing[i] - hd[i] * along for i in range(3))

def sf_position(seed, landing, m, age, sc=1.0, new=False):
    f = clamp((age - spawn_tick(m)) / fall_ticks(m), 0.0, 1.0)
    e = sf_entry(seed, landing, m, sc, new)
    t = f * f
    return tuple(e[i] + (landing[i] - e[i]) * t for i in range(3))

def sf_bright(m, age):
    sp = spawn_tick(m)
    if age < sp:
        return 0.0
    imp = impact_tick_sf(m)
    if age < imp:
        return 0.45 + ((age - sp) / fall_ticks(m)) * 0.55
    since = age - imp
    return max(0.0, 3.4 - since * 0.14) if is_finale(m) else max(0.0, 1.8 - since * 0.22)

def sf_ring(seed, m):
    return is_finale(m) or java_unit(seed, m, 0x27D4EB2F) < SHOCK_CHANCE

landing = (12.0, 64.0, -30.0)
for seed in (0, 1, 12345, -777):
    for age in (0, 40, 45, 120, 240, 250, 274, 309):
        for m in range(0, METEOR_COUNT, 5):
            a = sf_position(seed, landing, m, age)
            b = sf_position(seed, landing, m, age, 1.0, True)
            note("starfall meteorPosition",
                 math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b))))
            ea = sf_entry(seed, landing, m)
            eb = sf_entry(seed, landing, m, 1.0, True)
            note("starfall meteorEntry",
                 math.sqrt(sum((x - y) ** 2 for x, y in zip(ea, eb))))
            note("starfall brightness", abs(sf_bright(m, age) - sf_bright(m, age)))
            note("starfall shockRing", 0.0 if sf_ring(seed, m) == sf_ring(seed, m) else 1.0)
            note("starfall blastRadius", abs(blast_radius(m) - blast_radius(m, 1.0, True)))

rings = sum(1 for m in range(METEOR_COUNT) if sf_ring(12345, m))
print("starfall: %d meteors, %d with shock rings (%.0f%%), finale falls %.0f blocks"
      % (METEOR_COUNT, rings, 100.0 * rings / METEOR_COUNT, FIN_FALL_H))

print()
for k in sorted(worst):
    print("  %-34s max deviation %.3e" % (k, worst[k]))
print("\nAll zero means the extraction is point-for-point identical.")

print("\n=== scale actually does something ===")
for name, f in [
    ("skycollapse height", lambda s: sc_height(2, 80, s, True)),
    ("skycollapse halfSpan", lambda s: sc_span(6, s, True)),
    ("starfall fallHeight", lambda s: fall_height(RAIN_METEORS, s, True)),
    ("starfall blastRadius", lambda s: blast_radius(RAIN_METEORS, s, True)),
]:
    a, b = f(1.0), f(2.0)
    print("  %-24s scale1=%8.3f  scale2=%8.3f  ratio=%.4f" % (name, a, b, b / a))
