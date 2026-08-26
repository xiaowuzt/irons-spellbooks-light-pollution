#version 150

uniform sampler2D Sampler0;
uniform float time;
uniform float FlowSpeed;
uniform int colorCount;
uniform int color1, color2, color3, color4, color5, color6, color7, color8, color9, color10, color11, color12, color13, color14, color15, color16;
uniform float alpha;
uniform float gradientSpan;

in vec2 texCoord0;

out vec4 fragColor;

vec3 intToColor(int colorInt) {
    float r = float((colorInt >> 16) & 0xFF) / 255.0;
    float g = float((colorInt >> 8) & 0xFF) / 255.0;
    float b = float(colorInt & 0xFF) / 255.0;
    return vec3(r, g, b);
}

vec3 getColorByIndex(int index) {
    if (index == 0) return intToColor(color1);
    else if (index == 1) return intToColor(color2);
    else if (index == 2) return intToColor(color3);
    else if (index == 3) return intToColor(color4);
    else if (index == 4) return intToColor(color5);
    else if (index == 5) return intToColor(color6);
    else if (index == 6) return intToColor(color7);
    else if (index == 7) return intToColor(color8);
    else if (index == 8) return intToColor(color9);
    else if (index == 9) return intToColor(color10);
    else if (index == 10) return intToColor(color11);
    else if (index == 11) return intToColor(color12);
    else if (index == 12) return intToColor(color13);
    else if (index == 13) return intToColor(color14);
    else if (index == 14) return intToColor(color15);
    else if (index == 15) return intToColor(color16);
    else return intToColor(color1);
}

vec3 getGradientColor(float t) {
    if (colorCount <= 1) {
        return getColorByIndex(0);
    }

    t = fract(t);

    float scaledT = t * float(colorCount - 1);
    int index = int(floor(scaledT));
    float fraction = fract(scaledT);

    index = clamp(index, 0, colorCount - 2);

    vec3 color1 = getColorByIndex(index);
    vec3 color2 = getColorByIndex(index + 1);

    return mix(color1, color2, fraction);
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);

    float flowingTime = time * FlowSpeed;

    float gradientPos = (texCoord0.x * gradientSpan) - flowingTime;

    vec3 gradientColor = getGradientColor(gradientPos);

    float maskStrength = (texColor.r + texColor.g + texColor.b) / 3.0;

    vec3 finalColor = gradientColor * maskStrength * alpha;
    fragColor = vec4(finalColor, texColor.a);
}
