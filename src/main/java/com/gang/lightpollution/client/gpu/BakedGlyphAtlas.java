package com.gang.lightpollution.client.gpu;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The atlas a glyph was baked into, carried on the glyph itself.
 *
 * <p>Separate from {@code BakedGlyphAccess} because that one is a pure {@code @Accessor} interface for
 * fields vanilla already has, and a mixin of that kind cannot also declare methods with bodies.</p>
 *
 * <p>Carried rather than looked up: the obvious route, a map from the glyph's {@code GlyphRenderTypes}
 * to its location, silently returns the wrong entry. That type is a record, so a map keyed by it
 * compares the three render types it holds rather than the instance, and two structurally equal keys
 * collide. The symptom was an atlas with the right namespace and an empty path — binding no texture,
 * leaving every affected glyph a filled rectangle.</p>
 */
public interface BakedGlyphAtlas {
    @Nullable
    ResourceLocation lightPollution$atlas();

    void lightPollution$setAtlas(ResourceLocation atlas);
}
