package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.MagnetarParams;
import net.minecraft.world.phys.Vec3;

/** One magnetar another mod asked for, rather than one a spell caused. */
final class MagnetarInstance extends FxInstance implements MagnetarSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 10;

    private final MagnetarParams params;

    MagnetarInstance(Vec3 at, MagnetarParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public MagnetarParams shapeParams() {
        return params;
    }

    @Override
    public float wound(float partialTick) {
        return MagnetarShape.wound(getVisualAgeTicks(partialTick));
    }

    @Override
    public float flare(float partialTick) {
        return MagnetarShape.flare(getVisualAgeTicks(partialTick), MagnetarShape.FLARE_TICK);
    }
}
