#version 150
// Buffer A port of the project owner's supplied R1 source.
// Source and attribution details: docs/black-hole/references/R1-original.txt and THIRD_PARTY_NOTICES.md.
#moj_import <irons_spellbooks_light_pollution:bh_redshift_source.glsl>

uniform sampler2D bhDepthSampler;
uniform sampler2D bhHistorySampler;
uniform sampler2D bhHistoryDepthSampler;
uniform mat4 bhInverseProjectionMatrix;
uniform vec4 bhCenterRadius;
uniform vec4 bhDiskX;
uniform vec4 bhDiskY;
uniform vec4 bhDiskZ;
uniform vec4 bhQuality; // steps, global Doppler control, exposure, debug mode
uniform vec4 bhRadii; // gameplay radius, bounded optical support, geometric mass, reserved
uniform vec4 bhTemporal; // current-frame weight, history valid, reserved, reserved
uniform vec4 bhResolution; // trace target size and inverse size, not the full-size scene

in vec2 texCoord;
out vec4 fragColor;

vec3 viewPosition(vec2 uv, float depth) {
    vec4 p = bhInverseProjectionMatrix * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

vec3 diskLocal(vec3 value) {
    return vec3(dot(value, bhDiskX.xyz), dot(value, bhDiskY.xyz), dot(value, bhDiskZ.xyz));
}

vec3 sourceInitialDirection(vec3 origin, vec3 direction) {
    float distance = length(origin);
    if (distance <= 0.0001) return direction;
    vec3 radial = origin / distance;
    float nearFactor = CubicInterpolate(clamp(1.0 - (0.01 * distance - 1.0) / 4.0, 0.0, 1.0));
    float correction = 1.0 - sqrt(max(1.0 - nearFactor / distance, 1e-16));
    return normalize(direction - radial * dot(radial, direction) * correction);
}

void main() {
    fragColor = vec4(0.0);
    float unit = bhCenterRadius.w;
    if (unit < 0.005 || bhTiming.z <= 0.001) return;

    vec3 origin = diskLocal(-bhCenterRadius.xyz) / unit;
    // The supplied Buffer A jitters the camera ray as well as its first step.
    // Use the trace resolution; depth/history lookups remain at the actual pixel centre.
    vec2 rayUv = texCoord + 0.5 * vec2(
        RandomStep(texCoord, fract(bhTiming.x + 0.5)),
        RandomStep(texCoord, fract(bhTiming.x))) / max(bhResolution.xy, vec2(1.0));
    vec3 direction = diskLocal(normalize(viewPosition(rayUv, 1.0)));
    direction = sourceInitialDirection(origin, normalize(direction));

    float support = bhRadii.y / unit;
    float b = dot(origin, direction);
    float h = b * b - dot(origin, origin) + support * support;
    if (h <= 0.0) return;
    float root = sqrt(h);
    float enter = max(0.0, -b - root);
    float exitDistance = -b + root;
    if (exitDistance <= enter) return;

    float rawDepth = texture(bhDepthSampler, texCoord).r;
    float sceneDistance = rawDepth < 0.999999
        ? length(viewPosition(texCoord, rawDepth)) / unit : 1e20;
    if (sceneDistance <= enter) return;

    int steps = int(clamp(bhQuality.x, 80.0, 240.0));
    float qualityScale = 200.0 / float(steps);
    float outerRadius = bhSourceDisk.y;
    float escapeRadius = 2.5 * outerRadius;
    // Buffer A starts at the camera and uses its adaptive large steps while
    // approaching the hole. The support sphere is only a bounded visibility
    // rejection here; it must not move the geodesic's initial condition.
    vec3 rayPos = origin;
    vec3 lastPos = rayPos;
    float travelled = 0.0;
    float lastRadius = length(rayPos);
    float stepLength = 0.0;
    float taken = 0.0;
    vec4 color = vec4(0.0);

    for (int count = 0; count < 240; ++count) {
        if (count >= steps || travelled >= sceneDistance || color.a > 0.99) break;
        float distance = length(rayPos);
        if (distance > escapeRadius && distance > lastRadius && count > 50) break;
        if (distance < 0.1) {
            color.a = 1.0;
            break;
        }

        vec3 radial = rayPos / max(distance, 0.0001);
        float cosTheta = length(cross(radial, direction));
        float deltaPhiRate = -cosTheta * cosTheta * cosTheta * (1.5 / distance);
        float rayStep = count == 0
            ? RandomStep(texCoord, fract(bhTiming.x)) : 1.0;
        rayStep *= 0.15 + 0.25 * clamp(
            0.5 * (0.5 * distance / max(10.0, outerRadius) - 1.0), 0.0, 1.0);
        if (distance >= 2.0 * outerRadius) {
            rayStep *= distance;
        } else if (distance >= outerRadius) {
            rayStep *= ((2.0 * outerRadius - distance)
                + distance * (distance - outerRadius)) / outerRadius;
        } else {
            rayStep *= min(1.0, distance);
        }
        // RandomStep may legitimately be zero on the first iteration. Buffer A
        // still advances the iteration counter, then takes a normal second step.
        rayStep = min(max(rayStep * qualityScale, 0.0), sceneDistance - travelled);

        // Buffer A evaluates DiskColor at the current ray position, before the
        // geodesic update. Keeping this order preserves the supplied cloud phase
        // and its front-to-back step length.
        color = sourceDiskStep(color, stepLength, rayPos, lastPos,
            direction, length(origin));

        vec3 nextDirection = direction;
        if (cosTheta > 0.000001) {
            float deltaPhi = rayStep / distance * deltaPhiRate;
            float tangentDelta = deltaPhi + deltaPhi * deltaPhi * deltaPhi / 3.0;
            nextDirection = normalize(direction + tangentDelta
                * cross(cross(direction, radial), direction) / cosTheta);
        }
        vec3 nextPosition = rayPos + nextDirection * rayStep;
        lastRadius = distance;
        lastPos = rayPos;
        rayPos = nextPosition;
        direction = nextDirection;
        travelled += rayStep;
        stepLength = rayStep;
        taken = float(count + 1);
    }

    int debug = int(bhQuality.w + 0.5);
    if (debug == 1) {
        fragColor = vec4(bhDiskY.xyz * 0.5 + 0.5, bhTiming.z);
        return;
    }
    if (debug == 2) {
        fragColor = vec4(taken / float(steps), 0.15, 1.0 - taken / float(steps), bhTiming.z);
        return;
    }
    if (debug == 3) {
        fragColor = vec4(sceneDistance < exitDistance ? 1.0 : 0.0, 1.0,
            min(enter / 128.0, 1.0), bhTiming.z);
        return;
    }

    color.rgb *= bhDiskSettings.y * bhTiming.z;
    color.a = clamp(color.a * bhTiming.z, 0.0, 1.0);
    vec4 current = vec4(sourceInverseHdr(color.rgb), color.a);

    float historyDepth = texture(bhHistoryDepthSampler, texCoord).r;
    bool depthStable = abs(historyDepth - rawDepth) <= 0.00002;
    if (bhTemporal.y > 0.5 && depthStable) {
        vec4 previous = texture(bhHistorySampler, texCoord);
        current = mix(previous, current, clamp(bhTemporal.x, 0.0, 1.0));
    }
    fragColor = vec4(clamp(current.rgb, vec3(0.0), vec3(128.0)), clamp(current.a, 0.0, 1.0));
}
