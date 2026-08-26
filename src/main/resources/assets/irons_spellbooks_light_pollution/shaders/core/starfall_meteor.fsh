#version 150

// The three pieces of a meteor a light source alone cannot give: the shock rings
// it leaves in the air, the incandescent trail behind it, and the shock that
// spreads along the ground where it lands. All analytic, so a whole meteor shower
// is a couple of draw calls.
//
// Vertex colour: r = progress, g = mode selector, b = intensity, a = fade.
// Mode: < 0.34 trail, < 0.67 shock ring, else ground shock.
// UV0.x runs from the body outward, UV0.y crosses the ribbon.

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

/** Incandescent ramp: white-hot at the body, deep red where it has cooled. */
vec3 emberColour(float t) {
    vec3 white = vec3(1.00, 0.98, 0.92);
    vec3 gold  = vec3(1.00, 0.82, 0.42);
    vec3 amber = vec3(1.00, 0.48, 0.12);
    vec3 rust  = vec3(0.62, 0.13, 0.02);
    vec3 c = mix(white, gold, smoothstep(0.0, 0.22, t));
    c = mix(c, amber, smoothstep(0.22, 0.58, t));
    return mix(c, rust, smoothstep(0.58, 1.0, t));
}

void main() {
    float progress  = vertexColor.r;
    float mode      = vertexColor.g;
    float intensity = vertexColor.b * 4.0;
    float fade      = vertexColor.a;

    if (fade < 0.002 || intensity < 0.002) {
        discard;
    }

    if (mode < 0.34) {
        // ── Trail ─────────────────────────────────────────────────────
        float along = clamp(uvCoord.x, 0.0, 1.0);
        float across = abs(uvCoord.y - 0.5) * 2.0;

        // The ribbon is a flat quad, so the cross-section has to do the work of
        // making it read as a column of burning air rather than a paper strip.
        float width = mix(0.95, 0.22, along);
        float core = exp(-(across * across) / max(width * width, 0.0004));

        // Burning gas breaks up as it cools, so the tail should not be a clean
        // gradient all the way to the tip.
        float breakUp = hash12(vec2(floor(along * 26.0), progress * 7.0));
        float shred = mix(1.0, 0.45 + breakUp * 0.85, along);

        float lengthFade = exp(-along * 2.35) * shred;
        float brightness = core * lengthFade * intensity;

        vec3 rgb = emberColour(along) * brightness;
        // A hot sheath right behind the body, which is what sells the direction
        // of travel.
        rgb += vec3(1.0, 0.95, 0.85) * exp(-along * 12.0) * core * intensity * 0.7;

        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        fragColor = vec4(rgb, alpha) * ColorModulator;
        if (fragColor.a < 0.0015) {
            discard;
        }
        return;
    }

    if (mode < 0.67) {
        // ── Shock ring ────────────────────────────────────────────────
        // A front expanding outward from the meteor's path, left behind and
        // dying as it goes. UV0.x crosses its thickness, UV0.y runs all the way
        // around it.
        float thickness = clamp(uvCoord.x, 0.0, 1.0);
        float around = uvCoord.y;

        // Thin and hard on the outside, softer inward: the outer edge is the
        // front doing the work.
        float band = exp(-pow((thickness - 0.78) / 0.3, 2.0));
        // A tighter line right on the leading edge gives it a definite edge
        // rather than a soft haze.
        band += exp(-pow((thickness - 0.98) / 0.07, 2.0)) * 1.1;

        // A ring of perfectly even brightness reads as a solid hoop, so vary it
        // gently around the circumference. Low frequency and smooth on purpose:
        // a hard per-step hash broke the circle into visibly separate arcs.
        float wobble = 0.86 + 0.14 * sin(around * 6.28318 * 3.0);
        wobble *= 0.9 + 0.1 * sin(around * 6.28318 * 7.0 + 1.7);

        // Fades as it widens, so it dissolves outward instead of just switching
        // off.
        float brightness = band * wobble * intensity
                * (1.0 - progress) * (1.0 - progress * 0.35);

        // Near-white and slightly cool: this is compressed air, not burning
        // debris, and it must not be confused with the trail.
        vec3 rgb = mix(vec3(1.0, 0.99, 0.96), vec3(0.62, 0.78, 1.0), progress)
                * brightness;
        float alpha = clamp(brightness, 0.0, 1.0) * fade;
        fragColor = vec4(rgb, alpha) * ColorModulator;
        if (fragColor.a < 0.0015) {
            discard;
        }
        return;
    }

    // ── Ground shock ──────────────────────────────────────────────────
    vec2 centred = (uvCoord - 0.5) * 2.0;
    float radial = length(centred);
    if (radial > 1.0) {
        discard;
    }

    // An expanding front rather than a filled disc: the ground is already lit by
    // the impact's own light, so what is missing is the edge travelling outward.
    float front = clamp(progress, 0.0, 1.0);
    float ring = exp(-pow((radial - front) / 0.16, 2.0));
    ring += exp(-pow((radial - front * 0.62) / 0.22, 2.0)) * 0.45;

    float angle = atan(centred.y, centred.x);
    // Debris does not spread evenly, and a perfectly circular ring reads as a
    // decal.
    float tatter = 0.72 + 0.28 * hash12(vec2(floor(angle * 5.5), 3.0));
    ring *= tatter;

    float scorch = (1.0 - smoothstep(0.0, front, radial)) * 0.28;
    float brightness = (ring + scorch) * intensity * (1.0 - front * 0.55);

    vec3 rgb = emberColour(clamp(radial / max(front, 0.02), 0.0, 1.0)) * brightness;
    float alpha = clamp(brightness, 0.0, 1.0) * fade;
    fragColor = vec4(rgb, alpha) * ColorModulator;
    if (fragColor.a < 0.0015) {
        discard;
    }
}
