#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 uvCoord;
// Surface normal in camera-relative world space, and the direction from the surface to the
// camera. Interpolating these and taking the dot per fragment is what stops a sixteen-sided
// tube from looking like sixteen flat strips: a per-vertex facing term is linear across each
// quad, so every quad edge shows as a crease.
out vec3 surfaceNormal;
out vec3 toCamera;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord = UV0;
    surfaceNormal = Normal;
    // Vertices arrive camera-relative, so the camera is the origin.
    toCamera = -Position;
}
