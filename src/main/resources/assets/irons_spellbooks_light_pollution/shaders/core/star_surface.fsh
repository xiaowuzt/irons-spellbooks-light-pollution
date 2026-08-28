#version 150

// The surface of a star, on real sphere geometry.
//
// Vertex colour: rgb = body colour, a = intensity. UV0 is longitude and latitude. Normal is the
// true outward normal, which on a sphere is exact rather than interpolated from anything.
//
// Three things make this read as a body rather than a bright smear, and all three need a real
// normal:
//
//   * Limb darkening. A star is dimmer at its edge than its centre because you are looking
//     through more atmosphere at a shallower angle. The grey-atmosphere result is
//     I(mu)/I(1) = (2 + 3*mu)/5, so the limb sits at 0.4 of the middle. This single term is what
//     turns a disc into a ball.
//   * Granulation that stays on the surface. Sampled on the world-space normal, so a feature sits
//     on the star instead of sliding about as the camera moves. That sliding is the specific tell
//     that something is a billboard.
//   * Rotation. The sample direction is turned about the pole over time, so the features come round
//     again. A body that never turns reads as a decal however well it is shaded.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 surfaceNormal;
in vec3 toCamera;

out vec4 fragColor;

const float TAU = 6.28318530718;

float starHash(vec3 p) {
    return fract(sin(dot(p, vec3(17.13, 91.77, 47.31))) * 43758.5453);
}

/**
 * Value noise on a lattice. Named away from noise3, which is a reserved GLSL built-in that AMD's
 * compiler actually declares — overloading it crashed this mod at startup on those cards.
 */
float starValue(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(starHash(cell), starHash(cell + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(starHash(cell + vec3(0.0, 1.0, 0.0)),
                       starHash(cell + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(starHash(cell + vec3(0.0, 0.0, 1.0)),
                       starHash(cell + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(starHash(cell + vec3(0.0, 1.0, 1.0)),
                       starHash(cell + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

/** Turn about the world Y axis, which is the pole the mesh is built around. */
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

    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(toCamera);
    // Cosine of the angle between the surface and the line of sight. Back faces are culled, so
    // this only goes negative on the silhouette itself.
    float mu = max(0.0, dot(normal, view));

    float limb = (2.0 + 3.0 * mu) / 5.0;

    // A whole number of turns per GameTime wrap, so the pattern does not jump when GameTime
    // returns to zero every 24000 ticks.
    vec3 spun = spinY(normal, GameTime * TAU * 90.0);
    float grain = starValue(spun * 4.5) * 0.62 + starValue(spun * 11.0) * 0.38;
    // Low contrast on purpose. Heavy mottling reads as a rocky planet; a star's surface is
    // uneven, not blotchy.
    float lit = limb * (0.84 + 0.34 * grain);

    // The very edge, where mu approaches zero, gets a thin hot rim. Real stars have a
    // chromosphere there and it is also what stops the silhouette looking like a cut-out.
    lit += pow(1.0 - mu, 6.0) * 0.55;

    // Over-bright deliberately. These are supposed to hurt to look at.
    lit *= 1.5;

    vec3 colour = vertexColor.rgb * lit * intensity;
    fragColor = vec4(colour, 1.0) * vec4(ColorModulator.rgb, 1.0);
}
