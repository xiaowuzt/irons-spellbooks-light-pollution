package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
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
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * 512 KB. Only changed texture-row spans are transferred after collection.</p>
 */
final class SpellLightStateVolume {
    /** Same camera-local span as the occupancy cache it feeds. */
    static final int SIZE = SpellLightVoxelGrid.SIZE;
    private static final int VOLUME = SIZE * SIZE * SIZE;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = VOLUME / TEXTURE_WIDTH;
    private static final int MAX_DIRTY_BLOCKS = 128;

    private final SpellLightBlockMaskAtlas atlas;
    private final short[] ids = new short[VOLUME];
    private final ByteBuffer transfer = ByteBuffer.allocateDirect(VOLUME * 2)
            .order(ByteOrder.nativeOrder());
    /** Set from interaction callbacks, which Forge may dispatch off-thread. */
    private final AtomicBoolean rebuildRequested = new AtomicBoolean();
    private final Set<Long> dirtyBlocks = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> recentDirtyBlocks = new LinkedHashMap<>();
    private final BitSet dirtyRows = new BitSet(TEXTURE_HEIGHT);
    private final int[] dirtyMin = new int[TEXTURE_HEIGHT];
    private final int[] dirtyMax = new int[TEXTURE_HEIGHT];
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
        Arrays.fill(dirtyMin, TEXTURE_WIDTH);
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

    /** Interaction callbacks only enqueue coordinates; GL stays on the render thread. */
    void requestRefresh(BlockPos position) {
        if (dirtyBlocks.size() < MAX_DIRTY_BLOCKS) dirtyBlocks.add(position.asLong());
    }

    /**
     * Refreshes the volume for this frame's camera anchor and uploads changes.
     * The anchor deliberately matches {@link SpellLightVoxelGrid}'s so the
     * voxelizer can index this volume from its own cell coordinates. Returns
     * whether its GPU texture changed, allowing the renderer to keep the
     * expensive voxel reduction pass asleep between updates.
     */
    boolean update(@Nullable ClientLevel level, @Nullable Vec3 cameraPosition) {
        if (level == null || cameraPosition == null) {
            return false;
        }
        long started = PerfTracker.begin(PerfTracker.Section.VOXEL);
        try {
            return updateVolume(level, cameraPosition);
        } finally {
            PerfTracker.end(PerfTracker.Section.VOXEL, started);
        }
    }

    private boolean updateVolume(ClientLevel level, Vec3 cameraPosition) {
        ensureTexture();

        int targetX = Mth.floor(cameraPosition.x) - SIZE / 2;
        int targetY = Mth.floor(cameraPosition.y) - SIZE / 2;
        int targetZ = Mth.floor(cameraPosition.z) - SIZE / 2;

        boolean levelChanged = trackedLevel.get() != level;
        if (levelChanged) {
            trackedLevel = new java.lang.ref.WeakReference<>(level);
            dirtyBlocks.clear();
            recentDirtyBlocks.clear();
        }

        idsChanged = false;
        boolean requested = rebuildRequested.getAndSet(false);
        boolean rebuild = !hasOrigin || levelChanged || requested
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

        refreshDirtyBlocks(level);
        boolean masksChanged = atlas.processPending(Math.max(1,
                Math.min(8, AdaptiveVisualQuality.voxelScrubSlabs() * 2)));
        if (atlas.consumeRemapRequired()) {
            for (int index = 0; index < ids.length; index++) {
                short resolved = (short) atlas.resolvedId(ids[index] & 0xFFFF);
                if (ids[index] != resolved) {
                    ids[index] = resolved;
                    markChanged(index);
                }
            }
        }

        if (!dirtyRows.isEmpty()) {
            uploadIds();
        }
        return idsChanged || masksChanged;
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
        dirtyBlocks.clear();
        recentDirtyBlocks.clear();
        dirtyRows.clear();
        Arrays.fill(dirtyMin, TEXTURE_WIDTH);
        Arrays.fill(dirtyMax, 0);
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
        int planes = Math.max(1, Math.min(SIZE, AdaptiveVisualQuality.voxelScrubSlabs()));
        for (int plane = 0; plane < planes; plane++) {
            collect(level, originX, originY + scrubPlane, originZ, SIZE, 1, SIZE);
            scrubPlane = (scrubPlane + 1) % SIZE;
        }
    }

    private void refreshDirtyBlocks(ClientLevel level) {
        long now = level.getGameTime();
        for (Long position : dirtyBlocks) {
            if (dirtyBlocks.remove(position)) {
                if (recentDirtyBlocks.size() >= MAX_DIRTY_BLOCKS && !recentDirtyBlocks.containsKey(position)) {
                    var oldest = recentDirtyBlocks.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
                // Network block updates can follow the mouse event later.
                recentDirtyBlocks.put(position, now + 12);
            }
        }
        var iterator = recentDirtyBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() < now) {
                iterator.remove();
                continue;
            }
            BlockPos position = BlockPos.of(entry.getKey());
            int x = Math.max(originX, position.getX() - 1);
            int y = Math.max(originY, position.getY() - 1);
            int z = Math.max(originZ, position.getZ() - 1);
            collect(level, x, y, z,
                    Math.min(originX + SIZE, position.getX() + 2) - x,
                    Math.min(originY + SIZE, position.getY() + 2) - y,
                    Math.min(originZ + SIZE, position.getZ() + 2) - z);
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
                    write(x, y, z, (short) SpellLightBlockMaskAtlas.AIR_STATE);
                }
            }
        }
    }

    private void write(int x, int y, int z, short id) {
        int index = index(x, y, z);
        if (ids[index] != id) {
            ids[index] = id;
            markChanged(index);
        }
    }

    private void markChanged(int index) {
        idsChanged = true;
        int row = index / TEXTURE_WIDTH;
        int column = index % TEXTURE_WIDTH;
        dirtyRows.set(row);
        dirtyMin[row] = Math.min(dirtyMin[row], column);
        dirtyMax[row] = Math.max(dirtyMax[row], column + 1);
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
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG8,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, 0,
                GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        hasOrigin = false;
        Arrays.fill(ids, (short) SpellLightBlockMaskAtlas.SOLID_STATE);
        dirtyRows.set(0, TEXTURE_HEIGHT);
        Arrays.fill(dirtyMin, 0);
        Arrays.fill(dirtyMax, TEXTURE_WIDTH);
    }

    private void uploadIds() {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(textureId);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        try {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            for (int first = dirtyRows.nextSetBit(0); first >= 0;
                    first = dirtyRows.nextSetBit(first)) {
                int end = dirtyRows.nextClearBit(first);
                int min = TEXTURE_WIDTH, max = 0;
                for (int row = first; row < end; row++) {
                    min = Math.min(min, dirtyMin[row]);
                    max = Math.max(max, dirtyMax[row]);
                }
                transfer.clear();
                for (int row = first; row < end; row++) {
                    for (int column = min; column < max; column++) {
                        int id = ids[row * TEXTURE_WIDTH + column] & 0xFFFF;
                        transfer.put((byte) id).put((byte) (id >>> 8));
                    }
                }
                transfer.flip();
                TextureUpload.subImage2D(GL11.GL_TEXTURE_2D, 0, min, first,
                        max - min, end - first, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, transfer);
                dirtyRows.clear(first, end);
                Arrays.fill(dirtyMin, first, end, TEXTURE_WIDTH);
                Arrays.fill(dirtyMax, first, end, 0);
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
        }
    }
}
