#version 150

// Chromatic Accretion, adapted from XorDev's "Accretion" Shadertoy.
// The original 20 ray steps and seven turbulence layers are retained.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;
uniform vec3 CameraPos;
uniform float Time;
uniform float EffectProgress;
uniform float FormationProgress;
uniform float CollapseProgress;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec4 vertexColor;
in vec3 spherePos;

out vec4 fragColor;

const float RENDER_SPHERE_RADIUS = 1.0;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

bool intersectSphere(vec3 rayOrigin, vec3 rayDirection, float radius,
                     out float nearDistance, out float farDistance) {
    float projection = dot(rayOrigin, rayDirection);
    float offset = dot(rayOrigin, rayOrigin) - radius * radius;
    float discriminant = projection * projection - offset;
    if (discriminant < 0.0) {
        return false;
    }

    float root = sqrt(discriminant);
    nearDistance = -projection - root;
    farDistance = -projection + root;
    return true;
}

vec4 raymarchAccretion(vec3 rayDirection, float time) {
    vec4 accumulated = vec4(0.0);
    float depth = 0.0;

    for (int rayStep = 0; rayStep < 20; ++rayStep) {
        float iterator = float(rayStep + 1);
        vec3 point = depth * normalize(rayDirection) + vec3(0.1);

        point = vec3(
                atan(point.y / 0.2, point.x) * 2.0,
                point.z / 3.0,
                length(point.xy) - 5.0 - depth * 0.2);

        for (int turbulenceStep = 1; turbulenceStep <= 7; ++turbulenceStep) {
            float layer = float(turbulenceStep);
            point += sin(point.yzx * layer + time + 0.3 * iterator) / layer;
        }

        float distanceField = length(vec4(0.4 * cos(point) - 0.4, point.z));
        depth += distanceField;
        accumulated += (1.0 + cos(
                point.x + iterator * 0.4 + depth + vec4(6.0, 1.0, 2.0, 0.0)))
                / max(distanceField, 0.025);
    }

    return tanh(accumulated * accumulated / 400.0);
}

vec3 sampleRefractedScene(vec2 uv, vec2 radialDirection, vec2 tangentDirection,
                          float radialOffset, float tangentOffset, float chromaticOffset) {
    vec2 baseUv = clamp(
            uv - radialDirection * radialOffset + tangentDirection * tangentOffset,
            vec2(0.001), vec2(0.999));
    vec2 chroma = tangentDirection * chromaticOffset;
    return vec3(
            texture(DiffuseSampler, clamp(baseUv + chroma, vec2(0.001), vec2(0.999))).r,
            texture(DiffuseSampler, baseUv).g,
            texture(DiffuseSampler, clamp(baseUv - chroma, vec2(0.001), vec2(0.999))).b);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 rayOrigin = CameraPos;
    vec3 rayDirection = normalize(normalize(spherePos) - rayOrigin);
    bool cameraInside = length(rayOrigin) < RENDER_SPHERE_RADIUS - 0.001;

    float nearDistance;
    float farDistance;
    if (!intersectSphere(rayOrigin, rayDirection, RENDER_SPHERE_RADIUS,
                         nearDistance, farDistance)) {
        discard;
    }

    nearDistance = max(nearDistance, 0.0);
    if (farDistance <= nearDistance) {
        discard;
    }

    float impact = length(cross(rayOrigin, rayDirection));
    if (impact > RENDER_SPHERE_RADIUS) {
        discard;
    }

    vec4 centerClip = ProjMat * ModelViewMat * vec4(0.0, 0.0, 0.0, 1.0);
    float opaqueDepth = texture(DepthSampler, uv).r;
    float occlusionDepth = centerClip.z / centerClip.w * 0.5 + 0.5;
    if (!cameraInside) {
        vec3 occlusionPoint = rayOrigin + rayDirection * nearDistance;
        vec4 occlusionClip = ProjMat * ModelViewMat * vec4(occlusionPoint, 1.0);
        occlusionDepth = occlusionClip.z / occlusionClip.w * 0.5 + 0.5;
    }
    if (!cameraInside && opaqueDepth + 0.00001 < occlusionDepth) {
        discard;
    }

    float boundaryMask = 1.0 - smoothstep(0.78, 0.995, impact);
    if (boundaryMask <= 0.001) {
        discard;
    }

    vec2 fragmentNdc = uv * 2.0 - 1.0;
    vec2 centerNdc = centerClip.w > 0.0001
            ? centerClip.xy / centerClip.w
            : fragmentNdc;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 screenDelta = fragmentNdc - centerNdc;
    screenDelta.x *= aspect;
    vec2 radialDirection = length(screenDelta) > 0.0001
            ? normalize(screenDelta)
            : vec2(0.0);
    vec2 tangentDirection = vec2(-radialDirection.y, radialDirection.x);

    float formationFade = smoothstep(0.0, 1.0, FormationProgress);
    float collapseFlash = exp(-pow((CollapseProgress - 0.08) / 0.13, 2.0));
    float collapseFade = 1.0 - smoothstep(0.45, 1.0, CollapseProgress);
    float lifeFade = (0.10 + formationFade * 0.90) * collapseFade;

    float core = 1.0 - smoothstep(0.08, 0.34, impact);
    float ring = exp(-pow((impact - 0.40) / 0.17, 2.0));
    float outerFlow = exp(-pow((impact - 0.67) / 0.23, 2.0));
    float lensStrength = boundaryMask * lifeFade
            * (0.006 + core * 0.050 + ring * 0.028 + collapseFlash * 0.035);
    float orbit = sin(Time * 1.7 + impact * 13.0) * (ring + outerFlow * 0.35) * 0.008;
    float chromaticOffset = (0.0015 + ring * 0.0035 + collapseFlash * 0.0030) * lifeFade;

    vec3 originalScene = texture(DiffuseSampler, uv).rgb;
    vec3 refractedScene = sampleRefractedScene(
            uv, radialDirection, tangentDirection,
            lensStrength, orbit, chromaticOffset);

    vec3 marchDirection = normalize(vec3(
            rayDirection.x * 1.08,
            rayDirection.y * 1.08,
            rayDirection.z));
    vec4 accretion = raymarchAccretion(marchDirection, Time);
    float accretionLight = saturate(luminance(accretion.rgb) * 1.35 + accretion.a * 0.18);
    float sustainedPulse = 0.92 + 0.08 * sin(EffectProgress * 25.132741);
    float energy = (0.72 + formationFade * 0.72 + collapseFlash * 1.15) * sustainedPulse;
    vec3 emission = accretion.rgb * energy;
    emission += vec3(0.20, 0.42, 1.0) * outerFlow * accretionLight * 0.20;
    emission += vec3(1.0, 0.42, 0.16) * ring * accretionLight * 0.14;

    float lensMix = saturate((core * 0.72 + ring * 0.45 + outerFlow * 0.20) * lifeFade);
    vec3 effectColor = mix(originalScene, refractedScene, lensMix);
    effectColor *= 1.0 - core * lifeFade * 0.28;
    effectColor += emission * boundaryMask * lifeFade;

    float opacity = boundaryMask * lifeFade * saturate(
            0.08 + accretionLight * 0.88 + lensMix * 0.30 + collapseFlash * 0.28);
    opacity *= vertexColor.a;
    if (opacity <= 0.002) {
        discard;
    }

    // Premultiplied output pairs with ONE / ONE_MINUS_SRC_ALPHA blending.
    fragColor = vec4(effectColor * vertexColor.rgb * opacity, opacity);
}
