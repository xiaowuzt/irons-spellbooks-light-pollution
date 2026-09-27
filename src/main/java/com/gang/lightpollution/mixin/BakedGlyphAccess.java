package com.gang.lightpollution.mixin;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a baked glyph's place in the font atlas, its size, and its render types.
 *
 * <p>All of these are private with no getters. They are needed because the fragment-stage effects
 * sample the atlas around a pixel — an outline looks at eight neighbours, an extrusion at a stack of
 * offsets — and without the glyph's own UV cell those samples walk into whichever glyph happens to sit
 * next to it in the atlas. The visible result would be fragments of unrelated letters in the outline.
 *
 * <p>An accessor rather than reflection: this is read once per glyph per frame, on the render thread.
 */
@Mixin(BakedGlyph.class)
public interface BakedGlyphAccess {
    @Accessor("u0")
    float lightPollution$u0();

    @Accessor("u1")
    float lightPollution$u1();

    @Accessor("v0")
    float lightPollution$v0();

    @Accessor("v1")
    float lightPollution$v1();

    /** Left edge relative to the pen position, in pixels. */
    @Accessor("left")
    float lightPollution$left();

    @Accessor("right")
    float lightPollution$right();

    /** Top edge. Named for the direction it faces, not its sign — y grows downward here. */
    @Accessor("up")
    float lightPollution$up();

    @Accessor("down")
    float lightPollution$down();

}
