package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.StarfallParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the starfall renderer can draw: the spell's anchor entity, or an instance another mod asked
 * for through the API.
 */
public interface StarfallSource {
    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** The form knobs the shared shape maths needs. */
    StarfallParams shapeParams();

    /**
     * Where a meteor lands.
     *
     * <p>On the source rather than in the shape maths for the same reason as
     * {@link SkyCollapseSource#shardLanding}: the spell uses the world's heightmap.</p>
     */
    Vec3 meteorLanding(int meteor);
}
