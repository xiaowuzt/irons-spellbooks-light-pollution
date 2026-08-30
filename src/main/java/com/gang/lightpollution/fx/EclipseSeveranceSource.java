package com.gang.lightpollution.fx;

import com.gang.lightpollution.client.renderer.EclipseSeveranceVisualInstance;

/**
 * Anything the eclipse severance renderer can draw.
 *
 * <p>Thinner than the other sources: it hands over the visual state machine itself rather than a
 * params record. The sweep's particle arrays are integrated forward in place, so there is nothing to
 * recompute from parameters — the state <em>is</em> the effect, and copying it behind an interface of
 * accessors would be a wrapper around a wrapper.</p>
 */
public interface EclipseSeveranceSource {
    /** The ported state machine driving this sweep. */
    EclipseSeveranceVisualInstance visual();
}
