#version 150

// The central body of an effect, plus its glow. Used by every spell that has something at
// the middle: a nucleus, a white dwarf, a neutron star, a binary, a black hole.
//
// Vertex colour: rgb = body colour, a = intensity.
// UV0 is a centred unit disc. Normal carries the billboard's world-space right vector.
// ColorModulator.a selects the layer:
//   >= 1.5  core     — fallback body, see below
//   >= 0.5  corona   — tight halo hugging the body
//   else    bloom    — wide soft glow, drawn first and largest
//
// The bloom and corona are the live paths. The core layer is now only reached when
// star_surface fails to compile, because the body is drawn as a real sphere instead — a
// billboard sphere still had no silhouette of its own and, more to the point, the shaded part
// was only the innermost fifth of what reached the screen, since the bloom around it goes out
// to five and a half times the body radius. It is kept because a shader failing to compile on
// one driver is something this mod has actually shipped, and a missing star is worse than a
// flat one.
//
// Three layers rather than one falloff because a single exponential either has a visible
// edge or no centre. Stacking them is what makes a small bright thing read as *bright*
// instead of merely white.
//
// The core layer draws a sphere rather than a disc. The disc version was reported as looking
// fake, and it was: a flat billboard has no limb, no rotation and no surface, so it read as a
// white circle painted on the air. Raytracing the sphere on the same quad costs a square root
// and fixes all three at once, which is far cheaper than real geometry per body.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 basisRight;
in vec3 viewRay;

out vec4 fragColor;

const float TAU = 6.28318530718;

float coreHash(vec3 p) {
    return fract(sin(dot(p, vec3(17.13, 91.77, 47.31))) * 43758.5453);
}

/**
 * Value noise on a lattice. Named away from noise3, which is a reserved GLSL built-in that
 * AMD's compiler actually declares — overloading it crashed the game at startup on those cards.
 */
float coreValue(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = coreHash(cell);
    float n100 = coreHash(cell + vec3(1.0, 0.0, 0.0));
    float n010 = coreHash(cell + vec3(0.0, 1.0, 0.0));
    float n110 = coreHash(cell + vec3(1.0, 1.0, 0.0));
    float n001 = coreHash(cell + vec3(0.0, 0.0, 1.0));
    float n101 = coreHash(cell + vec3(1.0, 0.0, 1.0));
    float n011 = coreHash(cell + vec3(0.0, 1.0, 1.0));
    float n111 = coreHash(cell + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

/** Rotate about the world Y axis, so the body spins on an upright axis. */
vec3 spinY(vec3 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(c * v.x + s * v.z, v.y, c * v.z - s * v.x);
}

void main() {
    float intensity = vertexColor.a;
    if (intensity < 0.002) {
        discard;
    }
    vec2 centred = uvCoord * 2.0 - 1.0;
    float radius = length(centred);
    if (radius > 1.0) {
        discard;
    }

    float layer = ColorModulator.a;
    float lit;
    if (layer >= 1.5) {
        // Rebuild the sphere's world-space normal on the billboard. mu is the cosine of the
        // angle between the surface and the line of sight, which for a sphere seen head-on is
        // just the height of the hemisphere above the disc.
        float mu = sqrt(max(0.0, 1.0 - radius * radius));
        vec3 forward = normalize(-viewRay);
        vec3 right = normalize(basisRight);
        vec3 up = cross(right, forward);
        vec3 surface = normalize(centred.x * right + centred.y * up + mu * forward);

        // Eddington limb darkening for a grey atmosphere: I(mu)/I(1) = (2 + 3*mu)/5, so the
        // limb sits at 0.4 of the centre. This one term is what turns a circle into a ball.
        float limb = (2.0 + 3.0 * mu) / 5.0;

        // Granulation, carried around with the body. A whole number of turns per GameTime wrap
        // so the pattern does not jump when GameTime returns to zero every 24000 ticks.
        // Named away from "sample", which later GLSL versions promote to a keyword; a reserved
        // identifier is exactly what crashed this mod on AMD once already.
        vec3 spun = spinY(surface, GameTime * TAU * 90.0);
        float grain = coreValue(spun * 5.0) * 0.62 + coreValue(spun * 13.0) * 0.38;
        // Contrast kept low. Strong granulation reads as a rocky planet; a star's surface is
        // mottled, not patchy.
        lit = limb * (0.82 + 0.40 * grain) * 1.45;

        // The limb is genuinely sharp on a star, so this is a two-pixel antialias rather than a
        // fade. The corona is drawn over the top of it, which hides the join.
        lit *= smoothstep(1.0, 0.965, radius);
    } else {
        if (layer >= 0.5) {
            // Corona: hugging the body, falling off fast.
            lit = exp(-radius * radius * 5.5) * 0.75;
            // Spikes, four-fold. Not physical — it is the look of something too bright for the
            // eye, and that is what is wanted here.
            float ang = atan(centred.y, centred.x);
            lit += pow(abs(cos(ang * 2.0)), 8.0) * exp(-radius * 2.2) * 0.55;
        } else {
            // Bloom: wide, weak, and the thing that makes the body feel like it is emitting
            // rather than being lit.
            lit = pow(1.0 - radius, 2.6) * 0.42;
        }
        // Zero at the rim for both glow layers. The discard above trims the quad to a circle,
        // but a discard alone leaves whatever value the falloff had at radius 1 as a hard
        // circular edge -- the corona's exp() and its spikes both end well above zero.
        lit *= max(0.0, 1.0 - radius * radius);
    }

    vec3 colour = vertexColor.rgb * lit * intensity;
    fragColor = vec4(colour, 1.0) * vec4(ColorModulator.rgb, 1.0);
}
