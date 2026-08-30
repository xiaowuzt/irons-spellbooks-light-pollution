"""Pre-change formulas taken from the entity sources at git HEAD, post-change from fx/*Shape.
Both sides reimplemented independently, then compared point by point. Reading the code to judge
"behaviour unchanged" is not reliable for this kind of refactor."""
import math

def clamp(v, lo, hi): return max(lo, min(hi, v))

def smoothstep(t):
    c = clamp(t, 0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)

def norm(v):
    l = math.sqrt(sum(c * c for c in v))
    return tuple(c / l for c in v)

def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])

def scale(v, s): return tuple(c * s for c in v)
def add(*vs): return tuple(sum(c) for c in zip(*vs))
def lensqr(v): return sum(c * c for c in v)
def dist(a, b): return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))

worst = {}
def note(key, d): worst[key] = max(worst.get(key, 0.0), d)

M_LINES, M_REACH, M_STAR_R, M_TWIST = 14, 19.0, 2.1, 0.85
M_THREAD, M_WIND = 36, 230

def mag_axis(az_deg):
    az = math.radians(az_deg)
    tilt = math.radians(28.0)
    return norm((math.sin(tilt)*math.cos(az), math.cos(tilt), math.sin(tilt)*math.sin(az)))

def mag_field(az_deg, centre, line, along, wound, sc=1.0, new=False):
    axis = mag_axis(az_deg)
    side = cross(axis, (0.0, 1.0, 0.0))
    if lensqr(side) < 1e-6:
        side = cross(axis, (1.0, 0.0, 0.0))
    side = norm(side)
    other = norm(cross(axis, side))
    theta = along * math.pi
    s, c = math.sin(theta), math.cos(theta)
    shell = M_REACH * (0.45 + 0.55 * ((line % 3) / 2.0)) * (sc if new else 1.0)
    rho = shell * s * s * s
    z = shell * s * s * c + M_STAR_R * (sc if new else 1.0) * c
    azim = 2.0*math.pi*line/M_LINES + wound*M_TWIST*math.pi*2.0*s*s
    radial = add(scale(side, math.cos(azim)), scale(other, math.sin(azim)))
    return add(centre, scale(axis, z), scale(radial, rho))

def mag_wound(age):
    if age <= M_THREAD:
        return 0.0
    return clamp((age - M_THREAD) / (M_WIND - M_THREAD), 0.0, 1.0)

for az in (0.0, 47.0, 180.0, 313.0):
    for age in (0, 36, 37, 120, 230, 240, 256, 299):
        w = mag_wound(age)
        for line in range(M_LINES):
            for along in (0.0, 0.13, 0.5, 0.87, 1.0):
                a = mag_field(az, (5., 70., -3.), line, along, w, new=False)
                b = mag_field(az, (5., 70., -3.), line, along, w, 1.0, new=True)
                note("magnetar fieldPoint", dist(a, b))

gaps = [dist(mag_field(90.0, (0.,0.,0.), ln, 0.0, 0.5, 1.0, True),
             mag_field(90.0, (0.,0.,0.), ln, 1.0, 0.5, 1.0, True)) for ln in range(M_LINES)]
print("magnetar pole separation: %.3f .. %.3f blocks (expect about 2*STAR_RADIUS = %.1f)"
      % (min(gaps), max(gaps), 2 * M_STAR_R))

Q_SPINUP, Q_HOLD, Q_CONE, Q_TILT, Q_LEN, Q_BULLETS, Q_PREC = 40, 260, 20.0, 38.0, 54.0, 30, 1.5

def mq_bpt():
    return Q_LEN / (((Q_HOLD - Q_SPINUP) / Q_PREC) * 0.2)

def mq_phase(age):
    lit = max(1.0, Q_HOLD - Q_SPINUP)
    return ((age - Q_SPINUP) / lit) * Q_PREC * math.pi * 2.0

def mq_axis(az_deg):
    az = math.radians(az_deg)
    tilt = math.radians(Q_TILT)
    return norm((math.sin(tilt)*math.cos(az), math.cos(tilt), math.sin(tilt)*math.sin(az)))

def mq_helix(az_deg, centre, age, forward, frac, sc=1.0, new=False):
    travel = Q_LEN * (sc if new else 1.0) * frac
    lp = mq_phase(age - travel / (mq_bpt() * (sc if new else 1.0)))
    axis = mq_axis(az_deg)
    side = cross(axis, (0.0, 1.0, 0.0))
    if lensqr(side) < 1e-6:
        side = cross(axis, (1.0, 0.0, 0.0))
    side = norm(side)
    other = norm(cross(axis, side))
    cone = math.radians(Q_CONE)
    d = norm(add(scale(axis, math.cos(cone)),
                 scale(side, math.sin(cone)*math.cos(lp)),
                 scale(other, math.sin(cone)*math.sin(lp))))
    if not forward:
        d = scale(d, -1.0)
    return add(centre, scale(d, travel))

for az in (0.0, 47.0, 180.0, 313.0):
    for age in (0, 40, 41, 150, 260, 300, 319):
        for fwd in (True, False):
            for i in range(0, Q_BULLETS + 1, 3):
                frac = i / Q_BULLETS
                a = mq_helix(az, (0., 64., 0.), age, fwd, frac, new=False)
                b = mq_helix(az, (0., 64., 0.), age, fwd, frac, 1.0, new=True)
                note("microquasar helixPoint", dist(a, b))

P_SPINUP, P_SPINEND, P_ARMS, P_REACH, P_TURNS, P_ROT, P_HALFW, P_TILT = \
    34, 250, 2, 26.0, 2.2, 1.4, 1.1, 18.0

def pw_normal(az_deg):
    az = math.radians(az_deg)
    tilt = math.radians(P_TILT)
    return norm((math.sin(tilt)*math.cos(az), math.cos(tilt), math.sin(tilt)*math.sin(az)))

def pw_rot(age):
    lit = max(1.0, P_SPINEND - P_SPINUP)
    return ((age - P_SPINUP) / lit) * P_ROT * math.pi * 2.0

def pw_arm(az_deg, centre, arm, frac, rot, sc=1.0, new=False):
    n = pw_normal(az_deg)
    u = cross(n, (0.0, 1.0, 0.0))
    if lensqr(u) < 1e-6:
        u = cross(n, (1.0, 0.0, 0.0))
    u = norm(u)
    v = norm(cross(n, u))
    ang = frac*P_TURNS*math.pi*2.0 + arm*(math.pi*2.0/P_ARMS) + rot
    r = P_REACH * (sc if new else 1.0) * frac
    return add(centre, scale(u, math.cos(ang)*r), scale(v, math.sin(ang)*r))

def pw_width(frac, sc=1.0, new=False):
    return P_HALFW * (sc if new else 1.0) * (0.45 + 0.75 * frac)

for az in (0.0, 47.0, 180.0, 313.0):
    for age in (0, 34, 35, 140, 250, 260, 299):
        rot = pw_rot(age)
        for arm in range(P_ARMS):
            for i in range(41):
                frac = i / 40.0
                note("pinwheel armPoint",
                     dist(pw_arm(az, (0.,64.,0.), arm, frac, rot, new=False),
                          pw_arm(az, (0.,64.,0.), arm, frac, rot, 1.0, new=True)))
                note("pinwheel armWidth", abs(pw_width(frac) - pw_width(frac, 1.0, True)))

J_LAUNCH, J_GAMMA, J_VIEW, J_LEN, J_KNOTS = 34, 10.0, 17.0, 72.0, 5
J_BETA = math.sqrt(1.0 - 1.0 / (J_GAMMA * J_GAMMA))

def qj_apparent():
    th = math.radians(J_VIEW)
    return J_BETA * math.sin(th) / (1.0 - J_BETA * math.cos(th))

def qj_bpt():
    return J_LEN / 44.0 * (qj_apparent() / 6.0)

def qj_dir(az_deg):
    az = math.radians(az_deg)
    el = math.radians(16.0)
    return norm((math.cos(el)*math.cos(az), math.sin(el), math.cos(el)*math.sin(az)))

def qj_progress(knot, age):
    crossing = J_LEN / max(qj_bpt(), 0.001)
    launched = age - knot * (crossing / J_KNOTS)
    if launched <= 0.0:
        return -1.0
    return (launched % crossing) / crossing

def qj_knot(az_deg, centre, knot, age, sc=1.0, new=False):
    pr = qj_progress(knot, age)
    if pr < 0.0:
        return None
    return add(centre, scale(qj_dir(az_deg), J_LEN * (sc if new else 1.0) * pr))

print("quasar jet apparent speed: %.3fc (above 1 is expected -- light travel time)"
      % qj_apparent())

for az in (0.0, 47.0, 180.0, 313.0):
    for age in (0, 34, 35, 100, 280, 300, 339):
        for knot in range(J_KNOTS):
            a = qj_knot(az, (0., 64., 0.), knot, age, new=False)
            b = qj_knot(az, (0., 64., 0.), knot, age, 1.0, new=True)
            assert (a is None) == (b is None), "knot existence disagrees"
            if a is not None:
                note("quasarjet knotPosition", dist(a, b))

print()
for k in sorted(worst):
    print("  %-34s max deviation %.3e" % (k, worst[k]))
print("\nAll zero means the extraction is point-for-point identical.")

print("\n=== scale actually does something (scale=2 should double distances) ===")
for name, f in [
    ("magnetar fieldPoint", lambda s: mag_field(47.0, (0.,0.,0.), 3, 0.4, 0.5, s, True)),
    ("microquasar helix", lambda s: mq_helix(47.0, (0.,0.,0.), 150, True, 0.7, s, True)),
    ("pinwheel armPoint", lambda s: pw_arm(47.0, (0.,0.,0.), 1, 0.8, pw_rot(150), s, True)),
    ("quasarjet knot", lambda s: qj_knot(47.0, (0.,0.,0.), 2, 200, s, True)),
]:
    d1 = math.sqrt(lensqr(f(1.0)))
    d2 = math.sqrt(lensqr(f(2.0)))
    print("  %-22s scale1=%8.3f  scale2=%8.3f  ratio=%.4f" % (name, d1, d2, d2 / d1))
