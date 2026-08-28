#version 150

// The surface of the tooltip ring: a window onto deep space.
//
// The ring in ArcaneVortex is drawn twice, and the inner of the two passes goes through its
// cosmic render type — the band is not a coloured surface, it has a star field inside it. That
// is the whole reason the shape works: a flat ring reads as a drawn circle, and a ring you can
// see stars through reads as an opening.
//
// This does not port their cosmic stack. It samples the volumetric star field this mod already
// carries, the same one Gargantua's bent rays look out at, so the two agree by construction.
//
// UV0.x runs around the ring and UV0.y across the band. A direction is built from those and
// handed to the field, which means a star belongs to a place on the ring rather than to a place
// on the screen: the field turns with the band instead of sliding underneath it.

#moj_import <irons_spellbooks_light_pollution:star_nest.glsl>

uniform vec4 ColorModulator;
uniform float Drift;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

const float TAU = 6.28318530718;

void main() {
    float alpha = vertexColor.a;
    if (alpha < 0.004) {
        discard;
    }

    float around = uvCoord.x * TAU;
    // -1 at one lip of the band, +1 at the other.
    float across = uvCoord.y * 2.0 - 1.0;

    // A direction on a sphere: around the ring for longitude, across the band for latitude. The
    // latitude range is deliberately narrow, so the band shows a strip of sky rather than the
    // whole of it squeezed into a few pixels.
    vec3 dir = normalize(vec3(cos(around), across * 0.55, sin(around)));
    vec3 stars = starNest(dir, Drift);

    // Tinted toward the spell's accent rather than left as the field's own colours. Everything
    // else in these tooltips is that one colour, and a rainbow ring would not belong.
    vec3 tinted = mix(stars, stars * vertexColor.rgb * 2.2, 0.75);

    // The field is dark almost everywhere, which for a backdrop is right and for a band is not:
    // the ring would vanish between stars. A dim floor of the accent keeps the band readable as
    // a shape, and the stars sit on top of it.
    vec3 body = vertexColor.rgb * 0.10;

    // Soft at both lips. A hard edge is what makes a band look like a drawn outline, and this
    // one is meant to look like an opening.
    float edge = 1.0 - across * across;
    float shape = smoothstep(0.0, 0.45, edge);

    fragColor = vec4((body + tinted) * shape, alpha * shape) * ColorModulator;
}
