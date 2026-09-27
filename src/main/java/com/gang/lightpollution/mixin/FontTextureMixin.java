package com.gang.lightpollution.mixin;

import com.gang.lightpollution.client.gpu.BakedGlyphAtlas;
import com.gang.lightpollution.client.gpu.FontTextureAccess;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stamps each glyph with the atlas page it was baked into.
 *
 * <p>{@code FontTexture} owns an atlas page and hands out the glyphs on it, so it is the one place where
 * a glyph and its page are both in scope. The page's location is not a field here either — it is built
 * in {@code FontSet.stitch} and only used to register the texture — so it arrives via
 * {@link FontTextureAccess}.</p>
 *
 * <p>Stamped on {@code add} rather than at construction because one page serves many glyphs: the first
 * loop in {@code stitch} reuses an existing page for most of them and only the last creates a new one.
 * Stamping at construction would leave every reused glyph unmarked, which is what produced the two
 * different symptoms — unmarked glyphs fell back to plain text, wrongly marked ones drew solid.</p>
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin implements FontTextureAccess {
    @Unique
    private static final int[] PROBE_ADD = {0};

    @Unique
    @Nullable
    private ResourceLocation lightPollution$atlas;

    @Override
    @Nullable
    public ResourceLocation lightPollution$atlas() {
        return this.lightPollution$atlas;
    }

    @Override
    public void lightPollution$setAtlas(ResourceLocation atlas) {
        this.lightPollution$atlas = atlas;
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void lightPollution$stampAtlas(SheetGlyphInfo info,
                                           CallbackInfoReturnable<BakedGlyph> callback) {
        BakedGlyph glyph = callback.getReturnValue();
        if (PROBE_ADD[0]++ < 3) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "[stitch] FontTexture.add glyph={} page={}", glyph != null,
                    this.lightPollution$atlas);
        }
        if (glyph != null && this.lightPollution$atlas != null) {
            ((BakedGlyphAtlas) glyph).lightPollution$setAtlas(this.lightPollution$atlas);
        }
    }
}
