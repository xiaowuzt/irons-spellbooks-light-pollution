#version 150

// Full-screen quad equivalent of Gemini's vertex-ID triangle.
in vec3 Position;
in vec2 UV0;

out vec2 vUv;

void main() {
    vUv = UV0;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}