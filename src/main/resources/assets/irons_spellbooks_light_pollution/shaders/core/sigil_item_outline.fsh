#version 150

uniform sampler2D Sampler0;
uniform vec4 OutlineColor;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float textureAlpha = texture(Sampler0, texCoord0).a;
    if (textureAlpha < 0.1) {
        discard;
    }
    fragColor = vec4(OutlineColor.rgb, OutlineColor.a * textureAlpha);
}
