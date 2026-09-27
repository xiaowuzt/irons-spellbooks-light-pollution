package com.gang.lightpollution.client.gpu;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Lets an atlas page be told which location it was registered under.
 *
 * <p>Separate from {@code BakedGlyphAccess} because it is a different object: that one is the glyph,
 * this one is the page the glyph sits on. Both are needed — the page learns the location, then stamps it
 * onto every glyph it hands out.</p>
 */
public interface FontTextureAccess {
    @Nullable
    ResourceLocation lightPollution$atlas();

    void lightPollution$setAtlas(ResourceLocation atlas);
}
