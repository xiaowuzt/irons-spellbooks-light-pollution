package com.gang.lightpollution.client.tooltip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tooltip text as spokes of a slowly turning pinwheel.
 *
 * <p>Ported from ArcaneVortex's {@code renderRotatingColumn}, credited in {@code CREDITS.txt}. The
 * most distinctive thing in that mod's tooltips and also the cheapest: it draws no geometry at all,
 * only individual glyphs under their own transforms, so it needs no shader and no texture.</p>
 *
 * <p>Each line becomes one spoke. The spokes sit at even angles on a shared carousel, and every
 * spoke also spins about its own midpoint as the carousel turns, which is what makes it read as a
 * pinwheel rather than as text on a turntable.</p>
 *
 * <p>Three perturbations stack on each glyph, perpendicular to its spoke: a fixed jitter, a bow that
 * bulges the middle of the line outward, and a wave travelling along it. The jitter comes from a
 * Random reseeded per glyph, which matters — seeding once per frame would make it flicker instead of
 * being a fixed irregularity in the lettering.</p>
 */
public final class TextPinwheel {
    /** Degrees per second the whole carousel turns. */
    private static final float SPIN = 20.0F;
    /** How far a spoke's midpoint sits from the centre, in pixels. */
    private static final float ORBIT = 60.0F;
    /** Pixels between glyph centres along a spoke. */
    private static final float GLYPH_SPACING = 6.5F;
    /** Glyph scale. */
    private static final float GLYPH_SCALE = 0.85F;
    /** Font line height, for centring a glyph on its own point. */
    private static final int LINE_HEIGHT = 9;
    /** Peak of the jitter, in pixels either way. */
    private static final float JITTER = 2.0F;
    /** Peak of the bow at the middle of a line, in pixels. */
    private static final float BOW = 8.0F;
    /** Peak of the travelling wave, in pixels. */
    private static final float WAVE = 3.0F;
    /** Peak of the per-glyph tilt, in degrees either way. */
    private static final float TILT = 5.0F;

    /** Reused, and reseeded per glyph, so it allocates nothing per frame. */
    private static final Random JITTER_SOURCE = new Random();

    private TextPinwheel() {
    }

    /**
     * Draw every line as a spoke.
     *
     * <p>Spokes are spaced by dividing the circle, not at a fixed 45 degrees as the original did.
     * That one always had eight slots and left a gap where an item lacked a stat; here the lines are
     * whatever the tooltip has, so an even division is the only thing that stays balanced.</p>
     *
     * @param seconds wall-clock seconds, so it keeps turning while the game is paused
     */
    public static void render(GuiGraphics graphics, Font font, List<Component> lines,
                              float centreX, float centreY, float seconds) {
        if (lines.isEmpty()) {
            return;
        }
        float carousel = (seconds * SPIN) % 360.0F;
        for (int index = 0; index < lines.size(); index++) {
            float spokeAngle = carousel + index * (360.0F / lines.size());
            spoke(graphics, font, lines.get(index), centreX, centreY, spokeAngle, carousel,
                    index + 1);
        }
    }

    private static void spoke(GuiGraphics graphics, Font font, Component line,
                              float centreX, float centreY, float spokeAngle, float carousel,
                              int seed) {
        List<Glyph> glyphs = glyphsOf(line);
        if (glyphs.isEmpty()) {
            return;
        }

        // The spoke's midpoint: straight up from the centre, then turned by the spoke's angle.
        float spokeRadians = spokeAngle * Mth.DEG_TO_RAD;
        float midX = centreX - ORBIT * Mth.sin(spokeRadians);
        float midY = centreY - ORBIT * Mth.cos(spokeRadians);

        // The line's own direction, a quarter turn from the spoke, so text runs across the spoke
        // rather than along it.
        float runRadians = (spokeAngle + 90.0F) * Mth.DEG_TO_RAD;
        float cos = Mth.cos(runRadians);
        float sin = Mth.sin(runRadians);
        float start = -(glyphs.size() - 1) * GLYPH_SPACING * 0.5F;

        for (int index = 0; index < glyphs.size(); index++) {
            Glyph glyph = glyphs.get(index);
            if (glyph.character() == ' ') {
                continue;
            }
            float along = start + index * GLYPH_SPACING;
            // Reseeded per glyph, so the same glyph always gets the same jitter and tilt. Seeding
            // once per frame would turn a fixed irregularity into a flicker.
            JITTER_SOURCE.setSeed((long) seed * 1000L + index);
            float jitterX = (JITTER_SOURCE.nextFloat() - 0.5F) * JITTER * 2.0F;
            float jitterY = (JITTER_SOURCE.nextFloat() - 0.5F) * JITTER * 2.0F;
            float tilt = (JITTER_SOURCE.nextFloat() - 0.5F) * TILT * 2.0F;

            // Both of these push sideways off the line, so they use the perpendicular of the run
            // direction rather than the run direction itself.
            float bow = Mth.sin(index / (float) glyphs.size() * Mth.PI) * BOW;
            float wave = Mth.sin(carousel * 0.05F + index * 0.3F) * WAVE;
            float sideways = bow + wave;

            float x = midX + along * cos + sideways * sin + jitterX;
            float y = midY + along * sin - sideways * cos + jitterY;

            String text = String.valueOf(glyph.character());
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(x, y, 0.0F);
                pose.mulPose(Axis.ZP.rotationDegrees(tilt));
                pose.scale(GLYPH_SCALE, GLYPH_SCALE, GLYPH_SCALE);
                // Centred on its own point, so the tilt turns the glyph about itself.
                pose.translate(-font.width(text) * 0.5F, -LINE_HEIGHT * 0.5F, 0.0F);
                graphics.drawString(font, text, 0, 0, glyph.colour(), true);
            } finally {
                pose.popPose();
            }
        }
    }

    /** One character and the colour its style run gave it. */
    private record Glyph(char character, int colour) {
    }

    /**
     * Flatten a component to characters, each carrying its own colour.
     *
     * <p>Walked through the component's own visitor rather than by reading {@code getString}, because
     * the colour is what makes this worth doing: the name line carries an animated effect whose hue
     * varies per character, and taking the string alone would throw that away and leave one flat
     * colour.</p>
     */
    private static List<Glyph> glyphsOf(Component line) {
        List<Glyph> glyphs = new ArrayList<>();
        line.getVisualOrderText().accept((FormattedCharSink) (position, style, codePoint) -> {
            glyphs.add(new Glyph((char) codePoint, colourOf(style)));
            return true;
        });
        return glyphs;
    }

    private static int colourOf(Style style) {
        TextColor colour = style.getColor();
        return colour == null ? 0xFFFFFF : colour.getValue();
    }
}
