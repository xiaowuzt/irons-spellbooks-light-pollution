package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.SingularityEntity;
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
 * Draws Singularity: the solid body at its centre, the accretion glow around it,
 * and the bolts lashing off it.
 *
 * <p>The shockwaves are deliberately NOT here. They were analytic emissive shells
 * first, which read as glowing bubbles; they are now screen-space refraction in
 * {@link SpellLightPostProcessor}, because what a singularity does to the view is
 * bend it rather than light it.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SingularityWorldRenderer {
    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;
    private static final int BUFFER_CAPACITY = 262_144;
    /** How far past the core its halo extends, to carry the infalling glow. */
    private static final float CORE_HULL_SCALE = 3.0F;
    private static final double RENDER_DISTANCE = 256.0D;
    private static final double RENDER_DISTANCE_SQR = RENDER_DISTANCE * RENDER_DISTANCE;
    /** Ticks the detonation's shake lasts. */
    private static final float SHAKE_TICKS = 26.0F;
    /** Peak shake amplitude, in degrees. */
    private static final float SHAKE_STRENGTH = 8.0F;
    /** Distance at which the detonation can no longer be felt, in blocks. */
    private static final double SHAKE_RANGE = 80.0D;
    /**
     * The body's texture, from Some of FX's singularity bomb
     * (github.com/YangMao-Minister/some_of_fx, MIT, (c) 2026 Pizuka). The UV
     * layout below is that model's: a 16x16 sheet split into quarters, one for the
     * cage, one for its outline, one for the inner core.
     */
    private static final net.minecraft.resources.ResourceLocation CORE_TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    ExampleMod.MODID, "textures/effect/singularity_core.png");
    /** Half-extents of the three nested cubes, in the model's own 16ths. */
    private static final float OUTLINE_EXTENT = 2.2F;
    private static final float CAGE_EXTENT = 2.0F;
    private static final float INNER_EXTENT = 1.5F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
    private static float[][] unitSphereVertices;

    private SingularityWorldRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        List<SingularityEntity> effects = SpellLightEmitter.collectSingularities();
        if (effects.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance sphereShader = ConstellationShaders.singularity();
        if (minecraft.level == null || sphereShader == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        GlState state = GlState.capture();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(event.getPoseStack().last().pose());
            RenderSystem.applyModelViewMatrix();

            for (SingularityEntity entity : effects) {
                Vec3 centre = entity.coreCentre(partialTick);
                if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                    continue;
                }
                drawCore(camera, entity, partialTick, sphereShader);
                ShaderInstance bodyShader = ConstellationShaders.singularityCore();
                if (bodyShader != null) {
                    drawBody(camera, entity, partialTick, bodyShader);
                }
            }

            ShaderInstance boltShader = ConstellationShaders.bolt();
            if (boltShader != null) {
                BufferBuilder bolts = begin();
                int boltVertices = 0;
                for (SingularityEntity entity : effects) {
                    if (camera.distanceToSqr(entity.coreCentre(partialTick))
                            > RENDER_DISTANCE_SQR) {
                        continue;
                    }
                    boltVertices += emitBolts(bolts, camera, entity, partialTick);
                }
                drawBolts(bolts, boltShader, boltVertices);
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawCore(Vec3 camera, SingularityEntity entity,
                                 float partialTick, ShaderInstance shader) {
        float radius = entity.coreRadius(partialTick);
        float brightness = entity.coreBrightness(partialTick);
        if (radius <= 0.02F || brightness <= 0.02F) {
            return;
        }
        // g in the low band selects the core branch.
        emitSphere(camera, entity.coreCentre(partialTick), radius, CORE_HULL_SCALE,
                entity.charge(partialTick), 0.15F,
                Mth.clamp(brightness * 0.3F, 0.0F, 1.0F), 1.0F, shader);
    }

    /** One analytic sphere: a hull large enough to contain it, and its centre. */
    private static void emitSphere(Vec3 camera, Vec3 centre, float radius, float hullScale,
                                   float progress, float mode, float intensity, float fade,
                                   ShaderInstance shader) {
        int packed = color(progress, mode, intensity, fade);
        float relativeX = (float) (centre.x - camera.x);
        float relativeY = (float) (centre.y - camera.y);
        float relativeZ = (float) (centre.z - camera.z);
        Vector3f sphereCenter = new Vector3f(relativeX, relativeY, relativeZ);

        float hullRadius = radius * hullScale;
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

    /**
     * The solid body at the centre: three nested cubes, tumbling.
     *
     * <p>This is the only part of the spell that writes depth and blends
     * normally. Additive glow cannot produce a dark core, because black adds
     * nothing, so without a real occluding body the hole was only ever the ring
     * around it.</p>
     */
    private static void drawBody(Vec3 camera, SingularityEntity entity,
                                 float partialTick, ShaderInstance shader) {
        float radius = entity.coreRadius(partialTick);
        if (radius <= 0.02F) {
            return;
        }
        // Scale so the outermost cube matches the core's radius.
        float unit = radius / OUTLINE_EXTENT;
        float age = entity.getVisualAgeTicks(partialTick);
        float charge = entity.charge(partialTick);
        // Spins faster as it charges, which is most of what makes it read as
        // winding up rather than just sitting there.
        float spin = age * (0.02F + charge * 0.10F);
        float tumble = age * 0.013F;

        Vec3 centre = entity.coreCentre(partialTick);
        float x = (float) (centre.x - camera.x);
        float y = (float) (centre.y - camera.y);
        float z = (float) (centre.z - camera.z);

        // Darkens toward the collapse: the shell is being crushed onto whatever
        // is inside it.
        int cage = color(0.55F - charge * 0.35F, 0.30F - charge * 0.22F,
                0.85F - charge * 0.25F, 1.0F);
        int outline = color(0.85F, 0.55F + charge * 0.35F, 1.0F, 1.0F);
        int inner = color(0.10F, 0.02F, 0.16F, 1.0F);

        BufferBuilder builder = begin();
        int vertices = 0;
        vertices += emitCube(builder, x, y, z, unit * OUTLINE_EXTENT,
                spin, tumble, 0.25F, 0.0F, outline);
        vertices += emitCube(builder, x, y, z, unit * CAGE_EXTENT,
                spin, tumble, 0.0F, 0.0F, cage);
        vertices += emitCube(builder, x, y, z, unit * INNER_EXTENT,
                -spin * 1.6F, -tumble, 0.0F, 0.25F, inner);
        drawSolid(builder, shader, vertices);
    }

    /** One cube, rotated about Y then X, mapped to one quarter of the sheet. */
    private static int emitCube(BufferBuilder builder, float cx, float cy, float cz,
                                float extent, float spin, float tumble,
                                float u0, float v0, int packed) {
        float u1 = u0 + 0.25F;
        float v1 = v0 + 0.25F;
        float cosSpin = Mth.cos(spin);
        float sinSpin = Mth.sin(spin);
        float cosTumble = Mth.cos(tumble);
        float sinTumble = Mth.sin(tumble);

        // The eight corners, rotated once here rather than per face. Index bits
        // are x, y, z with -1 as 0.
        float[][] corner = new float[8][3];
        int index = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    float px = sx * extent;
                    float py = sy * extent;
                    float pz = sz * extent;
                    float rx = px * cosSpin - pz * sinSpin;
                    float rz = px * sinSpin + pz * cosSpin;
                    float ry = py * cosTumble - rz * sinTumble;
                    rz = py * sinTumble + rz * cosTumble;
                    corner[index][0] = cx + rx;
                    corner[index][1] = cy + ry;
                    corner[index][2] = cz + rz;
                    index++;
                }
            }
        }

        int[][] faces = {
                {0, 1, 3, 2},
                {4, 6, 7, 5},
                {0, 4, 5, 1},
                {2, 3, 7, 6},
                {0, 2, 6, 4},
                {1, 5, 7, 3},
        };
        float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};

        int vertices = 0;
        for (int[] face : faces) {
            for (int slot = 0; slot < 4; slot++) {
                float[] point = corner[face[slot]];
                vertex(builder, point[0], point[1], point[2],
                        uvs[slot][0], uvs[slot][1], packed);
            }
            vertices += 4;
        }
        return vertices;
    }

    /**
     * Draws the body with depth writes on and a normal blend, then hands the state
     * back the way the additive passes expect it.
     */
    private static void drawSolid(BufferBuilder builder, ShaderInstance shader,
                                  int vertices) {
        if (!builder.building() || vertices <= 0) {
            finish(builder);
            return;
        }
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();
            // The one place in this renderer that writes depth. That is the point
            // of it: the glow around the core cannot occlude anything.
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderTexture(0, CORE_TEXTURE);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        } finally {
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
        }
    }

    /**
     * The bolts lashing off the core. Directions and lengths are re-rolled on a
     * bucket that shortens as the charge builds, which is what makes them come
     * faster and faster.
     */
    private static int emitBolts(BufferBuilder builder, Vec3 camera,
                                 SingularityEntity entity, float partialTick) {
        float age = entity.getVisualAgeTicks(partialTick);
        if (age <= SingularityEntity.OPEN_END_TICK
                || age >= SingularityEntity.COLLAPSE_TICK) {
            return 0;
        }
        float charge = entity.charge(partialTick);
        Vec3 centre = entity.coreCentre(partialTick);
        int interval = entity.boltInterval(partialTick);
        int bucket = (int) (age / interval);
        // More of them at once as it winds up, not just faster.
        int count = 2 + Math.round(charge * (SingularityEntity.MAX_BOLTS - 2));

        int vertices = 0;
        for (int bolt = 0; bolt < count; bolt++) {
            Vec3 direction = entity.boltDirection(bolt, bucket);
            float length = entity.boltLength(bolt, bucket) * (0.4F + charge * 0.6F);
            vertices += SpellBoltRenderer.emit(builder, camera, centre,
                    centre.add(direction.scale(length)),
                    1.2F + charge * 1.6F, 1.0F,
                    bolt * 31 + bucket, 1.0F);
        }
        return vertices;
    }

    /** Shake from the detonation, 0 when nothing is going off. */
    public static float currentBlastShake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float shake = 0.0F;
        for (SingularityEntity entity : SpellLightEmitter.collectSingularities()) {
            float since = entity.getVisualAgeTicks(partialTick)
                    - SingularityEntity.COLLAPSE_TICK;
            if (since < 0.0F || since > SHAKE_TICKS) {
                continue;
            }
            float decay = 1.0F - since / SHAKE_TICKS;
            double distance = Math.sqrt(
                    camera.distanceToSqr(entity.coreCentre(partialTick)));
            float reach = (float) Mth.clamp(1.0D - distance / SHAKE_RANGE, 0.0D, 1.0D);
            shake = Math.max(shake, SHAKE_STRENGTH * decay * decay * reach);
        }
        return shake;
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
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        }
    }

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
    private record GlState(boolean blend, boolean depth, boolean cull, boolean depthWrite,
                           int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        private static GlState capture() {
            return new GlState(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        }

        private void restore() {
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depth) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.depthMask(depthWrite);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
