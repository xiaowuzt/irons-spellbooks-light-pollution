package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.entity.FuneralNovaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** The synchronized entity is invisible; the world subscriber draws the effect. */
public final class FuneralNovaRenderer extends EntityRenderer<FuneralNovaEntity> {
    public FuneralNovaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public void render(FuneralNovaEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
    }

    @Override
    public boolean shouldRender(FuneralNovaEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(FuneralNovaEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
