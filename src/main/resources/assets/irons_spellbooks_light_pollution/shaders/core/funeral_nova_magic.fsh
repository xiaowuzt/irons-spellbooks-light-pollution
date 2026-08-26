#version 150

// Forge 1.20.1 port of Gemini KillEffect (LGPL-2.1).

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

mat2 rot(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

float circle(vec2 uv, float r, float blur) {
    // Avoid undefined smoothstep(edge0 == edge1) on the hard center glow.
    // A tiny feather keeps the edge stable across GLSL drivers.
    float safeBlur = max(abs(blur), 0.0001);
    return 1.0 - smoothstep(r - safeBlur, r + safeBlur, length(uv));
}

float ring(vec2 uv, float outer, float inner, float blur) {
    float d = length(uv);
    return smoothstep(inner - blur, inner + blur, d)
         * (1.0 - smoothstep(outer - blur, outer + blur, d));
}

void main() {
    float time    = vertexColor.r;
    float stage   = vertexColor.g * 8.0;
    float intensity = vertexColor.b;
    float alpha   = vertexColor.a;

    vec2 uv = (uvCoord - 0.5) * 2.0;

    float d = length(uv);
    float angle = atan(uv.y, uv.x);

    float rotSpeed = 0.8;
    if (stage > 1.5) rotSpeed = 1.5;
    float rotation = time * rotSpeed * 3.14159;
    vec2 uvRot = uv * rot(-rotation);

    vec3 gold       = vec3(1.0, 0.75, 0.15);
    vec3 brightGold = vec3(1.0, 0.88, 0.35);
    vec3 whiteGold  = vec3(1.0, 0.95, 0.7);

    float outerRing = ring(uv, 0.95, 0.88, 0.008);
    float innerRing = ring(uv, 0.82, 0.78, 0.006);
    float thinRing  = ring(uv, 0.60, 0.585, 0.004);

    float centerGlow = circle(uv, 0.25, 0.0) * 0.3;
    float centerDot  = circle(uv, 0.08, 0.01) * 0.6;

    float runeCount = 8.0;
    float runeAngle = angle * runeCount / (2.0 * 3.14159);
    float rune = 0.0;

    float primaryRune = step(0.92, abs(sin(angle * 4.0 + rotation * 0.5)));
    primaryRune *= ring(uv, 0.93, 0.80, 0.01);

    float secondaryRune = step(0.95, abs(sin(angle * 8.0 + rotation * 0.3)));
    secondaryRune *= ring(uv, 0.85, 0.75, 0.008);

    float innerRune = step(0.94, abs(sin(angle * 16.0 + rotation * 0.7)));
    innerRune *= ring(uv, 0.62, 0.55, 0.005);

    rune = primaryRune + secondaryRune * 0.7 + innerRune * 0.5;

    float spokes = smoothstep(0.965, 0.995,
            abs(cos(angle * 6.0 - rotation * 0.65)));
    spokes *= ring(uv, 0.76, 0.28, 0.006);
    spokes *= 1.0 - smoothstep(0.28, 0.40, abs(fract(d * 7.0 - time) - 0.5));

    float glyphBand = ring(uv, 0.72, 0.66, 0.006);
    float glyphs = smoothstep(0.72, 0.94,
            sin(angle * 24.0 + rotation * 0.35) * 0.5 + 0.5) * glyphBand;

    float orbitSpark = exp(-pow(d - 0.52, 2.0) * 900.0)
                     * pow(max(0.0, sin(angle * 12.0 - rotation * 2.0)), 18.0);

    float fbmVal = fbm(uvRot * 6.0 + time * 0.2) * 0.15;
    float ringTexture = fbmVal * (outerRing + innerRing + thinRing);

    float pulse = sin(d * 15.0 - time * 8.0) * 0.5 + 0.5;
    pulse *= exp(-d * 2.5) * 0.2;
    pulse *= smoothstep(0.0, 0.3, time);

    float ringTotal = outerRing + innerRing * 0.8 + thinRing * 0.6;
    float circleTotal = centerGlow + centerDot;

    vec3 ringColor  = mix(gold, brightGold, ringTexture + rune * 0.3);
    vec3 runeColor  = mix(brightGold, whiteGold, rune * 0.5);
    vec3 pulseColor = whiteGold * pulse;

    vec3 rgb = ringColor * ringTotal
             + runeColor * rune * 0.9
             + whiteGold * spokes * 0.42
             + brightGold * glyphs * 0.65
             + whiteGold * orbitSpark * 1.8
             + gold * circleTotal
             + pulseColor;

    if (stage < 1.5) {

        float birthFade = smoothstep(0.0, 0.25, time) * smoothstep(0.0, 0.15, d);
        rgb *= birthFade;
    }

    float finalAlpha = (ringTotal * 0.7 + rune * 0.8 + spokes * 0.35
                      + glyphs * 0.55 + orbitSpark + circleTotal * 0.5 + pulse) * alpha;

    fragColor = vec4(rgb * intensity, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.002) {
        discard;
    }
}
