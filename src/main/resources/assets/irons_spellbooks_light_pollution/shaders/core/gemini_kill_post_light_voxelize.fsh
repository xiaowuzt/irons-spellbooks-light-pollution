#version 150

// Ported from VanillaDI's rolling depth/normal voxelizer. The cache layout
// matches the fine DDA in kill_effect_post_common.glsl: 64^3 blocks, 16 RGBA
// texels per block, and eight X subvoxels packed into each channel.
uniform sampler2D DepthSampler;
uniform sampler2D NormalSampler;
uniform sampler2D VoxelCacheSampler;
// Model-baked occupancy: one id per block from the volume, indexing that block
// state's 8^3 mask in the atlas. See SpellLightBlockMaskAtlas.
uniform sampler2D StateVolumeSampler;
uniform sampler2D ModelMaskSampler;

uniform vec4 CameraWorldPos;
uniform vec4 VoxelOrigin;
uniform vec4 PreviousVoxelOrigin;
// xyz = the state volume origin wrapped into its toroidal index space,
// w = 1 when the volume and atlas are both usable.
uniform vec4 StateVolumeWrap;
// x = collect this frame, y = history is valid.
uniform vec4 CacheParams;
uniform mat4 ProjectionMat;
uniform mat4 ViewMat;
uniform mat4 InverseProjectionMat;
uniform mat4 InverseViewMat;

in vec2 vUv;
out vec4 fragColor;

bool slProjectWorld(vec3 worldPosition, out vec2 uv, out float projectedDepth) {
    vec4 eye = ViewMat * vec4(worldPosition - CameraWorldPos.xyz, 1.0);
    vec4 clip = ProjectionMat * eye;
    if (clip.w <= 0.000001) {
        uv = vec2(0.0);
        projectedDepth = 1.0;
        return false;
    }

    vec3 ndc = clip.xyz / clip.w;
    uv = ndc.xy * 0.5 + 0.5;
    projectedDepth = ndc.z * 0.5 + 0.5;
    return all(greaterThanEqual(uv, vec2(0.0)))
            && all(lessThanEqual(uv, vec2(1.0)))
            && projectedDepth >= 0.0 && projectedDepth <= 1.0;
}

vec3 slWorldFromDepth(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 eye = InverseProjectionMat * clip;
    if (abs(eye.w) <= 0.000001) {
        return CameraWorldPos.xyz;
    }
    eye.xyz /= eye.w;
    // eye is OpenGL view space (-Z forward). InverseViewMat contains only
    // rotation, so absolute camera translation is applied exactly once here.
    return (InverseViewMat * vec4(eye.xyz, 0.0)).xyz + CameraWorldPos.xyz;
}

// Same three-state contract as VanillaDI: invalid samples preserve a history
// bit, force samples clear stale occupancy, and valid present samples set it.
//
// The cutout holes in a trapdoor, fence or iron bars are texture alpha, not
// model geometry, so they exist in the depth buffer (discarded texels write no
// depth) and nowhere in BlockState.getShape(). Reading occupancy back out of
// depth is what preserves them: a 3/16 trapdoor hole leaves one or two fully
// empty subvoxels at this cache's 1/8-block resolution.
bool collectVoxel(vec3 worldPosition, out bool valid, out bool force) {
    valid = false;
    force = false;

    vec2 uv;
    float expectedDepth;
    if (!slProjectWorld(worldPosition, uv, expectedDepth)) {
        return false;
    }

    // The single force test lives AFTER the normal nudge, exactly as upstream
    // does it, and that placement is load-bearing. The nudge lifts the sample
    // from 1/16 inside the geometry onto the visible surface; testing the raw
    // centre instead is fragile at oblique views, because a centre buried in a
    // 3/16-thick plate readily projects past the plate's thin screen silhouette
    // onto the background, trips "farther surface", and force-clears a bar that
    // is really there. That erodes chunks out of the occluder as the camera
    // swings past roughly 27 degrees off the plate normal.
    //
    // Removing it costs nothing: when the normal is invalid or sky the nudge is
    // skipped, so the test below still evaluates the unnudged centre and still
    // clears the stale bit left behind by a mined block.
    vec4 encodedNormal = texture(NormalSampler, uv);
    vec3 normalLightingView = encodedNormal.rgb * 2.0 - 1.0;
    vec3 nudgedWorld = worldPosition;
    if (encodedNormal.a > 0.5 && length(normalLightingView) > 0.00001) {
        // light_normals.fsh stores a +Z-forward lighting-view normal. Convert
        // it through raw OpenGL eye coordinates before offsetting in world
        // space.
        vec3 normalWorld = normalize((InverseViewMat
                * vec4(normalLightingView.x, normalLightingView.y,
                        -normalLightingView.z, 0.0)).xyz);
        nudgedWorld += normalWorld * (1.0 / 16.0);
    }

    if (!slProjectWorld(nudgedWorld, uv, expectedDepth)) {
        return false;
    }

    float depth = texture(DepthSampler, uv).r;
    if (depth > expectedDepth + 0.00001) {
        valid = true;
        force = true;
        return false;
    }
    if (depth >= 0.999999) {
        return false;
    }

    vec3 backProjected = slWorldFromDepth(uv, depth);
    valid = true;
    // Upstream's tolerance. `depth` is window depth, so this is ~0.2 blocks for
    // anything not right against the near plane -- roughly 1.6 subvoxels.
    //
    // A tighter fixed value was tried here to stop holes filling in, but that
    // diagnosis was confounded by the sliding grid anchor (see
    // LRN-20260823-003). Hole clearing is driven by the force test above and is
    // independent of this tolerance, whereas back-projection error along the
    // view ray grows at grazing angles -- so tightening it only stops bars from
    // ever being written, which erodes chunks out of the shadow at oblique
    // views. If holes ever do start filling in, this is the knob.
    return distance(backProjected, nudgedWorld) < depth * 0.2;
}

void collectRow(inout uint row, ivec3 blockCoord, int voxelRow, int voxelDepth) {
    for (int subX = 0; subX < 8; subX++) {
        vec3 worldPosition = VoxelOrigin.xyz + vec3(blockCoord)
                + (vec3(subX, voxelRow, voxelDepth) + 0.5) / 8.0;
        bool valid;
        bool force;
        bool present = collectVoxel(worldPosition, valid, force);
        uint bit = 1u << uint(subX);
        if (force) {
            row &= ~bit;
        }
        if (valid) {
            row |= present ? bit : 0u;
        }
    }
}

bool slBlockInBounds(ivec3 blockCoord) {
    return all(greaterThanEqual(blockCoord, ivec3(0)))
            && all(lessThan(blockCoord, ivec3(64)));
}

uint slDecodeRow(float channel) {
    return uint(floor(channel * 255.0 + 0.5));
}

// The volume shares the occupancy cache's anchor, so a cell coordinate maps
// straight onto it once the origin's toroidal wrap is added. 0 means "no baked
// mask", which leaves the depth path in charge of that cell.
int slBakedStateId(ivec3 blockCoord) {
    if (StateVolumeWrap.w < 0.5) {
        return 0;
    }
    ivec3 wrapped = (ivec3(StateVolumeWrap.xyz + 0.5) + blockCoord) & 63;
    int index = wrapped.x + wrapped.y * 64 + wrapped.z * 4096;
    vec4 encoded = texelFetch(StateVolumeSampler,
            ivec2(index & 511, index >> 9), 0);
    return int(floor(encoded.r * 255.0 + 0.5))
            + int(floor(encoded.g * 255.0 + 0.5)) * 256;
}

void main() {
    ivec2 coord = ivec2(gl_FragCoord.xy);
    int blockId = coord.y * 128 + coord.x / 16;
    ivec3 blockCoord = ivec3(blockId % 64, (blockId / 64) % 64,
            blockId / 4096);
    int slice = coord.x % 16;
    int voxelDepth = slice / 2;
    int voxelRowOffset = (slice % 2) * 4;

    // A block with a baked model mask needs no depth sampling at all: the mask
    // is exact, includes the cutout holes at full texture precision, and is the
    // same from every camera angle. This is what stops shadows losing chunks
    // when the occluder is seen close to edge-on.
    int bakedState = slBakedStateId(blockCoord);
    if (bakedState > 0) {
        fragColor = texelFetch(ModelMaskSampler,
                ivec2((bakedState % 128) * 16 + slice, bakedState / 128), 0);
        return;
    }

    uint row0 = 0u;
    uint row1 = 0u;
    uint row2 = 0u;
    uint row3 = 0u;

    bool hasHistory = CacheParams.y > 0.5 && PreviousVoxelOrigin.w > 0.5;
    if (hasHistory) {
        ivec3 originDelta = ivec3(floor(VoxelOrigin.xyz)
                - floor(PreviousVoxelOrigin.xyz));
        ivec3 previousBlock = blockCoord + originDelta;
        if (slBlockInBounds(previousBlock)) {
            int previousBlockId = previousBlock.x + previousBlock.y * 64
                    + previousBlock.z * 4096;
            vec4 cached = texelFetch(VoxelCacheSampler,
                    ivec2((previousBlockId % 128) * 16 + slice,
                            previousBlockId / 128), 0);
            row0 = slDecodeRow(cached.r);
            row1 = slDecodeRow(cached.g);
            row2 = slDecodeRow(cached.b);
            row3 = slDecodeRow(cached.a);
        }
    }

    if (CacheParams.x > 0.5) {
        collectRow(row0, blockCoord, voxelRowOffset, voxelDepth);
        collectRow(row1, blockCoord, voxelRowOffset + 1, voxelDepth);
        collectRow(row2, blockCoord, voxelRowOffset + 2, voxelDepth);
        collectRow(row3, blockCoord, voxelRowOffset + 3, voxelDepth);
    }

    fragColor = vec4(float(row0), float(row1), float(row2), float(row3)) / 255.0;
}
