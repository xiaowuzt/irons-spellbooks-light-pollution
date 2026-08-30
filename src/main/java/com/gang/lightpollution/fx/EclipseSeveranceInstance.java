package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.EclipseSeveranceParams;
import com.gang.lightpollution.api.FxHandle;
import com.gang.lightpollution.client.renderer.EclipseSeveranceVisualInstance;
import net.minecraft.world.phys.Vec3;

/**
 * One eclipse severance another mod asked for, rather than one a spell caused.
 *
 * <p>Does not extend {@link FxInstance}, and that is the whole reason this class exists separately:
 * the ported sweep runs on a monotonic wall clock with a fixed duration, so the generic tick-counting
 * fade envelope has nothing to offer it. Wrapping it in one would mean two clocks disagreeing.</p>
 */
final class EclipseSeveranceInstance implements EclipseSeveranceSource, FxHandle {
    private final EclipseSeveranceVisualInstance visual;
    private boolean removed;

    EclipseSeveranceInstance(Vec3 at, EclipseSeveranceParams params) {
        this.visual = new EclipseSeveranceVisualInstance(at.x, at.y, at.z,
                params.facingYawDegrees(), params.seed());
    }

    @Override
    public EclipseSeveranceVisualInstance visual() {
        return visual;
    }

    /**
     * Refuses to move.
     *
     * <p>The sweep's arc, its speed lines and its already-integrated particles are all in world
     * coordinates fixed when it was created. Moving the origin would leave the particles behind and
     * bend the arc away from them. Throwing is better than a silent no-op: a caller who expected this
     * to work should find out, not wonder why nothing happened.</p>
     */
    @Override
    public void setPosition(Vec3 position) {
        throw new UnsupportedOperationException(
                "An eclipse severance cannot be moved after it starts; its particles are already "
                        + "integrated in world space. Remove it and make a new one.");
    }

    @Override
    public void remove() {
        this.removed = true;
    }

    @Override
    public boolean isAlive() {
        return !removed && visual.isAlive(visual.ageSeconds());
    }
}
