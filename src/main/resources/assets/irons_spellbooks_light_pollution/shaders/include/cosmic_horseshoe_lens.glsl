// The Cosmic Horseshoe: a galaxy's light bent into an arc by the mass in front of it.
//
// Included by core/gemini_kill_post_cosmic_horseshoe.fsh after
// kill_effect_post_common.glsl, which supplies vUv, fragColor, SceneSampler and
// SL_SKY_DEPTH.
//
// The arc is not drawn. It is the image of a synthetic source galaxy, found by
// inverting the lens map at every pixel: given where a pixel looks, work out where in
// the source plane that light started, and sample the galaxy there. Everything that
// makes a horseshoe look like a horseshoe then arrives on its own -- the ring sits at
// the Einstein radius because that is where the map is singular; the arc is thin
// because lensing stretches tangentially and not radially; each star-forming knot
// appears more than once because the map is many-to-one; and the faint counter-image
// opposite the arc is the same map's second branch. None of those are painted in.
//
// The lens has to be elliptical, and that is the one place this departs from the
// obvious choice. A circular (singular isothermal sphere) lens is exactly symmetric:
// its caustic is a single point, an offset source produces two arcs of equal length on
// opposite sides, and no amount of tuning turns that into one long arc with one gap.
// Real horseshoes are lopsided because the lens galaxy itself is elliptical, which
// opens the caustic out into an astroid -- a source sitting near that caustic yields a
// long merged arc plus one stubby counter-image. For this system in particular the
// asymmetry cannot be blamed on the lens's neighbours: Dye et al. (2008) find the
// external shear is almost exactly zero, unique among large-separation lenses, so the
// ellipticity is the galaxy's own. Hence the elliptical isothermal potential below,
// which reduces to the circular case when q = 1.
//
// Measurements: Belokurov et al. (2007) report a ~300 degree ring 10 arcseconds
// across, so the arc leaves a gap of roughly 60 degrees -- that gap is the whole
// morphology and it is the acceptance test for the tuning constants. Jones et al.
// (2018) resolve the source into four star-forming regions of 4-8 kpc^2. The colours
// are not a choice: the source is a star-forming galaxy at redshift 2.379 whose
// rest-frame ultraviolet is redshifted into the blue, and the lens is a luminous red
// galaxy at redshift 0.444 with an old stellar population and no star formation.

uniform sampler2D DepthSampler;
uniform mat4 InverseProjectionMat;
uniform mat4 ProjectionMat;
// xyz = lens centre in OpenGL eye space (-Z forward), w = Einstein radius in blocks
uniform vec4 LensCentre;
// xyz = gap direction in eye space, unit length; w = potential axis ratio q
uniform vec4 GapDir;
// x = alignment 0..1, y = brightness, z = time in seconds, w = seed phase
uniform vec4 LensState;
// x = source radius, y = source offset, z = core radius, w = deflection reach
uniform vec4 SourceShape;

// Redshifted rest-frame ultraviolet from a galaxy at z = 2.379. Two tones, because a
// single colour scaled by brightness washes to white at the knots and the blue against
// red is the whole signature of the object.
#define HS_ARC_DEEP vec3(0.26, 0.47, 1.00)
#define HS_ARC_PALE vec3(0.84, 0.93, 1.00)
// An old, red, dead elliptical at z = 0.444.
#define HS_CORE_COLOUR vec3(1.00, 0.55, 0.26)
#define HS_CLUMPS 4
/**
 * Core radius of the lens, in units of the Einstein radius.
 *
 * A singular isothermal profile has an ill-defined deflection direction at the centre
 * and a magnitude that stays at a full Einstein radius all the way in, so it squeezes a
 * wide patch of screen into a vanishingly small patch of source -- which smeared the sky
 * into a dark ellipse around the core. Real galaxies are not singular, and the published
 * models of this system fit a power law rather than a true singularity, so softening the
 * profile removes the artefact and is the more faithful choice at the same time.
 */
#define HS_CORE_SOFT 0.13

float hsHash(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

/** Value noise on the source plane, for filaments within the star-forming regions. */
float hsNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = p - cell;
    f = f * f * (3.0 - 2.0 * f);
    float n = cell.x + cell.y * 57.0;
    return mix(mix(hsHash(n), hsHash(n + 1.0), f.x),
               mix(hsHash(n + 57.0), hsHash(n + 58.0), f.x), f.y);
}

/**
 * Deflection of a softened elliptical isothermal potential, in Einstein radii.
 *
 * The potential is psi = sqrt(q x^2 + y^2 / q + s^2) and this is its gradient. Away from
 * the centre the magnitude is one Einstein radius and only the direction varies with the
 * ellipticity; the s term takes it smoothly to zero at the centre instead of leaving it
 * undefined there. At q = 1 and s = 0 it collapses to the textbook singular isothermal
 * sphere.
 */
vec2 hsDeflect(vec2 t, float q) {
    float s = sqrt(q * t.x * t.x + t.y * t.y / q
            + HS_CORE_SOFT * HS_CORE_SOFT);
    return vec2(q * t.x, t.y / q) / s;
}

/**
 * Surface brightness of the source galaxy at a source-plane position.
 *
 * Four clumps over a smooth disk, because that is what the source actually is: Jones
 * et al. resolve four star-forming regions of 4-8 kpc^2 apiece. Regions that size are
 * 2-3 kpc across and a galaxy holding four of them is 10-15 kpc across, so each clump
 * runs about a fifth of the source's width -- which is where the 0.16-0.24 below comes
 * from rather than from taste. Their placement is hashed off the synced seed, so every
 * cast has its knots somewhere different and both sides agree on where.
 */
float hsSource(vec2 u, float radius, float phase) {
    float r = length(u) / max(radius, 1.0e-4);
    // The smooth disk the clumps sit in. Over half the light, and that matters more than
    // it sounds: at a third the four clumps dominated so completely that the arc rendered
    // as four separate beads rather than as a continuous glowing band with knots on it.
    // A lensed arc is a band first.
    float total = 0.55 * exp(-r * r * 1.5);

    for (int i = 0; i < HS_CLUMPS; ++i) {
        float a = phase + float(i) * 1.7;
        float ring = radius * (0.30 + 0.42 * hsHash(a + 3.1));
        float angle = 6.2831853 * hsHash(a + 7.7);
        vec2 at = vec2(cos(angle), sin(angle)) * ring;
        float width = radius * (0.16 + 0.08 * hsHash(a + 11.3));
        float d = length(u - at) / width;
        total += 0.95 * exp(-d * d);
    }

    // Filaments inside the star-forming regions. Applied as a modulation rather than
    // added, so it breaks the clumps up without inventing light outside them -- the
    // lens map stretches this along the arc, which is what turns smooth beads into the
    // shredded structure these arcs actually show.
    float grain = 0.72 + 0.56 * hsNoise(u / max(radius, 1.0e-4) * 2.7 + phase);
    return total * grain;
}

void main() {
    vec3 sceneColour = texture(SceneSampler, vUv).rgb;
    float bright = LensState.y;
    if (bright <= 0.001) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    vec4 clip = vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 0.000001) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }
    vec3 rayDir = normalize(eye.xyz / eye.w);

    vec3 centre = LensCentre.xyz;
    float thetaE = max(LensCentre.w, 0.001);
    float distance = length(centre);
    vec3 lensDir = centre / max(distance, 0.0001);

    // The construction below intersects each ray with the lens plane, so a ray pointing
    // away from the lens has nothing to intersect and is genuinely out of scope. This is
    // not the same as the bail that used to slice Gargantua in half: that one rejected
    // rays by their angle to the hole while the camera sat inside the effect volume,
    // whereas a lens plane really does only face one way.
    float facing = dot(rayDir, lensDir);
    if (facing <= 0.05) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    // Lensing is defined for an observer far enough away that the whole configuration
    // subtends a small angle. Walk into it and that stops being true, so rather than
    // let the geometry come apart it fades out over the last Einstein radius.
    float nearFade = smoothstep(thetaE * 0.6, thetaE * 1.6, distance);
    if (nearFade <= 0.001) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    // Where the solid world is along this ray, so terrain in front hides the effect.
    float sceneDistance = 1.0e9;
    float depth = texture(DepthSampler, vUv).r;
    if (depth < SL_SKY_DEPTH) {
        vec4 sceneClip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 sceneEye = InverseProjectionMat * sceneClip;
        if (abs(sceneEye.w) > 0.000001) {
            sceneDistance = length(sceneEye.xyz / sceneEye.w);
        }
    }
    float visible = smoothstep(distance - 2.0, distance + 2.0, sceneDistance);
    if (visible <= 0.001) {
        fragColor = vec4(sceneColour, 1.0);
        return;
    }

    // Where this ray crosses the plane through the lens, and a 2D frame in that plane
    // with x along the gap direction. Taking the frame from a world-space vector is what
    // makes the opening swing round as the player circles the lens instead of being
    // pinned to the screen.
    vec3 offset = rayDir * (distance / facing) - centre;
    vec3 gap = GapDir.xyz - lensDir * dot(GapDir.xyz, lensDir);
    if (dot(gap, gap) < 1.0e-6) {
        gap = abs(lensDir.y) < 0.9 ? cross(lensDir, vec3(0.0, 1.0, 0.0))
                                   : vec3(1.0, 0.0, 0.0);
    }
    vec3 axisU = normalize(gap);
    vec3 axisV = cross(lensDir, axisU);
    vec2 theta = vec2(dot(offset, axisU), dot(offset, axisV)) / thetaE;

    // Invert the lens map. An isothermal deflection has the same magnitude at every
    // radius -- that is the same flat-rotation-curve property that makes these profiles
    // fit real galaxies -- so left alone it would bend the entire screen no matter how
    // far from the lens. Real haloes end somewhere, so it is tapered off.
    float reach = max(SourceShape.w, 1.2);
    float taper = 1.0 - smoothstep(reach * 0.55, reach, length(theta));
    float q = clamp(GapDir.w, 0.3, 1.0);
    vec2 deflect = hsDeflect(theta, q) * taper;
    vec2 source = theta - deflect;

    // How hard this ray is magnified. The tangential magnification of an isothermal
    // profile goes as r/(r-1), so it diverges on the critical curve at r = 1 -- and that
    // divergence is the whole spell: it is why the ring is where the light piles up and
    // therefore why the ring is the part that burns. The damage already used it; the
    // brightness did not, which is why the arc looked flat and dull rather than blazing
    // along the ring and dying away off it. The floor keeps it finite on the curve itself.
    // Softened the same way hsDeflect is, so the magnification and the deflection are
    // derived from one profile rather than two that disagree near the centre.
    float rEll = sqrt(q * theta.x * theta.x + theta.y * theta.y / q
            + HS_CORE_SOFT * HS_CORE_SOFT);
    float mu = rEll / max(abs(rEll - 1.0), 0.05);
    float gain = clamp(sqrt(mu) * 0.55, 0.35, 3.2);

    // The arc. The alignment closing is what brings the source onto the axis, so the
    // offset starts large and settles -- the arc is born as a short stub on one side and
    // grows around the ring, which is what the alignment actually looks like.
    float settle = mix(2.6, 1.0, LensState.x);
    vec2 beta = vec2(SourceShape.y * settle, 0.0);
    vec2 relative = source - beta;
    float lit = hsSource(relative, SourceShape.x, LensState.w);

    // The halo. Every other effect in this mod is bloomed by the bright-pass and blur
    // chain, but this pass runs after all of it, so nothing here ever gets a glow. Rather
    // than fake one by blurring pixels, the same source is sampled again at a larger radius
    // and a fraction of the amplitude: it is the same galaxy through the same lens map, so
    // the glow follows the arc's real geometry all the way round.
    //
    // 1.7, not the 3.1 tried first. Three times the radius produced a wide soft band that
    // swallowed the arc inside it, and soft was the wrong direction entirely -- what makes
    // these arcs striking in the real images is a razor-thin brilliant line against black,
    // so the glow has to hug the arc rather than replace it.
    float haze = hsSource(relative, SourceShape.x * 1.7, LensState.w) * 0.20;

    // Colour by intensity rather than scaling one tone. Deep blue in the band, pale at the
    // knots. Multiplying a single mid-blue by a large gain just clipped every channel to
    // white and threw away the blue-against-red contrast the object is known for -- the
    // brightness was there and the colour was gone.
    float tone = clamp((lit + haze) * 0.55, 0.0, 1.0);
    vec3 arcColour = mix(HS_ARC_DEEP, HS_ARC_PALE, tone);
    vec3 arc = arcColour * (lit * 2.2 + haze) * gain;

    // The critical curve itself. Magnification formally diverges at rEll = 1, so the image
    // of any source material sitting there is a line with no width at all. That hard edge
    // is most of why real arcs look the way they do, and a smooth radial falloff blurs it
    // out of existence. Gated on lit, so it only draws where there is light to focus and
    // never paints a complete ring through the gap.
    float edge = exp(-pow((rEll - 1.0) / 0.055, 2.0));
    arc += arcColour * edge * lit * 2.0;

    // The lens galaxy. A red elliptical, well inside the ring, with the steep centrally
    // concentrated profile those actually have rather than a flat disk.
    float coreR = length(theta) / max(SourceShape.z, 0.001);
    vec3 core = HS_CORE_COLOUR * exp(-coreR * 1.55) * 2.1;

    // The real scene, read from the direction its light started out in. Sampling the
    // deflected position is what bends terrain, sky and the caster's other spell light
    // around the galaxy.
    //
    // Scaled by the distance ratio, and that term is not a fudge -- it is the part of the
    // lens equation this file was missing:
    //
    //     beta = theta - (D_ds / D_s) * alpha
    //
    // Real lensing gets away with a deflection of a whole Einstein radius because the
    // source is billions of light years further off than the lens, so a minute angular
    // deflection corresponds to an enormous transverse distance. Terrain forty blocks away
    // is at essentially the same distance as the lens, and displacing it by a full 11
    // blocks swelled the entire landscape into a mound. With the ratio in place, ground
    // just behind the lens barely moves and only genuinely distant scenery and the sky
    // bend fully, which is both correct and what removes the mound -- it is not tuned away.
    vec3 background = sceneColour;
    float ratio = sceneDistance > distance
            ? clamp((sceneDistance - distance) / max(sceneDistance, 0.001), 0.0, 1.0)
            : 0.0;
    vec2 sceneSource = theta - deflect * ratio;
    float bent = length(sceneSource - theta);
    if (bent > 0.001) {
        vec3 from = centre + (sceneSource.x * axisU + sceneSource.y * axisV) * thetaE;
        vec4 projected = ProjectionMat * vec4(from, 1.0);
        if (projected.w > 0.00001) {
            vec2 outUv = (projected.xy / projected.w) * 0.5 + 0.5;
            // A ray deflected off the edge of the screen has no data to read, which is
            // the unavoidable limit of doing this in screen space. Clamp to the edge and
            // then fade back to the undeflected colour by how far off it went, so it
            // degrades quietly instead of smearing the border.
            vec2 inside = clamp(outUv, 0.0, 1.0);
            float strayed = length(outUv - inside);
            vec3 sampled = mix(texture(SceneSampler, inside).rgb, sceneColour,
                    clamp(strayed * 7.0, 0.0, 1.0));
            // Keyed on how far the ray actually moved, and that is load-bearing rather
            // than tidy: the deflection is tapered to zero at the pass's edge, and if a
            // barely-deflected ray still returned a shifted sample then the taper boundary
            // would show up as a hard ring drawn across the world. Making zero deflection
            // return the untouched colour by construction leaves no boundary to see.
            float lensed = clamp(bent * 1.6, 0.0, 1.0) * nearFade * visible;
            background = mix(sceneColour, sampled, lensed);
        }
    }

    vec3 result = background + (arc + core) * bright * nearFade * visible;
    fragColor = vec4(result, 1.0);
}
