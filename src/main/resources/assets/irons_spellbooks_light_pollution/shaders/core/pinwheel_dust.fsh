#version 150

// A dust arm of a Wolf-Rayet pinwheel.
//
// Vertex colour: rgb = dust colour here, a = intensity.
// UV0.x runs from the binary at the centre (0) to the outer end of the arm (1).
// UV0.y crosses the arm.
//
// This is dust, not plasma, and everything below follows from that. Condensed carbon dust in
// a colliding-wind shock sits at a few hundred kelvin, not thousands, so it glows red-brown
// and never approaches white. It is also lumpy rather than smooth, because dust forms in
// clumps where the shock is densest, and it thins as it travels because it is expanding into
// nothing. A hot, smooth, blue-white arm would be an accretion disk, which is exactly the
// object this one must not be mistaken for.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

float noise11(float p) {
    float i = floor(p);
    float f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), f);
}

void main() {
    float along = uvCoord.x;
    float across = uvCoord.y * 2.0 - 1.0;
    float intensity = vertexColor.a;
    if (intensity < 0.002) {
        discard;
    }

    // Soft all the way across. Dust has no sharp edge — there is no surface, only where the
    // column density falls off.
    float body = exp(-across * across * 3.2);

    // Clumping along the arm, at two scales: large knots where the shock was densest and a
    // finer grain over them.
    float coarse = noise11(along * 7.0);
    float fine = noise11(along * 23.0 + 11.0);
    float lumps = 0.55 + 0.6 * coarse * (0.7 + 0.5 * fine);

    // Thins outward as it expands, and the innermost dust is the freshest and warmest.
    float thinning = pow(1.0 - along * 0.75, 1.2);

    float lit = body * lumps * thinning;

    // Deliberately over-bright, and deliberately not what the real object emits. These were
    // all clamped under one so the Doppler and line colours would not clip; the verdict was
    // that accuracy had been bought at the cost of impact. The colour ramps are still built
    // from real physics, but the exposure on top of them is chosen to hurt to look at.
    vec3 colour = vertexColor.rgb * lit * intensity * 2.3;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
