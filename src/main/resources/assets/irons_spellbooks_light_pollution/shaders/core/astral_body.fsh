#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseViewProjection;
uniform mat4 ViewProjection;
uniform vec2 SceneSize;
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

uniform vec3 BodyRadii;

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

void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    vec3 ray = normalize(unproject(uv, 1.0));
    vec3 ro = localPoint(-CenterRadius.xyz) / BodyRadii;
    vec3 rd = localPoint(ray) / BodyRadii;
    float aa = dot(rd, rd), bb = dot(ro, rd), cc = dot(ro, ro) - 1.0;
    float discriminant = bb * bb - aa * cc;
    float t;
    bool rim = false;
    if (discriminant < 0.0) {
        // The horizon is black, with a *thin* photon rim, not a recoloured luminous star.
        if (Mode < 0.5) discard;
        float closest = -bb / aa;
        float miss = length(ro + rd * closest);
        if (closest <= 0.0 || miss > 1.045) discard;
        t = closest;
        rim = true;
    } else {
        t = (-bb - sqrt(discriminant)) / aa;
        if (t < 0.0) t = (-bb + sqrt(discriminant)) / aa;
    }
    if (t <= 0.0 || t >= sceneDistance(uv) - 0.004) discard;
    vec3 at = ray * t;
    vec4 clip = ViewProjection * vec4(at, 1.0);
    gl_FragDepth = clip.z / clip.w * 0.5 + 0.5;
    if (Mode > 0.5) {
        if (rim) {
            float phase = atan(dot(at - CenterRadius.xyz, AxisW), dot(at - CenterRadius.xyz, AxisU));
            fragColor = vec4(vec3(0.95, 0.52, 0.14) * (0.5 + 0.25 * sin(phase * 3.0 + EffectTime)),
                    clamp(Visibility, 0.0, 1.0) * 0.65);
        } else fragColor = vec4(0.003, 0.001, 0.008, clamp(Visibility, 0.0, 1.0));
        return;
    }
    vec3 surface = normalize(ro + rd * t);
    vec3 normalLocal = normalize(surface / BodyRadii);
    vec3 normal = AxisU * normalLocal.x + AxisV * normalLocal.y + AxisW * normalLocal.z;
    float mu = max(dot(normal, -ray), 0.0);
    surface.xz = astralRotate(EffectTime * 0.13) * surface.xz;
    float seed = EffectSeed * 0.013;
    vec3 drift = surface * 5.8 + vec3(seed, EffectTime * 0.11, 0.0);
    float convection = astralFbm(drift + (astralFbm(drift * 0.75) - 0.5) * 1.7);
    float cells = astralNoise(surface * 27.0 + vec3(EffectTime * 0.32, seed, 0));
    float darkLanes = smoothstep(0.32, 0.51, convection);
    float heat = clamp(Parameters.x, 0.0, 1.0);
    vec3 cool = mix(vec3(0.28, 0.015, 0.002), vec3(0.075, 0.19, 0.34), heat);
    vec3 hot = mix(vec3(1.3, 0.56, 0.055), vec3(0.64, 0.88, 1.18), heat);
    vec3 colour = mix(cool, hot, darkLanes) * (0.66 + cells * 0.50);
    colour *= (0.32 + 0.68 * pow(mu, 0.65));
    colour += hot * pow(1.0 - mu, 8.0) * 0.25;
    fragColor = vec4(colour * Parameters.y, clamp(Visibility, 0.0, 1.0));
}
