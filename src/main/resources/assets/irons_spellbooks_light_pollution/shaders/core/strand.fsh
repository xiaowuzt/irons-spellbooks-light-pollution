#version 150

// Vertex contract: r=material auxiliary, g=mode/255, b=intensity/4, a=opacity.
// All time/seed/origin inputs are per SOURCE, set before every draw (never a global last writer).
uniform vec4 ColorModulator;
uniform float EffectTime;
uniform float EffectSeed;
uniform vec3 EffectOrigin;
in vec4 vertexColor;
in vec2 uvCoord;
in vec3 surfaceNormal;
in vec3 toCamera;
out vec4 fragColor;
const float TAU = 6.28318530718;

float packet(float x, float sharpness) {
    float v = fract(x) - 0.5;
    return exp(-v * v * sharpness);
}

void main() {
    float along = uvCoord.x;
    float around = uvCoord.y;
    float aux = vertexColor.r;
    int mode = int(floor(vertexColor.g * 255.0 + 0.5));
    float intensity = vertexColor.b * 4.0;
    float alpha = vertexColor.a;
    if (intensity < 0.002 || alpha < 0.004) discard;
    vec3 normal = normalize(surfaceNormal);
    float facing = abs(dot(normal, normalize(toCamera)));
    float body = 0.28 + 0.64 * facing;
    float limb = pow(1.0 - facing, 2.2) * 0.64;
    float seed = EffectSeed * 0.017;
    float time = EffectTime;
    float distanceFromCore = length(-toCamera - EffectOrigin);
    vec3 colour;
    float extra;
    if (mode == 0) {
        // Electrical avalanches travel pole-to-pole, with dark gaps between charges.
        colour = mix(vec3(0.40, 0.20, 1.0), vec3(1.0, 0.78, 0.48), aux);
        float poles = pow(abs(cos(along * 3.14159265)), 1.6);
        float charge = packet(along * 3.0 - time * (1.4 + aux) - seed, 65.0);
        float braid = 0.72 + 0.28 * sin(around * TAU * 3.0 + along * 48.0 - time * 7.0);
        extra = (0.24 + 0.48 * poles + charge * 1.10) * braid;
    } else if (mode == 1) {
        // Discrete outward-moving jet bullets, not the inward flow of a debris stream.
        colour = mix(vec3(1.0, 0.30, 0.18), vec3(0.32, 0.64, 1.0), aux);
        float knot = packet(along * 12.0 - time * 2.1 - seed, 48.0);
        float shear = 0.72 + 0.28 * sin(around * TAU * 4.0 - along * 92.0 + time * 8.0);
        extra = mix(0.45, 1.35, aux) * (1.0 - along * 0.55) * (0.35 + knot) * shear;
    } else if (mode == 2) {
        // Plus time: density peaks move toward along=0, the hole, not away from it.
        float flow = along * 11.0 + time * 1.85 + seed;
        float lump = packet(flow, 42.0);
        float threads = 0.60 + 0.40 * pow(0.5 + 0.5 * sin(around * TAU * 5.0 + flow * 2.0), 3.0);
        colour = mix(vec3(0.48, 0.78, 1.0), vec3(1.0, 0.24, 0.055), pow(along, 0.65));
        extra = (0.28 + 0.82 * lump) * threads * (1.0 - along * 0.42);
    } else if (mode == 3) {
        // The thin hot spine of a dust sheet. The volume pass supplies its cool, absorbing edges.
        float dust = packet(along * 8.0 - time * 0.7 - seed, 18.0);
        colour = mix(vec3(1.0, 0.62, 0.23), vec3(0.34, 0.085, 0.025), along);
        extra = (0.28 + dust * 0.58) * (1.0 - along * 0.62);
        body = 0.65; limb *= 0.3;
    } else if (mode == 4) {
        // A pulsar wave reaches outer gas later. World-space distance is periodic at loop joins.
        float localAge = time * 20.0 - distanceFromCore / 1.8;
        float pulse = localAge < 0.0 ? 0.0 : pow(1.0 - fract(localAge / 10.0), 2.0);
        colour = mix(vec3(1.0, 0.18, 0.095), vec3(0.18, 0.88, 0.50), step(0.5, aux));
        float fibres = 0.54 + 0.46 * pow(0.5 + 0.5 * sin(along * TAU * 7.0 + aux * 20.0), 3.0);
        extra = fibres * (0.42 + pulse * 1.15);
    } else if (mode == 5) {
        // Stellar prominences and a cooling wake; never a white, opaque laser trail.
        colour = mix(vec3(1.0, 0.72, 0.20), vec3(0.62, 0.075, 0.018), along);
        extra = (0.42 + packet(along * 4.0 + time * 0.8 + seed, 30.0) * 0.62)
                * (1.0 - along * 0.72);
    } else if (mode == 6) {
        colour = mix(vec3(0.12, 0.65, 1.0), vec3(1.0, 0.63, 0.15), aux);
        extra = 0.72 + packet(along * 6.0 - time * 0.3, 38.0) * 0.5;
    } else {
        colour = mix(vec3(0.06, 0.48, 1.0), vec3(0.76, 0.94, 1.0), aux);
        extra = 0.58 + packet(along * 14.0 + time * 8.0, 25.0) * 0.7;
        limb *= 0.25;
    }
    fragColor = vec4(colour * (body + limb) * extra * intensity, alpha) * ColorModulator;
}
