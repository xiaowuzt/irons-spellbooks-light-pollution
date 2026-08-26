package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.EclipseSeveranceShaders;
import com.gang.lightpollution.entity.EclipseSeveranceEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Procedural neon slash renderer for Eclipse Severance.
 *
 * <p>The visual composition is an adapted visual algorithm inspired by
 * Gemini's layered sweeping-attack effect. All Forge 1.20.1 geometry, timing,
 * batching, and state management here is an independent implementation. It
 * intentionally performs no framebuffer, scene-color, or depth-texture copy.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EclipseSeveranceWorldRenderer {
    private static final double MAX_RENDER_DISTANCE = 64.0D;
    private static final double MAX_RENDER_DISTANCE_SQR = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    private static final int MAX_VISIBLE_EFFECTS = 6;
    private static final float HALF_ARC_RADIANS = (float) Math.toRadians(75.0D);

    private static final Rgb CYAN = new Rgb(100.0F, 216.0F, 255.0F);
    private static final Rgb BLUE = new Rgb(88.0F, 148.0F, 255.0F);
    private static final Rgb VIOLET = new Rgb(194.0F, 124.0F, 255.0F);
    private static final Rgb WHITE = new Rgb(255.0F, 255.0F, 255.0F);

    private static final Lod HIGH = new Lod(52, 34, 78, 6, 64);
    private static final Lod MEDIUM = new Lod(38, 24, 52, 6, 48);
    private static final Lod LOW = new Lod(26, 15, 30, 4, 32);

    private static final Set<EclipseSeveranceEntity> ACTIVE_EFFECTS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<EclipseSeveranceEntity> FRAME_EFFECTS = new ArrayList<>();

    private EclipseSeveranceWorldRenderer() {
    }

    @SubscribeEvent
    public static void renderEffects(RenderLevelStageEvent event) {
        if (EclipseSeveranceShaders.arcShader() != null
                && EclipseSeveranceShaders.particleShader() != null) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ACTIVE_EFFECTS.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        collectVisibleEffects(event, minecraft, cameraPosition);
        if (FRAME_EFFECTS.isEmpty()) {
            return;
        }

        RenderStateSnapshot state = RenderStateSnapshot.capture();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        GeometryBatch batch = new GeometryBatch(builder, event.getPoseStack().last().pose());

        try {
            configureAdditiveWorldState();
            for (EclipseSeveranceEntity effect : FRAME_EFFECTS) {
                double distanceSquared = cameraPosition.distanceToSqr(effect.position());
                Lod lod = selectLod(distanceSquared, FRAME_EFFECTS.size());
                renderEffect(batch, effect, camera, cameraPosition, event.getPartialTick(), lod);
            }

            if (batch.vertexCount > 0) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                BufferUploader.drawWithShader(builder.end());
            }
        } finally {
            if (builder.building()) {
                builder.discard();
            }
            state.restore();
            FRAME_EFFECTS.clear();
        }
    }

    private static void collectVisibleEffects(RenderLevelStageEvent event, Minecraft minecraft,
                                              Vec3 cameraPosition) {
        FRAME_EFFECTS.clear();
        ACTIVE_EFFECTS.removeIf(effect -> !effect.isAlive() || effect.level() != minecraft.level);
        for (EclipseSeveranceEntity effect : ACTIVE_EFFECTS) {
            if (cameraPosition.distanceToSqr(effect.position()) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            if (!event.getFrustum().isVisible(effect.getBoundingBox().inflate(13.0D, 7.0D, 13.0D))) {
                continue;
            }
            FRAME_EFFECTS.add(effect);
        }

        FRAME_EFFECTS.sort(Comparator.comparingDouble(effect -> cameraPosition.distanceToSqr(effect.position())));
        if (FRAME_EFFECTS.size() > MAX_VISIBLE_EFFECTS) {
            FRAME_EFFECTS.subList(MAX_VISIBLE_EFFECTS, FRAME_EFFECTS.size()).clear();
        }
    }

    private static Lod selectLod(double distanceSquared, int activeCount) {
        if (distanceSquared > 46.0D * 46.0D || activeCount >= 5) {
            return LOW;
        }
        if (distanceSquared > 26.0D * 26.0D || activeCount >= 3) {
            return MEDIUM;
        }
        return HIGH;
    }

    private static void renderEffect(GeometryBatch batch, EclipseSeveranceEntity effect, Camera camera,
                                     Vec3 cameraPosition, float partialTick, Lod lod) {
        float age = Mth.clamp(effect.tickCount + partialTick, 0.0F, EclipseSeveranceEntity.LIFETIME_TICKS);
        if (age <= 0.01F) {
            return;
        }

        double x = Mth.lerp(partialTick, effect.xOld, effect.getX()) - cameraPosition.x;
        double y = Mth.lerp(partialTick, effect.yOld, effect.getY()) - cameraPosition.y;
        double z = Mth.lerp(partialTick, effect.zOld, effect.getZ()) - cameraPosition.z;
        Transform transform = Transform.create(x, y, z, effect.getFacingYaw(), camera);
        int seed = effect.getSeed();

        drawLayeredArcs(batch, transform, age, seed, lod);
        drawSpeedLines(batch, transform, age, seed, lod);
        drawSparks(batch, transform, age, seed, lod);
        drawLightning(batch, transform, age, seed, lod);
        drawShockwaveRings(batch, transform, age, seed, lod);
        drawCoreBurst(batch, transform, age, seed);
    }

    private static void drawLayeredArcs(GeometryBatch batch, Transform transform, float age,
                                        int seed, Lod lod) {
        for (int echo = 3; echo >= 0; echo--) {
            float delayedAge = age - echo * 1.35F;
            if (delayedAge <= 0.0F) {
                continue;
            }

            float reveal = easeOutCubic(Mth.clamp(delayedAge / EclipseSeveranceEntity.DAMAGE_TICK, 0.0F, 1.0F));
            float trailLength = Mth.lerp(smoothstep(0.0F, 1.0F, reveal), 0.24F, 1.0F);
            float trailStart = Math.max(0.0F, reveal - trailLength);
            float fade = 1.0F - smoothstep(10.0F, 27.0F, delayedAge);
            float echoAlpha = echo == 0 ? 1.0F : (float) Math.pow(0.56F, echo);
            float radiusOffset = -echo * 0.16F;
            float heightOffset = echo * 0.045F;

            for (int layer = 0; layer < 4; layer++) {
                float halfWidth = switch (layer) {
                    case 0 -> 0.82F;
                    case 1 -> 0.52F;
                    case 2 -> 0.27F;
                    default -> 0.085F;
                };
                float layerAlpha = switch (layer) {
                    case 0 -> 0.13F;
                    case 1 -> 0.24F;
                    case 2 -> 0.46F;
                    default -> 0.92F;
                };
                float layerRadius = radiusOffset - layer * 0.035F;
                drawArcBand(batch, transform, age, seed, lod.arcSegments, trailStart, reveal,
                        layerRadius, heightOffset, halfWidth,
                        layerAlpha * echoAlpha * fade, layer);
            }
        }
    }

    private static void drawArcBand(GeometryBatch batch, Transform transform, float age, int seed,
                                    int segments, float trailStart, float trailEnd,
                                    float radiusOffset, float heightOffset, float halfWidth,
                                    float alpha, int layer) {
        if (alpha <= 0.003F || trailEnd <= trailStart) {
            return;
        }

        for (int segment = 0; segment < segments; segment++) {
            float t0 = segment / (float) segments;
            float t1 = (segment + 1.0F) / segments;
            if (t1 < trailStart || t0 > trailEnd) {
                continue;
            }
            t0 = Mth.clamp(t0, trailStart, trailEnd);
            t1 = Mth.clamp(t1, trailStart, trailEnd);
            if (t1 - t0 <= 0.0001F) {
                continue;
            }

            ArcPoint p0 = arcPoint(t0, radiusOffset, heightOffset, seed);
            ArcPoint p1 = arcPoint(t1, radiusOffset, heightOffset, seed);
            float head0 = smoothstep(trailStart, Math.min(trailEnd, trailStart + 0.20F), t0)
                    * (1.0F - smoothstep(Math.max(trailStart, trailEnd - 0.10F), trailEnd, t0));
            float head1 = smoothstep(trailStart, Math.min(trailEnd, trailStart + 0.20F), t1)
                    * (1.0F - smoothstep(Math.max(trailStart, trailEnd - 0.10F), trailEnd, t1));
            float energy0 = 0.82F + 0.18F * Mth.sin(t0 * 31.0F - age * 1.7F + seed * 0.013F);
            float energy1 = 0.82F + 0.18F * Mth.sin(t1 * 31.0F - age * 1.7F + seed * 0.013F);
            Rgb color0 = arcColor(t0, layer, energy0);
            Rgb color1 = arcColor(t1, layer, energy1);

            batch.quad(transform,
                    p0.x, p0.y - halfWidth, p0.z,
                    p1.x, p1.y - halfWidth, p1.z,
                    p1.x, p1.y + halfWidth, p1.z,
                    p0.x, p0.y + halfWidth, p0.z,
                    color0, alpha * head0,
                    color1, alpha * head1,
                    color1, alpha * head1,
                    color0, alpha * head0);
        }
    }

    private static ArcPoint arcPoint(float t, float radiusOffset, float heightOffset, int seed) {
        float angle = Mth.lerp(t, -HALF_ARC_RADIANS, HALF_ARC_RADIANS);
        // Match the server-side 12-block hit volume with a visible outer blade.
        float radius = 10.0F + radiusOffset + Mth.sin(t * Mth.PI) * 1.05F;
        float turbulence = (random01(seed, Mth.floor(t * 37.0F), 19) - 0.5F) * 0.10F;
        float x = Mth.sin(angle) * radius;
        float z = Mth.cos(angle) * radius;
        float y = 1.32F + heightOffset + Mth.sin(t * Mth.PI) * 1.02F
                + Mth.sin(t * Mth.TWO_PI * 2.0F + seed * 0.007F) * 0.11F + turbulence;
        return new ArcPoint(x, y, z);
    }

    private static Rgb arcColor(float t, int layer, float energy) {
        Rgb gradient = mix(CYAN, VIOLET, smoothstep(0.08F, 0.92F, t));
        gradient = mix(gradient, BLUE, 0.16F * (1.0F - Mth.sin(t * Mth.PI)));
        float coreMix = layer / 3.0F;
        return mix(gradient, WHITE, coreMix * coreMix * 0.92F).scale(energy);
    }

    private static void drawSpeedLines(GeometryBatch batch, Transform transform, float age,
                                       int seed, Lod lod) {
        float globalAlpha = smoothstep(0.8F, 3.0F, age) * (1.0F - smoothstep(9.0F, 17.0F, age));
        if (globalAlpha <= 0.003F) {
            return;
        }

        for (int i = 0; i < lod.speedLines; i++) {
            float spawn = random01(seed, i, 31) * 4.0F;
            float life = age - spawn;
            if (life <= 0.0F || life >= 11.0F) {
                continue;
            }
            float angle = Mth.lerp(random01(seed, i, 32), -HALF_ARC_RADIANS * 1.08F,
                    HALF_ARC_RADIANS * 1.08F);
            float speed = Mth.lerp(random01(seed, i, 33), 0.34F, 0.72F);
            float startRadius = 0.75F + speed * life * 1.18F;
            float length = Mth.lerp(random01(seed, i, 34), 0.62F, 1.82F);
            float y = Mth.lerp(random01(seed, i, 35), 0.42F, 2.95F)
                    + Mth.sin(life * 0.7F + i) * 0.08F;
            float width = Mth.lerp(random01(seed, i, 36), 0.015F, 0.045F);
            float fade = (1.0F - smoothstep(5.0F, 11.0F, life)) * globalAlpha;
            Rgb color = mix(CYAN, VIOLET, random01(seed, i, 37));

            float sx = Mth.sin(angle) * startRadius;
            float sz = Mth.cos(angle) * startRadius;
            float ex = Mth.sin(angle) * (startRadius + length);
            float ez = Mth.cos(angle) * (startRadius + length);
            drawCrossRibbon(batch, transform, sx, y, sz, ex, y, ez, width,
                    mix(color, WHITE, 0.32F), fade * 0.72F);
        }
    }

    private static void drawSparks(GeometryBatch batch, Transform transform, float age,
                                   int seed, Lod lod) {
        for (int i = 0; i < lod.particles; i++) {
            float spawn = 2.6F + random01(seed, i, 51) * 5.2F;
            float life = age - spawn;
            float maxLife = 8.0F + random01(seed, i, 52) * 9.0F;
            if (life <= 0.0F || life >= maxLife) {
                continue;
            }

            float angle = Mth.lerp(random01(seed, i, 53), -HALF_ARC_RADIANS, HALF_ARC_RADIANS);
            float radius = Mth.lerp(random01(seed, i, 54), 2.2F, 10.2F);
            float radialVelocity = Mth.lerp(random01(seed, i, 55), 0.11F, 0.38F);
            float tangentVelocity = (random01(seed, i, 56) - 0.5F) * 0.18F;
            float verticalVelocity = Mth.lerp(random01(seed, i, 57), 0.035F, 0.24F);

            float radialX = Mth.sin(angle);
            float radialZ = Mth.cos(angle);
            float tangentX = Mth.cos(angle);
            float tangentZ = -Mth.sin(angle);
            float x = radialX * (radius + radialVelocity * life) + tangentX * tangentVelocity * life;
            float z = radialZ * (radius + radialVelocity * life) + tangentZ * tangentVelocity * life;
            float y = Mth.lerp(random01(seed, i, 58), 0.55F, 2.6F)
                    + verticalVelocity * life - 0.012F * life * life;
            float size = Mth.lerp(random01(seed, i, 59), 0.045F, 0.16F)
                    * (1.0F + smoothstep(0.0F, 2.0F, life) * 0.35F);
            float alpha = smoothstep(0.0F, 0.8F, life) * (1.0F - smoothstep(maxLife * 0.45F, maxLife, life));
            Rgb color = random01(seed, i, 60) > 0.66F ? WHITE
                    : mix(CYAN, VIOLET, random01(seed, i, 61));
            drawBillboard(batch, transform, x, y, z, size, color, alpha * 0.86F);
        }
    }

    private static void drawLightning(GeometryBatch batch, Transform transform, float age,
                                      int seed, Lod lod) {
        float alpha = smoothstep(2.8F, 4.6F, age) * (1.0F - smoothstep(10.0F, 16.0F, age));
        if (alpha <= 0.003F) {
            return;
        }

        for (int bolt = 0; bolt < lod.lightningBolts; bolt++) {
            float flicker = 0.72F + 0.28F * Mth.sin(age * 7.8F + bolt * 2.41F + seed * 0.017F);
            float angle = Mth.lerp(random01(seed, bolt, 71), -HALF_ARC_RADIANS, HALF_ARC_RADIANS);
            float radius = Mth.lerp(random01(seed, bolt, 72), 2.4F, 9.6F);
            float tangentSign = random01(seed, bolt, 73) < 0.5F ? -1.0F : 1.0F;
            float startX = Mth.sin(angle) * radius;
            float startZ = Mth.cos(angle) * radius;
            float startY = Mth.lerp(random01(seed, bolt, 74), 0.78F, 2.45F);
            int segmentCount = 5 + Mth.floor(random01(seed, bolt, 75) * 2.0F);

            float previousX = startX;
            float previousY = startY;
            float previousZ = startZ;
            for (int segment = 1; segment <= segmentCount; segment++) {
                float t = segment / (float) segmentCount;
                float tangent = tangentSign * t * Mth.lerp(random01(seed, bolt, 76), 0.8F, 1.8F);
                float outward = t * Mth.lerp(random01(seed, bolt, 77), 0.65F, 1.7F);
                float jitterScale = (1.0F - Math.abs(t * 2.0F - 1.0F)) * 0.42F;
                float jitterX = (random01(seed, bolt * 11 + segment, 78) - 0.5F) * jitterScale;
                float jitterY = (random01(seed, bolt * 11 + segment, 79) - 0.5F) * jitterScale;
                float jitterZ = (random01(seed, bolt * 11 + segment, 80) - 0.5F) * jitterScale;
                float radialX = Mth.sin(angle);
                float radialZ = Mth.cos(angle);
                float tangentX = Mth.cos(angle);
                float tangentZ = -Mth.sin(angle);
                float currentX = startX + radialX * outward + tangentX * tangent + jitterX;
                float currentY = startY + (random01(seed, bolt, 81) - 0.42F) * t + jitterY;
                float currentZ = startZ + radialZ * outward + tangentZ * tangent + jitterZ;

                drawCrossRibbon(batch, transform, previousX, previousY, previousZ,
                        currentX, currentY, currentZ, 0.11F,
                        mix(CYAN, VIOLET, 0.45F), alpha * flicker * 0.22F);
                drawCrossRibbon(batch, transform, previousX, previousY, previousZ,
                        currentX, currentY, currentZ, 0.026F,
                        WHITE, alpha * flicker * 0.94F);
                previousX = currentX;
                previousY = currentY;
                previousZ = currentZ;
            }
        }
    }

    private static void drawShockwaveRings(GeometryBatch batch, Transform transform, float age,
                                           int seed, Lod lod) {
        for (int ring = 0; ring < 3; ring++) {
            float start = EclipseSeveranceEntity.DAMAGE_TICK - 0.5F + ring * 1.65F;
            float duration = 10.5F + ring * 1.5F;
            float progress = (age - start) / duration;
            if (progress <= 0.0F || progress >= 1.0F) {
                continue;
            }

            float eased = easeOutCubic(progress);
            float radius = 0.65F + eased * (10.5F + ring * 0.85F);
            float halfWidth = Mth.lerp(progress, 0.19F, 0.055F);
            float alpha = smoothstep(0.0F, 0.12F, progress)
                    * (1.0F - smoothstep(0.42F, 1.0F, progress));
            Rgb color = ring == 1 ? mix(CYAN, VIOLET, 0.72F) : mix(CYAN, WHITE, 0.22F);
            float rotation = random01(seed, ring, 91) * Mth.TWO_PI + age * (ring % 2 == 0 ? 0.012F : -0.009F);
            drawRing(batch, transform, radius, halfWidth, 0.035F + ring * 0.018F,
                    rotation, color, alpha * (0.58F - ring * 0.08F), lod.ringSegments);
        }
    }

    private static void drawRing(GeometryBatch batch, Transform transform, float radius,
                                 float halfWidth, float y, float rotation, Rgb color,
                                 float alpha, int segments) {
        float inner = Math.max(0.01F, radius - halfWidth);
        float outer = radius + halfWidth;
        for (int segment = 0; segment < segments; segment++) {
            float a0 = rotation + Mth.TWO_PI * segment / segments;
            float a1 = rotation + Mth.TWO_PI * (segment + 1.0F) / segments;
            float sin0 = Mth.sin(a0);
            float cos0 = Mth.cos(a0);
            float sin1 = Mth.sin(a1);
            float cos1 = Mth.cos(a1);
            batch.quad(transform,
                    sin0 * inner, y, cos0 * inner,
                    sin1 * inner, y, cos1 * inner,
                    sin1 * outer, y, cos1 * outer,
                    sin0 * outer, y, cos0 * outer,
                    color, alpha * 0.35F,
                    color, alpha * 0.35F,
                    mix(color, WHITE, 0.35F), alpha,
                    mix(color, WHITE, 0.35F), alpha);
        }
    }

    private static void drawCoreBurst(GeometryBatch batch, Transform transform, float age, int seed) {
        float delta = (age - EclipseSeveranceEntity.DAMAGE_TICK) / 2.4F;
        float burst = (float) Math.exp(-delta * delta * 1.8F);
        if (burst <= 0.004F) {
            return;
        }

        float cx = 0.0F;
        float cy = 1.55F;
        float cz = 5.6F;
        drawBillboard(batch, transform, cx, cy, cz, 1.35F + burst * 0.85F,
                CYAN, burst * 0.12F);
        drawBillboard(batch, transform, cx, cy, cz, 0.82F + burst * 0.55F,
                VIOLET, burst * 0.28F);
        drawBillboard(batch, transform, cx, cy, cz, 0.28F + burst * 0.34F,
                WHITE, burst * 0.96F);

        int rays = 12;
        for (int ray = 0; ray < rays; ray++) {
            float angle = Mth.TWO_PI * ray / rays + random01(seed, ray, 101) * 0.22F;
            float length = Mth.lerp(random01(seed, ray, 102), 1.1F, 3.0F) * burst;
            float width = Mth.lerp(random01(seed, ray, 103), 0.025F, 0.075F);
            drawBillboardRay(batch, transform, cx, cy, cz, angle,
                    0.18F, 0.18F + length, width,
                    mix(CYAN, WHITE, 0.64F), burst * 0.58F);
        }
    }

    private static void drawBillboard(GeometryBatch batch, Transform transform,
                                      float x, float y, float z, float size,
                                      Rgb color, float alpha) {
        float rx = transform.billboardRightX * size;
        float ry = transform.billboardRightY * size;
        float rz = transform.billboardRightZ * size;
        float ux = transform.billboardUpX * size;
        float uy = transform.billboardUpY * size;
        float uz = transform.billboardUpZ * size;
        batch.quad(transform,
                x - rx - ux, y - ry - uy, z - rz - uz,
                x + rx - ux, y + ry - uy, z + rz - uz,
                x + rx + ux, y + ry + uy, z + rz + uz,
                x - rx + ux, y - ry + uy, z - rz + uz,
                color, alpha * 0.05F,
                color, alpha * 0.05F,
                mix(color, WHITE, 0.38F), alpha,
                mix(color, WHITE, 0.38F), alpha);
    }

    private static void drawBillboardRay(GeometryBatch batch, Transform transform,
                                         float cx, float cy, float cz, float angle,
                                         float inner, float outer, float halfWidth,
                                         Rgb color, float alpha) {
        float dx = Mth.cos(angle);
        float dy = Mth.sin(angle);
        float px = -dy;
        float py = dx;

        float innerX = cx + (transform.billboardRightX * dx + transform.billboardUpX * dy) * inner;
        float innerY = cy + (transform.billboardRightY * dx + transform.billboardUpY * dy) * inner;
        float innerZ = cz + (transform.billboardRightZ * dx + transform.billboardUpZ * dy) * inner;
        float outerX = cx + (transform.billboardRightX * dx + transform.billboardUpX * dy) * outer;
        float outerY = cy + (transform.billboardRightY * dx + transform.billboardUpY * dy) * outer;
        float outerZ = cz + (transform.billboardRightZ * dx + transform.billboardUpZ * dy) * outer;
        float sideX = (transform.billboardRightX * px + transform.billboardUpX * py) * halfWidth;
        float sideY = (transform.billboardRightY * px + transform.billboardUpY * py) * halfWidth;
        float sideZ = (transform.billboardRightZ * px + transform.billboardUpZ * py) * halfWidth;

        batch.quad(transform,
                innerX - sideX, innerY - sideY, innerZ - sideZ,
                outerX - sideX * 0.12F, outerY - sideY * 0.12F, outerZ - sideZ * 0.12F,
                outerX + sideX * 0.12F, outerY + sideY * 0.12F, outerZ + sideZ * 0.12F,
                innerX + sideX, innerY + sideY, innerZ + sideZ,
                color, alpha * 0.9F,
                color, 0.0F,
                color, 0.0F,
                color, alpha * 0.9F);
    }

    private static void drawCrossRibbon(GeometryBatch batch, Transform transform,
                                        float x0, float y0, float z0,
                                        float x1, float y1, float z1,
                                        float halfWidth, Rgb color, float alpha) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float horizontalLength = Mth.sqrt(dx * dx + dz * dz);
        float sideX = horizontalLength > 1.0E-4F ? -dz / horizontalLength * halfWidth : halfWidth;
        float sideZ = horizontalLength > 1.0E-4F ? dx / horizontalLength * halfWidth : 0.0F;

        batch.quad(transform,
                x0 - sideX, y0, z0 - sideZ,
                x1 - sideX, y1, z1 - sideZ,
                x1 + sideX, y1, z1 + sideZ,
                x0 + sideX, y0, z0 + sideZ,
                color, 0.0F, color, alpha, color, alpha, color, 0.0F);
        batch.quad(transform,
                x0, y0 - halfWidth, z0,
                x1, y1 - halfWidth, z1,
                x1, y1 + halfWidth, z1,
                x0, y0 + halfWidth, z0,
                color, 0.0F, color, alpha, color, alpha, color, 0.0F);
    }

    @SubscribeEvent
    public static void renderScreenAccent(RenderGuiEvent.Post event) {
        if (EclipseSeveranceShaders.arcShader() != null
                && EclipseSeveranceShaders.particleShader() != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || ACTIVE_EFFECTS.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        float flash = 0.0F;
        float vignette = 0.0F;
        for (EclipseSeveranceEntity effect : ACTIVE_EFFECTS) {
            if (!effect.isAlive() || effect.level() != minecraft.level
                    || cameraPosition.distanceToSqr(effect.position()) > 32.0D * 32.0D) {
                continue;
            }
            float age = effect.tickCount + event.getPartialTick();
            float delta = (age - EclipseSeveranceEntity.DAMAGE_TICK) / 1.55F;
            float localFlash = (float) Math.exp(-delta * delta * 2.2F);
            float localVignette = smoothstep(1.0F, 5.0F, age)
                    * (1.0F - smoothstep(9.0F, 20.0F, age));
            flash = Math.max(flash, localFlash);
            vignette = Math.max(vignette, localVignette);
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        if (flash > 0.01F) {
            int alpha = Mth.clamp(Mth.floor(flash * 34.0F), 0, 34);
            graphics.fill(0, 0, width, height, argb(alpha, 226, 244, 255));
        }
        if (vignette > 0.01F) {
            drawEdgeVignette(graphics, width, height, vignette);
        }
    }

    private static void drawEdgeVignette(GuiGraphics graphics, int width, int height, float strength) {
        int band = Math.max(2, Math.min(width, height) / 70);
        for (int i = 0; i < 8; i++) {
            float falloff = 1.0F - i / 8.0F;
            int alpha = Mth.clamp(Mth.floor(strength * falloff * falloff * 12.0F), 0, 12);
            if (alpha <= 0) {
                continue;
            }
            int inset = i * band;
            int color = argb(alpha, 42, 16, 74);
            graphics.fill(inset, inset, width - inset, inset + band, color);
            graphics.fill(inset, height - inset - band, width - inset, height - inset, color);
            graphics.fill(inset, inset + band, inset + band, height - inset - band, color);
            graphics.fill(width - inset - band, inset + band, width - inset, height - inset - band, color);
        }
    }

    private static void configureAdditiveWorldState() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void trackEffect(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof EclipseSeveranceEntity effect) {
            ACTIVE_EFFECTS.add(effect);
        }
    }

    @SubscribeEvent
    public static void untrackEffect(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof EclipseSeveranceEntity effect) {
            ACTIVE_EFFECTS.remove(effect);
            FRAME_EFFECTS.remove(effect);
        }
    }

    @SubscribeEvent
    public static void clearOnLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE_EFFECTS.clear();
        FRAME_EFFECTS.clear();
    }

    private static float random01(int seed, int index, int channel) {
        int value = seed ^ index * 0x9E3779B9 ^ channel * 0x85EBCA6B;
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return (value & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / Math.max(1.0E-5F, edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutCubic(float value) {
        float t = 1.0F - Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - t * t * t;
    }

    private static Rgb mix(Rgb first, Rgb second, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        return new Rgb(
                Mth.lerp(t, first.r, second.r),
                Mth.lerp(t, first.g, second.g),
                Mth.lerp(t, first.b, second.b));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (Mth.clamp(alpha, 0, 255) << 24)
                | (Mth.clamp(red, 0, 255) << 16)
                | (Mth.clamp(green, 0, 255) << 8)
                | Mth.clamp(blue, 0, 255);
    }

    private record Lod(int arcSegments, int speedLines, int particles,
                       int lightningBolts, int ringSegments) {
    }

    private record ArcPoint(float x, float y, float z) {
    }

    private record Rgb(float r, float g, float b) {
        private Rgb scale(float amount) {
            return new Rgb(r * amount, g * amount, b * amount);
        }
    }

    private static final class Transform {
        private final double originX;
        private final double originY;
        private final double originZ;
        private final float rightX;
        private final float rightZ;
        private final float forwardX;
        private final float forwardZ;
        private final float billboardRightX;
        private final float billboardRightY;
        private final float billboardRightZ;
        private final float billboardUpX;
        private final float billboardUpY;
        private final float billboardUpZ;

        private Transform(double originX, double originY, double originZ,
                          float rightX, float rightZ, float forwardX, float forwardZ,
                          float billboardRightX, float billboardRightY, float billboardRightZ,
                          float billboardUpX, float billboardUpY, float billboardUpZ) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.rightX = rightX;
            this.rightZ = rightZ;
            this.forwardX = forwardX;
            this.forwardZ = forwardZ;
            this.billboardRightX = billboardRightX;
            this.billboardRightY = billboardRightY;
            this.billboardRightZ = billboardRightZ;
            this.billboardUpX = billboardUpX;
            this.billboardUpY = billboardUpY;
            this.billboardUpZ = billboardUpZ;
        }

        private static Transform create(double originX, double originY, double originZ,
                                        float yawDegrees, Camera camera) {
            float yaw = yawDegrees * Mth.DEG_TO_RAD;
            float rightX = Mth.cos(yaw);
            float rightZ = Mth.sin(yaw);
            float forwardX = -Mth.sin(yaw);
            float forwardZ = Mth.cos(yaw);

            Vector3f cameraRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.rotation());
            Vector3f cameraUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.rotation());
            return new Transform(
                    originX, originY, originZ,
                    rightX, rightZ, forwardX, forwardZ,
                    cameraRight.x * rightX + cameraRight.z * rightZ,
                    cameraRight.y,
                    cameraRight.x * forwardX + cameraRight.z * forwardZ,
                    cameraUp.x * rightX + cameraUp.z * rightZ,
                    cameraUp.y,
                    cameraUp.x * forwardX + cameraUp.z * forwardZ);
        }

        private float worldX(float localX, float localZ) {
            return (float) (originX + rightX * localX + forwardX * localZ);
        }

        private float worldY(float localY) {
            return (float) (originY + localY);
        }

        private float worldZ(float localX, float localZ) {
            return (float) (originZ + rightZ * localX + forwardZ * localZ);
        }
    }

    private static final class GeometryBatch {
        private final BufferBuilder builder;
        private final Matrix4f matrix;
        private int vertexCount;

        private GeometryBatch(BufferBuilder builder, Matrix4f matrix) {
            this.builder = builder;
            this.matrix = matrix;
        }

        private void quad(Transform transform,
                          float x0, float y0, float z0,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          Rgb color0, float alpha0,
                          Rgb color1, float alpha1,
                          Rgb color2, float alpha2,
                          Rgb color3, float alpha3) {
            vertex(transform, x0, y0, z0, color0, alpha0);
            vertex(transform, x1, y1, z1, color1, alpha1);
            vertex(transform, x2, y2, z2, color2, alpha2);
            vertex(transform, x3, y3, z3, color3, alpha3);
        }

        private void vertex(Transform transform, float x, float y, float z, Rgb color, float alpha) {
            builder.vertex(matrix, transform.worldX(x, z), transform.worldY(y), transform.worldZ(x, z))
                    .color(toColorByte(color.r), toColorByte(color.g), toColorByte(color.b),
                            toColorByte(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F))
                    .endVertex();
            vertexCount++;
        }

        private static int toColorByte(float value) {
            return Mth.clamp(Mth.floor(value + 0.5F), 0, 255);
        }
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
            if (blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(depthWrite);
            if (cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
            }
        }
    }
}
