package com.gang.lightpollution.fx;

/** Original MC integration math (not a reference author's ray integrator). */
public final class BlackHoleMath {
    private BlackHoleMath() {}
    public static float finite(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
    public static float smooth(float t) {
        t = finite(t, 0, 1); return t * t * (3 - 2 * t);
    }
    public static float envelope(float age, int life, int open, int close) {
        if (!Float.isFinite(age) || age < 0 || age >= life || life < 1) return 0;
        float a = Math.max(1, Math.min(open, life * .4F));
        float b = Math.max(a, Math.min(close, life * .9F));
        return smooth(age / a) * (1 - smooth((age - b) / Math.max(1, life - b)));
    }
    /** Game radius of Radiant Collapse's inward travelling spherical shell. */
    public static float shellRadius(float age, int close, int life, float radius) {
        return radius * (1 - smooth((age - close) / Math.max(1F, life - close)));
    }
}
