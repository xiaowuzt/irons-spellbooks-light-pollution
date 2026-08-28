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

// Where along the quad the head sits. The renderer extends the quad behind the knot by the
// matching fraction, so this and HelixNebulaWorldRenderer.HEAD_MARGIN are one number split
// across two files -- change both or the head lands off centre.
#define HS_HEAD_AT 0.26

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

    // The head sits at HS_HEAD_AT, not at along = 0.
    //
    // It used to be centred on zero, which is the quad's own leading edge, so exactly half of
    // the head gaussian fell outside the quad and was clipped away. Every knot was rendered as
    // a hemisphere with a straight cut across it. Moving the centre inward and extending the
    // quad behind it by the matching amount is what makes a whole head.
    float fromHead = along - HS_HEAD_AT;
    float headAcross = exp(-across * across * 9.0);
    float head = exp(-fromHead * fromHead * 42.0) * headAcross;

    // The tail: widens and fades as it goes, the way an unconfined flow does. Measured from
    // the head rather than from the quad edge, so it starts where the head is.
    float downwind = max(0.0, fromHead);
    float spread = 0.35 + downwind * 1.5;
    float tail = exp(-(across * across) / (spread * spread) * 3.2)
            * exp(-downwind * 2.3) * 0.55;

    float lit = head + tail;

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
    // The along window matters more than the across one here: the tail was still at 5.5%
    // where the quad ends, which is the rectangle that showed around every knot.
    // Closes at both ends of the quad. The far end needed it because the tail was still at
    // 5.5% there; the near end needs it now too, because the head no longer sits on that edge
    // and whatever the gaussian leaves behind it would otherwise be cut off square.
    lit *= pow(max(0.0, 1.0 - across * across), 1.5)
            * smoothstep(0.0, HS_HEAD_AT * 0.7, along)
            * max(0.0, 1.0 - along * along);

    vec3 colour = vertexColor.rgb * lit * intensity * 2.6;
    fragColor = vec4(colour, 1.0) * ColorModulator;
}
