package com.gang.lightpollution.text.anim;

import java.util.Locale;
import java.util.Map;

/**
 * The named arguments an effect was written with.
 *
 * <p>Ported from TextAnimator's {@code Params} hierarchy (Apache 2.0, © 2023 Snownee); see
 * {@code TEXTANIMATOR_LICENSE.txt}. Changed from the original: four classes collapsed into one, and
 * the values are parsed to double or String at construction rather than stored as Object and coerced
 * on read. The original needed the generality for its own registry API; here the only producer is
 * this mod's parser.</p>
 */
public final class AnimParams {
    public static final AnimParams EMPTY = new AnimParams(Map.of());

    private final Map<String, String> values;

    public AnimParams(Map<String, String> values) {
        this.values = values;
    }

    /**
     * Parse {@code name=1.5 other=ff0000 flag} into params.
     *
     * <p>A bare word is a flag and reads back as {@code true}. Unknown keys are kept rather than
     * rejected: an effect only looks up the keys it knows, so a typo shows as a default instead of an
     * error in the middle of someone's item name.</p>
     */
    public static AnimParams parse(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        Map<String, String> parsed = new java.util.HashMap<>(4);
        for (String token : text.trim().split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals < 0) {
                parsed.put(token.toLowerCase(Locale.ROOT), "true");
            } else if (equals > 0) {
                parsed.put(token.substring(0, equals).toLowerCase(Locale.ROOT),
                        token.substring(equals + 1));
            }
        }
        return parsed.isEmpty() ? EMPTY : new AnimParams(parsed);
    }

    public float number(String key, float fallback) {
        String raw = values.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }

    public boolean flag(String key, boolean fallback) {
        String raw = values.get(key);
        return raw == null ? fallback : !"false".equalsIgnoreCase(raw);
    }

    /**
     * A colour as three floats, or the fallback.
     *
     * <p>Six hex digits, with an optional leading {@code #}. Returns the fallback rather than throwing
     * on anything else, for the same reason a bad number does.</p>
     */
    public float[] colour(String key, float[] fallback) {
        String raw = values.get(key);
        if (raw == null) {
            return fallback;
        }
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6) {
            return fallback;
        }
        try {
            int packed = Integer.parseInt(hex, 16);
            return new float[] {
                    ((packed >> 16) & 0xFF) / 255.0F,
                    ((packed >> 8) & 0xFF) / 255.0F,
                    (packed & 0xFF) / 255.0F,
            };
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }

    /** For round-tripping back to text. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
