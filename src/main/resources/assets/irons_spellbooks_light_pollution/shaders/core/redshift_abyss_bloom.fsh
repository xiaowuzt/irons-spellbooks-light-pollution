#version 150
// R1 Buffer B/C/D and Image: sonicether, "Gargantua With HDR Bloom", Shadertoy lstSRS.
// Supplied in baopinsui's "GR BH with volume accretion disk", Shadertoy 4XcfR2.
// Original formulas: docs/black-hole/references/R1-original.txt.
// Source attribution/licensing: THIRD_PARTY_NOTICES.md.
uniform sampler2D bhInputSampler;
uniform vec4 bhBlurStep; // C: (.5/width, 0, 0, 0); D: (0, .5/height, 0, 0)
in vec2 texCoord;
out vec4 fragColor;

// Original horizontal/vertical blur, selected only by the half-texel axis below.

vec3 ColorFetch(vec2 coord)
{
 	return texture(bhInputSampler, coord).rgb;   
}

float weights[5];
float offsets[5];


void main()
{    
    
    weights[0] = 0.19638062;
    weights[1] = 0.29675293;
    weights[2] = 0.09442139;
    weights[3] = 0.01037598;
    weights[4] = 0.00025940;
    
    offsets[0] = 0.00000000;
    offsets[1] = 1.41176471;
    offsets[2] = 3.29411765;
    offsets[3] = 5.17647059;
    offsets[4] = 7.05882353;
    
    // Source pixel centres, not interpolated UVs (which drift on odd-size HDR impulses).
    // Both C/D targets and their input are the same full-size atlas.
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(bhInputSampler, 0));
    
    vec3 color = vec3(0.0);
    float weightSum = 0.0;
    
    if (uv.x < 0.52)
    {
        color += ColorFetch(uv) * weights[0];
        weightSum += weights[0];

        for(int i = 1; i < 5; i++)
        {
            vec2 offset = offsets[i] * bhBlurStep.xy;
            color += ColorFetch(uv + offset) * weights[i];
            color += ColorFetch(uv - offset) * weights[i];
            weightSum += weights[i] * 2.0;
        }

        color /= weightSum;
    }

    fragColor = vec4(color,1.0);
}
