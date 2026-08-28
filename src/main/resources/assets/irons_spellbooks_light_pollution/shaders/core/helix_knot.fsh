#version 150

// One cometary knot in the Helix Nebula: a head being boiled off by the central star and
// a tail streaming radially away from it.
//
// Vertex colour: rgb = the emission colour of the ring this knot belongs to, a = intensity.
// UV0.x runs from the head (0) down the tail (1), always pointing radially outward.
// UV0.y crosses the knot.
//
// The head is on the star-facing side and the tail points away because that is the
// geometry photoevaporation produces — the ionizing flux erodes the near face and the
// liberated gas streams downwind. Every knot in the real object is oriented this way, and
// it is the single feature that makes the nebula read as full of comets rather than
// speckled with dots.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    float along = uvCoord.x;
    float across = uvCoord.y * 2.0 - 1.0;
    float intensity = vertexColor.a;
    if (intensity < 0.002) {
        discard;
    }

    // The head: compact, bright, and slightly flattened on the side facing the star,
    // which is the face being eroded.
    float headAlong = exp(-along * along * 42.0);
    float headAcross = exp(-across * across * 9.0);
    float head = headAlong * headAcross;

    // The tail: widens and fades as it goes, the way an unconfined flow does.
    float spread = 0.35 + along * 1.5;
    float tail = exp(-(across * across) / (spread * spread) * 3.2)
            * exp(-along * 2.3) * 0.55;

    float lit = head + tail;

    // Deliberately over-bright, and deliberately not what the real object emits. These were
    // all clamped under one so the Doppler and line colours would not clip; the verdict was
    // that accuracy had been bought at the cost of impact. The colour ramps are still built
    // from real physics, but the exposure on top of them is chosen to hurt to look at.
    vec3 colour = vertexColor.rgb * lit * intensity * 2.6;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
