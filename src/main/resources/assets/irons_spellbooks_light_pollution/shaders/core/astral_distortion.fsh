#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseViewProjection;
uniform mat4 ViewProjection;
uniform vec2 SceneSize;
uniform vec4 CenterRadius;
uniform vec3 AxisU;
uniform vec3 AxisV;
uniform vec3 AxisW;
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

uniform sampler2D SceneSampler;

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
    float closest = dot(CenterRadius.xyz, ray);
    if (closest <= 0.0) discard;
    float miss = length(ray * closest - CenterRadius.xyz);
    float radius = CenterRadius.w;
    if (miss >= radius) discard;
    float front = max(0.0, closest - sqrt(max(radius * radius - miss * miss, 0.0)));
    if (sceneDistance(uv) < front) discard;
    vec4 projected = ViewProjection * vec4(CenterRadius.xyz, 1.0);
    if (projected.w <= 0.0) discard;
    vec2 centreUv = projected.xy / projected.w * 0.5 + 0.5;
    vec2 radial = (uv - centreUv) * SceneSize;
    vec2 direction = radial / max(length(radial), 0.1);
    float falloff = pow(1.0 - miss / radius, 2.0);
    float field = Mode > 0.5 ? exp(-pow((miss / radius - 0.83) / 0.10, 2.0)) : falloff;
    vec2 turbulence = vec2(astralNoise(vec3(uv * 90.0, EffectTime * 1.1)),
                           astralNoise(vec3(uv * 85.0 + 17.0, -EffectTime * 0.9))) - 0.5;
    vec2 offset = (direction * (Mode > 0.5 ? 7.0 : 1.0) + turbulence * 3.0)
                / SceneSize * field * Visibility;
    vec2 sampleUv = clamp(uv + offset, vec2(0.001), vec2(0.999));
    // Never pull a foreground wall into a background shock, or smear the wall's silhouette.
    if (sceneDistance(sampleUv) < front) sampleUv = uv;
    fragColor = vec4(texture(SceneSampler, sampleUv).rgb, 1.0);
}
