package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.LeviathanParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the leviathan renderer can draw: the spell's anchor entity, or an instance another mod
 * asked for through the API.
 */
public interface LeviathanSource {
    /** Where the body is anchored, interpolated for the frame. */
    Vec3 anchor(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** The form knobs the shared shape maths needs. */
    LeviathanParams shapeParams();
}
