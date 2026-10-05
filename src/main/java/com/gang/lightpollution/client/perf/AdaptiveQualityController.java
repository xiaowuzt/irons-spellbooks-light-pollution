package com.gang.lightpollution.client.perf;

/** Pure policy: all clocks and measurements are supplied by the render-thread adapter. */
public final class AdaptiveQualityController {
    public enum Mode { FIXED, AUTO_BALANCED, AUTO_FIDELITY }
    private int cpuReduction, gpuReduction;
    private double cpuAverage, gpuAverage;
    private double cpuOver, gpuOver, under, cooldown;
    private boolean initialized;
    private int consecutiveSlowFrames;
    private String reason = "warming up";

    public void reset() {
        cpuReduction = gpuReduction = 0;
        cpuAverage = gpuAverage = cpuOver = gpuOver = under = cooldown = 0;
        initialized = false;
        consecutiveSlowFrames = 0;
        reason = "warming up";
    }

    public void observe(double seconds, double cpuMs, double gpuMs, double modCpuMs,
                        double modGpuMs, double budgetMs, Mode mode, int maximumReduction) {
        int limit = Math.max(0, Math.min(3, maximumReduction));
        cpuReduction = Math.min(cpuReduction, limit);
        gpuReduction = Math.min(gpuReduction, limit);
        if (mode == Mode.FIXED || limit == 0) {
            reset();
            reason = "fixed quality";
            return;
        }
        // A loading stall is not a sustained rendering workload. Never feed NaN into the EMA.
        if (!Double.isFinite(seconds) || !Double.isFinite(cpuMs) || !Double.isFinite(budgetMs)
                || seconds <= 0 || cpuMs < 0 || budgetMs <= 0) {
            suspend();
            return;
        }
        if (seconds > .25 || cpuMs > 250) {
            consecutiveSlowFrames++;
            if (consecutiveSlowFrames < 3) {
                cpuOver = gpuOver = under = 0;
                initialized = false;
                return;
            }
            // Repeated very slow frames are real pressure, not an endless loading exemption.
            seconds = Math.min(seconds, .25);
        } else consecutiveSlowFrames = 0;
        double weight = 1.0 - Math.exp(-seconds / 0.6);
        cpuAverage = initialized ? cpuAverage + (cpuMs - cpuAverage) * weight : cpuMs;
        boolean gpuKnown = Double.isFinite(gpuMs) && gpuMs >= 0;
        if (gpuKnown) gpuAverage = initialized ? gpuAverage + (gpuMs - gpuAverage) * weight : gpuMs;
        initialized = true;
        cooldown = Math.max(0, cooldown - seconds);
        double threshold = mode == Mode.AUTO_FIDELITY ? 1.30 : 1.15;
        // Only spend visual quality on work the mod can actually reduce.
        boolean cpuBusy = cpuAverage > budgetMs * threshold && modCpuMs >= Math.max(0.35, cpuAverage * .05);
        // Whole-frame timestamps include GPU idle gaps while the CPU prepares commands.
        // Require actual draw-submission samples, and prefer CPU when both span estimates tie.
        boolean gpuBusy = gpuKnown && gpuAverage > budgetMs * threshold
                && modGpuMs >= Math.max(.35, gpuAverage * .05)
                && (!cpuBusy || modGpuMs > modCpuMs);
        cpuOver = cpuBusy ? cpuOver + seconds : 0;
        gpuOver = gpuBusy ? gpuOver + seconds : 0;
        boolean spare = cpuAverage < budgetMs * 0.75
                && (gpuKnown ? gpuAverage < budgetMs * 0.75 : gpuReduction == 0);
        under = spare ? under + seconds : 0;
        if (cooldown > 0) return;
        double delay = mode == Mode.AUTO_FIDELITY ? 2.0 : 1.0;
        if (gpuOver >= delay && gpuReduction < limit
                && (cpuOver < delay || gpuAverage >= cpuAverage || cpuReduction >= limit)) {
            gpuReduction++;
            changed("GPU budget", 2.0);
        } else if (cpuOver >= delay && cpuReduction < limit) {
            cpuReduction++;
            changed("CPU budget", 2.0);
        } else if (under >= (mode == Mode.AUTO_FIDELITY ? 10.0 : 8.0)) {
            if (gpuReduction > 0) gpuReduction--;
            else if (cpuReduction > 0) cpuReduction--;
            else return;
            changed("recovering", 2.0);
        } else if ((cpuBusy && cpuReduction >= limit) || (gpuBusy && gpuReduction >= limit)) {
            reason = "minimum quality reached";
        }
    }

    private void changed(String why, double wait) {
        reason = why;
        cooldown = wait;
        cpuOver = gpuOver = under = 0;
    }

    /** Ignore pauses/reloads without discarding the user's current quality. */
    public void suspend() {
        cpuOver = gpuOver = under = 0;
        initialized = false;
        consecutiveSlowFrames = 0;
        reason = "sampling suspended";
    }

    public int cpuReduction() { return cpuReduction; }
    public int gpuReduction() { return gpuReduction; }
    public String reason() { return reason; }
}
