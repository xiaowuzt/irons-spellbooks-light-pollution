package com.gang.lightpollution.client;

import com.gang.lightpollution.entity.StargraveSingularityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * The anchor entity has no mesh. The world renderer below draws the actual
 * event horizon after the level has rendered its scene.
 */
public final class StargraveSingularityRenderer extends EntityRenderer<StargraveSingularityEntity> {
    public StargraveSingularityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public void render(StargraveSingularityEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Deliberately empty: StargraveSingularityWorldRenderer owns the post-scene pass.
    }

    @Override
    public boolean shouldRender(StargraveSingularityEntity entity, Frustum frustum,
                                double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(StargraveSingularityEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
