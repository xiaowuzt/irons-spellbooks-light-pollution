#version 150

// Second Sun. A disc placed far out on the sky, drawn as a billboard with the
// star's structure done analytically: granulated photosphere, limb darkening, a
// corona with streamers, and a nova shell once it goes off.
//
// Vertex colour: r = time seed, g = temperature, b = brightness, a = fade.
// UV0 spans the billboard 0..1. UV0 alone gives the disc; the extra state comes
// through NovaState.
//
// NovaState.x = nova progress 0..1 (0 before it detonates)
// NovaState.y = shell radius as a fraction of the billboard
// NovaState.z = the photosphere's radius as a fraction of the billboard
// NovaState.w = unused

uniform vec4 ColorModulator;
uniform vec4 NovaState;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i + vec3(0, 0, 0)), hash13(i + vec3(1, 0, 0)), f.x),
            mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
            mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

/** Convective granulation, sampled on the sphere's own surface. */
float granulation(vec3 normal, float t) {
    float n = noise3(normal * 4.5 + vec3(0.0, t * 0.25, 0.0)) * 0.6;
    n += noise3(normal * 12.0 - vec3(t * 0.2, 0.0, t * 0.15)) * 0.28;
    n += noise3(normal * 27.0 + vec3(t * 0.35)) * 0.12;
    return n;
}

void main() {
    float seed        = vertexColor.r * 60.0;
    float temperature = vertexColor.g;
    float brightness  = vertexColor.b * 5.0;
    float fade        = vertexColor.a;

    if (fade < 0.002 || brightness < 0.002) {
        discard;
    }

    vec2 centred = (uvCoord - 0.5) * 2.0;
    float radial = length(centred);

    // Everything is round, so anything outside the inscribed circle is billboard
    // corner and must not be drawn. Without this the corona's falloff still had a
    // few percent left out at radius 1.41, which drew the quad's corners and gave
    // the sun visible square edges.
    if (radial > 1.0) {
        discard;
    }

    // Where the photosphere ends. The billboard is deliberately larger than the
    // body so the corona has somewhere to live, so this cannot be assumed to be
    // the billboard's own edge.
    float surface = clamp(NovaState.z, 0.05, 0.99);

    // Temperature ramp: white through gold to deep red as it cools.
    vec3 hot  = vec3(1.00, 0.99, 0.96);
    vec3 warm = vec3(1.00, 0.82, 0.42);
    vec3 cool = vec3(0.92, 0.32, 0.12);
    vec3 core = mix(cool, warm, clamp(temperature * 2.0, 0.0, 1.0));
    core = mix(core, hot, clamp((temperature - 0.5) * 2.0, 0.0, 1.0));

    float nova = clamp(NovaState.x, 0.0, 1.0);
    vec3 rgb = vec3(0.0);
    float alpha = 0.0;

    // ── The disc ──────────────────────────────────────────────────────
    // Hard edge, because a sun has one; the corona lives outside it.
    float disc = 1.0 - smoothstep(surface * 0.97, surface, radial);
    if (disc > 0.001) {
        // Reconstruct the sphere's surface normal from the disc coordinate. This
        // is what the first version was missing: sampling the pattern in flat
        // billboard space made every cell the same size across the whole face,
        // which is precisely how a pasted texture looks. On the real surface the
        // cells compress toward the limb, and that foreshortening is what the eye
        // reads as roundness.
        float onDisc = clamp(radial / surface, 0.0, 1.0);
        float cosAngle = sqrt(max(0.0, 1.0 - onDisc * onDisc));
        vec3 normal = normalize(vec3(centred / surface, cosAngle));

        // Slow rotation about the pole, so the surface turns rather than sitting
        // still. A motionless face is the other half of why it read as printed on.
        float spin = seed * 0.05;
        float cs = cos(spin);
        float sn = sin(spin);
        vec3 spun = vec3(normal.x * cs - normal.z * sn, normal.y,
                normal.x * sn + normal.z * cs);

        // Driven hard: the additive blend saturates the middle to flat white
        // otherwise, and a disc with no falloff toward its edge is a circle
        // rather than a ball.
        float limb = 1.0 - 0.88 * (1.0 - cosAngle);

        float cells = granulation(spun, seed);
        float contrast = (cells - 0.5) * 2.0;
        float mottle = clamp(0.74 + contrast * 0.55, 0.28, 1.5);

        // Darker patches, also on the surface so they foreshorten with it.
        float spots = smoothstep(0.6, 0.78,
                noise3(spun * 2.2 + vec3(seed * 0.05)));
        float face = limb * mottle * (1.0 - spots * 0.5);

        // Gain below the clipping point, so the structure above survives the
        // additive blend instead of being crushed to white.
        rgb += core * brightness * 0.42 * face * disc;

        // A bright rim at the limb with prominences standing off it.
        float rim = pow(1.0 - cosAngle, 3.0);
        float archAngle = atan(centred.y, centred.x);
        float arches = 0.55 + 0.45 * noise3(vec3(archAngle * 3.4, seed * 0.2, 2.0));
        rgb += core * rim * arches * brightness * 0.5 * disc;

        alpha = max(alpha, disc * fade);
    }

    // ── Corona ────────────────────────────────────────────────────────
    if (radial > surface * 0.72) {
        float beyond = max(radial - surface, 0.0);
        // Reaches exactly zero at the billboard's inscribed edge, so no part of
        // the halo survives into the corners.
        float reach = max(1.0 - surface, 0.001);
        float falloff = exp(-beyond / (reach * 0.34))
                * (1.0 - smoothstep(0.0, reach, beyond));
        float angle = atan(centred.y, centred.x);
        // Streamers, so the halo is structured rather than a smooth gradient.
        float streamers = 0.6 + 0.4 * noise3(vec3(angle * 2.6, seed * 0.3, 1.0));
        streamers *= 0.75 + 0.45 * noise3(vec3(angle * 8.0, seed * 0.5, 4.0));
        float halo = falloff * streamers * (1.0 - disc);
        rgb += core * brightness * 0.4 * halo;
        alpha = max(alpha, clamp(halo, 0.0, 1.0) * fade * 0.85);
    }

    // ── Nova shell ────────────────────────────────────────────────────
    if (nova > 0.001) {
        float shell = clamp(NovaState.y, 0.0, 2.0);
        float front = exp(-pow((radial - shell) / 0.1, 2.0));
        // A second, wider front behind it.
        front += exp(-pow((radial - shell * 0.72) / 0.17, 2.0)) * 0.45;
        float angle = atan(centred.y, centred.x);
        float ragged = 0.72 + 0.28 * noise3(vec3(angle * 4.0, nova * 3.0, 7.0));
        float blast = front * ragged * (1.0 - nova * 0.4);

        rgb += vec3(1.0, 0.97, 0.92) * blast * brightness * 0.8;
        alpha = max(alpha, clamp(blast, 0.0, 1.0) * fade);
    }

    if (alpha < 0.0015) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
