package com.gang.lightpollution.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera-local volume of block-mask ids, one per block, indexing
 * {@link SpellLightBlockMaskAtlas}.
 *
 * <p>Together the two replace the depth buffer as the source of block occupancy
 * for the shadow cache. The voxelizer needs two texture fetches per cell — the
 * id here, then that state's 8<sup>3</sup> mask from the atlas — instead of
 * projecting 32 subvoxel centres into the depth buffer, and the answer no longer
 * depends on where the camera happens to be looking.</p>
 *
 * <p>Storage is toroidal, so rolling the anchor by a block only recollects the
 * slab that just entered the volume; a block leaving shares its slot with the
 * block entering opposite it. At one 16-bit id per block the whole volume is
 * 512 KB, small enough to re-upload whenever anything changes.</p>
 */
final class SpellLightStateVolume {
    /** Same camera-local span as the occupancy cache it feeds. */
    static final int SIZE = SpellLightVoxelGrid.SIZE;
    private static final int VOLUME = SIZE * SIZE * SIZE;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = VOLUME / TEXTURE_WIDTH;
    /** Horizontal planes revalidated per frame; the volume cycles every 16. */
    private static final int SCRUB_PLANES = 4;

    private final SpellLightBlockMaskAtlas atlas;
    private final short[] ids = new short[VOLUME];
    private final ByteBuffer transfer = ByteBuffer.allocateDirect(VOLUME * 2)
            .order(ByteOrder.nativeOrder());
    /** Set from interaction callbacks, which Forge may dispatch off-thread. */
    private final AtomicBoolean rebuildRequested = new AtomicBoolean();
    private java.lang.ref.WeakReference<ClientLevel> trackedLevel =
            new java.lang.ref.WeakReference<>(null);

    private int textureId = -1;
    private boolean hasOrigin;
    private int originX;
    private int originY;
    private int originZ;
    private int scrubPlane;
    private boolean idsChanged;

    SpellLightStateVolume(SpellLightBlockMaskAtlas atlas) {
        this.atlas = atlas;
    }

    int textureId() {
        return textureId;
    }

    boolean isReady() {
        return hasOrigin && textureId >= 0 && atlas.textureId() >= 0;
    }

    /** Origin wrapped into the toroidal index space, for the shader. */
    float wrapX() {
        return originX & (SIZE - 1);
    }

    float wrapY() {
        return originY & (SIZE - 1);
    }

    float wrapZ() {
        return originZ & (SIZE - 1);
    }

    /** Requests a full recollection on the next frame. */
    void requestRebuild() {
        rebuildRequested.set(true);
    }

    /**
     * Refreshes the volume for this frame's camera anchor and uploads changes.
     * The anchor deliberately matches {@link SpellLightVoxelGrid}'s so the
     * voxelizer can index this volume from its own cell coordinates.
     */
    void update(@Nullable ClientLevel level, @Nullable Vec3 cameraPosition) {
        if (level == null || cameraPosition == null) {
            return;
        }
        ensureTexture();

        int targetX = Mth.floor(cameraPosition.x) - SIZE / 2;
        int targetY = Mth.floor(cameraPosition.y) - SIZE / 2;
        int targetZ = Mth.floor(cameraPosition.z) - SIZE / 2;

        boolean levelChanged = trackedLevel.get() != level;
        if (levelChanged) {
            trackedLevel = new java.lang.ref.WeakReference<>(level);
        }

        idsChanged = false;
        boolean rebuild = !hasOrigin || levelChanged
                || rebuildRequested.getAndSet(false)
                || Math.abs(targetX - originX) >= SIZE
                || Math.abs(targetY - originY) >= SIZE
                || Math.abs(targetZ - originZ) >= SIZE;
        if (rebuild) {
            originX = targetX;
            originY = targetY;
            originZ = targetZ;
            hasOrigin = true;
            scrubPlane = 0;
            collect(level, originX, originY, originZ, SIZE, SIZE, SIZE);
        } else {
            collectNewlyCovered(level, targetX, targetY, targetZ);
            originX = targetX;
            originY = targetY;
            originZ = targetZ;
            scrub(level);
        }

        if (idsChanged) {
            uploadIds();
        }
    }

    void release() {
        if (textureId >= 0) {
            TextureUtil.releaseTextureId(textureId);
            textureId = -1;
        }
        hasOrigin = false;
        scrubPlane = 0;
        idsChanged = false;
        rebuildRequested.set(false);
        trackedLevel = new java.lang.ref.WeakReference<>(null);
        Arrays.fill(ids, (short) 0);
    }

    /** Recollects only the slabs the volume just rolled onto. */
    private void collectNewlyCovered(ClientLevel level, int targetX, int targetY, int targetZ) {
        int deltaX = targetX - originX;
        int deltaY = targetY - originY;
        int deltaZ = targetZ - originZ;
        // Overlapping corners are collected twice, which is cheaper than the
        // bookkeeping needed to exclude them and produces the same result.
        if (deltaX > 0) {
            collect(level, originX + SIZE, targetY, targetZ, deltaX, SIZE, SIZE);
        } else if (deltaX < 0) {
            collect(level, targetX, targetY, targetZ, -deltaX, SIZE, SIZE);
        }
        if (deltaY > 0) {
            collect(level, targetX, originY + SIZE, targetZ, SIZE, deltaY, SIZE);
        } else if (deltaY < 0) {
            collect(level, targetX, targetY, targetZ, SIZE, -deltaY, SIZE);
        }
        if (deltaZ > 0) {
            collect(level, targetX, targetY, originZ + SIZE, SIZE, SIZE, deltaZ);
        } else if (deltaZ < 0) {
            collect(level, targetX, targetY, targetZ, SIZE, SIZE, -deltaZ);
        }
    }

    /** Revalidates a rolling band so other players' block changes heal. */
    private void scrub(ClientLevel level) {
        for (int plane = 0; plane < SCRUB_PLANES; plane++) {
            collect(level, originX, originY + scrubPlane, originZ, SIZE, 1, SIZE);
            scrubPlane = (scrubPlane + 1) % SIZE;
        }
    }

    /** Collects an axis-aligned world-space box into the toroidal volume. */
    private void collect(ClientLevel level, int minX, int minY, int minZ,
                         int spanX, int spanY, int spanZ) {
        if (spanX <= 0 || spanY <= 0 || spanZ <= 0) {
            return;
        }
        int maxX = minX + spanX;
        int maxY = minY + spanY;
        int maxZ = minZ + spanZ;

        for (int sectionZ = SectionPos.blockToSectionCoord(minZ);
                sectionZ <= SectionPos.blockToSectionCoord(maxZ - 1); sectionZ++) {
            int boxMinZ = Math.max(minZ, SectionPos.sectionToBlockCoord(sectionZ));
            int boxMaxZ = Math.min(maxZ, SectionPos.sectionToBlockCoord(sectionZ) + 16);
            for (int sectionX = SectionPos.blockToSectionCoord(minX);
                    sectionX <= SectionPos.blockToSectionCoord(maxX - 1); sectionX++) {
                int boxMinX = Math.max(minX, SectionPos.sectionToBlockCoord(sectionX));
                int boxMaxX = Math.min(maxX, SectionPos.sectionToBlockCoord(sectionX) + 16);
                // Never force a chunk load from the render thread; an absent
                // chunk contributes no occupancy.
                LevelChunk chunk = level.getChunkSource()
                        .getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
                for (int sectionY = SectionPos.blockToSectionCoord(minY);
                        sectionY <= SectionPos.blockToSectionCoord(maxY - 1); sectionY++) {
                    int boxMinY = Math.max(minY, SectionPos.sectionToBlockCoord(sectionY));
                    int boxMaxY = Math.min(maxY, SectionPos.sectionToBlockCoord(sectionY) + 16);
                    LevelChunkSection section = sectionAt(chunk, sectionY);
                    if (section == null || section.hasOnlyAir()) {
                        clearBox(boxMinX, boxMaxX, boxMinY, boxMaxY, boxMinZ, boxMaxZ);
                        continue;
                    }
                    collectSection(section, boxMinX, boxMaxX,
                            boxMinY, boxMaxY, boxMinZ, boxMaxZ);
                }
            }
        }
    }

    private void collectSection(LevelChunkSection section, int minX, int maxX,
                                int minY, int maxY, int minZ, int maxZ) {
        for (int z = minZ; z < maxZ; z++) {
            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x < maxX; x++) {
                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                    write(x, y, z, (short) atlas.idFor(state));
                }
            }
        }
    }

    private void clearBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int z = minZ; z < maxZ; z++) {
            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x < maxX; x++) {
                    write(x, y, z, (short) 0);
                }
            }
        }
    }

    private void write(int x, int y, int z, short id) {
        int index = index(x, y, z);
        if (ids[index] != id) {
            ids[index] = id;
            idsChanged = true;
        }
    }

    /** Toroidal address; SIZE is a power of two so the wrap is a mask. */
    private static int index(int x, int y, int z) {
        return (x & (SIZE - 1))
                + (y & (SIZE - 1)) * SIZE
                + (z & (SIZE - 1)) * SIZE * SIZE;
    }

    @Nullable
    private static LevelChunkSection sectionAt(@Nullable LevelChunk chunk, int sectionY) {
        if (chunk == null) {
            return null;
        }
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = chunk.getSections();
        if (index < 0 || index >= sections.length) {
            return null;
        }
        return sections[index];
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
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG8,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, 0,
                GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        hasOrigin = false;
        Arrays.fill(ids, (short) 0);
    }

    private void uploadIds() {
        transfer.clear();
        for (short id : ids) {
            transfer.put((byte) (id & 0xFF));
            transfer.put((byte) ((id >> 8) & 0xFF));
        }
        transfer.flip();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(textureId);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                TEXTURE_WIDTH, TEXTURE_HEIGHT,
                GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, transfer);
    }
}
