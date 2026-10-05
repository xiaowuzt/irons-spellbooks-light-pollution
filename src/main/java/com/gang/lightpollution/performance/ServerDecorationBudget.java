package com.gang.lightpollution.performance;

/** Tick-time feedback for optional presentation only; never schedules game simulation. */
public final class ServerDecorationBudget {
    private double averageMillis;
    private boolean sampled;
    private int pressureTicks;
    private int recoveryTicks;
    private int reduction;

    public void recordTick(double millis) {
        if (!Double.isFinite(millis) || millis < 0.0D) {
            return;
        }
        averageMillis = sampled ? averageMillis + (millis - averageMillis) * 0.08D : millis;
        sampled = true;
        if (averageMillis >= 40.0D) {
            recoveryTicks = 0;
            if (++pressureTicks >= (averageMillis >= 48.0D ? 5 : 20)) {
                reduction = Math.min(3, reduction + 1);
                pressureTicks = 0;
            }
        } else if (averageMillis <= 30.0D) {
            pressureTicks = 0;
            if (++recoveryTicks >= 100) {
                reduction = Math.max(0, reduction - 1);
                recoveryTicks = 0;
            }
        } else {
            pressureTicks = 0;
            recoveryTicks = 0;
        }
    }

    public int limit(int fullBudget) {
        return (int) ((long) Math.max(0, fullBudget) * (4 - reduction) / 4);
    }

    public double averageMillis() {
        return averageMillis;
    }

    public void reset() {
        averageMillis = 0.0D;
        sampled = false;
        pressureTicks = 0;
        recoveryTicks = 0;
        reduction = 0;
    }
}
