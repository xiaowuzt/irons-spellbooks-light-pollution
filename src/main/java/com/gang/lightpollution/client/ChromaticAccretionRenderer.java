package com.gang.lightpollution.client;

import com.gang.lightpollution.entity.ChromaticAccretionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** The anchor is rendered by the post-scene world pass rather than as a mesh. */
public final class ChromaticAccretionRenderer extends EntityRenderer<ChromaticAccretionEntity> {
    public ChromaticAccretionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public void render(ChromaticAccretionEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // ChromaticAccretionWorldRenderer owns the post-scene pass.
    }

    @Override
    public boolean shouldRender(ChromaticAccretionEntity entity, Frustum frustum,
                                double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(ChromaticAccretionEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
