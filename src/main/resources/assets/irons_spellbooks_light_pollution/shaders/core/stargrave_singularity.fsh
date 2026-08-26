#version 150

// Adapted from ShaderTest by CeliaClaire; see THIRD_PARTY_NOTICES.md.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;
uniform vec3 CameraPos;
uniform float Time;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec4 vertexColor;
in vec3 spherePos;

out vec4 fragColor;

const float RENDER_SPHERE_RADIUS = 1.0;

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

float gaussianRing(float x, float center, float width) {
    float delta = (x - center) / max(width, 0.0001);
    return exp(-delta * delta);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; ++i) {
        value += noise(p) * amplitude;
        p = p * 2.0 + vec2(17.2, 11.8);
        amplitude *= 0.5;
    }
    return value;
}

vec3 sampleCinematicScene(vec2 uv, vec2 radialDir, vec2 tangentDir, float radialOffset, float tangentialOffset, float chroma) {
    vec2 baseUv = clamp(uv - radialDir * radialOffset + tangentDir * tangentialOffset, vec2(0.001), vec2(0.999));
    vec2 chromaOffset = tangentDir * chroma;

    float r = texture(DiffuseSampler, clamp(baseUv + chromaOffset, vec2(0.001), vec2(0.999))).r;
    float g = texture(DiffuseSampler, baseUv).g;
    float b = texture(DiffuseSampler, clamp(baseUv - chromaOffset, vec2(0.001), vec2(0.999))).b;
    return vec3(r, g, b);
}

bool intersectSphere(vec3 ro, vec3 rd, float radius, out float tNear, out float tFar) {
    float b = dot(ro, rd);
    float c = dot(ro, ro) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) {
        return false;
    }

    h = sqrt(h);
    tNear = -b - h;
    tFar = -b + h;
    return true;
}

vec3 sampleAccretionDisk(vec3 ro, vec3 rd) {
    vec3 diskEmission = vec3(0.0);
    if (abs(rd.y) <= 0.0001) {
        return diskEmission;
    }

    float tNear;
    float tFar;
    if (!intersectSphere(ro, rd, RENDER_SPHERE_RADIUS, tNear, tFar)) {
        return diskEmission;
    }

    tNear = max(tNear, 0.0);
    if (tFar <= tNear) {
        return diskEmission;
    }

    const float MAX_DISK_HALF_THICKNESS = 0.115;
    float tDiskA = (-MAX_DISK_HALF_THICKNESS - ro.y) / rd.y;
    float tDiskB = (MAX_DISK_HALF_THICKNESS - ro.y) / rd.y;
    float diskStart = max(min(tDiskA, tDiskB), tNear);
    float diskEnd = min(max(tDiskA, tDiskB), tFar);
    if (diskEnd <= diskStart) {
        return diskEmission;
    }

    const int DISK_STEPS = 4;
    float diskStep = (diskEnd - diskStart) / float(DISK_STEPS);

    for (int i = 0; i < DISK_STEPS; ++i) {
        float t = diskStart + diskStep * (float(i) + 0.5);
        vec3 diskPoint = ro + rd * t;
        float diskRadius = length(diskPoint.xz);
        float diskAngle = atan(diskPoint.z, diskPoint.x);
        float flowTime = Time * 1.35;
        float radialT = saturate((diskRadius - 0.22) / 0.72);
        float diskHalfThickness = 0.008 + 0.072 * exp(-4.6 * radialT);

        float verticalFade = 1.0 - smoothstep(diskHalfThickness * 0.35, diskHalfThickness, abs(diskPoint.y));
        float innerEdge = smoothstep(0.22, 0.28, diskRadius);
        float outerEdge = 1.0 - smoothstep(0.66, 0.82, diskRadius);
        float innerBand = exp(-pow((diskRadius - 0.32) / 0.085, 2.0));
        float middleBand = exp(-pow((diskRadius - 0.50) / 0.13, 2.0));
        float outerBand = exp(-pow((diskRadius - 0.66) / 0.16, 2.0));
        float diskNoise = fbm(diskPoint.xz * 5.0 + vec2(flowTime * 0.15, -flowTime * 0.10));
        float widthVariation = mix(0.92, 1.08, diskNoise);
        float spiralFlow = 0.5 + 0.5 * sin(diskAngle * 5.0 - diskRadius * 18.0 - flowTime + diskNoise * 1.8);
        float streakNoise = fbm(vec2(diskAngle * 2.8 - diskRadius * 1.8 - flowTime * 0.55, diskRadius * 5.8 + flowTime * 0.35));
        float laneContrast = mix(0.88, 1.16, saturate(spiralFlow * 0.72 + streakNoise * 0.38));
        float dustLanes = 1.0 - 0.12 * smoothstep(0.45, 0.82, 1.0 - spiralFlow) * smoothstep(0.25, 0.78, streakNoise);
        float radialTailFade = 1.0 - smoothstep(0.64, 0.80, diskRadius);
        float innerRim = exp(-pow((diskRadius - 0.285) / 0.042, 2.0));
        float radialProfile = innerBand * 1.42 + middleBand * 1.15 + outerBand * 0.14 + innerRim * 0.62;
        float diskMask = innerEdge * outerEdge * verticalFade * radialProfile * widthVariation * radialTailFade * laneContrast * dustLanes;

        vec2 orbital = diskRadius > 0.0001 ? diskPoint.xz / diskRadius : vec2(1.0, 0.0);
        float beaming = pow(max(dot(orbital, normalize(vec2(0.96, 0.28))), 0.0), 4.8);
        float viewShear = 0.5 + 0.5 * dot(orbital, normalize(vec2(-0.42, 0.91)));
        float temperature = saturate((1.0 - radialT) * 0.95 + beaming * 0.38 + innerRim * 0.58 + spiralFlow * 0.10);
        vec3 hotColor = vec3(1.0, 0.93, 0.82);
        vec3 warmColor = vec3(0.98, 0.73, 0.34);
        vec3 emberColor = vec3(0.86, 0.34, 0.10);
        vec3 diskColor = mix(emberColor, warmColor, saturate(temperature * 1.05));
        diskColor = mix(diskColor, hotColor, saturate(temperature * 0.84 + diskNoise * 0.10));
        diskColor = mix(diskColor, vec3(1.0, 0.98, 0.92), innerRim * 0.35 + beaming * 0.22);
        diskColor *= mix(0.88, 1.12, viewShear);
        float bridge = smoothstep(0.20, 0.27, diskRadius) * (1.0 - smoothstep(0.35, 0.44, diskRadius)) * verticalFade;

        float emission = (0.34 + beaming * 1.50 + innerRim * 0.95 + spiralFlow * 0.08) * diskMask;
        diskEmission += diskColor * emission * diskStep * 7.6;
        diskEmission += vec3(1.0, 0.96, 0.90) * bridge * (0.85 + innerRim * 0.65) * diskStep * 1.9;
    }

    return diskEmission;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 surface = normalize(spherePos);
    vec3 ro = CameraPos;
    vec3 rd = normalize(surface - ro);
    bool cameraInside = length(ro) < (RENDER_SPHERE_RADIUS - 0.001);

    float tNear;
    float tFar;
    if (!intersectSphere(ro, rd, RENDER_SPHERE_RADIUS, tNear, tFar)) {
        discard;
    }

    tNear = max(tNear, 0.0);
    if (tFar <= tNear) {
        discard;
    }

    float impact = length(cross(ro, rd));
    if (impact > 1.0) {
        discard;
    }

    vec4 centerClip = ProjMat * ModelViewMat * vec4(0.0, 0.0, 0.0, 1.0);
    float centerDepth = centerClip.z / centerClip.w * 0.5 + 0.5;
    float opaqueDepth = texture(DepthSampler, uv).r;

    const float OCCLUSION_RADIUS = RENDER_SPHERE_RADIUS;
    float occlusionDepth = centerDepth;
    float tOccNear;
    float tOccFar;
    if (!cameraInside && intersectSphere(ro, rd, OCCLUSION_RADIUS, tOccNear, tOccFar)) {
        vec3 occlusionPoint = ro + rd * max(tOccNear, 0.0);
        vec4 occlusionClip = ProjMat * ModelViewMat * vec4(occlusionPoint, 1.0);
        occlusionDepth = occlusionClip.z / occlusionClip.w * 0.5 + 0.5;
    }

    if (!cameraInside && opaqueDepth + 0.00001 < occlusionDepth) {
        discard;
    }

    vec3 originalScene = texture(DiffuseSampler, uv).rgb;

    vec2 fragNdc = vec2(uv.x * 2.0 - 1.0, uv.y * 2.0 - 1.0);
    vec2 centerNdc = centerClip.w > 0.0001 ? centerClip.xy / centerClip.w : fragNdc;
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 screenDelta = fragNdc - centerNdc;
    screenDelta.x *= aspect;
    vec2 screenDir = length(screenDelta) > 0.0001 ? normalize(screenDelta) : vec2(0.0);
    vec2 tangentDir = vec2(-screenDir.y, screenDir.x);

    const float SHADOW_RADIUS = 0.205;
    const float SHADOW_FEATHER = 0.030;
    const float PHOTON_RING_RADIUS = 0.255;
    const float PHOTON_RING_WIDTH = 0.018;

    float shadowMask = 1.0 - smoothstep(SHADOW_RADIUS, SHADOW_RADIUS + SHADOW_FEATHER, impact);
    float photonRing = gaussianRing(impact, PHOTON_RING_RADIUS, PHOTON_RING_WIDTH);
    float innerPhotonRing = gaussianRing(impact, PHOTON_RING_RADIUS - 0.020, PHOTON_RING_WIDTH * 0.85);
    float haloRing = gaussianRing(impact, PHOTON_RING_RADIUS + 0.060, 0.060);
    float farFieldLens = 1.0 - smoothstep(0.82, 1.0, impact);
    float coreLens = pow(1.0 - smoothstep(SHADOW_RADIUS, 0.70, impact), 2.2);
    float cinematicRing = saturate(photonRing * 1.25 + innerPhotonRing * 0.55 + haloRing * 0.28);
    float outerSoftFade = 1.0 - smoothstep(0.72, 1.02, impact);
    float outerBlend = smoothstep(0.60, 0.98, impact);
    outerSoftFade *= (1.0 - outerBlend * 0.35);

    float distortionStrength =
            farFieldLens * (0.014 + 0.040 / (impact + 0.10))
            + photonRing * 0.050
            + innerPhotonRing * 0.026
            + haloRing * 0.020
            + shadowMask * 0.145;
    distortionStrength *= outerSoftFade;

    float swirlStrength = (photonRing * 0.026 + haloRing * 0.010) * sign(dot(tangentDir, vec2(0.72, 0.28)));
    float chromaStrength = 0.0015 + cinematicRing * 0.0035;

    vec3 warpedScene = sampleCinematicScene(uv, screenDir, tangentDir, distortionStrength, swirlStrength, chromaStrength);
    vec3 secondaryScene = sampleCinematicScene(
            uv,
            screenDir,
            tangentDir,
            distortionStrength * 1.42 + photonRing * 0.024,
            swirlStrength * 1.7,
            chromaStrength * 1.4
    );

    vec3 color = mix(originalScene, warpedScene, saturate((farFieldLens * 0.92 + coreLens * 0.35) * outerSoftFade));
    color = mix(color, secondaryScene, saturate((photonRing * 0.32 + innerPhotonRing * 0.16) * outerSoftFade));
    color += vec3(0.08, 0.10, 0.16) * haloRing * farFieldLens * outerSoftFade * 0.35;

    vec3 diskEmission = sampleAccretionDisk(ro, rd);

    float edgeOnView = pow(1.0 - saturate(abs(normalize(ro).y)), 1.2);
    float foldStrength = edgeOnView * saturate(photonRing * 1.15 + haloRing * 0.32 + coreLens * 0.18);
    float flattenAmount = mix(1.0, 0.26, foldStrength);
    vec3 foldedDiskEmission = vec3(0.0);
    if (foldStrength > 0.015) {
        vec3 foldedRdFront = normalize(vec3(rd.x, rd.y * flattenAmount, rd.z));
        vec3 foldedRdBack = normalize(vec3(rd.x, -rd.y * flattenAmount, rd.z));
        vec3 foldedDiskFront = sampleAccretionDisk(ro, foldedRdFront);
        vec3 foldedDiskBack = sampleAccretionDisk(ro, foldedRdBack);
        foldedDiskEmission = foldedDiskFront * (0.34 + photonRing * 0.24)
                + foldedDiskBack * (0.18 + haloRing * 0.12);
    }

    float wrapBand = gaussianRing(abs(screenDelta.y), 0.18, 0.05) * gaussianRing(impact, 0.29, 0.08) * edgeOnView;
    vec3 wrapGlow = vec3(1.0, 0.90, 0.74) * wrapBand * (0.05 + photonRing * 0.14);

    color += (diskEmission + foldedDiskEmission * foldStrength + wrapGlow) * outerSoftFade;
    vec3 ringGlowColor = mix(vec3(1.0, 0.90, 0.72), vec3(1.0, 0.98, 0.95), saturate(photonRing * 0.8 + haloRing));
    color += ringGlowColor * photonRing * outerSoftFade * 0.82;
    color += vec3(1.0, 0.82, 0.62) * innerPhotonRing * outerSoftFade * 0.28;
    color += vec3(0.95, 0.78, 0.55) * haloRing * outerSoftFade * 0.16;

    float vignette = 1.0 - smoothstep(0.45, 1.05, impact);
    color += ringGlowColor * cinematicRing * vignette * outerSoftFade * 0.10;
    color = mix(color, vec3(0.0), shadowMask);

    float boundaryBlend = smoothstep(0.62, 0.98, impact);
    boundaryBlend = 1.0 - boundaryBlend * boundaryBlend;
    color = mix(originalScene, color, boundaryBlend);

    fragColor = vec4(color * vertexColor.rgb, vertexColor.a);
}
