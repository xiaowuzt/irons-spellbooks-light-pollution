#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseViewProjection;
uniform mat4 ViewProjection;
uniform vec2 SceneSize;
uniform float SampleCount;
uniform vec4 CenterRadius;
uniform vec3 AxisU;
uniform vec3 AxisV;
uniform vec3 AxisW;
uniform vec4 Parameters;
uniform float EffectTime;
uniform float EffectSeed;
uniform float Visibility;
uniform float Mode;
in vec2 texCoord;
out vec4 fragColor;
vec3 unproject(vec2 uv, float depth) {
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
float sceneDistance(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    return depth > 0.999999 ? 100000.0 : length(unproject(uv, depth));
}
vec3 localPoint(vec3 p) { return vec3(dot(p, AxisU), dot(p, AxisV), dot(p, AxisW)); }


float astralHash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.17, 0.43, 0.71));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
float astralNoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(astralHash(i), astralHash(i+vec3(1,0,0)), f.x),
                   mix(astralHash(i+vec3(0,1,0)), astralHash(i+vec3(1,1,0)), f.x), f.y),
               mix(mix(astralHash(i+vec3(0,0,1)), astralHash(i+vec3(1,0,1)), f.x),
                   mix(astralHash(i+vec3(0,1,1)), astralHash(i+vec3(1,1,1)), f.x), f.y), f.z);
}
float astralFbm(vec3 p) {
    return astralNoise(p) * 0.57 + astralNoise(p * 2.07 + 7.1) * 0.28
            + astralNoise(p * 4.13 - 4.8) * 0.15;
}
mat2 astralRotate(float a) { return mat2(cos(a), -sin(a), sin(a), cos(a)); }

const float TAU = 6.28318530718;
float gaussian(float x) { return exp(-x * x); }

// density, local colour: emission and absorption coexist in the same physical layer.
vec4 material(vec3 q) {
    float r = length(q.xz), theta = atan(q.z, q.x);
    float seed = EffectSeed * 0.007;
    vec3 drift = vec3(0.0, EffectTime * 0.025, seed);
    float cloud = astralFbm(q * 7.0 + drift);
    float density = 0.0;
    vec3 colour = vec3(0.0);
    if (Mode < 0.5) {
        // Two depth-separated ionised shells, not two rows of isolated glowing knots.
        float inner = gaussian((r - 1.0) / 0.13) * gaussian((q.y + 0.04 * sin(theta * 4.0)) / 0.18);
        float outer = gaussian((r - 1.42) / 0.17) * gaussian((q.y + q.x * 0.105 - 0.045) / 0.22);
        float veil = gaussian((length(q * vec3(1, 2.8, 1)) - 1.0) / 0.18) * 0.20;
        float lace = smoothstep(0.22, 0.68, cloud);
        float dust = smoothstep(0.52, 0.72, astralFbm(q * 13.0 + seed));
        float ionWave = 1.0 + 0.55 * gaussian((r - Parameters.y) / 0.085);
        density = (inner + outer + veil) * (0.30 + lace * 1.35) * Parameters.x;
        colour = (vec3(0.065, 0.63, 0.43) * (inner + veil)
                  + vec3(0.72, 0.075, 0.035) * outer) / max(inner + outer + veil, 0.001);
        colour *= (1.0 - dust * 0.84) * ionWave;
        density *= 1.0 + dust * 0.65;
    } else if (Mode < 1.5) {
        // Ragged supernova sheets with a tilted torus; no smooth camera-facing wind disc.
        float ell = length(q * vec3(0.91, 1.13, 1.0));
        float shell = gaussian((ell - 0.91) / 0.22);
        float folds = pow(1.0 - abs(sin(q.x * 10.0 + q.z * 8.0 + cloud * 8.0)), 6.0);
        float holes = smoothstep(0.30, 0.67, cloud);
        float localAge = EffectTime * 20.0 - length(q) * CenterRadius.w / 1.8;
        float pulse = localAge < 0.0 ? 0.0 : pow(1.0 - fract(localAge / 10.0), 2.0);
        float torus = gaussian((r - 0.32) / 0.09) * gaussian((q.y + q.x * 0.25) / 0.065);
        float wisp = gaussian((r - fract(EffectTime * 0.32) * 0.60 - 0.15) / 0.022)
                    * gaussian((q.y + q.x * 0.25) / 0.11);
        float gas = shell * holes * (0.28 + folds * 1.4);
        density = gas + torus * 0.85 + wisp * 0.28;
        vec3 lineColour = mix(vec3(0.80, 0.09, 0.035), vec3(0.095, 0.61, 0.33),
                             smoothstep(0.55, 0.70, cloud));
        colour = (lineColour * gas * (0.40 + pulse * 0.95)
                + vec3(0.17, 0.38, 0.80) * (torus + wisp) * (0.65 + pulse * 0.4)) / max(density, 0.001);
    } else if (Mode < 2.5) {
        // Finite-thickness, folded dust sheets around the unchanged Archimedean arm paths.
        float delta = theta - Parameters.y - r * 2.2 * TAU;
        float armDistance = abs(atan(sin(delta * 2.0), cos(delta * 2.0))) * r * 0.5;
        float width = (0.45 + r * 0.75) * 1.1 / 26.0;
        float fold = sin(r * 32.0 + theta * 3.0 - EffectTime * 0.7) * (0.006 + r * 0.018);
        float sheet = gaussian(armDistance / max(width * 1.25, 0.012))
                    * gaussian((q.y - fold) / (0.018 + r * 0.038));
        float edge = (1.0 - smoothstep(Parameters.x - 0.025, Parameters.x + 0.025, r))
                    * smoothstep(0.045, 0.12, r);
        float advected = astralFbm(vec3(r * 24.0 - EffectTime * 1.1, theta * 2.0, q.y * 55.0) + seed);
        float skirt = gaussian(armDistance / max(width * 2.4, 0.020))
                    * gaussian((q.y - fold) / (0.032 + r * 0.068));
        float hotDust = sheet * edge * (0.48 + advected * 1.4);
        float coolDust = skirt * edge * (0.16 + cloud * 0.20);
        density = hotDust + coolDust;
        // Copper-red outer sheets remain legible against a night sky; their broader, dark
        // skirts absorb the background. Only the narrow material spine is incandescent.
        vec3 hotColour = mix(vec3(1.0, 0.48, 0.095), vec3(0.32, 0.055, 0.014), smoothstep(0.10, 0.95, r));
        colour = (hotColour * hotDust + vec3(0.15, 0.030, 0.010) * coolDust) / max(density, 0.001);
        colour *= 0.65 + cloud * 0.70;
        // Wind collision creates fresh dust between the unequal-temperature stars.
        vec2 rotated = astralRotate(-Parameters.y) * q.xz;
        float shock = gaussian((rotated.x - 2.8 * rotated.y * rotated.y) / 0.012)
                    * gaussian(q.y / 0.05) * (1.0 - smoothstep(0.09, 0.22, r));
        colour = (colour * density + vec3(0.80, 0.45, 0.13) * shock) / max(density + shock, 0.001);
        density += shock;
        colour *= 1.0 + Parameters.w * 0.8;
    } else if (Mode < 3.5) {
        // Differentially rotating accretion disk. Empty inside the horizon, hot at the inner rim.
        float radial = smoothstep(0.27, 0.33, r) * (1.0 - smoothstep(0.82, 1.10, r));
        float angle = theta + EffectTime * 0.38 / max(pow(r, 1.5), 0.08);
        vec3 sampleAt = vec3(cos(angle) * r, sin(angle) * r, q.y) * 14.0 + seed;
        float turbulence = astralFbm(sampleAt);
        float lanes = 0.62 + 0.38 * sin(r * 120.0 + turbulence * 7.0);
        density = radial * gaussian(q.y / (0.032 + r * 0.016)) * (0.42 + turbulence * 1.45) * lanes;
        colour = mix(vec3(0.24, 0.55, 1.0), vec3(0.94, 0.26, 0.035), smoothstep(0.32, 0.95, r));
        colour *= (0.58 + lanes * 0.5) * (1.0 + Parameters.y * 2.0);
        density *= 0.2 + Parameters.x * 0.8;
    } else {
        // A structured stellar corona. No enormous white bloom disc hiding the photosphere.
        float rr = length(q);
        float streamer = pow(smoothstep(0.33, 0.70, astralFbm(normalize(q + 0.00001) * 9.0 + drift)), 2.0);
        density = exp(-max(rr - 1.0, 0.0) * 3.8) * smoothstep(0.98, 1.08, rr)
                * (0.12 + streamer * 0.60) * (1.0 - smoothstep(2.0, 2.8, rr));
        colour = mix(vec3(1.0, 0.29, 0.025), vec3(0.29, 0.59, 1.0), Parameters.x);
        colour *= 0.65 + Parameters.y * 0.3;
    }
    // Visibility thins gas instead of simply fading an opaque, glowing shell.
    return vec4(colour, max(density, 0.0) * max(Visibility, 0.0));
}

void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    vec3 ray = normalize(unproject(uv, 1.0));
    float bound = CenterRadius.w * (Mode > 3.5 ? 2.85 : 1.88);
    float b = dot(-CenterRadius.xyz, ray);
    float c = dot(CenterRadius.xyz, CenterRadius.xyz) - bound * bound;
    float disc = b * b - c;
    if (disc < 0.0) discard;
    float start = max(0.0, -b - sqrt(disc));
    float end = min(-b + sqrt(disc), sceneDistance(uv));
    if (end <= start) discard;
    int steps = int(clamp(SampleCount < 1.0 ? 112.0 : SampleCount, 32.0, 112.0));
    float stepLength = (end - start) / float(steps);
    float jitter = astralHash(vec3(gl_FragCoord.xy, 0.0));
    float transmittance = 1.0;
    vec3 radiance = vec3(0.0);
    for (int i = 0; i < 112; ++i) {
        if (i >= steps) break;
        float t = start + (float(i) + jitter) * stepLength;
        vec3 q = localPoint(ray * t - CenterRadius.xyz) / CenterRadius.w;
        vec4 sampleValue = material(q);
        float alpha = 1.0 - exp(-sampleValue.a * stepLength / CenterRadius.w * 10.5);
        radiance += transmittance * sampleValue.rgb * alpha;
        transmittance *= 1.0 - alpha;
        if (transmittance < 0.012) break;
    }
    if (1.0 - transmittance < 0.001) discard;
    // Premultiplied alpha keeps absorbing dust dark instead of adding black (= doing nothing).
    fragColor = vec4(radiance, 1.0 - transmittance);
}
