#version 150

uniform sampler2D VolumeSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseViewProjection;
uniform vec2 SceneSize;
uniform vec2 VolumeSize;
in vec2 texCoord;
out vec4 fragColor;

float sceneDistance(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    if (d >= 0.999999) return 100000.0;
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return length(p.xyz / p.w);
}

void main() {
    vec2 uv = gl_FragCoord.xy / SceneSize;
    float reference = sceneDistance(uv);
    float tolerance = max(0.15, reference * 0.015);
    vec2 pixel = uv * VolumeSize - 0.5;
    vec2 base = floor(pixel);
    vec2 fraction = fract(pixel);
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            vec2 offset = vec2(float(x), float(y));
            vec2 tapUv = clamp((base + offset + 0.5) / VolumeSize,
                    0.5 / VolumeSize, 1.0 - 0.5 / VolumeSize);
            float difference = abs(sceneDistance(tapUv) - reference);
            // Reject low-resolution samples from the other side of a foreground edge.
            // Sampling full-resolution depth at exactly the original ray centre keeps
            // this consistent with the volume pass and needs no third texture binding.
            float depthWeight = 1.0 - smoothstep(tolerance, tolerance * 2.0, difference);
            vec2 bilinear = mix(vec2(1.0) - fraction, fraction, offset);
            float weight = bilinear.x * bilinear.y * depthWeight;
            sum += texture(VolumeSampler, tapUv) * weight;
            weights += weight;
        }
    }
    if (weights < 0.00001) discard;
    fragColor = sum / weights;
}
