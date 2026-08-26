#version 150

// Adapted from ShaderTest by CeliaClaire; see THIRD_PARTY_NOTICES.md.

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;

out vec4 vertexColor;
out vec3 spherePos;

void main() {
    vec3 normalized = normalize(Position);
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    spherePos = normalized;
}
