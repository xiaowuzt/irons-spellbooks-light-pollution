#version 150

// A strand of real tube geometry: a magnetar's field line, a jet, a debris stream, a
// pinwheel's arm, a filament of the Crab.
//
// These were all camera-facing ribbons before, and that is why they read as decals rather
// than as objects. A ribbon has no cross-section, never occludes itself, and degenerates to
// nothing where the curve happens to point at the viewer. This shades a closed tube with a
// real geometric normal instead, so the strand is round, its far side is hidden by its near
// side, and the highlight moves as the camera does.
//
// Vertex contract, from TubeMeshBuilder: UV0.x runs along the tube, UV0.y around it, and the
// colour channels carry r = aux, g = mode, b = intensity, a = alpha.
//
// Mode picks the material. The colour ramp along the strand lives here rather than in the
// vertex colours on purpose: TubeMeshBuilder packs one colour for a whole emit call, so a
// gradient passed that way would have to be chopped into separate calls and every chop would
// show as a step. Driving it from UV0.x makes it continuous by construction.

#define MODE_FIELD    0
#define MODE_JET      1
#define MODE_DEBRIS   2
#define MODE_ARM      3
#define MODE_FILAMENT 4

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 uvCoord;
in vec3 surfaceNormal;
in vec3 toCamera;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash11(float p) {
    return fract(sin(p * 127.1) * 43758.5453123);
}

vec3 ramp(vec3 a, vec3 b, float t) {
    return mix(a, b, clamp(t, 0.0, 1.0));
}

void main() {
    float along = uvCoord.x;
    float around = uvCoord.y;
    float aux = vertexColor.r;
    int mode = int(floor(vertexColor.g * 255.0 + 0.5));
    float intensity = vertexColor.b * 4.0;
    float alpha = vertexColor.a;
    if (intensity < 0.002 || alpha < 0.004) {
        discard;
    }

    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(toCamera);
    float facing = abs(dot(normal, view));

    // A tube lit only by its facing term looks like a plastic pipe. What these are is hot
    // emitting material, so the near side is bright and the rim is brighter still — limb
    // brightening rather than a diffuse falloff.
    float body = 0.35 + 0.65 * facing;
    float limb = pow(1.0 - facing, 2.2) * 0.9;

    vec3 colour;
    float extra = 0.0;

    if (mode == MODE_FIELD) {
        // Violet at rest, driven toward white as the magnetosphere winds up. aux carries how
        // far it has wound.
        colour = ramp(vec3(0.52, 0.38, 1.00), vec3(1.00, 0.86, 0.62), aux);
        // Brightest at the poles, where the loops converge and the field is strongest: a
        // dipole's strength goes as 1/r^3, so the equatorial bulge really is the faint part.
        float poles = pow(abs(cos(along * 3.14159265)), 1.6);
        extra = 0.35 + 0.65 * poles;
        // Packets of charge sliding along the line, offset per strand so they do not pulse in
        // lockstep and read as one flashing object.
        float lane = hash11(floor(aux * 97.0) + 11.0);
        extra *= 0.75 + 0.55 * exp(-pow(fract(along * 3.0 - lane) - 0.5, 2.0) * 30.0);
    } else if (mode == MODE_JET) {
        // aux says which jet: 1 approaching, 0 receding. Relativistic beaming makes the one
        // coming at the viewer bright and blue and the other dim and red, which is the whole
        // reason SS 433's pair looks mismatched.
        colour = ramp(vec3(1.00, 0.42, 0.30), vec3(0.52, 0.74, 1.00), aux);
        extra = mix(0.45, 1.35, aux) * (1.0 - along * 0.55);
        // Discrete blobs. The ejecta leave the disk as bullets, so the strand is strung with
        // knots rather than being a smooth hose.
        float knot = exp(-pow(fract(along * 12.0) - 0.5, 2.0) * 26.0);
        extra *= 0.6 + 0.7 * knot;
    } else if (mode == MODE_DEBRIS) {
        // Hot at the leading tip, which has been in the tidal field longest and sits deepest
        // in the potential, cooling toward the trailing end.
        colour = ramp(vec3(0.80, 0.90, 1.00), vec3(1.00, 0.42, 0.20), along);
        extra = 0.45 + 0.55 * pow(1.0 - along, 1.4);
        float lump = exp(-pow(fract(along * 9.0) - 0.5, 2.0) * 20.0);
        extra *= 0.7 + 0.55 * lump;
    } else if (mode == MODE_ARM) {
        // Dust, warmed near the binary and cold far out.
        colour = ramp(vec3(1.00, 0.78, 0.46), vec3(0.66, 0.30, 0.20), along);
        extra = 0.5 + 0.5 * (1.0 - along);
    } else {
        // Crab filaments: hydrogen and sulphur red, or doubly ionised oxygen green. aux picks.
        colour = ramp(vec3(1.00, 0.34, 0.28), vec3(0.42, 1.00, 0.52), step(0.5, aux));
        // Whole turns, because a filament is a closed loop and along comes back to 0 where it
        // started. A frequency that is not a multiple of TAU leaves a brightness step at the
        // join, which is the seam this geometry exists to remove.
        //
        // The bracket matters too. This read 0.55 + 0.45 * sin(..) * 0.5 + 0.5, which by
        // precedence is 1.05 + 0.225 * sin -- the modulation was half the intended depth and the
        // whole strand sat above one. The base and depth here are picked to keep the mean close
        // to what that accident produced, since the filaments were tuned by eye against it.
        extra = 0.62 + 0.55 * (sin(along * TAU * 3.0 + aux * 20.0) * 0.5 + 0.5);
    }

    float lit = (body + limb) * extra * intensity;
    fragColor = vec4(colour * lit, alpha) * ColorModulator;
}
