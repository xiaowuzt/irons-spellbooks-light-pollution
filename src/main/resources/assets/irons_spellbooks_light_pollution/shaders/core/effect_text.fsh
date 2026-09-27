#version 150

// Fragment-stage text effects.
//
// The outline maths is ported from TheSalts' Text_Effects (MIT) — eight-direction sampling with the
// samples clamped to this glyph's own atlas cell. That clamp is the load-bearing part: the font atlas
// packs glyphs adjacent, so an unclamped sample reads whichever letter happens to sit next door and
// the outline fills with fragments of unrelated characters.
//
// Changed from the original: it took the glyph cell from four projected corner attributes it needed
// for its spin effect; here the cell arrives directly as (u0, v0, u1, v1), which is what the CPU side
// already knows.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 texCoord0;
in float effectId;
in vec4 effectColor;
in vec4 effectParams;
in vec4 glyphBounds;

out vec4 fragColor;

#define EFFECT_NONE      0
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
}

/** True when the CPU side filled in a usable atlas cell for this glyph. */
bool cell(out vec2 uvMin, out vec2 uvMax) {
    uvMin = glyphBounds.xy;
    uvMax = glyphBounds.zw;
    // A degenerate cell means it was not filled in. Falling back to the whole atlas would sample every
    // glyph, so the callers treat this as no effect rather than as a licence to roam.
    return uvMax.x > uvMin.x && uvMax.y > uvMin.y;
}

/** Eight-direction alpha probe, clamped to this glyph's cell. */
void outline(out vec4 result) {
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = texture(Sampler0, texCoord0) * vertexColor;
        return;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    float thickness = max(effectParams.x, 0.5);
    vec2 px = thickness / texSize;

    float centre = texture(Sampler0, texCoord0).a;

    float neighbour = 0.0;
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * 0.78539816;   // PI / 4
        vec2 at = texCoord0 + vec2(cos(angle), sin(angle)) * px;
        if (at.x < uvMin.x || at.x > uvMax.x || at.y < uvMin.y || at.y > uvMax.y) {
            continue;
        }
        neighbour = max(neighbour, texture(Sampler0, at).a);
    }

    if (centre < 0.1 && neighbour > 0.5) {
        result = effectColor;
    } else if (centre >= 0.1) {
        result = vec4(vertexColor.rgb, centre * vertexColor.a);
    } else {
        discard;
    }
}

/**
 * A glow around the glyph, sampled at three radii in eight directions.
 *
 * <p>Ported from TheSalts' Text_Effects (MIT). Unchanged apart from where the cell comes from — see the
 * note at the top of this file. Distinct from the CPU-side neon this mod already has, which fakes a
 * glow by drawing the glyph eleven times; this one is a real per-pixel falloff and draws once.</p>
 */
void neon(out vec4 result) {
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = texture(Sampler0, texCoord0) * vertexColor;
        return;
    }

    float centre = texture(Sampler0, texCoord0).a;
    float intensity = effectParams.x;
    float flickerSpeed = effectParams.y;
    vec2 texSize = vec2(textureSize(Sampler0, 0));

    float halo = 0.0;
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * 0.78539816;   // PI / 4
        for (float r = 1.0; r <= 3.0; r += 1.0) {
            vec2 at = texCoord0 + vec2(cos(angle), sin(angle)) * (r / texSize);
            if (at.x < uvMin.x || at.x > uvMax.x || at.y < uvMin.y || at.y > uvMax.y) {
                continue;
            }
            // Divided by the radius, so a near sample counts for more than a far one.
            halo += texture(Sampler0, at).a / r;
        }
    }
    halo = halo / 24.0 * intensity;

    float flicker = 1.0;
    if (flickerSpeed > 0.0001) {
        // Two sines at different rates, so it reads as an unsteady tube rather than a pulse.
        flicker = 0.85 + 0.15 * sin(GameTime * flickerSpeed * 5000.0)
                              * (0.5 + 0.5 * sin(GameTime * flickerSpeed * 10000.0 + 1.7));
    }
    vec3 lit = effectColor.rgb * flicker;

    if (centre > 0.1) {
        result = vec4(lit, centre * effectColor.a);
    } else if (halo > 0.05) {
        result = vec4(lit, halo * effectColor.a);
    } else {
        discard;
    }
}

/**
 * Red and blue pulled apart horizontally, screen-blended back together.
 *
 * <p>Ported from TheSalts' Text_Effects (MIT), constants unchanged.</p>
 */
void chromatic(out vec4 result) {
    vec2 uvMin, uvMax;
    if (!cell(uvMin, uvMax)) {
        result = texture(Sampler0, texCoord0) * vertexColor;
        return;
    }

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    float anim = 1.0 + 0.4 * sin(GameTime * effectParams.y * 1000.0);
    vec2 offset = vec2(effectParams.x * anim / texSize.x, 0.0);

    vec2 atR = texCoord0 - offset;
    vec2 atB = texCoord0 + offset;
    float aR = 0.0;
    float aB = 0.0;
    float aG = texture(Sampler0, texCoord0).a;
    if (atR.x >= uvMin.x && atR.x <= uvMax.x) {
        aR = texture(Sampler0, atR).a;
    }
    if (atB.x >= uvMin.x && atB.x <= uvMax.x) {
        aB = texture(Sampler0, atB).a;
    }

    float peak = max(max(aR, aG), aB);
    if (peak < 0.1) {
        discard;
    }
    vec3 colour = clamp(vec3(aR + aG * vertexColor.r,
                                  aG * vertexColor.g,
                             aB + aG * vertexColor.b), 0.0, 1.0);
    result = vec4(colour, peak * vertexColor.a);
}

/**
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

void main() {
    int id = int(effectId + 0.5);
    vec4 result;

    if (id == EFFECT_OUTLINE) {
        outline(result);
    } else if (id == EFFECT_NEON) {
        neon(result);
    } else if (id == EFFECT_CHROMATIC) {
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
    } else {
        result = texture(Sampler0, texCoord0) * vertexColor;
    }

    if (result.a < 0.01) {
        discard;
    }
    fragColor = result * ColorModulator;
}
