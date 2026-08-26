#version 150

// Leviathan. The body is a closed tube whose section is a rounded trapezoid with a
// flat ventral plate; the shader's job is to put a real snake's skin on it.
//
// Vertex layout POSITION_TEX_COLOR_NORMAL.
//   UV0.x = distance along the body in blocks (not normalised -- scale rows are laid
//           out at a physical size, so they do not stretch as the body extends)
//   UV0.y = fraction around the circumference, 0 and 1 both at the right flank,
//           0.25 dorsal, 0.75 ventral
//   Color = (aux, mode, intensity, fade)
//   Normal = true outward surface normal
// Mode bands: < 0.3 body, < 0.55 tooth, < 0.8 eye, else jaw.
//
// The previous version read `along` out of Color.r and `across` out of UV0.y. Those
// channels carry the facing term and the circumferential coordinate respectively,
// so every effect keyed on them was wrong: a high-frequency sine on the facing term
// drew view-dependent stripes bunched at the silhouette, and a hard flank cutoff on
// the circumference punched a hole along one line of the tube. Both are gone.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
// x = wrapped seconds, y = how far the body has come apart 0..1
uniform vec4 BodyState;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 surfaceNormal;
in vec3 toCamera;

out vec4 fragColor;

const float PI = 3.14159265;

float noiseAt(vec2 uv) {
    return texture(Sampler0, fract(uv)).r;
}

float noiseG(vec2 uv) {
    return texture(Sampler0, fract(uv)).g;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float aux       = vertexColor.r;
    float mode      = vertexColor.g;
    float intensity = vertexColor.b * 4.0;
    float fade      = vertexColor.a;

    if (fade < 0.002 || intensity < 0.002) {
        discard;
    }

    float t       = BodyState.x;
    float unravel = clamp(BodyState.y, 0.0, 1.0);
    float along   = uvCoord.x;          // blocks from the snout
    float around  = fract(uvCoord.y);   // 0..1 round the tube

    // Per-fragment facing. Interpolating the normal and taking the dot here rather
    // than handing over a per-vertex dot is what keeps a 16-sided tube from reading
    // as 16 flat strips: a per-vertex term is linear across each quad, so every
    // quad boundary shows up as a crease.
    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(toCamera);
    float facing = clamp(dot(normal, view), 0.0, 1.0);
    // Rim: bright where the surface turns away, which is what gives volume.
    float rim = pow(1.0 - facing, 2.2);

    // Where we are on the animal's circumference. 0.25 is the spine, 0.75 the belly.
    // Signed so dorsal is +1 and ventral -1, via the cosine of the angle from the
    // dorsal midline -- continuous everywhere, unlike a branch on the raw fraction.
    float dorsalAxis = cos((around - 0.25) * 2.0 * PI);
    // Distance from the dorsal midline, 0 at the spine and 1 at the belly.
    float fromSpine = 0.5 - 0.5 * dorsalAxis;

    // As it comes apart the body tears into strips along its length.
    if (unravel > 0.001) {
        float strip = noiseAt(vec2(along * 0.08, floor(around * 9.0) * 0.19));
        if (strip < unravel * 1.15) {
            discard;
        }
    }

    if (mode < 0.3) {
        // ── Body ──────────────────────────────────────────────────────
        // Ventral scutes are single broad transverse plates, one per body segment,
        // 118 to 166 of them over the whole animal. Nothing like the dorsal scales,
        // so the belly gets its own treatment entirely.
        float ventral = smoothstep(0.62, 0.80, fromSpine);

        // ── Dorsal scales ──
        // Row count falls from 21 at midbody to 17 before the vent, as it does on a
        // real snake as the body tapers.
        float rows = mix(21.0, 17.0, clamp(along / 90.0, 0.0, 1.0));
        // Rows run obliquely, not in a straight grid -- "straight versus oblique
        // dorsal scale rows" is a standard diagnostic character, and a square grid
        // is exactly what made the old version look like stitched tiles.
        float skew = along * 0.22;
        float rowCoord = around * rows + skew;
        float row = floor(rowCoord);
        float inRow = fract(rowCoord);
        // Scales are about twice as long as wide, and staggered row to row so they
        // interlock instead of lining up into columns. Body circumference at full
        // girth is about 12.6 blocks, so 21 rows puts a scale at 0.6 blocks wide
        // and therefore 1.2 long.
        float scaleWidth = 12.57 / rows;
        float scaleLength = scaleWidth * 2.0;
        float colCoord = along / scaleLength + mod(row, 2.0) * 0.5;
        float inCol = fract(colCoord);

        // Rhombic outline. Imbricate: the free edge is the trailing one, so the
        // gradient runs the length of each scale and brightens at its rear margin.
        vec2 local = vec2(inRow - 0.5, inCol - 0.5) * 2.0;
        float rhombus = 1.0 - clamp(abs(local.x) * 0.82 + abs(local.y) * 0.62, 0.0, 1.0);
        // Keel: a raised ridge down the middle of each scale.
        float keel = exp(-pow(local.x / 0.30, 2.0)) * smoothstep(0.0, 0.35, rhombus);
        // Free margin catching the light where this scale overlaps the next.
        float margin = smoothstep(0.55, 0.95, inCol) * smoothstep(0.12, 0.4, rhombus);
        // Each scale is its own shade, but the variation is gentle -- a strong
        // per-cell brightness step is what reads as square tiling.
        float tone = 0.88 + 0.12 * hash12(vec2(row, floor(colCoord)));

        float dorsalSkin = (rhombus * 0.55 + keel * 0.85 + margin * 0.75) * tone;

        // ── Ventral scutes ──
        // One broad plate per segment, bright seam between plates, and no
        // longitudinal division at all -- that is what makes a belly a belly.
        float scutes = along / 0.64;                    // ~140 over 90 blocks
        float inScute = fract(scutes);
        float seam = exp(-pow((inScute - 0.5) / 0.10, 2.0));
        float plate = 0.72 + 0.28 * smoothstep(0.0, 0.35, inScute)
                * smoothstep(1.0, 0.65, inScute);
        float ventralSkin = plate * 0.9 + seam * 0.7;

        float skin = mix(dorsalSkin, ventralSkin, ventral);

        // The vertebral line: keeled scales along the spine form a visible ridge on
        // a real snake, which is what tells the eye which way up it is now the
        // fictional dorsal spines are gone.
        float vertebral = exp(-pow(fromSpine / 0.13, 2.0));

        // A pulse of light running head to tail, so the body looks alive rather than
        // extruded. Keyed on real distance now, so it travels at a fixed speed.
        float pulse = exp(-pow(fract(along / 64.0 - t * 0.55) - 0.5, 2.0) / 0.02);

        float brightness = (skin * 0.85 + vertebral * 0.9 + rim * 1.15 + pulse * 0.8
                + facing * 0.30) * intensity;

        // Cold void-blue over the flanks, hot white along the spine, and a pale
        // belly, as almost every real snake has.
        vec3 flankColour = vec3(0.20, 0.42, 0.86);
        vec3 spineColour = vec3(0.92, 0.98, 1.0);
        vec3 bellyColour = vec3(0.72, 0.80, 0.94);
        vec3 rgb = mix(flankColour, spineColour,
                clamp(vertebral * 1.3 + pulse + keel * 0.4, 0.0, 1.0));
        rgb = mix(rgb, bellyColour, ventral * 0.8);
        rgb *= brightness;

        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        if (alpha < 0.0015) {
            discard;
        }
        fragColor = vec4(rgb, alpha) * ColorModulator;
        return;
    }

    if (mode < 0.55) {
        // ── Tooth ─────────────────────────────────────────────────────
        // aux is 1 for a fang and 0.4 for the teeth behind it. Enamel: bright at the
        // tip, translucent at the root, with a sharp rim so the recurve reads.
        float toTip = clamp(along, 0.0, 1.0);
        float enamel = mix(0.55, 1.0, toTip);
        float brightness = (enamel * 1.5 + rim * 1.8) * mix(0.7, 1.25, aux) * intensity;
        vec3 rgb = mix(vec3(0.78, 0.86, 0.96), vec3(1.0, 0.99, 0.96), toTip) * brightness;
        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        if (alpha < 0.0015) {
            discard;
        }
        fragColor = vec4(rgb, alpha) * ColorModulator;
        return;
    }

    if (mode < 0.8) {
        // ── Eye ───────────────────────────────────────────────────────
        // A hot slit pupil in a darker iris. The slit runs with the body, which is
        // why the eye reads as an eye and not as a bead.
        float slit = exp(-pow((around - 0.25) / 0.055, 2.0))
                + exp(-pow((around - 0.75) / 0.055, 2.0));
        float iris = 0.35 + 0.4 * facing;
        float brightness = (iris + slit * 2.6 + rim * 1.2) * intensity * 1.3;
        vec3 rgb = mix(vec3(0.86, 0.30, 0.12), vec3(1.0, 0.96, 0.82),
                clamp(slit, 0.0, 1.0)) * brightness;
        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        if (alpha < 0.0015) {
            discard;
        }
        fragColor = vec4(rgb, alpha) * ColorModulator;
        return;
    }

    // ── Jaw ───────────────────────────────────────────────────────────
    // Hot throat on the inside, dark plated bone outside, and a row of labial
    // scales along the lip. `fromSpine` is the way round the jaw bar, so the
    // throat side and the outer side can differ.
    float inner = smoothstep(0.45, 0.9, fromSpine);
    // Labial scales along the lip, roughly one per block of a nine-block jaw.
    float labial = 0.55 + 0.45 * exp(-pow(
            (fract(along * 9.0) - 0.5) / 0.26, 2.0));
    float plate = 0.34 + 0.3 * noiseAt(vec2(along * 4.0, fromSpine * 3.0));

    float brightness = (inner * 1.7 + plate * labial * 0.7 + rim * 1.1) * intensity;
    vec3 rgb = mix(vec3(0.30, 0.13, 0.44), vec3(1.0, 0.88, 0.74),
            clamp(inner * 1.1, 0.0, 1.0)) * brightness;
    float alpha = clamp(brightness, 0.0, 1.0) * fade;
    if (alpha < 0.0015) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
