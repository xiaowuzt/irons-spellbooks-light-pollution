package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SkyCollapseParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the sky collapse renderer can draw: the spell's anchor entity, or an instance another mod
 * asked for through the API.
 */
public interface SkyCollapseSource {
    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** The form knobs the shared shape maths needs. */
    SkyCollapseParams shapeParams();

    /**
     * Where a slab lands.
     *
     * <p>On the source rather than in the shape maths because the spell drops each slab onto the
     * terrain surface, which needs the world's heightmap. An API instance has no terrain to consult
     * and answers from its params instead.</p>
     */
    Vec3 shardLanding(int shard);
}
