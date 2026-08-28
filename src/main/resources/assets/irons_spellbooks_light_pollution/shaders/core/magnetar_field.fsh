#version 150

// One dipole field line of a magnetar, drawn as a thin ribbon along the loop.
//
// Vertex colour: rgb = the field's colour at this point, a = intensity.
// UV0.x runs from one pole (0) around the loop to the other (1).
// UV0.y crosses the ribbon.
//
// The travelling brightness along the loop is not decoration. A magnetosphere this
// strong is not static — it is being stressed, and the flare that ends the effect is a
// large-scale rearrangement of it. Charge crowding along the lines and moving is the
// visible tell that the field is loaded, which is what makes the wind-up readable rather
// than a bar that silently fills.

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

    // A hard thin core with a wide faint sheath, which is what makes a line read as a
    // filament of charge rather than a painted stripe.
    float core = exp(-across * across * 22.0);
    float sheath = exp(-across * across * 2.4) * 0.22;

    // Brightest at the poles, where the loops converge and the field is strongest. A
    // dipole's field strength goes as 1/r^3, so the equatorial bulge really is the faint
    // part of the loop.
    float poles = pow(abs(cos(along * 3.14159265)), 1.6);
    float strength = 0.35 + 0.65 * poles;

    // Packets of charge sliding along the line. Phase offset per line so they do not all
    // pulse in lockstep, which would read as a single flashing object.
    float lane = hash11(floor(uvCoord.y * 3.0) + 11.0);
    float packet = exp(-pow(fract(along * 3.0 - lane) - 0.5, 2.0) * 30.0);

    float lit = (core + sheath) * strength * (0.65 + 0.6 * packet);

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

    vec3 colour = vertexColor.rgb * lit * intensity * 2.8;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
