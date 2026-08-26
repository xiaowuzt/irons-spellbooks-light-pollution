#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 screenSize;

out vec2 fragCoord;
out vec4 vertexColor;
out vec3 worldPos;
out vec3 fPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vec2 ndc = gl_Position.xy / gl_Position.w;
    fragCoord = (ndc * 0.5 + 0.5) * screenSize;

    vertexColor = Color;
    worldPos = Position;

    fPos = -Position;
}
