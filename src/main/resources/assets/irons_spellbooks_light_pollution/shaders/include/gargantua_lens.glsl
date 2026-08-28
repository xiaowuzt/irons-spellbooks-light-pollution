// Gargantua: a black hole whose accretion disk is lensed by its own gravity.
//
// Included by core/gemini_kill_post_gargantua_lens.fsh after
// kill_effect_post_common.glsl, which supplies vUv, fragColor, SceneSampler and
// SL_SKY_DEPTH.
//
// Two things are going on here and they come from different places.
//
// The light bending is integrated as a real geodesic, because the signature image is
// the disk's FAR side bent up over the shadow and down under it, and that is multiple
// imaging rather than distortion. A closed-form deflection cannot produce it -- the
// weak-field alpha = 4M/b diverges logarithmically as b approaches the photon sphere,
// which is exactly where the higher-order images live. Stepping the photon equation
//
//     d2u/dphi2 + u = 3M u^2        (u = 1/r)
//
// in Cartesian leapfrog form gives those crossings for free: a ray passing close
// enough cuts the disk twice or more, and each crossing is one image. Radii follow the
// published Interstellar imagery (James, von Tunzelmann, Franklin & Thorne, Class.
// Quantum Grav. 32, 065001, 2015): the horizon sits at 1.8 r_g for spin a/M = 0.6, the
// shadow an observer sees is sqrt(27) = 5.196 r_g -- 2.6 times the horizon, because a
// black hole always looks bigger than it is -- and the disk starts at the innermost
// stable circular orbit, 3.83 r_g.
//
// The disk itself is volumetric, and that part follows ArcaneVortex's approach, used
// with its author's permission (see CREDITS.txt). The detail worth stealing is that
// noise modulates the disk's local THICKNESS rather than its brightness. Brightness
// noise gives a hard disk with blotches on it; thickness noise gives torn, wispy
// edges, which is what makes it read as gas. An earlier version of this file tested
// for plane crossings instead, which is more correct for an infinitely thin disk and
// looked like a painted ring.

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform mat4 InverseProjectionMat;
uniform mat4 ProjectionMat;
// xyz = hole centre in OpenGL eye space (-Z forward), w = r_g in blocks
uniform vec4 HoleCentre;
// xyz = spin axis in eye space, unit length; w = spin a/M
uniform vec4 SpinAxis;
// x = opened 0..1, y = criticality 0..1, z = wrapped seconds, w = swallowed lights
uniform vec4 HoleState;
// x = disk inner radius, y = outer radius, both in r_g; z = h/r; w = brightness
uniform vec4 DiskShape;

const int GARG_STEPS_MAX = 96;
const float GARG_HORIZON = 1.8;
const float GARG_SHADOW = 5.196;
const float GARG_ESCAPE = 60.0;
const float GARG_TAU = 6.2831853;
// Half-thickness at the inner and outer edge, in r_g. The real disk is nearer
// h/r = 1e-3 -- Thorne calls it a sheet of paper -- but at that scale it is thinner
// than a pixel and vanishes, so this is thickened to roughly 0.03 of the radius.
const float GARG_THICK_INNER = 0.46;
const float GARG_THICK_OUTER = 0.26;

/**
 * Disk colour by radius: pale white-yellow at the inner edge grading to amber and
 * then orange, with no blue anywhere.
 *
 * That absence is the physics, not a style choice. Gargantua has not been fed in
 * millions of years, so its accretion rate is tiny and the disk sits around 4500 K --
 * about the Sun's photosphere. A normally-fed disk would be blue-white, would emit
 * X-rays, and would have sterilised every planet near it.
 */
vec3 gargDiskColour(float t) {
    vec3 c0 = vec3(1.000, 0.973, 0.902);
    vec3 c1 = vec3(1.000, 0.839, 0.588);
    vec3 c2 = vec3(0.988, 0.667, 0.337);
    vec3 c3 = vec3(0.839, 0.424, 0.149);
    float k = clamp(t, 0.0, 1.0);
    if (k < 0.34) {
        return mix(c0, c1, k / 0.34);
    }
    if (k < 0.67) {
        return mix(c1, c2, (k - 0.34) / 0.33);
    }
    return mix(c2, c3, (k - 0.67) / 0.33);
}

float gargWhiteNoise(vec2 uv) {
    return fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453123);
}

/**
 * Entry and exit distance along a ray for a sphere at the origin, or (-1,-1) on a
 * miss. Entry is clamped to zero so a camera inside the sphere starts where it is.
 *
 * From ArcaneVortex: confining the march to a bounding sphere is the single biggest
 * saving in the whole pass. Marching from the camera means most steps are spent
 * crossing empty space before the ray is anywhere near the hole, which is why the
 * step budget had to be spread thin. Starting at the sphere puts every step where
 * something can actually happen.
 */
vec2 gargSphereHit(vec3 origin, vec3 dir, float radius) {
    float b = dot(origin, dir);
    float c = dot(origin, origin) - radius * radius;
    float disc = b * b - c;
    if (disc < 0.0) {
        return vec2(-1.0);
    }
    float root = sqrt(disc);
    return vec2(max(-b - root, 0.0), -b + root);
}


void main() {
    float rg = max(HoleCentre.w, 0.0001);
    vec3 sceneColour = texture(SceneSampler, vUv).rgb;

    vec4 clip = vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 0.000001) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }
    vec3 rayDir = normalize(eye.xyz / eye.w);

    vec3 centre = HoleCentre.xyz;
    float along = dot(centre, rayDir);
    float impact = length(centre - rayDir * along);
    float holeDistance = length(centre);
    float reach = rg * DiskShape.y * 1.35;

    // Everything below is in units of r_g, in the hole's own frame.
    vec3 axis = normalize(SpinAxis.xyz);
    vec3 p = -centre / rg;
    vec3 d = rayDir;
    float h = length(cross(p, d));

    // The bounding sphere is the only gate, and it has to be the only gate.
    //
    // There were two more in front of it -- one rejecting rays whose dot product with
    // the hole direction was negative, one rejecting rays whose perpendicular distance
    // to the hole's infinite line exceeded the reach. Both are correct only while the
    // camera is outside the sphere. Walk inside it and they start throwing away rays
    // that begin within the volume and legitimately pass through it: the first cuts
    // along the plane through the camera perpendicular to the hole, which projects to a
    // perfectly straight line on screen, and the effect appeared sliced in half by it.
    //
    // gargSphereHit already clamps its entry distance to zero, so a camera inside the
    // sphere simply starts marching from where it stands.
    vec2 span = gargSphereHit(p, d, DiskShape.y * 1.35);
    if (span.y < 0.0) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    // How far along this ray the solid world is, in blocks. Sky reads as infinity.
    // Needed per sample rather than once for the whole hole: standing on the ground,
    // terrain cuts across the disk, and a single whole-object test cannot tell an
    // occluded sample from a visible one.
    float sceneDistance = 1.0e9;
    float depth = texture(DepthSampler, vUv).r;
    if (depth < SL_SKY_DEPTH) {
        vec4 sceneClip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 sceneEye = InverseProjectionMat * sceneClip;
        if (abs(sceneEye.w) > 0.000001) {
            sceneDistance = length(sceneEye.xyz / sceneEye.w);
        }
    }

    p += d * span.x;
    float travelledBlocks = span.x * rg;
    if (travelledBlocks > sceneDistance) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    // A stable pair of axes spanning the disk plane, for the noise lookup.
    vec3 diskU = normalize(cross(axis, vec3(0.0, 0.0, 1.0)) + vec3(1.0e-4));
    vec3 diskV = cross(axis, diskU);

    // Jitter the start along the ray so the fixed march does not band. A tenth of a
    // step is enough to turn contouring into noise the eye reads as grain.
    p += d * (gargWhiteNoise(vUv * 512.0) * 0.1);

    int steps = int(mix(48.0, float(GARG_STEPS_MAX),
            clamp(reach / max(holeDistance, 0.001) * 1.6, 0.0, 1.0)));

    vec3 accum = vec3(0.0);
    float alpha = 0.0;
    bool captured = false;
    float time = HoleState.z;
    float inner = DiskShape.x;
    float outer = DiskShape.y;

    for (int i = 0; i < GARG_STEPS_MAX; ++i) {
        if (i >= steps) {
            break;
        }
        float radius = length(p);
        float height = dot(p, axis);
        // Step size has to shrink near the disk plane as well as near the hole. A
        // step scaled only by radius reaches 1.4 out at the disk's rim, which strides
        // straight over a body a third of a unit thick and leaves it in dashes.
        //
        // The radius term in the floor is not decoration. Without it, a viewer whose
        // eye lands in the disk's own plane sees height stay near zero along the whole
        // ray, the step pins at its minimum, and ninety-six steps cover four units of
        // a fifteen-unit journey -- the march never reaches the hole at all.
        float nearPlane = max(abs(height) * 0.55, radius * 0.02);
        float dt = clamp(min(radius * 0.13, nearPlane), 0.03, 1.4);

        p += d * dt;
        travelledBlocks += dt * rg;
        float travelled = length(p);
        // The photon equation in leapfrog form. Renormalising d keeps the affine
        // parameter consistent, which the first-order update does not preserve.
        d = normalize(d + (-1.5 * h * h * p / pow(travelled, 5.0)) * dt);

        if (travelled < GARG_HORIZON) {
            captured = true;
            break;
        }
        if (travelledBlocks > sceneDistance || travelled > GARG_ESCAPE) {
            break;
        }

        height = dot(p, axis);
        vec3 planar = p - axis * height;
        float radial = length(planar);
        if (radial < inner || radial > outer) {
            continue;
        }

        // Noise UV, following how ArcaneVortex does it, because the physically correct
        // version does not read as motion at all.
        //
        // The phase splits into two parts. Radius contributes a STATIC twist, which
        // makes the pattern a spiral; time contributes a UNIFORM rotation, the same rate
        // at every radius. A spiral turning rigidly is what the eye reads as a disk
        // rotating, because it tracks the arms sweeping past.
        //
        // Three earlier attempts used the real orbital law instead, omega proportional
        // to r^-1.5, and all three looked frozen. Two reasons. At the inner edge that
        // law gives 166 degrees a second, so consecutive frames of a fine noise pattern
        // are essentially uncorrelated -- it becomes temporal noise, which reads as
        // static grain rather than movement -- while the part slow enough to follow is
        // the dim outer rim. And with time running to 600 seconds the phase reached
        // 1740 radians, where sin and cos lose most of their precision on some drivers.
        // Wrapping to a turn keeps the trig honest.
        vec2 flat2 = vec2(dot(planar, diskU), dot(planar, diskV));
        // Static twist, which is what makes the pattern a spiral, and a separate
        // uniform rotation in time.
        //
        // The coefficient is the whole ballgame and it has to be expressed per disk
        // WIDTH, not copied as a raw number. ArcaneVortex uses 4.27 on a disk spanning
        // 0.195 to 1.5, so its spiral makes about 0.89 of a turn from inner edge to
        // outer. Reusing 4.27 here, where the disk spans 3.83 to 13.5 gravitational
        // radii, wound the same spiral 6.6 times instead — and a tightly wound spiral
        // rotating rigidly does not look like rotation at all, it looks like radial
        // drift, because turning a fine spiral through an angle is geometrically almost
        // the same as sliding it outward. That is exactly what it looked like: gas
        // spreading outward, no spin. 0.58 reproduces the same 0.89 of a turn across
        // this disk's actual width.
        float twist = radial * 0.58;
        // Not wrapped here. mod(x, TAU) * 1.35 is not congruent to x * 1.35 mod TAU, so
        // wrapping before the octave factor below made layers 1 and 2 snap from
        // 1.35*TAU back to 0 every 11.4 seconds -- a visible flick, not rotation. The
        // wrap happens once, after the multiply. Age runs to about 20 seconds, so the
        // phase stays under 19 radians and the trig keeps its precision anyway.
        float spin = time * 0.55;

        // Three layers at different scales, each turning slightly faster than the last
        // so they shear against one another. A single layer only ever translates, so
        // however fast it moves the pattern itself never changes.
        float noise = 0.0;
        float weight = 0.0;
        for (int octave = 0; octave < 3; ++octave) {
            float scale = 0.17 * pow(2.1, float(octave));
            // The octave factor multiplies only the time term. Applying it to the whole
            // phase, twist included, made each successive layer's spiral tighter — and
            // tighter spirals read as radial drift, so the layers that were supposed to
            // add turbulence were pulling the eye the wrong way instead.
            float drift = mod(twist + spin * (1.0 + float(octave) * 0.35), GARG_TAU);
            float cs = cos(drift);
            float sn = sin(drift);
            vec2 uv = vec2(flat2.x * cs - flat2.y * sn,
                           flat2.x * sn + flat2.y * cs) * scale;
            float layer = 1.0 / pow(2.0, float(octave));
            noise += layer * dot(texture(NoiseSampler, uv).rgb,
                    vec3(0.299, 0.587, 0.114));
            weight += layer;
        }
        noise /= max(weight, 0.001);

        float t = (radial - inner) / max(outer - inner, 0.001);
        // Noise drives thickness, which is what gives torn wispy edges rather than a
        // hard rim. Low noise thins the disk rather than discarding the sample: an
        // earlier version skipped samples below a cutoff, so when the noise texture
        // failed to bind every sample was discarded and the disk rendered as literally
        // nothing — a silent, total failure.
        float thickness = mix(GARG_THICK_INNER, GARG_THICK_OUTER, t)
                * mix(0.10, 1.35, noise);
        float coverage = smoothstep(thickness, 0.0, abs(height));
        if (coverage <= 0.002) {
            continue;
        }

        // Emission falls steeply outward, as a thin disk's temperature does, with
        // both edges eased so the disk does not terminate on a hard line.
        float profile = pow(1.0 - t, 1.6)
                * smoothstep(0.0, 0.08, t) * smoothstep(1.0, 0.90, t);
        // Every spell light it has eaten feeds the disk. This is the visual payoff for
        // the swallowing mechanic: a hole that has drained a crowded battlefield burns
        // visibly hotter than one opened over empty ground, and the detonation that
        // follows is stronger by the same count.
        float fed = 1.0 + min(HoleState.w, 6.0) * 0.16;
        // Noise modulates brightness as well as thickness. Thickness alone is enough
        // when the disk is seen face-on, because the eye is looking through it — but
        // near edge-on a ray crosses so much material that thickness variation
        // integrates away to a smooth average, and the disk goes featureless. Without
        // a brightness term there is nothing left to see moving.
        float grain = 0.35 + 0.65 * noise;
        vec3 emission = gargDiskColour(t) * profile * max(DiskShape.w, 0.0)
                * 9.0 * fed * grain;

        // Front to back, so the near side of the disk correctly hides the far side.
        //
        // Deliberately small. At the previous 2.2 a single sample clamped to fully
        // opaque, so the ray stopped at the first thing it touched and the final colour
        // came from exactly one sample: every bit of noise, shear and inflow was
        // discarded before it could accumulate, and the disk rendered as a smooth
        // saturated bar with no texture and therefore no visible motion. Building up
        // over a dozen or more samples is what makes the structure appear at all.
        float local = clamp(coverage * profile * 0.22, 0.0, 1.0);
        accum += emission * (1.0 - alpha) * local;
        alpha += (1.0 - alpha) * local;
        if (alpha > 0.99) {
            break;
        }
    }

    // The background, read along the ray's new direction. This is the half of the
    // effect ArcaneVortex leaves out, and it is what bends terrain, sky and other
    // spells' light around the hole.
    //
    // A ray deflected off the edge of the screen has no data to read -- that is the
    // unavoidable limit of doing this in screen space. Falling back to a flat fraction
    // of the undeflected colour, as an earlier version did, painted a hard-edged grey
    // dome above the shadow. Clamping to the screen edge and then fading back toward
    // the undeflected colour by how far off it went keeps it continuous: rays that
    // just miss read the edge pixel, rays that miss badly quietly stop contributing.
    vec3 background = vec3(0.0);
    if (!captured) {
        // How much this ray actually got bent. Rays that barely deflected have to come
        // out exactly equal to the untouched scene, and that is not a nicety: the pass
        // bails early where terrain sits in front of the bounding sphere, so the
        // boundary between "bailed" and "marched" falls along the sphere's intersection
        // with the ground. If a marched ray with negligible deflection returned even a
        // slightly shifted sample, that intersection showed up as a hard curved seam
        // drawn across the floor. Keying the blend on real deflection makes both sides
        // of the seam identical by construction.
        float bent = 1.0 - dot(d, rayDir);
        float lensed = clamp(bent * 20.0, 0.0, 1.0);
        background = sceneColour;
        if (lensed > 0.001) {
            vec4 outClip = ProjectionMat * vec4(d * max(holeDistance, 32.0), 1.0);
            if (outClip.w > 0.0001) {
                vec2 outUv = (outClip.xy / outClip.w) * 0.5 + 0.5;
                vec2 inside = clamp(outUv, 0.0, 1.0);
                // A ray deflected off the edge of the screen has no data to read --
                // the unavoidable limit of doing this in screen space. Clamp to the
                // edge, then fade back toward the undeflected colour by how far off it
                // went, so it degrades quietly instead of painting a grey dome.
                float strayed = length(outUv - inside);
                vec3 sampled = mix(texture(SceneSampler, inside).rgb, sceneColour,
                        clamp(strayed * 7.0, 0.0, 1.0));
                background = mix(sceneColour, sampled, lensed);
            }
        }
    }

    // The photon ring: a stack of images on the critical curve, within a percent of
    // the shadow's edge. Two orders are enough -- each successive one is demagnified
    // by exp(-2*pi), roughly 1/535, so the third is already invisible.
    //
    // Faint, warm, and scaled by the disk light this ray actually gathered. The ring
    // is not a light source: it is the disk's own light wrapped most of the way around
    // the hole, so it cannot be brighter than the disk or a different colour from it.
    // Drawn white at full strength it became a hard bright circle that cut the shadow
    // in half -- a line drawn over the physics rather than a result of it.
    float shadowEdge = rg * GARG_SHADOW;
    float first = exp(-pow((impact - shadowEdge) / max(rg * 0.17, 0.001), 2.0));
    float second = exp(-pow((impact - shadowEdge * 1.05) / max(rg * 0.11, 0.001), 2.0))
            / 535.0;
    // impact is the distance to the hole's infinite line, so it is only meaningful
    // ahead of the camera. Behind, it would place a ring where there is nothing.
    float ahead = step(0.0, along);
    float ringGain = 0.26 * ahead * max(DiskShape.w, 0.0) * (0.25 + 0.75 * alpha)
            * (1.0 + HoleState.y * 3.0);
    vec3 ring = gargDiskColour(0.0) * (first + second) * ringGain;

    vec3 result = accum + ring;
    if (!captured) {
        result += background * (1.0 - alpha);
    }
    // Criticality overexposes everything just before it lets go.
    result *= (1.0 + HoleState.y * 1.8) * clamp(HoleState.x, 0.0, 1.0);

    // No feather. The bounding sphere already provides one: at grazing incidence the
    // marched path through it approaches zero length, so the disk it can gather and the
    // deflection it can pick up both approach zero and the result converges on the
    // untouched scene by itself. The previous feather was keyed on the distance to the
    // hole's infinite line, which stops meaning anything once the camera is inside the
    // sphere -- there it faded the effect out from the wrong direction.
    fragColor = vec4(result, 1.0);
}

