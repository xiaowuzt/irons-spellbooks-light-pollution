package com.gang.lightpollution.client.renderer;

/** Pure visual timing. Every stage scales with the configured impact tick, not a fixed 52. */
public record CelestialVisualTimeline(float age, int impact, int lifetime, boolean confirmedImpact) {
    public float formation() { return VisualEnvelope.smooth(VisualEnvelope.progress(age, impact * 10F / 52, impact * 34F / 52)); }
    public float charge() { return VisualEnvelope.smooth(VisualEnvelope.progress(age, impact * 34F / 52, impact * 49F / 52)); }
    public float compression() { return VisualEnvelope.smooth(VisualEnvelope.progress(age, impact * 49F / 52, impact)); }
    public float beamTravel() { return VisualEnvelope.smooth(VisualEnvelope.progress(age, Math.max(0, impact - Math.min(4, impact * 0.08F)), impact)); }
    public float flash(float ticks) { return confirmedImpact ? VisualEnvelope.impulse(age, impact, ticks) : 0; }
    public float fade() { return VisualEnvelope.smooth(age / Math.max(1, Math.min(8, impact * 0.2F)))
            * (1 - VisualEnvelope.tail(age, lifetime, Math.max(1, Math.min(26, lifetime - impact)))); }
    /** Canonical charge age for the existing small lock sigils. */
    public float lockAge() { return Math.min(52, age * 52 / Math.max(1, impact)); }
}
