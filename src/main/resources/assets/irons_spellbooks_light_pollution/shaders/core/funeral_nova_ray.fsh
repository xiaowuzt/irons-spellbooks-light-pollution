#version 150

// Forge 1.20.1 port of Gemini KillEffect (LGPL-2.1).

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    float distAlong  = vertexColor.r;
    float rayIndex   = vertexColor.g;
    float intensity  = vertexColor.b * 4.0;
    float alpha      = vertexColor.a;

    if (alpha < 0.001 || intensity < 0.001) { discard; return; }

    float u = uvCoord.x;
    float v = uvCoord.y;

    float widthAtU = 0.03 + u * 0.10;
    float vCentered = abs(v - 0.5) * 2.0;
    float crossSection = exp(-vCentered * vCentered / (widthAtU * widthAtU));

    float distFalloff = 1.0 / (1.0 + u * u * 8.0);

    float sourcePeak = exp(-u * 6.0) * 0.7;

    float dither = hash2(vec2(u * 20.0 + rayIndex, rayIndex * 7.13)) * 0.3 + 0.7;

    float shimmer = 1.0 + 0.15 * sin(u * 40.0 + rayIndex * 13.0 + distAlong * 30.0);

    float brightness = (crossSection * distFalloff + sourcePeak * crossSection * 2.0)
                     * dither * shimmer * intensity;

    vec3 srcColor  = vec3(1.0, 1.0, 1.0);
    vec3 midColor  = vec3(0.55, 0.8, 1.0);
    vec3 tipColor  = vec3(0.2, 0.15, 0.8);

    float colorT = u;
    vec3 col = mix(srcColor, midColor, smoothstep(0.0, 0.4, colorT));
    col = mix(col, tipColor, smoothstep(0.4, 1.0, colorT));

    float hueShift = hash(rayIndex * 17.0) * 0.15;
    col = mix(col, col * vec3(1.0 + hueShift, 1.0, 1.0 - hueShift), 0.3);

    col *= brightness * 3.0;

    float finalAlpha = brightness * alpha;

    fragColor = vec4(col, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) discard;
}

