package com.gang.lightpollution.client.gpu;

import com.gang.lightpollution.client.perf.PerfTracker;

/** Count one outer text render, including any nested glyph rendering, exactly once. */
public final class TextRenderTiming {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private TextRenderTiming() {
    }

    public static long begin() {
        return DEPTH.get()[0]++ == 0 ? PerfTracker.begin(PerfTracker.Section.TEXT) : 0L;
    }

    public static void end(long token) {
        if (--DEPTH.get()[0] == 0) {
            PerfTracker.end(PerfTracker.Section.TEXT, token);
        }
    }
}
