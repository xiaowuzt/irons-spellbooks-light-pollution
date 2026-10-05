package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-block-state occupancy masks baked from the block's own rendered model.
 *
 * <p>The occupancy cache this feeds is otherwise derived from the rendered
 * depth buffer, which is the only way to see that a trapdoor's four holes are
 * texture alpha rather than model geometry — but it is also fundamentally
 * camera-dependent. Seen close to edge-on a 3/16-thick plate covers one or two
 * pixels of screen width, so the eight-by-eight pattern across its face has
 * nowhere near enough samples to resolve, and the projection error is large
 * enough that the voxelizer erases bars that really are there. That is what
 * makes shadows lose chunks at particular viewing angles.
 *
 * <p>Baking from {@link BakedModel} instead reads the same information the
 * renderer does — quad geometry plus {@link SpriteContents#isTransparent} for
 * the cutout alpha — with no camera involved, so the result is exact and
 * identical from every angle. Masks are cached per state and uploaded into a
 * GPU atlas; a companion volume of state ids indexes into it.</p>
 */
final class SpellLightBlockMaskAtlas {
    /** Subvoxels per block edge; matches the cache's 8^3 layout. */
    static final int SUBDIVISIONS = 8;
    /** 16 RGBA texels per block, as VanillaDI lays out its voxel texture. */
    static final int TEXELS_PER_STATE = 16;
    static final int BYTES_PER_STATE = TEXELS_PER_STATE * 4;
    /** Atlas geometry: 128 states per row, so x = (id % 128) * 16 + slice. */
    static final int STATES_PER_ROW = 128;
    static final int ATLAS_WIDTH = STATES_PER_ROW * TEXELS_PER_STATE;
    static final int ATLAS_ROWS = 16;
    static final int MAX_STATES = STATES_PER_ROW * ATLAS_ROWS;

    /**
     * Samples per quad edge. A block face spanning 1x1 gives a step of 1/48
     * block, about six samples across a subvoxel and three across a 16x16
     * sprite texel, so a 3x3-texel trapdoor hole is resolved exactly.
     */
    private static final int QUAD_SAMPLES = 48;

    /** Id 0 is reserved for "no baked mask; fall back to depth collection". */
    private static final int NO_STATE = 0;
    static final int SOLID_STATE = 1;
    static final int AIR_STATE = 2;

    private final Map<BlockState, Integer> stateIds = new IdentityHashMap<>();
    /**
     * Stable atlas ids: 0 is depth fallback, 1 is conservative solid occupancy,
     * 2 is known air. Model ids start at 3 and retain their slot while baking.
     */
    private final List<byte[]> masks = new ArrayList<>(List.of(
            new byte[BYTES_PER_STATE], solidMask(), new byte[BYTES_PER_STATE]));
    private final ArrayDeque<PendingMask> pending = new ArrayDeque<>();
    private final int[] resolvedIds = new int[MAX_STATES];
    private boolean remapRequired;
    private final ByteBuffer transfer = ByteBuffer.allocateDirect(BYTES_PER_STATE)
            .order(ByteOrder.nativeOrder());
    private int textureId = -1;
    private boolean exhaustedLogged;

    int textureId() {
        return textureId;
    }

    /**
     * Returns a stable atlas id. Unseen non-cube models queue for budgeted
     * baking with an opaque placeholder; air and solid cubes share reserved
     * masks. Unsupported/empty models retain the depth fallback.
     */
    int idFor(BlockState state) {
        if (state == null || state.isAir()) {
            return AIR_STATE;
        }
        Integer existing = stateIds.get(state);
        if (existing != null) {
            return existing;
        }
        if (state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            stateIds.put(state, SOLID_STATE);
            return SOLID_STATE;
        }
        if (masks.size() >= MAX_STATES) {
            if (!exhaustedLogged) {
                exhaustedLogged = true;
                com.gang.lightpollution.ExampleMod.LOGGER.warn(
                        "Spell light block-mask atlas is full at {} states; further states fall back to depth voxelization",
                        MAX_STATES);
            }
            stateIds.put(state, NO_STATE);
            return NO_STATE;
        }

        int id = masks.size();
        byte[] mask = solidMask();
        masks.add(mask);
        stateIds.put(state, id);
        resolvedIds[id] = id;
        pending.addLast(new PendingMask(state, id));
        return id;
    }

    /** Bound cold model baking per frame; unknown models stay opaque until ready. */
    boolean processPending(int maximum) {
        ensureTexture();
        boolean changed = false;
        long deadline = System.nanoTime() + 2_000_000L;
        for (int count = 0; count < maximum && !pending.isEmpty(); count++) {
            PendingMask next = pending.removeFirst();
            byte[] mask = bake(next.state());
            if (isEmpty(mask)) {
                // Keep the original depth fallback for models without a baked
                // surface. Existing volume references are remapped this frame.
                stateIds.put(next.state(), NO_STATE);
                resolvedIds[next.id()] = NO_STATE;
                remapRequired = true;
            }
            masks.set(next.id(), mask);
            upload(next.id(), mask);
            changed = true;
            if (System.nanoTime() >= deadline) break;
        }
        return changed;
    }

    boolean consumeRemapRequired() {
        boolean result = remapRequired;
        remapRequired = false;
        return result;
    }

    int resolvedId(int id) {
        return id <= AIR_STATE ? id : resolvedIds[id];
    }

    private static byte[] solidMask() {
        byte[] mask = new byte[BYTES_PER_STATE];
        Arrays.fill(mask, (byte) 0xFF);
        return mask;
    }

    private record PendingMask(BlockState state, int id) {}


    /** Drops every baked mask; call when block models are rebuilt. */
    void invalidate() {
        stateIds.clear();
        masks.clear();
        masks.add(new byte[BYTES_PER_STATE]);
        masks.add(solidMask());
        masks.add(new byte[BYTES_PER_STATE]);
        pending.clear();
        Arrays.fill(resolvedIds, 0);
        remapRequired = false;
        exhaustedLogged = false;
        if (textureId >= 0) {
            TextureUtil.releaseTextureId(textureId);
            textureId = -1;
        }
    }

    void release() {
        invalidate();
    }

    private byte[] bake(BlockState state) {
        byte[] mask = new byte[BYTES_PER_STATE];

        // A block that renders as a full opaque cube occludes every subvoxel.
        // Filling it directly is both cheaper and more robust than rasterizing
        // six faces into a hollow shell.
        if (state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            java.util.Arrays.fill(mask, (byte) 0xFF);
            return mask;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getBlockRenderer() == null) {
            return mask;
        }
        BakedModel model;
        try {
            model = minecraft.getBlockRenderer().getBlockModel(state);
        } catch (RuntimeException failure) {
            return mask;
        }
        if (model == null) {
            return mask;
        }

        RandomSource random = RandomSource.create();
        rasterizeQuads(model, state, null, random, mask);
        for (Direction direction : Direction.values()) {
            rasterizeQuads(model, state, direction, random, mask);
        }
        return mask;
    }

    private void rasterizeQuads(BakedModel model, BlockState state, Direction direction,
                                RandomSource random, byte[] mask) {
        List<BakedQuad> quads;
        // A fixed seed keeps the mask stable for models that randomise their
        // variant; null render type asks Forge for every layer at once.
        random.setSeed(0x9E3779B97F4A7C15L);
        try {
            quads = model.getQuads(state, direction, random, ModelData.EMPTY, null);
        } catch (RuntimeException failure) {
            return;
        }
        if (quads == null) {
            return;
        }
        for (BakedQuad quad : quads) {
            if (quad != null) {
                rasterizeQuad(quad, mask);
            }
        }
    }

    private void rasterizeQuad(BakedQuad quad, byte[] mask) {
        int[] vertices = quad.getVertices();
        // DefaultVertexFormat.BLOCK is 32 bytes per vertex: position (3 floats),
        // colour (4 bytes), UV0 (2 floats), UV2 (2 shorts), normal + padding.
        int stride = 8;
        if (vertices.length < stride * 4) {
            return;
        }

        TextureAtlasSprite sprite = quad.getSprite();
        SpriteContents contents = sprite == null ? null : sprite.contents();
        float spriteU0 = sprite == null ? 0.0F : sprite.getU0();
        float spriteU1 = sprite == null ? 1.0F : sprite.getU1();
        float spriteV0 = sprite == null ? 0.0F : sprite.getV0();
        float spriteV1 = sprite == null ? 1.0F : sprite.getV1();
        float spriteSpanU = spriteU1 - spriteU0;
        float spriteSpanV = spriteV1 - spriteV0;
        int spriteWidth = contents == null ? 0 : contents.width();
        int spriteHeight = contents == null ? 0 : contents.height();
        boolean alphaAvailable = contents != null && spriteWidth > 0 && spriteHeight > 0
                && Math.abs(spriteSpanU) > 1.0E-7F && Math.abs(spriteSpanV) > 1.0E-7F;

        float[] cornerX = new float[4];
        float[] cornerY = new float[4];
        float[] cornerZ = new float[4];
        float[] cornerU = new float[4];
        float[] cornerV = new float[4];
        for (int corner = 0; corner < 4; corner++) {
            int base = corner * stride;
            cornerX[corner] = Float.intBitsToFloat(vertices[base]);
            cornerY[corner] = Float.intBitsToFloat(vertices[base + 1]);
            cornerZ[corner] = Float.intBitsToFloat(vertices[base + 2]);
            cornerU[corner] = Float.intBitsToFloat(vertices[base + 4]);
            cornerV[corner] = Float.intBitsToFloat(vertices[base + 5]);
        }

        // Quantizing a face exactly on a subvoxel boundary puts it in the cell
        // OUTSIDE the geometry: a slab's top face at y=0.5 would land in cell 4,
        // the same cell a receiver standing on it occupies, and the surface
        // would shadow itself. Step a fraction of a subvoxel inward first.
        float[] inward = inwardOffset(quad, cornerX, cornerY, cornerZ);

        // Sample at cell CENTRES, never at cell boundaries. A sample sitting
        // exactly on a texel boundary is rounded by floor() onto the texel on
        // one particular side, and for a cutout that side decides whether the
        // hole survives. An oak trapdoor's hole spans texels [3,6), so the
        // boundary sample at s = 6/16 reads texel 6 -- opaque -- and marks the
        // subvoxel occupied even though every interior sample is transparent.
        // That silently filled three of the four holes while leaving the fourth
        // open, because the two hole bands sit at opposite ends of the
        // half-open interval and only one of them has its boundary land on an
        // opaque texel.
        for (int i = 0; i < QUAD_SAMPLES; i++) {
            float s = (i + 0.5F) / QUAD_SAMPLES;
            for (int j = 0; j < QUAD_SAMPLES; j++) {
                float t = (j + 0.5F) / QUAD_SAMPLES;

                if (alphaAvailable) {
                    float u = bilinear(cornerU, s, t);
                    float v = bilinear(cornerV, s, t);
                    int texelX = Mth.clamp((int) ((u - spriteU0) / spriteSpanU * spriteWidth),
                            0, spriteWidth - 1);
                    int texelY = Mth.clamp((int) ((v - spriteV0) / spriteSpanV * spriteHeight),
                            0, spriteHeight - 1);
                    if (contents.isTransparent(0, texelX, texelY)) {
                        continue;
                    }
                }

                int subX = subVoxel(bilinear(cornerX, s, t) + inward[0]);
                int subY = subVoxel(bilinear(cornerY, s, t) + inward[1]);
                int subZ = subVoxel(bilinear(cornerZ, s, t) + inward[2]);
                int index = maskByteIndex(subY, subZ);
                mask[index] = (byte) (mask[index] | (1 << subX));
            }
        }

    }

    /**
     * A small step along the quad's inward normal. The geometric normal is
     * oriented against {@link BakedQuad#getDirection()} so it reliably points
     * out of the block even for the slanted quads in stair and wall models.
     */
    private static float[] inwardOffset(BakedQuad quad,
                                        float[] cornerX, float[] cornerY, float[] cornerZ) {
        float edge1X = cornerX[1] - cornerX[0];
        float edge1Y = cornerY[1] - cornerY[0];
        float edge1Z = cornerZ[1] - cornerZ[0];
        float edge2X = cornerX[2] - cornerX[0];
        float edge2Y = cornerY[2] - cornerY[0];
        float edge2Z = cornerZ[2] - cornerZ[0];
        float normalX = edge1Y * edge2Z - edge1Z * edge2Y;
        float normalY = edge1Z * edge2X - edge1X * edge2Z;
        float normalZ = edge1X * edge2Y - edge1Y * edge2X;
        float length = (float) Math.sqrt(normalX * normalX
                + normalY * normalY + normalZ * normalZ);
        if (length < 1.0E-6F) {
            return new float[3];
        }
        normalX /= length;
        normalY /= length;
        normalZ /= length;

        Direction direction = quad.getDirection();
        if (direction != null) {
            float facing = normalX * direction.getStepX()
                    + normalY * direction.getStepY()
                    + normalZ * direction.getStepZ();
            if (facing < 0.0F) {
                normalX = -normalX;
                normalY = -normalY;
                normalZ = -normalZ;
            }
        }

        // A sixth of a subvoxel: enough to land inside the geometry, small
        // enough never to skip past a 3/16 plate's only occupied layer.
        final float step = 1.0F / (SUBDIVISIONS * 6);
        return new float[] {-normalX * step, -normalY * step, -normalZ * step};
    }

    /** Bilinear interpolation over the quad's four corners in winding order. */
    private static float bilinear(float[] corners, float s, float t) {
        float lower = Mth.lerp(s, corners[0], corners[1]);
        float upper = Mth.lerp(s, corners[3], corners[2]);
        return Mth.lerp(t, lower, upper);
    }

    private static int subVoxel(float blockLocal) {
        return Mth.clamp((int) (blockLocal * SUBDIVISIONS), 0, SUBDIVISIONS - 1);
    }

    /**
     * Byte holding the eight X bits for one (Y, Z) row, matching the shader:
     * slice = z * 2 + y / 4 selects the RGBA texel, y % 4 selects the channel.
     */
    static int maskByteIndex(int subY, int subZ) {
        return (subZ * 2 + subY / 4) * 4 + (subY % 4);
    }

    private static boolean isEmpty(byte[] mask) {
        for (byte value : mask) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private void upload(int id, byte[] mask) {
        ensureTexture();
        transfer.clear();
        transfer.put(mask);
        transfer.flip();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(textureId);
        TextureUpload.subImage2D(GL11.GL_TEXTURE_2D, 0,
                (id % STATES_PER_ROW) * TEXELS_PER_STATE, id / STATES_PER_ROW,
                TEXELS_PER_STATE, 1,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, transfer);
    }

    private void ensureTexture() {
        if (textureId >= 0) {
            return;
        }
        textureId = TextureUtil.generateTextureId();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        // Pre-fill even unassigned slots with conservative occupancy in one
        // transfer. Discovering many states must not issue one GL call per
        // placeholder before the baking budget has had a chance to run.
        ByteBuffer initial = ByteBuffer.allocateDirect(ATLAS_WIDTH * ATLAS_ROWS * 4);
        while (initial.hasRemaining()) initial.put((byte) 0xFF);
        for (int id = 0; id < masks.size(); id++) {
            initial.position(id * BYTES_PER_STATE);
            initial.put(masks.get(id));
        }
        initial.clear();
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                ATLAS_WIDTH, ATLAS_ROWS, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, initial);
    }
}
