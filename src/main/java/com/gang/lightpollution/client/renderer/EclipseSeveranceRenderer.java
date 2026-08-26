package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.EclipseSeveranceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * The entity is only a synchronized timing anchor. The Forge world-render
 * subscriber draws the complete effect in one additive batch.
 */
public final class EclipseSeveranceRenderer extends EntityRenderer<EclipseSeveranceEntity> {
    public EclipseSeveranceRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public void render(EclipseSeveranceEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Deliberately empty: EclipseSeveranceWorldRenderer owns the world pass.
    }

    @Override
    public boolean shouldRender(EclipseSeveranceEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(EclipseSeveranceEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
