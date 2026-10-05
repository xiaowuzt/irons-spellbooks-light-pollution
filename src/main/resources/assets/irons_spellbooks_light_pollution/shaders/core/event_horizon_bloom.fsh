#version 150
uniform sampler2D InputSampler;
uniform vec4 BlurStep;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 d = BlurStep.xy;
    vec3 c = texture(InputSampler, texCoord).rgb * 0.227027;
    c += (texture(InputSampler, texCoord + d * 1.384615).rgb + texture(InputSampler, texCoord - d * 1.384615).rgb) * 0.316216;
    c += (texture(InputSampler, texCoord + d * 3.230769).rgb + texture(InputSampler, texCoord - d * 3.230769).rgb) * 0.070270;
    fragColor = vec4(c, 1.0);
}
