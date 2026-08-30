package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.api.QuasarJetParams;
import com.gang.lightpollution.fx.FxRegistry;
import com.gang.lightpollution.fx.QuasarJetShape;
import com.gang.lightpollution.fx.QuasarJetSource;
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
 * Draws a quasar jet: a faint collimated channel, bright knots racing along it, and the
 * terminal lobe where it stops.
 *
 * <p>Two passes, distinguished by the sign of {@code ColorModulator.a}: the channel, then
 * the knots and lobe as discs. Keeping the channel dim is deliberate rather than a
 * brightness choice — only the knots do damage, and a bright continuous beam would tell the
 * player the opposite.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class QuasarJetWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 288.0D * 288.0D;
    /** The channel is straight, so it barely needs subdividing — only enough for the ramp. */
    private static final int CHANNEL_SEGMENTS = 24;
    /** Radius of a knot's disc, in blocks. */
    private static final double KNOT_RADIUS = 2.4D;

    /** Synchrotron emission from an ultra-relativistic jet: blue-white. */
    private static final float BEAM_R = 0.66F;
    private static final float BEAM_G = 0.82F;
    private static final float BEAM_B = 1.00F;
    /** The hotspot, where the beam is thermalised against the surrounding medium. */
    private static final float LOBE_R = 1.00F;
    private static final float LOBE_G = 0.74F;
    private static final float LOBE_B = 0.52F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private QuasarJetWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        // The spell's own anchors plus anything another mod asked for through the API. The
        // renderer does not distinguish them, which is the point of the source interface.
        List<QuasarJetSource> jets =
                new java.util.ArrayList<>(SpellLightEmitter.collectQuasarJets());
        jets.addAll(FxRegistry.quasarJets());
        if (jets.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.quasarBeam();
        ShaderInstance strandShader = ConstellationShaders.strand();
        if (minecraft.level == null || shader == null || strandShader == null) {
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
            drawChannels(jets, camera, partialTick, strandShader);
            drawBodies(jets, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawChannels(List<QuasarJetSource> jets, Vec3 camera,
                                     float partialTick, ShaderInstance shader) {
        BufferBuilder builder = beginTube();
        int vertices = 0;
        for (QuasarJetSource entity : jets) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            // Straight to the shared shape maths rather than through a method on the source, so a
            // spell anchor and an API instance go down the same path.
            QuasarJetParams params = entity.shapeParams();
            Vec3 direction = QuasarJetShape.direction(params);
            float launched = QuasarJetShape.launched(entity.getVisualAgeTicks(partialTick));
            // The nucleus. The jet has to be coming out of something.
            EffectCore.add(centre, 3.0D, 0.92F, 0.95F, 1.00F, brightness * 2.1F);
            double reach = QuasarJetShape.JET_LENGTH * launched;
            int alpha = (int) Math.max(0.0F, Math.min(255.0F, brightness * 210.0F));

            vertices += CurveTube.emit(builder, camera, CHANNEL_SEGMENTS, 0.0D, 1.0D,
                    fraction -> centre.add(direction.scale(reach * fraction)),
                    // Flares a little with distance, as the confining pressure drops.
                    fraction -> QuasarJetShape.JET_HALF_WIDTH * (1.0D + fraction * 0.5D),
                    // aux 1: this jet is the approaching one. A quasar throws two, but beaming
                    // makes the receding one invisible, so only one is ever drawn.
                    CurveTube.MODE_JET, 1.0F,
                    Math.min(1.0F, brightness * 0.15F), alpha);
        }
        draw(builder, shader, vertices, 1.0F);
    }

    /** The knots and the terminal lobe, both as camera-facing discs. */
    private static void drawBodies(List<QuasarJetSource> jets, Vec3 camera,
                                   float partialTick, ShaderInstance shader) {
        BufferBuilder builder = beginFlat();
        int vertices = 0;
        for (QuasarJetSource entity : jets) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);
            QuasarJetParams params = entity.shapeParams();

            for (int knot = 0; knot < QuasarJetShape.KNOT_COUNT; ++knot) {
                Vec3 at = QuasarJetShape.knotPosition(params, centre, knot, age);
                if (at == null) {
                    continue;
                }
                double progress = QuasarJetShape.knotProgress(knot, age);
                // Knots dim as they go, as the emitting material expands and cools.
                float shade = (float) (1.0D - progress * 0.45D);
                vertices += disc(builder, camera, at, KNOT_RADIUS,
                        CurveRibbon.pack(BEAM_R, BEAM_G, BEAM_B,
                                brightness * shade * 0.95F));
            }

            if (QuasarJetShape.launched(age) > 0.98F) {
                vertices += disc(builder, camera, QuasarJetShape.lobeCentre(params, centre),
                        QuasarJetShape.LOBE_RADIUS,
                        CurveRibbon.pack(LOBE_R, LOBE_G, LOBE_B, brightness * 0.55F));
            }
        }
        // Negative alpha switches the shader to its disc branch.
        draw(builder, shader, vertices, -1.0F);
    }

    /** A camera-facing square carrying a centred unit disc in its UVs. */
    private static int disc(BufferBuilder builder, Vec3 camera, Vec3 at, double radius,
                            int colour) {
        Vec3 toCamera = camera.subtract(at);
        if (toCamera.lengthSqr() < 1.0e-8D) {
            return 0;
        }
        Vec3 forward = toCamera.normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0e-8D) {
            right = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        right = right.normalize().scale(radius);
        Vec3 up = right.cross(forward).normalize().scale(radius);

        float cx = (float) (at.x - camera.x);
        float cy = (float) (at.y - camera.y);
        float cz = (float) (at.z - camera.z);
        emit(builder, cx, cy, cz, right, up, -1, -1, 0.0F, 0.0F, colour);
        emit(builder, cx, cy, cz, right, up, 1, -1, 1.0F, 0.0F, colour);
        emit(builder, cx, cy, cz, right, up, 1, 1, 1.0F, 1.0F, colour);
        emit(builder, cx, cy, cz, right, up, -1, 1, 0.0F, 1.0F, colour);
        return 4;
    }

    private static void emit(BufferBuilder builder, float cx, float cy, float cz,
                             Vec3 right, Vec3 up, int sx, int sy,
                             float u, float v, int colour) {
        builder.vertex(cx + (float) (right.x * sx + up.x * sy),
                        cy + (float) (right.y * sx + up.y * sy),
                        cz + (float) (right.z * sx + up.z * sy))
                .uv(u, v)
                .color((colour >> 16) & 0xFF, (colour >> 8) & 0xFF,
                        colour & 0xFF, (colour >>> 24) & 0xFF)
                .endVertex();
    }

    /**
     * Begin the buffer for tube geometry.
     *
     * <p>POSITION_TEX_COLOR_NORMAL, because TubeMeshBuilder writes a normal per vertex and the
     * strand shader declares one. This renderer needs two formats in one frame — the channel is a
     * tube and the knots and lobe are flat quads — so the two begins are kept separate rather
     * than one helper that would silently be wrong for whichever pass it was not written for.</p>
     */
    private static BufferBuilder beginTube() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        return effectBuffer;
    }

    /** Begin the buffer for flat camera-facing quads. */
    private static BufferBuilder beginFlat() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
    }

    private static void draw(BufferBuilder builder, ShaderInstance shader, int vertices,
                             float modulatorAlpha) {
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
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, modulatorAlpha);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException | LinkageError failure) {
            finish(builder);
            throw failure;
        } finally {
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
            effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
        }
    }
}
