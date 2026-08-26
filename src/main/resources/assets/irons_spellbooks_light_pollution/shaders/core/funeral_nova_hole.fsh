#version 150

// Forge 1.20.1 port of Gemini KillEffect (LGPL-2.1).

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i+vec2(1,0)), f.x),
               mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v=0.0, a=0.5;
    for(int i=0; i<4; i++){ v+=a*noise(p); p*=2.0; a*=0.5; }
    return v;
}

float easeOutExpo(float t)  { return t >= 1.0 ? 1.0 : 1.0 - pow(2.0, -10.0 * t); }
float easeInExpo(float t)   { return t <= 0.0 ? 0.0 : pow(2.0, 10.0 * (t - 1.0)); }
float easeInOutCubic(float t){ return t < 0.5 ? 4.0*t*t*t : 1.0-pow(-2.0*t+2.0,3.0)/2.0; }

vec2 applyLensing(vec2 uv, float rs, float time, float stage) {
    float d = length(uv);
    if (d < 0.001) return uv;

    float b = d;
    vec2 dir = uv / b;

    float alpha = rs * 0.18 / (b * b + rs * rs * 0.04);

    float rPhoton   = rs * 1.5;
    float distToPhoton = abs(b - rPhoton);
    float criticalBoost = 1.0 + exp(-distToPhoton * 30.0 / rs) * 2.5;

    float stageBoost = 1.0;
    if (stage > 3.5 && stage < 4.5) stageBoost = 1.4;
    if (stage > 4.5)                 stageBoost = 1.8;

    alpha *= criticalBoost * stageBoost;

    return uv + dir * alpha;
}

vec2 accretionDisk(vec2 uvLensed, float rs, float angle, float time, float stage) {
    float safeRs = max(rs, 0.012);
    float shadowRadius = safeRs * 2.6;
    float innerRadius = safeRs * 2.75;
    float outerRadius = safeRs * 4.35;

    // The Slang renderer's very thin ellipse turns into two saturated needles
    // once Forge's additive world pass and bloom are combined. Keep the strong
    // inclination, but give the disk enough vertical body and feather its tips.
    vec2 diskUv = vec2(uvLensed.x, (uvLensed.y + safeRs * 0.04) / 0.34);
    float diskR = length(diskUv);
    float n = diskR / safeRs;
    float diskAngle = atan(diskUv.y, diskUv.x);

    float diskMask = smoothstep(innerRadius * 0.76, innerRadius, diskR)
                   * (1.0 - smoothstep(outerRadius * 0.74, outerRadius, diskR));
    float tipFade = 1.0 - smoothstep(outerRadius * 0.66, outerRadius, abs(diskUv.x));

    float phase = time;
    if (stage > 3.5 && stage < 4.5) phase = 1.0 + time * 1.65;
    if (stage > 4.5) phase = 2.65 + time * 2.25;
    float omega = 6.2 * pow(max(n, 1.0), -1.5);
    float rotatingAngle = diskAngle - phase * omega;

    float armA = 0.5 + 0.5 * sin(rotatingAngle * 2.0 + log(max(n, 1.01)) * 11.0);
    float armB = 0.5 + 0.5 * sin(rotatingAngle * 5.0 - log(max(n, 1.01)) * 7.0 + 1.7);
    float turbulence = fbm(vec2(rotatingAngle * 2.3, n * 3.8) + vec2(phase * 0.18, 0.0));
    float filaments = smoothstep(0.42, 0.78, armA * 0.55 + armB * 0.20 + turbulence * 0.55);
    float dustLanes = smoothstep(0.30, 0.62,
            fbm(vec2(rotatingAngle * 4.0 + 7.0, n * 7.0 - phase * 0.25)));

    float emissivity = exp(-max(n - 2.8, 0.0) * 0.72);
    emissivity += exp(-abs(n - 3.35) * 2.8) * 0.50;
    emissivity += exp(-abs(n - 4.15) * 3.5) * 0.24;
    float diskFront = diskMask * emissivity
                    * (0.20 + filaments * 1.10 + turbulence * 0.38)
                    * mix(0.62, 1.0, dustLanes)
                    * mix(0.42, 1.0, tipFade);

    vec2 arcUv = vec2(uvLensed.x, uvLensed.y * 0.78);
    float arcR = length(arcUv);
    float farArc = exp(-abs(arcR - shadowRadius * 1.34) * 34.0 / safeRs);
    farArc *= smoothstep(-safeRs * 0.25, safeRs * 0.75, uvLensed.y);
    farArc *= 0.28 + 0.72 * smoothstep(0.0, shadowRadius * 1.5, abs(uvLensed.x));
    farArc *= 0.48 + turbulence * 0.40;

    float stageGain = 1.0;
    if (stage > 2.5 && stage < 3.5) stageGain = smoothstep(0.10, 0.78, time);
    if (stage > 3.5 && stage < 4.5) stageGain = 1.38;
    if (stage > 4.5) stageGain = (1.38 + time * 1.6) * (1.0 - smoothstep(0.78, 1.0, time));

    float diskTemp = clamp((diskR - innerRadius) / max(outerRadius - innerRadius, 0.001), 0.0, 1.0);
    return vec2((diskFront + farArc) * stageGain, diskTemp);
}

void main() {
    float time      = vertexColor.r;
    float stage     = vertexColor.g * 8.0;
    float brightness = vertexColor.b * 4.0;
    float alpha     = vertexColor.a;

    float rs = 0.22;

    float shadowRadius = rs * 2.6;

    if (stage > 2.5 && stage < 3.5) {

        float formT = easeOutExpo(time);
        rs     *= formT;
        shadowRadius = rs * 2.6;
    } else if (stage > 4.5) {

        float collapseT = easeInExpo(time);
        rs     *= (1.0 - collapseT * 0.85);
        shadowRadius = rs * 2.6;
    }

    vec2 uv = (uvCoord - 0.5) * 2.0;
    float d = length(uv);
    float angle = atan(uv.y, uv.x);

    vec2 uvLensed = applyLensing(uv, rs, time, stage);
    float dLensed = length(uvLensed);
    float angleLensed = atan(uvLensed.y, uvLensed.x);

    float insideShadow = 1.0 - smoothstep(shadowRadius - 0.01, shadowRadius + 0.005, dLensed);

    float penumbra = 1.0 - exp(-(dLensed - shadowRadius) * 60.0 / rs);
    penumbra = clamp(penumbra * insideShadow, 0.0, 1.0);

    vec2 diskResult = accretionDisk(uvLensed, rs, angleLensed, time, stage);
    float diskBrightness = diskResult.x;
    float diskTemp       = diskResult.y;

    float doppler = uvLensed.x * 0.5 + 0.5;
    float beaming = 1.0 + (1.0 - doppler) * 2.5;

    diskBrightness *= (0.3 + beaming * 0.7);

    float photonRingRadius = shadowRadius * 1.02;

    float photonRing = exp(-abs(dLensed - photonRingRadius) * 250.0);

    float photonRing2 = exp(-abs(dLensed - photonRingRadius * 0.97) * 180.0) * 0.3;

    float stars = 0.0;
    vec3 starColor = vec3(0.0);

    for (int i = 0; i < 8; i++) {
        float fi = float(i);

        vec2 starOrig = vec2(
            hash(vec2(fi, 0.3)) * 4.0 - 2.0,
            hash(vec2(fi, 0.7)) * 4.0 - 2.0
        );
        float starOrigDist = length(starOrig);

        if (starOrigDist < 2.5 && starOrigDist > shadowRadius * 0.5) {

            vec2 starLensed = applyLensing(starOrig, rs, time, stage);
            float starDist = length(uv - starLensed);

            float starBright = hash(vec2(fi, 0.9)) * 0.7 + 0.3;
            float starSize = 0.008 + starBright * 0.015;

            float stretchFactor = 1.0 / max(dLensed - shadowRadius, 0.02);
            float tangentialDist = abs(atan(uv.y, uv.x) - atan(starLensed.y, starLensed.x));
            tangentialDist /= (3.14159 * 2.0);
            float radialDist = abs(d - length(starLensed));

            float arc = exp(-radialDist * radialDist / (starSize * starSize * 0.3))
                      * exp(-tangentialDist * tangentialDist / (stretchFactor * 0.04));
            arc *= clamp(stretchFactor * 0.03, 0.1, 1.0);

            stars += arc * starBright;

            float starTemp = hash(vec2(fi, 1.1));
            starColor += mix(
                mix(vec3(0.5, 0.7, 1.0), vec3(1.0, 1.0, 1.0), starTemp),
                mix(vec3(1.0, 0.9, 0.5), vec3(1.0, 0.6, 0.3), starTemp * 0.5),
                step(0.5, starTemp)
            ) * arc * starBright;
        }
    }

    vec3 diskInner  = vec3(0.7, 0.85, 1.0);
    vec3 diskMid    = vec3(1.0, 0.65, 0.1);
    vec3 diskOuter  = vec3(0.8, 0.2, 0.03);

    float t = clamp(diskTemp, 0.0, 1.0);
    vec3 diskColor = mix(diskInner, diskMid, smoothstep(0.0, 0.4, t));
    diskColor = mix(diskColor, diskOuter, smoothstep(0.4, 1.0, t));

    vec3 diskBlue = diskColor * vec3(0.7, 0.8, 1.3);
    vec3 diskRed  = diskColor * vec3(1.3, 0.8, 0.6);
    diskColor = mix(diskBlue, diskRed, doppler);

    vec3 photonRingColor = vec3(14.0, 9.0, 5.0);

    vec3 rgb = vec3(0.0);

    rgb += diskColor * diskBrightness * 1.1;

    rgb += photonRingColor * photonRing * 1.5;
    rgb += photonRingColor * photonRing2 * 0.5;

    rgb += starColor * stars * 0.6;

    float photonGlow = exp(-abs(dLensed - shadowRadius) * 5.0 / rs) * 0.12;
    rgb += vec3(2.0, 3.0, 5.0) * photonGlow;

    float coronaV = exp(-abs(uv.y) * 1.0) * exp(-dLensed * 0.8) * 0.08;
    rgb += vec3(0.3, 0.15, 0.7) * coronaV;

    if (stage > 4.5) {
        float collapseBoost = 1.0 + time * 4.0;
        rgb *= collapseBoost;

        photonRingColor *= collapseBoost;
    }

    float transitionAlpha = alpha;

    if (stage > 2.5 && stage < 3.5) {

        float formAlpha = easeOutExpo(time * 1.2);
        transitionAlpha *= formAlpha;

        float birthFlash = exp(-time * 4.0) * 2.5;
        rgb += vec3(1.0, 0.9, 0.6) * birthFlash * 0.5;
    } else if (stage > 4.5) {

        if (time > 0.85) {
            float fadeOut = 1.0 - (time - 0.85) / 0.15;
            transitionAlpha *= fadeOut;
        }
    }

    float effectAlpha = photonRing * 0.95
                      + photonRing2 * 0.4
                      + diskBrightness * 0.8
                      + stars * 0.5
                      + photonGlow * 0.3;

    effectAlpha = max(effectAlpha, photonRing * 0.9);

    float finalAlpha = effectAlpha * transitionAlpha;

    fragColor = vec4(rgb * brightness, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.0005) {
        discard;
    }
}
