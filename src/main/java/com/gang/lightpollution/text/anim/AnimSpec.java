package com.gang.lightpollution.text.anim;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One or more animated effects with their parameters, addressable by a small integer.
 *
 * <p>The effects the ten single-character codes provide are a bit set, which fits in a font path
 * directly. These do not: a spec can say {@code grad from=5BCEFA to=F5A9B8 hue}, and a
 * {@code ResourceLocation} path may only contain {@code [a-z0-9_/.-]} — no {@code =}, no uppercase.
 * So specs are interned here and the path carries the id.</p>
 *
 * <p>Interned by their written text, so the same spec always gets the same id. That is what bounds the
 * table: a pack with thirty distinct animated strings gets thirty entries, however many times they are
 * drawn. Without it, every frame would mint a new id and the font-set caches keyed on the path would
 * grow without limit.</p>
 */
public final class AnimSpec {
    /**
     * Ceiling on distinct specs.
     *
     * <p>A caller generating specs from live data — a countdown, a health value — would otherwise
     * grow this table forever. At the ceiling new specs render unanimated rather than being accepted
     * and leaking. Five hundred is far past what a hand-written pack needs; reaching it means
     * something is building specs per frame, which this encoding cannot support.</p>
     */
    private static final int MAX_SPECS = 512;

    private static final Map<String, AnimSpec> BY_TEXT = new ConcurrentHashMap<>();
    private static final Map<Integer, AnimSpec> BY_ID = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final int id;
    private final String text;
    private final List<Entry> entries;

    /** One effect and the parameters it was written with. */
    public record Entry(TextAnim anim, AnimParams params) {
    }

    private AnimSpec(int id, String text, List<Entry> entries) {
        this.id = id;
        this.text = text;
        this.entries = entries;
    }

    public int id() {
        return id;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** The text this was parsed from, for round-tripping. */
    public String text() {
        return text;
    }

    /**
     * Intern a spec written as {@code shake a=2} or {@code shake a=2; wave} — semicolons separate
     * stacked effects.
     *
     * @return the spec, or null if nothing in it named a known effect
     */
    public static AnimSpec of(String written) {
        if (written == null || written.isBlank()) {
            return null;
        }
        String key = written.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        AnimSpec existing = BY_TEXT.get(key);
        if (existing != null) {
            return existing;
        }

        List<Entry> entries = new java.util.ArrayList<>(2);
        for (String clause : key.split(";")) {
            String trimmed = clause.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int space = trimmed.indexOf(' ');
            String name = space < 0 ? trimmed : trimmed.substring(0, space);
            TextAnim anim = TextAnim.byId(name);
            if (anim == null) {
                continue;
            }
            entries.add(new Entry(anim,
                    space < 0 ? AnimParams.EMPTY : AnimParams.parse(trimmed.substring(space + 1))));
        }
        if (entries.isEmpty()) {
            return null;
        }

        if (BY_TEXT.size() >= MAX_SPECS) {
            return null;
        }
        AnimSpec spec = new AnimSpec(NEXT_ID.getAndIncrement(), key, List.copyOf(entries));
        AnimSpec raced = BY_TEXT.putIfAbsent(key, spec);
        if (raced != null) {
            return raced;
        }
        BY_ID.put(spec.id, spec);
        return spec;
    }

    /** By the id encoded in a font path, or null. */
    public static AnimSpec byId(int id) {
        return BY_ID.get(id);
    }

    /** Apply every effect in this spec to one glyph, in written order. */
    public void apply(GlyphState glyph, long millis) {
        for (Entry entry : entries) {
            entry.anim().apply(glyph, entry.params(), millis);
        }
    }

    /** True when any effect in this spec needs the run key — that is, the typewriter. */
    public boolean needsRunKey() {
        for (Entry entry : entries) {
            if (entry.anim() == TextAnim.TYPEWRITER) {
                return true;
            }
        }
        return false;
    }

    /** True when any effect in this spec moves or turns the glyph, rather than only recolouring it. */
    public boolean movesGlyphs() {
        for (Entry entry : entries) {
            switch (entry.anim()) {
                case SHAKE, WAVE, WIGGLE, SWING, BOUNCE, PENDULUM, TURBULENCE, SCROLL, GLITCH -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }
}
