package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.client.GeminiKillEffectPostShaders;
import com.gang.lightpollution.client.perf.AdaptiveVisualQuality;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.gang.lightpollution.entity.GargantuaEntity;
import com.gang.lightpollution.entity.CosmicHorseshoeEntity;
import com.gang.lightpollution.entity.SingularityEntity;
import com.gang.lightpollution.entity.StarfallEntity;
import com.gang.lightpollution.fx.StarfallShape;
import com.gang.lightpollution.entity.StarlessEntity;
import com.gang.lightpollution.client.GeminiKillEffectPostShaders.Pass;
import com.gang.lightpollution.entity.SilhouetteEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared screen-space point-light pass for the migrated spell anchors.
 *
 * <p>This is the VanillaDI-style part of the integration: the spell supplies
 * a light marker in view space, while the post shader reconstructs visible
 * surfaces from the depth buffer, computes N dot L/specular response, and
 * traces short depth-buffer shadow rays. It changes scene illumination without
 * pretending to modify Minecraft's persistent block-light levels.</p>
 */
public final class SpellLightPostProcessor {
    private static final float NEAR_PLANE = 0.05F;
    /*
     * The light records are uploaded to a texture, so there is no small
     * gameplay-facing light slot limit. The only practical bound is the
     * driver's texture capacity and the amount of work the user asks for.
     */
    private static final int VOXEL_WIDTH = SpellLightVoxelGrid.TEXTURE_WIDTH;
    private static final int VOXEL_HEIGHT = SpellLightVoxelGrid.TEXTURE_HEIGHT;
    private static final int VOXEL_LOD_WIDTH = 128;
    private static final int VOXEL_LOD_HEIGHT = 2048;
    /** VanillaDI's voxelizer rebuild cadence for the rolling cache. */
    private static final int VOXEL_SKIP_FRAMES = 3;

    /**
     * VanillaDI's temporal accumulation is the part that prevents thin
     * occluders from changing shadow decisions every time the camera crosses a
     * screen pixel.  The matrices uploaded below are camera-relative and keep
     * the OpenGL eye-space sign convention intact, so the history pass can be
     * enabled without reintroducing the old translation double-count.
     */
    private static final boolean TEMPORAL_REPROJECTION_ENABLED = true;

    /** How close a meteor must be to earn a distortion pass, in blocks. */
    private static final double METEOR_SHOCK_DISTANCE = 72.0D;
    /** Distortion passes per frame at most; each one is fullscreen. */
    private static final int MAX_METEOR_SHOCKS = 3;

    private static BufferBuilder fullscreenBuffer = new BufferBuilder(4_096);
    private static TextureTarget sceneCopy;
    private static TextureTarget voxelGrid;
    /** Previous full voxel result. The voxelizer samples this while writing voxelGrid. */
    private static TextureTarget voxelHistory;
    private static TextureTarget voxelLod;
    private static TextureTarget normalTarget;
    private static TextureTarget lightPing;
    private static TextureTarget lightPong;
    /** Current frame's temporal output; spatial passes never overwrite it. */
    private static TextureTarget temporalTarget;
    /** Previous frame's pre-spatial temporal radiance. */
    private static TextureTarget lightHistory;
    /** Previous frame rejection inputs, matching VanillaDI's history targets. */
    private static TextureTarget previousDepth;
    /**
     * Independent copy of the current frame's depth.
     *
     * <p>The final blend draws into the main target, so it must not sample the
     * depth texture attached to that same framebuffer: reading an attachment of
     * the framebuffer being written is undefined in OpenGL, and showed up as
     * horizontal splits and rectangular bands that varied by driver.</p>
     */
    private static TextureTarget currentDepth;
    private static TextureTarget previousNormals;
    private static TextureTarget previousFrame;
    private static TextureTarget frameScratch;
    private static final SpellLightDataTexture lightData = new SpellLightDataTexture();
    private static final SpellLightVoxelGrid voxelCache = new SpellLightVoxelGrid();
    private static final SpellLightBlockMaskAtlas maskAtlas = new SpellLightBlockMaskAtlas();
    private static final SpellLightStateVolume stateVolume =
            new SpellLightStateVolume(maskAtlas);
    private static boolean voxelHistoryValid;
    /** True only after the current voxel texture has been reduced into voxelLod. */
    private static boolean voxelLodValid;
    private static int voxelFrame;
    /**
     * Rendered-frame counter driving the stochastic shadow sampler's seed.
     * Wrapped at 1024 so it stays exactly representable as a float uniform.
     */
    private static float shadowFrame;
    /**
     * Debug view selector, uploaded as {@code Params.z} to the lighting pass. A
     * nonzero value makes {@link #render} blit the raw lighting target to the
     * screen and skip temporal, spatial and blend, so the view shows exactly
     * what the lighting pass decided rather than the filtered result.
     */
    private static int debugMode;

    /** @return the debug view now selected, for command feedback. */
    public static int cycleDebugMode(int mode) {
        debugMode = Math.max(0, Math.min(4, mode));
        return debugMode;
    }
    /** One-shot guard for the in-world GPU occupancy readback. */
    /** Number of consecutive collection frames requested by a world update. */
    private static final AtomicInteger voxelRefreshFrames = new AtomicInteger();
    // A block update can arrive a few render frames after the mouse event
    // (especially while mining). Keep the cache in a short full-collection
    // window long enough for the new depth buffer to be observed reliably.
    private static final int WORLD_CHANGE_REFRESH_FRAMES = 12;
    private static boolean historyValid;
    /** Debug switch for isolating denoiser artefacts from mask artefacts. */
    private static volatile boolean filterEnabled = true;
    private static boolean runtimeDisabled;
    private static boolean failureLogged;
    private static boolean activeLogged;
    private static Vec3 previousCameraPosition;
    private static Vector3f previousCameraForward;
    private static Matrix4f previousProjection;
    private static Matrix4f previousView;
    private static long previousLightSignature;

    private SpellLightPostProcessor() {
    }

    /**
     * Requests a short burst of GPU voxel collection after a client-side
     * block interaction.  The request is thread-safe because Forge can dispatch
     * interaction callbacks outside the render callback; OpenGL work remains
     * confined to {@link #render}.
     */
    public static void requestVoxelRefresh() {
        voxelRefreshFrames.updateAndGet(current ->
                Math.max(current, WORLD_CHANGE_REFRESH_FRAMES));
        stateVolume.requestRebuild();
    }

    /** Refresh only the edited block and neighbouring connected shapes. */
    public static void requestVoxelRefresh(BlockPos position) {
        if (position != null) stateVolume.requestRefresh(position);
    }



    /**
     * Drops the baked occupancy masks so they are re-baked from the new
     * {@link net.minecraft.client.resources.model.BakedModel}s.
     *
     * <p>{@code BlockState} instances survive a resource reload — only the baked
     * models are rebuilt — so the atlas's identity-keyed cache would otherwise
     * keep serving masks baked from the previous models after a resource pack
     * changes a block's geometry or a texture's alpha.</p>
     *
     * <p>The state-id volume has to be rebuilt in the same breath: invalidating
     * the atlas renumbers its ids without touching the volume, so stale ids
     * would index other states' masks. Dropping the atlas texture also makes
     * {@code isReady()} false until then, which parks the shader on the depth
     * path for the intervening frames rather than on wrong masks.</p>
     */
    public static void onResourcesReloaded() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(SpellLightPostProcessor::onResourcesReloaded);
            return;
        }
        maskAtlas.invalidate();
        stateVolume.requestRebuild();
    }

    /**
     * Applies Silhouette on frames where the light pass did not run.
     *
     * <p>The inversion normally rides along in {@code LIGHT_BLEND}, which is the
     * one pass holding both the scene and the spell-light radiance. But that pass
     * only executes when there is at least one light to shade, and Silhouette is
     * usable on its own — cast with no other spell active, {@code render} returns
     * early and the field would do nothing at all. This standalone path composites
     * the scene against an empty radiance buffer so the negative still appears;
     * with no spell light, every lit value is zero, which is exactly the "all
     * shadow, all glowing" reading the spell should give.</p>
     */
    public static boolean renderSilhouetteOnly(Matrix4f projectionMatrix, Matrix4f viewMatrix,
                                               Camera camera, float partialTick) {
        if (runtimeDisabled || !SpellLightConfig.enabled || projectionMatrix == null
                || viewMatrix == null || camera == null || debugMode > 0
                || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        SilhouetteEntity field = SpellLightEmitter.strongestSilhouette(partialTick);
        if (field == null || field.getInversionStrength(partialTick) <= 0.001F) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (minecraft.level == null || mainTarget == null
                || mainTarget.getDepthTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return false;
        }

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        try {
            ensureSceneCopy(mainTarget.width, mainTarget.height);
            ensureLightTargets(mainTarget.width, mainTarget.height);
            ensureCurrentDepth(mainTarget.width, mainTarget.height);
            Matrix4f projection = new Matrix4f(projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f viewRotation = new Matrix4f(viewMatrix)
                    .setTranslation(0.0F, 0.0F, 0.0F);
            Matrix4f inverseView = new Matrix4f(viewRotation).invert();

            copyColor(mainTarget, sceneCopy);
            copyDepth(mainTarget, currentDepth);
            // An empty radiance buffer: nothing is lit, so the whole field reads
            // as shadow and glows.
            lightPong.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            lightPong.clear(Minecraft.ON_OSX);
            runBlendPass(sceneCopy.getColorTextureId(), lightPong, mainTarget,
                    currentDepth.getDepthTextureId(), inverseProjection,
                    inverseView, camera.getPosition(), partialTick);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
            return false;
        } finally {
            snapshot.restore();
        }
    }

    public static boolean render(List<SpellLightEmitter.Light> emitters,
                                 Matrix4f projectionMatrix, Matrix4f viewMatrix,
                                 Camera camera, float partialTick) {
        if (runtimeDisabled || !SpellLightConfig.enabled || emitters == null
                || projectionMatrix == null || viewMatrix == null || camera == null
                || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        if (emitters.isEmpty()) {
            historyValid = false;
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.SCREEN_LIGHTING);
        if (minecraft.level == null || mainTarget == null || shader == null
                || mainTarget.getDepthTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return false;
        }

        List<SpellLightEmitter.Light> lights = emitters.stream()
                .filter(light -> light != null && light.intensity() > 0.01F
                        && light.radius() > 0.1F)
                .limit(AdaptiveVisualQuality.lightLimit(SpellLightConfig.maxLights))
                .toList();
        if (lights.isEmpty()) {
            return false;
        }

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        long lightingStarted = PerfTracker.begin(PerfTracker.Section.LIGHTING);
        try {
            ensureSceneCopy(mainTarget.width, mainTarget.height);
            ensureVoxelGrid();
            ensureVoxelLod();
            ensureNormalTarget(mainTarget.width, mainTarget.height);
            ensureLightTargets(mainTarget.width, mainTarget.height);
            ensureCurrentDepth(mainTarget.width, mainTarget.height);
            Vec3 cameraPosition = camera.getPosition();
            Matrix4f projection = new Matrix4f(projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f worldToView = new Matrix4f(viewMatrix);
            // The shader reconstructs camera-relative positions with w=0 and
            // adds CameraWorldPos explicitly, so view translation must not be
            // present in the inverse rotation matrix.
            Matrix4f viewRotation = new Matrix4f(worldToView)
                    .setTranslation(0.0F, 0.0F, 0.0F);
            Matrix4f inverseView = new Matrix4f(viewRotation).invert();
            Matrix4f inverseViewProjection = new Matrix4f(projection)
                    .mul(viewRotation)
                    .invert();
            Vector3f forward = inverseView.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F))
                    .normalize();
            long lightSignature = signature(lights);
            if (!TEMPORAL_REPROJECTION_ENABLED
                    || !cameraStable(cameraPosition, forward, projection)
                    || lightSignature != previousLightSignature) {
                historyValid = false;
            }
            float aspect = mainTarget.width / (float) mainTarget.height;
            float fovRadians = currentFovRadians(minecraft);
            float farPlane = Math.max(minecraft.gameRenderer.getDepthFar(), 64.0F);

            copyColor(mainTarget, sceneCopy);
            runNormalsPass(normalTarget, mainTarget.getDepthTextureId(), inverseProjection);

            // The occupancy cache is generated from the actual rendered depth
            // and normal buffers.  This is deliberately GPU-only: collision
            // shapes cannot represent cutout holes in fences, bars, or
            // trapdoors.  voxelHistory is read-only input while voxelGrid is
            // the current write target, so the shader never samples its own
            // render target.
            boolean cacheWasReady = voxelCache.isReady();
            boolean anchorChanged = voxelCache.prepareForGpu(cameraPosition, voxelGrid);
            // Block occupancy comes from the blocks' own baked models, which is
            // exact and identical from every camera angle. The depth path below
            // still runs, but only for cells this volume has no mask for.
            boolean stateVolumeChanged = stateVolume.update(minecraft.level, cameraPosition);
            // A newly selected anchor is usable for the current frame, but its
            // rolling volume is only partially repopulated from visible depth.
            // Keep the shader in its conservative transition mode for this
            // frame instead of publishing the replacement as fully complete.
            boolean cacheTransitioningThisFrame = anchorChanged
                    || !cacheWasReady
                    || !voxelHistoryValid;
            boolean worldChangeRefresh = voxelRefreshFrames.get() > 0;
            // VanillaDI updates the rolling volume every SKIP_FRAMES render
            // frames. Reusing it forever while the camera moves leaves the
            // fence/trapdoor mask behind the player and makes shadows appear
            // to pass through walls until a block interaction occurs.
            boolean collectThisFrame = worldChangeRefresh
                    || !voxelHistoryValid
                    || anchorChanged
                    || !cacheWasReady
                    || stateVolumeChanged
                    || voxelFrame % VOXEL_SKIP_FRAMES == 0;
            // The baked-model and depth paths are immutable between collection
            // frames. Keep their 2048² targets intact until a camera roll,
            // block-state update, resource reload, or scheduled collection
            // frame requires new data; the lighting pass can sample the cache
            // without rebuilding it every render frame.
            boolean rebuildVoxelCache = collectThisFrame || stateVolumeChanged
                    || !voxelLodValid;
            if (rebuildVoxelCache) {
                long voxelStarted = PerfTracker.begin(PerfTracker.Section.VOXEL);
                try {
                runVoxelizePass(voxelGrid, mainTarget.getDepthTextureId(),
                        normalTarget.getColorTextureId(), voxelHistory.getColorTextureId(),
                        projection, viewRotation, inverseProjection, inverseView,
                        cameraPosition, collectThisFrame, voxelHistoryValid);
                runBuildLodPass(voxelLod, voxelGrid.getColorTextureId());
                voxelLodValid = true;
                copyColor(voxelGrid, voxelHistory);
                voxelHistoryValid = true;
                } finally {
                    PerfTracker.end(PerfTracker.Section.VOXEL, voxelStarted);
                }
            }
            voxelFrame++;
            shadowFrame = (shadowFrame + 1.0F) % 1024.0F;
            if (worldChangeRefresh) {
                voxelRefreshFrames.updateAndGet(current -> Math.max(current - 1, 0));
            }
            voxelCache.commitGpuUpload(voxelGrid);

            // Only a cache that cannot be sampled at all invalidates radiance
            // history. Rolling the anchor by whole blocks does not: the volume
            // is world-aligned and reprojected exactly, and the radiance being
            // reprojected lives in screen space, so flushing it every time the
            // camera crossed a block boundary was throwing away the temporal
            // accumulation that keeps subvoxel shadows from flickering.
            if (!cacheWasReady) {
                historyValid = false;
            }
            int lightCount = lightData.upload(lights, cameraPosition, projection, viewRotation,
                    cameraPosition);
        applyUniforms(shader, lightCount, fovRadians, aspect, farPlane,
                    mainTarget.width, mainTarget.height, historyValid,
                    cacheTransitioningThisFrame, projection,
                    inverseProjection, inverseView, inverseViewProjection, cameraPosition);

            // VanillaDI keeps lighting as HDR radiance until the final blend.
            runLightingPass(lightPing, mainTarget.getDepthTextureId(),
                    normalTarget.getColorTextureId(), voxelGrid.getColorTextureId(),
                    voxelLod.getColorTextureId(), shader);

            // A debug view is only meaningful unfiltered: temporal reprojection
            // and the spatial blur are exactly what a diagnosis needs to
            // exclude, so show the lighting pass's own output and stop here.
            if (debugMode > 0) {
                copyColor(lightPing, mainTarget);
                historyValid = false;
                return true;
            }

            TextureTarget current = lightPing;
            if (filterEnabled) {
            // Temporal decides per pixel whether
            // the previous radiance is usable, then emits a fresh history frame
            // for the spatial passes. Keeping this target separate is important:
            // the next frame must sample pre-spatial temporal radiance.
            runFilterPass(Pass.LIGHT_TEMPORAL, lightPing, lightHistory,
                    temporalTarget, mainTarget.getDepthTextureId(), 0.0F,
                    inverseProjection, inverseView, inverseViewProjection,
                    previousProjection, previousView, cameraPosition,
                    previousCameraPosition);
            runFrameAgePass(frameScratch, previousFrame,
                    mainTarget.getDepthTextureId(), historyValid);
            current = temporalTarget;
            // VanillaDI performs four progressively wider edge-aware spatial
            // passes after temporal reprojection.  The first two preserve
            // fence/trapdoor holes; the wider passes remove the remaining
            // binary subvoxel grain in open areas.
            // VanillaDI runs four progressively wider edge-aware spatial passes
            // because its shading takes ONE stochastic sample per pixel per
            // frame, so its input is extremely noisy. This port takes
            // SL_SHADOW_SAMPLES rays per light and then accumulates temporally,
            // so it starts far cleaner and does not need the wide iterations --
            // and those are what dilute a trapdoor cutout's light patch, which
            // is only a handful of pixels across, into the surrounding shadow.
            int spatialPasses = Math.max(0, Math.min(2, AdaptiveVisualQuality.spatialPasses()));
            for (int pass = 0; pass < spatialPasses; pass++) {
                float radius = 1 << pass;
                TextureTarget destination = current == lightPing ? lightPong : lightPing;
                runFilterPass(Pass.LIGHT_SPATIAL, current, null, destination,
                        mainTarget.getDepthTextureId(), radius, inverseProjection,
                        inverseView, null, null, null, cameraPosition, null);
                current = destination;
            }
            if (TEMPORAL_REPROJECTION_ENABLED) {
                copyColor(temporalTarget, lightHistory);
                copyColor(frameScratch, previousFrame);
                copyColor(normalTarget, previousNormals);
                runCopyDepthPass(previousDepth, mainTarget.getDepthTextureId());
                historyValid = true;
            } else {
                historyValid = false;
            }
            // Publish the encoded metadata stream as VanillaDI's previousData
            // target after all passes have consumed the current frame.
            lightData.copyToHistory();
            previousCameraPosition = cameraPosition;
            previousCameraForward = new Vector3f(forward);
            previousProjection = new Matrix4f(projection);
            previousView = new Matrix4f(viewRotation);
            previousLightSignature = lightSignature;
            } else {
                // Re-enabling starts with fresh history instead of stale radiance.
                historyValid = false;
            }

            // Debug: blending the unfiltered lighting output separates a mask
            // that never had the cutout hole from one whose hole the denoiser
            // averaged away. Both look like a solid shadow on screen.
            // Snapshot depth before the final pass writes the main target. Sampling
            // its attached depth while drawing to it is an OpenGL feedback loop.
            copyDepth(mainTarget, currentDepth);
            runBlendPass(sceneCopy.getColorTextureId(),
                    current, mainTarget,
                    currentDepth.getDepthTextureId(), inverseProjection,
                    inverseView, cameraPosition, partialTick);
            if (!activeLogged) {
                activeLogged = true;
                ExampleMod.LOGGER.debug("Spell illumination active: {} light(s), voxel={}x{}, temporal+spatial=1, FOV={} deg",
                        lights.size(), VOXEL_WIDTH, VOXEL_HEIGHT,
                        Math.round(Math.toDegrees(fovRadians)));
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
            return false;
        } finally {
            snapshot.restore();
            PerfTracker.end(PerfTracker.Section.LIGHTING, lightingStarted);
        }
    }

    /** Toggles the temporal + spatial denoise chain; returns the new state. */
    public static boolean toggleFilter() {
        filterEnabled = !filterEnabled;
        historyValid = false;
        return filterEnabled;
    }

    /**
     * Reads back the GPU occupancy bitfield for one world block as ASCII, one
     * 8x8 plane per Z slice.
     *
     * <p>This is the only way to tell a mask that never recorded a cutout hole
     * from one that recorded it and had it filtered away downstream: the first
     * is a voxelizer problem, the second a denoiser problem, and they look
     * identical on screen.</p>
     */
    public static List<String> dumpVoxelBlock(BlockPos position) {
        if (!RenderSystem.isOnRenderThread()) {
            return List.of("Not on the render thread.");
        }
        if (voxelGrid == null || !voxelCache.isReady()) {
            return List.of("No voxel cache is live; cast a light or place a test light first.");
        }
        int localX = position.getX() - (int) voxelCache.originX();
        int localY = position.getY() - (int) voxelCache.originY();
        int localZ = position.getZ() - (int) voxelCache.originZ();
        if (localX < 0 || localY < 0 || localZ < 0
                || localX >= SpellLightVoxelGrid.SIZE
                || localY >= SpellLightVoxelGrid.SIZE
                || localZ >= SpellLightVoxelGrid.SIZE) {
            return List.of("Block is outside the 64^3 cache volume.");
        }

        int blockId = localX + localY * 64 + localZ * 4096;
        int texelX = (blockId % 128) * 16;
        int texelY = blockId / 128;

        java.nio.ByteBuffer pixels = java.nio.ByteBuffer
                .allocateDirect(16 * 4).order(java.nio.ByteOrder.nativeOrder());
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, voxelGrid.frameBufferId);
        GL11.glReadPixels(texelX, texelY, 16, 1,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);

        List<String> lines = new java.util.ArrayList<>();
        lines.add("Voxel mask for " + position.toShortString()
                + " (local " + localX + "," + localY + "," + localZ + ")");
        int occupied = 0;
        for (int subZ = 0; subZ < 8; subZ++) {
            StringBuilder plane = new StringBuilder("z=" + subZ + " ");
            for (int subY = 7; subY >= 0; subY--) {
                int slice = subZ * 2 + subY / 4;
                int channel = subY % 4;
                int rowBits = pixels.get(slice * 4 + channel) & 0xFF;
                for (int subX = 0; subX < 8; subX++) {
                    boolean set = (rowBits & (1 << subX)) != 0;
                    if (set) {
                        occupied++;
                    }
                    plane.append(set ? '#' : '.');
                }
                plane.append(subY > 0 ? " | " : "");
            }
            lines.add(plane.toString());
        }
        lines.add("occupied subvoxels: " + occupied + " / 512");
        return lines;
    }

    /**
     * Screen-space pass for every open Starless void.
     *
     * <p>The void is an absence, so it is drawn by operating on what is already
     * behind it rather than by rendering a sphere: no mesh, no render type, no new
     * core shader. It runs independently of {@link #render} because a void that
     * has not released yet contributes no light at all, and the light pass returns
     * early on an empty emitter list.</p>
     *
     * <p>Draining other spells' lights is not done here — that is a per-light
     * intensity scale in {@code SpellLightEmitter}, which makes each light, its
     * falloff and its cast shadows fade as one.</p>
     */
    public static void renderStarless(List<StarlessEntity> voids, Matrix4f projectionMatrix,
                                      Matrix4f viewMatrix, Camera camera, float partialTick) {
        if (runtimeDisabled || !SpellLightConfig.enabled || voids == null || voids.isEmpty()
                || projectionMatrix == null || viewMatrix == null || camera == null
                || !RenderSystem.isOnRenderThread()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.STARLESS);
        if (minecraft.level == null || mainTarget == null || shader == null
                || mainTarget.getDepthTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        try {
            ensureSceneCopy(mainTarget.width, mainTarget.height);
            ensureCurrentDepth(mainTarget.width, mainTarget.height);
            Matrix4f projection = new Matrix4f(projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f viewRotation = new Matrix4f(viewMatrix)
                    .setTranslation(0.0F, 0.0F, 0.0F);
            Vec3 cameraPosition = camera.getPosition();

            for (StarlessEntity entity : voids) {
                float radius = entity.getVoidRadius(partialTick);
                float drain = entity.getDrainStrength(partialTick);
                float flash = entity.getReleaseFlash(partialTick);
                if (radius <= 0.01F && flash <= 0.002F) {
                    continue;
                }

                Vec3 center = entity.voidCenter(partialTick);
                // OpenGL eye coordinates: -Z forward. Keep this form for the
                // projection below, and hand the shader the +Z-forward form its
                // depth reconstruction uses.
                Vector3f eye = viewRotation.transformPosition(new Vector3f(
                        (float) (center.x - cameraPosition.x),
                        (float) (center.y - cameraPosition.y),
                        (float) (center.z - cameraPosition.z)));
                Vector3f centreUv = projectToScreen(projection, eye);
                if (centreUv == null) {
                    continue;
                }
                // Screen radius by projecting a point one radius to view-space
                // right, rather than deriving it from the FOV: this stays correct
                // under any projection the game hands us, including mods that
                // change it.
                Vector3f edgeUv = projectToScreen(projection,
                        new Vector3f(eye.x() + radius, eye.y(), eye.z()));
                float screenRadius = edgeUv == null
                        ? 0.05F
                        : Math.max(Math.abs(edgeUv.x() - centreUv.x()), 0.0008F);

                float swallowed = Math.min(entity.getSwallowedCount(), 4) / 4.0F;
                float ingest = entity.getIngestProgress(partialTick);
                float time = (minecraft.level.getGameTime() % 4096L + partialTick) * 0.05F;

                copyColor(mainTarget, sceneCopy);
                copyDepth(mainTarget, currentDepth);
                mainTarget.bindWrite(true);
                configureFullscreenState();
                shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
                shader.setSampler("DepthSampler", currentDepth.getDepthTextureId());
                set(shader, "Params", mainTarget.width, mainTarget.height, 0.0F, 0.0F);
                set(shader, "Center1", eye.x(), eye.y(), -eye.z(), Math.max(radius, 0.0001F));
                set(shader, "Center2", centreUv.x(), centreUv.y(), screenRadius, swallowed);
                set(shader, "MiscParams", drain,
                        flash * entity.getReleaseScale(), time, ingest);
                set(shader, "InverseProjectionMat", inverseProjection);
                RenderSystem.setShader(() -> shader);
                drawFullscreenQuad();
            }
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
        } finally {
            snapshot.restore();
        }
    }

    /**
     * Screen-space refraction for the meteors closest to the camera.
     *
     * <p>A meteor's shock front is not a light source. Drawing one as additive
     * geometry ahead of the body never read as air being torn open, twice over:
     * the head is already the brightest thing on screen, so extra light in the
     * same place cannot be perceived as separate structure. What the eye does read
     * as compressed air is the background bending, which only screen space can
     * do.</p>
     *
     * <p>Restricted to the nearest few meteors, and only ones already close to the
     * ground: it is a fullscreen pass per meteor, and the shower has forty.</p>
     */
    public static void renderMeteorShocks(List<StarfallEntity> showers,
                                          Matrix4f projectionMatrix, Matrix4f viewMatrix,
                                          Camera camera, float partialTick) {
        if (runtimeDisabled || !SpellLightConfig.enabled || showers == null
                || showers.isEmpty() || projectionMatrix == null || viewMatrix == null
                || camera == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.METEOR_SHOCK);
        if (minecraft.level == null || mainTarget == null || shader == null
                || mainTarget.getDepthTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        List<MeteorShock> shocks = collectMeteorShocks(showers, cameraPosition, partialTick);
        if (shocks.isEmpty()) {
            return;
        }

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        try {
            ensureSceneCopy(mainTarget.width, mainTarget.height);
            ensureCurrentDepth(mainTarget.width, mainTarget.height);
            Matrix4f projection = new Matrix4f(projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f viewRotation = new Matrix4f(viewMatrix)
                    .setTranslation(0.0F, 0.0F, 0.0F);

            for (MeteorShock shock : shocks) {
                Vector3f eye = viewRotation.transformPosition(new Vector3f(
                        (float) (shock.head.x - cameraPosition.x),
                        (float) (shock.head.y - cameraPosition.y),
                        (float) (shock.head.z - cameraPosition.z)));
                Vector3f headUv = projectToScreen(projection, eye);
                if (headUv == null) {
                    continue;
                }
                // Screen radius from a projected offset rather than the FOV, so it
                // stays correct under any projection the game hands us.
                Vector3f edgeUv = projectToScreen(projection,
                        new Vector3f(eye.x() + shock.radius, eye.y(), eye.z()));
                float screenRadius = edgeUv == null
                        ? 0.03F
                        : Math.max(Math.abs(edgeUv.x() - headUv.x()), 0.0015F);

                // The heading in screen space, so the distortion can be biased
                // ahead of the body instead of wrapping it like a lens.
                Vector3f aheadEye = viewRotation.transformPosition(new Vector3f(
                        (float) (shock.head.x + shock.heading.x - cameraPosition.x),
                        (float) (shock.head.y + shock.heading.y - cameraPosition.y),
                        (float) (shock.head.z + shock.heading.z - cameraPosition.z)));
                Vector3f aheadUv = projectToScreen(projection, aheadEye);
                float axisX = aheadUv == null ? 0.0F : aheadUv.x() - headUv.x();
                float axisY = aheadUv == null ? -1.0F : aheadUv.y() - headUv.y();
                float axisLength = (float) Math.sqrt(axisX * axisX + axisY * axisY);
                if (axisLength < 1.0E-6F) {
                    axisX = 0.0F;
                    axisY = -1.0F;
                } else {
                    axisX /= axisLength;
                    axisY /= axisLength;
                }

                copyColor(mainTarget, sceneCopy);
                copyDepth(mainTarget, currentDepth);
                mainTarget.bindWrite(true);
                configureFullscreenState();
                shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
                shader.setSampler("DepthSampler", currentDepth.getDepthTextureId());
                set(shader, "Params", mainTarget.width, mainTarget.height, 0.0F, 0.0F);
                set(shader, "ShockHead", headUv.x(), headUv.y(),
                        screenRadius, shock.speed);
                set(shader, "ShockAxis", axisX, axisY, shock.strength, 0.0F);
                set(shader, "MiscParams", 0.0F, 0.0F, 0.0F, 0.0F);
                set(shader, "InverseProjectionMat", inverseProjection);
                RenderSystem.setShader(() -> shader);
                drawFullscreenQuad();
            }
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
        } finally {
            snapshot.restore();
        }
    }

    /**
     * The meteors worth a distortion pass: airborne, in front of the camera and
     * near it, strongest first, capped.
     */
    private static List<MeteorShock> collectMeteorShocks(List<StarfallEntity> showers,
                                                         Vec3 cameraPosition,
                                                         float partialTick) {
        List<MeteorShock> found = new java.util.ArrayList<>();
        for (StarfallEntity entity : showers) {
            float age = entity.getVisualAgeTicks(partialTick);
            for (int meteor = 0; meteor < StarfallShape.METEOR_COUNT; meteor++) {
                int spawn = StarfallEntity.spawnTick(meteor);
                if (age < spawn || age >= StarfallEntity.impactTick(meteor)) {
                    continue;
                }
                Vec3 head = entity.meteorPosition(meteor, partialTick);
                double distanceSqr = cameraPosition.distanceToSqr(head);
                if (distanceSqr > METEOR_SHOCK_DISTANCE * METEOR_SHOCK_DISTANCE) {
                    continue;
                }
                boolean finale = StarfallEntity.isFinaleMeteor(meteor);
                float fall = Mth.clamp((age - spawn)
                        / (float) StarfallEntity.fallTicks(meteor), 0.0F, 1.0F);
                // The shock only becomes worth drawing once it is moving fast.
                if (fall < 0.2F) {
                    continue;
                }
                float radius = (finale ? 6.5F : 1.5F) * (0.7F + fall * 0.6F);
                float strength = (finale ? 0.09F : 0.03F) * (0.4F + fall * 0.9F);
                found.add(new MeteorShock(head, entity.meteorHeading(meteor),
                        radius, fall, strength, distanceSqr));
            }
        }
        found.sort(Comparator.comparingDouble(MeteorShock::sortKey));
        return found.size() > MAX_METEOR_SHOCKS
                ? found.subList(0, MAX_METEOR_SHOCKS)
                : found;
    }

    private record MeteorShock(Vec3 head, Vec3 heading, float radius, float speed,
                               float strength, double distanceSqr) {
        /** Nearest first, so the cap keeps the ones that fill the most screen. */
        private double sortKey() {
            return distanceSqr;
        }
    }

    /**
     * Screen-space refraction for every live Singularity.
     *
     * <p>This is the effect, not a garnish on it. Some of FX's shock wave
     * (github.com/YangMao-Minister/some_of_fx, MIT, (c) 2026 Pizuka) returns a UV
     * displacement and only a trace of added colour, and that is the reason theirs
     * reads as a collapsing volume of space: a singularity is not light, it is the
     * world seen through something. Drawing the same thing as an additive shell
     * gives a glowing bubble instead.</p>
     */
    public static void renderSingularityLenses(List<SingularityEntity> effects,
                                               Matrix4f projectionMatrix,
                                               Matrix4f viewMatrix, Camera camera,
                                               float partialTick) {
        if (runtimeDisabled || !SpellLightConfig.enabled || effects == null
                || effects.isEmpty() || projectionMatrix == null || viewMatrix == null
                || camera == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.SINGULARITY_LENS);
        if (minecraft.level == null || mainTarget == null || shader == null
                || mainTarget.getDepthTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        try {
            ensureSceneCopy(mainTarget.width, mainTarget.height);
            ensureCurrentDepth(mainTarget.width, mainTarget.height);
            Matrix4f projection = new Matrix4f(projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f viewRotation = new Matrix4f(viewMatrix)
                    .setTranslation(0.0F, 0.0F, 0.0F);
            Vec3 cameraPosition = camera.getPosition();

            for (SingularityEntity entity : effects) {
                Vec3 centre = entity.coreCentre(partialTick);
                Vector3f eye = viewRotation.transformPosition(new Vector3f(
                        (float) (centre.x - cameraPosition.x),
                        (float) (centre.y - cameraPosition.y),
                        (float) (centre.z - cameraPosition.z)));
                Vector3f centreUv = projectToScreen(projection, eye);
                if (centreUv == null) {
                    continue;
                }

                for (SingularityEntity.Lens lens : entity.lenses(partialTick)) {
                    copyColor(mainTarget, sceneCopy);
                    copyDepth(mainTarget, currentDepth);
                    mainTarget.bindWrite(true);
                    configureFullscreenState();
                    shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
                    shader.setSampler("DepthSampler", currentDepth.getDepthTextureId());
                    set(shader, "Params", mainTarget.width, mainTarget.height,
                            0.0F, 0.0F);
                    set(shader, "MiscParams", centreUv.x(), centreUv.y(), 0.0F, 0.0F);
                    set(shader, "LensCentre", eye.x(), eye.y(), eye.z(),
                            Math.max(lens.radius(), 0.0001F));
                    set(shader, "LensShape", lens.amplitude(), lens.ripple(),
                            lens.life(), lens.edge());
                    set(shader, "InverseProjectionMat", inverseProjection);
                    RenderSystem.setShader(() -> shader);
                    drawFullscreenQuad();
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
        } finally {
            snapshot.restore();
        }
    }

    /**
     * Gargantua: the disk, the shadow and the lensed background, in one pass.
     *
     * <p>Unlike every other effect in this mod, none of this is geometry. The disk's
     * far side has to appear bent up over the shadow and down under it, which is
     * multiple imaging rather than distortion — the same piece of disk seen twice
     * along two different bent paths. No mesh can express that, because the shape
     * depends on where the viewer is standing. So the pass integrates a photon
     * geodesic per pixel and samples the disk wherever the bent ray cuts its plane.
     * See the shader include for the equation and the published radii.</p>
     */
    public static void renderGargantua(List<GargantuaEntity> effects,
                                       Matrix4f projectionMatrix,
                                       Matrix4f viewMatrix, Camera camera,
                                       float partialTick) {
        if (runtimeDisabled || !SpellLightConfig.enabled || effects == null
                || effects.isEmpty() || projectionMatrix == null || viewMatrix == null
                || camera == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.GARGANTUA_LENS);
        if (minecraft.level == null || mainTarget == null || shader == null
                || mainTarget.getDepthTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        long lensStarted = PerfTracker.begin(PerfTracker.Section.CINEMATIC);
        try {
            ensureSceneCopy(mainTarget.width, mainTarget.height);
            ensureCurrentDepth(mainTarget.width, mainTarget.height);
            Matrix4f projection = new Matrix4f(projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f viewRotation = new Matrix4f(viewMatrix)
                    .setTranslation(0.0F, 0.0F, 0.0F);
            Vec3 cameraPosition = camera.getPosition();

            for (GargantuaEntity entity : effects) {
                float radius = entity.gravitationalRadius(partialTick);
                float brightness = entity.brightness(partialTick);
                if (radius <= 0.01F || brightness <= 0.01F) {
                    continue;
                }
                Vec3 centre = entity.centre(partialTick);
                Vector3f eye = viewRotation.transformPosition(new Vector3f(
                        (float) (centre.x - cameraPosition.x),
                        (float) (centre.y - cameraPosition.y),
                        (float) (centre.z - cameraPosition.z)));
                // The spin axis is a direction, so only the rotation applies to it.
                Vec3 axis = entity.spinAxis();
                Vector3f axisEye = viewRotation.transformDirection(new Vector3f(
                        (float) axis.x, (float) axis.y, (float) axis.z)).normalize();

                ScreenEffectBounds bounds = ScreenEffectBounds.sphere(projection, eye,
                        radius * GargantuaEntity.DISK_OUTER_RADIUS * 1.35F,
                        mainTarget.width, mainTarget.height);
                if (bounds.empty()) continue;

                copyColor(mainTarget, sceneCopy);
                copyDepth(mainTarget, currentDepth);
                mainTarget.bindWrite(true);
                configureFullscreenState();
                shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
                shader.setSampler("DepthSampler", currentDepth.getDepthTextureId());
                set(shader, "Params", mainTarget.width, mainTarget.height, 0.0F, 0.0F);
                set(shader, "HoleCentre", eye.x(), eye.y(), eye.z(), radius);
                // The disk's structure comes from this: noise modulates its local
                // thickness, which gives torn wispy edges instead of a hard ring
                // with blotches painted on it.
                shader.setSampler("NoiseSampler", noiseTextureId());
                set(shader, "SpinAxis", axisEye.x(), axisEye.y(), axisEye.z(),
                        GargantuaEntity.SPIN);
                set(shader, "HoleState", entity.opened(partialTick),
                        entity.criticality(partialTick),
                        SpellBoltRenderer.boltTime(),
                        entity.getSwallowedCount());
                set(shader, "DiskShape", GargantuaEntity.DISK_INNER_RADIUS,
                        GargantuaEntity.DISK_OUTER_RADIUS, AdaptiveVisualQuality.decorationScale(), brightness);
                set(shader, "InverseProjectionMat", inverseProjection);
                set(shader, "ProjectionMat", projection);
                RenderSystem.setShader(() -> shader);
                RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                drawFullscreenQuad(PerfTracker.Section.CINEMATIC);
            }
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
        } finally {
            snapshot.restore();
            PerfTracker.end(PerfTracker.Section.CINEMATIC, lensStarted);
        }
    }

    /**
     * The Cosmic Horseshoe: an arc of lensed galaxy light, plus the scene bent round it.
     *
     * <p>Cheaper than Gargantua by a wide margin, and for a structural reason rather
     * than by tuning. Gargantua integrates a photon geodesic and marches up to 96 steps
     * per pixel because its signature is multiple imaging of its own disk. A lens map is
     * closed form, so this is one evaluation and one extra texture read per pixel.</p>
     */
    public static void renderCosmicHorseshoe(List<CosmicHorseshoeEntity> effects,
                                             Matrix4f projectionMatrix,
                                             Matrix4f viewMatrix, Camera camera,
                                             float partialTick) {
        if (runtimeDisabled || !SpellLightConfig.enabled || effects == null
                || effects.isEmpty() || projectionMatrix == null || viewMatrix == null
                || camera == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ShaderInstance shader =
                GeminiKillEffectPostShaders.shader(Pass.COSMIC_HORSESHOE_LENS);
        if (minecraft.level == null || mainTarget == null || shader == null
                || mainTarget.getDepthTextureId() < 0
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        long lensStarted = PerfTracker.begin(PerfTracker.Section.CINEMATIC);
        try {
            ensureSceneCopy(mainTarget.width, mainTarget.height);
            ensureCurrentDepth(mainTarget.width, mainTarget.height);
            Matrix4f projection = new Matrix4f(projectionMatrix);
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Matrix4f viewRotation = new Matrix4f(viewMatrix)
                    .setTranslation(0.0F, 0.0F, 0.0F);
            Vec3 cameraPosition = camera.getPosition();

            for (CosmicHorseshoeEntity entity : effects) {
                float brightness = entity.brightness(partialTick);
                if (brightness <= 0.01F) {
                    continue;
                }
                Vec3 centre = entity.centre(partialTick);
                Vector3f eye = viewRotation.transformPosition(new Vector3f(
                        (float) (centre.x - cameraPosition.x),
                        (float) (centre.y - cameraPosition.y),
                        (float) (centre.z - cameraPosition.z)));
                // A direction, so only the rotation applies to it.
                Vec3 gap = entity.gapDirection();
                Vector3f gapEye = viewRotation.transformDirection(new Vector3f(
                        (float) gap.x, (float) gap.y, (float) gap.z)).normalize();

                // Matches the finite six-Einstein-radius support in the shader.
                ScreenEffectBounds bounds = ScreenEffectBounds.sphere(projection, eye,
                        CosmicHorseshoeEntity.EINSTEIN_RADIUS * 6.0F,
                        mainTarget.width, mainTarget.height);
                if (bounds.empty()) continue;

                copyColor(mainTarget, sceneCopy);
                copyDepth(mainTarget, currentDepth);
                mainTarget.bindWrite(true);
                configureFullscreenState();
                shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
                shader.setSampler("DepthSampler", currentDepth.getDepthTextureId());
                set(shader, "Params", mainTarget.width, mainTarget.height, 0.0F, 0.0F);
                set(shader, "LensCentre", eye.x(), eye.y(), eye.z(),
                        CosmicHorseshoeEntity.EINSTEIN_RADIUS);
                // The axis ratio is what makes this a horseshoe rather than two matching
                // arcs. 0.72 is typical of the ellipticals that lens like this.
                set(shader, "GapDir", gapEye.x(), gapEye.y(), gapEye.z(), 0.72F);
                set(shader, "LensState", entity.aligned(partialTick), brightness,
                        SpellBoltRenderer.boltTime(),
                        // A stable per-cast phase for the clump placement, kept small so
                        // the hash stays where sin has precision left.
                        (entity.getSeed() & 0xFFFF) * 0.0011F);
                set(shader, "SourceShape", CosmicHorseshoeEntity.SOURCE_RADIUS,
                        CosmicHorseshoeEntity.SOURCE_OFFSET, 0.55F, 3.4F);
                set(shader, "InverseProjectionMat", inverseProjection);
                set(shader, "ProjectionMat", projection);
                RenderSystem.setShader(() -> shader);
                RenderSystem.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                drawFullscreenQuad(PerfTracker.Section.CINEMATIC);
            }
        } catch (RuntimeException | LinkageError failure) {
            disableAfterFailure(failure);
        } finally {
            snapshot.restore();
            PerfTracker.end(PerfTracker.Section.CINEMATIC, lensStarted);
        }
    }

    /**
     * GL id of the shared noise tile.
     *
     * <p>The single-argument lookup matters. Its two-argument sibling returns the
     * fallback when the texture has never been used, and the noise tile is otherwise
     * only touched by the lightning renderer — so in any scene without lightning this
     * handed back id 0, the sampler read black, and the disk's noise cutoff then
     * discarded every single sample. The disk rendered as nothing at all. This
     * overload registers and loads on demand instead.</p>
     *
     * <p>Resolved every frame rather than cached, because a resource reload replaces
     * the texture object and a stale id fails the same silent way.</p>
     */
    private static int noiseTextureId() {
        return Minecraft.getInstance().getTextureManager()
                .getTexture(SpellBoltRenderer.NOISE).getId();
    }

    /**
     * Projects an OpenGL eye-space point to screen UV, or null when it is at or
     * behind the eye plane and has no meaningful screen position.
     */
    @org.jetbrains.annotations.Nullable
    private static Vector3f projectToScreen(Matrix4f projection, Vector3f eye) {
        org.joml.Vector4f clip = projection.transform(
                new org.joml.Vector4f(eye.x(), eye.y(), eye.z(), 1.0F));
        if (clip.w() <= 0.000001F) {
            return null;
        }
        return new Vector3f(
                clip.x() / clip.w() * 0.5F + 0.5F,
                clip.y() / clip.w() * 0.5F + 0.5F,
                clip.z() / clip.w() * 0.5F + 0.5F);
    }

    public static void release() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(SpellLightPostProcessor::release);
            return;
        }
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        if (currentDepth != null) {
            currentDepth.destroyBuffers();
            currentDepth = null;
        }
        destroyVoxelTargets();
        voxelCache.release();
        stateVolume.release();
        maskAtlas.release();
        destroyLightTargets();
        lightData.release();
        voxelHistoryValid = false;
        voxelLodValid = false;
        voxelFrame = 0;
        voxelRefreshFrames.set(0);
        historyValid = false;
        finish(fullscreenBuffer);
        runtimeDisabled = false;
        failureLogged = false;
        activeLogged = false;
        previousCameraPosition = null;
        previousCameraForward = null;
        previousProjection = null;
        previousView = null;
        previousLightSignature = 0L;
    }

    private static void ensureSceneCopy(int width, int height) {
        if (sceneCopy != null && sceneCopy.width == width && sceneCopy.height == height) {
            return;
        }
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
        }
        sceneCopy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        sceneCopy.setFilterMode(GL11.GL_LINEAR);
    }

    /**
     * Target holding this frame's depth, with its own depth attachment so the
     * final blend can sample depth while drawing to the main target.
     */
    private static void ensureCurrentDepth(int width, int height) {
        if (currentDepth != null && currentDepth.width == width
                && currentDepth.height == height) {
            return;
        }
        if (currentDepth != null) {
            currentDepth.destroyBuffers();
        }
        currentDepth = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        currentDepth.setFilterMode(GL11.GL_NEAREST);
    }

    private static void ensureVoxelGrid() {
        if (voxelGrid != null
                && voxelGrid.width == VOXEL_WIDTH
                && voxelGrid.height == VOXEL_HEIGHT
                && voxelHistory != null
                && voxelHistory.width == VOXEL_WIDTH
                && voxelHistory.height == VOXEL_HEIGHT) {
            return;
        }
        if (voxelGrid != null) {
            voxelGrid.destroyBuffers();
        }
        if (voxelHistory != null) {
            voxelHistory.destroyBuffers();
        }
        voxelGrid = new TextureTarget(VOXEL_WIDTH, VOXEL_HEIGHT, false, Minecraft.ON_OSX);
        voxelGrid.setFilterMode(GL11.GL_NEAREST);
        voxelGrid.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        voxelGrid.clear(Minecraft.ON_OSX);
        voxelHistory = new TextureTarget(VOXEL_WIDTH, VOXEL_HEIGHT, false, Minecraft.ON_OSX);
        voxelHistory.setFilterMode(GL11.GL_NEAREST);
        voxelHistory.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        voxelHistory.clear(Minecraft.ON_OSX);
        voxelHistoryValid = false;
        voxelFrame = 0;
        voxelCache.invalidateGpuUpload();
    }

    private static void ensureVoxelLod() {
        if (voxelLod != null && voxelLod.width == VOXEL_LOD_WIDTH
                && voxelLod.height == VOXEL_LOD_HEIGHT) {
            return;
        }
        if (voxelLod != null) {
            voxelLod.destroyBuffers();
        }
        voxelLod = new TextureTarget(VOXEL_LOD_WIDTH, VOXEL_LOD_HEIGHT, false, Minecraft.ON_OSX);
        voxelLod.setFilterMode(GL11.GL_NEAREST);
        voxelLod.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        voxelLod.clear(Minecraft.ON_OSX);
        voxelLodValid = false;
    }

    private static void ensureNormalTarget(int width, int height) {
        if (normalTarget != null && normalTarget.width == width && normalTarget.height == height) {
            return;
        }
        if (normalTarget != null) {
            normalTarget.destroyBuffers();
        }
        normalTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        normalTarget.setFilterMode(GL11.GL_NEAREST);
        normalTarget.setClearColor(0.5F, 0.5F, 0.0F, 1.0F);
    }

    private static void destroyVoxelTargets() {
        if (voxelGrid != null) {
            voxelGrid.destroyBuffers();
            voxelGrid = null;
        }
        if (voxelHistory != null) {
            voxelHistory.destroyBuffers();
            voxelHistory = null;
        }
        if (voxelLod != null) {
            voxelLod.destroyBuffers();
            voxelLod = null;
        }
        if (normalTarget != null) {
            normalTarget.destroyBuffers();
            normalTarget = null;
        }
        voxelHistoryValid = false;
        voxelLodValid = false;
        voxelFrame = 0;
        voxelRefreshFrames.set(0);
        voxelCache.invalidateGpuUpload();
    }

    private static void ensureLightTargets(int width, int height) {
        if (lightPing != null && lightPing.width == width && lightPing.height == height
                && lightPong != null && temporalTarget != null && lightHistory != null
                && previousDepth != null && previousNormals != null
                && previousFrame != null && frameScratch != null) {
            return;
        }
        destroyLightTargets();
        lightPing = createHdrTarget(width, height);
        lightPong = createHdrTarget(width, height);
        temporalTarget = createHdrTarget(width, height);
        lightHistory = createHdrTarget(width, height);
        previousDepth = createColorTarget(width, height);
        previousNormals = createColorTarget(width, height);
        previousFrame = createColorTarget(width, height);
        frameScratch = createColorTarget(width, height);
        historyValid = false;
    }

    private static TextureTarget createHdrTarget(int width, int height) {
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
        TextureUpload.image2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        target.checkStatus();
        target.clear(Minecraft.ON_OSX);
        return target;
    }

    private static TextureTarget createColorTarget(int width, int height) {
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        GlStateManager._bindTexture(target.getColorTextureId());
        target.setFilterMode(GL11.GL_NEAREST);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(Minecraft.ON_OSX);
        return target;
    }

    private static void destroyLightTargets() {
        if (lightPing != null) {
            lightPing.destroyBuffers();
            lightPing = null;
        }
        if (lightPong != null) {
            lightPong.destroyBuffers();
            lightPong = null;
        }
        if (temporalTarget != null) {
            temporalTarget.destroyBuffers();
            temporalTarget = null;
        }
        if (lightHistory != null) {
            lightHistory.destroyBuffers();
            lightHistory = null;
        }
        if (previousDepth != null) {
            previousDepth.destroyBuffers();
            previousDepth = null;
        }
        if (previousNormals != null) {
            previousNormals.destroyBuffers();
            previousNormals = null;
        }
        if (previousFrame != null) {
            previousFrame.destroyBuffers();
            previousFrame = null;
        }
        if (frameScratch != null) {
            frameScratch.destroyBuffers();
            frameScratch = null;
        }
    }

    private static void applyUniforms(ShaderInstance shader, int lightCount,
                                       float fovRadians, float aspect, float farPlane,
                                       int width, int height, boolean validHistory,
                                       boolean cacheTransitioning,
                                       Matrix4f projection, Matrix4f inverseProjection,
                                       Matrix4f inverseView, Matrix4f inverseViewProjection,
                                       Vec3 cameraPosition) {
        set(shader, "Params", width, height, debugMode, 0.0F);
        // w is a per-frame counter for the stochastic shadow sampler. It has to
        // advance every rendered frame, not every game tick: the temporal pass
        // is what averages the samples, so a seed that only changes with the
        // game clock would repeat the same sample set for a whole tick and
        // freeze the noise instead of resolving it. Wrapped to keep float
        // precision exact.
        set(shader, "TimePack", System.currentTimeMillis() / 1_000.0F,
                Minecraft.getInstance().level == null ? 0.0F
                        : Minecraft.getInstance().level.getGameTime(),
                validHistory ? 1.0F : 0.0F, shadowFrame);
        set(shader, "CameraParams", fovRadians, aspect, NEAR_PLANE, farPlane);
        set(shader, "MiscParams", (float) lightCount,
                AdaptiveVisualQuality.shadowSteps(SpellLightConfig.shadowSteps), 1.0F, 0.0F);
        set(shader, "LightDataParams", lightData.width(), lightData.height(),
                lightCount, validHistory ? 1.0F : 0.0F);
        set(shader, "ProjectionMat", projection);
        set(shader, "InverseProjectionMat", inverseProjection);
        set(shader, "InverseViewMat", inverseView);
        set(shader, "InverseViewProjectionMat", inverseViewProjection);
        set(shader, "CameraWorldPos", (float) cameraPosition.x,
                (float) cameraPosition.y, (float) cameraPosition.z, 1.0F);
        set(shader, "VoxelOrigin",
                voxelCache.originX(),
                voxelCache.originY(),
                voxelCache.originZ(),
                voxelCache.isReady()
                        ? (cacheTransitioning || voxelCache.isTransitioning() ? 2.0F : 1.0F)
                        : 0.0F);
    }

    private static void runLightingPass(TextureTarget destination, int depthTexture,
                                         int normalTexture, int voxelTexture, int voxelLodTexture,
                                         ShaderInstance shader) {
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("DepthSampler", depthTexture);
        shader.setSampler("NormalSampler", normalTexture);
        shader.setSampler("VoxelSampler", voxelTexture);
        shader.setSampler("VoxelLodSampler", voxelLodTexture);
        shader.setSampler("LightDataSampler", lightData.textureId());
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static void runNormalsPass(TextureTarget destination, int depthTexture,
                                       Matrix4f inverseProjection) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.LIGHT_NORMALS);
        if (shader == null) {
            throw new IllegalStateException("Missing spell light normal shader");
        }
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("DepthSampler", depthTexture);
        set(shader, "Params", destination.width, destination.height, 0.0F, 0.0F);
        set(shader, "InverseProjectionMat", inverseProjection);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static void runBuildLodPass(TextureTarget destination, int voxelTexture) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.LIGHT_BUILD_LOD);
        if (shader == null) {
            throw new IllegalStateException("Missing spell light voxel LOD shader");
        }
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("VoxelSampler", voxelTexture);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad(PerfTracker.Section.VOXEL);
    }

    private static void runVoxelizePass(TextureTarget destination, int depthTexture,
                                        int normalTexture, int historyTexture,
                                        Matrix4f projection, Matrix4f viewRotation,
                                        Matrix4f inverseProjection, Matrix4f inverseView,
                                        Vec3 cameraPosition, boolean collectThisFrame,
                                        boolean validHistory) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.LIGHT_VOXELIZE);
        if (shader == null) {
            throw new IllegalStateException("Missing spell light voxelize shader");
        }
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("DepthSampler", depthTexture);
        shader.setSampler("NormalSampler", normalTexture);
        shader.setSampler("VoxelCacheSampler", historyTexture);
        shader.setSampler("StateVolumeSampler", stateVolume.textureId());
        shader.setSampler("ModelMaskSampler", maskAtlas.textureId());
        set(shader, "StateVolumeWrap", stateVolume.wrapX(), stateVolume.wrapY(),
                stateVolume.wrapZ(), stateVolume.isReady() ? 1.0F : 0.0F);
        set(shader, "CameraWorldPos", (float) cameraPosition.x,
                (float) cameraPosition.y, (float) cameraPosition.z, 1.0F);
        set(shader, "VoxelOrigin", voxelCache.currentOriginX(),
                voxelCache.currentOriginY(), voxelCache.currentOriginZ(), 1.0F);
        set(shader, "PreviousVoxelOrigin", voxelCache.originX(),
                voxelCache.originY(), voxelCache.originZ(),
                validHistory && voxelCache.isReady() ? 1.0F : 0.0F);
        set(shader, "CacheParams", collectThisFrame ? 1.0F : 0.0F,
                validHistory ? 1.0F : 0.0F, 0.0F, 0.0F);
        set(shader, "ProjectionMat", projection);
        set(shader, "ViewMat", viewRotation);
        set(shader, "InverseProjectionMat", inverseProjection);
        set(shader, "InverseViewMat", inverseView);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad(PerfTracker.Section.VOXEL);
    }

    private static void runFilterPass(Pass pass, TextureTarget source, TextureTarget history,
                                      TextureTarget destination, int depthTexture, float radius,
                                      Matrix4f inverseProjection, Matrix4f inverseView,
                                      Matrix4f inverseViewProjection,
                                      Matrix4f previousProjection, Matrix4f previousView,
                                      Vec3 cameraPosition, Vec3 previousCameraPosition) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(pass);
        if (shader == null) {
            throw new IllegalStateException("Missing spell light filter shader: " + pass);
        }
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("SceneSampler", source.getColorTextureId());
        if (history != null && pass != Pass.LIGHT_TEMPORAL) {
            shader.setSampler("BloomSampler", history.getColorTextureId());
        }
        if ((pass == Pass.LIGHT_SPATIAL || pass == Pass.LIGHT_TEMPORAL)
                && depthTexture >= 0) {
            shader.setSampler("DepthSampler", depthTexture);
        }
        if ((pass == Pass.LIGHT_SPATIAL || pass == Pass.LIGHT_TEMPORAL)
                && normalTarget != null) {
            shader.setSampler("NormalSampler", normalTarget.getColorTextureId());
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (pass == Pass.LIGHT_SPATIAL || pass == Pass.LIGHT_TEMPORAL) {
            // Both filters address history texels directly, so they need the
            // destination's pixel dimensions.
            set(shader, "Params", destination.width, destination.height, 0.0F, 0.0F);
        }
        if (pass == Pass.LIGHT_SPATIAL && minecraft.level != null) {
            set(shader, "CameraParams",
                    currentFovRadians(minecraft),
                    destination.width / (float) Math.max(destination.height, 1),
                    NEAR_PLANE,
                    Math.max(minecraft.gameRenderer.getDepthFar(), 64.0F));
        }
        if (pass == Pass.LIGHT_SPATIAL) {
            set(shader, "InverseProjectionMat", inverseProjection);
            set(shader, "Step", radius);
        }
        if (pass == Pass.LIGHT_TEMPORAL && inverseViewProjection != null
                && previousProjection != null && previousView != null
                && cameraPosition != null && previousCameraPosition != null) {
            if (previousNormals != null) {
                shader.setSampler("PreviousNormalSampler", previousNormals.getColorTextureId());
            }
            if (previousDepth != null) {
                shader.setSampler("PreviousDepthSampler", previousDepth.getColorTextureId());
            }
            if (previousFrame != null) {
                shader.setSampler("PreviousFrameSampler", previousFrame.getColorTextureId());
            }
            if (history != null) {
                shader.setSampler("PreviousRadianceSampler", history.getColorTextureId());
            }
            set(shader, "InverseViewProjectionMat", inverseViewProjection);
            set(shader, "InverseViewMat", inverseView);
            set(shader, "PreviousProjectionMat", previousProjection);
            set(shader, "PreviousViewMat", previousView);
            set(shader, "CameraWorldPos", (float) cameraPosition.x,
                    (float) cameraPosition.y, (float) cameraPosition.z, 1.0F);
            set(shader, "PreviousCameraWorldPos", (float) previousCameraPosition.x,
                    (float) previousCameraPosition.y, (float) previousCameraPosition.z, 1.0F);
        }
        set(shader, "MiscParams", 0.0F,
                Math.max(8.0F, SpellLightConfig.shadowSteps), radius,
                historyValid ? 1.0F : 0.0F);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static void runFrameAgePass(TextureTarget destination, TextureTarget previous,
                                        int depthTexture, boolean validHistory) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.LIGHT_COPY_FRAME);
        if (shader == null) {
            throw new IllegalStateException("Missing spell light frame-history shader");
        }
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("DepthSampler", depthTexture);
        shader.setSampler("PreviousFrameSampler", previous.getColorTextureId());
        set(shader, "MiscParams", validHistory ? 1.0F : 0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static void runCopyDepthPass(TextureTarget destination, int depthTexture) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.LIGHT_COPY_DEPTH);
        if (shader == null) {
            throw new IllegalStateException("Missing spell light depth-history shader");
        }
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("DepthSampler", depthTexture);
        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static void runBlendPass(int sceneTexture, TextureTarget radiance,
                                     RenderTarget destination, int depthTexture,
                                     Matrix4f inverseProjection, Matrix4f inverseView,
                                     Vec3 cameraPosition, float partialTick) {
        ShaderInstance shader = GeminiKillEffectPostShaders.shader(Pass.LIGHT_BLEND);
        if (shader == null) {
            throw new IllegalStateException("Missing spell light blend shader");
        }
        destination.bindWrite(true);
        configureFullscreenState();
        shader.setSampler("SceneSampler", sceneTexture);
        shader.setSampler("BloomSampler", radiance.getColorTextureId());
        shader.setSampler("DepthSampler", depthTexture);

        // Silhouette turns the light/shadow relationship inside out. It is
        // applied here because this is the one pass holding both the scene and
        // the spell-light radiance, which is exactly the pair the inversion
        // needs; doing it in its own pass would mean re-deriving both.
        SilhouetteEntity field = SpellLightEmitter.strongestSilhouette(partialTick);
        float inversion = field == null ? 0.0F : field.getInversionStrength(partialTick);
        set(shader, "MiscParams", inversion, 0.0F, 0.0F, 0.0F);
        if (field != null && inversion > 0.001F) {
            Vec3 centre = field.fieldCenter(partialTick);
            set(shader, "SilhouetteField", (float) centre.x, (float) centre.y,
                    (float) centre.z, SilhouetteEntity.FIELD_RADIUS);
        } else {
            set(shader, "SilhouetteField", 0.0F, 0.0F, 0.0F, 0.0F);
        }
        set(shader, "InverseProjectionMat", inverseProjection);
        set(shader, "InverseViewMat", inverseView);
        set(shader, "CameraWorldPos", (float) cameraPosition.x,
                (float) cameraPosition.y, (float) cameraPosition.z, 1.0F);

        RenderSystem.setShader(() -> shader);
        drawFullscreenQuad();
    }

    private static float currentFovRadians(Minecraft minecraft) {
        int fov = minecraft.options.fov().get();
        return (float) Math.toRadians(Mth.clamp(fov, 30, 110));
    }

    private static void set(ShaderInstance shader, String name, float x, float y, float z, float w) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(x, y, z, w);
        }
    }

    private static void set(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static void set(ShaderInstance shader, String name, Matrix4f value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static void configureFullscreenState() {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawFullscreenQuad() {
        drawFullscreenQuad(PerfTracker.Section.LIGHTING);
    }

    private static void drawFullscreenQuad(PerfTracker.Section section) {
        finish(fullscreenBuffer);
        BufferBuilder builder = fullscreenBuffer;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        try {
            BufferBuilder.RenderedBuffer rendered = builder.end();
            int gpu = PerfTracker.beginGpu(section);
            try {
                BufferUploader.drawWithShader(rendered);
            } finally {
                PerfTracker.endGpu(gpu);
            }
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        }
    }

    private static void finish(BufferBuilder builder) {
        if (builder == null || !builder.building()) {
            return;
        }
        try {
            BufferBuilder.RenderedBuffer rendered = builder.end();
            if (rendered != null) {
                rendered.release();
            }
        } catch (RuntimeException ignored) {
            if (builder == fullscreenBuffer) {
                fullscreenBuffer = new BufferBuilder(4_096);
            }
        }
    }

    private static void copyColor(RenderTarget source, RenderTarget destination) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, source.width, source.height,
                0, 0, destination.width, destination.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /**
     * Blits depth into an independently attached depth texture. Both targets come
     * from vanilla's own depth format, so the copy is exact and full precision --
     * which the packed-colour depth history is not.
     */
    private static void copyDepth(RenderTarget source, RenderTarget destination) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
                0, 0, source.width, source.height,
                0, 0, destination.width, destination.height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void disableAfterFailure(Throwable failure) {
        runtimeDisabled = true;
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        if (currentDepth != null) {
            currentDepth.destroyBuffers();
            currentDepth = null;
        }
        destroyVoxelTargets();
        voxelCache.release();
        stateVolume.release();
        maskAtlas.release();
        destroyLightTargets();
        lightData.release();
        historyValid = false;
        if (!failureLogged) {
            failureLogged = true;
            ExampleMod.LOGGER.error(
                    "Spell screen lighting failed; disabling the shared light pass for this session",
                    failure);
        }
    }

    private static boolean cameraStable(Vec3 position, Vector3f forward,
                                        Matrix4f projection) {
        if (previousCameraPosition == null || previousCameraForward == null
                || previousProjection == null || previousView == null) {
            return false;
        }
        // Ordinary movement and ordinary mouse look go through world-space
        // reprojection, so only a teleport, a hard turn or a projection change
        // may flush history. The view matrix is deliberately not compared
        // element-wise: any mouse movement exceeds a 0.05 tolerance, which made
        // this return false on nearly every frame and disabled temporal
        // accumulation altogether.
        return position.distanceTo(previousCameraPosition) < 8.0D
                && forward.dot(previousCameraForward) > 0.72F
                && previousProjection.equals(projection, 0.05F);
    }

    /**
     * Identity of the light set for temporal-history purposes. Intensity and
     * colour are excluded on purpose: spell lights animate their brightness
     * every frame, and treating that as a new light set flushed the radiance
     * history continuously. Only a changed light count or a light that jumped a
     * whole block invalidates reprojection.
     */
    private static long signature(List<SpellLightEmitter.Light> lights) {
        long value = 0xcbf29ce484222325L;
        value ^= lights.size();
        value *= 0x100000001b3L;
        for (SpellLightEmitter.Light light : lights) {
            value ^= Mth.floor(light.position().x);
            value *= 0x100000001b3L;
            value ^= Mth.floor(light.position().y);
            value *= 0x100000001b3L;
            value ^= Mth.floor(light.position().z);
            value *= 0x100000001b3L;
        }
        return value;
    }

    private static final class RenderStateSnapshot {
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;
        private final int scissorX;
        private final int scissorY;
        private final int scissorWidth;
        private final int scissorHeight;
        private final boolean blend;
        private final boolean depth;
        private final boolean cull;
        private final boolean scissor;
        private final boolean depthWrite;
        private final int srcRgb;
        private final int dstRgb;
        private final int srcAlpha;
        private final int dstAlpha;
        private final int activeTexture;
        private final int[] shaderTextures;
        private final int[] boundTextures;
        private final int program;
        private final ShaderInstance shader;
        private final float[] shaderColor;

        private RenderStateSnapshot(int readFramebuffer, int drawFramebuffer,
                                    int[] viewport, int[] scissorBox,
                                    boolean blend, boolean depth, boolean cull,
                                    boolean scissor, boolean depthWrite, int srcRgb, int dstRgb,
                                    int srcAlpha, int dstAlpha, int activeTexture,
                                    int[] shaderTextures, int[] boundTextures, int program,
                                    ShaderInstance shader, float[] shaderColor) {
            this.readFramebuffer = readFramebuffer;
            this.drawFramebuffer = drawFramebuffer;
            this.viewportX = viewport[0];
            this.viewportY = viewport[1];
            this.viewportWidth = viewport[2];
            this.viewportHeight = viewport[3];
            this.scissorX = scissorBox[0];
            this.scissorY = scissorBox[1];
            this.scissorWidth = scissorBox[2];
            this.scissorHeight = scissorBox[3];
            this.blend = blend;
            this.depth = depth;
            this.cull = cull;
            this.scissor = scissor;
            this.depthWrite = depthWrite;
            this.srcRgb = srcRgb;
            this.dstRgb = dstRgb;
            this.srcAlpha = srcAlpha;
            this.dstAlpha = dstAlpha;
            this.activeTexture = activeTexture;
            this.shaderTextures = shaderTextures;
            this.boundTextures = boundTextures;
            this.program = program;
            this.shader = shader;
            this.shaderColor = shaderColor;
        }

        private static RenderStateSnapshot capture() {
            int[] viewport = new int[4];
            int[] scissor = new int[4];
            // The pass only touches the scene/depth sampler slots. Avoid
            // querying and restoring all 12 legacy texture units every frame.
            int[] textures = new int[3];
            int[] bindings = new int[3];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            int activeTexture = GlStateManager._getActiveTexture();
            for (int i = 0; i < textures.length; i++) {
                textures[i] = RenderSystem.getShaderTexture(i);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            GlStateManager._activeTexture(activeTexture);
            return new RenderStateSnapshot(
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    viewport,
                    scissor,
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    activeTexture, textures, bindings,
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    RenderSystem.getShader(), RenderSystem.getShaderColor().clone());
        }

        private void restore() {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) {
                RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            } else {
                RenderSystem.disableScissor();
            }
            RenderSystem.depthMask(depthWrite);
            for (int i = 0; i < shaderTextures.length; i++) {
                RenderSystem.setShaderTexture(i, shaderTextures[i]);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
                GlStateManager._bindTexture(boundTextures[i]);
            }
            GlStateManager._activeTexture(activeTexture);
            ProgramManager.glUseProgram(program);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShader(() -> shader);
        }
    }
}
