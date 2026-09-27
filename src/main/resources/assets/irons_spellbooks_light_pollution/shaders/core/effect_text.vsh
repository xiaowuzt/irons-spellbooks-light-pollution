#version 150

// Passes the glyph's effect data through to the fragment stage.
//
// The vertex position is untouched. Every effect this shader serves works in the fragment stage —
// outline samples around a pixel, extrusion stacks offsets, chromatic separates channels — and the
// effects that do move a glyph are applied on the CPU before the quad is emitted, which is what lets
// them work under ModernUI's own layout as well.

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler2;

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
// The four attributes vanilla text does not have.
in float EffectId;
in vec4 EffectColor;
in vec4 EffectParams;
in vec4 GlyphBounds;

out vec4 vertexColor;
out vec2 texCoord0;
out float effectId;
out vec4 effectColor;
out vec4 effectParams;
out vec4 glyphBounds;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    effectId = EffectId;
    effectColor = EffectColor;
    effectParams = EffectParams;
    glyphBounds = GlyphBounds;
}
