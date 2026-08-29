#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 uvCoord;
out vec3 fPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord = UV0;
    // The march direction comes from the vertex position. In GUI space that is screen
    // coordinates, so it varies across the shape and gives every part of the band a different
    // line of sight through the field.
    fPos = -Position;
}
