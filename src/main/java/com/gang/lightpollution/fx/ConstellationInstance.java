package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.ConstellationParams;
import net.minecraft.world.phys.Vec3;

/** One constellation another mod asked for, rather than one a spell caused. */
final class ConstellationInstance extends FxInstance implements ConstellationSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 8;

    private final ConstellationParams params;

    ConstellationInstance(Vec3 at, ConstellationParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public ConstellationParams shapeParams() {
        return params;
    }

    @Override
    public Vec3 anchorCenter(float partialTick) {
        return centre(partialTick);
    }
}
