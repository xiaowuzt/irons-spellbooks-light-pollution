package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.HelixNebulaEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Draws the Helix Nebula: two nested rings of cometary knots, each with its tail pointing
 * radially away from the white dwarf at the centre.
 *
 * <p>One quad per knot, all in a single buffer. The quad is oriented so the shader's
 * along-axis runs outward from the centre, which is what puts every head on the star-facing
 * side without the shader needing to know where the star is.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class HelixNebulaWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 256.0D * 256.0D;
    /** Length of a knot including its tail, in blocks. */
    private static final double KNOT_LENGTH = 3.6D;
    /**
     * Where along the quad the knot's head sits, as a fraction.
     *
     * <p>Must match {@code HS_HEAD_AT} in helix_knot.fsh. One number split across two files:
     * the shader decides where to draw the head, this decides how far the quad extends behind
     * it, and if they disagree the head lands off centre or gets clipped again.</p>
     */
    private static final double HEAD_MARGIN = 0.26D;
    /** Half-width of a knot's quad, in blocks. */
    private static final double KNOT_HALF_WIDTH = 1.05D;

    /** Inner ring: doubly ionised oxygen, blue-green. */
    private static final float INNER_R = 0.36F;
    private static final float INNER_G = 0.94F;
    private static final float INNER_B = 0.82F;
    /** Outer ring: hydrogen and nitrogen, red. */
    private static final float OUTER_R = 1.00F;
    private static final float OUTER_G = 0.36F;
    private static final float OUTER_B = 0.30F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private HelixNebulaWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<HelixNebulaEntity> nebulae = SpellLightEmitter.collectHelixNebulae();
        if (nebulae.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.helixKnot();
        if (minecraft.level == null || shader == null) {
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
            drawKnots(nebulae, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawKnots(List<HelixNebulaEntity> nebulae, Vec3 camera,
                                  float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;

        for (HelixNebulaEntity entity : nebulae) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);
            double inner = entity.shellRadius(age);
            double outer = inner * HelixNebulaEntity.OUTER_RING_SCALE;
            // The white dwarf. It is what ionises every knot in the shell, and it was not
            // being drawn at all.
            EffectCore.add(centre, 1.5D, 0.80F, 0.92F, 1.00F, brightness * 1.9F);

            // A stable frame for the rings, tilted so the pair reads as an oval eye
            // rather than a circle seen face-on.
            double azimuth = Math.toRadians(entity.azimuth());
            double tilt = Math.toRadians(HelixNebulaEntity.RING_INCLINATION);
            Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(azimuth), Math.cos(tilt),
                    Math.sin(tilt) * Math.sin(azimuth)).normalize();
            Vec3 axisU = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (axisU.lengthSqr() < 1.0e-6D) {
                axisU = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
            }
            axisU = axisU.normalize();
            Vec3 axisV = normal.cross(axisU).normalize();

            int seed = entity.getSeed();
            for (int i = 0; i < HelixNebulaEntity.KNOT_COUNT; ++i) {
                boolean outerRing = (i & 1) == 0;
                double ringRadius = outerRing ? outer : inner;

                // Placement hashed off the synced seed, so both sides agree and every cast
                // has its knots somewhere different.
                double angle = hash(seed, i, 1) * Math.PI * 2.0D;
                // Scattered through the shell's thickness rather than pinned to a circle,
                // because the knots occupy a layer, not a wire.
                double radial = ringRadius
                        + (hash(seed, i, 2) - 0.5D) * HelixNebulaEntity.SHELL_THICKNESS * 2.0D;
                double outOfPlane = (hash(seed, i, 3) - 0.5D)
                        * HelixNebulaEntity.SHELL_THICKNESS * 1.4D;

                Vec3 radialDir = axisU.scale(Math.cos(angle))
                        .add(axisV.scale(Math.sin(angle)));
                Vec3 at = centre.add(radialDir.scale(radial))
                        .add(normal.scale(outOfPlane));
                if (camera.distanceToSqr(at) > RENDER_DISTANCE_SQR) {
                    continue;
                }

                float shade = 0.55F + 0.45F * (float) hash(seed, i, 4);
                vertices += knot(builder, camera, at, radialDir, brightness * shade,
                        outerRing);
            }
        }
        draw(builder, shader, vertices);
    }

    /**
     * One knot, as a quad whose long axis runs radially outward.
     *
     * <p>The tail direction is the geometry, not a texture: the quad is built along the
     * outward radial vector, so the shader's {@code along} axis is guaranteed to point away
     * from the star for every knot without the shader being told where the star is.</p>
     */
    private static int knot(BufferBuilder builder, Vec3 camera, Vec3 at, Vec3 outward,
                            float intensity, boolean outerRing) {
        Vec3 toCamera = camera.subtract(at);
        Vec3 across = outward.cross(toCamera);
        if (across.lengthSqr() < 1.0e-8D) {
            return 0;
        }
        across = across.normalize().scale(KNOT_HALF_WIDTH);
        Vec3 downwind = outward.normalize();

        // The quad reaches back past the knot, not just forward from it. The shader places the
        // head at HEAD_MARGIN of the way along; with the quad starting exactly at the knot the
        // head's gaussian was half outside it and every knot rendered as a clean-cut
        // hemisphere. HEAD_MARGIN here and HS_HEAD_AT in helix_knot.fsh are the same number.
        double total = KNOT_LENGTH / (1.0D - HEAD_MARGIN);
        Vec3 back = downwind.scale(-total * HEAD_MARGIN);
        Vec3 front = downwind.scale(total * (1.0D - HEAD_MARGIN));

        int colour = colour(intensity, outerRing);
        float hx = (float) (at.x + back.x - camera.x);
        float hy = (float) (at.y + back.y - camera.y);
        float hz = (float) (at.z + back.z - camera.z);
        float tx = (float) (at.x + front.x - camera.x);
        float ty = (float) (at.y + front.y - camera.y);
        float tz = (float) (at.z + front.z - camera.z);
        float ax = (float) across.x;
        float ay = (float) across.y;
        float az = (float) across.z;

        vertex(builder, hx - ax, hy - ay, hz - az, 0.0F, 0.0F, colour);
        vertex(builder, tx - ax, ty - ay, tz - az, 1.0F, 0.0F, colour);
        vertex(builder, tx + ax, ty + ay, tz + az, 1.0F, 1.0F, colour);
        vertex(builder, hx + ax, hy + ay, hz + az, 0.0F, 1.0F, colour);
        return 4;
    }

    private static int colour(float intensity, boolean outerRing) {
        float r = outerRing ? OUTER_R : INNER_R;
        float g = outerRing ? OUTER_G : INNER_G;
        float b = outerRing ? OUTER_B : INNER_B;
        int alpha = (int) Math.max(0.0F, Math.min(255.0F, intensity * 225.0F));
        return (alpha << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(float value) {
        return (int) Math.max(0.0F, Math.min(255.0F, value * 255.0F));
    }

    /** Stable hash in [0,1) from the synced seed, a knot index and a field selector. */
    private static double hash(int seed, int index, int field) {
        int h = seed * 73_856_093 ^ index * 19_349_663 ^ field * 83_492_791;
        h ^= h >>> 13;
        h *= 1_274_126_177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
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

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices) {
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
            effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
        }
    }
}
