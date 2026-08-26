#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 uvCoord;
// See leviathan.vsh: per-fragment normals, because a per-vertex facing term
// creases at every quad edge and makes a round trunk look like a prism.
out vec3 surfaceNormal;
out vec3 toCamera;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord = UV0;
    surfaceNormal = Normal;
    toCamera = -Position;
}
