package com.gang.lightpollution.client.gpu;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * The render type the effect glyphs go through.
 *
 * <p>Composed the same way Forge composes its text type — translucent, lightmapped, the font atlas as
 * its texture — with two differences: our vertex format, and our shader.</p>
 *
 * <p>Memoized per atlas, because {@link RenderType} instances are compared by identity when the buffer
 * source decides whether it can keep batching. Building a new one per glyph would flush between every
 * character and turn a page of text into hundreds of draw calls.</p>
 */
public final class EffectRenderType extends RenderType {
    /** Set once the shader has loaded. Null until then, and the caller falls back to plain text. */
    private static ShaderInstance shader;

    // A map rather than Util.memoize: that call reobfuscates to an SRG name this jar cannot resolve
    // against the running game, and the failure lands in a static initialiser during shader load.
    private static final int MAX_ATLASES = 128;
    private static final Map<ResourceLocation, RenderType> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, RenderType> eldest) {
            return size() > MAX_ATLASES;
        }
    };

    private static RenderType build(ResourceLocation atlas) {
        CompositeState state = CompositeState.builder()
                .setShaderState(new ShaderStateShard(EffectRenderType::shader))
                .setTextureState(new TextureStateShard(atlas, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .createCompositeState(false);
        // Preserve text sorting: this type is also used by translucent world labels,
        // whose overlapping glyphs can require distance ordering.
        return create("light_pollution_effect_text", EffectVertexFormat.FORMAT, VertexFormat.Mode.QUADS,
                256, false, true, state);
    }

    private EffectRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                             int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                             Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /**
     * The type for one font atlas.
     *
     * <p>Cached because render types are compared by identity when the buffer source decides whether it
     * can keep batching — a fresh instance per glyph would flush between every character.</p>
     */
    public static synchronized RenderType of(ResourceLocation atlas) {
        return CACHE.computeIfAbsent(atlas, EffectRenderType::build);
    }

    public static synchronized void clearCache() {
        CACHE.clear();
    }

    public static ShaderInstance shader() {
        return shader;
    }

    /** True once the shader compiled, so a caller knows whether this path is usable at all. */
    public static boolean ready() {
        return shader != null;
    }

    static void setShader(ShaderInstance instance) {
        shader = instance;
    }
}
