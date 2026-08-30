package com.gang.lightpollution.text.anim;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How far each typewriter reveal has got.
 *
 * <p>Ported from TextAnimator's {@code TypewriterTrack} and {@code TypewriterTracks} (Apache 2.0,
 * © 2023 Snownee); see {@code TEXTANIMATOR_LICENSE.txt}. Changed from the original: Guava's
 * {@code Cache} is replaced by an access-ordered {@code LinkedHashMap} evicting on age and size, so
 * the port carries no dependency the host mods would otherwise not need.</p>
 *
 * <p>Keyed by the text being revealed, which is what lets the same string carry on across frames
 * instead of restarting. Two different strings revealing at once keep separate progress; the same
 * string drawn in two places shares one, which is what the original does and is the sane reading of
 * "this text is being typed".</p>
 *
 * <p>Entries expire a second after they were last drawn. A tooltip that closes stops being asked
 * about, so its progress is dropped and the next hover starts the reveal again — which is the
 * behaviour a typewriter effect on a tooltip should have.</p>
 */
final class Typewriter {
    /** Milliseconds of not being drawn before a reveal is forgotten. */
    private static final long EXPIRY_MILLIS = 1000L;
    /** Ceiling on tracked reveals, in case something draws a great many distinct strings. */
    private static final int MAX_TRACKS = 128;
    /** Milliseconds per character at speed 1. The original's mid-range default. */
    private static final float BASE_INTERVAL_MILLIS = 20.0F;

    private static final Map<String, Track> TRACKS =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Track> eldest) {
                    return size() > MAX_TRACKS;
                }
            };

    private Typewriter() {
    }

    private static final class Track {
        long startedAt;
        long touchedAt;
    }

    /**
     * How many characters of this run should be visible.
     *
     * <p>Synchronised because ModernUI lays out text on its own workers while the render thread draws,
     * and the map is not thread-safe. The critical section is a hash lookup and two arithmetic
     * operations, per glyph — measurable only if something is drawing tens of thousands of animated
     * glyphs, which the length cap already prevents.</p>
     *
     * @param key   identifies the run being revealed
     * @param speed characters per interval; higher is faster
     */
    static int revealed(String key, float speed, long millis) {
        if (key == null) {
            return Integer.MAX_VALUE;
        }
        synchronized (TRACKS) {
            Track track = TRACKS.get(key);
            if (track == null || millis - track.touchedAt > EXPIRY_MILLIS) {
                track = new Track();
                track.startedAt = millis;
                TRACKS.put(key, track);
            }
            track.touchedAt = millis;
            float interval = BASE_INTERVAL_MILLIS / Math.max(0.05F, speed);
            long elapsed = millis - track.startedAt;
            return (int) (elapsed / interval);
        }
    }

    /** Forget every reveal, so they start over. */
    static void reset() {
        synchronized (TRACKS) {
            TRACKS.clear();
        }
    }
}
