#version 150

// Sky Collapse. Two things a light source cannot give: the sky splitting open,
// and the slabs of it coming down.
//
// Vertex colour: r = progress, g = mode selector, b = intensity, a = fade.
// Mode: < 0.5 shard slab, else the rift.
// Shard UV: x = distance from the slab's centre 0..1, y = angle around it.
// Rift UV:  x = position along the rift 0..1, y = across the band 0..1.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise2(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
        mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x),
        f.y);
}

void main() {
    float progress  = vertexColor.r;
    float mode      = vertexColor.g;
    float intensity = vertexColor.b * 4.0;
    float fade      = vertexColor.a;

    if (fade < 0.002 || intensity < 0.002) {
        discard;
    }

    if (mode < 0.5) {
        // ── Shard of sky ──────────────────────────────────────────────
        // Radial coordinates, so the falloff follows whatever irregular outline
        // the geometry actually has instead of assuming a square.
        float edge = clamp(uvCoord.x, 0.0, 1.0);
        float around = uvCoord.y;

        // The torn rim, ragged around the outline.
        float ragged = 0.86 + 0.14 * noise2(vec2(around * 9.0, progress * 0.6));
        float rim = exp(-pow((edge - ragged) / 0.17, 2.0));
        // A thinner white-hot line right at the break.
        rim += exp(-pow((edge - ragged) / 0.055, 2.0)) * 1.4;

        // Cracks running inward from the rim, so the slab still looks like it is
        // coming apart on the way down.
        float inward = noise2(vec2(around * 5.0, edge * 3.4 + progress * 0.7));
        float veins = exp(-abs(inward - 0.5) * 13.0)
                * smoothstep(0.15, 1.0, edge) * 0.85;

        // The face itself: dim, cold, and slightly mottled.
        float face = (0.1 + 0.06 * noise2(vec2(around * 7.0, edge * 6.0)))
                * step(edge, ragged);

        float brightness = (rim + veins + face) * intensity;
        vec3 rgb = mix(vec3(0.30, 0.46, 0.86), vec3(1.0, 0.96, 0.88),
                clamp(rim * 0.8 + veins * 0.4, 0.0, 1.0)) * brightness;

        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        // The interior stays visible even where it is dim, or the slab reads as a
        // glowing outline with a hole in it.
        alpha = max(alpha, face * 2.2 * fade);
        fragColor = vec4(rgb, alpha) * ColorModulator;
        if (fragColor.a < 0.0015) {
            discard;
        }
        return;
    }

    // ── The rift ──────────────────────────────────────────────────────
    // One split running across the sky, not a web. It opens from the middle
    // outward: a real gap with torn edges and light pouring out of it.
    float along = clamp(uvCoord.x, 0.0, 1.0);
    float across = (uvCoord.y - 0.5) * 2.0;
    float offset = abs(across);

    // How far from the middle of the band this is, 0 at the centre and 1 at the
    // ends.
    float fromCentre = abs(along * 2.0 - 1.0);
    float spread = clamp(progress, 0.0, 1.0);
    if (fromCentre > spread) {
        discard;
    }

    // The gap is widest in the middle and tapers to nothing at the advancing
    // tips, which is what makes it read as splitting rather than sliding open.
    float reach = 1.0 - fromCentre / max(spread, 0.001);
    float gap = 0.42 * smoothstep(0.0, 0.55, reach) * spread;
    // Torn, not cut: the two edges wander independently.
    float tearA = gap * (0.78 + 0.34 * noise2(vec2(along * 26.0, 1.0)));
    float tearB = gap * (0.78 + 0.34 * noise2(vec2(along * 26.0, 9.0)));
    float tear = across < 0.0 ? tearA : tearB;

    float brightness;
    vec3 rgb;
    if (offset < tear) {
        // Inside the gap. Whatever is behind the sky is brighter than the sky.
        float depth = 1.0 - offset / max(tear, 0.001);
        float churn = 0.7 + 0.3 * noise2(vec2(along * 14.0, offset * 20.0));
        brightness = (0.55 + depth * 1.5) * churn * intensity;
        rgb = mix(vec3(0.72, 0.86, 1.0), vec3(1.0, 1.0, 0.99), depth) * brightness;
    } else {
        // Outside: the hot torn lip, then light bleeding into the sky.
        float beyond = offset - tear;
        float lip = exp(-pow(beyond / 0.045, 2.0)) * 2.2;
        // The bleed has to reach exactly zero before the band's own edge. An
        // exponential alone still had a few percent left at the edge, and a few
        // percent of a band this size is a solid grey slab with the quad's
        // silhouette around it -- which is what was showing up on screen.
        float bleed = exp(-beyond / 0.16) * 0.45
                * (1.0 - smoothstep(0.0, 0.42, beyond));
        brightness = (lip + bleed) * intensity;
        rgb = mix(vec3(0.55, 0.72, 1.0), vec3(1.0, 0.98, 0.94),
                clamp(lip * 0.5, 0.0, 1.0)) * brightness;
    }

    // Brighter right at the advancing tips, where it is actively breaking. Kept
    // near the split itself: applied across the band's full width it drew a bright
    // bar straight across the sky at the leading edge.
    float nearSplit = exp(-pow(max(offset - tear, 0.0) / 0.1, 2.0));
    float tip = exp(-pow((spread - fromCentre) / 0.05, 2.0)) * 1.4 * nearSplit;
    brightness += tip * intensity;
    rgb += vec3(1.0, 0.99, 0.96) * tip * intensity;

    float alpha = clamp(brightness, 0.0, 1.0) * fade;
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}

