package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.LeviathanParams;
import net.minecraft.world.phys.Vec3;

/** One leviathan another mod asked for, rather than one a spell caused. */
final class LeviathanInstance extends FxInstance implements LeviathanSource {
    /** Ticks to fade in over. Short, because the body arriving is its own entrance. */
    private static final int FADE_IN = 5;

    private final LeviathanParams params;

    LeviathanInstance(Vec3 at, LeviathanParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public LeviathanParams shapeParams() {
        return params;
    }

    @Override
    public Vec3 anchor(float partialTick) {
        return centre(partialTick);
    }
}
