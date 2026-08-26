#version 150

// A star as a body: photosphere with limb darkening, granulation, and a corona.
// Deliberately separate from funeral_nova_orb, whose vertex-colour channels drive
// a nova shell animation and whose palette is tuned orange-red for that effect.

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
