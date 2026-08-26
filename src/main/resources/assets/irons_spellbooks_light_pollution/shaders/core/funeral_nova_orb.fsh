#version 150

// Forge 1.20.1 port of Gemini KillEffect (LGPL-2.1).

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 vViewPos;
in vec3 vSphereCenter;

out vec4 fragColor;

void main() {
    float progress  = vertexColor.r;
    float heat      = vertexColor.g;
    float intensity = vertexColor.b * 4.0;
    float alpha     = vertexColor.a;
    float radius    = max(uvCoord.x, 0.001);

    if (alpha < 0.002 || intensity < 0.002) { discard; return; }

    vec3 C = vSphereCenter;

    vec3 D = normalize(vViewPos);

    vec3 oc = -C;
    float b = dot(oc, D);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) { discard; return; }
    h = sqrt(h);
    float t0 = max(-b - h, 0.0);
    float t1 = -b + h;
    if (t1 <= t0) { discard; return; }

    vec3 coreCol = mix(vec3(1.00, 0.55, 0.15), vec3(0.88, 0.96, 1.18), heat);
    vec3 rimCol  = mix(vec3(0.45, 0.06, 0.02), vec3(1.08, 0.32, 0.08), heat);

    const int STEPS = 12;
    float dt = (t1 - t0) / float(STEPS);
    vec3 acc = vec3(0.0);
    float trans = 1.0;

    for (int i = 0; i < STEPS; i++) {
        float t = t0 + (float(i) + 0.5) * dt;
        vec3 p = D * t;
        float d = length(p - C) / radius;
        float dd = d * d;

        float density = exp(-dd * 16.0) * 2.6
                      + exp(-dd * 5.0)  * 1.0
                      + exp(-dd * 1.5)  * 0.28;

        float boil = 0.84 + 0.16 * sin(p.x * 5.7 + p.y * 7.3 - p.z * 4.9
                                     + progress * 34.0 + float(i) * 0.7);
        float shellRadius = 0.24 + progress * 0.58;
        float shell = exp(-pow(d - shellRadius, 2.0) * 120.0)
                    * (1.0 - smoothstep(0.82, 1.0, progress));
        density = density * boil + shell * 1.15;

        vec3 sampleCol = mix(coreCol, rimCol, smoothstep(0.0, 0.85, d));

        acc += sampleCol * density * dt * trans;
        trans *= exp(-density * dt * 0.30);
    }
    acc *= 0.5;

    float flicker = 1.0 + 0.10 * sin(progress * 46.0 + vViewPos.x * 3.0)
                         + 0.04 * sin(progress * 91.0 + vViewPos.y * 5.0);

    vec3 rgb = acc * intensity * flicker;
    float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
    float aOut = clamp(lum * 0.5, 0.0, 1.0) * alpha;

    fragColor = vec4(rgb, aOut) * ColorModulator;
    if (fragColor.a < 0.0005) discard;
}
