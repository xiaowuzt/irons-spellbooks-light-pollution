// Forge 1.20.1 GLSL port of Gemini's KillEffect post chain.
// Upstream: https://github.com/AirZone-Team/Gemini
// Upstream revision: 25db876842c10c9d98dc4089e4f9873bade6d01a
// SPDX-License-Identifier: LGPL-2.1-only
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// ═══════════════════════════════════════════════════════════════════
//  KillEffect Post-Processing — multi-pass fragment shader
//
//  Compile-time defines select the active pass:
//    BRIGHT_PASS          — luminance threshold → bloom source
//    BRIGHT_PASS_EDGE     — enhanced BRIGHT_PASS with depth-edge detection
//    BLUR_H               — separable horizontal gaussian blur
//    BLUR_V               — separable vertical gaussian blur
//    DISTORTION           — screen-space heat distortion
//    GODRAY               — screen-space radial blur from effect centers
//    VOLUMETRIC_GODRAY    — ray-marched volumetric god rays (depth-aware)
//    CHROMATIC            — RGB channel separation (chromatic aberration)
//    SCREEN_LIGHTING      — pseudo ray-traced point light: N·L + specular
//                           with depth-buffer shadow rays (blocks/entities occlude)
//    SSRT                 — screen-space ray-traced reflections
//    BLACK_HOLE           — Screen-space black hole center: event horizon, photon
//                           ring (HDR), accretion disk (ray-marched), gravitational
//                           lensing, photon sphere glow, alpha=1 override
//    GLOW_FLASH           — Supernova pre-flash: bright pulsing central spot
//    SHOCKWAVE            — Expanding concentric shock rings
//    FLASH_SCREEN         — Full-screen white flash overlay
//    AFTERIMAGE           — Temporal motion trail blending
//    ACES                 — ACES filmic tone mapping (Narkowicz 2015 fit)
//
//  Uniforms (PostUniforms, std140, 160 bytes = 10 × vec4):
//    vec4 Params:       x=fbWidth, y=fbHeight, z=bloomStrength, w=threshold
//    vec4 TimePack:     x=time(sec), y=frameIndex, zw=unused
//    vec4 Center1:      xy=effect center NDC (-1..1), z=worldDist, w=unused
//    vec4 Center2:      xy=secondary center NDC, zw=unused
//    vec4 PassParams:   x=distortionStrength, y=godRayStrength,
//                       z=chromaticStrength, w=bloomRadius
//    vec4 BHParams:     x=bhRadiusUV, y=stage, z=progress, w=intensity
//    vec4 CameraParams: x=FOV(rad), y=aspect, z=near, w=far
//    vec4 LightViewPos: xyz=light pos (view space), w=radius
//    vec4 LightColor:   rgb=light color, a=intensity
//    vec4 MiscParams:   x=ssrIntensity, y=volumetricSteps, z=chainFade, w=unused
// ═══════════════════════════════════════════════════════════════════

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;
uniform sampler2D LightDataSampler;

uniform vec4 Params;
uniform vec4 TimePack;
uniform vec4 Center1;
uniform vec4 Center2;
uniform vec4 PassParams;
uniform vec4 BHParams;
uniform vec4 CameraParams;
uniform vec4 LightViewPos;
uniform vec4 LightColor;
uniform vec4 LightViewPos0;
uniform vec4 LightViewPos1;
uniform vec4 LightViewPos2;
uniform vec4 LightColor0;
uniform vec4 LightColor1;
uniform vec4 LightColor2;
uniform vec4 LightCenter0;
uniform vec4 LightCenter1;
uniform vec4 LightCenter2;
uniform vec4 MiscParams;
// x=packed texture width, y=height, z=active light count, w=history valid
uniform vec4 LightDataParams;

in vec2 vUv;
out vec4 fragColor;

// ── Luminance ─────────────────────────────────────────────────────

float luminance(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

// Depth at or above this counts as "no surface here", i.e. sky.
//
// Minecraft clears the depth buffer to exactly 1.0 and VanillaDI tests for that
// exact value. The perspective depth curve is extremely non-linear near the far
// plane, so a threshold that looks close to 1.0 is not: with Minecraft's 0.05
// near plane and a 12-chunk render distance, 0.999 is only ~40 blocks from the
// camera. Treating everything past 40 blocks as sky is what made the lit pool
// end at a straight horizontal line on flat ground, lose more of itself the
// further the camera backed away, and change shape when the view pitched.
// 0.99999 lands past 90% of the far plane at every render distance.
#define SL_SKY_DEPTH 0.99999

// ── ACES (Narkowicz 2015) ────────────────────────────────────────

vec3 aces(vec3 x) {
    float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// ── Shared hash ──────────────────────────────────────────────────

float postHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// ── Gravitational lensing helpers ─────────────────────────────────
// Shared by the black hole and the Starless void. Both bend the scene around
// a point, so the deflection model lives here rather than in one section.
float bhHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float bhNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(bhHash(i), bhHash(i + vec2(1.0, 0.0)), f.x),
               mix(bhHash(i + vec2(0.0, 1.0)), bhHash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float bhFbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * bhNoise(p);
        p *= 2.1;
        a *= 0.5;
    }
    return v;
}

// ══════════════════════════════════════════════════════════════
//  Screen-space gravitational lensing
//
//  Warps UV coordinates toward the black hole center, with
//  deflection strongest near the photon sphere and falling
//  off as 1/b².  Produces the characteristic "einstein ring"
//  distortion of the background scene.
// ══════════════════════════════════════════════════════════════

vec2 bhGravLens(vec2 uv, vec2 center, float rs, vec2 aspect) {
    vec2 delta = uv - center;
    vec2 ad = delta * aspect;          // aspect-corrected
    float d = length(ad);
    if (d < 0.001) return uv;

    float b = d;                       // impact parameter
    // Weak-field deflection: α ∝ Rs / b² (softened at center)
    float alpha = rs * 0.15 / (b * b + rs * rs * 0.08);

    // Critical boost at photon sphere (r ≈ 1.3 Rs in UV space)
    // Dramatically enhanced for Interstellar-like background warping
    float rPhoton = rs * 1.3;
    float distToPhoton = abs(b - rPhoton);
    alpha *= 1.0 + exp(-distToPhoton * 25.0 / max(rs, 0.01)) * 6.0;

    // Clamp to avoid tearing at extreme angles
    alpha = min(alpha, 0.55);

    vec2 dir = delta / max(d, 0.001);
    return uv + dir * alpha / aspect;
}

// ══════════════════════════════════════════════════════════════
//  Accretion disk — Keplerian rotation + doppler beaming
//
//  Geometrically thin, optically thick disk in normalized radius
//  units (n = r / Rs;  event horizon at n = 1):
//    - Emissivity peaks just outside the ISCO (n ≈ 1.6)
//    - Differential rotation: ω ∝ n^-1.5 (inner disk orbits faster)
//    - Relativistic doppler beaming: approaching side brighter/bluer
//    - Blackbody temperature ramp: blue-white inner → red outer
// ══════════════════════════════════════════════════════════════

// VanillaDI keeps the light buffer in a compact HDR representation through
// shade, temporal and all spatial passes.  These helpers are shared by every
// light pass; keeping them outside SCREEN_LIGHTING is required because the
// denoising and blend shaders are compiled as independent variants.
vec4 slEncodeHdr(vec3 color) {
    float maximum = min(max(color.r, max(color.g, color.b)), 255.0);
    if (maximum <= 0.0) return vec4(0.0);
    if (maximum < 1.0) return vec4(max(color, vec3(0.0)), 1.0);
    return vec4(max(color, vec3(0.0)) / maximum, 1.0 / maximum);
}

vec3 slDecodeHdr(vec4 color) {
    if (color.a <= 0.000001) return vec3(0.0);
    return max(color.rgb, vec3(0.0)) / color.a;
}

// ══════════════════════════════════════════════════════════════════
//  Pass 1: Bright Extract (luminance threshold with soft knee)
//  In:  SceneSampler (scene)
//  Out: fragColor (bright pixels only)
// ══════════════════════════════════════════════════════════════════

#ifdef BRIGHT_PASS

void main() {
    vec3 color = texture(SceneSampler, vUv).rgb;
    float lum = luminance(color);
    float t = Params.w;            // threshold
    float knee = 0.08;

    // Soft knee around threshold
    float w = clamp((lum - t + knee) / (2.0 * knee), 0.0, 1.0);
    float bright = w * max(lum - t, 0.0) / max(lum, 0.001);

    fragColor = vec4(color * bright, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 1b: Bright Extract (depth-edge enhanced)
//  In:  SceneSampler (scene), DepthSampler (depth)
//  Out: fragColor (bright pixels + edge-enhanced regions)
//
//  Extends BRIGHT_PASS with depth discontinuity detection.
//  Objects near the light source get edge glow even if their
//  raw luminance is below threshold, simulating rim lighting
//  from the glow sphere.
// ══════════════════════════════════════════════════════════════════

#ifdef BRIGHT_PASS_EDGE

uniform sampler2D DepthSampler;

void main() {
    vec3 color = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;

    float lum = luminance(color);
    float t = Params.w;
    float knee = 0.08;

    // Standard soft-knee threshold
    float w = clamp((lum - t + knee) / (2.0 * knee), 0.0, 1.0);
    float bright = w * max(lum - t, 0.0) / max(lum, 0.001);

    // ── Depth edge detection (Sobel 3×3) ────────────────────────
    vec2 ts = vec2(1.0 / Params.x, 1.0 / Params.y);

    float dTL = texture(DepthSampler, vUv + vec2(-ts.x,  ts.y)).r;
    float dT  = texture(DepthSampler, vUv + vec2( 0.0,   ts.y)).r;
    float dTR = texture(DepthSampler, vUv + vec2( ts.x,  ts.y)).r;
    float dL  = texture(DepthSampler, vUv + vec2(-ts.x,   0.0)).r;
    float dR  = texture(DepthSampler, vUv + vec2( ts.x,   0.0)).r;
    float dBL = texture(DepthSampler, vUv + vec2(-ts.x,  -ts.y)).r;
    float dB  = texture(DepthSampler, vUv + vec2( 0.0,  -ts.y)).r;
    float dBR = texture(DepthSampler, vUv + vec2( ts.x,  -ts.y)).r;

    float gx = -dTL - 2.0*dL - dBL + dTR + 2.0*dR + dBR;
    float gy = -dTL - 2.0*dT - dTR + dBL + 2.0*dB + dBR;
    float edge = sqrt(gx*gx + gy*gy);

    // ── Proximity to light source in screen space ────────────────
    vec2 lightNDC = Center1.xy;  // already NDC [-1,1]
    vec2 ndc = vUv * 2.0 - 1.0;
    float distToLight = length(ndc - lightNDC);
    float proximity = exp(-distToLight * 2.8);

    // ── Edge boost ──────────────────────────────────────────────
    float edgeBoost = edge * 3.5 * proximity;
    float enhancedBright = bright + edgeBoost * 0.25;

    // Fade edge boost by depth (distant edges produce less glow)
    float depthFade = 1.0 - smoothstep(0.85, 1.0, depth);
    enhancedBright += edge * 1.5 * proximity * depthFade * 0.1;

    enhancedBright = clamp(enhancedBright, 0.0, 3.0);

    // Subtle blue shift at edges (Fresnel-like)
    vec3 edgeColor = vec3(0.4, 0.6, 1.0);
    color = mix(color, color + edgeColor * 0.08, edge * proximity * 0.4 * depthFade);

    fragColor = vec4(color * enhancedBright, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 2a: Horizontal Gaussian Blur (separable, linear-sampled)
//  In:  SceneSampler (previous pass output)
//  Out: fragColor (horizontally blurred)
// ══════════════════════════════════════════════════════════════════

#ifdef BLUR_H

void main() {
    vec2 texelSize = vec2(1.0 / Params.x, 0.0);   // horizontal only
    float radius = PassParams.w * 0.5;              // bloom radius in pixels
    float sigma = max(radius, 0.5);

    vec3 color = vec3(0.0);
    float weightSum = 0.0;

    // 9-tap gaussian (radius=4px → sigma≈1.7): [-4σ, +4σ]
    int taps = int(ceil(sigma * 3.0));
    taps = clamp(taps, 3, 8);

    for (int i = -8; i <= 8; i++) {
        if (abs(i) > taps) continue;
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        color += texture(SceneSampler, vUv + texelSize * float(i)).rgb * w;
        weightSum += w;
    }
    fragColor = vec4(color / weightSum, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 2b: Vertical Gaussian Blur (separable, linear-sampled)
//  In:  SceneSampler (horizontally blurred output)
//  Out: fragColor (fully blurred bloom buffer)
// ══════════════════════════════════════════════════════════════════

#ifdef BLUR_V

void main() {
    vec2 texelSize = vec2(0.0, 1.0 / Params.y);   // vertical only
    float radius = PassParams.w * 0.5;
    float sigma = max(radius, 0.5);

    vec3 color = vec3(0.0);
    float weightSum = 0.0;

    int taps = int(ceil(sigma * 3.0));
    taps = clamp(taps, 3, 8);

    for (int i = -8; i <= 8; i++) {
        if (abs(i) > taps) continue;
        float w = exp(-float(i * i) / (2.0 * sigma * sigma));
        color += texture(SceneSampler, vUv + texelSize * float(i)).rgb * w;
        weightSum += w;
    }
    fragColor = vec4(color / weightSum, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 3: Screen-space Heat Distortion
//  In:  SceneSampler (scene), BloomSampler (depth/silhouette ref)
//  Out: fragColor (distorted scene)
//
//  Offsets UVs radially away from the effect center, creating a
//  heat-haze / gravitational lensing look in screen space.
// ══════════════════════════════════════════════════════════════════

#ifdef DISTORTION

float hashDistort(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noiseDistort(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hashDistort(i), hashDistort(i + vec2(1,0)), f.x),
               mix(hashDistort(i + vec2(0,1)), hashDistort(i + vec2(1,1)), f.x), f.y);
}

void main() {
    float strength = PassParams.x * 0.04;     // max ~4% screen offset
    vec2 center = Center1.xy * 0.5 + 0.5;     // NDC [-1,1] → UV [0,1]

    // Direction from effect center
    vec2 dir = vUv - center;
    float dist = length(dir);

    // Distortion falls off with 1/distance
    float distort = strength / (dist + 0.05);

    // Noise-based wobble (prevents sterile look)
    float n = noiseDistort(vUv * 80.0 + TimePack.x * 0.5) * 0.6;
    distort *= (0.7 + n);

    // Clamp maximum distortion
    distort = min(distort, 0.08);

    vec2 offsetUv = vUv + normalize(dir + 0.001) * distort;

    // Fade distortion near screen edges
    float edgeFade = 1.0 - smoothstep(0.75, 1.0, length(vUv - 0.5) * 2.0);

    vec3 distorted = texture(SceneSampler, offsetUv).rgb;
    vec3 original  = texture(SceneSampler, vUv).rgb;

    fragColor = vec4(mix(original, distorted, edgeFade), 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 4: God Rays (screen-space radial blur from effect centers)
//  In:  SceneSampler (HDR scene or bloom buffer)
//  Out: fragColor (scene + god rays)
//
//  Radially samples toward effect centers, accumulating bright
//  areas into volumetric light beams.
// ══════════════════════════════════════════════════════════════════

#ifdef GODRAY

void main() {
    float strength = PassParams.y;
    vec2 center1 = Center1.xy * 0.5 + 0.5;    // NDC → UV
    vec2 center2 = Center2.xy * 0.5 + 0.5;

    vec3 rays = vec3(0.0);
    float weightSum = 0.0;
    int samples = 32;

    // God rays from center1 (primary: black hole / hypernova)
    vec2 toCenter1 = center1 - vUv;
    float dist1 = length(toCenter1);
    vec2 dir1 = toCenter1 / max(dist1, 0.001);
    float decay1 = exp(-dist1 * 1.5);

    for (int i = 0; i < samples; i++) {
        float t = (float(i) + 0.5) / float(samples);
        vec2 suv = vUv + dir1 * t * dist1;
        float w = exp(-t * 3.0);              // attenuation along ray
        rays += texture(SceneSampler, suv).rgb * w;
        weightSum += w;
    }
    rays /= max(weightSum, 0.001);
    rays *= decay1 * strength;

    // God rays from center2 (secondary effect center)
    vec2 toCenter2 = center2 - vUv;
    float dist2 = length(toCenter2);
    if (dist2 < 2.0 && strength > 0.01) {
        vec2 dir2 = toCenter2 / max(dist2, 0.001);
        float decay2 = exp(-dist2 * 2.0);
        vec3 rays2 = vec3(0.0);
        float ws2 = 0.0;
        for (int i = 0; i < 16; i++) {
            float t = (float(i) + 0.5) / 16.0;
            vec2 suv = vUv + dir2 * t * dist2;
            float w = exp(-t * 3.0);
            rays2 += texture(SceneSampler, suv).rgb * w;
            ws2 += w;
        }
        rays2 /= max(ws2, 0.001);
        rays += rays2 * decay2 * strength * 0.6;
    }

    vec3 scene = texture(SceneSampler, vUv).rgb;
    fragColor = vec4(scene + rays, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 4b: Volumetric God Rays (ray-marched, depth-aware)
//  In:  SceneSampler (composited HDR scene), DepthSampler (depth)
//  Out: fragColor (scene + volumetric light shafts)
//
//  Replaces screen-space radial blur with true ray-marching
//  along the view direction toward the 3D light position.
//  Density accumulates via Beer's law through a spherical
//  volume centered on the light source.  Depth buffer provides
//  correct integration bounds — rays terminate at surfaces.
//
//  Algorithm:
//    1. Reconstruct view-space position from depth + UV
//    2. Step along the ray from camera toward the light source
//    3. At each step: compute density (Gaussian around light sphere)
//    4. Accumulate in-scattered light × phase function × transmittance
//    5. Composite result over the scene
//
//  Performance: 16 steps (configurable via MiscParams.y)
// ══════════════════════════════════════════════════════════════════

#ifdef VOLUMETRIC_GODRAY

uniform sampler2D DepthSampler;

// ── Reconstruct view-space position from depth + UV ──────────────
// Returns position in camera-basis view space:
//   X = camera right, Y = camera up, Z = camera forward (positive)
vec3 viewPosFromDepth(vec2 uv, float depth) {
    float fov   = CameraParams.x;
    float aspect = CameraParams.y;
    float near  = CameraParams.z;
    float far   = CameraParams.w;

    // NDC [-1,1]
    float ndcX = uv.x * 2.0 - 1.0;
    float ndcY = uv.y * 2.0 - 1.0;
    float ndcZ = depth * 2.0 - 1.0;

    // Reconstruct linear view-space Z (camera-forward distance)
    float viewZ = 2.0 * far * near / ((far + near) - ndcZ * (far - near));

    float halfFovTan = tan(fov * 0.5);
    float viewX = ndcX * viewZ * aspect * halfFovTan;
    float viewY = ndcY * viewZ * halfFovTan;

    return vec3(viewX, viewY, viewZ);
}

// Henyey-Greenstein phase function (forward-scattering bias)
float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    float denom = 1.0 + g2 - 2.0 * g * cosTheta;
    return (1.0 - g2) / (4.0 * 3.14159265 * denom * sqrt(denom));
}

void main() {
    float strength = PassParams.y;
    vec3 scene = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;

    if (strength < 0.01) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Reconstruct view-space position ──────────────────────────
    vec3 viewPos = viewPosFromDepth(vUv, depth);

    // ── Light position (view space) ─────────────────────────────
    vec3 lightPos = LightViewPos.xyz;
    float lightRadius = LightViewPos.w;

    vec3 toLight = lightPos - viewPos;
    float distToLight = length(toLight);
    vec3 lightDir = toLight / max(distToLight, 0.001);

    if (distToLight > lightRadius * 4.0) {
        // Too far from light source — skip for performance
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Ray march ───────────────────────────────────────────────
    int steps = int(MiscParams.y);
    steps = clamp(steps, 8, 32);
    float stepSize = distToLight / float(steps);

    // Jitter start position to reduce banding
    float jitter = postHash(vUv + fract(TimePack.x)) * stepSize;
    float t = jitter;

    vec3 accumulated = vec3(0.0);
    float transmittance = 1.0;

    for (int i = 0; i < 32; i++) {
        if (i >= steps) break;

        vec3 samplePos = viewPos + lightDir * t;
        float d = length(lightPos - samplePos);

        // Density: Gaussian peak at light center, zero outside radius
        float density = exp(-d * d / (lightRadius * lightRadius * 0.25));

        if (density > 0.002) {
            // Beer's law absorption + out-scattering
            float absorption = density * stepSize * 0.12;
            transmittance *= exp(-absorption);

            // Phase function (forward-scattering g=0.65)
            float cosTheta = dot(normalize(-viewPos), lightDir);
            float phase = phaseHG(cosTheta, 0.65);

            // In-scattered radiance from light source
            vec3 lightContrib = LightColor.rgb * LightColor.w
                / (1.0 + d * d * 0.0008);

            float scatterCoeff = density * stepSize * phase * transmittance * 0.025;
            accumulated += lightContrib * scatterCoeff;
        }

        t += stepSize;
        if (t > distToLight) break;
    }

    // ── Composite ───────────────────────────────────────────────
    vec3 godRays = accumulated * strength;

    // Warmer color near the light source
    float glowProximity = exp(-distToLight / (lightRadius * 1.5));
    godRays = mix(godRays, godRays * LightColor.rgb, glowProximity * 0.4);

    // Screen-blend with scene (1 - (1-scene)(1-godRays) ≈ scene + godRays)
    fragColor = vec4(scene + godRays, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 5: Chromatic Aberration
//  In:  SceneSampler
//  Out: fragColor (RGB-separated)
//
//  Offsets R and B channels radially from screen center.
// ══════════════════════════════════════════════════════════════════

#ifdef CHROMATIC

void main() {
    float strength = PassParams.z * 0.008;     // max ~0.8% offset
    vec2 center = vec2(0.5);                    // screen center
    vec2 dir = normalize(vUv - center + 0.001);
    float dist = length(vUv - center);
    float scale = strength * dist;

    float r = texture(SceneSampler, vUv + dir * scale).r;
    float g = texture(SceneSampler, vUv).g;
    float b = texture(SceneSampler, vUv - dir * scale).b;

    fragColor = vec4(r, g, b, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 5b: Screen-Space Dynamic Lighting (pseudo ray-traced)
//  In:  SceneSampler (composited HDR scene), DepthSampler (depth)
//  Out: fragColor (scene with dynamic light contribution)
//
//  The explosion's residual light source illuminates nearby blocks and
//  entities WITHOUT touching Minecraft's lightmap or world state —
//  this is a pure post-processing overlay.
//
//  Per pixel:
//    1. Reconstruct view-space position + screen-space normal from depth
//    2. N·L wrap diffuse + Blinn-Phong specular, inverse-square falloff
//    3. SHADOW RAY MARCH (pseudo ray tracing): step from the surface
//       point toward the light, projecting each step back to screen
//       space and comparing against the depth buffer.  Any blocker —
//       blocks OR entities (both write depth) — shadows the point.
//    4. Small unshadowed ambient term so fully shadowed areas still
//      receive a faint bounce.
//
//  Budget: 1 depth + 4 normal taps, plus a conservative 10-sample entity
//  fallback only when the voxel ray reports no block occluder.
// ══════════════════════════════════════════════════════════════════

#ifdef SCREEN_LIGHTING

uniform sampler2D DepthSampler;
uniform sampler2D NormalSampler;
uniform sampler2D VoxelSampler;
uniform sampler2D VoxelLodSampler;
uniform mat4 ProjectionMat;
uniform mat4 InverseProjectionMat;
uniform mat4 InverseViewMat;
uniform mat4 InverseViewProjectionMat;
uniform vec4 CameraWorldPos;
uniform vec4 VoxelOrigin;

// VanillaDI's cache is a 64^3 block grid. Each block owns 16 RGBA8 texels
// encoding an 8^3 occupancy field: eight X bits per channel, four Y rows per
// texel, and two texels for each Z plane. The CPU cache uses this exact layout.
bool slVoxelBlockInBounds(ivec3 block) {
    return all(greaterThanEqual(block, ivec3(0)))
            && all(lessThan(block, ivec3(64)));
}

int slVoxelBlockId(ivec3 block) {
    return block.x + block.y * 64 + block.z * 4096;
}

bool slBlockOccupied(ivec3 block) {
    if (!slVoxelBlockInBounds(block)) {
        return false;
    }
    int blockId = slVoxelBlockId(block);
    vec4 lod = texelFetch(VoxelLodSampler,
            ivec2(blockId % 128, blockId / 128), 0);
    return any(greaterThan(lod, vec4(0.001)));
}

bool slSubVoxelOccupied(ivec3 block, ivec3 subVoxel) {
    if (!slVoxelBlockInBounds(block)
            || any(lessThan(subVoxel, ivec3(0)))
            || any(greaterThanEqual(subVoxel, ivec3(8)))) {
        return false;
    }

    int blockId = slVoxelBlockId(block);
    int slice = subVoxel.z * 2 + subVoxel.y / 4;
    ivec2 coord = ivec2((blockId % 128) * 16 + slice, blockId / 128);
    vec4 voxelBits = texelFetch(VoxelSampler, coord, 0);
    int channel = subVoxel.y % 4;
    float channelValue = channel == 0 ? voxelBits.r
            : channel == 1 ? voxelBits.g
            : channel == 2 ? voxelBits.b
            : voxelBits.a;
    int rowBits = int(floor(channelValue * 255.0 + 0.5));
    return (rowBits & (1 << subVoxel.x)) != 0;
}

// Point query used only to classify a depth-buffer hit.  The block DDA above
// remains the authoritative block shadow test; this helper answers a narrower
// question: does the reconstructed depth point belong to cached world
// geometry?  Keeping that distinction is important for fences and trapdoors:
// a screen-space ray may land on a nearby bar even when the actual light ray
// passes through its hole.
bool slVoxelPointOccupiedExact(vec3 worldPosition) {
    if (VoxelOrigin.w < 0.5) {
        return false;
    }
    vec3 local = worldPosition - VoxelOrigin.xyz;
    if (any(lessThan(local, vec3(0.0)))
            || any(greaterThanEqual(local, vec3(64.0)))) {
        return false;
    }
    ivec3 block = ivec3(floor(local));
    if (!slBlockOccupied(block)) {
        return false;
    }
    vec3 fraction = fract(local);
    ivec3 subVoxel = ivec3(floor(fraction * 8.0));
    return slSubVoxelOccupied(block, subVoxel);
}

bool slVoxelPointInBounds(vec3 worldPosition) {
    if (VoxelOrigin.w < 0.5) {
        return false;
    }
    vec3 local = worldPosition - VoxelOrigin.xyz;
    return all(greaterThanEqual(local, vec3(0.0)))
            && all(lessThan(local, vec3(64.0)));
}

bool slVoxelPointOccupiedNear(vec3 worldPosition) {
    if (slVoxelPointOccupiedExact(worldPosition)) {
        return true;
    }

    // Depth reconstruction can put a surface exactly on the outside face of
    // a subvoxel. Probe one half subvoxel inward on each axis so a fence bar
    // is still recognized as block geometry without making a large opaque
    // neighbourhood around it.
    const float probe = 0.0625;
    return slVoxelPointOccupiedExact(worldPosition + vec3(probe, 0.0, 0.0))
            || slVoxelPointOccupiedExact(worldPosition - vec3(probe, 0.0, 0.0))
            || slVoxelPointOccupiedExact(worldPosition + vec3(0.0, probe, 0.0))
            || slVoxelPointOccupiedExact(worldPosition - vec3(0.0, probe, 0.0))
            || slVoxelPointOccupiedExact(worldPosition + vec3(0.0, 0.0, probe))
            || slVoxelPointOccupiedExact(worldPosition - vec3(0.0, 0.0, probe));
}

vec3 slWorldRelativeFromView(vec3 viewPos) {
    // The lighting pass uses camera-relative OpenGL eye coordinates, with +Z
    // pointing away from the camera.  InverseViewMat contains a translation
    // as well as the camera rotation; use w=0 so that translation is not added
    // here, then add the authoritative camera world position exactly once.
    // Going through inverse(viewProjection) * projection accidentally applies
    // that translation and causes a second camera offset in the voxel grid.
    vec3 relative = (InverseViewMat
            * vec4(viewPos.x, viewPos.y, -viewPos.z, 0.0)).xyz;
    return relative + CameraWorldPos.xyz;
}

// Trace the 8^3 field inside one occupied block. blockEntry is already inside
// the block and blockDistance is the remaining distance until its outer DDA
// boundary. This preserves narrow bar and trapdoor geometry instead of
// treating every non-air block as a solid cube.
bool slTraceBlockSubVoxels(ivec3 block, vec3 blockEntry, vec3 direction,
        float blockDistance) {
    if (blockDistance <= 0.00001) {
        return false;
    }

    vec3 subPosition = (blockEntry - vec3(block)) * 8.0;
    subPosition = clamp(subPosition, vec3(0.0001), vec3(7.9999));
    ivec3 subVoxel = ivec3(floor(subPosition));
    ivec3 stepDirection = ivec3(sign(direction));
    vec3 reciprocalDirection = 1.0
            / max(abs(direction) * 8.0, vec3(0.00001));
    vec3 sideDistance = vec3(
            direction.x >= 0.0
                    ? (float(subVoxel.x) + 1.0 - subPosition.x)
                            * reciprocalDirection.x
                    : (subPosition.x - float(subVoxel.x))
                            * reciprocalDirection.x,
            direction.y >= 0.0
                    ? (float(subVoxel.y) + 1.0 - subPosition.y)
                            * reciprocalDirection.y
                    : (subPosition.y - float(subVoxel.y))
                            * reciprocalDirection.y,
            direction.z >= 0.0
                    ? (float(subVoxel.z) + 1.0 - subPosition.z)
                            * reciprocalDirection.z
                    : (subPosition.z - float(subVoxel.z))
                            * reciprocalDirection.z);

    // A line can cross at most 22 cells in an 8^3 cube; 24 also covers ties.
    for (int stepIndex = 0; stepIndex < 24; stepIndex++) {
        if (slSubVoxelOccupied(block, subVoxel)) {
            return true;
        }

        float nextDistance = min(sideDistance.x,
                min(sideDistance.y, sideDistance.z));
        if (nextDistance >= blockDistance - 0.0001) {
            return false;
        }

        // Advance all axes crossed at the same boundary. This avoids testing
        // an unrelated corner-adjacent voxel when a ray runs on a diagonal.
        if (sideDistance.x <= nextDistance + 0.000001) {
            subVoxel.x += stepDirection.x;
            sideDistance.x += reciprocalDirection.x;
        }
        if (sideDistance.y <= nextDistance + 0.000001) {
            subVoxel.y += stepDirection.y;
            sideDistance.y += reciprocalDirection.y;
        }
        if (sideDistance.z <= nextDistance + 0.000001) {
            subVoxel.z += stepDirection.z;
            sideDistance.z += reciprocalDirection.z;
        }
        if (any(lessThan(subVoxel, ivec3(0)))
                || any(greaterThanEqual(subVoxel, ivec3(8)))) {
            return false;
        }
    }
    return false;
}

bool slVoxelOccluded(vec3 viewPos, vec3 surfaceNormal, vec3 lightPos) {
    // Do not sample the target while the CPU cache is still being built.
    if (VoxelOrigin.w < 0.5) {
        return false;
    }

    vec3 surface = slWorldRelativeFromView(viewPos);
    vec3 target = slWorldRelativeFromView(lightPos);
    vec3 ray = target - surface;
    float distanceToLight = length(ray);
    if (distanceToLight <= 0.12) {
        return false;
    }

    vec3 direction = ray / distanceToLight;
    vec3 normalWorld = slWorldRelativeFromView(viewPos + surfaceNormal)
            - surface;
    if (length(normalWorld) > 0.0001) {
        normalWorld = normalize(normalWorld);
    } else {
        normalWorld = vec3(0.0);
    }

    // Offset only enough to escape the receiver's own subvoxel. The ray then
    // terminates shortly before the emitter, so a block containing the light
    // does not shadow the source itself.
    vec3 rayStart = surface + normalWorld * 0.035 + direction * 0.035
            - VoxelOrigin.xyz;
    float maxRayDistance = distanceToLight - 0.07;
    if (maxRayDistance <= 0.0001) {
        return false;
    }

    // Clip the segment against the camera-local 64^3 volume before starting
    // the block DDA.  A receiver or emitter may be outside the cache while
    // the segment between them still crosses loaded geometry.  The previous
    // implementation returned immediately when the first point was outside,
    // which leaked light through off-screen/edge-of-grid walls.
    vec3 safeDirection = vec3(
            abs(direction.x) < 0.00001 ? (direction.x < 0.0 ? -0.00001 : 0.00001) : direction.x,
            abs(direction.y) < 0.00001 ? (direction.y < 0.0 ? -0.00001 : 0.00001) : direction.y,
            abs(direction.z) < 0.00001 ? (direction.z < 0.0 ? -0.00001 : 0.00001) : direction.z);
    vec3 invDirection = 1.0 / safeDirection;
    vec3 t0 = (vec3(0.0) - rayStart) * invDirection;
    vec3 t1 = (vec3(64.0) - rayStart) * invDirection;
    vec3 tMin = min(t0, t1);
    vec3 tMax = max(t0, t1);
    float enterDistance = max(0.0, max(tMin.x, max(tMin.y, tMin.z)));
    float exitDistance = min(maxRayDistance, min(tMax.x, min(tMax.y, tMax.z)));
    if (exitDistance <= enterDistance + 0.0001) {
        return false;
    }

    // Move a tiny distance into the volume so an exact grid boundary does not
    // make the negative-direction DDA immediately cross the wrong cell.
    float entryNudge = min(0.001, (exitDistance - enterDistance) * 0.25);
    enterDistance += entryNudge;
    if (exitDistance <= enterDistance + 0.0001) {
        return false;
    }
    vec3 gridOrigin = rayStart + direction * enterDistance;
    float rayLength = exitDistance - enterDistance;

    ivec3 block = ivec3(floor(gridOrigin));
    if (!slVoxelBlockInBounds(block)) {
        return false;
    }

    ivec3 stepDirection = ivec3(sign(direction));
    vec3 reciprocalDirection = 1.0 / max(abs(direction), vec3(0.00001));
    vec3 blockFraction = gridOrigin - floor(gridOrigin);
    vec3 sideDistance = vec3(
            direction.x >= 0.0 ? (1.0 - blockFraction.x)
                    * reciprocalDirection.x : blockFraction.x
                    * reciprocalDirection.x,
            direction.y >= 0.0 ? (1.0 - blockFraction.y)
                    * reciprocalDirection.y : blockFraction.y
                    * reciprocalDirection.y,
            direction.z >= 0.0 ? (1.0 - blockFraction.z)
                    * reciprocalDirection.z : blockFraction.z
                    * reciprocalDirection.z);
    float blockStart = 0.0;

    // A 64^3 grid can be crossed diagonally in fewer than 192 block steps.
    for (int blockStep = 0; blockStep < 192; blockStep++) {
        if (!slVoxelBlockInBounds(block) || blockStart >= rayLength) {
            return false;
        }

        float blockExit = min(sideDistance.x,
                min(sideDistance.y, sideDistance.z));
        float blockDistance = min(blockExit, rayLength) - blockStart;
        if (slBlockOccupied(block) && blockDistance > 0.0001) {
            float entryBias = min(0.0005, blockDistance * 0.5);
            if (slTraceBlockSubVoxels(block,
                    gridOrigin + direction * (blockStart + entryBias),
                    direction, blockDistance - entryBias)) {
                return true;
            }
        }

        if (blockExit >= rayLength - 0.0001) {
            return false;
        }

        // Keep tied boundary crossings together, matching the subvoxel DDA.
        if (sideDistance.x <= blockExit + 0.000001) {
            block.x += stepDirection.x;
            sideDistance.x += reciprocalDirection.x;
        }
        if (sideDistance.y <= blockExit + 0.000001) {
            block.y += stepDirection.y;
            sideDistance.y += reciprocalDirection.y;
        }
        if (sideDistance.z <= blockExit + 0.000001) {
            block.z += stepDirection.z;
            sideDistance.z += reciprocalDirection.z;
        }
        blockStart = blockExit;
    }
    return false;
}

// Reconstruct the lighting view from the exact projection matrix Minecraft
// used when it wrote the depth buffer. The lighting convention keeps forward
// as +Z, while OpenGL eye space is forward -Z.
vec3 slViewPosFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 0.000001) {
        return vec3(0.0);
    }
    eye.xyz /= eye.w;
    return vec3(eye.x, eye.y, -eye.z);
}

// Absolute world space for stable stochastic sampling.  Keep this on the
// same explicit inverse-view path as the voxel DDA rather than relying on a
// combined inverse matrix whose translation convention differs by render hook.
vec3 slWorldPosFromDepth(vec2 uv, float depth) {
    return slWorldRelativeFromView(slViewPosFromDepth(uv, depth));
}

// Reconstruct screen-space normal from depth gradients
vec3 screenSpaceNormal(vec2 uv, float depth, vec3 viewPos) {
    vec2 ts = vec2(1.0 / Params.x, 1.0 / Params.y);

    // Reconstruct neighbors' view-space positions
    float depthR = texture(DepthSampler, uv + vec2(ts.x, 0.0)).r;
    float depthL = texture(DepthSampler, uv - vec2(ts.x, 0.0)).r;
    float depthU = texture(DepthSampler, uv + vec2(0.0, ts.y)).r;
    float depthD = texture(DepthSampler, uv - vec2(0.0, ts.y)).r;

    // Pick the closer depth neighbor on each axis. This is the same edge
    // rule used by VanillaDI and avoids averaging across a block silhouette.
    vec2 uvX = abs(depthR - depth) < abs(depthL - depth)
            ? uv + vec2(ts.x, 0.0) : uv - vec2(ts.x, 0.0);
    vec2 uvY = abs(depthU - depth) < abs(depthD - depth)
            ? uv + vec2(0.0, ts.y) : uv - vec2(0.0, ts.y);
    vec3 pX = slViewPosFromDepth(uvX, texture(DepthSampler, uvX).r);
    vec3 pY = slViewPosFromDepth(uvY, texture(DepthSampler, uvY).r);
    vec3 n = normalize(cross(pY - viewPos, pX - viewPos));

    if (length(n) < 0.01) {
        return vec3(0.0, 0.0, -1.0);
    }

    // Ensure the normal faces the actual camera direction. A fixed -Z test
    // flips side walls unpredictably when their normal is nearly perpendicular
    // to the view axis.
    vec3 toCamera = normalize(-viewPos);
    if (dot(n, toCamera) < 0.0) n = -n;

    return n;
}

// NormalSampler is generated in a separate, edge-aware pass before lighting.
// Its alpha is zero for sky/no-depth pixels.  Retaining the depth-gradient
// path below as a fallback keeps this pass valid while the target is resized
// or a renderer leaves an invalid normal pixel, but normal sampling is the
// primary path so thin geometry does not inherit a neighbor's normal.
bool slReadNormalSampler(vec2 uv, out vec3 normal) {
    vec4 encoded = texture(NormalSampler, uv);
    if (encoded.a <= 0.5) {
        return false;
    }
    normal = encoded.rgb * 2.0 - 1.0;
    float normalLength = length(normal);
    if (normalLength <= 0.00001) {
        return false;
    }
    normal /= normalLength;
    return true;
}

vec3 slLightingNormal(vec2 uv, float depth, vec3 viewPos) {
    vec3 normal;
    if (!slReadNormalSampler(uv, normal)) {
        return screenSpaceNormal(uv, depth, viewPos);
    }

    // The normal pass normally makes this true already; retain the orientation
    // check for target clears and renderer-specific normal conventions.
    vec3 toCamera = normalize(-viewPos);
    return dot(normal, toCamera) < 0.0 ? -normal : normal;
}

// Project a lighting-view position through the exact frame projection.
bool slProjectToScreen(vec3 pos, out vec2 screenUV, out float expectedDepth) {
    screenUV = vec2(0.0);
    expectedDepth = 1.0;
    vec4 clip = ProjectionMat * vec4(pos.x, pos.y, -pos.z, 1.0);
    if (clip.w <= 0.000001) return false;
    vec3 ndc = clip.xyz / clip.w;
    screenUV = ndc.xy * 0.5 + 0.5;
    if (screenUV.x < 0.0 || screenUV.x > 1.0 || screenUV.y < 0.0 || screenUV.y > 1.0) {
        return false;
    }
    expectedDepth = ndc.z * 0.5 + 0.5;
    return true;
}

// Funeral Nova still uploads the original single-light uniforms. Keep that
// path separate from the VanillaDI light-record path used by spell emitters.
bool slLegacyProjectToScreen(vec3 pos, out vec2 screenUV,
        out float expectedDepth) {
    screenUV = vec2(0.0);
    expectedDepth = 1.0;
    float nearPlane = CameraParams.z;
    float farPlane = CameraParams.w;
    if (pos.z < nearPlane) return false;
    float halfFovTan = tan(CameraParams.x * 0.5);
    float ndcX = pos.x / (pos.z * CameraParams.y * halfFovTan);
    float ndcY = pos.y / (pos.z * halfFovTan);
    screenUV = vec2(ndcX * 0.5 + 0.5, ndcY * 0.5 + 0.5);
    if (screenUV.x < 0.0 || screenUV.x > 1.0
            || screenUV.y < 0.0 || screenUV.y > 1.0) {
        return false;
    }
    float ndcZ = (farPlane + nearPlane
            - 2.0 * farPlane * nearPlane / pos.z)
            / (farPlane - nearPlane);
    expectedDepth = ndcZ * 0.5 + 0.5;
    return true;
}

float slLegacyShadowVisibility(vec3 viewPos, vec3 normal, vec3 lightPos) {
    vec3 toLight = lightPos - viewPos;
    float distanceToLight = length(toLight);
    vec3 lightDirection = toLight / max(distanceToLight, 0.001);
    vec3 origin = viewPos + normal * 0.06 + lightDirection * 0.05;
    float rayLength = distanceToLight - 0.15;
    if (rayLength <= 0.0) return 1.0;
    const int steps = 10;
    float stepSize = rayLength / float(steps);
    float jitter = postHash(vUv * 173.0 + fract(TimePack.x) * 7.0);
    float distanceAlongRay = stepSize * (0.5 + jitter * 0.5);
    for (int stepIndex = 0; stepIndex < steps; stepIndex++) {
        vec3 samplePosition = origin + lightDirection * distanceAlongRay;
        vec2 sampleUv;
        float expectedDepth;
        if (slLegacyProjectToScreen(samplePosition, sampleUv,
                expectedDepth)) {
            float sceneDepth = texture(DepthSampler, sampleUv).r;
            float bias = 0.0012 + 0.004 * (distanceAlongRay / rayLength);
            if (sceneDepth < expectedDepth - bias) return 0.12;
        }
        distanceAlongRay += stepSize;
    }
    return 1.0;
}

vec3 slLegacyScreenLighting() {
    vec3 lightPosition = LightViewPos.xyz;
    float lightRadius = LightViewPos.w;
    vec3 lightColor = LightColor.rgb;
    float intensity = LightColor.w;
    vec3 scene = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;
    if (depth >= SL_SKY_DEPTH) {
        float ambientDistance = length(vUv - Center1.xy * 0.5 - 0.5);
        float ambient = exp(-ambientDistance * 2.5) * intensity * 0.04;
        return scene + lightColor * ambient;
    }
    vec3 viewPos = slViewPosFromDepth(vUv, depth);
    vec3 toLight = lightPosition - viewPos;
    float distanceToLight = length(toLight);
    vec3 lightDirection = toLight / max(distanceToLight, 0.001);
    float attenuation = 1.0 / (1.0 + distanceToLight * 0.09
            + distanceToLight * distanceToLight * 0.032);
    attenuation *= 1.0 - smoothstep(lightRadius * 0.6,
            lightRadius, distanceToLight);
    if (attenuation < 0.002) return scene;
    vec3 normal = screenSpaceNormal(vUv, depth, viewPos);
    float shadow = slLegacyShadowVisibility(viewPos, normal, lightPosition);
    float nDotL = max(dot(normal, lightDirection), 0.0);
    float halfLambert = nDotL * 0.5 + 0.5;
    halfLambert *= halfLambert;
    vec3 halfway = normalize(lightDirection + vec3(0.0, 0.0, 1.0));
    float specular = pow(max(dot(normal, halfway), 0.0), 32.0);
    vec3 contribution = lightColor * intensity * attenuation
            * halfLambert * shadow;
    contribution += lightColor * specular * attenuation
            * intensity * 0.35 * shadow;
    contribution += lightColor * intensity * attenuation * 0.06;
    vec3 lit = scene + contribution;
    float sceneLum = luminance(scene);
    float litLum = luminance(lit);
    if (litLum > 8.0 && litLum > sceneLum * 3.0) {
        lit = mix(lit, scene, smoothstep(8.0, 15.0, litLum));
    }
    return lit;
}

// ── Entity depth shadow fallback ──────────────────────────────────
// NOT CURRENTLY CALLED. slShadowVisibility uses VanillaDI's short contact ray
// instead, because this full-length march's tolerances grow with distance and
// produced false occluders on distant grazing surfaces. Kept because it is the
// only implementation of full-length entity shadows here and this project has
// no version control to recover it from.
//
// The main block shadow test is the camera-local voxel DDA.  Depth can still
// provide useful occlusion for entities (players/mobs), but using it blindly
// turns a visible fence bar into an opaque sheet.  This fallback therefore:
//   1) projects samples of the *same world ray* into screen space;
//   2) reconstructs a candidate depth hit in world space;
//   3) rejects candidates that land on cached block geometry; and
//   4) requires the candidate to be close to the actual ray, not merely close
//      in screen space.
// It is deliberately conservative. If the ray leaves the view or the voxel
// cache is unavailable, it returns no entity shadow rather than inventing a
// wall from unrelated depth.
bool slEntityDepthOccluded(vec3 viewPos, vec3 lightPos, vec3 worldPos) {
    if (VoxelOrigin.w < 0.5) {
        return false;
    }

    vec3 worldLight = slWorldRelativeFromView(lightPos);
    vec3 worldRay = worldLight - worldPos;
    float worldDistance = length(worldRay);
    if (worldDistance <= 0.18) {
        return false;
    }
    vec3 worldDirection = worldRay / worldDistance;

    // Leave a gap at both ends so the receiver and the emitter are not
    // rediscovered through their own depth pixels.
    float begin = 0.12;
    float end = worldDistance - 0.12;
    if (end <= begin) {
        return false;
    }

    const int sampleCount = 10;
    for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
        float t = (float(sampleIndex) + 0.5) / float(sampleCount);
        float along = mix(begin, end, t);
        float normalized = along / worldDistance;
        vec3 sampleView = mix(viewPos, lightPos, normalized);

        vec2 sampleUv;
        float expectedDepth;
        if (!slProjectToScreen(sampleView, sampleUv, expectedDepth)) {
            continue;
        }

        float observedDepth = texture(DepthSampler, sampleUv).r;
        if (observedDepth >= SL_SKY_DEPTH) {
            continue;
        }

        vec3 blockerView = slViewPosFromDepth(sampleUv, observedDepth);
        // Compare in reconstructed view distance rather than raw depth. This
        // keeps the threshold stable over the perspective depth curve.
        float forwardGap = sampleView.z - blockerView.z;
        float depthTolerance = max(0.10, abs(sampleView.z) * 0.012);
        if (forwardGap <= depthTolerance) {
            continue;
        }

        vec3 blockerWorld = slWorldPosFromDepth(sampleUv, observedDepth);
        vec3 fromSurface = blockerWorld - worldPos;
        float blockerAlong = dot(fromSurface, worldDirection);
        if (blockerAlong <= begin * 0.75
                || blockerAlong >= worldDistance - begin * 0.75) {
            continue;
        }

        // A projected sample can hit a nearby silhouette one or two pixels
        // away from the real ray. Reject that case with a world-space lateral
        // distance test. The small pixel term grows naturally with depth.
        vec3 lateral = fromSurface - worldDirection * blockerAlong;
        float pixelWorld = max(0.015, blockerAlong
                * tan(max(CameraParams.x, 0.01) * 0.5)
                / max(Params.y, 1.0));
        float rayTolerance = max(0.12, pixelWorld * 3.0);
        if (dot(lateral, lateral) > rayTolerance * rayTolerance) {
            continue;
        }

        // Keep the fallback local to the coherent cache. A depth hit outside
        // this volume may be an off-screen wall that the block DDA could not
        // inspect; treating it as an entity would recreate the old abrupt
        // full-screen cutoff at the cache boundary.
        if (!slVoxelPointInBounds(blockerWorld)) {
            continue;
        }

        // If the candidate is a block, fence bar, iron bar or trapdoor, the
        // voxel DDA either already caught the ray or intentionally left its
        // hole open. Never let the screen depth path turn that shape into a
        // full opaque plane.
        if (slVoxelPointOccupiedNear(blockerWorld)) {
            continue;
        }

        return true;
    }
    return false;
}

// ── Voxel + entity shadow ─────────────────────────────────────────
// Returns 1.0 = fully lit, 0.0 = occluded. The small ambient term in the
// lighting pass supplies the faint bounce for fully shadowed surfaces.
// VanillaDI's final fallback is a short screen-space ray so players and other
// depth-writing entities can cast shadows even though they are not in voxels.
bool slTraceScreenSpaceRay(vec3 origin, float depth, vec3 direction,
        float maxRayDistance, inout vec3 seed) {
    const int samples = 25;
    vec3 screenSpaceWorld = origin + direction * 0.01 * length(origin);
    float stepSize = 1.0 / 50.0;
    float jitter = postHash(seed.xy + seed.z * vec2(0.754877, 0.569841));
    seed += vec3(0.37, 0.61, 0.17);
    screenSpaceWorld += direction * jitter * stepSize;

    for (int sampleIndex = 0; sampleIndex < samples; sampleIndex++) {
        screenSpaceWorld += direction * stepSize;
        vec2 sampleUv;
        float expectedDepth;
        if (!slProjectToScreen(screenSpaceWorld, sampleUv, expectedDepth)) {
            break;
        }
        float observedDepth = texture(DepthSampler, sampleUv).r;
        float delta = expectedDepth - observedDepth;
        if (observedDepth != 1.0 && delta > 0.0
                && delta < 0.02 * (1.0 - depth)) {
            return true;
        }
    }
    return false;
}

// ── Stochastic area shadow ────────────────────────────────────────
// Shared with the reservoir sampler further down; declared here because the
// shadow test below is the first consumer and GLSL resolves top to bottom.
float slRandom(inout vec3 state) {
    state += vec3(1.0, 1.37, 2.11);
    return postHash(state.xy + state.z * vec2(0.754877, 0.569841));
}

vec3 slRandomPointOnSphere(inout vec3 state) {
    float a = slRandom(state) * 6.28318530718;
    float b = slRandom(state) * 2.0 - 1.0;
    float radial = sqrt(max(1.0 - b * b, 0.0));
    return vec3(radial * cos(a), radial * sin(a), b);
}

// VanillaDI samples a random point on the emitter's surface per pixel per frame
// and traces one ray to it, letting the temporal pass average the result into a
// penumbra. That is not a cosmetic choice here, it is what makes a cutout hole
// visible at all: the occupancy cache resolves 1/8 of a block, and an oak
// trapdoor's 3/16 hole leaves just ONE fully-empty subvoxel per axis. A single
// ray aimed at the light's centre either threads that one subvoxel or does not,
// so each hole comes out fully lit or fully black depending on the angle --
// exactly the "some holes work, some don't" pattern. Spreading the rays across
// the source turns that binary decision into the fraction of rays that get
// through, which is the physically right answer and degrades gracefully when
// the hole is near the cache's resolution limit.
//
// Cost is SL_SHADOW_SAMPLES block-DDA traces per light instead of one. Four
// converges within a few frames once the temporal pass accumulates them.
#define SL_SHADOW_SAMPLES 4
// Radius of the emitter surface the shadow rays aim at. VanillaDI's
// sphereLight() uses 0.5, but that figure sizes the light for its RADIANCE term;
// reusing it as the shadow aperture makes the penumbra far wider than a fine
// occluder's opening and erases the opening entirely. Simulated against the real
// baked oak-trapdoor mask, converged transmission at a hole centre runs:
//   r=0.50 -> 33%   r=0.30 -> 67%   r=0.20 -> 100%   r<=0.15 -> 100% and crisp.
// 0.20 is the largest radius that still reaches full brightness through a
// 1/8-block hole, so it keeps a soft edge on large occluders without closing the
// small ones.
#define SL_SHADOW_SOURCE_RADIUS 0.2
// Fully shadowed surfaces keep this much of the light as bounce, matching the
// single-ray behaviour this replaces.
#define SL_SHADOW_FLOOR 0.12

float slShadowVisibility(vec3 viewPos, float depth, vec3 N,
        vec3 lightPos, vec3 worldPos, inout vec3 seed) {
    float visible = 0.0;

    // Shadow cost is (lights x samples) block-DDA traces per pixel, so a fixed
    // sample count makes a nine-light constellation nine times the cost of a
    // single-light spell. Spend a roughly constant ray budget instead by
    // splitting it across the active lights: one light keeps the full quality,
    // and many lights each get fewer samples, with the temporal pass resolving
    // the extra noise. MiscParams.x carries the light count.
    int shadowSamples = int(clamp(float(SL_SHADOW_SAMPLES)
            / max(MiscParams.x, 1.0), 1.0, float(SL_SHADOW_SAMPLES)));

    for (int sampleIndex = 0; sampleIndex < shadowSamples; sampleIndex++) {
        // A point on the hemisphere of the source facing this surface, as in
        // the author's sphereLight(). Sampling the far hemisphere would place
        // the target behind the emitter and bias the penumbra.
        vec3 sphereNormal = slRandomPointOnSphere(seed);
        sphereNormal *= sign(dot(sphereNormal, normalize(viewPos - lightPos)));
        vec3 samplePos = lightPos + sphereNormal * SL_SHADOW_SOURCE_RADIUS;

        vec3 toLight = samplePos - viewPos;
        float dist = length(toLight);
        vec3 L = toLight / max(dist, 0.001);

        // Lift the origin only enough to escape the receiver surface. Keeping
        // the bias below a sub-voxel width is important for thin trapdoors and
        // bars.
        vec3 origin = viewPos + N * 0.015 + L * 0.01;
        float rayLen = dist - 0.04;
        if (rayLen <= 0.0) {
            visible += 1.0;
            continue;
        }

        // The camera-local block cache is the authoritative occluder. It covers
        // off-screen geometry and preserves holes/thin shapes, so query it
        // before falling back to the depth buffer.
        if (slVoxelOccluded(viewPos, N, samplePos)) {
            continue;
        }

        // VanillaDI's depth fallback is a 0.5-block CONTACT ray whose thickness
        // window closes as depth approaches 1, so it switches itself off at
        // range. slEntityDepthOccluded below marches the whole distance to the
        // light with tolerances that instead grow with distance, which invents
        // blobby occluders on a distant grazing floor whose own voxels are too
        // coarsely resolved to veto the hit.
        if (slTraceScreenSpaceRay(origin, depth, L, rayLen, seed)) {
            continue;
        }

        visible += 1.0;
    }

    return mix(SL_SHADOW_FLOOR, 1.0,
            visible / float(shadowSamples));
}

vec4 slLightTexel(int index) {
    int width = max(int(LightDataParams.x + 0.5), 1);
    return texelFetch(LightDataSampler,
            ivec2(index % width, index / width), 0);
}

// VanillaDI stores signed 24-bit integers in RGB. The high bit of B is a
// sign flag; the remaining seven bits are the high byte of the magnitude.
int slDecodeInt(vec3 encoded) {
    ivec3 bytes = ivec3(floor(encoded * 255.0 + 0.5));
    int sign = bytes.b >= 128 ? -1 : 1;
    int magnitude = bytes.r + bytes.g * 256
            + (bytes.b - 64 + sign * 64) * 256 * 256;
    return sign * magnitude;
}

float slDecodeFloat(vec3 encoded) {
    return float(slDecodeInt(encoded)) / 40000.0;
}

float slDecodeFloat1024(vec3 encoded) {
    return float(slDecodeInt(encoded)) / 1024.0;
}

int slMetadataLightCount() {
    return max(slDecodeInt(slLightTexel(35).rgb), 0);
}

void slLightRecord(int index, out vec4 position, out vec4 color) {
    int base = 36 + index * 11;
    vec3 lightPosition = vec3(
            slDecodeFloat1024(slLightTexel(base + 0).rgb),
            slDecodeFloat1024(slLightTexel(base + 1).rgb),
            slDecodeFloat1024(slLightTexel(base + 2).rgb));
    vec3 tangent = normalize(vec3(
            slDecodeFloat(slLightTexel(base + 3).rgb),
            slDecodeFloat(slLightTexel(base + 4).rgb),
            slDecodeFloat(slLightTexel(base + 5).rgb)));
    vec3 bitangent = normalize(vec3(
            slDecodeFloat(slLightTexel(base + 6).rgb),
            slDecodeFloat(slLightTexel(base + 7).rgb),
            slDecodeFloat(slLightTexel(base + 8).rgb)));
    vec3 rgb = slLightTexel(base + 9).rgb;
    vec3 lightParams = slLightTexel(base + 10).rgb;
    float intensity = lightParams.r * 100.0;
    int type = int(floor(lightParams.g * 255.0 + 0.5));

    // Preserve the original spell-light influence radius.  The finite source
    // sample remains a small sphere inside this gameplay-facing cutoff.
    float influenceRadius = max(lightParams.b * 64.0, 1.0);
    position = vec4(lightPosition, influenceRadius);
    color = vec4(rgb, intensity);
}

vec4 slLightCenterFromPosition(vec3 position) {
    vec2 screenUV;
    float expectedDepth;
    if (!slProjectToScreen(position, screenUV, expectedDepth)) {
        return vec4(0.0, 0.0, length(position), 0.0);
    }
    float distance = length(position);
    return vec4(screenUV * 2.0 - 1.0, distance, 1.0);
}

// VanillaDI samples a finite emitter instead of treating every light as an
// infinitely small point.  The migrated spell entities do not expose a
// tangent frame, so use a sphere sample derived from the emitter radius.  The
// seed is deliberately quantized to a short frame window: the center sample
// remains stable while the secondary sample slowly changes to hide hard shadow
// bands without introducing per-frame grain.
vec3 slSampleSphere(vec3 center, float radius, float seed) {
    float azimuth = seed * 6.28318530718;
    float elevation = mix(-1.0, 1.0, fract(seed * 1.61803398875 + 0.37));
    float radial = sqrt(max(1.0 - elevation * elevation, 0.0));
    return center + vec3(cos(azimuth) * radial,
                         sin(azimuth) * radial,
                         elevation) * radius;
}

float slAreaLightSeed(int index, vec3 worldPos) {
    // Keep the stratification stable for four game ticks. This acts as a
    // lightweight temporal reservoir: neighboring pixels share no random
    // state, but a static surface does not shimmer every rendered frame.
    float frameBucket = floor(TimePack.y * 0.25);
    return postHash(worldPos.xz * 4.71 + vec2(worldPos.y * 3.19,
            float(index) * 37.17 + frameBucket * 11.73));
}

// VanillaDI's shade pass uses a small reservoir of stochastic light samples.
// The Forge bridge has no item-display tangent frame, so every migrated spell
// emitter is represented as the author's spherical light (type 1) with a
// radius derived from the uploaded spell radius.  The sampling, cosine and
// inverse-square terms are otherwise kept in the same order as shade.fsh.
void slSampleSphereLight(vec3 fragPos, vec3 normal, vec3 position,
        float sourceRadius, float intensity, vec3 color, inout vec3 state,
        out vec3 sampledPosition, out vec3 sampledDirection,
        out vec3 sampledRadiance, out float sampledDistance) {
    vec3 sphereNormal = slRandomPointOnSphere(state);
    vec3 toSurface = normalize(fragPos - position);
    sphereNormal *= sign(dot(sphereNormal, toSurface));
    sampledPosition = position + sphereNormal * sourceRadius;
    vec3 toLight = sampledPosition - fragPos;
    sampledDistance = length(toLight);
    sampledDirection = toLight / max(sampledDistance, 0.0001);

    float diffuse = max(dot(normal, sampledDirection), 0.0);
    float cosine = dot(sampledDirection, sphereNormal);
    float area = 2.0 * 3.1415926535 * sourceRadius * sourceRadius;
    float attenuation = (2.0 * 3.1415926535 * intensity * diffuse)
            * (abs(cosine * area)
            / max(sampledDistance * sampledDistance, 0.0001));
    sampledRadiance = color * attenuation;
}

void main() {
    if (LightDataParams.z < 0.5) {
        fragColor = vec4(slLegacyScreenLighting(), 1.0);
        return;
    }

    float depth = texture(DepthSampler, vUv).r;

    // Sky pixels have no reconstructable surface. Keep the original small
    // source halo without feeding them into surface lighting.
    if (depth >= SL_SKY_DEPTH) {
        vec3 skyGlow = vec3(0.0);
        int lightCount = slMetadataLightCount();
        for (int index = 0; index < lightCount; index++) {
            vec4 packedPos;
            vec4 color;
            slLightRecord(index, packedPos, color);
            vec4 center = slLightCenterFromPosition(packedPos.xyz);
            // w is slLightCenterFromPosition's validity flag. Without this test
            // an off-screen or behind-camera light falls back to NDC (0,0), and
            // "distance from vUv to the projected centre" becomes "distance from
            // vUv to the middle of the screen" -- a glow pinned to the crosshair
            // wherever the player looks, banded into rings by the 8-bit output.
            if (center.w < 0.5) {
                continue;
            }
            float radius = max(packedPos.w, 0.0);
            float source = exp(-length((vUv - center.xy * 0.5 - 0.5)
                    * vec2(Params.x / max(Params.y, 1.0), 1.0))
                    / max(radius / max(abs(packedPos.z), 1.0) * 0.24, 0.012) * 2.4);
            skyGlow += color.rgb * source * color.a * 0.04;
        }
        fragColor = vec4(skyGlow, 1.0);
        return;
    }

    vec3 viewPos = slViewPosFromDepth(vUv, depth);
    vec3 worldPos = slWorldPosFromDepth(vUv, depth);
    vec3 N = slLightingNormal(vUv, depth, viewPos);
    vec3 radiance = vec3(0.0);
    // Keep the original point-light view and response curve. The precise
    // subvoxel shadow test below is independent of this shading model.
    vec3 V = normalize(vec3(0.0, 0.0, 1.0));
    // TimePack.w advances every rendered frame, so each frame draws a different
    // set of shadow rays and the temporal pass resolves them into a penumbra.
    // Binding the seed to the game clock instead would freeze the sample set
    // for a whole tick and show the noise as banding.
    vec3 seed = vec3(worldPos.xy, worldPos.z + TimePack.w);

    int lightCount = slMetadataLightCount();
    // Params.z selects a debug view. The Java pass blits this target straight to
    // the screen when it is nonzero, bypassing temporal/spatial/blend, so what
    // is shown is exactly what this pass decided for each pixel:
    //   1 = shadow visibility   2 = distance attenuation
    //   3 = reconstructed normal 4 = voxel occupancy under the surface
    // These separate "the light never reached here" from "the light reached here
    // and was then filtered away", which look identical in the final image.
    int debugMode = int(Params.z + 0.5);
    if (debugMode > 0) {
        vec3 shown = vec3(0.0);
        if (debugMode == 3) {
            shown = N * 0.5 + 0.5;
        } else if (debugMode == 4) {
            shown = slVoxelPointOccupiedNear(worldPos)
                    ? vec3(1.0, 0.2, 0.0) : vec3(0.0, 0.15, 0.35);
        } else {
            for (int index = 0; index < lightCount; index++) {
                vec4 packedPos;
                vec4 packedColor;
                slLightRecord(index, packedPos, packedColor);
                float dist = length(packedPos.xyz - viewPos);
                if (dist >= packedPos.w || dist <= 0.001) {
                    continue;
                }
                if (debugMode == 1) {
                    shown = vec3(slShadowVisibility(viewPos, depth, N,
                            packedPos.xyz, worldPos, seed));
                } else {
                    float attenuation = 1.0 / (1.0 + dist * 0.09
                            + dist * dist * 0.032);
                    attenuation *= 1.0 - smoothstep(packedPos.w * 0.88,
                            packedPos.w, dist);
                    shown = vec3(attenuation);
                }
                break;
            }
        }
        fragColor = vec4(shown, 1.0);
        return;
    }

    for (int index = 0; index < lightCount; index++) {
        vec4 packedPos;
        vec4 packedColor;
        slLightRecord(index, packedPos, packedColor);
        vec3 lightPos = packedPos.xyz;
        float lightRadius = packedPos.w;
        float intensity = packedColor.a;
        vec3 toLight = lightPos - viewPos;
        float dist = length(toLight);
        if (dist >= lightRadius || dist <= 0.001) continue;

        vec3 L = toLight / max(dist, 0.001);
        // Inverse-square with a softened near field. The asymptote is
        // ~31.25/d^2, which is close to VanillaDI's physical
        // 2*PI*intensity*|cos*area|/d^2 for a 0.5-block spherical source.
        float attenuation = 1.0 / (1.0 + dist * 0.09
                + dist * dist * 0.032);
        // Upstream has NO distance cutoff -- it relies on 1/d^2 alone. This port
        // culls at the emitter's radius for scope, so a fade is needed to avoid a
        // visible ring at the cull boundary, but it has to stay out of the way:
        // fading from 0.6*radius threw away the outer 40% of every light's reach
        // (at radius 18 it cut a 14-block sample to 7% and a 16-block one to 2%),
        // which is what made a light look like it "lost half its pool" as soon as
        // the camera backed off far enough to see that band.
        attenuation *= 1.0 - smoothstep(lightRadius * 0.88,
                lightRadius, dist);
        if (attenuation < 0.002) continue;

        float shadow = slShadowVisibility(viewPos, depth, N,
                lightPos, worldPos, seed);
        float NdotL = max(dot(N, L), 0.0);
        float halfLambert = NdotL * 0.5 + 0.5;
        halfLambert *= halfLambert;
        vec3 H = normalize(L + V);
        float specular = pow(max(dot(N, H), 0.0), 32.0);
        radiance += packedColor.rgb * intensity * attenuation
                * halfLambert * shadow;
        radiance += packedColor.rgb * specular * attenuation
                * intensity * 0.35 * shadow;
        radiance += packedColor.rgb * intensity * attenuation * 0.06;

        vec4 center = slLightCenterFromPosition(lightPos);
        float projectedRadius = clamp(lightRadius
                / max(abs(lightPos.z), 1.0) * 0.24, 0.012, 0.24);
        float halo = exp(-length((vUv - center.xy * 0.5 - 0.5)
                * vec2(Params.x / max(Params.y, 1.0), 1.0))
                / projectedRadius * 2.4) * intensity * 0.025;
        radiance += packedColor.rgb * halo;
    }

    // Keep the original pass contract: independent HDR radiance is filtered
    // by LIGHT_TEMPORAL/LIGHT_SPATIAL and composited once in LIGHT_BLEND.
    fragColor = slEncodeHdr(radiance);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  VanillaDI-style radiance denoising and blend
// ══════════════════════════════════════════════════════════════════

#ifdef LIGHT_TEMPORAL
uniform sampler2D DepthSampler;
uniform sampler2D NormalSampler;
uniform sampler2D PreviousNormalSampler;
uniform sampler2D PreviousDepthSampler;
uniform sampler2D PreviousFrameSampler;
// Previous frame's pre-spatial radiance. VanillaDI names this sampler
// PreviousRadianceSampler and that is what the Java pass binds; the shared
// BloomSampler slot is deliberately not declared for this pass.
uniform sampler2D PreviousRadianceSampler;
uniform mat4 InverseViewProjectionMat;
uniform mat4 InverseViewMat;
uniform mat4 PreviousProjectionMat;
uniform mat4 PreviousViewMat;
uniform vec4 CameraWorldPos;
uniform vec4 PreviousCameraWorldPos;

// Inverse of slPackDepth in LIGHT_COPY_DEPTH. VanillaDI unpacks IEEE-754 bits
// instead, which requires GLSL 330; this include compiles at 150 on Forge
// 1.20.1, so the port stores depth as normalised RGBA bytes.
float slUnpackDepth(vec4 value) {
    return dot(value, vec4(1.0, 1.0 / 256.0,
            1.0 / 65536.0, 1.0 / 16777216.0));
}

// Decode before interpolating. The compact HDR form stores a normalised colour
// alongside a reciprocal scale, so filtering the raw texels interpolates the
// two independently and yields a colour belonging to neither neighbour.
vec3 slHistoryRadiance(vec2 uv) {
    vec2 pixel = uv * max(Params.xy, vec2(1.0)) - 0.5;
    ivec2 base = ivec2(floor(pixel));
    vec2 weight = pixel - vec2(base);
    vec3 lower = mix(
            slDecodeHdr(texelFetch(PreviousRadianceSampler, base, 0)),
            slDecodeHdr(texelFetch(PreviousRadianceSampler,
                    base + ivec2(1, 0), 0)),
            weight.x);
    vec3 upper = mix(
            slDecodeHdr(texelFetch(PreviousRadianceSampler,
                    base + ivec2(0, 1), 0)),
            slDecodeHdr(texelFetch(PreviousRadianceSampler,
                    base + ivec2(1, 1), 0)),
            weight.x);
    return mix(lower, upper, weight.y);
}

// light_normals.fsh stores a +Z-forward lighting-view normal, which rotates
// with the camera. VanillaDI's normals are camera-relative but world-oriented,
// so both frames have to be lifted into world orientation before they can be
// compared; otherwise simply turning the view rejects every history sample.
// PreviousViewMat carries rotation only, so its transpose is its inverse.
vec3 slTemporalWorldNormal(mat4 viewToWorld, vec3 encoded) {
    vec3 lighting = encoded * 2.0 - 1.0;
    vec3 world = (viewToWorld
            * vec4(lighting.x, lighting.y, -lighting.z, 0.0)).xyz;
    return length(world) > 0.000001 ? normalize(world) : vec3(0.0);
}

void main() {
    // Radiance travels between passes in VanillaDI's compact HDR form, so it
    // has to be decoded before any arithmetic and re-encoded on the way out.
    vec3 current = slDecodeHdr(texture(SceneSampler, vUv));
    fragColor = slEncodeHdr(current);

    float valid = clamp(MiscParams.w, 0.0, 1.0);
    float depth = texture(DepthSampler, vUv).r;
    if (valid < 0.001 || depth >= SL_SKY_DEPTH) {
        return;
    }

    // Reconstruct the current world point, then project that point through
    // the previous frame's camera. This is the same contract as VanillaDI's
    // temporal pass and prevents radiance from sticking to screen pixels.
    vec4 clip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 relative = InverseViewProjectionMat * clip;
    if (abs(relative.w) < 0.000001) {
        return;
    }
    relative.xyz /= relative.w;
    vec3 world = relative.xyz + CameraWorldPos.xyz;
    vec4 previousClip = PreviousProjectionMat * vec4((PreviousViewMat
            * vec4(world - PreviousCameraWorldPos.xyz, 1.0)).xyz, 1.0);
    if (previousClip.w <= 0.000001) {
        return;
    }
    vec3 previousNdc = previousClip.xyz / previousClip.w * 0.5 + 0.5;
    if (clamp(previousNdc.xy, vec2(0.0), vec2(1.0)) != previousNdc.xy) {
        return;
    }

    // VanillaDI's history rejection. Without it a blend this strong smears
    // radiance across disocclusion edges and behind moving entities.
    vec4 encodedNormal = texture(NormalSampler, vUv);
    vec4 previousEncodedNormal = texture(PreviousNormalSampler, previousNdc.xy);
    if (encodedNormal.a < 0.5 || previousEncodedNormal.a < 0.5) {
        return;
    }
    if (dot(slTemporalWorldNormal(InverseViewMat, encodedNormal.rgb),
            slTemporalWorldNormal(transpose(PreviousViewMat),
                    previousEncodedNormal.rgb)) < 0.7) {
        return;
    }
    float previousDepth = slUnpackDepth(
            texture(PreviousDepthSampler, previousNdc.xy));
    if (abs(previousNdc.z - previousDepth) > 0.001 * previousNdc.z) {
        return;
    }

    // Converge quickly on freshly accumulated pixels, then settle at
    // VanillaDI's steady-state blend; the luminance term keeps very bright
    // pixels from trailing.
    vec3 previous = slHistoryRadiance(previousNdc.xy);
    float age = texture(PreviousFrameSampler, previousNdc.xy).r + 0.01;
    float luma = dot(previous, vec3(0.2125, 0.7154, 0.0721));
    float alpha = clamp(0.1 + pow(luma * 0.03, 40.0), 0.0, 0.5);
    float blend = max(1.0 / max(floor(age * 100.0), 1.0), alpha);
    fragColor = slEncodeHdr(mix(previous, current, blend));
}
#endif

#ifdef LIGHT_SPATIAL
uniform sampler2D DepthSampler;
uniform mat4 InverseProjectionMat;
// VanillaDI's spatial pass takes its widening factor from Step, one value per
// iteration. MiscParams is not declared for this pass, so reading the radius
// from it left every iteration at the narrowest kernel.
uniform float Step;

vec3 lightSpatialViewPosFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 0.000001) {
        return vec3(0.0);
    }
    eye.xyz /= eye.w;
    return vec3(eye.x, eye.y, -eye.z);
}

vec3 lightSpatialNormal(vec2 uv, float depth) {
    vec2 texel = 1.0 / max(Params.xy, vec2(1.0));
    vec3 center = lightSpatialViewPosFromDepth(uv, depth);
    float depthX = texture(DepthSampler, uv + vec2(texel.x, 0.0)).r;
    float depthY = texture(DepthSampler, uv + vec2(0.0, texel.y)).r;
    vec3 px = lightSpatialViewPosFromDepth(
            uv + vec2(texel.x, 0.0), depthX);
    vec3 py = lightSpatialViewPosFromDepth(
            uv + vec2(0.0, texel.y), depthY);
    vec3 normal = normalize(cross(py - center, px - center));
    vec3 toCamera = normalize(-center);
    return dot(normal, toCamera) < 0.0 ? -normal : normal;
}

void main() {
    vec2 texel = 1.0 / max(Params.xy, vec2(1.0));
    // VanillaDI offsets its taps by sqrt(Step), not Step: with Step running
    // 1/2/4/8 the widest kernel reaches 2.83 texels, not 8. This port's kernel
    // is far sparser than upstream's 5x5, so taking Step directly would both
    // overreach and alias.
    float radius = max(sqrt(Step), 1.0);
    vec3 center = slDecodeHdr(texture(SceneSampler, vUv));
    float centerDepth = texture(DepthSampler, vUv).r;
    vec3 centerPosition = lightSpatialViewPosFromDepth(vUv, centerDepth);
    vec3 centerNormal = lightSpatialNormal(vUv, centerDepth);
    float centerLuma = dot(center, vec3(0.2125, 0.7154, 0.0721));
    vec3 sum = center;
    float weightSum = 1.0;
    // Cross + diagonals preserve contact edges while four passes provide the
    // same progressively wider denoising profile as VanillaDI.
    const vec2 taps[8] = vec2[8](
        vec2(1.0, 0.0), vec2(-1.0, 0.0),
        vec2(0.0, 1.0), vec2(0.0, -1.0),
        vec2(0.7071, 0.7071), vec2(-0.7071, 0.7071),
        vec2(0.7071, -0.7071), vec2(-0.7071, -0.7071));
    for (int i = 0; i < 8; i++) {
        vec2 uv = clamp(vUv + taps[i] * texel * radius, vec2(0.0), vec2(1.0));
        float sampleDepth = texture(DepthSampler, uv).r;
        // VanillaDI discards any tap more than 0.15 blocks from the centre in
        // world space. Screen-space depth and normal weights constrain nothing
        // across a large flat floor -- both stay near 1.0 there -- so without
        // this the widest iteration averages away detail as fine as a
        // trapdoor's 3/16 cutout, which is only a few pixels once projected.
        vec3 samplePosition = lightSpatialViewPosFromDepth(uv, sampleDepth);
        float worldDistance = distance(samplePosition, centerPosition);
        if (worldDistance > 0.15) {
            continue;
        }
        float depthWeight = exp(-abs(sampleDepth - centerDepth) * 180.0);
        float normalWeight = pow(max(dot(centerNormal,
                lightSpatialNormal(uv, sampleDepth)), 0.0), 12.0);
        float spatialWeight = exp(-radius * 0.055);
        vec3 sampleRadiance = slDecodeHdr(texture(SceneSampler, uv));
        float lumaWeight = exp(-abs(dot(sampleRadiance,
                vec3(0.2125, 0.7154, 0.0721)) - centerLuma) / 5.0
                - worldDistance);
        float weight = depthWeight * normalWeight * spatialWeight * lumaWeight;
        sum += sampleRadiance * weight;
        weightSum += weight;
    }
    fragColor = slEncodeHdr(sum / max(weightSum, 0.0001));
}
#endif

#ifdef LIGHT_BLEND
uniform sampler2D DepthSampler;
uniform mat4 InverseProjectionMat;
uniform mat4 InverseViewMat;
uniform vec4 CameraWorldPos;
// xyz = field centre in world space, w = radius. Zero radius disables it.
uniform vec4 SilhouetteField;

void main() {
    // gl_FragCoord is always inside the viewport, so no clamp is needed. The
    // previous max(..., ivec2(0, 1)) forced y >= 1, which made the bottom row
    // sample the row above it. texelFetch rather than texture(): the radiance
    // target holds the compact HDR form, which must not be filtered.
    ivec2 coord = ivec2(gl_FragCoord.xy);
    vec3 scene = texelFetch(SceneSampler, coord, 0).rgb;
    vec3 radiance = slDecodeHdr(texelFetch(BloomSampler, coord, 0));
    // SCREEN_LIGHTING now emits the same additive contribution as the
    // original spell-light pass. Multiplying the scene by radiance (the
    // VanillaDI ratio convention) made the restored light look clipped and
    // removed part of the visible falloff.
    vec3 result = scene + radiance;
    float sceneLum = luminance(scene);
    float resultLum = luminance(result);
    if (resultLum > 8.0 && resultLum > sceneLum * 3.0) {
        result = mix(result, scene,
                smoothstep(8.0, 15.0, resultLum));
    }

    // Silhouette turns the light/shadow relationship inside out, but only for
    // depth-tested surfaces inside its own field: inverting the whole frame
    // would take the sky, the held item and the UI with it.
    float inversion = clamp(MiscParams.x, 0.0, 1.0);
    if (inversion > 0.001 && SilhouetteField.w > 0.001) {
        float depth = texture(DepthSampler, vUv).r;
        if (depth < SL_SKY_DEPTH) {
            vec4 clip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec4 eye = InverseProjectionMat * clip;
            if (abs(eye.w) > 0.000001) {
                eye.xyz /= eye.w;
                vec3 world = (InverseViewMat * vec4(eye.xyz, 0.0)).xyz
                        + CameraWorldPos.xyz;
                float fieldDistance = length(world - SilhouetteField.xyz);
                float region = 1.0 - smoothstep(SilhouetteField.w * 0.7,
                        SilhouetteField.w, fieldDistance);
                if (region > 0.001) {
                    // Driven by how bright the surface actually is. Keying the
                    // inversion off the spell-light radiance alone meant that
                    // with no other spell active the radiance buffer was empty
                    // everywhere, so the field washed out to one flat colour
                    // instead of turning light and dark around.
                    float litLum = clamp(luminance(result), 0.0, 1.0);
                    // Normalising by luminance alone divides a dark saturated
                    // pixel by a near-zero value, so the hue survived but the
                    // magnitude did not: the result left the displayable range
                    // and read as blown-out geometry. Normalise by the largest
                    // channel instead, which keeps the hue and bounds the result
                    // to 1 by construction.
                    vec3 chroma = result / max(max(result.r,
                            max(result.g, result.b)), 0.004);
                    vec3 negative = mix(vec3(1.0), chroma, 0.55)
                            * (1.0 - litLum);
                    result = mix(result, negative, region * inversion);
                }
            }
        }
    }

    fragColor = vec4(result, 1.0);
}
#endif

#ifdef STARLESS
uniform sampler2D DepthSampler;
uniform mat4 InverseProjectionMat;

// Center1.xyz = void centre in view space (+Z forward), Center1.w = radius in blocks.
// Center2.xy  = void centre in screen UV, .z = its screen radius, .w = swallowed 0..1
// MiscParams.x = drain 0..1, .y = release flash, .z = wrapped time (s), .w = ingest 0..1
//
// Draining other spells' lights is NOT done here: it is a per-light intensity
// scale in SpellLightEmitter, which makes a light, its falloff and its cast
// shadows go out together. This pass draws what only screen space can give --
// the scene bending into the void, the horizon, and the ring of swallowed light.
vec3 starlessViewPos(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 0.000001) {
        return vec3(0.0);
    }
    eye.xyz /= eye.w;
    return vec3(eye.x, eye.y, -eye.z);
}

/** Rotating filaments of swallowed light, brightest just outside the horizon. */
float starlessRing(vec2 delta, float horizon, float time, float aspect) {
    vec2 ad = vec2(delta.x * aspect, delta.y);
    float d = length(ad);
    float angle = atan(ad.y, ad.x);
    // Two counter-rotating noise bands keep the ring from reading as a decal.
    float swirl = bhFbm(vec2(angle * 2.2 + time * 0.9, d * 26.0 - time * 1.7));
    swirl = mix(swirl, bhFbm(vec2(angle * 3.7 - time * 1.4, d * 18.0)), 0.45);
    float band = exp(-pow((d - horizon * 1.18) / max(horizon * 0.30, 0.0015), 2.0));
    return band * (0.35 + swirl * 0.9);
}

/** Streaks converging on the horizon: light visibly being pulled in. */
float starlessInfall(vec2 delta, float horizon, float time, float aspect) {
    vec2 ad = vec2(delta.x * aspect, delta.y);
    float d = length(ad);
    if (d < horizon * 1.02 || d > horizon * 5.0) {
        return 0.0;
    }
    float angle = atan(ad.y, ad.x);
    // Spokes drift inward: the phase term marches toward the centre with time.
    float spokes = bhFbm(vec2(angle * 7.0, d * 7.0 - time * 3.4));
    spokes = pow(max(spokes, 0.0), 2.6);
    float reach = 1.0 - smoothstep(horizon * 1.05, horizon * 4.2, d);
    return spokes * reach;
}

void main() {
    float drain = clamp(MiscParams.x, 0.0, 1.0);
    float flash = clamp(MiscParams.y, 0.0, 1.0);
    float time = MiscParams.z;
    float ingest = clamp(MiscParams.w, 0.0, 1.0);
    float swallowed = clamp(Center2.w, 0.0, 1.0);

    vec2 centre = Center2.xy;
    float horizon = max(Center2.z, 0.0004);
    float aspect = max(Params.x, 1.0) / max(Params.y, 1.0);
    vec2 aspectVec = vec2(aspect, 1.0);
    vec2 delta = vUv - centre;
    float radial = length(delta * aspectVec);

    // Anything drawn in front of the void hides it, so the whole effect is
    // masked by a depth comparison rather than composited over the scene.
    float depth = texture(DepthSampler, vUv).r;
    float voidDistance = max(Center1.z, 0.001);
    float occluding = 0.0;
    if (depth < SL_SKY_DEPTH) {
        float sceneDistance = length(starlessViewPos(vUv, depth));
        // A surface nearer than the void's front face covers it.
        occluding = 1.0 - smoothstep(voidDistance - Center1.w * 1.2,
                voidDistance - Center1.w * 0.2, sceneDistance);
    }
    float visible = 1.0 - occluding;

    // ── Lensing ──────────────────────────────────────────────────────
    // The single most legible cue that something is eating light: the world
    // bends into it. Strength follows the ingest curve so it builds visibly.
    float lensStrength = horizon * (0.55 + ingest * 0.85) * visible;
    vec2 lensUv = bhGravLens(vUv, centre, lensStrength, aspectVec);
    // Split the channels across the deflection near the horizon. Cheap, and it
    // sells the gradient as gravitational rather than as a blur.
    float fringe = visible * exp(-pow(max(radial - horizon, 0.0)
            / max(horizon * 1.6, 0.002), 2.0)) * 0.010;
    vec2 toCentre = normalize(delta + vec2(1.0e-6));
    vec3 scene;
    scene.r = texture(SceneSampler, lensUv + toCentre * fringe).r;
    scene.g = texture(SceneSampler, lensUv).g;
    scene.b = texture(SceneSampler, lensUv - toCentre * fringe).b;

    vec3 result = scene;

    // ── Interior ─────────────────────────────────────────────────────
    // Colour is taken away first, then the light itself, so the inside reads as
    // a subtraction rather than as a black shape pasted on top.
    float interior = (1.0 - smoothstep(horizon * 0.80, horizon * 1.06, radial)) * visible;
    float bite = interior * drain;
    result = mix(result, vec3(luminance(result)), bite * 0.9);
    result *= 1.0 - bite * 0.985;

    // ── Swallowed light ──────────────────────────────────────────────
    // The ring is the payoff of the drain: every light the void took shows up
    // here, so it brightens with both the ingest curve and the swallowed count.
    float ringGain = (0.55 + ingest * 1.1) * (1.0 + swallowed * 1.6) * visible;
    float ring = starlessRing(delta, horizon, time, aspect) * ringGain;
    vec3 ringColour = mix(vec3(0.62, 0.28, 1.0), vec3(1.0, 0.93, 1.0),
            clamp(ring * 0.55 + swallowed * 0.35, 0.0, 1.0));
    result += ringColour * ring * 1.35;

    float infall = starlessInfall(delta, horizon, time, aspect)
            * ingest * visible * (0.8 + swallowed * 0.9);
    result += vec3(0.55, 0.3, 1.0) * infall * 0.85;

    // The horizon itself stays black even where the ring overlaps it.
    float core = (1.0 - smoothstep(horizon * 0.86, horizon * 1.0, radial)) * visible;
    result *= 1.0 - core * drain * 0.985;

    // ── Release ──────────────────────────────────────────────────────
    if (flash > 0.001) {
        // An expanding ring of everything it held, plus a screen-wide wash.
        float front = flash * 0.9;
        float shell = exp(-pow((radial - (1.0 - front) * horizon * 7.0)
                / max(horizon * 0.9, 0.004), 2.0));
        result += vec3(1.0, 0.97, 1.0) * shell * flash * 2.6;
        result += vec3(1.0, 0.96, 0.99) * flash * flash * 0.85;
    }

    fragColor = vec4(result, 1.0);
}
#endif

#ifdef LIGHT_COPY_DEPTH
uniform sampler2D DepthSampler;

vec4 slPackDepth(float value) {
    value = clamp(value, 0.0, 1.0);
    vec4 encoded = fract(value * vec4(1.0, 256.0, 65536.0, 16777216.0));
    encoded -= encoded.yzww * vec4(1.0 / 256.0,
            1.0 / 256.0, 1.0 / 256.0, 0.0);
    return encoded;
}

void main() {
    fragColor = slPackDepth(texture(DepthSampler, vUv).r);
}
#endif

#ifdef METEOR_SHOCK
uniform sampler2D DepthSampler;
uniform mat4 InverseProjectionMat;
uniform vec4 ShockHead;
uniform vec4 ShockAxis;

// Refraction of the scene around a meteor's shock front.
//
// A meteor's bow shock is not a light source, which is why drawing one as
// additive geometry never read as air being torn open: the head is already the
// brightest thing on screen, so more light in the same place just thickens the
// blob. What the eye actually reads as compressed air is the background being
// bent, so this displaces the scene instead of adding to it.
//
// ShockHead.xy = head position in screen UV, .z = its screen radius, .w = speed
//                0..1
// ShockAxis.xy = unit heading in screen space, .z = strength, .w = unused
// Params.xy    = target size in pixels
void main() {
    vec2 aspectVec = vec2(max(Params.x, 1.0) / max(Params.y, 1.0), 1.0);
    vec2 delta = (vUv - ShockHead.xy) * aspectVec;
    float radius = max(ShockHead.z, 0.0005) * aspectVec.x;

    // Distance in units of the shock's own radius, so the profile below is
    // independent of how close the meteor is.
    float scaled = length(delta) / radius;
    if (scaled > 2.6) {
        fragColor = vec4(texture(SceneSampler, vUv).rgb, 1.0);
        return;
    }

    vec2 heading = normalize(ShockAxis.xy + vec2(1e-6));
    vec2 outward = length(delta) < 1e-6 ? heading : normalize(delta);

    // Only the air ahead of the body is being compressed; behind it the wake is
    // the trail's business. This keeps the distortion from wrapping the head
    // into a lens, which would look like a bubble rather than a shock.
    float ahead = clamp(dot(outward, heading), -1.0, 1.0);
    float forwardLobe = smoothstep(-0.35, 0.85, ahead);

    // A shell rather than a filled disc: the displacement peaks on the front and
    // dies away on both sides of it.
    float shell = exp(-pow((scaled - 1.0) / 0.42, 2.0));

    float strength = ShockAxis.z * shell * forwardLobe
            * (0.35 + ShockHead.w * 0.85);
    if (strength < 0.0006) {
        fragColor = vec4(texture(SceneSampler, vUv).rgb, 1.0);
        return;
    }

    // Push the sample outward along the front's normal: the classic refraction
    // through a compressed shell.
    vec2 offset = outward * strength / aspectVec;
    vec2 sampleUv = clamp(vUv + offset, vec2(0.0), vec2(1.0));

    // Air at this pressure disperses, so the channels bend by different amounts.
    // Subtle on purpose -- this should register as heat haze, not as a prism.
    float fringe = strength * 0.22;
    vec3 scene;
    scene.r = texture(SceneSampler, clamp(vUv + offset * (1.0 + fringe),
            vec2(0.0), vec2(1.0))).r;
    scene.g = texture(SceneSampler, sampleUv).g;
    scene.b = texture(SceneSampler, clamp(vUv + offset * (1.0 - fringe),
            vec2(0.0), vec2(1.0))).b;

    // A faint brightening right on the front. Small, because the displacement is
    // what carries the effect; this only keeps the shell from reading as a
    // smudge on an otherwise dark sky.
    scene += vec3(0.62, 0.76, 1.0) * shell * forwardLobe * ShockAxis.z * 1.4;

    fragColor = vec4(scene, 1.0);
}
#endif

#ifdef SINGULARITY_LENS
uniform sampler2D DepthSampler;
uniform mat4 InverseProjectionMat;
// xyz = sphere centre in OpenGL eye space (-Z forward), w = its radius in blocks
uniform vec4 LensCentre;
// x = displacement amplitude in UV, y = tangential ripple amount,
// z = life 0..1 for the shell profile, w = white-edge strength
uniform vec4 LensShape;

// Screen-space refraction for Singularity, following Some of FX's shock wave
// (github.com/YangMao-Minister/some_of_fx, MIT, (c) 2026 Pizuka).
//
// The important thing this gets right and emissive geometry does not: a
// singularity's shock is not light, it is the world seen through something. Their
// pass returns a UV displacement and only a trace of added colour, and that is
// why theirs reads as a collapsing volume of space while a glowing shell reads as
// a bubble.
//
// The displacement is driven by the impact parameter -- the perpendicular
// distance from the sphere's centre to the view ray -- rather than by screen
// distance, so it stays correct as the sphere passes the camera.
void main() {
    vec2 aspectVec = vec2(max(Params.x, 1.0) / max(Params.y, 1.0), 1.0);
    float radius = max(LensCentre.w, 0.0001);

    // The ray this pixel looks along, in the same eye space the centre is in.
    vec4 clip = vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) < 0.000001) {
        fragColor = vec4(texture(SceneSampler, vUv).rgb, 1.0);
        return;
    }
    vec3 rayDir = normalize(eye.xyz / eye.w);

    // Impact parameter: |centre x dir| for a ray from the origin.
    vec3 centre = LensCentre.xyz;
    float along = dot(centre, rayDir);
    float impact = length(centre - rayDir * along);
    float distRatio = impact / radius;

    // Outside the silhouette there is nothing to bend.
    if (distRatio > 1.0 || along <= 0.0) {
        fragColor = vec4(texture(SceneSampler, vUv).rgb, 1.0);
        return;
    }

    // Behind geometry that is nearer than the sphere's front face, the sphere is
    // occluded and must not distort. Compared in view depth rather than in the
    // depth buffer's nonlinear units.
    float depth = texture(DepthSampler, vUv).r;
    if (depth < SL_SKY_DEPTH) {
        vec4 sceneClip = vec4(vUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 sceneEye = InverseProjectionMat * sceneClip;
        if (abs(sceneEye.w) > 0.000001) {
            float sceneDistance = length(sceneEye.xyz / sceneEye.w);
            float frontFace = along - sqrt(max(radius * radius - impact * impact, 0.0));
            if (sceneDistance < frontFace) {
                fragColor = vec4(texture(SceneSampler, vUv).rgb, 1.0);
                return;
            }
        }
    }

    // Screen direction away from the centre, for the radial pull. The centre's
    // screen position is handed in rather than derived here: inverting a matrix
    // per fragment to recover something constant across the whole pass would be
    // wasteful, and Java already projects it for the culling test.
    vec2 centreUv = MiscParams.xy;
    vec2 delta = (vUv - centreUv) * aspectVec;
    float screenDist = length(delta);
    vec2 dir = screenDist < 1e-5 ? vec2(0.0, 1.0) : delta / screenDist;

    float life = clamp(LensShape.z, 0.0, 1.0);
    // Their smooth profile: an envelope that dies toward the silhouette, times a
    // ring term keyed on how far the front has travelled. This is what makes the
    // distortion a travelling shell rather than a static lens.
    float env = max(0.0, 1.0 - 2.0 * distRatio);
    float ring = pow(abs(life - 2.0 * distRatio), 0.45);
    float factor = pow(max(life, 0.0001), 0.1) * env * (1.0 - life) * ring;

    vec2 offset = -dir * factor * LensShape.x;

    // A tangential ripple in the outer band, which is what stops the bend from
    // reading as a clean glass ball.
    if (LensShape.y > 0.0001 && distRatio > 0.72) {
        float band = (distRatio - 0.72) / 0.28;
        float ripple = sin(band * 18.0 - life * 12.0) * (1.0 - band);
        offset += vec2(-dir.y, dir.x) * ripple * LensShape.y;
    }

    vec2 sampleUv = clamp(vUv + offset / aspectVec, vec2(0.0), vec2(1.0));

    // Dispersion: the channels bend by slightly different amounts.
    float fringe = length(offset) * 0.35;
    vec3 scene;
    scene.r = texture(SceneSampler,
            clamp(vUv + offset * (1.0 + fringe) / aspectVec, vec2(0.0), vec2(1.0))).r;
    scene.g = texture(SceneSampler, sampleUv).g;
    scene.b = texture(SceneSampler,
            clamp(vUv + offset * (1.0 - fringe) / aspectVec, vec2(0.0), vec2(1.0))).b;

    // Only a trace of light, right on the front. The bend is the effect.
    float front = exp(-pow((distRatio - life * 0.5) / 0.1, 2.0));
    scene += vec3(0.78, 0.68, 1.0) * front * LensShape.w;

    fragColor = vec4(scene, 1.0);
}
#endif

#ifdef LIGHT_COPY_FRAME
uniform sampler2D DepthSampler;
uniform sampler2D PreviousFrameSampler;

void main() {
    float depth = texture(DepthSampler, vUv).r;
    if (depth >= SL_SKY_DEPTH) {
        fragColor = vec4(0.0);
        return;
    }
    float previous = texture(PreviousFrameSampler, vUv).r;
    float frame = MiscParams.x > 0.5 ? min(previous + 0.01, 1.0) : 0.01;
    fragColor = vec4(frame, 0.0, 0.0, 1.0);
}
#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 5c: Screen-Space Ray-Traced Reflections (SSRT)
//  In:  SceneSampler (composited HDR scene), DepthSampler (depth)
//  Out: fragColor (scene + indirect illumination via reflections)
//
//  Traces reflected view rays through screen space to simulate
//  indirect lighting.  For each pixel:
//    1. Reconstruct view-space position and normal
//    2. Reflect view direction about the normal
//    3. March along the reflected ray in view space
//    4. Project each step to screen space, check depth collision
//    5. On hit: sample scene color, apply Fresnel + distance falloff
//
//  Active only near the light source (hypernova stages 7-8)
//  where indirect illumination is visually significant.
//
//  Performance: 32 steps with adaptive step size.
// ══════════════════════════════════════════════════════════════════

#ifdef SSRT

uniform sampler2D DepthSampler;

// Reconstruct view-space position (shared)
vec3 ssrtViewPosFromDepth(vec2 uv, float depth) {
    float fov    = CameraParams.x;
    float aspect = CameraParams.y;
    float near   = CameraParams.z;
    float far    = CameraParams.w;
    float ndcX = uv.x * 2.0 - 1.0;
    float ndcY = uv.y * 2.0 - 1.0;
    float ndcZ = depth * 2.0 - 1.0;
    float viewZ = 2.0 * far * near / ((far + near) - ndcZ * (far - near));
    float halfFovTan = tan(fov * 0.5);
    float viewX = ndcX * viewZ * aspect * halfFovTan;
    float viewY = ndcY * viewZ * halfFovTan;
    return vec3(viewX, viewY, viewZ);
}

// Project view-space position → screen-space UV + expected depth
// Returns false if the point is behind the camera or off-screen
bool projectToScreen(vec3 pos, out vec2 screenUV, out float expectedDepth) {
    screenUV = vec2(0.0);
    expectedDepth = 1.0;
    float fov    = CameraParams.x;
    float aspect = CameraParams.y;
    float near   = CameraParams.z;
    float far    = CameraParams.w;

    float viewZ = pos.z;
    if (viewZ < near) return false;

    float halfFovTan = tan(fov * 0.5);
    float ndcX = pos.x / (viewZ * aspect * halfFovTan);
    float ndcY = pos.y / (viewZ * halfFovTan);

    screenUV = vec2(ndcX * 0.5 + 0.5, ndcY * 0.5 + 0.5);
    if (screenUV.x < 0.0 || screenUV.x > 1.0 || screenUV.y < 0.0 || screenUV.y > 1.0) {
        return false;
    }

    // Expected NDC depth at this position
    float ndcZ = (far + near - 2.0 * far * near / viewZ) / (far - near);
    expectedDepth = ndcZ * 0.5 + 0.5;
    return true;
}

void main() {
    float ssrIntensity = MiscParams.x;
    vec3 scene = texture(SceneSampler, vUv).rgb;
    float depth = texture(DepthSampler, vUv).r;

    // Skip sky (infinite depth) and when intensity is off
    if (depth >= 0.999 || ssrIntensity < 0.02) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Reconstruct ──────────────────────────────────────────────
    vec3 viewPos = ssrtViewPosFromDepth(vUv, depth);

    // Only trace near the light source
    vec3 toLight = LightViewPos.xyz - viewPos;
    float distToLight = length(toLight);
    if (distToLight > LightViewPos.w * 2.5) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Screen-space normal ──────────────────────────────────────
    vec2 ts = vec2(1.0 / Params.x, 1.0 / Params.y);
    vec3 posR = ssrtViewPosFromDepth(vUv + vec2(ts.x, 0.0),
                   texture(DepthSampler, vUv + vec2(ts.x, 0.0)).r);
    vec3 posL = ssrtViewPosFromDepth(vUv - vec2(ts.x, 0.0),
                   texture(DepthSampler, vUv - vec2(ts.x, 0.0)).r);
    vec3 posU = ssrtViewPosFromDepth(vUv + vec2(0.0, ts.y),
                   texture(DepthSampler, vUv + vec2(0.0, ts.y)).r);
    vec3 posD = ssrtViewPosFromDepth(vUv - vec2(0.0, ts.y),
                   texture(DepthSampler, vUv - vec2(0.0, ts.y)).r);

    float maxDz = max(max(abs(posR.z-viewPos.z), abs(posL.z-viewPos.z)),
                      max(abs(posU.z-viewPos.z), abs(posD.z-viewPos.z)));
    vec3 N;
    if (maxDz > 8.0) {
        N = vec3(0.0, 0.0, -1.0); // camera-facing
    } else {
        vec3 dx = normalize(posR - posL);
        vec3 dy = normalize(posU - posD);
        N = normalize(cross(dx, dy));
        if (dot(N, vec3(0.0, 0.0, -1.0)) < 0.0) N = -N;
    }

    // ── View direction ───────────────────────────────────────────
    vec3 V = normalize(vec3(0.0, 0.0, 1.0)); // toward camera (+Z in camera-basis)

    // Fresnel at this pixel
    float fresnel = pow(1.0 - abs(dot(N, V)), 5.0);
    fresnel = mix(0.04, 1.0, fresnel);

    // Only trace surfaces that can reflect (skip glancing angles for sky)
    if (fresnel < 0.05) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Reflect view direction ───────────────────────────────────
    vec3 R = reflect(-V, N);

    // ── Ray march ────────────────────────────────────────────────
    float rayDist = 0.0;
    float maxDist = distToLight * 1.3;
    int maxSteps = 32;
    float stepSize = maxDist / float(maxSteps);

    // Jitter
    rayDist += postHash(vUv + fract(TimePack.x * 100.0)) * stepSize * 0.5;

    vec3 hitColor = vec3(0.0);
    float hitWeight = 0.0;

    for (int i = 0; i < 32; i++) {
        if (i >= maxSteps || rayDist > maxDist) break;

        vec3 rayPos = viewPos + R * rayDist;
        vec2 screenUV;
        float expectedDepth;

        if (!projectToScreen(rayPos, screenUV, expectedDepth)) {
            rayDist += stepSize;
            continue;
        }

        float actualDepth = texture(DepthSampler, screenUV).r;
        float depthDiff = actualDepth - expectedDepth;

        // Collision: surface is in front of the ray point
        if (depthDiff > -stepSize * 0.8 && depthDiff < stepSize * 3.0) {
            vec3 reflectedColor = texture(SceneSampler, screenUV).rgb;

            // Distance falloff along the ray
            float distAtten = 1.0 / (1.0 + rayDist * rayDist * 0.005);

            // Proximity to light source (closer = brighter reflection)
            vec3 hitViewPos = ssrtViewPosFromDepth(screenUV, actualDepth);
            float hitDistToLight = length(LightViewPos.xyz - hitViewPos);
            float lightProximity = exp(-hitDistToLight / LightViewPos.w);

            hitColor = reflectedColor * fresnel * distAtten * lightProximity;
            hitWeight = 1.0;
            break;
        }

        // Adaptive step (larger steps further from camera)
        float adaptiveStep = stepSize * (0.5 + abs(rayPos.z) * 0.015);
        rayDist += max(adaptiveStep, stepSize * 0.25);
    }

    // ── Composite ────────────────────────────────────────────────
    float blend = ssrIntensity * 0.55;

    // Edge fade (reflections tend to disappear at screen edges)
    float edgeFade = 1.0 - smoothstep(0.7, 1.0, length(vUv - 0.5) * 2.0);
    blend *= edgeFade;

    // Stronger blend for rough surfaces (low fresnel = rough)
    blend *= mix(0.4, 1.0, fresnel);

    vec3 result = scene + hitColor * blend;

    fragColor = vec4(result, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 6: Black Hole Center (screen-space, HDR)
//  In:  SceneSampler (composited HDR scene with 3D BH, bloom, etc.)
//  Out: fragColor (scene + screen-space BH center)
//
//  Renders the black hole in screen space (n = radius / horizon radius):
//    1. Event horizon    — pure black disk, crisp edge (n < 1)
//    2. Horizon outline  — thin bright rim at the capture radius (n ≈ 1)
//    3. Photon ring      — extreme HDR ring at the photon sphere (n ≈ 1.3)
//    4. Accretion disk   — Keplerian differential rotation (ω ∝ n^-1.5),
//                          spiral density waves + fbm turbulence, relativistic
//                          doppler beaming, blackbody temperature ramp
//    + Screen-space gravitational lensing on the real scene background
//    + Photon sphere volumetric glow + polar corona
//
//  Runs BEFORE ACES so HDR photon ring survives bloom extraction
//  and tone mapping.
// ══════════════════════════════════════════════════════════════════

#ifdef BLACK_HOLE

// ── Pseudo-random hash (for accretion disk turbulence) ──────
vec3 bhAccretionDisk(vec2 delta, float time, float rs, float rotSpeed) {
    float safeRs = max(rs, 0.001);

    // A steeply inclined equatorial plane reads as a disk instead of a halo.
    vec2 diskP = vec2(delta.x, (delta.y + safeRs * 0.035) / 0.235);
    float diskR = length(diskP);
    float n = diskR / safeRs;
    float screenN = length(delta) / safeRs;
    if (n > 8.4 && screenN > 3.8) return vec3(0.0);

    float ang = atan(diskP.y, diskP.x);
    float omega = 5.4 * pow(max(n, 1.0), -1.5) * rotSpeed;
    float rotAng = ang - time * omega;

    float diskMask = smoothstep(1.48, 1.72, n)
                   * (1.0 - smoothstep(7.2, 8.35, n));

    float profile = exp(-max(n - 1.7, 0.0) * 0.70);
    profile += 0.52 * exp(-abs(n - 2.65) * 1.9);
    profile += 0.28 * exp(-abs(n - 4.15) * 1.4);
    profile += 0.13 * exp(-abs(n - 6.35) * 1.1);

    float spiralA = 0.5 + 0.5 * sin(rotAng * 2.0 + log(max(n, 1.01)) * 10.5);
    float spiralB = 0.5 + 0.5 * sin(rotAng * 5.0 - log(max(n, 1.01)) * 6.5 + 1.4);
    float turb = bhFbm(vec2(rotAng * 2.2, n * 3.3) + vec2(time * 0.10, 0.0));
    float filaments = smoothstep(0.34, 0.78,
            spiralA * 0.48 + spiralB * 0.20 + turb * 0.56);
    float darkLane = smoothstep(0.28, 0.66,
            bhFbm(vec2(rotAng * 4.1 + 8.0, n * 6.8 - time * 0.16)));
    float emission = diskMask * profile
                   * (0.16 + filaments * 1.20 + turb * 0.34)
                   * mix(0.52, 1.0, darkLane);

    // Relativistic Doppler beaming and temperature shift.
    float approaching = clamp(-diskP.x / max(diskR, 0.0001), -1.0, 1.0);
    float beam = pow(max(0.38, 1.0 + approaching * 0.72), 2.35);

    float temp = clamp((n - 1.55) / 6.3, 0.0, 1.0);
    vec3 col = mix(vec3(0.78, 0.90, 1.35), vec3(1.35, 0.58, 0.08),
                   smoothstep(0.02, 0.43, temp));
    col = mix(col, vec3(0.48, 0.035, 0.008), smoothstep(0.43, 1.0, temp));
    col *= mix(vec3(1.32, 0.77, 0.52), vec3(0.68, 0.86, 1.38),
               approaching * 0.5 + 0.5);

    // Far-side matter is bent over the shadow into a thin Einstein arc.
    float farArc = exp(-abs(screenN - 1.58) * 18.0);
    farArc *= smoothstep(-0.18 * safeRs, 0.72 * safeRs, delta.y);
    farArc *= 0.32 + 0.68 * smoothstep(0.0, 2.6 * safeRs, abs(delta.x));
    float arcNoise = 0.62 + 0.38 * bhNoise(vec2(rotAng * 5.0, time * 0.35));
    vec3 arcCol = mix(vec3(1.45, 0.45, 0.06), vec3(1.0, 0.92, 0.72),
                      clamp(approaching * 0.5 + 0.5, 0.0, 1.0));

    return col * emission * beam * 2.75 + arcCol * farArc * arcNoise * 1.8;
}

// ══════════════════════════════════════════════════════════════
//  Polar jet / corona (vertical emission near the poles)
// ══════════════════════════════════════════════════════════════

float bhCorona(vec2 delta, float rs) {
    // Vertical jet-like emission along the y-axis
    float jet = exp(-abs(delta.x) * 8.0 / max(rs, 0.01))
              * exp(-abs(delta.y - rs * 0.3) * 4.0 / max(rs, 0.01));
    jet += exp(-abs(delta.x) * 6.0 / max(rs, 0.01))
          * exp(-abs(delta.y + rs * 0.3) * 4.0 / max(rs, 0.01)) * 0.5;
    return jet * 0.15;
}

// ══════════════════════════════════════════════════════════════
//  Main
// ══════════════════════════════════════════════════════════════

void main() {
    vec2 centerUV = Center1.xy * 0.5 + 0.5;     // NDC → UV
    vec2 aspect = vec2(1.0, Params.y / Params.x);   // height → width correction

    vec2 delta = (vUv - centerUV) * aspect;
    float r = length(delta);                      // aspect-corrected distance

    // ── BH parameters ───────────────────────────────────────────
    float bhRadius   = BHParams.x;                 // event horizon in UV space
    float stage      = BHParams.y;
    float progress   = BHParams.z;
    float intensity  = clamp(BHParams.w, 0.0, 5.0);
    float time       = TimePack.x;

    // Scale radius by stage (forming → grow, collapse → shrink)
    float finalRs = bhRadius;

    // Stage 3 (BLACK_HOLE forming): exponential growth from small to full
    if (stage > 2.5 && stage < 3.5) {
        float growT = 1.0 - exp(-progress * 4.0);
        finalRs *= growT;

    // Stage 5 (COLLAPSE): quadratic shrink
    } else if (stage > 4.5) {
        float shrinkT = 1.0 - progress * progress * 0.85;
        finalRs *= max(shrinkT, 0.01);
    }

    // Early-out: negligible, or fully outside the influence zone
    float n = r / max(finalRs, 0.001);
    if (finalRs < 0.001 || n > 9.0) {
        fragColor = texture(SceneSampler, vUv);
        return;
    }

    // Stage-entry smooth ramp (forming only — collapse shrinks instead)
    float stageRamp = 1.0;
    if (stage > 2.5 && stage < 3.5) {
        stageRamp = smoothstep(0.0, 0.10, progress);
    } else if (stage > 4.5) {
        // Collapse: emissions (disk, photon ring, rim, glow, corona) die
        // out before the VOID stage — the pass deactivates at the boundary,
        // so anything still emitting would pop off.
        stageRamp = 1.0 - smoothstep(0.60, 0.92, progress);
    }

    // ════════════════════════════════════════════════════════════
    //  1. Screen-space gravitational lensing
    //  Warp the scene behind/across the black hole
    // ════════════════════════════════════════════════════════════

    vec2 lensedUv = bhGravLens(vUv, centerUV, finalRs, aspect);
    // Blend the lensing by stageRamp too — it grows during forming and
    // relaxes during collapse, so the warped region never pops.
    vec3 sceneLensed = texture(SceneSampler, mix(vUv, lensedUv, stageRamp)).rgb;
    vec3 sceneOrig   = texture(SceneSampler, vUv).rgb;

    // ════════════════════════════════════════════════════════════
    //  2. Black hole components (n = radius in event-horizon units)
    // ════════════════════════════════════════════════════════════

    // ── Event horizon: pure black disk with a crisp edge ─────
    float eh = 1.0 - smoothstep(0.97, 1.01, n);

    // ── Event horizon outline: thin bright rim right at the edge ──
    // (light trapped at the capture radius outlines the shadow)
    float rim = exp(-abs(n - 1.02) * 55.0);

    // ── Photon ring: ultra-thin HDR ring at the photon sphere ──
    float pr  = exp(-abs(n - 1.32) * 42.0);
    float pr2 = exp(-abs(n - 1.22) * 28.0) * 0.30;

    // ── Accretion disk (Keplerian rotation, doppler beamed) ──
    float rotSpeed = (stage > 4.5) ? 2.2 : 1.0;  // spin-up during collapse
    vec3 diskCol = bhAccretionDisk(delta, time, finalRs, rotSpeed);

    // ── Photon-sphere volumetric glow ──
    float sphereGlow     = exp(-abs(n - 1.3) * 7.0) * 0.50;
    float sphereGlowWide = exp(-abs(n - 1.6) * 3.0) * 0.20;

    // ── Corona (polar emission) ──
    float corona = bhCorona(delta, finalRs);

    // ════════════════════════════════════════════════════════════
    //  3. Color assembly
    // ════════════════════════════════════════════════════════════

    // Start with the gravitationally lensed scene
    vec3 color = sceneLensed;

    // Event horizon: pure black (overwrites scene)
    color = mix(color, vec3(0.0), eh);

    // Accretion disk (additive over the lensed background)
    color += diskCol * intensity * stageRamp;

    // Photon ring: extreme HDR → picked up by bloom + ACES
    color += vec3(18.0, 13.0, 7.0) * pr * intensity * stageRamp;
    color += vec3(18.0, 13.0, 7.0) * pr2 * 0.4 * intensity * stageRamp;

    // Event horizon outline rim
    color += vec3(1.6, 1.2, 0.45) * rim * intensity * stageRamp;

    // Photon-sphere volumetric glow
    color += vec3(2.2, 1.5, 0.50) * sphereGlow * intensity * stageRamp;
    color += vec3(1.2, 0.8, 0.25) * sphereGlowWide * intensity * stageRamp;

    // Corona: faint purple glow at poles
    color += vec3(0.6, 0.3, 0.9) * corona * intensity * stageRamp;

    // ════════════════════════════════════════════════════════════
    //  4. Influence zone: smooth blend outside the BH region
    // ════════════════════════════════════════════════════════════

    float influence = 1.0 - smoothstep(finalRs * 1.4, finalRs * 8.7, r);
    influence = max(influence, eh); // horizon is always fully opaque black
    vec3 finalColor = mix(sceneOrig, color, influence);

    fragColor = vec4(finalColor, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 7: Glow Flash (预爆闪烁)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (scene + flashing glow center)
//
//  Bright pulsing glow at the black hole center before supernova:
//    - Central bright spot with flickering (sin × random envelope)
//    - Expanding halo rings (multiple Gaussian falloffs)
//    - Color: white core → blue/purple halo as radius grows
//    - HDR output so bloom picks up the flash
//
//  BHParams:  y=stage(6), z=preExpansionRadius, w=flashIntensity
// ══════════════════════════════════════════════════════════════════

#ifdef GLOW_FLASH

// ── Pseudo-random for flicker envelope ──
float gfHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 centerUV = Center1.xy * 0.5 + 0.5;
    vec2 delta = vUv - centerUV;
    float dist = length(delta);

    float flashIntensity  = BHParams.w;           // bell pulse 0→1→0
    float preExpansionR   = max(BHParams.z, 0.01); // progress 0→1

    if (flashIntensity < 0.01) {
        fragColor = texture(SceneSampler, vUv);
        return;
    }

    float time = TimePack.x;

    // ── Flicker: fast sine envelope ──
    float flicker = sin(time * 70.0 + gfHash(vec2(floor(time * 4.0), 0.0)) * 6.28) * 0.5 + 0.5;
    flicker = mix(flicker, 1.0, 0.3);
    float flashMod = flashIntensity * flicker;
    // Die out fully before the hypernova stage — this pass deactivates
    // at the boundary, and any residual core brightness would pop off.
    flashMod *= 1.0 - smoothstep(0.85, 1.0, preExpansionR);

    // Radius grows from 0 to ~1.2 screen units over the flash duration
    float ringRadius = preExpansionR * 1.2;

    // ── Core: extremely bright central spot (HDR) ──
    float core = exp(-dist * 80.0) * flashMod * 200.0;

    // ── Inner glow: softer, wider ──
    float innerGlow = exp(-dist * 15.0) * flashMod * 30.0;

    // ── Main ring: expands from center ──
    float ring = exp(-abs(dist - ringRadius) * 35.0) * flashMod * 15.0;
    // Ring echo (inner)
    ring += exp(-abs(dist - ringRadius * 0.5) * 30.0) * flashMod * 6.0;
    // Ring precursor (outer, fainter)
    ring += exp(-abs(dist - ringRadius * 1.6) * 18.0) * flashMod * 3.0;

    // ── Wide halo ──
    float wideHalo = exp(-dist / (ringRadius * 1.5 + 0.1)) * flashMod * 8.0;

    // ── Color progression ──
    // Core: pure white
    vec3 coreColor = vec3(1.0, 1.0, 1.0);
    // Ring: white → electric blue → purple
    float colorT = preExpansionR;
    vec3 ringColor = mix(vec3(1.0, 1.0, 1.0), vec3(0.4, 0.65, 1.0), smoothstep(0.0, 0.5, colorT));
    ringColor = mix(ringColor, vec3(0.6, 0.2, 1.0), smoothstep(0.5, 1.0, colorT));
    // Halo: warm purple → deep violet
    vec3 haloColor = mix(vec3(0.7, 0.35, 1.0), vec3(0.3, 0.1, 0.7), colorT);

    // ── Composite ──
    vec3 scene = texture(SceneSampler, vUv).rgb;
    vec3 glowColor = coreColor * core
                   + ringColor * (innerGlow + ring)
                   + haloColor * wideHalo;

    // Global screen brightening (very subtle)
    glowColor += vec3(0.4, 0.3, 1.0) * flashMod * 0.8;

    fragColor = vec4(scene + glowColor, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 8: Shockwave (超新星冲击波)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (scene with shockwave distortion + glow)
//
//  Enhanced expanding blast:
//    - Three concentric shells (primary front + two echoes), each with
//      easeOutCubic expansion driven by hypernova progress
//    - Refraction warp: UVs bent along the radial direction at crests
//    - Chromatic split: R/B channels separate at the wave front
//    - HDR crest brightness (feeds bloom), white → electric blue ramp
//    - Radial glow: hot expanding core fading behind the front
//    - Angular turbulence on the front (debris look)
//
//  BHParams:  y=stage(8), z=hypernova progress (0→1), w=intensity (merge)
// ══════════════════════════════════════════════════════════════════

#ifdef SHOCKWAVE

void main() {
    vec2 centerUV = Center1.xy * 0.5 + 0.5;
    vec2 aspect = vec2(1.0, Params.y / Params.x);
    vec2 delta = (vUv - centerUV) * aspect;
    float dist = length(delta);
    vec2 dir = delta / max(dist, 0.0001);

    float p = clamp(BHParams.z, 0.0, 1.0);   // hypernova progress
    float intensity = BHParams.w;            // merge multiplier

    // easeOutCubic expansion: fast detonation, slow settle
    float e = 1.0 - pow(1.0 - p, 3.0);

    // ── Three concentric shells ─────────────────────────────────
    float R1 = e * 1.35;
    float R2 = e * 0.95 + 0.015;
    float R3 = e * 0.60 + 0.008;

    // Angular turbulence: the front is not a perfect circle
    float ang = atan(delta.y, delta.x);
    float turb = 0.80 + 0.20 * sin(ang * 14.0 + dist * 30.0 + p * 18.0)
                      * sin(ang * 5.0 - p * 9.0);

    float w1 = exp(-abs(dist - R1 * turb) * 26.0);
    float w2 = exp(-abs(dist - R2 * turb) * 22.0) * 0.55;
    float w3 = exp(-abs(dist - R3 * turb) * 18.0) * 0.30;

    // Delayed shells turn the longer hypernova into a sequence of detonations.
    float p4 = clamp((p - 0.22) / 0.78, 0.0, 1.0);
    float e4 = 1.0 - pow(1.0 - p4, 3.0);
    float w4 = exp(-abs(dist - e4 * 1.22 * turb) * 28.0)
             * smoothstep(0.0, 0.08, p4)
             * (1.0 - smoothstep(0.88, 1.0, p4)) * 0.78;

    float p5 = clamp((p - 0.50) / 0.50, 0.0, 1.0);
    float e5 = 1.0 - pow(1.0 - p5, 2.6);
    float w5 = exp(-abs(dist - e5 * 1.02 * turb) * 24.0)
             * smoothstep(0.0, 0.10, p5)
             * (1.0 - smoothstep(0.84, 1.0, p5)) * 0.58;

    // ── Refraction warp: bend UVs radially at the crests ────────
    float warp = (w1 * 0.040 + w2 * 0.024 + w3 * 0.014
                + w4 * 0.029 + w5 * 0.022) * intensity;
    warp *= smoothstep(0.0, 0.04, p);       // ease in, no pop
    warp *= 1.0 - p * 0.5;                  // calm down as it expands
    vec2 warpedUv = vUv - dir * warp / aspect;

    // ── Chromatic split at the wave front ───────────────────────
    float chroma = (w1 * 0.008 + w2 * 0.004 + w4 * 0.005 + w5 * 0.004)
                 * intensity * (1.0 - p * 0.45);
    vec2 cOff = dir * chroma / aspect;
    vec3 scene;
    scene.r = texture(SceneSampler, warpedUv + cOff).r;
    scene.g = texture(SceneSampler, warpedUv).g;
    scene.b = texture(SceneSampler, warpedUv - cOff).b;

    // ── HDR crest brightness: white-hot → electric blue ─────────
    float crest = (w1 * 3.4 + w2 * 1.8 + w3 * 1.0 + w4 * 2.35 + w5 * 1.75)
                * (1.0 - p * 0.42) * intensity;
    vec3 crestCol = mix(vec3(1.0, 0.98, 0.92), vec3(0.55, 0.72, 1.0),
                        clamp(p * 1.4, 0.0, 1.0));
    scene += crestCol * crest;

    // ── Radial glow: hot core fading behind the expanding front ──
    // The final smoothstep guarantees zero at the stage boundary —
    // this pass deactivates when afterglow begins.
    float glowR = max(R1, 0.03);
    float reigniteA = (p - 0.38) / 0.11;
    float reigniteB = (p - 0.64) / 0.09;
    float coreReignite = exp(-reigniteA * reigniteA) * 0.85
                       + exp(-reigniteB * reigniteB) * 0.55;
    float radialGlow = exp(-dist * 3.5 / glowR)
                     * (exp(-p * 2.2) * 2.15 + coreReignite) * intensity;
    radialGlow *= 1.0 - smoothstep(0.85, 1.0, p);
    scene += vec3(1.0, 0.75, 0.45) * radialGlow;

    // Fade influence near screen edges
    float edgeFade = 1.0 - smoothstep(0.75, 1.0, length(vUv - 0.5) * 2.0);
    vec3 original = texture(SceneSampler, vUv).rgb;
    fragColor = vec4(mix(original, scene, edgeFade), 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 8b: Flash Screen (全屏闪光)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (scene + global white flash)
//
//  Global white flash during hypernova initiation:
//    - Quick bell-curve: 0→1→0 over ~0.3s
//    - Core: full white overlay
//    - Edge: subtle vignette preserved
//    - HDR output so bloom picks up the flash after ACES
//
//  BHParams:  z=flashIntensity(0→1), w=flashDuration
// ══════════════════════════════════════════════════════════════════

#ifdef FLASH_SCREEN

void main() {
    vec3 scene = texture(SceneSampler, vUv).rgb;

    // ── Auto-compute flash intensity from BHParams ─────────────
    float flashIntensity;
    if (abs(BHParams.y - 7.0) < 0.5) {
        // Stage 7 (FLASH): intensity directly in w (bell-curve pulse)
        flashIntensity = BHParams.w;
    } else if (abs(BHParams.y - 8.0) < 0.5) {
        // Stage 8 (HYPERNOVA): z=progress, w=intensity multiplier.
        // Fast exponential decay after detonation; smoothstep ease-in over
        // the first ~5% so the white-out ramps up instead of popping on.
        float p = BHParams.z;
        float primary = exp(-p * 6.0) * 1.0;
        float reignitePhase = (p - 0.34) / 0.055;
        float reignite = exp(-reignitePhase * reignitePhase) * 0.42;
        flashIntensity = (primary + reignite) * BHParams.w;
        flashIntensity *= smoothstep(0.0, 0.05, p);
    } else {
        flashIntensity = BHParams.z;
    }
    if (flashIntensity < 0.001) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    // ── Global white flash ──────────────────────────────────────
    // Re-shape through smoothstep: zero-slope onset, no harsh snap.
    float flash = clamp(flashIntensity, 0.0, 1.0);
    flash = flash * flash * (3.0 - 2.0 * flash);

    // Slight color: warm white → cool white based on intensity
    vec3 flashColor = mix(vec3(1.0, 0.95, 0.85), vec3(1.0, 1.0, 1.0), flash);

    // Preserve vignette at screen edges (flash fades at corners)
    float vignette = 1.0 - length(vUv - 0.5) * 0.5;

    // Composite: scene → partially flash → near-white at peak
    vec3 result = mix(scene, flashColor * 1.5, flash * vignette);

    // At peak flash (>0.8), push toward pure white
    result = mix(result, vec3(1.0), smoothstep(0.8, 1.0, flash) * 0.9);

    fragColor = vec4(result, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 8c: Temporal Afterimage (残影 / 拖尾)
//  In:  SceneSampler (current composited frame)
//       BloomSampler (previous frame history)
//  Out: fragColor (current + trail blend)
//
//  Blends current frame with previous frames for motion trails:
//    current + prev*0.5 + prev2*0.25
//  Creates cinematic drag / ghosting effect during hypernova.
//
//  BHParams:  z=afterimageStrength (0=none, 1=full)
// ══════════════════════════════════════════════════════════════════

#ifdef AFTERIMAGE

void main() {
    vec3 current = texture(SceneSampler, vUv).rgb;
    vec3 prev    = texture(BloomSampler, vUv).rgb;

    // Afterimage strength from hypernova progress (BHParams.z):
    // strongest at detonation, quadratic decay as the explosion settles.
    float strength = 1.0 - BHParams.z;
    strength *= strength;
    if (strength < 0.01) {
        fragColor = vec4(current, 1.0);
        return;
    }

    // Blend: current dominates, previous frame ghosts behind
    vec3 blend = current + prev * 0.5 * strength;

    // Slight desaturation on the trail (makes it look like an afterimage)
    float lum = dot(prev, vec3(0.299, 0.587, 0.114));
    blend += lum * 0.15 * strength;

    fragColor = vec4(blend, 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass 9: ACES Tone Mapping (HDR → LDR)
//  In:  SceneSampler (composited HDR scene)
//  Out: fragColor (LDR 0..1)
//
//  Must run LAST, after all HDR effects are composited.
//
//  chainFade (MiscParams.z) crossfades between the raw (clamped) scene
//  and the tone-mapped + vignetted output.  Without this, the moment
//  the post chain activates the ENTIRE screen would suddenly shift
//  brightness/contrast — the "fullscreen flash" bug.  With the fade
//  the filmic look eases in over ~0.9s (smoothstep, driven from Java).
// ══════════════════════════════════════════════════════════════════

#ifdef ACES

void main() {
    vec3 hdr = texture(SceneSampler, vUv).rgb;
    float chainFade = clamp(MiscParams.z, 0.0, 1.0);

    // Apply ACES tone mapping (handles HDR → LDR)
    vec3 mapped = aces(hdr);

    // Subtle vignette (scaled in with the chain)
    vec2 uvC = vUv - 0.5;
    float vignette = 1.0 - dot(uvC, uvC) * 0.35;

    vec3 graded = mapped * vignette;
    vec3 raw    = clamp(hdr, 0.0, 1.0);

    fragColor = vec4(mix(raw, graded, chainFade), 1.0);
}

#endif

// ══════════════════════════════════════════════════════════════════
//  Pass: Composite (scene + bloom, pre-ACES)
//  Used internally between BLUR_V and subsequent passes.
//  Not a standalone pass — composed inline by the processor.
// ══════════════════════════════════════════════════════════════════

#ifdef COMPOSITE

void main() {
    vec3 scene = texture(SceneSampler, vUv).rgb;
    vec3 bloom = texture(BloomSampler, vUv).rgb;
    float strength = Params.z;
    fragColor = vec4(scene + bloom * strength, 1.0);
}

#endif

// ── Fallback ──────────────────────────────────────────────────────

#if !defined(BRIGHT_PASS) && !defined(BRIGHT_PASS_EDGE) \
    && !defined(BLUR_H) && !defined(BLUR_V) \
    && !defined(DISTORTION) && !defined(GODRAY) && !defined(VOLUMETRIC_GODRAY) \
    && !defined(CHROMATIC) && !defined(SCREEN_LIGHTING) && !defined(SSRT) \
    && !defined(LIGHT_TEMPORAL) && !defined(LIGHT_SPATIAL) && !defined(LIGHT_BLEND) \
    && !defined(LIGHT_COPY_DEPTH) && !defined(LIGHT_COPY_FRAME) \
    && !defined(BLACK_HOLE) && !defined(GLOW_FLASH) && !defined(SHOCKWAVE) \
    && !defined(FLASH_SCREEN) && !defined(AFTERIMAGE) \
    && !defined(ACES) && !defined(COMPOSITE) && !defined(STARLESS) \
    && !defined(METEOR_SHOCK) && !defined(SINGULARITY_LENS) \
    && !defined(GARGANTUA_LENS) && !defined(COSMIC_HORSESHOE_LENS)

void main() {
    fragColor = texture(SceneSampler, vUv);
}
#endif
