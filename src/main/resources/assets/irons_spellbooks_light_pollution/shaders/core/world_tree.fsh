#version 150

// World Tree. Half-buried roots, a buttressed trunk, four orders of branches, and
// leaf-cluster cards in the crown.
//
// Vertex layout POSITION_TEX_COLOR_NORMAL.
//   UV0.x = along the part. Blocks for wood, 0..1 petiole-to-tip for a leaf.
//   UV0.y = around the tube, or across the blade for a leaf.
//   Color = (aux, mode, intensity, fade). aux is leaf hue, or branch depth for wood.
//   Normal = true outward surface normal
// Mode bands: < 0.25 root, < 0.5 trunk, < 0.75 branch, else leaf.
//
// The old trunk found its facets with floor(uvCoord.y * 7.0) and shaded each of the
// seven bands a flat different brightness. Quantising a shading term like that is
// what produces visible stitched squares, so nothing here quantises anything: the
// buttresses are geometry, and every surface term below is continuous.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
// x = wrapped seconds, y = how far it has hardened into crystal 0..1
uniform vec4 TreeState;

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

/** Living gold, shifting to pale crystal as the tree hardens. */
vec3 woodColour(float hard, float lit) {
    vec3 living = mix(vec3(0.42, 0.26, 0.10), vec3(1.00, 0.82, 0.42), lit);
    vec3 crystal = mix(vec3(0.52, 0.60, 0.72), vec3(0.94, 0.98, 1.00), lit);
    return mix(living, crystal, hard);
}

/**
 * Bark shared by roots, trunk and branches. `along` is in blocks so the grain runs
 * at a real size instead of stretching with the part's length, and `girth` scales
 * the pattern down on thin twigs.
 */
float bark(float along, float around, float girth) {
    // Grain runs the length of the part, which is what wood does. Slight lateral
    // wander so the lines are not ruled.
    float wander = noiseG(vec2(along * 0.06, around * 1.7)) * 0.10;
    float lines = noiseAt(vec2(around * 7.0 * girth + wander, along * 0.22));
    // Coarse fissures on top, only on thick wood.
    float fissure = noiseG(vec2(around * 2.3, along * 0.07));
    fissure = smoothstep(0.55, 0.95, fissure) * clamp(girth, 0.0, 1.0);
    return 0.62 + 0.38 * lines - fissure * 0.30;
}

/**
 * One ovate leaflet. `p.x` is across the blade and `p.y` runs petiole to tip.
 * Returns coverage in x and midrib/vein brightness in y.
 */
vec2 leaflet(vec2 p) {
    if (p.y < 0.0 || p.y > 1.0) {
        return vec2(0.0);
    }
    // Widest at about 40% along, drawn out to an acuminate tip -- the outline that
    // makes a shape read as a leaf rather than as a lozenge.
    float width = 2.32 * sqrt(p.y) * pow(1.0 - p.y, 0.75);
    float edge = width - abs(p.x);
    if (edge <= 0.0) {
        return vec2(0.0);
    }
    float coverage = smoothstep(0.0, 0.06, edge);
    // Midrib.
    float rib = exp(-pow(p.x / 0.055, 2.0)) * smoothstep(0.02, 0.25, width);
    // Lateral veins, leaving the midrib at an angle and reaching the margin.
    float lateralPhase = fract((p.y - abs(p.x) * 0.55) * 7.0);
    float lateral = exp(-pow((lateralPhase - 0.5) / 0.17, 2.0)) * coverage * 0.55;
    // The margin catches light.
    float margin = (1.0 - smoothstep(0.0, 0.09, edge)) * 0.8;
    return vec2(coverage, rib * 1.3 + lateral + margin);
}

void main() {
    float aux       = vertexColor.r;
    float mode      = vertexColor.g;
    float intensity = vertexColor.b * 4.0;
    float fade      = vertexColor.a;

    if (fade < 0.002 || intensity < 0.002) {
        discard;
    }

    float t     = TreeState.x;
    float hard  = clamp(TreeState.y, 0.0, 1.0);
    float along = uvCoord.x;
    float around = fract(uvCoord.y);

    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(toCamera);
    float facing = clamp(dot(normal, view), 0.0, 1.0);
    float rim = pow(1.0 - facing, 2.0);
    // Light from above, so the tree has a top and a bottom.
    float sky = clamp(normal.y * 0.5 + 0.5, 0.0, 1.0);

    if (mode < 0.25) {
        // ── Root ──────────────────────────────────────────────────────
        // Thick and fissured, and only its upper surface is out of the ground, so
        // the sky term does most of the shaping.
        float skin = bark(along, around, 1.0);
        // Sap pulses travel from the trunk toward the tip.
        float sap = exp(-pow(fract(along / 22.0 - t * 0.9) - 0.5, 2.0) / 0.014);

        float brightness = (skin * (0.35 + 0.65 * sky) + sap * 1.1 + rim * 0.55)
                * intensity;
        vec3 rgb = woodColour(hard, clamp(sap + sky * 0.35, 0.0, 1.0)) * brightness;
        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        if (alpha < 0.0015) {
            discard;
        }
        fragColor = vec4(rgb, alpha) * ColorModulator;
        return;
    }

    if (mode < 0.5) {
        // ── Trunk ─────────────────────────────────────────────────────
        // The buttresses are real geometry now, so the shader only has to shade a
        // round surface -- the lobes show up through the normal on their own.
        float skin = bark(along, around, 1.0);
        // Sap rising, and it slows as it climbs.
        float rising = exp(-pow(fract(along / 34.0 - t * 0.5) - 0.5, 2.0) / 0.02);

        float brightness = (skin * (0.42 + 0.58 * facing) + rising * 0.9
                + rim * 0.85) * intensity;
        vec3 rgb = woodColour(hard, clamp(rising * 0.9 + facing * 0.25, 0.0, 1.0))
                * brightness;
        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        if (alpha < 0.0015) {
            discard;
        }
        fragColor = vec4(rgb, alpha) * ColorModulator;
        return;
    }

    if (mode < 0.75) {
        // ── Branch ────────────────────────────────────────────────────
        // aux is how far out this limb is, 0 for a primary and 1 for a twig. Bark
        // smooths and brightens toward the twigs, as real bark thins.
        float depth = aux;
        float skin = bark(along, around, 1.0 - depth * 0.7);
        float smoothed = mix(skin, 0.85, depth * 0.7);
        // The outermost order glows: that is where the crown is being fed.
        float feed = depth * depth * 0.9;

        float brightness = (smoothed * (0.45 + 0.55 * facing) + feed + rim * 0.8)
                * intensity;
        vec3 rgb = woodColour(hard, clamp(feed + facing * 0.3, 0.0, 1.0)) * brightness;
        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        if (alpha < 0.0015) {
            discard;
        }
        fragColor = vec4(rgb, alpha) * ColorModulator;
        return;
    }

    // ── Leaf cluster ──────────────────────────────────────────────────
    // Three leaflets on one card, so a card reads as a spray rather than as a single
    // outsized pane. p.x runs across the card, p.y petiole to tip.
    vec2 p = vec2((around - 0.5) * 2.0, along);
    float coverage = 0.0;
    float veins = 0.0;
    for (int i = 0; i < 3; i++) {
        float lean = (float(i) - 1.0) * 0.46;
        float cosLean = cos(lean);
        float sinLean = sin(lean);
        vec2 q = vec2(p.x * cosLean - p.y * sinLean, p.x * sinLean + p.y * cosLean);
        // The outer two leaflets are shorter, as they are on a real compound spray.
        float shrink = (i == 1) ? 1.0 : 0.78;
        vec2 hit = leaflet(vec2(q.x / shrink, q.y / shrink));
        coverage = max(coverage, hit.x);
        veins = max(veins, hit.y);
    }
    if (coverage <= 0.002) {
        discard;
    }

    float hue = aux;
    // Six-way palette. The hue distribution is weighted warm and green-teal by the
    // entity, so the magenta and violet bands are accents rather than a sixth of
    // the crown each.
    vec3 glass;
    float band = hue * 6.0;
    if (band < 1.0)      glass = mix(vec3(1.0, 0.82, 0.30), vec3(0.96, 0.58, 0.18), band);
    else if (band < 2.0) glass = mix(vec3(0.96, 0.58, 0.18), vec3(0.72, 0.86, 0.30), band - 1.0);
    else if (band < 3.0) glass = mix(vec3(0.72, 0.86, 0.30), vec3(0.24, 0.82, 0.56), band - 2.0);
    else if (band < 4.0) glass = mix(vec3(0.24, 0.82, 0.56), vec3(0.18, 0.72, 0.90), band - 3.0);
    else if (band < 5.0) glass = mix(vec3(0.18, 0.72, 0.90), vec3(0.52, 0.34, 0.92), band - 4.0);
    else                 glass = mix(vec3(0.52, 0.34, 0.92), vec3(0.90, 0.28, 0.52), band - 5.0);

    // Leaf cards are drawn two-sided, so the interpolated normal may point away
    // from the camera; only its magnitude is meaningful here. A blade seen face-on
    // presents its whole area and contributes most; edge-on it is nearly a line and
    // should almost vanish, which is what keeps the crown from looking like a solid
    // shell of colour from every angle.
    float blade = abs(dot(normal, view));
    float translucence = 0.45 + 0.75 * blade;
    float shimmer = 0.86 + 0.14 * noiseG(vec2(hue * 9.0 + along * 1.4,
            around * 1.4 + t * 0.15));

    float brightness = coverage * translucence * shimmer * intensity * 0.85;
    vec3 rgb = glass * brightness + vec3(1.0, 0.98, 0.92) * veins * intensity * 0.30;
    // Hardening drains the colour toward crystal, matching the wood.
    rgb = mix(rgb, vec3(dot(rgb, vec3(0.33))) * vec3(0.92, 0.97, 1.0), hard * 0.55);

    float alpha = clamp(brightness + veins * 0.35, 0.0, 1.0) * fade;
    if (alpha < 0.0015) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
