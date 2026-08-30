package com.gang.lightpollution.fx;

import com.gang.lightpollution.client.renderer.GeminiKillEffectVisualInstance;

/**
 * Anything the funeral nova renderer can draw.
 *
 * <p>Same arrangement as {@link EclipseSeveranceSource}: it hands over the ported state machine
 * directly, because the ten-stage sequence is the state rather than something derived from
 * parameters.</p>
 */
public interface FuneralNovaSource {
    /** The ported state machine driving this collapse. */
    GeminiKillEffectVisualInstance visual();
}
