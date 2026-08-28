#version 150

// A quasar jet: one collimated needle with knots racing along it, ending in a hotspot.
//
// Vertex colour: rgb = colour here, a = intensity.
// UV0.x runs from the nucleus (0) to the terminal hotspot (1).
// UV0.y crosses the beam. When ColorModulator.a is negative this instead draws the diffuse
// terminal lobe, where UV0 is a centred unit disc.
//
// Deliberately unlike the microquasar's ribbon in the ways the objects differ: one beam
// rather than two, straight rather than helical, and the knots are discrete bright bodies
// travelling along a faint continuous channel rather than structure frozen into the shape.
// The channel is dim on purpose — it is only dangerous when a knot arrives, and the shader
// should not suggest otherwise.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    float intensity = vertexColor.a;
    if (intensity < 0.002) {
        discard;
    }

    if (ColorModulator.a < 0.0) {
        // Terminal lobe: a diffuse cocoon of shocked material, brightest at the hotspot
        // where the beam actually stops.
        vec2 centred = uvCoord * 2.0 - 1.0;
        float radius = length(centred);
        if (radius > 1.0) {
            discard;
        }
        float hotspot = exp(-radius * radius * 9.0);
        float cocoon = pow(1.0 - radius, 1.7) * 0.30;
        // Zero at the disc's rim, so the lobe has no visible circular cut.
        float rim = max(0.0, 1.0 - radius * radius);
        vec3 colour = vertexColor.rgb * (hotspot + cocoon) * rim * intensity * 2.7;
        fragColor = vec4(colour, 1.0) * vec4(ColorModulator.rgb, 1.0);
        return;
    }

    float along = uvCoord.x;
    float across = uvCoord.y * 2.0 - 1.0;

    // The channel. Narrow and faint: a collimated beam, and one that is not itself the
    // hazard.
    float core = exp(-across * across * 30.0);
    float sheath = exp(-across * across * 5.0) * 0.16;
    // Slight flaring with distance, which real jets do as the confining pressure drops.
    float flare = 1.0 + along * 0.5;
    float channel = (core / flare + sheath) * (0.45 + 0.3 * (1.0 - along));


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
    channel *= pow(max(0.0, 1.0 - across * across), 1.5);

    vec3 colour = vertexColor.rgb * channel * intensity * 1.9;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
