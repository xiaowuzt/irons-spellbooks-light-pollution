package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.ConstellationEntity;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;

/**
 * Draws Constellation's star as real geometry.
 *
 * <p>The star has to be an actual body, not a particle: it is the subject of the
 * spell and the viewer follows it around its orbit. A dedicated program
 * intersects the eye ray with the star's sphere, so the disc has a hard
 * silhouette with limb darkening and granulation, and the hull beyond the disc
 * becomes corona.</p>
 *
 * <p>The hull is drawn larger than the star so there is geometry for that corona
 * to land on; the fragment stage decides where the body ends.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ConstellationWorldRenderer {
    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;
    private static final int BUFFER_CAPACITY = 8_192;
    /** How far past the photosphere the hull extends, to carry the corona. */
    private static final float CORONA_HULL_SCALE = 2.6F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
    private static float[][] unitSphereVertices;

    private ConstellationWorldRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        List<ConstellationEntity> stars = SpellLightEmitter.collectConstellations();
        if (stars.isEmpty()) {
            return;
        }
        ShaderInstance shader = ConstellationShaders.star();
        if (shader == null || Minecraft.getInstance().level == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();

        // The camera rotation belongs in ModelViewMat, and the vertices below are
        // submitted raw in camera-relative space -- the same convention as
        // ChromaticAccretionWorldRenderer and StargraveSingularityWorldRenderer.
        // This renderer used to bake the event pose into the vertices instead and
        // leave ModelViewMat to whatever the previous stage had left behind, so
        // when that turned out to be the camera rotation the star was drawn
        // rotated twice -- away from the position its own wake particles use.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(poseStack.last().pose());
            RenderSystem.applyModelViewMatrix();
            for (ConstellationEntity entity : stars) {
                for (int star = 0; star < ConstellationEntity.STAR_COUNT; star++) {
                    drawStar(camera, entity, star, partialTick, shader);
                }
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawStar(Vec3 camera, ConstellationEntity entity,
                                 int star, float partialTick, ShaderInstance shader) {
        float brightness = entity.starBrightness(star, partialTick);
        if (brightness <= 0.02F) {
            return;
        }
        Vec3 position = entity.starPosition(star, partialTick);
        float age = entity.getVisualAgeTicks(partialTick);

        // A slow swell and a slow rise in temperature across the star's life, so
        // it reads as something building rather than a fixed prop, and shrinks
        // back as it burns out.
        float lifetime = Mth.clamp(age / ConstellationEntity.LIFETIME_TICKS, 0.0F, 1.0F);
        float radius = ConstellationEntity.STAR_BODY_RADIUS
                * (1.0F + lifetime * 0.18F) * Mth.clamp(brightness, 0.35F, 1.0F);
        float heat = Mth.clamp(0.7F + lifetime * 0.25F, 0.0F, 1.0F);

        float alpha = Mth.clamp(brightness, 0.0F, 1.0F);
        // r carries a time seed for the granulation, g the colour temperature,
        // b the brightness, a the fade.
        float seed = age * 0.025F;
        int packed = color(Mth.frac(seed), heat,
                Mth.clamp(brightness * 0.45F, 0.0F, 1.0F), alpha);

        float relativeX = (float) (position.x - camera.x);
        float relativeY = (float) (position.y - camera.y);
        float relativeZ = (float) (position.z - camera.z);
        // Raw camera-relative: the vertex stage puts both this and the vertices
        // into view space with the one ModelViewMat set up in render().
        Vector3f sphereCenter = new Vector3f(relativeX, relativeY, relativeZ);

        // The hull is wider than the star so the corona has somewhere to draw.
        // UV0.x still carries the true radius, which is what the ray/sphere test
        // uses to find the photosphere.
        float hullRadius = radius * CORONA_HULL_SCALE;
        float[][] sphere = unitSphere();
        BufferBuilder builder = begin();
        for (float[] point : sphere) {
            vertex(builder,
                    relativeX + point[0] * hullRadius,
                    relativeY + point[1] * hullRadius,
                    relativeZ + point[2] * hullRadius,
                    radius, 0.0F, packed);
        }
        draw(builder, shader, sphere.length, sphereCenter);
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
                             Vector3f sphereCenter) {
        if (!builder.building() || vertices <= 0) {
            finish(builder);
            return;
        }
        // Additive blending was previously left switched on. This runs inside
        // LevelRenderer, so the leak reached whatever it drew next.
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthWasWriting = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
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
            if (shader.getUniform("SphereCenter") != null) {
                shader.getUniform("SphereCenter")
                        .set(sphereCenter.x, sphereCenter.y, sphereCenter.z);
            }
            // Set every time rather than relying on the JSON default: uniforms
            // persist on the ShaderInstance between draws, and Stellar
            // Convergence tints this same program per star.
            if (shader.getUniform("TintColor") != null) {
                shader.getUniform("TintColor").set(1.0F, 1.0F, 1.0F, 1.0F);
            }
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        } finally {
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blendWasEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthWasEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cullWasEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.depthMask(depthWasWriting);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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
}
