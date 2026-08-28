#version 150

// One jet of SS 433, drawn as a ribbon following the corkscrew the precessing disk
// leaves behind. The helix itself is built on the CPU — this shades a segment of it.
//
// Vertex colour: rgb = the beamed colour for this segment, a = intensity.
// UV0.x runs from the disk outward along the jet, UV0.y crosses the ribbon.
//
// The colour arrives already Doppler-beamed rather than being computed here, because
// beaming depends on the angle between the jet and the viewer, which is a per-segment
// quantity the CPU already has while walking the helix. Doing it per fragment would
// recompute the same value thousands of times and need the camera position uploaded.

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

    // Across the ribbon: a bright core with soft shoulders, so the jet reads as a
    // collimated beam rather than a flat band.
    float core = exp(-across * across * 7.0);
    float halo = exp(-across * across * 1.6) * 0.30;

    // Along the jet: discrete knots. SS 433 does not emit a smooth stream — the ejecta
    // leave the disk as bullets, and the radio images resolve them as separate blobs
    // strung along the helix. Twelve of them over the length, with the spacing jittered
    // so they do not read as a regular dotted line.
    float knotPhase = along * 12.0;
    float knotIndex = floor(knotPhase);
    float withinKnot = fract(knotPhase) - 0.5 + (hash11(knotIndex) - 0.5) * 0.35;
    float knot = exp(-withinKnot * withinKnot * 26.0);

    // The jet thins and cools as it goes, so the far end is dimmer and the knots there
    // carry proportionally more of what light is left.
    float reach = 1.0 - along * 0.62;
    float body = (core + halo) * reach;
    float lit = body * (0.55 + 0.45 * knot) + knot * core * 0.85;

    lit *= 1.55;


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

    vec3 colour = vertexColor.rgb * lit * intensity;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
