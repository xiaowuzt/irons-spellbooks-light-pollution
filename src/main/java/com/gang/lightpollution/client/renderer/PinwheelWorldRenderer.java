package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.entity.PinwheelEntity;
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
 * Draws a Wolf-Rayet pinwheel: two Archimedean dust arms turning rigidly.
 *
 * <p>Arm curves come from the entity at the same rotation the damage sweep uses, so a gap
 * that looks safe is safe — and stops being safe when the arm arrives, which is the effect.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PinwheelWorldRenderer {
    private static final int BUFFER_CAPACITY = 262_144;
    private static final double RENDER_DISTANCE_SQR = 224.0D * 224.0D;
    /** Quads along one arm. It winds a bit over two turns, so it needs them. */
    private static final int SEGMENTS = 130;

    /** Freshly condensed dust, nearest the binary: warm, still bright. */
    private static final float INNER_R = 1.00F;
    private static final float INNER_G = 0.66F;
    private static final float INNER_B = 0.38F;
    /** Old dust at the outer end: cooled to a deep red-brown. */
    private static final float OUTER_R = 0.58F;
    private static final float OUTER_G = 0.22F;
    private static final float OUTER_B = 0.14F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);

    private PinwheelWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!SpellRenderStage.shouldDraw(
                event, RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
            return;
        }
        List<PinwheelEntity> wheels = SpellLightEmitter.collectPinwheels();
        if (wheels.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.pinwheelDust();
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
            drawArms(wheels, camera, partialTick, shader);
            // The central bodies, over everything else. Six of these effects drew
            // only their outer structure and left the middle empty.
            EffectCore.flush(effectBuffer, camera);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawArms(List<PinwheelEntity> wheels, Vec3 camera,
                                 float partialTick, ShaderInstance shader) {
        BufferBuilder builder = begin();
        int vertices = 0;

        for (PinwheelEntity entity : wheels) {
            float brightness = entity.brightness(partialTick);
            if (brightness <= 0.01F) {
                continue;
            }
            Vec3 centre = entity.centre(partialTick);
            if (camera.distanceToSqr(centre) > RENDER_DISTANCE_SQR) {
                continue;
            }
            float age = entity.getVisualAgeTicks(partialTick);
            double rotation = entity.rotation(age);
            // Grows outward as it spins up, so the spiral is seen being written rather than
            // appearing whole.
            double grown = entity.spunUp(partialTick);
            // The binary. Two hot stars, so two bodies rather than one — and they are what
            // the arms trail from, which is why the centre cannot be empty.
            Vec3 normal = entity.planeNormal();
            Vec3 offsetAxis = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (offsetAxis.lengthSqr() < 1.0e-6D) {
                offsetAxis = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
            }
            offsetAxis = offsetAxis.normalize().scale(1.5D);
            double spin = entity.rotation(age);
            Vec3 swing = offsetAxis.scale(Math.cos(spin))
                    .add(normal.cross(offsetAxis).normalize().scale(1.5D * Math.sin(spin)));
            EffectCore.add(centre.add(swing), 1.7D, 0.72F, 0.88F, 1.00F, brightness * 2.0F);
            EffectCore.add(centre.subtract(swing), 1.2D, 1.00F, 0.82F, 0.58F,
                    brightness * 1.5F);

            for (int arm = 0; arm < PinwheelEntity.ARMS; ++arm) {
                final int index = arm;
                vertices += CurveRibbon.emit(builder, camera, SEGMENTS,
                        0.02D, Math.max(0.05D, grown),
                        fraction -> entity.armPoint(centre, index, fraction, rotation),
                        PinwheelEntity::armWidth,
                        fraction -> dustColour(fraction, brightness));
            }
        }
        draw(builder, shader, vertices);
    }

    /**
     * Dust colour along an arm, warm near the binary and cold at the far end.
     *
     * <p>That direction is the physics: dust is condensing continuously at the shock and
     * cooling as it is carried out, so age and radius are the same axis. Nothing here reaches
     * white — dust at a few hundred kelvin cannot.</p>
     */
    private static int dustColour(double fraction, float brightness) {
        float t = (float) Math.max(0.0D, Math.min(1.0D, fraction));
        float r = INNER_R + (OUTER_R - INNER_R) * t;
        float g = INNER_G + (OUTER_G - INNER_G) * t;
        float b = INNER_B + (OUTER_B - INNER_B) * t;
        return CurveRibbon.pack(r, g, b, brightness * 0.9F);
    }

    private static BufferBuilder begin() {
        finish(effectBuffer);
        effectBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        return effectBuffer;
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
