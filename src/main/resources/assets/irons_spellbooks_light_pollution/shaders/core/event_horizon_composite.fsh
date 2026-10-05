#version 150
// The supplied Image pass's filmic curve is applied ONLY to the spell, never to the whole game.
uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D EffectSampler;
uniform sampler2D Bloom0;
uniform sampler2D Bloom1;
uniform sampler2D Bloom2;
uniform sampler2D Bloom3;
uniform sampler2D Bloom4;
uniform sampler2D Bloom5;
uniform vec4 HoleCentre;
uniform vec4 State;
uniform mat4 InverseProjectionMat;
uniform mat4 ProjectionMat;
in vec2 texCoord;
out vec4 fragColor;
vec3 unproject(vec2 uv, float depth) {
    vec4 p = InverseProjectionMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
float frontDistance(vec3 ray) {
    float b = dot(HoleCentre.xyz, ray);
    float radius = HoleCentre.w * State.w;
    float h = b * b - dot(HoleCentre.xyz, HoleCentre.xyz) + radius * radius;
    // Outside the projected sphere, bloom may spread but must not bend a foreground surface.
    return h > 0.0 ? max(0.0, b - sqrt(h))
            : max(0.0, -HoleCentre.z) / max(0.001, -ray.z);
}
vec3 grade(vec3 color) {
    color = pow(max(color * 200.0, vec3(0)), vec3(1.5));
    color = pow(color / (1.0 + color), vec3(1.0 / 1.5));
    color = color * color * (3.0 - 2.0 * color);
    color = pow(max(color, vec3(0)), vec3(1.3, 1.20, 1.0));
    return pow(clamp(color * 1.01, 0.0, 1.0), vec3(0.7 / 2.2));
}
void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    float rawDepth = texture(DepthSampler, texCoord).r;
    vec3 ray = normalize(unproject(texCoord, 1.0));
    float front = frontDistance(ray);
    float sceneDistance = rawDepth < 0.999999 ? length(unproject(texCoord, rawDepth)) : 1e6;
    // Stop both the volume AND its bloom from leaking through a foreground wall.
    if (State.y <= .001 || sceneDistance < max(0.0, front)) { fragColor = scene; return; }
    vec4 effect = texture(EffectSampler, texCoord);
    vec3 halo = texture(Bloom0, texCoord).rgb;
    halo += texture(Bloom1, texCoord).rgb * 1.5;
    halo += texture(Bloom2, texCoord).rgb;
    halo += texture(Bloom3, texCoord).rgb * 1.5;
    halo += texture(Bloom4, texCoord).rgb * 1.8;
    halo += texture(Bloom5, texCoord).rgb;
    vec3 emission = grade(effect.rgb + halo * .08);
    // Small, depth-checked screen-space background deflection. The disk itself uses bent 3-D rays.
    vec4 projected = ProjectionMat * vec4(HoleCentre.xyz, 1.0);
    vec3 background = scene.rgb;
    if (projected.w > 0.01 && HoleCentre.z < -HoleCentre.w) {
        vec2 centre = projected.xy / projected.w * .5 + .5;
        vec2 delta = texCoord - centre;
        float radius = abs(ProjectionMat[1][1] * HoleCentre.w / projected.w) * .5;
        float aspect = abs(ProjectionMat[1][1] / ProjectionMat[0][0]);
        vec2 metric = delta * vec2(aspect, 1);
        float r2 = dot(metric, metric);
        float bend = min(.55, radius * radius * .38 / max(r2, .00001)) * State.y;
        vec2 bent = texCoord - delta * bend * (1.0 - smoothstep(radius * 4.0, radius * 7.0, length(metric)));
        if (all(greaterThan(bent, vec2(0))) && all(lessThan(bent, vec2(1)))) {
            float bentDepth = texture(DepthSampler, bent).r;
            float bentDistance = bentDepth < .999999 ? length(unproject(bent, bentDepth)) : 1e6;
            if (bentDistance >= frontDistance(normalize(unproject(bent, 1.0)))) background = texture(SceneSampler, bent).rgb;
        }
    }
    vec3 attenuated = background * (1.0 - effect.a);
    // Screen blend preserves HDR glow against bright skies without clamping the whole frame white.
    fragColor = vec4(1.0 - (1.0 - attenuated) * (1.0 - emission), scene.a);
}
