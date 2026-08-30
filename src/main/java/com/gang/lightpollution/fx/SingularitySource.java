package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.SingularityParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the singularity renderer can draw: the spell's anchor entity, or an instance another mod
 * asked for through the API.
 */
public interface SingularitySource {
    /** Where the core is, interpolated for the frame. */
    Vec3 coreCentre(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** The form knobs the shared shape maths needs. */
    SingularityParams shapeParams();
}
