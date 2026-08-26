package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.CelestialJudgmentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Programmatic transparent renderer for the seven supplied spell textures. */
public class CelestialJudgmentRenderer extends EntityRenderer<CelestialJudgmentEntity> {
    private static final ResourceLocation TOP_OUTER = texture("magic_circle_2");
    private static final ResourceLocation TOP_INNER_1 = texture("inner_magic_circle_5");
    private static final ResourceLocation TOP_INNER_2 = texture("inner_magic_circle_2");
    private static final ResourceLocation TOP_INNER_3 = texture("inner_magic_circle_3");
    private static final ResourceLocation BODY_RING = texture("side_ring_strip");
    private static final ResourceLocation FOOT_CIRCLE = texture("magic_circle_1");
    private static final ResourceLocation BEAM = texture("beam_cylinder");

    public CelestialJudgmentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                "irons_spellbooks_light_pollution", "textures/spell/celestial/" + name + ".png");
    }

    @Override
    public void render(
            CelestialJudgmentEntity effect,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        Entity targetEntity = effect.level().getEntity(effect.getTargetId());
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }

        float width = Mth.clamp(target.getBbWidth(), 0.35F, 12.0F);
        float height = Mth.clamp(target.getBbHeight(), 0.5F, 24.0F);
        // Keep ordinary mobs readable while making the circles follow both
        // the target's footprint and its height for giant entities.
        float bodyRadius = Mth.clamp(
                Math.max(1.20F, Math.max(width * 1.35F, height * 0.38F)),
                1.20F,
                10.0F);
        float topOffset = Mth.clamp(height * 0.10F, 0.70F, 2.20F);
        float layerGap = Mth.clamp(height * 0.10F, 0.28F, 1.80F);
        float topBaseY = height + topOffset;
        float finalTopY = topBaseY + layerGap * 3.0F;
        float age = effect.tickCount + partialTick;
        float alpha = fadeAlpha(age);

        poseStack.pushPose();
        // The anchor is kept at the target's feet. Compensate for client-side
        // interpolation so the layers stay visually attached between packets.
        poseStack.translate(
                target.getX() - effect.getX(),
                target.getY() - effect.getY(),
                target.getZ() - effect.getZ());

        drawDisc(poseStack, bufferSource, TOP_OUTER, bodyRadius * 1.28F,
                topBaseY, age * 1.6F, alpha * reveal(age, 0.0F), 1.0F);
        drawDisc(poseStack, bufferSource, TOP_INNER_1, bodyRadius * 1.14F,
                topBaseY + layerGap, -age * 2.1F, alpha * reveal(age, 4.0F), 1.0F);
        drawDisc(poseStack, bufferSource, TOP_INNER_2, bodyRadius * 1.00F,
                topBaseY + layerGap * 2.0F, age * 2.8F, alpha * reveal(age, 8.0F), 1.0F);
        drawDisc(poseStack, bufferSource, TOP_INNER_3, bodyRadius * 0.86F,
                finalTopY, -age * 3.4F, alpha * reveal(age, 12.0F), 1.0F);

        float ringAlpha = alpha * Mth.clamp((age - 10.0F) / 14.0F, 0.0F, 1.0F);
        drawRing(poseStack, bufferSource, bodyRadius * 1.08F,
                Math.max(0.80F, height * 0.70F), height * 0.38F,
                age * 1.9F, ringAlpha);

        float footAlpha = alpha * Mth.clamp((age - 18.0F) / 12.0F, 0.0F, 1.0F);
        drawDisc(poseStack, bufferSource, FOOT_CIRCLE, bodyRadius * 1.20F,
                0.035F, -age * 1.1F, footAlpha, 1.0F);

        float beamAlpha = alpha * Mth.clamp((age - 28.0F) / 12.0F, 0.0F, 1.0F) * 0.38F;
        drawBeam(poseStack, bufferSource, Math.max(0.24F, bodyRadius * 0.22F),
                0.08F, finalTopY + Math.max(0.25F, layerGap * 0.35F),
                age * 1.4F, beamAlpha);
        poseStack.popPose();
    }

    private static float fadeAlpha(float age) {
        if (age < 8.0F) {
            return Mth.clamp(age / 8.0F, 0.0F, 1.0F);
        }
        if (age > 78.0F) {
            return Mth.clamp((100.0F - age) / 22.0F, 0.0F, 1.0F);
        }
        return 1.0F;
    }

    private static float reveal(float age, float start) {
        return Mth.clamp((age - start) / 10.0F, 0.0F, 1.0F);
    }

    private static void drawDisc(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ResourceLocation texture,
            float radius,
            float y,
            float rotation,
            float alpha,
            float scale) {
        if (alpha <= 0.01F) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        poseStack.pushPose();
        poseStack.translate(0.0F, y, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.scale(radius * scale, radius * scale, radius * scale);
        VertexConsumer consumer = bufferSource.getBuffer(emissiveLayer(texture));
        PoseStack.Pose transformed = poseStack.last();
        vertex(consumer, transformed, -1.0F, 0.0F, -1.0F, 0.0F, 1.0F, alpha, 0.0F, 1.0F, 0.0F);
        vertex(consumer, transformed, 1.0F, 0.0F, -1.0F, 1.0F, 1.0F, alpha, 0.0F, 1.0F, 0.0F);
        vertex(consumer, transformed, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, alpha, 0.0F, 1.0F, 0.0F);
        vertex(consumer, transformed, -1.0F, 0.0F, 1.0F, 0.0F, 0.0F, alpha, 0.0F, 1.0F, 0.0F);
        poseStack.popPose();
    }

    private static void drawRing(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float radius,
            float height,
            float y,
            float rotation,
            float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, y, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        VertexConsumer consumer = bufferSource.getBuffer(emissiveLayer(BODY_RING));
        PoseStack.Pose pose = poseStack.last();
        int sides = 32;
        for (int i = 0; i < sides; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / sides);
            float a1 = (float) (Math.PI * 2.0D * (i + 1) / sides);
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            float u0 = i / (float) sides;
            float u1 = (i + 1) / (float) sides;
            vertex(consumer, pose, x0, -height * 0.5F, z0, u0, 1.0F, alpha, x0, 0.0F, z0);
            vertex(consumer, pose, x1, -height * 0.5F, z1, u1, 1.0F, alpha, x1, 0.0F, z1);
            vertex(consumer, pose, x1, height * 0.5F, z1, u1, 0.0F, alpha, x1, 0.0F, z1);
            vertex(consumer, pose, x0, height * 0.5F, z0, u0, 0.0F, alpha, x0, 0.0F, z0);
        }
        poseStack.popPose();
    }

    private static void drawBeam(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float radius,
            float bottom,
            float top,
            float rotation,
            float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        VertexConsumer consumer = bufferSource.getBuffer(emissiveLayer(BEAM));
        PoseStack.Pose pose = poseStack.last();
        int sides = 16;
        for (int i = 0; i < sides; i++) {
            float a0 = (float) (Math.PI * 2.0D * i / sides);
            float a1 = (float) (Math.PI * 2.0D * (i + 1) / sides);
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            float u0 = i / (float) sides;
            float u1 = (i + 1) / (float) sides;
            vertex(consumer, pose, x0, bottom, z0, u0, 1.0F, alpha, x0, 0.0F, z0);
            vertex(consumer, pose, x1, bottom, z1, u1, 1.0F, alpha, x1, 0.0F, z1);
            vertex(consumer, pose, x1, top, z1, u1, 0.0F, alpha, x1, 0.0F, z1);
            vertex(consumer, pose, x0, top, z0, u0, 0.0F, alpha, x0, 0.0F, z0);
        }
        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            float alpha,
            float nx,
            float ny,
            float nz) {
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        consumer.vertex(poseMatrix, x, y, z)
                .color(255, 255, 255, Mth.clamp((int) (alpha * 255.0F), 0, 255))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, nx, ny, nz)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(CelestialJudgmentEntity entity) {
        return TOP_OUTER;
    }

    /**
     * The layer these emissive quads ride on.
     *
     * <p>{@code entityTranslucentEmissive} is a Forge addition, and Oculus swaps in a
     * shader pack's programs by looking the render type up in a fixed table of the
     * ones it knows about. This one is not in that table, so a pack replaces nothing
     * and every quad drawn on it vanishes — the same failure Oculus issue 630
     * describes for Forge's unlit entity shader. The vanilla translucent layer *is* in
     * the table, so falling back to it keeps the spell visible.</p>
     *
     * <p>The cost is that it stops being unlit: the circles and the beam pick up world
     * lighting and dim at night instead of staying white. Visible, but a long way
     * better than the whole spell being invisible.</p>
     */
    private static RenderType emissiveLayer(ResourceLocation texture) {
        return ShaderPackState.packActive()
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityTranslucentEmissive(texture);
    }
}
