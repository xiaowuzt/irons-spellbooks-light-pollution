#version 150

#define PHI 1.618033988
#define M_PI 3.1415926535897932384626433832795

uniform float time;
uniform vec2 screenSize;

uniform float yaw;
uniform float pitch;

in vec2 fragCoord;
in vec4 vertexColor;
in vec3 worldPos;
in vec3 fPos;

out vec4 fragColor;

vec3 aces(vec3 color) {
    mat3 m1 = mat3(
    0.59719, 0.07600, 0.02840,
    0.35458, 0.90834, 0.13383,
    0.04823, 0.01566, 0.83777
    );
    mat3 m2 = mat3(
    1.60475, -0.10208, -0.00327,
    -0.53108,  1.10813, -0.07276,
    -0.07367, -0.00605,  1.07602
    );
    vec3 v = m1 * color;
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;

    return m2 * (a / b);
}

float dot_noise(vec3 p) {
    const mat3 GOLD = mat3(
    -0.571464913, +0.814921382, +0.096597072,
    -0.278044873, -0.303026659, +0.911518454,
    +0.772087367, +0.494042493, +0.399753815);

    return dot(cos(GOLD * p), sin(PHI * p * GOLD));
}

float dist(vec3 p) {
    float noise = dot_noise(p);

    return (0.02 + abs(noise) * 0.3);
}

void main() {
    vec4 dir = normalize(vec4(-fPos, 0.0));

    float sb = sin(pitch);
    float cb = cos(pitch);
    dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0.0));

    float sa = sin(-yaw);
    float ca = cos(-yaw);
    dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0.0));

    vec3 rayDir = normalize(dir.xyz);

    vec3 p = vec3(0.0, 0.0, time * 10.0);

    p += rayDir * 0.1;

    vec3 l = vec3(0.0);

    for(float i = 0.0; i < 80.0; i++) {
        float s = dist(p);

        p += rayDir * s;

        vec3 colorMod = vec3(6.0, 1.0, 1.0);

        l += cos(p.z * colorMod) * 1e2;
    }

    vec3 finalColor = pow(aces(l * l / 1e7), vec3(1.0 / 2.2));

    fragColor = vec4(finalColor, 1.0);
}
