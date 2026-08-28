#version 150

// A tidal disruption event's debris stream: a star drawn out into a ribbon.
//
// Vertex colour: rgb = colour at this point along the stream, a = intensity.
// UV0.x runs from the leading tip (0) to the trailing end (1).
// UV0.y crosses the stream.
//
// The gradient along the stream is physical rather than decorative. The leading tip has
// been in the tidal field longest and is closest to the hole, so it is the hottest and
// thinnest part; the trailing end is cooler, thicker, still recognisably stellar material.
// That is why the colour runs blue-white at the tip through gold to a dull red at the tail
// and not the other way round.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

void main() {
    float along = uvCoord.x;
    float across = uvCoord.y * 2.0 - 1.0;
    float intensity = vertexColor.a;
    if (intensity < 0.002) {
        discard;
    }

    // Cross-section: a bright filament in a wider sheath of stripped gas.
    float core = exp(-across * across * 13.0);
    float sheath = exp(-across * across * 2.0) * 0.28;

    // Clumping along the stream. Debris streams are not smooth — they fragment as they are
    // stretched, and the clumps are what make it read as torn material rather than a hose.
    float lumpPhase = along * 9.0;
    float lump = exp(-pow(fract(lumpPhase) - 0.5 + (hash11(floor(lumpPhase)) - 0.5) * 0.4,
            2.0) * 20.0);

    // Hotter and brighter toward the leading tip, which is deepest in the tidal field.
    float lead = pow(1.0 - along, 1.4);
    float lit = (core + sheath) * (0.45 + 0.55 * lead) * (0.7 + 0.55 * lump);


    // Deliberately over-bright, and deliberately not what the real object emits. These were
    // all clamped under one so the Doppler and line colours would not clip; the verdict was
    // that accuracy had been bought at the cost of impact. The colour ramps are still built
    // from real physics, but the exposure on top of them is chosen to hurt to look at.
    vec3 colour = vertexColor.rgb * lit * intensity * 2.4;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
