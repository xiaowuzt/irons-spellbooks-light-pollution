#version 150

// Forge 1.20.1 port of Gemini KillEffect (LGPL-2.1).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 SphereCenter;

out vec4 vertexColor;
out vec2 uvCoord;
out vec3 vViewPos;
out vec3 vSphereCenter;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vViewPos = viewPos.xyz;
    vSphereCenter = (ModelViewMat * vec4(SphereCenter, 1.0)).xyz;
    vertexColor = Color;
    uvCoord = UV0;
}
