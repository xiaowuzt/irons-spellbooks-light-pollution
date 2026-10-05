#version 150
// R1 Buffer B/C/D and Image: sonicether, "Gargantua With HDR Bloom", Shadertoy lstSRS.
// Supplied in baopinsui's "GR BH with volume accretion disk", Shadertoy 4XcfR2.
// Original formulas: docs/black-hole/references/R1-original.txt.
// Source attribution/licensing: THIRD_PARTY_NOTICES.md.
// The supplied Image pass's filmic curve is applied ONLY to the spell, never to the whole game.
uniform sampler2D bhSceneSampler;
uniform sampler2D bhDepthSampler;
uniform sampler2D bhEffectSampler;
uniform sampler2D bhBloomSampler;
uniform vec4 bhResolution; // bloom atlas width, height, 1/width, 1/height
uniform vec4 bhCenterRadius;
uniform vec4 bhR1Options; // x: background deflection (0 when R2 already lenses); y: bloom strength (source .08)
uniform vec4 bhTiming; // seconds, age ticks, envelope, local gameplay rate
uniform vec4 bhQuality; // steps, Doppler, exposure, debug mode
uniform vec4 bhRadii; // gameplay influence, optical support, geometric mass, reserved
uniform mat4 bhInverseProjectionMatrix;
uniform mat4 bhProjectionMatrix;
in vec2 texCoord;
out vec4 fragColor;
vec3 unproject(vec2 uv, float depth) {
    vec4 p = bhInverseProjectionMatrix * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
float frontDistance(vec3 ray) {
    float b = dot(bhCenterRadius.xyz, ray);
    float radius = bhCenterRadius.w * (bhRadii.y / max(bhCenterRadius.w, .005));
    float h = b * b - dot(bhCenterRadius.xyz, bhCenterRadius.xyz) + radius * radius;
    // Outside the projected sphere, bloom may spread but must not bend a foreground surface.
    return h > 0.0 ? max(0.0, b - sqrt(h))
            : max(0.0, -bhCenterRadius.z) / max(0.001, -ray.z);
}
vec3 saturate(vec3 x)
{
    return clamp(x, vec3(0.0), vec3(1.0));
}

vec4 cubic(float x)
{
    float x2 = x * x;
    float x3 = x2 * x;
    vec4 w;
    w.x =   -x3 + 3.0*x2 - 3.0*x + 1.0;
    w.y =  3.0*x3 - 6.0*x2       + 4.0;
    w.z = -3.0*x3 + 3.0*x2 + 3.0*x + 1.0;
    w.w =  x3;
    return w / 6.0;
}

vec4 BicubicTexture(in sampler2D tex, in vec2 coord)
{
	vec2 resolution = bhResolution.xy;

	coord *= resolution;

	float fx = fract(coord.x);
    float fy = fract(coord.y);
    coord.x -= fx;
    coord.y -= fy;

    fx -= 0.5;
    fy -= 0.5;

    vec4 xcubic = cubic(fx);
    vec4 ycubic = cubic(fy);

    vec4 c = vec4(coord.x - 0.5, coord.x + 1.5, coord.y - 0.5, coord.y + 1.5);
    vec4 s = vec4(xcubic.x + xcubic.y, xcubic.z + xcubic.w, ycubic.x + ycubic.y, ycubic.z + ycubic.w);
    vec4 offset = c + vec4(xcubic.y, xcubic.w, ycubic.y, ycubic.w) / s;

    vec4 sample0 = texture(tex, vec2(offset.x, offset.z) / resolution);
    vec4 sample1 = texture(tex, vec2(offset.y, offset.z) / resolution);
    vec4 sample2 = texture(tex, vec2(offset.x, offset.w) / resolution);
    vec4 sample3 = texture(tex, vec2(offset.y, offset.w) / resolution);

    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);

    return mix( mix(sample3, sample2, sx), mix(sample1, sample0, sx), sy);
}

vec3 ColorFetch(vec2 coord)
{
 	return texture(bhEffectSampler, coord).rgb;   
}

vec3 BloomFetch(vec2 coord)
{
 	return BicubicTexture(bhBloomSampler, coord).rgb;   
}

vec3 Grab(vec2 coord, const float octave, const vec2 offset)
{
 	float scale = exp2(octave);
    
    coord /= scale;
    coord -= offset;

    return BloomFetch(coord);
}

vec2 CalcOffset(float octave)
{
    vec2 offset = vec2(0.0);
    
    vec2 padding = vec2(10.0) / bhResolution.xy;
    
    offset.x = -min(1.0, floor(octave / 3.0)) * (0.25 + padding.x);
    
    offset.y = -(1.0 - (1.0 / exp2(octave))) - padding.y * octave;

	offset.y += min(1.0, floor(octave / 3.0)) * 0.35;
    
 	return offset;   
}

vec3 GetBloom(vec2 coord)
{
 	vec3 bloom = vec3(0.0);
    
    //Reconstruct bloom from multiple blurred images
    bloom += Grab(coord, 1.0, vec2(CalcOffset(0.0))) * 1.0;
    bloom += Grab(coord, 2.0, vec2(CalcOffset(1.0))) * 1.5;
	bloom += Grab(coord, 3.0, vec2(CalcOffset(2.0))) * 1.0;
    bloom += Grab(coord, 4.0, vec2(CalcOffset(3.0))) * 1.5;
    bloom += Grab(coord, 5.0, vec2(CalcOffset(4.0))) * 1.8;
    bloom += Grab(coord, 6.0, vec2(CalcOffset(5.0))) * 1.0;
    bloom += Grab(coord, 7.0, vec2(CalcOffset(6.0))) * 1.0;
    bloom += Grab(coord, 8.0, vec2(CalcOffset(7.0))) * 1.0;

	return bloom;
}

vec3 grade(vec3 color) {
    // Verbatim Image tonemapping/grading; world integration remains outside this curve.
    color = pow(color, vec3(1.5));
    color = color / (1.0 + color);
    color = pow(color, vec3(1.0 / 1.5));
    color = mix(color, color * color * (3.0 - 2.0 * color), vec3(1.0));
    color = pow(color, vec3(1.3, 1.20, 1.0));
    color = saturate(color * 1.01);
    return pow(color, vec3(0.7 / 2.2));
}
void main() {
    vec4 scene = texture(bhSceneSampler, texCoord);
    float rawDepth = texture(bhDepthSampler, texCoord).r;
    vec3 ray = normalize(unproject(texCoord, 1.0));
    float front = frontDistance(ray);
    float sceneDistance = rawDepth < 0.999999 ? length(unproject(texCoord, rawDepth)) : 1e6;
    // Stop both the volume AND its bloom from leaking through a foreground wall.
    if (bhTiming.z <= .001 || sceneDistance < max(0.0, front)) { fragColor = scene; return; }
    // Preserve Image's pixel-centre sampling. Scene matches the output viewport; the
    // separate bhResolution remains the atlas size, including when the effect is scaled.
    vec2 imageUv = gl_FragCoord.xy / vec2(textureSize(bhSceneSampler, 0));
    vec4 effect = texture(bhEffectSampler, imageUv);
    vec3 halo = GetBloom(imageUv);
    if (bhQuality.w > .5 && bhQuality.w < 3.5) { fragColor=vec4(mix(scene.rgb,effect.rgb,effect.a),scene.a);return; }
    vec3 emission = grade((effect.rgb + halo * bhR1Options.y) * (bhQuality.z / .85));
    // Small, depth-checked screen-space background deflection. The disk itself uses bent 3-D rays.
    vec4 projected = bhProjectionMatrix * vec4(bhCenterRadius.xyz, 1.0);
    vec3 background = scene.rgb;
    if (projected.w > 0.01 && bhCenterRadius.z < -bhCenterRadius.w) {
        vec2 centre = projected.xy / projected.w * .5 + .5;
        vec2 delta = texCoord - centre;
        float radius = abs(bhProjectionMatrix[1][1] * bhCenterRadius.w / projected.w) * .5;
        float aspect = abs(bhProjectionMatrix[1][1] / bhProjectionMatrix[0][0]);
        vec2 metric = delta * vec2(aspect, 1);
        float r2 = dot(metric, metric);
        float bend = min(.55, radius * radius * .38 / max(r2, .00001)) * bhTiming.z * bhR1Options.x;
        vec2 bent = texCoord - delta * bend * (1.0 - smoothstep(radius * 4.0, radius * 7.0, length(metric)));
        if (all(greaterThan(bent, vec2(0))) && all(lessThan(bent, vec2(1)))) {
            float bentDepth = texture(bhDepthSampler, bent).r;
            float bentDistance = bentDepth < .999999 ? length(unproject(bent, bentDepth)) : 1e6;
            if (bentDistance >= frontDistance(normalize(unproject(bent, 1.0)))) background = texture(bhSceneSampler, bent).rgb;
        }
    }
    vec3 attenuated = background * (1.0 - effect.a);
    // Screen blend preserves HDR glow against bright skies without clamping the whole frame white.
    fragColor = vec4(1.0 - (1.0 - attenuated) * (1.0 - emission), scene.a);
}
