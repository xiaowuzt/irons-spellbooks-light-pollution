package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.StargraveSingularityEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side post-scene pass for Stargrave Singularity.
 *
 * <p>Adapted from CeliaClaire's ShaderTest black-hole renderer. The MIT
 * attribution is recorded in THIRD_PARTY_NOTICES.md. Gameplay state remains
 * server-owned by StargraveSingularityEntity.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class StargraveSingularityWorldRenderer {
    private static final int LATITUDE_SEGMENTS = 24;
    private static final int LONGITUDE_SEGMENTS = 48;
    private static final double QUERY_RANGE = 160.0D;
    private static final double QUERY_RANGE_SQR = QUERY_RANGE * QUERY_RANGE;
    private static final float RANDOM_TILT_RANGE = (float) Math.toRadians(20.0D);
    private static final Quaternionf BASE_DISK_ROTATION = new Quaternionf()
            .rotateZ((float) Math.toRadians(16.0D))
            .rotateX((float) Math.toRadians(-28.0D));

    private static TextureTarget sceneCopyTarget;
    private static TextureTarget opaqueDepthTarget;
    private static final Set<StargraveSingularityEntity> ACTIVE_SINGULARITIES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<StargraveSingularityEntity> FRAME_SINGULARITIES = new ArrayList<>();
    private static boolean opaqueDepthCapturedThisFrame;

    private StargraveSingularityWorldRenderer() {
    }

    @SubscribeEvent
    public static void renderSingularities(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            collectFrameSingularities(event);
            opaqueDepthCapturedThisFrame = !FRAME_SINGULARITIES.isEmpty();
            if (opaqueDepthCapturedThisFrame) {
                captureOpaqueDepth();
            }
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (!opaqueDepthCapturedThisFrame || FRAME_SINGULARITIES.isEmpty()) {
            resetFrameCapture();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            resetFrameCapture();
            return;
        }

        ShaderInstance shader = StargraveSingularityRenderType.getShader();
        if (shader == null || opaqueDepthTarget == null) {
            resetFrameCapture();
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        List<StargraveSingularityEntity> singularities = FRAME_SINGULARITIES;
        // Draw distant holes first so the nearer event horizon remains visually dominant.
        singularities.sort(Comparator.comparingDouble(
                singularity -> -cameraPos.distanceToSqr(singularity.position())));

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureSceneCopyTarget(mainTarget);
        copyColor(mainTarget, sceneCopyTarget);
        mainTarget.bindWrite(false);

        if (shader.getUniform("ScreenSize") != null) {
            shader.getUniform("ScreenSize").set((float) mainTarget.width, (float) mainTarget.height);
        }

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        try {
            modelViewStack.setIdentity();
            modelViewStack.mulPoseMatrix(event.getPoseStack().last().pose());
            RenderSystem.applyModelViewMatrix();

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            shader.setSampler("DiffuseSampler", sceneCopyTarget.getColorTextureId());
            shader.setSampler("DepthSampler", opaqueDepthTarget.getDepthTextureId());
            for (StargraveSingularityEntity singularity : singularities) {
                renderSingularity(shader, singularity, cameraPos, event.getPartialTick());
            }
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            mainTarget.bindWrite(false);
            resetFrameCapture();
        }
    }

    private static void collectFrameSingularities(RenderLevelStageEvent event) {
        FRAME_SINGULARITIES.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ACTIVE_SINGULARITIES.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        ACTIVE_SINGULARITIES.removeIf(entity -> !entity.isAlive() || entity.level() != minecraft.level);
        for (StargraveSingularityEntity entity : ACTIVE_SINGULARITIES) {
            if (cameraPos.distanceToSqr(entity.position()) <= QUERY_RANGE_SQR
                    && event.getFrustum().isVisible(entity.getBoundingBox()
                    .inflate(entity.getVisualRadius() * 2.5F))) {
                FRAME_SINGULARITIES.add(entity);
            }
        }
    }

    private static void resetFrameCapture() {
        opaqueDepthCapturedThisFrame = false;
        FRAME_SINGULARITIES.clear();
    }

    private static void captureOpaqueDepth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureOpaqueDepthTarget(mainTarget);
        try {
            copyDepth(mainTarget, opaqueDepthTarget);
        } finally {
            mainTarget.bindWrite(false);
        }
    }

    private static void renderSingularity(ShaderInstance shader, StargraveSingularityEntity singularity,
                                          Vec3 cameraPos, float partialTick) {
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        try {
            double x = Mth.lerp(partialTick, singularity.xOld, singularity.getX()) - cameraPos.x;
            double y = Mth.lerp(partialTick, singularity.yOld, singularity.getY()) - cameraPos.y;
            double z = Mth.lerp(partialTick, singularity.zOld, singularity.getZ()) - cameraPos.z;
            float age = singularity.tickCount + partialTick;
            float fadeIn = Mth.clamp(age / 10.0F, 0.0F, 1.0F);
            float fadeOut = 1.0F - Mth.clamp(
                    (age - (StargraveSingularityEntity.LIFETIME_TICKS - 30.0F)) / 30.0F,
                    0.0F, 1.0F);
            float scale = singularity.getVisualRadius() * fadeIn * fadeOut;
            if (scale <= 0.001F) {
                return;
            }

            Vector3f cameraObjectSpace = new Vector3f((float) -x, (float) -y, (float) -z);
            Quaternionf diskRotation = getDiskRotation(singularity);
            new Quaternionf(diskRotation).conjugate().transform(cameraObjectSpace);
            cameraObjectSpace.div(scale);

            poseStack.translate(x, y, z);
            poseStack.mulPose(diskRotation);
            poseStack.scale(scale, scale, scale);
            RenderSystem.applyModelViewMatrix();

            if (shader.getUniform("CameraPos") != null) {
                shader.getUniform("CameraPos").set(cameraObjectSpace.x, cameraObjectSpace.y, cameraObjectSpace.z);
            }
            if (shader.getUniform("Time") != null && Minecraft.getInstance().level != null) {
                shader.getUniform("Time").set(
                        (Minecraft.getInstance().level.getGameTime() + partialTick) * 0.04F);
            }

            BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            addSphere(bufferBuilder);
            RenderSystem.enableCull();
            GL11.glCullFace(cameraObjectSpace.lengthSquared() > 1.05F
                    ? GL11.GL_BACK
                    : GL11.GL_FRONT);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(bufferBuilder.end());
        } finally {
            GL11.glCullFace(GL11.GL_BACK);
            poseStack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static Quaternionf getDiskRotation(StargraveSingularityEntity singularity) {
        UUID uuid = singularity.getUUID();
        float spin = getRandomAngle(uuid, 0x1F123BB5L);
        float tiltX = Mth.lerp(getRandomUnit(uuid, 0x52A4D17EL), -RANDOM_TILT_RANGE, RANDOM_TILT_RANGE);
        float tiltZ = Mth.lerp(getRandomUnit(uuid, 0x7C3F91D2L), -RANDOM_TILT_RANGE, RANDOM_TILT_RANGE);
        return new Quaternionf(BASE_DISK_ROTATION).rotateY(spin).rotateX(tiltX).rotateZ(tiltZ);
    }

    private static float getRandomAngle(UUID uuid, long salt) {
        return getRandomUnit(uuid, salt) * Mth.TWO_PI;
    }

    private static float getRandomUnit(UUID uuid, long salt) {
        long hash = uuid.getMostSignificantBits()
                ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 17)
                ^ salt;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return (float) ((hash >>> 1) / (double) Long.MAX_VALUE);
    }

    private static void addSphere(BufferBuilder bufferBuilder) {
        for (int lat = 0; lat < LATITUDE_SEGMENTS; lat++) {
            float v0 = (float) lat / LATITUDE_SEGMENTS;
            float v1 = (float) (lat + 1) / LATITUDE_SEGMENTS;
            float theta0 = (v0 - 0.5F) * Mth.PI;
            float theta1 = (v1 - 0.5F) * Mth.PI;
            for (int lon = 0; lon < LONGITUDE_SEGMENTS; lon++) {
                float u0 = (float) lon / LONGITUDE_SEGMENTS;
                float u1 = (float) (lon + 1) / LONGITUDE_SEGMENTS;
                float phi0 = u0 * Mth.TWO_PI;
                float phi1 = u1 * Mth.TWO_PI;
                addTriangle(bufferBuilder, theta0, phi0, theta1, phi0, theta1, phi1);
                addTriangle(bufferBuilder, theta0, phi0, theta1, phi1, theta0, phi1);
            }
        }
    }

    private static void addTriangle(BufferBuilder bufferBuilder, float thetaA, float phiA,
                                    float thetaB, float phiB, float thetaC, float phiC) {
        addVertex(bufferBuilder, thetaA, phiA);
        addVertex(bufferBuilder, thetaB, phiB);
        addVertex(bufferBuilder, thetaC, phiC);
    }

    private static void addVertex(BufferBuilder bufferBuilder, float theta, float phi) {
        float cosTheta = Mth.cos(theta);
        bufferBuilder.vertex(cosTheta * Mth.cos(phi), Mth.sin(theta), cosTheta * Mth.sin(phi))
                .color(255, 255, 255, 255).endVertex();
    }

    private static void ensureSceneCopyTarget(RenderTarget mainTarget) {
        if (sceneCopyTarget == null
                || sceneCopyTarget.width != mainTarget.width
                || sceneCopyTarget.height != mainTarget.height) {
            if (sceneCopyTarget != null) {
                sceneCopyTarget.destroyBuffers();
            }
            sceneCopyTarget = new TextureTarget(mainTarget.width, mainTarget.height, false, Minecraft.ON_OSX);
        }
        sceneCopyTarget.setFilterMode(9729);
    }

    private static void ensureOpaqueDepthTarget(RenderTarget mainTarget) {
        if (opaqueDepthTarget == null
                || opaqueDepthTarget.width != mainTarget.width
                || opaqueDepthTarget.height != mainTarget.height) {
            if (opaqueDepthTarget != null) {
                opaqueDepthTarget.destroyBuffers();
            }
            opaqueDepthTarget = new TextureTarget(mainTarget.width, mainTarget.height, true, Minecraft.ON_OSX);
        }
        opaqueDepthTarget.setFilterMode(9728);
    }

    private static void copyColor(RenderTarget source, RenderTarget target) {
        RenderSystem.assertOnRenderThreadOrInit();
        try {
            GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
            GlStateManager._glBindFramebuffer(36009, target.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height,
                    0, 0, target.width, target.height, 16384, 9728);
        } finally {
            source.bindWrite(false);
        }
    }

    private static void copyDepth(RenderTarget source, RenderTarget target) {
        RenderSystem.assertOnRenderThreadOrInit();
        try {
            GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
            GlStateManager._glBindFramebuffer(36009, target.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height,
                    0, 0, target.width, target.height, 256, 9728);
        } finally {
            source.bindWrite(false);
        }
    }

    @SubscribeEvent
    public static void trackSingularity(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof StargraveSingularityEntity singularity) {
            ACTIVE_SINGULARITIES.add(singularity);
        }
    }

    @SubscribeEvent
    public static void untrackSingularity(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof StargraveSingularityEntity singularity) {
            ACTIVE_SINGULARITIES.remove(singularity);
            FRAME_SINGULARITIES.remove(singularity);
        }
    }

    @SubscribeEvent
    public static void releaseRenderTargets(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE_SINGULARITIES.clear();
        resetFrameCapture();
        releaseRenderTargets();
    }

    /** Releases temporary framebuffers when leaving a client world. */
    public static void releaseRenderTargets() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(StargraveSingularityWorldRenderer::releaseRenderTargetsOnRenderThread);
            return;
        }
        releaseRenderTargetsOnRenderThread();
    }

    private static void releaseRenderTargetsOnRenderThread() {
        resetFrameCapture();
        if (sceneCopyTarget != null) {
            sceneCopyTarget.destroyBuffers();
            sceneCopyTarget = null;
        }
        if (opaqueDepthTarget != null) {
            opaqueDepthTarget.destroyBuffers();
            opaqueDepthTarget = null;
        }
    }
}
