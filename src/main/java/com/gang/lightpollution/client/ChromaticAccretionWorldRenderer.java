package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.ChromaticAccretionEntity;
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
 * Bounded post-scene renderer for the Chromatic Accretion spell.
 *
 * <p>The expensive Shadertoy-style raymarch only runs on the projected sphere,
 * while copied color/depth buffers provide refraction and solid-block
 * occlusion. The fragment shader emits premultiplied alpha, so the empty part
 * of the proxy cannot become a black rectangle.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChromaticAccretionWorldRenderer {
    private static final int LATITUDE_SEGMENTS = 20;
    private static final int LONGITUDE_SEGMENTS = 40;
    private static final double QUERY_RANGE = 144.0D;
    private static final double QUERY_RANGE_SQR = QUERY_RANGE * QUERY_RANGE;
    private static final Quaternionf BASE_ROTATION = new Quaternionf()
            .rotateZ((float) Math.toRadians(12.0D))
            .rotateX((float) Math.toRadians(-20.0D));

    private static final Set<ChromaticAccretionEntity> ACTIVE_EFFECTS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<ChromaticAccretionEntity> FRAME_EFFECTS = new ArrayList<>();

    private static TextureTarget sceneCopyTarget;
    private static TextureTarget opaqueDepthTarget;
    private static boolean opaqueDepthCapturedThisFrame;

    private ChromaticAccretionWorldRenderer() {
    }

    @SubscribeEvent
    public static void renderAccretions(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            collectFrameEffects(event);
            opaqueDepthCapturedThisFrame = !FRAME_EFFECTS.isEmpty();
            if (opaqueDepthCapturedThisFrame) {
                captureOpaqueDepth();
            }
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (!opaqueDepthCapturedThisFrame || FRAME_EFFECTS.isEmpty()) {
            resetFrameCapture();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ChromaticAccretionRenderType.getShader();
        if (minecraft.level == null || shader == null || opaqueDepthTarget == null) {
            resetFrameCapture();
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        FRAME_EFFECTS.sort(Comparator.comparingDouble(
                effect -> -cameraPos.distanceToSqr(effect.position())));

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

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            shader.setSampler("DiffuseSampler", sceneCopyTarget.getColorTextureId());
            shader.setSampler("DepthSampler", opaqueDepthTarget.getDepthTextureId());
            for (ChromaticAccretionEntity effect : FRAME_EFFECTS) {
                renderAccretion(shader, effect, cameraPos, event.getPartialTick());
            }
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            mainTarget.bindWrite(false);
            resetFrameCapture();
        }
    }

    private static void collectFrameEffects(RenderLevelStageEvent event) {
        FRAME_EFFECTS.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ACTIVE_EFFECTS.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        ACTIVE_EFFECTS.removeIf(effect -> !effect.isAlive() || effect.level() != minecraft.level);
        for (ChromaticAccretionEntity effect : ACTIVE_EFFECTS) {
            float radius = Math.max(0.5F, effect.getVisualRadius());
            if (cameraPos.distanceToSqr(effect.position()) <= QUERY_RANGE_SQR
                    && event.getFrustum().isVisible(effect.getBoundingBox().inflate(radius * 1.6F))) {
                FRAME_EFFECTS.add(effect);
            }
        }
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

    private static void renderAccretion(ShaderInstance shader, ChromaticAccretionEntity effect,
                                        Vec3 cameraPos, float partialTick) {
        float ageTicks = effect.getVisualAgeTicks(partialTick);
        float scale = effect.getVisualRadius();
        if (scale <= 0.01F || ageTicks < 0.0F) {
            return;
        }

        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        try {
            double x = Mth.lerp(partialTick, effect.xOld, effect.getX()) - cameraPos.x;
            double y = Mth.lerp(partialTick, effect.yOld, effect.getY()) - cameraPos.y;
            double z = Mth.lerp(partialTick, effect.zOld, effect.getZ()) - cameraPos.z;

            Quaternionf rotation = getRotation(effect);
            Vector3f cameraObjectSpace = new Vector3f((float) -x, (float) -y, (float) -z);
            new Quaternionf(rotation).conjugate().transform(cameraObjectSpace);
            cameraObjectSpace.div(scale);

            poseStack.translate(x, y, z);
            poseStack.mulPose(rotation);
            poseStack.scale(scale, scale, scale);
            RenderSystem.applyModelViewMatrix();

            setUniform(shader, "CameraPos", cameraObjectSpace.x, cameraObjectSpace.y, cameraObjectSpace.z);
            setUniform(shader, "Time", ageTicks / 20.0F);
            setUniform(shader, "EffectProgress",
                    Mth.clamp(ageTicks / ChromaticAccretionEntity.LIFETIME_TICKS, 0.0F, 1.0F));
            setUniform(shader, "FormationProgress",
                    Mth.clamp(ageTicks / ChromaticAccretionEntity.FORMATION_END_TICK, 0.0F, 1.0F));
            setUniform(shader, "CollapseProgress", Mth.clamp(
                    (ageTicks - ChromaticAccretionEntity.COLLAPSE_TICK)
                            / (ChromaticAccretionEntity.LIFETIME_TICKS
                            - (float) ChromaticAccretionEntity.COLLAPSE_TICK),
                    0.0F, 1.0F));

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

    private static void setUniform(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static void setUniform(ShaderInstance shader, String name, float x, float y, float z) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(x, y, z);
        }
    }

    private static Quaternionf getRotation(ChromaticAccretionEntity effect) {
        UUID uuid = effect.getUUID();
        float spin = randomUnit(uuid, 0x17A4C5D9L) * Mth.TWO_PI;
        float wobble = Mth.lerp(randomUnit(uuid, 0x4D5F21A3L),
                (float) Math.toRadians(-9.0D), (float) Math.toRadians(9.0D));
        return new Quaternionf(BASE_ROTATION).rotateY(spin).rotateZ(wobble);
    }

    private static float randomUnit(UUID uuid, long salt) {
        long hash = uuid.getMostSignificantBits()
                ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 19)
                ^ salt;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return (float) ((hash >>> 1) / (double) Long.MAX_VALUE);
    }

    private static void addSphere(BufferBuilder bufferBuilder) {
        for (int latitude = 0; latitude < LATITUDE_SEGMENTS; latitude++) {
            float theta0 = ((float) latitude / LATITUDE_SEGMENTS - 0.5F) * Mth.PI;
            float theta1 = ((float) (latitude + 1) / LATITUDE_SEGMENTS - 0.5F) * Mth.PI;
            for (int longitude = 0; longitude < LONGITUDE_SEGMENTS; longitude++) {
                float phi0 = (float) longitude / LONGITUDE_SEGMENTS * Mth.TWO_PI;
                float phi1 = (float) (longitude + 1) / LONGITUDE_SEGMENTS * Mth.TWO_PI;
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

    private static void resetFrameCapture() {
        opaqueDepthCapturedThisFrame = false;
        FRAME_EFFECTS.clear();
    }

    @SubscribeEvent
    public static void trackAccretion(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide
                && event.getEntity() instanceof ChromaticAccretionEntity effect) {
            ACTIVE_EFFECTS.add(effect);
        }
    }

    @SubscribeEvent
    public static void untrackAccretion(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide
                && event.getEntity() instanceof ChromaticAccretionEntity effect) {
            ACTIVE_EFFECTS.remove(effect);
            FRAME_EFFECTS.remove(effect);
        }
    }

    @SubscribeEvent
    public static void releaseRenderTargets(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE_EFFECTS.clear();
        resetFrameCapture();
        releaseRenderTargets();
    }

    public static void releaseRenderTargets() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(ChromaticAccretionWorldRenderer::releaseRenderTargetsOnRenderThread);
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
