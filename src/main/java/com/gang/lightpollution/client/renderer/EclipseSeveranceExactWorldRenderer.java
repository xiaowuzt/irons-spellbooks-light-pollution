package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.EclipseSeveranceShaders;
import com.gang.lightpollution.entity.EclipseSeveranceEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact Forge-side rendering path for Gemini's Celestial sweeping attack.
 *
 * <p>The source project uses Minecraft 26.2's GPU pipeline. This class keeps
 * its geometry order, UV contract, timing and shader uniforms while using the
 * 1.20.1 {@link ShaderInstance} API. The old renderer remains as a fallback
 * until the three shaders have loaded.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EclipseSeveranceExactWorldRenderer {
    private static final float ARC_RANGE = (float) Math.toRadians(145.0D);
    private static final int ARC_SEGMENTS = 72;
    private static final int MAX_EFFECTS = 5;
    private static final int BUFFER_CAPACITY = 786_432;
    private static final double RENDER_DISTANCE = 32.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;
    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
    private static boolean staleBufferWarningLogged;
    private static final List<EclipseSeveranceEntity> ACTIVE = new ArrayList<>();
    private static final List<EclipseSeveranceEntity> FRAME = new ArrayList<>();
    private static final Map<EclipseSeveranceEntity, EclipseSeveranceVisualInstance> INSTANCES =
            new IdentityHashMap<>();
    private static boolean postAppliedThisFrame;

    private EclipseSeveranceExactWorldRenderer() {
    }

    @SubscribeEvent
    public static void renderEffects(RenderLevelStageEvent event) {
        // Two separate ifs rather than if/else: under a shader pack the geometry
        // moves to AFTER_LEVEL, so both halves land in the same invocation and the
        // geometry has to run before the pass that reads the colour buffer.
        if (SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
            renderWorldGeometry(event);
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            renderScreenPost();
        }
    }

    private static void renderWorldGeometry(RenderLevelStageEvent event) {
        postAppliedThisFrame = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !shadersReady()) {
            FRAME.clear();
            return;
        }

        collectFrame(event, minecraft);
        if (FRAME.isEmpty()) {
            postAppliedThisFrame = false;
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        RenderStateSnapshot state = RenderStateSnapshot.capture();
        try {
            for (EclipseSeveranceEntity effect : FRAME) {
                EclipseSeveranceVisualInstance visual = visualFor(effect);
                float age = visual.ageSeconds();
                visual.advanceTo(age);
                if (!visual.isAlive(age)) {
                    continue;
                }
                renderOne(SpellRenderStage.levelPoseStack(event), event.getCamera(), cameraPosition, visual, age);
            }
        } finally {
            state.restore();
        }
    }

    private static void renderScreenPost() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ACTIVE.isEmpty()) {
            postAppliedThisFrame = false;
            FRAME.clear();
            return;
        }
        try {
            postAppliedThisFrame = renderPost(minecraft);
        } finally {
            FRAME.clear();
        }
    }

    private static boolean shadersReady() {
        return EclipseSeveranceShaders.arcShader() != null
                && EclipseSeveranceShaders.particleShader() != null;
    }

    private static void collectFrame(RenderLevelStageEvent event, Minecraft minecraft) {
        FRAME.clear();
        Vec3 camera = event.getCamera().getPosition();
        ACTIVE.removeIf(effect -> {
            boolean remove = effect.isRemoved() || effect.level() != minecraft.level;
            if (remove) {
                INSTANCES.remove(effect);
            }
            return remove;
        });

        for (EclipseSeveranceEntity effect : ACTIVE) {
            EclipseSeveranceVisualInstance visual = visualFor(effect);
            float age = visual.ageSeconds();
            if (!visual.isAlive(age)
                    || camera.distanceToSqr(effect.position()) > RENDER_DISTANCE_SQR
                    || !event.getFrustum().isVisible(effect.getBoundingBox().inflate(4.0D))) {
                continue;
            }
            FRAME.add(effect);
        }
    }

    private static EclipseSeveranceVisualInstance visualFor(EclipseSeveranceEntity effect) {
        EclipseSeveranceVisualInstance visual = INSTANCES.get(effect);
        if (visual == null || visual.synchronizedSeed() != effect.getSeed()) {
            visual = new EclipseSeveranceVisualInstance(effect);
            INSTANCES.put(effect, visual);
        }
        return visual;
    }

    private static void renderOne(PoseStack poseStack, Camera camera, Vec3 cameraPosition,
                                  EclipseSeveranceVisualInstance visual, float age) {
        Matrix4f matrix = poseStack.last().pose();
        float sweep = visual.sweepProgress(age);
        float effectProgress = visual.effectProgress(age);
        float arcAlpha = visual.arcAlpha(age);
        float particleAlpha = visual.particleAlpha(age);
        float lightningAlpha = visual.lightningAlpha(age);
        float ringProgress = visual.ringProgress(age);
        float ringAlpha = visual.ringAlpha(age);
        float burstAlpha = visual.burstAlpha(age);
        float time = System.currentTimeMillis() / 1_000.0F;
        EclipseSeveranceShaders.SweepUniformData uniforms =
                EclipseSeveranceShaders.celestialSweep(
                        time, sweep, effectProgress,
                        EclipseSeveranceShaders.CELESTIAL_RADIUS, visual.seed);

        drawArc(poseStack, matrix, cameraPosition, visual, sweep, arcAlpha, uniforms);
        drawSpeedLines(poseStack, matrix, cameraPosition, visual, sweep, arcAlpha, uniforms);
        drawParticles(poseStack, matrix, camera, cameraPosition, visual, particleAlpha, uniforms);
        drawLightning(poseStack, matrix, cameraPosition, visual, lightningAlpha, uniforms);
        drawRings(poseStack, matrix, cameraPosition, visual, ringProgress, ringAlpha, uniforms);
        drawBurst(poseStack, matrix, camera, cameraPosition, visual, effectProgress, burstAlpha, uniforms);
    }

    private static void drawArc(PoseStack poseStack, Matrix4f matrix, Vec3 camera,
                                EclipseSeveranceVisualInstance visual, float sweep,
                                float alpha, EclipseSeveranceShaders.SweepUniformData uniforms) {
        if (sweep <= 0.001F || alpha <= 0.004F) {
            return;
        }
        BufferBuilder builder = begin();
        int vertices = 0;
        float start = visual.arcStart;
        float range = positiveAngleRange(visual.arcStart, visual.arcEnd);
        for (int echo = 3; echo >= 0; echo--) {
            float progress = clamp01(sweep - echo * EclipseSeveranceShaders.CELESTIAL_ECHO_SPACING);
            if (progress < 0.002F) {
                continue;
            }
            float echoFade = echo == 0 ? 1.0F : (float) Math.pow(0.62F, echo);
            float radius = EclipseSeveranceShaders.CELESTIAL_RADIUS * (1.0F - echo * 0.035F);
            float inner = radius * 0.42F;
            float outer = radius * 1.22F;
            float end = start + range * progress;
            int segments = ARC_SEGMENTS;
            for (int i = 0; i < segments; i++) {
                float t0 = i / (float) segments;
                float t1 = (i + 1.0F) / segments;
                float a0 = start + (end - start) * t0;
                float a1 = start + (end - start) * t1;
                float lift0 = EclipseSeveranceShaders.CELESTIAL_VERTICAL_LIFT * Mth.sin(t0 * Mth.PI);
                float lift1 = EclipseSeveranceShaders.CELESTIAL_VERTICAL_LIFT * Mth.sin(t1 * Mth.PI);
                int c0 = color(0.55F + 0.45F * Mth.sin(t0 * Mth.PI), 0.0F,
                        echo / 5.0F, EclipseSeveranceShaders.CELESTIAL_INTENSITY * alpha * echoFade);
                int c1 = color(0.55F + 0.45F * Mth.sin(t1 * Mth.PI), 0.0F,
                        echo / 5.0F, EclipseSeveranceShaders.CELESTIAL_INTENSITY * alpha * echoFade);
                arcVertex(builder, matrix, visual, camera, a0, inner, lift0, t0, 0.0F, c0);
                arcVertex(builder, matrix, visual, camera, a0, outer, lift0, t0, 1.0F, c0);
                arcVertex(builder, matrix, visual, camera, a1, outer, lift1, t1, 1.0F, c1);
                arcVertex(builder, matrix, visual, camera, a1, inner, lift1, t1, 0.0F, c1);
                vertices += 4;
            }
        }
        draw(builder, EclipseSeveranceShaders.arcShader(), uniforms, true, vertices);
    }

    private static void drawSpeedLines(PoseStack poseStack, Matrix4f matrix, Vec3 camera,
                                       EclipseSeveranceVisualInstance visual, float sweep,
                                       float alpha, EclipseSeveranceShaders.SweepUniformData uniforms) {
        if (alpha <= 0.004F) {
            return;
        }
        BufferBuilder builder = begin();
        int vertices = 0;
        float range = positiveAngleRange(visual.arcStart, visual.arcEnd);
        for (int i = 0; i < EclipseSeveranceShaders.CELESTIAL_SPEED_LINE_COUNT; i++) {
            float t = (i + 0.35F) / EclipseSeveranceShaders.CELESTIAL_SPEED_LINE_COUNT;
            float jitter = (float) Math.sin(visual.seed * 1.7F + i * 12.9898F) * 0.018F;
            float angle = visual.arcStart + range * clamp01(t + jitter);
            float radialX = Mth.cos(angle);
            float radialZ = Mth.sin(angle);
            float perpendicularX = -radialZ;
            float perpendicularZ = radialX;
            float startRadius = EclipseSeveranceShaders.CELESTIAL_RADIUS
                    * (0.62F + 0.2F * pseudo(i, visual.seed));
            float length = EclipseSeveranceShaders.CELESTIAL_RADIUS
                    * EclipseSeveranceShaders.CELESTIAL_LINE_LENGTH
                    * (0.55F + 0.7F * pseudo(i + 17, visual.seed));
            float endRadius = startRadius + length;
            float width = EclipseSeveranceShaders.CELESTIAL_LINE_WIDTH
                    * (0.55F + pseudo(i + 41, visual.seed));
            float y = (float) visual.originY()
                    + EclipseSeveranceShaders.CELESTIAL_VERTICAL_LIFT * Mth.sin(t * Mth.PI);
            float sx = (float) visual.originX() + radialX * startRadius;
            float sz = (float) visual.originZ() + radialZ * startRadius;
            float ex = (float) visual.originX() + radialX * endRadius;
            float ez = (float) visual.originZ() + radialZ * endRadius;
            int c = color(pseudo(i + 9, visual.seed), 1.0F, 0.0F,
                    sweep * alpha * EclipseSeveranceShaders.CELESTIAL_INTENSITY * 0.72F
                            * (0.45F + 0.55F * pseudo(i + 3, visual.seed)));
            ribbonVertex(builder, matrix, sx - perpendicularX * width, y,
                    sz - perpendicularZ * width, camera, 0.0F, -1.0F, c);
            ribbonVertex(builder, matrix, sx + perpendicularX * width, y,
                    sz + perpendicularZ * width, camera, 0.0F, 1.0F, c);
            ribbonVertex(builder, matrix, ex + perpendicularX * width, y,
                    ez + perpendicularZ * width, camera, 1.0F, 1.0F, c);
            ribbonVertex(builder, matrix, ex - perpendicularX * width, y,
                    ez - perpendicularZ * width, camera, 1.0F, -1.0F, c);
            vertices += 4;
        }
        draw(builder, EclipseSeveranceShaders.particleShader(), uniforms, true, vertices);
    }

    private static void drawParticles(PoseStack poseStack, Matrix4f matrix, Camera camera,
                                      Vec3 cameraPosition, EclipseSeveranceVisualInstance visual,
                                      float globalAlpha, EclipseSeveranceShaders.SweepUniformData uniforms) {
        if (globalAlpha <= 0.004F) {
            return;
        }
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.rotation());
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.rotation());
        BufferBuilder builder = begin();
        int vertices = 0;
        for (int i = 0; i < visual.particleCount(); i++) {
            float life = visual.particleLife(i);
            if (life <= 0.0F) {
                continue;
            }
            float normalizedLife = life / Math.max(visual.particleMaxLife(i), 0.001F);
            float particleAlpha = normalizedLife * normalizedLife * globalAlpha
                    * EclipseSeveranceShaders.CELESTIAL_INTENSITY;
            if (particleAlpha <= 0.004F) {
                continue;
            }
            float half = visual.particleSize(i) * 0.5F;
            float x = visual.particleX(i) - (float) cameraPosition.x;
            float y = visual.particleY(i) - (float) cameraPosition.y;
            float z = visual.particleZ(i) - (float) cameraPosition.z;
            float rx = right.x * half, ry = right.y * half, rz = right.z * half;
            float ux = up.x * half, uy = up.y * half, uz = up.z * half;
            int c = color(normalizedLife, 0.0F, pseudo(i, visual.seed), particleAlpha);
            vertex(builder, matrix, x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 0.0F, c);
            vertex(builder, matrix, x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 1.0F, c);
            vertex(builder, matrix, x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 1.0F, c);
            vertex(builder, matrix, x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 0.0F, c);
            vertices += 4;
        }
        draw(builder, EclipseSeveranceShaders.particleShader(), uniforms, true, vertices);
    }

    private static void drawLightning(PoseStack poseStack, Matrix4f matrix, Vec3 camera,
                                      EclipseSeveranceVisualInstance visual, float alpha,
                                      EclipseSeveranceShaders.SweepUniformData uniforms) {
        if (alpha <= 0.004F) {
            return;
        }
        BufferBuilder builder = begin();
        int vertices = 0;
        for (int bolt = 0; bolt < visual.boltCount(); bolt++) {
            for (int segment = 0; segment < visual.boltSegments(bolt) - 1; segment++) {
                float x0 = visual.boltX(bolt, segment);
                float y0 = visual.boltY(bolt, segment);
                float z0 = visual.boltZ(bolt, segment);
                float x1 = visual.boltX(bolt, segment + 1);
                float y1 = visual.boltY(bolt, segment + 1);
                float z1 = visual.boltZ(bolt, segment + 1);
                float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
                float cx = (float) camera.x, cy = (float) camera.y, cz = (float) camera.z;
                float toCameraX = cx - (x0 + x1) * 0.5F;
                float toCameraY = cy - (y0 + y1) * 0.5F;
                float toCameraZ = cz - (z0 + z1) * 0.5F;
                float nx = dy * toCameraZ - dz * toCameraY;
                float ny = dz * toCameraX - dx * toCameraZ;
                float nz = dx * toCameraY - dy * toCameraX;
                float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                if (length <= 0.0001F) {
                    continue;
                }
                float width = EclipseSeveranceShaders.CELESTIAL_LIGHTNING_WIDTH
                        * (0.7F + 0.5F * pseudo(bolt * 11 + segment, visual.seed));
                nx = nx / length * width;
                ny = ny / length * width;
                nz = nz / length * width;
                int c = color(pseudo(bolt, visual.seed), 1.0F, 1.0F,
                        alpha * EclipseSeveranceShaders.CELESTIAL_INTENSITY);
                vertex(builder, matrix, x0 - nx - cx, y0 - ny - cy, z0 - nz - cz, 0.0F, -1.0F, c);
                vertex(builder, matrix, x0 + nx - cx, y0 + ny - cy, z0 + nz - cz, 0.0F, 1.0F, c);
                vertex(builder, matrix, x1 + nx - cx, y1 + ny - cy, z1 + nz - cz, 1.0F, 1.0F, c);
                vertex(builder, matrix, x1 - nx - cx, y1 - ny - cy, z1 - nz - cz, 1.0F, -1.0F, c);
                vertices += 4;
            }
        }
        draw(builder, EclipseSeveranceShaders.particleShader(), uniforms, true, vertices);
    }

    private static void drawRings(PoseStack poseStack, Matrix4f matrix, Vec3 camera,
                                  EclipseSeveranceVisualInstance visual, float progress, float alpha,
                                  EclipseSeveranceShaders.SweepUniformData uniforms) {
        if (progress <= 0.003F || alpha <= 0.004F) {
            return;
        }
        float cx = (float) camera.x;
        float cy = (float) camera.y;
        float cz = (float) camera.z;
        for (int ring = 0; ring < 3; ring++) {
            float delayed = clamp01(progress - ring * 0.09F);
            if (delayed <= 0.002F) {
                continue;
            }
            float radius = EclipseSeveranceShaders.CELESTIAL_RADIUS
                    * EclipseSeveranceShaders.CELESTIAL_RING_SCALE * delayed * (1.0F + ring * 0.13F);
            float size = radius * 1.34F + 0.08F;
            float y = (float) visual.originY() - 0.12F + ring * 0.025F - cy;
            int c = color(ring / 5.0F, 1.0F, 0.0F,
                    alpha * EclipseSeveranceShaders.CELESTIAL_INTENSITY * (1.0F - ring * 0.11F));
            float x = (float) visual.originX() - cx;
            float z = (float) visual.originZ() - cz;
            // Gemini submits each photon ring separately. Keeping the draw
            // calls separate preserves the source depth/blend ordering.
            BufferBuilder builder = begin();
            vertex(builder, matrix, x - size, y, z - size, 0.0F, 0.0F, c);
            vertex(builder, matrix, x - size, y, z + size, 0.0F, 1.0F, c);
            vertex(builder, matrix, x + size, y, z + size, 1.0F, 1.0F, c);
            vertex(builder, matrix, x + size, y, z - size, 1.0F, 0.0F, c);
            draw(builder, EclipseSeveranceShaders.arcShader(), uniforms, false, 4);
        }
    }

    private static void drawBurst(PoseStack poseStack, Matrix4f matrix, Camera camera, Vec3 cameraPosition,
                                  EclipseSeveranceVisualInstance visual, float progress, float alpha,
                                  EclipseSeveranceShaders.SweepUniformData uniforms) {
        if (alpha <= 0.004F) {
            return;
        }
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.rotation());
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.rotation());
        float size = EclipseSeveranceShaders.CELESTIAL_RADIUS * (0.32F + progress * 0.72F);
        float x = (float) (visual.originX() + visual.dirX * EclipseSeveranceShaders.CELESTIAL_RADIUS * 0.56F)
                - (float) cameraPosition.x;
        float y = (float) visual.originY()
                + EclipseSeveranceShaders.CELESTIAL_VERTICAL_LIFT * 0.5F - (float) cameraPosition.y;
        float z = (float) (visual.originZ() + visual.dirZ * EclipseSeveranceShaders.CELESTIAL_RADIUS * 0.56F)
                - (float) cameraPosition.z;
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size, uy = up.y * size, uz = up.z * size;
        int c = color(progress, 1.0F, 1.0F,
                alpha * EclipseSeveranceShaders.CELESTIAL_INTENSITY);
        BufferBuilder builder = begin();
        vertex(builder, matrix, x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 0.0F, c);
        vertex(builder, matrix, x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 1.0F, c);
        vertex(builder, matrix, x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 1.0F, c);
        vertex(builder, matrix, x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 0.0F, c);
        draw(builder, EclipseSeveranceShaders.arcShader(), uniforms, false, 4);
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        try {
            effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        } catch (IllegalStateException staleState) {
            if (staleState.getMessage() == null
                    || !staleState.getMessage().contains("Already building")) {
                throw staleState;
            }

            // Oculus/ImmediatelyFast may leave the builder's public and
            // internal build-state flags out of sync after an interrupted
            // upload. Drop that poisoned builder instead of crashing every
            // subsequent frame.
            effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
            effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            if (!staleBufferWarningLogged) {
                staleBufferWarningLogged = true;
                ExampleMod.LOGGER.warn(
                        "Recovered a stale Eclipse Severance vertex buffer after an interrupted render batch");
            }
        }
        return effectBuffer;
    }

    /**
     * Finish an abandoned one-shot batch.  {@code discard()} is not reliable
     * with the 1.20.1 render mixins used by the pack, so use {@code end()} and
     * release the resulting CPU-side buffer instead.
     */
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
            if (builder == effectBuffer) {
                effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
            }
        }
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader,
                             EclipseSeveranceShaders.SweepUniformData uniforms,
                             boolean depthWrite, int vertices) {
        if (shader == null || !builder.building() || vertices <= 0) {
            finish(builder);
            return;
        }
        try {
            configureWorldState(depthWrite);
            RenderSystem.setShader(() -> shader);
            EclipseSeveranceShaders.applySweepUniforms(shader, uniforms);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            EclipseSeveranceShaders.disableForCompatibility(failure);
            EclipseSeverancePostProcessor.release();
        }
    }

    private static void configureWorldState(boolean depthWrite) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(depthWrite);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void arcVertex(BufferBuilder builder, Matrix4f matrix,
                                  EclipseSeveranceVisualInstance visual, Vec3 camera,
                                  float angle, float radius, float lift,
                                  float u, float v, int color) {
        vertex(builder, matrix,
                (float) (visual.originX() + Mth.cos(angle) * radius - camera.x),
                (float) (visual.originY() + lift - camera.y),
                (float) (visual.originZ() + Mth.sin(angle) * radius - camera.z),
                u, v, color);
    }

    private static void ribbonVertex(BufferBuilder builder, Matrix4f matrix,
                                     float x, float y, float z, Vec3 camera,
                                     float u, float v, int color) {
        vertex(builder, matrix, x - (float) camera.x, y - (float) camera.y,
                z - (float) camera.z, u, v, color);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               float x, float y, float z, float u, float v, int color) {
        builder.vertex(matrix, x, y, z).uv(u, v)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF).endVertex();
    }

    private static int color(float red, float green, float blue, float alpha) {
        int r = Math.round(clamp01(red) * 255.0F);
        int g = Math.round(clamp01(green) * 255.0F);
        int b = Math.round(clamp01(blue) * 255.0F);
        int a = Math.round(clamp01(alpha) * 255.0F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float pseudo(int index, float seed) {
        double value = Math.sin(index * 12.9898D + seed * 78.233D) * 43758.5453D;
        return (float) (value - Math.floor(value));
    }

    private static float positiveAngleRange(float start, float end) {
        float range = end - start;
        return range < 0.0F ? range + Mth.TWO_PI : range;
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / Math.max(edge1 - edge0, 0.0001F));
        return t * t * (3.0F - 2.0F * t);
    }

    private static boolean renderPost(Minecraft minecraft) {
        if (EclipseSeveranceShaders.postShader() == null) {
            return false;
        }
        float distortion = 0.0F;
        float chromatic = 0.0F;
        float flash = 0.0F;
        float vignette = 0.0F;
        // Gemini evaluates the post pass over every active instance, even if
        // a particular instance was culled from world geometry this frame.
        for (EclipseSeveranceEntity effect : ACTIVE) {
            EclipseSeveranceVisualInstance visual = INSTANCES.get(effect);
            if (visual == null) {
                continue;
            }
            float age = visual.ageSeconds();
            float arc = visual.arcAlpha(age) * EclipseSeveranceShaders.CELESTIAL_INTENSITY;
            distortion = Math.max(distortion, EclipseSeveranceShaders.CELESTIAL_DISTORTION * arc);
            chromatic = Math.max(chromatic, EclipseSeveranceShaders.CELESTIAL_CHROMATIC * arc);
            flash = Math.max(flash, EclipseSeveranceShaders.CELESTIAL_FLASH
                    * visual.burstAlpha(age) * EclipseSeveranceShaders.CELESTIAL_INTENSITY);
            vignette = Math.max(vignette, EclipseSeveranceShaders.CELESTIAL_VIGNETTE * visual.ringAlpha(age));
        }
        return EclipseSeverancePostProcessor.render(
                new EclipseSeverancePostProcessor.EffectParameters(
                        distortion, chromatic, flash, vignette,
                        EclipseSeveranceShaders.CELESTIAL_PRIMARY_R,
                        EclipseSeveranceShaders.CELESTIAL_PRIMARY_G,
                        EclipseSeveranceShaders.CELESTIAL_PRIMARY_B));
    }

    @SubscribeEvent
    public static void renderFallbackGui(RenderGuiEvent.Post event) {
        if (!shadersReady()) {
            return;
        }
        if (postAppliedThisFrame) {
            postAppliedThisFrame = false;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ACTIVE.isEmpty()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float flash = 0.0F;
        float vignette = 0.0F;
        for (EclipseSeveranceEntity effect : ACTIVE) {
            EclipseSeveranceVisualInstance visual = INSTANCES.get(effect);
            if (visual == null || camera.distanceToSqr(effect.position()) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = visual.ageSeconds();
            flash = Math.max(flash, EclipseSeveranceShaders.CELESTIAL_FLASH
                    * visual.burstAlpha(age) * EclipseSeveranceShaders.CELESTIAL_INTENSITY);
            vignette = Math.max(vignette, EclipseSeveranceShaders.CELESTIAL_VIGNETTE * visual.ringAlpha(age));
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        if (flash > 0.01F) {
            graphics.fill(0, 0, width, height,
                    (Mth.clamp(Mth.floor(flash * 34.0F), 0, 34) << 24) | 0xE2F4FF);
        }
        if (vignette > 0.01F) {
            int band = Math.max(2, Math.min(width, height) / 70);
            for (int i = 0; i < 8; i++) {
                float falloff = 1.0F - i / 8.0F;
                int alpha = Mth.clamp(Mth.floor(vignette * falloff * falloff * 12.0F), 0, 12);
                int color = (alpha << 24) | 0x2A104A;
                int inset = i * band;
                graphics.fill(inset, inset, width - inset, inset + band, color);
                graphics.fill(inset, height - inset - band, width - inset, height - inset, color);
                graphics.fill(inset, inset + band, inset + band, height - inset - band, color);
                graphics.fill(width - inset - band, inset + band, width - inset, height - inset - band, color);
            }
        }
    }

    @SubscribeEvent
    public static void trackEffect(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof EclipseSeveranceEntity effect) {
            if (!ACTIVE.contains(effect)) {
                ACTIVE.add(effect);
                while (ACTIVE.size() > MAX_EFFECTS) {
                    EclipseSeveranceEntity oldest = ACTIVE.remove(0);
                    EclipseSeveranceVisualInstance visual = INSTANCES.remove(oldest);
                    if (visual != null) {
                        visual.discard();
                    }
                    FRAME.remove(oldest);
                }
            }
            visualFor(effect);
        }
    }

    @SubscribeEvent
    public static void untrackEffect(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof EclipseSeveranceEntity effect) {
            ACTIVE.remove(effect);
            EclipseSeveranceVisualInstance visual = INSTANCES.remove(effect);
            if (visual != null) {
                visual.discard();
            }
            FRAME.remove(effect);
        }
    }

    @SubscribeEvent
    public static void clearOnLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        FRAME.clear();
        INSTANCES.clear();
        postAppliedThisFrame = false;
        EclipseSeverancePostProcessor.release();
    }

    private static final class RenderStateSnapshot {
        private final boolean blend;
        private final boolean depth;
        private final boolean cull;
        private final boolean depthWrite;
        private final int srcRgb;
        private final int dstRgb;
        private final int srcAlpha;
        private final int dstAlpha;
        private final ShaderInstance shader;
        private final float[] shaderColor;

        private RenderStateSnapshot(boolean blend, boolean depth, boolean cull, boolean depthWrite,
                                    int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                                    ShaderInstance shader, float[] shaderColor) {
            this.blend = blend;
            this.depth = depth;
            this.cull = cull;
            this.depthWrite = depthWrite;
            this.srcRgb = srcRgb;
            this.dstRgb = dstRgb;
            this.srcAlpha = srcAlpha;
            this.dstAlpha = dstAlpha;
            this.shader = shader;
            this.shaderColor = shaderColor;
        }

        private static RenderStateSnapshot capture() {
            return new RenderStateSnapshot(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    RenderSystem.getShader(), RenderSystem.getShaderColor().clone());
        }

        private void restore() {
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            if (shader != null) RenderSystem.setShader(() -> shader);
        }
    }
}
