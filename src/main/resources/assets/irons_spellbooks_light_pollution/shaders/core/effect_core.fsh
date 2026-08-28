#version 150

// The central body of an effect, plus its glow. Used by every spell that has something at
// the middle: a nucleus, a white dwarf, a neutron star, a binary, a black hole.
//
// Vertex colour: rgb = body colour, a = intensity.
// UV0 is a centred unit disc.
// ColorModulator.a selects the layer:
//   >= 1.5  core     — the body itself, small and hard-edged
//   >= 0.5  corona   — tight halo hugging the body
//   else    bloom    — wide soft glow, drawn first and largest
//
// Three layers rather than one falloff because a single exponential either has a visible
// edge or no centre. Stacking them is what makes a small bright thing read as *bright*
// instead of merely white, and brightness is the point — these are the parts of the effect
// that are supposed to hurt to look at.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    float intensity = vertexColor.a;
    if (intensity < 0.002) {
        discard;
    }
    vec2 centred = uvCoord * 2.0 - 1.0;
    float radius = length(centred);
    if (radius > 1.0) {
        discard;
    }

    float layer = ColorModulator.a;
    float lit;
    if (layer >= 1.5) {
        // The body. Flat and saturated across most of it with a fast rolloff at the rim,
        // so it reads as a surface rather than as a smudge.
        lit = smoothstep(1.0, 0.62, radius) * 1.35;
        // A hotter pip at the very centre. Deliberately over-bright.
        lit += exp(-radius * radius * 26.0) * 0.9;
    } else if (layer >= 0.5) {
        // Corona: hugging the body, falling off fast.
        lit = exp(-radius * radius * 5.5) * 0.75;
        // Spikes, four-fold. Not physical — it is the look of something too bright for the
        // eye, and that is what is wanted here.
        float ang = atan(centred.y, centred.x);
        float spike = pow(abs(cos(ang * 2.0)), 8.0) * exp(-radius * 2.2) * 0.55;
        lit += spike;
    } else {
        // Bloom: wide, weak, and the thing that makes the body feel like it is emitting
        // rather than being lit.
        lit = pow(1.0 - radius, 2.6) * 0.42;
    }

    vec3 colour = vertexColor.rgb * lit * intensity;
    fragColor = vec4(colour, 1.0) * vec4(ColorModulator.rgb, 1.0);
}
