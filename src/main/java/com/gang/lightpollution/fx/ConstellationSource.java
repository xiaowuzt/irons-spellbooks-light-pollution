package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.ConstellationParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the constellation renderer can draw: the spell's anchor entity, or an instance another mod
 * asked for through the API.
 */
public interface ConstellationSource {
    /** Where the orbit is centred, interpolated for the frame. */
    Vec3 anchorCenter(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** The form knobs the shared shape maths needs. */
    ConstellationParams shapeParams();
}
