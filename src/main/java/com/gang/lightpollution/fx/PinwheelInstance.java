package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.PinwheelParams;
import net.minecraft.world.phys.Vec3;

/** One pinwheel another mod asked for, rather than one a spell caused. */
final class PinwheelInstance extends FxInstance implements PinwheelSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 10;

    private final PinwheelParams params;

    PinwheelInstance(Vec3 at, PinwheelParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public PinwheelParams shapeParams() {
        return params;
    }

    @Override
    public float spunUp(float partialTick) {
        return PinwheelShape.spunUp(getVisualAgeTicks(partialTick));
    }
}
