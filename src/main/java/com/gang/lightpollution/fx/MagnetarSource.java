package com.gang.lightpollution.fx;

import com.gang.lightpollution.api.MagnetarParams;
import net.minecraft.world.phys.Vec3;

/**
 * Anything the magnetar renderer can draw: the spell's anchor entity, or an instance another
 * mod asked for through the API.
 */
public interface MagnetarSource {
    /** Where the neutron star is, interpolated for the frame. */
    Vec3 centre(float partialTick);

    /** How far into its life it is, in ticks, interpolated for the frame. */
    float getVisualAgeTicks(float partialTick);

    /** Overall visibility, 0 to 1, including any fade in or out. */
    float brightness(float partialTick);

    /** The form knobs the shared shape maths needs. */
    MagnetarParams shapeParams();

    /** How far the field has wound up, 0 to 1. */
    float wound(float partialTick);

    /** The flare, 0 to 1. */
    float flare(float partialTick);
}
