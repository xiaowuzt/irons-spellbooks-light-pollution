// Volumetric star field: Kali's "Star Nest", from Shadertoy.
//
// NOT original to this mod or to ArcaneVortex, which is where it was found. It is Kali's
// work, reproduced here with the constants unchanged — iterations 17, formuparam 0.53,
// volsteps 20, tile 0.85, darkmatter 0.10, distfading 0.73 — and credited in CREDITS.txt.
// The mod author confirmed on 2026-08-28 that Shadertoy use is permitted here; the
// attribution is kept regardless, since that is the one obligation every plausible
// licence shares.
//
// Adapted in one way only: the original derives its ray direction from screen coordinates
// and a camera rotation, because it is a full-screen backdrop. Here a world-space
// direction is passed in instead, so it can be sampled along a light ray that gravity has
// already bent. That makes it a real celestial sphere — the same direction always returns
// the same stars, which is what stops it sliding about as the camera turns.

#ifndef STAR_NEST_GLSL
#define STAR_NEST_GLSL

#define SN_ITERATIONS 17
#define SN_FORMUPARAM 0.53
#define SN_VOLSTEPS 20
#define SN_STEPSIZE 0.1
#define SN_TILE 0.850
#define SN_BRIGHTNESS 0.0015
#define SN_DARKMATTER 0.300
#define SN_DISTFADING 0.730
#define SN_SATURATION 0.850

/**
 * The field along a unit world direction.
 *
 * @param dir   unit direction to look along
 * @param drift how far to have travelled through the field, for slow parallax
 */
vec3 starNest(vec3 dir, float drift) {
    vec3 from = vec3(1.0, 0.5, 0.5) + vec3(drift * 2.0, drift, -2.0);

    float s = 0.1;
    float fade = 1.0;
    vec3 v = vec3(0.0);

    for (int r = 0; r < SN_VOLSTEPS; r++) {
        vec3 p = from + s * dir * 0.5;
        p = abs(vec3(SN_TILE) - mod(p, vec3(SN_TILE * 2.0)));

        float pa = 0.0;
        float a = 0.0;
        for (int i = 0; i < SN_ITERATIONS; i++) {
            p = abs(p) / dot(p, p) - SN_FORMUPARAM;
            a += abs(length(p) - pa);
            pa = length(p);
        }

        float dm = max(0.0, SN_DARKMATTER - a * a * 0.001);
        a *= a * a;
        if (r > 6) {
            fade *= 1.0 - dm;
        }

        v += fade;
        v += vec3(s, s * s, s * s * s * s) * a * SN_BRIGHTNESS * fade;
        fade *= SN_DISTFADING;
        s += SN_STEPSIZE;
    }

    v = mix(vec3(length(v)), v, SN_SATURATION);
    return v * 0.01;
}

#endif
