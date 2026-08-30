package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SingularityParams;
import net.minecraft.world.phys.Vec3;

/** One singularity another mod asked for, rather than one a spell caused. */
final class SingularityInstance extends FxInstance implements SingularitySource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 6;

    private final SingularityParams params;

    SingularityInstance(Vec3 at, SingularityParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public SingularityParams shapeParams() {
        return params;
    }

    @Override
    public Vec3 coreCentre(float partialTick) {
        return centre(partialTick);
    }
}
