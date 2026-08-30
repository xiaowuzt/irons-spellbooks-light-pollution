package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.WorldTreeParams;
import net.minecraft.world.phys.Vec3;

/**
 * One world tree another mod asked for, rather than one a spell caused.
 *
 * <p>Lays its roots on the flat height the params name, since it has no terrain to sample.</p>
 */
final class WorldTreeInstance extends FxInstance implements WorldTreeSource {
    /** Ticks to fade in over. Short: the trunk rising is its own entrance. */
    private static final int FADE_IN = 4;

    private final WorldTreeParams params;
    /** Built on first use rather than in the constructor, which may be off the render thread. */
    private WorldTreeShape.Skeleton tree;

    WorldTreeInstance(Vec3 at, WorldTreeParams params) {
        super(at, FADE_IN, params.lifetimeTicks());
        this.params = params;
    }

    @Override
    public WorldTreeParams shapeParams() {
        return params;
    }

    @Override
    public Vec3 seedPoint(float partialTick) {
        return centre(partialTick);
    }

    @Override
    public WorldTreeShape.Skeleton tree() {
        if (tree == null) {
            tree = WorldTreeShape.build(params);
        }
        return tree;
    }

    @Override
    public Vec3 rootPoint(int root, float t, float partialTick) {
        return WorldTreeShape.rootPointAt(params, centre(partialTick), root, t, params.groundY());
    }
}
