package com.gang.lightpollution.text.anim;

import java.util.Locale;

/**
 * Recognises the two written forms of an animation and hands back an interned {@link AnimSpec}.
 *
 * <p>Two forms rather than one because they answer different needs. The long form carries parameters:</p>
 *
 * <pre>
 * &amp;{shake a=2 f=1.5}text&amp;{/}
 * &amp;{grad from=5BCEFA to=F5A9B8 hue}text&amp;{/}
 * &amp;{shake; wave a=0.5}text&amp;{/}
 * </pre>
 *
 * <p>The short form is two characters and takes an effect's defaults:</p>
 *
 * <pre>
 * &amp;xs text     shake
 * &amp;xw text     wave
 * </pre>
 *
 * <p>{@code &x} prefixes the short form because {@code x} is the only letter left that neither vanilla
 * nor this mod's ten single-character codes use. A bare {@code &shake} cannot work: fourteen of the
 * fifteen effect names begin with a letter already taken, so {@code &shake} would read as {@code &s}
 * — the energy-bar effect — followed by a literal {@code hake}.</p>
 *
 * <p>Not a syntax TextAnimator has. It uses XML-ish tags ({@code <shake>…</shake>}), which would
 * collide with the JSON and SNBT this mod's codes are usually written inside.</p>
 */
public final class AnimCodes {
    /** Opens the long form. */
    public static final String OPEN = "&{";
    /** Closes the long form's scope. */
    public static final String CLOSE = "&{/}";
    /** Prefixes the short form. */
    public static final char SHORT_PREFIX = 'x';

    private AnimCodes() {
    }

    /**
     * What a short code stands for, or null.
     *
     * <p>Two letters for every effect, reading like its name, plus one letter for the commonest ones.
     * The two-letter form is matched first, which is what makes both unambiguous: {@code &xsh} cannot
     * be mistaken for {@code &xs} followed by a literal {@code h}.</p>
     *
     * <p>Kept beside the enum rather than in it: these are a concession to typing, not part of an
     * effect's identity.</p>
     */
    public static TextAnim shortCode(String letters) {
        return switch (letters.toLowerCase(Locale.ROOT)) {
            case "sh", "s" -> TextAnim.SHAKE;
            case "wa", "w" -> TextAnim.WAVE;
            case "wi", "i" -> TextAnim.WIGGLE;
            case "sw", "g" -> TextAnim.SWING;
            case "bo", "b" -> TextAnim.BOUNCE;
            case "pe", "p" -> TextAnim.PENDULUM;
            case "tu", "t" -> TextAnim.TURBULENCE;
            case "sc", "c" -> TextAnim.SCROLL;
            case "ra", "r" -> TextAnim.RAINBOW;
            case "gr", "d" -> TextAnim.GRADIENT;
            case "pu", "u" -> TextAnim.PULSE;
            case "fa", "f" -> TextAnim.FADE;
            case "sd", "h" -> TextAnim.SHADOW;
            case "gl", "l" -> TextAnim.GLITCH;
            case "sp", "e" -> TextAnim.SPECTRUM;
            case "ne" -> TextAnim.NEON;
            case "ty" -> TextAnim.TYPEWRITER;
            default -> null;
        };
    }

    /** Every short code for an effect, longest first, for documentation and completion. */
    public static String shortCodesFor(TextAnim anim) {
        StringBuilder found = new StringBuilder();
        for (char first = 'a'; first <= 'z'; first++) {
            for (char second = 'a'; second <= 'z'; second++) {
                if (shortCode(String.valueOf(first) + second) == anim) {
                    if (!found.isEmpty()) {
                        found.append(' ');
                    }
                    found.append("&x").append(first).append(second);
                }
            }
        }
        for (char letter = 'a'; letter <= 'z'; letter++) {
            if (shortCode(String.valueOf(letter)) == anim) {
                if (!found.isEmpty()) {
                    found.append(' ');
                }
                found.append("&x").append(letter);
            }
        }
        return found.toString();
    }

    /**
     * How far the long form opening at {@code start} extends, or -1 if it is not one.
     *
     * @return the index just past the closing brace
     */
    public static int longFormEnd(String text, int start) {
        if (!text.startsWith(OPEN, start)) {
            return -1;
        }
        int close = text.indexOf('}', start + OPEN.length());
        return close < 0 ? -1 : close + 1;
    }

    /**
     * The spec named by the long form at {@code start}, or null.
     *
     * <p>Null also for {@code &{/}}, which closes a scope rather than naming an effect — the caller
     * distinguishes the two with {@link #isClose}.</p>
     */
    public static AnimSpec parseLongForm(String text, int start) {
        int end = longFormEnd(text, start);
        if (end < 0) {
            return null;
        }
        String inner = text.substring(start + OPEN.length(), end - 1).trim();
        return "/".equals(inner) ? null : AnimSpec.of(inner);
    }

    public static boolean isClose(String text, int start) {
        return text.startsWith(CLOSE, start);
    }

    /**
     * How long the short form at {@code start} is, or 0 if it is not one.
     *
     * <p>Counts the whole thing including {@code &x}, so 4 for a two-letter code and 3 for one letter.
     * Two letters are tried first, which is what keeps both unambiguous.</p>
     */
    public static int shortFormLength(String text, int start) {
        if (start + 2 >= text.length()
                || text.charAt(start) != '&'
                || Character.toLowerCase(text.charAt(start + 1)) != SHORT_PREFIX) {
            return 0;
        }
        if (start + 3 < text.length()
                && shortCode(text.substring(start + 2, start + 4)) != null) {
            return 4;
        }
        return shortCode(text.substring(start + 2, start + 3)) != null ? 3 : 0;
    }

    /**
     * The spec named by the short form at {@code start}, or null.
     *
     * <p>{@code start} must point at the {@code &}.</p>
     */
    public static AnimSpec parseShortForm(String text, int start) {
        int length = shortFormLength(text, start);
        if (length == 0) {
            return null;
        }
        TextAnim anim = shortCode(text.substring(start + 2, start + length));
        return anim == null ? null : AnimSpec.of(anim.id());
    }

    /** True when a short form starts here. */
    public static boolean isShortForm(String text, int start) {
        return shortFormLength(text, start) > 0;
    }

    /** Names of every effect, for error messages and documentation. */
    public static String listEffects() {
        StringBuilder names = new StringBuilder();
        for (TextAnim anim : TextAnim.values()) {
            if (!names.isEmpty()) {
                names.append(' ');
            }
            names.append(anim.id().toLowerCase(Locale.ROOT));
        }
        return names.toString();
    }
}
