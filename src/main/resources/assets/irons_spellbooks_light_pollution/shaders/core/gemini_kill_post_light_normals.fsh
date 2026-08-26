#version 150

// Reconstructs an edge-aware camera-space normal from Minecraft's main depth
// buffer.  The positive-Z convention matches the spell-light shading pass.
uniform sampler2D DepthSampler;
uniform vec4 Params;
uniform mat4 InverseProjectionMat;

in vec2 vUv;
out vec4 fragColor;

// Sky is depth 1.0 exactly, as VanillaDI's normals.fsh tests for. The
// perspective depth curve is steep near the far plane, so a threshold that
// merely looks close to 1.0 cuts off much nearer than it appears: 0.999 is only
// about 40 blocks out at a 12-chunk render distance, which left every surface
// past that with no normal and therefore no lighting. Kept in step with
// SL_SKY_DEPTH in kill_effect_post_common.glsl.
bool slNormalDepthValid(float depth) {
    return depth < 0.99999;
}

vec3 slNormalViewPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 0.000001) {
        return vec3(0.0);
    }
    eye.xyz /= eye.w;
    return vec3(eye.x, eye.y, -eye.z);
}

void main() {
    float centerDepth = texture(DepthSampler, vUv).r;
    if (!slNormalDepthValid(centerDepth)) {
        // Alpha marks sky/no-depth pixels so later shading can reject them.
        fragColor = vec4(0.5, 0.5, 1.0, 0.0);
        return;
    }

    vec2 texel = 1.0 / max(Params.xy, vec2(1.0));
    vec2 uvRight = clamp(vUv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0));
    vec2 uvLeft = clamp(vUv - vec2(texel.x, 0.0), vec2(0.0), vec2(1.0));
    vec2 uvUp = clamp(vUv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0));
    vec2 uvDown = clamp(vUv - vec2(0.0, texel.y), vec2(0.0), vec2(1.0));

    float depthRight = texture(DepthSampler, uvRight).r;
    float depthLeft = texture(DepthSampler, uvLeft).r;
    float depthUp = texture(DepthSampler, uvUp).r;
    float depthDown = texture(DepthSampler, uvDown).r;

    // Choose the neighboring surface closest in depth on each axis.  This is
    // the edge rule used by VanillaDI and prevents normals from bleeding
    // across a block silhouette.
    bool useRight = slNormalDepthValid(depthRight)
            && (!slNormalDepthValid(depthLeft)
            || abs(depthRight - centerDepth) < abs(depthLeft - centerDepth));
    bool useUp = slNormalDepthValid(depthUp)
            && (!slNormalDepthValid(depthDown)
            || abs(depthUp - centerDepth) < abs(depthDown - centerDepth));

    vec2 uvX = useRight ? uvRight : uvLeft;
    vec2 uvY = useUp ? uvUp : uvDown;
    float depthX = useRight ? depthRight : depthLeft;
    float depthY = useUp ? depthUp : depthDown;
    vec3 center = slNormalViewPosition(vUv, centerDepth);

    if (!slNormalDepthValid(depthX) || !slNormalDepthValid(depthY)) {
        vec3 fallback = normalize(-center);
        fragColor = vec4(fallback * 0.5 + 0.5, 1.0);
        return;
    }

    vec3 tangentX = slNormalViewPosition(uvX, depthX) - center;
    vec3 tangentY = slNormalViewPosition(uvY, depthY) - center;
    vec3 normal = cross(tangentY, tangentX);
    float normalLength = length(normal);
    if (normalLength < 0.00001) {
        normal = normalize(-center);
    } else {
        normal /= normalLength;
        if (dot(normal, -center) < 0.0) {
            normal = -normal;
        }
    }

    fragColor = vec4(normal * 0.5 + 0.5, 1.0);
}
