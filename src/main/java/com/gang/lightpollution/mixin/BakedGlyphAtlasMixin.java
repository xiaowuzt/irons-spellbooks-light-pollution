package com.gang.lightpollution.mixin;

import com.gang.lightpollution.client.gpu.BakedGlyphAtlas;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every baked glyph a field naming the atlas it lives in.
 *
 * <p>Drawing a glyph through our own render type means binding the same atlas vanilla would have, and
 * that location is not recoverable from the glyph: it is handed to {@code RenderType.text(…)} at
 * construction and disappears into a composite state's texture shard.</p>
 *
 * <p>The first attempt kept a map from the glyph's {@code GlyphRenderTypes} to the location. That fails
 * because {@code GlyphRenderTypes} is a record — its {@code equals} compares the three render types it
 * holds, so entries for different atlases compare equal and a lookup returns whichever was written
 * last. The observable result was an atlas with the right namespace and an empty path, which binds no
 * texture at all and leaves every affected glyph as a filled rectangle.</p>
 *
 * <p>A field on the glyph has no key to get wrong.</p>
 */
@Mixin(BakedGlyph.class)
public abstract class BakedGlyphAtlasMixin implements BakedGlyphAtlas {
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
}
