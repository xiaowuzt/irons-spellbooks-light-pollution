package com.gang.lightpollution.client.gpu;

import com.gang.lightpollution.mixin.BakedGlyphAccess;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Emits one glyph as a quad carrying its effect parameters.
 *
 * <p>Four vertices in vanilla's own winding and with vanilla's own bounds, plus the four attributes the
 * effect shader needs. Written here rather than by calling {@code font.drawInBatch} per glyph because
 * that path uses the fixed text vertex format, which has nowhere to put an effect id.</p>
 *
 * <p>The italic skew is reproduced rather than skipped: it is a per-vertex x offset proportional to
 * height, so a glyph drawn without it in an italic run would visibly stand upright among its
 * neighbours.</p>
 */
public final class EffectGlyphEmitter {
    private static final int[] PROBE_NULL = {0};


    /**
     * Vanilla's own vertical bias, from {@code BakedGlyph.render}.
     *
     * <p>Three pixels up. Not a magic number to be tidied away — the glyph's own {@code up}/{@code down}
     * are relative to a baseline three pixels above where the caller's y sits, so dropping this draws
     * every glyph three pixels low.</p>
     */
    private static final float BASELINE_BIAS = 3.0F;

    private EffectGlyphEmitter() {
    }

    /**
     * Draw one glyph through the effect shader.
     *
     * @param effectId     which effect; 0 falls through to plain text in the shader
     * @param effectColour packed ARGB for the effect's own colour
     * @param params       up to four knobs, meaning whatever the effect says
     * @return false when this glyph cannot be drawn this way, so the caller falls back
     */
    public static boolean emit(BakedGlyph glyph, MultiBufferSource buffers, Matrix4f pose,
                               float x, float y, int colour, boolean italic,
                               int packedLight, int effectId, int effectColour, float[] params) {
        if (!EffectRenderType.ready()) {
            return false;
        }
        BakedGlyphAccess access = (BakedGlyphAccess) glyph;
        // Read off the glyph rather than looked up: see BakedGlyphAtlasMixin for why a map keyed
        // by GlyphRenderTypes returns the wrong atlas.
        ResourceLocation atlas = ((com.gang.lightpollution.client.gpu.BakedGlyphAtlas) glyph).lightPollution$atlas();
        if (atlas == null) {
            if (PROBE_NULL[0]++ < 3) {
                com.mojang.logging.LogUtils.getLogger().info(
                        "[emit] no atlas on glyph {}", glyph.getClass().getName());
            }
            return false;
        }

        RenderType type = EffectRenderType.of(atlas);
        VertexConsumer consumer = buffers.getBuffer(type);
        // The four extra attributes have no named method on VertexConsumer, so they need the buffer's
        // own raw writes. A consumer that is something else — a wrapper some other mod installed —
        // could not honour this format anyway, so decline rather than cast and throw.
        if (!(consumer instanceof BufferVertexConsumer)) {
            return false;
        }

        float left = x + access.lightPollution$left();
        float right = x + access.lightPollution$right();
        float up = access.lightPollution$up() - BASELINE_BIAS;
        float down = access.lightPollution$down() - BASELINE_BIAS;
        float top = y + up;
        float bottom = y + down;
        // Skew grows with height, so the top of a tall glyph leans further than the bottom of a short
        // one. Matches vanilla's 0.25 factor exactly; a different factor would make our glyphs lean at
        // a different angle from the rest of the run.
        float skewTop = italic ? 1.0F - 0.25F * up : 0.0F;
        float skewBottom = italic ? 1.0F - 0.25F * down : 0.0F;

        float u0 = access.lightPollution$u0();
        float u1 = access.lightPollution$u1();
        float v0 = access.lightPollution$v0();
        float v1 = access.lightPollution$v1();

        float er = (effectColour >> 16 & 0xFF) / 255.0F;
        float eg = (effectColour >> 8 & 0xFF) / 255.0F;
        float eb = (effectColour & 0xFF) / 255.0F;
        float ea = (effectColour >>> 24) / 255.0F;

        float p0 = params.length > 0 ? params[0] : 0.0F;
        float p1 = params.length > 1 ? params[1] : 0.0F;
        float p2 = params.length > 2 ? params[2] : 0.0F;
        float p3 = params.length > 3 ? params[3] : 0.0F;

        int r = colour >> 16 & 0xFF;
        int g = colour >> 8 & 0xFF;
        int b = colour & 0xFF;
        int a = colour >>> 24;

        vertex(consumer, pose, left + skewTop, top, r, g, b, a, u0, v0, packedLight,
                effectId, er, eg, eb, ea, p0, p1, p2, p3, u0, v0, u1, v1);
        vertex(consumer, pose, left + skewBottom, bottom, r, g, b, a, u0, v1, packedLight,
                effectId, er, eg, eb, ea, p0, p1, p2, p3, u0, v0, u1, v1);
        vertex(consumer, pose, right + skewBottom, bottom, r, g, b, a, u1, v1, packedLight,
                effectId, er, eg, eb, ea, p0, p1, p2, p3, u0, v0, u1, v1);
        vertex(consumer, pose, right + skewTop, top, r, g, b, a, u1, v0, packedLight,
                effectId, er, eg, eb, ea, p0, p1, p2, p3, u0, v0, u1, v1);
        return true;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y,
                               int r, int g, int b, int a, float u, float v, int packedLight,
                               int effectId, float er, float eg, float eb, float ea,
                               float p0, float p1, float p2, float p3,
                               float bu0, float bv0, float bu1, float bv1) {
        // The four vanilla attributes through the interface's own methods, so anything decorating the
        // consumer sees what it expects. Each of those advances the element cursor itself.
        consumer.vertex(pose, x, y, 0.0F)
                .color(r, g, b, a)
                .uv(u, v)
                .uv2(packedLight);

        // The four new ones have no named method. putFloat writes at an offset within the current
        // element without advancing, so each element's floats go at their own offsets and then
        // nextElement steps past it.
        BufferVertexConsumer buffer = (BufferVertexConsumer) consumer;

        buffer.putFloat(0, effectId);
        buffer.nextElement();

        buffer.putFloat(0, er);
        buffer.putFloat(4, eg);
        buffer.putFloat(8, eb);
        buffer.putFloat(12, ea);
        buffer.nextElement();

        buffer.putFloat(0, p0);
        buffer.putFloat(4, p1);
        buffer.putFloat(8, p2);
        buffer.putFloat(12, p3);
        buffer.nextElement();

        buffer.putFloat(0, bu0);
        buffer.putFloat(4, bv0);
        buffer.putFloat(8, bu1);
        buffer.putFloat(12, bv1);
        buffer.nextElement();

        consumer.endVertex();
    }
}
