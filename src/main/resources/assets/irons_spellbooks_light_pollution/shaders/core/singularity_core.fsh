#version 150

// Singularity's body: the nested cage from Some of FX's singularity bomb
// (github.com/YangMao-Minister/some_of_fx, MIT, (c) 2026 Pizuka), rewritten as
// direct geometry because its model targets a newer format than this runs on.
//
// This is the one part of the effect that is NOT additive. It writes depth and
// blends normally, so the shell genuinely occludes what is behind it and the dark
// interior stays dark. Everything else about the spell glows; without a solid
// body to sit inside, a "black hole" under additive blending is only ever a ring.
//
// Vertex colour: rgb = tint, a = fade.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, uvCoord);
    if (texel.a < 0.04) {
        discard;
    }
    vec4 result = texel * vertexColor * ColorModulator;
    if (result.a < 0.04) {
        discard;
    }
    fragColor = result;
}
