#version 150

// Stellar Convergence. The stars reuse the analytic star program and the bolts
// linking them use the shared bolt program; this draws the converged column and
// its burst.
//
// Vertex colour: r = progress, g = mode selector, b = intensity, a = fade.
// Mode: < 0.67 column, else burst ring. Bolts moved to the shared bolt
// program, which draws them as a screen-space distance field instead.
// UV0.x runs along the primitive, UV0.y across it.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    float progress  = vertexColor.r;
    float mode      = vertexColor.g;
    float intensity = vertexColor.b * 4.0;
    float fade      = vertexColor.a;

    if (fade < 0.002 || intensity < 0.002) {
        discard;
    }

    if (mode < 0.67) {
        // ── Column ────────────────────────────────────────────────────
        // The converged beam, from the constellation down to the ground. UV0.y
        // runs 0 at the ground to 1 at the top; UV0.x crosses the shaft.
        float across = abs(uvCoord.x - 0.5) * 2.0;
        float up = clamp(uvCoord.y, 0.0, 1.0);

        // A hard white core inside a wider glow: without the hard core a beam of
        // this width reads as fog.
        float core = exp(-pow(across / 0.14, 2.0));
        float halo = exp(-pow(across / 0.62, 2.0)) * 0.42;

        // Standing waves running up the shaft, so it is clearly channelling
        // rather than just being lit.
        float bands = 0.82 + 0.18 * sin(up * 46.0 - progress * 30.0);

        // Slight widening toward the ground where it spreads on impact.
        float flare = 1.0 + (1.0 - up) * 0.35;

        float brightness = (core * bands * flare + halo) * intensity;
        vec3 rgb = mix(vec3(0.86, 0.92, 1.0), vec3(1.0, 1.0, 1.0),
                clamp(core, 0.0, 1.0)) * brightness;
        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        fragColor = vec4(rgb, alpha) * ColorModulator;
        if (fragColor.a < 0.0015) {
            discard;
        }
        return;
    }

    // ── Burst ring ────────────────────────────────────────────────────
    // The shock leaving the column's foot along the ground.
    vec2 centred = (uvCoord - 0.5) * 2.0;
    float radial = length(centred);
    if (radial > 1.0) {
        discard;
    }

    float front = clamp(progress, 0.0, 1.0);
    float ring = exp(-pow((radial - front) / 0.13, 2.0));
    ring += exp(-pow((radial - front * 0.6) / 0.2, 2.0)) * 0.4;

    float angle = atan(centred.y, centred.x);
    float tatter = 0.74 + 0.26 * hash12(vec2(floor(angle * 6.0), 2.0));
    ring *= tatter;

    float wash = (1.0 - smoothstep(0.0, front, radial)) * 0.24;
    float brightness = (ring + wash) * intensity * (1.0 - front * 0.5);
    vec3 rgb = mix(vec3(1.0, 1.0, 1.0), vec3(0.68, 0.82, 1.0), front) * brightness;
    float alpha = clamp(brightness, 0.0, 1.0) * fade;
    fragColor = vec4(rgb, alpha) * ColorModulator;
    if (fragColor.a < 0.0015) {
        discard;
    }
}
