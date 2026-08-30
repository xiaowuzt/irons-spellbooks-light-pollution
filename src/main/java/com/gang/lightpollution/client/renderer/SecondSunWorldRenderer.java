package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.client.ConstellationShaders;
import com.gang.lightpollution.api.SecondSunParams;
import com.gang.lightpollution.fx.FxRegistry;
import com.gang.lightpollution.fx.SecondSunShape;
import com.gang.lightpollution.fx.SecondSunSource;
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
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws Second Sun as a camera-facing disc far out on the sky.
 *
 * <p>Placed at a fixed distance along its own direction from the camera rather
 * than at a world position: at this angular size it belongs to the sky, and a
 * world-anchored disc would slide across the view as the player walks. The disc's
 * size in blocks is derived from its angular size so it stays the same fraction
 * of the screen whatever the distance is.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SecondSunWorldRenderer {
    private static final int BUFFER_CAPACITY = 8_192;
    /** How far out the disc is placed, in blocks. */
    private static final float SKY_DISTANCE = 700.0F;
    /** How far past the disc the billboard extends, to carry the corona. */
    private static final float CORONA_MARGIN = 2.3F;

    private static BufferBuilder effectBuffer = new BufferBuilder(BUFFER_CAPACITY);
    private static final Vector3f CAMERA_UP = new Vector3f();
    private static final Vector3f CAMERA_RIGHT = new Vector3f();

    private SecondSunWorldRenderer() {
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
        List<SecondSunSource> suns =
                new java.util.ArrayList<>(SpellLightEmitter.collectSecondSuns());
        suns.addAll(FxRegistry.secondSuns());
        if (suns.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = ConstellationShaders.secondSun();
        if (minecraft.level == null || shader == null) {
            return;
        }

        float partialTick = event.getPartialTick();
        Camera camera = event.getCamera();
        updateCameraVectors(camera);

        GlStateGuard state = GlStateGuard.capture();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity();
            modelView.mulPoseMatrix(SpellRenderStage.levelPose(event));
            RenderSystem.applyModelViewMatrix();
            for (SecondSunSource sun : suns) {
                drawDisc(sun, partialTick, shader);
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            state.restore();
        }
    }

    private static void drawDisc(SecondSunSource sun, float partialTick,
                                 ShaderInstance shader) {
        float brightness = sun.brightness(partialTick);
        if (brightness <= 0.01F) {
            return;
        }
        // The billboard's half-size in blocks, from the angular radius. A disc
        // this far out has to be sized by angle or it stops matching the light it
        // is supposed to be emitting.
        // Straight to the shared shape maths rather than through a method on the source, so a
        // spell anchor and an API instance go down the same path.
        SecondSunParams params = sun.shapeParams();
        float age = sun.getVisualAgeTicks(partialTick);
        float angle = SecondSunShape.discAngleDegrees(params, age);
        float halfSize = (float) (Math.tan(Math.toRadians(angle)) * SKY_DISTANCE)
                * CORONA_MARGIN;

        // Camera-relative, so the disc keeps its place on the sky as the player
        // moves rather than being left behind.
        Vec3 direction = SecondSunShape.discDirection(params, age);
        float centreX = (float) direction.x * SKY_DISTANCE;
        float centreY = (float) direction.y * SKY_DISTANCE;
        float centreZ = (float) direction.z * SKY_DISTANCE;

        int packed = color(Mth.frac(age * 0.006F), SecondSunShape.temperature(age),
                Mth.clamp(brightness * 0.2F, 0.0F, 1.0F), 1.0F);

        float rightX = CAMERA_RIGHT.x * halfSize;
        float rightY = CAMERA_RIGHT.y * halfSize;
        float rightZ = CAMERA_RIGHT.z * halfSize;
        float upX = CAMERA_UP.x * halfSize;
        float upY = CAMERA_UP.y * halfSize;
        float upZ = CAMERA_UP.z * halfSize;

        BufferBuilder builder = begin();
        vertex(builder, centreX - rightX - upX, centreY - rightY - upY,
                centreZ - rightZ - upZ, 0.0F, 0.0F, packed);
        vertex(builder, centreX - rightX + upX, centreY - rightY + upY,
                centreZ - rightZ + upZ, 0.0F, 1.0F, packed);
        vertex(builder, centreX + rightX + upX, centreY + rightY + upY,
                centreZ + rightZ + upZ, 1.0F, 1.0F, packed);
        vertex(builder, centreX + rightX - upX, centreY + rightY - upY,
                centreZ + rightZ - upZ, 1.0F, 0.0F, packed);

        // The nova's shell is expressed as a fraction of the billboard, because
        // the billboard is what the shader's coordinates are relative to.
        float novaAge = age - SecondSunShape.NOVA_TICK;
        float nova = novaAge < 0.0F ? 0.0F
                : Mth.clamp(novaAge / (float) (SecondSunShape.NOVA_END_TICK
                        - SecondSunShape.NOVA_TICK), 0.0F, 1.0F);
        float shell = nova <= 0.0F ? 0.0F : 0.3F + nova * 0.85F;
        // The photosphere's share of the billboard. The shader needs it because
        // the billboard is deliberately larger than the body.
        draw(builder, shader, nova, shell, 1.0F / CORONA_MARGIN);
    }

    private static void updateCameraVectors(Camera camera) {
        Quaternionf rotation = new Quaternionf(camera.rotation());
        CAMERA_UP.set(0.0F, 1.0F, 0.0F).rotate(rotation);
        CAMERA_RIGHT.set(1.0F, 0.0F, 0.0F).rotate(rotation);
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

    private static void draw(BufferBuilder builder, ShaderInstance shader,
                             float nova, float shell, float surface) {
        if (shader == null || !builder.building()) {
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
            // No depth test: it is on the sky, behind everything, and testing it
            // against a depth buffer that never had the sky written into it would
            // hide it behind terrain it is nowhere near.
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(() -> shader);
            if (shader.getUniform("NovaState") != null) {
                shader.getUniform("NovaState").set(nova, shell, surface, 0.0F);
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

    /** Everything this renderer changes has to go back exactly as it was. */
}
