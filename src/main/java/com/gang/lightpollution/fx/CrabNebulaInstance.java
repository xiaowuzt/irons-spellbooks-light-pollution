package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.CrabNebulaParams;
import net.minecraft.world.phys.Vec3;

/** One supernova remnant another mod asked for, rather than one a spell caused. */
final class CrabNebulaInstance extends FxInstance implements CrabNebulaSource {
    /** Ticks to fade in over. The spell takes 40 to unfold its cage; this is only a fade. */
    private static final int FADE_IN = 14;

    private final CrabNebulaParams params;

    CrabNebulaInstance(Vec3 at, CrabNebulaParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public CrabNebulaParams shapeParams() {
        return params;
    }
}
