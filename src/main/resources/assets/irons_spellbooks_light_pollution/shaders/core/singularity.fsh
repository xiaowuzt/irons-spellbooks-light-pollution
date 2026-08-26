#version 150

// The accretion glow around Singularity's core.
//
// The shockwaves used to live here too, as analytic emissive shells. They are
// screen-space refraction now (see SINGULARITY_LENS in the post-process include),
// because bending the view is what a singularity does to a scene, and a glowing
// shell only ever read as a bubble.
//
// Everything here is emissive: the blend is additive, so black is simply absent
// and alpha does not occlude. The genuinely dark middle comes from the solid,
// depth-writing body drawn by singularity_core, not from this.
//
// Vertex colour: r = progress, b = intensity, a = fade.
// UV0.x carries the sphere radius in blocks; UV0.y is unused.

uniform vec4 ColorModulator;
uniform vec3 SphereCenter;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 vViewPos;

out vec4 fragColor;

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

void main() {
    float progress  = vertexColor.r;
    float intensity = vertexColor.b * 4.0;
    float fade      = vertexColor.a;
    float radius    = max(uvCoord.x, 0.001);

    if (fade < 0.002 || intensity < 0.002) {
        discard;
    }

    // Ray/sphere intersection in view space; the eye is the origin. Same trick as
    // the analytic star: the hull only has to be large enough to contain the
    // sphere, and the fragment stage finds the real surface.
    vec3 centre = SphereCenter;
    vec3 dir = normalize(vViewPos);
    float b = dot(-centre, dir);
    float c = dot(centre, centre) - radius * radius;
    float disc = b * b - c;

    if (disc < 0.0) {
        // Outside the body: infalling light wound around it.
        float miss = sqrt(max(c - b * b, 0.0));
        float halo = radius / max(miss, 0.0001);
        float glow = pow(clamp(halo, 0.0, 1.0), 2.4);
        float swirl = 0.62 + 0.38 * noise3(dir * 11.0 + vec3(progress * 6.0));
        vec3 haloRgb = mix(vec3(0.46, 0.22, 0.92), vec3(1.0, 0.88, 1.0),
                clamp(glow * 1.5, 0.0, 1.0)) * glow * swirl * intensity * 0.7;
        float haloAlpha = clamp(glow * swirl, 0.0, 1.0) * fade * 0.9;
        if (haloAlpha < 0.0015) {
            discard;
        }
        fragColor = vec4(haloRgb, haloAlpha) * ColorModulator;
        return;
    }

    float tHit = -b - sqrt(disc);
    vec3 surface = dir * max(tHit, 0.0);
    vec3 normal = normalize(surface - centre);
    float cosAngle = clamp(dot(normal, -dir), 0.0, 1.0);

    // Limb brightening rather than limb darkening: what is being seen is material
    // falling in around the outside, so the edge is where the line of sight passes
    // through the most of it.
    float limb = 0.35 + 0.65 * pow(1.0 - cosAngle, 1.6);

    // Bands of infalling material, sheared so they wrap the body instead of
    // sitting on it like paint.
    float shear = noise3(normal * 4.0 + vec3(0.0, progress * 7.0, 0.0));
    float bands = 0.5 + 0.5 * sin(normal.y * 9.0 + shear * 5.0 + progress * 14.0);
    float grain = 0.6 + 0.4 * noise3(normal * 15.0 + vec3(progress * 9.0));

    // A dark pupil dead centre, thinning the glow where the solid body shows
    // through. Only a detail: under additive blending anything dark is absent, so
    // the real hole is the body, not this.
    float pupil = smoothstep(0.42, 0.0, 1.0 - cosAngle);

    float body = limb * (0.45 + bands * 0.75) * grain * (1.0 - pupil * 0.85);
    vec3 rgb = mix(vec3(0.54, 0.20, 1.0), vec3(1.0, 0.93, 1.0),
            clamp(limb * 1.2, 0.0, 1.0)) * body * intensity;

    // A hard bright ring right at the silhouette.
    float ring = pow(1.0 - cosAngle, 7.0) * 2.2;
    rgb += vec3(1.0, 0.96, 1.0) * ring * intensity * 0.5;

    float alpha = clamp(body + ring, 0.0, 1.0) * fade;
    if (alpha < 0.0015) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
