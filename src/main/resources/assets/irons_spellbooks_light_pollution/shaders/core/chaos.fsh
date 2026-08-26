#version 400

uniform float time;
uniform vec2 screenSize;
uniform int useType;
uniform float yaw;
uniform float pitch;

in vec4 vertexColor;

out vec4 fragColor;

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

float perlinNoiseOctaves(vec3 p, int octaves, float persistence, float contrast)
{
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

vec3 blend3BW(vec3 dark, vec3 mid, vec3 midAlt, vec3 bright, float n, vec2 uv, float time)
{
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

vec3 galaxy(vec2 fragCoord, float mult, float speed, vec2 res, float time)
{
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

vec4 cloudRaymarch(vec2 fragCoord, vec2 resolution, float t, float yaw, float pitch)
{
    vec4 O = vec4(0.0);
    vec2 I = fragCoord / resolution;

    I = (I - 0.5) * 2.0;
    I.x *= resolution.x / resolution.y;

    vec3 rayDir = applyCamera(I, yaw, pitch);

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

float g(vec4 p, float s) {
    p *= s;
    return abs(dot(sin(p), cos(p.zxwy)) - 1.0) / s;
}

vec4 gyroidCave(vec2 fragCoord, vec2 resolution, float T, float yaw, float pitch)
{
    vec4 O = vec4(0.0);

    vec2 C = fragCoord;
    vec2 r = resolution;

    vec2 uv = (C - 0.5 * r) / r.y;

    vec3 rayDir = applyCamera(uv, yaw, pitch);

    float i = 0.0;
    float d = 0.0;
    float z = 0.0;
    float s = 0.0;

    vec4 o = vec4(0.0);
    vec4 q = vec4(0.0);
    vec4 p = vec4(0.0);
    vec4 U = vec4(2.0, 1.0, 0.0, 3.0);

    for (i = 0.0; i < 79.0; i++)
    {
        z += d + 5e-4;

        q = vec4(rayDir * z, 0.2);

        q.z += T / 30.0;

        s = q.y + 0.1;

        q.y = abs(s);
        p = q;
        p.y -= 0.11;

        float angle = 11.0 * p.z;
        float c = cos(angle);
        float s_rot = sin(angle);
        p.xy *= mat2(c, s_rot, -s_rot, c);
        p.y -= 0.2;

        d = abs(g(p, 8.0) - g(p, 24.0)) / 4.0;

        p = 1.0 + cos(0.7 * U + 5.0 * q.z);

        float multiplier = (s > 0.0) ? 1.0 : 0.1;
        float denominator = max((s > 0.0) ? d : (d * d * d), 5e-4);
        o += multiplier * p.w * p / denominator;
    }

    float pulse = 1.4 + sin(T) * sin(1.7 * T) * sin(2.3 * T);
    o += pulse * 1e3 * U / length(q.xy);

    O = tanh(o / 1e5);

    return O;
}

void main()
{
    vec2 fragCoord = gl_FragCoord.xy;
    vec3 finalColor;

    if(useType == 1)
    {
        vec4 cloudColor = cloudRaymarch(fragCoord, screenSize, time, yaw, pitch);
        finalColor = cloudColor.rgb;
    }
    else if(useType == 2)
    {
        vec4 caveColor = gyroidCave(fragCoord, screenSize, time, yaw, pitch);
        finalColor = caveColor.rgb;
    }
    else
    {
        finalColor = galaxy(fragCoord, 0.03, 0.5, screenSize, time) / 4.0
        + galaxy(fragCoord, 0.01, 3.0, screenSize, time);
    }

    fragColor = vec4(finalColor, 1.0) * vertexColor;
}
