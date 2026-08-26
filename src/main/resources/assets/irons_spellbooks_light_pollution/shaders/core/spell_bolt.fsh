#version 150

// Lightning, drawn the way Some of FX (github.com/YangMao-Minister/some_of_fx,
// MIT, (c) 2026 Pizuka) draws it: a signed distance to the bolt's centreline,
// with the sample point pushed sideways by noise before the distance is taken.
//
// The earlier attempt here built the zig-zag out of geometry. That has two
// problems this does not: the ribbon's width is fixed in blocks, so a distant
// bolt thins to a thread and a close one becomes a plank; and the kinks are
// baked per vertex, so the jitter is limited to the segment count. Displacing
// the sample point instead gives a bolt whose width is whatever the shader wants
// and whose detail is per pixel.
//
// The displacement reads a real noise tile rather than a hash. A sin-based hash
// has visible banding at the low frequencies that set the bolt's overall path,
// and that banding is what made the first version read as a smooth ribbon.
//
// Vertex colour: r = flicker seed, g = mode, b = intensity, a = fade.
// UV0.x runs along the bolt, UV0.y across the ribbon it is drawn in.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
// x = wall clock seconds, wrapped. Drives the flicker.
uniform vec4 BoltTime;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float noiseAt(vec2 uv) {
    return texture(Sampler0, fract(uv)).r;
}

float noiseG(vec2 uv) {
    return texture(Sampler0, fract(uv)).g;
}

void main() {
    float seed      = vertexColor.r * 64.0;
    float intensity = vertexColor.b * 4.0;
    float fade      = vertexColor.a;

    if (fade < 0.002 || intensity < 0.002) {
        discard;
    }

    float along = clamp(uvCoord.x, 0.0, 1.0);
    // Centred and normalised: -1 at one edge of the ribbon, +1 at the other.
    float across = (uvCoord.y - 0.5) * 2.0;

    float t = BoltTime.x;

    // Lateral displacement of the sample point. Two octaves at very different
    // rates: the slow one is the bolt's overall path, the fast one is the
    // per-frame crackle. Both walk along the tile rather than sampling a point,
    // so the path slides instead of popping.
    float slow = noiseAt(vec2(along * 0.35 + seed * 0.11, t * 0.06 + seed * 0.07))
            * 2.0 - 1.0;
    float fast = noiseG(vec2(along * 2.1 + seed * 0.03, t * 0.9)) * 2.0 - 1.0;
    float wander = slow * 0.66 + fast * 0.22;
    // Pinned at both ends, so the bolt actually meets whatever it connects.
    wander *= sin(along * 3.14159265);

    float d = abs(across - wander);

    // The channel: very thin, and the visible thickness comes from the glow
    // around it rather than from the channel itself.
    float core = 1.0 - smoothstep(0.0, 0.075, d);
    float glow = exp(-d * 5.5) * 0.5;

    // Branches: short offshoots leaving the main channel. Cheap version of the
    // real thing, but it is what stops a bolt reading as a single drawn stroke.
    float branchCell = floor(along * 7.0);
    float branchLive = step(0.62,
            noiseAt(vec2(branchCell * 0.19 + seed * 0.05, floor(t * 8.0) * 0.031)));
    float branchOffset = (noiseG(vec2(branchCell * 0.23, seed * 0.13)) - 0.5) * 1.4;
    float branchD = abs(across - wander - branchOffset * fract(along * 7.0));
    float branch = (1.0 - smoothstep(0.0, 0.05, branchD)) * branchLive * 0.55;

    // Hard on/off flicker. An arc that dims smoothly reads as a glowing wire.
    float strobe = 0.5 + 0.5 * step(0.24,
            noiseAt(vec2(floor(along * 4.0) * 0.27 + seed * 0.09,
                    floor(t * 22.0) * 0.017)));

    // Fades out at the very ends so it does not stop with a flat cut.
    float ends = smoothstep(0.0, 0.06, along) * smoothstep(0.0, 0.06, 1.0 - along);

    float brightness = (core * 2.4 + branch + glow) * strobe * ends * intensity;

    // Electric blue-white: hottest in the channel, blue in the glow.
    vec3 rgb = mix(vec3(0.42, 0.62, 1.0), vec3(1.0, 1.0, 1.0),
            clamp(core * 1.3 + branch, 0.0, 1.0)) * brightness;

    float alpha = clamp(brightness, 0.0, 1.0) * fade;
    if (alpha < 0.0015) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
