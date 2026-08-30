package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.HelixNebulaParams;
import net.minecraft.world.phys.Vec3;

/** One planetary nebula another mod asked for, rather than one a spell caused. */
final class HelixNebulaInstance extends FxInstance implements HelixNebulaSource {
    /** Ticks to fade in over. Shorter than the spell's ignition, which is a lighting star. */
    private static final int FADE_IN = 12;

    private final HelixNebulaParams params;

    HelixNebulaInstance(Vec3 at, HelixNebulaParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public HelixNebulaParams shapeParams() {
        return params;
    }
}
