package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.MicroquasarParams;
import net.minecraft.world.phys.Vec3;

/** One microquasar another mod asked for, rather than one a spell caused. */
final class MicroquasarInstance extends FxInstance implements MicroquasarSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 12;

    private final MicroquasarParams params;

    MicroquasarInstance(Vec3 at, MicroquasarParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public MicroquasarParams shapeParams() {
        return params;
    }
}
