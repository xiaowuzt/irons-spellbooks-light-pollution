#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 uvCoord;
out vec3 surfaceNormal;
out vec3 toCamera;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord = UV0;
    // A real geometric normal off the mesh, not reconstructed from a billboard frame. On a sphere
    // this is exact, which is what lets the surface detail stay pinned to the surface.
    surfaceNormal = Normal;
    // Position is camera-relative in every renderer here, so the direction back to the eye is
    // just the negated position.
    toCamera = -Position;
}
