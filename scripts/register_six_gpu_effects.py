"""Register the six new fragment-stage effects in the enum and the short-code table."""
import sys
from pathlib import Path

root = Path(sys.argv[1])
anim = root / "text/anim"

ENUM_OLD = """    FRINGE("fringe") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_FRINGE;
            glyph.fragmentParams[0] = params.number("i", 1.2F);
            glyph.fragmentParams[1] = params.number("f", 1.0F);
        }
    };

    /** Effect ids the fragment shader dispatches on. Must match effect_text.fsh. */
    public static final int FRAGMENT_OUTLINE = 1;
    public static final int FRAGMENT_GLOW = 2;
    public static final int FRAGMENT_FRINGE = 3;"""

ENUM_NEW = """    FRINGE("fringe") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_FRINGE;
            glyph.fragmentParams[0] = params.number("i", 1.2F);
            glyph.fragmentParams[1] = params.number("f", 1.0F);
        }
    },
    /**
     * The glyph stacked behind itself, receding down and right.
     *
     * <p>{@code m=0} darkens each layer automatically; {@code m=1} runs from the glyph's own colour to
     * {@code c}. The original's third mode interpolated across three colours, which is not ported — it
     * would need two more vertex attributes for a look these two already cover.</p>
     */
    EXTRUDE("extrude") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_EXTRUDE;
            glyph.fragmentColour = params.colourPacked("c", 0xFF303030);
            glyph.fragmentParams[0] = params.number("d", 1.0F);
            // Capped at sixteen because the shader's loop is bounded there; a higher number would
            // silently stop at sixteen, which is worse than clamping it here.
            glyph.fragmentParams[1] = Math.min(16.0F, Math.max(1.0F, params.number("l", 4.0F)));
            glyph.fragmentParams[2] = params.number("m", 0.0F);
        }
    },
    /** Diagonal stripes sweeping across the glyph. */
    HATCH("hatch") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_HATCH;
            glyph.fragmentColour = params.colourPacked("c", 0xFFFFFFFF);
            glyph.fragmentParams[1] = params.number("f", 1.0F);
            glyph.fragmentParams[2] = params.number("d", 6.0F);
        }
    },
    /** Per-scanline jitter, quantised to frames for a television-static read. */
    STATIC("static") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_NOISE;
            glyph.fragmentParams[0] = params.number("i", 2.0F);
            glyph.fragmentParams[1] = params.number("f", 1.0F);
        }
    },
    /** Smooth turbulent displacement, from crossed sines at different rates. */
    LIQUID("liquid") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_LIQUID;
            glyph.fragmentParams[0] = params.number("i", 1.5F);
            glyph.fragmentParams[1] = params.number("f", 1.0F);
        }
    },
    /** The glyph filling with water to a level, with a rippling surface. */
    WATER("water") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_WATER;
            glyph.fragmentColour = params.colourPacked("c", 0xFF3FA9F5);
            glyph.fragmentParams[0] = params.number("l", 0.6F);
            glyph.fragmentParams[1] = params.number("a", 1.0F);
            glyph.fragmentParams[2] = params.number("f", 1.0F);
            glyph.fragmentParams[3] = params.number("w", 2.0F);
        }
    },
    /** The top half of the glyph sliding sideways against the bottom. */
    SPLIT("split") {
        @Override
        public void apply(GlyphState glyph, AnimParams params, long millis) {
            glyph.fragmentEffect = FRAGMENT_SPLIT;
            glyph.fragmentParams[0] = params.number("i", 1.0F);
            glyph.fragmentParams[1] = params.number("f", 1.0F);
        }
    };

    /** Effect ids the fragment shader dispatches on. Must match effect_text.fsh. */
    public static final int FRAGMENT_OUTLINE = 1;
    public static final int FRAGMENT_GLOW = 2;
    public static final int FRAGMENT_FRINGE = 3;
    public static final int FRAGMENT_EXTRUDE = 4;
    public static final int FRAGMENT_HATCH = 5;
    public static final int FRAGMENT_NOISE = 6;
    public static final int FRAGMENT_LIQUID = 7;
    public static final int FRAGMENT_WATER = 8;
    public static final int FRAGMENT_SPLIT = 9;"""

p = anim / "TextAnim.java"
t = p.read_text(encoding="utf-8")
assert ENUM_OLD in t, "FRINGE entry not found"
p.write_text(t.replace(ENUM_OLD, ENUM_NEW, 1), encoding="utf-8")
print("TextAnim: six effects registered")

CODES_OLD = """            case "fr" -> TextAnim.FRINGE;"""
CODES_NEW = """            case "fr" -> TextAnim.FRINGE;
            case "ex" -> TextAnim.EXTRUDE;
            case "ha" -> TextAnim.HATCH;
            case "st" -> TextAnim.STATIC;
            case "li" -> TextAnim.LIQUID;
            case "wt" -> TextAnim.WATER;
            case "sl" -> TextAnim.SPLIT;"""

p = anim / "AnimCodes.java"
t = p.read_text(encoding="utf-8")
assert CODES_OLD in t, "fringe short code not found"
p.write_text(t.replace(CODES_OLD, CODES_NEW, 1), encoding="utf-8")
print("AnimCodes: &xex &xha &xst &xli &xwt &xsl")
