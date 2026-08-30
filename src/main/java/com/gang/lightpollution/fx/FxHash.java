package com.gang.lightpollution.fx;

/**
 * The hashes the scattered effects place their pieces with.
 *
 * <p>One copy each, because these are the reason a spell's knots, filaments and meteors land in the
 * same places on the server and on every client without any of it being synced. Copies that are
 * byte-identical today could diverge under an innocent-looking edit, and the symptom would be a cage
 * that looks subtly different to each player rather than an error.</p>
 *
 * <p>Two functions rather than one because the effects were written at different times and picked
 * different mixers. They are not interchangeable: swapping one for the other would rearrange every
 * existing effect, which is a visible change for no benefit.</p>
 */
public final class FxHash {
    private FxHash() {
    }

    /** Stable value in [0,1) from a seed, an index and a field selector. */
    public static double at(int seed, int index, int field) {
        int h = seed * 73_856_093 ^ index * 19_349_663 ^ field * 83_492_791;
        h ^= h >>> 13;
        h *= 1_274_126_177;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    /**
     * Stable value in [0,1) from a seed, an index and an arbitrary salt.
     *
     * <p>The salt is a long here rather than a small selector, which is what lets a caller use a
     * different named constant per quantity — an angle, a radius, a delay — instead of numbering
     * fields.</p>
     */
    public static float unit(int seed, int index, long salt) {
        long hash = (seed & 0xFFFFFFFFL) * 0x2545F4914F6CDD1DL ^ (index + 1L) * salt;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return (float) ((hash >>> 1) / (double) Long.MAX_VALUE);
    }
}
