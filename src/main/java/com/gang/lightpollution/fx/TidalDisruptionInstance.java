package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.TidalDisruptionParams;
import net.minecraft.world.phys.Vec3;

/** One tidal disruption another mod asked for, rather than one a spell caused. */
final class TidalDisruptionInstance extends FxInstance implements TidalDisruptionSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 8;

    private final TidalDisruptionParams params;

    TidalDisruptionInstance(Vec3 at, TidalDisruptionParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public float flare(float partialTick) {
        return TidalDisruptionShape.flare(getVisualAgeTicks(partialTick), params.flareStartTick());
    }

    @Override
    public TidalDisruptionParams shapeParams() {
        return params;
    }
}
