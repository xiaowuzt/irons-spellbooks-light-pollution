"""The six remaining fragment-stage effects.

All ported from TheSalts' Text_Effects (MIT) with their maths and constants intact. Two departures,
both because our vertex format carries one effect colour rather than three:

  - extrude keeps modes 0 (auto-darken) and 1 (base-to-effect gradient) and drops mode 2, which
    interpolates across three colours. Mode 2 would need two more vec4 attributes for a look the other
    two already cover.
  - Everywhere the original reconstructs the glyph cell from four projected corners, we read it from the
    GlyphBounds attribute, which the CPU side already knows.
"""
import sys
from pathlib import Path

p = Path(sys.argv[1])
t = p.read_text(encoding="utf-8")

DEFINES_OLD = """#define EFFECT_NONE      0
#define EFFECT_OUTLINE   1
#define EFFECT_NEON      2
#define EFFECT_CHROMATIC 3"""

DEFINES_NEW = """#define EFFECT_NONE      0
#define EFFECT_OUTLINE   1
#define EFFECT_NEON      2
#define EFFECT_CHROMATIC 3
#define EFFECT_EXTRUDE   4
#define EFFECT_HATCH     5
#define EFFECT_NOISE     6
#define EFFECT_LIQUID    7
#define EFFECT_WATER     8
#define EFFECT_SPLIT     9

/** Cheap hash. The shared common.glsl is a vertex-stage include, so this is local. */
float rnd(vec2 seed) {
    return fract(sin(dot(seed, vec2(12.9898, 78.233))) * 43758.5453);
}"""

BODIES = """/**
 * The glyph stacked behind itself, receding down and right.
 *
 * <p>Modes 0 and 1 only: auto-darkening, and a gradient from the glyph's colour to the effect colour.
 * The original's third mode interpolates across three colours, which would need two more vec4
 * attributes for a look these two already cover.</p>
 */
void extrude(out vec4 result) {
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = texture(Sampler0, texCoord0) * vertexColor;
        return;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    float depth = effectParams.x;
    int layers = max(int(effectParams.y + 0.5), 1);
    int mode = int(effectParams.z + 0.5);
    // Down and right, matching the direction vanilla's own text shadow falls.
    vec2 stride = vec2(depth, depth) / texSize;

    float centre = texture(Sampler0, texCoord0).a;
    if (centre > 0.1) {
        result = vec4(vertexColor.rgb, centre * vertexColor.a);
        return;
    }

    // Nearest layer first, stopping at the first hit — a deeper layer sits behind this one.
    for (int i = 1; i <= 16; i++) {
        if (i > layers) {
            break;
        }
        vec2 at = texCoord0 - stride * float(i);
        if (at.x < uvMin.x || at.x > uvMax.x || at.y < uvMin.y || at.y > uvMax.y) {
            continue;
        }
        float a = texture(Sampler0, at).a;
        if (a > 0.5) {
            if (mode == 1 && layers > 1) {
                float k = float(i - 1) / float(layers - 1);
                result = vec4(mix(vertexColor.rgb, effectColor.rgb, k),
                              a * mix(vertexColor.a, effectColor.a, k));
            } else if (mode == 1) {
                result = vec4(effectColor.rgb, a * effectColor.a);
            } else {
                float darken = float(i) / float(layers + 1);
                result = vec4(vertexColor.rgb * (1.0 - darken * 0.65), a * vertexColor.a);
            }
            return;
        }
    }
    discard;
}

/** Diagonal stripes sweeping across the glyph, a quarter on and three quarters off. */
void hatch(out vec4 result) {
    float centre = texture(Sampler0, texCoord0).a;
    if (centre < 0.1) {
        discard;
    }
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = vec4(vertexColor.rgb, centre * vertexColor.a);
        return;
    }

    vec2 size = uvMax - uvMin;
    float u = size.x > 0.0001 ? (texCoord0.x - uvMin.x) / size.x : 0.5;
    float v = size.y > 0.0001 ? (texCoord0.y - uvMin.y) / size.y : 0.5;
    float density = max(effectParams.z, 0.5);

    float band = fract((u + v) * density - GameTime * effectParams.y * 2.0);
    float onStripe = 1.0 - step(0.25, band);
    result = vec4(mix(vertexColor.rgb, effectColor.rgb, onStripe),
                  centre * mix(vertexColor.a, max(vertexColor.a, effectColor.a), onStripe));
}

/** Per-scanline jitter quantised to discrete frames, for a television-static read. */
void noise(out vec4 result) {
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = texture(Sampler0, texCoord0) * vertexColor;
        return;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    // Floored, so the jitter jumps between frames rather than sliding; sliding reads as a wobble.
    float t = floor(GameTime * effectParams.y * 1500.0);
    vec2 jitter = vec2(rnd(vec2(floor(texCoord0.y * texSize.y) + t, 0.0)) - 0.5,
                       rnd(vec2(floor(texCoord0.x * texSize.x) + t, 1.0)) - 0.5)
                  * effectParams.x / texSize;

    vec2 at = texCoord0 + jitter;
    if (at.x < uvMin.x || at.x > uvMax.x || at.y < uvMin.y || at.y > uvMax.y) {
        discard;
    }
    float a = texture(Sampler0, at).a;
    if (a < 0.1) {
        discard;
    }
    result = vec4(vertexColor.rgb * (0.85 + 0.3 * rnd(vec2(t, 0.5))), a * vertexColor.a);
}

/** Smooth turbulent displacement, from crossed sines running at different rates. */
void liquid(out vec4 result) {
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = texture(Sampler0, texCoord0) * vertexColor;
        return;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    float t = GameTime * effectParams.y * 1000.0;
    vec2 displace = vec2(
        sin(texCoord0.y * 40.0 + t) * cos(texCoord0.x * 25.0 - t * 0.7),
        cos(texCoord0.x * 40.0 - t) * sin(texCoord0.y * 25.0 + t * 0.7)
    ) * effectParams.x / texSize;

    vec2 at = texCoord0 + displace;
    if (at.x < uvMin.x || at.x > uvMax.x || at.y < uvMin.y || at.y > uvMax.y) {
        discard;
    }
    float a = texture(Sampler0, at).a;
    if (a < 0.1) {
        discard;
    }
    result = vec4(vertexColor.rgb, a * vertexColor.a);
}

/** The glyph filling with water to a level, with a two-frequency surface. */
void water(out vec4 result) {
    float centre = texture(Sampler0, texCoord0).a;
    if (centre < 0.1) {
        discard;
    }
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = vec4(vertexColor.rgb, centre * vertexColor.a);
        return;
    }

    vec2 size = uvMax - uvMin;
    float u = size.x > 0.0001 ? (texCoord0.x - uvMin.x) / size.x : 0.5;
    float v = size.y > 0.0001 ? (texCoord0.y - uvMin.y) / size.y : 0.5;

    float level = clamp(effectParams.x, 0.0, 1.0);
    float t = GameTime * effectParams.z * 12000.0;
    float freq = max(effectParams.w, 0.5);
    // Two frequencies rather than one, so the surface does not read as a single clean sine.
    float wave = (sin(u * 6.2831853 * freq + t) * 0.6
                + sin(u * 6.2831853 * freq * 2.0 + t * 1.3) * 0.4) * effectParams.y * 0.08;
    float surface = (1.0 - level) + wave;

    if (v < surface) {
        result = vec4(vertexColor.rgb, centre * vertexColor.a);
        return;
    }

    float below = 1.0 - surface > 0.0001 ? clamp((v - surface) / (1.0 - surface), 0.0, 1.0) : 0.5;
    vec3 shallow = effectColor.rgb;
    vec3 colour = mix(shallow, shallow * 0.45, below);
    // A thin bright line right at the surface, which is what makes it read as a waterline.
    float glint = smoothstep(0.0, 0.05, below) * (1.0 - smoothstep(0.0, 0.10, below));
    colour = mix(colour, mix(shallow, vec3(1.0), 0.5), glint * 0.4);
    result = vec4(colour, centre * effectColor.a);
}

/** The top half of the glyph sliding sideways against the bottom. */
void split(out vec4 result) {
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = texture(Sampler0, texCoord0) * vertexColor;
        return;
    }

    vec2 size = uvMax - uvMin;
    float v = size.y > 0.0001 ? (texCoord0.y - uvMin.y) / size.y : 0.5;
    bool top = v < 0.5;

    float slide = sin(GameTime * effectParams.y * 3000.0) * 0.5 + 0.5;
    float ratio = clamp(effectParams.x * 0.2, 0.05, 0.6);

    float u;
    if (top) {
        u = (texCoord0.x - uvMin.x) * (1.0 + ratio) - (1.0 - slide) * ratio * size.x;
        if (u < 0.0 || u > size.x) {
            discard;
        }
    } else {
        u = (texCoord0.x - uvMin.x) * (1.0 + ratio) - ratio * size.x;
        if (u < 0.0) {
            discard;
        }
    }

    float a = texture(Sampler0, vec2(uvMin.x + u, texCoord0.y)).a;
    if (a < 0.1) {
        discard;
    }
    result = vec4(vertexColor.rgb, a * vertexColor.a);
}

void main() {"""

DISPATCH_OLD = """    } else if (id == EFFECT_CHROMATIC) {
        chromatic(result);
    } else {"""

DISPATCH_NEW = """    } else if (id == EFFECT_CHROMATIC) {
        chromatic(result);
    } else if (id == EFFECT_EXTRUDE) {
        extrude(result);
    } else if (id == EFFECT_HATCH) {
        hatch(result);
    } else if (id == EFFECT_NOISE) {
        noise(result);
    } else if (id == EFFECT_LIQUID) {
        liquid(result);
    } else if (id == EFFECT_WATER) {
        water(result);
    } else if (id == EFFECT_SPLIT) {
        split(result);
    } else {"""

for old, new in ((DEFINES_OLD, DEFINES_NEW), ("void main() {", BODIES), (DISPATCH_OLD, DISPATCH_NEW)):
    assert old in t, old[:50]
    t = t.replace(old, new, 1)

p.write_text(t, encoding="utf-8")
print(f"{p.name}: six effects added, now {len(t.splitlines())} lines")
