package com.gang.lightpollution.mixin;

import com.gang.lightpollution.client.gpu.FontTextureAccess;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tells each new atlas page which location it was registered under.
 *
 * <p>{@code FontSet.stitch} builds that location, hands it to the render types, registers the texture
 * under it, and keeps no reference. This is the last moment both the location and the page are in
 * scope.</p>
 *
 * <p>A {@code @Redirect} on the registration rather than an {@code @Inject} with captured locals: the
 * call gives us both arguments directly, so it does not depend on the order or count of local variables
 * in a method we do not own.</p>
 */
@Mixin(FontSet.class)
public abstract class FontSetMixin {
    @Redirect(
            method = "stitch",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;register("
                            + "Lnet/minecraft/resources/ResourceLocation;"
                            + "Lnet/minecraft/client/renderer/texture/AbstractTexture;)V")
    )
    private void lightPollution$nameAtlasPage(TextureManager manager, ResourceLocation atlas,
                                              AbstractTexture page) {
        manager.register(atlas, page);
        if (page instanceof FontTextureAccess access) {
            access.lightPollution$setAtlas(atlas);
        }
    }
}
