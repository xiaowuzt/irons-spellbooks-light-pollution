package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.WorldTreeParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the world tree renderer can draw: the spell's anchor entity, or an instance another mod
 * asked for through the API.
 */
public interface WorldTreeSource {
    /** Where the trunk rises from, interpolated for the frame. */
    Vec3 seedPoint(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** The form knobs the shared shape maths needs. */
    WorldTreeParams shapeParams();

    /** The built tree. Built once and held, not rebuilt per frame. */
    WorldTreeShape.Skeleton tree();

    /**
     * Where a root point sits.
     *
     * <p>On the source rather than in the shape maths because the spell lays its roots on the terrain
     * surface, which needs the world's heightmap.</p>
     */
    Vec3 rootPoint(int root, float t, float partialTick);
}
