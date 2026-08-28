#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 uvCoord;
out vec3 basisRight;
out vec3 viewRay;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    uvCoord = UV0;
    // The billboard's world-space right vector, which the fragment stage needs to rebuild a
    // sphere normal that stays put on the surface as the camera moves. Without a world-space
    // frame the surface detail swims with the view, which is what makes a billboard announce
    // itself as a billboard.
    basisRight = Normal;
    // Camera-relative, because every renderer feeding this subtracts the camera position, so
    // -normalize(viewRay) is the direction from the fragment back to the eye.
    viewRay = Position;
}
