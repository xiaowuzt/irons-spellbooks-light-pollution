package com.gang.lightpollution.fx;

/**
 * Stateless temporal-history rules with no Minecraft, matrix-library or GL dependency.
 * This is deliberately not reprojection: any numeric transform change invalidates history.
 *
 * <p>The caller supplies unscaled, frame-interpolated game time in seconds, not wall-clock
 * time or the disk's timeRate * rotationSpeed animation time. Compute elapsedSeconds from
 * the last successful capture. Pausing that clock produces delta = 0, not a minimum step.
 *
 * <p>Transform arrays are finite 4x4 matrices (16 elements, in one consistent layout).
 * They are inspected only during a call and are never retained or modified. The renderer
 * must save a separate copy after a successful capture, not alias a mutable current matrix.
 */
public final class RedshiftTemporalPolicy {
    public static final double MAX_HISTORY_GAP_SECONDS = 0.25;
    private static final int TRANSFORM_COMPONENTS = 16;
    private static final double LOG_TWO = Math.log(2.0);

    private RedshiftTemporalPolicy() {}

    /**
     * Whether the previous color must be discarded. On reset, render current color with
     * weight 1 (even while paused); otherwise use {@link #blendWeight(double, double)}.
     *
     * <p>initialized means a previous capture actually succeeded. elapsedSeconds is the
     * current frame's game time minus that capture's game time. A disabled effect or
     * accumulator must invalidate history, rather than leave it initialized for re-enable.
     *
     * <p>The signature is a caller-managed history revision, not an automatically verified
     * scene hash. It must change on cast/effect identity, world, camera state not represented
     * by the transform, effect transform/size, config, resolution, FOV/projection or depth
     * state changes. Use exact comparisons/revision changes in the renderer; equality of an
     * arbitrary 32-bit hash cannot prove compatibility. The transform must include camera
     * movement, or the caller must invalidate that movement separately. This method cannot
     * detect state omitted by the caller.
     */
    public static boolean shouldReset(boolean enabled, boolean initialized, double elapsedSeconds,
                                      float[] currentTransform, float[] previousTransform,
                                      int currentSignature, int previousSignature) {
        return !enabled || !initialized || !isHistoryIntervalValid(elapsedSeconds)
                || currentSignature != previousSignature
                || !sameTransform(currentTransform, previousTransform);
    }

    /** A paused frame and an interval of exactly 0.25 seconds remain valid; rollback does not. */
    public static boolean isHistoryIntervalValid(double elapsedSeconds) {
        return Double.isFinite(elapsedSeconds) && elapsedSeconds >= 0.0
                && elapsedSeconds <= MAX_HISTORY_GAP_SECONDS;
    }

    /**
     * Whether the caller may mark the current capture initialized, including after a reset
     * or while paused. capturedSuccessfully must reflect the renderer's real capture result;
     * this helper does not perform a copy, inspect a texture or validate its dimensions/depth.
     * Store this capture's time, cloned transform and signature only when this returns true;
     * otherwise mark history uninitialized. A finite new clock can seed history after rollback.
     */
    public static boolean isHistoryCaptureValid(boolean enabled, boolean capturedSuccessfully,
                                                double gameTimeSeconds, float[] currentTransform) {
        return enabled && capturedSuccessfully && Double.isFinite(gameTimeSeconds)
                && isValidTransform(currentTransform);
    }

    /**
     * Current-frame weight: 1 - pow(0.5, deltaSeconds / halfLife).
     * expm1 keeps small positive steps accurate without subtractive cancellation.
     * Zero game-time progress returns exactly zero; a zero disk animation speed does not.
     *
     * <p>This is just the decay formula, not the history-validity decision. Call shouldReset
     * first and use weight 1 on a reset. Long gaps are mathematically supported here but
     * cannot reuse history according to isHistoryIntervalValid.
     *
     * @throws IllegalArgumentException for nonfinite/negative deltaSeconds or
     *         nonfinite/nonpositive halfLife
     */
    public static float blendWeight(double deltaSeconds, double halfLife) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0) {
            throw new IllegalArgumentException("deltaSeconds must be nonnegative and finite");
        }
        if (!Double.isFinite(halfLife) || halfLife <= 0.0) {
            throw new IllegalArgumentException("halfLife must be positive and finite");
        }
        if (deltaSeconds == 0.0) return 0.0f;
        return (float) -Math.expm1(-LOG_TWO * (deltaSeconds / halfLife));
    }

    private static boolean sameTransform(float[] current, float[] previous) {
        if (!isValidTransform(current) || !isValidTransform(previous)) return false;
        for (int i = 0; i < TRANSFORM_COMPONENTS; i++) {
            // No epsilon: without reprojection even subpixel motion must invalidate history.
            // Signed zeros compare equal because they represent the same transform.
            if (current[i] != previous[i]) return false;
        }
        return true;
    }

    private static boolean isValidTransform(float[] transform) {
        if (transform == null || transform.length != TRANSFORM_COMPONENTS) return false;
        for (float component : transform) {
            if (!Float.isFinite(component)) return false;
        }
        return true;
    }
}
