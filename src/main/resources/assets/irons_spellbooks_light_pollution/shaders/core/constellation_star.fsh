#version 150

// Analytic star. The hull is a unit sphere scaled to the star's radius; the
// surface is found by intersecting the eye ray with that sphere, so the body has
// a hard silhouette like a real disc rather than the soft blob a volumetric
// march gives. Everything outside the disc becomes corona.
//
// Vertex colour carries the envelope: r = time seed, g = colour temperature,
// b = brightness, a = overall fade. UV0.x is the radius in view-space units.

uniform vec4 ColorModulator;
// Multiplies the final colour. Lets one program serve stars that are meant to be
// visibly different colours without duplicating the whole shader.
uniform vec4 TintColor;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 vViewPos;
in vec3 vSphereCenter;

out vec4 fragColor;

// Cheap 3D value noise; enough for granulation at this scale.
float hash13(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i + vec3(0, 0, 0)), hash13(i + vec3(1, 0, 0)), f.x),
            mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
            mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

float granulation(vec3 dir, float t) {
    float n = noise3(dir * 7.0 + vec3(0.0, t * 0.35, 0.0)) * 0.6;
    n += noise3(dir * 17.0 - vec3(t * 0.22, 0.0, t * 0.17)) * 0.3;
    n += noise3(dir * 41.0 + vec3(t * 0.5)) * 0.1;
    return n;
}

void main() {
    float seed       = vertexColor.r * 40.0;
    float temperature = vertexColor.g;
    float brightness = vertexColor.b * 3.0;
    float fade       = vertexColor.a;
    float radius     = max(uvCoord.x, 0.001);

    if (fade < 0.002 || brightness < 0.002) {
        discard;
    }

    vec3 centre = vSphereCenter;
    vec3 dir = normalize(vViewPos);

    // Ray/sphere intersection in view space; the eye is the origin.
    float b = dot(-centre, dir);
    float c = dot(centre, centre) - radius * radius;
    float disc = b * b - c;

    // Hot core to cool rim, driven by temperature.
    vec3 coreColour = mix(vec3(1.00, 0.72, 0.32), vec3(1.00, 0.98, 0.94), temperature);
    vec3 edgeColour = mix(vec3(0.95, 0.34, 0.10), vec3(1.00, 0.78, 0.45), temperature);

    if (disc < 0.0) {
        // Corona only. Distance from the ray to the centre sets the falloff.
        float miss = sqrt(max(c - b * b, 0.0));
        float halo = radius / max(miss, 0.0001);
        float glow = pow(clamp(halo, 0.0, 1.0), 2.6);

        // A smooth radial falloff is exactly what makes a star read as a lamp.
        // Real coronae are structured, so break it into streamers around the
        // limb and let them drift.
        vec3 offAxis = normalize(vViewPos * dot(centre, centre)
                - centre * dot(vViewPos, centre) + vec3(1e-5));
        float around = atan(offAxis.y, offAxis.x);
        float streamers = 0.62
                + 0.38 * noise3(vec3(around * 2.4, seed * 0.35, 1.7));
        streamers *= 0.75 + 0.45 * noise3(vec3(around * 7.0, seed * 0.6, 4.3));

        vec3 rgb = edgeColour * glow * streamers * brightness * 0.5;
        // Prominences: short arcs standing off the limb, brightest just outside
        // the photosphere.
        float limbBand = exp(-pow((halo - 0.86) / 0.1, 2.0));
        rgb += coreColour * limbBand * streamers * brightness * 0.55;

        float alpha = clamp(glow * streamers, 0.0, 1.0) * fade * 0.8;
        fragColor = vec4(rgb, alpha) * ColorModulator * TintColor;
        if (fragColor.a < 0.0008) {
            discard;
        }
        return;
    }

    float tHit = -b - sqrt(disc);
    if (tHit < 0.0) {
        tHit = 0.0;
    }
    vec3 surface = dir * tHit;
    vec3 normal = normalize(surface - centre);

    // Limb darkening: the classic 1 - u(1 - cos) law makes the disc read as a
    // sphere instead of a flat sticker. Driven hard, because the additive blend
    // saturates the disc to flat white otherwise -- which is what made the star
    // look like a ball of light with no surface at all.
    float cosAngle = clamp(dot(normal, -dir), 0.0, 1.0);
    float limb = 1.0 - 0.88 * (1.0 - cosAngle);

    float cells = granulation(normal, seed);
    // Centre the cells around zero so they can darken as well as brighten. Only
    // ever adding to a value that already clips meant the granulation was
    // invisible on screen.
    float contrast = (cells - 0.5) * 2.0;
    float surfaceMix = clamp(0.5 + contrast * 0.9, 0.0, 1.0);
    vec3 surfaceColour = mix(edgeColour, coreColour, surfaceMix);

    // Convective mottling plus a slow overall pulse.
    float mottle = clamp(0.72 + contrast * 0.55, 0.25, 1.5);
    float pulse = 0.96 + 0.04 * sin(seed * 1.7);

    // Gain kept below the clipping point so the structure above survives the
    // additive blend instead of being crushed to white.
    vec3 rgb = surfaceColour * brightness * 0.62 * limb * mottle * pulse;

    // A thin bright ring right at the limb sells the edge of the photosphere.
    float rim = pow(1.0 - cosAngle, 3.0);
    rgb += coreColour * rim * brightness * 0.5;

    fragColor = vec4(rgb, fade) * ColorModulator * TintColor;
    if (fragColor.a < 0.0008) {
        discard;
    }
}
