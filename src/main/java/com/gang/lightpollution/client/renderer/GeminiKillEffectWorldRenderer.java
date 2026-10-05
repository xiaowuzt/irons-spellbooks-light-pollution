package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.perf.PerfTracker;
import com.gang.lightpollution.client.GeminiKillEffectShaders;
import com.gang.lightpollution.entity.FuneralNovaEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Forge 1.20.1 world renderer for Gemini's complete Hypernova/KillEffect.
 *
 * <p>Geometry, stage envelopes, vertex-channel packing, and draw order match
 * the source renderer. The active spell entity owns both the visual age and
 * damage pulses; entity death is never a trigger for this renderer.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GeminiKillEffectWorldRenderer {
    private static final int MAX_EFFECTS = 8;
    private static final int MAX_PARTICLE_BATCH = 4096;
    private static final double RENDER_DISTANCE = 192.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;

    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;
    private static final int RAY_COUNT = 32;
    private static final float RAY_LENGTH_FACTOR = 2.75F;
    private static final float RAY_TIP_WIDTH = 0.25F;
    private static final int BUFFER_CAPACITY = 1_048_576;
    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private static final List<FuneralNovaEntity> ACTIVE = new ArrayList<>();
    private static final Map<FuneralNovaEntity, GeminiKillEffectVisualInstance> INSTANCES =
            new IdentityHashMap<>();
    private static final float[] PARTICLE_BATCH = new float[MAX_PARTICLE_BATCH * 8];
    private static final Vector3f CAMERA_UP = new Vector3f();
    private static final Vector3f CAMERA_RIGHT = new Vector3f();
    private static final List<PostFrameState> POST_STATES = new ArrayList<>();

    private static float[][] unitSphereVertices;

    private GeminiKillEffectWorldRenderer() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof FuneralNovaEntity nova)) {
            return;
        }
        if (!ACTIVE.contains(nova)) {
            ACTIVE.add(nova);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                || !(event.getEntity() instanceof FuneralNovaEntity nova)) {
            return;
        }
        ACTIVE.remove(nova);
        INSTANCES.remove(nova);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        INSTANCES.clear();
        POST_STATES.clear();
        GeminiKillEffectPostProcessor.release();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        // Two separate ifs rather than if/else: under a shader pack the geometry
        // moves to AFTER_LEVEL, so both halves land in the same invocation and the
        // geometry has to run before the pass that reads the colour buffer.
        if (SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
            renderWorld(event);
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            renderPost();
        }
    }

    private static void renderWorld(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            POST_STATES.clear();
            return;
        }

        ACTIVE.removeIf(entity -> {
            boolean remove = entity.isRemoved() || entity.level() != minecraft.level;
            if (remove) INSTANCES.remove(entity);
            return remove;
        });
        if (ACTIVE.isEmpty()
                && com.gang.lightpollution.fx.FxRegistry.funeralNovas().isEmpty()) {
            POST_STATES.clear();
            return;
        }

        float partialTick = event.getPartialTick();
        Vec3 cameraPosition = event.getCamera().getPosition();
        POST_STATES.clear();
        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        long started = PerfTracker.begin(PerfTracker.Section.CINEMATIC);
        try {
            int rendered = 0;
            for (FuneralNovaEntity entity : ACTIVE) {
                if (rendered >= MAX_EFFECTS) break;
                if (cameraPosition.distanceToSqr(entity.position()) > RENDER_DISTANCE_SQR) continue;
                float age = entity.getVisualAgeTicks(partialTick) / 20.0F;
                GeminiKillEffectVisualInstance visual = visualFor(entity);
                visual.setPosition(entity.position());
                visual.advanceTo(age);
                int stage = visual.currentStage(age);
                if (stage < 0) continue;

                float progress = visual.stageProgress(age);
                POST_STATES.add(new PostFrameState(
                        entity.position().add(0.0D, 1.5D, 0.0D),
                        stage, progress, age, 1.0F, visual.chainFade(age)));

                if (!GeminiKillEffectShaders.ready()
                        || cameraPosition.distanceToSqr(entity.position()) > RENDER_DISTANCE_SQR) {
                    continue;
                }
                renderOne(SpellRenderStage.levelPoseStack(event), event.getCamera(), cameraPosition, visual, age);
                rendered++;
            }
            // Anything another mod asked for through the API. These are stepped by the registry's
            // own tick rather than from an entity's age, so nothing is advanced here.
            for (com.gang.lightpollution.fx.FuneralNovaSource source
                    : com.gang.lightpollution.fx.FxRegistry.funeralNovas()) {
                if (rendered >= MAX_EFFECTS) {
                    break;
                }
                GeminiKillEffectVisualInstance visual = source.visual();
                float age = com.gang.lightpollution.fx.FxRegistry.funeralNovaAge(source);
                int stage = visual.currentStage(age);
                if (stage < 0) {
                    continue;
                }
                Vec3 at = visual.position();
                if (cameraPosition.distanceToSqr(at) > RENDER_DISTANCE_SQR) continue;
                POST_STATES.add(new PostFrameState(at.add(0.0D, 1.5D, 0.0D), stage,
                        visual.stageProgress(age), age, 1.0F, visual.chainFade(age)));
                if (!GeminiKillEffectShaders.ready()
                        || cameraPosition.distanceToSqr(at) > RENDER_DISTANCE_SQR) {
                    continue;
                }
                renderOne(SpellRenderStage.levelPoseStack(event), event.getCamera(), cameraPosition,
                        visual, age);
                rendered++;
            }
        } finally {
            snapshot.restore();
            PerfTracker.end(PerfTracker.Section.CINEMATIC, started);
        }
    }

    private static void renderPost() {
        PostFrameState frame = primaryPostFrameState();
        if (frame == null) {
            return;
        }
        GeminiKillEffectPostProcessor.render(
                frame.center(), frame.stage(), frame.progress(), frame.elapsedSeconds(),
                frame.intensity(), frame.chainFade());
    }

    private static GeminiKillEffectVisualInstance visualFor(FuneralNovaEntity entity) {
        GeminiKillEffectVisualInstance visual = INSTANCES.get(entity);
        if (visual == null || visual.synchronizedSeed() != entity.getSeed()) {
            visual = new GeminiKillEffectVisualInstance(entity.position(), entity.getSeed());
            INSTANCES.put(entity, visual);
        }
        return visual;
    }

    public static float currentCameraShake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }

        float shake = 0.0F;
        for (FuneralNovaEntity entity : ACTIVE) {
            if (entity.isRemoved() || entity.level() != minecraft.level
                    || minecraft.gameRenderer.getMainCamera().getPosition()
                    .distanceToSqr(entity.position()) > RENDER_DISTANCE_SQR) {
                continue;
            }
            GeminiKillEffectVisualInstance visual = visualFor(entity);
            visual.setPosition(entity.position());
            visual.advanceTo(entity.getVisualAgeTicks(partialTick) / 20.0F);
            shake = Math.max(shake, visual.shakeIntensity());
        }
        return shake;
    }

    private static void renderOne(PoseStack poseStack, Camera camera, Vec3 cameraPosition,
                                  GeminiKillEffectVisualInstance visual, float age) {
        int stage = visual.currentStage(age);
        if (visual.shouldRenderMagic(age)) {
            drawMagic(poseStack, cameraPosition, visual, age);
        }
        if (visual.shouldRenderBlackHole(age)) {
            drawBlackHole(poseStack, camera, cameraPosition, visual, age);
        }
        if (stage >= GeminiKillEffectVisualInstance.STAGE_MAGIC_CIRCLE
                && stage <= GeminiKillEffectVisualInstance.STAGE_COLLAPSE
                && visual.particleCount() > 0) {
            int count = visual.fillParticleBatch(PARTICLE_BATCH, MAX_PARTICLE_BATCH, 1.0F);
            drawParticles(poseStack, camera, cameraPosition, PARTICLE_BATCH, count);
        }
        if (stage >= GeminiKillEffectVisualInstance.STAGE_FLASH
                && stage <= GeminiKillEffectVisualInstance.STAGE_AFTERGLOW
                && visual.burstCount() > 0) {
            int count = visual.fillBurstBatch(PARTICLE_BATCH, MAX_PARTICLE_BATCH, 1.0F);
            drawParticles(poseStack, camera, cameraPosition, PARTICLE_BATCH, count);
        }
        if (stage >= GeminiKillEffectVisualInstance.STAGE_HYPERNOVA
                && stage <= GeminiKillEffectVisualInstance.STAGE_FADE_OUT) {
            drawHypernova(poseStack, camera, cameraPosition, visual, age);
        }
    }

    private static void drawMagic(PoseStack poseStack, Vec3 camera,
                                  GeminiKillEffectVisualInstance visual, float age) {
        int stage = visual.currentStage(age);
        float progress = visual.stageProgress(age);
        float shaderStage = stage == GeminiKillEffectVisualInstance.STAGE_BLACK_HOLE
                ? GeminiKillEffectVisualInstance.STAGE_MAGIC_TOWER : stage;
        float alpha = visual.magicTransitionAlpha(age);
        Vec3 position = visual.position();
        float distance = (float) Math.sqrt(camera.distanceToSqr(position)) + 0.01F;
        float baseSize = 1.5F * (1.0F + distance * 0.08F);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = begin();
        int vertices = 0;

        if (stage == GeminiKillEffectVisualInstance.STAGE_MAGIC_CIRCLE) {
            float size = baseSize * (0.5F + progress * 0.5F);
            float circleAlpha = alpha * (progress < 0.2F ? progress / 0.2F : 1.0F);
            vertices += horizontalQuad(builder, matrix, position.x, position.y + 0.02D, position.z,
                    size, camera, color(progress, 1.0F / 8.0F, 0.8F, circleAlpha));
        } else {
            boolean transition = false;
            float transitionProgress = 0.0F;
            if (stage == GeminiKillEffectVisualInstance.STAGE_MAGIC_TOWER && progress > 0.70F) {
                transition = true;
                transitionProgress = (progress - 0.70F) / 0.30F;
            } else if (stage == GeminiKillEffectVisualInstance.STAGE_BLACK_HOLE) {
                transition = true;
                transitionProgress = 1.0F;
            }

            if (stage == GeminiKillEffectVisualInstance.STAGE_MAGIC_TOWER && progress < 0.25F) {
                float circleFade = 1.0F - progress / 0.25F;
                vertices += horizontalQuad(builder, matrix, position.x, position.y + 0.02D, position.z,
                        baseSize * (1.0F + progress * 0.4F), camera,
                        color(1.0F, 1.0F / 8.0F, 0.8F, alpha * circleFade));
            }

            for (int i = 0; i < 12; i++) {
                float layerProgress = i / 12.0F;
                float scale = (float) Math.pow(0.9D, i);
                float height = i * 0.3F * baseSize;
                float layerAlpha = (float) Math.pow(0.82D, i) * alpha;
                float yOffset = 0.02F + height;
                float riseProgress = clamp((progress - i * 0.05F) / 0.15F, 0.0F, 1.0F);
                layerAlpha *= riseProgress;
                if (transition) {
                    yOffset = 0.02F + height * (1.0F - transitionProgress * 0.85F);
                    layerAlpha *= 1.0F - layerProgress * transitionProgress * 0.7F;
                }
                vertices += horizontalQuad(builder, matrix, position.x, position.y + yOffset, position.z,
                        baseSize * scale, camera,
                        color(progress, shaderStage / 8.0F, 0.8F, layerAlpha));
            }
        }
        draw(builder, GeminiKillEffectShaders.magic(), true, vertices, null);
    }

    private static void drawBlackHole(PoseStack poseStack, Camera viewCamera, Vec3 camera,
                                      GeminiKillEffectVisualInstance visual, float age) {
        int stage = visual.currentStage(age);
        float progress = visual.stageProgress(age);
        float alpha = visual.blackHoleTransitionAlpha(age);
        if (alpha < 0.001F) return;
        float holeSize = 1.5F;
        float brightness = 1.0F;
        if (stage == GeminiKillEffectVisualInstance.STAGE_MAGIC_TOWER) {
            float t = (progress - 0.70F) / 0.30F;
            holeSize = 0.02F + t * t * 0.28F;
            brightness = 0.3F + t * 0.7F;
        } else if (stage == GeminiKillEffectVisualInstance.STAGE_BLACK_HOLE) {
            holeSize = 0.3F + progress * 1.2F;
            if (progress < 0.30F) {
                float t = progress / 0.30F;
                holeSize = Math.max(holeSize, 0.05F + t * 0.5F);
                brightness = 0.5F + t * 0.5F;
            }
            alpha = progress < 0.15F ? Math.max(alpha, progress / 0.15F) : Math.max(alpha, 1.0F);
        } else if (stage == GeminiKillEffectVisualInstance.STAGE_COLLAPSE) {
            holeSize = 1.5F * (1.0F - progress * 0.8F);
            brightness = 1.0F + progress * 3.0F;
        }

        updateCameraVectors(viewCamera);
        Vec3 position = visual.position().add(0.0D, 1.5D, 0.0D);
        BufferBuilder builder = begin();
        billboard(builder, poseStack.last().pose(), position, camera, holeSize * 3.25F,
                color(progress, stage / 8.0F, brightness / 4.0F, clamp01(alpha)));
        draw(builder, GeminiKillEffectShaders.hole(), true, 4, null);
    }

    private static void drawParticles(PoseStack poseStack, Camera viewCamera, Vec3 camera,
                                      float[] batch, int count) {
        if (count <= 0) return;
        updateCameraVectors(viewCamera);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = begin();
        int vertices = 0;
        for (int i = 0; i < count; i++) {
            int offset = i * 8;
            billboard(builder, matrix,
                    new Vec3(batch[offset], batch[offset + 1], batch[offset + 2]),
                    camera, batch[offset + 3],
                    color(batch[offset + 4], batch[offset + 5], batch[offset + 6], batch[offset + 7]));
            vertices += 4;
        }
        draw(builder, GeminiKillEffectShaders.particle(), true, vertices, null);
    }

    private static void drawHypernova(PoseStack poseStack, Camera viewCamera, Vec3 camera,
                                      GeminiKillEffectVisualInstance visual, float age) {
        int stage = visual.currentStage(age);
        float progress = visual.stageProgress(age);
        float alpha;
        if (stage == GeminiKillEffectVisualInstance.STAGE_HYPERNOVA) {
            alpha = 1.0F - (float) Math.pow(2.0D, -6.0D * progress);
        } else if (stage == GeminiKillEffectVisualInstance.STAGE_AFTERGLOW) {
            float fade = 1.0F - progress;
            alpha = 0.08F + fade * fade * fade * 0.92F;
        } else {
            alpha = 0.08F * visual.fadeOutAlpha(age);
        }
        if (alpha < 0.005F) return;

        updateCameraVectors(viewCamera);
        Vec3 base = visual.position();
        Vec3 center = base.add(0.0D, 2.5D, 0.0D);
        float distance = (float) Math.sqrt(camera.distanceToSqr(center)) + 0.1F;
        Matrix4f matrix = poseStack.last().pose();

        if (stage == GeminiKillEffectVisualInstance.STAGE_HYPERNOVA) {
            float ringSize = distance * 0.58F * (1.0F + progress * 2.1F);
            float fade = 1.0F - progress;
            float ringAlpha = alpha * fade * (float) Math.sqrt(fade);
            float echoProgress = clamp01((progress - 0.12F) / 0.88F);
            BufferBuilder rings = begin();
            int ringVertices = 0;
            ringVertices += horizontalQuad(rings, matrix, base.x, base.y + 0.04D, base.z,
                    ringSize, camera,
                    color(progress * 0.62F, 0.0F, 2.4F / 4.0F, ringAlpha));
            ringVertices += horizontalQuad(rings, matrix, base.x, base.y + 0.055D, base.z,
                    ringSize * 0.72F, camera,
                    color(echoProgress * 0.58F, 0.0F, 1.55F / 4.0F, ringAlpha * 0.62F));
            draw(rings, GeminiKillEffectShaders.nova(), false, ringVertices, null);
        }

        float novaProgress = stage == GeminiKillEffectVisualInstance.STAGE_HYPERNOVA
                ? progress : 1.0F;
        float novaIntensity = stage == GeminiKillEffectVisualInstance.STAGE_HYPERNOVA
                ? 1.0F : stage == GeminiKillEffectVisualInstance.STAGE_AFTERGLOW ? 0.65F : 0.25F;
        BufferBuilder nova = begin();
        billboard(nova, matrix, center, camera, distance * 0.44F,
                color(novaProgress, 0.5F, novaIntensity, alpha));
        int novaVertices = 4;
        if (stage == GeminiKillEffectVisualInstance.STAGE_HYPERNOVA) {
            float flashTime = 0.33F + 0.045F * Mth.sin(progress * Mth.PI * 4.0F);
            float flashIntensity = 3.85F + Mth.sin(progress * Mth.PI * 3.5F)
                    * 0.15F * (1.0F - progress);
            float corePulse = 0.72F + 0.28F * Mth.sin(progress * Mth.PI * 5.0F);
            billboard(nova, matrix, center, camera, distance * 0.29F,
                    color(flashTime, 0.0F, flashIntensity / 4.0F,
                            alpha * clamp(corePulse, 0.55F, 1.0F)));
            novaVertices += 4;
        }
        draw(nova, GeminiKillEffectShaders.nova(), false, novaVertices, null);

        drawGlowSphere(poseStack, camera, center, progress, alpha, stage);
        drawRadialRays(poseStack, camera, center, distance, progress, alpha, stage);
    }

    private static void drawGlowSphere(PoseStack poseStack, Vec3 camera, Vec3 center,
                                       float progress, float alpha, int stage) {
        float radius;
        float heat;
        float boost;
        if (stage == GeminiKillEffectVisualInstance.STAGE_HYPERNOVA) {
            float expansion = 1.0F - (float) Math.pow(1.0F - progress, 2.4D);
            radius = 2.0F + expansion * 8.0F;
            heat = 1.0F - progress * 0.48F;
            boost = 0.75F + progress * 1.35F;
        } else if (stage == GeminiKillEffectVisualInstance.STAGE_AFTERGLOW) {
            float decay = 1.0F - progress;
            radius = 10.0F - progress * 5.5F;
            heat = 0.52F - progress * 0.30F;
            boost = 0.32F + decay * decay * 1.78F;
        } else {
            float fade = 1.0F - progress * progress * (3.0F - 2.0F * progress);
            radius = 1.0F + 3.5F * fade;
            heat = 0.22F * fade;
            boost = 0.32F * fade;
        }

        int packed = color(progress, heat, boost * 2.2F * alpha / 4.0F, alpha);
        float relativeX = (float) (center.x - camera.x);
        float relativeY = (float) (center.y - camera.y);
        float relativeZ = (float) (center.z - camera.z);
        Matrix4f matrix = poseStack.last().pose();

        // Forge 1.20.1 bakes the event PoseStack into BufferBuilder vertices.
        // Pass the identically baked center explicitly instead of asking the
        // fragment shader to recover it from the global ModelViewMat origin.
        Vector3f sphereCenter = matrix.transformPosition(
                relativeX, relativeY, relativeZ, new Vector3f());
        BufferBuilder builder = begin();
        float[][] sphere = unitSphere();
        for (float[] point : sphere) {
            vertex(builder, matrix,
                    relativeX + point[0] * radius,
                    relativeY + point[1] * radius,
                    relativeZ + point[2] * radius,
                    radius, 0.0F, packed);
        }
        draw(builder, GeminiKillEffectShaders.orb(), true, sphere.length, shader -> {
            if (shader.getUniform("SphereCenter") != null) {
                shader.getUniform("SphereCenter").set(
                        sphereCenter.x, sphereCenter.y, sphereCenter.z);
            }
        });
    }

    private static void drawRadialRays(PoseStack poseStack, Vec3 camera, Vec3 center,
                                       float distance, float progress, float alpha, int stage) {
        float rayAlpha;
        float rayIntensity;
        if (stage == GeminiKillEffectVisualInstance.STAGE_HYPERNOVA) {
            float pulse = 0.82F + 0.18F * Mth.sin(progress * Mth.PI * 9.0F);
            rayAlpha = alpha * (0.55F + progress * 0.45F) * pulse;
            rayIntensity = 1.0F + progress * 1.5F;
        } else if (stage == GeminiKillEffectVisualInstance.STAGE_AFTERGLOW) {
            float decay = 1.0F - progress;
            rayAlpha = alpha * decay * decay;
            rayIntensity = 2.5F * decay * decay;
        } else {
            return;
        }
        if (rayAlpha < 0.005F) return;

        float originX = (float) (center.x - camera.x);
        float originY = (float) (center.y - camera.y);
        float originZ = (float) (center.z - camera.z);
        float length = distance * RAY_LENGTH_FACTOR * (0.8F + progress * 0.5F);
        float width = RAY_TIP_WIDTH * (1.0F + progress * 0.5F);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = begin();
        for (int i = 0; i < RAY_COUNT; i++) {
            float angle = Mth.TWO_PI * i / RAY_COUNT;
            float variation = 0.65F + 0.35F * Mth.sin(i * 2.7F + 1.3F);
            emitRayQuad(builder, matrix, originX, originY, originZ,
                    Mth.cos(angle), Mth.sin(angle), length, width,
                    color(progress, i / (float) RAY_COUNT,
                            rayIntensity * variation / 4.0F, rayAlpha));
        }
        draw(builder, GeminiKillEffectShaders.ray(), true, RAY_COUNT * 4, null);
    }

    private static float[][] unitSphere() {
        if (unitSphereVertices != null) return unitSphereVertices;
        float[][] vertices = new float[SPHERE_LAT * SPHERE_LON * 4][3];
        int index = 0;
        for (int latitude = 0; latitude < SPHERE_LAT; latitude++) {
            double theta0 = Math.PI * latitude / SPHERE_LAT;
            double theta1 = Math.PI * (latitude + 1) / SPHERE_LAT;
            for (int longitude = 0; longitude < SPHERE_LON; longitude++) {
                double phi0 = Math.PI * 2.0D * longitude / SPHERE_LON;
                double phi1 = Math.PI * 2.0D * (longitude + 1) / SPHERE_LON;
                vertices[index++] = spherePoint(theta0, phi0);
                vertices[index++] = spherePoint(theta0, phi1);
                vertices[index++] = spherePoint(theta1, phi1);
                vertices[index++] = spherePoint(theta1, phi0);
            }
        }
        unitSphereVertices = vertices;
        return vertices;
    }

    private static float[] spherePoint(double theta, double phi) {
        return new float[]{
                (float) (Math.sin(theta) * Math.cos(phi)),
                (float) Math.cos(theta),
                (float) (Math.sin(theta) * Math.sin(phi))
        };
    }

    private static void updateCameraVectors(Camera camera) {
        Quaternionf rotation = new Quaternionf(camera.rotation());
        CAMERA_UP.set(0.0F, 1.0F, 0.0F).rotate(rotation);
        CAMERA_RIGHT.set(1.0F, 0.0F, 0.0F).rotate(rotation);
    }

    private static void billboard(BufferBuilder builder, Matrix4f matrix,
                                  Vec3 position, Vec3 camera, float halfSize, int packedColor) {
        float x = (float) (position.x - camera.x);
        float y = (float) (position.y - camera.y);
        float z = (float) (position.z - camera.z);
        float rightX = CAMERA_RIGHT.x * halfSize;
        float rightY = CAMERA_RIGHT.y * halfSize;
        float rightZ = CAMERA_RIGHT.z * halfSize;
        float upX = CAMERA_UP.x * halfSize;
        float upY = CAMERA_UP.y * halfSize;
        float upZ = CAMERA_UP.z * halfSize;
        vertex(builder, matrix, x - rightX - upX, y - rightY - upY, z - rightZ - upZ,
                0.0F, 0.0F, packedColor);
        vertex(builder, matrix, x - rightX + upX, y - rightY + upY, z - rightZ + upZ,
                0.0F, 1.0F, packedColor);
        vertex(builder, matrix, x + rightX + upX, y + rightY + upY, z + rightZ + upZ,
                1.0F, 1.0F, packedColor);
        vertex(builder, matrix, x + rightX - upX, y + rightY - upY, z + rightZ - upZ,
                1.0F, 0.0F, packedColor);
    }

    private static int horizontalQuad(BufferBuilder builder, Matrix4f matrix,
                                      double x, double y, double z, float halfSize,
                                      Vec3 camera, int packedColor) {
        float relativeX = (float) (x - camera.x);
        float relativeY = (float) (y - camera.y);
        float relativeZ = (float) (z - camera.z);
        vertex(builder, matrix, relativeX - halfSize, relativeY, relativeZ - halfSize,
                0.0F, 0.0F, packedColor);
        vertex(builder, matrix, relativeX - halfSize, relativeY, relativeZ + halfSize,
                0.0F, 1.0F, packedColor);
        vertex(builder, matrix, relativeX + halfSize, relativeY, relativeZ + halfSize,
                1.0F, 1.0F, packedColor);
        vertex(builder, matrix, relativeX + halfSize, relativeY, relativeZ - halfSize,
                1.0F, 0.0F, packedColor);
        return 4;
    }

    private static void emitRayQuad(BufferBuilder builder, Matrix4f matrix,
                                    float originX, float originY, float originZ,
                                    float screenDirectionX, float screenDirectionY,
                                    float length, float width, int packedColor) {
        float directionX = CAMERA_RIGHT.x * screenDirectionX + CAMERA_UP.x * screenDirectionY;
        float directionY = CAMERA_RIGHT.y * screenDirectionX + CAMERA_UP.y * screenDirectionY;
        float directionZ = CAMERA_RIGHT.z * screenDirectionX + CAMERA_UP.z * screenDirectionY;
        float perpendicularX = CAMERA_RIGHT.x * -screenDirectionY + CAMERA_UP.x * screenDirectionX;
        float perpendicularY = CAMERA_RIGHT.y * -screenDirectionY + CAMERA_UP.y * screenDirectionX;
        float perpendicularZ = CAMERA_RIGHT.z * -screenDirectionY + CAMERA_UP.z * screenDirectionX;
        float halfWidth = width * 0.5F;
        float tipX = originX + directionX * length;
        float tipY = originY + directionY * length;
        float tipZ = originZ + directionZ * length;

        // UV.x=0 is the luminous source in funeral_nova_ray.fsh, so the
        // geometry must begin at the nova center and extend only outward.
        vertex(builder, matrix,
                originX - perpendicularX * halfWidth,
                originY - perpendicularY * halfWidth,
                originZ - perpendicularZ * halfWidth,
                0.0F, 0.0F, packedColor);
        vertex(builder, matrix,
                originX + perpendicularX * halfWidth,
                originY + perpendicularY * halfWidth,
                originZ + perpendicularZ * halfWidth,
                0.0F, 1.0F, packedColor);
        vertex(builder, matrix,
                tipX + perpendicularX * halfWidth,
                tipY + perpendicularY * halfWidth,
                tipZ + perpendicularZ * halfWidth,
                1.0F, 1.0F, packedColor);
        vertex(builder, matrix,
                tipX - perpendicularX * halfWidth,
                tipY - perpendicularY * halfWidth,
                tipZ - perpendicularZ * halfWidth,
                1.0F, 0.0F, packedColor);
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
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
            if (builder == effectBuffer) {
                effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
            }
        }
    }

    private static void draw(BufferBuilder builder, @Nullable ShaderInstance shader,
                             boolean depthTest, int vertices,
                             @Nullable java.util.function.Consumer<ShaderInstance> uniforms) {
        if (shader == null || !builder.building() || vertices <= 0) {
            finish(builder);
            return;
        }
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(() -> shader);
            if (uniforms != null) uniforms.accept(shader);
            BufferBuilder.RenderedBuffer rendered = builder.end();
            int gpu = PerfTracker.beginGpu(PerfTracker.Section.CINEMATIC);
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

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               float x, float y, float z, float u, float v, int color) {
        builder.vertex(matrix, x, y, z).uv(u, v)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    private static int color(float red, float green, float blue, float alpha) {
        int r = Math.round(clamp01(red) * 255.0F);
        int g = Math.round(clamp01(green) * 255.0F);
        int b = Math.round(clamp01(blue) * 255.0F);
        int a = Math.round(clamp01(alpha) * 255.0F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    public static List<PostFrameState> postFrameStates() {
        return List.copyOf(POST_STATES);
    }

    @Nullable
    public static PostFrameState primaryPostFrameState() {
        return POST_STATES.isEmpty() ? null : POST_STATES.get(0);
    }

    public record PostFrameState(Vec3 center, int stage, float progress,
                                 float elapsedSeconds, float intensity, float chainFade) {
    }

    private static final class RenderStateSnapshot {
        private final boolean blendEnabled;
        private final boolean depthEnabled;
        private final boolean cullEnabled;
        private final boolean depthWrite;
        private final int blendSourceRgb;
        private final int blendDestinationRgb;
        private final int blendSourceAlpha;
        private final int blendDestinationAlpha;
        private final ShaderInstance shader;
        private final float[] shaderColor;

        private RenderStateSnapshot(boolean blendEnabled, boolean depthEnabled, boolean cullEnabled,
                                    boolean depthWrite, int blendSourceRgb, int blendDestinationRgb,
                                    int blendSourceAlpha, int blendDestinationAlpha,
                                    ShaderInstance shader, float[] shaderColor) {
            this.blendEnabled = blendEnabled;
            this.depthEnabled = depthEnabled;
            this.cullEnabled = cullEnabled;
            this.depthWrite = depthWrite;
            this.blendSourceRgb = blendSourceRgb;
            this.blendDestinationRgb = blendDestinationRgb;
            this.blendSourceAlpha = blendSourceAlpha;
            this.blendDestinationAlpha = blendDestinationAlpha;
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
                    RenderSystem.getShader(),
                    RenderSystem.getShaderColor().clone());
        }

        private void restore() {
            RenderSystem.blendFuncSeparate(
                    blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha);
            if (blendEnabled) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (depthEnabled) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthWrite);
            if (cullEnabled) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            if (shader != null) RenderSystem.setShader(() -> shader);
        }
    }
}
