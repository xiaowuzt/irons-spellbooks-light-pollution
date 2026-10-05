package com.gang.lightpollution.client.renderer;

/** Small, deterministic envelopes. Tick inputs are synchronized ages, never wall-clock time. */
public final class VisualEnvelope {
    private VisualEnvelope() { }

    public static float clamp(float x) {
        return Math.max(0.0F, Math.min(1.0F, x));
    }

    public static float progress(float age, float start, float end) {
        // Some configured charge phases are shorter than one tick. Flooring their denominator
        // to 1 delays the end of the animation beyond the actual impact event.
        if (end <= start) return age < start ? 0.0F : 1.0F;
        return clamp((age - start) / (end - start));
    }

    public static float smooth(float x) {
        x = clamp(x);
        return x * x * (3.0F - 2.0F * x);
    }

    /** No pre-echo: an impact may not light the world before the damage event. */
    public static float impulse(float age, float at, float duration) {
        if (age < at || duration <= 0.0F) return 0.0F;
        float t = progress(age, at, at + duration);
        return (1.0F - t) * (1.0F - t);
    }

    /** Periodic pulse with a distance delay, shared by gas, filaments and their lights. */
    public static float pulse(float age, float distance, float speed, float period) {
        float local = age - distance / Math.max(0.01F, speed);
        if (local < 0.0F || period <= 0.0F) return 0.0F;
        float phase = (local % period) / period;
        return (1.0F - phase) * (1.0F - phase);
    }

    /** Zero lifetime is the public visual API's unlimited lifetime, not an immediate fade. */
    public static float tail(float age, int lifetime, int fadeTicks) {
        if (lifetime <= 0) return 0.0F;
        return smooth(progress(age, Math.max(0, lifetime - fadeTicks), lifetime));
    }
}
