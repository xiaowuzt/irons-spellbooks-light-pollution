#version 400

#define M_PI 3.1415926535897932384626433832795

const int cosmiccount = 10;
const int cosmicoutof = 81;
const float lightmix = 1.5f;

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time;

uniform float yaw;
uniform float pitch;
uniform float externalScale;

uniform float opacity;

uniform mat2 cosmicuvs[cosmiccount];

uniform int useCosmicType;
uniform vec4 cosmicColor0;

uniform vec2 screenSize;

in float vertexDistance;
in vec2 texCoord0;
in vec3 fPos;

out vec4 fragColor;

vec3 applyCamera(vec2 uv, float yaw, float pitch)
{
    vec3 rayDir = normalize(vec3(uv, 1.0));

    float sinPitch = sin(pitch);
    float cosPitch = cos(pitch);
    rayDir = vec3(
    rayDir.x,
    rayDir.y * cosPitch - rayDir.z * sinPitch,
    rayDir.y * sinPitch + rayDir.z * cosPitch
    );

    float sinYaw = sin(-yaw);
    float cosYaw = cos(-yaw);
    rayDir = vec3(
    rayDir.z * sinYaw + rayDir.x * cosYaw,
    rayDir.y,
    rayDir.z * cosYaw - rayDir.x * sinYaw
    );

    return normalize(rayDir);
}

mat4 rotationMatrix(vec3 axis, float angle)
{
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c, oc * axis.x * axis.y - axis.z * s, oc * axis.z * axis.x + axis.y * s, 0.0,
    oc * axis.x * axis.y + axis.z * s, oc * axis.y * axis.y + c, oc * axis.y * axis.z - axis.x * s, 0.0,
    oc * axis.z * axis.x - axis.y * s, oc * axis.y * axis.z + axis.x * s, oc * axis.z * axis.z + c, 0.0,
    0.0, 0.0, 0.0, 1.0);
}

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 hash(float n) {
    float x = fract(sin(n * 12.9898) * 43758.5453);
    float y = fract(sin(n * 78.233) * 43758.5453);
    return vec2(x, y);
}

vec3 hash3(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p * (p.x + p.y + p.z)) * 2.0 - 1.0;
}

float perlinNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);

    float n000 = dot(hash3(i + vec3(0.0, 0.0, 0.0)), f - vec3(0.0, 0.0, 0.0));
    float n100 = dot(hash3(i + vec3(1.0, 0.0, 0.0)), f - vec3(1.0, 0.0, 0.0));
    float n010 = dot(hash3(i + vec3(0.0, 1.0, 0.0)), f - vec3(0.0, 1.0, 0.0));
    float n110 = dot(hash3(i + vec3(1.0, 1.0, 0.0)), f - vec3(1.0, 1.0, 0.0));
    float n001 = dot(hash3(i + vec3(0.0, 0.0, 1.0)), f - vec3(0.0, 0.0, 1.0));
    float n101 = dot(hash3(i + vec3(1.0, 0.0, 1.0)), f - vec3(1.0, 0.0, 1.0));
    float n011 = dot(hash3(i + vec3(0.0, 1.0, 1.0)), f - vec3(0.0, 1.0, 1.0));
    float n111 = dot(hash3(i + vec3(1.0, 1.0, 1.0)), f - vec3(1.0, 1.0, 1.0));

    return mix(
        mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
        mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y),
        u.z
    );
}

float perlinNoiseOctaves(vec3 p, int octaves, float persistence, float contrast) {
    float total = 0.0;
    float amplitude = 1.0;
    float frequency = 1.0;
    float maxValue = 0.0;

    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        total += perlinNoise(p * frequency) * amplitude;
        maxValue += amplitude;
        amplitude *= persistence;
        frequency *= 2.0;
    }

    float n = total / maxValue * 0.5 + 0.5;
    n = (n - 0.5) * contrast + 0.5;
    return clamp(n, 0.0, 1.0);
}

vec4 cloudRaymarch(vec2 fragCoord, vec2 resolution, float t, float yaw, float pitch)
{
    vec4 O = vec4(0.0);
    vec2 I = fragCoord / resolution;

    I = (I - 0.5) * 2.0;
    I.x *= resolution.x / resolution.y;

    vec3 rayDir = applyCamera(I, yaw * -1, pitch * -1);

    float z = 0.0;
    float d = 0.0;
    float s = 0.0;

    for(int iter = 0; iter < 100; iter++)
    {
        vec3 p = z * rayDir;

        d = 5.0;
        for(int turbIter = 0; turbIter < 20; turbIter++)
        {
            if(d >= 200.0) break;

            p += 0.6 * sin(vec3(p.y, p.z, p.x) * d - 0.2 * t) / d;
            d += d;
        }

        s = 0.3 - abs(p.y);
        d = 0.005 + max(s, -s * 0.2) / 4.0;
        z += d;

        vec4 colorShift = cos(vec4(s / 0.07 + p.x + 0.5 * t - vec4(3.0, 4.0, 5.0, 0.0))) + 1.5;
        O += colorShift * exp(s / 0.1) / d;
    }

    O = tanh(O * O / 4e8);

    return O;
}

vec3 blend3BW(vec3 dark, vec3 mid, vec3 midAlt, vec3 bright, float n, vec2 uv, float time) {
    vec3 blobPos = vec3(uv * 0.4, time * 0.02);
    float blobNoise = perlinNoise(blobPos) * 0.5 + 0.5;
    blobNoise = smoothstep(0.35, 0.65, blobNoise);

    vec3 midMix = mix(mid, midAlt, blobNoise);
    float t1 = smoothstep(0.2, 0.5, n);
    float t2 = smoothstep(0.2, 1.0, n);

    vec3 col1 = mix(dark, midMix, t1);
    vec3 col2 = mix(midMix, bright, t2);
    vec3 tint = mix(col1, col2, n);

    return mix(vec3(n), tint, 1.0);
}

vec3 galaxy(vec2 fragCoord, float mult, float speed, vec2 res, float time) {
    vec2 uv = fragCoord * mult;
    float tSpeed = speed < 3.0 ? speed * 3.0 : speed;
    vec3 p = vec3(uv + time / speed, time / tSpeed);

    float n = 1.0 - abs(perlinNoiseOctaves(p * 0.15, 8, 0.6, 4.0));
    n = pow(n, 2.0);

    return blend3BW(
        vec3(0.0, 0.0, 0.1),
        vec3(0.4, 0.0, 0.6),
        vec3(0.2, 0.0, 0.6),
        vec3(1.6, 1.0, 1.6),
        n, uv, time
    );
}

void main(void)
{
    vec4 mask = texture(Sampler0, texCoord0.xy);
    float oneOverExternalScale = 1.0 / externalScale;
    int uvtiles = 16;

    vec4 col = vec4(0.1, 0.0, 0.3, 1.0);
    float pulse = mod(time, 400) / 400.0;
    col.g = sin(pulse * M_PI * 2) * 0.075 + 0.225;
    col.b = cos(pulse * M_PI * 2) * 0.05 + 0.3;

    if (useCosmicType == 0) {
        if (abs(cosmicColor0.w - 1.33) < 0.01) {
        } else if (abs(cosmicColor0.w - 2.33) < 0.01) {
            col = vec4(0.95, 0.8, 0.9, 1.0);
        } else {
            col.rgb = cosmicColor0.rgb;
        }
    }
    else if (useCosmicType == 2) {
        col = vec4(0.1, 0.1, 0.1, 1.0);
    }
    else if (useCosmicType == 3) {
        float depth = length(fPos);
        vec3 baseColor = vec3(0.05, 0.02, 0.1);
        vec3 glowColor = vec3(0.4, 0.1, 0.6);
        //col.rgb = mix(baseColor, glowColor, smoothstep(0.0, 0.5, sin(depth * 0.5 + time * 0.1)));
        col.rgb = vec3(0.0, 0.0, 0.01);
    }
    else if (useCosmicType == 4) {
        vec3 nebula = vec3(0.08, 0.01, 0.15);
        float n1 = sin(fPos.x * 0.3 + fPos.y * 0.4 + time * 0.05) * 0.5 + 0.5;
        float n2 = cos(fPos.y * 0.2 + fPos.z * 0.3 + time * 0.03) * 0.5 + 0.5;
        nebula += vec3(0.15 * n1, 0.05 * n2, 0.2 * (n1 + n2));
        col.rgb = nebula;
    }
    else if (useCosmicType == 5) {
        col = vec4(0.0, 0.0, 0.05, 1.0);
        float stars = pow(rand(gl_FragCoord.xy), 50.0) * 0.1;
        col.rgb += vec3(stars);
    }
    else if (useCosmicType == 6) {
        col = vec4(0.0, 0.02, 0.03, 1.0);
    }
    else if (useCosmicType == 7) {
        float t = time * 0.05;
        vec3 color1 = vec3(0.2, 0.0, 0.3);
        vec3 color2 = vec3(0.0, 0.0, 0.4);
        vec3 color3 = vec3(0.3, 0.0, 0.2);
        float f1 = sin(t) * 0.5 + 0.5;
        float f2 = sin(t * 1.3 + 1.0) * 0.5 + 0.5;
        float f3 = sin(t * 0.7 + 2.0) * 0.5 + 0.5;
        col.rgb = (color1 * f1 + color2 * f2 + color3 * f3) / (f1 + f2 + f3);
    }
    else if (useCosmicType == 8) {
        float t = time * 0.03;
        vec3 color1 = vec3(0.7, 0.4, 0.9);
        vec3 color2 = vec3(0.3, 0.7, 0.9);
        vec3 color3 = vec3(0.9, 0.5, 0.8);
        float blend1 = sin(t) * 0.5 + 0.5;
        float blend2 = cos(t * 1.3) * 0.5 + 0.5;
        col.rgb = mix(mix(color1, color2, blend1), color3, blend2);
    }
    else if (useCosmicType == 9) {
        vec2 uv = fPos.xy / (length(fPos) + 0.1);
        float angle = atan(uv.y, uv.x) + time * 0.05;
        float hue = angle / (2.0 * M_PI);
        hue = fract(hue);
        vec3 rainbow = 0.5 + 0.5 * cos(2.0 * M_PI * (hue + vec3(0, 2.0 / 3.0, 1.0 / 3.0)));
        col.rgb = rainbow * 0.3;
    }
    else if (useCosmicType == 10) {
        col = vec4(0.02, 0.03, 0.08, 1.0);
        float aurora = sin(fPos.x * 0.2 + fPos.y * 0.3 + time * 0.03) * 0.5 + 0.5;
        aurora = smoothstep(0.3, 0.7, aurora);
        vec3 auroraColor = mix(vec3(0.1, 0.5, 0.3), vec3(0.3, 0.1, 0.6), sin(time * 0.1) * 0.5 + 0.5);
        col.rgb += auroraColor * aurora * 0.4;
    }
    else if (useCosmicType == 11) {
        col = vec4(0.02, 0.03, 0.1, 1.0);

        vec2 uv = fPos.xy / (length(fPos) + 0.1);
        float angle = atan(uv.y, uv.x);
        float radius = length(uv) * 2.0;

        float rotation = time * 0.05;
        angle += rotation + radius * 3.0;

        float spiral = sin(angle * 8.0 + radius * 20.0) * 0.5 + 0.5;
        spiral = smoothstep(0.4, 0.6, spiral);

        vec3 innerColor = vec3(0.8, 0.5, 1.0);
        vec3 outerColor = vec3(0.2, 0.4, 1.0);
        vec3 spiralColor = mix(innerColor, outerColor, radius);

        float glow = exp(-radius * 5.0) * 0.7;

        col.rgb += spiralColor * spiral * 0.6;
        col.rgb += vec3(0.8, 0.9, 1.0) * glow;
    }
    else if (useCosmicType == 12) {
        col = vec4(0.0, 0.0, 0.02, 1.0);

        vec2 uv = fPos.xy / (length(fPos) + 0.5);

        float aurora1 = sin(uv.x * 3.0 + uv.y * 2.0 + time * 0.1);
        aurora1 = smoothstep(0.3, 0.8, aurora1) * 0.4;
        vec3 color1 = vec3(0.3, 0.8, 0.5);

        float aurora2 = cos(uv.x * 2.5 - uv.y * 1.7 + time * 0.15);
        aurora2 = smoothstep(0.2, 0.7, aurora2) * 0.3;
        vec3 color2 = vec3(0.7, 0.3, 0.9);

        float aurora3 = sin(uv.x * 4.0 - uv.y * 3.0 + time * 0.2);
        aurora3 = smoothstep(0.4, 0.9, aurora3) * 0.25;
        vec3 color3 = vec3(0.2, 0.5, 0.9);

        col.rgb += color1 * aurora1;
        col.rgb += color2 * aurora2;
        col.rgb += color3 * aurora3;

        float stars = pow(rand(gl_FragCoord.xy), 100.0) * 0.2;
        col.rgb += vec3(1.0, 1.0, 1.0) * stars;
    }
    else if (useCosmicType == 13) {
        float t = time * 0.02;
        vec3 color1 = vec3(0.12, 0.02, 0.18);
        vec3 color2 = vec3(0.18, 0.01, 0.12);
        vec3 color3 = vec3(0.10, 0.00, 0.15);

        float blend1 = sin(t) * 0.5 + 0.5;
        float blend2 = cos(t * 1.7) * 0.5 + 0.5;

        vec3 bg1 = mix(color1, color2, blend1);
        vec3 bg2 = mix(bg1, color3, blend2);

        float nebula1 = sin(fPos.x * 0.2 + fPos.y * 0.3 + t * 2.0) * 0.5 + 0.5;
        float nebula2 = cos(fPos.y * 0.25 + fPos.z * 0.15 + t * 1.5) * 0.5 + 0.5;
        vec3 nebulaColor = mix(vec3(0.25, 0.05, 0.35), vec3(0.35, 0.03, 0.25), nebula1);
        float nebulaIntensity = nebula1 * nebula2 * 0.15;

        col.rgb = bg2 + nebulaColor * nebulaIntensity;

        float starsBg = pow(rand(gl_FragCoord.xy * 0.7), 80.0) * 0.08;
        col.rgb += vec3(0.7, 0.3, 0.8) * starsBg;
    }
    else if (useCosmicType == 14) {
        col = vec4(0.0, 0.0, 0.05, 1.0);

        vec4 rotatedPos = normalize(vec4(-fPos, 0.0));
        float sb = sin(pitch);
        float cb = cos(pitch);
        rotatedPos = normalize(vec4(rotatedPos.x, rotatedPos.y * cb - rotatedPos.z * sb, rotatedPos.y * sb + rotatedPos.z * cb, 0.0));
        float sa = sin(-yaw);
        float ca = cos(-yaw);
        rotatedPos = normalize(vec4(rotatedPos.z * sa + rotatedPos.x * ca, rotatedPos.y, rotatedPos.z * ca - rotatedPos.x * sa, 0.0));

        vec2 screenCoord = gl_FragCoord.xy;

        vec3 galaxyEffect = galaxy(screenCoord, 0.03, 0.5, screenSize, time) / 4.0 + galaxy(screenCoord, 0.01, 3.0, screenSize, time);

        float rotationInfluence = sin(time * 0.1 + rotatedPos.x * 2.0) * 0.5 + 0.5;
        galaxyEffect *= (0.8 + rotationInfluence * 0.4);

        col.rgb += galaxyEffect;

        vec2 uv = fPos.xy / (length(fPos) + 0.5);
        float nebula = sin(uv.x * 2.0 + uv.y * 3.0 + time * 0.05) * 0.5 + 0.5;
        nebula = smoothstep(0.3, 0.7, nebula) * 0.2;
        vec3 nebulaColor = mix(vec3(0.1, 0.0, 0.3), vec3(0.3, 0.1, 0.5), sin(time * 0.1) * 0.5 + 0.5);
        col.rgb += nebulaColor * nebula;
    }
    else if (useCosmicType == 15) {
        vec4 cloudResult = cloudRaymarch(gl_FragCoord.xy, screenSize, time * 0.5, yaw, pitch);
        col = cloudResult;
    }
    else if (useCosmicType == 16) {
        vec3 bgColor1 = vec3(0.073, 0.073, 0.073);
        vec3 bgColor2 = vec3(0.149, 0.047, 0.152);
        float bgBlend = sin(time * 0.05) * 0.5 + 0.5;
        col = vec4(mix(bgColor1, bgColor2, bgBlend), 1.0);
    }

    if (useCosmicType == 1 && abs(cosmicColor0.w - 1.33) < 0.01) {
        float hue = (time * 0.05 + fPos.x * 0.1 + fPos.y * 0.1) * 0.5;
        hue = fract(hue);
        vec3 rainbow = 0.5 + 0.5 * cos(2.0 * M_PI * (hue + vec3(0, 2.0 / 3.0, 1.0 / 3.0)));
        col.rgb = mix(vec3(1.0), rainbow, 0.6);
    } else if (useCosmicType == 1) {
        col.rgb = cosmicColor0.rgb;
    }

    if (useCosmicType != 14) {
        vec4 dir = normalize(vec4(-fPos, 0.0));
        float sb = sin(pitch);
        float cb = cos(pitch);
        dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0.0));
        float sa = sin(-yaw);
        float ca = cos(-yaw);
        dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0.0));
        vec4 ray;

        for (int i = 0; i < 16; i++) {
            int mult = 16 - i;
            int j = i + 7;
            float rand1 = (j * j * 4321.0 + j * 8.0) * 2.0;
            int k = j + 1;
            float rand2 = (k * k * k * 239.0 + k * 37.0) * 3.6;
            float rand3 = rand1 * 347.4 + rand2 * 63.4;
            vec3 axis = normalize(vec3(sin(rand1), sin(rand2), cos(rand3)));
            ray = dir * rotationMatrix(axis, mod(rand3, 2.0 * M_PI));
            float rawu = 0.5 + (atan(ray.z, ray.x) / (2.0 * M_PI));
            float rawv = 0.5 + (asin(ray.y) / M_PI);
            float scale = mult * 0.5 + 2.75;
            float u = rawu * scale * externalScale;
            float v = (rawv + time * 0.0002 * oneOverExternalScale) * scale * 0.6 * externalScale;

            float pulseFactor = 1.0;
            if (useCosmicType == 5 && fract(rand1) < 0.1) {
                float pulsePhase = mod(time * 0.8 + rand2 * 10.0, 6.0);
                if (pulsePhase < 1.0) {
                    pulseFactor = 1.0;
                } else if (pulsePhase < 2.0) {
                    float intensity = (pulsePhase - 1.0);
                    pulseFactor = 1.0 + intensity * 5.0;
                } else if (pulsePhase < 3.0) {
                    pulseFactor = 6.0;
                } else {
                    float intensity = 1.0 - (pulsePhase - 3.0) / 3.0;
                    pulseFactor = 1.0 + intensity * 5.0;
                }
            }

            if (useCosmicType == 11) {
                continue;
            }

            if (useCosmicType == 12) {
                continue;
            }



            if (useCosmicType == 8) {
                continue;
            }

            float sizeFactor = 1.0;
            if (useCosmicType == 9) {
                sizeFactor = 1.5 + rand(vec2(rand1, rand2)) * 1.5;
                u *= sizeFactor;
                v *= sizeFactor;
            }

            u *= pulseFactor;
            v *= pulseFactor;

            vec2 tex = vec2(u, v);
            int tu = int(mod(floor(u * uvtiles), uvtiles));
            int tv = int(mod(floor(v * uvtiles), uvtiles));
            int position = ((171 * tu) + (489 * tv) + (303 * (i + 31)) + 17209) ^ 10086;
            int symbol = int(mod(float(position), float(cosmicoutof)));
            int rotation = int(mod(pow(float(tu), float(tv)) + float(tu) + 3.0 + float(tv * i), 8.0));
            bool flip = false;
            if (rotation >= 4) {
                rotation -= 4;
                flip = true;
            }

            if (symbol >= 0 && symbol < cosmiccount) {
                vec2 cosmictex;
                vec4 tcol;
                float ru = clamp(mod(u, 1.0) * uvtiles - tu, 0.0, 1.0);
                float rv = clamp(mod(v, 1.0) * uvtiles - tv, 0.0, 1.0);

                if (useCosmicType == 7) {
                    float distort = sin(time * 0.5 + ru * 5.0) * 0.05;
                    ru += distort;
                    rv += cos(time * 0.4 + rv * 4.0) * 0.05;
                }

                if (flip) {
                    ru = 1.0 - ru;
                }

                float oru = ru;
                float orv = rv;

                if (rotation == 1) {
                    oru = 1.0 - rv;
                    orv = ru;
                } else if (rotation == 2) {
                    oru = 1.0 - ru;
                    orv = 1.0 - rv;
                } else if (rotation == 3) {
                    oru = rv;
                    orv = 1.0 - ru;
                }

                float umin = cosmicuvs[symbol][0][0];
                float umax = cosmicuvs[symbol][1][0];
                float vmin = cosmicuvs[symbol][0][1];
                float vmax = cosmicuvs[symbol][1][1];
                cosmictex.x = umin * (1.0 - oru) + umax * oru;
                cosmictex.y = vmin * (1.0 - orv) + vmax * orv;
                tcol = texture(Sampler0, cosmictex);
                float a = tcol.r * (0.5 + (1.0 / mult) * 1.0) * (1.0 - smoothstep(0.15, 0.48, abs(rawv - 0.5)));

                vec3 starColor = vec3(0.0);

                if (useCosmicType == 0 && abs(cosmicColor0.w - 2.33) < 0.01) {
                    float choice = mod(rand1, 3.0);
                    if (choice < 1.0) {
                        starColor = vec3(1.0, 0.7, 0.8);
                    } else if (choice < 2.0) {
                        starColor = vec3(0.9, 0.7, 1.0);
                    } else {
                        starColor = vec3(1.0, 0.8, 0.9);
                    }
                    starColor = mix(starColor, vec3(1.0), 0.3);
                }
                else if (useCosmicType == 1 || useCosmicType == 15) {
                    float hue = (time * 0.2 + rawu * 3.0 + float(i) * 0.3) * 0.3;
                    hue = fract(hue);
                    vec3 rainbow = 0.5 + 0.5 * cos(2.0 * M_PI * (hue + vec3(0, 2.0 / 3.0, 1.0 / 3.0)));
                    starColor = mix(vec3(1.0), rainbow, 0.6);
                }
                else if (useCosmicType == 2) {
                    if (fract(rand2 * 0.4) < 0.2) {
                        starColor = vec3(fract(rand1 * 0.7), fract(rand2 * 0.7), fract(rand3 * 0.7));
                    } else {
                        float gray = dot(tcol.rgb, vec3(0.299, 0.587, 0.114));
                        starColor = vec3(gray);
                    }
                }
                else if (useCosmicType == 3) {
                    float hue = (time * 0.3 + rawu * 5.0 + float(i) * 0.5) * 0.15;
                    hue = fract(hue);
                    vec3 crystal = 0.7 + 0.3 * cos(2.0 * M_PI * (hue + vec3(0, 1.0 / 3.0, 2.0 / 3.0)));

                    float blink = sin(time * 3.0 + rand1 * 10.0) * 0.3 + 0.7;
                    starColor = crystal * blink;

                    float glow = smoothstep(0.8, 1.0, tcol.r) * 0.5;
                    starColor += vec3(0.8, 0.9, 1.0) * glow;
                }
                else if (useCosmicType == 4) {
                    vec3 baseColor = vec3(0.7, 0.8, 1.0);
                    vec3 nebulaColor = vec3(0.9, 0.4, 0.9);

                    float colorShift = sin(time * 0.5 + rawu * 10.0) * 0.5 + 0.5;
                    starColor = mix(baseColor, nebulaColor, colorShift);

                    float trail = smoothstep(0.3, 1.0, tcol.r);
                    starColor *= trail * 1.5;
                }
                else if (useCosmicType == 5) {
                    if (fract(rand1) < 0.1) {
                        float pulsePhase = mod(time * 0.8 + rand2 * 10.0, 6.0);
                        if (pulsePhase < 2.0) {
                            float intensity = clamp((pulsePhase - 1.0), 0.0, 1.0);
                            starColor = mix(vec3(0.4, 0.6, 1.0), vec3(1.0), intensity);
                        } else if (pulsePhase < 3.0) {
                            starColor = vec3(1.0);
                        } else {
                            float intensity = clamp((pulsePhase - 3.0) / 3.0, 0.0, 1.0);
                            starColor = mix(vec3(1.0), vec3(0.4, 0.6, 1.0), intensity);
                        }
                    } else {
                        starColor = vec3(0.4, 0.6, 1.0);
                    }
                }
                else if (useCosmicType == 6) {
                    float hue = (time * 0.2 + rawu * 3.0 + float(i) * 0.3) * 0.3;
                    hue = fract(hue);
                    vec3 rainbow = 0.5 + 0.5 * cos(2.0 * M_PI * (hue + vec3(0, 2.0 / 3.0, 1.0 / 3.0)));
                    starColor = mix(vec3(1.0), rainbow, 0.6);
                }
                else if (useCosmicType == 7) {
                    float choice = mod(rand1, 6.0);
                    if (choice < 1.0) starColor = vec3(1.0, 0.5, 0.8);
                    else if (choice < 2.0) starColor = vec3(0.5, 1.0, 0.8);
                    else if (choice < 3.0) starColor = vec3(1.0, 1.0, 0.5);
                    else if (choice < 4.0) starColor = vec3(0.8, 0.5, 1.0);
                    else if (choice < 5.0) starColor = vec3(1.0, 0.8, 0.5);
                    else starColor = vec3(0.5, 0.8, 1.0);

                    float glow = smoothstep(0.7, 1.0, tcol.r) * 0.7;
                    starColor += vec3(1.0, 1.0, 1.0) * glow;
                }
                else if (useCosmicType == 13) {
                    vec3 color1 = vec3(0.95, 0.25, 0.85);
                    vec3 color2 = vec3(0.85, 0.35, 0.95);

                    float choice = fract(rand1 * 0.3);
                    if (choice < 0.6) {
                        starColor = color1;
                    } else {
                        starColor = color2;
                    }

                    starColor = min(starColor * 1.15, 1.0);

                    float hueShift = sin(time * 0.5 + float(i) * 0.2) * 0.1;
                    if (choice < 0.3) {
                        starColor.r += hueShift * 0.2;
                    } else if (choice < 0.6) {
                        starColor.b += hueShift * 0.2;
                    } else {
                        starColor.g += hueShift * 0.1;
                    }

                    float blink = sin(time * 2.0 + rand1 * 10.0) * 0.1 + 0.9;
                    starColor *= blink;

                    float glow = smoothstep(0.7, 1.0, tcol.r) * 0.4;
                    starColor += vec3(1.0, 0.8, 1.0) * glow;
                }
                else if (useCosmicType == 16) {
                    vec3 pink = vec3(1.0, 0.0, 0.5412);
                    vec3 blue = vec3(0.3608, 0.7255, 0.9882);
                    float choice = fract(rand1 * 0.37 + rand2 * 0.53);

                    if (choice < 0.5) starColor = pink;
                    else starColor = blue;

                    float blink = sin(time * 1.5 + rand1 * 8.0) * 0.15 + 0.85;
                    starColor *= blink;

                    float glow = smoothstep(0.75, 1.0, tcol.r) * 0.4;
                    starColor += vec3(1.0, 0.85, 0.95) * glow;
                }
                else {
                    starColor = vec3(
                    fract(rand1 * 0.3) * 0.3 + 0.4,
                    fract(rand2 * 0.4) * 0.4 + 0.6,
                    fract(rand1 * 0.3) * 0.3 + 0.7
                    );
                }

                col += vec4(starColor, 1.0) * a;
            }
        }
    }

    if (useCosmicType == 2) {
        float noise = rand(gl_FragCoord.xy + time);
        col.rgb += (noise - 0.5) * 0.1;
    }

    if (useCosmicType == 3) {
        vec2 refOffset = vec2(
        sin(fPos.x * 0.5 + time * 0.2) * 0.01,
        cos(fPos.y * 0.5 + time * 0.15) * 0.01
        );

        col.rgb = texture(Sampler0, texCoord0 + refOffset).rgb * 0.1 + col.rgb * 0.9;

        float glow = sin(time * 0.5) * 0.1 + 0.15;
        col.rgb += vec3(0.7, 0.8, 1.0) * glow * (1.0 - mask.r);
    }

    if (useCosmicType == 4) {
        vec2 coord = gl_FragCoord.xy * 0.5;
        float dust = rand(coord + time * 0.1);
        dust = smoothstep(0.95, 1.0, dust) * 0.3;

        col.rgb += vec3(0.8, 0.9, 1.0) * dust;

        float flow = sin(fPos.x * 0.1 + fPos.y * 0.15 + time * 0.05) * 0.1;
        col.rgb += vec3(0.4, 0.1, 0.5) * flow * (1.0 - mask.r);
    }

    if (useCosmicType == 7) {
        vec2 uv = fPos.xy / length(fPos);
        float band = sin(uv.x * 10.0 + time * 0.5) * 0.5 + 0.5;
        band = smoothstep(0.4, 0.6, band) * 0.3;

        vec3 bandColor = mix(vec3(1.0, 0.5, 0.8), vec3(0.5, 1.0, 0.8), sin(time * 0.3) * 0.5 + 0.5);
        col.rgb += bandColor * band;

        float sparkle = pow(rand(gl_FragCoord.xy + time), 100.0) * 2.0;
        col.rgb += vec3(1.0, 1.0, 0.8) * sparkle;
    }

    if (useCosmicType == 8) {
        vec2 uv = fPos.xy / (length(fPos) + 0.5);

        float bubble1 = sin(uv.x * 4.0 + time * 0.2) *
        cos(uv.y * 3.0 + time * 0.15);
        bubble1 = smoothstep(0.7, 0.9, bubble1);

        float bubble2 = cos(uv.x * 5.0 - time * 0.18) *
        sin(uv.y * 4.0 + time * 0.12);
        bubble2 = smoothstep(0.6, 0.8, bubble2);

        float bubble3 = sin(uv.x * 6.0 + time * 0.25) *
        cos(uv.y * 5.0 - time * 0.22);
        bubble3 = smoothstep(0.8, 0.95, bubble3);

        vec3 bubbleColor1 = vec3(1.0, 0.8, 0.9);
        vec3 bubbleColor2 = vec3(0.8, 0.9, 1.0);
        vec3 bubbleColor3 = vec3(0.9, 1.0, 0.8);

        col.rgb += bubbleColor1 * bubble1 * 0.5;
        col.rgb += bubbleColor2 * bubble2 * 0.4;
        col.rgb += bubbleColor3 * bubble3 * 0.6;

        float glow = sin(time * 2.0) * 0.1 + 0.2;
        col.rgb += vec3(1.0, 1.0, 1.0) * glow * (bubble1 + bubble2 + bubble3) * 0.3;
    }

    if (useCosmicType == 10) {
        vec2 uv = fPos.xy / (length(fPos) + 0.5);
        float aurora = sin(uv.x * 3.0 + uv.y * 5.0 + time * 0.1) * 0.5 + 0.5;
        aurora = smoothstep(0.3, 0.7, aurora);
        vec3 auroraColor = mix(vec3(0.2, 0.8, 0.5), vec3(0.6, 0.3, 0.9), sin(time * 0.2) * 0.5 + 0.5);
        col.rgb += auroraColor * aurora * 0.3;

        float trails = sin(fPos.z * 10.0 + time * 0.2) * 0.5 + 0.5;
        col.rgb += vec3(0.8, 0.9, 1.0) * trails * 0.1;
    }

    if (useCosmicType == 11) {
        vec2 uv = fPos.xy / (length(fPos) + 0.1);
        float radius = length(uv) * 2.0;
        float glow = exp(-radius * 8.0) * 0.8;

        float angle = atan(uv.y, uv.x) + time * 0.1;
        float halo = sin(angle * 12.0) * 0.5 + 0.5;
        halo = smoothstep(0.4, 0.6, halo) * glow * 0.5;

        vec3 haloColor = mix(vec3(0.9, 0.7, 1.0), vec3(0.4, 0.6, 1.0), radius);

        col.rgb += haloColor * halo;
    }

    if (useCosmicType == 12) {
        vec2 uv = fPos.xy / (length(fPos) + 0.5);
        float glow = sin(uv.x * 0.5 + uv.y * 0.7 + time * 0.1) * 0.5 + 0.5;
        glow = smoothstep(0.2, 0.8, glow) * 0.1;
        col.rgb += vec3(0.3, 0.6, 0.8) * glow;

        float band = cos(uv.x * 8.0 + time * 0.15) * 0.5 + 0.5;
        band = smoothstep(0.4, 0.6, band) * 0.2;
        col.rgb += vec3(0.7, 0.3, 0.9) * band;
    }

    if (useCosmicType == 13)
    {
        vec2 coord = gl_FragCoord.xy * 0.3;
        float dust = pow(rand(coord + time * 0.05), 60.0) * 0.15;
        vec3 dustColor = mix(vec3(0.9, 0.4, 0.8), vec3(0.7, 0.3, 0.9), sin(time * 0.1) * 0.5 + 0.5);
        col.rgb += dustColor * dust;

        vec2 uv = fPos.xy / (length(fPos) + 0.5);
        float band = sin(uv.x * 5.0 + time * 0.1) * 0.5 + 0.5;
        band = smoothstep(0.4, 0.7, band) * 0.15;
        vec3 bandColor = mix(vec3(0.8, 0.2, 0.6), vec3(0.6, 0.1, 0.7), abs(sin(time * 0.2)));
        col.rgb += bandColor * band;
    }

    col.rgb *= lightmix;
    col.a *= mask.r * opacity;
    col = clamp(col, 0.0, 1.0);
    fragColor = linear_fog(col * 1.25, vertexDistance, FogStart, FogEnd, FogColor);
}