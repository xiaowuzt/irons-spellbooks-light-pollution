package com.gang.lightpollution.client.perf;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import java.util.Arrays;
import java.util.Locale;

/** Render-thread measurements. Timestamp queries never wait and never nest TIME_ELAPSED queries. */
public final class PerfTracker {
    public enum Section { FRAME, LIGHTING, VOXEL, ASTRAL, GEOMETRY, CINEMATIC, TEXT, LIGHT_COLLECTION }
    private static final Section[] SECTIONS = Section.values();
    private static final int SLOTS = 128, HISTORY = 600, GPU_FRAMES = 64;
    private static final int[] queries = new int[SLOTS * 2];
    private static final int[] owner = new int[SLOTS], queryFrame = new int[SLOTS];
    private static final byte[] state = new byte[SLOTS];
    private static final int[] outstanding = new int[GPU_FRAMES], frameIds = new int[GPU_FRAMES];
    private static final boolean[] ended = new boolean[GPU_FRAMES];
    private static final double[][] gpuBuckets = new double[GPU_FRAMES][SECTIONS.length];
    private static final double[] cpu = new double[SECTIONS.length], cpuEma = new double[SECTIONS.length];
    private static final double[] gpuEma = new double[SECTIONS.length], history = new double[HISTORY];
    private static int frame, nextSlot, historySize, historyCursor;
    private static boolean supported, checked, sampling, recording;
    private static long frameToken, gpuPublishedAt;
    private static int frameGpuToken;
    private static int gpuPublishedFrame = -1;
    private static double lastCpu;

    private PerfTracker() {}

    public static void beginFrame(boolean validFrame) {
        if (frame == Integer.MAX_VALUE) release();
        poll();
        if (!validFrame) {
            recording = sampling = false;
            return;
        }
        Arrays.fill(cpu, 0);
        frame++;
        int bucket = frame % GPU_FRAMES;
        // A severely backlogged GPU simply loses a sample rather than blocking or growing memory.
        frameIds[bucket] = frame;
        outstanding[bucket] = 0;
        ended[bucket] = false;
        Arrays.fill(gpuBuckets[bucket], 0);
        sampling = frame % 4 == 0;
        recording = true;
        frameToken = begin(Section.FRAME);
        frameGpuToken = beginGpu(Section.FRAME);
    }

    public static void endFrame() {
        if (!recording) return;
        end(Section.FRAME, frameToken);
        endGpu(frameGpuToken);
        ended[frame % GPU_FRAMES] = true;
        recording = false;
        lastCpu = cpu[Section.FRAME.ordinal()];
        history[historyCursor] = lastCpu;
        historyCursor = (historyCursor + 1) % HISTORY;
        historySize = Math.min(HISTORY, historySize + 1);
        for (int i = 0; i < cpu.length; i++) cpuEma[i] += (cpu[i] - cpuEma[i]) * 0.08;
    }

    /** CPU-only scope. Geometry building must never be included in a GPU timestamp interval. */
    public static long begin(Section section) {
        if (!recording) return 0;
        return System.nanoTime();
    }

    public static void end(Section section, long token) {
        if (token != 0) cpu[section.ordinal()] += Math.max(0, System.nanoTime() - token) / 1_000_000.0;
    }

    /** Place directly around GPU submission, after any CPU mesh construction or world queries. */
    public static int beginGpu(Section section) {
        if (!recording || !sampling || !gpuSupported()) return 0;
        if (section == Section.TEXT || section == Section.LIGHT_COLLECTION) return 0;
        for (int n = 0; n < SLOTS; n++) {
            int slot = nextSlot;
            nextSlot = (nextSlot + 1) & (SLOTS - 1);
            if (state[slot] != 0) continue;
            if (queries[slot * 2] == 0) {
                queries[slot * 2] = GL15.glGenQueries();
                queries[slot * 2 + 1] = GL15.glGenQueries();
            }
            owner[slot] = section.ordinal();
            queryFrame[slot] = frame;
            state[slot] = 1;
            outstanding[frame % GPU_FRAMES]++;
            GL33.glQueryCounter(queries[slot * 2], GL33.GL_TIMESTAMP);
            return slot + 1;
        }
        return 0;
    }

    public static void endGpu(int token) {
        if (token == 0) return;
        int slot = token - 1;
        if (slot >= 0 && state[slot] == 1) {
            GL33.glQueryCounter(queries[slot * 2 + 1], GL33.GL_TIMESTAMP);
            state[slot] = 2;
        }
    }

    private static boolean gpuSupported() {
        if (!checked) {
            checked = true;
            supported = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query;
        }
        return supported;
    }

    private static void poll() {
        if (!supported) return;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (state[slot] != 2 || GL15.glGetQueryObjecti(queries[slot * 2 + 1], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
            long start = GL33.glGetQueryObjectui64(queries[slot * 2], GL15.GL_QUERY_RESULT);
            long stop = GL33.glGetQueryObjectui64(queries[slot * 2 + 1], GL15.GL_QUERY_RESULT);
            int bucket = queryFrame[slot] % GPU_FRAMES;
            if (frameIds[bucket] == queryFrame[slot]) {
                gpuBuckets[bucket][owner[slot]] += Math.max(0, stop - start) / 1_000_000.0;
                outstanding[bucket]--;
            }
            state[slot] = 0;
        }
        for (int bucket = 0; bucket < GPU_FRAMES; bucket++) {
            if (ended[bucket] && outstanding[bucket] == 0 && frameIds[bucket] > gpuPublishedFrame
                    && gpuBuckets[bucket][0] > 0) {
                for (int i = 0; i < gpuEma.length; i++) {
                    gpuEma[i] = gpuPublishedFrame < 0 ? gpuBuckets[bucket][i]
                            : gpuEma[i] + (gpuBuckets[bucket][i] - gpuEma[i]) * 0.25;
                }
                gpuPublishedFrame = frameIds[bucket];
                gpuPublishedAt = System.nanoTime();
            }
        }
    }

    public static double frameCpuMs() { return lastCpu; }
    public static double frameGpuMs() {
        return gpuPublishedAt != 0 && System.nanoTime() - gpuPublishedAt < 500_000_000L ? gpuEma[0] : Double.NaN;
    }
    public static double modCpuMs() { return modSum(cpuEma, false); }
    public static double modGpuMs() { return modSum(gpuEma, true); }
    private static double modSum(double[] values, boolean gpu) {
        double total = 0;
        for (Section section : SECTIONS) {
            // VOXEL is nested inside LIGHTING: report it, but don't count it twice.
            if (section != Section.FRAME && (gpu || section != Section.VOXEL)) total += values[section.ordinal()];
        }
        return total;
    }

    public static String[] report() {
        double[] sorted = Arrays.copyOf(history, historySize);
        Arrays.sort(sorted);
        String[] rows = new String[SECTIONS.length + 1];
        rows[0] = String.format(Locale.ROOT, "CPU frame p50/p95/p99: %.2f / %.2f / %.2f ms (%d frames); GPU frame span: %s",
                percentile(sorted, .50), percentile(sorted, .95), percentile(sorted, .99), historySize,
                Double.isNaN(frameGpuMs()) ? "unavailable" : String.format(Locale.ROOT, "%.2f ms", frameGpuMs()));
        boolean freshGpu = !Double.isNaN(frameGpuMs());
        for (int i = 0; i < SECTIONS.length; i++) {
            boolean measured = freshGpu && SECTIONS[i] != Section.TEXT && SECTIONS[i] != Section.LIGHT_COLLECTION;
            rows[i + 1] = String.format(Locale.ROOT, "%s: CPU %.3f ms, GPU %s", SECTIONS[i], cpuEma[i],
                    measured ? String.format(Locale.ROOT, "%.3f ms", gpuEma[i]) : "n/a");
        }
        return rows;
    }

    private static double percentile(double[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil((sorted.length - 1) * p)];
    }

    public static void release() {
        for (int i = 0; i < queries.length; i++) if (queries[i] != 0) {
            GL15.glDeleteQueries(queries[i]);
            queries[i] = 0;
        }
        Arrays.fill(state, (byte) 0);
        Arrays.fill(cpuEma, 0);
        Arrays.fill(gpuEma, 0);
        Arrays.fill(outstanding, 0);
        Arrays.fill(ended, false);
        frame = nextSlot = historySize = historyCursor = 0;
        gpuPublishedFrame = -1;
        gpuPublishedAt = 0;
        lastCpu = 0;
        frameToken = 0;
        frameGpuToken = 0;
        supported = checked = recording = sampling = false;
    }
}
