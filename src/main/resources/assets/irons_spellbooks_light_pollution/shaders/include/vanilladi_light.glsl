// Forge 1.20.1 GLSL 150 port of VanillaDI's shade pass.
// The light records use the same 36-header/11-texel format as the upstream
// resource pack.  The only bridge-specific part is the world-space rolling
// voxel origin supplied by Java; the sampling, reservoir selection, finite
// sphere emitter and two shadow paths follow shade.fsh.

uniform sampler2D DepthSampler;
uniform sampler2D NormalSampler;
uniform sampler2D VoxelSampler;
uniform sampler2D VoxelLodSampler;
uniform sampler2D LightDataSampler;

uniform vec4 TimePack;
uniform vec4 LightDataParams;
uniform vec4 CameraWorldPos;
uniform vec4 VoxelOrigin;
uniform mat4 ProjectionMat;
uniform mat4 InverseProjectionMat;
uniform mat4 InverseViewMat;

in vec2 vUv;
out vec4 fragColor;

const float PI = 3.14159265358979323846;

int decodeInt(vec3 encoded) {
    ivec3 bytes = ivec3(floor(encoded * 255.0 + 0.5));
    int sign = bytes.b >= 128 ? -1 : 1;
    int magnitude = bytes.r + bytes.g * 256
            + (bytes.b - 64 + sign * 64) * 256 * 256;
    return sign * magnitude;
}

float decodeFloat(vec3 encoded) {
    return float(decodeInt(encoded)) / 40000.0;
}

float decodeFloat1024(vec3 encoded) {
    return float(decodeInt(encoded)) / 1024.0;
}

float hash(vec3 value) {
    return fract(sin(dot(value, vec3(12.9898, 78.233, 37.719)))
            * 43758.5453123);
}

float random(inout vec3 seed) {
    seed += vec3(1.0, 1.37, 2.11);
    return hash(seed);
}

vec4 encodeHdr(vec3 color) {
    float maximum = min(max(color.r, max(color.g, color.b)), 255.0);
    if (maximum <= 0.0) return vec4(0.0);
    if (maximum < 1.0) return vec4(max(color, vec3(0.0)), 1.0);
    return vec4(max(color, vec3(0.0)) / maximum, 1.0 / maximum);
}

vec3 viewPositionFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) <= 0.000001) return vec3(0.0);
    eye.xyz /= eye.w;
    // Lighting coordinates face +Z, while OpenGL eye coordinates face -Z.
    return vec3(eye.x, eye.y, -eye.z);
}

vec3 worldPositionFromView(vec3 viewPosition) {
    return (InverseViewMat * vec4(viewPosition.x, viewPosition.y,
            -viewPosition.z, 0.0)).xyz + CameraWorldPos.xyz;
}

bool projectToScreen(vec3 viewPosition, out vec2 uv, out float depth) {
    vec4 clip = ProjectionMat * vec4(viewPosition.x, viewPosition.y,
            -viewPosition.z, 1.0);
    if (clip.w <= 0.000001) {
        uv = vec2(0.0);
        depth = 1.0;
        return false;
    }
    vec3 ndc = clip.xyz / clip.w;
    uv = ndc.xy * 0.5 + 0.5;
    depth = ndc.z * 0.5 + 0.5;
    return all(greaterThanEqual(uv, vec2(0.0)))
            && all(lessThanEqual(uv, vec2(1.0)));
}

bool blockInBounds(ivec3 block) {
    return all(greaterThanEqual(block, ivec3(0)))
            && all(lessThan(block, ivec3(64)));
}

int blockId(ivec3 block) {
    return block.x + block.y * 64 + block.z * 4096;
}

bool blockOccupied(ivec3 block) {
    if (!blockInBounds(block)) return false;
    int id = blockId(block);
    vec4 lod = texelFetch(VoxelLodSampler,
            ivec2(id % 128, id / 128), 0);
    return any(greaterThan(lod, vec4(0.0)));
}

bool subVoxelOccupied(ivec3 block, ivec3 subVoxel) {
    if (!blockInBounds(block)
            || any(lessThan(subVoxel, ivec3(0)))
            || any(greaterThanEqual(subVoxel, ivec3(8)))) {
        return false;
    }
    int id = blockId(block);
    int sliceIndex = subVoxel.z * 2 + subVoxel.y / 4;
    vec4 packedTexel = texelFetch(VoxelSampler,
            ivec2((id % 128) * 16 + sliceIndex, id / 128), 0);
    int row = subVoxel.y % 4;
    float packedValue = row == 0 ? packedTexel.r : row == 1 ? packedTexel.g
            : row == 2 ? packedTexel.b : packedTexel.a;
    int bits = int(floor(packedValue * 255.0 + 0.5));
    return (bits & (1 << subVoxel.x)) != 0;
}

bool voxelPointOccupied(vec3 worldPosition) {
    if (VoxelOrigin.w < 0.5) return false;
    vec3 local = worldPosition - VoxelOrigin.xyz;
    if (any(lessThan(local, vec3(0.0)))
            || any(greaterThanEqual(local, vec3(64.0)))) return false;
    ivec3 block = ivec3(floor(local));
    if (!blockOccupied(block)) return false;
    ivec3 subVoxel = ivec3(floor(fract(local) * 8.0));
    return subVoxelOccupied(block, subVoxel);
}

bool traceSubVoxels(ivec3 block, vec3 entry, vec3 direction,
        float blockDistance) {
    if (blockDistance <= 0.00001) return false;

    vec3 position = clamp((entry - vec3(block)) * 8.0,
            vec3(0.0001), vec3(7.9999));
    ivec3 voxel = ivec3(floor(position));
    ivec3 stepDirection = ivec3(sign(direction));
    vec3 invDirection = 1.0 / max(abs(direction) * 8.0,
            vec3(0.00001));
    vec3 side = vec3(
            direction.x >= 0.0 ? (float(voxel.x) + 1.0 - position.x)
                    * invDirection.x : (position.x - float(voxel.x))
                    * invDirection.x,
            direction.y >= 0.0 ? (float(voxel.y) + 1.0 - position.y)
                    * invDirection.y : (position.y - float(voxel.y))
                    * invDirection.y,
            direction.z >= 0.0 ? (float(voxel.z) + 1.0 - position.z)
                    * invDirection.z : (position.z - float(voxel.z))
                    * invDirection.z);

    for (int i = 0; i < 24; i++) {
        if (subVoxelOccupied(block, voxel)) return true;
        float next = min(side.x, min(side.y, side.z));
        if (next >= blockDistance - 0.0001) return false;
        if (side.x <= next + 0.000001) {
            voxel.x += stepDirection.x;
            side.x += invDirection.x;
        }
        if (side.y <= next + 0.000001) {
            voxel.y += stepDirection.y;
            side.y += invDirection.y;
        }
        if (side.z <= next + 0.000001) {
            voxel.z += stepDirection.z;
            side.z += invDirection.z;
        }
        if (any(lessThan(voxel, ivec3(0)))
                || any(greaterThanEqual(voxel, ivec3(8)))) return false;
    }
    return false;
}

float traceVoxels(vec3 worldOrigin, vec3 direction, float maxDistance) {
    if (VoxelOrigin.w < 0.5) return -1.0;
    vec3 origin = worldOrigin - VoxelOrigin.xyz;
    vec3 cell = floor(origin);
    if (any(lessThan(cell, vec3(0.0)))
            || any(greaterThanEqual(cell, vec3(64.0)))) return -1.0;

    vec3 safeDirection = vec3(
            abs(direction.x) < 0.00001 ? (direction.x < 0.0 ? -0.00001 : 0.00001) : direction.x,
            abs(direction.y) < 0.00001 ? (direction.y < 0.0 ? -0.00001 : 0.00001) : direction.y,
            abs(direction.z) < 0.00001 ? (direction.z < 0.0 ? -0.00001 : 0.00001) : direction.z);
    ivec3 stepDirection = ivec3(sign(safeDirection));
    vec3 reciprocal = 1.0 / abs(safeDirection);
    vec3 fraction = origin - cell;
    vec3 side = vec3(
            safeDirection.x >= 0.0 ? (1.0 - fraction.x) * reciprocal.x : fraction.x * reciprocal.x,
            safeDirection.y >= 0.0 ? (1.0 - fraction.y) * reciprocal.y : fraction.y * reciprocal.y,
            safeDirection.z >= 0.0 ? (1.0 - fraction.z) * reciprocal.z : fraction.z * reciprocal.z);
    ivec3 block = ivec3(cell);
    float start = 0.0;

    for (int i = 0; i < 192; i++) {
        if (!blockInBounds(block) || start >= maxDistance) return -1.0;
        float exit = min(side.x, min(side.y, side.z));
        float length = min(exit, maxDistance) - start;
        if (blockOccupied(block) && length > 0.0001) {
            vec3 entry = origin + safeDirection
                    * (start + min(0.0005, length * 0.5));
            if (traceSubVoxels(block, entry, safeDirection,
                    length - min(0.0005, length * 0.5))) return start;
        }
        if (exit >= maxDistance - 0.0001) return -1.0;
        if (side.x <= exit + 0.000001) {
            block.x += stepDirection.x;
            side.x += reciprocal.x;
        }
        if (side.y <= exit + 0.000001) {
            block.y += stepDirection.y;
            side.y += reciprocal.y;
        }
        if (side.z <= exit + 0.000001) {
            block.z += stepDirection.z;
            side.z += reciprocal.z;
        }
        start = exit;
    }
    return -1.0;
}

bool traceScreenSpaceRay(vec3 origin, float depth, vec3 direction,
        float maxRayDistance, inout vec3 seed) {
    const int samples = 25;
    vec3 screenWorld = origin + direction * 0.01 * length(origin);
    float stepSize = 1.0 / 50.0;
    screenWorld += direction * random(seed) * stepSize;
    for (int i = 0; i < samples; i++) {
        screenWorld += direction * stepSize;
        vec2 uv;
        float expectedDepth;
        if (!projectToScreen(screenWorld, uv, expectedDepth)) break;
        float observedDepth = texture(DepthSampler, uv).r;
        float delta = expectedDepth - observedDepth;
        if (observedDepth != 1.0 && delta > 0.0
                && delta < 0.02 * (1.0 - depth)) {
            // Screen-space fallback is for entities absent from the voxel mask.
            // A fence/trapdoor hit at an empty subvoxel is an opening, not a
            // solid blocker.
            vec3 blockerWorld = worldPositionFromView(
                    viewPositionFromDepth(uv, observedDepth));
            if (!voxelPointOccupied(blockerWorld)) return true;
        }
    }
    return false;
}

vec4 lightTexel(int index) {
    int width = max(int(LightDataParams.x + 0.5), 1);
    return texelFetch(LightDataSampler,
            ivec2(index % width, index / width), 0);
}

int lightCount() {
    return max(int(floor(LightDataParams.z + 0.5)), 0);
}

struct LightSample {
    vec3 position;
    vec3 normal;
    vec3 direction;
    vec3 radiance;
    float distance;
    float influenceRadius;
};

vec3 randomPointOnSphere(inout vec3 seed) {
    float a = random(seed) * 2.0 * PI;
    float b = random(seed) * 2.0 - 1.0;
    float s = sqrt(max(1.0 - b * b, 0.0));
    return vec3(s * cos(a), s * sin(a), b);
}

bool samplePointOnSphere(vec3 fragPosition, vec3 position,
        inout vec3 point, inout vec3 normal, out float area,
        inout vec3 seed) {
    const float radius = 0.5;
    vec3 towardSurface = normalize(fragPosition - position);
    normal = randomPointOnSphere(seed);
    normal *= sign(dot(normal, towardSurface));
    point = position + normal * radius;
    area = 2.0 * PI * radius * radius;
    return true;
}

LightSample sampleLight(int index, vec3 fragPosition, vec3 normal,
        inout vec3 seed) {
    int base = index * 11 + 36;
    vec3 position = vec3(
            decodeFloat1024(lightTexel(base + 0).rgb),
            decodeFloat1024(lightTexel(base + 1).rgb),
            decodeFloat1024(lightTexel(base + 2).rgb));
    vec3 tangent = normalize(vec3(
            decodeFloat(lightTexel(base + 3).rgb),
            decodeFloat(lightTexel(base + 4).rgb),
            decodeFloat(lightTexel(base + 5).rgb)));
    vec3 bitangent = normalize(vec3(
            decodeFloat(lightTexel(base + 6).rgb),
            decodeFloat(lightTexel(base + 7).rgb),
            decodeFloat(lightTexel(base + 8).rgb)));
    // Keep the record decode identical to VanillaDI.  Tangent/bitangent are
    // present for custom light types; migrated spell lights are spheres.
    vec3 sourceColor = lightTexel(base + 9).rgb;
    vec3 properties = lightTexel(base + 10).rgb;
    float intensity = properties.r * 100.0;
    int type = int(floor(properties.g * 255.0 + 0.5));
    float influenceRadius = max(properties.b * 64.0, 1.0);
    mat3 tbn = mat3(tangent, bitangent,
            normalize(cross(tangent, bitangent)));

    vec3 point = position;
    vec3 sourceNormal = tbn[2];
    float area = 0.0;
    bool valid = false;
    if (type == 1) {
        valid = samplePointOnSphere(fragPosition, position, point,
                sourceNormal, area, seed);
    }

    vec3 direction = normalize(point - fragPosition);
    float distanceToLight = length(point - fragPosition);
    float diffuse = max(dot(normal, direction), 0.0);
    float cosine = dot(direction, sourceNormal);
    float attenuation = float(valid) * (2.0 * PI * intensity * diffuse)
            * (abs(cosine * area) / max(distanceToLight * distanceToLight,
                    0.0001));

    LightSample sample;
    sample.position = point;
    sample.normal = sourceNormal;
    sample.direction = direction;
    sample.radiance = sourceColor * attenuation;
    sample.distance = distanceToLight;
    sample.influenceRadius = influenceRadius;
    return sample;
}

vec3 shade(vec3 fragPosition, float depth, vec3 normal, inout vec3 seed) {
    int count = lightCount();
    if (count <= 0) return vec3(0.0);
    const int selections = 16;
    float pdf = 1.0 / float(count);
    float weightSum = 0.0;
    float sampleCount = 0.0;
    LightSample survived;
    survived.radiance = vec3(0.0);

    for (int i = 0; i < selections; i++) {
        int index = min(int(floor(random(seed) * float(count))), count - 1);
        LightSample sample = sampleLight(index, fragPosition, normal, seed);
        float centerDistance = length(sample.position - fragPosition);
        float rangeFade = 1.0 - smoothstep(sample.influenceRadius * 0.60,
                sample.influenceRadius, centerDistance);
        sample.radiance *= rangeFade;
        float weight = length(sample.radiance) / max(pdf, 0.0001);
        weightSum += weight;
        sampleCount += 1.0;
        if (random(seed) < weight / max(weightSum, 0.0001)) {
            survived = sample;
        }
    }

    if (length(survived.radiance) <= 0.000001 || sampleCount <= 0.0) {
        return vec3(0.0);
    }

    vec3 worldPosition = worldPositionFromView(fragPosition);
    float minDistance = 0.015 * max(length(fragPosition), 1.0);
    vec3 traversalOrigin = worldPosition + survived.direction * minDistance;
    float traceDistance = traceVoxels(traversalOrigin,
            survived.direction, survived.distance);
    bool shadowed = traceDistance >= 0.0;
    if (!shadowed) {
        shadowed = traceScreenSpaceRay(fragPosition, depth,
                survived.direction, survived.distance, seed);
    }
    if (shadowed) return vec3(0.0);

    float p = length(survived.radiance);
    float reservoirWeight = p > 0.0
            ? (1.0 / p) * weightSum / sampleCount : 0.0;
    return survived.radiance * reservoirWeight;
}

void main() {
    float depth = texture(DepthSampler, vUv).r;
    if (depth >= 0.999) {
        fragColor = encodeHdr(vec3(0.0));
        return;
    }
    vec3 viewPosition = viewPositionFromDepth(vUv, depth);
    vec3 normal = normalize(texture(NormalSampler, vUv).rgb * 2.0 - 1.0);
    vec3 seed = vec3(vUv, floor(TimePack.y / 3.0));
    fragColor = encodeHdr(shade(viewPosition, depth, normal, seed));
}
