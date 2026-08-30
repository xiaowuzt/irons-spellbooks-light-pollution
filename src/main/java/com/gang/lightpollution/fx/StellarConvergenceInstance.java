package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.StellarConvergenceParams;
import net.minecraft.world.phys.Vec3;

/**
 * One stellar convergence another mod asked for, rather than one a spell caused.
 *
 * <p>Its shell and its ground point are the same position. The spell puts the shell above the point
 * the column lands on; a caller who wants that can pass the shell centre and read the column as going
 * through it.</p>
 */
final class StellarConvergenceInstance extends FxInstance implements StellarConvergenceSource {
    /** Ticks to fade in over. */
    private static final int FADE_IN = 10;

    private final StellarConvergenceParams params;

    StellarConvergenceInstance(Vec3 at, StellarConvergenceParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public StellarConvergenceParams shapeParams() {
        return params;
    }

    @Override
    public Vec3 shellCentre(float partialTick) {
        return centre(partialTick);
    }

    @Override
    public Vec3 groundCentre(float partialTick) {
        return centre(partialTick);
    }
}
