#version 150

// Reduces VanillaDI's 16 texels per block to a single block-occupancy texel.
// The full 8 x 8 x 8 mask remains in VoxelSampler for the fine DDA step.
uniform sampler2D VoxelSampler;

in vec2 vUv;
out vec4 fragColor;

void main() {
    ivec2 blockCoord = ivec2(gl_FragCoord.xy);
    bool occupied = false;

    // SpellLightVoxelGrid stores each 8x8x8 block as 16 RGBA texels at
    // x = blockX * 16 + slice, y = blockY.
    for (int slice = 0; slice < 16; slice++) {
        vec4 voxelBits = texelFetch(VoxelSampler,
                ivec2(blockCoord.x * 16 + slice, blockCoord.y), 0);
        if (any(greaterThan(voxelBits, vec4(0.0)))) {
            occupied = true;
            break;
        }
    }

    fragColor = occupied ? vec4(1.0) : vec4(0.0);
}
