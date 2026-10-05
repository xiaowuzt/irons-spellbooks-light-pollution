package com.gang.lightpollution.fx;

/** Shared, pure geometry/timeline math. The visual radius follows the damage radius. */
public final class EventHorizonShape {
    // The outermost dim gas/haze fades to zero at this radius (shader State.w).
    // Keep culling, ray integration and bloom occlusion on exactly the same support.
    public static final float VOLUME_RADIUS = 5.5F;
    private EventHorizonShape() {}
    public static float envelope(float age, int lifetime, int openTick, int collapseTick) {
        if (!Float.isFinite(age) || lifetime <= 0 || age < 0 || age >= lifetime) return 0;
        float open = Math.min(Math.max(1, openTick), lifetime * 0.4F);
        float close = Math.min(Math.max(open, collapseTick), lifetime * 0.9F);
        return smooth(age / Math.max(0.1F, open))
                * (1 - smooth((age - close) / Math.max(0.1F, lifetime - close)));
    }
    private static float smooth(float t) {
        float v = Math.max(0, Math.min(1, t));
        return v * v * (3 - 2 * v);
    }
    public static float unitRadius(double effectRadius, float envelope) {
        return (float) Math.max(0, effectRadius) / 6.0F * Math.max(0, envelope);
    }
}
