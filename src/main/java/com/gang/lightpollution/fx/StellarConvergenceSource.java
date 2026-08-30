package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.StellarConvergenceParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the stellar convergence renderer can draw: the spell's anchor entity, or an instance
 * another mod asked for through the API.
 */
public interface StellarConvergenceSource {
    /** Where the shell is centred, interpolated for the frame. */
    Vec3 shellCentre(float partialTick);

    /** Where the column meets the ground, interpolated for the frame. */
    Vec3 groundCentre(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** The form knobs the shared shape maths needs. */
    StellarConvergenceParams shapeParams();
}
