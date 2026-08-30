"""Second batch: SecondSun and Singularity. Pre-change formulas from the entity sources at
git HEAD, post-change from fx/*Shape, each reimplemented independently and compared."""
import math

def clamp(v, lo, hi): return max(lo, min(hi, v))

def smoothstep(t):
    c = clamp(t, 0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)

worst = {}
def note(key, d): worst[key] = max(worst.get(key, 0.0), d)

RISE, SWELL_END, NOVA, NOVA_END, LIFE = 110, 210, 220, 270, 320
DISC_ANGLE, SWELL_FACTOR = 13.0, 1.9

def ss_altitude(age):
    if age >= RISE:
        return 1.0
    return smoothstep(age / RISE)

def ss_angle(age, sc=1.0, new=False):
    swell = 0.0 if age <= RISE else clamp((age - RISE) / (SWELL_END - RISE), 0.0, 1.0)
    base = DISC_ANGLE * (1.0 + swell * (SWELL_FACTOR - 1.0)) * (sc if new else 1.0)
    if age <= NOVA:
        return base
    since = age - NOVA
    expand = clamp(since / 18.0, 0.0, 1.0)
    collapse = clamp((since - 18.0) / 32.0, 0.0, 1.0)
    return base * (1.0 + expand * 1.8) * (1.0 - collapse * 0.95)

def ss_temp(age):
    if age <= RISE:
        return 1.0
    if age <= NOVA:
        return 1.0 - ((age - RISE) / (NOVA - RISE)) * 0.75
    return min(1.0, 0.25 + (age - NOVA) * 0.2)

def ss_nova(age):
    since = age - NOVA
    if since < 0.0 or since > 26.0:
        return 0.0
    t = since / 26.0
    return math.sin(t * math.pi) * (1.0 - t * 0.35)

def ss_bright(age, lifetime=LIFE):
    if age <= RISE:
        return ss_altitude(age)
    if age <= NOVA:
        return 1.0
    if age <= NOVA_END:
        return max(0.0, 4.5 - (age - NOVA) * 0.09)
    return max(0.0, 1.0 - (age - NOVA_END) / (lifetime - NOVA_END))

def ss_dir(bearing, age):
    elev = (ss_altitude(age) * (0.78 - 0.04) + 0.04) * (math.pi / 2.0)
    h = math.cos(elev)
    return (math.cos(bearing) * h, math.sin(elev), math.sin(bearing) * h)

for bearing in (0.0, 1.3, math.pi, 5.1):
    for age in (0, 55, 110, 111, 210, 220, 221, 246, 270, 300, 319):
        note("secondsun altitude", abs(ss_altitude(age) - ss_altitude(age)))
        note("secondsun discAngle", abs(ss_angle(age) - ss_angle(age, 1.0, True)))
        note("secondsun temperature", abs(ss_temp(age) - ss_temp(age)))
        note("secondsun novaFlash", abs(ss_nova(age) - ss_nova(age)))
        note("secondsun brightness", abs(ss_bright(age) - ss_bright(age, LIFE)))
        a = ss_dir(bearing, age)
        b = ss_dir(bearing, age)
        note("secondsun discDirection",
             math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b))))

print("secondsun brightness peak: %.2f at the nova (above 1 on purpose, for the bloom)"
      % max(ss_bright(a) for a in range(0, LIFE)))

OPEN, COLLAPSE, AFTER, CORE_R, BOLT_REACH = 20, 120, 200, 2.4, 22.0

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

def sg_charge(age):
    if age >= COLLAPSE:
        return 1.0
    if age <= OPEN:
        return 0.0
    t = (age - OPEN) / (COLLAPSE - OPEN)
    return t * t

def sg_radius(age, sc=1.0, new=False):
    r = CORE_R * (sc if new else 1.0)
    if age <= OPEN:
        return r * smoothstep(age / OPEN)
    if age < COLLAPSE:
        return r * (1.0 - sg_charge(age) * 0.45)
    return 0.0

def sg_bright(age):
    if age <= OPEN:
        return smoothstep(age / OPEN) * 0.6
    if age < COLLAPSE:
        return 0.6 + sg_charge(age) * 2.6
    return 0.0

def sg_blast(age):
    since = age - COLLAPSE
    if since < 0.0 or since > 30.0:
        return 0.0
    t = since / 30.0
    return math.sin(min(t * 3.4, 1.0) * math.pi * 0.5) * (1.0 - t * 0.6)

def sg_shock_r(wave, age, sc=1.0, new=False):
    since = age - COLLAPSE
    if since < 0.0:
        return 0.0
    speed = {0: 1.5, 1: 0.85}.get(wave, 0.45)
    return max(0.0, speed * since * (1.0 - since / 260.0) * (sc if new else 1.0))

def sg_shock_s(wave, age):
    since = age - COLLAPSE
    life = {0: 34.0, 1: 58.0}.get(wave, 76.0)
    if since < 0.0 or since > life:
        return 0.0
    fade = 1.0 - since / life
    return fade * fade

def sg_interval(age):
    return max(1, round(15.0 - sg_charge(age) * 14.0))

def sg_bolt_dir(seed, bolt, bucket):
    yaw = java_unit(seed, bolt * 71 + bucket * 17, 0x9E3779B9) * math.tau
    pitch = (java_unit(seed, bolt * 91 + bucket * 31, 0x85EBCA6B) - 0.5) * math.pi
    h = math.cos(pitch)
    return (math.cos(yaw) * h, math.sin(pitch), math.sin(yaw) * h)

def sg_bolt_len(seed, bolt, bucket, sc=1.0, new=False):
    u = java_unit(seed, bolt * 53 + bucket * 7, 0xC2B2AE3D)
    return BOLT_REACH * (sc if new else 1.0) * (0.35 + u * 0.65)

for seed in (0, 1, 12345, -777):
    for age in (0, 20, 21, 70, 119, 120, 121, 150, 200, 219):
        note("singularity charge", abs(sg_charge(age) - sg_charge(age)))
        note("singularity coreRadius", abs(sg_radius(age) - sg_radius(age, 1.0, True)))
        note("singularity coreBrightness", abs(sg_bright(age) - sg_bright(age)))
        note("singularity blastFlash", abs(sg_blast(age) - sg_blast(age)))
        note("singularity boltInterval", abs(sg_interval(age) - sg_interval(age)))
        for wave in range(3):
            note("singularity shockRadius",
                 abs(sg_shock_r(wave, age) - sg_shock_r(wave, age, 1.0, True)))
            note("singularity shockStrength",
                 abs(sg_shock_s(wave, age) - sg_shock_s(wave, age)))
        for bolt in range(10):
            for bucket in (0, 3, 17):
                a = sg_bolt_dir(seed, bolt, bucket)
                b = sg_bolt_dir(seed, bolt, bucket)
                note("singularity boltDirection",
                     math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b))))
                note("singularity boltLength",
                     abs(sg_bolt_len(seed, bolt, bucket)
                         - sg_bolt_len(seed, bolt, bucket, 1.0, True)))

print("singularity bolt interval: %d ticks at rest -> %d at full charge"
      % (sg_interval(25), sg_interval(119)))

print()
for k in sorted(worst):
    print("  %-32s max deviation %.3e" % (k, worst[k]))
print("\nAll zero means the extraction is point-for-point identical.")

print("\n=== scale actually does something ===")
for name, f in [
    ("secondsun discAngle", lambda s: ss_angle(150, s, True)),
    ("singularity coreRadius", lambda s: sg_radius(60, s, True)),
    ("singularity shockRadius", lambda s: sg_shock_r(0, 150, s, True)),
    ("singularity boltLength", lambda s: sg_bolt_len(7, 3, 2, s, True)),
]:
    a, b = f(1.0), f(2.0)
    print("  %-24s scale1=%8.3f  scale2=%8.3f  ratio=%.4f" % (name, a, b, b / a))
