#version 150

// Volumetric plasma: a ray marched through a rotating noise field.
//
// Ported from ArcaneVortex's volumetric_shader, credited in CREDITS.txt. Taken as it stands:
// the golden-ratio rotation matrix, the 80 steps, the cos(p.z) accumulation, the ACES
// tonemap and the 2.2 gamma are all its own, and the look is what those numbers produce.
//
// Written opaque, like theirs. Their render type carries the "no_transparency" shard, so the
// fragment goes down as-is; an earlier version here blended it at partial alpha, and the three
// shells of the sigil mixed into one muddy shape instead of layering into a band with a rim
// either side. The alpha channel is therefore ignored downstream and only gates the discard.
//
// Its time came from that mod's own clock, which is a uniform set per draw here.

#define PHI 1.618033988

uniform float Drift;
uniform float Yaw;
uniform float Pitch;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 fPos;

out vec4 fragColor;

vec3 aces(vec3 colour) {
    mat3 m1 = mat3(
        0.59719, 0.07600, 0.02840,
        0.35458, 0.90834, 0.13383,
        0.04823, 0.01566, 0.83777);
    mat3 m2 = mat3(
        1.60475, -0.10208, -0.00327,
        -0.53108, 1.10813, -0.07276,
        -0.07367, -0.00605, 1.07602);
    vec3 v = m1 * colour;
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    return m2 * (a / b);
}

/**
 * The field. A dot product of cos and sin through a golden-ratio rotation, which is what makes
 * it aperiodic — a plain axis-aligned noise would show its lattice as visible banding.
 *
 * Named away from any reserved built-in: declaring a function whose name collides with one
 * crashed this mod at startup on AMD cards once already.
 */
float volPlasmaNoise(vec3 p) {
    const mat3 GOLD = mat3(
        -0.571464913, +0.814921382, +0.096597072,
        -0.278044873, -0.303026659, +0.911518454,
        +0.772087367, +0.494042493, +0.399753815);
    return dot(cos(GOLD * p), sin(PHI * p * GOLD));
}

/** Step length: short where the field is dense, so detail is not marched past. */
float volStep(vec3 p) {
    return 0.02 + abs(volPlasmaNoise(p)) * 0.3;
}

void main() {
    float alpha = vertexColor.a;
    if (alpha < 0.004) {
        discard;
    }

    vec4 dir = normalize(vec4(-fPos, 0.0));

    float sb = sin(Pitch);
    float cb = cos(Pitch);
    dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0.0));

    float sa = sin(-Yaw);
    float ca = cos(-Yaw);
    dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0.0));

    vec3 rayDir = normalize(dir.xyz);
    vec3 p = vec3(0.0, 0.0, Drift * 10.0) + rayDir * 0.1;
    vec3 l = vec3(0.0);

    for (int i = 0; i < 80; i++) {
        p += rayDir * volStep(p);
        // The 6 on red is what gives the field its banded, filamentary colour: red cycles six
        // times as fast along the march as green and blue do.
        l += cos(p.z * vec3(6.0, 1.0, 1.0)) * 1e2;
    }

    vec3 finalColour = pow(aces(l * l / 1e7), vec3(1.0 / 2.2));
    // Alpha 1: their render type disables blending, so this is written as-is.
    fragColor = vec4(finalColour, 1.0);
}
