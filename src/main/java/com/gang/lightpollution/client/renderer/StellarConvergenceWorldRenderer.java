package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.api.StellarConvergenceParams;
import com.gang.lightpollution.fx.FxRegistry;
import com.gang.lightpollution.fx.StellarConvergenceShape;
import com.gang.lightpollution.fx.StellarConvergenceSource;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws Stellar Convergence: nine star bodies, the bolts linking them, the
 * converged column and its burst.
 *
 * <p>The star bodies reuse Constellation's analytic star program — they are the
 * same kind of object, and that program already gives a hard silhouette with limb
 * darkening. The bolts go through the shared bolt program. Everything else uses
 * this spell's own.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class StellarConvergenceWorldRenderer {
    private static final int SPHERE_LAT = 12;
    private static final int SPHERE_LON = 20;
    private static final int BUFFER_CAPACITY = 262_144;
    /** How far past the body the star's hull extends, to carry its corona. */
    private static final float STAR_HULL_SCALE = 2.5F;
    /** Sides on the column's shaft. */
    private static final int COLUMN_SIDES = 6;
    /** How wide the column is, in blocks. */
    private static final float COLUMN_HALF_WIDTH = 5.0F;
    /** Ticks over which the burst ring expands. */
    private static final float BURST_TICKS = 16.0F;
    /** Radius the burst ring reaches, in blocks. */
    private static final float BURST_RADIUS = 22.0F;
    private static final double RENDER_DISTANCE = 260.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
    private static float[][] unitSphereVertices;

    private StellarConvergenceWorldRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        // A shader pack composites over the main target after this
        // stage, so under one the draw has to move to AFTER_LEVEL.
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        // The spell's own anchors plus anything another mod asked for through the API. The
        // renderer does not distinguish them, which is the point of the source interface.
        List<StellarConvergenceSource> effects =
                new java.util.ArrayList<>(SpellLightEmitter.collectConvergences());
        effects.addAll(FxRegistry.stellarConvergences());
        if (effects.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance beamShader = ConstellationShaders.convergence();
        ShaderInstance starShader = ConstellationShaders.star();
        if (minecraft.level == null || beamShader == null || starShader == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        GlStateGuard state = GlStateGuard.capture();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(SpellRenderStage.levelPose(event));
            RenderSystem.applyModelViewMatrix();

            BufferBuilder builder = begin();
            int vertices = 0;
            for (StellarConvergenceSource entity : effects) {
                if (camera.distanceToSqr(entity.shellCentre(partialTick))
                        > RENDER_DISTANCE_SQR) {
                    continue;
                }
                vertices += emitColumn(builder, camera, entity, partialTick);
                vertices += emitBurst(builder, camera, entity, partialTick);
            }
            draw(builder, beamShader, vertices, null, null);

            // The bolts are a separate program, so they get their own batch.
            ShaderInstance boltShader = ConstellationShaders.bolt();
            if (boltShader != null) {
                BufferBuilder bolts = begin();
                int boltVertices = 0;
                for (StellarConvergenceSource entity : effects) {
                    if (camera.distanceToSqr(entity.shellCentre(partialTick))
                            > RENDER_DISTANCE_SQR) {
                        continue;
                    }
                    boltVertices += emitBolts(bolts, camera, entity, partialTick);
                }
                drawBolts(bolts, boltShader, boltVertices);
            }

            for (StellarConvergenceSource entity : effects) {
                if (camera.distanceToSqr(entity.shellCentre(partialTick))
                        > RENDER_DISTANCE_SQR) {
                    continue;
                }
                drawStars(camera, entity, partialTick, starShader);
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    /**
     * Bolts linking the constellation. Each star links to the next two rather
     * than to all of them: a complete graph of nine points is 36 arcs and reads
     * as a ball of wire, not a constellation.
     */
    private static int emitBolts(BufferBuilder builder, Vec3 camera,
                                 StellarConvergenceSource entity, float partialTick) {
        float weave = StellarConvergenceShape.weaveProgress(entity.getVisualAgeTicks(partialTick));
        if (weave <= 0.01F) {
            return 0;
        }
        int vertices = 0;
        for (int star = 0; star < StellarConvergenceShape.STAR_COUNT; star++) {
            for (int step = 1; step <= 2; step++) {
                int other = (star + step) % StellarConvergenceShape.STAR_COUNT;
                float brightness = Math.min(StellarConvergenceShape.starBrightness(star, entity.getVisualAgeTicks(partialTick)),
                        StellarConvergenceShape.starBrightness(other, entity.getVisualAgeTicks(partialTick)));
                if (brightness <= 0.05F) {
                    continue;
                }
                vertices += SpellBoltRenderer.emit(builder, camera,
                        StellarConvergenceShape.starPosition(entity.shapeParams(), entity.shellCentre(partialTick), star, entity.getVisualAgeTicks(partialTick)),
                        StellarConvergenceShape.starPosition(entity.shapeParams(), entity.shellCentre(partialTick), other, entity.getVisualAgeTicks(partialTick)),
                        brightness * (step == 1 ? 1.6F : 1.0F),
                        Math.min(brightness, 1.0F),
                        star * 17 + other, weave);
            }
        }
        return vertices;
    }

    /**
     * The bolts, in their own batch. Depth-tested and additive like everything
     * else here, but through the shared bolt program rather than this spell's.
     */
    private static void drawBolts(BufferBuilder builder, ShaderInstance shader,
                                  int vertices) {
        if (!builder.building() || vertices <= 0) {
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
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // The bolt program reads its wander from a noise tile, so bind it
            // before the draw or the sampler is whatever the last pass left.
            RenderSystem.setShaderTexture(0, SpellBoltRenderer.NOISE);
            RenderSystem.setShader(() -> shader);
            if (shader.getUniform("BoltTime") != null) {
                shader.getUniform("BoltTime")
                        .set(SpellBoltRenderer.boltTime(), 0.0F, 0.0F, 0.0F);
            }
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        }
    }

    /** The converged column, a prism from the constellation down to the ground. */
    private static int emitColumn(BufferBuilder builder, Vec3 camera,
                                  StellarConvergenceSource entity, float partialTick) {
        float strength = StellarConvergenceShape.columnStrength(entity.getVisualAgeTicks(partialTick));
        if (strength <= 0.02F) {
            return 0;
        }
        Vec3 top = entity.shellCentre(partialTick);
        Vec3 ground = entity.groundCentre(partialTick);
        float phase = entity.getVisualAgeTicks(partialTick) * 0.05F;
        // g in the middle band selects the column branch.
        int packed = color(Mth.frac(phase), 0.5F,
                Mth.clamp(strength * 0.38F, 0.0F, 1.0F),
                Mth.clamp(strength, 0.0F, 1.0F));

        float topX = (float) (top.x - camera.x);
        float topY = (float) (top.y - camera.y);
        float topZ = (float) (top.z - camera.z);
        float groundX = (float) (ground.x - camera.x);
        float groundY = (float) (ground.y - camera.y);
        float groundZ = (float) (ground.z - camera.z);

        int vertices = 0;
        for (int side = 0; side < COLUMN_SIDES; side++) {
            float a0 = Mth.PI * side / COLUMN_SIDES;
            // Half a turn only: the faces are two-sided, so a full turn would draw
            // every one of them twice.
            float c0 = Mth.cos(a0) * COLUMN_HALF_WIDTH;
            float s0 = Mth.sin(a0) * COLUMN_HALF_WIDTH;

            vertex(builder, groundX - c0, groundY, groundZ - s0, 0.0F, 0.0F, packed);
            vertex(builder, topX - c0, topY, topZ - s0, 0.0F, 1.0F, packed);
            vertex(builder, topX + c0, topY, topZ + s0, 1.0F, 1.0F, packed);
            vertex(builder, groundX + c0, groundY, groundZ + s0, 1.0F, 0.0F, packed);
            vertices += 4;
        }
        return vertices;
    }

    /** The ground shock leaving the column's foot. */
    private static int emitBurst(BufferBuilder builder, Vec3 camera,
                                 StellarConvergenceSource entity, float partialTick) {
        float flash = StellarConvergenceShape.burstFlash(entity.getVisualAgeTicks(partialTick));
        if (flash <= 0.01F) {
            return 0;
        }
        float progress = 1.0F - flash;
        Vec3 ground = entity.groundCentre(partialTick);
        // g in the top band selects the burst branch.
        int packed = color(progress, 0.85F, 0.6F, flash);

        float x = (float) (ground.x - camera.x);
        float y = (float) (ground.y - camera.y) + 0.04F;
        float z = (float) (ground.z - camera.z);
        vertex(builder, x - BURST_RADIUS, y, z - BURST_RADIUS, 0.0F, 0.0F, packed);
        vertex(builder, x - BURST_RADIUS, y, z + BURST_RADIUS, 0.0F, 1.0F, packed);
        vertex(builder, x + BURST_RADIUS, y, z + BURST_RADIUS, 1.0F, 1.0F, packed);
        vertex(builder, x + BURST_RADIUS, y, z - BURST_RADIUS, 1.0F, 0.0F, packed);
        return 4;
    }

    /** The nine bodies, each its own draw so it can carry its own centre. */
    private static void drawStars(Vec3 camera, StellarConvergenceSource entity,
                                  float partialTick, ShaderInstance shader) {
        float age = entity.getVisualAgeTicks(partialTick);
        for (int star = 0; star < StellarConvergenceShape.STAR_COUNT; star++) {
            float brightness = StellarConvergenceShape.starBrightness(star, entity.getVisualAgeTicks(partialTick));
            if (brightness <= 0.02F) {
                continue;
            }
            Vec3 position = StellarConvergenceShape.starPosition(entity.shapeParams(), entity.shellCentre(partialTick), star, entity.getVisualAgeTicks(partialTick));
            float radius = StellarConvergenceShape.STAR_RADIUS
                    * (0.85F + Math.min(brightness, 1.5F) * 0.2F);
            // The body carries its own colour through TintColor. Leaving it to the
            // shader's temperature ramp made all nine bodies near-white, so only
            // the shadows on the ground were coloured and the stars themselves
            // were indistinguishable.
            float[] tint = StellarConvergenceShape.starColour(star);
            int packed = color(Mth.frac(age * 0.02F + star * 0.11F), 0.92F,
                    Mth.clamp(brightness * 0.4F, 0.0F, 1.0F),
                    Mth.clamp(brightness, 0.0F, 1.0F));

            float relativeX = (float) (position.x - camera.x);
            float relativeY = (float) (position.y - camera.y);
            float relativeZ = (float) (position.z - camera.z);
            Vector3f sphereCenter = new Vector3f(relativeX, relativeY, relativeZ);

            float hullRadius = radius * STAR_HULL_SCALE;
            float[][] sphere = unitSphere();
            BufferBuilder builder = begin();
            for (float[] point : sphere) {
                vertex(builder,
                        relativeX + point[0] * hullRadius,
                        relativeY + point[1] * hullRadius,
                        relativeZ + point[2] * hullRadius,
                        radius, 0.0F, packed);
            }
            draw(builder, shader, sphere.length, sphereCenter, tint);
        }
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
    }

    private static void vertex(BufferBuilder builder,
                               float x, float y, float z, float u, float v, int color) {
        builder.vertex(x, y, z).uv(u, v)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices,
                             Vector3f sphereCenter, float[] tint) {
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
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(() -> shader);
            if (sphereCenter != null && shader.getUniform("SphereCenter") != null) {
                shader.getUniform("SphereCenter")
                        .set(sphereCenter.x, sphereCenter.y, sphereCenter.z);
            }
            if (shader.getUniform("TintColor") != null) {
                shader.getUniform("TintColor").set(
                        tint == null ? 1.0F : tint[0],
                        tint == null ? 1.0F : tint[1],
                        tint == null ? 1.0F : tint[2],
                        1.0F);
            }
            BufferUploader.drawWithShader(builder.end());
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
            if (builder == effectBuffer) {
                effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
            }
        }
    }

    private static int color(float red, float green, float blue, float alpha) {
        int r = Math.round(Mth.clamp(red, 0.0F, 1.0F) * 255.0F);
        int g = Math.round(Mth.clamp(green, 0.0F, 1.0F) * 255.0F);
        int b = Math.round(Mth.clamp(blue, 0.0F, 1.0F) * 255.0F);
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float[][] unitSphere() {
        if (unitSphereVertices != null) {
            return unitSphereVertices;
        }
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
        double sinTheta = Math.sin(theta);
        return new float[] {
                (float) (sinTheta * Math.cos(phi)),
                (float) Math.cos(theta),
                (float) (sinTheta * Math.sin(phi))};
    }

    /** Everything this renderer changes has to go back exactly as it was. */
}
