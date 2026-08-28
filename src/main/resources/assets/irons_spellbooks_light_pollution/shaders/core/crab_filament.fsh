#version 150

// The Crab Nebula: filaments of the cage, and the wind nebula inside it.
//
// Vertex colour: rgb = colour here, a = intensity.
// UV0.x runs along a filament; UV0.y crosses it.
// When ColorModulator.a is negative this instead draws the interior wind nebula, where UV0 is
// a centred unit disc.
//
// The two are drawn by one program because they belong to one object, but they are shaded
// nothing alike on purpose. The filaments are line emission from cooling gas — knotty, sharp,
// red and green. The wind nebula is synchrotron radiation from the pulsar's relativistic
// electrons — smooth, structureless, blue-white. In every image of this remnant those two
// components look like different materials, and flattening them into one palette would lose
// the thing that makes the Crab recognisable.

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
    float intensity = vertexColor.a;
    if (intensity < 0.002) {
        discard;
    }

    if (ColorModulator.a < 0.0) {
        // The wind nebula. Smooth and centrally concentrated, with no structure at all —
        // synchrotron emission is not clumpy the way line emission is.
        vec2 centred = uvCoord * 2.0 - 1.0;
        float radius = length(centred);
        if (radius > 1.0) {
            discard;
        }
        float glow = pow(1.0 - radius, 2.2) * 0.5 + exp(-radius * radius * 5.0) * 0.5;
        // Zero at the rim, so the wind nebula has no circular cut around it.
        glow *= max(0.0, 1.0 - radius * radius);
        vec3 colour = vertexColor.rgb * glow * intensity * 1.6;
        fragColor = vec4(colour, 1.0) * vec4(ColorModulator.rgb, 1.0);
        return;
    }

    float along = uvCoord.x;
    float across = uvCoord.y * 2.0 - 1.0;

    // A filament is a thin sharp thread. Much harder-edged than the pinwheel's dust, because
    // this is line emission from a compressed sheet and it really does have an edge.
    float core = exp(-across * across * 26.0);
    float halo = exp(-across * across * 6.0) * 0.18;

    // Knotted along its length, and fading at both ends so the cage has openings rather than
    // filaments that stop dead.
    float knots = 0.5 + 0.7 * noise11(along * 14.0);
    float ends = pow(sin(along * 3.14159265), 0.4);

    float lit = (core + halo) * knots * ends;

    // Deliberately over-bright, and deliberately not what the real object emits. These were
    // all clamped under one so the Doppler and line colours would not clip; the verdict was
    // that accuracy had been bought at the cost of impact. The colour ramps are still built
    // from real physics, but the exposure on top of them is chosen to hurt to look at.
    // Forced to exactly zero at the quad's own edge. Every one of these shaders was leaving a
    // residue there -- the helix knots 0.055 at their far end, the jet ribbon 0.06 at its
    // sides -- and raising the exposure to make the effects striking multiplied that residue
    // into a plainly visible rectangle or a hard line along every strand. The window costs
    // almost nothing at the centre and removes the seam by construction rather than by hoping
    // the falloff got small enough.
    lit *= pow(max(0.0, 1.0 - across * across), 1.5);

    vec3 colour = vertexColor.rgb * lit * intensity * 2.5;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
