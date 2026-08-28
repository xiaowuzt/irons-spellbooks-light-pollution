package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.CrabNebulaEntity;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;

/**
 * Draws the Crab Nebula: a hollow cage of filaments, and the wind nebula inside it.
 *
 * <p>Two passes, because the two components are genuinely different materials — line emission
 * from cooling gas and synchrotron radiation from the pulsar's wind. Drawing them alike would
 * lose what makes the object recognisable.</p>
 *
 * <p>The filaments stay on the shell's surface. Nothing is drawn between them, which is the
 * whole point: it is a cage over a void, and a player can be inside it.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class CrabNebulaWorldRenderer {
    private static final int BUFFER_CAPACITY = 524_288;
    private static final double RENDER_DISTANCE_SQR = 224.0D * 224.0D;
    /** Quads along one filament. */
    private static final int SEGMENTS = 34;

    /** Filaments: hydrogen and sulphur line emission, red. */
    private static final float LINE_R = 1.00F;
    private static final float LINE_G = 0.34F;
    private static final float LINE_B = 0.28F;
    /** Some filaments run green, in doubly ionised oxygen. */
    private static final float LINE_ALT_R = 0.42F;
    private static final float LINE_ALT_G = 1.00F;
    private static final float LINE_ALT_B = 0.52F;
    /** The wind nebula: synchrotron, blue-white and structureless. */
    private static final float WIND_R = 0.72F;
    private static final float WIND_G = 0.84F;
    private static final float WIND_B = 1.00F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private CrabNebulaWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<CrabNebulaEntity> nebulae = SpellLightEmitter.collectCrabNebulas();
        if (nebulae.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.crabFilament();
        if (minecraft.level == null || shader == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        GlState state = GlState.capture();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(SpellRenderStage.levelPose(event));
            RenderSystem.applyModelViewMatrix();
            drawWind(nebulae, camera, partialTick, shader);
            drawCage(nebulae, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    /** The interior wind nebula. Drawn first, so the cage reads as being in front of it. */
    private static void drawWind(List<CrabNebulaEntity> nebulae, Vec3 camera,
                                 float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;
        for (CrabNebulaEntity entity : nebulae) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);
            double radius = entity.shellRadius(age) * CrabNebulaEntity.WIND_FRACTION;
            // Pulses on the pulsar's rhythm, which is the tell that something is driving it.
            float pulse = 0.7F + 0.5F * entity.windPulse(partialTick);
            vertices += disc(builder, camera, centre, radius,
                    CurveRibbon.pack(WIND_R, WIND_G, WIND_B, brightness * pulse * 0.6F));
        }
        draw(builder, shader, vertices, -1.0F);
    }

    private static void drawCage(List<CrabNebulaEntity> nebulae, Vec3 camera,
                                 float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;
        for (CrabNebulaEntity entity : nebulae) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);
            int seed = entity.getSeed();
            // The pulsar. It drives the whole interior, and it pulses on its own rhythm.
            EffectCore.add(centre, 1.3D, 0.86F, 0.92F, 1.00F,
                    brightness * (1.4F + entity.windPulse(partialTick) * 2.2F));

            for (int filament = 0; filament < CrabNebulaEntity.FILAMENTS; ++filament) {
                final int index = filament;
                // Roughly a third of the filaments run green rather than red, which is what
                // the real remnant does: different lines from different ionisation states.
                boolean green = CrabNebulaEntity.hash(seed, filament, 7) < 0.34D;
                float shade = 0.6F + 0.5F * (float)
                        CrabNebulaEntity.hash(seed, filament, 8);
                int colour = green
                        ? CurveRibbon.pack(LINE_ALT_R, LINE_ALT_G, LINE_ALT_B,
                                brightness * shade * 0.85F)
                        : CurveRibbon.pack(LINE_R, LINE_G, LINE_B,
                                brightness * shade * 0.9F);

                vertices += CurveRibbon.emit(builder, camera, SEGMENTS, 0.0D, 1.0D,
                        fraction -> entity.filamentPoint(centre, index, fraction, age),
                        fraction -> CrabNebulaEntity.FILAMENT_HALF_WIDTH,
                        fraction -> colour);
            }
        }
        draw(builder, shader, vertices, 1.0F);
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

    private static BufferBuilder begin() {
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
        }
    }
}
