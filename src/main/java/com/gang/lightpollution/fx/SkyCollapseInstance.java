package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SkyCollapseParams;
import net.minecraft.world.phys.Vec3;

/**
 * One skycollapse another mod asked for, rather than one a spell caused.
 *
 * <p>Lands its pieces on the flat plane the params name, since it has no terrain to sample.</p>
 */
final class SkyCollapseInstance extends FxInstance implements SkyCollapseSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 6;

    private final SkyCollapseParams params;

    SkyCollapseInstance(Vec3 at, SkyCollapseParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public SkyCollapseParams shapeParams() {
        return params;
    }

    @Override
    public Vec3 shardLanding(int shard) {
        return SkyCollapseShape.flatLanding(params, centre(0.0F), shard);
    }
}
