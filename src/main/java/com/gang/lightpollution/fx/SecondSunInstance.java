package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SecondSunParams;
import net.minecraft.world.phys.Vec3;

/** One secondsun another mod asked for, rather than one a spell caused. */
final class SecondSunInstance extends FxInstance implements SecondSunSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 8;

    private final SecondSunParams params;

    SecondSunInstance(Vec3 at, SecondSunParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public SecondSunParams shapeParams() {
        return params;
    }

    @Override
    public float brightness(float partialTick) {
        return SecondSunShape.brightness(getVisualAgeTicks(partialTick), params.lifetimeTicks());
    }
}
