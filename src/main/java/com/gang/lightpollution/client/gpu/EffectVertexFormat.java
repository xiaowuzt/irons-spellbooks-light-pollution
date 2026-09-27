package com.gang.lightpollution.client.gpu;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.google.common.collect.ImmutableMap;

/**
 * The vertex format the fragment-stage effects need, and nothing more.
 *
 * <p>Vanilla text is {@code POSITION_COLOR_TEX_LIGHTMAP} — position, colour, atlas UV, light. There is
 * no spare slot, which is why the resource pack this is ported from had to encode its effect choice in
 * the text colour itself. That costs the colour, and limits a colour to one effect.</p>
 *
 * <p>Adding attributes instead costs a custom format and shader, but keeps the colour meaning colour
 * and lets an effect carry real parameters. Four extra attributes:</p>
 *
 * <ul>
 *   <li><b>EffectId</b> — which effect, as a float because that is what a vertex attribute gives us
 *       cheaply and the fragment shader compares against small integers anyway.</li>
 *   <li><b>EffectColor</b> — the outline's colour, the extrusion's front face, and so on. Separate
 *       from the glyph's own colour, which the effect still needs.</li>
 *   <li><b>EffectParams</b> — four floats. Thickness, speed, density, depth: every effect ported here
 *       needs at most four and most need two.</li>
 *   <li><b>GlyphBounds</b> — the glyph's UV cell as (u0, v0, u1, v1). Load-bearing rather than
 *       convenience: the effects sample around a pixel, and without knowing where this glyph ends
 *       those samples read whichever glyph sits beside it in the atlas.</li>
 * </ul>
 *
 * <p>Sixteen extra floats per vertex. A page of animated lore is a few thousand vertices, so the cost
 * is nothing next to a single block model.</p>
 */
public final class EffectVertexFormat {
    // Every element below takes index 0. The index is not the attribute slot — that comes from an
    // element's position in the format — and the constructor rejects a non-zero index for any usage
    // but UV. Numbering these 1, 2, 3 threw before the shader could even register.

    /** Which effect to run. 0 means none, and the shader falls through to plain text. */
    public static final VertexFormatElement EFFECT_ID = new VertexFormatElement(
            0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);
    /** The effect's own colour, RGBA as floats rather than bytes so a shader can read it directly. */
    public static final VertexFormatElement EFFECT_COLOR = new VertexFormatElement(
            0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
    /** Four knobs, meaning whatever the effect says they mean. */
    public static final VertexFormatElement EFFECT_PARAMS = new VertexFormatElement(
            0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
    /** This glyph's cell in the atlas, as (u0, v0, u1, v1). */
    public static final VertexFormatElement GLYPH_BOUNDS = new VertexFormatElement(
            0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);

    /**
     * Position, colour, UV and light exactly as vanilla text has them, then the four new attributes.
     *
     * <p>The vanilla four come first and in vanilla's order deliberately: the glyph quad is emitted by
     * the same code that emits a plain one, so anything that reads the leading attributes — a debug
     * overlay, a profiler, a shader pack's fallback path — sees the layout it expects.</p>
     */
    // Rebuilt rather than taken from DefaultVertexFormat. Referencing those static fields compiles,
    // but this jar is reobfuscated to SRG names while the running game carries official ones, so the
    // lookup fails at class initialisation with NoSuchFieldError. The constructor is stable under both.
    private static final VertexFormatElement POSITION = new VertexFormatElement(
            0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);
    private static final VertexFormatElement COLOR = new VertexFormatElement(
            0, VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.COLOR, 4);
    private static final VertexFormatElement UV0 = new VertexFormatElement(
            0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
    /** The lightmap coordinate. Short, and at UV index 2 — which is what a non-zero index is for. */
    private static final VertexFormatElement UV2 = new VertexFormatElement(
            2, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.UV, 2);

    public static final VertexFormat FORMAT = new VertexFormat(
            ImmutableMap.<String, VertexFormatElement>builder()
                    .put("Position", POSITION)
                    .put("Color", COLOR)
                    .put("UV0", UV0)
                    .put("UV2", UV2)
                    .put("EffectId", EFFECT_ID)
                    .put("EffectColor", EFFECT_COLOR)
                    .put("EffectParams", EFFECT_PARAMS)
                    .put("GlyphBounds", GLYPH_BOUNDS)
                    .build());

    private EffectVertexFormat() {
    }
}
