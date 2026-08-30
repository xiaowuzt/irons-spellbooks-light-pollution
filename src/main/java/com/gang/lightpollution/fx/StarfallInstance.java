package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.StarfallParams;
import net.minecraft.world.phys.Vec3;

/**
 * One starfall another mod asked for, rather than one a spell caused.
 *
 * <p>Lands its pieces on the flat plane the params name, since it has no terrain to sample.</p>
 */
final class StarfallInstance extends FxInstance implements StarfallSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 6;

    private final StarfallParams params;

    StarfallInstance(Vec3 at, StarfallParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public StarfallParams shapeParams() {
        return params;
    }

    @Override
    public Vec3 meteorLanding(int meteor) {
        return StarfallShape.flatLanding(params, centre(0.0F), meteor);
    }
}
